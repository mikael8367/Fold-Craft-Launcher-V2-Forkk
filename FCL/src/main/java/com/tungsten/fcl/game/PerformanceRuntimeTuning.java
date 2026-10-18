package com.tungsten.fcl.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds JVM and render-buffer performance options.
 *
 * This class intentionally does not read or write Minecraft options.txt.
 * Render scaling can help GPU-bound devices, but cannot guarantee gains in
 * CPU-bound worlds or when a renderer/mod is the bottleneck.
 */
public final class PerformanceRuntimeTuning {

    private PerformanceRuntimeTuning() {
    }

    /**
     * Strong GPU-saving profile: 0.40 renders 16% of the original pixel count.
     * An explicit user-selected fixed resolution always takes precedence.
     */
    public static float getRenderScale(boolean fpsBooster, boolean forceResolution) {
        return getRenderScale(fpsBooster, false, forceResolution);
    }

    /**
     * Ultra mode is opt-in because 30% width/height means only 9% of the
     * original pixel count. An explicit fixed resolution always wins.
     */
    public static float getRenderScale(boolean fpsBooster, boolean ultraFps, boolean forceResolution) {
        if (!fpsBooster || forceResolution) {
            return 1.0f;
        }
        return ultraFps ? 0.30f : 0.40f;
    }

    public static List<String> buildJvmArguments(int javaMajorVersion,
                                                 boolean fpsBooster,
                                                 boolean ramSaver) {
        if ((!fpsBooster && !ramSaver) || javaMajorVersion < 8) {
            return Collections.emptyList();
        }

        List<String> arguments = new ArrayList<>();
        arguments.add("-XX:+UseG1GC");

        if (fpsBooster) {
            // This is a target, not a guarantee; it favors shorter GC pauses.
            arguments.add("-XX:MaxGCPauseMillis=50");
            arguments.add("-XX:+ParallelRefProcEnabled");
            // Prefer concurrent work for explicit System.gc() requests.
            arguments.add("-XX:+ExplicitGCInvokesConcurrent");
        } else {
            arguments.add("-XX:MaxGCPauseMillis=200");
        }

        // FPS mode wins when both profiles are enabled: avoid RAM-only heap
        // shrinking and string deduplication work competing for CPU time.
        if (ramSaver && !fpsBooster) {
            arguments.add("-XX:MinHeapFreeRatio=10");
            arguments.add("-XX:MaxHeapFreeRatio=25");
            if (javaMajorVersion >= 17) {
                arguments.add("-XX:+UseStringDeduplication");
            }
        }

        return Collections.unmodifiableList(arguments);
    }
}
