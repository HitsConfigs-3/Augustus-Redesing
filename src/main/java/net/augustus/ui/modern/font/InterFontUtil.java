package net.augustus.ui.modern.font;

import java.awt.Font;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.font.UnicodeFontRenderer;
import net.augustus.ui.modern.render.UiTheme;

/**
 * {@link CustomFontUtil} backed by the Inter SDF atlas renderer for ultra-sharp,
 * non-pixelated typography across Medium (500), SemiBold (600), and Bold (700) weights.
 * Automatically delegates non-Latin scripts outside the SDF atlas to a HiDPI Slick TTF fallback.
 */
public class InterFontUtil extends CustomFontUtil {
   private final SdfFontAtlas atlas;
   private final String sdfBase;
   private final float renderSize;
   private final float thicknessBias;
   private final int defaultShadowArgb;
   private final String ttfResourceName;
   private UnicodeFontRenderer lazyFallback;

   public InterFontUtil(String fontName, String sdfBase, String ttfResourceName, float renderSize, float thicknessBias, int defaultShadowArgb) {
      super();
      this.atlas = SdfFontAtlas.get(sdfBase, thicknessBias);
      this.sdfBase = sdfBase;
      this.renderSize = renderSize;
      this.thicknessBias = thicknessBias;
      this.defaultShadowArgb = defaultShadowArgb;
      this.ttfResourceName = ttfResourceName;
      this.scaleOverride = 1;
      this.fontHeight = this.atlas.getVisualHeight(renderSize);
      Font raw = FontManager.loadFontResource(ttfResourceName, Math.round(renderSize * 2.0F));
      this.awtFont = raw != null ? raw : new Font("SansSerif", Font.PLAIN, Math.round(renderSize * 2.0F));
   }

   public InterFontUtil deriveSize(float newSize) {
      return new InterFontUtil(this.getFontName() + "_" + newSize, this.sdfBase, this.ttfResourceName, newSize, this.thicknessBias, this.defaultShadowArgb);
   }

   public float getEffectiveSize() {
      return this.renderSize * UiTheme.uiScale();
   }

   private UnicodeFontRenderer getFallbackRenderer() {
      if (this.lazyFallback == null) {
         this.lazyFallback = new UnicodeFontRenderer(this.awtFont, 1.0F);
      }
      return this.lazyFallback;
   }

   @Override
   public float getFontHeight() {
      return this.atlas.getVisualHeight(this.getEffectiveSize());
   }

   @Override
   public float getStringWidth(String str) {
      if (str == null || str.isEmpty()) {
         return 0.0F;
      }
      if (this.atlas.supportsAllGlyphs(str)) {
         return this.atlas.getStringWidth(str, this.getEffectiveSize());
      }
      return (float)this.getFallbackRenderer().getStringWidth(str) * UiTheme.uiScale();
   }

   @Override
   public float getStringHeight(String str) {
      return this.getFontHeight();
   }

   @Override
   public void drawString(String str, double x, double y, int color) {
      if (str == null || str.isEmpty()) {
         return;
      }
      if (this.atlas.supportsAllGlyphs(str)) {
         this.atlas.drawString(str, (float)x, (float)y, this.getEffectiveSize(), color, this.defaultShadowArgb);
      } else {
         this.getFallbackRenderer().drawString(str, (float)x, (float)y, color);
      }
   }

   @Override
   public void drawStringWithShadow(String str, double x, double y, int color) {
      if (str == null || str.isEmpty()) {
         return;
      }
      if (this.atlas.supportsAllGlyphs(str)) {
         int strongShadow = UiTheme.withAlpha(0x000000, 165);
         this.atlas.drawString(str, (float)x, (float)y, this.getEffectiveSize(), color, strongShadow);
      } else {
         this.getFallbackRenderer().drawStringWithShadow(str, (float)x, (float)y, color);
      }
   }

   @Override
   public void drawString(String str, double x, double y, int color, boolean shadow) {
      if (shadow) {
         this.drawStringWithShadow(str, x, y, color);
      } else {
         this.drawString(str, x, y, color);
      }
   }

   @Override
   public void drawCenteredStringInBox(String str, double x, double y, double w, double h, int color) {
      float sw = this.getStringWidth(str);
      float sh = this.getFontHeight();
      this.drawString(str, x + (w - sw) * 0.5, y + (h - sh) * 0.5, color);
   }

