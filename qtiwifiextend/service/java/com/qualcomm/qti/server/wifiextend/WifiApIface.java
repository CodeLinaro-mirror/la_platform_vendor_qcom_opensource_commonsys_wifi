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

import com.qualcomm.qti.wifiextend.MacAddress;
import android.util.Log;

import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.function.Supplier;

public class WifiApIface implements WifiHal.WifiInterface {
    private static final String TAG = "ExtendWifiApIface";
    private final IWifiApIface mWifiApIface;

    public WifiApIface(android.hardware.wifi.IWifiApIface apIface) {
        mWifiApIface = createWifiApIfaceAidlImplMockable(apIface);
    }

    protected WifiApIfaceAidlImpl createWifiApIfaceAidlImplMockable(
            android.hardware.wifi.IWifiApIface apIface) {
        return new WifiApIfaceAidlImpl(apIface);
    }

    private <T> T validateAndCall(String methodStr, T defaultVal, Supplier<T> supplier) {
        if (mWifiApIface == null) {
            Log.wtf(TAG, "Cannot call " + methodStr + " because mWifiApIface is null");
            return defaultVal;
        }
        return supplier.get();
    }

    /** Abstraction of WifiApIface */
    public interface IWifiApIface {
        /**
         * Get the name of this interface.
         *
         * @return Name of this interface, or null on error.
         */
        String getName();

        /**
         * Get the names of the bridged AP instances.
         *
         * @return List containing the names of the bridged AP instances,
         *         or an empty vector for a non-bridged AP. Returns null
         *         if an error occurred.
         */
        List<String> getBridgedInstances();

        /**
         * Gets the factory MAC address of the interface.
         *
         * @return Factory MAC address of the interface, or null on error.
         */
        MacAddress getFactoryMacAddress();

        /**
         * Set the country code for this interface.
         *
         * @param countryCode two-letter country code (as ISO 3166).
         * @return true if successful, false otherwise.
         */
        boolean setCountryCode(byte[] countryCode);

        /**
         * Reset all the AP interfaces' MAC address to the factory MAC address.
         *
         * @return true if successful, false otherwise.
         */
        boolean resetToFactoryMacAddress();

        /**
         * Check whether {@link #setMacAddress(MacAddress)} is supported by this HAL.
         *
         * @return true if supported, false otherwise.
         */
        boolean isSetMacAddressSupported();

        /**
         * Changes the MAC address of the interface to the given MAC address.
         *
         * @param mac MAC address to change to.
         * @return true if successful, false otherwise.
         */
        boolean setMacAddress(MacAddress mac);
    }

    /**
     * See comments for {@link IWifiApIface#getName()}
     */
    @Override
    public String getName() {
        return validateAndCall("getName", null,
                () -> mWifiApIface.getName());
    }

    /**
     * See comments for {@link IWifiApIface#getBridgedInstances()}
     */
    public List<String> getBridgedInstances() {
        return validateAndCall("getBridgedInstances", null,
                () -> mWifiApIface.getBridgedInstances());
    }

    /**
     * See comments for {@link IWifiApIface#getFactoryMacAddress()}
     */
    public MacAddress getFactoryMacAddress() {
        return validateAndCall("getFactoryMacAddress", null,
                () -> mWifiApIface.getFactoryMacAddress());
    }

    /**
     * See comments for {@link IWifiApIface#setCountryCode(byte[])}
     */
    public boolean setCountryCode(String countryCode) {
        if (countryCode == null || countryCode.length() != 2) {
            Log.e(TAG, "Invalid country code " + countryCode);
            return false;
        }
        try {
            final byte[] code = countryCode.getBytes(StandardCharsets.UTF_8);
            return validateAndCall("setCountryCode", false,
                    () -> mWifiApIface.setCountryCode(code));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid country code " + countryCode + ", error: " + e);
            return false;
        }

    }

    /**
     * See comments for {@link IWifiApIface#resetToFactoryMacAddress()}
     */
    public boolean resetToFactoryMacAddress() {
        return validateAndCall("resetToFactoryMacAddress", false,
                () -> mWifiApIface.resetToFactoryMacAddress());
    }

    /**
     * See comments for {@link IWifiApIface#isSetMacAddressSupported()}
     */
    public boolean isSetMacAddressSupported() {
        return validateAndCall("isSetMacAddressSupported", false,
                () -> mWifiApIface.isSetMacAddressSupported());
    }

    /**
     * See comments for {@link IWifiApIface#setMacAddress(MacAddress)}
     */
    public boolean setMacAddress(MacAddress mac) {
        if (mac == null) {
            Log.e(TAG, "setMacAddress received a null MAC address");
            return false;
        }
        return validateAndCall("setMacAddress", false,
                () -> mWifiApIface.setMacAddress(mac));
    }

}
