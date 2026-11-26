package net.augustus.settings;

import java.io.IOException;
import java.util.Objects;
import net.augustus.Augustus;
import net.augustus.clickgui.components.SliderComponent;
import net.augustus.clickgui.components.TextFieldComponent;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.modules.Module;
import net.augustus.render.RenderUtil;
import net.augustus.settings.property.DoubleProperty;
import net.augustus.ui.modern.components.ModernSettingsPanel;
import net.augustus.ui.modern.components.ModernSlider;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.utils.input.KeyboardInputUtil;
import net.augustus.utils.math.MathStatisticsUtil;
import net.minecraft.util.MathHelper;

public class DoubleValue extends Setting {
   private final TextFieldComponent textField;
   private int decimalPlaces;
   private boolean wasArrowAdjusting;
   private long arrowPressStartTimeMs;
   private long lastArrowRepeatTimeMs;
   private boolean scrollAdjusted;
   private final SliderComponent slider;
   private final ModernSlider modernSlider = new ModernSlider();
   private double minValue;
   private int labelWidth;
   private final DoubleProperty v;
   private double lastSyncedValue;
   private boolean arrowAdjusting;
   private double maxValue;
   private int renderedHeight = ModernSettingsPanel.MIN_CARD_HEIGHT;

   @Override
   public int getHeight() {
      return this.renderedHeight;
   }

   public DoubleValue visibleWhen(SettingVisibilityCondition condition) {
      this.visibilityCondition = condition;
      return this;
   }

