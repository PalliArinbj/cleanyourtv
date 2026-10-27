package io.github.palliarinbj.spurningar;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class StoreTest {

    @Test public void theNameJustUsedComesFirstAndOnlyOnce() {
        assertEquals(Arrays.asList("Dóra", "Anna", "Bjarki"),
                Store.recentFirst(Arrays.asList("Anna", "dóra", "Bjarki"), "Dóra", 8));
    }

    @Test public void theOldestNamesFallOff() {
        assertEquals(Arrays.asList("E", "A", "B"), Store.recentFirst(Arrays.asList("A", "B", "C", "D"), "E", 3));
        assertEquals(Collections.singletonList("A"), Store.recentFirst(Collections.emptyList(), "A", 8));
    }
}
