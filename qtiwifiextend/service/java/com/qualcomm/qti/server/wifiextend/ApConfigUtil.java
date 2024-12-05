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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.StringJoiner;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.qualcomm.qti.wifiextend.SoftApConfiguration;

public class ApConfigUtil {

    private static final String TAG = "ExtendApConfigUtil";

    /* Return code for updateConfiguration. */
    public static final int SUCCESS = 0;
    public static final int ERROR_NO_CHANNEL = 1;
    public static final int ERROR_GENERIC = 2;
    public static final int ERROR_UNSUPPORTED_CONFIGURATION = 3;

    /**
     * Wi-Fi unknown standard
     */
    public static final int WIFI_STANDARD_UNKNOWN = 0;
    /**
     * Wi-Fi 802.11a/b/g
     */
    public static final int WIFI_STANDARD_LEGACY = 1;
    /**
     * Wi-Fi 802.11n
     */
    public static final int WIFI_STANDARD_11N = 4;
    /**
     * Wi-Fi 802.11ac
     */
    public static final int WIFI_STANDARD_11AC = 5;
    /**
     * Wi-Fi 802.11ax
     */
    public static final int WIFI_STANDARD_11AX = 6;
    /**
     * Wi-Fi 802.11ad
     */
    public static final int WIFI_STANDARD_11AD = 7;
    /**
     * Wi-Fi 802.11be
     */
    public static final int WIFI_STANDARD_11BE = 8;

    /**
     * 2.4 GHz band first channel number
     */
    public static final int BAND_24_GHZ_FIRST_CH_NUM = 1;
    /**
     * 2.4 GHz band last channel number
     */
    public static final int BAND_24_GHZ_LAST_CH_NUM = 14;
    /**
     * 2.4 GHz band frequency of first channel in MHz
     */
    public static final int BAND_24_GHZ_START_FREQ_MHZ = 2412;
    /**
     * 5 GHz band first channel number
     */
    public static final int BAND_5_GHZ_FIRST_CH_NUM = 32;
    /**
     * 5 GHz band last channel number
     */
    public static final int BAND_5_GHZ_LAST_CH_NUM = 177;
    /**
     * 5 GHz band frequency of first channel in MHz
     */
    public static final int BAND_5_GHZ_START_FREQ_MHZ = 5160;
    /**
     * 6 GHz band first channel number
     */
    public static final int BAND_6_GHZ_FIRST_CH_NUM = 1;
    /**
     * 6 GHz band last channel number
     */
    public static final int BAND_6_GHZ_LAST_CH_NUM = 233;
    /**
     * 6 GHz band frequency of first channel in MHz
     */
    public static final int BAND_6_GHZ_START_FREQ_MHZ = 5955;
    /**
     * 6 GHz band operating class 136 channel 2 center frequency in MHz
     */
    public static final int BAND_6_GHZ_OP_CLASS_136_CH_2_FREQ_MHZ = 5935;
    /**
     * 60 GHz band first channel number
     */
    public static final int BAND_60_GHZ_FIRST_CH_NUM = 1;
    /**
     * 60 GHz band last channel number
     */
    public static final int BAND_60_GHZ_LAST_CH_NUM = 6;
    /**
     * 60 GHz band frequency of first channel in MHz
     */
    public static final int BAND_60_GHZ_START_FREQ_MHZ = 58320;

    public static boolean isNonPasswordAP(int securityType) {
        return (securityType == SoftApConfiguration.SECURITY_TYPE_OPEN
            || securityType == SoftApConfiguration.SECURITY_TYPE_WPA3_OWE_TRANSITION
            || securityType == SoftApConfiguration.SECURITY_TYPE_WPA3_OWE);
    }

    public static boolean isSecurityTypeRestrictedFor6gBand(int type) {
        switch(type) {
            case SoftApConfiguration.SECURITY_TYPE_OPEN:
            case SoftApConfiguration.SECURITY_TYPE_WPA2_PSK:
            case SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION:
            case SoftApConfiguration.SECURITY_TYPE_WPA3_OWE_TRANSITION:
                return true;
        }
        return false;
    }

    /**
     * Helper function for comparing two SoftApConfiguration.
     *
     * @param currentConfig the original configuration.
     * @param newConfig the new configuration which plan to apply.
     * @return true if the difference between the two configurations requires a restart to apply,
     *         false otherwise.
     */
    public static boolean checkConfigurationChangeNeedToRestart(
            SoftApConfiguration currentConfig, SoftApConfiguration newConfig) {
        return !Objects.equals(currentConfig.getWifiSsid(), newConfig.getWifiSsid())
                || !Objects.equals(currentConfig.getBssid(), newConfig.getBssid())
                || currentConfig.getSecurityType() != newConfig.getSecurityType()
                || !Objects.equals(currentConfig.getPassphrase(), newConfig.getPassphrase())
                || currentConfig.isHiddenSsid() != newConfig.isHiddenSsid()
                || currentConfig.getBand() != newConfig.getBand()
                || currentConfig.getChannel() != newConfig.getChannel()
                || (!currentConfig.getChannels().toString()
                        .equals(newConfig.getChannels().toString()));
    }

