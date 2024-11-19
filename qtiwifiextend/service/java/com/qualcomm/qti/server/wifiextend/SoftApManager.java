/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Changes from Qualcomm Innovation Center, Inc. are provided under the following license:
 * Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.qualcomm.qti.server.wifiextend;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import android.content.Context;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Message;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.WorkSource;
import android.text.TextUtils;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.util.Log;

import com.qualcomm.qti.server.wifiextend.WifiNative.SoftApHalCallback;
import com.qualcomm.qti.server.wifiextend.WifiNative.InterfaceCallback;
import com.qualcomm.qti.server.wifiextend.statemachine.IState;
import com.qualcomm.qti.server.wifiextend.statemachine.State;
import com.qualcomm.qti.server.wifiextend.statemachine.StateMachine;
//import com.qualcomm.qti.server.wifiextend.ApConfigUtil.SUCCESS;
//import com.qualcomm.qti.server.wifiextend.ApConfigUtil.ERROR_NO_CHANNEL;
//import com.qualcomm.qti.server.wifiextend.ApConfigUtil.ERROR_GENERIC;
//import com.qualcomm.qti.server.wifiextend.ApConfigUtil.ERROR_UNSUPPORTED_CONFIGURATION;

import com.qualcomm.qti.wifiextend.MacAddress;
import com.qualcomm.qti.wifiextend.QtiWifiExtendManager;
import com.qualcomm.qti.wifiextend.SoftApConfiguration;
import com.qualcomm.qti.wifiextend.SoftApInfo;
import com.qualcomm.qti.wifiextend.WifiClient;
import com.qualcomm.qti.wifiextend.WifiSsid;

public class SoftApManager implements ActiveModeManager {
    private static final String TAG = "ExtendSoftApManager";
    private final Context mContext;
    private final WifiNative mWifiNative;
    private final QtiWifiExtendInjector mWifiInjector;
    private final ActiveModeWarden mActiveModeWarden;
    private String mCountryCode;

    private final Listener<SoftApManager> mModeListener;
    private final QtiWifiExtendServiceImpl.SoftApListener mSoftApCallback;

    private final SoftApStateMachine mStateMachine;

    private String mApInterfaceName;

    @Nullable
    private WorkSource mRequestorWs = null;

    private final SoftApConfigStore mSoftApConfigStore;

    private final SoftApModeConfiguration mOriginalModeConfiguration;

    private SoftApConfiguration mCurrentSoftApConfiguration;

    private Map<String, SoftApInfo> mCurrentSoftApInfoMap = new HashMap<>();

    private Map<String, List<WifiClient>> mConnectedClientWithApInfoMap = new HashMap<>();
    Map<WifiClient, Integer> mPendingDisconnectClients = new HashMap<>();

    private Set<MacAddress> mBlockedClientList = new HashSet<>();
    private Set<MacAddress> mAllowedClientList = new HashSet<>();

    static final long SOFT_AP_PENDING_DISCONNECTION_CHECK_DELAY_MS = 1000;

    //private Set<Integer> mSafeChannelFrequencyList = new HashSet<>();
    /**
     * Listener for soft AP events.
     * Register to WifiNative layer.
     */
    private final SoftApHalCallback mSoftApHalCallback = new SoftApHalCallback() {
        @Override
        public void onFailure() {
            mStateMachine.sendMessage(SoftApStateMachine.CMD_FAILURE);
        }

        @Override
        public void onInstanceFailure(String instanceName) {
            mStateMachine.sendMessage(SoftApStateMachine.CMD_FAILURE, instanceName);
        }

        @Override
        public void onInfoChanged(String apIfaceInstance, int frequency,
            int bandwidth,
            int generation,
            MacAddress apIfaceInstanceMacAddress) {
            SoftApInfo apInfo = new SoftApInfo();
            apInfo.setFrequency(frequency);
            apInfo.setBandwidth(bandwidth);
            apInfo.setWifiStandard(generation);
            if (apIfaceInstanceMacAddress != null) {
                apInfo.setBssid(apIfaceInstanceMacAddress);
            }
            apInfo.setApInstanceIdentifier(apIfaceInstance != null
                    ? apIfaceInstance : mApInterfaceName);
            mStateMachine.sendMessage(
                    SoftApStateMachine.CMD_AP_INFO_CHANGED, 0, 0, apInfo);
        }

        @Override
        public void onConnectedClientsChanged(String apIfaceInstance, MacAddress clientAddress,
                boolean isConnected) {
            if (clientAddress != null) {
                WifiClient client = new WifiClient(clientAddress, apIfaceInstance != null
                        ? apIfaceInstance : mApInterfaceName);
                mStateMachine.sendMessage(SoftApStateMachine.CMD_ASSOCIATED_STATIONS_CHANGED,
                        isConnected ? 1 : 0, 0, client);
            } else {
                Log.e(getTag(), "onConnectedClientsChanged: Invalid type returned");
            }
        }
    };

