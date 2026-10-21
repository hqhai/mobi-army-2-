package player;
import CLib.*;
import Equipment.*;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
/** Owned Magenta VIP. Native gun stays separate from the new tilted-head artwork. */
public final class PhotonSkin {
 public static final short ID=30001;
 private static mImage[] poses;
 private PhotonSkin(){}
 public static boolean is(Equip e){return e!=null && e.glass==9 && e.type==1 && e.id==ID;}
 public static boolean isEquipped(PlayerEquip e){return e!=null && is(e.equips[1]);}
 private static void load(){
  if(poses!=null)return;
  Pixmap src=new Pixmap(Gdx.files.internal("skins/magenta-photon/vip-poses-v5.png"));
  mImage[] result=new mImage[6];
  try{for(int i=0;i<6;i++){
   int sx=(i%3)*512,sy=(i/3)*512,l=sx+512,t=sy+512,r=-1,b=-1;
   for(int y=sy;y<sy+512;y++)for(int x=sx;x<sx+512;x++)if((src.getPixel(x,y)&255)>=190){l=Math.min(l,x);t=Math.min(t,y);r=Math.max(r,x);b=Math.max(b,y);}
   if(r<l)throw new IllegalStateException("Empty Photon frame");
   Equip head=PlayerEquip.getEquip((byte)9,(byte)1,(short)19),body=PlayerEquip.getEquip((byte)9,(byte)2,(short)20);
   int top=Math.min(head.dy[i],body.dy[i]);int bottom=Math.max(head.dy[i]+head.h[i],body.dy[i]+body.h[i]);
   int height=bottom-top,width=Math.max(1,Math.round((float)(r-l+1)*height/(b-t+1)));
   Pixmap p=new Pixmap(width,height,Pixmap.Format.RGBA8888);
   try{p.setBlending(Pixmap.Blending.None);p.setFilter(Pixmap.Filter.NearestNeighbour);p.drawPixmap(src,l,t,r-l+1,b-t+1,0,0,width,height);
    for(int y=0;y<height;y++)for(int x=0;x<width;x++)if((p.getPixel(x,y)&255)<190)p.drawPixel(x,y,0);
    CLib.Image im=new CLib.Image();im.texture=new Texture(p);im.texture.setFilter(Texture.TextureFilter.Nearest,Texture.TextureFilter.Nearest);im.width=width;im.height=height;result[i]=new mImage(im);
   }finally{p.dispose();}
  }poses=result;}finally{src.dispose();}
 }
 public static boolean paint(mGraphics g,PlayerEquip equip,int look,int frame,int x,int y){
  if(!isEquipped(equip)||frame<0||frame>=6)return false;
  load();mImage im=poses[frame];
  Equip head=PlayerEquip.getEquip((byte)9,(byte)1,(short)19),body=PlayerEquip.getEquip((byte)9,(byte)2,(short)20);
  int bottom=16+Math.max(head.dy[frame]+head.h[frame],body.dy[frame]+body.h[frame]);
  int flip=frame<2?(look==0?2:0):look;
  equip.paintSung(x,y,look,frame,g);
  int center=6+head.dx[frame]+head.w[frame]/2;
  g.drawRegion(im,0,0,im.image.width,im.image.height,flip,x+(look==0?center:-center),y+bottom,mGraphics.BOTTOM|mGraphics.HCENTER,false);
  return true;
 }
 // No outfit means no VIP appearance. Kept for the native fallback call site.
 public static boolean paint(mGraphics g,int gun,int look,int frame,int x,int y){return false;}
 public static boolean icon(mGraphics g,Equip equip,int x,int y,boolean clip){
  if(!is(equip))return false;load();
  mImage im=poses[0];g.drawRegion(im,0,0,im.image.width,im.image.height,0,x+8,y+16,mGraphics.BOTTOM|mGraphics.HCENTER,clip);return true;
 }
}
