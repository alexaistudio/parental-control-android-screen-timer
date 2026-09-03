package dev.tvtimer.app;

import android.os.Build;
import android.view.DisplayCutout;
import android.view.ViewGroup;

final class SystemBarInsets {
    private SystemBarInsets() {
    }

    @SuppressWarnings("deprecation")
    static void apply(ViewGroup view) {
        view.setClipToPadding(false);
        view.setOnApplyWindowInsetsListener((target, insets) -> {
            int left = insets.getSystemWindowInsetLeft();
            int top = insets.getSystemWindowInsetTop();
            int right = insets.getSystemWindowInsetRight();
            int bottom = insets.getSystemWindowInsetBottom();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                DisplayCutout cutout = insets.getDisplayCutout();
                if (cutout != null) {
                    left = Math.max(left, cutout.getSafeInsetLeft());
                    top = Math.max(top, cutout.getSafeInsetTop());
                    right = Math.max(right, cutout.getSafeInsetRight());
                    bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                }
            }
            target.setPadding(left, top, right, bottom);
            return insets;
        });
        view.requestApplyInsets();
    }
}
