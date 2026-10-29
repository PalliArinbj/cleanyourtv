package io.github.palliarinbj.spurningar;

import java.util.List;
import java.util.Random;

/**
 * One game at one difficulty: questions until the first wrong answer. The score is the number answered right.
 * Three helpers, one of each: remove 1, 2 or 3 wrong answers from the question showing. More than one can be used
 * on the same question while it has the wrong answers left to remove.
 *
 * Breaks in the run, after a right answer: at every tenth, a player who has used a helper is offered the big
 * question, a harder one that wins a used helper back; at every other fifth (or a tenth with no helper to win back)
 * the player picks the next question's subject from two.
 */
final class Game {
    enum Result { RIGHT, WRONG }

    /** What comes before the next question. */
    enum Break { NONE, SUBJECT, BIG }

    static final int[] HELPERS = {1, 2, 3};
    static final int SUBJECT_EVERY = 5, BIG_EVERY = 10, SUBJECT_CHOICES = 2;

    final int level;
    private final Deck deck;
    private final Random random;
    private final boolean[] helperUsed = new boolean[HELPERS.length + 1];   // by size, 1 to 3
    private Round round;
    private int score;
    /** Every helper ever used this game, won back or not: the leaderboard's tie-break. */
    private int helperUses;
    private boolean over;
    /** Set once a game ends because the difficulty has no questions left; it ends as a win, not a loss. */
    private boolean outOfQuestions;
    private boolean big;
    /** The helper the last big question won back, 0 for none. */
    private int regained;

    Game(Deck deck, int level, Random random) {
        this.deck = deck;
        this.level = level;
        this.random = random;
        deal(deck.next(0));
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

    /** True while the question showing is the big question. */
    boolean big() {
        return big;
    }

    /** The helper (1 to 3) won back by the big question just answered, or 0. */
    int regained() {
        return regained;
    }

    /** 1-based number of the question showing. */
    int number() {
        return score + 1;
    }

    boolean helperLeft(int size) {
        return !helperUsed[size];
    }

    /** Helpers used this game, counting one that was won back: fewer is better on the leaderboard. */
    int helpersUsed() {
        return helperUses;
    }

    /** The largest helper used and not won back yet, or 0: what the big question can win back. */
    int toWinBack() {
        for (int i = HELPERS.length - 1; i >= 0; i--) if (helperUsed[HELPERS[i]]) return HELPERS[i];
        return 0;
    }

    /** Whether the helper can be used now: not used before, and enough wrong answers left on this question. */
    boolean canUse(int size) {
        return !over && round != null && helperLeft(size) && round.wrongLeft() >= size;
    }

    /** Uses the helper on the question showing; the slots it removed. */
    List<Integer> useHelper(int size) {
        if (!canUse(size)) throw new IllegalStateException("helper " + size + " cannot be used now");
        helperUsed[size] = true;
        helperUses++;
        return round.removeWrong(size, random);
    }

    /** Answers the question showing. A right answer waits for the next question; a wrong one ends the game. */
    Result answer(int slot) {
        if (over) throw new IllegalStateException("the game is over");
        if (round.removed(slot)) throw new IllegalArgumentException("slot " + slot + " was removed");
        if (slot == round.correctSlot()) {
            score++;
            if (big) {
                regained = toWinBack();
                if (regained > 0) helperUsed[regained] = false;
            }
            return Result.RIGHT;
        }
        over = true;
        return Result.WRONG;
    }

    /** The break due before the next question, after a right answer. */
    Break pending() {
        if (over || score == 0 || score % SUBJECT_EVERY != 0) return Break.NONE;
        if (score % BIG_EVERY == 0 && toWinBack() > 0) return Break.BIG;
        return subjectChoices().size() >= SUBJECT_CHOICES ? Break.SUBJECT : Break.NONE;
    }

    /** Two subjects that still have a question for the next position, to pick from. */
    List<String> subjectChoices() {
        List<String> all = deck.subjects(score);
        return all.subList(0, Math.min(SUBJECT_CHOICES, all.size()));
    }

    /** The next question after a right answer; false when the difficulty has run out, which ends the game. */
    boolean next() {
        return !over && deal(deck.next(score));
    }

    /** The next question from the subject the player picked. */
    boolean next(String subject) {
        return !over && deal(deck.next(score, subject));
    }

    /** The big question, taken up by the player. */
    boolean nextBig() {
        if (over) return false;
        boolean dealt = deal(deck.big());
        big = dealt;
        return dealt;
    }

    private boolean deal(Question q) {
        big = false;
        regained = 0;
        if (q == null) {
            over = true;
            outOfQuestions = true;
            round = null;
            return false;
        }
        round = new Round(q, random);
        return true;
    }
}
