package mobiarmy.war;

import mobiarmy.server.Equip;
import mobiarmy.server.ThorSkin;
import mobiarmy.server.User;
import mobiarmy.war.Boss.AlienUfo;
import mobiarmy.war.Boss.bullet.ApacheBullet;
import mobiarmy.war.Boss.bullet.Bullet;

/**
 * Character passives unlocked by wearing a full +14 set (gun, hat, armour, glasses, backpack of the same set).
 * The set picks the strength: set 1 gives half, gold the full passive. A costume worn on top does not matter.
 *
 * <p>The numbers come from duel simulations (scripts/sim/DuelSim.java): free rules, level 254, both sides in full
 * gold +14, bots from perfect to sloppy aim. The 10/2026 pass took Granos (holes) and Chicky down from 66-68% on
 * average and Rocketer and King Kong up from 30-35%; with Magenta's UFO and Apache's new hunt every character now
 * averages 44-54% (perfect aim still favours Apache, sloppy aim Granos and King Kong).
 * Passives are off in "Sát thương gốc" rooms, where gear does not count either.
 */
public final class SetPassive {
    /** The upgrade level every piece of the set needs. */
    public static final int SET_PLUS = 14;
    /** Set names as the last word of the gear's name, weakest first; index + 1 is the set tier. */
    public static final String[] SETS = {"1", "2", "3", "4", "bạc", "vàng"};
    /** Passive strength in percent for set tiers 1..6. */
    public static final int[] STRENGTH = {0, 50, 60, 70, 80, 90, 100};

    static final int GUNNER = 0, MISS6 = 1, KINGKONG = 3, ROCKETER = 4, CHICKY = 6, TARZAN = 7, MAGENTA = 9;

