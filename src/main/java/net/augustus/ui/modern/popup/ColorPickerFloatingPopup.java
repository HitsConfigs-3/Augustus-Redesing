package net.augustus.ui.modern.popup;

import java.awt.Color;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.modules.render.CustomClientColor;
import net.augustus.render.RenderUtil;
import net.augustus.settings.ColorSetting;
import net.augustus.ui.modern.components.ModernButton;
import net.augustus.ui.modern.components.ModernSlider;
import net.augustus.ui.modern.components.ModernToggleSwitch;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.utils.render.GlStateSaver;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

/**
 * Floating Liquid Glass HSV color picker popup adapted from Icarus's ColorPopup,
 * supporting Primary & Fade colors, Alpha, Rainbow/Fade/Astolfo/ClientColor modes,
 * Speed & Distance sliders, and Hex copy/paste without mutating ColorSetting property schemas.
 */
public final class ColorPickerFloatingPopup implements FloatingPopup {
   private enum DragTarget {
      NONE,
      SV,
      HUE,
      ALPHA
   }

   private static final GlStateSaver GL_SAVER = GlStateSaver.create();

   private final PopupHost host;
   private final ColorSetting setting;
   private final ModernToggleSwitch rainbowSwitch = new ModernToggleSwitch();
   private final ModernToggleSwitch fadeSwitch = new ModernToggleSwitch();
   private final ModernToggleSwitch astolfoSwitch = new ModernToggleSwitch();
   private final ModernToggleSwitch clientColorSwitch = new ModernToggleSwitch();
   private final ModernSlider speedSlider = new ModernSlider();
   private final ModernSlider distSlider = new ModernSlider();
   private final ModernSlider clientAlphaSlider = new ModernSlider();
   private final ModernButton copyButton = new ModernButton();
   private final ModernButton pasteButton = new ModernButton();

   private boolean editingFadeTarget = false;
   private DragTarget drag = DragTarget.NONE;
   private int lastDragX;
   private int lastDragY;
   private boolean draggingSpeed = false;
   private boolean draggingDist = false;
   private boolean draggingClientAlpha = false;
   private int sliderClickX = -9999;
   private int sliderClickY = -9999;

   private float x;
   private float y;
   private float width = 276.0F;
   private float height = 308.0F;
   private float openAnim = 0.0F;

   private float svX, svY, svW, svH;
   private float hueX, hueY, hueW, hueH;
   private float alphaX, alphaY, alphaW, alphaH;
   private float tabPrimX, tabPrimY, tabPrimW, tabFadeX, tabFadeY, tabFadeW, tabH;

   public ColorPickerFloatingPopup(PopupHost host, ColorSetting setting) {
      this.host = host;
      this.setting = setting;
   }

   public void updateAnchor(float anchorX, float anchorY, int containerW, int containerH) {
      boolean hasFade = this.setting.isFadeEnabled() && !this.setting.isRainbowEnabled() && !this.setting.isClientColorEnabled();
      if (!hasFade) {
         this.editingFadeTarget = false;
      }

      boolean hasModes = this.setting.isRainbowEnabled() || this.setting.isFadeEnabled() || this.setting.isAstolfoEnabled() || this.setting.isClientColorEnabled();
      this.width = 278.0F;
      this.height = 294.0F + (hasFade ? 28.0F : 0.0F) + (hasModes ? 54.0F : 0.0F);

      this.x = Math.max(16.0F, Math.min(anchorX, containerW - this.width - 14.0F));
      if (anchorY + this.height + 6.0F <= containerH - 12.0F) {
         this.y = anchorY + 6.0F;
      } else {
         this.y = Math.max(14.0F, Math.min(containerH - this.height - 14.0F, anchorY - this.height - 24.0F));
      }
   }

