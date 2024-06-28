/* Copyright (c) 2021-2022 Qualcomm Innovation Center, Inc.
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

package com.qualcomm.qti.qtiwifi;

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

import com.qualcomm.qti.qtiwifi.ThermalData;
import com.qualcomm.qti.qtiwifi.CarPlayIEData;

public class QtiWifiManager {
    private static final String TAG = "QtiWifiManager";
    private static ApplicationBinderCallback mApplicationCallback = null;
    private static Context mContext;
    private static boolean mServiceAlreadyBound = false;
    private static IQtiWifiManager mUniqueInstance = null;
    IQtiWifiManager mService;

    private QtiWifiManager(Context context, IQtiWifiManager service) {
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
            Intent serviceIntent = new Intent("com.qualcomm.qti.server.qtiwifi.QtiWifiService");
            serviceIntent.setPackage("com.qualcomm.qti.server.qtiwifi");
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
            mUniqueInstance = IQtiWifiManager.Stub.asInterface(service);
            new QtiWifiManager(mContext, mUniqueInstance);
            mApplicationCallback.onAvailable(new QtiWifiManager(mContext, mUniqueInstance));
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
        public abstract void onAvailable(QtiWifiManager manager);
    }

    public List<String> getAvailableInterfaces() {
        try {
            return mService.getAvailableInterfaces();
        } catch (RemoteException e) {
            Log.e(TAG, "getAvailableInterfaces: " + e);
            return null;
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
            throw e.rethrowFromSystemServer();
        }
    }

    public void unregisterVendorEventCallback(VendorEventCallback callback) {
        if (callback == null) throw new IllegalArgumentException("callback cannot be null");
        Log.v(TAG, "unregisterVendorEventCallback: callback=" + callback);

        try {
            mService.unregisterVendorEventCallback(callback.hashCode());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public ThermalData getThermalInfo(String ifname) {
        if (ifname == null) throw new IllegalArgumentException("ifname cannot be null");
        Log.v(TAG, "getThermalInfo: ifname=" + ifname);
        try {
            return mService.getThermalInfo(ifname);
        } catch (RemoteException e) {
            Log.e(TAG, "getThermalInfo: " + e);
            return null;
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
            throw e.rethrowFromSystemServer();
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
            throw e.rethrowFromSystemServer();
        }
    }

    public boolean enableCarPlayIE(CarPlayIEData carPlayIEData) {
        try {
            Log.d(TAG, "setCarPlayIE");
            return mService.enableCarPlayIE(carPlayIEData);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public boolean disableCarPlayIE() {
        try {
            Log.d(TAG, "disableCarPlayIE");
            return mService.disableCarPlayIE();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
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
            throw e.rethrowFromSystemServer();
        }
    }

    public String[] listHostapdVendorInterfaces() {
        try {
            return mService.listHostapdVendorInterfaces();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public String[] listSupplicantVendorInterfaces() {
        try {
            return mService.listSupplicantVendorInterfaces();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public String doHostapdDriverCmd(String ifname, String command) {
        try {
            return mService.doHostapdDriverCmd(ifname, command);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public String doSupplicantDriverCmd(String command) {
        try {
            return mService.doSupplicantDriverCmd(command);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Base class for vendor event callback. Should be extended by applications and
     * set when calling
     * {@link QtiWifiManager#registerVendorEventCallback(VendorCallback, Handler)}.
     *
     */
    public interface VendorEventCallback {
        public abstract void onThermalChanged(String ifname, int thermal_state);
        public abstract void onCongestionChanged(String ifname, int percentage);
    }
}
