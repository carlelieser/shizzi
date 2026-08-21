package dev.shizzi;

/** Privileged session surface, executed inside the Shizuku-spawned shell process. */
interface ITetherService {

    /**
     * Brings up protected tethering and leaves it up. Blocking.
     *
     * Never throws across the binder: failures are reported in the returned
     * JSON so the UI can surface them verbatim (R7.5). Tears down on any
     * failure, so a failed start never leaves clients on a physical
     * upstream (R6.1). [logging] carries the setting across so the session's
     * own entries are governed from the first line, not the first toggle.
     */
    String start(boolean logging);

    /**
     * Turns session logging on or off in this process.
     *
     * Separate from [start] because the setting can change mid-session, and
     * this process cannot read the app's DataStore.
     */
    void setLogging(boolean enabled);

    /**
     * Tears the session down in fail-closed order: downstream, then the test
     * network (R6.1). Safe to call when no session is active.
     */
    String stop();

    /**
     * Current session state as JSON. Cheap enough to poll every second — the
     * one field needing a dumpsys is rate-limited behind its own interval.
     */
    String getStatus();

    /** Diagnostic sequence; acquires and releases its own resources. */
    String runProbes(boolean attemptTethering, int availabilityTimeoutMs);

    /**
     * Empties this process's log file.
     *
     * The app can read /data/local/tmp but not write it, so clearing from the
     * UI alone would leave the shell's entries — most of the history — behind.
     */
    void clearLog();

    /**
     * Resolves the two hidden APIs the app cannot work without, and nothing
     * else. Creates no TUN and starts no hotspot, so it is cheap enough to run
     * from onboarding.
     *
     * Here rather than in the app process because reflection onto these
     * surfaces answers for the calling UID: run as an ordinary app it reports
     * both absent on every release, including those the app works on.
     */
    String checkCompatibility();

    /**
     * Stages the tethering APEX at [localPath] for install on the next boot.
     *
     * Here rather than in the app process because the install needs shell UID
     * and a path under /data/local/tmp, which the app can read but not write —
     * so this side does the copy as well as the install.
     *
     * Never throws across the binder: pm's own output is the only useful
     * account of a rejected APEX, and it comes back verbatim in the JSON (R7.5).
     */
    String installTetheringApex(String localPath);

    /** Checked by the app to detect a stale shell process (R2.5). */
    int getContractVersion();
}
