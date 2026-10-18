package com.tungsten.fcl.game;

import com.tungsten.fclcore.util.versioning.GameVersionNumber;

import java.util.Arrays;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
     * Preferences must follow the options.txt file, not the version ID.
     * Multiple instances can share .minecraft/options.txt when game-directory
     * isolation is disabled; in that case their profile and backup state is shared.
     */
    public static String statePreferenceKeyFor(String optionsPath) {
        if (optionsPath == null || optionsPath.trim().isEmpty()) {
            throw new IllegalArgumentException("optionsPath must not be empty");
        }
        String normalizedPath = new File(optionsPath).getAbsoluteFile().toPath().normalize().toString();
        String encodedPath = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(normalizedPath.getBytes(StandardCharsets.UTF_8));
        return "performanceRamSaverApplied:" + encodedPath;
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
        // These thresholds include the exact snapshot when Mojang introduced the
        // option. GameVersionNumber.compareTo(release) treats some snapshots as
        // earlier than their associated release, even when they already use the
        // new options.txt schema.
        boolean hasModernDistanceKey = version.isAtLeast("1.7.2", "13w38a");
        boolean hasCloudStatusKey = version.isAtLeast("1.8", "14w25a");
        boolean hasBiomeBlend = version.isAtLeast("1.13", "18w15a");
        boolean hasEntityDistanceScaling = version.isAtLeast("1.16", "20w17a");
        boolean hasSimulationDistance = version.isAtLeast("1.18", "21w38a");
        boolean hasGraphicsPreset = version.isAtLeast("1.21.11", "25w41a");

        if (!hasModernDistanceKey) {
            // Legacy viewDistance is an enum (0=Far ... 3=Tiny), not chunks.
            // Writing 4/6 here is outside the vanilla 0-3 range.
            settings.put("viewDistance", "3");
        } else {
            settings.put("renderDistance", String.valueOf(renderDistance));
        }

        // fancyGraphics was replaced in the 1.16 pre-release series by
        // graphicsMode, which was later replaced by graphicsPreset in 25w41a.
        if (version.compareTo("1.16-pre1") < 0) {
            settings.put("fancyGraphics", "false");
        } else if (!hasGraphicsPreset) {
            settings.put("graphicsMode", "0");
        } else {
            settings.put("graphicsPreset", "fast");
        }

        // Older clients use a boolean, intervening releases accept numeric
        // levels, and the latest graphics preset schema uses a boolean again.
        if (!hasCloudStatusKey || hasGraphicsPreset) {
            settings.put("ao", "false");
        } else {
            settings.put("ao", "0");
        }

        settings.put("particles", "2");

        if (hasModernDistanceKey) {
            settings.put("mipmapLevels", "0");
        }
        if (version.compareTo("1.8.1-pre4") >= 0) {
            settings.put("entityShadows", "false");
        }

        // The clouds option was renamed from "clouds" to "renderClouds" in
        // snapshot 14w25a, before the 1.8 release.
        if (hasCloudStatusKey) {
            settings.put("renderClouds", "false");
        } else {
            settings.put("clouds", "false");
        }

        if (hasBiomeBlend) {
            settings.put("biomeBlendRadius", "0");
        }
        if (hasEntityDistanceScaling) {
            settings.put("entityDistanceScaling", ramSaver ? "0.5" : "0.75");
        }
        if (hasSimulationDistance) {
            // Vanilla's supported simulation-distance range starts at 5 chunks.
            settings.put("simulationDistance", ramSaver ? "5" : "6");
        }

        // maxFps replaced the old FPS mode in snapshot 13w38a. Older clients
        // use an enum rather than a numeric cap, so do not invent maxFps there.
        if (ramSaver && hasModernDistanceKey) {
            settings.put("maxFps", "60");
        }

        return Collections.unmodifiableMap(settings);
    }
}
