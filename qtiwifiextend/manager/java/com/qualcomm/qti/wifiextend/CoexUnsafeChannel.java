/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.os.Parcel;
import android.os.Parcelable;

public final class CoexUnsafeChannel implements Parcelable {
   public static final int POWER_CAP_NONE = Integer.MAX_VALUE;
   public static final int WIFI_BAND_24_GHZ = 1;
   public static final int WIFI_BAND_5_GHZ = 1 << 1;
   public static final int WIFI_BAND_6_GHZ = 1 << 3;

   private int mBand;
   private int mChannel;
   private int mPowerCapDbm;

   public CoexUnsafeChannel(int band, int channel, int powerCapDbm) {
       mBand = band;
       mChannel = channel;
       mPowerCapDbm = powerCapDbm;
   }

   public CoexUnsafeChannel(int band, int channel) {
       mBand = band;
       mChannel = channel;
       mPowerCapDbm = POWER_CAP_NONE;
   }

   public int getBand() {
       return mBand;
   }

   public int getChannel() {
       return mChannel;
   }

   public int getPowerCapDbm() {
       return mPowerCapDbm;
   }

    public String toString() {
        StringBuilder sj = new StringBuilder("CoexUnsafeChannel{");
        if (mBand == WIFI_BAND_24_GHZ) {
            sj.append("2.4GHz");
        } else if (mBand == WIFI_BAND_5_GHZ) {
            sj.append("5GHz");
        } else if (mBand == WIFI_BAND_6_GHZ) {
            sj.append("6GHz");
        } else {
            sj.append("UNKNOWN BAND");
        }
        sj.append(", ").append(mChannel);
        if (mPowerCapDbm != POWER_CAP_NONE) {
            sj.append(", ").append(mPowerCapDbm).append("dBm");
        }
        sj.append('}');
        return sj.toString();
    }

    public int describeContents() {
        return 0;
    }

    public void readFromParcel(Parcel in) {
        mBand = in.readInt();
        mChannel = in.readInt();
        mPowerCapDbm = in.readInt();
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mBand);
        dest.writeInt(mChannel);
        dest.writeInt(mPowerCapDbm);
    }

    private CoexUnsafeChannel(Parcel in) {
        readFromParcel(in);
    }

    public static final Parcelable.Creator<CoexUnsafeChannel> CREATOR
         = new Parcelable.Creator<CoexUnsafeChannel>() {
        public CoexUnsafeChannel createFromParcel(Parcel in) {
            return new CoexUnsafeChannel(in);
        }

        public CoexUnsafeChannel[] newArray(int size) {
            return new CoexUnsafeChannel[size];
        }
    };

}
