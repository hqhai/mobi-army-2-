package player;

import CLib.mGraphics;
import CLib.mImage;
import CLib.mSound;
import Equipment.PlayerEquip;
import coreLG.CCanvas;
import effect.Camera;
import effect.Explosion;
import effect.Smoke;
import item.BM;
import item.Bullet;
import item.Item;
import map.Background;
import map.MM;
import model.CRes;
import static model.CRes.angle;
import static model.CRes.cos;
import static model.CRes.fixangle;
import static model.CRes.sin;
import model.FilePack;
import model.Font;
import model.FrameImage;
import model.Position;
import network.GameService;
import network.Session_ME;
import screen.CScreen;
import screen.GameScr;
import screen.PrepareScr;
import shop.ShopItem;

public class CPlayer {
    public static final boolean TEAM_GREEN = true;
    public static final boolean TEAM_YELLOW = false;
    public boolean team;
    boolean isWaterBum;
    public static final byte NUM_GUN = 10;
    public static final byte GUN_CANNON = 0;
    public static final byte GUN_AK = 1;
    public static final byte GUN_PROTON = 2;
    public static final byte GUN_CHUOI = 3;
    public static final byte GUN_ROCKET = 4;
    public static final byte GUN_MORTAR = 5;
    public static final byte GUN_CHICKEN = 6;
    public static final byte GUN_BOOMERANG = 7;
    public static final byte GUN_HAMMER = 8;
    public static final byte GUN_PINGPONG = 10;
    public static final byte GUN_LASER_GIRL = 9;
    public static final byte GUN_BOMB_SMALL = 11;
    public static final byte GUN_BOMB_BIG = 12;
    public static final byte GUN_BUG_ROBOT = 13;
    public static final byte GUN_ROBOT = 14;
    public static final byte GUN_BIG_ROBOT = 15;
    public static final byte GUN_UFO = 16;
    public static final byte GUN_BOSS_KHICAU = 17;
    public static final byte GUN_BOSS_GUNKHICAU = 18;
    public static final byte GUN_BOSS_BOMBKHICAU = 19;
    public static final byte GUN_FAN1 = 20;
    public static final byte GUN_BOSS_EYEKHICAU = 21;
    public static final byte GUN_BOSS_SPIDER = 22;
    public static final byte GIFT_1 = 23;
    public static final byte GIFT_2 = 24;
    public static final byte GHOST = 25;
    public static final byte GHOST_2 = 26;
    static mImage crosshair;
    public static boolean isShooting;
    public boolean shootFrame;
    public static boolean isStopFall;
    public static boolean isGetPosition = false;
    public boolean isFreeze;
    public static mImage[] pImg = new mImage[25];
    public boolean isPaint = true;
    public boolean isFly = false;
    public boolean isBum = false;
    public static FilePack filePack;
    public static byte[] fileData;
    public boolean notPaintNormal;
    public boolean flyPlayer;
    public boolean earthwakeActive;
    public boolean cantSee;
    public int xBug;
    public static mImage robotArm;
    public static mImage robotLeg;
    public static mImage robotBody;
    public static mImage robotInjured;
    public static mImage khicau;
    public static mImage gunkhicau;
    public static mImage bomb;
    public static mImage fan1;
    public static mImage fan2;
    public static mImage bombKhiCau;
    public static mImage mainGun;
    public static mImage eye;
    public static mImage back_fan;
    public static mImage front_fan;
    public static mImage injured;
    public static mImage diamond;
    public static mImage diamond2;
    public static mImage ghost;
    public static mImage ghost2;
    public static mImage fire;
    public static mImage imgUFO;
    public static mImage imgUFOFire;
    public static mImage spider;
    public static mImage web;
    private byte critical;
    public int idBullet;
    public boolean ghostBit;
    public mImage clanIcon;
    private int crossHairW;
    private int crossHairH;
    private int crossHairX;
    private int crossHairY;
    FrameImage pFrameImg;
    public boolean isJump;
    int framebd_1 = 0;
    public int frameleg_1 = 0;
    public int x;
    public int y;
    public static final int pW = 24;
    public static final int pH = 24;
    public boolean falling;
    public int[] item;
    public int itemUsed = -1;
    public int CurSelectedItem = 0;
    public boolean isUsedItem;
    public boolean is2TurnItem;
    public int lastForcePoint = 0;
    public int lastForcePoint_2 = 0;
    public int movePoint = 0;
    public int IDDB;
    byte index;
    public byte gun = 0;
    public boolean isAllowSendPosAfterShoot;
    public boolean isActiveFall;
    public boolean activeFallbyEx;
    public boolean chophepGuiUpdateXY;
    public boolean isSecondPower = false;
    public boolean isDoublePower = false;
    public byte force = 0;
    public byte force_2 = 0;
    public int maxforce = 30;
    public int maxforce2 = 30;
    int radius = 30;
    public int angle = 0;
    int speedChangeAngle = 0;
    public int dx;
    public int dy;
    int curFrame = 5;
    int frameDelay;
    public int look = 2;
    int hurtLook;
    public static final int LEFT = 0;
    public static final int RIGHT = 2;
    public static final byte PSTATE_STAND = 0;
    public static final byte PSTATE_MOVE = 1;
    public static final byte PSTATE_AIM = 2;
    public static final byte PSTATE_READYSHOOT = 3;
    static final byte PSTATE_SHOOT = 4;
    public static final byte PSTATE_DIE = 5;
    public static final byte PSTATE_WIN = 7;
    public static final byte PSTATE_HURT = 8;
    public static final byte PSTATE_CAPTURE = 9;
    protected byte state;
    int fspider;
    public static final int SLEFT = 0;
    public static final int SRIGHT = 1;
    public static final int SUP = 2;
    public static final int SDOWN = 3;
    public int sLook;
    public static final int MAX_MOVE_POINT = 60;
    /** LAN: tier of this player's set passive (message 114), 0 without one. */
    public int setTier;

    /** Tarzan with his set passive "Khế Ước Rừng Xanh" (server SetPassive): no wind, so his aim guide is exact; longer walk. */
    public boolean pactTarzan() {
        return this.gun == 7 && this.setTier > 0;
    }

    /** Tarzan with his set passive climbs any ground (Climb, build 8): walls, and upside down under overhangs. */
    public boolean climber() {
        return this.pactTarzan() && !(this instanceof Boss);
    }

    /** How far to turn his drawing: by the ground he holds on to, while he climbs and is alive. */
    public int climbTurn() {
        return this.climber() && this.state != 5 && !this.falling ? Climb.turn(Climb.down(this.x, this.y)) : 0;
    }

    /** The middle of his body, where his shots leave from: 11 px over his feet, turned with a climber. */
    public int[] aimCentre() {
        int down = this.climber() && !this.falling ? Climb.down(this.x, this.y) : Climb.SOUTH;
        if (down == Climb.SOUTH) return new int[] {this.x, this.y - Math.round(11 * this.grownScale())};
        return new int[] {this.x - Climb.DIRS[down][0] * 12, this.y - Climb.DIRS[down][1] * 12};
    }

    /** A climber's way along the ground (Climb.step), -1 before his first step, and whether it keeps it on his right. */
    private int climbHeading = -1;
    private boolean climbRight;

    /**
     * One step of a climber (move): along the ground, runRange pixels, his way kept between steps so he goes round
     * bumps instead of back and forth; false when he cannot go on that way.
     */
    private boolean climbStep(int directMove, int runRange) {
        boolean right = directMove == 2;
        if (this.climbHeading >= 0 && right != this.climbRight) this.climbHeading = (this.climbHeading + 2) % 4; // turned round
        this.climbRight = right;
        int[] p = {this.x, this.y, this.climbHeading};
        boolean moved = false;
        for (int i = 0; i < runRange; i++) {
            if (!Climb.step(p, right)) break;
            moved = true;
        }
        if (!moved) return false;
        this.x = p[0];
        this.y = p[1];
        this.climbHeading = p[2];
        this.nexty = this.y;
        this.falling = false;
        return true;
    }

    /** Rocketer's POW with his set passive "WorldWar": a wind-free flare at any angle (server WorldWarFlare). */
    public boolean worldWarPow() {
        return this.gun == 4 && this.setTier > 0 && this.itemUsed == 100;
    }

    /** Granos's POW with his set passive (build 7): the seed of his lasers, aimed at any angle too (server laserSower). */
    public boolean laserSeedPow() {
        return this.gun == 5 && this.setTier > 0 && this.itemUsed == 100;
    }

    /**
     * Lowest aim of this character (angleLock), or straight down for Rocketer's WorldWar POW and Granos's seed; no
     * lower than SHOCK_RAISE degrees above it while Electrician's shock slows him (the server holds him to it too).
     */
    public int angleLimit() {
        // (Tarzan climbing off the floor aims all round: his body is turned, build 8.)
        boolean turned = this.climber() && !this.falling && Climb.down(this.x, this.y) != Climb.SOUTH;
        int limit = this.worldWarPow() || this.laserSeedPow() || turned ? -90 : angleLock[this.gun];
        return this.paralyzed == 1 && limit > -90 ? Math.min(90, limit + SHOCK_RAISE) : limit;
    }

    /** Steps a turn: 60, twice as many for Tarzan with his set passive; Electrician's shock sets fewer (Player.theluc). */
    public int maxMovePoint() {
        if (this.paralyzed > 0) return this.paralyzedSteps;
        return this.pactTarzan() ? 120 : MAX_MOVE_POINT;
    }

    /**
     * Electrician's shock this turn (status message 114, build 7): 2 locked, no walking and no new angle (frozenAngle,
     * the angle he had when it began); 1 slowed, fewer steps and aim SHOCK_RAISE degrees higher at the lowest; 0 none.
     */
    public int paralyzed, paralyzedSteps, frozenAngle;
    /** Server build 8: paralyzed by the ice of a +15 set (SetElement): no walking, any angle ("Cóng"). */
    public static final int FROZEN = 3;
    /**
     * The +15 set elements on him (status 114): 1 Thiêu (burning: loses HP at his turns, gains rage slower), 2 Độc
     * (heals less, his items work less), from build 14 4 Cóng (his shots have less force).
     */
    public int elementFlags;
    /** Build 14: the element of his own +15 set (1 Lôi, 2 Hỏa, 3 Băng, 4 Độc; 0 none) and its charge, 100: his next shot carries it. */
    public int elementOwn, elementCharge;
    /** Build 14: percent of force his shots lose this turn, chilled by the ice of a +15 set (the guide follows it). */
    public int chillNow;
    public static final String[] ELEMENT_NAMES = {"", "Lôi", "Hỏa", "Băng", "Độc"};
    public static final int[] ELEMENT_COLORS = {0xffffff, 0xffe040, 0xff6a30, 0x7fd8ff, 0xcc66ff};
    public static final com.badlogic.gdx.graphics.Color[] ELEMENT_TINTS = new com.badlogic.gdx.graphics.Color[ELEMENT_COLORS.length];
    static {
        for (int i = 0; i < ELEMENT_COLORS.length; i++) ELEMENT_TINTS[i] = new com.badlogic.gdx.graphics.Color(ELEMENT_COLORS[i] << 8 | 0xff);
    }
    /** Electrician's shock (and the lightning of a +15 set) raises the lowest aim by this many degrees (build 11; 30 until build 14). */
    public static final int SHOCK_RAISE = 20;
    public boolean active;
    boolean isSendM_autoDie;
    public int hp;
    public int maxhp;
    String hpText;
    String expText;
    String cupText;
    public byte bulletType;
    byte nShoot;
    public byte currAngry;
    public byte angryX;
    public boolean isAngry;
    public boolean isInvisible;
    public boolean isVampire;
    public boolean isRunSpeed;
    /** Apache's set passive: hidden by his hunt, he walks twice as far (status message 114, server build 6). */
    public boolean huntRun;
    /** King Kong's set passive: how big he has grown standing still, in percent (status message 114, build 7). */
    public int bodyPercent = 100;

    /**
     * LAN: turns of this player left before his Teleport works again (status message 114, build 7): it is never used
     * up, but waits 3 of his turns between uses. Practice has no wait.
     */
    public int teleportCooldown, concreteCooldown;

    public boolean itemCooling(int itemID) {
        if (GameScr.trainingMode) return false;
        return (itemID == Item.ITEM_TELEPORT && this.teleportCooldown > 0) || (itemID == Item.CONCRETE && this.concreteCooldown > 0);
    }

    /**
     * How high a step a walker climbs: 4 px on the original maps, MM.mapScale times that on the bigger LAN maps (the
     * server's Player.CLIMB); a higher one is a wall.
     */
    public static int climb() {
        return 4 * MM.mapScale;
    }

    /** How much bigger to draw him: King Kong grown standing still, alive; everyone else as usual. */
    public float grownScale() {
        return this.gun == GUN_CHUOI && this.state != PSTATE_DIE ? this.bodyPercent / 100f : 1f;
    }

