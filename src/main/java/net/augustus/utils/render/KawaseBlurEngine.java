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
 * GPU Dual-Filter Kawase Blur engine adapted from Solana Render for Minecraft 1.8.9 (GLSL 120).
 *
 * <p>Uses a reusable downsampled framebuffer pyramid with hardware bilinear filtering and
 * edge clamping, caching the blurred background once per frame to avoid redundant passes.
 * Draws pure transparent blurred regions without tints, color overlays, or gradients.</p>
 */
public final class KawaseBlurEngine implements MC {
   private static final int MAX_PYRAMID_LEVELS = 4;
   private static final float DEFAULT_EDGE_SMOOTHNESS = 0.7F;

   private static final BlurRenderTarget[] PYRAMID = new BlurRenderTarget[MAX_PYRAMID_LEVELS];
   private static final IntBuffer VIEWPORT_BUFFER = BufferUtils.createIntBuffer(16);
   private static final GlStateSaver PASS_STATE_SAVER = GlStateSaver.create();
   private static final GlStateSaver DRAW_STATE_SAVER = GlStateSaver.create();

   private static ShaderProgram kawaseDownShader;
   private static ShaderProgram kawaseUpShader;
   private static ShaderProgram blurRectShader;
   private static ShaderProgram liquidGlassShader;
   private static boolean shadersInitialized = false;
   private static boolean shaderInitFailed = false;

   private static boolean frameCaptured = false;
   private static float capturedRadius = -1.0F;
   private static int capturedSourceWidth = -1;
   private static int capturedSourceHeight = -1;
   private static int capturedSourceTexture = -1;

   private KawaseBlurEngine() {
   }

   /**
    * Marks the cached per-frame blur texture as stale so the next blur draw refreshes it.
    */
   public static void invalidateFrame() {
      frameCaptured = false;
   }

   /**
    * Forces an immediate recapture of the current framebuffer for floating overlays (menus/tooltips).
    */
   public static void recapture(float radius) {
      frameCaptured = false;
      ensureCaptured(mc.getFramebuffer(), radius);
   }

   /**
    * Returns whether GPU Kawase blur can currently execute safely.
    */
   public static boolean isAvailable() {
      return OpenGlHelper.isFramebufferEnabled()
         && BlurShadowManager.isShadersCompatible()
         && ensureShaders();
   }

   /**
    * Draws a pure transparent Kawase-blurred rectangle using the cached frame blur.
    */
   public static void drawBlur(float x, float y, float width, float height, float blurRadius) {
      drawRoundedBlur(x, y, width, height, 0.0F, 0.0F, 0.0F, 0.0F, blurRadius);
   }

   /**
    * Draws a pure transparent Kawase-blurred rounded rectangle with uniform corner radius.
    */
   public static void drawRoundedBlur(float x, float y, float width, float height, float cornerRadius, float blurRadius) {
      drawRoundedBlur(x, y, width, height, cornerRadius, cornerRadius, cornerRadius, cornerRadius, blurRadius);
   }

   /**
    * Draws a pure transparent Kawase-blurred rounded rectangle with per-corner radii.
    */
   public static void drawRoundedBlur(
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
      if (width <= 0.0F || height <= 0.0F || blurRadius <= 0.0F || !isAvailable()) {
         return;
      }

      Framebuffer source = mc.getFramebuffer();
      if (!ensureCaptured(source, blurRadius) || PYRAMID[0] == null) {
         return;
      }

      drawBlurFromTarget(
         PYRAMID[0],
         x,
         y,
         width,
         height,
         topLeftRadius,
         topRightRadius,
         bottomRightRadius,
         bottomLeftRadius
      );
   }

   /**
    * Recaptures the current framebuffer (including windows behind a floating popup) and draws
    * a pure transparent rounded blur region for menus and tooltips.
    */
   public static void drawPopupBlur(float x, float y, float width, float height, float cornerRadius, float blurRadius) {
      if (width <= 0.0F || height <= 0.0F || blurRadius <= 0.0F || !isAvailable()) {
         return;
      }

      Framebuffer source = mc.getFramebuffer();
      frameCaptured = false;
      if (!ensureCaptured(source, blurRadius) || PYRAMID[0] == null) {
         return;
      }

      drawBlurFromTarget(
         PYRAMID[0],
         x,
         y,
         width,
         height,
         cornerRadius,
         cornerRadius,
         cornerRadius,
         cornerRadius
      );
      // Mark frame invalidated so any subsequent window/background draw does not reuse popup-contaminated blur.
      frameCaptured = false;
   }

