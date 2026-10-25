package mobiarmy.war.Boss.bullet;

import mobiarmy.war.Gun;
import mobiarmy.war.Player;

/**
 * One of Granos's lasers (SetPassive "Người Gieo Laze"): the UFO's beam (bullet 42) straight down from the sky onto
 * one of his grenades, two rounds after his seed. It stops on the ground or on one of his opponents and hurts only
 * them, each by its whole damage, as true damage (MapData.granosStrike: no defense, no luck); the clients of build 7
 * draw it from the top of the screen.
 */
public class GranosLaser extends UfoLaserBullet {

    public GranosLaser(Gun gun, int att, int x, int y) {
        super(gun, att, x, y);
    }

    /** Digs like the UFO's laser; every opponent within reach loses the laser's whole damage, near or far. */
    @Override
    public void collision() {
        super.gun.mapData.makeHole(super.bX, super.bY, super.bulletId, 100);
        for (Player player : super.gun.mapData.players) {
            if (player != null && !player.isDie && player.isCollision(super.bX, super.bY, super.radius)
                    && player.countInvisible2 == 0 && this.hurts(player)) {
                super.gun.setEff.add(player);
                player.attAffect += super.att;
            }
        }
    }

    @Override
    protected boolean hurts(Player player) {
        Player granos = super.gun.shooter();
        return granos != null && mobiarmy.war.SetPassive.enemies(granos, player);
    }
}
