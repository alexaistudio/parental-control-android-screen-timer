package dev.tvtimer.app;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Set;

public final class LimiterAccessibilityService extends AccessibilityService {
    private static final String TAG = "TVTimerService";
    private static final long TICK_MILLIS = 1_000L;
    private static final long PERSIST_INTERVAL_MILLIS = 5_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            evaluateNow();
            handler.postDelayed(this, TICK_MILLIS);
        }
    };

    private ConfigStore store;
    private WindowManager windowManager;
    private PowerManager powerManager;
    private String activePackage;
    private boolean interactive;
    private boolean dreaming;
    private boolean countedDuringPreviousInterval;
    private boolean connected;
    private long lastTickElapsed = -1L;
    private long pendingUsageMillis;
    private String pendingUsageDay;
    private TextView timerView;
    private View blockerView;
    private int overlayFailureCount;
    private long nextOverlayAttemptElapsed;
    private BroadcastReceiver stateReceiver;
    private BroadcastReceiver usbReceiver;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        store = new ConfigStore(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        interactive = powerManager != null && powerManager.isInteractive();
        connected = true;
        lastTickElapsed = SystemClock.elapsedRealtime();
        registerReceivers();
        preferenceListener = (preferences, key) -> handler.post(this::evaluateNow);
        store.registerListener(preferenceListener);
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        Log.i(TAG, "Accessibility service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return;
        }
        CharSequence packageName = event.getPackageName();
        if (packageName == null) {
            return;
        }
        String nextPackage = packageName.toString();
        if (!ForegroundEventPolicy.shouldReplaceActivePackage(
                nextPackage,
                getPackageName(),
                type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        )) {
            return;
        }
        if (!nextPackage.equals(activePackage)) {
            evaluateNow();
            activePackage = nextPackage;
            countedDuringPreviousInterval = false;
            lastTickElapsed = SystemClock.elapsedRealtime();
            evaluateNow();
        }
    }

    @Override
    public void onInterrupt() {
        countedDuringPreviousInterval = false;
        flushPendingUsage();
        removeAllOverlays();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        shutdown();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        shutdown();
        super.onDestroy();
    }

    private void evaluateNow() {
        if (!connected || store == null) {
            return;
        }
        long nowElapsed = SystemClock.elapsedRealtime();
        String day = DayKey.localDay(System.currentTimeMillis());
        long elapsed = LimitMath.elapsedDelta(lastTickElapsed, nowElapsed);
        if (countedDuringPreviousInterval && elapsed > 0L) {
            if (pendingUsageDay != null && !pendingUsageDay.equals(day)) {
                flushPendingUsage();
            }
            pendingUsageDay = day;
            pendingUsageMillis += elapsed;
        }
        lastTickElapsed = nowElapsed;

        if (pendingUsageMillis >= PERSIST_INTERVAL_MILLIS) {
            flushPendingUsage();
        }

        boolean screenActive = powerManager != null && powerManager.isInteractive() && !dreaming;
        interactive = screenActive;
        Set<String> selectedPackages = store.getSelectedPackages();
        boolean targetActive = store.isEnforcementEnabled()
                && interactive
                && AppScope.isTarget(
                        store.getScope(),
                        activePackage,
                        getPackageName(),
                        selectedPackages
                );

        ConfigStore.DayState dayState = store.getDayState(day);
        long inMemoryUsage = day.equals(pendingUsageDay) ? pendingUsageMillis : 0L;
        long used = dayState.getUsedMillis() > Long.MAX_VALUE - inMemoryUsage
                ? Long.MAX_VALUE
                : dayState.getUsedMillis() + inMemoryUsage;
        long remaining = LimitMath.remaining(
                store.getDailyLimitMillis(),
                dayState.getBonusMillis(),
                used
        );

        if (!targetActive) {
            countedDuringPreviousInterval = false;
            removeAllOverlays();
            return;
        }
        if (remaining <= 0L) {
            countedDuringPreviousInterval = false;
            showBlocker();
        } else {
            countedDuringPreviousInterval = true;
            showTimer(remaining);
        }
    }

    private void showTimer(long remainingMillis) {
        removeBlocker();
        if (timerView == null) {
            if (!canAttemptOverlay()) {
                return;
            }
            TextView view = new TextView(this);
            view.setTextColor(Color.WHITE);
            view.setTextSize(18f);
            view.setGravity(Gravity.CENTER);
            view.setPadding(dp(14), dp(8), dp(14), dp(8));
            GradientDrawable background = new GradientDrawable();
            background.setColor(0xcc000000);
            background.setCornerRadius(dp(8));
            view.setBackground(background);
            view.setContentDescription("Оставшееся экранное время");

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.END;
            params.x = dp(22);
            params.y = dp(22);
            try {
                windowManager.addView(view, params);
                timerView = view;
                recordOverlaySuccess();
            } catch (RuntimeException exception) {
                recordOverlayFailure("timer", exception);
                return;
            }
        }
        timerView.setText(LimitMath.formatCountdown(remainingMillis));
    }

    private void showBlocker() {
        removeTimer();
        if (blockerView != null || !canAttemptOverlay()) {
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(24), dp(20), dp(24), dp(20));
        renderPinPrompt(panel);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                Math.min(dp(620), getResources().getDisplayMetrics().widthPixels - dp(40)),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        root.addView(panel, panelParams);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                PixelFormat.OPAQUE
        );
        params.gravity = Gravity.TOP | Gravity.START;
        try {
            windowManager.addView(root, params);
            blockerView = root;
            root.requestFocus();
            recordOverlaySuccess();
        } catch (RuntimeException exception) {
            recordOverlayFailure("blocker", exception);
        }
    }

    private void renderPinPrompt(LinearLayout panel) {
        panel.removeAllViews();
        TextView title = overlayText("Время закончилось", 30f, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, wrapParams(dp(8)));
        TextView instructions = overlayText("Введите PIN родителя", 20f, 0xffeeeeee);
        instructions.setGravity(Gravity.CENTER);
        panel.addView(instructions, wrapParams(dp(12)));

        final PinPadView[] holder = new PinPadView[1];
        holder[0] = new PinPadView(this, pin -> {
            if (store.verifyPin(pin)) {
                renderParentActions(panel);
            } else {
                holder[0].showError("Неверный PIN");
            }
        });
        panel.addView(holder[0], wrapParams(0));
    }

    private void renderParentActions(LinearLayout panel) {
        panel.removeAllViews();
        TextView title = overlayText("Что сделать?", 28f, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, wrapParams(dp(18)));

        Button addTime = overlayButton("Добавить 15 минут");
        addTime.setOnClickListener(view -> {
            flushPendingUsage();
            String day = DayKey.localDay(System.currentTimeMillis());
            if (store.addBonus(day, ConfigStore.EXTRA_TIME_MILLIS)) {
                removeBlocker();
                countedDuringPreviousInterval = false;
                lastTickElapsed = SystemClock.elapsedRealtime();
                evaluateNow();
            }
        });
        panel.addView(addTime, buttonParams());

        Button disable = overlayButton("Отключить полностью");
        disable.setOnClickListener(view -> {
            flushPendingUsage();
            if (store.setEnforcementEnabled(false)) {
                countedDuringPreviousInterval = false;
                removeAllOverlays();
            }
        });
        panel.addView(disable, buttonParams());

        Button back = overlayButton("Назад");
        back.setOnClickListener(view -> renderPinPrompt(panel));
        panel.addView(back, buttonParams());
        addTime.requestFocus();
    }

    private void registerReceivers() {
        stateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                evaluateNow();
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    interactive = false;
                    countedDuringPreviousInterval = false;
                    flushPendingUsage();
                    removeAllOverlays();
                } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    interactive = true;
                    lastTickElapsed = SystemClock.elapsedRealtime();
                    evaluateNow();
                } else if (Intent.ACTION_DREAMING_STARTED.equals(action)) {
                    dreaming = true;
                    countedDuringPreviousInterval = false;
                    flushPendingUsage();
                    removeAllOverlays();
                } else if (Intent.ACTION_DREAMING_STOPPED.equals(action)) {
                    dreaming = false;
                    lastTickElapsed = SystemClock.elapsedRealtime();
                    evaluateNow();
                }
            }
        };
        IntentFilter stateFilter = new IntentFilter();
        stateFilter.addAction(Intent.ACTION_SCREEN_ON);
        stateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        stateFilter.addAction(Intent.ACTION_DREAMING_STARTED);
        stateFilter.addAction(Intent.ACTION_DREAMING_STOPPED);
        registerSystemReceiver(stateReceiver, stateFilter);

        usbReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (UsbRecoveryReceiver.isRecoveryIntent(intent)) {
                    UsbRecoveryReceiver.performRecovery(context);
                }
            }
        };
        IntentFilter usbFilter = new IntentFilter();
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        usbFilter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        registerSystemReceiver(usbReceiver, usbFilter);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerSystemReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    private void flushPendingUsage() {
        if (store != null && pendingUsageMillis > 0L && pendingUsageDay != null) {
            if (!store.addUsage(pendingUsageDay, pendingUsageMillis)) {
                Log.e(TAG, "Unable to persist elapsed usage");
                return;
            }
            pendingUsageMillis = 0L;
        }
    }

    private void shutdown() {
        if (!connected) {
            return;
        }
        connected = false;
        handler.removeCallbacks(ticker);
        flushPendingUsage();
        countedDuringPreviousInterval = false;
        removeAllOverlays();
        if (store != null && preferenceListener != null) {
            store.unregisterListener(preferenceListener);
        }
        unregisterSafely(stateReceiver);
        unregisterSafely(usbReceiver);
        Log.i(TAG, "Accessibility service disconnected");
    }

    private void unregisterSafely(BroadcastReceiver receiver) {
        if (receiver == null) {
            return;
        }
        try {
            unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
            Log.w(TAG, "Receiver was already unregistered");
        }
    }

    private boolean canAttemptOverlay() {
        return windowManager != null && SystemClock.elapsedRealtime() >= nextOverlayAttemptElapsed;
    }

    private void recordOverlaySuccess() {
        overlayFailureCount = 0;
        nextOverlayAttemptElapsed = 0L;
    }

    private void recordOverlayFailure(String overlayName, RuntimeException exception) {
        overlayFailureCount = Math.min(overlayFailureCount + 1, 6);
        long delaySeconds = Math.min(60L, 1L << overlayFailureCount);
        nextOverlayAttemptElapsed = SystemClock.elapsedRealtime() + delaySeconds * 1_000L;
        Log.e(TAG, "Unable to show " + overlayName + "; retry is delayed", exception);
    }

    private void removeAllOverlays() {
        removeTimer();
        removeBlocker();
    }

    private void removeTimer() {
        if (timerView != null) {
            removeViewSafely(timerView);
            timerView = null;
        }
    }

    private void removeBlocker() {
        if (blockerView != null) {
            removeViewSafely(blockerView);
            blockerView = null;
        }
    }

    private void removeViewSafely(View view) {
        try {
            if (windowManager != null) {
                windowManager.removeViewImmediate(view);
            }
        } catch (IllegalArgumentException exception) {
            Log.w(TAG, "Overlay was already removed");
        }
    }

    private TextView overlayText(String text, float size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button overlayButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(18f);
        button.setMinWidth(dp(320));
        button.setMinHeight(dp(58));
        return button;
    }

    private LinearLayout.LayoutParams wrapParams(int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = bottomMargin;
        return params;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(360), dp(64));
        params.bottomMargin = dp(10);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
