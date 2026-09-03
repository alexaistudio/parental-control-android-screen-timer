package dev.tvtimer.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

final class LanguageSwitcherView extends LinearLayout {
    interface Listener {
        void onLanguageChanged();
    }

    LanguageSwitcherView(Context context) {
        this(context, () -> {
        });
    }

    LanguageSwitcherView(Context context, Listener listener) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.END);

        TextView label = new TextView(context);
        label.setText(R.string.language_label);
        label.setTextColor(0xffb0bec5);
        label.setTextSize(14f);
        LayoutParams labelParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelParams.setMarginEnd(dp(8));
        addView(label, labelParams);

        String selected = new ConfigStore(context).getLanguage();
        addLanguageButton("RU", R.string.language_russian, AppLanguage.RUSSIAN, selected, listener);
        addLanguageButton("EN", R.string.language_english, AppLanguage.ENGLISH, selected, listener);
    }

    private void addLanguageButton(
            String label,
            int contentDescription,
            String language,
            String selected,
            Listener listener
    ) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setContentDescription(getResources().getString(contentDescription));
        button.setTextSize(14f);
        button.setAllCaps(false);
        button.setMinWidth(dp(58));
        button.setMinHeight(dp(48));
        button.setPadding(dp(8), 0, dp(8), 0);
        boolean active = language.equals(selected);
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_focused},
                new int[]{android.R.attr.state_pressed},
                new int[]{}
        };
        button.setBackgroundTintList(new ColorStateList(
                states,
                new int[]{0xffffd54f, 0xffffb300, active ? 0xff00695c : 0xff37474f}
        ));
        button.setTextColor(new ColorStateList(
                states,
                new int[]{Color.BLACK, Color.BLACK, Color.WHITE}
        ));
        button.setOnFocusChangeListener((view, hasFocus) -> {
            float scale = hasFocus ? 1.08f : 1f;
            view.animate().scaleX(scale).scaleY(scale).setDuration(90L).start();
            view.setElevation(hasFocus ? dp(8) : dp(1));
        });
        button.setOnClickListener(view -> {
            if (language.equals(new ConfigStore(getContext()).getLanguage())) {
                return;
            }
            if (!AppLanguage.select(getContext(), language)) {
                Toast.makeText(
                        getContext(),
                        R.string.language_save_failed,
                        Toast.LENGTH_LONG
                ).show();
                return;
            }
            listener.onLanguageChanged();
        });
        LayoutParams params = new LayoutParams(dp(64), dp(48));
        params.setMargins(dp(2), 0, dp(2), 0);
        addView(button, params);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
