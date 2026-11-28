package net.augustus.ui.menu;

import java.awt.Color;
import net.augustus.font.FontManager;
import net.augustus.ui.AugustusGuiScreen;
import net.augustus.ui.TabbedMenuScreen;
import net.augustus.ui.modern.font.InterFontUtil;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.MenuScreenSkin;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;

/**
 * Base page of the tabbed client menu ({@link TabbedMenuScreen}) and shared Augustus Redesing
 * glass, typography, and cursor-hover primitives.
 */
public abstract class ClientMenuTab extends AugustusGuiScreen {
   private static final int PILL_FILL_TOP = MenuScreenSkin.FILL_TOP;
   private static final int PILL_FILL_BOTTOM = MenuScreenSkin.FILL_BOTTOM;
   private static final float PILL_VEIL = MenuScreenSkin.VEIL;
   private static final float PILL_EDGE_MIX = MenuScreenSkin.EDGE_MIX;
   private static final int PILL_EDGE_ALPHA = MenuScreenSkin.EDGE_ALPHA;
   private static final int PILL_EDGE_HOVER_ALPHA = MenuScreenSkin.EDGE_HOVER_ALPHA;
   private static final float PILL_LIGHT_PEAK = MenuScreenSkin.LIGHT_PEAK;
   private static final float PILL_LIGHT_RADIUS = 0.46F;
   private static final float PILL_LIGHT_LIFT = MenuScreenSkin.LIGHT_LIFT;
   private static final float PILL_PRESS_DARKEN = MenuScreenSkin.PRESS_DARKEN;
   private static final float FROST_STRENGTH = 1.0F;
   private static final int FROST_ARC_STEPS = 6;

   public static final int TEXT_BASE = MenuScreenSkin.LABEL_COLOR;
   public static final int TEXT_SUB = 0xB8B7A9C8;
   public static final int CONTAINER_TOP = 0xFF140D22;
   public static final int CONTAINER_BOTTOM = 0xFF0E0919;

   private static InterFontUtil cachedTitleFont;
   private static InterFontUtil cachedHeadingFont;
   private static InterFontUtil cachedBodyFont;
   private static InterFontUtil cachedSmallFont;
   private static InterFontUtil cachedCaptionFont;

   private Color statusColor;
   private final String tabTitle;
   private String statusText = null;

   public static Color getStatusColor(ClientMenuTab clientMenuTab) {
      return clientMenuTab.statusColor;
   }

   public void setStatus(String str, Color color) {
      this.statusText = str;
      this.statusColor = color;
   }

   public ClientMenuTab(String str) {
      this.statusColor = null;
      this.tabTitle = str;
   }

   public static String getStatusText(ClientMenuTab clientMenuTab) {
      return clientMenuTab.statusText;
   }

   public String getTabTitle() {
      return this.tabTitle;
   }

   public LucideIcon getTabIcon() {
      return null;
   }

   /**
    * Surfaces painted behind widgets (cards behind text fields, right-side list containers).
    */
   public void drawChromeBehind() {
   }

   // ---------------------------------------------------------------------------------------------
   // Surfaces
   // ---------------------------------------------------------------------------------------------

   protected static float cardRadius(float width, float height) {
      return Math.max(0.0F, Math.min(Math.min(width, height) * 0.5F, 8.5F));
   }

