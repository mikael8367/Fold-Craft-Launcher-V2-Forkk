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
    public void unsupportedLegacyJavaDoesNotReceiveTuningFlags() {
        assertTrue(PerformanceRuntimeTuning.buildJvmArguments(7, true, true).isEmpty());
    }

    @Test
    public void fpsProfileOnlyChangesJvmGarbageCollectionArguments() {
        List<String> arguments = PerformanceRuntimeTuning.buildJvmArguments(8, true, false);

        assertTrue(arguments.contains("-XX:+UseG1GC"));
        assertTrue(arguments.contains("-XX:MaxGCPauseMillis=100"));
        assertTrue(arguments.contains("-XX:+ParallelRefProcEnabled"));
        assertFalse(arguments.stream().anyMatch(argument -> argument.startsWith("-Xmx")));
        assertFalse(arguments.stream().anyMatch(argument -> argument.startsWith("-D")));
    }

    @Test
    public void ramSaverTunesHeapReclamationWithoutOverridingHeapLimit() {
        List<String> arguments = PerformanceRuntimeTuning.buildJvmArguments(8, false, true);

        assertTrue(arguments.contains("-XX:+UseG1GC"));
        assertTrue(arguments.contains("-XX:MaxGCPauseMillis=200"));
        assertTrue(arguments.contains("-XX:MinHeapFreeRatio=10"));
        assertTrue(arguments.contains("-XX:MaxHeapFreeRatio=35"));
        assertFalse(arguments.contains("-XX:+UseStringDeduplication"));
        assertFalse(arguments.stream().anyMatch(argument -> argument.startsWith("-Xmx")));
    }

    @Test
    public void modernJavaCanUseStringDeduplicationForRamSaver() {
        List<String> arguments = PerformanceRuntimeTuning.buildJvmArguments(17, false, true);

        assertTrue(arguments.contains("-XX:+UseStringDeduplication"));
    }

    @Test
    public void fpsProfileTakesPauseTargetWhenBothModesAreEnabled() {
        List<String> arguments = PerformanceRuntimeTuning.buildJvmArguments(21, true, true);

        assertEquals("-XX:MaxGCPauseMillis=100", arguments.get(1));
        assertTrue(arguments.contains("-XX:MinHeapFreeRatio=10"));
        assertTrue(arguments.contains("-XX:+UseStringDeduplication"));
    }

    @Test
    public void returnedArgumentsAreImmutable() {
        List<String> arguments = PerformanceRuntimeTuning.buildJvmArguments(17, true, false);
        try {
            arguments.add("-XX:MaxGCPauseMillis=1");
            throw new AssertionError("Expected an immutable argument list");
        } catch (UnsupportedOperationException expected) {
            // Expected: the shared tuning definition cannot be modified by callers.
        }
    }
}
