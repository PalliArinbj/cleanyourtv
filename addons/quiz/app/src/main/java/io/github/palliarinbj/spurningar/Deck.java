package io.github.palliarinbj.spurningar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Picks the questions of one game: a short warm-up from the age below the chosen difficulty, then the difficulty's
 * own age. Questions the player has seen are left for last, and no question comes twice in a game. A question can
 * also be asked for from one subject (the subject pick) or from the age above (the big question).
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
        return next(position, null);
    }

    /** The next question for that position from one subject, or from any when subject is null. */
    Question next(int position, String subject) {
        Question q = pick(ageAt(position), subject);
        if (q == null && ageAt(position) != level) q = pick(level, subject);
        if (q == null && subject != null) q = pick(level, null);
        return take(q);
    }

    /** The big question: from the age above the difficulty, or from the difficulty itself at the top. */
    Question big() {
        int above = above(level);
        Question q = above > 0 ? pick(above, null) : null;
        if (q == null) q = pick(level, null);
        return take(q);
    }

    /** The subjects that still have a question for that position, in random order. */
    List<String> subjects(int position) {
        Set<String> out = new LinkedHashSet<>();
        int age = ageAt(position);
        for (Question q : all) if (q.age == age && !used.contains(q.id())) out.add(q.subject);
        List<String> list = new ArrayList<>(out);
        Collections.shuffle(list, random);
        return list;
    }

    private Question take(Question q) {
        if (q != null) used.add(q.id());
        return q;
    }

    private Question pick(int age, String subject) {
        List<Question> fresh = new ArrayList<>(), old = new ArrayList<>();
        for (Question q : all) {
            if (q.age != age || used.contains(q.id()) || (subject != null && !subject.equals(q.subject))) continue;
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

    /** The age group above this one, or 0 for the oldest. */
    static int above(int age) {
        int[] ages = QuestionBank.AGES;
        for (int i = 0; i < ages.length - 1; i++) if (ages[i] == age) return ages[i + 1];
        return 0;
    }
}
