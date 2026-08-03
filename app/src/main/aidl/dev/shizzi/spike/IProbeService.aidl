package dev.shizzi.spike;

/**
 * Privileged probe surface, executed inside the Shizuku-spawned shell process.
 *
 * Deliberately tiny: the spike answers viability questions, so the only
 * operations are "run the probes" and "tear everything down". The session
 * lifecycle of the real product (R2.2 start/stop/getStatus) is not modelled
 * here.
 */
interface IProbeService {

    /**
     * Runs the full probe sequence and returns a JSON report.
     *
     * Blocking. Never throws across the binder: every failure is captured as a
     * probe result inside the report so the UI can surface it verbatim (R7.5).
     *
     * @param socksHost unused by the spike; reserved so the contract does not
     *                  change shape when the datapath lands.
     */
    String runProbes(boolean attemptTethering, int availabilityTimeoutMs);

    /**
     * Releases anything the probe run left behind, in fail-closed order:
     * downstream first, then the test network (R6.1).
     *
     * Safe to call when no probe run is active.
     */
    String teardown();

    /** Contract version, checked by the app to detect a stale shell process (R2.5). */
    int getContractVersion();
}
