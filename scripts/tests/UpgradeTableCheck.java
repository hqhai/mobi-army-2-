import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Upgrade tables (+1 to +16): sizes, ordering, byte headroom, the web copy and the platinum chest. */
public class UpgradeTableCheck {
    static int checks;

    public static void main(String[] args) throws Exception {
        int[][] tables = {LanPlayerGame.UPGRADE_RATE, LanPlayerGame.UPGRADE_XU, LanPlayerGame.UPGRADE_LUONG,
                LanPlayerGame.UPGRADE_STONES, LanPlayerGame.UPGRADE_BONUS, LanPlayerGame.UPGRADE_PITY};
        for (int[] table : tables) check(table.length == LanPlayerGame.MAX_PLUS + 1, "table size " + table.length);
        check(LanPlayerGame.MAX_PLUS == 16, "max level is +16");
        check(LanPlayerGame.UPGRADE_RATE[1] == LanPlayerGame.RATE_SCALE && LanPlayerGame.UPGRADE_RATE[LanPlayerGame.MAX_PLUS] >= 1, "rate 100% at +1 and never 0");
        check(LanPlayerGame.UPGRADE_RATE[13] * 500 == LanPlayerGame.RATE_SCALE && LanPlayerGame.UPGRADE_RATE[14] * 1000 == LanPlayerGame.RATE_SCALE, "+13 is 1/500 and +14 is 1/1000");
        check(LanPlayerGame.UPGRADE_PITY[13] == 500 && LanPlayerGame.UPGRADE_PITY[14] == 1000 && LanPlayerGame.UPGRADE_PITY[12] == 0, "pity at 500 and 1000 tries");
        check(LanPlayerGame.UPGRADE_PITY[15] == 1500 && LanPlayerGame.UPGRADE_PITY[16] == 2000 && LanPlayerGame.UPGRADE_RATE[15] == 1 && LanPlayerGame.UPGRADE_RATE[16] == 1, "+15 and +16: 1/1000, sure at 1500 and 2000 tries");
        for (int n = 2; n <= LanPlayerGame.MAX_PLUS; n++) {
            check(n > 14 ? LanPlayerGame.UPGRADE_RATE[n] <= LanPlayerGame.UPGRADE_RATE[n - 1] : LanPlayerGame.UPGRADE_RATE[n] < LanPlayerGame.UPGRADE_RATE[n - 1], "rate falls at +" + n);
            check(n <= 14 || LanPlayerGame.UPGRADE_PITY[n] > LanPlayerGame.UPGRADE_PITY[n - 1], "pity grows at +" + n);
            check(LanPlayerGame.UPGRADE_BONUS[n] > LanPlayerGame.UPGRADE_BONUS[n - 1], "bonus grows at +" + n);
            check(LanPlayerGame.UPGRADE_XU[n] > LanPlayerGame.UPGRADE_XU[n - 1], "xu grows at +" + n);
        }
        // inv_ability is a byte: gold gear (15) + three level-10 gems (19 each) + the top bonus must stay <= 127.
        check(15 + 3 * 19 + LanPlayerGame.UPGRADE_BONUS[LanPlayerGame.MAX_PLUS] <= Byte.MAX_VALUE, "top bonus fits a byte");

        String js = Files.readString(Path.of("web/nhan-vat/player.js"), StandardCharsets.UTF_8);
        String[] names = {"rate", "xu", "luong", "stones", "bonus", "pity"};
        for (int i = 0; i < names.length; i++) {
            Matcher m = Pattern.compile("\\b" + names[i] + ": \\[([^\\]]*)\\]").matcher(js);
            check(m.find(), "web table " + names[i] + " present");
            int[] web = Arrays.stream(m.group(1).split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray();
            check(Arrays.equals(web, tables[i]), "web table " + names[i] + " matches the server");
        }

        // Insurance: one per tier, covering ever higher levels up to the top, mirrored on the web page.
        int upTo = LanPlayerGame.DROP_FROM;
        for (LanPlayerGame.Insurance insurance : LanPlayerGame.Insurance.values()) {
            check(insurance.upTo > upTo, insurance.name + " covers more levels");
            upTo = insurance.upTo;
            check(js.contains("{id: " + insurance.id + ", name: '" + insurance.name + "', upTo: " + insurance.upTo + "}"), insurance.name + " on the web");
        }
        check(upTo == LanPlayerGame.MAX_PLUS, "the best insurance covers +16");
        // The swirl (and the server's wait) is 1 second, 2 for a success at +12 or higher.
        for (int n = 1; n <= LanPlayerGame.MAX_PLUS; n++) {
            check(LanPlayerGame.spinMillis(n, false) == 1000, "1 second swirl on a fail at +" + n);
            check(LanPlayerGame.spinMillis(n, true) == (n >= 12 ? 2000 : 1000), "swirl on a success at +" + n);
        }

        LanChests.Chest platinum = LanChests.find("platinum");
        check(platinum.plus == 14, "platinum chest gives +14 (the +15 and +16 elements are earned)");
        check(platinum.id == 84 && !platinum.skins, "platinum chest is special item 84 with gear prizes");
        LanChests.Chest diamond = LanChests.find("diamond");
        check(diamond.plus == 16 && diamond.id == 85 && !diamond.skins && diamond.xu == 100_000_000 && diamond.luong == 10_000,
                "diamond chest is special item 85: gear at +16 for 100M xu or 10,000 luong");
        check(js.contains("plus >= 15 ? 7"), "the web colours +15 and +16 as diamond (tier 7)");
        System.out.println("UPGRADE TABLES PASSED: " + checks + " checks");
    }

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
