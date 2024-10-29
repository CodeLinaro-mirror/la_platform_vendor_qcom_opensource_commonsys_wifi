/* Copyright (c) 2022-2023 Qualcomm Innovation Center, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.qualcomm.qti.server.wifiextend;

import vendor.qti.hardware.wifi.qtiwifi.IQtiWifi;
import vendor.qti.hardware.wifi.qtiwifi.IQtiWifiCallback;
import vendor.qti.hardware.wifi.qtiwifi.IfaceInfo;
import vendor.qti.hardware.wifi.qtiwifi.IfaceType;
import vendor.qti.hardware.wifi.qtiwifi.QtiWifiStatusCode;
import android.os.Handler;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.HwRemoteBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceSpecificException;
import android.util.Log;

import com.qualcomm.qti.server.wifiextend.util.GeneralUtil.Mutable;
import com.qualcomm.qti.server.wifiextend.QtiWifiExtendServiceImpl.QtiWifiHalListener;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashSet;

/**
 * HAL calls to set up the qtiwifi daemon. Uses the AIDL qtiwifi interface.
 */
public class QtiWifiHal {
    private static final String TAG = "ExtendQtiWifiHal";
    private static final String HAL_INSTANCE_NAME = IQtiWifi.DESCRIPTOR + "/cem";

    private static final int MIN_PORT_NUM = 0;
    private static final int MAX_PORT_NUM = 65535;

    private final Object mLock = new Object();
    private boolean mVerboseLoggingEnabled = false;
    private boolean mServiceDeclared = false;
    private String mVendorIfaceName = null;
    private Set<IfaceInfo> mActiveInterfaces = new HashSet<>();
    private QtiWifiHalListener mWifiHalListener;

    // qtiwifi AIDL interface objects
    private IQtiWifi mIQtiWifi = null;
    private QtiWifiDeathRecipient mQtiWifiDeathRecipient;

    /**
     * Register Hal listener for vendor events
     */
    public void registerWifiHalListener(QtiWifiHalListener listener) {
        mWifiHalListener = listener;
    }

    private class QtiWifiCallback extends IQtiWifiCallback.Stub {
        @Override
        public void onCtrlEvent(String ifaceName, String eventStr) {
            Log.i(TAG, ifaceName + ": received qtiwifi event: " + eventStr);
            if (eventStr == null) return;
            if (mWifiHalListener == null) {
                Log.e(TAG, "No listener is registered, return here");
                return;
            }

            // CTRL-EVENT-THERMAL-CHANGED level=3
            if (eventStr.startsWith(QtiWifiExtendServiceImpl.THERMAL_EVENT_STR)) {
                    Matcher match = QtiWifiExtendServiceImpl.THERMAL_PATTERN.matcher(eventStr);
                if (match.find()) {
                    int level = Integer.parseInt(match.group(1));
                    mWifiHalListener.onThermalChanged(ifaceName, level);
                } else {
                    Log.e(TAG, "Could not parse thermal event=" + eventStr);
                }
            // CTRL-EVENT-CONGESTION-REPORT percentage=3
            } else if (eventStr.startsWith(QtiWifiExtendServiceImpl.CONGESTION_EVENT_STR)) {
                    Matcher match = QtiWifiExtendServiceImpl.CONGESTION_PATTERN.matcher(eventStr);
                if (match.find()) {
                    int percent = Integer.parseInt(match.group(1));
                    mWifiHalListener.onCongestionChanged(ifaceName, percent);
                } else {
                    Log.e(TAG, "Could not parse congestion event=" + eventStr);
                }
            } else {
                Log.e(TAG, "Could not parse this event");
            }
        }

        @Override
        public String getInterfaceHash() {
            return IQtiWifiCallback.HASH;
        }

        @Override
        public int getInterfaceVersion() {
            return IQtiWifiCallback.VERSION;
        }
    }

    private class QtiWifiDeathRecipient implements DeathRecipient {
        @Override
        public void binderDied() {
                synchronized (mLock) {
                    Log.w(TAG, "IQtiWifi binder died.");
                    QtiWifiServiceDiedHandler();
                }
        }
    }

    public QtiWifiHal() {
        mQtiWifiDeathRecipient = new QtiWifiDeathRecipient();
        Log.i(TAG, "QtiWifiHal() invoked");
    }

