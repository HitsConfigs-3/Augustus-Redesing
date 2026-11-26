package net.augustus.ui.modern.components;

import net.augustus.clickgui.ClickGuiScreen;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.render.RenderUtil;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;

/**
 * Reusable Liquid Glass button supporting icons, variants, hover transitions, and crisp borders.
 */
public class ModernButton {
   public enum Variant {
      GLASS,
      PRIMARY,
      ACTIVE_GREEN,
      DANGER
   }

   /**
    * Applies the ClickGUI open/close envelope to one ARGB at the point of draw.
    *
    * <p>WHY per colour: the envelope cannot ride on GL state - {@code GlassRenderer} resets the GL colour
    * before every quad and the font renderer resets it after every string - so anything this button paints
    * has to carry the value itself. Only colours are scaled, never positions or sizes: this is a fade.
    * Call it exactly once per token, at the draw, so nothing is faded twice (which would fade that value
    * at the square of the intended rate).
    */
   private static int faded(int argb) {
      // Settled overlay (everything except the ~288 ms open/close transition): skip the arithmetic. The
      // value is never stored, so it can never leak a stale envelope into another frame or screen.
      return ClickGuiScreen.getScreenFade() >= 0.999F ? argb : ClickGuiScreen.fadeArgb(argb);
   }

   private float hoverAnim = 0.0F;
   private float pressAnim = 0.0F;
   private boolean enabled = true;
   private float lastX;
   private float lastY;
   private float lastW;
   private float lastH;

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public void updateHitBounds(float x, float y, float width, float height) {
      this.lastX = x;
      this.lastY = y;
      this.lastW = width;
      this.lastH = height;
   }

   public void render(
      float x,
      float y,
      float width,
      float height,
      String label,
      LucideIcon icon,
      Variant variant,
      int mouseX,
      int mouseY
   ) {
      this.render(x, y, width, height, UiTheme.CONTROL_RADIUS, label, icon, variant, mouseX, mouseY, FontManager.interMedium);
   }

   public void renderPill(
      float x,
      float y,
      float width,
      float height,
      String label,
      LucideIcon icon,
      Variant variant,
      int mouseX,
      int mouseY
   ) {
      this.render(x, y, width, height, height * 0.5F, label, icon, variant, mouseX, mouseY, FontManager.interMedium);
   }

   public void render(
      float x,
      float y,
      float width,
      float height,
      String label,
      LucideIcon icon,
      Variant variant,
      int mouseX,
      int mouseY,
      CustomFontUtil font
   ) {
      this.render(x, y, width, height, UiTheme.CONTROL_RADIUS, label, icon, variant, mouseX, mouseY, font);
   }

