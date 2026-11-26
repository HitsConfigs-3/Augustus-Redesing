package net.augustus.clickgui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.augustus.Augustus;
import net.augustus.clickgui.components.TextFieldComponent;
import net.augustus.config.ClipboardConfigCodec;
import net.augustus.events.listeners.ClickGuiListener;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.modules.Categorys;
import net.augustus.modules.FeatureBase;
import net.augustus.modules.Module;
import net.augustus.render.RenderUtil;
import net.augustus.settings.DynamicSettingList;
import net.augustus.settings.Setting;
import net.augustus.settings.SettingGroup;
import net.augustus.ui.modern.components.ModernButton;
import net.augustus.ui.modern.components.ModernSettingsPanel;
import net.augustus.ui.modern.components.ModernToggleSwitch;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.ui.widgets.SmoothScrollBar;
import net.augustus.utils.ChatUtil;
import net.augustus.utils.audio.ClientSounds;
import net.augustus.utils.input.KeyboardInputUtil;
import net.augustus.utils.render.ScrollFadeCompositor;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

public class ModuleSettingsPanel {
   private static final String TAB_ALL = "__ALL__";
   private static final String TAB_GENERAL = "__GENERAL__";

   private int panelHeight;
   private int cornerRadius;
   public boolean horizontalScrollVisible = false;
   private boolean anySettingFocused;
   private int screenX;
   private int panelWidth;
   private Module currentModule;
   private CustomFontUtil settingFont;
   public boolean verticalScrollVisible = false;
   private final SmoothScrollBar horizontalScrollBar;
   private int screenY;
   private CustomFontUtil headerFont;
   private final SmoothScrollBar verticalScrollBar;
   private final ModernButton resetButton = new ModernButton();
   private final ModernToggleSwitch heroToggleSwitch = new ModernToggleSwitch();
   private float heroCardAnim = 0.0F;
   private float heroToggleAnim = 0.0F;
   private float tabContentTransAnim = 1.0F;
   private String lastModuleName = "";

   private String activeSectionKey = TAB_ALL;
   private final List<String> sectionTabKeys = new ArrayList<>();
   private final List<String> sectionTabLabels = new ArrayList<>();
   private final List<float[]> sectionTabRects = new ArrayList<>();
   private int lastContentTopY = 82;

   public ModuleSettingsPanel() {
      this.currentModule = Augustus.getInstance().getModuleManager().velocity;
      this.anySettingFocused = false;
      this.settingFont = FontManager.interMedium;
      this.headerFont = FontManager.interSemiBoldLarge;
      this.verticalScrollBar = new SmoothScrollBar();
      this.horizontalScrollBar = new SmoothScrollBar();
   }

   private int computeContentTopY(Module module) {
      return 84;
   }

   private static String formatCategoryLabel(Categorys cat) {
      if (cat == null) {
         return "Module";
      }
      String raw = cat.name().toLowerCase(Locale.ROOT);
      return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
   }

   private boolean isSettingVisibleInPanel(Module module, Setting setting) {
      setting.setCachedVisible(setting.isVisible());
      return setting.isVisible()
         && setting.isCachedVisible()
         && (setting.isVisualOnly() || !module.isLoadFromConfig() || Augustus.getInstance().isConfigEditingEnabled());
   }

   private boolean isSettingIncludedInActiveTab(Setting setting) {
      if (TAB_ALL.equals(this.activeSectionKey)) {
         return true;
      }
      if (TAB_GENERAL.equals(this.activeSectionKey)) {
         return !(setting instanceof SettingGroup);
      }
      return setting instanceof SettingGroup && setting.getName().equals(this.activeSectionKey);
   }

