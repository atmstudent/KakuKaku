#!/usr/bin/env python3
"""Draws the launcher icons: the original glyph 画 (tools/icon_kanji.png) followed by a bold 2.

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

KANJI_SCALE = 0.94   # the 2 takes some room, so 画 is a little smaller than before
DIGIT_HEIGHT = 0.70  # of the kanji's height
GAP = 0.07           # of the kanji's width


def compose():
    """画2 on a transparent canvas, tightly cropped, with 画 drawn at its original size scaled by KANJI_SCALE"""
    kw = round(KANJI.width * KANJI_SCALE)
    kanji = KANJI.resize((kw, kw), Image.LANCZOS)

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
    canvas.alpha_composite(digit, (kw + gap, kw - dh))  # sits on the same baseline as 画
    return canvas


def paste_centered(target, art, center, width):
    """Pastes [art] scaled to [width] pixels wide so that its middle is at [center]"""
    height = round(art.height * width / art.width)
    scaled = art.resize((width, height), Image.LANCZOS)
    target.alpha_composite(scaled, (round(center[0] - width / 2), round(center[1] - height / 2)))


def main():
    art = compose()
    old_kanji_width = KANJI.width  # in the 432 px foreground

    for density, size in FOREGROUND.items():
        image = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        # In the 432 px master the old glyph sat in the middle; 画 keeps its place relative to the whole art
        scale = size / 432
        paste_centered(image, art, (size / 2, size / 2), round(art.width * scale))
        image.save(os.path.join(RES, f'mipmap-{density}', 'ic_launcher_foreground.png'))

    for density, size in LEGACY.items():
        path = os.path.join(RES, f'mipmap-{density}', 'ic_launcher.png')
        old = Image.open(path).convert('RGBA')
        mask = old.split()[3]
        icon = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        icon.paste((255, 255, 255, 255), mask=mask)  # the white rounded square, glyph removed
        # In the legacy icon the old glyph was about this wide (measured on the old files: 0.432 of the icon)
        paste_centered(icon, art, (size / 2, size / 2), round(size * 0.432 * art.width / (art.height)))
        icon.save(path)


main()
