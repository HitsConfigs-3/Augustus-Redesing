package net.augustus.settings;

import java.io.IOException;
import java.util.ArrayList;
import net.augustus.ClientManagers;
import net.augustus.clickgui.components.TextFieldComponent;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.modules.Module;
import net.augustus.modules.misc.Dummy;
import net.augustus.settings.property.StringProperty;
import net.augustus.ui.modern.components.ModernSettingsPanel;
import net.augustus.ui.modern.components.ModernTextInput;
import net.augustus.ui.modern.render.UiTheme;

public class TextInputSetting extends Setting {
   private final StringProperty n;
   private int labelWidth;
   private int renderedHeight = ModernSettingsPanel.MIN_CARD_HEIGHT;
   private final TextFieldComponent textField;
   private long lastClickTimeMs;
   private final ModernTextInput textInput = new ModernTextInput();

   public TextInputSetting(Module module, String str, String str2, boolean flag, SettingGroup doubleValue) {
       super(module, doubleValue, str);
      this.labelWidth = -1;
      this.lastClickTimeMs = 0L;
      this.textField = new TextFieldComponent(str2, flag ? StringInputMode.HIDDEN : StringInputMode.DEFAULT);
      this.n = (StringProperty)new StringProperty(this, "N", str2, str2).withOnValueChanged(obj -> this.setTextFieldValue((String)obj));
      if (module != null && !(module instanceof Dummy)) {
         ClientManagers.settingManager.registerSetting(this);
         ArrayList list = doubleValue != null ? doubleValue.getChildren() : module.getSettings();
         list.removeIf(obj2 -> this.matchesSettingName((Setting)obj2));
         list.add(this);
      }
   }

   @Override
   public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      if (mouseButton == 0 && this.textInput.isHovered(mouseX, mouseY)) {
         if (!this.textField.isFocused()) {
            this.textField.setFocused(true);
         } else {
            this.textField.mouseClicked(mouseX, mouseY, mouseButton);
            this.textField.setFocused(true);
         }
         this.setFocused(true);
         return true;
      }
      this.textField.mouseClicked(mouseX, mouseY, mouseButton);
      this.setFocused(this.textField.isFocused());
      return false;
   }

   public TextInputSetting(Module module, String str, boolean flag) {
       this(module, str, "", flag);
   }

   private boolean matchesSettingName(Setting setting) {
      return setting.getName().equals(this.getName());
   }

   public void setValue(String value) {
      this.n.setValue(value);
      this.textField.setText(value);
   }

   @Override
   public void render(int n2, int n3, int n4, int n5, int n6, int n7, CustomFontUtil customFontUtil, boolean flag) {
      customFontUtil = FontManager.interMedium;
      super.render(n2, n3, n4, n5, n6, n7, customFontUtil, flag);
      String str = this.getFormattedDisplayName();
      if (this.consumeLabelDirty(this.labelWidth == -1)) {
         this.labelWidth = (int)customFontUtil.getStringWidth(str);
      }

      float cardW = ModernSettingsPanel.resolveCardWidth(n4, n6);
      float boxW = Math.min(250.0F, Math.max(175.0F, cardW * 0.29F));
      ModernSettingsPanel.CardMetrics m = ModernSettingsPanel.drawSettingCard(
         n4,
         n5,
         n6,
         str,
         this.getDescription(),
         this.shouldShowInlineDescription(),
         boxW,
         false,
         this.textField.isFocused(),
         flag,
         n2,
         n3
      );
      this.renderedHeight = m.cardH;
      this.applyCardMetrics(m);

      if (flag) {
         float boxX = m.controlAreaRightX - boxW;
         float boxY = m.controlCenterY - 12.0F;
         this.textInput.render(boxX, boxY, boxW, 24.0F, this.textField, "Enter value...", null, n2, n3, FontManager.interMedium);
         this.n.setValue(this.textField.getText());
         this.setFocused(this.textField.isFocused());
      }
   }

   public TextInputSetting(Module module, String str, String str2, SettingGroup doubleValue) {
       this(module, str, str2, false, doubleValue);
   }

   @Override
   public int getHeight() {
      return this.renderedHeight;
   }

   public TextInputSetting(Module module, String str, String str2, boolean flag) {
       this(module, str, str2, flag, null);
   }

   public TextInputSetting(Module module, String str, SettingGroup doubleValue) {
       this(module, str, "", false, doubleValue);
   }

   public TextInputSetting(Module module, String str, String str2) {
       this(module, str, str2, false, null);
   }

   public TextInputSetting(Module module, String str, boolean flag, SettingGroup doubleValue) {
       this(module, str, "", flag, doubleValue);
   }

   public TextInputSetting(Module module, String str) {
       this(module, str, "");
   }

   public String getValue() {
      return (String)this.n.getValue();
   }

   @Override
   public void resetToDefault() {
      this.n.resetToDefault();
      this.textField.setText(this.getValue());
   }

   @Override
   public void keyTyped(char typedChar, int keyCode) throws IOException {
      super.keyTyped(typedChar, keyCode);
      this.textField.keyTyped(typedChar, keyCode);
   }

   @Override
   public void renderTooltip() {
      this.drawDescriptionTooltip(this.labelWidth);
   }

   public void setTextFieldValue(String textFieldValue) {
      this.textField.setText(textFieldValue);
   }

}