   /**
    * Outer frosted glass container panel (sidebar frame, right list container, changelog sheet).
    */
   public final void menuGlassPanel(float x, float y, float width, float height, float hover, float alpha) {
      if (width <= 0.0F || height <= 0.0F) {
         return;
      }
      float h = UiAnimation.clamp01(hover);
      float a = UiAnimation.clamp01(alpha);
      if (a <= 0.004F) {
         return;
      }
      float r = cardRadius(width, height);
      GlassRenderer.drawSoftShadow(
         x, y, width, height, r, 10.0F, 3.0F,
         UiTheme.scaleAlpha(0x4504020A, a)
      );
      this.menuFrost(x, y, width, height, r, a);

      int accent = UiTheme.accentColor();
      int topFill = UiTheme.lerpArgb(CONTAINER_TOP, accent, 0.06F);
      int botFill = UiTheme.lerpArgb(CONTAINER_BOTTOM, UiTheme.accentDeep(), 0.05F);
      int restBorder = UiTheme.lerpArgb(0x28FFFFFF, accent, 0.24F);
      int hovBorder = UiTheme.withAlpha(UiTheme.lerpArgb(accent, 0xFFE2DCFF, 0.22F), 210);
      int border = UiTheme.lerpArgb(restBorder, hovBorder, h);

      float lightX = Math.max(x + 12.0F, Math.min(x + width - 12.0F, TabbedMenuScreen.mouseX()));
      float lightY = Math.max(y + 12.0F, Math.min(y + height - 12.0F, TabbedMenuScreen.mouseY()));

      GlassRenderer.drawMenuGlassBox(
         x,
         y,
         width,
         height,
         r,
         UiTheme.scaleAlpha(topFill, 0.72F * a),
         UiTheme.scaleAlpha(botFill, 0.78F * a),
         UiTheme.scaleAlpha(border, a),
         UiTheme.BORDER_THICKNESS,
         lightX,
         lightY,
         0.10F * h * a,
         Math.max(width, height) * 0.42F,
         UiTheme.scaleAlpha(UiTheme.lerpArgb(accent, 0xFFFFFFFF, 0.45F), a)
      );
   }

   public final void menuCard(float x, float y, float width, float height, float hover, float alpha) {
      this.menuGlassPanel(x, y, width, height, hover, alpha);
   }

   public final void menuRow(float x, float y, float width, float height, float hover, float alpha) {
      this.menuSurface(x, y, width, height, Math.min(6.0F, Math.min(width, height) * 0.5F), hover, 0.0F, alpha);
   }

   public final void menuSurface(float x, float y, float width, float height, float radius, float hover,
         float press, float alpha) {
      if (width <= 0.0F || height <= 0.0F) {
         return;
      }
      float h = UiAnimation.clamp01(hover);
      float p = UiAnimation.clamp01(press);
      float a = UiAnimation.clamp01(alpha);
      if (a <= 0.004F) {
         return;
      }
      float r = Math.min(radius, Math.min(width, height) * 0.5F);
      this.menuFrost(x, y, width, height, r, a);

      int accent = UiTheme.accentColor();
      if (h > 0.04F) {
         GlassRenderer.drawSoftShadow(
            x, y, width, height, r, 6.0F, 0.5F,
            UiTheme.scaleAlpha(UiTheme.withAlpha(accent, Math.round(32.0F * h)), a)
         );
      }

      float lightX = Math.max(x + width * 0.12F, Math.min(x + width * 0.88F, TabbedMenuScreen.mouseX()));
      float lightY = Math.max(y + height * 0.20F, Math.min(y + height * 0.80F, TabbedMenuScreen.mouseY()));

      int edgeRgb = UiTheme.lerpArgb(accent, PILL_FILL_TOP, PILL_EDGE_MIX);
      int edgeAlpha = Math.round(PILL_EDGE_ALPHA + (PILL_EDGE_HOVER_ALPHA - PILL_EDGE_ALPHA) * h);

      GlassRenderer.drawMenuGlassBox(
         x,
         y,
         width,
         height,
         r,
         UiTheme.scaleAlpha(PILL_FILL_TOP, PILL_VEIL * a * (1.0F - PILL_PRESS_DARKEN * p)),
         UiTheme.scaleAlpha(PILL_FILL_BOTTOM, PILL_VEIL * a * (1.0F - PILL_PRESS_DARKEN * p)),
         UiTheme.scaleAlpha(UiTheme.withAlpha(edgeRgb, edgeAlpha), a),
         UiTheme.BORDER_THICKNESS,
         lightX,
         lightY,
         PILL_LIGHT_PEAK * h * a,
         Math.max(width, height) * PILL_LIGHT_RADIUS,
         UiTheme.scaleAlpha(UiTheme.lerpArgb(accent, 0xFFFFFFFF, PILL_LIGHT_LIFT), a)
      );
   }

