package net.augustus.ui.modern.components;

import net.augustus.ClientManagers;
import net.augustus.font.FontManager;
import net.augustus.ui.TabbedMenuScreen;
import net.augustus.ui.menu.MenuBackdrop;
import net.augustus.ui.modern.font.InterFontUtil;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.MenuScreenSkin;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/**
 * Sleek Augustus-styled frosted glass button for client menu tabs, supporting Tabler icons,
 * trailing chevrons, semantic variants, smaller crisp Inter typography, and cursor-tracking hover glow.
 */
public class GlassMenuButton extends GuiButton {
   public enum Variant {
      DEFAULT,
      PRIMARY,
      DANGER,
      TOGGLE
   }

   private static InterFontUtil cachedButtonFont;
   private static final int FROST_ARC_STEPS = 6;

   private LucideIcon leftIcon;
   private LucideIcon rightIcon;
   private Variant variant = Variant.DEFAULT;
   private Boolean toggleState;
   private boolean iconOnly;
   private float hoverAnim;
   private float pressAnim;

   public GlassMenuButton(int id, int x, int y, int width, int height, String label) {
      super(id, x, y, width, height, label);
   }

   public GlassMenuButton withIcon(LucideIcon icon) {
      this.leftIcon = icon;
      return this;
   }

   public GlassMenuButton withTrailingIcon(LucideIcon icon) {
      this.rightIcon = icon;
      return this;
   }

   public GlassMenuButton withVariant(Variant variant) {
      this.variant = variant != null ? variant : Variant.DEFAULT;
      return this;
   }

   public GlassMenuButton withToggleState(boolean enabled) {
      this.variant = Variant.TOGGLE;
      this.toggleState = Boolean.valueOf(enabled);
      return this;
   }

   public GlassMenuButton withIconOnly(boolean iconOnly) {
      this.iconOnly = iconOnly;
      return this;
   }

   public void setToggleState(boolean enabled) {
      this.toggleState = Boolean.valueOf(enabled);
   }

   private static InterFontUtil buttonFont() {
      if (cachedButtonFont == null && FontManager.interMedium != null) {
         cachedButtonFont = FontManager.interMedium.deriveSize(10.5F);
      }
      return cachedButtonFont != null ? cachedButtonFont : FontManager.body();
   }