   private void rebuildSectionTabs(Module module) {
      this.sectionTabKeys.clear();
      this.sectionTabLabels.clear();

      List<SettingGroup> visibleGroups = new ArrayList<>();
      for (Setting setting : (Iterable<Setting>)(module.getSettings())) {
         if (this.isSettingVisibleInPanel(module, setting) && setting instanceof SettingGroup) {
            visibleGroups.add((SettingGroup)setting);
         }
      }

      if (!visibleGroups.isEmpty()) {
         this.sectionTabKeys.add(TAB_ALL);
         this.sectionTabLabels.add("All");
         this.sectionTabKeys.add(TAB_GENERAL);
         this.sectionTabLabels.add("General");
         for (SettingGroup group : visibleGroups) {
            this.sectionTabKeys.add(group.getName());
            this.sectionTabLabels.add(group.getFormattedDisplayName());
         }
      }

      if (!TAB_ALL.equals(this.activeSectionKey) && !this.sectionTabKeys.contains(this.activeSectionKey)) {
         this.activeSectionKey = TAB_ALL;
      }
   }

   public void handleScrollBarClick(int n, int n2, int n3) throws IOException {
      this.verticalScrollBar.mouseClicked(n, n2, n3);
      this.horizontalScrollBar.mouseClicked(n, n2, n3);
   }

   public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      if (mouseButton == 0 && this.heroToggleSwitch.isHovered(mouseX, mouseY) && this.currentModule != null) {
         this.currentModule.toggle();
         ClickGuiListener.postClickGuiRender();
         return;
      }

      if (mouseButton == 0 && this.resetButton.isHovered(mouseX, mouseY)) {
         for (Setting setting : (Iterable<Setting>)(Augustus.getInstance().getSettingManager().getSettings())) {
            if (setting.getParentModule() == this.currentModule) {
               setting.resetToDefault();
            }
         }
         return;
      }

      if (this.currentModule.handleKeybindClick(mouseX, mouseY, mouseButton)) {
         return;
      }

      if (mouseY < this.lastContentTopY) {
         return;
      }

      if (this.currentModule.handleBehaviorCardClick(mouseX, mouseY, mouseButton)) {
         return;
      }

      if (mouseButton == 0 && !this.sectionTabRects.isEmpty()) {
         for (int i = 0; i < this.sectionTabRects.size() && i < this.sectionTabKeys.size(); i++) {
            float[] rect = this.sectionTabRects.get(i);
            if (RenderUtil.isHovered(rect[0], rect[1], rect[2], rect[3], mouseX, mouseY)) {
               String clickedKey = this.sectionTabKeys.get(i);
               if (!clickedKey.equals(this.activeSectionKey)) {
                  this.tabContentTransAnim = 0.0F;
               }
               this.activeSectionKey = clickedKey;
               this.currentModule.setScrollOffsetY(0);
               this.verticalScrollBar.resetPendingScroll();
               if (!TAB_ALL.equals(clickedKey) && !TAB_GENERAL.equals(clickedKey)) {
                  for (Setting s : (Iterable<Setting>)(this.currentModule.getSettings())) {
                     if (s instanceof SettingGroup && s.getName().equals(clickedKey)) {
                        ((SettingGroup)s).setExpanded(true);
                        break;
                     }
                  }
               }
               return;
            }
         }
      }

