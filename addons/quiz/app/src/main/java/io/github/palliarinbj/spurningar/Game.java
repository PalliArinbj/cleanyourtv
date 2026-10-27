package io.github.palliarinbj.spurningar;

import java.util.List;
import java.util.Random;

/**
 * One game at one difficulty: questions until the first wrong answer. The score is the number answered right.
 * Three helpers, one of each: remove 1, 2 or 3 wrong answers from the question showing. More than one can be used
 * on the same question while it has the wrong answers left to remove.
 */
final class Game {
    enum Result { RIGHT, WRONG }

    static final int[] HELPERS = {1, 2, 3};

    final int level;
    private final Deck deck;
    private final Random random;
    private final boolean[] helperUsed = new boolean[HELPERS.length + 1];   // by size, 1 to 3
    private Round round;
    private int score;
    private boolean over;
    /** Set once a game ends because the difficulty has no questions left; it ends as a win, not a loss. */
    private boolean outOfQuestions;

    Game(Deck deck, int level, Random random) {
        this.deck = deck;
        this.level = level;
        this.random = random;
        deal();
    }

    Round round() {
        return round;
    }

    int score() {
        return score;
    }

    boolean over() {
        return over;
    }

    boolean outOfQuestions() {
        return outOfQuestions;
    }

    /** 1-based number of the question showing. */
    int number() {
        return score + 1;
    }

    boolean helperLeft(int size) {
        return !helperUsed[size];
    }

    int helpersUsed() {
        int n = 0;
        for (int size : HELPERS) if (helperUsed[size]) n++;
        return n;
    }

    /** Whether the helper can be used now: not used before, and enough wrong answers left on this question. */
    boolean canUse(int size) {
        return !over && round != null && helperLeft(size) && round.wrongLeft() >= size;
    }

    /** Uses the helper on the question showing; the slots it removed. */
    List<Integer> useHelper(int size) {
        if (!canUse(size)) throw new IllegalStateException("helper " + size + " cannot be used now");
        helperUsed[size] = true;
        return round.removeWrong(size, random);
    }

    /** Answers the question showing. A right answer waits for {@link #next()}; a wrong one ends the game. */
    Result answer(int slot) {
        if (over) throw new IllegalStateException("the game is over");
        if (round.removed(slot)) throw new IllegalArgumentException("slot " + slot + " was removed");
        if (slot == round.correctSlot()) {
            score++;
            return Result.RIGHT;
        }
        over = true;
        return Result.WRONG;
    }

    /** The next question after a right answer; false when the difficulty has run out, which ends the game. */
    boolean next() {
        if (over) return false;
        deal();
        return !over;
    }

    private void deal() {
        Question q = deck.next(score);
        if (q == null) {
            over = true;
            outOfQuestions = true;
            round = null;
        } else {
            round = new Round(q, random);
        }
    }
}
