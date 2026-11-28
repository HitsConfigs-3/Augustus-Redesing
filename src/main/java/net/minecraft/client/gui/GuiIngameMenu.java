package net.minecraft.client.gui;

import wdl.WDLHooks;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import java.io.IOException;
import net.augustus.Augustus;
import net.augustus.ClientManagers;
import net.augustus.ui.VanillaScreenText;
import net.augustus.ui.menu.AbstractMainMenuScreen;
import net.augustus.ui.menu.ClientTabContainerScreen;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.MenuScreenSkin;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.ui.multiplayer.ServerListHost;
import net.minecraft.client.gui.achievement.GuiAchievements;
import net.minecraft.client.gui.achievement.GuiStats;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.OldServerPinger;
import net.minecraft.client.resources.I18n;

public class GuiIngameMenu extends GuiScreen implements ServerListHost {
   private ServerData reconnectServerData;
   private static final OldServerPinger oldServerPinger = new OldServerPinger();
   private String hoveringText;

   private int cardX;
   private int cardY;
   private int cardW;
   private int cardH;
   private int headerY;
   private int dividerY;
   private int levelBoxX;
   private int levelBoxY;
   private int levelBoxW;
   private int levelBoxH;
   private boolean showLevelBlock;

   @Override
   public void drawScreen(int mouseX, int mouseY, float partialTicks) {
      UiAnimation.beginFrame();
      this.drawDefaultBackground();

      MenuScreenSkin.drawGlassContainer((float)this.cardX, (float)this.cardY, (float)this.cardW, (float)this.cardH, 10.0F);
      VanillaScreenText.drawHeader(this.width, I18n.format("menu.game", new Object[0]), LucideIcon.SLIDERS_HORIZONTAL, this.headerY);
      GlassRenderer.drawSeparator(
         (float)(this.cardX + 14),
         (float)this.dividerY,
         (float)(this.cardX + this.cardW - 14),
         (float)this.dividerY,
         1.0F,
         UiTheme.GLASS_DIVIDER
      );

      if (this.showLevelBlock) {
         this.drawLevelProgressCard(mouseX, mouseY);
      }

      super.drawScreen(mouseX, mouseY, partialTicks);
   }

