package net.augustus.ui.modern.components;

import java.util.HashMap;
import java.util.Map;
import net.augustus.clickgui.ClickGuiScreen;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.render.RenderUtil;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;

/**
 * Reusable Frosted Liquid Glass tab pills (horizontal category filter tabs and vertical sidebar items)
 * with continuous sliding indicator bars, localized cursor reveal lighting, press compression,
 * and smooth state transitions.
 */
public final class ModernTabs {
   private static final Map<String, float[]> ANIM_STATES = new HashMap<>();

   /**
    * Category chip label face, derived once. The reference's chip caps are 11.8 tall, which puts the
    * label at ~14.2 against the shared 16.0 medium face -- and the 14 % difference is not cosmetic:
    * nine chips at 16.0 overflow the 824-px chip band by ~46 px, which is why our row lost its last
    * chip behind the trailing view-mode button.
    */
   private static CustomFontUtil CHIP_LABEL_FONT;

   private static float sidebarIndicatorY = -1.0F;
   private static float sidebarIndicatorH = 18.0F;

   private ModernTabs() {
   }

   /** The chip label face; see {@link #CHIP_LABEL_FONT}. */
   public static CustomFontUtil chipLabelFont() {
      if (CHIP_LABEL_FONT == null) {
         CHIP_LABEL_FONT = FontManager.interMedium.deriveSize(14.2F);
      }
      return CHIP_LABEL_FONT;
   }

   /**
    * Applies the ClickGUI open/close envelope to one ARGB at the point of draw.
    *
    * <p>The envelope cannot ride on GL state ({@code GlassRenderer} resets the GL colour before every quad,
    * the font renderer after every string), so every tab fill, rail, indicator, count badge and label has to
    * carry the value itself. Only alpha is scaled: the pill geometry and the animation state this class
    * caches in {@link #ANIM_STATES} are never faded, so a transition cannot leave stale faded geometry or a
    * stale faded colour behind.
    *
    * <p>Wrapped exactly once per token, at the draw call.
    */
   private static int faded(int argb) {
      // Settled overlay (everything except the ~288 ms open/close transition): skip the arithmetic.
      return ClickGuiScreen.getScreenFade() >= 0.999F ? argb : ClickGuiScreen.fadeArgb(argb);
   }

   private static float[] getAnimState(String prefix, String key, boolean active, boolean hovered) {
      String id = prefix + ":" + (key == null ? "" : key);
      float[] state = ANIM_STATES.get(id);
      if (state == null) {
         state = new float[]{active ? 1.0F : 0.0F, hovered ? 1.0F : 0.0F, 0.0F};
         ANIM_STATES.put(id, state);
      }
      boolean pressed = hovered && UiAnimation.isPrimaryMouseDown();
      state[0] = UiTheme.smoothState(state[0], active ? 1.0F : 0.0F);
      state[1] = UiTheme.smoothState(state[1], hovered ? 1.0F : 0.0F);
      state[2] = UiTheme.smoothPress(state[2], pressed);
      return state;
   }

   public static void drawPillTab(
      float x,
      float y,
      float width,
      float height,
      String label,
      LucideIcon icon,
      boolean active,
      boolean hovered
   ) {
      drawPillTab(x, y, width, height, label, icon, active, hovered, -9999, -9999);
   }

