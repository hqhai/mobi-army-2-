package mobiarmy.war;

import mobiarmy.server.Equip;
import mobiarmy.server.ThorSkin;
import mobiarmy.server.User;

/**
 * The element of a full +15 or +16 set (LAN): upgraded to +15, a piece of gear takes the element its owner picks
 * (table lan_equip_element), and a full set whose five pieces are all +15 or more and of the same element (on top of
 * the +14 set passive, SetPassive) gives each of the wearer's shots a chance to strike each opponent it hurts with it.
 * The chance and the strength follow the lowest piece: CHANCE[0] and the first numbers at +15, the second at +16.
 * Whatever an element does lasts the opponent's next turn (fire and poison: the round until it).
 */
public final class SetElement {
    /** Element ids as kept in lan_equip_element; 0 none. */
    public static final int NONE = 0, LIGHTNING = 1, FIRE = 2, ICE = 3, POISON = 4;
    public static final String[] NAMES = {"", "Lôi", "Hỏa", "Băng", "Độc"};
    /** Upgrade levels the element needs: the set's lowest piece picks the strength. */
    public static final int FIRST_PLUS = 15, TOP_PLUS = 16;
    /** Chance in percent that a shot strikes an opponent it hurt with the element, at +15 and +16. */
    public static final int[] CHANCE = {10, 15}; // (20 and 35 until 26/10/2026)
    /** Lightning: his next turn he walks LIGHTNING_SLOW percent less and aims no lower than SetPassive.ELECTRICIAN_MIN_ANGLE. */
    public static final int LIGHTNING_SLOW = 30;
    /** Fire: he takes FIRE_MORE percent more damage until the end of his next turn. */
    public static final int[] FIRE_MORE = {15, 25};
    /** Ice: his next turn he cannot walk. Poison: he loses POISON_HP percent of his max HP at the start of his next POISON_TURNS turns. */
    public static final int[] POISON_HP = {3, 5};
    public static final int POISON_TURNS = 2;

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
        player.element = set == null ? NONE : set[0];
        player.elementLevel = set == null ? 0 : set[1];
    }

    /**
     * The shooter's shot just hurt target (Player.updateAllAffect, the shot's own damage only): a roll of CHANCE
     * strikes him with the shooter's element, for his next turn.
     */
    static void hit(Player shooter, Player target) {
        if (shooter == null || shooter.element == NONE || target.isDie || target.typePlayer != 0 || !SetPassive.enemies(shooter, target)) return;
        if (mobiarmy.Util.nextInt(100) >= CHANCE[shooter.elementLevel]) return;
        switch (shooter.element) {
            case LIGHTNING -> {
                if (target.paralyzeNext == 0) {
                    target.paralyzeNext = 1;
                    target.paralyzeSlow = LIGHTNING_SLOW;
                }
            }
            case FIRE -> {
                target.burnTurns = 2; // through his next turn: it ends as the one after begins
                target.burnPercent = Math.max(target.burnPercent, FIRE_MORE[shooter.elementLevel]);
            }
            case ICE -> {
                if (target.paralyzeNext < 2) target.paralyzeNext = FROZEN; // (locked already keeps him still too)
            }
            case POISON -> {
                target.elementPoisonTurns = POISON_TURNS;
                target.elementPoisonHP = Math.max(target.elementPoisonHP, POISON_HP[shooter.elementLevel]);
                target.elementPoisonBy = shooter.index;
            }
            default -> {
            }
        }
    }

    /** Player.paralyzed for the ice: no walking this turn, aim as he likes (the clients write "Đóng băng"). */
    public static final int FROZEN = 3;

    /** Damage he takes, burning: FIRE_MORE more. */
    static int taken(Player target, int damage) {
        return target.burnTurns > 0 && damage > 0 ? damage + damage * target.burnPercent / 100 : damage;
    }

    /**
     * At the start of the player's turn (Player.updateTurn): the fire burns out once his next turn is over, the poison
     * takes its HP (counted for whoever poisoned him).
     */
    static void newTurn(Player player) {
        if (player.burnTurns > 0 && --player.burnTurns == 0) player.burnPercent = 0;
        if (player.elementPoisonTurns <= 0 || player.isDie) return;
        player.elementPoisonTurns--;
        MapData map = player.mapData;
        int before = player.hp;
        player.updateHP(-Math.max(1, player.hpMax * player.elementPoisonHP / 100));
        Player by = player.elementPoisonBy >= 0 && player.elementPoisonBy < map.players.length ? map.players[player.elementPoisonBy] : null;
        if (by != null && SetPassive.enemies(by, player)) {
            by.statDealt += before - player.hp;
            if (player.isDie) by.statKills++;
        }
        if (player.hp < before) SetPassive.hurt(player);
        map.updateHP(player.index, player.hp, player.pixel);
        map.updateAngry(player.index, player.angry);
        if (player.elementPoisonTurns == 0) player.elementPoisonHP = 0;
        if (player.isDie) map.isTurn = true;
    }
}
