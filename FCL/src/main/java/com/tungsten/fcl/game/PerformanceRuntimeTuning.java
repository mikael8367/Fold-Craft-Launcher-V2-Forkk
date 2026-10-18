package com.tungsten.fcl.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds conservative JVM-only performance options.
 *
 * This class intentionally does not read or write Minecraft options.txt.
 * JVM flags can improve garbage-collection behavior, but cannot guarantee a
 * frame-rate increase or eliminate memory use caused by mods and native code.
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
            // Favor shorter pauses to reduce GC-related frame-time spikes.
            arguments.add("-XX:MaxGCPauseMillis=100");
            arguments.add("-XX:+ParallelRefProcEnabled");
        } else {
            // Avoid an unnecessarily aggressive pause target in RAM-only mode.
            arguments.add("-XX:MaxGCPauseMillis=200");
        }

        if (ramSaver) {
            // Let the JVM return more unused heap after collection where possible.
            arguments.add("-XX:MinHeapFreeRatio=10");
            arguments.add("-XX:MaxHeapFreeRatio=35");

            // String deduplication is enabled only on modern Java runtimes, where
            // it is a standard G1 option and does not depend on legacy flags.
            if (javaMajorVersion >= 17) {
                arguments.add("-XX:+UseStringDeduplication");
            }
        }

        return Collections.unmodifiableList(arguments);
    }
}