   protected void menuFrost(float x, float y, float width, float height, float radius, float alpha) {
      MenuBackdrop backdrop = TabbedMenuScreen.backdrop();
      float strength = FROST_STRENGTH * UiAnimation.clamp01(alpha);
      if (backdrop == null || strength <= 0.004F || width < 2.0F || height < 2.0F) {
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
         backdrop.drawFrost(fx, fy, fw, fh, strength);
         return;
      }
      backdrop.drawFrost(fx, fy + r, fw, fh - r * 2.0F, strength);
      for (int i = 0; i < FROST_ARC_STEPS; i++) {
         float outer = r * (float)i / (float)FROST_ARC_STEPS;
         float inner = r * (float)(i + 1) / (float)FROST_ARC_STEPS;
         float insetStep = arcInset(r, outer);
         float rowW = fw - insetStep * 2.0F;
         if (rowW <= 0.0F) {
            continue;
         }
         backdrop.drawFrost(fx + insetStep, fy + outer, rowW, inner - outer, strength);
         backdrop.drawFrost(fx + insetStep, fy + fh - inner, rowW, inner - outer, strength);
      }
   }

   private static float arcInset(float radius, float distance) {
      float d = Math.max(0.0F, Math.min(radius, distance));
      float k = radius - d;
      float square = radius * radius - k * k;
      return radius - (float)Math.sqrt((double)Math.max(0.0F, square));
   }

   public final void menuEmptyState(float x, float y, float width, float height, LucideIcon icon, String title,
         String hint) {
      this.menuEmptyState(x, y, width, height, icon, title, hint, true);
   }

   public final void menuEmptyState(float x, float y, float width, float height, LucideIcon icon, String title,
         String hint, boolean drawContainer) {
      if (width < 40.0F || height < 32.0F) {
         return;
      }
      if (drawContainer) {
         this.menuGlassPanel(x, y, width, height, 0.0F, 0.92F);
      }

      InterFontUtil titleFace = menuHeadingFont();
      InterFontUtil hintFace = menuSmallFont();
      float cx = x + width * 0.5F;
      float cy = y + height * 0.5F;
      float cursorY = cy - (icon != null ? 22.0F : 10.0F);
      int accent = UiTheme.accentColor();

      if (icon != null && icon.isAvailable()) {
         GlassRenderer.drawSoftShadow(cx - 15.0F, cursorY - 15.0F, 30.0F, 30.0F, 15.0F, 12.0F, 0.0F,
               UiTheme.withAlpha(accent, 95));
         GlassRenderer.drawCircle(cx, cursorY, 15.0F, UiTheme.withAlpha(accent, 34),
               UiTheme.withAlpha(UiTheme.accentSoftGlow(), 90), 1.0F);
         icon.drawCentered(cx, cursorY, 16.0F, UiTheme.lerpArgb(accent, 0xFFFFFFFF, 0.35F), false);
         cursorY += 23.0F;
      }

      if (titleFace != null && title != null) {
         titleFace.drawStringWithShadow(title, (double)(cx - titleFace.getStringWidth(title) * 0.5F),
               (double)cursorY, TEXT_BASE);
         cursorY += titleFace.getFontHeight() + 4.0F;
      }

      if (hintFace != null && hint != null && height > 76.0F) {
         float maxW = Math.max(40.0F, width - 28.0F);
         if (hintFace.getStringWidth(hint) <= maxW) {
            hintFace.drawStringWithShadow(hint, (double)(cx - hintFace.getStringWidth(hint) * 0.5F),
                  (double)cursorY, UiTheme.scaleAlpha(TEXT_SUB, 0.92F));
         } else {
            String[] words = hint.split(" ");
            StringBuilder line = new StringBuilder();
            for (String w : words) {
               String test = line.length() == 0 ? w : line + " " + w;
               if (hintFace.getStringWidth(test) > maxW && line.length() > 0) {
                  String s = line.toString();
                  hintFace.drawStringWithShadow(s, (double)(cx - hintFace.getStringWidth(s) * 0.5F),
                        (double)cursorY, UiTheme.scaleAlpha(TEXT_SUB, 0.92F));
                  cursorY += hintFace.getFontHeight() + 1.5F;
                  line = new StringBuilder(w);
               } else {
                  line = new StringBuilder(test);
               }
            }
            if (line.length() > 0) {
               String s = line.toString();
               hintFace.drawStringWithShadow(s, (double)(cx - hintFace.getStringWidth(s) * 0.5F),
                     (double)cursorY, UiTheme.scaleAlpha(TEXT_SUB, 0.92F));
            }
         }
      }
   }

