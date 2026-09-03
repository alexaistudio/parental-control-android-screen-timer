package dev.tvtimer.app;

import android.app.DownloadManager;
import android.app.admin.DevicePolicyManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends LocalizedActivity {
    private static final String TAG = "ScreenTimerActivity";
    private static final String APK_MIME_TYPE = "application/vnd.android.package-archive";
    private static final long UPDATE_TIMEOUT_MILLIS = 15L * 60L * 1_000L;
    private static final String SUPPORT_ADDRESS = "TMoM4t1JsevXo42cRBiYue51NXrsjuGhqd";

    private ConfigStore store;
    private DownloadManager downloadManager;
    private DevicePolicyManager devicePolicyManager;
    private ScrollView scrollView;
    private LinearLayout content;
    private boolean adminUnlocked;
    private TextView serviceStatus;
    private TextView deviceAdminStatus;
    private Button deviceAdminButton;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
    private int screenGeneration;
    private boolean destroyed;
    private volatile long activeDownloadId = -1L;
    private volatile File activeDownloadFile;
    private volatile boolean updateReady;
    private boolean showingAuthenticatorQr;
    private boolean qrReturnsToSettings;
    private boolean setupQrAcknowledged;
    private boolean appSelectionOpen;
    private boolean accessibilitySettingsOpen;
    private boolean showingDiagnostics;
    private int diagnosticPage;
    private List<String> diagnosticPages;
    private Set<String> selectedPackagesDraft;
    private TextView appSelectionSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ConfigStore(this);
        downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        selectedPackagesDraft = store.getSelectedPackages();
        if (store.isConfigured()) {
            DeviceOwnerProtection.ensureUninstallBlocked(this);
            LauncherProfileManager.apply(this, store.getLauncherProfile());
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff121212);
        SystemBarInsets.apply(root);
        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(0xff121212);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = contentHorizontalPadding();
        content.setPadding(horizontalPadding, dp(16), horizontalPadding, dp(24));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int outerSpace = screenWidth < dp(600) ? dp(24) : dp(80);
        int menuWidth = Math.max(1, Math.min(dp(720), screenWidth - outerSpace));
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                menuWidth,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_HORIZONTAL
        );
        root.addView(scrollView, scrollParams);
        setContentView(root);
        renderCurrentScreen();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        backgroundExecutor.shutdownNow();
        updateExecutor.shutdownNow();
        if (!updateReady) {
            discardActiveDownload();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (showingDiagnostics) {
            showingDiagnostics = false;
            diagnosticPages = null;
            adminUnlocked = true;
            renderSettings();
            return;
        }
        if (showingAuthenticatorQr) {
            showingAuthenticatorQr = false;
            if (qrReturnsToSettings && store.isConfigured()) {
                adminUnlocked = true;
                renderSettings();
            } else {
                finish();
            }
            return;
        }
        if (store != null && store.isConfigured() && adminUnlocked) {
            adminUnlocked = false;
            renderLockedHome();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content == null) {
            return;
        }
        if (appSelectionOpen) {
            appSelectionOpen = false;
            selectedPackagesDraft = store.getSelectedPackages();
            updateAppSelectionSummary();
            return;
        }
        if (accessibilitySettingsOpen) {
            accessibilitySettingsOpen = false;
            DiagnosticLog.info(
                    this,
                    TAG,
                    "Returned from accessibility settings; enabled="
                            + isAccessibilityServiceEnabled()
            );
        }
        if (!store.isConfigured()) {
            adminUnlocked = false;
            if (setupQrAcknowledged) {
                renderSetup();
            } else if (!showingAuthenticatorQr) {
                renderAuthenticatorQr();
            }
        } else if (serviceStatus != null) {
            updateServiceStatus();
            updateDeviceAdminStatus();
            configureDeviceAdminButton();
        }
    }

    private void renderCurrentScreen() {
        if (!store.isConfigured()) {
            renderAuthenticatorQr();
        } else if (adminUnlocked) {
            renderSettings();
        } else {
            renderLockedHome();
        }
    }

    private void renderAuthenticatorQr() {
        clearScreen();
        showingAuthenticatorQr = true;
        qrReturnsToSettings = store.isConfigured();
        addTitle(getString(qrReturnsToSettings
                ? R.string.qr_current_title
                : R.string.qr_pair_title));
        addParagraph(getString(R.string.qr_instructions));
        String secret = store.getOrCreateAuthenticatorSecret();
        ImageView qr = new ImageView(this);
        int availableWidth = getResources().getDisplayMetrics().widthPixels
                - (2 * contentHorizontalPadding());
        int qrMaxSize = qrReturnsToSettings ? dp(280) : dp(220);
        int qrSize = Math.max(
                dp(120),
                Math.min(
                        qrMaxSize,
                        Math.min(availableWidth, getResources().getDisplayMetrics().heightPixels / 3)
                )
        );
        qr.setImageBitmap(QrCodeRenderer.render(TotpAuthenticator.provisioningUri(secret), qrSize));
        qr.setContentDescription(getString(R.string.qr_content_description));
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(qrSize, qrSize);
        qrParams.gravity = Gravity.CENTER_HORIZONTAL;
        qrParams.topMargin = dp(8);
        qrParams.bottomMargin = dp(12);
        content.addView(qr, qrParams);
        addNotice(getString(R.string.qr_code_notice), 0xffb2dfdb);
        Button done = addButton(qrReturnsToSettings
                ? getString(R.string.qr_done_settings)
                : getString(R.string.qr_done_setup));
        done.setOnClickListener(view -> {
            showingAuthenticatorQr = false;
            if (qrReturnsToSettings) {
                adminUnlocked = true;
                renderSettings();
            } else {
                setupQrAcknowledged = true;
                renderSetup();
            }
        });
        done.requestFocus();
    }

    private void renderSetup() {
        clearScreen();
        showingAuthenticatorQr = false;
        selectedPackagesDraft = store.getSelectedPackages();
        addTitle(getString(R.string.setup_title));
        if (store.hasUsbRecoveryNotice()) {
            addNotice(getString(R.string.usb_recovery_completed), 0xffffcc80);
        }
        addParagraph(getString(R.string.setup_intro));

        EditText pin = addInput(getString(R.string.pin_hint), true);
        EditText confirmation = addInput(getString(R.string.repeat_pin_hint), true);
        MinuteLimitControl limitControl = addMinuteLimitControl(
                Long.parseLong(getString(R.string.default_minutes))
        );

        RadioGroup scopeGroup = new RadioGroup(this);
        scopeGroup.setOrientation(LinearLayout.VERTICAL);
        RadioButton allApps = addRadio(scopeGroup, getString(R.string.scope_all));
        RadioButton selectedApps = addRadio(scopeGroup, getString(R.string.scope_selected));
        allApps.setChecked(true);
        addSection(scopeGroup);

        Button chooseApps = addButton(getString(R.string.choose_apps));
        chooseApps.setOnClickListener(view -> openAppSelection());
        appSelectionSummary = addParagraph("");
        updateAppSelectionSummary();
        chooseApps.setVisibility(View.GONE);
        appSelectionSummary.setVisibility(View.GONE);

        scopeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean visible = checkedId == selectedApps.getId();
            chooseApps.setVisibility(visible ? View.VISIBLE : View.GONE);
            appSelectionSummary.setVisibility(visible ? View.VISIBLE : View.GONE);
        });

        addNotice(getString(R.string.accessibility_notice), 0xffb2dfdb);
        CheckBox accessibilityConsent = new CheckBox(this);
        accessibilityConsent.setText(R.string.accessibility_consent);
        accessibilityConsent.setTextColor(Color.WHITE);
        accessibilityConsent.setTextSize(17f);
        applyRowFocus(accessibilityConsent);
        addSection(accessibilityConsent);
        addNotice(getString(R.string.usb_recovery_setup_notice), 0xffffcc80);

        Button save = addButton(getString(R.string.save_and_open_service));
        save.setOnClickListener(view -> {
            String value = pin.getText().toString();
            if (!PinHasher.isValidFormat(value)) {
                showError(getString(R.string.pin_format_error));
                pin.requestFocus();
                return;
            }
            if (!value.equals(confirmation.getText().toString())) {
                showError(getString(R.string.pin_mismatch));
                confirmation.requestFocus();
                return;
            }
            long dailyLimit = limitControl.getLimitMillis();
            String scope = selectedApps.isChecked() ? AppScope.SELECTED : AppScope.ALL;
            Set<String> selected = new HashSet<>(selectedPackagesDraft);
            if (AppScope.SELECTED.equals(scope) && selected.isEmpty()) {
                showError(getString(R.string.select_app_error));
                return;
            }
            if (!accessibilityConsent.isChecked()) {
                showError(getString(R.string.accessibility_consent_error));
                accessibilityConsent.requestFocus();
                return;
            }
            int generation = screenGeneration;
            setButtonBusy(save, true, getString(R.string.saving));
            backgroundExecutor.execute(() -> {
                try {
                    boolean saved = store.configure(value, dailyLimit, scope, selected);
                    postToScreen(generation, () -> {
                        if (!saved) {
                            setButtonBusy(save, false, getString(R.string.save_and_open_service));
                            showError(getString(R.string.settings_save_failed));
                            return;
                        }
                        DeviceOwnerProtection.ensureUninstallBlocked(this);
                        adminUnlocked = true;
                        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
                        renderSettings();
                        openAccessibilitySettings();
                    });
                } catch (RuntimeException exception) {
                    postToScreen(generation, () -> {
                        setButtonBusy(save, false, getString(R.string.save_and_open_service));
                        showError(getString(R.string.settings_invalid));
                    });
                }
            });
        });
    }

    private void renderLockedHome() {
        clearScreen();
        content.setPadding(dp(18), dp(10), dp(18), dp(10));
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = textView(getString(R.string.locked_title), 26f, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = matchWrapParams();
        titleParams.bottomMargin = dp(4);
        content.addView(title, titleParams);

        int generation = screenGeneration;
        final PinPadView[] holder = new PinPadView[1];
        holder[0] = new PinPadView(this, enteredPin -> {
            holder[0].setBusy(true);
            backgroundExecutor.execute(() -> {
                boolean verified;
                try {
                    verified = store.verifyParentCode(enteredPin, System.currentTimeMillis());
                } catch (RuntimeException exception) {
                    verified = false;
                }
                boolean result = verified;
                postToScreen(generation, () -> {
                    if (result) {
                        adminUnlocked = true;
                        renderSettings();
                    } else {
                        holder[0].showError(getString(R.string.parent_code_invalid));
                    }
                });
            });
        });
        addSection(holder[0]);
        scrollView.post(() -> {
            if (!destroyed && screenGeneration == generation && !adminUnlocked) {
                content.setMinimumHeight(scrollView.getHeight());
                content.setGravity(Gravity.CENTER);
                scrollView.scrollTo(0, 0);
            }
        });
    }

    private void renderSettings() {
        clearScreen();
        showingDiagnostics = false;
        selectedPackagesDraft = store.getSelectedPackages();
        addTitle(getString(R.string.settings_title));
        serviceStatus = addParagraph("");
        updateServiceStatus();

        Button accessibility = addButton(getString(R.string.open_accessibility_settings));
        accessibility.setOnClickListener(view -> openAccessibilitySettings());
        addNotice(getString(R.string.accessibility_restricted_hint), 0xffffcc80);
        Button appInfo = addButton(getString(R.string.open_app_info));
        appInfo.setOnClickListener(view -> openAppInfo());
        Button diagnostics = addButton(getString(R.string.show_diagnostic_log));
        diagnostics.setOnClickListener(view -> renderDiagnostics());

        addSubheading(getString(R.string.removal_protection_title));
        deviceAdminStatus = addParagraph("");
        deviceAdminButton = addButton(getString(R.string.enable_removal_protection));
        updateDeviceAdminStatus();
        configureDeviceAdminButton();
        if (!DeviceOwnerProtection.isDeviceOwner(this)) {
            addNotice(getString(R.string.device_owner_notice), 0xffffcc80);
        }

        addSubheading(getString(R.string.phone_code_title));
        addParagraph(getString(R.string.phone_code_info));
        Button showQr = addButton(getString(R.string.show_current_qr));
        showQr.setOnClickListener(view -> renderAuthenticatorQr());

        String day = DayKey.localDay(System.currentTimeMillis());
        ConfigStore.DayState dayState = store.getDayState(day);
        addParagraph(getString(
                R.string.used_today,
                LimitMath.formatCountdown(dayState.getUsedMillis())
        ));
        if (dayState.getBonusMillis() > 0L) {
            addParagraph(getString(
                    R.string.added_today,
                    LimitMath.formatCountdown(dayState.getBonusMillis())
            ));
        }

        CheckBox enforcement = new CheckBox(this);
        enforcement.setText(R.string.enable_limit);
        enforcement.setTextColor(Color.WHITE);
        enforcement.setTextSize(18f);
        enforcement.setChecked(store.isEnforcementEnabled());
        applyRowFocus(enforcement);
        addSection(enforcement);

        MinuteLimitControl limitControl = addMinuteLimitControl(
                store.getDailyLimitMillis() / 60_000L
        );

        addSubheading(getString(R.string.usage_warning_title));
        int storedWarningInterval = store.getUsageWarningIntervalMinutes();
        CheckBox usageWarning = new CheckBox(this);
        usageWarning.setText(R.string.usage_warning_enable);
        usageWarning.setTextColor(Color.WHITE);
        usageWarning.setTextSize(18f);
        usageWarning.setChecked(storedWarningInterval != UsageWarningPolicy.DISABLED);
        applyRowFocus(usageWarning);
        addSection(usageWarning);

        RadioGroup warningIntervalGroup = new RadioGroup(this);
        warningIntervalGroup.setOrientation(LinearLayout.VERTICAL);
        int selectedWarningInterval = storedWarningInterval == UsageWarningPolicy.DISABLED
                ? 10
                : storedWarningInterval;
        for (int minutes : UsageWarningPolicy.CHOICES_MINUTES) {
            addTaggedRadio(
                    warningIntervalGroup,
                    getResources().getQuantityString(
                            R.plurals.usage_warning_interval,
                            minutes,
                            minutes
                    ),
                    minutes,
                    selectedWarningInterval
            );
        }
        warningIntervalGroup.setVisibility(usageWarning.isChecked() ? View.VISIBLE : View.GONE);
        addSection(warningIntervalGroup);
        usageWarning.setOnCheckedChangeListener((button, checked) -> warningIntervalGroup.setVisibility(
                checked ? View.VISIBLE : View.GONE
        ));
        addNotice(getString(R.string.usage_warning_example), 0xffb2dfdb);

        addSubheading(getString(R.string.extension_title));
        RadioGroup extensionGroup = new RadioGroup(this);
        extensionGroup.setOrientation(LinearLayout.VERTICAL);
        addTaggedRadio(
                extensionGroup,
                getString(R.string.extension_ask_every_time),
                ExtensionDurationPolicy.ASK_EVERY_TIME,
                store.getDefaultExtensionMinutes()
        );
        for (int minutes : ExtensionDurationPolicy.CHOICES_MINUTES) {
            addTaggedRadio(
                    extensionGroup,
                    minutes == 60
                            ? getString(R.string.extension_auto_hour)
                            : getResources().getQuantityString(
                                    R.plurals.extension_auto_minutes,
                                    minutes,
                                    minutes
                            ),
                    minutes,
                    store.getDefaultExtensionMinutes()
            );
        }
        addSection(extensionGroup);

        addSubheading(getString(R.string.launcher_title));
        RadioGroup launcherGroup = new RadioGroup(this);
        launcherGroup.setOrientation(LinearLayout.VERTICAL);
        addTaggedRadio(launcherGroup, getString(R.string.launcher_timer), LauncherProfile.DEFAULT, store.getLauncherProfile());
        addTaggedRadio(launcherGroup, getString(R.string.launcher_calculator), LauncherProfile.CALCULATOR, store.getLauncherProfile());
        addTaggedRadio(launcherGroup, getString(R.string.launcher_media), LauncherProfile.MEDIA, store.getLauncherProfile());
        addTaggedRadio(launcherGroup, getString(R.string.launcher_clock), LauncherProfile.CLOCK, store.getLauncherProfile());
        addTaggedRadio(launcherGroup, getString(R.string.launcher_weather), LauncherProfile.WEATHER, store.getLauncherProfile());
        addTaggedRadio(launcherGroup, getString(R.string.launcher_notes), LauncherProfile.NOTES, store.getLauncherProfile());
        addTaggedRadio(launcherGroup, getString(R.string.launcher_calendar), LauncherProfile.CALENDAR, store.getLauncherProfile());
        addTaggedRadio(launcherGroup, getString(R.string.launcher_files), LauncherProfile.FILES, store.getLauncherProfile());
        addTaggedRadio(launcherGroup, getString(R.string.launcher_gallery), LauncherProfile.GALLERY, store.getLauncherProfile());
        addTaggedRadio(launcherGroup, getString(R.string.launcher_help), LauncherProfile.HELP, store.getLauncherProfile());
        addSection(launcherGroup);
        addNotice(getString(R.string.launcher_notice), 0xffb2dfdb);

        RadioGroup scopeGroup = new RadioGroup(this);
        scopeGroup.setOrientation(LinearLayout.VERTICAL);
        RadioButton allApps = addRadio(scopeGroup, getString(R.string.scope_all));
        RadioButton selectedApps = addRadio(scopeGroup, getString(R.string.scope_selected));
        if (AppScope.SELECTED.equals(store.getScope())) {
            selectedApps.setChecked(true);
        } else {
            allApps.setChecked(true);
        }
        addSection(scopeGroup);

        Button chooseApps = addButton(getString(R.string.choose_apps));
        chooseApps.setOnClickListener(view -> openAppSelection());
        appSelectionSummary = addParagraph("");
        updateAppSelectionSummary();
        boolean appsVisible = selectedApps.isChecked();
        chooseApps.setVisibility(appsVisible ? View.VISIBLE : View.GONE);
        appSelectionSummary.setVisibility(appsVisible ? View.VISIBLE : View.GONE);
        scopeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean visible = checkedId == selectedApps.getId();
            chooseApps.setVisibility(visible ? View.VISIBLE : View.GONE);
            appSelectionSummary.setVisibility(visible ? View.VISIBLE : View.GONE);
        });

        addSubheading(getString(R.string.change_pin_title));
        EditText newPin = addInput(getString(R.string.new_pin_hint), true);
        EditText newPinConfirmation = addInput(getString(R.string.repeat_new_pin_hint), true);

        Button save = addButton(getString(R.string.save_settings));
        save.setOnClickListener(view -> {
            long dailyLimit = limitControl.getLimitMillis();
            String scope = selectedApps.isChecked() ? AppScope.SELECTED : AppScope.ALL;
            Set<String> selected = new HashSet<>(selectedPackagesDraft);
            if (AppScope.SELECTED.equals(scope) && selected.isEmpty()) {
                showError(getString(R.string.select_app_error));
                return;
            }
            String replacementPin = newPin.getText().toString();
            if (!replacementPin.isEmpty()) {
                if (!PinHasher.isValidFormat(replacementPin)) {
                    showError(getString(R.string.new_pin_format_error));
                    return;
                }
                if (!replacementPin.equals(newPinConfirmation.getText().toString())) {
                    showError(getString(R.string.new_pin_mismatch));
                    return;
                }
            }
            boolean enforcementEnabled = enforcement.isChecked();
            int defaultExtensionMinutes = checkedTaggedInt(
                    extensionGroup,
                    ConfigStore.DEFAULT_EXTENSION_MINUTES
            );
            int usageWarningIntervalMinutes = usageWarning.isChecked()
                    ? checkedTaggedInt(warningIntervalGroup, 10)
                    : UsageWarningPolicy.DISABLED;
            String launcherProfile = checkedTaggedString(
                    launcherGroup,
                    LauncherProfile.DEFAULT
            );
            int generation = screenGeneration;
            setButtonBusy(save, true, getString(R.string.saving));
            backgroundExecutor.execute(() -> {
                try {
                    boolean settingsSaved = store.updateSettings(
                            dailyLimit,
                            scope,
                            selected,
                            enforcementEnabled,
                            defaultExtensionMinutes,
                            usageWarningIntervalMinutes,
                            launcherProfile
                    );
                    boolean pinSaved = replacementPin.isEmpty() || store.changePin(replacementPin);
                    postToScreen(generation, () -> {
                        if (!settingsSaved) {
                            setButtonBusy(save, false, getString(R.string.save_settings));
                            showError(getString(R.string.settings_save_failed));
                            return;
                        }
                        if (!pinSaved) {
                            setButtonBusy(save, false, getString(R.string.save_settings));
                            showError(getString(R.string.settings_saved_pin_failed));
                            return;
                        }
                        try {
                            LauncherProfileManager.apply(this, launcherProfile);
                        } catch (RuntimeException exception) {
                            setButtonBusy(save, false, getString(R.string.save_settings));
                            showError(getString(R.string.settings_saved_launcher_failed));
                            return;
                        }
                        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
                        renderSettings();
                    });
                } catch (RuntimeException exception) {
                    postToScreen(generation, () -> {
                        setButtonBusy(save, false, getString(R.string.save_settings));
                        showError(getString(R.string.settings_invalid));
                    });
                }
            });
        });

        addSubheading(getString(R.string.updates_title));
        TextView updateStatus = addParagraph(getString(
                R.string.installed_version,
                BuildConfig.VERSION_NAME
        ));
        updateStatus.setTypeface(Typeface.MONOSPACE);
        Button updateButton = addButton(getString(R.string.check_update));
        UpdateUi updateUi = new UpdateUi(screenGeneration, updateStatus, updateButton);
        updateButton.setOnClickListener(view -> checkForUpdate(updateUi));

        Button lock = addButton(getString(R.string.lock_settings));
        lock.setOnClickListener(view -> {
            adminUnlocked = false;
            renderLockedHome();
        });
        addNotice(getString(R.string.usb_recovery_settings_notice), 0xffffcc80);

        addSubheading(getString(R.string.support_title));
        addParagraph(getString(R.string.support_info));
        ImageView supportQr = new ImageView(this);
        int supportQrSize = Math.min(
                dp(260),
                getResources().getDisplayMetrics().widthPixels - (2 * contentHorizontalPadding())
        );
        supportQr.setImageBitmap(QrCodeRenderer.render(SUPPORT_ADDRESS, supportQrSize));
        supportQr.setContentDescription(getString(R.string.support_qr_description));
        LinearLayout.LayoutParams supportQrParams = new LinearLayout.LayoutParams(
                supportQrSize,
                supportQrSize
        );
        supportQrParams.gravity = Gravity.CENTER_HORIZONTAL;
        supportQrParams.bottomMargin = dp(8);
        content.addView(supportQr, supportQrParams);
        TextView supportAddress = addParagraph(SUPPORT_ADDRESS);
        supportAddress.setTypeface(Typeface.MONOSPACE);
        supportAddress.setTextIsSelectable(true);
        Button copyAddress = addButton(getString(R.string.copy_support_address));
        copyAddress.setOnClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("USDT TRC-20", SUPPORT_ADDRESS));
                Toast.makeText(this, R.string.support_address_copied, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openAppSelection() {
        appSelectionOpen = true;
        startActivity(new Intent(this, AppSelectionActivity.class));
    }

    private void updateAppSelectionSummary() {
        if (appSelectionSummary == null) {
            return;
        }
        int count = selectedPackagesDraft == null ? 0 : selectedPackagesDraft.size();
        appSelectionSummary.setText(count == 0
                ? getString(R.string.apps_not_selected)
                : getResources().getQuantityString(R.plurals.apps_selected, count, count));
    }

    private void updateServiceStatus() {
        if (serviceStatus != null) {
            serviceStatus.setText(isAccessibilityServiceEnabled()
                    ? R.string.service_enabled
                    : R.string.service_disabled);
            serviceStatus.setTextColor(isAccessibilityServiceEnabled() ? 0xffa5d6a7 : 0xffffcc80);
        }
    }

    private void updateDeviceAdminStatus() {
        if (deviceAdminStatus == null) {
            return;
        }
        if (DeviceOwnerProtection.isDeviceOwner(this)) {
            boolean blocked = DeviceOwnerProtection.ensureUninstallBlocked(this);
            deviceAdminStatus.setText(blocked
                    ? R.string.device_owner_blocked
                    : R.string.device_owner_unconfirmed);
            deviceAdminStatus.setTextColor(blocked ? 0xffa5d6a7 : 0xffff8a80);
            return;
        }
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN)) {
            deviceAdminStatus.setText(R.string.device_admin_unsupported);
            deviceAdminStatus.setTextColor(0xffffcc80);
            return;
        }
        boolean active = devicePolicyManager != null && devicePolicyManager.isAdminActive(
                new ComponentName(this, TimerDeviceAdminReceiver.class)
        );
        deviceAdminStatus.setText(active
                ? R.string.device_admin_enabled
                : R.string.device_admin_disabled);
        deviceAdminStatus.setTextColor(active ? 0xffa5d6a7 : 0xffffcc80);
    }

    private void configureDeviceAdminButton() {
        if (deviceAdminButton == null) {
            return;
        }
        deviceAdminButton.setEnabled(true);
        deviceAdminButton.setText(R.string.enable_removal_protection);
        deviceAdminButton.setOnClickListener(null);
        boolean supported = getPackageManager().hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN);
        boolean active = supported
                && devicePolicyManager != null
                && devicePolicyManager.isAdminActive(
                        new ComponentName(this, TimerDeviceAdminReceiver.class)
                );
        if (DeviceOwnerProtection.isDeviceOwner(this)) {
            deviceAdminButton.setEnabled(false);
            deviceAdminButton.setText(R.string.device_owner_protects);
            return;
        }
        if (!supported || active) {
            deviceAdminButton.setEnabled(false);
            deviceAdminButton.setText(active
                    ? R.string.protection_enabled
                    : R.string.feature_unavailable);
            return;
        }
        deviceAdminButton.setOnClickListener(view -> {
            store.grantMaintenanceWindow();
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(
                            DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                            new ComponentName(this, TimerDeviceAdminReceiver.class)
                    )
                    .putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            getString(R.string.device_admin_description)
                    );
            try {
                startActivity(intent);
            } catch (RuntimeException exception) {
                showError(getString(R.string.device_admin_open_failed));
            }
        });
    }

    private boolean isAccessibilityServiceEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabled == null) {
            return false;
        }
        ComponentName expected = new ComponentName(this, LimiterAccessibilityService.class);
        for (String flattened : enabled.split(":")) {
            ComponentName component = ComponentName.unflattenFromString(flattened);
            if (expected.equals(component)) {
                return true;
            }
        }
        return false;
    }

    private void openAccessibilitySettings() {
        store.grantMaintenanceWindow();
        accessibilitySettingsOpen = true;
        ComponentName component = new ComponentName(this, LimiterAccessibilityService.class);
        DiagnosticLog.info(this, TAG, "Opening accessibility service settings");
        try {
            Intent details = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                    .putExtra(Intent.EXTRA_COMPONENT_NAME, component);
            startActivity(details);
        } catch (RuntimeException directFailure) {
            DiagnosticLog.warning(
                    this,
                    TAG,
                    "Firmware rejected the service details screen; trying the service list",
                    directFailure
            );
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (RuntimeException fallbackFailure) {
                accessibilitySettingsOpen = false;
                DiagnosticLog.error(
                        this,
                        TAG,
                        "Firmware did not open accessibility settings",
                        fallbackFailure
                );
                showError(getString(R.string.accessibility_settings_open_failed));
            }
        }
    }

    private void openAppInfo() {
        store.grantMaintenanceWindow();
        try {
            Intent intent = new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(intent);
            DiagnosticLog.info(this, TAG, "Opened app info for restricted-settings permission");
        } catch (RuntimeException exception) {
            DiagnosticLog.error(this, TAG, "Firmware did not open app info", exception);
            showError(getString(R.string.app_info_open_failed));
        }
    }

    private void renderDiagnostics() {
        if (!showingDiagnostics || diagnosticPages == null) {
            DiagnosticLog.info(
                    this,
                    TAG,
                    "Diagnostic QR opened; accessibilityEnabled="
                            + isAccessibilityServiceEnabled()
            );
            diagnosticPages = DiagnosticLog.qrPages(this);
            diagnosticPage = 0;
        }
        showingDiagnostics = true;
        clearScreen();
        addTitle(getString(R.string.diagnostic_title));
        addParagraph(getString(R.string.diagnostic_instructions));
        int pageCount = diagnosticPages.size();
        diagnosticPage = Math.max(0, Math.min(diagnosticPage, pageCount - 1));
        addNotice(
                getString(R.string.diagnostic_page, diagnosticPage + 1, pageCount),
                0xffb2dfdb
        );
        String payload = "Android Screen Timer diagnostics "
                + (diagnosticPage + 1) + "/" + pageCount + "\n"
                + diagnosticPages.get(diagnosticPage);
        ImageView qr = new ImageView(this);
        int availableWidth = getResources().getDisplayMetrics().widthPixels
                - (2 * contentHorizontalPadding()) - dp(32);
        int qrSize = Math.max(
                dp(160),
                Math.min(dp(380), Math.min(availableWidth, getResources().getDisplayMetrics().heightPixels / 2))
        );
        qr.setImageBitmap(QrCodeRenderer.render(payload, qrSize));
        qr.setContentDescription(getString(R.string.diagnostic_qr_description));
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(qrSize, qrSize);
        qrParams.gravity = Gravity.CENTER_HORIZONTAL;
        qrParams.bottomMargin = dp(8);
        content.addView(qr, qrParams);

        if (diagnosticPage > 0) {
            Button previous = addButton(getString(R.string.previous_log_page));
            previous.setOnClickListener(view -> {
                diagnosticPage--;
                renderDiagnostics();
            });
        }
        if (diagnosticPage + 1 < pageCount) {
            Button next = addButton(getString(R.string.next_log_page));
            next.setOnClickListener(view -> {
                diagnosticPage++;
                renderDiagnostics();
            });
            next.requestFocus();
        }
        Button copy = addButton(getString(R.string.copy_diagnostic_log));
        copy.setOnClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText(
                        "Android Screen Timer diagnostics",
                        DiagnosticLog.snapshot(this)
                ));
                Toast.makeText(this, R.string.diagnostic_log_copied, Toast.LENGTH_SHORT).show();
            }
        });
        Button back = addButton(getString(R.string.back_to_settings));
        back.setOnClickListener(view -> {
            showingDiagnostics = false;
            diagnosticPages = null;
            renderSettings();
        });
        if (pageCount == 1) {
            back.requestFocus();
        }
    }

    private void checkForUpdate(UpdateUi ui) {
        if (activeDownloadId >= 0L) {
            discardActiveDownload();
        }
        setButtonBusy(ui.button, true, getString(R.string.checking));
        ui.status.setText(R.string.checking_latest_release);
        updateExecutor.execute(() -> {
            try {
                GithubUpdateClient.ReleaseInfo release = new GithubUpdateClient().fetchLatest();
                if (!VersionComparator.isNewer(release.version, BuildConfig.VERSION_NAME)) {
                    postToScreen(ui.generation, () -> {
                        ui.status.setText(getString(
                                R.string.latest_version_installed,
                                BuildConfig.VERSION_NAME
                        ));
                        resetUpdateButton(ui);
                    });
                    return;
                }
                postToScreen(ui.generation, () -> startUpdateDownload(release, ui));
            } catch (Exception exception) {
                Log.e(TAG, "Unable to check GitHub release", exception);
                postToScreen(ui.generation, () -> {
                    ui.status.setText(R.string.update_check_failed);
                    resetUpdateButton(ui);
                });
            }
        });
    }

    private void startUpdateDownload(GithubUpdateClient.ReleaseInfo release, UpdateUi ui) {
        if (downloadManager == null) {
            ui.status.setText(R.string.download_service_unavailable);
            resetUpdateButton(ui);
            return;
        }
        File downloads = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloads == null) {
            ui.status.setText(R.string.update_storage_unavailable);
            resetUpdateButton(ui);
            return;
        }
        String relativePath = "updates/" + release.assetName;
        File target = new File(downloads, relativePath);
        File parent = target.getParentFile();
        if ((parent != null && !parent.exists() && !parent.mkdirs())
                || (target.exists() && !target.delete())) {
            ui.status.setText(R.string.update_file_prepare_failed);
            resetUpdateButton(ui);
            return;
        }
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(release.downloadUrl))
                    .setTitle(getString(R.string.app_name) + " " + release.version)
                    .setDescription(getString(R.string.update_notification_description))
                    .setMimeType(APK_MIME_TYPE)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationInExternalFilesDir(
                            this,
                            Environment.DIRECTORY_DOWNLOADS,
                            relativePath
                    );
            long downloadId = downloadManager.enqueue(request);
            activeDownloadId = downloadId;
            activeDownloadFile = target;
            updateReady = false;
            ui.status.setText(getString(
                    R.string.update_downloading,
                    release.tag,
                    UpdateProgress.render(0L, 0L)
            ));
            updateExecutor.execute(() -> monitorDownload(downloadId, target, release, ui));
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to enqueue update", exception);
            ui.status.setText(R.string.download_start_failed);
            resetUpdateButton(ui);
        }
    }

    private void monitorDownload(
            long downloadId,
            File target,
            GithubUpdateClient.ReleaseInfo release,
            UpdateUi ui
    ) {
        int previousPercent = -2;
        long deadline = SystemClock.elapsedRealtime() + UPDATE_TIMEOUT_MILLIS;
        try {
            boolean complete = false;
            while (!Thread.currentThread().isInterrupted()
                    && downloadId == activeDownloadId
                    && !complete) {
                if (SystemClock.elapsedRealtime() >= deadline) {
                    throw new IOException("Update download timed out");
                }
                DownloadSnapshot snapshot = queryDownload(downloadId);
                if (snapshot.status == DownloadManager.STATUS_FAILED) {
                    throw new IOException("DownloadManager failure " + snapshot.reason);
                }
                if (snapshot.status == DownloadManager.STATUS_SUCCESSFUL) {
                    complete = true;
                }
                int percent = UpdateProgress.percent(snapshot.downloadedBytes, snapshot.totalBytes);
                if (percent != previousPercent) {
                    previousPercent = percent;
                    String progress = UpdateProgress.render(
                            snapshot.downloadedBytes,
                            snapshot.totalBytes
                    );
                    postToScreen(ui.generation, () -> ui.status.setText(getString(
                            R.string.update_downloading,
                            release.tag,
                            progress
                    )));
                }
                if (!complete) {
                    Thread.sleep(400L);
                }
            }
            if (downloadId != activeDownloadId || Thread.currentThread().isInterrupted()) {
                return;
            }
            UpdateVerifier.verify(this, target, release.digest, release.version);
            Uri installerUri = downloadManager.getUriForDownloadedFile(downloadId);
            if (installerUri == null) {
                throw new IOException("DownloadManager did not provide a content URI");
            }
            updateReady = true;
            postToScreen(ui.generation, () -> {
                ui.status.setText(getString(
                        R.string.update_downloaded,
                        release.tag,
                        UpdateProgress.render(1L, 1L)
                ));
                ui.button.setEnabled(true);
                ui.button.setText(R.string.install_update);
                ui.button.setOnClickListener(view -> installUpdate(downloadId, ui));
            });
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            Log.e(TAG, "Update download or verification failed", exception);
            if (downloadId == activeDownloadId) {
                discardActiveDownload();
            }
            postToScreen(ui.generation, () -> {
                ui.status.setText(R.string.update_rejected);
                resetUpdateButton(ui);
            });
        }
    }

    private DownloadSnapshot queryDownload(long downloadId) throws IOException {
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor cursor = downloadManager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) {
                throw new IOException("Download disappeared");
            }
            return new DownloadSnapshot(
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                    )),
                    cursor.getLong(cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                    ))
            );
        }
    }

    private void installUpdate(long downloadId, UpdateUi ui) {
        store.grantMaintenanceWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
            ui.status.setText(R.string.allow_unknown_source);
            try {
                startActivity(new Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())
                ));
            } catch (RuntimeException exception) {
                showError(getString(R.string.unknown_source_open_failed));
            }
            return;
        }
        Uri uri = downloadManager == null ? null : downloadManager.getUriForDownloadedFile(downloadId);
        if (uri == null) {
            ui.status.setText(R.string.update_file_missing);
            resetUpdateButton(ui);
            return;
        }
        try {
            Intent install = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, APK_MIME_TYPE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(install);
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to open package installer", exception);
            showError(getString(R.string.installer_open_failed));
        }
    }

    private void resetUpdateButton(UpdateUi ui) {
        ui.button.setEnabled(true);
        ui.button.setText(R.string.check_update);
        ui.button.setOnClickListener(view -> checkForUpdate(ui));
    }

    private synchronized void discardActiveDownload() {
        long downloadId = activeDownloadId;
        activeDownloadId = -1L;
        updateReady = false;
        if (downloadManager != null && downloadId >= 0L) {
            downloadManager.remove(downloadId);
        }
        File target = activeDownloadFile;
        activeDownloadFile = null;
        if (target != null && target.exists() && !target.delete()) {
            Log.w(TAG, "Unable to delete an obsolete update file");
        }
    }

    private void clearScreen() {
        screenGeneration++;
        content.removeAllViews();
        content.setMinimumHeight(0);
        content.setGravity(Gravity.TOP);
        int horizontalPadding = contentHorizontalPadding();
        content.setPadding(horizontalPadding, dp(16), horizontalPadding, dp(24));
        serviceStatus = null;
        deviceAdminStatus = null;
        deviceAdminButton = null;
        LanguageSwitcherView languageSwitcher = new LanguageSwitcherView(this, this::recreate);
        LinearLayout.LayoutParams languageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        languageParams.gravity = Gravity.END;
        languageParams.bottomMargin = dp(8);
        content.addView(languageSwitcher, languageParams);
        scrollView.scrollTo(0, 0);
        int generation = screenGeneration;
        scrollView.post(() -> {
            if (!destroyed && screenGeneration == generation) {
                scrollView.scrollTo(0, 0);
            }
        });
    }

    private void addTitle(String text) {
        TextView title = textView(text, 24f, Color.WHITE);
        title.setGravity(Gravity.START);
        LinearLayout.LayoutParams params = matchWrapParams();
        params.bottomMargin = dp(10);
        content.addView(title, params);
    }

    private TextView addSubheading(String text) {
        TextView heading = textView(text, 18f, Color.WHITE);
        LinearLayout.LayoutParams params = matchWrapParams();
        params.topMargin = dp(12);
        params.bottomMargin = dp(4);
        content.addView(heading, params);
        return heading;
    }

    private TextView addParagraph(String text) {
        TextView paragraph = textView(text, 15f, 0xffeeeeee);
        LinearLayout.LayoutParams params = matchWrapParams();
        params.bottomMargin = dp(7);
        content.addView(paragraph, params);
        return paragraph;
    }

    private void addNotice(String text, int color) {
        TextView notice = textView(text, 14f, color);
        notice.setPadding(dp(10), dp(7), dp(10), dp(7));
        notice.setBackgroundColor(0xff263238);
        LinearLayout.LayoutParams params = matchWrapParams();
        params.topMargin = dp(10);
        params.bottomMargin = dp(8);
        content.addView(notice, params);
    }

    private MinuteLimitControl addMinuteLimitControl(long initialMinutes) {
        addSubheading(getString(R.string.daily_limit_title));
        MinuteLimitControl control = new MinuteLimitControl(initialMinutes);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(12), dp(8), dp(12), dp(10));
        container.setBackgroundColor(0xff263238);

        control.valueView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams valueParams = matchWrapParams();
        valueParams.bottomMargin = dp(6);
        container.addView(control.valueView, valueParams);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        addLimitButton(buttons, "−15", () -> control.adjust(-15L));
        addLimitButton(buttons, "−1", () -> control.adjust(-1L));
        addLimitButton(buttons, "+1", () -> control.adjust(1L));
        addLimitButton(buttons, "+15", () -> control.adjust(15L));
        container.addView(buttons, matchWrapParams());
        addSection(container);
        return control;
    }

    private void addLimitButton(LinearLayout row, String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(15f);
        button.setAllCaps(false);
        button.setMinHeight(dp(44));
        applyTvButtonFocus(button);
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        row.addView(button, params);
    }

    private EditText addInput(String hint, boolean password) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(0xff9e9e9e);
        input.setTextColor(Color.WHITE);
        input.setTextSize(15f);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER
                | (password ? InputType.TYPE_NUMBER_VARIATION_PASSWORD : InputType.TYPE_NUMBER_VARIATION_NORMAL));
        input.setMaxWidth(dp(420));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        params.gravity = Gravity.START;
        params.bottomMargin = dp(10);
        content.addView(input, params);
        return input;
    }

    private RadioButton addRadio(RadioGroup group, String text) {
        RadioButton button = new RadioButton(this);
        button.setId(View.generateViewId());
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15f);
        button.setMinHeight(dp(42));
        button.setPadding(dp(8), 0, dp(8), 0);
        applyRowFocus(button);
        group.addView(button, matchWrapParams());
        return button;
    }

    private void addTaggedRadio(
            RadioGroup group,
            String text,
            int value,
            int selectedValue
    ) {
        RadioButton button = addRadio(group, text);
        button.setTag(value);
        button.setChecked(value == selectedValue);
    }

    private void addTaggedRadio(
            RadioGroup group,
            String text,
            String value,
            String selectedValue
    ) {
        RadioButton button = addRadio(group, text);
        button.setTag(value);
        button.setChecked(value.equals(selectedValue));
    }

    private int checkedTaggedInt(RadioGroup group, int fallback) {
        View checked = group.findViewById(group.getCheckedRadioButtonId());
        return checked != null && checked.getTag() instanceof Integer
                ? (Integer) checked.getTag()
                : fallback;
    }

    private String checkedTaggedString(RadioGroup group, String fallback) {
        View checked = group.findViewById(group.getCheckedRadioButtonId());
        return checked != null && checked.getTag() instanceof String
                ? (String) checked.getTag()
                : fallback;
    }

    private Button addButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(15f);
        button.setMinHeight(dp(46));
        button.setMaxWidth(dp(620));
        button.setSingleLine(false);
        button.setMaxLines(2);
        button.setPadding(dp(12), dp(4), dp(12), dp(4));
        button.setAllCaps(false);
        applyTvButtonFocus(button);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(8);
        params.bottomMargin = dp(4);
        content.addView(button, params);
        return button;
    }

    private void addSection(View view) {
        LinearLayout.LayoutParams params = matchWrapParams();
        params.bottomMargin = dp(8);
        content.addView(view, params);
    }

    private TextView textView(String text, float size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.12f);
        return view;
    }

    private void applyTvButtonFocus(Button button) {
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
    }

    private void applyRowFocus(View view) {
        view.setOnFocusChangeListener((focusedView, hasFocus) -> {
            focusedView.setBackgroundColor(hasFocus ? 0xff455a64 : Color.TRANSPARENT);
            focusedView.animate().scaleX(1f).scaleY(1f).setDuration(0L).start();
        });
    }

    private void setButtonBusy(Button button, boolean busy, String text) {
        button.setEnabled(!busy);
        button.setText(text);
    }

    private void postToScreen(int generation, Runnable action) {
        runOnUiThread(() -> {
            if (!destroyed && screenGeneration == generation) {
                action.run();
            }
        });
    }

    private LinearLayout.LayoutParams matchWrapParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int contentHorizontalPadding() {
        return getResources().getDisplayMetrics().widthPixels < dp(600) ? dp(12) : dp(20);
    }

    private static final class UpdateUi {
        private final int generation;
        private final TextView status;
        private final Button button;

        private UpdateUi(int generation, TextView status, Button button) {
            this.generation = generation;
            this.status = status;
            this.button = button;
        }
    }

    private static final class DownloadSnapshot {
        private final int status;
        private final int reason;
        private final long downloadedBytes;
        private final long totalBytes;

        private DownloadSnapshot(int status, int reason, long downloadedBytes, long totalBytes) {
            this.status = status;
            this.reason = reason;
            this.downloadedBytes = downloadedBytes;
            this.totalBytes = totalBytes;
        }
    }

    private final class MinuteLimitControl {
        private final TextView valueView;
        private long minutes;

        private MinuteLimitControl(long initialMinutes) {
            minutes = LimitMath.adjustDailyMinutes(initialMinutes, 0L);
            valueView = textView("", 24f, Color.WHITE);
            valueView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            updateText();
        }

        private void adjust(long delta) {
            minutes = LimitMath.adjustDailyMinutes(minutes, delta);
            updateText();
        }

        private long getLimitMillis() {
            return minutes * 60_000L;
        }

        private void updateText() {
            valueView.setText(getString(R.string.minutes_short, minutes));
        }
    }
}