    /**
     * Gunner "Súng Thần Công": nobody is lucky on his shots, himself included; his POW fires one giant cannonball
     * that hits GUNNER_POW_DAMAGE percent as hard and digs a GUNNER_CANNON_HOLE percent hole; he gains rage
     * GUNNER_RAGE percent as fast; and each opponent the cannonball kills hits him back for GUNNER_RECOIL percent
     * of the damage it dealt (all at full strength).
     */
    static final int GUNNER_POW_DAMAGE = 150, GUNNER_CANNON_HOLE = 200, GUNNER_RAGE = 150, GUNNER_RECOIL = 20;
    /** Miss 6: every third hit ignores all defense and crits for this multiplier (percent, full strength). */
    static final int MISS6_EVERY = 3, MISS6_CRIT = 150;
    /**
     * Rocketer "WorldWar": his POW fires no rockets but a flare the wind does not move (at any angle: the client lifts
     * his aim limit for it). Where it lands a bomber flies over and drops WORLDWAR_BOMBS nuclear bombs WORLDWAR_SPACING
     * apart across a tight area, each hitting within WORLDWAR_RADIUS (T-Rex's mushroom of fire) with twice his attack
     * like every POW. Radiation then spreads from the blast for up to WORLDWAR_SHOCK percent of that doubled attack,
     * fading to nothing by RADIATION_FAR away; it spares Rocketer himself unless he stood within RADIATION_NEAR of it,
     * and the bombs always spare him. He gains rage WORLDWAR_RAGE percent as fast; his other shots are plain.
     */
    public static final int WORLDWAR_BOMBS = 3, WORLDWAR_SPACING = 30, WORLDWAR_RADIUS = 70, WORLDWAR_SHOCK = 10;
    static final int WORLDWAR_RAGE = 140;
    /** How far the WorldWar radiation reaches (it fades to nothing here), and how close it still catches Rocketer. */
    public static final int RADIATION_FAR = 450, RADIATION_NEAR = 90;
    /**
     * Chicky "Trứng Phục sinh": an opponent hit by both the chicken (bullet 19) and its egg (20) in one shot gets an
     * Easter egg at his feet, and so does the one hit by every CHICKY_EGG_HITS-th egg of hers in a match; she has one
     * Easter egg waiting at a time (MapData.eggWaiting). The egg keeps the damage her chicken and eggs dealt to
     * opponents with the shot that laid it. It hatches after CHICKY_EGG_ROUNDS rounds without digging: Chicky and her
     * whole team heal CHICKY_EGG_HEAL percent of that, and opponents within CHICKY_EGG_RADIUS lose CHICKY_EGG_DAMAGE
     * percent as true damage (full strength).
     */
    static final int CHICKY_EGG_ROUNDS = 2, CHICKY_EGG_RADIUS = 50, CHICKY_EGG_HITS = 5;
    static final int CHICKY_EGG_HEAL = 10, CHICKY_EGG_DAMAGE = 25;
    /**
     * Tarzan "Khế Ước Rừng Xanh": he takes TARZAN_LESS percent less damage; his boomerang ignores the wind, blows up
     * TARZAN_RADIUS percent as wide and hits TARZAN_RETURN percent harder on its way back; he walks TARZAN_STEPS a turn
     * instead of 60 (his aim guide is exact, his boomerang feeling no wind). His pact with King Kong: a King Kong across the
     * battle and he deal each other only half their damage, whatever the set; a King Kong on his side and he deal
     * PACT_DAMAGE percent more and take PACT_LESS percent less while both stand.
     */
    public static final int TARZAN_LESS = 8, TARZAN_RADIUS = 150, TARZAN_RETURN = 20, TARZAN_STEPS = 120;
    static final int PACT_DAMAGE = 20, PACT_LESS = 10;
    /** Steps a turn without Tarzan's passive (Player.theluc). */
    static final int STEPS = 60;
    /** King Kong: each round he stands still takes this much off damage he takes, up to KINGKONG_MAX. */
    static final int KINGKONG_STEP = 10, KINGKONG_MAX = 20;
    /**
     * King Kong once his ground is rock: each further round still his banana digs this much smaller (percent, last
     * value from then on), and it hits KINGKONG_ROCK_DAMAGE percent softer (full strength).
     */
    static final int[] KINGKONG_ROCK_SHRINK = {10, 15, 30};
    static final int KINGKONG_ROCK_DAMAGE = 10;
    /** Electrician: an opponent hit by ELECTRICIAN_STUN_BOLTS of his bolts in one shot loses his next turn. */
    static final int ELECTRICIAN_STUN_BOLTS = 3;
    /** Granos digs wider holes with its own bullets (percent, full strength; was 300, which dropped too many). */
    static final int GRANOS_HOLE = 200;
    static final int ELECTRICIAN = 2, GRANOS = 5;
    /**
     * Apache "Thợ săn ẩn nấp": every APACHE_HIDE_HAMMERS of his hammers that hurt an opponent (counted while he is
     * seen) hide him from opponents (as with the invisibility item) and let him walk twice as far, until he loses HP
     * again; each hammer of his POW that hits an opponent heals him APACHE_HEAL_PERCENT of his max HP (full strength).
     */
    static final int APACHE = 8, APACHE_HIDE_HAMMERS = 4, APACHE_HEAL_PERCENT = 10;
    /** King Kong: after this many rounds stood still the ground under him cannot be dug until he moves. */
    static final int KINGKONG_ROCK_ROUNDS = 2;

    /**
     * Magenta "Người Ngoài Hành Tinh": when her POW's laser hits an opponent, a UFO on her side (Boss.AlienUfo) is
     * called over him, unless one of hers still flies. It has ALIEN_HP percent of her max HP and its laser hits with
     * ALIEN_LASER percent of her attack; it fires on its first turn, then takes turns flying over the nearest opponent
     * and firing, until shot down. Whoever of her opponents shoots it heals ALIEN_SHOT_HEAL percent of the damage dealt
     * to it (the heal does not depend on the set).
     */
    static final int ALIEN_HP = 15, ALIEN_LASER = 50, ALIEN_SHOT_HEAL = 5;
    /** How high over its target the UFO hovers. */
    static final int ALIEN_ALTITUDE = 200;

    private SetPassive() {
    }

