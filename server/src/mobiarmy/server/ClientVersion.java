package mobiarmy.server;

/**
 * LAN client builds. At login a client sends its build number (GameMidlet.LAN_BUILD) after the version string;
 * clients from before this check send nothing and count as build 0. The server turns away builds below the required
 * one, so a client change nobody should be able to skip (such as the shorter aim guide) is enforced by raising
 * REQUIRED and GameMidlet.LAN_BUILD together, then publishing the client update. A change older clients can live
 * without raises only LATEST, and the server sends them something they understand (Session.build tells which).
 */
public final class ClientVersion {
    /**
     * The oldest build that may log in: 8, which walks Tarzan's climb the server's way (Climb), keeps Rocketer's late
     * WorldWar bombs on the ground and puts gift box items in their slots (7 showed no aim guide unless item 36 is on).
     */
    public static final int REQUIRED = 8;
    /**
     * Bag size (game, web and admin gifts). From BIG_BAG_BUILD the client reads the bag count unsigned; older ones read
     * a signed byte and see only the first OLD_BAG_SHOWN pieces (SessionHandler.updateRuong).
     */
    public static final int BAG_LIMIT = 250, BIG_BAG_BUILD = 10, OLD_BAG_SHOWN = 127;
    /** Must equal GameMidlet.LAN_BUILD of the client built from the same code (scripts/tests/ClientVersionCheck). */
    public static final int LATEST = 14; // 14 flies Tarzan's boomerang back, shows the element charges and Thiêu/Cóng/Tê/Độc, a shocked aim 20 higher; 13 fires the bullets the server sent paths for (no stuck turn after Miss 6's or Electrician's POW); 12 raises a shocked aim by 30 from his own lowest and stops the arrow keys at 90; 11 greys item 37 while it cools down; 10 reads 250-piece bags, waits for a new map's ground before anyone falls; 9 drew the angle arc; 8 still plays
    /**
     * From this build the client plays Tarzan's kick (message KICK_MESSAGE: the kicked players fly where they land);
     * older ones get each landing spot as message 53 and show the player there at once.
     */
    public static final int KICK_BUILD = 7, KICK_MESSAGE = 127;
    /**
     * From this build the client fires as many bullets as a shot has paths. Older ones fire one more for the POW of
     * Miss 6 (6 for the server's 5) and of Electrician (7 for 6), read past the paths and leave the next player unable
     * to act for a whole turn, so they get the last path once more (SessionHandler.shoot): only the picture changes.
     */
    public static final int PATHS_BUILD = 13;
    /**
     * From this build the client flies Tarzan's POW boomerang back to him (message BOOMERANG_MESSAGE, SetPassive
     * "Vòng quanh thế giới"); older ones only see the POW bar he gets back.
     */
    public static final int BOOMERANG_BUILD = 14, BOOMERANG_MESSAGE = -127;

    private ClientVersion() {
    }

    /** -Darmy.client.minBuild=0 lets every client in again, for example while the client update is not published yet. */
    public static int required() {
        return Integer.getInteger("army.client.minBuild", REQUIRED);
    }

    /** Why a client of this build may not log in, or null when it may. */
    public static String problem(int build) {
        int required = required();
        if (build >= required) {
            return null;
        }
        String which = build > 0 ? "Bản game của bạn đã cũ (LAN " + build + "), " : "Bản game của bạn đã cũ, ";
        // Only the restart: the launcher (MobiArmy2.cmd) updates itself, nobody needs to download the game again.
        return which + "máy chủ cần bản LAN " + required + " trở lên. Hãy tắt hẳn game rồi mở lại MobiArmy2, game sẽ tự cập nhật.";
    }
}
