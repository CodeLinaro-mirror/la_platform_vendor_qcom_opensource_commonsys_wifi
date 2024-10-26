/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.annotation.NonNull;
import android.net.wifi.WifiManager;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.qualcomm.qti.wifiextend.MacAddress;
import com.qualcomm.qti.wifiextend.SoftApConfiguration;

import com.qualcomm.qti.server.wifiextend.WifiNative.HostapdDeathEventHandler;
import com.qualcomm.qti.server.wifiextend.WifiNative.SoftApHalCallback;

import java.io.PrintWriter;

/**
 * This is @ThreadSafe.
 * To maintain thread-safety, the locking protocol is that every non-static method (regardless of
 * access level) acquires mLock.
 */

public class HostapdHal {
    private static final String TAG = "ExtendHostapdHal";

    private final Object mLock = new Object();
    private boolean mVerboseLoggingEnabled = false;
    private boolean mVerboseHalLoggingEnabled = false;
    private final Context mContext;
    //private final Handler mEventHandler;
    private QtiWifiExtendThreadRunner mEventHandler;

    // Hostapd HAL interface object - might be implemented by HIDL or AIDL
    private IHostapdHal mIHostapd;

    public HostapdHal(Context context, QtiWifiExtendThreadRunner handler) {
        mContext = context;
        mEventHandler = handler;
    }

    /** Abstraction of HAL interface */
    interface IHostapdHal {
        /**
         * Begin initializing the IHostapdHal object. Specific initialization logic differs
         * between the HIDL and AIDL implementations.
         *
         * @return true if the initialization routine was successful
         */
        boolean initialize();

        /**
         * Start hostapd daemon.
         */
        boolean startDaemon();

        /**
         * Enable/Disable verbose logging.
         *
         * @param verboseEnabled true to enable, false to disable.
         * @param halVerboseEnabled true to enable hal verbose logging, false to disable.
         */
        void enableVerboseLogging(boolean verboseEnabled, boolean halVerboseEnabled);

        /**
         * Add and start a new access point.
         *
         * @param ifaceName Name of the interface.
         * @param config Configuration to use for the AP.
         * @param isMetered Indicates the network is metered or not. Ignored in AIDL imp.
         * @param onFailureListener A runnable to be triggered on failure.
         * @return true on success, false otherwise.
         */
        boolean addAccessPoint(String ifaceName,
                SoftApConfiguration config, boolean isMetered,
                Runnable onFailureListener);

        /**
         * Remove a previously started access point.
         *
         * @param ifaceName Name of the interface.
         * @return true on success, false otherwise.
         */
            boolean removeAccessPoint(String ifaceName);

        /**
         * Remove a previously connected client.
         *
         * @param ifaceName Name of the interface.
         * @param client Mac Address of the client.
         * @param reasonCode One of disconnect reason code which defined in {@link WifiManager}.
         * @return true on success, false otherwise.
         */
        boolean forceClientDisconnect(String ifaceName,
                MacAddress client, int reasonCode);

        /**
         * Register the provided callback handler for SoftAp events.
         * <p>
         * Note that only one callback can be registered at a time - any registration overrides previous
         * registrations.
         *
         * @param ifaceName Name of the interface.
         * @param callback Callback listener for AP events.
         * @return true on success, false on failure.
         */
        boolean registerApCallback(String ifaceName,
                SoftApHalCallback callback);

        /**
         * Returns whether or not the hostapd supports getting the AP info from the callback.
        */
        boolean isApInfoCallbackSupported();

        /**
         * Registers a death notification for hostapd.
         * @return Returns true on success.
         */
        boolean registerDeathHandler(HostapdDeathEventHandler handler);

        /**
         * Deregisters a death notification for hostapd.
         * @return Returns true on success.
         */
        boolean deregisterDeathHandler();

        /**
         * Signals whether Initialization started successfully.
         */
        boolean isInitializationStarted();

        /**
         * Signals whether Initialization completed successfully.
         */
        boolean isInitializationComplete();

        /**
         * Terminate the hostapd daemon & wait for it's death.
         */
        void terminate();

        /**
         * Dump information about the specific implementation.
         */
    }

    /**
     * Enable/Disable verbose logging.
     */
    public void enableVerboseLogging(boolean verboseEnabled, boolean halVerboseEnabled) {
        synchronized (mLock) {
            mVerboseLoggingEnabled = verboseEnabled;
            mVerboseHalLoggingEnabled = halVerboseEnabled;
            if (mIHostapd != null) {
                mIHostapd.enableVerboseLogging(verboseEnabled, halVerboseEnabled);
            }
        }
    }

    /**
     * Initialize the HostapdHal. Creates the internal IHostapdHal object
     * and calls its initialize method.
     *
     * @return true if the initialization succeeded
     */
    public boolean initialize() {
        synchronized (mLock) {
            if (mVerboseLoggingEnabled) {
                Log.i(TAG, "Initializing Hostapd Service.");
            }
            if (mIHostapd != null) {
                Log.wtf(TAG, "Hostapd HAL has already been initialized.");
                return false;
            }
            mIHostapd = createIHostapdHalMockable();
            if (mIHostapd == null) {
                Log.e(TAG, "Failed to get Hostapd HAL instance");
                return false;
            }
            mIHostapd.enableVerboseLogging(mVerboseLoggingEnabled, mVerboseHalLoggingEnabled);
            if (!mIHostapd.initialize()) {
                Log.e(TAG, "Fail to init hostapd, Stopping hostapd startup");
                mIHostapd = null;
                return false;
            }
            return true;
        }
    }

