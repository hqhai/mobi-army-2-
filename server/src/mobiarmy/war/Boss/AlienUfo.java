package mobiarmy.war.Boss;

import mobiarmy.war.Player;
import mobiarmy.war.SetPassive;

/**
 * Magenta's set passive "Người Ngoài Hành Tinh" (SetPassive): a UFO on her side, called over the opponent her POW hit.
 * It looks and flies like the UFO item (27): on its first turn it fires its laser straight down where it was called,
 * then it takes turns flying over the nearest opponent and firing there, until it is shot down or Magenta falls.
 * The laser only hurts her opponents; whoever shoots the UFO heals a share of the damage dealt to it.
 */
public class AlienUfo extends Boss {

    /** Over its target: the next turn fires. */
    private boolean aimed = true;

    public AlienUfo(Player owner, int hp, int laser, int x, int y) {
        super("UFO", (byte) 16, hp, laser, 0, 0, 0, 24, 24, x, y);
        super.isFly = true;
        super.isFaction = false;
        super.isTurn = true;
        super.isBoss = false;
        super.team = owner.team;
        super.leadIndex = owner.index;
    }

    /** Magenta, who called it. */
    public Player owner() {
        Player[] players = super.mapData.players;
        return super.leadIndex >= 0 && super.leadIndex < players.length ? players[super.leadIndex] : null;
    }

    /** Its laser only hurts her opponents. */
    public boolean hurts(Player player) {
        Player owner = this.owner();
        return owner != null && SetPassive.enemies(owner, player);
    }

    @Override
    public void update() {
        super.update();
        if (super.index != super.mapData.getTurn() || System.currentTimeMillis() <= super.mapData.timeUntilAction2 || super.isShoot) {
            return;
        }
        Player owner = this.owner();
        if (owner == null || owner.isDie) {
            // Its caller fell: it flies off.
            super.updateHP(-super.hp);
            super.mapData.updateHP(super.index, super.hp, super.pixel);
            super.mapData.isTurn = true;
            return;
        }
        if (this.aimed) {
            this.aimed = false;
            // The laser: bullet 42 hits twice its attack, so half of it goes in.
            super.mapData.shootBullet(super.index, false, 42, super.x, super.y, super.width, super.height, 270, 10, 0, 1, super.att / 2, -1);
            super.mapData.updateAffect(super.index);
            super.isShoot = true;
            super.mapData.isTurn = true;
            return;
        }
        Player target = null;
        for (Player p : super.mapData.players) {
            if (p == null || p.isDie || p.typePlayer != 0 || !this.hurts(p)) continue;
            if (target == null || Math.abs(p.x - super.x) < Math.abs(target.x - super.x)) target = p;
        }
        if (target != null) {
            this.aimed = true;
            int[] spot = SetPassive.alienSpot(super.mapData, target);
            super.changeLocationFly(spot[0], spot[1]);
        }
        super.mapData.isTurn = true;
    }

    @Override
    public void die() {
        super.die();
        super.isCollision = false;
    }
}
