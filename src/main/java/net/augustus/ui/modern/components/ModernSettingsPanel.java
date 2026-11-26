package net.augustus.ui.modern.components;

import java.util.ArrayList;
import java.util.List;
import net.augustus.ClientManagers;
import net.augustus.clickgui.ClickGuiScreen;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.font.TextWrapper;
import net.augustus.render.RenderUtil;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.utils.render.KawaseBlurEngine;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Reusable Liquid Glass card-row layout, section titles, dividers, and button-group helpers
 * for module settings panels.
 */
public final class ModernSettingsPanel {
   public static final int CARD_GAP = 8;
   public static final int SECTION_TOP_GAP = 14;
   public static final float CARD_RADIUS = UiTheme.CARD_RADIUS;
   public static final float CARD_PAD_X = 14.0F;
   public static final float CARD_PAD_Y = 9.0F;
   public static final int MIN_CARD_HEIGHT = 40;
   public static final int SECTION_TITLE_HEIGHT = 24;
   public static final int TOP_GROUP_HEADER_HEIGHT = 30;
   public static final int SUB_GROUP_HEADER_HEIGHT = 26;

   private ModernSettingsPanel() {
   }

   /**
    * Applies the ClickGUI open/close envelope to one ARGB at the point of draw.
    *
    * <p>WHY it has to be done per colour: the envelope cannot ride on GL state. {@code GlassRenderer}'s
    * primitive pass hard-resets {@code GlStateManager.color(1,1,1,1)} before every quad and the font
    * renderer resets the colour after each string, so a global {@code glColor} fade is silently dropped
    * by exactly the two heaviest subsystems. Every card, section header and tab drawn here has to carry
    * the value itself.
    *
    * <p>WHY only colours are wrapped and never the metrics: this is a fade, not a zoom, so positions and
    * sizes are untouched.
    *
    * <p>Never hand an already-faded ARGB back into this method: this is the single application point for
    * the panel, and a second application would fade that value at the square of the intended rate.
    */
   private static int faded(int argb) {
      // Settled overlay (everything except the ~288 ms open/close transition): skip the arithmetic
      // entirely. The result is never cached - it is recomputed on every draw, so a colour can never
      // leak a stale envelope into another frame or another screen.
      return ClickGuiScreen.getScreenFade() >= 0.999F ? argb : ClickGuiScreen.fadeArgb(argb);
   }

   public static final class CardMetrics {
      public final float cardX;
      public final float cardY;
      public final float cardW;
      public final int cardH;
      public final int headerHeight;
      public final float controlAreaRightX;
      public final float controlCenterY;
      public final boolean hovered;
      public final boolean descriptionTruncated;
      public final int tooltipWidth;

      private CardMetrics(
         float cardX,
         float cardY,
         float cardW,
         int cardH,
         int headerHeight,
         float controlAreaRightX,
         float controlCenterY,
         boolean hovered,
         boolean descriptionTruncated,
         int tooltipWidth
      ) {
         this.cardX = cardX;
         this.cardY = cardY;
         this.cardW = cardW;
         this.cardH = cardH;
         this.headerHeight = headerHeight;
         this.controlAreaRightX = controlAreaRightX;
         this.controlCenterY = controlCenterY;
         this.hovered = hovered;
         this.descriptionTruncated = descriptionTruncated;
         this.tooltipWidth = tooltipWidth;
      }
   }

   private static final class PreparedDescription {
      final List<String> lines;
      final boolean truncated;

      PreparedDescription(List<String> lines, boolean truncated) {
         this.lines = lines;
         this.truncated = truncated;
      }
   }

   public static float resolveCardWidth(int x, int boundsWidth) {
      if (boundsWidth > 0) {
         return Math.max(240.0F, (float)(boundsWidth - x - 16));
      }
      return 420.0F;
   }