   public void render(
      float x,
      float y,
      float width,
      float height,
      float cornerRadius,
      String label,
      LucideIcon icon,
      Variant variant,
      int mouseX,
      int mouseY,
      CustomFontUtil font
   ) {
      this.lastX = x;
      this.lastY = y;
      this.lastW = width;
      this.lastH = height;

      boolean hovered = this.enabled && RenderUtil.isHovered(x, y, width, height, mouseX, mouseY);
      boolean pressed = hovered && UiAnimation.isPrimaryMouseDown();
      // One motion token for every ordinary control transition (UiTheme.stateSpeed(): 95% in ~230 ms),
      // so a button settles in step with a toggle, a tab and a selector. Still the same frame-rate
      // independent law, still no snapping on the way out.
      this.hoverAnim = UiTheme.smoothState(this.hoverAnim, hovered ? 1.0F : 0.0F);
      this.pressAnim = UiTheme.smoothPress(this.pressAnim, pressed);

      float radius = cornerRadius;
      int accent = UiTheme.accentColor();

      int topFill;
      int bottomFill;
      int border;
      float topHL;

      if (!this.enabled) {
         // Disabled rows keep the flat, quiet plate: they are not interactive, so they must not read
         // as a live glass surface (GlassRenderer skips the material for enabled == false).
         topFill = UiTheme.glassDisabledFillArgb();
         bottomFill = topFill;
         border = UiTheme.scaleAlpha(UiTheme.GLASS_BORDER, 0.55F);
         topHL = 0.02F;
      } else if (variant == Variant.PRIMARY) {
         if (this.hoverAnim > 0.03F) {
            GlassRenderer.drawSoftShadow(x, y, width, height, radius, 8.0F, 1.0F, faded(UiTheme.withAlpha(accent, Math.round(64.0F * this.hoverAnim))));
         }
         // Brand fills stay brand-coloured but are now expressed as a mix of the level veil, so the
         // material underneath is still visible through a primary button instead of being covered.
         topFill = UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, this.hoverAnim, 0.0F, UiTheme.lerpArgb(accent, 0xFFEAE5FF, 0.08F + 0.10F * this.hoverAnim), 0.90F);
         bottomFill = UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, this.hoverAnim, 0.0F, UiTheme.lerpArgb(UiTheme.accentDeep(), 0xFF100E24, 0.25F), 0.90F);
         border = UiTheme.lerpArgb(UiTheme.withAlpha(UiTheme.accentSoftGlow(), 175), 0xDDFFFFFF, this.hoverAnim);
         topHL = 0.14F + 0.06F * this.hoverAnim;
      } else if (variant == Variant.ACTIVE_GREEN) {
         int green = UiTheme.enabledColor();
         if (this.hoverAnim > 0.03F) {
            GlassRenderer.drawSoftShadow(x, y, width, height, radius, 8.0F, 1.0F, faded(UiTheme.withAlpha(green, Math.round(60.0F * this.hoverAnim))));
         }
         topFill = UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, this.hoverAnim, 0.0F, UiTheme.lerpArgb(green, 0xFFFFFFFF, 0.10F * this.hoverAnim), 0.86F);
         bottomFill = UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, this.hoverAnim, 0.0F, UiTheme.lerpArgb(green, 0xFF08100C, 0.24F), 0.86F);
         border = UiTheme.lerpArgb(UiTheme.withAlpha(0xFFFFFFFF, 150), 0xDDFFFFFF, this.hoverAnim);
         topHL = 0.14F + 0.06F * this.hoverAnim;
      } else if (variant == Variant.DANGER) {
         int baseFill = UiTheme.glassControlVeilArgb(UiTheme.LEVEL_CONTROL_RAISED, this.hoverAnim, 0.0F, accent);
         topFill = this.hoverAnim > 0.02F
            ? UiTheme.lerpArgb(baseFill, UiTheme.withAlpha(UiTheme.DANGER_ACCENT, 210), this.hoverAnim)
            : baseFill;
         bottomFill = UiTheme.lerpArgb(topFill, UiTheme.glassRecessedDepthRgb(), 0.22F);
         border = UiTheme.lerpArgb(UiTheme.withAlpha(UiTheme.DANGER_ACCENT, 165), 0xDDFFFFFF, this.hoverAnim);
         topHL = UiTheme.glassTopHighlightAlpha(this.hoverAnim);
      } else {
         border = UiTheme.glassBorderFocusedOrHover(this.hoverAnim * 0.75F, accent);
         topFill = UiTheme.glassControlVeilArgb(UiTheme.LEVEL_CONTROL_RAISED, this.hoverAnim, 0.0F, accent);
         bottomFill = UiTheme.lerpArgb(topFill, UiTheme.glassRecessedDepthRgb(), 0.18F);
         topHL = UiTheme.glassTopHighlightAlpha(this.hoverAnim);
      }

      GlassRenderer.drawInteractiveSurface(
         x,
         y,
         width,
         height,
         radius,
         faded(topFill),
         faded(bottomFill),
         faded(border),
         UiTheme.BORDER_THICKNESS,
         topHL,
         this.hoverAnim,
         this.pressAnim,
         this.enabled,
         (float)mouseX,
         (float)mouseY,
         faded(accent)
      );

      float nudgeY = this.enabled ? this.pressAnim * 0.4F : 0.0F;
      int fg = !this.enabled
         ? UiTheme.disabledTextColor()
         : (variant == Variant.GLASS && !hovered ? UiTheme.secondaryTextColor() : UiTheme.TEXT_PRIMARY);
      // `fg` is derived from raw tokens above, so a single application here is the only one it gets.
      fg = faded(fg);
      boolean hasText = label != null && !label.isEmpty();
      float iconSize = Math.min(13.0F, height * 0.50F);

      if (icon != null && hasText) {
         float textW = font.getStringWidth(label);
         float gap = 6.0F;
         float totalW = iconSize + gap + textW;
         float startX = x + (width - totalW) * 0.5F;
         icon.drawCentered(startX + iconSize * 0.5F, y + height * 0.5F + nudgeY, iconSize, fg);
         font.drawVerticallyCenteredString(label, (double)(startX + iconSize + gap), (double)(y + nudgeY), (double)height, fg);
      } else if (icon != null) {
         icon.drawCentered(x + width * 0.5F, y + height * 0.5F + nudgeY, iconSize, fg);
      } else if (hasText) {
         font.drawCenteredStringInBox(label, (double)x, (double)(y + nudgeY), (double)width, (double)height, fg);
      }
   }

   public boolean isHovered(int mouseX, int mouseY) {
      return this.enabled && RenderUtil.isHovered(this.lastX, this.lastY, this.lastW, this.lastH, mouseX, mouseY);
   }
}
