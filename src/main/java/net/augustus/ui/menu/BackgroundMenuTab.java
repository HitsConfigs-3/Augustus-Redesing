package net.augustus.ui.menu;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.augustus.Augustus;
import net.augustus.ClientManagers;
import net.augustus.modules.misc.ClientSettings;
import net.augustus.ui.TabbedMenuScreen;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.ui.widgets.ScrollBar;
import net.augustus.utils.shader.BackgroundShader;
import net.augustus.utils.shader.BackgroundShaderManager;
import net.minecraft.client.gui.GuiTextField;

/**
 * Unified Backdrop & Shader gallery featuring live animated preview (for both Minecraft
 * animated shader clips and built-in Augustus GLSL shaders), category filtering, search,
 * and quick toggles for Custom BG and Mouse Input.
 */
public class BackgroundMenuTab extends ClientMenuTab {
   private static final float HERO_H = 88.0F;
   private static final float CARD_H = 44.0F;
   private static final float ROW_GAP = 8.0F;
   private static final float ROW_PITCH = CARD_H + ROW_GAP;
   private static final float COL_GAP = 8.0F;

   private static final int FILTER_ALL = 0;
   private static final int FILTER_ANIMATED = 1;
   private static final int FILTER_SHADERS = 2;

   private static final class GalleryItem {
      final MenuBackdrop.BackdropPreset scene;
      final BackgroundShader shader;

      GalleryItem(MenuBackdrop.BackdropPreset scene) {
         this.scene = scene;
         this.shader = null;
      }

      GalleryItem(BackgroundShader shader) {
         this.scene = null;
         this.shader = shader;
      }

      boolean isScene() {
         return this.scene != null;
      }

      String getName() {
         return this.scene != null ? this.scene.getDisplayName() : (this.shader != null ? this.shader.getName() : "");
      }
   }

   private final ScrollBar scrollBar;
   private GuiTextField searchField;

   private int filterMode = FILTER_ALL;

   private float areaX;
   private float areaTop;
   private float areaW;
   private float areaBottom;

   private float heroX;
   private float heroY;
   private float heroW;

   private float gridPanelX;
   private float gridPanelY;
   private float gridPanelW;
   private float gridPanelH;

   private float searchX;
   private float searchY;
   private float searchW;
   private float searchH;

   private float customToggleX;
   private float customToggleY;
   private float toggleW;
   private float toggleH;
   private float mouseToggleX;
   private float mouseToggleY;

   private float clearBtnX;
   private float clearBtnY;
   private float clearBtnSize;

   private final float[] chipX = new float[3];
   private final float[] chipW = new float[3];
   private float chipY;
   private float chipH;

   private int lastRowCount = -1;
   private String lastQuery = "";

   public BackgroundMenuTab() {
      super("Background");
      this.scrollBar = new ScrollBar(Math.round(ROW_PITCH));
   }

   @Override
   public LucideIcon getTabIcon() {
      return LucideIcon.PHOTO;
   }

   private List getAllShaders() {
      return Augustus.getInstance().getBackgroundShaderManager().getSortedShaders();
   }

   private List<GalleryItem> getFilteredItems() {
      String query = this.searchField != null ? this.searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
      List<GalleryItem> result = new ArrayList<>();

      if (this.filterMode == FILTER_ALL || this.filterMode == FILTER_ANIMATED) {
         List<MenuBackdrop.BackdropPreset> scenes = MenuBackdrop.getPresets();
         for (int i = 0; i < scenes.size(); i++) {
            MenuBackdrop.BackdropPreset p = scenes.get(i);
            if (query.isEmpty()
               || p.getDisplayName().toLowerCase(Locale.ROOT).contains(query)
               || p.getSubtitle().toLowerCase(Locale.ROOT).contains(query)
               || "minecraft".contains(query) && p.getId().startsWith("mc_")) {
               result.add(new GalleryItem(p));
            }
         }
      }

      if (this.filterMode == FILTER_ALL || this.filterMode == FILTER_SHADERS) {
         List allShaders = this.getAllShaders();
         for (int i = 0; i < allShaders.size(); i++) {
            BackgroundShader shader = (BackgroundShader)allShaders.get(i);
            if (query.isEmpty() || (shader.getName() != null && shader.getName().toLowerCase(Locale.ROOT).contains(query))) {
               result.add(new GalleryItem(shader));
            }
         }
      }

      return result;
   }

