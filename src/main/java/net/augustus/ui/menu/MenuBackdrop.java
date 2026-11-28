package net.augustus.ui.menu;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import net.augustus.ui.modern.render.UiAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;

/**
 * Ambient backdrop engine for menu screens: plays selectable ping-pong frame sequences
 * (Earth Horizon custom clip + animated Minecraft shader scenes), keeping matching low-res frost twins
 * synchronized in VRAM via a tiny 4-slot {@link DynamicTexture} ring.
 */
public final class MenuBackdrop {
   public static final class BackdropPreset {
      private final String id;
      private final String displayName;
      private final String subtitle;
      private final String framePattern;
      private final String frostPattern;
      private final int frameCount;
      private final float frameSeconds;
      private final int swatchTop;
      private final int swatchBot;

      BackdropPreset(
         String id,
         String displayName,
         String subtitle,
         String framePattern,
         String frostPattern,
         int frameCount,
         float frameSeconds,
         int swatchTop,
         int swatchBot
      ) {
         this.id = id;
         this.displayName = displayName;
         this.subtitle = subtitle;
         this.framePattern = framePattern;
         this.frostPattern = frostPattern;
         this.frameCount = frameCount;
         this.frameSeconds = frameSeconds;
         this.swatchTop = swatchTop;
         this.swatchBot = swatchBot;
      }

      public String getId() {
         return this.id;
      }

      public String getDisplayName() {
         return this.displayName;
      }

      public String getSubtitle() {
         return this.subtitle;
      }

      public int getSwatchTop() {
         return this.swatchTop;
      }

      public int getSwatchBot() {
         return this.swatchBot;
      }
   }

   public static final String DEFAULT_PRESET_ID = "moon_horizon";

   private static final List<BackdropPreset> PRESETS = Collections.unmodifiableList(Arrays.asList(
      new BackdropPreset(
         "moon_horizon",
         "Earth Horizon",
         "Custom orbital clip",
         "textures/menu/bg_%02d.jpg",
         "textures/menu/bgb_%02d.jpg",
         90,
         30.0F / 90.0F,
         0xFF282458,
         0xFF0E1020
      ),
      new BackdropPreset(
         "mc_northern",
         "MC Northern Lights",
         "Animated MC aurora",
         "textures/menu/mc_northern/f_%02d.jpg",
         "textures/menu/mc_northern/b_%02d.jpg",
         60,
         16.0F / 60.0F,
         0xFF059669,
         0xFF0B1120
      ),
      new BackdropPreset(
         "mc_rainy",
         "MC Rainy Cabin",
         "Animated MC rain",
         "textures/menu/mc_rainy/f_%02d.jpg",
         "textures/menu/mc_rainy/b_%02d.jpg",
         60,
         16.0F / 60.0F,
         0xFF0284C7,
         0xFF0F172A
      ),
      new BackdropPreset(
         "mc_overgrown",
         "MC Overgrown",
         "Animated MC forest",
         "textures/menu/mc_overgrown/f_%02d.jpg",
         "textures/menu/mc_overgrown/b_%02d.jpg",
         60,
         16.0F / 60.0F,
         0xFF15803D,
         0xFF0B1310
      ),
      new BackdropPreset(
         "mc_fireplace",
         "MC Cozy Cabin",
         "Animated MC hearth",
         "textures/menu/mc_fireplace/f_%02d.jpg",
         "textures/menu/mc_fireplace/b_%02d.jpg",
         60,
         16.0F / 60.0F,
         0xFFD97706,
         0xFF271308
      )
   ));

   private static final float FADE_PORTION = 0.62F;
   private static final String FALLBACK_PNG_FRAME = "textures/menu/bg_%02d.png";
   private static final String FALLBACK_PNG_FROST = "textures/menu/bgb_%02d.png";

   private static final MenuBackdrop SHARED = new MenuBackdrop();

   private BackdropPreset activePreset = PRESETS.get(0);
   private final Slot base = new Slot();
   private final Slot incoming = new Slot();
   private final Slot baseFrost = new Slot();
   private final Slot incomingFrost = new Slot();
   private float clock;
   private boolean unavailable;

   private float drawX;
   private float drawY;
   private float drawW;
   private float drawH;
   private float drawOpacity;
   private float drawFade;
   private int drawStep = -1;
   private boolean drawValid;

   public static MenuBackdrop getShared() {
      return SHARED;
   }

   public static List<BackdropPreset> getPresets() {
      return PRESETS;
   }

   public static BackdropPreset findPreset(String id) {
      if (id != null) {
         for (BackdropPreset p : PRESETS) {
            if (p.id.equalsIgnoreCase(id.trim())) {
               return p;
            }
         }
      }
      return PRESETS.get(0);
   }

   public BackdropPreset getActivePreset() {
      return this.activePreset;
   }

