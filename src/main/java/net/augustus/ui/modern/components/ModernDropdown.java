package net.augustus.ui.modern.components;

import java.util.List;
import net.augustus.clickgui.ClickGuiScreen;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.render.RenderUtil;
import net.augustus.ui.modern.popup.DropdownFloatingPopup;
import net.augustus.ui.modern.popup.PopupHost;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Reusable Liquid Glass dropdown selector supporting single-selection and multi-selection
 * with floating Kawase-blurred option popup and rotating chevron animation.
 */
public class ModernDropdown {
   private float hoverAnim = 0.0F;
   private float openAnim = 0.0F;
   private float pressAnim = 0.0F;
   private float lastX;
   private float lastY;
   private float lastW;
   private float lastH = 24.0F;
   private DropdownFloatingPopup popup;

   /**
    * Applies the ClickGUI open/close envelope to one ARGB at the point of draw.
    *
    * <p>The envelope cannot ride on GL state ({@code GlassRenderer} resets the GL colour before every quad,
    * the font renderer after every string), so the trigger well, its summary text and the chevron carry the
    * value themselves. Colours only - coordinates stay untouched, because this is a fade, not a resize.
    * Wrapped exactly once per token, at the draw.
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
      String summaryText,
      int mouseX,
      int mouseY,
      PopupHost host,
      DropdownFloatingPopup.Source source,
      float windowLocalAnchorX,
      float windowLocalBottomY
   ) {
      this.lastX = x;
      this.lastY = y;
      this.lastW = width;
      this.lastH = height;

      if (this.popup == null && host != null && source != null) {
         this.popup = new DropdownFloatingPopup(host, source);
      }

      boolean isOpen = host != null && host.isOpenFor(this);
      boolean hovered = RenderUtil.isHovered(x, y, width, height, mouseX, mouseY);
      boolean pressed = hovered && UiAnimation.isPrimaryMouseDown();
      this.hoverAnim = UiTheme.smoothState(this.hoverAnim, hovered ? 1.0F : 0.0F);
      this.openAnim = UiTheme.smoothState(this.openAnim, isOpen ? 1.0F : 0.0F);
      this.pressAnim = UiTheme.smoothPress(this.pressAnim, pressed);

      int accent = UiTheme.accentColor();
      // Trigger wells are insets, so they carry the WELL level's glass material (painted inside
      // drawRecessedWell) rather than a flat veil; the focus weight is the open state, which is what
      // the dropdown's "focus" actually is while its popup is on screen.
      GlassRenderer.drawRecessedWell(
         x,
         y,
         width,
         height,
         UiTheme.CONTROL_RADIUS,
         Math.max(this.hoverAnim, this.pressAnim * 0.4F),
         this.openAnim,
         (float)mouseX,
         (float)mouseY,
         faded(accent),
         isOpen ? UiTheme.THICK_BORDER_THICKNESS : UiTheme.BORDER_THICKNESS
      );

      float nudgeY = this.pressAnim * 0.35F;
      CustomFontUtil font = FontManager.interMedium;
      String text = summaryText == null || summaryText.isEmpty() ? "-" : summaryText;
      float maxTextW = Math.max(20.0F, width - 30.0F);
      String trimmed = trimToWidth(font, text, maxTextW);
      font.drawVerticallyCenteredString(trimmed, (double)(x + 10.0F), (double)(y + nudgeY), (double)height, faded(UiTheme.TEXT_PRIMARY));

      // Rotating chevron icon
      float cx = x + width - 13.0F;
      float cy = y + height * 0.5F + nudgeY;
      GL11.glPushMatrix();
      GlStateManager.translate(cx, cy, 0.0F);
      GlStateManager.rotate(180.0F * this.openAnim, 0.0F, 0.0F, 1.0F);
      GlStateManager.translate(-cx, -cy, 0.0F);
      LucideIcon.CHEVRON_DOWN.drawCentered(cx, cy, 12.0F, faded(isOpen || hovered ? UiTheme.accentSoftGlow() : UiTheme.secondaryTextColor()));
      GL11.glPopMatrix();

      if (isOpen && this.popup != null) {
         this.popup.updateAnchor(windowLocalAnchorX, windowLocalBottomY, width, host.getClipWidth(), host.getClipHeight());
      }
   }

   public boolean handleClick(int mouseX, int mouseY, int mouseButton, PopupHost host, DropdownFloatingPopup.Source source, float windowLocalAnchorX, float windowLocalBottomY) {
      if (mouseButton != 0 || !RenderUtil.isHovered(this.lastX, this.lastY, this.lastW, this.lastH, mouseX, mouseY)) {
         return false;
      }
      if (host != null && source != null) {
         if (this.popup == null) {
            this.popup = new DropdownFloatingPopup(host, source);
         }
         this.popup.updateAnchor(windowLocalAnchorX, windowLocalBottomY, this.lastW, host.getClipWidth(), host.getClipHeight());
         host.toggle(this, this.popup);
      }
      return true;
   }

   private static String trimToWidth(CustomFontUtil font, String text, float maxW) {
      if (font.getStringWidth(text) <= maxW) {
         return text;
      }
      StringBuilder sb = new StringBuilder();
      float ellipsisW = font.getStringWidth("...");
      for (int i = 0; i < text.length(); i++) {
         char ch = text.charAt(i);
         if (font.getStringWidth(sb.toString() + ch) + ellipsisW > maxW) {
            break;
         }
         sb.append(ch);
      }
      return sb.append("...").toString();
   }

   public boolean isHovered(int mouseX, int mouseY) {
      return RenderUtil.isHovered(this.lastX, this.lastY, this.lastW, this.lastH, mouseX, mouseY);
   }
}
