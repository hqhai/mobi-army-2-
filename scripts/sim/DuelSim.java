package mobiarmy.war;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import mobiarmy.Util;
import mobiarmy.server.GameData;
import mobiarmy.server.Glass;
import mobiarmy.war.Boss.bullet.Bullet;
import sun.misc.Unsafe;

/**
 * Headless 1v1 duels on the real match code (MapData turn loop, Gun and bullets, holes, SetPassive, SkillBalance), for
 * balancing the characters. Run it with scripts/sim/Run-DuelSim.ps1; it needs the database (maps, characters).
 *
 * <p>Both sides wear a full gold +14 set (passives at the given tier). Bots aim like the in-game Bot (the lowest force
 * whose path goes through the opponent's body), then aim the middle of a spread and pick when Chicky's egg drops or
 * Apache's hammer splits; a hand error is added that grows with the wind the shot feels. They use POW as soon as it is
 * full, walk only when they have no shot or to step off an Easter egg, and use no items.
 *
 * <p>Args (key=value): matches, threads, rules (0 free, 1 balanced, 2 base), tier, maxTurns, sf / sa / s2 / sw (hand
 * error of force, angle, force2, and per full wind), evade (chance to step off an egg or from under a UFO), ufo (chance to
 * shoot down an opponent's UFO that one shot kills), hp / att / def / luck (stat
 * points on top of the character's start), pow, heroes (e.g. 5,6), attN (override character N's attack), matrix.
 */public class DuelSim {
    static int matches = 2000, threads = Runtime.getRuntime().availableProcessors(), rules = RoomRules.BALANCED,
            tier = 6, maxTurns = 120, ptsHp = 0, ptsAtt = 0, ptsDef = 0, ptsLuck = 0;
    static double sigmaForce = 0.6, sigmaAng = 1.0, sigmaF2 = 1.0, sigmaWind = 1.0, evadeChance = 0.5, ufoChance = 0.6;
    static boolean usePow = true, matrix = false;
    static int[] heroes = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    static final int[] MAPS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23,
        24, 25, 26, 28, 29, 36, 37, 38, 39};
    static final String[] NAMES = {"Gunner", "Miss 6", "Electrician", "King Kong", "Rocketer", "Granos", "Chicky",
        "Tarzan", "Apache", "Magenta"};
    static final java.util.Map<Integer, Integer> attOverride = new HashMap<>();
    /** Gold set: 15 per stat a piece, +37 at +14, five pieces; 8% per stat a piece. */
    static final int GEAR = 5 * (15 + 37), GEAR_PERCENT = 5 * 8;

    static final class Tally {
        long games, wins, losses, draws, dealt, taken, turns, shots, hits, kills, fallKills, fallDeaths, eggs;
        long pows, powDealt, shotDealt;
    }

    static final Tally[] tally = new Tally[10];
    static final long[][] pairWins = new long[10][10], pairGames = new long[10][10];

    public static void main(String[] args) throws Exception {
        for (String arg : args) {
            String[] kv = arg.split("=", 2);
            String v = kv.length > 1 ? kv[1] : "";
            switch (kv[0]) {
                case "matches" -> matches = Integer.parseInt(v);
                case "threads" -> threads = Integer.parseInt(v);
                case "rules" -> rules = Integer.parseInt(v);
                case "tier" -> tier = Integer.parseInt(v);
                case "maxTurns" -> maxTurns = Integer.parseInt(v);
                case "sf" -> sigmaForce = Double.parseDouble(v);
                case "sa" -> sigmaAng = Double.parseDouble(v);
                case "s2" -> sigmaF2 = Double.parseDouble(v);
                case "sw" -> sigmaWind = Double.parseDouble(v);
                case "hp" -> ptsHp = Integer.parseInt(v);
                case "att" -> ptsAtt = Integer.parseInt(v);
                case "def" -> ptsDef = Integer.parseInt(v);
                case "luck" -> ptsLuck = Integer.parseInt(v);
                case "pow" -> usePow = Boolean.parseBoolean(v);
                case "evade" -> evadeChance = Double.parseDouble(v);
                case "ufo" -> ufoChance = Double.parseDouble(v);
                case "matrix" -> matrix = Boolean.parseBoolean(v);
                case "heroes" -> heroes = Arrays.stream(v.split(",")).mapToInt(Integer::parseInt).toArray();
                default -> {
                    if (kv[0].startsWith("att")) attOverride.put(Integer.parseInt(kv[0].substring(3)), Integer.parseInt(v));
                    else throw new IllegalArgumentException("unknown arg " + kv[0]);
                }
            }
        }
        for (int i = 0; i < tally.length; i++) tally[i] = new Tally();
        mobiarmy.server.Map.loadMap();
        Glass.loadGlass();
        GameData.loadHole();
        for (var e : attOverride.entrySet()) Glass.entrys[e.getKey()].att = e.getValue();

        List<int[]> pairs = new ArrayList<>();
        for (int i = 0; i < heroes.length; i++) {
            for (int j = i + 1; j < heroes.length; j++) pairs.add(new int[] {heroes[i], heroes[j]});
        }
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger done = new AtomicInteger();
        long t0 = System.currentTimeMillis();
        List<Future<?>> futures = new ArrayList<>();
        for (int m = 0; m < matches; m++) {
            int[] pair = pairs.get(m % pairs.size());
            boolean swap = (m / pairs.size()) % 2 == 1;
            int a = swap ? pair[1] : pair[0], b = swap ? pair[0] : pair[1];
            futures.add(pool.submit(() -> {
                try {
                    runMatch(a, b);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                int n = done.incrementAndGet();
                if (n % Math.max(1, matches / 10) == 0) {
                    System.err.printf(Locale.ROOT, "%d/%d matches, %.0fs%n", n, matches, (System.currentTimeMillis() - t0) / 1000.0);
                }
                return null;
            }));
        }
        for (Future<?> f : futures) f.get();
        pool.shutdown();
        report((System.currentTimeMillis() - t0) / 1000.0);
    }

    static void report(double seconds) {
        System.out.printf(Locale.ROOT, "%d duels, rules %d, set tier %d, hand error force %.2f angle %.2f force2 %.2f wind %.2f,"
                + " evade %.2f, +hp %d +att %d +def %d +luck %d, %.0fs%n",
                matches, rules, tier, sigmaForce, sigmaAng, sigmaF2, sigmaWind, evadeChance, ptsHp, ptsAtt, ptsDef, ptsLuck, seconds);
        StringBuilder atts = new StringBuilder();
        for (int h : heroes) atts.append(NAMES[h]).append(' ').append(Glass.entrys[h].att).append(", ");
        System.out.println("att: " + atts);
        System.out.println("hero          win%    +-   W-L-D            dealt  taken  turns  hit%  kills fall-k fall-d eggs  /shot  /pow");
        Integer[] order = Arrays.stream(heroes).boxed().toArray(Integer[]::new);
        Arrays.sort(order, (x, y) -> Double.compare(rate(tally[y]), rate(tally[x])));
        for (int h : order) {
            Tally t = tally[h];
            double p = rate(t), n = Math.max(1, t.games);
            double ci = 196 * Math.sqrt(Math.max(p, 1) * Math.max(100 - p, 1) / 10000.0 / n);
            System.out.printf(Locale.ROOT, "%-12s %5.1f  %4.1f   %4d-%4d-%3d  %6d %6d %6.1f %5.1f %6.2f %6.2f %6.2f %5.2f %6d %5d%n",
                    NAMES[h], p, ci, t.wins, t.losses, t.draws, t.dealt / (long) n, t.taken / (long) n, t.turns / n,
                    t.shots == 0 ? 0 : 100.0 * t.hits / t.shots, t.kills / n, t.fallKills / n, t.fallDeaths / n, t.eggs / n,
                    t.shots == t.pows ? 0 : t.shotDealt / (t.shots - t.pows), t.pows == 0 ? 0 : t.powDealt / t.pows);
        }
        if (matrix) {
            System.out.print("\nrow beats col %  ");
            for (int h : heroes) System.out.printf(Locale.ROOT, "%6.6s", NAMES[h]);
            System.out.println();
            for (int r : heroes) {
                System.out.printf(Locale.ROOT, "%-16s", NAMES[r]);
                for (int c : heroes) {
                    System.out.print(r == c || pairGames[r][c] == 0 ? "     -" : String.format(Locale.ROOT, "%6.0f", 100.0 * pairWins[r][c] / pairGames[r][c]));
                }
                System.out.println();
            }
        }
    }

    /** Win rate with draws as half. */
    static double rate(Tally t) {
        return t.games == 0 ? 0 : 100.0 * (t.wins + t.draws / 2.0) / t.games;
    }

    static void runMatch(int a, int b) throws Exception {
        SimMap map = SimMap.create(MAPS[Util.nextInt(MAPS.length)]);
        map.rules = (byte) rules;
        map.skillRules = rules == RoomRules.BALANCED;
        List<mobiarmy.server.Map.Point> points = new ArrayList<>(Arrays.asList(map.map.data.points));
        Player pa = spawn(map, 0, a, 1, points), pb = spawn(map, 1, b, 2, points);
        Brain[] brains = {new Brain(pa, pb), new Brain(pb, pa)};
        map.isWar = true;
        map.isTurn = true;
        int turns = 0;
        while (map.isWar && turns < maxTurns) {
            map.update();
            if (!map.isWar) break;
            int t = map.getTurn();
            if (t < 0) break;
            Player me = map.players[t];
            if (t > 1) {
                // A summon's turn (Magenta's UFO): it acts at once unless it already did in map.update.
                map.timeUntilAction2 = 0;
                if (!map.isTurn) me.update();
                map.isTurn = true;
                continue;
            }
            if (me.isDie || me.isShoot) {
                map.isTurn = true;
                continue;
            }
            turns++;
            brains[t].act();
            if (!me.isShoot) map.isTurn = true;
        }
        if (map.isWar) map.updateComplete();
        int result = map.typeComplete; // 1: a won, 2: b won, 3 or -1: draw
        synchronized (tally) {
            record(a, pa, brains[0], result == 1 ? 1 : result == 2 ? -1 : 0, map);
            record(b, pb, brains[1], result == 2 ? 1 : result == 1 ? -1 : 0, map);
            pairGames[a][b]++;
            pairGames[b][a]++;
            if (result == 1) pairWins[a][b]++;
            if (result == 2) pairWins[b][a]++;
        }
    }

    static void record(int hero, Player p, Brain brain, int result, MapData map) {
        Tally t = tally[hero];
        t.games++;
        if (result > 0) t.wins++;
        else if (result < 0) t.losses++;
        else t.draws++;
        t.dealt += p.statDealt;
        t.taken += p.statTaken;
        t.turns += brain.shots;
        t.shots += brain.shots;
        t.hits += brain.hits;
        t.kills += p.statKills;
        Player foe = brain.foe;
        if (foe.isDie && foe.y > map.height + 100) t.fallKills++;
        if (p.isDie && p.y > map.height + 100) t.fallDeaths++;
        t.eggs += brain.eggsLaid;
        t.pows += brain.pows;
        t.powDealt += brain.powDealt;
        t.shotDealt += brain.shotDealt;
    }

    static Player spawn(SimMap map, int index, int glassId, int team, List<mobiarmy.server.Map.Point> points) {
        Glass glass = Glass.entrys[glassId];
        int[] s = stats(glass);
        Player p = new Player(map, null, NAMES[glassId], glassId, 50, s[0], s[1], s[2], s[3], s[4], 24, 24, null);
        p.index = (byte) index;
        p.setTier = rules == RoomRules.BASE ? 0 : tier;
        p.theluc = SetPassive.steps(p);
        p.baseAtt = glass.att;
        p.isBoss = false;
        p.isFly = false;
        p.team = team;
        mobiarmy.server.Map.Point point = points.remove(Util.nextInt(points.size()));
        p.x = point.x;
        p.y = point.y;
        map.players[index] = p;
        map.nPlayer++;
        p.chuanHoaXY();
        return p;
    }

    /** HP, attack, defense, luck, teamwork in a full gold +14 set (Glass.createAbility), or a fresh character's. */
    static int[] stats(Glass glass) {
        int[] start = glass.ability != null ? glass.ability : new int[5];
        if (rules == RoomRules.BASE) {
            return new int[] {1000 + start[0] * 10, glass.att * (start[1] / 3 + 100) / 100, start[2] * 10, start[3] * 10, start[4] * 10};
        }
        int[] ab = start.clone();
        ab[0] += ptsHp;
        ab[1] += ptsAtt;
        ab[2] += ptsDef;
        ab[3] += ptsLuck;
        int[] s = new int[5];
        s[0] = 1000 + ab[0] * 10 + GEAR * 10 + (1000 + ab[0]) * GEAR_PERCENT / 100;
        s[1] = glass.att * ((ab[1] + GEAR) / 3 + 100 + GEAR_PERCENT) / 100;
        for (int i = 2; i < 5; i++) {
            s[i] = (ab[i] + GEAR) * 10;
            s[i] += s[i] * GEAR_PERCENT / 100;
        }
        return s;
    }

    static final class Shot {
        int ang, force, force2;

        Shot(int ang, int force, int force2) {
            this.ang = ang;
            this.force = force;
            this.force2 = force2;
        }
    }

    /** One side's bot. */
    static final class Brain {
        final Player me, foe;
        long shots, hits, eggsLaid, pows, powDealt, shotDealt;
        int seenX, seenY;
        boolean seen;

        Brain(Player me, Player foe) {
            this.me = me;
            this.foe = foe;
        }

        void act() {
            MapData map = this.me.mapData;
            if (usePow && this.me.angry >= 100) map.useItem(this.me.index, (byte) 100);
            if (ThreadLocalRandom.current().nextDouble() < evadeChance) this.stepOffEggs();
            // Hidden by his hunt, Apache runs somewhere else so that he is not where he was last seen.
            if (this.me.huntHidden && this.me.huntRun) walk(this.me, ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
            // A hidden opponent (Apache's hunt, invisibility) is shot where he was last seen.
            boolean hidden = this.foe.huntHidden || this.foe.countInvisible > 0;
            if (!hidden || !this.seen) {
                this.seenX = this.foe.x;
                this.seenY = this.foe.y;
                this.seen = true;
            }
            // An opponent's UFO that one shot brings down is often shot first (its laser hurts, shooting it heals).
            Player ufo = this.enemyUfo();
            if (ufo != null && ufo.hp <= this.me.att / 2 && ThreadLocalRandom.current().nextDouble() < ufoChance) {
                Shot down = aim(this.me, ufo, ufo.x, ufo.y, true);
                if (down != null) {
                    this.fire(down);
                    return;
                }
            }
            Shot shot = aim(this.me, this.foe, this.seenX, this.seenY, true);
            if (shot == null) {
                walk(this.me, this.seenX >= this.me.x ? 1 : -1);
                shot = aim(this.me, this.foe, this.seenX, this.seenY, true);
            }
            if (shot == null) shot = aim(this.me, this.foe, this.seenX, this.seenY, false);
            if (shot == null) shot = new Shot(this.seenX >= this.me.x ? 45 : 135, 20, 30);
            this.fire(shot);
        }

        /** An opponent's flying UFO (Magenta's passive), or null. */
        Player enemyUfo() {
            for (Player p : this.me.mapData.players) {
                if (p instanceof mobiarmy.war.Boss.AlienUfo u && !u.isDie && u.hurts(this.me)) return u;
            }
            return null;
        }

        void fire(Shot shot) {
            MapData map = this.me.mapData;
            this.handError(shot);
            int dealt = this.me.statDealt;
            boolean pow = this.me.isPow;
            int eggs = map.pendingEggs.size();
            map.shoot(this.me.index, -1, shot.ang, shot.force, shot.force2);
            this.shots++;
            int gained = this.me.statDealt - dealt;
            if (gained > 0) this.hits++;
            if (pow) {
                this.pows++;
                this.powDealt += gained;
            } else {
                this.shotDealt += gained;
            }
            this.eggsLaid += Math.max(0, map.pendingEggs.size() - eggs);
        }

        /** Share of the wind (percent) this turn's shot feels: the bullet's own share, after the shooter's passive. */
        int windFelt() {
            if (SetPassive.worldWar(this.me, this.me.isPow)) return 0;
            int pct = switch (this.me.bulletId) {
                case 0, 2 -> 80;
                case 1, 10 -> 50;
                case 9, 49 -> 40;
                case 11, 17 -> 30;
                case 19 -> 20;
                case 21 -> SetPassive.boomerangWind(this.me, 100);
                default -> 80;
            };
            return Math.abs(this.me.mapData.windX) * pct / 100;
        }

        void handError(Shot shot) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            int min = Glass.entrys[this.me.glassID].angle;
            // People misjudge the wind: the more of it the shot feels, the bigger the error.
            double wind = sigmaWind * this.windFelt() / 70.0;
            shot.force = Math.max(1, Math.min(30, (int) Math.round(shot.force + r.nextGaussian() * (sigmaForce + wind))));
            int ang = (int) Math.round(shot.ang + r.nextGaussian() * (sigmaAng + 2 * wind));
            shot.ang = shot.ang <= 90 ? Math.max(min, Math.min(90, ang)) : Math.max(90, Math.min(180 - min, ang));
            if (shot.force2 > 0) shot.force2 = Math.max(1, (int) Math.round(shot.force2 + r.nextGaussian() * sigmaF2));
        }

        /** Walks off an opponent's Easter egg that would catch him when it hatches, or from under a UFO's laser. */
        void stepOffEggs() {
            Player ufo = this.enemyUfo();
            if (ufo != null && Math.abs(ufo.x - this.me.x) < 70) {
                int dir = this.me.x >= ufo.x ? 1 : -1;
                if (!walk(this.me, dir)) walk(this.me, -dir);
                return;
            }
            for (TimeBomb bomb : this.me.mapData.timeBombs) {
                if (!bomb.egg || bomb.leadIndex == this.me.index) continue;
                if (Math.hypot(this.me.x - bomb.x, this.me.y - bomb.y) <= SetPassive.CHICKY_EGG_RADIUS + 6) {
                    int dir = this.me.x >= bomb.x ? 1 : -1;
                    if (!walk(this.me, dir)) walk(this.me, -dir);
                    return;
                }
            }
        }
    }

    /**
     * Walks up to his steps toward dir without falling off the map or into a deep pit; true if he moved. Steps as
     * Player.move does (2 px running), so the server ends exactly where this stops.
     */
    static boolean walk(Player me, int dir) {
        MapData map = me.mapData;
        int step = me.isRunSpeed || me.huntRun ? 2 : 1;
        int x = me.x, y = me.y, to = x;
        for (int i = 0; i <= me.theluc - me.buocdi; i++) {
            int nx = x + dir * step;
            if (map.isCollisionMap(nx, y - 5)) break;
            int ny = -1;
            for (int k = 4; k >= 0; k--) {
                if (map.isCollisionMap(nx, y - k)) {
                    ny = y - k;
                    break;
                }
            }
            if (ny < 0) {
                ny = y;
                while (ny < map.height + 200 && !map.isCollisionMap(nx, ny)) ny++;
            }
            if (ny > map.height + 50 || ny - y > 80) break;
            x = nx;
            y = ny;
            to = x;
        }
        if (to == me.x) return false;
        me.updateXY(to, me.y);
        return true;
    }

    /** A gun that only traces paths. */
    static final class Tracer extends Gun {
        Tracer(MapData map, int index, boolean pow) {
            super(map, index, pow);
            this.test = true;
        }
    }

    /**
     * The in-game Bot's aim: lowest force first, the side facing the target first, the flattest angle first; the first
     * path through the target's body is then refined (refine).
     */
    static Shot aim(Player me, Player foe, int tx, int ty, boolean land) {
        int bulletId = me.bulletId;
        boolean flare = SetPassive.worldWar(me, me.isPow);
        int minAng = flare ? -60 : Glass.entrys[me.glassID].angle;
        int bx = tx - foe.width / 2, by = ty - foe.height, bw = foe.width, bh = foe.height;
        boolean right = tx >= me.x;
        boolean timed = bulletId == 17 || bulletId == 19; // the shot's force2 matters: find the path without it first
        Tracer gun = new Tracer(me.mapData, me.index, me.isPow);
        for (int force = 1; force <= 30; force++) {
            for (int side = 0; side < 2; side++) {
                boolean toRight = side == 0 == right;
                for (int k = 0; ; k++) {
                    int ang = toRight ? minAng + k : 180 - minAng - k;
                    if (toRight ? ang > 90 : ang < 90) break;
                    int frame = trace(gun, me, bulletId, ang, force, timed ? 0 : 30, land, bx, by, bw, bh);
                    if (frame > 0) return refine(gun, me, foe, bulletId, ang, force, timed ? frame : 0, land, minAng);
                }
            }
        }
        return null;
    }

    /**
     * Around the first path that gets there, the shot that hurts the target most: people aim the middle of a spread
     * (King Kong's bananas, Electrician's bolts), not its edge. reach: frame the bullet got there (timed shots), else 0.
     */
    static Shot refine(Tracer gun, Player me, Player foe, int bulletId, int ang, int force, int reach, boolean land, int minAng) {
        Shot best = null;
        int bestScore = -1;
        for (int f = Math.max(1, force - 1); f <= Math.min(30, force + 1); f++) {
            for (int a = ang - 3; a <= ang + 3; a++) {
                if (ang <= 90 ? a < minAng || a > 90 : a < 90 || a > 180 - minAng) continue;
                int split = reach > 0 ? bestSplit(gun, me, foe, bulletId, a, f, reach + 2, land) : 30;
                int score = splash(gun, me, foe, bulletId, a, f, split, land);
                if (score > bestScore || (score == bestScore && a == ang && f == force)) {
                    bestScore = score;
                    best = new Shot(a, f, split);
                }
            }
        }
        return best;
    }

    /** Frame (1+) at which a bullet of this shot is inside the box, 0 if none gets there. */
    static int trace(Tracer gun, Player me, int bulletId, int ang, int force, int force2, boolean land, int bx, int by, int bw, int bh) {
        gun.shootBullet(bulletId, me.x, me.y, me.width, me.height, ang, force, force2, 1, 100, -1);
        for (Bullet b : gun.bullets) {
            b.isCanCollisionMap = land;
            b.isCanCollisionPlayer = true;
        }
        for (int guard = 0; guard < 3000; guard++) {
            boolean next = false;
            for (Bullet b : gun.bullets.toArray(new Bullet[0])) {
                b.isCanCollision = false;
                if (b.bX >= bx && b.bX < bx + bw && b.bY >= by && b.bY < by + bh) return Math.max(1, b.frame);
                if (!b.collect) {
                    next = true;
                    b.nextXY();
                }
            }
            if (!next) break;
        }
        return 0;
    }

    /**
     * Apache: the split frame (up to the frame his hammer would reach the body) whose hammers hurt the target most;
     * Chicky: the frame to drop the egg so that chicken and egg hurt the most.
     */
    static int bestSplit(Tracer gun, Player me, Player foe, int bulletId, int ang, int force, int reach, boolean land) {
        int best = reach, bestScore = -1;
        for (int split = Math.max(1, reach - 16); split <= reach; split++) {
            int score = splash(gun, me, foe, bulletId, ang, force, split, land);
            if (score > bestScore) {
                bestScore = score;
                best = split;
            }
        }
        return best;
    }

    /** Splash damage (before defense) the explosions of this shot would deal to foe, the map left as it is. */
    static int splash(Tracer gun, Player me, Player foe, int bulletId, int ang, int force, int force2, boolean land) {
        gun.shootBullet(bulletId, me.x, me.y, me.width, me.height, ang, force, force2, 1, 100, -1);
        for (Bullet b : gun.bullets) b.isCanCollisionMap = land;
        java.util.Set<Bullet> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        int total = 0;
        for (int guard = 0; guard < 3000; guard++) {
            boolean next = false;
            for (Bullet b : gun.bullets.toArray(new Bullet[0])) {
                b.isCanCollision = false;
                if (!b.collect) {
                    next = true;
                    b.nextXY();
                }
                if (b.collect && seen.add(b) && b.radius > 0) total += foe.calcAtt(b.bX, b.bY, b.radius, b.att);
            }
            if (!next) break;
        }
        return total;
    }

    /** A match map whose terrain lookups use a bitmap (kept in step with every hole) instead of scanning bricks. */
    static final class SimMap extends MapData {
        static final ConcurrentHashMap<Integer, Object[]> BRICKS = new ConcurrentHashMap<>();
        static Unsafe unsafe;
        boolean[] solid;
        int ox, oy, sw, sh;

        private SimMap() throws java.io.IOException {
            super(null, (byte) 0);
        }

        static SimMap create(int mapId) throws Exception {
            if (unsafe == null) {
                Field field = Unsafe.class.getDeclaredField("theUnsafe");
                field.setAccessible(true);
                unsafe = (Unsafe) field.get(null);
            }
            SimMap m = (SimMap) unsafe.allocateInstance(SimMap.class);
            m.map = mobiarmy.server.Map.get(mapId);
            m.mapId = mapId;
            m.width = m.map.data.width;
            m.height = m.map.data.height;
            m.data = new ArrayList<>();
            m.tornados = new ArrayList<>();
            m.timeBombs = new ArrayList<>();
            m.bossAdds = new ArrayList<>();
            m.protectedGround = new ArrayList<>();
            m.pendingEggs = new ArrayList<>();
            for (mobiarmy.server.Map.Brick brick : m.map.data.bricks) {
                Object[] img = BRICKS.computeIfAbsent(brick.id, id -> {
                    try {
                        BufferedImage bi = ImageIO.read(new File("res/icon/map/" + id + ".png"));
                        int[] argb = new int[bi.getWidth() * bi.getHeight()];
                        bi.getRGB(0, 0, bi.getWidth(), bi.getHeight(), argb, 0, bi.getWidth());
                        return new Object[] {argb, bi.getWidth(), bi.getHeight()};
                    } catch (java.io.IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                m.data.add(m.new Image(brick.id, brick.x, brick.y, (int[]) img[0], (short) (int) img[1], (short) (int) img[2], !isNotColision(brick.id)));
            }
            m.players = new Player[Room.MAX_E_PLAYER]; // summons (Magenta's UFO) take slots from Room.INDEX_START on
            m.indexTurnPlayer = -1;
            m.indexTurnBoss = -1;
            m.indexBoss = Room.INDEX_START;
            m.typeComplete = -1;
            m.completeWar = -1;
            m.maxSecondTurn = 25;
            m.team = -1;
            m.rules = RoomRules.FREE;
            m.buildSolid();
            return m;
        }

        void buildSolid() {
            int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
            for (Image img : this.data) {
                x0 = Math.min(x0, img.x);
                y0 = Math.min(y0, img.y);
                x1 = Math.max(x1, img.x + img.width);
                y1 = Math.max(y1, img.y + img.height);
            }
            if (this.data.isEmpty()) x0 = y0 = x1 = y1 = 0;
            this.ox = x0;
            this.oy = y0;
            this.sw = Math.max(1, x1 - x0);
            this.sh = Math.max(1, y1 - y0);
            this.solid = new boolean[this.sw * this.sh];
            for (Image img : this.data) {
                for (int j = 0; j < img.height; j++) {
                    for (int i = 0; i < img.width; i++) {
                        if (img.isNotAlpha(img.argb[j * img.width + i])) this.solid[(img.y + j - this.oy) * this.sw + img.x + i - this.ox] = true;
                    }
                }
            }
        }

        @Override
        public boolean isCollisionMap(int x, int y) {
            int i = x - this.ox, j = y - this.oy;
            return i >= 0 && j >= 0 && i < this.sw && j < this.sh && this.solid[j * this.sw + i];
        }

        /** As MapData.isCollisionPlayer, but only over the slots in use (the array has room for 100 entities). */
        @Override
        public boolean isCollisionPlayer(int x, int y) {
            for (int i = 0; i < this.indexBoss && i < this.players.length; i++) {
                Player player = this.players[i];
                if (player != null && player.isCollision && player.isCollision(x, y) && player.countInvisible2 == 0) return true;
            }
            return false;
        }

        @Override
        public void makeHole(int x, int y, int bulletType, int scale) {
            super.makeHole(x, y, bulletType, scale);
            BufferedImage mask = GameData.holeIMask[GameData.getHoleType(bulletType)];
            int w = mask.getWidth() * scale / 100 + 4, h = mask.getHeight() * scale / 100 + 4;
            for (int yy = y - h / 2 - 2; yy <= y + h / 2 + 2; yy++) {
                for (int xx = x - w / 2 - 2; xx <= x + w / 2 + 2; xx++) {
                    int i = xx - this.ox, j = yy - this.oy;
                    if (i < 0 || j < 0 || i >= this.sw || j >= this.sh || !this.solid[j * this.sw + i]) continue;
                    this.solid[j * this.sw + i] = super.isCollisionMap(xx, yy);
                }
            }
        }

        @Override
        public void endTheWar() {
            if (!this.isWar || this.typeComplete == -1) return;
            this.isWar = false;
        }
    }
}
