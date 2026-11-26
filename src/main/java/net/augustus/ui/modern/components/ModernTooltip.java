package net.augustus.ui.modern.components;

import java.util.List;
import net.augustus.clickgui.ClickGuiScreen;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.font.TextWrapper;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

/**
 * Reusable pure transparent Liquid Glass floating tooltip with legible Inter typography,
 * crisp ambient rim borders, and smooth scale + fade entrance animation.
 */
public final class ModernTooltip {
   private static final float PAD_X = 11.0F;
   private static final float PAD_Y = 7.5F;
   private static final float LINE_GAP = 3.0F;

   private static String lastTooltipText = null;
   private static long lastDrawTimeMs = 0L;
   private static float openAnim = 0.0F;

   private ModernTooltip() {
   }

   /**
    * Applies the ClickGUI open/close envelope to one ARGB at the point of draw.
    *
    * <p>WHY it is folded in here and not passed in: this class is only ever reached through the ClickGUI's
    * own tooltip path, and the tooltip has its own entrance envelope ({@code eased}). Applying the overlay
    * envelope to the already-eased colour keeps both factors on the same value exactly once - the tooltip's
    * own alpha stays inside {@code eased}, and the overlay multiplies the finished colour. Multiplying
    * {@code eased} by the overlay separately would be a second application and would fade at the square.
    *
    * <p>The border and the text are each wrapped once, at their draw.
    */
   private static int faded(int argb) {
      // Settled overlay (everything except the ~288 ms open/close transition): skip the arithmetic.
      return ClickGuiScreen.getScreenFade() >= 0.999F ? argb : ClickGuiScreen.fadeArgb(argb);
   }

   public static void draw(CustomFontUtil preferredFont, String text, int anchorX, int anchorY, int maxWrapWidth) {
      if (text == null || text.isEmpty()) {
         return;
      }

      long now = System.currentTimeMillis();
      if (!text.equals(lastTooltipText) || now - lastDrawTimeMs > 120L) {
         openAnim = 0.0F;
         lastTooltipText = text;
      }
      lastDrawTimeMs = now;
      openAnim = UiAnimation.smooth(openAnim, 1.0F, 22.0F);
      float eased = UiAnimation.easeOutCubic(openAnim);
      if (eased <= 0.01F) {
         eased = 0.02F;
      }

      CustomFontUtil font = preferredFont != null ? preferredFont : FontManager.interMedium;
      List<String> lines = TextWrapper.wrapText(text, Math.max(160, maxWrapWidth), font);
      if (lines.isEmpty()) {
         return;
      }

      float maxLineW = 0.0F;
      for (String line : lines) {
         maxLineW = Math.max(maxLineW, font.getStringWidth(line));
      }

      float lineH = font.getFontHeight();
      float boxW = maxLineW + PAD_X * 2.0F;
      float boxH = lines.size() * lineH + Math.max(0, lines.size() - 1) * LINE_GAP + PAD_Y * 2.0F;

      int screenW = Math.max(320, Display.getWidth());
      int screenH = Math.max(240, Display.getHeight());
      float x = anchorX + 14.0F;
      float y = anchorY + 12.0F - (1.0F - eased) * 3.0F;

      if (x + boxW > screenW - 8.0F) {
         x = Math.max(8.0F, anchorX - boxW - 10.0F);
      }
      if (y + boxH > screenH - 8.0F) {
         y = Math.max(8.0F, screenH - boxH - 8.0F);
      }

      float scale = 0.95F + 0.05F * eased;
      float pivotX = x + boxW * 0.5F;
      float pivotY = y;
      GL11.glPushMatrix();
      GL11.glTranslatef(pivotX, pivotY, 0.0F);
      GL11.glScalef(scale, scale, 1.0F);
      GL11.glTranslatef(-pivotX, -pivotY, 0.0F);

      GlassRenderer.drawFloatingGlassPopup(x, y, boxW, boxH, UiTheme.POPUP_RADIUS, UiTheme.BORDER_THICKNESS, faded(UiTheme.GLASS_BORDER_STRONG), eased);

      // `eased` is this tooltip's own entrance envelope; the overlay envelope is multiplied onto the
      // finished colour here, once, so the two factors never compound.
      int textArgb = faded(UiTheme.scaleAlpha(UiTheme.TEXT_PRIMARY, eased));
      float textY = y + PAD_Y;
      for (String line : lines) {
         font.drawString(line, (double)(x + PAD_X), (double)textY, textArgb);
         textY += lineH + LINE_GAP;
      }

      GL11.glPopMatrix();
   }
}
