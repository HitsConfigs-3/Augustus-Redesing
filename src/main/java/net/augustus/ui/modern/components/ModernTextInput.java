package net.augustus.ui.modern.components;

import net.augustus.clickgui.ClickGuiScreen;
import net.augustus.clickgui.components.TextFieldComponent;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.render.RenderUtil;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;
import org.lwjgl.opengl.GL11;

/**
 * Reusable Liquid Glass text field with crisp border animation, leading icon, placeholder,
 * and compatibility with {@link TextFieldComponent} or inline text strings.
 */
public class ModernTextInput {
   private float focusAnim = 0.0F;
   private float hoverAnim = 0.0F;
   private float lastX;
   private float lastY;
   private float lastW;
   private float lastH;

   /**
    * Applies the ClickGUI open/close envelope to one ARGB at the point of draw.
    *
    * <p>The envelope cannot ride on GL state ({@code GlassRenderer} resets the GL colour before every quad,
    * the font renderer after every string), so the field well, the icon, the placeholder, the caret and the
    * clear glyph each carry the value themselves. Only alpha is scaled - the text layout, caret position and
    * {@code TextFieldComponent}'s own editing state are untouched.
    *
    * <p>Wrapped exactly once per token, at the draw call. {@code TextFieldComponent} consumes the ARGB it is
    * handed directly, so the colour passed to it is never faded a second time on its way out.
    */
   private static int faded(int argb) {
      // Settled overlay (everything except the ~288 ms open/close transition): skip the arithmetic.
      return ClickGuiScreen.getScreenFade() >= 0.999F ? argb : ClickGuiScreen.fadeArgb(argb);
   }

   public void render(
      float x,
      float y,
      float width,
      float height,
      TextFieldComponent field,
      String placeholder,
      LucideIcon leadingIcon,
      int mouseX,
      int mouseY,
      CustomFontUtil font
   ) {
      this.lastX = x;
      this.lastY = y;
      this.lastW = width;
      this.lastH = height;

      CustomFontUtil textFont = font != null ? font : FontManager.interMedium;
      boolean hovered = RenderUtil.isHovered(x, y, width, height, mouseX, mouseY);
      boolean focused = field != null && field.isFocused();
      this.focusAnim = UiTheme.smoothState(this.focusAnim, focused ? 1.0F : 0.0F);
      this.hoverAnim = UiTheme.smoothState(this.hoverAnim, hovered ? 1.0F : 0.0F);

      int accent = UiTheme.accentColor();
      float borderW = focused ? UiTheme.THICK_BORDER_THICKNESS : UiTheme.BORDER_THICKNESS;
      // The field is an inset, so it reads LEVEL_WELL's material (painted inside drawRecessedWell)
      // and this only adds hover/focus feedback on top.
      GlassRenderer.drawRecessedWell(
         x,
         y,
         width,
         height,
         UiTheme.CONTROL_RADIUS,
         this.hoverAnim,
         this.focusAnim,
         (float)mouseX,
         (float)mouseY,
         faded(accent),
         borderW
      );

      float padLeft = 10.0F;
      if (leadingIcon != null) {
         leadingIcon.drawCentered(x + 14.0F, y + height * 0.5F, 13.0F, faded(focused ? UiTheme.TEXT_PRIMARY : UiTheme.mutedTextColor()));
         padLeft = 27.0F;
      }

      float textX = x + padLeft;
      float textY = y + (height - textFont.getFontHeight()) * 0.5F;
      String current = field != null ? field.getText() : "";
      if ((current == null || current.isEmpty()) && !focused && placeholder != null && !placeholder.isEmpty()) {
         textFont.drawString(placeholder, (double)textX, (double)textY, faded(UiTheme.scaleAlpha(UiTheme.mutedTextColor(), 0.78F)));
      } else if (field != null) {
         boolean scissorActive = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
         // Draw clipped inside the box so overly long strings don't bleed past the border.
         field.drawTextField((int)textX, (int)textY, mouseX, mouseY, textFont, faded(UiTheme.TEXT_PRIMARY));
         if (!scissorActive && GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
         }
      }
   }

