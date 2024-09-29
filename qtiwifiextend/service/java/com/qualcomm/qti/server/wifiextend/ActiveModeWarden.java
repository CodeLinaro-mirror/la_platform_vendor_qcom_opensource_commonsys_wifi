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

import android.annotation.NonNull;
import android.annotation.Nullable;

import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.WorkSource;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.LocalLog;
import android.util.Log;
import android.util.Pair;

import com.qualcomm.qti.server.wifiextend.statemachine.StateMachine;
import com.qualcomm.qti.wifiextend.SoftApConfiguration;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class provides the implementation for SoftAp operating modes.
 */
public class ActiveModeWarden {
    private static final String TAG = "ExtendWifiActiveModeWarden";

    private final Set<SoftApManager> mSoftApManagers = new ArraySet<>();

    private final Looper mLooper;
    private final Handler mHandler;

    private WifiNative mWifiNative;
    private final WifiController mWifiController;

    private boolean mIsShuttingdown = false;

    private QtiWifiExtendServiceImpl.SoftApListener mSoftApListener;

    private QtiWifiExtendInjector mWifiInjector;

    //private LocalLog mLocalLog;

    ActiveModeWarden(QtiWifiExtendInjector injector, Looper looper, WifiNative wifiNative) {
        mWifiInjector = injector;
        mLooper = looper;
        mHandler = new Handler(looper);
        mWifiNative = wifiNative;
        mWifiController = new WifiController();

        mWifiNative.registerStatusListener(isReady -> {
			Log.i(TAG, "enter listener callback");
            if (!isReady && !mIsShuttingdown) {
                mHandler.post(() -> {
                    Log.e(TAG, "One of the native daemons (ExtendWifiHal/ExtendHostapd/ExtendQtiWifi) died. Triggering recovery");

                    // immediately trigger SelfRecovery if we receive a notice about an
                    // underlying daemon failure
                    // Note: SelfRecovery has a circular dependency with ActiveModeWarden and is
                    // instantiated after ActiveModeWarden, so use WifiInjector to get the instance
                    // instead of directly passing in SelfRecovery in the constructor.
                    mWifiInjector.getSelfRecovery().trigger(SelfRecovery.REASON_WIFINATIVE_FAILURE);
                });
            }
        });
    }

    Collection<ActiveModeManager> getActiveModeManagers() {
        ArrayList<ActiveModeManager> activeModeManagers = new ArrayList<>();
        activeModeManagers.addAll(mSoftApManagers);
        return activeModeManagers;
    }

    public void registerSoftApListener(@NonNull QtiWifiExtendServiceImpl.SoftApListener  listener) {
        mSoftApListener = listener;
    }

    private class SoftApManagerListener implements ActiveModeManager.Listener<SoftApManager> {
        @Override
        public void onStarted(SoftApManager softApManager) {
            Log.i(TAG, "SoftApManager start successfully" + softApManager);
        }

        @Override
        public void onStopped(SoftApManager softApManager) {
            mSoftApManagers.remove(softApManager);
            mWifiController.sendMessage(WifiController.CMD_AP_STOPPED);
        }

        @Override
        public void onStartFailure(SoftApManager softApManager) {
            mSoftApManagers.remove(softApManager);
            mWifiController.sendMessage(WifiController.CMD_AP_START_FAILURE);
            Log.e(TAG, "SoftApManager start failed!" + softApManager);
        }
    }

    //private void updateCapabilityToSoftApModeManager(SoftApCapability capability) {
    //    for (SoftApManager softApManager : mSoftApManagers) {
    //        softApManager.updateCapability(capability);
    //    }
    //}

    private void updateConfigurationToSoftApModeManager(SoftApConfiguration config) {
        for (SoftApManager softApManager : mSoftApManagers) {
            softApManager.updateConfiguration(config);
        }
    }

    private int getActiveModeManagerCount() {
        return mSoftApManagers.size();
    }

    private void shutdownWifi() {
        Log.d(TAG, "Shutting down all mode managers");
        for (ActiveModeManager manager : getActiveModeManagers()) {
            manager.stop();
        }
    }

    private boolean hasAnySoftApManager() {
        return !mSoftApManagers.isEmpty();
    }

    private void startSoftApModeManager(
        SoftApModeConfiguration softApModeConfig, WorkSource requestorWs) {
        Log.d(TAG, "Starting SoftApModeManager config = " + softApModeConfig.getSoftApConfiguration());
        SoftApManager manager = mWifiInjector.makeSoftApManager(
                new SoftApManagerListener(), mSoftApListener, softApModeConfig, requestorWs);
        mSoftApManagers.add(manager);
    }

