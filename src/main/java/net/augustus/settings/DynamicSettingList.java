package net.augustus.settings;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.augustus.ClientManagers;
import net.augustus.clickgui.ClickGuiScreen;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.ui.modern.font.InterFontUtil;
import net.augustus.modules.Module;
import net.augustus.modules.misc.Dummy;
import net.augustus.render.RenderUtil;
import net.augustus.settings.property.IndexedSettingFactory;
import net.augustus.settings.property.SettingGroupListProperty;
import net.augustus.ui.modern.components.ModernButton;
import net.augustus.ui.modern.components.ModernSettingsPanel;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiTheme;

public class DynamicSettingList extends Setting {
   public int hoveredRemoveIndex;
   public static final Color REMOVE_HOVER_COLOR = new Color(200, 100, 100);
   public static final Color REMOVE_COLOR = new Color(180, 80, 80);
   public boolean addButtonHovered;
   public int labelWidth;
   public final int maxEntries;
   public static final Color ADD_COLOR = new Color(80, 180, 80);
   public final IndexedSettingFactory entryFactory;
   public final SettingGroupListProperty entriesProperty;
   private int cycleIndex;
   public static final Color ADD_HOVER_COLOR = new Color(100, 200, 100);
   public final int minEntries;
   public int renderedHeight;
   public int nextEntryId;
   public static final CustomFontUtil BUTTON_FONT = FontManager.consolas40;

   /**
    * Applies the ClickGUI open/close envelope to one ARGB at the point of draw.
    *
    * <p>WHY this list needs its own fade: the child rows and the Add/Remove buttons fade themselves, but the
    * list header label and its horizontal divider are painted here, so they would otherwise stay bright over
    * the fading rows. Only alpha is scaled; the measured header height, the row offsets and the scroll maths
    * are untouched.
    *
    * <p>Each colour is wrapped exactly once, at the draw.
    */
   private static int faded(int argb) {
      // Settled overlay (everything except the ~288 ms open/close transition): skip the arithmetic.
      return ClickGuiScreen.getScreenFade() >= 0.999F ? argb : ClickGuiScreen.fadeArgb(argb);
   }
   private final ModernButton addButton = new ModernButton();
   private final List<ModernButton> removeButtons = new ArrayList<>();

   public int getEntryCount() {
      return ((List)this.entriesProperty.getValue()).size();
   }

   public Setting nextEntry() {
      this.cycleIndex++;
      if (this.cycleIndex >= this.getEntryCount()) {
         this.cycleIndex = 0;
      }

      return this.getEntry(this.cycleIndex);
   }

