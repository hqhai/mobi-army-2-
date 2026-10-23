package mobiarmy.war;

import mobiarmy.server.Equip;
import mobiarmy.server.ThorSkin;
import mobiarmy.server.User;

/**
 * Character passives unlocked by wearing a full +14 set (gun, hat, armour, glasses, backpack of the same set).
 * The set picks the strength: set 1 gives half, gold the full passive. A costume worn on top does not matter.
 *
 * <p>The numbers come from a duel simulation (both sides in full gold +14): without passives the low-attack
 * characters won 25-33% and Apache/King Kong 74-83%; with them every character lands around 44-60%.
 * Passives are off in "Sát thương gốc" rooms, where gear does not count either.
 */
public final class SetPassive {
    /** The upgrade level every piece of the set needs. */
    public static final int SET_PLUS = 14;
    /** Set names as the last word of the gear's name, weakest first; index + 1 is the set tier. */
    static final String[] SETS = {"1", "2", "3", "4", "bạc", "vàng"};
    /** Passive strength in percent for set tiers 1..6. */
    static final int[] STRENGTH = {0, 50, 60, 70, 80, 90, 100};

    static final int GUNNER = 0, MISS6 = 1, KINGKONG = 3, ROCKETER = 4, CHICKY = 6, TARZAN = 7, MAGENTA = 9;

    /**
     * Gunner "Súng Thần Công": nobody is lucky on his shots, himself included; his POW fires one giant cannonball
     * that hits GUNNER_POW_DAMAGE percent as hard and digs a GUNNER_CANNON_HOLE percent hole; he gains rage
     * GUNNER_RAGE percent as fast; and each opponent the cannonball kills hits him back for GUNNER_RECOIL percent
     * of the damage it dealt (all at full strength).
     */
    static final int GUNNER_POW_DAMAGE = 150, GUNNER_CANNON_HOLE = 200, GUNNER_RAGE = 120, GUNNER_RECOIL = 20;
    /** Miss 6: every third hit ignores all defense and crits for this multiplier (percent, full strength). */
    static final int MISS6_EVERY = 3, MISS6_CRIT = 150;
    static final int ROCKETER_DAMAGE = 25;
    /**
     * Chicky "Trứng Phục sinh": an opponent hit by both the chicken (bullet 19) and its egg (20) in one shot gets an
     * egg at his feet. It hatches after CHICKY_EGG_ROUNDS rounds: opponents within CHICKY_EGG_RADIUS take
     * CHICKY_EGG_PERCENT of their max HP as true damage and the egg digs a CHICKY_EGG_HOLE percent hole; Chicky's
     * team within reach heals that much instead, and then no hole is dug.
     */
    static final int CHICKY_EGG_ROUNDS = 2, CHICKY_EGG_RADIUS = 50, CHICKY_EGG_PERCENT = 2, CHICKY_EGG_HOLE = 200;
    static final int TARZAN_LESS = 12;
    /** King Kong: each round he stands still takes this much off damage he takes, up to KINGKONG_MAX. */
    static final int KINGKONG_STEP = 10, KINGKONG_MAX = 20;
    /**
     * King Kong once his ground is rock: each further round still his banana digs this much smaller (percent, last
     * value from then on), and it hits KINGKONG_ROCK_DAMAGE percent softer (full strength).
     */
    static final int[] KINGKONG_ROCK_SHRINK = {10, 15, 30};
    static final int KINGKONG_ROCK_DAMAGE = 30;
    /** Electrician: an opponent hit by ELECTRICIAN_STUN_BOLTS of his bolts in one shot loses his next turn. */
    static final int ELECTRICIAN_STUN_BOLTS = 3;
    /** Granos digs three times as wide holes with its own bullets (percent, full strength). */
    static final int GRANOS_HOLE = 300;
    static final int ELECTRICIAN = 2, GRANOS = 5;
    /** King Kong: after this many rounds stood still the ground under him cannot be dug until he moves. */
    static final int KINGKONG_ROCK_ROUNDS = 2;