    private void stopSoftApModeManagers() {
        for (SoftApManager softApManager : mSoftApManagers) {
            softApManager.stop();
        }
    }

    public void start() {
        mWifiController.start();
    }

    /** Starts SoftAp. */
    public void startSoftAp(SoftApModeConfiguration softApConfig, WorkSource requestorWs) {
        mWifiController.sendMessage(WifiController.CMD_SET_AP, 1, 0,
                Pair.create(softApConfig, requestorWs));
    }

    /** Stop SoftAp. */
    public void stopSoftAp() {
        mWifiController.sendMessage(WifiController.CMD_SET_AP, 0, 0);
    }

    /** Update SoftAp Capability. */
    //public void updateSoftApCapability(SoftApCapability capability, int ipMode) {
    //    mWifiController.sendMessage(WifiController.CMD_UPDATE_AP_CAPABILITY, ipMode, 0, capability);
    //}

    /** Update SoftAp Configuration. */
    public void updateSoftApConfiguration(SoftApConfiguration config) {
        mWifiController.sendMessage(WifiController.CMD_UPDATE_AP_CONFIG, config);
    }

    /** Disable Wifi for recovery purposes. */
    public void recoveryDisableWifi() {
        mWifiController.sendMessage(WifiController.CMD_RECOVERY_DISABLE_WIFI);
    }

    private class WifiController extends StateMachine {

        private static final String TAG = "ExtendWifiController";

        // Maximum limit to use for timeout delay if the value from overlay setting is too large.
        private static final int MAX_RECOVERY_TIMEOUT_DELAY_MS = 4000;

        private static final int BASE = 0x00080000;

        static final int CMD_SET_AP                                 = BASE + 10;

        static final int CMD_AP_STOPPED                             = BASE + 15;
        // Command used to trigger a wifi stack restart when in active mode
        static final int CMD_RECOVERY_RESTART_WIFI                  = BASE + 17;
        // Internal command used to complete wifi stack restart
        static final int CMD_RECOVERY_RESTART_WIFI_CONTINUE          = BASE + 18;
        // Command to disable wifi when SelfRecovery is throttled or otherwise not doing full
        // recovery
        static final int CMD_RECOVERY_DISABLE_WIFI                   = BASE + 19;
        static final int CMD_DEFERRED_RECOVERY_RESTART_WIFI          = BASE + 22;
        static final int CMD_AP_START_FAILURE                        = BASE + 23;
        static final int CMD_UPDATE_AP_CAPABILITY                    = BASE + 24;
        static final int CMD_UPDATE_AP_CONFIG                        = BASE + 25;
        static final int CMD_RECOVERY_RESTART_WIFI_HAL               = BASE + 26;

        private final EnabledState mEnabledState;
        private final DisabledState mDisabledState;

        WifiController() {
            super(TAG, mLooper);
            final int threshold = 100;
            DefaultState defaultState = new DefaultState(threshold);
            mEnabledState = new EnabledState(threshold);
            mDisabledState = new DisabledState(threshold);
            addState(defaultState); {
                addState(mDisabledState, defaultState);
                addState(mEnabledState, defaultState);
            }
            setLogRecSize(100);
            setLogOnlyTransitions(false);
        }

        /**
         * Return the additional string to be logged by LogRec.
         *
         * @param msg that was processed
         * @return information to be logged as a String
         */
        @Override
        protected String getLogRecString(Message msg) {
            StringBuilder sb = new StringBuilder();
            sb.append(msg.arg1)
                    .append(" ").append(msg.arg2)
                    .append(" num SoftApManagers:").append(mSoftApManagers.size());
            if (msg.obj != null) {
                sb.append(" ").append(msg.obj);
            }
            return sb.toString();
        }

        @Override
        protected String getWhatToString(int what) {
            switch (what) {
                case CMD_AP_START_FAILURE:
                    return "CMD_AP_START_FAILURE";
                case CMD_AP_STOPPED:
                    return "CMD_AP_STOPPED";
                case CMD_DEFERRED_RECOVERY_RESTART_WIFI:
                    return "CMD_DEFERRED_RECOVERY_RESTART_WIFI";
                case CMD_RECOVERY_DISABLE_WIFI:
                    return "CMD_RECOVERY_DISABLE_WIFI";
                case CMD_RECOVERY_RESTART_WIFI:
                    return "CMD_RECOVERY_RESTART_WIFI";
                case CMD_RECOVERY_RESTART_WIFI_CONTINUE:
                    return "CMD_RECOVERY_RESTART_WIFI_CONTINUE";
                case CMD_RECOVERY_RESTART_WIFI_HAL:
                    return "CMD_RECOVERY_RESTART_WIFI_HAL";
                case CMD_SET_AP:
                    return "CMD_SET_AP";
                case CMD_UPDATE_AP_CAPABILITY:
                    return "CMD_UPDATE_AP_CAPABILITY";
                case CMD_UPDATE_AP_CONFIG:
                    return "CMD_UPDATE_AP_CONFIG";
                case RunnerState.STATE_ENTER_CMD:
                    return "Enter";
                case RunnerState.STATE_EXIT_CMD:
                    return "Exit";
                default:
                    return "what:" + what;
            }
        }

