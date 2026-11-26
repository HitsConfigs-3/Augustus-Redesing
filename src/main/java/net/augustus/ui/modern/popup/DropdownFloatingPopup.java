package net.augustus.ui.modern.popup;

import java.util.List;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.render.RenderUtil;
import net.augustus.ui.modern.components.ModernCheckbox;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;
import org.lwjgl.opengl.GL11;

/**
 * Floating Liquid Glass dropdown popup rendered on top of scrolled panels with live Kawase Blur,
 * visible crisp borders, scroll support, and single or multi-select support.
 */
public final class DropdownFloatingPopup implements FloatingPopup {
   public interface Source {
      List<String> getOptions();

      boolean isSelected(int index);

      /** Called on click; returns true if the popup should close after selection. */
      boolean select(int index);

      boolean isMultiSelect();
   }

   private static final int MAX_VISIBLE_ROWS = 8;
   private static final float ROW_HEIGHT = 24.0F;
   private static final float PAD = 6.0F;

   private final Source source;
   private final PopupHost host;
   private float x;
   private float y;
   private float width;
   private float height;
   private int scrollOffset = 0;
   private float openAnim = 0.0F;
   private ModernCheckbox[] checkboxes = new ModernCheckbox[0];

   public DropdownFloatingPopup(PopupHost host, Source source) {
      this.host = host;
      this.source = source;
   }

