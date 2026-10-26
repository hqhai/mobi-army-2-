package map;

import CLib.Image;
import CLib.mGraphics;
import CLib.mImage;
import coreLG.CCanvas;
import effect.Explosion;

import java.util.Hashtable;
import java.util.Vector;

import model.CRes;
import model.IAction2;
import model.TimeBomb;
import network.GameService;
import player.PM;
import screen.GameScr;
import screen.MenuScr;

public class MM {
    public static int mapWidth;
    public static int mapHeight;
    /**
     * LAN: how many times bigger the server's maps are than the game's own (status message 114, server Map.SCALE;
     * 2 from build 7, 1 again since 25/10/2026; the first match of a session used to draw the water line for x2 before
     * the first status came, high enough to hide everyone: setScale redraws it). The map itself comes scaled from the server; this only adjusts what depends on it: the step a
     * walker climbs (CPlayer.climb), and the water and background.
     */
    public static int mapScale = 1;
    public static Vector<CMap> maps = new Vector();
    public static Vector<MapFile> mapFiles = new Vector();
    int index = 0;
    public static boolean isHaveWaterOrGlass;
    public static final byte WATER = 0;
    public static final byte GLASS = 1;
    public static final byte GLASS_2 = 2;
    public static byte curWaterType;
    public static int mapID;
    static Background background;
    public static byte NUM_MAP;
    public static String[] mapName;
    public static String[] mapFileName;
    public static final byte WATERBUM_SMALL_THING = 0;
    public static final byte WATERBUM_NORMAL_THING = 1;
    public static final byte WATERBUM_BIG_THING = 2;
    public static byte[] fullData;
    int count1 = 0;
    int count2 = 0;
    public static Vector<MapImage> mapImages = new Vector();
    public static Vector<Object> vHoleInfo = new Vector();
    public static short[] undestroyTile;

    public void createMap(int mapID) {
        isHaveWaterOrGlass = false;
        CRes.out("=====================> Create map = " + mapID);
        MM.mapID = mapID;
        groundAskedAt = System.currentTimeMillis();
        this.loadMapFile(mapID);
    }

    /** The server's map scale (status 114): a change moves the water line of the map on screen (createBackGround). */
    public static void setScale(int scale) {
        if (scale == mapScale) return;
        mapScale = scale;
        if (GameScr.mm != null && Background.water != null && Background.inWater != null) GameScr.mm.createBackGround();
    }

    public void createBackGround() {
        // LAN: on maps mapScale times bigger the water line sits as high, relatively, as on the original map.
        Background.waterY = mapHeight - mapScale * (Background.water.image.getHeight() + Background.inWater.image.getHeight() - 37);
        CRes.out(this.getClass().getName() + " createBackGround have mapID = " + mapID);
        if (GameScr.curGRAPHIC_LEVEL != 2) {
            for (int i = 0; i < mapFiles.size(); ++i) {
                MapFile mf = (MapFile) mapFiles.elementAt(i);
                if (mapID == mf.mapID) {
                    background = new Background((byte) mf.backGroundID);
                    if (mf.yBackGround != -1) {
                        background.yBackGr = mf.yBackGround;
                    }

                    if (mf.yCloud != -1) {
                        background.yCloud = mf.yCloud - 100;
                    }

                    if (mf.yWater != -1) {
                        Background.waterY += mf.yWater * mapScale;
                    }

                    if (mf.water_class != -1) {
                        curWaterType = (byte) mf.water_class;
                        isHaveWaterOrGlass = true;
                    }
                    break;
                }
            }
        }

    }

    public void clearBackGround() {
        CRes.out("===================================> OnClear BG");
        mapFiles.removeAllElements();
    }

    public byte[] getDataByID(int id) {
        byte[] data = null;

        for (int i = 0; i < mapFiles.size(); ++i) {
            MapFile mf = (MapFile) mapFiles.elementAt(i);
            if (mf.mapID == id) {
                data = mf.data;
            }
        }

        return data;
    }

