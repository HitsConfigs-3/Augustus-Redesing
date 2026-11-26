package net.augustus.modules.render;

import java.awt.Color;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import net.augustus.modules.Categorys;
import net.augustus.modules.Module;
import net.augustus.settings.BlurShadowSettings;
import net.augustus.font.CustomFontUtil;
import net.augustus.ClientManagers;
import net.augustus.settings.ColorSetting;
import net.augustus.settings.SettingNameManager;
import net.augustus.events.listeners.DownloadTerrainListener;
import net.augustus.config.ConfigManager;
import net.augustus.Augustus;
import net.augustus.clickgui.ClickGuiScreen;
import net.augustus.clickgui.HudEditorScreen;
import net.augustus.config.VisualsConfigFile;
import net.augustus.settings.SettingChangeListener;
import net.augustus.events.listeners.TickListener;
import net.augustus.settings.SettingDescriptionManager;
import net.augustus.settings.BooleanValue;
import net.augustus.settings.StringValue;
import net.augustus.settings.SettingGroup;
import net.augustus.settings.DoubleValue;
import net.augustus.utils.render.KawaseBlurEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;

public class ClickGui extends Module implements DownloadTerrainListener, TickListener {
   public final SettingGroup generalGroup;
   public final StringValue guiScale;
   public final StringValue sorting;
   public BooleanValue germanMode;
   public final BooleanValue openInInventory;
   public final DoubleValue dynamicKeyTime;
   public final SettingGroup visualsGroup;
   public final BlurShadowSettings clickGui;
   public final BooleanValue blurWindows;
   public final BooleanValue blurPanels;
   public final BooleanValue blurMenus;
   public final BooleanValue blurOptions;
   public final DoubleValue blurIntensity;
   public final DoubleValue glassDarkness;
   public final DoubleValue glassOpacity;
   /** Opacity of the sidebar tint inside the ClickGUI window; 0 blends it into the window glass. */
   public final DoubleValue sidebarOpacity;
   public final BooleanValue outline;
   public final DoubleValue alpha;
   public final SettingGroup visibilityGroup;
   public final StringValue descriptions;
   public final StringValue settingNames;
   public final BooleanValue moduleDescriptions;
   public final BooleanValue settingDescriptions;
   public final SettingGroup colors;
   public final ColorSetting backgroundColor;
   public final ColorSetting outlineColor;
   public final ColorSetting headerColor;
   public final ColorSetting textColor;
   public final ColorSetting hoverTextColor;
   public final ColorSetting enabledColor;
   public final ColorSetting disabledColor;

