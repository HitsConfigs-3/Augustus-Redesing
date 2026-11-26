package net.augustus.settings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.modules.Module;
import net.augustus.modules.ModuleToggleListener;
import net.augustus.modules.modes.AbstractSubMode;
import net.augustus.settings.property.BooleanProperty;
import net.augustus.ui.modern.components.ModernModeSelector;
import net.augustus.ui.modern.components.ModernSettingsPanel;

public class MultiModeSetting extends Setting implements ModuleToggleListener {
   private final List modeProperties;
   private int dragToggleState;
   private final ConcurrentHashMap modeWidthCache;
   private final boolean[] enabledStates;
   private int labelWidth;
   private static final int SEPARATOR_WIDTH;
   private final Module ownerModule;
   private int renderedHeight = ModernSettingsPanel.MIN_CARD_HEIGHT;
   private final List subModes;
   private final ModernModeSelector modeSelector = new ModernModeSelector();

   @Override
   public int getHeight() {
      return this.renderedHeight;
   }

   public MultiModeSetting(Module module, String str, BiFunction[] biFunctions) {
      super(module, null, str);
      this.modeProperties = new ArrayList();
      this.subModes = new ArrayList();
      this.labelWidth = -1;
      this.modeWidthCache = new ConcurrentHashMap();
      this.dragToggleState = 0;
      this.renderedHeight = ModernSettingsPanel.MIN_CARD_HEIGHT;
      this.ownerModule = module;
      this.enabledStates = new boolean[biFunctions.length];

      for (int i = 0; i < biFunctions.length; i++) {
         int n = i;
         String name = ((AbstractSubMode)biFunctions[i].apply(null, null)).getName();
         SettingGroup settingGroup = (SettingGroup)(new SettingGroup(module, str + name + "Group_MCSV", true)).withDisplayName(name);
         settingGroup.visibleWhen(() -> this.isSubModeGroupVisible(n, settingGroup));
         AbstractSubMode subMode = (AbstractSubMode)biFunctions[i].apply(module, settingGroup);
         subMode.setVisibilitySupplier(() -> this.enabledStates[n]);
         this.subModes.add(subMode);
         List modeProperties2 = this.modeProperties;
         BooleanProperty booleanProperty2 = (BooleanProperty)new BooleanProperty(this, subMode.getName(), false).withValueSupplier(() -> this.enabledStates[n]);
         Consumer consumer = obj -> {
            this.enabledStates[n] = (Boolean)(obj);
            if (this.ownerModule.isToggled()) {
               boolean flag = (Boolean)(obj);
               subMode.setEnabled(flag);
            }
         };
         modeProperties2.add(booleanProperty2.withOnValueApplied(consumer));
      }

      module.getToggleListeners().add(this);
   }

   public MultiModeSetting(Module module, String str, SettingGroup doubleValue, BiFunction[] biFunctions) {
      super(module, doubleValue, str);
      this.modeProperties = new ArrayList();
      this.subModes = new ArrayList();
      this.labelWidth = -1;
      this.modeWidthCache = new ConcurrentHashMap();
      this.dragToggleState = 0;
      this.renderedHeight = ModernSettingsPanel.MIN_CARD_HEIGHT;
      this.ownerModule = module;
      this.enabledStates = new boolean[biFunctions.length];

      for (int i = 0; i < biFunctions.length; i++) {
         int n = i;
         String name = ((AbstractSubMode)biFunctions[i].apply(null, null)).getName();
         SettingGroup settingGroup = (SettingGroup)(new SettingGroup(module, str + name + "Group_MCSV", true)).withDisplayName(name);
         settingGroup.visibleWhen(() -> this.isNestedSubModeGroupVisible(n, settingGroup));
         AbstractSubMode subMode = (AbstractSubMode)biFunctions[i].apply(module, settingGroup);
         subMode.setVisibilitySupplier(() -> this.enabledStates[n]);
         this.subModes.add(subMode);
         List modeProperties2 = this.modeProperties;
         BooleanProperty booleanProperty2 = (BooleanProperty)new BooleanProperty(this, subMode.getName(), false).withValueSupplier(() -> this.enabledStates[n]);
         Consumer consumer = obj -> {
            this.enabledStates[n] = (Boolean)(obj);
            if (this.ownerModule.isToggled()) {
               boolean flag = (Boolean)(obj);
               subMode.setEnabled(flag);
            }
         };
         modeProperties2.add(booleanProperty2.withOnValueApplied(consumer));
      }

      module.getToggleListeners().add(this);
   }

   @Override
   public void renderTooltip() {
      this.drawDescriptionTooltip(this.labelWidth);
   }

   private String[] getModeNamesArray() {
      String[] names = new String[this.subModes.size()];
      for (int i = 0; i < this.subModes.size(); i++) {
         names[i] = ((AbstractSubMode)this.subModes.get(i)).getName();
      }
      return names;
   }

   private int countEnabledModes() {
      int count = 0;
      for (boolean b : this.enabledStates) {
         if (b) {
            count++;
         }
      }
      return count;
   }

