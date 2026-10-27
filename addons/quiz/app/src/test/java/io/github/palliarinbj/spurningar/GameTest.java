package io.github.palliarinbj.spurningar;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GameTest {

    /** n questions at each age, named "<age>-<i>?". */
    private static List<Question> bank(int n) {
        List<Question> all = new ArrayList<>();
        for (int age : QuestionBank.AGES) {
            for (int i = 0; i < n; i++) {
                all.add(new Question("dyr", age, age + "-" + i + "?", new String[] {"rétt", "a", "b", "c"}, ""));
            }
        }
        return all;
    }

    private static Game game(int level, int n, long seed) {
        Random r = new Random(seed);
        return new Game(new Deck(bank(n), level, Collections.emptySet(), r), level, r);
    }

    @Test public void theFirstTwoQuestionsComeFromTheAgeBelow() {
        Deck deck = new Deck(bank(5), 13, Collections.emptySet(), new Random(1));
        assertEquals(10, deck.next(0).age);
        assertEquals(10, deck.next(1).age);
        assertEquals(13, deck.next(2).age);
        Deck youngest = new Deck(bank(5), 7, Collections.emptySet(), new Random(1));
        assertEquals(7, youngest.next(0).age);
    }

    @Test public void noQuestionTwiceInAGameAndSeenOnesLast() {
        List<Question> all = bank(4);
        Set<String> seen = new HashSet<>();
        for (Question q : all) if (q.age == 16 && !q.text.equals("16-3?")) seen.add(q.id());
        Deck deck = new Deck(all, 16, seen, new Random(7));
        deck.next(0);
        deck.next(1);
        assertEquals("16-3?", deck.next(2).text);   // the only one not seen comes first
        Set<String> texts = new HashSet<>();
        for (int i = 3; i < 6; i++) assertTrue(texts.add(deck.next(i).text));
        assertNull(deck.next(6));   // all four 16s used
    }

    @Test public void aRightAnswerScoresAndAWrongOneEndsTheGame() {
        Game g = game(10, 6, 3);
        assertEquals(Game.Result.RIGHT, g.answer(g.round().correctSlot()));
        assertEquals(1, g.score());
        assertTrue(g.next());
        assertEquals(2, g.number());
        int wrong = (g.round().correctSlot() + 1) % Round.SLOTS;
        assertEquals(Game.Result.WRONG, g.answer(wrong));
        assertTrue(g.over());
        assertFalse(g.outOfQuestions());
        assertEquals(1, g.score());
        assertFalse(g.next());
    }

    @Test public void theAnswersAreShuffledAndTheRightOneIsWhereItSays() {
        Set<Integer> slots = new HashSet<>();
        Random random = new Random(1);   // one per game, as in the app
        for (int i = 0; i < 40; i++) {
            Round r = new Round(bank(1).get(0), random);
            assertEquals("rétt", r.answer(r.correctSlot()));
            slots.add(r.correctSlot());
        }
        assertEquals(4, slots.size());
    }

    @Test public void eachHelperOnceAndOnlyWrongAnswersGo() {
        Game g = game(7, 6, 5);
        int right = g.round().correctSlot();
        List<Integer> gone = g.useHelper(2);
        assertEquals(2, gone.size());
        assertFalse(gone.contains(right));
        assertFalse(g.canUse(2));          // used
        assertFalse(g.canUse(3));          // only one wrong answer left
        assertTrue(g.canUse(1));
        g.useHelper(1);
        assertEquals(0, g.round().wrongLeft());
        assertEquals(2, g.helpersUsed());
        assertEquals(Game.Result.RIGHT, g.answer(right));
        g.next();
        assertTrue(g.canUse(3));
        g.useHelper(3);
        assertEquals(Game.Result.RIGHT, g.answer(g.round().correctSlot()));
        assertEquals(3, g.helpersUsed());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aRemovedAnswerCannotBeChosen() {
        Game g = game(7, 6, 9);
        int slot = g.useHelper(1).get(0);
        g.answer(slot);
    }

    @Test public void runningOutOfQuestionsEndsTheGameWithoutALoss() {
        Game g = game(7, 2, 11);
        g.answer(g.round().correctSlot());
        assertTrue(g.next());
        g.answer(g.round().correctSlot());
        assertFalse(g.next());
        assertTrue(g.over());
        assertTrue(g.outOfQuestions());
        assertEquals(2, g.score());
    }

    @Test public void twoGamesAreNotTheSame() {
        Game a = game(16, 20, 1), b = game(16, 20, 2);
        assertNotEquals(a.round().question.text + a.round().correctSlot(), b.round().question.text + b.round().correctSlot());
    }
}
