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

package com.qualcomm.qti.server.qtiwifi;

import android.content.Context;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemProperties;
import android.os.RemoteCallbackList;
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
import java.util.regex.Pattern;
import java.util.Date;
import java.text.SimpleDateFormat;

import com.qualcomm.qti.qtiwifi.ICsiCallback;
import com.qualcomm.qti.qtiwifi.IQtiWifiManager;
import com.qualcomm.qti.qtiwifi.IVendorEventCallback;
import com.qualcomm.qti.qtiwifi.ThermalData;
import com.qualcomm.qti.qtiwifi.CarPlayIEData;
import vendor.qti.hardware.wifi.supplicant.ISupplicantVendor;
import vendor.qti.hardware.wifi.qtiwifi.IfaceInfo;
import vendor.qti.hardware.wifi.qtiwifi.IfaceType;

public final class QtiWifiServiceImpl extends IQtiWifiManager.Stub {
    private static final String TAG = "QtiWifiServiceImpl";
    private boolean mServiceStarted = false;
    private WifiManager mWifiManager;

    private final Context mContext;
    private Object mLock = new Object();
    private final IntentFilter mQtiIntentFilter;

    private HandlerThread mHandlerThread = null;
    private Handler mHandler = null;
    private QtiWifiThreadRunner mQtiWifiThreadRunner = null;

    QtiWifiCsiHal qtiWifiCsiHal;
    QtiSupplicantStaIfaceHal qtiSupplicantStaIfaceHal;
    QtiHostapdHal qtiHostapdHal;
    QtiWifiHal qtiWifiHal;

    private boolean mIsQtiSupplicantHalInitialized = false;
    private boolean mIsQtiHostapdHalInitialized = false;

    private WifiHalListener mHalListener = new WifiHalListenerImpl();

    /* Hal vendor event string */
    public static final String THERMAL_EVENT_STR = "CTRL-EVENT-THERMAL-CHANGED";
    public static final String CONGESTION_EVENT_STR = "CTRL-EVENT-CONGESTION-REPORT";
    public static final Pattern THERMAL_PATTERN =
        Pattern.compile(THERMAL_EVENT_STR + " level=([0-9]+)");
    public static final Pattern CONGESTION_PATTERN =
        Pattern.compile(CONGESTION_EVENT_STR + " percentage=([0-9]+)");

    /* Vendor callbacks */
    private final RemoteCallbackList<IVendorEventCallback> mVendorEventCallbacks;
    private final HashMap<Integer, IVendorEventCallback> mVendorEventCallbacksMap = new HashMap<>();

    public ThermalData getThermalInfo(String ifname) {
        final String kGetThermalCmd = "DRIVER GET_THERMAL_INFO";
        enforceAccessPermission();
        String reply;
        reply = qtiWifiHal.doQtiWifiCmd(ifname, kGetThermalCmd);

        int[] info = new int[2];
        try {
            String[] infoString = reply.split("\\s+");
            info[0] = Integer.parseInt(infoString[0]);
            info[1] = Integer.parseInt(infoString[1]);
        } catch (Exception e) {
            Log.e(TAG, "invalid result for get thermal info");
            return null;
        }
        ThermalData thermalData = new ThermalData();
        thermalData.setTemperature(info[0]);
        thermalData.setThermalLevel(toFrameworkThermalLevel(info[1]));
        return thermalData;
    }

    public boolean enableCarPlayIE(CarPlayIEData carPlayIEData) {
        String[] ifnames = listHostapdVendorInterfaces();
        if (ifnames.length == 0) {
            Log.e(TAG, "can't get ap interface.");
            return false;
        }

        List<String> ifacesList = Arrays.asList(ifnames);
        String iface = ifacesList.get(0);
        Log.d(TAG, "Set carplay IE");
        qtiHostapdHal.enableSoftapCarPlay(iface, carPlayIEData);
        return true;
    }

