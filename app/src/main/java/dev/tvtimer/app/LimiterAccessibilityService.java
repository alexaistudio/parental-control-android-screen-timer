package dev.tvtimer.app;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.pm.ResolveInfo;
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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LimiterAccessibilityService extends AccessibilityService {
    private static final String TAG = "TVTimerService";
    private static final long TICK_MILLIS = 1_000L;
    private static final long PERSIST_INTERVAL_MILLIS = 5_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
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
    private boolean configurationPresent;
    private boolean enforcementEnabled;
    private boolean protectedScreenActive;
    private String targetScope = AppScope.ALL;
    private Set<String> targetPackages = Collections.emptySet();
    private Set<String> homePackages = Collections.emptySet();
    private Set<String> launchablePackages = Collections.emptySet();
    private long dailyLimitMillis = ConfigStore.DEFAULT_LIMIT_MILLIS;
    private long lastTickElapsed = -1L;
    private long pendingUsageMillis;
    private String pendingUsageDay;
    private TextView timerView;
    private View blockerView;
    private BlockReason blockerReason;
    private int overlayFailureCount;
    private long nextOverlayAttemptElapsed;
    private BroadcastReceiver stateReceiver;
    private BroadcastReceiver usbReceiver;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
    private int pinPromptGeneration;
    private long pendingWarningMinutes;
    private String pendingWarningDay;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLanguage.wrap(newBase));
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        store = new ConfigStore(this);
        if (store.isConfigured()) {
            DeviceOwnerProtection.ensureUninstallBlocked(this);
        }
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        homePackages = loadHomePackages();
        launchablePackages = loadLaunchablePackages();
        interactive = powerManager != null && powerManager.isInteractive();
        connected = true;
        refreshRuntimeConfiguration();
        lastTickElapsed = SystemClock.elapsedRealtime();
        registerReceivers();
        preferenceListener = (preferences, key) -> {
            if (ConfigStore.affectsRuntimeConfiguration(key)) {
                handler.post(() -> {
                    if (ConfigStore.isLanguagePreference(key)) {
                        AppLanguage.apply(this);
                        rebuildLocalizedOverlays();
                    }
                    refreshRuntimeConfiguration();
                    evaluateNow();
                });
            }
        };
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
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence packageName = event.getPackageName();
        if (packageName == null) {
            return;
        }
        String nextPackage = packageName.toString();
        CharSequence className = event.getClassName();
        String nextClass = className == null ? null : className.toString();
        boolean protectionWasActive = protectedScreenActive;
        if (nextPackage.equals(getPackageName())
                && MainActivity.class.getName().equals(nextClass)) {
            protectedScreenActive = false;
        } else if (!nextPackage.equals(getPackageName())) {
            if (RemovalProtectionPolicy.isSensitiveScreen(nextPackage, nextClass)) {
                protectedScreenActive = true;
            } else if (!RemovalProtectionPolicy.isProtectionPackage(nextPackage)) {
                protectedScreenActive = false;
            }
        }
        if (protectionWasActive != protectedScreenActive) {
            evaluateNow();
        }
        if (!launchablePackages.isEmpty()
                && !launchablePackages.contains(nextPackage)
                && !homePackages.contains(nextPackage)
                && !targetPackages.contains(nextPackage)
                && !nextPackage.equals(getPackageName())) {
            return;
        }
        if (!ForegroundEventPolicy.shouldReplaceActivePackage(
                nextPackage,
                nextClass,
                getPackageName(),
                MainActivity.class.getName(),
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
        backgroundExecutor.shutdownNow();
        super.onDestroy();
    }

    private void refreshRuntimeConfiguration() {
        if (store == null) {
            configurationPresent = false;
            enforcementEnabled = false;
            targetScope = AppScope.ALL;
            targetPackages = Collections.emptySet();
            dailyLimitMillis = ConfigStore.DEFAULT_LIMIT_MILLIS;
            return;
        }
        configurationPresent = store.isConfigured();
        if (!configurationPresent) {
            protectedScreenActive = false;
        }
        enforcementEnabled = store.isEnforcementEnabled();
        targetScope = store.getScope();
        targetPackages = store.getSelectedPackages();
        dailyLimitMillis = store.getDailyLimitMillis();
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
        if (configurationPresent
                && screenActive
                && protectedScreenActive
                && !store.isMaintenanceAllowed(System.currentTimeMillis())) {
            countedDuringPreviousInterval = false;
            showBlocker(BlockReason.REMOVAL_PROTECTION);
            return;
        }
        boolean targetActive = enforcementEnabled
                && interactive
                && !homePackages.contains(activePackage)
                && AppScope.isTarget(
                        targetScope,
                        activePackage,
                        getPackageName(),
                        targetPackages
                );

        ConfigStore.DayState dayState = store.getDayState(day);
        long inMemoryUsage = day.equals(pendingUsageDay) ? pendingUsageMillis : 0L;
        long used = dayState.getUsedMillis() > Long.MAX_VALUE - inMemoryUsage
                ? Long.MAX_VALUE
                : dayState.getUsedMillis() + inMemoryUsage;
        long remaining = LimitMath.remaining(
                dailyLimitMillis,
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
            showBlocker(BlockReason.TIME_LIMIT);
        } else {
            long warningMinutes = store.getDueUsageWarningMinutes(day, used);
            if (warningMinutes > 0L) {
                countedDuringPreviousInterval = false;
                showUsageWarning(warningMinutes, day);
            } else {
                countedDuringPreviousInterval = true;
                showTimer(remaining);
            }
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
            view.setContentDescription(getString(R.string.timer_remaining_description));

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
        String countdown = LimitMath.formatCountdown(remainingMillis);
        if (!countdown.contentEquals(timerView.getText())) {
            timerView.setText(countdown);
        }
    }

    private void showUsageWarning(long warningMinutes, String day) {
        if (blockerView != null && blockerReason != BlockReason.USAGE_WARNING) {
            removeBlocker();
        }
        pendingWarningMinutes = warningMinutes;
        pendingWarningDay = day;
        showBlocker(BlockReason.USAGE_WARNING);
    }

    private void showBlocker(BlockReason reason) {
        removeTimer();
        if (blockerView != null && blockerReason == reason) {
            return;
        }
        if (blockerView != null) {
            removeBlocker();
        }
        if (!canAttemptOverlay()) {
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setFocusable(false);
        root.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        LanguageSwitcherView languageSwitcher = new LanguageSwitcherView(this, () -> {
        });
        FrameLayout.LayoutParams languageParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END
        );
        languageParams.setMargins(0, dp(16), dp(18), 0);
        root.addView(languageSwitcher, languageParams);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(24), dp(20), dp(24), dp(20));
        View initialFocus = reason == BlockReason.USAGE_WARNING
                ? renderUsageWarning(panel)
                : renderPinPrompt(panel, reason);

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
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE
        );
        params.gravity = Gravity.TOP | Gravity.START;
        try {
            windowManager.addView(root, params);
            blockerView = root;
            blockerReason = reason;
            if (initialFocus instanceof PinPadView) {
                PinPadView pinPad = (PinPadView) initialFocus;
                pinPad.post(pinPad::requestInitialFocus);
            } else {
                initialFocus.post(initialFocus::requestFocus);
            }
            recordOverlaySuccess();
        } catch (RuntimeException exception) {
            recordOverlayFailure("blocker", exception);
        }
    }

    private PinPadView renderPinPrompt(LinearLayout panel, BlockReason reason) {
        int generation = ++pinPromptGeneration;
        panel.removeAllViews();
        String titleText = reason == BlockReason.TIME_LIMIT
                ? getString(R.string.time_finished_title)
                : getString(R.string.settings_protection_title);
        String instructionText = reason == BlockReason.TIME_LIMIT
                ? getString(R.string.time_finished_instruction)
                : getString(R.string.settings_protection_instruction);
        TextView title = overlayText(titleText, 30f, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, wrapParams(dp(8)));
        TextView instructions = overlayText(instructionText, 20f, 0xffeeeeee);
        instructions.setGravity(Gravity.CENTER);
        panel.addView(instructions, wrapParams(dp(12)));

        final PinPadView[] holder = new PinPadView[1];
        holder[0] = new PinPadView(this, pin -> {
            holder[0].setBusy(true);
            backgroundExecutor.execute(() -> {
                boolean verified;
                try {
                    verified = store != null
                            && store.verifyParentCode(pin, System.currentTimeMillis());
                } catch (RuntimeException exception) {
                    Log.e(TAG, "Unable to verify parent code", exception);
                    verified = false;
                }
                boolean result = verified;
                handler.post(() -> {
                    if (!connected
                            || blockerView == null
                            || generation != pinPromptGeneration) {
                        return;
                    }
                    if (result) {
                        renderParentActions(panel, reason);
                    } else {
                        holder[0].showError(getString(R.string.parent_code_invalid));
                    }
                });
            });
        });
        panel.addView(holder[0], wrapParams(0));
        holder[0].post(holder[0]::requestInitialFocus);
        return holder[0];
    }

    private View renderUsageWarning(LinearLayout panel) {
        pinPromptGeneration++;
        panel.removeAllViews();
        TextView title = overlayText(getString(R.string.usage_pause_title), 30f, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, wrapParams(dp(10)));

        TextView message = overlayText(
                getResources().getQuantityString(
                        R.plurals.usage_warning_message,
                        (int) pendingWarningMinutes,
                        pendingWarningMinutes
                ),
                23f,
                0xffeeeeee
        );
        message.setGravity(Gravity.CENTER);
        panel.addView(message, wrapParams(dp(20)));

        Button continueButton = overlayButton(getString(R.string.continue_yes));
        continueButton.setOnClickListener(view -> acknowledgeUsageWarning(false));
        panel.addView(continueButton, buttonParams());

        Button finishButton = overlayButton(getString(R.string.finish_no));
        finishButton.setOnClickListener(view -> acknowledgeUsageWarning(true));
        panel.addView(finishButton, buttonParams());
        return continueButton;
    }

    private void acknowledgeUsageWarning(boolean finishWatching) {
        if (pendingWarningDay == null
                || !store.acknowledgeUsageWarning(pendingWarningDay, pendingWarningMinutes)) {
            return;
        }
        removeBlocker();
        countedDuringPreviousInterval = false;
        lastTickElapsed = SystemClock.elapsedRealtime();
        if (finishWatching) {
            if (!performGlobalAction(GLOBAL_ACTION_HOME)) {
                performGlobalAction(GLOBAL_ACTION_BACK);
            }
        } else {
            evaluateNow();
        }
    }

    private void renderParentActions(LinearLayout panel, BlockReason reason) {
        pinPromptGeneration++;
        panel.removeAllViews();
        TextView title = overlayText(getString(R.string.parent_actions_title), 28f, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, wrapParams(dp(18)));

        if (reason == BlockReason.REMOVAL_PROTECTION) {
            Button allow = overlayButton(getString(R.string.allow_changes_two_minutes));
            allow.setOnClickListener(view -> {
                if (store.grantMaintenanceWindow()) {
                    removeBlocker();
                    evaluateNow();
                }
            });
            panel.addView(allow, buttonParams());

            Button home = overlayButton(getString(R.string.return_home));
            home.setOnClickListener(view -> {
                protectedScreenActive = false;
                removeBlocker();
                performGlobalAction(GLOBAL_ACTION_HOME);
            });
            panel.addView(home, buttonParams());

            Button back = overlayButton(getString(R.string.back));
            back.setOnClickListener(view -> renderPinPrompt(panel, reason));
            panel.addView(back, buttonParams());
            allow.post(allow::requestFocus);
            return;
        }

        int defaultMinutes = store.getDefaultExtensionMinutes();
        if (defaultMinutes != ExtensionDurationPolicy.ASK_EVERY_TIME) {
            grantExtension(defaultMinutes);
            return;
        }

        title.setText(R.string.extension_prompt);
        Button firstChoice = null;
        for (int minutes : ExtensionDurationPolicy.CHOICES_MINUTES) {
            Button choice = overlayButton(extensionLabel(minutes));
            choice.setOnClickListener(view -> grantExtension(minutes));
            panel.addView(choice, buttonParams());
            if (firstChoice == null) {
                firstChoice = choice;
            }
        }

        Button disable = overlayButton(getString(R.string.disable_completely));
        disable.setOnClickListener(view -> {
            flushPendingUsage();
            if (store.setEnforcementEnabled(false)) {
                countedDuringPreviousInterval = false;
                removeAllOverlays();
            }
        });
        panel.addView(disable, buttonParams());

        Button back = overlayButton(getString(R.string.back));
        back.setOnClickListener(view -> renderPinPrompt(panel, reason));
        panel.addView(back, buttonParams());
        if (firstChoice != null) {
            firstChoice.post(firstChoice::requestFocus);
        }
    }

    private void grantExtension(int minutes) {
        flushPendingUsage();
        String day = DayKey.localDay(System.currentTimeMillis());
        if (store.addBonus(day, ExtensionDurationPolicy.toMillis(minutes))) {
            removeBlocker();
            countedDuringPreviousInterval = false;
            lastTickElapsed = SystemClock.elapsedRealtime();
            evaluateNow();
        }
    }

    private String extensionLabel(int minutes) {
        return minutes == 60
                ? getString(R.string.one_hour)
                : getResources().getQuantityString(
                        R.plurals.minutes_label,
                        minutes,
                        minutes
                );
    }

    private void rebuildLocalizedOverlays() {
        if (timerView != null) {
            timerView.setContentDescription(getString(R.string.timer_remaining_description));
        }
        BlockReason reason = blockerReason;
        if (reason == null) {
            return;
        }
        long warningMinutes = pendingWarningMinutes;
        String warningDay = pendingWarningDay;
        removeBlocker();
        pendingWarningMinutes = warningMinutes;
        pendingWarningDay = warningDay;
        showBlocker(reason);
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

    private Set<String> loadHomePackages() {
        Set<String> result = new HashSet<>();
        Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        try {
            for (ResolveInfo info : getPackageManager().queryIntentActivities(homeIntent, 0)) {
                if (info.activityInfo != null && info.activityInfo.packageName != null) {
                    result.add(info.activityInfo.packageName);
                }
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to enumerate TV launcher packages", exception);
        }
        return result;
    }

    private Set<String> loadLaunchablePackages() {
        Set<String> result = new HashSet<>();
        collectLaunchablePackages(Intent.CATEGORY_LEANBACK_LAUNCHER, result);
        collectLaunchablePackages(Intent.CATEGORY_LAUNCHER, result);
        return result;
    }

    private void collectLaunchablePackages(String category, Set<String> destination) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
        try {
            for (ResolveInfo info : getPackageManager().queryIntentActivities(intent, 0)) {
                if (info.activityInfo != null && info.activityInfo.packageName != null) {
                    destination.add(info.activityInfo.packageName);
                }
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to enumerate launchable TV applications", exception);
        }
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
            pinPromptGeneration++;
            removeViewSafely(blockerView);
            blockerView = null;
            blockerReason = null;
            pendingWarningMinutes = 0L;
            pendingWarningDay = null;
        }
    }

    private enum BlockReason {
        TIME_LIMIT,
        REMOVAL_PROTECTION,
        USAGE_WARNING
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
        button.setAllCaps(false);
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_focused},
                new int[]{android.R.attr.state_pressed},
                new int[]{}
        };
        button.setBackgroundTintList(new ColorStateList(
                states,
                new int[]{0xffffd54f, 0xffffb300, 0xff455a64}
        ));
        button.setTextColor(new ColorStateList(
                states,
                new int[]{Color.BLACK, Color.BLACK, Color.WHITE}
        ));
        button.setOnFocusChangeListener((view, hasFocus) -> {
            float scale = hasFocus ? 1.06f : 1f;
            view.animate().scaleX(scale).scaleY(scale).setDuration(90L).start();
            view.setElevation(hasFocus ? dp(10) : dp(2));
        });
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
