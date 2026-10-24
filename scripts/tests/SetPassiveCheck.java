package mobiarmy.war;

import java.lang.reflect.Field;
import java.util.ArrayList;
import mobiarmy.server.Equip;
import mobiarmy.server.Glass;
import mobiarmy.server.User;
import mobiarmy.war.Boss.bullet.Bullet;
import sun.misc.Unsafe;

/**
 * SetPassive: which gear unlocks a passive and what each passive does, on real Player and Bullet code with an empty
 * map and no database (same fixture as SkillBalanceCheck).
 */
public class SetPassiveCheck {
    static int checks;

    public static void main(String[] args) throws Exception {
        sets();
        damage();
        ground();
        items();
        ufoItem();
        System.out.println("SET PASSIVE PASSED: " + checks + " checks; set detection, Gunner, Miss 6, Rocketer, Chicky, Electrician, Tarzan, King Kong, Magenta");
    }

    static void sets() throws Exception {
        String[] pieces = {"súng", "nón", "giáp", "kính", "balo"};
        User user = user(0);
        for (int type = 0; type < 5; type++) user.equips.add(equip(0, type, pieces[type] + " vàng", 14));
        check(SetPassive.tier(user, 0) == 6, "full gold +14 is tier 6");
        user.equips.get(2).level2 = 13;
        check(SetPassive.tier(user, 0) == 0, "one piece below +14: no passive");
        user.equips.get(2).level2 = 14;
        user.equips.get(3).name = "kính bạc";
        check(SetPassive.tier(user, 0) == 0, "mixed sets: no passive");
        user.equips.get(3).name = "kính vàng";
        user.equips.get(4).isUse = false;
        check(SetPassive.tier(user, 0) == 0, "four pieces: no passive");
        user.equips.get(4).isUse = true;
        Equip costume = equip(0, 1, "batman", 0);
        costume.vip = 1;
        user.equips.add(costume);
        check(SetPassive.tier(user, 0) == 6, "a costume on top does not break the set");
        check(SetPassive.tier(user, 1) == 0, "the set belongs to its own character");

        User low = user(0);
        for (int type = 0; type < 5; type++) low.equips.add(equip(0, type, pieces[type] + " cannon 1", 14));
        check(SetPassive.tier(low, 0) == 1, "set 1 is tier 1");
        check(SetPassive.STRENGTH[1] == 50 && SetPassive.STRENGTH[6] == 100, "set 1 gives half, gold the full passive");
    }

