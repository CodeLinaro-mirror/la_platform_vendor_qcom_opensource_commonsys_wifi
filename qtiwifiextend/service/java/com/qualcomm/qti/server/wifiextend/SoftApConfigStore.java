/*
 * Copyright (C) 2010 The Android Open Source Project
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

import static com.qualcomm.qti.wifiextend.SoftApConfiguration.SECURITY_TYPE_WPA2_PSK;
import static com.qualcomm.qti.wifiextend.SoftApConfiguration.SECURITY_TYPE_WPA3_OWE_TRANSITION;
import static com.qualcomm.qti.wifiextend.SoftApConfiguration.SECURITY_TYPE_WPA3_SAE;
import static com.qualcomm.qti.wifiextend.SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION;

import com.qualcomm.qti.wifiextend.SoftApConfiguration;
import com.qualcomm.qti.wifiextend.MacAddress;
import com.qualcomm.qti.wifiextend.WifiSsid;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import android.content.Context;
import android.util.SparseIntArray;
import android.text.TextUtils;
import android.util.Log;

public class SoftApConfigStore {
    private static final String TAG = "ExtendSoftApConfigStore";

    private String filePath;
    private String softApConfigFileStr;
    private String countryCodeFileStr;
    private static final String softApConfigFileName = "ExtendSoftApConfig.properties";
    private static final String countryCodeFileName = "ExtendSoftApCountryCode.properties";
    private static final String defaultSsid = "Android";

    private static final int RAND_SSID_INT_MIN = 1000;
    private static final int RAND_SSID_INT_MAX = 9999;

    static final int SAE_ASCII_MIN_LEN = 1;

    static final int PSK_ASCII_MIN_LEN = 8;

    static final int PSK_SAE_ASCII_MAX_LEN = 63;

    private String mCountryCode;

    private SoftApConfiguration mPersistentWifiApConfig;

    private final Context mContext;

    public SoftApConfigStore(Context context) {
        mContext = context;
        filePath = context.getFilesDir().getPath();
        softApConfigFileStr = filePath + "/" + softApConfigFileName;
        countryCodeFileStr = filePath + "/" + countryCodeFileName;
        File apConfigFile = new File(softApConfigFileStr);
        if (apConfigFile.exists()) {
            mPersistentWifiApConfig = getSoftApConfigFromFile();
        } else {
            mPersistentWifiApConfig = null;
        }

        File countryCodeFile = new File(countryCodeFileStr);
        if (countryCodeFile.exists()) {
            mCountryCode = getCountryCodeFromFile();
        } else {
            mCountryCode = null;
        }

    }

    public void setCountryCode(String countryCode) {
        mCountryCode = countryCode;
        saveCountryCodeIntoFile(mCountryCode);
    }

    public String getCountryCode() {
        return mCountryCode;
    }

    private void saveCountryCodeIntoFile(String countryCode) {
        Log.d(TAG, "save country code into file " + countryCodeFileStr);
        try {
            FileOutputStream fs = new FileOutputStream(countryCodeFileStr, false);
            Properties prop = new Properties();
            prop.setProperty("CountryCode", ((countryCode == null) ? "null" : countryCode));
            prop.store(new OutputStreamWriter(fs, "utf-8"),
                    "Extend SoftAp default countryCode from User Setting");
            fs.close();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private String getCountryCodeFromFile() {
        Log.d(TAG, "get country code from file " + countryCodeFileStr);
        try {
            FileInputStream fs = new FileInputStream(countryCodeFileStr);
            Properties prop = new Properties();
            prop.load(new InputStreamReader(fs, "utf-8"));
            String countryCode = prop.getProperty("CountryCode");
            fs.close();
            return (countryCode.equals("null")) ? null : countryCode;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        return null;
    }

    public synchronized SoftApConfiguration getSoftApConfiguration() {
        if (mPersistentWifiApConfig == null) {
            /* Use default configuration. */
            Log.d(TAG, "ApConfig is null, use default AP configuration and save it into file");
            mPersistentWifiApConfig = updatePersistentRandomizedMacAddress(getDefaultApConfiguration());
            saveSoftApConfigIntoFile(mPersistentWifiApConfig);
        }
        return mPersistentWifiApConfig;
    }

    public synchronized void setSoftApConfiguration(SoftApConfiguration config) {
        if (config == null) {
            Log.d(TAG, "new config is null, use default AP configuration");
            mPersistentWifiApConfig = updatePersistentRandomizedMacAddress(getDefaultApConfiguration());
            saveSoftApConfigIntoFile(mPersistentWifiApConfig);
        } else {
            mPersistentWifiApConfig = updatePersistentRandomizedMacAddress(config);
            saveSoftApConfigIntoFile(mPersistentWifiApConfig);
        }
    }

    private void saveSoftApConfigIntoFile(SoftApConfiguration softApConfig) {
        Log.d(TAG, "save ApConfig into file " + softApConfigFileStr);
        try {
            FileOutputStream fs = new FileOutputStream(softApConfigFileStr, false);
            Properties prop = new Properties();
            prop.setProperty("WifiSsid",
                ((softApConfig.getWifiSsid() == null) ? "null" : softApConfig.getWifiSsid().toString()));
            prop.setProperty("Bssid",
                ((softApConfig.getBssid() == null) ? "null" : softApConfig.getBssid().toString()));
            prop.setProperty("Passphrase",
                ((softApConfig.getPassphrase() == null) ? "null" : softApConfig.getPassphrase()));
            prop.setProperty("SecurityType", Integer.toString(softApConfig.getSecurityType()));

            SparseIntArray channels = softApConfig.getChannels();
            for (int i = 0; i < channels.size(); i++) {
                prop.setProperty("band" + i, Integer.toString(channels.keyAt(i)));
                prop.setProperty("channel" + i, Integer.toString(channels.valueAt(i)));
            }

            prop.setProperty("HiddenSsid", Boolean.toString(softApConfig.isHiddenSsid()));
            prop.setProperty("MaxNumberOfClients", Integer.toString(softApConfig.getMaxNumberOfClients()));
            prop.setProperty("MacRandomizationSetting", Integer.toString(softApConfig.getMacRandomizationSetting()));
            prop.setProperty("PersistentRandomizedMacAddress", softApConfig.getPersistentRandomizedMacAddress().toString());

            String blockMacStr = null;
            for (MacAddress mac : softApConfig.getBlockedClientList()) {
                blockMacStr += mac.toString() + "#";
            }
            prop.setProperty("BlockedClientList",
            ((blockMacStr == null) ? "null" : blockMacStr));

            String allowMacStr = null;
            for (MacAddress mac : softApConfig.getAllowedClientList()) {
                allowMacStr += mac.toString() + "#";
            }
            prop.setProperty("AllowedClientList",
            ((allowMacStr == null) ? "null" : allowMacStr));

            prop.store(new OutputStreamWriter(fs, "utf-8"),
                    "Extend SoftAp configuration");
            fs.close();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private SoftApConfiguration getSoftApConfigFromFile() {
        Log.d(TAG, "read ApConfig from file " + softApConfigFileStr);
        SoftApConfiguration.Builder softApConfigBuilder = new SoftApConfiguration.Builder();
        int securityType = SoftApConfiguration.SECURITY_TYPE_OPEN;
        String passphrase = null;
        String bssid = null;
        int[] channels = {-1 ,-1};
        int[] apBands = {-1, -1};
	SparseIntArray channelsArray = new SparseIntArray();
        List<MacAddress> blockedList = new ArrayList<>();
        List<MacAddress> allowedList = new ArrayList<>();
        try {
            InputStream fs = new BufferedInputStream(new FileInputStream(softApConfigFileStr));
            Properties prop = new Properties();

            prop.load(new InputStreamReader(fs, "utf-8"));
            Set<String> keys = prop.stringPropertyNames();
            for (String key : keys) {
                String value = prop.getProperty(key);
                if (value.equals("null")) {
                    continue;
                }
                switch (key) {
                    case "WifiSsid":
                        final WifiSsid wifiSsid = WifiSsid.fromString(value);
                        softApConfigBuilder.setWifiSsid(wifiSsid);
                        break;
                    case "Bssid":
                        bssid = value;
                        softApConfigBuilder.setBssid(MacAddress.fromString(bssid));
                        break;
                    case "Passphrase":
                        passphrase = value;
                        break;
                    case "SecurityType":
                        securityType = Integer.parseInt(value);
                        break;
                    case "band0":
                        apBands[0] = Integer.parseInt(value);
                        break;
                    case "band1":
                        apBands[1] = Integer.parseInt(value);
                        break;
                    case "channel0":
                        channels[0] = Integer.parseInt(value);
                        break;
                    case "channel1":
                        channels[1] = Integer.parseInt(value);
                        break;
                    case "HiddenSsid":
                        softApConfigBuilder.setHiddenSsid(Boolean.parseBoolean(value));
                        break;
                    case "MaxNumberOfClients":
                        softApConfigBuilder.setMaxNumberOfClients(Integer.parseInt(value));
                        break;
                    case "MacRandomizationSetting":
                        softApConfigBuilder.setMacRandomizationSetting(Integer.parseInt(value));
                        break;
                    case "PersistentRandomizedMacAddress":
                        softApConfigBuilder.setRandomizedMacAddress(MacAddress.fromString(value));
                        break;
                    case "BlockedClientList":
                        String[] blockClientStrs = value.split("#");
                        for (String str : blockClientStrs) {
                            blockedList.add(MacAddress.fromString(str));
                        }
                        softApConfigBuilder.setBlockedClientList(blockedList);
                        break;
                    case "AllowedClientList":
                        String[] allowClientStrs = value.split("#");
                        for (String str : allowClientStrs) {
                            allowedList.add(MacAddress.fromString(str));
                        }
                        softApConfigBuilder.setAllowedClientList(allowedList);
                        break;
                    default:
                        Log.w(TAG, "Ignoring unknown tag found: " + key);
                        break;
                }
            }
            if (ApConfigUtil.isNonPasswordAP(securityType)) {
                softApConfigBuilder.setPassphrase(null, securityType);
            } else {
                softApConfigBuilder.setPassphrase(passphrase, securityType);
            }

            for (int i = 0; i < channels.length; i++) {
                if ((channels[i] != -1) && (apBands[i] != -1)) {
		    channelsArray.put(apBands[i], channels[i]);
                }
            }
            if ((channelsArray.size() == 1) ||(channelsArray.size() == 2))
                softApConfigBuilder.setChannels(channelsArray);

            if (bssid != null) {
                // Force MAC randomization setting to none when BSSID is configured
                softApConfigBuilder.setMacRandomizationSetting(
                        SoftApConfiguration.RANDOMIZATION_NONE);
            }
            fs.close();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        return softApConfigBuilder.build();
    }

    private SoftApConfiguration getDefaultApConfiguration() {
        SoftApConfiguration.Builder configBuilder = new SoftApConfiguration.Builder();
        configBuilder.setWifiSsid(defaultSsid + "_" + getRandomIntForDefaultSsid());
        configBuilder.setPassphrase(generatePassword(),
        SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION);

        int[] dual_bands = new int[] {
            SoftApConfiguration.BAND_2GHZ,
            SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ};
        configBuilder.setBands(dual_bands);

        return configBuilder.build();
    }

    private static int getRandomIntForDefaultSsid() {
        Random random = new Random();
        return random.nextInt((RAND_SSID_INT_MAX - RAND_SSID_INT_MIN) + 1) + RAND_SSID_INT_MIN;
    }

    private static String generatePassword() {
        // Characters that will be used for password generation. Some characters commonly known to
        // be confusing like 0 and O excluded from this list.
        final String allowed = "23456789abcdefghijkmnpqrstuvwxyz";
        final int passLength = 15;

        StringBuilder sb = new StringBuilder(passLength);
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < passLength; i++) {
            sb.append(allowed.charAt(random.nextInt(allowed.length())));
        }
        return sb.toString();
    }

    private SoftApConfiguration updatePersistentRandomizedMacAddress(SoftApConfiguration config) {
        if (config.getPersistentRandomizedMacAddress() != null) {
            return config;
        }
        MacAddress randomizedMacAddress = generateRandomMacAddress();
        return new SoftApConfiguration.Builder(config)
                .setRandomizedMacAddress(randomizedMacAddress).build();
    }

    private MacAddress generateRandomMacAddress() {
        byte MAC_ADDRESS_LOCALLY_ASSIGNED_BYTE_MASK = (1 << 1);
        byte MAC_ADDRESS_MULTICAST_BYTE_MASK = (1 << 0);
        Random random = new Random();
        byte[] macAddrBytes = new byte[6];
        random.nextBytes(macAddrBytes);
        macAddrBytes[0] |= MAC_ADDRESS_LOCALLY_ASSIGNED_BYTE_MASK;
        macAddrBytes[0] &= ~MAC_ADDRESS_MULTICAST_BYTE_MASK;

        return MacAddress.fromBytes(macAddrBytes);
    }

    public static boolean validateApWifiConfiguration(SoftApConfiguration apConfig) {
        // first check the SSID
        WifiSsid ssid = apConfig.getWifiSsid();
        if (ssid == null || ssid.getBytes().length == 0) {
            Log.d(TAG, "SSID for softap configuration cannot be null or 0 length.");
            return false;
        }

        // BSSID can be set if caller own permission:android.Manifest.permission.NETWORK_SETTINGS.
        if (apConfig.getBssid() != null) {
            Log.e(TAG, "Config BSSID needs NETWORK_SETTINGS permission");
            return false;
        }

        String preSharedKey = apConfig.getPassphrase();
        boolean hasPreSharedKey = !TextUtils.isEmpty(preSharedKey);
        int authType;

        try {
            authType = apConfig.getSecurityType();
        } catch (IllegalStateException e) {
            Log.d(TAG, "Unable to get AuthType for softap config: " + e.getMessage());
            return false;
        }

        if (ApConfigUtil.isNonPasswordAP(authType)) {
            // open networks should not have a password
            if (hasPreSharedKey) {
                Log.d(TAG, "open softap network should not have a password");
                return false;
            }
        } else if (authType == SECURITY_TYPE_WPA2_PSK
                || authType == SECURITY_TYPE_WPA3_SAE_TRANSITION
                || authType == SECURITY_TYPE_WPA3_SAE) {
            // this is a config that should have a password - check that first
            if (!hasPreSharedKey) {
                Log.d(TAG, "softap network password must be set");
                return false;
            }

           {
                final CharsetEncoder asciiEncoder = StandardCharsets.US_ASCII.newEncoder();
                if (!asciiEncoder.canEncode(preSharedKey)) {
                    Log.d(TAG, "passphrase not ASCII encodable");
                    return false;
                }
                if (!validateApConfigAsciiPreSharedKey(authType, preSharedKey)) {
                    // failed preSharedKey checks for WPA2 and WPA3 SAE (Transition) mode.
                    return false;
                }
            }
        } else {
            // this is not a supported security type
            Log.d(TAG, "softap configs must either be open or WPA2 PSK networks");
            return false;
        }

        if (ApConfigUtil.isSecurityTypeRestrictedFor6gBand(authType)) {
            for (int band : apConfig.getBands()) {
                // Only return failure if requested band is limitted to 6GHz only
                if (band == SoftApConfiguration.BAND_6GHZ) {
                    Log.d(TAG, "security type is not allowed for softap in 6GHz band");
                    return false;
                }
            }
        }

        if (authType == SECURITY_TYPE_WPA3_OWE_TRANSITION) {
            if (apConfig.getBands().length > 1) {
                Log.d(TAG, "softap owe transition must use single band");
                return false;
            }
        }

        return true;
    }

    private static boolean validateApConfigAsciiPreSharedKey(int securityType, String preSharedKey) {
        final int sharedKeyLen = preSharedKey.length();
        final int keyMinLen = securityType == SECURITY_TYPE_WPA3_SAE
                ? SAE_ASCII_MIN_LEN : PSK_ASCII_MIN_LEN;
        if (sharedKeyLen < keyMinLen || sharedKeyLen > PSK_SAE_ASCII_MAX_LEN) {
            Log.d(TAG, "softap network password string size must be at least " + keyMinLen
                    + " and no more than " + PSK_SAE_ASCII_MAX_LEN + " when type is "
                    + securityType);
            return false;
        }

        try {
            preSharedKey.getBytes(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "softap network password verification failed: malformed string");
            return false;
        }
        return true;
    }

}
