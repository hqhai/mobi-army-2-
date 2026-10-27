package mobiarmy.war;

import mobiarmy.war.Boss.ScienceTrex;

/**
 * Team passives: a side with at least TEAM_NEED different characters of one group gets its passive, whatever their
 * gear (MapData.startGame), under the "Tự do" and "Cân bằng" rules; not under "Sát thương gốc", nor in boss rooms
 * (everyone on side 3: no sides).
 *
 * <ul>
 * <li>"Kẻ Ngoại Lai" (OUTSIDER: Chicky, Apache, Magenta): their POWs go through OUTSIDER_PIERCE percent of the
 * opponents' defense (until 26/10/2026 a 25 percent chance of true damage).
 * <li>"Liên Minh Binh Bét" (PRIVATES: Gunner, Miss 6, Electrician): the whole side takes PRIVATES_ROUND percent less
 * damage a round, up to PRIVATES_MOST, and PRIVATES_LOW percent less below half HP.
 * <li>"Nhà Khoa Học" (SCIENTISTS: Rocketer, Granos): the first of them to fall calls a T-rex on the side
 * (Boss.ScienceTrex) with SCIENTIST_TREX_HP percent of his max HP, SCIENTIST_TREX_ATT percent of his attack, his
 * defense and luck, which fights like the boss T-rex. The second to fall calls none. They fire SCIENTIST_EXTRA more
 * bullets, POW too (one more rocket,
 * WorldWar bomb, laser grenade each until 26/10/2026, when the pair won 80% of the duo duels: now none).
 * </ul>
 */
public final class SetTeam {
    /** Different characters of a group a side needs. */
    public static final int TEAM_NEED = 2;
    public static final int OUTSIDER = 1, PRIVATES = 2, SCIENTISTS = 4;
    static final int[][] GROUPS = {
            {SetPassive.CHICKY, SetPassive.APACHE, SetPassive.MAGENTA},
            {SetPassive.GUNNER, SetPassive.MISS6, SetPassive.ELECTRICIAN},
            {SetPassive.ROCKETER, SetPassive.GRANOS}};
    static final int[] FLAGS = {OUTSIDER, PRIVATES, SCIENTISTS};
    public static final String[] NAMES = {"Kẻ Ngoại Lai", "Liên Minh Binh Bét", "Nhà Khoa Học"};

    /**
     * An Outsider's POW goes through OUTSIDER_PIERCE percent of his opponents' defense. (True damage, every POW or
     * every other, or half the defense put the group at 62-64% of the 2v2 duels on 26/10/2026, 49% without it.)
     */
    public static final int OUTSIDER_PIERCE = 25;
    public static final int PRIVATES_ROUND = 1, PRIVATES_MOST = 10, PRIVATES_LOW = 10;
    public static final int SCIENTIST_EXTRA = 0;
    /**
     * The Scientists' T-rex has these percents of the fallen Scientist's max HP and attack (100 and 100 until 26/10/2026,
     * when the pair won 80% of the 2v2 duels; 50 and 100, 50 and 60 still 61-62%, 47% without the T-rex).
     */
    public static final int SCIENTIST_TREX_HP = 30, SCIENTIST_TREX_ATT = 60;

    private SetTeam() {
    }

    /** Gives each player the passives of his side (Player.teamPacts). */
    public static void apply(MapData map, boolean base) {
        for (Player p : map.players) {
            if (p == null || p.typePlayer != 0) continue;
            p.teamPacts = 0;
            if (base || p.team == 3) continue;
            for (int g = 0; g < GROUPS.length; g++) {
                java.util.Set<Integer> kinds = new java.util.HashSet<>();
                for (Player q : map.players) {
                    if (q == null || q.typePlayer != 0 || q.team != p.team) continue;
                    for (int glass : GROUPS[g]) if (q.glassID == glass) kinds.add(glass);
                }
                if (kinds.size() >= TEAM_NEED) p.teamPacts |= FLAGS[g];
            }
        }
    }

    static boolean has(Player p, int flag) {
        return p != null && (p.teamPacts & flag) != 0;
    }

    static boolean inGroup(Player p, int flag) {
        for (int g = 0; g < FLAGS.length; g++) {
            if (FLAGS[g] != flag) continue;
            for (int glass : GROUPS[g]) if (p.glassID == glass) return true;
        }
        return false;
    }

    /** Before a shot: an Outsider's POW pierces his opponents' defense (pierced). */
    static void beforeShot(Player shooter) {
        shooter.outsiderPierce = shooter.isPow && has(shooter, OUTSIDER) && inGroup(shooter, OUTSIDER);
    }

    /** The defense target puts up against the shooter's shot: OUTSIDER_PIERCE percent less against an Outsider's POW. */
    static int pierced(Player shooter, Player target, int def) {
        return shooter != null && shooter.outsiderPierce && SetPassive.enemies(shooter, target) ? def * (100 - OUTSIDER_PIERCE) / 100 : def;
    }

    /** One more bullet for a Scientist's shots (Gun: Rocketer's rockets, Granos's grenades). */
    public static int extraBullets(Player shooter) {
        return shooter != null && has(shooter, SCIENTISTS) && inGroup(shooter, SCIENTISTS) ? SCIENTIST_EXTRA : 0;
    }

    /** Damage a Private's side takes, after the round's and the low HP's cut. */
    static int taken(Player target, int damage) {
        if (!has(target, PRIVATES) || damage <= 0) return damage;
        int round = Math.min(PRIVATES_MOST, PRIVATES_ROUND * Math.max(0, target.mapData.round));
        damage -= damage * round / 100;
        if (target.hp * 2 < target.hpMax) damage -= damage * PRIVATES_LOW / 100;
        return damage;
    }

    /** A player fell: the first Scientist of a side to fall calls its T-rex (MapData.update, callTrex). */
    static void died(Player p) {
        if (p.mapData == null || !p.mapData.isWar || p.typePlayer != 0 || !has(p, SCIENTISTS) || !inGroup(p, SCIENTISTS)) return;
        if (p.mapData.trexTeams.add(p.team)) p.mapData.trexPending.add(p);
    }

    /** Calls the T-rexes of the Scientists who fell since the last call, where they fell. */
    public static void callTrex(MapData map) {
        while (!map.trexPending.isEmpty()) {
            Player scientist = map.trexPending.remove(0);
            if (map.indexBoss >= map.players.length) continue;
            int x = Math.max(30, Math.min(map.width - 30, scientist.x));
            int y = scientist.y > map.height ? 0 : Math.max(0, scientist.y - 10);
            while (y < map.height && !map.isCollisionMap(x, y)) y++;
            if (y >= map.height) {
                // He fell off the map: by a teammate still standing.
                Player mate = null;
                for (Player q : map.players) {
                    if (q != null && !q.isDie && q.team == scientist.team && q.typePlayer == 0) mate = q;
                }
                if (mate == null) continue;
                x = mate.x;
                y = mate.y;
            }
            ScienceTrex trex = new ScienceTrex(scientist, x, y);
            map.addBoss(trex);
            trex.chuanHoaXY();
        }
    }
}
