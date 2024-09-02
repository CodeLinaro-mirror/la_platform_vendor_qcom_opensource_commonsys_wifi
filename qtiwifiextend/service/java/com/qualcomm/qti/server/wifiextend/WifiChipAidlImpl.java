/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.content.Context;
import android.hardware.wifi.IWifiApIface;
import android.hardware.wifi.IWifiChip.ChannelCategoryMask;
import android.hardware.wifi.IWifiChip.CoexRestriction;
import android.hardware.wifi.IWifiChip.FeatureSetMask;
import android.hardware.wifi.IWifiChip.LatencyMode;
import android.hardware.wifi.IWifiChip.MultiStaUseCase;
import android.hardware.wifi.IWifiChip.TxPowerScenario;
import android.hardware.wifi.IWifiChip.UsableChannelFilter;
import android.hardware.wifi.IWifiChipEventCallback;
import android.hardware.wifi.IfaceConcurrencyType;
import android.hardware.wifi.IfaceType;
import android.hardware.wifi.WifiAntennaMode;
import android.hardware.wifi.WifiBand;
import android.hardware.wifi.WifiChipCapabilities;
import android.hardware.wifi.WifiDebugHostWakeReasonStats;
import android.hardware.wifi.WifiDebugRingBufferFlags;
import android.hardware.wifi.WifiDebugRingBufferStatus;
import android.hardware.wifi.WifiIfaceMode;
import android.hardware.wifi.WifiRadioCombination;
import android.hardware.wifi.WifiRadioConfiguration;
import android.hardware.wifi.WifiStatusCode;
import android.hardware.wifi.WifiUsableChannel;

import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.Log;
//import com.android.modules.utils.build.SdkLevel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.qualcomm.qti.wifiextend.CoexUnsafeChannel;
import com.qualcomm.qti.wifiextend.QtiWifiExtendManager;
import com.qualcomm.qti.server.wifiextend.WifiChip.IWifiChip;
import com.qualcomm.qti.server.wifiextend.WifiChip.WifiAvailableChannel;


public class WifiChipAidlImpl implements IWifiChip {
    private static final String TAG = "ExtendWifiChipAidlImpl";
    private android.hardware.wifi.IWifiChip mWifiChip;
    private android.hardware.wifi.IWifiChipEventCallback mHalCallback;
    private WifiChip.Callback mFrameworkCallback;
    private final Object mLock = new Object();
    private Context mContext;

    public static final int WIFI_BAND_INDEX_24_GHZ = 0;
    /** @hide */
    public static final int WIFI_BAND_INDEX_5_GHZ = 1;
    /** @hide */
    public static final int WIFI_BAND_INDEX_5_GHZ_DFS_ONLY = 2;
    /** @hide */
    public static final int WIFI_BAND_INDEX_6_GHZ = 3;
    /** @hide */
    public static final int WIFI_BAND_INDEX_60_GHZ = 4;

