package net.augustus.ui.menu;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.augustus.Augustus;
import net.augustus.changelog.ChangelogEntry;
import net.augustus.changelog.ChangelogEntryType;
import net.augustus.ui.TabbedMenuScreen;
import net.augustus.ui.modern.font.InterFontUtil;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.MenuScreenSkin;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.ui.widgets.ScrollBar;

/**
 * Changelog view rendered inside a single frosted glass document container with
 * Latest version badge, semantic dot markers, and cursor-illuminated entry hover states.
 */
public class ChangelogMenuTab extends ClientMenuTab {
   private static final int LINE_UNIT = 13;
   private static final float PANEL_PAD_X = 14.0F;
   private static final float PANEL_PAD_RIGHT = 18.0F;
   private static final float PANEL_PAD_Y = 12.0F;
   private static final float HEADER_GAP = 6.0F;
   private static final float ENTRY_GAP = 3.0F;
   private static final float SECTION_GAP = 14.0F;

   private final ScrollBar scrollBar;
   private final List displaySections = new ArrayList();
   private float lastWrapWidth = -1.0F;

   private float panelX;
   private float panelTop;
   private float panelW;
   private float panelH;

   private static final class DisplayEntry {
      final ChangelogEntryType type;
      final List lines;
      final float height;

      DisplayEntry(ChangelogEntryType type, List lines, float height) {
         this.type = type;
         this.lines = lines;
         this.height = height;
      }
   }

   private static final class DisplaySection {
      final String versionTitle;
      final List entries;
      final float totalHeight;

      DisplaySection(String versionTitle, List entries, float totalHeight) {
         this.versionTitle = versionTitle;
         this.entries = entries;
         this.totalHeight = totalHeight;
      }
   }

   public ChangelogMenuTab() {
      super("Changelog");
      this.scrollBar = new ScrollBar(LINE_UNIT);
   }

   @Override
   public LucideIcon getTabIcon() {
      return LucideIcon.FILE_TEXT;
   }

   private void layoutGeometry() {
      this.panelX = TabbedMenuScreen.contentX();
      this.panelTop = TabbedMenuScreen.bodyTop();
      this.panelW = TabbedMenuScreen.contentW();
      this.panelH = Math.max(80.0F, TabbedMenuScreen.bodyBottom() - this.panelTop);
   }

   private void rebuildDisplaySections(float textWrapWidth) {
      this.displaySections.clear();
      this.lastWrapWidth = textWrapWidth;

      InterFontUtil font = menuSmallFont();
      float linePitch = Math.max(12.5F, menuSmallHeight() + 2.5F);
      float headingPitch = menuHeadingHeight() + HEADER_GAP;

      List rawSections = Augustus.getInstance().getChangeLogLoader().sections;
      for (int s = 0; s < rawSections.size(); s++) {
         ChangeLogSection section = (ChangeLogSection)rawSections.get(s);
         List displayEntries = new ArrayList();
         float secHeight = headingPitch;

         List rawEntries = section.getEntries();
         for (int e = 0; e < rawEntries.size(); e++) {
            ChangelogEntry entry = (ChangelogEntry)rawEntries.get(e);
            String cleaned = cleanEntryText(entry.getRawText());
            List wrapped = wrapText(cleaned, font, textWrapWidth);
            float entryH = Math.max(1, wrapped.size()) * linePitch + ENTRY_GAP;
            displayEntries.add(new DisplayEntry(entry.getType(), wrapped, entryH));
            secHeight += entryH;
         }

         if (s < rawSections.size() - 1) {
            secHeight += SECTION_GAP;
         }
         this.displaySections.add(new DisplaySection(section.getVersionTitle(), displayEntries, secHeight));
      }
   }

   private float totalFlowHeight() {
      float total = PANEL_PAD_Y * 2.0F;
      for (int i = 0; i < this.displaySections.size(); i++) {
         total += ((DisplaySection)this.displaySections.get(i)).totalHeight;
      }
      return total;
   }

   private static String cleanEntryText(String raw) {
      if (raw == null) {
         return "";
      }
      String text = raw.trim();
      if (text.startsWith("-")) {
         text = text.substring(1).trim();
      }
      if (!text.isEmpty() && Character.isLowerCase(text.charAt(0))) {
         text = Character.toUpperCase(text.charAt(0)) + text.substring(1);
      }
      return text;
   }

