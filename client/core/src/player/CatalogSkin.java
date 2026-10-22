package player;

import CLib.*;
import Equipment.*;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.utils.*;

/** Costume identity and source rectangles come from assets; stats remain server-owned. */
public final class CatalogSkin {
    private static JsonValue catalog;
    private static final java.util.Map<Integer,mImage[]> cache = new java.util.HashMap<Integer,mImage[]>();
    private CatalogSkin() {}
    private static JsonValue find(Equip e) {
        if(e==null)return null;
        if(catalog==null)catalog=new JsonReader().parse(Gdx.files.internal("skins/thor-classes/catalog.json"));
        for(JsonValue v=catalog.child;v!=null;v=v.next)
            if(e.glass==v.getInt("glass")&&e.type==v.getInt("slot")&&e.id==v.getInt("id"))return v;
        return null;
    }
    private static mImage[] load(JsonValue v) {
        int id=v.getInt("id");
        if(cache.containsKey(id))return cache.get(id);
        byte c=(byte)v.getInt("glass");
        Equip head=PlayerEquip.getEquip(c,(byte)1,(short)v.getInt("head"));
        Equip body=PlayerEquip.getEquip(c,(byte)2,(short)v.getInt("body"));
        if(head==null||body==null)throw new IllegalStateException("Missing costume reference "+id);
        Pixmap src=new Pixmap(Gdx.files.internal(v.getString("asset")));
        boolean fixed=v.getBoolean("fixedCanvas",false);
        if(v.getBoolean("removeNavy",false)) {
            Pixmap rgba=new Pixmap(src.getWidth(),src.getHeight(),Pixmap.Format.RGBA8888);
            rgba.setBlending(Pixmap.Blending.None);rgba.drawPixmap(src,0,0);src.dispose();src=rgba;
            removeBackground(src);
        }
        mImage[] result=new mImage[v.get("rects").size];
        try {for(int f=0;f<result.length;f++) {
            int[] r=v.get("rects").get(f).asIntArray();
            int l=r[0]+r[2],t=r[1]+r[3],right=-1,bottom=-1;
            for(int y=r[1];y<r[1]+r[3];y++)for(int x=r[0];x<r[0]+r[2];x++)
                if((src.getPixel(x,y)&255)>=190){l=Math.min(l,x);t=Math.min(t,y);right=Math.max(right,x);bottom=Math.max(bottom,y);}
            if(right<l)throw new IllegalStateException("Empty costume frame "+id+":"+f);
            if(fixed){l=r[0];t=r[1];right=l+r[2]-1;bottom=t+r[3]-1;}
            int height=fixed?v.getInt("nativeHeight"):Math.max(1,Math.round((Math.max(head.dy[f]+head.h[f],body.dy[f]+body.h[f])-Math.min(head.dy[f],body.dy[f]))*v.getFloat("scale",1)));
            int width=fixed?v.getInt("nativeWidth"):Math.max(1,Math.round((float)(right-l+1)*height/(bottom-t+1)));
            Pixmap p=new Pixmap(width,height,Pixmap.Format.RGBA8888);
            try {p.setBlending(Pixmap.Blending.None);p.setFilter(Pixmap.Filter.NearestNeighbour);
                p.drawPixmap(src,l,t,right-l+1,bottom-t+1,0,0,width,height);
                for(int y=0;y<height;y++)for(int x=0;x<width;x++)if((p.getPixel(x,y)&255)<190)p.drawPixel(x,y,0);
                CLib.Image im=new CLib.Image();im.texture=new Texture(p);im.texture.setFilter(Texture.TextureFilter.Nearest,Texture.TextureFilter.Nearest);im.width=width;im.height=height;result[f]=new mImage(im);
            }finally{p.dispose();}
        }}finally{src.dispose();}
        cache.put(id,result);return result;
    }
    public static boolean paint(mGraphics g,PlayerEquip outfit,int look,int frame,int x,int y) {
        if(outfit==null||frame<0)return false;
        JsonValue v=find(outfit.equips[1]);if(v==null)return false;
        if(frame>=v.get("rects").size)return false;
        mImage im=load(v)[frame];byte c=(byte)v.getInt("glass");
        if(v.getBoolean("fixedCanvas",false)) {
            if(v.getBoolean("drawWeapon",true))outfit.paintSung(x,y,look,frame,g);
            g.drawRegion(im,0,0,im.image.width,im.image.height,look==v.getInt("sourceLook",0)?0:2,x,y,mGraphics.BOTTOM|mGraphics.HCENTER,false);
            return true;
        }
        Equip head=PlayerEquip.getEquip(c,(byte)1,(short)v.getInt("head")),body=PlayerEquip.getEquip(c,(byte)2,(short)v.getInt("body"));
        int center=18-v.getInt("nativeWidth")/2+head.dx[frame]+head.w[frame]/2;
        int bottom=40-v.getInt("nativeHeight")+Math.max(head.dy[frame]+head.h[frame],body.dy[frame]+body.h[frame]);
        outfit.paintSung(x,y,look,frame,g);
        g.drawRegion(im,0,0,im.image.width,im.image.height,look==2?0:2,x+(look==0?center:-center),y+bottom,mGraphics.BOTTOM|mGraphics.HCENTER,false);
        return true;
    }
    /** Remove only the source sheet's navy backdrop; retain black outlines and blue lightning. */
    private static void removeBackground(Pixmap p) {
        p.setBlending(Pixmap.Blending.None);
        for(int y=0;y<p.getHeight();y++)for(int x=0;x<p.getWidth();x++){
            int rgba=p.getPixel(x,y),r=rgba>>>24,g=(rgba>>>16)&255,b=(rgba>>>8)&255;
            if(r<32&&g<55&&b<90&&b>r+12)p.drawPixel(x,y,0);
        }
    }
    public static boolean icon(mGraphics g,Equip e,int x,int y,boolean clip) {
        JsonValue v=find(e);if(v==null)return false;mImage im=load(v)[0];
        g.drawRegion(im,0,0,im.image.width,im.image.height,0,x+8,y+16,mGraphics.BOTTOM|mGraphics.HCENTER,clip);return true;
    }
}
