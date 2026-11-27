package net.augustus.render;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import net.augustus.utils.render.GlStateSaver;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * A PNG bundled inside the client JAR, decoded once and drawn as an alpha-blended, colour-tinted quad.
 *
 * <p>WHY not the Minecraft texture manager: {@code Gui.drawTexturedModalRect} hard-codes a 256x256 atlas
 * (it divides the texture coordinates by 256), so it can only address sprite sheets of that exact size.
 * A single mark needs its own texture at its own resolution, which is what this loader uploads; the image
 * keeps its intrinsic proportions, so callers size it by height alone and never stretch it.
 *
 * <p>WHY the GL state dance: the quad goes through the fixed-function pipeline (glColor + bound texture)
 * while the surrounding ClickGUI paints through shader-backed drawers. Every piece of state this method
 * touches is captured and restored, so a mark can be dropped into the middle of any draw pass without
 * changing what the next widget renders.
 *
 * <p>The image is uploaded at the resolution stored in the jar. Because there are no mipmaps (the texture
 * is not power-of-two), the stored size should be within a few times the size it is drawn at: bilinear
 * minification under-samples the diagonals far beyond that, while a larger source adds nothing but aliasing.
 *
 * <p>Not thread-safe by design: it uploads on first use, so it must be drawn from the render thread only.
 */
public final class ClasspathTexture {
   /**
    * One saver shared by every instance, like the SDF font renderer's and GlassRenderer's own savers, so a
    * texture drawn every frame does not allocate one per draw. Safe because {@link #draw} never re-enters.
    */
   private static final GlStateSaver STATE_SAVER = GlStateSaver.create();

   private final String resourcePath;
   /** GL texture id, or {@code -1} while unloaded / when the resource could not be decoded. */
   private int textureId = -1;
   /** width / height of the decoded image, used to preserve its proportions. */
   private float aspect = 1.0F;
   /** One attempt only: a missing resource must not retry (and re-hit the classpath) every frame. */
   private boolean loadAttempted;

   private ClasspathTexture(String resourcePath) {
      this.resourcePath = resourcePath;
   }

   /**
    * Declares a texture. Nothing is read or uploaded here: the first draw (or {@link #isReady()}) does that,
    * once the render thread owns a GL context.
    */
   public static ClasspathTexture of(String resourcePath) {
      return new ClasspathTexture(resourcePath);
   }

   /** True once the PNG is decoded and uploaded; false when it is missing or unreadable. */
   public boolean isReady() {
      this.ensureLoaded();
      return this.textureId > 0;
   }

   /** Intrinsic width / height ratio of the image, valid whenever {@link #isReady()} is true. */
   public float getAspect() {
      this.ensureLoaded();
      return this.aspect;
   }

   /**
    * Draws the image with its top-left corner at {@code (x, y)} and the given height, tinted and faded by
    * {@code argb}. Width follows from the aspect ratio, so proportions survive any size.
    */
   public void draw(float x, float y, float height, int argb) {
      this.ensureLoaded();
      int alpha = argb >>> 24 & 255;
      if (this.textureId <= 0 || height <= 0.0F || alpha == 0) {
         return;
      }

      float width = height * this.aspect;
      int red = argb >> 16 & 255;
      int green = argb >> 8 & 255;
      int blue = argb & 255;

      GlStateSaver saver = STATE_SAVER;
      saver.setBlend(true);
      saver.setAlpha(false);
      saver.setDepth(false);
      saver.setCull(false);
      saver.setLighting(false);
      saver.setTexture2D(true);

      // Blend factors and the alpha test are not covered by GlStateSaver, so they are read back manually
      // (same pair of calls the SDF font renderer makes around its own textured quads).
      int prevBlendSrc = GlStateManager.getBlendSrcFactor();
      int prevBlendDst = GlStateManager.getBlendDstFactor();
      int prevBlendSrcAlpha = GlStateManager.getBlendSrcFactorAlpha();
      int prevBlendDstAlpha = GlStateManager.getBlendDstFactorAlpha();
      int prevAlphaFunc = GlStateManager.getAlphaFunc();
      float prevAlphaRef = GlStateManager.getAlphaRef();
      int prevActiveTexture = GlStateManager.getActiveTextureUnit();
      int prevTextureOnUnit0;

      // alphaFunc(GREATER, 0.0F): the fade-in envelope drives alpha from 0, and the default alpha test
      // (0.1) would delete the mark outright on the first frames of the open animation.
      GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
      GlStateManager.setActiveTexture(33984);
      // Read unit 0's texture AFTER switching to it: getBoundTexture() reports the bound texture of the
      // active unit, so reading it earlier would capture another unit's texture and restore it here.
      prevTextureOnUnit0 = GlStateManager.getBoundTexture();
      GlStateManager.bindTexture(this.textureId);
      GlStateManager.color((float)red / 255.0F, (float)green / 255.0F, (float)blue / 255.0F, (float)alpha / 255.0F);

      Tessellator tessellator = Tessellator.getInstance();
      WorldRenderer worldRenderer = tessellator.getWorldRenderer();
      worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
      // BufferedImageTextureUtil uploads row 0 first, so v = 0 is the image's top row: pairing v = 0 with
      // the quad's top edge keeps the art upright under the GUI's downward-growing y axis.
      worldRenderer.pos((double)x, (double)(y + height), 0.0).tex(0.0, 1.0).endVertex();
      worldRenderer.pos((double)(x + width), (double)(y + height), 0.0).tex(1.0, 1.0).endVertex();
      worldRenderer.pos((double)(x + width), (double)y, 0.0).tex(1.0, 0.0).endVertex();
      worldRenderer.pos((double)x, (double)y, 0.0).tex(0.0, 0.0).endVertex();
      tessellator.draw();

      GlStateManager.bindTexture(prevTextureOnUnit0);
      GlStateManager.setActiveTexture(prevActiveTexture);
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
      GlStateManager.alphaFunc(prevAlphaFunc, prevAlphaRef);
      GlStateManager.tryBlendFuncSeparate(prevBlendSrc, prevBlendDst, prevBlendSrcAlpha, prevBlendDstAlpha);
      saver.restoreTexture2D();
      saver.restoreLighting();
      saver.restoreCull();
      saver.restoreDepth();
      saver.restoreAlpha();
      saver.restoreBlend();
   }

   private void ensureLoaded() {
      if (this.textureId > 0 || this.loadAttempted) {
         return;
      }

      this.loadAttempted = true;
      BufferedImage image = null;
      try (InputStream in = ClasspathTexture.class.getResourceAsStream(this.resourcePath)) {
         if (in != null) {
            image = ImageIO.read(in);
         }
      } catch (Throwable ignored) {
         image = null;
      }

      if (image == null) {
         return;
      }

      try {
         this.aspect = (float)image.getWidth() / (float)image.getHeight();
         this.textureId = BufferedImageTextureUtil.uploadTexture(image);
      } catch (Throwable ignored) {
         this.textureId = -1;
      }
   }
}
