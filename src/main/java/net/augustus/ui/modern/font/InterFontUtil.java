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

   public void drawStringAtSize(String str, float x, float y, float customSize, int color, boolean withShadow) {
      if (str == null || str.isEmpty()) {
         return;
      }
      if (this.atlas.supportsAllGlyphs(str)) {
         int shadow = withShadow ? this.defaultShadowArgb : 0;
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
}
