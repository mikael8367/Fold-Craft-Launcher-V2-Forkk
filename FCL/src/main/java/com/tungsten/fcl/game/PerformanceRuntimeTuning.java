package com.tungsten.fcl.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loader-independent JVM performance profiles for Android Minecraft.
 *
 * These arguments are appended at the common launcher layer, so they apply to
 * vanilla and every mod loader using the same JVM startup path (Forge, NeoForge,
 * Fabric, Quilt, and compatible loaders). They never rewrite options.txt or
 * loader-owned configuration files.
 *
 * GC pause targets are soft goals, not FPS guarantees. A GPU-, renderer-, or
 * mod-limited game may not gain frames from JVM tuning alone.
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
     * Build runtime arguments without depending on a specific mod loader.
     * Ultra mode uses more aggressive G1 pause goals only; it does not force
     * unsupported JVM flags or override the user's selected heap size.
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
            int pauseTarget;
            if (fpsBoosterUltra) {
                // Java 17+ has more modern G1 behavior; Java 8 keeps a less
                // aggressive target to avoid excessive GC work on older runtimes.
                pauseTarget = javaMajorVersion >= 17 ? 30 : 40;
            } else {
                pauseTarget = 50;
            }

            arguments.add("-XX:MaxGCPauseMillis=" + pauseTarget);
            arguments.add("-XX:+ParallelRefProcEnabled");
            arguments.add("-XX:+ExplicitGCInvokesConcurrent");

            if (fpsBoosterUltra && javaMajorVersion >= 17) {
                // Start concurrent marking earlier to reduce the risk of G1
                // falling behind during allocation-heavy modded gameplay.
                arguments.add("-XX:InitiatingHeapOccupancyPercent=25");
            }
        } else {
            arguments.add("-XX:MaxGCPauseMillis=200");
        }

        // Heap shrinking and string deduplication can use extra CPU. Keep them
        // exclusive to RAM Saver so the FPS profiles prioritize frame pacing.
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
     * Native render-buffer scale for the standard FPS profile.
     * This is applied by the launcher to the output buffer only; it does not
     * rewrite options.txt or a mod-loader configuration file.
     */
    public static float getRenderScale(boolean fpsBooster, boolean forceResolution) {
        if (forceResolution) {
            return 1.0f;
        }
        return fpsBooster ? 0.85f : 1.0f;
    }

    /**
     * Ultra mode reduces both render-buffer dimensions to 70%, which means
     * roughly 49% of the pixels of full resolution. This can materially help
     * GPU-bound phones, at the cost of a softer image. User-forced resolution
     * always wins and is never scaled behind their back.
     */
    public static float getRenderScale(boolean fpsBooster, boolean ultraFps, boolean forceResolution) {
        if (forceResolution) {
            return 1.0f;
        }
        if (ultraFps) {
            return 0.70f;
        }
        return fpsBooster ? 0.85f : 1.0f;
    }
}
