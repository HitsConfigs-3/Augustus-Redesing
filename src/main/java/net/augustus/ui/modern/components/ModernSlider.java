package net.augustus.ui.modern.components;

import net.augustus.clickgui.ClickGuiScreen;
import net.augustus.clickgui.components.TextFieldComponent;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.render.RenderUtil;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;
import net.minecraft.util.MathHelper;

/**
 * Reusable Liquid Glass slider with animated fill & thumb, thick visible rail outline,
 * smooth drag handling, and integrated numeric input badge.
 */
public class ModernSlider {
   private float fillAnim = 0.0F;
   private float hoverAnim = 0.0F;
   private float knobScaleAnim = 0.0F;
   private boolean initialized = false;

   private float trackX;
   private float trackY;
   private float trackW;
   private float trackH;
   private float chipX;
   private float chipY;
   private float chipW;
   private float chipH;

   /**
    * Applies the ClickGUI open/close envelope to one ARGB at the point of draw.
    *
    * <p>The envelope cannot ride on GL state ({@code GlassRenderer} resets the GL colour before every quad
    * and the font renderer resets it after every string), so each rail, fill, thumb and label colour below
    * carries the value itself. Only alpha is scaled: the rail geometry, the value chip rectangle and the
    * returned value are all untouched, because this is a colour fade, not a layout change.
    *
    * <p>Note the rail colour is deliberately opaque ({@code | 0xFF000000}); fading happens here at the draw
    * call, after the geometry maths, so nothing that a position is derived from is ever faded.
    */
   private static int faded(int argb) {
      // Settled overlay (everything except the ~288 ms open/close transition): skip the arithmetic.
      return ClickGuiScreen.getScreenFade() >= 0.999F ? argb : ClickGuiScreen.fadeArgb(argb);
   }