      for (Setting setting2 : (Iterable<Setting>)(this.currentModule.getSettings())) {
         if (this.isSettingVisibleInPanel(this.currentModule, setting2) && this.isSettingIncludedInActiveTab(setting2)) {
            if (setting2.renderY + setting2.getHeight() > -20 && setting2.renderY < this.panelHeight + 20) {
               if (setting2.mouseClicked(mouseX, mouseY, mouseButton)) {
                  break;
               }
            }
         }
      }
   }

   /**
    * GUI Scale: glScissor and ScrollFadeCompositor work in raw framebuffer pixels and ignore the
    * modelview matrix, so the clip rects this panel builds must be mapped through exactly the transform
    * the ClickGUI renders with (and inverts for mouse input).
    *
    * <p>The panel is drawn inside the window's content zoom and is handed its origin in CONTENT
    * coordinates (see MainClickGuiWindow.drawSettingsContent), so contentToScreenX/Y - the forward form
    * of the zoom - is the whole conversion; no window origin subtraction is involved any more, which is
    * what previously left the clip rects unscaled at non-1.0 factors.
    */
   private float clipScreenX(float contentX) {
      MainClickGuiWindow main = MainClickGuiWindow.activeMainWindow();
      return main != null ? main.contentToScreenX(contentX) : contentX;
   }

   private float clipScreenY(float contentY) {
      MainClickGuiWindow main = MainClickGuiWindow.activeMainWindow();
      return main != null ? main.contentToScreenY(contentY) : contentY;
   }

   /** Content-space size -> on-screen size for the same clip rect. */
   private float clipSpan(float contentSpan) {
      MainClickGuiWindow main = MainClickGuiWindow.activeMainWindow();
      return main != null ? main.contentSpanToScreen(contentSpan) : contentSpan;
   }

   public void drawPanel(int n, int n2, int n3, int n4, int n5, int n6, int n7, Module module, boolean flag) {
      if (module != null && module != this.currentModule) {
         this.activeSectionKey = TAB_ALL;
         this.currentModule = module;
         this.tabContentTransAnim = 0.0F;
         this.verticalScrollBar.resetPendingScroll();
      } else if (module != null) {
         this.currentModule = module;
      }

      this.screenX = n5;
      this.screenY = n6;
      this.panelWidth = n3;
      this.panelHeight = n4;
      this.cornerRadius = n7;
      this.headerFont = FontManager.interSemiBoldLarge;
      this.settingFont = FontManager.interMedium;

      this.rebuildSectionTabs(this.currentModule);
      int contentTopY = this.computeContentTopY(this.currentModule);
      this.lastContentTopY = contentTopY;

      // Elevated Level 2 Hero Module Header Card (8px radius, crisp 1px crystal reflection border)
      float headerCardW = ModernSettingsPanel.resolveCardWidth(0, n3);
      float heroY = 2.0F;
      float heroH = 66.0F;
      boolean enabled = this.currentModule.isToggled();
      boolean heroHovered = RenderUtil.isHovered(0.0F, heroY, headerCardW, heroH, n, n2);
      if (!this.currentModule.getName().equals(this.lastModuleName)) {
         this.heroToggleAnim = enabled ? 1.0F : 0.0F;
         this.lastModuleName = this.currentModule.getName();
      }
      this.heroCardAnim = UiAnimation.smooth(this.heroCardAnim, heroHovered ? 1.0F : 0.0F, 14.0F);
      this.heroToggleAnim = UiAnimation.smooth(this.heroToggleAnim, enabled ? 1.0F : 0.0F, 18.0F);

      // Close/open transition: the envelope cannot live in GL state (the glass primitives and the font
      // renderer both reset the colour per call), so it is folded into each colour this panel paints.
      int accent = ClickGuiScreen.fadeArgb(UiTheme.accentColor());
      GlassRenderer.drawHeroCard(0.0F, heroY, headerCardW, heroH, UiTheme.CARD_RADIUS, this.heroCardAnim, this.heroToggleAnim, (float)n, (float)n2, accent);

      // Left visual squircle icon container
      float iconSize = 40.0F;
      float iconBoxX = 14.0F;
      float iconBoxY = heroY + (heroH - iconSize) * 0.5F;
      int wellTop = UiTheme.lerpArgb(UiTheme.glassRecessedFillArgb(this.heroCardAnim * 0.4F, 0.0F, accent), UiTheme.withAlpha(UiTheme.lerpArgb(accent, 0xFF1D1833, 0.48F), 185), this.heroToggleAnim);
      int wellBot = UiTheme.lerpArgb(wellTop, 0xFF0A0C12, 0.25F);
      int wellBorder = ClickGuiScreen.fadeArgb(UiTheme.lerpArgb(UiTheme.GLASS_BORDER, UiTheme.withAlpha(UiTheme.accentSoftGlow(), 175), this.heroToggleAnim));
      GlassRenderer.drawRoundedGradientBox(iconBoxX, iconBoxY, iconSize, iconSize, UiTheme.CARD_RADIUS, wellTop, wellBot, wellBorder, 1.0F, 0.08F + 0.06F * this.heroToggleAnim);
      int glyphColor = ClickGuiScreen.fadeArgb(UiTheme.lerpArgb(UiTheme.secondaryTextColor(), UiTheme.TEXT_PRIMARY, Math.max(this.heroToggleAnim, this.heroCardAnim * 0.8F)));
      LucideIcon.forCategory(this.currentModule.getCategorys()).drawCentered(iconBoxX + iconSize * 0.5F, iconBoxY + iconSize * 0.5F, 19.0F, glyphColor);

      // Right balanced cluster: [Bind pill] [Reset button] | [Toggle Switch]
      float rightEdge = headerCardW - 14.0F;
      float toggleW = 36.0F;
      float toggleH = 18.0F;
      float toggleX = rightEdge - toggleW;
      float toggleY = heroY + (heroH - toggleH) * 0.5F;
      this.heroToggleSwitch.render(toggleX, toggleY, toggleW, toggleH, enabled, n, n2);

      float divX = toggleX - 10.0F;
      GlassRenderer.drawSeparator(divX, heroY + 20.0F, divX, heroY + heroH - 20.0F, 1.0F, ClickGuiScreen.fadeArgb(UiTheme.GLASS_DIVIDER));

      float resetW = 68.0F;
      float resetX = divX - 10.0F - resetW;
      float actionY = heroY + (heroH - 24.0F) * 0.5F;
      this.resetButton.render(resetX, actionY, resetW, 24.0F, "Reset", LucideIcon.ROTATE_CCW, ModernButton.Variant.GLASS, n, n2);
      int bindW = this.currentModule.renderKeybindPill((int)(resetX - 8.0F), (int)actionY, n, n2);

      // Left hierarchy: SemiBold Title + Category badge + Regular #94A3B8 Description
      float textLeft = iconBoxX + iconSize + 14.0F;
      float headerLeftMaxW = Math.max(120.0F, (resetX - 8.0F - (float)bindW) - textLeft - 14.0F);
      String catLabel = formatCategoryLabel(this.currentModule.getCategorys());
      float badgeW = FontManager.interSmall.getStringWidth(catLabel) + 14.0F;
      float titleMaxW = Math.max(80.0F, headerLeftMaxW - badgeW - 10.0F);
      String modTitle = ModernSettingsPanel.trimToWidth(this.headerFont, this.currentModule.getModuleName(), titleMaxW);
      String modDesc = this.currentModule.getDescription() != null ? this.currentModule.getDescription().trim() : "";

      float titleY = modDesc.isEmpty() ? heroY + (heroH - this.headerFont.getFontHeight()) * 0.5F : heroY + 13.0F;
      this.headerFont.drawString(modTitle, (double)textLeft, (double)titleY, ClickGuiScreen.fadeArgb(UiTheme.TEXT_PRIMARY));

      float badgeX = textLeft + this.headerFont.getStringWidth(modTitle) + 10.0F;
      float badgeY = titleY + (this.headerFont.getFontHeight() - 17.0F) * 0.5F;
      if (badgeX + badgeW <= textLeft + headerLeftMaxW) {
         GlassRenderer.drawRoundedBox(badgeX, badgeY, badgeW, 17.0F, 5.0F, UiTheme.withAlpha(accent, 42), ClickGuiScreen.fadeArgb(UiTheme.withAlpha(UiTheme.accentSoftGlow(), 125)), 1.0F);
         FontManager.interSmall.drawCenteredStringInBox(catLabel, (double)badgeX, (double)badgeY, (double)badgeW, 17.0, ClickGuiScreen.fadeArgb(UiTheme.accentSoftGlow()));
      }

      if (!modDesc.isEmpty()) {
         String safeDesc = ModernSettingsPanel.trimToWidth(FontManager.interRegular, modDesc, headerLeftMaxW);
         FontManager.interRegular.drawString(safeDesc, (double)textLeft, (double)(heroY + 38.0F), ClickGuiScreen.fadeArgb(UiTheme.mutedTextColor()));
      }

      GlassRenderer.drawSeparator(0.0F, (float)(contentTopY - 6), headerCardW, (float)(contentTopY - 6), UiTheme.SEPARATOR_THICKNESS, ClickGuiScreen.fadeArgb(UiTheme.GLASS_DIVIDER));

      RenderUtil.scissorRectScaled(
         this.clipScreenX((float)(this.screenX - 4)),
         this.clipScreenY((float)(this.screenY + contentTopY - 2)),
         this.clipScreenX((float)(this.screenX + n3)),
         this.clipScreenY((float)(this.screenY + this.panelHeight)),
         1.0
      );

      this.tabContentTransAnim = UiAnimation.smooth(this.tabContentTransAnim, 1.0F, 18.0F);
      float tabSlideY = (1.0F - UiAnimation.easeOutCubic(this.tabContentTransAnim)) * 7.0F;

      int scrollX = this.currentModule.getScrollOffsetX();
      int scrollY = this.currentModule.getScrollOffsetY();
      int cursorY = contentTopY + 4;
      float cardW = ModernSettingsPanel.resolveCardWidth(scrollX, n3);
      int maxContentRight = Math.round(ModernSettingsPanel.resolveCardWidth(0, n3));

      boolean fadeCaptured = ScrollFadeCompositor.beginRegion(
         this.clipScreenX((float)(this.screenX - 4)),
         this.clipScreenY((float)(this.screenY + contentTopY - 2)),
         this.clipSpan((float)(n3 + 4)),
         this.clipSpan((float)(this.panelHeight - contentTopY + 2))
      );

      GL11.glPushMatrix();
      GL11.glTranslatef(0.0F, tabSlideY, 0.0F);
      try {
         // Section Filter Tabs (Button Group) when module defines collapsible sections
         this.sectionTabRects.clear();
         if (!this.sectionTabLabels.isEmpty()) {
            int activeIdx = Math.max(0, this.sectionTabKeys.indexOf(this.activeSectionKey));
            int tabsY = cursorY + scrollY;
            int tabsH = ModernSettingsPanel.drawSectionFilterTabs(
               (float)scrollX,
               (float)tabsY,
               cardW,
               this.sectionTabLabels,
               activeIdx,
               this.sectionTabRects,
               tabsY + 30 > contentTopY - 20 && tabsY < n4 + 20,
               n,
               n2
            );
            cursorY += tabsH + 12;
         }

         boolean showBehavior = TAB_ALL.equals(this.activeSectionKey) || TAB_GENERAL.equals(this.activeSectionKey);
         if (showBehavior) {
            int secY = cursorY + scrollY;
            if (secY + ModernSettingsPanel.SECTION_TITLE_HEIGHT > -20 && secY < n4 + 20) {
               ModernSettingsPanel.drawSectionTitle((float)scrollX, (float)secY, cardW, "Module");
            }
            cursorY += ModernSettingsPanel.SECTION_TITLE_HEIGHT + 5;

            int behY = cursorY + scrollY;
            boolean drawBeh = behY + 84 > -20 && behY < n4 + 20;
            int behH = this.currentModule.renderBehaviorCard(scrollX, behY, n3, n, n2, drawBeh);
            cursorY += behH + ModernSettingsPanel.SECTION_TOP_GAP;
         }

         boolean hasAnyGroupInModule = !this.sectionTabLabels.isEmpty();
         boolean anyGroupRendered = false;
         boolean inUngroupedBlock = false;
         int ungroupedCount = 0;

         for (Setting setting : (Iterable<Setting>)(this.currentModule.getSettings())) {
            if (!this.isSettingVisibleInPanel(this.currentModule, setting) || !this.isSettingIncludedInActiveTab(setting)) {
               continue;
            }

            boolean isSectionContainer = setting instanceof SettingGroup || setting instanceof DynamicSettingList;
            if (isSectionContainer) {
               if (inUngroupedBlock || anyGroupRendered) {
                  cursorY += ModernSettingsPanel.SECTION_TOP_GAP;
               }
               inUngroupedBlock = false;
               anyGroupRendered = true;
            } else {
               if (!inUngroupedBlock) {
                  if (anyGroupRendered) {
                     cursorY += ModernSettingsPanel.SECTION_TOP_GAP;
                  }
                  String sectionName = anyGroupRendered ? "Additional Settings" : (hasAnyGroupInModule ? "General" : "Settings");
                  int secTitleY = cursorY + scrollY;
                  if (secTitleY + ModernSettingsPanel.SECTION_TITLE_HEIGHT > -20 && secTitleY < n4 + 20) {
                     ModernSettingsPanel.drawSectionTitle((float)scrollX, (float)secTitleY, cardW, sectionName);
                  }
                  cursorY += ModernSettingsPanel.SECTION_TITLE_HEIGHT + 5;
                  inUngroupedBlock = true;
                  ungroupedCount = 0;
               } else if (ungroupedCount > 0) {
                  cursorY += ModernSettingsPanel.CARD_GAP;
               }
               ungroupedCount++;
            }

            int drawY = cursorY + scrollY;
            boolean itemVisible = drawY + setting.getHeight() > -20 && drawY < n4 + 20;
            setting.render(n, n2, scrollX, drawY, n3, n4, this.settingFont, itemVisible);
            cursorY += setting.getHeight();
            if (maxContentRight < setting.getContentWidth()) {
               maxContentRight = setting.getContentWidth();
            }
         }

         cursorY += 8;
      } finally {
         GL11.glPopMatrix();
         if (fadeCaptured) {
            float topFade = scrollY < -4 ? 14.0F : 0.0F;
            float bottomFade = (cursorY + scrollY) > (n4 - 2) ? 28.0F : 0.0F;
            ScrollFadeCompositor.endAndComposite(
               -4.0F, (float)(contentTopY - 2), (float)(n3 + 4), (float)(this.panelHeight - contentTopY + 2), topFade, bottomFade
            );
         }
      }

      if (n2 >= contentTopY) {
         for (Setting setting2 : (Iterable<Setting>)(this.currentModule.getSettings())) {
            if (this.isSettingVisibleInPanel(this.currentModule, setting2)
               && this.isSettingIncludedInActiveTab(setting2)
               && setting2.renderY + setting2.getHeight() > contentTopY) {
               setting2.renderTooltip();
            }
         }
      }

      this.anySettingFocused = false;
      this.checkFocusedSettings(this.currentModule.getSettings());
      RenderUtil.scissorRectScaled(
         this.clipScreenX((float)this.screenX),
         this.clipScreenY((float)(this.screenY + contentTopY - 2)),
         this.clipScreenX((float)(this.screenX + n3)),
         this.clipScreenY((float)(this.screenY + this.panelHeight)),
         1.0
      );
      int colorInt2 = ClickGuiScreen.fadeArgb(UiTheme.accentColor());
      this.verticalScrollVisible = this.verticalScrollBar.drawVerticalBar(n3 - 10, contentTopY + 2, n3 - 2, n4 - 2, contentTopY - scrollY, cursorY, colorInt2);
      this.horizontalScrollVisible = this.horizontalScrollBar.drawHorizontalBar(0, n4 - 8, n3 - 12, n4 - 1, -scrollX, maxContentRight, colorInt2);
      if (this.horizontalScrollVisible) {
         this.currentModule.setScrollOffsetY(this.verticalScrollBar.updateScrollOffset(cursorY, contentTopY, n4, scrollY, 0, 0, n3, n4 - 50, n, n2));
      } else {
         this.currentModule.setScrollOffsetY(this.verticalScrollBar.updateScrollOffset(cursorY, contentTopY, n4, scrollY, 0, 0, n3, n4, n, n2));
      }

      if (this.verticalScrollVisible) {
         this.currentModule.setScrollOffsetX(this.horizontalScrollBar.updateScrollOffset(maxContentRight, 0, n3, scrollX, 0, n4 - 50, n3, n4, n, n2));
      } else {
         this.currentModule.setScrollOffsetX(this.horizontalScrollBar.updateScrollOffset(maxContentRight, 0, n3, scrollX, 0, 0, n3, n4, n, n2));
      }
   }

   public boolean isAnySettingFocused() {
      return this.anySettingFocused;
   }

   public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
      for (Setting setting : (Iterable<Setting>)(this.currentModule.getSettings())) {
         if (setting.isVisible() && setting.isCachedVisible() && this.isSettingIncludedInActiveTab(setting)) {
            setting.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
         }
      }
   }

   public void keyTyped(char typedChar, int keyCode) throws IOException {
      this.currentModule.handleSettingsKeyTyped(typedChar, keyCode);
      if (this.currentModule != null && KeyboardInputUtil.isCtrlKeyCombo(keyCode) && Display.isActive()) {
         if (Augustus.getInstance().isConfigEditingEnabled() && !TextFieldComponent.anyFieldFocused) {
            switch (keyCode) {
               case 45:
               case 46:
                  if (!ClipboardConfigCodec.exportModule(this.currentModule)) {
                     ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE6);
                     ChatUtil.sendChat("Failed to export " + ((FeatureBase)this.currentModule).getName() + " to clipboard");
                  }
                  break;
               case 47:
                  if (!ClipboardConfigCodec.importModule(this.currentModule)) {
                     ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE6);
                     ChatUtil.sendChat("Failed to import " + ((FeatureBase)this.currentModule).getName() + " to clipboard");
                  }
            }
         }
      }

      for (Setting setting : (Iterable<Setting>)(this.currentModule.getSettings())) {
         if (setting.isVisible() && setting.isCachedVisible() && this.isSettingIncludedInActiveTab(setting)) {
            setting.keyTyped(typedChar, keyCode);
         }
      }
   }

   public void mouseReleased(int mouseX, int mouseY, int state) {
      this.verticalScrollBar.mouseReleased(mouseX, mouseY, state);
      this.horizontalScrollBar.mouseReleased(mouseX, mouseY, state);

      for (Setting setting : (Iterable<Setting>)(this.currentModule.getSettings())) {
         if (setting.isVisible() && setting.isCachedVisible() && this.isSettingIncludedInActiveTab(setting)) {
            setting.mouseReleased(mouseX, mouseY, state);
         }
      }
   }

   public void onClose() {
      for (Setting setting : (Iterable<Setting>)(this.currentModule.getSettings())) {
         if (setting.isVisible() && setting.isCachedVisible()) {
            setting.onGuiClosed();
         }
      }
   }

   private void checkFocusedSettings(List list) {
      for (Setting setting : (Iterable<Setting>)(list)) {
         if (this.anySettingFocused) {
            return;
         }

         if (setting.isVisible()) {
            if (setting.isFocused()) {
               this.anySettingFocused = true;
               return;
            }

            if (setting instanceof SettingGroup || setting instanceof DynamicSettingList) {
               if (setting instanceof SettingGroup) {
                  this.checkFocusedSettings(((SettingGroup)setting).getChildren());
               } else {
                  this.checkFocusedSettings(((DynamicSettingList)setting).getEntries());
               }
            }
         }
      }
   }
}
