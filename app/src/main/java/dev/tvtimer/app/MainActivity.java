package dev.tvtimer.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String TAG = "TVTimerActivity";
    private static final String APK_MIME_TYPE = "application/vnd.android.package-archive";
    private static final long UPDATE_TIMEOUT_MILLIS = 15L * 60L * 1_000L;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ConfigStore(this);
        downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        if (store.isConfigured()) {
            DeviceOwnerProtection.ensureUninstallBlocked(this);
            LauncherProfileManager.apply(this, store.getLauncherProfile());
        }

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(0xff121212);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(40), dp(28), dp(40), dp(40));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        setContentView(scrollView);
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
        if (!store.isConfigured()) {
            adminUnlocked = false;
            if (setupQrAcknowledged) {
                renderSetup();
            } else if (!showingAuthenticatorQr) {
                renderAuthenticatorQr(false);
            }
        } else if (serviceStatus != null) {
            updateServiceStatus();
            updateDeviceAdminStatus();
            configureDeviceAdminButton();
        }
    }

    private void renderCurrentScreen() {
        if (!store.isConfigured()) {
            renderAuthenticatorQr(false);
        } else if (adminUnlocked) {
            renderSettings();
        } else {
            renderLockedHome();
        }
    }

    private void renderAuthenticatorQr(boolean regenerated) {
        clearScreen();
        showingAuthenticatorQr = true;
        qrReturnsToSettings = store.isConfigured();
        addTitle(regenerated ? "Новый код с телефона" : "Привязка телефона");
        addParagraph(
                "Откройте на телефоне Google Authenticator, Microsoft Authenticator, Aegis "
                        + "или другое TOTP-приложение и отсканируйте QR-код. QR уникален для "
                        + "этой установки и никуда не отправляется."
        );
        String secret = regenerated
                ? store.regenerateAuthenticatorSecret()
                : store.getOrCreateAuthenticatorSecret();
        ImageView qr = new ImageView(this);
        int qrSize = Math.min(dp(360), getResources().getDisplayMetrics().heightPixels / 2);
        qr.setImageBitmap(QrCodeRenderer.render(TotpAuthenticator.provisioningUri(secret), qrSize));
        qr.setContentDescription("QR-код для привязки приложения-аутентификатора");
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(qrSize, qrSize);
        qrParams.gravity = Gravity.CENTER_HORIZONTAL;
        qrParams.topMargin = dp(8);
        qrParams.bottomMargin = dp(12);
        content.addView(qr, qrParams);
        addNotice(
                "Код в телефоне меняется каждые 30 секунд. Телевизор принимает текущий и "
                        + "предыдущие коды в пределах 5 минут без сохранения списка кодов в памяти.",
                0xffb2dfdb
        );
        Button done = addButton(qrReturnsToSettings
                ? "QR отсканирован — вернуться в настройки"
                : "QR отсканирован — продолжить настройку");
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
        addTitle("TV Timer — первоначальная настройка");
        if (store.hasUsbRecoveryNotice()) {
            addNotice("USB-восстановление выполнено: прежний PIN и настройки удалены, защита выключена.", 0xffffcc80);
        }
        addParagraph("Задайте резервный PIN и дневное время. Для родительского доступа можно использовать PIN или меняющийся код с телефона.");

        EditText pin = addInput("PIN: 4–8 цифр", true);
        EditText confirmation = addInput("Повторите PIN", true);
        MinuteLimitControl limitControl = addMinuteLimitControl(
                Long.parseLong(getString(R.string.default_minutes))
        );

        RadioGroup scopeGroup = new RadioGroup(this);
        scopeGroup.setOrientation(LinearLayout.VERTICAL);
        RadioButton allApps = addRadio(scopeGroup, "Весь телевизор и все приложения");
        RadioButton selectedApps = addRadio(scopeGroup, "Только выбранные приложения");
        allApps.setChecked(true);
        addSection(scopeGroup);

        TextView appHeading = addSubheading("Приложения");
        LinearLayout appList = new LinearLayout(this);
        appList.setOrientation(LinearLayout.VERTICAL);
        AppCheckState appChecks = populateAppChecksAsync(appList, Collections.emptySet());
        addSection(appList);
        appHeading.setVisibility(View.GONE);
        appList.setVisibility(View.GONE);

        scopeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean visible = checkedId == selectedApps.getId();
            appHeading.setVisibility(visible ? View.VISIBLE : View.GONE);
            appList.setVisibility(visible ? View.VISIBLE : View.GONE);
        });

        addNotice(
                "Для работы нужно вручную включить службу специальных возможностей. Она получает только имя активного приложения, не читает текст окон, не использует сеть и показывает таймер поверх изображения.",
                0xffb2dfdb
        );
        CheckBox accessibilityConsent = new CheckBox(this);
        accessibilityConsent.setText("Я понимаю назначение службы и разрешаю определять активное приложение для применения лимита");
        accessibilityConsent.setTextColor(Color.WHITE);
        accessibilityConsent.setTextSize(17f);
        applyRowFocus(accessibilityConsent);
        addSection(accessibilityConsent);
        addNotice(
                "Аварийный сброс: подключение USB-флешки очищает PIN и выключает защиту. На некоторых прошивках события от USB-клавиатур и приёмников приложению не передаются.",
                0xffffcc80
        );

        Button save = addButton("Сохранить и открыть включение службы");
        save.setOnClickListener(view -> {
            String value = pin.getText().toString();
            if (!PinHasher.isValidFormat(value)) {
                showError("PIN должен содержать от 4 до 8 цифр");
                pin.requestFocus();
                return;
            }
            if (!value.equals(confirmation.getText().toString())) {
                showError("PIN-коды не совпадают");
                confirmation.requestFocus();
                return;
            }
            long dailyLimit = limitControl.getLimitMillis();
            String scope = selectedApps.isChecked() ? AppScope.SELECTED : AppScope.ALL;
            if (AppScope.SELECTED.equals(scope) && !appChecks.loaded) {
                showError("Подождите, пока загрузится список приложений");
                return;
            }
            Set<String> selected = checkedPackages(appChecks.checks);
            if (AppScope.SELECTED.equals(scope) && selected.isEmpty()) {
                showError("Выберите хотя бы одно приложение");
                return;
            }
            if (!accessibilityConsent.isChecked()) {
                showError("Подтвердите использование службы специальных возможностей");
                accessibilityConsent.requestFocus();
                return;
            }
            int generation = screenGeneration;
            setButtonBusy(save, true, "Сохранение…");
            backgroundExecutor.execute(() -> {
                try {
                    boolean saved = store.configure(value, dailyLimit, scope, selected);
                    postToScreen(generation, () -> {
                        if (!saved) {
                            setButtonBusy(save, false, "Сохранить и открыть включение службы");
                            showError("Не удалось сохранить настройки");
                            return;
                        }
                        DeviceOwnerProtection.ensureUninstallBlocked(this);
                        adminUnlocked = true;
                        Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show();
                        renderSettings();
                        openAccessibilitySettings();
                    });
                } catch (RuntimeException exception) {
                    postToScreen(generation, () -> {
                        setButtonBusy(save, false, "Сохранить и открыть включение службы");
                        showError("Проверьте введённые настройки");
                    });
                }
            });
        });
    }

    private void renderLockedHome() {
        clearScreen();
        content.setPadding(dp(18), dp(10), dp(18), dp(10));
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = textView("TV Timer — код родителя", 26f, Color.WHITE);
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
                        holder[0].showError("Неверный PIN или код");
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
        addTitle("Настройки TV Timer");
        serviceStatus = addParagraph("");
        updateServiceStatus();

        Button accessibility = addButton("Открыть системную настройку службы");
        accessibility.setOnClickListener(view -> openAccessibilitySettings());

        addSubheading("Защита удаления");
        deviceAdminStatus = addParagraph("");
        deviceAdminButton = addButton("Включить усиленную защиту удаления");
        updateDeviceAdminStatus();
        configureDeviceAdminButton();
        if (!DeviceOwnerProtection.isDeviceOwner(this)) {
            addNotice(
                    "Для гарантированного запрета удаления нужен режим Device Owner. Он включается "
                            + "через ADB на чистом профиле; точная команда указана в README.",
                    0xffffcc80
            );
        }

        addSubheading("Код с телефона");
        addParagraph("TOTP-код меняется каждые 30 секунд и принимается в течение 5 минут. Старые коды не накапливаются в памяти.");
        Button replaceQr = addButton("Создать новый QR-код");
        replaceQr.setOnClickListener(view -> renderAuthenticatorQr(true));

        String day = DayKey.localDay(System.currentTimeMillis());
        ConfigStore.DayState dayState = store.getDayState(day);
        addParagraph("Использовано сегодня: " + LimitMath.formatCountdown(dayState.getUsedMillis()));
        if (dayState.getBonusMillis() > 0L) {
            addParagraph("Добавлено сегодня: " + LimitMath.formatCountdown(dayState.getBonusMillis()));
        }

        CheckBox enforcement = new CheckBox(this);
        enforcement.setText("Применять ограничение");
        enforcement.setTextColor(Color.WHITE);
        enforcement.setTextSize(18f);
        enforcement.setChecked(store.isEnforcementEnabled());
        applyRowFocus(enforcement);
        addSection(enforcement);

        MinuteLimitControl limitControl = addMinuteLimitControl(
                store.getDailyLimitMillis() / 60_000L
        );

        addSubheading("Продление после кода родителя");
        RadioGroup extensionGroup = new RadioGroup(this);
        extensionGroup.setOrientation(LinearLayout.VERTICAL);
        addTaggedRadio(
                extensionGroup,
                "Спрашивать каждый раз: 10, 15, 20, 30, 40 минут или 1 час",
                ExtensionDurationPolicy.ASK_EVERY_TIME,
                store.getDefaultExtensionMinutes()
        );
        for (int minutes : ExtensionDurationPolicy.CHOICES_MINUTES) {
            addTaggedRadio(
                    extensionGroup,
                    minutes == 60 ? "Сразу продолжать на 1 час" : "Сразу продолжать на " + minutes + " минут",
                    minutes,
                    store.getDefaultExtensionMinutes()
            );
        }
        addSection(extensionGroup);

        addSubheading("Название и иконка в меню телевизора");
        RadioGroup launcherGroup = new RadioGroup(this);
        launcherGroup.setOrientation(LinearLayout.VERTICAL);
        addTaggedRadio(launcherGroup, "TV Timer", LauncherProfile.DEFAULT, store.getLauncherProfile());
        addTaggedRadio(launcherGroup, "Калькулятор", LauncherProfile.CALCULATOR, store.getLauncherProfile());
        addTaggedRadio(launcherGroup, "Медиа-служба", LauncherProfile.MEDIA, store.getLauncherProfile());
        addSection(launcherGroup);
        addNotice(
                "Маскировка меняет плитку в launcher, но не скрывает приложение из системного списка. Защиту от удаления обеспечивает код и Device Owner.",
                0xffb2dfdb
        );

        RadioGroup scopeGroup = new RadioGroup(this);
        scopeGroup.setOrientation(LinearLayout.VERTICAL);
        RadioButton allApps = addRadio(scopeGroup, "Весь телевизор и все приложения");
        RadioButton selectedApps = addRadio(scopeGroup, "Только выбранные приложения");
        if (AppScope.SELECTED.equals(store.getScope())) {
            selectedApps.setChecked(true);
        } else {
            allApps.setChecked(true);
        }
        addSection(scopeGroup);

        TextView appHeading = addSubheading("Приложения");
        LinearLayout appList = new LinearLayout(this);
        appList.setOrientation(LinearLayout.VERTICAL);
        AppCheckState appChecks = populateAppChecksAsync(appList, store.getSelectedPackages());
        addSection(appList);
        boolean appsVisible = selectedApps.isChecked();
        appHeading.setVisibility(appsVisible ? View.VISIBLE : View.GONE);
        appList.setVisibility(appsVisible ? View.VISIBLE : View.GONE);
        scopeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean visible = checkedId == selectedApps.getId();
            appHeading.setVisibility(visible ? View.VISIBLE : View.GONE);
            appList.setVisibility(visible ? View.VISIBLE : View.GONE);
        });

        addSubheading("Смена PIN (необязательно)");
        EditText newPin = addInput("Новый PIN", true);
        EditText newPinConfirmation = addInput("Повторите новый PIN", true);

        Button save = addButton("Сохранить настройки");
        save.setOnClickListener(view -> {
            long dailyLimit = limitControl.getLimitMillis();
            String scope = selectedApps.isChecked() ? AppScope.SELECTED : AppScope.ALL;
            if (AppScope.SELECTED.equals(scope) && !appChecks.loaded) {
                showError("Подождите, пока загрузится список приложений");
                return;
            }
            Set<String> selected = checkedPackages(appChecks.checks);
            if (AppScope.SELECTED.equals(scope) && selected.isEmpty()) {
                showError("Выберите хотя бы одно приложение");
                return;
            }
            String replacementPin = newPin.getText().toString();
            if (!replacementPin.isEmpty()) {
                if (!PinHasher.isValidFormat(replacementPin)) {
                    showError("Новый PIN должен содержать от 4 до 8 цифр");
                    return;
                }
                if (!replacementPin.equals(newPinConfirmation.getText().toString())) {
                    showError("Новые PIN-коды не совпадают");
                    return;
                }
            }
            boolean enforcementEnabled = enforcement.isChecked();
            int defaultExtensionMinutes = checkedTaggedInt(
                    extensionGroup,
                    ConfigStore.DEFAULT_EXTENSION_MINUTES
            );
            String launcherProfile = checkedTaggedString(
                    launcherGroup,
                    LauncherProfile.DEFAULT
            );
            int generation = screenGeneration;
            setButtonBusy(save, true, "Сохранение…");
            backgroundExecutor.execute(() -> {
                try {
                    boolean settingsSaved = store.updateSettings(
                            dailyLimit,
                            scope,
                            selected,
                            enforcementEnabled,
                            defaultExtensionMinutes,
                            launcherProfile
                    );
                    boolean pinSaved = replacementPin.isEmpty() || store.changePin(replacementPin);
                    postToScreen(generation, () -> {
                        if (!settingsSaved) {
                            setButtonBusy(save, false, "Сохранить настройки");
                            showError("Не удалось сохранить настройки");
                            return;
                        }
                        if (!pinSaved) {
                            setButtonBusy(save, false, "Сохранить настройки");
                            showError("Настройки сохранены, но PIN изменить не удалось");
                            return;
                        }
                        try {
                            LauncherProfileManager.apply(this, launcherProfile);
                        } catch (RuntimeException exception) {
                            setButtonBusy(save, false, "Сохранить настройки");
                            showError("Настройки сохранены, но launcher не применил новую иконку");
                            return;
                        }
                        Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show();
                        renderSettings();
                    });
                } catch (RuntimeException exception) {
                    postToScreen(generation, () -> {
                        setButtonBusy(save, false, "Сохранить настройки");
                        showError("Проверьте введённые настройки");
                    });
                }
            });
        });

        addSubheading("Обновления");
        TextView updateStatus = addParagraph("Установлена версия " + BuildConfig.VERSION_NAME);
        updateStatus.setTypeface(Typeface.MONOSPACE);
        Button updateButton = addButton("Проверить обновление");
        UpdateUi updateUi = new UpdateUi(screenGeneration, updateStatus, updateButton);
        updateButton.setOnClickListener(view -> checkForUpdate(updateUi));

        Button lock = addButton("Заблокировать настройки");
        lock.setOnClickListener(view -> {
            adminUnlocked = false;
            renderLockedHome();
        });
        addNotice(
                "USB-флешка — аварийный ключ: при подключении локальные PIN и настройки удаляются, а оверлей снимается.",
                0xffffcc80
        );
    }

    private AppCheckState populateAppChecksAsync(LinearLayout container, Set<String> selected) {
        AppCheckState state = new AppCheckState();
        TextView loading = textView("Загрузка списка приложений…", 16f, 0xffb0bec5);
        container.addView(loading, matchWrapParams());
        Set<String> safeSelected = selected == null
                ? Collections.emptySet()
                : new HashSet<>(selected);
        int generation = screenGeneration;
        backgroundExecutor.execute(() -> {
            AppLoadResult result = loadLaunchableApps(safeSelected);
            postToScreen(generation, () -> {
                container.removeAllViews();
                for (InstalledApp app : result.apps) {
                    CheckBox checkBox = new CheckBox(this);
                    checkBox.setText(getString(R.string.app_entry_format, app.label, app.packageName));
                    checkBox.setTextColor(Color.WHITE);
                    checkBox.setTextSize(17f);
                    checkBox.setPadding(dp(8), dp(5), dp(8), dp(5));
                    checkBox.setChecked(safeSelected.contains(app.packageName));
                    applyRowFocus(checkBox);
                    container.addView(checkBox, matchWrapParams());
                    state.checks.put(app.packageName, checkBox);
                }
                if (state.checks.isEmpty()) {
                    TextView empty = textView("Запускаемые приложения не найдены", 16f, 0xffffcc80);
                    container.addView(empty, matchWrapParams());
                }
                state.loaded = true;
                if (result.restricted) {
                    Toast.makeText(
                            this,
                            "Прошивка ограничила чтение списка приложений",
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
        });
        return state;
    }

    private AppLoadResult loadLaunchableApps(Set<String> previouslySelected) {
        Map<String, InstalledApp> apps = new LinkedHashMap<>();
        boolean restricted = !queryApps(Intent.CATEGORY_LEANBACK_LAUNCHER, apps);
        restricted |= !queryApps(Intent.CATEGORY_LAUNCHER, apps);
        for (String packageName : previouslySelected) {
            if (!apps.containsKey(packageName)) {
                apps.put(packageName, new InstalledApp(packageName, packageName + " (сейчас не найдено)"));
            }
        }
        List<InstalledApp> result = new ArrayList<>(apps.values());
        Collections.sort(result, (first, second) -> first.label
                .toLowerCase(Locale.ROOT)
                .compareTo(second.label.toLowerCase(Locale.ROOT)));
        return new AppLoadResult(result, restricted);
    }

    @SuppressWarnings("deprecation")
    private boolean queryApps(String category, Map<String, InstalledApp> destination) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(category);
        PackageManager packageManager = getPackageManager();
        try {
            for (ResolveInfo resolveInfo : packageManager.queryIntentActivities(intent, 0)) {
                if (resolveInfo.activityInfo == null) {
                    continue;
                }
                String packageName = resolveInfo.activityInfo.packageName;
                if (getPackageName().equals(packageName)) {
                    continue;
                }
                CharSequence loadedLabel = resolveInfo.loadLabel(packageManager);
                String label = loadedLabel == null ? packageName : loadedLabel.toString();
                destination.put(packageName, new InstalledApp(packageName, label));
            }
            return true;
        } catch (SecurityException exception) {
            return false;
        }
    }

    private Set<String> checkedPackages(Map<String, CheckBox> checks) {
        Set<String> selected = new HashSet<>();
        for (Map.Entry<String, CheckBox> entry : checks.entrySet()) {
            if (entry.getValue().isChecked()) {
                selected.add(entry.getKey());
            }
        }
        return selected;
    }

    private void updateServiceStatus() {
        if (serviceStatus != null) {
            serviceStatus.setText(isAccessibilityServiceEnabled()
                    ? "Служба контроля: включена"
                    : "Служба контроля: выключена — лимит пока не применяется");
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
                    ? "Device Owner: удаление TV Timer системно запрещено"
                    : "Device Owner активен, но запрет удаления не подтвердился");
            deviceAdminStatus.setTextColor(blocked ? 0xffa5d6a7 : 0xffff8a80);
            return;
        }
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN)) {
            deviceAdminStatus.setText("Прошивка не поддерживает администраторов устройства.");
            deviceAdminStatus.setTextColor(0xffffcc80);
            return;
        }
        boolean active = devicePolicyManager != null && devicePolicyManager.isAdminActive(
                new ComponentName(this, TimerDeviceAdminReceiver.class)
        );
        deviceAdminStatus.setText(active
                ? "Усиленная защита удаления: включена"
                : "Усиленная защита удаления: выключена");
        deviceAdminStatus.setTextColor(active ? 0xffa5d6a7 : 0xffffcc80);
    }

    private void configureDeviceAdminButton() {
        if (deviceAdminButton == null) {
            return;
        }
        deviceAdminButton.setEnabled(true);
        deviceAdminButton.setText("Включить усиленную защиту удаления");
        deviceAdminButton.setOnClickListener(null);
        boolean supported = getPackageManager().hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN);
        boolean active = supported
                && devicePolicyManager != null
                && devicePolicyManager.isAdminActive(
                        new ComponentName(this, TimerDeviceAdminReceiver.class)
                );
        if (DeviceOwnerProtection.isDeviceOwner(this)) {
            deviceAdminButton.setEnabled(false);
            deviceAdminButton.setText("Device Owner уже защищает удаление");
            return;
        }
        if (!supported || active) {
            deviceAdminButton.setEnabled(false);
            deviceAdminButton.setText(active ? "Защита уже включена" : "Функция недоступна");
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
                showError("Прошивка не открыла включение защиты удаления");
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
        try {
            store.grantMaintenanceWindow();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (RuntimeException exception) {
            showError("Прошивка не открыла настройки специальных возможностей");
        }
    }

    private void checkForUpdate(UpdateUi ui) {
        if (activeDownloadId >= 0L) {
            discardActiveDownload();
        }
        setButtonBusy(ui.button, true, "Проверяю…");
        ui.status.setText("Проверяю последний стабильный релиз…");
        updateExecutor.execute(() -> {
            try {
                GithubUpdateClient.ReleaseInfo release = new GithubUpdateClient().fetchLatest();
                if (!VersionComparator.isNewer(release.version, BuildConfig.VERSION_NAME)) {
                    postToScreen(ui.generation, () -> {
                        ui.status.setText("Установлена актуальная версия " + BuildConfig.VERSION_NAME);
                        resetUpdateButton(ui);
                    });
                    return;
                }
                postToScreen(ui.generation, () -> startUpdateDownload(release, ui));
            } catch (Exception exception) {
                Log.e(TAG, "Unable to check GitHub release", exception);
                postToScreen(ui.generation, () -> {
                    ui.status.setText("Не удалось проверить обновление. Проверьте интернет и повторите.");
                    resetUpdateButton(ui);
                });
            }
        });
    }

    private void startUpdateDownload(GithubUpdateClient.ReleaseInfo release, UpdateUi ui) {
        if (downloadManager == null) {
            ui.status.setText("Системная служба загрузок недоступна на этой прошивке.");
            resetUpdateButton(ui);
            return;
        }
        File downloads = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloads == null) {
            ui.status.setText("Хранилище для обновления недоступно.");
            resetUpdateButton(ui);
            return;
        }
        String relativePath = "updates/" + release.assetName;
        File target = new File(downloads, relativePath);
        File parent = target.getParentFile();
        if ((parent != null && !parent.exists() && !parent.mkdirs())
                || (target.exists() && !target.delete())) {
            ui.status.setText("Не удалось подготовить файл обновления.");
            resetUpdateButton(ui);
            return;
        }
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(release.downloadUrl))
                    .setTitle("TV Timer " + release.version)
                    .setDescription("Безопасное обновление TV Timer")
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
            ui.status.setText(
                    "Обновление найдено: " + release.tag
                            + "\nСкачиваю…\n"
                            + UpdateProgress.render(0L, 0L)
            );
            updateExecutor.execute(() -> monitorDownload(downloadId, target, release, ui));
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to enqueue update", exception);
            ui.status.setText("Системная служба не смогла начать загрузку.");
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
                    postToScreen(ui.generation, () -> ui.status.setText(
                            "Обновление найдено: " + release.tag
                                    + "\nСкачиваю…\n" + progress
                    ));
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
                ui.status.setText(
                        "Обновление " + release.tag + " скачано\n"
                                + UpdateProgress.render(1L, 1L)
                                + "\nSHA-256 и подпись проверены."
                );
                ui.button.setEnabled(true);
                ui.button.setText("Установить обновление");
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
                ui.status.setText("Обновление отклонено: загрузка или проверка APK не пройдена.");
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
            ui.status.setText(
                    "Разрешите TV Timer установку из этого источника, затем вернитесь и нажмите «Установить»."
            );
            try {
                startActivity(new Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())
                ));
            } catch (RuntimeException exception) {
                showError("Прошивка не открыла разрешение установки");
            }
            return;
        }
        Uri uri = downloadManager == null ? null : downloadManager.getUriForDownloadedFile(downloadId);
        if (uri == null) {
            ui.status.setText("Файл обновления больше недоступен. Проверьте обновление снова.");
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
            showError("Прошивка не открыла установщик APK");
        }
    }

    private void resetUpdateButton(UpdateUi ui) {
        ui.button.setEnabled(true);
        ui.button.setText("Проверить обновление");
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
        content.setPadding(dp(40), dp(28), dp(40), dp(40));
        serviceStatus = null;
        deviceAdminStatus = null;
        deviceAdminButton = null;
        scrollView.scrollTo(0, 0);
        int generation = screenGeneration;
        scrollView.post(() -> {
            if (!destroyed && screenGeneration == generation) {
                scrollView.scrollTo(0, 0);
            }
        });
    }

    private void addTitle(String text) {
        TextView title = textView(text, 28f, Color.WHITE);
        title.setGravity(Gravity.START);
        LinearLayout.LayoutParams params = matchWrapParams();
        params.bottomMargin = dp(14);
        content.addView(title, params);
    }

    private TextView addSubheading(String text) {
        TextView heading = textView(text, 20f, Color.WHITE);
        LinearLayout.LayoutParams params = matchWrapParams();
        params.topMargin = dp(16);
        params.bottomMargin = dp(6);
        content.addView(heading, params);
        return heading;
    }

    private TextView addParagraph(String text) {
        TextView paragraph = textView(text, 18f, 0xffeeeeee);
        LinearLayout.LayoutParams params = matchWrapParams();
        params.bottomMargin = dp(10);
        content.addView(paragraph, params);
        return paragraph;
    }

    private void addNotice(String text, int color) {
        TextView notice = textView(text, 16f, color);
        notice.setPadding(dp(14), dp(10), dp(14), dp(10));
        notice.setBackgroundColor(0xff263238);
        LinearLayout.LayoutParams params = matchWrapParams();
        params.topMargin = dp(10);
        params.bottomMargin = dp(8);
        content.addView(notice, params);
    }

    private MinuteLimitControl addMinuteLimitControl(long initialMinutes) {
        addSubheading("Дневной лимит");
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
        button.setTextSize(18f);
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        applyTvButtonFocus(button);
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(56), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        row.addView(button, params);
    }

    private EditText addInput(String hint, boolean password) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(0xff9e9e9e);
        input.setTextColor(Color.WHITE);
        input.setTextSize(18f);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER
                | (password ? InputType.TYPE_NUMBER_VARIATION_PASSWORD : InputType.TYPE_NUMBER_VARIATION_NORMAL));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(420), dp(58));
        params.bottomMargin = dp(10);
        content.addView(input, params);
        return input;
    }

    private RadioButton addRadio(RadioGroup group, String text) {
        RadioButton button = new RadioButton(this);
        button.setId(View.generateViewId());
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(18f);
        button.setMinHeight(dp(50));
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
        button.setTextSize(17f);
        button.setMinHeight(dp(56));
        button.setAllCaps(false);
        applyTvButtonFocus(button);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(60)
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
            float scale = hasFocus ? 1.06f : 1f;
            view.animate().scaleX(scale).scaleY(scale).setDuration(90L).start();
            view.setElevation(hasFocus ? dp(10) : dp(2));
        });
    }

    private void applyRowFocus(View view) {
        view.setOnFocusChangeListener((focusedView, hasFocus) -> {
            focusedView.setBackgroundColor(hasFocus ? 0xff455a64 : Color.TRANSPARENT);
            float scale = hasFocus ? 1.02f : 1f;
            focusedView.animate().scaleX(scale).scaleY(scale).setDuration(90L).start();
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

    private static final class InstalledApp {
        private final String packageName;
        private final String label;

        private InstalledApp(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }

    private static final class AppCheckState {
        private final Map<String, CheckBox> checks = new LinkedHashMap<>();
        private boolean loaded;
    }

    private static final class AppLoadResult {
        private final List<InstalledApp> apps;
        private final boolean restricted;

        private AppLoadResult(List<InstalledApp> apps, boolean restricted) {
            this.apps = apps;
            this.restricted = restricted;
        }
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
            valueView.setText(minutes + " мин");
        }
    }
}
