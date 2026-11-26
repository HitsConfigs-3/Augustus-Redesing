package net.augustus.ui.modern.components;

import java.awt.Color;
import net.augustus.clickgui.ClickGuiScreen;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.render.RenderUtil;
import net.augustus.settings.ColorSetting;
import net.augustus.ui.modern.popup.ColorPickerFloatingPopup;
import net.augustus.ui.modern.popup.PopupHost;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;

/**
 * Compact Liquid Glass color row trigger displaying a crisp rounded color swatch,
 * hex readout, active effect badge, and opening a floating HSV + Mode ColorPicker popup.
 */
public class ModernColorPicker {
   private float hoverAnim = 0.0F;
   private float pillX;
   private float pillY;
   private float pillW;
   private float pillH = 24.0F;
   private ColorPickerFloatingPopup popup;

   /**
    * Applies the ClickGUI open/close envelope to one ARGB at the point of draw.
    *
    * <p>The envelope cannot ride on GL state ({@code GlassRenderer} resets the GL colour before every quad,
    * the font renderer after every string), so the pill well, the live colour swatch, the hex readout and the
    * pipette glyph each carry the value themselves. Only alpha moves; the swatch keeps its geometry, and the
    * setting's own colour value ({@code liveColor}) is only scaled on its way to the draw, never stored.
    *
    * <p>Wrapped exactly once per token, at the draw call.
    */
   private static int faded(int argb) {
      // Settled overlay (everything except the ~288 ms open/close transition): skip the arithmetic.
      return ClickGuiScreen.getScreenFade() >= 0.999F ? argb : ClickGuiScreen.fadeArgb(argb);
   }

   public float measurePillWidth(ColorSetting setting) {
      String readout = this.buildReadout(setting);
      return Math.max(138.0F, FontManager.interMedium.getStringWidth(readout) + 56.0F);
   }

   public void renderPill(
      float pillX,
      float pillY,
      float maxPillWidth,
      ColorSetting setting,
      int mouseX,
      int mouseY,
      PopupHost host,
      float windowLocalPillX,
      float windowLocalBottomY
   ) {
      if (this.popup == null && host != null) {
         this.popup = new ColorPickerFloatingPopup(host, setting);
      }

      int liveColor = setting.getColorInt((byte)0);
      String readout = this.buildReadout(setting);
      CustomFontUtil fontMed = FontManager.interMedium;

      this.pillH = 24.0F;
      this.pillW = Math.min(Math.max(138.0F, fontMed.getStringWidth(readout) + 56.0F), Math.max(138.0F, maxPillWidth));
      this.pillX = pillX;
      this.pillY = pillY;

      boolean isOpen = host != null && host.isOpenFor(this);
      boolean hovered = RenderUtil.isHovered(this.pillX, this.pillY, this.pillW, this.pillH, mouseX, mouseY);
      this.hoverAnim = UiTheme.smoothState(this.hoverAnim, hovered ? 1.0F : 0.0F);
      float openT = UiTheme.smoothState("cp_opn_" + System.identityHashCode(this), isOpen ? 1.0F : 0.0F);

      int accent = UiTheme.accentColor();
      // A colour row trigger is an inset field, so it takes the deep-inset level's material (one step
      // past a plain well, because it is nested in a card row that may itself be a well).
      GlassRenderer.drawRecessedWell(
         this.pillX,
         this.pillY,
         this.pillW,
         this.pillH,
         UiTheme.CONTROL_RADIUS,
         this.hoverAnim,
         openT,
         (float)mouseX,
         (float)mouseY,
         faded(accent),
         isOpen ? UiTheme.THICK_BORDER_THICKNESS : UiTheme.BORDER_THICKNESS,
         UiTheme.LEVEL_WELL_DEEP
      );
      // The swatch carries the setting's live colour, so it is faded on its way to the draw only: the
      // setting's own value must never be touched.
      GlassRenderer.drawRoundedBox(this.pillX + 4.5F, this.pillY + 4.0F, 18.0F, 16.0F, 4.5F, faded(liveColor), faded(0xEEFFFFFF), 1.3F);
      fontMed.drawVerticallyCenteredString(readout, (double)(this.pillX + 27.5F), (double)this.pillY, (double)this.pillH, faded(UiTheme.TEXT_PRIMARY));
      LucideIcon.PIPETTE.drawCentered(this.pillX + this.pillW - 12.0F, this.pillY + this.pillH * 0.5F, 12.0F, faded(hovered || isOpen ? UiTheme.accentSoftGlow() : UiTheme.secondaryTextColor()));

      if (isOpen && this.popup != null) {
         this.popup.updateAnchor(windowLocalPillX, windowLocalBottomY, host.getClipWidth(), host.getClipHeight());
      }
   }

   public void render(
      float labelX,
      float pillX,
      float y,
      float maxRowWidth,
      String label,
      ColorSetting setting,
      int mouseX,
      int mouseY,
      PopupHost host,
      float windowLocalPillX,
      float windowLocalBottomY
   ) {
      FontManager.interSemiBold.drawVerticallyCenteredString(label, (double)labelX, (double)y, 26.0, faded(UiTheme.primaryTextColor()));
      float availableW = Math.max(138.0F, maxRowWidth - (pillX - labelX));
      this.renderPill(pillX, y + 1.0F, availableW, setting, mouseX, mouseY, host, windowLocalPillX, windowLocalBottomY);
   }

   private String buildReadout(ColorSetting setting) {
      int liveColor = setting.getColorInt((byte)0);
      Color c = setting.isClientColorEnabled() ? new Color(liveColor, true) : setting.getPrimaryColor();
      String modeTag = "";
      if (setting.isRainbowEnabled()) {
         modeTag = "Rainbow";
      } else if (setting.isFadeEnabled()) {
         modeTag = "Fade";
      } else if (setting.isAstolfoEnabled()) {
         modeTag = "Astolfo";
      } else if (setting.isClientColorEnabled()) {
         modeTag = "Client";
      }
      String hex = String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
      return modeTag.isEmpty() ? hex : hex + " (" + modeTag + ")";
   }

   public boolean handleClick(int mouseX, int mouseY, int mouseButton, PopupHost host, ColorSetting setting, float windowLocalPillX, float windowLocalBottomY) {
      if (mouseButton != 0 || !RenderUtil.isHovered(this.pillX, this.pillY, this.pillW, this.pillH, mouseX, mouseY)) {
         return false;
      }
      if (host != null) {
         if (this.popup == null) {
            this.popup = new ColorPickerFloatingPopup(host, setting);
         }
         this.popup.updateAnchor(windowLocalPillX, windowLocalBottomY, host.getClipWidth(), host.getClipHeight());
         host.toggle(this, this.popup);
      }
      return true;
   }

   public float getPillWidth() {
      return this.pillW;
   }
}
