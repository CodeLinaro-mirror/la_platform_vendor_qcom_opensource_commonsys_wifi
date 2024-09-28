/*
 *   Copyright (c) 2021 The Linux Foundation. All rights reserved.
 *
 *   Redistribution and use in source and binary forms, with or without
 *   modification, are permitted provided that the following conditions are
 *   met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   * Neither the name of [Organization] nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 *   THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 *   WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 *   MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 *   ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 *   BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *   CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *   SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 *   BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 *   WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 *   OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 *   IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *   Changes from Qualcomm Innovation Center, Inc. are provided under the following license:
 *   Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
 *   SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.qualcomm.qti.wifiextend;


import android.os.Parcel;
import android.os.Parcelable;

/**
 * Class for thermal info structure
 */
public final class ThermalData implements Parcelable {
    private int mTemperature;
    private int mLevel;

    public static final int THERMAL_INFO_LEVEL_FULL_PERF = 0;
    public static final int THERMAL_INFO_LEVEL_REDUCED_PERF = 1;
    public static final int THERMAL_INFO_LEVEL_TX_OFF = 2;
    public static final int THERMAL_INFO_LEVEL_SHUT_DOWN = 3;
    public static final int THERMAL_INFO_LEVEL_UNKNOWN = -1;

    public int getTemperature() {
        return mTemperature;
    }

    public void setTemperature(int temp) {
        mTemperature = temp;
    }

    public int getThermalLevel() {
        return mLevel;
    }

    public void setThermalLevel(int level) {
        mLevel = level;
    }

    public static final Parcelable.Creator<ThermalData> CREATOR
         = new Parcelable.Creator<ThermalData>() {
        public ThermalData createFromParcel(Parcel in) {
            return new ThermalData(in);
        }

        public ThermalData[] newArray(int size) {
            return new ThermalData[size];
        }
    };

    public ThermalData() {
    }

    private ThermalData(Parcel in) {
        readFromParcel(in);
    }

    public String toString() {
        StringBuilder sj = new StringBuilder("ThermalData{");
        sj.append("temperature: ").append(mTemperature);
        sj.append(", level: ");
        if (mLevel == THERMAL_INFO_LEVEL_FULL_PERF) {
            sj.append("full perf");
        } else if (mLevel == THERMAL_INFO_LEVEL_REDUCED_PERF) {
            sj.append("reduced perf");
        } else if (mLevel == THERMAL_INFO_LEVEL_TX_OFF) {
            sj.append("tx off");
        } else if (mLevel == THERMAL_INFO_LEVEL_SHUT_DOWN) {
            sj.append("shut down");
        } else if (mLevel == THERMAL_INFO_LEVEL_UNKNOWN) {
            sj.append("unknow");
        }
        sj.append('}');
        return sj.toString();
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mTemperature);
        out.writeInt(mLevel);
    }

    public void readFromParcel(Parcel in) {
        mTemperature = in.readInt();
        mLevel = in.readInt();
    }

    public int describeContents() {
        return 0;
    }
}
