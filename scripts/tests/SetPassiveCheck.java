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
        kick();
        teleport();
        granos();
        healItems();
        trueDamage();
        gifts();
        elements();
        climb();
        gunnerKnock();
        teamPassives();
        System.out.println("SET PASSIVE PASSED: " + checks + " checks; set detection, Gunner, Miss 6, Rocketer, Chicky, Electrician, Tarzan, King Kong, Magenta, Granos, Teleport,"
                + " heal items, true damage, gift boxes, +15 elements, Tarzan's climb, Gunner's knock, team passives");
    }

    /** A player with these match item slots (Player.items), whose turn it is. */
    static Player withItems(MapData map, int index, int x, int glassId, int setTier, byte... items) {
        Player player = new Player(map, null, "p" + index, glassId, 1, 10_000, 1000, 0, 0, 0, 24, 24, items);
        player.index = (byte) index;
        player.x = (short) x;
        player.y = 1000;
        player.isFly = true;
        player.team = index % 2 + 1;
        player.setTier = setTier;
        map.players[index] = player;
        return player;
    }

    static void turnOf(MapData map, Player player) {
        map.isTurnPlayer = true;
        map.indexTurnPlayer = player.index;
        player.isUseItem = false;
        player.isShoot = false;
    }

    /**
     * The HP item heals 35% of the user's max HP (was 350 HP); the team HP item heals each of his team 40% of the user's
     * max HP (was 300). Miss 6 with her set waits one turn less for the Teleport.
     */
    static void healItems() throws Exception {
        MapData map = map();
        Player me = withItems(map, 0, 100, 5, 0, (byte) 0, (byte) 10, (byte) 1);
        Player mate = withItems(map, 2, 300, 5, 0);
        mate.hpMax = 20_000;
        me.hp = 1000;
        mate.hp = 1000;
        turnOf(map, me);
        map.useItem(0, (byte) 0);
        check(me.hp == 1000 + 3500, "the HP item heals 35% of his max HP: " + me.hp);
        turnOf(map, me);
        map.useItem(0, (byte) 10);
        check(me.hp == 4500 + 4000 && mate.hp == 1000 + 4000, "the team HP item heals each 40% of the user's max HP: " + me.hp + ", " + mate.hp);

        Player miss = withItems(map, 4, 500, SetPassive.MISS6, 6, (byte) 1);
        turnOf(map, miss);
        map.useItem(4, (byte) 1);
        check(miss.teleportCooldown == Player.TELEPORT_COOLDOWN - 1, "Miss 6's set: the Teleport waits one turn less: " + miss.teleportCooldown);
        Player plain = withItems(map, 6, 700, SetPassive.MISS6, 0, (byte) 1);
        turnOf(map, plain);
        map.useItem(6, (byte) 1);
        check(plain.teleportCooldown == Player.TELEPORT_COOLDOWN, "without the set as everyone: " + plain.teleportCooldown);
    }

    /** True damage (Granos's lasers, Miss 6's third hit) skips defense, luck and the passives that take damage off. */
    static void trueDamage() throws Exception {
        MapData map = map();
        Player granos = player(map, 0, 100, SetPassive.GRANOS, 6);
        Player tarzan = player(map, 1, 300, SetPassive.TARZAN, 6);
        tarzan.def = 5000;
        tarzan.lucky = true;
        granos.lucky = true;
        tarzan.attAffect = 1000;
        granos.laserStrike = true;
        int before = tarzan.hp;
        tarzan.updateAllAffect(granos);
        granos.laserStrike = false;
        check(before - tarzan.hp == 1000, "a laser takes all of it from Tarzan (no defense, luck or his 8% less): " + (before - tarzan.hp));
    }

    /**
     * From round 10 a gift box floats where the fighters can see it, one a round and never more than there are
     * fighters; shot down it puts a free random item in a free slot of the shooter's, or heals him 20% of his max HP.
     */
    static void gifts() throws Exception {
        MapData map = map();
        map.players = new Player[16];
        map.indexBoss = (byte) Room.INDEX_START;
        map.isWar = true;
        block(map, 0, 1000, 3000, 200);
        Player shooter = withItems(map, 0, 800, 5, 0, (byte) 2, (byte) -1, (byte) 0, (byte) 3);
        Player foe = withItems(map, 1, 1400, 5, 0);
        map.round = MapData.GIFT_ROUND - 1;
        map.dropGift();
        check(map.players[Room.INDEX_START] == null, "no gift box before round " + MapData.GIFT_ROUND);
        map.round = MapData.GIFT_ROUND;
        map.dropGift();
        Player box = map.players[Room.INDEX_START];
        check(box != null && box.gift && box.isFly && box.glassID == 24, "round " + MapData.GIFT_ROUND + ": a floating gift box");
        check(box.x >= 800 - 80 && box.x <= 1400 + 80 && box.y < 1000 && box.y >= 1000 - 180 && !map.isCollisionMap(box.x, box.y),
                "between the fighters, in the air above the ground: " + box.x + "," + box.y);
        map.dropGift();
        check(map.players[Room.INDEX_START + 1] == null, "one a round");
        map.round++;
        map.dropGift();
        map.round++;
        map.dropGift();
        check(map.players[Room.INDEX_START + 1] != null && map.players[Room.INDEX_START + 2] == null, "never more boxes than fighters (2)");
        check(!SetPassive.enemies(shooter, box) && !SetPassive.enemies(box, foe), "a gift box is nobody's opponent");

        box.attAffect = box.hp;
        box.updateAllAffect(shooter);
        Player.Item got = shooter.items[1];
        check(box.isDie && got.itemId >= 2 && got.gift && !got.isUse, "shot down: an item in his free slot, free of charge: " + got.itemId);
        Player other = map.players[Room.INDEX_START + 1];
        shooter.hp = 1000;
        other.attAffect = other.hp;
        other.updateAllAffect(shooter);
        check(shooter.hp == 1000 + 2000, "slots full: 20% of his max HP instead: " + shooter.hp);
        int favoured = 0;
        for (int i = 0; i < 10_000; i++) {
            int id = MapData.giftItem();
            for (int f : MapData.GIFT_FAVOURED) if (f == id) favoured++;
        }
        check(favoured > 3900 && favoured < 4600, "bombs, digging, time bombs and the head guide make about 43% of the gifts: " + favoured);
    }

    /**
     * +15/+16 set elements: each hit has a 10% (+16: 15%) chance to strike; fire takes 15% (25%) more until the end of
     * his next turn, ice keeps him from walking, lightning slows him 30% and lifts his aim, poison takes 3% (5%) of his
     * max HP at the start of each of his next 2 turns.
     */
    static void elements() throws Exception {
        MapData map = map();
        Player shooter = player(map, 0, 100, 5, 0);
        Player foe = player(map, 1, 300, 5, 0);
        shooter.element = SetElement.FIRE;
        int struck = 0;
        for (int i = 0; i < 4000; i++) {
            foe.burnTurns = 0;
            SetElement.hit(shooter, foe);
            if (foe.burnTurns > 0) struck++;
        }
        check(struck > 4000 * 7 / 100 && struck < 4000 * 13 / 100, "+15: about 10% of hits strike: " + struck + " of 4000");
        shooter.elementLevel = 1;
        struck = 0;
        for (int i = 0; i < 4000; i++) {
            foe.burnTurns = 0;
            SetElement.hit(shooter, foe);
            if (foe.burnTurns > 0) struck++;
        }
        check(struck > 4000 * 12 / 100 && struck < 4000 * 18 / 100, "+16: about 15%: " + struck + " of 4000");

        foe.burnTurns = 2;
        foe.burnPercent = SetElement.FIRE_MORE[0];
        check(SetPassive.taken(foe, 1000) == 1150, "burning: 15% more damage");
        SetElement.newTurn(foe);
        check(SetPassive.taken(foe, 1000) == 1150, "through his next turn");
        SetElement.newTurn(foe);
        check(SetPassive.taken(foe, 1000) == 1000 && foe.burnPercent == 0, "then it burns out");

        foe.paralyzeNext = SetElement.FROZEN;
        foe.updateTurn();
        check(foe.paralyzed == SetElement.FROZEN && foe.theluc == 0, "ice: no steps on his next turn");
        foe.updateTurn();
        check(foe.paralyzed == 0 && foe.theluc == 60, "and only that one");

        foe.paralyzeNext = 1;
        foe.paralyzeSlow = SetElement.LIGHTNING_SLOW;
        foe.updateTurn();
        check(foe.theluc == 42 && SetPassive.shockedAngle(foe, 10) == SetPassive.ELECTRICIAN_MIN_ANGLE, "lightning: 30% fewer steps, aims no lower than 60: " + foe.theluc);
        foe.updateTurn();

        foe.hp = foe.hpMax;
        foe.elementPoisonTurns = SetElement.POISON_TURNS;
        foe.elementPoisonHP = SetElement.POISON_HP[0];
        foe.elementPoisonBy = shooter.index;
        foe.updateTurn();
        check(foe.hp == foe.hpMax - foe.hpMax * 3 / 100 && shooter.statDealt == foe.hpMax * 3 / 100, "poison: 3% of his max HP, counted for the shooter");
        foe.updateTurn();
        foe.updateTurn();
        check(foe.hp == foe.hpMax - 2 * (foe.hpMax * 3 / 100), "on 2 turns only: " + foe.hp);
    }

    /**
     * Tarzan's climb (Climb): with his set he stands on any ground, walls and overhangs too, and only falls when it is
     * gone; the server checks a walk against the steps he has left.
     */
    static void climb() throws Exception {
        MapData map = map();
        block(map, 0, 1000, 3000, 200);  // the floor, its top at y 1000
        block(map, 500, 720, 50, 280);   // a wall at x 500-549 up to the overhang
        block(map, 300, 700, 300, 20);   // an overhang at y 700-719 over x 300-599
        Player tarzan = player(map, 0, 400, SetPassive.TARZAN, 6);
        tarzan.isFly = false;
        tarzan.y = 900;
        tarzan.chuanHoaXY();
        check(tarzan.climber() && tarzan.x == 400 && tarzan.y == 999, "he lands on the floor, on the air pixel above it: " + tarzan.y);
        check(Climb.down(map, 499, 950) == Climb.EAST && Climb.down(map, 400, 720) == Climb.NORTH, "against the wall his feet point at it, under the overhang up");
        check(Climb.reach(map, 400, 999, 499, 950, 200) == 99 + 49, "up to the wall and up it: " + Climb.reach(map, 400, 999, 499, 950, 200));
        check(Climb.reach(map, 400, 999, 400, 900, 500) == -1, "not into the air");
        int under = Climb.reach(map, 400, 999, 400, 720, 1000);
        check(under == 99 + 279 + 99, "up the wall and on under the overhang, upside down: " + under);
        check(Climb.reach(map, 400, 999, 400, 720, 300) == -1, "but not with too few steps");

        tarzan.theluc = SetPassive.TARZAN_STEPS;
        tarzan.updateXY(480, 999);
        check(tarzan.x == 480 && tarzan.buocdi == 80, "a walk along the floor costs its pixels: " + tarzan.buocdi);
        tarzan.updateXY(499, 900);
        check(tarzan.x == 480, "too far for what he has left: he stays");
        tarzan.updateXY(499, 980);
        check(tarzan.x == 499 && tarzan.y == 980 && tarzan.buocdi == 80 + 19 + 19, "up the wall: " + tarzan.buocdi);
        tarzan.chuanHoaXY();
        check(tarzan.y == 980, "he holds on to the wall");
        int[] box = tarzan.bodyBox();
        check(box[0] == 499 - 24 && box[2] == 24 && box[1] == 980 - 12, "his body sticks out of the wall: " + java.util.Arrays.toString(box));
        int[] mid = Climb.middle(499, 980, Climb.EAST, 24);
        check(mid[0] == 487 && mid[1] == 980, "his shots leave from the middle of his turned body");

        map.data.remove(1); // the wall is dug away: he falls to the floor
        tarzan.chuanHoaXY();
        check(tarzan.x == 499 && tarzan.y == 999, "the wall gone, he falls onto the floor: " + tarzan.y);

        Player walker = player(map, 1, 1200, 5, 0);
        walker.isFly = false;
        walker.y = 900;
        walker.chuanHoaXY();
        check(!walker.climber() && walker.y == 1000, "anyone else stands on the ground as before: " + walker.y);
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

        // Electrician: an opponent hit by 3 bolts in one shot loses his next turn; by 2 he is locked on it (no walking,
        // no new angle), by 1 slowed (30% fewer steps, no aim below 60 degrees).
        Player elec = player(map3, 4, 100, SetPassive.ELECTRICIAN, 6);
        Player zapped = player(map3, 5, 300, 5, 0);
        SetPassive.beforeShot(elec);
        apply(map3, elec, zapped, 2);
        SetPassive.afterShot(elec);
        check(zapped.stunTurns == 0 && zapped.paralyzeNext == 1 && zapped.paralyzeSlow == 30 && zapped.paralyzed == 0,
                "1 bolt: slowed, from his next turn");
        zapped.updateTurn();
        check(zapped.paralyzed == 1 && zapped.theluc == 42 && zapped.paralyzeNext == 0, "on it he walks 42 steps instead of 60");
        check(SetPassive.shockedAngle(zapped, 45) == 60 && SetPassive.shockedAngle(zapped, 75) == 75
                && SetPassive.shockedAngle(zapped, 135) == 120 && SetPassive.shockedAngle(zapped, -30) == 60
                && SetPassive.shockedAngle(zapped, 330) == 60 && SetPassive.shockedAngle(zapped, 200) == 120,
                "and aims no lower than 60 degrees on either side");
        zapped.updateTurn();
        check(zapped.paralyzed == 0 && zapped.theluc == 60 && SetPassive.shockedAngle(zapped, 45) == 45, "one turn only");
        SetPassive.beforeShot(elec);
        apply(map3, elec, zapped, 2);
        apply(map3, elec, zapped, 2);
        SetPassive.afterShot(elec);
        check(zapped.stunTurns == 0 && zapped.paralyzeNext == 2, "2 bolts: no stun, locked");
        zapped.lastAng = 50;
        zapped.updateTurn();
        check(zapped.paralyzed == 2 && zapped.theluc == 0 && SetPassive.shockedAngle(zapped, 30) == 50,
                "on his next turn he cannot walk and fires at the angle of his last shot");
        int lockedX = zapped.x;
        zapped.updateXY(lockedX + 10, zapped.y);
        check(zapped.x == lockedX, "not even one step");
        zapped.updateTurn();
        SetPassive.beforeShot(elec);
        for (int i = 0; i < 3; i++) apply(map3, elec, zapped, 2);
        SetPassive.afterShot(elec);
        check(zapped.stunTurns == 1 && zapped.paralyzeNext == 0, "3 bolts: the opponent loses his next turn");
        elec.setTier = 1;
        SetPassive.beforeShot(elec);
        apply(map3, elec, zapped, 2);
        SetPassive.afterShot(elec);
        zapped.updateTurn();
        check(zapped.theluc == 51, "set 1: 15% fewer steps");
        zapped.updateTurn();
        elec.setTier = 6;

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

        // Tarzan takes 8% less; King Kong up to 20% less, in step with his size over the 8 rounds he grows.
        Player tarzan = player(map, 7, 100, SetPassive.TARZAN, 6);
        check(hit(map, rocketer, tarzan, false) == plain * 92 / 100, "Tarzan takes 8% less");
        pact();
        worldWar();
        MapData map2 = map();
        Player shooter = player(map2, 0, 100, 5, 0);
        Player kong = player(map2, 1, 300, SetPassive.KINGKONG, 6);
        int full = hit(map2, shooter, kong, false);
        kong.hp = kong.hpMax;
        int baseHp = kong.hpMax;
        SetPassive.newTurn(kong);
        check(kong.passiveStill == 0, "the first turn does not count");
        SetPassive.newTurn(kong);
        // (He also grows, so a blast aimed at his usual middle lands a little off his new one: compare what he takes.)
        check(SetPassive.taken(kong, 1000) == 980, "King Kong after 1 still round: -2% (20% over 8 rounds): " + SetPassive.taken(kong, 1000));
        check(kong.hpMax == baseHp + baseHp * 2 / 100, "and 2% more max HP: " + kong.hpMax + " of " + baseHp);
        for (int i = 0; i < 5; i++) SetPassive.newTurn(kong);
        check(SetPassive.taken(kong, 1000) == 850 && kong.hpMax == baseHp + baseHp * 15 / 100, "after 6 rounds: -15% and +15% max HP: " + SetPassive.taken(kong, 1000) + ", " + kong.hpMax);
        // A blast in the middle of his grown body (300% by now) hurts him as one in the middle of his usual one, less 20%.
        check(SetPassive.bodyPercent(kong) == 250, "after six still rounds King Kong is 250% big");
        Bullet mid = new Bullet(new Gun(map2, shooter.index, false), 0, 1000, kong.x, kong.y - kong.bodyHeight() / 2, 0, 0, 0, 0, 0);
        mid.radius = 16;
        kong.hp = kong.hpMax;
        kong.lucky = false;
        shooter.lucky = false;
        kong.attBullet(mid);
        kong.updateAllAffect(shooter);
        int grown = kong.hpMax - kong.hp;
        check(grown <= full * 85 / 100 && grown >= full * 84 / 100, "a hit on him then: about 85% (" + grown + " of " + full + ")");
        for (int i = 0; i < 4; i++) SetPassive.newTurn(kong);
        check(SetPassive.taken(kong, 1000) == 800 && kong.hpMax == baseHp + baseHp * 20 / 100 && SetPassive.bodyPercent(kong) == 300,
                "full size after 8 rounds: -20%, +20% max HP, 300% big: " + SetPassive.taken(kong, 1000) + ", " + kong.hpMax);
        check(SetPassive.powDamage(kong, true) == 130 && SetPassive.powDamage(kong, false) == 100, "his POW hits at most 30% harder (was 3 times)");

        // Grown, his bananas leave from outside his body (as MapData.shoot fires them): they no longer hit him at once.
        MapData land = map();
        block(land, 0, 1000, 3000, 200);
        Player big = player(land, 0, 1000, SetPassive.KINGKONG, 6);
        big.isFly = false;
        big.passiveStill = 8;
        for (int ang = 30; ang <= 90; ang += 10) {
            Gun bananas = new Gun(land, 0, false);
            bananas.shootBullet(9, big.x, big.y, big.bodyWidth(), big.bodyHeight(), ang, 15, 0, 1, 1000, -1);
            boolean inside = false;
            for (Bullet b : bananas.bullets) inside |= big.isCollision(b.bX, b.bY);
            check(!inside, "at " + ang + " degrees his 4 bananas start outside his 300% body");
        }
        kong.x += 5;
        SetPassive.newTurn(kong);
        check(hit(map2, shooter, kong, false) == full && kong.hpMax == baseHp, "moving resets King Kong, his max HP too: " + kong.hpMax);

        alien();
    }

    /**
     * Magenta "Người Ngoài Hành Tinh": when her POW's laser hits an opponent, a UFO on her side is called over him. It
     * fires on its first turn, then flies over the nearest opponent and fires there, until shot down. Its laser only
     * hurts her opponents and its damage counts for her; whoever of them shoots it heals 20% of what he took off it.
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

        // Shooting it: the foe heals 20% of the HP he takes off it, which counts as nobody's damage.
        foe.hp = 1000;
        Bullet shot = new Bullet(new Gun(sky, foe.index, false), 0, 300, ufo.x, ufo.y - 12, 0, 0, 0, 0, 0);
        shot.radius = 16;
        int dealt = foe.statDealt;
        ufo.attBullet(shot);
        ufo.updateAllAffect(foe);
        check(ufo.hp == 300 && foe.hp == 1000 + 60, "shooting it (300) heals the foe 20% (60): ufo " + ufo.hp + ", foe " + foe.hp);
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
    /** Wider holes (Granos, Gunner's cannonball), King Kong's rock: ground he protects, the same banana, a bigger body. */
    static void ground() throws Exception {
        MapData map = map();
        Player elec = player(map, 0, 100, SetPassive.ELECTRICIAN, 6);
        Player granos = player(map, 1, 300, SetPassive.GRANOS, 6);
        check(SetPassive.holeScale(elec, 2) == 115, "Electrician's bolts are 15% bigger, holes too");
        Gun bolts = new Gun(map, 0, false);
        bolts.shootBullet(2, elec.x, elec.y, 24, 24, 45, 20, 0, 1, 1000, -1);
        check(bolts.bullets.size() == 3 && bolts.bullets.get(0).radius == mobiarmy.server.GameData.radius(2) * 115 / 100,
                "and blow up 15% wider: " + bolts.bullets.get(0).radius);
        check(SetPassive.holeScale(granos, 7) == 100, "items dig as usual");
        check(SetPassive.holeScale(granos, 11) == 180, "Granos digs 1.8 times as wide");
        granos.setTier = 1;
        check(SetPassive.holeScale(granos, 11) == 140, "set 1 Granos: 1.4 times");
        check(SetPassive.holeScale(null, 2) == 100, "no shooter, usual hole");

        Player kk = player(map, 2, 600, SetPassive.KINGKONG, 6);
        Player foe = player(map, 3, 1600, 5, 0);
        int banana = kk.bulletIdByGlassID();
        int normal = hit(map, kk, foe, false);
        // As he grows his usual bananas shrink, and their holes: 50% at full size (8 rounds), but hit as hard.
        int[] shrink = {100, 94, 88, 82, 75, 69, 63, 57, 50, 50};
        for (int still = 0; still < shrink.length; still++) {
            kk.passiveStill = still;
            check(SetPassive.holeScale(kk, banana) == shrink[still] && SetPassive.powGrowth(kk, false) == shrink[still],
                    still + " still rounds: his usual banana and hole " + shrink[still] + "%: " + SetPassive.holeScale(kk, banana));
        }
        kk.passiveStill = 2;
        check(hit(map, kk, foe, false) == normal, "on rock: the banana hits as hard as ever");
        int[] sizes = {100, 125, 150, 175, 200, 225, 250, 275, 300, 300};
        for (int still = 0; still < sizes.length; still++) {
            kk.passiveStill = still;
            check(SetPassive.bodyPercent(kk) == sizes[still], still + " still rounds: King Kong is " + sizes[still] + "% big");
        }
        kk.passiveStill = 0;
        check(!kk.isCollision(kk.x + 13, kk.y - 5) && !kk.isCollision(kk.x, kk.y - 26), "his usual body is 24 px");
        kk.passiveStill = 2;
        check(kk.isCollision(kk.x + 13, kk.y - 5) && kk.isCollision(kk.x, kk.y - 26) && kk.isCollision(kk.x - 13, kk.y - 1),
                "grown, a shot hits him further out on each side and on top, from the feet up");
        check(kk.calcAtt(kk.x + 13, kk.y - 5, 0, 1000) > 0, "and hurts him there");
        kk.setTier = 1;
        check(SetPassive.bodyPercent(kk) == 125, "set 1: half as much growth");
        kk.setTier = 6;
        // His POW grows with him: bananas as much bigger but at most 30% stronger, digging nothing.
        kk.passiveStill = 8;
        kk.isPow = true;
        check(SetPassive.holeScale(kk, banana) == 0 && SetPassive.powGrowth(kk, true) == 300, "300% big: his POW is 3 times bigger and digs no ground");
        Gun pow = new Gun(map, kk.index, true);
        pow.shootBullet(9, kk.x, kk.y, kk.bodyWidth(), kk.bodyHeight(), 45, 20, 0, 1, 1000, -1);
        check(pow.bullets.size() == 4 && pow.bullets.get(0).att == 1000 / 4 * 2 * 130 / 100
                && pow.bullets.get(0).radius == mobiarmy.server.GameData.radius(9) * 3, "its 4 bananas hit 30% harder, 3 times as wide: " + pow.bullets.get(0).att);
        kk.isPow = false;
        check(SetPassive.holeScale(kk, banana) == 50 && SetPassive.powGrowth(kk, false) == 50, "his usual shot half as big at full size");
        Gun usual = new Gun(map, kk.index, false);
        usual.shootBullet(9, kk.x, kk.y, kk.bodyWidth(), kk.bodyHeight(), 45, 20, 0, 1, 1000, -1);
        check(usual.bullets.get(0).att == 1000 / 4 && usual.bullets.get(0).radius == mobiarmy.server.GameData.radius(9) / 2,
                "its bananas hit as hard, half as wide: " + usual.bullets.get(0).radius);
        kk.passiveStill = 2;
        kk.setTier = 1;
        kk.setTier = 0;
        check(SetPassive.bodyPercent(kk) == 100 && SetPassive.bodyPercent(foe) == 100, "no set, and any other character: usual size");
        kk.setTier = 6;
        kk.passiveStill = 0;

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
        SetPassive.worldWarLate = 0; // every bomb goes off as it lands; the late ones: lateNukes()
        try {
            worldWarBombs();
            lateNukes();
        } finally {
            SetPassive.worldWarLate = SetPassive.WORLDWAR_LATE;
        }
    }

    /**
     * Late WorldWar bombs: they dig nothing and hurt nobody as they land, but lie there as time bombs (ids from
     * MapData.LATE_NUKE_FIRST, the shot message says which bullets) that go off a round later, sparing Rocketer.
     */
    static void lateNukes() throws Exception {
        SetPassive.worldWarLate = 100;
        Ground ground = map(Ground.class);
        Player rocketer = player(ground, 0, 600, SetPassive.ROCKETER, 6);
        Player foe = player(ground, 1, 640, 5, 0);
        rocketer.y = 1200;
        foe.y = 1200;
        rocketer.isPow = true;
        Gun gun = new Gun(ground, rocketer.index, true);
        gun.shootBullet(10, rocketer.x, rocketer.y, 24, 24, 90, 5, 0, 1, 1000, -1);
        gun.fillXY();
        int late = 0;
        for (Bullet b : gun.bullets) {
            if (b instanceof mobiarmy.war.Boss.bullet.WorldWarFlare.Bomb bomb && bomb.lateId >= MapData.LATE_NUKE_FIRST
                    && bomb.lateId < MapData.LATE_NUKE_FIRST + MapData.LATE_NUKES) late++;
        }
        check(late == SetPassive.WORLDWAR_BOMBS, "all bombs late at 100%: " + late);
        check(ground.holes == 0, "late bombs dig nothing as they land: " + ground.holes);
        int lying = 0;
        for (TimeBomb bomb : ground.timeBombs) if (bomb.nuke && bomb.leadIndex == rocketer.index && bomb.att == 2000) lying++;
        check(lying == SetPassive.WORLDWAR_BOMBS, "they lie on the ground as time bombs with his POW's damage: " + lying);
        int fighters = 2;
        check(ground.timeBombs.get(0).count == SetPassive.WORLDWAR_LATE_ROUNDS * fighters, "they go off a round later: " + ground.timeBombs.get(0).count);
        rocketer.attAffect = foe.attAffect = 0; // (the radiation of the shot itself)
        TimeBomb nearest = ground.timeBombs.get(0);
        for (TimeBomb bomb : ground.timeBombs) if (Math.abs(bomb.x - foe.x) < Math.abs(nearest.x - foe.x)) nearest = bomb;
        ground.explodeTimeBomb(nearest);
        check(rocketer.hp == rocketer.hpMax, "a late bomb spares Rocketer: " + rocketer.hp + "/" + rocketer.hpMax);
        check(foe.hp < foe.hpMax, "and hurts the opponent next to it: " + foe.hp + "/" + foe.hpMax);
    }

    static void worldWarBombs() throws Exception {
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

    /**
     * Tarzan's POW pull: with a yell, every opponent the POW boomerang hurt flies 60 px toward Tarzan (30 with set
     * 1, never closer than 20 px), over gaps and bumps, until a wall stops him; one pulled off the map dies by Tarzan's hand. Clients of build 7
     * play the flight (message 127), older ones get where he landed (message 53).
     */
    static void kick() throws Exception {
        MapData map = map();
        block(map, 0, 1000, 900, 50);   // the floor, its top at y 1000...
        block(map, 960, 1000, 240, 50); // ...a gap at x 900-959, and nothing past x 1199
        block(map, 900, 1100, 60, 50);  // the bottom of the gap
        block(map, 300, 940, 20, 60);   // a wall 60 px high at x 300-319
        block(map, 700, 992, 10, 8);    // a bump 8 px high at x 700-709
        Player tarzan = player(map, 0, 100, SetPassive.TARZAN, 6);
        Player foe = player(map, 1, 600, 5, 0);
        Player mate = player(map, 2, 610, 5, 0);
        for (Player p : new Player[] {tarzan, foe, mate}) p.isFly = false;

        check(kicked(map, tarzan, foe, 590, false) == 600, "a plain boomerang pulls nobody");
        check(kicked(map, tarzan, foe, 590, true) == 540 && foe.y == 1000, "his POW pulls him 60 px toward Tarzan: " + foe.x + "," + foe.y);
        check(mate.x == 610 && mate.y == 1000, "not his own side");
        foe.x = 600;
        check(kicked(map, tarzan, foe, 610, true) == 540, "toward Tarzan wherever the blast is");
        tarzan.setTier = 1;
        foe.x = 600;
        check(kicked(map, tarzan, foe, 590, true) == 570, "set 1: 30 px");
        tarzan.setTier = 0;
        foe.x = 600;
        check(kicked(map, tarzan, foe, 590, true) == 600, "no set, no pull");
        tarzan.setTier = 6;
        foe.x = 340;
        check(kicked(map, tarzan, foe, 330, true) == 320 && foe.y == 1000, "a wall stops him: " + foe.x + "," + foe.y);
        foe.x = 750;
        check(kicked(map, tarzan, foe, 760, true) == 690 && foe.y == 1000, "he flies over a bump: " + foe.x + "," + foe.y);
        foe.x = 150;
        check(kicked(map, tarzan, foe, 160, true) == 100 + SetPassive.TARZAN_PULL_GAP, "never closer than 20 px to Tarzan: " + foe.x);
        foe.x = 1000;
        check(kicked(map, tarzan, foe, 1010, true) == 940 && foe.y == 1100 && !foe.isDie, "over the gap's edge, down into it: " + foe.x + "," + foe.y);
        tarzan.x = 2000; // Tarzan beyond the end of the ground: pulled off it, he falls off the map
        foe.x = 1180;
        foe.y = 1000;
        int kills = tarzan.statKills;
        kicked(map, tarzan, foe, 1170, true);
        tarzan.x = 100;
        check(foe.isDie && foe.hp == 0 && tarzan.statKills == kills + 1, "kicked off the map he dies, a kill for Tarzan");

        // What the clients get: all hear the yell; build 7 plays the flight, older builds get where he landed.
        Watcher fresh = watcher(mobiarmy.server.ClientVersion.KICK_BUILD), old = watcher(mobiarmy.server.ClientVersion.KICK_BUILD - 1);
        tarzan.user = user(SetPassive.TARZAN);
        tarzan.user.id = 42;
        tarzan.user.session = fresh;
        mate.user = user(5);
        mate.user.session = old;
        Player next = player(map, 3, 600, 5, 0);
        next.isFly = false;
        kicked(map, tarzan, next, 590, true);
        check(fresh.commands.contains(mobiarmy.server.ClientVersion.KICK_MESSAGE) && !fresh.commands.contains(53), "build 7 gets the kick: " + fresh.commands);
        java.io.DataInputStream flight = fresh.read(mobiarmy.server.ClientVersion.KICK_MESSAGE);
        check(flight.readUnsignedByte() == 1 && flight.readUnsignedByte() == 3 && flight.readShort() == 540 && flight.readShort() == 1000,
                "who flies and where he lands");
        check(old.commands.contains(53) && !old.commands.contains(mobiarmy.server.ClientVersion.KICK_MESSAGE), "an older build gets his spot: " + old.commands);
        java.io.DataInputStream spot = old.read(53);
        check(spot.readByte() == 3 && spot.readShort() == 540 && spot.readShort() == 1000, "message 53 puts him there");
        for (Watcher watcher : new Watcher[] {fresh, old}) {
            java.io.DataInputStream yell = watcher.read(9);
            check(yell.readInt() == 42 && yell.readUTF().equals(SetPassive.TARZAN_YELL), "Tarzan yells");
        }
    }

    /**
     * LAN item 1 "Teleport" is not used up: once used it waits 3 of the player's turns (3, 2, 1) and then works again.
     * Status message 114 ends with who has grown (King Kong standing still) and whose Teleport is waiting how long.
     */
    static void teleport() throws Exception {
        MapData map = map();
        Player p = new Player(map, null, "t", 0, 1, 1000, 100, 0, 0, 0, 24, 24, new byte[] {1, 0, -1, -1});
        p.index = 0;
        p.team = 1;
        p.x = 100;
        p.y = 1000;
        p.isFly = true;
        map.players[0] = p;
        Player other = player(map, 1, 600, 5, 0);
        Player kong = player(map, 2, 900, SetPassive.KINGKONG, 6);
        kong.passiveStill = 2;
        Watcher seen = watcher(mobiarmy.server.ClientVersion.LATEST);
        other.user = user(5);
        other.user.session = seen;

        map.useItem(0, (byte) Player.TELEPORT_ITEM);
        check(p.bulletId == 5 && p.teleportCooldown == Player.TELEPORT_COOLDOWN && p.findUnusedItemById(Player.TELEPORT_ITEM) != null,
                "the Teleport works and stays: " + p.teleportCooldown + " turns to wait");
        java.io.DataInputStream status = seen.read(114);
        int count = status.readUnsignedByte();
        status.skipBytes(3 * count);
        check(count == 3 && status.readUnsignedByte() == 0, "status: three players, nobody hidden");
        check(status.readUnsignedByte() == 1 && status.readUnsignedByte() == 2 && status.readUnsignedByte() == 6, "King Kong has his set");
        check(status.readUnsignedByte() == 0, "nobody runs");
        check(status.readUnsignedByte() == 1 && status.readUnsignedByte() == 2 && status.readUnsignedShort() == 150, "King Kong is 150% big");
        check(status.readUnsignedByte() == 1 && status.readUnsignedByte() == 0 && status.readUnsignedByte() == Player.TELEPORT_ITEM
                && status.readUnsignedByte() == 3, "and the Teleport waits 3 turns");
        check(status.readUnsignedByte() == mobiarmy.server.Map.SCALE, "then how many times bigger the maps are");
        check(status.readUnsignedByte() == 0, "then who is shocked by Electrician: nobody");
        check(status.readUnsignedByte() == 0 && status.available() == 0, "and last (build 8) who the +15 elements strike: nobody");

        for (int left = 2; left >= 1; left--) {
            p.updateTurn();
            map.useItem(0, (byte) Player.TELEPORT_ITEM);
            check(p.teleportCooldown == left && !p.isUseItem && p.bulletId != 5, "a turn later it still waits: " + left + " left");
        }
        p.updateTurn();
        map.useItem(0, (byte) Player.TELEPORT_ITEM);
        check(p.bulletId == 5 && p.teleportCooldown == Player.TELEPORT_COOLDOWN, "on the third turn it works again");
        p.updateTurn();
        map.useItem(0, (byte) 0);
        check(p.isUseItem && p.findUnusedItemById(0) == null, "other items are still used up");
    }

    /**
     * Granos "Người Gieo Laze": his POW with the set fires one seed (no wind, no hole, no damage) instead of his five
     * grenades; where it lands 5 grenades drop in a row 36 px apart; two rounds later his lasers strike them from the
     * sky: 1000 true damage each on his opponents it reaches (no defense), nothing on his side; the grenades are gone.
     */
    static void granos() throws Exception {
        // The lasers dig like the UFO's (hole of bullet 42): a 20x20 hole here, as no masks are loaded.
        int laserHole = mobiarmy.server.GameData.getHoleType(42);
        java.awt.image.BufferedImage savedHole = mobiarmy.server.GameData.holeIMask[laserHole];
        java.awt.image.BufferedImage hole = new java.awt.image.BufferedImage(20, 20, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < 20; i++) for (int j = 0; j < 20; j++) hole.setRGB(j, i, 0xff000000);
        mobiarmy.server.GameData.holeIMask[laserHole] = hole;
        try {
            granosRow();
        } finally {
            mobiarmy.server.GameData.holeIMask[laserHole] = savedHole;
        }
    }

    static void granosRow() throws Exception {
        MapData map = map();
        block(map, 0, 1000, 3000, 200);
        map.isWar = true;
        map.windX = 60;
        Player granos = player(map, 0, 300, SetPassive.GRANOS, 6);
        Player foe = player(map, 1, 1000, 3, 0);
        foe.def = 5000; // would take half off a usual hit
        Player mate = player(map, 2, 1036, 5, 0);
        for (Player p : new Player[] {granos, foe, mate}) p.isFly = false;
        check(SetPassive.holeScale(granos, 11) == 180 && SetPassive.holeScale(granos, 42) == 100, "his grenades dig 1.8 times as wide, his lasers as usual");

        Gun pow = new Gun(map, 0, true);
        pow.shootBullet(11, granos.x, granos.y, 24, 24, 45, 20, 0, 1, 1000, -1);
        check(pow.bulletId == mobiarmy.war.Boss.bullet.GranosSeed.ID && pow.bullets.size() == 1
                && pow.bullets.get(0) instanceof mobiarmy.war.Boss.bullet.GranosSeed && pow.bullets.get(0).ax100 == 0,
                "his POW fires one seed, which the wind does not move");
        granos.setTier = 0;
        Gun plain = new Gun(map, 0, true);
        plain.shootBullet(11, granos.x, granos.y, 24, 24, 45, 20, 0, 1, 1000, -1);
        check(plain.bulletId == 11 && plain.bullets.size() == 5, "without the set: his five grenades");
        granos.setTier = 6;

        // The seed lands in front of the foe: a row of grenades drops there, nothing is dug or hurt yet.
        Watcher seen = watcher(mobiarmy.server.ClientVersion.LATEST);
        foe.user = user(3);
        foe.user.session = seen;
        Gun seedGun = new Gun(map, 0, true);
        seedGun.radius = -1;
        seedGun.bullets.add(new mobiarmy.war.Boss.bullet.GranosSeed(seedGun, 1000, 1000, 800, 0, 0));
        seedGun.fillXY();
        check(foe.attAffect == 0 && map.isCollisionMap(1000, 1000), "the seed hurts nobody and digs nothing");
        check(map.pendingEggs.size() == 5, "five grenades: " + map.pendingEggs.size());
        int[] xs = {928, 964, 1000, 1036, 1072};
        for (int k = 0; k < 5; k++) {
            TimeBomb g = map.pendingEggs.get(k);
            check(g.grenade && g.id < 0 && g.leadIndex == 0 && g.x == xs[k] && g.y == 1000 && g.att == 1000 && g.count == 6,
                    "grenade " + k + " lies at " + g.x + "," + g.y + " for 2 rounds of 3 fighters, its laser 1000");
        }

        // Two rounds of three turns: the lasers strike at the sixth turn change.
        int foeHp = foe.hp, mateHp = mate.hp, granosHp = granos.hp, dealt = granos.statDealt;
        for (int turn = 1; turn <= 5; turn++) map.timeBombTurn();
        check(map.timeBombs.size() == 5 && foe.hp == foeHp, "after five turn changes they still wait");
        java.io.DataInputStream laid = seen.read(109);
        check(laid.readByte() == 0 && laid.readByte() < 0 && laid.readInt() == 1072 && laid.readInt() == 1000 && laid.readByte() == 0,
                "the clients get them as time bombs with ids below 0, and whose they are");
        map.timeBombTurn();
        check(map.timeBombs.isEmpty() && map.pendingEggs.isEmpty(), "at the sixth the lasers strike and the grenades go");
        check(foe.hp == foeHp - 2000 && granos.statDealt > dealt,
                "the foe on a grenade loses 1000 from it and 1000 from the next one, his defense counting for nothing: " + (foeHp - foe.hp));
        check(mate.hp == mateHp && granos.hp == granosHp, "his own side loses nothing");
        java.io.DataInputStream beams = seen.read(22);
        beams.readByte();
        beams.readByte();
        check(beams.readByte() == 0 && beams.readByte() == 42, "one shot of UFO lasers, fired by Granos");
        beams.skipBytes(6);
        check(beams.readByte() == 5 && beams.readByte() == 5, "five beams, one after another");
        check(beams.readShort() > 1 && beams.readShort() == 928 && beams.readShort() == 1000 - SetPassive.GRANOS_DROP + 20,
                "the first from the sky above the first grenade");

        // Practice has no turns: the seed lays nothing there.
        map.isWar = false;
        Gun practice = new Gun(map, 0, true);
        practice.radius = -1;
        practice.bullets.add(new mobiarmy.war.Boss.bullet.GranosSeed(practice, 1000, 1500, 800, 0, 0));
        practice.fillXY();
        check(map.pendingEggs.isEmpty(), "no grenades in practice");
    }

    /** Tarzan's boomerang blowing up at x, level with the target; returns where the target stands after the shot. */
    /** Gunner's cannonball knocks every opponent it hurt 40 px away from the blast (his plain shots do not). */
    static void gunnerKnock() throws Exception {
        MapData map = map();
        block(map, 0, 1000, 2000, 50);
        Player gunner = player(map, 0, 100, SetPassive.GUNNER, 6);
        Player foe = player(map, 1, 600, 5, 0);
        Player mate = player(map, 2, 610, 5, 0);
        for (Player p : new Player[] {gunner, foe, mate}) p.isFly = false;
        check(cannonAt(map, gunner, foe, 590, false) == 600, "a plain shot knocks nobody");
        check(cannonAt(map, gunner, foe, 590, true) == 600 + SetPassive.GUNNER_KNOCK && foe.y == 1000, "the cannonball knocks him 40 px: " + foe.x);
        check(mate.x == 610, "not his own side");
        gunner.setTier = 0;
        foe.x = 600;
        check(cannonAt(map, gunner, foe, 590, true) == 600, "no set, no knock");
    }

    static int cannonAt(MapData map, Player gunner, Player target, int x, boolean pow) {
        SetPassive.beforeShot(gunner);
        gunner.isPow = pow;
        Bullet ball = new Bullet(new Gun(map, gunner.index, pow), 0, 1000, x, target.y - 12, 0, 0, 0, 0, 0);
        ball.radius = 30;
        target.hp = target.hpMax;
        for (Player p : map.players) {
            if (p != null && !p.isDie) p.attBullet(ball);
        }
        map.updateAffect(gunner.index);
        SetPassive.afterShot(gunner);
        gunner.isPow = false;
        return target.x;
    }

    /**
     * Team passives (SetTeam), for a side with 2 different characters of a group: Outsiders' POWs deal true damage 25%
     * of the time; the Privates' side takes 1% less a round (up to 30%) and 30% less below half HP; Scientists fire one
     * more bullet and the first of them to fall calls a T-rex on their side.
     */
    static void teamPassives() throws Exception {
        MapData map = map();
        map.players = new Player[16];
        map.indexBoss = (byte) Room.INDEX_START;
        map.isWar = true;
        block(map, 0, 1000, 3000, 200);
        Player chicky = player(map, 0, 100, SetPassive.CHICKY, 0);
        Player gunner = player(map, 1, 300, SetPassive.GUNNER, 0);
        Player apache = player(map, 2, 500, SetPassive.APACHE, 0);
        Player miss = player(map, 3, 700, SetPassive.MISS6, 0);
        Player rocketer = player(map, 4, 900, SetPassive.ROCKETER, 0);
        Player granos = player(map, 6, 1100, SetPassive.GRANOS, 0);
        SetTeam.apply(map, false);
        check(chicky.teamPacts == (SetTeam.OUTSIDER | SetTeam.SCIENTISTS) && rocketer.teamPacts == chicky.teamPacts,
                "team 1: Outsiders (Chicky, Apache) and Scientists (Rocketer, Granos): " + chicky.teamPacts);
        check(gunner.teamPacts == SetTeam.PRIVATES && miss.teamPacts == SetTeam.PRIVATES, "team 2: Privates (Gunner, Miss 6)");
        SetTeam.apply(map, true);
        check(chicky.teamPacts == 0 && gunner.teamPacts == 0, "none in a base damage room");
        miss.glassID = SetPassive.GUNNER;
        SetTeam.apply(map, false);
        check(gunner.teamPacts == 0, "two of the same character are not two of the group");
        miss.glassID = SetPassive.MISS6;
        SetTeam.apply(map, false);

        // Outsiders: a POW rolls 25% for true damage, which no defense takes off.
        int rolls = 0;
        chicky.isPow = true;
        for (int i = 0; i < 4000; i++) {
            SetTeam.beforeShot(chicky);
            if (chicky.outsiderTrue) rolls++;
        }
        check(rolls > 850 && rolls < 1150, "an Outsider's POW is true damage about 25% of the time: " + rolls + "/4000");
        chicky.isPow = false;
        SetTeam.beforeShot(chicky);
        check(!chicky.outsiderTrue, "never his usual shots");
        gunner.def = 20_000;
        map.round = 0;
        int soft = apply(map, chicky, gunner);
        chicky.outsiderTrue = true;
        check(apply(map, chicky, gunner) == 1000 && soft < 1000, "true damage skips his defense (and the Privates' cut): " + soft);
        chicky.outsiderTrue = false;
        gunner.def = 0;

        // Privates: 1% less a round, at most 30%, and 30% less below half HP.
        map.round = 10;
        check(apply(map, chicky, gunner) == 900, "round 10: 10% less: " + apply(map, chicky, gunner));
        map.round = 80;
        check(apply(map, chicky, gunner) == 700, "at most 30% less");
        gunner.hp = gunner.hpMax / 2 - 1;
        check(SetTeam.taken(gunner, 1000) == 700 * 70 / 100, "below half HP 30% less again");
        map.round = 0;
        check(apply(map, gunner, chicky) == 1000, "the other side as usual");

        // Scientists: one more rocket and grenade, one more WorldWar bomb.
        Gun rockets = new Gun(map, rocketer.index, false);
        rockets.shootBullet(10, rocketer.x, rocketer.y, 24, 24, 45, 20, 0, 1, 900, -1);
        check(rockets.bullets.size() == 4, "a Scientist Rocketer fires 4 rockets: " + rockets.bullets.size());
        Gun grenades = new Gun(map, granos.index, true);
        grenades.shootBullet(11, granos.x, granos.y, 24, 24, 45, 20, 0, 1, 1000, -1);
        check(grenades.bullets.size() == 6, "a Scientist Granos 6 grenades, POW too: " + grenades.bullets.size());
        Player lone = player(map, 5, 1300, SetPassive.ROCKETER, 0);
        SetTeam.apply(map, false);
        Gun plain = new Gun(map, lone.index, false);
        plain.shootBullet(10, lone.x, lone.y, 24, 24, 45, 20, 0, 1, 900, -1);
        check(plain.bullets.size() == 3, "a Rocketer without Granos on his side: 3");
        map.players[5] = null;
        SetTeam.apply(map, false);

        // The first Scientist to fall calls a T-rex on his side with his HP and stats; the second calls none.
        rocketer.hpMax = 12_000;
        rocketer.att = 1500;
        rocketer.updateHP(-rocketer.hp);
        SetTeam.callTrex(map);
        Player trex = map.players[Room.INDEX_START];
        check(rocketer.isDie && trex instanceof mobiarmy.war.Boss.ScienceTrex && trex.team == rocketer.team && trex.hpMax == 12_000
                && trex.att == 1500 && trex.glassID == 15 && !trex.isDie && trex.isTurn && trex.isFaction,
                "Rocketer falls: a T-rex on his side, his max HP and attack");
        check(trex.y == 1000 && Math.abs(trex.x - 900) <= 30, "where he fell, on the ground: " + trex.x + "," + trex.y);
        Bullet stomp = new Bullet(new Gun(map, trex.index, false), 35, 1000, chicky.x, chicky.y - 12, 0, 0, 0, 0, 0);
        java.lang.reflect.Method hurts = Bullet.class.getDeclaredMethod("hurts", Player.class);
        hurts.setAccessible(true);
        check(!(boolean) hurts.invoke(stomp, chicky) && (boolean) hurts.invoke(stomp, gunner), "its shots spare its side");
        granos.updateHP(-granos.hp);
        SetTeam.callTrex(map);
        check(map.players[Room.INDEX_START + 1] == null, "the second Scientist calls none");
    }

    static int kicked(MapData map, Player tarzan, Player target, int x, boolean pow) {
        SetPassive.beforeShot(tarzan);
        tarzan.isPow = pow;
        Bullet boomerang = new Bullet(new Gun(map, tarzan.index, pow), 21, 1000, x, target.y - 12, 0, 0, 0, 0, 0);
        boomerang.radius = 30;
        target.hp = target.hpMax;
        for (Player p : map.players) {
            if (p != null && !p.isDie) p.attBullet(boomerang);
        }
        map.updateAffect(tarzan.index);
        SetPassive.afterShot(tarzan);
        tarzan.isPow = false;
        return target.x;
    }

    /** A solid block of ground. */
    static void block(MapData map, int x, int y, int w, int h) {
        int[] solid = new int[w * h];
        java.util.Arrays.fill(solid, 0xff808080);
        map.data.add(map.new Image(0, x, y, solid, w, h, true));
    }

    /** A game connection of the given client build that keeps what the server sends it. */
    static final class Watcher extends mobiarmy.server.Session {
        java.util.List<Integer> commands;
        java.util.List<byte[]> sent;

        Watcher() throws Exception {
            super(null, -1);
        }

        @Override
        public void sendMessage(mobiarmy.io.Message message) {
            this.commands.add((int) message.getCommand());
            this.sent.add(message.getData());
        }

        /** The last message of this command. */
        java.io.DataInputStream read(int command) {
            return new java.io.DataInputStream(new java.io.ByteArrayInputStream(this.sent.get(this.commands.lastIndexOf(command))));
        }
    }

    static Watcher watcher(int build) throws Exception {
        Watcher watcher = (Watcher) unsafe().allocateInstance(Watcher.class);
        watcher.commands = new ArrayList<>();
        watcher.sent = new ArrayList<>();
        watcher.sessionHandler = new mobiarmy.server.SessionHandler(watcher);
        watcher.build = build;
        return watcher;
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
        map.trexTeams = new java.util.HashSet<>();
        map.trexPending = new ArrayList<>();
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
