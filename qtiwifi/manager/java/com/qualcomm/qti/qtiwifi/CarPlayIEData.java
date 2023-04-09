/*
 *   Changes from Qualcomm Innovation Center are provided under the following license:
 *   Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 *   SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.qualcomm.qti.qtiwifi;


import android.os.Parcel;
import android.os.Parcelable;

/**
 * Class for CarplayIE info structure
 */
public final class CarPlayIEData implements Parcelable {
    private String mVendorIE;
    private String mAssocRespElement;
    private String mAccessNetworkType;
    private String mEsr;
    private String mInternet;
    private String mVenueType;
    private String mVenueGroup;
    private String mHessid;

    public String getVendorIe() {
        return mVendorIE;
    }

    public String getAssocRespElement() {
        return mAssocRespElement;
    }

    public String getAccessNetworkType() {
        return mAccessNetworkType;
    }

    public String getEsr() {
        return mEsr;
    }

    public String getInternet() {
        return mInternet;
    }

    public String getVenueType() {
        return mVenueType;
    }

    public String getVenueGroup() {
        return mVenueGroup;
    }

    public String getHessid() {
        return mHessid;
    }

    public void setVendorIE(String vendorIE) {
        mVendorIE = vendorIE;
    }

    public void setAssocRespElement(String assocRespElement) {
        mAssocRespElement = assocRespElement;
    }

    public void setAccessNetworkType(String accessNetworkType) {
        mAccessNetworkType = accessNetworkType;
    }

    public void setEsr(String esr) {
        mEsr = esr;
    }

    public void setInternet(String internet) {
        mInternet = internet;
    }

    public void setVenueType(String venueType) {
        mVenueType = venueType;
    }

    public void setVenueGroup(String venueGroup) {
        mVenueGroup = venueGroup;
    }

    public void setHessid(String hessid) {
        mHessid = hessid;
    }

    public static final Parcelable.Creator<CarPlayIEData> CREATOR
         = new Parcelable.Creator<CarPlayIEData>() {
        public CarPlayIEData createFromParcel(Parcel in) {
            return new CarPlayIEData(in);
        }

        public CarPlayIEData[] newArray(int size) {
            return new CarPlayIEData[size];
        }
    };

    public CarPlayIEData() {
    }

    private CarPlayIEData(Parcel in) {
        readFromParcel(in);
    }


    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mVendorIE);
        out.writeString(mAssocRespElement);
	out.writeString(mAccessNetworkType);
        out.writeString(mEsr);
        out.writeString(mInternet);
        out.writeString(mVenueType);
        out.writeString(mVenueGroup);
        out.writeString(mHessid);
    }

    public void readFromParcel(Parcel in) {
        mVendorIE = in.readString();
        mAssocRespElement = in.readString();
	mAccessNetworkType = in.readString();
        mEsr = in.readString();
        mInternet = in.readString();
        mVenueType = in.readString();
        mVenueGroup = in.readString();
        mHessid = in.readString();
    }

    public int describeContents() {
        return 0;
    }
}


