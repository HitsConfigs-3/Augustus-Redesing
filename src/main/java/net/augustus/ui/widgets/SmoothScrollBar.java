package net.augustus.ui.widgets;

import net.augustus.Augustus;
import net.augustus.render.RenderUtil;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.utils.input.KeyboardInputUtil;
import java.io.IOException;
import net.minecraft.util.MathHelper;

public class SmoothScrollBar {
   private int lastDragMouseX;
   private boolean dragging;
   private int lastDragMouseY;
   private float targetOffsetF;
   private float smoothedOffsetF;
   private boolean offsetInitialized;
   private float hoverAnim;
   private int lastMouseX = -9999;
   private int lastMouseY = -9999;
   private boolean vertical;
   private int trackLeftX;
   private int trackTopY;
   private int trackRightX;
   private int trackBottomY;
   private int thumbTopY;
   private int thumbBottomY;
   private int thumbRightX;
   private boolean visible;
   private int thumbLeftX;

   public SmoothScrollBar() {
      this.vertical = false;
   }

   public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      if (!this.visible || mouseButton != 0) {
         return;
      }
      if (RenderUtil.isPointInBounds(mouseX, mouseY, (double)(this.thumbLeftX - 2), (double)(this.thumbTopY - 2), (double)(this.thumbRightX + 2), (double)(this.thumbBottomY + 2))) {
         this.dragging = true;
         this.lastDragMouseX = mouseX;
         this.lastDragMouseY = mouseY;
      } else if (RenderUtil.isPointInBounds(mouseX, mouseY, (double)(this.trackLeftX - 3), (double)this.trackTopY, (double)(this.trackRightX + 3), (double)this.trackBottomY)) {
         // Click on track moves scrollbar smoothly toward click position
         if (this.vertical) {
            float trackH = Math.max(1.0F, (float)(this.trackBottomY - this.trackTopY));
            float thumbH = Math.max(20.0F, (float)(this.thumbBottomY - this.thumbTopY));
            float ratio = MathHelper.clamp_float(((float)mouseY - (float)this.trackTopY - thumbH * 0.5F) / Math.max(1.0F, trackH - thumbH), 0.0F, 1.0F);
            this.lastDragMouseY = mouseY;
            this.dragging = true;
            this.pendingTrackJumpRatio = ratio;
         } else {
            float trackW = Math.max(1.0F, (float)(this.trackRightX - this.trackLeftX));
            float thumbW = Math.max(20.0F, (float)(this.thumbRightX - this.thumbLeftX));
            float ratio = MathHelper.clamp_float(((float)mouseX - (float)this.trackLeftX - thumbW * 0.5F) / Math.max(1.0F, trackW - thumbW), 0.0F, 1.0F);
            this.lastDragMouseX = mouseX;
            this.dragging = true;
            this.pendingTrackJumpRatio = ratio;
         }
      }
   }

   private float pendingTrackJumpRatio = -1.0F;

   public int updateScrollOffset(int contentLength, int viewStart, int viewEnd, int currentOffset, int hitX, int hitY, int hitW, int hitH, int mouseX, int mouseY) {
      this.lastMouseX = mouseX;
      this.lastMouseY = mouseY;
      int viewportSpan = Math.max(1, viewEnd - viewStart);
      if (contentLength <= viewportSpan) {
         this.targetOffsetF = 0.0F;
         this.smoothedOffsetF = 0.0F;
         this.offsetInitialized = true;
         this.pendingTrackJumpRatio = -1.0F;
         return 0;
      }

      float maxScroll = (float)(contentLength - viewportSpan);
      if (!this.offsetInitialized || Math.abs((float)currentOffset - this.smoothedOffsetF) > 48.0F) {
         this.targetOffsetF = MathHelper.clamp_float((float)currentOffset, -maxScroll, 0.0F);
         this.smoothedOffsetF = this.targetOffsetF;
         this.offsetInitialized = true;
      }

      if (this.pendingTrackJumpRatio >= 0.0F) {
         this.targetOffsetF = -this.pendingTrackJumpRatio * maxScroll;
         this.smoothedOffsetF = UiAnimation.smooth(this.smoothedOffsetF, this.targetOffsetF, 22.0F);
         this.pendingTrackJumpRatio = -1.0F;
      }

      float wheelDelta = (float)Augustus.getInstance().getMouseWheelTracker().lastDelta;
      if (wheelDelta != 0.0F && RenderUtil.isHovered((float)hitX, (float)hitY, (float)hitW, (float)hitH, mouseX, mouseY) && !KeyboardInputUtil.isCtrlDown()) {
         float step = wheelDelta * 0.48F;
         this.targetOffsetF = MathHelper.clamp_float(this.targetOffsetF + step, -maxScroll, 0.0F);
      }

      if (this.dragging) {
         double ratioScale = (double)contentLength / (double)viewportSpan;
         if (this.vertical) {
            float delta = (float)(this.lastDragMouseY - mouseY) * (float)ratioScale;
            this.targetOffsetF = MathHelper.clamp_float(this.targetOffsetF + delta, -maxScroll, 0.0F);
            this.smoothedOffsetF = this.targetOffsetF;
            this.lastDragMouseY = mouseY;
         } else {
            float delta = (float)(this.lastDragMouseX - mouseX) * (float)ratioScale;
            this.targetOffsetF = MathHelper.clamp_float(this.targetOffsetF + delta, -maxScroll, 0.0F);
            this.smoothedOffsetF = this.targetOffsetF;
            this.lastDragMouseX = mouseX;
         }
      } else {
         this.targetOffsetF = MathHelper.clamp_float(this.targetOffsetF, -maxScroll, 0.0F);
         this.smoothedOffsetF = UiAnimation.smooth(this.smoothedOffsetF, this.targetOffsetF, 15.0F);
      }

      return Math.round(this.smoothedOffsetF);
   }

   public void resetPendingScroll() {
      this.offsetInitialized = false;
      this.pendingTrackJumpRatio = -1.0F;
   }

   public void mouseReleased(int mouseX, int mouseY, int state) {
      this.dragging = false;
      this.pendingTrackJumpRatio = -1.0F;
   }

   public boolean drawHorizontalBar(int left, int top, int right, int bottom, int scrollPos, int contentWidth, int accentUnused) {
      this.vertical = false;
      this.trackLeftX = left;
      this.trackTopY = top;
      this.trackRightX = right;
      this.trackBottomY = bottom;
      int trackW = right - left;
      if (contentWidth <= trackW) {
         this.visible = false;
         this.dragging = false;
         return false;
      }

      double fracStart = MathHelper.clamp_double((double)Math.max(0, scrollPos) / (double)contentWidth, 0.0, 1.0);
      int thumbW = Math.max(26, (int)Math.round(((double)trackW / (double)contentWidth) * (double)trackW));
      int thumbLeft = left + (int)Math.round(fracStart * (double)(trackW));
      if (thumbLeft + thumbW > right) {
         thumbLeft = Math.max(left, right - thumbW);
      }
      this.thumbLeftX = thumbLeft;
      this.thumbRightX = Math.min(right, thumbLeft + thumbW);
      this.thumbTopY = top;
      this.thumbBottomY = bottom;

      boolean hovered = this.dragging || RenderUtil.isHovered((float)left, (float)(top - 3), (float)trackW, (float)(bottom - top + 6), this.lastMouseX, this.lastMouseY);
      this.hoverAnim = UiAnimation.smooth(this.hoverAnim, hovered ? 1.0F : 0.0F, 16.0F);

      float maxH = Math.max(4.5F, (float)(bottom - top));
      float h = UiAnimation.lerp(Math.min(4.0F, maxH), maxH, this.hoverAnim);
      float barY = (float)bottom - h;
      int accent = UiTheme.accentColor();

      GlassRenderer.drawRoundedBox((float)left, barY, (float)trackW, h, h * 0.5F, UiTheme.withAlpha(0xFF0A0A14, Math.round(70.0F + 60.0F * this.hoverAnim)), UiTheme.withAlpha(UiTheme.GLASS_BORDER, 90), 0.75F);
      if (this.hoverAnim > 0.04F) {
         GlassRenderer.drawSoftShadow((float)this.thumbLeftX, barY, (float)(this.thumbRightX - this.thumbLeftX), h, h * 0.5F, 5.0F, 0.0F, UiTheme.withAlpha(accent, Math.round(95.0F * this.hoverAnim)));
      }
      int topC = UiTheme.withAlpha(UiTheme.lerpArgb(accent, UiTheme.accentSoftGlow(), 0.25F), Math.round(165.0F + 75.0F * this.hoverAnim));
      int botC = UiTheme.withAlpha(accent, Math.round(150.0F + 80.0F * this.hoverAnim));
      GlassRenderer.drawRoundedGradientBox((float)this.thumbLeftX, barY, (float)(this.thumbRightX - this.thumbLeftX), h, h * 0.5F, topC, botC, UiTheme.withAlpha(UiTheme.accentSoftGlow(), Math.round(135.0F + 90.0F * this.hoverAnim)), 0.9F, 0.16F * this.hoverAnim);
      this.visible = true;
      return true;
   }

   public boolean drawVerticalBar(int left, int top, int right, int bottom, int scrollPos, int contentHeight, int accentUnused) {
      this.vertical = true;
      this.trackLeftX = left;
      this.trackTopY = top;
      this.trackRightX = right;
      this.trackBottomY = bottom;
      int trackH = bottom - top;
      if (contentHeight <= trackH) {
         this.visible = false;
         this.dragging = false;
         return false;
      }

      float maxScroll = Math.max(1.0F, (float)(contentHeight - trackH));
      float visiblePos = MathHelper.clamp_float((float)(scrollPos - top), 0.0F, maxScroll);
      int thumbH = Math.max(28, (int)Math.round(((double)trackH / (double)contentHeight) * (double)trackH));
      float scrollRatio = visiblePos / maxScroll;
      int thumbTop = top + Math.round(scrollRatio * (float)Math.max(0, trackH - thumbH));

      this.thumbLeftX = left;
      this.thumbTopY = thumbTop;
      this.thumbRightX = right;
      this.thumbBottomY = Math.min(bottom, thumbTop + thumbH);

      boolean hovered = this.dragging || RenderUtil.isHovered((float)(left - 4), (float)top, (float)(right - left + 8), (float)trackH, this.lastMouseX, this.lastMouseY);
      this.hoverAnim = UiAnimation.smooth(this.hoverAnim, hovered ? 1.0F : 0.0F, 16.0F);

      float maxW = Math.max(4.5F, (float)(right - left));
      float w = UiAnimation.lerp(Math.min(4.0F, maxW), Math.min(6.5F, maxW), this.hoverAnim);
      float barX = (float)right - w;
      int accent = UiTheme.accentColor();

      // Subtle recessed scrollbar track rail
      GlassRenderer.drawRoundedBox(barX, (float)top, w, (float)trackH, w * 0.5F, UiTheme.withAlpha(0xFF0A0A14, Math.round(68.0F + 62.0F * this.hoverAnim)), UiTheme.withAlpha(UiTheme.GLASS_BORDER, 85), 0.75F);
      // Glowing capsule thumb
      if (this.hoverAnim > 0.04F) {
         GlassRenderer.drawSoftShadow(barX, (float)this.thumbTopY, w, (float)(this.thumbBottomY - this.thumbTopY), w * 0.5F, 6.0F, 0.0F, UiTheme.withAlpha(accent, Math.round(95.0F * this.hoverAnim)));
      }
      int topC = UiTheme.withAlpha(UiTheme.lerpArgb(accent, UiTheme.accentSoftGlow(), 0.25F), Math.round(170.0F + 75.0F * this.hoverAnim));
      int botC = UiTheme.withAlpha(UiTheme.accentDeep(), Math.round(165.0F + 75.0F * this.hoverAnim));
      GlassRenderer.drawRoundedGradientBox(barX, (float)this.thumbTopY, w, (float)(this.thumbBottomY - this.thumbTopY), w * 0.5F, topC, botC, UiTheme.withAlpha(UiTheme.accentSoftGlow(), Math.round(135.0F + 90.0F * this.hoverAnim)), 0.9F, 0.16F * this.hoverAnim);
      this.visible = true;
      return true;
   }
}