   public static String formatUiLabel(String raw) {
      if (raw == null || raw.isEmpty()) {
         return "";
      }
      String s = raw.trim();
      s = s.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
      s = s.replaceAll("([A-Z]{2,})([A-Z][a-z]{2,})", "$1 $2");
      s = s.replace("Array List", "ArrayList").replace("Click Gui", "ClickGui");
      s = s.replaceAll("\\s{2,}", " ").trim();
      return s;
   }

   public static void drawGlassCardSurface(float x, float y, float width, float height, float radius, boolean hovered, boolean focused) {
      drawGlassCardSurface("anon@" + Math.round(x) + "_" + Math.round(width), x, y, width, height, radius, hovered, focused, true, hovered ? Math.round(x + width * 0.5F) : -9999, hovered ? Math.round(y + height * 0.5F) : -9999);
   }

   public static void drawGlassCardSurface(
      String animKey,
      float x,
      float y,
      float width,
      float height,
      float radius,
      boolean hovered,
      boolean focused,
      boolean enabled,
      int mouseX,
      int mouseY
   ) {
      int accent = UiTheme.accentColor();
      float hoverT = enabled ? UiAnimation.smoothState("sc_hov_" + animKey, hovered ? 1.0F : 0.0F, 16.0F) : 0.0F;
      float focusT = enabled ? UiAnimation.smoothState("sc_foc_" + animKey, focused ? 1.0F : 0.0F, 18.0F) : 0.0F;
      float borderW = hovered || focused ? UiTheme.THICK_BORDER_THICKNESS : UiTheme.BORDER_THICKNESS;
      int baseBorder = UiTheme.glassBorderFocusedOrHover(hoverT * 0.65F, accent);
      int borderArgb = focusT > 0.02F
         ? UiTheme.lerpArgb(baseBorder, UiTheme.withAlpha(UiTheme.accentSoftGlow(), 242), focusT)
         : baseBorder;

      boolean windowOrPanelBlurred = ClientManagers.moduleManager != null
         && ClientManagers.moduleManager.clickGui != null
         && (ClientManagers.moduleManager.clickGui.isWindowBlurActive() || ClientManagers.moduleManager.clickGui.isPanelBlurActive());
      boolean optionBlur = ClientManagers.moduleManager != null
         && ClientManagers.moduleManager.clickGui != null
         && ClientManagers.moduleManager.clickGui.isOptionBlurActive();
      float blurR = ClientManagers.moduleManager != null && ClientManagers.moduleManager.clickGui != null
         ? ClientManagers.moduleManager.clickGui.getBlurRadius()
         : UiTheme.blurRadius();

      // The fill and border above are built from the RAW tokens, so `accent`/`borderArgb` still carry
      // their own alpha here; the envelope is applied once, at the two draw calls below.
      if (optionBlur && !windowOrPanelBlurred && blurR > 0.05F && KawaseBlurEngine.isAvailable()) {
         GlassRenderer.drawOptionGlass(x, y, width, height, radius, borderW, faded(borderArgb));
      } else {
         GlassRenderer.drawGlassCardWithHover(x, y, width, height, radius, hoverT, 0.0F, focusT, 0.0F, (float)mouseX, (float)mouseY, faded(accent), faded(borderArgb), borderW);
      }
   }

