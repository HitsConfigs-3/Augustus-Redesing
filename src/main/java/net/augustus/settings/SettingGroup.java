package net.augustus.settings;

import java.io.IOException;
import java.util.ArrayList;
import net.augustus.Augustus;
import net.augustus.ClientManagers;
import net.augustus.clickgui.ClickGuiScreen;
import net.augustus.clickgui.MainClickGuiWindow;
import net.augustus.config.ClipboardConfigCodec;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.modules.Module;
import net.augustus.modules.misc.Dummy;
import net.augustus.render.RenderUtil;
import net.augustus.settings.property.BooleanProperty;
import net.augustus.ui.modern.components.ModernSettingsPanel;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.utils.ChatUtil;
import net.augustus.utils.audio.ClientSounds;
import org.lwjgl.opengl.GL11;

public class SettingGroup extends Setting {
   private int maxChildWidth;
   private final BooleanProperty b;
   private float labelWidth;
   private float headerPillWidth = 100.0F;
   private final ArrayList visibleChildren;
   private int renderedHeight = ModernSettingsPanel.TOP_GROUP_HEADER_HEIGHT;
   private int visibleChildCount;
   private final ArrayList children;
   private float expandAnim = 0.0F;
   private boolean animInitialized = false;

   private int getHeaderHeight() {
      return this.getParentGroup() != null
         ? ModernSettingsPanel.SUB_GROUP_HEADER_HEIGHT
         : ModernSettingsPanel.TOP_GROUP_HEADER_HEIGHT;
   }

