package net.augustus.settings;

import java.io.IOException;
import java.util.Locale;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.modules.Module;
import net.augustus.ui.modern.components.ModernButton;
import net.augustus.ui.modern.components.ModernSettingsPanel;
import net.augustus.ui.modern.render.LucideIcon;

public class ActionSetting extends Setting {
   private long lastClickTimeMs;
   private int labelWidth;
   private int renderedHeight = ModernSettingsPanel.MIN_CARD_HEIGHT;
   private final Runnable action;
   private final ModernButton button = new ModernButton();

   public ActionSetting(Module module, String str, Runnable runnable, SettingGroup doubleValue) {
      this(str, module, doubleValue, runnable);
   }

   @Override
   public void render(int n, int n2, int n3, int n4, int n5, int n6, CustomFontUtil customFontUtil, boolean flag) {
      customFontUtil = FontManager.interMedium;
      super.render(n, n2, n3, n4, n5, n6, customFontUtil, flag);
      String displayName2 = this.getFormattedDisplayName();
      if (this.consumeLabelDirty(this.labelWidth == -1)) {
         this.labelWidth = (int)customFontUtil.getStringWidth(displayName2);
      }

      String lower = displayName2.toLowerCase(Locale.ROOT);
      String btnLabel = "Run";
      LucideIcon icon = LucideIcon.ZAP;
      ModernButton.Variant variant = ModernButton.Variant.GLASS;
      if (lower.contains("delete")) {
         btnLabel = "Delete";
         icon = LucideIcon.TRASH_2;
         variant = ModernButton.Variant.DANGER;
      } else if (lower.contains("folder") || lower.contains("open")) {
         btnLabel = "Open";
         icon = LucideIcon.FOLDER;
      } else if (lower.contains("refresh")) {
         btnLabel = "Refresh";
         icon = LucideIcon.REFRESH_CW;
      } else if (lower.contains("import")) {
         btnLabel = "Import";
         icon = LucideIcon.UPLOAD;
      } else if (lower.contains("upload")) {
         btnLabel = "Upload";
         icon = LucideIcon.UPLOAD;
      } else if (lower.contains("create")) {
         btnLabel = "Create";
         icon = LucideIcon.PLUS;
      }

      float btnW = Math.max(96.0F, FontManager.interSemiBold.getStringWidth(btnLabel) + 38.0F);
      ModernSettingsPanel.CardMetrics m = ModernSettingsPanel.drawSettingCard(
         n3,
         n4,
         n5,
         displayName2,
         this.getDescription(),
         this.shouldShowInlineDescription(),
         btnW,
         false,
         false,
         flag,
         n,
         n2
      );
      this.renderedHeight = m.cardH;
      this.applyCardMetrics(m);

      if (flag) {
         float btnX = m.controlAreaRightX - btnW;
         float btnY = m.controlCenterY - 12.0F;
         this.button.render(btnX, btnY, btnW, 24.0F, btnLabel, icon, variant, n, n2);
      }
   }

   @Override
   public void resetToDefault() {
   }

   @Override
   public int getHeight() {
      return this.renderedHeight;
   }

   @Override
   public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      super.mouseClicked(mouseX, mouseY, mouseButton);
      if (mouseButton == 0 && this.button.isHovered(mouseX, mouseY)) {
         long currentTimeMs = System.currentTimeMillis();
         if (currentTimeMs - this.lastClickTimeMs > 50L) {
            this.lastClickTimeMs = currentTimeMs;
            this.action.run();
            return true;
         }
      }

      return false;
   }

   @Override
   public void renderTooltip() {
      this.drawDescriptionTooltip(this.labelWidth);
   }

   public ActionSetting(Module module, String str, Runnable runnable) {
      this(str, module, null, runnable);
   }

   private ActionSetting(String str, Module module, SettingGroup doubleValue, Runnable runnable) {
      super(module, doubleValue, str);
      this.labelWidth = -1;
      this.lastClickTimeMs = 0L;
      this.action = runnable;
   }
}
