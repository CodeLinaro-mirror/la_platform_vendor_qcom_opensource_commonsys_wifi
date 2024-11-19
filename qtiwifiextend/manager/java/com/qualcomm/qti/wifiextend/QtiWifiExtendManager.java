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
package com.qualcomm.qti.wifiextend;

import android.content.Context;
import android.os.Handler;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.content.Intent;
import java.util.List;

import com.qualcomm.qti.wifiextend.ThermalData;
import com.qualcomm.qti.wifiextend.CoexUnsafeChannel;
import com.qualcomm.qti.wifiextend.WifiClient;
import com.qualcomm.qti.wifiextend.SoftApInfo;
import com.qualcomm.qti.wifiextend.SoftApConfiguration;
import com.qualcomm.qti.wifiextend.IQtiWifiExtendManager;
import com.qualcomm.qti.wifiextend.IExtendSoftApCallback;
import com.qualcomm.qti.wifiextend.IVendorEventCallback;

public class QtiWifiExtendManager {
    private static final String TAG = "QtiWifiExtendManager";
    private static ApplicationBinderCallback mApplicationCallback = null;
    private static Context mContext;
    private static boolean mServiceAlreadyBound = false;
    private static IQtiWifiExtendManager mUniqueInstance = null;
    IQtiWifiExtendManager mService;

    public static final String WIFI_AP_STATE_CHANGED_ACTION =
        "com.qualcomm.qti.server.wifiextend.WIFI_AP_STATE_CHANGED";

    public static final String WIFI_AP_CLIENTS_CHANGED_ACTION =
        "com.qualcomm.qti.server.wifiextend.WIFI_AP_CLIENTS_CHANGED";

    public static final String EXTRA_WIFI_AP_STATE = "wifi_state";
    public static final String EXTRA_WIFI_AP_FAILURE_REASON = "WIFI_AP_FAILURE_REASON";
    public static final String EXTRA_PREVIOUS_WIFI_AP_STATE = "previous_wifi_state";
    public static final String EXTRA_WIFI_AP_INTERFACE_NAME = "WIFI_AP_INTERFACE_NAME";

    public static final int SAP_CLIENT_BLOCK_REASON_CODE_BLOCKED_BY_USER = 0;
    public static final int SAP_CLIENT_BLOCK_REASON_CODE_NO_MORE_STAS = 1;
    public static final int SAP_CLIENT_DISCONNECT_REASON_CODE_UNSPECIFIED = 2;

    public static final int SAP_START_FAILURE_GENERAL= 0;
    public static final int SAP_START_FAILURE_NO_CHANNEL = 1;
    public static final int SAP_START_FAILURE_UNSUPPORTED_CONFIGURATION = 2;
    public static final int SAP_START_FAILURE_USER_REJECTED = 3;
    public static final int WIFI_AP_STATE_DISABLING = 10;
    public static final int WIFI_AP_STATE_DISABLED = 11;
    public static final int WIFI_AP_STATE_ENABLING = 12;
    public static final int WIFI_AP_STATE_ENABLED = 13;
    public static final int WIFI_AP_STATE_FAILED = 14;

    public static final int COEX_RESTRICTION_WIFI_DIRECT = 0x1 << 0;
    public static final int COEX_RESTRICTION_SOFTAP = 0x1 << 1;
    public static final int COEX_RESTRICTION_WIFI_AWARE = 0x1 << 2;

    private QtiWifiExtendManager(Context context, IQtiWifiExtendManager service) {
        mContext = context;
        mService = service;
        Log.i(TAG, "QtiWifiManager created");
    }

    public static void initialize(Context context, ApplicationBinderCallback cb) {
        try {
            bindService(context);
        } catch (ServiceFailedToBindException e) {
            Log.e(TAG, "ServiceFailedToBindException received");
        }
        mApplicationCallback = cb;
        mContext = context;
    }

