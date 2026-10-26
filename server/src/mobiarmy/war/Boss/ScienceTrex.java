package mobiarmy.war.Boss;

import mobiarmy.Util;
import mobiarmy.war.Boss.bullet.BulletTrajectory;
import mobiarmy.war.Player;
import mobiarmy.war.SetPassive;

/**
 * The T-rex the Scientists' team passive calls when the first of them falls (SetTeam): on his side, with his max HP,
 * attack, defense and luck, it takes turns like a player and fights like the boss T-rex (Trex): stomps an opponent
 * close by, else fires its big rocket (30%) or its laser at a random one. Its hits are as strong as the boss's next to
 * the boss's attack (750): stomp 1200, rocket 750, laser 300. Its shots spare its side (Bullet.hurts) and it keeps the
 * side in the match while it stands.
 */
public class ScienceTrex extends Boss {

    static final int BOSS_ATT = 750, STOMP = 1200, ROCKET = 750, LASER = 300;

    public ScienceTrex(Player scientist, int x, int y) {
        super("T-rex", (byte) 15, Math.max(1, scientist.hpMax), scientist.att, scientist.def, scientist.luck, 0, 45, 50, x, y);
        super.isBoss = false;
        super.isTurn = true;
        super.team = scientist.team;
        super.leadIndex = scientist.index;
    }

    private int hit(int boss) {
        return Math.max(1, super.att * boss / BOSS_ATT);
    }

    /** Opponents of the side that called it. */
    private boolean foe(Player p) {
        Player caller = this.caller();
        return p != null && !p.isDie && p != this && caller != null && SetPassive.enemies(caller, p) && p.isFaction;
    }

    @Override
    public void update() {
        super.update();
        if (super.index != super.mapData.getTurn() || System.currentTimeMillis() <= super.mapData.timeUntilAction2 || super.isShoot) {
            return;
        }
        if (super.trajectory == null) {
            Player near = null;
            java.util.List<Player> foes = new java.util.ArrayList<>();
            for (Player p : super.mapData.players) {
                if (!this.foe(p)) continue;
                foes.add(p);
                if (near == null || Math.abs(p.x - super.x) < Math.abs(near.x - super.x)) near = p;
            }
            if (near == null) {
                super.mapData.isTurn = true;
                return;
            }
            if (Math.abs(super.x - near.x) < 90) {
                super.mapData.shootBullet(super.index, false, 35, super.x, super.y, super.width, super.height, 0, 1, 0, 1, this.hit(STOMP), 400);
                super.mapData.updateAffect(super.index);
                super.mapData.isTurn = true;
                super.isShoot = true;
                return;
            }
            Player target = foes.get(Util.nextInt(foes.size()));
            if (Util.nextInt(100) < 30) {
                super.mapData.shootBullet(super.index, false, 37, super.x, super.y, super.width, super.height, 90, 10, target.x, 1, this.hit(ROCKET), -1);
                super.mapData.updateAffect(super.index);
                super.mapData.isTurn = true;
                super.isShoot = true;
            } else {
                int w = target.width * 2;
                int h = target.height;
                int toX = target.x - w / 2;
                int toY = target.y - h;
                super.trajectory = new BulletTrajectory(super.mapData, super.index, 40, super.x, super.y, super.width, super.height, Util.nextInt(toX - 50, toX + 50), toY, w, h, 60, 15, false, false);
                super.trajectory.start();
            }
        } else if (super.trajectory.complate) {
            if (super.trajectory.place) {
                super.mapData.shootBullet(super.index, false, super.trajectory.bulletId, super.x, super.y, super.width, super.height, super.trajectory.ang, super.trajectory.force, super.trajectory.force2, 1, this.hit(LASER), 50);
                super.mapData.updateAffect(super.index);
                super.isShoot = true;
            }
            super.mapData.isTurn = true;
        }
    }
}