    /** Set tier 1..6 of the full +14 set the user wears on this character, or 0. */
    public static int tier(User user, int glassId) {
        int set = -1;
        boolean[] slots = new boolean[5];
        for (Equip equip : user.equips) {
            if (equip.glassID != glassId || !equip.isUse || equip.vip != 0 || ThorSkin.is(equip)) continue;
            if (equip.type < 0 || equip.type > 4 || equip.level2 < SET_PLUS) return 0;
            int piece = setOf(equip);
            if (piece < 0 || (set >= 0 && piece != set)) return 0;
            set = piece;
            slots[equip.type] = true;
        }
        for (boolean slot : slots) if (!slot) return 0;
        return set + 1;
    }

    /** Index in SETS of the set this piece belongs to, or -1 (from the catalog name, e.g. "nón bạc"). */
    public static int setOf(Equip equip) {
        Equip catalog = Equip.entrys == null ? null : Equip.get((byte) equip.glassID, equip.id, equip.type);
        String name = catalog != null && catalog.name != null ? catalog.name : equip.name;
        if (name == null) return -1;
        String last = name.substring(name.lastIndexOf(' ') + 1);
        for (int i = 0; i < SETS.length; i++) if (SETS[i].equals(last)) return i;
        return -1;
    }

    static int scaled(Player player, int full) {
        return full * STRENGTH[player.setTier] / 100;
    }

    /** Starts the shot and hit counts over (practice, when the set changes). */
    public static void resetCounters(Player player) {
        player.passiveShots = 0;
        player.passiveHits = 0;
        player.passiveStill = 0;
    }

    /** Called before a player's shot. */
    public static void beforeShot(Player shooter) {
        for (Player p : shooter.mapData.players) {
            if (p == null) continue;
            p.hitChicken = false;
            p.hitEgg = false;
            p.hitBolts = 0;
            p.hitLaser = false;
        }
        shooter.cannonRecoil = 0;
        shooter.huntHammers.clear();
        shooter.eggsLanded.clear();
        shooter.lastEggTarget = null;
        shooter.dealtBeforeShot = shooter.statDealt;
        if (shooter.setTier == 0) return;
        shooter.passiveShots++;
        shooter.passiveHit = false;
        shooter.passiveTrueShot = shooter.glassID == MISS6 && shooter.passiveHits % MISS6_EVERY == MISS6_EVERY - 1;
    }

    /** Gunner's shots: nobody is lucky, himself included (call before the luck roll). */
    static void noLuck(Player shooter) {
        if (shooter != null && shooter.setTier != 0 && shooter.glassID == GUNNER) shooter.mapData.disableLuck = true;
    }

    /** True while this shot is Gunner's cannonball (his POW with the set passive). */
    static boolean cannon(Player shooter) {
        return shooter != null && shooter.setTier != 0 && shooter.glassID == GUNNER && shooter.isPow;
    }

    /** Rage gained, after Gunner's and Rocketer's faster rage. */
    static int rage(Player player, int add) {
        if (add <= 0 || player.setTier == 0) return add;
        int pace = switch (player.glassID) {
            case GUNNER -> GUNNER_RAGE;
            case ROCKETER -> WORLDWAR_RAGE;
            default -> 100;
        };
        return add * (100 + scaled(player, pace - 100)) / 100;
    }

    /** One bullet of this shot hurt target: what Chicky's egg, Electrician's stun and Magenta's UFO count. */
    static void bulletHit(Player shooter, Player target, int bulletId) {
        if (shooter == null || !enemies(shooter, target)) return;
        if (bulletId == 19) target.hitChicken = true;
        if (bulletId == 20) target.hitEgg = true;
        if (bulletId == 2) target.hitBolts++;
        if (bulletId == 49) target.hitLaser = true;
    }

