package mobiarmy.war;

import mobiarmy.server.Equip;
import mobiarmy.server.ThorSkin;
import mobiarmy.server.User;

/**
 * The element of a full +15 or +16 set (LAN): upgraded to +15, a piece of gear takes the element its owner picks
 * (table lan_equip_element), and a full set whose five pieces are all +15 or more and of the same element (on top of
 * the +14 set passive, SetPassive) charges with the damage its wearer deals opponents, his summons and time bombs too.
 * Once it reaches CHARGE, his next shot carries the element to every opponent it hurts; hit or miss, the charge then starts over. No
 * luck in it: the clients show the charge (message 114). The strength follows the lowest piece: the first numbers at
 * +15, the second at +16. "His next N turns" are the turns of the one struck, the strike lasting until the next one
 * begins.
 * <ul>
 * <li>Thiêu (fire): at the start of his next BURN_TURNS turns he loses BURN_HP percent of his max HP, and burning he
 * gains RAGE_SLOW percent less rage.
 * <li>Cóng (ice): his next turn he cannot walk, and his shots of his next CHILL_TURNS turns have CHILL_FORCE percent less force.
 * <li>Tê (lightning): his next SHOCK_TURNS turns he walks SHOCK_SLOW percent less and aims SetPassive.ELECTRICIAN_RAISE degrees higher
 * at the lowest, as Electrician's shock.
 * <li>Độc (poison): for his next POISON_TURNS turns he heals POISON_HEAL percent less and his items work ITEM_LESS percent
 * less (heals, and the bullets of items).
 * </ul>
 * (Until 26/10/2026 each shot had a 10 or 15 percent chance to strike: the luck decided too much.)
 */
public final class SetElement {
    /** Element ids as kept in lan_equip_element; 0 none. */
    public static final int NONE = 0, LIGHTNING = 1, FIRE = 2, ICE = 3, POISON = 4;
    public static final String[] NAMES = {"", "Lôi", "Hỏa", "Băng", "Độc"};
    /** What each element does to the one it strikes, as the clients write it over him. */
    public static final String[] EFFECTS = {"", "Tê", "Thiêu", "Cóng", "Độc"};
    /** Upgrade levels the element needs: the set's lowest piece picks the strength. */
    public static final int FIRST_PLUS = 15, TOP_PLUS = 16;
    /** Damage his shots deal opponents before the next one carries the element, at +15 and +16. */
    public static final int[] CHARGE = {1800, 1400};
    public static final int BURN_TURNS = 2;
    public static final int[] BURN_HP = {3, 4};
    public static final int RAGE_SLOW = 50;
    public static final int CHILL_TURNS = 2;
    public static final int[] CHILL_FORCE = {12, 16};
    public static final int SHOCK_TURNS = 2;
    public static final int[] SHOCK_SLOW = {50, 65};
    public static final int POISON_TURNS = 2;
    public static final int[] POISON_HEAL = {40, 60};
    public static final int[] ITEM_LESS = {25, 35};

    private SetElement() {
    }

    /** Elements the user's pieces have, by db key (lan_equip_element), empty on a database hiccup. */
    public static java.util.Map<Integer, Integer> elements(User user) {
        java.util.Map<Integer, Integer> out = new java.util.HashMap<>();
        try {
            for (var row : mobiarmy.server.Server.dbManager.selectColumnName(
                    "SELECT db_key, element FROM lan_equip_element WHERE user_id = ?", user.id)) {
                out.put(row.getInt("db_key"), row.getInt("element"));
            }
        } catch (Exception e) {
            out.clear();
        }
        return out;
    }

