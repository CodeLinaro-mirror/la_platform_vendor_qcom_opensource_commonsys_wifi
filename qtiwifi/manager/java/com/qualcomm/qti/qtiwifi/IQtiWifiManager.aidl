/* Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.qualcomm.qti.qtiwifi;
import com.qualcomm.qti.qtiwifi.ICsiCallback;
import com.qualcomm.qti.qtiwifi.ThermalData;
import com.qualcomm.qti.qtiwifi.IVendorEventCallback;
import com.qualcomm.qti.qtiwifi.IQtiInterfaceCallback;
import com.qualcomm.qti.qtiwifi.CsiConfiguration;

interface IQtiWifiManager
{
    void startCsi();
    void stopCsi();
    void scheduleCsiStart(int duration);
    void scheduleCsiStop(int duration);
    void registerCsiCallback(in IBinder binder, in ICsiCallback callback, int callbackIdentifier);
    void unregisterCsiCallback(int callbackIdentifier);
    List<String> getAvailableInterfaces();
    void registerVendorEventCallback(in IVendorEventCallback callback, in int callbackIdentifier);
    void unregisterVendorEventCallback(in int callbackIdentifier);
    ThermalData getThermalInfo(String ifname);
    boolean setTxPower(String ifname, int dbm);
    String getBssInfo();
    String getStatsBssInfo(in byte[] MacAddress);
    void registerCallback(in IBinder binder, in IQtiInterfaceCallback cb, int callbackIdentifier);
    void unregisterCallback(int callbackIdentifier);
    void setCsiConfiguration(in CsiConfiguration config);
    CsiConfiguration getCsiConfiguration();

}
