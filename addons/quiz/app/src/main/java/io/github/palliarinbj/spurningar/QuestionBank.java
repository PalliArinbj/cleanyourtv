package io.github.palliarinbj.spurningar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads questions.txt: one question per line, subject|age|question|right answer|wrong|wrong|wrong|fact.
 * Lines starting with # are comments; "#?" marks a question held back for review. A line that breaks the format
 * is skipped rather than shown half-right (tests/test_quiz_questions.py keeps the file clean).
 */
final class QuestionBank {
    static final int[] AGES = {7, 10, 13, 16};

    private QuestionBank() {}

    static List<Question> parse(Reader reader) throws IOException {
        List<Question> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        BufferedReader in = new BufferedReader(reader);
        String line;
        while ((line = in.readLine()) != null) {
            Question q = parseLine(line);
            if (q != null && seen.add(q.text)) out.add(q);
        }
        return out;
    }

    static Question parseLine(String line) {
        if (line == null) return null;
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) return null;
        String[] f = line.split("\\|", -1);
        if (f.length != 7 && f.length != 8) return null;
        for (int i = 0; i < 7; i++) {
            f[i] = f[i].trim();
            if (f[i].isEmpty()) return null;
        }
        int age;
        try {
            age = Integer.parseInt(f[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (!validAge(age)) return null;
        String[] answers = {f[3], f[4], f[5], f[6]};
        Set<String> distinct = new HashSet<>();
        for (String a : answers) if (!distinct.add(a.toLowerCase(Locale.ROOT))) return null;
        return new Question(f[0], age, f[2], answers, f.length == 8 ? f[7].trim() : "");
    }

    static boolean validAge(int age) {
        for (int a : AGES) if (a == age) return true;
        return false;
    }
}