    /**
     * {element, 0 for +15 or 1 for +16} of the full +15 set the user wears on this character, or null: five pieces
     * (gun, hat, armour, glasses, backpack), all +15 or more and of one element, as for SetPassive.tier.
     */
    public static int[] of(User user, int glassId) {
        if (user == null || SetPassive.tier(user, glassId) == 0) return null;
        java.util.Map<Integer, Integer> elements = elements(user);
        int element = NONE, lowest = Integer.MAX_VALUE;
        for (Equip equip : user.equips) {
            if (equip.glassID != glassId || !equip.isUse || equip.vip != 0 || ThorSkin.is(equip)) continue;
            if (equip.level2 < FIRST_PLUS) return null;
            int own = elements.getOrDefault(equip.dbKey, NONE);
            if (own == NONE || (element != NONE && own != element)) return null;
            element = own;
            lowest = Math.min(lowest, equip.level2);
        }
        return element == NONE ? null : new int[] {element, lowest >= TOP_PLUS ? 1 : 0};
    }

    /** Sets up the player's element for the match (MapData.startGame), none in "Sát thương gốc" rooms. */
    public static void apply(Player player, User user, int glassId, boolean baseRules) {
        int[] set = baseRules ? null : of(user, glassId);
        give(player, set == null ? NONE : set[0], set == null ? 0 : set[1]);
    }

    /** The player fights with this element (NONE for none) at this level (0 for +15, 1 for +16), uncharged. */
    public static void give(Player player, int element, int level) {
        player.element = element;
        player.elementLevel = element == NONE ? 0 : Math.max(0, Math.min(1, level));
        player.elementCharge = 0;
        player.elementReady = false;
    }

    /** Before the player's shot (MapData.shoot): a charged element goes with it, and the charge starts over. */
    static void beforeShot(Player shooter) {
        shooter.elementShotNow = shooter.element != NONE && shooter.elementReady;
        if (shooter.elementShotNow) {
            shooter.elementReady = false;
            shooter.elementCharge = 0;
        }
    }

    /** After the player's shot: it carried its element, if it had one, to every opponent it hurt. */
    static void afterShot(Player shooter) {
        shooter.elementShotNow = false;
    }

    /** He dealt an opponent this much damage (Player.updateAllAffect, the fire's burning aside): it charges his element. */
    static void dealt(Player shooter, int damage) {
        if (shooter == null || shooter.element == NONE || shooter.elementReady || damage <= 0) return;
        shooter.elementCharge = Math.min(CHARGE[shooter.elementLevel], shooter.elementCharge + damage);
        if (shooter.elementCharge >= CHARGE[shooter.elementLevel]) shooter.elementReady = true; // for his next shot
    }

    /** How far the player's element has charged, in percent (100: his next shot carries it); -1 without one. */
    public static int chargePercent(Player player) {
        if (player.element == NONE) return -1;
        if (player.elementReady) return 100;
        return Math.min(99, player.elementCharge * 100 / CHARGE[player.elementLevel]);
    }

    /** The shooter's shot just hurt target (Player.updateAllAffect): a charged shot strikes him with the element. */
    static void hit(Player shooter, Player target) {
        if (shooter == null || !shooter.elementShotNow || target.isDie || target.typePlayer != 0 || !SetPassive.enemies(shooter, target)) return;
        int level = shooter.elementLevel;
        switch (shooter.element) {
            case LIGHTNING -> {
                shock(target, SHOCK_SLOW[level]);
                target.shockTurns = SHOCK_TURNS - 1; // (newTurn shocks him for those after the next)
                target.shockSlow = Math.max(target.shockSlow, SHOCK_SLOW[level]);
            }
            case FIRE -> {
                target.burnTurns = BURN_TURNS;
                target.burnHP = Math.max(target.burnHP, BURN_HP[level]);
                target.burnBy = shooter.index;
            }
            case ICE -> {
                if (target.paralyzeNext < 2) target.paralyzeNext = FROZEN;
                target.chillTurns = CHILL_TURNS;
                target.chillForce = Math.max(target.chillForce, CHILL_FORCE[level]);
            }
            case POISON -> {
                target.elementPoisonTurns = POISON_TURNS;
                target.poisonHeal = Math.max(target.poisonHeal, POISON_HEAL[level]);
                target.poisonItem = Math.max(target.poisonItem, ITEM_LESS[level]);
            }
            default -> {
            }
        }
    }

