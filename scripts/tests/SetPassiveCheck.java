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
        chickyEgg();
        items();
        System.out.println("SET PASSIVE PASSED: " + checks + " checks; set detection, Gunner, Miss 6, Rocketer, Chicky, Tarzan, King Kong, Magenta");
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
        // Gunner "Đòn kết liễu": +30% point blank down to -20% from 600 px; POW always lucky; with "Chỉ kẻ đầu" a big
        // blast that stuns.
        MapData map = map();
        Player target = player(map, 1, 1000, 5, 0);
        Player plainShooter = player(map, 6, 1000, 5, 0);
        plainShooter.team = 3 - target.team;
        int plain = hit(map, plainShooter, target, false);
        Player gunner = player(map, 0, 1000, SetPassive.GUNNER, 6);
        check(hit(map, gunner, target, false) == plain * 130 / 100, "point blank: +30%");
        gunner.x = 1300;
        check(hit(map, gunner, target, false) == plain * 105 / 100, "300 px away: +5%");
        gunner.x = 1600;
        check(hit(map, gunner, target, false) == plain * 80 / 100, "600 px away: -20%");
        gunner.x = 2500;
        check(hit(map, gunner, target, false) == plain * 80 / 100, "farther stays at -20%");
        gunner.setTier = 1;
        gunner.x = 1000;
        check(hit(map, gunner, target, false) == plain * 115 / 100, "set 1 point blank: +15%");
        gunner.setTier = 6;
        gunner.lucky = false;
        SetPassive.powLuck(gunner);
        check(!gunner.lucky, "no POW, luck as usual");
        gunner.isPow = true;
        SetPassive.powLuck(gunner);
        check(gunner.lucky, "the POW shot is always lucky");
        gunner.isPow = false;
        check(SetPassive.holeScale(gunner, 0) == 100, "a plain Gunner hole");
        gunner.headGuideShots = 2; // item 36 "Chỉ kẻ đầu"
        SetPassive.beforeShot(gunner);
        check(SetPassive.holeScale(gunner, 0) == 200, "the finisher blast digs twice as wide");
        apply(map, gunner, target);
        check(target.stunTurns == 1, "the finisher stuns: the target loses its next turn");
        SetPassive.afterShot(gunner);
        check(gunner.headGuideShots == 1 && SetPassive.holeScale(gunner, 0) == 100, "one finisher shot used");
        target.stunTurns = 0;
        apply(map, gunner, target);
        check(target.stunTurns == 0, "no stun outside the finisher");

        // Rocketer +25%; Granos digs instead and deals plain damage.
        Player rocketer = player(map, 2, 100, SetPassive.ROCKETER, 6);
        Player granos = player(map, 3, 100, 5, 6);
        check(hit(map, rocketer, target, false) == plain * 125 / 100, "Rocketer +25%");
        check(hit(map, granos, target, false) == plain, "Granos: no extra damage");

        // Chicky: every fourth shot freezes the opponent it hits for one turn.
        MapData map3 = map();
        Player chicky = player(map3, 0, 100, SetPassive.CHICKY, 6);
        Player frozen = player(map3, 1, 300, 5, 0);
        for (int i = 1; i <= 3; i++) hit(map3, chicky, frozen, true);
        check(frozen.countFreeze == 0, "shots 1-3 do not freeze");
        hit(map3, chicky, frozen, true);
        check(frozen.countFreeze == SetPassive.CHICKY_FREEZE, "the fourth shot freezes");
        frozen.updateTurn();
        check(frozen.countFreeze == 1, "frozen through the next turn");
        frozen.updateTurn();
        check(frozen.countFreeze == 0, "thawed the turn after");

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

        // Tarzan takes 12% less; King Kong 10% less per round stood still, up to 30%.
        Player tarzan = player(map, 7, 100, SetPassive.TARZAN, 6);
        check(hit(map, rocketer, tarzan, false) == plain * 125 / 100 * 88 / 100, "Tarzan takes 12% less");
        MapData map2 = map();
        Player shooter = player(map2, 0, 100, 5, 0);
        Player kong = player(map2, 1, 300, SetPassive.KINGKONG, 6);
        int full = hit(map2, shooter, kong, false);
        SetPassive.newTurn(kong);
        check(kong.passiveStill == 0, "the first turn does not count");
        SetPassive.newTurn(kong);
        SetPassive.newTurn(kong);
        check(hit(map2, shooter, kong, false) == full * 80 / 100, "King Kong after 2 still rounds: -20%");
        for (int i = 0; i < 5; i++) SetPassive.newTurn(kong);
        check(hit(map2, shooter, kong, false) == full * 70 / 100, "King Kong caps at -30%");
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

    /** Wider holes (Electrician, Granos), ground King Kong protects, and Chicky's sure egg. */
    static void ground() throws Exception {
        MapData map = map();
        Player elec = player(map, 0, 100, SetPassive.ELECTRICIAN, 6);
        Player granos = player(map, 1, 300, SetPassive.GRANOS, 6);
        check(SetPassive.holeScale(elec, 2) == 150, "Electrician's own bullet digs 50% wider");
        check(SetPassive.holeScale(elec, 7) == 100, "items dig as usual");
        check(SetPassive.holeScale(granos, 11) == 300, "Granos digs three times as wide");
        granos.setTier = 1;
        check(SetPassive.holeScale(granos, 11) == 200, "set 1 Granos: twice as wide");
        check(SetPassive.holeScale(null, 2) == 100, "no shooter, usual hole");

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

    /** Chicky's fourth shot: the server picks the second force that drops the egg on an opponent. */
    static void chickyEgg() throws Exception {
        MapData map = map();
        Player chicky = player(map, 0, 300, SetPassive.CHICKY, 6);
        chicky.y = 1500;
        int ang = 60, force = 20, att = 1000;
        // Where the chicken flies: a trial with the egg dropped as late as possible.
        Gun path = new Gun(map, chicky.index, false);
        path.shootBullet(19, chicky.x, chicky.y, chicky.width, chicky.height, ang, force, 30, 1, att, -1);
        path.fillXY();
        mobiarmy.war.Boss.bullet.Bullet chicken = path.bullets.get(0);
        mobiarmy.war.Boss.bullet.Bullet.Frame drop = chicken.frames.get(12);
        Player enemy = player(map, 1, drop.fX, 5, 0);
        enemy.y = (short) (drop.fY + 160);
        int picked = map.sureHitForce2(chicky, 19, ang, force, 30, att);
        Gun trial = new Gun(map, chicky.index, false);
        trial.dryRun = true;
        trial.shootBullet(19, chicky.x, chicky.y, chicky.width, chicky.height, ang, force, picked, 1, att, -1);
        trial.fillXY();
        check(trial.dryHits.contains(enemy), "the picked second force lands on the enemy (picked " + picked + ")");
        Gun own = new Gun(map, chicky.index, false);
        own.dryRun = true;
        own.shootBullet(19, chicky.x, chicky.y, chicky.width, chicky.height, ang, force, 30, 1, att, -1);
        own.fillXY();
        check(!own.dryHits.contains(enemy), "the player's own second force would have missed");
        check(enemy.hp == enemy.hpMax && map.data.isEmpty(), "trial shots change nothing");
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
        Bullet bullet = new Bullet(new Gun(map, shooter.index, false), 0, 1000, target.x, target.y - 12, 0, 0, 0, 0, 0);
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