    public boolean isTileDestroy(int id) {
        for (int i = 0; i < undestroyTile.length; ++i) {
            if (id == undestroyTile[i]) {
                return true;
            }
        }

        return false;
    }

    public void addImage(final int id, byte[] dataRaw, int len) {
        mImage.createImage((byte[]) dataRaw, 0, len, (IAction2) (new IAction2() {
            public void perform(Object object) {
                Image imgTem = (Image) object;

                int sizeLimit;
                for (sizeLimit = 0; sizeLimit < MM.maps.size(); ++sizeLimit) {
                    CMap cmap = (CMap) MM.maps.elementAt(sizeLimit);
                    if (cmap.id == id) {
                        cmap.createRGB(new mImage(imgTem));
                    }
                }

                ++MM.this.count2;
                if (MM.this.count2 == MM.this.count1) {
                    CCanvas.endDlg();
                    if (MenuScr.isTraining) {
                        MenuScr.isTraining = false;
                        GameService.gI().training((byte) 0);
                    }
                }

                MM.mapImages.addElement(new MapImage(new mImage(imgTem), id));
                sizeLimit = GameScr.curGRAPHIC_LEVEL == 2 ? 5 : 30;
                if (MM.mapImages.size() >= sizeLimit) {
                    while (MM.mapImages.size() > sizeLimit) {
                        MM.mapImages.removeElementAt(0);
                    }
                }

                CRes.out("=====================> MapImage " + MM.mapImages.size());
            }
        }));
    }

    public void addImage(int id, mImage image) {
        int sizeLimit;
        for (sizeLimit = 0; sizeLimit < maps.size(); ++sizeLimit) {
            CMap cmap = (CMap) maps.elementAt(sizeLimit);
            if (cmap.id == id) {
                cmap.createRGB(image);
            }
        }

        ++this.count2;
        if (this.count2 == this.count1) {
            CCanvas.endDlg();
            if (MenuScr.isTraining) {
                MenuScr.isTraining = false;
                GameService.gI().training((byte) 0);
            }
        }

        mapImages.addElement(new MapImage(image, id));
        sizeLimit = GameScr.curGRAPHIC_LEVEL == 2 ? 5 : 30;
        if (mapImages.size() >= sizeLimit) {
            while (mapImages.size() > sizeLimit) {
                mapImages.removeElementAt(0);
            }
        }

    }

    public boolean containsImage(int id) {
        for (int i = 0; i < mapImages.size(); ++i) {
            MapImage mI = (MapImage) mapImages.elementAt(i);
            if (mI.id == id) {
                return true;
            }
        }

        return false;
    }

    public mImage getImage(int id) {
        for (int i = 0; i < mapImages.size(); ++i) {
            MapImage mI = (MapImage) mapImages.elementAt(i);
            if (mI.id == id) {
                return mI.image;
            }
        }

        return null;
    }

    public static boolean isExistId(int id) {
        for (int i = 0; i < maps.size(); ++i) {
            CMap cmap = (CMap) maps.elementAt(i);
            if (cmap.aMap != null && cmap.id == id) {
                return true;
            }
        }

        return false;
    }

    /** When the map was made (createMap), and how long its ground is waited for at most (a piece may never come). */
    private static long groundAskedAt;
    static final long GROUND_WAIT = 10000L;

    /**
     * LAN: whether every piece of the map's ground has its picture yet. A piece the client has not seen this session
     * comes from the server (message 126) after the match has begun, above all on the random map, whose map is only
     * known then; until it comes nothing stands on it.
     */
    public static boolean groundReady() {
        if (System.currentTimeMillis() - groundAskedAt > GROUND_WAIT) {
            return true;
        }

        for (int i = 0; i < maps.size(); ++i) {
            if (((CMap) maps.elementAt(i)).aMap == null) {
                return false;
            }
        }

        return true;
    }

    public static int[] rgbMap(int id) {
        for (int i = 0; i < maps.size(); ++i) {
            CMap array = (CMap) maps.elementAt(i);
            if (array.id == id) {
                return array.aMap;
            }
        }

        return null;
    }

