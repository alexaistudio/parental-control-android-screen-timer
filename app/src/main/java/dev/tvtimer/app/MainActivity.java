package dev.tvtimer.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
    private ConfigStore store;
    private ScrollView scrollView;
    private LinearLayout content;
    private boolean adminUnlocked;
    private TextView serviceStatus;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private int screenGeneration;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ConfigStore(this);

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
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
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
            renderSetup();
        } else if (serviceStatus != null) {
            updateServiceStatus();
        }
    }

    private void renderCurrentScreen() {
        if (!store.isConfigured()) {
            renderSetup();
        } else if (adminUnlocked) {
            renderSettings();
        } else {
            renderLockedHome();
        }
    }

    private void renderSetup() {
        clearScreen();
        addTitle("TV Timer — первоначальная настройка");
        if (store.hasUsbRecoveryNotice()) {
            addNotice("USB-восстановление выполнено: прежний PIN и настройки удалены, защита выключена.", 0xffffcc80);
        }
        addParagraph("Задайте PIN и дневное время. Лимит считается только пока экран активен и работает контролируемое приложение.");

        EditText pin = addInput("PIN: 4–8 цифр", true);
        EditText confirmation = addInput("Повторите PIN", true);
        EditText minutes = addInput("Минут в день (1–1440)", false);
        minutes.setText(getString(R.string.default_minutes));

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
            Long dailyLimit = parseLimit(minutes);
            if (dailyLimit == null) {
                return;
            }
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
        TextView title = textView("TV Timer — PIN родителя", 26f, Color.WHITE);
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
                    verified = store.verifyPin(enteredPin);
                } catch (RuntimeException exception) {
                    verified = false;
                }
                boolean result = verified;
                postToScreen(generation, () -> {
                    if (result) {
                        adminUnlocked = true;
                        renderSettings();
                    } else {
                        holder[0].showError("Неверный PIN");
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

        EditText minutes = addInput("Минут в день (1–1440)", false);
        minutes.setText(String.valueOf(store.getDailyLimitMillis() / 60_000L));

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
            Long dailyLimit = parseLimit(minutes);
            if (dailyLimit == null) {
                return;
            }
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
            int generation = screenGeneration;
            setButtonBusy(save, true, "Сохранение…");
            backgroundExecutor.execute(() -> {
                try {
                    boolean settingsSaved = store.updateSettings(
                            dailyLimit,
                            scope,
                            selected,
                            enforcementEnabled
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

    private Long parseLimit(EditText input) {
        try {
            long minutes = Long.parseLong(input.getText().toString());
            if (minutes < 1L || minutes > 1_440L) {
                throw new NumberFormatException("outside range");
            }
            return minutes * 60_000L;
        } catch (NumberFormatException exception) {
            showError("Укажите от 1 до 1440 минут");
            input.requestFocus();
            return null;
        }
    }

    private void updateServiceStatus() {
        if (serviceStatus != null) {
            serviceStatus.setText(isAccessibilityServiceEnabled()
                    ? "Служба контроля: включена"
                    : "Служба контроля: выключена — лимит пока не применяется");
            serviceStatus.setTextColor(isAccessibilityServiceEnabled() ? 0xffa5d6a7 : 0xffffcc80);
        }
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
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (RuntimeException exception) {
            showError("Прошивка не открыла настройки специальных возможностей");
        }
    }

    private void clearScreen() {
        screenGeneration++;
        content.removeAllViews();
        content.setMinimumHeight(0);
        content.setGravity(Gravity.TOP);
        content.setPadding(dp(40), dp(28), dp(40), dp(40));
        serviceStatus = null;
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
}
