"""Keep the quiz add-on's question bank (addons/quiz) in the shape the game expects.

One question per line: subject|age|question|right answer|wrong|wrong|wrong|fact. The game shuffles the answers,
so the right one is always written first. The fact is shown after a wrong answer: a short line that teaches
something. Lines starting with # are comments; "#?" holds a question back for review. How hard a question may be
is set out at the top of the file: an average person of that age should get 5 to 10 right in a row.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from collections import Counter
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_tcl_trivia import SUBJECTS  # noqa: E402

QUESTIONS = Path(__file__).resolve().parents[1] / "addons/quiz/app/src/main/assets/questions.txt"
AGES = {"7", "10", "13", "16"}
MAX_QUESTION = 120   # three lines on the question card at 1920x1080
MAX_ANSWER = 40      # one line in an answer box of the 2x2 grid
MAX_FACT = 160


def questions():
    """(line number, fields) for every question line."""
    out = []
    for n, line in enumerate(QUESTIONS.read_text(encoding="utf-8").split("\n"), 1):
        if line.strip() and not line.startswith("#"):
            out.append((n, line.split("|")))
    return out


class QuizQuestionsTest(unittest.TestCase):
    def test_every_line_has_the_eight_fields(self):
        for n, f in questions():
            with self.subTest(line=n):
                self.assertEqual(len(f), 8, "subject|age|question|right|wrong|wrong|wrong|fact")
                self.assertTrue(all(x.strip() == x and x for x in f), "no empty fields, no spaces round a |")

    def test_subject_and_age(self):
        for n, f in questions():
            with self.subTest(line=n):
                self.assertIn(f[0], SUBJECTS)
                self.assertIn(f[1], AGES)

    def test_the_question_is_a_question(self):
        for n, f in questions():
            with self.subTest(line=n, question=f[2]):
                self.assertTrue(f[2].endswith("?"), "ends with a question mark")
                self.assertLessEqual(len(f[2]), MAX_QUESTION)

    def test_four_different_answers_that_fit(self):
        for n, f in questions():
            with self.subTest(line=n, question=f[2]):
                answers = f[3:7]
                self.assertEqual(len({a.casefold() for a in answers}), 4, "the four answers differ")
                for a in answers:
                    self.assertLessEqual(len(a), MAX_ANSWER, a)
                    self.assertFalse(a.endswith("."), f"no full stop after an answer: {a}")

    def test_the_fact_is_a_statement(self):
        for n, f in questions():
            with self.subTest(line=n):
                self.assertLessEqual(len(f[7]), MAX_FACT)
                self.assertNotIn("?", f[7])

    def test_no_question_twice(self):
        texts = Counter(f[2] for _, f in questions())
        self.assertEqual([t for t, c in texts.items() if c > 1], [])

    def test_every_difficulty_has_questions(self):
        ages = Counter(f[1] for _, f in questions())
        for age in AGES:
            self.assertGreaterEqual(ages[age], 10, f"age {age} has too few questions for a game")


if __name__ == "__main__":
    unittest.main()
