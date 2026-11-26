package net.augustus.utils.render;

import net.augustus.ClientManagers;

/**
 * Reusable helper for applying pure transparent Kawase blur to any GUI component
 * (windows, panels, menus, tooltips, buttons, sliders, and options).
 */
public final class BlurComponent {
   private BlurComponent() {
   }

   /**
    * Draws pure transparent Kawase blur behind a rectangular component.
    */
   public static void drawRect(float x, float y, float width, float height, float blurRadius) {
      KawaseBlurEngine.drawBlur(x, y, width, height, blurRadius);
   }

   /**
    * Draws pure transparent Kawase blur behind a rounded rectangular component.
    */
   public static void drawRounded(float x, float y, float width, float height, float cornerRadius, float blurRadius) {
      KawaseBlurEngine.drawRoundedBlur(x, y, width, height, cornerRadius, blurRadius);
   }

   /**
    * Draws pure transparent Kawase blur behind a rounded rectangular component with per-corner radii.
    */
   public static void drawRounded(
      float x,
      float y,
      float width,
      float height,
      float topLeftRadius,
      float topRightRadius,
      float bottomRightRadius,
      float bottomLeftRadius,
      float blurRadius
   ) {
      KawaseBlurEngine.drawRoundedBlur(
         x,
         y,
         width,
         height,
         topLeftRadius,
         topRightRadius,
         bottomRightRadius,
         bottomLeftRadius,
         blurRadius
      );
   }

   /**
    * Draws pure transparent Kawase blur for floating overlays (context menus, tooltips, dropdowns)
    * by capturing the current framebuffer content directly underneath the popup.
    */
   public static void drawPopup(float x, float y, float width, float height, float cornerRadius, float blurRadius) {
      KawaseBlurEngine.drawPopupBlur(x, y, width, height, cornerRadius, blurRadius);
   }

   /**
    * Draws a subtle Liquid Glass panel: pure transparent Kawase blur inside with optical edge refraction,
    * delicate white Fresnel specular sheen at the bevel, and a crisp antialiased border.
    */
   public static void drawLiquidGlass(
      float x,
      float y,
      float width,
      float height,
      float topLeftRadius,
      float topRightRadius,
      float bottomRightRadius,
      float bottomLeftRadius,
      float blurRadius,
      float borderWidth,
      int borderArgb,
      float fresnelAlpha,
      float distortStrength
   ) {
      KawaseBlurEngine.drawLiquidGlass(
         x,
         y,
         width,
         height,
         topLeftRadius,
         topRightRadius,
         bottomRightRadius,
         bottomLeftRadius,
         blurRadius,
         borderWidth,
         borderArgb,
         fresnelAlpha,
         distortStrength
      );
   }

   public static void drawLiquidGlass(
      float x,
      float y,
      float width,
      float height,
      float topLeftRadius,
      float topRightRadius,
      float bottomRightRadius,
      float bottomLeftRadius,
      float blurRadius,
      float borderWidth,
      int borderArgb,
      float fresnelAlpha,
      float distortStrength,
      int glassTintArgb,
      float frostGrain,
      float sheenStrength,
      float saturationBoost,
      float globalAlpha
   ) {
      KawaseBlurEngine.drawLiquidGlass(
         x,
         y,
         width,
         height,
         topLeftRadius,
         topRightRadius,
         bottomRightRadius,
         bottomLeftRadius,
         blurRadius,
         borderWidth,
         borderArgb,
         fresnelAlpha,
         distortStrength,
         glassTintArgb,
         frostGrain,
         sheenStrength,
         saturationBoost,
         globalAlpha
      );
   }

   /**
    * Draws a subtle Liquid Glass panel with uniform corner radius.
    */
   public static void drawLiquidGlass(
      float x,
      float y,
      float width,
      float height,
      float cornerRadius,
      float blurRadius,
      float borderWidth,
      int borderArgb,
      float fresnelAlpha,
      float distortStrength
   ) {
      drawLiquidGlass(
         x,
         y,
         width,
         height,
         cornerRadius,
         cornerRadius,
         cornerRadius,
         cornerRadius,
         blurRadius,
         borderWidth,
         borderArgb,
         fresnelAlpha,
         distortStrength
      );
   }

   /**
    * Draws a subtle Liquid Glass floating popup capturing the live contents behind it.
    */
   public static void drawPopupLiquidGlass(
      float x,
      float y,
      float width,
      float height,
      float cornerRadius,
      float blurRadius,
      float borderWidth,
      int borderArgb,
      float fresnelAlpha,
      float distortStrength
   ) {
      KawaseBlurEngine.drawPopupLiquidGlass(
         x,
         y,
         width,
         height,
         cornerRadius,
         blurRadius,
         borderWidth,
         borderArgb,
         fresnelAlpha,
         distortStrength
      );
   }

   public static void drawPopupLiquidGlass(
      float x,
      float y,
      float width,
      float height,
      float cornerRadius,
      float blurRadius,
      float borderWidth,
      int borderArgb,
      float fresnelAlpha,
      float distortStrength,
      int glassTintArgb,
      float frostGrain,
      float sheenStrength,
      float saturationBoost,
      float globalAlpha
   ) {
      KawaseBlurEngine.drawPopupLiquidGlass(
         x,
         y,
         width,
         height,
         cornerRadius,
         blurRadius,
         borderWidth,
         borderArgb,
         fresnelAlpha,
         distortStrength,
         glassTintArgb,
         frostGrain,
         sheenStrength,
         saturationBoost,
         globalAlpha
      );
   }

   /**
    * Draws pure transparent Kawase blur for ClickGUI option controls when option blur is enabled
    * and not already covered by an enclosing window or panel blur pass.
    */
   public static void drawClickGuiOptionBlur(float x, float y, float width, float height, float cornerRadius) {
      if (ClientManagers.moduleManager != null
         && ClientManagers.moduleManager.clickGui != null
         && ClientManagers.moduleManager.clickGui.isOptionBlurActive()
         && !ClientManagers.moduleManager.clickGui.isWindowBlurActive()
         && !ClientManagers.moduleManager.clickGui.isPanelBlurActive()) {
         KawaseBlurEngine.drawRoundedBlur(
            x,
            y,
            width,
            height,
            cornerRadius,
            ClientManagers.moduleManager.clickGui.getBlurRadius()
         );
      }
   }
}
