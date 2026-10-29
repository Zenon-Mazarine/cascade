"""Small motion-design kit for Cascade's ads: phone mockup, kinetic type, easing, clip reading,
and an encoder that writes one scene per file. Frames are drawn with PIL + numpy at 1080x1920, 30 fps."""
import math
import os
import subprocess

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H, FPS = 1080, 1920, 30

FONTS = "/usr/share/fonts/opentype/inter/"


def font(name, size):
    paths = {
        "display": FONTS + "InterDisplay-Bold.otf",
        "display-black": FONTS + "InterDisplay-Black.otf",
        "display-light": FONTS + "InterDisplay-Light.otf",
        "display-thin": FONTS + "InterDisplay-Thin.otf",
        "text": FONTS + "Inter-Medium.otf",
        "text-regular": FONTS + "Inter-Regular.otf",
        "serif": "/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf",
        "serif-italic": "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Italic.ttf",
        "mono": "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
    }
    return ImageFont.truetype(paths[name], size)


# ── easing ────────────────────────────────────────────────────────────
def clamp(x, a=0.0, b=1.0):
    return max(a, min(b, x))


def ease_out(t):
    t = clamp(t)
    return 1 - (1 - t) ** 3


def ease_in_out(t):
    t = clamp(t)
    return 4 * t ** 3 if t < 0.5 else 1 - (-2 * t + 2) ** 3 / 2


def ease_back(t, s=1.7):
    t = clamp(t)
    return 1 + (s + 1) * (t - 1) ** 3 + s * (t - 1) ** 2


def spring(t, damping=0.5, freq=2.2):
    t = max(0.0, t)
    return 1 - math.exp(-t * 6 * damping) * math.cos(t * freq * 2 * math.pi)


def seg(t, a, b):
    """0 → 1 as t goes from a to b."""
    return clamp((t - a) / (b - a)) if b > a else float(t >= a)


# ── backgrounds ───────────────────────────────────────────────────────
def gradient(top, bottom, w=W, h=H):
    t = np.linspace(0, 1, h)[:, None, None]
    a = np.array(top, dtype=np.float32)[None, None, :]
    b = np.array(bottom, dtype=np.float32)[None, None, :]
    g = a + (b - a) * t
    return np.repeat(g, w, axis=1).astype(np.uint8)


def radial_glow(img, cx, cy, r, color, strength):
    """Adds a soft colored glow (numpy, in place-ish)."""
    h, w = img.shape[:2]
    y, x = np.ogrid[:h, :w]
    d = np.sqrt((x - cx) ** 2 + (y - cy) ** 2) / r
    m = np.clip(1 - d, 0, 1) ** 2 * strength
    out = img.astype(np.float32) + m[..., None] * np.array(color, np.float32)[None, None, :]
    return np.clip(out, 0, 255).astype(np.uint8)


# ── phone mockup ──────────────────────────────────────────────────────
SCREEN_W, SCREEN_H = 660, 1467  # 9:20, like the recordings
BEZEL = 18
PHONE_W, PHONE_H = SCREEN_W + 2 * BEZEL, SCREEN_H + 2 * BEZEL
R_SCREEN, R_PHONE = 64, 64 + BEZEL


def _rounded_mask(w, h, r, scale=3):
    m = Image.new("L", (w * scale, h * scale), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, w * scale - 1, h * scale - 1], radius=r * scale, fill=255)
    return m.resize((w, h), Image.LANCZOS)


SCREEN_MASK = _rounded_mask(SCREEN_W, SCREEN_H, R_SCREEN)


def phone_body():
    body = Image.new("RGBA", (PHONE_W, PHONE_H), (0, 0, 0, 0))
    mask = _rounded_mask(PHONE_W, PHONE_H, R_PHONE)
    fill = Image.new("RGBA", (PHONE_W, PHONE_H), (14, 14, 18, 255))
    body.paste(fill, (0, 0), mask)
    d = ImageDraw.Draw(body)
    # A thin metallic rim.
    d.rounded_rectangle([1, 1, PHONE_W - 2, PHONE_H - 2], radius=R_PHONE, outline=(70, 72, 80, 255), width=3)
    return body


PHONE_BODY = phone_body()


def phone_shadow():
    pad = 120
    s = Image.new("RGBA", (PHONE_W + 2 * pad, PHONE_H + 2 * pad), (0, 0, 0, 0))
    ImageDraw.Draw(s).rounded_rectangle([pad, pad + 30, pad + PHONE_W, pad + PHONE_H + 30], radius=R_PHONE, fill=(0, 0, 0, 150))
    return s.filter(ImageFilter.GaussianBlur(45)), pad


PHONE_SHADOW, SHADOW_PAD = phone_shadow()


def phone(screen_img, scale=1.0, angle=0.0):
    """Returns an RGBA phone with the given screen (PIL RGB, any size) inside, plus its shadow."""
    scr = screen_img.convert("RGB").resize((SCREEN_W, SCREEN_H), Image.BILINEAR)
    ph = PHONE_BODY.copy()
    ph.paste(scr, (BEZEL, BEZEL), SCREEN_MASK)
    # Camera hole.
    ImageDraw.Draw(ph).ellipse([PHONE_W / 2 - 11, BEZEL + 22, PHONE_W / 2 + 11, BEZEL + 44], fill=(5, 5, 7, 255))
    full = PHONE_SHADOW.copy()
    full.alpha_composite(ph, (SHADOW_PAD, SHADOW_PAD))
    if scale != 1.0:
        full = full.resize((int(full.width * scale), int(full.height * scale)), Image.BILINEAR)
    if angle:
        full = full.rotate(angle, expand=True, resample=Image.BICUBIC)
    return full


