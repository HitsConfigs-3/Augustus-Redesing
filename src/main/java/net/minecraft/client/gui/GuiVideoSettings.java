package net.minecraft.client.gui;

import java.io.IOException;
import net.augustus.modules.Module;
import net.augustus.Augustus;
import net.augustus.events.listeners.GuiScaleListener;
import net.augustus.ui.VanillaScreenText;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.MenuScreenSkin;
import net.augustus.ui.modern.render.UiTheme;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.optifine.Lang;
import net.optifine.config.Config;
import net.optifine.gui.GuiAnimationSettingsOF;
import net.optifine.gui.GuiDetailSettingsOF;
import net.optifine.gui.GuiOptionButtonOF;
import net.optifine.gui.GuiOptionSliderOF;
import net.optifine.gui.GuiOtherSettingsOF;
import net.optifine.gui.GuiPerformanceSettingsOF;
import net.optifine.gui.GuiQualitySettingsOF;
import net.optifine.gui.TooltipManager;
import net.optifine.gui.TooltipProviderOptions;
import net.optifine.shaders.gui.GuiShaders;

public class GuiVideoSettings extends GuiScreen {
   public String screenTitle = "Video Settings";
   private GameSettings guiGameSettings;
   private static GameSettings.Options[] videoOptions = new GameSettings.Options[]{
      GameSettings.Options.GRAPHICS,
      GameSettings.Options.RENDER_DISTANCE,
      GameSettings.Options.AMBIENT_OCCLUSION,
      GameSettings.Options.FRAMERATE_LIMIT,
      GameSettings.Options.AO_LEVEL,
      GameSettings.Options.VIEW_BOBBING,
      GameSettings.Options.GUI_SCALE,
      GameSettings.Options.USE_VBO,
      GameSettings.Options.GAMMA,
      GameSettings.Options.BLOCK_ALTERNATIVES,
      GameSettings.Options.DYNAMIC_LIGHTS,
      GameSettings.Options.DYNAMIC_FOV
   };
   private TooltipManager tooltipManager = new TooltipManager(this, new TooltipProviderOptions());
   private int cardX;
   private int cardY;
   private int cardW;
   private int cardH;
   private int headerY;
   private int dividerY;

   @Override
   public void drawScreen(int mouseX, int mouseY, float partialTicks) {
      this.drawDefaultBackground();
      MenuScreenSkin.drawGlassContainer((float)this.cardX, (float)this.cardY, (float)this.cardW, (float)this.cardH, 10.0F);
      VanillaScreenText.drawHeader(this.width, this.screenTitle, LucideIcon.DEVICE_DESKTOP, this.headerY);
      GlassRenderer.drawSeparator(
         (float)(this.cardX + 14),
         (float)this.dividerY,
         (float)(this.cardX + this.cardW - 14),
         (float)this.dividerY,
         1.0F,
         UiTheme.GLASS_DIVIDER
      );

      String ofFooter = ((Module)Augustus.getInstance().getModuleManager().invisMode).isToggled()
         ? "OptiFine HD M5 Ultra"
         : "OptiFine HD M5 Ultra (Hard-forked by EnZaXD & DasNeueUpdate)";
      VanillaScreenText.drawSmallString(ofFooter, 8.0F, (float)(this.height - 13), 0x99CBD5E1);
      String mcVersion = "Minecraft 1.8.9";
      VanillaScreenText.drawSmallString(mcVersion, (float)(this.width - VanillaScreenText.getSmallStringWidth(mcVersion) - 8), (float)(this.height - 13), 0x99CBD5E1);

      super.drawScreen(mouseX, mouseY, partialTicks);
      this.tooltipManager.drawTooltips(mouseX, mouseY, this.buttonList);
   }

   @Override
   public void keyTyped(char typedChar, int keyCode) throws IOException {
      super.keyTyped(typedChar, keyCode);
      if (keyCode == 1) {
         this.mc.gameSettings.saveOptions();
      }
   }

   public static int getButtonWidth(GuiButton button) {
      return button.width;
   }

   public GuiVideoSettings(GuiScreen screen, GameSettings gameSettings) {
      super(screen);
      this.guiGameSettings = gameSettings;
      GuiScreen.NO_TOGGLE_SCREENS.add(this);
   }

