package dev.shizzi;

/**
 * Privileged session surface, executed inside the Shizuku-spawned shell process.
 *
 * Shaped per R2.2: start, stop, getStatus. The probe sequence remains available
 * as a diagnostic because it is how every failure in this stack has been
 * diagnosed, but it is no longer the primary operation.
 */
interface ITetherService {

    /**
     * Brings up protected tethering and leaves it up.
     *
     * Restarts the downstream, creates the TUN, starts the userspace datapath,
     * and verifies the tethering stack selected the owned interface. Returns a
     * JSON status. Blocking, and never throws across the binder: failures are
     * reported in the status so the UI can surface them verbatim (R7.5).
     *
     * On any failure the session is torn down before returning, so a failed
     * start never leaves clients on a physical upstream (R6.1).
     *
     * [logging] carries the setting across, so the session's own entries are
     * governed from the first line rather than from the first toggle.
     */
    String start(boolean logging);

    /**
     * Turns session logging on or off in this process.
     *
     * Separate from [start] because the setting can change while a session is
     * running, and the shell process cannot read the app's DataStore: without
     * a push, a toggle would not reach the process writing most of the entries
     * until the next start.
     */
    void setLogging(boolean enabled);

    /**
     * Tears the session down in fail-closed order: downstream first, then the
     * test network (R6.1). Safe to call when no session is active.
     */
    String stop();

    /**
     * Current session state as JSON, without changing anything.
     *
     * Cheap enough to poll every second. Most of what it reports is held by
     * this process or read from /proc, and the one field that needs a dumpsys
     * — the count of associated devices — is rate-limited behind its own
     * refresh interval rather than read per call.
     */
    String getStatus();

    /**
     * Runs the full diagnostic probe sequence and returns a JSON report.
     *
     * Independent of the session: acquires and releases its own resources.
     */
    String runProbes(boolean attemptTethering, int availabilityTimeoutMs);

    /**
     * Empties this process's log file.
     *
     * Needed because the shell writes to /data/local/tmp, which the app process
     * can read but not write: without a call across the binder, clearing from
     * the UI would silently empty only the app's half of the history and leave
     * the shell's entries — most of what is worth reading — in place.
     */
    void clearLog();

    /** Contract version, checked by the app to detect a stale shell process (R2.5). */
    int getContractVersion();
}