   @Override
   public void drawCenteredStringInBoxWithShadow(String str, double x, double y, double w, double h, int color) {
      float sw = this.getStringWidth(str);
      float sh = this.getFontHeight();
      this.drawStringWithShadow(str, x + (w - sw) * 0.5, y + (h - sh) * 0.5, color);
   }

   @Override
   public void drawHorizontallyCenteredString(String str, double x, double y, double w, int color) {
      float sw = this.getStringWidth(str);
      this.drawString(str, x + (w - sw) * 0.5, y, color);
   }

   @Override
   public void drawVerticallyCenteredString(String str, double x, double y, double h, int color) {
      float sh = this.getFontHeight();
      this.drawString(str, x, y + (h - sh) * 0.5, color);
   }

   @Override
   public void drawVerticallyCenteredStringFixed(String str, double x, double y, double h, int color) {
      float sh = this.getFontHeight();
      this.drawString(str, x, y + (h - sh) * 0.5, color);
   }

   @Override
   public void onGuiScale() {
      // SDF font atlas is resolution-independent and requires no reload on GUI scale changes.
   }

   /**
    * Draws one line at an explicit size instead of {@link #getEffectiveSize()} - the route a scaled title
    * (the 3x/5x "Augustus" on the main menus) takes so the glyphs are rasterised at the final size rather
    * than a small run magnified by {@code glScalef}, which is what made the old title look blocky.
    *
    * <p>{@code withShadow} falls back to the strong shadow {@link #drawStringWithShadow} uses when the face
    * has no shadow of its own (the body faces carry a transparent one), so a scaled title keeps the depth
    * the bitmap call it replaces had.</p>
    */
   public void drawStringAtSize(String str, float x, float y, float customSize, int color, boolean withShadow) {
      if (str == null || str.isEmpty()) {
         return;
      }
      if (this.atlas.supportsAllGlyphs(str)) {
         int shadow = 0;
         if (withShadow) {
            shadow = this.defaultShadowArgb != 0 ? this.defaultShadowArgb : UiTheme.withAlpha(0x000000, 165);
         }
         this.atlas.drawString(str, x, y, customSize, color, shadow);
      } else {
         this.getFallbackRenderer().drawString(str, x, y, color);
      }
   }

   public float getStringWidthSpaced(String str, float letterSpacing) {
      if (str == null || str.isEmpty()) {
         return 0.0F;
      }
      if (this.atlas.supportsAllGlyphs(str)) {
         return this.atlas.getStringWidth(str, this.getEffectiveSize(), letterSpacing * UiTheme.uiScale(), false);
      }
      return (float)this.getFallbackRenderer().getStringWidth(str) * UiTheme.uiScale();
   }

   public void drawStringSpaced(String str, float x, float y, float letterSpacing, int color) {
      if (str == null || str.isEmpty()) {
         return;
      }
      if (this.atlas.supportsAllGlyphs(str)) {
         this.atlas.drawString(str, x, y, this.getEffectiveSize(), color, this.defaultShadowArgb, letterSpacing * UiTheme.uiScale(), false);
      } else {
         this.getFallbackRenderer().drawString(str, x, y, color);
      }
   }

   public void drawGradientString(String str, float x, float y, int leftArgb, int midArgb, int rightArgb) {
      if (str == null || str.isEmpty()) {
         return;
      }
      if (this.atlas.supportsAllGlyphs(str)) {
         this.atlas.drawGradientString(str, x, y, this.getEffectiveSize(), leftArgb, midArgb, rightArgb, this.defaultShadowArgb, 0.0F);
      } else {
         this.getFallbackRenderer().drawString(str, x, y, leftArgb);
      }
   }

   public void drawGradientStringSpaced(String str, float x, float y, float letterSpacing, int leftArgb, int midArgb, int rightArgb) {
      if (str == null || str.isEmpty()) {
         return;
      }
      if (this.atlas.supportsAllGlyphs(str)) {
         this.atlas.drawGradientString(str, x, y, this.getEffectiveSize(), leftArgb, midArgb, rightArgb, this.defaultShadowArgb, letterSpacing * UiTheme.uiScale());
      } else {
         this.getFallbackRenderer().drawString(str, x, y, leftArgb);
      }
   }