    /** Magenta: how much of the wind her laser no longer feels, in percent (full strength: none at all). */
    static final int MAGENTA_WIND_IGNORED = 100;

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
    static int setOf(Equip equip) {
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
        }
        shooter.cannonRecoil = 0;
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

    /** Rage gained, after Gunner's faster rage. */
    static int rage(Player player, int add) {
        if (add <= 0 || player.setTier == 0 || player.glassID != GUNNER) return add;
        return add * (100 + scaled(player, GUNNER_RAGE - 100)) / 100;
    }

    /** One bullet of this shot hurt target: what Chicky's egg and Electrician's stun count. */
    static void bulletHit(Player shooter, Player target, int bulletId) {
        if (shooter == null || !enemies(shooter, target)) return;
        if (bulletId == 19) target.hitChicken = true;
        if (bulletId == 20) target.hitEgg = true;
        if (bulletId == 2) target.hitBolts++;
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
        for (Player target : map.players) {
            if (target == null || target.isDie || !enemies(shooter, target)) continue;
            if (shooter.glassID == CHICKY && target.hitChicken && target.hitEgg) map.layEgg(shooter, target);
            if (shooter.glassID == ELECTRICIAN && target.hitBolts >= ELECTRICIAN_STUN_BOLTS) target.stunTurns = 1;
        }
        if (shooter.cannonRecoil > 0 && !shooter.isDie) {
            shooter.updateHP(-shooter.cannonRecoil);
            map.updateHP(shooter.index, shooter.hp, shooter.pixel);
        }
        shooter.cannonRecoil = 0;
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

    /** Egg damage or heal for p: CHICKY_EGG_PERCENT of max HP at the owner's strength, at least 1. */
    static int eggAmount(Player owner, Player p) {
        return Math.max(1, p.hpMax * scaled(owner, CHICKY_EGG_PERCENT * 100) / 10000);
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
        if (shooter == null || shooter.setTier == 0 || att <= 0) return att;
        int bonus = switch (shooter.glassID) {
            case GUNNER -> shooter.isPow ? GUNNER_POW_DAMAGE - 100 : 0;
            case ROCKETER -> ROCKETER_DAMAGE;
            case KINGKONG -> rockRounds(shooter) > 0 ? -KINGKONG_ROCK_DAMAGE : 0;
            default -> 0;
        };
        return att + att * scaled(shooter, bonus) / 100;
    }


    /** True when this hit from shooter ignores defense and crits (Miss 6's third hit). */
    static boolean trueHit(Player shooter, Player target) {
        return shooter != null && shooter.passiveTrueShot && enemies(shooter, target);
    }

    /** Opponents: different teams, or anyone else in a free-for-all room (everyone is team 3 there). */
    static boolean enemies(Player a, Player b) {
        return a != b && (a.team != b.team || a.team == 3);
    }

    /** Miss 6's crit multiplier in percent. */
    static int trueHitCrit(Player shooter) {
        return 100 + scaled(shooter, MISS6_CRIT - 100);
    }

    /** Damage the target takes after its own passive. */
    static int taken(Player target, int damage) {
        if (target.setTier == 0 || damage <= 0) return damage;
        int less = switch (target.glassID) {
            case TARZAN -> TARZAN_LESS;
            case KINGKONG -> Math.min(KINGKONG_MAX, KINGKONG_STEP * target.passiveStill);
            default -> 0;
        };
        return damage - damage * scaled(target, less) / 100;
    }

    /** Share of the wind (percent) the shooter's shots feel, out of usual (100 = the whole wind). */
    public static int windShare(Player shooter, int usual) {
        if (shooter == null || shooter.setTier == 0 || shooter.glassID != MAGENTA) return usual;
        return usual * (100 - scaled(shooter, MAGENTA_WIND_IGNORED)) / 100;
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
