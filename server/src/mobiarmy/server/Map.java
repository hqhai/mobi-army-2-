package mobiarmy.server;

import com.google.gson.Gson;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 *
 * @author Văn Tú
 */
public class Map {
    
    public byte id;
    public String name;
    public String file;
    public Data data;
    public short bg;
    public short mapAddY;
    public short cl2AddY;
    public short inWaterAddY;
    public short bullEffShower;
    
    public class Data {
        public short width;
        public short height;
        public Brick bricks[];
        public Point points[];
    }
    
    public class Brick {
        public int id;
        public int x;
        public int y;
    }
    
    public class Point {
        public short x;
        public short y;
    }
    
    public static Map[] entrys;

    /**
     * LAN: every battle map is SCALE times bigger than in the database (wider, taller, ground SCALE times thicker):
     * loadMap multiplies sizes, brick places and start points, and brick() enlarges the brick images (Scale2x when
     * twice as big), for the matches and for the clients alike (they get the bricks' places in message 90 and their
     * images from message 126). Players, bullets and holes keep their size. -Darmy.map.scale=1 plays the original
     * maps, the default again since 25/10/2026 (2 from build 7; 3 times was tried first: too far apart, and its blocky
     * ground looked bad).
     */
    public static final int SCALE = Math.max(1, Integer.getInteger("army.map.scale", 1));
    /** Version of the map list the clients keep (message 90 type 2): a new one when the scale changes. */
    public static final int INFO_VERSION = 12 + SCALE - 1;

    /** A brick image as the game uses it: SCALE times bigger, as pixels (collision) and as PNG (for the clients). */
    public static final class BrickImage {
        public final int[] argb;
        public final int width, height;
        public final byte[] png;

        BrickImage(int[] argb, int width, int height, byte[] png) {
            this.argb = argb;
            this.width = width;
            this.height = height;
            this.png = png;
        }
    }

    private static final java.util.Map<Integer, BrickImage> BRICKS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Brick image id from res/icon/map, enlarged SCALE times: with Scale2x when twice as big (its slopes and curves
     * stay smooth instead of turning into steps), else by repeating pixels. The server collides with exactly the
     * pixels of the PNG the clients get.
     */
    public static BrickImage brick(int id) throws java.io.IOException {
        BrickImage cached = BRICKS.get(id);
        if (cached != null) return cached;
        java.io.File file = new java.io.File("res/icon/map/" + id + ".png");
        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(file);
        int w = img.getWidth(), h = img.getHeight();
        int[] argb = new int[w * h];
        img.getRGB(0, 0, w, h, argb, 0, w);
        byte[] png = null;
        if (SCALE == 1) {
            png = java.nio.file.Files.readAllBytes(file.toPath());
        } else if (SCALE == 2) {
            argb = scale2x(argb, w, h);
            w *= 2;
            h *= 2;
        } else {
            int[] size = new int[2];
            argb = mobiarmy.war.SetPassive.scaleMask(argb, w, h, SCALE * 100, size);
            w = size[0];
            h = size[1];
        }
        if (SCALE != 1) {
            java.awt.image.BufferedImage big = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            big.setRGB(0, 0, w, h, argb, 0, w);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(big, "png", out);
            png = out.toByteArray();
        }
        BrickImage brick = new BrickImage(argb, w, h, png);
        BRICKS.put(id, brick);
        return brick;
    }

    /**
     * Scale2x (AdvMAME2x), the pixel-art enlarger: each pixel becomes 2x2, and a corner takes the colour of its two
     * neighbours when they agree and the other two do not, so diagonals and curves come out smooth and sharp instead
     * of in 2 px steps. Fully transparent pixels count as one colour.
     */
    static int[] scale2x(int[] src, int w, int h) {
        int[] in = new int[src.length];
        for (int i = 0; i < src.length; i++) in[i] = src[i] >>> 24 == 0 ? 0 : src[i];
        int[] out = new int[4 * w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int e = in[y * w + x];
                int b = y > 0 ? in[(y - 1) * w + x] : e;     // above
                int d = x > 0 ? in[y * w + x - 1] : e;       // left
                int f = x < w - 1 ? in[y * w + x + 1] : e;   // right
                int hh = y < h - 1 ? in[(y + 1) * w + x] : e; // below
                int o = 2 * y * 2 * w + 2 * x;
                out[o] = d == b && b != f && d != hh ? d : e;
                out[o + 1] = b == f && b != d && f != hh ? f : e;
                out[o + 2 * w] = d == hh && d != b && hh != f ? d : e;
                out[o + 2 * w + 1] = hh == f && d != hh && b != f ? f : e;
            }
        }
        return out;
    }

    /** Sizes, brick places and start points SCALE times bigger. */
    static void scale(Data data) {
        if (SCALE == 1 || data == null) return;
        data.width = (short) (data.width * SCALE);
        data.height = (short) (data.height * SCALE);
        if (data.bricks != null) {
            for (Brick brick : data.bricks) {
                brick.x *= SCALE;
                brick.y *= SCALE;
            }
        }
        if (data.points != null) {
            for (Point point : data.points) {
                point.x = (short) (point.x * SCALE);
                point.y = (short) (point.y * SCALE);
            }
        }
    }

    public static void loadMap() throws SQLException {
        ArrayList<DBManager.DataRow> rows = Server.dbManager.selectColumnName("SELECT * FROM map");
        entrys = new Map[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            entrys[i] = new Map();
            entrys[i].id = rows.get(i).getByte("id");
            entrys[i].name = rows.get(i).getString("name");
            entrys[i].data = new Gson().fromJson(rows.get(i).getString("data"), Data.class);
            scale(entrys[i].data);
            entrys[i].file = rows.get(i).getString("file");
            entrys[i].bg = rows.get(i).getShort("bg");
            entrys[i].mapAddY = rows.get(i).getShort("mapAddY");
            entrys[i].bullEffShower = rows.get(i).getShort("bullEffShower");
            entrys[i].inWaterAddY = rows.get(i).getShort("inWaterAddY");
            entrys[i].cl2AddY = rows.get(i).getShort("cl2AddY");
        }
    }
    
    public static Map get(int mapID) {
        for (Map entry : entrys) {
            if (entry.id == mapID) {
                return entry;
            }
        }
        return null;
    }
}
