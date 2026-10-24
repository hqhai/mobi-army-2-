"""Chicky's Easter eggs in the colours of each kind of her bullets (her skins).

Reads the classic egg (client res/gui/egg.png, kind 0) and recolours its shell, bands, dots and outline with each
chicken's palette from server/res/bullet6.png. Writes res/gui/eggs.png for the desktop and Android clients (one
32x40 egg per kind, top to bottom; model/TimeBomb draws the row of the egg owner's kind) and a preview here.

Run from the repository root: python art/chicky-eggs/make_eggs.py
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
BASE = os.path.join(ROOT, 'client', 'desktop', 'assets', 'res', 'gui', 'egg.png')
CHICKENS = os.path.join(ROOT, 'server', 'res', 'bullet6.png')
OUTPUTS = [os.path.join(ROOT, 'client', side, 'assets', 'res', 'gui', 'eggs.png') for side in ('desktop', 'android')]
PREVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'preview.png')

# The classic egg's colours, by the part they paint.
PARTS = {
    'shell': 0xfff4c2, 'shell_hi': 0xfffbe7, 'shell_shade': 0xecddae,
    'band_a': 0xff5fa2, 'band_a_hi': 0xff8cbc, 'band_b': 0x47b6ff,
    'dots_a': 0x9cdf70, 'dots_b': 0xffd95d, 'outline': 0x6b3d1f,
}

# Kind -> colour of each part, picked from that chicken (kind 0 keeps the classic egg).
PALETTES = {
    1: dict(shell=0xffe9df, shell_hi=0xfff5f0, shell_shade=0xf2cdbd, band_a=0xe00b62, band_a_hi=0xf2569a,
            band_b=0xdb4d49, dots_a=0x900d05, dots_b=0xf08a6a, outline=0x5a0a1a),            # pink
    2: dict(shell=0xffe1dd, shell_hi=0xfff1ef, shell_shade=0xf2c3bd, band_a=0xcd0c0c, band_a_hi=0xeb474c,
            band_b=0x900d05, dots_a=0xfcd202, dots_b=0xeb474c, outline=0x5a1008),            # red
    3: dict(shell=0xffe8d6, shell_hi=0xfff4ea, shell_shade=0xf5cdb0, band_a=0xda4003, band_a_hi=0xf1712b,
            band_b=0x891901, dots_a=0xfcd202, dots_b=0xf1712b, outline=0x5a1400),            # fire
    4: dict(shell=0xffeadb, shell_hi=0xfff5ee, shell_shade=0xf5d0b8, band_a=0xf1712b, band_a_hi=0xff9c63,
            band_b=0x2fd8e8, dots_a=0xb60e00, dots_b=0xda4003, outline=0x5a1400),            # orange and cyan
    5: dict(shell=0xeceadb, shell_hi=0xf6f5ec, shell_shade=0xd6d3bf, band_a=0x817a53, band_a_hi=0xa09c8c,
            band_b=0x5a542f, dots_a=0xddb34d, dots_b=0x4a4626, outline=0x2a2414),            # army
    6: dict(shell=0xf2f0ea, shell_hi=0xfbfaf6, shell_shade=0xd8d6cf, band_a=0xe01010, band_a_hi=0xff5a5a,
            band_b=0xb60e00, dots_a=0xe8b84a, dots_b=0xd02020, outline=0x3a0a04),            # Iron Man
    7: dict(shell=0xeeeeec, shell_hi=0xf9f9f8, shell_shade=0xd4d4d0, band_a=0x777777, band_a_hi=0xa5a5a3,
            band_b=0x404040, dots_a=0x5c5c5c, dots_b=0x9a9a98, outline=0x2a2a2a),            # silver
    8: dict(shell=0xfff1d6, shell_hi=0xfff8ea, shell_shade=0xf2dcb0, band_a=0xc57d2a, band_a_hi=0xddb34d,
            band_b=0x724919, dots_a=0xfcd202, dots_b=0xc57d2a, outline=0x3d2508),            # gold
    9: dict(shell=0xe6f2fc, shell_hi=0xf4f9fe, shell_shade=0xc8dcef, band_a=0x547eaa, band_a_hi=0x7aa8d3,
            band_b=0x314762, dots_a=0xd23c3c, dots_b=0x7aa8d3, outline=0x1f2c3e),            # Thor
    10: dict(shell=0x2c3654, shell_hi=0x3d4a70, shell_shade=0x222a42, band_a=0xf2cf3a, band_a_hi=0xffe680,
             band_b=0x151b2b, dots_a=0xf2cf3a, dots_b=0x5a6a96, outline=0x0c0f18),           # Batman
    11: dict(shell=0xf2ead6, shell_hi=0xfaf6ea, shell_shade=0xe4d7b2, band_a=0xb0a07a, band_a_hi=0xcfc09a,
             band_b=0x746649, dots_a=0x534934, dots_b=0x7e7357, outline=0x3a3022),           # mummy
    12: dict(shell=0xffe9cf, shell_hi=0xfff4e6, shell_shade=0xf7d2a8, band_a=0xd06108, band_a_hi=0xfd9b2c,
             band_b=0x8c3700, dots_a=0x3c8a2e, dots_b=0xfd9b2c, outline=0x4a1e00),           # pumpkin
}


def rgb(value):
    return (value >> 16) & 255, (value >> 8) & 255, value & 255


def part_of(pixel):
    """The part of the classic egg this colour paints (nearest of its colours)."""
    r, g, b = pixel
    return min(PARTS, key=lambda part: sum((c - d) ** 2 for c, d in zip((r, g, b), rgb(PARTS[part]))))


def recolour(base, palette):
    egg = base.copy()
    pixels = egg.load()
    for y in range(egg.height):
        for x in range(egg.width):
            r, g, b, a = pixels[x, y]
            if a == 0:
                continue
            # The see-through edge pixels are all outline (their colour is the outline's, faded).
            part = 'outline' if a < 255 else part_of((r, g, b))
            pixels[x, y] = rgb(palette[part]) + (a,)
    return egg


def main():
    base = Image.open(BASE).convert('RGBA')
    chickens = Image.open(CHICKENS).convert('RGBA')
    kinds = chickens.height // chickens.width
    if base.size != (32, 40):
        raise SystemExit('expected a 32x40 egg, got %s' % (base.size,))
    if kinds != len(PALETTES) + 1:
        raise SystemExit('bullet6.png has %d kinds, palettes cover %d' % (kinds, len(PALETTES) + 1))
    sheet = Image.new('RGBA', (32, 40 * kinds), (0, 0, 0, 0))
    for kind in range(kinds):
        egg = base if kind == 0 else recolour(base, PALETTES[kind])
        sheet.paste(egg, (0, 40 * kind))
    for path in OUTPUTS:
        sheet.save(path, optimize=True)
        print('wrote', os.path.relpath(path, ROOT))

    scale = 4
    preview = Image.new('RGBA', (kinds * 40 * scale // 2 + 16, 20 * scale // 2 + 40 * scale // 2 + 24), (70, 88, 120, 255))
    for kind in range(kinds):
        egg = sheet.crop((0, 40 * kind, 32, 40 * kind + 40)).resize((32 * scale // 2, 40 * scale // 2), Image.NEAREST)
        chicken = chickens.crop((0, 20 * kind, 20, 20 * kind + 20)).resize((20 * scale // 2, 20 * scale // 2), Image.NEAREST)
        x = 8 + kind * 40 * scale // 2
        preview.paste(chicken, (x + 12, 8), chicken)
        preview.paste(egg, (x, 8 + 20 * scale // 2 + 8), egg)
    preview.save(PREVIEW)
    print('wrote', os.path.relpath(PREVIEW, ROOT))


if __name__ == '__main__':
    main()
