/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.librca.models;

public class InputStats {
    public static class RadioConfig {
        public int channel;
        public int channelWidth;
        public int txPower;
        public String phy;
    }
    public class Association{
        /** Rate information for receive/transmit data rate*/
        public class Rate {
            public long bitRate;
            public int chWidth;
            public int mcs;
        }
        public long connected;
        public long inactive;
        public int rssi;
        public long rxBytes;
        public long rxPackets;
        public Rate rxRate;
        public long txBytes;
        public long txDuration;
        public long txFailed;
        public long txPackets;
        public Rate txRate;
        public long txRetries;
        public int ackSignal;
        public int ackSignalAvg;
        public long txPacketsCounters;
        public long txErrorsCounters;
        public long txDroppedCounters;
        public long activeMsRadio;
        public long busyMsRadio;
        public long noiseRadio;
        public long receiveMsRadio;
        public long transmitMsRadio;
        public long timeStamp;
    }
    public String bssid;
    public String station;
    public RadioConfig radioConfig;
    Association association;
}
