package net.augustus.ui.menu;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.augustus.ui.TabbedMenuScreen;
import net.augustus.ui.modern.font.InterFontUtil;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.ui.widgets.ScrollBar;

/**
 * Credits view presenting contributor groups inside responsive frosted glass cards
 * with hover illumination, crisp typography, and Tabler icons.
 */
public class CreditsMenuTab extends ClientMenuTab {
   private static final int LINE_UNIT = 13;
   private static final float OUTER_PAD = 12.0F;
   private static final float CARD_GAP = 10.0F;
   private static final float ROW_GAP = 10.0F;

   private static final class SimpleCreditsSection extends CreditsSection {
      private final List members;
      private final List links;

      SimpleCreditsSection(String title, List members, List links) {
         super(title);
         this.members = members;
         this.links = links;
      }

      @Override
      public List getMemberLines() {
         return this.members;
      }

      @Override
      public List getSocialLinks() {
         return this.links;
      }
   }

   public final ScrollBar scrollBar;
   private final List sections = Arrays.asList(new CreditsSection[]{
      new DeveloperCreditsSection(),
      new ContributorCreditsSection(),
      new SimpleCreditsSection("Redesigner", Arrays.asList("§eDream Visuals", "Augustus Redesigner."), Collections.emptyList()),
      new SimpleCreditsSection("OpenAugustus", Arrays.asList("§eSkid Solutions", "Provided OpenAugustus."), Collections.emptyList()),
      new SimpleCreditsSection("Sponsor", Arrays.asList("§eHitsConfigs", "Official Sponsor."), Collections.emptyList())
   });

   private float panelX;
   private float panelTop;
   private float panelW;
   private float panelH;

   private List getSections() {
      return this.sections;
   }

   @Override
   public LucideIcon getTabIcon() {
      return LucideIcon.INFO_CIRCLE;
   }

   public CreditsMenuTab() {
      super("Credits");
      this.scrollBar = new ScrollBar(LINE_UNIT);
   }

   private void layoutGeometry() {
      this.panelX = TabbedMenuScreen.contentX();
      this.panelTop = TabbedMenuScreen.bodyTop();
      this.panelW = TabbedMenuScreen.contentW();
      this.panelH = Math.max(96.0F, TabbedMenuScreen.bodyBottom() - this.panelTop);
   }

   private float cardHeight(CreditsSection section) {
      float line = Math.max(13.0F, menuSmallHeight() + 3.0F);
      float height = OUTER_PAD + menuHeadingHeight() + 10.0F;
      height += (float)section.getMemberLines().size() * line;
      if (!section.getSocialLinks().isEmpty()) {
         height += 12.0F + line + (float)section.getSocialLinks().size() * line;
      }
      return height + OUTER_PAD;
   }

   private float flowHeight(float cardW) {
      float height = 4.0F;
      int columns = this.panelW >= 540.0F ? 2 : 1;
      List list = this.getSections();
      for (int i = 0; i < list.size(); i += columns) {
         float rowHeight = this.cardHeight((CreditsSection)list.get(i));
         if (columns == 2 && i + 1 < list.size()) {
            rowHeight = Math.max(rowHeight, this.cardHeight((CreditsSection)list.get(i + 1)));
         }
         height += rowHeight + ROW_GAP;
      }
      return height;
   }

   @Override
   public void initContent() {
      this.layoutGeometry();
      float innerW = Math.max(120.0F, this.panelW - 14.0F);
      int columns = this.panelW >= 540.0F ? 2 : 1;
      float cardW = (innerW - (columns == 2 ? CARD_GAP : 0.0F)) / (float)columns;
      this.scrollBar.setItemCount(Math.max(1, (int)Math.ceil((double)(this.flowHeight(cardW) / (float)LINE_UNIT))));
      this.scrollBar.setBounds(
         Math.round(this.panelX + this.panelW - 8.0F),
         Math.round(this.panelTop + 4.0F),
         Math.round(this.panelX + this.panelW - 3.0F),
         Math.round(this.panelTop + this.panelH - 4.0F)
      );
   }

