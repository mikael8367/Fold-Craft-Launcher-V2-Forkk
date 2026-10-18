package com.tungsten.fcl.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds JVM-only performance options for Android Minecraft sessions.
 *
 * Performance profiles never change Minecraft's render-buffer dimensions or
 * options.txt. These JVM flags are best-effort hints, not a guarantee of more
 * FPS when the bottleneck is the GPU, CPU, thermal throttling, native code,
 * or a particular mod.
 */
public final class PerformanceRuntimeTuning {

    private PerformanceRuntimeTuning() {
    }

    /**
     * Compatibility method for existing callers. Always preserve resolution.
     */
    public static float getRenderScale(boolean fpsBooster, boolean forceResolution) {
        return 1.0f;
    }

    /**
     * Compatibility overload for older callers. Ultra mode also preserves the
     * exact render-buffer dimensions chosen by the user/launcher.
     */
    public static float getRenderScale(boolean fpsBooster, boolean ultraFps, boolean forceResolution) {
        return 1.0f;
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
            // A pause goal is only a target; very low targets can waste CPU
            // on repeated collections. Keep the profile conservative.
            arguments.add("-XX:MaxGCPauseMillis=50");
            arguments.add("-XX:+ParallelRefProcEnabled");
            arguments.add("-XX:+ExplicitGCInvokesConcurrent");
        } else {
            arguments.add("-XX:MaxGCPauseMillis=200");
        }

        // When FPS mode is enabled, avoid RAM-only heap-shrinking and
        // string-deduplication overhead competing with the frame workload.
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
