/*
 * Copyright (C) 2022 The Android Open Source Project
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
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.function.Supplier;

import com.qualcomm.qti.server.wifiextend.WifiHalAidlImpl;
import com.qualcomm.qti.server.wifiextend.WifiChip;

//import com.qualcomm.qti.server.wifiextend.WifiHal.DeathRecipient;
//import com.qualcomm.qti.server.wifiextend.WifiHal.IWifiHal;
import com.qualcomm.qti.server.wifiextend.WifiChip;

public class WifiHal {
    private static final String TAG = "ExtendWifiHal";
    private IWifiHal mWifiHal;

    /**
     * Wifi operation status codes.
     */
    public static final int WIFI_STATUS_SUCCESS = 0;
    public static final int WIFI_STATUS_ERROR_WIFI_CHIP_INVALID = 1;
    public static final int WIFI_STATUS_ERROR_WIFI_IFACE_INVALID = 2;
    public static final int WIFI_STATUS_ERROR_WIFI_RTT_CONTROLLER_INVALID = 3;
    public static final int WIFI_STATUS_ERROR_NOT_SUPPORTED = 4;
    public static final int WIFI_STATUS_ERROR_NOT_AVAILABLE = 5;
    public static final int WIFI_STATUS_ERROR_NOT_STARTED = 6;
    public static final int WIFI_STATUS_ERROR_INVALID_ARGS = 7;
    public static final int WIFI_STATUS_ERROR_BUSY = 8;
    public static final int WIFI_STATUS_ERROR_UNKNOWN = 9;
    public static final int WIFI_STATUS_ERROR_REMOTE_EXCEPTION = 10;

    public interface IWifiHal {
        /**
         * Get the chip corresponding to the provided chipId.
         *
         * @param chipId ID of the chip.
         * @return {@link WifiChip} if successful, null otherwise.
         */
        WifiChip getChip(int chipId);
        /**
         * Retrieves the list of all chip id's on the device.
         * The corresponding |WifiChip| object for any chip can be
         * retrieved using the |getChip| method.
         *
         * @return List of all chip id's on the device, or null if an error occurred.
         */
        List<Integer> getChipIds();
        /**
         * Register for HAL event callbacks.
         *
         * @param callback Instance of {@link WifiHal.Callback}
         * @return true if successful, false otherwise.
         */
        boolean registerEventCallback(WifiHal.Callback callback);
        /**
         * Initialize the Wi-Fi HAL service. Must initialize before calling {@link #start()}
         *
         * @param deathRecipient Instance of {@link WifiHal.DeathRecipient}
         */
        void initialize(WifiHal.DeathRecipient deathRecipient);
        /**
         * Check if the initialization is complete and the HAL is ready to accept commands.
         *
         * @return true if initialization is complete, false otherwise.
         */
        boolean isInitializationComplete();
        /**
         * Check if the Wi-Fi HAL supported on this device.
         *
         * @return true if supported, false otherwise.
         */
        boolean isSupported();
        /**
         * Start the Wi-Fi HAL.
         *
         * @return {@link WifiHal.WifiStatusCode} indicating the result.
         */
        int start();
        /**
         * Get the current state of the HAL.
         *
         * @return true if started, false otherwise.
         */
        boolean isStarted();
        /**
         * Stop the Wi-Fi HAL.
         *
         * Note: Calling stop() and then start() is a valid way of resetting state in
         * the HAL, driver, and firmware.
         *
         * @return true if successful, false otherwise.
         */
        boolean stop();
        /**
         * Invalidate the Wi-Fi HAL. Call when a significant error occurred external to the root
         * Wi-Fi HAL, for instance in a Wi-Fi chip retrieved from this HAL.
         */
        void invalidate();
    }

    /**
     * Interface that can be created by the Wi-Fi HAL.
     */
    public interface WifiInterface {
        /**
         * Get the name of this interface.
         */
        String getName();
    }

    /**
     * Framework callback object. Will get called when the equivalent events are received
     * from the HAL.
     */
    public interface Callback {
        /**
         * Called when the Wi-Fi system failed in a way that caused it be disabled.
         * Calling start again must restart Wi-Fi as if stop then start was called
         * (full state reset). When this event is received, all WifiChip & WifiIface
         * objects retrieved after the last call to start will be considered invalid.
         *
         * @param status Failure reason code.
         */
        void onFailure(int status);

        /**
         * Called in response to a call to start, indicating that the operation
         * completed. After this callback the HAL must be fully operational.
         */
        void onStart();

        /**
         * Called in response to a call to stop, indicating that the operation
         * completed. When this event is received, all WifiChip objects retrieved
         * after the last call to start will be considered invalid.
         */
        void onStop();

        /**
         * Must be called when the Wi-Fi subsystem restart completes.
         * Once this event is received, the framework must fully reset the Wi-Fi stack state.
         *
         * @param status Status code.
         */
        void onSubsystemRestart(int status);
    }

    /**
     * Framework death recipient object. Called if the death recipient registered with the HAL
     * indicates that the service died.
     */
    public interface DeathRecipient {
        /**
         * Called on service death.
         */
        void onDeath();
    }

    public WifiHal(Context context) {
        mWifiHal = createWifiHalMockable(context);
    }

    protected IWifiHal createWifiHalMockable(Context context) {
        if (WifiHalAidlImpl.serviceDeclared()) {
            return new WifiHalAidlImpl(context);
        } else {
            Log.e(TAG, "No HIDL or AIDL service available for the Wifi Vendor HAL.");
            return null;
        }
    }

    private <T> T validateAndCall(String methodStr, T defaultVal, @NonNull Supplier<T> supplier) {
        if (mWifiHal == null) {
            Log.wtf(TAG, "Cannot call " + methodStr + " because mWifiHal is null");
            return defaultVal;
        }
        return supplier.get();
    }

    /**
     * See comments for {@link IWifiHal#getChip(int)}
     */
    public WifiChip getChip(int chipId) {
        return validateAndCall("getChip", null,
                () -> mWifiHal.getChip(chipId));
    }

    /**
     * See comments for {@link IWifiHal#getChipIds()}
     */
    public List<Integer> getChipIds() {
        return validateAndCall("getChipIds", null,
                () -> mWifiHal.getChipIds());
    }

    /**
     * See comments for {@link IWifiHal#registerEventCallback(Callback)}
     */
    public boolean registerEventCallback(Callback callback) {
        return validateAndCall("registerEventCallback", false,
                () -> mWifiHal.registerEventCallback(callback));
    }

    /**
     * See comments for {@link IWifiHal#initialize(DeathRecipient)}
     */
    public void initialize(WifiHal.DeathRecipient deathRecipient) {
        if (mWifiHal != null) {
            mWifiHal.initialize(deathRecipient);
        }
    }

    /**
     * See comments for {@link IWifiHal#isInitializationComplete()}
     */
    public boolean isInitializationComplete() {
        return validateAndCall("isInitializationComplete", false,
                () -> mWifiHal.isInitializationComplete());
    }

    /**
     * See comments for {@link IWifiHal#isSupported()}
     */
    public boolean isSupported() {
        return validateAndCall("isSupported", false,
                () -> mWifiHal.isSupported());
    }

    /**
     * See comments for {@link IWifiHal#start()}
     * return @WifiStatusCode
     */
    public int start() {
        return validateAndCall("start", WIFI_STATUS_ERROR_UNKNOWN,
                () -> mWifiHal.start());
    }

    /**
     * See comments for {@link IWifiHal#isStarted()}
     */
    public boolean isStarted() {
        return validateAndCall("isStarted", false,
                () -> mWifiHal.isStarted());
    }

    /**
     * See comments for {@link IWifiHal#stop()}
     */
    public boolean stop() {
        return validateAndCall("stop", false,
                () -> mWifiHal.stop());
    }

    /**
     * See comments for {@link IWifiHal#invalidate()}
     */
    public void invalidate() {
        if (mWifiHal != null) {
            mWifiHal.invalidate();
        }
    }
}
