#!/usr/bin/env python3
"""Builds the tutorial slides and the README screenshots from the raw emulator captures in tools/tutorial/raw.

    tools/make_tutorial.py

The raw captures are 1080x2400 screenshots of the debug build (light mode, demo-mode status bar, sample text from
the home screen). Each slide is cropped below the status bar, annotated (orange frames with numbered badges, the
numbers are explained in the slide text in strings.xml) and written as WebP to
app/src/main/res/drawable-nodpi/tutorial_N.webp. The plain README screenshots go to docs/screenshots.
To change an annotation, edit SLIDES below (rectangles are in raw pixels: left, top, right, bottom) and run again.
"""
import os
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, 'tutorial', 'raw')
OUT = os.path.join(HERE, '..', 'app', 'src', 'main', 'res', 'drawable-nodpi')
DOCS = os.path.join(HERE, '..', 'docs', 'screenshots')
FONT = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'

CROP_TOP = 130          # drops the status bar (clock, screen recording chip)
SLIDE_WIDTH = 640
README_WIDTH = 540
ORANGE = (255, 109, 0)

# (raw file, [(number, (left, top, right, bottom)[, 'arrow']), ...]); 'arrow' draws a double arrow along the middle of the rectangle instead of a frame
SLIDES = [
    ('01_home.png', [(1, (277, 896, 802, 1043))]),
    ('03_box.png', [(1, (100, 1304, 492, 1696)), (2, (420, 1620, 520, 1720))]),
    ('04_popup.png', [(1, (70, 192, 298, 277)), (2, (26, 360, 1054, 1460))]),
    ('05_quick.png', [(1, (70, 192, 228, 277)), (2, (360, 298, 808, 436))]),
    ('06_select_menu.png', [(1, (670, 1396, 750, 1496)), (2, (760, 1152, 1038, 1236))]),
    ('08_notification.png', [(1, (232, 802, 996, 918))]),
    ('11_filter.png', [(1, (102, 1396, 494, 1788)), (2, (40, 1560, 560, 1620), 'arrow')]),
]

README = [('01_home.png', 'home.png'), ('04_popup.png', 'scan.png'), ('09_dictionaries.png', 'dictionaries.png'), ('10_settings.png', 'settings.png')]


def annotate(image, marks):
    draw = ImageDraw.Draw(image)
    font = ImageFont.truetype(FONT, 58)
    for number, rect, *kind in marks:
        l, t, r, b = rect
        t -= CROP_TOP
        b -= CROP_TOP
        if kind and kind[0] == 'arrow':
            y = (t + b) // 2
            head = 34
            draw.line((l + head, y, r - head, y), fill=ORANGE, width=14)
            draw.polygon([(l, y), (l + head * 1.6, y - head), (l + head * 1.6, y + head)], fill=ORANGE)
            draw.polygon([(r, y), (r - head * 1.6, y - head), (r - head * 1.6, y + head)], fill=ORANGE)
            t = y - 40
        else:
            draw.rounded_rectangle((l, t, r, b), radius=18, outline=ORANGE, width=8)
        # numbered badge on the top-left corner of the frame
        cx, cy, rad = l, t, 40
        draw.ellipse((cx - rad, cy - rad, cx + rad, cy + rad), fill=ORANGE, outline='white', width=5)
        w = draw.textlength(str(number), font=font)
        draw.text((cx - w / 2, cy - 34), str(number), font=font, fill='white')


def scaled(image, width):
    return image.resize((width, round(image.height * width / image.width)), Image.LANCZOS)


def main():
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(DOCS, exist_ok=True)

    for index, (name, marks) in enumerate(SLIDES, start=1):
        image = Image.open(os.path.join(RAW, name)).convert('RGB')
        image = image.crop((0, CROP_TOP, image.width, image.height))
        annotate(image, marks)
        path = os.path.join(OUT, 'tutorial_%d.webp' % index)
        scaled(image, SLIDE_WIDTH).save(path, 'WEBP', quality=82, method=6)
        print(path, os.path.getsize(path) // 1024, 'KB')

    for raw, name in README:
        image = Image.open(os.path.join(RAW, raw)).convert('RGB')
        image = image.crop((0, CROP_TOP, image.width, image.height))
        path = os.path.join(DOCS, name)
        scaled(image, README_WIDTH).save(path, 'PNG', optimize=True)
        print(path, os.path.getsize(path) // 1024, 'KB')


if __name__ == '__main__':
    main()
