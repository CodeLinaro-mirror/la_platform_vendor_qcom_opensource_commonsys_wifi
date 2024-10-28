/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.annotation.IntDef;
import android.content.Context;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * This class is used to recover the wifi stack from a fatal failure. The recovery mechanism
 * involves triggering a stack restart (essentially simulating an airplane mode toggle) using
 * {@link ActiveModeWarden}.
 * The current triggers for:
 * 1. Last resort watchdog bite.
 * 2. HAL/wificond crashes during normal operation.
 * 3. TBD: supplicant crashes during normal operation.
 */
public class SelfRecovery {
    private static final String TAG = "ExtendWifiSelfRecovery";

    /**
     * Reason codes for the various recovery triggers.
     */
    public static final int REASON_LAST_RESORT_WATCHDOG = 0;
    public static final int REASON_WIFINATIVE_FAILURE = 1;
    public static final int REASON_STA_IFACE_DOWN = 2;
    public static final int REASON_API_CALL = 3;
    public static final int REASON_SUBSYSTEM_RESTART = 4;
    public static final int REASON_IFACE_ADDED = 5;

    /**
     * State for self recovery.
     */
    private static final int STATE_NO_RECOVERY = 0;
    private static final int STATE_DISABLE_WIFI = 1;
    private static final int STATE_RESTART_WIFI = 2;

    private final Context mContext;
    private final ActiveModeWarden mActiveModeWarden;
    // Time since boot (in millis) that restart occurred
    private final WifiNative mWifiNative;
    private int mSelfRecoveryReason;
    // Self recovery state
    private int mRecoveryState;

    /**
     * Return the recovery reason code as string.
     * @param reason the reason code
     * @return the recovery reason as string
     */
    public static String getRecoveryReasonAsString(int reason) {
        switch (reason) {
            case REASON_LAST_RESORT_WATCHDOG:
                return "Last Resort Watchdog";
            case REASON_WIFINATIVE_FAILURE:
                return "WifiNative Failure";
            case REASON_STA_IFACE_DOWN:
                return "Sta Interface Down";
            case REASON_API_CALL:
                return "API call (e.g. user)";
            case REASON_SUBSYSTEM_RESTART:
                return "Subsystem Restart";
            case REASON_IFACE_ADDED:
                return "Interface Added";
            default:
                return "Unknown " + reason;
        }
    }

    /**
     * Invoked when self recovery completed.
     */
    public void onRecoveryCompleted() {
        mRecoveryState = STATE_NO_RECOVERY;
    }

    /**
     * Invoked when Wifi is stopped with all client mode managers removed.
     */
    public void onWifiStopped() {
        if (mRecoveryState == STATE_DISABLE_WIFI) {
            onRecoveryCompleted();
        }
    }

    /**
     * Returns true if recovery is currently in progress.
     */
    public boolean isRecoveryInProgress() {
        // return true if in recovery progress
        return mRecoveryState != STATE_NO_RECOVERY;
    }

    public SelfRecovery(Context context, ActiveModeWarden activeModeWarden,
            WifiNative wifiNative) {
        mContext = context;
        mActiveModeWarden = activeModeWarden;
        mWifiNative = wifiNative;
        mRecoveryState = STATE_NO_RECOVERY;
    }

    /**
     * Trigger recovery.
     *
     * This method does the following:
     * 1. Checks reason code used to trigger recovery
     * 2. Checks for sta iface down triggers and disables wifi by sending {@link
     * ActiveModeWarden#recoveryDisableWifi()} to {@link ActiveModeWarden} to disable wifi.
     * 3. Throttles restart calls for underlying native failures
     * 4. Sends {@link ActiveModeWarden#recoveryRestartWifi(int)} to {@link ActiveModeWarden} to
     * initiate the stack restart.
     * @param reason One of the above |REASON_*| codes.
     */
    public void trigger(int reason) {
        if (!(reason == REASON_LAST_RESORT_WATCHDOG || reason == REASON_WIFINATIVE_FAILURE
             || reason == REASON_API_CALL || reason == REASON_IFACE_ADDED)) {
            Log.e(TAG, "Invalid trigger reason. Ignoring...");
            return;
        }

        Log.e(TAG, "Triggering recovery for reason: " + getRecoveryReasonAsString(reason));
        if (reason == REASON_WIFINATIVE_FAILURE) {
           Log.e(TAG, "Recovery disabled. Disabling wifi");
           mActiveModeWarden.recoveryDisableWifi();
           mRecoveryState = STATE_DISABLE_WIFI;
           return;
        }
        mSelfRecoveryReason = reason;
        mRecoveryState = STATE_RESTART_WIFI;
    }

}