    /**
     * Called after a player's shot has been applied: Chicky lays her eggs, Electrician stuns, Gunner takes the
     * cannonball's recoil.
     */
    public static void afterShot(Player shooter) {
        if (shooter.setTier != 0 && shooter.passiveHit) shooter.passiveHits++;
        shooter.passiveTrueShot = false;
        if (shooter.headGuideShots > 0) shooter.headGuideShots--;
        if (shooter.setTier == 0) return;
        MapData map = shooter.mapData;
        int dealt = shooter.statDealt - shooter.dealtBeforeShot; // her own shot: what the chicken and its eggs dealt
        java.util.Set<Player> laid = new java.util.HashSet<>();
        for (Player target : map.players) {
            if (target == null || target.isDie || !enemies(shooter, target)) continue;
            if (shooter.glassID == CHICKY && target.hitChicken && target.hitEgg && !map.eggWaiting(shooter) && laid.add(target)) {
                map.layEgg(shooter, target, dealt);
            }
            if (shooter.glassID == ELECTRICIAN && target.hitBolts >= ELECTRICIAN_STUN_BOLTS) target.stunTurns = 1;
        }
        if (shooter.glassID == CHICKY && !shooter.eggsLanded.isEmpty()) {
            // Every CHICKY_EGG_HITS-th of her eggs to hit an opponent in the match lays one too, where it landed.
            // (Not on an opponent who already got one this shot, nor while one of hers waits: the count then waits.)
            shooter.eggHits += shooter.eggsLanded.size();
            if (shooter.eggHits >= CHICKY_EGG_HITS && !map.eggWaiting(shooter) && laid.add(shooter.lastEggTarget)) {
                shooter.eggHits -= CHICKY_EGG_HITS;
                map.layEgg(shooter, shooter.lastEggTarget, dealt);
            }
        }
        shooter.eggsLanded.clear();
        // In a match only (practice has no turns for it), while the map has room for one more entity.
        if (shooter.glassID == MAGENTA && shooter.isPow && !shooter.isDie && map.isWar && map.indexBoss < map.players.length
                && !alienFlying(map, shooter)) {
            for (Player target : map.players) {
                if (target == null || target.isDie || !target.hitLaser || target.typePlayer != 0 || !enemies(shooter, target)) continue;
                int[] spot = alienSpot(map, target);
                AlienUfo ufo = new AlienUfo(shooter, Math.max(1, shooter.hpMax * scaled(shooter, ALIEN_HP) / 100),
                        shooter.att * scaled(shooter, ALIEN_LASER) / 100, spot[0], Math.max(0, spot[1] - 150));
                map.addBoss(ufo);
                // It flies down over him: the clients then show its beam on the spot it will fire at.
                ufo.changeLocationFly(spot[0], spot[1]);
                break;
            }
        }
        if (shooter.cannonRecoil > 0 && !shooter.isDie) {
            shooter.updateHP(-shooter.cannonRecoil);
            map.updateHP(shooter.index, shooter.hp, shooter.pixel);
        }
        shooter.cannonRecoil = 0;
        if (!shooter.huntHammers.isEmpty() && !shooter.isDie) {
            if (shooter.isPow) {
                shooter.updateHP(shooter.hpMax * scaled(shooter, APACHE_HEAL_PERCENT) / 100 * shooter.huntHammers.size());
                map.updateHP(shooter.index, shooter.hp, shooter.pixel);
            }
            if (!shooter.huntHidden) {
                shooter.huntCount += shooter.huntHammers.size();
                if (shooter.huntCount >= APACHE_HIDE_HAMMERS) {
                    // Hidden and running: the status sent right after this shot tells the clients.
                    shooter.huntCount = 0;
                    shooter.huntHidden = true;
                    shooter.huntRun = true;
                }
            }
        }
        shooter.huntHammers.clear();
    }

    /** One of Chicky's eggs (bullet 20) hurt an opponent: each egg counts once toward her CHICKY_EGG_HITS. */
    static void eggHit(Player shooter, Player target, Bullet bullet) {
        if (shooter == null || shooter.setTier == 0 || shooter.glassID != CHICKY || bullet.bulletId != 20 || !enemies(shooter, target)) return;
        shooter.eggsLanded.add(bullet);
        shooter.lastEggTarget = target;
    }

    /**
     * A hammer of Apache's (17, or one of the 18s it splits into, not the puff of the split) hurt an opponent: counted
     * once per hammer.
     */
    static void hammerHit(Player shooter, Player target, Bullet bullet) {
        if (shooter == null || shooter.setTier == 0 || shooter.glassID != APACHE || !enemies(shooter, target)) return;
        if (bullet.bulletId == 18 || (bullet.bulletId == 17 && bullet.att > ApacheBullet.SPLIT_ATT)) shooter.huntHammers.add(bullet);
    }

