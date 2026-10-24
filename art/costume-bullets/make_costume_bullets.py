"""Costume bullets in the game's own style.

Each character's bullet sheet server/res/bullet{glass}.png holds its bullet kinds (20 x 20, one frame each; Electrician
2 frames, Granos 4), and a gun's `bullet` picks one: silver guns 7, gold 8, the Batman costume 5, Iron Man 6. This adds
four kinds per character, recoloured from the character's own bullets so shapes and sizes stay the game's:
  thor     silver with electric blue (Apache: his hammer kind 4 as Mjölnir, steel head and leather grip)
  batman   the Batman kind 5 in black and night blue with yellow glints (it was olive)
  mummy    bandage linen with wrapping stripes
  pumpkin  orange with a green stem
Iron Man keeps kind 6, already red with a blue core. The kinds' indexes go to server/res/costume-bullets.json; the server
gives them to the costumes' guns at start (mobiarmy.server.CostumeBullets). Run from the repository root:
  python art/costume-bullets/make_costume_bullets.py
Running it again starts from the original sheets (their heights are kept in the JSON).
"""
import json
import os

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT_JSON = os.path.join(ROOT, 'server', 'res', 'costume-bullets.json')
CELL = 20
FRAMES = {2: 2, 5: 4}          # frames per bullet kind (Bullet.paint: drawRegionFrame(..., bulletID, n))
SILVER = {9: 6}                # silver kind; 7 for everyone else (Magenta's gems: 6 is the grey one)
BATMAN = 5
KINDS = ['thor', 'batman', 'mummy', 'pumpkin']


def lum(r, g, b):
    return (r * 299 + g * 587 + b * 114) / 1000


def ramp(colors, l):
    """Colour for luminance l (0..255) along a dark-to-light ramp."""
    t = max(0.0, min(1.0, (l - 30) / 210.0))
    i = min(len(colors) - 2, int(t * (len(colors) - 1)))
    f = t * (len(colors) - 1) - i
    a, b = colors[i], colors[i + 1]
    return tuple(int(a[k] + (b[k] - a[k]) * f) for k in range(3))


def hexes(*values):
    return [tuple(int(v[i:i + 2], 16) for i in (1, 3, 5)) for v in values]


SILVER_BLUE = hexes('#1e2a3a', '#3c5878', '#5f8fc0', '#a6d2f2', '#f0fbff')
ELECTRIC_RAMP = hexes('#0b3a66', '#1d79c2', '#4fc3ff', '#b6ecff', '#ffffff')
STEEL = hexes('#2a3038', '#56606c', '#8a96a4', '#c4ced8', '#f2f6fa')
NIGHT = hexes('#0a0c12', '#161c2c', '#26304a', '#3c4a6c', '#5a6a8c')
LINEN = hexes('#4e4330', '#8a7a58', '#c2b18a', '#e6d9b4', '#fff7de')
ORANGE = hexes('#5a2000', '#a84400', '#e26f0c', '#ff9d2e', '#ffd27a')
YELLOW = (242, 207, 58)
STEM = [(46, 110, 30), (86, 160, 52)]
LEATHER = hexes('#3a2210', '#6b3e1c', '#9a5e2e')


def is_outline(r, g, b):
    return lum(r, g, b) < 28


def is_brown(r, g, b):
    return r > g > b and r - b > 40 and lum(r, g, b) < 170