   public void setPreset(String id) {
      BackdropPreset target = findPreset(id);
      if (this.activePreset == target && !this.unavailable) {
         return;
      }
      this.activePreset = target;
      this.base.index = -1;
      this.incoming.index = -1;
      this.baseFrost.index = -1;
      this.incomingFrost.index = -1;
      this.unavailable = false;
      this.drawValid = false;
      this.drawStep = -1;
      this.clock = 0.0F;
   }

   /** True while the baked frames could be loaded; the screen falls back to a plain fill otherwise. */
   public boolean isAvailable() {
      return !this.unavailable;
   }

   /** Restarts the loop from its first frame (used when the menu is entered). */
   public void restart() {
      this.clock = 0.0F;
   }

   /** Clears cached frost geometry when another backdrop renderer (GLSL shader or vanilla) is active. */
   public void invalidateDraw() {
      this.drawValid = false;
   }

   /** Draws the animated backdrop into the given rectangle in logical GUI coordinates. */
   public void draw(float x, float y, float width, float height, float opacity) {
      this.drawValid = false;
      if (this.unavailable || opacity <= 0.003F || width < 4.0F || height < 4.0F) {
         return;
      }

      this.clock += UiAnimation.dt();
      float timeline = this.clock / this.activePreset.frameSeconds;
      int step = (int)Math.floor((double)timeline);
      float phase = timeline - (float)step;
      float fade = UiAnimation.easeInOutCubic(UiAnimation.clamp01((phase - (1.0F - FADE_PORTION)) / FADE_PORTION));
      this.drawStep = step;
      this.drawFade = fade;

      String fallbackFrame = DEFAULT_PRESET_ID.equals(this.activePreset.id) ? FALLBACK_PNG_FRAME : null;
      if (!this.ensure(this.base, frameAt(step, this.activePreset.frameCount), this.activePreset.framePattern, fallbackFrame)
         || !this.ensure(this.incoming, frameAt(step + 1, this.activePreset.frameCount), this.activePreset.framePattern, fallbackFrame)) {
         return;
      }

      float zoom = 1.012F + 0.008F * (float)Math.sin((double)(this.clock * 0.09F));
      float quadW = width * zoom;
      float quadH = height * zoom;
      float quadX = x - (quadW - width) * 0.5F + (float)Math.sin((double)(this.clock * 0.031F)) * width * 0.0025F;
      float quadY = y - (quadH - height) * 0.5F + (float)Math.cos((double)(this.clock * 0.024F)) * height * 0.0025F;

      this.drawX = quadX;
      this.drawY = quadY;
      this.drawW = quadW;
      this.drawH = quadH;
      this.drawOpacity = opacity;
      this.drawValid = true;

      drawQuad(this.base, quadX, quadY, quadW, quadH, opacity);
      if (fade > 0.002F) {
         drawQuad(this.incoming, quadX, quadY, quadW, quadH, opacity * fade);
      }
   }

   /** Draws an animated miniature preview without altering the main backdrop frost geometry. */
   public boolean drawPreview(float x, float y, float width, float height) {
      if (this.unavailable || width < 4.0F || height < 4.0F) {
         return false;
      }
      int step = Math.max(0, this.drawStep);
      float fade = UiAnimation.clamp01(this.drawFade);
      if (step == 0 && !this.drawValid) {
         float timeline = this.clock / this.activePreset.frameSeconds;
         step = (int)Math.floor((double)timeline);
         float phase = timeline - (float)step;
         fade = UiAnimation.easeInOutCubic(UiAnimation.clamp01((phase - (1.0F - FADE_PORTION)) / FADE_PORTION));
      }
      String fallbackFrame = DEFAULT_PRESET_ID.equals(this.activePreset.id) ? FALLBACK_PNG_FRAME : null;
      if (!this.ensure(this.base, frameAt(step, this.activePreset.frameCount), this.activePreset.framePattern, fallbackFrame)
         || !this.ensure(this.incoming, frameAt(step + 1, this.activePreset.frameCount), this.activePreset.framePattern, fallbackFrame)) {
         return false;
      }
      drawQuad(this.base, x, y, width, height, 1.0F);
      if (fade > 0.002F) {
         drawQuad(this.incoming, x, y, width, height, fade);
      }
      return true;
   }

