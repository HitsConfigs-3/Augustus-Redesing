package net.augustus.ui;

import java.awt.Color;
import java.io.IOException;
import java.util.List;
import net.augustus.Augustus;
import net.augustus.ClientManagers;
import net.augustus.font.FontManager;
import net.augustus.render.RenderUtil;
import net.augustus.ui.menu.ClientMenuTab;
import net.augustus.ui.menu.MenuBackdrop;
import net.augustus.ui.modern.components.GlassMenuButton;
import net.augustus.ui.modern.font.InterFontUtil;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.MaskedTexture;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.utils.network.ProxyEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

/**
 * Unified Augustus Redesing frame for the client menu tabs:
 * frosted blurred backdrop, top brand header with Augustus mark + tab title + status,
 * left frosted sidebar glass card with animated sliding gradient selection pill and
 * cursor-tracking hover glow, and bottom-left back icon control.
 */
public class TabbedMenuScreen extends AugustusGuiScreen {
   public static final int TAB_BUTTON_HEIGHT = 24;
   public static final int HEADER_HEIGHT = 44;
   public static final int TAB_SPACING = 4;
   public static final int SIDEBAR_WIDTH = 152;

   private static final float TOP_BAR_H = 30.0F;
   private static final float TITLE_ICON = 13.5F;
   private static final float MAX_PAGE_WIDTH = 1080.0F;
   private static final MaskedTexture BRAND_MARK = MaskedTexture.of("/assets/augustus/textures/menu/logo_mark.png");

   private static final int RAIL_PLATE_LEADING_R_PERCENT = 57;
   private static final int RAIL_PLATE_LEADING_G_PERCENT = 59;
   private static final int RAIL_PLATE_LEADING_B_PERCENT = 69;
   private static final int RAIL_PLATE_TRAILING_R_PERCENT = 39;
   private static final int RAIL_PLATE_TRAILING_G_PERCENT = 55;
   private static final int RAIL_PLATE_TRAILING_B_PERCENT = 42;

   private final List tabs;
   private final MenuBackdrop backdrop = MenuBackdrop.getShared();
   private int selectedTabIndex;
   private float activePlateY = -1.0F;

   private static TabbedMenuScreen activeFrame;
   private static float layoutPad = 14.0F;
   private static float layoutGap = 10.0F;
   private static float layoutRow = 24.0F;
   private static float layoutHeaderTop = 12.0F;
   private static float layoutRailX = 14.0F;
   private static float layoutRailW = 158.0F;
   private static float layoutRailTop = 52.0F;
   private static float layoutContentX = 182.0F;
   private static float layoutContentW = 780.0F;
   private static float layoutHeaderY = 12.0F;
   private static float layoutBodyTop = 52.0F;
   private static float layoutBodyBottom = 520.0F;
   private static float layoutMouseX = -9999.0F;
   private static float layoutMouseY = -9999.0F;

   public static float pad() {
      return layoutPad;
   }

   public static float gap() {
      return layoutGap;
   }

   public static float rowH() {
      return layoutRow;
   }

   public static float railX() {
      return layoutRailX;
   }

   public static float railW() {
      return layoutRailW;
   }

   public static float railTop() {
      return layoutRailTop;
   }

   public static float contentX() {
      return layoutContentX;
   }

   public static float contentW() {
      return layoutContentW;
   }

   public static float headerY() {
      return layoutHeaderY;
   }

   public static float bodyTop() {
      return layoutBodyTop;
   }

   public static float bodyBottom() {
      return layoutBodyBottom;
   }

   public static float mouseX() {
      return layoutMouseX;
   }

   public static float mouseY() {
      return layoutMouseY;
   }

   public static MenuBackdrop backdrop() {
      return activeFrame == null ? null : activeFrame.backdrop;
   }

   public TabbedMenuScreen(List list, GuiScreen screen, int n) {
      super(screen);
      this.selectedTabIndex = n;
      this.setParentScreen(screen);
      this.tabs = list;

      for (ClientMenuTab clientMenuTab : (Iterable<ClientMenuTab>)(this.tabs)) {
         if (this.shouldHideTab(clientMenuTab)) {
            list.remove(clientMenuTab);
         }
      }

      this.disableDefaultBackground();
      activeFrame = this;
   }