def recolor(frame, kind, glass):
    out = frame.copy()
    px = out.load()
    w, h = out.size
    opaque = [(x, y) for y in range(h) for x in range(w) if px[x, y][3]]
    top = min((y for _, y in opaque), default=0)
    lums = sorted(lum(*px[x, y][:3]) for x, y in opaque)
    bright = lums[int(len(lums) * 0.9)] if lums else 255  # the brightest tenth of this bullet
    width = {y: sum(1 for xx, yy in opaque if yy == y) for y in range(h)}
    for x, y in opaque:
        r, g, b, a = px[x, y]
        if is_outline(r, g, b):
            continue
        l = lum(r, g, b)
        saturated = max(r, g, b) - min(r, g, b) > 60
        if kind == 'thor':
            if glass == 8 and not (b > r + 50):
                # Mjölnir: the wide rows are the head (steel), the narrow ones the grip (leather); its cyan band glows.
                px[x, y] = (ramp(STEEL, l) if width[y] >= 8 else ramp(LEATHER, l)) + (a,)
            elif saturated:
                px[x, y] = ramp(ELECTRIC_RAMP, l) + (a,)  # gems and cores become lightning blue
            else:
                px[x, y] = ramp(SILVER_BLUE, l) + (a,)
        elif kind == 'batman':
            px[x, y] = (YELLOW if l >= bright and l > 150 else ramp(NIGHT, l)) + (a,)
        elif kind == 'mummy':
            c = ramp(LINEN, l)
            if (x + 2 * y) % 5 == 0:                      # wrapping stripes
                c = tuple(int(v * 0.72) for v in c)
            px[x, y] = c + (a,)
        elif kind == 'pumpkin':
            c = ramp(ORANGE, l)
            if (x % 4) == 0 and l < 200:                  # pumpkin ribs
                c = tuple(int(v * 0.8) for v in c)
            px[x, y] = c + (a,)
    if kind == 'pumpkin' and opaque:
        # A short stem on top, in the middle of the top row.
        xs = sorted(x for x, y in opaque if y == top)
        mid = xs[len(xs) // 2]
        for dy, color in ((0, STEM[1]), (1, STEM[0])):
            if 0 <= top + dy < h and px[mid, top + dy][3]:
                px[mid, top + dy] = color + (255,)
    return out


def main():
    previous = json.load(open(OUT_JSON, encoding='utf-8')) if os.path.exists(OUT_JSON) else None
    result = {'generator': 'art/costume-bullets/make_costume_bullets.py', 'baseHeights': [], 'kinds': []}
    preview_rows = []
    for glass in range(10):
        path = os.path.join(ROOT, 'server', 'res', 'bullet%d.png' % glass)
        sheet = Image.open(path).convert('RGBA')
        base_height = previous['baseHeights'][glass] if previous else sheet.height
        base = sheet.crop((0, 0, sheet.width, base_height))
        frames = FRAMES.get(glass, 1)
        per_kind = CELL * frames
        count = base_height // per_kind
        silver = SILVER.get(glass, 7)
        sources = {'thor': 4 if glass == 8 else silver, 'batman': BATMAN, 'mummy': silver, 'pumpkin': silver}
        added = []
        indexes = {}
        for n, kind in enumerate(KINDS):
            src = base.crop((0, sources[kind] * per_kind, sheet.width, (sources[kind] + 1) * per_kind))
            made = Image.new('RGBA', src.size, (0, 0, 0, 0))
            for f in range(frames):
                cell = src.crop((0, f * CELL, sheet.width, (f + 1) * CELL))
                made.alpha_composite(recolor(cell, kind, glass), (0, f * CELL))
            added.append(made)
            indexes[kind] = count + n
        out = Image.new('RGBA', (sheet.width, base_height + per_kind * len(KINDS)), (0, 0, 0, 0))
        out.alpha_composite(base, (0, 0))
        for n, made in enumerate(added):
            out.alpha_composite(made, (0, base_height + n * per_kind))
        out.save(path, optimize=True)
        size = os.path.getsize(path)
        if size > 32767:
            raise SystemExit('%s is %d bytes; the equipment packet sends its length as a short' % (path, size))
        result['baseHeights'].append(base_height)
        result['kinds'].append(dict(glass=glass, ironman=6, **indexes))
        row = [base.crop((0, k * per_kind, sheet.width, k * per_kind + CELL)) for k in (sources['thor'], BATMAN, 6, silver)]
        row += [made.crop((0, 0, sheet.width, CELL)) for made in added]
        preview_rows.append(row)
        print('glass %d: %d kinds -> %s, %d bytes' % (glass, count, indexes, size))
    with open(OUT_JSON, 'w', encoding='utf-8') as f:
        json.dump(result, f, separators=(',', ':'))
        f.write('\n')
    scale = 4
    preview = Image.new('RGBA', (8 * 24 * scale, 10 * 24 * scale), (150, 200, 240, 255))
    for g, row in enumerate(preview_rows):
        for i, im in enumerate(row):
            preview.alpha_composite(im.resize((im.width * scale, CELL * scale), Image.NEAREST), (i * 24 * scale, g * 24 * scale))
    preview.save(os.path.join(os.path.dirname(__file__), 'preview.png'), optimize=True)
    print('wrote', OUT_JSON)


if __name__ == '__main__':
    main()
