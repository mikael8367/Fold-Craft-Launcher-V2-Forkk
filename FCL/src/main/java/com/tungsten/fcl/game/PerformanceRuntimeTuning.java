package com.tungsten.fcl.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds conservative JVM-only performance options.
 *
 * This class intentionally does not read or write Minecraft options.txt.
 * JVM flags and a lower render buffer can improve frame pacing on GPU-bound
 * devices, but cannot guarantee a frame-rate increase in CPU-bound worlds.
 */
public final class PerformanceRuntimeTuning {

    private PerformanceRuntimeTuning() {
    }

    /**
     * Aggressive low-end-device render scale. 0.50 means the game renders
     * one quarter of full-resolution pixels, then Android scales the buffer
     * to the visible TextureView. Explicit fixed resolution remains untouched.
     */
    public static float getRenderScale(boolean fpsBooster, boolean forceResolution) {
        return fpsBooster && !forceResolution ? 0.50f : 1.0f;
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
            // A pause goal is only a GC target, not a guarantee.
            arguments.add("-XX:MaxGCPauseMillis=50");
            arguments.add("-XX:+ParallelRefProcEnabled");
            // Avoid a full stop-the-world collection for explicit System.gc() requests.
            arguments.add("-XX:+ExplicitGCInvokesConcurrent");
        } else {
            arguments.add("-XX:MaxGCPauseMillis=200");
        }

        if (ramSaver) {
            // Avoid aggressive heap shrinking when FPS mode is also enabled:
            // frequent collections can hurt frame time on a low-end CPU.
            if (!fpsBooster) {
                arguments.add("-XX:MinHeapFreeRatio=10");
                arguments.add("-XX:MaxHeapFreeRatio=25");
            }
            if (javaMajorVersion >= 17) {
                arguments.add("-XX:+UseStringDeduplication");
            }
        }

        return Collections.unmodifiableList(arguments);
    }
}