   public ClickGui() {
       super("ClickGui", "KlickbareGrafischeBenutzeroberfläche", new Color(124, 58, 237), Categorys.RENDER);
      this.generalGroup = new SettingGroup(this, "General", true);
      this.guiScale = (StringValue)new StringValue(this, "GuiScale", "Normal", new String[]{"Small", "Normal", "Large"}, this.generalGroup)
         .withDisplayName("GUI Scale")
         .withChangeListener(VisualsConfigFile::saveNow);
      this.guiScale.setDescription("Adjusts ClickGUI scale cleanly with vector graphics and sharp SDF text (Small 0.85x, Normal 1.0x, Large 1.25x).");

      StringValue setting = new StringValue(this, "Sorting", "Alphabet", new String[]{"Random", "Length", "Alphabet"}, this.generalGroup);
      setting.setDescription("Determines how modules are sorted inside categories and lists.");
      SettingChangeListener listener = () -> {
         this.sortCategoryModules();
         VisualsConfigFile.saveNow();
      };
      this.sorting = (StringValue)setting.withChangeListener(listener);

      BooleanValue booleanValue = new BooleanValue(this, "GermanMode", false, this.generalGroup);
      booleanValue.setDescription("Switches displayed module names to their legacy German labels.");
      listener = () -> {
         for (Module module : (Iterable<Module>)(ClientManagers.moduleManager.getModules())) {
            module.setName(this.germanMode.getValue() ? module.getGermanName() : module.getName());
         }
         VisualsConfigFile.saveNow();
      };
      this.germanMode = (BooleanValue)booleanValue.withChangeListener(listener);

      this.openInInventory = (BooleanValue)new BooleanValue(this, "OpenInInventory", true, this.generalGroup)
         .withChangeListener(VisualsConfigFile::saveNow);
      this.openInInventory.setDescription("Allows opening and interacting with the ClickGUI while containers are open.");

      this.dynamicKeyTime = (DoubleValue)new DoubleValue(this, "DynamicKeyTime", 500.0, 0.0, 2000.0, 0, this.generalGroup)
         .withChangeListener(VisualsConfigFile::saveNow);
      this.dynamicKeyTime.setDescription("Hold duration in milliseconds before key-hold binds automatically toggle off.");

      this.visualsGroup = new SettingGroup(this, "Visuals", true);
      this.clickGui = new BlurShadowSettings(this, "ClickGui", this.visualsGroup).withCustomShadowColor(Color.black);
      this.blurWindows = (BooleanValue)new BooleanValue(this, "BlurWindows", true, this.visualsGroup)
         .withVisibility(this.clickGui::isBlurSettingEnabled)
         .withChangeListener(VisualsConfigFile::saveNow);
      this.blurWindows.setDescription("Enables real-time frosted glass Kawase blur behind main ClickGUI windows.");
      this.blurPanels = (BooleanValue)new BooleanValue(this, "BlurPanels", true, this.visualsGroup)
         .withVisibility(this.clickGui::isBlurSettingEnabled)
         .withChangeListener(VisualsConfigFile::saveNow);
      this.blurPanels.setDescription("Enables blurred backing behind nested panels and settings panes.");
      this.blurMenus = (BooleanValue)new BooleanValue(this, "BlurMenus", true, this.visualsGroup)
         .withVisibility(this.clickGui::isBlurSettingEnabled)
         .withChangeListener(VisualsConfigFile::saveNow);
      this.blurMenus.setDescription("Enables frosted blur on dropdown menus and context popups.");
      this.blurOptions = (BooleanValue)new BooleanValue(this, "BlurOptions", true, this.visualsGroup)
         .withVisibility(this.clickGui::isBlurSettingEnabled)
         .withChangeListener(VisualsConfigFile::saveNow);
      this.blurOptions.setDescription("Blurs background behind floating color picker and option overlays.");
      this.blurIntensity = (DoubleValue)new DoubleValue(this, "BlurIntensity", 12.0, 0.0, 30.0, 1, this.visualsGroup)
         .withDisplayName("Blur Intensity")
         .withVisibility(this.clickGui::isBlurSettingEnabled)
         .withChangeListener(this::onBlurIntensityChanged);
      this.blurIntensity.setDescription("Adjusts real-time Kawase blur radius on glass windows and popups (0 disables blur).");
      this.glassDarkness = (DoubleValue)new DoubleValue(this, "GlassDarkness", 78.0, 0.0, 100.0, 0, this.visualsGroup)
         .withDisplayName("Glass Darkness")
         .withChangeListener(VisualsConfigFile::saveNow);
      this.glassDarkness.setDescription("Controls frosted glass shading from light crystal (0) to deep indigo obsidian (100).");
      this.glassOpacity = (DoubleValue)new DoubleValue(this, "GlassOpacity", 88.0, 0.0, 100.0, 0, this.visualsGroup)
         .withDisplayName("Glass Opacity")
         .withChangeListener(VisualsConfigFile::saveNow);
      this.glassOpacity.setDescription("Controls frosted tint opacity in real time (0 keeps pure transparent liquid glass).");
      // The sidebar is an internal region of the ClickGUI window, not a separate slab, so its tint
      // has to be controllable independently of the window glass without becoming its own surface.
      // 0 blends the sidebar fully into the window; 100 applies the strongest sidebar tint.
      this.sidebarOpacity = (DoubleValue)new DoubleValue(this, "SidebarOpacity", 100.0, 0.0, 100.0, 0, this.visualsGroup)
         .withDisplayName("Sidebar Opacity")
         .withChangeListener(VisualsConfigFile::saveNow);
      this.sidebarOpacity.setDescription("Opacity of the sidebar tint inside the ClickGUI window (0 blends it into the window glass).");
      this.outline = (BooleanValue)new BooleanValue(this, "Outline", true, this.visualsGroup)
         .withChangeListener(VisualsConfigFile::saveNow);
      this.outline.setDescription("Renders subtle crystal border highlights around windows, cards, and interactive controls.");
      this.alpha = (DoubleValue)new DoubleValue(this, "Alpha", 224.0, 0.0, 255.0, 0, this.visualsGroup)
         .withChangeListener(VisualsConfigFile::saveNow);
      this.alpha.setDescription("Base window opacity level for legacy background compositing.");

      this.visibilityGroup = new SettingGroup(this, "Visibility", true);
      this.moduleDescriptions = (BooleanValue)new BooleanValue(this, "ModuleDescriptions", true, this.visibilityGroup)
         .withChangeListener(VisualsConfigFile::saveNow);
      this.moduleDescriptions.setDescription("Shows concise two-line descriptions on module rows, lists, and headers.");
      this.settingDescriptions = (BooleanValue)new BooleanValue(this, "SettingDescriptions", true, this.visibilityGroup)
         .withChangeListener(VisualsConfigFile::saveNow);
      this.settingDescriptions.setDescription("Shows helpful explanation text beneath setting titles inside cards.");
      StringValue setting2 = new StringValue(this, "Descriptions", "Default", new String[]{"Default"}, this.visibilityGroup);
      setting2.setDescription("Selects which localized description pack is loaded for module and setting help.");
      listener = () -> {
         this.reloadSettingDescriptionsAsync();
         VisualsConfigFile.saveNow();
      };
      this.descriptions = (StringValue)setting2.withChangeListener(listener);
      StringValue setting3 = new StringValue(this, "SettingNames", "English_Default", new String[]{"English_Default", "English_New"}, this.visibilityGroup);
      setting3.setDescription("Chooses the display naming convention used across module settings.");
      listener = () -> {
         CompletableFuture.runAsync(() -> this.reloadSettingNames());
         VisualsConfigFile.saveNow();
      };
      this.settingNames = (StringValue)setting3.withChangeListener(listener);

      this.colors = new SettingGroup(this, "Colors", true);
      Color color2 = new Color(14, 16, 26, 224);
      SettingGroup colors2 = this.colors;
      Color color3 = color2;
      String str2 = "BackgroundColor";
      this.backgroundColor = new ColorSetting(this, str2, color3, colors2);
      this.backgroundColor.setDescription("Customizes primary dark glass tint when overrides are active.");
      color2 = new Color(40, 39, 55);
      colors2 = this.colors;
      color3 = color2;
      str2 = "OutlineColor";
      this.outlineColor = new ColorSetting(this, str2, color3, colors2);
      this.outlineColor.setDescription("Sets custom border reflection tint around glass surfaces.");
      color2 = new Color(34, 34, 48);
      colors2 = this.colors;
      color3 = color2;
      str2 = "HeaderColor";
      this.headerColor = new ColorSetting(this, str2, color3, colors2);
      this.headerColor.setDescription("Custom header bar highlight color for ClickGUI panels.");
      color2 = new Color(226, 232, 240);
      colors2 = this.colors;
      color3 = color2;
      str2 = "TextColor";
      this.textColor = new ColorSetting(this, str2, color3, colors2);
      this.textColor.setDescription("Customizes primary and secondary UI label typography color.");
      color2 = new Color(124, 58, 237);
      colors2 = this.colors;
      color3 = color2;
      str2 = "TextColorHover";
      this.hoverTextColor = new ColorSetting(this, str2, color3, colors2).withClientColorDefault(true).withDefaultClientColorAlpha((short)255);
      this.hoverTextColor.setDescription("Primary violet accent highlight linked to Client Color unless customized.");
      color2 = new Color(34, 197, 94);
      colors2 = this.colors;
      color3 = color2;
      str2 = "TrueColor";
      this.enabledColor = new ColorSetting(this, str2, color3, colors2);
      this.enabledColor.setDescription("Status color used to indicate enabled modules and active badges.");
      color2 = new Color(239, 68, 68);
      colors2 = this.colors;
      color3 = color2;
      str2 = "FalseColor";
      this.disabledColor = new ColorSetting(this, str2, color3, colors2);
      this.disabledColor.setDescription("Status color used for disabled warnings and keybind conflict alerts.");
      this.setKeyCode((byte)54);
      this.setToggleSoundEnabled(false);
   }

