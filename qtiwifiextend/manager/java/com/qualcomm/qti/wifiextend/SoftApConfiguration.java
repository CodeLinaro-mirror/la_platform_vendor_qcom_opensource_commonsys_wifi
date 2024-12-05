/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.qualcomm.qti.wifiextend;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.SparseIntArray;
import android.util.Log;
import android.text.TextUtils;

import java.util.Objects;
import java.util.List;
import java.util.ArrayList;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;

public final class SoftApConfiguration implements Parcelable {

    private static final String TAG = "ExtendSoftApConfiguration";
    static final int PSK_MIN_LEN = 8;
    static final int PSK_MAX_LEN = 63;
    public static final int BAND_2GHZ = 1 << 0;
    public static final int BAND_5GHZ = 1 << 1;
    public static final int BAND_6GHZ = 1 << 2;
    public static final int BAND_60GHZ = 1 << 3;
    public static final int BAND_ANY = BAND_2GHZ | BAND_5GHZ | BAND_6GHZ;

    public static int[] BAND_TYPES = {BAND_2GHZ, BAND_5GHZ, BAND_6GHZ, BAND_60GHZ};

    private static boolean isBandValid(int band) {
        int bandAny = BAND_2GHZ | BAND_5GHZ | BAND_6GHZ | BAND_60GHZ;
        return ((band != 0) && ((band & ~bandAny) == 0));
    }

    private static final int MIN_CH_2G_BAND = 1;
    private static final int MAX_CH_2G_BAND = 14;
    private static final int MIN_CH_5G_BAND = 34;
    private static final int MAX_CH_5G_BAND = 196;
    private static final int MIN_CH_6G_BAND = 1;
    private static final int MAX_CH_6G_BAND = 253;
    private static final int MIN_CH_60G_BAND = 1;
    private static final int MAX_CH_60G_BAND = 6;

    private static boolean isChannelBandPairValid(int channel, int band) {
        switch (band) {
            case BAND_2GHZ:
                if (channel < MIN_CH_2G_BAND || channel >  MAX_CH_2G_BAND) {
                    return false;
                }
                break;

            case BAND_5GHZ:
                if (channel < MIN_CH_5G_BAND || channel >  MAX_CH_5G_BAND) {
                    return false;
                }
                break;

            case BAND_6GHZ:
                if (channel < MIN_CH_6G_BAND || channel >  MAX_CH_6G_BAND) {
                    return false;
                }
                break;

            case BAND_60GHZ:
                if (channel < MIN_CH_60G_BAND || channel >  MAX_CH_60G_BAND) {
                    return false;
                }
                break;

            default:
                return false;
        }
        return true;
    }

    private final WifiSsid mWifiSsid;
    private final MacAddress mBssid;
    private final String mPassphrase;

    /**
     * The operating security type of the AP.
     * One of the following security types:
     * {@link #SECURITY_TYPE_OPEN},
     * {@link #SECURITY_TYPE_WPA2_PSK},
     * {@link #SECURITY_TYPE_WPA3_SAE_TRANSITION},
     * {@link #SECURITY_TYPE_WPA3_SAE},
     * {@link #SECURITY_TYPE_WPA3_OWE_TRANSITION},
     * {@link #SECURITY_TYPE_WPA3_OWE}
     */
    private final int mSecurityType;
    private final SparseIntArray mChannels;
    private final boolean mHiddenSsid;
    private final int mMaxNumberOfClients;
    /**
     * The MAC address randomization settings of the AP.
     * One of the following settings:
     * {@link #RANDOMIZATION_NONE},
     * {@link #RANDOMIZATION_PERSISTENT},
     * {@link #RANDOMIZATION_NON_PERSISTENT},
     */
    private int mMacRandomizationSetting;
    private final MacAddress mPersistentRandomizedMacAddress;
    private final List<MacAddress> mBlockedClientList;
    private final List<MacAddress> mAllowedClientList;

