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
    public void fpsBoosterNeverChangesRenderResolution() {
        assertEquals(1.0f, PerformanceRuntimeTuning.getRenderScale(true, false), 0.0001f);
    }

    @Test
    public void ultraFpsNeverChangesRenderResolution() {
        assertEquals(1.0f, PerformanceRuntimeTuning.getRenderScale(true, true, false), 0.0001f);
    }

    @Test
    public void fixedAndNormalResolutionArePreserved() {
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
    public void ramSaverEncouragesHeapReclamationWithoutOverridingHeapLimit() {
        List<String> arguments = PerformanceRuntimeTuning.buildJvmArguments(8, false, true);
        assertTrue(arguments.contains("-XX:+UseG1GC"));
        assertTrue(arguments.contains("-XX:MaxGCPauseMillis=200"));
        assertTrue(arguments.contains("-XX:MinHeapFreeRatio=10"));
        assertTrue(arguments.contains("-XX:MaxHeapFreeRatio=25"));
        assertFalse(arguments.contains("-XX:+UseStringDeduplication"));
        assertFalse(arguments.stream().anyMatch(argument -> argument.startsWith("-Xmx")));
    }

    @Test
    public void modernJavaCanUseStringDeduplicationForRamSaver() {
        assertTrue(PerformanceRuntimeTuning.buildJvmArguments(17, false, true)
                .contains("-XX:+UseStringDeduplication"));
    }

    @Test
    public void fpsProfileTakesPriorityWhenBothModesAreEnabled() {
        List<String> arguments = PerformanceRuntimeTuning.buildJvmArguments(21, true, true);
        assertEquals("-XX:MaxGCPauseMillis=50", arguments.get(1));
        assertTrue(arguments.contains("-XX:+ExplicitGCInvokesConcurrent"));
        assertFalse(arguments.contains("-XX:MinHeapFreeRatio=10"));
        assertFalse(arguments.contains("-XX:MaxHeapFreeRatio=25"));
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
}
