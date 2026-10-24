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
    Field chatOpen;
    /** Chat as the player sees it: the chat icon was clicked and its text box is (or is no longer) on screen. */
    void chat(boolean open) throws Exception { screen.isChat=open; chatOpen.setBoolean(GameScr.tfChat,open); }
    void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
        System.out.println("PASS " + label);
    }
    /** Message 114 for these player indexes, none poisoned or stunned; hidden >= 0 adds the build 5 list naming it. */
    static network.Message status(int[] players,int hidden) throws Exception {
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream(); java.io.DataOutputStream out=new java.io.DataOutputStream(bytes);
        out.writeByte(players.length);
        for(int index:players){out.writeByte(index); out.writeBoolean(false); out.writeBoolean(false);}
        if(hidden>=0){out.writeByte(1); out.writeByte(hidden);}
        return new network.Message((byte)114,bytes.toByteArray());
    }
    /** Message 109: a time bomb or Chicky's Easter egg appears (type 0, with its owner when >= 0), explodes (1) or hatches (2). */
    static network.Message bomb(int type,int id,int x,int y,int owner) throws Exception {
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream(); java.io.DataOutputStream out=new java.io.DataOutputStream(bytes);
        out.writeByte(type); out.writeByte(id);
        if(type==0){out.writeInt(x); out.writeInt(y); if(owner>=0) out.writeByte(owner);}
        return new network.Message((byte)109,bytes.toByteArray());
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
            chatOpen=model.TField.class.getDeclaredField("isOpenInput"); chatOpen.setAccessible(true);
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
            reset(); player.angle=270; input.keyDown(Keys.W); repeat(); check(player.angle==270,"upper angle bound");
            reset(); player.angle=0; input.keyDown(Keys.S); repeat(); check(player.angle==0,"lower angle bound at a 0 gun limit");
            reset(); CPlayer.angleLock[0]=-45; player.angle=0; input.keyDown(Keys.S); check(player.angle==-1,"aim below horizontal");
            input.keyUp(Keys.S); reset(); player.angle=-45; input.keyDown(Keys.S); repeat();
            check(player.angle==-45,"stops at the gun's angle limit"); CPlayer.angleLock[0]=0;
            for(int key:new int[]{Keys.LEFT,Keys.A,Keys.RIGHT,Keys.D}) {
                reset(); input.keyDown(key); tick(); int expected=(key==Keys.LEFT||key==Keys.A)?99:101;
                check(player.x==expected,"movement key " + key);
                player.falling=false; input.keyUp(key); tick(); check(player.x==expected,"movement release " + key);
            }
            reset(); input.keyDown(Keys.A); input.keyDown(Keys.LEFT); input.keyUp(Keys.LEFT); tick();
            check(player.x==99,"A remains held after LEFT released");
            reset(); input.keyDown(Keys.A); input.keyDown(Keys.D); tick(); check(player.x==100,"opposing movement cancels");
            reset(); chat(true); input.keyDown(Keys.W); input.keyDown(Keys.A); repeat();
            check(player.angle==45 && player.x==100,"chat blocks gameplay keys"); chat(false);
            reset(); PM.curP=1; input.keyDown(Keys.W); input.keyDown(Keys.D); repeat();
            check(player.angle==45 && player.x==100,"other turn blocks controls");
            reset(); input.keyDown(Keys.W); player.force=10; super.pause(); super.resume(); repeat();
            check(player.angle==46 && player.force==0,"focus loss clears held input and force");
            reset(); input.keyDown(Keys.W); chat(true); tick(); chat(false); repeat();
            check(player.angle==46,"chat transition clears held input");
            reset(); chat(true); tick(); check(screen.isChat,"open chat box keeps blocking controls");
            chatOpen.setBoolean(GameScr.tfChat,false); GameScr.tfChat.setText(""); tick();
            check(!screen.isChat,"closing the chat box without text gives controls back");
            input.keyDown(Keys.W); check(player.angle==46,"aim works after an empty chat box"); input.keyUp(Keys.W);
            input.keyDown(Keys.D); tick(); input.keyUp(Keys.D); check(player.x==101,"moving works after an empty chat box");
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
            chat(true); input.keyDown(Keys.R); input.keyUp(Keys.R);
            check(effect.Camera.mode==effect.Camera.FREE_MODE,"chat does not toggle camera"); chat(false);
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
            // "lm x" in the match chat: the LỰC MAX menu without the menu, never sent to the room.
            check(GameScr.applyForceCommand("hello",player)==null && GameScr.applyForceCommand("lm",player)==null
                    && GameScr.applyForceCommand("lm abc",player)==null && GameScr.applyForceCommand("lm 5 6 7",player)==null
                    && GameScr.applyForceCommand("film 5",player)==null,"ordinary chat is not a force command");
            player.maxforce=30; player.maxforce2=30;
            check("Lực max: 25".equals(GameScr.applyForceCommand("lm 25",player)) && player.maxforce==25,"lm 25 sets the max force");
            check(GameScr.applyForceCommand(" LM40 ",player)!=null && player.maxforce==30,"above 30 is kept at 30, any case, no space needed");
            check(GameScr.applyForceCommand("lm 0",player)!=null && player.maxforce==1,"below 1 is kept at 1");
            check(GameScr.applyForceCommand("lm 20 12",player)!=null && player.maxforce==20 && player.maxforce2==30,"second force is only for Chicky and Apache");
            player.gun=8;
            check("Lực max: 20, lực max 2: 12".equals(GameScr.applyForceCommand("lm 20 12",player)) && player.maxforce2==12,"Apache sets both forces");
            check("Lực max: 22, lực max 2: 12".equals(GameScr.applyForceCommand("lm 22",player)),"one number keeps Apache's second force");
            player.gun=0;
            coreLG.TerrainMidlet.myInfo=new model.PlayerInfo(); coreLG.TerrainMidlet.myInfo.IDDB=1; CCanvas.gameScr=screen;
            sent.clear(); GameScr.tfChat.setText("lm 18"); screen.isChat=true; screen.chatWait=0; screen.sendTypedChat();
            check(player.maxforce==18 && sent.isEmpty() && GameScr.tfChat.getText().isEmpty() && !screen.isChat,
                    "typing lm 18 in the chat box sets the force and sends nothing");
            // "g x" / "gs x" in the match chat: the aim above or below the horizontal, the way the player faces.
            short lockBefore=CPlayer.angleLock[player.gun]; CPlayer.angleLock[player.gun]=-45;
            player.look=2;
            check("Góc: 45".equals(GameScr.applyAngleCommand("g 45",player)) && player.angle==45,"g 45 aims 45 degrees up facing right");
            check("Góc: -20".equals(GameScr.applyAngleCommand("gs 20",player)) && player.angle==-20,"gs 20 aims 20 degrees down facing right");
            player.look=0;
            check("Góc: 30".equals(GameScr.applyAngleCommand("G30",player)) && player.angle==150,"g 30 facing left is 150 in world terms, any case, no space");
            check("Góc: -30".equals(GameScr.applyAngleCommand("gs 30",player)) && player.angle==210,"gs 30 facing left aims down to the left");
            check("Góc: -45".equals(GameScr.applyAngleCommand("gs 80",player)),"the character's lowest aim still applies");
            player.look=2;
            check("Góc: 90".equals(GameScr.applyAngleCommand("g 120",player)) && player.angle==90,"at most 90");
            check(GameScr.applyAngleCommand("gg",player)==null && GameScr.applyAngleCommand("g",player)==null
                    && GameScr.applyAngleCommand("go 5",player)==null && GameScr.applyAngleCommand("gsx 5",player)==null,"ordinary chat is not an aim command");
            sent.clear(); GameScr.tfChat.setText("g 33"); screen.isChat=true; screen.chatWait=0; screen.sendTypedChat();
            check(player.angle==33 && sent.isEmpty() && GameScr.tfChat.getText().isEmpty(),"typing g 33 in the chat box aims and sends nothing");
            CPlayer.angleLock[player.gun]=lockBefore; player.angle=45;
            // POW (item 100) and the other items reach the server in practice too (it runs there: Training.useItem).
            sent.clear(); GameScr.trainingMode=true; network.GameService.gI().useItem((byte)100); GameScr.trainingMode=false;
            check(sent.size()==1 && sent.get(0).command==26 && sent.get(0).reader().readByte()==100,"POW in practice is sent to the server");
            sent.clear(); GameScr.tfChat.setText("gg"); screen.isChat=true; screen.chatWait=0; screen.sendTypedChat();
            check(sent.size()==1 && sent.get(0).command==9,"ordinary chat is still sent to the room");
            sent.clear(); network.GameService.gI().login("someone","abc123","2.4.1");
            java.io.DataInputStream login=sent.get(0).reader();
            check(sent.get(0).command==1 && login.readUTF().equals("someone") && login.readUTF().equals("abc123") && login.readUTF().equals("2.4.1")
                    && login.readInt()==com.teamobi.mobiarmy2.GameMidlet.LAN_BUILD,"login sends the LAN build after the version");
            // Apache's set passive "Thợ săn ẩn nấp": message 114 names who is hidden, message 80 shows them again.
            if(effect.Explosion.invisibleEff==null){Image shimmer=new Image(); shimmer.width=24; shimmer.height=32; effect.Explosion.invisibleEff=new mImage(shimmer);}
            CPlayer foe=new CPlayer(2,(byte)1,200,100,false,2,(byte)16,null,100); foe.team=!player.team; // art loaded in this harness
            PM.p=new CPlayer[]{player,foe}; if(GameScr.exs==null) GameScr.exs=new java.util.Vector<>(); // initGame makes it in a match
            int effects=GameScr.exs.size();
            network.MessageHandler.gI().onMessage(status(new int[]{0,1},1));
            check(foe.isInvisible && !player.isInvisible && GameScr.exs.size()==effects+1,"status 114 hides the player it lists, with the shimmer");
            network.MessageHandler.gI().onMessage(status(new int[]{0,1},1));
            check(foe.isInvisible && GameScr.exs.size()==effects+1,"hiding again adds no second shimmer");
            network.MessageHandler.gI().onMessage(status(new int[]{0,1},-1));
            check(foe.isInvisible,"a status without the hidden list (older server) changes nothing");
            network.MessageHandler.gI().onMessage(new network.Message((byte)80,new byte[]{1}));
            check(!foe.isInvisible,"message 80 shows the player again");
            foe.setState((byte)5); network.MessageHandler.gI().onMessage(status(new int[]{0,1},1));
            check(!foe.isInvisible,"a dead player is not hidden");
            // Message 114 then lists who has a set passive and its tier (the others have none).
            foe.setTier=3;
            {java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream(); java.io.DataOutputStream out=new java.io.DataOutputStream(bytes);
             out.writeByte(2); out.writeByte(0); out.writeBoolean(false); out.writeBoolean(false); out.writeByte(1); out.writeBoolean(false); out.writeBoolean(false);
             out.writeByte(0); out.writeByte(1); out.writeByte(0); out.writeByte(6);
             network.MessageHandler.gI().onMessage(new network.Message((byte)114,bytes.toByteArray()));}
            check(player.setTier==6 && foe.setTier==0,"message 114 tells each player's set passive tier");
            // Tarzan's set passive: 120 steps a turn instead of 60.
            byte gunBefore=player.gun;
            player.gun=7;
            check(player.pactTarzan() && player.maxMovePoint()==120,"Tarzan with his set walks 120 steps");
            player.setTier=0;
            check(!player.pactTarzan() && player.maxMovePoint()==60,"without it 60");
            // Rocketer's WorldWar POW: any angle, straight down included; his usual limit otherwise.
            player.gun=4; player.setTier=6; player.look=2;
            short rocketLock=CPlayer.angleLock[4]; CPlayer.angleLock[4]=0;
            player.itemUsed=100; player.angle=-80; player.checkNomarShoot();
            check(player.worldWarPow() && player.angle==-80,"WorldWar POW aims below Rocketer's limit");
            player.itemUsed=-1; player.angle=-80; player.checkNomarShoot();
            check(!player.worldWarPow() && player.angle==0,"his usual limit without POW");
            player.setTier=0; player.itemUsed=100; player.angle=-80; player.checkNomarShoot();
            check(player.angle==0,"POW without the set keeps the limit");
            CPlayer.angleLock[4]=rocketLock; player.gun=gunBefore; player.itemUsed=-1; player.angle=45;
            // POW floats its own icon (picture 38 of item.png), not the one of LAN item 38 "Cơn mưa độc".
            if(effect.Explosion.itemEffImg==null){Image burst=new Image(); burst.width=40; burst.height=40; effect.Explosion.itemEffImg=new mImage(burst);}
            effects=GameScr.exs.size(); player.UseItem(item.Item.ITEM_ANGRY,true,0);
            effect.Explosion powIcon=null;
            for(int i=effects;i<GameScr.exs.size();i++){effect.Explosion e=(effect.Explosion)GameScr.exs.get(i); if(e.type==5) powIcon=e;}
            check(powIcon!=null && powIcon.itemID==item.Item.ITEM_ANGRY && item.Item.POISON_RAIN!=item.Item.ITEM_ANGRY,"POW shows its own icon, not the poison rain's");
            player.itemUsed=-1;
            // Chicky's Easter egg (message 109) comes with whose it is. Hatching: the camera goes to it, then it goes off
            // in the giant mushroom of T-Rex's big rocket (no white flash, no hole); the next messages (the HP it
            // changes) wait until then.
            if(effect.Explosion.teleport==null){Image puff=new Image(); puff.width=32; puff.height=32; effect.Explosion.teleport=new mImage(puff);}
            if(effect.Explosion.imgNuke==null){Image fire=new Image(); fire.width=48; fire.height=62; effect.Explosion.imgNuke=new mImage(fire);}
            GameScr gameBefore=CCanvas.gameScr; CCanvas.gameScr=screen; GameScr.timeBombs.clear();
            network.MessageHandler.gI().onMessage(bomb(0,100,150,100,1));
            model.TimeBomb laid=GameScr.timeBombs.isEmpty()?null:GameScr.timeBombs.get(0);
            check(laid!=null && laid.isEgg() && laid.owner==1 && laid.x==150,"an Easter egg arrives with whose it is");
            network.Session_ME.receiveSynchronized=0; CCanvas.lockNotify=false; GameScr.whiteEffect=false;
            network.MessageHandler.gI().onMessage(bomb(2,100,0,0,-1));
            check(GameScr.timeBombs.contains(laid) && laid.hatchIn==model.TimeBomb.HATCH_FRAMES && !GameScr.whiteEffect,"hatching: the camera goes to the egg first, no white flash");
            check(network.Session_ME.receiveSynchronized==1 && CCanvas.lockNotify && CCanvas.tNotify<0,"what it heals and hurts waits for it to go off");
            int fires=GameScr.exs.size();
            for(int i=0;i<model.TimeBomb.HATCH_FRAMES;i++) laid.update();
            check(!GameScr.timeBombs.contains(laid) && GameScr.exs.size()==fires+7,"then it goes off in the giant mushroom");
            network.MessageHandler.gI().onMessage(bomb(0,101,60,100,-1)); network.Session_ME.receiveSynchronized=0;
            network.MessageHandler.gI().onMessage(bomb(1,101,0,0,-1));
            check(GameScr.timeBombs.size()==1 && GameScr.timeBombs.get(0).hatchIn>0,"an egg told to explode hatches the same way");
            GameScr.timeBombs.clear(); network.Session_ME.receiveSynchronized=0; CCanvas.lockNotify=false; CCanvas.tNotify=0;
            CCanvas.gameScr=gameBefore;
            PM.p=new CPlayer[]{player};
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