   public double render(
      float trackX,
      float trackY,
      float trackW,
      float rowHeight,
      int mouseX,
      int mouseY,
      double value,
      double minValue,
      double maxValue,
      int decimalPlaces,
      boolean dragging,
      int clickX,
      int clickY,
      TextFieldComponent textField,
      String formattedValue
   ) {
      double cleanVal = value == 1.7976931348623157E308 ? minValue : value;
      double span = Math.max(1.0E-9, maxValue - minValue);

      float chipReserve = 58.0F;
      float gap = 12.0F;
      float sliderBarW = Math.max(60.0F, trackW - chipReserve - gap);
      float barH = 4.0F;
      float barY = trackY + (rowHeight - barH) * 0.5F;

      this.trackX = trackX;
      this.trackY = trackY;
      this.trackW = sliderBarW;
      this.trackH = rowHeight;

      this.chipW = chipReserve;
      this.chipH = 24.0F;
      this.chipX = trackX + sliderBarW + gap;
      this.chipY = trackY + (rowHeight - this.chipH) * 0.5F;

      if (dragging && RenderUtil.isHovered(trackX - 6.0F, trackY, sliderBarW + 12.0F, rowHeight, clickX, clickY)) {
         double norm = MathHelper.clamp_double((double)(mouseX - trackX) / (double)sliderBarW, 0.0, 1.0);
         cleanVal = minValue + norm * span;
         cleanVal = roundToDecimalPlaces(cleanVal, decimalPlaces);
      }

      float targetFrac = (float)MathHelper.clamp_double((cleanVal - minValue) / span, 0.0, 1.0);
      if (!this.initialized) {
         this.fillAnim = targetFrac;
         this.initialized = true;
      }

      boolean barHovered = RenderUtil.isHovered(trackX - 4.0F, trackY, sliderBarW + 8.0F, rowHeight, mouseX, mouseY);
      this.fillAnim = dragging ? targetFrac : UiAnimation.smooth(this.fillAnim, targetFrac, 22.0F);
      this.hoverAnim = UiTheme.smoothState(this.hoverAnim, barHovered || dragging ? 1.0F : 0.0F);
      this.knobScaleAnim = UiAnimation.smooth(this.knobScaleAnim, dragging ? 1.0F : (barHovered ? 0.55F : 0.0F), 18.0F);

      int accent = UiTheme.accentColor();
      // Sleek 4px recessed rail track. The rail is only 4 px tall, so it deliberately does NOT spend a
      // blur sample on its own material (a 4 px strip of blurred world is invisible), but its colour is
      // still the WELL level's tint plus a depth rim — no hand-written opaque fill, so the slider rail
      // stays in the same colour family as the wells and fields around it.
      int railTop = UiTheme.glassLevelRgb(UiTheme.LEVEL_WELL, this.hoverAnim, 0.0F, accent) | 0xFF000000;
      GlassRenderer.drawRoundedBox(
         trackX,
         barY,
         sliderBarW,
         barH,
         barH * 0.5F,
         faded(railTop),
         faded(UiTheme.GLASS_BORDER),
         0.85F
      );
      // Active #8B5CF6 violet gradient fill
      float fillW = Math.max(barH, sliderBarW * this.fillAnim);
      if (this.fillAnim > 0.005F) {
         if (this.hoverAnim > 0.05F) {
            GlassRenderer.drawSoftShadow(trackX, barY, Math.max(barH, fillW), barH, barH * 0.5F, 5.5F, 0.0F, faded(UiTheme.withAlpha(accent, Math.round(72.0F * this.hoverAnim))));
         }
         int fillLeft = UiTheme.lerpArgb(accent, UiTheme.accentSoftGlow(), 0.12F);
         int fillRight = UiTheme.withAlpha(UiTheme.accentSoftGlow(), 248);
         GlassRenderer.drawRoundedGradientBox(
            trackX,
            barY,
            Math.max(barH, fillW),
            barH,
            barH * 0.5F,
            faded(UiTheme.withAlpha(fillLeft, 245)),
            faded(fillRight),
            0,
            0.0F,
            0.0F
         );
      }

      // Clean circular thumb indicator with crisp ring and subtle hover halo
      float knobX = trackX + MathHelper.clamp_float(sliderBarW * this.fillAnim, barH * 0.5F, sliderBarW - barH * 0.5F);
      float knobY = barY + barH * 0.5F;
      float knobR = 5.5F + 1.0F * this.knobScaleAnim;
      if (this.knobScaleAnim > 0.04F) {
         GlassRenderer.drawCircle(knobX, knobY, knobR + 3.0F * this.knobScaleAnim, faded(UiTheme.withAlpha(accent, Math.round(54.0F * this.knobScaleAnim))));
      }
      GlassRenderer.drawSoftShadow(knobX - knobR, knobY - knobR, knobR * 2.0F, knobR * 2.0F, knobR, 4.0F, 1.0F, faded(0x58020308));
      int knobRing = UiTheme.lerpArgb(0x2E000000, UiTheme.withAlpha(accent, 235), this.knobScaleAnim);
      // The thumb face is deliberately near-opaque, but it still has to fade with the overlay: a solid
      // white dot left at full strength over a vanishing card is exactly the pop this change removes.
      GlassRenderer.drawCircle(knobX, knobY, knobR, faded(0xFFF8FAFC), faded(knobRing), 1.25F);

      // Right-hand numeric field: a deeper recess than the slider's own well, because it is a field
      // *inside* the control row, so it uses LEVEL_WELL_DEEP through the same coverage function rather
      // than its own darker literal. Same material pipeline, one level deeper.
      boolean chipHovered = RenderUtil.isHovered(this.chipX, this.chipY, this.chipW, this.chipH, mouseX, mouseY);
      boolean chipFocused = textField != null && textField.isFocused();
      float chipHovT = UiTheme.smoothState("sl_chp_hov_" + System.identityHashCode(this), chipHovered ? 1.0F : 0.0F);
      float chipFocT = UiTheme.smoothState("sl_chp_foc_" + System.identityHashCode(this), chipFocused ? 1.0F : 0.0F);

      GlassRenderer.drawRecessedWell(
         this.chipX,
         this.chipY,
         this.chipW,
         this.chipH,
         UiTheme.CONTROL_RADIUS,
         chipHovT,
         chipFocT,
         (float)mouseX,
         (float)mouseY,
         faded(accent),
         chipFocused ? UiTheme.THICK_BORDER_THICKNESS : UiTheme.BORDER_THICKNESS,
         UiTheme.LEVEL_WELL_DEEP
      );

      if (textField != null && chipFocused) {
         CustomFontUtil font = FontManager.interMedium;
         float tw = Math.min(this.chipW - 10.0F, font.getStringWidth(textField.getText()));
         float textX = this.chipX + Math.max(4.0F, (this.chipW - tw) * 0.5F);
         float textY = this.chipY + (this.chipH - font.getFontHeight()) * 0.5F;
         textField.drawTextField((int)textX, (int)textY, mouseX, mouseY, font, faded(UiTheme.TEXT_PRIMARY));
      } else {
         String display = formattedValue != null ? formattedValue : formatNumber(cleanVal, decimalPlaces);
         if (textField != null) {
            float dw = FontManager.interMedium.getStringWidthTabular(display);
            float dx = this.chipX + Math.max(4.0F, (this.chipW - dw) * 0.5F);
            float dy = this.chipY + (this.chipH - FontManager.interMedium.getFontHeight()) * 0.5F;
            textField.updateLayout((int)dx, (int)dy, mouseX, mouseY, FontManager.interMedium);
         }
         FontManager.interMedium.drawCenteredStringInBoxTabular(display, (double)this.chipX, (double)this.chipY, (double)this.chipW, (double)this.chipH, faded(UiTheme.TEXT_PRIMARY));
      }

      return cleanVal;
   }

   public boolean isTrackHovered(int mouseX, int mouseY) {
      return RenderUtil.isHovered(this.trackX - 6.0F, this.trackY, this.trackW + 12.0F, this.trackH, mouseX, mouseY);
   }

   public boolean isValueChipHovered(int mouseX, int mouseY) {
      return RenderUtil.isHovered(this.chipX, this.chipY, this.chipW, this.chipH, mouseX, mouseY);
   }

   public float getTrackX() {
      return this.trackX;
   }

   public float getTrackY() {
      return this.trackY;
   }

   public float getTrackWidth() {
      return this.trackW;
   }

   public float getTrackHeight() {
      return this.trackH;
   }

   public float getChipX() {
      return this.chipX;
   }

   public float getChipY() {
      return this.chipY;
   }

   public float getChipWidth() {
      return this.chipW;
   }

   public float getChipHeight() {
      return this.chipH;
   }

   public static String formatNumber(double d, int decimalPlaces) {
      if (decimalPlaces <= 0) {
         return String.valueOf((int)Math.round(d));
      }
      double rounded = roundToDecimalPlaces(d, decimalPlaces);
      int intPart = (int)rounded;
      if ((double)intPart == rounded) {
         return String.valueOf(intPart);
      }
      return String.valueOf(rounded);
   }

   private static double roundToDecimalPlaces(double d, int n) {
      if (n <= 0) {
         return (double)Math.round(d);
      }
      double factor = Math.pow(10.0, (double)n);
      return (double)Math.round(d * factor) / factor;
   }
}