   public boolean shouldHideTab(ClientMenuTab clientMenuTab) {
      return false;
   }

   private void onTabSelected(ClientMenuTab clientMenuTab, GuiButton button) {
      this.selectedTabIndex = this.getTabs().indexOf(clientMenuTab);
      ClientMenuTab selectedTab = this.getSelectedTab();
      selectedTab.onResize(this.mc, this.width, this.height);
   }

   public List getTabs() {
      return this.tabs;
   }

   public ClientMenuTab getSelectedTab() {
      return (ClientMenuTab)this.getTabs().get(this.selectedTabIndex);
   }

   @Override
   public void handleKeyboardInput() throws IOException {
      super.handleKeyboardInput();
      if (this.getSelectedTab() != null) {
         this.getSelectedTab().handleKeyboardInput();
      }
   }

   @Override
   public void updateScreen() {
      super.updateScreen();
      if (this.getSelectedTab() != null) {
         this.getSelectedTab().updateScreen();
      }
   }

   @Override
   public void onGuiClosed() {
      super.onGuiClosed();
      if (this.getSelectedTab() != null) {
         this.getSelectedTab().onGuiClosed();
      }
   }

   @Override
   public void handleMouseInput() throws IOException {
      super.handleMouseInput();
      if (this.getSelectedTab() != null) {
         this.getSelectedTab().handleMouseInput();
      }
   }

   private void updateLayout() {
      float w = this.width <= 0 ? 1366.0F : (float)this.width;
      float h = this.height <= 0 ? 768.0F : (float)this.height;

      float pad = clamp(Math.round(h * 0.028F), 10.0F, 18.0F);
      float gap = clamp(Math.round(h * 0.018F), 8.0F, 12.0F);
      float row = clamp(Math.round(h * 0.045F), 21.0F, 25.0F);
      float railW = clamp(Math.round(w * 0.175F), 144.0F, 174.0F);
      float railBudget = w * 0.28F;
      if (railW > railBudget) {
         railW = Math.max(114.0F, railBudget);
      }

      float available = w - pad * 2.0F;
      float composition = Math.min(available, railW + gap + MAX_PAGE_WIDTH);
      float originX = pad + (available - composition) * 0.5F;

      layoutPad = pad;
      layoutGap = gap;
      layoutRow = row;
      layoutHeaderTop = pad;
      layoutRailX = originX;
      layoutRailW = railW;
      layoutHeaderY = layoutHeaderTop;
      layoutBodyTop = layoutHeaderTop + TOP_BAR_H + gap * 0.75F;
      layoutRailTop = layoutBodyTop;
      layoutContentX = originX + railW + gap;
      layoutContentW = Math.max(200.0F, composition - railW - gap);
      layoutBodyBottom = h - pad;
   }

   @Override
   public void onResize(Minecraft mc2, int n, int n2) {
      super.onResize(mc2, n, n2);
      this.updateLayout();
      if (this.getSelectedTab() != null) {
         this.getSelectedTab().onResize(mc2, n, n2);
      }
   }

   @Override
   public void initContent() {
      super.initContent();
      NO_TOGGLE_SCREENS.add(this);
      this.updateLayout();

      // Replace the default "<-" text back button (id 0) with a sleek square icon glass button at bottom-left
      if (this.getParentScreen() != null) {
         for (int i = 0; i < this.buttonList.size(); i++) {
            GuiButton existing = (GuiButton)this.buttonList.get(i);
            if (existing.id == 0 || "<-".equals(existing.displayString)) {
               int backSize = 26;
               GlassMenuButton backBtn = new GlassMenuButton(
                  existing.id,
                  Math.round(layoutRailX),
                  Math.round(layoutBodyBottom - (float)backSize),
                  backSize,
                  backSize,
                  ""
               ).withIcon(LucideIcon.ARROW_LEFT).withIconOnly(true);
               this.buttonList.set(i, backBtn);
               break;
            }
         }
      }

      float cardPad = 7.0F;
      float btnStep = sidebarItemHeight() + sidebarItemGap();
      float btnX = layoutRailX + cardPad;
      float btnW = layoutRailW - cardPad * 2.0F;

      int n = 0;
      for (ClientMenuTab clientMenuTab : (Iterable<ClientMenuTab>)(this.getTabs())) {
         float btnY = layoutRailTop + cardPad + (float)n * btnStep;
         SidebarTabButton tabBtn = new SidebarTabButton(
            this.buttonList.size() + 10,
            Math.round(btnX),
            Math.round(btnY),
            Math.round(btnW),
            Math.round(sidebarItemHeight()),
            clientMenuTab,
            n
         );
         this.addCustomButton(tabBtn, button -> this.onTabSelected(clientMenuTab, button));
         n++;
      }

      this.getSelectedTab().setWorldAndResolution(Minecraft.getMinecraft(), this.width, this.height);
   }

