package net.augustus.settings;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.augustus.clickgui.MainClickGuiWindow;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.modules.Module;
import net.augustus.settings.property.StringProperty;
import net.augustus.ui.modern.components.ModernDropdown;
import net.augustus.ui.modern.components.ModernModeSelector;
import net.augustus.ui.modern.components.ModernSettingsPanel;
import net.augustus.ui.modern.popup.DropdownFloatingPopup;
import net.augustus.ui.modern.popup.PopupHost;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketThreadUtil;

public class StringValue extends Setting {
   private final ConcurrentHashMap optionWidthCache;
   private int labelWidth;
   private final StringProperty s;
   private static final int SEPARATOR_WIDTH;
   private int renderedHeight = ModernSettingsPanel.MIN_CARD_HEIGHT;
   private String[] options;
   private final ModernModeSelector modeSelector = new ModernModeSelector();
   private final ModernDropdown dropdown = new ModernDropdown();
   private float lastControlX;
   private float lastControlBottomY;
   private float lastControlW = 240.0F;
   private boolean lastUsedDropdown = false;
   private final DropdownFloatingPopup.Source dropdownSource = new DropdownFloatingPopup.Source() {
      @Override
      public List<String> getOptions() {
         return StringValue.this.options != null ? Arrays.asList(StringValue.this.options) : Arrays.asList();
      }

      @Override
      public boolean isSelected(int index) {
         return StringValue.this.options != null
            && index >= 0
            && index < StringValue.this.options.length
            && StringValue.this.options[index] != null
            && StringValue.this.options[index].equals(StringValue.this.getSelectedValue());
      }

      @Override
      public boolean select(int index) {
         if (StringValue.this.options != null && index >= 0 && index < StringValue.this.options.length && StringValue.this.options[index] != null) {
            if (!StringValue.this.options[index].equals(StringValue.this.getSelectedValue())) {
               StringValue.this.notifyUserChanged();
            }
            StringValue.this.setSelectedValue(StringValue.this.options[index]);
         }
         return true;
      }

      @Override
      public boolean isMultiSelect() {
         return false;
      }
   };

   public String getSelectedValue() {
      return (String)this.s.getValue();
   }

   @Override
   public int getHeight() {
      return this.renderedHeight;
   }

   public StringValue(Module module, String str, String str2, String[] strings, SettingGroup doubleValue) {
      this(str, module, doubleValue, str2, strings);
   }

   public void setOptions(String[] strings) {
      if (Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
         this.applyOptions(strings);
      } else {
         PacketThreadUtil.runOnMainThreadImmediate(() -> this.applyOptions(strings));
      }
   }

   private void updateLabelWidth() {
      if (this.consumeLabelDirty(this.labelWidth == -1)) {
         this.labelWidth = (int)FontManager.interSemiBold.getStringWidth(this.getFormattedDisplayName());
      }
   }

   public StringValue(Module module, String str, String str2, String[] strings) {
      this(str, module, null, str2, strings);
   }

   @Override
   public void resetToDefault() {
      this.setSelectedValue((String)this.s.getDefaultValue());
   }

   public void setSelectedValue(String selectedValue) {
      if (!selectedValue.equals(this.getSelectedValue())) {
         this.notifyValueChanged();
      }

      this.s.setValue(selectedValue);
   }

