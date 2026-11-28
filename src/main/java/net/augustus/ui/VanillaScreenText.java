package net.augustus.ui;

import net.augustus.font.FontManager;
import net.augustus.ui.modern.font.InterFontUtil;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.MenuScreenSkin;
import net.augustus.ui.modern.render.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/**
 * Compact typography helpers for vanilla-derived screens, aligned with the
 * Augustus Redesing visual hierarchy (13.5F titles, 10.5F body, 9.0F small metadata).
 */
public final class VanillaScreenText {
   private VanillaScreenText() {
   }

   private static InterFontUtil font() {
      return FontManager.uiBody();
   }

   private static InterFontUtil titleFont() {
      return FontManager.uiTitle();
   }

   private static InterFontUtil smallFont() {
      return FontManager.uiSmall();
   }

   private static FontRenderer bitmap() {
      return Minecraft.getMinecraft().fontRendererObj;
   }

   private static int restingColor(int color) {
      return (color & 0x00FFFFFF) == 0x00FFFFFF ? MenuScreenSkin.LABEL_COLOR : color;
   }

   public static void drawCenteredString(String text, int x, int y, int color) {
      InterFontUtil font = font();
      int resting = restingColor(color);
      if (font == null) {
         FontRenderer legacy = bitmap();
         legacy.drawStringWithShadow(text, (float)x - (float)legacy.getStringWidth(text) / 2.0F, (float)y, resting);
         return;
      }
      font.drawStringWithShadow(text, (double)((float)x - font.getStringWidthFormatted(text) / 2.0F), (double)y, resting);
   }

   public static void drawCenteredOnScreen(int screenWidth, String text, int y, int color) {
      drawCenteredString(text, screenWidth / 2, y, color);
   }

   /** Renders a modern screen title with an optional centered Tabler icon beside the title. */
   public static void drawHeader(int screenWidth, String title, LucideIcon icon, int y) {
      InterFontUtil tf = titleFont();
      if (tf == null) {
         drawCenteredOnScreen(screenWidth, title, y, 0xFFF8FAFC);
         return;
      }
      String safeTitle = title == null ? "" : title;
      float textW = tf.getStringWidthFormatted(safeTitle);
      float iconSize = icon != null && icon.isAvailable() ? 12.5F : 0.0F;
      float gap = iconSize > 0.0F ? 6.0F : 0.0F;
      float totalW = iconSize + gap + textW;
      float startX = (float)screenWidth * 0.5F - totalW * 0.5F;
      if (iconSize > 0.0F) {
         float iconY = (float)y + (tf.getFontHeight() - iconSize) * 0.5F;
         icon.draw(startX, iconY, iconSize, UiTheme.lerpArgb(UiTheme.accentColor(), 0xFFFFFFFF, 0.35F), false);
      }
      tf.drawStringWithShadow(safeTitle, (double)(startX + iconSize + gap), (double)y, 0xFFF8FAFC);
   }

   public static void drawString(String text, int x, int y, int color) {
      InterFontUtil font = font();
      int resting = restingColor(color);
      if (font == null) {
         bitmap().drawStringWithShadow(text, (float)x, (float)y, resting);
         return;
      }
      font.drawStringWithShadow(text, (double)x, (double)y, resting);
   }

   public static void drawSmallString(String text, float x, float y, int color) {
      InterFontUtil sf = smallFont();
      int resting = restingColor(color);
      if (sf == null) {
         bitmap().drawStringWithShadow(text, x, y, resting);
         return;
      }
      sf.drawStringWithShadow(text, (double)x, (double)y, resting);
   }

   public static void drawSmallCenteredString(String text, float centerX, float y, int color) {
      InterFontUtil sf = smallFont();
      int resting = restingColor(color);
      if (sf == null) {
         FontRenderer legacy = bitmap();
         legacy.drawStringWithShadow(text, centerX - (float)legacy.getStringWidth(text) / 2.0F, y, resting);
         return;
      }
      sf.drawStringWithShadow(text, (double)(centerX - sf.getStringWidthFormatted(text) / 2.0F), (double)y, resting);
   }

   public static int getFontHeight() {
      InterFontUtil f = font();
      return Math.round(f != null ? f.getFontHeight() : 9.0F);
   }

   public static int getStringWidth(String text) {
      if (text == null) {
         return 0;
      }
      InterFontUtil font = font();
      return font == null ? bitmap().getStringWidth(text) : Math.round(font.getStringWidthFormatted(text));
   }

   public static int getSmallStringWidth(String text) {
      if (text == null) {
         return 0;
      }
      InterFontUtil sf = smallFont();
      return sf == null ? bitmap().getStringWidth(text) : Math.round(sf.getStringWidthFormatted(text));
   }
}
