package com.tungsten.fcl.game;

import com.tungsten.fclcore.util.versioning.GameVersionNumber;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression tests for version-specific Minecraft graphics option names and values. */
public class PerformanceProfileOptionsTest {

    private Map<String, String> profile(String version, boolean fpsBooster, boolean ramSaver) {
        return PerformanceProfileOptions.buildProfile(
                GameVersionNumber.asGameVersion(version), fpsBooster, ramSaver);
    }

    @Test
    public void disabledProfilesDoNotOverrideAnyOptions() {
        assertTrue(profile("1.21.11", false, false).isEmpty());
    }

    @Test
    public void legacyClientUsesLegacyOptionNamesAndDoesNotInventFpsCap() {
        Map<String, String> settings = profile("1.6.4", true, false);

        assertEquals("3", settings.get("viewDistance"));
        assertFalse(settings.containsKey("renderDistance"));
        assertEquals("false", settings.get("fancyGraphics"));
        assertEquals("false", settings.get("clouds"));
        assertFalse(settings.containsKey("renderClouds"));
        assertFalse(settings.containsKey("mipmapLevels"));
        assertFalse(settings.containsKey("maxFps"));
    }

    @Test
    public void minecraft112UsesNumericAmbientOcclusionAndPreservesUserFpsCap() {
        Map<String, String> settings = profile("1.12.2", true, false);

        assertEquals("6", settings.get("renderDistance"));
        assertEquals("false", settings.get("fancyGraphics"));
        assertEquals("0", settings.get("ao"));
        assertEquals("false", settings.get("renderClouds"));
        assertEquals("0", settings.get("mipmapLevels"));
        assertFalse(settings.containsKey("clouds"));
        assertFalse(settings.containsKey("graphics"));
        assertFalse(settings.containsKey("ambientOcclusion"));
        assertFalse(settings.containsKey("maxFps"));
    }

    @Test
    public void minecraft116UsesNumericGraphicsModeAndRamSaverCap() {
        Map<String, String> settings = profile("1.16.5", false, true);

        assertEquals("4", settings.get("renderDistance"));
        assertEquals("0", settings.get("graphicsMode"));
        assertFalse(settings.containsKey("fancyGraphics"));
        assertFalse(settings.containsKey("graphicsPreset"));
        assertEquals("0.5", settings.get("entityDistanceScaling"));
        assertEquals("60", settings.get("maxFps"));
        assertFalse(settings.containsKey("simulationDistance"));
    }

    @Test
    public void minecraft12110KeepsGraphicsModeSchema() {
        Map<String, String> settings = profile("1.21.10", false, true);

        assertEquals("0", settings.get("graphicsMode"));
        assertFalse(settings.containsKey("graphicsPreset"));
        assertEquals("4", settings.get("simulationDistance"));
        assertEquals("60", settings.get("maxFps"));
    }

    @Test
    public void minecraft12111AndNewerUseGraphicsPresetSchema() {
        Map<String, String> settings = profile("1.21.11", false, true);

        assertEquals("fast", settings.get("graphicsPreset"));
        assertFalse(settings.containsKey("graphicsMode"));
        assertFalse(settings.containsKey("fancyGraphics"));
        assertEquals("false", settings.get("ao"));
        assertEquals("false", settings.get("renderClouds"));
        assertEquals("4", settings.get("simulationDistance"));
        assertEquals("60", settings.get("maxFps"));
    }

    @Test
    public void snapshotBeforeNewPresetSchemaKeepsGraphicsMode() {
        Map<String, String> settings = profile("25w40a", false, true);

        assertEquals("0", settings.get("graphicsMode"));
        assertFalse(settings.containsKey("graphicsPreset"));
        assertEquals("0", settings.get("ao"));
    }

    @Test
    public void snapshotIntroducingGraphicsPresetsUsesNewSchemaImmediately() {
        Map<String, String> settings = profile("25w41a", false, true);

        assertEquals("fast", settings.get("graphicsPreset"));
        assertFalse(settings.containsKey("graphicsMode"));
        assertEquals("false", settings.get("ao"));
    }

    @Test
    public void renderDistanceAndFpsCapUseTheirSnapshotCutover() {
        Map<String, String> before = profile("13w37a", false, true);
        Map<String, String> after = profile("13w38a", false, true);

        assertEquals("3", before.get("viewDistance"));
        assertFalse(before.containsKey("maxFps"));
        assertEquals("4", after.get("renderDistance"));
        assertEquals("60", after.get("maxFps"));
    }

    @Test
    public void snapshotsUsingNewReleaseNumberingUseModernOptions() {
        Map<String, String> settings = profile("26.3", true, true);

        assertEquals("fast", settings.get("graphicsPreset"));
        assertEquals("4", settings.get("renderDistance"));
        assertEquals("4", settings.get("simulationDistance"));
        assertEquals("60", settings.get("maxFps"));
    }
}
