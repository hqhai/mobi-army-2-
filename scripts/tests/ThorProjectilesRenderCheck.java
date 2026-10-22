package player;

import CLib.*;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.teamobi.mobiarmy2.MainGame;

/** Renders the production effect code, not an artist mockup. */
public class ThorProjectilesRenderCheck extends MainGame {
    static Throwable failure; mGraphics g;BitmapFont font;ThorProjectiles.Profile[] profiles;int frames;
    public void create(){try{super.create();g=new mGraphics(new SpriteBatch());font=new BitmapFont(true);profiles=ThorProjectiles.parse(Gdx.files.internal("skins/thor-classes/projectiles.json").readString());checkShotWiring();checkPixels();}catch(Throwable t){fail(t);}}
    void checkPixels() {
        java.util.Set<Long> fingerprints=new java.util.HashSet<Long>();int count=0;
        for(ThorProjectiles.Profile p:profiles)if(p.sprite!=null)for(boolean pow:new boolean[]{false,true}) {
            Pixmap pixels=ThorProjectileSprites.pixels(p,pow);int opaque=0,blue=0,silver=0;long hash=1;
            try {
                for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++) {
                    int rgba=pixels.getPixel(x,y),a=rgba&255;hash=hash*31+rgba;
                    if(a==0)continue;opaque++;int r=rgba>>>24,g=(rgba>>>16)&255,b=(rgba>>>8)&255;
                    if(b>r+25&&g>r+15)blue++;
                    if(r>80&&g>80&&b>80&&Math.abs(r-g)<40&&Math.abs(b-g)<40)silver++;
                    if(x==0||y==0||x==pixels.getWidth()-1||y==pixels.getHeight()-1)throw new AssertionError("clipped sprite "+p.glass);
                    if(ThorProjectileSprites.background(rgba))throw new AssertionError("navy background leaked "+p.glass);
                }
                if(opaque<60||opaque>pixels.getWidth()*pixels.getHeight()/2||blue<8||silver<8)throw new AssertionError("empty/opaque/missing material sprite "+p.glass+":"+pow);
                if(!fingerprints.add(hash))throw new AssertionError("duplicate sprite "+p.glass);count++;
                for(int frame=0;frame<16;frame++) {
                    Pixmap turn=ThorProjectileSprites.rotated(pixels,frame);
                    try {
                        int size=turn.getWidth(),center=size/2;
                        if(turn.getPixel(center,center)!=pixels.getPixel(center,center))throw new AssertionError("rotation moved body pivot");
                        for(int edge=0;edge<size;edge++)if(((turn.getPixel(edge,0)|turn.getPixel(edge,size-1)|turn.getPixel(0,edge)|turn.getPixel(size-1,edge))&255)!=0)
                            throw new AssertionError("rotation clipped sprite "+p.glass+":"+frame);
                    }finally{turn.dispose();}
                }
            }finally{pixels.dispose();}
        }
        System.out.println("THOR ATLAS PASSED: "+count+" distinct sprites, 256 rotations, transparent borders, stable pivots, silver and lightning retained");
    }
    // Suppress unrelated smoke/network prerequisites; execute the real Bullet initialization.
    static class Shot extends item.Bullet {
        Shot(int type,int shooter){super((byte)type,new short[]{80,85,90},new short[]{80,75,80},(byte)shooter,0);}
        public void getPlayerEffect(){}
    }
    void checkShotWiring()throws Exception {
        Pixmap pixels=new Pixmap(64,64,Pixmap.Format.RGBA8888);pixels.setColor(1,1,1,1);pixels.fill();
        CLib.Image image=new CLib.Image();image.texture=new Texture(pixels);image.width=64;image.height=64;pixels.dispose();mImage fixture=new mImage(image);
        for(java.lang.reflect.Field f:item.Bullet.class.getDeclaredFields())if(java.lang.reflect.Modifier.isStatic(f.getModifiers())&&f.getType()==mImage.class){f.setAccessible(true);f.set(null,fixture);}
        for(int i=0;i<10;i++)Equipment.PlayerEquip.bullets[i]=fixture;
        screen.GameScr.bm=new item.BM();for(int i=0;i<58;i++)for(int j=0;j<2;j++)screen.GameScr.bm.frameBulletSpecial[i][j]=new model.FrameImage(image,8,8);
        java.lang.reflect.Field uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);sun.misc.Unsafe unsafe=(sun.misc.Unsafe)uf.get(null);
        PM.p=new CPlayer[]{(CPlayer)unsafe.allocateInstance(CPlayer.class),(CPlayer)unsafe.allocateInstance(CPlayer.class)};PM.curP=0;
        java.lang.reflect.Field profile=item.Bullet.class.getDeclaredField("thorProfile"),pow=item.Bullet.class.getDeclaredField("heroPow");profile.setAccessible(true);pow.setAccessible(true);
        int count=0;
        for(ThorProjectiles.Profile p:profiles) {
            PM.p[0].gun=(byte)p.glass;PM.p[1].gun=(byte)p.glass;
            for(int type:p.types)for(boolean powered:new boolean[]{false,true}) {
                PM.p[0].equip=ThorProjectilesCheck.outfit(p);PM.p[1].equip=new Equipment.PlayerEquip();
                screen.GameScr.bm.heroPowShot=powered;Shot a=new Shot(type,0),b=new Shot(type,1);
                if(profile.get(a)==null||profile.get(b)!=null||pow.getBoolean(a)!=powered)throw new AssertionError("owner / remote / POW wiring "+p.glass);
                PM.p[0].equip=new Equipment.PlayerEquip();screen.GameScr.bm.heroPowShot=!powered;
                if(profile.get(a)==null||pow.getBoolean(a)!=powered||profile.get(new Shot(type,0))!=null)throw new AssertionError("shot snapshot / unequip "+p.glass);
                java.lang.reflect.Field fx=item.Bullet.class.getDeclaredField("x"),fy=item.Bullet.class.getDeclaredField("y");fx.setAccessible(true);fy.setAccessible(true);
                if(fx.getInt(a)!=80||fy.getInt(a)!=80)throw new AssertionError("cosmetic changed trajectory start");count++;
            }
        }
        System.out.println("THOR SHOT WIRING PASSED: "+count+" real Bullet initializations, two shooters, equip/unequip and POW snapshots");
    }
    static void fail(Throwable t){failure=t;t.printStackTrace();Gdx.app.exit();}
    public void render(){if(failure!=null)return;try{
        Gdx.gl.glClearColor(.035f,.065f,.10f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        OrthographicCamera camera=new OrthographicCamera();camera.setToOrtho(true,1200,1200);g.g.setProjectionMatrix(camera.combined);mGraphics.zoomLevel=3;
        g.begin();font.draw(g.g,"THOR",20,15);font.draw(g.g,"NORMAL",230,15);font.draw(g.g,"POW",605,15);font.draw(g.g,"LOW DETAIL (POW)",930,15);
        String[] names={"Gunner","Miss 6","Electrician","KingKong","Rocketer","Granos","Chicky","Tarzan","Apache","Magenta"};
        for(int i=0;i<10;i++) {
            ThorProjectiles.Profile p=profiles[i];int y=30+i*38;font.draw(g.g,names[i],15,y*3-6);
            for(int col=0;col<3;col++) {
                int x=85+col*123;boolean pow=col!=0,low=col==2;
                if(p.beam)ThorProjectiles.beam(g,p,pow,low,x-25,y,x+25,y,17);
                else {ThorProjectiles.projectile(g,p,pow,low,x,y,x-2,y,17);if(p.nativeHammer){// Native artwork loaded via the approved skin below.
                    java.lang.reflect.Method load=ThorSkin.class.getDeclaredMethod("load");load.setAccessible(true);load.invoke(null);
                    java.lang.reflect.Field f=ThorSkin.class.getDeclaredField(pow?"powHammer":"hammer");f.setAccessible(true);((model.FrameImage)f.get(null)).drawFrame(1,x,y,0,3,g,false);}}
            }
        }
        g.end();if(++frames==4){Pixmap image=Pixmap.createFromFrameBuffer(0,0,1200,1200);PixmapIO.writePNG(Gdx.files.absolute(System.getProperty("thor.preview")),image,-1,true);image.dispose();System.out.println("THOR RENDER PASSED: 10 classes x normal/POW/low detail");Gdx.app.exit();}
    }catch(Throwable t){fail(t);}}
    public static void main(String[] args){Lwjgl3ApplicationConfiguration c=new Lwjgl3ApplicationConfiguration();c.setInitialVisible(false);c.setWindowedMode(1200,1200);c.disableAudio(true);new Lwjgl3Application(new ThorProjectilesRenderCheck(),c);if(failure!=null)System.exit(1);}
}