    /** How far his grown head rises above the usual 32 px (the HP bar and name move up as much). */
    public int grownLift() {
        return Math.round(32 * (this.grownScale() - 1f));
    }
    public boolean isStopWind;
    public boolean isPoison;
    public boolean poisonEff;
    public int tPEff;
    public boolean isCom;
    short lastx;
    public int lastUpdateX;
    public int lastUpdateY;
    public String name;
    int vy;
    int g;
    int nextx;
    int nexty;
    boolean isMove;
    public static mImage lua;
    public static short[] angleLock;
    public static short[] angleLockMain;
    public static mImage buggun;
    public static mImage bugbody;
    public static mImage bugleg;
    PlayerEquip equip;
    int smokeDelayWhenDie;
    boolean isCapture;
    byte whoCapture;
    boolean capUp;
    boolean capDown;
    int capX;
    int capY;
    int playerHit;
    int xTo;
    int yTo;
    int xFrom;
    int yFrom;
    boolean flyActive;
    int xa;
    int ya;
    boolean isPointActive;
    public int yPoint;
    boolean outMap;
    int va;
    int vx;
    int tMove;
    public int xToNow;
    public int yToNow;
    int timeCount;
    int delay;
    int delayCount;
    boolean isDelay;
    public boolean isExplore;
    public int xBugBack;
    public int yBugBack;
    public int bombIndex;
    public short[][] _x;
    public short[][] _y;
    public static int xSuper;
    public static int ySuper;
    public static boolean isStopFire;
    public boolean isPaintCountDown;
    public static int xM;
    public static int yM;
    public static int frameM;
    public int frameC;
    public static boolean isMirror;
    public static boolean closeMirror;
    public static int tCl;
    public int ta;
    public int fa;
    public int yT;
    public int hpRectW;
    boolean hpChangeVisible;
    boolean addExp;
    boolean addCup;
    int exp;
    int cup;
    int hpChangeAmount;
    int dyhp;
    int dyExp;
    int dyCup;
    public int nQuanHam;
    boolean hpTang;
    byte runSpeed;
    public static int deltaY;
    public static int tB;
    static int deltaX;
    public static int tBalloon;
    public static int deltaBalloon;
    boolean isHoldFire;
    boolean isHoldAngle;
    int t;
    long timeHold;

    static {
        lua = Smoke.lua;
        angleLock = null;
        angleLockMain = null;
    }

    public static void init() {
        try {
            filePack = new FilePack(fileData);
            crosshair = GameScr.crosshair;
            pImg[0] = filePack.loadImage("yellowP.png");
            pImg[1] = filePack.loadImage("cuteGirl.png");
            pImg[2] = filePack.loadImage("greenP.png");
            pImg[3] = filePack.loadImage("conKhiP.png");
            pImg[4] = filePack.loadImage("rocketer.png");
            pImg[5] = filePack.loadImage("robot.png");
            pImg[6] = filePack.loadImage("ga.png");
            pImg[7] = filePack.loadImage("tazz.png");
            pImg[8] = filePack.loadImage("apache.png");
            pImg[9] = filePack.loadImage("cowg.png");
            pImg[10] = filePack.loadImage("magenta.png");
            pImg[11] = filePack.loadImage("bosssmall.png");
            pImg[12] = filePack.loadImage("bossbig.png");
            buggun = filePack.loadImage("bug_gun.png");
            bugbody = filePack.loadImage("bug_body.png");
            bugleg = filePack.loadImage("bug_leg.png");
            fire = filePack.loadImage("fire.png");
            robotArm = filePack.loadImage("arm.png");
            robotLeg = filePack.loadImage("leg.png");
            robotBody = filePack.loadImage("body.png");
            robotInjured = filePack.loadImage("body_injured.png");
            pImg[14] = filePack.loadImage("bossrobot.png");
            imgUFO = filePack.loadImage("UFO.png");
            imgUFOFire = filePack.loadImage("UFOFire.png");
            khicau = filePack.loadImage("khicau.png");
            gunkhicau = filePack.loadImage("gun.png");
            bomb = filePack.loadImage("bomb.png");
            fan1 = filePack.loadImage("fan1.png");
            fan2 = filePack.loadImage("fan2.png");
            bombKhiCau = filePack.loadImage("gunbig.png");
            mainGun = filePack.loadImage("mainGun.png");
            eye = filePack.loadImage("eye.png");
            ghost = filePack.loadImage("ma.png");
            ghost2 = filePack.loadImage("ma2.png");
            back_fan = filePack.loadImage("back_fan.png");
            front_fan = filePack.loadImage("front_fan.png");
            injured = filePack.loadImage("injured.png");
            spider = filePack.loadImage("spider.png");
            diamond = filePack.loadImage("diamond.png");
            pImg[18] = filePack.loadImage("mainGun.png");
            pImg[21] = eye;
            pImg[13] = buggun;
            pImg[15] = robotArm;
        } catch (Exception var1) {
            var1.printStackTrace();
        }

        filePack = null;
    }

    public CPlayer(int IDDB, byte index, int X, int Y, boolean isCom, int look, byte gunType, PlayerEquip equip, int maxHP) {
        this.hurtLook = this.look;
        this.state = 0;
        this.sLook = 1;
        this.active = false;
        this.isSendM_autoDie = false;
        this.hpText = "";
        this.expText = "";
        this.cupText = "";
        this.bulletType = 0;
        this.vy = 0;
        this.g = 1;
        this.isMove = false;
        this.smokeDelayWhenDie = 100;
        this.isCapture = false;
        this.whoCapture = -1;
        this.flyActive = false;
        this.outMap = false;
        this.timeCount = 0;
        this.delay = 1;
        this.delayCount = 0;
        this.isDelay = false;
        this.isExplore = true;
        this.hpRectW = 25;
        this.hpChangeVisible = false;
        this.addExp = false;
        this.addCup = false;
        this.hpChangeAmount = 0;
        this.runSpeed = 1;
        this.IDDB = IDDB;
        this.index = index;
        this.isCom = isCom;
        this.look = look;
        this.gun = gunType;
        this.equip = equip;
        this.isMove = false;
        if (gunType >= 11) {
            this.angle = 0;
        } else {
            this.angle = look == 2 ? (angleLock[this.gun] < 0 ? 0 : angleLock[this.gun]) : 180 - angleLock[this.gun];
        }

        this.team = this.index % 2 == 0;
        if (equip != null) {
            this.idBullet = equip.equips[0].bullet;
        }

        if (this.gun == 3) {
            this.pFrameImg = new FrameImage(pImg[this.gun].image, 30, 32);
        } else if (this.gun == 6) {
            this.pFrameImg = new FrameImage(pImg[this.gun].image, 29, 24);
        } else if (this.gun == 7) {
            this.pFrameImg = new FrameImage(pImg[this.gun].image, 32, 32);
        } else if (this.gun == 11) {
            this.pFrameImg = new FrameImage(pImg[this.gun].image, 18, 19);
        } else if (this.gun == 12) {
            this.pFrameImg = new FrameImage(pImg[this.gun].image, 30, 28);
        } else if (this.gun == 14) {
            this.pFrameImg = new FrameImage(pImg[this.gun].image, 24, 30);
        } else if (this.gun == 13) {
            this.pFrameImg = new FrameImage(pImg[this.gun].image, 15, 15);
        } else if (this.gun == 15) {
            this.pFrameImg = new FrameImage(pImg[this.gun].image, 35, 40);
        } else if (this.gun == 9) {
            this.pFrameImg = new FrameImage(pImg[this.gun].image, 24, 24);
        } else if (this.gun == 10) {
            this.pFrameImg = new FrameImage(pImg[this.gun].image, 32, 32);
        } else if (this.gun != 16) {
            if (this.gun == 18) {
                this.pFrameImg = new FrameImage(pImg[this.gun].image, 21, 20);
            } else if (this.gun != 17 && this.gun != 19) {
                if (this.gun == 21) {
                    this.pFrameImg = new FrameImage(pImg[this.gun].image, 12, 12);
                } else if (this.gun != 20 && this.gun != 22 && this.gun != 23 && this.gun != 24 && this.gun != 25 && this.gun != 26) {
                    this.pFrameImg = new FrameImage(pImg[this.gun].image, 24, 24);
                }
            }
        }

        this.falling = true;
        this.activeFallbyEx = false;
        this.chophepGuiUpdateXY = true;
        this.x = X;
        this.y = Y;
        xM = this.x;
        yM = this.y - 50;
        this.capX = X;
        this.capY = Y;
        this.lastUpdateX = X;
        this.lastUpdateY = Y;
        this.nextx = this.x;
        this.nexty = this.y;
        this.lastx = (short) this.x;
        this.itemUsed = -1;
        this.item = new int[PrepareScr.numCurItemSlot];
        this.maxhp = maxHP;
        this.hp = this.maxhp;
        this.isPoison = false;
        this.crossHairH = crosshair.image.getHeight();
        this.crossHairW = crosshair.image.getWidth();
    }

    public void capture(byte whoCapture) {
        this.isCapture = true;
        this.whoCapture = whoCapture;
        this.capDown = true;
        this.capX = this.x;
        this.capY = this.y;
    }

    public void ghostHit(int player) {
        this.ghostBit = true;
        this.playerHit = player;
    }

    public void capturePlayer() {
        if (this.isCapture) {
            this.falling = false;
            this.sLook = 3;
            if (this.capDown) {
                this.y += 6;
                if (this.y >= PM.p[this.whoCapture].y - 24) {
                    this.y = PM.p[this.whoCapture].y - 24;
                    this.capDown = false;
                    this.capUp = true;
                }
            }

            if (this.capUp) {
                this.y -= 6;
                PM.p[this.whoCapture].y = this.y + 24;
                PM.p[this.whoCapture].nexty = this.y + 24;
                if (this.y <= this.capY) {
                    this.y = this.capY;
                    this.nexty = this.capY;
                    this.capUp = false;
                    this.isCapture = false;
                    CCanvas.lockNotify = true;
                }
            }
        }

    }

    public boolean checkMapDeep(int deep, int x, int y) {
        int startPointY = y;

        for (int i = 0; i < deep; ++i) {
            if (!GameScr.mm.isLand(x, startPointY)) {
                return false;
            }

            ++startPointY;
        }

        return true;
    }

    public boolean isBestLocation(int x, int y, int width, int height) {
        int startPointX = x - width / 2;

        for (int i = 0; i < width; ++i) {
            if (!this.checkMapDeep(height, startPointX, y)) {
                return false;
            }

            ++startPointX;
        }

        return true;
    }

    public boolean isChangeLocation() {
        return !this.checkMapDeep(60, this.x, this.y);
    }

    public Position move(int angle, int dis) {
        int xa = dis * CRes.cos(CRes.fixangle(angle)) >> 10;
        int ya = -(dis * CRes.sin(CRes.fixangle(angle))) >> 10;
        return new Position(xa, ya);
    }

    public void setState(byte state) {
        if (this.state != 5) {
            this.state = state;
        }
    }

    public byte getState() {
        return this.state;
    }

    /** Tarzan's kick (message 127, LAN build 7): KICK_TICKS ticks in an arc from where he stood to where he lands. */
    private static final int KICK_TICKS = 28, KICK_PEAK = 40;
    private int kickTick = -1, kickFromX, kickFromY, kickToX, kickToY;

    public void kickTo(int xT, int yT) {
        this.kickFromX = this.x;
        this.kickFromY = this.y;
        this.kickToX = xT;
        this.kickToY = yT;
        this.kickTick = 0;
        this.falling = false;
        this.isActiveFall = false;
        this.activeFallbyEx = false;
        this.isMove = false;
    }

    public boolean kicked() {
        return this.kickTick >= 0;
    }

    /** One tick of the kick's flight; false once he has landed (the next message may then be read). */
    private boolean kickFly() {
        if (this.kickTick < 0) {
            return false;
        }
        float t = ++this.kickTick / (float) KICK_TICKS;
        if (t >= 1f) {
            this.kickTick = -1;
            GameScr.pm.updatePlayerXY(this.index, (short) this.kickToX, (short) this.kickToY);
            this.xToNow = this.kickToX;
            this.yToNow = this.kickToY;
            Session_ME.receiveSynchronized = 0;
            return false;
        }
        this.x = this.kickFromX + Math.round((this.kickToX - this.kickFromX) * t);
        this.y = this.kickFromY + Math.round((this.kickToY - this.kickFromY) * t - 4 * KICK_PEAK * t * (1 - t));
        return true;
    }

    /**
     * Tarzan's POW boomerang flying back to him after a hit (message -127, LAN build 14, server SetPassive
     * "Vòng quanh thế giới"): BOOMERANG_TICKS ticks in an arc from where it hit; it only shows, nothing waits for it.
     */
    private static final int BOOMERANG_TICKS = 20;
    private int boomerangTick = -1, boomerangFromX, boomerangFromY;

    public void boomerangBack(int xFrom, int yFrom) {
        this.boomerangFromX = xFrom;
        this.boomerangFromY = yFrom;
        this.boomerangTick = 0;
    }

    public boolean boomerangFlying() {
        return this.boomerangTick >= 0;
    }

