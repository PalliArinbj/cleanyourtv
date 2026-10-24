package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClockSceneTest {
    private static final int[] GRAIN = ClockScene.grainPixels(ClockScene.GRAIN_SIZE, ClockScene.GRAIN_MAX_ALPHA, 1L);

    @Test public void theGrainIsWhiteAndNeverStrongerThanTheMax() {
        assertEquals(ClockScene.GRAIN_SIZE * ClockScene.GRAIN_SIZE, GRAIN.length);
        for (int p : GRAIN) {
            assertEquals(0x00FFFFFF, p & 0x00FFFFFF);
            assertTrue((p >>> 24) <= ClockScene.GRAIN_MAX_ALPHA);
        }
    }

    @Test public void theGrainUsesEveryLevelSoItDithersRatherThanTints() {
        Set<Integer> alphas = new HashSet<>();
        for (int p : GRAIN) alphas.add(p >>> 24);
        assertEquals(ClockScene.GRAIN_MAX_ALPHA + 1, alphas.size());
        double mean = Arrays.stream(GRAIN).map(p -> p >>> 24).average().orElse(0);
        assertEquals(ClockScene.GRAIN_MAX_ALPHA / 2.0, mean, 0.1);
    }

    @Test public void theGrainIsTheSameEveryTime() {
        assertArrayEquals(GRAIN, ClockScene.grainPixels(ClockScene.GRAIN_SIZE, ClockScene.GRAIN_MAX_ALPHA, 1L));
    }

    @Test public void theTidbitChangesEveryTriviaMinutes() {
        assertEquals(5, ClockScene.triviaEvery(5));
        assertEquals(1, ClockScene.triviaEvery(1));
        assertEquals(30, ClockScene.triviaEvery(30));
    }

    @Test public void zeroTriviaMinutesStillChangesTheTidbitInTheScreensaver() {
        // On Home 0 means "only when Home comes back", which never happens while the screensaver runs.
        assertEquals(ClockScene.DEFAULT_TRIVIA_MINUTES, ClockScene.triviaEvery(0));
        assertTrue(ClockScene.DEFAULT_TRIVIA_MINUTES > 0);
    }

    @Test public void theGrainIsAboutOneBandingStep() {
        // The glow's strongest stop is a quarter of a channel (0x40): at most 64 levels over half the screen, one
        // level per band. Grain of 1 to 3 levels covers a step without becoming visible noise on black.
        assertTrue(ClockScene.GRAIN_MAX_ALPHA >= 1 && ClockScene.GRAIN_MAX_ALPHA <= 3);
    }
}
