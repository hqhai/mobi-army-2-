package player;

import effect.HeroEffects;

import CLib.mGraphics;
import Equipment.Equip;
import Equipment.PlayerEquip;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import player.PM;
import java.util.ArrayList;
import java.util.Iterator;

/** Client cosmetics only. Identity, allowed projectile types and artwork live in JSON. */
public final class ThorProjectiles {
    private ThorProjectiles() {}
    private static Profile[] profiles;
    public static final class Profile {
        public final int glass, slot, id, color, radius, trail;
        public final int[] types, outline;
        public final float powScale;
        public final boolean nativeHammer, beam, spin;
        public final String sprite;
        public final int[] normalRect,powRect,normalPivot,powPivot;
        public final int normalWidth,powWidth;
        Profile(JsonValue v) {
            glass=v.getInt("glass"); slot=v.getInt("slot"); id=v.getInt("id");
            color=Integer.parseInt(v.getString("color"),16);
            radius=v.getInt("radius"); trail=v.getInt("trail");
            powScale=v.getFloat("powScale"); types=v.get("types").asIntArray();
            outline=v.get("outline").asIntArray();
            nativeHammer=v.getBoolean("nativeHammer",false);
            beam=v.getBoolean("beam",false); spin=v.getBoolean("spin",false);
            sprite=v.getString("sprite",null);
            normalRect=sprite==null?null:v.get("normalRect").asIntArray();
            powRect=sprite==null?null:v.get("powRect").asIntArray();
            normalPivot=sprite==null?null:v.get("normalPivot").asIntArray();
            powPivot=sprite==null?null:v.get("powPivot").asIntArray();
            normalWidth=v.getInt("normalWidth",0);powWidth=v.getInt("powWidth",0);
        }
        public boolean accepts(int type) { for(int t:types)if(t==type)return true;return false; }
    }
    public static Profile[] parse(String json) {
        JsonValue root=new JsonReader().parse(json);Profile[] result=new Profile[root.size];int i=0;
        for(JsonValue v=root.child;v!=null;v=v.next)result[i++]=new Profile(v);
        return result;
    }
    private static Profile[] catalog() {
        if(profiles==null)profiles=parse(Gdx.files.internal("skins/thor-classes/projectiles.json").readString("UTF-8"));
        return profiles;
    }
    public static Profile resolve(Profile[] catalog, int glass, PlayerEquip outfit, int type) {
        if(outfit==null||outfit.equips==null)return null;
        for(Profile p:catalog) {
            if(p.glass!=glass||!p.accepts(type)||p.slot<0||p.slot>=outfit.equips.length)continue;
            Equip e=outfit.equips[p.slot];
            if(e!=null&&e.glass==p.glass&&e.type==p.slot&&e.id==p.id)return p;
        }
        return null;
    }
    public static Profile forShot(int shooter,int type) {
        if(PM.p==null||shooter<0||shooter>=PM.p.length||PM.p[shooter]==null)return null;
        return resolve(catalog(),PM.p[shooter].gun,PM.p[shooter].equip,type);
    }
    private static void line(mGraphics g,int color,int x,int y,int xx,int yy) {
        g.setColor(color);g.drawLine(x,y,xx,yy,false);
    }
    private static void bolt(mGraphics g,int color,int x,int y,int xx,int yy,int phase,int amplitude) {
        int dx=xx-x,dy=yy-y;double length=Math.sqrt((double)dx*dx+(double)dy*dy);
        if(length<1)return;
        int count=Math.max(1,Math.min(24,(int)length/7)),px=x,py=y;
        for(int i=1;i<=count;i++) {
            double t=(double)i/count,j=i==count?0:((phase+i*11)%7-3)*amplitude/3.0;
            int nx=x+(int)(dx*t-dy/length*j),ny=y+(int)(dy*t+dx/length*j);
            line(g,color,px,py,nx,ny);px=nx;py=ny;
        }
    }
    /** Returns true only when this renderer replaces the native projectile sprite. */
    public static boolean projectile(mGraphics g,Profile p,boolean pow,boolean low,int x,int y,int lastX,int lastY,int phase) {
        if(p==null||p.beam)return false;
        if(p.sprite!=null) {
            ThorProjectileSprites.paint(g,p,pow,x,y,lastX,lastY,phase);
            return true;
        }
        double angle=Math.atan2(y-lastY,x-lastX),scale=pow?p.powScale:1;
        int length=low?8:(int)(p.trail*scale);
        int tx=x-(int)(Math.cos(angle)*length),ty=y-(int)(Math.sin(angle)*length);
        bolt(g,p.color,tx,ty,x,y,phase,low?1:pow?5:2);
        if(!low) {
            int arcs=pow?7:3,r=(int)(p.radius*scale);
            for(int i=0;i<arcs;i++) {
                double a=(phase*9+i*360.0/arcs)*Math.PI/180;
                int ax=x+(int)(Math.cos(a)*r),ay=y+(int)(Math.sin(a)*r);
                int inner=r-4-(phase+i*3)%5;
                int bx=x+(int)(Math.cos(a+.3)*inner),by=y+(int)(Math.sin(a+.3)*inner);
                int cx=x+(int)(Math.cos(a+.7)*(r+2)),cy=y+(int)(Math.sin(a+.7)*(r+2));
                line(g,p.color,ax,ay,bx,by);
                line(g,0xe9faff,bx,by,cx,cy);
                if(pow)line(g,p.color,bx,by,bx+(ax-x)/3,by-4);
            }
        }
        if(p.nativeHammer)return false; // Preserve approved rotating Apache hammer artwork.
        if(p.spin)angle=phase*.24;
        double c=Math.cos(angle),s=Math.sin(angle);
        // Fill the small native-sized silhouette before its bevel and rune core.
        int[] hits=new int[p.outline.length/2];
        for(int row=-10;row<=10;row++) {
            int n=0;
            for(int i=0;i<p.outline.length-2;i+=2) {
                int ya=p.outline[i+1],yb=p.outline[i+3];
                if((ya<=row&&yb>row)||(yb<=row&&ya>row))
                    hits[n++]=(int)Math.round(p.outline[i]+(double)(row-ya)*(p.outline[i+2]-p.outline[i])/(yb-ya));
            }
            java.util.Arrays.sort(hits,0,n);
            for(int i=0;i+1<n;i+=2)line(g,row<0?0x9daebf:0x526a83,
                    x+(int)Math.round((hits[i]*c-row*s)*scale),y+(int)Math.round((hits[i]*s+row*c)*scale),
                    x+(int)Math.round((hits[i+1]*c-row*s)*scale),y+(int)Math.round((hits[i+1]*s+row*c)*scale));
        }
        for(int i=0;i<p.outline.length-2;i+=2) {
            int ax=x+(int)Math.round((p.outline[i]*c-p.outline[i+1]*s)*scale);
            int ay=y+(int)Math.round((p.outline[i]*s+p.outline[i+1]*c)*scale);
            int bx=x+(int)Math.round((p.outline[i+2]*c-p.outline[i+3]*s)*scale);
            int by=y+(int)Math.round((p.outline[i+2]*s+p.outline[i+3]*c)*scale);
            line(g,0x172a3b,ax+1,ay+1,bx+1,by+1);
            line(g,0xcfe5f2,ax,ay,bx,by);
        }
        line(g,p.color,x-2,y,x+2,y);line(g,0xffffff,x,y-2,x,y+2);
        return true;
    }
    public static void beam(mGraphics g,Profile p,boolean pow,boolean low,int x,int y,int xx,int yy,int phase) {
        int dx=xx-x,dy=yy-y;double length=Math.sqrt((double)dx*dx+(double)dy*dy);
        if(length<1)return;
        int width=low?1:pow?5:2;
        for(int i=-width;i<=width;i++) {
            int ox=(int)Math.round(-dy/length*i),oy=(int)Math.round(dx/length*i);
            line(g,i==0?0xffffff:p.color,x+ox,y+oy,xx+ox,yy+oy);
        }
        if(!low){bolt(g,0xe9faff,x,y,xx,yy,phase,pow?12:6);bolt(g,p.color,x,y,xx,yy,phase+3,pow?16:8);}
    }
    /** Bounded, per-battle pool; expired effects cannot persist into another BM instance. */
    public static final class Impacts {
        private final ArrayList<Impact> items=new ArrayList<Impact>();
        public void add(Profile p,boolean pow,int x,int y) {
            if(p==null)return;
            if(items.size()>=64)items.remove(0);
            items.add(new Impact(p,pow,x,y,System.nanoTime()));
        }
        public void paint(mGraphics g,boolean low) {
            long now=System.nanoTime();
            for(Iterator<Impact> it=items.iterator();it.hasNext();) {
                Impact e=it.next();double age=(now-e.start)/1000000.0;
                if(age>420){it.remove();continue;}
                int r=(int)(3+age/420*(e.pow?26:13));
                HeroEffects.ring(g,e.x,e.y,r,e.p.color,0);
                if(!low)for(int i=0;i<(e.pow?8:4);i++) {
                    double a=i*Math.PI*2/(e.pow?8:4);
                    bolt(g,e.p.color,e.x,e.y,e.x+(int)(Math.cos(a)*r),e.y+(int)(Math.sin(a)*r),(int)age/50,3);
                }
            }
        }
    }
    private static final class Impact {
        final Profile p;final boolean pow;final int x,y;final long start;
        Impact(Profile p,boolean pow,int x,int y,long start){this.p=p;this.pow=pow;this.x=x;this.y=y;this.start=start;}
    }
}