   @Override
   public void render(int n, int n2, int n3, int n4, int n5, int n6, CustomFontUtil customFontUtil, boolean flag) {
      customFontUtil = FontManager.interMedium;
      super.render(n, n2, n3, n4, n5, n6, customFontUtil, flag);
      this.updateLabelWidth();

      String label = this.getFormattedDisplayName();
      float cardW = ModernSettingsPanel.resolveCardWidth(n3, n5);
      this.lastUsedDropdown = this.options != null && this.options.length > 6;

      PopupHost host = MainClickGuiWindow.activePopupHost();
      MainClickGuiWindow mw = MainClickGuiWindow.activeMainWindow();
      float ox = mw != null ? (float)mw.getSettingsOffsetX() : 0.0F;
      float oy = mw != null ? (float)mw.getSettingsOffsetY() : 0.0F;

      if (this.lastUsedDropdown) {
         float dropW = Math.min(250.0F, Math.max(175.0F, cardW * 0.29F));
         boolean isOpen = host != null && host.isOpenFor(this.dropdown);
         ModernSettingsPanel.CardMetrics m = ModernSettingsPanel.drawSettingCard(
            n3,
            n4,
            n5,
            label,
            this.getDescription(),
            this.shouldShowInlineDescription(),
            dropW,
            false,
            isOpen,
            flag,
            n,
            n2
         );
         this.renderedHeight = m.cardH;
         this.applyCardMetrics(m);

         float dropX = m.controlAreaRightX - dropW;
         float dropY = m.controlCenterY - 12.0F;
         this.lastControlX = dropX;
         this.lastControlBottomY = dropY + 24.0F;
         this.lastControlW = dropW;

         if (flag) {
            this.dropdown.render(
               dropX,
               dropY,
               dropW,
               24.0F,
               this.getSelectedValue(),
               n,
               n2,
               host,
               this.dropdownSource,
               dropX + ox,
               this.lastControlBottomY + oy
            );
         }
         return;
      }

      float groupW = this.modeSelector.measureSingleRowWidth(this.options);
      float maxSingleRowW = Math.min(440.0F, cardW * 0.50F);
      if (groupW > 0.0F && groupW <= maxSingleRowW) {
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

         float gx = m.controlAreaRightX - groupW;
         float gy = m.controlCenterY - 12.0F;
         this.lastControlX = gx;
         this.lastControlW = groupW;
         if (flag) {
            this.modeSelector.renderConnectedSingle(gx, gy, 24.0F, this.options, this.getSelectedValue(), true, n, n2);
         }
      } else {
         float bodyW = Math.max(160.0F, cardW - ModernSettingsPanel.CARD_PAD_X * 2.0F);
         int bodyH = this.modeSelector.measureWrappedHeight(bodyW, this.options);
         ModernSettingsPanel.CardMetrics m = ModernSettingsPanel.drawStackedCard(
            n3,
            n4,
            n5,
            label,
            this.getDescription(),
            this.shouldShowInlineDescription(),
            this.getSelectedValue(),
            bodyH,
            false,
            false,
            flag,
            n,
            n2
         );
         this.renderedHeight = m.cardH;
         this.applyCardMetrics(m);

         float bx = m.cardX + ModernSettingsPanel.CARD_PAD_X;
         float by = m.controlCenterY;
         this.lastControlX = bx;
         this.lastControlW = bodyW;
         this.modeSelector.renderSegments(
            bx,
            by,
            bodyW,
            this.options,
            idx -> this.options != null && idx >= 0 && idx < this.options.length && this.options[idx] != null && this.options[idx].equals(this.getSelectedValue()),
            flag,
            n,
            n2
         );
      }
   }

   @Override
   public void randomize() {
      if (!this.getDisplayName().contains("Point selection mode") && this.options != null && this.options.length > 0) {
         this.setSelectedValue(this.options[ThreadLocalRandom.current().nextInt(this.options.length)]);
      }
   }

   private StringValue(String str, Module module, SettingGroup doubleValue, String str2, String[] strings) {
      super(module, doubleValue, str);
      this.s = (StringProperty)new StringProperty(this, "S", "").withOnValueChanged(obj -> this.setSelectedValue((String)obj));
      this.renderedHeight = ModernSettingsPanel.MIN_CARD_HEIGHT;
      this.labelWidth = -1;
      this.optionWidthCache = new ConcurrentHashMap();
      this.s.setValue(str2);
      this.s.setDefaultValue(str2);
      this.setOptions(strings);
   }

   static {
      String str = ", ";
      SEPARATOR_WIDTH = (int)FontManager.consolas30Booleans.getStringWidth(str);
   }

   public int getSelectedIndex() {
      return Arrays.asList(this.options).indexOf(this.getSelectedValue());
   }

   public String[] getOptions() {
      return this.options;
   }

   @Override
   public void renderTooltip() {
      this.drawDescriptionTooltip(this.labelWidth);
   }

   private void applyOptions(String[] strings) {
      this.options = strings;
      this.optionWidthCache.clear();

      for (String str : strings) {
         if (str != null) {
            this.optionWidthCache.put(str, (int)FontManager.consolas30Booleans.getStringWidth(str));
         }
      }
   }

   @Override
   public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      if (this.lastUsedDropdown) {
         PopupHost host = MainClickGuiWindow.activePopupHost();
         MainClickGuiWindow mw = MainClickGuiWindow.activeMainWindow();
         float ox = mw != null ? (float)mw.getSettingsOffsetX() : 0.0F;
         float oy = mw != null ? (float)mw.getSettingsOffsetY() : 0.0F;
         return this.dropdown.handleClick(mouseX, mouseY, mouseButton, host, this.dropdownSource, this.lastControlX + ox, this.lastControlBottomY + oy);
      }
      if ((mouseButton == 0 || mouseButton == 1) && this.options != null) {
         int hit = this.modeSelector.hitTest(mouseX, mouseY);
         if (hit >= 0 && hit < this.options.length && this.options[hit] != null) {
            if (!this.options[hit].equals(this.getSelectedValue())) {
               this.notifyUserChanged();
            }
            this.setSelectedValue(this.options[hit]);
            return true;
         }
      }
      return false;
   }
}
