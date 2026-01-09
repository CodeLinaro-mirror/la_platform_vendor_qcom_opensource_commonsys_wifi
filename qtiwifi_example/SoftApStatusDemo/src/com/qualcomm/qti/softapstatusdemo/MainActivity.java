/* Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.qualcomm.qti.softapstatusdemo;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.TetheringManager;
import android.net.wifi.WifiManager;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiClient;
import android.net.wifi.SoftApInfo;
import android.net.wifi.SoftApCapability;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.qualcomm.qti.qtiwifi.QtiWifiManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "SoftApStatusDemo";

    // UI Components
    private EditText editTextSsid;
    private EditText editTextPassword;
    private Spinner spinnerSecurity;
    private Button buttonStartSoftAp;
    private Button buttonStopSoftAp;
    private Button buttonRegisterCallback;
    private Button buttonUnregisterCallback;
    private Button buttonClearLog;
    private TextView textViewCombinedStatus;
    private TextView textViewConnectedClients;

    // Managers
    private WifiManager mWifiManager;
    private static QtiWifiManager mQtiWifiManager = null;
    private Handler mHandler;

    // State
    private boolean mCallbackRegistered = false;
    private boolean mSoftApRunning = false;
    private SimpleDateFormat mTimeFormat;
    private int mSelectedSecurityType = 0; // 0=Open, 1=WPA2, 2=WPA3

    // QtiWifi Vendor Event Callback
    private QtiWifiManager.VendorEventCallback mVendorEventCallback =
        new QtiWifiManager.VendorEventCallback() {

        @Override
        public void onStaConnecting(String ifname, String macAddress) {
            Log.i(TAG, "onStaConnecting: ifname=" + ifname + ", MAC=" + macAddress);
            
            mHandler.post(() -> {
                String timestamp = mTimeFormat.format(new Date());
                String logEntry = "[" + timestamp + "] [Vendor] AP-STA-CONNECTING " + macAddress + "\n";
                logEntry += "  → STA " + macAddress + " is connecting...\n";
                appendVendorEvent(logEntry + "\n");
            });
        }
        
        @Override
        public void onPasswordWrong(String ifname, String macAddress, int reasonCode) {
            Log.i(TAG, "onPasswordWrong: ifname=" + ifname + ", MAC=" + macAddress 
                  + ", reason=" + reasonCode);
            
            mHandler.post(() -> {
                String timestamp = mTimeFormat.format(new Date());
                String reasonStr = QtiWifiManager.passwordWrongReasonToString(reasonCode);
                String logEntry = "[" + timestamp + "] [Vendor] AP-STA-PASSWORD-WRONG " 
                                + macAddress + " reason=" + reasonCode + "\n";
                logEntry += "  → STA " + macAddress + " failed: " + reasonStr + "\n";
                appendVendorEvent(logEntry + "\n");
            });
        }

        @Override
        public void onThermalChanged(String ifname, int level) {
            Log.i(TAG, "onThermalChanged: ifname=" + ifname + ", level=" + level);
        }

        @Override
        public void onCongestionChanged(String ifname, int percentage) {
            Log.i(TAG, "onCongestionChanged: ifname=" + ifname + ", percentage=" + percentage);
        }
    };

    // QtiWifiManager initialization callback
    private QtiWifiManager.ApplicationBinderCallback mApplicationCallback = 
        new QtiWifiManager.ApplicationBinderCallback() {
        @Override
        public void onAvailable(QtiWifiManager qtiWifiManager) {
            Log.d(TAG, "QtiWifiManager onAvailable");
            mQtiWifiManager = qtiWifiManager;
            mHandler.post(() -> {
                Toast.makeText(MainActivity.this, 
                    "QtiWifiManager initialized", Toast.LENGTH_SHORT).show();
                appendNativeStatus("QtiWifiManager initialized successfully");
                // Don't register callback here - wait for AP to be enabled
                // Check if AP is already running
                if (mWifiManager.isWifiApEnabled()) {
                    appendNativeStatus("SoftAP already running, will register callback after delay");
                    mHandler.postDelayed(() -> {
                        registerVendorCallback();
                    }, 2000); // 2 second delay to ensure hostapd is ready
                }
            });
        }
    };

    // SoftAP state change receiver
    private BroadcastReceiver mSoftApStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.WIFI_AP_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_AP_STATE, 
                    WifiManager.WIFI_AP_STATE_FAILED);
                String stateStr = getSoftApStateString(state);
                appendNativeStatus("SoftAP State Changed: " + stateStr);
                mSoftApRunning = (state == WifiManager.WIFI_AP_STATE_ENABLED);
            }
        }
    };

    // SoftAP callback for connected clients and status
    private WifiManager.SoftApCallback mSoftApCallback = new WifiManager.SoftApCallback() {
        @Override
        public void onConnectedClientsChanged(List<WifiClient> clients) {
            Log.d(TAG, "onConnectedClientsChanged: " + clients.size() + " clients");
            mHandler.post(() -> {
                appendNativeStatus("onConnectedClientsChanged: " + clients.size() + " clients");
                updateConnectedClients(clients);
            });
        }

        @Override
        public void onInfoChanged(SoftApInfo info) {
            Log.d(TAG, "onInfoChanged: " + info);
            mHandler.post(() -> {
                appendNativeStatus("onInfoChanged: " + info.toString());
            });
        }

        @Override
        public void onCapabilityChanged(SoftApCapability capability) {
            Log.d(TAG, "onCapabilityChanged: " + capability);
            mHandler.post(() -> {
                appendNativeStatus("onCapabilityChanged: " + capability.toString());
            });
        }

        @Override
        public void onStateChanged(int state, int failureReason) {
            Log.d(TAG, "onStateChanged: state=" + state + ", reason=" + failureReason);
            mHandler.post(() -> {
                String stateStr = getSoftApStateString(state);
                int delay = 0;
                String msg = "onStateChanged: " + stateStr;
                if (failureReason != 0) {
                    msg += ", failureReason=" + failureReason;
                }
                appendNativeStatus(msg);
                mSoftApRunning = (state == WifiManager.WIFI_AP_STATE_ENABLED);
                // Register vendor callback in both ENABLING and ENABLED stages for robustness
                // ENABLING: matches system service HAL initialization timing
                // ENABLED: backup registration in case ENABLING registration fails
                if ((state == WifiManager.WIFI_AP_STATE_ENABLING || state == WifiManager.WIFI_AP_STATE_ENABLED) 
                        && mQtiWifiManager != null) {
                    if (state == WifiManager.WIFI_AP_STATE_ENABLING) {
                        delay = 2000;
                    } else {
                        delay = 0;
                    }
                    String stageStr = (state == WifiManager.WIFI_AP_STATE_ENABLING) ? "enabling" : "enabled";
                    appendNativeStatus("SoftAP " + stageStr + " - will register vendor callback after delay " + delay);
                    Log.d(TAG, "SoftAP " + stageStr + " - will register vendor callback after delay " + delay);
                    mHandler.postDelayed(() -> {
                        appendNativeStatus("Starting vendor callback registration...");
                        Log.d(TAG, "Starting vendor callback registration...");
                        if (mCallbackRegistered) {
                            // Unregister old callback first
                            try {
                                appendNativeStatus("Unregistering old vendor callback...");
                                Log.d(TAG, "Unregistering old vendor callback...");
                                mQtiWifiManager.unregisterVendorEventCallback(mVendorEventCallback);
                                mCallbackRegistered = false;
                                appendNativeStatus("Old vendor callback unregistered successfully");
                                Log.d(TAG, "Old vendor callback unregistered successfully");
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to unregister before re-register", e);
                                appendNativeStatus("Failed to unregister: " + e.getMessage());
                            }
                        }
                        // Register callback
                        appendNativeStatus("Attempting to register vendor callback...");
                        Log.d(TAG, "Attempting to register vendor callback...");
                        registerVendorCallback();
                    }, delay);
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mHandler = new Handler(Looper.getMainLooper());
        mTimeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

        // Initialize WifiManager
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        // Initialize QtiWifiManager
        QtiWifiManager.initialize(this, mApplicationCallback);
        Log.i(TAG, "QtiWifiManager.initialize() called");

        // Initialize UI
        initializeViews();

        // Register SoftAP state receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        registerReceiver(mSoftApStateReceiver, filter);

        // Register SoftAP callback
        try {
            mWifiManager.registerSoftApCallback(getMainExecutor(), mSoftApCallback);
            appendNativeStatus("SoftAP callback registered");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register SoftAP callback", e);
            appendNativeStatus("Failed to register SoftAP callback: " + e.getMessage());
        }
    }

    private void initializeViews() {
        // Configuration fields
        editTextSsid = findViewById(R.id.editTextSsid);
        editTextPassword = findViewById(R.id.editTextPassword);
        spinnerSecurity = findViewById(R.id.spinnerSecurity);

        // Set default SSID
        editTextSsid.setText("TestAP_" + android.os.Build.MODEL);

        // Setup security spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.security_types, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSecurity.setAdapter(adapter);
        spinnerSecurity.setSelection(1); // Default to WPA2
        spinnerSecurity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mSelectedSecurityType = position;
                // Enable/disable password field based on security type
                editTextPassword.setEnabled(position != 0); // Disable for Open
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Buttons
        buttonStartSoftAp = findViewById(R.id.buttonStartSoftAp);
        buttonStopSoftAp = findViewById(R.id.buttonStopSoftAp);
        buttonRegisterCallback = findViewById(R.id.buttonRegisterCallback);
        buttonUnregisterCallback = findViewById(R.id.buttonUnregisterCallback);
        buttonClearLog = findViewById(R.id.buttonClearLog);

        // Status views
        textViewCombinedStatus = findViewById(R.id.textViewCombinedStatus);
        textViewConnectedClients = findViewById(R.id.textViewConnectedClients);

        // Set text size and color for status views
        textViewCombinedStatus.setTextSize(16);
        textViewCombinedStatus.setTextColor(0xFF000000); // Black

        // Set scrolling
        textViewCombinedStatus.setMovementMethod(ScrollingMovementMethod.getInstance());

        // Set click listeners
        buttonStartSoftAp.setOnClickListener(this);
        buttonStopSoftAp.setOnClickListener(this);
        buttonRegisterCallback.setOnClickListener(this);
        buttonUnregisterCallback.setOnClickListener(this);
        buttonClearLog.setOnClickListener(this);

        // Initial status
        appendNativeStatus("App started - Ready to configure SoftAP");
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.buttonStartSoftAp) {
            startSoftAp();
        } else if (id == R.id.buttonStopSoftAp) {
            stopSoftAp();
        } else if (id == R.id.buttonRegisterCallback) {
            registerVendorCallback();
        } else if (id == R.id.buttonUnregisterCallback) {
            unregisterVendorCallback();
        } else if (id == R.id.buttonClearLog) {
            clearLog();
        }
    }

    private void startSoftAp() {
        Log.d(TAG, "startSoftAp()");

        if (mSoftApRunning) {
            showMessage(getString(R.string.softap_already_running));
            return;
        }

        // Validate input
        String ssid = editTextSsid.getText().toString().trim();
        if (TextUtils.isEmpty(ssid)) {
            showMessage(getString(R.string.invalid_ssid));
            return;
        }

        String password = editTextPassword.getText().toString();
        if (mSelectedSecurityType != 0 && password.length() < 8) {
            showMessage(getString(R.string.invalid_password));
            return;
        }

        try {
            // Build SoftAP configuration
            SoftApConfiguration.Builder configBuilder = new SoftApConfiguration.Builder();
            configBuilder.setSsid(ssid);

            // Set security type
            switch (mSelectedSecurityType) {
                case 0: // Open
                    configBuilder.setPassphrase(null, SoftApConfiguration.SECURITY_TYPE_OPEN);
                    appendNativeStatus("Configuring Open network: " + ssid);
                    break;
                case 1: // WPA2-PSK
                    configBuilder.setPassphrase(password, SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
                    appendNativeStatus("Configuring WPA2-PSK network: " + ssid);
                    break;
                case 2: // WPA3-SAE
                    configBuilder.setPassphrase(password, SoftApConfiguration.SECURITY_TYPE_WPA3_SAE);
                    appendNativeStatus("Configuring WPA3-SAE network: " + ssid);
                    break;
            }

            SoftApConfiguration config = configBuilder.build();
            // Use WifiManager.startTetheredHotspot for Android 13
            // This method properly starts the DHCP server and allows IP address allocation
            appendNativeStatus("Starting SoftAP with startTetheredHotspot...");
            boolean result = mWifiManager.startTetheredHotspot(config);
            if (result) {
                appendNativeStatus("SoftAP start requested successfully");
                showMessage(getString(R.string.softap_started));
            } else {
                appendNativeStatus("SoftAP start request failed");
                showMessage(getString(R.string.softap_start_failed));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start SoftAP", e);
            appendNativeStatus("Exception starting SoftAP: " + e.getMessage());
            showMessage(getString(R.string.softap_start_failed) + ": " + e.getMessage());
        }
    }

    private void stopSoftAp() {
        Log.d(TAG, "stopSoftAp()");
        // Unregister vendor callback before stopping SoftAP
        if (mCallbackRegistered && mQtiWifiManager != null) {
            try {
                appendNativeStatus("Unregistering vendor callback before stopping SoftAP...");
                mQtiWifiManager.unregisterVendorEventCallback(mVendorEventCallback);
                mCallbackRegistered = false;
                appendNativeStatus("Vendor callback unregistered successfully");
            } catch (Exception e) {
                Log.e(TAG, "Failed to unregister vendor callback", e);
                appendNativeStatus("Failed to unregister vendor callback: " + e.getMessage());
            }
        }
        try {
            boolean result = mWifiManager.stopSoftAp();
            if (result) {
                appendNativeStatus("SoftAP stop requested successfully");
                showMessage(getString(R.string.softap_stopped));
                mSoftApRunning = false;
            } else {
                appendNativeStatus("SoftAP stop request failed");
                showMessage(getString(R.string.softap_stop_failed));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop SoftAP", e);
            appendNativeStatus("Exception stopping SoftAP: " + e.getMessage());
            showMessage(getString(R.string.softap_stop_failed) + ": " + e.getMessage());
        }
    }

    private void registerVendorCallback() {
        if (mQtiWifiManager == null) {
            appendNativeStatus("Cannot register vendor callback: QtiWifiManager is null");
            Log.e(TAG, "Cannot register vendor callback: QtiWifiManager is null");
            showMessage(getString(R.string.qtiwifi_not_available));
            return;
        }
        if (mCallbackRegistered) {
            appendNativeStatus("Vendor callback already registered, skipping");
            Log.d(TAG, "Vendor callback already registered, skipping");
            showMessage("Callback already registered");
            return;
        }
        try {
            appendNativeStatus("Calling QtiWifiManager.registerVendorEventCallback()...");
            Log.d(TAG, "Calling QtiWifiManager.registerVendorEventCallback()...");
            mQtiWifiManager.registerVendorEventCallback(mVendorEventCallback, null);
            mCallbackRegistered = true;
            showMessage(getString(R.string.callback_registered));
            String timestamp = mTimeFormat.format(new Date());
            appendVendorEvent("[" + timestamp + "] [Vendor] Vendor callback registered successfully\n\n");
            Log.i(TAG, "Vendor event callback registered successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register callback", e);
            appendNativeStatus("Exception registering vendor callback: " + e.getMessage());
            appendNativeStatus("Stack trace: " + Log.getStackTraceString(e));
            showMessage("Failed to register callback: " + e.getMessage());
        }
    }

    private void unregisterVendorCallback() {
        if (mQtiWifiManager == null) {
            appendNativeStatus("Cannot unregister vendor callback: QtiWifiManager is null");
            showMessage(getString(R.string.qtiwifi_not_available));
            return;
        }
        if (!mCallbackRegistered) {
            appendNativeStatus("Vendor callback not registered, skipping unregister");
            showMessage("Callback not registered");
            return;
        }
        try {
            appendNativeStatus("Calling QtiWifiManager.unregisterVendorEventCallback()...");
            mQtiWifiManager.unregisterVendorEventCallback(mVendorEventCallback);
            mCallbackRegistered = false;
            showMessage(getString(R.string.callback_unregistered));
            String timestamp = mTimeFormat.format(new Date());
            appendVendorEvent("[" + timestamp + "] [Vendor] Vendor callback unregistered successfully\n\n");
            Log.i(TAG, "Vendor event callback unregistered successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister callback", e);
            appendNativeStatus("Exception unregistering vendor callback: " + e.getMessage());
            showMessage("Failed to unregister callback: " + e.getMessage());
        }
    }

    private void clearLog() {
        textViewCombinedStatus.setText(getString(R.string.no_status));
    }

    private void appendNativeStatus(String status) {
        String timestamp = mTimeFormat.format(new Date());
        String statusText = "[" + timestamp + "] [Native] " + status + "\n";
        String currentText = textViewCombinedStatus.getText().toString();
        if (currentText.equals(getString(R.string.no_status))) {
            textViewCombinedStatus.setText(statusText);
        } else {
            textViewCombinedStatus.append(statusText);
        }
        scrollToBottom(textViewCombinedStatus);
    }

    private void appendVendorEvent(String event) {
        String currentText = textViewCombinedStatus.getText().toString();
        if (currentText.equals(getString(R.string.no_status))) {
            textViewCombinedStatus.setText(event);
        } else {
            textViewCombinedStatus.append(event);
        }
        scrollToBottom(textViewCombinedStatus);
    }

    private void updateConnectedClients(List<WifiClient> clients) {
        if (clients == null || clients.isEmpty()) {
            textViewConnectedClients.setText(getString(R.string.no_clients));
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Connected Clients: ").append(clients.size()).append("\n\n");
        for (int i = 0; i < clients.size(); i++) {
            WifiClient client = clients.get(i);
            sb.append((i + 1)).append(". ");
            sb.append("MAC: ").append(client.getMacAddress()).append("\n");
            sb.append("\n");
        }
        textViewConnectedClients.setText(sb.toString());
    }

    private String getSoftApStateString(int state) {
        switch (state) {
            case WifiManager.WIFI_AP_STATE_DISABLED:
                return "DISABLED";
            case WifiManager.WIFI_AP_STATE_DISABLING:
                return "DISABLING";
            case WifiManager.WIFI_AP_STATE_ENABLED:
                return "ENABLED";
            case WifiManager.WIFI_AP_STATE_ENABLING:
                return "ENABLING";
            case WifiManager.WIFI_AP_STATE_FAILED:
                return "FAILED";
            default:
                return "UNKNOWN(" + state + ")";
        }
    }

    private void scrollToBottom(TextView textView) {
        if (textView.getLayout() != null) {
            int scrollAmount = textView.getLayout().getLineTop(textView.getLineCount()) 
                - textView.getHeight();
            if (scrollAmount > 0) {
                textView.scrollTo(0, scrollAmount);
            } else {
                textView.scrollTo(0, 0);
            }
        }
    }

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy()");
        super.onDestroy();
        // Stop SoftAP if running
        if (mSoftApRunning) {
            stopSoftAp();
        }
        // Unregister receivers
        try {
            unregisterReceiver(mSoftApStateReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister receiver", e);
        }
        // Unregister SoftAP callback
        try {
            mWifiManager.unregisterSoftApCallback(mSoftApCallback);
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister SoftAP callback", e);
        }
        // Unregister vendor callback
        if (mCallbackRegistered && mQtiWifiManager != null) {
            try {
                mQtiWifiManager.unregisterVendorEventCallback(mVendorEventCallback);
            } catch (Exception e) {
                Log.e(TAG, "Failed to unregister vendor callback", e);
            }
        }
        // Unbind QtiWifiManager service
        try {
            QtiWifiManager.unbindService(getApplicationContext());
        } catch (Exception e) {
            Log.e(TAG, "Failed to unbind QtiWifiManager", e);
        }
    }
}