    public boolean disableCarPlayIE() {
        String[] ifnames = listHostapdVendorInterfaces();
        if (ifnames.length == 0) {
            Log.e(TAG, "can't get ap interface.");
            return false;
        }

        List<String> ifacesList = Arrays.asList(ifnames);
        String iface = ifacesList.get(0);
        Log.d(TAG, "Disable carplay IE");
        qtiHostapdHal.disableSoftapCarPlay(iface);
        return true;
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
        if (ifname == null) {
            throw new IllegalArgumentException("ifname cannot be null");
        }

        //vendor requirement to limit max tx power >= 8dBm.
        if (dbm < 8) {
            Log.e(TAG, "Expecting max tx power limit >= 8 dBm, while actual dBm=" + dbm);
            return false;
        }

        final String kSetTxPowerCmd = "DRIVER SET_TXPOWER " + dbm;
        String reply;

        Log.v(TAG, "setTxPower: ifname=" + ifname + " TX power=" + dbm);
        reply = qtiWifiHal.doQtiWifiCmd(ifname, kSetTxPowerCmd);

        return setSuccess(reply);
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
        if (ifname == null) {
            throw new IllegalArgumentException("ifname cannot be null");
        }

        //we're not checking ofdmlvl here and mode is treated as 0 in hal layer if not 1.
        final String kSetAniCmd = "DRIVER SET_ANI_LEVEL " + mode + " " + ofdmlvl;
        String reply;

        Log.v(TAG, "setAni: ifname=" + ifname + " mode=" + mode + " level=" + ofdmlvl);
        reply = qtiWifiHal.doQtiWifiCmd(ifname, kSetAniCmd);
        return setSuccess(reply);
    }

    /**
     * Set Congestion report parameters.
     *
     * @param ifname Name of the interface.
     * @param enable ani level mode(0: auto, 1: fixed, else: auto).
     * @param thre Only when congestion achieved the threshold need to report.
     * @param inter Interval to report congestion.
     * @return result of setCongestionReport.
     *
     * @throws IllegalArgumentException if ifname is null.
     */
    public boolean setCongestionReport(String ifname, int enable, int thre, int inter) {
        if (ifname == null) {
            throw new IllegalArgumentException("ifname cannot be null");
        }

        final String kSetCongestionReportCmd = "DRIVER SET_CONGESTION_REPORT "
                     + enable + " " + thre + " " + inter;
        String reply;
        //threshold and interval limitation are checked in hal layer
        Log.v(TAG, "setCongestionReport: ifname=" + ifname + " enable=" + enable
                + " threshold=" + thre + " interval=" + inter);
        reply = qtiWifiHal.doQtiWifiCmd(ifname, kSetCongestionReportCmd);
        return setSuccess(reply);
    }

    // Defined to be used by Hal
    public interface WifiHalListener {
        void onThermalChanged(String ifname, int level);
        void onCongestionChanged(String ifname, int percent);
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

    private class WifiHalListenerImpl implements WifiHalListener {
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