   @Override
   public void drawButton(Minecraft mc, int mouseX, int mouseY) {
      if (!this.visible) {
         return;
      }
      this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition
         && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;
      this.hoverAnim = UiTheme.smoothState(this.hoverAnim, this.enabled && this.hovered ? 1.0F : 0.0F);
      this.pressAnim = UiTheme.smoothPress(this.pressAnim, this.enabled && this.hovered && UiAnimation.isPrimaryMouseDown());

      float x = (float)this.xPosition;
      float y = (float)this.yPosition;
      float w = (float)this.width;
      float h = (float)this.height;
      float r = ClientManagers.moduleManager != null
         && ClientManagers.moduleManager.clientSettings != null
         && !ClientManagers.moduleManager.clientSettings.roundedButtons.getValue()
            ? 0.0F
            : Math.max(0.0F, Math.min(Math.min(w, h) * 0.5F, 5.5F));

      int accent = UiTheme.accentColor();
      int effAccent = this.variant == Variant.DANGER ? UiTheme.DANGER_ACCENT : accent;

      if (this.enabled && this.hoverAnim > 0.04F) {
         int haloAlpha = Math.round((this.variant == Variant.PRIMARY ? 46.0F : 30.0F) * this.hoverAnim);
         GlassRenderer.drawSoftShadow(x, y, w, h, r, 6.0F, 0.5F, UiTheme.withAlpha(effAccent, haloAlpha));
      }

      drawBackdropFrost(x, y, w, h, r);

      float press = 1.0F - MenuScreenSkin.PRESS_DARKEN * this.pressAnim;
      int baseTop = MenuScreenSkin.FILL_TOP;
      int baseBot = MenuScreenSkin.FILL_BOTTOM;
      if (this.variant == Variant.PRIMARY) {
         baseTop = UiTheme.lerpArgb(MenuScreenSkin.FILL_TOP, accent, 0.22F + 0.14F * this.hoverAnim);
         baseBot = UiTheme.lerpArgb(MenuScreenSkin.FILL_BOTTOM, UiTheme.accentDeep(), 0.26F + 0.12F * this.hoverAnim);
      } else if (this.variant == Variant.DANGER && this.hoverAnim > 0.02F) {
         baseTop = UiTheme.lerpArgb(MenuScreenSkin.FILL_TOP, UiTheme.DANGER_ACCENT, 0.18F * this.hoverAnim);
         baseBot = UiTheme.lerpArgb(MenuScreenSkin.FILL_BOTTOM, 0xFF280B12, 0.22F * this.hoverAnim);
      }

      int topFill = this.enabled ? UiTheme.scaleAlpha(baseTop, MenuScreenSkin.VEIL * press) : UiTheme.glassDisabledFillArgb();
      int botFill = this.enabled ? UiTheme.scaleAlpha(baseBot, MenuScreenSkin.VEIL * press) : topFill;
      int edgeRgb = this.variant == Variant.PRIMARY
         ? UiTheme.lerpArgb(accent, 0xFFE2DCFF, 0.25F * this.hoverAnim)
         : UiTheme.lerpArgb(effAccent, MenuScreenSkin.FILL_TOP, this.variant == Variant.DANGER ? 0.52F : MenuScreenSkin.EDGE_MIX);
      int edgeAlpha = this.variant == Variant.PRIMARY
         ? Math.round(185.0F + 70.0F * this.hoverAnim)
         : Math.round((float)MenuScreenSkin.EDGE_ALPHA + (float)(MenuScreenSkin.EDGE_HOVER_ALPHA - MenuScreenSkin.EDGE_ALPHA) * this.hoverAnim);
      int borderArgb = this.enabled
         ? UiTheme.withAlpha(edgeRgb, edgeAlpha)
         : UiTheme.scaleAlpha(UiTheme.GLASS_BORDER, 0.55F);

      float lightX = Math.max(x + w * 0.12F, Math.min(x + w * 0.88F, (float)mouseX));
      float lightY = Math.max(y + h * 0.20F, Math.min(y + h * 0.80F, (float)mouseY));
      float lightAlpha = this.enabled ? (0.24F * this.hoverAnim) * (1.0F - 0.30F * this.pressAnim) : 0.0F;
      int lightColor = UiTheme.lerpArgb(effAccent, 0xFFF3EEFF, 0.45F);

      GlassRenderer.drawMenuGlassBox(
         x,
         y,
         w,
         h,
         r,
         topFill,
         botFill,
         borderArgb,
         UiTheme.BORDER_THICKNESS,
         lightX,
         lightY,
         lightAlpha,
         Math.max(w, h) * 0.46F,
         lightColor
      );

      this.mouseDragged(mc, mouseX, mouseY);

      float pressOffsetY = this.enabled ? this.pressAnim * 0.45F : 0.0F;
      int fg = this.enabled ? MenuScreenSkin.LABEL_COLOR : UiTheme.disabledTextColor();
      int iconColor = !this.enabled
         ? UiTheme.disabledTextColor()
         : (this.variant == Variant.DANGER
            ? UiTheme.lerpArgb(0xFFFCA5A5, 0xFFEF4444, this.hoverAnim * 0.6F)
            : (this.variant == Variant.PRIMARY
               ? 0xFFF8F5FF
               : UiTheme.lerpArgb(accent, 0xFFFFFFFF, 0.32F)));

      float iconSize = Math.min(11.5F, h * 0.48F);

      if (this.iconOnly && this.leftIcon != null && this.leftIcon.isAvailable()) {
         this.leftIcon.drawCentered(x + w * 0.5F, y + h * 0.5F + pressOffsetY, iconSize, fg, false);
         return;
      }

      InterFontUtil font = buttonFont();
      String label = this.displayString == null ? "" : this.displayString;
      if (font == null) {
         drawCenteredString(mc.fontRendererObj, label, this.xPosition + this.width / 2, this.yPosition + (this.height - 8) / 2, fg);
         return;
      }

      boolean hasLeft = this.leftIcon != null && this.leftIcon.isAvailable();
      boolean hasRight = this.rightIcon != null && this.rightIcon.isAvailable();
      boolean hasDot = this.variant == Variant.TOGGLE && this.toggleState != null && !hasRight;
      float rightReserve = hasRight ? iconSize + 8.0F : (hasDot ? 12.0F : 0.0F);
      float padX = Math.max(8.0F, h * 0.35F);
      float iconGap = 5.5F;
      float textW = font.getStringWidthFormatted(label);
      float groupW = textW + (hasLeft ? iconSize + iconGap : 0.0F);
      float availW = Math.max(20.0F, w - padX * 2.0F - rightReserve);

      float scale = groupW > availW ? Math.max(0.75F, availW / groupW) : 1.0F;
      float contentW = groupW * scale;
      float centerAreaW = w - rightReserve;
      float startX = x + (hasRight || hasDot ? Math.max(padX, (centerAreaW - contentW) * 0.5F) : (w - contentW) * 0.5F);
      float centerY = y + h * 0.5F + pressOffsetY;

      if (hasLeft) {
         float drawIconSize = iconSize * scale;
         this.leftIcon.drawCentered(startX + drawIconSize * 0.5F, centerY, drawIconSize, iconColor, false);
         startX += drawIconSize + iconGap * scale;
      }

      float textY = y + (h - font.getFontHeight() * scale) * 0.5F + pressOffsetY;
      if (scale < 0.995F) {
         font.drawStringAtSize(
            InterFontUtil.stripFormatting(label),
            startX,
            textY,
            font.getEffectiveSize() * scale,
            fg,
            true
         );
      } else {
         font.drawStringFormatted(label, (double)startX, (double)textY, fg, true);
      }

      if (hasRight) {
         float chevSize = Math.min(10.0F, h * 0.42F);
         this.rightIcon.drawCentered(x + w - 9.0F, centerY, chevSize, UiTheme.scaleAlpha(0xFFC4B5FD, 0.85F + 0.15F * this.hoverAnim), false);
      } else if (hasDot) {
         int dotColor = this.toggleState.booleanValue() ? 0xFF4ADE80 : 0xFFF87171;
         GlassRenderer.drawCircle(x + w - 9.5F, centerY, 2.5F, dotColor);
      }
   }