    static void damage() throws Exception {
        // Gunner "Súng Thần Công": POW cannonball +50% and a hole twice as wide, nobody lucky on his shots, rage 20%
        // faster, recoil of 20% of what the cannonball dealt to an opponent it killed.
        MapData map = map();
        Player target = player(map, 1, 1000, 5, 0);
        Player plainShooter = player(map, 6, 1000, 5, 0);
        plainShooter.team = 3 - target.team;
        int plain = hit(map, plainShooter, target, false);
        Player gunner = player(map, 0, 1000, SetPassive.GUNNER, 6);
        check(hit(map, gunner, target, false) == plain, "a plain Gunner shot: usual damage");
        check(SetPassive.holeScale(gunner, 0) == 100, "a plain Gunner hole");
        gunner.isPow = true;
        check(hit(map, gunner, target, false) == plain * 150 / 100, "the cannonball: +50%");
        check(SetPassive.holeScale(gunner, 0) == 200, "the cannonball digs twice as wide");
        gunner.setTier = 1;
        check(hit(map, gunner, target, false) == plain * 125 / 100, "set 1 cannonball: +25%");
        gunner.setTier = 6;
        map.disableLuck = false;
        SetPassive.noLuck(plainShooter);
        check(!map.disableLuck, "others' shots: luck as usual");
        SetPassive.noLuck(gunner);
        check(map.disableLuck, "Gunner's shots: nobody is lucky");
        map.disableLuck = false;
        gunner.angry = 0;
        gunner.updateAngry(10);
        check(gunner.angry == 15, "Gunner's rage grows 50% faster, got " + gunner.angry);
        target.hpMax = 50;
        gunner.hp = gunner.hpMax;
        SetPassive.beforeShot(gunner);
        apply(map, gunner, target);
        check(target.isDie, "the cannonball kills");
        SetPassive.afterShot(gunner);
        check(gunner.hp == gunner.hpMax - 10, "recoil: 20% of the 50 dealt, got " + (gunner.hpMax - gunner.hp));
        target.isDie = false;
        target.hpMax = 100_000;
        gunner.isPow = false;
        gunner.hp = gunner.hpMax;
        SetPassive.beforeShot(gunner);
        apply(map, gunner, target);
        SetPassive.afterShot(gunner);
        check(gunner.hp == gunner.hpMax, "no kill, no recoil");

        // Rocketer's shots are plain (his passive is the WorldWar POW, below); Granos digs instead, plain damage too.
        Player rocketer = player(map, 2, 100, SetPassive.ROCKETER, 6);
        Player granos = player(map, 3, 100, 5, 6);
        check(hit(map, rocketer, target, false) == plain, "Rocketer: plain shots");
        rocketer.updateAngry(10);
        check(rocketer.angry == 14, "Rocketer's rage grows 40% faster, got " + rocketer.angry);
        check(hit(map, granos, target, false) == plain, "Granos: no extra damage");

        // Chicky "Trứng Phục sinh": hit by the chicken and its egg, the opponent gets an Easter egg that keeps the
        // damage of that shot. It hatches without digging: Chicky and her team heal a quarter of it wherever they
        // stand, and opponents in reach lose half.
        DigCount map3 = map(DigCount.class);
        Player chicky = player(map3, 0, 100, SetPassive.CHICKY, 6);
        Player foe = player(map3, 1, 300, 5, 0);
        SetPassive.beforeShot(chicky);
        apply(map3, chicky, foe, 19);
        SetPassive.afterShot(chicky);
        check(map3.pendingEggs.isEmpty(), "the chicken alone lays no egg");
        SetPassive.beforeShot(chicky);
        int shot = apply(map3, chicky, foe, 19);
        shot += apply(map3, chicky, foe, 20);
        SetPassive.afterShot(chicky);
        check(map3.pendingEggs.size() == 1, "chicken and egg: an egg at the opponent's feet");
        TimeBomb egg = map3.pendingEggs.get(0);
        check(egg.egg && egg.id >= 100 && egg.x == foe.x && egg.count > 0, "the egg waits its rounds");
        check(shot > 0 && egg.att == shot, "the egg keeps the damage its chicken and egg dealt: " + egg.att + " of " + shot);
        map3.pendingEggs.clear();
        map3.timeBombs.add(egg);
        Player mate = player(map3, 2, 2000, 5, 0);
        mate.team = chicky.team;
        Player farFoe = player(map3, 3, 2600, 5, 0);
        farFoe.team = foe.team;
        chicky.hp = chicky.hpMax / 2;
        mate.hp = mate.hpMax / 2;
        foe.hp = foe.hpMax;
        map3.hatchEgg(egg);
        check(!map3.timeBombs.contains(egg), "the egg is gone once hatched");
        check(foe.hpMax - foe.hp == shot / 4, "the opponent in reach loses a quarter of it: " + (foe.hpMax - foe.hp));
        check(farFoe.hp == farFoe.hpMax, "an opponent out of reach is safe");
        check(chicky.hp == chicky.hpMax / 2 + shot / 10, "Chicky heals a tenth");
        check(mate.hp == mate.hpMax / 2 + shot / 10, "and so does her team, wherever they stand");
        check(map3.holes == 0, "the egg digs no ground");

        // Every 5th of her eggs to hit an opponent in a match lays one too, where it landed; set 1 is half as strong.
        MapData map4 = map();
        Player hen = player(map4, 0, 100, SetPassive.CHICKY, 6);
        Player target4 = player(map4, 1, 300, 5, 0);
        for (int i = 0; i < SetPassive.CHICKY_EGG_HITS - 1; i++) {
            SetPassive.beforeShot(hen);
            apply(map4, hen, target4, 20);
            SetPassive.afterShot(hen);
        }
        check(map4.pendingEggs.isEmpty(), "4 eggs that hit: no Easter egg yet");
        SetPassive.beforeShot(hen);
        int fifth = apply(map4, hen, target4, 20);
        SetPassive.afterShot(hen);
        check(map4.pendingEggs.size() == 1 && map4.pendingEggs.get(0).x == target4.x && map4.pendingEggs.get(0).att == fifth,
                "the 5th egg to hit lays one, keeping the damage of its shot");
        check(hen.eggHits == 0, "and the count starts over");
        SetPassive.beforeShot(hen);
        apply(map4, hen, target4, 19);
        apply(map4, hen, target4, 20);
        SetPassive.afterShot(hen);
        check(map4.pendingEggs.size() == 1, "one Easter egg of hers at a time: none while the first waits");
        Player weak = player(map4, 2, 100, SetPassive.CHICKY, 1);
        SetPassive.beforeShot(weak);
        int weakShot = apply(map4, weak, target4, 19) + apply(map4, weak, target4, 20);
        SetPassive.afterShot(weak);
        check(map4.pendingEggs.size() == 2 && SetPassive.eggShare(weak, weakShot, SetPassive.CHICKY_EGG_HEAL) == weakShot * 5 / 100
                && SetPassive.eggShare(weak, weakShot, SetPassive.CHICKY_EGG_DAMAGE) == weakShot * 12 / 100, "set 1: heals 5%, hurts 12% of the damage");
        MapData coop = map();
        Player layer = player(coop, 0, 100, SetPassive.CHICKY, 0);
        Gun drop = new Gun(coop, layer.index, false);
        drop.shootBullet(19, layer.x, layer.y, 24, 24, 45, 20, 5, 1, 1000, -1);
        for (int i = 0; i < 5; i++) drop.bullets.get(0).nextXY();
        check(drop.bullets.size() == 2 && drop.bullets.get(0).att == 550 && drop.bullets.get(1).att == 300,
                "the chicken carries 55% of Chicky's attack, the egg it drops 30%");

        // Electrician: an opponent hit by 3 bolts in one shot loses his next turn.
        Player elec = player(map3, 4, 100, SetPassive.ELECTRICIAN, 6);
        Player zapped = player(map3, 5, 300, 5, 0);
        SetPassive.beforeShot(elec);
        apply(map3, elec, zapped, 2);
        apply(map3, elec, zapped, 2);
        SetPassive.afterShot(elec);
        check(zapped.stunTurns == 0, "2 bolts: no stun");
        SetPassive.beforeShot(elec);
        for (int i = 0; i < 3; i++) apply(map3, elec, zapped, 2);
        SetPassive.afterShot(elec);
        check(zapped.stunTurns == 1, "3 bolts: the opponent loses his next turn");

        // Miss 6: every third hit ignores defense and crits x1.5.
        Player miss = player(map, 4, 100, SetPassive.MISS6, 6);
        Player armored = player(map, 5, 100, 5, 0);
        armored.def = 6000;
        int normal = hit(map, miss, armored, true);
        check(normal < plain * 50 / 100, "defense cuts a normal hit");
        hit(map, miss, armored, true);
        int third = hit(map, miss, armored, true);
        check(third == plain * 150 / 100, "Miss 6 third hit: true damage x1.5, got " + third + " for " + plain);
        check(hit(map, miss, armored, true) == normal, "the fourth hit is normal again");
        Player ally = player(map, 6, 100, 5, 0);
        ally.team = miss.team;
        ally.def = 6000;
        miss.passiveHits = 2;
        SetPassive.beforeShot(miss);
        check(apply(map, miss, ally) < plain * 50 / 100, "no true damage on a teammate");

        // Tarzan takes 8% less; King Kong 10% less per round stood still, up to 20%.
        Player tarzan = player(map, 7, 100, SetPassive.TARZAN, 6);
        check(hit(map, rocketer, tarzan, false) == plain * 92 / 100, "Tarzan takes 8% less");
        pact();
        worldWar();
        MapData map2 = map();
        Player shooter = player(map2, 0, 100, 5, 0);
        Player kong = player(map2, 1, 300, SetPassive.KINGKONG, 6);
        int full = hit(map2, shooter, kong, false);
        SetPassive.newTurn(kong);
        check(kong.passiveStill == 0, "the first turn does not count");
        SetPassive.newTurn(kong);
        check(hit(map2, shooter, kong, false) == full * 90 / 100, "King Kong after 1 still round: -10%");
        for (int i = 0; i < 5; i++) SetPassive.newTurn(kong);
        check(hit(map2, shooter, kong, false) == full * 80 / 100, "King Kong caps at -20%");
        kong.x += 5;
        SetPassive.newTurn(kong);
        check(hit(map2, shooter, kong, false) == full, "moving resets King Kong");

        alien();
    }

