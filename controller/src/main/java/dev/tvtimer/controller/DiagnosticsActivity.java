package dev.tvtimer.controller;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DiagnosticsActivity extends Activity {
    private static final int REQUEST_SAVE_LOG = 4102;

    private TextView logView;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ControllerLanguage.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ControllerLog.info("Diagnostics/UI", "Log viewer opened");
        buildUi();
        refreshLog();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(16));
        root.setBackgroundColor(getColor(R.color.controller_background));

        TextView title = text(getString(R.string.diagnostics_title), 22, true);
        root.addView(title, matchWrap());

        TextView note = text(getString(R.string.diagnostics_note), 14, false);
        LinearLayout.LayoutParams noteParams = matchWrap();
        noteParams.topMargin = dp(6);
        root.addView(note, noteParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.topMargin = dp(10);
        root.addView(actions, actionsParams);

        Button copy = button(R.string.copy_full_log);
        copy.setOnClickListener(view -> copyLog());
        actions.addView(copy, weightedButton());

        Button save = button(R.string.save_log_file);
        save.setOnClickListener(view -> chooseSaveLocation());
        LinearLayout.LayoutParams saveParams = weightedButton();
        saveParams.leftMargin = dp(8);
        actions.addView(save, saveParams);

        Button refresh = button(R.string.refresh_log);
        refresh.setOnClickListener(view -> refreshLog());
        LinearLayout.LayoutParams refreshParams = matchWrap();
        refreshParams.topMargin = dp(8);
        root.addView(refresh, refreshParams);

        TextView rawTitle = text(getString(R.string.full_log_title), 17, true);
        LinearLayout.LayoutParams rawTitleParams = matchWrap();
        rawTitleParams.topMargin = dp(12);
        root.addView(rawTitle, rawTitleParams);

        ScrollView logScroll = new ScrollView(this);
        logScroll.setFillViewport(true);
        logView = text("", 12, false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setPadding(dp(10), dp(10), dp(10), dp(10));
        logView.setBackgroundColor(0xffffffff);
        logScroll.addView(logView, matchWrap());
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = dp(6);
        root.addView(logScroll, scrollParams);

        setContentView(root);
    }

    private void refreshLog() {
        logView.setText(ControllerLog.snapshot());
    }

    private void copyLog() {
        ControllerLog.info("Diagnostics/UI", "Copy full log requested");
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, R.string.copy_log_failed, Toast.LENGTH_LONG).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(
                "Android Screen Timer Parent diagnostics", ControllerLog.snapshot()));
        Toast.makeText(this, R.string.log_copied, Toast.LENGTH_SHORT).show();
        refreshLog();
    }

    private void chooseSaveLocation() {
        ControllerLog.info("Diagnostics/UI", "Save full log requested");
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TITLE,
                        "android-screen-timer-parent-" + timestamp + ".txt");
        try {
            startActivityForResult(intent, REQUEST_SAVE_LOG);
        } catch (RuntimeException exception) {
            ControllerLog.error("Diagnostics/Save", "Document picker could not open", exception);
            Toast.makeText(this, R.string.save_log_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SAVE_LOG || resultCode != RESULT_OK
                || data == null || data.getData() == null) {
            if (requestCode == REQUEST_SAVE_LOG) {
                ControllerLog.info("Diagnostics/Save", "Save cancelled by user or provider");
            }
            return;
        }
        Uri destination = data.getData();
        try (OutputStream output = getContentResolver().openOutputStream(destination, "wt")) {
            if (output == null) {
                throw new IllegalStateException("Document provider returned no output stream");
            }
            ControllerLog.info("Diagnostics/Save", "Writing complete snapshot to selected document");
            output.write(ControllerLog.snapshot().getBytes(StandardCharsets.UTF_8));
            output.flush();
            ControllerLog.info("Diagnostics/Save", "Log file saved successfully");
            Toast.makeText(this, R.string.log_saved, Toast.LENGTH_SHORT).show();
            refreshLog();
        } catch (Exception exception) {
            ControllerLog.error("Diagnostics/Save", "Unable to save log file", exception);
            Toast.makeText(this, R.string.save_log_failed, Toast.LENGTH_LONG).show();
            refreshLog();
        }
    }

    private TextView text(String value, int sizeSp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(getColor(R.color.controller_text));
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private Button button(int textResource) {
        Button button = new Button(this);
        button.setText(textResource);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weightedButton() {
        return new LinearLayout.LayoutParams(0, dp(50), 1f);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