   private static void drawBackdropFrost(float x, float y, float width, float height, float radius) {
      MenuBackdrop backdrop = TabbedMenuScreen.backdrop();
      if (backdrop == null || !backdrop.isAvailable() || width < 2.0F || height < 2.0F) {
         return;
      }
      float inset = 1.0F;
      float fx = x + inset;
      float fy = y + inset;
      float fw = width - inset * 2.0F;
      float fh = height - inset * 2.0F;
      if (fw <= 1.0F || fh <= 1.0F) {
         return;
      }
      float r = Math.min(Math.max(0.0F, radius - inset), Math.min(fw, fh) * 0.5F);
      if (r < 0.5F) {
         backdrop.drawFrost(fx, fy, fw, fh, 1.0F);
         return;
      }
      backdrop.drawFrost(fx, fy + r, fw, fh - r * 2.0F, 1.0F);
      for (int i = 0; i < FROST_ARC_STEPS; i++) {
         float outer = r * (float)i / (float)FROST_ARC_STEPS;
         float inner = r * (float)(i + 1) / (float)FROST_ARC_STEPS;
         float d = Math.max(0.0F, Math.min(r, outer));
         float k = r - d;
         float step = r - (float)Math.sqrt((double)Math.max(0.0F, r * r - k * k));
         float rowW = fw - step * 2.0F;
         if (rowW <= 0.0F) {
            continue;
         }
         backdrop.drawFrost(fx + step, fy + outer, rowW, inner - outer, 1.0F);
         backdrop.drawFrost(fx + step, fy + fh - inner, rowW, inner - outer, 1.0F);
      }
   }
}
