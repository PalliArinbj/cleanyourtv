package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class TriviaTest {
    private static List<String> texts(int n) {
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < n; i++) texts.add("tidbit " + i);
        return texts;
    }

    @Test public void onlyTheTextIsShown() {
        assertEquals(Arrays.asList("Kvikasilfur (Hg) og bróm (Br) eru fljótandi.", "Án reita.", "Tveir | í texta."),
                Trivia.parse(Arrays.asList(
                        "efnafraedi|13|Kvikasilfur (Hg) og bróm (Br) eru fljótandi.",
                        "Án reita.",
                        "saga|7|Tveir | í texta.")));
    }

    @Test public void blankLinesCommentsAndAByteOrderMarkAreSkipped() {
        assertEquals(Collections.singletonList("Eitt."), Trivia.parse(Arrays.asList(
                "﻿# Efnafræði", "", "   ", "  # athugasemd", "dyr|7|Eitt.", "dyr|7|  ")));
    }

    @Test public void everyTidbitComesOnceBeforeAnyRepeats() {
        List<String> texts = texts(50);
        Trivia.Deck deck = new Trivia.Deck();
        Random random = new Random(1);
        for (int round = 0; round < 3; round++) {
            Set<Integer> seen = new HashSet<>();
            for (int i = 0; i < texts.size(); i++) seen.add(Trivia.next(deck, texts, random));
            assertEquals(texts.size(), seen.size());
        }
    }

    @Test public void aNewDeckNeverStartsWithTheLastOneShown() {
        List<String> texts = texts(3);
        Trivia.Deck deck = new Trivia.Deck();
        Random random = new Random(7);
        int last = -1;
        for (int round = 0; round < 200; round++) {
            int first = Trivia.next(deck, texts, random);
            if (round > 0) assertNotEquals(last, first);
            Trivia.next(deck, texts, random);
            last = Trivia.next(deck, texts, random);
        }
    }

    @Test public void thePlaceInTheDeckSurvivesARestart() {
        List<String> texts = texts(20);
        Trivia.Deck deck = new Trivia.Deck();
        Random random = new Random(3);
        for (int i = 0; i < 5; i++) Trivia.next(deck, texts, random);
        int[] order = Trivia.order(texts.size(), deck.seed, deck.avoid);
        // Home restarted: the same deck, read back from preferences, goes on where it was.
        Trivia.Deck kept = new Trivia.Deck();
        kept.seed = deck.seed;
        kept.position = deck.position;
        kept.avoid = deck.avoid;
        kept.stamp = deck.stamp;
        assertEquals(order[5], Trivia.next(kept, texts, new Random(99)));
    }

    @Test public void aChangedListDealsANewDeck() {
        Trivia.Deck deck = new Trivia.Deck();
        Random random = new Random(5);
        for (int i = 0; i < 4; i++) Trivia.next(deck, texts(10), random);
        Trivia.next(deck, texts(12), random);
        assertEquals(1, deck.position);
        assertEquals(Trivia.stamp(texts(12)), deck.stamp);
    }

    @Test public void aSubjectWithThreeTimesTheLinesComesUpThreeTimesAsOften() {
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < 30; i++) texts.add("efnafraedi " + i);
        for (int i = 0; i < 10; i++) texts.add("saga " + i);
        Trivia.Deck deck = new Trivia.Deck();
        Random random = new Random(11);
        int chemistry = 0;
        for (int i = 0; i < 400; i++) if (texts.get(Trivia.next(deck, texts, random)).startsWith("efnafraedi")) chemistry++;
        assertEquals(300, chemistry);
    }

    @Test public void oneTidbitIsShownEveryTime() {
        Trivia.Deck deck = new Trivia.Deck();
        Random random = new Random(2);
        for (int i = 0; i < 5; i++) assertEquals(0, Trivia.next(deck, texts(1), random));
        assertTrue(deck.position <= 1);
    }
}
