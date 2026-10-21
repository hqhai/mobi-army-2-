package effect;

import CLib.mGraphics;
import player.PM;
import player.ThorSkin;

/** Cosmetic only: follows authoritative projectile endpoints, never changes physics. */
public final class HeroEffects {
    private HeroEffects() {}

    public static boolean thor(int shooter) {
        return ThorSkin.isShooter(shooter);
    }

    public static boolean photon(int shooter) {
        return PM.p != null && shooter >= 0 && shooter < PM.p.length
                && PM.p[shooter] != null && PM.p[shooter].gun == 9;
    }

    public static int phase() {
        return (int) ((System.nanoTime() / 65000000L) % 120);
    }

    private static void line(mGraphics g, int color, int x, int y, int xx, int yy) {
        g.setColor(color);
        g.drawLine(x, y, xx, yy, false);
    }

    public static void ring(mGraphics g, int x, int y, int radius, int color, int phase) {
        for (int i = 0; i < 12; i++) {
            double a = (i * 30 + phase * 3) * Math.PI / 180;
            double b = a + Math.PI / 6;
            line(g, color, x + (int)(Math.cos(a)*radius), y + (int)(Math.sin(a)*radius),
                    x + (int)(Math.cos(b)*radius), y + (int)(Math.sin(b)*radius));
        }
    }

    public static void flare(mGraphics g, int x, int y, int radius, boolean thunder, int phase) {
        flareColor(g,x,y,radius,thunder ? 0x63cfff : 0xffc340,phase);
    }

    private static void flareColor(mGraphics g,int x,int y,int radius,int color,int phase) {
        ring(g, x, y, radius / 2, color, phase);
        for (int i = 0; i < 6; i++) {
            double a = (i * 60 + phase * 11) * Math.PI / 180;
            int dx = (int)(Math.cos(a)*radius), dy = (int)(Math.sin(a)*radius);
            line(g, color, x + dx / 3, y + dy / 3, x + dx, y + dy);
        }
        line(g, 0xffffff, x-2, y, x+2, y);
        line(g, 0xffffff, x, y-2, x, y+2);
    }

    public static void aura(mGraphics g, int x, int y, boolean thunder, boolean activated, int phase) {
        int radius = activated ? 26 : 20;
        int color = thunder ? 0x63cfff : 0xffc340;
        ring(g, x, y-18, radius + phase%3, color, phase);
        for (int i = 0; i < (activated ? 6 : 3); i++) {
            int xx = x - radius + (i*17 + phase*3) % (radius*2);
            int yy = y - (i*11 + phase*2)%43;
            if (thunder) {
                line(g, color, xx, yy, xx+4, yy-7);
                line(g, 0xe9faff, xx+4, yy-7, xx-2, yy-11);
                line(g, color, xx-2, yy-11, xx+2, yy-17);
            } else {
                line(g, color, xx, yy, xx, yy-5);
                line(g, 0xfff5ce, xx-2, yy-3, xx+2, yy-3);
            }
        }
    }

    public static void beam(mGraphics g, int x, int y, int xx, int yy, boolean pow,
                            boolean lowDetail, int phase) {
        int dx = xx-x, dy = yy-y;
        double length = Math.sqrt((double)dx*dx+(double)dy*dy);
        if (length < 1) return;
        int width = lowDetail ? 1 : pow ? 7 : 3;
        for (int i = -width; i <= width; i++) {
            int ox = (int)Math.round(-dy/length*i), oy = (int)Math.round(dx/length*i);
            int color = Math.abs(i) == 0 ? 0xffffff
                    : Math.abs(i) < width ? 0xffd34e : 0xd68b18;
            line(g, color, x+ox, y+oy, xx+ox, yy+oy);
        }
        if (!lowDetail) {
            flareColor(g, xx, yy, pow ? 18 : 9, 0xffdf68, phase);
            // Zigzag lightning on both sides, clipped to the travelled beam segment.
            int segments=Math.max(1,Math.min(24,(int)(length/12)));
            for(int side=-1;side<=1;side+=2) {
                int lastX=x,lastY=y;
                for(int i=1;i<=segments;i++) {
                    double t=(double)i/segments;
                    int jitter=((i*13+phase*7)%9)-4;
                    double offset=i==segments?0:side*(width+3+(pow?4:0))+jitter;
                    int px=x+(int)(dx*t-dy/length*offset);
                    int py=y+(int)(dy*t+dx/length*offset);
                    line(g,0xffc52e,lastX,lastY,px,py);
                    line(g,0xfff4ba,lastX,lastY-1,px,py-1);
                    if(pow && i%3==0 && i<segments) {
                        int bx=px+(int)(-dy/length*side*8);
                        int by=py+(int)(dx/length*side*8);
                        line(g,0xffe783,px,py,bx,by);
                    }
                    lastX=px;lastY=py;
                }
            }
        }
    }

    public static void hammer(mGraphics g, int x, int y, int lastX, int lastY, int phase) {
        int dx=x-lastX, dy=y-lastY;
        double length=Math.sqrt((double)dx*dx+(double)dy*dy);
        if (length>0) {
            int tx=x-(int)(dx/length*42), ty=y-(int)(dy/length*42);
            line(g, 0x248bc9, tx, ty, x, y);
            line(g, 0x63cfff, tx, ty, (tx+x)/2+3, (ty+y)/2-4);
            line(g, 0xe9faff, (tx+x)/2+3, (ty+y)/2-4, x, y);
        }
        // Broken, branching arcs hug the hammer; no circular aura around the player.
        for (int i=0;i<7;i++) {
            double a=(i*360.0/7+phase*9)*Math.PI/180;
            int radius=23+(phase+i*3)%7;
            int ax=x+(int)(Math.cos(a)*radius), ay=y+(int)(Math.sin(a)*radius);
            int bx=x+(int)(Math.cos(a+.3)*(radius-7)), by=y+(int)(Math.sin(a+.3)*(radius-7));
            int cx=x+(int)(Math.cos(a+.6)*(radius+3)), cy=y+(int)(Math.sin(a+.6)*(radius+3));
            line(g,0x248bc9,ax+1,ay+1,bx+1,by+1);
            line(g,0x63cfff,ax,ay,bx,by);
            line(g,0xe9faff,bx,by,cx,cy);
            line(g,0x63cfff,bx,by,bx+(ax-x)/3,by-6);
        }
    }
}
