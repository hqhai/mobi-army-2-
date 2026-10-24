"""Green big numbers for HP gained (CPlayer.painthpChange draws them with Font.bigHealFont).

The game draws every HP change with fcg14m.png, orange digits without a plus sign, so a heal looked like a hit. This
copies the space, the digits and the minus of that font in green (same shading, outline and corners) and adds a plus
in the same style. Characters, top to bottom in 13x21 cells: " 0123456789+-".

Run from the repository root: python art/heal-font/make_heal_font.py
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FONTS = os.path.join(ROOT, 'client', 'desktop', 'assets', 'res', 'font', 'fontT')
SOURCE = os.path.join(FONTS, 'fcg14m.png')
OUTPUT = os.path.join(FONTS, 'fcg14m_heal.png')
PREVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'preview.png')

SOURCE_CHARS = " 0123456789.,:!?()-'/"
CELL_W, CELL_H = 13, 21
DARK, LIGHT = (18, 128, 40), (178, 255, 120)  # green for the orange bottom and the yellow top of a digit


def green(pixel):
    r, g, b, a = pixel
    if a == 0 or r < 200 or b > 60:
        return pixel  # transparent, the black outline or the grey corners stay
    t = max(0.0, min(1.0, (g - 129) / (255 - 129)))  # where on the orange (g 129) to yellow (g 255) shading
    return tuple(round(d + (l - d) * t) for d, l in zip(DARK, LIGHT)) + (a,)


def cell(font, char):
    i = SOURCE_CHARS.index(char)
    return font.crop((0, i * CELL_H, CELL_W, (i + 1) * CELL_H))


def plus(font):
    """A plus as thick as the font's minus, shaded like its digits row by row, outlined in black."""
    four = cell(font, '4')
    fill_of_row = {}
    for y in range(CELL_H):
        for x in range(CELL_W):
            r, g, b, a = four.getpixel((x, y))
            if a and r > 200 and b < 60:
                fill_of_row[y] = (r, g, b, a)
    shape = [
        '..g##g..',
        '..#ff#..',
        '..#ff#..',
        'g##ff##g',
        '#ffffff#',
        '#ffffff#',
        'g##ff##g',
        '..#ff#..',
        '..#ff#..',
        '..g##g..',
    ]
    top = 8
    glyph = Image.new('RGBA', (CELL_W, CELL_H), (255, 255, 255, 0))
    for dy, line in enumerate(shape):
        y = top + dy
        for x, mark in enumerate(line):
            if mark == '#':
                glyph.putpixel((x, y), (0, 0, 0, 255))
            elif mark == 'g':
                glyph.putpixel((x, y), (105, 105, 105, 255))
            elif mark == 'f':
                glyph.putpixel((x, y), fill_of_row.get(y, (255, 213, 0, 255)))
    return glyph


def main():
    font = Image.open(SOURCE).convert('RGBA')
    glyphs = [cell(font, c) for c in ' 0123456789'] + [plus(font), cell(font, '-')]
    sheet = Image.new('RGBA', (CELL_W, CELL_H * len(glyphs)), (255, 255, 255, 0))
    for i, glyph in enumerate(glyphs):
        sheet.paste(glyph, (0, i * CELL_H))
    sheet.putdata([green(p) for p in sheet.getdata()])
    sheet.save(OUTPUT, optimize=True)
    print('wrote', os.path.relpath(OUTPUT, ROOT))

    scale = 4
    text = '+ 1234 - 567'
    chars = ' 0123456789+-'
    widths = [4, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 6]
    preview = Image.new('RGBA', ((sum(widths[chars.index(c)] for c in text) + 8) * scale, (CELL_H + 4) * scale), (60, 70, 90, 255))
    x = 4
    for c in text:
        i = chars.index(c)
        glyph = sheet.crop((0, i * CELL_H, CELL_W, (i + 1) * CELL_H)).resize((CELL_W * scale, CELL_H * scale), Image.NEAREST)
        preview.paste(glyph, (x * scale, 2 * scale), glyph)
        x += widths[i]
    preview.save(PREVIEW)
    print('wrote', os.path.relpath(PREVIEW, ROOT))


if __name__ == '__main__':
    main()
