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

import java.util.List;
import java.util.function.Supplier;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.util.Log;

import com.qualcomm.qti.wifiextend.CoexUnsafeChannel;
import com.qualcomm.qti.server.wifiextend.util.GeneralUtil.Mutable;
import com.qualcomm.qti.server.wifiextend.WifiChip.IWifiChip;

public class WifiChip {
    public static final String TAG = "ExtendWifiChip";
    private IWifiChip mWifiChip;

    /**
     * Interface concurrency types used in reporting device concurrency capabilities.
     */
    public static final int IFACE_CONCURRENCY_TYPE_STA = 0;
    public static final int IFACE_CONCURRENCY_TYPE_AP = 1;
    public static final int IFACE_CONCURRENCY_TYPE_AP_BRIDGED = 2;
    public static final int IFACE_CONCURRENCY_TYPE_P2P = 3;
    public static final int IFACE_CONCURRENCY_TYPE_NAN = 4;

    /**
     * Supported interface types.
     */
    public static final int IFACE_TYPE_STA = 0;
    public static final int IFACE_TYPE_AP = 1;
    public static final int IFACE_TYPE_P2P = 2;
    public static final int IFACE_TYPE_NAN = 3;

    /**
     * Antenna configurations.
     */
    public static final int WIFI_ANTENNA_MODE_UNSPECIFIED = 0;
    public static final int WIFI_ANTENNA_MODE_1X1 = 1;
    public static final int WIFI_ANTENNA_MODE_2X2 = 2;
    public static final int WIFI_ANTENNA_MODE_3X3 = 3;
    public static final int WIFI_ANTENNA_MODE_4X4 = 4;

    /**
     * Response containing a value and a status code.
     *
     * @param <T> Type of value that should be returned.
     */
    public static class Response<T> {
        private Mutable<T> mMutable;
        private int mStatusCode;

        public Response(T initialValue) {
            mMutable = new Mutable<>(initialValue);
            mStatusCode = WifiHal.WIFI_STATUS_ERROR_UNKNOWN;
        }

        public void setValue(T value) {
            mMutable.value = value;
        }

        public T getValue() {
            return mMutable.value;
        }

        //WifiHal.WifiStatusCode
        public void setStatusCode(int statusCode) {
            mStatusCode = statusCode;
        }

        //return @WifiHal.WifiStatusCode
        public int getStatusCode() {
            return mStatusCode;
        }
    }

    public class WifiAvailableChannel {
        /**
         * Wifi channel frequency in MHz.
         */
        private int mFrequency;

        /**
         * Bitwise OR of modes (OP_MODE_*) allowed on this channel.
         */
        private int mOpModes;

        /**
         * Wifi Infrastructure client (STA) operational mode.
         */
        public static final int OP_MODE_STA = 1 << 0;
        /**
         * Wifi SoftAp (Mobile Hotspot) operational mode.
         */
        public static final int OP_MODE_SAP = 1 << 1;
        /**
         * Wifi Direct client (CLI) operational mode.
         */
        public static final int OP_MODE_WIFI_DIRECT_CLI = 1 << 2;
        /**
         * Wifi Direct Group Owner (GO) operational mode.
         */
        public static final int OP_MODE_WIFI_DIRECT_GO = 1 << 3;
        /**
         * Wifi Aware (NAN) operational mode.
         */
        public static final int OP_MODE_WIFI_AWARE = 1 << 4;
        /**
         * Wifi Tunneled Direct Link Setup (TDLS) operational mode.
         */
        public static final int OP_MODE_TDLS = 1 << 5;
        /*
         * Filter channel based on regulatory constraints.
         * @hide
         */
        public static final int FILTER_REGULATORY = 0;
        /**
         * Filter channel based on interference from cellular radio.
         * @hide
         */
        public static final int FILTER_CELLULAR_COEXISTENCE = 1 << 0;
        /**
         * Filter channel based on current concurrency state.
         * @hide
         */
        public static final int FILTER_CONCURRENCY = 1 << 1;
        /**
         * Filter channel for the Wi-Fi Aware instant communication mode.
         * @hide
         */
        public static final int FILTER_NAN_INSTANT_MODE = 1 << 2;

        public WifiAvailableChannel(int freq, int opModes) {
            mFrequency = freq;
            mOpModes = opModes;
        }
    }

    /**
     * Framework callback object. Will get called when the equivalent events are received
     * from the HAL.
     */
    public interface Callback {
        /**
         * Indicates that a chip reconfiguration failed. This is a fatal
         * error and any iface objects available previously must be considered
         * invalid. The client can attempt to recover by trying to reconfigure the
         * chip again using {@link IWifiChip#configureChip(int)}.
         *
         * @param status Failure reason code.
         */
        void onChipReconfigureFailure(int status);

        /**
         * Indicates that the chip has been reconfigured successfully. At
         * this point, the interfaces available in the mode must be able to be
         * configured. When this is called, any previous iface objects must be
         * considered invalid.
         *
         * @param modeId The mode that the chip switched to, corresponding to the id
         *        property of the target ChipMode.
         */
        void onChipReconfigured(int modeId);

