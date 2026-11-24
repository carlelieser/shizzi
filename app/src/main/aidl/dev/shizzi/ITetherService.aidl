package dev.shizzi;

import android.os.IBinder;
import android.os.ParcelFileDescriptor;

interface ITetherService {

    String start(boolean logging, String vpnMode, String wiredTethering, boolean isWifiHotspotOn, String dnsServers) = 0;

    void setLogging(boolean enabled) = 1;

    String stop() = 2;

    String releaseOrphanedDownstream(in String[] types) = 3;

    String getStatus() = 4;

    String runProbes(boolean attemptTethering, int availabilityTimeoutMs) = 5;

    void clearLog() = 6;

    String checkCompatibility() = 7;

    String installTetheringApex(in ParcelFileDescriptor apex) = 8;

    String rebootDevice() = 9;

    int getContractVersion() = 10;

    // The adb-started helper learns that an app process holds its Binder (stops re-sending it).
    void attach(IBinder client) = 11;

    // Shizuku calls this on an old helper when a new version starts or the app unbinds with remove.
    void destroy() = 16777114;
}