    /**
     * Checks if band is a valid combination of {link  SoftApConfiguration#BandType} values
     */
    public static boolean isBandValid(int band) {
        int bandAny = SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ
                | SoftApConfiguration.BAND_6GHZ | SoftApConfiguration.BAND_60GHZ;
        return ((band != 0) && ((band & ~bandAny) == 0));
    }

    /**
     * Check if the band contains a certain sub-band
     *
     * @param band The combination of bands to validate
     * @param testBand the test band to validate on
     * @return true if band contains testBand, false otherwise
     */
    public static boolean containsBand(int band, int testBand) {
        return ((band & testBand) != 0);
    }

    /**
     * Checks if band contains multiple sub-bands
     * @param band a combination of sub-bands
     * @return true if band has multiple sub-bands, false otherwise
     */
    public static boolean isMultiband(int band) {
        return ((band & (band - 1)) != 0);
    }

    /**
     * Helper function to get device support 802.11 BE on Soft AP or not
     *
     * @param context the caller context used to get value from resource file.
     * @return true if supported, false otherwise.
     * Depends on chip's capability, set false by default
     * Consider wifi module on CEM support 80211be, should to set true
     * But QtiWifiExtendService don't enable MLO on single band AP
     * and disable MLO on bridged AP. If set true here,  bridged AP will
     * fail to start.
     * To do: add MLO logical, enable MLO for single band AP and
     * disable MLO for bridged AP.
     */
    public static boolean isIeee80211beSupported(Context context) {
        return false;
    }

    /**
     * Convert from an array of primitive bytes to an array list of Byte.
     */
    public static ArrayList<Byte> byteArrayToArrayList(byte[] bytes) {
        ArrayList<Byte> byteList = new ArrayList<>();
        for (Byte b : bytes) {
            byteList.add(b);
        }
        return byteList;
    }

    /**
     * Collect a List of allowed channels for ACS operations on a selected band
     *
     * @param band on which channel list are required
     * @param oemConfigString Configuration string from OEM resource file.
     *        An empty string means all channels on this band are allowed
     * @param callerConfig allowed chnannels as required by the caller
     *
     * @return List of channel numbers that meet both criteria
     */
    public static List<Integer> collectAllowedAcsChannels(int band,
            String oemConfigString, int[] callerConfig) {

        // Convert the OEM config string into a set of channel numbers
        Set<Integer> allowedChannelSet = getOemAllowedChannels(band, oemConfigString);

        // Update the allowed channels with user configuration
        allowedChannelSet.retainAll(getCallerAllowedChannels(band, callerConfig));

        return new ArrayList<Integer>(allowedChannelSet);
    }

    /**
     * Convert channel/band to frequency.
     * Note: the utility does not perform any regulatory domain compliance.
     * @param channel number to convert
     * @param band of channel to convert, type of SoftApConfiguration.BAND_XXXX
     * @return center frequency in Mhz of the channel, -1 if no match
     */
    public static int convertChannelToFrequency(int channel, int band) {
        if (band == SoftApConfiguration.BAND_2GHZ) {
            // Special case
            if (channel == 14) {
                return 2484;
            } else if (channel >= BAND_24_GHZ_FIRST_CH_NUM && channel <= BAND_24_GHZ_LAST_CH_NUM) {
                return ((channel - BAND_24_GHZ_FIRST_CH_NUM) * 5) + BAND_24_GHZ_START_FREQ_MHZ;
            } else {
                return -1;
            }
        }
        if (band == SoftApConfiguration.BAND_5GHZ) {
            if (channel >= BAND_5_GHZ_FIRST_CH_NUM && channel <= BAND_5_GHZ_LAST_CH_NUM) {
                return ((channel - BAND_5_GHZ_FIRST_CH_NUM) * 5) + BAND_5_GHZ_START_FREQ_MHZ;
            } else {
                return -1;
            }
        }
        if (band == SoftApConfiguration.BAND_6GHZ) {
            if (channel >= BAND_6_GHZ_FIRST_CH_NUM && channel <= BAND_6_GHZ_LAST_CH_NUM) {
                if (channel == 2) {
                    return BAND_6_GHZ_OP_CLASS_136_CH_2_FREQ_MHZ;
                }
                return ((channel - BAND_6_GHZ_FIRST_CH_NUM) * 5) + BAND_6_GHZ_START_FREQ_MHZ;
            } else {
                return -1;
            }
        }
        if (band == SoftApConfiguration.BAND_60GHZ) {
            if (channel >= BAND_60_GHZ_FIRST_CH_NUM && channel <= BAND_60_GHZ_LAST_CH_NUM) {
                return ((channel - BAND_60_GHZ_FIRST_CH_NUM) * 2160) + BAND_60_GHZ_START_FREQ_MHZ;
            } else {
                return -1;
            }
        }
        return -1;
    }

