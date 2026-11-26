package net.augustus.clickgui;

import net.augustus.Augustus;
import net.augustus.font.CustomFontUtil;
import net.augustus.ui.modern.components.ModernTooltip;
import net.augustus.utils.interfaces.MC;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

public class TooltipRenderer implements MC {
   private static final int DEFAULT_MAX_WIDTH = Math.min(Display.getWidth() / 2, Display.getWidth());

   // NOTE (close-transition): this class deliberately paints no colour of its own, so there is nothing here
   // to multiply the ClickGUI overlay envelope into. drawTooltip() only manages the scissor state and then
   // hands the request to ModernTooltip.draw, which is where the tooltip's border and text are actually
   // painted - and that is where ModernTooltip applies the envelope, once, on top of its own entrance
   // envelope. Fading here as well would be a second application (the tooltip would fade at the square of
   // the intended rate while the rest of the overlay faded linearly).
   public static void drawTooltip(CustomFontUtil customFontUtil, String str, int n, int n2) {
      drawTooltip(customFontUtil, str, n, n2, DEFAULT_MAX_WIDTH);
   }

   public static void drawTooltip(CustomFontUtil customFontUtil, String str, int n, int n2, int n3) {
      MainClickGuiWindow mw = Augustus.getInstance().getClickGui().getMainWindow();
      if (mw.isHoveringModuleContextMenu() || mw.getPopupHost().hasActivePopup()) {
         return;
      }
      boolean scissorActive = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
      if (scissorActive) {
         GL11.glDisable(GL11.GL_SCISSOR_TEST);
      }
      try {
         ModernTooltip.draw(customFontUtil, str, n, n2, n3);
      } finally {
         if (scissorActive) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
         }
      }
   }
}
