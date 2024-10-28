/*
 * Copyright (C) 2008 The Android Open Source Project
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

import android.os.Binder;
import android.content.Context;
import android.annotation.NonNull;
import android.net.wifi.WifiManager;
import android.os.WorkSource;
import android.os.RemoteException;
import android.os.RemoteCallbackList;
import android.util.Log;

import com.qualcomm.qti.server.wifiextend.WifiChip.WifiAvailableChannel;
import com.qualcomm.qti.wifiextend.CoexUnsafeChannel;
import com.qualcomm.qti.wifiextend.MacAddress;
import com.qualcomm.qti.wifiextend.SoftApConfiguration;
import com.qualcomm.qti.wifiextend.QtiWifiExtendManager;
import com.qualcomm.qti.wifiextend.IVendorEventCallback;
import com.qualcomm.qti.wifiextend.WifiClient;
import com.qualcomm.qti.wifiextend.ThermalData;

public class WifiNative {
    private static final String TAG = "ExtendWifiNative";
    private final Context mContext;
    private final WifiHal mWifiHal;
    private final HostapdHal mHostapdHal;
    private QtiHostapdHal mQtiHostapdHal;
    private QtiWifiHal mQtiWifiHal;
    private final QtiWifiExtendThreadRunner mEventHandler;
    private final Object mLock = new Object();
    public static final int START_HAL_RETRY_TIMES = 3;
    private static final int START_HAL_RETRY_INTERVAL_MS = 20;
    private WifiChip mWifiChip = null;
    private final WifiHal.Callback mWifiEventCallback;
    private final WifiChipEventCallback mWifiChipEventCallback;
	private QtiWifiHalInternalDeathRecipient mQtiWifiHalInternalDeathRecipient;
    private WifiDeathRecipient mIWifiDeathRecipient;
    private HashMap<String, WifiApIface> mWifiApIfaces = new HashMap<>();
    private boolean mIsQtiHostapdHalInitialized = false;
    private final List<CoexUnsafeChannel> mCurrentCoexUnsafeChannels = new ArrayList<>();
    private HashSet<StatusListener> mStatusListeners = new HashSet<>();

    private QtiWifiHal.QtiWifiHalListener mQtiHalListener;
    private boolean mIsQtiWifiHalInitialized = false;

    /* Vendor callbacks */
    private final RemoteCallbackList<IVendorEventCallback> mVendorEventCallbacks = new RemoteCallbackList<>();;
    private final HashMap<Integer, IVendorEventCallback> mVendorEventCallbacksMap = new HashMap<>();

    public WifiNative(Context context,
                   WifiHal wifihal,
                   HostapdHal hostapdHal,
                   QtiWifiExtendThreadRunner handler) {
            mContext = context;
            mWifiHal = wifihal;
            mHostapdHal = hostapdHal;
            mEventHandler = handler;
            mWifiEventCallback = new WifiEventCallback();
            mWifiChipEventCallback = new WifiChipEventCallback();
            mIWifiDeathRecipient = new WifiDeathRecipient();
            mQtiWifiHalInternalDeathRecipient = new QtiWifiHalInternalDeathRecipient();
            mQtiHalListener = new QtiWifiHalListenerImpl();
    }

    /**
     * Callbacks for SoftAp instance.
     */
    public interface SoftApHalCallback {
        /**
         * Invoked when there is a fatal failure and the SoftAp is shutdown.
         */
        void onFailure();

        /**
         * Invoked when there is a fatal happen in specific instance only.
         */
        default void onInstanceFailure(String instanceName) {}

        /**
         * Invoked when a channel switch event happens - i.e. the SoftAp is moved to a different
         * channel. Also called on initial registration.
         *
         * @param apIfaceInstance The identity of the ap instance.
         * @param frequency The new frequency of the SoftAp. A value of 0 is invalid and is an
         *                     indication that the SoftAp is not enabled.
         * @param bandwidth The new bandwidth of the SoftAp.
         * @param generation The new generation of the SoftAp.
         */
        void onInfoChanged(String apIfaceInstance, int frequency, int bandwidth,
                int generation, MacAddress apIfaceInstanceMacAddress);
        /**
         * Invoked when there is a change in the associated station (STA).
         *
         * @param apIfaceInstance The identity of the ap instance.
         * @param clientAddress Macaddress of the client.
         * @param isConnected Indication as to whether the client is connected (true), or
         *                    disconnected (false).
         */
        void onConnectedClientsChanged(String apIfaceInstance, MacAddress clientAddress,
                boolean isConnected);
    }

    /**
     * Callback to notify hostapd death.
     */
    public interface HostapdDeathEventHandler {
        /**
         * Invoked when the supplicant dies.
         */
        void onDeath();
    }

    /**
     * Callback to notify when the associated interface is destroyed, up or down.
     */
    public interface InterfaceCallback {
        /**
         * Interface destroyed by HalDeviceManager.
         *
         * @param ifaceName Name of the iface.
         */
        void onDestroyed(String ifaceName);

        /**
         * Interface is up.
         *
         * @param ifaceName Name of the iface.
         */
        void onUp(String ifaceName);

        /**
         * Interface is down.
         *
         * @param ifaceName Name of the iface.
         */
        void onDown(String ifaceName);
    }

    /**
     * Callback to notify when the status of one of the native daemons
     * (wificond, wpa_supplicant & vendor HAL) changes.
     */
    public interface StatusListener {
        /**
         * @param allReady Indicates if all the native daemons are ready for operation or not.
         */
        void onStatusChanged(boolean allReady);
    }

    private class QtiWifiHalListenerImpl implements QtiWifiHal.QtiWifiHalListener {
        int mLastThermalLevel = ThermalData.THERMAL_INFO_LEVEL_UNKNOWN;

        @Override
        public void onThermalChanged(String ifname, int level) {
            synchronized (mVendorEventCallbacks) {
                level = toFrameworkThermalLevel(level);
                // Reduce duplicate Thermal change event report.
                if (level == mLastThermalLevel) {
                    Log.d(TAG, "ignore duplicate report thermal with same level " + level);
                    return;
                }
                mLastThermalLevel = level;
                // Trigger callbacks
                int itemCount = mVendorEventCallbacks.beginBroadcast();
                for (int i = 0; i < itemCount; ++i) {
                    try {
                        mVendorEventCallbacks.getBroadcastItem(i).onThermalChanged(ifname, level);
                    } catch (Exception e) {
                        Log.e(TAG, "onThermalChanged error.");
                    }
                }
                mVendorEventCallbacks.finishBroadcast();
            }
        }

        @Override
        public void onCongestionChanged(String ifname, int percentage) {
            synchronized (mVendorEventCallbacks) {
                // Trigger callbacks
                int itemCount = mVendorEventCallbacks.beginBroadcast();
                for (int i = 0; i < itemCount; ++i) {
                    try {
                        mVendorEventCallbacks.getBroadcastItem(i).onCongestionChanged(
                                ifname, percentage);
                    } catch (Exception e) {
                        Log.e(TAG, "onCongestionChanged error.");
                    }
                }
                mVendorEventCallbacks.finishBroadcast();
            }
        }
    }

    /**
     * Register a StatusListener to get notified about any status changes from the native daemons.
     *
     * It is safe to re-register the same callback object - duplicates are detected and only a
     * single copy kept.
     *
     * @param listener StatusListener listener object.
     */
    public void registerStatusListener(@NonNull StatusListener listener) {
        synchronized (mLock) {
            mStatusListeners.add(listener);
        }
    }

    public boolean initialize() {
        synchronized (mLock) {
            wifiHalInitializeInternal();
            checkAndInitQtiWifiHal();
            return true;
        }
    }

    public boolean startSoftAp(
            @NonNull String ifaceName, SoftApConfiguration config, SoftApHalCallback callback) {
        if (!mHostapdHal.registerApCallback(ifaceName, callback)) {
            Log.e(TAG, "Failed to register hostapd hal event callback");
            return false;
        }

        if (!mHostapdHal.addAccessPoint(ifaceName, config, false, callback::onFailure)) {
            Log.e(TAG, "addAccessPoint failure");
            return false;
        }
        return true;
    }

    public String setupInterfaceForSoftApMode(
            @NonNull InterfaceCallback interfaceCallback, @NonNull WorkSource requestorWs,
            int band, boolean isBridged, @NonNull SoftApManager softApManager) {
            if (!startWifiHal()) {
                Log.e(TAG, "wifi HAL fail to start");
            }
            if (!initializeAndStartHostapd()) {
                Log.e(TAG, "hostapd fail to start");
            }

            WifiApIface apIface = createApInterface(isBridged);
            if (apIface != null) {
                //Init vendor hostapd HAL when AP interface create successfully
                checkAndInitHostapdVendorHal();
                return apIface.getName();
            }
            Log.e(TAG, "WifiApIface is null");
            return null;
   }

    public boolean isWifiStarted() {
        Log.d(TAG, "isWifiStart");
        synchronized (mLock) {
            return mWifiHal.isStarted();
        }
    }

    public boolean resetApMacToFactoryMacAddress(@NonNull String interfaceName) {
        synchronized (mLock) {
            WifiApIface iface = getApIface(interfaceName);
            if (iface == null) return false;
            return iface.resetToFactoryMacAddress();
        }
    }

    /**
     * Force a softap client disconnect with specific reason code.
     *
     * @param ifaceName Name of the interface.
     * @param client Mac address to force disconnect in clients of the SoftAp.
     * @param reasonCode One of disconnect reason code which defined in {@link ApConfigUtil}.
     * @return true on success, false otherwise.
     */
    public boolean forceClientDisconnect(@NonNull String ifaceName,
            @NonNull MacAddress client, int reasonCode) {
        return mHostapdHal.forceClientDisconnect(ifaceName, client, reasonCode);
    }

    public boolean setApMacAddress(String interfaceName, MacAddress mac) {
        synchronized (mLock) {
            WifiApIface iface = getApIface(interfaceName);
            if (iface == null) return false;
            return iface.setMacAddress(mac);
        }
    }

    public boolean setApCountryCode(@NonNull String ifaceName, String countryCode) {
        synchronized (mLock) {
            WifiApIface iface = getApIface(ifaceName);
            if (iface == null) return false;
            return iface.setCountryCode(countryCode);
        }
    }

    public boolean removeIfaceInstanceFromBridgedApIface(@NonNull String ifaceName,
            @NonNull String apIfaceInstance) {
        if (mWifiChip == null) return false;
        return mWifiChip.removeIfaceInstanceFromBridgedApIface(ifaceName, apIfaceInstance);
    }

    public List<String> getBridgedApInstances(@NonNull String ifaceName) {
        synchronized (mLock) {
            WifiApIface iface = getApIface(ifaceName);
            if (iface == null) return null;
            return iface.getBridgedInstances();
        }
    }

    public int[] getUsableChannels(int band) {
        synchronized (mLock) {
            if (mWifiChip == null) return null;
            return mWifiChip.getUsableChannels(band,
                WifiAvailableChannel.OP_MODE_SAP, WifiAvailableChannel.FILTER_CONCURRENCY);
        }
    }

    public void setCoexUnsafeChannels(@NonNull List<CoexUnsafeChannel> coexUnsafeChannels) {
        if (coexUnsafeChannels == null) {
            Log.e(TAG, "setCoexUnsafeChannels called with null unsafe channel set");
            return;
        }
        synchronized (mLock) {
            if (new HashSet(mCurrentCoexUnsafeChannels).equals(new HashSet(coexUnsafeChannels))) {
                // Do not update if the unsafe channels haven't changed since the last time
                return;
            }
            mCurrentCoexUnsafeChannels.clear();
            mCurrentCoexUnsafeChannels.addAll(coexUnsafeChannels);
            Log.d(TAG, "Current unsafe channels: " + mCurrentCoexUnsafeChannels);
            if (mWifiChip != null) {
               mWifiChip.setCoexUnsafeChannels(mCurrentCoexUnsafeChannels,
                                          QtiWifiExtendManager.COEX_RESTRICTION_SOFTAP);
            }
        }
    }

    private class QtiWifiHalInternalDeathRecipient implements QtiWifiHal.InternalDeathRecipient {
        @Override
        public void onDeath() {
            mEventHandler.run(() -> {
                Log.i(TAG, "ExtendQtiWifi HAL service died");
                synchronized (mLock) { // prevents race condition with surrounding method
                    mIsQtiWifiHalInitialized = false;
                    onNativeDaemonDeath();
                }
            });
        }
    }

    public void checkAndInitQtiWifiHal() {
        Log.i(TAG, "checkAndInitQtiWifiHal");
        mQtiWifiHal = new QtiWifiHal();
        mQtiWifiHal.initialize(mQtiWifiHalInternalDeathRecipient);
        mQtiWifiHal.registerWifiHalListener(mQtiHalListener);
        mIsQtiWifiHalInitialized = true;
    }

    public ThermalData getThermalInfo(String ifname) {
        if (mIsQtiWifiHalInitialized == false) {
            Log.e(TAG, "QtiWifiHal is not initialzied");
            return null;
        }

        final String kGetThermalCmd = "DRIVER GET_THERMAL_INFO";
        enforceAccessPermission();
        String reply;
        reply = mEventHandler.call(
                () -> mQtiWifiHal.doQtiWifiCmd(ifname, kGetThermalCmd), null);

        int[] info = new int[2];
        try {
            if (reply == null) {
                Log.e(TAG, "timeout to get thermal info");
                return null;
            } else {
                String[] infoString = reply.split("\\s+");
                info[0] = Integer.parseInt(infoString[0]);
                info[1] = Integer.parseInt(infoString[1]);
            }
        } catch (Exception e) {
            Log.e(TAG, "invalid result for get thermal info");
            return null;
        }
        ThermalData thermalData = new ThermalData();
        thermalData.setTemperature(info[0]);
        thermalData.setThermalLevel(toFrameworkThermalLevel(info[1]));
        return thermalData;
    }

    /**
     * Set TX power limitation in dBm.
     *
     * @param ifname Name of the interface.
     * @param dbm    TX power in dBm.
     * @return Results of setTxPower.
     *
     * @throws IllegalArgumentException if ifname is null.
     */
    public boolean setTxPower(String ifname, int dbm) {
        if (mIsQtiWifiHalInitialized == false) {
            Log.e(TAG, "QtiWifiHal is not initialzied");
            return false;
        }

        if (ifname == null) {
            throw new IllegalArgumentException("ifname cannot be null");
        }

        // vendor requirement to limit max tx power >= 8dBm.
        if (dbm < 8) {
            Log.e(TAG, "Expecting max tx power limit >= 8 dBm, while actual dBm=" + dbm);
            return false;
        }

        final String kSetTxPowerCmd = "DRIVER SET_TXPOWER " + dbm;
        String reply;

        Log.v(TAG, "setTxPower: ifname=" + ifname + " TX power=" + dbm);
        reply = mEventHandler.call(
                () -> mQtiWifiHal.doQtiWifiCmd(ifname, kSetTxPowerCmd), null);

        return setSuccess(reply);
    }

    /**
     * Set ANI level.
     *
     * @param ifname  Name of the interface.
     * @param mode    ani level mode(0: auto, 1: fixed, else: auto).
     * @param ofdmlvl ANI level.
     * @return result of setAni.
     *
     * @throws IllegalArgumentException if ifname is null.
     */
    public boolean setAni(String ifname, int mode, int ofdmlvl) {
        if (mIsQtiWifiHalInitialized == false) {
            Log.e(TAG, "QtiWifiHal is not initialzied");
            return false;
        }

        if (ifname == null) {
            throw new IllegalArgumentException("ifname cannot be null");
        }

        // we're not checking ofdmlvl here and mode is treated as 0 in hal layer if not
        // 1.
        final String kSetAniCmd = "DRIVER SET_ANI_LEVEL " + mode + " " + ofdmlvl;
        String reply;

        Log.v(TAG, "setAni: ifname=" + ifname + " mode=" + mode + " level=" + ofdmlvl);
        reply = mEventHandler.call(
                () -> mQtiWifiHal.doQtiWifiCmd(ifname, kSetAniCmd), null);

        return setSuccess(reply);
    }

    /**
     * Set Congestion report parameters.
     *
     * @param ifname Name of the interface.
     * @param enable ani level mode(0: auto, 1: fixed, else: auto).
     * @param thre   Only when congestion achieved the threshold need to report.
     * @param inter  Interval to report congestion.
     * @return result of setCongestionReport.
     *
     * @throws IllegalArgumentException if ifname is null.
     */
    public boolean setCongestionReport(String ifname, int enable, int thre, int inter) {
        if (mIsQtiWifiHalInitialized == false) {
            Log.e(TAG, "QtiWifiHal is not initialzied");
            return false;
        }

        if (ifname == null) {
            throw new IllegalArgumentException("ifname cannot be null");
        }

        final String kSetCongestionReportCmd = "DRIVER SET_CONGESTION_REPORT "
                + enable + " " + thre + " " + inter;
        String reply;
        // threshold and interval limitation are checked in hal layer
        Log.v(TAG, "setCongestionReport: ifname=" + ifname + " enable=" + enable
                + " threshold=" + thre + " interval=" + inter);
        reply = mEventHandler.call(
                () -> mQtiWifiHal.doQtiWifiCmd(ifname, kSetCongestionReportCmd), null);

        return setSuccess(reply);
    }

    public String getClientIpAddress(WifiClient client) {
        if (client == null) {
            throw new IllegalArgumentException("WifiClient cannot be null");
        }

        String ifname = client.getApInstanceIdentifier();
        String macaddr = client.getMacAddress().toString();
        String kGetClientIpAddressCmd = "DRIVER GET_CLIENT_IP_ADDRESS " + macaddr;
        String reply;
        Log.v(TAG, "getClientIpAddress: ifname = " + ifname + " macAddr = " + macaddr);
        reply = mEventHandler.call(
                () -> mQtiWifiHal.doQtiWifiCmd(ifname, kGetClientIpAddressCmd), null);
        return reply;
    }

    public boolean setDataSharing(String ifname, boolean enable) {
        if (mIsQtiWifiHalInitialized == false) {
            Log.e(TAG, "QtiWifiHal is not initialzied");
            return false;
        }

        String kSetDataSharingCmd = "DRIVER SET_DATA_SHARING " + enable;
        String reply;
        Log.v(TAG, "setDataSharing: ifname = " + ifname + " enable = " + enable);
        reply = mEventHandler.call(
                () -> mQtiWifiHal.doQtiWifiCmd(ifname, kSetDataSharingCmd), null);
        return setSuccess(reply);
    }

    public void registerVendorEventCallback(IVendorEventCallback callback,
            int callbackIdentifier) {
        // verify arguments
        if (callback == null) {
            throw new IllegalArgumentException("Callback must not be null");
        }
        enforceAccessPermission();
        Log.i(TAG, "registerVendorEventCallback uid=%" + Binder.getCallingUid());
        synchronized (mVendorEventCallbacks) {
            mVendorEventCallbacks.register(callback);
            mVendorEventCallbacksMap.put(callbackIdentifier, callback);
        }
    }

    public void unregisterVendorEventCallback(int callbackIdentifier) {
        Log.i(TAG, "registerVendorEventCallback uid=%" + Binder.getCallingUid());
        enforceAccessPermission();
        synchronized (mVendorEventCallbacks) {
            IVendorEventCallback callback = mVendorEventCallbacksMap.get(callbackIdentifier);
            if (callback == null) {
                Log.d(TAG, "no such registered callback found, id=" + callbackIdentifier);
                return;
            }
            mVendorEventCallbacks.unregister(callback);
            mVendorEventCallbacksMap.remove(callbackIdentifier);
        }
    }

    private int toFrameworkThermalLevel(int original_val) {
        switch (original_val) {
            case 0:
                return ThermalData.THERMAL_INFO_LEVEL_FULL_PERF;
            case 2:
                return ThermalData.THERMAL_INFO_LEVEL_REDUCED_PERF;
            case 4:
                return ThermalData.THERMAL_INFO_LEVEL_TX_OFF;
            case 5:
                return ThermalData.THERMAL_INFO_LEVEL_SHUT_DOWN;
        }
        return ThermalData.THERMAL_INFO_LEVEL_UNKNOWN;
    }

    private boolean setSuccess(String reply) {
        if (reply != null && reply.contains("OK")) {
            return true;
        }
        return false;
    }

    private void enforceAccessPermission() {
        mContext.enforceCallingOrSelfPermission(
            android.Manifest.permission.ACCESS_WIFI_STATE, TAG);
    }

    public void checkAndInitHostapdVendorHal() {
        Log.i(TAG, "checkAndInitHostapdVendorHal");
        mQtiHostapdHal = new QtiHostapdHal();
        mQtiHostapdHal.initialize();
        //Thermal and congestion report callback are registered into qtiwifi HAL
        //mQtiHostapdHal.registerWifiHalListener(null);
        mIsQtiHostapdHalInitialized = true;
    }

    public String[] listHostapdVendorInterfaces() {
        if (!mIsQtiHostapdHalInitialized) {
            return null;
        }
        return mEventHandler.call(() -> mQtiHostapdHal.listVendorInterfaces(), null);
    }

    public String doHostapdCtrlIfaceCmd(String ifname, String command) {
        if (!mIsQtiHostapdHalInitialized) {
            return null;
        }
        return mEventHandler.call(() -> mQtiHostapdHal.doCtrlIfaceCmd(ifname, command), null);
    }

    /** Helper method to lookup the corresponding AP iface object using iface name. */
    private WifiApIface getApIface(@NonNull String ifaceName) {
        synchronized (mLock) {
            return mWifiApIfaces.get(ifaceName);
        }
    }

    private WifiApIface createApInterface(boolean isBridged) {
        int request_mode_id = 3;
        synchronized(mLock) {
            if (!isWifiStarted()) {
                return null;
            }
            // get all chip IDs
            List<Integer> chipIds = mWifiHal.getChipIds();
            if (chipIds == null) {
                return null;
            }
            mWifiChip = mWifiHal.getChip(chipIds.get(0));
            if (mWifiChip == null) {
                Log.e(TAG, "Fail to get wifi chip from wifi Hal");
                return null;
            }
            if (!mWifiChip.configureChip(request_mode_id)) {
                Log.e(TAG, "Fail to configure wifi chip mode id");
                return null;
            }
            WifiApIface iface = null;
            if (isBridged == true) {
                iface =(WifiApIface) mWifiChip.createBridgedApIface();
            } else {
                iface =(WifiApIface) mWifiChip.createApIface();
            }
            if (iface == null) {
                Log.e(TAG, "createApInterface: failed to create Ap interface");
                return null;
            }
            String ifaceName = iface.getName();
            mWifiApIfaces.put(ifaceName, iface);
            return iface;
        }
    }

    public void teardownInterface(@NonNull String ifaceName) {
       if (getApIface(ifaceName) == null) return;
       if (!mWifiChip.removeApIface(ifaceName)) {
            Log.e(TAG, "Iwifichip fail to remove Ap interface " + ifaceName);
       }
       stopHostapd(ifaceName);
       stopWifiHal();
       mWifiApIfaces.remove(ifaceName);
    }

    public void stopHostapd(@NonNull String ifaceName) {
        if (!mHostapdHal.removeAccessPoint(ifaceName)) {
            Log.e(TAG, "Failed to remove access point on " + ifaceName);
        }
        if (!mHostapdHal.deregisterDeathHandler()) {
            Log.e(TAG, "Failed to deregister hostapd death handler");
        }
        mHostapdHal.terminate();
    }

    private boolean retrieveWifiChip(WifiHal.WifiInterface iface) {
        synchronized(mLock) {
            if (mWifiChip == null) {
                Log.e(TAG, "Fail to get wifi chip when register chip event callback");
                return false;
            }
            if (!registerChipCallback()) {
                Log.e(TAG, "Failed to register chip callback");
                mWifiChip = null;
                return false;
            }
        }
        return true;
    }

    /**
     * Registers the sta iface callback.
     */
    private boolean registerChipCallback() {
        synchronized (mLock) {
            if (mWifiChip == null) return false;
            return mWifiChip.registerCallback(mWifiChipEventCallback);
        }
    }

    private boolean initializeAndStartHostapd() {
        if (!startAndWaitForHostapdConnection()) {
            Log.e(TAG, "Failed to connect to hostapd");
            return false;
        }
        if (!mHostapdHal.registerDeathHandler(
                new HostapdDeathHandlerInternal())) {
            Log.e(TAG, "Failed to register hostapd death handler");
            return false;
        }
        return true;
    }

    /**
     * Helper method invoked to trigger the status changed callback after one of the native
     * daemon's death.
     */
    private void onNativeDaemonDeath() {
        synchronized (mLock) {
            for (StatusListener listener : mStatusListeners) {
                listener.onStatusChanged(false);
				Log.i(TAG, "native daemons died, trigger listener callback.");
            }
            for (StatusListener listener : mStatusListeners) {
                listener.onStatusChanged(true);
            }
        }
    }

    /**
     * Death handler for the hostapd daemon.
     */
    private class HostapdDeathHandlerInternal implements HostapdDeathEventHandler {
        @Override
        public void onDeath() {
            synchronized (mLock) {
                Log.i(TAG, "hostapd died. Cleaning up internal state.");
                mIsQtiHostapdHalInitialized = false;
                onNativeDaemonDeath();
            }
        }
    }
     private static final int CONNECT_TO_HOSTAPD_RETRY_INTERVAL_MS = 100;
     private static final int CONNECT_TO_HOSTAPD_RETRY_TIMES = 50;
     /**
      * This method is called to wait for establishing connection to hostapd.
      *
      * @return true if connection is established, false otherwise.
      */
     private boolean startAndWaitForHostapdConnection() {
         // Start initialization if not already started.
         if (!mHostapdHal.isInitializationStarted()
                 && !mHostapdHal.initialize()) {
             return false;
         }
         if (!mHostapdHal.startDaemon()) {
             Log.e(TAG, "Failed to startup hostapd");
             return false;
         }
         boolean connected = false;
         int connectTries = 0;
         while (!connected && connectTries++ < CONNECT_TO_HOSTAPD_RETRY_TIMES) {
             // Check if the initialization is complete.
             connected = mHostapdHal.isInitializationComplete();
             if (connected) {
                 break;
             }
             try {
                 Thread.sleep(CONNECT_TO_HOSTAPD_RETRY_INTERVAL_MS);
             } catch (InterruptedException ignore) {
             }
         }
         return connected;
     }

     private void wifiHalInitializeInternal() {
        mWifiHal.initialize(mIWifiDeathRecipient);
        mWifiHal.registerEventCallback(mWifiEventCallback);
     }

     private boolean startWifiHal() {
        Log.d(TAG, "startWifi");
        synchronized (mLock) {
            int triedCount = 0;
            while (triedCount <= START_HAL_RETRY_TIMES) {
                int status = mWifiHal.start();
                if (status == WifiHal.WIFI_STATUS_SUCCESS) {
                    //managerStatusListenerDispatch();
                    if (triedCount != 0) {
                        Log.d(TAG, "start IWifi succeeded after trying "
                                 + triedCount + " times");
                    }
                    return true;
                } else if (status == WifiHal.WIFI_STATUS_ERROR_NOT_AVAILABLE) {
                    // Should retry. Hal might still be stopping. the registered event
                    // callback will not be cleared.
                    Log.e(TAG, "Cannot start wifi because unavailable. Retrying...");
                    try {
                        Thread.sleep(START_HAL_RETRY_INTERVAL_MS);
                    } catch (InterruptedException ignore) {
                        // no-op
                    }
                    triedCount++;
                } else {
                    // Should not retry on other failures.
                    // Will be handled in the onFailure event.
                    Log.e(TAG, "Cannot start IWifi. Status: " + status);
                    return false;
                }
            }
            Log.e(TAG, "Cannot start IWifi after trying " + triedCount + " times");
            return false;
        }
     }

    private void stopWifiHal() {
        Log.d(TAG, "stopWifiHal");
        synchronized (mLock) {
            if (!mWifiHal.isInitializationComplete()) {
                Log.w(TAG, "stopWifi was called, but Wifi Hal is not initialized");
                return;
            }
            if (!mWifiHal.stop()) {
                Log.e(TAG, "Cannot stop IWifi");
            }
        }
    }

    private class WifiDeathRecipient implements WifiHal.DeathRecipient {
        @Override
        public void onDeath() {
            mEventHandler.run(() -> {
                Log.i(TAG, "extend Wifi HAL service died");
                synchronized (mLock) { // prevents race condition with surrounding method
                onNativeDaemonDeath();
                //teardownInternal();
                //stopWifiHal();
                }
            });
        }
    }

     private class WifiEventCallback implements WifiHal.Callback {
        @Override
        public void onStart() {
            mEventHandler.run(() -> {
                Log.d(TAG, "IWifiEventCallback.onStart");
                // NOP: only happens in reaction to my calls - will handle directly
            });
        }

        @Override
        public void onStop() {
            mEventHandler.run(() -> {
                Log.d(TAG, "IWifiEventCallback.onStop");
                // NOP: only happens in reaction to my calls - will handle directly
            });
        }

        @Override
        public void onFailure(int status) {
            mEventHandler.run(() -> {
                Log.e(TAG, "IWifiEventCallback.onFailure. Status: " + status);
                synchronized (mLock) {
                    //teardownInternal();
                    stopWifiHal();
                }
            });
        }

        @Override
        public void onSubsystemRestart(int status) {
            Log.i(TAG, "onSubsystemRestart");
            mEventHandler.run(() -> {
                Log.i(TAG, "IWifiEventCallback.onSubsystemRestart. Status: " + status);
                synchronized (mLock) {
                    Log.i(TAG, "Attempting to invoke mSubsystemRestartListener");
                    //for (SubsystemRestartListenerProxy cb : mSubsystemRestartListener) {
                    //    Log.i(TAG, "Invoking mSubsystemRestartListener");
                    //    cb.action();
                    //}
                }
            });
        }
    }

  /**
     * Callback for events on the chip.
     */
    private class WifiChipEventCallback implements WifiChip.Callback {
        @Override
        public void onChipReconfigured(int modeId) {
            Log.d(TAG, "onChipReconfigured " + modeId);
        }

        @Override
        public void onChipReconfigureFailure(int status) {
            Log.d(TAG, "onChipReconfigureFailure " + status);
        }

        public void onIfaceAdded(int type, String name) {
            Log.d(TAG, "onIfaceAdded " + type + ", name: " + name);
        }

        @Override
        public void onIfaceRemoved(int type, String name) {
            Log.d(TAG, "onIfaceRemoved " + type + ", name: " + name);
        }

        @Override
        public void onDebugRingBufferDataAvailable() {
            Log.d(TAG, "onDebugRingBufferDataAvailable");
        }

        @Override
        public void onDebugErrorAlert(int errorCode, byte[] debugData) {
            Log.d(TAG, "onDebugErrorAlert " + errorCode);

        }

        @Override
        public void onRadioModeChange() {
            Log.d(TAG, "onRadioModeChange");
        }
    }
}
