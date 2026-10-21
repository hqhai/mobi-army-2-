package player;
import CLib.*;import Equipment.*;import coreLG.*;import model.PlayerInfo;
import com.badlogic.gdx.*;import com.badlogic.gdx.backends.lwjgl3.*;import com.badlogic.gdx.graphics.*;import com.badlogic.gdx.graphics.g2d.*;import com.teamobi.mobiarmy2.MainGame;
public class PhotonPoseReview extends MainGame {
 static Throwable failure; mGraphics g; PlayerEquip iron,photon; int frames;
 public void create(){try{super.create();g=new mGraphics(new SpriteBatch());TerrainMidlet.myInfo=new PlayerInfo();String root=System.getProperty("review.root");CCanvas.readMess(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(root,"verification/thor-catalog.bin")),(byte)1);
 byte[] body=java.nio.file.Files.readAllBytes(java.nio.file.Path.of(root,"server/res/player/cowg.png"));CPlayer.pImg[9]=new mImage(CLib.Image.createImage(body,0,body.length));
 byte[] atlas=java.nio.file.Files.readAllBytes(java.nio.file.Path.of(root,"server/res/bigImage9.png"));PlayerEquip.imgData[9]=new mImage(CLib.Image.createImage(atlas,0,atlas.length));
 iron=new PlayerEquip(new short[][]{{9,0,21},{9,1,19},{9,2,20},{9,3,-1},{9,4,-1}});
 photon=new PlayerEquip(new short[][]{{9,0,21},{9,1,19},{9,2,20},{9,3,-1},{9,4,-1}});photon.equips[1].id=30001; if(iron.equips[0]==null||iron.equips[1]==null||iron.equips[2]==null)throw new AssertionError("native catalog missing");
 }catch(Throwable t){failure=t;t.printStackTrace();Gdx.app.exit();}}
 public void render(){if(failure!=null)return;try{Gdx.gl.glClearColor(.06f,.09f,.15f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);OrthographicCamera c=new OrthographicCamera();c.setToOrtho(true,1000,400);g.g.setProjectionMatrix(c.combined);mGraphics.zoomLevel=4;g.begin();
 for(int i=0;i<10;i++){int look=i<6?2:0,frame=i<6?i:i-6;int x=15+i*24;iron.paintSung(x,35,look,frame,g);iron.paintFace(x,35,look,frame,g);iron.paintNon(x,35,look,frame,g);iron.paintGiap(x,35,look,frame,g);if(!PhotonSkin.paint(g,photon,look,frame,x,85))throw new AssertionError("override failed");}
 g.end();if(++frames==12){Pixmap p=Pixmap.createFromFrameBuffer(0,0,1000,400);PixmapIO.writePNG(Gdx.files.absolute(System.getProperty("review.root")+"/art/concepts/magenta-photon/photon-ten-review-v6.png"),p,-1,true);p.dispose();if(PhotonSkin.paint(null,10,0,0,0,0))throw new AssertionError("boss replaced");System.out.println("Rendered ten pose pairs for visual review only");Gdx.app.exit();}}catch(Throwable t){failure=t;t.printStackTrace();Gdx.app.exit();}}
 public static void main(String[] args){Lwjgl3ApplicationConfiguration c=new Lwjgl3ApplicationConfiguration();c.setInitialVisible(false);c.setWindowedMode(1000,400);c.disableAudio(true);new Lwjgl3Application(new PhotonPoseReview(),c);if(failure!=null)System.exit(1);}
}
