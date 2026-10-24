package mobiarmy.war.Boss.bullet;

import mobiarmy.war.Gun;

/**
 *
 * @author TGDD
 */
public class ItemUfoBullet extends Bullet {
    
    private final int hp;
    private final int team;
    
    public ItemUfoBullet(Gun gun, int att, int hp, int team, int x, int y) {
        super(gun, 53, att, x, y, 0, 0, 0, 0, 0);
        this.hp = hp;
        this.team = team;
    }
    
    @Override
    public void nextXY() {
        super.collect = true;
        // LAN: one frame, where the UFO appears; the client reads the first frame of every shot (with none it threw).
        super.frames.add(new Bullet.Frame(super.frame, super.bX, super.bY, 0, 0));
//        if(this.isCanCollision) {
//            this.collision();
//        }
        super.gun.mapData.addBoss(new ItemUfo(this.team, super.gun.index, this.hp, super.att, super.bX, super.bY));
    }
    
}