   public static void drawPillTab(
      float x,
      float y,
      float width,
      float height,
      String label,
      LucideIcon icon,
      boolean active,
      boolean hovered,
      int mouseX,
      int mouseY
   ) {
      int accent = UiTheme.accentColor();
      float[] anim = getAnimState("pill", label, active, hovered);
      float actT = anim[0];
      float hovT = anim[1];
      float pressT = anim[2];

      if (actT > 0.03F) {
         // A capsule halo at half the chip's height, so the glow's outline is the chip's own silhouette and
         // not a larger rounded rectangle with a smaller radius (which is what a fixed radius reads as on a
         // 34px pill). Blur 9 keeps the halo inside the band the caller scissors to.
         GlassRenderer.drawSoftShadow(x, y, width, height, height * 0.5F, 9.0F, 1.5F, faded(UiTheme.withAlpha(accent, Math.round(96.0F * actT))));
      } else if (hovT > 0.04F) {
         GlassRenderer.drawSoftShadow(x, y, width, height, height * 0.5F, 6.0F, 1.0F, faded(UiTheme.glassShadowArgb(0.18F * hovT)));
      }

      // Measured off the reference:
      //   idle   top rgb(47,50,75) -> bottom rgb(39,39,65), i.e. only a few levels above the page;
      //   active top rgb(93,46,180) -> bottom rgb(70,34,146), a two-stop vertical purple.
      //
      // WHY the fills carry an explicit alpha and are painted through drawInteractiveSurface: a chip is
      // a tinted pane over the window's own glass, so even the SELECTED one has to let the backdrop
      // read through it. Painting it as a flat opaque gradient (what this used to do) turned the active
      // chip into a slab and the whole bar stopped looking like glass. Alpha is now the only thing that
      // separates the states, and the surface underneath supplies the material.
      //
      // There is still no ring: the reference has none at rest, so borderArgb is 0. The accent only
      // ever appears as fill.
      int idleTop = UiTheme.withAlpha(UiTheme.lerpArgb(0xFF2F324B, accent, 0.05F + 0.03F * hovT), 46);
      int idleBot = UiTheme.withAlpha(UiTheme.lerpArgb(0xFF272739, accent, 0.03F + 0.02F * hovT), 38);
      int activeTop = UiTheme.withAlpha(UiTheme.lerpArgb(accent, 0xFF2C1064, 0.35F), 156);
      int activeBot = UiTheme.withAlpha(UiTheme.lerpArgb(accent, 0xFF1E0A46, 0.55F), 168);

      int topFill = UiTheme.lerpArgb(idleTop, activeTop, actT);
      int bottomFill = UiTheme.lerpArgb(idleBot, activeBot, actT);

      GlassRenderer.drawInteractiveSurface(
         x,
         y,
         width,
         height,
         // The target's chips are full pills: the radius is half the height, not a fixed 6.
         height * 0.5F,
         faded(topFill),
         faded(bottomFill),
         0,
         0.0F,
         // A one-row sheen along the top edge is all the modelling the reference gives the idle chips.
         Math.max(0.05F * (1.0F - actT), 0.02F),
         // The selected chip is treated as "state 1" so the surface keeps the full tint instead of
         // attenuating it back to 18 %; hover still layers on top.
         Math.max(hovT, actT),
         pressT,
         true,
         (float)mouseX,
         (float)mouseY,
         faded(accent)
      );

      float nudge = pressT * 0.4F;
      // Every chip label in the reference reads near-white whether the chip is active or not -- the
      // selection is carried by the fill, not by the type.
      int fg = 0xFFFFFFFF;
      if (icon != null) {
         int iconClr = UiTheme.lerpArgb(fg, 0xFFFFFFFF, actT * 0.5F);
         // Measured off the target: the chip icon is ~11.4 content px, its centre sits 16.6 in from the
         // pill's left edge, and the label follows 16.1 after it.
         icon.drawCentered(x + 17.2F, y + height * 0.5F + nudge, 12.8F, faded(iconClr));
      }
      if (label != null && !label.isEmpty()) {
         CustomFontUtil font = chipLabelFont();
         float textX = icon != null ? x + 32.7F : x + 10.0F;
         font.drawVerticallyCenteredString(label, (double)textX, (double)(y + nudge), (double)height, faded(fg));
      }
   }

   public static void drawSidebarItem(
      float x,
      float y,
      float width,
      float height,
      String label,
      LucideIcon icon,
      boolean active,
      int mouseX,
      int mouseY
   ) {
      drawSidebarItem(x, y, width, height, label, null, icon, active, mouseX, mouseY);
   }

