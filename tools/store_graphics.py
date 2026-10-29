#!/usr/bin/env python3
"""Builds Play Store graphics from emulator screenshots.

usage: python3 tools/store_graphics.py <shots dir> <out dir>

- phone screenshots: 1080x2160 (Play allows at most 2:1), a caption over a dark gradient,
  the screenshot below with rounded corners
- feature graphic: 1024x500
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

SHOTS, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
for cand in ["/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf", "/usr/share/fonts/opentype/noto/NotoSans-Bold.ttf"]:
    if os.path.exists(cand):
        FONT_BOLD = cand
for cand in ["/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf", "/usr/share/fonts/opentype/noto/NotoSans-Regular.ttf"]:
    if os.path.exists(cand):
        FONT = cand

SCREENS = [
    ("store-1-water", "Your apps, afloat", "Water: favorites bob on a pool", (14, 90, 115), (6, 30, 40)),
    ("store-2-ink", "Brushstrokes on paper", "Ink: calm, lettered, sealed in red", (60, 50, 42), (20, 17, 15)),
    ("store-3-neon", "Neon nights", "Every favorite is a glowing sign", (90, 20, 70), (12, 8, 22)),
    ("store-4-stone", "A zen garden", "Stone: pebbles on raked sand", (90, 80, 62), (32, 28, 22)),
    ("store-5-cosmos", "Your own constellation", "Cosmos: apps as drifting stars", (40, 34, 100), (6, 6, 20)),
    ("store-6-paper", "Folded with care", "Paper: origami cards that crease", (150, 70, 40), (40, 30, 26)),
    ("store-7-picker", "Seven styles. One tap.", "Layout, motion, sound and haptics change together", (14, 90, 115), (40, 30, 90)),
    ("store-8-index", "Find any app fast", "Thumb-index tabs, search, and pull up for all apps", (60, 50, 42), (12, 10, 9)),
]

W, H = 1080, 2160


def vgradient(w, h, top, bottom):
    im = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(im)
    for y in range(h):
        t = y / max(1, h - 1)
        d.line([(0, y), (w, y)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    return im


def rounded(im, r):
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, im.size[0], im.size[1]], radius=r, fill=255)
    out = Image.new("RGBA", im.size)
    out.paste(im, (0, 0), mask)
    return out


def fit_text(draw, text, font_path, max_w, start):
    size = start
    while size > 20:
        f = ImageFont.truetype(font_path, size)
        if draw.textlength(text, font=f) <= max_w:
            return f
        size -= 2
    return ImageFont.truetype(font_path, size)


for name, title, sub, c1, c2 in SCREENS:
    path = os.path.join(SHOTS, name + ".png")
    if not os.path.exists(path):
        print("missing", path)
        continue
    canvas = vgradient(W, H, c1, c2).convert("RGBA")
    d = ImageDraw.Draw(canvas)
    tf = fit_text(d, title, FONT_BOLD, W - 120, 84)
    sf = fit_text(d, sub, FONT, W - 140, 42)
    d.text((W / 2, 150), title, font=tf, fill="white", anchor="mm")
    d.text((W / 2, 235), sub, font=sf, fill=(255, 255, 255, 200), anchor="mm")
    shot = Image.open(path).convert("RGB")
    target_h = H - 330
    scale = target_h / shot.size[1]
    shot = shot.resize((int(shot.size[0] * scale), target_h), Image.LANCZOS)
    x = (W - shot.size[0]) // 2
    y = 300
    # Soft shadow, then the screen with rounded corners.
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle([x + 6, y + 14, x + shot.size[0] + 6, y + shot.size[1] + 14], radius=56, fill=(0, 0, 0, 120))
    canvas = Image.alpha_composite(canvas, shadow.filter(ImageFilter.GaussianBlur(24)))
    canvas.alpha_composite(rounded(shot, 52), (x, y))
    canvas.convert("RGB").save(os.path.join(OUT, f"phone-{name.split('-')[1]}-{name.split('-', 2)[2]}.png"))
    print("wrote", name)

# Feature graphic: icon + name + tagline on the left, three phones fanned on the right.
fg = vgradient(1024, 500, (14, 90, 115), (59, 42, 107)).convert("RGBA")
d = ImageDraw.Draw(fg)
icon_path = os.path.join(os.path.dirname(OUT.rstrip("/")), "graphics", "icon-512.png")
if not os.path.exists(icon_path):
    icon_path = os.path.join(OUT, "icon-512.png")
if os.path.exists(icon_path):
    icon = Image.open(icon_path).convert("RGB").resize((120, 120), Image.LANCZOS)
    fg.alpha_composite(rounded(icon, 28), (56, 110))
d.text((56, 262), "Cascade", font=ImageFont.truetype(FONT_BOLD, 64), fill="white")
d.text((58, 340), "A home screen with personality", font=ImageFont.truetype(FONT, 28), fill=(255, 255, 255, 220))
d.text((58, 380), "7 living styles · motion · sound · haptics", font=ImageFont.truetype(FONT, 22), fill=(255, 255, 255, 170))
for i, (name, angle, dx, dy) in enumerate([("store-2-ink", 8, 600, 70), ("store-3-neon", -6, 840, 70), ("store-1-water", 0, 715, 40)]):
    p = os.path.join(SHOTS, name + ".png")
    if not os.path.exists(p):
        continue
    s = Image.open(p).convert("RGB")
    s = s.resize((int(s.size[0] * 470 / s.size[1]), 470), Image.LANCZOS)
    r = rounded(s, 26).rotate(angle, expand=True, resample=Image.BICUBIC)
    fg.alpha_composite(r, (dx - r.size[0] // 2 + 60, dy))
fg.convert("RGB").save(os.path.join(OUT, "feature-graphic-1024x500.png"))
print("wrote feature graphic")
