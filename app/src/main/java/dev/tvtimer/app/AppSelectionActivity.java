package dev.tvtimer.app;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppSelectionActivity extends LocalizedActivity {
    private static final String STATE_SELECTED_PACKAGES = "selected_packages";
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Map<String, CheckBox> checks = new LinkedHashMap<>();
    private ConfigStore store;
    private LinearLayout appList;
    private Set<String> previouslySelected = Collections.emptySet();
    private boolean loaded;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ConfigStore(this);
        ArrayList<String> restored = savedInstanceState == null
                ? null
                : savedInstanceState.getStringArrayList(STATE_SELECTED_PACKAGES);
        previouslySelected = restored == null
                ? store.getSelectedPackages()
                : new HashSet<>(restored);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff121212);
        SystemBarInsets.apply(root);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xff121212);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = getResources().getDisplayMetrics().widthPixels < dp(600)
                ? dp(12)
                : dp(20);
        content.setPadding(horizontalPadding, dp(10), horizontalPadding, dp(14));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int outerSpace = screenWidth < dp(600) ? dp(24) : dp(80);
        int menuWidth = Math.max(1, Math.min(dp(720), screenWidth - outerSpace));
        root.addView(scroll, new FrameLayout.LayoutParams(
                menuWidth,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_HORIZONTAL
        ));

        LanguageSwitcherView languageSwitcher = new LanguageSwitcherView(this, () -> {
            persistCurrentSelection();
            recreate();
        });
        LinearLayout.LayoutParams languageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        languageParams.gravity = Gravity.END;
        languageParams.bottomMargin = dp(5);
        content.addView(languageSwitcher, languageParams);

        TextView title = text(getString(R.string.app_selection_title), 24f, Color.WHITE);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(title, matchWrap(dp(10)));
        content.addView(text(
                getString(R.string.app_selection_instructions),
                15f,
                0xffeeeeee
        ), matchWrap(dp(12)));

        Button done = button(getString(R.string.app_selection_done));
        done.setOnClickListener(view -> saveAndFinish());
        content.addView(done, matchWrap(dp(18)));

        appList = new LinearLayout(this);
        appList.setOrientation(LinearLayout.VERTICAL);
        appList.addView(text(getString(R.string.apps_loading), 15f, 0xffb0bec5), matchWrap(0));
        content.addView(appList, matchWrap(0));
        setContentView(root);
        done.requestFocus();
        loadApps();
    }

    @Override
    public void onBackPressed() {
        saveAndFinish();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        loader.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putStringArrayList(
                STATE_SELECTED_PACKAGES,
                new ArrayList<>(currentSelection())
        );
        super.onSaveInstanceState(outState);
    }

    private void loadApps() {
        Set<String> selectedSnapshot = new HashSet<>(previouslySelected);
        loader.execute(() -> {
            LoadResult result = queryLaunchableApps(selectedSnapshot);
            runOnUiThread(() -> {
                if (destroyed) {
                    return;
                }
                appList.removeAllViews();
                checks.clear();
                for (InstalledApp app : result.apps) {
                    CheckBox checkBox = new CheckBox(this);
                    checkBox.setText(getString(
                            R.string.app_entry_format,
                            app.label,
                            app.packageName
                    ));
                    checkBox.setTextColor(Color.WHITE);
                    checkBox.setTextSize(14f);
                    checkBox.setMinHeight(dp(38));
                    checkBox.setPadding(dp(5), dp(2), dp(5), dp(2));
                    Drawable icon = app.icon == null
                            ? getPackageManager().getDefaultActivityIcon()
                            : app.icon.mutate();
                    icon.setBounds(0, 0, dp(30), dp(30));
                    checkBox.setCompoundDrawablesRelative(icon, null, null, null);
                    checkBox.setCompoundDrawablePadding(dp(7));
                    checkBox.setChecked(selectedSnapshot.contains(app.packageName));
                    applyTvFocus(checkBox);
                    appList.addView(checkBox, matchWrap(dp(2)));
                    checks.put(app.packageName, checkBox);
                }
                if (checks.isEmpty()) {
                    appList.addView(
                            text(getString(R.string.apps_not_found), 15f, 0xffffcc80),
                            matchWrap(0)
                    );
                }
                loaded = true;
                if (result.restricted) {
                    Toast.makeText(
                            this,
                            getString(R.string.apps_query_restricted),
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
        });
    }

    private LoadResult queryLaunchableApps(Set<String> selected) {
        Map<String, InstalledApp> apps = new LinkedHashMap<>();
        boolean restricted = !queryCategory(Intent.CATEGORY_LEANBACK_LAUNCHER, apps);
        restricted |= !queryCategory(Intent.CATEGORY_LAUNCHER, apps);
        for (String packageName : selected) {
            if (!apps.containsKey(packageName)) {
                apps.put(
                        packageName,
                        new InstalledApp(
                                packageName,
                                getString(R.string.app_missing_suffix, packageName),
                                loadApplicationIcon(packageName)
                        )
                );
            }
        }
        List<InstalledApp> result = new ArrayList<>(apps.values());
        Collections.sort(result, (first, second) -> first.label
                .toLowerCase(Locale.ROOT)
                .compareTo(second.label.toLowerCase(Locale.ROOT)));
        return new LoadResult(result, restricted);
    }

    @SuppressWarnings("deprecation")
    private boolean queryCategory(String category, Map<String, InstalledApp> destination) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
        PackageManager manager = getPackageManager();
        try {
            for (ResolveInfo info : manager.queryIntentActivities(intent, 0)) {
                if (info.activityInfo == null) {
                    continue;
                }
                String packageName = info.activityInfo.packageName;
                if (getPackageName().equals(packageName)) {
                    continue;
                }
                CharSequence loadedLabel = info.loadLabel(manager);
                String label = loadedLabel == null ? packageName : loadedLabel.toString();
                Drawable icon;
                try {
                    icon = info.loadIcon(manager);
                } catch (RuntimeException exception) {
                    icon = manager.getDefaultActivityIcon();
                }
                destination.put(packageName, new InstalledApp(packageName, label, icon));
            }
            return true;
        } catch (SecurityException exception) {
            return false;
        }
    }

    private void saveAndFinish() {
        if (!persistCurrentSelection()) {
            Toast.makeText(this, R.string.app_selection_save_failed, Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, R.string.app_selection_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private boolean persistCurrentSelection() {
        return store.updateSelectedPackages(currentSelection());
    }

    private Drawable loadApplicationIcon(String packageName) {
        PackageManager manager = getPackageManager();
        try {
            return manager.getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException | RuntimeException exception) {
            return manager.getDefaultActivityIcon();
        }
    }

    private Set<String> currentSelection() {
        Set<String> selected = new HashSet<>(previouslySelected);
        if (loaded) {
            selected.clear();
            for (Map.Entry<String, CheckBox> entry : checks.entrySet()) {
                if (entry.getValue().isChecked()) {
                    selected.add(entry.getKey());
                }
            }
        }
        return selected;
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14f);
        button.setAllCaps(false);
        button.setMinHeight(dp(40));
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_focused},
                new int[]{android.R.attr.state_pressed},
                new int[]{}
        };
        button.setBackgroundTintList(new ColorStateList(
                states,
                new int[]{0xffffd54f, 0xffffb300, 0xff37474f}
        ));
        button.setTextColor(new ColorStateList(
                states,
                new int[]{Color.BLACK, Color.BLACK, Color.WHITE}
        ));
        applyTvFocus(button);
        return button;
    }

    private void applyTvFocus(View view) {
        view.setFocusable(true);
        view.setAlpha(0.88f);
        view.setOnFocusChangeListener((focusedView, hasFocus) -> {
            focusedView.animate().scaleX(1f).scaleY(1f).setDuration(0L).start();
            focusedView.setAlpha(hasFocus ? 1f : 0.88f);
            focusedView.setElevation(hasFocus ? dp(8) : 0f);
        });
    }

    private LinearLayout.LayoutParams matchWrap(int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = bottomMargin;
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class InstalledApp {
        private final String packageName;
        private final String label;
        private final Drawable icon;

        private InstalledApp(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    private static final class LoadResult {
        private final List<InstalledApp> apps;
        private final boolean restricted;

        private LoadResult(List<InstalledApp> apps, boolean restricted) {
            this.apps = apps;
            this.restricted = restricted;
        }
    }
}
