/*
 *  Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 *  SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.qualcomm.qti.qtiwifi;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;

import android.net.MacAddress;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.util.List;
import java.util.ArrayList;

/**
 * Configuration class for CSI (Channel State Information) monitoring.
 *
 * CSI provides detailed information about the wireless channel between transmitter
 * and receiver, including amplitude and phase information for each subcarrier in an
 * OFDM system. This is useful for various applications including indoor
 * localization, gesture recognition, and wireless sensing.
 *
 * <p>Example usage:
 * <pre>{@code
 * CsiConfiguration config = new CsiConfiguration.Builder()
 *     .setIsActive(false)  // Passive mode
 *     .setFrequency(5180)  // Channel 36 (5 GHz)
 *     .setBandwidth(CsiConfiguration.CHANNEL_WIDTH_80MHZ)
 *     .setNsMask(0x3)  // Spatial streams 1 and 2
 *     .setFrameType(CsiConfiguration.FRAME_TYPE_DATA)
 *     .setFrameSubType(0x0)  // Data frames
 *     .setMacAddresses(macList)
 *     .setcsiDurationSeconds(60)
 *     .setReportIntervalMillis(1000)
 *     .build();
 * }</pre>
 */
public final class CsiConfiguration implements Parcelable {

    // Frame Type Constants
    /**
     * Frame Type: Management Frame (0x00)
     * Management frames are used for network management operations like association,
     * authentication, beacons, and probe requests/responses.
     */
    public static final int FRAME_TYPE_MGMT = 0x00;

    /**
     * Frame Type: Control Frame (0x01)
     * Control frames assist in the delivery of data frames, including RTS, CTS, and
     * ACK frames.
     */
    public static final int FRAME_TYPE_CTRL = 0x01;

    /**
     * Frame Type: Data Frame (0x02)
     * Data frames carry the actual payload data between stations.
     */
    public static final int FRAME_TYPE_DATA = 0x02;

    @Retention(SOURCE)
    @IntDef({
            FRAME_TYPE_MGMT,
            FRAME_TYPE_CTRL,
            FRAME_TYPE_DATA,
    })
    public @interface FrameType {}

    // Channel Bandwidth Constants
    /**
     * Channel Bandwidth: 20 MHz
     * Standard bandwidth for 2.4 GHz and 5/6 GHz bands.
     */
    public static final int CHANNEL_WIDTH_20MHZ = 20;

    /**
     * Channel Bandwidth: 40 MHz
     * Available for 2.4 GHz and 5/6 GHz bands. Provides double the throughput of
     * 20 MHz.
     */
    public static final int CHANNEL_WIDTH_40MHZ = 40;

    /**
     * Channel Bandwidth: 80 MHz
     * Available for 5 GHz and 6 GHz bands only. Introduced in 802.11ac.
     */
    public static final int CHANNEL_WIDTH_80MHZ = 80;

    /**
     * Channel Bandwidth: 160 MHz
     * Available for 5 GHz and 6 GHz bands only. Provides maximum throughput in
     * 802.11ac/ax.
     */
    public static final int CHANNEL_WIDTH_160MHZ = 160;

    /**
     * Channel Bandwidth: 320 MHz
     * Available for 6 GHz bands only. Provides maximum throughput in 802.11be.
     */
    public static final int CHANNEL_WIDTH_320MHZ = 320;

    @Retention(SOURCE)
    @IntDef({
            CHANNEL_WIDTH_20MHZ,
            CHANNEL_WIDTH_40MHZ,
            CHANNEL_WIDTH_80MHZ,
            CHANNEL_WIDTH_160MHZ,
            CHANNEL_WIDTH_320MHZ,
    })
    public @interface ChannelWidth {}

    /**
     * CSI Monitoring Mode
     * false = Passive mode: CSI data is collected from frames transmitted by
     * specified MAC addresses. Multiple MAC addresses can be monitored simultaneously.
     * true = Active mode: Device actively requests CSI data from a single MAC
     * address.
     */
    private final boolean isActive;

