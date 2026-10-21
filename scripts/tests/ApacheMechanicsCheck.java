import mobiarmy.war.*;
import mobiarmy.war.Boss.bullet.*;
import java.util.*;
public class ApacheMechanicsCheck {
 public static void main(String[] args) throws Exception {
  java.lang.reflect.Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true);
  sun.misc.Unsafe u=(sun.misc.Unsafe)f.get(null);
  int checks=0;
  for(int angle:new int[]{30,60,120,150}) for(int split:new int[]{5,12,20}) for(boolean pow:new boolean[]{false,true}) {
   MapData map=(MapData)u.allocateInstance(MapData.class);
   map.width=3000; map.height=2000; map.tornados=new ArrayList<>();
   Gun gun=new Gun(map,0,pow);
   gun.shootBullet(17,1000,900,24,24,angle,24,split,1,400,10);
   if(gun.bullets.size()!=1 || !(gun.bullets.get(0) instanceof ApacheBullet)) throw new AssertionError("parent");
   Bullet parent=gun.bullets.get(0); parent.isCanCollision=false; parent.isCanCollisionMap=false; parent.isCanCollisionPlayer=false;
   for(int frame=1;frame<split;frame++) { parent.nextXY(); if(gun.bullets.size()!=1)throw new AssertionError("early split"); }
   parent.nextXY();
   if(gun.bullets.size()!=4 || !parent.collect || parent.att!=10)throw new AssertionError("split");
   for(int i=1;i<=3;i++) { Bullet child=gun.bullets.get(i); if(child.bulletId!=18 || child.att!=(pow?200:100)) throw new AssertionError("child stats"); }
   if(gun.bullets.get(1).vx==gun.bullets.get(3).vx && gun.bullets.get(1).vy==gun.bullets.get(3).vy)throw new AssertionError("spread");
   checks++;
  }
  System.out.println("APACHE MECHANICS PASSED: "+checks+" scenarios; actual Gun + ApacheBullet, empty-map fixture");
 }
}
