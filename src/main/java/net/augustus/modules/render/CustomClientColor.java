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

/**
 * "Client Color" (the sidebar's Glass Color option): one colour that tints the glass of the whole ClickGUI
 * and drives the client accent, plus the two independent material sliders.
 *
 * <p>WHY the colour lives here and not in a bespoke panel: the glass tint is read from this module by
 * {@code UiTheme.glassBaseRgb} on every frame, so the window, the sidebar, every card, well, control and
 * popup pick a new tint up in the same frame it is picked, with no propagation step to forget. The panel
 * the user sees is this module's settings page (Preset / Glass Tint / Opacity / Blur Intensity), so every
 * control is a real {@code Setting}: it is rendered by the house card/slider/chip widgets, it persists in
 * {@code visuals.json}, and config loading restores it like any other module value.</p>
 *
 * <p>WHY the opacity is its own setting: the tint's <em>colour</em> and its <em>strength</em> are different
 * knobs. Strength must go through {@code UiTheme.glassTintMix()} (fed by the ClickGui module's
 * GlassOpacity), which is the single transfer that decides how much blurred backdrop survives; writing the
 * colour's own alpha instead would double-compensate and flatten the material into a solid plate.</p>
 */
public class CustomClientColor extends Module implements TimerUpdateListener {
   /** Presets, in display order. Chips are drawn for every entry, so this must stay at six or fewer. */
   private static final String[] PRESETS = {"Purple", "Blue", "Cyan", "Emerald", "Red", "Neutral"};
   /**
    * Not a preset: the state the selector takes when the picked colour matches no swatch, so the row
    * highlights nothing instead of pointing at a colour the user is no longer using.
    */
   private static final String CUSTOM_PRESET = "Custom";

   public final StringValue themePreset;
   public final ColorSetting color2;
   public final DoubleValue glassOpacity;
   public final DoubleValue blurIntensity;
   private boolean syncing;
   /** Last RGB handed to the accent/client-colour copies, so the timer path only writes on real changes. */
   private int lastPushedTint;

   public CustomClientColor() {
      super("CustomClientColor", "EinstellbareKundenFarbe", new Color(124, 58, 237), Categorys.RENDER);
      this.setDescription("Tints the glass across the whole ClickGUI: pick a colour or a preset, then how strongly it covers the blurred backdrop.");

      // Order matters: it is the order of the page's cards.
      this.themePreset = (StringValue)new StringValue(this, "ThemePreset", PRESETS[0], PRESETS)
         .withDisplayName("Preset");
      this.themePreset.setDescription("Curated glass tints. Picking a swatch sets the colour below; dragging the picker switches this row to Custom.");

      this.color2 = (ColorSetting)new ColorSetting(this, "ColorSetting", new Color(124, 58, 237))
         .withDisplayName("Glass Tint");
      this.color2.setDescription("Hue of the glass tint and of the client colour. Brightness, blur and transparency stay the material's, so no surface turns solid.");

      this.glassOpacity = (DoubleValue)new DoubleValue(this, "GlassOpacity", 100.0, 0.0, 100.0, 0)
         .withDisplayName("Opacity");
      this.glassOpacity.setDescription("How strongly the tint covers the blurred backdrop, independently of the colour (0 keeps pure liquid glass).");

      this.blurIntensity = (DoubleValue)new DoubleValue(this, "BlurIntensity", 12.0, 0.0, 30.0, 1)
         .withDisplayName("Blur Intensity");
      this.blurIntensity.setDescription("Kawase blur radius behind the glass surfaces (0 disables blur).");

      this.themePreset.withChangeListener(this::onPresetChanged);
      // User-only: a config load must not push this default into the ClickGui module's GlassOpacity, which
      // is the value the renderer actually reads (an existing user may have it at something other than 88).
      this.glassOpacity.withUserChangeListener(this::onOpacityChanged);
      this.blurIntensity.withChangeListener(this::onBlurSliderChanged);
      this.syncOpacityFromClickGui();
      this.lastPushedTint = this.color2.getColorInt((byte)0) & 0x00FFFFFF;
   }

   /** Colour of a preset swatch. Unknown names fall back to Purple, the client's own default. */
   public static Color presetColorFor(String presetName) {
      if ("Purple".equals(presetName)) {
         return new Color(124, 58, 237);
      }
      if ("Blue".equals(presetName)) {
         return new Color(59, 130, 246);
      }
      if ("Cyan".equals(presetName)) {
         return new Color(6, 182, 212);
      }
      if ("Emerald".equals(presetName)) {
         return new Color(16, 185, 129);
      }
      if ("Red".equals(presetName)) {
         return new Color(239, 68, 68);
      }
      if ("Neutral".equals(presetName)) {
         return new Color(148, 163, 184);
      }
      return new Color(124, 58, 237);
   }

   public static boolean isPreset(String presetName) {
      if (presetName == null) {
         return false;
      }
      for (String preset : PRESETS) {
         if (preset.equals(presetName)) {
            return true;
         }
      }
      return false;
   }

   /**
    * Maps the pre-redesign swatch names onto their replacements. Configs saved before the redesign still
    * hold "Violet"/"Indigo"/"Rose"; without this they would restore a value that matches no chip.
    */
   private static String normalizePreset(String presetName) {
      if ("Violet".equals(presetName)) {
         return "Purple";
      }
      if ("Indigo".equals(presetName)) {
         return "Blue";
      }
      if ("Rose".equals(presetName)) {
         return "Red";
      }
      return presetName;
   }

