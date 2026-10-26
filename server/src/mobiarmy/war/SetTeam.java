package mobiarmy.war;

import mobiarmy.war.Boss.ScienceTrex;

/**
 * Team passives: a side with at least TEAM_NEED different characters of one group gets its passive, whatever their
 * gear (MapData.startGame). Not in free-for-all rooms (no sides) nor in "Sát thương gốc" rooms.
 *
 * <ul>
 * <li>"Kẻ Ngoại Lai" (OUTSIDER: Chicky, Apache, Magenta): each of their POWs has OUTSIDER_TRUE percent chance to deal
 * true damage (no defense, no luck, no passive taking damage off).
 * <li>"Liên Minh Binh Bét" (PRIVATES: Gunner, Miss 6, Electrician): the whole side takes PRIVATES_ROUND percent less
 * damage a round, up to PRIVATES_MOST, and PRIVATES_LOW percent less below half HP.
 * <li>"Nhà Khoa Học" (SCIENTISTS: Rocketer, Granos): they fire one more bullet, POW too (one more WorldWar bomb, one more
 * laser grenade); the first of them to fall calls a T-rex on the side (Boss.ScienceTrex) with his max HP, attack,
 * defense and luck, which fights like the boss T-rex. The second to fall calls none.
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

    public static final int OUTSIDER_TRUE = 25;
    public static final int PRIVATES_ROUND = 1, PRIVATES_MOST = 30, PRIVATES_LOW = 30;
    public static final int SCIENTIST_EXTRA = 1;

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

    /** Before a shot: an Outsider's POW rolls for true damage. */
    static void beforeShot(Player shooter) {
        shooter.outsiderTrue = shooter.isPow && has(shooter, OUTSIDER) && inGroup(shooter, OUTSIDER)
                && mobiarmy.Util.nextInt(100) < OUTSIDER_TRUE;
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