   public void drawFrost(float x, float y, float width, float height, float alphaTop, float alphaBottom) {
      if (!this.drawValid || (alphaTop <= 0.003F && alphaBottom <= 0.003F)) {
         return;
      }

      float left = Math.max(x, this.drawX);
      float top = Math.max(y, this.drawY);
      float right = Math.min(x + width, this.drawX + this.drawW);
      float bottom = Math.min(y + height, this.drawY + this.drawH);
      if (right - left < 0.05F || bottom - top < 0.05F) {
         return;
      }

      float ramp = height > 0.001F ? 1.0F / height : 0.0F;
      float topAlpha = alphaTop + (alphaBottom - alphaTop) * ((top - y) * ramp);
      float bottomAlpha = alphaTop + (alphaBottom - alphaTop) * ((bottom - y) * ramp);

      float u0 = (left - this.drawX) / this.drawW;
      float u1 = (right - this.drawX) / this.drawW;
      float v0 = (top - this.drawY) / this.drawH;
      float v1 = (bottom - this.drawY) / this.drawH;
      float quadW = right - left;
      float quadH = bottom - top;

      String fallbackFrost = DEFAULT_PRESET_ID.equals(this.activePreset.id) ? FALLBACK_PNG_FROST : null;
      if (this.ensure(this.baseFrost, frameAt(this.drawStep, this.activePreset.frameCount), this.activePreset.frostPattern, fallbackFrost)) {
         drawQuad(this.baseFrost, left, top, quadW, quadH, u0, v0, u1, v1,
            this.drawOpacity * topAlpha, this.drawOpacity * bottomAlpha);
      }

      if (this.drawFade > 0.002F
         && this.ensure(this.incomingFrost, frameAt(this.drawStep + 1, this.activePreset.frameCount), this.activePreset.frostPattern, fallbackFrost)) {
         float weight = this.drawOpacity * this.drawFade;
         drawQuad(this.incomingFrost, left, top, quadW, quadH, u0, v0, u1, v1,
            weight * topAlpha, weight * bottomAlpha);
      }
   }

   /** Frost with a flat alpha, the common case for glass panels. */
   public void drawFrost(float x, float y, float width, float height, float alpha) {
      this.drawFrost(x, y, width, height, alpha, alpha);
   }

   private static int frameAt(int step, int frameCount) {
      int period = (frameCount - 1) * 2;
      int wrapped = step % period;
      if (wrapped < 0) {
         wrapped += period;
      }
      return wrapped < frameCount ? wrapped : period - wrapped;
   }

   private boolean ensure(Slot slot, int index, String jpegPattern, String pngPattern) {
      if (slot.texture != null && slot.index == index) {
         return true;
      }

      BufferedImage image = load(index, jpegPattern, pngPattern);
      if (image == null) {
         this.unavailable = true;
         return false;
      }

      try {
         if (slot.texture == null || slot.width != image.getWidth() || slot.height != image.getHeight()) {
            slot.texture = new DynamicTexture(image.getWidth(), image.getHeight());
            slot.width = image.getWidth();
            slot.height = image.getHeight();
         }

         image.getRGB(0, 0, slot.width, slot.height, slot.texture.getTextureData(), 0, slot.width);
         slot.texture.updateDynamicTexture();
         slot.texture.setBlurMipmap(true, false);
         slot.index = index;
         return true;
      } catch (Throwable t) {
         this.unavailable = true;
         return false;
      }
   }

   private static BufferedImage load(int index, String jpegPattern, String pngPattern) {
      BufferedImage image = loadPath(jpegPattern, index);
      if (image == null && pngPattern != null) {
         image = loadPath(pngPattern, index);
      }
      return image;
   }

   private static BufferedImage loadPath(String pattern, int index) {
      ResourceLocation location = new ResourceLocation("augustus", String.format(pattern, Integer.valueOf(index)));
      InputStream stream = null;
      try {
         stream = Minecraft.getMinecraft().getResourceManager().getResource(location).getInputStream();
         return ImageIO.read(stream);
      } catch (Throwable t) {
         return null;
      } finally {
         if (stream != null) {
            try {
               stream.close();
            } catch (Throwable ignored) {
            }
         }
      }
   }

   private static void drawQuad(Slot slot, float x, float y, float width, float height, float alpha) {
      drawQuad(slot, x, y, width, height, 0.0F, 0.0F, 1.0F, 1.0F, alpha, alpha);
   }

   private static void drawQuad(
      Slot slot, float x, float y, float width, float height, float u0, float v0, float u1, float v1,
      float alphaTop, float alphaBottom) {
      if (slot.texture == null) {
         return;
      }

      float top = UiAnimation.clamp01(alphaTop);
      float bottom = UiAnimation.clamp01(alphaBottom);
      GlStateManager.enableTexture2D();
      GlStateManager.enableBlend();
      GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
      GlStateManager.bindTexture(slot.texture.getGlTextureId());

      Tessellator tessellator = Tessellator.getInstance();
      WorldRenderer renderer = tessellator.getWorldRenderer();
      renderer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
      renderer.pos((double)x, (double)(y + height), 0.0D).tex((double)u0, (double)v1).color(1.0F, 1.0F, 1.0F, bottom).endVertex();
      renderer.pos((double)(x + width), (double)(y + height), 0.0D).tex((double)u1, (double)v1).color(1.0F, 1.0F, 1.0F, bottom).endVertex();
      renderer.pos((double)(x + width), (double)y, 0.0D).tex((double)u1, (double)v0).color(1.0F, 1.0F, 1.0F, top).endVertex();
      renderer.pos((double)x, (double)y, 0.0D).tex((double)u0, (double)v0).color(1.0F, 1.0F, 1.0F, top).endVertex();
      tessellator.draw();

      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
   }

   private static final class Slot {
      private DynamicTexture texture;
      private int index = -1;
      private int width;
      private int height;
   }
}
