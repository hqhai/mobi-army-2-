import mobiarmy.war.RoomWait;

/**
 * When the owner may start a match (RoomWait.startProblem), LAN solo: uneven sides, one player against a team and a
 * boss room alone are fine; a team room still needs someone on each side, and everyone else must be ready.
 */
public class RoomStartCheck {
    static int checks;

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        checks++;
        System.out.println("PASS " + what);
    }

    public static void main(String[] args) {
        String alone = RoomWait.startProblem(0, 1, 0, null);
        check(alone != null && alone.contains("Mỗi bên"), "a team room alone cannot start: " + alone);
        check(RoomWait.startProblem(1, 2, 0, null) != null, "two players on one side cannot start");
        check(RoomWait.startProblem(0, 1, 1, null) == null, "one against one starts");
        check(RoomWait.startProblem(0, 1, 2, null) == null && RoomWait.startProblem(6, 3, 1, null) == null,
                "uneven sides start: one player may take on a team");
        check(RoomWait.startProblem(RoomWait.BOSS_ROOM, 1, 0, null) == null, "a boss room may be played alone");
        check(RoomWait.startProblem(RoomWait.BOSS_ROOM, 2, 1, null) == null, "and with any number of players");
        String waiting = RoomWait.startProblem(0, 1, 2, "bob");
        check(waiting != null && waiting.contains("bob"), "everyone but the owner must be ready: " + waiting);
        check(RoomWait.startProblem(RoomWait.BOSS_ROOM, 1, 1, "bob") != null, "in a boss room too");
        System.out.println("ROOM START PASSED: " + checks + " checks");
    }
}
