package io.github.palliarinbj.spurningar;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class BreaksTest {

    private static final String[] SUBJECTS = {"dyr", "geimurinn", "listir", "plontur"};

    /** n questions per subject at each age, named "<age>-<subject>-<i>?". */
    private static List<Question> bank(int n) {
        List<Question> all = new ArrayList<>();
        for (int age : QuestionBank.AGES) {
            for (String s : SUBJECTS) {
                for (int i = 0; i < n; i++) {
                    all.add(new Question(s, age, age + "-" + s + "-" + i + "?", new String[] {"rétt", "a", "b", "c"}, ""));
                }
            }
        }
        return all;
    }

    private static Game game(int level, long seed) {
        Random r = new Random(seed);
        return new Game(new Deck(bank(20), level, Collections.emptySet(), r), level, r);
    }

    /** Answers right until the score reaches the target, taking each question as it comes. */
    private static void rightUntil(Game g, int target) {
        while (g.score() < target) {
            g.answer(g.round().correctSlot());
            if (g.score() < target) assertTrue(g.next());
        }
    }

    @Test public void aSubjectPickAtFiveAndFifteen() {
        Game g = game(10, 1);
        rightUntil(g, 4);
        assertEquals(Game.Break.NONE, g.pending());
        g.next();
        rightUntil(g, 5);
        assertEquals(Game.Break.SUBJECT, g.pending());
        List<String> two = g.subjectChoices();
        assertEquals(2, two.size());
        assertNotEquals(two.get(0), two.get(1));
        assertTrue(g.next(two.get(1)));
        assertEquals(two.get(1), g.round().question.subject);
        assertEquals(10, g.round().question.age);
    }

    @Test public void theBigQuestionAtTenOnlyWithAHelperToWinBack() {
        Game g = game(10, 2);
        rightUntil(g, 10);
        assertEquals(Game.Break.SUBJECT, g.pending());   // no helper used: a subject pick instead

        Game h = game(10, 3);
        rightUntil(h, 9);
        h.next();
        h.useHelper(2);
        h.answer(h.round().correctSlot());
        assertEquals(10, h.score());
        assertEquals(Game.Break.BIG, h.pending());
        assertEquals(2, h.toWinBack());
    }

    @Test public void theBigQuestionComesFromTheAgeAboveAndWinsTheLargestHelperBack() {
        Game g = game(10, 4);
        g.useHelper(1);
        g.answer(g.round().correctSlot());
        g.next();
        g.useHelper(3);
        g.answer(g.round().correctSlot());
        assertTrue(g.nextBig());
        assertTrue(g.big());
        assertEquals(13, g.round().question.age);
        assertEquals(Game.Result.RIGHT, g.answer(g.round().correctSlot()));
        assertEquals(3, g.regained());
        assertTrue(g.helperLeft(3));
        assertFalse(g.helperLeft(1));
        assertEquals(2, g.helpersUsed());                // both uses still count for the tie-break
        g.next();
        assertFalse(g.big());
        assertEquals(0, g.regained());
    }

    @Test public void theAdultsBigQuestionIsAnAdultOne() {
        Game g = game(16, 5);
        g.useHelper(1);
        g.answer(g.round().correctSlot());
        assertTrue(g.nextBig());
        assertEquals(16, g.round().question.age);
    }

    @Test public void aWrongBigQuestionEndsTheGameAsAnyOther() {
        Game g = game(7, 6);
        g.useHelper(1);
        g.answer(g.round().correctSlot());
        g.nextBig();
        int wrong = (g.round().correctSlot() + 1) % Round.SLOTS;
        if (g.round().removed(wrong)) wrong = (wrong + 1) % Round.SLOTS;
        assertEquals(Game.Result.WRONG, g.answer(wrong));
        assertTrue(g.over());
        assertEquals(0, g.regained());
    }

    @Test public void aSubjectRunDryFallsBackToAnyQuestion() {
        Random r = new Random(7);
        Deck deck = new Deck(bank(1), 10, Collections.emptySet(), r);
        assertEquals("listir", deck.next(5, "listir").subject);
        Question q = deck.next(6, "listir");                // the only 10-year-old art question is used
        assertEquals(10, q.age);
        assertNotEquals("listir", q.subject);
    }

    @Test public void subjectsOnlyOffersThoseWithQuestionsLeft() {
        Deck deck = new Deck(bank(1), 13, Collections.emptySet(), new Random(8));
        deck.next(5, "dyr");
        assertEquals(new HashSet<>(List.of("geimurinn", "listir", "plontur")), new HashSet<>(deck.subjects(5)));
    }

    @Test public void tiers() {
        assertEquals(Progress.NONE, Progress.tier(4));
        assertEquals(Progress.BRONZE, Progress.tier(5));
        assertEquals(Progress.SILVER, Progress.tier(19));
        assertEquals(Progress.GOLD, Progress.tier(20));
        assertTrue(Progress.reached(5));
        assertTrue(Progress.reached(10));
        assertTrue(Progress.reached(20));
        assertFalse(Progress.reached(15));
        assertFalse(Progress.reached(0));
    }
}
