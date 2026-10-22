package player;

import Equipment.*;
import CLib.mGraphics;
import java.nio.file.*;

/** No GL/DB required: equipment identity, item isolation, reconnect and rendering bounds. */
public final class ThorProjectilesCheck {
    static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    static PlayerEquip outfit(ThorProjectiles.Profile p) {
        PlayerEquip outfit=new PlayerEquip();Equip e=new Equip();
        e.glass=(byte)p.glass;e.type=(byte)p.slot;e.id=(short)p.id;outfit.equips[p.slot]=e;return outfit;
    }
    static final class Recorder extends mGraphics {
        int lines,minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE;
        public void setColor(int color){}
        public void drawLine(int x,int y,int xx,int yy,boolean clip){lines++;minX=Math.min(minX,Math.min(x,xx));maxX=Math.max(maxX,Math.max(x,xx));minY=Math.min(minY,Math.min(y,yy));maxY=Math.max(maxY,Math.max(y,yy));}
    }
    public static void main(String[] args)throws Exception {
        ThorProjectiles.Profile[] all=ThorProjectiles.parse(new String(Files.readAllBytes(Paths.get(args[0])),"UTF-8"));
        int[][] expected={{0},{1},{2},{9},{10},{11},{19,20},{21},{17,18},{49}};
        check(all.length==10,"ten classes");
        for(int i=0;i<10;i++) {
            ThorProjectiles.Profile p=all[i];PlayerEquip equipped=outfit(p);
            check(p.glass==i&&p.id==(i==8?30000:30100+i),"catalog identity "+i);
            for(int type=0;type<60;type++) {
                boolean allowed=false;for(int t:expected[i])if(t==type)allowed=true;
                check((ThorProjectiles.resolve(all,i,equipped,type)!=null)==allowed,"class/projectile isolation "+i+":"+type);
            }
            int type=expected[i][0];
            check(ThorProjectiles.resolve(all,(i+1)%10,equipped,type)==null,"wrong class");
            check(ThorProjectiles.resolve(all,i,null,type)==null,"no outfit");
            check(ThorProjectiles.resolve(all,i,new PlayerEquip(),type)==null,"unequipped");
            equipped.equips[p.slot].id--;
            check(ThorProjectiles.resolve(all,i,equipped,type)==null,"different VIP");
            equipped=outfit(p);equipped.equips[p.slot].type=0;
            check(ThorProjectiles.resolve(all,i,equipped,type)==null,"wrong slot metadata");
            check(ThorProjectiles.resolve(all,i,outfit(p),type)==p,"reconstructed equipment after reconnect");
            if(p.sprite!=null) {
                check(i<8,"only eight approved classes use new atlas");
                check(p.normalRect.length==4&&p.powRect.length==4,"two atlas regions");
                check(p.normalPivot[0]>0&&p.normalPivot[0]<p.normalRect[2]&&p.normalPivot[1]>0&&p.normalPivot[1]<p.normalRect[3],"normal pivot inside art");
                check(p.powPivot[0]>0&&p.powPivot[0]<p.powRect[2]&&p.powPivot[1]>0&&p.powPivot[1]<p.powRect[3],"POW pivot inside art");
                check(p.powWidth>p.normalWidth&&p.powWidth<=64,"bounded POW size");
                continue; // Pixel/GL checks exercise these sprites in ThorProjectilesRenderCheck.
            }
            for(boolean pow:new boolean[]{false,true})for(boolean low:new boolean[]{false,true})for(int direction:new int[]{-1,1}) {
                Recorder g=new Recorder();
                if(p.beam){ThorProjectiles.beam(g,p,pow,low,100,100,100+direction*80,140,17);check(g.minX>=18&&g.maxX<=183,"beam stays on segment");}
                else {boolean replaced=ThorProjectiles.projectile(g,p,pow,low,100,100,100-direction*2,100,17);check(replaced!=p.nativeHammer,"Apache keeps sprite; other bullets replaced");}
                check(g.lines>0&&g.lines<400,"bounded renderer work");
                check(g.minX>=0&&g.maxX<=200&&g.minY>=0&&g.maxY<=200,"bounded visual geometry");
            }
            Recorder normal=new Recorder(),pow=new Recorder();
            if(p.beam){ThorProjectiles.beam(normal,p,false,false,0,0,60,0,2);ThorProjectiles.beam(pow,p,true,false,0,0,60,0,2);}
            else {ThorProjectiles.projectile(normal,p,false,false,100,100,99,100,2);ThorProjectiles.projectile(pow,p,true,false,100,100,99,100,2);}
            check(pow.maxY-pow.minY>normal.maxY-normal.minY,"POW visibly larger "+i);
        }
        Recorder empty=new Recorder();check(!ThorProjectiles.projectile(empty,null,false,false,0,0,0,0,0)&&empty.lines==0,"ordinary equipment leaves native rendering untouched");
        ThorProjectiles.Impacts impacts=new ThorProjectiles.Impacts();impacts.add(null,true,0,0);impacts.paint(empty,false);check(empty.lines==0,"no unowned impact");
        for(int i=0;i<100;i++)impacts.add(all[0],true,30,30);
        java.lang.reflect.Field items=ThorProjectiles.Impacts.class.getDeclaredField("items");items.setAccessible(true);
        check(((java.util.List<?>)items.get(impacts)).size()==64,"impact pool capped");
        check(((java.util.List<?>)items.get(new ThorProjectiles.Impacts())).isEmpty(),"new battle has no previous impacts");
        Thread.sleep(450);impacts.paint(new Recorder(),false);check(((java.util.List<?>)items.get(impacts)).isEmpty(),"impact expiry");
        System.out.println("THOR PROJECTILES PASSED: "+checks+" checks");
    }
}
