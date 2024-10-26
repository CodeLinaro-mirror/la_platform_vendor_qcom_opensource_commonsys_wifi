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

 package com.qualcomm.qti.server.wifiextend;

import android.os.WorkSource;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Base class for available WiFi operating modes.
 *
 * Currently supported modes SoftAp.
 */
public interface ActiveModeManager {
    /**
     * Listener for ActiveModeManager state changes.
     * @param <T> type of ActiveModeManager that is being listened
     */
    interface Listener<T extends ActiveModeManager> {
        /**
         * Invoked when mode manager completes start.
         */
        void onStarted(T activeModeManager);
        /**
         * Invoked when mode manager completes stop.
         */
        void onStopped(T activeModeManager);
        /**
         * Invoked when mode manager completes a role switch.
         */
        //void onRoleChanged(T activeModeManager);
        /**
         * Invoked when mode manager encountered a failure on start or on mode switch.
         */
        void onStartFailure(T activeModeManager);
    }

    /**
     * Method used to stop the Manager for a given Wifi operational mode.
     */
    void stop();

    /**
     * Method to retrieve the original requestorWs
     */
    WorkSource getRequestorWs();

}
