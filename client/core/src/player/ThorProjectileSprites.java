package player;

import CLib.Image;
import CLib.mGraphics;
import CLib.mImage;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import java.util.HashMap;
import java.util.Map;

/** Extracts approved artwork at load time; body pivots stay on the authoritative bullet position. */
final class ThorProjectileSprites {
    private static final Map<String,mImage[]> cache=new HashMap<String,mImage[]>();
    private ThorProjectileSprites() {}
    static boolean background(int rgba) {
        int r=rgba>>>24,g=(rgba>>>16)&255,b=(rgba>>>8)&255;
        return r<35&&g<60&&b<85&&b>r+7&&g>r+3;
    }
    static Pixmap pixels(ThorProjectiles.Profile p,boolean pow) {
        int[] rect=pow?p.powRect:p.normalRect,pivot=pow?p.powPivot:p.normalPivot;
        int width=pow?p.powWidth:p.normalWidth;
        Pixmap source=new Pixmap(Gdx.files.internal(p.sprite));
        Pixmap cut=new Pixmap(rect[2],rect[3],Pixmap.Format.RGBA8888);
        try {
            if(rect[0]<0||rect[1]<0||rect[0]+rect[2]>source.getWidth()||rect[1]+rect[3]>source.getHeight())
                throw new IllegalArgumentException("Thor projectile crop outside atlas: "+p.glass);
            cut.setBlending(Pixmap.Blending.None);
            cut.drawPixmap(source,rect[0],rect[1],rect[2],rect[3],0,0,rect[2],rect[3]);
            for(int y=0;y<cut.getHeight();y++)for(int x=0;x<cut.getWidth();x++)
                if(background(cut.getPixel(x,y)))cut.drawPixel(x,y,0);
            double scale=(double)width/rect[2];
            int h=Math.max(1,(int)Math.round(rect[3]*scale));
            int px=(int)Math.round(pivot[0]*scale),py=(int)Math.round(pivot[1]*scale);
            int half=(int)Math.ceil(Math.hypot(Math.max(px,width-px),Math.max(py,h-py)))+2;
            Pixmap result=new Pixmap(half*2,half*2,Pixmap.Format.RGBA8888);
            result.setBlending(Pixmap.Blending.None);result.setFilter(Pixmap.Filter.NearestNeighbour);
            result.drawPixmap(cut,0,0,rect[2],rect[3],half-px,half-py,width,h);
            return result;
        } finally {cut.dispose();source.dispose();}
    }
    static Pixmap rotated(Pixmap source,int frame) {
        int size=source.getWidth(),center=size/2;
        Pixmap result=new Pixmap(size,size,Pixmap.Format.RGBA8888);result.setBlending(Pixmap.Blending.None);
        double angle=frame*Math.PI/8,c=Math.cos(angle),s=Math.sin(angle);
        for(int y=0;y<size;y++)for(int x=0;x<size;x++) {
            int sx=center+(int)Math.round((x-center)*c+(y-center)*s);
            int sy=center+(int)Math.round(-(x-center)*s+(y-center)*c);
            if(sx>=0&&sy>=0&&sx<size&&sy<size)result.drawPixel(x,y,source.getPixel(sx,sy));
        }
        return result;
    }
    private static mImage sprite(ThorProjectiles.Profile p,boolean pow,int frame) {
        String key=p.glass+":"+pow;
        mImage[] existing=cache.get(key);if(existing!=null)return existing[frame];
        Pixmap pixels=pixels(p,pow);
        try {
            mImage[] loaded=new mImage[16];
            for(int i=0;i<loaded.length;i++) {
                Pixmap turn=rotated(pixels,i);
                try {
                    Image image=new Image();image.texture=new Texture(turn);
                    image.texture.setFilter(Texture.TextureFilter.Nearest,Texture.TextureFilter.Nearest);
                    image.width=turn.getWidth();image.height=turn.getHeight();loaded[i]=new mImage(image);
                }finally{turn.dispose();}
            }
            cache.put(key,loaded);return loaded[frame];
        } finally {pixels.dispose();}
    }
    static void paint(mGraphics g,ThorProjectiles.Profile p,boolean pow,int x,int y,int lastX,int lastY,int phase) {
        int degrees=(int)Math.round(Math.toDegrees(Math.atan2(y-lastY,x-lastX)));
        if(p.spin)degrees=phase*14;
        int frame=((int)Math.round(degrees/22.5)%16+16)%16;
        // Pre-rotated native pixels avoid mGraphics' zoom-dependent rotation-origin offset.
        g.drawImage(sprite(p,pow,frame),x,y,mGraphics.HCENTER|mGraphics.VCENTER,false);
    }
}
