package mobiarmy.war.Boss.bullet;

import mobiarmy.war.Boss.Boss;
import mobiarmy.war.Player;
import mobiarmy.war.SetPassive;

/**
 *
 * @author Văn Tú
 */
public class ItemUfo extends Boss {

    /** LAN: laser shots before the UFO leaves. */
    public static final int SHOTS = 3;

    private boolean isLod;
    private int shots;

    /** att: its laser's damage (the caller's attack, MapData.useItem item 27). */
    public ItemUfo(int team, int leadIndex, int hp, int att, int x, int y) {
        super("UFO", (byte)16, hp, att, 0, 0, 0, 24, 24, x, y);
        super.isFly = true;
        super.isFaction = false;
        super.isTurn = true;
        this.isLod = false;
        super.isBoss = false;
        super.team = team;
        super.leadIndex = leadIndex;
    }

    /** An opponent of whoever called it (LAN: also in free-for-all rooms, where everyone is team 3). */
    private boolean isFoe(Player player) {
        Player caller = this.caller();
        return caller != null ? SetPassive.enemies(caller, player) : player.team != this.team;
    }

    @Override
    public void update() {
        super.update();
        //Đến lượt chưa
        if (super.index == super.mapData.getTurn() && System.currentTimeMillis() > super.mapData.timeUntilAction2) {
            //Bắn
            if (!super.isShoot) {
                if (this.isLod) {
                    int yfake = super.y;
                    conguoichoi:
                    {
                        while(true) {
                            if (super.mapData.isCollisionMap(super.x, yfake)) {
                                break conguoichoi;
                            }
                            if (yfake > super.mapData.height + 100) {
                                this.isLod = false;
                                break conguoichoi;
                            }
                            yfake++;
                        }
                    }
                }
                if (!this.isLod) {
                    Player player = null;
                    for (Player player1 : super.mapData.players) {
                        if (player1 != null && !player1.isDie && player1.isFaction && this.isFoe(player1) && (player == null || (Math.abs(player1.x - super.x) < Math.abs(player.x - super.x) && (Math.abs(player1.y - super.y) < Math.abs(player.y - super.y))))) {
                            player = player1;
                        }
                    }
                    if (player != null) {
                        this.isLod = true;
                        short toX = player.x;
                        short toY = super.y;
                        if (super.y > player.y - 200 || super.y < player.y + 200) {
                            toY = (short) (player.y - 200);
                        }
                        while(true) {
                            if (!super.mapData.isCollisionMap(toX, toY)) {
                                break;
                            }
                            toY--;
                        }
                        super.changeLocationFly(toX, toY);
                    }
                    super.mapData.isTurn = true;
                } else {
                    this.isLod = false;
                    // LAN: the laser (bullet 42 hits twice its attack) hits with the caller's attack.
                    super.mapData.shootBullet(super.index, false, 42, super.x, super.y, super.width, super.height, 270, 10, 0, 1, super.att / 2, -1);
                    super.mapData.updateAffect(super.index);
                    super.isShoot = true;
                    super.mapData.isTurn = true;
                    if (++this.shots >= SHOTS && !super.isDie) {
                        // Its last shot: it leaves.
                        super.updateHP(-super.hp);
                        super.mapData.updateHP(super.index, super.hp, super.pixel);
                    }
                }
            }
        }
    }

    @Override
    public void die() {
        super.die();
        super.isCollision = false;
    }

}
