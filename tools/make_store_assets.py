#!/usr/bin/env python3
"""
Genere les visuels exiges par Google Play a partir du trace de cavalier utilise
pour les pieces de l'application, afin que l'icone du store et le plateau soient
dessines par la meme main.

    python tools/make_store_assets.py

Produit dans store/ :
  icon-512.png       icone de la fiche Play (512 x 512, opaque)
  feature-1024x500.png image de presentation
  icon-1024.png      version haute definition, utile si un autre format est demande

Le rendu se fait en quadruple resolution puis est reduit : c'est ce qui donne des
bords nets sans dependre d'un moteur antialiasing externe.
"""

from __future__ import annotations

import os
from PIL import Image, ImageDraw, ImageFont

# --- Couleurs, reprises du theme de l'application ---------------------------
INK_TOP = (23, 30, 41)       # #171E29
INK_BOTTOM = (13, 17, 23)    # #0D1117
PIECE = (247, 244, 236)      # blanc creme des pieces
PIECE_EDGE = (58, 58, 56)
GOLD = (233, 185, 107)       # #E9B96B
TEXT = (232, 235, 240)
TEXT_SOFT = (154, 162, 177)

SS = 4  # facteur de suréchantillonnage


def cubic(p0, c1, c2, p1, steps=28):
    """Aplatit une courbe de Bezier cubique en segments."""
    out = []
    for i in range(1, steps + 1):
        t = i / steps
        u = 1 - t
        x = u * u * u * p0[0] + 3 * u * u * t * c1[0] + 3 * u * t * t * c2[0] + t * t * t * p1[0]
        y = u * u * u * p0[1] + 3 * u * u * t * c1[1] + 3 * u * t * t * c2[1] + t * t * t * p1[1]
        out.append((x, y))
    return out


def knight_outline():
    """Profil du cavalier, en coordonnees normalisees 0..1 (memes valeurs que ChessBoard.kt)."""
    pts = [(0.735, 0.735)]
    pts += cubic((0.735, 0.735), (0.762, 0.585), (0.738, 0.430), (0.652, 0.330))
    pts += cubic((0.652, 0.330), (0.620, 0.292), (0.598, 0.252), (0.600, 0.205))
    pts += [(0.652, 0.108), (0.548, 0.180), (0.487, 0.098), (0.452, 0.205)]
    pts += cubic((0.452, 0.205), (0.398, 0.262), (0.318, 0.330), (0.258, 0.408))
    pts += cubic((0.258, 0.408), (0.216, 0.462), (0.196, 0.502), (0.212, 0.522))
    pts += cubic((0.212, 0.522), (0.232, 0.545), (0.276, 0.536), (0.302, 0.518))
    pts += [(0.372, 0.492)]
    pts += cubic((0.372, 0.492), (0.418, 0.518), (0.436, 0.560), (0.444, 0.612))
    pts += cubic((0.444, 0.612), (0.454, 0.668), (0.436, 0.708), (0.414, 0.735))
    return pts


def vertical_gradient(size, top, bottom):
    w, h = size
    img = Image.new("RGB", (1, h))
    for y in range(h):
        t = y / max(1, h - 1)
        img.putpixel((0, y), tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    return img.resize((w, h), Image.NEAREST)


def draw_knight(draw, ox, oy, scale, base_fill=None):
    """
    Dessine le cavalier, socle compris, dans un carre de cote `scale` place en (ox, oy).

    `base_fill` colore le socle : c'est la qu'on place la couleur de marque, plutot
    que sur un ornement libre qui risquerait d'etre lu comme autre chose.
    """
    def P(pt):
        return (ox + pt[0] * scale, oy + pt[1] * scale)

    draw.polygon([P(p) for p in knight_outline()], fill=PIECE, outline=PIECE_EDGE, width=max(1, round(scale * 0.012)))

    # oeil
    r = 0.026 * scale
    cx, cy = P((0.432, 0.318))
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=PIECE_EDGE)

    # socle etage
    fill = base_fill or PIECE
    for x0, y0, x1, y1, rad in ((0.300, 0.730, 0.700, 0.790, 0.016), (0.205, 0.790, 0.795, 0.885, 0.030)):
        draw.rounded_rectangle([P((x0, y0)), P((x1, y1))], radius=rad * scale,
                               fill=fill, outline=PIECE_EDGE, width=max(1, round(scale * 0.012)))


def load_font(size, bold=False):
    candidates = ["segoeuib.ttf", "seguisb.ttf", "arialbd.ttf"] if bold else ["segoeui.ttf", "arial.ttf"]
    for name in candidates:
        path = os.path.join(os.environ.get("WINDIR", "C:/Windows"), "Fonts", name)
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def make_icon(side=512):
    big = side * SS
    img = vertical_gradient((big, big), INK_TOP, INK_BOTTOM)
    draw = ImageDraw.Draw(img)

    # Un motif centre et massif : c'est ce qui reste lisible a 48 px dans une liste.
    # Pas d'ornement libre : un arc au bas de l'icone se lisait comme une bouche et
    # transformait l'ensemble en visage souriant.
    scale = big * 0.74
    draw_knight(draw, (big - scale) / 2, (big - scale) / 2, scale, base_fill=GOLD)

    return img.resize((side, side), Image.LANCZOS)


def make_feature(width=1024, height=500):
    bw, bh = width * SS, height * SS
    img = vertical_gradient((bw, bh), INK_TOP, INK_BOTTOM)
    draw = ImageDraw.Draw(img)

    # Play peut recadrer cette image : on garde une marge confortable a droite
    # plutot que de remplir la largeur jusqu'au bord.
    scale = bh * 0.70
    draw_knight(draw, bw * 0.075, (bh - scale) / 2, scale, base_fill=GOLD)

    x = bw * 0.075 + scale + bw * 0.05
    title = load_font(round(bh * 0.155), bold=True)
    sub = load_font(round(bh * 0.072))

    # Texte destine au public : accents obligatoires, contrairement au code source.
    draw.text((x, bh * 0.31), "ChessForge", font=title, fill=TEXT)
    draw.text((x, bh * 0.50), "Vos parties analysées,", font=sub, fill=TEXT_SOFT)
    draw.text((x, bh * 0.60), "vos erreurs transformées", font=sub, fill=TEXT_SOFT)
    draw.text((x, bh * 0.70), "en exercices.", font=sub, fill=GOLD)

    return img.resize((width, height), Image.LANCZOS)


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = os.path.join(root, "store")
    os.makedirs(out, exist_ok=True)

    for name, img in (
        ("icon-512.png", make_icon(512)),
        ("icon-1024.png", make_icon(1024)),
        ("feature-1024x500.png", make_feature()),
    ):
        path = os.path.join(out, name)
        img.save(path, "PNG", optimize=True)
        print(f"  {name:22} {img.size[0]}x{img.size[1]}  {os.path.getsize(path) // 1024} Ko")


if __name__ == "__main__":
    main()
