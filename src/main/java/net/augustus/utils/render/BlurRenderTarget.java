package net.augustus.utils.render;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Reusable offscreen framebuffer target configured for linear filtering and edge clamping.
 * Adapted from Solana Render's CustomRenderTarget for Minecraft 1.8.9.
 */
public class BlurRenderTarget extends Framebuffer {
   public BlurRenderTarget(int width, int height) {
      super(Math.max(1, width), Math.max(1, height), false);
      this.setFramebufferColor(0.0F, 0.0F, 0.0F, 0.0F);
   }

   /**
    * Resizes this target only when the requested dimensions differ from the current size.
    */
   public boolean ensureSize(int width, int height) {
      int targetWidth = Math.max(1, width);
      int targetHeight = Math.max(1, height);
      if (this.framebufferObject < 0 || this.needsResize(targetWidth, targetHeight)) {
         this.createBindFramebuffer(targetWidth, targetHeight);
         return true;
      }
      return false;
   }

   @Override
   public void setFramebufferFilter(int filter) {
      if (this.isFramebufferSupported() && this.framebufferTexture >= 0) {
         this.framebufferFilter = GL11.GL_LINEAR;
         GlStateManager.bindTexture(this.framebufferTexture);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
         GlStateManager.bindTexture(0);
      }
   }
}