    /**
     * Operating Frequency in MHz
     * The center frequency of the Wi-Fi channel to monitor.
     *
     * Valid ranges:
     *   2.4 GHz band: 2412-2484 MHz (channels 1-14)
     *   5 GHz band: 4915-5895 MHz (channels 36-165)
     *   6 GHz band: 5925-7125 MHz (channels 1-233)
     *
     * An {@link IllegalArgumentException} will be thrown if the frequency is not in these ranges.
     *
     * Common examples:
     *   2412 MHz = Channel 1 (2.4 GHz)
     *   2437 MHz = Channel 6 (2.4 GHz)
     *   2462 MHz = Channel 11 (2.4 GHz)
     *   5180 MHz = Channel 36 (5 GHz)
     *   5200 MHz = Channel 40 (5 GHz)
     *   5220 MHz = Channel 44 (5 GHz)
     *   5745 MHz = Channel 149 (5 GHz)
     *
     */
    private final int frequency;

    /**
     * Channel Bandwidth in MHz
     * The bandwidth of the channel to monitor. Must be one of:
     *   {#CHANNEL_WIDTH_20MHZ} - 20 MHz (all bands)
     *   {#CHANNEL_WIDTH_40MHZ} - 40 MHz (all bands)
     *   {#CHANNEL_WIDTH_80MHZ} - 80 MHz (5/6 GHz only)
     *   {#CHANNEL_WIDTH_160MHZ} - 160 MHz (5/6 GHz only)
     *   {#CHANNEL_WIDTH_320MHZ} - 320 MHz (6 GHz only)
     *   Note: 2.4 GHz band supports only 20 MHz and 40 MHz bandwidths.
     * An {@link IllegalArgumentException} will be thrown if the bandwidth is not one of these values.
     */
    private final @ChannelWidth int bandwidth;

    /**
     * Number of Spatial Streams Mask (nsMask)
     * Bitmask specifying which spatial streams to capture in MIMO systems.
     * Each bit represents a spatial stream:
     *   Bit 0 (0x1): Spatial Stream 1
     *   Bit 1 (0x2): Spatial Stream 2
     *   Bit 2 (0x4): Spatial Stream 3
     *   Bit 3 (0x8): Spatial Stream 4
     *
     * Examples:
     *   0x1 (1): Only spatial stream 1
     *   0x3 (3): Spatial streams 1 and 2
     *   0x7 (7): Spatial streams 1, 2, and 3
     *   0xF (15): All 4 spatial streams
     */
    private final int nsMask;

    /**
     * Frame Type Filter (802.11 Frame Control bits 3-2)
     * Specifies which type of 802.11 frames to capture CSI data from.
     * This corresponds to the Type field in the 802.11 Frame Control field.
     *
     * Valid values:
     *   {#FRAME_TYPE_MGMT} (0x00): Management frames
     *   {#FRAME_TYPE_CTRL} (0x01): Control frames
     *   {#FRAME_TYPE_DATA} (0x02): Data frames
     *
     * An {@link IllegalArgumentException} will be thrown if the frame type is not one of these values.
     * This value is encoded in bits 3-2 of the Frame Control field.
     */
    private final @FrameType int frameType;

    /**
     * Frame Subtype Filter (802.11 Frame Control bits 7-4)
     * Specifies the subtype of frames to capture within the selected frame type.
     * This corresponds to the Subtype field in the 802.11 Frame Control field.
     *
     * Valid range: 0-15 (4-bit field as per IEEE 802.11 standard)
     *
     * Management Frame Subtypes (Type=0):
     *   0x0: Association Request
     *   0x1: Association Response
     *   0x4: Probe Request
     *   0x5: Probe Response
     *   0x8: Beacon
     *   0xA: Disassociation
     *   0xB: Authentication
     *   0xC: Deauthentication
     *
     * Control Frame Subtypes (Type=1):
     *   0xB: RTS (Request to Send)
     *   0xC: CTS (Clear to Send)
     *   0xD: ACK (Acknowledgment)
     *
     * Data Frame Subtypes (Type=2):
     *   0x0: Data
     *   0x4: Null (no data)
     *   0x8: QoS Data
     *
     * An {@link IllegalArgumentException} will be thrown if the subtype is not in the range 0-15.
     * This value is encoded in bits 7-4 of the Frame Control field.
     */
    private final int frameSubType;

    /**
     * CSI Capture Duration in Seconds
     * Specifies how long to capture CSI data.
     * Value of 0 may indicate continuous capture until explicitly stopped.
     * An {@link IllegalArgumentException} will be thrown if the value is negative.
     */
    private final int csiDurationSeconds;

    /**
     * Request Interval in Milliseconds (Active Mode Only)
     * For active mode (isActive=true): Interval between CSI data requests. Must be positive.
     * For passive mode (isActive=false): Not used (set to 0)
     * An {@link IllegalArgumentException} will be thrown if this is not positive in active mode.
     */
    private final int requestIntervalMillis;