    /**
     * Magenta "Người Ngoài Hành Tinh": when her POW's laser hits an opponent, a UFO on her side is called over him. It
     * fires on its first turn, then flies over the nearest opponent and fires there, until shot down. Its laser only
     * hurts her opponents and its damage counts for her; whoever of them shoots it heals 5% of what he took off it.
     */
    static void alien() throws Exception {
        MapData sky = map();
        sky.players = new Player[16];
        sky.indexBoss = (byte) Room.INDEX_START;
        sky.isWar = true;
        Player magenta = player(sky, 0, 400, SetPassive.MAGENTA, 6);
        Player foe = player(sky, 1, 900, 5, 0);
        magenta.hpMax = 4000;
        magenta.att = 1000;
        laserShot(sky, magenta, foe, false);
        check(sky.players[8] == null, "a plain shot calls no UFO");
        SetPassive.beforeShot(magenta);
        magenta.isPow = true;
        SetPassive.afterShot(magenta);
        check(sky.players[8] == null, "a POW that hits nobody calls none");
        laserShot(sky, magenta, foe, true);
        check(sky.players[8] instanceof mobiarmy.war.Boss.AlienUfo, "her POW on the foe calls a UFO");
        mobiarmy.war.Boss.AlienUfo ufo = (mobiarmy.war.Boss.AlienUfo) sky.players[8];
        check(ufo.team == magenta.team && ufo.leadIndex == magenta.index && !ufo.isFaction && ufo.isTurn && !ufo.isBoss,
                "on her side, with turns of its own, and no fighter (it does not keep her team in the match)");
        check(ufo.x == foe.x && ufo.y == foe.y - SetPassive.ALIEN_ALTITUDE, "it hovers over him: " + ufo.x + "," + ufo.y);
        check(ufo.hpMax == 600 && ufo.att == 500, "15% of her HP, a laser of half her attack: " + ufo.hpMax + " / " + ufo.att);
        check(ufo.hurts(foe) && !ufo.hurts(magenta), "its laser hurts her opponents only");
        laserShot(sky, magenta, foe, true);
        check(sky.players[9] == null, "one UFO of hers at a time");

        // Shooting it: the foe heals 5% of the HP he takes off it, which counts as nobody's damage.
        foe.hp = 1000;
        Bullet shot = new Bullet(new Gun(sky, foe.index, false), 0, 300, ufo.x, ufo.y - 12, 0, 0, 0, 0, 0);
        shot.radius = 16;
        int dealt = foe.statDealt;
        ufo.attBullet(shot);
        ufo.updateAllAffect(foe);
        check(ufo.hp == 300 && foe.hp == 1000 + 15, "shooting it (300) heals the foe 5% (15): ufo " + ufo.hp + ", foe " + foe.hp);
        check(foe.statDealt == dealt, "hitting a UFO is no damage dealt");

        // Its laser's damage counts for Magenta.
        foe.hp = 100_000;
        int before = magenta.statDealt;
        Bullet beam = new Bullet(new Gun(sky, ufo.index, false), 42, 500, foe.x, foe.y - 12, 0, 0, 0, 0, 0);
        beam.radius = 80;
        foe.attBullet(beam);
        foe.updateAllAffect(ufo);
        check(magenta.statDealt - before == 100_000 - foe.hp && foe.hp < 100_000, "the UFO's damage counts for her: " + (magenta.statDealt - before));

        ufo.updateHP(-ufo.hp);
        laserShot(sky, magenta, foe, true);
        check(sky.players[9] instanceof mobiarmy.war.Boss.AlienUfo, "once it is down she calls another");

        MapData practice = map();
        Player alone = player(practice, 0, 400, SetPassive.MAGENTA, 6);
        Player dummy = player(practice, 1, 900, 5, 0);
        practice.indexBoss = (byte) Room.INDEX_START;
        laserShot(practice, alone, dummy, true);
        check(practice.players[0] == alone && practice.players[1] == dummy && !practice.isWar, "practice (no match running) calls no UFO");
        MapData plainSky = map();
        plainSky.players = new Player[16];
        plainSky.indexBoss = (byte) Room.INDEX_START;
        plainSky.isWar = true;
        Player plain = player(plainSky, 0, 400, SetPassive.MAGENTA, 0);
        laserShot(plainSky, plain, player(plainSky, 1, 900, 5, 0), true);
        check(plainSky.players[8] == null, "no set, no UFO");
    }