        /**
         * Indicates that the chip has encountered a fatal error.
         * Client must not attempt to parse either the errorCode or debugData.
         * Must only be captured in a bugreport.
         *
         * @param errorCode Vendor defined error code.
         * @param debugData Vendor defined data used for debugging.
         */
        void onDebugErrorAlert(int errorCode, byte[] debugData);

        /**
         * Reports debug ring buffer data.
         *
         * The ring buffer data collection is event based:
         * - Driver calls this callback when new records are available, the
         *   |WifiDebugRingBufferStatus| passed up to framework in the callback
         *   indicates to framework if more data is available in the ring buffer.
         *   It is not expected that driver will necessarily always empty the ring
         *   immediately as data is available. Instead the driver will report data
         *   every X seconds, or if N bytes are available, based on the parameters
         *   set via |startLoggingToDebugRingBuffer|.
         * - In the case where a bug report has to be captured, the framework will
         *   require driver to upload all data immediately. This is indicated to
         *   driver when framework calls |forceDumpToDebugRingBuffer|. The driver
         *   will start sending all available data in the indicated ring by repeatedly
         *   invoking this callback.
         *
         * @param ignore them
         */
        void onDebugRingBufferDataAvailable();

        /**
         * Indicates that a new iface has been added to the chip.
         *
         * @param type Type of iface added.
         * @param name Name of iface added.
         */
        void onIfaceAdded(int type, String name);

        /**
         * Indicates that an existing iface has been removed from the chip.
         *
         * @param type Type of iface removed.
         * @param name Name of iface removed.
         */
        void onIfaceRemoved(int type, String name);

        /**
         * Indicates a radio mode change.
         * Radio mode change could be a result of:
         * a) Bringing up concurrent interfaces (ex. STA + AP).
         * b) Change in operating band of one of the concurrent interfaces
         * (ex. STA connection moved from 2.4G to 5G)
         *
         * @param ignore them
         */
        void onRadioModeChange();
    }

    public interface IWifiChip {
        /**
        * Configure the chip.
        *
        * @param modeId Mode that the chip must switch to, corresponding to the
        *               id property of the target ChipMode.
        * @return true if successful, false otherwise.
        */
       boolean configureChip(int modeId);

       /**
        * Create an AP interface on the chip.
        *
        * @return {@link WifiApIface} object, or null if a failure occurred.
        */
       WifiApIface createApIface();
       /**
        * Create a bridged AP interface on the chip.
        *
        * @return {@link WifiApIface} object, or null if a failure occurred.
        */
       WifiApIface createBridgedApIface();
       /**
        * Get the AP interface corresponding to the provided ifaceName.
        *
        * @param ifaceName Name of the interface.
        * @return {@link WifiApIface} if the interface exists, null otherwise.
        */
       WifiApIface getApIface(String ifaceName);
        /**
        * List all the AP iface names configured on the chip.
        * The corresponding |WifiApIface| object for any iface
        * can be retrieved using the |getApIface| method.
        *
        * @return List of all AP interface names on the chip, or null if an error occurred.
        */
       List<String> getApIfaceNames();
       /**
        * Get the ID assigned to this chip.
        *
        * @return Chip ID, or -1 if an error occurred.
        */
       int getId();

       /**
         * Retrieve a list of usable Wifi channels for the specified band and operational modes.
         *
         * @param band Band for which the list of usable channels is requested.
         * @param mode Bitmask of modes that the caller is interested in.
         * @param filter Bitmask of filters. Specifies whether driver should filter
         *        channels based on additional criteria. If no filter is specified,
         *        then the driver should return usable channels purely based on
         *        regulatory constraints.
         * @return List of channels represented by {@link WifiAvailableChannel},
         *         or null if an error occurred.
         */
        //List<WifiAvailableChannel> getUsableChannels(int band, int mode, int filter);
        int[] getUsableChannels(int band, int mode, int filter);
        /**
         * Register for chip event callbacks.
         *
         * @param callback Instance of {@link WifiChip.Callback}
         * @return true if successful, false otherwise.
         */
        boolean registerCallback(Callback callback);

        /**
         * Removes the AP interface with the provided ifaceName.
         *
         * @param ifaceName Name of the iface.
         * @return true if successful, false otherwise.
         */
        boolean removeApIface(String ifaceName);

        /**
         * Removes an instance of AP iface with name |ifaceName| from the
         * bridged AP with name |brIfaceName|.
         *
         * Note: Use {@link #removeApIface(String)} with the |brIfaceName| to remove the bridged iface.
         *
         * @param brIfaceName Name of the bridged AP iface.
         * @param ifaceName Name of the AP instance.
         * @return true if successful, false otherwise.
         */
        boolean removeIfaceInstanceFromBridgedApIface(String brIfaceName, String ifaceName);
        /**
         * Set the current coex unsafe channels to avoid and their restrictions.
         *
         * @param unsafeChannels List of {@link CoexUnsafeChannel} to avoid.
         * @param restrictions int containing a bitwise-OR combination of
         *                     {@link android.net.wifi.WifiManager.CoexRestriction}.
         * @return true if successful, false otherwise.
         */
        boolean setCoexUnsafeChannels(List<CoexUnsafeChannel> unsafeChannels, int restrictions);