   private int columnsForWidth() {
      if (this.areaW >= 610.0F) {
         return 3;
      }
      return this.areaW >= 360.0F ? 2 : 1;
   }

   private void layoutGeometry() {
      this.areaX = TabbedMenuScreen.contentX();
      this.areaTop = TabbedMenuScreen.bodyTop();
      this.areaW = TabbedMenuScreen.contentW();
      this.areaBottom = TabbedMenuScreen.bodyBottom();

      this.heroX = this.areaX;
      this.heroY = this.areaTop;
      this.heroW = this.areaW;

      this.gridPanelX = this.areaX;
      this.gridPanelY = this.heroY + HERO_H + 10.0F;
      this.gridPanelW = this.areaW;
      this.gridPanelH = Math.max(96.0F, this.areaBottom - this.gridPanelY);

      this.searchW = Math.min(228.0F, Math.max(172.0F, this.heroW * 0.36F));
      this.searchX = this.heroX + this.heroW - 12.0F - this.searchW;
      this.searchY = this.heroY + 14.0F;
      this.searchH = 26.0F;

      if (this.searchField != null) {
         this.searchField.xPosition = Math.round(this.searchX);
         this.searchField.yPosition = Math.round(this.searchY);
      }

      this.clearBtnSize = 16.0F;
      this.clearBtnX = this.searchX + this.searchW - this.clearBtnSize - 5.0F;
      this.clearBtnY = this.searchY + (this.searchH - this.clearBtnSize) * 0.5F;

      this.toggleH = 24.0F;
      this.toggleW = (this.searchW - 6.0F) * 0.5F;
      this.customToggleX = this.searchX;
      this.customToggleY = this.searchY + this.searchH + 8.0F;
      this.mouseToggleX = this.searchX + this.toggleW + 6.0F;
      this.mouseToggleY = this.customToggleY;

      this.chipH = 16.0F;
      this.chipY = this.gridPanelY + 6.5F;
      String[] labels = new String[]{"All", "Animated", "GLSL Shaders"};
      float cursorX = this.gridPanelX + 128.0F;
      for (int i = 0; i < labels.length; i++) {
         float w = menuCaptionFont() != null ? menuCaptionFont().getStringWidth(labels[i]) + 14.0F : 46.0F;
         this.chipX[i] = cursorX;
         this.chipW[i] = w;
         cursorX += w + 5.0F;
      }
   }

   private void syncScrollBounds(int filteredCount) {
      int cols = this.columnsForWidth();
      int rows = Math.max(1, (filteredCount + cols - 1) / cols);
      if (rows != this.lastRowCount) {
         this.scrollBar.setItemCount(rows);
         this.lastRowCount = rows;
      }
      float viewTop = this.gridPanelY + 32.0F;
      float viewBottom = this.gridPanelY + this.gridPanelH - 10.0F;
      this.scrollBar.setBounds(
         Math.round(this.gridPanelX + this.gridPanelW - 10.0F),
         Math.round(viewTop),
         Math.round(this.gridPanelX + this.gridPanelW - 5.0F),
         Math.round(viewBottom)
      );
   }

   @Override
   public void initContent() {
      String previousText = this.searchField != null ? this.searchField.getText() : "";
      this.layoutGeometry();

      this.searchField = this.addTextField(Math.round(this.searchX), Math.round(this.searchY), Math.round(this.searchW), Math.round(this.searchH));
      this.searchField.setCustomFont(menuBodyFont());
      this.searchField.setMaxStringLength(48);
      this.searchField.setText(previousText);

      this.lastRowCount = -1;
      this.syncScrollBounds(this.getFilteredItems().size());
   }