   @Override
   public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
      super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);

      for (Setting setting : (Iterable<Setting>)((List)this.entriesProperty.getValue())) {
         if (setting.isVisible()) {
            setting.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
         }
      }
   }

   public boolean canAddEntry() {
      return this.getEntryCount() < this.maxEntries;
   }

   public DynamicSettingList withInitialCount(int initialCount) {
      int n2 = Math.max(this.minEntries, Math.min(this.maxEntries, initialCount));
      List value = (List)this.entriesProperty.getValue();

      for (int i = 0; i < n2; i++) {
         int nextEntryId2 = this.nextEntryId;
         Setting setting = this.entryFactory.createSetting(nextEntryId2);
         value.add(setting);
         this.updateEntryIndexLabel(setting, i);
         this.nextEntryId++;
      }

      this.entriesProperty.setDefaultValue(value);
      return this;
   }

   @Override
   public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      super.mouseClicked(mouseX, mouseY, mouseButton);
      if (mouseButton == 0 && this.addButtonHovered && this.canAddEntry()) {
         this.addEntry();
         return true;
      } else if (mouseButton == 0 && this.hoveredRemoveIndex >= 0 && this.canRemoveEntry()) {
         this.removeEntry(this.hoveredRemoveIndex);
         return true;
      } else {
         int n = (int)(this.renderFont.getFontHeight() + 5.0F);
         List value = (List)this.entriesProperty.getValue();

         for (int i = 0; i < value.size(); i++) {
            Setting setting = (Setting)value.get(i);
            if (setting.isVisible()) {
               if (setting.mouseClicked(mouseX, mouseY, mouseButton)) {
                  return true;
               }

               n += setting.getHeight();
            }
         }

         return false;
      }
   }

   public DynamicSettingList(Module module, String str, IndexedSettingFactory indexedSettingFactory, int n, int n2) {
       super(module, null, str);
      this.renderedHeight = 32;
      this.labelWidth = -1;
      this.hoveredRemoveIndex = -1;
      this.addButtonHovered = false;
      this.nextEntryId = 0;
      this.cycleIndex = 0;
      this.entryFactory = n3 -> {
         Setting setting = indexedSettingFactory.createSetting(n3);
         if (!setting.getName().contains("_")) {
         }

         ClientManagers.settingManager.getSettings().removeIf(obj -> obj == setting);
         if (module != null && !(module instanceof Dummy)) {
            module.getSettings().removeIf(obj2 -> obj2 == setting);
         }

         if (setting.getParentGroup() != null) {
            setting.getParentGroup().getChildren().removeIf(obj3 -> obj3 == setting);
         }

         if (setting instanceof SettingGroup) {
            this.unregisterGroupChildren((SettingGroup)setting);
         }

         if (module != null && module.getProperties() != null) {
            module.getProperties().removeIf(obj4 -> obj4 == setting);
         }

         return setting;
      };
      this.minEntries = n;
      this.maxEntries = n2;
      this.entriesProperty = new SettingGroupListProperty(this, "L", new ArrayList(), this.entryFactory);
   }

   @Override
   public void resetToDefault() {
      this.entriesProperty.resetToDefault();
   }

   public boolean canRemoveEntry() {
      return this.getEntryCount() > this.minEntries;
   }

   @Override
   public void updateVisibility() {
      super.updateVisibility();

      for (Setting setting : (Iterable<Setting>)((List)this.entriesProperty.getValue())) {
         setting.updateVisibility();
      }
   }

   private void unregisterGroupChildren(SettingGroup doubleValue) {

      for (Setting setting : (Iterable<Setting>)(doubleValue.getChildren())) {
         ClientManagers.settingManager.getSettings().removeIf(obj -> obj == setting);
         if (this.getParentModule() != null && this.getParentModule().getProperties() != null) {
            this.getParentModule().getProperties().removeIf(obj2 -> obj2 == setting);
         }

         if (setting instanceof SettingGroup) {
            this.unregisterGroupChildren((SettingGroup)setting);
         }
      }
   }

   public void updateEntryIndexLabel(Setting setting, int n) {
      String displayName2 = setting.getDisplayName();
      if (displayName2 != null) {
         int n2 = displayName2.indexOf(95);
         String str;
         if (n2 <= 0) {
            str = displayName2.replaceAll("\\s*\\d+$", "").trim();
         } else {
            str = displayName2.substring(0, n2);
         }

         setting.updateDisplayName(str + " " + n);
      }

      if (setting instanceof SettingGroup) {
         for (Setting setting2 : (Iterable<Setting>)(((SettingGroup)setting).getChildren())) {
            this.updateEntryIndexLabel(setting2, n);
         }
      }
   }

   @Override
   public int getHeight() {
      return this.renderedHeight;
   }

   public SettingGroupListProperty getEntriesProperty() {
      return this.entriesProperty;
   }

   @Override
   public void render(int n, int n2, int n3, int n4, int n5, int n6, CustomFontUtil customFontUtil, boolean flag) {
      // Same 12px upper-case divider type as the plain section titles and the collapsible group
      // headers (ModernSettingsPanel.drawSectionTitle / drawCollapsibleSectionHeader): this is another
      // divider label, so it must not be the one heading rendered at three times their size.
      InterFontUtil headerFace = FontManager.interSection;
      super.render(n, n2, n3, n4, n5, n6, FontManager.interMedium, flag);
      String str = ModernSettingsPanel.formatUiLabel(this.getFormattedDisplayName()).toUpperCase(Locale.ROOT);
      boolean flag2 = this.labelWidth == -1;
      if (this.consumeLabelDirty(flag2)) {
         this.labelWidth = (int)headerFace.getStringWidthSpaced(str, 1.1F);
      }

      float cardW = ModernSettingsPanel.resolveCardWidth(n3, n5);
      int headerH = 24;
      if (flag) {
         headerFace.drawStringSpaced(str, n3 + 2, n4 + (headerH - headerFace.getFontHeight()) * 0.5F, 1.1F, faded(UiTheme.mutedTextColor()));
         float addBtnW = 68.0F;
         float addX = (float)n3 + cardW - addBtnW;
         float addY = (float)n4 + (float)(headerH - 22) * 0.5F;
         float lineStart = (float)n3 + this.labelWidth + 14.0F;
         float lineEnd = (this.canAddEntry() ? addX : (float)n3 + cardW) - 10.0F;
         if (lineEnd > lineStart + 12.0F) {
            GlassRenderer.drawSeparator(lineStart, (float)n4 + (float)headerH * 0.5F, lineEnd, (float)n4 + (float)headerH * 0.5F, UiTheme.SEPARATOR_THICKNESS, faded(UiTheme.GLASS_DIVIDER));
         }

         this.addButtonHovered = false;
         if (this.canAddEntry()) {
            this.addButton.render(addX, addY, addBtnW, 22.0F, "Add", LucideIcon.PLUS, ModernButton.Variant.ACTIVE_GREEN, n, n2);
            this.addButtonHovered = this.addButton.isHovered(n, n2);
         }
      }

      this.renderedHeight = headerH + 6;
      this.hoveredRemoveIndex = -1;
      this.contentWidth = (int)Math.ceil((double)cardW);
      List value = (List)this.entriesProperty.getValue();
      while (this.removeButtons.size() < value.size()) {
         this.removeButtons.add(new ModernButton());
      }

      int visibleIdx = 0;
      for (int i = 0; i < value.size(); i++) {
         Setting setting = (Setting)value.get(i);
         if (setting.isVisible()) {
            if (visibleIdx > 0) {
               this.renderedHeight += ModernSettingsPanel.CARD_GAP;
            }
            int childY = n4 + this.renderedHeight;
            int childX = n3 + 28;
            boolean childVisible = flag && childY + setting.getHeight() > -20 && childY < n6 + 20;
            setting.render(n, n2, childX, childY, n5, n6, FontManager.interMedium, childVisible);
            if (childVisible) {
               ModernButton remBtn = this.removeButtons.get(i);
               float remY = (float)childY + (float)(Math.min(42, setting.getHeight()) - 22) * 0.5F;
               if (this.canRemoveEntry()) {
                  remBtn.render((float)n3, remY, 22.0F, 22.0F, null, LucideIcon.MINUS, ModernButton.Variant.DANGER, n, n2);
                  if (remBtn.isHovered(n, n2)) {
                     this.hoveredRemoveIndex = i;
                  }
               } else {
                  remBtn.render((float)n3, remY, 22.0F, 22.0F, null, LucideIcon.MINUS, ModernButton.Variant.GLASS, -9999, -9999);
               }
            }

            this.renderedHeight += setting.getHeight();
            this.contentWidth = Math.max(this.contentWidth, setting.getContentWidth() + 28);
            visibleIdx++;
         }
      }

      if (flag) {
         for (Setting setting2 : (Iterable<Setting>)(value)) {
            if (setting2.isVisible()) {
               setting2.renderTooltip();
            }
         }
      }
   }

   public List getEntries() {
      return (List)this.entriesProperty.getValue();
   }

   public void removeEntry(int n) {
      if (this.canRemoveEntry() && n >= 0 && n < this.getEntryCount()) {
         ((List)this.entriesProperty.getValue()).remove(n);
         this.refreshEntryLabels();
      }
   }

   @Override
   public void keyTyped(char typedChar, int keyCode) throws IOException {
      super.keyTyped(typedChar, keyCode);

      for (Setting setting : (Iterable<Setting>)((List)this.entriesProperty.getValue())) {
         if (setting.isVisible()) {
            setting.keyTyped(typedChar, keyCode);
         }
      }
   }

   public void reindexEntryLabels() {
      List value = (List)this.entriesProperty.getValue();

      for (int i = 0; i < value.size(); i++) {
         Setting setting = (Setting)value.get(i);
         if (setting != null) {
            this.updateEntryIndexLabel(setting, i);
         }
      }
   }

   @Override
   public void mouseReleased(int mouseX, int mouseY, int state) {
      super.mouseReleased(mouseX, mouseY, state);

      for (Setting setting : (Iterable<Setting>)((List)this.entriesProperty.getValue())) {
         if (setting.isVisible()) {
            setting.mouseReleased(mouseX, mouseY, state);
         }
      }
   }

   public Setting getEntry(int n) {
      List value = (List)this.entriesProperty.getValue();
      return n >= 0 && n < value.size() ? (Setting)value.get(n) : null;
   }

   private void addEntry() {
      if (this.canAddEntry()) {
         List value = (List)this.entriesProperty.getValue();
         int nextEntryId2 = this.nextEntryId;
         Setting setting = this.entryFactory.createSetting(nextEntryId2);
         value.add(setting);
         this.updateEntryIndexLabel(setting, value.size() - 1);
         this.nextEntryId++;
      }
   }

   private void refreshEntryLabels() {
      this.reindexEntryLabels();
   }

   public void setNextEntryId(int n) {
      this.nextEntryId = n;
   }

   @Override
   public void renderTooltip() {
      this.drawDescriptionTooltip(this.labelWidth);
   }

}