    private void paintBoomerang(mGraphics g) {
        if (this.boomerangTick < 0 || GameScr.bm == null) {
            return;
        }
        FrameImage picture = GameScr.bm.frameBulletSpecial[21][0];
        if (picture == null || picture.nFrame <= 0) {
            return;
        }
        float t = this.boomerangTick / (float) BOOMERANG_TICKS;
        int toY = this.y - 20 - this.grownLift();
        int peak = Math.max(40, Math.abs(this.x - this.boomerangFromX) / 4);
        int xB = this.boomerangFromX + Math.round((this.x - this.boomerangFromX) * t);
        int yB = this.boomerangFromY + Math.round((toY - this.boomerangFromY) * t - 4 * peak * t * (1 - t));
        picture.drawFrame(this.boomerangTick % picture.nFrame, xB, yB, 0, 3, g, false);
    }

    public void flyToPoint(int xT, int yT) {
        this.flyActive = true;
        this.xTo = xT;
        this.yTo = yT;
        this.xFrom = this.x;
        this.yFrom = this.y;
        if (this.xTo > this.xFrom) {
            this.sLook = 1;
            this.look = 2;
        } else {
            this.sLook = 0;
            this.look = 0;
        }

    }

    public void flyTo(int speed) {
        if (this.x >= MM.mapWidth) {
            this.x = -100;
            this.xTo = this.xFrom;
            this.yTo = this.yFrom;
            Boss.camY = 0;
        }

        int dx = this.xTo - this.x;
        int dy = this.y - this.yTo;
        int angel = CRes.angle(dx, dy);
        this.xa = this.move(angel, speed).x;
        this.ya = this.move(angel, speed).y;
        this.x += this.xa;
        this.y += this.ya;
        if (this.x < this.xTo + speed / 2 && this.x >= this.xTo - speed / 2 && this.y < this.yTo + speed / 2 && this.y >= this.yTo - speed / 2) {
            this.x = this.xTo;
            this.y = this.yTo;

            for (this.yPoint = this.yTo; !GameScr.mm.isLand(this.x, this.yPoint) && this.yPoint < MM.mapHeight; ++this.yPoint) {
            }

            this.bulletType = -1;
            this.isPointActive = true;
            this.flyActive = false;
            CCanvas.lockNotify = true;
        }

    }

    public void update() {
        if (this.boomerangTick >= 0 && ++this.boomerangTick > BOOMERANG_TICKS) {
            this.boomerangTick = -1; // back in his hand
        }
        if (this.kickFly()) {
            return;
        }
        if (this.paralyzed == 2) {
            this.angle = this.frozenAngle; // locked by Electrician's shock: whatever tries to turn the gun, it stays
        }

        if (PM.curP == GameScr.myIndex) {
            if (this.angryX < this.currAngry) {
                this.angryX = (byte) (this.angryX + 2);
            }

            if (this.angryX > this.currAngry) {
                this.angryX = this.currAngry;
            }
        }

        switch (this.state) {
            case 2:
            case 3:
            case 4:
            case 6:
            default:
                break;
            case 5:
                if (this.gun != 23 && this.gun != 24 && this.gun != 25 && this.gun != 26) {
                    if (this.smokeDelayWhenDie <= 0) {
                        GameScr.sm.addSmoke(this.x, this.y - 8, (byte) 5);
                        this.smokeDelayWhenDie = 100;
                    } else {
                        --this.smokeDelayWhenDie;
                    }
                }
                break;
            case 7:
                this.animWin();
                break;
            case 8:
                this.animHurt();
        }

        // (King Kong grown standing still holds his gun further out: the crosshair stays outside him.)
        int aimRadius = Math.round(this.radius * this.grownScale());
        this.dx = aimRadius * CRes.cos(this.angle) >> 10;
        this.dy = aimRadius * CRes.sin(this.angle) >> 10;
        if (this.isCom && !this.flyPlayer && !this.isJump) {
            if (this.nextx == this.x && this.nexty == this.y) {
                this.setState((byte) 1);
            }

            if (this.climber()) {
                // Climbing Tarzan (build 8): the server sends each step of his, along walls too: he goes straight there.
                if (this.nextx != this.x || this.nexty != this.y) {
                    this.look = this.nextx < this.x ? 0 : this.nextx > this.x ? 2 : this.look;
                    this.x = this.nextx;
                    this.y = this.nexty;
                    this.animMove();
                }
            } else if (this.nextx < this.x && !this.falling) {
                this.move(0);
            } else if (this.nextx > this.x && !this.falling) {
                this.move(2);
            }

            if (this.nextx == this.x && this.nexty != this.y && this.state == 0 && !this.falling) {
                this.y = this.nexty;
            }
        }

        if (this.activeFallbyEx && !BM.active) {
            this.falling = true;
            this.activeFallbyEx = false;
            this.isActiveFall = true;
        }

        if (this.falling) {
            this.fall();
        }

        if (!this.isUsedItem) {
            this.angleReset();
        }

        if (this.poisonEff) {
            ++this.tPEff;
            if (this.tPEff == 20) {
                this.tPEff = 0;
                this.poisonEff = false;
                CCanvas.lockNotify = true;
            }

            if (CCanvas.gameTick % 2 == 0) {
                new Explosion(CRes.random(this.x - 10, this.x + 10), CRes.random(this.y - 20, this.y + 2), (byte) 10);
            }
        }

        if (this.state == 1 && this.x == this.xToNow && this.y == this.yToNow && this.isMove) {
            this.isMove = false;
            Session_ME.receiveSynchronized = 0;
        }

        if (this.isMove) {
            ++this.tMove;
            if (this.tMove == 200) {
                this.tMove = 0;
                this.x = this.xToNow;
                this.y = this.yToNow;
                this.tMove = 0;
                this.isMove = false;
                Session_ME.receiveSynchronized = 0;
            }
        }

    }

    public void angleReset() {
        if (this.gun <= 9) {
            if (this.gun != 15) {
                if (this.gun != 17) {
                    if (this.look == 0 && this.angle > 180 - this.angleLimit()) {
                        this.angle = 180 - this.angleLimit();
                    }

                    if (this.look == 2 && this.angle < this.angleLimit()) {
                        this.angle = this.angleLimit();
                    }

                }
            }
        }
    }

    public void checkNomarShoot() {
        int limit = this.angleLimit();
        if (this.look == 0 && this.angle > 180 - limit) {
            this.angle = 180 - limit;
        }

        if (this.look == 2) {
            if (this.angle < limit) {
                this.angle = limit;
            }

            if (this.angle > 270) {
                this.angle -= 360;
                if (CRes.abs(this.angle) > CRes.abs(limit)) {
                    this.angle = limit;
                }
            }
        }

    }

    public void angleUpdate() {
        if (!this.isUsedItem) {
            this.checkNomarShoot();
        } else {
            if (this.look == 0) {
                if (this.itemUsed == 20) {
                    if (this.angle > 225) {
                        this.angle = 225;
                    }

                    if (this.angle < 180) {
                        this.angle = 180;
                    }
                } else if (this.itemUsed == 22) {
                    if (this.angle != 91) {
                        this.angle = 91;
                        this.curFrame = 3;
                    }
                } else if (this.itemUsed == 23) {
                    this.angle = -269;
                } else {
                    this.checkNomarShoot();
                }
            }

            if (this.look == 2) {
                if (this.itemUsed == 20) {
                    if (this.angle > 0) {
                        this.angle = 0;
                    }

                    if (this.angle < -45) {
                        this.angle = -45;
                    }
                } else if (this.itemUsed == 22) {
                    if (this.angle != 89) {
                        this.angle = 89;
                        this.curFrame = 3;
                    }
                } else if (this.itemUsed == 23) {
                    this.angle = -89;
                } else {
                    this.checkNomarShoot();
                }
            }
        }

    }

    public void fall() {
        if (!MM.groundReady()) {
            // LAN: the ground is still coming (the first match on a map): falling now went through it, out of the
            // map, and the players stayed out of sight until the server placed them again a turn or so later.
            return;
        }

        if (this.flyPlayer || this.isCapture) {
            if (this.gun == 25 || this.gun == 26) {
                this.falling = false;
                this.isActiveFall = false;
                if (this.state == 5) {
                    new Explosion(this.x, this.y, (byte) 1);
                    this.isPaint = false;
                }

                return;
            }

            if (this.state != 5) {
                this.falling = false;
                this.isActiveFall = false;
                return;
            }
        }

        if (this.y > MM.mapHeight + 200) {
            if (this.isActiveFall) {
                this.isAllowSendPosAfterShoot = true;
                this.isActiveFall = false;
                if (this.state != 5 && GameScr.myIndex == this.index && !this.isSendM_autoDie) {
                    GameService.gI().move((short) this.x, (short) this.y);
                    this.die();
                    this.isSendM_autoDie = true;
                }

                this.falling = false;
                if (GameScr.trainingMode) {
                    GameService.gI().training((byte) 1);
                    GameScr.trainingMode = false;
                    this.die();
                }
            }

            this.nexty = this.y;
            if (this.state != 1) {
                this.resetLastUpdateXY(this.x, this.y);
            }
        } else {
            this.vy += this.g;
            int nextFallY = this.y + this.vy;
            int h = Math.abs(nextFallY - this.y);

            boolean climber = this.climber();
            if (climber) {
                while (this.y > 0 && GameScr.mm.isLand(this.x, this.y)) --this.y; // up out of the ground, onto his anchor
            }
            for (int i = 0; i <= h; ++i) {
                // (A climber stops on the first air pixel next to any ground: the server's Climb.settle.)
                if (climber ? Climb.attached(this.x, this.y) : GameScr.mm.isLand(this.x, this.y)) {
                    this.vy = 0;
                    this.falling = false;
                    if (this.isActiveFall) {
                        this.isAllowSendPosAfterShoot = true;
                        this.isActiveFall = false;
                        if (PM.getCurPlayer().gun == 15) {
                            this.earthwakeActive = true;
                        }
                    }

                    this.nexty = this.y;
                    if (this.state != 1) {
                        this.resetLastUpdateXY(this.x, this.y);
                    }

                    if (this.index == GameScr.myIndex && this.state != 1 && !BM.active && this.chophepGuiUpdateXY) {
                        GameService.gI().requiredUpdateXY((short) this.x, (short) this.y);
                        if (this.itemUsed == 23) {
                            GameScr.pm.updatePlayerXY(this.index, (short) this.x, (short) this.y);
                        }
                    }
                    break;
                }

                ++this.y;
            }
        }

        if (MM.isHaveWaterOrGlass && !this.isWaterBum && MM.checkWaterBum(this.x, this.y, (byte) 2)) {
            this.isWaterBum = true;
        }

    }

    public void holdFire() {
        CCanvas.keyPressed[12] = false;
        CCanvas.keyPressed[13] = false;
        if (this.state != 3 && this.force > 1) {
            this.setState((byte) 3);
            this.bulletType = Bullet.setBulletType(this.gun);
            if (Bullet.isDoubleBull(this.bulletType) && !this.isUsedItem) {
                this.isDoublePower = true;
            } else if (this.is2TurnItem) {
                this.isDoublePower = Bullet.isDoubleBull(this.bulletType);
            } else {
                this.isDoublePower = false;
            }

            GameScr.time.stop();
        } else if (!this.isDoublePower) {
            ++this.force;
            if (this.force >= this.maxforce) {
                this.shoot();
                if (Bullet.isDoubleBull(this.bulletType)) {
                    this.isDoublePower = true;
                }

                GameScr.clearKey();
                isStopFire = true;
            }
        } else if (!this.isSecondPower) {
            ++this.force;
            if (this.force >= this.maxforce) {
                this.isSecondPower = true;
            }
        } else {
            ++this.force_2;
            if (this.force_2 >= this.maxforce2) {
                this.shoot();
                this.isSecondPower = false;
                GameScr.clearKey();
                isStopFire = true;
            }
        }

    }

    public void fire() {
        if (!this.isDoublePower) {
            if (this.force > 1 && this.state == 3) {
                this.setState((byte) 2);
                this.shoot();
                GameScr.clearKey();
                this.force = 0;
            } else {
                this.force = 0;
            }
        } else if (this.state == 3) {
            if (!this.isSecondPower) {
                this.isSecondPower = true;
            } else if (this.force_2 > 1) {
                this.setState((byte) 2);
                this.shoot();
                this.isSecondPower = false;
                GameScr.clearKey();
                this.force = 0;
                this.force_2 = 0;
            }
        } else {
            this.force = 0;
            this.force_2 = 0;
        }

    }