   /**
    * Draws a subtle Liquid Glass rounded panel using the pure transparent Kawase blur backdrop
    * combined with optical edge refraction, subtle specular Fresnel rim, and antialiased border.
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
      drawLiquidGlass(
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
         0,
         0.0F,
         0.085F,
         1.12F,
         1.0F
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
      if (width <= 0.0F || height <= 0.0F || blurRadius <= 0.05F || globalAlpha <= 0.005F || !isAvailable() || liquidGlassShader == null) {
         return;
      }

      Framebuffer source = mc.getFramebuffer();
      if (!ensureCaptured(source, blurRadius) || PYRAMID[0] == null) {
         return;
      }

      drawLiquidGlassFromTarget(
         PYRAMID[0],
         x,
         y,
         width,
         height,
         topLeftRadius,
         topRightRadius,
         bottomRightRadius,
         bottomLeftRadius,
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
    * Draws a Liquid Glass floating popup by capturing whatever is already rendered underneath it.
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
      drawPopupLiquidGlass(
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
         0,
         0.0F,
         0.095F,
         1.15F,
         1.0F
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
      if (width <= 0.0F || height <= 0.0F || blurRadius <= 0.05F || globalAlpha <= 0.005F || !isAvailable() || liquidGlassShader == null) {
         return;
      }

      Framebuffer source = mc.getFramebuffer();
      frameCaptured = false;
      if (!ensureCaptured(source, blurRadius) || PYRAMID[0] == null) {
         return;
      }

      drawLiquidGlassFromTarget(
         PYRAMID[0],
         x,
         y,
         width,
         height,
         cornerRadius,
         cornerRadius,
         cornerRadius,
         cornerRadius,
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
      frameCaptured = false;
   }

   /**
    * Runs Dual-Filter Kawase blur on an arbitrary source framebuffer and returns the blurred target.
    * Used by {@link KawaseBlurPass} for HUD/screen blur integration.
    */
   public static Framebuffer blurToFramebuffer(Framebuffer source, int sigma) {
      if (source == null || !isAvailable()) {
         return source;
      }

      float radius = Math.max(1.0F, (float)sigma);
      if (!captureFromFramebuffer(source, radius) || PYRAMID[0] == null) {
         return source;
      }

      return PYRAMID[0];
   }

   private static boolean ensureCaptured(Framebuffer source, float blurRadius) {
      if (source == null || source.framebufferTexture <= 0) {
         return false;
      }

      if (frameCaptured
         && PYRAMID[0] != null
         && Math.abs(capturedRadius - blurRadius) < 0.05F
         && capturedSourceWidth == source.framebufferTextureWidth
         && capturedSourceHeight == source.framebufferTextureHeight
         && capturedSourceTexture == source.framebufferTexture) {
         return true;
      }

      boolean ok = captureFromFramebuffer(source, blurRadius);
      if (ok) {
         frameCaptured = true;
         capturedRadius = blurRadius;
         capturedSourceWidth = source.framebufferTextureWidth;
         capturedSourceHeight = source.framebufferTextureHeight;
         capturedSourceTexture = source.framebufferTexture;
      }
      return ok;
   }

   private static boolean captureFromFramebuffer(Framebuffer source, float radius) {
      if (source == null || source.framebufferTexture <= 0 || source.framebufferTextureWidth <= 0 || source.framebufferTextureHeight <= 0) {
         return false;
      }

      int srcWidth = source.framebufferTextureWidth;
      int srcHeight = source.framebufferTextureHeight;
      int levels = resolvePyramidLevels(radius);
      float offset = resolvePassOffset(radius, levels);

      // Save GL state before touching FBOs, matrices, viewport, and scissor.
      int prevFramebuffer = GL11.glGetInteger(36006);
      int prevActiveTexture = GlStateManager.getActiveTextureUnit();
      GlStateManager.setActiveTexture(33984);
      int prevBoundTexture = GlStateManager.getBoundTexture();
      VIEWPORT_BUFFER.clear();
      GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT_BUFFER);
      boolean scissorWasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
      if (scissorWasEnabled) {
         GL11.glDisable(GL11.GL_SCISSOR_TEST);
      }

