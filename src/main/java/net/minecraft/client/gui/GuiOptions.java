package net.minecraft.client.gui;

import net.augustus.ui.VanillaScreenText;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.MenuScreenSkin;
import net.augustus.ui.modern.render.UiTheme;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.audio.SoundEventAccessorComposite;
import net.minecraft.client.audio.SoundHandler;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.EnumDifficulty;

public class GuiOptions extends GuiScreen implements GuiYesNoCallback {
   private static final GameSettings.Options[] OPTIONS = new GameSettings.Options[]{GameSettings.Options.FOV};
   private final GameSettings game_settings_1;
   private GuiButton difficultyButton;
   private GuiLockIconButton lockButton;
   public String title = "Options";
   private int cardX;
   private int cardY;
   private int cardW;
   private int cardH;
   private int headerY;
   private int dividerY;

   @Override
   public void initGui() {
      this.buttonList.clear();
      this.title = I18n.format("options.title", new Object[0]);

      this.cardW = Math.min(352, Math.max(296, this.width - 32));
      int padX = 14;
      int padTop = 14;
      int padBottom = 14;
      int colGap = 10;
      int btnH = 22;
      int rowStep = 28;
      int fullW = this.cardW - padX * 2;
      int colW = (fullW - colGap) / 2;

      this.cardH = padTop + 26 + rowStep * 5 + 12 + btnH + padBottom;
      this.cardX = (this.width - this.cardW) / 2;
      this.cardY = Math.max(12, (this.height - this.cardH) / 2);
      this.headerY = this.cardY + padTop;
      this.dividerY = this.headerY + 18;

      int leftX = this.cardX + padX;
      int rightX = leftX + colW + colGap;
      int topRowY = this.dividerY + 9;

      int n = 0;
      for (GameSettings.Options options : OPTIONS) {
         int bx = leftX + (n % 2) * (colW + colGap);
         int by = topRowY + rowStep * (n >> 1);
         if (options.getEnumFloat()) {
            GuiOptionSlider slider = new GuiOptionSlider(options.returnEnumOrdinal(), bx, by, options);
            slider.width = colW;
            slider.height = btnH;
            this.buttonList.add(slider);
         } else {
            GuiOptionButton guiOptionButton = new GuiOptionButton(
               options.returnEnumOrdinal(),
               bx,
               by,
               options,
               this.game_settings_1.getKeyBinding(options)
            );
            guiOptionButton.width = colW;
            guiOptionButton.height = btnH;
            this.buttonList.add(guiOptionButton);
         }
         n++;
      }

      if (this.mc.theWorld != null) {
         EnumDifficulty difficulty = this.mc.theWorld.getDifficulty();
         int dx = leftX + (n % 2) * (colW + colGap);
         int dy = topRowY + rowStep * (n >> 1);
         this.difficultyButton = new GuiButton(108, dx, dy, colW, btnH, this.getDifficultyText(difficulty)).withIcon(LucideIcon.SHIELD);
         this.buttonList.add(this.difficultyButton);
         if (this.mc.isSingleplayer() && !this.mc.theWorld.getWorldInfo().isHardcoreModeEnabled()) {
            this.difficultyButton.setWidth(colW - btnH - 4);
            this.lockButton = new GuiLockIconButton(109, this.difficultyButton.xPosition + this.difficultyButton.getButtonWidth() + 4, dy);
            this.lockButton.width = btnH;
            this.lockButton.height = btnH;
            this.buttonList.add(this.lockButton);
            this.lockButton.setLocked(this.mc.theWorld.getWorldInfo().isDifficultyLocked());
            this.lockButton.enabled = !this.lockButton.isLocked();
            this.difficultyButton.enabled = !this.lockButton.isLocked();
         } else {
            this.difficultyButton.enabled = false;
         }
      }

      int gridY = topRowY + rowStep + 4;
      this.buttonList.add(new GuiButton(110, leftX, gridY, colW, btnH, I18n.format("options.skinCustomisation", new Object[0])).withIcon(LucideIcon.SHIRT));
      this.buttonList.add(new GuiOptions.DefaultGuiButton(this, 8675309, rightX, gridY, colW, btnH, "Super Secret Settings...").withIcon(LucideIcon.EYE));

      gridY += rowStep;
      this.buttonList.add(new GuiButton(101, leftX, gridY, colW, btnH, I18n.format("options.video", new Object[0])).withIcon(LucideIcon.DEVICE_DESKTOP));
      this.buttonList.add(new GuiButton(106, rightX, gridY, colW, btnH, I18n.format("options.sounds", new Object[0])).withIcon(LucideIcon.BROADCAST));

      gridY += rowStep;
      this.buttonList.add(new GuiButton(102, leftX, gridY, colW, btnH, I18n.format("options.language", new Object[0])).withIcon(LucideIcon.GLOBE));
      this.buttonList.add(new GuiButton(100, rightX, gridY, colW, btnH, I18n.format("options.controls", new Object[0])).withIcon(LucideIcon.KEYBOARD));

      gridY += rowStep;
      this.buttonList.add(new GuiButton(105, leftX, gridY, colW, btnH, I18n.format("options.resourcepack", new Object[0])).withIcon(LucideIcon.BOXES));
      this.buttonList.add(new GuiButton(103, rightX, gridY, colW, btnH, I18n.format("options.chat.title", new Object[0])).withIcon(LucideIcon.USERS_GROUP));

      gridY += rowStep + 6;
      this.buttonList.add(new GuiButton(200, leftX, gridY, fullW, btnH, I18n.format("gui.done", new Object[0])).withIcon(LucideIcon.CHECK).withPrimaryAccent(true));
   }