   private static List wrapText(String text, InterFontUtil font, float maxWidth) {
      List lines = new ArrayList();
      if (text == null || text.isEmpty()) {
         lines.add("");
         return lines;
      }
      String[] words = text.split("\\s+");
      StringBuilder current = new StringBuilder();
      for (int i = 0; i < words.length; i++) {
         String word = words[i];
         if (current.length() == 0) {
            current.append(word);
         } else {
            String candidate = current + " " + word;
            if (font.getStringWidth(candidate) <= maxWidth) {
               current.setLength(0);
               current.append(candidate);
            } else {
               lines.add(current.toString());
               current.setLength(0);
               current.append(word);
            }
         }
      }
      if (current.length() > 0) {
         lines.add(current.toString());
      }
      return lines;
   }

   private static int dotColorForType(ChangelogEntryType type) {
      if (type == null) {
         return 0xFFA855F7;
      }
      switch (type) {
         case ADDED:
            return 0xFFA855F7;
         case IMPROVED:
            return 0xFFFBBF24;
         case FIXED:
            return 0xFF38BDF8;
         case REMOVED:
            return 0xFFF87171;
         default:
            return 0xFFA855F7;
      }
   }

   @Override
   public void initContent() {
      this.layoutGeometry();
      float textWrap = Math.max(120.0F, this.panelW - PANEL_PAD_X - PANEL_PAD_RIGHT - 18.0F);
      this.rebuildDisplaySections(textWrap);

      this.scrollBar.setItemCount(Math.max(1, (int)Math.ceil((double)(this.totalFlowHeight() / (float)LINE_UNIT))));
      this.scrollBar.setBounds(
         Math.round(this.panelX + this.panelW - 10.0F),
         Math.round(this.panelTop + 10.0F),
         Math.round(this.panelX + this.panelW - 5.0F),
         Math.round(this.panelTop + this.panelH - 10.0F)
      );
   }