   @Override
   public void draw(int mouseX, int mouseY) {
      this.openAnim = UiAnimation.smooth(this.openAnim, 1.0F, 22.0F);
      float eased = UiAnimation.easeOutCubic(this.openAnim);
      float scale = 0.94F + 0.06F * eased;
      float pivotX = this.x + this.width * 0.5F;
      float pivotY = this.y;
      GL11.glPushMatrix();
      GL11.glTranslatef(pivotX, pivotY, 0.0F);
      GL11.glScalef(scale, scale, 1.0F);
      GL11.glTranslatef(-pivotX, -pivotY, 0.0F);

      GlassRenderer.drawFloatingGlassPopup(
         this.x,
         this.y,
         this.width,
         this.height,
         UiTheme.POPUP_RADIUS,
         UiTheme.THICK_BORDER_THICKNESS,
         UiTheme.GLASS_BORDER_STRONG,
         eased
      );

      float pad = 12.0F;
      float curY = this.y + pad;
      float innerW = this.width - pad * 2.0F;
      int accent = UiTheme.accentColor();
      CustomFontUtil fontSemi = FontManager.interSemiBold;
      CustomFontUtil fontMed = FontManager.interMedium;

      // Header row: preview swatch + hex label + copy/paste
      int livePreview = this.setting.getColorInt((byte)0);
      GlassRenderer.drawRoundedBox(this.x + pad, curY + 1.0F, 22.0F, 22.0F, 6.0F, livePreview, 0xEEFFFFFF, 1.6F);
      Color activeColor = this.editingFadeTarget ? this.setting.getFadeColor() : this.setting.getPrimaryColor();
      String hex = String.format("#%02X%02X%02X", activeColor.getRed(), activeColor.getGreen(), activeColor.getBlue());
      fontSemi.drawVerticallyCenteredString(hex, (double)(this.x + pad + 30.0F), (double)curY, 24.0, UiTheme.TEXT_PRIMARY);

      this.copyButton.render(this.x + this.width - pad - 52.0F, curY, 24.0F, 24.0F, null, LucideIcon.COPY, ModernButton.Variant.GLASS, mouseX, mouseY);
      this.pasteButton.render(this.x + this.width - pad - 24.0F, curY, 24.0F, 24.0F, null, LucideIcon.CLIPBOARD, ModernButton.Variant.GLASS, mouseX, mouseY);
      curY += 30.0F;

      // Primary / Fade color target selector tabs when Fade is enabled
      boolean showFadeTabs = this.setting.isFadeEnabled() && !this.setting.isRainbowEnabled() && !this.setting.isClientColorEnabled();
      if (showFadeTabs) {
         this.tabH = 24.0F;
         float trayX = this.x + pad;
         float trayY = curY;
         float trayPad = 3.0F;
         this.tabPrimW = (innerW - trayPad * 2.0F) * 0.5F;
         this.tabFadeW = this.tabPrimW;
         this.tabPrimX = trayX + trayPad;
         this.tabPrimY = trayY + trayPad;
         this.tabFadeX = this.tabPrimX + this.tabPrimW;
         this.tabFadeY = trayY + trayPad;
         float btnH = this.tabH - trayPad * 2.0F;

         GlassRenderer.drawRecessedWell(trayX, trayY, innerW, this.tabH, 7.0F, 0.0F, 0.0F, (float)mouseX, (float)mouseY, accent, eased, UiTheme.LEVEL_WELL);
         float slideT = UiAnimation.smoothState("cp_tab_slide_" + System.identityHashCode(this), this.editingFadeTarget ? 1.0F : 0.0F, 18.0F);
         float pillX = UiAnimation.lerp(this.tabPrimX, this.tabFadeX, slideT);
         // The sliding Primary / Fade End pill is a raised control inside the tray well, so it takes the
         // raised level's veil (pulled towards the accent) over the vessel material the tray painted.
         int pillTop = UiTheme.scaleAlpha(UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, 0.0F, 1.0F, UiTheme.lerpArgb(accent, 0xFFEAE5FF, 0.14F), 0.86F), eased);
         int pillBot = UiTheme.scaleAlpha(UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, 0.0F, 1.0F, UiTheme.accentDeep(), 0.86F), eased);
         GlassRenderer.drawSoftShadow(pillX, this.tabPrimY, this.tabPrimW, btnH, 5.0F, 5.0F, 1.0F, UiTheme.withAlpha(accent, Math.round(52.0F * eased)));
         GlassRenderer.drawRoundedGradientBox(pillX, this.tabPrimY, this.tabPrimW, btnH, 5.0F, pillTop, pillBot, UiTheme.scaleAlpha(0xD8EAE5FF, eased), 1.0F, 0.16F * eased);