    private void loadMapFile(int id) {
        byte[] data = this.getDataByID(id);
        if (data != null) {
            this.count1 = 0;
            this.count2 = 0;
            int seek = 0;
            mapWidth = CRes.getShort(seek, data);
            seek += 2;
            mapHeight = CRes.getShort(seek, data);
            seek += 2;
            int nLand = data[seek];
            int[] landID = new int[nLand];
            ++seek;
            Hashtable h = new Hashtable();
            CMap cmap = null;

            for (int i = 0; i < nLand; ++i) {
                landID[i] = data[seek];
                if (this.containsImage(landID[i])) {
                    cmap = new CMap(landID[i], (short) CRes.getShort(seek + 1, data), (short) CRes.getShort(seek + 3, data), this.getImage(landID[i]), !this.isTileDestroy(landID[i]));
                    cmap.index = i;
                    this.addMap(cmap);
                } else {
                    cmap = new CMap(landID[i], (short) CRes.getShort(seek + 1, data), (short) CRes.getShort(seek + 3, data), (mImage) null, !this.isTileDestroy(landID[i]));
                    cmap.index = i;
                    this.addMap(cmap);
                    if (!h.containsKey(String.valueOf(landID[i]))) {
                        GameService.gI().getMaterialIcon((byte) 2, landID[i], -1);
                    }

                    h.put(String.valueOf(landID[i]), new MapImage((mImage) null, landID[i]));
                }

                seek += 5;
            }

            if (h != null) {
                this.count1 = h.size();
            }

            if (MenuScr.isTraining) {
                MenuScr.isTraining = false;
                GameService.gI().training((byte) 0);
            }

            CRes.out("=====================> MapImage " + mapImages.size());
            if (this.count1 == 0) {
                CCanvas.endDlg();
            }

        }
    }

    public void addMap(CMap m) {
        maps.addElement(m);
    }

    public CMap getMap(int i) {
        return (CMap) maps.elementAt(i);
    }

    public boolean isLand(int x, int y) {
        for (int i = 0; i < maps.size(); ++i) {
            CMap curT = (CMap) maps.elementAt(i);
            if (CRes.inRect(x, y, curT.x, curT.y, curT.width, curT.height) && CRes.isLand(curT.getPixel(x - curT.x, y - curT.y))) {
                return true;
            }
        }

        return false;
    }

    public static boolean checkWaterBum(int x, int y, byte bumType) {
        if (GameScr.curGRAPHIC_LEVEL == 2) {
            if (GameScr.exs.size() > 3) {
                return true;
            }

            if (GameScr.curGRAPHIC_LEVEL == 1 && GameScr.exs.size() > 6) {
                return true;
            }
        }

        if (curWaterType == 0) {
            if (y >= Background.waterY + 48) {
                switch (bumType) {
                    case 0:
                        new Explosion(x, y, (byte) 2);
                        return true;
                    case 1:
                        new Explosion(x, y - 5, (byte) 2);
                        new Explosion(x, y + 6, (byte) 2);
                        return true;
                    case 2:
                        new Explosion(x, y - 10, (byte) 2);
                        new Explosion(x + 12, y, (byte) 2);
                        new Explosion(x - 12, y, (byte) 2);
                        return true;
                }
            }
        } else if (curWaterType == 1) {
            if (y >= Background.glassY) {
                switch (bumType) {
                    case 0:
                        GameScr.sm.addRock(x, y, CRes.random(-4, 4), CRes.random(-11, -9), (byte) 6);
                        return true;
                    case 1:
                        GameScr.sm.addRock(x, y, CRes.random(-4, 4), CRes.random(-14, -12), (byte) 6);
                        GameScr.sm.addRock(x, y, CRes.random(-6, 6), CRes.random(-11, -9), (byte) 6);
                        return true;
                    case 2:
                        GameScr.sm.addRock(x, y, CRes.random(-4, 4), CRes.random(-13, -11), (byte) 6);
                        GameScr.sm.addRock(x, y, CRes.random(-6, 6), CRes.random(-11, -9), (byte) 6);
                        GameScr.sm.addRock(x, y, CRes.random(-2, 2), CRes.random(-17, -15), (byte) 6);
                        return true;
                }
            }
        } else if (curWaterType == 2 && y >= Background.glassY) {
            switch (bumType) {
                case 0:
                    GameScr.sm.addRock(x, y, CRes.random(-4, 4), CRes.random(-11, -9), (byte) 14);
                    return true;
                case 1:
                    GameScr.sm.addRock(x, y, CRes.random(-4, 4), CRes.random(-14, -12), (byte) 14);
                    GameScr.sm.addRock(x, y, CRes.random(-6, 6), CRes.random(-11, -9), (byte) 14);
                    return true;
                case 2:
                    GameScr.sm.addRock(x, y, CRes.random(-4, 4), CRes.random(-13, -11), (byte) 14);
                    GameScr.sm.addRock(x, y, CRes.random(-6, 6), CRes.random(-11, -9), (byte) 14);
                    GameScr.sm.addRock(x, y, CRes.random(-2, 2), CRes.random(-17, -15), (byte) 14);
                    return true;
            }
        }

        return false;
    }