    /**
     * Report Interval in Milliseconds
     * Interval at which CSI data is reported to the application. Must be positive.
     * Smaller values provide more frequent updates but may increase overhead.
     * An {@link IllegalArgumentException} will be thrown if this is not a positive value.
     */
    private final int reportIntervalMillis;

    /**
     * MAC Addresses to Monitor
     * For passive mode (isActive=false): List of one or more MAC addresses to capture CSI data from.
     * For active mode (isActive=true): A list containing exactly one MAC address.
     *
     * The list cannot be null or empty, and cannot contain the "00:00:00:00:00:00" MAC address.
     * An {@link IllegalArgumentException} will be thrown if these conditions are not met.
     * @see android.net.MacAddress
     */
    private final List<MacAddress> macAddresses;

    /** Private constructor for Builder. */
    private CsiConfiguration(
            boolean isActive,
            int frequency,
            @ChannelWidth int bandwidth,
            int nsMask,
            @FrameType int frameType,
            int frameSubType,
            int csiDurationSeconds,
            int requestIntervalMillis,
            int reportIntervalMillis,
            List<MacAddress> macAddresses) {
        this.isActive = isActive;
        this.frequency = frequency;
        this.bandwidth = bandwidth;
        this.nsMask = nsMask;
        this.frameType = frameType;
        this.frameSubType = frameSubType;
        this.csiDurationSeconds = csiDurationSeconds;
        this.requestIntervalMillis = requestIntervalMillis;
        this.reportIntervalMillis = reportIntervalMillis;
        this.macAddresses = macAddresses != null ? new ArrayList<>(macAddresses) : null;
    }

    private CsiConfiguration(Parcel in) {
        isActive = in.readBoolean();
        frequency = in.readInt();
        bandwidth = in.readInt();
        nsMask = in.readInt();
        frameType = in.readInt();
        frameSubType = in.readInt();
        csiDurationSeconds = in.readInt();
        requestIntervalMillis = in.readInt();
        reportIntervalMillis = in.readInt();
        int listSize = in.readInt();
        if (listSize > 0) {
            List<MacAddress> tempList = new ArrayList<>();
            for (int i = 0; i < listSize; i++) {
                tempList.add(MacAddress.fromString(in.readString()));
            }
            macAddresses = tempList;
        } else {
            macAddresses = null;
        }
    }

    /**
     * Gets the CSI monitoring mode.
     *
     * @return false for passive mode, true for active mode
     */
    public boolean isActive() {
        return isActive;
    }

    /**
     * Gets the operating frequency.
     *
     * @return Frequency in MHz
     */
    public int getFrequency() {
        return frequency;
    }

    /**
     * Gets the channel bandwidth.
     *
     * @return Bandwidth in MHz (20, 40, 80, 160, or 320)
     */
    public @ChannelWidth int getBandwidth() {
        return bandwidth;
    }

    /**
     * Gets the channel specification string for driver command.
     * This method constructs the chanspec from frequency and bandwidth.
     *
     * @return Channel specification string in hex format
     */
    private String getChanSpec() {
        return buildChanSpec(frequency, bandwidth);
    }

    /**
     * Gets the spatial streams mask.
     *
     * @return Bitmask indicating which spatial streams to capture
     */
    public int getNsMask() {
        return nsMask;
    }

    /**
     * Gets the frame type filter.
     *
     * @return Frame type (FRAME_TYPE_MGMT, FRAME_TYPE_CTRL, or FRAME_TYPE_DATA)
     */
    public @FrameType int getFrameType() {
        return frameType;
    }

    /**
     * Gets the frame subtype filter.
     *
     * @return Frame subtype (0-15)
     */
    public int getFrameSubType() {
        return frameSubType;
    }

    /**
     * Gets the 802.11 Frame Control filter byte.
     * Combines frameType and frameSubType according to 802.11 Frame Control structure:
     *   Bits 1-0: Protocol Version (always 0 for WLAN)
     *   Bits 3-2: Type (frameType)
     *   Bits 7-4: Subtype (frameSubType)
     *
     * Formula: (frameSubType << 4) | (frameType << 2) | 0
     *
     * Example: For QoS Data frame (Type=2, Subtype=8):
     * filter = (8 << 4) | (2 << 2) | 0 = 0x80 | 0x08 = 0x88
     */
    private int getFilter() {
        // 802.11 Frame Control structure: (subtype << 4) | (type << 2) | protocol_version
        return (frameSubType << 4) | (frameType << 2) | 0;
    }

