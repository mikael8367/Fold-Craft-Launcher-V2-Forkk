package com.tungsten.fcl.game;

import com.tungsten.fclcore.util.versioning.GameVersionNumber;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Version-aware Minecraft options used by the launcher's FPS and RAM profiles.
 *
 * Minecraft has renamed or changed several options between releases. Keeping the
 * schema here makes the mapping independently testable without launching a game.
 */
public final class PerformanceProfileOptions {

    private static final List<String> MANAGED_OPTION_KEYS = Collections.unmodifiableList(Arrays.asList(
            "viewDistance",
            "renderDistance",
            "fancyGraphics",
            "graphics",
            "graphicsMode",
            "graphicsPreset",
            "ao",
            "ambientOcclusion",
            "particles",
            "mipmapLevels",
            "entityShadows",
            "clouds",
            "renderClouds",
            "maxFps",
            "biomeBlendRadius",
            "entityDistanceScaling",
            "simulationDistance"
    ));

    private PerformanceProfileOptions() {
    }

    public static List<String> getManagedOptionKeys() {
        return MANAGED_OPTION_KEYS;
    }

    /**
     * Returns only options that should be overridden for an enabled profile.
     * Options absent from this map are removed from the managed set while the
     * profile is active, except maxFps: FPS-only mode intentionally preserves
     * the user's configured cap.
     */
    public static Map<String, String> buildProfile(GameVersionNumber version,
                                                   boolean fpsBooster,
                                                   boolean ramSaver) {
        if (version == null || (!fpsBooster && !ramSaver)) {
            return Collections.emptyMap();
        }

        Map<String, String> settings = new LinkedHashMap<>();
        int renderDistance = ramSaver ? 4 : 6;
        if (version.compareTo("1.7.2") < 0) {
            settings.put("viewDistance", String.valueOf(renderDistance));
        } else {
            settings.put("renderDistance", String.valueOf(renderDistance));
        }

        // fancyGraphics was replaced by graphicsMode in 1.16, then by
        // graphicsPreset in 1.21.11. The value formats differ by generation.
        if (version.compareTo("1.16") < 0) {
            settings.put("fancyGraphics", "false");
        } else if (version.compareTo("1.21.11") < 0) {
            settings.put("graphicsMode", "0");
        } else {
            settings.put("graphicsPreset", "fast");
        }

        // Older clients use a boolean, many intervening releases accept numeric
        // levels, and current releases use a boolean again.
        if (version.compareTo("1.8") < 0 || version.compareTo("1.21.11") >= 0) {
            settings.put("ao", "false");
        } else {
            settings.put("ao", "0");
        }

        settings.put("particles", "2");

        if (version.compareTo("1.7.2") >= 0) {
            settings.put("mipmapLevels", "0");
        }
        if (version.compareTo("1.8.1") >= 0) {
            settings.put("entityShadows", "false");
        }

        // The clouds option was renamed from "clouds" to "renderClouds" in 1.8.
        if (version.compareTo("1.8") >= 0) {
            settings.put("renderClouds", "false");
        } else {
            settings.put("clouds", "false");
        }

        if (version.compareTo("1.13") >= 0) {
            settings.put("biomeBlendRadius", "0");
        }
        if (version.compareTo("1.16") >= 0) {
            settings.put("entityDistanceScaling", ramSaver ? "0.5" : "0.75");
        }
        if (version.compareTo("1.18") >= 0) {
            settings.put("simulationDistance", ramSaver ? "4" : "6");
        }

        // maxFps was added in 1.7.2. On older clients we avoid writing an
        // unrelated/unsupported key instead of assuming their legacy FPS enum.
        if (ramSaver && version.compareTo("1.7.2") >= 0) {
            settings.put("maxFps", "60");
        }

        return Collections.unmodifiableMap(settings);
    }
}