def place_center(canvas, rgba, cx, cy):
    canvas.alpha_composite(rgba, (int(cx - rgba.width / 2), int(cy - rgba.height / 2)))


# ── text ──────────────────────────────────────────────────────────────
def text_layer(txt, fnt, fill=(255, 255, 255), glow=None, glow_radius=18, tracking=0):
    """Renders text to a tight RGBA image (optionally with a colored glow)."""
    bbox = fnt.getbbox(txt)
    tw = bbox[2] - bbox[0] + tracking * max(0, len(txt) - 1)
    th = bbox[3] - bbox[1]
    pad = glow_radius * 3 if glow else 8
    img = Image.new("RGBA", (int(tw + 2 * pad), int(th + 2 * pad)), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if tracking:
        x = pad - bbox[0]
        for ch in txt:
            d.text((x, pad - bbox[1]), ch, font=fnt, fill=fill)
            x += fnt.getlength(ch) + tracking
    else:
        d.text((pad - bbox[0], pad - bbox[1]), txt, font=fnt, fill=fill)
    if glow:
        g = Image.new("RGBA", img.size, (0, 0, 0, 0))
        alpha = img.split()[3]
        g.paste(Image.new("RGBA", img.size, glow + (255,)), (0, 0), alpha)
        g = g.filter(ImageFilter.GaussianBlur(glow_radius))
        g2 = g.filter(ImageFilter.GaussianBlur(glow_radius / 3))
        out = Image.new("RGBA", img.size, (0, 0, 0, 0))
        out.alpha_composite(g)
        out.alpha_composite(g)
        out.alpha_composite(g2)
        out.alpha_composite(img)
        return out
    return img


def put_text(canvas, layer, cx, cy, alpha=1.0, dy=0.0, scale=1.0, blur=0.0):
    if alpha <= 0.003:
        return
    l = layer
    if scale != 1.0:
        l = l.resize((max(1, int(l.width * scale)), max(1, int(l.height * scale))), Image.BICUBIC)
    if blur > 0.3:
        l = l.filter(ImageFilter.GaussianBlur(blur))
    if alpha < 1.0:
        a = l.split()[3].point(lambda v: int(v * alpha))
        l = l.copy()
        l.putalpha(a)
    canvas.alpha_composite(l, (int(cx - l.width / 2), int(cy + dy - l.height / 2)))


def word_reveal(canvas, words, fnt, cx, cy, t, start, stagger=0.07, dur=0.45, fill=(255, 255, 255), gap=None, glow=None):
    """Words rise in one by one (classic kinetic type)."""
    layers = [text_layer(w, fnt, fill, glow=glow) for w in words]
    space = gap if gap is not None else fnt.getlength(" ")
    pad = layers[0].width - (fnt.getbbox(words[0])[2] - fnt.getbbox(words[0])[0])
    widths = [l.width - pad for l in layers]
    total = sum(widths) + space * (len(words) - 1)
    x = cx - total / 2
    for i, (l, w) in enumerate(zip(layers, widths)):
        p = ease_out(seg(t, start + i * stagger, start + i * stagger + dur))
        put_text(canvas, l, x + w / 2, cy, alpha=p, dy=(1 - p) * 40)
        x += w + space


# ── clips ─────────────────────────────────────────────────────────────
class Clip:
    """Frames of a recording, resampled to 30 fps and scaled to the phone screen size."""

    def __init__(self, path, start=0.0, dur=None, size=(SCREEN_W, SCREEN_H)):
        args = ["ffmpeg", "-v", "error", "-ss", f"{start}", "-i", path]
        if dur:
            args += ["-t", f"{dur}"]
        args += ["-vf", f"fps={FPS},scale={size[0]}:{size[1]}:flags=bicubic", "-f", "rawvideo", "-pix_fmt", "rgb24", "-"]
        raw = subprocess.run(args, capture_output=True, check=True).stdout
        n = len(raw) // (size[0] * size[1] * 3)
        self.frames = np.frombuffer(raw, np.uint8)[: n * size[0] * size[1] * 3].reshape(n, size[1], size[0], 3)
        self.n = n

    def at(self, i):
        i = int(clamp(i, 0, self.n - 1))
        return Image.fromarray(self.frames[i])


# ── encoder ───────────────────────────────────────────────────────────
class Scene:
    """Write frames for one scene into its own mp4 (renamed into place only on success)."""

    def __init__(self, path):
        self.path = path
        self.tmp = path[:-4] + ".part.mp4"
        self.p = subprocess.Popen(
            ["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
             "-c:v", "libx264", "-preset", "veryfast", "-crf", "18", "-pix_fmt", "yuv420p", "-r", str(FPS), self.tmp],
            stdin=subprocess.PIPE,
        )

    def add(self, img):
        if isinstance(img, Image.Image):
            img = np.asarray(img.convert("RGB"))
        self.p.stdin.write(np.ascontiguousarray(img, dtype=np.uint8).tobytes())

    def close(self):
        self.p.stdin.close()
        if self.p.wait() == 0:
            os.replace(self.tmp, self.path)
        else:
            raise RuntimeError("ffmpeg failed for " + self.path)


def concat(parts, out):
    lst = out + ".txt"
    with open(lst, "w") as f:
        for p in parts:
            f.write(f"file '{os.path.abspath(p)}'\n")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", out], check=True)
    os.remove(lst)


def mux(video, audio, out):
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", video, "-i", audio, "-map", "0:v", "-map", "1:a", "-c:v", "copy",
                    "-c:a", "aac", "-b:a", "192k", "-shortest", out], check=True)
