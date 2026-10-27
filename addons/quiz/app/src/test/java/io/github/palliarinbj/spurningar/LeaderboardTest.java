package io.github.palliarinbj.spurningar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LeaderboardTest {

    @Test public void higherScoreFirstThenFewerHelpersThenWhoeverWasFirst() {
        Leaderboard b = new Leaderboard();
        assertEquals(0, b.add("Anna", 5, 2, 100));
        assertEquals(0, b.add("Bjarki", 8, 3, 200));
        assertEquals(1, b.add("Dóra", 5, 1, 300));   // same score as Anna, fewer helpers
        assertEquals(3, b.add("Einar", 5, 2, 400));  // ties Anna on both: after her
        assertEquals("Bjarki Dóra Anna Einar", names(b));
    }

    @Test public void tenPlacesAndTheLastOneFallsOff() {
        Leaderboard b = new Leaderboard();
        for (int i = 1; i <= 10; i++) b.add("P" + i, i, 0, i);
        assertEquals(-1, b.place(1, 0, 99));     // ties the last, but came later
        assertEquals(9, b.place(1, 0, 0));       // ties the last and came earlier
        assertEquals(0, b.add("Ný", 20, 3, 50));
        assertEquals(10, b.entries().size());
        assertEquals("P2", b.entries().get(9).name);
    }

    @Test public void aScoreOfNothingNeverMakesTheBoard() {
        assertEquals(-1, new Leaderboard().place(0, 0, 0));
    }

    @Test public void namesAreTidied() {
        assertEquals("Þórunn Ýr", Leaderboard.cleanName("  Þórunn   Ýr "));
        assertEquals("a b", Leaderboard.cleanName("a|b"));
        assertEquals("Aðalsteinn S", Leaderboard.cleanName("Aðalsteinn Sigurðsson"));
    }

    @Test public void itReadsBackWhatItWrote() {
        Leaderboard b = new Leaderboard();
        b.add("Æsa", 7, 1, 1700000000000L);
        b.add("Ólafur", 9, 0, 1700000001000L);
        Leaderboard read = Leaderboard.parse(b.serialize() + "broken line\n3|x|1|Nafn\n");
        assertEquals("Ólafur Æsa", names(read));
        assertEquals(1700000000000L, read.entries().get(1).time);
    }

    private static String names(Leaderboard b) {
        StringBuilder s = new StringBuilder();
        for (Leaderboard.Entry e : b.entries()) s.append(s.length() > 0 ? " " : "").append(e.name);
        return s.toString();
    }
}
