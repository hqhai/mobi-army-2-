import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import mobiarmy.war.MapData;
import mobiarmy.war.Player;
import sun.misc.Unsafe;

/** Wind: one wind for a whole round of players (bosses' turns in between included), drifting a little between rounds. */
public class WindCheck {
    static int checks;

    public static void main(String[] args) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        MapData map = (MapData) ((Unsafe) field.get(null)).allocateInstance(MapData.class);
        map.players = new Player[8];
        map.players[0] = player(map, false);
        map.players[1] = player(map, false);
        map.players[5] = player(map, true); // a boss between the players' turns

        int wind = map.windForTurn(0);
        check(Math.abs(wind) <= MapData.WIND_MAX, "first wind within the range: " + wind);
        check(map.windForTurn(5) == wind && map.windForTurn(1) == wind && map.windForTurn(5) == wind,
                "the whole round shoots with one wind, bosses' turns included");

        boolean sameWithinRounds = true, smallSteps = true, inRange = true;
        int changes = 0, previous = wind;
        Set<Integer> seen = new HashSet<>();
        for (int round = 0; round < 2000; round++) {
            int next = map.windForTurn(0); // player 0 again: a new round
            sameWithinRounds &= map.windForTurn(5) == next && map.windForTurn(1) == next;
            smallSteps &= Math.abs(next - previous) <= MapData.WIND_DRIFT;
            inRange &= Math.abs(next) <= MapData.WIND_MAX;
            if (next != previous) changes++;
            seen.add(next);
            previous = next;
        }
        check(sameWithinRounds, "every round keeps one wind");
        check(smallSteps, "between rounds the wind moves at most " + MapData.WIND_DRIFT);
        check(inRange, "the wind never passes " + MapData.WIND_MAX);
        check(changes > 1500 && seen.size() > 60, "the wind keeps moving and covers the range (" + changes + " changes, " + seen.size() + " values)");
        System.out.println("WIND PASSED: " + checks + " checks");
    }

    static Player player(MapData map, boolean boss) {
        Player player = new Player(map, null, boss ? "boss" : "p", 0, 1, 1000, 100, 0, 0, 0, 24, 24, null);
        player.isBoss = boss;
        return player;
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        checks++;
        System.out.println("PASS " + what);
    }
}
