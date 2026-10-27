#!/usr/bin/env python3
"""Builds every app and repository image from the logo.

    pip install pillow numpy scipy
    python3 tools/make_images.py [path/to/new-logo.png]

With no argument it uses docs/assets/lena-logo.png. Makes the launcher icon (foreground and
themed silhouette), the notification icon, the in-app avatar, and docs/assets/lena-avatar.png
and social-preview.png, and the Home Assistant integration's brand images. The logo is expected to be square on a plain dark background; if yours
changes, update BG (its background colour) and the artwork centre and width below.
"""
import sys, os
import numpy as np
from PIL import Image, ImageFilter, ImageDraw, ImageFont
from scipy import ndimage

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = sys.argv[1] if len(sys.argv) > 1 else f"{REPO}/docs/assets/lena-logo.png"
RES = f"{REPO}/android/app/src/main/res"
src = Image.open(SRC).convert("RGB")
W = src.size[0]
BG = (4, 5, 19)
cx, cy = (58 + 647) / 2, (16 + 654) / 2   # centre of the artwork
SPAN = 640                                 # artwork width in source pixels

# ---- clean silhouette (main shapes only) -----------------------------------
a = np.asarray(src).astype(float)
d = np.abs(a - np.array(BG)).max(2)
m = Image.fromarray((np.clip((d - 10) / 45, 0, 1) * 255).astype("uint8"))
m = m.filter(ImageFilter.MinFilter(9)).filter(ImageFilter.MaxFilter(9))
binary = np.asarray(m) > 128
lab, n = ndimage.label(binary)
sizes = ndimage.sum(binary, lab, range(1, n + 1))
keep = np.isin(lab, [i + 1 for i, s in enumerate(sizes) if s > binary.size * 0.002])
sil = Image.fromarray((keep * 255).astype("uint8")).filter(ImageFilter.GaussianBlur(1.2))

def place(img, px, content_px, fill):
    """img centred on the artwork, so the artwork spans content_px of a px canvas."""
    s = content_px / SPAN
    big = img.resize((round(W * s), round(W * s)), Image.LANCZOS)
    c = Image.new(img.mode, (px, px), fill)
    c.paste(big, (round(px / 2 - cx * s), round(px / 2 - cy * s)))
    return c

DENS = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
for name, k in DENS.items():
    px = round(108 * k)
    os.makedirs(f"{RES}/mipmap-{name}", exist_ok=True)
    place(src, px, 68 * k, BG).save(f"{RES}/mipmap-{name}/ic_launcher_foreground.png", optimize=True)
    mono = Image.new("LA", (px, px), (255, 0))
    mono.putalpha(place(sil, px, 60 * k, 0))
    mono.save(f"{RES}/mipmap-{name}/ic_launcher_monochrome.png", optimize=True)
    # notification icon: white silhouette, 24dp with the artwork about 21dp wide
    npx = round(24 * k)
    os.makedirs(f"{RES}/drawable-{name}", exist_ok=True)
    stat = Image.new("LA", (npx, npx), (255, 0))
    stat.putalpha(place(sil, npx, 21.5 * k, 0))
    stat.save(f"{RES}/drawable-{name}/ic_stat_logo.png", optimize=True)

# in-app avatar: what the launcher icon shows (the middle 72dp at the same scale)
os.makedirs(f"{RES}/drawable-nodpi", exist_ok=True)
full = place(src, 432, 68 * 4, BG)
full.crop((72, 72, 360, 360)).resize((256, 256), Image.LANCZOS).save(f"{RES}/drawable-nodpi/lena_avatar.png", optimize=True)

# ---- repository images -------------------------------------------------------
assets = f"{REPO}/docs/assets"
os.makedirs(assets, exist_ok=True)
if os.path.abspath(SRC) != os.path.abspath(f"{assets}/lena-logo.png"):
    src.save(f"{assets}/lena-logo.png", optimize=True)
place(src, 512, 430, BG).save(f"{assets}/lena-avatar.png", optimize=True)

# social preview, 1280x640: logo left, name and line right
sp = Image.new("RGB", (1280, 640), BG)
logo = place(src, 560, 520, BG)
sp.paste(logo, (70, 40))
dr = ImageDraw.Draw(sp)
fonts = "/usr/share/fonts/opentype/inter"
title = ImageFont.truetype(f"{fonts}/InterDisplay-Bold.otf", 150)
line = ImageFont.truetype(f"{fonts}/Inter-Regular.otf", 38)
dr.text((680, 200), "Lena", font=title, fill=(255, 255, 255))
for i, t in enumerate(["A personal AI assistant", "on your own hardware"]):
    dr.text((686, 390 + i * 52), t, font=line, fill=(170, 176, 210))
sp.save(f"{assets}/social-preview.png", optimize=True)

# ---- Home Assistant brand images ----------------------------------------------
# Home Assistant 2026.3+ shows a custom integration's own brand/ images. The rules (from the
# home-assistant/brands repository): PNG, trimmed, transparency preferred; icon square at 256
# and 512 (@2x); logo landscape with its short side 128-256 (256-512 for @2x); dark_ files are
# used on dark themes. The icon is the logo on its dark tile with rounded corners, the same as
# the app icon, so its white shape stays visible on light themes too. The logo adds her name:
# dark text for light themes, white for dark ones.
brand = f"{REPO}/home-assistant/custom_components/lena/brand"
os.makedirs(brand, exist_ok=True)

def tile(px):
    """The artwork on a rounded dark square, px wide, with transparent corners."""
    t = place(src, px * 4, px * 4 * 0.86, BG).convert("RGBA")   # drawn 4x, then shrunk: smooth corners
    mask = Image.new("L", t.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, t.size[0] - 1, t.size[1] - 1), radius=round(px * 4 * 0.22), fill=255)
    t.putalpha(mask)
    return t.resize((px, px), Image.LANCZOS)

def wordmark(h, colour):
    """Tile plus "Lena", h pixels high, trimmed to the content."""
    name = ImageFont.truetype(f"{fonts}/InterDisplay-Bold.otf", round(h * 0.62))
    gap = round(h * 0.22)
    left, top, right, bottom = name.getbbox("Lena")
    canvas = Image.new("RGBA", (h + gap + right - left, h), (0, 0, 0, 0))
    canvas.alpha_composite(tile(h))
    ImageDraw.Draw(canvas).text((h + gap - left, (h - (bottom - top)) / 2 - top), "Lena", font=name, fill=colour)
    return canvas

for suffix, k in (("", 1), ("@2x", 2)):
    tile(256 * k).save(f"{brand}/icon{suffix}.png", optimize=True)
    wordmark(128 * k, (24, 26, 40, 255)).save(f"{brand}/logo{suffix}.png", optimize=True)
    wordmark(128 * k, (255, 255, 255, 255)).save(f"{brand}/dark_logo{suffix}.png", optimize=True)
print("ok")