    public static final int SECURITY_TYPE_OPEN = 0;
    public static final int SECURITY_TYPE_WPA2_PSK = 1;
    public static final int SECURITY_TYPE_WPA3_SAE_TRANSITION = 2;
    public static final int SECURITY_TYPE_WPA3_SAE = 3;
    public static final int SECURITY_TYPE_WPA3_OWE_TRANSITION = 4;
    public static final int SECURITY_TYPE_WPA3_OWE = 5;

    public static final int RANDOMIZATION_NONE = 0;
    public static final int RANDOMIZATION_PERSISTENT = 1;
    public static final int RANDOMIZATION_NON_PERSISTENT = 2;

    private SoftApConfiguration(WifiSsid ssid, MacAddress bssid, String passphrase,
        int securityType, SparseIntArray channels, boolean hiddenSsid,
        int maxNumberOfClients, int macRandomizationSetting,
        MacAddress persistentRandomizedMacAddress,
        List<MacAddress> blockedList, List<MacAddress> allowedList) {

        mWifiSsid = ssid;
        mBssid = bssid;
        mPassphrase = passphrase;
        mSecurityType = securityType;
        if (channels.size() != 0) {
            mChannels = channels.clone();
        } else {
            mChannels = new SparseIntArray(1);
            mChannels.put(BAND_2GHZ, 0);
        }
        mHiddenSsid = hiddenSsid;
        mMaxNumberOfClients = maxNumberOfClients;
        mMacRandomizationSetting = macRandomizationSetting;
        mPersistentRandomizedMacAddress = persistentRandomizedMacAddress;
        mBlockedClientList = new ArrayList<>(blockedList);
        mAllowedClientList = new ArrayList<>(allowedList);
    }

    public boolean equals(Object otherObj) {
        if (this == otherObj) {
            return true;
        }
        if (!(otherObj instanceof SoftApConfiguration)) {
            return false;
        }
        SoftApConfiguration other = (SoftApConfiguration) otherObj;
        return Objects.equals(mWifiSsid, other.mWifiSsid)
                && Objects.equals(mBssid, other.mBssid)
                && Objects.equals(mPassphrase, other.mPassphrase)
                && mSecurityType == other.mSecurityType
                && mChannels.toString().equals(other.mChannels.toString())
                && mHiddenSsid == other.mHiddenSsid
                && mMaxNumberOfClients == other.mMaxNumberOfClients
                && mMacRandomizationSetting == other.mMacRandomizationSetting
                && Objects.equals(mPersistentRandomizedMacAddress,
                        other.mPersistentRandomizedMacAddress)
                && Objects.equals(mBlockedClientList, other.mBlockedClientList)
                && Objects.equals(mAllowedClientList, other.mAllowedClientList);
    }

    public int hashCode() {
        return Objects.hash(mWifiSsid, mBssid, mPassphrase, mSecurityType,
                mChannels.toString(), mHiddenSsid, mMaxNumberOfClients,
                mMacRandomizationSetting, mPersistentRandomizedMacAddress,
                mBlockedClientList, mAllowedClientList);
    }

