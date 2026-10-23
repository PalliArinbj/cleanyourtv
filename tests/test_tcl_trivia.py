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
# Atomic number, symbol and Icelandic name, as in is.wikipedia.org/wiki/Listi_yfir_frumefni (checked 2026-10-23).
ELEMENTS = """
1 H Vetni|2 He Helín|3 Li Litín|4 Be Beryllín|5 B Bór|6 C Kolefni|7 N Köfnunarefni|8 O Súrefni|9 F Flúor|10 Ne Neon
11 Na Natrín|12 Mg Magnesín|13 Al Ál|14 Si Kísill|15 P Fosfór|16 S Brennisteinn|17 Cl Klór|18 Ar Argon|19 K Kalín
20 Ca Kalsín|21 Sc Skandín|22 Ti Títan|23 V Vanadín|24 Cr Króm|25 Mn Mangan|26 Fe Járn|27 Co Kóbolt|28 Ni Nikkel
29 Cu Kopar|30 Zn Sink|31 Ga Gallín|32 Ge German|33 As Arsen|34 Se Selen|35 Br Bróm|36 Kr Krypton|37 Rb Rúbidín
38 Sr Strontín|39 Y Yttrín|40 Zr Sirkon|41 Nb Níóbín|42 Mo Mólýbden|43 Tc Teknetín|44 Ru Rúþen|45 Rh Ródín
46 Pd Palladín|47 Ag Silfur|48 Cd Kadmín|49 In Indín|50 Sn Tin|51 Sb Antimon|52 Te Tellúr|53 I Joð|54 Xe Xenon
55 Cs Sesín|56 Ba Barín|57 La Lantan|58 Ce Serín|59 Pr Praseódým|60 Nd Neódým|61 Pm Prómetín|62 Sm Samarín
63 Eu Evrópín|64 Gd Gadólín|65 Tb Terbín|66 Dy Dysprósín|67 Ho Hólmín|68 Er Erbín|69 Tm Túlín|70 Yb Ytterbín
71 Lu Lútetín|72 Hf Hafnín|73 Ta Tantal|74 W Volfram|75 Re Renín|76 Os Osmín|77 Ir Iridín|78 Pt Platína|79 Au Gull
80 Hg Kvikasilfur|81 Tl Þallín|82 Pb Blý|83 Bi Bismút|84 Po Pólon|85 At Astat|86 Rn Radon|87 Fr Fransín|88 Ra Radín
89 Ac Aktín|90 Th Þórín|91 Pa Protaktín|92 U Úran|93 Np Neptúnín|94 Pu Plúton|95 Am Ameríkín|96 Cm Kúrín
97 Bk Berkelín|98 Cf Kalifornín|99 Es Einsteinín|100 Fm Fermín|101 Md Mendelevín|102 No Nóbelín|103 Lr Lawrensín
104 Rf Rutherfordín|105 Db Dubnín|106 Sg Seborgín|107 Bh Bohrín|108 Hs Hassín|109 Mt Meitnerín|110 Ds Darmstadtín
111 Rg Röntgenín|112 Cn Kópernikín|113 Nh Níhonín|114 Fl Fleróvín|115 Mc Moskóvín|116 Lv Livermorín|117 Ts Tennessín
118 Og Oganesson
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
