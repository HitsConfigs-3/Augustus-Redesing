package net.augustus.settings;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import net.augustus.clickgui.ClickGuiScreen;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.modules.Module;
import net.augustus.render.RenderUtil;
import net.augustus.settings.property.BooleanProperty;
import net.augustus.ui.modern.components.ModernSettingsPanel;
import net.augustus.ui.modern.components.ModernToggleSwitch;
import net.augustus.ui.modern.render.UiTheme;

public class BooleanValue extends Setting {
   private int labelWidth;
   private int renderedHeight = ModernSettingsPanel.MIN_CARD_HEIGHT;
   private float lastCardW = 420.0F;
   private final BooleanProperty b;
   private final ModernToggleSwitch toggleSwitch = new ModernToggleSwitch();

   /**
    * Applies the ClickGUI open/close envelope to one ARGB at the point of draw.
    *
    * <p>WHY this row needs its own fade: the card behind it is drawn by
    * {@code ModernSettingsPanel.drawSettingCard} (which fades its own colours), but the compact-mode label
    * and the On/Off status readout are painted here, so without this they would hang at full brightness over
    * a fading card for the whole ~290 ms close. Only alpha is scaled; the toggle and the measured label
    * width are untouched.
    *
    * <p>Each colour is wrapped exactly once, at the draw, and never a value that already came out of here.
    */
   private static int faded(int argb) {
      // Settled overlay (everything except the ~288 ms open/close transition): skip the arithmetic.
      return ClickGuiScreen.getScreenFade() >= 0.999F ? argb : ClickGuiScreen.fadeArgb(argb);
   }

   private BooleanValue(String str, Module module, SettingGroup doubleValue, boolean flag) {
      super(module, doubleValue, str);
      this.b = (BooleanProperty)new BooleanProperty(this, "B", false).withOnValueChanged(obj -> this.setValue((boolean)obj));
      this.labelWidth = -1;
      BooleanProperty booleanProperty2 = this.b;
      Boolean bool = flag;
      booleanProperty2.setValue(bool);
      booleanProperty2 = this.b;
      Boolean bool3 = flag;
      booleanProperty2.setDefaultValue(bool3);
   }

   @Override
   public void render(int n, int n2, int n3, int n4, int n5, int n6, CustomFontUtil customFontUtil, boolean flag) {
      customFontUtil = FontManager.interMedium;
      super.render(n, n2, n3, n4, n5, n6, customFontUtil, flag);
      String label = this.getFormattedDisplayName();
      if (this.consumeLabelDirty(this.labelWidth == -1)) {
         this.labelWidth = (int)customFontUtil.getStringWidth(label);
      }

      boolean compact = n5 <= 0;
      if (compact) {
         this.renderedHeight = 28;
         if (flag) {
            customFontUtil.drawVerticallyCenteredString(label, (double)n3, (double)n4, 28.0, faded(UiTheme.primaryTextColor()));
            float switchW = 34.0F;
            float switchH = 18.0F;
            float switchX = (float)(n3 + this.labelWidth + 8);
            float switchY = (float)n4 + (28.0F - switchH) * 0.5F;
            this.toggleSwitch.render(switchX, switchY, switchW, switchH, this.getValue(), n, n2);
            this.contentWidth = (int)(switchX - (float)n3 + switchW + 10.0F);
         }
         return;
      }

      float switchW = 36.0F;
      float switchH = 18.0F;
      float statusMaxW = Math.max(FontManager.interRegular.getStringWidth("On"), FontManager.interRegular.getStringWidth("Off"));
      float rightReserve = switchW + 10.0F + statusMaxW;

      ModernSettingsPanel.CardMetrics m = ModernSettingsPanel.drawSettingCard(
         n3,
         n4,
         n5,
         label,
         this.getDescription(),
         this.shouldShowInlineDescription(),
         rightReserve,
         false,
         false,
         flag,
         n,
         n2
      );
      this.renderedHeight = m.cardH;
      this.lastCardW = m.cardW;
      this.applyCardMetrics(m);

      if (flag) {
         boolean checked = this.getValue();
         String statusText = checked ? "On" : "Off";
         float statusW = FontManager.interRegular.getStringWidth(statusText);
         float switchX = m.controlAreaRightX - switchW;
         float switchY = m.controlCenterY - switchH * 0.5F;
         int statusColor = checked ? UiTheme.accentSoftGlow() : UiTheme.mutedTextColor();
         FontManager.interRegular.drawVerticallyCenteredString(
            statusText,
            (double)(switchX - 10.0F - statusW),
            (double)m.cardY,
            (double)m.cardH,
            faded(statusColor)
         );
         this.toggleSwitch.render(switchX, switchY, switchW, switchH, checked, n, n2);
      }
   }

   @Override
   public void renderTooltip() {
      this.drawDescriptionTooltip(this.labelWidth + 6);
   }

   public void setDefaultValue(boolean defaultValue) {
      BooleanProperty booleanProperty = this.b;
      Boolean bool = defaultValue;
      booleanProperty.setDefaultValue(bool);
   }

   public boolean getValue() {
      return (Boolean)this.b.getValue();
   }

   public BooleanValue(Module module, String str, boolean flag, SettingGroup doubleValue) {
      this(str, module, doubleValue, flag);
   }

   public BooleanValue(Module module, String str, boolean flag) {
      this(str, module, null, flag);
   }

   public BooleanValue(String str, boolean flag) {
      this(str, null, null, flag);
   }

   @Override
   public void resetToDefault() {
      boolean defaultValue = (Boolean)this.b.getDefaultValue();
      this.setValue(defaultValue);
   }

   @Override
   public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      super.mouseClicked(mouseX, mouseY, mouseButton);
      if (mouseButton == 0
         && (this.toggleSwitch.isHovered(mouseX, mouseY)
            || (this.boundsWidth > 0 && RenderUtil.isHovered((float)this.renderX, (float)this.renderY, this.lastCardW, (float)this.renderedHeight, mouseX, mouseY)))) {
         this.setValue(!this.getValue());
         return true;
      } else {
         return false;
      }
   }

   public void setValue(boolean value) {
      if (this.getValue() != value) {
         this.notifyValueChanged();
      }

      BooleanProperty booleanProperty = this.b;
      Boolean bool = value;
      booleanProperty.setValue(bool);
   }

   @Override
   public int getHeight() {
      return this.renderedHeight;
   }

   @Override
   public void randomize() {
      this.setValue(ThreadLocalRandom.current().nextBoolean());
   }
}