   @Override
   public void drawContent(int mouseX, int mouseY, float partialTicks) {
      this.layoutGeometry();
      BackgroundShaderManager bsm = Augustus.getInstance().getBackgroundShaderManager();
      List allShaders = this.getAllShaders();
      List<GalleryItem> filteredItems = this.getFilteredItems();
      this.syncScrollBounds(filteredItems.size());

      BackgroundShader currentShader = bsm.getCurrentShader();
      MenuBackdrop.BackdropPreset activeScene = MenuBackdrop.getShared().getActivePreset();
      boolean shaderMode = bsm.isShaderBackdropActive();
      boolean customBgEnabled = ClientManagers.moduleManager.clientSettings.customBackground.getValue();
      boolean mouseInputEnabled = ClientManagers.moduleManager.clientSettings.mouseInputsOnBackgrounds.getValue();

      // 1. Top Hero Preview & Controls Card
      this.menuGlassPanel(this.heroX, this.heroY, this.heroW, HERO_H, 0.0F, 1.0F);

      float previewW = Math.min(148.0F, Math.max(106.0F, this.heroW * 0.25F));
      float previewH = HERO_H - 20.0F;
      float previewX = this.heroX + 12.0F;
      float previewY = this.heroY + 10.0F;

      GlassRenderer.drawRoundedGradientBox(previewX, previewY, previewW, previewH, 6.0F,
         UiTheme.menuPillFillTopArgb(), UiTheme.menuPillFillBottomArgb(), 0, 0.0F, 0.0F);
      boolean liveRendered = false;
      if (customBgEnabled) {
         if (shaderMode && currentShader != null) {
            liveRendered = currentShader.renderInBox(previewX + 2.0F, previewY + 2.0F, previewW - 4.0F, previewH - 4.0F, 6000.0F);
         } else {
            liveRendered = MenuBackdrop.getShared().drawPreview(previewX + 2.0F, previewY + 2.0F, previewW - 4.0F, previewH - 4.0F);
         }
      }
      if (!liveRendered) {
         int swatchTop = shaderMode && currentShader != null
            ? swatchTopForName(currentShader.getName())
            : activeScene.getSwatchTop();
         int swatchBot = shaderMode && currentShader != null
            ? swatchBottomForName(currentShader.getName())
            : activeScene.getSwatchBot();
         GlassRenderer.drawRoundedGradientBox(previewX + 2.0F, previewY + 2.0F, previewW - 4.0F, previewH - 4.0F, 5.0F,
            UiTheme.withAlpha(swatchTop, 160), UiTheme.withAlpha(swatchBot, 190), 0, 0.0F, 0.0F);
         LucideIcon.PHOTO.drawCentered(previewX + previewW * 0.5F, previewY + previewH * 0.5F - 3.0F, 18.0F, 0xB8E2E8F0);
      }

      // Vignette + frame outline around live preview
      GlassRenderer.drawRoundedGradientBox(previewX, previewY + previewH * 0.48F, previewW, previewH * 0.52F, 6.0F,
         0x00090612, 0xB8090612, 0, 0.0F, 0.0F);
      GlassRenderer.drawRoundedOutline(previewX, previewY, previewW, previewH, 6.0F, 1.0F,
         UiTheme.withAlpha(UiTheme.accentColor(), 135));
      menuBadge(liveRendered ? (shaderMode ? "SHADER" : "SCENE") : (customBgEnabled ? "FALLBACK" : "OFF"),
         previewX + 6.0F, previewY + previewH - (menuCaptionHeight() + 8.0F),
         liveRendered ? UiTheme.withAlpha(UiTheme.accentColor(), 0xCC) : 0x99334155, 0xFFFFFFFF);

      // Middle metadata
      float infoX = previewX + previewW + 14.0F;
      float rightPanelLeft = this.customToggleX - 12.0F;
      float infoW = Math.max(80.0F, rightPanelLeft - infoX);

      String caption = !customBgEnabled
         ? "VANILLA BACKGROUND"
         : (shaderMode ? "ACTIVE GLSL SHADER" : "ACTIVE ANIMATED SCENE");
      menuCaptionFont().drawString(caption, infoX, this.heroY + 15.0F, UiTheme.withAlpha(UiTheme.accentSoftGlow(), 0xCC));
      String activeName = !customBgEnabled
         ? "Off (Vanilla Dirt)"
         : (shaderMode ? (currentShader != null ? currentShader.getName() : "None") : activeScene.getDisplayName());
      menuTitleFont().drawString(menuFit(menuTitleFont(), activeName, infoW), infoX, this.heroY + 28.0F, 0xFFF8FAFC);

      int totalCount = MenuBackdrop.getPresets().size() + allShaders.size();
      String subInfo = filteredItems.size() == totalCount
         ? MenuBackdrop.getPresets().size() + " animated clips + " + allShaders.size() + " GLSL shaders"
         : filteredItems.size() + " of " + totalCount + " backdrops shown";
      menuSmallText(menuFit(menuSmallFont(), subInfo, infoW), infoX, this.heroY + 50.0F, TEXT_SUB);

      String modeHint = shaderMode
         ? (mouseInputEnabled ? "Interactive shader parallax ON" : "Augustus default GLSL shader active")
         : activeScene.getSubtitle() + " (Synchronized menu frost)";
      menuCaptionFont().drawString(menuFit(menuCaptionFont(), modeHint, infoW), infoX, this.heroY + 65.0F, 0x99CBD5E1);

      if (this.searchField != null) {
         if (this.searchField.getText().isEmpty() && !this.searchField.isFocused()) {
            this.drawFieldPlaceholder("Search backdrops...", Math.round(this.searchX) + 14, Math.round(this.searchY),
               Math.round(this.searchH), 0x99A1A1AA, menuBodyFont(), 8.0F);
            LucideIcon.SEARCH.draw(this.searchX + 7.0F, this.searchY + (this.searchH - 11.0F) * 0.5F,
               11.0F, 0x99A1A1AA, false);
         } else {
            LucideIcon.SEARCH.draw(this.searchX + this.searchW - (this.searchField.getText().isEmpty() ? 18.0F : 34.0F),
               this.searchY + (this.searchH - 11.0F) * 0.5F, 11.0F, UiTheme.withAlpha(UiTheme.accentSoftGlow(), 0x80), false);
            if (!this.searchField.getText().isEmpty()) {
               boolean clearHover = menuHovered(this.clearBtnX, this.clearBtnY, this.clearBtnSize, this.clearBtnSize, mouseX, mouseY);
               LucideIcon.CLOSE.drawCentered(this.clearBtnX + this.clearBtnSize * 0.5F, this.clearBtnY + this.clearBtnSize * 0.5F,
                  10.0F, clearHover ? 0xFFFFFFFF : 0xCCB8C2D4);
            }
         }
      }

      this.drawTogglePill("bg.toggle.custom", "Custom BG", customBgEnabled, this.customToggleX, this.customToggleY, this.toggleW, this.toggleH, mouseX, mouseY);
      this.drawTogglePill("bg.toggle.mouse", "Mouse Input", mouseInputEnabled, this.mouseToggleX, this.mouseToggleY, this.toggleW, this.toggleH, mouseX, mouseY);

      // 2. Bottom Backdrop & Shaders Gallery Panel
      this.menuGlassPanel(this.gridPanelX, this.gridPanelY, this.gridPanelW, this.gridPanelH, 0.0F, 1.0F);

      float hdrY = this.gridPanelY + 10.0F;
      LucideIcon.BOXES.draw(this.gridPanelX + 12.0F, hdrY - 1.0F, 11.5F, UiTheme.withAlpha(UiTheme.accentSoftGlow(), 0xCC), false);
      menuSectionLabel("BACKDROPS & SHADERS", this.gridPanelX + 27.0F, hdrY, 1.0F);

      String[] chipLabels = new String[]{"All", "Animated", "GLSL Shaders"};
      for (int c = 0; c < chipLabels.length; c++) {
         boolean selected = this.filterMode == c;
         boolean hovered = menuHovered(this.chipX[c], this.chipY, this.chipW[c], this.chipH, mouseX, mouseY);
         float chov = this.menuHover("bg.chip." + c, hovered);
         this.menuSurface(this.chipX[c], this.chipY, this.chipW[c], this.chipH, 5.0F, chov, selected ? 0.5F : 0.0F, 1.0F);
         if (selected) {
            GlassRenderer.drawRoundedOutline(this.chipX[c], this.chipY, this.chipW[c], this.chipH, 5.0F, 1.0F,
               UiTheme.withAlpha(UiTheme.accentColor(), 200));
         }
         menuCaptionFont().drawCenteredStringInBox(chipLabels[c], (double)this.chipX[c], (double)this.chipY,
            (double)this.chipW[c], (double)this.chipH, selected ? 0xFFFFFFFF : 0xFFCBD5E1);
      }

      menuTextRight(filteredItems.size() + " / " + totalCount, this.gridPanelX + this.gridPanelW - 14.0F, hdrY, TEXT_SUB);
      GlassRenderer.drawSeparator(this.gridPanelX + 12.0F, this.gridPanelY + 28.0F,
         this.gridPanelX + this.gridPanelW - 12.0F, this.gridPanelY + 28.0F, 1.0F, UiTheme.GLASS_DIVIDER);

      float viewTop = this.gridPanelY + 33.0F;
      float viewBottom = this.gridPanelY + this.gridPanelH - 10.0F;
      float viewLeft = this.gridPanelX + 12.0F;
      float viewW = Math.max(120.0F, this.gridPanelW - 28.0F);

      if (filteredItems.isEmpty()) {
         this.menuEmptyState(viewLeft, viewTop, viewW, viewBottom - viewTop,
            LucideIcon.SEARCH, "No matching backdrops", "Clear the search filter to browse all scenes and shaders.", false);
         return;
      }

      int cols = this.columnsForWidth();
      float cardW = (viewW - COL_GAP * (cols - 1)) / (float)cols;
      float scrollOffset = (float)((int)this.scrollBar.getClampedScrollOffset());

      this.enableScissor(
         Math.round(this.gridPanelX + 2.0F),
         Math.round(viewTop),
         Math.round(this.gridPanelX + this.gridPanelW - 12.0F),
         Math.round(viewBottom)
      );

      for (int i = 0; i < filteredItems.size(); i++) {
         GalleryItem item = filteredItems.get(i);
         int row = i / cols;
         int col = i % cols;

         float cardX = viewLeft + col * (cardW + COL_GAP);
         float cardY = viewTop + scrollOffset + row * ROW_PITCH;
         if (cardY + CARD_H < viewTop - 10.0F || cardY > viewBottom + 10.0F) {
            continue;
         }

         boolean active = customBgEnabled && (item.isScene()
            ? (!shaderMode && item.scene.getId().equalsIgnoreCase(activeScene.getId()))
            : (shaderMode && item.shader == currentShader));
         boolean overCard = mouseX >= viewLeft && mouseX <= viewLeft + viewW && mouseY >= viewTop && mouseY <= viewBottom
            && menuHovered(cardX, cardY, cardW, CARD_H, mouseX, mouseY);
         float hover = this.menuHover((item.isScene() ? "scene." : "shader.") + item.getName(), overCard);

         this.menuSurface(cardX, cardY, cardW, CARD_H, 7.0F, hover, active ? 0.55F : 0.0F, 1.0F);

         if (active) {
            int accentWashLeft = UiTheme.withAlpha(UiTheme.accentColor(), 68);
            int accentWashRight = UiTheme.withAlpha(UiTheme.accentColor(), 14);
            GlassRenderer.drawRoundedGradientBoxHorizontal(cardX + 1.0F, cardY + 1.0F, cardW - 2.0F, CARD_H - 2.0F, 6.0F,
               accentWashLeft, accentWashRight, 1.0F);
            GlassRenderer.drawRoundedOutline(cardX, cardY, cardW, CARD_H, 7.0F, 1.25F,
               UiTheme.withAlpha(UiTheme.accentColor(), 200));
            menuAccentRail(cardX + 2.0F, cardY + 8.0F, CARD_H - 16.0F, 1.0F);
         }

         float swatchSize = 26.0F;
         float swatchX = cardX + 9.0F;
         float swatchY = cardY + (CARD_H - swatchSize) * 0.5F;
         int swTop = item.isScene() ? item.scene.getSwatchTop() : swatchTopForName(item.getName());
         int swBot = item.isScene() ? item.scene.getSwatchBot() : swatchBottomForName(item.getName());
         GlassRenderer.drawRoundedGradientBox(swatchX, swatchY, swatchSize, swatchSize, 6.0F, swTop, swBot, 0, 0.0F, 0.0F);
         GlassRenderer.drawRoundedOutline(swatchX, swatchY, swatchSize, swatchSize, 6.0F, 1.0F,
            active ? UiTheme.withAlpha(0xFFFFFFFF, 140) : UiTheme.withAlpha(0xFFFFFFFF, 45));
         LucideIcon cardIcon = item.isScene() ? LucideIcon.PLAYER_PLAY : iconForName(item.getName());
         cardIcon.drawCentered(swatchX + swatchSize * 0.5F, swatchY + swatchSize * 0.5F, 12.5F, 0xFFF8FAFC);

         float textX = swatchX + swatchSize + 9.0F;
         float maxTextW = Math.max(40.0F, cardW - (textX - cardX) - (active ? 26.0F : 10.0F));
         int titleColor = active ? UiTheme.lerpArgb(UiTheme.accentColor(), 0xFFFFFFFF, 0.72F) : UiTheme.lerpArgb(0xFFE2E8F0, 0xFFFFFFFF, hover);
         menuText(menuFit(menuBodyFont(), item.getName(), maxTextW), textX, cardY + 9.0F, titleColor);

         String subLabel = active
            ? "Active backdrop"
            : (item.isScene() ? item.scene.getSubtitle() : (item.shader.isCompiled() ? "Augustus GLSL" : "Shader preset"));
         menuCaptionFont().drawString(menuFit(menuCaptionFont(), subLabel, maxTextW), textX, cardY + 24.0F,
            active ? UiTheme.accentSoftGlow() : 0x9994A3B8);

         if (active) {
            LucideIcon.CIRCLE_CHECK.drawCentered(cardX + cardW - 14.0F, cardY + CARD_H * 0.5F, 13.0F, UiTheme.accentColor());
         }
      }

      this.disableScissor();
      this.scrollBar.updateAndDraw();
   }

