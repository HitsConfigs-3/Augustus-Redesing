package net.augustus.ui.config;

import net.augustus.Augustus;
import net.augustus.ClientManagers;
import net.augustus.clickgui.ClickGuiWindow;
import net.augustus.clickgui.CreateConfigScreen;
import net.augustus.config.ModuleConfigStorage;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.render.RenderUtil;
import net.augustus.settings.options.NotificationCategoryOption;
import net.augustus.ui.modern.components.ModernButton;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.ui.notifications.NotificationLevel;
import net.augustus.ui.notifications.NotificationManager;
import net.augustus.ui.widgets.SmoothScrollBar;
import net.augustus.utils.ChatUtil;
import net.augustus.utils.audio.ClientSounds;
import net.augustus.utils.file.ClientFileUtil;
import net.augustus.utils.file.ClientPaths;
import net.augustus.utils.file.DirectoryWatchService;
import net.augustus.utils.render.BlurComponent;
import net.augustus.utils.render.ScrollFadeCompositor;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

public class ConfigSystemPanel extends ClickGuiWindow {
   private static final String CONFIGS_DIRECTORY = ClientPaths.CLIENT_DIR + File.separator + "configs";
   private static final float MY_CONFIGS_HEADER_WIDTH = FontManager.consolas32.getStringWidth("Local Configs");
   private static final float CREATE_BUTTON_WIDTH = FontManager.consolas32.getStringWidth("Create");
   private static final float LOAD_BUTTON_WIDTH = FontManager.consolas32.getStringWidth("Load");
   private static final float SAVE_BUTTON_WIDTH = FontManager.consolas32.getStringWidth("Save");
   private static final float FOLDER_BUTTON_WIDTH = FontManager.consolas32.getStringWidth("Folder");
   private static final float REFRESH_BUTTON_WIDTH = FontManager.consolas32.getStringWidth("Refresh");
   private static final float DELETE_BUTTON_WIDTH = FontManager.consolas32.getStringWidth("Delete");
   private static final float CREATE_BUTTON_SMALL_WIDTH = FontManager.consolas30.getStringWidth("Create");
   private static final float LOAD_BUTTON_SMALL_WIDTH = FontManager.consolas30.getStringWidth("Load");
   private static final float SAVE_BUTTON_SMALL_WIDTH = FontManager.consolas30.getStringWidth("Save");
   private static final float FOLDER_BUTTON_SMALL_WIDTH = FontManager.consolas30.getStringWidth("Folder");
   private static final float REFRESH_BUTTON_SMALL_WIDTH = FontManager.consolas30.getStringWidth("Refresh");
   private static final float DELETE_BUTTON_SMALL_WIDTH = FontManager.consolas30.getStringWidth("Delete");

   public final CopyOnWriteArrayList<AbstractConfigListItem> userConfigs = new CopyOnWriteArrayList<>();
   private final SmoothScrollBar userConfigsScrollBar = new SmoothScrollBar();
   private final CustomFontUtil itemFont = FontManager.interSemiBold;
   private final CustomFontUtil headerFont = FontManager.interBold;
   private final ModernButton createButton = new ModernButton();
   private final ModernButton loadButton = new ModernButton();
   private final ModernButton saveButton = new ModernButton();
   private final ModernButton folderButton = new ModernButton();
   private final ModernButton refreshButton = new ModernButton();
   private final ModernButton deleteButton = new ModernButton();
   private int userConfigsScrollOffset = 0;
   private AbstractConfigListItem selectedConfig;
   public CompletableFuture refreshFuture;
   private boolean initialLoadTriggered = false;

   /**
    * Applies the ClickGUI open/close envelope to one ARGB at the point of draw.
    *
    * <p>WHY the panel needs this on top of its inherited shell fade: {@code ClickGuiWindow.drawWindow}
    * already fades the window chrome, but this panel's inner list separator, the "Local Configs" header,
    * every config row and its scrollbar are painted by this class, so without it they would stay bright over
    * the fading shell for the whole ~290 ms close.
    *
    * <p>The colours passed to the shared helpers here are always RAW tokens; the envelope is applied once, at
    * the argument position, so nothing is faded twice. Only alpha moves - the row geometry, the scissor rects
    * and the scroll offsets are untouched.
    */
   private static int faded(int argb) {
      // Settled overlay (everything except the ~288 ms open/close transition): skip the arithmetic.
      return screenTransition() >= 0.999F ? argb : fadedArgb(argb);
   }

