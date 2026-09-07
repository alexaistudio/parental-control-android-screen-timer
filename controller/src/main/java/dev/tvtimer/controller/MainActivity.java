package dev.tvtimer.controller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private final DeviceRegistry devices = new DeviceRegistry();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicBoolean remoteBusy = new AtomicBoolean(false);
    private final Handler remoteRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable remoteRefresh = new Runnable() {
        @Override
        public void run() {
            if (remotePanel != null && remotePanel.getVisibility() == View.VISIBLE) {
                refreshRemoteState(false);
                remoteRefreshHandler.postDelayed(this, 5_000L);
            }
        }
    };

    private AdbClient adbClient;
    private AdbDiscovery discovery;
    private LinearLayout deviceList;
    private EditText hostField;
    private EditText pairPortField;
    private EditText connectPortField;
    private EditText pairCodeField;
    private Button scanButton;
    private Button pairButton;
    private Button connectButton;
    private Button installButton;
    private ProgressBar scanProgress;
    private ProgressBar installProgress;
    private TextView statusView;
    private CheckBox accessibilityCheck;
    private CheckBox deviceOwnerCheck;
    private CheckBox disableDebugCheck;
    private View remotePanel;
    private EditText remoteCodeField;
    private EditText remoteMinutesField;
    private EditText remoteCommentField;
    private TextView remoteRemainingView;
    private TextView remoteDetailsView;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ControllerLanguage.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SystemBarInsets.apply(findViewById(R.id.rootScroll));
        adbClient = new AdbClient(this);
        bindViews();
        bindActions();
        ControllerLog.info("Main/UI", "Main screen created");
    }

    private void bindViews() {
        deviceList = findViewById(R.id.deviceList);
        hostField = findViewById(R.id.editHost);
        pairPortField = findViewById(R.id.editPairPort);
        connectPortField = findViewById(R.id.editConnectPort);
        pairCodeField = findViewById(R.id.editPairCode);
        pairCodeField.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        scanButton = findViewById(R.id.buttonScan);
        pairButton = findViewById(R.id.buttonPair);
        connectButton = findViewById(R.id.buttonConnect);
        installButton = findViewById(R.id.buttonInstall);
        scanProgress = findViewById(R.id.progressScan);
        installProgress = findViewById(R.id.progressInstall);
        statusView = findViewById(R.id.textStatus);
        accessibilityCheck = findViewById(R.id.checkAccessibility);
        deviceOwnerCheck = findViewById(R.id.checkDeviceOwner);
        disableDebugCheck = findViewById(R.id.checkDisableDebug);
        remotePanel = findViewById(R.id.panelRemoteControl);
        remoteCodeField = findViewById(R.id.editRemoteCode);
        remoteMinutesField = findViewById(R.id.editRemoteMinutes);
        remoteCommentField = findViewById(R.id.editRemoteComment);
        remoteRemainingView = findViewById(R.id.textRemoteRemaining);
        remoteDetailsView = findViewById(R.id.textRemoteDetails);
        remoteCodeField.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    }

    private void bindActions() {
        findViewById(R.id.buttonRu).setOnClickListener(view -> switchLanguage("ru"));
        findViewById(R.id.buttonEn).setOnClickListener(view -> switchLanguage("en"));
        findViewById(R.id.buttonInstructions).setOnClickListener(view -> showWifiInstructions());
        findViewById(R.id.buttonUsbInstructions).setOnClickListener(
                view -> showUsbInstructions());
        findViewById(R.id.buttonDiagnostics).setOnClickListener(view -> {
            ControllerLog.info("Main/UI", "Opening persistent diagnostics");
            startActivity(new Intent(this, DiagnosticsActivity.class));
        });
        scanButton.setOnClickListener(view -> {
            ControllerLog.info("UserAction", "SCAN BUTTON pressed transport=wifi");
            startScan();
        });
        pairButton.setOnClickListener(view -> {
            ControllerLog.info("UserAction", "PAIR BUTTON pressed target="
                    + endpointForLog(pairPortField));
            pair();
        });
        connectButton.setOnClickListener(view -> {
            ControllerLog.info("UserAction", "CONNECT BUTTON pressed target="
                    + endpointForLog(connectPortField));
            connect();
        });
        installButton.setOnClickListener(view -> {
            ControllerLog.info("UserAction", "INSTALL BUTTON pressed target="
                    + endpointForLog(connectPortField));
            confirmInstall();
        });
        findViewById(R.id.buttonRemoteRefresh).setOnClickListener(view -> refreshRemoteState(true));
        findViewById(R.id.buttonRemoteAdd).setOnClickListener(view -> adjustRemoteTime(true));
        findViewById(R.id.buttonRemoteSubtract).setOnClickListener(view -> adjustRemoteTime(false));
    }

    private void switchLanguage(String language) {
        if (!language.equals(ControllerLanguage.get(this))) {
            ControllerLanguage.set(this, language);
            recreate();
        }
    }

    private void showWifiInstructions() {
        ControllerLog.info("UserAction", "WIFI INSTRUCTIONS opened");
        new AlertDialog.Builder(this)
                .setTitle(R.string.wifi_instructions_title)
                .setMessage(R.string.wifi_instructions_body)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void showUsbInstructions() {
        ControllerLog.info("UserAction", "USB INSTRUCTIONS opened");
        new AlertDialog.Builder(this)
                .setTitle(R.string.usb_instructions_title)
                .setMessage(R.string.usb_instructions_body)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void startScan() {
        if (!beginOperation()) {
            return;
        }
        ControllerLog.info("Discovery/UI", "Network discovery requested by user");
        adbClient.disconnect();
        setRemoteConnected(false);
        installButton.setEnabled(false);
        devices.clear();
        deviceList.removeAllViews();
        scanProgress.setVisibility(View.VISIBLE);
        setStatus(R.string.status_scanning);

        if (discovery != null) {
            discovery.stop();
        }
        discovery = new AdbDiscovery(this, new AdbDiscovery.Listener() {
            @Override
            public void onEndpoint(DeviceEndpoint endpoint) {
                ControllerLog.result("Discovery/Found",
                        "FOUND target=" + endpoint.host
                                + " pairingPort=" + endpoint.pairingPort
                                + " connectionPort=" + endpoint.connectionPort
                                + " name=" + endpoint.name);
                ControllerLog.info("Discovery/UI", "Endpoint displayed host=" + endpoint.host
                        + " pairingPort=" + endpoint.pairingPort
                        + " connectionPort=" + endpoint.connectionPort
                        + " name=" + endpoint.name);
                devices.upsert(endpoint);
                renderDevices();
            }

            @Override
            public void onFinished() {
                finishScan();
            }
        });
        discovery.start(10_000);

        worker.execute(() -> LegacyAdbScanner.scan(endpoint -> runOnUiThread(() -> {
            devices.upsert(endpoint);
            renderDevices();
        })));
    }

    private void finishScan() {
        scanProgress.setVisibility(View.GONE);
        busy.set(false);
        setControlsEnabled(true);
        List<DeviceEndpoint> snapshot = devices.snapshot();
        if (snapshot.isEmpty()) {
            ControllerLog.result("Discovery/Result",
                    "NO DEVICE FOUND: no ADB endpoint was discovered; wait for scan completion, "
                            + "enable Wireless debugging, or enter the target IP and its separate "
                            + "pairing/connection ports manually");
            ControllerLog.warning("Discovery/Diagnosis",
                    "No NSD endpoint and no open legacy TCP/5555 endpoint. The target is not "
                            + "advertising wireless/network ADB, the devices are on different or "
                            + "isolated networks, or mDNS/multicast is blocked. Use the separate "
                            + "pairing and connection ports shown by Wireless debugging.", null);
            ControllerLog.info("Discovery/UI", "Scan finished; no endpoints found");
            setStatus(R.string.status_none);
        } else {
            ControllerLog.info("Discovery/UI", "Scan finished; endpoints=" + snapshot.size());
            setStatus(getString(R.string.status_found, snapshot.size()));
        }
    }

    private void renderDevices() {
        List<DeviceEndpoint> snapshot = devices.snapshot();
        deviceList.removeAllViews();
        for (DeviceEndpoint endpoint : snapshot) {
            Button button = new Button(this);
            String ports = endpoint.host;
            if (endpoint.connectionPort > 0) {
                ports += ":" + endpoint.connectionPort;
            }
            if (endpoint.pairingPort > 0) {
                ports += " · pair " + endpoint.pairingPort;
            }
            button.setText(getString(R.string.found_device, endpoint.name, ports));
            button.setAllCaps(false);
            button.setMinHeight(dp(58));
            button.setOnClickListener(view -> selectDevice(endpoint));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(6);
            deviceList.addView(button, params);
        }
    }

    private void selectDevice(DeviceEndpoint endpoint) {
        ControllerLog.info("Discovery/UI", "Endpoint selected host=" + endpoint.host
                + " pairingPort=" + endpoint.pairingPort
                + " connectionPort=" + endpoint.connectionPort);
        if (discovery != null) {
            discovery.stop();
        }
        hostField.setText(endpoint.host);
        if (endpoint.pairingPort > 0) {
            pairPortField.setText(String.valueOf(endpoint.pairingPort));
        }
        if (endpoint.connectionPort > 0) {
            connectPortField.setText(String.valueOf(endpoint.connectionPort));
        }
        setStatus(getString(R.string.status_selected, endpoint.name));
        if (endpoint.canConnect()) {
            connect();
        } else if (endpoint.canPair()) {
            pairCodeField.requestFocus();
        }
    }

    private void pair() {
        String host = hostField.getText().toString().trim();
        int port = parsePort(pairPortField, -1);
        String code = pairCodeField.getText().toString().trim();
        if (host.isBlank() || port <= 0 || !code.matches("\\d{6}")) {
            ControllerLog.warning("Pair/UI", "Validation rejected host=" + host
                    + " pairingPort=" + port + " pairingCode=<redacted>", null);
            setStatus(R.string.invalid_pair);
            return;
        }
        if (!beginOperation()) {
            return;
        }
        setStatus(getString(R.string.status_pairing, host));
        worker.execute(() -> {
            try {
                adbClient.pair(host, port, code);
                runOnUiThread(() -> {
                    pairCodeField.setText("");
                    endOperation();
                    setStatus(R.string.paired_rescan);
                    startScan();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> showError(exception));
            }
        });
    }

    private void connect() {
        String host = hostField.getText().toString().trim();
        int port = parsePort(connectPortField, 5555);
        if (host.isBlank()) {
            ControllerLog.warning("Connect/UI", "Validation rejected an empty host", null);
            setStatus(R.string.invalid_host);
            return;
        }
        if (port <= 0) {
            ControllerLog.warning("Connect/UI", "Validation rejected connectionPort=" + port, null);
            setStatus(R.string.invalid_port);
            return;
        }
        connectTo(host, port);
    }

    private void connectTo(String host, int port) {
        if (!beginOperation()) {
            return;
        }
        connectPortField.setText(String.valueOf(port));
        setStatus(getString(R.string.status_connecting, host, port));
        worker.execute(() -> {
            try {
                String label = adbClient.connect(host, port);
                runOnUiThread(() -> {
                    endOperation();
                    installButton.setEnabled(true);
                    setStatus(getString(R.string.status_connected, label));
                    setRemoteConnected(true);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> showError(exception));
            }
        });
    }

    private void confirmInstall() {
        if (!deviceOwnerCheck.isChecked()) {
            install();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.configure_device_owner)
                .setMessage(R.string.device_owner_warning)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.continue_action, (dialog, which) -> install())
                .show();
    }

    private void install() {
        String host = hostField.getText().toString().trim();
        int port = parsePort(connectPortField, 5555);
        if (!adbClient.isConnectedTo(host, port)) {
            ControllerLog.result("Install/Rejected",
                    "INSTALL NOT STARTED target=" + host + ":" + port
                            + " reason=no active ADB connection; press Connect first");
            setStatus(R.string.connect_first);
            return;
        }
        if (!beginOperation()) {
            return;
        }
        installProgress.setProgress(0);
        installProgress.setVisibility(View.VISIBLE);
        setStatus(getString(R.string.status_installing, 0));
        boolean configureAccessibility = accessibilityCheck.isChecked();
        boolean requestOwner = deviceOwnerCheck.isChecked();
        boolean disableDebug = disableDebugCheck.isChecked();
        ControllerLog.info("Install/UI", "Install requested host=" + host + " port=" + port
                + " accessibility=" + configureAccessibility
                + " deviceOwner=" + requestOwner
                + " disableWirelessDebug=" + disableDebug);
        worker.execute(() -> {
            try {
                AdbClient.InstallResult result = adbClient.installAndConfigure(
                        configureAccessibility,
                        requestOwner,
                        disableDebug,
                        new AdbClient.ProgressListener() {
                            @Override
                            public void onProgress(int percent) {
                                runOnUiThread(() -> {
                                    installProgress.setProgress(percent);
                                    setStatus(getString(R.string.status_installing, percent));
                                });
                            }

                            @Override
                            public void onWaitingForPackageManager(int elapsedSeconds) {
                                runOnUiThread(() -> setStatus(getString(
                                        R.string.status_waiting_for_package_manager,
                                        elapsedSeconds
                                )));
                            }

                            @Override
                            public void onConfiguring() {
                                runOnUiThread(() -> setStatus(R.string.status_configuring));
                            }
                        });
                runOnUiThread(() -> showSuccess(result, disableDebug));
            } catch (Exception exception) {
                runOnUiThread(() -> showError(exception));
            }
        });
    }

    private void showSuccess(AdbClient.InstallResult result, boolean disableDebugRequested) {
        ControllerLog.info("Install/UI", "Install completed accessibility="
                + result.accessibilityEnabled + " deviceOwnerRequested="
                + result.deviceOwnerRequested + " deviceOwnerEnabled="
                + result.deviceOwnerEnabled + " debuggingDisabled="
                + result.debuggingDisabled + " device=" + result.deviceLabel);
        installProgress.setVisibility(View.GONE);
        endOperation();
        StringBuilder notes = new StringBuilder();
        if (!result.accessibilityEnabled) {
            notes.append(getString(R.string.partial_accessibility));
        }
        if (result.deviceOwnerRequested) {
            notes.append(getString(result.deviceOwnerEnabled
                    ? R.string.device_owner_enabled
                    : R.string.device_owner_not_enabled));
        }
        if (disableDebugRequested) {
            notes.append(getString(result.debuggingDisabled
                    ? R.string.debug_off_note
                    : R.string.debug_manual_note));
        }
        setStatus(getString(R.string.status_complete, notes.toString()));
        installButton.setEnabled(!result.debuggingDisabled);
        if (result.debuggingDisabled) {
            setRemoteConnected(false);
        }
    }

    private void setRemoteConnected(boolean connected) {
        remoteRefreshHandler.removeCallbacks(remoteRefresh);
        remotePanel.setVisibility(connected ? View.VISIBLE : View.GONE);
        if (connected) {
            remoteRemainingView.setText(R.string.remote_not_refreshed);
            remoteDetailsView.setText("");
            remoteRefreshHandler.postDelayed(remoteRefresh, 5_000L);
        }
    }

    private void refreshRemoteState(boolean userInitiated) {
        if (remotePanel.getVisibility() != View.VISIBLE || remoteBusy.get()) {
            return;
        }
        String code = remoteCodeField.getText().toString().trim();
        if (!code.matches("[0-9]{4,8}")) {
            if (userInitiated) {
                remoteRemainingView.setText(R.string.remote_not_refreshed);
            }
            return;
        }
        if (!remoteBusy.compareAndSet(false, true)) {
            return;
        }
        worker.execute(() -> {
            try {
                AdbClient.TimerState state = adbClient.readTimerState(code);
                runOnUiThread(() -> {
                    remoteBusy.set(false);
                    renderRemoteState(state);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    remoteBusy.set(false);
                    showRemoteError(exception);
                });
            }
        });
    }

    private void adjustRemoteTime(boolean add) {
        if (remotePanel.getVisibility() != View.VISIBLE || !remoteBusy.compareAndSet(false, true)) {
            return;
        }
        String code = remoteCodeField.getText().toString().trim();
        int minutes = parseRemoteMinutes();
        if (!code.matches("[0-9]{4,8}") || minutes <= 0) {
            remoteBusy.set(false);
            remoteRemainingView.setText(R.string.remote_not_refreshed);
            return;
        }
        int signedMinutes = add ? minutes : -minutes;
        String comment = remoteCommentField.getText().toString();
        worker.execute(() -> {
            try {
                AdbClient.TimerState state = adbClient.adjustTimer(code, signedMinutes, comment);
                runOnUiThread(() -> {
                    remoteBusy.set(false);
                    renderRemoteState(state);
                    setStatus(R.string.remote_updated);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    remoteBusy.set(false);
                    showRemoteError(exception);
                });
            }
        });
    }

    private int parseRemoteMinutes() {
        try {
            int value = Integer.parseInt(remoteMinutesField.getText().toString().trim());
            return value >= 1 && value <= 1_440 ? value : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void renderRemoteState(AdbClient.TimerState state) {
        remoteRemainingView.setText(getString(R.string.remote_remaining,
                formatDuration(state.getRemainingMillis())));
        String bonus = (state.getBonusMillis() >= 0L ? "+" : "−")
                + formatDuration(Math.abs(state.getBonusMillis()));
        remoteDetailsView.setText(getString(
                R.string.remote_details,
                formatDuration(state.getUsedMillis()),
                bonus,
                state.isEnforcementEnabled() ? getString(R.string.remote_limit_on)
                        : getString(R.string.remote_limit_off)
        ));
    }

    private void showRemoteError(Exception exception) {
        String message = exception.getMessage();
        remoteDetailsView.setText(message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message);
    }

    private String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1_000L;
        long hours = totalSeconds / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", hours, minutes);
    }

    private boolean beginOperation() {
        if (!busy.compareAndSet(false, true)) {
            setStatus(R.string.busy);
            return false;
        }
        setControlsEnabled(false);
        return true;
    }

    private void endOperation() {
        busy.set(false);
        setControlsEnabled(true);
    }

    private void setControlsEnabled(boolean enabled) {
        scanButton.setEnabled(enabled);
        pairButton.setEnabled(enabled);
        connectButton.setEnabled(enabled);
        installButton.setEnabled(enabled);
    }

    private void showError(Exception exception) {
        ControllerLog.error("Main/UI", "Operation failed", exception);
        scanProgress.setVisibility(View.GONE);
        installProgress.setVisibility(View.GONE);
        endOperation();
        String host = hostField.getText().toString().trim();
        int port = parsePort(connectPortField, 5555);
        installButton.setEnabled(port > 0 && adbClient.isConnectedTo(host, port));
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        setStatus(getString(R.string.status_error, message));
    }

    private int parsePort(EditText field, int fallback) {
        String value = field.getText().toString().trim();
        if (value.isBlank()) {
            return fallback;
        }
        try {
            int port = Integer.parseInt(value);
            return port >= 1 && port <= 65535 ? port : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private String endpointForLog(EditText portField) {
        String host = hostField.getText().toString().trim();
        String port = portField.getText().toString().trim();
        return (host.isBlank() ? "<empty-ip>" : host)
                + ":" + (port.isBlank() ? "<empty-port>" : port);
    }

    private void setStatus(int stringResource) {
        statusView.setText(stringResource);
    }

    private void setStatus(String message) {
        statusView.setText(message);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        ControllerLog.info("Main/UI", "Main screen destroyed");
        if (discovery != null) {
            discovery.stop();
        }
        adbClient.disconnect();
        remoteRefreshHandler.removeCallbacks(remoteRefresh);
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (remotePanel != null && remotePanel.getVisibility() == View.VISIBLE) {
            remoteRefreshHandler.postDelayed(remoteRefresh, 5_000L);
        }
    }

    @Override
    protected void onPause() {
        remoteRefreshHandler.removeCallbacks(remoteRefresh);
        super.onPause();
    }
}
