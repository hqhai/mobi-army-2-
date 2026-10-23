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
        check(gunner.angry == 12, "Gunner's rage grows 20% faster, got " + gunner.angry);
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

        // Rocketer +25%; Granos digs instead and deals plain damage.
        Player rocketer = player(map, 2, 100, SetPassive.ROCKETER, 6);
        Player granos = player(map, 3, 100, 5, 6);
        check(hit(map, rocketer, target, false) == plain * 125 / 100, "Rocketer +25%");
        check(hit(map, granos, target, false) == plain, "Granos: no extra damage");

        // Chicky "Trứng Phục sinh": hit by the chicken and its egg, the opponent gets an egg that hatches for 2% of
        // max HP as true damage; Chicky's team in reach heals 2% and keeps its ground.
        MapData map3 = map();
        Player chicky = player(map3, 0, 100, SetPassive.CHICKY, 6);
        Player foe = player(map3, 1, 300, 5, 0);
        SetPassive.beforeShot(chicky);
        apply(map3, chicky, foe, 19);
        SetPassive.afterShot(chicky);
        check(map3.pendingEggs.isEmpty(), "the chicken alone lays no egg");
        SetPassive.beforeShot(chicky);
        apply(map3, chicky, foe, 19);
        apply(map3, chicky, foe, 20);
        SetPassive.afterShot(chicky);
        check(map3.pendingEggs.size() == 1, "chicken and egg: an egg at the opponent's feet");
        TimeBomb egg = map3.pendingEggs.get(0);
        check(egg.egg && egg.id >= 100 && egg.x == foe.x && egg.count > 0, "the egg waits its rounds");
        foe.hp = foe.hpMax;
        map3.timeBombs.add(egg);
        int eggHole = mobiarmy.server.GameData.getHoleType(57);
        java.awt.image.BufferedImage savedHole = mobiarmy.server.GameData.holeIMask[eggHole];
        mobiarmy.server.GameData.holeIMask[eggHole] = new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        map3.hatchEgg(egg);
        mobiarmy.server.GameData.holeIMask[eggHole] = savedHole;
        check(foe.hpMax - foe.hp == foe.hpMax * 2 / 100, "hatching: 2% of max HP, got " + (foe.hpMax - foe.hp));
        Player mate = player(map3, 2, 310, 5, 0);
        mate.team = chicky.team;
        mate.hp = mate.hpMax / 2;
        foe.hp = foe.hpMax;
        map3.hatchEgg(egg);
        check(mate.hp == mate.hpMax / 2 + mate.hpMax * 2 / 100, "a teammate in reach heals 2%");
        check(foe.hpMax - foe.hp == foe.hpMax * 2 / 100, "the opponent is still hurt");

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

        // Tarzan takes 12% less; King Kong 10% less per round stood still, up to 20%.
        Player tarzan = player(map, 7, 100, SetPassive.TARZAN, 6);
        check(hit(map, rocketer, tarzan, false) == plain * 125 / 100 * 88 / 100, "Tarzan takes 12% less");
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

        // Magenta: her shots feel no wind (half the wind with set 1); everyone else feels all of it.
        MapData windy = map();
        windy.windX = 60;
        windy.windY = 0;
        Player magenta = player(windy, 0, 100, SetPassive.MAGENTA, 6);
        Player other = player(windy, 1, 300, 5, 6);
        check(new Gun(windy, magenta.index, false).windX == 0, "full gold Magenta: no wind");
        check(new Gun(windy, other.index, false).windX == 60, "others feel the whole wind");
        magenta.setTier = 1;
        check(new Gun(windy, magenta.index, false).windX == 30, "set 1 Magenta: half the wind");
        magenta.setTier = 0;
        check(new Gun(windy, magenta.index, false).windX == 60, "no set, no passive");
    }

    /** Wider holes (Granos, Gunner's cannonball), King Kong's rock: ground he protects, smaller and softer banana. */
    static void ground() throws Exception {
        MapData map = map();
        Player elec = player(map, 0, 100, SetPassive.ELECTRICIAN, 6);
        Player granos = player(map, 1, 300, SetPassive.GRANOS, 6);
        check(SetPassive.holeScale(elec, 2) == 100, "Electrician digs as usual now");
        check(SetPassive.holeScale(granos, 7) == 100, "items dig as usual");
        check(SetPassive.holeScale(granos, 11) == 300, "Granos digs three times as wide");
        granos.setTier = 1;
        check(SetPassive.holeScale(granos, 11) == 200, "set 1 Granos: twice as wide");
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
        check(hit(map, kk, foe, false) == normal * 70 / 100, "on rock: banana 30% softer");

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

    static MapData map() throws Exception {
        MapData map = (MapData) unsafe().allocateInstance(MapData.class);
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
