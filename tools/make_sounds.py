#!/usr/bin/env python3
"""Synthesizes Cascade's interface sounds into app/src/main/res/raw/.

Everything is generated from scratch (sine sweeps, filtered noise, Karplus-Strong plucks),
so there are no third-party audio assets. Re-run after tweaking:  python3 tools/make_sounds.py
"""
import math
import os
import random
import struct
import wave

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")
random.seed(7)


def silence(seconds):
    return [0.0] * int(SR * seconds)


def mix(dst, src, at=0.0, gain=1.0):
    start = int(at * SR)
    need = start + len(src)
    if need > len(dst):
        dst.extend([0.0] * (need - len(dst)))
    for i, v in enumerate(src):
        dst[start + i] += v * gain
    return dst


def sweep(f0, f1, sweep_time, decay, dur, attack=0.002, harmonic=0.15):
    """A sine whose pitch glides from f0 to f1: the classic water-drop 'bloop'."""
    out, phase = [], 0.0
    for i in range(int(dur * SR)):
        t = i / SR
        k = min(t / sweep_time, 1.0)
        f = f0 * (f1 / f0) ** k
        phase += 2 * math.pi * f / SR
        env = (1 - math.exp(-t / attack)) * math.exp(-t / decay)
        out.append(env * (math.sin(phase) + harmonic * math.sin(2 * phase)))
    return out


def noise(dur):
    return [random.uniform(-1, 1) for _ in range(int(dur * SR))]


def one_pole(sig, cutoff):
    a = math.exp(-2 * math.pi * cutoff / SR)
    out, y = [], 0.0
    for x in sig:
        y = (1 - a) * x + a * y
        out.append(y)
    return out


def bandpass(sig, lo, hi):
    high = one_pole(sig, hi)
    low = one_pole(sig, lo)
    return [h - l for h, l in zip(high, low)]


def envelope(sig, attack, decay):
    out = []
    for i, v in enumerate(sig):
        t = i / SR
        env = min(t / attack, 1.0) if attack > 0 else 1.0
        if t > attack:
            env *= math.exp(-(t - attack) / decay)
        out.append(v * env)
    return out


def pluck(freq, dur, damping=0.996, brightness=0.5):
    """Karplus-Strong string: a soft, koto-like pluck."""
    n = int(SR / freq)
    buf = one_pole(noise(n / SR + 0.001), 2500 + 4000 * brightness)[:n]
    out = []
    for i in range(int(dur * SR)):
        v = buf[i % n]
        nxt = buf[(i + 1) % n]
        buf[i % n] = damping * 0.5 * (v + nxt)
        out.append(v)
    return out


def tone(freq, decay, dur, partials=((1, 1.0),)):
    out = []
    for i in range(int(dur * SR)):
        t = i / SR
        s = sum(a * math.sin(2 * math.pi * freq * m * t) for m, a in partials)
        out.append(s * math.exp(-t / decay) * min(t / 0.0008, 1.0))
    return out


def write(name, sig, peak=0.8, fade=0.01):
    m = max(1e-9, max(abs(v) for v in sig))
    sig = [v / m * peak for v in sig]
    nf = int(fade * SR)
    for i in range(min(nf, len(sig))):
        sig[-1 - i] *= i / nf
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name + ".wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, v)) * 32767)) for v in sig))
    print(f"{name}.wav  {len(sig) / SR:.2f}s")


# ── Water ─────────────────────────────────────────────────────────────
write("water_drop", sweep(380, 1250, 0.05, 0.035, 0.18))
write("water_bubble", sweep(620, 1100, 0.03, 0.018, 0.09), peak=0.7)

soft = sweep(180, 420, 0.09, 0.07, 0.30, attack=0.006)
mix(soft, sweep(300, 650, 0.05, 0.03, 0.15), at=0.08, gain=0.45)
write("water_soft", soft, peak=0.7)

splash = envelope(one_pole(noise(0.7), 1800), 0.006, 0.12)
for at, f0, f1, g in [(0.05, 900, 1700, 0.5), (0.13, 700, 1400, 0.42), (0.22, 1000, 1900, 0.3),
                      (0.31, 600, 1200, 0.26), (0.42, 820, 1500, 0.18)]:
    mix(splash, sweep(f0, f1, 0.04, 0.03, 0.15), at=at, gain=g)
write("water_splash", splash)

# ── Ink ───────────────────────────────────────────────────────────────
write("ink_pluck", pluck(392, 0.7), peak=0.7)

tap = tone(1150, 0.012, 0.08, partials=((1, 1.0), (2.01, 0.3), (3.2, 0.12)))
mix(tap, envelope(noise(0.004), 0.0005, 0.001), gain=0.25)
write("ink_tap", tap, peak=0.6)


def brush(dur, attack, decay, lo=700, hi=5000):
    sig = bandpass(noise(dur), lo, hi)
    flutter = one_pole([random.uniform(0.4, 1.0) for _ in range(len(sig))], 30)
    sig = [v * f for v, f in zip(sig, flutter)]
    return envelope(sig, attack, decay)


write("ink_brush", brush(0.26, 0.045, 0.07), peak=0.55)

stamp = sweep(140, 65, 0.06, 0.05, 0.2, attack=0.001, harmonic=0.3)
mix(stamp, envelope(one_pole(noise(0.05), 3000), 0.001, 0.015), gain=0.35)
write("ink_stamp", stamp, peak=0.75)

ink_switch = brush(0.6, 0.12, 0.16, lo=500, hi=4200)
mix(ink_switch, pluck(196, 0.9, damping=0.997), at=0.33, gain=0.9)
mix(ink_switch, pluck(294, 0.8, damping=0.996), at=0.36, gain=0.5)
write("ink_switch", ink_switch)