    /** Magenta's laser (bullet 49) hits target in one of her shots, a POW or not. */
    static void laserShot(MapData map, Player magenta, Player target, boolean pow) {
        magenta.isPow = pow;
        SetPassive.beforeShot(magenta);
        Bullet laser = new Bullet(new Gun(map, magenta.index, pow), 49, 1000, target.x, target.y - 12, 0, 0, 0, 0, 0);
        laser.radius = 16;
        target.attBullet(laser);
        target.attAffect = 0;
        target.attDirect = 0;
        SetPassive.afterShot(magenta);
        magenta.isPow = false;
    }
    /** Wider holes (Granos, Gunner's cannonball), King Kong's rock: ground he protects, smaller and softer banana. */
    static void ground() throws Exception {
        MapData map = map();
        Player elec = player(map, 0, 100, SetPassive.ELECTRICIAN, 6);
        Player granos = player(map, 1, 300, SetPassive.GRANOS, 6);
        check(SetPassive.holeScale(elec, 2) == 100, "Electrician digs as usual now");
        check(SetPassive.holeScale(granos, 7) == 100, "items dig as usual");
        check(SetPassive.holeScale(granos, 11) == 200, "Granos digs twice as wide");
        granos.setTier = 1;
        check(SetPassive.holeScale(granos, 11) == 150, "set 1 Granos: half as wide again");
        check(SetPassive.holeScale(null, 2) == 100, "no shooter, usual hole");

        Player kk = player(map, 2, 600, SetPassive.KINGKONG, 6);
        Player foe = player(map, 3, 1600, 5, 0);
        int banana = kk.bulletIdByGlassID();
        int normal = hit(map, kk, foe, false);
        int[] holes = {100, 90, 85, 70, 70};
        for (int still = 1; still <= 5; still++) {
            kk.passiveStill = still;
            check(SetPassive.holeScale(kk, banana) == holes[still - 1], still + " still rounds: banana hole " + holes[still - 1] + "%");
        }
        kk.passiveStill = 1;
        check(hit(map, kk, foe, false) == normal, "no rock yet: full damage");
        kk.passiveStill = 2;
        check(hit(map, kk, foe, false) == normal * 90 / 100, "on rock: banana 10% softer");

        // Nearest-pixel scaling, the same formula as the client's CMap.scaledMask.
        int[] mask = {1, 2, 3, 4};
        int[] size = new int[2];
        int[] big = SetPassive.scaleMask(mask, 2, 2, 150, size);
        check(size[0] == 3 && size[1] == 3, "150% of 2x2 is 3x3");
        check(java.util.Arrays.equals(big, new int[] {1, 1, 2, 1, 1, 2, 3, 3, 4}), "nearest pixel: " + java.util.Arrays.toString(big));

        // A real hole in a solid block, with and without King Kong standing on it.
        int type = mobiarmy.server.GameData.getHoleType(0);
        java.awt.image.BufferedImage hole = new java.awt.image.BufferedImage(20, 20, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < 20; i++) for (int j = 0; j < 20; j++) hole.setRGB(j, i, 0xff000000); // black digs, red only burns the edge
        java.awt.image.BufferedImage saved = mobiarmy.server.GameData.holeIMask[type];
        mobiarmy.server.GameData.holeIMask[type] = hole;
        try {
            MapData land = map();
            int[] solid = new int[100 * 100];
            java.util.Arrays.fill(solid, 0xff808080);
            land.data.add(land.new Image(0, 0, 0, solid, 100, 100, true));
            land.makeHole(50, 50, 0, 100);
            check(!land.isCollisionMap(50, 50) && land.isCollisionMap(70, 50), "a plain hole digs 20x20");
            land.makeHole(20, 20, 0, 200);
            check(!land.isCollisionMap(20 + 18, 20) && land.isCollisionMap(20 + 22, 20), "a 200% hole digs 40x40");
            Player kong = player(land, 0, 80, SetPassive.KINGKONG, 6);
            kong.y = 82;
            kong.passiveStill = SetPassive.KINGKONG_ROCK_ROUNDS;
            land.protectedGround = SetPassive.protectedGround(land);
            check(land.protectedGround.size() == 1, "King Kong still for 2 rounds protects his ground");
            land.makeHole(80, 72, 0, 100); // rows 62-81; his ground starts at row 78
            check(land.isCollisionMap(80, 80) && !land.isCollisionMap(80, 70), "his ground stays, the rest is dug");
            kong.passiveStill = 1;
            check(SetPassive.protectedGround(land).isEmpty(), "not before 2 rounds");
        } finally {
            mobiarmy.server.GameData.holeIMask[type] = saved;
        }
    }