    /**
     * The player just lost HP: a hidden Apache shows himself again and walks as usual, unless the invisibility item
     * still hides him (Player.updateTurn shows him when it runs out).
     */
    static void hurt(Player player) {
        player.huntRun = false;
        if (!player.huntHidden) return;
        player.huntHidden = false;
        if (player.countInvisible == 0 && player.countInvisible2 == 0) player.mapData.cancelInvisible(player.index);
    }

    /** After a hit: counts it. */
    static void afterHit(Player shooter, Player target, int damage) {
        if (damage <= 0 || !enemies(shooter, target)) return;
        shooter.passiveHit = true;
    }

    /** The shooter killed an opponent, who lost this much HP: Gunner's cannonball hits him back for part of it. */
    static void killed(Player shooter, int lost) {
        if (cannon(shooter)) shooter.cannonRecoil += lost * scaled(shooter, GUNNER_RECOIL) / 100;
    }

    /** Players within the egg's reach of (x, y). */
    static boolean eggReach(Player p, int x, int y) {
        return p != null && !p.isDie && Math.hypot(p.x - x, p.y - y) <= CHICKY_EGG_RADIUS;
    }

    /** Part of the damage an egg kept (CHICKY_EGG_HEAL or CHICKY_EGG_DAMAGE percent) at the owner's strength, at least 1. */
    static int eggShare(Player owner, int dealt, int percent) {
        return Math.max(1, dealt * scaled(owner, percent) / 100);
    }

    /** Rounds King Kong has had rock ground for this turn, 0 when he has none. */
    static int rockRounds(Player p) {
        if (p == null || p.setTier == 0 || p.glassID != KINGKONG) return 0;
        return Math.max(0, p.passiveStill - KINGKONG_ROCK_ROUNDS + 1);
    }

    /**
     * Hidden damage bonus in percent (table lan_hidden_bonus, admin-granted rare sets): every worn slot of this
     * character must be one of the user's rare pieces. Not shown anywhere in the game or on the web.
     */
    public static int hiddenAtt(User user, int glassId) {
        java.util.Map<Integer, Integer> rare = new java.util.HashMap<>();
        try {
            for (var row : mobiarmy.server.Server.dbManager.selectColumnName(
                    "SELECT db_key, att_percent FROM lan_hidden_bonus WHERE user_id = ?", user.id)) {
                rare.put(row.getInt("db_key"), row.getInt("att_percent"));
            }
        } catch (Exception e) {
            return 0;
        }
        if (rare.isEmpty()) return 0;
        boolean[] slots = new boolean[5];
        int percent = Integer.MAX_VALUE;
        for (Equip equip : user.equips) {
            if (equip.glassID != glassId || !equip.isUse || equip.vip != 0 || ThorSkin.is(equip)) continue;
            Integer p = rare.get(equip.dbKey);
            if (p == null || equip.type < 0 || equip.type > 4) return 0;
            slots[equip.type] = true;
            percent = Math.min(percent, p);
        }
        for (boolean slot : slots) if (!slot) return 0;
        return percent;
    }

    /** Damage of one explosion on target after the shooter's passive. */
    static int dealt(Player shooter, Player target, int att) {
        if (shooter != null && shooter.hiddenAtt > 0 && att > 0) att += att * shooter.hiddenAtt / 100;
        if (shooter == null || att <= 0) return att;
        if (pactFoes(shooter, target)) att /= 2;
        Player pact = pactAlly(shooter);
        if (pact != null && enemies(shooter, target)) att += att * scaled(pact, PACT_DAMAGE) / 100;
        if (shooter.setTier == 0) return att;
        int bonus = switch (shooter.glassID) {
            case GUNNER -> shooter.isPow ? GUNNER_POW_DAMAGE - 100 : 0;
            case KINGKONG -> rockRounds(shooter) > 0 ? -KINGKONG_ROCK_DAMAGE : 0;
            default -> 0;
        };
        return att + att * scaled(shooter, bonus) / 100;
    }