   /**
    * Measures and optionally draws a standard full-width Liquid Glass setting card with left-aligned
    * bold/semibold title + optional muted description subtitle, leaving {@code rightReserveW} on the right.
    */
   public static CardMetrics drawSettingCard(
      int x,
      int y,
      int boundsWidth,
      String title,
      String description,
      boolean showDescription,
      float rightReserveW,
      boolean forceHovered,
      boolean focused,
      boolean drawVisuals,
      int mouseX,
      int mouseY
   ) {
      float cardX = (float)x;
      float cardY = (float)y;
      float cardW = resolveCardWidth(x, boundsWidth);
      float gutter = rightReserveW > 0.0F ? rightReserveW + 18.0F : 0.0F;
      float leftMaxW = Math.max(120.0F, cardW - CARD_PAD_X * 2.0F - gutter);

      CustomFontUtil titleFont = FontManager.interMedium;
      CustomFontUtil descFont = FontManager.interRegular;
      PreparedDescription prep = prepareDescription(showDescription ? description : null, leftMaxW, 2, descFont);

      float titleH = titleFont.getFontHeight();
      float descH = descFont.getFontHeight();
      int cardH;
      if (prep.lines.isEmpty()) {
         cardH = MIN_CARD_HEIGHT;
      } else {
         float linesH = prep.lines.size() * descH + Math.max(0, prep.lines.size() - 1) * 2.5F;
         cardH = Math.max(54, Math.round(CARD_PAD_Y * 2.0F + titleH + 3.0F + linesH));
      }

      boolean hovered = forceHovered || RenderUtil.isHovered(cardX, cardY, cardW, (float)cardH, mouseX, mouseY);
      if (drawVisuals) {
         String animKey = (title != null ? title : "") + "_" + x;
         drawGlassCardSurface(animKey, cardX, cardY, cardW, (float)cardH, CARD_RADIUS, hovered, focused, true, mouseX, mouseY);

         String safeTitle = trimToWidth(titleFont, title != null ? title : "", leftMaxW);
         if (prep.lines.isEmpty()) {
            titleFont.drawVerticallyCenteredString(safeTitle, (double)(cardX + CARD_PAD_X), (double)cardY, (double)cardH, faded(UiTheme.primaryTextColor()));
         } else {
            float titleY = cardY + CARD_PAD_Y;
            float descY = titleY + titleH + 3.0F;
            titleFont.drawString(safeTitle, (double)(cardX + CARD_PAD_X), (double)titleY, faded(UiTheme.primaryTextColor()));
            int descColor = faded(UiTheme.mutedTextColor());
            for (int i = 0; i < prep.lines.size(); i++) {
               descFont.drawString(prep.lines.get(i), (double)(cardX + CARD_PAD_X), (double)(descY + i * (descH + 2.5F)), descColor);
            }
         }
      }

      float rightEdge = cardX + cardW - CARD_PAD_X;
      float centerY = cardY + (float)cardH * 0.5F;
      return new CardMetrics(cardX, cardY, cardW, cardH, cardH, rightEdge, centerY, hovered, prep.truncated, Math.round(leftMaxW));
   }