    private static void bindService(Context context)
        throws ServiceFailedToBindException {
        if (!mServiceAlreadyBound  || mUniqueInstance == null) {
            Log.d(TAG, "bindService- !mServiceAlreadyBound  || uniqueInstance == null");
            Intent serviceIntent = new Intent("com.qualcomm.qti.server.wifiextend.WifiExtendService");
            serviceIntent.setPackage("com.qualcomm.qti.server.wifiextend");
            if (!context.bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE)) {
                Log.e(TAG,"Failed to connect to Provider service");
                throw new ServiceFailedToBindException("Failed to connect to Provider service");
            }
        }
    }

    public static void unbindService(Context context) {
        if(mServiceAlreadyBound) {
            context.unbindService(mConnection);
            mServiceAlreadyBound = false;
            mUniqueInstance = null;
        }
    }

    protected static ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "Connection object created");
            mServiceAlreadyBound = true;
            if (service == null) {
                Log.e(TAG, "qtiwifi service not available");
                return;
            }
            mUniqueInstance = IQtiWifiExtendManager.Stub.asInterface(service);
            mApplicationCallback.onAvailable(new QtiWifiExtendManager(mContext, mUniqueInstance));
        }
        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, "Remote service disconnected");
            mServiceAlreadyBound = false;
            mUniqueInstance = null;
        }
    };

    public static class ServiceFailedToBindException extends Exception {
        public static final long serialVersionUID = 1L;

        private ServiceFailedToBindException(String inString) {
            super(inString);
        }
    }

    public interface ApplicationBinderCallback {
        public abstract void onAvailable(QtiWifiExtendManager manager);
    }

    public boolean startSoftAp(SoftApConfiguration config) {
        try {
            return mService.startSoftAp(config);
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    public boolean stopSoftAp() {
        try {
            return mService.stopSoftAp();
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    public SoftApConfiguration getSoftApConfiguration() {
        try {
            return mService.getSoftApConfiguration();
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    public boolean setSoftApConfiguration(SoftApConfiguration softApConfig) {
        try {
            return mService.setSoftApConfiguration(softApConfig);
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    public int getWifiApState() {
        try {
            return mService.getWifiApState();
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    private static class ExtendSoftApCallbackProxy extends IExtendSoftApCallback.Stub {
        private final Handler mHandler;
        private final ExtendSoftApCallback mCallback;

        ExtendSoftApCallbackProxy(Looper looper, ExtendSoftApCallback callback) {
            mHandler = new Handler(looper);
            mCallback = callback;
        }

        @Override
        public void onStateChanged(int state, int failureReason) throws RemoteException {
            mHandler.post(() -> {
                mCallback.onStateChanged(state, failureReason);
            });
        }

        @Override
        public void onConnectedClientsChanged(WifiClient client, boolean isConnected,
                                        int disconectReason) throws RemoteException {
            mHandler.post(() -> {
                mCallback.onConnectedClientsChanged(client, isConnected, disconectReason);
            });
        }

        @Override
        public void onInfoChanged(List<SoftApInfo> softApInfoList) throws RemoteException {
            mHandler.post(() -> {
                mCallback.onInfoChanged(softApInfoList);
            });
        }
    }

    public interface ExtendSoftApCallback {
        public abstract void onStateChanged(int state, int failureReason);
        public abstract void onConnectedClientsChanged(WifiClient client, 
                                   boolean isConnected, int disconectReason);
        public abstract void onInfoChanged(List<SoftApInfo> softApInfoList);
    }

    public void registerExtendSoftApCallback(ExtendSoftApCallback callback, Handler handler) {
        if (callback == null) throw new IllegalArgumentException("callback cannot be null");
        Log.v(TAG, "registerExtendSoftApCallback: callback=" + callback + ", handler=" + handler);

        Looper looper = (handler == null) ? mContext.getMainLooper() : handler.getLooper();
        try {
            mService.registerExtendSoftApCallback(new ExtendSoftApCallbackProxy(looper, callback),
                    callback.hashCode());
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    public void unregisterExtendSoftApCallback(ExtendSoftApCallback callback) {
        if (callback == null) throw new IllegalArgumentException("callback cannot be null");
        Log.v(TAG, "UnregisterExtendSoftApCallback: callback=" + callback);

        try {
            mService.unregisterExtendSoftApCallback(callback.hashCode());
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    public int[] getUsableChannels(int band) {
        try {
            return mService.getUsableChannels(band);
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    public void setCoexUnsafeChannels(List<CoexUnsafeChannel> coexUnsafeChannels) {
        try {
            mService.setCoexUnsafeChannels(coexUnsafeChannels);
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    public void setDefaultCountryCode(String countryCode) {
        try {
            mService.setDefaultCountryCode(countryCode);
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    public String[] listHostapdVendorInterfaces() {
        try {
            return mService.listHostapdVendorInterfaces();
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    public String doHostapdCtrlIfaceCmd(String ifname, String command) {
        try {
            return mService.doHostapdCtrlIfaceCmd(ifname, command);
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    public ThermalData getThermalInfo(String ifname) {
        if (ifname == null) throw new IllegalArgumentException("ifname cannot be null");
        Log.v(TAG, "getThermalInfo: ifname=" + ifname);
        try {
            return mService.getThermalInfo(ifname);
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    /**
     * Set TX power limitation in dBm.
     *
     * @param ifname Name of the interface.
     * @param dbm TX power in dBm.
     * @return Results of setTxPower.
     *
     * @throws IllegalArgumentException if ifname is null.
     */
    public boolean setTxPower(String ifname, int dbm) {
        try {
            return mService.setTxPower(ifname, dbm);
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    /**
     * Set ANI level.
     *
     * @param ifname Name of the interface.
     * @param mode ani level mode(0: auto, 1: fixed, else: auto).
     * @param ofdmlvl ANI level.
     * @return result of setAni.
     *
     * @throws IllegalArgumentException if ifname is null.
     */
    public boolean setAni(String ifname, int mode, int ofdmlvl) {
        try {
            return mService.setAni(ifname, mode, ofdmlvl);
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    /**
     * Set congestion report parameter.
     *
     * @param ifname Name of the interface.
     * @param enable Enable or disable congestion report.
     * @param thre Only when congestion achieved the threshold need to report.
     * @param inter Interval to report congestion.
     * @return result of setCongestionReport.
     */
    public boolean setCongestionReport(String ifname, int enable, int thre, int inter) {
        try {
            return mService.setCongestionReport(ifname, enable, thre, inter);
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    public String getClientIpAddress(WifiClient client) {
        try {
            return mService.getClientIpAddress(client);
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    public boolean setDataSharing(String ifname, boolean enable) {
        try {
            return mService.setDataSharing(ifname, enable);
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    private static class VendorEventCallbackProxy extends IVendorEventCallback.Stub {
        private final Handler mHandler;
        private final VendorEventCallback mCallback;

        VendorEventCallbackProxy(Looper looper, VendorEventCallback callback) {
            mHandler = new Handler(looper);
            mCallback = callback;
        }

        @Override
        public void onThermalChanged(String ifname, int thermal_state) throws RemoteException {
            mHandler.post(() -> {
                mCallback.onThermalChanged(ifname, thermal_state);
            });
        }

        @Override
        public void onCongestionChanged(String ifname, int percentage) throws RemoteException {
            mHandler.post(() -> {
                mCallback.onCongestionChanged(ifname, percentage);
            });
        }
    }

    public void registerVendorEventCallback(VendorEventCallback callback, Handler handler) {
        if (callback == null) throw new IllegalArgumentException("callback cannot be null");
        Log.v(TAG, "registerVendorEventCallback: callback=" + callback + ", handler=" + handler);

        Looper looper = (handler == null) ? mContext.getMainLooper() : handler.getLooper();
        try {
            mService.registerVendorEventCallback(new VendorEventCallbackProxy(looper, callback),
                    callback.hashCode());
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    public void unregisterVendorEventCallback(VendorEventCallback callback) {
        if (callback == null) throw new IllegalArgumentException("callback cannot be null");
        Log.v(TAG, "unregisterVendorEventCallback: callback=" + callback);

        try {
            mService.unregisterVendorEventCallback(callback.hashCode());
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    /**
     * Base class for vendor event callback. Should be extended by applications and
     * set when calling
     * {@link QtiWifiExtendManager#registerVendorEventCallback(VendorCallback, Handler)}.
     *
     */
    public interface VendorEventCallback {
        public abstract void onThermalChanged(String ifname, int thermal_state);
        public abstract void onCongestionChanged(String ifname, int percentage);
    }
}