    /** no band specified; use channel list instead */
    public static final int WIFI_BAND_UNSPECIFIED = 0;
    public static final int WIFI_BAND_24_GHZ = 1 << WIFI_BAND_INDEX_24_GHZ;
    /** 5 GHz band excluding DFS channels */
    public static final int WIFI_BAND_5_GHZ = 1 << WIFI_BAND_INDEX_5_GHZ;
    /** DFS channels from 5 GHz band only */
    public static final int WIFI_BAND_5_GHZ_DFS_ONLY  = 1 << WIFI_BAND_INDEX_5_GHZ_DFS_ONLY;
    /** 6 GHz band */
    public static final int WIFI_BAND_6_GHZ = 1 << WIFI_BAND_INDEX_6_GHZ;
    /** 60 GHz band */
    public static final int WIFI_BAND_60_GHZ = 1 << WIFI_BAND_INDEX_60_GHZ;
    /** Both 2.4 GHz band and 5 GHz band; no DFS channels */
    public static final int WIFI_BAND_BOTH = WIFI_BAND_24_GHZ | WIFI_BAND_5_GHZ;
    /**
     * 2.4Ghz band + DFS channels from 5 GHz band only
     * @hide
     */
    public static final int WIFI_BAND_24_GHZ_WITH_5GHZ_DFS  =
            WIFI_BAND_24_GHZ | WIFI_BAND_5_GHZ_DFS_ONLY;
    /** 5 GHz band including DFS channels */
    public static final int WIFI_BAND_5_GHZ_WITH_DFS  = WIFI_BAND_5_GHZ | WIFI_BAND_5_GHZ_DFS_ONLY;
    /** Both 2.4 GHz band and 5 GHz band; with DFS channels */
    public static final int WIFI_BAND_BOTH_WITH_DFS =
            WIFI_BAND_24_GHZ | WIFI_BAND_5_GHZ | WIFI_BAND_5_GHZ_DFS_ONLY;
    /** 2.4 GHz band and 5 GHz band (no DFS channels) and 6 GHz */
    public static final int WIFI_BAND_24_5_6_GHZ = WIFI_BAND_BOTH | WIFI_BAND_6_GHZ;
    /** 2.4 GHz band and 5 GHz band; with DFS channels and 6 GHz */
    public static final int WIFI_BAND_24_5_WITH_DFS_6_GHZ =
            WIFI_BAND_BOTH_WITH_DFS | WIFI_BAND_6_GHZ;
    /** @hide */
    public static final int WIFI_BAND_24_5_6_60_GHZ =
            WIFI_BAND_24_5_6_GHZ | WIFI_BAND_60_GHZ;
    /** @hide */
    public static final int WIFI_BAND_24_5_WITH_DFS_6_60_GHZ =
            WIFI_BAND_24_5_6_60_GHZ | WIFI_BAND_5_GHZ_DFS_ONLY;

    public WifiChipAidlImpl(android.hardware.wifi.IWifiChip chip, Context context) {
        mWifiChip = chip;
        mContext = context;
    }

