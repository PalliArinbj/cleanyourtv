package io.github.palliarinbj.spurningar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Picks the questions of one game: a short warm-up from the age below the chosen difficulty, then the difficulty's
 * own age. Questions the player has seen are left for last, and no question comes twice in a game.
 */
final class Deck {
    /** Questions from the age below before the difficulty's own; the youngest difficulty has none below it. */
    static final int WARM_UP = 2;

    private final List<Question> all;
    private final int level;
    private final Set<String> seen;
    private final Random random;
    private final Set<String> used = new HashSet<>();

    /** @param seen ids of questions this player has had before; read, never changed here */
    Deck(List<Question> all, int level, Set<String> seen, Random random) {
        this.all = all;
        this.level = level;
        this.seen = seen;
        this.random = random;
    }

    /** The age the question at this 0-based position in the game is drawn from. */
    int ageAt(int position) {
        int below = below(level);
        return position < WARM_UP && below > 0 ? below : level;
    }

    /** The next question for that position, or null when the difficulty has run out of questions. */
    Question next(int position) {
        Question q = pick(ageAt(position));
        if (q == null && ageAt(position) != level) q = pick(level);
        if (q != null) used.add(q.id());
        return q;
    }

    private Question pick(int age) {
        List<Question> fresh = new ArrayList<>(), old = new ArrayList<>();
        for (Question q : all) {
            if (q.age != age || used.contains(q.id())) continue;
            (seen.contains(q.id()) ? old : fresh).add(q);
        }
        List<Question> from = fresh.isEmpty() ? old : fresh;
        return from.isEmpty() ? null : from.get(random.nextInt(from.size()));
    }

    /** The age group below this one, or 0 for the youngest. */
    static int below(int age) {
        int[] ages = QuestionBank.AGES;
        for (int i = 1; i < ages.length; i++) if (ages[i] == age) return ages[i - 1];
        return 0;
    }
}
