public class ThorSkinDisabledCheck {
 public static void main(String[] args) {
  if(player.ThorSkin.paint(null,new Equipment.PlayerEquip(),2,0,0,0))throw new AssertionError("unowned skin enabled");
  if(player.ThorSkin.projectile(null,0)!=null)throw new AssertionError("unowned projectile changed");
  System.out.println("THOR UNOWNED CHECK PASSED");
 }
}
