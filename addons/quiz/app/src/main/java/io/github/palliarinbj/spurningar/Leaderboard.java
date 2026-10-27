package io.github.palliarinbj.spurningar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The top ten of one difficulty. Higher score first; on a tie, fewer helpers used, then whoever got there first.
 * Stored as one line per entry, score|helpers|time|name, so it needs no JSON and reads the same in tests.
 */
final class Leaderboard {
    static final int SIZE = 10;
    static final int MAX_NAME = 12;

    static final class Entry {
        final String name;
        final int score;
        final int helpers;
        final long time;

        Entry(String name, int score, int helpers, long time) {
            this.name = name;
            this.score = score;
            this.helpers = helpers;
            this.time = time;
        }
    }

    static final Comparator<Entry> ORDER = Comparator.<Entry>comparingInt(e -> -e.score)
            .thenComparingInt(e -> e.helpers)
            .thenComparingLong(e -> e.time);

    private final List<Entry> entries = new ArrayList<>();

    List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /** The 0-based place a result would take, or -1 when it does not make the board. A score of 0 never does. */
    int place(int score, int helpers, long time) {
        if (score <= 0) return -1;
        Entry e = new Entry("", score, helpers, time);
        int place = 0;
        while (place < entries.size() && ORDER.compare(entries.get(place), e) <= 0) place++;
        return place < SIZE ? place : -1;
    }

    /** Adds the result where it belongs and drops what falls off the end; its place, or -1. */
    int add(String name, int score, int helpers, long time) {
        int place = place(score, helpers, time);
        if (place < 0) return -1;
        entries.add(place, new Entry(cleanName(name), score, helpers, time));
        while (entries.size() > SIZE) entries.remove(entries.size() - 1);
        return place;
    }

    /** Trimmed, single spaces, no field separator, at most MAX_NAME characters. */
    static String cleanName(String name) {
        String n = name == null ? "" : name.replace('|', ' ').replaceAll("\\s+", " ").trim();
        return n.length() > MAX_NAME ? n.substring(0, MAX_NAME).trim() : n;
    }

    String serialize() {
        StringBuilder b = new StringBuilder();
        for (Entry e : entries) {
            b.append(e.score).append('|').append(e.helpers).append('|').append(e.time).append('|').append(e.name)
                    .append('\n');
        }
        return b.toString();
    }

    /** Reads what {@link #serialize()} wrote; broken lines are skipped and the order is rebuilt. */
    static Leaderboard parse(String text) {
        Leaderboard board = new Leaderboard();
        if (text == null) return board;
        for (String line : text.split("\n")) {
            String[] f = line.split("\\|", 4);
            if (f.length != 4) continue;
            try {
                int score = Integer.parseInt(f[0]), helpers = Integer.parseInt(f[1]);
                long time = Long.parseLong(f[2]);
                String name = cleanName(f[3]);
                if (score > 0 && helpers >= 0 && !name.isEmpty()) board.entries.add(new Entry(name, score, helpers, time));
            } catch (NumberFormatException ignored) {
            }
        }
        board.entries.sort(ORDER);
        while (board.entries.size() > SIZE) board.entries.remove(board.entries.size() - 1);
        return board;
    }
}
