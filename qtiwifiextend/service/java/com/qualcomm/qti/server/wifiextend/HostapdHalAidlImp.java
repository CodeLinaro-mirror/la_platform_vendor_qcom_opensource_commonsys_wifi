/*
 * Copyright (C) 2021 The Android Open Source Project
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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.hardware.wifi.hostapd.ApInfo;
import android.hardware.wifi.hostapd.BandMask;
import android.hardware.wifi.hostapd.ChannelBandwidth;
import android.hardware.wifi.hostapd.ChannelParams;
import android.hardware.wifi.hostapd.ClientInfo;
import android.hardware.wifi.hostapd.DebugLevel;
import android.hardware.wifi.hostapd.EncryptionType;
import android.hardware.wifi.hostapd.FrequencyRange;
import android.hardware.wifi.hostapd.Generation;
import android.hardware.wifi.hostapd.HwModeParams;
import android.hardware.wifi.hostapd.IHostapd;
import android.hardware.wifi.hostapd.IHostapdCallback;
import android.hardware.wifi.hostapd.Ieee80211ReasonCode;
import android.hardware.wifi.hostapd.IfaceParams;
import android.hardware.wifi.hostapd.NetworkParams;

import android.net.wifi.WifiManager;
import android.annotation.NonNull;
import android.os.Handler;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceSpecificException;
import android.util.Log;
//import com.android.modules.utils.build.SdkLevel;

//import com.qualcomm.qti.server.wifiextend.WifiHal.DeathRecipient;
import com.qualcomm.qti.server.wifiextend.WifiNative.SoftApHalCallback;
import com.qualcomm.qti.server.wifiextend.WifiNative.HostapdDeathEventHandler;
import com.qualcomm.qti.server.wifiextend.HostapdHal.IHostapdHal;
import com.qualcomm.qti.wifiextend.MacAddress;
import com.qualcomm.qti.wifiextend.SoftApConfiguration;
import com.qualcomm.qti.wifiextend.SoftApInfo;
import com.qualcomm.qti.wifiextend.QtiWifiExtendManager;

public class HostapdHalAidlImp implements IHostapdHal {
    private static final String TAG = "ExtendHostapdHalAidlImp";
    private static final String HAL_INSTANCE_NAME = IHostapd.DESCRIPTOR + "/cem";
    public static final long WAIT_FOR_DEATH_TIMEOUT_MS = 50L;

    private final Object mLock = new Object();
    private boolean mVerboseLoggingEnabled = false;
    private boolean mVerboseHalLoggingEnabled = false;
    private final Context mContext;
    private final QtiWifiExtendThreadRunner mEventHandler;

    // Hostapd HAL interface objects
    private IHostapd mIHostapd;
    private HashMap<String, Runnable> mSoftApFailureListeners = new HashMap<>();
    private SoftApHalCallback mSoftApEventCallback;
    private Set<String> mActiveInstances = new HashSet<>();
    private HostapdDeathEventHandler mDeathEventHandler;
    private boolean mServiceDeclared = false;
    private CountDownLatch mWaitForDeathLatch;

    /**
     * Default death recipient. Called any time the service dies.
     */
    private class HostapdDeathRecipient implements DeathRecipient {
        private final IBinder mWho;
        @Override
        /* Do nothing as we override the default function binderDied(IBinder who). */
        public void binderDied() {
            synchronized (mLock) {
                Log.w(TAG, "IHostapd/IHostapd died. who " + mWho + " service "
                        + getServiceBinderMockable());
                if (mWho == getServiceBinderMockable()) {
                    if (mWaitForDeathLatch != null) {
                        mWaitForDeathLatch.countDown();
                    }
                    mEventHandler.run(() -> {
                        synchronized (mLock) {
                            Log.w(TAG, "Handle IHostapd/IHostapd died.");
                            hostapdServiceDiedHandler(mWho);
                        }
                    });
                }
            }
        }

        HostapdDeathRecipient(IBinder who) {
            mWho = who;
        }
    }

    public HostapdHalAidlImp(@NonNull Context context, @NonNull QtiWifiExtendThreadRunner handler) {
        mContext = context;
        mEventHandler = handler;
        Log.d(TAG, "init HostapdHalAidlImp");
    }

    /**
     * Enable/Disable verbose logging.
     *
     */
    @Override
    public void enableVerboseLogging(boolean verboseEnabled, boolean halVerboseEnabled) {
        synchronized (mLock) {
            mVerboseLoggingEnabled = verboseEnabled;
            mVerboseHalLoggingEnabled = halVerboseEnabled;
            setDebugParams();
        }
    }

    /**
     * Returns whether or not the hostapd supports getting the AP info from the callback.
     */
    @Override
    public boolean isApInfoCallbackSupported() {
        // Supported in the AIDL implementation
        return true;
    }

    /**
     * Checks whether the IHostapd service is declared, and therefore should be available.
     * @return true if the IHostapd service is declared
     */
    @Override
    public boolean initialize() {
        synchronized (mLock) {
            if (mVerboseLoggingEnabled) {
                Log.i(TAG, "Checking if IHostapd service is declared.");
            }
            mServiceDeclared = serviceDeclared();
            return mServiceDeclared;
        }
    }

    /**
     * Register for callbacks with the hostapd service. On service-side event,
     * the hostapd service will trigger our IHostapdCallback implementation, which
     * in turn calls the proper SoftApHalCallback registered with us by WifiNative.
     */
    private boolean registerCallback(IHostapdCallback callback) {
        synchronized (mLock) {
            String methodStr = "registerCallback";
            if (!checkHostapdAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mIHostapd.registerCallback(callback);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Register the provided callback handler for SoftAp events.
     * <p>
     * Note that only one callback can be registered at a time - any registration overrides previous
     * registrations.
     *
     * @param ifaceName Name of the interface.
     * @param listener Callback listener for AP events.
     * @return true on success, false on failure.
     */
    @Override
    public boolean registerApCallback(String ifaceName,
            WifiNative.SoftApHalCallback callback) {
        // TODO(b/195980798) : Create a hashmap to associate the listener with the ifaceName
        synchronized (mLock) {
            if (callback == null) {
                Log.e(TAG, "registerApCallback called with a null callback");
                return false;
            }
            mSoftApEventCallback = callback;
            Log.i(TAG, "registerApCallback Successful in " + ifaceName);
            return true;
        }
    }

    /**
     * Add and start a new access point.
     *
     * @param ifaceName Name of the interface.
     * @param config Configuration to use for the AP.
     * @param isMetered Indicates if the network is metered or not.
     * @param onFailureListener A runnable to be triggered on failure.
     * @return true on success, false otherwise.
     */
    @Override
    public boolean addAccessPoint(String ifaceName, SoftApConfiguration config,
            boolean isMetered, Runnable onFailureListener) {
        synchronized (mLock) {
            final String methodStr = "addAccessPoint";
            Log.d(TAG, methodStr + ": " + ifaceName);
            if (!checkHostapdAndLogFailure(methodStr)) {
                return false;
            }
            try {
                IfaceParams ifaceParams = prepareIfaceParams(ifaceName, config);
                NetworkParams nwParams = prepareNetworkParams(isMetered, config);
                if (ifaceParams == null || nwParams == null) {
                    Log.e(TAG, "addAccessPoint parameters could not be prepared.");
                    return false;
                }
                mIHostapd.addAccessPoint(ifaceParams, nwParams);
                mSoftApFailureListeners.put(ifaceName, onFailureListener);
                return true;
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Unrecognized apBand: " + config.getBand());
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Remove a previously started access point.
     *
     * @param ifaceName Name of the interface.
     * @return true on success, false otherwise.
     */
    @Override
    public boolean removeAccessPoint(String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "removeAccessPoint";
            if (!checkHostapdAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mSoftApFailureListeners.remove(ifaceName);
                mSoftApEventCallback = null;
                mIHostapd.removeAccessPoint(ifaceName);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Remove a previously connected client.
     *
     * @param ifaceName Name of the interface.
     * @param client Mac Address of the client.
     * @param reasonCode One of disconnect reason code which defined in {@link WifiManager}.
     * @return true on success, false otherwise.
     */
    @Override
    public boolean forceClientDisconnect(String ifaceName,
            MacAddress client, int reasonCode) {
        synchronized (mLock) {
            final String methodStr = "forceClientDisconnect";
            try {
                if (!checkHostapdAndLogFailure(methodStr)) {
                    return false;
                }
                byte[] clientMacByteArray = client.toByteArray();
                int disconnectReason;
                switch (reasonCode) {
                    case QtiWifiExtendManager.SAP_CLIENT_BLOCK_REASON_CODE_BLOCKED_BY_USER:
                        disconnectReason = Ieee80211ReasonCode.WLAN_REASON_PREV_AUTH_NOT_VALID;
                        break;
                    case QtiWifiExtendManager.SAP_CLIENT_BLOCK_REASON_CODE_NO_MORE_STAS:
                        disconnectReason = Ieee80211ReasonCode.WLAN_REASON_DISASSOC_AP_BUSY;
                        break;
                    case QtiWifiExtendManager.SAP_CLIENT_DISCONNECT_REASON_CODE_UNSPECIFIED:
                        disconnectReason = Ieee80211ReasonCode.WLAN_REASON_UNSPECIFIED;
                        break;
                    default:
                        throw new IllegalArgumentException(
                                "Unknown disconnect reason code:" + reasonCode);
                }
                mIHostapd.forceClientDisconnect(ifaceName, clientMacByteArray, disconnectReason);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Registers a death notification for hostapd.
     * @return Returns true on success.
     */
    @Override
    public boolean registerDeathHandler(HostapdDeathEventHandler handler) {
        synchronized (mLock) {
            if (mDeathEventHandler != null) {
                Log.e(TAG, "Death handler already present");
            }
            mDeathEventHandler = handler;
            return true;
        }
    }

    /**
     * Deregisters a death notification for hostapd.
     * @return Returns true on success.
     */
    @Override
    public boolean deregisterDeathHandler() {
        synchronized (mLock) {
            if (mDeathEventHandler == null) {
                Log.e(TAG, "No Death handler present");
                return false;
            }
            mDeathEventHandler = null;
            return true;
        }
    }

    /**
     * Handle hostapd death.
     */
    private void hostapdServiceDiedHandler(IBinder who) {
        synchronized (mLock) {
            if (who != getServiceBinderMockable()) {
                Log.w(TAG, "Ignoring stale death recipient notification");
                return;
            }
            mIHostapd = null;
            if (mDeathEventHandler != null) {
                mDeathEventHandler.onDeath();
            }
        }
    }

    private class HostapdCallback extends IHostapdCallback.Stub {
        @Override
        public void onFailure(String ifaceName, String instanceName) {
            Log.w(TAG, "Failure on iface " + ifaceName + ", instance: " + instanceName);
            Runnable onFailureListener = mSoftApFailureListeners.get(ifaceName);
            if (onFailureListener != null) {
                mActiveInstances.remove(instanceName);
                if (mActiveInstances.size() == 0) {
                    onFailureListener.run();
                } else if (mSoftApEventCallback != null) {
                    mSoftApEventCallback.onInstanceFailure(instanceName);
                }
            }
        }

        @Override
        public void onApInstanceInfoChanged(ApInfo info) {
            Log.v(TAG, "onApInstanceInfoChanged on " + info.ifaceName + " / "
                    + info.apIfaceInstance);
            try {
                if (mSoftApEventCallback != null) {
                    mSoftApEventCallback.onInfoChanged(info.apIfaceInstance, info.freqMhz,
                            mapHalChannelBandwidthToSoftApInfo(info.channelBandwidth),
                            mapHalGenerationToWifiStandard(info.generation),
                            MacAddress.fromBytes(info.apIfaceInstanceMacAddress));
                }
                mActiveInstances.add(info.apIfaceInstance);
            } catch (IllegalArgumentException iae) {
                Log.e(TAG, " Invalid apIfaceInstanceMacAddress, " + iae);
            }
        }

        @Override
        public void onConnectedClientsChanged(ClientInfo info) {
            try {
                Log.d(TAG, "onConnectedClientsChanged on " + info.ifaceName
                        + " / " + info.apIfaceInstance
                        + " and Mac is " + MacAddress.fromBytes(info.clientAddress).toString()
                        + " isConnected: " + info.isConnected);
                if (mSoftApEventCallback != null) {
                    mSoftApEventCallback.onConnectedClientsChanged(info.apIfaceInstance,
                            MacAddress.fromBytes(info.clientAddress), info.isConnected);
                }
            } catch (IllegalArgumentException iae) {
                Log.e(TAG, " Invalid clientAddress, " + iae);
            }
        }

        @Override
        public String getInterfaceHash() {
            return IHostapdCallback.HASH;
        }

        @Override
        public int getInterfaceVersion() {
            return IHostapdCallback.VERSION;
        }
    }

    /**
     * Signals whether Initialization started and found the declared service
     */
    @Override
    public boolean isInitializationStarted() {
        synchronized (mLock) {
            return mServiceDeclared;
        }
    }

    /**
     * Signals whether Initialization completed successfully.
     */
    @Override
    public boolean isInitializationComplete() {
        synchronized (mLock) {
            return mIHostapd != null;
        }
    }

    /**
     * Indicates whether the AIDL service is declared
     */
    public static boolean serviceDeclared() {
        // Service Manager API ServiceManager#isDeclared supported after T.
        return ServiceManager.isDeclared(HAL_INSTANCE_NAME);
    }

    /**
     * Wrapper functions created to be mockable in unit tests
     */
    protected IBinder getServiceBinderMockable() {
        synchronized (mLock) {
            if (mIHostapd == null) return null;
            return mIHostapd.asBinder();
        }
    }

    protected IHostapd getHostapdMockable() {
        synchronized (mLock) {
            {
                return IHostapd.Stub.asInterface(
                        ServiceManager.waitForDeclaredService(HAL_INSTANCE_NAME));
            }
        }
    }

    /**
     * Start hostapd daemon
     *
     * @return true when succeed, otherwise false.
     */
    @Override
    public boolean startDaemon() {
        synchronized (mLock) {
            final String methodStr = "startDaemon";
            mIHostapd = getHostapdMockable();
            if (mIHostapd == null) {
                Log.e(TAG, "Service hostapd wasn't found.");
                return false;
            }
            Log.i(TAG, "Obtained IHostApd binder.");
            Log.i(TAG, "Local Version: " + IHostapd.VERSION);

            try {
                Log.i(TAG, "Remote Version: " + mIHostapd.getInterfaceVersion());
                IBinder serviceBinder = getServiceBinderMockable();
                if (serviceBinder == null) return false;
                mWaitForDeathLatch = null;
                serviceBinder.linkToDeath(new HostapdDeathRecipient(serviceBinder), /* flags= */ 0);
                if (!setDebugParams()) return false;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
            if (!registerCallback(new HostapdCallback())) {
                Log.e(TAG, "Failed to register callback, stopping hostapd AIDL startup");
                mIHostapd = null;
                return false;
            }
            return true;
        }
    }

    /**
     * Terminate the hostapd daemon & wait for it's death.
     */
    @Override
    public void terminate() {
        synchronized (mLock) {
            final String methodStr = "terminate";
            if (!checkHostapdAndLogFailure(methodStr)) {
                return;
            }
            Log.i(TAG, "Terminate HostApd Service.");
            try {
                mWaitForDeathLatch = new CountDownLatch(1);
                mIHostapd.terminate();
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            }
        }

        // Now wait for death listener callback to confirm that it's dead.
        try {
            if (!mWaitForDeathLatch.await(WAIT_FOR_DEATH_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "Timed out waiting for confirmation of hostapd death");
            } else {
                Log.d(TAG, "Got service death confirmation");
            }
        } catch (InterruptedException e) {
            Log.w(TAG, "Failed to wait for hostapd death");
        }
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        synchronized (mLock) {
            hostapdServiceDiedHandler(getServiceBinderMockable());
            Log.e(TAG, "IHostapd." + methodStr + " failed with exception", e);
        }
    }

    /**
     * Set the debug log level for hostapd.
     *
     * @return true if request is sent successfully, false otherwise.
     */
    private boolean setDebugParams() {
        synchronized (mLock) {
            final String methodStr = "setDebugParams";
            if (!checkHostapdAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mIHostapd.setDebugParams(mVerboseHalLoggingEnabled
                        ? DebugLevel.DEBUG : DebugLevel.INFO);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    private static int getEncryptionType(SoftApConfiguration localConfig) {
        int encryptionType;
        switch (localConfig.getSecurityType()) {
            case SoftApConfiguration.SECURITY_TYPE_OPEN:
                encryptionType = EncryptionType.NONE;
                break;
            case SoftApConfiguration.SECURITY_TYPE_WPA2_PSK:
                encryptionType = EncryptionType.WPA2;
                break;
            case SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION:
                encryptionType = EncryptionType.WPA3_SAE_TRANSITION;
                break;
            case SoftApConfiguration.SECURITY_TYPE_WPA3_SAE:
                encryptionType = EncryptionType.WPA3_SAE;
                break;
            case SoftApConfiguration.SECURITY_TYPE_WPA3_OWE_TRANSITION:
                encryptionType = EncryptionType.WPA3_OWE_TRANSITION;
                break;
            case SoftApConfiguration.SECURITY_TYPE_WPA3_OWE:
                encryptionType = EncryptionType.WPA3_OWE;
                break;
            default:
                // We really shouldn't default to None, but this was how NetworkManagementService
                // used to do this.
                encryptionType = EncryptionType.NONE;
                break;
        }
        return encryptionType;
    }

    private static int getHalBandMask(int apBand) throws IllegalArgumentException {
        int bandMask = 0;

        if (!ApConfigUtil.isBandValid(apBand)) {
            throw new IllegalArgumentException();
        }

        if (ApConfigUtil.containsBand(apBand, SoftApConfiguration.BAND_2GHZ)) {
            bandMask |= BandMask.BAND_2_GHZ;
        }
        if (ApConfigUtil.containsBand(apBand, SoftApConfiguration.BAND_5GHZ)) {
            bandMask |= BandMask.BAND_5_GHZ;
        }
        if (ApConfigUtil.containsBand(apBand, SoftApConfiguration.BAND_6GHZ)) {
            bandMask |= BandMask.BAND_6_GHZ;
        }
        if (ApConfigUtil.containsBand(apBand, SoftApConfiguration.BAND_60GHZ)) {
            bandMask |= BandMask.BAND_60_GHZ;
        }

        return bandMask;
    }

   /**
     * Prepare the acsChannelFreqRangesMhz in ChannelParams.
     */
    private void prepareAcsChannelFreqRangesMhz(ChannelParams channelParams,
            int band, SoftApConfiguration config) {
        List<FrequencyRange> ranges = new ArrayList<>();
        if ((band & SoftApConfiguration.BAND_2GHZ) != 0) {
            ranges.addAll(toAcsFreqRanges(SoftApConfiguration.BAND_2GHZ, config));
        }
        if ((band & SoftApConfiguration.BAND_5GHZ) != 0) {
            ranges.addAll(toAcsFreqRanges(SoftApConfiguration.BAND_5GHZ, config));
        }
        if ((band & SoftApConfiguration.BAND_6GHZ) != 0) {
            ranges.addAll(toAcsFreqRanges(SoftApConfiguration.BAND_6GHZ, config));
        }
        channelParams.acsChannelFreqRangesMhz = ranges.toArray(
                new FrequencyRange[ranges.size()]);
    }

    /**
     * Convert OEM and SoftApConfiguration channel restrictions to a list of FreqRanges
     */
    private List<FrequencyRange> toAcsFreqRanges(int band, SoftApConfiguration config) {
        List<Integer> allowedChannelList;
        List<FrequencyRange> frequencyRanges = new ArrayList<>();

        if (!ApConfigUtil.isBandValid(band) || ApConfigUtil.isMultiband(band)) {
            Log.e(TAG, "Invalid band : " + band);
            return frequencyRanges;
        }

        String oemConfig;
        switch (band) {
            case SoftApConfiguration.BAND_2GHZ:
                oemConfig = null;
                break;
            case SoftApConfiguration.BAND_5GHZ:
                oemConfig = null;
                break;
            case SoftApConfiguration.BAND_6GHZ:
                oemConfig = null;
                break;
            default:
                return frequencyRanges;
        }
        allowedChannelList = ApConfigUtil.collectAllowedAcsChannels(band, oemConfig, new int[] {});
        if (allowedChannelList.isEmpty()) {
            Log.e(TAG, "Empty list of allowed channels");
            return frequencyRanges;
        }
        Collections.sort(allowedChannelList);

        // Convert the sorted list to a set of frequency ranges
        boolean rangeStarted = false;
        int prevChannel = -1;
        FrequencyRange freqRange = null;
        for (int channel : allowedChannelList) {
            // Continuation of an existing frequency range
            if (rangeStarted) {
                if (channel == prevChannel + 1) {
                    prevChannel = channel;
                    continue;
                }

                // End of the existing frequency range
                freqRange.endMhz = ApConfigUtil.convertChannelToFrequency(prevChannel, band);
                frequencyRanges.add(freqRange);
                // We will continue to start a new frequency range
            }

            // Beginning of a new frequency range
            freqRange = new FrequencyRange();
            freqRange.startMhz = ApConfigUtil.convertChannelToFrequency(channel, band);
            rangeStarted = true;
            prevChannel = channel;
        }

        // End the last range
        freqRange.endMhz = ApConfigUtil.convertChannelToFrequency(prevChannel, band);
        frequencyRanges.add(freqRange);

        return frequencyRanges;
    }

    /**
     * Map hal bandwidth to SoftApInfo.
     *
     * @param bandwidth The channel bandwidth of the AP which is defined in the HAL.
     * @return The channel bandwidth in the SoftApinfo.
     */
    public int mapHalChannelBandwidthToSoftApInfo(int channelBandwidth) {
        switch (channelBandwidth) {
            case ChannelBandwidth.BANDWIDTH_20_NOHT:
                return SoftApInfo.CHANNEL_WIDTH_20MHZ_NOHT;
            case ChannelBandwidth.BANDWIDTH_20:
                return SoftApInfo.CHANNEL_WIDTH_20MHZ;
            case ChannelBandwidth.BANDWIDTH_40:
                return SoftApInfo.CHANNEL_WIDTH_40MHZ;
            case ChannelBandwidth.BANDWIDTH_80:
                return SoftApInfo.CHANNEL_WIDTH_80MHZ;
            case ChannelBandwidth.BANDWIDTH_80P80:
                return SoftApInfo.CHANNEL_WIDTH_80MHZ_PLUS_MHZ;
            case ChannelBandwidth.BANDWIDTH_160:
                return SoftApInfo.CHANNEL_WIDTH_160MHZ;
            case ChannelBandwidth.BANDWIDTH_320:
                return SoftApInfo.CHANNEL_WIDTH_320MHZ;
            case ChannelBandwidth.BANDWIDTH_2160:
                return SoftApInfo.CHANNEL_WIDTH_2160MHZ;
            case ChannelBandwidth.BANDWIDTH_4320:
                return SoftApInfo.CHANNEL_WIDTH_4320MHZ;
            case ChannelBandwidth.BANDWIDTH_6480:
                return SoftApInfo.CHANNEL_WIDTH_6480MHZ;
            case ChannelBandwidth.BANDWIDTH_8640:
                return SoftApInfo.CHANNEL_WIDTH_8640MHZ;
            default:
                return SoftApInfo.CHANNEL_WIDTH_INVALID;
        }
    }

    /**
     * Map SoftApInfo bandwidth to hal.
     *
     * @param channelBandwidth The channel bandwidth as defined in SoftApInfo
     * @return The channel bandwidth as defined in hal
     */
    public int mapSoftApInfoBandwidthToHal(int channelBandwidth) {
        switch (channelBandwidth) {
            case SoftApInfo.CHANNEL_WIDTH_AUTO:
                return ChannelBandwidth.BANDWIDTH_AUTO;
            case SoftApInfo.CHANNEL_WIDTH_20MHZ_NOHT:
                return ChannelBandwidth.BANDWIDTH_20_NOHT;
            case SoftApInfo.CHANNEL_WIDTH_20MHZ:
                return ChannelBandwidth.BANDWIDTH_20;
            case SoftApInfo.CHANNEL_WIDTH_40MHZ:
                return ChannelBandwidth.BANDWIDTH_40;
            case SoftApInfo.CHANNEL_WIDTH_80MHZ:
                return ChannelBandwidth.BANDWIDTH_80;
            case SoftApInfo.CHANNEL_WIDTH_80MHZ_PLUS_MHZ:
                return ChannelBandwidth.BANDWIDTH_80P80;
            case SoftApInfo.CHANNEL_WIDTH_160MHZ:
                return ChannelBandwidth.BANDWIDTH_160;
            case SoftApInfo.CHANNEL_WIDTH_320MHZ:
                return ChannelBandwidth.BANDWIDTH_320;
            case SoftApInfo.CHANNEL_WIDTH_2160MHZ:
                return ChannelBandwidth.BANDWIDTH_2160;
            case SoftApInfo.CHANNEL_WIDTH_4320MHZ:
                return ChannelBandwidth.BANDWIDTH_4320;
            case SoftApInfo.CHANNEL_WIDTH_6480MHZ:
                return ChannelBandwidth.BANDWIDTH_6480;
            case SoftApInfo.CHANNEL_WIDTH_8640MHZ:
                return ChannelBandwidth.BANDWIDTH_8640;
            default:
                return ChannelBandwidth.BANDWIDTH_INVALID;
        }
    }

    /**
     * Map hal generation to wifi standard.
     *
     * @param generation The operation mode of the AP which is defined in HAL.
     * @return The wifi standard in the ScanResult.
     */
    public int mapHalGenerationToWifiStandard(int generation) {
        switch (generation) {
            case Generation.WIFI_STANDARD_LEGACY:
                return ApConfigUtil.WIFI_STANDARD_LEGACY;
            case Generation.WIFI_STANDARD_11N:
                return ApConfigUtil.WIFI_STANDARD_11N;
            case Generation.WIFI_STANDARD_11AC:
                return ApConfigUtil.WIFI_STANDARD_11AC;
            case Generation.WIFI_STANDARD_11AX:
                return ApConfigUtil.WIFI_STANDARD_11AX;
            case Generation.WIFI_STANDARD_11BE:
                return ApConfigUtil.WIFI_STANDARD_11BE;
            case Generation.WIFI_STANDARD_11AD:
                return ApConfigUtil.WIFI_STANDARD_11AD;
            default:
                return ApConfigUtil.WIFI_STANDARD_UNKNOWN;
        }
    }

    private NetworkParams prepareNetworkParams(boolean isMetered,
            SoftApConfiguration config) {
        NetworkParams nwParams = new NetworkParams();
        ArrayList<Byte> ssid = ApConfigUtil.byteArrayToArrayList(config.getWifiSsid().getBytes());
        nwParams.ssid = new byte[ssid.size()];
        for (int i = 0; i < ssid.size(); i++) {
            nwParams.ssid[i] = ssid.get(i);
        }

        nwParams.vendorElements = new byte[0];

        nwParams.isMetered = isMetered;
        nwParams.isHidden = config.isHiddenSsid();
        nwParams.encryptionType = getEncryptionType(config);
        nwParams.passphrase = (config.getPassphrase() != null)
                    ? config.getPassphrase() : "";

        if (nwParams.ssid == null || nwParams.passphrase == null) {
            return null;
        }
        return nwParams;
    }

    private IfaceParams prepareIfaceParams(String ifaceName, SoftApConfiguration config)
            throws IllegalArgumentException {
        IfaceParams ifaceParams = new IfaceParams();
        ifaceParams.name = ifaceName;
        ifaceParams.hwModeParams = prepareHwModeParams(config);
        ifaceParams.channelParams = prepareChannelParamsList(config);
        if (ifaceParams.name == null || ifaceParams.hwModeParams == null
                || ifaceParams.channelParams == null) {
            return null;
        }
        return ifaceParams;
    }

    private HwModeParams prepareHwModeParams(SoftApConfiguration config) {
        HwModeParams hwModeParams = new HwModeParams();
        //The target Wi-Fi chip is QCA6797,
        //then support both 11ax and 6GHz band.
        hwModeParams.enable80211N = true;
        hwModeParams.enable80211AC = true;
        hwModeParams.enable80211AX = true;
        hwModeParams.enable6GhzBand = true;
        hwModeParams.enableHeSingleUserBeamformer = true;
        hwModeParams.enableHeSingleUserBeamformee = true;
        hwModeParams.enableHeMultiUserBeamformer = true;
        hwModeParams.enableHeTargetWakeTime = true;
        hwModeParams.enable80211BE = ApConfigUtil.isIeee80211beSupported(mContext);

        hwModeParams.maximumChannelBandwidth =
                mapSoftApInfoBandwidthToHal(SoftApInfo.CHANNEL_WIDTH_AUTO);

        return hwModeParams;
    }

    private ChannelParams[] prepareChannelParamsList(SoftApConfiguration config)
            throws IllegalArgumentException {
        int nChannels = 1;
        boolean repeatBand = false;
        nChannels = config.getChannels().size();

        if (config.getSecurityType()
                == SoftApConfiguration.SECURITY_TYPE_WPA3_OWE_TRANSITION) {
            nChannels = 2;
            repeatBand = true;
        }
        ChannelParams[] channelParamsList = new ChannelParams[nChannels];
        for (int i = 0; i < nChannels; i++) {
            int band = config.getBand();
            int channel = config.getChannel();
            if (!repeatBand) {
                band = config.getChannels().keyAt(i);
                channel = config.getChannels().valueAt(i);
            }
            channelParamsList[i] = new ChannelParams();
            channelParamsList[i].channel = channel;
            channelParamsList[i].enableAcs = channel == 0;
            channelParamsList[i].bandMask = getHalBandMask(band);
            channelParamsList[i].acsChannelFreqRangesMhz = new FrequencyRange[0];
            if (channelParamsList[i].enableAcs) {
                //config_wifiSoftapAcsIncludeDfs is false
                channelParamsList[i].acsShouldExcludeDfs = true;
                //if (ApConfigUtil.isSendFreqRangesNeeded(band, mContext, config)) {
                prepareAcsChannelFreqRangesMhz(channelParamsList[i], band, config);
                //}
            }
            if (channelParamsList[i].acsChannelFreqRangesMhz == null) {
                return null;
            }
        }
        return channelParamsList;
    }

    /**
     * Returns false if Hostapd is null, and logs failure to call methodStr
     */
    private boolean checkHostapdAndLogFailure(String methodStr) {
        synchronized (mLock) {
            if (mIHostapd == null) {
                Log.e(TAG, "Can't call " + methodStr + ", IHostapd is null");
                return false;
            }
            return true;
        }
    }

    /**
     * Logs failure for a service specific exception. Error codes are defined in HostapdStatusCode
     */
    private void handleServiceSpecificException(
            ServiceSpecificException exception, String methodStr) {
        synchronized (mLock) {
            Log.e(TAG, "IHostapd." + methodStr + " failed: " + exception.toString());
        }
    }
}