# ── Classic ───────────────────────────────────────────────────────────
write("classic_tick", tone(1800, 0.004, 0.03), peak=0.5)

# ── Stone (zen garden) ────────────────────────────────────────────────
def woodblock(f, decay=0.03, dur=0.12):
    return tone(f, decay, dur, partials=((1, 1.0), (2.7, 0.35), (4.1, 0.12)))


click = woodblock(900, 0.025)
mix(click, tone(320, 0.05, 0.15), gain=0.6)
mix(click, envelope(one_pole(noise(0.01), 4000), 0.0005, 0.002), gain=0.3)
write("stone_click", click, peak=0.75)
write("stone_tap", woodblock(1300, 0.02, 0.09), peak=0.6)
write("stone_rake", envelope(bandpass(noise(0.4), 120, 900), 0.06, 0.12), peak=0.5)

shishi = woodblock(600, 0.06, 0.3)
mix(shishi, woodblock(1520, 0.03, 0.15), gain=0.4)
mix(shishi, tone(180, 0.08, 0.3), gain=0.5)
for at, f0, f1, g in [(0.28, 900, 1600, 0.25), (0.36, 700, 1300, 0.2), (0.45, 1000, 1800, 0.15), (0.55, 800, 1400, 0.1)]:
    mix(shishi, sweep(f0, f1, 0.03, 0.02, 0.1), at=at, gain=g)
write("stone_switch", shishi)

# ── Paper (origami) ───────────────────────────────────────────────────
snap = envelope(bandpass(noise(0.05), 2000, 9000), 0.0005, 0.008)
mix(snap, tone(220, 0.02, 0.06), gain=0.4)
write("paper_tap", snap, peak=0.6)

fold = []
for at, g in [(0.0, 1.0), (0.045, 0.7), (0.085, 0.5), (0.12, 0.3)]:
    mix(fold, envelope(bandpass(noise(0.03), 1500, 7000), 0.001, 0.008), at=at, gain=g)
write("paper_fold", fold, peak=0.55)
write("paper_flick", envelope(bandpass(noise(0.03), 3000, 10000), 0.0005, 0.004), peak=0.5)

flip = envelope(bandpass(noise(0.5), 400, 4000), 0.18, 0.1)
mix(flip, snap, at=0.34, gain=0.8)
write("paper_switch", flip, peak=0.7)

# ── Neon (night city) ─────────────────────────────────────────────────
def square_sweep(f0, f1, sweep_time, decay, dur):
    out, phase = [], 0.0
    for i in range(int(dur * SR)):
        t = i / SR
        k = min(t / sweep_time, 1.0)
        f = f0 * (f1 / f0) ** k
        phase += 2 * math.pi * f / SR
        s = sum(math.sin(h * phase) / h for h in (1, 3, 5, 7))
        out.append(s * math.exp(-t / decay) * min(t / 0.001, 1.0))
    return one_pole(out, 6000)


write("neon_blip", square_sweep(660, 440, 0.04, 0.05, 0.14), peak=0.5)


def buzz(f, dur, gate_rate=0.0):
    out = []
    for i in range(int(dur * SR)):
        t = i / SR
        saw = sum(math.sin(2 * math.pi * f * h * t) / h for h in range(1, 12))
        gate = 1.0
        if gate_rate:
            gate = 0.35 + 0.65 * (1 if random.random() > 0.002 else 0)
        out.append(saw * gate)
    return out


write("neon_buzz", envelope(one_pole(buzz(120, 0.3, 1), 2500), 0.01, 0.12), peak=0.45)

zap = []
for k in range(14):
    mix(zap, envelope(bandpass(noise(0.02), 2000, 8000), 0.0005, 0.004), at=random.uniform(0, 0.35), gain=random.uniform(0.3, 1.0))
rise = []
phase = 0.0
for i in range(int(0.7 * SR)):
    t = i / SR
    f = 80 * (3.0 ** (t / 0.7))
    phase += 2 * math.pi * f / SR
    rise.append(sum(math.sin(h * phase) / h for h in range(1, 8)) * min(t / 0.2, 1.0) * (1 - t / 0.7) ** 0.5)
mix(zap, one_pole(rise, 3000), gain=0.6)
mix(zap, square_sweep(880, 660, 0.03, 0.08, 0.2), at=0.62, gain=0.8)
write("neon_zap", zap, peak=0.65)

# ── Cosmos (constellation) ────────────────────────────────────────────
def bell(f, dur=1.2):
    out = []
    for i in range(int(dur * SR)):
        t = i / SR
        s = (math.sin(2 * math.pi * f * t) * math.exp(-t / 0.9)
             + 0.5 * math.sin(2 * math.pi * f * 2.76 * t) * math.exp(-t / 0.5)
             + 0.25 * math.sin(2 * math.pi * f * 5.4 * t) * math.exp(-t / 0.3)
             + 0.12 * math.sin(2 * math.pi * f * 8.93 * t) * math.exp(-t / 0.2))
        out.append(s * min(t / 0.002, 1.0))
    return out


write("cosmos_chime", bell(880), peak=0.5)
sparkle = []
for k in range(6):
    mix(sparkle, tone(random.uniform(2000, 4200), 0.06, 0.2), at=k * 0.055, gain=1.0 - k * 0.12)
write("cosmos_sparkle", sparkle, peak=0.4)
swell = envelope(one_pole(noise(1.6), 900), 0.45, 0.35)
swell = [v * 0.5 for v in swell]
for k, f in enumerate([440, 554.4, 659.3, 880]):
    mix(swell, bell(f, 1.3), at=0.35 + k * 0.08, gain=0.6)
write("cosmos_switch", swell, peak=0.6)