        @Override
        public void start() {
            // Initialize these values at bootup to defaults, will be overridden by API calls
            // for further toggles.

            setInitialState(mDisabledState);

            // Initialize the lower layers before we start.
            mWifiNative.initialize();
            super.start();
        }

        private int readWifiRecoveryDelay() {
            int recoveryDelayMillis = 2000;
            if (recoveryDelayMillis > MAX_RECOVERY_TIMEOUT_DELAY_MS) {
                recoveryDelayMillis = MAX_RECOVERY_TIMEOUT_DELAY_MS;
                Log.w(TAG, "Overriding timeout delay with maximum limit value");
            }
            return recoveryDelayMillis;
        }

        abstract class BaseState extends RunnerState {
            BaseState(int threshold, @NonNull LocalLog localLog) {
                super(threshold, mWifiInjector.getExtendWifiHandlerLocalLog());
            }

            @Override
            public void enterImpl() {
            }

            @Override
            public void exitImpl() {
            }

            @Override
            String getMessageLogRec(int what) {
                return ActiveModeWarden.class.getSimpleName() + "."
                        + DefaultState.class.getSimpleName() + "." + getWhatToString(what);
            }

            @Override
            public final boolean processMessageImpl(Message msg) {
                // not in emergency mode, process messages normally
                return processMessageFiltered(msg);
            }

            protected abstract boolean processMessageFiltered(Message msg);
       }

        class DefaultState extends RunnerState {
            DefaultState(int threshold) {
                super(threshold, mWifiInjector.getExtendWifiHandlerLocalLog());
            }

            @Override
            String getMessageLogRec(int what) {
                return ActiveModeWarden.class.getSimpleName() + "."
                        + DefaultState.class.getSimpleName() + "." + getWhatToString(what);
            }

            @Override
            void enterImpl() {
            }

            @Override
            void exitImpl() {
            }

            @Override
            public boolean processMessageImpl(Message msg) {
                switch (msg.what) {
                    case CMD_AP_STOPPED:
                    case CMD_AP_START_FAILURE:
                    case CMD_RECOVERY_RESTART_WIFI:
                    case CMD_RECOVERY_RESTART_WIFI_CONTINUE:
                    case CMD_DEFERRED_RECOVERY_RESTART_WIFI:
                        break;
                    case CMD_RECOVERY_DISABLE_WIFI:
                        log("Recovery has been throttled, disable wifi");
                        shutdownWifi();
                        // onStopped will move the state machine to "DisabledState".
                        sendMessageDelayed(CMD_RECOVERY_RESTART_WIFI_HAL,
                                msg.obj, readWifiRecoveryDelay());
                        break;
                    case CMD_UPDATE_AP_CAPABILITY:
                        //updateCapabilityToSoftApModeManager((SoftApCapability) msg.obj);
                        break;
                    case CMD_UPDATE_AP_CONFIG:
                        updateConfigurationToSoftApModeManager((SoftApConfiguration) msg.obj);
                        break;
                    default:
                        throw new RuntimeException("WifiController.handleMessage " + msg.what);
                }
                return HANDLED;
            }
        }

        class DisabledState extends BaseState {
            DisabledState(int threshold) {
                super(threshold, mWifiInjector.getExtendWifiHandlerLocalLog());
            }

            @Override
            public void enterImpl() {
                log("DisabledState.enter()");
                super.enterImpl();
                if (hasAnySoftApManager()) {
                    Log.e(TAG, "Entered DisabledState, but has active mode managers");
                }
            }

            @Override
            public void exitImpl() {
                log("DisabledState.exit()");
                super.exitImpl();
            }

