package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Trivia in place of the greeting, from a theme's trivia.txt (UTF-8): one tidbit a line as
 * {@code subject|age|text}, e.g. {@code efnafraedi|13|Kvikasilfur (Hg) og bróm (Br) eru ...}. Home shows only the
 * text; subject and age are there to keep the list balanced. Blank lines and lines starting with # are skipped,
 * and a line without | is all text.
 *
 * The tidbits come as a shuffled deck: every one is shown once before any repeats, the place in the deck survives a
 * restart, and the next deck never starts with the last one shown. A theme push with a changed list starts a new
 * deck. A subject with three times the lines comes up three times as often.
 */
final class Trivia {
    private static final String TAG = "TvHome.Trivia";
    private static final String PREFS = "trivia";

    private Trivia() { }

    /** The tidbits in file, or none when it is missing or unreadable (Home then shows the greeting). */
    static List<String> read(File file) {
        if (file == null) return Collections.emptyList();
        List<String> lines = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file),
                StandardCharsets.UTF_8))) {
            for (String line; (line = in.readLine()) != null; ) lines.add(line);
        } catch (Exception e) {
            Log.w(TAG, "could not read " + file + ": " + e.getMessage());
            return Collections.emptyList();
        }
        return parse(lines);
    }

    static List<String> parse(List<String> lines) {
        List<String> texts = new ArrayList<>();
        for (String line : lines) {
            String text = !line.isEmpty() && line.charAt(0) == 0xFEFF ? line.substring(1) : line;   // Notepad's byte-order mark
            if (text.trim().isEmpty() || text.trim().startsWith("#")) continue;
            String[] fields = text.split("\\|", 3);
            text = fields[fields.length - 1].trim();
            if (!text.isEmpty()) texts.add(text);
        }
        return texts;
    }

    /** Where Home is in the deck; kept in SharedPreferences between restarts. */
    static final class Deck {
        long seed;
        int position;
        /** The tidbit the new deck must not start with: the one shown last; -1 for none. */
        int avoid = -1;
        /** Which list the deck was dealt from (stamp()); a different list deals a new deck. */
        String stamp = "";
    }

    static String stamp(List<String> texts) {
        return texts.size() + ":" + texts.hashCode();
    }

    /** The deck's order: every index once, shuffled by seed, avoid moved off the front when there is a choice. */
    static int[] order(int size, long seed, int avoid) {
        List<Integer> indexes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) indexes.add(i);
        Collections.shuffle(indexes, new Random(seed));
        if (size > 1 && indexes.get(0) == avoid) Collections.swap(indexes, 0, 1);
        int[] order = new int[size];
        for (int i = 0; i < size; i++) order[i] = indexes.get(i);
        return order;
    }

    /** The index of the next tidbit to show, moving deck on; a new list or a finished deck deals a new one. */
    static int next(Deck deck, List<String> texts, Random random) {
        String stamp = stamp(texts);
        if (!stamp.equals(deck.stamp)) {
            deck.stamp = stamp;
            deck.avoid = -1;
            deal(deck, random);
        } else if (deck.position >= texts.size()) {
            deck.avoid = order(texts.size(), deck.seed, deck.avoid)[texts.size() - 1];
            deal(deck, random);
        }
        return order(texts.size(), deck.seed, deck.avoid)[deck.position++];
    }

    private static void deal(Deck deck, Random random) {
        deck.seed = random.nextLong();
        deck.position = 0;
    }

    /** next() with the deck kept in the launcher's preferences. texts must not be empty. */
    static String next(Context ctx, List<String> texts) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Deck deck = new Deck();
        deck.seed = prefs.getLong("seed", 0);
        deck.position = prefs.getInt("position", 0);
        deck.avoid = prefs.getInt("avoid", -1);
        deck.stamp = prefs.getString("stamp", "");
        String text = texts.get(next(deck, texts, new Random()));
        prefs.edit().putLong("seed", deck.seed).putInt("position", deck.position).putInt("avoid", deck.avoid)
                .putString("stamp", deck.stamp).apply();
        return text;
    }
}
