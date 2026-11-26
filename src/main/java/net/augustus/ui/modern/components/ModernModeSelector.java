package net.augustus.ui.modern.components;

import java.util.ArrayList;
import java.util.List;
import net.augustus.clickgui.ClickGuiScreen;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.render.RenderUtil;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;

/**
 * Reusable Liquid Glass segmented button group & wrapped option-pill selector.
 * Features recessed frosted container trays, smoothly sliding active selection indicators,
 * localized hover lighting, press compression, and wrapped multi-row pill grids.
 */
public class ModernModeSelector {
   private static final class SegmentHit {
      final int optionIndex;
      final float x;
      final float y;
      final float w;
      final float h;

      SegmentHit(int optionIndex, float x, float y, float w, float h) {
         this.optionIndex = optionIndex;
         this.x = x;
         this.y = y;
         this.w = w;
         this.h = h;
      }
   }

   private float lastX;
   private float lastY;
   private float lastWidth;
   private float lastHeight = 24.0F;
   private int lastOptionCount = 0;
   private final List<SegmentHit> cachedHits = new ArrayList<>();
   private float[] hoverAnims = new float[0];
   private float[] selectAnims = new float[0];
   private float[] pressAnims = new float[0];
   private float trayHoverAnim = 0.0F;
   private float sliderX = -1.0F;
   private float sliderW = -1.0F;

   /**
    * Applies the ClickGUI open/close envelope to one ARGB at the point of draw.
    *
    * <p>The envelope cannot ride on GL state ({@code GlassRenderer} resets the GL colour before every quad,
    * the font renderer after every string), so the tray well, every segment fill, the dividers and the
    * labels each carry the value themselves. It scales alpha only - all the hit-test rectangles cached by
    * this selector keep describing the settled layout, so input cannot drift during the transition.
    *
    * <p>Applied once per token, at the draw call: a fill and the border derived from it are each wrapped
    * separately, never a value that already came out of this method.
    */
   private static int faded(int argb) {
      // Settled overlay (everything except the ~288 ms open/close transition): skip the arithmetic.
      return ClickGuiScreen.getScreenFade() >= 0.999F ? argb : ClickGuiScreen.fadeArgb(argb);
   }

   private void ensureAnimCapacity(int count) {
      if (this.hoverAnims.length < count) {
         int nextCap = Math.max(count, 8);
         this.hoverAnims = java.util.Arrays.copyOf(this.hoverAnims, nextCap);
         this.selectAnims = java.util.Arrays.copyOf(this.selectAnims, nextCap);
         this.pressAnims = java.util.Arrays.copyOf(this.pressAnims, nextCap);
      }
   }

   public interface OptionPredicate {
      boolean isSelected(int index);
   }

   public interface OptionConsumer {
      void onSelect(int index);
   }

   /**
    * Measures the total width required to render {@code options} as a single-row
    * connected segmented button group.
    */
   public float measureSingleRowWidth(String[] options) {
      if (options == null || options.length == 0) {
         return 0.0F;
      }
      CustomFontUtil font = FontManager.interMedium;
      float total = 0.0F;
      for (String opt : options) {
         if (opt != null && !opt.isEmpty()) {
            total += Math.max(40.0F, font.getStringWidth(opt) + 18.0F);
         }
      }
      return total;
   }

   /**
    * Computes the wrapped multi-row height of {@code options} inside {@code maxWidth} without drawing.
    */
   public int measureWrappedHeight(float maxWidth, String[] options) {
      if (options == null || options.length == 0) {
         return 23;
      }
      CustomFontUtil font = FontManager.interMedium;
      float chipH = 23.0F;
      float chipPadX = 11.0F;
      float gapX = 6.0F;
      float gapY = 6.0F;

      float curX = 0.0F;
      float curY = 0.0F;
      for (String opt : options) {
         if (opt == null || opt.isEmpty()) {
            continue;
         }
         float chipW = Math.max(40.0F, font.getStringWidth(opt) + chipPadX * 2.0F);
         if (curX > 0.0F && curX + chipW > maxWidth) {
            curX = 0.0F;
            curY += chipH + gapY;
         }
         curX += chipW + gapX;
      }
      return Math.round(curY + chipH);
   }