   @Override
   public void drawContent(int mouseX, int mouseY, float partialTicks) {
      this.layoutGeometry();
      float innerW = Math.max(120.0F, this.panelW - 14.0F);
      int columns = this.panelW >= 540.0F ? 2 : 1;
      float cardW = (innerW - (columns == 2 ? CARD_GAP : 0.0F)) / (float)columns;

      this.scrollBar.setItemCount(Math.max(1, (int)Math.ceil((double)(this.flowHeight(cardW) / (float)LINE_UNIT))));
      this.scrollBar.setBounds(
         Math.round(this.panelX + this.panelW - 8.0F),
         Math.round(this.panelTop + 4.0F),
         Math.round(this.panelX + this.panelW - 3.0F),
         Math.round(this.panelTop + this.panelH - 4.0F)
      );

      List list = this.getSections();
      if (list.isEmpty()) {
         this.menuEmptyState(this.panelX, this.panelTop, this.panelW, this.panelH,
            LucideIcon.USERS, "No credits listed", "Contributor details will appear here.", true);
         return;
      }

      float line = Math.max(13.0F, menuSmallHeight() + 3.0F);
      float top = this.panelTop + (float)((int)this.scrollBar.getClampedScrollOffset());
      float viewTop = this.panelTop;
      float viewBottom = this.panelTop + this.panelH;

      this.enableScissor(
         Math.round(this.panelX - 2.0F),
         Math.round(viewTop),
         Math.round(this.panelX + innerW + 2.0F),
         Math.round(viewBottom)
      );

      for (int i = 0; i < list.size(); i += columns) {
         CreditsSection left = (CreditsSection)list.get(i);
         CreditsSection right = (columns == 2 && i + 1 < list.size()) ? (CreditsSection)list.get(i + 1) : null;
         float rowHeight = this.cardHeight(left);
         if (right != null) {
            rowHeight = Math.max(rowHeight, this.cardHeight(right));
         }

         if (top + rowHeight >= viewTop - 10.0F && top <= viewBottom + 10.0F) {
            this.drawCreditsCard("credits." + i, left, this.panelX, top, cardW, rowHeight, line, mouseX, mouseY, viewTop, viewBottom);
            if (right != null) {
               this.drawCreditsCard("credits." + (i + 1), right, this.panelX + cardW + CARD_GAP, top, cardW, rowHeight, line, mouseX, mouseY, viewTop, viewBottom);
            }
         }
         top += rowHeight + ROW_GAP;
      }

      this.disableScissor();
      this.scrollBar.updateAndDraw();
   }

   private void drawCreditsCard(String id, CreditsSection section, float x, float y, float w, float h, float line,
         int mouseX, int mouseY, float viewTop, float viewBottom) {
      boolean hovered = mouseY >= viewTop && mouseY <= viewBottom && menuHovered(x, y, w, h, mouseX, mouseY);
      float hover = this.menuHover(id, hovered);
      this.menuGlassPanel(x, y, w, h, hover, 1.0F);

      float padX = x + OUTER_PAD;
      float cursorY = y + OUTER_PAD;
      float textW = w - OUTER_PAD * 2.0F;

      LucideIcon.USERS.draw(padX, cursorY + 1.0F, 12.0F, UiTheme.withAlpha(UiTheme.accentSoftGlow(), 0xCC), false);
      menuHeading(menuFit(menuHeadingFont(), section.getSectionTitle(), textW - 18.0F), padX + 17.0F, cursorY, 0xFFF8FAFC);
      cursorY += menuHeadingHeight() + 6.0F;

      GlassRenderer.drawSeparator(padX, cursorY, x + w - OUTER_PAD, cursorY, 1.0F, UiTheme.GLASS_DIVIDER);
      cursorY += 5.0F;

      InterFontUtil small = menuSmallFont();
      List members = section.getMemberLines();
      for (int m = 0; m < members.size(); m++) {
         String member = (String)members.get(m);
         small.drawString(menuFit(small, member, textW), padX, cursorY, 0xFFE2E8F0);
         cursorY += line;
      }

      List links = section.getSocialLinks();
      if (!links.isEmpty()) {
         cursorY += 5.0F;
         GlassRenderer.drawSeparator(padX, cursorY, x + w - OUTER_PAD, cursorY, 1.0F, UiTheme.withAlpha(UiTheme.accentColor(), 50));
         cursorY += 6.0F;

         menuCaptionFont().drawString("LINKS", padX, cursorY, UiTheme.accentSoftGlow());
         cursorY += line;

         for (int l = 0; l < links.size(); l++) {
            String link = (String)links.get(l);
            LucideIcon.EXTERNAL_LINK.draw(padX, cursorY + 1.0F, 9.5F, 0xCC94A3B8, false);
            small.drawString(menuFit(small, link, textW - 14.0F), padX + 14.0F, cursorY, 0xFFCBD5E1);
            cursorY += line;
         }
      }
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