    /**
     * Gets the list of MAC addresses to monitor.
     *
     * @return List of MacAddress objects (single element for active mode, multiple for passive mode)
     */
    public List<MacAddress> getMacAddresses() {
        return macAddresses != null ? new ArrayList<>(macAddresses) : null;
    }

    /**
     * Gets the CSI capture duration.
     *
     * @return Duration in seconds
     */
    public int getcsiDurationSeconds() {
        return csiDurationSeconds;
    }

    /**
     * Gets the request interval for active mode.
     *
     * @return Interval in milliseconds (only used in active mode)
     */
    public int getRequestIntervalMillis() {
        return requestIntervalMillis;
    }

    /**
     * Gets the report interval.
     *
     * @return Interval in milliseconds
     */
    public int getReportIntervalMillis() {
        return reportIntervalMillis;
    }

    /**
     * Builds the chanspec hex string from frequency and bandwidth.
     * The format of the chanspec is as follows:
     *   Bits 7-0:   Channel number
     *   Bits 12-8:  5-bit sub-band index for control sideband. 0b11111 for none.
     *   Bits 15-13: Bandwidth (1=20MHz, 2=40MHz, 3=80MHz, 4=160MHz, 5=320MHz)
     *   Bits 17-16: Band (1=2.4GHz, 2=5GHz, 3=6GHz)
     *   Bits 23-18: Reserved
     *
     * @param freq Frequency in MHz.
     * @param bw   Bandwidth in MHz.
     * @return Chanspec hex string (e.g., "0x2A1041").
     */
    private static String buildChanSpec(int freq, int bw) {
        int channel;
        int bandCode;
        int bwCode;
        int subband = 0b11111; // Default to no control sideband

        // Determine band and channel number
        if (freq >= 2412 && freq <= 2484) { // 2.4 GHz
            bandCode = 1;
            if (freq == 2484) {
                channel = 14; // Special case for channel 14 (2.4GHz)
            } else {
                channel = (freq - 2407) / 5;
            }
        } else if (freq >= 5160 && freq <= 5885) { // 5 GHz
            bandCode = 2;
            channel = (freq - 5000) / 5;
        } else if (freq >= 5925 && freq <= 7125) { // 6 GHz
            bandCode = 3;
            if (freq == 5935) {
                channel = 2;
            } else{
                channel = (freq - 5950) / 5;
            }
        } else {
            return "0x0"; // Invalid frequency
        }

        // Map bandwidth to code
        switch (bw) {
            case CHANNEL_WIDTH_20MHZ:
                bwCode = 1;
                break;
            case CHANNEL_WIDTH_40MHZ:
                bwCode = 2;
                break;
            case CHANNEL_WIDTH_80MHZ:
                bwCode = 3;
                break;
            case CHANNEL_WIDTH_160MHZ:
                bwCode = 4;
                break;
            case CHANNEL_WIDTH_320MHZ:
                bwCode = 5;
                break;
            default:
                bwCode = 1; // Default to 20 MHz
        }

        // Build the chanspec integer
        int chanspec = (channel & 0xFF) |
                       ((subband & 0x1F) << 8) |
                       ((bwCode & 0x7) << 13) |
                       ((bandCode & 0x3) << 16);

        return String.format("0x%X", chanspec);
    }

    public static final Parcelable.Creator<CsiConfiguration> CREATOR
         = new Parcelable.Creator<CsiConfiguration>() {
        public CsiConfiguration createFromParcel(Parcel in) {
            return new CsiConfiguration(in);
        }

        public CsiConfiguration[] newArray(int size) {
            return new CsiConfiguration[size];
        }
    };

