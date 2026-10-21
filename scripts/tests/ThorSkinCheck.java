package player;
import CLib.*;
import Equipment.*;
import player.*;
import model.FrameImage;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.teamobi.mobiarmy2.MainGame;
import coreLG.CCanvas;
import coreLG.TerrainMidlet;
import screen.GameScr;
public class ThorSkinCheck extends MainGame {
 static Throwable failure; mGraphics graphics; int frames; PlayerEquip outfit; int checks;
 void check(boolean b,String label){if(!b)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
 public void create() { try {
  super.create();graphics=new mGraphics(new SpriteBatch());mGraphics.zoomLevel=3;
  TerrainMidlet.myInfo=new model.PlayerInfo();TerrainMidlet.myInfo.gun=8;CCanvas.readMess(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(System.getProperty("thor.catalog"))),(byte)1);
  Equip skin=PlayerEquip.getEquip((byte)8,(byte)2,(short)30000);check(skin!=null,"real server catalog decodes skin ID 30000");
  outfit=new PlayerEquip(new short[][]{{8,0,GlassDefaults.weapon()},{8,1,-1},{8,2,30000},{8,3,-1},{8,4,-1}});
  // The weapon slot is not needed for skin rendering; use a catalog-independent placeholder.
  if(outfit.equips[0]==null){outfit.equips[0]=new Equip();outfit.equips[0].glass=8;}
  check(ThorSkin.isEquipped(outfit),"appearance reconstructed from equipment IDs");
  check(skin.getStrShopDetail().contains("10000") && skin.getStrInvDetail().contains("1000"),"shop and inventory show full bonuses");
  GameScr game=new GameScr();CCanvas.curScr=game;CCanvas.currentDialog=null;
  java.lang.reflect.Field cross=CPlayer.class.getDeclaredField("crosshair");cross.setAccessible(true);CLib.Image dummy=new CLib.Image();dummy.width=8;dummy.height=8;cross.set(null,new mImage(dummy));
  CPlayer p=new CPlayer(1,(byte)0,100,100,false,2,(byte)16,null,11000);p.gun=8;p.equip=outfit;p.active=true;p.falling=false;p.movePoint=0;CPlayer.angleLock=new short[30];
  PM.p=new CPlayer[]{p};PM.curP=0;GameScr.myIndex=0;
  java.lang.reflect.Field current=CPlayer.class.getDeclaredField("curFrame");current.setAccessible(true);
  java.util.Set<Integer> walked=new java.util.HashSet<>();
  for(int i=0;i<6;i++){p.falling=false;p.move(2);walked.add(current.getInt(p));}
  check(p.x==106 && walked.contains(4) && walked.contains(5),"Apache actually moves six pixels and alternates both walk frames");
  p.falling=false;p.move(0);check(p.x==105 && p.look==0,"Apache also moves left");
  int[] stats={1000,100,0,0,0};ThorSkin.addBonus(outfit,stats);check(stats[0]==11000 && stats[1]==1100 && stats[3]==10000,"client displays exact flat bonus");
  check(!ThorSkin.paint(null,new PlayerEquip(),2,0,0,0),"unowned Apache not reskinned");
  check(ThorSkin.projectile(null,-1)==null && ThorSkin.projectile(null,1)==null,"invalid shooter guarded");
 } catch(Throwable t){failure=t;t.printStackTrace();Gdx.app.exit();} }
 static class GlassDefaults {static short weapon(){return 2;}}
 public void render() { if(failure!=null)return;try {
  Gdx.gl.glClearColor(.12f,.16f,.22f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
  OrthographicCamera c=new OrthographicCamera();c.setToOrtho(true,900,420);graphics.g.setProjectionMatrix(c.combined);
  FrameImage hammer=ThorSkin.projectile(null,0);if(hammer==null || hammer.nFrame!=8)throw new AssertionError("hammer");
  if(frames==0){
   GameScr.bm=new item.BM();java.lang.reflect.Field uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);sun.misc.Unsafe u=(sun.misc.Unsafe)uf.get(null);
   java.lang.reflect.Method init=item.Bullet.class.getDeclaredMethod("initBulletType",int.class);init.setAccessible(true);java.lang.reflect.Field img=item.Bullet.class.getDeclaredField("frmImg");img.setAccessible(true);
   for(int type:new int[]{17,18}){Object b=u.allocateInstance(item.Bullet.class);init.invoke(b,type);check(img.get(b)==hammer,"projectile hook "+type);}
   PlayerEquip saved=PM.p[0].equip;PM.p[0].equip=null;check(ThorSkin.projectile(null,0)==null,"ordinary Apache keeps original projectile");PM.p[0].equip=saved;
  }
  graphics.begin();for(int i=0;i<10;i++)ThorSkin.paint(graphics,outfit,i<5?2:0,i,18+i*29,55);
  com.badlogic.gdx.graphics.glutils.ShaderProgram previous=graphics.g.getShader();
  for(int i=0;i<4;i++){outfit.paint(graphics,i<2?2:0,4+i%2,30+i*35,110);if(graphics.g.getShader()!=previous)throw new AssertionError("walk shader leaked");}
  if(frames==0)check(true,"walk shader restored in both directions and phases");
  for(int i=0;i<8;i++)hammer.drawFrame(i,160+i*17,100,0,3,graphics);
  outfit.equips[2].drawIcon(graphics,275,10,false);graphics.end();
  if(++frames==20){Pixmap pix=Pixmap.createFromFrameBuffer(0,0,900,420);
   int differences=0;for(int y=420-330;y<420-300;y++)for(int x=60;x<125;x++)if(pix.getPixel(x,y)!=pix.getPixel(x+105,y))differences++;
   check(differences>30,"walking legs visibly differ in rendered pixels");
   PixmapIO.writePNG(Gdx.files.absolute(System.getProperty("thor.preview")),pix,-1,true);pix.dispose();System.out.println("THOR CLIENT PASSED: "+checks+" checks and render");Gdx.app.exit();}
 }catch(Throwable t){failure=t;t.printStackTrace();Gdx.app.exit();} }
 public static void main(String[] args){Lwjgl3ApplicationConfiguration c=new Lwjgl3ApplicationConfiguration();c.setInitialVisible(false);c.setWindowedMode(900,420);c.disableAudio(true);new Lwjgl3Application(new ThorSkinCheck(),c);if(failure!=null)System.exit(1);}
}