   private void drawLevelProgressCard(int mouseX, int mouseY) {
      net.augustus.irc.IrcManager irc = net.augustus.irc.IrcManager.getInstance();
      long playMs = irc.getPlayTimeMillis();
      int currentLevel = net.augustus.irc.ClientLevelSystem.levelForPlayTime(playMs);
      int nextLevel = currentLevel + 1;
      float targetProgress = net.augustus.irc.ClientLevelSystem.progressWithinLevel(playMs);
      float animProgress = UiAnimation.smoothState("ingame.level.progress", targetProgress, 10.0F);
      long remainingMs = net.augustus.irc.ClientLevelSystem.millisToNextLevel(playMs);
      int pct = Math.max(0, Math.min(100, Math.round(targetProgress * 100.0F)));

      float x = (float)this.levelBoxX;
      float y = (float)this.levelBoxY;
      float w = (float)this.levelBoxW;
      float h = (float)this.levelBoxH;
      boolean hovered = mouseX >= this.levelBoxX && mouseX <= this.levelBoxX + this.levelBoxW
         && mouseY >= this.levelBoxY && mouseY <= this.levelBoxY + this.levelBoxH;
      float hoverT = UiTheme.smoothState("ingame.level.hover", hovered ? 1.0F : 0.0F);

      int accent = UiTheme.accentColor();
      MenuScreenSkin.drawGlassContainer(x, y, w, h, 8.0F);
      if (hoverT > 0.01F) {
         GlassRenderer.drawRoundedOutline(
            x, y, w, h, 8.0F, UiTheme.BORDER_THICKNESS,
            UiTheme.scaleAlpha(UiTheme.withAlpha(UiTheme.lerpArgb(accent, 0xFFFFFFFF, 0.25F), 185), hoverT)
         );
      }

      float pad = 8.0F;
      net.augustus.ui.modern.font.InterFontUtil titleFont = net.augustus.font.FontManager.uiSmall();
      net.augustus.ui.modern.font.InterFontUtil captionFont = net.augustus.font.FontManager.uiCaption();

      float contentX = x + pad;
      float contentW = w - pad * 2.0F;
      float topRowY = y + 5.5F;

      String lvlText = "Nivel " + currentLevel;
      String nextText = "Nv." + nextLevel;
      String remText = pct + "%  •  Falta " + net.augustus.irc.ClientLevelSystem.formatDuration(remainingMs);

      if (titleFont != null && captionFont != null) {
         titleFont.drawStringWithShadow(lvlText, (double)contentX, (double)topRowY, 0xFFF8FAFC);
         float afterLvl = contentX + titleFont.getStringWidth(lvlText) + 5.0F;
         LucideIcon.ARROW_RIGHT.draw(afterLvl, topRowY + 1.5F, 7.5F, UiTheme.lerpArgb(accent, 0xFFFFFFFF, 0.35F), false);
         captionFont.drawStringWithShadow(nextText, (double)(afterLvl + 10.0F), (double)(topRowY + 1.0F), UiTheme.lerpArgb(accent, 0xFFFFFFFF, 0.40F));

         float remW = captionFont.getStringWidth(remText);
         captionFont.drawStringWithShadow(remText, (double)(contentX + contentW - remW), (double)(topRowY + 1.0F), 0xFFB8C2D4);
      } else {
         String fallbackLeft = lvlText + " -> " + nextText;
         this.fontRendererObj.drawStringWithShadow(fallbackLeft, contentX, topRowY, 0xFFF8FAFC);
         this.fontRendererObj.drawStringWithShadow(remText, contentX + contentW - (float)this.fontRendererObj.getStringWidth(remText), topRowY, 0xFFB8C2D4);
      }

      float barY = y + h - 9.5F;
      float barH = 4.5F;
      GlassRenderer.drawRoundedRect(contentX, barY, contentW, barH, 2.5F, 0x9906080F);
      GlassRenderer.drawRoundedOutline(contentX, barY, contentW, barH, 2.5F, 0.75F, UiTheme.withAlpha(accent, 75));
      float fillW = animProgress > 0.005F ? Math.max(5.0F, contentW * animProgress) : 0.0F;
      if (fillW > 0.0F) {
         GlassRenderer.drawRoundedGradientBoxHorizontal(
            contentX, barY, Math.min(contentW, fillW), barH, 2.5F,
            UiTheme.withAlpha(accent, 235),
            UiTheme.withAlpha(UiTheme.accentSoftGlow(), 250),
            1.0F
         );
      }
   }