   public ConfigSystemPanel(String title, int x, int y, int width, int height) {
      super(title, x, y, width, height);
      DirectoryWatchService.watch(CONFIGS_DIRECTORY)
         .onCreate(path -> this.onLocalConfigFileModified(((Path)path).toFile()))
         .onDelete(path -> this.onLocalConfigFileDeleted((Path)path))
         .register();
   }

   private void scanLocalConfigDirectory() {
      this.userConfigs.clear();
      try {
         File[] files = new File(CONFIGS_DIRECTORY).listFiles(file -> !file.isDirectory() && file.getName().endsWith(".json"));
         if (files != null) {
            for (File file : files) {
               this.loadLocalConfigEntry(file);
            }
         }
      } catch (Exception ignored) {
      }
      this.sortUserConfigs();
   }

   private void onLocalConfigFileModified(File file) {
      this.removeConfigEntryForFile(file);
      this.loadLocalConfigEntry(file);
      this.sortUserConfigs();
   }

   private void onLocalConfigFileDeleted(Path path) {
      this.removeConfigEntryForFile(path.toFile());
   }

   private void sortUserConfigs() {
      this.userConfigs.sort(Comparator.comparing(item -> item.getConfigName().toLowerCase()));
   }

   private void removeConfigEntryForFile(File file) {
      String fileName = file.getName();
      int dotIdx = fileName.lastIndexOf('.');
      String name = dotIdx >= 0 ? fileName.substring(0, dotIdx) : fileName;
      this.userConfigs.stream()
         .filter(item -> item.getConfigName().equals(name))
         .findFirst()
         .ifPresent(this.userConfigs::remove);
      this.sortUserConfigs();
   }

   private void loadLocalConfigEntry(File file) {
      String fileName = file.getName();
      if (!fileName.endsWith(".json")) {
         return;
      }
      try {
         String configName = fileName.substring(0, fileName.length() - 5);
         String[] header = Augustus.getInstance().getConfigManager().moduleConfigStorage.getConfigHeaderInfo(configName, "configs");
         String date = header != null && header.length > 1 ? header[1] : "";
         String time = header != null && header.length > 2 ? header[2] : "";
         String editableStr = header != null && header.length == 5 ? header[3] : "true";
         String author = header != null && header.length >= 4 ? (header.length == 5 ? header[4] : header[3]) : "";
         this.userConfigs.stream().filter(item -> item.getConfigName().equals(configName)).findAny().ifPresent(this.userConfigs::remove);
         this.userConfigs.add(new UserConfigListItem(configName, author, date, time, Boolean.parseBoolean(editableStr)));
      } catch (Exception e) {
         e.printStackTrace();
         if (mc.theWorld != null) {
            notifyConfigStatus("Failed to load config: " + file.getName(), NotificationLevel.ERROR);
         }
      }
   }

   public static void notifyConfigStatus(String message, NotificationLevel level) {
      if (mc.theWorld != null) {
         ChatUtil.sendChat(message);
         if (Augustus.getInstance().getModuleManager().notifications.isToggled() && NotificationCategoryOption.CONFIG_SYSTEM.isSelected()) {
            NotificationManager.pushNotification("Configsystem", message, level);
         }
      }
   }

