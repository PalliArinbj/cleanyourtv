package io.github.palliarinbj.spurningar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** One question on screen: its answers in shuffled slots, and which slots a helper has removed. */
final class Round {
    static final int SLOTS = 4;

    final Question question;
    /** order[slot] is the index into question.answers shown in that slot; index 0 is the right answer. */
    private final int[] order = new int[SLOTS];
    private final boolean[] removed = new boolean[SLOTS];

    Round(Question question, Random random) {
        this.question = question;
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) indexes.add(i);
        Collections.shuffle(indexes, random);
        for (int i = 0; i < SLOTS; i++) order[i] = indexes.get(i);
    }

    String answer(int slot) {
        return question.answers[order[slot]];
    }

    int correctSlot() {
        for (int i = 0; i < SLOTS; i++) if (order[i] == 0) return i;
        throw new IllegalStateException();
    }

    boolean removed(int slot) {
        return removed[slot];
    }

    /** Wrong answers still showing. */
    int wrongLeft() {
        int n = 0;
        for (int i = 0; i < SLOTS; i++) if (!removed[i] && order[i] != 0) n++;
        return n;
    }

    /** Removes n of the wrong answers still showing, at random; the slots removed, in the order to animate them. */
    List<Integer> removeWrong(int n, Random random) {
        List<Integer> wrong = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) if (!removed[i] && order[i] != 0) wrong.add(i);
        if (n > wrong.size()) throw new IllegalArgumentException(n + " > " + wrong.size());
        Collections.shuffle(wrong, random);
        List<Integer> out = new ArrayList<>(wrong.subList(0, n));
        for (int slot : out) removed[slot] = true;
        return out;
    }
}