    public String toString() {
        StringBuilder sbuf = new StringBuilder();
        sbuf.append("ssid = ").append(mWifiSsid == null ? null : mWifiSsid.toString());
        if (mBssid != null) sbuf.append(" \n bssid = ").append(mBssid.toString());
        sbuf.append(" \n Passphrase = ").append(
                TextUtils.isEmpty(mPassphrase) ? "<empty>" : "<non-empty>");
        sbuf.append(" \n SecurityType = ").append(getSecurityType());
        sbuf.append(" \n Channels = ").append(mChannels);
        sbuf.append(" \n HiddenSsid = ").append(mHiddenSsid);
        sbuf.append(" \n MaxClient = ").append(mMaxNumberOfClients);
        sbuf.append(" \n MacRandomizationSetting = ").append(mMacRandomizationSetting);
        sbuf.append(" \n mPersistentRandomizedMacAddress = ")
                .append(mPersistentRandomizedMacAddress);
        sbuf.append(" \n BlockedClientList = ").append(mBlockedClientList);
        sbuf.append(" \n AllowedClientList= ").append(mAllowedClientList);
        return sbuf.toString();
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mWifiSsid, 0);
        dest.writeParcelable(mBssid, flags);
        dest.writeString(mPassphrase);
        dest.writeInt(mSecurityType);
        writeSparseIntArray(dest, mChannels);
        dest.writeBoolean(mHiddenSsid);
        dest.writeInt(mMaxNumberOfClients);
        dest.writeInt(mMacRandomizationSetting);
        dest.writeParcelable(mPersistentRandomizedMacAddress, flags);
        dest.writeTypedList(mBlockedClientList);
        dest.writeTypedList(mAllowedClientList);
    }

    private static void writeSparseIntArray(Parcel dest,
            SparseIntArray val) {
        if (val == null) {
            dest.writeInt(-1);
            return;
        }
        int n = val.size();
        dest.writeInt(n);
        int i = 0;
        while (i < n) {
            dest.writeInt(val.keyAt(i));
            dest.writeInt(val.valueAt(i));
            i++;
        }
    }

    private static SparseIntArray readSparseIntArray(Parcel in) {
        int n = in.readInt();
        if (n < 0) {
            return new SparseIntArray();
        }
        SparseIntArray sa = new SparseIntArray(n);
        while (n > 0) {
            int key = in.readInt();
            int value = in.readInt();
            sa.append(key, value);
            n--;
        }
        return sa;
    }

    public int describeContents() {
        return 0;
    }

    public static final Creator<SoftApConfiguration> CREATOR = new Creator<SoftApConfiguration>() {
        public SoftApConfiguration createFromParcel(Parcel in) {
            return new SoftApConfiguration(
                    in.readParcelable(WifiSsid.class.getClassLoader()),
                    in.readParcelable(MacAddress.class.getClassLoader()),
                    in.readString(), in.readInt(), readSparseIntArray(in), in.readBoolean(), 
                    in.readInt(), in.readInt(), in.readParcelable(MacAddress.class.getClassLoader()),
                    in.createTypedArrayList(MacAddress.CREATOR),
                    in.createTypedArrayList(MacAddress.CREATOR));
        }

        @Override
        public SoftApConfiguration[] newArray(int size) {
            return new SoftApConfiguration[size];
        }
    };

    public WifiSsid getWifiSsid() {
        return mWifiSsid;
    }

    public MacAddress getBssid() {
        return mBssid;
    }

    public String getPassphrase() {
        return mPassphrase;
    }

    public int getSecurityType() {
        return mSecurityType;
    }

    public int getBand() {
        return mChannels.keyAt(0);
    }

    public int getChannel() {
        return mChannels.valueAt(0);
    }
    public int[] getBands() {
        int[] bands = new int[mChannels.size()];
        for (int i = 0; i < bands.length; i++) {
            bands[i] = mChannels.keyAt(i);
        }
        return bands;
    }

    public SparseIntArray getChannels() {
        return mChannels.clone();
    }

    public boolean isHiddenSsid() {
        return mHiddenSsid;
    }

    public int getMaxNumberOfClients() {
        return mMaxNumberOfClients;
    }

    public int getMacRandomizationSetting() {
        return mMacRandomizationSetting;
    }

    public MacAddress getPersistentRandomizedMacAddress() {
        return mPersistentRandomizedMacAddress;
    }

    public List<MacAddress> getBlockedClientList() {
        return mBlockedClientList;
    }

    public List<MacAddress> getAllowedClientList() {
        return mAllowedClientList;
    }

    public static final class Builder {
        private WifiSsid mWifiSsid;
        private MacAddress mBssid;
        private String mPassphrase;
        private int mSecurityType;
        private SparseIntArray mChannels;
        private boolean mHiddenSsid;
        private int mMaxNumberOfClients;
        private int mMacRandomizationSetting;
        private MacAddress mPersistentRandomizedMacAddress;
        private List<MacAddress> mBlockedClientList;
        private List<MacAddress> mAllowedClientList;

        /**
         * Constructs a Builder with default values (see {@link Builder}).
         */
        public Builder() {
            mWifiSsid = null;
            mBssid = null;
            mPassphrase = null;
            mSecurityType = SECURITY_TYPE_OPEN;
            mChannels = new SparseIntArray(1);
            mChannels.put(BAND_2GHZ, 0);
            mHiddenSsid = false;
            mMaxNumberOfClients = 0;
            mMacRandomizationSetting = RANDOMIZATION_NON_PERSISTENT;
            mPersistentRandomizedMacAddress = null;
            mBlockedClientList = new ArrayList<>();
            mAllowedClientList = new ArrayList<>();
        }

        /**
         * Constructs a Builder initialized from an existing {@link SoftApConfiguration} instance.
         */
        public Builder(SoftApConfiguration other) {
            if (other == null) {
                Log.e(TAG, "Cannot provide a null SoftApConfiguration");
                return;
            }

            mWifiSsid = other.mWifiSsid;
            mBssid = other.mBssid;
            mPassphrase = other.mPassphrase;
            mSecurityType = other.mSecurityType;
            mChannels = other.mChannels.clone();
            mHiddenSsid = other.mHiddenSsid;
            mMaxNumberOfClients = other.mMaxNumberOfClients;
            mMacRandomizationSetting = other.mMacRandomizationSetting;
            mPersistentRandomizedMacAddress = other.mPersistentRandomizedMacAddress;
            mBlockedClientList = new ArrayList<>(other.mBlockedClientList);
            mAllowedClientList = new ArrayList<>(other.mAllowedClientList);
 
            if (mBssid != null) {
                // Auto set correct MAC randomization setting for the legacy SoftApConfiguration
                // to avoid the exception happen when framework (system server) copy
                // SoftApConfiguration.
                mMacRandomizationSetting = RANDOMIZATION_NONE;
            }
        }

        /**
         * Builds the {@link SoftApConfiguration}.
         *
         * @return A new {@link SoftApConfiguration}, as configured by previous method calls.
         */
        public SoftApConfiguration build() {
            for (MacAddress client : mAllowedClientList) {
                if (mBlockedClientList.contains(client)) {
                    throw new IllegalArgumentException("A MacAddress exist in both client list");
                }
            }

            // mMacRandomizationSetting supported from S.
            if (mBssid != null && mMacRandomizationSetting != RANDOMIZATION_NONE) {
                throw new IllegalArgumentException("A BSSID had configured but MAC randomization"
                        + " setting is not NONE");
            }

            return new SoftApConfiguration(mWifiSsid, mBssid, mPassphrase,
                    mSecurityType, mChannels, mHiddenSsid, mMaxNumberOfClients,
                    mMacRandomizationSetting, mPersistentRandomizedMacAddress,
                    mBlockedClientList, mAllowedClientList);
        }

        public Builder setWifiSsid(WifiSsid wifiSsid) {
            mWifiSsid = wifiSsid;
            return this;
        }

        public Builder setWifiSsid(String ssid) {
            if (ssid == null) {
                mWifiSsid = null;
                return this;
            }

            //Preconditions.checkStringNotEmpty(ssid);
            //Preconditions.checkArgument(StandardCharsets.UTF_8.newEncoder().canEncode(ssid));
            mWifiSsid = WifiSsid.fromUtf8Text(ssid);
            return this;
        }

        public Builder setBssid(MacAddress bssid) {
            if (bssid != null && !bssid.equals(MacAddress.ALL_ZEROS_MAC_ADDRESS)) {
                if (bssid.getAddressType() != MacAddress.TYPE_UNICAST) {
                    throw new IllegalArgumentException("bssid doesn't support "
                            + "multicast or broadcast mac address");
                }
            }
            mBssid = bssid;
            return this;
        }

        public Builder setPassphrase(String passphrase, int securityType) {

            if (securityType == SECURITY_TYPE_OPEN
                    || securityType == SECURITY_TYPE_WPA3_OWE_TRANSITION
                    || securityType == SECURITY_TYPE_WPA3_OWE) {
                if (passphrase != null) {
                    throw new IllegalArgumentException(
                            "passphrase should be null when security type is open");
                }
            } else {
                if ((securityType == SECURITY_TYPE_WPA2_PSK
                        || securityType == SECURITY_TYPE_WPA3_SAE_TRANSITION)) {
                    int passphraseByteLength = 0;
                    if (!TextUtils.isEmpty(passphrase)) {
                        passphraseByteLength = passphrase.getBytes(StandardCharsets.UTF_8).length;
                    }
                    if (passphraseByteLength < PSK_MIN_LEN || passphraseByteLength > PSK_MAX_LEN) {
                        throw new IllegalArgumentException(
                                "Passphrase length must be at least " + PSK_MIN_LEN
                                        + " and no more than " + PSK_MAX_LEN
                                        + " for WPA2_PSK and WPA3_SAE_TRANSITION Mode");
                    }
                }
            }
            mSecurityType = securityType;
            mPassphrase = passphrase;
            return this;
        }

        public Builder setHiddenSsid(boolean hiddenSsid) {
            mHiddenSsid = hiddenSsid;
            return this;
        }

        public Builder setBand(int band) {
            if (!isBandValid(band)) {
                throw new IllegalArgumentException("Invalid band type: " + band);
            }
            mChannels = new SparseIntArray(1);
            mChannels.put(band, 0);
            return this;
        }

        public Builder setBands(int[] bands) {
            if (bands.length == 0 || bands.length > 2) {
                throw new IllegalArgumentException("Unsupported number of bands("
                        + bands.length + ") configured");
            }
            SparseIntArray channels = new SparseIntArray(bands.length);
            for (int val : bands) {
                if (!isBandValid(val)) {
                    throw new IllegalArgumentException("Invalid band type: " + val);
                }
                channels.put(val, 0);
            }
            mChannels = channels;
            return this;
        }

        public Builder setChannel(int channel, int band) {
            if (!isChannelBandPairValid(channel, band)) {
                throw new IllegalArgumentException("Invalid channel(" + channel
                        + ") & band (" + band + ") configured");
            }
            mChannels = new SparseIntArray(1);
            mChannels.put(band, channel);
            return this;
        }

        public Builder setChannels(SparseIntArray channels) {
            if (channels.size() == 0 || channels.size() > 2) {
                throw new IllegalArgumentException("Unsupported number of channels("
                        + channels.size() + ") configured");
            }
            for (int i = 0; i < channels.size(); i++) {
                int channel = channels.valueAt(i);
                int band = channels.keyAt(i);
                if (channel == 0) {
                    if (!isBandValid(band)) {
                        throw new IllegalArgumentException("Invalid band type: " + band);
                    }
                } else {
                    if (!isChannelBandPairValid(channel, band)) {
                        throw new IllegalArgumentException("Invalid channel(" + channel
                                + ") & band (" + band + ") configured");
                    }
                }
            }
            mChannels = channels.clone();
            return this;
        }

        public Builder setMaxNumberOfClients(int maxNumberOfClients) {
            if (maxNumberOfClients < 0) {
                throw new IllegalArgumentException("maxNumberOfClients should be not negative");
            }
            mMaxNumberOfClients = maxNumberOfClients;
            return this;
        }

        public Builder setAllowedClientList(List<MacAddress> allowedClientList) {
            mAllowedClientList = new ArrayList<>(allowedClientList);
            return this;
        }

        public Builder setBlockedClientList(List<MacAddress> blockedClientList) {
            mBlockedClientList = new ArrayList<>(blockedClientList);
            return this;
        }

        public Builder setMacRandomizationSetting(
                int macRandomizationSetting) {
            mMacRandomizationSetting = macRandomizationSetting;
            return this;
        }

        public Builder setRandomizedMacAddress(MacAddress mac) {
            if (mac == null) {
                throw new IllegalArgumentException("setRandomizedMacAddress received"
                        + " null MacAddress.");
            }
            mPersistentRandomizedMacAddress = mac;
            return this;
        }
    }
}
