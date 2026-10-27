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
 * full, walk only when they have no shot or to step off an Easter egg, and use no item but the Teleport: on the
 * server's bigger maps (mobiarmy.server.Map.SCALE, -Darmy.map.scale) an opponent more than TELEPORT_FAR px away is
 * out of reach, so when it is ready (every 3 turns) they teleport to about TELEPORT_GAP px from him.
 *
 * <p>Args (key=value): matches, threads, rules (0 free, 1 balanced, 2 base), tier, maxTurns, sf / sa / s2 / sw (hand
 * error of force, angle, force2, and per full wind), evade (chance to step off an egg or from under a UFO), ufo (chance to
 * shoot down an opponent's UFO that one shot kills), hp / att / def / luck (stat
 * points on top of the character's start), pow, heroes (e.g. 5,6), attN (override character N's attack), matrix,
 * teleport (true by default on maps bigger than the originals).
 */public class DuelSim {
    static int matches = 2000, threads = Runtime.getRuntime().availableProcessors(), rules = RoomRules.BALANCED,
            tier = 6, maxTurns = 120, ptsHp = 0, ptsAtt = 0, ptsDef = 0, ptsLuck = 0;
    static double sigmaForce = 0.6, sigmaAng = 1.0, sigmaF2 = 1.0, sigmaWind = 1.0, evadeChance = 0.5, ufoChance = 0.6;
    static boolean usePow = true, matrix = false, useTeleport = mobiarmy.server.Map.SCALE > 1, team2 = false;
    /** 2v2: side 1 always two different ones of these characters (duo=a+b, or a+b+c for a group), null for random sides. */
    static int[] duo;
    /** The +15 set element (SetElement) of side 1's and side 2's players (el1, el2), at elevel (0 +15, 1 +16). */
    static int el1 = SetElement.NONE, el2 = SetElement.NONE, elevel = 0;
    /** 1v1: every duel is this character against each of the others in turn (focus=N), -1 for all pairs. */
    static int focus = -1;
    /** 2v2: with the team passives (SetTeam), false to measure a group without its own (teams=false). */
    static boolean teamPassives = true;
    /** Games side 1 won (a draw counts half, as two halves), of sideGames. */
    static long sideWins2, sideGames;
    /** 2v2 (team=true): wins of each pair of characters as one side, keyed lower*10+higher. */
    static final long[] duoWins = new long[100], duoGames = new long[100];
    /** Teleport when the opponent is farther than this (px), to land about TELEPORT_GAP from him, within TELEPORT_MISS. */
    static final int TELEPORT_FAR = 600, TELEPORT_GAP = 250, TELEPORT_MISS = 120;
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
        long pows, powDealt, shotDealt, teleports;
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
                case "teleport" -> useTeleport = Boolean.parseBoolean(v);
                case "evade" -> evadeChance = Double.parseDouble(v);
                case "ufo" -> ufoChance = Double.parseDouble(v);
                case "matrix" -> matrix = Boolean.parseBoolean(v);
                case "team" -> team2 = Boolean.parseBoolean(v);
                case "heroes" -> heroes = Arrays.stream(v.split(",")).mapToInt(Integer::parseInt).toArray();
                case "duo" -> duo = Arrays.stream(v.split("[,+]")).mapToInt(Integer::parseInt).toArray(); // (4+5 through Run-Sim)
                case "focus" -> focus = Integer.parseInt(v);
                case "teams" -> teamPassives = Boolean.parseBoolean(v);
                case "el1" -> el1 = Integer.parseInt(v);
                case "el2" -> el2 = Integer.parseInt(v);
                case "elevel" -> elevel = Integer.parseInt(v);
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
            for (int j = i + 1; j < heroes.length; j++) if (focus < 0 || heroes[i] == focus || heroes[j] == focus) pairs.add(new int[] {heroes[i], heroes[j]});
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
                    if (team2) runTeamMatch(); else runMatch(a, b);
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
        if (team2) reportDuos();
        double side = sideGames == 0 ? 0 : 50.0 * sideWins2 / sideGames;
        System.out.printf(Locale.ROOT, "%nside 1 (%s, element %s +%d) against side 2 (element %s): %.1f%% +- %.1f of %d%n",
                duo == null ? "random" : NAMES[duo[0]] + " + " + NAMES[duo[1]], SetElement.NAMES[el1].isEmpty() ? "none" : SetElement.NAMES[el1],
                elevel == 0 ? 15 : 16, SetElement.NAMES[el2].isEmpty() ? "none" : SetElement.NAMES[el2], side,
                196 * Math.sqrt(Math.max(side, 1) * Math.max(100 - side, 1) / 10000.0 / Math.max(1, sideGames)), sideGames);
    }

    static void report(double seconds) {
        System.out.printf(Locale.ROOT, "%d duels, rules %d, set tier %d, hand error force %.2f angle %.2f force2 %.2f wind %.2f,"
                + " evade %.2f, +hp %d +att %d +def %d +luck %d, maps x%d, teleport %b, %.0fs%n",
                matches, rules, tier, sigmaForce, sigmaAng, sigmaF2, sigmaWind, evadeChance, ptsHp, ptsAtt, ptsDef, ptsLuck,
                mobiarmy.server.Map.SCALE, useTeleport, seconds);
        StringBuilder atts = new StringBuilder();
        for (int h : heroes) atts.append(NAMES[h]).append(' ').append(Glass.entrys[h].att).append(", ");
        System.out.println("att: " + atts);
        System.out.println("hero          win%    +-   W-L-D            dealt  taken  turns  hit%  kills fall-k fall-d eggs  /shot  /pow  tp");
        Integer[] order = Arrays.stream(heroes).boxed().toArray(Integer[]::new);
        Arrays.sort(order, (x, y) -> Double.compare(rate(tally[y]), rate(tally[x])));
        for (int h : order) {
            Tally t = tally[h];
            double p = rate(t), n = Math.max(1, t.games);
            double ci = 196 * Math.sqrt(Math.max(p, 1) * Math.max(100 - p, 1) / 10000.0 / n);
            System.out.printf(Locale.ROOT, "%-12s %5.1f  %4.1f   %4d-%4d-%3d  %6d %6d %6.1f %5.1f %6.2f %6.2f %6.2f %5.2f %6d %5d %4.1f%n",
                    NAMES[h], p, ci, t.wins, t.losses, t.draws, t.dealt / (long) n, t.taken / (long) n, t.turns / n,
                    t.shots == 0 ? 0 : 100.0 * t.hits / t.shots, t.kills / n, t.fallKills / n, t.fallDeaths / n, t.eggs / n,
                    t.shots == t.pows ? 0 : t.shotDealt / (t.shots - t.pows), t.pows == 0 ? 0 : t.powDealt / t.pows, t.teleports / n);
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
            sideGames++;
            sideWins2 += result == 1 ? 2 : result == 2 ? 0 : 1;
        }
    }

    /**
     * 2v2 (team=true): two random sides of two different characters each (the same character may be on both sides),
     * with the team passives (SetTeam) and the Jungle Pact; each bot shoots the nearest living opponent.
     */
    static void runTeamMatch() throws Exception {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int a1 = heroes[r.nextInt(heroes.length)], a2, b1 = heroes[r.nextInt(heroes.length)], b2;
        do a2 = heroes[r.nextInt(heroes.length)]; while (a2 == a1);
        do b2 = heroes[r.nextInt(heroes.length)]; while (b2 == b1);
        if (duo != null) { // (more than two: two different ones of them, as a group of a team passive)
            a1 = duo[r.nextInt(duo.length)];
            do a2 = duo[r.nextInt(duo.length)]; while (a2 == a1);
        }
        SimMap map = SimMap.create(MAPS[Util.nextInt(MAPS.length)]);
        map.rules = (byte) rules;
        map.skillRules = rules == RoomRules.BALANCED;
        List<mobiarmy.server.Map.Point> points = new ArrayList<>(Arrays.asList(map.map.data.points));
        int[] glass = {a1, b1, a2, b2}; // turns alternate sides
        Player[] ps = new Player[4];
        for (int i = 0; i < 4; i++) ps[i] = spawn(map, i, glass[i], i % 2 == 0 ? 1 : 2, points);
        SetTeam.apply(map, rules == RoomRules.BASE || !teamPassives);
        Brain[] brains = new Brain[4];
        for (int i = 0; i < 4; i++) brains[i] = new Brain(ps[i], ps[(i + 1) % 4]);
        map.isWar = true;
        map.isTurn = true;
        int turns = 0, idle = 0;
        while (map.isWar && turns < maxTurns * 2) {
            map.update();
            if (!map.isWar) break;
            int t = map.getTurn();
            if (t < 0) break;
            Player me = map.players[t];
            if (++idle > 5000) { // only summons' or skipped turns this long: tell what is left and call it a draw
                StringBuilder left = new StringBuilder("STUCK 2v2 map " + map.mapId + " " + map.width + "x" + map.height + " turn " + t + ":");
                for (Player p : map.players) {
                    if (p == null) continue;
                    left.append(String.format(Locale.ROOT, " [%d %s team %d type %d hp %d/%d die %b faction %b shoot %b xy %d,%d]", p.index,
                            p.name, p.team, p.typePlayer, p.hp, p.hpMax, p.isDie, p.isFaction, p.isShoot, p.x, p.y));
                }
                System.err.println(left);
                break;
            }
            if (t >= 4) {
                map.timeUntilAction2 = 0;
                if (!map.isTurn) me.update();
                map.isTurn = true;
                continue;
            }
            if (me.isDie || me.isShoot) {
                map.isTurn = true;
                continue;
            }
            idle = 0;
            turns++;
            brains[t].act();
            if (!me.isShoot) map.isTurn = true;
        }
        if (map.isWar) map.updateComplete();
        int result = map.typeComplete; // 1: team 1 won, 2: team 2 won, else a draw
        synchronized (tally) {
            for (int i = 0; i < 4; i++) {
                int side = i % 2 == 0 ? 1 : 2;
                record(glass[i], ps[i], brains[i], result == side ? 1 : result == 3 - side ? -1 : 0, map);
            }
            int da = Math.min(a1, a2) * 10 + Math.max(a1, a2), db = Math.min(b1, b2) * 10 + Math.max(b1, b2);
            duoGames[da]++;
            duoGames[db]++;
            if (result == 1) duoWins[da] += 2;
            else if (result == 2) duoWins[db] += 2;
            else { duoWins[da]++; duoWins[db]++; }
            sideGames++;
            sideWins2 += result == 1 ? 2 : result == 2 ? 0 : 1;
        }
    }

    static void reportDuos() {
        System.out.println("\nsides (2 characters)          win%    +-  games  passive");
        List<Integer> duos = new ArrayList<>();
        for (int d = 0; d < 100; d++) if (duoGames[d] > 0) duos.add(d);
        duos.sort((x, y) -> Double.compare(duoWins[y] * 1.0 / duoGames[y], duoWins[x] * 1.0 / duoGames[x]));
        for (int d : duos) {
            double p = 50.0 * duoWins[d] / duoGames[d];
            double ci = 196 * Math.sqrt(Math.max(p, 1) * Math.max(100 - p, 1) / 10000.0 / duoGames[d]);
            int x = d / 10, y = d % 10;
            String pact = "";
            for (int g = 0; g < SetTeam.GROUPS.length; g++) {
                boolean hx = false, hy = false;
                for (int k : SetTeam.GROUPS[g]) { hx |= k == x; hy |= k == y; }
                if (hx && hy) pact = SetTeam.NAMES[g];
            }
            if ((x == 3 && y == 7)) pact = "Khế ước rừng xanh";
            System.out.printf(Locale.ROOT, "%-12s + %-12s %5.1f  %4.1f  %5d  %s%n", NAMES[x], NAMES[y], p, ci, duoGames[d], pact);
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
        t.teleports += brain.teleports;
    }

    static Player spawn(SimMap map, int index, int glassId, int team, List<mobiarmy.server.Map.Point> points) {
        Glass glass = Glass.entrys[glassId];
        int[] s = stats(glass);
        byte[] items = useTeleport ? new byte[] {(byte) Player.TELEPORT_ITEM} : null;
        Player p = new Player(map, null, NAMES[glassId], glassId, 50, s[0], s[1], s[2], s[3], s[4], 24, 24, items);
        p.index = (byte) index;
        p.setTier = rules == RoomRules.BASE ? 0 : tier;
        if (rules != RoomRules.BASE) SetElement.give(p, team == 1 ? el1 : el2, elevel);
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
        final Player me;
        Player foe;
        long shots, hits, eggsLaid, pows, powDealt, shotDealt, teleports;
        int seenX, seenY;
        boolean seen;

        Brain(Player me, Player foe) {
            this.me = me;
            this.foe = foe;
        }

        void act() {
            MapData map = this.me.mapData;
            this.retarget();
            // Far away on a big map he teleports nearer this turn (one item a turn), keeping his POW for later.
            boolean far = useTeleport && Math.abs(this.foe.x - this.me.x) > TELEPORT_FAR && this.me.teleportCooldown == 0;
            if (usePow && this.me.angry >= 100 && !far) map.useItem(this.me.index, (byte) 100);
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
            // The opponent far away on a big map is out of reach: get nearer with the Teleport when it is ready.
            if (far && this.teleport()) return;
            Shot shot = aim(this.me, this.foe, this.seenX, this.seenY, true);
            if (shot == null) {
                walk(this.me, this.seenX >= this.me.x ? 1 : -1);
                shot = aim(this.me, this.foe, this.seenX, this.seenY, true);
            }
            if (shot == null) shot = aim(this.me, this.foe, this.seenX, this.seenY, false);
            if (shot == null) shot = new Shot(this.seenX >= this.me.x ? 45 : 135, 20, 30);
            this.fire(shot);
        }

        /**
         * Teleports to about TELEPORT_GAP px short of the opponent (on his near side), if the Teleport is ready and some
         * shot of it lands within TELEPORT_MISS of there; true if he went.
         */
        boolean teleport() {
            MapData map = this.me.mapData;
            if (this.me.teleportCooldown > 0 || this.me.findUnusedItemById(Player.TELEPORT_ITEM) == null) return false;
            int dir = this.seenX >= this.me.x ? 1 : -1;
            int tx = this.seenX - dir * TELEPORT_GAP;
            Tracer gun = new Tracer(map, this.me.index, false);
            Shot best = null;
            int bestMiss = TELEPORT_MISS + 1;
            for (int force = 6; force <= 30; force++) {
                for (int a = 15; a <= 85; a++) {
                    int ang = dir > 0 ? a : 180 - a;
                    int x = landing(gun, this.me, ang, force);
                    if (x != Integer.MIN_VALUE && Math.abs(x - tx) < bestMiss) {
                        bestMiss = Math.abs(x - tx);
                        best = new Shot(ang, force, 0);
                    }
                }
            }
            if (best == null) return false;
            map.useItem(this.me.index, (byte) Player.TELEPORT_ITEM);
            if (this.me.bulletId != 5) return false; // refused
            this.teleports++;
            map.shoot(this.me.index, -1, best.ang, best.force, 0);
            return true;
        }

        /**
         * 2v2: the nearest living opponent, the Scientists' T-rex too (a new one when his falls; where he was last seen
         * is forgotten).
         */
        void retarget() {
            Player best = this.foe != null && !this.foe.isDie ? this.foe : null;
            for (Player p : this.me.mapData.players) {
                if (p == null || p.isDie || p.gift || p.team == this.me.team) continue;
                if (p.typePlayer != 0 && !(p instanceof mobiarmy.war.Boss.ScienceTrex)) continue;
                if (best == null || Math.abs(p.x - this.me.x) < Math.abs(best.x - this.me.x)) best = p;
            }
            if (best != null && best != this.foe) this.seen = false;
            if (best != null) this.foe = best;
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
            // Chilled by the ice (SetElement): he sees it over his head and pushes harder, as far as the bar goes.
            if (this.me.chilledNow && this.me.chillForce > 0) shot.force = Math.min(30, (int) Math.round(shot.force * 100.0 / (100 - this.me.chillForce)));
            this.handError(shot);
            int dealt = this.me.statDealt;
            boolean pow = this.me.isPow;
            long eggs = map.pendingEggs.stream().filter(e -> e.egg).count();
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
            this.eggsLaid += Math.max(0, map.pendingEggs.stream().filter(e -> e.egg).count() - eggs);
        }

        /** Share of the wind (percent) this turn's shot feels: the bullet's own share, after the shooter's passive. */
        int windFelt() {
            if (SetPassive.worldWar(this.me, this.me.isPow) || SetPassive.laserSower(this.me, this.me.isPow)) return 0;
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
                if (!(bomb.egg || bomb.grenade) || bomb.leadIndex == this.me.index) continue;
                // An egg's blast, or one of Granos's lasers (a step's width either side of his grenade).
                int reach = bomb.egg ? SetPassive.CHICKY_EGG_RADIUS + 6 : SetPassive.GRANOS_LASER_RADIUS + 12;
                if (Math.hypot(this.me.x - bomb.x, this.me.y - bomb.y) <= reach) {
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
            if (map.isCollisionMap(nx, y - Player.CLIMB - 1)) break;
            int ny = -1;
            for (int k = Player.CLIMB; k >= 0; k--) {
                if (map.isCollisionMap(nx, y - k)) {
                    ny = y - k;
                    break;
                }
            }
            if (ny < 0) {
                ny = y;
                while (ny < map.height + 200 && !map.isCollisionMap(nx, ny)) ny++;
            }
            if (ny > map.height + 50 || ny - y > 80 * mobiarmy.server.Map.SCALE) break;
            x = nx;
            y = ny;
            to = x;
        }
        if (to == me.x) return false;
        me.updateXY(to, me.y);
        return true;
    }

    /**
     * Where a Teleport (bullet 5: no wind, through players, stops on the ground) fired at ang, force would land: its x,
     * or Integer.MIN_VALUE when it falls off the map. Traced with a plain bullet, so nobody moves.
     */
    static int landing(Gun gun, Player me, int ang, int force) {
        int w = me.bodyWidth(), h = me.bodyHeight(); // as MapData.shoot fires it
        int bx = me.x + ((w - 4) * GameData.cos(ang) >> 10);
        int by = me.y - (h / 2) - ((h - 4) * GameData.sin(ang) >> 10);
        int vx = force * GameData.cos(ang) >> 10;
        int vy = -(force * GameData.sin(ang) >> 10);
        Bullet b = new Bullet(gun, 5, 0, bx, by, vx, vy, 0, 0, 80);
        b.isCanCollision = false;
        b.isCanCollisionPlayer = false;
        for (int guard = 0; guard < 4000 && !b.collect; guard++) b.nextXY();
        return b.collect && me.mapData.isCollisionMap(b.bX, b.bY) ? b.bX : Integer.MIN_VALUE;
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
        boolean flare = SetPassive.worldWar(me, me.isPow) || SetPassive.laserSower(me, me.isPow);
        int minAng = flare ? -60 : Glass.entrys[me.glassID].angle;
        int bw = foe.bodyWidth(), bh = foe.bodyHeight(), bx = tx - bw / 2, by = ty - bh; // King Kong grows standing still
        boolean right = tx >= me.x;
        boolean timed = bulletId == 17 || bulletId == 19; // the shot's force2 matters: find the path without it first
        Tracer gun = new Tracer(me.mapData, me.index, me.isPow);
        // Electrician's shock: slowed, no aim below 60 degrees; locked, only the angle of his last shot.
        if (me.paralyzed >= 1) minAng = Math.min(90, minAng + SetPassive.ELECTRICIAN_RAISE);
        if (me.paralyzed == 2 && me.lastAng != Integer.MIN_VALUE) {
            for (int force = 1; force <= 30; force++) {
                if (trace(gun, me, bulletId, me.lastAng, force, 30, land, bx, by, bw, bh) > 0) return new Shot(me.lastAng, force, 30);
            }
            return null;
        }
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
        gun.shootBullet(bulletId, me.x, me.y, me.bodyWidth(), me.bodyHeight(), ang, force, force2, 1, 100, -1);
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
        gun.shootBullet(bulletId, me.x, me.y, me.bodyWidth(), me.bodyHeight(), ang, force, force2, 1, 100, -1);
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
                        // The bricks as the matches use them: mobiarmy.server.Map.SCALE times bigger.
                        mobiarmy.server.Map.BrickImage bi = mobiarmy.server.Map.brick(id);
                        return new Object[] {bi.argb, bi.width, bi.height};
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
            m.trexTeams = new java.util.HashSet<>();
            m.trexPending = new java.util.ArrayList<>();
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
