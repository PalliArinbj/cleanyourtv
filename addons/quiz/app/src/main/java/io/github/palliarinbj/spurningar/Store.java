package io.github.palliarinbj.spurningar;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the quiz keeps on the TV, shared by everyone in the household: the four leaderboards, the names used on them
 * (most recent first, offered again so nobody types twice) and the questions already asked, so repeats come last.
 */
final class Store {
    private static final String BOARD = "board_";
    private static final String NAMES = "names";
    private static final String SEEN = "seen";
    private static final String LAST_LEVEL = "last_level";
    static final int MAX_NAMES = 8;
    /** Enough to cover the whole bank; the oldest ids go first when it is full. */
    static final int MAX_SEEN = 4000;

    private final SharedPreferences prefs;

    Store(Context context) {
        prefs = context.getSharedPreferences("quiz", Context.MODE_PRIVATE);
    }

    Leaderboard board(int level) {
        return Leaderboard.parse(prefs.getString(BOARD + level, ""));
    }

    void saveBoard(int level, Leaderboard board) {
        prefs.edit().putString(BOARD + level, board.serialize()).apply();
    }

    List<String> names() {
        String all = prefs.getString(NAMES, "");
        List<String> out = new ArrayList<>();
        for (String n : all.split("\n")) if (!n.isEmpty()) out.add(n);
        return out;
    }

    /** Puts the name first in the list of names offered, dropping the oldest past MAX_NAMES. */
    void useName(String name) {
        prefs.edit().putString(NAMES, String.join("\n", recentFirst(names(), name, MAX_NAMES))).apply();
    }

    static List<String> recentFirst(List<String> names, String name, int max) {
        List<String> out = new ArrayList<>();
        out.add(name);
        for (String n : names) if (out.size() < max && !n.equalsIgnoreCase(name)) out.add(n);
        return out;
    }

    Set<String> seen() {
        return new HashSet<>(seenInOrder());
    }

    void markSeen(String id) {
        LinkedHashSet<String> seen = seenInOrder();
        seen.remove(id);
        seen.add(id);
        List<String> list = new ArrayList<>(seen);
        if (list.size() > MAX_SEEN) list = list.subList(list.size() - MAX_SEEN, list.size());
        prefs.edit().putString(SEEN, String.join(",", list)).apply();
    }

    private LinkedHashSet<String> seenInOrder() {
        String all = prefs.getString(SEEN, "");
        return all.isEmpty() ? new LinkedHashSet<>() : new LinkedHashSet<>(Arrays.asList(all.split(",")));
    }

    int lastLevel() {
        return prefs.getInt(LAST_LEVEL, QuestionBank.AGES[0]);
    }

    void setLastLevel(int level) {
        prefs.edit().putInt(LAST_LEVEL, level).apply();
    }
}