   /**
    * Measures and optionally draws a two-row stacked Liquid Glass setting card with title + optional
    * description in the top header, an internal horizontal divider, and {@code bodyHeight} space below.
    */
   public static CardMetrics drawStackedCard(
      int x,
      int y,
      int boundsWidth,
      String title,
      String description,
      boolean showDescription,
      String rightBadgeText,
      int bodyHeight,
      boolean forceHovered,
      boolean focused,
      boolean drawVisuals,
      int mouseX,
      int mouseY
   ) {
      float cardX = (float)x;
      float cardY = (float)y;
      float cardW = resolveCardWidth(x, boundsWidth);

      CustomFontUtil titleFont = FontManager.interMedium;
      CustomFontUtil descFont = FontManager.interRegular;
      float badgeReserve = rightBadgeText != null && !rightBadgeText.isEmpty() ? descFont.getStringWidth(rightBadgeText) + 28.0F : 0.0F;
      float leftMaxW = Math.max(120.0F, cardW - CARD_PAD_X * 2.0F - badgeReserve);
      PreparedDescription prep = prepareDescription(showDescription ? description : null, leftMaxW, 2, descFont);

      float titleH = titleFont.getFontHeight();
      float descH = descFont.getFontHeight();
      int headerH;
      if (prep.lines.isEmpty()) {
         headerH = 36;
      } else {
         float linesH = prep.lines.size() * descH + Math.max(0, prep.lines.size() - 1) * 2.5F;
         headerH = Math.max(48, Math.round(CARD_PAD_Y + titleH + 3.5F + linesH + 6.0F));
      }

      int totalH = headerH + 6 + Math.max(24, bodyHeight) + 10;
      boolean hovered = forceHovered || RenderUtil.isHovered(cardX, cardY, cardW, (float)totalH, mouseX, mouseY);
      if (drawVisuals) {
         String animKey = "stk_" + (title != null ? title : "") + "_" + x;
         drawGlassCardSurface(animKey, cardX, cardY, cardW, (float)totalH, CARD_RADIUS, hovered, focused, true, mouseX, mouseY);

         String safeTitle = trimToWidth(titleFont, title != null ? title : "", leftMaxW);
         if (prep.lines.isEmpty()) {
            titleFont.drawVerticallyCenteredString(safeTitle, (double)(cardX + CARD_PAD_X), (double)cardY, (double)headerH, faded(UiTheme.primaryTextColor()));
         } else {
            float titleY = cardY + CARD_PAD_Y;
            float descY = titleY + titleH + 3.5F;
            titleFont.drawString(safeTitle, (double)(cardX + CARD_PAD_X), (double)titleY, faded(UiTheme.primaryTextColor()));
            int descColor = faded(UiTheme.mutedTextColor());
            for (int i = 0; i < prep.lines.size(); i++) {
               descFont.drawString(prep.lines.get(i), (double)(cardX + CARD_PAD_X), (double)(descY + i * (descH + 2.5F)), descColor);
            }
         }

         if (rightBadgeText != null && !rightBadgeText.isEmpty()) {
            float bw = descFont.getStringWidth(rightBadgeText) + 16.0F;
            float bh = 20.0F;
            float bx = cardX + cardW - CARD_PAD_X - bw;
            float by = cardY + ((float)headerH - bh) * 0.5F;
            GlassRenderer.drawRoundedBox(bx, by, bw, bh, 5.5F, faded(UiTheme.withAlpha(UiTheme.accentColor(), 55)), faded(UiTheme.withAlpha(UiTheme.accentSoftGlow(), 180)), 1.15F);
            descFont.drawCenteredStringInBox(rightBadgeText, (double)bx, (double)by, (double)bw, (double)bh, faded(UiTheme.TEXT_PRIMARY));
         }

         float divY = cardY + (float)headerH + 1.0F;
         GlassRenderer.drawSeparator(cardX + CARD_PAD_X, divY, cardX + cardW - CARD_PAD_X, divY, 1.0F, faded(UiTheme.GLASS_DIVIDER));
      }

      float rightEdge = cardX + cardW - CARD_PAD_X;
      float bodyTop = cardY + (float)headerH + 7.0F;
      return new CardMetrics(cardX, cardY, cardW, totalH, headerH, rightEdge, bodyTop, hovered, prep.truncated, Math.round(leftMaxW));
   }

   /**
    * Draws a clean non-collapsible bold section title with a full-width trailing horizontal divider.
    */
   public static void drawSectionTitle(float x, float y, float width, String title) {
      String label = formatUiLabel(title).toUpperCase(java.util.Locale.ROOT);
      float h = (float)SECTION_TITLE_HEIGHT;
      float ty = y + (h - FontManager.interSection.getFontHeight()) * 0.5F;
      FontManager.interSection.drawStringSpaced(label, x + 2.0F, ty, 1.1F, faded(UiTheme.mutedTextColor()));
      float lineStart = x + FontManager.interSection.getStringWidthSpaced(label, 1.1F) + 12.0F;
      float lineEnd = x + width;
      if (lineEnd - lineStart > 20.0F) {
         GlassRenderer.drawSeparator(lineStart, y + h * 0.5F, lineEnd, y + h * 0.5F, UiTheme.SEPARATOR_THICKNESS, faded(UiTheme.GLASS_DIVIDER));
      }
   }

   public static float drawCollapsibleSectionHeader(
      float x,
      float y,
      float width,
      String title,
      boolean expanded,
      int mouseX,
      int mouseY
   ) {
      return drawCollapsibleSectionHeader(x, y, width, title, expanded, -1, false, mouseX, mouseY);
   }