    /**
     * Generates the driver command string for CSI configuration.
     *
     * @return Driver command string
     */
    public String getDriverCommand() {
        StringBuilder sb = new StringBuilder();
        sb.append("CSI_MONITORING config ");
        sb.append(isActive ? 1 : 0).append(" ");
        sb.append(getChanSpec()).append(" ");
        sb.append("0x03 "); // setting coreMask to default value of 0x03
        sb.append("0x").append(Integer.toHexString(nsMask)).append(" ");
        // Build 802.11 Frame Control filter byte: (subtype << 4) | (type << 2) | protocol_version
        int filter = getFilter();
        sb.append("0x").append(Integer.toHexString(filter)).append(" ");
        if (macAddresses != null && !macAddresses.isEmpty()) {
            if (!isActive) {
                // Passive mode: include count and all MAC addresses
                sb.append(macAddresses.size()).append(" ");
                for (int i = 0; i < macAddresses.size(); i++) {
                    sb.append(macAddresses.get(i).toString());
                    if (i < macAddresses.size() - 1) {
                        sb.append(" ");
                    }
                }
            } else {
                // Active mode: single MAC address (first in list)
                sb.append(macAddresses.get(0).toString());
            }
        }
        sb.append(" ");
        sb.append("0 "); //setting csi type to default value of 0
        sb.append(csiDurationSeconds).append(" ");
        if(isActive)
            sb.append(requestIntervalMillis).append(" ");
        sb.append(reportIntervalMillis).append(" ");

        return sb.toString();
    }

    @Override
    public String toString() {
        return "CsiConfiguration{" +
                "isActive=" + isActive +
                ", frequency=" + frequency + " MHz" +
                ", bandwidth=" + bandwidth + " MHz" +
                ", chanSpec='" + getChanSpec() + '\'' +
                ", nsMask=0x" + Integer.toHexString(nsMask) +
                ", frameType=0x" + Integer.toHexString(frameType) +
                ", frameSubType=0x" + Integer.toHexString(frameSubType) +
                ", csiDurationSeconds=" + csiDurationSeconds +
                ", requestIntervalMillis=" + requestIntervalMillis +
                ", reportIntervalMillis=" + reportIntervalMillis +
                ", macAddresses=" + macAddresses +
                '}';
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeBoolean(isActive);
        out.writeInt(frequency);
        out.writeInt(bandwidth);
        out.writeInt(nsMask);
        out.writeInt(frameType);
        out.writeInt(frameSubType);
        out.writeInt(csiDurationSeconds);
        out.writeInt(requestIntervalMillis);
        out.writeInt(reportIntervalMillis);
        if (macAddresses != null) {
            out.writeInt(macAddresses.size());
            for (MacAddress mac : macAddresses) {
                out.writeString(mac.toString());
            }
        } else {
            out.writeInt(0);
        }
    }

    public void readFromParcel(Parcel in) {
        // This method is kept for compatibility but is no longer used
        // since fields are now final and initialized in the constructor
    }

    public int describeContents() {
        return 0;
    }

    /**
     * Builder for {@link CsiConfiguration}.
     *
     * Allows step-by-step configuration of CSI monitoring parameters.
     * All fields are optional and default to 0 or null.
     *
     * Example usage:
     *
     * CsiConfiguration config = new CsiConfiguration.Builder()
     *     .setIsActive(false)  // Passive mode
     *     .setFrequency(5180)  // Channel 36 (5 GHz)
     *     .setBandwidth(CsiConfiguration.CHANNEL_WIDTH_80MHZ)
     *     .setNsMask(0x3)  // Spatial streams 1 and 2
     *     .setFrameType(CsiConfiguration.FRAME_TYPE_DATA)
     *     .setFrameSubType(0x0)  // Data frames
     *     .setMacAddresses(macList)
     *     .setcsiDurationSeconds(60)
     *     .setReportIntervalMillis(1000)
     *     .build();
     * }
     */
    public static final class Builder {
        private boolean misActive;
        private int mFrequency;
        private @ChannelWidth int mBandwidth;
        private int mNsMask;
        private @FrameType int mFrameType;
        private int mFrameSubType;
        private int mcsiDurationSeconds;
        private int mRequestIntervalMillis;
        private int mReportIntervalMillis;
        private List<MacAddress> mMacAddresses;

        /**
         * Constructs a Builder with default values.
         */
        public Builder() {
            misActive = false;
            mFrequency = 0;
            mBandwidth = CHANNEL_WIDTH_20MHZ;
            mNsMask = 0;
            mFrameType = 0;
            mFrameSubType = 0;
            mcsiDurationSeconds = 0;
            mRequestIntervalMillis = 0;
            mReportIntervalMillis = 0;
            mMacAddresses = null;
        }

