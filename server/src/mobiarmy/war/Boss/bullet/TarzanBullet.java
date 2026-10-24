package mobiarmy.war.Boss.bullet;

import mobiarmy.war.Gun;
import mobiarmy.war.SetPassive;

/**
 *
 * @author TGDD
 */
public class TarzanBullet extends Bullet {

    private byte    quayLai;
    private final boolean addTZ;
    
    /** Tarzan's set passive (SetPassive "Khế Ước Rừng Xanh"): no wind, a wider blast, harder on the way back. */
    private final boolean pact;

    public TarzanBullet(Gun gun, int att, int x, int y, int vx, int vy) {
        super(gun, 21, att, x, y, vx, vy, SetPassive.boomerangWind(gun.shooter(), gun.windX), SetPassive.boomerangWind(gun.shooter(), gun.windY), 50);
        this.quayLai = -1;
        this.addTZ = (vx <= 0);
        this.pact = SetPassive.pactTarzan(gun.shooter());
        if (this.pact) super.radius = super.radius * SetPassive.TARZAN_RADIUS / 100;
    }

    @Override
    public void collision() {
        // On its way back (it has turned): the boomerang hits harder.
        if (this.pact && this.quayLai >= 0) super.att += super.att * SetPassive.TARZAN_RETURN / 100;
        super.collision();
    }

    @Override
    public void nextXY() {
        super.nextXY();
        if(this.quayLai == 0) {
            if(this.addTZ) {
                this.vx += 1;
            } else {
                this.vx -=1;
            }
            this.quayLai = 1;
        } else if(this.quayLai == 1) {
            if(this.addTZ) {
                this.vx += 2;
            } else {
                this.vx -= 2;
            }
        } else if(super.isMaxY) {
            this.quayLai = 0;
        }
    }
    
}