   private void addCustomButton(GuiButton button, GuiButtonClickHandler handler) {
      button.id = this.buttonList.size() + 10;
      this.buttonList.add(button);
      this.registerButtonClickHandler(button.id, handler);
   }

   private void registerButtonClickHandler(int id, GuiButtonClickHandler handler) {
      try {
         java.lang.reflect.Field f = AugustusGuiScreen.class.getDeclaredField("buttonClickHandlers");
         f.setAccessible(true);
         java.util.Map map = (java.util.Map)f.get(this);
         map.put(Integer.valueOf(id), handler);
      } catch (Throwable ignored) {
      }
   }

   private static float sidebarItemHeight() {
      return Math.max(23.0F, Math.min(27.0F, layoutRow + 2.0F));
   }

   private static float sidebarItemGap() {
      return 3.5F;
   }

   private void drawMenuBackdrop() {
      float w = (float)this.width;
      float h = (float)this.height;
      net.augustus.utils.shader.BackgroundShaderManager bsm = Augustus.getInstance().getBackgroundShaderManager();
      if (bsm != null && !bsm.isCustomBackgroundEnabled()) {
         this.backdrop.invalidateDraw();
         this.drawGradientRect(0, 0, this.width, this.height, UiTheme.menuPillFillTopArgb(), 0xFF0A0D16);
      } else if (bsm != null && bsm.isShaderBackdropActive() && bsm.renderActiveShaderFullscreen()) {
         this.backdrop.invalidateDraw();
      } else if (this.backdrop.isAvailable()) {
         this.backdrop.draw(0.0F, 0.0F, w, h, 1.0F);
         this.backdrop.drawFrost(0.0F, 0.0F, w, h, 0.72F);
      } else {
         this.backdrop.invalidateDraw();
         RenderUtil.drawRect(0, 0, this.width, this.height, UiTheme.menuPillFillBottomArgb());
      }

      // Atmospheric themed glass scrim for crisp glass contrast
      this.drawGradientRect(0, 0, this.width, this.height, UiTheme.menuScrimTopArgb(), UiTheme.menuScrimBottomArgb());

      // Subtle brand ambient wash from top-left logo corner
      int accent = UiTheme.accentColor();
      GlassRenderer.drawRadialGlow(
         0.0F,
         0.0F,
         w,
         h,
         0.0F,
         layoutRailX + 18.0F,
         layoutHeaderTop + 18.0F,
         Math.max(220.0F, w * 0.28F),
         Math.max(150.0F, h * 0.26F),
         1.8F,
         1.8F,
         UiTheme.withAlpha(accent, 52)
      );
   }

   @Override
   public void drawScreen(int mouseX, int mouseY, float partialTicks) {
      this.updateLayout();
      layoutMouseX = (float)mouseX;
      layoutMouseY = (float)mouseY;

      this.drawMenuBackdrop();

      ClientMenuTab tab = this.getSelectedTab();
      tab.disableDefaultBackground();

      this.drawSidebarCard(tab);
      tab.drawChromeBehind();
      tab.drawScreen(mouseX, mouseY, partialTicks);

      this.drawActiveSidebarPlate();
      super.drawScreen(mouseX, mouseY, partialTicks);
      this.drawHeader(tab);
   }