   public void renderString(
      float x,
      float y,
      float width,
      float height,
      String text,
      boolean focused,
      String placeholder,
      LucideIcon leadingIcon,
      boolean showClearButton,
      int mouseX,
      int mouseY,
      long cursorTimerMs,
      CustomFontUtil font
   ) {
      this.lastX = x;
      this.lastY = y;
      this.lastW = width;
      this.lastH = height;

      CustomFontUtil textFont = font != null ? font : FontManager.interMedium;
      boolean hovered = RenderUtil.isHovered(x, y, width, height, mouseX, mouseY);
      this.focusAnim = UiTheme.smoothState(this.focusAnim, focused ? 1.0F : 0.0F);
      this.hoverAnim = UiTheme.smoothState(this.hoverAnim, hovered ? 1.0F : 0.0F);

      int accent = UiTheme.accentColor();
      float borderW = focused ? UiTheme.THICK_BORDER_THICKNESS : UiTheme.BORDER_THICKNESS;
      GlassRenderer.drawRecessedWell(
         x,
         y,
         width,
         height,
         UiTheme.CONTROL_RADIUS,
         this.hoverAnim,
         this.focusAnim,
         (float)mouseX,
         (float)mouseY,
         faded(accent),
         borderW
      );

      float padLeft = 10.0F;
      if (leadingIcon != null) {
         leadingIcon.drawCentered(x + 14.0F, y + height * 0.5F, 13.0F, faded(focused ? UiTheme.TEXT_PRIMARY : UiTheme.mutedTextColor()));
         padLeft = 27.0F;
      }

      float textX = x + padLeft;
      float textY = y + (height - textFont.getFontHeight()) * 0.5F;
      float rightReserve = showClearButton && ! (text == null || text.isEmpty()) ? 24.0F : 10.0F;
      float maxTextW = Math.max(24.0F, width - padLeft - rightReserve);
      boolean empty = text == null || text.isEmpty();

      if (empty && !focused) {
         if (placeholder != null && !placeholder.isEmpty()) {
            textFont.drawString(placeholder, (double)textX, (double)textY, faded(UiTheme.scaleAlpha(UiTheme.mutedTextColor(), 0.80F)));
         }
      } else {
         String display = trimLeadingToFit(textFont, text == null ? "" : text, maxTextW);
         textFont.drawString(display, (double)textX, (double)textY, faded(UiTheme.TEXT_PRIMARY));
         if (focused && (System.currentTimeMillis() - cursorTimerMs) % 900L < 450L) {
            float caretX = textX + textFont.getStringWidth(display) + 1.5F;
            GlassRenderer.drawSeparator(caretX, textY + 1.0F, caretX, textY + textFont.getFontHeight() - 1.0F, 1.6F, faded(UiTheme.TEXT_PRIMARY));
         }
      }

      if (showClearButton && !empty) {
         float clearCx = x + width - 14.0F;
         float clearCy = y + height * 0.5F;
         boolean clearHover = RenderUtil.isHovered(clearCx - 9.0F, y, 18.0F, height, mouseX, mouseY);
         GlassRenderer.drawVectorClose(clearCx, clearCy, 8.5F, faded(clearHover ? UiTheme.TEXT_PRIMARY : UiTheme.mutedTextColor()));
      }
   }

   public boolean isHovered(int mouseX, int mouseY) {
      return RenderUtil.isHovered(this.lastX, this.lastY, this.lastW, this.lastH, mouseX, mouseY);
   }

   public boolean isClearButtonHovered(int mouseX, int mouseY) {
      float clearCx = this.lastX + this.lastW - 14.0F;
      return RenderUtil.isHovered(clearCx - 9.0F, this.lastY, 18.0F, this.lastH, mouseX, mouseY);
   }

   private static String trimLeadingToFit(CustomFontUtil font, String text, float maxWidth) {
      if (text.isEmpty() || font.getStringWidth(text) <= maxWidth) {
         return text;
      }
      int start = text.length();
      while (start > 0 && font.getStringWidth(text.substring(start - 1)) <= maxWidth) {
         start--;
      }
      return text.substring(start);
   }
}
