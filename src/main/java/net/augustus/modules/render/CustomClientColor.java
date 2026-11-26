package net.augustus.modules.render;

import java.awt.Color;
import net.augustus.Augustus;
import net.augustus.ClientManagers;
import net.augustus.config.VisualsConfigFile;
import net.augustus.events.EventTimerUpdate;
import net.augustus.events.listeners.TimerUpdateListener;
import net.augustus.modules.Categorys;
import net.augustus.modules.Module;
import net.augustus.settings.ColorSetting;
import net.augustus.settings.DoubleValue;
import net.augustus.settings.StringValue;

public class CustomClientColor extends Module implements TimerUpdateListener {
   public final StringValue themePreset;
   public final ColorSetting color2;
   public final DoubleValue blurIntensity;
   private boolean syncing;

   public CustomClientColor() {
      super("CustomClientColor", "EinstellbareKundenFarbe", new Color(124, 58, 237), Categorys.RENDER);
      this.themePreset = (StringValue)new StringValue(
         this,
         "ThemePreset",
         "Violet",
         new String[]{"Violet", "Indigo", "Cyan", "Emerald", "Rose", "Custom"}
      )
         .withDisplayName("Theme Preset");
      this.themePreset.setDescription("Choose one of the curated accent palettes or pick a custom color below.");

      this.color2 = (ColorSetting)new ColorSetting(this, "ColorSetting", new Color(124, 58, 237))
         .withDisplayName("Accent Color");
      this.color2.setDescription("Primary client accent color used for switches, highlights, badges, and borders.");

      this.blurIntensity = (DoubleValue)new DoubleValue(this, "BlurIntensity", 12.0, 0.0, 30.0, 1)
         .withDisplayName("Blur Intensity");
      this.blurIntensity.setDescription("Controls real-time Kawase blur radius across glass surfaces (0 disables blur).");

      this.themePreset.withChangeListener(() -> onPresetChanged());
      this.blurIntensity.withChangeListener(() -> onBlurSliderChanged());
   }

   public static Color presetColorFor(String presetName) {
      if ("Indigo".equals(presetName)) {
         return new Color(99, 102, 241);
      }
      if ("Cyan".equals(presetName)) {
         return new Color(6, 182, 212);
      }
      if ("Emerald".equals(presetName)) {
         return new Color(16, 185, 129);
      }
      if ("Rose".equals(presetName)) {
         return new Color(244, 63, 94);
      }
      return new Color(124, 58, 237);
   }

   private void onPresetChanged() {
      if (this.syncing) {
         return;
      }
      String selected = this.themePreset.getSelectedValue();
      if (selected != null && !"Custom".equals(selected)) {
         this.syncing = true;
         try {
            Color c = presetColorFor(selected);
            this.color2.setRainbowEnabled(false);
            this.color2.setFadeEnabled(false);
            this.color2.setAstolfoEnabled(false);
            this.color2.setPrimaryColor(c);
            this.syncClientColorNow();
            VisualsConfigFile.saveNow();
         } finally {
            this.syncing = false;
         }
      }
   }

   private void onBlurSliderChanged() {
      if (this.syncing) {
         return;
      }
      this.syncing = true;
      try {
         if (ClientManagers.moduleManager != null && ClientManagers.moduleManager.clickGui != null) {
            double val = this.blurIntensity.getValue();
            if (Math.abs(ClientManagers.moduleManager.clickGui.blurIntensity.getValue() - val) > 0.01) {
               ClientManagers.moduleManager.clickGui.blurIntensity.setValue(val);
            }
         }
         VisualsConfigFile.saveNow();
      } finally {
         this.syncing = false;
      }
   }

   public void syncBlurFromClickGui(double val) {
      if (this.syncing) {
         return;
      }
      this.syncing = true;
      try {
         if (Math.abs(this.blurIntensity.getValue() - val) > 0.01) {
            this.blurIntensity.setValue(val);
         }
      } finally {
         this.syncing = false;
      }
   }

   public void syncThemeAfterColorPick() {
      if (this.syncing || this.color2 == null || this.themePreset == null) {
         return;
      }
      this.syncClientColorNow();
      String selected = this.themePreset.getSelectedValue();
      if (selected != null && !"Custom".equals(selected)) {
         Color expected = presetColorFor(selected);
         Color actual = this.color2.getPrimaryColor();
         if (actual != null
            && (Math.abs(actual.getRed() - expected.getRed()) > 2
               || Math.abs(actual.getGreen() - expected.getGreen()) > 2
               || Math.abs(actual.getBlue() - expected.getBlue()) > 2)) {
            this.syncing = true;
            try {
               this.themePreset.setSelectedValue("Custom");
            } finally {
               this.syncing = false;
            }
         }
      }
   }

   public void syncClientColorNow() {
      if (Augustus.getInstance() != null && this.color2 != null) {
         Augustus.getInstance().setClientColor(this.color2.getColorInt((byte)0));
      }
   }

   @Override
   public void onEnable() {
      this.syncClientColorNow();
   }

   @Override
   public void onDisable() {
      if (this.color2 != null) {
         Augustus.getInstance().setClientColor(this.color2.getColorInt((byte)0));
      } else {
         Augustus.getInstance().setClientColor(new Color(124, 58, 237).getRGB());
      }
   }

   @Override
   public void updateEventSubscriptions() {
      this.toggleEventSubscription(42);
   }

   @Override
   public void onTimerUpdate(EventTimerUpdate event) {
      Augustus.getInstance().setClientColor(this.color2.getColorInt((byte)0));
   }
}