        /**
         * Set the country code for this Wifi chip.
         *
         * @param code 2-byte country code to set (as defined in ISO 3166).
         * @return true if successful, false otherwise.
         */
        boolean setCountryCode(byte[] code);
    }

    public WifiChip(android.hardware.wifi.IWifiChip chip, Context context) {
        mWifiChip = createWifiChipAidlImplMockable(chip, context);
    }

    protected WifiChipAidlImpl createWifiChipAidlImplMockable(
            android.hardware.wifi.IWifiChip chip, Context context) {
        return new WifiChipAidlImpl(chip, context);
    }

    private <T> T validateAndCall(String methodStr, T defaultVal, @NonNull Supplier<T> supplier) {
        if (mWifiChip == null) {
            Log.wtf(TAG, "Cannot call " + methodStr + " because mWifiChip is null");
            return defaultVal;
        }
        return supplier.get();
    }

    /**
     * See comments for {@link IWifiChip#configureChip(int)}
     */
    public boolean configureChip(int modeId) {
        return validateAndCall("configureChip", false,
                () -> mWifiChip.configureChip(modeId));
    }

    /**
     * See comments for {@link IWifiChip#createApIface()}
     */
    public WifiApIface createApIface() {
        return validateAndCall("createApIface", null,
                () -> mWifiChip.createApIface());
    }

    /**
     * See comments for {@link IWifiChip#createBridgedApIface()}
     */
    public WifiApIface createBridgedApIface() {
        return validateAndCall("createBridgedApIface", null,
                () -> mWifiChip.createBridgedApIface());
    }

    /**
     * See comments for {@link IWifiChip#getApIface(String)}
     */
    public WifiApIface getApIface(String ifaceName) {
        return validateAndCall("getApIface", null,
                () -> mWifiChip.getApIface(ifaceName));
    }

    /**
     * See comments for {@link IWifiChip#getApIfaceNames()}
     */
    public List<String> getApIfaceNames() {
        return validateAndCall("getApIfaceNames", null,
                () -> mWifiChip.getApIfaceNames());
    }

    /**
     * See comments for {@link IWifiChip#getId()}
     */
    public int getId() {
        return validateAndCall("getId", -1, () -> mWifiChip.getId());
    }

    /**
     * See comments for {@link IWifiChip#getUsableChannels(int, int, int)}
     */
    //public List<WifiAvailableChannel> getUsableChannels(int band, int mode, int filter) {
    public int[] getUsableChannels(int band, int mode, int filter) {
        return validateAndCall("getUsableChannels", null,
                () -> mWifiChip.getUsableChannels(band, mode, filter));
    }

    /**
     * See comments for {@link IWifiChip#registerCallback(Callback)}
     */
    public boolean registerCallback(Callback callback) {
        return validateAndCall("registerCallback", false,
                () -> mWifiChip.registerCallback(callback));
    }

    /**
     * See comments for {@link IWifiChip#removeApIface(String)}
     */
    public boolean removeApIface(String ifaceName) {
        return validateAndCall("removeApIface", false,
                () -> mWifiChip.removeApIface(ifaceName));
    }

    /**
     * See comments for {@link IWifiChip#removeIfaceInstanceFromBridgedApIface(String, String)}
     */
    public boolean removeIfaceInstanceFromBridgedApIface(String brIfaceName, String ifaceName) {
        return validateAndCall("removeIfaceInstanceFromBridgedApIface", false,
                () -> mWifiChip.removeIfaceInstanceFromBridgedApIface(brIfaceName, ifaceName));
    }

    /**
     * See comments for {@link IWifiChip#setCoexUnsafeChannels(List, int)}
     */
    public boolean setCoexUnsafeChannels(List<CoexUnsafeChannel> unsafeChannels, int restrictions) {
        return validateAndCall("setCoexUnsafeChannels", false,
                () -> mWifiChip.setCoexUnsafeChannels(unsafeChannels, restrictions));
    }

    /**
     * See comments for {@link IWifiChip#setCountryCode(byte[])}
     */
    public boolean setCountryCode(String countryCode) {
        if (countryCode == null || countryCode.length() != 2) {
            Log.e(TAG, "Invalid country code " + countryCode);
            return false;
        }
        try {
            final byte[] code = countryCode.getBytes(StandardCharsets.UTF_8);
            return validateAndCall("setCountryCode", false,
                    () -> mWifiChip.setCountryCode(code));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid country code " + countryCode + ", error: " + e);
            return false;
        }
    }
}