    /**
     * See comments for {@link IWifiChip#configureChip(int)}
     */
    @Override
    public boolean configureChip(int modeId) {
        final String methodStr = "configureChip";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiChip.configureChip(modeId);
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
     * See comments for {@link IWifiChip#createApIface()}
     */
    @Override
    public WifiApIface createApIface() {
        final String methodStr = "createApIface";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                IWifiApIface iface = mWifiChip.createApIface();
                return new WifiApIface(iface);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#createBridgedApIface()}
     */
    @Override
    public WifiApIface createBridgedApIface() {
        final String methodStr = "createBridgedApIface";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                IWifiApIface iface = mWifiChip.createBridgedApIface();
                return new WifiApIface(iface);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#getApIface(String)}
     */
    @Override
    public WifiApIface getApIface(String ifaceName) {
        final String methodStr = "getApIface";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                IWifiApIface iface = mWifiChip.getApIface(ifaceName);
                return new WifiApIface(iface);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#getApIfaceNames()}
     */
    @Override
    public List<String> getApIfaceNames() {
        final String methodStr = "getApIfaceNames";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                String[] ifaceNames = mWifiChip.getApIfaceNames();
                return Arrays.asList(ifaceNames);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#getId()}
     */
    @Override
    public int getId() {
        final String methodStr = "getId";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return -1;
                return mWifiChip.getId();
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return -1;
        }
    }


    /**
     * See comments for {@link IWifiChip#getUsableChannels(int, int, int)}
     */
    @Override
    public int[] getUsableChannels(int band, int mode, int filter) {
        final String methodStr = "getUsableChannels";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return null;
                WifiUsableChannel[] halChannels = mWifiChip.getUsableChannels(
                        frameworkToHalWifiBand(band),
                        frameworkToHalIfaceMode(mode),
                        frameworkToHalUsableFilter(filter));
                int[] frameworkChannels = new int[halChannels.length];
                for (int i = 0; i < halChannels.length; i++) {
                     frameworkChannels[i] = halChannels[i].channel;
                }
                return frameworkChannels;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            } catch (IllegalArgumentException e) {
                handleIllegalArgumentException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiChip#registerCallback(WifiChip.Callback)}
     */
    @Override
    public boolean registerCallback(WifiChip.Callback callback) {
        final String methodStr = "registerCallback";
        synchronized (mLock) {
            if (!checkIfaceAndLogFailure(methodStr)) return false;
            if (mFrameworkCallback != null) {
                Log.e(TAG, "Framework callback is already registered");
                return false;
            } else if (callback == null) {
                Log.e(TAG, "Cannot register a null callback");
                return false;
            }

            try {
                mHalCallback = new ChipEventCallback();
                mWifiChip.registerEventCallback(mHalCallback);
                mFrameworkCallback = callback;
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
     * See comments for {@link IWifiChip#removeApIface(String)}
     */
    @Override
    public boolean removeApIface(String ifaceName) {
        final String methodStr = "removeApIface";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiChip.removeApIface(ifaceName);
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
     * See comments for {@link IWifiChip#removeIfaceInstanceFromBridgedApIface(String, String)}
     */
    @Override
    public boolean removeIfaceInstanceFromBridgedApIface(String brIfaceName, String ifaceName) {
        final String methodStr = "removeIfaceInstanceFromBridgedApIface";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiChip.removeIfaceInstanceFromBridgedApIface(brIfaceName, ifaceName);
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
     * See comments for {@link IWifiChip#setCoexUnsafeChannels(List, int)}
     */
    @Override
    public boolean setCoexUnsafeChannels(List<CoexUnsafeChannel> unsafeChannels, int restrictions) {
        final String methodStr = "setCoexUnsafeChannels";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                android.hardware.wifi.IWifiChip.CoexUnsafeChannel[] halChannels =
                        frameworkToHalCoexUnsafeChannels(unsafeChannels);
                int halRestrictions = frameworkToHalCoexRestrictions(restrictions);
                mWifiChip.setCoexUnsafeChannels(halChannels, halRestrictions);
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
     * See comments for {@link IWifiChip#setCountryCode(byte[])}
     */
    @Override
    public boolean setCountryCode(byte[] code) {
        final String methodStr = "setCountryCode";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiChip.setCountryCode(code);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    private class ChipEventCallback extends IWifiChipEventCallback.Stub {

        @Override
        public void onChipReconfigured(int modeId) throws RemoteException {
            if (mFrameworkCallback == null) return;
            mFrameworkCallback.onChipReconfigured(modeId);
        }

        @Override
        public void onChipReconfigureFailure(int statusCode) {
            if (mFrameworkCallback == null) return;
            // TODO: convert to framework status code once WifiHalAidlImpl exists
            mFrameworkCallback.onChipReconfigureFailure(statusCode);
        }

        @Override
        public void onIfaceAdded(int type, String name) {
            if (mFrameworkCallback == null) return;
            mFrameworkCallback.onIfaceAdded(halToFrameworkIfaceType(type), name);
        }

        @Override
        public void onIfaceRemoved(int type, String name) {
            if (mFrameworkCallback == null) return;
            mFrameworkCallback.onIfaceRemoved(halToFrameworkIfaceType(type), name);
        }

        @Override
        public void onDebugRingBufferDataAvailable(WifiDebugRingBufferStatus status, byte[] data) {
			if (mFrameworkCallback == null) return;
            mFrameworkCallback.onDebugRingBufferDataAvailable();
        }

        @Override
        public void onDebugErrorAlert(int errorCode, byte[] debugData) {
            if (mFrameworkCallback == null) return;
            mFrameworkCallback.onDebugErrorAlert(errorCode, debugData);
        }

        @Override
        public void onRadioModeChange(RadioModeInfo[] radioModeInfoList) {
			if (mFrameworkCallback == null) return;
            mFrameworkCallback.onRadioModeChange();
        }

        @Override
        public String getInterfaceHash() {
            return IWifiChipEventCallback.HASH;
        }

        @Override
        public int getInterfaceVersion() {
            return IWifiChipEventCallback.VERSION;
        }
    }

    private static int halToFrameworkIfaceType(int type) {
        switch (type) {
            case IfaceType.STA:
                return WifiChip.IFACE_TYPE_STA;
            case IfaceType.AP:
                return WifiChip.IFACE_TYPE_AP;
            case IfaceType.P2P:
                return WifiChip.IFACE_TYPE_P2P;
            case IfaceType.NAN_IFACE:
                return WifiChip.IFACE_TYPE_NAN;
            default:
                Log.e(TAG, "Invalid IfaceType received: " + type);
                return -1;
        }
    }

    private static android.hardware.wifi.IWifiChip.CoexUnsafeChannel[]
            frameworkToHalCoexUnsafeChannels(
            List<com.qualcomm.qti.wifiextend.CoexUnsafeChannel> frameworkUnsafeChannels) {
        final ArrayList<android.hardware.wifi.IWifiChip.CoexUnsafeChannel> halList =
                new ArrayList<>();
        for (com.qualcomm.qti.wifiextend.CoexUnsafeChannel frameworkUnsafeChannel : frameworkUnsafeChannels) {
            final android.hardware.wifi.IWifiChip.CoexUnsafeChannel halUnsafeChannel =
                    new android.hardware.wifi.IWifiChip.CoexUnsafeChannel();
            switch (frameworkUnsafeChannel.getBand()) {
                case (WIFI_BAND_24_GHZ):
                    halUnsafeChannel.band = WifiBand.BAND_24GHZ;
                    break;
                case (WIFI_BAND_5_GHZ):
                    halUnsafeChannel.band = WifiBand.BAND_5GHZ;
                    break;
                case (WIFI_BAND_6_GHZ):
                    halUnsafeChannel.band = WifiBand.BAND_6GHZ;
                    break;
                case (WIFI_BAND_60_GHZ):
                    halUnsafeChannel.band = WifiBand.BAND_60GHZ;
                    break;
                default:
                    Log.e(TAG, "Tried to set unsafe channel with unknown band: "
                            + frameworkUnsafeChannel.getBand());
                    continue;
            }
            halUnsafeChannel.channel = frameworkUnsafeChannel.getChannel();
            final int powerCapDbm = frameworkUnsafeChannel.getPowerCapDbm();
            if (powerCapDbm != CoexUnsafeChannel.POWER_CAP_NONE) {
                halUnsafeChannel.powerCapDbm = powerCapDbm;
            } else {
                halUnsafeChannel.powerCapDbm =
                        android.hardware.wifi.IWifiChip.NO_POWER_CAP_CONSTANT;
            }
            halList.add(halUnsafeChannel);
        }

        android.hardware.wifi.IWifiChip.CoexUnsafeChannel[] halArray =
                new android.hardware.wifi.IWifiChip.CoexUnsafeChannel[halList.size()];
        for (int i = 0; i < halList.size(); i++) {
            halArray[i] = halList.get(i);
        }
        return halArray;
    }

    private static int frameworkToHalCoexRestrictions(
            int restrictions) {
        int halRestrictions = 0;
        if ((restrictions & QtiWifiExtendManager.COEX_RESTRICTION_WIFI_DIRECT) != 0) {
            halRestrictions |= CoexRestriction.WIFI_DIRECT;
        }
        if ((restrictions & QtiWifiExtendManager.COEX_RESTRICTION_SOFTAP) != 0) {
            halRestrictions |= CoexRestriction.SOFTAP;
        }
        if ((restrictions & QtiWifiExtendManager.COEX_RESTRICTION_WIFI_AWARE) != 0) {
            halRestrictions |= CoexRestriction.WIFI_AWARE;
        }
        return halRestrictions;
    }

    private static int frameworkToHalWifiBand(int frameworkBand) throws IllegalArgumentException {
        switch (frameworkBand) {
            case WIFI_BAND_UNSPECIFIED:
                return WifiBand.BAND_UNSPECIFIED;
            case WIFI_BAND_24_GHZ:
                return WifiBand.BAND_24GHZ;
            case WIFI_BAND_5_GHZ:
                return WifiBand.BAND_5GHZ;
            case WIFI_BAND_5_GHZ_DFS_ONLY:
                return WifiBand.BAND_5GHZ_DFS;
            case WIFI_BAND_5_GHZ_WITH_DFS:
                return WifiBand.BAND_5GHZ_WITH_DFS;
            case WIFI_BAND_BOTH:
                return WifiBand.BAND_24GHZ_5GHZ;
            case WIFI_BAND_BOTH_WITH_DFS:
                return WifiBand.BAND_24GHZ_5GHZ_WITH_DFS;
            case WIFI_BAND_6_GHZ:
                return WifiBand.BAND_6GHZ;
            case WIFI_BAND_24_5_6_GHZ:
                return WifiBand.BAND_24GHZ_5GHZ_6GHZ;
            case WIFI_BAND_24_5_WITH_DFS_6_GHZ:
                return WifiBand.BAND_24GHZ_5GHZ_WITH_DFS_6GHZ;
            case WIFI_BAND_60_GHZ:
                return WifiBand.BAND_60GHZ;
            case WIFI_BAND_24_5_6_60_GHZ:
                return WifiBand.BAND_24GHZ_5GHZ_6GHZ_60GHZ;
            case WIFI_BAND_24_5_WITH_DFS_6_60_GHZ:
                return WifiBand.BAND_24GHZ_5GHZ_WITH_DFS_6GHZ_60GHZ;
            case WIFI_BAND_24_GHZ_WITH_5GHZ_DFS:
            default:
                throw new IllegalArgumentException("bad band " + frameworkBand);
        }
    }

    private static int frameworkToHalIfaceMode(int mode) {
        int halMode = 0;
        if ((mode & WifiAvailableChannel.OP_MODE_STA) != 0) {
            halMode |= WifiIfaceMode.IFACE_MODE_STA;
        }
        if ((mode & WifiAvailableChannel.OP_MODE_SAP) != 0) {
            halMode |= WifiIfaceMode.IFACE_MODE_SOFTAP;
        }
        if ((mode & WifiAvailableChannel.OP_MODE_WIFI_DIRECT_CLI) != 0) {
            halMode |= WifiIfaceMode.IFACE_MODE_P2P_CLIENT;
        }
        if ((mode & WifiAvailableChannel.OP_MODE_WIFI_DIRECT_GO) != 0) {
            halMode |= WifiIfaceMode.IFACE_MODE_P2P_GO;
        }
        if ((mode & WifiAvailableChannel.OP_MODE_WIFI_AWARE) != 0) {
            halMode |= WifiIfaceMode.IFACE_MODE_NAN;
        }
        if ((mode & WifiAvailableChannel.OP_MODE_TDLS) != 0) {
            halMode |= WifiIfaceMode.IFACE_MODE_TDLS;
        }
        return halMode;
    }

    private static int halToFrameworkIfaceMode(int halMode) {
        int mode = 0;
        if ((halMode & WifiIfaceMode.IFACE_MODE_STA) != 0) {
            mode |= WifiAvailableChannel.OP_MODE_STA;
        }
        if ((halMode & WifiIfaceMode.IFACE_MODE_SOFTAP) != 0) {
            mode |= WifiAvailableChannel.OP_MODE_SAP;
        }
        if ((halMode & WifiIfaceMode.IFACE_MODE_P2P_CLIENT) != 0) {
            mode |= WifiAvailableChannel.OP_MODE_WIFI_DIRECT_CLI;
        }
        if ((halMode & WifiIfaceMode.IFACE_MODE_P2P_GO) != 0) {
            mode |= WifiAvailableChannel.OP_MODE_WIFI_DIRECT_GO;
        }
        if ((halMode & WifiIfaceMode.IFACE_MODE_NAN) != 0) {
            mode |= WifiAvailableChannel.OP_MODE_WIFI_AWARE;
        }
        if ((halMode & WifiIfaceMode.IFACE_MODE_TDLS) != 0) {
            mode |= WifiAvailableChannel.OP_MODE_TDLS;
        }
        return mode;
    }

    private static int frameworkToHalUsableFilter(int filter) {
        int halFilter = 0;  // O implies no additional filter other than regulatory (default)
        if ((filter & WifiAvailableChannel.FILTER_CONCURRENCY) != 0) {
            halFilter |= UsableChannelFilter.CONCURRENCY;
        }
        if ((filter & WifiAvailableChannel.FILTER_CELLULAR_COEXISTENCE) != 0) {
            halFilter |= UsableChannelFilter.CELLULAR_COEXISTENCE;
        }
        if ((filter & WifiAvailableChannel.FILTER_NAN_INSTANT_MODE) != 0) {
            halFilter |= UsableChannelFilter.NAN_INSTANT_MODE;
        }

        return halFilter;
    }

    private boolean checkIfaceAndLogFailure(String methodStr) {
        if (mWifiChip == null) {
            Log.e(TAG, "Unable to call " + methodStr + " because iface is null.");
            return false;
        }
        return true;
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        mWifiChip = null;
        Log.e(TAG, methodStr + " failed with remote exception: " + e);
    }

    private void handleServiceSpecificException(ServiceSpecificException e, String methodStr) {
        Log.e(TAG, methodStr + " failed with service-specific exception: " + e);
    }

    private void handleIllegalArgumentException(IllegalArgumentException e, String methodStr) {
        Log.e(TAG, methodStr + " failed with illegal argument exception: " + e);
    }

}
