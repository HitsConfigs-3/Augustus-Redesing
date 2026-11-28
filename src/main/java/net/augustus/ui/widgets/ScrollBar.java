package net.augustus.ui.widgets;

import net.augustus.Augustus;
import net.augustus.ui.TabbedMenuScreen;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.MathHelper;

public class ScrollBar {
   private double dragOffsetY;
   public double rightX;
   private double scrollSpeed;
   public double viewportHeight;
   private double thumbHeight;
   public final double itemHeight;
   private long lastUpdateNanos;
   private double pendingScrollDelta;
   public double leftX;
   public final int contentPadding;
   public int bottomY;
   private boolean dragging;
   public double thumbTopY;
   public int topY;
   public int itemCount;
   public double scrollOffset;
   private float hoverAnim;

   public void clampScrollOffset() {
      this.scrollOffset = MathHelper.clamp_double(this.scrollOffset, Math.min(0.0, -((double)this.itemCount * this.itemHeight - (double)(this.bottomY - this.topY))), 0.0);
   }

   public void recalculateThumbMetrics() {
      double d = (double)this.itemCount * this.itemHeight + 0.0;
      double d2 = (double)(this.bottomY - this.topY);
      if (d2 < d) {
         double d3 = d2 / d;
         double d4 = d3 * d2;
         this.thumbTopY = -(this.scrollOffset / d3 * d3 * d3) + (double)this.topY;
         this.thumbHeight = d4;
         this.viewportHeight = d2;
      }
   }

   public void resetDefaultBounds() {
      this.setBounds(new ScaledResolution(Minecraft.getMinecraft()).getScaledWidth() / 2 + 150, (byte)40, new ScaledResolution(Minecraft.getMinecraft()).getScaledWidth() / 2 + 156, new ScaledResolution(Minecraft.getMinecraft()).getScaledHeight() - 40);
   }

   public void setItemCount(int n) {
      this.itemCount = n;
   }

   public boolean isPointOverThumb(double d, double d2) {
      return d < this.rightX && d >= this.leftX && d2 >= this.thumbTopY && d2 < this.thumbTopY + this.thumbHeight;
   }

   public boolean fitsWithoutScrolling() {
      return this.itemCount == 0 ? true : (double)(this.bottomY - this.topY) >= (double)this.itemCount * this.itemHeight + 0.0;
   }

   public void handleMouseDrag(double d) {
      this.recalculateThumbMetrics();
      double d2 = this.viewportHeight / ((double)this.itemCount * this.itemHeight + 0.0);
      double d3 = (double)((int)(-d / d2));
      if (this.dragging) {
         this.scrollOffset = d3 - this.dragOffsetY;
      }

      this.clampScrollOffset();
   }

