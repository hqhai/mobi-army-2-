package player;

/**
 * LAN: a small log of every player's drawing state (army-debug.log next to the game's data), written at the start of a
 * match and at each turn, to find why an opponent is sometimes not drawn in the first match until a turn goes by.
 */
public final class DebugLog {
    private static final long MAX = 512 * 1024;

    private DebugLog() {
    }

    public static void players(String when) {
        try {
            java.io.File file = new java.io.File(System.getProperty("user.home", "."), "army-debug.log");
            boolean append = !file.exists() || file.length() < MAX;
            try (java.io.PrintWriter out = new java.io.PrintWriter(new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(file, append), java.nio.charset.StandardCharsets.UTF_8))) {
                out.println(new java.util.Date() + " " + when + " me=" + screen.GameScr.myIndex + " scale=" + map.MM.mapScale
                        + " map=" + map.MM.mapID + " " + map.MM.mapWidth + "x" + map.MM.mapHeight + " cam=" + effect.Camera.x + "," + effect.Camera.y);
                if (PM.p == null) return;
                for (int i = 0; i < PM.p.length; i++) {
                    CPlayer p = PM.p[i];
                    if (p == null) continue;
                    out.println("  #" + i + " " + p.name + " gun=" + p.gun + " xy=" + p.x + "," + p.y + " state=" + p.getState()
                            + " frame=" + p.curFrame + " look=" + p.look + " paint=" + p.isPaint + " invisible=" + p.isInvisible
                            + " falling=" + p.falling + " hp=" + p.hp + "/" + p.maxhp + " team=" + p.team + " com=" + p.isCom
                            + " equip=" + (p.equip != null) + " body=" + p.bodyPercent + " turn=" + p.climbTurn());
                }
            }
        } catch (Throwable ignored) {
            // A log must never break the game.
        }
    }
}
