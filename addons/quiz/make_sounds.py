"""Makes the quiz's sound effects into app/src/main/res/raw/ as 16-bit mono WAV, so there is nothing to license.

Each sound is built from sine tones with soft attacks and decays; change a recipe below and run it again:
    tools/venv/bin/python -I addons/quiz/make_sounds.py      (Windows: tools\\venv\\Scripts\\python.exe)
Needs numpy. The output is committed, so a build does not need Python.
"""
import pathlib
import wave

import numpy as np

RATE = 22050
OUT = pathlib.Path(__file__).resolve().parent / "app/src/main/res/raw"


def t(seconds):
    return np.arange(int(RATE * seconds)) / RATE


def envelope(n, attack=0.005, release=0.08):
    """A soft attack and an exponential fade, so nothing clicks."""
    e = np.ones(n)
    a = max(1, int(RATE * attack))
    e[:a] = np.linspace(0, 1, a)
    e *= np.exp(-np.arange(n) / (RATE * release))
    return e


def tone(freq, seconds, attack=0.005, release=0.15, harmonics=(1, 0.3, 0.1)):
    """A bell-ish note: the fundamental and a few quieter overtones."""
    x = t(seconds)
    wave_ = sum(h * np.sin(2 * np.pi * freq * (i + 1) * x) for i, h in enumerate(harmonics))
    return wave_ * envelope(len(x), attack, release)


def at(track, sound, start):
    """Mixes the sound into the track at start seconds."""
    i = int(RATE * start)
    end = min(len(track), i + len(sound))
    track[i:end] += sound[:end - i]
    return track


def silence(seconds):
    return np.zeros(int(RATE * seconds))


def note(name):
    """A4 = 440 Hz; names like C5, E5, G#4."""
    names = {"C": -9, "C#": -8, "D": -7, "D#": -6, "E": -5, "F": -4, "F#": -3, "G": -2, "G#": -1, "A": 0,
             "A#": 1, "B": 2}
    return 440 * 2 ** ((names[name[:-1]] + 12 * (int(name[-1]) - 4)) / 12)


def move():
    """Focus moving between answers: a short soft tick."""
    return tone(1800, 0.05, 0.001, 0.012, (1, 0.2)) * 0.35


def lock():
    """An answer locked in: a low thump, then a rising, trembling tone for the suspense."""
    x = t(1.15)
    freq = 220 + 220 * (x / x[-1]) ** 2
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    riser = (np.sin(phase) + 0.25 * np.sin(2 * phase)) * (0.55 + 0.45 * np.sin(2 * np.pi * 9 * x))
    riser *= np.linspace(0.05, 0.6, len(x)) * envelope(len(x), 0.05, 10)
    return at(riser * 0.45, tone(110, 0.25, 0.002, 0.06, (1, 0.5)) * 0.8, 0)


def right():
    """A right answer: a bright rising arpeggio."""
    track = silence(0.9)
    for i, n in enumerate(["C5", "E5", "G5", "C6"]):
        at(track, tone(note(n), 0.6, 0.003, 0.18), i * 0.07)
    return track * 0.3


def wrong():
    """A wrong answer: two low notes sliding down."""
    track = silence(0.9)
    at(track, tone(note("D#3"), 0.35, 0.01, 0.2, (1, 0.6, 0.3)), 0)
    x = t(0.55)
    freq = note("C3") * (1 - 0.12 * x / x[-1])
    slide = np.sin(2 * np.pi * np.cumsum(freq) / RATE) + 0.5 * np.sin(4 * np.pi * np.cumsum(freq) / RATE)
    at(track, slide * envelope(len(x), 0.01, 0.3), 0.28)
    return track * 0.35


def helper():
    """A helper used: a falling sparkle as the wrong answers break away."""
    track = silence(0.7)
    rng = np.random.default_rng(3)
    for i in range(9):
        at(track, tone(2400 - i * 180 + rng.uniform(-60, 60), 0.18, 0.001, 0.05, (1, 0.2)), i * 0.045)
    return track * 0.22


def milestone():
    """Five more in a row: a short fanfare."""
    track = silence(1.2)
    for start, n in [(0, "G4"), (0.12, "C5"), (0.24, "E5"), (0.36, "G5")]:
        at(track, tone(note(n), 0.5, 0.004, 0.2, (1, 0.5, 0.2)), start)
    for n in ["C5", "E5", "G5", "C6"]:
        at(track, tone(note(n), 0.8, 0.01, 0.35, (1, 0.4, 0.15)), 0.5)
    return track * 0.18


def fanfare():
    """A place on the leaderboard: a longer fanfare."""
    track = silence(2.0)
    melody = [(0, "C5"), (0.15, "C5"), (0.3, "C5"), (0.45, "G5"), (0.75, "E5"), (0.9, "G5")]
    for start, n in melody:
        at(track, tone(note(n), 0.45, 0.004, 0.18, (1, 0.5, 0.25)), start)
    for n in ["C5", "E5", "G5", "C6", "E6"]:
        at(track, tone(note(n), 1.0, 0.01, 0.45, (1, 0.4, 0.15)), 1.05)
    return track * 0.15


def start():
    """A game starting: a quick upward sweep."""
    x = t(0.35)
    freq = 300 + 900 * (x / x[-1])
    sweep = np.sin(2 * np.pi * np.cumsum(freq) / RATE) * envelope(len(x), 0.02, 0.12)
    return at(sweep * 0.25, tone(note("C6"), 0.3, 0.002, 0.1) * 0.2, 0.25)


def write(name, samples):
    samples = np.clip(samples, -1, 1)
    data = (samples * 32767).astype("<i2").tobytes()
    with wave.open(str(OUT / f"{name}.wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(data)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, make in [("sfx_move", move), ("sfx_lock", lock), ("sfx_right", right), ("sfx_wrong", wrong),
                       ("sfx_helper", helper), ("sfx_milestone", milestone), ("sfx_fanfare", fanfare),
                       ("sfx_start", start)]:
        write(name, make())
        print(name, (OUT / f"{name}.wav").stat().st_size)