      int prevMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
      GL11.glMatrixMode(GL11.GL_PROJECTION);
      GL11.glPushMatrix();
      GL11.glLoadIdentity();
      GL11.glOrtho(0.0, 1.0, 1.0, 0.0, -1.0, 1.0);
      GL11.glMatrixMode(GL11.GL_MODELVIEW);
      GL11.glPushMatrix();
      GL11.glLoadIdentity();

      PASS_STATE_SAVER.setBlend(false);
      PASS_STATE_SAVER.setAlpha(false);
      PASS_STATE_SAVER.setDepth(false);
      PASS_STATE_SAVER.setCull(false);
      PASS_STATE_SAVER.setLighting(false);
      PASS_STATE_SAVER.setFog(false);
      PASS_STATE_SAVER.setTexture2D(true);
      boolean prevDepthMask = GlStateManager.isDepthMaskEnabled();
      GlStateManager.depthMask(false);
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

      int savedSourceFilter = source.framebufferFilter;
      try {
         // Ensure pyramid targets exist with matching downsampled dimensions.
         for (int i = 0; i < levels; i++) {
            int levelW = Math.max(1, srcWidth >> (i + 1));
            int levelH = Math.max(1, srcHeight >> (i + 1));
            if (PYRAMID[i] == null) {
               PYRAMID[i] = new BlurRenderTarget(levelW, levelH);
            } else {
               PYRAMID[i].ensureSize(levelW, levelH);
            }
         }

         // Temporarily sample the main scene framebuffer with GL_LINEAR + CLAMP_TO_EDGE on pass 0.
         GlStateManager.bindTexture(source.framebufferTexture);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
         GlStateManager.bindTexture(0);

         // Downsample pass chain: source -> L0 -> L1 -> ... -> L(levels-1) without per-pass program churn.
         GlStateManager.setActiveTexture(33984);
         kawaseDownShader.bind();
         kawaseDownShader.setUniform1f("offset", offset);
         for (int i = 0; i < levels; i++) {
            Framebuffer passSrc = i == 0 ? source : PYRAMID[i - 1];
            BlurRenderTarget passDst = PYRAMID[i];
            passDst.bindFramebuffer(true);
            kawaseDownShader.setUniform2f(
               "halfpixel",
               0.5F / (float)passSrc.framebufferTextureWidth,
               0.5F / (float)passSrc.framebufferTextureHeight
            );
            kawaseDownShader.bindTextureUnit0Fast("tex", passSrc.framebufferTexture);
            drawUnitQuad();
         }
         kawaseDownShader.unbind();

         // Upsample pass chain: L(levels-1) -> ... -> L1 -> L0
         if (levels > 1) {
            kawaseUpShader.bind();
            kawaseUpShader.setUniform1f("offset", offset);
            for (int i = levels - 1; i > 0; i--) {
               Framebuffer passSrc = PYRAMID[i];
               BlurRenderTarget passDst = PYRAMID[i - 1];
               passDst.bindFramebuffer(true);
               kawaseUpShader.setUniform2f(
                  "halfpixel",
                  0.5F / (float)passSrc.framebufferTextureWidth,
                  0.5F / (float)passSrc.framebufferTextureHeight
               );
               kawaseUpShader.bindTextureUnit0Fast("tex", passSrc.framebufferTexture);
               drawUnitQuad();
            }
            kawaseUpShader.unbind();
         }

         return true;
      } finally {
         // Restore source texture filter.
         GlStateManager.bindTexture(source.framebufferTexture);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, savedSourceFilter);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, savedSourceFilter);
         GlStateManager.bindTexture(0);

         // Restore GL state, matrices, viewport, scissor, and previous framebuffer.
         GlStateManager.depthMask(prevDepthMask);
         PASS_STATE_SAVER.restoreTexture2D();
         PASS_STATE_SAVER.restoreFog();
         PASS_STATE_SAVER.restoreLighting();
         PASS_STATE_SAVER.restoreCull();
         PASS_STATE_SAVER.restoreDepth();
         PASS_STATE_SAVER.restoreAlpha();
         PASS_STATE_SAVER.restoreBlend();

         GL11.glMatrixMode(GL11.GL_MODELVIEW);
         GL11.glPopMatrix();
         GL11.glMatrixMode(GL11.GL_PROJECTION);
         GL11.glPopMatrix();
         GL11.glMatrixMode(prevMatrixMode);

         GlStateManager.setActiveTexture(33984);
         GlStateManager.bindTexture(prevBoundTexture);
         GlStateManager.setActiveTexture(prevActiveTexture);
         OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, prevFramebuffer);
         GL11.glViewport(
            VIEWPORT_BUFFER.get(0),
            VIEWPORT_BUFFER.get(1),
            VIEWPORT_BUFFER.get(2),
            VIEWPORT_BUFFER.get(3)
         );
         if (scissorWasEnabled) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
         }
      }
   }

   private static void drawBlurFromTarget(
      BlurRenderTarget target,
      float x,
      float y,
      float width,
      float height,
      float topLeftRadius,
      float topRightRadius,
      float bottomRightRadius,
      float bottomLeftRadius
   ) {
      int prevBlendSrc = GlStateManager.getBlendSrcFactor();
      int prevBlendDst = GlStateManager.getBlendDstFactor();
      int prevBlendSrcAlpha = GlStateManager.getBlendSrcFactorAlpha();
      int prevBlendDstAlpha = GlStateManager.getBlendDstFactorAlpha();
      int prevAlphaFunc = GlStateManager.getAlphaFunc();
      float prevAlphaRef = GlStateManager.getAlphaRef();

      DRAW_STATE_SAVER.setBlend(true);
      DRAW_STATE_SAVER.setAlpha(false);
      DRAW_STATE_SAVER.setDepth(false);
      DRAW_STATE_SAVER.setCull(false);
      DRAW_STATE_SAVER.setLighting(false);
      DRAW_STATE_SAVER.setTexture2D(true);

      GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

      blurRectShader.bind();
      blurRectShader.bindTextureUniform("tex", target);
      blurRectShader.setUniform2f("size", width, height);
      blurRectShader.setUniform4f("cornerRadius", topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius);
      blurRectShader.setUniform1f("smoothness", DEFAULT_EDGE_SMOOTHNESS);

      Tessellator tessellator = Tessellator.getInstance();
      WorldRenderer worldRenderer = tessellator.getWorldRenderer();
      worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
      worldRenderer.pos((double)x, (double)(y + height), 0.0).tex(0.0, 1.0).endVertex();
      worldRenderer.pos((double)(x + width), (double)(y + height), 0.0).tex(1.0, 1.0).endVertex();
      worldRenderer.pos((double)(x + width), (double)y, 0.0).tex(1.0, 0.0).endVertex();
      worldRenderer.pos((double)x, (double)y, 0.0).tex(0.0, 0.0).endVertex();
      tessellator.draw();

      blurRectShader.unbind();

      GlStateManager.alphaFunc(prevAlphaFunc, prevAlphaRef);
      GlStateManager.tryBlendFuncSeparate(prevBlendSrc, prevBlendDst, prevBlendSrcAlpha, prevBlendDstAlpha);
      DRAW_STATE_SAVER.restoreTexture2D();
      DRAW_STATE_SAVER.restoreLighting();
      DRAW_STATE_SAVER.restoreCull();
      DRAW_STATE_SAVER.restoreDepth();
      DRAW_STATE_SAVER.restoreAlpha();
      DRAW_STATE_SAVER.restoreBlend();
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
   }

   private static void drawLiquidGlassFromTarget(
      BlurRenderTarget target,
      float x,
      float y,
      float width,
      float height,
      float topLeftRadius,
      float topRightRadius,
      float bottomRightRadius,
      float bottomLeftRadius,
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
      int prevBlendSrc = GlStateManager.getBlendSrcFactor();
      int prevBlendDst = GlStateManager.getBlendDstFactor();
      int prevBlendSrcAlpha = GlStateManager.getBlendSrcFactorAlpha();
      int prevBlendDstAlpha = GlStateManager.getBlendDstFactorAlpha();
      int prevAlphaFunc = GlStateManager.getAlphaFunc();
      float prevAlphaRef = GlStateManager.getAlphaRef();

      DRAW_STATE_SAVER.setBlend(true);
      DRAW_STATE_SAVER.setAlpha(false);
      DRAW_STATE_SAVER.setDepth(false);
      DRAW_STATE_SAVER.setCull(false);
      DRAW_STATE_SAVER.setLighting(false);
      DRAW_STATE_SAVER.setTexture2D(true);

      GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

      float br = (float)(borderArgb >> 16 & 255) / 255.0F;
      float bg = (float)(borderArgb >> 8 & 255) / 255.0F;
      float bb = (float)(borderArgb & 255) / 255.0F;
      float ba = (float)(borderArgb >>> 24 & 255) / 255.0F;

      float tr = (float)(glassTintArgb >> 16 & 255) / 255.0F;
      float tg = (float)(glassTintArgb >> 8 & 255) / 255.0F;
      float tb = (float)(glassTintArgb & 255) / 255.0F;
      float ta = (float)(glassTintArgb >>> 24 & 255) / 255.0F;

      int ambientArgb = net.augustus.ui.modern.render.UiTheme.windowAmbientTintArgb();
      float ar = (float)(ambientArgb >> 16 & 255) / 255.0F;
      float ag = (float)(ambientArgb >> 8 & 255) / 255.0F;
      float ab = (float)(ambientArgb & 255) / 255.0F;
      float aa = (float)(ambientArgb >>> 24 & 255) / 255.0F;

      int borderTop = borderArgb != 0 ? net.augustus.ui.modern.render.UiTheme.glassTopBorderArgb(borderArgb) : 0;
      int borderBot = borderArgb != 0 ? net.augustus.ui.modern.render.UiTheme.glassBottomBorderArgb(borderArgb) : 0;
      br = (float)(borderTop >> 16 & 255) / 255.0F;
      bg = (float)(borderTop >> 8 & 255) / 255.0F;
      bb = (float)(borderTop & 255) / 255.0F;
      ba = (float)(borderTop >>> 24 & 255) / 255.0F;
      float bbr = (float)(borderBot >> 16 & 255) / 255.0F;
      float bbg = (float)(borderBot >> 8 & 255) / 255.0F;
      float bbb = (float)(borderBot & 255) / 255.0F;
      float bba = (float)(borderBot >>> 24 & 255) / 255.0F;

      liquidGlassShader.bind();
      liquidGlassShader.bindTextureUniform("tex", target);
      liquidGlassShader.setUniform2f("size", width, height);
      liquidGlassShader.setUniform4f("cornerRadius", topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius);
      liquidGlassShader.setUniform1f("smoothness", 0.85F);
      liquidGlassShader.setUniform1f("fresnelPower", 10.0F);
      liquidGlassShader.setUniform1f("fresnelAlpha", fresnelAlpha);
      liquidGlassShader.setUniform1f("fresnelMix", 0.82F);
      liquidGlassShader.setUniform1f("distortStrength", distortStrength);
      liquidGlassShader.setUniform4f("glassTint", tr, tg, tb, ta);
      liquidGlassShader.setUniform4f("ambientTint", ar, ag, ab, aa);
      liquidGlassShader.setUniform1f("frostGrain", frostGrain);
      liquidGlassShader.setUniform1f("sheenStrength", sheenStrength);
      liquidGlassShader.setUniform1f("saturationBoost", saturationBoost);
      liquidGlassShader.setUniform1f("globalAlpha", globalAlpha);
      liquidGlassShader.setUniform1f("borderWidth", borderWidth);
      liquidGlassShader.setUniform4f("borderColor", br, bg, bb, ba);
      liquidGlassShader.setUniform4f("borderColorBottom", bbr, bbg, bbb, bba);
      liquidGlassShader.setUniform4f("outerShadowColor", 0.0F, 0.0F, 0.0F, 0.28F);

      Tessellator tessellator = Tessellator.getInstance();
      WorldRenderer worldRenderer = tessellator.getWorldRenderer();
      worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
      worldRenderer.pos((double)x, (double)(y + height), 0.0).tex(0.0, 1.0).endVertex();
      worldRenderer.pos((double)(x + width), (double)(y + height), 0.0).tex(1.0, 1.0).endVertex();
      worldRenderer.pos((double)(x + width), (double)y, 0.0).tex(1.0, 0.0).endVertex();
      worldRenderer.pos((double)x, (double)y, 0.0).tex(0.0, 0.0).endVertex();
      tessellator.draw();

      liquidGlassShader.unbind();

      GlStateManager.alphaFunc(prevAlphaFunc, prevAlphaRef);
      GlStateManager.tryBlendFuncSeparate(prevBlendSrc, prevBlendDst, prevBlendSrcAlpha, prevBlendDstAlpha);
      DRAW_STATE_SAVER.restoreTexture2D();
      DRAW_STATE_SAVER.restoreLighting();
      DRAW_STATE_SAVER.restoreCull();
      DRAW_STATE_SAVER.restoreDepth();
      DRAW_STATE_SAVER.restoreAlpha();
      DRAW_STATE_SAVER.restoreBlend();
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
   }

   private static void drawUnitQuad() {
      Tessellator tessellator = Tessellator.getInstance();
      WorldRenderer worldRenderer = tessellator.getWorldRenderer();
      worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
      worldRenderer.pos(0.0, 1.0, 0.0).tex(0.0, 0.0).endVertex();
      worldRenderer.pos(1.0, 1.0, 0.0).tex(1.0, 0.0).endVertex();
      worldRenderer.pos(1.0, 0.0, 0.0).tex(1.0, 1.0).endVertex();
      worldRenderer.pos(0.0, 0.0, 0.0).tex(0.0, 1.0).endVertex();
      tessellator.draw();
   }

   private static int resolvePyramidLevels(float radius) {
      if (radius < 6.0F) {
         return 2;
      }
      if (radius < 14.0F) {
         return 3;
      }
      return 4;
   }

   private static float resolvePassOffset(float radius, int levels) {
      if (levels <= 2) {
         return 0.75F + Math.max(0.0F, Math.min(1.0F, radius / 6.0F)) * 0.85F;
      }
      if (levels == 3) {
         return 1.0F + Math.max(0.0F, Math.min(1.0F, (radius - 6.0F) / 8.0F)) * 1.1F;
      }
      return 1.2F + Math.max(0.0F, Math.min(1.0F, (radius - 14.0F) / 16.0F)) * 1.3F;
   }

   private static boolean ensureShaders() {
      if (shadersInitialized) {
         return true;
      }
      if (shaderInitFailed) {
         return false;
      }

      try {
         String passVert = RenderSystemAssetLoader.loadAssetString("impl/kawase/kawase_pass.vert");
         String downFrag = RenderSystemAssetLoader.loadAssetString("impl/kawase/kawase_down.frag");
         String upFrag = RenderSystemAssetLoader.loadAssetString("impl/kawase/kawase_up.frag");
         String rectVert = RenderSystemAssetLoader.loadAssetString("impl/kawase/blur_rect.vert");
         String rectFrag = RenderSystemAssetLoader.loadAssetString("impl/kawase/blur_rect.frag");
         String glassVert = RenderSystemAssetLoader.loadAssetString("impl/glass/liquid_glass.vert");
         String glassFrag = RenderSystemAssetLoader.loadAssetString("impl/glass/liquid_glass.frag");

         kawaseDownShader = new ShaderProgram(passVert, downFrag);
         kawaseUpShader = new ShaderProgram(passVert, upFrag);
         blurRectShader = new ShaderProgram(rectVert, rectFrag);
         liquidGlassShader = new ShaderProgram(glassVert, glassFrag);
         shadersInitialized = true;
         return true;
      } catch (Throwable t) {
         shaderInitFailed = true;
         t.printStackTrace();
         return false;
      }
   }
}