    /**
     * LAN item 27 "UFO": a UFO on the user's side that flies over his nearest opponent and fires its laser there, with
     * his attack, every other turn; after 3 shots it leaves. It hits only his opponents and its damage counts for him.
     */
    static void ufoItem() throws Exception {
        for (boolean freeForAll : new boolean[] {false, true}) {
            Ground sky = map(Ground.class);
            sky.players = new Player[16];
            sky.indexBoss = (byte) Room.INDEX_START;
            sky.isWar = true;
            Player user = player(sky, 0, 400, 5, 0);
            Player foe = player(sky, 1, 900, 5, 0);
            Player mate = player(sky, 2, 905, 5, 0);
            for (Player p : new Player[] {user, foe, mate}) {
                p.y = 1200; // on the ground
                p.hp = p.hpMax = 100_000;
            }
            mate.team = user.team;
            if (freeForAll) user.team = foe.team = mate.team = 3;
            user.att = 1000;
            sky.shootBullet(user.index, false, 53, user.x, user.y, 24, 24, 0, 0, 0, 1, user.att, -1); // MapData.useItem(27)
            check(sky.players[8] instanceof mobiarmy.war.Boss.bullet.ItemUfo, "item 27 calls a UFO");
            Player ufo = sky.players[8];
            check(ufo.att == 1000 && ufo.caller() == user && !ufo.isFaction, "with the user's attack, on his side, no fighter");
            int shots = 0, lost = 0, turns = 0;
            for (; turns < 10 && !ufo.isDie; turns++) {
                // Its turn, as MapData.update gives it.
                sky.isTurnPlayer = true;
                sky.indexTurnPlayer = ufo.index;
                sky.timeUntilAction2 = 0;
                ufo.isShoot = false;
                int before = foe.hp, mateBefore = mate.hp;
                ufo.update();
                if (freeForAll) lost += mateBefore - mate.hp; // no sides in a free-for-all room: he is a foe too
                if (foe.hp < before) {
                    shots++;
                    lost += before - foe.hp;
                    check(before - foe.hp <= 1000, "a laser hits with at most the user's attack: " + (before - foe.hp));
                }
            }
            String room = freeForAll ? " (free-for-all room)" : "";
            check(shots == 3 && ufo.isDie && turns == 6, "it flies over the foe, fires, and after 3 shots it leaves" + room + ": " + shots + " shots in " + turns + " turns");
            check(user.hp == user.hpMax && (freeForAll || mate.hp == mate.hpMax), "its laser spares the user's side" + room);
            check(user.statDealt == lost, "its damage counts for the user" + room + ": " + user.statDealt + " of " + lost);
        }
    }
    /** LAN items 37 "Bê tông cốt thép" and 38 "Cơn mưa độc". */
    static void items() throws Exception {
        MapData map = map();
        Player user = player(map, 0, 300, 5, 0);
        check(SetPassive.protectedGround(map).isEmpty(), "no concrete, nothing protected");
        user.concreteTurns = 2; // what MapData.useItem sets for item 37
        check(SetPassive.protectedGround(map).size() == 1, "concrete protects the ground under you");
        user.updateTurn();
        check(SetPassive.protectedGround(map).size() == 1, "still protected next round");
        user.updateTurn();
        check(SetPassive.protectedGround(map).isEmpty(), "gone after 2 rounds");

        // The poison rain: whoever its Sao băng drops hit heals half as much for 3 turns.
        Player rainer = player(map, 2, 800, 5, 0);
        Player victim = player(map, 3, 800, 5, 0);
        Player away = player(map, 5, 2500, 5, 0);
        rainer.y = 1500;
        victim.y = 1500;
        away.y = 1500;
        rainer.poisonRain = true; // MapData.useItem for item 38, with bulletId 23
        map.shootBullet(rainer.index, false, 23, rainer.x, rainer.y, 24, 24, 90, 1, 0, 1, 0, -1);
        check(victim.antiHealTurns == Player.ANTI_HEAL_TURNS, "the rain marks who it hits");
        check(away.antiHealTurns == 0, "players far away are not marked");
        check(!rainer.poisonRain, "the rain is used up");
        victim.hp = 100;
        victim.updateHP(300);
        check(victim.hp == 250, "poisoned: an HP item heals half (+150)");
        for (int i = 0; i < Player.ANTI_HEAL_TURNS; i++) victim.updateTurn();
        victim.hp = 100;
        victim.updateHP(300);
        check(victim.hp == 400, "after 3 turns heals are whole again");
    }