   @Override
   public String getGuiTitle() {
      return "Client Color";
   }

   private void onPresetChanged() {
      if (this.syncing) {
         return;
      }

      String selected = normalizePreset(this.themePreset.getSelectedValue());
      if (selected != null && !selected.equals(this.themePreset.getSelectedValue())) {
         // Legacy name: rewrite it once so the chip row and the config agree from now on.
         this.syncing = true;
         try {
            this.themePreset.setSelectedValue(selected);
         } finally {
            this.syncing = false;
         }
      }
      if (!isPreset(selected)) {
         return;
      }

      this.syncing = true;
      try {
         Color presetColor = presetColorFor(selected);
         // A preset is a flat colour: the animated modes would fight the swatch selection.
         this.color2.setRainbowEnabled(false);
         this.color2.setFadeEnabled(false);
         this.color2.setAstolfoEnabled(false);
         this.color2.setPrimaryColor(presetColor);
         this.applyTintNow();
         VisualsConfigFile.saveNow();
      } finally {
         this.syncing = false;
      }
   }

   private void onOpacityChanged() {
      if (this.syncing) {
         return;
      }
      this.syncing = true;
      try {
         ClickGui clickGui = ClientManagers.moduleManager != null ? ClientManagers.moduleManager.clickGui : null;
         if (clickGui != null && clickGui.glassOpacity != null) {
            double value = this.glassOpacity.getValue();
            if (Math.abs(clickGui.glassOpacity.getValue() - value) > 0.01) {
               clickGui.glassOpacity.setValue(value);
            }
         }
         VisualsConfigFile.saveNow();
      } finally {
         this.syncing = false;
      }
   }

   /** Reverse push: the ClickGui module's own Glass Opacity slider keeps this page in sync. */
   public void syncOpacityFromClickGui() {
      if (this.syncing) {
         return;
      }
      ClickGui clickGui = ClientManagers.moduleManager != null ? ClientManagers.moduleManager.clickGui : null;
      if (clickGui == null || clickGui.glassOpacity == null) {
         return;
      }
      this.syncing = true;
      try {
         double value = clickGui.glassOpacity.getValue();
         if (Math.abs(this.glassOpacity.getValue() - value) > 0.01) {
            this.glassOpacity.setValue(value);
         }
      } finally {
         this.syncing = false;
      }
   }

   private void onBlurSliderChanged() {
      if (this.syncing) {
         return;
      }
      this.syncing = true;
      try {
         ClickGui clickGui = ClientManagers.moduleManager != null ? ClientManagers.moduleManager.clickGui : null;
         if (clickGui != null) {
            double value = this.blurIntensity.getValue();
            if (Math.abs(clickGui.blurIntensity.getValue() - value) > 0.01) {
               clickGui.blurIntensity.setValue(value);
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

   /**
    * Pushes the chosen colour to the copies that do not read it live.
    *
    * <p>The glass tint needs no push at all ({@code UiTheme} reads the colour every frame). What does need
    * one is the accent: the ClickGui module's accent is a separate {@code ColorSetting} whose default mode
    * follows {@code Augustus.getClientColor()} — so it tracks this module on its own as long as it is in
    * client-colour mode, and is written explicitly only when the user detached it.</p>
    */
   public void applyTintNow() {
      int rgb = this.color2.getColorInt((byte)0) & 0x00FFFFFF;
      this.lastPushedTint = rgb;
      if (Augustus.getInstance() != null) {
         Augustus.getInstance().setClientColor(0xFF000000 | rgb);
      }
      ClickGui clickGui = ClientManagers.moduleManager != null ? ClientManagers.moduleManager.clickGui : null;
      if (clickGui != null && clickGui.hoverTextColor != null && !clickGui.hoverTextColor.isClientColorEnabled()) {
         clickGui.hoverTextColor.setPrimaryColor(new Color(rgb));
      }
   }

   /**
    * Keeps the preset row honest: as soon as the picker moves off the selected swatch, the row switches to
    * Custom instead of highlighting a colour that is no longer applied.
    */
   private void markPresetForCurrentColor() {
      if (this.syncing) {
         return;
      }
      String selected = this.themePreset.getSelectedValue();
      if (!isPreset(selected)) {
         return;
      }
      Color expected = presetColorFor(selected);
      Color actual = this.color2.getPrimaryColor();
      if (actual != null
         && (Math.abs(actual.getRed() - expected.getRed()) > 2
            || Math.abs(actual.getGreen() - expected.getGreen()) > 2
            || Math.abs(actual.getBlue() - expected.getBlue()) > 2)) {
         this.syncing = true;
         try {
            this.themePreset.setSelectedValue(CUSTOM_PRESET);
         } finally {
            this.syncing = false;
         }
      }
   }

   @Override
   public void onEnable() {
      this.applyTintNow();
   }

   @Override
   public void onDisable() {
      this.applyTintNow();
   }

   @Override
   public void updateEventSubscriptions() {
      this.toggleEventSubscription(42);
   }

   @Override
   public void onTimerUpdate(EventTimerUpdate event) {
      // The picker writes the colour continuously while the user drags, and ColorSetting fires no change
      // listener, so the accent/client-colour copies (not the glass, which is live) catch up here.
      int rgb = this.color2.getColorInt((byte)0) & 0x00FFFFFF;
      if (rgb != this.lastPushedTint) {
         this.applyTintNow();
         this.markPresetForCurrentColor();
      }
   }
}
