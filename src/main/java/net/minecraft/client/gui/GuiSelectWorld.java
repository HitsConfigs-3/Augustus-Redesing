package net.minecraft.client.gui;

import java.util.Date;
import net.augustus.font.FontManager;
import net.augustus.ui.VanillaScreenText;
import net.augustus.ui.modern.font.InterFontUtil;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.MenuScreenSkin;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;
import net.minecraft.util.EnumChatFormatting;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import net.augustus.modules.misc.ClientSettings;
import net.minecraft.client.AnvilConverterException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.ISaveFormat;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.SaveFormatComparator;
import net.minecraft.world.storage.WorldInfo;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GuiSelectWorld extends GuiScreen implements GuiYesNoCallback {
   private static final Logger LOGGER2 = LogManager.getLogger();
   private final DateFormat dateFormat = new SimpleDateFormat();
   public String screenTitle = "Select world";
   private boolean selected;
   private int selectedIndex;
   private java.util.List saveList;
   private GuiSelectWorld.List availableWorlds;
   private String worldText;
   private String conversionText;
   private String[] gameModeNames = new String[4];
   private boolean confirmingDelete;
   private GuiButton deleteButton;
   private GuiButton selectButton;
   private GuiButton renameButton;
   private GuiButton recreateButton;

   public static String[] accessGameModeNames(GuiSelectWorld guiSelectWorld) {
      return guiSelectWorld.gameModeNames;
   }

   public static GuiButton getDeleteButton(GuiSelectWorld guiSelectWorld) {
      return guiSelectWorld.deleteButton;
   }

   public static GuiButton getRecreateButton(GuiSelectWorld guiSelectWorld) {
      return guiSelectWorld.recreateButton;
   }

   public String getSaveName(int n) {
      String displayName = ((SaveFormatComparator)this.saveList.get(n)).getDisplayName();
      if (StringUtils.isEmpty(displayName)) {
         displayName = I18n.format("selectWorld.world", new Object[0]) + " " + (n + 1);
      }

      return displayName;
   }

   public static java.util.List accessSaveList(GuiSelectWorld guiSelectWorld) {
      return guiSelectWorld.saveList;
   }

   public String getSaveFileName(int n) {
      return ((SaveFormatComparator)this.saveList.get(n)).getFileName();
   }

   @Override
   public void drawScreen(int mouseX, int mouseY, float partialTicks) {
      this.availableWorlds.drawScreen(mouseX, mouseY, partialTicks);
      VanillaScreenText.drawHeader(this.width, this.screenTitle, LucideIcon.GLOBE, 16);
      super.drawScreen(mouseX, mouseY, partialTicks);
      ClientSettings.renderMainMenuAccountInfo();
   }

   public void addWorldSelectionButtons() {
      int barW = Math.min(430, Math.max(296, this.width - 72));
      int leftX = (this.width - barW) / 2;
      int gap = 6;
      int topBtnW = (barW - gap) / 2;
      int botBtnW = (barW - gap * 3) / 4;
      int rem = barW - botBtnW * 4 - gap * 3;
      int topY = this.height - 54;
      int botY = this.height - 30;
      int btnH = 20;

      this.buttonList.add(
         (this.selectButton = new GuiButton(1, leftX, topY, topBtnW, btnH, I18n.format("selectWorld.select", new Object[0])))
            .withIcon(LucideIcon.PLAYER_PLAY)
            .withPrimaryAccent(true)
      );
      this.buttonList.add(new GuiButton(3, leftX + topBtnW + gap, topY, barW - topBtnW - gap, btnH, I18n.format("selectWorld.create", new Object[0])).withIcon(LucideIcon.PLUS));
      this.buttonList.add((this.renameButton = new GuiButton(6, leftX, botY, botBtnW, btnH, I18n.format("selectWorld.rename", new Object[0]))).withIcon(LucideIcon.FILE_TEXT));
      this.buttonList.add((this.deleteButton = new GuiButton(2, leftX + botBtnW + gap, botY, botBtnW, btnH, I18n.format("selectWorld.delete", new Object[0]))).withIcon(LucideIcon.TRASH_2));
      this.buttonList.add((this.recreateButton = new GuiButton(7, leftX + (botBtnW + gap) * 2, botY, botBtnW, btnH, I18n.format("selectWorld.recreate", new Object[0]))).withIcon(LucideIcon.REFRESH_CW));
      this.buttonList.add(new GuiButton(0, leftX + (botBtnW + gap) * 3, botY, botBtnW + rem, btnH, I18n.format("gui.cancel", new Object[0])).withIcon(LucideIcon.ARROW_LEFT));
      this.selectButton.enabled = false;
      this.deleteButton.enabled = false;
      this.renameButton.enabled = false;
      this.recreateButton.enabled = false;
   }

   @Override
   public void actionPerformed(GuiButton button) throws IOException {
      if (button.enabled) {
         if (button.id == 2) {
            String saveName = this.getSaveName(this.selectedIndex);
            if (saveName != null) {
               this.confirmingDelete = true;
               GuiYesNo guiYesNo = makeDeleteWorldYesNo(this, saveName, this.selectedIndex);
               this.mc.displayGuiScreen(guiYesNo);
            }
         } else if (button.id == 1) {
            this.selectWorld(this.selectedIndex);
         } else if (button.id == 3) {
            this.mc.displayGuiScreen(new GuiCreateWorld(this));
         } else if (button.id == 6) {
            this.mc.displayGuiScreen(new GuiRenameWorld(this, this.getSaveFileName(this.selectedIndex)));
         } else if (button.id == 0) {
            GuiScreen parentScreen2 = this.parentScreen;
            this.mc.displayGuiScreen(parentScreen2);
         } else if (button.id == 7) {
            GuiCreateWorld guiCreateWorld2 = new GuiCreateWorld(this);
            ISaveHandler saveLoader = this.mc.getSaveLoader().getSaveLoader(this.getSaveFileName(this.selectedIndex), false);
            WorldInfo worldInfo = saveLoader.loadWorldInfo();
            saveLoader.flush();
            guiCreateWorld2.recreateFromExistingWorld(worldInfo);
            this.mc.displayGuiScreen(guiCreateWorld2);
         } else {
            this.availableWorlds.actionPerformed(button);
         }
      }
   }

   @Override
   public void initGui() {
      this.screenTitle = I18n.format("selectWorld.title", new Object[0]);

      try {
         this.loadLevelList();
      } catch (AnvilConverterException e) {
         LOGGER2.error("Couldn't load level list", e);
         this.mc.displayGuiScreen((GuiScreen)new GuiErrorScreen("Unable to load worlds", e.getMessage()));
         return;
      }

      this.worldText = I18n.format("selectWorld.world", new Object[0]);
      this.conversionText = I18n.format("selectWorld.conversion", new Object[0]);
      this.gameModeNames[WorldSettings.GameType.SURVIVAL.getID()] = I18n.format("gameMode.survival", new Object[0]);
      this.gameModeNames[WorldSettings.GameType.CREATIVE.getID()] = I18n.format("gameMode.creative", new Object[0]);
      this.gameModeNames[WorldSettings.GameType.ADVENTURE.getID()] = I18n.format("gameMode.adventure", new Object[0]);
      this.gameModeNames[WorldSettings.GameType.SPECTATOR.getID()] = I18n.format("gameMode.spectator", new Object[0]);
      this.availableWorlds = new GuiSelectWorld.List(this, this.mc);
      this.availableWorlds.registerScrollButtons(4, 5);
      this.addWorldSelectionButtons();
   }

   public static DateFormat accessDateFormat(GuiSelectWorld guiSelectWorld) {
      return guiSelectWorld.dateFormat;
   }

   public static GuiYesNo makeDeleteWorldYesNo(GuiYesNoCallback guiYesNoCallback, String str, int n) {
      String str2 = I18n.format("selectWorld.deleteQuestion", new Object[0]);
      String str3 = "'" + str + "' " + I18n.format("selectWorld.deleteWarning", new Object[0]);
      String str4 = I18n.format("selectWorld.deleteButton", new Object[0]);
      String str5 = I18n.format("gui.cancel", new Object[0]);
      return new GuiYesNo(guiYesNoCallback, str2, str3, str4, str5, n);
   }

   public static int assignSelectedIndex(GuiSelectWorld guiSelectWorld, int n) {
      return guiSelectWorld.selectedIndex = n;
   }

   public static GuiButton getRenameButton(GuiSelectWorld guiSelectWorld) {
      return guiSelectWorld.renameButton;
   }

   public static String getConversionText(GuiSelectWorld guiSelectWorld) {
      return guiSelectWorld.conversionText;
   }

   private void loadLevelList() throws AnvilConverterException {
      this.saveList = this.mc.getSaveLoader().getSaveList();
      Collections.sort(this.saveList);
      this.selectedIndex = -1;
   }

   public static GuiButton getSelectButton(GuiSelectWorld guiSelectWorld) {
      return guiSelectWorld.selectButton;
   }

   public GuiSelectWorld(GuiScreen screen) {
      super(screen);
   }

   public void selectWorld(int n) {
      if (!this.selected) {
         this.selected = true;
         String saveFileName = this.getSaveFileName(n);
         if (saveFileName == null) {
            saveFileName = "World" + n;
         }

         String saveName = this.getSaveName(n);
         if (saveName == null) {
            saveName = "World" + n;
         }

         if (this.mc.getSaveLoader().canLoadWorld(saveFileName)) {
            WorldSettings worldSettings = (WorldSettings)null;
            this.mc.launchIntegratedServer(saveFileName, saveName, worldSettings);
         }
      }
   }

   @Override
   public void confirmClicked(boolean flag, int n) {
      if (this.confirmingDelete) {
         this.confirmingDelete = false;
         if (flag) {
            ISaveFormat saveLoader = this.mc.getSaveLoader();
            saveLoader.flushCache();
            saveLoader.deleteWorldDirectory(this.getSaveFileName(n));

            try {
               this.loadLevelList();
            } catch (AnvilConverterException e) {
               LOGGER2.error("Couldn't load level list", e);
            }
         }

         this.mc.displayGuiScreen(this);
      }
   }

   public static String getWorldText(GuiSelectWorld guiSelectWorld) {
      return guiSelectWorld.worldText;
   }

   public static int accessSelectedIndex(GuiSelectWorld guiSelectWorld) {
      return guiSelectWorld.selectedIndex;
   }

   @Override
   public void handleMouseInput() throws IOException {
      super.handleMouseInput();
      this.availableWorlds.handleMouseInput();
   }



   public static class List extends GuiSlot {
      public final GuiSelectWorld selectWorldScreen;

      public List(GuiSelectWorld guiSelectWorld, Minecraft mc2) {
         super(mc2, guiSelectWorld.width, guiSelectWorld.height, 42, guiSelectWorld.height - 66, 42);
         this.selectWorldScreen = guiSelectWorld;
      }

      @Override
      public int getListWidth() {
         return Math.min(430, Math.max(240, this.selectWorldScreen.width - 72));
      }

      @Override
      public int getScrollBarX() {
         return this.left + (this.width + this.getListWidth()) / 2 + 4;
      }

      @Override
      public int getContentHeight() {
         return GuiSelectWorld.accessSaveList(this.selectWorldScreen).size() * 42;
      }

      @Override
      public void drawBackground() {
         this.selectWorldScreen.drawDefaultBackground();
      }

      @Override
      public void drawSlot(int n, int n2, int n3, int n4, int n5, int n6) {
         SaveFormatComparator saveFormatComparator = (SaveFormatComparator)GuiSelectWorld.accessSaveList(this.selectWorldScreen).get(n);
         String displayName = saveFormatComparator.getDisplayName();
         if (StringUtils.isEmpty(displayName)) {
            displayName = GuiSelectWorld.getWorldText(this.selectWorldScreen) + " " + (n + 1);
         }

         String fileName = saveFormatComparator.getFileName() + " (" + GuiSelectWorld.accessDateFormat(this.selectWorldScreen).format(new Date(saveFormatComparator.getLastTimePlayed())) + ")";
         String modeLine;
         if (saveFormatComparator.requiresConversion()) {
            modeLine = GuiSelectWorld.getConversionText(this.selectWorldScreen);
         } else {
            modeLine = GuiSelectWorld.accessGameModeNames(this.selectWorldScreen)[saveFormatComparator.getEnumGameType().getID()];
            if (saveFormatComparator.isHardcoreModeEnabled()) {
               modeLine = EnumChatFormatting.DARK_RED + I18n.format("gameMode.hardcore", new Object[0]) + EnumChatFormatting.RESET;
            }

            if (saveFormatComparator.getCheatsEnabled()) {
               modeLine = modeLine + ", " + I18n.format("selectWorld.cheats", new Object[0]);
            }
         }

         float rowX = (float)(n2 - 2);
         float rowY = (float)n3;
         float rowW = (float)this.getListWidth();
         float rowH = (float)n4;
         boolean selected = this.isSelected(n);
         boolean hovered = (float)n5 >= rowX && (float)n5 <= rowX + rowW && (float)n6 >= rowY && (float)n6 <= rowY + rowH;
         String hoverKey = "augustus.selectworld.row." + n;
         MenuScreenSkin.drawOptionSurface(
            rowX,
            rowY,
            rowW,
            rowH,
            MenuScreenSkin.optionRadius(rowW, rowH),
            UiTheme.smoothState(hoverKey, hovered ? 1.0F : 0.0F),
            UiTheme.smoothPress(hoverKey + ".press", 0.0F, hovered && UiAnimation.isPrimaryMouseDown()),
            true,
            (float)n5,
            (float)n6,
            UiTheme.accentColor()
         );

         // Leading world emblem tile
         float badgeSize = 26.0F;
         float badgeX = rowX + 10.0F;
         float badgeY = rowY + (rowH - badgeSize) * 0.5F;
         GlassRenderer.drawRoundedGradientBox(badgeX, badgeY, badgeSize, badgeSize, 5.5F,
            selected ? UiTheme.accentColor() : UiTheme.menuPillFillTopArgb(),
            selected ? UiTheme.accentDeep() : UiTheme.menuPillFillBottomArgb(), 0, 0.0F, 0.0F);
         GlassRenderer.drawRoundedOutline(badgeX, badgeY, badgeSize, badgeSize, 5.5F, 1.0F,
            selected ? UiTheme.withAlpha(UiTheme.accentColor(), 200) : UiTheme.GLASS_BORDER);
         LucideIcon.GLOBE.drawCentered(badgeX + badgeSize * 0.5F, badgeY + badgeSize * 0.5F, 13.0F, selected ? 0xFFF8FAFC : UiTheme.withAlpha(UiTheme.accentSoftGlow(), 0xCC));

         float textX = badgeX + badgeSize + 10.0F;
         float maxTextW = Math.max(40.0F, rowW - (textX - rowX) - 12.0F);
         InterFontUtil titleFace = FontManager.uiBody();
         InterFontUtil smallFace = FontManager.uiSmall();

         String fitName = FontManager.uiFit(titleFace, displayName, maxTextW);
         String fitMeta = FontManager.uiFit(smallFace, fileName, maxTextW);
         String fitMode = FontManager.uiFit(smallFace, modeLine, maxTextW);

         VanillaScreenText.drawString(fitName, Math.round(textX), Math.round(rowY + 4.0F), selected ? 0xFFF8FAFC : 16777215);
         VanillaScreenText.drawSmallString(fitMeta, textX, rowY + 15.5F, 0xFFB4BED0);
         VanillaScreenText.drawSmallString(fitMode, textX, rowY + 25.0F, 0xCC94A3B8);

         if (selected) {
            MenuScreenSkin.drawSelectionRing(rowX, rowY, rowW, rowH, MenuScreenSkin.optionRadius(rowW, rowH));
         }
      }

      @Override
      public boolean isSelected(int n) {
         return n == GuiSelectWorld.accessSelectedIndex(this.selectWorldScreen);
      }

      @Override
      public void elementClicked(int n, boolean flag, int n2, int n3) {
         GuiSelectWorld.assignSelectedIndex(this.selectWorldScreen, n);
         boolean flag2 = GuiSelectWorld.accessSelectedIndex(this.selectWorldScreen) >= 0 && GuiSelectWorld.accessSelectedIndex(this.selectWorldScreen) < this.getSize();
         GuiSelectWorld.getSelectButton(this.selectWorldScreen).enabled = flag2;
         GuiSelectWorld.getDeleteButton(this.selectWorldScreen).enabled = flag2;
         GuiSelectWorld.getRenameButton(this.selectWorldScreen).enabled = flag2;
         GuiSelectWorld.getRecreateButton(this.selectWorldScreen).enabled = flag2;
         if (flag && flag2) {
            this.selectWorldScreen.selectWorld(n);
         }
      }

      @Override
      public int getSize() {
         return GuiSelectWorld.accessSaveList(this.selectWorldScreen).size();
      }
   }
}