   public float getStringWidthTabular(String str) {
      if (str == null || str.isEmpty()) {
         return 0.0F;
      }
      if (this.atlas.supportsAllGlyphs(str)) {
         return this.atlas.getStringWidth(str, this.getEffectiveSize(), 0.0F, true);
      }
      return (float)this.getFallbackRenderer().getStringWidth(str) * UiTheme.uiScale();
   }

   public void drawStringTabular(String str, float x, float y, int color) {
      if (str == null || str.isEmpty()) {
         return;
      }
      if (this.atlas.supportsAllGlyphs(str)) {
         this.atlas.drawString(str, x, y, this.getEffectiveSize(), color, this.defaultShadowArgb, 0.0F, true);
      } else {
         this.getFallbackRenderer().drawString(str, x, y, color);
      }
   }

   public void drawCenteredStringInBoxTabular(String str, double x, double y, double w, double h, int color) {
      float sw = this.getStringWidthTabular(str);
      float sh = this.getFontHeight();
      this.drawStringTabular(str, (float)(x + (w - sw) * 0.5), (float)(y + (h - sh) * 0.5), color);
   }

   // ---------------------------------------------------------------------------------------------
   // Legacy '§' formatted strings, rendered with Inter instead of the bitmap font.
   //
   // WHY this exists: a large part of the stock 1.8.9 UI labels its controls with section-sign colour
   // codes ("Difficulty: §cHard", "Remove invalid: §cFalse", "§cFernan's Altshop"). Those strings used to
   // be bounced back to the bitmap FontRenderer because the SDF atlas has no '§' glyph, which is exactly
   // why a handful of buttons in an otherwise Inter-only menu still looked pixelated. These helpers parse
   // the codes and paint each run with the same Inter face, so every label on a screen shares one
   // typeface while still keeping the colour the original string asked for.
   // ---------------------------------------------------------------------------------------------

   /** Legacy formatting colours, index 0-15 = §0-§f. */
   private static final int[] LEGACY_COLORS = {
      0xFF000000, 0xFF0000AA, 0xFF00AA00, 0xFF00AAAA, 0xFFAA0000, 0xFFAA00AA, 0xFFFFAA00, 0xFFAAAAAA,
      0xFF555555, 0xFF5555FF, 0xFF55FF55, 0xFF55FFFF, 0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF
   };

   /**
    * Resolves a '§' code to its colour, or returns {@code -1} when the code is not a colour one.
    *
    * <p>'§r' answers {@link Integer#MIN_VALUE} so callers can distinguish "reset to the caller's colour"
    * from "not a colour code at all".</p>
    */
   public static int legacyColorOf(char code) {
      if (code >= '0' && code <= '9') {
         return LEGACY_COLORS[code - '0'];
      }
      if (code >= 'a' && code <= 'f') {
         return LEGACY_COLORS[10 + (code - 'a')];
      }
      if (code >= 'A' && code <= 'F') {
         return LEGACY_COLORS[10 + (code - 'A')];
      }
      return code == 'r' || code == 'R' ? Integer.MIN_VALUE : -1;
   }

   /** True when any '§' formatting code is present. */
   public static boolean hasFormatting(String str) {
      return str != null && str.indexOf('\u00a7') >= 0;
   }

   /** Drops every '§x' pair; used for measuring and for the SDF glyph-coverage test. */
   public static String stripFormatting(String str) {
      if (str == null || str.indexOf('\u00a7') < 0) {
         return str;
      }
      StringBuilder out = new StringBuilder(str.length());
      for (int i = 0; i < str.length(); i++) {
         char c = str.charAt(i);
         if (c == '\u00a7' && i + 1 < str.length()) {
            i++;
         } else {
            out.append(c);
         }
      }
      return out.toString();
   }

   /** Width of a '§'-formatted string measured with this Inter face (codes add no advance). */
   public float getStringWidthFormatted(String str) {
      if (str == null || str.isEmpty()) {
         return 0.0F;
      }
      if (!hasFormatting(str)) {
         return this.getStringWidth(str);
      }
      float total = 0.0F;
      int runStart = 0;
      for (int i = 0; i < str.length(); i++) {
         if (str.charAt(i) == '\u00a7' && i + 1 < str.length()) {
            if (i > runStart) {
               total += this.getStringWidth(str.substring(runStart, i));
            }
            i++;
            runStart = i + 1;
         }
      }
      if (runStart < str.length()) {
         total += this.getStringWidth(str.substring(runStart));
      }
      return total;
   }

