package net.augustus.ui.modern.render;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import net.augustus.render.BufferedImageTextureUtil;
import net.augustus.utils.render.GlStateSaver;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * A classpath PNG whose artwork is recoloured to the live client colour at upload time.
 *
 * <p>WHY the recolour is baked instead of multiplied at draw time: the brand mark is a shaded piece of
 * artwork -- a bright apex, a mid-tone left leg, a deep right leg -- and that shading lives in the
 * file's RGB, not in its alpha. The earlier version of this class kept only the alpha and forced the
 * RGB to white, so the mark collapsed to a flat silhouette in a single accent tint: every facet was
 * gone. Modulating the file's own saturated violet by the accent is not an option either, because
 * violet x violet lands on a mute, muddy result.
 *
 * <p>So the file is decoded once and re-uploaded with each pixel's own colour scaled per channel by
 * {@code accent / DEFAULT_ACCENT}. For the default client colour that is the identity, which keeps the
 * artwork exactly as drawn; for a custom accent the whole mark swings to that hue while keeping every
 * relative value, i.e. the design survives the recolour. The texture is rebuilt only when the accent
 * actually changes, which is rare enough that an upload of a few hundred pixels is free.
 *
 * <p>Not thread-safe: it uploads on first use, so it must be drawn from the render thread.
 */
public final class MaskedTexture {
   /** One saver shared by every instance, mirroring {@link net.augustus.render.ClasspathTexture}. */
   private static final GlStateSaver STATE_SAVER = GlStateSaver.create();

   /** The colour the artwork was authored in; scaling against it makes the default accent a no-op. */
   private static final int AUTHORED_ACCENT = 0xFF7C3AED;

   private final String resourcePath;
   private int textureId = -1;
   private float aspect = 1.0F;
   private int[] artworkRgb;
   private int artworkWidth;
   private int artworkHeight;
   /** Accent the uploaded texture was built for, or 0 when the upload is the untouched artwork. */
   private int tintedAccent;
   /** One attempt only: a missing resource must not re-hit the classpath every frame. */
   private boolean loadAttempted;

   private MaskedTexture(String resourcePath) {
      this.resourcePath = resourcePath;
   }

   public static MaskedTexture of(String resourcePath) {
      return new MaskedTexture(resourcePath);
   }

   public boolean isReady() {
      this.ensureLoaded();
      return this.textureId > 0;
   }

   /** Intrinsic width / height ratio, valid once {@link #isReady()} is true. */
   public float getAspect() {
      this.ensureLoaded();
      return this.aspect;
   }

