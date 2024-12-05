/*
 * Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.qualcomm.qti.wifiextend;

oneway interface IVendorEventCallback
{
    void onThermalChanged(String ifname, int level);
    void onCongestionChanged(String ifname, int percentage);
}