   private void drawSidebarCard(ClientMenuTab tab) {
      int count = this.getTabs().size();
      float cardPad = 7.0F;
      float itemH = sidebarItemHeight();
      float cardH = cardPad * 2.0F + (float)count * itemH + (float)Math.max(0, count - 1) * sidebarItemGap();
      tab.menuGlassPanel(layoutRailX, layoutRailTop, layoutRailW, cardH, 0.0F, 1.0F);
   }

   private void drawActiveSidebarPlate() {
      float cardPad = 7.0F;
      float itemH = sidebarItemHeight();
      float step = itemH + sidebarItemGap();
      float targetY = layoutRailTop + cardPad + (float)this.selectedTabIndex * step;
      if (this.activePlateY < 0.0F) {
         this.activePlateY = targetY;
      } else {
         this.activePlateY = UiAnimation.smooth(this.activePlateY, targetY, 18.0F);
      }

      float px = layoutRailX + cardPad;
      float pw = layoutRailW - cardPad * 2.0F;
      float py = this.activePlateY;
      float radius = 6.0F;
      int accent = UiTheme.accentColor();

      // Soft outer violet bloom behind active plate
      GlassRenderer.drawSoftShadow(
         px, py, pw, itemH, radius, 8.0F, 1.0F,
         UiTheme.withAlpha(accent, 90)
      );

      GlassRenderer.drawRoundedGradientBoxHorizontal(
         px,
         py,
         pw,
         itemH,
         radius,
         UiTheme.withAlpha(railPlateLeadingRgb(), 220),
         UiTheme.withAlpha(railPlateTrailingRgb(), 190),
         1.0F
      );
      GlassRenderer.drawRoundedOutline(
         px,
         py,
         pw,
         itemH,
         radius,
         UiTheme.BORDER_THICKNESS,
         UiTheme.withAlpha(UiTheme.lerpArgb(accent, 0xFFFFFFFF, 0.35F), 155)
      );
   }

   private void drawHeader(ClientMenuTab tab) {
      if (tab == null) {
         return;
      }
      int accent = UiTheme.accentColor();

      // 1) Top-left Augustus 'A' mark inside rounded glass badge
      float badgeSize = 28.0F;
      float bx = layoutRailX;
      float by = layoutHeaderTop + (TOP_BAR_H - badgeSize) * 0.5F;
      GlassRenderer.drawSoftShadow(bx, by, badgeSize, badgeSize, 7.0F, 8.0F, 1.0F, UiTheme.withAlpha(accent, 75));
      GlassRenderer.drawMenuGlassBox(
         bx,
         by,
         badgeSize,
         badgeSize,
         7.0F,
         UiTheme.withAlpha(UiTheme.lerpArgb(UiTheme.menuPillFillTopArgb(), accent, 0.20F), 220),
         UiTheme.withAlpha(UiTheme.menuPillFillBottomArgb(), 232),
         UiTheme.withAlpha(UiTheme.lerpArgb(accent, 0xFFFFFFFF, 0.25F), 140),
         UiTheme.BORDER_THICKNESS,
         bx + badgeSize * 0.5F,
         by + badgeSize * 0.5F,
         0.0F,
         12.0F,
         0
      );
      if (BRAND_MARK.isReady()) {
         float markH = 16.5F;
         float markW = markH * BRAND_MARK.getAspect();
         BRAND_MARK.draw(
            bx + (badgeSize - markW) * 0.5F,
            by + (badgeSize - markH) * 0.5F,
            markW,
            markH,
            accent,
            0xFFFFFFFF,
            0xFFFFFFFF
         );
      }

      // 2) Active tab icon + title
      InterFontUtil titleFace = ClientMenuTab.menuTitleFont();
      float x = layoutContentX + 2.0F;
      float centerY = layoutHeaderTop + TOP_BAR_H * 0.5F;

      if (tab.getTabIcon() != null && tab.getTabIcon().isAvailable()) {
         tab.getTabIcon().drawCentered(x + TITLE_ICON * 0.5F, centerY, TITLE_ICON, 0xFFEAE6F5, false);
         x += TITLE_ICON + 8.0F;
      }

      float titleRight = x;
      if (titleFace != null) {
         float ty = centerY - titleFace.getFontHeight() * 0.5F;
         titleFace.drawStringWithShadow(tab.getTabTitle(), (double)x, (double)ty, 0xFFF5F3FA);
         titleRight = x + titleFace.getStringWidth(tab.getTabTitle());
      }

      // 3) Right side: Status text + compact active session/proxy pill (without overlapping logo)
      float rightX = layoutContentX + layoutContentW;
      float statusWidth = this.drawStatusLine(rightX, titleRight + 14.0F, centerY);
      this.drawSessionPill(rightX - statusWidth - (statusWidth > 0.0F ? 10.0F : 0.0F), titleRight + 14.0F, centerY);
   }