   @Override
   public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
      super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
      if (this.isExpanded()) {
         int n = this.getHeaderHeight() + 6;
         int visibleIdx = 0;

         for (int i = 0; i < this.visibleChildCount && i < this.visibleChildren.size(); i++) {
            Setting setting = (Setting)this.visibleChildren.get(i);
            if (setting.isVisible() && setting.isCachedVisible()) {
               if (visibleIdx > 0) {
                  n += ModernSettingsPanel.CARD_GAP;
               }
               if (this.renderY + n + setting.getHeight() > -20 && this.renderY + n < this.boundsHeight + 20) {
                  setting.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
               }

               n += setting.getHeight();
               visibleIdx++;
            }
         }
      }
   }

   public SettingGroup(Module module, String str, SettingGroup doubleValue) {
      super(module, doubleValue, str + "Groupygroupy");
      this.children = new ArrayList();
      this.renderedHeight = this.getHeaderHeight();
      this.maxChildWidth = 0;
      this.b = new BooleanProperty(this, "B", false);
      this.labelWidth = -1.0F;
      this.visibleChildren = new ArrayList();
      this.visibleChildCount = 0;
      this.withDisplayName(str);
      if (module != null && !(module instanceof Dummy)) {
         ClientManagers.settingManager.registerSetting(this);
         doubleValue.getChildren().removeIf(obj -> this.matchesSubGroupName((Setting)obj));
         doubleValue.getChildren().add(this);
      }
   }

   @Override
   public void resetToDefault() {
      this.b.resetToDefault();
   }

   public SettingGroup visibleWhen(SettingVisibilityCondition condition) {
      return (SettingGroup)super.withVisibility(condition);
   }

   private boolean matchesExpandedGroupName(Setting setting) {
      return setting.getName().equals(this.getName());
   }

   private boolean matchesGroupName(Setting setting) {
      return setting.getName().equals(this.getName());
   }

   public SettingGroup(Module module, String str, boolean flag) {
      super(module, null, str + "Groupygroupy");
      this.children = new ArrayList();
      this.renderedHeight = this.getHeaderHeight();
      this.maxChildWidth = 0;
      this.b = new BooleanProperty(this, "B", false);
      this.labelWidth = -1.0F;
      this.visibleChildren = new ArrayList();
      this.visibleChildCount = 0;
      this.withDisplayName(str);
      this.setExpanded(flag);
      BooleanProperty booleanProperty = this.b;
      Boolean bool = flag;
      booleanProperty.setDefaultValue(bool);
      if (module != null && !(module instanceof Dummy)) {
         ClientManagers.settingManager.registerSetting(this);
         this.getParentModule().getSettings().removeIf(obj -> this.matchesExpandedGroupName((Setting)obj));
         this.getParentModule().getSettings().add(this);
      }
   }

   @Override
   public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      super.mouseClicked(mouseX, mouseY, mouseButton);
      boolean flag = false;
      int headerH = this.getHeaderHeight();
      int n = headerH + 6;
      int visibleIdx = 0;
      if (this.isExpanded() && Augustus.getInstance().getClickGui().getMainWindow().contextMenuSetting != this) {
         for (int i = 0; i < this.visibleChildCount && i < this.visibleChildren.size(); i++) {
            Setting setting = (Setting)this.visibleChildren.get(i);
            if (setting.isVisible() && setting.isCachedVisible() && (setting.isVisualOnly() || !this.getParentModule().isLoadFromConfig() || Augustus.getInstance().isConfigEditingEnabled())) {
               if (visibleIdx > 0) {
                  n += ModernSettingsPanel.CARD_GAP;
               }
               if (this.renderY + n + setting.getHeight() > -20 && this.renderY + n < this.boundsHeight + 20) {
                  if (setting.mouseClicked(mouseX, mouseY, mouseButton)) {
                     flag = true;
                     break;
                  }
               }

               n += setting.getHeight();
               visibleIdx++;
            }
         }
      }

      if (!flag && (mouseButton == 0 || mouseButton == 1)) {
         float hitW = Math.max(this.headerPillWidth, ModernSettingsPanel.resolveCardWidth(this.renderX, this.boundsWidth));
         if (RenderUtil.isHovered((float)this.renderX, (float)this.renderY, hitW, (float)headerH, mouseX, mouseY)
            && this != Augustus.getInstance().getClickGui().getMainWindow().contextMenuSetting) {
            if (mouseButton == 0) {
               this.setExpanded(!this.isExpanded());
            } else {
               Augustus.getInstance().getClickGui().getMainWindow().contextMenuX = mouseX + Augustus.getInstance().getClickGui().getMainWindow().getSettingsOffsetX();
               Augustus.getInstance().getClickGui().getMainWindow().contextMenuY = mouseY + Augustus.getInstance().getClickGui().getMainWindow().getSettingsOffsetY();
               Augustus.getInstance().getClickGui().getMainWindow().contextMenuSetting = this;
            }

            flag = true;
         } else if (this == Augustus.getInstance().getClickGui().getMainWindow().contextMenuSetting) {
            int offsetX = Augustus.getInstance().getClickGui().getMainWindow().getSettingsOffsetX();
            int offsetY = Augustus.getInstance().getClickGui().getMainWindow().getSettingsOffsetY();
            if (RenderUtil.isHovered(
               (float)Augustus.getInstance().getClickGui().getMainWindow().contextMenuX, (float)Augustus.getInstance().getClickGui().getMainWindow().contextMenuY, 130.0F, 59.0F, mouseX + offsetX, mouseY + offsetY
            )) {
               this.handleContextMenuClick(mouseX + offsetX, mouseY + offsetY);
               flag = true;
            } else {
               Augustus.getInstance().getClickGui().getMainWindow().contextMenuSetting = null;
            }
         }
      }

      return flag;
   }

   public boolean hasVisibleChildren() {
      return this.visibleChildCount != 0;
   }

   @Override
   public void keyTyped(char typedChar, int keyCode) throws IOException {
      super.keyTyped(typedChar, keyCode);
      if (this.isExpanded()) {
         int n = this.getHeaderHeight() + 6;
         int visibleIdx = 0;

         for (int i = 0; i < this.visibleChildCount && i < this.visibleChildren.size(); i++) {
            Setting setting = (Setting)this.visibleChildren.get(i);
            if (setting.isVisible() && setting.isCachedVisible()) {
               if (visibleIdx > 0) {
                  n += ModernSettingsPanel.CARD_GAP;
               }
               if (this.renderY + n + setting.getHeight() > -20 && this.renderY + n < this.boundsHeight + 20) {
                  setting.keyTyped(typedChar, keyCode);
               }

               n += setting.getHeight();
               visibleIdx++;
            }
         }
      }
   }

   @Override
   public Setting withVisibility(SettingVisibilityCondition condition) {
      return this.visibleWhen(condition);
   }

   @Override
   public void mouseReleased(int mouseX, int mouseY, int state) {
      super.mouseReleased(mouseX, mouseY, state);
      if (this.isExpanded()) {
         int n = this.getHeaderHeight() + 6;
         int visibleIdx = 0;

         for (int i = 0; i < this.visibleChildCount && i < this.visibleChildren.size(); i++) {
            Setting setting = (Setting)this.visibleChildren.get(i);
            if (setting.isVisible() && setting.isCachedVisible()) {
               if (visibleIdx > 0) {
                  n += ModernSettingsPanel.CARD_GAP;
               }
               if (this.renderY + n + setting.getHeight() > -20 && this.renderY + n < this.boundsHeight + 20) {
                  setting.mouseReleased(mouseX, mouseY, state);
               }

               n += setting.getHeight();
               visibleIdx++;
            }
         }
      }
   }

   private void handleContextMenuClick(int n, int n2) {
      if (Augustus.getInstance().getClickGui().getMainWindow().contextMenuSetting == this && Augustus.getInstance().isConfigEditingEnabled()) {
         int contextMenuX = Augustus.getInstance().getClickGui().getMainWindow().contextMenuX;
         int contextMenuY = Augustus.getInstance().getClickGui().getMainWindow().contextMenuY;
         if (RenderUtil.isHovered((float)contextMenuX, (float)contextMenuY, 130.0F, 19.0F, n, n2)) {
            if (!ClipboardConfigCodec.importSettingGroupFromClipboard(this.getParentModule(), this)) {
               ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE6);
               ChatUtil.sendChat("Failed to import " + this.getName() + " to clipboard");
            }
         } else if (RenderUtil.isHovered((float)contextMenuX, (float)(contextMenuY + 21), 130.0F, 19.0F, n, n2)) {
            if (!ClipboardConfigCodec.exportSettingGroup(this.getParentModule(), this)) {
               ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE6);
               ChatUtil.sendChat("Failed to export " + this.getName() + " to clipboard");
            }
         }
      }
   }

   public boolean isExpanded() {
      return (Boolean)this.b.getValue();
   }

   @Override
   public void renderTooltip() {
      this.drawDescriptionTooltip((int)(this.labelWidth + 20.0F));

      if (this.isExpanded()) {
         for (int i = 0; i < this.visibleChildCount && i < this.visibleChildren.size(); i++) {
            ((Setting)this.visibleChildren.get(i)).renderTooltip();
         }
      }
   }

   public SettingGroup(Module module, String str) {
      super(module, null, str + "Groupygroupy");
      this.children = new ArrayList();
      this.renderedHeight = this.getHeaderHeight();
      this.maxChildWidth = 0;
      this.b = new BooleanProperty(this, "B", false);
      this.labelWidth = -1.0F;
      this.visibleChildren = new ArrayList();
      this.visibleChildCount = 0;
      this.withDisplayName(str);
      if (module != null && !(module instanceof Dummy)) {
         ClientManagers.settingManager.registerSetting(this);
         this.getParentModule().getSettings().removeIf(obj -> this.matchesGroupName((Setting)obj));
         this.getParentModule().getSettings().add(this);
      }
   }

   public void setExpanded(boolean expanded) {
      BooleanProperty booleanProperty = this.b;
      Boolean bool = expanded;
      booleanProperty.setValue(bool);
   }

   @Override
   public int getHeight() {
      return this.renderedHeight;
   }

   @Override
   public void render(int n, int n2, int n3, int n4, int n5, int n6, CustomFontUtil customFontUtil, boolean flag) {
      boolean nested = this.getParentGroup() != null;
      customFontUtil = nested ? FontManager.interSemiBold : FontManager.interBold;
      super.render(n, n2, n3, n4, n5, n6, customFontUtil, flag);
      String displayName2 = this.getFormattedDisplayName();
      boolean flag2 = this.labelWidth == -1.0F;
      if (this.consumeLabelDirty(flag2)) {
         this.labelWidth = (float)((int)customFontUtil.getStringWidth(displayName2));
      }

      int headerH = this.getHeaderHeight();
      float sectionW = ModernSettingsPanel.resolveCardWidth(n3, n5);
      if (!this.animInitialized) {
         this.expandAnim = this.isExpanded() ? 1.0F : 0.0F;
         this.animInitialized = true;
      }
      this.expandAnim = UiAnimation.smooth(this.expandAnim, this.isExpanded() ? 1.0F : 0.0F, 18.0F);
      float easedExpand = UiAnimation.easeOutCubic(this.expandAnim);

      if (flag) {
         this.headerPillWidth = ModernSettingsPanel.drawCollapsibleSectionHeader(
            (float)n3,
            (float)n4,
            sectionW,
            displayName2,
            this.isExpanded(),
            easedExpand,
            this.visibleChildCount,
            nested,
            n,
            n2
         );
         this.tooltipAreaWidth = Math.round(this.labelWidth + 24.0F);
      }

      this.renderedHeight = headerH;
      this.maxChildWidth = Math.round(sectionW);

      if (easedExpand > 0.01F && this.visibleChildCount > 0) {
         int childX = nested ? n3 + 14 : n3;
         int bodyTopOffset = 6;
         int measuredBodyH = bodyTopOffset;
         int renderedVisible = 0;

         boolean pushedScissor = false;
         float slideOffset = -(1.0F - easedExpand) * 6.0F;
         if (flag && easedExpand < 0.995F) {
            // Measure full target body height first cleanly before pushing clipped animated viewport
            int fullBodyH = bodyTopOffset;
            int visIdx = 0;
            for (int i = 0; i < this.visibleChildCount && i < this.visibleChildren.size(); i++) {
               Setting setting = (Setting)this.visibleChildren.get(i);
               if (setting.isVisible() && setting.isCachedVisible() && (setting.isVisualOnly() || !this.getParentModule().isLoadFromConfig() || Augustus.getInstance().isConfigEditingEnabled())) {
                  if (visIdx > 0) {
                     fullBodyH += ModernSettingsPanel.CARD_GAP;
                  }
                  fullBodyH += setting.getHeight();
                  visIdx++;
               }
            }
            if (visIdx > 0) {
               fullBodyH += 4;
            }
            int animatedBodyH = Math.max(0, Math.round((float)fullBodyH * easedExpand));
            MainClickGuiWindow mw = MainClickGuiWindow.activeMainWindow();
            if (animatedBodyH > 1 && mw != null) {
               // GUI Scale: pushVerticalScissor clips in raw framebuffer pixels, ignoring the modelview
               // matrix, so the band has to be mapped through the same content zoom the ClickGUI draws
               // with. n4 is panel-local and the panel origin is a content-space offset
               // (getSettingsOffsetY()), so this is a plain content -> framebuffer conversion.
               float contentTop = (float)mw.getSettingsOffsetY() + (float)(n4 + headerH);
               float contentBottom = contentTop + (float)animatedBodyH;
               GlassRenderer.pushVerticalScissor(mw.contentToScreenY(contentTop), mw.contentToScreenY(contentBottom));
               pushedScissor = true;
            }
         }

         GL11.glPushMatrix();
         if (slideOffset < -0.05F) {
            GL11.glTranslatef(0.0F, slideOffset, 0.0F);
         }
         try {
            for (int i = 0; i < this.visibleChildCount && i < this.visibleChildren.size(); i++) {
               Setting setting = (Setting)this.visibleChildren.get(i);
               if (setting.isVisible() && setting.isCachedVisible() && (setting.isVisualOnly() || !this.getParentModule().isLoadFromConfig() || Augustus.getInstance().isConfigEditingEnabled())) {
                  if (renderedVisible > 0) {
                     measuredBodyH += ModernSettingsPanel.CARD_GAP;
                  }
                  int childY = n4 + headerH + measuredBodyH;
                  boolean childVisible = flag && childY + setting.getHeight() > -20 && childY < n6 + 20;
                  setting.render(n, n2, childX, childY, n5, n6, customFontUtil, childVisible);
                  measuredBodyH += setting.getHeight();
                  int cw = setting.getContentWidth() + (nested ? 14 : 0);
                  if (this.maxChildWidth < cw) {
                     this.maxChildWidth = cw;
                  }
                  renderedVisible++;
               }
            }

            if (renderedVisible > 0) {
               if (nested && flag) {
                  // Close transition: this separator is drawn by the settings tree itself, so it has to
                  // carry the shared envelope - the glass primitives and the font renderer both reset
                  // the GL colour per call, so it cannot be applied globally.
                  GlassRenderer.drawSeparator(
                     (float)(n3 + 4),
                     (float)(n4 + headerH + 4),
                     (float)(n3 + 4),
                     (float)(n4 + headerH + measuredBodyH - 2),
                     1.75F,
                     ClickGuiScreen.fadeArgb(UiTheme.withAlpha(UiTheme.accentColor(), 135))
                  );
               }
               measuredBodyH += 4;
            }
         } finally {
            GL11.glPopMatrix();
            if (pushedScissor) {
               GlassRenderer.popScissor();
            }
         }

         this.renderedHeight = headerH + Math.round((float)measuredBodyH * easedExpand);
      }
   }

   private boolean matchesSubGroupName(Setting setting) {
      return setting.getName().equals(this.getName());
   }

   public SettingGroup(Module module, String str, boolean flag, SettingGroup doubleValue) {
      super(module, doubleValue, str + "Groupygroupy");
      this.children = new ArrayList();
      this.renderedHeight = this.getHeaderHeight();
      this.maxChildWidth = 0;
      this.b = new BooleanProperty(this, "B", false);
      this.labelWidth = -1.0F;
      this.visibleChildren = new ArrayList();
      this.visibleChildCount = 0;
      this.withDisplayName(str);
      this.setExpanded(flag);
      BooleanProperty booleanProperty = this.b;
      Boolean bool = flag;
      booleanProperty.setDefaultValue(bool);
      if (module != null && !(module instanceof Dummy)) {
         ClientManagers.settingManager.registerSetting(this);
         doubleValue.getChildren().removeIf(obj -> this.matchesNestedGroupName((Setting)obj));
         doubleValue.getChildren().add(this);
      }
   }

   public ArrayList getChildren() {
      return this.children;
   }

   private boolean matchesNestedGroupName(Setting setting) {
      return setting.getName().equals(this.getName());
   }

   @Override
   public int getContentWidth() {
      return this.maxChildWidth;
   }

   @Override
   public void updateVisibility() {
      super.updateVisibility();
      this.visibleChildCount = 0;

      for (Setting setting : (Iterable<Setting>)(this.children)) {
         setting.setCachedVisible(setting.isVisible());
         setting.setRenderFont(this.renderFont);
         if (setting.isVisible() && setting.isCachedVisible() && (setting.isVisualOnly() || !this.getParentModule().isLoadFromConfig() || Augustus.getInstance().isConfigEditingEnabled())) {
            if (this.visibleChildren.size() <= this.visibleChildCount) {
               this.visibleChildren.add(setting);
            } else {
               this.visibleChildren.set(this.visibleChildCount, setting);
            }

            this.visibleChildCount++;
         }
      }

      for (Setting setting2 : (Iterable<Setting>)(this.children)) {
         setting2.updateVisibility();
      }
   }
}
