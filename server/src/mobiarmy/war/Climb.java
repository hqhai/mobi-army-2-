package mobiarmy.war;

/**
 * Tarzan's climb (SetPassive "Khế Ước Rừng Xanh", LAN): with his set passive he walks on any ground, up walls and
 * upside down under overhangs, his feet always on it; he falls only when the ground he holds on to is dug away, and
 * nothing in the way stops him (players never block a walk).
 *
 * <p>A climber stands on an anchor, an air pixel touching ground in one of the 8 directions (Player.x, y; on flat
 * ground the pixel above it, one higher than a walker's feet). He walks pixel by pixel along the ground like a hand on
 * a wall: the right key keeps the ground on his right, the left key on his left, so on a floor they go right and left,
 * up a wall and on under the overhang. The client (player.Climb) walks the same way; the server checks each walk
 * against the steps he has left (reach). Which way his feet point (down) turns his body: the shot leaves from its
 * middle (MapData.shoot) and bullets hit it there (Player.isCollision).
 */
public final class Climb {
    /** E, S, W, N, clockwise on the screen (y grows downward); right of d is d + 1, left of d is d + 3. */
    static final int[][] DIRS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
    public static final int EAST = 0, SOUTH = 1, WEST = 2, NORTH = 3;

    private Climb() {
    }

    /** Ground, or off the sides or the top of the map (he cannot walk out of it). */
    static boolean blocked(MapData map, int x, int y) {
        return x < 0 || x >= map.width || y < 0 || map.isCollisionMap(x, y);
    }

    /** An air pixel with ground in one of the 8 directions around it: he holds on there. */
    public static boolean attached(MapData map, int x, int y) {
        if (map.isCollisionMap(x, y)) return false;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx != 0 || dy != 0) && map.isCollisionMap(x + dx, y + dy)) return true;
            }
        }
        return false;
    }

    /**
     * Which way his feet point standing at this anchor: the ground straight below, right, left or above, in that order,
     * else the ground diagonally below or above; SOUTH when there is none (falling).
     */
    public static int down(MapData map, int x, int y) {
        for (int d : new int[] {SOUTH, EAST, WEST, NORTH}) {
            if (map.isCollisionMap(x + DIRS[d][0], y + DIRS[d][1])) return d;
        }
        if (map.isCollisionMap(x - 1, y + 1) || map.isCollisionMap(x + 1, y + 1)) return SOUTH;
        if (map.isCollisionMap(x - 1, y - 1) || map.isCollisionMap(x + 1, y - 1)) return NORTH;
        return SOUTH;
    }

    /**
     * Where he is after going to the nearest anchor from where he is (x, y): up out of the ground he is in, or down onto
     * the ground he falls on when he holds on to nothing; below the map (height + 200) when nothing catches him.
     */
    public static int[] settle(MapData map, int x, int y) {
        if (map.isCollisionMap(x, y)) {
            while (y > 0 && map.isCollisionMap(x, y)) y--;
            return new int[] {x, y};
        }
        while (y < map.height + 200 && !attached(map, x, y)) y++;
        return new int[] {x, y};
    }

    /**
     * Fewest steps (pixels, 4 ways) from one anchor to another along the ground, if at most limit: -1 when it is farther
     * or cannot be walked to. What the client walks is never shorter, so a walk it allowed is never refused.
     */
    public static int reach(MapData map, int fromX, int fromY, int toX, int toY, int limit) {
        if (fromX == toX && fromY == toY) return 0;
        if (limit <= 0 || Math.abs(toX - fromX) + Math.abs(toY - fromY) > limit || !attached(map, toX, toY)) return -1;
        int size = 2 * limit + 1;
        int[] dist = new int[size * size];
        java.util.Arrays.fill(dist, -1);
        byte[] cell = new byte[size * size]; // 0 unknown, 1 an anchor, 2 not one
        int[] queue = new int[size * size];
        int head = 0, tail = 0;
        int start = limit * size + limit;
        dist[start] = 0;
        queue[tail++] = start;
        while (head < tail) {
            int at = queue[head++];
            int ax = at % size, ay = at / size;
            if (dist[at] >= limit) continue;
            for (int[] d : DIRS) {
                int nx = ax + d[0], ny = ay + d[1];
                if (nx < 0 || ny < 0 || nx >= size || ny >= size) continue;
                int next = ny * size + nx;
                if (dist[next] >= 0) continue;
                int mx = fromX - limit + nx, my = fromY - limit + ny;
                if (cell[next] == 0) cell[next] = (byte) (mx >= 0 && mx < map.width && my >= 0 && attached(map, mx, my) ? 1 : 2);
                if (cell[next] != 1) continue;
                dist[next] = dist[at] + 1;
                if (mx == toX && my == toY) return dist[next];
                queue[tail++] = next;
            }
        }
        return -1;
    }

    /** The middle of his body standing at this anchor with his feet toward down, half his height away from the ground. */
    public static int[] middle(int x, int y, int down, int height) {
        return new int[] {x - DIRS[down][0] * height / 2, y - DIRS[down][1] * height / 2};
    }
}
