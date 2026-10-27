package mobiarmy.war;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import mobiarmy.io.Message;
import mobiarmy.server.Session;
import mobiarmy.server.SessionHandler;
import mobiarmy.war.Boss.bullet.ApacheBullet;
import mobiarmy.war.Boss.bullet.Bullet;
import sun.misc.Unsafe;

/**
 * Apache's set passive "Thợ săn ẩn nấp": every 3 of his hammers that hurt an opponent hide him from opponents and let
 * him walk twice as far until he loses HP, and each hammer of his POW that hits an opponent heals him. Real Player,
 * Bullet and SessionHandler code on an empty map with no database (the SetPassiveCheck fixture).
 */
public class ApacheHuntCheck {
    static int checks;

    /** Records who the map shows again (message 80) instead of sending it. */
    static final class RecordingMap extends MapData {
        List<Integer> shown;

        RecordingMap() throws IOException {
            super(null, (byte) 0); // never called: map() allocates without constructors
        }

        @Override
        public void cancelInvisible(int index) {
            shown.add(index);
        }
    }

    /** A game connection that only records what the server sends. */
    static final class Capture extends Session {
        List<byte[]> sent;
        List<Integer> commands;

        Capture() throws Exception {
            super(null, -1); // never called either
        }

        @Override
        public void sendMessage(Message message) {
            commands.add((int) message.getCommand());
            sent.add(message.getData());
        }
    }

    public static void main(String[] args) throws Exception {
        hiding();
        status();
        hammers();
        System.out.println("APACHE HUNT PASSED: " + checks + " checks; " + SetPassive.APACHE_HIDE_HAMMERS + " hammers hide and run, showing again, invisibility item, status 114, POW hammer heals");
    }

    static void hiding() throws Exception {
        RecordingMap map = map();
        Player apache = player(map, 0, 1000, SetPassive.APACHE, 6);
        Player foe = player(map, 1, 1100, 5, 0);
        Player mate = player(map, 2, 1300, 5, 0);
        for (int turn = 0; turn < 5; turn++) SetPassive.newTurn(apache);
        check(!apache.huntHidden, "rounds without losing HP no longer hide him");
        shot(map, apache, foe, eighteens(SetPassive.APACHE_HIDE_HAMMERS - 1));
        check(!apache.huntHidden && apache.huntCount == SetPassive.APACHE_HIDE_HAMMERS - 1, (SetPassive.APACHE_HIDE_HAMMERS - 1) + " hammers hit: not hidden yet");
        shot(map, apache, foe, 17);
        check(apache.huntHidden && apache.huntRun && apache.huntCount == 0, "the " + SetPassive.APACHE_HIDE_HAMMERS + "th hammer (a whole one, no split) hides him and he runs");
        check(map.shown.isEmpty(), "hiding itself sends nothing (the status after the shot does)");
        shot(map, apache, foe, 18, 18);
        check(apache.huntCount == 0, "hammers while hidden do not count");
        int x = apache.x;
        apache.buocdi = 0;
        apache.updateXY(x + 20, apache.y);
        check(apache.x == x + 20 && apache.buocdi == 10, "running, he walks 2 px a step: " + apache.buocdi + " steps");
        check(apply(map, foe, apache) > 0, "a foe's shot hurts him");
        check(!apache.huntHidden && !apache.huntRun && map.shown.equals(List.of(0)),
                "losing HP shows him to everyone and he walks as usual: " + map.shown);
        x = apache.x;
        apache.buocdi = 0;
        apache.updateXY(x + 20, apache.y);
        check(apache.x == x + 20 && apache.buocdi == 20, "seen, 1 px a step again");

        // What counts: his own hammers that hurt an opponent, not the puff where a hammer splits, not bullet 0.
        SetPassive.beforeShot(apache);
        foe.attBullet(hammer(map, apache, 17, foe, ApacheBullet.SPLIT_ATT));
        mate.attBullet(hammer(map, apache, 18, mate, 1000));
        foe.attBullet(hammer(map, apache, 0, foe, 1000));
        SetPassive.afterShot(apache);
        check(apache.huntCount == 0, "the split's puff, a hammer on a mate and a plain bullet do not count");
        shot(map, apache, foe, eighteens(SetPassive.APACHE_HIDE_HAMMERS));
        check(apache.huntHidden && apache.huntRun, "the count started over: " + SetPassive.APACHE_HIDE_HAMMERS + " more hammers hide him again");

        // The invisibility item (4) keeps hiding him when he is hit; it shows him when it runs out, not before.
        map.shown.clear();
        apache.countInvisible = 2;
        apply(map, foe, apache);
        check(!apache.huntHidden && !apache.huntRun && map.shown.isEmpty(), "with the item still on, losing HP does not show him");
        apache.updateTurn();
        check(map.shown.isEmpty() && apache.countInvisible == 1, "item turn 1 of 2: still hidden");
        apache.updateTurn();
        check(map.shown.equals(List.of(0)), "the item runs out and he is shown: " + map.shown);
        map.shown.clear();
        shot(map, apache, foe, eighteens(SetPassive.APACHE_HIDE_HAMMERS));
        apache.countInvisible = 1;
        apache.updateTurn();
        check(apache.huntHidden && map.shown.isEmpty(), "the item running out does not show him while the passive hides him");
        apache.die();
        check(!apache.huntHidden && !apache.huntRun, "a dead hunter is not hidden and does not run");

        RecordingMap other = map();
        Player plain = player(other, 0, 1000, SetPassive.APACHE, 0);
        Player gunner = player(other, 2, 1200, SetPassive.GUNNER, 6);
        Player target = player(other, 1, 1100, 5, 0);
        shot(other, plain, target, 18, 18, 18, 18, 18);
        shot(other, gunner, target, 18, 18, 18, 18, 18);
        check(!plain.huntHidden, "Apache without a full +14 set never hides");
        check(!gunner.huntHidden, "only Apache hides");
    }

