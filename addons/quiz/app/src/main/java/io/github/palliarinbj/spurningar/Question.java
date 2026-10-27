package io.github.palliarinbj.spurningar;

/** One question from questions.txt: the right answer first in {@link #answers}, then the three wrong ones. */
final class Question {
    final String subject;
    final int age;
    final String text;
    final String[] answers;
    /** The fact the question was made from, shown after a wrong answer; empty when there is none. */
    final String fact;

    Question(String subject, int age, String text, String[] answers, String fact) {
        this.subject = subject;
        this.age = age;
        this.text = text;
        this.answers = answers;
        this.fact = fact;
    }

    String correct() {
        return answers[0];
    }

    /** A short stable id for the seen list: the same question text gives the same id after an update. */
    String id() {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Long.toHexString(crc.getValue());
    }
}
