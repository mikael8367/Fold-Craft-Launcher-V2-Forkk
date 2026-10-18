package com.tungsten.fcl.game;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PerformanceRuntimeTuningTest {

    @Test
    public void disabledProfilesDoNotAddJvmArguments() {
        assertTrue(PerformanceRuntimeTuning.buildJvmArguments(17, false, false).isEmpty());
    }

    @Test
    public void standardFpsProfileUsesSlightlyReducedRenderScale() {
        assertEquals(0.85f, PerformanceRuntimeTuning.getRenderScale(true, false), 0.0001f);
    }

    @Test
    public void ultraFpsUsesLowerRenderScale() {
        assertEquals(0.70f, PerformanceRuntimeTuning.getRenderScale(true, true, false), 0.0001f);
    }

    @Test
    public void standardFpsProfileReducesNativeRenderBufferWithoutEditingOptions() {
        assertEquals(0.85f, PerformanceRuntimeTuning.getRenderScale(true, false), 0.0001f);
    }

    @Test
    public void ultraFpsProfileUsesMoreAggressiveNativeRenderScale() {
        assertEquals(0.70f, PerformanceRuntimeTuning.getRenderScale(false, true, false), 0.0001f);
    }

    @Test
    public void fixedResolutionIsPreservedAndDisabledProfileUsesFullScale() {
        assertEquals(1.0f, PerformanceRuntimeTuning.getRenderScale(true, true, true), 0.0001f);
        assertEquals(1.0f, PerformanceRuntimeTuning.getRenderScale(false, false), 0.0001f);
    }

    @Test
    public void unsupportedLegacyJavaDoesNotReceiveTuningFlags() {
        assertTrue(PerformanceRuntimeTuning.buildJvmArguments(7, true, true).isEmpty());
    }

    @Test
    public void fpsProfileAddsGcArgumentsWithoutChangingHeapOrSystemProperties() {
        List<String> arguments = PerformanceRuntimeTuning.buildJvmArguments(8, true, false);
        assertTrue(arguments.contains("-XX:+UseG1GC"));
        assertTrue(arguments.contains("-XX:MaxGCPauseMillis=50"));
        assertTrue(arguments.contains("-XX:+ParallelRefProcEnabled"));
        assertTrue(arguments.contains("-XX:+ExplicitGCInvokesConcurrent"));
        assertFalse(arguments.stream().anyMatch(argument -> argument.startsWith("-Xmx")));
        assertFalse(arguments.stream().anyMatch(argument -> argument.startsWith("-D")));
    }

    @Test
    public void ultraFpsUsesModernJavaGcTuning() {
        List<String> arguments = PerformanceRuntimeTuning.buildJvmArguments(25, false, true, false);
        assertTrue(arguments.contains("-XX:+UseG1GC"));
        assertTrue(arguments.contains("-XX:MaxGCPauseMillis=30"));
        assertTrue(arguments.contains("-XX:+ParallelRefProcEnabled"));
        assertTrue(arguments.contains("-XX:+ExplicitGCInvokesConcurrent"));
        assertTrue(arguments.contains("-XX:InitiatingHeapOccupancyPercent=25"));
        assertFalse(arguments.stream().anyMatch(argument -> argument.startsWith("-Xmx")));
    }

    @Test
    public void ultraFpsKeepsLegacyRuntimeFlagsCompatible() {
        List<String> arguments = PerformanceRuntimeTuning.buildJvmArguments(8, false, true, false);
        assertTrue(arguments.contains("-XX:MaxGCPauseMillis=40"));
        assertFalse(arguments.contains("-XX:InitiatingHeapOccupancyPercent=25"));
    }

    @Test
    public void ramSaverUsesLowRetentionAndReducedGcWorkerCounts() {
        List<String> arguments = PerformanceRuntimeTuning.buildJvmArguments(8, false, true);
        assertTrue(arguments.contains("-XX:+UseG1GC"));
        assertTrue(arguments.contains("-XX:MaxGCPauseMillis=200"));
        assertTrue(arguments.contains("-XX:MinHeapFreeRatio=5"));
        assertTrue(arguments.contains("-XX:MaxHeapFreeRatio=20"));
        assertTrue(arguments.contains("-XX:SoftRefLRUPolicyMSPerMB=100"));
        assertTrue(arguments.contains("-XX:ParallelGCThreads=2"));
        assertTrue(arguments.contains("-XX:ConcGCThreads=1"));
        assertFalse(arguments.contains("-XX:+UseStringDeduplication"));
        assertFalse(arguments.stream().anyMatch(argument -> argument.startsWith("-Xmx")));
    }

    @Test
    public void modernJavaCanUseStringDeduplicationForRamSaver() {
        List<String> arguments = PerformanceRuntimeTuning.buildJvmArguments(17, false, true);
        assertTrue(arguments.contains("-XX:+UseStringDeduplication"));
        assertTrue(arguments.contains("-XX:InitiatingHeapOccupancyPercent=30"));
    }

    @Test
    public void ramOptimizationIsNotDroppedWhenFpsAndRamProfilesAreBothEnabled() {
        List<String> arguments = PerformanceRuntimeTuning.buildJvmArguments(21, true, true);
        assertTrue(arguments.contains("-XX:MaxGCPauseMillis=50"));
        assertTrue(arguments.contains("-XX:MinHeapFreeRatio=5"));
        assertTrue(arguments.contains("-XX:MaxHeapFreeRatio=20"));
        assertTrue(arguments.contains("-XX:SoftRefLRUPolicyMSPerMB=100"));
        assertTrue(arguments.contains("-XX:ParallelGCThreads=2"));
        assertTrue(arguments.contains("-XX:ConcGCThreads=1"));
        assertTrue(arguments.contains("-XX:+UseStringDeduplication"));
        assertTrue(arguments.contains("-XX:InitiatingHeapOccupancyPercent=30"));
    }

    @Test
    public void ultraFpsAndRamSaverDoNotDefineConflictingOccupancyThresholds() {
        List<String> arguments = PerformanceRuntimeTuning.buildJvmArguments(21, false, true, true);
        assertEquals(1, arguments.stream()
                .filter(argument -> argument.startsWith("-XX:InitiatingHeapOccupancyPercent="))
                .count());
        assertTrue(arguments.contains("-XX:InitiatingHeapOccupancyPercent=25"));
        assertTrue(arguments.contains("-XX:+UseStringDeduplication"));
    }

    @Test
    public void fpsProfileTakesPriorityWithoutRamSaver() {
        List<String> arguments = PerformanceRuntimeTuning.buildJvmArguments(21, true, false);
        assertEquals("-XX:MaxGCPauseMillis=50", arguments.get(1));
        assertTrue(arguments.contains("-XX:+ExplicitGCInvokesConcurrent"));
        assertFalse(arguments.contains("-XX:MinHeapFreeRatio=5"));
        assertFalse(arguments.contains("-XX:SoftRefLRUPolicyMSPerMB=100"));
        assertFalse(arguments.contains("-XX:+UseStringDeduplication"));
    }

    @Test
    public void returnedArgumentsAreImmutable() {
        List<String> arguments = PerformanceRuntimeTuning.buildJvmArguments(17, true, false);
        try {
            arguments.add("-XX:MaxGCPauseMillis=1");
            throw new AssertionError("Expected an immutable argument list");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    @Test
    public void disabledProfilesAlsoKeepNativeRenderScale() {
        assertEquals(1.0f, PerformanceRuntimeTuning.getRenderScale(false, false, false), 0.0001f);
    }
}