    static void status() throws Exception {
        RecordingMap map = map();
        Player apache = player(map, 0, 1000, SetPassive.APACHE, 6);
        Player foe = player(map, 3, 1100, 5, 0);
        foe.antiHealTurns = 2;
        apache.huntHidden = true;
        apache.huntRun = true;
        Capture session = session();
        session.sessionHandler.status(map.players);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(session.sent.get(0)));
        check(session.commands.equals(List.of(114)), "status is message 114");
        check(in.readUnsignedByte() == 2, "two players");
        check(in.readUnsignedByte() == 0 && !in.readBoolean() && !in.readBoolean(), "Apache: not poisoned, not stunned");
        check(in.readUnsignedByte() == 3 && in.readBoolean() && !in.readBoolean(), "the foe: poisoned");
        check(in.readUnsignedByte() == 1 && in.readUnsignedByte() == 0, "then the hidden list: Apache only");
        check(in.readUnsignedByte() == 1 && in.readUnsignedByte() == 0 && in.readUnsignedByte() == 6,
                "then who has a set passive: Apache at tier 6");
        check(in.readUnsignedByte() == 1 && in.readUnsignedByte() == 0, "then who runs twice as far: Apache");
        check(in.readUnsignedByte() == 0 && in.readUnsignedByte() == 0,
                "then (build 7) who has grown, King Kong standing still, and whose Teleport waits: nobody");
        check(in.readUnsignedByte() == mobiarmy.server.Map.SCALE, "then how many times bigger the maps are");
        check(in.readUnsignedByte() == 0, "then who is shocked by Electrician: nobody");
        check(in.readUnsignedByte() == 0, "then (build 8) who the +15 set elements strike: nobody");
        check(in.readUnsignedByte() == 0 && in.available() == 0, "and last (build 14) whose element charges: nobody's");
        apache.huntHidden = false;
        apache.huntRun = false;
        session.sessionHandler.status(map.players);
        in = new DataInputStream(new ByteArrayInputStream(session.sent.get(1)));
        in.skipBytes(1 + 2 * 3);
        check(in.readUnsignedByte() == 0 && in.available() == 10, "nobody hidden: an empty list (the set tiers, empty run, size and cooldown lists, the map scale, empty shock, element and charge lists follow)");
    }

    static void hammers() throws Exception {
        RecordingMap map = map();
        Player apache = player(map, 0, 1000, SetPassive.APACHE, 6);
        Player foe = player(map, 1, 1100, 5, 0);
        Player mate = player(map, 2, 1300, 5, 0);
        Player foe2 = player(map, 3, 1110, 5, 0);
        apache.hpMax = 1000;

        apache.hp = 100;
        apache.isPow = true;
        SetPassive.beforeShot(apache);
        Bullet split = hammer(map, apache, 18, foe);
        foe.attBullet(split);
        foe2.attBullet(split);
        foe2.attBullet(hammer(map, apache, 18, foe2));
        mate.attBullet(hammer(map, apache, 18, mate));
        foe.attBullet(hammer(map, apache, 17, foe));
        foe.attBullet(hammer(map, apache, 0, foe));
        SetPassive.afterShot(apache);
        check(apache.hp == 100 + 3 * (1000 * SetPassive.APACHE_HEAL_PERCENT / 100), "POW: 3 hammers hit foes (one of them two foes; the mate's and a non-hammer bullet do not count): +" + SetPassive.APACHE_HEAL_PERCENT + "% each, hp " + apache.hp);

        apache.hp = 100;
        apache.isPow = false;
        SetPassive.beforeShot(apache);
        foe.attBullet(hammer(map, apache, 18, foe));
        SetPassive.afterShot(apache);
        check(apache.hp == 100, "no POW, no heal");

        apache.isPow = true;
        apache.setTier = 1;
        SetPassive.beforeShot(apache);
        foe.attBullet(hammer(map, apache, 18, foe));
        SetPassive.afterShot(apache);
        check(apache.hp == 100 + 1000 * (SetPassive.APACHE_HEAL_PERCENT * SetPassive.STRENGTH[1] / 100) / 100, "set 1: half per hammer, hp " + apache.hp);

        apache.hp = 100;
        apache.setTier = 6;
        apache.antiHealTurns = 1;
        SetPassive.beforeShot(apache);
        foe.attBullet(hammer(map, apache, 18, foe));
        SetPassive.afterShot(apache);
        check(apache.hp == 100 + 1000 * SetPassive.APACHE_HEAL_PERCENT / 100 / 2, "the poison rain halves it, hp " + apache.hp);
        apache.antiHealTurns = 0;

        apache.hp = 100;
        apache.setTier = 0;
        SetPassive.beforeShot(apache);
        foe.attBullet(hammer(map, apache, 18, foe));
        SetPassive.afterShot(apache);
        check(apache.hp == 100, "no set, no heal");

        // His ambush: shots fired while his hunt hides him hit APACHE_AMBUSH percent harder.
        apache.setTier = 6;
        apply(map, apache, foe); // (the hammers above left their damage on him: this takes it)
        apache.huntHidden = false; // (and their count hid Apache)
        int seen = apply(map, apache, foe);
        apache.huntHidden = true;
        int hidden = apply(map, apache, foe);
        apache.huntHidden = false;
        check(Math.abs(hidden - seen * (100 + SetPassive.APACHE_AMBUSH) / 100) <= 2, "from hiding his shots hit " + SetPassive.APACHE_AMBUSH + "% harder: " + hidden + " for " + seen);
        apache.setTier = 0;
        apache.huntHidden = true;
        check(apply(map, apache, foe) == seen, "no set, no ambush");
        apache.huntHidden = false;
    }

    /** n hammers that split (bullet 18). */
    static int[] eighteens(int n) {
        int[] ids = new int[n];
        java.util.Arrays.fill(ids, 18);
        return ids;
    }

    /** One shot of shooter whose bullets (ids) all explode on target. */
    static void shot(MapData map, Player shooter, Player target, int... bulletIds) {
        SetPassive.beforeShot(shooter);
        for (int id : bulletIds) target.attBullet(hammer(map, shooter, id, target, 1000));
        SetPassive.afterShot(shooter);
        target.attAffect = 0;
        target.attDirect = 0;
    }

    /** One of shooter's bullets exploding where `at` stands. */
    static Bullet hammer(MapData map, Player shooter, int bulletId, Player at) {
        return hammer(map, shooter, bulletId, at, 1000);
    }

    static Bullet hammer(MapData map, Player shooter, int bulletId, Player at, int att) {
        Bullet bullet = new Bullet(new Gun(map, shooter.index, false), bulletId, att, at.x, at.y - 12, 0, 0, 0, 0, 0);
        bullet.radius = 16;
        return bullet;
    }

    /** shooter hits target once (bullet 0), as MapData applies a shot; returns the HP target lost. */
    static int apply(MapData map, Player shooter, Player target) {
        target.hp = target.hpMax;
        target.lucky = false;
        shooter.lucky = false;
        target.attBullet(hammer(map, shooter, 0, target));
        target.updateAllAffect(shooter);
        return target.hpMax - target.hp;
    }

    static RecordingMap map() throws Exception {
        RecordingMap map = (RecordingMap) unsafe().allocateInstance(RecordingMap.class);
        map.shown = new ArrayList<>();
        map.width = 3000;
        map.height = 2000;
        map.players = new Player[8];
        map.data = new ArrayList<>();
        map.tornados = new ArrayList<>();
        map.protectedGround = new ArrayList<>();
        map.timeBombs = new ArrayList<>();
        map.pendingEggs = new ArrayList<>();
        return map;
    }

    static Player player(MapData map, int index, int x, int glassId, int setTier) {
        Player player = new Player(map, null, "p" + index, glassId, 1, 100_000, 1000, 0, 0, 0, 24, 24, null);
        player.index = (byte) index;
        player.x = (short) x;
        player.y = 1000;
        player.isFly = true; // keeps chuanHoaXY off the empty map
        player.team = index % 2 + 1;
        player.setTier = setTier;
        map.players[index] = player;
        return player;
    }

    static Capture session() throws Exception {
        Capture session = (Capture) unsafe().allocateInstance(Capture.class);
        session.sent = new ArrayList<>();
        session.commands = new ArrayList<>();
        session.sessionHandler = new SessionHandler(session);
        return session;
    }

    static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        checks++;
        System.out.println("PASS " + what);
    }
}