   @Override
   public void initGui() {
      this.buttonList.clear();
      boolean hideCustomMenu = ClientManagers.moduleManager.invisMode.isToggled()
         && ClientManagers.moduleManager.invisMode.shouldDisableCustomEscapeMenu();

      this.cardW = 256;
      int padX = 14;
      int padTop = 14;
      int padBottom = 14;
      int colGap = 8;
      int rowGap = 7;
      int btnH = 22;
      int fullW = this.cardW - padX * 2;
      int halfW = (fullW - colGap) / 2;

      int headerH = 24;
      int leftX = this.cardX + padX;
      int rightX = leftX + halfW + colGap;
      int rowStep = btnH + rowGap;

      // Button index 0 must remain the quit/return button because WDLHooks expects get(0) when injecting.
      String quitLabel = this.mc.isIntegratedServerRunning()
         ? I18n.format("menu.returnToMenu", new Object[0])
         : I18n.format("menu.disconnect", new Object[0]);
      GuiButton quitBtn = new GuiButton(1, leftX, 0, fullW, btnH, quitLabel).withIcon(LucideIcon.LOGOUT);
      this.buttonList.add(quitBtn);

      GuiButton backBtn = new GuiButton(4, leftX, 0, fullW, btnH, I18n.format("menu.returnToGame", new Object[0]))
         .withIcon(LucideIcon.PLAYER_PLAY)
         .withPrimaryAccent(true);
      this.buttonList.add(backBtn);

      GuiButton achBtn = new GuiButton(5, leftX, 0, halfW, btnH, I18n.format("gui.achievements", new Object[0])).withIcon(LucideIcon.SPARKLES);
      GuiButton statsBtn = new GuiButton(6, rightX, 0, halfW, btnH, I18n.format("gui.stats", new Object[0])).withIcon(LucideIcon.ACTIVITY);
      this.buttonList.add(achBtn);
      this.buttonList.add(statsBtn);

      GuiButton augBtn = null;
      GuiButton directBtn = null;
      if (!hideCustomMenu) {
         augBtn = new GuiButton(1000, leftX, 0, halfW, btnH, Augustus.getInstance().getName()).withIcon(LucideIcon.SHIELD);
         directBtn = new GuiButton(1001, rightX, 0, halfW, btnH, I18n.format("selectServer.direct", new Object[0])).withIcon(LucideIcon.LINK);
         this.buttonList.add(augBtn);
         this.buttonList.add(directBtn);
      }

      GuiButton optBtn = new GuiButton(0, leftX, 0, halfW, btnH, I18n.format("menu.options", new Object[0])).withIcon(LucideIcon.SETTINGS);
      GuiButton lanBtn = new GuiButton(7, rightX, 0, halfW, btnH, I18n.format("menu.shareToLan", new Object[0])).withIcon(LucideIcon.BROADCAST);
      lanBtn.enabled = this.mc.isSingleplayer() && !this.mc.getIntegratedServer().getPublic();
      this.buttonList.add(optBtn);
      this.buttonList.add(lanBtn);

      int beforeWdl = this.buttonList.size();
      if (!hideCustomMenu) {
         WDLHooks.injectWDLButtons(this, this.buttonList);
      }
      boolean hasWdl = this.buttonList.size() > beforeWdl;

      int rows = 4 + (hideCustomMenu ? 0 : 1) + (hasWdl ? 1 : 0);
      this.showLevelBlock = !hideCustomMenu;
      this.cardH = padTop + headerH + rows * btnH + (rows - 1) * rowGap + padBottom;
      this.cardX = (this.width - this.cardW) / 2;
      if (this.showLevelBlock) {
         this.levelBoxW = this.cardW;
         this.levelBoxH = 29;
         this.levelBoxX = this.cardX;
         this.levelBoxY = 12;
         int centeredY = (this.height - this.cardH) / 2;
         this.cardY = Math.max(this.levelBoxY + this.levelBoxH + 10, centeredY);
      } else {
         this.cardY = Math.max(16, (this.height - this.cardH) / 2);
      }
      this.headerY = this.cardY + padTop;
      this.dividerY = this.headerY + 18;

      leftX = this.cardX + padX;
      rightX = leftX + halfW + colGap;
      int y = this.dividerY + 8;

      backBtn.xPosition = leftX;
      backBtn.yPosition = y;
      y += rowStep;

      achBtn.xPosition = leftX;
      achBtn.yPosition = y;
      statsBtn.xPosition = rightX;
      statsBtn.yPosition = y;
      y += rowStep;

      if (hasWdl) {
         int gearW = 24;
         int statusW = fullW - gearW - colGap;
         for (int i = beforeWdl; i < this.buttonList.size(); i++) {
            GuiButton wdlBtn = (GuiButton)this.buttonList.get(i);
            wdlBtn.yPosition = y;
            wdlBtn.height = btnH;
            if (wdlBtn.id == 1464093807 || wdlBtn.width <= 40) {
               wdlBtn.xPosition = leftX + statusW + colGap;
               wdlBtn.width = gearW;
               wdlBtn.displayString = "";
               wdlBtn.withIcon(LucideIcon.SETTINGS);
            } else {
               wdlBtn.xPosition = leftX;
               wdlBtn.width = statusW;
               wdlBtn.withIcon(LucideIcon.DOWNLOAD);
            }
         }
         y += rowStep;
      }

      if (augBtn != null && directBtn != null) {
         augBtn.xPosition = leftX;
         augBtn.yPosition = y;
         directBtn.xPosition = rightX;
         directBtn.yPosition = y;
         y += rowStep;
      }

      optBtn.xPosition = leftX;
      optBtn.yPosition = y;
      lanBtn.xPosition = rightX;
      lanBtn.yPosition = y;
      y += rowStep;

      quitBtn.xPosition = leftX;
      quitBtn.yPosition = y;
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      if (mouseButton == 0 && this.showLevelBlock
         && mouseX >= this.levelBoxX && mouseX <= this.levelBoxX + this.levelBoxW
         && mouseY >= this.levelBoxY && mouseY <= this.levelBoxY + this.levelBoxH) {
         int ircIdx = Augustus.getInstance().getClientTabManager().indexOf(net.augustus.ui.menu.IrcMenuTab.class);
         this.mc.displayGuiScreen(new ClientTabContainerScreen(this, ircIdx));
         return;
      }
      super.mouseClicked(mouseX, mouseY, mouseButton);
   }

