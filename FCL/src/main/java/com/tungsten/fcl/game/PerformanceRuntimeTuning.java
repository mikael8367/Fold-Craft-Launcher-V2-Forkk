package com.tungsten.fcl.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds conservative JVM-only performance options for Android Minecraft sessions.
 *
 * Performance profiles never change Minecraft's render-buffer dimensions or options.txt.
 * They can reduce GC-related frame-time spikes, but cannot guarantee a frame-rate increase
 * when the bottleneck is the GPU, CPU, renderer, native code, or a particular mod.
 */
public final class PerformanceRuntimeTuning {

    private PerformanceRuntimeTuning() {
    }

    /**
     * Compatibility overload used by the standard FPS Booster and RAM Saver.
     */
    public static List<String> buildJvmArguments(int javaMajorVersion,
                                                 boolean fpsBooster,
                                                 boolean ramSaver) {
        return buildJvmArguments(javaMajorVersion, fpsBooster, false, ramSaver);
    }

    /**
     * Builds JVM arguments for normal FPS, Ultra FPS, or RAM Saver.
     *
     * Ultra mode is deliberately stricter only on modern Java runtimes where the
     * G1 flags used below are supported. G1 pause targets are soft goals, not guarantees.
     */
    public static List<String> buildJvmArguments(int javaMajorVersion,
                                                 boolean fpsBooster,
                                                 boolean fpsBoosterUltra,
                                                 boolean ramSaver) {
        boolean fpsProfile = fpsBooster || fpsBoosterUltra;
        if ((!fpsProfile && !ramSaver) || javaMajorVersion < 8) {
            return Collections.emptyList();
        }

        List<String> arguments = new ArrayList<>();
        arguments.add("-XX:+UseG1GC");

        if (fpsProfile) {
            // A tighter pause target may reduce GC-related hitching, at some throughput cost.
            int pauseTarget = fpsBoosterUltra
                    ? (javaMajorVersion >= 17 ? 40 : 45)
                    : 50;
            arguments.add("-XX:MaxGCPauseMillis=" + pauseTarget);
            arguments.add("-XX:+ParallelRefProcEnabled");
            arguments.add("-XX:+ExplicitGCInvokesConcurrent");

            if (fpsBoosterUltra && javaMajorVersion >= 17) {
                // For modern runtimes, begin the first G1 marking cycles earlier.
                // G1 can later adapt its threshold to observed allocation behavior.
                arguments.add("-XX:InitiatingHeapOccupancyPercent=30");
            }
        } else {
            arguments.add("-XX:MaxGCPauseMillis=200");
        }

        // Do not spend additional CPU on heap shrinking/deduplication while FPS mode
        // is active; RAM Saver applies these trade-offs only when FPS mode is off.
        if (ramSaver && !fpsProfile) {
            arguments.add("-XX:MinHeapFreeRatio=10");
            arguments.add("-XX:MaxHeapFreeRatio=25");
            if (javaMajorVersion >= 17) {
                arguments.add("-XX:+UseStringDeduplication");
            }
        }

        return Collections.unmodifiableList(arguments);
    }

    /**
     * Compatibility helper: performance profiles preserve the render resolution chosen
     * by the user and never modify options.txt.
     */
    public static float getRenderScale(boolean fpsBooster, boolean forceResolution) {
        return 1.0f;
    }

    /**
     * Compatibility helper: Ultra FPS also preserves the user's render resolution.
     */
    public static float getRenderScale(boolean fpsBooster, boolean ultraFps, boolean forceResolution) {
        return 1.0f;
    }
}