    /** Shocked on his next turn as by Electrician (Player.paralyzed 1): slow percent fewer steps, aim raised. */
    private static void shock(Player target, int slow) {
        if (target.paralyzeNext >= 2) return; // (frozen by ice, or locked, keeps him still: that holds)
        target.paralyzeSlow = target.paralyzeNext == 1 ? Math.max(target.paralyzeSlow, slow) : slow;
        target.paralyzeNext = 1;
    }

    /** Player.paralyzed for the ice: no walking this turn, aim as he likes (the clients write "Cóng"). */
    public static final int FROZEN = 3;

    /** Burning now: struck by fire and not burnt out yet (his last burning turn included). */
    public static boolean burning(Player p) {
        return p.burnTurns > 0 || p.burningNow;
    }

    /** Chilled now: his shots of this turn or of turns to come have less force. */
    public static boolean chilled(Player p) {
        return p.chillTurns > 0 || p.chilledNow;
    }

    /** Poisoned now, by the element (not the poison rain item, nor the poison bullet). */
    public static boolean poisoned(Player p) {
        return p.elementPoisonTurns > 0 || p.poisonedNow;
    }

    /** Rage he gains, burning: RAGE_SLOW percent less. */
    static int rage(Player player, int add) {
        return add > 0 && burning(player) ? add * (100 - RAGE_SLOW) / 100 : add;
    }

    /** HP he heals, poisoned: POISON_HEAL less. */
    static int heal(Player player, int add) {
        return add > 0 && poisoned(player) ? add * (100 - player.poisonHeal) / 100 : add;
    }

    /** What an item of his does (its heal, or its bullet's attack), poisoned: ITEM_LESS less. */
    public static int item(Player player, int amount) {
        return poisoned(player) ? amount * (100 - player.poisonItem) / 100 : amount;
    }

    /** The force of his shot this turn, chilled: CHILL_FORCE less. */
    static int force(Player player, int force) {
        return player.chilledNow ? force * (100 - player.chillForce) / 100 : force;
    }

    /**
     * At the start of the player's turn (Player.updateTurn): the lightning shocks him for his next turn too while it
     * lasts, the chill holds this turn's shots or wears off, so does the poison, and the fire takes its HP (counted for
     * whoever lit it) or burns out.
     */
    static void newTurn(Player player) {
        if (player.shockTurns > 0) {
            player.shockTurns--;
            shock(player, player.shockSlow);
        }
        if (player.shockTurns == 0) player.shockSlow = 0;
        player.chilledNow = player.chillTurns > 0;
        if (player.chilledNow) player.chillTurns--;
        else player.chillForce = 0;
        player.poisonedNow = player.elementPoisonTurns > 0;
        if (player.poisonedNow) {
            player.elementPoisonTurns--;
        } else {
            player.poisonHeal = 0;
            player.poisonItem = 0;
        }
        player.burningNow = player.burnTurns > 0 && !player.isDie;
        if (!player.burningNow) {
            player.burnTurns = 0;
            player.burnHP = 0;
            return;
        }
        player.burnTurns--;
        MapData map = player.mapData;
        int before = player.hp;
        player.updateHP(-Math.max(1, player.hpMax * player.burnHP / 100));
        Player by = player.burnBy >= 0 && player.burnBy < map.players.length ? map.players[player.burnBy] : null;
        if (by != null && SetPassive.enemies(by, player)) {
            by.statDealt += before - player.hp;
            if (player.isDie) by.statKills++;
        }
        if (player.hp < before) SetPassive.hurt(player);
        map.updateHP(player.index, player.hp, player.pixel);
        map.updateAngry(player.index, player.angry);
        if (player.isDie) map.isTurn = true;
    }
}