   public static float drawCollapsibleSectionHeader(
      float x,
      float y,
      float width,
      String title,
      boolean expanded,
      int childCount,
      boolean nested,
      int mouseX,
      int mouseY
   ) {
      return drawCollapsibleSectionHeader(x, y, width, title, expanded, expanded ? 1.0F : 0.0F, childCount, nested, mouseX, mouseY);
   }

   /**
    * Draws a bold section header matching reference section dividers ("Hand", "Elder Guardian"),
    * with a subtle count badge, horizontal divider, and smooth rotating accordion chevron button.
    */
   public static float drawCollapsibleSectionHeader(
      float x,
      float y,
      float width,
      String title,
      boolean expanded,
      float expandProgress,
      int childCount,
      boolean nested,
      int mouseX,
      int mouseY
   ) {
      CustomFontUtil font = nested ? FontManager.interSemiBold : FontManager.interBold;
      float headerH = nested ? (float)SUB_GROUP_HEADER_HEIGHT : (float)TOP_GROUP_HEADER_HEIGHT;
      String cleanTitle = formatUiLabel(title);
      boolean hovered = RenderUtil.isHovered(x, y, width, headerH, mouseX, mouseY);
      float hovT = UiTheme.smoothState("grp_hov_" + cleanTitle + "_" + nested, hovered ? 1.0F : 0.0F);
      float prsT = UiTheme.smoothPress("grp_prs_" + cleanTitle + "_" + nested, 0.0F, hovered && UiAnimation.isPrimaryMouseDown());
      float expT = UiAnimation.clamp01(expandProgress);
      int accent = UiTheme.accentColor();

      int idleTitle = nested ? UiTheme.secondaryTextColor() : UiTheme.TEXT_PRIMARY;
      int titleColor = UiTheme.lerpArgb(idleTitle, UiTheme.accentSoftGlow(), hovT * 0.85F);
      font.drawVerticallyCenteredString(cleanTitle, (double)(x + 2.0F), (double)y, (double)headerH, faded(titleColor));

      float cursorX = x + font.getStringWidth(cleanTitle) + 10.0F;
      if (childCount > 0) {
         String countStr = String.valueOf(childCount);
         float badgeW = Math.max(20.0F, FontManager.interSmall.getStringWidth(countStr) + 10.0F);
         float badgeH = 17.0F;
         float badgeY = y + (headerH - badgeH) * 0.5F;
         int badgeBorder = UiTheme.lerpArgb(UiTheme.GLASS_BORDER, UiTheme.withAlpha(UiTheme.accentSoftGlow(), 195), Math.max(hovT, expT * 0.45F));
         // Count badges are small secondary controls: a level veil for the resting state, pulled towards
         // the accent as the group expands, instead of an absolute dark fill.
         int badgeFill = UiTheme.lerpArgb(
            UiTheme.glassControlVeilArgb(UiTheme.LEVEL_CONTROL_RAISED, 0.0F, 0.0F, accent),
            UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, hovT, expT * 0.55F, accent, 0.72F),
            Math.max(hovT, expT * 0.75F)
         );
         GlassRenderer.drawRoundedBox(cursorX, badgeY, badgeW, badgeH, 5.0F, faded(badgeFill), faded(badgeBorder), 1.05F);
         FontManager.interSmall.drawCenteredStringInBox(countStr, (double)cursorX, (double)badgeY, (double)badgeW, (double)badgeH, faded(UiTheme.lerpArgb(UiTheme.secondaryTextColor(), UiTheme.TEXT_PRIMARY, hovT)));
         cursorX += badgeW + 10.0F;
      }

      float btnSize = nested ? 21.0F : 24.0F;
      float btnX = x + width - btnSize;
      float btnY = y + (headerH - btnSize) * 0.5F;

      float lineEnd = btnX - 10.0F;
      if (lineEnd - cursorX > 14.0F) {
         int divColor = UiTheme.lerpArgb(UiTheme.GLASS_DIVIDER, UiTheme.withAlpha(UiTheme.accentSoftGlow(), 185), hovT);
         GlassRenderer.drawSeparator(cursorX, y + headerH * 0.5F, lineEnd, y + headerH * 0.5F, UiTheme.SEPARATOR_THICKNESS, faded(divColor));
      }