   @Override
   public void drawScreen(int mouseX, int mouseY, float partialTicks) {
      this.drawDefaultBackground();
      MenuScreenSkin.drawGlassContainer((float)this.cardX, (float)this.cardY, (float)this.cardW, (float)this.cardH, 10.0F);
      VanillaScreenText.drawHeader(this.width, this.title, LucideIcon.SETTINGS, this.headerY);
      GlassRenderer.drawSeparator(
         (float)(this.cardX + 14),
         (float)this.dividerY,
         (float)(this.cardX + this.cardW - 14),
         (float)this.dividerY,
         1.0F,
         UiTheme.GLASS_DIVIDER
      );
      super.drawScreen(mouseX, mouseY, partialTicks);
   }

   @Override
   public void actionPerformed(GuiButton button) throws IOException {
      if (button.enabled) {
         if (button.id < 100 && button instanceof GuiOptionButton) {
            GameSettings.Options options = ((GuiOptionButton)button).returnEnumOptions();
            this.game_settings_1.setOptionValue(options, 1);
            button.displayString = this.game_settings_1.getKeyBinding(GameSettings.Options.getEnumOptions(button.id));
         }

         if (button.id == 108) {
            this.mc.theWorld.getWorldInfo().setDifficulty(EnumDifficulty.getDifficultyEnum(this.mc.theWorld.getDifficulty().getDifficultyId() + 1));
            this.difficultyButton.displayString = this.getDifficultyText(this.mc.theWorld.getDifficulty());
         }

         if (button.id == 109) {
            Minecraft mc2 = this.mc;
            GuiYesNo guiYesNo = new GuiYesNo(
               this,
               new ChatComponentTranslation("difficulty.lock.title", new Object[0]).getFormattedText(),
               new ChatComponentTranslation(
                     "difficulty.lock.question",
                     new Object[]{new ChatComponentTranslation(this.mc.theWorld.getWorldInfo().getDifficulty().getDifficultyResourceKey(), new Object[0])}
                  )
                  .getFormattedText(),
               109
            );
            mc2.displayGuiScreen(guiYesNo);
         }

         if (button.id == 110) {
            this.mc.gameSettings.saveOptions();
            this.mc.displayGuiScreen(new GuiCustomizeSkin(this));
         }

         if (button.id == 8675309) {
            this.mc.entityRenderer.activateNextShader();
         }

         if (button.id == 101) {
            this.mc.gameSettings.saveOptions();
            this.mc.displayGuiScreen(new GuiVideoSettings(this, this.game_settings_1));
         }

         if (button.id == 100) {
            this.mc.gameSettings.saveOptions();
            this.mc.displayGuiScreen(new GuiControls(this, this.game_settings_1));
         }

         if (button.id == 102) {
            this.mc.gameSettings.saveOptions();
            this.mc.displayGuiScreen(new GuiLanguage(this, this.game_settings_1, this.mc.getLanguageManager()));
         }

         if (button.id == 103) {
            this.mc.gameSettings.saveOptions();
            this.mc.displayGuiScreen(new ScreenChatOptions(this, this.game_settings_1));
         }

         if (button.id == 200) {
            this.mc.gameSettings.saveOptions();
            GuiScreen parentScreen2 = this.parentScreen;
            this.mc.displayGuiScreen(parentScreen2);
         }

         if (button.id == 105) {
            this.mc.gameSettings.saveOptions();
            this.mc.displayGuiScreen(new GuiScreenResourcePacks(this));
         }

         if (button.id == 106) {
            this.mc.gameSettings.saveOptions();
            this.mc.displayGuiScreen(new GuiScreenOptionsSounds(this, this.game_settings_1));
         }
      }
   }

   public GuiOptions(GuiScreen screen, GameSettings gameSettings) {
      super(screen);
      this.game_settings_1 = gameSettings;
      GuiScreen.NO_TOGGLE_SCREENS.add(this);
   }

   @Override
   public void confirmClicked(boolean flag, int n) {
      this.mc.displayGuiScreen(this);
      if (n == 109 && flag && this.mc.theWorld != null) {
         this.mc.theWorld.getWorldInfo().setDifficultyLocked(true);
         this.lockButton.setLocked(true);
         this.lockButton.enabled = false;
         this.difficultyButton.enabled = false;
      }
   }

   public String getDifficultyText(EnumDifficulty enumDifficulty) {
      ChatComponentText chatComponentText = new ChatComponentText("");
      chatComponentText.appendSibling(new ChatComponentTranslation("options.difficulty", new Object[0]));
      chatComponentText.appendText(": ");
      chatComponentText.appendSibling(new ChatComponentTranslation(enumDifficulty.getDifficultyResourceKey(), new Object[0]));
      return chatComponentText.getFormattedText();
   }

   public static class DefaultGuiButton extends GuiButton {
      public final GuiOptions optionsScreen;

      public DefaultGuiButton(GuiOptions guiOptions, int n, int n2, int n3, int n4, int n5, String str) {
         super(n, n2, n3, n4, n5, str);
         this.optionsScreen = guiOptions;
      }

      @Override
      public void playPressSound(SoundHandler soundHandler) {
         SoundEventAccessorComposite composite = soundHandler.getRandomSoundFromCategories(
            new SoundCategory[]{SoundCategory.ANIMALS, SoundCategory.BLOCKS, SoundCategory.MOBS, SoundCategory.PLAYERS, SoundCategory.WEATHER}
         );
         if (composite != null) {
            soundHandler.playSound(PositionedSoundRecord.create(composite.getSoundEventLocation(), 0.5F));
         }
      }
   }
}
