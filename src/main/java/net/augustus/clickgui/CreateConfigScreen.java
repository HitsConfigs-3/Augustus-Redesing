package net.augustus.clickgui;

import net.augustus.Augustus;
import net.augustus.config.ModuleConfigStorage;
import net.augustus.font.FontManager;
import net.augustus.render.RenderUtil;
import net.augustus.ui.modern.components.ModernButton;
import net.augustus.ui.modern.components.ModernTextInput;
import net.augustus.ui.modern.components.ModernToggleSwitch;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.utils.ChatUtil;
import net.augustus.utils.render.KawaseBlurEngine;
import java.io.IOException;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;

public class CreateConfigScreen extends GuiScreen {
   private final GuiScreen parentScreen;
   private boolean unprotected = true;
   private String configName = "";
   private boolean inputFocused = true;
   private long cursorTimerMs = System.currentTimeMillis();

   private final ModernTextInput nameInput = new ModernTextInput();
   private final ModernToggleSwitch protectedSwitch = new ModernToggleSwitch();
   private final ModernButton createButton = new ModernButton();
   private final ModernButton cancelButton = new ModernButton();

   public CreateConfigScreen(GuiScreen screen) {
      this.parentScreen = screen;
   }

   private void createConfig() {
      String trimmed = this.configName.trim();
      if (!trimmed.isEmpty()) {
         ChatUtil.sendChat("Config created: §c" + trimmed);
         ModuleConfigStorage moduleConfigStorage = Augustus.getInstance().getConfigManager().moduleConfigStorage;
         moduleConfigStorage.saveConfig(trimmed, "configs", this.unprotected);
         this.configName = "";
      }
   }

   @Override
   public void initGui() {
      super.initGui();
      Keyboard.enableRepeatEvents(true);
      this.inputFocused = true;
      this.cursorTimerMs = System.currentTimeMillis();
   }

   @Override
   public void onGuiClosed() {
      Keyboard.enableRepeatEvents(false);
      super.onGuiClosed();
   }

   @Override
   public void drawScreen(int mouseX, int mouseY, float partialTicks) {
      UiAnimation.beginFrame();
      KawaseBlurEngine.invalidateFrame();

      ScaledResolution sr = new ScaledResolution(this.mc);
      float w = 248.0F;
      float h = 152.0F;
      float x = ((float)sr.getScaledWidth() - w) * 0.5F;
      float y = ((float)sr.getScaledHeight() - h) * 0.5F;

      GlassRenderer.drawFloatingGlassPopup(x, y, w, h, UiTheme.WINDOW_RADIUS, UiTheme.THICK_BORDER_THICKNESS, UiTheme.GLASS_BORDER_STRONG);

      float pad = 16.0F;
      FontManager.interBold.drawString("Create Config", (double)(x + pad), (double)(y + 14.0F), UiTheme.TEXT_PRIMARY);
      GlassRenderer.drawSeparator(x + pad, y + 36.0F, x + w - pad, y + 36.0F, UiTheme.SEPARATOR_THICKNESS, UiTheme.GLASS_DIVIDER);

      this.nameInput.renderString(
         x + pad,
         y + 46.0F,
         w - pad * 2.0F,
         26.0F,
         this.configName,
         this.inputFocused,
         "Config name...",
         LucideIcon.FOLDER,
         true,
         mouseX,
         mouseY,
         this.cursorTimerMs,
         FontManager.interMedium
      );

      this.protectedSwitch.drawLabeledRow(x + pad, y + 80.0F, w - pad * 2.0F, "Protected", !this.unprotected, mouseX, mouseY);

      float btnW = (w - pad * 2.0F - 8.0F) * 0.5F;
      this.cancelButton.render(x + pad, y + 112.0F, btnW, 26.0F, "Cancel", LucideIcon.CLOSE, ModernButton.Variant.GLASS, mouseX, mouseY);
      this.createButton.render(x + pad + btnW + 8.0F, y + 112.0F, btnW, 26.0F, "Create", LucideIcon.PLUS, ModernButton.Variant.PRIMARY, mouseX, mouseY);

      super.drawScreen(mouseX, mouseY, partialTicks);
   }

   @Override
   public void keyTyped(char typedChar, int keyCode) throws IOException {
      if (keyCode == Keyboard.KEY_ESCAPE) {
         this.mc.displayGuiScreen(this.parentScreen);
         return;
      }
      if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
         this.createConfig();
         this.mc.displayGuiScreen(this.parentScreen);
         return;
      }
      if (this.inputFocused) {
         if (keyCode == Keyboard.KEY_BACK) {
            if (!this.configName.isEmpty()) {
               this.configName = this.configName.substring(0, this.configName.length() - 1);
            }
            return;
         }
         if (typedChar >= 32 && typedChar != 127) {
            this.configName += typedChar;
            this.cursorTimerMs = System.currentTimeMillis();
         }
      }
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      super.mouseClicked(mouseX, mouseY, mouseButton);
      if (mouseButton != 0) {
         return;
      }

      ScaledResolution sr = new ScaledResolution(this.mc);
      float w = 248.0F;
      float h = 152.0F;
      float x = ((float)sr.getScaledWidth() - w) * 0.5F;
      float y = ((float)sr.getScaledHeight() - h) * 0.5F;
      if (this.nameInput.isHovered(mouseX, mouseY)) {
         if (!this.configName.isEmpty() && this.nameInput.isClearButtonHovered(mouseX, mouseY)) {
            this.configName = "";
         }
         this.inputFocused = true;
         this.cursorTimerMs = System.currentTimeMillis();
         return;
      }
      this.inputFocused = false;

      if (this.protectedSwitch.isHovered(mouseX, mouseY) || RenderUtil.isHovered(x + 16.0F, y + 80.0F, w - 32.0F, 24.0F, mouseX, mouseY)) {
         this.unprotected = !this.unprotected;
         return;
      }
      if (this.cancelButton.isHovered(mouseX, mouseY)) {
         this.mc.displayGuiScreen(this.parentScreen);
         return;
      }
      if (this.createButton.isHovered(mouseX, mouseY)) {
         this.createConfig();
         this.mc.displayGuiScreen(this.parentScreen);
      }
   }
}