         boolean primHover = RenderUtil.isHovered(this.tabPrimX, this.tabPrimY, this.tabPrimW, btnH, mouseX, mouseY);
         boolean fadeHover = RenderUtil.isHovered(this.tabFadeX, this.tabFadeY, this.tabFadeW, btnH, mouseX, mouseY);
         int primCol = UiTheme.scaleAlpha(!this.editingFadeTarget || primHover ? UiTheme.TEXT_PRIMARY : UiTheme.secondaryTextColor(), eased);
         int fadeCol = UiTheme.scaleAlpha(this.editingFadeTarget || fadeHover ? UiTheme.TEXT_PRIMARY : UiTheme.secondaryTextColor(), eased);
         fontSemi.drawCenteredStringInBox("Primary", (double)this.tabPrimX, (double)this.tabPrimY, (double)this.tabPrimW, (double)btnH, primCol);
         fontSemi.drawCenteredStringInBox("Fade End", (double)this.tabFadeX, (double)this.tabFadeY, (double)this.tabFadeW, (double)btnH, fadeCol);
         curY += 30.0F;
      }

      float[] hsb = this.getActiveHsb();
      int alphaVal = this.getActiveAlpha();

      // 2D Saturation / Brightness Picker Square
      this.svX = this.x + pad;
      this.svY = curY;
      this.svW = innerW;
      this.svH = 112.0F;
      drawSvSquare(this.svX + 1.0F, this.svY + 1.0F, this.svW - 2.0F, this.svH - 2.0F, hsb[0]);
      GlassRenderer.drawRoundedOutline(this.svX, this.svY, this.svW, this.svH, 5.0F, UiTheme.BORDER_THICKNESS, this.drag == DragTarget.SV ? UiTheme.withAlpha(UiTheme.accentSoftGlow(), 220) : UiTheme.GLASS_BORDER_STRONG);

      float curPx = this.svX + MathHelper.clamp_float(hsb[1], 0.0F, 1.0F) * this.svW;
      float curPy = this.svY + (1.0F - MathHelper.clamp_float(hsb[2], 0.0F, 1.0F)) * this.svH;
      GlassRenderer.drawCircle(curPx, curPy, 5.8F, 0x00000000, 0xAA000000, 2.2F);
      GlassRenderer.drawCircle(curPx, curPy, 5.0F, 0x00000000, 0xFFEAE5FF, 1.6F);
      curY += this.svH + 10.0F;

      // Hue gradient bar
      this.hueX = this.x + pad;
      this.hueY = curY;
      this.hueW = innerW;
      this.hueH = 13.0F;
      drawHueBar(this.hueX + 2.0F, this.hueY + 1.5F, this.hueW - 4.0F, this.hueH - 3.0F);
      GlassRenderer.drawRoundedOutline(this.hueX, this.hueY, this.hueW, this.hueH, 4.5F, 1.4F, this.drag == DragTarget.HUE ? UiTheme.withAlpha(UiTheme.accentSoftGlow(), 220) : UiTheme.GLASS_BORDER_STRONG);
      float hueKnobX = this.hueX + MathHelper.clamp_float(hsb[0], 0.0F, 1.0F) * this.hueW;
      GlassRenderer.drawCircle(hueKnobX, this.hueY + this.hueH * 0.5F, 6.5F, 0xFFFFFFFF, UiTheme.withAlpha(accent, 190), 1.5F);
      curY += this.hueH + 10.0F;

      // Alpha gradient bar
      this.alphaX = this.x + pad;
      this.alphaY = curY;
      this.alphaW = innerW;
      this.alphaH = 13.0F;
      int rgbOpaque = 0xFF000000 | Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
      drawAlphaBar(this.alphaX + 2.0F, this.alphaY + 1.5F, this.alphaW - 4.0F, this.alphaH - 3.0F, rgbOpaque);
      GlassRenderer.drawRoundedOutline(this.alphaX, this.alphaY, this.alphaW, this.alphaH, 4.5F, 1.4F, this.drag == DragTarget.ALPHA ? UiTheme.withAlpha(UiTheme.accentSoftGlow(), 220) : UiTheme.GLASS_BORDER_STRONG);
      float alphaKnobX = this.alphaX + MathHelper.clamp_float((float)alphaVal / 255.0F, 0.0F, 1.0F) * this.alphaW;
      GlassRenderer.drawCircle(alphaKnobX, this.alphaY + this.alphaH * 0.5F, 6.5F, 0xFFFFFFFF, UiTheme.withAlpha(accent, 190), 1.5F);
      curY += this.alphaH + 12.0F;

      // Mode Toggles grid (2x2)
      boolean hideClientToggle = this.setting.getParentModule() instanceof CustomClientColor;
      float halfW = (innerW - 10.0F) * 0.5F;
      this.rainbowSwitch.drawLabeledRow(this.x + pad, curY, halfW, "Rainbow", this.setting.isRainbowEnabled(), mouseX, mouseY);
      this.fadeSwitch.drawLabeledRow(this.x + pad + halfW + 10.0F, curY, halfW, "Fade", this.setting.isFadeEnabled(), mouseX, mouseY);
      curY += 26.0F;

      this.astolfoSwitch.drawLabeledRow(this.x + pad, curY, halfW, "Astolfo", this.setting.isAstolfoEnabled(), mouseX, mouseY);
      if (!hideClientToggle) {
         this.clientColorSwitch.drawLabeledRow(this.x + pad + halfW + 10.0F, curY, halfW, "Client", this.setting.isClientColorEnabled(), mouseX, mouseY);
      }
      curY += 28.0F;

      // Mode-specific sliders
      if (this.setting.isRainbowEnabled()) {
         drawCompactSliderRow(fontMed, "Speed", this.x + pad, curY, innerW, mouseX, mouseY, this.speedSlider, this.setting.getRainbowSpeed(), 1.0, 200.0, this.draggingSpeed, this.sliderClickX, this.sliderClickY, v -> this.setting.setRainbowSpeed(v));
         curY += 25.0F;
         drawCompactSliderRow(fontMed, "Distance", this.x + pad, curY, innerW, mouseX, mouseY, this.distSlider, this.setting.getRainbowDistance(), 0.0, 200.0, this.draggingDist, this.sliderClickX, this.sliderClickY, v -> this.setting.setRainbowDistance(v));
      } else if (this.setting.isFadeEnabled()) {
         drawCompactSliderRow(fontMed, "Speed", this.x + pad, curY, innerW, mouseX, mouseY, this.speedSlider, this.setting.getFadeSpeed(), 1.0, 200.0, this.draggingSpeed, this.sliderClickX, this.sliderClickY, v -> this.setting.setFadeSpeed(v));
         curY += 25.0F;
         drawCompactSliderRow(fontMed, "Distance", this.x + pad, curY, innerW, mouseX, mouseY, this.distSlider, this.setting.getFadeDistance(), 0.0, 200.0, this.draggingDist, this.sliderClickX, this.sliderClickY, v -> this.setting.setFadeDistance(v));
      } else if (this.setting.isAstolfoEnabled()) {
         drawCompactSliderRow(fontMed, "Speed", this.x + pad, curY, innerW, mouseX, mouseY, this.speedSlider, this.setting.getAstolfoSpeed(), 1.0, 200.0, this.draggingSpeed, this.sliderClickX, this.sliderClickY, v -> this.setting.setAstolfoSpeed(v));
         curY += 25.0F;
         drawCompactSliderRow(fontMed, "Distance", this.x + pad, curY, innerW, mouseX, mouseY, this.distSlider, this.setting.getAstolfoDistance(), 0.0, 200.0, this.draggingDist, this.sliderClickX, this.sliderClickY, v -> this.setting.setAstolfoDistance(v));
      } else if (this.setting.isClientColorEnabled() && !hideClientToggle) {
         drawCompactSliderRow(fontMed, "Alpha", this.x + pad, curY, innerW, mouseX, mouseY, this.clientAlphaSlider, (double)this.setting.getClientColorAlphaValue(), 1.0, 255.0, this.draggingClientAlpha, this.sliderClickX, this.sliderClickY, v -> this.setting.setClientColorAlpha((int)Math.round(v)));
      }

      GL11.glPopMatrix();
   }

   @Override
   public void onClosed() {
      this.openAnim = 0.0F;
   }

   private interface ValueSetter {
      void apply(double v);
   }

   private void drawCompactSliderRow(
      CustomFontUtil font,
      String label,
      float x,
      float y,
      float w,
      int mouseX,
      int mouseY,
      ModernSlider slider,
      double val,
      double min,
      double max,
      boolean dragging,
      int clickX,
      int clickY,
      ValueSetter setter
   ) {
      font.drawVerticallyCenteredString(label, (double)x, (double)y, 22.0, UiTheme.secondaryTextColor());
      float labelSpace = 56.0F;
      double next = slider.render(
         x + labelSpace,
         y,
         w - labelSpace,
         22.0F,
         mouseX,
         mouseY,
         val,
         min,
         max,
         0,
         dragging,
         clickX,
         clickY,
         null,
         String.valueOf((int)Math.round(val))
      );
      if (next != val) {
         setter.apply(next);
         this.setting.notifyValueChanged();
         this.setting.notifyUserChanged();
      }
   }

   @Override
   public boolean contains(int mouseX, int mouseY) {
      return RenderUtil.isHovered(this.x, this.y, this.width, this.height, mouseX, mouseY);
   }

   @Override
   public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
      if (mouseButton != 0) {
         return false;
      }

      if (this.copyButton.isHovered(mouseX, mouseY)) {
         Color c = this.editingFadeTarget ? this.setting.getFadeColor() : this.setting.getPrimaryColor();
         String hex = String.format("#%02X%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
         try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(hex), null);
         } catch (Exception ignored) {
         }
         return true;
      }

      if (this.pasteButton.isHovered(mouseX, mouseY)) {
         try {
            Object raw = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (raw instanceof String) {
               Color parsed = parseHexColor(((String)raw).trim());
               if (parsed != null) {
                  if (this.editingFadeTarget) {
                     this.setting.setFadeAlpha(parsed.getAlpha());
                     this.setting.setFadeColor(parsed);
                     Color.RGBtoHSB(parsed.getRed(), parsed.getGreen(), parsed.getBlue(), this.setting.getFadeHsb());
                  } else {
                     this.setting.setPrimaryAlpha(parsed.getAlpha());
                     this.setting.setPrimaryColor(parsed);
                     Color.RGBtoHSB(parsed.getRed(), parsed.getGreen(), parsed.getBlue(), this.setting.getPrimaryHsb());
                  }
                  this.setting.notifyValueChanged();
                  this.setting.notifyUserChanged();
               }
            }
         } catch (Exception ignored) {
         }
         return true;
      }

      boolean showFadeTabs = this.setting.isFadeEnabled() && !this.setting.isRainbowEnabled() && !this.setting.isClientColorEnabled();
      if (showFadeTabs) {
         if (RenderUtil.isHovered(this.tabPrimX, this.tabPrimY, this.tabPrimW, this.tabH, mouseX, mouseY)) {
            this.editingFadeTarget = false;
            return true;
         }
         if (RenderUtil.isHovered(this.tabFadeX, this.tabFadeY, this.tabFadeW, this.tabH, mouseX, mouseY)) {
            this.editingFadeTarget = true;
            return true;
         }
      }

      if (RenderUtil.isHovered(this.svX, this.svY, this.svW, this.svH, mouseX, mouseY)) {
         this.drag = DragTarget.SV;
         this.applyDrag(mouseX, mouseY);
         return true;
      }
      if (RenderUtil.isHovered(this.hueX - 3.0F, this.hueY - 3.0F, this.hueW + 6.0F, this.hueH + 6.0F, mouseX, mouseY)) {
         this.drag = DragTarget.HUE;
         this.applyDrag(mouseX, mouseY);
         return true;
      }
      if (RenderUtil.isHovered(this.alphaX - 3.0F, this.alphaY - 3.0F, this.alphaW + 6.0F, this.alphaH + 6.0F, mouseX, mouseY)) {
         this.drag = DragTarget.ALPHA;
         this.applyDrag(mouseX, mouseY);
         return true;
      }

      if (this.rainbowSwitch.isHovered(mouseX, mouseY)) {
         boolean next = !this.setting.isRainbowEnabled();
         this.setting.setRainbowEnabled(next);
         if (next) {
            this.setting.setFadeEnabled(false);
            this.setting.setAstolfoEnabled(false);
            this.setting.setClientColorEnabled(false);
         }
         this.setting.notifyValueChanged();
         this.setting.notifyUserChanged();
         return true;
      }
      if (this.fadeSwitch.isHovered(mouseX, mouseY)) {
         boolean next = !this.setting.isFadeEnabled();
         this.setting.setFadeEnabled(next);
         if (next) {
            this.setting.setRainbowEnabled(false);
            this.setting.setAstolfoEnabled(false);
            this.setting.setClientColorEnabled(false);
         }
         this.setting.notifyValueChanged();
         this.setting.notifyUserChanged();
         return true;
      }
      if (this.astolfoSwitch.isHovered(mouseX, mouseY)) {
         boolean next = !this.setting.isAstolfoEnabled();
         this.setting.setAstolfoEnabled(next);
         if (next) {
            this.setting.setRainbowEnabled(false);
            this.setting.setFadeEnabled(false);
            this.setting.setClientColorEnabled(false);
         }
         this.setting.notifyValueChanged();
         this.setting.notifyUserChanged();
         return true;
      }
      if (!(this.setting.getParentModule() instanceof CustomClientColor) && this.clientColorSwitch.isHovered(mouseX, mouseY)) {
         boolean next = !this.setting.isClientColorEnabled();
         this.setting.setClientColorEnabled(next);
         if (next) {
            this.setting.setRainbowEnabled(false);
            this.setting.setFadeEnabled(false);
            this.setting.setAstolfoEnabled(false);
         }
         this.setting.notifyValueChanged();
         this.setting.notifyUserChanged();
         return true;
      }

      this.sliderClickX = mouseX;
      this.sliderClickY = mouseY;
      if (this.speedSlider.isTrackHovered(mouseX, mouseY)) {
         this.draggingSpeed = true;
      }
      if (this.distSlider.isTrackHovered(mouseX, mouseY)) {
         this.draggingDist = true;
      }
      if (this.clientAlphaSlider.isTrackHovered(mouseX, mouseY)) {
         this.draggingClientAlpha = true;
      }

      return true;
   }

   @Override
   public void mouseClickMove(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) {
      if (mouseButton == 0 && this.drag != DragTarget.NONE) {
         this.applyDrag(mouseX, mouseY);
      }
   }

   @Override
   public void mouseReleased(int mouseX, int mouseY, int state) {
      this.drag = DragTarget.NONE;
      this.draggingSpeed = false;
      this.draggingDist = false;
      this.draggingClientAlpha = false;
      this.sliderClickX = -9999;
      this.sliderClickY = -9999;
   }

   private void applyDrag(int mouseX, int mouseY) {
      float[] hsb = this.getActiveHsb();
      int alphaVal = this.getActiveAlpha();

      if (this.drag == DragTarget.SV) {
         hsb[1] = MathHelper.clamp_float((mouseX - this.svX) / this.svW, 0.0F, 1.0F);
         hsb[2] = 1.0F - MathHelper.clamp_float((mouseY - this.svY) / this.svH, 0.0F, 1.0F);
      } else if (this.drag == DragTarget.HUE) {
         hsb[0] = MathHelper.clamp_float((mouseX - this.hueX) / this.hueW, 0.0F, 0.995F);
      } else if (this.drag == DragTarget.ALPHA) {
         alphaVal = MathHelper.clamp_int(Math.round(((mouseX - this.alphaX) / this.alphaW) * 255.0F), 0, 255);
      }

      int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
      Color nextColor = new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alphaVal);
      if (this.editingFadeTarget) {
         this.setting.setFadeAlpha(alphaVal);
         this.setting.setFadeColor(nextColor);
      } else {
         this.setting.setPrimaryAlpha(alphaVal);
         this.setting.setPrimaryColor(nextColor);
      }
      this.setting.notifyValueChanged();
      this.setting.notifyUserChanged();
   }

   private float[] getActiveHsb() {
      return this.editingFadeTarget ? this.setting.getFadeHsb() : this.setting.getPrimaryHsb();
   }

   private int getActiveAlpha() {
      return this.editingFadeTarget ? this.setting.getFadeAlpha() : this.setting.getPrimaryAlpha();
   }

   private static Color parseHexColor(String s) {
      if (s.startsWith("#")) {
         s = s.substring(1);
      }
      if (s.length() == 6) {
         int rgb = (int)Long.parseLong(s, 16);
         return new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 255);
      }
      if (s.length() == 8) {
         int rgba = (int)Long.parseLong(s, 16);
         return new Color((rgba >> 24) & 255, (rgba >> 16) & 255, (rgba >> 8) & 255, rgba & 255);
      }
      return null;
   }

   private static void drawSvSquare(float x, float y, float w, float h, float hue) {
      int hueRgb = Color.HSBtoRGB(hue, 1.0F, 1.0F);
      int hr = (hueRgb >> 16) & 255;
      int hg = (hueRgb >> 8) & 255;
      int hb = hueRgb & 255;

      GL_SAVER.setBlend(true);
      GL_SAVER.setTexture2D(false);
      GL_SAVER.setAlpha(false);
      GL11.glShadeModel(GL11.GL_SMOOTH);
      GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

      Tessellator tess = Tessellator.getInstance();
      WorldRenderer wr = tess.getWorldRenderer();
      // Top white -> pure hue; Bottom left -> white, bottom right -> hue
      wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
      wr.pos((double)x, (double)(y + h), 0.0).color(255, 255, 255, 255).endVertex();
      wr.pos((double)(x + w), (double)(y + h), 0.0).color(hr, hg, hb, 255).endVertex();
      wr.pos((double)(x + w), (double)y, 0.0).color(hr, hg, hb, 255).endVertex();
      wr.pos((double)x, (double)y, 0.0).color(255, 255, 255, 255).endVertex();
      tess.draw();

      // Overlay vertical black gradient from transparent top to opaque black bottom
      wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
      wr.pos((double)x, (double)(y + h), 0.0).color(0, 0, 0, 255).endVertex();
      wr.pos((double)(x + w), (double)(y + h), 0.0).color(0, 0, 0, 255).endVertex();
      wr.pos((double)(x + w), (double)y, 0.0).color(0, 0, 0, 0).endVertex();
      wr.pos((double)x, (double)y, 0.0).color(0, 0, 0, 0).endVertex();
      tess.draw();

      GL11.glShadeModel(GL11.GL_FLAT);
      GL_SAVER.restoreAlpha();
      GL_SAVER.restoreTexture2D();
      GL_SAVER.restoreBlend();
   }

   private static void drawHueBar(float x, float y, float w, float h) {
      int[] stops = {
         0xFFFF0000,
         0xFFFFFF00,
         0xFF00FF00,
         0xFF00FFFF,
         0xFF0000FF,
         0xFFFF00FF,
         0xFFFF0000
      };
      GL_SAVER.setBlend(true);
      GL_SAVER.setTexture2D(false);
      GL_SAVER.setAlpha(false);
      GL11.glShadeModel(GL11.GL_SMOOTH);

      Tessellator tess = Tessellator.getInstance();
      WorldRenderer wr = tess.getWorldRenderer();
      float segW = w / 6.0F;
      wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
      for (int i = 0; i < 6; i++) {
         float x0 = x + segW * i;
         float x1 = x + segW * (i + 1);
         int c0 = stops[i];
         int c1 = stops[i + 1];
         wr.pos((double)x0, (double)(y + h), 0.0).color((c0 >> 16) & 255, (c0 >> 8) & 255, c0 & 255, 255).endVertex();
         wr.pos((double)x1, (double)(y + h), 0.0).color((c1 >> 16) & 255, (c1 >> 8) & 255, c1 & 255, 255).endVertex();
         wr.pos((double)x1, (double)y, 0.0).color((c1 >> 16) & 255, (c1 >> 8) & 255, c1 & 255, 255).endVertex();
         wr.pos((double)x0, (double)y, 0.0).color((c0 >> 16) & 255, (c0 >> 8) & 255, c0 & 255, 255).endVertex();
      }
      tess.draw();

      GL11.glShadeModel(GL11.GL_FLAT);
      GL_SAVER.restoreAlpha();
      GL_SAVER.restoreTexture2D();
      GL_SAVER.restoreBlend();
   }

   private static void drawAlphaBar(float x, float y, float w, float h, int rgbOpaque) {
      int r = (rgbOpaque >> 16) & 255;
      int g = (rgbOpaque >> 8) & 255;
      int b = rgbOpaque & 255;

      GL_SAVER.setBlend(true);
      GL_SAVER.setTexture2D(false);
      GL_SAVER.setAlpha(false);
      GL11.glShadeModel(GL11.GL_SMOOTH);

      Tessellator tess = Tessellator.getInstance();
      WorldRenderer wr = tess.getWorldRenderer();
      wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
      wr.pos((double)x, (double)(y + h), 0.0).color(r, g, b, 0).endVertex();
      wr.pos((double)(x + w), (double)(y + h), 0.0).color(r, g, b, 255).endVertex();
      wr.pos((double)(x + w), (double)y, 0.0).color(r, g, b, 255).endVertex();
      wr.pos((double)x, (double)y, 0.0).color(r, g, b, 0).endVertex();
      tess.draw();

      GL11.glShadeModel(GL11.GL_FLAT);
      GL_SAVER.restoreAlpha();
      GL_SAVER.restoreTexture2D();
      GL_SAVER.restoreBlend();
   }
}