   private void drawTogglePill(String id, String label, boolean enabled, float x, float y, float w, float h, int mouseX, int mouseY) {
      boolean hovered = menuHovered(x, y, w, h, mouseX, mouseY);
      float hover = this.menuHover(id, hovered);
      this.menuSurface(x, y, w, h, 6.0F, hover, enabled ? 0.35F : 0.0F, 1.0F);
      if (enabled) {
         GlassRenderer.drawRoundedGradientBoxHorizontal(x + 1.0F, y + 1.0F, w - 2.0F, h - 2.0F, 5.0F,
            UiTheme.withAlpha(UiTheme.accentColor(), 48), UiTheme.withAlpha(UiTheme.accentColor(), 18), 1.0F);
         GlassRenderer.drawRoundedOutline(x, y, w, h, 6.0F, 1.0F, UiTheme.withAlpha(UiTheme.accentColor(), 150));
      }

      float dotX = x + 11.0F;
      float dotY = y + h * 0.5F;
      int dotColor = enabled ? UiTheme.accentColor() : 0x9964748B;
      if (enabled) {
         GlassRenderer.drawCircle(dotX, dotY, 4.2F, UiTheme.withAlpha(dotColor, 75));
      }
      GlassRenderer.drawCircle(dotX, dotY, 2.3F, dotColor);

      menuSmallText(menuFit(menuSmallFont(), label, w - 22.0F), x + 19.0F, y + (h - menuSmallHeight()) * 0.5F,
         enabled ? 0xFFF8FAFC : 0xFFCBD5E1);
   }