   public float getTargetGuiScaleFactor() {
      if (this.guiScale != null) {
         String sel = this.guiScale.getSelectedValue();
         if ("Small".equals(sel)) {
            return 0.85F;
         }
         if ("Large".equals(sel)) {
            return 1.25F;
         }
      }
      return 1.0F;
   }

   @Override
   public void onEnable() {
      if (mc.currentScreen instanceof GuiContainer && !this.openInInventory.getValue()) {
         this.toggle();
      } else {
         Augustus.getInstance().getConfigManager().snapshotAll();
         Augustus.getInstance().getDiscordRpcManager().start();
         if (mc.thePlayer != null && mc.theWorld != null) {
            if (mc.currentScreen != null) {
               mc.thePlayer.closeScreen();
            }

            mc.displayGuiScreen(Augustus.getInstance().getClickGui());
         }
      }
   }

   @Override
   public void onDisable() {
      Augustus.getInstance().getConfigManager().saveAllAsync();
   }

   @Override
   public void updateEventSubscriptions() {
      this.toggleEventSubscription(8);
      this.toggleEventSubscription(40);
   }

   @Override
   public void onTick() {
      if (!(mc.currentScreen instanceof ClickGuiScreen) && !(mc.currentScreen instanceof HudEditorScreen) && this.isToggled()) {
         this.toggle();
      }
   }

