package net.augustus.settings;

import java.io.IOException;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.modules.Module;
import net.augustus.render.RenderUtil;
import net.augustus.settings.property.IntegerProperty;
import net.augustus.ui.modern.components.ModernButton;
import net.augustus.ui.modern.components.ModernSettingsPanel;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiTheme;
import org.lwjgl.input.Keyboard;

public class KeybindSetting extends Setting {
   private int boxY;
   private final IntegerProperty k;
   private int boxX;
   private int boxWidth;
   private boolean listeningForKey;
   private int boxHeight;
   private int renderedHeight = ModernSettingsPanel.MIN_CARD_HEIGHT;
   private final ModernButton keyButton = new ModernButton();

   public KeybindSetting visibleWhen(SettingVisibilityCondition condition) {
      this.visibilityCondition = condition;
      return this;
   }

   @Override
   public Setting withVisibility(SettingVisibilityCondition condition) {
      return this.visibleWhen(condition);
   }

   public void setKeyCode(int keyCode) {
      IntegerProperty integerProperty = this.k;
      Integer num = keyCode;
      integerProperty.setValue(num);
   }

   public KeybindSetting(Module module, String str) {
       super(module, null, str);
      this.k = new IntegerProperty(this, "K", 0);
   }

   public int getKeyCode() {
      return (Integer)this.k.getValue();
   }

   @Override
   public int getHeight() {
      return this.renderedHeight;
   }

   @Override
   public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      super.mouseClicked(mouseX, mouseY, mouseButton);
      boolean flag = false;
      if (!RenderUtil.isHovered((float)this.boxX, (float)this.boxY, (float)this.boxWidth, (float)this.boxHeight, mouseX, mouseY) || this.listeningForKey || mouseButton != 0 && mouseButton != 1) {
         if (this.listeningForKey) {
            int n = this.boxWidth + 20;
            int boxHeight2 = this.boxHeight;
            int boxY2 = this.boxY;
            if (this.isPointInBounds(this.boxX, boxY2, n, boxHeight2, mouseX, mouseY) && (mouseButton == 0 || mouseButton == 1) || mouseButton > 1) {
               this.setKeyCode(mouseButton + 10000);
            }

            this.listeningForKey = false;
            this.setFocused(false);
         }
      } else {
         this.listeningForKey = true;
         this.setFocused(true);
         flag = true;
      }

      return flag;
   }

   @Override
   public void resetToDefault() {
      this.k.resetToDefault();
   }

   @Override
   public void keyTyped(char typedChar, int keyCode) throws IOException {
      super.keyTyped(typedChar, keyCode);
      if (this.listeningForKey) {
         IntegerProperty integerProperty = this.k;
         Integer num = keyCode == 1 ? -1 : keyCode;
         integerProperty.setValue(num);
         this.listeningForKey = false;
         this.setFocused(false);
      }
   }

   @Override
   public void renderTooltip() {
      this.drawDescriptionTooltip((int)FontManager.interSemiBold.getStringWidth(this.getDisplayName()) + 6);
   }

   private boolean isPointInBounds(int n, int n2, int n3, int n4, int n5, int n6) {
      return n5 >= n && n6 >= n2 && n5 < n + n3 && n6 < n2 + n4;
   }

   public KeybindSetting(Module module, String str, SettingGroup doubleValue) {
       super(module, doubleValue, str);
      this.k = new IntegerProperty(this, "K", 0);
   }

   @Override
   public Setting withDisplayName(String str) {
      return this.named(str);
   }

   @Override
   public void render(int n, int n2, int n3, int n4, int n5, int n6, CustomFontUtil customFontUtil, boolean flag) {
      customFontUtil = FontManager.interMedium;
      super.render(n, n2, n3, n4, n5, n6, customFontUtil, flag);
      String label = this.getFormattedDisplayName();
      String keyName = this.listeningForKey
         ? "Press key..."
         : (this.getKeyCode() >= 10000 ? "Mouse" + (this.getKeyCode() - 10000) : Keyboard.getKeyName(Math.max(this.getKeyCode(), 0)));
      float btnW = Math.max(100.0F, FontManager.interSemiBold.getStringWidth(keyName) + 36.0F);
      ModernSettingsPanel.CardMetrics m = ModernSettingsPanel.drawSettingCard(
         n3,
         n4,
         n5,
         label,
         this.getDescription(),
         this.shouldShowInlineDescription(),
         btnW,
         false,
         this.listeningForKey,
         flag,
         n,
         n2
      );
      this.renderedHeight = m.cardH;
      this.applyCardMetrics(m);
      this.boxWidth = Math.round(btnW);
      this.boxHeight = 24;
      this.boxX = Math.round(m.controlAreaRightX - btnW);
      this.boxY = Math.round(m.controlCenterY - 12.0F);

      if (flag) {
         this.keyButton.render(
            (float)this.boxX,
            (float)this.boxY,
            (float)this.boxWidth,
            (float)this.boxHeight,
            keyName,
            LucideIcon.KEYBOARD,
            this.listeningForKey ? ModernButton.Variant.PRIMARY : ModernButton.Variant.GLASS,
            n,
            n2
         );
      }
   }

   public KeybindSetting named(String str) {
      this.setDisplayName(str);
      return this;
   }

}