   @Override
   public void render(int n, int n2, int n3, int n4, int n5, int n6, CustomFontUtil customFontUtil, boolean flag) {
      customFontUtil = FontManager.interMedium;
      super.render(n, n2, n3, n4, n5, n6, customFontUtil, flag);
      String label = this.getFormattedDisplayName();
      this.wasArrowAdjusting = this.arrowAdjusting;
      if (this.consumeLabelDirty(this.labelWidth == -1)) {
         this.labelWidth = (int)customFontUtil.getStringWidth(label);
      }

      float cardW = ModernSettingsPanel.resolveCardWidth(n3, n5);
      float sliderW = Math.min(316.0F, Math.max(200.0F, cardW * 0.36F));
      ModernSettingsPanel.CardMetrics m = ModernSettingsPanel.drawSettingCard(
         n3,
         n4,
         n5,
         label,
         this.getDescription(),
         this.shouldShowInlineDescription(),
         sliderW,
         false,
         this.isFocused(),
         flag,
         n,
         n2
      );
      this.renderedHeight = m.cardH;
      this.applyCardMetrics(m);

      if (flag) {
         float sliderX = m.controlAreaRightX - sliderW;

         if (RenderUtil.isHovered(sliderX - 6.0F, m.cardY, sliderW + 12.0F, (float)m.cardH, n, n2)) {
            if (this.arrowAdjusting
               && (KeyboardInputUtil.isKeyDown(203) || KeyboardInputUtil.isKeyDown(205))
               && System.currentTimeMillis() - this.arrowPressStartTimeMs >= 400L
               && System.currentTimeMillis() - this.lastArrowRepeatTimeMs >= 50L) {
               double step = 1.0 / Math.pow(10.0, (double)Math.min(this.decimalPlaces, 3)) * (double)(KeyboardInputUtil.isKeyDown(203) ? -1 : 1);
               if (this.getValue() + step <= this.maxValue && this.getValue() + step >= this.minValue) {
                  this.setValue(this.getValue() + step);
                  this.notifyValueChanged();
                  this.notifyUserChanged();
               }
               this.lastArrowRepeatTimeMs = System.currentTimeMillis();
            }

            this.scrollAdjusted = false;
            if (KeyboardInputUtil.isCtrlDown()) {
               int lastDelta = Augustus.getInstance().getMouseWheelTracker().lastDelta;
               if (lastDelta != 0) {
                  lastDelta = lastDelta > 0 ? 1 : -1;
                  double step = (double)Math.abs(lastDelta) / Math.pow(10.0, (double)Math.min(this.decimalPlaces, 3)) * (double)lastDelta;
                  if (this.getValue() + step <= this.maxValue && this.getValue() + step >= this.minValue) {
                     this.setValue(this.getValue() + step);
                     this.notifyValueChanged();
                     this.notifyUserChanged();
                     this.scrollAdjusted = true;
                  }
               }
            }
         }

         if (!KeyboardInputUtil.isKeyDown(203) && !KeyboardInputUtil.isKeyDown(205)) {
            this.arrowAdjusting = false;
         }

         String displayVal = this.textField.isFocused() ? null : ModernSlider.formatNumber(this.getValue(), this.decimalPlaces);
         double nextVal = this.modernSlider.render(
            sliderX,
            m.cardY,
            sliderW,
            (float)m.cardH,
            n,
            n2,
            this.getValue(),
            this.minValue,
            this.maxValue,
            this.decimalPlaces,
            this.slider.isDragging(),
            this.slider.getClickX(),
            this.slider.getClickY(),
            this.textField,
            displayVal
         );

         this.slider.x = (int)this.modernSlider.getTrackX();
         this.slider.y = (int)this.modernSlider.getTrackY();
         this.slider.sliderWidth = (int)this.modernSlider.getTrackWidth();
         this.slider.sliderHeight = (int)this.modernSlider.getTrackHeight();

         if (nextVal != this.getValue()) {
            this.notifyValueChanged();
            this.notifyUserChanged();
         }
         this.setValue(nextVal);

         if (this.textField.isFocused()) {
            if (!Objects.equals(this.textField.getText(), "")) {
               if (!this.textField.getText().equals("-") && !this.textField.getText().endsWith("-") && !this.textField.getText().endsWith("E")) {
                  Double parsed = this.tryParseDouble(this.textField.getText());
                  if (parsed != null) {
                     double clamped = MathHelper.clamp_double(parsed, this.minValue, this.maxValue);
                     double rounded = this.roundToDecimals(clamped, this.decimalPlaces);
                     if (rounded != this.getValue()) {
                        this.notifyValueChanged();
                        this.notifyUserChanged();
                     }
                     this.setValue(rounded);
                  }
               } else {
                  this.setValue(1.7976931348623157E308);
               }
            }
         } else {
            double val = this.getValue();
            Double parsedField = this.textField.getText() != null && !this.textField.getText().isEmpty()
               ? this.tryParseDouble(this.textField.getText())
               : null;
            if (this.lastSyncedValue != val || parsedField != null && parsedField != val) {
               this.textField.setText(ModernSlider.formatNumber(val, this.decimalPlaces));
            } else if (this.textField.getText() != null && this.textField.getText().isEmpty()) {
               this.textField.setText(ModernSlider.formatNumber(this.lastSyncedValue, this.decimalPlaces));
            }
            this.lastSyncedValue = this.getValue();
         }

         this.setFocused(this.textField.isFocused() || this.slider.isDragging());
      }
   }

   public TextFieldComponent getTextField() {
      return this.textField;
   }

   public void setValue(double value) {
      DoubleProperty doubleProperty = this.v;
      Double d2 = value;
      doubleProperty.setValue(d2);
   }

   public void setMaxValue(double d) {
      this.maxValue = d;
   }

   public void setMinValue(double d) {
      this.minValue = d;
   }

   private double roundToDecimals(double d, int n) {
      if (n <= 0) {
         return (double)Math.round(d);
      } else {
         double d2 = Math.pow(10.0, (double)n);
         return (double)Math.round(d * d2) / d2;
      }
   }

   private DoubleValue(String str, Module module, SettingGroup doubleValue, double d, double d2, double d3, int n) {
       super(module, doubleValue, str);
      this.v = new DoubleProperty(this, "V", 0.0);
      this.slider = new SliderComponent();
      this.lastArrowRepeatTimeMs = 0L;
      this.arrowPressStartTimeMs = 0L;
      this.arrowAdjusting = false;
      this.labelWidth = -1;
      this.lastSyncedValue = 0.0;
      this.wasArrowAdjusting = false;
      this.scrollAdjusted = false;
      this.setValue(d);
      DoubleProperty doubleProperty = this.v;
      Double d4 = d;
      doubleProperty.setDefaultValue(d4);
      this.minValue = d2;
      this.maxValue = d3;
      this.decimalPlaces = n;
      this.textField = new TextFieldComponent(String.valueOf(d), StringInputMode.NUMERIC);
   }