    public QtiWifiServiceImpl(Context context) {
        Log.d(TAG, "QtiWifiServiceImpl ctor");
        mContext = context;

        mQtiIntentFilter = new IntentFilter();
        mQtiIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        mQtiIntentFilter.addAction(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        mContext.registerReceiver(mQtiReceiver, mQtiIntentFilter);

        mHandlerThread = new HandlerThread("QtiWifiHandlerThread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mQtiWifiThreadRunner = new QtiWifiThreadRunner(mHandler);
        mVendorEventCallbacks = new RemoteCallbackList<>();

        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        checkAndInitQtiWifiHal();
        if (mWifiManager.isWifiApEnabled()) {
            Log.d(TAG, "isWifiApEnabled true");
            checkAndInitHostapdVendorHal();
            mIsQtiHostapdHalInitialized = true;
        }
        if (mWifiManager.isWifiEnabled()) {
            Log.d(TAG, "isWifiEnabled true");
            checkAndInitSupplicantStaIfaceHal();
            mIsQtiSupplicantHalInitialized = true;
        }
    }

    protected void destroyService() {
        Log.d(TAG, "destroyService()");
        mHandlerThread.quit();
        mContext.unregisterReceiver(mQtiReceiver);
        mServiceStarted = false;
    }

    public void checkAndInitHostapdVendorHal() {
        Log.i(TAG, "checkAndInitHostapdVendorHal");
        qtiHostapdHal = new QtiHostapdHal();
        qtiHostapdHal.initialize();
        qtiHostapdHal.registerWifiHalListener(mHalListener);
    }

    public void checkAndInitCfrHal() {
        Log.i(TAG, "checkAndInitCfrHal");
        qtiWifiCsiHal = new QtiWifiCsiHal();
        qtiWifiCsiHal.initialize();
    }

    public void checkAndInitQtiWifiHal() {
        Log.i(TAG, "checkAndInitQtiWifiHal");
        qtiWifiHal = new QtiWifiHal();
        qtiWifiHal.initialize();
        qtiWifiHal.registerWifiHalListener(mHalListener);
    }

    public void checkAndInitSupplicantStaIfaceHal() {
        Log.i(TAG, "checkAndInitSupplicantStaIfaceHal");
        qtiSupplicantStaIfaceHal = new QtiSupplicantStaIfaceHal();
        qtiSupplicantStaIfaceHal.initialize();
        if (!qtiSupplicantStaIfaceHal.setupVendorIface("wlan0")) {
            Log.e(TAG, "Failed to setup iface in supplicant on wlan0");
        }
        qtiSupplicantStaIfaceHal.registerWifiHalListener(mHalListener);
    }

    private final BroadcastReceiver mQtiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                 int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                 if ((state == WifiManager.WIFI_STATE_ENABLED) && !mIsQtiSupplicantHalInitialized) {
                     Log.i(TAG, "Didn't iniltailze the supplicant hals, now initializing");
                     checkAndInitCfrHal();
                     checkAndInitSupplicantStaIfaceHal();
                     mIsQtiSupplicantHalInitialized = true;
                 } else if (state == WifiManager.WIFI_STATE_DISABLED) {
                     Log.i(TAG, "received wifi disabled");
                     mIsQtiSupplicantHalInitialized = false;
                 }
            } else if (WifiManager.WIFI_AP_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_AP_STATE, WifiManager.WIFI_AP_STATE_FAILED);
                if ((state == WifiManager.WIFI_AP_STATE_ENABLED) && !mIsQtiHostapdHalInitialized) {
                    Log.i(TAG, "Didn't initialize hostapd hal, now initializing");
                    checkAndInitHostapdVendorHal();
                    mIsQtiHostapdHalInitialized = true;
                } else if (state == WifiManager.WIFI_AP_STATE_DISABLED) {
                    Log.i(TAG, "received ap disabled");
                    mIsQtiHostapdHalInitialized = false;
                }
            }
        }
    };

    public String[] listHostapdVendorInterfaces() {
        if (!mIsQtiHostapdHalInitialized) {
            return null;
        }
        return mQtiWifiThreadRunner.call(() ->
            qtiHostapdHal.listVendorInterfaces(), null);
    }

    public String[] listSupplicantVendorInterfaces() {
        if (!mIsQtiSupplicantHalInitialized) {
            return null;
        }
        return mQtiWifiThreadRunner.call(() ->
            qtiSupplicantStaIfaceHal.listVendorInterfaces(), null);
    }

    public boolean isSupplicantIface(String ifname) {
        String[] ifnames = listSupplicantVendorInterfaces();
        if (ifnames != null && Arrays.asList(ifnames).contains(ifname)) {
           return true;
        }
        return false;
    }

    public boolean isHostapdIface(String ifname) {
        String[] ifnames = listHostapdVendorInterfaces();
        if (ifnames != null && Arrays.asList(ifnames).contains(ifname)) {
           return true;
        }
        return false;
    }