   /**
    * Draws a '§'-formatted string with Inter, honouring legacy colour codes (and §r).
    *
    * <p>Style codes (bold/italic/underline/strikethrough/obfuscated) are consumed but not applied: the
    * SDF atlas has no synthetic styles, and printing them literally would be worse than ignoring them.</p>
    */
   public void drawStringFormatted(String str, double x, double y, int baseColor, boolean shadow) {
      if (str == null || str.isEmpty()) {
         return;
      }
      if (!hasFormatting(str)) {
         if (shadow) {
            this.drawStringWithShadow(str, x, y, baseColor);
         } else {
            this.drawString(str, x, y, baseColor);
         }
         return;
      }
      float penX = (float)x;
      float penY = (float)y;
      int color = baseColor;
      int runStart = 0;
      for (int i = 0; i < str.length(); i++) {
         if (str.charAt(i) != '\u00a7' || i + 1 >= str.length()) {
            continue;
         }
         if (i > runStart) {
            String run = str.substring(runStart, i);
            if (shadow) {
               this.drawStringWithShadow(run, penX, penY, color);
            } else {
               this.drawString(run, penX, penY, color);
            }
            penX += this.getStringWidth(run);
         }
         int resolved = legacyColorOf(str.charAt(i + 1));
         if (resolved >= 0) {
            color = resolved;
         } else if (resolved == Integer.MIN_VALUE) {
            color = baseColor;
         }
         i++;
         runStart = i + 1;
      }
      if (runStart < str.length()) {
         String run = str.substring(runStart);
         if (shadow) {
            this.drawStringWithShadow(run, penX, penY, color);
         } else {
            this.drawString(run, penX, penY, color);
         }
      }
   }

   /** Centres a '§'-formatted string inside a box, exactly like {@link #drawCenteredStringInBox}. */
   public void drawCenteredStringFormattedInBox(String str, double x, double y, double w, double h, int color) {
      float sw = this.getStringWidthFormatted(str);
      float sh = this.getFontHeight();
      this.drawStringFormatted(str, x + (w - sw) * 0.5, y + (h - sh) * 0.5, color, false);
   }

   /** Horizontally centred '§'-formatted string at an explicit baseline-ish y. */
   public void drawHorizontallyCenteredStringFormatted(String str, double x, double y, double w, int color) {
      float sw = this.getStringWidthFormatted(str);
      this.drawStringFormatted(str, x + (w - sw) * 0.5, y, color, false);
   }

   // ---------------------------------------------------------------------------------------------
   // Inter replacements for FontRenderer#trimStringToWidth, so text fields can scroll and place the
   // caret with the same metrics they are drawn with.
   // ---------------------------------------------------------------------------------------------

   /** Longest prefix of {@code str} that fits in {@code maxWidth} pixels (Inter metrics). */
   public String trimToWidth(String str, float maxWidth) {
      if (str == null || str.isEmpty() || maxWidth < 0.0F) {
         return "";
      }
      if (this.getStringWidth(str) <= maxWidth) {
         return str;
      }
      int i = str.length();
      while (i > 0 && this.getStringWidth(str.substring(0, i)) > maxWidth) {
         i--;
      }
      // Keep surrogate pairs intact so an emoji or a supplementary glyph is never split in half.
      if (i > 0 && i < str.length() && Character.isHighSurrogate(str.charAt(i - 1))) {
         i--;
      }
      return str.substring(0, i);
   }

   /** Longest suffix of {@code str} that fits in {@code maxWidth} pixels (Inter metrics). */
   public String trimToWidthReverse(String str, float maxWidth) {
      if (str == null || str.isEmpty() || maxWidth < 0.0F) {
         return "";
      }
      if (this.getStringWidth(str) <= maxWidth) {
         return str;
      }
      int i = 0;
      while (i < str.length() && this.getStringWidth(str.substring(i)) > maxWidth) {
         i++;
      }
      if (i < str.length() && Character.isLowSurrogate(str.charAt(i))) {
         i++;
      }
      return str.substring(i);
   }
}