   public static void drawSidebarItem(
      float x,
      float y,
      float width,
      float height,
      String label,
      String countBadge,
      LucideIcon icon,
      boolean active,
      int mouseX,
      int mouseY
   ) {
      boolean hovered = RenderUtil.isHovered(x, y, width, height, mouseX, mouseY);
      int accent = UiTheme.accentColor();
      float[] anim = getAnimState("side", label, active, hovered);
      float actT = anim[0];
      float hovT = anim[1];
      float pressT = anim[2];

      if (active) {
         float targetIndH = height - 13.0F;
         float targetIndY = y + (height - targetIndH) * 0.5F;
         if (sidebarIndicatorY < 0.0F) {
            sidebarIndicatorY = targetIndY;
            sidebarIndicatorH = targetIndH;
         } else {
            sidebarIndicatorY = UiAnimation.smooth(sidebarIndicatorY, targetIndY, 22.0F);
            sidebarIndicatorH = UiAnimation.smooth(sidebarIndicatorH, targetIndH, 22.0F);
         }
      }

      if (actT > 0.02F) {
         GlassRenderer.drawSoftShadow(
            x,
            y,
            width,
            height,
            UiTheme.PILL_RADIUS,
            9.0F,
            1.5F,
            faded(UiTheme.withAlpha(accent, Math.round(38.0F * actT)))
         );
      }

      if (actT > 0.02F || hovT > 0.02F) {
         float surfaceAlpha = Math.max(actT, hovT);
         // Sidebar rails are level veils too (a hover is the raised veil pulled slightly towards the
         // accent, the active rail the same veil pulled hard), so the sidebar's own glass material stays
         // visible under them instead of being covered by an opaque dark plate.
         //
         // WHY the hover coverage is 0.30 and not a whisper: at 0.14 the row only gained a faint tint
         // over the material, which reads as "this row changed colour", not as "a glass surface is
         // floating under the pointer". 0.30 keeps the backdrop clearly visible while giving the hover
         // an actual body -- the same relationship the selected plate uses.
         int hoverTop = UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, hovT, 0.0F, accent, 0.30F);
         int hoverBot = UiTheme.lerpArgb(hoverTop, UiTheme.glassRecessedDepthRgb(), 0.30F);
         int actTop = UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, hovT, actT, UiTheme.lerpArgb(accent, 0xFF261E52, 0.34F), 0.72F);
         int actBot = UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, hovT, actT, UiTheme.lerpArgb(UiTheme.accentDeep(), 0xFF131126, 0.48F), 0.72F);

         int topFill = UiTheme.lerpArgb(hoverTop, actTop, actT);
         int botFill = UiTheme.lerpArgb(hoverBot, actBot, actT);
         int border = UiTheme.lerpArgb(
            UiTheme.withAlpha(UiTheme.GLASS_BORDER, Math.round(165.0F * hovT)),
            UiTheme.withAlpha(UiTheme.accentSoftGlow(), 175),
            actT
         );

         if (hovT > 0.02F && actT < 0.5F) {
            // The hover is a floating surface, so it gets the same whisper of accent light the selected
            // plate has -- without it the row reads as a filled rectangle rather than as glass.
            GlassRenderer.drawSoftShadow(x, y, width, height, 8.9F, 7.0F, 1.0F, faded(UiTheme.withAlpha(accent, Math.round(46.0F * hovT))));
         }

         GlassRenderer.drawInteractiveSurface(
            x,
            y,
            width,
            height,
            // 8.9 is the selected plate's radius, so a hover and a selection read as the same object.
            8.9F,
            faded(UiTheme.scaleAlpha(topFill, surfaceAlpha)),
            faded(UiTheme.scaleAlpha(botFill, surfaceAlpha)),
            faded(border),
            UiTheme.BORDER_THICKNESS,
            UiTheme.glassTopHighlightAlpha(Math.max(hovT * 0.85F, actT * 0.85F)) * surfaceAlpha,
            Math.max(hovT, actT * 0.45F),
            pressT,
            true,
            hovered ? (float)mouseX : (x + 12.0F),
            hovered ? (float)mouseY : (y + height * 0.5F),
            faded(accent)
         );
      }

      float slideOffset = actT * 3.0F + hovT * 1.2F - pressT * 0.4F;
      int idleFg = 0xFFA6B2C6;
      int fg = UiTheme.lerpArgb(idleFg, UiTheme.TEXT_PRIMARY, Math.max(actT, hovT * 0.90F));
      int iconColor = UiTheme.lerpArgb(
         UiTheme.lerpArgb(idleFg, UiTheme.TEXT_PRIMARY, hovT * 0.85F),
         UiTheme.accentSoftGlow(),
         actT * 0.90F
      );
      if (icon != null) {
         if (actT > 0.1F) {
            GlassRenderer.drawCircle(x + 17.0F + slideOffset, y + height * 0.5F, 6.0F, faded(UiTheme.withAlpha(accent, Math.round(26.0F * actT))));
         }
         icon.drawCentered(x + 17.0F + slideOffset, y + height * 0.5F, 14.0F, faded(iconColor));
      }

      float rightReserve = countBadge != null && !countBadge.isEmpty() ? FontManager.interSmall.getStringWidthTabular(countBadge) + 22.0F : 8.0F;
      float labelMaxW = Math.max(40.0F, width - 34.0F - slideOffset - rightReserve);
      String safeLabel = ModernSettingsPanel.trimToWidth(FontManager.interMedium, label, labelMaxW);
      FontManager.interMedium.drawVerticallyCenteredString(safeLabel, (double)(x + 33.0F + slideOffset), (double)y, (double)height, faded(fg));

      if (countBadge != null && !countBadge.isEmpty()) {
         float bw = Math.max(20.0F, FontManager.interSmall.getStringWidthTabular(countBadge) + 10.0F);
         float bh = 16.0F;
         float bx = x + width - 7.0F - bw;
         float by = y + (height - bh) * 0.5F;
         int bFillActive = UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, 0.0F, 1.0F, UiTheme.lerpArgb(accent, 0xFF131620, 0.45F), 0.72F);
         int bFillIdle = UiTheme.glassControlVeilArgb(UiTheme.LEVEL_CONTROL_RAISED, 0.0F, 0.0F, accent);
         int bFill = UiTheme.lerpArgb(bFillIdle, bFillActive, actT);
         int bBorder = actT > 0.3F
            ? UiTheme.withAlpha(UiTheme.accentSoftGlow(), 135)
            : UiTheme.GLASS_BORDER;
         GlassRenderer.drawRoundedBox(bx, by, bw, bh, 5.0F, faded(bFill), faded(bBorder), 1.0F);
         int bText = actT > 0.3F ? UiTheme.TEXT_PRIMARY : UiTheme.mutedTextColor();
         FontManager.interSmall.drawCenteredStringInBoxTabular(countBadge, (double)bx, (double)by, (double)bw, (double)bh, faded(bText));
      }
   }

   /**
    * Draws the continuous sliding active indicator bar along the left edge of sidebar items.
    */
   public static void drawSidebarSlidingIndicator(float sidebarItemX) {
      if (sidebarIndicatorY < 0.0F) {
         return;
      }
      int accent = UiTheme.accentColor();
      float indX = sidebarItemX + 2.5F;
      GlassRenderer.drawSoftShadow(indX, sidebarIndicatorY, 3.0F, sidebarIndicatorH, 1.5F, 6.0F, 0.0F, faded(UiTheme.withAlpha(accent, 140)));
      GlassRenderer.drawRoundedGradientBox(
         indX,
         sidebarIndicatorY,
         3.0F,
         sidebarIndicatorH,
         1.5F,
         faded(UiTheme.withAlpha(UiTheme.lerpArgb(accent, 0xFFFFFFFF, 0.25F), 255)),
         faded(UiTheme.withAlpha(accent, 235)),
         0,
         0.0F,
         0.20F
      );
   }
}