    public void registerVendorEventCallback(IVendorEventCallback callback,
            int callbackIdentifier) {
        // verify arguments
        if (callback == null) {
            throw new IllegalArgumentException("Callback must not be null");
        }
        enforceAccessPermission();
        Log.i(TAG, "registerVendorEventCallback uid=%" + Binder.getCallingUid());
        synchronized(mVendorEventCallbacks) {
            mVendorEventCallbacks.register(callback);
            mVendorEventCallbacksMap.put(callbackIdentifier, callback);
        }
    }

    public void unregisterVendorEventCallback(int callbackIdentifier) {
        Log.i(TAG, "registerVendorEventCallback uid=%" + Binder.getCallingUid());
        enforceAccessPermission();
        synchronized(mVendorEventCallbacks) {
            IVendorEventCallback callback = mVendorEventCallbacksMap.get(callbackIdentifier);
            if (callback == null) {
                Log.d(TAG, "no such registered callback found, id=" + callbackIdentifier);
                return;
            }
            mVendorEventCallbacks.unregister(callback);
            mVendorEventCallbacksMap.remove(callbackIdentifier);
        }
    }

    public List<String> getAvailableInterfaces() {
        enforceAccessPermission();
        List<String> ifaces = new ArrayList<String>();

        IfaceInfo[] wifiHalIfaces = qtiWifiHal.getAvailableInterfaces();

        if (wifiHalIfaces != null && wifiHalIfaces.length > 0) {
            for (IfaceInfo iface : wifiHalIfaces) {
                ifaces.add(iface.name);
            }
        }

        return ifaces;
    }

    private boolean setSuccess(String reply) {
        if (reply != null && reply.contains("OK")) {
            return true;
        }
        return false;
    }

   @Override
    public void registerCsiCallback(IBinder binder, ICsiCallback callback,
            int callbackIdentifier) {
        // verify arguments
        if (binder == null) {
            throw new IllegalArgumentException("Binder must not be null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("Callback must not be null");
        }
        enforceAccessPermission();
        Log.i(TAG, "registerCsiCallback uid=%" + Binder.getCallingUid());
        mQtiWifiThreadRunner.run(() ->
            qtiWifiCsiHal.registerCsiCallback(binder, callback, callbackIdentifier));
    }

    @Override
    public void unregisterCsiCallback(int callbackIdentifier) {
        enforceAccessPermission();
        Log.i(TAG, "unregisterCsiCallback uid=%" + Binder.getCallingUid());
        mQtiWifiThreadRunner.run(() ->
            qtiWifiCsiHal.unregisterCsiCallback(callbackIdentifier));
    }

    /**
     * see {@link com.qualcomm.qti.qtiwifi.QtiWifiManager#startCsi}
     */
    public void startCsi() {
        enforceChangePermission();
        Log.i(TAG, "startCsi");
        mQtiWifiThreadRunner.run(() -> qtiWifiCsiHal.startCsi());
        mQtiWifiThreadRunner.run(() -> qtiSupplicantStaIfaceHal.doDriverCmd(
				"CSI start 0"));
    }

    /**
     * see {@link com.qualcomm.qti.qtiwifi.QtiWifiManager#stopCsi}
     */
    public void stopCsi() {
        enforceChangePermission();
        Log.i(TAG, "stopCsi");
        mQtiWifiThreadRunner.run(() -> qtiSupplicantStaIfaceHal.doDriverCmd(
				"CSI stop"));
        mQtiWifiThreadRunner.run(() -> qtiWifiCsiHal.stopCsi());
    }

    public String doHostapdDriverCmd(String ifname, String command) {
       return mQtiWifiThreadRunner.call(() ->
           qtiHostapdHal.doDriverCmd(ifname, command), null);
    }

    public String doSupplicantDriverCmd(String command) {
        return mQtiWifiThreadRunner.call(() ->
            qtiSupplicantStaIfaceHal.doDriverCmd(command), null);
    }

    private void enforceAccessPermission() {
        mContext.enforceCallingOrSelfPermission(
            android.Manifest.permission.ACCESS_WIFI_STATE, TAG);
    }

    private void enforceChangePermission() {
        mContext.enforceCallingOrSelfPermission(
            android.Manifest.permission.CHANGE_WIFI_STATE, TAG);
    }
}
