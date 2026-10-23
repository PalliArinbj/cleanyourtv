"""Keep the TCL 75C745 theme's trivia.txt in the shape the launcher and the plan expect.

Home shows each line's text in place of the greeting, up to two lines over the clock (themes/README.md, Trivia).
The subject and age fields are what keeps the list balanced as it grows into the hundreds: chemistry about three
times a normal subject, and every age from 7 to adult.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from collections import Counter
from pathlib import Path
import re
import unittest


TRIVIA = Path(__file__).resolve().parents[1] / "examples/tcl-75c745-google-tv/trivia.txt"
SUBJECTS = {
    "saga-islands", "mannkynssaga", "nattura-islands", "dyr", "geimurinn", "mannslikaminn", "edlisfraedi",
    "efnafraedi", "islensk-tunga", "bokmenntir", "landafraedi", "taekni", "staerdfraedi", "ithrottir", "listir",
    "plontur",
}
AGES = {"7", "10", "13", "16"}
MAX_LENGTH = 130   # two lines over the clock at 1920x1080
# Atomic number, symbol and Icelandic name, as in the periodic table of the Icelandic school book
# Efnafræði (namsbokasafn.is/efnafraedi-2e/lotukerfi, checked 2026-10-23).
ELEMENTS = """
1 H Vetni|2 He Helíum|3 Li Litíum|4 Be Beryllíum|5 B Bór|6 C Kolefni|7 N Nitur|8 O Súrefni|9 F Flúor|10 Ne Neon
11 Na Natríum|12 Mg Magnesíum|13 Al Ál|14 Si Kísill|15 P Fosfór|16 S Brennisteinn|17 Cl Klór|18 Ar Argon|19 K Kalíum
20 Ca Kalsíum|21 Sc Skandíum|22 Ti Títan|23 V Vanadíum|24 Cr Króm|25 Mn Mangan|26 Fe Járn|27 Co Kóbalt|28 Ni Nikkel
29 Cu Kopar|30 Zn Sink|31 Ga Gallíum|32 Ge Germaníum|33 As Arsen|34 Se Selen|35 Br Bróm|36 Kr Krypton|37 Rb Rúbidíum
38 Sr Strontíum|39 Y Yttríum|40 Zr Sirkón|41 Nb Níóbíum|42 Mo Mólýbden|43 Tc Teknetíum|44 Ru Rúteníum|45 Rh Ródíum
46 Pd Palladíum|47 Ag Silfur|48 Cd Kadmíum|49 In Indíum|50 Sn Tin|51 Sb Antímón|52 Te Tellúríum|53 I Joð|54 Xe Xenon
55 Cs Sesíum|56 Ba Baríum|57 La Lantan|58 Ce Seríum|59 Pr Praseódým|60 Nd Neódým|61 Pm Prómetíum|62 Sm Samaríum
63 Eu Evrópíum|64 Gd Gadólíníum|65 Tb Terbíum|66 Dy Dysprósíum|67 Ho Hólmíum|68 Er Erbíum|69 Tm Túlíum|70 Yb Ytterbíum
71 Lu Lútetíum|72 Hf Hafníum|73 Ta Tantal|74 W Wolfram|75 Re Reníum|76 Os Osmíum|77 Ir Iridíum|78 Pt Platína|79 Au Gull
80 Hg Kvikasilfur|81 Tl Tallíum|82 Pb Blý|83 Bi Bismút|84 Po Pólóníum|85 At Astat|86 Rn Radon|87 Fr Fransíum|88 Ra Radíum
89 Ac Aktíníum|90 Th Þóríum|91 Pa Prótaktín|92 U Úran|93 Np Neptúníum|94 Pu Plútóníum|95 Am Ameríkíum|96 Cm Curíum
97 Bk Berkelíum|98 Cf Kaliforníum|99 Es Einsteiníum|100 Fm Fermíum|101 Md Mendelevíum|102 No Nóbelíum|103 Lr Lárensíum
104 Rf Rutherfordíum|105 Db Dubníum|106 Sg Seaborgíum|107 Bh Bohríum|108 Hs Hassíum|109 Mt Meitneríum|110 Ds Darmstadtíum
111 Rg Röntgeníum|112 Cn Kópernísíum|113 Nh Níhóníum|114 Fl Flerovíum|115 Mc Moskóvíum|116 Lv Livermóríum|117 Ts Tennessín
118 Og Oganessón
"""
CARD = re.compile(r"^(\S+) \((\w+)\) hefur sætistöluna (\d+)\b")


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

    def test_every_element_has_one_card_with_its_name_symbol_and_number(self):
        table = {}
        for entry in ELEMENTS.replace("\n", "|").split("|"):
            if entry.strip():
                number, symbol, name = entry.split()
                table[int(number)] = (name, symbol)
        self.assertEqual(list(range(1, 119)), sorted(table))
        cards = Counter()
        for n, line in entries():
            match = CARD.match(line.split("|", 2)[-1])
            if not match:
                continue
            name, symbol, number = match.group(1), match.group(2), int(match.group(3))
            with self.subTest(line=n):
                self.assertEqual(table.get(number), (name, symbol))
            cards[number] += 1
        self.assertEqual([], [z for z in table if cards[z] != 1], "elements without exactly one card")

    def test_the_file_reads_as_utf8_without_a_byte_order_mark(self):
        self.assertFalse(TRIVIA.read_bytes().startswith(b"\xef\xbb\xbf"))


if __name__ == "__main__":
    unittest.main()