    /** Tarzan with his set passive, the one whose pact with King Kong counts. */
    public static boolean pactTarzan(Player p) {
        return p != null && p.setTier != 0 && p.glassID == TARZAN;
    }

    /** A pact Tarzan and a King Kong on opposite sides: they deal each other half. */
    static boolean pactFoes(Player a, Player b) {
        if (a == null || b == null || !enemies(a, b)) return false;
        return (pactTarzan(a) && b.glassID == KINGKONG) || (pactTarzan(b) && a.glassID == KINGKONG);
    }

    /**
     * The pact Tarzan whose pact strengthens p: p himself with a King Kong standing on his side, or the pact Tarzan
     * standing on the side of King Kong p; null when there is none.
     */
    static Player pactAlly(Player p) {
        if (p == null || p.mapData == null || p.mapData.players == null || (p.glassID != TARZAN && p.glassID != KINGKONG)) return null;
        for (Player q : p.mapData.players) {
            if (q == null || q == p || q.isDie || enemies(p, q)) continue;
            if (pactTarzan(p) && q.glassID == KINGKONG) return p;
            if (p.glassID == KINGKONG && pactTarzan(q)) return q;
        }
        return null;
    }

    /** Steps the player may walk a turn (Player.theluc). */
    public static int steps(Player p) {
        return pactTarzan(p) ? TARZAN_STEPS : STEPS;
    }

    /** Share of the wind Tarzan's boomerang feels (usual: 10 percent), none with his passive. */
    public static int boomerangWind(Player shooter, int wind) {
        return pactTarzan(shooter) ? 0 : wind * 10 / 100;
    }

    /** Rocketer's POW with his set passive: the WorldWar flare and its bombers instead of his rockets. */
    public static boolean worldWar(Player shooter, boolean pow) {
        return pow && shooter != null && shooter.setTier != 0 && shooter.glassID == ROCKETER;
    }

    /** What the WorldWar radiation takes at the heart of the blast. */
    public static int worldWarShock(Player shooter, int att) {
        return Math.max(1, att * scaled(shooter, WORLDWAR_SHOCK) / 100);
    }

    /** The WorldWar radiation dist px from the blast: shock fading to 0 at RADIATION_FAR, at least 1 short of it. */
    public static int radiation(int shock, int dist) {
        if (dist >= RADIATION_FAR) return 0;
        return Math.max(1, shock * (RADIATION_FAR - dist) / RADIATION_FAR);
    }


    /** True when this hit from shooter ignores defense and crits (Miss 6's third hit). */
    static boolean trueHit(Player shooter, Player target) {
        return shooter != null && shooter.passiveTrueShot && enemies(shooter, target);
    }

    /** Opponents: different teams, or anyone else in a free-for-all room (everyone is team 3 there). */
    public static boolean enemies(Player a, Player b) {
        return a != b && (a.team != b.team || a.team == 3);
    }

    /** Miss 6's crit multiplier in percent. */
    static int trueHitCrit(Player shooter) {
        return 100 + scaled(shooter, MISS6_CRIT - 100);
    }

    /** Damage the target takes after its own passive. */
    static int taken(Player target, int damage) {
        if (damage <= 0) return damage;
        Player pact = pactAlly(target);
        if (pact != null) damage -= damage * scaled(pact, PACT_LESS) / 100;
        if (target.setTier == 0) return damage;
        int less = switch (target.glassID) {
            case TARZAN -> TARZAN_LESS;
            case KINGKONG -> Math.min(KINGKONG_MAX, KINGKONG_STEP * target.passiveStill);
            default -> 0;
        };
        return damage - damage * scaled(target, less) / 100;
    }

    /** One of Magenta's UFOs still flies (she calls a new one only after it is down). */
    static boolean alienFlying(MapData map, Player owner) {
        for (Player p : map.players) {
            if (p instanceof AlienUfo ufo && !ufo.isDie && ufo.leadIndex == owner.index) return true;
        }
        return false;
    }