   private float drawStatusLine(float rightX, float minLeftX, float centerY) {
      ClientMenuTab tab = this.getSelectedTab();
      if (tab == null) {
         return 0.0F;
      }
      String status = ClientMenuTab.getStatusText(tab);
      InterFontUtil face = ClientMenuTab.menuBodyFont();
      if (status == null || status.isEmpty() || face == null) {
         return 0.0F;
      }
      int argb;
      if ("Waiting...".equals(status)) {
         argb = UiTheme.lerpArgb(UiTheme.accentColor(), 0xFFF1F5F9, 0.45F);
      } else {
         Color color = ClientMenuTab.getStatusColor(tab);
         argb = color == null ? 0xFFF2F0FA : UiTheme.lerpArgb(color.getRGB(), 0xFFFFFFFF, 0.15F);
      }
      float available = rightX - minLeftX;
      if (available < 40.0F) {
         return 0.0F;
      }
      String fitted = ClientMenuTab.menuFit(face, status, available);
      float w = face.getStringWidth(fitted);
      float y = centerY - face.getFontHeight() * 0.5F;
      face.drawStringWithShadow(fitted, (double)(rightX - w), (double)y, argb);
      return w;
   }

   private void drawSessionPill(float rightX, float minLeftX, float centerY) {
      if (ClientManagers.moduleManager == null || ClientManagers.moduleManager.clientSettings == null
            || ClientManagers.moduleManager.clientSettings.hideMainMenuAccountInfos.getValue()) {
         return;
      }
      InterFontUtil face = ClientMenuTab.menuSmallFont();
      if (face == null || rightX - minLeftX < 130.0F) {
         return;
      }
      String username = this.mc != null && this.mc.getSession() != null ? this.mc.getSession().getUsername() : "Player";
      boolean validSession = this.mc != null && this.mc.getSession() != null && this.mc.getSession().getToken().length() >= 5;
      ProxyEntry proxy = Augustus.getInstance() != null ? Augustus.getInstance().getProxy() : null;
      String proxyText = proxy == null ? "Direct" : proxy.getHost() + ":" + proxy.getPort();
      String label = username + "  •  " + proxyText;

      float maxW = Math.min(230.0F, rightX - minLeftX);
      String fitted = ClientMenuTab.menuFit(face, label, maxW - 22.0F);
      float textW = face.getStringWidth(fitted);
      float pillW = textW + 22.0F;
      float pillH = 18.0F;
      float px = rightX - pillW;
      float py = centerY - pillH * 0.5F;

      GlassRenderer.drawRoundedBox(
         px, py, pillW, pillH, pillH * 0.5F,
         UiTheme.withAlpha(UiTheme.menuPillFillBottomArgb(), 0x66),
         0x1CFFFFFF,
         0.75F
      );
      GlassRenderer.drawCircle(px + 8.5F, centerY, 2.3F, validSession ? 0xFF4ADE80 : 0xFFF87171);
      face.drawStringWithShadow(fitted, (double)(px + 14.5F), (double)(centerY - face.getFontHeight() * 0.5F), 0xCCB8C2D4);
   }