    /**
     * Checks whether the IQtiWifi service is declared, and therefore should be available.
     *
     * @return true if the IQtiWifi service is declared
     */
    public boolean initialize() {
        synchronized (mLock) {
            if (mIQtiWifi != null) {
                Log.i(TAG, "Service is already initialized, skipping initialize method");
                return true;
            }
            if (mVerboseLoggingEnabled) {
                Log.i(TAG, "Checking for IQtiWifi service.");
            }
            mServiceDeclared = serviceDeclared();
            getQtiWifiInstance();
            return mServiceDeclared;
        }
    }

    /**
     * Wrapper functions to access HAL objects, created to be mockable in unit tests
     */
    protected IQtiWifi getQtiWifiMockable() {
        synchronized (mLock) {
            try {
                return IQtiWifi.Stub.asInterface(
                        ServiceManager.waitForDeclaredService(HAL_INSTANCE_NAME));
            } catch (Exception e) {
                Log.e(TAG, "Unable to get IQtiWifi service, " + e);
                return null;
            }
        }
    }

    protected IBinder getServiceBinderMockable() {
        synchronized (mLock) {
            if (mIQtiWifi == null) {
                return null;
            }
            return mIQtiWifi.asBinder();
        }
    }

    public boolean getQtiWifiInstance() {

        final String methodStr = "getQtiWifiInstance";
        if (mIQtiWifi != null) {
            Log.i(TAG, "Service is already initialized, skipping " + methodStr);
            return true;
        }

        mIQtiWifi = getQtiWifiMockable();
        if (!checkQtiWifiAndLogFailure(methodStr)) {
            return false;
        }

        Log.i(TAG, "Obtained IQtiWifi binder.");

        try {
            IBinder serviceBinder = getServiceBinderMockable();
            if (serviceBinder == null) {
                return false;
            }
            serviceBinder.linkToDeath(mQtiWifiDeathRecipient, /* flags= */  0);
            IQtiWifiCallback callback = new QtiWifiCallback();
            mIQtiWifi.registerQtiWifiCallback(callback);
            return true;
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    /**
     * Indicates whether the AIDL service is declared
     */
    public static boolean serviceDeclared() {
        return ServiceManager.isDeclared(HAL_INSTANCE_NAME);
    }

    /**
     * Returns false if QtiWifi is null, and logs failure to call methodStr
     */
    private boolean checkQtiWifiAndLogFailure(final String methodStr) {
        synchronized (mLock) {
            if (mIQtiWifi == null) {
                Log.e(TAG, "Can't call " + methodStr + ", IQtiWifi is null");
                return false;
            }
            return true;
        }
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        synchronized (mLock) {
            Log.e(TAG, "IQtiWifi." + methodStr + " failed with exception", e);
        }
    }

    private void handleServiceSpecificException(ServiceSpecificException e, String methodStr) {
        synchronized (mLock) {
            Log.e(TAG, "IQtiWifi." + methodStr + " failed with exception", e);
        }
    }

    /**
     * Handle QtiWifi death.
     */
    private void QtiWifiServiceDiedHandler() {
        synchronized (mLock) {
            mIQtiWifi = null;
            mActiveInterfaces.clear();
        }
    }

    /**
     * run Driver command
     *
     * @param command Driver Command
     * @return status
     */
    public String doQtiWifiCmd(String iface, String command)
    {
        synchronized (mLock) {
            final String methodStr = "doQtiWifiCmd";
            final Mutable<String> reply = new Mutable<>();

            reply.value = "";

            if (!checkQtiWifiAndLogFailure(methodStr)) {
                return null;
            }

            try {
                reply.value = mIQtiWifi.doQtiWifiCmd(iface, command);
            } catch (RemoteException e) {
                Log.e(TAG, "doQtiWifiCmd failed with RemoteException");
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                Log.e(TAG, "doQtiWifiCmd failed with ServiceSpecificException");
                handleServiceSpecificException(e, methodStr);
            }
            return reply.value;
         }
    }

    /**
     * List active SAP instances
     *
     * @return available SAP instances
     */
    public IfaceInfo[] getAvailableInterfaces() {
        synchronized (mLock) {
            String methodStr = "listAvailableInterfaces";
            if (!checkQtiWifiAndLogFailure(methodStr)) {
                return null;
            }

            try {
                return mIQtiWifi.listAvailableInterfaces();
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }
}