    /** One shot of the shooter on the target, through beforeShot/afterShot like MapData.shoot. */
    static int hit(MapData map, Player shooter, Player target, boolean counted) {
        if (counted) SetPassive.beforeShot(shooter);
        int lost = apply(map, shooter, target);
        if (counted) SetPassive.afterShot(shooter);
        return lost;
    }

    /**
     * Tarzan "Khế Ước Rừng Xanh": a King Kong across the battle and he deal each other half, whatever the set; one on
     * his side and he deal 20% more and take 10% less while both stand. His boomerang ignores the wind and blows up
     * half as wide again; he walks 120 steps a turn.
     */
    static void pact() throws Exception {
        MapData foes = map();
        Player tarzan = player(foes, 0, 100, SetPassive.TARZAN, 6);
        Player kong = player(foes, 1, 300, SetPassive.KINGKONG, 0);
        Player other = player(foes, 2, 500, 5, 0);
        check(hit(foes, tarzan, kong, false) == 500, "pact: Tarzan deals half to a King Kong across the battle");
        check(hit(foes, kong, tarzan, false) == 500 * 92 / 100, "and takes half from him (then his own 8% less)");
        check(hit(foes, other, kong, false) == 1000, "others hit King Kong as usual");
        tarzan.setTier = 0;
        check(hit(foes, tarzan, kong, false) == 1000, "no set, no pact");
        tarzan.setTier = 6;

        MapData allies = map();
        Player friend = player(allies, 0, 100, SetPassive.TARZAN, 6);
        Player foe = player(allies, 1, 300, 5, 0);
        Player ape = player(allies, 2, 500, SetPassive.KINGKONG, 0);
        check(hit(allies, friend, foe, false) == 1200, "with King Kong on his side Tarzan deals 20% more");
        check(hit(allies, ape, foe, false) == 1200, "and so does King Kong");
        check(hit(allies, foe, ape, false) == 900, "King Kong takes 10% less");
        check(hit(allies, foe, friend, false) == 900 * 92 / 100, "Tarzan 10% less, then his own 8%");
        friend.setTier = 1;
        check(hit(allies, ape, foe, false) == 1100, "set 1 Tarzan: half the pact's boost");
        friend.setTier = 6;
        ape.isDie = true;
        check(hit(allies, friend, foe, false) == 1000, "the pact holds while both stand");
        ape.isDie = false;

        Player plainTarzan = player(allies, 3, 700, SetPassive.TARZAN, 0);
        check(SetPassive.steps(friend) == 120 && SetPassive.steps(plainTarzan) == 60 && SetPassive.steps(foe) == 60, "Tarzan walks 120 steps a turn");
        check(SetPassive.boomerangWind(friend, 50) == 0 && SetPassive.boomerangWind(plainTarzan, 50) == 5, "his boomerang ignores the wind");
        Gun pactGun = new Gun(allies, friend.index, false), usualGun = new Gun(allies, plainTarzan.index, false);
        pactGun.radius = usualGun.radius = -1; // as shootBullet sets it: the bullet's own radius
        int wide = new mobiarmy.war.Boss.bullet.TarzanBullet(pactGun, 1000, 100, 900, 5, -5).radius;
        int usual = new mobiarmy.war.Boss.bullet.TarzanBullet(usualGun, 1000, 700, 900, 5, -5).radius;
        check(wide == usual * SetPassive.TARZAN_RADIUS / 100 && wide > usual, "and blows up half as wide again: " + wide + " vs " + usual);
    }

