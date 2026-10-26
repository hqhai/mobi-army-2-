import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import mobiarmy.server.Map;
import mobiarmy.war.MapData;
import mobiarmy.war.Player;
import sun.misc.Unsafe;

/**
 * LAN maps x2 (Map.SCALE): every brick image is enlarged with Scale2x (each 2x2 block made of the pixel and its four
 * neighbours, diagonals smoothed), its PNG for the clients decodes to exactly the pixels the server collides with and
 * fits message 126; map sizes, brick places and start points grow as much; and a walker climbs steps Map.SCALE times
 * higher, so slopes stay walkable. Run from server/ (it reads res/icon/map).
 * No database needed.
 */
public class MapScaleCheck {
    static int checks;

    public static void main(String[] args) throws Exception {
        // The default is the original size (1) since 25/10/2026; run this with -Darmy.map.scale=2 to check the x2 maps.
        check(Map.SCALE == 2 && Map.INFO_VERSION == 13, "maps twice as big with -Darmy.map.scale=2; clients get a new map list (13)");
        check(Player.CLIMB == 8, "a walker climbs 8 px steps (4 on the original maps)");

        // Scale2x on a little picture: a diagonal edge comes out smooth, a flat area stays as it was.
        int o = 0xff336699, t = 0;
        int[] stair = {o, t, o, o};               // 2x2: a diagonal of ground
        java.lang.reflect.Method s2x = Map.class.getDeclaredMethod("scale2x", int[].class, int.class, int.class);
        s2x.setAccessible(true);
        int[] big = (int[]) s2x.invoke(null, stair, 2, 2);
        check(big.length == 16 && big[0] == o && big[15] == o && big[5] == o && big[3] == t,
                "Scale2x: 2x2 becomes 4x4, the ground keeps its corners and the empty corner stays empty");
        check(big[6] == o && big[2] == t, "the corner next to the diagonal is filled: a smooth slope instead of a step");

        File[] pngs = new File("res/icon/map").listFiles((dir, name) -> name.matches("\\d+\\.png"));
        int bricks = 0, biggest = 0;
        for (File png : pngs) {
            int id = Integer.parseInt(png.getName().replace(".png", ""));
            java.awt.image.BufferedImage original = ImageIO.read(png);
            Map.BrickImage brick = Map.brick(id);
            int w = original.getWidth(), h = original.getHeight();
            check(brick.width == 2 * w && brick.height == 2 * h, "brick " + id + " is twice as big");
            for (int y = 0; y < brick.height; y++) {
                for (int x = 0; x < brick.width; x++) {
                    // Scale2x: every pixel is its source pixel's colour or one of that pixel's four neighbours'.
                    int sx = x / 2, sy = y / 2, got = brick.argb[y * brick.width + x];
                    boolean ok = false;
                    int[][] around = {{0, 0}, {0, -1}, {-1, 0}, {1, 0}, {0, 1}};
                    for (int[] d : around) {
                        int nx = Math.max(0, Math.min(w - 1, sx + d[0])), ny = Math.max(0, Math.min(h - 1, sy + d[1]));
                        int want = original.getRGB(nx, ny);
                        if ((want >>> 24 == 0 && got >>> 24 == 0) || want == got) ok = true;
                    }
                    if (!ok) throw new AssertionError("brick " + id + " pixel " + x + "," + y + ": " + Integer.toHexString(got) + " is from nowhere");
                }
            }
            java.awt.image.BufferedImage sent = ImageIO.read(new ByteArrayInputStream(brick.png));
            check(sent.getWidth() == brick.width && sent.getHeight() == brick.height, "brick " + id + ": the PNG the clients get has its size");
            for (int y = 0; y < brick.height; y++) {
                for (int x = 0; x < brick.width; x++) {
                    int a = sent.getRGB(x, y), b = brick.argb[y * brick.width + x];
                    if ((a >>> 24 == 0) != (b >>> 24 == 0)) throw new AssertionError("brick " + id + ": the clients' ground differs at " + x + "," + y);
                }
            }
            check(brick.png.length <= 32767, "brick " + id + " fits message 126 (" + brick.png.length + " bytes)");
            check(Map.brick(id) == brick, "brick " + id + " is made once");
            biggest = Math.max(biggest, brick.png.length);
            bricks++;
        }
        check(bricks > 100, bricks + " bricks checked, the biggest PNG " + biggest + " bytes");

        // Sizes, brick places and start points of a map.
        Map map = new Map();
        Map.Data data = map.new Data();
        data.width = 800;
        data.height = 600;
        Map.Brick brick = map.new Brick();
        brick.id = 4;
        brick.x = -1;
        brick.y = 420;
        data.bricks = new Map.Brick[] {brick};
        Map.Point point = map.new Point();
        point.x = 150;
        point.y = 382;
        data.points = new Map.Point[] {point};
        java.lang.reflect.Method scale = Map.class.getDeclaredMethod("scale", Map.Data.class);
        scale.setAccessible(true);
        scale.invoke(null, data);
        check(data.width == 1600 && data.height == 1200 && brick.x == -2 && brick.y == 840 && point.x == 300 && point.y == 764,
                "a map 800x600 becomes 1600x1200 with its bricks and start points");

        // A slope rising 2 px a column on the original map: 4 px steps every 2 px now (left as steps here), still walkable.
        MapData land = (MapData) unsafe().allocateInstance(MapData.class);
        land.width = 3000;
        land.height = 2000;
        land.players = new Player[8];
        land.data = new ArrayList<>();
        land.tornados = new ArrayList<>();
        land.protectedGround = new ArrayList<>();
        land.timeBombs = new ArrayList<>();
        for (int k = 0; k < 20; k++) {
            int[] solid = new int[2 * (400 + 4 * k)];
            java.util.Arrays.fill(solid, 0xff808080);
            land.data.add(land.new Image(0, 100 + 2 * k, 1000 - 4 * k, solid, 2, 400 + 4 * k, true));
        }
        int[] floor = new int[100 * 400];
        java.util.Arrays.fill(floor, 0xff808080);
        land.data.add(land.new Image(0, 0, 1000, floor, 100, 400, true));
        Player walker = new Player(land, null, "w", 3, 1, 1000, 100, 0, 0, 0, 24, 24, null);
        walker.index = 0;
        walker.x = 90;
        walker.y = 1000;
        land.players[0] = walker;
        walker.updateXY(130, walker.y);
        check(walker.x == 130 && walker.y == 1000 - 4 * 15, "a walker goes up the scaled slope: " + walker.x + "," + walker.y);
        System.out.println("MAP SCALE PASSED: " + checks + " checks");
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
