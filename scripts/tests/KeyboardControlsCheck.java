package screen;

import CLib.Image;
import CLib.mImage;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.teamobi.mobiarmy2.MainGame;
import coreLG.CCanvas;
import item.BM;
import player.CPlayer;
import player.PM;
import java.lang.reflect.*;

public class KeyboardControlsCheck extends MainGame {
    static Throwable failure;
    static int checks;
    GameScr screen;
    CPlayer player;
    InputProcessor input;
    Method update;
    Field nextRepeat;
    void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
        System.out.println("PASS " + label);
    }
    void tick() throws Exception { update.invoke(screen); }
    void repeat() throws Exception { nextRepeat.setLong(screen, 0L); tick(); }
    void reset() {
        screen.clearDesktopKeys(); screen.isChat=false; screen.isShowPausemenu=false;
        PM.curP=0; BM.active=false; CPlayer.isShooting=false;
        player.active=true; player.falling=false; player.look=2; player.angle=45;
        player.x=100; player.y=100; player.movePoint=0;
    }
    @Override public void create() {
        try {
            super.create();
            screen=new GameScr(); CCanvas.curScr=screen; CCanvas.currentDialog=null;
            Field crosshair=CPlayer.class.getDeclaredField("crosshair"); crosshair.setAccessible(true);
            Image image=new Image(); image.width=8; image.height=8;
            crosshair.set(null,new mImage(image));
            player=new CPlayer(1,(byte)0,100,100,false,2,(byte)16,null,100);
            player.gun=0; CPlayer.angleLock=new short[30];
            PM.p=new CPlayer[]{player}; GameScr.myIndex=0;
            input=Gdx.input.getInputProcessor();
            update=GameScr.class.getDeclaredMethod("updateDesktopKeys"); update.setAccessible(true);
            nextRepeat=GameScr.class.getDeclaredField("nextDesktopAngleAt"); nextRepeat.setAccessible(true);
            for(int key:new int[]{Keys.UP,Keys.W,Keys.DOWN,Keys.S}) {
                reset(); int direction=(key==Keys.UP||key==Keys.W)?1:-1;
                input.keyDown(key); check(player.angle==45+direction,"tap angle key " + key);
                input.keyDown(key); check(player.angle==45+direction,"ignore OS repeat " + key);
                tick(); check(player.angle==45+direction,"initial hold delay " + key);
                repeat(); repeat(); check(player.angle==45+3*direction,"held repeat " + key);
                input.keyUp(key); repeat(); check(player.angle==45+3*direction,"release stops " + key);
            }
            reset(); input.keyDown(Keys.W); Thread.sleep(220); tick();
            check(player.angle==47,"real elapsed hold repeats");
            Thread.sleep(50); tick(); check(player.angle==48,"real elapsed repeat continues");
            input.keyUp(Keys.W); Thread.sleep(50); tick(); check(player.angle==48,"real elapsed release stops");
            reset(); input.keyDown(Keys.UP); input.keyDown(Keys.W);
            check(player.angle==46,"same direction aliases do not double step");
            input.keyUp(Keys.UP); repeat(); check(player.angle==47,"W remains held after UP released");
            input.keyUp(Keys.W); repeat(); check(player.angle==47,"both aliases released");
            reset(); input.keyDown(Keys.W); input.keyDown(Keys.S); repeat();
            check(player.angle==46,"opposing aim cancels");
            input.keyUp(Keys.S); repeat(); check(player.angle==47,"remaining aim resumes");
            reset(); player.look=0; input.keyDown(Keys.W); repeat(); check(player.angle==43,"left facing aim");
            reset(); player.angle=180; input.keyDown(Keys.W); repeat(); check(player.angle==180,"upper angle bound");
            reset(); player.angle=0; input.keyDown(Keys.S); repeat(); check(player.angle==0,"lower angle bound");
            for(int key:new int[]{Keys.LEFT,Keys.A,Keys.RIGHT,Keys.D}) {
                reset(); input.keyDown(key); tick(); int expected=(key==Keys.LEFT||key==Keys.A)?99:101;
                check(player.x==expected,"movement key " + key);
                player.falling=false; input.keyUp(key); tick(); check(player.x==expected,"movement release " + key);
            }
            reset(); input.keyDown(Keys.A); input.keyDown(Keys.LEFT); input.keyUp(Keys.LEFT); tick();
            check(player.x==99,"A remains held after LEFT released");
            reset(); input.keyDown(Keys.A); input.keyDown(Keys.D); tick(); check(player.x==100,"opposing movement cancels");
            reset(); screen.isChat=true; input.keyDown(Keys.W); input.keyDown(Keys.A); repeat();
            check(player.angle==45 && player.x==100,"chat blocks gameplay keys");
            reset(); PM.curP=1; input.keyDown(Keys.W); input.keyDown(Keys.D); repeat();
            check(player.angle==45 && player.x==100,"other turn blocks controls");
            reset(); input.keyDown(Keys.W); player.force=10; super.pause(); super.resume(); repeat();
            check(player.angle==46 && player.force==0,"focus loss clears held input and force");
            reset(); input.keyDown(Keys.W); screen.isChat=true; tick(); screen.isChat=false; repeat();
            check(player.angle==46,"chat transition clears held input");
            reset(); input.keyDown(Keys.W); player.falling=true; repeat(); check(player.angle==46,"falling pauses aim");
            reset(); map.MM.mapWidth=3000; map.MM.mapHeight=2000;
            CScreen.w=640; CScreen.h=360; effect.Camera.x=500; effect.Camera.y=300;
            GameScr.cam.setPlayerMode(0);
            player.force=10; input.keyDown(Keys.R);
            check(effect.Camera.mode==effect.Camera.FREE_MODE && player.force==0,"R enables camera and cancels charge");
            input.keyDown(Keys.R); check(effect.Camera.mode==effect.Camera.FREE_MODE,"holding R does not toggle twice");
            input.keyUp(Keys.R);
            for(int key:new int[]{Keys.UP,Keys.W,Keys.DOWN,Keys.S,Keys.LEFT,Keys.A,Keys.RIGHT,Keys.D}) {
                effect.Camera.x=500; effect.Camera.y=300;
                int dx=(key==Keys.LEFT||key==Keys.A)?-8:(key==Keys.RIGHT||key==Keys.D)?8:0;
                int dy=(key==Keys.UP||key==Keys.W)?-8:(key==Keys.DOWN||key==Keys.S)?8:0;
                input.keyDown(key); tick(); tick();
                check(effect.Camera.x==500+2*dx && effect.Camera.y==300+2*dy,"camera held pan key "+key);
                check(player.x==100 && player.angle==45,"camera key leaves player unchanged "+key);
                input.keyUp(key); tick();
                check(effect.Camera.x==500+2*dx && effect.Camera.y==300+2*dy,"camera release stops "+key);
            }
            PM.curP=1; input.keyDown(Keys.D); int cameraBefore=effect.Camera.x; tick(); input.keyUp(Keys.D);
            check(effect.Camera.x==cameraBefore+8,"camera works outside own turn"); PM.curP=0;
            effect.Camera.x=0; input.keyDown(Keys.A); tick(); input.keyUp(Keys.A);
            check(effect.Camera.x==0,"camera left boundary");
            effect.Camera.x=3000-640; input.keyDown(Keys.D); tick(); input.keyUp(Keys.D);
            check(effect.Camera.x==2360,"camera right boundary");
            effect.Camera.y=-1000; input.keyDown(Keys.W); tick(); input.keyUp(Keys.W);
            check(effect.Camera.y==-1000,"camera sky boundary");
            effect.Camera.y=2000-360; input.keyDown(Keys.S); tick(); input.keyUp(Keys.S);
            check(effect.Camera.y==1640,"camera lower boundary");
            input.keyDown(Keys.D); super.pause(); super.resume(); cameraBefore=effect.Camera.x; tick();
            check(effect.Camera.x==cameraBefore,"focus loss stops camera pan");
            screen.isChat=true; input.keyDown(Keys.R); input.keyUp(Keys.R);
            check(effect.Camera.mode==effect.Camera.FREE_MODE,"chat does not toggle camera"); screen.isChat=false;
            input.keyDown(Keys.R); input.keyUp(Keys.R);
            check(effect.Camera.mode==effect.Camera.PLAYER_MODE,"R returns to player follow");
            input.keyDown(Keys.W); check(player.angle==46,"aim restored after camera off"); input.keyUp(Keys.W);
            reset(); effect.Camera.x=0; effect.Camera.y=0;
            GameScr.trai=new mImage(image); GameScr.phai=new mImage(image); GameScr.crossHair2=new mImage(image);
            GameScr.xF=-1000; GameScr.yF=-1000; GameScr.xL=-1000; GameScr.yL=-1000; GameScr.xR=-1000; GameScr.yR=-1000;
            CCanvas.isPointerDown[0]=true; CCanvas.pX[0]=300; CCanvas.pY[0]=200;
            CPlayer.isStopFire=false; CCanvas.pausemenu.isShow=false;
            player.onPointerDrag(300,200,0); player.onPointerHold(300,200,0);
            check(player.angle==45 && player.look==2,"desktop mouse drag and hold cannot aim");
            com.teamobi.mobiarmy2.GameMidlet.DEVICE=1;
            player.onPointerHold(300,200,0);
            check(player.angle!=45,"touch aim remains available on mobile");
            com.teamobi.mobiarmy2.GameMidlet.DEVICE=4; CCanvas.isPointerDown[0]=false;
            reset(); GameScr.trainingMode=false; tick();
            final java.util.List<network.Message> sent=new java.util.ArrayList<>();
            network.GameService.gI().setSession(new network.ISession() {
                public boolean isConnected(){return true;}
                public void setHandler(network.IMessageHandler h){}
                public void connect(String h,String p){}
                public void sendMessage(network.Message m){sent.add(new network.Message(m.command,m.getData()));}
            });
            input.keyDown(Keys.D); tick(); input.keyUp(Keys.D); player.falling=false; tick();
            check(sent.size()==1 && sent.get(0).command==21,"keyboard movement sends position after landing");
            check(sent.get(0).reader().readShort()==101 && sent.get(0).reader().readShort()==100,"movement packet has current coordinates");
            check(player.lastUpdateX==101 && player.lastUpdateY==100,"keyboard movement updates rollback anchor");
            sent.clear(); player.x=123; player.y=104; player.force=12;
            Method sendShot=CPlayer.class.getDeclaredMethod("sendWaitForFire",byte.class,byte.class); sendShot.setAccessible(true);
            sendShot.invoke(player,(byte)0,(byte)1);
            check(sent.size()==2 && sent.get(0).command==21 && sent.get(1).command==22,"position packet precedes shot packet");
            check(sent.get(0).reader().readShort()==123 && sent.get(0).reader().readShort()==104,"pre-shot position matches moved player");
            player.resetXYwhenNEXTTURN(); check(player.x==123 && player.y==104,"next turn does not snap to old position");
            sent.clear(); GameScr.trainingMode=true; sendShot.invoke(player,(byte)0,(byte)1);
            check(sent.size()==1 && sent.get(0).command==84,"training keeps training protocol");
            GameScr.trainingMode=false;
            System.out.println("KEYBOARD CHECKS PASSED: " + checks);
        } catch(Throwable t) { failure=t; t.printStackTrace(); }
        finally { Gdx.app.exit(); }
    }
    @Override public void render() {}
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false); config.setWindowedMode(640,360); config.disableAudio(true);
        new Lwjgl3Application(new KeyboardControlsCheck(),config);
        if(failure!=null) System.exit(1);
    }
}
