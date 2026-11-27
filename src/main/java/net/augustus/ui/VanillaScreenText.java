package net.augustus.ui;

import net.augustus.font.FontManager;
import net.augustus.ui.modern.font.InterFontUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/**
 * Title and message text for the stock screens the client still shows around the redesigned menus: the
 * pause menu, options, world and server selectors, achievements, the share-to-LAN and disconnect screens.
 *
 * <p>WHY this exists: those screens keep their vanilla layout, but their buttons are painted as ClickGUI
 * glass, so a title still rendered in the 8 px bitmap font read as a screenshot glued onto another
 * application. Only the typeface changes here - the anchor stays the top edge, exactly like
 * {@code GuiScreen.drawCenteredString}, so no layout moves; Inter's line box is about 3 px taller and every
 * one of these titles has room for that.</p>
 *
 * <p>Deliberately NOT applied to inventory, sign, book, beacon or achievement-tree text: those are placed
 * pixel-exactly around vanilla 9 px rows and a taller face would collide with their slots and icons.</p>
 */
public final class VanillaScreenText {
   private VanillaScreenText() {
   }

   private static InterFontUtil font() {
      return FontManager.body();
   }

   private static FontRenderer bitmap() {
      return Minecraft.getMinecraft().fontRendererObj;
   }

   /** Replacement for {@code GuiScreen.drawCenteredString(fontRendererObj, text, x, y, color)}. */
   public static void drawCenteredString(String text, int x, int y, int color) {
      InterFontUtil font = font();
      if (font == null) {
         FontRenderer legacy = bitmap();
         legacy.drawStringWithShadow(text, (float)x - (float)legacy.getStringWidth(text) / 2.0F, (float)y, color);
         return;
      }
      font.drawStringWithShadow(text, (double)((float)x - font.getStringWidth(text) / 2.0F), (double)y, color);
   }

   /** Horizontally centred on the screen width, {@code y} still meaning the top edge. */
   public static void drawCenteredOnScreen(int screenWidth, String text, int y, int color) {
      drawCenteredString(text, screenWidth / 2, y, color);
   }

   /** Replacement for {@code GuiScreen.drawString(fontRendererObj, text, x, y, color)}. */
   public static void drawString(String text, int x, int y, int color) {
      InterFontUtil font = font();
      if (font == null) {
         bitmap().drawStringWithShadow(text, (float)x, (float)y, color);
         return;
      }
      font.drawStringWithShadow(text, (double)x, (double)y, color);
   }

   /** Height of the text {@link #drawString} paints, for the callers that stack lines themselves. */
   public static int getFontHeight() {
      return Math.round(FontManager.bodyHeight());
   }
}