package com.tungsten.fcl.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loader-independent JVM performance profiles for Android Minecraft.
 *
 * These options affect the game JVM only. They do not rewrite Minecraft
 * options.txt, loader-owned configuration files, or the configured heap limit.
 *
 * Garbage-collection settings are trade-offs, not a guaranteed FPS or RAM
 * reduction. Actual results depend on Java version, mods, renderer and device.
 */
public final class PerformanceRuntimeTuning {

    private PerformanceRuntimeTuning() {
    }

    /** Compatibility overload used by the standard FPS Booster and RAM Saver. */
    public static List<String> buildJvmArguments(int javaMajorVersion,
                                                 boolean fpsBooster,
                                                 boolean ramSaver) {
        return buildJvmArguments(javaMajorVersion, fpsBooster, false, ramSaver);
    }

    /**
     * Builds runtime arguments for the requested profiles.
     *
     * Ultra mode tightens G1's pause target. RAM Saver independently enables
     * heap shrinking, less persistent soft-reference caches, fewer GC workers,
     * and string deduplication on modern Java. When both profiles are active,
     * the memory arguments are retained instead of being silently dropped.
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
        // G1 is required by the String Deduplication option used on newer Java.
        arguments.add("-XX:+UseG1GC");

        if (fpsProfile) {
            int pauseTarget;
            if (fpsBoosterUltra) {
                // Java 17+ has newer G1 behavior; keep legacy runtimes less aggressive.
                pauseTarget = javaMajorVersion >= 17 ? 30 : 40;
            } else {
                pauseTarget = 50;
            }

            arguments.add("-XX:MaxGCPauseMillis=" + pauseTarget);
            arguments.add("-XX:+ParallelRefProcEnabled");
            arguments.add("-XX:+ExplicitGCInvokesConcurrent");

            if (fpsBoosterUltra && javaMajorVersion >= 17) {
                // Begin marking earlier to handle allocation-heavy modded sessions.
                arguments.add("-XX:InitiatingHeapOccupancyPercent=25");
            }
        } else {
            arguments.add("-XX:MaxGCPauseMillis=200");
        }

        if (ramSaver) {
            // Encourage heap commitment to shrink after collection where Xms permits.
            // This never changes -Xmx or the user's configured min/max heap values.
            arguments.add("-XX:MinHeapFreeRatio=5");
            arguments.add("-XX:MaxHeapFreeRatio=20");

            // Let soft-reference-backed caches expire sooner under memory pressure.
            // This trades some cache reuse for lower retention; some mods may reload data.
            arguments.add("-XX:SoftRefLRUPolicyMSPerMB=100");

            // Reduce native thread-stack overhead on low-memory phones. Collection can
            // take longer, so do not apply this to the FPS-only profile.
            arguments.add("-XX:ParallelGCThreads=2");
            arguments.add("-XX:ConcGCThreads=1");

            // On modern G1 runtimes, deduplicate repeated Strings commonly held by mods.
            if (javaMajorVersion >= 17) {
                arguments.add("-XX:+UseStringDeduplication");

                // Start reclamation earlier for the RAM profile. Ultra mode already
                // uses a tighter 25% threshold, so avoid conflicting duplicate flags.
                if (!fpsBoosterUltra) {
                    arguments.add("-XX:InitiatingHeapOccupancyPercent=30");
                }
            }
        }

        return Collections.unmodifiableList(arguments);
    }

    /**
     * Native render-buffer scale for the standard FPS profile.
     * This affects the output buffer only and does not rewrite options.txt.
     */
    public static float getRenderScale(boolean fpsBooster, boolean forceResolution) {
        if (forceResolution) {
            return 1.0f;
        }
        return fpsBooster ? 0.85f : 1.0f;
    }

    /**
     * Ultra mode uses 70% of each render-buffer dimension (49% of the pixels).
     * A user-forced resolution always takes precedence.
     */
    public static float getRenderScale(boolean fpsBooster,
                                       boolean ultraFps,
                                       boolean forceResolution) {
        if (forceResolution) {
            return 1.0f;
        }
        if (ultraFps) {
            return 0.70f;
        }
        return fpsBooster ? 0.85f : 1.0f;
    }
}