    public void shoot() {
        this.active = false;
        isShooting = true;
        this.shootFrame = true;
        this.nShoot = 1;
        isGetPosition = true;
        this.bulletType = Bullet.setBulletType(this.gun);
        if (this.itemUsed == 2) {
            this.nShoot = (byte) (this.nShoot * 2);
            this.itemUsed = -1;
            this.sendWaitForFire(this.bulletType, this.nShoot);
        } else if (this.itemUsed == 1) {
            this.itemUsed = -1;
            this.sendWaitForFire((byte) 5, (byte) 1);
        } else if (this.itemUsed == 8) {
            this.itemUsed = -1;
            this.sendWaitForFire((byte) 4, (byte) 1);
        } else if (this.itemUsed == 11) {
            this.itemUsed = -1;
            this.sendWaitForFire((byte) 16, (byte) 1);
        } else if (this.itemUsed == 6) {
            this.itemUsed = -1;
            this.sendWaitForFire((byte) 6, (byte) 1);
        } else if (this.itemUsed == 7) {
            this.itemUsed = -1;
            this.sendWaitForFire((byte) 7, (byte) 1);
        } else if (this.itemUsed == 8) {
            this.itemUsed = -1;
            this.sendWaitForFire((byte) 4, (byte) 1);
        } else if (this.itemUsed == 9) {
            this.itemUsed = -1;
            this.sendWaitForFire((byte) 8, (byte) 1);
        } else if (this.itemUsed == 30) {
            this.itemUsed = -1;
            this.sendWaitForFire((byte) 56, (byte) 1);
        } else if (this.itemUsed == 16) {
            this.itemUsed = -1;
            this.sendWaitForFire((byte) 14, (byte) 1);
        } else if (this.itemUsed == 17) {
            this.itemUsed = -1;
            this.sendWaitForFire((byte) 13, (byte) 1);
        } else if (this.itemUsed == 18) {
            this.itemUsed = -1;
            this.sendWaitForFire((byte) 22, (byte) 1);
        } else if (this.itemUsed == 19) {
            this.itemUsed = -1;
            this.sendWaitForFire((byte) 26, (byte) 1);
        } else if (this.itemUsed == 20) {
            this.itemUsed = -1;
            this.sendWaitForFire((byte) 25, (byte) 1);
        } else if (this.itemUsed == 21) {
            this.itemUsed = -1;
            this.sendWaitForFire((byte) 23, (byte) 1);
        } else if (this.itemUsed == 22) {
            this.itemUsed = -1;
            this.sendWaitForFire((byte) 28, (byte) 1);
        } else if (this.itemUsed == 23) {
            this.itemUsed = -1;
            this.sendWaitForFire((byte) 30, (byte) 1);
        } else if (this.itemUsed == 24) {
            this.itemUsed = -1;
            this.sendWaitForFire((byte) 50, (byte) 1);
        } else if (this.itemUsed == 25) {
            this.itemUsed = -1;
            this.sendWaitForFire((byte) 51, (byte) 1);
        } else if (this.itemUsed == 26) {
            this.itemUsed = -1;
            this.sendWaitForFire((byte) 52, (byte) 1);
        } else if (this.itemUsed == 27) {
            this.itemUsed = -1;
            this.sendWaitForFire((byte) 53, (byte) 1);
        } else if (this.itemUsed == 28) {
            this.itemUsed = -1;
            this.sendWaitForFire((byte) 54, (byte) 1);
        } else if (this.itemUsed == 29) {
            this.itemUsed = -1;
            this.sendWaitForFire((byte) 55, (byte) 1);
        } else if (this.itemUsed == 31) {
            this.itemUsed = -1;
            this.sendWaitForFire((byte) 57, (byte) 1);
        } else {
            this.sendWaitForFire(this.bulletType, this.nShoot);
        }

        angleLock[this.gun] = angleLockMain[this.gun];
    }

    public void shoot(byte critical, byte whoShoot, short xP, short yP, byte _bullType, short[][] _x, short[][] _y, byte _numShoot, byte force2, short angle, short[][] _xHit, short[][] _yHit, int xSuperT, int ySuperT) {
        this.shootFrame = true;
        this.isPointActive = false;
        if (GameScr.pm.isYourTurn() && this.index == GameScr.myIndex) {
            this.active = false;
            isShooting = true;
        }

        this.x = this.nextx = this.lastx = xP;
        this.y = this.nexty = yP;
        xSuper = xSuperT;
        ySuper = ySuperT;
        this.resetLastUpdateXY(this.x, this.y);
        if (_bullType != 36) {
            if (_x[0][0] > xP) {
                this.look = 2;
            } else {
                this.look = 0;
            }
        }

        this.bulletType = _bullType;
        this.angle = angle;
        this.checkAngleForSprite();
        CRes.out("name char shoot| " + this.name + "/" + angle + "/curFame " + this.curFrame);
        GameScr.bm.setBullType(critical, whoShoot, _bullType, _x, _y, _numShoot, force2, _xHit, _yHit, this.equip != null ? this.equip.equips[0].bullet : 0);
        this.setState((byte) 4);
        if (_bullType == 35) {
            this.isBum = true;
        }

        if (_bullType == 43) {
            for (int i = 0; i < _x.length; ++i) {
                if (_x[i][0] + MM.mapWidth / 10 >= MM.mapWidth) {
                    this.bombIndex = i;
                    break;
                }
            }

            this._x = _x;
            this._y = _y;
            GameScr.pm.flyTo(whoShoot, (short) (MM.mapWidth + 100), _y[0][0]);
        }

        if (_bullType == 44) {
            Boss.xTo = _x[0][_x[0].length - 1];
        }

        if (_bullType == 47) {
            try {
                this.check_Spider_look();
            } catch (Exception var16) {
            }
        }

    }

    public void check_Spider_look() {
        int xTo = this._x[2][3];
        int yTo = this._y[2][3];
        int look = Smoke.checkWay(this.x, this.y, xTo, yTo);
        switch (look) {
            case 0:
                this.sLook = 0;
                break;
            case 1:
                this.sLook = 1;
                break;
            case 2:
                this.sLook = 2;
                break;
            case 3:
                this.sLook = 3;
        }

    }

    public void lucky() {
        GameScr.sm.addRock(this.x, this.y - 12, CRes.random(4), CRes.random(-8, -5), (byte) 20);
        GameScr.sm.addRock(this.x, this.y - 12, CRes.random(3), CRes.random(-8, -4), (byte) 20);
        GameScr.sm.addRock(this.x, this.y - 12, -CRes.random(4), CRes.random(-8, -5), (byte) 20);
        GameScr.sm.addRock(this.x, this.y - 12, -CRes.random(3), CRes.random(-8, -4), (byte) 20);
        CCanvas.tNotify = 0;
        CCanvas.lockNotify = true;
    }

    void sendWaitForFire(byte bulletType, byte numShoot) {
        if (GameScr.trainingMode) {
            GameService.gI().waitForFIRETraining(bulletType, (short) this.x, (short) this.y, (short) this.angle, this.force, this.force_2, numShoot);
            this.isUsedItem = false; // practice: items and POW again on the next shot
            // Training has no next turn, so movement stamina would never refill after the first walk.
            this.movePoint = 0;
        } else {
            // The server uses its stored position, not the coordinates in the shot packet.
            syncMovementPosition();
            GameService.gI().waitForFIRE(bulletType, (short) this.x, (short) this.y, (short) this.angle, this.force, this.force_2, numShoot);
        }

        if (headGuideShots > 0) headGuideShots--;
        this.lastForcePoint = this.force;
        this.lastForcePoint_2 = this.force_2;
        this.force = 0;
        this.force_2 = 0;
    }

    public void UseItem(int itemID, boolean isCOM_Use, int curSlot) {
        CRes.out("======>  isCOM_Use " + isCOM_Use);
        if (!isCOM_Use) {
            if (this.isUsedItem) {
                return;
            }
            if (this.itemCooling(itemID)) {
                if (CCanvas.gameScr != null && coreLG.TerrainMidlet.myInfo != null) {
                    CCanvas.gameScr.showChat(coreLG.TerrainMidlet.myInfo.IDDB, (itemID == Item.CONCRETE ? "Bê tông cốt thép: chờ " + this.concreteCooldown : "Dịch chuyển: chờ " + this.teleportCooldown) + " lượt nữa", 90);
                }
                return;
            }

            GameService.gI().useItem((byte) itemID);
            if (itemID == Item.HEAD_GUIDE) headGuideShots = 2; // this turn and the next
            if (PrepareScr.currLevel != 7 && itemID != Item.ITEM_TELEPORT) { // the Teleport stays, cooling down
                this.item[curSlot] = -1;
            }

            this.isUsedItem = true;
        } else {
            boolean poisonRain = itemID == Item.POISON_RAIN;
            if (GameScr.trainingMode && this.index == GameScr.myIndex && itemID != 100) {
                // Practice: the server needs the item too (Gunner's finisher, concrete, poison rain); it is never used up.
                GameService.gI().useItem((byte) itemID);
                if (itemID == Item.HEAD_GUIDE) headGuideShots = 2;
            }
            switch (itemID) {
                case 0:
                case 2:
                case 32:
                case 33:
                    this.is2TurnItem = true;
                    new Explosion(this.x, this.y - 12, (byte) 5, this.index, (byte) itemID);
                    break;
                case 1:
                case 6:
                case 7:
                case 8:
                case 9:
                case 11:
                case 16:
                case 17:
                case 18:
                case 19:
                case 21:
                case Item.POISON_RAIN: // falls like Sao băng
                case 24:
                case 25:
                case 26:
                case 27:
                case 28:
                case 29:
                case 30:
                    CRes.out("fly");
                    this.is2TurnItem = false;
                    new Explosion(this.x, this.y - 12, (byte) 5, this.index, (byte) itemID);
                    break;
                case 3:
                    this.is2TurnItem = true;
                    this.isRunSpeed = true;
                    new Explosion(this.x, this.y - 12, (byte) 5, this.index, (byte) itemID);
                    if (!this.isInvisible) {
                        new Explosion(this.x, this.y, (byte) 4, this.index, (byte) itemID);
                    }
                    break;
                case 4:
                case 34:
                    this.is2TurnItem = true;
                    this.isInvisible = true;
                    new Explosion(this.x, this.y - 12, (byte) 5, this.index, (byte) itemID);
                    new Explosion(this.x, this.y, (byte) 4, this.index, (byte) itemID);
                    break;
                case 5:
                case Item.CONCRETE:
                    this.is2TurnItem = true;
                    new Explosion(this.x, this.y - 12, (byte) 5, this.index, (byte) itemID);
                    break;
                case 10:
                    this.is2TurnItem = true;
                    new Explosion(this.x, this.y - 12, (byte) 5, this.index, (byte) itemID);
                    break;
                case 20:
                    if (this.look == 2) {
                        this.angle = 0;
                    }

                    if (this.look == 0) {
                        this.angle = 180;
                    }

                    this.is2TurnItem = false;
                    new Explosion(this.x, this.y - 12, (byte) 5, this.index, (byte) itemID);
                    break;
                case 22:
                    this.angle = 89;
                    this.is2TurnItem = false;
                    new Explosion(this.x, this.y - 12, (byte) 5, this.index, (byte) itemID);
                    break;
                case 23:
                    this.angle = -89;
                    this.is2TurnItem = false;
                    new Explosion(this.x, this.y - 12, (byte) 5, this.index, (byte) itemID);
                    break;
                case 35:
                    this.is2TurnItem = true;
                    new Explosion(this.x, this.y - 12, (byte) 5, this.index, (byte) itemID);
                    new Explosion(this.x, this.y, (byte) 4, this.index, (byte) itemID);
                    break;
                case 100:
                    new Explosion(this.x, this.y - 12, (byte) 5, this.index, Item.ITEM_ANGRY); // POW's own icon, not item 38's
            }

            // The poison rain aims and flies like Sao băng (item 21).
            this.itemUsed = poisonRain ? 21 : itemID;
            this.setState((byte) 0);
            if (!this.isInvisible) {
                new Explosion(this.x, this.y - 12, (byte) 3, this.index, (byte) itemID);
            }

            if (this.index == GameScr.myIndex && itemID != 100 && itemID != Item.ITEM_TELEPORT && !GameScr.trainingMode) {
                --ShopItem.getI(itemID).num; // (the Teleport is never used up: it cools down)
            }

            mSound.playSound(1, mSound.volumeSound, 3);
            this.force = 0;
            this.force_2 = 0;
            if (this.itemUsed != 100 && this.isSecondPower) {
                this.isSecondPower = false;
            }

            if (this.gun == 6 || this.gun == 8) {
                this.isDoublePower = true;
            }
        }

    }

