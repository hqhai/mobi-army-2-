package mobiarmy.war;

/**
 * Damage in balanced rooms (RoomRules.BALANCED): stats, gear, upgrades and gems only pay off on accurate shots.
 *
 * <p>A shot's attack is split into the character's own attack and the bonus that stat points, gear, upgrades, gems
 * and the team add on top. The character's own attack follows the game's usual splash falloff. The bonus also needs
 * accuracy, which is the share of damage that falloff keeps (100 means the explosion is right on the target).
 * A direct hit (the explosion inside the target's body) is the only hit that can crit on luck; it also ignores part
 * of the target's defense and hurts high-HP targets more. Luck and defense are capped so stacked gear cannot make a
 * player nearly immune.
 */
public final class SkillBalance {
    /** From this accuracy up the stat bonus counts in full... */
    public static final int BONUS_FULL_ACCURACY = 80;
    /** ...and from this accuracy down not at all, with a straight line in between. */
    public static final int BONUS_ZERO_ACCURACY = 40;
    /** Share of the target's defense that a direct hit ignores. */
    public static final int DIRECT_PIERCE_PERCENT = 50;
    /** A direct hit also deals this share of the target's max HP above BASE_HP. */
    public static final int DIRECT_TANK_PERCENT = 8;
    /** Max HP of a character with no stat points and no gear. */
    public static final int BASE_HP = 1000;
    /** Most damage defense can take off. */
    public static final int MAX_REDUCTION_PERCENT = 50;
    /** Highest chance to be lucky in a turn. */
    public static final int MAX_LUCK_PERCENT = 30;

    private SkillBalance() {
    }

    /** Percent of a shooter's attack that comes from stats rather than from the character itself. */
    public static int bonusPercent(int att, int baseAtt) {
        if (att <= 0 || baseAtt >= att) return 0;
        return (int) ((long) (att - Math.max(0, baseAtt)) * 100 / att);
    }

    /** How much of the stat bonus an explosion with this accuracy keeps, 0 to 100. */
    public static int bonusKept(int accuracy) {
        if (accuracy >= BONUS_FULL_ACCURACY) return 100;
        if (accuracy <= BONUS_ZERO_ACCURACY) return 0;
        return (accuracy - BONUS_ZERO_ACCURACY) * 100 / (BONUS_FULL_ACCURACY - BONUS_ZERO_ACCURACY);
    }

    /**
     * Damage of one explosion. splashDamage is what the classic falloff leaves of the bullet's attack att;
     * bonusPercent is the stat share of the shooter's attack.
     */
    public static int hitDamage(int splashDamage, int att, int bonusPercent) {
        if (splashDamage <= 0 || att <= 0) return 0;
        int accuracy = (int) Math.min(100, (long) splashDamage * 100 / att);
        int bonus = (int) ((long) splashDamage * bonusPercent / 100);
        return splashDamage - bonus + (int) ((long) bonus * bonusKept(accuracy) / 100);
    }

    /** Share of damage that this much defense takes off: the classic curve, capped. */
    public static double reduction(int def) {
        return Math.min(1 - Math.exp(-Math.max(0, def) / 6000.0), MAX_REDUCTION_PERCENT / 100.0);
    }

    /** Chance to be lucky in a turn: the classic curve, capped. */
    public static double luckChance(int luck) {
        return Math.min(1 - Math.exp(-Math.max(0, luck) / 6000.0), MAX_LUCK_PERCENT / 100.0);
    }

    /** Extra damage a direct hit deals to a target with this max HP. */
    public static int tankDamage(int hpMax) {
        return Math.max(0, hpMax - BASE_HP) * DIRECT_TANK_PERCENT / 100;
    }

    /**
     * Damage a target takes from one shot. total is the sum of its explosions and direct the part of it that came
     * from explosions inside the target's body; def already includes the lucky-turn bonus.
     */
    public static int taken(int total, int direct, boolean shooterLucky, int targetHpMax, int def) {
        direct = Math.max(0, Math.min(direct, total));
        int splash = total - direct;
        if (direct > 0) {
            if (shooterLucky) direct *= 2;
            direct += tankDamage(targetHpMax);
        }
        int pierced = (int) ((long) def * (100 - DIRECT_PIERCE_PERCENT) / 100);
        return (int) Math.round(splash * (1 - reduction(def)) + direct * (1 - reduction(pierced)));
    }
}
