import java.lang.reflect.Field;
import java.util.ArrayList;
import mobiarmy.server.Equip;
import mobiarmy.server.Glass;
import mobiarmy.server.User;
import mobiarmy.war.Gun;
import mobiarmy.war.MapData;
import mobiarmy.war.Player;
import mobiarmy.war.RoomRules;
import mobiarmy.war.RoomWait;
import mobiarmy.war.SkillBalance;
import mobiarmy.war.Boss.bullet.Bullet;
import sun.misc.Unsafe;

/**
 * Balanced-room damage (stat bonus only on accurate hits, crits and armour piercing only on direct hits) and the room
 * rules owners pick. Real Player, Bullet and RoomWait code, empty map, no database.
 */
public class SkillBalanceCheck {
    static int checks;

    public static void main(String[] args) throws Exception {
        formulas();
        battle();
        roomRules();
        System.out.println("SKILL BALANCE PASSED: " + checks + " checks; SkillBalance formulas + Player.attBullet/updateAllAffect, room rules, empty-map fixture");
    }

    /** What each room rule puts into battle, and who may change it. */
    static void roomRules() throws Exception {
        check(RoomRules.valid(0) && RoomRules.valid(2) && !RoomRules.valid(3) && !RoomRules.valid(-1), "three rules");
        check(RoomRules.name(RoomRules.BASE).equals("Sát thương gốc") && RoomRules.name(9).equals("Tự do"), "names");

        Glass.entrys = new Glass[10];
        Glass catalog = new Glass();
        catalog.id = 8;
        catalog.att = 431;
        catalog.ability = new int[] {0, 0, 10, 10, 10};
        Glass.entrys[8] = catalog;
        User user = user(8);
        Glass apache = user.glass[8];
        apache.att = 431;
        apache.ability = new int[] {50, 60, 10, 20, 10};
        Equip gun = equip(8, 0, 5, new byte[] {0, 30, 0, 0, 0}, new byte[5]);
        Equip thor = equip(8, mobiarmy.server.ThorSkin.SLOT, mobiarmy.server.ThorSkin.ID, new byte[5], new byte[] {5, 5, 5, 5, 5});
        user.equips.add(gun);
        user.equips.add(thor);
        int[] free = RoomRules.battleStats(user, RoomRules.FREE);
        int[] balanced = RoomRules.battleStats(user, RoomRules.BALANCED);
        thor.isUse = false;
        int[] withoutThor = RoomRules.battleStats(user, RoomRules.FREE);
        thor.isUse = true;
        check(java.util.Arrays.equals(balanced, withoutThor), "balanced rooms ignore Skin Thor and nothing else");
        check(free[0] > balanced[0] && free[1] > balanced[1], "free rooms count Skin Thor");
        check(balanced[1] > 431, "balanced rooms keep points and gear (the damage rules gate them)");
        check(java.util.Arrays.equals(RoomRules.battleStats(user, RoomRules.BASE), new int[] {1000, 431, 100, 100, 100}),
                "base rooms: a fresh level 1 character, no points or gear: " + java.util.Arrays.toString(RoomRules.battleStats(user, RoomRules.BASE)));

        byte saved = RoomRules.defaultRules;
        try {
            RoomRules.defaultRules = RoomRules.BALANCED;
            RoomWait room = new RoomWait((byte) 0, (byte) 0, (byte) 0, new byte[] {0}, 0, 0, (byte) 8);
            check(room.rules == RoomRules.BALANCED, "a room starts with the admin's default");
            User owner = user(0), guest = user(0);
            owner.id = 1;
            guest.id = 2;
            room.join(owner, "");
            room.join(guest, "");
            guest.ready = true;
            room.changeRules(guest, RoomRules.FREE);
            check(room.rules == RoomRules.BALANCED && guest.ready, "only the owner picks the rules");
            room.changeRules(owner, (byte) 7);
            check(room.rules == RoomRules.BALANCED, "unknown rules refused");
            room.changeRules(owner, RoomRules.BASE);
            check(room.rules == RoomRules.BASE && !guest.ready, "owner picks the rules and everyone has to get ready again");
            room.started = true;
            room.changeRules(owner, RoomRules.FREE);
            check(room.rules == RoomRules.BASE, "no changes during a match");
            room.started = false;
            room.leave(guest);
            room.leave(owner);
            check(room.rules == RoomRules.BALANCED, "an emptied room goes back to the default");
        } finally {
            RoomRules.defaultRules = saved;
        }
    }

    static User user(int glassId) throws Exception {
        User user = (User) unsafe().allocateInstance(User.class);
        user.glass = new Glass[10];
        for (int i = 0; i < user.glass.length; i++) {
            user.glass[i] = new Glass();
            user.glass[i].id = (byte) i;
            user.glass[i].user = user;
        }
        user.selectGlass = (byte) glassId;
        user.equips = new ArrayList<>();
        user.index = -1;
        user.xu = 1_000_000;
        return user;
    }

    static Equip equip(int glassId, int type, int id, byte[] ability, byte[] percent) {
        Equip equip = new Equip();
        equip.glassID = (byte) glassId;
        equip.type = (byte) type;
        equip.id = (short) id;
        equip.inv_ability = ability;
        equip.inv_percen = percent;
        equip.isUse = true;
        return equip;
    }