    /** Where Magenta's UFO hovers over target: ALIEN_ALTITUDE above him, out of the ground, on the map. */
    public static int[] alienSpot(MapData map, Player target) {
        int x = Math.max(12, Math.min(map.width - 12, target.x));
        int y = Math.max(30, target.y - ALIEN_ALTITUDE);
        if (y > target.y - 40) y = target.y - 40;
        while (y > 0 && map.isCollisionMap(x, y)) y--;
        return new int[] {x, y};
    }

    /** A shot of shooter took lost HP off Magenta's UFO: her opponents heal ALIEN_SHOT_HEAL percent of it. */
    static void alienShot(AlienUfo ufo, Player shooter, int lost) {
        if (lost <= 0 || shooter == null || shooter.isDie || shooter.typePlayer != 0 || !ufo.hurts(shooter)) return;
        shooter.updateHP(Math.max(1, lost * ALIEN_SHOT_HEAL / 100));
        shooter.mapData.updateHP(shooter.index, shooter.hp, shooter.pixel);
        shooter.mapData.updateAngry(shooter.index, shooter.angry);
    }

    /**
     * At the start of the player's own turn: King Kong counts the rounds he is still where he was at the start of
     * his last turn. Walking, teleporting or being knocked away all reset it.
     */
    static void newTurn(Player player) {
        if (player.setTier == 0) return;
        boolean still = player.passiveX == player.x && player.passiveY == player.y;
        player.passiveStill = still ? player.passiveStill + 1 : 0;
        player.passiveX = player.x;
        player.passiveY = player.y;
    }

    /**
     * Hole size in percent for this shooter's bullet: Granos digs wider, Gunner's cannonball too, King Kong on rock
     * ground smaller. The client gets the same number with the shot (SessionHandler.shoot) and scales the hole the
     * same way.
     */
    public static int holeScale(Player shooter, int bulletId) {
        if (shooter == null || shooter.setTier == 0 || bulletId != shooter.bulletIdByGlassID()) return 100;
        int rock = rockRounds(shooter);
        return switch (shooter.glassID) {
            case GUNNER -> shooter.isPow ? 100 + scaled(shooter, GUNNER_CANNON_HOLE - 100) : 100;
            case GRANOS -> 100 + scaled(shooter, GRANOS_HOLE - 100);
            case KINGKONG -> rock > 0 ? 100 - scaled(shooter, KINGKONG_ROCK_SHRINK[Math.min(rock, KINGKONG_ROCK_SHRINK.length) - 1]) : 100;
            default -> 100;
        };
    }

    /**
     * Ground that cannot be dug right now: under every King Kong who has stood still long enough, and under whoever
     * used item 37 "Bê tông cốt thép" this round or the last.
     * Each rectangle is {x, y, width, height} in map pixels; the client gets them with every shot.
     */
    public static java.util.List<short[]> protectedGround(MapData map) {
        java.util.List<short[]> rects = new java.util.ArrayList<>();
        for (Player player : map.players) {
            boolean rock = player != null && player.setTier != 0 && player.glassID == KINGKONG && player.passiveStill >= KINGKONG_ROCK_ROUNDS;
            boolean concrete = player != null && player.concreteTurns > 0; // LAN item 37 "Bê tông cốt thép"
            if (player != null && !player.isDie && (rock || concrete)) {
                rects.add(new short[] {(short) (player.x - 16), (short) (player.y - 4), 32, 24});
            }
        }
        return rects;
    }

    /** Hole mask scaled by percent with nearest-pixel sampling; the client's CMap.scaledMask does exactly this. */
    public static int[] scaleMask(int[] mask, int w, int h, int percent, int[] size) {
        int w2 = Math.max(1, w * percent / 100), h2 = Math.max(1, h * percent / 100);
        int[] out = new int[w2 * h2];
        for (int i = 0; i < h2; i++) {
            for (int j = 0; j < w2; j++) out[i * w2 + j] = mask[(i * h / h2) * w + (j * w / w2)];
        }
        size[0] = w2;
        size[1] = h2;
        return out;
    }
}
