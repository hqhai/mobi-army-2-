package mobiarmy.server;

/**
 * LAN client builds. At login a client sends its build number (GameMidlet.LAN_BUILD) after the version string;
 * clients from before this check send nothing and count as build 0. The server turns away builds below the required
 * one, so a client change nobody should be able to skip (such as the shorter aim guide) is enforced by raising
 * REQUIRED and GameMidlet.LAN_BUILD together, then publishing the client update.
 */
public final class ClientVersion {
    /** Must equal GameMidlet.LAN_BUILD of the client built from the same code (scripts/tests/ClientVersionCheck). */
    public static final int REQUIRED = 5;

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
        return which + "máy chủ cần bản LAN " + required + " trở lên. Hãy tắt game rồi mở lại MobiArmy2 để tự cập nhật, "
                + "hoặc tải bản mới ở trang chủ của máy chủ (cổng 8123).";
    }
}
