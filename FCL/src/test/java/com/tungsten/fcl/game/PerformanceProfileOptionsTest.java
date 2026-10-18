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
    public void snapshotBeforeCloudOptionRenameUsesLegacyCloudKey() {
        Map<String, String> settings = profile("14w24a", true, false);

        assertEquals("false", settings.get("clouds"));
        assertFalse(settings.containsKey("renderClouds"));
        assertEquals("false", settings.get("ao"));
    }

    @Test
    public void cloudOptionRenameIsAppliedAtSnapshotBoundary() {
        Map<String, String> before = profile("14w24a", false, true);
        Map<String, String> after = profile("14w25a", false, true);

        assertEquals("false", before.get("clouds"));
        assertFalse(before.containsKey("renderClouds"));
        assertEquals("false", after.get("renderClouds"));
        assertFalse(after.containsKey("clouds"));
        assertEquals("0", after.get("ao"));
    }

    @Test
    public void graphicsModeChangesAtThePreReleaseBoundary() {
        Map<String, String> before = profile("20w22a", true, false);
        Map<String, String> after = profile("1.16-pre1", true, false);

        assertEquals("false", before.get("fancyGraphics"));
        assertFalse(before.containsKey("graphicsMode"));
        assertEquals("0", after.get("graphicsMode"));
        assertFalse(after.containsKey("fancyGraphics"));
    }

    @Test
    public void entityAndSimulationDistanceOptionsRespectSnapshotBoundaries() {
        Map<String, String> beforeEntityDistance = profile("20w16a", false, true);
        Map<String, String> afterEntityDistance = profile("20w17a", false, true);
        Map<String, String> beforeSimulationDistance = profile("21w37a", false, true);
        Map<String, String> afterSimulationDistance = profile("21w38a", false, true);

        assertFalse(beforeEntityDistance.containsKey("entityDistanceScaling"));
        assertEquals("0.5", afterEntityDistance.get("entityDistanceScaling"));
        assertFalse(beforeSimulationDistance.containsKey("simulationDistance"));
        assertEquals("4", afterSimulationDistance.get("simulationDistance"));
    }

    @Test
    public void nullVersionAndDisabledProfileDoNotProduceOverrides() {
        assertTrue(PerformanceProfileOptions.buildProfile(null, true, true).isEmpty());
        assertTrue(profile("1.21.11", false, false).isEmpty());
    }

    @Test
    public void generatedProfileIsImmutable() {
        Map<String, String> settings = profile("1.21.11", true, true);
        try {
            settings.put("renderDistance", "32");
            throw new AssertionError("Expected profile map to be immutable");
        } catch (UnsupportedOperationException expected) {
            // Profile definitions are immutable shared data.
        }
    }

    @Test
    public void sharedGameDirectoriesShareProfileStateKey() {
        String sharedPath = "/data/user/0/com.example.fcl/.minecraft/options.txt";
        assertEquals(
                PerformanceProfileOptions.statePreferenceKeyFor(sharedPath),
                PerformanceProfileOptions.statePreferenceKeyFor(
                        "/data/user/0/com.example.fcl/.minecraft/../.minecraft/options.txt")
        );
    }

    @Test
    public void isolatedGameDirectoriesGetDifferentProfileStateKeys() {
        String first = PerformanceProfileOptions.statePreferenceKeyFor(
                "/data/user/0/com.example.fcl/.minecraft/versions/1.12.2/options.txt");
        String second = PerformanceProfileOptions.statePreferenceKeyFor(
                "/data/user/0/com.example.fcl/.minecraft/versions/1.21.11/options.txt");
        org.junit.Assert.assertNotEquals(first, second);
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
