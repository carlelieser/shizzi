package dev.shizzi;

import android.os.ParcelFileDescriptor;

interface ITetherService {

    String start(boolean logging);

    void setLogging(boolean enabled);

    String stop();

    String getStatus();

    String runProbes(boolean attemptTethering, int availabilityTimeoutMs);

    void clearLog();

    String checkCompatibility();

    String installTetheringApex(in ParcelFileDescriptor apex);

    String rebootDevice();

    String grantBackgroundStart();

    boolean hasBackgroundStart();

    int getContractVersion();
}