        /**
         * Constructs a Builder initialized from an existing {@link CsiConfiguration} instance.
         *
         * @param config existing CsiConfiguration to copy from
         * @throws IllegalArgumentException if config is null
         */
        public Builder(CsiConfiguration config) {
            if (config == null) {
                throw new IllegalArgumentException("Cannot provide a null CsiConfiguration");
            }
            misActive = config.isActive;
            mFrequency = config.frequency;
            mBandwidth = config.bandwidth;
            mNsMask = config.nsMask;
            mFrameType = config.frameType;
            mFrameSubType = config.frameSubType;
            mcsiDurationSeconds = config.csiDurationSeconds;
            mRequestIntervalMillis = config.requestIntervalMillis;
            mReportIntervalMillis = config.reportIntervalMillis;
            mMacAddresses = config.macAddresses != null
                    ? new ArrayList<>(config.macAddresses) : null;
        }

        /**
         * Sets the CSI monitoring mode.
         *
         * @param isActive false for passive mode, true for active mode
         * @return Builder for chaining
         */
        public Builder setIsActive(boolean isActive) {
            misActive = isActive;
            return this;
        }

        /**
         * Sets the operating frequency.
         *
         * @param frequency Frequency in MHz. Valid ranges are 2412-2484, 4915-5895, or 5925-7125.
         * @return Builder for chaining
         * @throws IllegalArgumentException on build() if frequency is invalid.
         */
        public Builder setFrequency(int frequency) {
            mFrequency = frequency;
            return this;
        }

        /**
         * Sets the channel bandwidth.
         *
         * @param bandwidth Bandwidth in MHz. Use one of:
         *                  {@link #CHANNEL_WIDTH_20MHZ},
         *                  {@link #CHANNEL_WIDTH_40MHZ},
         *                  {@link #CHANNEL_WIDTH_80MHZ},
         *                  {@link #CHANNEL_WIDTH_160MHZ},
         *                  {@link #CHANNEL_WIDTH_320MHZ}
         * @return Builder for chaining
         * @throws IllegalArgumentException on build() if bandwidth is invalid.
         */
        public Builder setBandwidth(@ChannelWidth int bandwidth) {
            mBandwidth = bandwidth;
            return this;
        }

        /**
         * Sets the channel specification (deprecated - use setFrequency and setBandwidth instead).
         *
         * @param chanSpec Channel specification string (ignored)
         * @return Builder for chaining
         * @deprecated Use {@link #setFrequency(int)} and {@link #setBandwidth(int)} instead
         */
        @Deprecated
        public Builder setChanSpec(String chanSpec) {
            // Deprecated - chanspec is now built from frequency and bandwidth
            // This method is kept for backward compatibility but does nothing
            return this;
        }

        /**
         * Sets the spatial streams mask.
         *
         * @param nsMask Bitmask for spatial streams (e.g., 0x3 for streams 1 and 2)
         * @return Builder for chaining
         */
        public Builder setNsMask(int nsMask) {
            mNsMask = nsMask;
            return this;
        }

        /**
         * Sets the frame type filter.
         *
         * @param frameType Frame type (FRAME_TYPE_MGMT, FRAME_TYPE_CTRL, or FRAME_TYPE_DATA)
         * @return Builder for chaining
         * @throws IllegalArgumentException on build() if frameType is invalid.
         */
        public Builder setFrameType(@FrameType int frameType) {
            mFrameType = frameType;
            return this;
        }

        /**
         * Sets the frame subtype filter.
         *
         * @param frameSubType Frame subtype (0-15)
         * @return Builder for chaining
         * @throws IllegalArgumentException on build() if frameSubType is outside the range 0-15.
         */
        public Builder setFrameSubType(@IntRange(from = 0, to = 15) int frameSubType) {
            mFrameSubType = frameSubType;
            return this;
        }

        /**
         * Sets the CSI capture duration.
         *
         * @param csiDurationSeconds Duration in seconds. Cannot be negative.
         * @return Builder for chaining
         * @throws IllegalArgumentException on build() if duration is negative.
         */
        public Builder setcsiDurationSeconds(int csiDurationSeconds) {
            mcsiDurationSeconds = csiDurationSeconds;
            return this;
        }

        /**
         * Sets the request interval (for active mode).
         *
         * @param requestIntervalMillis Interval in milliseconds. Must be positive if in active mode.
         * @return Builder for chaining
         * @throws IllegalArgumentException on build() if interval is not positive in active mode.
         */
        public Builder setRequestIntervalMillis(int requestIntervalMillis) {
            mRequestIntervalMillis = requestIntervalMillis;
            return this;
        }

