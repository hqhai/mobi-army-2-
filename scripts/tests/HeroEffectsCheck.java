package player;

import CLib.mGraphics;
import Equipment.PlayerEquip;
import coreLG.CCanvas;
import coreLG.TerrainMidlet;
import effect.HeroEffects;
import item.BM;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.teamobi.mobiarmy2.MainGame;

/** Real GL render plus POW lifetime/identity regression without modifying user accounts. */
public final class HeroEffectsCheck extends MainGame {
    static Throwable failure;
    mGraphics g;
    PlayerEquip thor;
    int frames, checks;
    void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
        System.out.println("PASS "+label);
    }
    public void create() {
        try {
            super.create();
            g=new mGraphics(new SpriteBatch());
            TerrainMidlet.myInfo=new model.PlayerInfo();
            CCanvas.readMess(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                    System.getProperty("review.root"),"verification/thor-catalog.bin")),(byte)1);
            thor=new PlayerEquip(new short[][]{{8,0,2},{8,1,-1},{8,2,30000},{8,3,-1},{8,4,-1}});
            java.lang.reflect.Field uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            uf.setAccessible(true);
            sun.misc.Unsafe u=(sun.misc.Unsafe)uf.get(null);
            CPlayer p=(CPlayer)u.allocateInstance(CPlayer.class);
            p.gun=8;p.equip=thor;PM.p=new CPlayer[]{p};
            check(HeroEffects.thor(0),"owned Thor selected");
            check(!HeroEffects.thor(-1) && !HeroEffects.thor(1),"invalid shooter ignored");
            p.equip=null;check(!HeroEffects.thor(0),"ordinary Apache unchanged");
            p.gun=9;check(HeroEffects.photon(0),"Magenta photon selected");
            p.gun=0;check(!HeroEffects.photon(0),"other guns unchanged");
            BM bm=new BM();
            p.itemUsed=100;
            bm.setBullType((byte)0,(byte)0,(byte)17,null,null,(byte)1,(byte)0,null,null,0);
            check(bm.heroPowShot,"POW captured from accepted item 100");
            p.itemUsed=-1;check(bm.heroPowShot,"POW persists through delayed child projectiles");
            bm.setBullType((byte)0,(byte)0,(byte)17,null,null,(byte)1,(byte)0,null,null,0);
            check(!bm.heroPowShot,"next ordinary shot clears POW");
            p.isAngry=true;
            bm.setBullType((byte)0,(byte)0,(byte)49,null,null,(byte)1,(byte)0,null,null,0);
            check(!bm.heroPowShot,"full angry meter alone does not enable POW shot");
            p.gun=8;p.equip=thor;
            model.FrameImage normal=ThorSkin.projectile(null,0,false);
            model.FrameImage powered=ThorSkin.projectile(null,0,true);
            check(normal.frameWidth==20 && powered.frameWidth==40 && powered.nFrame==8,
                    "POW hammer is double size with eight rotation frames");
            p.equip=null;
            check(ThorSkin.projectile(normal,0,true)==normal,"unowned POW retains original bullet");
        } catch(Throwable t) { fail(t); }
    }
    void fail(Throwable t){failure=t;t.printStackTrace();Gdx.app.exit();}
    public void render() {
        if(failure!=null)return;
        try {
            Gdx.gl.glClearColor(.06f,.09f,.15f,1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            OrthographicCamera c=new OrthographicCamera();c.setToOrtho(true,960,600);
            g.g.setProjectionMatrix(c.combined);mGraphics.zoomLevel=3;
            g.begin();
            HeroEffects.beam(g,20,55,140,20,false,false,frames);
            HeroEffects.beam(g,140,20,290,65,false,false,frames);
            HeroEffects.beam(g,20,120,140,85,true,false,frames);
            HeroEffects.beam(g,140,85,290,130,true,false,frames);
            thor.paint(g,2,0,45,193);
            for(int i=0;i<3;i++){
                int x=130+i*65,y=173+(i-1)*9;
                HeroEffects.hammer(g,x,y,x-10,y+2,frames);
                // The same rotating hammer atlas used by live Thor bullets.
                java.lang.reflect.Method load=ThorSkin.class.getDeclaredMethod("load");
                load.setAccessible(true);load.invoke(null);
                java.lang.reflect.Field hammer=ThorSkin.class.getDeclaredField("powHammer");
                hammer.setAccessible(true);
                ((model.FrameImage)hammer.get(null)).drawFrame(i*2,x,y,0,3,g);
            }
            g.end();
            if(++frames==16){
                Pixmap pix=Pixmap.createFromFrameBuffer(0,0,960,600);
                PixmapIO.writePNG(Gdx.files.absolute(System.getProperty("review.root")
                        +"/art/concepts/hero-effects-preview.png"),pix,-1,true);
                pix.dispose();
                System.out.println("HERO EFFECTS PASSED: "+checks+" checks plus GL render");
                Gdx.app.exit();
            }
        }catch(Throwable t){fail(t);}
    }
    public static void main(String[] args){
        Lwjgl3ApplicationConfiguration c=new Lwjgl3ApplicationConfiguration();
        c.setInitialVisible(false);c.setWindowedMode(960,600);c.disableAudio(true);
        new Lwjgl3Application(new HeroEffectsCheck(),c);
        if(failure!=null)System.exit(1);
    }
}
