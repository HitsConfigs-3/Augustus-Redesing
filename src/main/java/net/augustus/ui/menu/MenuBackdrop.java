package net.augustus.ui.menu;

import java.awt.image.BufferedImage;
import java.io.InputStream;
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
 * Ambient backdrop of the redesigned main menu: the clip supplied with the redesign, baked into a sequence
 * of graded stills and played back as a slow ping-pong loop with a crossfade between neighbours.
 *
 * <p>Why a baked sequence rather than the video or a live shader: decoding a 1080p clip at runtime would
 * cost far more than the menu is worth on Java 8 / 1.8.9 hardware, and the frames are graded once at bake
 * time instead. The bake keeps a second, heavily blurred copy of every frame (the <em>frost</em> set, a
 * 320x180 smear of the same grade). Panels and the player figure frost themselves by sampling that set
 * rather than by reading the framebuffer: a per-frame capture with {@code KawaseBlurEngine} looks the same
 * but stalls some drivers badly enough to freeze the client, so the blur is paid for once, at bake time,
 * and is guaranteed to line up with whatever is behind it because it <em>is</em> what is behind it.
 *
 * <p>Only four textures are ever resident - two sharp slots and two frost slots - and each slot owns a
 * fixed-size {@link DynamicTexture} whose pixel buffer is rewritten when the sequence advances (180 frames
 * would otherwise sit in VRAM permanently for a background the glass panels cover most of). Both the loop
 * and its drift are driven by {@link UiAnimation#dt()}, so playback is frame-rate independent.
 */
public final class MenuBackdrop {
   /** Frames baked by {@code work/bake_video_backdrop.py}: 30 s of source at 3 fps. */
   private static final int FRAME_COUNT = 90;
   /** Seconds of source time represented by one frame. */
   private static final float FRAME_SECONDS = 30.0F / (float)FRAME_COUNT;
   /** Share of each interval spent crossfading into the next frame. */
   private static final float FADE_PORTION = 0.62F;
   /** Baked frames are JPEG (q82, 4:4:4); PNG is kept as a fallback for hand-replaced art. */
   private static final String FRAME_PATH_JPEG = "textures/menu/bg_%02d.jpg";
   private static final String FRAME_PATH_PNG = "textures/menu/bg_%02d.png";
   /** Blurred twin of each frame, used as the frost source for panels and the figure dissolve. */
   private static final String FROST_PATH_JPEG = "textures/menu/bgb_%02d.jpg";
   private static final String FROST_PATH_PNG = "textures/menu/bgb_%02d.png";

   private final Slot base = new Slot();
   private final Slot incoming = new Slot();
   private final Slot baseFrost = new Slot();
   private final Slot incomingFrost = new Slot();
   private float clock;
   private boolean unavailable;

   /*
    * Geometry of the most recent draw(). Frost callers work in the same GUI coordinates as the screen, so
    * the backdrop has to hand back where it actually put the frame - including the drift and the zoom - or
    * the frost would slide against the image it is supposed to be a blur of.
    */
   private float drawX;
   private float drawY;
   private float drawW;
   private float drawH;
   private float drawOpacity;
   private float drawFade;
   private int drawStep = -1;
   private boolean drawValid;

   /** True while the baked frames could be loaded; the screen falls back to a plain gradient otherwise. */
   public boolean isAvailable() {
      return !this.unavailable;
   }

   /** Restarts the loop from its first frame (used when the menu is entered). */
   public void restart() {
      this.clock = 0.0F;
   }

   /**
    * Draws the backdrop into the given rectangle in the caller's (logical GUI) coordinates.
    *
    * @param opacity global alpha, so the screen can fade the whole backdrop in on entry
    */
   public void draw(float x, float y, float width, float height, float opacity) {
      this.drawValid = false;
      if (this.unavailable || opacity <= 0.003F || width < 4.0F || height < 4.0F) {
         return;
      }

      this.clock += UiAnimation.dt();
      float timeline = this.clock / FRAME_SECONDS;
      int step = (int)Math.floor((double)timeline);
      float phase = timeline - (float)step;
      float fade = UiAnimation.easeInOutCubic(UiAnimation.clamp01((phase - (1.0F - FADE_PORTION)) / FADE_PORTION));
      this.drawStep = step;
      this.drawFade = fade;

      if (!this.ensure(this.base, frameAt(step), FRAME_PATH_JPEG, FRAME_PATH_PNG)
         || !this.ensure(this.incoming, frameAt(step + 1), FRAME_PATH_JPEG, FRAME_PATH_PNG)) {
         return;
      }

      // A very slow zoom and pan: enough parallax to read as motion, never enough to soften the frame.
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

   /**
    * Draws the <em>blurred</em> twin of whatever the backdrop currently shows behind the given rectangle,
    * clipped to the backdrop itself and crossfaded exactly like the sharp frame, so a caller can frost a
    * panel with the real backdrop rather than an approximation of it.
    *
    * <p>The frost set is only 320x180 and magnified, so the sample is a genuine wide blur; panels stack
    * their translucent veil on top of it. This is the same picture a live {@code KawaseBlurEngine} capture
    * would produce, minus the driver hang the capture caused.
    *
    * @param alphaTop    alpha at the top edge of the rectangle
    * @param alphaBottom alpha at the bottom edge; different values give a vertical dissolve
    */
   public void drawFrost(float x, float y, float width, float height, float alphaTop, float alphaBottom) {
      if (!this.drawValid || (alphaTop <= 0.003F && alphaBottom <= 0.003F)) {
         return;
      }

      // Clip the request to the drawn quad and shrink the destination with it, so asking for a region near
      // the edge does not stretch the blur across the whole frame.
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

      if (this.ensure(this.baseFrost, frameAt(this.drawStep), FROST_PATH_JPEG, FROST_PATH_PNG)) {
         drawQuad(this.baseFrost, left, top, quadW, quadH, u0, v0, u1, v1,
            this.drawOpacity * topAlpha, this.drawOpacity * bottomAlpha);
      }

      if (this.drawFade > 0.002F
         && this.ensure(this.incomingFrost, frameAt(this.drawStep + 1), FROST_PATH_JPEG, FROST_PATH_PNG)) {
         float weight = this.drawOpacity * this.drawFade;
         drawQuad(this.incomingFrost, left, top, quadW, quadH, u0, v0, u1, v1,
            weight * topAlpha, weight * bottomAlpha);
      }
   }

   /** Frost with a flat alpha, the common case for glass panels. */
   public void drawFrost(float x, float y, float width, float height, float alpha) {
      this.drawFrost(x, y, width, height, alpha, alpha);
   }

   /**
    * Frame index for a sequence step, ping-ponged so the loop is seamless: 0..89 then 88..1 and back to
    * 0. The reversed half costs nothing (the frames are already resident) and removes the jump that
    * playing 89 straight into 0 would produce.
    */
   private static int frameAt(int step) {
      int period = (FRAME_COUNT - 1) * 2;
      int wrapped = step % period;
      if (wrapped < 0) {
         wrapped += period;
      }

      return wrapped < FRAME_COUNT ? wrapped : period - wrapped;
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
         // Bilinear, no mipmaps: the quad is only slightly magnified and mipmapping would soften it.
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
      if (image == null) {
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

   /**
    * One textured quad with a per-vertex alpha pair, so a frost band can ramp into the image instead of
    * ending on a hard edge. The vertex colours are written per vertex, which is why this needs
    * {@code POSITION_TEX_COLOR} and a white {@code GlStateManager.color} under it.
    */
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

   /** One double-buffered frame: the texture it is uploaded into and the index it currently holds. */
   private static final class Slot {
      private DynamicTexture texture;
      private int index = -1;
      private int width;
      private int height;
   }
}
