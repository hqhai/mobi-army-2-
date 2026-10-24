"""Thor costumes drawn the way the game draws its own costumes.

For every character the costume is made from that character's own sprites, so it keeps the game's pixel style,
size and all six equipment frames:
  helmet  = the silver hat ("nón bạc"), brighter, with a white wing at the back and a blonde lock under it
  weapon  = the silver weapon ("súng bạc" and the like)
  armour  = the silver armour, darkened to charcoal with silver studs
  cape    = the Batman costume's cape, in red (Granos has no cape piece)
The pieces are appended under each equipment atlas server/res/bigImage{glass}.png and described in
server/res/thor-costume.json; the server installs them at start (mobiarmy.server.ThorCostume). It also writes the
web icons (web/img/thor), the in-game icons (client/desktop/assets/skins/thor-native/icons.png) and a preview.

Run from the repository root after exporting the equipment catalog from the database:
  mariadb --batch --raw army -e "SELECT glassID, id, type, IFNULL(name,''), vip, IFNULL(data,'null'), x, y, w, h,
      dx, dy, bullet FROM equip ORDER BY glassID, id" > equip.tsv
  python art/thor-native/make_thor_costume.py equip.tsv
Running it again starts from the original atlases (their heights are kept in thor-costume.json).
"""
import json
import os
import sys

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
COSTUME_JSON = os.path.join(ROOT, 'server', 'res', 'thor-costume.json')
FACES = ['yellowP', 'cuteGirl', 'greenP', 'conKhiP', 'rocketer', 'robot', 'ga', 'tazz', 'apache', 'cowg']
SIZE = {3: (30, 32), 6: (29, 24), 7: (32, 32)}  # Equipment.Equip.drawImage
FRAMES = 6
MAX_PNG = 65535  # the client reads the atlas length as an unsigned short

OUTLINE = (8, 8, 8, 255)
WING = ["...##",
        "..#W#",
        ".#WW#",
        "#WWL#",
        "#WLS#",
        ".#S#."]
WING_COLORS = {'#': OUTLINE, 'W': (255, 255, 255, 255), 'L': (214, 222, 232, 255), 'S': (150, 162, 176, 255)}
HAIR = ["YYY#",
        "YYy#",
        ".Yy#",
        ".##."]
HAIR_COLORS = {'#': OUTLINE, 'Y': (247, 215, 94, 255), 'y': (205, 150, 42, 255)}
TOP, BOTTOM = 6, 5  # room above the helmet for the wing, below it for the hair
CAPE = {(0x1f, 0x2e, 0x4b): (0x8a, 0x14, 0x14), (0x28, 0x3e, 0x64): (0xa8, 0x1c, 0x1c), (0x28, 0x3e, 0x63): (0xa8, 0x1c, 0x1c),
        (0x43, 0x52, 0x6f): (0xcc, 0x2a, 0x2a), (0x67, 0x76, 0x93): (0xe0, 0x40, 0x40), (0x8b, 0x9a, 0xb7): (0xf2, 0x6a, 0x5a),
        (0x00, 0x24, 0x6d): (0x5e, 0x0c, 0x0c)}


def load_catalog(path):
    equips = {}
    with open(path, encoding='utf-8') as f:
        next(f)
        for line in f:
            g, i, t, name, vip, data, x, y, w, h, dx, dy, bullet = line.rstrip('\n').split('\t')
            equips[(int(g), int(i))] = {
                'type': int(t), 'name': name, 'vip': int(vip), 'data': None if data in ('null', '') else json.loads(data),
                'x': json.loads(x), 'y': json.loads(y), 'w': json.loads(w), 'h': json.loads(h),
                'dx': json.loads(dx), 'dy': json.loads(dy), 'bullet': int(bullet)}
    return equips


def find(equips, glass, type_, test):
    for (g, i), e in sorted(equips.items()):
        if g == glass and e['type'] == type_ and test(e):
            return i, e
    raise KeyError((glass, type_))


def lum(r, g, b):
    return (r * 299 + g * 587 + b * 114) / 1000


def is_skin(r, g, b):
    return r > 150 and g > 80 and r > g > b and r - b > 60


def grey(r, g, b):
    return max(r, g, b) - min(r, g, b) < 28


def silver_helmet(region):
    """Brighter silver for the grey parts; hair, eyes and coloured trims keep their colour; outlines stay."""
    out = region.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if not a or not grey(r, g, b):
                continue
            l = lum(r, g, b)
            if l < 30:
                continue
            v = 95 + (l - 30) * 150 / 225
            px[x, y] = (int(v), int(v + 6), int(v + 14), a)
    return out