   private void ensureLoaded() {
      if (this.loadAttempted) {
         return;
      }

      this.loadAttempted = true;
      try (InputStream stream = MaskedTexture.class.getResourceAsStream(this.resourcePath)) {
         if (stream == null) {
            return;
         }

         BufferedImage source = ImageIO.read(stream);
         if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
            return;
         }

         this.artworkWidth = source.getWidth();
         this.artworkHeight = source.getHeight();
         this.aspect = (float)this.artworkWidth / (float)this.artworkHeight;
         this.artworkRgb = source.getRGB(0, 0, this.artworkWidth, this.artworkHeight, null, 0, this.artworkWidth);
         this.upload(AUTHORED_ACCENT);
      } catch (Exception ignored) {
         this.textureId = -1;
      }
   }

   /** Rebuilds the texture for {@code accentArgb} if the current upload was built for another one. */
   public void prepare(int accentArgb) {
      this.ensureLoaded();
      if (this.artworkRgb == null) {
         return;
      }
      if (this.textureId > 0 && this.tintedAccent == accentArgb) {
         return;
      }
      this.upload(accentArgb == 0 ? AUTHORED_ACCENT : accentArgb);
   }

   private void upload(int accentArgb) {
      int[] pixels = new int[this.artworkRgb.length];
      float fr = (float)(accentArgb >> 16 & 255) / (float)(AUTHORED_ACCENT >> 16 & 255);
      float fg = (float)(accentArgb >> 8 & 255) / (float)(AUTHORED_ACCENT >> 8 & 255);
      float fb = (float)(accentArgb & 255) / (float)(AUTHORED_ACCENT & 255);
      for (int i = 0; i < this.artworkRgb.length; ++i) {
         int argb = this.artworkRgb[i];
         int alpha = argb >>> 24;
         if (alpha == 0) {
            pixels[i] = 0;
            continue;
         }
         int r = scaleChannel(argb >> 16 & 255, fr);
         int g = scaleChannel(argb >> 8 & 255, fg);
         int b = scaleChannel(argb & 255, fb);
         pixels[i] = alpha << 24 | r << 16 | g << 8 | b;
      }

      BufferedImage tinted = new BufferedImage(this.artworkWidth, this.artworkHeight, BufferedImage.TYPE_INT_ARGB);
      tinted.setRGB(0, 0, this.artworkWidth, this.artworkHeight, pixels, 0, this.artworkWidth);
      this.textureId = BufferedImageTextureUtil.uploadTexture(tinted);
      this.tintedAccent = accentArgb;
   }

   private static int scaleChannel(int value, float factor) {
      int scaled = Math.round((float)value * factor);
      return scaled < 0 ? 0 : (scaled > 255 ? 255 : scaled);
   }

   /**
    * Draws the artwork with its top-left corner at {@code (x, y)}, sized by <b>height</b>; width follows
    * from the aspect ratio, so the artwork never distorts. The texture already carries the accent.
    */
   public void draw(float x, float y, float height, int accentArgb) {
      this.draw(x, y, height * this.aspect, height, accentArgb, 0xFFFFFFFF, 0xFFFFFFFF);
   }

   public void drawVerticalGradient(float x, float y, float height, int topArgb, int bottomArgb) {
      this.draw(x, y, height * this.aspect, height, -1, topArgb, bottomArgb);
   }

   public void drawVerticalGradientByWidth(float x, float y, float width, int topArgb, int bottomArgb) {
      this.ensureLoaded();
      if (this.aspect <= 0.0F) {
         return;
      }

      this.draw(x, y, width, width / this.aspect, -1, topArgb, bottomArgb);
   }

   /**
    * Draws the texture into an explicit box. {@code accentArgb} of {@code -1} leaves the texture as it
    * is; any other value rebuilds it for that accent first.
    */
   public void draw(float x, float y, float width, float height, int accentArgb, int topArgb, int bottomArgb) {
      if (accentArgb != -1) {
         this.prepare(accentArgb);
      } else {
         this.ensureLoaded();
      }
      if (this.textureId <= 0 || width <= 0.0F || height <= 0.0F) {
         return;
      }

      if ((topArgb >>> 24) == 0 && (bottomArgb >>> 24) == 0) {
         return;
      }

      GlStateSaver saver = STATE_SAVER;
      saver.setBlend(true);
      saver.setAlpha(false);
      saver.setDepth(false);
      saver.setCull(false);
      saver.setLighting(false);
      saver.setTexture2D(true);

      int prevBlendSrc = GlStateManager.getBlendSrcFactor();
      int prevBlendDst = GlStateManager.getBlendDstFactor();
      int prevBlendSrcAlpha = GlStateManager.getBlendSrcFactorAlpha();
      int prevBlendDstAlpha = GlStateManager.getBlendDstFactorAlpha();
      int prevAlphaFunc = GlStateManager.getAlphaFunc();
      float prevAlphaRef = GlStateManager.getAlphaRef();
      int prevActiveTexture = GlStateManager.getActiveTextureUnit();

      // alphaFunc(GREATER, 0): the open animation drives alpha from 0, and the default 0.1 test would
      // delete the mark on the first frames of the fade-in.
      GlStateManager.tryBlendFuncSeparate(
         GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA
      );
      GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
      GlStateManager.setActiveTexture(33984);
      int prevTextureOnUnit0 = GlStateManager.getBoundTexture();
      GlStateManager.bindTexture(this.textureId);
      // White: the texture already carries both the hue and the shading.
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

      Tessellator tessellator = Tessellator.getInstance();
      WorldRenderer worldRenderer = tessellator.getWorldRenderer();
      worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
      putVertex(worldRenderer, x, y + height, 0.0F, 1.0F, bottomArgb);
      putVertex(worldRenderer, x + width, y + height, 1.0F, 1.0F, bottomArgb);
      putVertex(worldRenderer, x + width, y, 1.0F, 0.0F, topArgb);
      putVertex(worldRenderer, x, y, 0.0F, 0.0F, topArgb);
      tessellator.draw();

      GlStateManager.alphaFunc(prevAlphaFunc, prevAlphaRef);
      GlStateManager.tryBlendFuncSeparate(prevBlendSrc, prevBlendDst, prevBlendSrcAlpha, prevBlendDstAlpha);
      GlStateManager.setActiveTexture(prevActiveTexture);
      GlStateManager.bindTexture(prevTextureOnUnit0);
      saver.restoreTexture2D();
      saver.restoreLighting();
      saver.restoreCull();
      saver.restoreDepth();
      saver.restoreAlpha();
      saver.restoreBlend();
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
   }

   private static void putVertex(WorldRenderer worldRenderer, float x, float y, float u, float v, int argb) {
      worldRenderer.pos((double)x, (double)y, 0.0)
         .tex((double)u, (double)v)
         .color(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24 & 255)
         .endVertex();
   }
}