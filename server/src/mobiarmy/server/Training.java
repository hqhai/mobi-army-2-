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

    public Training(Session session) throws IOException {
        this.session = session;
        this.map = new MapData(null, (byte) 0);
        for (int i = 0; i < 2; i++) {
            Player player = new Player(map, null, i == 0 ? session.user.name : "Enemy",
                    session.user.glass().id, 1, 1000, 0, 0, 0, 0, 24, 24, null);
            Map.Point point = map.map.data.points[i == 0 ? 0 : map.map.data.points.length - 1];
            player.index = (byte) i;
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

    public void shoot(short x, short y, short angle, byte force, byte force2) {
        if (!started || x < 0 || x >= map.width || y < 0 || y >= map.height) return;
        Player player = map.players[0];
        player.x = x;
        player.y = y;
        Gun gun = new Gun(map, 0, false);
        gun.shootBullet(player.bulletIdByGlassID(), x, y, 24, 24,
                Math.floorMod(angle, 360), Math.max(1, Math.min(30, force)),
                Math.max(1, Math.min(30, force2)), 1, 0, -1);
        gun.fillXY();
        session.sessionHandler.shoot(gun);
    }
}