        /**
         * Sets the report interval.
         *
         * @param reportIntervalMillis Interval in milliseconds. Must be positive.
         * @return Builder for chaining
         * @throws IllegalArgumentException on build() if interval is not positive.
         */
        public Builder setReportIntervalMillis(int reportIntervalMillis) {
            mReportIntervalMillis = reportIntervalMillis;
            return this;
        }

        /**
     * Sets the MAC addresses to monitor.
     * For passive mode (isActive=false): provide one or more MAC addresses.
     * For active mode (isActive=true): provide a single MAC address in the list.
     *
     * @param macAddresses List of MacAddress objects. Cannot be null, empty, or contain
     *                     the "00:00:00:00:00:00" MAC address.
         * @return Builder for chaining
         * @throws IllegalArgumentException on build() if MAC address requirements are not met.
         */
        public Builder setMacAddresses(List<MacAddress> macAddresses) {
            mMacAddresses = macAddresses != null ? new ArrayList<>(macAddresses) : null;
            return this;
        }

        /**
         * Validates the configuration parameters before building the
         * CsiConfiguration object.
         *
         * @throws IllegalArgumentException if any parameter is invalid.
         */
        private void validate() {
            // Validate frequency
            if (!((mFrequency >= 2412 && mFrequency <= 2484) ||
                  (mFrequency >= 5160 && mFrequency <= 5885) ||
                  (mFrequency >= 5925 && mFrequency <= 7125))) {
                throw new IllegalArgumentException("Invalid frequency. Valid ranges are " +
                        "2412-2484, 5160-5885, or 5925-7125 MHz.");
            }

            // Validate bandwidth
            if (mBandwidth != CHANNEL_WIDTH_20MHZ &&
                mBandwidth != CHANNEL_WIDTH_40MHZ &&
                mBandwidth != CHANNEL_WIDTH_80MHZ &&
                mBandwidth != CHANNEL_WIDTH_160MHZ &&
                mBandwidth != CHANNEL_WIDTH_320MHZ) {
                throw new IllegalArgumentException("Invalid bandwidth: " + mBandwidth);
            }

            // Validate frame type
            if (mFrameType != FRAME_TYPE_MGMT && mFrameType != FRAME_TYPE_CTRL &&
                mFrameType != FRAME_TYPE_DATA) {
                throw new IllegalArgumentException("Invalid frame type: " + mFrameType);
            }

            // Validate frame subtype
            if (mFrameSubType < 0 || mFrameSubType > 15) {
                throw new IllegalArgumentException(
                        "frameSubType must be between 0 and 15");
            }

            // Validate CSI duration
            if (mcsiDurationSeconds < 0) {
                throw new IllegalArgumentException("CSI duration cannot be negative.");
            }

            // Validate report interval
            if (mReportIntervalMillis <= 0) {
                throw new IllegalArgumentException(
                        "Report interval must be a positive value");
            }

            // Validate MAC addresses
            if (mMacAddresses == null || mMacAddresses.isEmpty()) {
                throw new IllegalArgumentException(
                        "MAC address list cannot be null or empty.");
            }
            for (MacAddress mac : mMacAddresses) {
                if (mac.equals(MacAddress.fromString("00:00:00:00:00:00"))) {
                    throw new IllegalArgumentException("Invalid MAC address: " +
                            "00:00:00:00:00:00 is not allowed.");
                }
            }

            // Validate parameters specific to active or passive mode
            if (misActive) {
                // Active mode requires exactly one MAC address
                if (mMacAddresses.size() != 1) {
                    throw new IllegalArgumentException("Exactly one MAC address must " +
                            "be provided in active mode.");
                }
                // Active mode requires a positive request interval
                if (mRequestIntervalMillis <= 0) {
                    throw new IllegalArgumentException("Request interval must be a " +
                            "positive value in active mode");
                }
            }
        }

        /**
         * Builds the {@link CsiConfiguration} after validating all parameters.
         *
         * @return A new {@link CsiConfiguration} instance.
         * @throws IllegalArgumentException if any of the configuration parameters are
         *         invalid.
         */
        public CsiConfiguration build() {
            validate();
            return new CsiConfiguration(
                    misActive,
                    mFrequency,
                    mBandwidth,
                    mNsMask,
                    mFrameType,
                    mFrameSubType,
                    mcsiDurationSeconds,
                    mRequestIntervalMillis,
                    mReportIntervalMillis,
                    mMacAddresses);
        }
    }
}
