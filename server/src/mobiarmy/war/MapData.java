package mobiarmy.war;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import javax.imageio.ImageIO;
import mobiarmy.Util;
import mobiarmy.server.GameData;
import mobiarmy.server.Item;
import mobiarmy.server.Map;
import mobiarmy.server.Server;
import static mobiarmy.server.Text.__;
import mobiarmy.server.User;
import mobiarmy.war.Boss.BigBoom;
import mobiarmy.war.Boss.GiftBox;
import mobiarmy.war.Boss.GiftBox2;
import mobiarmy.war.Boss.Robot;
import mobiarmy.war.Boss.RobotSpider;
import mobiarmy.war.Boss.SmallBoom;
import mobiarmy.war.Boss.Trex;
import mobiarmy.war.Boss.Ufo;
import mobiarmy.war.Boss.bullet.Bullet;

/**
 *
 * @author Văn Tú
 */
public class MapData {
    
    public RoomWait roomWait;
    public Map map;
    public short width;
    public short height;
    public int mapId;
    public ArrayList<Image> data;
    public ArrayList<Tornado> tornados;
    public Player[] players;
    public long timeWait;
    public int windX;
    public int windY;
    public int nTurn;
    public byte indexBoss;
    public boolean isTurn;
    public boolean isTurnPlayer;
    public int indexTurnPlayer;
    public int indexTurnBoss;
    public boolean isFightBoss;
    public int typeComplete;
    public long completeWar = -1;
    public int countTimeBomb;
    public ArrayList<TimeBomb> timeBombs;
    public TimeBomb timeBomb;
    public ArrayList<int[]> bossAdds;
    public int nGift;
    public int timeTurn;
    public int maxSecondTurn = 25;
    public int teamPoint;
    public int team = -1;
    public boolean isWar;
    private int wager;
    public int nPlayer;
    public long timeUntilAction2;
    public boolean disableLuck;
    /** RoomRules picked by the room owner, fixed when the match starts. */
    public byte rules = RoomRules.FREE;
    /** SkillBalance damage for this match (balanced rooms). */
    public boolean skillRules;
    public ArrayList<Player> choses = null;

    public MapData(RoomWait roomWait, byte mapID) throws IOException {
        this.roomWait = roomWait;
        this.map = Map.get(this.mapId = mapID);
        this.data = new ArrayList<>();
        this.tornados = new ArrayList<>();
        this.timeBombs = new ArrayList<>();
        this.width = this.map.data.width;
        this.height =  this.map.data.height;
        for (Map.Brick brick : map.data.bricks) {
            BufferedImage img = ImageIO.read(new File("res/icon/map/"+brick.id+".png"));
            int[] argb = new int[img.getWidth() * img.getHeight()];
            img.getRGB(0, 0, img.getWidth(), img.getHeight(), argb, 0, img.getWidth());
            Image image = new Image(brick.id, brick.x, brick.y, argb, (short)img.getWidth(), (short)img.getHeight(), !isNotColision(brick.id));
            this.data.add(image);
        }
        this.players = new Player[Room.MAX_E_PLAYER];
        this.indexTurnPlayer = -1;
        this.indexTurnBoss = -1;
        this.windX = 0;
        this.windY = 0;
        this.nTurn = 0;
        this.indexBoss = Room.INDEX_START;
        this.typeComplete = -1;
        this.nGift = 0;
        this.countTimeBomb = 0;
        this.isFightBoss = false;
        this.bossAdds = new ArrayList<>();
        this.nPlayer = 0;
    }
    
    public void startGame(User[] users) {
        this.wager = this.roomWait.getWager();
        this.roomWait.started = true;
        this.isWar = true;
        this.rules = this.roomWait.rules;
        this.skillRules = this.rules == RoomRules.BALANCED;
        //Đặt vị trí của người chơi
        ArrayList<Map.Point> points = new ArrayList<>();
        points.addAll(Arrays.asList(this.map.data.points));
        int teaPs[] = new int[10];
        int teams[] = new int[10];
        for (User user : users) {
            if (user != null) {
                int []  iArr = RoomRules.battleStats(user, this.rules);
                Player player = new Player(this, user, user.name, user.glass().id, user.glass().level, iArr[0], iArr[1], iArr[2], iArr[3], iArr[4], 24, 24, user.setItems);
                player.setTier = this.rules == RoomRules.BASE ? 0 : SetPassive.tier(user, user.glass().id);
                player.theluc = SetPassive.steps(player); // Tarzan's passive walks farther
                player.baseAtt = user.glass().att;
                player.hiddenAtt = SetPassive.hiddenAtt(user, user.glass().id);
                player.isBoss = false;
                player.isFly = false;
                player.team = this.roomWait.type == 5 ? 3 : user.team;
                //vi tri
                Map.Point point = points.remove(Util.nextInt(points.size()));
                player.x = point.x;
                player.y = point.y;
                //tui dung item
                if (user.setItems[4] != -1) {
                    user.addItem(12, -1);
                }
                if (user.setItems[5] != -1) {
                    user.addItem(13, -1);
                }
                if (user.setItems[6] != -1) {
                    user.addItem(14, -1);
                }
                if (user.setItems[7] != -1) {
                    user.addItem(15, -1);
                }
                //Cộng điểm đđ
                teaPs[player.team] += player.teaP;
                teams[player.team]++;
                this.players[player.index = user.index] = player;
                this.nPlayer++;
            }
        }
        for (int i = 0; i < teaPs.length; i++) {
            teaPs[i] /= 200;
        }
        //Cộng điểm đồng đội
        for (Player player : this.players) {
            if (player != null && teams[player.team] > 1) {
                player.hp = player.hpMax = player.hpMax + (teaPs[player.team] * 10);//hp
                player.att = player.att + (teaPs[player.team] / 3);//att
                player.def = player.def + (teaPs[player.team] * 10);//def
                player.luck = player.luck + (teaPs[player.team] * 10);//luck
            }
        }
        
        for (Player player : this.players) {
            if (player != null && player.user != null && player.user.session != null) {
                player.user.session.sessionHandler.loadInfoMap(this);
            }
        }
        this.setBoss();
        if (!this.isFightBoss) {
            this.nGift = 2;
        }
        this.isTurn = true;
        this.addTimeUntilAction(1000);
    }
    