    public void paint(mGraphics g) {
        if (this.isPaint) {
            if (PrepareScr.currLevel != 7 || PrepareScr.currLevel == 7 && this.state != 5 && this.hp > 0) {
                if (!CRes.isNullOrEmpty(GameScr.res)) {
                    this.paintPlayer(g);
                    this.painthp(g);
                    this.paintName(g);
                } else if (!this.isInvisible) {
                    this.paintPlayer(g);
                    this.painthp(g);
                    this.paintName(g);
                } else {
                    label109:
                    {
                        if (this.index != GameScr.myIndex) {
                            PM var10001 = GameScr.pm;
                            if (this.team != PM.getMyPlayer().team) {
                                break label109;
                            }
                        }

                        this.paintPlayer(g);
                        this.painthp(g);
                        this.paintName(g);
                    }
                }

                this.painthpChange(g);
                this.paintExpChange(g);
                this.paintCup(g);
                this.paintBoomerang(g);
                if (GameScr.pm.isYourTurn() && GameScr.myIndex == this.index && this.state != 8) {
                    this.paintCrosshair(g);
                }

                if (CCanvas.isDebugging()) {
                    String stateStr = "";
                    if (this.state == 0) {
                        stateStr = "STAND 0";
                    }

                    if (this.state == 1) {
                        stateStr = " MOVE   1";
                    }

                    if (this.state == 2) {
                        stateStr = " AIM   2";
                    }

                    if (this.state == 3) {
                        stateStr = " READYSHOOT 3";
                    }

                    if (this.state == 4) {
                        stateStr = " SHOOT 4";
                    }

                    if (this.state == 5) {
                        stateStr = " DIE 5";
                    }

                    if (this.state == 7) {
                        stateStr = " WIN   6";
                    }

                    if (this.state == 8) {
                        stateStr = " HURT  8";
                    }

                    if (this.state == 9) {
                        stateStr = " CAPTURE   9";
                    }

                    String lookStr = "";
                    if (this.look == 0) {
                        lookStr = "LEFT";
                    }

                    if (this.look == 2) {
                        lookStr = "RIGHT";
                    }

                    Font.normalFont.drawString(g, stateStr, this.x, this.y - 44 - 15, 2);
                    Font.normalFont.drawString(g, lookStr, this.x, this.y - 44 - 30, 2);
                    Font.normalFont.drawString(g, "angle " + this.angle, this.x, this.y - 44 - 30 - 15, 2);
                    Font.normalFont.drawString(g, "lock[ " + angleLock[this.gun] + " ]", this.x, this.y - 44 - 30 - 15 - 15, 2);
                    g.setColor(2263535);
                    g.fillRect(this.x, this.y - 5, 1, 1, false);

                    for (int i = 0; i < 5; ++i) {
                        g.setColor(16767817);
                        g.fillRect(this.x, this.y - i, 1, 1, false);
                    }

                    Font.normalFont.drawString(g, this.x + "/" + this.y, this.x, this.y, 2);
                    g.setColor(1133755);
                    g.drawLine(this.x, this.y, this.x + this.dx, this.y - this.dy - 11, false);
                }
            }

        }
    }