            @Override
            public boolean processMessageFiltered(Message msg) {
                switch (msg.what) {
                    case CMD_SET_AP:
                        // note: CMD_SET_AP is handled/dropped in ECM mode - will not start here
                        if (msg.arg1 == 1) {
                            Pair<SoftApModeConfiguration, WorkSource> softApConfigAndWs =
                                    (Pair) msg.obj;
                            startSoftApModeManager(
                                    softApConfigAndWs.first, softApConfigAndWs.second);
                            transitionTo(mEnabledState);
                        }
                        break;
                    case CMD_RECOVERY_RESTART_WIFI:
                        log("Recovery triggered, already in disabled state");
                        sendMessageDelayed(CMD_RECOVERY_RESTART_WIFI_CONTINUE,
                                Collections.emptyList(), readWifiRecoveryDelay());
                        break;
                    case CMD_DEFERRED_RECOVERY_RESTART_WIFI:
                        // wait mRecoveryDelayMillis for letting driver clean reset.
                        sendMessageDelayed(CMD_RECOVERY_RESTART_WIFI_CONTINUE,
                                msg.obj, readWifiRecoveryDelay());
                        break;
                    case CMD_RECOVERY_RESTART_WIFI_CONTINUE:
                        log("Recovery in progress, start wifi");
                        List<ActiveModeManager> modeManagersBeforeRecovery = (List) msg.obj;
                        // No user controlled mode managers before recovery, so check if wifi
                        // was toggled on.
                        for (ActiveModeManager activeModeManager : modeManagersBeforeRecovery) {
                            if (activeModeManager instanceof SoftApManager) {
                                SoftApManager softApManager = (SoftApManager) activeModeManager;
                                startSoftApModeManager(
                                        softApManager.getSoftApModeConfiguration(),
                                        softApManager.getRequestorWs());
                            }
                        }
                        transitionTo(mEnabledState);
                        break;
                    case CMD_RECOVERY_RESTART_WIFI_HAL:
                        log("Recovery in process, initialize wifi and qtiwifi HAL");
                        mWifiNative.initialize();
                        break;
                    default:
                        return NOT_HANDLED;
                }
                return HANDLED;
            }
        }

        class EnabledState extends BaseState {
            EnabledState(int threshold) {
                super(threshold, mWifiInjector.getExtendWifiHandlerLocalLog());
            }

            @Override
            public void enterImpl() {
                log("EnabledState.enter()");
                super.enterImpl();
                if (!hasAnySoftApManager()) {
                    Log.e(TAG, "Entered EnabledState, but no active mode managers");
                }
            }

            @Override
            public void exitImpl() {
                log("EnabledState.exit()");
                if (hasAnySoftApManager()) {
                    Log.e(TAG, "Exiting EnabledState, but has active mode managers");
                }
                super.exitImpl();
            }

            @Override
            public boolean processMessageFiltered(Message msg) {
                switch (msg.what) {
                    case CMD_SET_AP:
                        // note: CMD_SET_AP is handled/dropped in ECM mode - will not start here
                        if (msg.arg1 == 1) {
                            Pair<SoftApModeConfiguration, WorkSource> softApConfigAndWs =
                                    (Pair) msg.obj;
                            startSoftApModeManager(
                                    softApConfigAndWs.first, softApConfigAndWs.second);
                        } else {
                            stopSoftApModeManagers();
                        }
                        break;
                    case CMD_AP_STOPPED:
                    case CMD_AP_START_FAILURE:
                        if (hasAnySoftApManager()) {
                            log("AP disabled, remain in EnabledState.");
                            break;
                        }
                        if (msg.what == CMD_AP_STOPPED) {
                        //    mWifiInjector.getSelfRecovery().onWifiStopped();
                        //    if (mWifiInjector.getSelfRecovery().isRecoveryInProgress()) {
                                // Recovery in progress, transit to disabled state.
                        //        transitionTo(mDisabledState);
                        //        break;
                        //    }
                        }
                        log("SoftAp mode disabled, return to DisabledState");
                        transitionTo(mDisabledState);
                        break;
                    case  CMD_DEFERRED_RECOVERY_RESTART_WIFI:
                        // Wifi shutdown is not completed yet, still in enabled state.
                        // Defer the message and wait for entering disabled state.
                        deferMessage(msg);
                        break;
                    case CMD_RECOVERY_RESTART_WIFI: {
                        log("Recovery triggered, disable wifi");
                        // Store all instances of tethered SAP mode managers
                        //List<ActiveModeManager> modeManagersBeforeRecovery = mSoftApManagers.stream()
                        //        .filter(m -> ROLE_SOFTAP_TETHERED.equals(m.getRole()))
                        //        .collect(Collectors.toList());
                        //deferMessage(obtainMessage(CMD_DEFERRED_RECOVERY_RESTART_WIFI,
                        //        modeManagersBeforeRecovery));
                        //shutdownWifi();
                        // onStopped will move the state machine to "DisabledState".
                        break;
                    }
                    default:
                        return NOT_HANDLED;
                }
                return HANDLED;
            }
        }
    }
}