    public SoftApManager(
            Context context,
            Looper looper,
            WifiNative wifiNative,
            QtiWifiExtendInjector wifiInjector,
            ActiveModeManager.Listener<SoftApManager> listener,
            QtiWifiExtendServiceImpl.SoftApListener callback,
            SoftApConfigStore softApConfigStore,
            SoftApModeConfiguration apConfig,
            ActiveModeWarden activeModeWarden,
            WorkSource requestorWs) {
        mContext = context;
        mWifiNative = wifiNative;
        mWifiInjector = wifiInjector;
        mCountryCode = apConfig.getCountryCode();
        mModeListener = listener;
        mSoftApCallback = callback;
        mSoftApConfigStore = softApConfigStore;
        mActiveModeWarden = activeModeWarden;
        mCurrentSoftApConfiguration = apConfig.getSoftApConfiguration();
        // null is a valid input and means we use the user-configured SoftAp settings.
        if (mCurrentSoftApConfiguration == null) {
            mCurrentSoftApConfiguration = mSoftApConfigStore.getSoftApConfiguration();
            // may still be null if we fail to load the default config
        }
        mOriginalModeConfiguration = new SoftApModeConfiguration(mCurrentSoftApConfiguration, mCountryCode);
        mStateMachine = new SoftApStateMachine(looper);
        configureInternalConfiguration();

        mStateMachine.sendMessage(SoftApStateMachine.CMD_START, requestorWs);
    }

    @Override
    public void stop() {
        Log.d(getTag(), " currentstate: " + getCurrentStateName());
        mStateMachine.sendMessage(SoftApStateMachine.CMD_STOP);
    }


    private String getTag() {
        return TAG + "[" + (mApInterfaceName == null ? "unknown" : mApInterfaceName) + "]";
    }

    public WorkSource getRequestorWs() {
        return mRequestorWs;
    }

    private String getCurrentStateName() {
        IState currentState = mStateMachine.getCurrentState();

        if (currentState != null) {
            return currentState.getName();
        }

        return "StateMachine not active";
    }

    public SoftApModeConfiguration getSoftApModeConfiguration() {
        return new SoftApModeConfiguration(
                mCurrentSoftApConfiguration, mCountryCode);
    }

    public void updateConfiguration(@NonNull SoftApConfiguration config) {
        mStateMachine.sendMessage(SoftApStateMachine.CMD_UPDATE_CONFIG, config);
    }

    private void updateChangeableConfiguration(SoftApConfiguration newConfig) {
        if (mCurrentSoftApConfiguration == null || newConfig == null) {
            return;
        }

        SoftApConfiguration.Builder newConfigurBuilder =
                new SoftApConfiguration.Builder(mCurrentSoftApConfiguration)
                .setAllowedClientList(newConfig.getAllowedClientList())
                .setBlockedClientList(newConfig.getBlockedClientList())
                .setMaxNumberOfClients(newConfig.getMaxNumberOfClients());

        mCurrentSoftApConfiguration = newConfigurBuilder.build();
        configureInternalConfiguration();
    }

    private void configureInternalConfiguration() {
        if (mCurrentSoftApConfiguration == null) {
            return;
        }
        mBlockedClientList = new HashSet<>(mCurrentSoftApConfiguration.getBlockedClientList());
        mAllowedClientList = new HashSet<>(mCurrentSoftApConfiguration.getAllowedClientList());
    }

    private void disconnectAllClients() {
        for (WifiClient client : getConnectedClientList()) {
            mWifiNative.forceClientDisconnect(mApInterfaceName, client.getMacAddress(),
                    QtiWifiExtendManager.SAP_CLIENT_DISCONNECT_REASON_CODE_UNSPECIFIED);
        }
    }

    private void stopSoftAp() {
        disconnectAllClients();
        mWifiNative.teardownInterface(mApInterfaceName);
        Log.d(getTag(), "Soft AP is stopped");
    }

