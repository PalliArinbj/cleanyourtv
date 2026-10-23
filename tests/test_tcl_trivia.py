"""Keep the TCL 75C745 theme's trivia.txt in the shape the launcher and the plan expect.

Home shows each line's text in place of the greeting, up to two lines over the clock (themes/README.md, Trivia).
The subject and age fields are what keeps the list balanced as it grows into the hundreds: chemistry about three
times a normal subject, and every age from 7 to adult.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from collections import Counter
from pathlib import Path
import unittest


TRIVIA = Path(__file__).resolve().parents[1] / "examples/tcl-75c745-google-tv/trivia.txt"
SUBJECTS = {
    "saga-islands", "mannkynssaga", "nattura-islands", "dyr", "geimurinn", "mannslikaminn", "edlisfraedi",
    "efnafraedi", "islensk-tunga", "bokmenntir", "landafraedi", "taekni", "staerdfraedi", "ithrottir", "listir",
    "plontur",
}
AGES = {"7", "10", "13", "16"}
MAX_LENGTH = 130   # two lines over the clock at 1920x1080


def entries():
    lines = TRIVIA.read_text(encoding="utf-8").splitlines()
    return [(n, line) for n, line in enumerate(lines, 1) if line.strip() and not line.lstrip().startswith("#")]


class TriviaFileTest(unittest.TestCase):
    def test_every_line_is_subject_age_text(self):
        for n, line in entries():
            with self.subTest(line=n):
                fields = line.split("|", 2)
                self.assertEqual(3, len(fields), line)
                subject, age, text = fields
                self.assertIn(subject, SUBJECTS)
                self.assertIn(age, AGES)
                self.assertEqual(text, text.strip())
                self.assertTrue(text)

    def test_tidbits_fit_in_two_lines_and_keep_the_tone(self):
        for n, line in entries():
            text = line.split("|", 2)[-1]
            with self.subTest(line=n, text=text):
                self.assertLessEqual(len(text), MAX_LENGTH)
                self.assertNotIn("?", text, "a statement, not a question")
                self.assertNotIn("!", text, "no exclamation marks")

    def test_no_tidbit_twice(self):
        texts = Counter(line.split("|", 2)[-1] for _, line in entries())
        self.assertEqual([], [text for text, count in texts.items() if count > 1])

    def test_chemistry_has_at_least_three_times_a_normal_subject(self):
        counts = Counter(line.split("|", 1)[0] for _, line in entries())
        others = [count for subject, count in counts.items() if subject != "efnafraedi"]
        self.assertGreaterEqual(counts["efnafraedi"], 3 * max(others))

    def test_the_file_reads_as_utf8_without_a_byte_order_mark(self):
        self.assertFalse(TRIVIA.read_bytes().startswith(b"\xef\xbb\xbf"))


if __name__ == "__main__":
    unittest.main()