    public void makeHole(int x, int y, byte bulletType, int indexTest) {
        if (bulletType == CMap.holeScaleBullet && CMap.holeScale <= 0) {
            return; // King Kong's POW digs no ground (server SetPassive.holeScale 0)
        }
        int holeW = CMap.getHoleW(bulletType);
        int holeH = CMap.getHoleH(bulletType);

        int i;
        for (i = 0; i < maps.size(); ++i) {
            CMap curT = (CMap) maps.elementAt(i);
            if (curT.isDestroy && CRes.isHit(x - holeW / 2, y - holeH / 2, holeW, holeH, curT.x, curT.y, curT.width, curT.height)) {
                ((CMap) maps.elementAt(i)).makeHole(x, y, bulletType);
            }
        }

        for (i = 0; i < PM.p.length; ++i) {
            if (PM.p[i] != null) {
                if (CRes.inRect(PM.p[i].x, PM.p[i].y, x - holeW / 2, y - holeH / 2, holeW, holeH)) {
                    if (PM.p[i].getState() != 5 && PM.p[i].bulletType != 30) {
                        PM.p[i].activeHurt(x > PM.p[i].x ? 2 : 0);
                    }

                    PM.p[i].isActiveFall = false;
                    PM.p[i].activeFallbyEx = true;
                    PM.p[i].chophepGuiUpdateXY = true;
                }

                if (PM.p[i].gun == 16) {
                    while (!this.isLand(PM.p[i].x, PM.p[i].yPoint)) {
                        ++PM.p[i].yPoint;
                        if (PM.p[i].yPoint > mapHeight) {
                            break;
                        }
                    }
                }
            }
        }

        for (i = 0; i < GameScr.timeBombs.size(); ++i) {
            TimeBomb b = (TimeBomb) GameScr.timeBombs.elementAt(i);
            if (b != null && !b.isFall) {
                b.isFall = true;
            }
        }

    }

    public void update() {
        if (background != null) {
            background.update();
        }

        for (int i = 0; i < maps.size(); ++i) {
            if (maps.elementAt(i) != null) {
                ((CMap) maps.elementAt(i)).update();
            }
        }

    }

    public void paint(mGraphics g) {
        if (!GameScr.cantSee) {
            for (int i = 0; i < maps.size(); ++i) {
                if (maps.elementAt(i) != null) {
                    ((CMap) maps.elementAt(i)).paint(g);
                }
            }

        }
    }

    public void paintBackGround(mGraphics g) {
        if (!GameScr.cantSee) {
            if (background != null) {
                background.paint(g);
            }

        }
    }

    public void paintWater(mGraphics g) {
        if (!GameScr.cantSee) {
            Background.drawWater(curWaterType, g);
        }
    }

    public void onClearMap() {
        maps.removeAllElements();
    }
}
