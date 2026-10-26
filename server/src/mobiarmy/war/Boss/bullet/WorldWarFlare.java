package mobiarmy.war.Boss.bullet;

import mobiarmy.war.Gun;
import mobiarmy.war.MapData;
import mobiarmy.war.Player;
import mobiarmy.war.SetPassive;

/**
 * Rocketer's POW with his set passive "WorldWar" (SetPassive): a flare the wind does not move, like the B52 item's.
 * Where it lands a bomber flies over the map and drops SetPassive.WORLDWAR_BOMBS nuclear bombs (BOMB_ID) across the
 * area, left to right in the order the bomber passes them, and radiation spreads from its spot (Gun.worldWarShock).
 *
 * <p>Every bomb lands on ground: one over a gap lands on the nearest ground toward the flare, the row moves in from
 * the edges of the map, and a bomb over an overhang lands on top of it. Their paths are worked out in advance in
 * small steps, as the shot message sends each step in a byte.
 */
public class WorldWarFlare extends Bullet {

    /** The flare and its bombs (client build 5 plays them; the shot goes out as bullet ID). */
    public static final int ID = 60, BOMB_ID = 61;
    /** Height the bomber flies above the flare, as the B52's; how far left of its spot a bomb starts falling. */
    static final int ALTITUDE = 320, LEAD = 140;
    /** Largest step of a bomb's path (the message sends each step in a byte). */
    static final int MAX_STEP = 50;

    private boolean dropped;

    public WorldWarFlare(Gun gun, int att, int x, int y, int vx, int vy) {
        super(gun, ID, att, x, y, vx, vy, 0, 0, 80);
        super.isCanCollision = false;
    }

    @Override
    public void nextXY() {
        super.nextXY();
        if (!super.collect || this.dropped) return;
        this.dropped = true;
        MapData map = super.gun.mapData;
        if (super.bX < 0 || super.bX > map.width || super.bY > map.height) return; // lost off the map: no bomber
        int bombs = SetPassive.WORLDWAR_BOMBS + mobiarmy.war.SetTeam.extraBullets(super.gun.shooter()), spacing = SetPassive.WORLDWAR_SPACING;
        int half = bombs / 2 * spacing + 10;
        int center = half * 2 < map.width ? Math.max(half, Math.min(map.width - half, super.bX)) : map.width / 2;
        int altitude = super.bY - ALTITUDE;
        java.util.List<int[]> spots = new java.util.ArrayList<>();
        for (int i = 0; i < bombs; i++) {
            int x = center + (i - bombs / 2) * spacing;
            int y = free(spots, x) ? surface(map, x, altitude) : -1;
            for (int step = 10; y < 0 && step <= 2 * spacing; step += 10) {
                // Over a gap (or where another bomb lands): the nearest free ground, toward the flare first.
                for (int side : x < super.bX ? new int[] {step, -step} : new int[] {-step, step}) {
                    int other = x + side;
                    if (other < 0 || other > map.width || !free(spots, other)) continue;
                    int found = surface(map, other, altitude);
                    if (found >= 0) {
                        x = other;
                        y = found;
                        break;
                    }
                }
            }
            for (int k = 0; y < 0; k++) {
                // No ground near: beside the flare, where it is free.
                int other = super.bX + (k % 2 == 0 ? 1 : -1) * 20 * ((k + 1) / 2);
                if (k > 40 || (other >= 0 && other <= map.width && free(spots, other))) {
                    x = k > 40 ? super.bX : other;
                    int found = surface(map, x, altitude);
                    y = found >= 0 ? found : super.bY;
                }
            }
            spots.add(new int[] {x, y});
        }
        spots.sort((a, b) -> Integer.compare(a[0], b[0])); // the order the bomber passes them
        for (int[] spot : spots) {
            int top = Math.min(altitude, spot[1] - 60);
            Bomb bomb = new Bomb(super.gun, super.att, path(spot[0] - LEAD, top, spot[0], spot[1]));
            // Each bomb may lie where it lands and go off a round later (SetPassive.WORLDWAR_LATE percent).
            if (mobiarmy.Util.nextInt(100) < SetPassive.worldWarLate) bomb.lateId = map.nextLateNuke();
            super.gun.bullets.add(bomb);
        }
        super.gun.worldWarShock = SetPassive.worldWarShock(super.gun.shooter(), super.att);
        super.gun.worldWarX = super.bX;
        super.gun.worldWarY = super.bY;
    }

    /** No other bomb lands within 20 of x. */
    static boolean free(java.util.List<int[]> spots, int x) {
        for (int[] spot : spots) {
            if (Math.abs(spot[0] - x) < 20) return false;
        }
        return true;
    }

    /** Where a bomb falling at x from y lands: the top of the ground it meets (of the rock it starts in), -1 if none. */
    static int surface(MapData map, int x, int y) {
        int yy = Math.max(0, y);
        if (map.isCollisionMap(x, yy)) {
            while (yy > 0 && map.isCollisionMap(x, yy - 1)) yy--;
            return yy;
        }
        for (; yy < map.height; yy++) {
            if (map.isCollisionMap(x, yy)) return yy;
        }
        return -1;
    }

    /** A fall from (x0, y0) to (x1, y1), gathering speed, in steps of at most MAX_STEP. */
    static int[][] path(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int n = Math.max(8, Math.max(dx, 2 * dy) / MAX_STEP + 1);
        int[][] points = new int[n + 1][];
        for (int i = 0; i <= n; i++) {
            points[i] = new int[] {x0 + (x1 - x0) * i / n, y0 + (int) ((long) (y1 - y0) * i * i / ((long) n * n))};
        }
        return points;
    }

    /**
     * One nuclear bomb: follows its path a point a frame and goes off where it ends, or, late (lateId, a time bomb id
     * of MapData.nextLateNuke, -1 for the others), lies there as a time bomb that goes off a round later
     * (MapData.layLateNuke); the shot tells the clients which (SessionHandler.shoot).
     */
    public static class Bomb extends Bullet {
        private final int[][] path;
        private int step;
        public int lateId = -1;

        Bomb(Gun gun, int att, int[][] path) {
            super(gun, BOMB_ID, att, path[0][0], path[0][1], 0, 0, 0, 0, 0);
            super.isCanCollisionMap = false;
            super.isCanCollisionPlayer = false;
            this.path = path;
        }

        /** The bombs spare the Rocketer who called them; only the radiation can catch him, up close. */
        @Override
        protected boolean hurts(Player player) {
            return player != super.gun.shooter();
        }

        @Override
        public void nextXY() {
            super.frame++;
            super.frames.add(new Bullet.Frame(super.frame, super.bX, super.bY, super.bX - super.xOld, super.bY - super.yOld));
            if (this.step + 1 >= this.path.length) {
                super.collect = true;
                if (this.lateId >= 0) {
                    super.gun.mapData.layLateNuke(this.lateId, super.gun.index, super.att, super.bX, super.bY);
                } else if (super.isCanCollision) {
                    super.collision();
                }
                return;
            }
            super.xOld = super.bX;
            super.yOld = super.bY;
            this.step++;
            super.lastX = super.bX = this.path[this.step][0];
            super.lastY = super.bY = this.path[this.step][1];
        }
    }
}