   @Override
   public void render(int n, int n2, int n3, int n4, int n5, int n6, CustomFontUtil customFontUtil, boolean flag) {
      customFontUtil = FontManager.interMedium;
      super.render(n, n2, n3, n4, n5, n6, customFontUtil, flag);
      String label = this.getFormattedDisplayName();
      if (this.consumeLabelDirty(this.labelWidth == -1)) {
         this.labelWidth = (int)customFontUtil.getStringWidth(label);
      }

      float cardW = ModernSettingsPanel.resolveCardWidth(n3, n5);
      String[] names = this.getModeNamesArray();
      float groupW = this.modeSelector.measureSingleRowWidth(names);
      float maxSingleRowW = Math.min(430.0F, cardW * 0.50F);

      if (groupW > 0.0F && groupW <= maxSingleRowW && names.length <= 4) {
         ModernSettingsPanel.CardMetrics m = ModernSettingsPanel.drawSettingCard(
            n3,
            n4,
            n5,
            label,
            this.getDescription(),
            this.shouldShowInlineDescription(),
            groupW,
            false,
            false,
            flag,
            n,
            n2
         );
         this.renderedHeight = m.cardH;
         this.applyCardMetrics(m);
         this.modeSelector.renderConnectedSegments(
            m.controlAreaRightX - groupW,
            m.controlCenterY - 12.0F,
            24.0F,
            names,
            idx -> idx >= 0 && idx < this.enabledStates.length && this.enabledStates[idx],
            flag,
            n,
            n2
         );
      } else {
         float bodyW = Math.max(160.0F, cardW - ModernSettingsPanel.CARD_PAD_X * 2.0F);
         int bodyH = this.modeSelector.measureWrappedHeight(bodyW, names);
         int activeCount = this.countEnabledModes();
         String badge = activeCount > 0 ? activeCount + " enabled" : "None active";
         ModernSettingsPanel.CardMetrics m = ModernSettingsPanel.drawStackedCard(
            n3,
            n4,
            n5,
            label,
            this.getDescription(),
            this.shouldShowInlineDescription(),
            badge,
            bodyH,
            false,
            false,
            flag,
            n,
            n2
         );
         this.renderedHeight = m.cardH;
         this.applyCardMetrics(m);
         this.modeSelector.renderSegments(
            m.cardX + ModernSettingsPanel.CARD_PAD_X,
            m.controlCenterY,
            bodyW,
            names,
            idx -> idx >= 0 && idx < this.enabledStates.length && this.enabledStates[idx],
            flag,
            n,
            n2
         );
      }
   }

   private boolean isNestedSubModeGroupVisible(int n, SettingGroup doubleValue) {
      return this.enabledStates[n] && doubleValue.hasVisibleChildren();
   }

   @Override
   public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      super.mouseClicked(mouseX, mouseY, mouseButton);
      if (mouseButton == 0 || mouseButton == 1) {
         int hit = this.modeSelector.hitTest(mouseX, mouseY);
         if (hit >= 0 && hit < this.subModes.size()) {
            this.dragToggleState = this.enabledStates[hit] ? 1 : 2;
            boolean next = !this.enabledStates[hit];
            this.enabledStates[hit] = next;
            if (this.ownerModule.isToggled()) {
               ((AbstractSubMode)this.subModes.get(hit)).setEnabled(next);
            }
            this.notifyValueChanged();
            this.notifyUserChanged();
            return true;
         }
      }
      return false;
   }

   @Override
   public Setting withDisplayName(String str) {
      return this.named(str);
   }

   @Override
   public void onModuleToggled(boolean flag) {
      for (int i = 0; i < this.subModes.size(); i++) {
         if (this.enabledStates[i]) {
            ((AbstractSubMode)this.subModes.get(i)).setEnabled(flag);
         }
      }
   }

   public MultiModeSetting named(String str) {
      this.setDisplayName(str);
      return this;
   }

   public boolean[] getEnabledStates() {
      return this.enabledStates;
   }

   @Override
   public void resetToDefault() {
      for (BooleanProperty booleanProperty : (Iterable<BooleanProperty>)(this.modeProperties)) {
         booleanProperty.resetToDefault();
      }
   }

   static {
      String str = ", ";
      SEPARATOR_WIDTH = (int)FontManager.consolas30.getStringWidth(str);
   }

   private boolean isSubModeGroupVisible(int n, SettingGroup doubleValue) {
      return this.enabledStates[n] && doubleValue.hasVisibleChildren();
   }

   @Override
   public Setting withVisibility(SettingVisibilityCondition condition) {
      return this.visibleWhen(condition);
   }

   public List getSubModes() {
      return this.subModes;
   }

   @Override
   public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
      if (this.dragToggleState != 0 && (clickedMouseButton == 0 || clickedMouseButton == 1)) {
         int hit = this.modeSelector.hitTest(mouseX, mouseY);
         if (hit >= 0 && hit < this.subModes.size() && this.enabledStates[hit] == (this.dragToggleState == 1)) {
            boolean flag = !this.enabledStates[hit];
            this.enabledStates[hit] = flag;
            if (this.ownerModule.isToggled()) {
               ((AbstractSubMode)this.subModes.get(hit)).setEnabled(flag);
            }
            this.notifyValueChanged();
            this.notifyUserChanged();
         }
      }
   }

   @Override
   public void mouseReleased(int mouseX, int mouseY, int state) {
      this.dragToggleState = 0;
   }

   public MultiModeSetting visibleWhen(SettingVisibilityCondition condition) {
      this.visibilityCondition = condition;
      return this;
   }
}