    private void paintPlayer(mGraphics g) {
        // Magenta shows the same POW fire as every other character (it used to get a hero aura instead).
        if (this.state != 5 && this.gun < 11 && this.isAngry) {
            ++this.ta;
            if (this.ta == 2) {
                this.ta = 0;
                ++this.fa;
                if (this.fa > 3) {
                    this.fa = 0;
                    this.yT = 0;
                }
            }

            g.beginScaleAround(this.x, this.y, this.grownScale());
            try {
                g.drawRegion(lua, 0, this.fa * 47, 41, 47, 0, this.x, this.y, 33, false);
            } finally {
                g.endScaleAround();
            }
        }

        int Look = this.look;
        if (this.state == 8) {
            Look = this.hurtLook;
        }

        if (this.gun != 15) {
            if (this.gun != 16) {
                if (this.gun != 17) {
                    if (this.gun != 19) {
                        if (this.gun != 21) {
                            if (this.gun != 22) {
                                if (this.gun != 23 && this.gun != 24) {
                                    if (PrepareScr.currLevel != 7 || PrepareScr.currLevel == 7 && this.state != 5 && this.hp != 0) {
                                        // King Kong grows standing still (set passive): his whole body, around his feet.
                                        g.beginScaleAround(this.x, this.y, this.grownScale());
                                        g.beginTurnAround(this.x, this.y, this.climbTurn()); // Tarzan on a wall or upside down
                                        try {
                                            if (this.equip != null) {
                                                this.equip.paint(g, Look, this.curFrame, this.x, this.y);
                                            } else {
                                                if (!PhotonSkin.paint(g, this.gun, Look, this.curFrame, this.x, this.y))
                                                    this.pFrameImg.drawFrame(this.curFrame, this.x, this.y, Look, 33, g);
                                            }
                                        } finally {
                                            g.endTurnAround();
                                            g.endScaleAround();
                                        }
                                    }

                                    if (this.clanIcon != null && GameScr.iconOnOf) {
                                        g.drawImage(this.clanIcon, this.x, this.y - 47, 33, false);
                                    }

                                    if (closeMirror) {
                                        ++tCl;
                                        if (tCl == 10) {
                                            tCl = 0;
                                            isMirror = false;
                                            closeMirror = false;
                                        }
                                    }

                                    if (this.isPoison && CCanvas.gameTick % 5 == 0) {
                                        new Explosion(CRes.random(this.x - 10, this.x + 10), CRes.random(this.y - 20, this.y + 2), (byte) 10);
                                    }

                                    if (GameScr.bm.critical == 0 && this.index == GameScr.myIndex && CCanvas.gameTick % 2 == 0) {
                                        ++this.frameC;
                                        if (this.frameC == 2) {
                                            this.frameC = 0;
                                        }
                                    }

                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void paintCrosshair(mGraphics g) {
        if (!(this instanceof Boss)) {
            int[] centre = this.aimCentre(); // (turned with Tarzan climbing)
            g.drawImage(crosshair, centre[0] + this.dx, centre[1] - this.dy, mGraphics.HCENTER | mGraphics.VCENTER, false);
            if (CCanvas.isDebugging()) {
                String param = "a " + this.angle;
                Font.smallFont.drawString(g, param, this.x + this.dx + 5, this.y - this.dy - 11 + 5, 2, false);
                this.crossHairX = this.x - this.dx;
                this.crossHairY = this.y + this.dy + 11;
                g.drawImage(crosshair, this.crossHairX, this.crossHairY, mGraphics.HCENTER | mGraphics.VCENTER, false);
                param = "xywh " + this.crossHairX + "/" + this.crossHairY + "/" + this.crossHairW + "/" + this.crossHairH;
                Font.smallFont.drawString(g, param, this.crossHairX, this.crossHairY - 20, 2, false);
            }

        }
    }

    /** Build 14: his set's element charging, a thin bar under his HP in its colour; full, it blinks: his next shot carries it. */
    private void paintElementCharge(mGraphics g, int x, int y) {
        g.setColor(0x1a1a1a);
        g.fillRect(x, y, 25, 3, false);
        if (this.elementCharge < 100 || CCanvas.gameTick % 10 > 3) {
            g.setColor(ELEMENT_COLORS[Math.min(this.elementOwn, ELEMENT_COLORS.length - 1)]);
            g.fillRect(x, y, 25 * Math.min(100, this.elementCharge) / 100, 3, false);
        }
    }

    void painthp(mGraphics g) {
        if (GameScr.res.equals("")) {
            if (this.gun != 15) {
                if (this.state != 5 && this.hp > 0) {
                    int dy = (this.gun != 10 ? 40 : 46) + this.grownLift();
                    g.setColor(16777215);
                    g.fillRect(this.x - 15, this.y + 5 - dy, 25, 4, false);
                    if (this.hpRectW > 16) {
                        g.setColor(65280);
                    } else if (this.hpRectW > 8) {
                        g.setColor(16744512);
                    } else {
                        g.setColor(16711680);
                    }

                    if (this.hpRectW > 25) {
                        this.hpRectW = 25;
                    }

                    g.fillRect(this.x - 15, this.y + 5 - dy, this.hpRectW, 4, false);
                    g.setColor(0);
                    g.drawRect(this.x - 15, this.y + 5 - dy, 25, 4, false);
                    if (this.elementOwn > 0) this.paintElementCharge(g, this.x - 15, this.y + 10 - dy);
                }

            }
        }
    }

    /** How big this player's last shot's bullets are drawn, percent (0 as usual): King Kong's grown POW (build 7). */
    public int passiveBonus;
    /** From the server (message 114): healing halved by the poison rain, or losing the next turn. */
    public boolean poisoned, stunned;
    private static final com.badlogic.gdx.graphics.Color POISON_PURPLE = new com.badlogic.gdx.graphics.Color(0.8f, 0.4f, 1f, 1f);

    public void paintName(mGraphics g) {
        if (this.state != 5) {
            // smallFont has only digits and capitals; borderFont is white with a black edge, tinted purple here.
            int y = this.y - 60 - this.grownLift();
            if (this.stunned) {
                Font.normalYFont.drawString(g, "Choáng", this.x, y, 2);
                y -= 13;
            }
            if (this.paralyzed > 0) {
                Font.normalYFont.drawString(g, this.paralyzed == FROZEN ? "Cóng" : this.paralyzed == 2 ? "Tê liệt" : "Tê", this.x, y, 2);
                y -= 13;
            }
            if ((this.elementFlags & 4) != 0 && this.paralyzed != FROZEN) {
                Font.normalYFont.drawString(g, "Cóng", this.x, y, 2);
                y -= 13;
            }
            if ((this.elementFlags & 1) != 0) {
                Font.normalYFont.drawString(g, "Thiêu", this.x, y, 2);
                y -= 13;
            }
            if ((this.elementFlags & 2) != 0 && !this.poisoned) {
                g.tint = POISON_PURPLE;
                Font.borderFont.drawString(g, "Độc", this.x, y, 2);
                g.tint = null;
                y -= 13;
            }
            if (this.poisoned) {
                g.tint = POISON_PURPLE;
                Font.borderFont.drawString(g, "Độc", this.x, y, 2);
                g.tint = null;
            }
        }
        if (PM.curP == this.index) {
            if (CCanvas.gameTick % 10 > 5) {
                (this.team ? Font.smallFontRed : Font.smallFontYellow).drawString(g, this.name.toUpperCase(), this.x, this.y - 44 - this.grownLift(), 2);
            }
        } else {
            (this.team ? Font.smallFontRed : Font.smallFontYellow).drawString(g, this.name.toUpperCase(), this.x, this.y - 44 - this.grownLift(), 2);
        }

        int dy = this.gun != 10 ? 27 : 35;
        PrepareScr.paintQuanHam(this.nQuanHam, this.x, this.y - dy, mGraphics.VCENTER | mGraphics.HCENTER, g);
    }

    public void updateAngry(byte angry) {
        this.currAngry = angry;
        if (this.currAngry == 100) {
            this.isAngry = true;
        }

        if (this.currAngry != 100) {
            this.isAngry = false;
        }

    }

    public void updateHP(int nextHP, byte pixel) {
        CRes.out("===> nextHP = " + nextHP + " ___ pixel= " + pixel + " __ name = " + this.name);
        boolean itemHP = false;
        this.hpChangeVisible = true;
        this.hpRectW = pixel;
        if (nextHP < this.hp) {
            this.hpChangeAmount = Math.abs(this.hp - nextHP);
            this.hpText = "- " + this.hpChangeAmount;
            this.hpTang = false;
            if (GameScr.cam != null) {
                GameScr.cam.setTargetPointMode(this.x, this.y);
            }
        } else {
            this.hpTang = true;
            this.hpChangeAmount = Math.abs(nextHP - this.hp);
            this.hpText = "+ " + this.hpChangeAmount;
            itemHP = true;
        }

        this.dyhp = 25;
        this.hp = nextHP;
        if (nextHP <= 0) {
            this.die();
        }

        if (!itemHP) {
            GameScr.waitting();
        }
    }

    public void updateExp(int exp) {
        this.addExp = true;
        this.exp = exp;
        this.dyExp = 30;
        if (exp >= 0) {
            this.expText = "exp: +" + exp;
        } else {
            this.expText = "exp: " + exp;
        }

        GameScr.cam.setTargetPointMode(this.x, this.y);
        GameScr.waitting();
    }

    public void updateCup(int cup) {
        this.addCup = true;
        this.cup = cup;
        this.dyCup = 35;
        if (cup >= 0) {
            this.cupText = " +" + cup;
        } else {
            this.cupText = "" + cup;
        }

        GameScr.cam.setTargetPointMode(this.x, this.y);
        GameScr.waitting();
    }

    /**
     * LAN: hidden by Apache's set passive "Thợ săn ẩn nấp", drawn as with the invisibility item (opponents see
     * nothing, the team sees the shimmer) until message 80 shows the player again.
     */
    public void hide() {
        if (this.isInvisible || this.state == 5) return;
        this.isInvisible = true;
        if (GameScr.exs != null) new Explosion(this.x, this.y, (byte) 4, this.index, (byte) Item.ITEM_INVISIBLE); // set up by initGame
    }

    /** Kind of bullet this player's weapon throws (its row in the character's bullet sheet), or -1. */
    public int bulletKind() {
        return this.equip != null && this.equip.equips[0] != null ? this.equip.equips[0].bullet : -1;
    }

    public void die() {
        CRes.out("======> Roi vao trang thai die!");
        this.setState((byte) 5);
        this.hp = 0;
        this.curFrame = 7;
        if (this.isInvisible) {
            this.isInvisible = false;
        }

        this.huntRun = false;
        if (this.isRunSpeed) {
            this.isRunSpeed = false;
        }

        if (this.flyPlayer) {
            if (this.gun == 21) {
                return;
            }

            if (this.gun == 17) {
                return;
            }

            this.falling = true;
        }

        Session_ME.receiveSynchronized = 0;
    }

    void paintExpChange(mGraphics g) {
        if (this.addExp) {
            g.setColor(16711680);
            Font.borderFont.drawString(g, this.expText, this.x, this.y - this.dyExp, mGraphics.VCENTER | mGraphics.HCENTER);
            ++this.dyExp;
            if (this.dyExp > 200) {
                this.addExp = false;
                this.expText = "";
            }
        }

    }

    void paintCup(mGraphics g) {
        if (this.addCup) {
            Font.borderFont.drawString(g, this.cupText, this.x, this.y - this.dyCup, mGraphics.VCENTER | mGraphics.HCENTER);
            g.drawImage(CScreen.cup, this.x + 20, this.y - this.dyCup + 10, 3, false);
            ++this.dyCup;
            if (this.dyCup > 200) {
                this.addCup = this.falling;
                this.cupText = "";
            }
        }

    }

    void painthpChange(mGraphics g) {
        if (this.hpChangeVisible) {
            g.setColor(16711680);
            // LAN: HP gained shows green with its plus (bigFont has no plus and only the orange of a hit).
            (this.hpTang ? Font.bigHealFont : Font.bigFont).drawString(g, this.hpText, this.x, this.y - this.dyhp, mGraphics.VCENTER | mGraphics.HCENTER);
            ++this.dyhp;
            if (this.dyhp > 50) {
                this.hpChangeVisible = false;
                this.hpText = "";
            }
        }

    }

    public void move(int directMove) {
        // In a match the next turn clears the shooting state; training never gets a next turn.
        boolean trainingShotDone = GameScr.trainingMode && this.state == 4 && !BM.active && !isShooting;
        if (this.state == 0 || this.state == 2 || this.state == 8 || trainingShotDone) {
            this.setState((byte) 1);
            this.curFrame = 4;
        }

        if (this.state != 3) {
            if (directMove == 0) {
                if (this.look != 0) {
                    this.angle = 180 - this.angle;
                }
            } else if (directMove == 2 && this.look != 2) {
                this.angle = 180 - this.angle;
            }

            this.look = directMove;
        }

        if (!this.isFreeze) {
            if (this.state == 1) {
                if (!this.isActiveFall) {
                    this.isActiveFall = true;
                }

                if (MM.isHaveWaterOrGlass && GameScr.exs.size() == 0) {
                    MM.checkWaterBum(this.x, this.y, (byte) 0);
                }

                byte runRange = this.runSpeed;
                if (this.isRunSpeed || this.huntRun) {
                    runRange = 2;
                }

                if (!this.isCom) {
                    if (this.movePoint > this.maxMovePoint() || this.paralyzed >= 2) { // (locked or frozen: not one step)
                        return;
                    }

                    ++this.movePoint;
                }

                this.animMove();
                if (this.climber()) { // Tarzan climbing (build 8): along the ground, walls and overhangs too
                    if (!this.climbStep(directMove, runRange) && !this.isCom) --this.movePoint;
                    return;
                }
                if (directMove == 0) {
                    this.x -= runRange;
                } else if (directMove == 2) {
                    this.x += runRange;
                }

                this.falling = true;
                if (GameScr.mm.isLand(this.x, this.y - climb() - 1)) {
                    if (!this.isCom) {
                        --this.movePoint;
                    }

                    this.falling = false;
                    if (directMove == 0) {
                        this.x += runRange;
                    } else if (directMove == 2) {
                        this.x -= runRange;
                    }

                    if (this.isCom && this.x == this.nextx && this.y != this.nexty) {
                        this.y = this.nexty;
                    }
                } else {
                    for (int i = climb(); i >= 0; --i) {
                        if (GameScr.mm.isLand(this.x, this.y - i)) {
                            this.falling = false;
                            this.y -= i;
                            this.nexty = this.y;
                            if (this.isCom && this.x == this.nextx && this.y != this.nexty) {
                                this.y = this.nexty;
                            }
                        }
                    }
                }
            }

        }
    }

    public void move(int directMove, int f, int ff) {
        if (this.state == 0 || this.state == 2 || this.state == 8) {
            this.setState((byte) 1);
            this.curFrame = 4;
        }

        if (this.state != 3) {
            if (directMove == 0) {
                if (this.look != 0) {
                    this.angle = 180 - this.angle;
                }

                this.look = 0;
            } else if (directMove == 2) {
                if (this.look != 2) {
                    this.angle = 180 - this.angle;
                }

                this.look = 2;
            }
        }

        if (!this.isFreeze) {
            if (this.state == 1) {
                if (!this.isActiveFall) {
                    this.isActiveFall = true;
                }

                if (MM.isHaveWaterOrGlass && GameScr.exs.size() == 0) {
                    MM.checkWaterBum(this.x, this.y, (byte) 0);
                }

                byte runRange = this.runSpeed;
                if (this.isRunSpeed || this.huntRun) {
                    runRange = 2;
                }

                if (!this.isCom) {
                    if (this.movePoint > this.maxMovePoint() || this.paralyzed >= 2) { // (locked or frozen: not one step)
                        return;
                    }

                    ++this.movePoint;
                }

                this.animMove();
                if (this.climber()) { // Tarzan climbing (build 8): along the ground, walls and overhangs too
                    if (!this.climbStep(directMove, runRange)) {
                        if (!this.isCom) --this.movePoint;
                    } else if (GameScr.pm.isYourTurn() && !CCanvas.isPointerDown[this.index]) {
                        this.resetLastUpdateXY(this.x, this.y);
                        GameService.gI().move((short) this.x, (short) this.y);
                    }
                    return;
                }
                if (directMove == 0) {
                    this.x -= runRange;
                } else if (directMove == 2) {
                    this.x += runRange;
                }

                this.falling = true;
                if (GameScr.pm.isYourTurn() && !CCanvas.isPointerDown[this.index]) {
                    this.resetLastUpdateXY(this.x, this.y);
                    GameService.gI().move((short) this.x, (short) this.y);
                }

                if (GameScr.mm.isLand(this.x, this.y - climb() - 1)) {
                    if (!this.isCom) {
                        --this.movePoint;
                    }

                    if (directMove == 0) {
                        this.x += runRange;
                    } else if (directMove == 2) {
                        this.x -= runRange;
                    }

                    this.falling = false;
                    if (this.isCom && this.x == this.nextx && this.y != this.nexty) {
                        this.y = this.nexty;
                    }
                } else {
                    for (int i = climb(); i >= 0; --i) {
                        if (GameScr.mm.isLand(this.x, this.y - i)) {
                            this.y -= i;
                            this.nexty = this.y;
                            this.falling = false;
                            if (this.isCom && this.x == this.nextx && this.y != this.nexty) {
                                this.y = this.nexty;
                            }
                        }
                    }
                }
            }

        }
    }

    public void aimUp() {
    }

    public void aimDown() {
    }

    public void checkAngleForSprite() {
        if (this.angle < 255 && this.angle > 90) {
            if (this.angle > 195) {
                this.curFrame = 0;
            } else if (this.angle > 165) {
                this.curFrame = 1;
            } else if (this.angle > 115) {
                this.curFrame = 2;
            } else {
                this.curFrame = 3;
            }
        } else if (this.angle > 65) {
            this.curFrame = 3;
        } else if (this.angle > 15) {
            this.curFrame = 2;
        } else if (this.angle > -15) {
            this.curFrame = 1;
        } else {
            this.curFrame = 0;
        }

    }

    public void animMove() {
        ++this.frameDelay;
        if (this.frameDelay > 2) {
            ++this.curFrame;
            if (this.curFrame == 6) {
                this.curFrame = 4;
            }

            this.frameDelay = 0;
        }

    }

    public void animWin() {
        ++this.frameDelay;
        if (this.frameDelay > 4) {
            ++this.curFrame;
            if (this.curFrame == 10) {
                this.curFrame = 8;
            }

            this.frameDelay = 0;
        }

    }

    public void activeHurt(int direct) {
        if (this.state != 8) {
            if (this.state != 5) {
                this.setState((byte) 8);
                this.frameDelay = 0;
                this.curFrame = 6;
                this.hurtLook = direct;
            }
        }
    }

    public void checkGhostLook(int xP, int xG) {
        if (xP > xG) {
            this.look = 2;
        } else {
            this.look = 0;
        }

    }

    public void animHurt() {
        ++this.frameDelay;
        if (this.frameDelay > 25) {
            if (this.hp <= 0) {
                this.die();
            } else {
                this.setState((byte) 0);
                this.checkAngleForSprite();
                this.frameDelay = 0;
            }
        }

    }

    public void setWin() {
        this.curFrame = 8;
        this.setState((byte) 7);
    }

    public static void paintBugRobot(mGraphics g, int x, int y) {
        ++tB;
        if (tB == 10) {
            tB = 0;
        }

        if (tB == 5) {
            deltaY = 1;
        }

        if (tB == 0) {
            deltaY = 0;
        }

        int xBody = x + 4;
        int yBody = y - 10 + deltaY;
        int xGun = x - 15;
        int yGun = y - 15 + deltaY;
        g.drawRegion(bugbody, 0, 0, 42, 30, 2, xBody, yBody, mGraphics.BOTTOM | mGraphics.HCENTER, false);
        g.drawRegion(bugleg, 0, 0, 44, 25, 2, x, y, mGraphics.BOTTOM | mGraphics.HCENTER, false);
        g.drawRegion(buggun, 0, 15, 15, 15, 0, xGun, yGun, mGraphics.BOTTOM | mGraphics.HCENTER, false);
    }

    public static void paintBigRobot(mGraphics g, int x, int y) {
        ++tB;
        if (tB == 10) {
            tB = 0;
        }

        if (tB == 5) {
            deltaY = 1;
        }

        if (tB == 0) {
            deltaY = 0;
        }

        g.drawImage(robotLeg, x, y, mGraphics.BOTTOM | mGraphics.HCENTER, false);
        int align = mGraphics.VCENTER | mGraphics.HCENTER;
        deltaX = -20;
        g.drawRegion(robotArm, 0, 0, 35, 40, 0, x + deltaX, y - 22 + deltaY, align, false);
        g.drawImage(robotBody, x - 1, y - 10 + deltaY, mGraphics.BOTTOM | mGraphics.HCENTER, false);
        g.drawRegion(robotArm, 0, 0, 35, 40, 0, x, y - 20 + deltaY, align, false);
    }

    public static void paintUFO(mGraphics g, int x, int y) {
        g.drawRegion(imgUFO, 0, 0, 51, 46, 0, x, y, 33, false);
        if (CCanvas.gameTick % 3 == 0) {
            g.drawRegion(imgUFOFire, 0, 0, 16, 11, 0, x, y + -5, 17, false);
        } else {
            g.drawRegion(imgUFOFire, 0, 11, 16, 11, 0, x, y - 5, 17, false);
        }

    }

    public static void paintBalloon(mGraphics g, int x, int y) {
        ++tBalloon;
        if (tBalloon == 20) {
            tBalloon = 0;
        }

        if (tBalloon <= 10) {
            deltaBalloon = -1;
        } else {
            deltaBalloon = 0;
        }

        g.drawImage(Background.balloon, x, y + deltaBalloon, 33, false);
    }

    public static void paintSimplePlayer(int GunType, int FRAME, int X, int Y, int Look, PlayerEquip pEquip, mGraphics g) {
        int frameH = GunType == 3 ? 32 : 24;
        if (GunType == 7) {
            frameH = 32;
        }

        if (GunType == 12) {
            frameH = 28;
        }

        if (GunType == 14) {
            frameH = 30;
            FRAME = 0;
        }

        if (GunType == 10) {
            frameH = 22;
        }

        if (GunType == 13) {
            paintBugRobot(g, X, Y);
        } else if (GunType == 15) {
            paintBigRobot(g, X, Y);
        } else if (GunType == 16) {
            paintUFO(g, X, Y);
        } else if (GunType == 17) {
            paintBalloon(g, X, Y);
        } else if (GunType == 22) {
            paintSpider(g, X, Y);
        } else if (GunType == 25) {
            paintGhost(g, 0, X, Y);
        } else if (GunType == 26) {
            paintGhost(g, 1, X, Y);
        } else {
            if (pEquip != null) {
                pEquip.paint(g, Look, FRAME, X, Y);
            } else {
                g.drawRegion(pImg[GunType], 0, FRAME * frameH, pImg[GunType].image.getWidth(), frameH, Look, X, Y, mGraphics.BOTTOM | mGraphics.HCENTER, false);
            }
        }
    }

    public static void paintSpider(mGraphics g, int x, int y) {
        g.setColor(8026746);
        g.drawRegion(spider, 0, 0, 41, 22, 1, x + 1, y, 33, false);
        g.drawRegion(spider, 0, 0, 41, 22, 0, x, y - 22, 33, false);
    }

    public static void paintGhost(mGraphics g, int type, int x, int y) {
        g.drawRegion(type == 0 ? ghost : ghost2, 0, 0, 35, 32, 0, x, y, 33, false);
    }

    public void resetLastUpdateXY(int x, int y) {
        if (this.index == GameScr.myIndex) {
            this.lastUpdateX = x;
            this.lastUpdateY = y;
        }

    }

    public void syncMovementPosition() {
        if (this.index == GameScr.myIndex && GameScr.pm.isYourTurn()) {
            this.resetLastUpdateXY(this.x, this.y);
            this.lastx = (short) this.x;
            GameService.gI().move((short) this.x, (short) this.y);
        }
    }

    public void resetXYwhenNEXTTURN() {
        if (this.x != this.lastUpdateX) {
            this.x = this.lastUpdateX;
        }

        if (this.y != this.lastUpdateY) {
            this.y = this.lastUpdateY;
        }

    }

    public void onPointerPressed(int xScreen, int yScreen, int index) {
        if (!(this instanceof Boss)) {
            if (!isStopFire) {
                if (CCanvas.currentDialog == null) {
                    if (!GameScr.pm.isYourTurn() || this.state != 5) {
                        if (!isShooting) {
                            if (!CCanvas.pausemenu.isShow) {
                                if (!CCanvas.isTouch) {
                                    return;
                                }

                                ++this.t;
                                if (this.t == 1) {
                                    if (CCanvas.isPointer(this.crossHairX, this.crossHairY, this.crossHairW, this.crossHairH, index)) {
                                        this.isHoldAngle = true;
                                    }

                                    if (CCanvas.isPointer(GameScr.xF - 15, GameScr.yF - 15, 50, 50, index)) {
                                        this.isHoldFire = true;
                                    }
                                }
                            }

                        }
                    }
                }
            }
        }
    }

    public void onPointerDrag(int xScreen, int yScreen, int index) {
        if (!isStopFire) {
            if (CCanvas.currentDialog == null) {
                if (!CCanvas.isPointer(0, GameScr.yF - 10, CCanvas.width, CCanvas.hieght, index)) {
                    if (!CCanvas.pausemenu.isShow) {
                        if (CCanvas.isTouch) {
                            if (this.state != 5 && !this.falling) {
                                if (!isShooting) {
                                    ++this.t;
                                    if (this.t == 1) {
                                        if (CCanvas.isPointer(this.crossHairX, this.crossHairY, this.crossHairW, this.crossHairH, index)) {
                                            this.isHoldAngle = true;
                                        }

                                        if (CCanvas.isPointer(GameScr.xF - 15, GameScr.yF - 15, 50, 50, 0)) {
                                            this.isHoldFire = true;
                                        }
                                    }

                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void onPointerDragRightConner(int xScreen, int yScreen, int index) {
    }

    public void onPointerHold(int xScreen, int yScreen, int index) {
        if (!isStopFire) {
            if (CCanvas.currentDialog == null) {
                if (this.state != 5 && !this.falling) {
                    if (CCanvas.isPointerClick[index]) {
                        this.isHoldAngle = false;
                        this.t = 0;
                    }

                    if (!isShooting) {
                        if (!CCanvas.pausemenu.isShow) {
                            int xOffset = 30;
                            int yOffset = 30;
                            if (CCanvas.isPointer(this.crossHairX, this.crossHairY, this.crossHairW, this.crossHairH, index)) {
                                this.isHoldAngle = true;
                            }

                            if (CCanvas.isPointer(GameScr.xF - xOffset / 2, GameScr.yF - yOffset / 2, GameScr.crossHair2.image.getWidth() + xOffset, GameScr.crossHair2.image.getHeight() + yOffset, index)) {
                                this.isHoldFire = true;
                            }

                            if (CCanvas.isPointer(GameScr.xL - GameScr.trai.image.width / 2 - xOffset / 2, GameScr.yL - GameScr.trai.image.height / 2 - yOffset / 2, GameScr.trai.image.getWidth() + xOffset, GameScr.trai.image.getHeight() + yOffset, index) && GameScr.pm.isYourTurn()) {
                                this.move(0);
                                return;
                            }

                            if (CCanvas.isPointer(GameScr.xR - GameScr.phai.image.width / 2 - xOffset / 2, GameScr.yR - GameScr.trai.image.height / 2 - yOffset / 2, GameScr.phai.image.getWidth() + xOffset, GameScr.phai.image.getHeight() + yOffset, index) && GameScr.pm.isYourTurn()) {
                                this.move(2);
                                return;
                            }

                            if (this.isHoldFire) {
                                xOffset = 30;
                                yOffset = 30;
                                if (CCanvas.isPointer(GameScr.xF - xOffset / 2, GameScr.yF - yOffset / 2, GameScr.crossHair2.image.getWidth() + xOffset, GameScr.crossHair2.image.getHeight() + yOffset, index) && !this.falling) {
                                    if (GameScr.pm.isYourTurn()) {
                                        this.holdFire();
                                    }

                                    return;
                                }
                            }

                            boolean range1 = CCanvas.isPointer(0, CScreen.ITEM_HEIGHT * 2, CCanvas.w, CCanvas.h - 2 * CScreen.ITEM_HEIGHT, index);
                            boolean range2 = CCanvas.isPointer(0, this.y + 10 - Camera.y, CCanvas.w, CCanvas.h, index);
                            this.crossHairX -= Camera.x;
                            this.crossHairY -= Camera.y;
                            boolean range3 = CCanvas.isPointer(this.crossHairX, this.crossHairY, 150, 150, index);
                            CRes.out("crssX/crssY/crssW/crssH " + this.crossHairX + "/" + this.crossHairY + "/" + (this.crossHairX + this.crossHairW) + "/" + (this.crossHairY + this.crossHairH));
                            CRes.out("Camera " + Camera.x + "/" + Camera.y);
                            CRes.out("mouse " + xScreen + "/" + yScreen);
                            CRes.out("range3 " + range3);
                            if (!this.isHoldFire && !CCanvas.isPc()) {
                                if (xScreen <= this.x - Camera.x) {
                                    this.look = 0;
                                } else {
                                    this.look = 2;
                                }

                                int dx = this.x - xScreen - Camera.x;
                                int dy = this.y - yScreen - Camera.y;
                                int a = CRes.angle(-dx, dy);
                                CRes.out("a Trc = " + a);
                                if (this.look == 2 && a < this.angleLimit()) {
                                    a = this.angleLimit();
                                }

                                if (this.look == 0 && a > 180 - this.angleLimit()) {
                                    a = 180 - this.angleLimit();
                                }

                                this.angle = a;
                                this.angleUpdate();
                                this.checkAngleForSprite();
                            }
                        }

                    }
                }
            }
        }
    }

    public void onPointerReleased(int xScreen, int yScreen, int index) {
        if (!CCanvas.pausemenu.isShow) {
            if (!GameScr.pm.isYourTurn() || this.state != 5) {
                if (this.lastx != this.x) {
                    this.resetLastUpdateXY(this.x, this.y);
                    GameService.gI().move((short) this.x, (short) this.y);
                }

                this.lastx = (short) this.x;
                if (this.isHoldFire) {
                    this.fire();
                    this.isHoldFire = false;
                } else {
                    this.setState((byte) 0);
                }

            }
        }
    }
    
    /** GunBound-style angle arc (build 9): the angles he may aim at, dotted around his body, and where he aims. */
    static final int ARC_RANGE = 0x9fd8ec, ARC_TICK = 0xffffff, ARC_NEEDLE = 0xffd23c;

    public void drawAngleArc(mGraphics g) {
        if (this != PM.getMyPlayer()) return;
        int size = Math.round(24 * this.grownScale());
        int cx = this.x, cy = this.y - size / 2;
        boolean turned = this.climber() && !this.falling && Climb.down(this.x, this.y) != Climb.SOUTH;
        if (turned) {
            int[] centre = this.aimCentre();
            cx = centre[0];
            cy = centre[1];
        }
        int radius = size / 2 + 26;
        // The range: facing right from his lowest aim up to straight up, facing left from straight up down to it.
        int limit = this.angleLimit();
        int from, to;
        if (turned) {
            from = 0;
            to = 359;
        } else if (this.look == 2) {
            from = limit;
            to = 90;
        } else {
            from = 90;
            to = 180 - limit;
        }
        g.setColor(ARC_RANGE);
        for (int a = from; a <= to; a += 4) {
            g.fillRect(cx + (radius * cos(fixangle(a)) >> 10) - 1, cy - (radius * sin(fixangle(a)) >> 10) - 1, 2, 2, false);
        }
        // Ticks every 30 degrees, a little outside the arc.
        g.setColor(ARC_TICK);
        for (int a = -Math.floorDiv(-from, 30) * 30; a <= to; a += 30) {
            int c = cos(fixangle(a)), s = sin(fixangle(a));
            g.drawLine(cx + ((radius + 1) * c >> 10), cy - ((radius + 1) * s >> 10), cx + ((radius + 5) * c >> 10), cy - ((radius + 5) * s >> 10), false);
        }
        // The needle, from close to his body out past the arc, and the angle at its tip.
        int angle = fixangle(this.angle), c = cos(angle), s = sin(angle);
        g.setColor(ARC_NEEDLE);
        for (int r = size / 2 + 6; r <= radius + 6; r += 2) {
            g.fillRect(cx + (r * c >> 10) - 1, cy - (r * s >> 10) - 1, 3, 3, false);
        }
        int shown = this.angle >= 90 && this.angle <= 270 ? 180 - this.angle : this.angle > 270 ? this.angle - 360 : this.angle;
        Font.smallFontYellow.drawString(g, "" + shown, cx + ((radius + 16) * c >> 10), cy - ((radius + 16) * s >> 10) - 5, 2);
    }

    public void drawKegoc(mGraphics g) {
        if (!this.guideShown()) {
            return;
        }
        // From his body as big as it is, as the server fires it (MapData.shoot).
        int w = Math.round(24 * this.grownScale()), h = w, lent;
        int feetX = this.x, feetY = this.y;
        if (this.climber() && !this.falling && Climb.down(this.x, this.y) != Climb.SOUTH) {
            // Tarzan climbing: from the middle of his turned body, where the server fires it (MapData.shoot).
            int[] centre = this.aimCentre();
            feetX = centre[0];
            feetY = centre[1] + h / 2;
        }
        int x = feetX + ((w - 4) * cos(this.angle) >> 10);
        int y = feetY -(h / 2) - ((h - 4) * sin(this.angle) >> 10);
        int force = this.chillNow > 0 ? this.maxforce * (100 - this.chillNow) / 100 : this.maxforce; // chilled: as the server weakens it
        int vx = force * cos(this.angle) >> 10;
        int vy = -(force * sin(this.angle) >> 10);
        this.guideColor = GUIDE_COLOR;
        switch (this.itemUsed) {
            case 1:
                this.guideColor = GUIDE_COLOR;
                drawBullet(g, 5, x, y, vx, vy, 0, 0, 80);
                break;
            case 6:
                this.guideColor = GUIDE_COLOR;
                drawBullet(g, 6, x, y, vx, vy, this.guideWindX(70), this.guideWindY(70), 90);
                break;
            case 7:
                this.guideColor = GUIDE_COLOR;
                drawBullet(g, 7, x, y, vx, vy, this.guideWindX(70), this.guideWindY(70), 80);
                break;
            case 8:
                this.guideColor = GUIDE_COLOR;
                drawBullet(g, 4, x, y, vx, vy, 0, 0, 80);
                break;
            case 9:
                this.guideColor = GUIDE_COLOR;
                drawBullet(g, 8, x, y, vx, vy, this.guideWindX(70), this.guideWindY(70), 70);
                break;
            case 11:
                this.guideColor = GUIDE_COLOR;
                drawBullet(g, 16, x, y, vx, vy, 0, 0, 100);
                break;
            case 16:
                this.guideColor = GUIDE_COLOR;
                drawBullet(g, 14, x, y, vx, vy, this.guideWindX(10), this.guideWindY(10), 50);
                break;
            case 17:
                this.guideColor = GUIDE_COLOR;
                drawBullet(g, 13, x, y, vx, vy, this.guideWindX(50), this.guideWindY(50), 120);
                break;
            case 18:
                this.guideColor = GUIDE_COLOR;
                //drawBullet(g, 22, x, y, vx, vy, 0, 0, 0);
                break;
            case 19:
                this.guideColor = GUIDE_COLOR;
                drawBullet(g, 26, x, y, vx, vy, this.guideWindX(30), this.guideWindY(30), 60);
                break;
            case 20:
                this.guideColor = GUIDE_COLOR;
                drawBullet(g, 25, x, y, vx, vy, 0, 0, -50);
                break;
            case 21:
                this.guideColor = GUIDE_COLOR;
                drawBullet(g, 23, x, y, vx, vy, this.guideWindX(20), this.guideWindY(20), 100);
                break;
            case 22:
                this.guideColor = GUIDE_COLOR;
                drawBullet(g, 28, x, y, vx, vy, 0, 0, 20);
                break;
            case 23:
                this.guideColor = GUIDE_COLOR;
                drawBullet(g, 30, x, y, 0, 2, 0, 0, 0);
                break;
            case 24:
                this.guideColor = GUIDE_COLOR;
                //drawBullet(g, 50, x, y, 0, 0, 0, 0, 0);
                break;
            case 25:
                this.guideColor = GUIDE_COLOR;
                drawBullet(g, 51, x, y, vx, vy, this.guideWindX(5), this.guideWindY(5), 60);
                break;
            case 26:
                this.guideColor = GUIDE_COLOR;
                drawBullet(g, 52, x, y, vx, vy, this.guideWindX(10), this.guideWindY(10), 100);
                break;
            case 27:
                this.guideColor = GUIDE_COLOR;
                //drawBullet(g, 53, x, y, 0, 0, 0, 0, 0);
                break;
            case 28:
                this.guideColor = GUIDE_COLOR;
                drawBullet(g, 54, x, y, vx, vy, 0, 0, 80);
                break;
            case 29:
                this.guideColor = GUIDE_COLOR;
                drawBullet(g, 55, x, y, vx, vy, this.guideWindX(6), this.guideWindY(6), 60);
                break;
            case 30:
                this.guideColor = GUIDE_COLOR;
                lent = 3; 
                int step = 5;
                for (int j = 0; j < lent; j++) {
                    int i = this.angle + (j - lent / 2) * step;
                    x = this.x + ((w - 4) * cos(i) >> 10);
                    y = this.y - (h / 2) - ((h - 4) * sin(i) >> 10);
                    vx = force * cos(i) >> 10;
                    vy = -(force * sin(i) >> 10);
                    drawBullet(g, 56, x, y, vx, vy, this.guideWindX(70), this.guideWindY(70), 70);
                }
                break;
            case 31:
                this.guideColor = GUIDE_COLOR;
                drawBullet(g, 57, x, y, vx, vy, 0, 0, 120);
                break;

            default:
                switch (this.gun) {
                    case 0:
                        drawBullet(g, 0, x, y, vx, vy, this.guideWindX(80) , this.guideWindY(80), 100);
                        break;
                    case 1:
                        drawBullet(g, 1, x, y, vx, vy, this.guideWindX(50) , this.guideWindY(50), 50);
                        break;
                    case 2:
                        lent = this.itemUsed == 100 ? 6 : 3;
                        int startAngle = this.angle - (lent / 2) * 5;
                        for (int i = 0; i < lent; i++) {
                            int currentAngle = startAngle + i * 5;
                            x = this.x + ((w - 4) * cos(currentAngle) >> 10);
                            y = this.y -(h / 2) - ((h - 4) * sin(currentAngle) >> 10);
                            vx = force * cos(currentAngle) >> 10;
                            vy = -(force * sin(currentAngle) >> 10);
                            drawBullet(g, 2, x, y, vx, vy, this.guideWindX(80), this.guideWindY(80), 60);
                        }
                        break;
                    case 3:
                        for (int i = this.angle - 6, j = 0; j < 4; i += 4, j++) {
                            x = this.x + ((w - 4) * cos(i) >> 10);
                            y = this.y -(h / 2) - ((h - 4) * sin(i) >> 10);
                            vx = force * cos(i) >> 10;
                            vy = -(force * sin(i) >> 10);
                            drawBullet(g, 9, x, y, vx, vy, this.guideWindX(40), this.guideWindY(40), 90);
                        }
                        break;
                    case 4:
                        if (this.worldWarPow()) {
                            drawBullet(g, 4, x, y, vx, vy, 0, 0, 80); // the WorldWar flare: no wind, like the B52's
                        } else {
                            drawBullet(g, 10, x, y, vx, vy, this.guideWindX(50) , this.guideWindY(50), 80);
                        }
                        break;
                    case 5:
                        if (this.laserSeedPow()) {
                            drawBullet(g, 4, x, y, vx, vy, 0, 0, 80); // Granos's seed: no wind, like the flare
                        } else {
                            drawBullet(g, 11, x, y, vx, vy, this.guideWindX(30) , this.guideWindY(30), 90);
                        }
                        break;
                    case 6:
                        drawBullet(g, 19, x, y, vx, vy, this.guideWindX(20) , this.guideWindY(20), 50);
                        break;
                    case 7:
                        // Tarzan's set passive: the wind no longer moves his boomerang.
                        int boomerangWind = this.pactTarzan() ? 0 : 10;
                        drawBullet(g, 21, x, y, vx, vy, this.guideWindX(boomerangWind) , this.guideWindY(boomerangWind), 50);
                        break;
                    case 8:
                        drawBullet(g, 17, x, y, vx, vy, this.guideWindX(30) , this.guideWindY(30), 100);
                        break;
                    case 9:
                        vx = (force + 5) * cos(this.angle) >> 10;
                        vy = -((force + 5) * sin(this.angle) >> 10);
                        drawBullet(g, 49, x, y, vx, vy, this.guideWindX(40) , this.guideWindY(40), 70);
                        break;
                }
                break;
        }
    }
    
    
    /** Shots left with item 36 "Chỉ kẻ đầu": the aim guide follows the wind too, right where the shot will fly. */
    public static int headGuideShots;
    /** Colour of the guide path being drawn. */
    private int guideColor;
    /** Map pixels between two dots of the aim guide, and the path length over which they shrink to the smallest. */
    private static final int GUIDE_GAP = 14, GUIDE_FADE = 600;
    /** Light sea blue of the guide, and the paler blue of its branches (Chicky's egg, Apache's hammers, Magenta's laser). */
    private static final int GUIDE_COLOR = 0x7fd3f7, GUIDE_BRANCH = 0xc4ecff;
    private static mImage guideDot;
    private static com.badlogic.gdx.graphics.Color guideTint, guideNumberTint;

    /**
     * LAN aim guide (build 7): none, for everyone alike, unless item 36 "Chỉ kẻ đầu" is on (this turn and the next):
     * then round dots, big at the gun and smaller along the way (like GunPow), over the whole path the shot will fly,
     * wind included, until it meets ground or a player in sight.
     */
    public boolean guideShown() {
        return headGuideShots > 0 && this == PM.getMyPlayer();
    }

    /** Wind a guide path feels: share percent of the real wind, as the server's bullet does. */
    private int guideWindX(int share) {
        return GameScr.windx * share / 100;
    }

    private int guideWindY(int share) {
        return GameScr.windy * share / 100;
    }

    private void drawBullet(mGraphics g, int bulletId, int x, int y, int vx, int vy, int ax100, int ay100, int g100) {
        this.guidePath(g, bulletId, x, y, vx, vy, ax100, ay100, g100, 0);
    }

    /**
     * One path of the aim guide, stepped frame by frame like the server's bullets (Bullet.nextXY), with a dot every
     * GUIDE_GAP pixels; walked is how far the guide has come before this path starts (the dots keep shrinking).
     * Chicky's egg, Apache's three hammers and Magenta's laser branch off where the shot would make them.
     */
    private void guidePath(mGraphics g, int bulletId, int x, int y, int vx, int vy, int ax100, int ay100, int g100, int walked) {
        int vyTemp2 = 0;
        int vxTemp = 0;
        int vyTemp = 0;
        int x0 = x;
        int y0 = y;
        int xold = x;
        int yold = y;
        int nextDot = walked;
        byte quayLai = -1;
        boolean addTZ = (vx <= 0);
        for (int frame = 0; frame < 1000; frame++, xold = x, yold = y) {
            if ((x < -100) || (x > MM.mapWidth + 100) || (y > MM.mapHeight + 100)) {
                return;
            }
            x = xold + vx;
            y = yold + vy;
            int steps = Math.max(Math.abs(x - xold), Math.abs(y - yold));
            float stepLength = steps == 0 ? 0f : (float) Math.sqrt((double) (x - xold) * (x - xold) + (double) (y - yold) * (y - yold)) / steps;
            for (int s = 1; s <= steps; s++) {
                int px = xold + (x - xold) * s / steps;
                int py = yold + (y - yold) * s / steps;
                walked += Math.round(stepLength);
                if ((s % 3 == 0 || s == steps) && this.guideBlocked(px, py)) { // every 3 px is close enough to see
                    this.drawGuideDot(g, px, py, walked);
                    return;
                }
                if (walked >= nextDot) {
                    this.drawGuideDot(g, px, py, walked);
                    nextDot = walked + GUIDE_GAP;
                }
            }
            if ((bulletId == 19 || bulletId == 17) && frame < 30 && (frame + 1) % 5 == 0) {
                // Chicky's and Apache's step numbers (for "lm" force 2), every 5 steps and faint so the guide stays light.
                if (guideNumberTint == null) guideNumberTint = new com.badlogic.gdx.graphics.Color(1f, 1f, 1f, 0.5f);
                g.tint = guideNumberTint;
                Font.normalGFont.drawString(g, String.valueOf(frame + 1), x, y + 5, 65);
                g.tint = null;
            }
            vxTemp  += Math.abs(ax100);
            vyTemp  += Math.abs(ay100);
            vyTemp2 += g100;
            if(Math.abs(vxTemp) >= 100) {
                if(ax100 > 0) {
                    vx += vxTemp / 100;
                } else {
                    vx -= vxTemp / 100;
                }
                vxTemp %= 100;
            }
            if(Math.abs(vyTemp) >= 100) {
                if(ay100 > 0) {
                    vy += vyTemp / 100;
                } else {
                    vy -= vyTemp / 100;
                }
                vyTemp %= 100;
            }
            if(Math.abs(vyTemp2) >= 100) {
                vy += vyTemp2 / 100;
                vyTemp2 %= 100;
            }
            if (bulletId == 17 && frame + 1 == this.maxforce2) {
                int color = this.guideColor;
                this.guideColor = GUIDE_BRANCH;
                int ang = this.angle + fixangle(angle(PM.getMyPlayer().x - xold, PM.getMyPlayer().y - yold));
                if (this.angle < 90) {
                    ang = 180 - ang;
                }
                for (int i = ang - 15, j = 0; j < 3; j++, i += 15) {
                    int hx = xold + (20 * cos(i) >> 10);
                    int hy = yold - 12 - (20 * sin(i) >> 10);
                    int hvx = this.maxforce * cos(i) >> 11;
                    int hvy = -(this.maxforce * sin(i) >> 11);
                    this.guidePath(g, 18, hx, hy, hvx, hvy, ax100, ay100, g100, walked);
                }
                this.guideColor = color;
                return;
            }
            if (bulletId == 21) {
                if(quayLai == 0) {
                    if(addTZ) {
                        vx += 1;
                    } else {
                        vx -=1;
                    }
                    quayLai = 1;
                } else if(quayLai == 1) {
                    if(addTZ) {
                        vx += 2;
                    } else {
                        vx -= 2;
                    }
                } else if(vy > 0) {
                    quayLai = (byte) (quayLai + 1);
                }
            }
            if(vy >= 0 && bulletId == 49) {
                int ang = angle(x - x0, y0 - y);
                vx = (this.maxforce * cos(ang) >> 10);
                vy = (this.maxforce * sin(ang) >> 10);
                if (vx == 0 && vy == 0) {
                    // As the server's MirrorBullet for force 0 or 1.
                    vx = 15 * cos(ang) >> 10;
                    vy = 15 * sin(ang) >> 10;
                }
                if (vx != 0) {
                    while (Math.abs(vx) < 15) {
                        vx += vx;
                        vy += vy;
                    }
                }
                int color = this.guideColor;
                this.guideColor = GUIDE_BRANCH;
                this.guidePath(g, -1, x, y, vx, vy, 0, 0, 0, walked);
                this.guideColor = color;
                return;
            }
            if (bulletId == 19 && frame + 1 == this.maxforce2) {
                int color = this.guideColor;
                this.guideColor = GUIDE_BRANCH;
                this.guidePath(g, 20, x, y + 8, 0, 0, ax100 / 2, ay100 / 2, 30, walked);
                this.guideColor = color;
            }
        }
    }

    /** The guide stops on ground and on a player in sight (never on a hidden one: it must not give him away). */
    private boolean guideBlocked(int x, int y) {
        if (GameScr.mm != null && GameScr.mm.isLand(x, y)) return true;
        if (PM.p == null) return false;
        for (CPlayer o : PM.p) {
            if (o == null || o == this || o.getState() == 5 || o.isInvisible) continue;
            if (x >= o.x - 12 && x < o.x + 12 && y >= o.y - 24 && y < o.y) return true;
        }
        return false;
    }

    /** One soft dot of the guide in guideColor, smaller and fainter the farther along the path (walked pixels). */
    private void drawGuideDot(mGraphics g, int x, int y, int walked) {
        if (guideDot == null) {
            guideDot = mImage.createImage("/gui/aimdot.png");
            guideTint = new com.badlogic.gdx.graphics.Color();
        }
        float scale = Math.max(0.4f, 0.8f - 0.4f * walked / GUIDE_FADE);
        com.badlogic.gdx.graphics.Color.rgb888ToColor(guideTint, this.guideColor);
        guideTint.a = Math.max(0.25f, 0.9f * (float) Math.exp(-walked / 700.0)); // fainter toward the far end, never gone
        g.tint = guideTint;
        g.drawScale = scale;
        g.drawImage(guideDot, x, y, 3, false);
        g.drawScale = 1f;
        g.tint = null;
    }
}
