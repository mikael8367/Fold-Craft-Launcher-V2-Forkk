package com.tungsten.fcl.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds conservative JVM-only performance options.
 *
 * This class intentionally does not read or write Minecraft options.txt.
 * JVM flags can reduce garbage-collection pauses or heap retention, but cannot
 * guarantee a frame-rate increase or eliminate memory use from mods/native code.
 */
public final class PerformanceRuntimeTuning {

    private PerformanceRuntimeTuning() {
    }

    /**
     * Returns JVM arguments for the enabled profiles.
     *
     * @param javaMajorVersion selected Java major version
     * @param fpsBooster whether the FPS profile is enabled
     * @param ramSaver whether the RAM profile is enabled
     */
    public static List<String> buildJvmArguments(int javaMajorVersion,
                                                 boolean fpsBooster,
                                                 boolean ramSaver) {
        if ((!fpsBooster && !ramSaver) || javaMajorVersion < 8) {
            return Collections.emptyList();
        }

        List<String> arguments = new ArrayList<>();
        arguments.add("-XX:+UseG1GC");

        if (fpsBooster) {
            // A tighter pause goal favors frame pacing, at the cost of potentially
            // more frequent GC work. This is a target, not a maximum-pause guarantee.
            arguments.add("-XX:MaxGCPauseMillis=50");
            arguments.add("-XX:+ParallelRefProcEnabled");

            // With G1, explicit System.gc() requests can trigger a concurrent cycle
            // instead of a stop-the-world full collection, reducing mod-induced stalls.
            arguments.add("-XX:+ExplicitGCInvokesConcurrent");
        } else {
            // RAM-only mode avoids the more aggressive FPS pause target.
            arguments.add("-XX:MaxGCPauseMillis=200");
        }

        if (ramSaver) {
            // Encourage the JVM to shrink unused heap more readily after collections.
            // This can trade some allocation/GC throughput for a lower retained heap.
            arguments.add("-XX:MinHeapFreeRatio=10");
            arguments.add("-XX:MaxHeapFreeRatio=25");

            // String deduplication is enabled only on modern Java runtimes, where
            // it is standard with G1 and may reduce duplicate string storage in modpacks.
            if (javaMajorVersion >= 17) {
                arguments.add("-XX:+UseStringDeduplication");
            }
        }

        return Collections.unmodifiableList(arguments);
    }
}