    /** Flat ground at y 1200 that counts the holes it would dig. */
    static class Ground extends DigCount {
        Ground() throws java.io.IOException {
            super();
        }

        @Override
        public boolean isCollisionMap(int x, int y) {
            return y >= 1200;
        }
    }

    /**
     * Rocketer "WorldWar": his POW fires a wind-free flare; a bomber drops the nuclear bombs in a tight row across
     * where it lands, and radiation spreads from the spot, up to 20% of his attack and fading to nothing with distance
     * (it spares Rocketer himself unless he stood right on top of it).
     */
    static void worldWar() throws Exception {
        Ground ground = map(Ground.class);
        ground.windX = 50;
        Player rocketer = player(ground, 0, 200, SetPassive.ROCKETER, 6);
        Player far = player(ground, 1, 2800, 5, 0);
        rocketer.y = 1200;
        rocketer.isPow = true;
        Gun gun = new Gun(ground, rocketer.index, true);
        gun.shootBullet(10, rocketer.x, rocketer.y, 24, 24, 45, 20, 0, 1, 1000, -1);
        gun.fillXY();
        check(gun.bulletId == mobiarmy.war.Boss.bullet.WorldWarFlare.ID && gun.bullets.size() == 1 + SetPassive.WORLDWAR_BOMBS,
                "WorldWar: the flare and " + SetPassive.WORLDWAR_BOMBS + " bombs, got " + gun.bullets.size());
        Bullet flare = gun.bullets.get(0);
        check(flare.bulletId == 60 && flare.ax100 == 0 && flare.ay100 == 0, "the flare feels no wind");
        int lastX = Integer.MIN_VALUE;
        boolean apart = true;
        for (int i = 1; i < gun.bullets.size(); i++) {
            Bullet bomb = gun.bullets.get(i);
            Bullet.Frame end = bomb.frames.get(bomb.frames.size() - 1);
            if (bomb.bulletId != 61 || end.fY != 1200 || (lastX != Integer.MIN_VALUE && end.fX - lastX != SetPassive.WORLDWAR_SPACING)) apart = false;
            lastX = end.fX;
        }
        check(apart, "the bombs land on the ground " + SetPassive.WORLDWAR_SPACING + " apart, left to right");
        check(ground.holes == SetPassive.WORLDWAR_BOMBS, "each bomb digs: " + ground.holes);
        check(gun.bullets.get(1).att == 2000, "each bomb hits with twice his attack, like every POW: " + gun.bullets.get(1).att);
        check(gun.worldWarShock == 200, "the radiation starts at a fifth of his attack at the heart: " + gun.worldWarShock);
        check(SetPassive.radiation(200, 0) == 200 && SetPassive.radiation(200, SetPassive.RADIATION_FAR) == 0
                        && SetPassive.radiation(200, SetPassive.RADIATION_FAR + 100) == 0
                        && SetPassive.radiation(200, 100) > SetPassive.radiation(200, 300),
                "radiation is full at the heart, fades with distance, and is gone past RADIATION_FAR");
        int farDist = (int) Math.hypot(far.x - gun.worldWarX, far.y - gun.worldWarY);
        check(far.attAffect == SetPassive.radiation(gun.worldWarShock, farDist),
                "the far opponent takes only the faded radiation for his distance (" + farDist + "): " + far.attAffect);
        int selfDist = (int) Math.hypot(rocketer.x - gun.worldWarX, rocketer.y - gun.worldWarY);
        check(rocketer.attAffect == (selfDist <= SetPassive.RADIATION_NEAR ? SetPassive.radiation(gun.worldWarShock, selfDist) : 0),
                "Rocketer is caught only when within RADIATION_NEAR (he is " + selfDist + " away): " + rocketer.attAffect);

        // Called down on his own head: the bombs spare him, only the radiation catches him.
        Ground home = map(Ground.class);
        Player caller = player(home, 0, 600, SetPassive.ROCKETER, 6);
        caller.y = 1200;
        caller.isPow = true;
        Gun own = new Gun(home, caller.index, true);
        own.shootBullet(10, caller.x, caller.y, 24, 24, 90, 5, 0, 1, 1000, -1);
        own.fillXY();
        int ownDist = (int) Math.hypot(caller.x - own.worldWarX, caller.y - own.worldWarY);
        check(own.bullets.size() == 1 + SetPassive.WORLDWAR_BOMBS && ownDist <= SetPassive.RADIATION_NEAR, "the bombs fall around him");
        check(caller.attAffect == SetPassive.radiation(own.worldWarShock, ownDist), "his own bombs spare him: " + caller.attAffect);

        Gun plainShot = new Gun(ground, rocketer.index, false);
        rocketer.isPow = false;
        plainShot.shootBullet(10, rocketer.x, rocketer.y, 24, 24, 45, 20, 0, 1, 1000, -1);
        check(plainShot.bulletId == 10 && plainShot.bullets.size() == 3, "without POW: his three rockets");
        rocketer.setTier = 0;
        rocketer.isPow = true;
        Gun noSet = new Gun(ground, rocketer.index, true);
        noSet.shootBullet(10, rocketer.x, rocketer.y, 24, 24, 45, 20, 0, 1, 1000, -1);
        check(noSet.bulletId == 10 && noSet.bullets.size() == 3, "no set: the usual POW rockets");
    }