    public class Image {
        
        public int id;
        public int argb[];
        public int width;
        public int height;
        public int x;
        public int y;
        public boolean canColision;

        public Image(int id, int x, int y, int[] data, int w, int h, boolean show) {
            this.x = x;
            this.y = y;
            this.id = id;
            this.canColision = show;
            this.setData(data, w, h);
        }

        public final void setData(int[] data, int W, int H) {
            this.width = W;
            this.height = H;
            this.argb = new int[data.length];
            System.arraycopy(data, 0, this.argb, 0, data.length);
        }

        public boolean inRegion(int x, int y, int x0, int y0, int w, int h) {
            return x >= x0 && x < x0 + w && y >= y0 && y < y0 + h;
        }
        
        public boolean isNotAlpha(int rgb) {
            return (rgb >> 24) != 0;
        }

        public boolean isCollision(int x, int y) {
            return (this.inRegion(x, y, this.x, this.y, this.width, this.height) && this.isNotAlpha(this.getARGB(x - this.x, y - this.y)));
        }
        
        public int getARGB(int x, int y) {
            return this.argb[y * this.width + x];
        }
        
        public boolean intersecRegions(int x1, int y1, int w1, int h1, int x2, int y2, int w2, int h2) {
            return x1 + w1 >= x2 && x1 <= x2 + w2 && y1 + h1 >= y2 && y1 <= y2 + h2;
        }
        
        public void collision(int bX, int bY, int w, int h, int[] data) {
            if(!this.canColision || !this.intersecRegions(bX - w / 2, bY - h / 2, w, h, this.x, this.y, this.width, this.height)) {
                return;
            }
            bX -= this.x + w / 2;
            bY -= this.y + h / 2;
            for(int i = 0; i < h; i++) {
                for(int j = 0; j < w; j++) {
                    if(this.inRegion(bX + j, bY + i, 0, 0, this.width, this.height)) {
                        if(data[i * w + j] == 0xffff0000 && this.isNotAlpha(this.getARGB(bX + j, bY + i))) {
                            this.argb[(bY + i) * this.width + bX + j] = 0xff000000;
                        } else if(data[i * w + j] == 0xff000000) {
                            this.argb[(bY + i) * this.width + bX + j] = 0;
                        }
                    }
                }
            }
        }
        
    }
    
    public static boolean isNotColision(int id) {
        short[] idNotColision = new short[]{70, 71, 73, 74, 75, 77, 78, 79, 97};
        for(int i = 0; i < idNotColision.length; i++) {
            if(id == idNotColision[i]) {
                return true;
            }
        }
        return false;
    }

    public boolean isCollisionMap(int x, int y) {
        for (int i = 0; i < this.data.size(); i++) {
            if (this.data.get(i).isCollision(x, y)) {
                return true;
            }
        }
        return false;
    }
    
    public void makeHole(int x, int y, int bulletType) {
        this.makeHole(x, y, bulletType, 100);
    }

    /** Ground under a King Kong with his set passive; refreshed with every shot and sent to the clients with it. */
    public java.util.List<short[]> protectedGround = new ArrayList<>();
    private static final java.util.Map<Integer, int[][]> SCALED_HOLES = new java.util.concurrent.ConcurrentHashMap<>();

    /** scale: hole size in percent (SetPassive.holeScale). */
    public void makeHole(int x, int y, int bulletType, int scale) {
        int type = GameData.getHoleType(bulletType);
        int[][] hole = SCALED_HOLES.computeIfAbsent(type * 10000 + scale, key -> {
            int w = GameData.holeIMask[type].getWidth();
            int h = GameData.holeIMask[type].getHeight();
            int[] rgb = new int[w * h];
            GameData.holeIMask[type].getRGB(0, 0, w, h, rgb, 0, w);
            if (scale == 100) return new int[][] {rgb, {w, h}};
            int[] size = new int[2];
            int[] scaled = SetPassive.scaleMask(rgb, w, h, scale, size);
            return new int[][] {scaled, size};
        });
        int[] rgb = hole[0];
        int w = hole[1][0];
        int h = hole[1][1];
        if (!this.protectedGround.isEmpty()) {
            rgb = rgb.clone();
            for (int i = 0; i < h; i++) {
                for (int j = 0; j < w; j++) {
                    int px = x - w / 2 + j, py = y - h / 2 + i;
                    for (short[] r : this.protectedGround) {
                        if (px >= r[0] && px < r[0] + r[2] && py >= r[1] && py < r[1] + r[3]) {
                            rgb[i * w + j] = 0; // neither dig nor blacken this pixel
                            break;
                        }
                    }
                }
            }
        }
        int num = 0;
        while(num < this.data.size()) {
            this.data.get(num).collision(x, y, w, h, rgb);
            num++;
        }
//        this.saveImageMap();
    }
    
    public BufferedImage image;
            
