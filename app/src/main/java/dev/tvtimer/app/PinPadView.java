package dev.tvtimer.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class PinPadView extends LinearLayout {
    public interface Listener {
        void onPinSubmitted(String pin);
    }

    private final StringBuilder pin = new StringBuilder();
    private final TextView display;
    private final TextView message;
    private final Listener listener;
    private final GridLayout keys;
    private boolean busy;

    public PinPadView(Context context) {
        this(context, pin -> {
        });
    }

    public PinPadView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setFocusable(true);

        display = new TextView(context);
        display.setTextColor(Color.WHITE);
        display.setTextSize(25f);
        display.setGravity(Gravity.CENTER);
        display.setMinWidth(dp(240));
        display.setMinHeight(dp(40));
        display.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        display.setContentDescription("Введённый PIN");
        addView(display, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        message = new TextView(context);
        message.setTextColor(0xffff8a80);
        message.setTextSize(15f);
        message.setGravity(Gravity.CENTER);
        LayoutParams messageParams = new LayoutParams(LayoutParams.WRAP_CONTENT, dp(26));
        addView(message, messageParams);

        keys = new GridLayout(context);
        keys.setColumnCount(3);
        keys.setRowCount(4);
        addDigitButton(keys, "1");
        addDigitButton(keys, "2");
        addDigitButton(keys, "3");
        addDigitButton(keys, "4");
        addDigitButton(keys, "5");
        addDigitButton(keys, "6");
        addDigitButton(keys, "7");
        addDigitButton(keys, "8");
        addDigitButton(keys, "9");
        addActionButton(keys, "⌫", view -> erase());
        addDigitButton(keys, "0");
        addActionButton(keys, "OK", view -> submit());
        addView(keys, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        updateDisplay();
        keys.getChildAt(0).requestFocus();
    }

    public void showError(String text) {
        setBusy(false);
        message.setText(text);
        clearPin();
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
        for (int index = 0; index < keys.getChildCount(); index++) {
            keys.getChildAt(index).setEnabled(!busy);
        }
        if (busy) {
            message.setText("Проверка…");
        }
    }

    public void clearPin() {
        pin.setLength(0);
        updateDisplay();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (busy) {
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            int keyCode = event.getKeyCode();
            if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                append((char) ('0' + keyCode - KeyEvent.KEYCODE_0));
                return true;
            }
            if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
                append((char) ('0' + keyCode - KeyEvent.KEYCODE_NUMPAD_0));
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                erase();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                submit();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void addDigitButton(GridLayout grid, String digit) {
        addActionButton(grid, digit, view -> append(digit.charAt(0)));
    }

    private void addActionButton(GridLayout grid, String label, OnClickListener onClickListener) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setTextSize(19f);
        button.setMinWidth(dp(72));
        button.setMinHeight(dp(44));
        button.setAllCaps(false);
        applyTvFocus(button);
        button.setOnClickListener(onClickListener);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = dp(82);
        params.height = dp(48);
        params.setMargins(dp(3), dp(2), dp(3), dp(2));
        grid.addView(button, params);
    }

    private void append(char digit) {
        if (pin.length() < 8) {
            pin.append(digit);
            message.setText("");
            updateDisplay();
        }
    }

    private void erase() {
        if (pin.length() > 0) {
            pin.deleteCharAt(pin.length() - 1);
            updateDisplay();
        }
    }

    private void submit() {
        if (busy) {
            return;
        }
        if (!PinHasher.isValidFormat(pin.toString())) {
            showError("Введите от 4 до 8 цифр");
            return;
        }
        listener.onPinSubmitted(pin.toString());
    }

    private void applyTvFocus(Button button) {
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
        button.setOnFocusChangeListener((view, hasFocus) -> {
            float scale = hasFocus ? 1.10f : 1f;
            view.animate().scaleX(scale).scaleY(scale).setDuration(90L).start();
            view.setElevation(hasFocus ? dp(10) : dp(2));
        });
    }

    private void updateDisplay() {
        StringBuilder masked = new StringBuilder();
        for (int index = 0; index < pin.length(); index++) {
            masked.append('●');
        }
        display.setText(masked.length() == 0 ? "— — — —" : masked.toString());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