def winged(region):
    """The helmet with a wing at its back (the atlas faces left) and a lock of hair under it."""
    w, h = region.size
    out = Image.new('RGBA', (w + TOP, h + TOP + BOTTOM), (0, 0, 0, 0))
    out.alpha_composite(silver_helmet(region), (0, TOP))
    alpha = [[region.getpixel((x, y))[3] > 0 for x in range(w)] for y in range(h)]
    rows = [y for y in range(h) if any(alpha[y])]
    px = out.load()

    def stamp(shape, colors, left, top, over_outline_only):
        for dy, line in enumerate(shape):
            for dx, ch in enumerate(line):
                x, y = left + dx, top + dy
                if ch == '.' or not (0 <= x < out.width and 0 <= y < out.height):
                    continue
                if px[x, y][3] == 0 or (not over_outline_only and ch != '#'):
                    px[x, y] = colors[ch]

    if rows:
        top = rows[0]
        attach = top + max(1, (rows[-1] - top) // 4)
        back = max((x for x in range(w) if alpha[attach][x]), default=w - 1)
        stamp(WING, WING_COLORS, back - 2, TOP + attach - len(WING) + 2, False)
        low = [y for y in rows if y >= rows[-1] - 2]
        back = max(max((x for x in range(w) if alpha[y][x]), default=0) for y in low)
        bottom = max((y for y in range(h) for x in range(max(0, back - 2), back + 1) if alpha[y][x]), default=rows[-1])
        stamp(HAIR, HAIR_COLORS, back - 2, TOP + bottom + 1, True)
    return out, 0, -TOP


def dark_armour(region):
    out = region.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if not a or is_skin(r, g, b):
                continue
            l = lum(r, g, b)
            if l < 30:
                continue
            if l > 200:
                px[x, y] = (232, 238, 244, a)  # studs
                continue
            t = l / 200.0
            px[x, y] = (int(38 + 70 * t), int(40 + 72 * t), int(46 + 80 * t), a)
    return out, 0, 0


def red_cape(region):
    out = region.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a and (r, g, b) in CAPE:
                px[x, y] = CAPE[(r, g, b)] + (a,)
    return out, 0, 0


def pieces_for(equips, glass):
    """{name: (source piece, transform)} for one character."""
    silver = lambda e: e['vip'] == 0 and e['name'].split(' ')[-1] == 'bạc'
    _, hat = find(equips, glass, 1, silver)
    gun_id, gun = find(equips, glass, 0, silver)
    _, armour = find(equips, glass, 2, silver)
    _, batman = find(equips, glass, 1, lambda e: e['vip'] == 1 and e['name'] == 'batman')
    made = {'helmet': (hat, winged), 'weapon': (gun, lambda r: (r.copy(), 0, 0)), 'armor': (armour, dark_armour)}
    if batman['data'][4] >= 0:
        made['cape'] = (equips[(glass, batman['data'][4])], red_cape)
    return made, gun['bullet']


def build(equips, glass, base):
    """Atlas with the Thor pieces appended under the original, and each piece's frames."""
    made, bullet = pieces_for(equips, glass)
    images, frames = [], {}
    for name, (src, transform) in made.items():
        cache = {}
        frames[name] = []
        for f in range(FRAMES):
            box = (src['x'][f], src['y'][f], src['x'][f] + src['w'][f], src['y'][f] + src['h'][f])
            if box not in cache:
                cache[box] = transform(base.crop(box))
                images.append(cache[box])
            frames[name].append((cache[box], src['dx'][f], src['dy'][f]))
    x = y = row = 0
    spot = {}
    for item in images:
        im = item[0]
        if x + im.width > base.width:
            x, y, row = 0, y + row + 1, 0
        spot[id(item)] = (x, y)
        x += im.width + 1
        row = max(row, im.height)
    atlas = Image.new('RGBA', (base.width, base.height + y + row + 1), (0, 0, 0, 0))
    atlas.alpha_composite(base, (0, 0))
    for item in images:
        atlas.alpha_composite(item[0], (spot[id(item)][0], base.height + spot[id(item)][1]))
    pieces = {}
    for name, entries in frames.items():
        piece = {k: [] for k in ('x', 'y', 'w', 'h', 'dx', 'dy')}
        for item, dx, dy in entries:
            sx, sy = spot[id(item)]
            piece['x'].append(sx)
            piece['y'].append(base.height + sy)
            piece['w'].append(item[0].width)
            piece['h'].append(item[0].height)
            piece['dx'].append(dx + item[1])
            piece['dy'].append(dy + item[2])
        pieces[name] = piece
    pieces['weapon']['bullet'] = bullet
    return atlas, pieces


def compose(glass, atlas, pieces, frame, canvas=(64, 64), anchor=(32, 52)):
    """One frame as Equipment.PlayerEquip.paint draws it: weapon, cape, body, helmet, armour."""
    img = Image.new('RGBA', canvas, (0, 0, 0, 0))
    W, H = SIZE.get(glass, (24, 24))
    X, Y = anchor

    def piece(name):
        p = pieces.get(name)
        if p:
            region = atlas.crop((p['x'][frame], p['y'][frame], p['x'][frame] + p['w'][frame], p['y'][frame] + p['h'][frame]))
            img.alpha_composite(region, (X - W // 2 + p['dx'][frame] + 18, Y - H + p['dy'][frame] + 40))

    body = Image.open(os.path.join(ROOT, 'server', 'res', 'player', FACES[glass] + '.png')).convert('RGBA')
    fh = body.height // 10
    piece('weapon')
    piece('cape')
    img.alpha_composite(body.crop((0, frame * fh, body.width, (frame + 1) * fh)), (X - body.width // 2, Y - fh))
    piece('helmet')
    piece('armor')
    return img


def trim(img):
    box = img.getbbox()
    return img.crop(box) if box else img


def main(catalog_path):
    equips = load_catalog(catalog_path)
    previous = json.load(open(COSTUME_JSON, encoding='utf-8')) if os.path.exists(COSTUME_JSON) else None
    result = {'generator': 'art/thor-native/make_thor_costume.py', 'baseHeights': [], 'glasses': []}
    preview = Image.new('RGBA', (64 * 3 * FRAMES, 64 * 3 * 10), (150, 200, 240, 255))
    icons = Image.new('RGBA', (16 * 10, 16), (0, 0, 0, 0))
    for glass in range(10):
        path = os.path.join(ROOT, 'server', 'res', 'bigImage%d.png' % glass)
        current = Image.open(path).convert('RGBA')
        base_height = previous['baseHeights'][glass] if previous else current.height
        base = current.crop((0, 0, current.width, base_height))
        atlas, pieces = build(equips, glass, base)
        atlas.save(path, optimize=True)
        size = os.path.getsize(path)
        if size > MAX_PNG:
            raise SystemExit('%s is %d bytes, the client takes at most %d' % (path, size, MAX_PNG))
        result['baseHeights'].append(base_height)
        result['glasses'].append(dict(glass=glass, **pieces))
        for f in range(FRAMES):
            frame = compose(glass, atlas, pieces, f).resize((192, 192), Image.NEAREST)
            preview.alpha_composite(frame, (f * 192, glass * 192))
        standing = trim(compose(glass, atlas, pieces, 0))
        web = standing.resize((standing.width * 2, standing.height * 2), Image.NEAREST)
        web_id = 30000 if glass == 8 else 30100 + glass
        web.save(os.path.join(ROOT, 'web', 'img', 'thor', '%d.png' % web_id), optimize=True)
        # In-game icon: the helmeted head, i.e. the helmet piece's box (it covers the face) over the body.
        helmet = pieces['helmet']
        W, H = SIZE.get(glass, (24, 24))
        left, top = 32 - W // 2 + helmet['dx'][0] + 18, 52 - H + helmet['dy'][0] + 40
        head = compose(glass, atlas, {'helmet': helmet}, 0).crop((left, top, left + helmet['w'][0], top + helmet['h'][0]))
        head = trim(head)
        scale = min(16 / head.width, 16 / head.height, 1)
        head = head.resize((max(1, round(head.width * scale)), max(1, round(head.height * scale))), Image.NEAREST)
        icons.alpha_composite(head, (glass * 16 + (16 - head.width) // 2, 16 - head.height))
        print('glass %d: atlas %dx%d, %d bytes' % (glass, atlas.width, atlas.height, size))
    with open(COSTUME_JSON, 'w', encoding='utf-8') as f:
        json.dump(result, f, separators=(',', ':'))
        f.write('\n')
    os.makedirs(os.path.join(ROOT, 'client', 'desktop', 'assets', 'skins', 'thor-native'), exist_ok=True)
    icons.save(os.path.join(ROOT, 'client', 'desktop', 'assets', 'skins', 'thor-native', 'icons.png'), optimize=True)
    preview.save(os.path.join(os.path.dirname(__file__), 'preview.png'), optimize=True)
    print('wrote', COSTUME_JSON)


if __name__ == '__main__':
    main(sys.argv[1])