   @Override
   public void actionPerformed(GuiButton button) throws IOException {
      if (button.enabled) {
         int guiScale = this.guiGameSettings.guiScale;
         if (button.id < 200 && button instanceof GuiOptionButton) {
            this.guiGameSettings.setOptionValue(((GuiOptionButton)button).returnEnumOptions(), 1);
            button.displayString = this.guiGameSettings.getKeyBinding(GameSettings.Options.getEnumOptions(button.id));
         }

         if (button.id == 200) {
            this.mc.gameSettings.saveOptions();
            GuiScreen parentScreen2 = this.parentScreen;
            this.mc.displayGuiScreen(parentScreen2);
         }

         if (this.guiGameSettings.guiScale != guiScale) {
            ScaledResolution sr = new ScaledResolution(this.mc);
            int scaledWidth = sr.getScaledWidth();
            int scaledHeight = sr.getScaledHeight();
            this.setWorldAndResolution(this.mc, scaledWidth, scaledHeight);
            GuiScaleListener.postGuiScale();
         }

         if (button.id == 201) {
            this.mc.gameSettings.saveOptions();
            GuiDetailSettingsOF guiDetailSettingsOF = new GuiDetailSettingsOF(this, this.guiGameSettings);
            this.mc.displayGuiScreen(guiDetailSettingsOF);
         }

         if (button.id == 202) {
            this.mc.gameSettings.saveOptions();
            GuiQualitySettingsOF guiQualitySettingsOF = new GuiQualitySettingsOF(this, this.guiGameSettings);
            this.mc.displayGuiScreen(guiQualitySettingsOF);
         }

         if (button.id == 211) {
            this.mc.gameSettings.saveOptions();
            GuiAnimationSettingsOF guiAnimationSettingsOF = new GuiAnimationSettingsOF(this, this.guiGameSettings);
            this.mc.displayGuiScreen(guiAnimationSettingsOF);
         }

         if (button.id == 212) {
            this.mc.gameSettings.saveOptions();
            GuiPerformanceSettingsOF guiPerformanceSettingsOF = new GuiPerformanceSettingsOF(this, this.guiGameSettings);
            this.mc.displayGuiScreen(guiPerformanceSettingsOF);
         }

         if (button.id == 222) {
            this.mc.gameSettings.saveOptions();
            GuiOtherSettingsOF guiOtherSettingsOF = new GuiOtherSettingsOF(this, this.guiGameSettings);
            this.mc.displayGuiScreen(guiOtherSettingsOF);
         }

         if (button.id == 231) {
            if (Config.isAntialiasing() || Config.isAntialiasingConfigured()) {
               Config.showGuiMessage(Lang.get("of.message.shaders.aa1"), Lang.get("of.message.shaders.aa2"));
               return;
            }

            if (Config.isAnisotropicFiltering()) {
               Config.showGuiMessage(Lang.get("of.message.shaders.af1"), Lang.get("of.message.shaders.af2"));
               return;
            }

            if (Config.isFastRender()) {
               Config.showGuiMessage(Lang.get("of.message.shaders.fr1"), Lang.get("of.message.shaders.fr2"));
               return;
            }

            if (Config.getGameSettings().anaglyph) {
               Config.showGuiMessage(Lang.get("of.message.shaders.an1"), Lang.get("of.message.shaders.an2"));
               return;
            }

            this.mc.gameSettings.saveOptions();
            GuiShaders guiShaders = new GuiShaders(this, this.guiGameSettings);
            this.mc.displayGuiScreen((GuiScreen)guiShaders);
         }
      }
   }

   @Override
   public void initGui() {
      this.screenTitle = I18n.format("options.videoTitle", new Object[0]);
      this.buttonList.clear();

      this.cardW = Math.min(358, Math.max(300, this.width - 32));
      int padX = 14;
      int padTop = 14;
      int padBottom = 14;
      int colGap = 10;
      int btnH = 21;
      int rowStep = 26;
      int fullW = this.cardW - padX * 2;
      int colW = (fullW - colGap) / 2;

      int optionRows = (videoOptions.length + 1) / 2;
      int categoryRows = 3;
      this.cardH = padTop + 26 + (optionRows + categoryRows) * rowStep + 6 + btnH + padBottom;
      this.cardX = (this.width - this.cardW) / 2;
      this.cardY = Math.max(10, (this.height - this.cardH) / 2 - 4);
      this.headerY = this.cardY + padTop;
      this.dividerY = this.headerY + 18;

      int leftX = this.cardX + padX;
      int rightX = leftX + colW + colGap;
      int startY = this.dividerY + 8;

      for (int i = 0; i < videoOptions.length; i++) {
         GameSettings.Options options = videoOptions[i];
         if (options != null) {
            int n = i % 2 == 0 ? leftX : rightX;
            int n2 = startY + rowStep * (i / 2);
            if (options.getEnumFloat()) {
               GuiOptionSliderOF slider = new GuiOptionSliderOF(options.returnEnumOrdinal(), n, n2, options);
               slider.width = colW;
               slider.height = btnH;
               this.buttonList.add(slider);
            } else {
               GuiOptionButtonOF optBtn = new GuiOptionButtonOF(options.returnEnumOrdinal(), n, n2, options, this.guiGameSettings.getKeyBinding(options));
               optBtn.width = colW;
               optBtn.height = btnH;
               this.buttonList.add(optBtn);
            }
         }
      }

      int catY = startY + rowStep * optionRows + 2;
      this.buttonList.add(new GuiOptionButton(231, leftX, catY, colW, btnH, Lang.get("of.options.shaders")).withIcon(LucideIcon.SPARKLES));
      this.buttonList.add(new GuiOptionButton(202, rightX, catY, colW, btnH, Lang.get("of.options.quality")).withIcon(LucideIcon.SLIDERS_HORIZONTAL));
      catY += rowStep;
      this.buttonList.add(new GuiOptionButton(201, leftX, catY, colW, btnH, Lang.get("of.options.details")).withIcon(LucideIcon.LIST));
      this.buttonList.add(new GuiOptionButton(212, rightX, catY, colW, btnH, Lang.get("of.options.performance")).withIcon(LucideIcon.ZAP));
      catY += rowStep;
      this.buttonList.add(new GuiOptionButton(211, leftX, catY, colW, btnH, Lang.get("of.options.animations")).withIcon(LucideIcon.WIND));
      this.buttonList.add(new GuiOptionButton(222, rightX, catY, colW, btnH, Lang.get("of.options.other")).withIcon(LucideIcon.SETTINGS));
      catY += rowStep + 4;
      this.buttonList.add(new GuiButton(200, leftX, catY, fullW, btnH, I18n.format("gui.done", new Object[0])).withIcon(LucideIcon.CHECK).withPrimaryAccent(true));
   }

   public static int getButtonHeight(GuiButton button) {
      return button.height;
   }

   public static String getGuiChatText(GuiChat guiChat) {
      return guiChat.inputField.getText();
   }

   public static void drawGradientRect(GuiScreen screen, int n, int n2, int n3, int n4, int n5, int n6) {
      screen.drawGradientRect(n, n2, n3, n4, n5, n6);
   }

}
