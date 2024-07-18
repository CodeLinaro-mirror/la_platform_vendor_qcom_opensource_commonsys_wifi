/**
 * Copyright (c) 2008, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import com.qualcomm.qti.wifiextend.CoexUnsafeChannel;
import com.qualcomm.qti.wifiextend.IExtendSoftApCallback;
import com.qualcomm.qti.wifiextend.IVendorEventCallback;
import com.qualcomm.qti.wifiextend.SoftApConfiguration;
import com.qualcomm.qti.wifiextend.ThermalData;

/**
  * Service interface that exposes primitives for controlling SoftAp on extend target board
  */
interface IQtiWifiExtendManager {
    boolean startSoftAp(in SoftApConfiguration softApConfig);
    boolean stopSoftAp();
    SoftApConfiguration getSoftApConfiguration();
    boolean setSoftApConfiguration(in SoftApConfiguration softApConfig);
    int getWifiApState();
    void registerExtendSoftApCallback(in IExtendSoftApCallback callback, in int callbackIdentifier);
    void UnregisterExtendSoftApCallback(in int callbackIdentifier);

    int[] getUsableChannels(int band);
    void setCoexUnsafeChannels(in List<CoexUnsafeChannel> coexUnsafeChannels);
    void setDefaultCountryCode(String countryCode);

    String[] listHostapdVendorInterfaces();
    String doHostapdCtrlIfaceCmd(String ifname, String command);

    ThermalData getThermalInfo(String ifname);
    boolean setTxPower(String ifname, int dbm);
    boolean setAni(String ifname, int mode, int ofdmlvl);
    boolean setCongestionReport(String ifname, int enable, int threshold, int interval);
    String getClientIpAddress(in byte[] macAddr);
    boolean setDataSharing(boolean enable);

    /* IVendorEventCallback defined for vendor value added feature like:
     * thermal management, congestion report
     */
    void registerVendorEventCallback(in IVendorEventCallback callback, in int callbackIdentifier);
    void unregisterVendorEventCallback(in int callbackIdentifier);
}
