package dev.tvtimer.app;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LimiterAccessibilityService extends AccessibilityService {
    private static final String TAG = "ScreenTimerService";
    private static final long TICK_MILLIS = 1_000L;
    private static final long PERSIST_INTERVAL_MILLIS = 5_000L;
    private static final long PROTECTION_STABILITY_MILLIS = 400L;
    private static final String SYSTEM_DIALOG_REASON_KEY = "reason";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final Runnable connector = this::initializeRuntimeSafely;
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
    private KeyguardManager keyguardManager;
    private String activePackage;
    private boolean interactive;
    private boolean dreaming;
    private boolean countedDuringPreviousInterval;
    private boolean connected;
    private boolean configurationPresent;
    private boolean enforcementEnabled;
    private boolean protectedScreenActive;
    private long protectionEligibleAtElapsed;
    private boolean systemSettingsProtectionEnabled = true;
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
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
    private int pinPromptGeneration;
    private long pendingWarningMinutes;
    private String pendingWarningDay;
    private int connectionAttempts;
    private boolean parentModeActive;
    private boolean parentModeGestureEnabled = true;
    private ModeSwitchFrameLayout blockerRoot;
    private LinearLayout blockerPanel;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLanguage.wrap(newBase));
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        connectionAttempts = 0;
        handler.removeCallbacks(connector);
        handler.post(connector);
    }

    private void initializeRuntimeSafely() {
        if (connected) {
            return;
        }
        connectionAttempts++;
        DiagnosticLog.info(
                this,
                TAG,
                "Connection attempt " + connectionAttempts + " started"
        );
        try {
            store = new ConfigStore(this);
            if (store.isConfigured()) {
                DeviceOwnerProtection.ensureUninstallBlocked(this);
            }
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            homePackages = loadHomePackages();
            launchablePackages = loadLaunchablePackages();
            interactive = powerManager != null && powerManager.isInteractive();
            refreshRuntimeConfiguration();
            lastTickElapsed = SystemClock.elapsedRealtime();
            registerReceivers();
            preferenceListener = (preferences, key) -> {
                if (ConfigStore.affectsRuntimeConfiguration(key)) {
                    handler.post(() -> {
                        boolean parentModeGestureChanged =
                                ConfigStore.isParentModeGesturePreference(key);
                        if (ConfigStore.isLanguagePreference(key)) {
                            AppLanguage.apply(this);
                            rebuildLocalizedOverlays();
                        }
                        refreshRuntimeConfiguration();
                        if (parentModeGestureChanged) {
                            rebuildBlockerForParentModeGesture();
                        }
                        evaluateNow();
                    });
                }
            };
            store.registerListener(preferenceListener);
            connected = true;
            handler.removeCallbacks(ticker);
            handler.post(ticker);
            DiagnosticLog.info(
                    this,
                    TAG,
                    "Connected; configured=" + configurationPresent
                            + ", enforcement=" + enforcementEnabled
                );
        } catch (RuntimeException exception) {
            DiagnosticLog.error(
                    this,
                    TAG,
                    "Connection attempt " + connectionAttempts + " failed",
                    exception
            );
            cleanupRuntimeRegistrations();
            if (connectionAttempts < 3) {
                handler.postDelayed(connector, connectionAttempts * 2_000L);
            }
        }
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
        CharSequence className = event.getClassName();
        String nextClass = className == null ? null : className.toString();
        boolean protectionWasActive = protectedScreenActive;
        if (nextPackage.equals(getPackageName())
                && MainActivity.class.getName().equals(nextClass)) {
            protectedScreenActive = false;
        } else if (!nextPackage.equals(getPackageName())) {
            if (RemovalProtectionPolicy.isSensitiveScreen(
                    nextPackage,
                    nextClass,
                    systemSettingsProtectionEnabled
            )) {
                protectedScreenActive = true;
            } else if (!RemovalProtectionPolicy.isProtectionPackage(
                    nextPackage,
                    systemSettingsProtectionEnabled
            )) {
                protectedScreenActive = false;
            }
        }
        if (protectionWasActive != protectedScreenActive) {
            if (protectedScreenActive) {
                protectionEligibleAtElapsed = SystemClock.elapsedRealtime()
                        + PROTECTION_STABILITY_MILLIS;
                DiagnosticLog.info(
                        this,
                        TAG,
                        "Sensitive screen detected; protection pending for package="
                                + nextPackage + ", class=" + nextClass
                );
                handler.postDelayed(this::evaluateNow, PROTECTION_STABILITY_MILLIS);
            } else {
                protectionEligibleAtElapsed = 0L;
                DiagnosticLog.info(
                        this,
                        TAG,
                        "Sensitive screen cleared"
                );
                evaluateNow();
            }
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
        DiagnosticLog.warning(this, TAG, "Service interrupted by Android", null);
        countedDuringPreviousInterval = false;
        flushPendingUsage();
        removeAllOverlays();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        AppLanguage.apply(this);
        boolean hadTimer = timerView != null;
        BlockReason reason = blockerReason;
        boolean restoreParentMode = parentModeActive;
        long warningMinutes = pendingWarningMinutes;
        String warningDay = pendingWarningDay;
        removeTimer();
        if (reason != null) {
            removeBlocker();
            pendingWarningMinutes = warningMinutes;
            pendingWarningDay = warningDay;
            showBlocker(reason);
            if (restoreParentMode && reason != BlockReason.RECOVERY) {
                activateParentMode(reason);
            }
        } else if (hadTimer) {
            evaluateNow();
        }
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
            parentModeGestureEnabled = true;
            systemSettingsProtectionEnabled = true;
            return;
        }
        configurationPresent = store.isConfigured();
        if (!configurationPresent) {
            protectedScreenActive = false;
            protectionEligibleAtElapsed = 0L;
        }
        enforcementEnabled = store.isEnforcementEnabled();
        targetScope = store.getScope();
        targetPackages = store.getSelectedPackages();
        dailyLimitMillis = store.getDailyLimitMillis();
        parentModeGestureEnabled = store.isParentModeGestureEnabled();
        systemSettingsProtectionEnabled = store.isSystemSettingsProtectionEnabled();
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

        boolean screenActive = powerManager != null
                && powerManager.isInteractive()
                && (keyguardManager == null || !keyguardManager.isKeyguardLocked())
                && !dreaming;
        interactive = screenActive;
        if (screenActive && store.isRecoveryModeRequested()) {
            countedDuringPreviousInterval = false;
            showBlocker(BlockReason.RECOVERY);
            if (blockerView != null) {
                store.clearRecoveryModeRequest();
            }
            return;
        }
        if (parentModeActive && blockerView != null) {
            countedDuringPreviousInterval = false;
            return;
        }
        if (configurationPresent
                && screenActive
                && RemovalProtectionPolicy.shouldBlock(
                        protectedScreenActive,
                        nowElapsed,
                        protectionEligibleAtElapsed,
                        store.isMaintenanceAllowed(System.currentTimeMillis())
                )) {
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
            timerView = view;
            try {
                windowManager.addView(view, params);
                recordOverlaySuccess();
            } catch (RuntimeException exception) {
                if (timerView == view) {
                    timerView = null;
                }
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
        if (blockerView != null && !blockerView.isAttachedToWindow()) {
            blockerView = null;
            blockerRoot = null;
            blockerPanel = null;
            blockerReason = null;
            parentModeActive = false;
        }
        if (blockerView != null && blockerReason == reason) {
            return;
        }
        if (blockerView != null) {
            removeBlocker();
        }
        if (!canAttemptOverlay()) {
            return;
        }

        ModeSwitchFrameLayout root = new ModeSwitchFrameLayout(this);
        root.setBackgroundColor(reason == BlockReason.RECOVERY ? 0xff0b3558 : Color.BLACK);
        root.setFocusable(false);
        root.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        boolean compact = getResources().getDisplayMetrics().widthPixels < dp(600);
        int panelPadding = compact ? dp(8) : dp(14);
        panel.setPadding(panelPadding, dp(8), panelPadding, dp(8));
        View initialFocus;
        if (reason == BlockReason.USAGE_WARNING) {
            initialFocus = renderUsageWarning(panel);
        } else if (reason == BlockReason.RECOVERY) {
            parentModeActive = true;
            initialFocus = renderParentActions(panel, reason);
        } else {
            initialFocus = renderChildPinPrompt(panel, reason);
        }

        ScrollView panelScroll = new ScrollView(this);
        panelScroll.setFillViewport(true);
        panelScroll.setClipToPadding(true);
        panelScroll.setPadding(0, dp(52), 0, dp(10));
        ScrollView.LayoutParams panelContentParams = new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        panelContentParams.gravity = Gravity.CENTER_VERTICAL;
        panelScroll.addView(panel, panelContentParams);
        int outerMargin = compact ? dp(16) : dp(40);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                Math.max(
                        1,
                        Math.min(
                                dp(560),
                                getResources().getDisplayMetrics().widthPixels - (2 * outerMargin)
                        )
                ),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
        );
        root.addView(panelScroll, panelParams);

        if (reason != BlockReason.USAGE_WARNING && parentModeGestureEnabled) {
            root.setParentModeAction(() -> activateParentMode(reason));
            View parentHotspot = new View(this);
            parentHotspot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            parentHotspot.setOnClickListener(view -> root.recordSecretTap());
            FrameLayout.LayoutParams hotspotParams = new FrameLayout.LayoutParams(
                    dp(56),
                    dp(56),
                    Gravity.TOP | Gravity.START
            );
            root.addView(parentHotspot, hotspotParams);
        }

        LanguageSwitcherView languageSwitcher = new LanguageSwitcherView(this, () -> {
        });
        FrameLayout.LayoutParams languageParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END
        );
        languageParams.setMargins(0, dp(16), dp(18), 0);
        root.addView(languageSwitcher, languageParams);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE
        );
        params.gravity = Gravity.TOP | Gravity.START;
        blockerView = root;
        blockerRoot = root;
        blockerPanel = panel;
        blockerReason = reason;
        try {
            windowManager.addView(root, params);
            if (initialFocus instanceof PinPadView) {
                PinPadView pinPad = (PinPadView) initialFocus;
                pinPad.post(pinPad::requestInitialFocus);
            } else {
                initialFocus.post(initialFocus::requestFocus);
            }
            recordOverlaySuccess();
            DiagnosticLog.info(this, TAG, "Blocker shown; reason=" + reason);
        } catch (RuntimeException exception) {
            root.cancelParentModeAction();
            if (blockerView == root) {
                blockerView = null;
                blockerRoot = null;
                blockerPanel = null;
                blockerReason = null;
                parentModeActive = false;
            }
            recordOverlayFailure("blocker", exception);
        }
    }

    private PinPadView renderChildPinPrompt(LinearLayout panel, BlockReason reason) {
        int generation = ++pinPromptGeneration;
        panel.removeAllViews();
        String titleText = reason == BlockReason.TIME_LIMIT
                ? getString(R.string.time_finished_title)
                : getString(R.string.settings_protection_title);
        String instructionText = reason == BlockReason.TIME_LIMIT
                ? getString(R.string.time_finished_instruction)
                : getString(R.string.settings_protection_instruction);
        TextView title = overlayText(titleText, 24f, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, wrapParams(dp(8)));
        TextView instructions = overlayText(instructionText, 16f, 0xffeeeeee);
        instructions.setGravity(Gravity.CENTER);
        panel.addView(instructions, wrapParams(dp(12)));

        final PinPadView[] holder = new PinPadView[1];
        holder[0] = new PinPadView(this, pin -> {
            holder[0].setBusy(true);
            backgroundExecutor.execute(() -> {
                boolean verified;
                try {
                    verified = store != null
                            && store.verifyAuthenticatorCode(pin, System.currentTimeMillis());
                } catch (RuntimeException exception) {
                    Log.e(TAG, "Unable to verify parent code", exception);
                    verified = false;
                }
                boolean result = verified;
                handler.post(() -> {
                    if (!connected
                            || blockerView == null
                            || generation != pinPromptGeneration) {
                        recoverStalePinPrompt(holder[0], reason, generation);
                        return;
                    }
                    if (result) {
                        if (reason == BlockReason.REMOVAL_PROTECTION) {
                            if (store.grantMaintenanceWindow()) {
                                removeBlocker();
                                evaluateNow();
                            }
                        } else {
                            grantExtension(store.getDefaultExtensionMinutes());
                        }
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

    private void activateParentMode(BlockReason reason) {
        if (blockerRoot == null || blockerPanel == null || reason == BlockReason.USAGE_WARNING) {
            return;
        }
        parentModeActive = true;
        blockerRoot.setBackgroundColor(0xff0b3558);
        PinPadView pinPad = renderParentPinPrompt(blockerPanel, reason);
        pinPad.post(pinPad::requestInitialFocus);
    }

    private PinPadView renderParentPinPrompt(LinearLayout panel, BlockReason reason) {
        int generation = ++pinPromptGeneration;
        panel.removeAllViews();
        TextView title = overlayText(getString(R.string.parent_mode_title), 22f, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, wrapParams(dp(6)));
        TextView instructions = overlayText(
                getString(R.string.parent_mode_instruction),
                15f,
                0xffe3f2fd
        );
        instructions.setGravity(Gravity.CENTER);
        panel.addView(instructions, wrapParams(dp(10)));

        final PinPadView[] holder = new PinPadView[1];
        holder[0] = new PinPadView(this, pin -> {
            holder[0].setBusy(true);
            backgroundExecutor.execute(() -> {
                boolean verified = false;
                try {
                    if (store != null) {
                        verified = store.verifyPin(pin)
                                || store.consumeEmergencyCode(pin, System.currentTimeMillis());
                    }
                } catch (RuntimeException exception) {
                    Log.e(TAG, "Unable to verify parent PIN", exception);
                }
                boolean result = verified;
                handler.post(() -> {
                    if (!connected || blockerView == null || generation != pinPromptGeneration) {
                        recoverStalePinPrompt(holder[0], reason, generation);
                        return;
                    }
                    if (result) {
                        renderParentActions(panel, reason);
                    } else {
                        holder[0].showError(getString(R.string.parent_pin_invalid));
                    }
                });
            });
        });
        panel.addView(holder[0], wrapParams(0));
        return holder[0];
    }

    private void recoverStalePinPrompt(
            PinPadView pinPad,
            BlockReason reason,
            int generation
    ) {
        DiagnosticLog.warning(
                this,
                TAG,
                "Discarded stale PIN result; reason=" + reason
                        + ", generation=" + generation
                        + ", currentGeneration=" + pinPromptGeneration,
                null
        );
        View staleRoot = pinPad == null ? null : pinPad.getRootView();
        if (staleRoot != null
                && staleRoot != blockerView
                && staleRoot.isAttachedToWindow()) {
            removeViewSafely(staleRoot);
        }
    }

    private View renderUsageWarning(LinearLayout panel) {
        pinPromptGeneration++;
        panel.removeAllViews();
        TextView title = overlayText(getString(R.string.usage_pause_title), 24f, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, wrapParams(dp(10)));

        TextView message = overlayText(
                getResources().getQuantityString(
                        R.plurals.usage_warning_message,
                        (int) pendingWarningMinutes,
                        pendingWarningMinutes
                ),
                18f,
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

    private View renderParentActions(LinearLayout panel, BlockReason reason) {
        pinPromptGeneration++;
        panel.removeAllViews();
        TextView title = overlayText(getString(R.string.parent_actions_title), 22f, Color.WHITE);
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
                protectionEligibleAtElapsed = 0L;
                removeBlocker();
                performGlobalAction(GLOBAL_ACTION_HOME);
            });
            panel.addView(home, buttonParams());

            Button back = overlayButton(getString(R.string.back));
            back.setOnClickListener(view -> returnToChildMode(reason));
            panel.addView(back, buttonParams());
            allow.post(allow::requestFocus);
            return allow;
        }

        title.setText(R.string.extension_prompt);
        Button firstChoice = null;
        LinearLayout choiceRow = null;
        for (int index = 0; index < ExtensionDurationPolicy.CHOICES_MINUTES.length; index++) {
            int minutes = ExtensionDurationPolicy.CHOICES_MINUTES[index];
            if (index % 2 == 0) {
                choiceRow = new LinearLayout(this);
                choiceRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                rowParams.bottomMargin = dp(4);
                panel.addView(choiceRow, rowParams);
            }
            Button choice = overlayButton(extensionLabel(minutes));
            choice.setOnClickListener(view -> grantExtension(minutes));
            choiceRow.addView(choice, choiceButtonParams());
            if (firstChoice == null) {
                firstChoice = choice;
            }
        }

        Button settings = overlayButton(getString(R.string.open_timer_settings));
        settings.setOnClickListener(view -> openParentSettings());
        panel.addView(settings, buttonParams());

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
        back.setOnClickListener(view -> returnToChildMode(reason));
        panel.addView(back, buttonParams());
        if (firstChoice != null) {
            firstChoice.post(firstChoice::requestFocus);
        }
        return firstChoice == null ? back : firstChoice;
    }

    private void openParentSettings() {
        if (store == null || !store.grantParentSettingsLaunch(System.currentTimeMillis())) {
            return;
        }
        removeAllOverlays();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(intent);
        } catch (RuntimeException exception) {
            DiagnosticLog.error(this, TAG, "Unable to open parent settings", exception);
        }
    }

    private void returnToChildMode(BlockReason reason) {
        if (blockerRoot == null || blockerPanel == null) {
            return;
        }
        parentModeActive = false;
        blockerRoot.setBackgroundColor(Color.BLACK);
        if (reason == BlockReason.RECOVERY) {
            removeBlocker();
            evaluateNow();
            return;
        }
        PinPadView pinPad = renderChildPinPrompt(blockerPanel, reason);
        pinPad.post(pinPad::requestInitialFocus);
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
        if (minutes == 60) {
            return getString(R.string.one_hour);
        }
        if (minutes == 90) {
            return getString(R.string.ninety_minutes);
        }
        if (minutes == 120) {
            return getString(R.string.two_hours);
        }
        return getResources().getQuantityString(
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
        boolean restoreParentMode = parentModeActive;
        removeBlocker();
        pendingWarningMinutes = warningMinutes;
        pendingWarningDay = warningDay;
        showBlocker(reason);
        if (restoreParentMode && reason != BlockReason.RECOVERY) {
            activateParentMode(reason);
        }
    }

    private void rebuildBlockerForParentModeGesture() {
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
                String action = intent.getAction();
                String systemDialogReason = Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)
                        ? intent.getStringExtra(SYSTEM_DIALOG_REASON_KEY)
                        : null;
                if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)) {
                    DiagnosticLog.info(
                            LimiterAccessibilityService.this,
                            TAG,
                            "System dialogs closed; reason=" + systemDialogReason
                    );
                }
                if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)
                        && RemovalProtectionPolicy.isHomeNavigationReason(systemDialogReason)
                        && blockerReason == BlockReason.REMOVAL_PROTECTION) {
                    DiagnosticLog.info(
                            LimiterAccessibilityService.this,
                            TAG,
                            "System navigation closed the protected screen"
                    );
                    protectedScreenActive = false;
                    protectionEligibleAtElapsed = 0L;
                    countedDuringPreviousInterval = false;
                    lastTickElapsed = SystemClock.elapsedRealtime();
                    removeBlocker();
                    return;
                }
                evaluateNow();
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    interactive = false;
                    countedDuringPreviousInterval = false;
                    flushPendingUsage();
                    removeAllOverlays();
                } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    interactive = true;
                    lastTickElapsed = SystemClock.elapsedRealtime();
                    evaluateNow();
                    handler.postDelayed(LimiterAccessibilityService.this::evaluateNow, 300L);
                    handler.postDelayed(LimiterAccessibilityService.this::evaluateNow, 1_500L);
                } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
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
        stateFilter.addAction(Intent.ACTION_USER_PRESENT);
        stateFilter.addAction(Intent.ACTION_DREAMING_STARTED);
        stateFilter.addAction(Intent.ACTION_DREAMING_STOPPED);
        stateFilter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerSystemReceiver(stateReceiver, stateFilter);

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
        boolean wasConnected = connected;
        connected = false;
        handler.removeCallbacks(connector);
        handler.removeCallbacks(ticker);
        flushPendingUsage();
        countedDuringPreviousInterval = false;
        removeAllOverlays();
        cleanupRuntimeRegistrations();
        if (wasConnected) {
            DiagnosticLog.info(this, TAG, "Disconnected");
        }
    }

    private void cleanupRuntimeRegistrations() {
        if (store != null && preferenceListener != null) {
            try {
                store.unregisterListener(preferenceListener);
            } catch (RuntimeException exception) {
                DiagnosticLog.warning(this, TAG, "Unable to remove settings listener", exception);
            }
        }
        preferenceListener = null;
        unregisterSafely(stateReceiver);
        stateReceiver = null;
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
        DiagnosticLog.error(
                this,
                TAG,
                "Unable to show " + overlayName + "; retry delayed " + delaySeconds + " seconds",
                exception
        );
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
            BlockReason removedReason = blockerReason;
            pinPromptGeneration++;
            removeViewSafely(blockerView);
            blockerView = null;
            if (blockerRoot != null) {
                blockerRoot.cancelParentModeAction();
            }
            blockerRoot = null;
            blockerPanel = null;
            blockerReason = null;
            parentModeActive = false;
            pendingWarningMinutes = 0L;
            pendingWarningDay = null;
            DiagnosticLog.info(this, TAG, "Blocker removed; reason=" + removedReason);
        }
    }

    private final class ModeSwitchFrameLayout extends FrameLayout {
        private static final long PARENT_HOLD_MILLIS = 8_000L;
        private static final long TAP_WINDOW_MILLIS = 4_000L;
        private static final int REQUIRED_TAPS = 7;

        private Runnable parentModeAction;
        private Runnable pendingHold;
        private int secretTapCount;
        private long firstSecretTapElapsed;

        private ModeSwitchFrameLayout(Context context) {
            super(context);
        }

        private void setParentModeAction(Runnable action) {
            parentModeAction = action;
        }

        private void recordSecretTap() {
            long now = SystemClock.elapsedRealtime();
            if (firstSecretTapElapsed == 0L || now - firstSecretTapElapsed > TAP_WINDOW_MILLIS) {
                firstSecretTapElapsed = now;
                secretTapCount = 0;
            }
            secretTapCount++;
            if (secretTapCount >= REQUIRED_TAPS) {
                secretTapCount = 0;
                firstSecretTapElapsed = 0L;
                runParentModeAction();
            }
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (event.getKeyCode() != KeyEvent.KEYCODE_BACK) {
                return super.dispatchKeyEvent(event);
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                cancelParentModeAction();
                pendingHold = this::runParentModeAction;
                handler.postDelayed(pendingHold, PARENT_HOLD_MILLIS);
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                cancelParentModeAction();
            }
            return true;
        }

        private void runParentModeAction() {
            pendingHold = null;
            if (parentModeAction != null && !parentModeActive) {
                parentModeAction.run();
            }
        }

        private void cancelParentModeAction() {
            if (pendingHold != null) {
                handler.removeCallbacks(pendingHold);
                pendingHold = null;
            }
        }
    }

    private enum BlockReason {
        TIME_LIMIT,
        REMOVAL_PROTECTION,
        USAGE_WARNING,
        RECOVERY
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
        button.setTextSize(14f);
        button.setMinHeight(dp(40));
        button.setSingleLine(false);
        button.setMaxLines(2);
        button.setPadding(dp(8), dp(2), dp(8), dp(2));
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
            view.animate().scaleX(1f).scaleY(1f).setDuration(0L).start();
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
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = dp(6);
        return params;
    }

    private LinearLayout.LayoutParams choiceButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
