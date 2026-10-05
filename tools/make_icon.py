#!/usr/bin/env python3
"""Draws the launcher icons: the original glyph 画 (tools/icon_kanji.png) followed by a bold superscript 2 (画²).

    tools/make_icon.py

Writes ic_launcher_foreground.png (adaptive icon layer) and ic_launcher.png (legacy icon) for every density
under app/src/main/res/mipmap-*. The legacy icon keeps the white rounded square of the existing file.
"""
import os
from PIL import Image, ImageDraw, ImageFont

RES = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res')
FONT = '/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc'
KANJI = Image.open(os.path.join(os.path.dirname(__file__), 'icon_kanji.png')).convert('RGBA')
FOREGROUND = {'mdpi': 108, 'hdpi': 162, 'xhdpi': 216, 'xxhdpi': 324, 'xxxhdpi': 432}
LEGACY = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}

DIGIT_HEIGHT = 0.58  # the superscript 2 is this fraction of 画's height
GAP = 0.04           # of the kanji's width

# Width of 画² as a fraction of the icon. The adaptive icon shows only the middle two thirds of its layer, and the
# legacy icon is a rounded square of about 80% of the file: both fractions leave a generous margin around the logo
FOREGROUND_ART_WIDTH = 0.46
LEGACY_ART_WIDTH = 0.52


def compose():
    """画² on a transparent canvas, tightly cropped, with 画 at its original size"""
    kw = KANJI.width
    kanji = KANJI

    # Find the font size whose digit is DIGIT_HEIGHT of the kanji's height
    size = 100
    for _ in range(3):
        font = ImageFont.truetype(FONT, size)
        box = font.getbbox('2')
        size = round(size * (kw * DIGIT_HEIGHT) / (box[3] - box[1]))
    font = ImageFont.truetype(FONT, size)
    box = font.getbbox('2')
    dw, dh = box[2] - box[0], box[3] - box[1]

    gap = round(kw * GAP)
    canvas = Image.new('RGBA', (kw + gap + dw, kw), (0, 0, 0, 0))
    canvas.alpha_composite(kanji, (0, 0))
    digit = Image.new('RGBA', (dw, dh), (0, 0, 0, 0))
    ImageDraw.Draw(digit).text((-box[0], -box[1]), '2', font=font, fill=(0, 0, 0, 255))
    canvas.alpha_composite(digit, (kw + gap, 0))  # top aligned with 画: a superscript
    return canvas


def paste_centered(target, art, center, width):
    """Pastes [art] scaled to [width] pixels wide so that its middle is at [center]"""
    height = round(art.height * width / art.width)
    scaled = art.resize((width, height), Image.LANCZOS)
    target.alpha_composite(scaled, (round(center[0] - width / 2), round(center[1] - height / 2)))


def main():
    art = compose()

    for density, size in FOREGROUND.items():
        image = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        paste_centered(image, art, (size / 2, size / 2), round(size * FOREGROUND_ART_WIDTH))
        image.save(os.path.join(RES, f'mipmap-{density}', 'ic_launcher_foreground.png'))

    for density, size in LEGACY.items():
        path = os.path.join(RES, f'mipmap-{density}', 'ic_launcher.png')
        old = Image.open(path).convert('RGBA')
        mask = old.split()[3]
        icon = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        icon.paste((255, 255, 255, 255), mask=mask)  # the white rounded square, glyph removed
        paste_centered(icon, art, (size / 2, size / 2), round(size * LEGACY_ART_WIDTH))
        icon.save(path)


main()