   private static int railPlateLeadingRgb() {
      int accent = UiTheme.accentColor();
      int r = Math.min(255, (accent >> 16 & 255) * RAIL_PLATE_LEADING_R_PERCENT / 100 + 20);
      int g = Math.min(255, (accent >> 8 & 255) * RAIL_PLATE_LEADING_G_PERCENT / 100 + 12);
      int b = Math.min(255, (accent & 255) * RAIL_PLATE_LEADING_B_PERCENT / 100 + 28);
      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   private static int railPlateTrailingRgb() {
      int accent = UiTheme.accentColor();
      int r = (accent >> 16 & 255) * RAIL_PLATE_TRAILING_R_PERCENT / 100;
      int g = (accent >> 8 & 255) * RAIL_PLATE_TRAILING_G_PERCENT / 100;
      int b = (accent & 255) * RAIL_PLATE_TRAILING_B_PERCENT / 100;
      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   private static float clamp(float value, float min, float max) {
      return value < min ? min : (value > max ? max : value);
   }

   /**
    * Sidebar navigation button rendered cleanly over the sliding gradient active pill.
    */
   private final class SidebarTabButton extends GuiButton {
      private final ClientMenuTab tab;
      private final int tabIndex;
      private float hoverAnim;
      private float activeAnim;

      private SidebarTabButton(int id, int x, int y, int width, int height, ClientMenuTab tab, int tabIndex) {
         super(id, x, y, width, height, tab.getTabTitle());
         this.tab = tab;
         this.tabIndex = tabIndex;
      }

      @Override
      public void drawButton(Minecraft mc, int mouseX, int mouseY) {
         if (!this.visible) {
            return;
         }
         boolean isActive = this.tabIndex == TabbedMenuScreen.this.selectedTabIndex;
         this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition
            && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;
         this.hoverAnim = UiTheme.smoothState(this.hoverAnim, this.hovered ? 1.0F : 0.0F);
         this.activeAnim = UiTheme.smoothState(this.activeAnim, isActive ? 1.0F : 0.0F);

         float x = (float)this.xPosition;
         float y = (float)this.yPosition;
         float w = (float)this.width;
         float h = (float)this.height;
         float radius = 6.0F;
         int accent = UiTheme.accentColor();

         float hovOnly = this.hoverAnim * (1.0F - this.activeAnim);
         if (hovOnly > 0.02F) {
            float lightX = Math.max(x + 8.0F, Math.min(x + w - 8.0F, (float)mouseX));
            float lightY = Math.max(y + 4.0F, Math.min(y + h - 4.0F, (float)mouseY));
            GlassRenderer.drawMenuGlassBox(
               x,
               y,
               w,
               h,
               radius,
               UiTheme.withAlpha(railPlateLeadingRgb(), Math.round(92.0F * hovOnly)),
               UiTheme.withAlpha(railPlateTrailingRgb(), Math.round(76.0F * hovOnly)),
               UiTheme.withAlpha(UiTheme.lerpArgb(accent, 0xFFF8FAFC, 0.25F), Math.round(130.0F * hovOnly)),
               UiTheme.BORDER_THICKNESS,
               lightX,
               lightY,
               0.24F * hovOnly,
               Math.max(w, h) * 0.45F,
               UiTheme.lerpArgb(accent, 0xFFFFFFFF, 0.50F)
            );
         }

         float slide = !isActive ? UiAnimation.easeOutCubic(this.hoverAnim) * 2.0F : 0.0F;
         float emphasis = Math.max(this.activeAnim, this.hoverAnim * 0.78F);
         int iconColor = UiTheme.lerpArgb(0xFFB4ABC8, 0xFFFFFFFF, emphasis);
         int textColor = UiTheme.lerpArgb(0xFFD5CEE4, 0xFFFFFFFF, emphasis);

         float iconX = x + 10.0F + slide;
         float iconSize = 11.5F;
         LucideIcon icon = this.tab.getTabIcon();
         if (icon != null && icon.isAvailable()) {
            icon.drawCentered(iconX + iconSize * 0.5F, y + h * 0.5F, iconSize, iconColor, false);
         }

         InterFontUtil face = ClientMenuTab.menuBodyFont();
         float textX = iconX + iconSize + 8.0F;
         if (face != null) {
            String fitted = ClientMenuTab.menuFit(face, this.tab.getTabTitle(), x + w - 8.0F - textX);
            float ty = y + (h - face.getFontHeight()) * 0.5F;
            face.drawStringWithShadow(fitted, (double)textX, (double)ty, textColor);
         }
      }
   }
}
