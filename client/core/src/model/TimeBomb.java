package model;

import CLib.mGraphics;
import CLib.mImage;
import coreLG.CCanvas;
import effect.Camera;
import effect.Explosion;
import item.Bullet;
import player.CPlayer;
import player.PM;
import screen.GameScr;

public class TimeBomb {
   public int id;
   public int x;
   public int y;
   public boolean isExplore;
   public boolean isFall;
   public boolean falling;
   int dy;
   /** Whose Chicky's Easter egg this is (index in PM.p), -1 when the server does not say. */
   public int owner = -1;
   /** Frames until the hatching egg goes off (GameScr.hatchEgg), 0 while it is not hatching. */
   public int hatchIn;
   /** Time the camera takes to reach the egg before it goes off. */
   public static final int HATCH_FRAMES = 10;

   public TimeBomb(int id, int x, int y) {
      this.id = id;
      this.x = x;
      this.y = y;
      new Explosion(x, y, (byte)1);
   }

   /**
    * Chicky's Easter eggs (server ids 100 and up) instead of a time bomb: one EGG_W x EGG_H picture per kind of her
    * bullets, top to bottom (art/chicky-eggs), so the egg wears the colours of her skin.
    */
   static mImage eggs;
   static final int EGG_W = 32, EGG_H = 40;
   /** How far aside the egg is drawn while a player stands on it, so the player does not hide it. */
   static final int EGG_ASIDE = 18;

   public boolean isEgg() {
      return this.id >= 100;
   }

   /** One of Granos's grenades (server ids below 0, build 7): his lasers strike it two rounds after his seed. */
   public boolean isGrenade() {
      return this.id < 0;
   }

   /** How high above the ground Granos's grenades appear, falling (the server's SetPassive.GRANOS_DROP). */
   public static final int GRENADE_DROP = 300;
   private static final com.badlogic.gdx.graphics.Color ARMED_RED = new com.badlogic.gdx.graphics.Color(1f, 0.45f, 0.45f, 1f);

   /** Row of eggs.png: the kind of bullet the owner's chicken is (her skin), the classic egg when unknown. */
   int eggKind() {
      CPlayer p = PM.p != null && this.owner >= 0 && this.owner < PM.p.length ? PM.p[this.owner] : null;
      int kind = p != null ? p.bulletKind() : 0;
      return kind > 0 && (kind + 1) * EGG_H <= eggs.image.getHeight() ? kind : 0;
   }

   /** One of Rocketer's late WorldWar bombs (server MapData.LATE_NUKE_FIRST.., build 8): it goes off a round later. */
   public boolean isLateNuke() {
      return this.id >= 70 && this.id < 100;
   }

   public void paint(mGraphics g) {
      if (this.isLateNuke()) {
         if (Bullet.bomb == null) return;
         // The nuclear bomb lies on the ground, blinking red: it will go off.
         boolean blink = CCanvas.gameTick % 20 < 4;
         if (blink) g.tint = ARMED_RED;
         try {
            g.drawRegion(Bullet.bomb, 0, 0, 24, 24, 0, this.x, this.y, mGraphics.HCENTER | mGraphics.BOTTOM, false);
         } finally {
            g.tint = null;
         }
         return;
      }
      if (this.isGrenade()) {
         if (Bullet.grenadeImg == null) return;
         // Blinks red now and then: armed, a laser will come down on it.
         boolean blink = !this.falling && CCanvas.gameTick % 20 < 4;
         if (blink) g.tint = ARMED_RED;
         try {
            g.drawImage(Bullet.grenadeImg, this.x, this.y, mGraphics.HCENTER | mGraphics.BOTTOM, false);
         } finally {
            g.tint = null;
         }
         return;
      }
      if (this.isEgg()) {
         if (eggs == null) eggs = mImage.createImage("/gui/eggs.png");
         int bob = CCanvas.gameTick % 10 < 5 ? 0 : 2;
         int drawX = this.x + (this.underPlayer() ? EGG_ASIDE : 0);
         g.drawRegion(eggs, 0, this.eggKind() * EGG_H, EGG_W, EGG_H, 0, drawX, this.y - bob, mGraphics.HCENTER | mGraphics.BOTTOM, false);
         return;
      }
      int frame = CCanvas.gameTick % 3 == 0 ? 0 : 1;
      g.drawRegion(Explosion.timeBomb, 0, frame * 15, 28, 15, 0, this.x, this.y, mGraphics.HCENTER | mGraphics.BOTTOM, false);
   }

   /** A living, visible player stands on this spot (an invisible one does not move the egg, which would give him away). */
   boolean underPlayer() {
      if (PM.p == null) return false;
      for (CPlayer p : PM.p) {
         if (p != null && p.hp > 0 && !p.isInvisible && Math.abs(p.x - this.x) < 16 && Math.abs(p.y - this.y) < 24) return true;
      }
      return false;
   }

   /** The giant mushroom of fire (as T-Rex's big rocket and the time bomb go off). */
   void mushroom() {
      int[][] array = GameScr.getPointAround(this.x, this.y, 7);

      for(int i = 0; i < 7; ++i) {
         new Explosion(array[0][i], array[1][i], (byte)7);
      }
   }

   public void update() {
      int y1;
      if (this.hatchIn > 0 && --this.hatchIn == 0) {
         this.mushroom();
         GameScr.timeBombs.removeElement(this);
         return;
      }

      if (this.isExplore) {
         Camera.shaking = 2;
         this.mushroom();
         GameScr.timeBombs.removeElement(this);
      }

      int x1;
      if (this.isFall) {
         this.falling = true;

         for(x1 = 0; x1 < 14; ++x1) {
            if (GameScr.mm.isLand(this.x - 7 + x1, this.y)) {
               this.falling = false;
               this.dy = 0;
               break;
            }
         }

         this.isFall = false;
      }

      if (this.falling) {
         x1 = this.x;
         y1 = this.y;
         ++this.dy;
         this.y += this.dy;

         for(int i = 0; i < 14; ++i) {
            if (GameScr.mm.isLand(this.x - 7 + i, this.y)) {
               this.falling = false;
               this.dy = 0;
               int[] p = Bullet.getCollisionPoint(x1, y1, this.x, this.y);
               if (p != null) {
                  this.y = p[1];
               }
               break;
            }
         }
      }

   }
}
