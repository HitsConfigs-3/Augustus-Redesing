package net.augustus.settings;

import java.util.List;
import net.augustus.clickgui.ClickGuiScreen;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.font.TextWrapper;
import net.augustus.modules.Module;
import net.augustus.ui.modern.components.ModernSettingsPanel;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.UiTheme;

public class TextLabelSetting extends Setting {
   private int renderedHeight = 36;
   private String text;

   /**
    * Applies the ClickGUI open/close envelope to one ARGB at the point of draw.
    *
    * <p>WHY this row needs its own fade: the card surface is drawn by
    * {@code ModernSettingsPanel.drawGlassCardSurface} (which fades its own colours), but the accent bar and
    * the text of this label are painted here, so they would otherwise stay bright over a fading card for the
    * whole ~290 ms close. Only alpha is scaled: the wrapped line layout and the measured card height are
    * untouched, because this is a colour fade and not a reflow.
    *
    * <p>Each colour is wrapped exactly once, at the draw.
    */
   private static int faded(int argb) {
      // Settled overlay (everything except the ~288 ms open/close transition): skip the arithmetic.
      return ClickGuiScreen.getScreenFade() >= 0.999F ? argb : ClickGuiScreen.fadeArgb(argb);
   }

   public TextLabelSetting(Module module, SettingGroup doubleValue, String str) {
      super(module, doubleValue, str);
      this.text = str;
      this.renderedHeight = 36;
   }

   @Override
   public void render(int n, int n2, int n3, int n4, int n5, int n6, CustomFontUtil customFontUtil, boolean flag) {
      super.render(n, n2, n3, n4, n5, n6, customFontUtil, flag);
      String display = this.text != null ? this.text : this.getDisplayName();
      float cardW = ModernSettingsPanel.resolveCardWidth(n3, n5);
      CustomFontUtil font = FontManager.interMedium;
      float innerW = Math.max(120.0F, cardW - ModernSettingsPanel.CARD_PAD_X * 2.0F - 8.0F);
      List<String> lines = TextWrapper.wrapText(display, (int)innerW, font);
      float lineH = font.getFontHeight();
      int lineCount = Math.max(1, lines.size());
      int cardH = Math.max(36, Math.round(ModernSettingsPanel.CARD_PAD_Y * 2.0F + lineCount * lineH + Math.max(0, lineCount - 1) * 3.0F));
      this.renderedHeight = cardH;
      this.contentWidth = (int)Math.ceil((double)cardW);

      if (flag) {
         // The card surface fades itself (it is shared with every other setting row); the bar and the
         // text below are this row's own paint, so they carry the envelope here.
         ModernSettingsPanel.drawGlassCardSurface((float)n3, (float)n4, cardW, (float)cardH, ModernSettingsPanel.CARD_RADIUS, false, false);
         GlassRenderer.drawRoundedRect((float)n3 + 6.0F, (float)n4 + 8.0F, 3.0F, (float)cardH - 16.0F, 1.5F, faded(UiTheme.withAlpha(UiTheme.accentColor(), 200)));
         if (lines.isEmpty()) {
            font.drawVerticallyCenteredString(display, (double)(n3 + 16.0F), (double)n4, (double)cardH, faded(UiTheme.secondaryTextColor()));
         } else {
            float textY = (float)n4 + ModernSettingsPanel.CARD_PAD_Y;
            for (String line : lines) {
               font.drawString(line, (double)(n3 + 16.0F), (double)textY, faded(UiTheme.secondaryTextColor()));
               textY += lineH + 3.0F;
            }
         }
      }
   }

   @Override
   public void resetToDefault() {
   }

   @Override
   public int getHeight() {
      return this.renderedHeight;
   }

   public void setText(String str) {
      this.text = str;
      int n = 1;
      if (str != null) {
         for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == '\n') {
               n++;
            }
         }
      }
      this.renderedHeight = Math.max(n * 18 + 18, 36);
   }

   @Override
   public void renderTooltip() {
   }
}