    public void initImage() {
        image = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_RGB);
        //map
        for (int i = 0; i < this.data.size(); i++) {
            if (this.data.get(i).x >= 0 && this.data.get(i).x + this.data.get(i).width <= this.width && this.data.get(i).y >= 0 && this.data.get(i).y + this.data.get(i).height <= this.height) {
                image.setRGB(this.data.get(i).x, this.data.get(i).y, this.data.get(i).width, this.data.get(i).height, this.data.get(i).argb, 0, this.data.get(i).width);
            } else {
            }
        }
        for (int i = 0; i < this.width; i++) {
            for (int j = 0; j < this.height; j++) {
                if (image.getRGB(i, j) == -16777216 && image.getRGB(i, j) != 0xffff0000) {
                    image.setRGB(i, j, 0xFFFFFF);
                }
            }
        }
    }
    
    public void saveImageMap() {
        initImage();
        try {
            ImageIO.write(image, "png", new File(this.mapId+".png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public boolean updateComplete() {
        //Neu la danh boss
        if (this.isFightBoss) {
            int nBossLive = 0;
            int nPlayerLive = 0;
            for (Player player : this.players) {
                if (player != null && !player.isDie && player.isFaction) {
                    if (player.isBoss) {
                        nBossLive++;
                    } else {
                        nPlayerLive++;
                    }
                }
            }
            if (nBossLive == 0 || nPlayerLive == 0) {
                if (nPlayerLive == 0) {
                    this.typeComplete = 0;
                } else {
                    this.typeComplete = 4;
                }
            }
        } else {
            int nReadLive = 0;
            int nBlueLive = 0;
            for (Player player : this.players) {
                if (player != null && !player.isDie && player.isFaction) {
                    if (player.team == 1) {
                        nBlueLive++;
                    }
                    if (player.team == 2) {
                        nReadLive++;
                    }
                }
            }
            if (nBlueLive == 0 || nReadLive == 0) {
                if (nBlueLive > 0) {
                    this.typeComplete = 1;
                } else if (nReadLive > 0) {
                    this.typeComplete = 2;
                } else if (nBlueLive == nReadLive) {
                    this.typeComplete = 3;
                }
            }
        }
        if (this.typeComplete != -1) {
            this.endTheWar();
            return true;
        }
        return false;
    }
    
    public void endTheWar() {
        if (!this.isWar || this.typeComplete == -1) return;
        this.isWar = false;
        String matchKey = System.currentTimeMillis() + "-" + System.identityHashCode(this);
        int fighters = 0;
        for (Player player : this.players) if (player != null && player.user != null) fighters++;
        for (Player player : this.players) {
            if (player != null && player.user != null) {
                int result = this.typeComplete == 3 ? 0
                        : (this.typeComplete == 4 || this.typeComplete == player.team ? 1 : -1);
                // LAN: win rates per character and set feed the SetPassive balance (admin page "Cân bằng").
                if (!this.isFightBoss) {
                    Server.dbManager.update("INSERT INTO lan_match_result (glass_id, set_tier, rules, players, result) VALUES (?, ?, ?, ?, ?)",
                            player.glassID, player.setTier, (int) this.rules, fighters, result);
                }
                if (!this.isFightBoss) {
                    Server.dbManager.update("INSERT INTO lan_match_player (match_key, map_id, user_id, name, glass_id, team, result,"
                            + " dealt, taken, healed, kills) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", matchKey, this.mapId,
                            player.user.id, player.user.name, player.glassID, player.team, result,
                            player.statDealt, player.statTaken, player.statHealed, player.statKills);
                }
                long before = player.user.xu;
                player.user.addXu(result * this.wager, player.user.session != null);
                if (player.user.session != null) {
                    player.user.session.sessionHandler.endTheWar(result, (int) (player.user.xu - before));
                }
                player.user.ready = false;
            }
        }
        Server.requestSave();
        //reset map
        this.isTurn = false;
        this.isWar = false;
        this.indexTurnPlayer = -1;
        this.indexTurnBoss = -1;
        this.windX = 0;
        this.windY = 0;
        this.nTurn = 0;
        this.indexBoss = Room.INDEX_START;
        this.typeComplete = -1;
        this.data.clear();
        this.tornados.clear();
        this.timeBombs.clear();
        this.pendingEggs.clear();
        for (int i = 0; i  <this.players.length; i++) {
            this.players[i] = null;
        }
        this.completeWar = System.currentTimeMillis() + 10000L;
        this.nGift = 0;
        this.countTimeBomb = 0;
        this.bossAdds.clear();
        this.roomWait.endGame();
    }
    
    public void cancelInvisible(int index) {
        for (Player player : this.players) {
            if (player != null && player.user != null && player.user.session != null) {
                player.user.session.sessionHandler.cancelInvisible(index);
            }
        }
    }
    
    public void blind(int index, int type) {
        for (Player player : this.players) {
            if (player != null && player.user != null && player.user.session != null) {
                player.user.session.sessionHandler.blind(index, type);
            }
        }
    }
    
    public void freeze(int index, int type) {
        for (Player player : this.players) {
            if (player != null && player.user != null && player.user.session != null) {
                player.user.session.sessionHandler.freeze(index, type);
            }
        }
    }
    
    public void poison(int index) {
        for (Player player : this.players) {
            if (player != null && player.user != null && player.user.session != null) {
                player.user.session.sessionHandler.poison(index);
            }
        }
    }
    
    public void updateAngry(int index, int angry) {
        for (Player player : this.players) {
            if (player != null && player.user != null && player.user.session != null) {
                player.user.session.sessionHandler.updateAngry(index, angry);
            }
        }
    }
    
    public int getTurn() {
        if (this.isTurnPlayer) {
            return this.indexTurnPlayer;
        } else {
            return this.indexTurnBoss;
        }
    }
    
    public void updateHP(int index, int hp, int pixel) {
        for (Player player : this.players) {
            if (player != null && player.user != null && player.user.session != null) {
                player.user.session.sessionHandler.updateHP(index, hp, pixel);
            }
        }
    }
    
    public Player findPlayer(int userID) {
        for (Player player : this.players) {
            if (player != null && player.userID == userID) {
                return player;
            }
        }
        return null;
    }
    
    public void addTimeBomb(int id, int x, int y) {
        for (Player player : this.players) {
            if (player != null && player.user != null && player.user.session != null) {
                player.user.session.sessionHandler.addTimeBomb(id, x, y);
            }
        }
    }

    /** Chicky's Easter egg appears, with whose it is (the clients colour it like her chicken). */
    void addTimeBomb(int id, int x, int y, int owner) {
        for (Player player : this.players) {
            if (player != null && player.user != null && player.user.session != null) {
                player.user.session.sessionHandler.addTimeBomb(id, x, y, owner);
            }
        }
    }
    
    public Player[] addBoss(Player... players) {
        for (Player boss : players) {
            boss.mapData = this;
            this.players[boss.index = this.indexBoss++] = boss;
        }
        for (Player player : this.players) {
            if (player != null && player.user != null && player.user.session != null) {
                player.user.session.sessionHandler.addPlayer(players);
            }
        }
        return players;
    }

    public void tornadoTurnUpd() {
        if (!this.tornados.isEmpty()) {
            for (int i = this.tornados.size() - 1; i >= 0; i--) {
                this.tornados.get(i).update();
            }
        }
    }
    
    public void explodeTimeBomb(int id) {
        for (Player player : this.players) {
            if (player != null && player.user != null && player.user.session != null) {
                player.user.session.sessionHandler.explodeTimeBomb(id);
            }
        }
    }
    
    public void explodeTimeBomb(TimeBomb timeBomb) {
        this.explodeTimeBomb(timeBomb.id);
        this.timeBombs.remove(timeBomb);
        this.makeHole(timeBomb.x, timeBomb.y, 57);
        
        for (Player player : this.players) {
            if (player != null && !player.isDie) {
                if (player.isCollision(timeBomb.x, timeBomb.y, timeBomb.radius)) {
                    player.attAffect = player.calcAtt(timeBomb.x, timeBomb.y, timeBomb.radius, timeBomb.att);
                }
            }
        }
        this.updateAffect(timeBomb.leadIndex);
    }
    
    public void timeBombTurn() {
        //add
        if (this.timeBomb != null) {
            this.addTimeBomb(this.timeBomb.id, this.timeBomb.x, this.timeBomb.y);
            this.timeBomb = null;
        }
        //update xy
        if (!this.timeBombs.isEmpty()) {
            for (int i = this.timeBombs.size() - 1; i >= 0; i--) {
                this.timeBombs.get(i).update();
            }
        }
        //add Chicky's eggs laid during the last turn
        for (TimeBomb egg : this.pendingEggs) {
            this.timeBombs.add(egg);
            this.addTimeBomb(egg.id, egg.x, egg.y, egg.leadIndex);
        }
        this.pendingEggs.clear();
        //no
        for (int i = this.timeBombs.size() - 1; i >= 0; i--) {
            TimeBomb bomb = this.timeBombs.get(i);
            bomb.count--;
            if (bomb.count == 0) {
                if (bomb.egg) this.hatchEgg(bomb);
                else this.explodeTimeBomb(bomb);
            }
        }
    }

    /** Chicky's eggs laid this turn; they appear at the start of the next one (like the time bomb item). */
    ArrayList<TimeBomb> pendingEggs = new ArrayList<>();
    private int countEgg;

    /** One of this Chicky's Easter eggs is still waiting to hatch: she lays the next only after it has. */
    boolean eggWaiting(Player owner) {
        for (TimeBomb egg : this.pendingEggs) if (egg.leadIndex == owner.index) return true;
        for (TimeBomb egg : this.timeBombs) if (egg.egg && egg.leadIndex == owner.index) return true;
        return false;
    }

    /**
     * Chicky's "Trứng Phục sinh": an egg at the target's feet that hatches after SetPassive.CHICKY_EGG_ROUNDS rounds;
     * it keeps (in att) the damage her shot that laid it dealt.
     */
    void layEgg(Player owner, Player target, int dealt) {
        int fighters = 0;
        for (Player p : this.players) if (p != null && !p.isDie && p.isTurn) fighters++;
        TimeBomb egg = new TimeBomb(this, 100 + this.countEgg++ % 27, owner.index, dealt, target.x, target.y,
                SetPassive.CHICKY_EGG_ROUNDS * Math.max(1, fighters), SetPassive.CHICKY_EGG_RADIUS);
        egg.egg = true;
        this.pendingEggs.add(egg);
    }

    /**
     * The egg hatches. The clients see it go off first and only then the HP it changes: Chicky and her whole team heal
     * a quarter of the damage it kept, her opponents within reach lose half as true damage. It digs no ground.
     */
    void hatchEgg(TimeBomb egg) {
        this.timeBombs.remove(egg);
        for (Player player : this.players) {
            if (player != null && player.user != null && player.user.session != null) {
                player.user.session.sessionHandler.hatchEgg(egg.id);
            }
        }
        Player owner = egg.leadIndex >= 0 && egg.leadIndex < this.players.length ? this.players[egg.leadIndex] : null;
        if (owner != null) {
            int heal = SetPassive.eggShare(owner, egg.att, SetPassive.CHICKY_EGG_HEAL);
            int damage = SetPassive.eggShare(owner, egg.att, SetPassive.CHICKY_EGG_DAMAGE);
            for (Player p : this.players) {
                if (p == null || p.isDie) continue;
                if (SetPassive.enemies(owner, p)) {
                    if (!SetPassive.eggReach(p, egg.x, egg.y)) continue;
                    int before = p.hp;
                    p.updateHP(-damage);
                    owner.statDealt += before - p.hp;
                    if (p.isDie) owner.statKills++;
                    if (p.hp < before) SetPassive.hurt(p);
                } else {
                    p.updateHP(heal); // Chicky herself and her team, wherever they stand
                }
                this.updateHP(p.index, p.hp, p.pixel);
                this.updateAngry(p.index, p.angry);
            }
        }
        this.addTimeUntilAction(2000);
    }
    
    public boolean isHaveBoss(int glassID) {
        for (Player player : this.players) {
            if (player != null && !player.isDie && player.isFaction && player.isBoss && (glassID == -1 || player.glassID == glassID)) {
                return true;
            }
        }
        return false;
    }
    
    public boolean isHavePlayer(int glassID) {
        for (Player player : this.players) {
            if (player != null && !player.isDie && player.isFaction && !player.isBoss && (glassID == -1 || player.glassID == glassID)) {
                return true;
            }
        }
        return false;
    }
    
    public void setTurn(int turn) {
        for (Player player : this.players) {
            if (player != null && player.user != null && player.user.session != null) {
                player.user.session.sessionHandler.setTurn(turn);
            }
        }
    }
    
    /** Strongest wind either way. */
    public static final int WIND_MAX = 70;
    /** Most the wind changes from one round to the next. */
    public static final int WIND_DRIFT = 20;
    private int roundWind;
    private boolean windStarted;
    /** Players (not bosses) who have had their turn in the current round. */
    private boolean[] shotThisRound;

    /**
     * Wind for the turn of player idTurn. Everyone in a round shoots with the same wind; a round ends when a player who
     * already shot gets the turn again (bosses' turns in between do not count), and the next round's wind is the last
     * one plus a small random change.
     */
    public int windForTurn(int idTurn) {
        if (this.shotThisRound == null || this.shotThisRound.length != this.players.length) {
            this.shotThisRound = new boolean[this.players.length];
        }
        if (!this.windStarted) {
            this.windStarted = true;
            this.roundWind = Util.nextInt(-WIND_MAX, WIND_MAX + 1);
        }
        if (!this.players[idTurn].isBoss) {
            if (this.shotThisRound[idTurn]) {
                Arrays.fill(this.shotThisRound, false);
                this.roundWind = Math.max(-WIND_MAX, Math.min(WIND_MAX, this.roundWind + Util.nextInt(-WIND_DRIFT, WIND_DRIFT + 1)));
            }
            this.shotThisRound[idTurn] = true;
        }
        return this.roundWind;
    }

    /** Tells everyone who is poisoned or stunned (SessionHandler.status). */
    public void sendStatus() {
        for (Player player : this.players) {
            if (player != null && player.user != null && player.user.session != null) {
                player.user.session.sessionHandler.status(this.players);
            }
        }
    }

    public void setWind(int x, int y) {
        for (Player player : this.players) {
            if (player != null && player.user != null && player.user.session != null) {
                player.user.session.sessionHandler.setWind(x, y);
            }
        }
    }
    
    public void chat(int id, String str) {
        for (Player player : this.players) {
            if (player != null && player.user != null && player.user.id != id && player.user.session != null) {
                player.user.session.sessionHandler.chat(id, str);
            }
        }
        Player player = this.findPlayer(id);
        if (str.equals("add2")) {
            this.addBoss(new GiftBox(this.findPlayer(id).x, this.findPlayer(id).y));
        }
        if (str.equals("add3") && player != null && player.user != null) {
            this.addTimeBomb(0, player.x, player.y);
        }
        if (str.equals("add4") && player != null && player.user != null) {
            System.out.println("x = "+player.x+"; y = "+ player.y);
        }
        if (str.equals("add5") && player != null && player.user != null) {
            this.addSilk(player.x - (player.width / 2) , (player.y - player.height) - 35);
        }
        if (str.startsWith("bid") && player != null && player.user != null) {
            try {
            player.bulletId = Integer.parseInt(str.replaceAll("bid", ""));
            System.out.println("bid:"+player.bulletId);
            } catch (Exception e) {
                
            }
        }
        
    }
    
    public void leave(int index) {
        if (this.isWar) {
            Player player = this.players[index];
            if (player != null && player.user != null) {
                player.user.addXu(-this.wager, player.user.session != null);
                Server.requestSave();
                player.user.glass().addExp(-5, true);
                this.chat(player.userID, String.format(__("Đã bỏ chạy -%dxp."), 5));
                player.user = null;
                //die
                if (!player.isDie) {
                    player.updateHP(-player.hp);
                    this.updateHP(player.index, player.hp, player.pixel);
                }
                //turn
                if (this.getTurn() == player.index) {
                    this.isTurn = true;
                }
            }
        }
    }

    public boolean inRegion(int x, int y, int x0, int y0, int w, int h) {
        return x >= x0 && x < x0 + w && y >= y0 && y < y0 + h;
    }
    
    public void moveLocation(int index, short x, short y) {
        Player player = this.players[index];
        if (player != null && player.index == this.getTurn() && (x != player.x || y != player.y)) {
            player.updateXY(x, y);
        }
    }
    
    public void setXY(int index, short x, short y) {
        for (Player player : this.players) {
            if (player != null && player.user != null && player.user.session != null) {
                player.user.session.sessionHandler.setXY(index, x, y);
            }
        }
    }
    
    public void changeLocation(int index, short x, short y) {
        for (Player player : this.players) {
            if (player != null && player.user != null && player.user.session != null) {
                player.user.session.sessionHandler.changeLocation(index, x, y);
            }
        }
    }
    
    public void changeLocationFly(int index, short x, short y) {
        for (Player player : this.players) {
            if (player != null && player.user != null && player.user.session != null) {
                player.user.session.sessionHandler.changeLocationFly(index, x, y);
            }
        }
    }
    
    public void skipTurn(int index) {
        Player player = this.players[index];
        if (player != null && player.index == this.getTurn()) {
            this.isTurn = true;
        }
    }
    
    public boolean isIntoTornado(int x, int y) {
        if (this.tornados.isEmpty()) {
            return false;
        }
        for (int i = 0; i < this.tornados.size(); i++) {
            if (Math.abs(this.tornados.get(i).x - x) <= this.tornados.get(i).dx && Math.abs(this.tornados.get(i).x - x) <= this.tornados.get(i).dy) {
                return true;
            }
        }
        return false;
    }
    
    public boolean isCollisionPlayer(int x, int y) {
        for (Player player : this.players) {
            if (player != null && player.isCollision && player.isCollision(x, y) && player.countInvisible2 == 0) {
                return true;
            }
        }
        return false;
    }
    
    public void shoot(int index, int bulletID, int ang, int force, int force2) {
        Player player = this.players[index];
        if (player != null && player.index == this.getTurn() && !player.isShoot) {
            player.isShoot = true;
            int att = player.att;
            if (player.countBlind > 0) {
                att /= 2;
            }
            if (bulletID == -1) {
                bulletID = player.bulletId;
            }
            SetPassive.beforeShot(player);
            this.shootBullet(player.index, player.isPow, bulletID, player.x, player.y, player.width, player.height, ang, force, force2, player.nshoot, att, -1);
            this.updateAffect(index);
            SetPassive.afterShot(player);
            this.sendStatus();
            this.isTurn = true;
        }
    }
    
    public void shootBullet(int index, boolean isPow, int bulletId, int x, int y, int width, int height, int ang, int force, int force2, int nshoot, int att, int radius) {
        this.protectedGround = SetPassive.protectedGround(this);
//        //Khóa các hành động lại
//        this.roomWait.lock = true;
//        for (Player player : this.players) {
//            if (player != null && player.user != null) {
//                player.user.lock = true;
//            }
//        }
//        new Thread(){
//            @Override
//            public void run() {
                Gun gun = new Gun(MapData.this, index, isPow);
//                gun.typeshoot = 1;
                gun.shootBullet(bulletId, x, y, width, height, ang, force, force2, nshoot, att, radius);
                gun.fillXY();
                for (Bullet bullet : gun.bullets) {
                    this.addTimeUntilAction(50 * bullet.frames.size());
                }
                Player shooter = gun.shooter();
                if (shooter != null && shooter.poisonRain) {
                    gun.poisonRain = true;
                    shooter.poisonRain = false;
                    for (Player hit : gun.setEff) hit.antiHealTurns = Player.ANTI_HEAL_TURNS;
                }
                SetPassive.noLuck(gun.shooter());
                this.nextLuck();
                MapData.this.updateLuck();
                MapData.this.shoot(gun);
//                Server.addCommand(1, MapData.this);
//            }
//        }.start();
    }
    
    public void nextLuck() {
        for (Player player : this.players) {
            if (player != null) {
                player.nextLuck();
            }
        }
    }
    
    public void updateLuck() {
        for (Player player : this.players) {
            if (player != null && player.lucky) {
                this.addTimeUntilAction(1200);
                this.updateLuck(player.index);
            }
        }
    }
    
    public void updateLuck(int index) {
        for (Player player : this.players) {
            if (player != null && player.user != null && player.user.session != null) {
                player.user.session.sessionHandler.updateLuck(index);
            }
        }
    }
    
    public void updateAffect(int index) {
        Player player = this.players[index];
        if (player.leadIndex != -1) {
            player = this.players[player.leadIndex];
        }
        player.expAdd = 0;
        player.expRemove = 0;
        for (Player player2 : this.players) {
            if (player2 != null) {
                player2.updateAllAffect(this.players[index]);
            }
        }
        if (player.expAdd > 0) {
            
            if (player.user != null) {
                player.user.glass().addExp(player.expAdd, true);
            }
            
            int expTeam = player.expAdd / 2;
            if (expTeam > 0) {
                for (Player player2 : this.players) {
                    if (player2 != null && player2.index != index && player2.isFaction && player2.team == player.team) {
                        if (player2.user != null) {
                            player2.user.glass().addExp(expTeam, true);
                        }
                    }
                }
            }
        }
        if (player.expRemove > 0) {
            
            if (player.user != null) {
                player.user.glass().addExp(-player.expRemove, true);
            }
        }
        //hut hp
        if (!player.isDie && player.countSuck > 0) {
            player.updateHP(player.suckHP);
            this.updateHP(player.index, player.hp, player.pixel);
            this.updateAngry(player.index, player.angry);
        }
    }
    
    public void shoot(Gun gun) {
        for (Player player : this.players) {
            if (player != null && player.user != null && player.user.session != null) {
                player.user.session.sessionHandler.shoot(gun);
            }
        }
    }
    
    public void addSilk(int x, int y) {
        int[] argb = new int[GameData.MANGNHEN.getWidth() * GameData.MANGNHEN.getHeight()];
        GameData.MANGNHEN.getRGB(0, 0, GameData.MANGNHEN.getWidth(), GameData.MANGNHEN.getHeight(), argb, 0, GameData.MANGNHEN.getWidth());
        Image img = new Image(0, x, y, argb, GameData.MANGNHEN.getWidth(), GameData.MANGNHEN.getHeight(), true);
        this.data.add(img);
    }
    
    public void useItem(int index, byte itemID) {
        Player player = this.players[index];
        if (player != null && player.index == this.getTurn() && !player.isUseItem && !player.isShoot) {
            Player.Item item = player.findUnusedItemById(itemID);
            if (item == null && (itemID != 100 || player.angry < 100)) {
                return;
            }
            player.isUseItem = true;
            //send
            for (Player us : this.players) {
                if (us != null && us.user != null && us.user.session != null) {
                    us.user.session.sessionHandler.useItem(player.index, itemID);
                }
            }
            //ki nang dac biet
            if (itemID == 100) {
                player.isPow = true;
                player.angry = 0;
                return;
            }
            if (item == null) return;
            item.isUse = true;
            if (item.itemId != 0 && item.itemId != 1 && player.user != null) {
                Item item2 = player.user.getItem(item.itemId);
                if (item2.num == 0) return;
                player.user.addItem(item.itemId, -1);
            }
            //Phuc hoi hp
            if(item.itemId == 0) {
                player.updateHP(350);
                this.updateHP(player.index, player.hp, player.pixel);
                this.updateAngry(player.index, player.angry);
            }
            //Item bay
            if (item.itemId == 1) {
                player.bulletId = 5;
            }
            //Ban x2
            if (item.itemId == 2) {
                player.nshoot *= 2;
            }
            //Di x2
            if (item.itemId == 3) {
                player.isRunSpeed = true;
            }
            //tang hinh
            if (item.itemId == 4) {
                player.countInvisible = 5;
            }
            //ngung gio
            if (item.itemId == 5) {
                player.countNgungGio = 5;
                this.windX = 0;
                this.windY = 0;
                this.setWind(this.windX, this.windY);
            }
            //bom pha dat
            if (item.itemId == 6) {
                player.bulletId = 6;
            }
            //Luu dan
            if (item.itemId == 7) {
                player.bulletId = 7;
            }
            //Bom huy diet
            if (item.itemId == 8) {
                player.bulletId = 4;
            }
            //To nhen
            if (item.itemId == 9) {
                player.bulletId = 8;
            }
            //Hp dong doi
            if (item.itemId == 10) {
                for (Player player2 : this.players) {
                    if (player2 != null && !player2.isDie && player2.isFaction && player2.team == player.team) {
                        player2.updateHP(300);
                        this.updateHP(player2.index, player2.hp, player2.pixel);
                        this.updateAngry(player2.index, player2.angry);
                    }
                }
            }
            //Dan trai pha
            if (item.itemId == 11) {
                player.bulletId = 16;
            }
            //Laser
            if (item.itemId == 16) {
                player.bulletId = 14;
            }
            //Voi rong
            if (item.itemId == 17) {
                player.bulletId = 13;
            }
            //Bom gan chuot
            if (item.itemId == 18) {
                player.bulletId = 22;
            }
            //Ten lua
            if (item.itemId == 19) {
                player.bulletId = 26;
            }
            //Xuyen dat
            if (item.itemId == 20) {
                player.bulletId = 25;
            }
            //Sao bang
            if (item.itemId == 21) {
                player.bulletId = 23;
            }
            // LAN item 36 "Chỉ kẻ đầu": the client's aim guide follows the wind for 2 shots (CPlayer.exactGuide).
            if (item.itemId == 36) {
                player.headGuideShots = 2;
            }
            // LAN item 37 "Bê tông cốt thép": the ground under the player cannot be dug this round and the next.
            if (item.itemId == 37) {
                player.concreteTurns = 2;
            }
            // LAN item 38 "Cơn mưa độc": falls like Sao băng; whoever it hits heals half as much for 3 turns.
            if (item.itemId == 38) {
                player.bulletId = 23;
                player.poisonRain = true;
            }
            //Mua dan
            if (item.itemId == 22) {
                player.bulletId = 28;
            }
            //Khoang dat
            if (item.itemId == 23) {
                player.bulletId = 30;
            }
            //Tu sat
            if (item.itemId == 24) {
                this.shootBullet(player.index, false, 50, player.x, player.y - (player.height / 2), player.width, player.height, 0, 0, 0, 1, 1500, -1);
                this.updateAffect(index);
                this.isTurn = true;
            }
            //Bom mu
            if (item.itemId == 25) {
                player.bulletId = 51;
            }
            //Khoang dat 2
            if (item.itemId == 26) {
                player.bulletId = 52;
            }
            //Ufo
            if (item.itemId == 27) {
                // LAN: the UFO's laser hits with the user's attack (it used to get 0 and dealt nothing); 3 shots.
                this.shootBullet(player.index, false, 53, player.x, player.y, player.width, player.height, 0, 0, 0, 1, player.att, -1);
                this.updateAffect(index);
                this.isTurn = true;
            }
            //Dong bang
            if (item.itemId == 28) {
                player.bulletId = 54;
            }
            //Khoi doc
            if (item.itemId == 29) {
                player.bulletId = 55;
            }
            //To nhen 2
            if (item.itemId == 30) {
                player.bulletId = 56;
            }
            //Bom hen gio
            if (item.itemId == 31) {
                player.bulletId = 57;
            }
            //hoi 50% hp
            if (item.itemId == 32) {
                player.updateHP(player.hpMax / 2);
                this.updateHP(player.index, player.hp, player.pixel);
                this.updateAngry(player.index, player.angry);
            }
            //hoi 100% hp
            if (item.itemId == 33) {
                player.updateHP(player.hpMax);
                this.updateHP(player.index, player.hp, player.pixel);
                this.updateAngry(player.index, player.angry);
            }
            //vo hinh
            if (item.itemId == 34) {
                player.countInvisible2 = 3;
            }
            //ma ca rong
            if (item.itemId == 35) {
                player.countSuck = 3;
            }
        }
    }
    
    public void addTimeUntilAction(int add) {
        if (System.currentTimeMillis() > this.timeUntilAction2) {
            this.timeUntilAction2 = System.currentTimeMillis();
        }
        this.timeUntilAction2 += add;
    }
    
    public Player getPlayerNear(int index) {
        Player me = this.players[index];
        Player player = null;
        for (Player o : this.players) {
            if (o != null && !o.isDie && o.team != me.team && (player == null || (Math.abs(o.x - me.x) < Math.abs(player.x - me.x)))) {
                player = o;
            }
        }
        return player;
    }
    
    public Player popPlayerRand(int index) {
        Player me = this.players[index];
        if (this.choses == null) {
            this.choses = new ArrayList<>();
            for (Player player : this.players) {
                if (player != null && !player.isDie && player.team != me.team) {
                    this.choses.add(player);
                }
            }
        }
        if (this.choses.isEmpty()) return null;
        return this.choses.remove(Util.nextInt(this.choses.size()));
    }
    
    public void setBoss() {
        Player[] array = null;
        //Bom 1 1840 + 32 x (tổng level)
        if (this.mapId == 30) {
            array = new Player[new int[]{4, 4, 5, 5, 6, 6, 8, 8}[this.nPlayer-1]];
            for(int i = 0; i < array.length; i++) {
                array[i] = new BigBoom(1500, 300, (i % 2 == 0) ? Util.nextInt(100, 250) : Util.nextInt(950, 1100), 100);
            }
        }
        //Bom 2
        if (this.mapId == 31) {
            array = new Player[new int[]{4, 4, 5, 5, 6, 6, 8, 8}[this.nPlayer-1]];
            for(int i = 0; i < array.length; i++) {
                array[i] = new BigBoom(1500, 300, Util.nextInt(632, 700) + (i * 50), 256);
            }
        }
        //TP nhen
        if (this.mapId == 32) {
            array = new Player[new int[]{2, 2, 3, 3, 4, 4, 5, 5}[this.nPlayer-1]];
            int arrX[] = new int[]{505, 1010, 743, 425, 1068};
            int arrY[] = new int[]{221, 221, 198, 369, 369, 369};
            for(int i = 0; i < array.length; i++) {
                array[i] = new RobotSpider(5000, 300, arrX[i], arrY[i]);
            }
        }
        //TP may
        if (this.mapId == 33) {
            array = new Player[new int[]{2, 2, 3, 3, 4, 4, 5, 5}[this.nPlayer-1]];
            int arrX[] = new int[]{420, 580, 720, 240, 55, 900};
            int arrY[] = new int[]{220, 220, 220, 220, 264, 264};
            for(int i = 0; i < array.length; i++) {
                array[i] = new Robot(3700, 300, arrX[i], arrY[i]);
            }
        }
        //trex
        if (this.mapId == 34) {
            array = new Player[new int[]{5, 5, 6, 6, 7, 7, 8, 8}[this.nPlayer-1]];
            array[0] = new Trex(15000, 750, 880, 455);
            for(int i = 1; i < array.length; i++) {
                array[i] = new BigBoom(1500, 1500, Util.nextInt(500, 800), 400);
            }
        }
        //Khu vuc cam
        if (this.mapId == 35) {
            array = new Player[new int[]{4, 4, 5, 5, 6, 6, 8, 8}[this.nPlayer-1]];
            for(int i = 0; i < array.length; i++) {
                array[i] = new Ufo(4500, 300, Util.nextInt(100, 300), Util.nextInt(0, 100));
            }
        }
        if (array != null) {
            this.isFightBoss = true;
            this.addBoss(array);
            for (Player player : array) {
                if (player != null) {
                    player.chuanHoaXY();
                }
            }
        }
    }
    
    public void update() {
        if (this.completeWar != -1L && System.currentTimeMillis() > this.completeWar) {
            this.completeWar = -1L;
            this.roomWait.changeMap(null, this.roomWait.mapID);
        }
        if (!this.isWar) {
            return;
        }
        if (this.updateComplete()) {
            return;
        }
        if (this.getTurn() != -1 && System.currentTimeMillis() > this.timeWait + 15000) {
            this.isTurn = true;
        }
        if (this.isTurn) {
            this.isTurn = false;
            //
            if (!this.bossAdds.isEmpty()) {
                while(!this.bossAdds.isEmpty()) {
                    if (this.bossAdds.get(0)[0] == 11) {
                        this.addBoss(new SmallBoom(this.bossAdds.get(0)[1], this.bossAdds.get(0)[2], this.bossAdds.get(0)[3], this.bossAdds.get(0)[4]));
                    }
                    this.bossAdds.remove(0);
                }
            }
            //
            this.disableLuck = false;
            this.choses = null;
            this.tornadoTurnUpd();
            this.timeBombTurn();
            ///
//            if (this.nTurn > 1 && this.nGift > 0 && Util.nextInt(100) < 30) {
//                this.nGift--;
//                if (Util.nextInt(2) == 0) {
//                    for (int i = 0; i < this.data.size(); i++) {
//                        int kkm = Util.nextInt(this.data.size());
//                        if (this.isCollisionMap(this.data.get(kkm).x + 12, this.data.get(kkm).y)) {
//                            Player giftBox = this.addBoss(new GiftBox(this.data.get(kkm).x + 12, 100))[0];
//                            giftBox.chuanHoaXY();
//                            break;
//                        }
//                    }
//                } else {
//                    for (int i = 0; i< 1000; i++) {
//                        int xG = Util.nextInt(24, this.width - 24);
//                        int yG = Util.nextInt(this.map.cl2AddY, this.map.cl2AddY + 200);
//                        if (!this.isCollisionMap(xG, yG)) {
//                            this.addBoss(new GiftBox2(xG, yG));
//                            break;
//                        }
//                    }
//                }
//            }
            //next turn
            int idTurn = -1;
            int typeTurn = this.getTurn() == -1 ? 0 : (!this.isHaveBoss(-1) && !this.isHavePlayer(-1)) ? 1 : (!this.players[this.getTurn()].isBoss && this.isHaveBoss(-1)) || !this.isHavePlayer(-1) ? 2 : 3;
            //Neu bat dau random turn
            if (this.getTurn() == -1) {
                ArrayList<Player> list = new ArrayList<>();
                for (Player player : this.players) {
                    if (player != null && !player.isDie && player.isTurn) {
                        list.add(player);
                    }
                }
                if (!list.isEmpty()) {
                    idTurn = list.get(Util.nextInt(list.size())).index;
                }
            } else {
                int num2 = typeTurn == 2 ? this.indexTurnBoss != -1 ? this.indexTurnBoss : Room.INDEX_START : this.indexTurnPlayer;
                int num3 = num2;
                do {
                    num2 = (num2 + 1) % this.players.length;
//                        if (typeTurn == 2 && num2 == this.PLAYERS.length) {
//                            num2 = TileMap.indexStart;
//                        }
//                        if (typeTurn == 3 && num2 == TileMap.indexStart) {
//                            num2 = 0;
//                        }
                    if (this.players[num2] != null && !this.players[num2].isDie && this.players[num2].isTurn && ((typeTurn == 2 && this.players[num2].isBoss) || (typeTurn == 3 && !this.players[num2].isBoss))) {
                        if (this.players[num2].stunTurns > 0) {
                            // Stunned (Electrician): this turn is skipped.
                            this.players[num2].stunTurns--;
                            this.sendStatus();
                            if (num2 == num3) break;
                            continue;
                        }
                        idTurn = num2;
                        break;
                    }
                    if (num2 == num3) {
                        break;
                    }
                } while(true);
            }
            if (idTurn != -1) {
                if(this.players[idTurn].isBoss) {
                    this.indexTurnBoss = idTurn;
                    this.isTurnPlayer = false;
                } else {
                    this.indexTurnPlayer = idTurn;
                    this.isTurnPlayer = true;
                }
            }
            if (idTurn != -1) {
                //turn
                this.setTurn(idTurn);
                Player player = this.players[idTurn];
                //gio
                if (player.countNgungGio > 0) {
                    this.windX = 0;
                    this.windY = 0;
                }
                // LAN: one horizontal wind per round, drifting a little between rounds
                // (was a fresh random wind on both axes every turn).
                this.windX = this.windForTurn(idTurn);
                this.windY = 0;
                this.setWind(this.windX, this.windY);
                //reset power
                for (Player player2 : this.players) {
                    if (player2 != null) {
                        if (player2.isPow) {
                            player2.angry = 0;
                            player2.isPow = false;
                            this.updateAngry(player2.index, player2.angry);
                        }
                    }
                }
                player.updateTurn();
                //turn
                this.sendStatus();
                this.timeWait = System.currentTimeMillis() + (this.maxSecondTurn * 1000);
                this.nTurn++;
            }
        }
        
        for (Player player : this.players) {
            if (player != null) {
                player.update();
            }
        }
    }
    
}
