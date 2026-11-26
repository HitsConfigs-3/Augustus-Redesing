package net.augustus.utils.render;

import java.nio.IntBuffer;
import net.augustus.utils.interfaces.MC;
import net.augustus.utils.shader.ShaderProgram;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Offscreen premultiplied-alpha compositor for scrollable glass regions.
 * Captures list/settings items at native framebuffer resolution and composites them
 * with a smooth vertical top/bottom alpha fade mask over the frosted backdrop.
 */
public final class ScrollFadeCompositor implements MC {
   private static final GlStateSaver STATE_SAVER = GlStateSaver.create();
   private static final IntBuffer PREV_SCISSOR = BufferUtils.createIntBuffer(16);

   private static Framebuffer fadeFbo;
   private static ShaderProgram fadeShader;
   private static boolean shaderInitialized = false;
   private static boolean shaderFailed = false;

   private static boolean active = false;
   private static int prevFramebuffer = 0;

   private ScrollFadeCompositor() {
   }

   /** Whether an offscreen region is currently capturing (i.e. the game framebuffer is NOT bound). */
   public static boolean isActive() {
      return active;
   }

   /**
    * Drops a capture that was begun but never composited, re-binding the framebuffer that was current when the
    * region opened.
    *
    * <p>WHY this must exist: while a region is active the compositor owns the framebuffer binding, so every
    * draw call in the rest of the frame lands in the offscreen texture instead of on screen. A region that is
    * lost (an early return, an exception outside a finally, a re-entrant call) therefore does not degrade
    * gracefully - it makes everything drawn afterwards invisible for the rest of the frame and, because
    * {@code active} stays true, suppresses every later {@link #beginRegion} call while still keeping the
    * offscreen target bound. The frame-boundary watchdog in ClickGuiScreen uses this to guarantee that the
    * overlay never leaks the binding across frames.
    */
   public static void forceAbort() {
      if (!active) {
         return;
      }

      active = false;
      OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, prevFramebuffer);
   }

   public static boolean beginRegion(float absX, float absY, float width, float height) {
      if (active || !OpenGlHelper.isFramebufferEnabled() || !ensureShader() || mc == null || mc.getFramebuffer() == null) {
         return false;
      }
      Framebuffer mainFbo = mc.getFramebuffer();
      int fbW = Math.max(1, mainFbo.framebufferTextureWidth);
      int fbH = Math.max(1, mainFbo.framebufferTextureHeight);
      if (fadeFbo == null) {
         fadeFbo = new Framebuffer(fbW, fbH, false);
      } else if (fadeFbo.framebufferWidth != fbW || fadeFbo.framebufferHeight != fbH) {
         fadeFbo.createBindFramebuffer(fbW, fbH);
      }
      if (fadeFbo.framebufferTexture <= 0) {
         return false;
      }

      prevFramebuffer = GL11.glGetInteger(36006);

      boolean scissorWas = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
      PREV_SCISSOR.clear();
      GL11.glGetInteger(GL11.GL_SCISSOR_BOX, PREV_SCISSOR);
      int sX = PREV_SCISSOR.get(0);
      int sY = PREV_SCISSOR.get(1);
      int sW = PREV_SCISSOR.get(2);
      int sH = PREV_SCISSOR.get(3);

      fadeFbo.bindFramebuffer(false);
      // Clear only the padded region rect in the offscreen target for minimal fill overhead
      int cx = Math.max(0, Math.round(absX) - 18);
      int cy = Math.max(0, fbH - Math.round(absY + height) - 8);
      int cw = Math.min(fbW - cx, Math.round(width) + 36);
      int ch = Math.min(fbH - cy, Math.round(height) + 16);
      GL11.glEnable(GL11.GL_SCISSOR_TEST);
      GL11.glScissor(cx, cy, Math.max(1, cw), Math.max(1, ch));
      GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
      GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

      if (scissorWas) {
         GL11.glScissor(sX, sY, sW, sH);
      } else {
         GL11.glDisable(GL11.GL_SCISSOR_TEST);
      }

      active = true;
      return true;
   }

   public static void endAndComposite(
      float localX,
      float localY,
      float localWidth,
      float localHeight,
      float topFadePx,
      float bottomFadePx
   ) {
      endAndComposite(localX, localY, localWidth, localHeight, topFadePx, bottomFadePx, 0.0F, 0.0F);
   }

   public static void endAndComposite(
      float localX,
      float localY,
      float localWidth,
      float localHeight,
      float topFadePx,
      float bottomFadePx,
      float leftFadePx,
      float rightFadePx
   ) {
      if (!active) {
         return;
      }
      active = false;

      OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, prevFramebuffer);
      if (fadeFbo == null || fadeFbo.framebufferTexture <= 0 || mc == null || mc.getFramebuffer() == null) {
         return;
      }

      Framebuffer mainFbo = mc.getFramebuffer();
      float fbW = (float)Math.max(1, mainFbo.framebufferTextureWidth);
      float fbH = (float)Math.max(1, mainFbo.framebufferTextureHeight);

      int prevBlendSrc = GlStateManager.getBlendSrcFactor();
      int prevBlendDst = GlStateManager.getBlendDstFactor();
      int prevBlendSrcAlpha = GlStateManager.getBlendSrcFactorAlpha();
      int prevBlendDstAlpha = GlStateManager.getBlendDstFactorAlpha();
      int prevAlphaFunc = GlStateManager.getAlphaFunc();
      float prevAlphaRef = GlStateManager.getAlphaRef();

      STATE_SAVER.setBlend(true);
      STATE_SAVER.setAlpha(false);
      STATE_SAVER.setDepth(false);
      STATE_SAVER.setCull(false);
      STATE_SAVER.setLighting(false);
      STATE_SAVER.setTexture2D(true);

      // Premultiplied alpha composition preserves antialiased edges and SDF fonts identically
      GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

      int prevActiveTex = GlStateManager.getActiveTextureUnit();
      GlStateManager.setActiveTexture(33984);
      int prevTex = GlStateManager.getBoundTexture();
      GlStateManager.bindTexture(fadeFbo.framebufferTexture);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

      fadeShader.bind();
      fadeShader.setUniform1i("u_tex", 0);
      fadeShader.setUniform2f("u_fbSize", fbW, fbH);
      fadeShader.setUniform1f("u_regionWidth", Math.max(1.0F, localWidth));
      fadeShader.setUniform1f("u_regionHeight", Math.max(1.0F, localHeight));
      fadeShader.setUniform1f("u_topFade", Math.max(0.0F, topFadePx));
      fadeShader.setUniform1f("u_bottomFade", Math.max(0.0F, bottomFadePx));
      fadeShader.setUniform1f("u_leftFade", Math.max(0.0F, leftFadePx));
      fadeShader.setUniform1f("u_rightFade", Math.max(0.0F, rightFadePx));

      Tessellator tessellator = Tessellator.getInstance();
      WorldRenderer wr = tessellator.getWorldRenderer();
      wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
      wr.pos((double)localX, (double)(localY + localHeight), 0.0).tex(0.0, 1.0).endVertex();
      wr.pos((double)(localX + localWidth), (double)(localY + localHeight), 0.0).tex(1.0, 1.0).endVertex();
      wr.pos((double)(localX + localWidth), (double)localY, 0.0).tex(1.0, 0.0).endVertex();
      wr.pos((double)localX, (double)localY, 0.0).tex(0.0, 0.0).endVertex();
      tessellator.draw();

      fadeShader.unbind();
      GlStateManager.bindTexture(prevTex);
      GlStateManager.setActiveTexture(prevActiveTex);
      GlStateManager.alphaFunc(prevAlphaFunc, prevAlphaRef);
      GlStateManager.tryBlendFuncSeparate(prevBlendSrc, prevBlendDst, prevBlendSrcAlpha, prevBlendDstAlpha);
      STATE_SAVER.restoreTexture2D();
      STATE_SAVER.restoreLighting();
      STATE_SAVER.restoreCull();
      STATE_SAVER.restoreDepth();
      STATE_SAVER.restoreAlpha();
      STATE_SAVER.restoreBlend();
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
   }

   private static boolean ensureShader() {
      if (shaderInitialized) {
         return true;
      }
      if (shaderFailed) {
         return false;
      }
      try {
         String vert = RenderSystemAssetLoader.loadAssetString("impl/glass/glass_primitive.vert");
         String frag = RenderSystemAssetLoader.loadAssetString("impl/glass/fade_mask.frag");
         fadeShader = new ShaderProgram(vert, frag);
         shaderInitialized = true;
         return true;
      } catch (Throwable t) {
         shaderFailed = true;
         t.printStackTrace();
         return false;
      }
   }
}