    private void sendBroadcastApConnectionsChanged() {
        final Intent intent = new Intent(QtiWifiExtendManager.WIFI_AP_CLIENTS_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);

        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void addClientToPendingDisconnectionList(WifiClient client, int reason) {
        Log.d(getTag(), "Fail to disconnect client: " + client.getMacAddress()
                + ", add it into pending list");
        mPendingDisconnectClients.put(client, reason);
        mStateMachine.getHandler().removeMessages(
                SoftApStateMachine.CMD_FORCE_DISCONNECT_PENDING_CLIENTS);
        mStateMachine.sendMessageDelayed(
                SoftApStateMachine.CMD_FORCE_DISCONNECT_PENDING_CLIENTS,
                SOFT_AP_PENDING_DISCONNECTION_CHECK_DELAY_MS);
    }

    private List<WifiClient> getConnectedClientList() {
        List<WifiClient> connectedClientList = new ArrayList<>();
        for (List<WifiClient> it : mConnectedClientWithApInfoMap.values()) {
            connectedClientList.addAll(it);
        }
        return connectedClientList;
    }

    private void updateApState(int newState, int currentState, int reason) {
        mSoftApCallback.onStateChanged(newState, reason);

        //send the AP state change broadcast
        final Intent intent = new Intent(QtiWifiExtendManager.WIFI_AP_STATE_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        intent.putExtra(QtiWifiExtendManager.EXTRA_WIFI_AP_STATE, newState);
        intent.putExtra(QtiWifiExtendManager.EXTRA_PREVIOUS_WIFI_AP_STATE, currentState);
        if (newState == QtiWifiExtendManager.WIFI_AP_STATE_FAILED) {
            //only set reason number when softAP start failed
            intent.putExtra(QtiWifiExtendManager.EXTRA_WIFI_AP_FAILURE_REASON, reason);
        }

        intent.putExtra(QtiWifiExtendManager.EXTRA_WIFI_AP_INTERFACE_NAME, mApInterfaceName);

    }

    private int setMacAddress() {
        MacAddress mac = mCurrentSoftApConfiguration.getBssid();

        if (mac == null) {
            // If no BSSID is explicitly requested, (re-)configure the factory MAC address. Some
            // drivers may not support setting the MAC at all, so fail soft in this case.
            if (!mWifiNative.resetApMacToFactoryMacAddress(mApInterfaceName)) {
                Log.w(getTag(), "failed to reset to factory MAC address; "
                        + "continuing with current MAC");
            }
        } else {
            if (!mWifiNative.setApMacAddress(mApInterfaceName, mac)) {
                Log.e(getTag(), "failed to set explicitly requested MAC address");
                return ApConfigUtil.ERROR_GENERIC;
            }
        }

        return ApConfigUtil.SUCCESS;
    }

    private int startSoftAp() {
        Log.d(getTag(), "startSoftAp: channels " + mCurrentSoftApConfiguration.getChannels()
                    + " iface " + mApInterfaceName + " country " + mCountryCode);

        int result = setMacAddress();
        if (result != ApConfigUtil.SUCCESS) {
            return result;
        }

        // Make a copy of configuration for updating AP band and channel.
        SoftApConfiguration.Builder localConfigBuilder =
                new SoftApConfiguration.Builder(mCurrentSoftApConfiguration);

//        result = ApConfigUtil.updateApChannelConfig(
//                mWifiNative, mCoexManager, mContext.getResources(), mCountryCode,
//                localConfigBuilder, mCurrentSoftApConfiguration, mCurrentSoftApCapability);

        if (mCurrentSoftApConfiguration.isHiddenSsid()) {
            Log.d(getTag(), "SoftAP is a hidden network");
        }

        if (!mWifiNative.startSoftAp(mApInterfaceName,
                  localConfigBuilder.build(), mSoftApHalCallback)) {
            Log.e(getTag(), "Soft AP start failed");
            return ApConfigUtil.ERROR_GENERIC;
        }

        Log.d(getTag(), "Soft AP is started ");

        return ApConfigUtil.SUCCESS;
    }

    private void handleStartSoftApFailure(int result) {
        if (result == ApConfigUtil.SUCCESS) {
            return;
        }
        int failureReason = QtiWifiExtendManager.SAP_START_FAILURE_GENERAL;
        if (result == ApConfigUtil.ERROR_NO_CHANNEL) {
            failureReason = QtiWifiExtendManager.SAP_START_FAILURE_NO_CHANNEL;
        } else if (result == ApConfigUtil.ERROR_UNSUPPORTED_CONFIGURATION) {
            failureReason = QtiWifiExtendManager.SAP_START_FAILURE_UNSUPPORTED_CONFIGURATION;
        }
        updateApState(QtiWifiExtendManager.WIFI_AP_STATE_FAILED,
                QtiWifiExtendManager.WIFI_AP_STATE_ENABLING,
                failureReason);
        stopSoftAp();
    }

    private int setCountryCode() {
        int band = mCurrentSoftApConfiguration.getBand();
        if (TextUtils.isEmpty(mCountryCode)) {
            if (band == SoftApConfiguration.BAND_5GHZ || band == SoftApConfiguration.BAND_6GHZ) {
                // Country code is mandatory for 5GHz/6GHz band.
                Log.e(getTag(), "Invalid country code, "
                        + "required for setting up soft ap in band:" + band);
                return ApConfigUtil.ERROR_GENERIC;
            }
            // Absence of country code is not fatal for 2Ghz & Any band options.
            return ApConfigUtil.SUCCESS;
        }
        if (!mWifiNative.setApCountryCode(
                mApInterfaceName, mCountryCode.toUpperCase(Locale.ROOT))) {
            if (band == SoftApConfiguration.BAND_5GHZ || band == SoftApConfiguration.BAND_6GHZ) {
                // Return an error if failed to set country code when AP is configured for
                // 5GHz/6GHz band.
                Log.e(getTag(), "Failed to set country code, "
                        + "required for setting up soft ap in band: " + band);
                return ApConfigUtil.ERROR_GENERIC;
            }
            // Failure to set country code is not fatal for other band options.
        }
        return ApConfigUtil.SUCCESS;
    }

    private boolean checkSoftApClient(SoftApConfiguration config, WifiClient newClient) {
        if (mBlockedClientList.contains(newClient.getMacAddress())) {
            Log.d(getTag(), "Force disconnect for client: " + newClient + "in blocked list");
            if (!mWifiNative.forceClientDisconnect(
                    mApInterfaceName, newClient.getMacAddress(),
                    QtiWifiExtendManager.SAP_CLIENT_BLOCK_REASON_CODE_BLOCKED_BY_USER)) {
                addClientToPendingDisconnectionList(newClient,
                        QtiWifiExtendManager.SAP_CLIENT_BLOCK_REASON_CODE_BLOCKED_BY_USER);
            }
            return false;
        }
        if (!mAllowedClientList.contains(newClient.getMacAddress())) {
            //mSoftApCallback.onBlockedClientConnecting(newClient,
            //        QtiWifiExtendManager.SAP_CLIENT_BLOCK_REASON_CODE_BLOCKED_BY_USER);
            Log.d(getTag(), "Force disconnect for unauthorized client: " + newClient);
            if (!mWifiNative.forceClientDisconnect(
                    mApInterfaceName, newClient.getMacAddress(),
                    QtiWifiExtendManager.SAP_CLIENT_BLOCK_REASON_CODE_BLOCKED_BY_USER)) {
                addClientToPendingDisconnectionList(newClient,
                        QtiWifiExtendManager.SAP_CLIENT_BLOCK_REASON_CODE_BLOCKED_BY_USER);
            }
            return false;
        }
        int maxConfig = 32;
        if (config.getMaxNumberOfClients() > 0) {
            maxConfig = Math.min(maxConfig, config.getMaxNumberOfClients());
        }

        if (getConnectedClientList().size() >= maxConfig) {
            Log.i(getTag(), "No more room for new client:" + newClient);
            if (!mWifiNative.forceClientDisconnect(
                    mApInterfaceName, newClient.getMacAddress(),
                    QtiWifiExtendManager.SAP_CLIENT_BLOCK_REASON_CODE_NO_MORE_STAS)) {
                addClientToPendingDisconnectionList(newClient,
                        QtiWifiExtendManager.SAP_CLIENT_BLOCK_REASON_CODE_NO_MORE_STAS);
            }
            //mSoftApCallback.onBlockedClientConnecting(newClient,
            //        QtiWifiExtendManager.SAP_CLIENT_BLOCK_REASON_CODE_NO_MORE_STAS);
            // Avoid report the max client blocked in the same settings.
            return false;
        }
        return true;
    }

 private class SoftApStateMachine extends StateMachine {
        // Commands for the state machine.
        public static final int CMD_START = 0;
        public static final int CMD_STOP = 1;
        public static final int CMD_FAILURE = 2;
        public static final int CMD_INTERFACE_STATUS_CHANGED = 3;
        public static final int CMD_ASSOCIATED_STATIONS_CHANGED = 4;
        public static final int CMD_NO_ASSOCIATED_STATIONS_TIMEOUT = 5;
        public static final int CMD_INTERFACE_DESTROYED = 7;
        public static final int CMD_INTERFACE_DOWN = 8;
        public static final int CMD_AP_INFO_CHANGED = 9;
        public static final int CMD_UPDATE_CAPABILITY = 10;
        public static final int CMD_UPDATE_CONFIG = 11;
        public static final int CMD_FORCE_DISCONNECT_PENDING_CLIENTS = 12;
        public static final int CMD_NO_ASSOCIATED_STATIONS_TIMEOUT_ON_ONE_INSTANCE = 13;
        public static final int CMD_SAFE_CHANNEL_FREQUENCY_CHANGED = 14;
        public static final int CMD_HANDLE_WIFI_CONNECTED = 15;
        public static final int CMD_UPDATE_COUNTRY_CODE = 16;
        public static final int CMD_DRIVER_COUNTRY_CODE_CHANGED = 17;
        public static final int CMD_DRIVER_COUNTRY_CODE_CHANGE_TIMED_OUT = 18;
        public static final int CMD_PLUGGED_STATE_CHANGED = 19;

        private final State mActiveState = new ActiveState();
        private final State mIdleState;
        private final State mStartedState;

        private final InterfaceCallback mWifiNativeInterfaceCallback = new InterfaceCallback() {
            @Override
            public void onDestroyed(String ifaceName) {
                if (mApInterfaceName != null && mApInterfaceName.equals(ifaceName)) {
                    sendMessage(CMD_INTERFACE_DESTROYED);
                }
            }

            @Override
            public void onUp(String ifaceName) {
                if (mApInterfaceName != null && mApInterfaceName.equals(ifaceName)) {
                    sendMessage(CMD_INTERFACE_STATUS_CHANGED, 1);
                }
            }

            @Override
            public void onDown(String ifaceName) {
                if (mApInterfaceName != null && mApInterfaceName.equals(ifaceName)) {
                    sendMessage(CMD_INTERFACE_STATUS_CHANGED, 0);
                }
            }
        };

        SoftApStateMachine(Looper looper) {
            super(TAG, looper);

            final int threshold = 100;
            mIdleState = new IdleState(threshold);
            mStartedState = new StartedState(threshold);
            // CHECKSTYLE:OFF IndentationCheck
            addState(mActiveState);
                addState(mIdleState, mActiveState);
                addState(mStartedState, mActiveState);
            // CHECKSTYLE:ON IndentationCheck

            setInitialState(mIdleState);
            start();
        }

        private class ActiveState extends State {
            @Override
            public void exit() {
                mModeListener.onStopped(SoftApManager.this);
            }
        }

        @Override
        protected String getWhatToString(int what) {
            switch (what) {
                case CMD_START:
                    return "CMD_START";
                case CMD_STOP:
                    return "CMD_STOP";
                case CMD_FAILURE:
                    return "CMD_FAILURE";
                case CMD_INTERFACE_STATUS_CHANGED:
                    return "CMD_INTERFACE_STATUS_CHANGED";
                case CMD_ASSOCIATED_STATIONS_CHANGED:
                    return "CMD_ASSOCIATED_STATIONS_CHANGED";
                case CMD_INTERFACE_DESTROYED:
                    return "CMD_INTERFACE_DESTROYED";
                case CMD_INTERFACE_DOWN:
                    return "CMD_INTERFACE_DOWN";
                case CMD_AP_INFO_CHANGED:
                    return "CMD_AP_INFO_CHANGED";
                case CMD_UPDATE_CONFIG:
                    return "CMD_UPDATE_CONFIG";
                case CMD_FORCE_DISCONNECT_PENDING_CLIENTS:
                    return "CMD_FORCE_DISCONNECT_PENDING_CLIENTS";
                case CMD_SAFE_CHANNEL_FREQUENCY_CHANGED:
                    return "CMD_SAFE_CHANNEL_FREQUENCY_CHANGED";
                case CMD_UPDATE_COUNTRY_CODE:
                    return "CMD_UPDATE_COUNTRY_CODE";
                case RunnerState.STATE_ENTER_CMD:
                    return "Enter";
                case RunnerState.STATE_EXIT_CMD:
                    return "Exit";
                default:
                    return "what:" + what;
            }
        }

        private class IdleState extends RunnerState {
            IdleState(int threshold) {
                super(threshold, mWifiInjector.getExtendWifiHandlerLocalLog());
            }

            @Override
            public void enterImpl() {
                mApInterfaceName = null;
            }

            @Override
            public void exitImpl() {
            }

            @Override
            String getMessageLogRec(int what) {
                return SoftApManager.class.getSimpleName() + "." + IdleState.class.getSimpleName()
                        + "." + getWhatToString(what);
            }

            @Override
            public boolean processMessageImpl(Message message) {
                switch (message.what) {
                    case CMD_STOP:
                        quitNow();
                        break;
                    case CMD_START:
                        mRequestorWs = (WorkSource) message.obj;
                        WifiSsid wifiSsid = mCurrentSoftApConfiguration != null
                                ? mCurrentSoftApConfiguration.getWifiSsid() : null;
                        if (wifiSsid == null || wifiSsid.getBytes().length == 0) {
                            Log.e(getTag(), "Unable to start soft AP without valid configuration");
                            updateApState(QtiWifiExtendManager.WIFI_AP_STATE_FAILED,
                            QtiWifiExtendManager.WIFI_AP_STATE_DISABLED,
                            QtiWifiExtendManager.SAP_START_FAILURE_GENERAL);
                            mModeListener.onStartFailure(SoftApManager.this);
                            break;
                        }
                        //ingore 5G/6G bands doesn't support case in some country code
                        //ingore 6G bands require  WPA3 security type otherwise needs to
                        //remove 6G bands.
                        mApInterfaceName = mWifiNative.setupInterfaceForSoftApMode(
                                mWifiNativeInterfaceCallback, mRequestorWs,
                                mCurrentSoftApConfiguration.getBand(), SoftApManager.this);
                        if (TextUtils.isEmpty(mApInterfaceName)) {
                            Log.e(getTag(), "setup failure when creating ap interface.");
                            updateApState(QtiWifiExtendManager.WIFI_AP_STATE_FAILED,
                                    QtiWifiExtendManager.WIFI_AP_STATE_DISABLED,
                                    QtiWifiExtendManager.SAP_START_FAILURE_GENERAL);
                            mModeListener.onStartFailure(SoftApManager.this);
                            break;
                        }
                        updateApState(QtiWifiExtendManager.WIFI_AP_STATE_ENABLING,
                                QtiWifiExtendManager.WIFI_AP_STATE_DISABLED, 0);
                        int result = setCountryCode();
                        if (result != ApConfigUtil.SUCCESS) {
                            handleStartSoftApFailure(result);
                            break;
                        }
                        result = startSoftAp();
                        if (result != ApConfigUtil.SUCCESS) {
                            handleStartSoftApFailure(result);
                            break;
                        }
                        transitionTo(mStartedState);
                        break;
                    case CMD_UPDATE_CONFIG:
                        SoftApConfiguration newConfig = (SoftApConfiguration) message.obj;
                        Log.d(getTag(), "Configuration changed to " + newConfig);
                        // Idle mode, update all configurations.
                        mCurrentSoftApConfiguration = newConfig;
                        configureInternalConfiguration();
                        break;
                    case CMD_UPDATE_COUNTRY_CODE:
                        String countryCode = (String) message.obj;
                        if (!TextUtils.isEmpty(countryCode)) {
                            mCountryCode = countryCode;
                        }
                        break;
                    default:
                        // Ignore all other commands.
                        break;
                }

                return HANDLED;
            }
        }

        private class StartedState extends RunnerState {
            StartedState(int threshold) {
                super(threshold, mWifiInjector.getExtendWifiHandlerLocalLog());
            }

            private void removeIfaceInstanceFromBridgedApIface(String instanceName) {
                if (TextUtils.isEmpty(instanceName)) {
                    return;
                }
                if (mCurrentSoftApInfoMap.containsKey(instanceName)) {
                    Log.i(getTag(), "remove instance " + instanceName + "("
                            + mCurrentSoftApInfoMap.get(instanceName).getFrequency()
                            + ") from bridged iface " + mApInterfaceName);
                    mWifiNative.removeIfaceInstanceFromBridgedApIface(mApInterfaceName,
                            instanceName);
                    // Remove the info and update it.
                    updateSoftApInfo(mCurrentSoftApInfoMap.get(instanceName), true);
                }
            }

            /**
             * When configuration changed, it need to force some clients disconnect to match the
             * configuration.
             */
            private void updateClientConnection() {
                final int maxAllowedClientsByHardwareAndCarrier = 32;
                final int userApConfigMaxClientCount =
                        mCurrentSoftApConfiguration.getMaxNumberOfClients();
                int finalMaxClientCount = maxAllowedClientsByHardwareAndCarrier;
                if (userApConfigMaxClientCount > 0) {
                    finalMaxClientCount = Math.min(userApConfigMaxClientCount,
                            maxAllowedClientsByHardwareAndCarrier);
                }
                List<WifiClient> currentClients = getConnectedClientList();
                int targetDisconnectClientNumber = currentClients.size() - finalMaxClientCount;
                List<WifiClient> allowedConnectedList = new ArrayList<>();
                Iterator<WifiClient> iterator = currentClients.iterator();
                while (iterator.hasNext()) {
                    WifiClient client = iterator.next();
                    if (mBlockedClientList.contains(client.getMacAddress())
                              && !mAllowedClientList.contains(client.getMacAddress())) {
                        Log.d(getTag(), "Force disconnect for not allowed client: " + client);
                        if (!mWifiNative.forceClientDisconnect(
                                mApInterfaceName, client.getMacAddress(),
                                QtiWifiExtendManager.SAP_CLIENT_BLOCK_REASON_CODE_BLOCKED_BY_USER)) {
                            addClientToPendingDisconnectionList(client,
                                    QtiWifiExtendManager.SAP_CLIENT_BLOCK_REASON_CODE_BLOCKED_BY_USER);
                        }
                        targetDisconnectClientNumber--;
                    } else {
                        allowedConnectedList.add(client);
                    }
                }

                if (targetDisconnectClientNumber > 0) {
                    Iterator<WifiClient> allowedClientIterator = allowedConnectedList.iterator();
                    while (allowedClientIterator.hasNext()) {
                        if (targetDisconnectClientNumber == 0) break;
                        WifiClient allowedClient = allowedClientIterator.next();
                        Log.d(getTag(), "Force disconnect for client due to no more room: "
                                + allowedClient);
                        if (!mWifiNative.forceClientDisconnect(
                                mApInterfaceName, allowedClient.getMacAddress(),
                                QtiWifiExtendManager.SAP_CLIENT_BLOCK_REASON_CODE_NO_MORE_STAS)) {
                            addClientToPendingDisconnectionList(allowedClient,
                                    QtiWifiExtendManager.SAP_CLIENT_BLOCK_REASON_CODE_NO_MORE_STAS);
                        }
                        targetDisconnectClientNumber--;
                    }
                }
            }

            /**
             * Set stations associated with this soft AP
             * @param client The station for which connection state changed.
             * @param isConnected True for the connection changed to connect, otherwise false.
             */
            private void updateConnectedClients(WifiClient client, boolean isConnected) {
                if (client == null) {
                    return;
                }

                if (null != mPendingDisconnectClients.remove(client)) {
                    Log.d(getTag(), "Remove client: " + client.getMacAddress()
                            + "from pending disconnectionlist");
                }

                String apInstanceIdentifier = client.getApInstanceIdentifier();
                List clientList = mConnectedClientWithApInfoMap.computeIfAbsent(
                        apInstanceIdentifier, k -> new ArrayList<>());
                int index = clientList.indexOf(client);

                if ((index != -1) == isConnected) {
                    Log.e(getTag(), "Drop client connection event, client "
                            + client + "isConnected: " + isConnected
                            + " , duplicate event or client is blocked");
                    return;
                }
                if (isConnected) {
                    boolean isAllow = checkSoftApClient(mCurrentSoftApConfiguration, client);
                    if (isAllow) {
                        clientList.add(client);
                    } else {
                        return;
                    }
                } else {
                    if (null == clientList.remove(index)) {
                        Log.e(getTag(), "client doesn't exist in list, it should NOT happen");
                    }
                }

                // Update clients list.
                mConnectedClientWithApInfoMap.put(apInstanceIdentifier, clientList);
                SoftApInfo currentInfoWithClientsChanged = mCurrentSoftApInfoMap
                        .get(apInstanceIdentifier);
                Log.d(getTag(), "The connected wifi stations have changed with count: "
                        + clientList.size() + ": " + clientList + " on the AP which info is "
                        + currentInfoWithClientsChanged);

                sendBroadcastApConnectionsChanged();
                if (mSoftApCallback != null) {
                    //Not contain disconnectReason Info
                    //Here set the disconnectReason always to 0
                    mSoftApCallback.onConnectedClientsChanged(client,
                    isConnected, 0);
                } else {
                    Log.e(getTag(),
                            "SoftApCallback is null. Dropping ConnectedClientsChanged event.");
                }
            }

            /**
             * @param apInfo, the new SoftApInfo changed. Null used to clean up.
             */
            private void updateSoftApInfo(SoftApInfo apInfo, boolean isRemoved) {
                Log.d(getTag(), "SoftApInfo update " + apInfo + ", isRemoved: " + isRemoved);
                if (apInfo == null) {
                    // Clean up
                    mCurrentSoftApInfoMap.clear();
                    mConnectedClientWithApInfoMap.clear();
                    sendBroadcastApConnectionsChanged();
                    mSoftApCallback.onInfoChanged(new ArrayList<>(mCurrentSoftApInfoMap.values()));
                    return;
                }
                String changedInstance = apInfo.getApInstanceIdentifier();
                if (apInfo.equals(mCurrentSoftApInfoMap.get(changedInstance))) {
                    if (isRemoved) {
                        boolean isClientConnected =
                                mConnectedClientWithApInfoMap.get(changedInstance).size() > 0;
                        mCurrentSoftApInfoMap.remove(changedInstance);
                        mConnectedClientWithApInfoMap.remove(changedInstance);
                        sendBroadcastApConnectionsChanged();
                        mSoftApCallback.onInfoChanged(new ArrayList<>(mCurrentSoftApInfoMap.values()));
                    }
                    return;
                }

                // Make sure an empty client list is created when info updated
                List clientList = mConnectedClientWithApInfoMap.computeIfAbsent(
                        changedInstance, k -> new ArrayList<>());

                if (clientList.size() != 0) {
                    Log.e(getTag(), "The info: " + apInfo
                            + " changed when client connected, it should NOT happen!!");
                }

                mCurrentSoftApInfoMap.put(changedInstance, new SoftApInfo(apInfo));
                sendBroadcastApConnectionsChanged();
                mSoftApCallback.onInfoChanged(new ArrayList<>(mCurrentSoftApInfoMap.values()));
            }

            @Override
            public void enterImpl() {

                Handler handler = mStateMachine.getHandler();

                //mSarManager.setSapWifiState(QtiWifiExtendManager.WIFI_AP_STATE_ENABLED);
                Log.d(getTag(), "Resetting connected clients on start");
                mConnectedClientWithApInfoMap.clear();
                mPendingDisconnectClients.clear();
            }

            @Override
            public void exitImpl() {
                stopSoftAp();
                if (getConnectedClientList().size() != 0) {
                    Log.d(getTag(), "Resetting num stations on stop");
                    mConnectedClientWithApInfoMap.clear();
                    sendBroadcastApConnectionsChanged();
                    if (mSoftApCallback != null) {
                        //only exist one bridged AP. If exit from started state
                        //the SoftApInfoList will be null
                        mSoftApCallback.onInfoChanged(new ArrayList<>(mCurrentSoftApInfoMap.values()));
                    }
                }
                mPendingDisconnectClients.clear();

                updateApState(QtiWifiExtendManager.WIFI_AP_STATE_DISABLED,
                        QtiWifiExtendManager.WIFI_AP_STATE_DISABLING, 0);

                mApInterfaceName = null;
                updateSoftApInfo(null, false);
            }

            @Override
            String getMessageLogRec(int what) {
                return SoftApManager.class.getSimpleName() + "." + RunnerState.class.getSimpleName()
                        + "." + getWhatToString(what);
            }

            @Override
            public boolean processMessageImpl(Message message) {
                switch (message.what) {
                    case CMD_ASSOCIATED_STATIONS_CHANGED:
                        if (!(message.obj instanceof WifiClient)) {
                            Log.e(getTag(), "Invalid type returned for"
                                    + " CMD_ASSOCIATED_STATIONS_CHANGED");
                            break;
                        }
                        boolean isConnected = (message.arg1 == 1);
                        WifiClient client = (WifiClient) message.obj;
                        Log.d(getTag(), "CMD_ASSOCIATED_STATIONS_CHANGED, Client: "
                                + client.getMacAddress().toString() + " isConnected: "
                                + isConnected);
                        updateConnectedClients(client, isConnected);
                        break;
                    case CMD_AP_INFO_CHANGED:
                        if (!(message.obj instanceof SoftApInfo)) {
                            Log.e(getTag(), "Invalid type returned for"
                                    + " CMD_AP_INFO_CHANGED");
                            break;
                        }
                        SoftApInfo apInfo = (SoftApInfo) message.obj;
                        if (apInfo.getFrequency() < 0) {
                            Log.e(getTag(), "Invalid ap channel frequency: "
                                    + apInfo.getFrequency());
                            break;
                        }
                        updateSoftApInfo(apInfo, false);
                        break;
                    case CMD_STOP:
                        updateApState(QtiWifiExtendManager.WIFI_AP_STATE_DISABLING,
                                QtiWifiExtendManager.WIFI_AP_STATE_ENABLED, 0);
                        quitNow();
                        break;
                    case CMD_START:
                        // Already started, ignore this command.
                        break;
                    case CMD_FAILURE:
                        String instance = (String) message.obj;
                            List<String> instances =
                                    mWifiNative.getBridgedApInstances(mApInterfaceName);
                            if (instance != null) {
                                Log.i(getTag(), "receive instanceFailure on " + instance);
                                removeIfaceInstanceFromBridgedApIface(instance);
                                // there is an available instance, keep AP on.
                                if (mCurrentSoftApInfoMap.size() == 1) {
                                    break;
                                }
                            } else if (mCurrentSoftApInfoMap.size() == 1 && instances != null
                                    && instances.size() == 1) {
                                if (!mCurrentSoftApInfoMap.containsKey(instances.get(0))) {
                                    // there is an available instance but the info doesn't be
                                    // updated, keep AP on and remove unavailable instance info.
                                    for (String unavailableInstance
                                            : mCurrentSoftApInfoMap.keySet()) {
                                        removeIfaceInstanceFromBridgedApIface(unavailableInstance);
                                    }
                                    break;
                                }
                            }
                        Log.w(getTag(), "hostapd failure, stop and report failure");
                        /* fall through */
                    //case CMD_UPDATE_CAPABILITY:
                        //SoftApCapability capability = (SoftApCapability) message.obj;
                        //mCurrentSoftApCapability = new SoftApCapability(capability);
                        //updateClientConnection();
                        //updateSafeChannelFrequencyList();
                    //    break;
                    case CMD_UPDATE_CONFIG:
                        SoftApConfiguration newConfig = (SoftApConfiguration) message.obj;
                        SoftApConfiguration originalConfig =
                                mOriginalModeConfiguration.getSoftApConfiguration();
                        if (!ApConfigUtil.checkConfigurationChangeNeedToRestart(
                                originalConfig, newConfig)) {
                            Log.d(getTag(), "Configuration changed to " + newConfig);
                            if (mCurrentSoftApConfiguration.getMaxNumberOfClients()
                                    != newConfig.getMaxNumberOfClients()) {
                                Log.d(getTag(), "Max Client changed, reset to record the metrics");
                                //mEverReportMetricsForMaxClient = false;
                            }
                            updateChangeableConfiguration(newConfig);
                            updateClientConnection();
                        } else {
                            Log.d(getTag(), "Ignore the config: " + newConfig
                                    + " update since it requires restart");
                        }
                        break;
                    case CMD_UPDATE_COUNTRY_CODE:
                        String countryCode = (String) message.obj;
                        if (!TextUtils.isEmpty(countryCode)
                                && !TextUtils.equals(mCountryCode, countryCode)
                                && mWifiNative.setApCountryCode(
                                mApInterfaceName, countryCode.toUpperCase(Locale.ROOT))) {
                            Log.i(getTag(), "Update country code when Soft AP enabled from "
                                    + mCountryCode + " to " + countryCode);
                            mCountryCode = countryCode;
                        }
                        break;
                    case CMD_FORCE_DISCONNECT_PENDING_CLIENTS:
                        if (mPendingDisconnectClients.size() != 0) {
                            Log.d(getTag(), "Disconnect pending list is NOT empty");
                            mPendingDisconnectClients.forEach((pendingClient, reason)->
                                    mWifiNative.forceClientDisconnect(mApInterfaceName,
                                    pendingClient.getMacAddress(), reason));
                            sendMessageDelayed(
                                    SoftApStateMachine.CMD_FORCE_DISCONNECT_PENDING_CLIENTS,
                                    SOFT_AP_PENDING_DISCONNECTION_CHECK_DELAY_MS);
                        }
                        break;
                    default:
                        return NOT_HANDLED;
                }
                return HANDLED;
            }
        }
    }

}
