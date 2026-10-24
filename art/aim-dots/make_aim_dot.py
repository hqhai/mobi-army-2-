"""White round dot for the aim guide (CPlayer.drawGuideDot tints and scales it).

The guide used to be a thin polyline. It is now a row of round dots, big at the gun and smaller along the way, like
GunPow's. The client has no circle primitive (mGraphics.fillArc draws a square), so the dot is a 16x16 white disc with
a soft edge and a faint dark rim that keeps it readable over bright sky.

Run from the repository root: python art/aim-dots/make_aim_dot.py
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUTPUTS = [os.path.join(ROOT, 'client', side, 'assets', 'res', 'gui', 'aimdot.png') for side in ('desktop', 'android')]
PREVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'preview.png')

SIZE = 16
SUPER = 8  # supersampling for a smooth edge
RADIUS = 7.0  # the white disc
RIM = 7.9  # the dark rim around it


def dot():
    big = SIZE * SUPER
    image = Image.new('RGBA', (big, big), (0, 0, 0, 0))
    pixels = image.load()
    center = big / 2
    for y in range(big):
        for x in range(big):
            d = ((x + 0.5 - center) ** 2 + (y + 0.5 - center) ** 2) ** 0.5 / SUPER
            if d <= RADIUS:
                pixels[x, y] = (255, 255, 255, 255)
            elif d <= RIM:
                pixels[x, y] = (40, 40, 40, 150)
    return image.resize((SIZE, SIZE), Image.LANCZOS)


def preview(image):
    # Tinted the way the game tints it (blue character guide, yellow hammers, red laser), shrinking along the path.
    sheet = Image.new('RGBA', (220, 40), (120, 170, 220, 255))
    for i, (tint, scale) in enumerate([((0, 0, 255), 1.0), ((0, 0, 255), 0.8), ((0, 0, 255), 0.6),
                                       ((255, 255, 0), 0.6), ((255, 0, 0), 0.45), ((255, 0, 0), 0.35)]):
        size = max(2, round(SIZE * scale))
        tinted = Image.new('RGBA', image.size)
        src, dst = image.load(), tinted.load()
        for y in range(SIZE):
            for x in range(SIZE):
                r, g, b, a = src[x, y]
                dst[x, y] = (r * tint[0] // 255, g * tint[1] // 255, b * tint[2] // 255, a)
        tinted = tinted.resize((size, size), Image.LANCZOS)
        sheet.alpha_composite(tinted, (10 + i * 34 + (SIZE - size) // 2, 12 + (SIZE - size) // 2))
    return sheet


def main():
    image = dot()
    for path in OUTPUTS:
        image.save(path)
    preview(image).save(PREVIEW)
    print('wrote', ', '.join(OUTPUTS))


if __name__ == '__main__':
    main()