   @Override
   public void drawContent(int mouseX, int mouseY, float partialTicks) {
      this.layoutGeometry();
      float textWrap = Math.max(120.0F, this.panelW - PANEL_PAD_X - PANEL_PAD_RIGHT - 18.0F);
      if (Math.abs(textWrap - this.lastWrapWidth) > 1.0F || this.displaySections.isEmpty()) {
         this.rebuildDisplaySections(textWrap);
         this.scrollBar.setItemCount(Math.max(1, (int)Math.ceil((double)(this.totalFlowHeight() / (float)LINE_UNIT))));
      }
      this.scrollBar.setBounds(
         Math.round(this.panelX + this.panelW - 10.0F),
         Math.round(this.panelTop + 10.0F),
         Math.round(this.panelX + this.panelW - 5.0F),
         Math.round(this.panelTop + this.panelH - 10.0F)
      );

      this.menuGlassPanel(this.panelX, this.panelTop, this.panelW, this.panelH, 0.0F, 1.0F);

      float viewTop = this.panelTop + 4.0F;
      float viewBottom = this.panelTop + this.panelH - 4.0F;
      if (this.displaySections.isEmpty()) {
         this.menuEmptyState(this.panelX, this.panelTop, this.panelW, this.panelH,
            LucideIcon.FILE_TEXT, "No changelog entries", "Release notes will appear here.", false);
         return;
      }

      InterFontUtil bodyFace = menuSmallFont();
      float linePitch = Math.max(12.5F, menuSmallHeight() + 2.5F);
      float headingPitch = menuHeadingHeight() + HEADER_GAP;
      float contentLeft = this.panelX + PANEL_PAD_X;
      float contentRight = this.panelX + this.panelW - PANEL_PAD_RIGHT;
      float rowW = contentRight - contentLeft;

      this.enableScissor(
         Math.round(this.panelX + 2.0F),
         Math.round(viewTop),
         Math.round(this.panelX + this.panelW - 10.0F),
         Math.round(viewBottom)
      );

      float y = this.panelTop + PANEL_PAD_Y + (float)((int)this.scrollBar.getClampedScrollOffset());
      for (int s = 0; s < this.displaySections.size(); s++) {
         DisplaySection section = (DisplaySection)this.displaySections.get(s);
         float secBottom = y + section.totalHeight;
         if (secBottom >= viewTop - 20.0F && y <= viewBottom + 20.0F) {
            // Version header bullet dot + version title + Latest badge
            int headerDotColor = s == 0 ? 0xFFA855F7 : 0xCC94A3B8;
            if (s == 0) {
               GlassRenderer.drawCircle(contentLeft + 3.0F, y + menuHeadingHeight() * 0.52F, 4.5F, UiTheme.withAlpha(headerDotColor, 65));
            }
            GlassRenderer.drawCircle(contentLeft + 3.0F, y + menuHeadingHeight() * 0.52F, 2.2F, headerDotColor);

            float titleX = contentLeft + 11.0F;
            menuHeading(section.versionTitle, titleX, y, 0xFFF8FAFC);

            if (s == 0) {
               float badgeX = titleX + menuHeadingFont().getStringWidth(section.versionTitle) + 8.0F;
               float badgeY = y + (menuHeadingHeight() - (menuCaptionHeight() + 5.0F)) * 0.5F;
               menuBadge("Latest", badgeX, badgeY, 0xCC7C3AED, 0xFFFFFFFF);
            }

            float entryY = y + headingPitch;
            for (int e = 0; e < section.entries.size(); e++) {
               DisplayEntry entry = (DisplayEntry)section.entries.get(e);
               float entryBottom = entryY + entry.height;
               if (entryBottom >= viewTop - 10.0F && entryY <= viewBottom + 10.0F) {
                  boolean inViewport = mouseY >= viewTop && mouseY <= viewBottom;
                  boolean hovered = inViewport && menuHovered(contentLeft - 4.0F, entryY - 1.5F, rowW + 6.0F, entry.height, mouseX, mouseY);
                  float hover = this.menuHover("cl." + s + "." + e, hovered);

                  if (hover > 0.015F) {
                     GlassRenderer.drawMenuGlassBox(
                        contentLeft - 4.0F,
                        entryY - 1.5F,
                        rowW + 6.0F,
                        entry.height,
                        5.0F,
                        UiTheme.withAlpha(MenuScreenSkin.FILL_TOP, Math.round(hover * 85.0F)),
                        UiTheme.withAlpha(MenuScreenSkin.FILL_BOTTOM, Math.round(hover * 70.0F)),
                        UiTheme.withAlpha(UiTheme.accentColor(), Math.round(hover * 95.0F)),
                        1.0F,
                        mouseX,
                        mouseY,
                        MenuScreenSkin.LIGHT_PEAK * hover * 0.7F,
                        MenuScreenSkin.LIGHT_RADIUS,
                        UiTheme.accentSoftGlow()
                     );
                  }

                  int dotColor = dotColorForType(entry.type);
                  float dotCenterX = contentLeft + 4.0F;
                  float dotCenterY = entryY + menuSmallHeight() * 0.52F;
                  if (hover > 0.05F) {
                     GlassRenderer.drawCircle(dotCenterX, dotCenterY, 3.6F, UiTheme.withAlpha(dotColor, Math.round(70.0F * hover)));
                  }
                  GlassRenderer.drawCircle(dotCenterX, dotCenterY, 2.0F, dotColor);

                  int textColor = UiTheme.lerpArgb(0xFFD8DEE9, 0xFFFFFFFF, hover);
                  float lineY = entryY;
                  for (int l = 0; l < entry.lines.size(); l++) {
                     bodyFace.drawString((String)entry.lines.get(l), contentLeft + 13.0F, lineY, textColor);
                     lineY += linePitch;
                  }
               }
               entryY += entry.height;
            }

            if (s < this.displaySections.size() - 1) {
               float sepY = secBottom - SECTION_GAP * 0.5F;
               GlassRenderer.drawSeparator(contentLeft, sepY, contentRight, sepY, 1.0F, UiTheme.GLASS_DIVIDER);
            }
         }
         y += section.totalHeight;
      }

      this.disableScissor();
      this.scrollBar.updateAndDraw();
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      super.mouseClicked(mouseX, mouseY, mouseButton);
      this.scrollBar.handleMouseClick((double)mouseX, (double)mouseY);
   }

   @Override
   public void handleMouseInput() throws IOException {
      super.handleMouseInput();
      this.scrollBar.handleMouseWheel();
   }

   @Override
   public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
      super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
      this.scrollBar.handleMouseDrag((double)mouseY);
   }
}