    static int apply(MapData map, Player shooter, Player target) {
        return apply(map, shooter, target, 0);
    }

    static int apply(MapData map, Player shooter, Player target, int bulletId) {
        Bullet bullet = new Bullet(new Gun(map, shooter.index, false), bulletId, 1000, target.x, target.y - 12, 0, 0, 0, 0, 0);
        bullet.radius = 16;
        target.hp = target.hpMax;
        target.lucky = false;
        shooter.lucky = false;
        target.attBullet(bullet);
        target.updateAllAffect(shooter);
        return target.hpMax - target.hp;
    }

    /** A map that counts the holes it would dig. */
    static class DigCount extends MapData {
        int holes;

        DigCount() throws java.io.IOException {
            super(null, (byte) 0);
        }

        @Override
        public void makeHole(int x, int y, int bulletType, int scale) {
            this.holes++;
        }
    }

    static MapData map() throws Exception {
        return map(MapData.class);
    }

    static <T extends MapData> T map(Class<T> type) throws Exception {
        T map = type.cast(unsafe().allocateInstance(type));
        map.width = 3000;
        map.height = 2000;
        map.players = new Player[8];
        map.data = new ArrayList<>();
        map.tornados = new ArrayList<>();
        map.protectedGround = new ArrayList<>();
        map.timeBombs = new ArrayList<>();
        map.pendingEggs = new ArrayList<>();
        return map;
    }

    static Player player(MapData map, int index, int x, int glassId, int setTier) {
        Player player = new Player(map, null, "p" + index, glassId, 1, 100_000, 1000, 0, 0, 0, 24, 24, null);
        player.index = (byte) index;
        player.x = (short) x;
        player.y = 1000;
        player.isFly = true; // keeps chuanHoaXY off the empty map
        player.team = index % 2 + 1;
        player.setTier = setTier;
        map.players[index] = player;
        return player;
    }

    static User user(int glassId) throws Exception {
        User user = (User) unsafe().allocateInstance(User.class);
        user.glass = new Glass[10];
        for (int i = 0; i < user.glass.length; i++) {
            user.glass[i] = new Glass();
            user.glass[i].id = (byte) i;
            user.glass[i].user = user;
        }
        user.selectGlass = (byte) glassId;
        user.equips = new ArrayList<>();
        return user;
    }

    static Equip equip(int glassId, int type, String name, int plus) {
        Equip equip = new Equip();
        equip.glassID = (byte) glassId;
        equip.type = (byte) type;
        equip.name = name;
        equip.level2 = (byte) plus;
        equip.isUse = true;
        return equip;
    }

    static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        checks++;
    }
}
