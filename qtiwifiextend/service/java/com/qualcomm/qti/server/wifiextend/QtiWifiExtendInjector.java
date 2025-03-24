/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.qualcomm.qti.wifiextend.SoftApConfiguration;

import android.content.Context;
import android.util.LocalLog;
import android.util.Log;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.WorkSource;

public class QtiWifiExtendInjector {
    private static final String TAG = "ExtendWifiInjector";

    private HandlerThread mHandlerThread;
    private QtiWifiExtendThreadRunner mQtiWifiThreadRunner;
    private LocalLog mWifiHandlerLocalLog;

    SoftApConfigStore mSoftApConfigStore;
    WifiCountryCode mWifiCountryCode;
    ActiveModeWarden mActiveModeWarden;
    WifiNative mWifiNative;
    SelfRecovery mSelfRecovery;

    Context mContext;

    WifiHal mWifihal;
    HostapdHal mHostapdHal;

    Looper wifiLooper;
    Handler wifiHandler;

    QtiWifiExtendInjector(Context context) {
        mContext = context;
        mHandlerThread = new HandlerThread("QtiWifiExtendThread");
        mHandlerThread.start();
        wifiLooper = mHandlerThread.getLooper();
        mQtiWifiThreadRunner = new QtiWifiExtendThreadRunner(new Handler(wifiLooper));

        mWifiHandlerLocalLog = new LocalLog(1024);
        mSoftApConfigStore = new SoftApConfigStore(context);
        mWifiCountryCode = new WifiCountryCode(mSoftApConfigStore);

        mWifihal = new WifiHal(context);
        mHostapdHal = new HostapdHal(context, mQtiWifiThreadRunner);
        mWifiNative = new WifiNative(context, mWifihal, mHostapdHal, mQtiWifiThreadRunner);
        mActiveModeWarden = new ActiveModeWarden(this, wifiLooper, mWifiNative);
        mSelfRecovery = new SelfRecovery(context, mActiveModeWarden, mWifiNative);

    }

    public SoftApManager makeSoftApManager(
            ActiveModeManager.Listener<SoftApManager> listener,
            QtiWifiExtendServiceImpl.SoftApListener callback,
            SoftApModeConfiguration config,
            WorkSource requestorWs) {
        return new SoftApManager(mContext, wifiLooper,
            mWifiNative, this,
            listener, callback, mSoftApConfigStore,
            config, mActiveModeWarden, requestorWs);
    }

    public WifiNative getWifiNative() {
        return mWifiNative;
    }

    public HandlerThread getWifiHandlerThread() {
        return mHandlerThread;
    }

    public QtiWifiExtendThreadRunner getWifiRunner() {
        return mQtiWifiThreadRunner;
    }

    public SoftApConfigStore getSoftApConfigStore() {
        return mSoftApConfigStore;
    }

    public WifiCountryCode getWifiCountryCode() {
        return mWifiCountryCode;
    }

    public LocalLog getExtendWifiHandlerLocalLog() {
        return mWifiHandlerLocalLog;
    }

    public ActiveModeWarden getActiveModeWarden() {
        return mActiveModeWarden;
    }

    public SelfRecovery getSelfRecovery() {
        return mSelfRecovery;
    }
}