   public DoubleValue(String str, double d, double d2, double d3, int n) {
       this(str, null, null, d, d2, d3, n);
   }

   public DoubleValue named(String str) {
      this.setDisplayName(str);
      return this;
   }

   public DoubleValue(Module module, String str, double d, double d2, double d3, int n) {
       this(str, module, null, d, d2, d3, n);
   }

   @Override
   public void resetToDefault() {
      this.v.resetToDefault();
   }

   public double getMaxValue() {
      return this.maxValue;
   }

   private Double tryParseDouble(String str) {
      try {
         return Double.parseDouble(str);
      } catch (NumberFormatException e) {
         return null;
      }
   }

   public double getValue() {
      return (Double)this.v.getValue();
   }

   @Override
   public void randomize() {
      if (this.getName().contains("frequency")) {
         this.setValue(MathStatisticsUtil.lerp(this.minValue, 5.0, Math.random()));
      } else if (this.getDisplayName().contains("Rate of change")) {
         this.setValue(MathStatisticsUtil.lerp(this.minValue, 5.0, Math.random()));
      } else {
         this.setValue(MathStatisticsUtil.lerp(this.minValue, this.maxValue, Math.random()));
      }
   }

   @Override
   public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
      super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
      this.slider.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
   }

   public double getMinValue() {
      return this.minValue;
   }

   @Override
   public void mouseReleased(int mouseX, int mouseY, int state) {
      super.mouseReleased(mouseX, mouseY, state);
      this.slider.mouseReleased(mouseX, mouseY, state);
   }

   @Override
   public void renderTooltip() {
      this.drawDescriptionTooltip(this.labelWidth);
   }

   public void setDecimalPlaces(int n) {
      this.decimalPlaces = n;
   }

   public boolean isWasArrowAdjusting() {
      return this.wasArrowAdjusting;
   }

   public double getValue(boolean flag) {
      return flag ? MathHelper.clamp_double(this.getValue(), this.minValue, this.maxValue) : this.getValue();
   }

   public int getDecimalPlaces() {
      return this.decimalPlaces;
   }

   @Override
   public Setting withDisplayName(String str) {
      return this.named(str);
   }

   public SliderComponent getSlider() {
      return this.slider;
   }

   @Override
   public Setting withVisibility(SettingVisibilityCondition condition) {
      return this.visibleWhen(condition);
   }

   @Override
   public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      super.mouseClicked(mouseX, mouseY, mouseButton);
      this.slider.mouseClicked(mouseX, mouseY, mouseButton);
      if (mouseButton == 0 && this.modernSlider.isValueChipHovered(mouseX, mouseY)) {
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
      if (mouseButton == 0 && this.modernSlider.isTrackHovered(mouseX, mouseY)) {
         this.slider.setDragging(true);
         this.textField.setFocused(false);
         return true;
      }
      return false;
   }

   public DoubleValue(Module module, String str, double d, double d2, double d3, int n, SettingGroup doubleValue) {
       this(str, module, doubleValue, d, d2, d3, n);
   }

   @Override
   public void keyTyped(char typedChar, int keyCode) throws IOException {
      super.keyTyped(typedChar, keyCode);
      this.textField.keyTyped(typedChar, keyCode);
      this.slider.keyTyped(typedChar, keyCode);
      if (RenderUtil.isHovered(
            (float)this.slider.getX(), (float)this.slider.getY(), (float)this.slider.getSliderWidth(), (float)this.slider.getSliderHeight(), this.lastMouseX, this.lastMouseY
         )
         && (keyCode == 203 || keyCode == 205)) {
         this.arrowAdjusting = true;
         this.arrowPressStartTimeMs = System.currentTimeMillis();
         this.lastArrowRepeatTimeMs = System.currentTimeMillis();
         double d = 1.0 / Math.pow(10.0, (double)this.decimalPlaces) * (double)(keyCode == 203 ? -1 : 1);
         if (this.getValue() + d <= this.maxValue && this.getValue() + d >= this.minValue) {
            this.setValue(this.getValue() + d);
            this.notifyValueChanged();
            this.notifyUserChanged();
         }
      }
   }

   public boolean isScrollAdjusted() {
      return this.scrollAdjusted;
   }


}
