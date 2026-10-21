package player;
import CLib.*;import Equipment.*;import coreLG.*;import model.*;import screen.*;import network.*;
import com.badlogic.gdx.*;import com.badlogic.gdx.backends.lwjgl3.*;import com.badlogic.gdx.graphics.*;import com.badlogic.gdx.graphics.g2d.*;import com.teamobi.mobiarmy2.MainGame;
public class ThorShopUiCheck extends MainGame {
 static Throwable failure; mGraphics g; int frames;
 void check(boolean b,String label){if(!b)throw new AssertionError(label);System.out.println("PASS "+label);}
 public void create(){try{
  super.create();g=new mGraphics(new SpriteBatch());mGraphics.zoomLevel=2;CCanvas.width=640;CCanvas.hieght=360;
  TerrainMidlet.myInfo=new PlayerInfo();TerrainMidlet.myInfo.gun=8;TerrainMidlet.myInfo.level2=1;TerrainMidlet.myInfo.luong=1000;
  CCanvas.readMess(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(System.getProperty("thor.catalog"))),(byte)1);
  TerrainMidlet.myInfo.myEquip=new PlayerEquip(new short[][]{{8,0,2},{8,1,-1},{8,2,0},{8,3,-1},{8,4,-1}});
  MenuScr.getIdMenu(0);if(CCanvas.menuScr==null)CCanvas.menuScr=new MenuScr();if(CCanvas.equipScreen==null)CCanvas.equipScreen=new EquipScreen();
  MessageHandler.gI().onMessage(new Message((byte)103,java.nio.file.Files.readAllBytes(java.nio.file.Path.of(System.getProperty("thor.catalog")+".shop"))));
  check(CCanvas.shopEquipScr.price.equals("Giá: 1000 lượng - Vĩnh viễn"),"actual shop packet shows exact price and permanence");
  check(CCanvas.shopEquipScr.equipName.contains("Skin Thor") && CCanvas.shopEquipScr.equipDetail.contains("10000"),"actual shop selects Thor first with bonuses");
  MessageHandler.gI().onMessage(new Message((byte)104,java.nio.file.Files.readAllBytes(java.nio.file.Path.of(System.getProperty("thor.catalog")+".purchase"))));
  Equip owned=null;for(Object object:EquipScreen.inventory){Equip e=(Equip)object;if(ThorSkin.is(e))owned=e;}check(owned!=null && owned.dbKey>0,"purchase packet adds usable inventory item");
  TerrainMidlet.myInfo.myEquip.equips[2]=owned;
  CCanvas.equipScreen.getBaseAttribute();java.lang.reflect.Field atts=EquipScreen.class.getDeclaredField("atts");atts.setAccessible(true);int[] stats=(int[])atts.get(CCanvas.equipScreen);
  check(stats[0]==11000 && stats[3]==10000 && stats[1]==PlayerEquip.getEquipGlass((byte)8).maxDamage+1000,"equipment screen shows full purchased stats");
  CCanvas.equipScreen.removeThorSkin();CCanvas.equipScreen.getBaseAttribute();stats=(int[])atts.get(CCanvas.equipScreen);check(stats[0]==1000 && stats[3]==0 && !ThorSkin.isEquipped(TerrainMidlet.myInfo.myEquip),"remove menu action restores ordinary armor and stats");
 }catch(Throwable t){failure=t;t.printStackTrace();Gdx.app.exit();}}
 public void render(){if(failure!=null)return;try{if(++frames<20)return;Gdx.gl.glClearColor(.1f,.12f,.18f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);OrthographicCamera c=new OrthographicCamera();c.setToOrtho(true,1280,720);g.g.setProjectionMatrix(c.combined);g.begin();CScreen.lastSCreen=null;CCanvas.shopEquipScr.cmx=0;CCanvas.shopEquipScr.expandDetail=true;CCanvas.shopEquipScr.paint(g);g.end();Pixmap pix=Pixmap.createFromFrameBuffer(0,0,1280,720);PixmapIO.writePNG(Gdx.files.absolute(System.getProperty("thor.shopPreview")),pix,-1,true);pix.dispose();System.out.println("THOR SHOP UI PASSED");Gdx.app.exit();}catch(Throwable t){failure=t;t.printStackTrace();Gdx.app.exit();}}
 public static void main(String[] args){Lwjgl3ApplicationConfiguration c=new Lwjgl3ApplicationConfiguration();c.setInitialVisible(false);c.setWindowedMode(1280,720);c.disableAudio(true);new Lwjgl3Application(new ThorShopUiCheck(),c);if(failure!=null)System.exit(1);}
}