   /** Renders a compact pill badge (e.g. "Latest", "SOCKS5", count pill). Returns width drawn. */
   public static float menuBadge(String text, float x, float y, int bgArgb, int textArgb) {
      InterFontUtil face = menuCaptionFont();
      if (face == null || text == null || text.isEmpty()) {
         return 0.0F;
      }
      float padX = 5.5F;
      float h = Math.max(12.0F, face.getFontHeight() + 3.5F);
      float w = face.getStringWidth(text) + padX * 2.0F;
      GlassRenderer.drawRoundedRect(x, y, w, h, 4.0F, bgArgb);
      face.drawCenteredStringInBox(text, (double)x, (double)y, (double)w, (double)h, textArgb);
      return w;
   }

   protected static int menuAccentTint(float alpha) {
      return UiTheme.scaleAlpha(UiTheme.lerpArgb(UiTheme.accentColor(), 0xFFFFFFFF, 0.24F), alpha);
   }

   public static void menuAccentRail(float x, float y, float height, float alpha) {
      float thickness = Math.max(1.5F, UiTheme.BORDER_THICKNESS * 2.2F);
      float radius = thickness * 0.5F;
      float railH = Math.min(height, height * 0.56F);
      GlassRenderer.drawRoundedRect(x, y + (height - railH) * 0.5F, thickness,
            railH, radius, UiTheme.scaleAlpha(UiTheme.accentColor(), alpha));
   }

   // ---------------------------------------------------------------------------------------------
   // Crisp Smaller Typography System
   // ---------------------------------------------------------------------------------------------

   public static InterFontUtil menuTitleFont() {
      if (cachedTitleFont == null && FontManager.interSemiBold != null) {
         cachedTitleFont = FontManager.interSemiBold.deriveSize(14.5F);
      }
      return cachedTitleFont != null ? cachedTitleFont : FontManager.body();
   }

   public static InterFontUtil menuHeadingFont() {
      if (cachedHeadingFont == null && FontManager.interSemiBold != null) {
         cachedHeadingFont = FontManager.interSemiBold.deriveSize(12.5F);
      }
      return cachedHeadingFont != null ? cachedHeadingFont : FontManager.body();
   }

   public static InterFontUtil menuBodyFont() {
      if (cachedBodyFont == null && FontManager.interMedium != null) {
         cachedBodyFont = FontManager.interMedium.deriveSize(10.5F);
      }
      return cachedBodyFont != null ? cachedBodyFont : FontManager.body();
   }

   public static InterFontUtil menuSmallFont() {
      if (cachedSmallFont == null && FontManager.interRegular != null) {
         cachedSmallFont = FontManager.interRegular.deriveSize(9.5F);
      }
      return cachedSmallFont != null ? cachedSmallFont : menuBodyFont();
   }

