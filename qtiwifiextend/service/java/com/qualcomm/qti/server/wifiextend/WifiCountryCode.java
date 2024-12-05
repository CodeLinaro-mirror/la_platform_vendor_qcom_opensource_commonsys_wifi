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

import android.text.TextUtils;
import android.util.Log;
import com.qualcomm.qti.server.wifiextend.SoftApConfigStore;

public class WifiCountryCode {
    private static final String TAG = "ExtendWifiCountryCode";
    //mDriverCountryCode is unknown without wificond.
    //private String mDriverCountryCode = null;
    private String mDefaultCountryCodeFromUser = null;
    private String mDefaultCountryAfterBootUp = "US";
    private SoftApConfigStore mSoftApConfigStore;

    public WifiCountryCode(SoftApConfigStore softApConfigStore) {
        mSoftApConfigStore = softApConfigStore;
    }

    //After setDefaultCountryCode, OEM service need to stop/start SoftAp
    //to make it take effect
    public void setDefaultCountryCodeFromUser(String countryCode) {
        if (TextUtils.isEmpty(countryCode)) {
            Log.d(TAG, "Fail to set default country code because the country code is empty");
            return;
        }
        mDefaultCountryCodeFromUser = countryCode;
        mSoftApConfigStore.setCountryCode(countryCode);
        Log.i(TAG, "Default country code updated in config store: " + countryCode);
    }

    public synchronized String getCountryCode() {
        String countryCodeFromFile = mSoftApConfigStore.getCountryCode();
        if (mDefaultCountryCodeFromUser != null) {
            return mDefaultCountryCodeFromUser;
        } else if (countryCodeFromFile != null) {
            return countryCodeFromFile;
        } else {
            return mDefaultCountryAfterBootUp;
        }
    }

}