   public int renderConnectedSingle(
      float x,
      float y,
      float height,
      String[] options,
      String selected,
      boolean drawVisuals,
      int mouseX,
      int mouseY
   ) {
      return this.renderConnectedSegments(
         x,
         y,
         height,
         options,
         idx -> options[idx] != null && options[idx].equals(selected),
         drawVisuals,
         mouseX,
         mouseY
      );
   }

   /**
    * Renders a unified connected Liquid Glass segmented button group with vertical internal dividers
    * and highlighted active segment(s).
    */
   public int renderConnectedSegments(
      float x,
      float y,
      float height,
      String[] options,
      OptionPredicate selectedFn,
      boolean drawVisuals,
      int mouseX,
      int mouseY
   ) {
      this.cachedHits.clear();
      this.lastX = x;
      this.lastY = y;
      this.lastHeight = height;
      this.lastOptionCount = options == null ? 0 : options.length;
      if (options == null || options.length == 0) {
         this.lastWidth = 0.0F;
         return Math.round(height);
      }

      CustomFontUtil font = FontManager.interMedium;
      float totalW = 0.0F;
      List<Integer> validIdx = new ArrayList<>();
      List<Float> segWidths = new ArrayList<>();
      for (int i = 0; i < options.length; i++) {
         String opt = options[i];
         if (opt == null || opt.isEmpty()) {
            continue;
         }
         float w = Math.max(40.0F, font.getStringWidth(opt) + 18.0F);
         validIdx.add(i);
         segWidths.add(w);
         totalW += w;
      }
      this.lastWidth = totalW;

      if (totalW <= 0.0F) {
         return Math.round(height);
      }

      this.ensureAnimCapacity(options.length);
      int accent = UiTheme.accentColor();
      boolean anyHovered = drawVisuals && RenderUtil.isHovered(x, y, totalW, height, mouseX, mouseY);
      this.trayHoverAnim = UiTheme.smoothState(this.trayHoverAnim, anyHovered ? 1.0F : 0.0F);
      if (drawVisuals) {
         // The tray is the container of the button group, so it gets the CARD level's glass material:
         // the group reads as one glass shelf with the segments sitting on it, instead of a flat
         // translucent strip. Well styling still comes from the level's recessive tint.
         GlassRenderer.drawRecessedWell(
            x, y, totalW, height, UiTheme.CONTROL_RADIUS, this.trayHoverAnim * 0.7F, 0.0F, (float)mouseX, (float)mouseY, faded(accent), UiTheme.BORDER_THICKNESS, UiTheme.LEVEL_WELL
         );
      }

      // Check if single-selection so we can glide one cohesive active selection indicator seamlessly
      int selectedCount = 0;
      int singleSelectedSeg = -1;
      for (int s = 0; s < validIdx.size(); s++) {
         if (selectedFn.isSelected(validIdx.get(s))) {
            selectedCount++;
            singleSelectedSeg = s;
         }
      }

      float curX = x;
      if (drawVisuals && selectedCount == 1 && singleSelectedSeg >= 0) {
         float targetX = x;
         for (int s = 0; s < singleSelectedSeg; s++) {
            targetX += segWidths.get(s);
         }
         float targetW = segWidths.get(singleSelectedSeg);
         if (this.sliderX < x - 20.0F || this.sliderX > x + totalW + 20.0F || this.sliderW <= 0.0F) {
            this.sliderX = targetX;
            this.sliderW = targetW;
         } else {
            this.sliderX = UiAnimation.smooth(this.sliderX, targetX, 22.0F);
            this.sliderW = UiAnimation.smooth(this.sliderW, targetW, 22.0F);
         }

         float actHovT = this.hoverAnims[validIdx.get(singleSelectedSeg)];
         float actPrsT = this.pressAnims[validIdx.get(singleSelectedSeg)];
         GlassRenderer.drawSoftShadow(this.sliderX + 2.0F, y + 2.0F, this.sliderW - 4.0F, height - 4.0F, 6.0F, 7.0F, 1.0F, faded(UiTheme.withAlpha(accent, 65)));
         // The sliding selection pill now carries LEVEL_CONTROL_RAISED's material (its own blur sample
         // of the frame's shared capture) instead of being an opaque accent slab, so the tray's glass
         // stays visible through the selected segment. Press is folded in as interaction weight.
         int fillTop = UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, actHovT, actPrsT, UiTheme.lerpArgb(accent, 0xFFEAE4FF, 0.10F + 0.10F * actHovT), 0.88F);
         int fillBot = UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, actHovT, actPrsT, UiTheme.lerpArgb(UiTheme.accentDeep(), 0xFF120F28, 0.28F), 0.88F);
         GlassRenderer.drawInteractiveSurface(
            this.sliderX + 2.0F,
            y + 2.0F,
            this.sliderW - 4.0F,
            height - 4.0F,
            5.5F,
            faded(fillTop),
            faded(fillBot),
            faded(UiTheme.withAlpha(UiTheme.accentSoftGlow(), 220)),
            1.1F,
            0.22F,
            actHovT,
            actPrsT,
            true,
            (float)mouseX,
            (float)mouseY,
            faded(accent)
         );
      }

      curX = x;
      for (int s = 0; s < validIdx.size(); s++) {
         int optIdx = validIdx.get(s);
         float segW = segWidths.get(s);
         this.cachedHits.add(new SegmentHit(optIdx, curX, y, segW, height));

         if (drawVisuals) {
            boolean selected = selectedFn.isSelected(optIdx);
            boolean hovered = RenderUtil.isHovered(curX, y, segW, height, mouseX, mouseY);
            boolean pressed = hovered && UiAnimation.isPrimaryMouseDown();
            this.hoverAnims[optIdx] = UiTheme.smoothState(this.hoverAnims[optIdx], hovered ? 1.0F : 0.0F);
            this.selectAnims[optIdx] = UiTheme.smoothState(this.selectAnims[optIdx], selected ? 1.0F : 0.0F);
            this.pressAnims[optIdx] = UiTheme.smoothPress(this.pressAnims[optIdx], pressed);
            float hovT = this.hoverAnims[optIdx];
            float selT = this.selectAnims[optIdx];
            float prsT = this.pressAnims[optIdx];

            if (s > 0) {
               boolean prevSelected = selectedFn.isSelected(validIdx.get(s - 1));
               if (!selected && !prevSelected) {
                  GlassRenderer.drawSeparator(curX, y + 5.5F, curX, y + height - 5.5F, 1.0F, faded(UiTheme.GLASS_DIVIDER));
               }
            }

            // KEEP the existing rule that the single-selection pill owns the selected segment's fill:
            // only the multi-select branch paints per segment. The difference is that the fills are now
            // level veils (accent-pulled, hover/press weighted) drawn over the segment's own material
            // rather than absolute ARGB slabs, so a hovered or selected segment is still glass.
            if (selectedCount != 1 && (selT > 0.02F || hovT > 0.02F)) {
               int hoverTop = UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, hovT, prsT, accent, 0.55F);
               int hoverBot = UiTheme.lerpArgb(hoverTop, UiTheme.glassRecessedDepthRgb(), 0.22F);
               int selTop = UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, hovT, Math.max(selT, prsT), UiTheme.lerpArgb(accent, 0xFFEAE4FF, 0.10F + 0.08F * hovT), 0.88F);
               int selBot = UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, hovT, Math.max(selT, prsT), UiTheme.lerpArgb(UiTheme.accentDeep(), 0xFF120F28, 0.28F), 0.88F);
               int topF = UiTheme.lerpArgb(hoverTop, selTop, selT);
               int botF = UiTheme.lerpArgb(hoverBot, selBot, selT);
               int borderF = UiTheme.lerpArgb(UiTheme.withAlpha(UiTheme.accentSoftGlow(), Math.round(130.0F * hovT)), UiTheme.withAlpha(UiTheme.accentSoftGlow(), 220), selT);
               GlassRenderer.drawInteractiveSurface(curX + 2.0F, y + 2.0F, segW - 4.0F, height - 4.0F, 5.5F, faded(topF), faded(botF), faded(borderF), 1.05F, 0.20F * Math.max(selT, hovT), Math.max(hovT, selT * 0.4F), prsT, true, (float)mouseX, (float)mouseY, faded(accent));
            } else if (selectedCount == 1 && !selected && hovT > 0.03F) {
               int hoverTop = UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, hovT, prsT, accent, 0.55F);
               int hoverBot = UiTheme.lerpArgb(hoverTop, UiTheme.glassRecessedDepthRgb(), 0.22F);
               GlassRenderer.drawInteractiveSurface(curX + 2.0F, y + 2.0F, segW - 4.0F, height - 4.0F, 5.5F, faded(hoverTop), faded(hoverBot), faded(UiTheme.withAlpha(UiTheme.accentSoftGlow(), Math.round(135.0F * hovT))), 1.0F, 0.12F * hovT, hovT, prsT, true, (float)mouseX, (float)mouseY, faded(accent));
            }

            int fg = UiTheme.lerpArgb(UiTheme.secondaryTextColor(), UiTheme.TEXT_PRIMARY, Math.max(selT, hovT));
            font.drawCenteredStringInBox(options[optIdx], (double)curX, (double)(y + prsT * 0.35F), (double)segW, (double)height, faded(fg));
         }

         curX += segW;
      }

      return Math.round(height);
   }

   public int renderSingle(
      float x,
      float y,
      float maxWidth,
      String[] options,
      String selected,
      int mouseX,
      int mouseY
   ) {
      return this.renderSegments(
         x,
         y,
         maxWidth,
         options,
         idx -> options[idx] != null && options[idx].equals(selected),
         true,
         mouseX,
         mouseY
      );
   }

   public int renderSegments(
      float x,
      float y,
      float maxWidth,
      String[] options,
      OptionPredicate selectedFn,
      int mouseX,
      int mouseY
   ) {
      return this.renderSegments(x, y, maxWidth, options, selectedFn, true, mouseX, mouseY);
   }

   public int renderSegments(
      float x,
      float y,
      float maxWidth,
      String[] options,
      OptionPredicate selectedFn,
      boolean drawVisuals,
      int mouseX,
      int mouseY
   ) {
      this.cachedHits.clear();
      this.lastX = x;
      this.lastY = y;
      this.lastWidth = maxWidth;
      this.lastOptionCount = options == null ? 0 : options.length;
      if (options == null || options.length == 0) {
         this.lastHeight = 23.0F;
         return 23;
      }

      this.ensureAnimCapacity(options.length);
      CustomFontUtil font = FontManager.interMedium;
      int accent = UiTheme.accentColor();
      float chipH = 23.0F;
      float chipPadX = 11.0F;
      float gapX = 6.0F;
      float gapY = 6.0F;

      float curX = x;
      float curY = y;
      for (int i = 0; i < options.length; i++) {
         String opt = options[i];
         if (opt == null || opt.isEmpty()) {
            continue;
         }
         float chipW = Math.max(40.0F, font.getStringWidth(opt) + chipPadX * 2.0F);
         if (curX > x && curX + chipW > x + maxWidth) {
            curX = x;
            curY += chipH + gapY;
         }

         this.cachedHits.add(new SegmentHit(i, curX, curY, chipW, chipH));

         if (drawVisuals) {
            boolean selected = selectedFn.isSelected(i);
            boolean hovered = RenderUtil.isHovered(curX, curY, chipW, chipH, mouseX, mouseY);
            boolean pressed = hovered && UiAnimation.isPrimaryMouseDown();
            this.hoverAnims[i] = UiTheme.smoothState(this.hoverAnims[i], hovered ? 1.0F : 0.0F);
            this.selectAnims[i] = UiTheme.smoothState(this.selectAnims[i], selected ? 1.0F : 0.0F);
            this.pressAnims[i] = UiTheme.smoothPress(this.pressAnims[i], pressed);
            float hovT = this.hoverAnims[i];
            float selT = this.selectAnims[i];
            float prsT = this.pressAnims[i];

            if (selT > 0.04F) {
               GlassRenderer.drawSoftShadow(curX, curY, chipW, chipH, 6.0F, 6.0F, 1.0F, faded(UiTheme.withAlpha(accent, Math.round(48.0F * selT))));
            }

            // Free-standing pills: each one is its own raised glass chip (LEVEL_CONTROL_RAISED
            // material inside drawInteractiveSurface), crossfaded from the idle veil to an
            // accent-pulled veil as it becomes selected.
            int idleFill = UiTheme.glassControlVeilArgb(UiTheme.LEVEL_CONTROL_RAISED, hovT, prsT, accent);
            int idleBot = UiTheme.lerpArgb(idleFill, UiTheme.glassRecessedDepthRgb(), 0.25F);
            int selTop = UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, hovT, Math.max(selT, prsT), UiTheme.lerpArgb(accent, 0xFFEAE4FF, 0.12F + 0.08F * hovT), 0.88F);
            int selBot = UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, hovT, Math.max(selT, prsT), UiTheme.lerpArgb(UiTheme.accentDeep(), 0xFF120F28, 0.28F), 0.88F);
            int fillTop = UiTheme.lerpArgb(idleFill, selTop, selT);
            int fillBot = UiTheme.lerpArgb(idleBot, selBot, selT);
            int border = UiTheme.lerpArgb(
               UiTheme.glassBorderFocusedOrHover(hovT * 0.72F, accent),
               UiTheme.withAlpha(UiTheme.accentSoftGlow(), 225),
               selT
            );

            GlassRenderer.drawInteractiveSurface(
               curX,
               curY,
               chipW,
               chipH,
               6.0F,
               faded(fillTop),
               faded(fillBot),
               faded(border),
               UiTheme.BORDER_THICKNESS,
               UiTheme.glassTopHighlightAlpha(Math.max(hovT, selT)),
               Math.max(hovT, selT * 0.35F),
               prsT,
               true,
               (float)mouseX,
               (float)mouseY,
               faded(accent)
            );

            int fg = UiTheme.lerpArgb(UiTheme.secondaryTextColor(), UiTheme.TEXT_PRIMARY, Math.max(selT, hovT));
            font.drawCenteredStringInBox(opt, (double)curX, (double)(curY + prsT * 0.35F), (double)chipW, (double)chipH, faded(fg));
         }
         curX += chipW + gapX;
      }

      this.lastHeight = (curY + chipH) - y;
      return Math.round(this.lastHeight);
   }

   public int hitTest(int mouseX, int mouseY) {
      for (SegmentHit hit : this.cachedHits) {
         if (RenderUtil.isHovered(hit.x, hit.y, hit.w, hit.h, mouseX, mouseY)) {
            return hit.optionIndex;
         }
      }
      return -1;
   }

   public int hitTest(float x, float y, float maxWidth, String[] options, int mouseX, int mouseY) {
      if (!this.cachedHits.isEmpty()) {
         return this.hitTest(mouseX, mouseY);
      }
      if (options == null || options.length == 0) {
         return -1;
      }
      CustomFontUtil font = FontManager.interMedium;
      float chipH = 23.0F;
      float chipPadX = 11.0F;
      float gapX = 6.0F;
      float gapY = 6.0F;

      float curX = x;
      float curY = y;
      for (int i = 0; i < options.length; i++) {
         String opt = options[i];
         if (opt == null || opt.isEmpty()) {
            continue;
         }
         float chipW = Math.max(40.0F, font.getStringWidth(opt) + chipPadX * 2.0F);
         if (curX > x && curX + chipW > x + maxWidth) {
            curX = x;
            curY += chipH + gapY;
         }
         if (RenderUtil.isHovered(curX, curY, chipW, chipH, mouseX, mouseY)) {
            return i;
         }
         curX += chipW + gapX;
      }
      return -1;
   }

   public float getLastHeight() {
      return this.lastHeight;
   }
}
