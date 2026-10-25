package mobiarmy.war.Boss.bullet;

import mobiarmy.war.Gun;
import mobiarmy.war.Player;

/**
 * Granos's POW with his set passive "Người Gieo Laze" (SetPassive.laserSower): one seed instead of his grenades. It
 * flies without wind like Rocketer's flare and lands softly, with no hole and no damage; where it lands a row of his
 * grenades drops (MapData.layGrenades) that his lasers strike two rounds later. Clients of build 7 draw it as a flare.
 */
public class GranosSeed extends Bullet {

    public static final int ID = 62;
    private boolean landed;

    public GranosSeed(Gun gun, int att, int x, int y, int vx, int vy) {
        super(gun, ID, att, x, y, vx, vy, 0, 0, 80);
        super.isCanCollision = false;
    }

    @Override
    public void nextXY() {
        super.nextXY();
        if (!super.collect || this.landed) return;
        this.landed = true;
        Player owner = super.gun.shooter();
        // Off the map, a trial shot or practice (no turns there): no grenades.
        if (owner == null || super.gun.test || super.gun.dryRun || !super.gun.mapData.isWar) return;
        if (super.bX < 0 || super.bX >= super.gun.mapData.width || super.bY > super.gun.mapData.height + 100) return;
        super.gun.mapData.layGrenades(owner, super.bX, super.bY);
    }
}
