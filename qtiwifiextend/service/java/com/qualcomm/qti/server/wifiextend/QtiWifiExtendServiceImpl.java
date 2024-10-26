/* Copyright (c) 2021-2022, 2024 Qualcomm Innovation Center, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted (subject to the limitations in the
 * disclaimer below) provided that the following conditions are met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *
 *   * Neither the name of Qualcomm Innovation Center nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE
 * GRANTED BY THIS LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT
 * HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.qualcomm.qti.server.wifiextend;

import android.annotation.NonNull;
import android.content.Context;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemProperties;
import android.os.RemoteException;
import android.os.RemoteCallbackList;
import android.os.WorkSource;

import android.util.LocalLog;
import android.util.Log;

import android.net.wifi.WifiManager;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import com.android.internal.util.RingBuffer;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.Date;
import java.text.SimpleDateFormat;

import com.qualcomm.qti.wifiextend.WifiClient;
import com.qualcomm.qti.wifiextend.ThermalData;
import com.qualcomm.qti.wifiextend.IQtiWifiExtendManager;
import com.qualcomm.qti.wifiextend.CoexUnsafeChannel;
import com.qualcomm.qti.wifiextend.IVendorEventCallback;
import com.qualcomm.qti.wifiextend.IExtendSoftApCallback;
import com.qualcomm.qti.wifiextend.SoftApConfiguration;
import com.qualcomm.qti.wifiextend.SoftApInfo;
import com.qualcomm.qti.wifiextend.QtiWifiExtendManager;
import vendor.qti.hardware.wifi.qtiwifi.IfaceInfo;

public final class QtiWifiExtendServiceImpl extends IQtiWifiExtendManager.Stub {
    private static final String TAG = "QtiWifiExtendServiceImpl";
    private boolean mServiceStarted = false;

    private final Context mContext;
    private Object mLock = new Object();

    private QtiWifiExtendInjector mWifiInjector;
    private HandlerThread mHandlerThread;
    private QtiWifiExtendThreadRunner mQtiWifiThreadRunner;

    WifiNative mWifiNative;

    SoftApConfigStore mSoftApConfigStore;
    WifiCountryCode mWifiCountryCode;
    ActiveModeWarden mActiveModeWarden;
    private final WifiCountryCode mCountryCode;

    private SoftApTracker mSoftApTracker;

    /* Extend SoftAp callbacks */
    private final RemoteCallbackList<IExtendSoftApCallback> mExtendSoftApCallbacks;
    private final HashMap<Integer, IExtendSoftApCallback> mExtendSoftApCallbacksMap = new HashMap<>();


    public QtiWifiExtendServiceImpl(Context context,  QtiWifiExtendInjector wifiInjector) {
        Log.d(TAG, "QtiWifiExtendServiceImpl ctor");
        mContext = context;
        mWifiInjector = wifiInjector;

        mExtendSoftApCallbacks = new RemoteCallbackList<>();

        mHandlerThread = mWifiInjector.getWifiHandlerThread();
        mQtiWifiThreadRunner = mWifiInjector.getWifiRunner();

        mSoftApConfigStore = mWifiInjector.getSoftApConfigStore();
        mWifiCountryCode = mWifiInjector.getWifiCountryCode();
        mActiveModeWarden = mWifiInjector.getActiveModeWarden();

        //invoke WifiNative.initialize() in ActiveModeWarden.start();
        mActiveModeWarden.start();
        mSoftApTracker = new SoftApTracker();
        mActiveModeWarden.registerSoftApListener(mSoftApTracker);
        mCountryCode = mWifiInjector.getWifiCountryCode();

        mWifiNative = mWifiInjector.getWifiNative();

    }

    protected void destroyService() {
        Log.d(TAG, "destroyService()");
        mHandlerThread.quit();
        mServiceStarted = false;
    }

    private boolean startSoftApInternal(SoftApModeConfiguration apConfig,
        WorkSource requestorWs) {
        SoftApConfiguration softApConfig = apConfig.getSoftApConfiguration();
        if (softApConfig != null
                && (!SoftApConfigStore.validateApWifiConfiguration(softApConfig))) {
            Log.e(TAG, "Invalid SoftApConfiguration");
            return false;
        }

        Log.d(TAG, "startSoftApInternal" + apConfig);
        mSoftApConfigStore.setSoftApConfiguration(softApConfig);
        mActiveModeWarden.startSoftAp(apConfig, requestorWs);
        return true;
    }

    public boolean startSoftAp(SoftApConfiguration config) {
        int callingUid = Binder.getCallingUid();
        String packageName = "OEM service";
        WorkSource requestorWs = new WorkSource(callingUid, packageName);

        if(!startSoftApInternal(new SoftApModeConfiguration(config,
            mCountryCode.getCountryCode()), requestorWs)) {
                mSoftApTracker.setFailedWhileEnabling();
                return false;
        }

        return true;
    }

    public boolean stopSoftAp() {
        Log.d(TAG, "stopSoftAp");
        mActiveModeWarden.stopSoftAp();
        return true;
    }

    public SoftApConfiguration getSoftApConfiguration() {
        return mSoftApConfigStore.getSoftApConfiguration();
    }

    public int getWifiApState() {
        return mSoftApTracker.getState();
    }

    public boolean setSoftApConfiguration(SoftApConfiguration config) {
        if (config == null)
            return false;
        if (SoftApConfigStore.validateApWifiConfiguration(config)) {
            mSoftApConfigStore.setSoftApConfiguration(config);
            mActiveModeWarden.updateSoftApConfiguration(config);
            return true;
        } else {
            Log.e(TAG, "Invalid SoftAp Configuration");
            return false;
        }
    }

    public void setDefaultCountryCode(String countryCode) {
        mWifiCountryCode.setDefaultCountryCodeFromUser(countryCode);
    }

    public String[] listHostapdVendorInterfaces() {
        return mWifiNative.listHostapdVendorInterfaces();
    }

    public String doHostapdCtrlIfaceCmd(String ifname, String command) {
        return mWifiNative.doHostapdCtrlIfaceCmd(ifname, command);
    }

    public ThermalData getThermalInfo(String ifname) {
		return mWifiNative.getThermalInfo(ifname);
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
        return mWifiNative.setTxPower(ifname, dbm);
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
        return mWifiNative.setAni(ifname, mode, ofdmlvl);
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
        return mWifiNative.setCongestionReport(ifname, enable, thre, inter);
    }

    public String getClientIpAddress(WifiClient client) {
        return mWifiNative.getClientIpAddress(client);
    }

    public boolean setDataSharing(String ifname, boolean enable) {
        return mWifiNative.setDataSharing(ifname, enable);
    }

    public void registerVendorEventCallback(IVendorEventCallback callback,
            int callbackIdentifier) {
        mWifiNative.registerVendorEventCallback(callback, callbackIdentifier);
    }

    public void unregisterVendorEventCallback(int callbackIdentifier) {
        mWifiNative.unregisterVendorEventCallback(callbackIdentifier);
    }

    static boolean isValidBandForGetUsableChannels(int band) {
        switch (band) {
            case WifiChipAidlImpl.WIFI_BAND_UNSPECIFIED:
            case WifiChipAidlImpl.WIFI_BAND_24_GHZ:
            case WifiChipAidlImpl.WIFI_BAND_5_GHZ_WITH_DFS:
            case WifiChipAidlImpl.WIFI_BAND_BOTH_WITH_DFS:
            case WifiChipAidlImpl.WIFI_BAND_6_GHZ:
            case WifiChipAidlImpl.WIFI_BAND_24_5_WITH_DFS_6_GHZ:
            case WifiChipAidlImpl.WIFI_BAND_60_GHZ:
            case WifiChipAidlImpl.WIFI_BAND_24_5_WITH_DFS_6_60_GHZ:
                return true;
            default:
                return false;
        }
    }

    public int[] getUsableChannels(int band) {
        int[] channels = new int[] {};
        if (!isValidBandForGetUsableChannels(band)) {
            Log.e(TAG, "Unsupported band: " + band);
            return channels;
        }
        channels = mQtiWifiThreadRunner.call(
            () -> mWifiNative.getUsableChannels(band), null);
        if (channels == null) {
            Log.e(TAG, "no usable channeles");
        }
        return channels;
    }

    public void setCoexUnsafeChannels(@NonNull List<CoexUnsafeChannel> unsafeChannels) {
        mQtiWifiThreadRunner.run(
        () -> mWifiNative.setCoexUnsafeChannels(unsafeChannels));
    }

    public void registerExtendSoftApCallback(IExtendSoftApCallback callback,
        int callbackIdentifier) {
        // verify arguments
        if (callback == null) {
            throw new IllegalArgumentException("Callback must not be null");
        }
        enforceAccessPermission();
        Log.i(TAG, "registerExtendSoftApCallback uid=%" + Binder.getCallingUid());
        synchronized (mExtendSoftApCallbacks) {
            mExtendSoftApCallbacks.register(callback);
            mExtendSoftApCallbacksMap.put(callbackIdentifier, callback);
        }
    }

    public void unregisterExtendSoftApCallback(int callbackIdentifier) {
        Log.i(TAG, "unregisterExtendSoftApCallback uid=%" + Binder.getCallingUid());
        enforceAccessPermission();
        synchronized (mExtendSoftApCallbacks) {
            IExtendSoftApCallback callback = mExtendSoftApCallbacksMap.get(callbackIdentifier);
            if (callback == null) {
                Log.d(TAG, "no such registered callback found, id=" + callbackIdentifier);
                return;
            }
            mExtendSoftApCallbacks.unregister(callback);
            mExtendSoftApCallbacksMap.remove(callbackIdentifier);
        }
    }

    public interface SoftApListener {
        void onStateChanged(int state, int failureReason);
        void onConnectedClientsChanged(WifiClient client, boolean isConnected,
                                        int disconectReason);
        void onInfoChanged(List<SoftApInfo> softApInfoList);
    }

    private class SoftApTracker implements SoftApListener {
        private final Object mLock = new Object();
        private int mSoftApState = QtiWifiExtendManager.WIFI_AP_STATE_DISABLED;
        private WifiClient mWifiClient = null;
        private List<SoftApInfo> mSoftApInfoList = new ArrayList<>();

        public int getState() {
            synchronized (mLock) {
                return mSoftApState;
            }
        }

        public void setState(int state) {
            synchronized (mLock) {
                mSoftApState = state;
            }
        }

        public boolean setEnablingIfAllowed() {
            synchronized (mLock) {
                if (mSoftApState != QtiWifiExtendManager.WIFI_AP_STATE_DISABLED
                        && mSoftApState != QtiWifiExtendManager.WIFI_AP_STATE_FAILED) {
                    return false;
                }
                mSoftApState = QtiWifiExtendManager.WIFI_AP_STATE_ENABLING;
                return true;
            }
        }

        public void setFailedWhileEnabling() {
            synchronized (mLock) {
                if (mSoftApState == QtiWifiExtendManager.WIFI_AP_STATE_ENABLING) {
                    mSoftApState = QtiWifiExtendManager.WIFI_AP_STATE_FAILED;
                }
            }
        }

        public List<SoftApInfo> getSoftApInfos() {
            synchronized (mLock) {
                return mSoftApInfoList;
            }
        }

        @Override
        public void onStateChanged(int state, int failureReason) {
            synchronized (mLock) {
                mSoftApState = state;
            }
            //notifyRegisterOnStateChanged(mRegisteredSoftApCallbacks, state, failureReason);
            int itemCount = mExtendSoftApCallbacks.beginBroadcast();
            for (int i = 0; i < itemCount; i++) {
                try {
                    mExtendSoftApCallbacks.getBroadcastItem(i).onStateChanged(state,
                                failureReason);
                } catch (RemoteException e) {
                    Log.e(TAG, "onStateChanged: remote exception -- " + e);
                }
            }
            mExtendSoftApCallbacks.finishBroadcast();
        }

	@Override
        public void onConnectedClientsChanged(WifiClient client, boolean isConnected,
            int disconectReason) {
            synchronized (mLock) {
                mWifiClient = new WifiClient(client.getMacAddress(), client.getApInstanceIdentifier());
            }
            int itemCount = mExtendSoftApCallbacks.beginBroadcast();
            for (int i = 0; i < itemCount; i++) {
                try {
                    mExtendSoftApCallbacks.getBroadcastItem(i).onConnectedClientsChanged(mWifiClient,
                        isConnected, disconectReason);
                } catch (RemoteException e) {
                    Log.e(TAG, "onConnectedClientsChanged: remote exception -- " + e);
                }
            }
            mExtendSoftApCallbacks.finishBroadcast();
        }

        @Override
        public void onInfoChanged(List<SoftApInfo> softApInfoList) {
            synchronized (mLock) {
                mSoftApInfoList = deepCopySoftApInfo(softApInfoList);
            }
            int itemCount = mExtendSoftApCallbacks.beginBroadcast();
            for (int i = 0; i < itemCount; i++) {
                try {
                    mExtendSoftApCallbacks.getBroadcastItem(i).onInfoChanged(mSoftApInfoList);
                } catch (RemoteException e) {
                    Log.e(TAG, "onInfoChanged: remote exception -- " + e);
                }
            }
            mExtendSoftApCallbacks.finishBroadcast();
        }
    }

    private List<SoftApInfo> deepCopySoftApInfo(List<SoftApInfo> softApInfoList) {
        List<SoftApInfo> deepCopyList = new ArrayList<>();
        for (SoftApInfo info : softApInfoList) {
            deepCopyList.add(new SoftApInfo(info));
        }
        return deepCopyList;
    }

    private void enforceAccessPermission() {
        mContext.enforceCallingOrSelfPermission(
            android.Manifest.permission.ACCESS_WIFI_STATE, TAG);
    }

}
