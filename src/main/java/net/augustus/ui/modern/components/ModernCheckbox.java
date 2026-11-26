package net.augustus.ui.modern.components;

import net.augustus.clickgui.ClickGuiScreen;
import net.augustus.render.RenderUtil;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;

/**
 * Reusable Liquid Glass checkbox with smooth animated state and thickened Lucide checkmark.
 */
public class ModernCheckbox {
   private float checkAnim = 0.0F;
   private float hoverAnim = 0.0F;
   private float pressAnim = 0.0F;
   private boolean initialized = false;
   private float lastX;
   private float lastY;
   private float lastSize = 18.0F;

   /**
    * Applies the ClickGui open/close envelope to one ARGB at the point of draw.
    *
    * <p>The envelope cannot ride on GL state ({@code GlassRenderer} resets the GL colour before every quad
    * and the font renderer resets it after every string), so the box, its shadow and its check mark each
    * carry the value themselves. It is applied exactly once, here, and never to a derived value, so the
    * alpha is never scaled twice.
    */
   private static int faded(int argb) {
      // Settled overlay (everything except the ~288 ms open/close transition): skip the arithmetic.
      return ClickGuiScreen.getScreenFade() >= 0.999F ? argb : ClickGuiScreen.fadeArgb(argb);
   }

   public void render(float x, float y, float size, boolean checked, int mouseX, int mouseY) {
      if (!this.initialized) {
         this.checkAnim = checked ? 1.0F : 0.0F;
         this.initialized = true;
      }
      this.lastX = x;
      this.lastY = y;
      this.lastSize = size;

      boolean hovered = RenderUtil.isHovered(x - 2.0F, y - 2.0F, size + 4.0F, size + 4.0F, mouseX, mouseY);
      boolean pressed = hovered && UiAnimation.isPrimaryMouseDown();
      this.checkAnim = UiTheme.smoothState(this.checkAnim, checked ? 1.0F : 0.0F);
      this.hoverAnim = UiTheme.smoothState(this.hoverAnim, hovered ? 1.0F : 0.0F);
      float pressT = UiTheme.smoothPress(this.pressAnim, pressed);
      this.pressAnim = pressT;

      int accent = UiTheme.accentColor();
      // Both ends are level veils through the shared transfer: the OFF face is a raised control
      // surface, the ON face the same surface pulled towards the accent, so the box never becomes an
      // opaque plate and the card's glass stays visible through it.
      int offTop = UiTheme.glassControlVeilArgb(UiTheme.LEVEL_CONTROL_RAISED, this.hoverAnim, 0.0F, accent);
      int offBot = UiTheme.lerpArgb(offTop, UiTheme.glassRecessedDepthRgb(), 0.28F);
      int onTop = UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, this.hoverAnim, this.checkAnim, UiTheme.lerpArgb(accent, 0xFFEAE5FF, 0.14F), 0.88F);
      int onBot = UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, this.hoverAnim, this.checkAnim, UiTheme.accentDeep(), 0.88F);
      int topFill = UiTheme.lerpArgb(offTop, onTop, this.checkAnim);
      int botFill = UiTheme.lerpArgb(offBot, onBot, this.checkAnim);
      int borderArgb = UiTheme.lerpArgb(
         UiTheme.glassBorderFocusedOrHover(this.hoverAnim * 0.7F, accent),
         UiTheme.withAlpha(0xFFEAE5FF, 225),
         this.checkAnim * 0.85F
      );

      float radius = Math.max(4.5F, size * 0.28F);
      if (this.checkAnim > 0.05F) {
         GlassRenderer.drawSoftShadow(x, y, size, size, radius, 5.0F, 1.0F, faded(UiTheme.withAlpha(accent, Math.round(52.0F * this.checkAnim))));
      }
      GlassRenderer.drawInteractiveSurface(
         x,
         y,
         size,
         size,
         radius,
         faded(topFill),
         faded(botFill),
         faded(borderArgb),
         UiTheme.BORDER_THICKNESS,
         UiTheme.glassTopHighlightAlpha(Math.max(this.hoverAnim, this.checkAnim * 0.65F)),
         this.hoverAnim,
         pressT,
         true,
         (float)mouseX,
         (float)mouseY,
         faded(accent)
      );

      if (this.checkAnim > 0.05F) {
         float iconSize = size * (0.58F + 0.14F * UiAnimation.easeOutCubic(this.checkAnim));
         int checkColor = UiTheme.withAlpha(0xFFFFFFFF, Math.round(255.0F * this.checkAnim));
         LucideIcon.CHECK.drawCentered(x + size * 0.5F, y + size * 0.5F + pressT * 0.35F, iconSize, faded(checkColor), false);
      }
   }

   public boolean isHovered(int mouseX, int mouseY) {
      return RenderUtil.isHovered(this.lastX - 3.0F, this.lastY - 3.0F, this.lastSize + 6.0F, this.lastSize + 6.0F, mouseX, mouseY);
   }
}
