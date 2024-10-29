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

import com.qualcomm.qti.wifiextend.SoftApConfiguration;

class SoftApModeConfiguration {
    private final String mCountryCode;
    private final SoftApConfiguration mSoftApConfig;

    SoftApModeConfiguration(SoftApConfiguration config, String countryCode) {
        mSoftApConfig = config;
        mCountryCode = countryCode;
    }

    public String getCountryCode() {
        return mCountryCode;
    }

    public SoftApConfiguration getSoftApConfiguration() {
        return mSoftApConfig;
    }

}
