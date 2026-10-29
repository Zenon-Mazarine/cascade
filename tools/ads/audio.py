"""Soundtracks for the ads: a synthesized music bed, plus Cascade's own interface sounds placed on cue."""
import math
import os
import wave

import numpy as np

SR = 44100
RAW = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "res", "raw")

NOTE = {n: i for i, n in enumerate(["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"])}


def hz(name):
    """'A3' → 220.0"""
    n, o = name[:-1], int(name[-1])
    return 440.0 * 2 ** ((NOTE[n] + 12 * (o + 1) - 69) / 12)


def lowpass(x, cutoff):
    a = math.exp(-2 * math.pi * cutoff / SR)
    y = np.empty_like(x)
    acc = 0.0
    # Vectorised one-pole via scipy-free recursion in chunks.
    b = 1 - a
    for i in range(len(x)):
        acc = b * x[i] + a * acc
        y[i] = acc
    return y


def lp_fast(x, cutoff, passes=1):
    """A cheap low-pass: moving average sized to the cutoff (fine for pads and noise)."""
    k = max(1, int(SR / (cutoff * 2.2)))
    ker = np.ones(k) / k
    for _ in range(passes):
        x = np.convolve(x, ker, mode="same")
    return x


def env(n, a, d, s=0.0, r=0.0, total=None):
    """ADSR-ish: attack a, decay d to sustain s, release r at the end (seconds)."""
    t = np.arange(n) / SR
    e = np.where(t < a, t / max(a, 1e-4), s + (1 - s) * np.exp(-(t - a) / max(d, 1e-4)))
    if r and total:
        rel = np.clip((total - t) / r, 0, 1)
        e = e * rel
    return e


def saw(f, n, detune=0.0):
    t = np.arange(n) / SR
    out = np.zeros(n)
    for dv in (-detune, 0.0, detune):
        ph = (t * f * (1 + dv)) % 1.0
        out += 2 * ph - 1
    return out / 3


def sine(f, n, phase=0.0):
    t = np.arange(n) / SR
    return np.sin(2 * math.pi * f * t + phase)


def load_wav(name):
    with wave.open(os.path.join(RAW, name + ".wav")) as w:
        data = np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32) / 32768
    return data


class Track:
    def __init__(self, seconds):
        self.n = int(seconds * SR)
        self.L = np.zeros(self.n)
        self.R = np.zeros(self.n)
        self.duck = np.ones(self.n)

    def add(self, sig, at, gain=1.0, pan=0.0, ducks=False):
        i = int(at * SR)
        if i >= self.n:
            return
        sig = sig[: self.n - i] * gain
        l = math.cos((pan + 1) * math.pi / 4)
        r = math.sin((pan + 1) * math.pi / 4)
        self.L[i:i + len(sig)] += sig * l * 1.414
        self.R[i:i + len(sig)] += sig * r * 1.414

    def sfx(self, name, at, gain=0.8, pan=0.0, rate=1.0):
        s = load_wav(name)
        if rate != 1.0:
            idx = np.arange(0, len(s) - 1, rate)
            s = np.interp(idx, np.arange(len(s)), s)
        self.add(s, at, gain, pan)

    def sidechain(self, at, depth=0.45, release=0.25):
        i = int(at * SR)
        m = int(release * SR)
        if i >= self.n:
            return
        curve = 1 - depth * np.exp(-np.arange(min(m, self.n - i)) / (m / 4))
        self.duck[i:i + len(curve)] = np.minimum(self.duck[i:i + len(curve)], curve)

    def save(self, path, music=None, music_gain=1.0):
        L, R = self.L.copy(), self.R.copy()
        if music is not None:
            L += music[0][: self.n] * self.duck[: len(music[0])] * music_gain
            R += music[1][: self.n] * self.duck[: len(music[1])] * music_gain
        peak = max(np.abs(L).max(), np.abs(R).max(), 1e-6)
        g = 0.89 / peak
        # Gentle fade at the very end.
        f = int(0.05 * SR)
        fade = np.ones(self.n)
        fade[-f:] = np.linspace(1, 0, f)
        st = np.stack([L * g * fade, R * g * fade], axis=1)
        st = np.tanh(st * 1.2) / np.tanh(1.2)
        with wave.open(path, "wb") as w:
            w.setnchannels(2)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes((st * 32767).astype(np.int16).tobytes())


