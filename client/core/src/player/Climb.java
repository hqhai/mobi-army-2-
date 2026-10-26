package player;

import screen.GameScr;

/**
 * Tarzan's climb with his set passive (LAN build 8, the server's war.Climb does the same): he walks on any ground, up
 * walls and upside down, standing on an anchor, an air pixel next to the ground (on flat ground the pixel above it).
 * He walks like a hand on a wall: the right key keeps the ground on his right, the left key on his left; he falls
 * only when no ground is left around him.
 */
public final class Climb {
    /** E, S, W, N, clockwise on the screen (y grows downward); right of d is d + 1, left of d is d + 3. */
    static final int[][] DIRS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
    public static final int EAST = 0, SOUTH = 1, WEST = 2, NORTH = 3;

    private Climb() {
    }

    static boolean land(int x, int y) {
        return GameScr.mm != null && GameScr.mm.isLand(x, y);
    }

    /** Ground, or off the sides or the top of the map (he cannot walk out of it). */
    static boolean blocked(int x, int y) {
        return x < 0 || x >= map.MM.mapWidth || y < 0 || land(x, y);
    }

    /** An air pixel with ground in one of the 8 directions around it: he holds on there. */
    public static boolean attached(int x, int y) {
        if (land(x, y)) return false;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx != 0 || dy != 0) && land(x + dx, y + dy)) return true;
            }
        }
        return false;
    }

    /** Which way his feet point at this anchor: ground below, right, left or above, else diagonally below or above. */
    public static int down(int x, int y) {
        int[] order = {SOUTH, EAST, WEST, NORTH};
        for (int d : order) {
            if (land(x + DIRS[d][0], y + DIRS[d][1])) return d;
        }
        if (land(x - 1, y + 1) || land(x + 1, y + 1)) return SOUTH;
        if (land(x - 1, y - 1) || land(x + 1, y - 1)) return NORTH;
        return SOUTH;
    }

    /** How far his body is turned for drawing, in degrees clockwise: feet down 0, on a wall to his right -90... */
    public static int turn(int down) {
        switch (down) {
            case EAST:
                return -90;
            case WEST:
                return 90;
            case NORTH:
                return 180;
            default:
                return 0;
        }
    }

    /**
     * One pixel's walk from p (p[0], p[1]; p[2] his heading, -1 when he has none yet) with the ground on his right
     * (rightKey) or left: p is changed; false when there is nowhere to go.
     */
    public static boolean step(int[] p, boolean rightKey) {
        int x = p[0], y = p[1];
        int h = p[2];
        int side = rightKey ? 1 : 3, away = rightKey ? 3 : 1;
        if (h < 0) h = (down(x, y) + away) % 4; // starting off: ground on the chosen hand
        for (int turns = 0; turns < 4; turns++) {
            int s = (h + side) % 4;
            int sx = x + DIRS[s][0], sy = y + DIRS[s][1];
            if (!blocked(sx, sy) && attached(sx, sy)) { // round a corner, toward the ground
                p[0] = sx;
                p[1] = sy;
                p[2] = s;
                return true;
            }
            int fx = x + DIRS[h][0], fy = y + DIRS[h][1];
            if (!blocked(fx, fy) && attached(fx, fy)) {
                p[0] = fx;
                p[1] = fy;
                p[2] = h;
                return true;
            }
            h = (h + away) % 4; // a wall ahead: turn away from it and go on along it
        }
        return false;
    }
}