   public void updateAndDraw() {
      long l = System.nanoTime() - this.lastUpdateNanos;
      double pendingScrollDelta2 = this.pendingScrollDelta;
      pendingScrollDelta2 = MathHelper.clamp_double(pendingScrollDelta2, -this.scrollSpeed * ((double)l / 1000000.0), this.scrollSpeed * ((double)l / 1000000.0));
      this.lastUpdateNanos = System.nanoTime();
      this.pendingScrollDelta -= pendingScrollDelta2;
      this.scrollOffset += pendingScrollDelta2;
      double scrollOffset2 = this.scrollOffset;
      this.clampScrollOffset();
      if (scrollOffset2 != this.scrollOffset) {
         this.pendingScrollDelta = 0.0;
      }

      if (!this.fitsWithoutScrolling()) {
         this.recalculateThumbMetrics();
         float trackL = (float)this.leftX;
         float trackR = (float)this.rightX;
         float trackT = (float)this.topY;
         float trackB = (float)this.bottomY;
         float trackH = Math.max(1.0F, trackB - trackT);
         float thumbT = Math.max(trackT, (float)this.thumbTopY);
         float thumbH = Math.max(18.0F, Math.min(trackB - thumbT, (float)this.thumbHeight));

         float mx = TabbedMenuScreen.mouseX();
         float my = TabbedMenuScreen.mouseY();
         boolean hovered = this.dragging || (mx >= trackL - 4.0F && mx <= trackR + 4.0F && my >= trackT && my <= trackB);
         this.hoverAnim = UiTheme.smoothState(this.hoverAnim, hovered ? 1.0F : 0.0F);

         float maxW = Math.max(3.5F, trackR - trackL);
         float w = UiAnimation.lerp(Math.min(3.5F, maxW), Math.min(5.5F, maxW), this.hoverAnim);
         float barX = trackR - w;
         float r = w * 0.5F;
         int accent = UiTheme.accentColor();

         GlassRenderer.drawRoundedBox(
            barX,
            trackT,
            w,
            trackH,
            r,
            UiTheme.withAlpha(0xFF0A0814, Math.round(72.0F + 56.0F * this.hoverAnim)),
            UiTheme.withAlpha(UiTheme.GLASS_BORDER, 90),
            0.75F
         );
         if (this.hoverAnim > 0.04F) {
            GlassRenderer.drawSoftShadow(
               barX,
               thumbT,
               w,
               thumbH,
               r,
               5.5F,
               0.0F,
               UiTheme.withAlpha(accent, Math.round(105.0F * this.hoverAnim))
            );
         }
         int topC = UiTheme.withAlpha(UiTheme.lerpArgb(accent, UiTheme.accentSoftGlow(), 0.30F), Math.round(180.0F + 70.0F * this.hoverAnim));
         int botC = UiTheme.withAlpha(UiTheme.accentDeep(), Math.round(170.0F + 75.0F * this.hoverAnim));
         GlassRenderer.drawRoundedGradientBox(
            barX,
            thumbT,
            w,
            thumbH,
            r,
            topC,
            botC,
            UiTheme.withAlpha(UiTheme.accentSoftGlow(), Math.round(145.0F + 95.0F * this.hoverAnim)),
            0.85F,
            0.0F
         );
      }
   }

   public double getClampedScrollOffset() {
      return MathHelper.clamp_double(this.scrollOffset, Math.min(0.0, -((double)this.itemCount * this.itemHeight - (double)(this.bottomY - this.topY))), 0.0);
   }

   public void setBounds(int n, int n2, int n3, int n4) {
      this.leftX = (double)n;
      this.topY = n2;
      this.rightX = (double)n3;
      this.bottomY = n4;
      this.recalculateThumbMetrics();
   }

   public void scrollToBottom() {
      this.scrollOffset = MathHelper.clamp_double(-9999999.0, Math.min(0.0, -((double)this.itemCount * this.itemHeight - (double)(this.bottomY - this.topY))), 0.0);
   }

   public boolean isAtBottom() {
      return this.scrollOffset == MathHelper.clamp_double(-9999999.0, Math.min(0.0, -((double)this.itemCount * this.itemHeight - (double)(this.bottomY - this.topY))), 0.0);
   }

   public void handleMouseClick(double d, double d2) {
      this.recalculateThumbMetrics();
      double d3 = this.viewportHeight / ((double)this.itemCount * this.itemHeight + 0.0);
      double d4 = (double)((int)(-d2 / d3));
      if (this.dragging) {
         this.dragging = false;
      } else if (this.isPointOverThumb(d, d2)) {
         this.dragging = true;
         this.dragOffsetY = d4 - this.getClampedScrollOffset();
      }

      this.clampScrollOffset();
   }

   public ScrollBar(int n) {
       super();
      this.contentPadding = 0;
      this.itemHeight = (double)n;
      this.resetDefaultBounds();
   }

   public void handleMouseWheel() {
      int n = Math.round((float)Augustus.getInstance().getMouseWheelTracker().consumeDelta() / 3.0F);
      this.pendingScrollDelta += (double)n;
      if (n != 0) {
         this.scrollSpeed = Math.abs(this.pendingScrollDelta / 200.0);
      }

      this.scrollOffset = MathHelper.clamp_double(this.scrollOffset, Math.min(0.0, -((double)this.itemCount * this.itemHeight - (double)(this.bottomY - this.topY))), 0.0);
   }

   public void setLeftX(double d) {
      this.leftX = d;
   }
}
