package dev.shizzi;

import android.os.ParcelFileDescriptor;

interface ITetherService {

    String start(boolean logging, String vpnMode, String wiredTethering, boolean isWifiHotspotOn);

    void setLogging(boolean enabled);

    String stop();

    String releaseOrphanedDownstream(in String[] types);

    String getStatus();

    String runProbes(boolean attemptTethering, int availabilityTimeoutMs);

    void clearLog();

    String checkCompatibility();

    String installTetheringApex(in ParcelFileDescriptor apex);

    String rebootDevice();

    int getContractVersion();
}
