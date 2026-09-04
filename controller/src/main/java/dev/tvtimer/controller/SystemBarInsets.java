package dev.tvtimer.controller;

import android.annotation.TargetApi;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

final class SystemBarInsets {
    private SystemBarInsets() {
    }

    static void apply(View root) {
        int baseLeft = root.getPaddingLeft();
        int baseTop = root.getPaddingTop();
        int baseRight = root.getPaddingRight();
        int baseBottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                int[] insets = Api30Impl.systemBarsAndCutout(windowInsets);
                left = insets[0];
                top = insets[1];
                right = insets[2];
                bottom = insets[3];
            } else {
                left = windowInsets.getSystemWindowInsetLeft();
                top = windowInsets.getSystemWindowInsetTop();
                right = windowInsets.getSystemWindowInsetRight();
                bottom = windowInsets.getSystemWindowInsetBottom();
            }
            view.setPadding(
                    baseLeft + left,
                    baseTop + top,
                    baseRight + right,
                    baseBottom + bottom
            );
            return windowInsets;
        });
        root.requestApplyInsets();
    }

    @TargetApi(Build.VERSION_CODES.R)
    private static final class Api30Impl {
        private Api30Impl() {
        }

        static int[] systemBarsAndCutout(WindowInsets windowInsets) {
            android.graphics.Insets insets = windowInsets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
            );
            return new int[]{insets.left, insets.top, insets.right, insets.bottom};
        }
    }
}
