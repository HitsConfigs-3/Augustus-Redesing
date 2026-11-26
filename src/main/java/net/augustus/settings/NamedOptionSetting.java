package net.augustus.settings;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.modules.Module;
import net.augustus.settings.property.StringProperty;
import net.augustus.ui.modern.components.ModernModeSelector;
import net.augustus.ui.modern.components.ModernSettingsPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketThreadUtil;

public class NamedOptionSetting extends Setting {
   private EnumSettingOption selectedOption;
   private final ConcurrentHashMap optionWidthCache;
   private static final int SEPARATOR_WIDTH;
   private int labelWidth;
   private int renderedHeight = ModernSettingsPanel.MIN_CARD_HEIGHT;
   private final Map optionsByName;
   private final StringProperty s;
   private EnumSettingOption[] options;
   private final ModernModeSelector modeSelector = new ModernModeSelector();

   private EnumSettingOption findOptionByName(String str) {
      return (EnumSettingOption)this.optionsByName.get(str);
   }

   private void selectOptionByName(String str) {
      this.setSelectedOption(this.findOptionByName(str));
   }

   public NamedOptionSetting(Module module, String str, EnumSettingOption enumSettingOption, EnumSettingOption[] enumSettingOptions) {
      this(str, module, null, enumSettingOption, enumSettingOptions);
   }

   private void updateLabelWidth() {
      if (this.consumeLabelDirty(this.labelWidth == -1)) {
         this.labelWidth = (int)FontManager.interSemiBold.getStringWidth(this.getFormattedDisplayName());
      }
   }

   private String[] getOptionNamesArray() {
      if (this.options == null) {
         return new String[0];
      }
      String[] names = new String[this.options.length];
      for (int i = 0; i < this.options.length; i++) {
         names[i] = this.options[i] != null ? this.options[i].getDisplayName() : "";
      }
      return names;
   }

   private NamedOptionSetting(String str, Module module, SettingGroup doubleValue, EnumSettingOption enumSettingOption, EnumSettingOption[] enumSettingOptions) {
      super(module, doubleValue, str);
      this.optionsByName = new HashMap();
      this.s = (StringProperty)((StringProperty)new StringProperty(this, "S", "").withOnValueChanged(obj -> this.selectOptionByName((String)obj))).withValueSupplier(() -> this.selectedOption.getDisplayName());
      this.renderedHeight = ModernSettingsPanel.MIN_CARD_HEIGHT;
      this.labelWidth = -1;
      this.optionWidthCache = new ConcurrentHashMap();
      this.selectedOption = enumSettingOption;
      StringProperty stringProperty3 = this.s;
      stringProperty3.setValue(enumSettingOption.getDisplayName());
      stringProperty3 = this.s;
      stringProperty3.setDefaultValue(enumSettingOption.getDisplayName());
      this.setOptions(enumSettingOptions);
   }

   private void applyOptionsOnMainThread(EnumSettingOption[] enumSettingOptions) {
      this.applyOptions(enumSettingOptions);
   }

   @Override
   public void render(int n, int n2, int n3, int n4, int n5, int n6, CustomFontUtil customFontUtil, boolean flag) {
      customFontUtil = FontManager.interMedium;
      super.render(n, n2, n3, n4, n5, n6, customFontUtil, flag);
      this.updateLabelWidth();

      String label = this.getFormattedDisplayName();
      float cardW = ModernSettingsPanel.resolveCardWidth(n3, n5);
      String[] names = this.getOptionNamesArray();
      String selectedName = this.selectedOption != null ? this.selectedOption.getDisplayName() : "";

      float groupW = this.modeSelector.measureSingleRowWidth(names);
      float maxSingleRowW = Math.min(440.0F, cardW * 0.50F);
      if (groupW > 0.0F && groupW <= maxSingleRowW && names.length <= 5) {
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
         this.modeSelector.renderConnectedSingle(m.controlAreaRightX - groupW, m.controlCenterY - 12.0F, 24.0F, names, selectedName, flag, n, n2);
      } else {
         float bodyW = Math.max(160.0F, cardW - ModernSettingsPanel.CARD_PAD_X * 2.0F);
         int bodyH = this.modeSelector.measureWrappedHeight(bodyW, names);
         ModernSettingsPanel.CardMetrics m = ModernSettingsPanel.drawStackedCard(
            n3,
            n4,
            n5,
            label,
            this.getDescription(),
            this.shouldShowInlineDescription(),
            selectedName,
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
            idx -> idx >= 0 && idx < names.length && names[idx].equals(selectedName),
            flag,
            n,
            n2
         );
      }
   }

   public int getSelectedIndex() {
      return Arrays.asList(this.options).indexOf(this.getSelectedOption());
   }

   @Override
   public void renderTooltip() {
      this.drawDescriptionTooltip(this.labelWidth);
   }

   public EnumSettingOption[] getOptions() {
      return this.options;
   }

   @Override
   public void resetToDefault() {
      this.setSelectedOption(this.findOptionByName((String)this.s.getDefaultValue()));
   }

   @Override
   public void randomize() {
      if (!this.getDisplayName().contains("Point selection mode") && this.options != null && this.options.length > 0) {
         this.setSelectedOption(this.options[ThreadLocalRandom.current().nextInt(this.options.length)]);
      }
   }

   @Override
   public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      if ((mouseButton == 0 || mouseButton == 1) && this.options != null) {
         int hit = this.modeSelector.hitTest(mouseX, mouseY);
         if (hit >= 0 && hit < this.options.length && this.options[hit] != null) {
            if (!this.options[hit].equals(this.selectedOption)) {
               this.notifyUserChanged();
            }
            this.setSelectedOption(this.options[hit]);
            return true;
         }
      }
      return false;
   }

   @Override
   public int getHeight() {
      return this.renderedHeight;
   }

   public EnumSettingOption getSelectedOption() {
      return this.selectedOption;
   }

   public NamedOptionSetting(Module module, String str, EnumSettingOption enumSettingOption, EnumSettingOption[] enumSettingOptions, SettingGroup doubleValue) {
      this(str, module, doubleValue, enumSettingOption, enumSettingOptions);

      for (EnumSettingOption enumSettingOption2 : enumSettingOptions) {
         this.optionsByName.put(enumSettingOption2.getDisplayName(), enumSettingOption2);
      }
   }

   private void applyOptions(EnumSettingOption[] enumSettingOptions) {
      this.options = enumSettingOptions;
      this.optionWidthCache.clear();

      for (EnumSettingOption enumSettingOption : enumSettingOptions) {
         if (enumSettingOption != null) {
            String displayName2 = enumSettingOption.getDisplayName();
            if (displayName2 != null) {
               this.optionWidthCache.put(displayName2, (int)FontManager.consolas30Booleans.getStringWidth(displayName2));
            }
         }
      }
   }

   static {
      String str = ", ";
      SEPARATOR_WIDTH = (int)FontManager.consolas30Booleans.getStringWidth(str);
   }

   public void setSelectedOption(EnumSettingOption enumSettingOption) {
      if (enumSettingOption != null) {
         if (!enumSettingOption.equals(this.getSelectedOption())) {
            this.notifyValueChanged();
         }

         this.selectedOption = enumSettingOption;
         this.s.setValue(enumSettingOption.getDisplayName());
      }
   }

   public void setOptions(EnumSettingOption[] enumSettingOptions) {
      if (Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
         this.applyOptions(enumSettingOptions);
      } else {
         PacketThreadUtil.runOnMainThreadImmediate(() -> this.applyOptionsOnMainThread(enumSettingOptions));
      }
   }
}