   @Override
   public void onDownloadTerrain() {

      for (Module module : (Iterable<Module>)(ClientManagers.moduleManager.getModules())) {
         module.setName(this.germanMode.getValue() ? module.getGermanName() : module.getName());
      }
   }

   private void sortCategoryModules() {
      switch (this.sorting.getSelectedValue()) {
         case "Random":
            for (Categorys categorys2 : Categorys.values()) {
               if (!categorys2.hidden) {
                  categorys2.getModules().clear();
                  categorys2.getModules().addAll(categorys2.getDisplayModules());
               }
            }
            break;
         case "Alphabet":
            for (Categorys categorys3 : Categorys.values()) {
               if (!categorys3.hidden) {
                  categorys3.getModules().sort(Comparator.comparing(Module::getModuleName));
               }
            }
            break;
         case "Length":
            for (Categorys categorys4 : Categorys.values()) {
               if (!categorys4.hidden) {
                  categorys4.getModules().sort(Comparator.comparingDouble(obj -> getModuleNameLengthSortKey((Module)obj)));
               }
            }
      }
   }

   private static double getModuleNameLengthSortKey(Module module) {
      CustomFontUtil moduleFont = Augustus.getInstance().getClickGui().getMainWindow().getModuleFont();
      String str = module.getModuleName();
      return (double)(1000.0F - moduleFont.getStringWidth(str));
   }

   private void reloadSettingNames() {
      Augustus.getInstance().getSettingNameManager().loadLanguageFile(this.settingNames.getSelectedValue());
   }

   private void reloadSettingDescriptionsAsync() {
      CompletableFuture.runAsync(() -> this.reloadSettingDescriptions());
   }

   private void reloadSettingDescriptions() {
      Augustus.getInstance().getSettingDescriptionManager().loadDescriptions(this.descriptions.getSelectedValue());
   }

   public boolean isClickGuiBlurEnabled() {
      return this.clickGui.isBlurSettingEnabled()
         && (ClientManagers.moduleManager.blur.isToggled() || ClientManagers.moduleManager.blur.inScreens.getValue())
         && KawaseBlurEngine.isAvailable();
   }

   public boolean isWindowBlurActive() {
      return this.isClickGuiBlurEnabled() && this.blurWindows.getValue();
   }

   public boolean isPanelBlurActive() {
      return this.isClickGuiBlurEnabled() && this.blurPanels.getValue();
   }

   public boolean isMenuBlurActive() {
      return this.isClickGuiBlurEnabled() && this.blurMenus.getValue();
   }

   public boolean isOptionBlurActive() {
      return this.isClickGuiBlurEnabled() && this.blurOptions.getValue();
   }

   private void onBlurIntensityChanged() {
      if (ClientManagers.moduleManager != null && ClientManagers.moduleManager.customClientColor != null) {
         ClientManagers.moduleManager.customClientColor.syncBlurFromClickGui(this.blurIntensity.getValue());
      }
      VisualsConfigFile.saveNow();
   }

   public float getBlurRadius() {
      return Math.max(0.0F, (float)this.blurIntensity.getValue());
   }

   public float getBlurIntensity() {
      return this.getBlurRadius();
   }

   public float getGlassDarkness() {
      return Math.max(0.0F, Math.min(1.0F, (float)(this.glassDarkness.getValue() / 100.0)));
   }

   public float getGlassOpacity() {
      return Math.max(0.0F, Math.min(1.0F, (float)(this.glassOpacity.getValue() / 100.0)));
   }

   public float getSidebarOpacity() {
      return Math.max(0.0F, Math.min(1.0F, (float)(this.sidebarOpacity.getValue() / 100.0)));
   }

}
