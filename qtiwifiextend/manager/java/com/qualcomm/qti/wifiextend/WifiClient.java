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
import android.util.Log;

import java.util.Objects;

public final class WifiClient implements Parcelable {

    private static final String TAG = "WifiExtendClient";

    private final MacAddress mMacAddress;

    /** The identifier of the AP instance which the client connected. */
    private final String mApInstanceIdentifier;

    /**
     * The mac address of this client.
     */
    public MacAddress getMacAddress() {
        return mMacAddress;
    }

    public String getApInstanceIdentifier() {
        return mApInstanceIdentifier;
    }

    private WifiClient(Parcel in) {
        mMacAddress = in.readParcelable(this.getClass().getClassLoader());
        mApInstanceIdentifier = in.readString();
    }

    public WifiClient(MacAddress macAddress, String apInstanceIdentifier) {
        if (macAddress == null) {
            Log.wtf(TAG, "Null MacAddress provided");
            this.mMacAddress = MacAddress.ALL_ZEROS_MAC_ADDRESS;
        } else {
            this.mMacAddress = macAddress;
        }
        this.mApInstanceIdentifier = apInstanceIdentifier;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mMacAddress, flags);
        dest.writeString(mApInstanceIdentifier);
    }

    public static final Creator<WifiClient> CREATOR = new Creator<WifiClient>() {
        public WifiClient createFromParcel(Parcel in) {
            return new WifiClient(in);
        }

        public WifiClient[] newArray(int size) {
            return new WifiClient[size];
        }
    };

    public String toString() {
        return "WifiClient{"
                + "mMacAddress=" + mMacAddress
                + "mApInstanceIdentifier=" + mApInstanceIdentifier
                + '}';
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WifiClient)) return false;
        WifiClient client = (WifiClient) o;
        return Objects.equals(mMacAddress, client.mMacAddress)
                && mApInstanceIdentifier.equals(client.mApInstanceIdentifier);
    }

    public int hashCode() {
        return Objects.hash(mMacAddress, mApInstanceIdentifier);
    }
}