   @Override
   public void drawWindow(int mouseX, int mouseY, float partialTicks) {
      super.drawWindow(mouseX, mouseY, partialTicks);
      if (!this.isVisible()) {
         return;
      }

      ScaledResolution sr = new ScaledResolution(mc);
      GL11.glPushMatrix();
      GL11.glScaled(1.0 / (double)sr.getScaleFactor(), 1.0 / (double)sr.getScaleFactor(), 1.0);
      mouseX *= sr.getScaleFactor();
      mouseY *= sr.getScaleFactor();
      GL11.glPushMatrix();
      mouseX -= this.getX();
      mouseY -= this.getY();
      GL11.glTranslatef((float)this.getX(), (float)this.getY(), 0.0F);

      int listWidth = this.getWidth() * 3 / 4;
      int sidebarWidth = this.getWidth() - listWidth;
      if (!ClientManagers.moduleManager.clickGui.isWindowBlurActive() && ClientManagers.moduleManager.clickGui.isPanelBlurActive()) {
         float blurRadius = ClientManagers.moduleManager.clickGui.getBlurRadius();
         float corner = this.isFullScreen() ? 0.0F : (float)this.cornerRadius;
         GlassRenderer.drawLiquidGlassSurface(0.0F, 34.0F, (float)listWidth, (float)(this.getHeight() - 34), 0.0F, 0.0F, 0.0F, corner, blurRadius, UiTheme.BORDER_THICKNESS, faded(UiTheme.glassBorderColor()));
         GlassRenderer.drawLiquidGlassSurface((float)listWidth, 34.0F, (float)sidebarWidth, (float)(this.getHeight() - 34), 0.0F, 0.0F, corner, 0.0F, blurRadius, UiTheme.BORDER_THICKNESS, faded(UiTheme.glassBorderColor()));
      }

      GlassRenderer.drawSeparator((float)listWidth, 34.0F, (float)listWidth, (float)this.getHeight(), UiTheme.SEPARATOR_THICKNESS, faded(UiTheme.GLASS_DIVIDER));
      GlassRenderer.drawSeparator(0.0F, 82.0F, (float)listWidth, 82.0F, UiTheme.SEPARATOR_THICKNESS, faded(UiTheme.GLASS_DIVIDER));

      GL11.glEnable(GL11.GL_SCISSOR_TEST);
      RenderUtil.scissorRectScaled(
         (float)this.getX(),
         (float)this.getY(),
         (float)(this.getX() + this.getWidth()),
         (float)(this.getY() + this.getHeight()),
         1.0
      );

      this.headerFont.drawVerticallyCenteredString("Local Configs", 16.0, 34.0, 44.0, faded(UiTheme.TEXT_PRIMARY));

      RenderUtil.scissorRectScaled(
         (float)(this.getX() + this.cornerRadius),
         (float)(this.getY() + 90),
         (float)(this.getX() + listWidth - 3),
         (float)(this.getY() + this.getHeight() - this.cornerRadius),
         1.0
      );

      int bottomLimit = this.getHeight() - this.cornerRadius;
      boolean fadeCaptured = ScrollFadeCompositor.beginRegion(
         (float)(this.getX() + this.cornerRadius), 90.0F + (float)this.getY(), (float)(listWidth - this.cornerRadius - 3), (float)(bottomLimit - 90)
      );
      int offsetY = 0;
      int itemAreaWidth = listWidth - 28;
      int itemHeight = 32;
      try {
         for (AbstractConfigListItem item : this.userConfigs) {
            int itemX = 14;
            int itemY = 92 + offsetY + this.userConfigsScrollOffset;
            if (itemY + itemHeight >= 80 && itemY <= bottomLimit + 10) {
               boolean isSelected = this.selectedConfig != null && item.equals(this.selectedConfig);
               boolean rowHover = RenderUtil.isHovered((float)itemX, (float)itemY, (float)itemAreaWidth, (float)itemHeight, mouseX, mouseY);
               float selT = UiAnimation.smoothState("csp_sel_" + item.getConfigName(), isSelected ? 1.0F : 0.0F, 16.0F);
               float hovT = UiAnimation.smoothState("csp_hov_" + item.getConfigName(), rowHover ? 1.0F : 0.0F, 16.0F);
               int border = UiTheme.lerpArgb(UiTheme.GLASS_BORDER, UiTheme.withAlpha(UiTheme.accentSoftGlow(), 225), Math.max(selT, hovT * 0.75F));
               GlassRenderer.drawGlassCardWithHover((float)itemX, (float)itemY, (float)itemAreaWidth, (float)itemHeight, UiTheme.CARD_RADIUS, hovT, selT, 0.0F, 0.0F, (float)mouseX, (float)mouseY, faded(UiTheme.accentColor()), faded(border), UiTheme.BORDER_THICKNESS);
               if (selT > 0.05F) {
                  GlassRenderer.drawRoundedRect((float)(itemX + 3), (float)(itemY + 6), 3.0F, (float)(itemHeight - 12), 1.5F, faded(UiTheme.withAlpha(UiTheme.accentColor(), Math.round(240.0F * selT))));
               }
               item.renderItem(itemX + 14 + Math.round(selT * 4.0F), itemY + (itemHeight - (int)this.itemFont.getFontHeight()) / 2, itemAreaWidth - 28, itemHeight, mouseX, mouseY, this.itemFont, isSelected);
            }
            offsetY += 38;
         }
      } finally {
         if (fadeCaptured) {
            float topFade = this.userConfigsScrollOffset < -4 ? 12.0F : 0.0F;
            float bottomFade = (92 + offsetY + this.userConfigsScrollOffset) > bottomLimit ? 22.0F : 0.0F;
            ScrollFadeCompositor.endAndComposite(
               (float)this.cornerRadius, 90.0F, (float)(listWidth - this.cornerRadius - 3), (float)(bottomLimit - 90), topFade, bottomFade
            );
         }
      }

      this.userConfigsScrollBar.drawVerticalBar(listWidth - 11, 90, listWidth - 4, bottomLimit, 90 - this.userConfigsScrollOffset, offsetY, faded(UiTheme.accentColor()));
      this.userConfigsScrollOffset = this.userConfigsScrollBar.updateScrollOffset(
         offsetY, 90, bottomLimit, this.userConfigsScrollOffset, 0, 90, listWidth, bottomLimit - 90, mouseX, mouseY
      );

      RenderUtil.scissorRectScaled(
         (float)(this.getX() + listWidth + 3),
         (float)(this.getY() + 36),
         (float)(this.getX() + this.getWidth() - this.cornerRadius),
         (float)(this.getY() + this.getHeight() - this.cornerRadius),
         1.0
      );

      float btnX = (float)listWidth + 16.0F;
      float btnW = Math.max(80.0F, (float)sidebarWidth - 32.0F);
      this.createButton.render(btnX, 46.0F, btnW, 28.0F, "Create", LucideIcon.PLUS, ModernButton.Variant.PRIMARY, mouseX, mouseY);
      this.loadButton.render(btnX, 82.0F, btnW, 28.0F, "Load", LucideIcon.UPLOAD, ModernButton.Variant.GLASS, mouseX, mouseY);
      this.saveButton.render(btnX, 118.0F, btnW, 28.0F, "Save", LucideIcon.DOWNLOAD, ModernButton.Variant.GLASS, mouseX, mouseY);
      this.folderButton.render(btnX, 154.0F, btnW, 28.0F, "Folder", LucideIcon.FOLDER, ModernButton.Variant.GLASS, mouseX, mouseY);
      this.refreshButton.render(btnX, 190.0F, btnW, 28.0F, "Refresh", LucideIcon.REFRESH_CW, ModernButton.Variant.GLASS, mouseX, mouseY);

      float deleteY = (float)(this.getHeight() - 42);
      this.deleteButton.render(btnX, deleteY, btnW, 28.0F, "Delete", LucideIcon.TRASH_2, ModernButton.Variant.DANGER, mouseX, mouseY);

      GL11.glDisable(GL11.GL_SCISSOR_TEST);
      if (this.isMouseOver(0, 0, this.getWidth(), bottomLimit, mouseX, mouseY)) {
         for (AbstractConfigListItem item : this.userConfigs) {
            item.renderTooltip();
         }
      }

      GL11.glPopMatrix();
      GL11.glPopMatrix();
   }

