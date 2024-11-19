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
import java.util.Objects;
import com.qualcomm.qti.wifiextend.MacAddress;

public final class SoftApInfo implements Parcelable {

    public static final int CHANNEL_WIDTH_AUTO = -1;
    public static final int CHANNEL_WIDTH_INVALID = 0;
    public static final int CHANNEL_WIDTH_20MHZ_NOHT = 1;
    public static final int CHANNEL_WIDTH_20MHZ = 2;
    public static final int CHANNEL_WIDTH_40MHZ = 3;
    public static final int CHANNEL_WIDTH_80MHZ = 4;
    public static final int CHANNEL_WIDTH_80MHZ_PLUS_MHZ = 5;
    public static final int CHANNEL_WIDTH_160MHZ = 6;
    public static final int CHANNEL_WIDTH_2160MHZ = 7;
    public static final int CHANNEL_WIDTH_4320MHZ = 8;
    public static final int CHANNEL_WIDTH_6480MHZ = 9;
    public static final int CHANNEL_WIDTH_8640MHZ = 10;
    public static final int CHANNEL_WIDTH_320MHZ = 11;

    public static final int WIFI_STANDARD_UNKNOWN = 0;
    public static final int WIFI_STANDARD_LEGACY = 1;
    public static final int WIFI_STANDARD_11N = 4;
    public static final int WIFI_STANDARD_11AC = 5;
    public static final int WIFI_STANDARD_11AX = 6;
    public static final int WIFI_STANDARD_11AD = 7;
    public static final int WIFI_STANDARD_11BE = 8;

    private int mFrequency = 0;
    private int mBandwidth = CHANNEL_WIDTH_INVALID;
    private MacAddress mBssid;
    private String mApInstanceIdentifier;
    private int mWifiStandard = WIFI_STANDARD_UNKNOWN;

    public int getFrequency() {
        return mFrequency;
    }

    public void setFrequency(int freq) {
        mFrequency = freq;
    }

    public int getBandwidth() {
        return mBandwidth;
    }

    public void setBandwidth(int bandwidth) {
        mBandwidth = bandwidth;
    }

    public MacAddress getBssid() {
        return mBssid;
    }

    public void setBssid(MacAddress bssid) {
        if (bssid != null && !bssid.equals(MacAddress.ALL_ZEROS_MAC_ADDRESS)
            && !bssid.equals(MacAddress.BROADCAST_ADDRESS)) {
                mBssid = bssid;
        }
    }

    public void setWifiStandard(int wifiStandard) {
        mWifiStandard = wifiStandard;
    }

    public int getWifiStandard() {
        return mWifiStandard;
    }

    public void setApInstanceIdentifier(String apInstanceIdentifier) {
        mApInstanceIdentifier = apInstanceIdentifier;
    }

    public String getApInstanceIdentifier() {
        return mApInstanceIdentifier;
    }

    public SoftApInfo(SoftApInfo source) {
        if (source != null) {
            mFrequency = source.mFrequency;
            mBandwidth = source.mBandwidth;
            mBssid = source.mBssid;
            mApInstanceIdentifier = source.mApInstanceIdentifier;
            mWifiStandard = source.mWifiStandard;
        }
    }

    public SoftApInfo() {
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mFrequency);
        dest.writeInt(mBandwidth);
        dest.writeParcelable(mBssid, flags);
        dest.writeString(mApInstanceIdentifier);
        dest.writeInt(mWifiStandard);
    }

    public static final Creator<SoftApInfo> CREATOR = new Creator<SoftApInfo>() {
        public SoftApInfo createFromParcel(Parcel in) {
            SoftApInfo info = new SoftApInfo();
            info.mFrequency = in.readInt();
            info.mBandwidth = in.readInt();
            info.mBssid = in.readParcelable(MacAddress.class.getClassLoader());
            info.mApInstanceIdentifier = in.readString();
            info.mWifiStandard = in.readInt();
            return info;
        }

        public SoftApInfo[] newArray(int size) {
            return new SoftApInfo[size];
        }
    };

    public String toString() {
        StringBuilder sbuf = new StringBuilder();
        sbuf.append("SoftApInfo{");
        sbuf.append("bandwidth= ").append(mBandwidth);
        sbuf.append(", frequency= ").append(mFrequency);
        if (mBssid != null) sbuf.append(",bssid=").append(mBssid.toString());
        sbuf.append(", mApInstanceIdentifier= ").append(mApInstanceIdentifier);
        sbuf.append(", wifiStandard= ").append(mWifiStandard);
        sbuf.append("}");
        return sbuf.toString();
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SoftApInfo)) return false;
        SoftApInfo softApInfo = (SoftApInfo) o;
        return mFrequency == softApInfo.mFrequency
                && mBandwidth == softApInfo.mBandwidth
                && Objects.equals(mBssid, softApInfo.mBssid)
                && Objects.equals(mApInstanceIdentifier, softApInfo.mApInstanceIdentifier)
                && mWifiStandard == softApInfo.mWifiStandard;
    }

    public int hashCode() {
        return Objects.hash(mFrequency, mBandwidth, mBssid, mApInstanceIdentifier, mWifiStandard);
    }
}
