package mobiarmy.server;

import java.io.IOException;
import mobiarmy.io.Message;
import mobiarmy.war.Gun;
import mobiarmy.war.MapData;
import mobiarmy.war.Player;

/** A private practice map; never enters the room/reward lifecycle. */
public final class Training {
    private final Session session;
    private final MapData map;
    private boolean started;

    public Training(Session session, byte mapId) throws IOException {
        this.session = session;
        // Any map with two spawn points; anything else falls back to the tutorial map 0.
        Map picked = Map.get(mapId);
        boolean usable = picked != null && picked.data != null && picked.data.points.length >= 2;
        this.map = new MapData(null, usable ? mapId : (byte) 0);
        for (int i = 0; i < 2; i++) {
            Player player = new Player(map, null, i == 0 ? session.user.name : "Enemy",
                    session.user.glass().id, 1, 1000, 0, 0, 0, 0, 24, 24, null);
            Map.Point point = map.map.data.points[i == 0 ? 0 : map.map.data.points.length - 1];
            player.index = (byte) i;
            player.team = i + 1; // the dummy is an opponent (set passives such as Chicky's egg aim at opponents)
            // The player's full +14 set works in practice too (wider holes, sure egg, Gunner's stacks).
            if (i == 0) player.setTier = mobiarmy.war.SetPassive.tier(session.user, session.user.glass().id);
            player.x = point.x;
            player.y = point.y;
            map.players[i] = player;
        }
    }

    public void load() throws IOException {
        Message message = new Message(-6);
        message.writer().writeByte(map.mapId);
        session.sendMessage(message);
    }

    public void start() throws IOException {
        if (started) return;
        started = true;
        Message message = new Message(20);
        for (short equip : session.user.getData()) message.writer().writeShort(equip);
        message.writer().writeByte(map.mapId);
        message.writer().writeByte(25);
        message.writer().writeShort(0);
        for (int i = 0; i < 8; i++) {
            Player player = map.players[i];
            message.writer().writeShort(player == null ? -1 : player.x);
            if (player != null) {
                message.writer().writeShort(player.y);
                message.writer().writeShort(player.hpMax);
            }
        }
        session.sendMessage(message);
    }

    /** Bullets the practice accepts from the client besides the character's own: the items' (MapData.useItem). */
    private static final java.util.Set<Integer> ITEM_BULLETS = java.util.Set.of(4, 5, 6, 7, 8, 13, 14, 16, 22, 23, 25, 26, 28, 30, 51, 52, 54, 55, 56, 57);

    /**
     * Practice items (LAN): free and never used up. POW needs a full rage bar like a match; the others that matter on
     * the server are Chỉ kẻ đầu (Gunner's finisher), Bê tông cốt thép and Cơn mưa độc. Bullet items arrive with the shot.
     */
    public void useItem(byte itemID) {
        if (!started) return;
        Player player = map.players[0];
        switch (itemID) {
            case 100 -> {
                if (player.angry < 100) return;
                player.isPow = true;
                player.angry = 0;
                session.sessionHandler.updateAngry(0, 0);
            }
            case 36 -> player.headGuideShots = 2;
            case 37 -> player.concreteTurns = 2;
            case 38 -> player.poisonRain = true;
            default -> { }
        }
    }

    public void shoot(byte requested, short x, short y, short angle, byte force, byte force2, byte nShoot) {
        if (!started || x < 0 || x >= map.width || y < 0 || y >= map.height) return;
        Player player = map.players[0];
        player.x = x;
        player.y = y;
        // Checked on every shot: gear changed on the web during practice counts at once.
        int tier = mobiarmy.war.SetPassive.tier(session.user, session.user.glass().id);
        player.hiddenAtt = mobiarmy.war.SetPassive.hiddenAtt(session.user, session.user.glass().id);
        if (tier != player.setTier) {
            player.setTier = tier;
            mobiarmy.war.SetPassive.resetCounters(player);
        }
        int own = player.bulletIdByGlassID();
        int bullet = requested == own || ITEM_BULLETS.contains((int) requested) ? requested : own;
        if (player.poisonRain) bullet = 23; // the poison rain falls like Sao băng
        int ang = Math.floorMod(angle, 360), f = Math.max(1, Math.min(30, force)), f2 = Math.max(1, Math.min(30, force2));
        mobiarmy.war.SetPassive.beforeShot(player);
        if (player.passiveFreezeShot() && bullet == 19) f2 = map.sureHitForce2(player, bullet, ang, f, f2, 0);
        map.protectedGround = mobiarmy.war.SetPassive.protectedGround(map);
        Gun gun = new Gun(map, 0, player.isPow);
        gun.shootBullet(bullet, x, y, 24, 24, ang, f, f2, Math.max(1, Math.min(2, nShoot)), 0, -1);
        gun.fillXY();
        if (player.poisonRain) {
            gun.poisonRain = true;
            player.poisonRain = false;
        }
        session.sessionHandler.shoot(gun);
        mobiarmy.war.SetPassive.afterShot(player);
        player.isPow = false;
        player.updateTurn(); // the next practice shot is a new turn: concrete, poison and stun count down
        // Rage grows like a match: +10 a turn (Player.updateTurn adds it at each turn of a match).
        player.updateAngry(10);
        session.sessionHandler.updateAngry(0, player.angry);
    }
}
