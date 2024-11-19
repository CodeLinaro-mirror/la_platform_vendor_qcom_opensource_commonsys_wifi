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

import android.annotation.NonNull;
import android.net.wifi.WifiManager;
import android.os.WorkSource;
import android.util.Log;

import com.qualcomm.qti.server.wifiextend.WifiChip.WifiAvailableChannel;
import com.qualcomm.qti.wifiextend.CoexUnsafeChannel;
import com.qualcomm.qti.wifiextend.MacAddress;
import com.qualcomm.qti.wifiextend.SoftApConfiguration;
import com.qualcomm.qti.wifiextend.QtiWifiExtendManager;

public class WifiNative {
    private static final String TAG = "ExtendWifiNative";
    private final WifiHal mWifiHal;
    private final HostapdHal mHostapdHal;
    QtiHostapdHal mQtiHostapdHal;
    private final QtiWifiExtendThreadRunner mEventHandler;
    private final Object mLock = new Object();
    public static final int START_HAL_RETRY_TIMES = 3;
    private static final int START_HAL_RETRY_INTERVAL_MS = 20;
    WifiChip mWifiChip = null;
    private final WifiHal.Callback mWifiEventCallback;
	private final WifiChipEventCallback mWifiChipEventCallback;
    private WifiDeathRecipient mIWifiDeathRecipient;
    private HashMap<String, WifiApIface> mWifiApIfaces = new HashMap<>();
    private boolean mIsQtiHostapdHalInitialized = false;
    private final List<CoexUnsafeChannel> mCurrentCoexUnsafeChannels = new ArrayList<>();

    public WifiNative(WifiHal wifihal,
                   HostapdHal hostapdHal,
                   QtiHostapdHal qtiHostapdHal,
                   QtiWifiExtendThreadRunner handler) {
            mWifiHal = wifihal;
            mHostapdHal = hostapdHal;
            mQtiHostapdHal = qtiHostapdHal;
            mEventHandler = handler;
            mWifiEventCallback = new WifiEventCallback();
            mWifiChipEventCallback = new WifiChipEventCallback();
            mIWifiDeathRecipient = new WifiDeathRecipient();
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

    public boolean initialize() {
        synchronized (mLock) {
            wifiHalInitializeInternal();
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
            int band, @NonNull SoftApManager softApManager) {
            if (!initializeAndStartHostapd()) {
                Log.e(TAG, "hostapd fail to start");
            }

            WifiApIface apIface = createBridgeApInterface();
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

    public void checkAndInitHostapdVendorHal() {
        Log.i(TAG, "checkAndInitHostapdVendorHal");
        //qtiHostapdHal = new QtiHostapdHal();
        mQtiHostapdHal.initialize();
        mQtiHostapdHal.registerWifiHalListener(null);
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

    private WifiApIface createBridgeApInterface() {
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
            WifiApIface iface = null;
            iface =(WifiApIface) mWifiChip.createBridgedApIface();
            if (iface == null) {
                Log.e(TAG, "createBridgeApInterface: failed to create bridgedAp interface");
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
            Log.e(TAG, "Fail to teardown interface " + ifaceName);
       	    return;
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
     * Death handler for the hostapd daemon.
     */
    private class HostapdDeathHandlerInternal implements HostapdDeathEventHandler {
        @Override
        public void onDeath() {
            synchronized (mLock) {
                Log.i(TAG, "hostapd died. Cleaning up internal state.");
          //      onNativeDaemonDeath();
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
                    //WifiChipInfo[] wifiChipInfos = getAllChipInfo();
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
