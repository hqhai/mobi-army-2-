"""White dot with a grey ring for the aim guide (CPlayer.drawGuideDot tints, fades and scales it).

The guide is a row of round dots in a light sea blue, fainter and smaller the farther along the way. The client has
no circle primitive (mGraphics.fillArc draws a square), so the dot is a 16x16 white disc with a grey ring around it
that fades softly to nothing. The game tints the whole dot, so the core turns light blue (seen on dark maps) and the
ring a deep blue (seen on bright sky, where a light blue alone disappeared).

Run from the repository root: python art/aim-dots/make_aim_dot.py
"""
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUTPUTS = [os.path.join(ROOT, 'client', side, 'assets', 'res', 'gui', 'aimdot.png') for side in ('desktop', 'android')]
PREVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'preview.png')

SIZE = 16
SUPER = 8  # supersampling for a smooth edge
CORE = 4.2  # the white core...
RING = 6.2  # ...then a grey ring (deep blue once tinted)...
EDGE = 7.8  # ...fading to nothing here
RING_GREY = 70

GUIDE = (127, 211, 247)  # CPlayer.GUIDE_COLOR, light sea blue
BRANCH = (196, 236, 255)  # CPlayer.GUIDE_BRANCH, the egg / hammers / laser


def smoothstep(a, b, x):
    t = max(0.0, min(1.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def dot():
    big = SIZE * SUPER
    image = Image.new('RGBA', (big, big), (255, 255, 255, 0))
    pixels = image.load()
    center = big / 2
    for y in range(big):
        for x in range(big):
            d = math.hypot(x + 0.5 - center, y + 0.5 - center) / SUPER
            grey = round(255 + (RING_GREY - 255) * smoothstep(CORE - 0.6, CORE + 0.6, d))
            alpha = round(255 * (1 - smoothstep(RING, EDGE, d)))
            pixels[x, y] = (grey, grey, grey, alpha)
    return image.resize((SIZE, SIZE), Image.LANCZOS)


def preview(image):
    # As the game draws a path over bright sky (top) and a dark map (bottom): smaller and fainter along the way.
    sheet = Image.new('RGBA', (300, 60), (140, 192, 242, 255))
    sheet.paste((26, 42, 58, 255), (0, 30, 300, 60))
    src = image.load()
    for i in range(12):
        walked = i * 60
        tint = GUIDE if i < 9 else BRANCH
        alpha = max(0.25, 0.9 * math.exp(-walked / 700))  # CPlayer.drawGuideDot
        scale = max(0.4, 0.8 - 0.4 * walked / 600)
        tinted = Image.new('RGBA', image.size)
        dst = tinted.load()
        for y in range(SIZE):
            for x in range(SIZE):
                g, a = src[x, y][0], src[x, y][3]
                dst[x, y] = tuple(c * g // 255 for c in tint) + (round(a * alpha),)
        size = max(2, round(SIZE * scale))
        tinted = tinted.resize((size, size), Image.LANCZOS)
        for top in (7, 37):
            sheet.alpha_composite(tinted, (8 + i * 24 + (SIZE - size) // 2, top + (SIZE - size) // 2))
    return sheet


def main():
    image = dot()
    for path in OUTPUTS:
        image.save(path)
    preview(image).save(PREVIEW)
    print('wrote', ', '.join(OUTPUTS))


if __name__ == '__main__':
    main()
