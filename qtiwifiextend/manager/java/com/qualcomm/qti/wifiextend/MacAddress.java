/*
 * Copyright 2017 The Android Open Source Project
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
import java.util.Objects;
import java.util.Arrays;

public final class MacAddress implements Parcelable {

    private static final int ETHER_ADDR_LEN = 6;

    /**
     * The MacAddress representing the unique broadcast MAC address.
     */
    public static final MacAddress BROADCAST_ADDRESS = MacAddress.fromString("ff:ff:ff:ff:ff:ff");

    public static final MacAddress ALL_ZEROS_MAC_ADDRESS = MacAddress.fromString("00:00:00:00:00:00");

    public static final int TYPE_UNKNOWN = 0;
    /** Indicates a MAC address is a unicast address. */
    public static final int TYPE_UNICAST = 1;
    /** Indicates a MAC address is a multicast address. */
    public static final int TYPE_MULTICAST = 2;
    /** Indicates a MAC address is the broadcast address. */
    public static final int TYPE_BROADCAST = 3;

    private static final long VALID_LONG_MASK = (1L << 48) - 1;
    private static final long LOCALLY_ASSIGNED_MASK = MacAddress.fromString("2:0:0:0:0:0").mAddr;
    private static final long MULTICAST_MASK = MacAddress.fromString("1:0:0:0:0:0").mAddr;

    // Internal representation of the MAC address as a single 8 byte long.
    // The encoding scheme sets the two most significant bytes to 0. The 6 bytes of the
    // MAC address are encoded in the 6 least significant bytes of the long, where the first
    // byte of the array is mapped to the 3rd highest logical byte of the long, the second
    // byte of the array is mapped to the 4th highest logical byte of the long, and so on.
    private final long mAddr;

    private MacAddress(long addr) {
        mAddr = (VALID_LONG_MASK & addr);
    }

    public int getAddressType() {
        if (equals(BROADCAST_ADDRESS)) {
            return TYPE_BROADCAST;
        }
        if ((mAddr & MULTICAST_MASK) != 0) {
            return TYPE_MULTICAST;
        }
        return TYPE_UNICAST;
    }

    public boolean isLocallyAssigned() {
        return (mAddr & LOCALLY_ASSIGNED_MASK) != 0;
    }

    public byte[] toByteArray() {
        byte[] bytes = new byte[ETHER_ADDR_LEN];
        long tmp = mAddr;
        int index = ETHER_ADDR_LEN;
        while (index-- > 0) {
            bytes[index] = (byte) tmp;
            tmp = tmp >> 8;
        }
        return bytes;
    }

    public String toString() {
        return stringAddrFromLongAddr(mAddr);
    }

    private static String stringAddrFromLongAddr(long addr) {
        return String.format("%02x:%02x:%02x:%02x:%02x:%02x",
                (addr >> 40) & 0xff,
                (addr >> 32) & 0xff,
                (addr >> 24) & 0xff,
                (addr >> 16) & 0xff,
                (addr >> 8) & 0xff,
                addr & 0xff);
    }

    public static MacAddress fromString(String addr) {
        return new MacAddress(longAddrFromStringAddr(addr));
    }

    // Internal conversion function equivalent to longAddrFromByteAddr(byteAddrFromStringAddr(addr))
    // that avoids the allocation of an intermediary byte[].
    private static long longAddrFromStringAddr(String addr) {
        Objects.requireNonNull(addr);
        String[] parts = addr.split(":");
        if (parts.length != ETHER_ADDR_LEN) {
            throw new IllegalArgumentException(addr + " was not a valid MAC address");
        }
        long longAddr = 0;
        for (int i = 0; i < parts.length; i++) {
            int x = Integer.valueOf(parts[i], 16);
            if (x < 0 || 0xff < x) {
                throw new IllegalArgumentException(addr + "was not a valid MAC address");
            }
            longAddr = x + (longAddr << 8);
        }
        return longAddr;
    }

    public int hashCode() {
        return (int) ((mAddr >> 32) ^ mAddr);
    }

    public boolean equals(Object o) {
        return (o instanceof MacAddress) && ((MacAddress) o).mAddr == mAddr;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeLong(mAddr);
    }

    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<MacAddress> CREATOR =
            new Parcelable.Creator<MacAddress>() {
                public MacAddress createFromParcel(Parcel in) {
                    return new MacAddress(in.readLong());
                }

                public MacAddress[] newArray(int size) {
                    return new MacAddress[size];
                }
            };

    public static boolean isMacAddress(byte[] addr) {
        return addr != null && addr.length == ETHER_ADDR_LEN;
    }

    public static MacAddress fromBytes(byte[] addr) {
        if (!isMacAddress(addr)) {
            throw new IllegalArgumentException(
                    Arrays.toString(addr) + " was not a valid MAC address");
        }
        long longAddr = 0;
        for (byte b : addr) {
            final int uint8Byte = b & 0xff;
            longAddr = (longAddr << 8) + uint8Byte;
        }
        return new MacAddress(longAddr);
    }

}