   public void updateAnchor(float anchorX, float anchorY, float minWidth, int containerW, int containerH) {
      List<String> options = this.source.getOptions();
      CustomFontUtil font = FontManager.interMedium;
      float neededW = minWidth;
      for (String opt : options) {
         if (opt != null) {
            neededW = Math.max(neededW, font.getStringWidth(opt) + 48.0F);
         }
      }
      this.width = Math.min(Math.max(130.0F, neededW), Math.max(160.0F, containerW - 40.0F));
      int visibleRows = Math.min(MAX_VISIBLE_ROWS, Math.max(1, options.size()));
      this.height = visibleRows * ROW_HEIGHT + PAD * 2.0F;

      this.x = Math.max(16.0F, Math.min(anchorX, containerW - this.width - 14.0F));
      if (anchorY + this.height + 8.0F <= containerH - 12.0F) {
         this.y = anchorY + 4.0F;
      } else {
         this.y = Math.max(16.0F, anchorY - this.height - 28.0F);
      }

      if (this.checkboxes.length != options.size()) {
         this.checkboxes = new ModernCheckbox[options.size()];
         for (int i = 0; i < options.size(); i++) {
            this.checkboxes[i] = new ModernCheckbox();
         }
      }

      int maxScroll = Math.max(0, options.size() - visibleRows);
      this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset));
   }

   @Override
   public void draw(int mouseX, int mouseY) {
      this.openAnim = UiAnimation.smooth(this.openAnim, 1.0F, 22.0F);
      float eased = UiAnimation.easeOutCubic(this.openAnim);
      int accent = UiTheme.accentColor();

      float scale = 0.94F + 0.06F * eased;
      float pivotX = this.x + this.width * 0.5F;
      float pivotY = this.y;
      GL11.glPushMatrix();
      GL11.glTranslatef(pivotX, pivotY, 0.0F);
      GL11.glScalef(scale, scale, 1.0F);
      GL11.glTranslatef(-pivotX, -pivotY, 0.0F);

      GlassRenderer.drawFloatingGlassPopup(
         this.x,
         this.y,
         this.width,
         this.height,
         UiTheme.POPUP_RADIUS,
         UiTheme.THICK_BORDER_THICKNESS,
         UiTheme.GLASS_BORDER_STRONG,
         eased
      );

      List<String> options = this.source.getOptions();
      int visibleRows = Math.min(MAX_VISIBLE_ROWS, Math.max(1, options.size()));
      CustomFontUtil font = FontManager.interMedium;
      boolean hasScrollbar = options.size() > visibleRows;
      float rowW = this.width - PAD * 2.0F - (hasScrollbar ? 7.0F : 0.0F);

      for (int r = 0; r < visibleRows; r++) {
         int idx = this.scrollOffset + r;
         if (idx >= options.size()) {
            break;
         }
         String option = options.get(idx);
         float ry = this.y + PAD + r * ROW_HEIGHT;
         float rx = this.x + PAD;
         float rh = ROW_HEIGHT - 2.0F;
         boolean hovered = RenderUtil.isHovered(rx, ry, rowW, rh, mouseX, mouseY);
         boolean selected = this.source.isSelected(idx);
         boolean pressed = hovered && UiAnimation.isPrimaryMouseDown();
         float hovT = UiTheme.smoothState("dd_hov_" + System.identityHashCode(this) + "_" + idx, hovered ? 1.0F : 0.0F);
         float selT = UiTheme.smoothState("dd_sel_" + System.identityHashCode(this) + "_" + idx, selected ? 1.0F : 0.0F);
         float prsT = UiTheme.smoothPress("dd_prs_" + System.identityHashCode(this) + "_" + idx, 0.0F, pressed);

         if (hovT > 0.02F || (selT > 0.02F && !this.source.isMultiSelect())) {
            float activeMix = this.source.isMultiSelect() ? hovT : Math.max(hovT, selT);
            // Rows are raised controls inside the popup: their state fills are level veils (accent
            // pulled by the interaction mix) drawn over the row's own material, so a highlighted row
            // stays glass and the popup's backdrop is still visible through it.
            int hovFill = UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, hovT, prsT, accent, 0.35F);
            int selTop = UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, hovT, Math.max(selT, prsT), UiTheme.lerpArgb(accent, 0xFF282058, 0.25F), 0.70F);
            int selBot = UiTheme.glassControlTintArgb(UiTheme.LEVEL_CONTROL_RAISED, hovT, Math.max(selT, prsT), UiTheme.accentDeep(), 0.70F);
            int topFill = this.source.isMultiSelect() ? hovFill : UiTheme.lerpArgb(hovFill, selTop, selT);
            int botFill = this.source.isMultiSelect() ? UiTheme.lerpArgb(hovFill, UiTheme.glassRecessedDepthRgb(), 0.25F) : UiTheme.lerpArgb(UiTheme.lerpArgb(hovFill, UiTheme.glassRecessedDepthRgb(), 0.25F), selBot, selT);
            int border = UiTheme.withAlpha(UiTheme.accentSoftGlow(), Math.round(185.0F * activeMix));
            GlassRenderer.drawInteractiveSurface(
               rx,
               ry,
               rowW,
               rh,
               5.5F,
               UiTheme.scaleAlpha(topFill, eased * Math.min(1.0F, activeMix * 1.15F)),
               UiTheme.scaleAlpha(botFill, eased * Math.min(1.0F, activeMix * 1.15F)),
               UiTheme.scaleAlpha(border, eased * activeMix),
               1.0F,
               UiTheme.glassTopHighlightAlpha(activeMix) * eased,
               hovT,
               prsT,
               true,
               (float)mouseX,
               (float)mouseY,
               accent
            );
         }

         int labelColor = UiTheme.scaleAlpha(UiTheme.lerpArgb(UiTheme.secondaryTextColor(), UiTheme.TEXT_PRIMARY, Math.max(selT, hovT)), eased);
         float textNudge = prsT * 0.4F;
         if (this.source.isMultiSelect()) {
            this.checkboxes[idx].render(rx + 6.0F, ry + 3.0F + textNudge, 16.0F, selected, mouseX, mouseY);
            font.drawVerticallyCenteredString(option, (double)(rx + 28.0F), (double)(ry + textNudge), (double)rh, labelColor);
         } else {
            font.drawVerticallyCenteredString(option, (double)(rx + 8.0F + selT * 2.0F), (double)(ry + textNudge), (double)rh, labelColor);
            if (selT > 0.05F) {
               LucideIcon.CHECK.drawCentered(rx + rowW - 12.0F, ry + rh * 0.5F + textNudge, 11.5F, UiTheme.scaleAlpha(UiTheme.accentSoftGlow(), eased * selT));
            }
         }
      }

      if (hasScrollbar) {
         float trackX = this.x + this.width - 8.0F;
         float trackY = this.y + PAD;
         float trackH = this.height - PAD * 2.0F;
         GlassRenderer.drawRoundedRect(trackX, trackY, 3.5F, trackH, 1.75F, UiTheme.scaleAlpha(0x36080814, eased));
         float thumbH = Math.max(18.0F, trackH * ((float)visibleRows / (float)options.size()));
         int maxScroll = Math.max(1, options.size() - visibleRows);
         float thumbY = trackY + (trackH - thumbH) * ((float)this.scrollOffset / (float)maxScroll);
         GlassRenderer.drawRoundedGradientBox(
            trackX,
            thumbY,
            3.5F,
            thumbH,
            1.75F,
            UiTheme.scaleAlpha(UiTheme.withAlpha(UiTheme.accentSoftGlow(), 225), eased),
            UiTheme.scaleAlpha(UiTheme.withAlpha(accent, 225), eased),
            0,
            0.0F,
            0.0F
         );
      }

      GL11.glPopMatrix();
   }

   @Override
   public boolean contains(int mouseX, int mouseY) {
      return RenderUtil.isHovered(this.x, this.y, this.width, this.height, mouseX, mouseY);
   }

   @Override
   public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
      if (mouseButton != 0) {
         return false;
      }
      List<String> options = this.source.getOptions();
      int visibleRows = Math.min(MAX_VISIBLE_ROWS, Math.max(1, options.size()));
      float rowW = this.width - PAD * 2.0F;
      for (int r = 0; r < visibleRows; r++) {
         int idx = this.scrollOffset + r;
         if (idx >= options.size()) {
            break;
         }
         float ry = this.y + PAD + r * ROW_HEIGHT;
         if (RenderUtil.isHovered(this.x + PAD, ry, rowW, ROW_HEIGHT - 2.0F, mouseX, mouseY)) {
            boolean shouldClose = this.source.select(idx);
            if (shouldClose) {
               this.host.close();
            }
            return true;
         }
      }
      return true;
   }

   @Override
   public boolean handleScroll(int delta) {
      List<String> options = this.source.getOptions();
      int visibleRows = Math.min(MAX_VISIBLE_ROWS, Math.max(1, options.size()));
      int maxScroll = Math.max(0, options.size() - visibleRows);
      if (maxScroll == 0) {
         return false;
      }
      this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset - (delta > 0 ? 1 : -1)));
      return true;
   }

   @Override
   public void onClosed() {
      this.openAnim = 0.0F;
   }
}
