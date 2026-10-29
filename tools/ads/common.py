"""Pieces shared by the ads."""
import os

from PIL import Image, ImageDraw, ImageFilter

from kit import *  # noqa

BRAND_TOP, BRAND_BOT = (14, 90, 115), (59, 42, 107)
ICON = Image.open(os.path.join(os.path.dirname(__file__), "..", "..", "store", "graphics", "icon-512.png")).convert("RGB")
_BG = {}


def brand_bg():
    if "b" not in _BG:
        _BG["b"] = gradient(BRAND_TOP, BRAND_BOT)
    return _BG["b"]


def end_card(lt, line="A home screen with personality"):
    """Icon pops, wordmark, line, and the store pill. lt = seconds since the card started."""
    canvas = Image.fromarray(brand_bg()).convert("RGBA")
    p = ease_back(seg(lt, 0.0, 0.6), 1.4)
    size = int(300 * max(0.01, p))
    ic = Image.new("RGBA", (300, 300), (0, 0, 0, 0))
    m = Image.new("L", (300, 300), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, 299, 299], radius=68, fill=255)
    ic.paste(ICON.resize((300, 300), Image.LANCZOS), (0, 0), m)
    ic = ic.resize((max(1, size), max(1, size)), Image.LANCZOS)
    sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle([W / 2 - size / 2, 700 - size / 2 + 24, W / 2 + size / 2, 700 + size / 2 + 24], radius=int(68 * size / 300), fill=(0, 0, 0, 90))
    canvas.alpha_composite(sh.filter(ImageFilter.GaussianBlur(30)))
    canvas.alpha_composite(ic, (int(W / 2 - size / 2), int(700 - size / 2)))
    q = ease_out(seg(lt, 0.35, 0.8))
    put_text(canvas, text_layer("Cascade", font("display-black", 140)), W / 2, 1000, alpha=q, dy=40 * (1 - q))
    q = ease_out(seg(lt, 0.55, 1.0))
    put_text(canvas, text_layer(line, font("text", 46), (215, 232, 240)), W / 2, 1110, alpha=q, dy=30 * (1 - q))
    q = ease_back(seg(lt, 0.9, 1.4))
    pill = Image.new("RGBA", (560, 120), (0, 0, 0, 0))
    ImageDraw.Draw(pill).rounded_rectangle([0, 0, 559, 119], radius=60, fill=(255, 255, 255, 255))
    tl = text_layer("Free on Google Play", font("display", 46), (20, 40, 60))
    pill.alpha_composite(tl, (int(280 - tl.width / 2), int(60 - tl.height / 2)))
    put_text(canvas, pill, W / 2, 1380, alpha=clamp(q * 1.3), scale=0.8 + 0.2 * q)
    return canvas