   public OldServerPinger getOldServerPinger() {
      return oldServerPinger;
   }

   public void renderHoveringText(GuiScreen screen, int mouseX, int mouseY) {
      if (this.hoveringText != null) {
         screen.drawHoveringText(Lists.newArrayList(Splitter.on("\n").split(this.hoveringText)), mouseX, mouseY);
      }
   }

   @Override
   public void actionPerformed(GuiButton button) throws IOException {
      WDLHooks.handleWDLButtonClick(this, button);
      switch (button.id) {
         case 0:
            this.mc.displayGuiScreen(new GuiOptions(this, this.mc.gameSettings));
            break;
         case 1:
            button.enabled = false;
            this.disconnectFromWorld();
            break;
         case 4:
            this.mc.displayGuiScreen(null);
            this.mc.setIngameFocus();
            break;
         case 5:
            this.mc.displayGuiScreen(new GuiAchievements(this, this.mc.thePlayer.getStatFileWriter()));
            break;
         case 6:
            this.mc.displayGuiScreen(new GuiStats(this, this.mc.thePlayer.getStatFileWriter()));
            break;
         case 7:
            this.mc.displayGuiScreen(new GuiShareToLan(this));
            break;
         case 1000:
            this.mc.displayGuiScreen(new ClientTabContainerScreen(this, 0));
            break;
         case 1001:
            this.mc.displayGuiScreen(new GuiScreenServerList(
               this, this.reconnectServerData = new ServerData("", this.mc.isSingleplayer() ? "" : this.mc.getCurrentServerData().serverIP, false)
            ));
            break;
         default:
            break;
      }
   }

   private void disconnectFromWorld() {
      boolean singleplayer = this.mc.isIntegratedServerRunning();
      this.mc.theWorld.sendQuittingDisconnectingPacket();
      this.mc.loadWorld(null);
      if (singleplayer) {
         this.mc.displayGuiScreen(new GuiMainMenu());
      } else {
         this.mc.displayGuiScreen(new GuiMultiplayer(new GuiMainMenu()));
      }
   }

   @Override
   public void confirmClicked(boolean result, int id) {
      if (result) {
         this.disconnectFromWorld();
         this.mc.displayGuiScreen(new GuiConnecting(new GuiMultiplayer(AbstractMainMenuScreen.createMainMenuScreen()), this.mc, this.reconnectServerData));
      } else {
         this.mc.displayGuiScreen(this);
      }
   }

   public void setHoveringText(String text) {
      this.hoveringText = text;
   }

   @Override
   public void updateScreen() {
      super.updateScreen();
   }
}