def music_bed(seconds, bpm, chords, start_drums=0.0, end_drums=None, riser_at=None, impact_at=None, intensity=1.0):
    """chords: list of lists of note names, one chord per bar (cycled)."""
    n = int(seconds * SR)
    L = np.zeros(n)
    R = np.zeros(n)
    beat = 60.0 / bpm
    bar = beat * 4
    nbars = int(math.ceil(seconds / bar))
    rng = np.random.default_rng(3)

    def add(sig, at, gl, gr):
        i = int(at * SR)
        if i >= n:
            return
        s = sig[: n - i]
        L[i:i + len(s)] += s * gl
        R[i:i + len(s)] += s * gr

    for b in range(nbars):
        ch = chords[b % len(chords)]
        t0 = b * bar
        m = int((bar + 0.6) * SR)
        # Pad: detuned saws, softened.
        pad = np.zeros(m)
        for k, note in enumerate(ch):
            pad += saw(hz(note), m, detune=0.004 + 0.002 * k)
        pad = lp_fast(pad, 900, passes=2) * env(m, 0.5, 2.0, 0.7, 0.5, bar + 0.6) * 0.16
        add(pad, t0, 0.9, 1.0)
        # Sub bass on the root.
        root = hz(ch[0][:-1] + "2")
        sub = sine(root, m) * env(m, 0.02, 1.2, 0.6, 0.3, bar + 0.2) * 0.22
        add(sub, t0, 1.0, 1.0)
        # Pluck arpeggio in 8ths, an octave up.
        pattern = [0, 2, 1, 3, 2, 1, 3, 2]
        for s8 in range(8):
            note = ch[pattern[s8] % len(ch)]
            f = hz(note) * 2
            pm = int(0.45 * SR)
            pl = (sine(f, pm) + 0.3 * sine(2 * f, pm) + 0.1 * sine(3 * f, pm)) * env(pm, 0.003, 0.16) * 0.09 * intensity
            pan = 0.35 if s8 % 2 else -0.35
            add(pl, t0 + s8 * beat / 2, math.cos((pan + 1) * math.pi / 4) * 1.2, math.sin((pan + 1) * math.pi / 4) * 1.2)

    # Drums.
    if end_drums is None:
        end_drums = seconds
    t = start_drums
    k = 0
    while t < end_drums - 0.01:
        km = int(0.4 * SR)
        tt = np.arange(km) / SR
        f = 45 + 80 * np.exp(-tt / 0.03)
        kick = np.sin(2 * math.pi * np.cumsum(f) / SR) * np.exp(-tt / 0.18) * 0.55 * intensity
        if k % 2 == 0:
            add(kick, t, 1, 1)
        hm = int(0.06 * SR)
        hat = rng.uniform(-1, 1, hm)
        hat = (hat - lp_fast(hat, 6000)) * np.exp(-np.arange(hm) / SR / 0.012) * 0.10 * intensity
        add(hat, t + beat / 2, 0.8, 1.1)
        if k % 4 == 2:
            cm = int(0.25 * SR)
            cl = rng.uniform(-1, 1, cm)
            cl = lp_fast(cl - lp_fast(cl, 900), 5000) * np.exp(-np.arange(cm) / SR / 0.05) * 0.22 * intensity
            add(cl, t, 1.05, 0.95)
        t += beat
        k += 1

    if riser_at is not None and impact_at is not None:
        dur = impact_at - riser_at
        m = int(dur * SR)
        tt = np.arange(m) / SR
        noise = rng.uniform(-1, 1, m)
        noise = noise - lp_fast(noise, 300)
        rise = noise * (tt / dur) ** 2 * 0.25
        add(rise, riser_at, 1, 1)
    if impact_at is not None:
        m = int(2.5 * SR)
        tt = np.arange(m) / SR
        boom = np.sin(2 * math.pi * np.cumsum(38 + 60 * np.exp(-tt / 0.05)) / SR) * np.exp(-tt / 0.6) * 0.6
        shimmer = sum(sine(hz(nn) * 2, m) * np.exp(-tt / (1.4 - 0.2 * j)) for j, nn in enumerate(chords[0])) * 0.06
        add(boom + shimmer, impact_at, 1, 1)
    return L, R