    static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    static void formulas() {
        check(SkillBalance.bonusPercent(410, 410) == 0, "no stats, no bonus");
        check(SkillBalance.bonusPercent(1000, 400) == 60, "60% of 1000 comes from stats");
        check(SkillBalance.bonusKept(100) == 100 && SkillBalance.bonusKept(80) == 100, "full bonus from 80% accuracy");
        check(SkillBalance.bonusKept(60) == 50, "half the bonus at 60% accuracy");
        check(SkillBalance.bonusKept(40) == 0 && SkillBalance.bonusKept(5) == 0, "no bonus at 40% and below");
        check(SkillBalance.hitDamage(1000, 1000, 60) == 1000, "a perfect hit keeps everything");
        check(SkillBalance.hitDamage(300, 1000, 60) == 120, "a far hit keeps only the character's own share");
        check(SkillBalance.hitDamage(300, 1000, 0) == 300, "a character without stats loses nothing on far hits");
        check(SkillBalance.hitDamage(-50, 1000, 60) == 0, "blast corners never heal");
        check(Math.abs(SkillBalance.luckChance(100_000) - SkillBalance.MAX_LUCK_PERCENT / 100.0) < 1e-9, "luck is capped");
        check(Math.abs(SkillBalance.reduction(100_000) - SkillBalance.MAX_REDUCTION_PERCENT / 100.0) < 1e-9, "defense is capped");
        check(SkillBalance.tankDamage(3000) == 2000 * SkillBalance.DIRECT_TANK_PERCENT / 100 && SkillBalance.tankDamage(800) == 0, "tank damage counts HP above 1000");
    }

    static void battle() throws Exception {
        // Target stands at x=500 on y=1000: its body is x 488..511, y 976..999 and its centre (500, 988).
        int direct = 990, far = 526;

        MapData classic = map(false);
        Player shooter = player(classic, 0, 100, 3000, 1000, 400, 0);
        Player target = player(classic, 1, 500, 5000, 1000, 1000, 0);
        int classicDirect = shoot(classic, shooter, target, 500, direct);
        int classicFar = shoot(classic, shooter, target, far, direct);
        check(classicDirect == 998 && classicFar == 300, "classic falloff unchanged: " + classicDirect + " / " + classicFar);

        MapData skill = map(true);
        shooter = player(skill, 0, 100, 3000, 1000, 400, 0);
        target = player(skill, 1, 500, 5000, 1000, 1000, 0);
        int tank = SkillBalance.tankDamage(5000);
        check(shoot(skill, shooter, target, 500, direct) == classicDirect + tank, "direct hit: full damage plus tank damage");
        check(shoot(skill, shooter, target, far, direct) == 120, "far hit: only the 40% that is the character's own attack");

        shooter.baseAtt = shooter.att;
        check(shoot(skill, shooter, target, far, direct) == classicFar, "a shooter without stat bonus deals the classic far damage");
        shooter.baseAtt = 400;

        shooter.lucky = true;
        check(shoot(skill, shooter, target, 500, direct) == 2 * classicDirect + tank, "luck doubles a direct hit");
        check(shoot(skill, shooter, target, far, direct) == 120, "luck does nothing for a far hit");
        shooter.lucky = false;

        target.def = 6000; // classic curve 63%, capped at 50%; a direct hit sees 3000 → 39.3%
        int pierced = (int) Math.round((classicDirect + tank) * Math.exp(-3000 / 6000.0));
        check(Math.abs(shoot(skill, shooter, target, 500, direct) - pierced) <= 1, "direct hit pierces half the defense");
        check(shoot(skill, shooter, target, far, direct) == 60, "far hit meets the capped 50% defense");
        target.def = 0;

        Player lucky = player(skill, 2, 500, 100, 1000, 1000, 0);
        lucky.lucky = true;
        shooter.baseAtt = shooter.att;
        shoot(skill, shooter, lucky, far, direct);
        check(!lucky.isDie && lucky.hp == 10, "a lucky turn still saves you from splash: hp " + lucky.hp);
        shoot(skill, shooter, lucky, 500, direct);
        check(lucky.isDie, "a lucky turn does not save you from a direct hit");

        Player gambler = player(skill, 3, 900, 5000, 1000, 1000, 0);
        gambler.luck = 1_000_000;
        int hits = 0;
        for (int i = 0; i < 20_000; i++) {
            gambler.nextLuck();
            if (gambler.lucky) hits++;
        }
        check(Math.abs(hits / 20_000.0 - SkillBalance.MAX_LUCK_PERCENT / 100.0) < 0.02, "luck chance capped in battle: " + hits / 200.0 + "%");
    }

    static MapData map(boolean skillRules) throws Exception {
        MapData map = (MapData) unsafe().allocateInstance(MapData.class);
        map.width = 3000;
        map.height = 2000;
        map.players = new Player[8];
        map.data = new ArrayList<>();
        map.tornados = new ArrayList<>();
        map.skillRules = skillRules;
        return map;
    }

    static Player player(MapData map, int index, int x, int hp, int att, int baseAtt, int def) {
        Player player = new Player(map, null, "p" + index, 0, 1, hp, att, def, 0, 0, 24, 24, null);
        player.index = (byte) index;
        player.baseAtt = baseAtt;
        player.x = (short) x;
        player.y = 1000;
        player.isFly = true; // keeps chuanHoaXY off the empty map
        map.players[index] = player;
        return player;
    }

    /** Explodes one bullet of the shooter at (x, y) and applies it like MapData.updateAffect; returns the HP lost. */
    static int shoot(MapData map, Player shooter, Player target, int x, int y) {
        Bullet bullet = new Bullet(new Gun(map, shooter.index, false), 0, shooter.att, x, y, 0, 0, 0, 0, 0);
        bullet.radius = 16;
        target.hp = target.hpMax;
        if (target.isCollision(x, y, bullet.radius)) target.attBullet(bullet);
        target.updateAllAffect(shooter);
        return target.hpMax - target.hp;
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        checks++;
    }
}