    /**
     * Wrapper function to create the IHostapdHal object. Created to be mockable in unit tests.
     */
    protected IHostapdHal createIHostapdHalMockable() {
        synchronized (mLock) {
            // Prefer AIDL implementation if service is declared.
            if (HostapdHalAidlImp.serviceDeclared()) {
                Log.i(TAG, "Initializing hostapd using AIDL implementation.");
                return new HostapdHalAidlImp(mContext, mEventHandler);
            }
            Log.e(TAG, "No HIDL or AIDL service available for hostapd.");
            return null;
        }
    }

    /**
     * Returns whether or not the hostapd supports getting the AP info from the callback.
     */
    public boolean isApInfoCallbackSupported() {
        synchronized (mLock) {
            String methodStr = "isApInfoCallbackSupported";
            if (mIHostapd == null) {
                return handleNullIHostapd(methodStr);
            }
            return mIHostapd.isApInfoCallbackSupported();
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
    public boolean registerApCallback(String ifaceName, SoftApHalCallback callback) {
        synchronized (mLock) {
            String methodStr = "registerApCallback";
            if (mIHostapd == null) {
                return handleNullIHostapd(methodStr);
            }
            return mIHostapd.registerApCallback(ifaceName, callback);
        }
    }

    /**
     * Add and start a new access point.
     *
     * @param ifaceName Name of the interface.
     * @param config Configuration to use for the AP.
     * @param isMetered Indicates the network is metered or not.
     * @param onFailureListener A runnable to be triggered on failure.
     * @return true on success, false otherwise.
     */
    public boolean addAccessPoint(String ifaceName, SoftApConfiguration config,
                                  boolean isMetered, Runnable onFailureListener) {
        synchronized (mLock) {
            String methodStr = "addAccessPoint";
            if (mIHostapd == null) {
                return handleNullIHostapd(methodStr);
            }
            return mIHostapd.addAccessPoint(ifaceName, config, isMetered, onFailureListener);
        }
    }

    /**
     * Remove a previously started access point.
     *
     * @param ifaceName Name of the interface.
     * @return true on success, false otherwise.
     */
    public boolean removeAccessPoint(String ifaceName) {
        synchronized (mLock) {
            String methodStr = "removeAccessPoint";
            if (mIHostapd == null) {
                return handleNullIHostapd(methodStr);
            }
            return mIHostapd.removeAccessPoint(ifaceName);
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
    public boolean forceClientDisconnect(String ifaceName,
            MacAddress client, int reasonCode) {
        synchronized (mLock) {
            String methodStr = "forceClientDisconnect";
            if (mIHostapd == null) {
                return handleNullIHostapd(methodStr);
            }
            return mIHostapd.forceClientDisconnect(ifaceName, client, reasonCode);
        }
    }

    /**
     * Registers a death notification for hostapd.
     * @return Returns true on success.
     */
    public boolean registerDeathHandler(HostapdDeathEventHandler handler) {
        synchronized (mLock) {
            String methodStr = "registerDeathHandler";
            if (mIHostapd == null) {
                return handleNullIHostapd(methodStr);
            }
            return mIHostapd.registerDeathHandler(handler);
        }
    }

    /**
     * Deregisters a death notification for hostapd.
     * @return Returns true on success.
     */
    public boolean deregisterDeathHandler() {
        synchronized (mLock) {
            String methodStr = "deregisterDeathHandler";
            if (mIHostapd == null) {
                return handleNullIHostapd(methodStr);
            }
            return mIHostapd.deregisterDeathHandler();
        }
    }

    /**
     * Signals whether Initialization completed successfully.
     */
    public boolean isInitializationStarted() {
        synchronized (mLock) {
            String methodStr = "isInitializationStarted";
            if (mIHostapd == null) {
                return handleNullIHostapd(methodStr);
            }
            return mIHostapd.isInitializationStarted();
        }
    }

    /**
     * Signals whether Initialization completed successfully.
     */
    public boolean isInitializationComplete() {
        synchronized (mLock) {
            String methodStr = "isInitializationComplete";
            if (mIHostapd == null) {
                return handleNullIHostapd(methodStr);
            }
            return mIHostapd.isInitializationComplete();
        }
    }

    /**
     * Start the hostapd daemon.
     *
     * @return true on success, false otherwise.
     */
    public boolean startDaemon() {
        synchronized (mLock) {
            String methodStr = "startDaemon";
            if (mIHostapd == null) {
                return handleNullIHostapd(methodStr);
            }
            return mIHostapd.startDaemon();
        }
    }

    /**
     * Terminate the hostapd daemon & wait for it's death.
     */
    public void terminate() {
        synchronized (mLock) {
            String methodStr = "terminate";
            if (mIHostapd == null) {
                handleNullIHostapd(methodStr);
                return;
            }
            mIHostapd.terminate();
        }
    }

    private boolean handleNullIHostapd(String methodStr) {
        Log.e(TAG, "Cannot call " + methodStr + " because mIHostapd is null.");
        return false;
    }

    /**
     * Returns whether the hostapd HAL supports reporting the single instance died event.
     */
    public boolean isSoftApInstanceDiedHandlerSupported() {
        return (mIHostapd != null) && (mIHostapd instanceof HostapdHalAidlImp);
    }
}