   private static LucideIcon iconForName(String name) {
      if (name == null) {
         return LucideIcon.PHOTO;
      }
      String lower = name.toLowerCase(Locale.ROOT);
      if (lower.contains("galaxy") || lower.contains("space") || lower.contains("star") || lower.contains("blackhole") || lower.contains("polar")) {
         return LucideIcon.SPARKLES;
      }
      if (lower.contains("globe") || lower.contains("world") || lower.contains("water") || lower.contains("cloud")) {
         return LucideIcon.GLOBE;
      }
      if (lower.contains("cube") || lower.contains("grid") || lower.contains("matrix") || lower.contains("tri")) {
         return LucideIcon.BOXES;
      }
      return LucideIcon.PHOTO;
   }

   private static int swatchTopForName(String name) {
      if (name == null) {
         return UiTheme.accentColor();
      }
      String lower = name.toLowerCase(Locale.ROOT);
      if (lower.contains("red") || lower.contains("fire") || lower.contains("sun") || lower.contains("lava")) {
         return 0xFFE11D48;
      }
      if (lower.contains("green") || lower.contains("matrix") || lower.contains("emerald")) {
         return 0xFF059669;
      }
      if (lower.contains("blue") || lower.contains("water") || lower.contains("ice") || lower.contains("sky") || lower.contains("polar")) {
         return 0xFF0284C7;
      }
      if (lower.contains("gold") || lower.contains("amber") || lower.contains("yellow")) {
         return 0xFFD97706;
      }
      return UiTheme.accentColor();
   }