      int chevronBorder = UiTheme.lerpArgb(
         UiTheme.GLASS_BORDER,
         UiTheme.withAlpha(UiTheme.accentSoftGlow(), 225),
         Math.max(hovT, expT * 0.5F)
      );
      int chevronFill = UiTheme.glassControlVeilArgb(UiTheme.LEVEL_CONTROL_RAISED, hovT, expT * 0.35F, accent);
      GlassRenderer.drawInteractiveSurface(
         btnX,
         btnY,
         btnSize,
         btnSize,
         6.0F,
         faded(chevronFill),
         faded(UiTheme.lerpArgb(chevronFill, UiTheme.glassRecessedDepthRgb(), 0.28F)),
         faded(chevronBorder),
         UiTheme.BORDER_THICKNESS,
         UiTheme.glassTopHighlightAlpha(Math.max(hovT, expT * 0.5F)),
         hovT,
         prsT,
         true,
         (float)mouseX,
         (float)mouseY,
         faded(accent)
      );

      float chevCx = btnX + btnSize * 0.5F;
      float chevCy = btnY + btnSize * 0.5F + prsT * 0.35F;
      GL11.glPushMatrix();
      GlStateManager.translate(chevCx, chevCy, 0.0F);
      GlStateManager.rotate(-90.0F * (1.0F - expT), 0.0F, 0.0F, 1.0F);
      GlStateManager.translate(-chevCx, -chevCy, 0.0F);
      LucideIcon.CHEVRON_DOWN.drawCentered(
         chevCx,
         chevCy,
         nested ? 11.0F : 12.5F,
         faded(UiTheme.lerpArgb(UiTheme.secondaryTextColor(), UiTheme.accentSoftGlow(), Math.max(hovT, expT * 0.75F)))
      );
      GL11.glPopMatrix();