   @Override
   public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
      if (this.isVisible()) {
         super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
      }
   }

   @Override
   public void mouseReleased(int mouseX, int mouseY, int state) {
      if (this.isVisible()) {
         super.mouseReleased(mouseX, mouseY, state);
         this.userConfigsScrollBar.mouseReleased(mouseX, mouseY, state);
      }
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      if (!this.isVisible()) {
         return;
      }

      super.mouseClicked(mouseX, mouseY, mouseButton);
      ScaledResolution sr = new ScaledResolution(mc);
      mouseX *= sr.getScaleFactor();
      mouseY *= sr.getScaleFactor();
      mouseX -= this.getX();
      mouseY -= this.getY();

      int listWidth = this.getWidth() * 3 / 4;

      if (mouseButton == 0 && Augustus.getInstance().isConfigEditingEnabled() && this.createButton.isHovered(mouseX, mouseY)) {
         ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE);
         mc.displayGuiScreen(new CreateConfigScreen(Augustus.getInstance().getClickGui()));
      }

      if (mouseButton == 0 && this.loadButton.isHovered(mouseX, mouseY) && this.selectedConfig != null) {
         Augustus.getInstance().getConfigManager().snapshotAll();
         Augustus.getInstance().getConfigManager().moduleConfigStorage.loadConfig(this.selectedConfig.getConfigName(), "configs");
      }

      if (mouseButton == 0
         && Augustus.getInstance().isConfigEditingEnabled()
         && this.saveButton.isHovered(mouseX, mouseY)
         && this.selectedConfig != null) {
         String configName = this.selectedConfig.getConfigName();
         ModuleConfigStorage storage = Augustus.getInstance().getConfigManager().moduleConfigStorage;
         storage.saveConfig(configName, "configs", this.selectedConfig.isEditable());
         this.loadLocalConfigEntry(new File(storage.getDirectoryPath() + File.separator + "configs", configName + ".json"));
         this.sortUserConfigs();
         this.selectedConfig = this.userConfigs.stream().filter(item -> item.getConfigName().equals(configName)).findFirst().orElse(null);
      }

      if (mouseButton == 0 && this.folderButton.isHovered(mouseX, mouseY)) {
         ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE);
         try {
            Desktop.getDesktop().open(new File(new File(ClientPaths.CLIENT_DIR + "/configs").getAbsolutePath()));
         } catch (Exception e) {
            notifyConfigStatus("Error opening folder", NotificationLevel.ERROR);
         }
      }

      if (mouseButton == 0 && this.refreshButton.isHovered(mouseX, mouseY)) {
         if (this.refreshFuture == null || this.refreshFuture.isDone()) {
            this.refreshFuture = CompletableFuture.runAsync(this::reloadAllConfigs);
         }
         notifyConfigStatus("Refreshing local configs...", NotificationLevel.INFO);
         ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE);
      }

      if (mouseButton == 0 && this.deleteButton.isHovered(mouseX, mouseY) && this.selectedConfig != null) {
         String configName = this.selectedConfig.getConfigName();
         if (this.refreshFuture == null || this.refreshFuture.isDone()) {
            this.refreshFuture = CompletableFuture.runAsync(() -> {
               if (this.selectedConfig != null) {
                  ClientFileUtil.moveToTrashOrDelete(new File(CONFIGS_DIRECTORY + File.separator + this.selectedConfig.getConfigName() + ".json"));
                  this.selectedConfig = null;
               }
               this.reloadAllConfigs();
            });
            ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE2);
            notifyConfigStatus("Config moved to trash bin: §c" + configName, NotificationLevel.ERROR);
         }
      }

      this.userConfigsScrollBar.mouseClicked(mouseX, mouseY, mouseButton);

      int listLeft = this.getX() + this.cornerRadius;
      int listTop = this.getY() + 90;
      int listRight = this.getX() + listWidth - 3;
      int listBottom = this.getY() + this.getHeight() - this.cornerRadius;
      boolean inList = this.isMouseWithinBounds(listLeft, listTop, listRight, listBottom, mouseX + this.getX(), mouseY + this.getY());
      if (inList) {
         int rowW = listWidth - 28;
         int y = 92 + this.userConfigsScrollOffset;
         for (AbstractConfigListItem item : this.userConfigs) {
            if (mouseButton == 0 && RenderUtil.isHovered(14.0F, (float)y, (float)rowW, 32.0F, mouseX, mouseY)) {
               this.selectedConfig = item;
            }
            item.mouseClicked(mouseX, mouseY, mouseButton);
            y += 38;
         }
      }
   }

   public ConfigSystemPanel initPanel() {
      super.closeWindow();
      return this;
   }

   private void reloadAllConfigs() {
      this.scanLocalConfigDirectory();
      if (this.selectedConfig != null) {
         String name = this.selectedConfig.getConfigName();
         this.selectedConfig = this.userConfigs.stream().filter(item -> item.getConfigName().equals(name)).findFirst().orElse(null);
      }
   }

   @Override
   public void keyTyped(char typedChar, int keyCode) throws IOException {
      if (this.isVisible()) {
         super.keyTyped(typedChar, keyCode);
      }
   }

   public void onPanelOpened() {
      if (!this.initialLoadTriggered || this.refreshFuture == null || this.refreshFuture.isDone()) {
         this.initialLoadTriggered = true;
         this.refreshFuture = CompletableFuture.runAsync(this::reloadAllConfigs);
      }
   }

   public void ensureConfigsLoaded() {
      if (!this.initialLoadTriggered) {
         this.initialLoadTriggered = true;
         this.refreshFuture = CompletableFuture.runAsync(this::reloadAllConfigs);
      }
   }

   @Override
   protected boolean usesPanelBlurLayout() {
      return true;
   }

   public AbstractConfigListItem getSelectedConfig() {
      return this.selectedConfig;
   }

   public void setSelectedConfig(AbstractConfigListItem item) {
      this.selectedConfig = item;
   }

   public void openCreateConfigScreen() {
      if (Augustus.getInstance().isConfigEditingEnabled()) {
         ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE);
         mc.displayGuiScreen(new CreateConfigScreen(Augustus.getInstance().getClickGui()));
      }
   }

   public void loadSelectedConfig() {
      if (this.selectedConfig != null) {
         Augustus.getInstance().getConfigManager().snapshotAll();
         Augustus.getInstance().getConfigManager().moduleConfigStorage.loadConfig(this.selectedConfig.getConfigName(), "configs");
      }
   }

   public void saveSelectedConfig() {
      if (Augustus.getInstance().isConfigEditingEnabled() && this.selectedConfig != null) {
         String configName = this.selectedConfig.getConfigName();
         ModuleConfigStorage storage = Augustus.getInstance().getConfigManager().moduleConfigStorage;
         storage.saveConfig(configName, "configs", this.selectedConfig.isEditable());
         this.loadLocalConfigEntry(new File(storage.getDirectoryPath() + File.separator + "configs", configName + ".json"));
         this.sortUserConfigs();
         this.selectedConfig = this.userConfigs.stream().filter(item -> item.getConfigName().equals(configName)).findFirst().orElse(null);
      }
   }

   public void openConfigsFolder() {
      ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE);
      try {
         Desktop.getDesktop().open(new File(new File(ClientPaths.CLIENT_DIR + "/configs").getAbsolutePath()));
      } catch (Exception e) {
         notifyConfigStatus("Error opening folder", NotificationLevel.ERROR);
      }
   }

   public void refreshConfigsAsync() {
      if (this.refreshFuture == null || this.refreshFuture.isDone()) {
         this.refreshFuture = CompletableFuture.runAsync(this::reloadAllConfigs);
      }
      notifyConfigStatus("Refreshing local configs...", NotificationLevel.INFO);
      ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE);
   }

   public void deleteSelectedConfig() {
      if (this.selectedConfig != null) {
         String configName = this.selectedConfig.getConfigName();
         if (this.refreshFuture == null || this.refreshFuture.isDone()) {
            this.refreshFuture = CompletableFuture.runAsync(() -> {
               if (this.selectedConfig != null) {
                  ClientFileUtil.moveToTrashOrDelete(new File(CONFIGS_DIRECTORY + File.separator + this.selectedConfig.getConfigName() + ".json"));
                  this.selectedConfig = null;
               }
               this.reloadAllConfigs();
            });
            ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE2);
            notifyConfigStatus("Config moved to trash bin: §c" + configName, NotificationLevel.ERROR);
         }
      }
   }
}