   private static int swatchBottomForName(String name) {
      if (name == null) {
         return UiTheme.accentDeep();
      }
      String lower = name.toLowerCase(Locale.ROOT);
      if (lower.contains("red") || lower.contains("fire") || lower.contains("sun") || lower.contains("lava")) {
         return 0xFF4C0519;
      }
      if (lower.contains("green") || lower.contains("matrix") || lower.contains("emerald")) {
         return 0xFF064E3B;
      }
      if (lower.contains("blue") || lower.contains("water") || lower.contains("ice") || lower.contains("sky") || lower.contains("polar")) {
         return 0xFF0F172A;
      }
      if (lower.contains("gold") || lower.contains("amber") || lower.contains("yellow")) {
         return 0xFF451A03;
      }
      return UiTheme.accentDeep();
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      if (mouseButton == 0 && this.searchField != null && !this.searchField.getText().isEmpty()
         && menuHovered(this.clearBtnX - 2.0F, this.clearBtnY - 2.0F, this.clearBtnSize + 4.0F, this.clearBtnSize + 4.0F, mouseX, mouseY)) {
         this.searchField.setText("");
         this.lastRowCount = -1;
         this.scrollBar.scrollOffset = 0.0D;
         ClientSettings.playButtonClickSound();
         return;
      }

      super.mouseClicked(mouseX, mouseY, mouseButton);

      if (mouseButton == 0) {
         if (menuHovered(this.customToggleX, this.customToggleY, this.toggleW, this.toggleH, mouseX, mouseY)) {
            boolean next = !ClientManagers.moduleManager.clientSettings.customBackground.getValue();
            ClientManagers.moduleManager.clientSettings.customBackground.setValue(next);
            Augustus.getInstance().getConfigManager().saveAllAsync();
            ClientSettings.playButtonClickSound();
            return;
         }
         if (menuHovered(this.mouseToggleX, this.mouseToggleY, this.toggleW, this.toggleH, mouseX, mouseY)) {
            boolean next = !ClientManagers.moduleManager.clientSettings.mouseInputsOnBackgrounds.getValue();
            ClientManagers.moduleManager.clientSettings.mouseInputsOnBackgrounds.setValue(next);
            Augustus.getInstance().getConfigManager().saveAllAsync();
            ClientSettings.playButtonClickSound();
            return;
         }

         for (int c = 0; c < 3; c++) {
            if (menuHovered(this.chipX[c], this.chipY, this.chipW[c], this.chipH, mouseX, mouseY)) {
               this.filterMode = c;
               this.lastRowCount = -1;
               this.scrollBar.scrollOffset = 0.0D;
               ClientSettings.playButtonClickSound();
               return;
            }
         }

         float viewTop = this.gridPanelY + 33.0F;
         float viewBottom = this.gridPanelY + this.gridPanelH - 10.0F;
         float viewLeft = this.gridPanelX + 12.0F;
         float viewW = Math.max(120.0F, this.gridPanelW - 28.0F);
         if (mouseX >= viewLeft && mouseX <= viewLeft + viewW && mouseY >= viewTop && mouseY <= viewBottom) {
            List<GalleryItem> filteredItems = this.getFilteredItems();
            int cols = this.columnsForWidth();
            float cardW = (viewW - COL_GAP * (cols - 1)) / (float)cols;
            float scrollOffset = (float)((int)this.scrollBar.getClampedScrollOffset());
            for (int i = 0; i < filteredItems.size(); i++) {
               int row = i / cols;
               int col = i % cols;
               float cardX = viewLeft + col * (cardW + COL_GAP);
               float cardY = viewTop + scrollOffset + row * ROW_PITCH;
               if (menuHovered(cardX, cardY, cardW, CARD_H, mouseX, mouseY)) {
                  GalleryItem item = filteredItems.get(i);
                  BackgroundShaderManager bsm = Augustus.getInstance().getBackgroundShaderManager();
                  if (item.isScene()) {
                     bsm.selectAnimatedScene(item.scene.getId());
                  } else {
                     bsm.selectShader(item.shader.getName().toLowerCase(Locale.ROOT));
                     bsm.setBackdropMode(BackgroundShaderManager.MODE_SHADER);
                  }
                  if (!ClientManagers.moduleManager.clientSettings.customBackground.getValue()) {
                     ClientManagers.moduleManager.clientSettings.customBackground.setValue(true);
                  }
                  Augustus.getInstance().getConfigManager().saveAllAsync();
                  ClientSettings.playButtonClickSound();
                  break;
               }
            }
         }
      }

      this.scrollBar.handleMouseClick((double)mouseX, (double)mouseY);
   }

   @Override
   public void keyTyped(char typedChar, int keyCode) throws IOException {
      super.keyTyped(typedChar, keyCode);
      if (this.searchField != null && !this.searchField.getText().equals(this.lastQuery)) {
         this.lastQuery = this.searchField.getText();
         this.lastRowCount = -1;
         this.scrollBar.scrollOffset = 0.0D;
      }
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