      return width;
   }

   /**
    * Draws a segmented Liquid Glass button group of section filter tabs with a smooth sliding selection
    * indicator pill and recessed frosted container tray.
    */
   public static int drawSectionFilterTabs(
      float x,
      float y,
      float maxWidth,
      List<String> labels,
      int activeIndex,
      List<float[]> outRects,
      boolean drawVisuals,
      int mouseX,
      int mouseY
   ) {
      if (outRects != null) {
         outRects.clear();
      }
      if (labels == null || labels.isEmpty()) {
         return 0;
      }

      CustomFontUtil font = FontManager.interMedium;
      float pillH = 24.0F;
      float padX = 12.0F;
      float gapX = 4.0F;
      float gapY = 6.0F;
      int accent = UiTheme.accentColor();

      float curX = x + 3.0F;
      float curY = y + 3.0F;
      float maxRowRight = curX;
      int rows = 1;
      List<String> formattedLabels = new ArrayList<>(labels.size());
      List<float[]> planned = new ArrayList<>(labels.size());
      for (int i = 0; i < labels.size(); i++) {
         String label = formatUiLabel(labels.get(i));
         formattedLabels.add(label);
         float pillW = Math.max(44.0F, font.getStringWidth(label) + padX * 2.0F);
         if (curX > x + 3.0F && curX + pillW > x + maxWidth - 3.0F) {
            curX = x + 3.0F;
            curY += pillH + gapY;
            rows++;
         }
         planned.add(new float[]{curX, curY, pillW, pillH});
         if (outRects != null) {
            outRects.add(new float[]{curX, curY, pillW, pillH});
         }
         maxRowRight = Math.max(maxRowRight, curX + pillW);
         curX += pillW + gapX;
      }

      if (drawVisuals) {
         boolean singleRow = rows == 1;
         if (singleRow) {
            float trayW = Math.max(60.0F, (maxRowRight - x) + 3.0F);
            GlassRenderer.drawRecessedWell(x, y, trayW, pillH + 6.0F, UiTheme.CARD_RADIUS, 0.0F, 0.0F, (float)mouseX, (float)mouseY, faded(accent), UiTheme.BORDER_THICKNESS, UiTheme.LEVEL_WELL);
         }

         if (activeIndex >= 0 && activeIndex < planned.size()) {
            float[] actRect = planned.get(activeIndex);
            // The indicator travels, so it keeps the faster 20 rate (95% in 150 ms) instead of the
            // ordinary control rate; it must not lag behind the pointer.
            float indX = UiTheme.smoothStateAt("sec_tab_ind_x", actRect[0], 20.0F);
            float indY = UiTheme.smoothStateAt("sec_tab_ind_y", actRect[1], 20.0F);
            float indW = UiTheme.smoothStateAt("sec_tab_ind_w", actRect[2], 20.0F);
            float indH = actRect[3];

            boolean actHov = RenderUtil.isHovered(actRect[0], actRect[1], actRect[2], actRect[3], mouseX, mouseY);
            float actHovT = UiTheme.smoothState("sec_tab_act_hov", actHov ? 1.0F : 0.0F);

            GlassRenderer.drawSoftShadow(indX, indY, indW, indH, UiTheme.CONTROL_RADIUS, 8.0F, 1.5F, faded(UiTheme.withAlpha(accent, 66)));
            // The selected pill is a raised control over the tray well: it now carries the raised
            // level's material (inside drawInteractiveSurface) and an accent-pulled veil over it.
            int topFill = UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, actHovT, 1.0F, UiTheme.lerpArgb(accent, 0xFFEAE4FF, 0.10F + 0.10F * actHovT), 0.88F);
            int botFill = UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, actHovT, 1.0F, UiTheme.lerpArgb(UiTheme.accentDeep(), 0xFF13102B, 0.30F), 0.88F);
            GlassRenderer.drawInteractiveSurface(
               indX,
               indY,
               indW,
               indH,
               UiTheme.CONTROL_RADIUS,
               faded(topFill),
               faded(botFill),
               faded(UiTheme.withAlpha(UiTheme.accentSoftGlow(), 225)),
               UiTheme.BORDER_THICKNESS,
               0.24F,
               actHovT,
               0.0F,
               true,
               (float)mouseX,
               (float)mouseY,
               faded(accent)
            );
         }

         for (int i = 0; i < planned.size(); i++) {
            float[] r = planned.get(i);
            boolean active = i == activeIndex;
            boolean hovered = RenderUtil.isHovered(r[0], r[1], r[2], r[3], mouseX, mouseY);
            float hovT = UiTheme.smoothState("sec_tab_hov_" + i + "_" + formattedLabels.get(i), hovered ? 1.0F : 0.0F);
            float prsT = UiTheme.smoothPress("sec_tab_prs_" + i + "_" + formattedLabels.get(i), 0.0F, hovered && UiAnimation.isPrimaryMouseDown());
            float actT = UiTheme.smoothState("sec_tab_act_" + i + "_" + formattedLabels.get(i), active ? 1.0F : 0.0F);

            if (!active && (!singleRow || hovT > 0.03F)) {
               // Inert tabs are raised control surfaces too (they are the same pill family as the
               // selected indicator above), so a hovered tab gets the level's material and a veil
               // instead of a flat fill; inside the single-row tray the resting tab stays transparent
               // because the tray already carries the group's surface.
               int idleFill = singleRow
                  ? UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, hovT, prsT, accent, 0.45F)
                  : UiTheme.glassControlVeilArgb(UiTheme.LEVEL_CONTROL_RAISED, hovT, prsT, accent);
               int idleBot = UiTheme.lerpArgb(idleFill, UiTheme.glassRecessedDepthRgb(), 0.3F);
               int border = hovT > 0.03F
                  ? UiTheme.withAlpha(UiTheme.accentSoftGlow(), Math.round((singleRow ? 135.0F : 190.0F) * hovT))
                  : (singleRow ? 0 : UiTheme.GLASS_BORDER);
               GlassRenderer.drawInteractiveSurface(
                  r[0],
                  r[1],
                  r[2],
                  r[3],
                  UiTheme.CONTROL_RADIUS,
                  faded(idleFill),
                  faded(idleBot),
                  faded(border),
                  1.0F,
                  UiTheme.glassTopHighlightAlpha(hovT) * (singleRow ? hovT : 1.0F),
                  hovT,
                  prsT,
                  true,
                  (float)mouseX,
                  (float)mouseY,
                  faded(accent)
               );
            }
            int fg = UiTheme.lerpArgb(UiTheme.secondaryTextColor(), UiTheme.TEXT_PRIMARY, Math.max(actT, hovT));
            font.drawCenteredStringInBox(formattedLabels.get(i), (double)r[0], (double)(r[1] + prsT * 0.35F), (double)r[2], (double)r[3], faded(fg));
         }
      }

      return Math.round((curY + pillH + 3.0F) - y);
   }

   public static void drawGroupTreeLine(float x, float topY, float bottomY, float childMidY, boolean isLastChild) {
      int lineColor = UiTheme.GLASS_BORDER;
      GlassRenderer.drawSeparator(x + 4.0F, topY, x + 4.0F, bottomY, 1.8F, faded(lineColor));
      GlassRenderer.drawSeparator(x + 4.0F, childMidY, x + 12.0F, childMidY, 1.8F, faded(lineColor));
   }

   private static PreparedDescription prepareDescription(String raw, float maxWidth, int maxLines, CustomFontUtil font) {
      if (raw == null || raw.trim().isEmpty()) {
         return new PreparedDescription(new ArrayList<>(), false);
      }

      String normalized = raw.replace("\r\n", "\n").replace('\r', '\n').trim();
      boolean truncated = false;
      StringBuilder summary = new StringBuilder();
      String[] rawLines = normalized.split("\n");
      for (String rawLine : rawLines) {
         String trimmed = rawLine.trim();
         if (trimmed.isEmpty()) {
            continue;
         }
         if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            truncated = true;
            continue;
         }
         if (summary.length() > 0) {
            summary.append(' ');
         }
         summary.append(trimmed);
      }

      String collapsed = summary.toString().trim();
      if (collapsed.isEmpty()) {
         collapsed = normalized.replace('\n', ' ').trim();
      }
      if (truncated && collapsed.endsWith(":")) {
         collapsed = collapsed.substring(0, collapsed.length() - 1) + ".";
      }

      List<String> wrapped = TextWrapper.wrapText(collapsed, Math.max(100, (int)maxWidth), font);
      if (wrapped.size() > maxLines) {
         truncated = true;
         List<String> limited = new ArrayList<>(maxLines);
         for (int i = 0; i < maxLines; i++) {
            limited.add(wrapped.get(i));
         }
         String last = trimToWidth(font, limited.get(maxLines - 1), maxWidth);
         if (!last.endsWith("...")) {
            last = appendEllipsisToFit(font, last, maxWidth);
         }
         limited.set(maxLines - 1, last);
         return new PreparedDescription(limited, true);
      }

      return new PreparedDescription(wrapped, truncated);
   }

   private static String appendEllipsisToFit(CustomFontUtil font, String text, float maxW) {
      String ellipsis = "...";
      String cur = text;
      while (!cur.isEmpty() && font.getStringWidth(cur + ellipsis) > maxW) {
         cur = cur.substring(0, cur.length() - 1);
      }
      return cur.trim() + ellipsis;
   }

   public static String trimToWidth(CustomFontUtil font, String text, float maxW) {
      if (text == null || text.isEmpty() || font.getStringWidth(text) <= maxW) {
         return text == null ? "" : text;
      }
      String ellipsis = "...";
      float ellW = font.getStringWidth(ellipsis);
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < text.length(); i++) {
         char ch = text.charAt(i);
         if (font.getStringWidth(sb.toString() + ch) + ellW > maxW) {
            break;
         }
         sb.append(ch);
      }
      return sb.append(ellipsis).toString();
   }
}