   public static InterFontUtil menuCaptionFont() {
      if (cachedCaptionFont == null && FontManager.interSemiBold != null) {
         cachedCaptionFont = FontManager.interSemiBold.deriveSize(8.5F);
      }
      return cachedCaptionFont != null ? cachedCaptionFont : menuSmallFont();
   }

   protected static InterFontUtil menuFace() {
      return menuBodyFont();
   }

   public static float menuTextWidth(String text) {
      InterFontUtil face = menuFace();
      return face == null || text == null ? 0.0F : face.getStringWidthFormatted(text);
   }

   public static float menuTextHeight() {
      InterFontUtil face = menuFace();
      return face == null ? 8.5F : face.getFontHeight();
   }

   public static float menuSmallHeight() {
      InterFontUtil face = menuSmallFont();
      return face == null ? 8.0F : face.getFontHeight();
   }

   public static float menuCaptionHeight() {
      InterFontUtil face = menuCaptionFont();
      return face == null ? 7.5F : face.getFontHeight();
   }

   public static void menuHeading(String text, float x, float y, int argb) {
      InterFontUtil face = menuHeadingFont();
      if (face != null && text != null && !text.isEmpty()) {
         face.drawStringWithShadow(text, (double)x, (double)y, argb);
      }
   }

   public static float menuHeadingHeight() {
      InterFontUtil face = menuHeadingFont();
      return face == null ? 10.0F : face.getFontHeight();
   }

   public static void menuText(String text, float x, float y, int argb) {
      InterFontUtil face = menuFace();
      if (face != null && text != null && !text.isEmpty()) {
         face.drawStringFormatted(text, (double)x, (double)y, argb, true);
      }
   }

   public static void menuSmallText(String text, float x, float y, int argb) {
      InterFontUtil face = menuSmallFont();
      if (face != null && text != null && !text.isEmpty()) {
         face.drawStringFormatted(text, (double)x, (double)y, argb, true);
      }
   }

   public static void menuTextRight(String text, float rightX, float y, int argb) {
      menuText(text, rightX - menuTextWidth(text), y, argb);
   }

   public static void menuTextCentered(String text, float centreX, float y, int argb) {
      menuText(text, centreX - menuTextWidth(text) * 0.5F, y, argb);
   }

   protected static void menuSectionLabel(String text, float x, float y, float alpha) {
      InterFontUtil face = menuCaptionFont();
      if (face == null || text == null) {
         return;
      }
      face.drawStringSpaced(text, x, y, 0.65F, UiTheme.scaleAlpha(TEXT_SUB, 0.85F * alpha));
   }

   public static String menuFit(String text, float maxWidth) {
      return menuFit(menuFace(), text, maxWidth);
   }

   public static String menuFit(InterFontUtil face, String text, float maxWidth) {
      if (face == null || text == null) {
         return text == null ? "" : text;
      }
      if (face.getStringWidthFormatted(text) <= maxWidth) {
         return text;
      }
      String plain = InterFontUtil.stripFormatting(text);
      return face.trimToWidth(plain, Math.max(0.0F, maxWidth - face.getStringWidth("..."))) + "...";
   }

   protected static void menuSpreadRow(String left, String right, float x, float rightX, float y) {
      menuText(left, x, y, TEXT_SUB);
      menuTextRight(right, rightX, y, TEXT_BASE);
   }

   // ---------------------------------------------------------------------------------------------
   // Motion
   // ---------------------------------------------------------------------------------------------

   public final float menuHover(String id, boolean active) {
      return UiTheme.smoothState("tabs." + this.tabTitle + "." + id, active ? 1.0F : 0.0F);
   }

   protected static boolean menuHovered(float x, float y, float width, float height, int mouseX, int mouseY) {
      return (float)mouseX >= x && (float)mouseX <= x + width && (float)mouseY >= y && (float)mouseY <= y + height;
   }

   protected static float clamp(float value, float min, float max) {
      return value < min ? min : (value > max ? max : value);
   }
}