    private static Set<Integer> getSetForAllChannelsInBand(int band) {
        switch(band) {
            case SoftApConfiguration.BAND_2GHZ:
                return IntStream.rangeClosed(
                        BAND_24_GHZ_FIRST_CH_NUM,
                        BAND_24_GHZ_LAST_CH_NUM)
                        .boxed()
                        .collect(Collectors.toSet());

            case SoftApConfiguration.BAND_5GHZ:
                return IntStream.rangeClosed(
                        BAND_5_GHZ_FIRST_CH_NUM,
                        BAND_5_GHZ_LAST_CH_NUM)
                        .boxed()
                        .collect(Collectors.toSet());

            case SoftApConfiguration.BAND_6GHZ:
                return IntStream.rangeClosed(
                        BAND_6_GHZ_FIRST_CH_NUM,
                        BAND_6_GHZ_LAST_CH_NUM)
                        .boxed()
                        .collect(Collectors.toSet());
            default:
                Log.e(TAG, "Invalid band: " + bandToString(band));
                return Collections.emptySet();
        }
    }

    /**
     * Converts a SoftApConfiguration.BAND_* constant to a meaningful String
     */
    public static String bandToString(int band) {
        StringJoiner sj = new StringJoiner(" & ");
        sj.setEmptyValue("unspecified");
        if ((band & SoftApConfiguration.BAND_2GHZ) != 0) {
            sj.add("2Ghz");
        }
        band &= ~SoftApConfiguration.BAND_2GHZ;

        if ((band & SoftApConfiguration.BAND_5GHZ) != 0) {
            sj.add("5Ghz");
        }
        band &= ~SoftApConfiguration.BAND_5GHZ;

        if ((band & SoftApConfiguration.BAND_6GHZ) != 0) {
            sj.add("6Ghz");
        }
        band &= ~SoftApConfiguration.BAND_6GHZ;

        if ((band & SoftApConfiguration.BAND_60GHZ) != 0) {
            sj.add("60Ghz");
        }
        band &= ~SoftApConfiguration.BAND_60GHZ;
        if (band != 0) {
            return "Invalid band";
        }
        return sj.toString();
    }

    private static Set<Integer> getOemAllowedChannels(int band, String oemConfigString) {
        if (TextUtils.isEmpty(oemConfigString)) {
            // Empty string means all channels are allowed in this band
            return getSetForAllChannelsInBand(band);
        }

        // String is not empty, parsing it
        Set<Integer> allowedChannelsOem = new HashSet<>();

        for (String channelRange : oemConfigString.split(",")) {
            try {
                if (channelRange.contains("-")) {
                    String[] channels  = channelRange.split("-");
                    if (channels.length != 2) {
                        Log.e(TAG, "Unrecognized channel range, length is " + channels.length);
                        continue;
                    }
                    int start = Integer.parseInt(channels[0].trim());
                    int end = Integer.parseInt(channels[1].trim());
                    if (start > end) {
                        Log.e(TAG, "Invalid channel range, from " + start + " to " + end);
                        continue;
                    }
                    allowedChannelsOem.addAll(IntStream.rangeClosed(start, end)
                            .boxed().collect(Collectors.toSet()));
                } else if (!TextUtils.isEmpty(channelRange)) {
                    int channel = Integer.parseInt(channelRange.trim());
                    allowedChannelsOem.add(channel);
                }
            } catch (NumberFormatException e) {
                // Ignore malformed value
                Log.e(TAG, "Malformed channel value detected: " + e);
                continue;
            }
        }

        return allowedChannelsOem;
    }

    private static Set<Integer> getCallerAllowedChannels(int band, int[] callerConfig) {
        if (callerConfig.length == 0) {
            // Empty set means all channels are allowed in this band
            return getSetForAllChannelsInBand(band);
        }

        // Otherwise return the caller set as is
        return IntStream.of(callerConfig).boxed()
                .collect(Collectors.toCollection(HashSet::new));
    }
}
