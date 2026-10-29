"""Ad 3 · "Safe to try" · 13 s, 1080x1920.

The main worry about any launcher: losing your setup. A generic phone home screen steps back,
Cascade slides in, shows a few styles, then one tap on Original brings the old home screen back.
Usage: python3 ad3_safe.py <footage dir> <out dir> [--frames]
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(__file__))
from kit import *  # noqa
from common import end_card
import audio

FOOT, OUT = sys.argv[1], sys.argv[2]
FRAMES_ONLY = "--frames" in sys.argv
os.makedirs(OUT, exist_ok=True)

T_SWAP, T_STYLES, T_BACK, T_END, TOTAL = 2.4, 4.6, 8.0, 10.6, 13.2


def clip(name, start, dur, still):
    p = os.path.join(FOOT, name + ".mp4")
    if os.path.exists(p):
        try:
            c = Clip(p, start, dur)
            if c.n > 5:
                return c
        except Exception as e:
            print("clip failed", name, e)
    return Image.open(os.path.join(FOOT, f"still-{still}.png")).convert("RGB")


WATER = clip("idle-water", 0.3, 3.0, "water")
NEON = clip("idle-neon", 0.3, 1.4, "neon")
COSMOS = clip("idle-cosmos", 0.3, 1.4, "cosmos")


def frame_of(c, lt):
    return c.at(lt * FPS) if isinstance(c, Clip) else c


def generic_home():
    """A stand-in for 'your current home screen': a plain icon grid, no real brand."""
    img = Image.new("RGB", (SCREEN_W, SCREEN_H))
    a = gradient((236, 170, 120), (90, 70, 150), SCREEN_W, SCREEN_H)
    img = Image.fromarray(a)
    d = ImageDraw.Draw(img, "RGBA")
    d.text((40, 30), "9:41", fill=(255, 255, 255), font=font("text", 26))
    # A widget.
    d.rounded_rectangle([40, 120, SCREEN_W - 40, 330], radius=36, fill=(255, 255, 255, 70))
    d.text((70, 150), "Thursday", fill=(255, 255, 255), font=font("text", 30))
    d.text((70, 190), "21°", fill=(255, 255, 255), font=font("display", 84))
    colors = [(74, 144, 226), (80, 200, 120), (245, 166, 35), (208, 2, 27), (144, 19, 254), (80, 227, 194),
              (248, 231, 28), (189, 16, 224), (126, 211, 33), (65, 117, 5), (74, 74, 74), (255, 107, 129)]
    s, g = 108, 46
    x0 = (SCREEN_W - (4 * s + 3 * g)) / 2
    for r in range(4):
        for c in range(4):
            if r == 3 and c > 1:
                # A folder.
                x, y = x0 + c * (s + g), 420 + r * (s + 70)
                d.rounded_rectangle([x, y, x + s, y + s], radius=30, fill=(255, 255, 255, 90))
                for k in range(4):
                    xx = x + 14 + (k % 2) * 44
                    yy = y + 14 + (k // 2) * 44
                    d.rounded_rectangle([xx, yy, xx + 36, yy + 36], radius=10, fill=colors[(k * 3 + c) % 12] + (255,))
                continue
            col = colors[(r * 4 + c) % 12]
            x, y = x0 + c * (s + g), 420 + r * (s + 70)
            d.rounded_rectangle([x, y, x + s, y + s], radius=30, fill=col + (255,))
            d.rounded_rectangle([x + 22, y + s + 18, x + s - 22, y + s + 30], radius=6, fill=(255, 255, 255, 150))
    # Dock.
    d.rounded_rectangle([30, SCREEN_H - 230, SCREEN_W - 30, SCREEN_H - 80], radius=48, fill=(255, 255, 255, 60))
    for c in range(4):
        x = x0 + c * (s + g)
        d.rounded_rectangle([x, SCREEN_H - 210, x + s, SCREEN_H - 210 + s], radius=30, fill=colors[(c * 5 + 2) % 12] + (255,))
    return img


HOME = generic_home()
BG = gradient((24, 22, 40), (6, 6, 12))


def check(canvas, cx, cy, p, color=(120, 230, 160)):
    if p <= 0:
        return
    d = ImageDraw.Draw(canvas)
    r = 46 * ease_back(p)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color + (255,))
    q = clamp(p * 1.5)
    pts = [(cx - 20, cy + 2), (cx - 6, cy + 16), (cx + 22, cy - 14)]
    if q > 0.05:
        d.line(pts[:2] if q < 0.5 else pts, fill=(10, 40, 20), width=9, joint="curve")


def frame(t):
    canvas = Image.fromarray(BG).convert("RGBA")
    if t < T_SWAP:
        p = spring(t / 1.0, 0.6, 1.2)
        place_center(canvas, phone(HOME, 1.0), W / 2, 1180 + (1 - p) * 900)
        word_reveal(canvas, ["Try", "a", "new", "home", "screen…"], font("display", 82), W / 2, 200, t, 0.2, stagger=0.07)
        put_text(canvas, text_layer("without losing yours.", font("display", 82), (150, 230, 255)), W / 2, 310,
                 alpha=ease_out(seg(t, 0.9, 1.3)), dy=30 * (1 - ease_out(seg(t, 0.9, 1.3))))
        return canvas

    if t < T_STYLES:
        lt = t - T_SWAP
        p = ease_in_out(seg(lt, 0.1, 0.9))
        # Your home screen steps back to the left; Cascade slides in on top.
        old = phone(HOME, 1.0 - 0.38 * p, angle=8 * p)
        place_center(canvas, old, W / 2 - 340 * p, 1180 - 60 * p)
        new = phone(frame_of(WATER, lt), 0.78 + 0.12 * p)
        place_center(canvas, new, W / 2 + 110 * p + (1 - p) * 900, 1200)
        q = ease_out(seg(lt, 0.6, 1.0))
        put_text(canvas, text_layer("Your icons, folders and widgets", font("display", 64)), W / 2, 200, alpha=q)
        put_text(canvas, text_layer("stay exactly where they are.", font("display", 64), (150, 230, 255)), W / 2, 290, alpha=ease_out(seg(lt, 0.8, 1.2)))
        check(canvas, W / 2 - 340 - 70, 700, seg(lt, 1.0, 1.5))
        return canvas

    if t < T_BACK:
        lt = t - T_STYLES
        old = phone(HOME, 0.62, angle=8)
        place_center(canvas, old, W / 2 - 340, 1120)
        check(canvas, W / 2 - 340 - 70, 700, 1.0)
        k = int(lt / 1.13)
        src = [WATER, NEON, COSMOS][min(2, k)]
        scr = frame_of(src, (lt - k * 1.13) + (2.2 if k == 0 else 0))
        place_center(canvas, phone(scr, 0.9), W / 2 + 110, 1200)
        q = ease_out(seg(lt, 0.1, 0.5))
        put_text(canvas, text_layer("Play with seven styles.", font("display", 76)), W / 2, 230, alpha=q, dy=30 * (1 - q))
        return canvas

    if t < T_END:
        lt = t - T_BACK
        # The "Original" chip, a tap, and Cascade steps aside.
        p = ease_in_out(seg(lt, 0.9, 1.7))
        new = phone(frame_of(COSMOS, 1.3), 0.9 - 0.2 * p)
        old = phone(HOME, 0.62 + 0.38 * p, angle=8 * (1 - p))
        if p > 0.5:
            place_center(canvas, new, W / 2 + 110 + 900 * p, 1200)
            place_center(canvas, old, W / 2 - 340 * (1 - p), 1120 + 60 * p)
        else:
            place_center(canvas, old, W / 2 - 340 * (1 - p), 1120 + 60 * p)
            place_center(canvas, new, W / 2 + 110 + 900 * p, 1200)
        chip_p = ease_back(seg(lt, 0.0, 0.4))
        chip = Image.new("RGBA", (420, 120), (0, 0, 0, 0))
        dd = ImageDraw.Draw(chip)
        dd.rounded_rectangle([0, 0, 419, 119], radius=60, fill=(255, 255, 255, 255))
        dd.arc([40, 34, 92, 86], 200, 520, fill=(30, 40, 60), width=8)
        dd.polygon([(38, 46), (56, 30), (60, 56)], fill=(30, 40, 60))
        tl = text_layer("Original", font("display", 50), (30, 40, 60))
        chip.alpha_composite(tl, (230 - tl.width // 2 + 20, 60 - tl.height // 2))
        tap = seg(lt, 0.55, 0.95)
        press = 1 - 0.08 * math.sin(clamp(tap * 2) * math.pi)
        put_text(canvas, chip, W / 2, 380, alpha=clamp(chip_p * 1.5) * (1 - seg(lt, 1.6, 2.0)), scale=(0.7 + 0.3 * chip_p) * press)
        if 0 < tap < 1:
            d = ImageDraw.Draw(canvas)
            r = 30 + 90 * ease_out(tap)
            d.ellipse([W / 2 + 60 - r, 380 - r, W / 2 + 60 + r, 380 + r], outline=(255, 255, 255, int(220 * (1 - tap))), width=6)
        q = ease_out(seg(lt, 1.2, 1.6))
        put_text(canvas, text_layer("One tap brings it back.", font("display", 76), (150, 230, 255)), W / 2, 230, alpha=q, dy=30 * (1 - q))
        out = ease_in_out(seg(lt, T_END - T_BACK - 0.3, T_END - T_BACK))
        if out:
            canvas.alpha_composite(Image.new("RGBA", (W, H), (14, 90, 115, int(255 * out))))
        return canvas

    return end_card(t - T_END, "Safe to try. Easy to love.")


def render():
    if FRAMES_ONLY:
        for name, t in [("hook", 1.8), ("swap", T_SWAP + 1.6), ("styles", T_STYLES + 1.6), ("back", T_BACK + 0.7), ("back2", T_BACK + 2.0), ("end", T_END + 1.6)]:
            frame(t).convert("RGB").save(os.path.join(OUT, f"ad3-{name}.jpg"), quality=90)
        return
    bounds = [0, T_SWAP, T_STYLES, T_BACK, T_END, TOTAL]
    parts = []
    for k in range(len(bounds) - 1):
        path = os.path.join(OUT, f"ad3-part{k}.mp4")
        sc = Scene(path)
        for fi in range(int(round(bounds[k] * FPS)), int(round(bounds[k + 1] * FPS))):
            sc.add(frame(fi / FPS))
        sc.close()
        parts.append(path)
        print("scene", k, flush=True)
    concat(parts, os.path.join(OUT, "ad3-video.mp4"))

    tr = audio.Track(TOTAL)
    tr.sfx("paper_flick", 0.3, 0.5)
    tr.sfx("water_splash", T_SWAP + 0.2, 0.55)
    tr.sfx("cosmos_sparkle", T_SWAP + 1.0, 0.45)
    tr.sfx("neon_blip", T_STYLES + 1.13, 0.5)
    tr.sfx("cosmos_chime", T_STYLES + 2.26, 0.4)
    tr.sfx("classic_tick", T_BACK + 0.6, 0.7)
    tr.sfx("paper_switch", T_BACK + 0.9, 0.55)
    tr.sfx("water_drop", T_END + 0.05, 0.6)
    tr.sfx("cosmos_chime", T_END + 0.9, 0.4)
    bed = audio.music_bed(TOTAL, 96, [["C4", "E4", "G4", "B4"], ["A3", "C4", "E4", "G4"], ["F3", "A3", "C4", "E4"], ["G3", "B3", "D4", "E4"]],
                          start_drums=T_SWAP, end_drums=T_END - 0.3, riser_at=T_END - 1.0, impact_at=T_END, intensity=0.8)
    wav = os.path.join(OUT, "ad3-audio.wav")
    tr.save(wav, bed, music_gain=0.75)
    mux(os.path.join(OUT, "ad3-video.mp4"), wav, os.path.join(OUT, "Cascade-ad-safe-to-try.mp4"))
    print("done")


if __name__ == "__main__":
    render()
