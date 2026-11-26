package net.augustus.config;

import java.io.File;
import net.augustus.modules.FeatureBase;
import net.augustus.modules.ModuleManager;
import net.augustus.modules.render.Blur;
import net.augustus.modules.render.ClickGui;
import net.augustus.modules.render.CustomClientColor;
import net.augustus.settings.Setting;
import net.augustus.settings.property.ConfigProperty;

/**
 * Dedicated independent configuration file ({@code augustus/visuals.json}) for APPEARANCE
 * settings: ClickGUI (including GUI Scale), Blur &amp; Glow, and Client Color.
 *
 * <p>Loaded at startup after module defaults and auto-saved when appearance settings change
 * or when closing the ClickGUI, preventing combat/module presets from overwriting theme visuals.</p>
 */
public class VisualsConfigFile extends AbstractFeatureConfigFile {
   private static volatile VisualsConfigFile activeInstance;

   public VisualsConfigFile(ModuleManager moduleManager) {
      super(buildAppearanceFeatures(moduleManager));
      activeInstance = this;
   }

   private static FeatureBase[] buildAppearanceFeatures(ModuleManager moduleManager) {
      if (moduleManager == null) {
         return new FeatureBase[0];
      }
      return new FeatureBase[]{
         moduleManager.clickGui,
         moduleManager.blur,
         moduleManager.customClientColor
      };
   }

   @Override
   public String getSubDirectory() {
      return "";
   }

   @Override
   public String getDirectoryPath() {
      return BASE_CONFIG_DIR;
   }

   @Override
   public String getName() {
      return "visuals";
   }

   @Override
   public String getFilePath() {
      return BASE_CONFIG_DIR + File.separator + this.getName() + ".json";
   }

   @Override
   public boolean alwaysReserializeOnSave() {
      return true;
   }

   @Override
   public boolean markLoadedEvenIfEmpty() {
      return true;
   }

   public void saveNowInternal() {
      try {
         this.save(true);
      } catch (Throwable ignored) {
      }
   }

   public static void saveNow() {
      VisualsConfigFile inst = activeInstance;
      if (inst != null) {
         inst.saveNowInternal();
      }
   }

   /**
    * Returns true if {@code prop} belongs to an APPEARANCE module ({@link ClickGui},
    * {@link Blur}, or {@link CustomClientColor}) that must remain isolated from general module configs.
    */
   public static boolean isProtectedVisualProperty(ConfigProperty prop) {
      if (prop == null) {
         return false;
      }
      if (isVisualModule(prop)) {
         return true;
      }
      FeatureBase cur = prop.getFeatureBase();
      int guard = 0;
      while (cur != null && guard++ < 8) {
         if (isVisualModule(cur)) {
            return true;
         }
         if (cur instanceof ConfigProperty) {
            cur = ((ConfigProperty)cur).getFeatureBase();
         } else {
            break;
         }
      }
      return false;
   }

   private static boolean isVisualModule(Object obj) {
      return obj instanceof ClickGui || obj instanceof Blur || obj instanceof CustomClientColor;
   }
}
