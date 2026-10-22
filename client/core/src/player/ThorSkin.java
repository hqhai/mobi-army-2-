package player;

import CLib.Image;
import CLib.mGraphics;
import CLib.mImage;
import Equipment.Equip;
import Equipment.PlayerEquip;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import model.FrameImage;

/** Purchased Apache costume; identity matches the server catalog. */
public final class ThorSkin {
    public static final short ID = 30000;
    private static boolean attempted;
    private static mImage[] poses;
    private static FrameImage hammer;
    private static FrameImage powHammer;
    private static Pixmap rotationSource;
    private static mImage icon;
    private static mImage[] walkLegs;
    private static ShaderProgram steelShader;

    private ThorSkin() {}

    private static boolean load() {
        if (attempted) return poses != null;
        attempted = true;
        Pixmap source = null;
        try {
            source = new Pixmap(Gdx.files.internal("skins/apache-thor/concept.png"));
            mImage[] loaded = new mImage[] {
                new mImage(crop(source, 108, 20, 342, 445, 26, 34)),
                new mImage(crop(source, 560, 45, 408, 420, 33, 34)),
                new mImage(crop(source, 998, 45, 440, 420, 36, 34))
            };
            // Use the same rotating-frame implementation as the original Apache projectile.
            hammer = new FrameImage(crop(source, 1095, 674, 282, 143, 20, 20), 8);
            rotationSource.dispose();
            rotationSource = null;
            powHammer = new FrameImage(crop(source, 1095, 674, 282, 143, 40, 40), 8);
            rotationSource.dispose();
            rotationSource = null;
            icon = new mImage(crop(source, 108, 20, 342, 445, 12, 16));
            loadWalk();
            poses = loaded;
            return true;
        } catch (RuntimeException error) {
            Gdx.app.error("ThorSkin", "Prototype unavailable; using original Apache assets", error);
            return false;
        } finally {
            // FrameImage reads the source texture's CPU pixels while building rotations.
            if (rotationSource != null) { rotationSource.dispose(); rotationSource = null; }
            if (source != null) source.dispose();
        }
    }

    private static void loadWalk() {
        // Apache Iron Man armor (equipment 23), bottom five rows of movement frames 4/5.
        // Keep the original distinct silhouettes; recolor only at draw time.
        Pixmap atlas = new Pixmap(Gdx.files.internal("skins/apache-thor/apache-equipment-source.png"));
        try {
            walkLegs = new mImage[] {
                new mImage(crop(atlas, 11, 79, 11, 5, 18, 9)),
                new mImage(crop(atlas, 102, 70, 7, 5, 12, 9))
            };
        } finally { atlas.dispose(); }
        steelShader = new ShaderProgram(
                "attribute vec4 a_position; attribute vec4 a_color; attribute vec2 a_texCoord0;\n"
                + "uniform mat4 u_projTrans; varying vec4 v_color; varying vec2 v_texCoords;\n"
                + "void main(){v_color=a_color;v_color.a*=255.0/254.0;"
                + "v_texCoords=a_texCoord0;gl_Position=u_projTrans*a_position;}",
                "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
                + "varying vec4 v_color; varying vec2 v_texCoords; uniform sampler2D u_texture;\n"
                + "void main(){vec4 c=texture2D(u_texture,v_texCoords);"
                + "float l=dot(c.rgb,vec3(0.299,0.587,0.114));"
                + "gl_FragColor=vec4(l*0.80,l*0.88,l,c.a)*v_color;}");
        if (!steelShader.isCompiled()) throw new IllegalStateException(steelShader.getLog());
    }

    private static Image crop(Pixmap source, int x, int y, int w, int h, int width, int height) {
        Pixmap pixels = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixels.setBlending(Pixmap.Blending.None);
        pixels.setFilter(Pixmap.Filter.NearestNeighbour);
        int drawH = Math.min(height, Math.round((float) h * width / w));
        pixels.drawPixmap(source, x, y, w, h, 0, width == height ? (height - drawH) / 2 : height - drawH, width, drawH);
        // Concept lighting is not part of the silhouette.
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                int rgba = pixels.getPixel(px, py);
                if ((rgba & 255) < 160) pixels.drawPixel(px, py, 0);
            }
        }
        Image image = new Image();
        image.texture = new Texture(pixels);
        image.texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        image.width = width;
        image.height = height;
        if (width == height) rotationSource = pixels;
        else pixels.dispose();
        return image;
    }

    public static boolean is(Equip equip) {
        return equip != null && equip.glass == 8 && equip.type == 2 && equip.id == ID;
    }

    public static boolean isEquipped(PlayerEquip equip) {
        return equip != null && is(equip.equips[2]);
    }

    public static boolean isShooter(int shooter) {
        return PM.p != null && shooter >= 0 && shooter < PM.p.length && PM.p[shooter] != null
                && isEquipped(PM.p[shooter].equip);
    }

    public static boolean paint(mGraphics g, PlayerEquip equip, int look, int frame, int x, int y) {
        if (!isEquipped(equip) || !load()) return false;
        if (frame == 4 || frame == 5) {
            paintWalk(g, look, frame, x, y);
            return true;
        }
        int pose = frame >= 1 && frame <= 3 ? 1 : frame >= 8 ? 2 : 0;
        mImage image = poses[pose];
        g.drawRegion(image, 0, 0, image.image.width, image.image.height, look == 0 ? 2 : 0,
                x, y, mGraphics.BOTTOM | mGraphics.HCENTER, false);
        return true;
    }

    private static void part(mGraphics g, int look, int sx, int sy, int w, int h, int dx, int dy, int x, int y) {
        int left = look == 0 ? 26 - sx - w - dx : sx + dx;
        g.drawRegion(poses[0], sx, sy, w, h, look == 0 ? 2 : 0, x - 13 + left,
                y - 34 + sy + dy, mGraphics.TOP | mGraphics.LEFT, false);
    }

    private static void paintWalk(mGraphics g, int look, int frame, int x, int y) {
        int bob = frame == 4 ? -1 : 0; // Same phase as Iron Man's head/armor offsets.
        part(g, look, 0, 25, 7, 9, 0, 0, x, y); // cape edge
        mImage legs = walkLegs[frame - 4];
        ShaderProgram previous = g.g.getShader();
        try {
            g.g.setShader(steelShader);
            // Original atlas faces left, unlike the right-facing Thor concept.
            g.drawRegion(legs, 0, 0, legs.image.width, legs.image.height, look,
                    x + (look == 0 ? -3 : 3), y, mGraphics.BOTTOM | mGraphics.HCENTER, false);
        } finally { g.g.setShader(previous); }
        // Keep the hand-held hammer complete and cover the hip seam with the torso.
        part(g, look, 10, 24, 8, 8, 0, bob, x, y);
        part(g, look, 0, 0, 26, 27, 0, bob, x, y);
    }

    public static boolean paintIcon(mGraphics g, Equip equip, int x, int y, boolean clip) {
        if (!is(equip) || !load()) return false;
        g.drawRegion(icon, 0, 0, 12, 16, 0, x + 2, y, mGraphics.TOP | mGraphics.LEFT, clip);
        return true;
    }

    public static FrameImage projectile(FrameImage original, int shooter) {
        return PM.p != null && shooter >= 0 && shooter < PM.p.length && PM.p[shooter] != null
                && isEquipped(PM.p[shooter].equip) && load() ? hammer : original;
    }

    public static FrameImage projectile(FrameImage original, int shooter, boolean pow) {
        return isShooter(shooter) && load() ? (pow ? powHammer : hammer) : original;
    }
}
