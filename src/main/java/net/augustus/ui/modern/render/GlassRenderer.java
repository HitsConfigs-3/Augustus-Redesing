package net.augustus.ui.modern.render;

import java.nio.IntBuffer;
import net.augustus.ClientManagers;
import net.augustus.render.RenderUtil;
import net.augustus.utils.render.BlurComponent;
import net.augustus.utils.render.GlStateSaver;
import net.augustus.utils.render.RenderSystemAssetLoader;
import net.augustus.utils.shader.ShaderProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Antialiased SDF vector primitive & subtle Liquid Glass renderer (GLSL 120).
 *
 * <p>Combines Solana Render's Fresnel Liquid Glass look and crisp AA borders with
 * IllusionIndex's GLSL 120 signed-distance shaders, multi-tier depth elevation,
 * and localized cursor hover lighting.</p>
 */
public final class GlassRenderer {
   private static final float DEFAULT_SMOOTHNESS = 0.55F;
   private static final GlStateSaver STATE_SAVER = GlStateSaver.create();
   private static final IntBuffer SCISSOR_BOX_BUF = BufferUtils.createIntBuffer(16);
   private static final int MAX_SCISSOR_DEPTH = 16;
   private static final int[][] SCISSOR_STACK = new int[MAX_SCISSOR_DEPTH][5];
   private static int scissorDepth = 0;

   private static ShaderProgram primitiveShader;
   private static boolean shaderInitialized = false;
   private static boolean shaderFailed = false;
   /** Separate program for {@link #drawRadialGlow}: the shape program's uniform set stays untouched. */
   private static ShaderProgram glowShader;
   private static boolean glowShaderInitialized = false;
   private static boolean glowShaderFailed = false;

   /**
    * The overlay's open/close envelope, 1 while the ClickGUI is settled and 0 when it has fully closed.
    *
    * <p>WHY the renderer needs its own copy: everything the overlay draws <em>on top of the window shell</em>
    * - the cards, wells and controls that go through {@link #drawGlassMaterialPane} - used to be composited
    * at the level's own coverage with no transition term at all, because the only envelope the shell knew
    * about was the one threaded into {@code getOpenProgress()} (window surface, sidebar tint, window
    * chrome). Fading the shell therefore left its nested panes at full strength: on the exit frame the
    * window dissolved around a set of bright plates that then vanished with it, which is the "the cards
    * pop out at the end" the close animation showed. Multiplying the envelope into the nested panes'
    * coverage makes every surface of the overlay leave together.
    *
    * <p>It is set once per frame, before anything is drawn, from the one sampler that already advances the
    * envelope ({@code ClickGuiScreen.drawScreen}); anything that draws outside that frame - the HUD, the
    * offscreen warm-up - keeps the default of 1 and is therefore unaffected.
    */
   private static float overlayEnvelope = 1.0F;

   private GlassRenderer() {
   }

   /**
    * Publishes the overlay's open/close envelope for the frame about to be drawn. Called once per frame by
    * the ClickGUI screen; see {@link #overlayEnvelope} for why the renderer needs it at all.
    */
   public static void setOverlayEnvelope(float envelope) {
      overlayEnvelope = UiAnimation.clamp01(envelope);
   }

   /** Current overlay open/close envelope, 1 when the overlay is settled or not drawn at all. */
   public static float overlayEnvelope() {
      return overlayEnvelope;
   }

   /**
    * {@code argb} with its alpha multiplied by the frame's overlay envelope (RGB untouched).
    *
    * <p>Only for colours this renderer computes itself. A caller-supplied colour is the caller's to fade
    * (the overlay's window passes every one of them through {@code ClickGuiScreen.fadeArgb}), and fading
    * it here as well would apply the envelope at the square of its intended rate.
    */
   private static int envelopeArgb(int argb) {
      return overlayEnvelope >= 0.999F ? argb : UiTheme.scaleAlpha(argb, overlayEnvelope);
   }

   /**
    * Intersects the current OpenGL scissor region with the horizontal band [absTopY, absBottomY]
    * in screen framebuffer coordinates (Y increasing downwards, scale 1.0). Must be balanced with {@link #popScissor()}.
    */
   public static void pushVerticalScissor(float absTopY, float absBottomY) {
      Minecraft mc = Minecraft.getMinecraft();
      int fbW = mc != null && mc.getFramebuffer() != null ? mc.getFramebuffer().framebufferWidth : 1920;
      int fbH = mc != null && mc.getFramebuffer() != null ? mc.getFramebuffer().framebufferHeight : 1080;
      boolean wasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
      SCISSOR_BOX_BUF.clear();
      GL11.glGetInteger(GL11.GL_SCISSOR_BOX, SCISSOR_BOX_BUF);
      int prevX = SCISSOR_BOX_BUF.get(0);
      int prevY = SCISSOR_BOX_BUF.get(1);
      int prevW = SCISSOR_BOX_BUF.get(2);
      int prevH = SCISSOR_BOX_BUF.get(3);

      if (scissorDepth < MAX_SCISSOR_DEPTH) {
         SCISSOR_STACK[scissorDepth][0] = wasEnabled ? 1 : 0;
         SCISSOR_STACK[scissorDepth][1] = prevX;
         SCISSOR_STACK[scissorDepth][2] = prevY;
         SCISSOR_STACK[scissorDepth][3] = prevW;
         SCISSOR_STACK[scissorDepth][4] = prevH;
      }
      scissorDepth++;

      int glBottom = Math.max(0, Math.round((float)fbH - absBottomY));
      int glTop = Math.min(fbH, Math.round((float)fbH - absTopY));
      int newX = wasEnabled ? prevX : 0;
      int newRight = wasEnabled ? prevX + prevW : fbW;
      int newY = wasEnabled ? Math.max(prevY, glBottom) : glBottom;
      int newTop = wasEnabled ? Math.min(prevY + prevH, glTop) : glTop;

      GL11.glEnable(GL11.GL_SCISSOR_TEST);
      GL11.glScissor(newX, newY, Math.max(0, newRight - newX), Math.max(0, newTop - newY));
   }

   public static void popScissor() {
      if (scissorDepth <= 0) {
         return;
      }
      scissorDepth--;
      if (scissorDepth < MAX_SCISSOR_DEPTH) {
         int[] saved = SCISSOR_STACK[scissorDepth];
         GL11.glScissor(saved[1], saved[2], saved[3], saved[4]);
         if (saved[0] == 1) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
         } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
         }
      }
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
         String frag = RenderSystemAssetLoader.loadAssetString("impl/glass/glass_primitive.frag");
         primitiveShader = new ShaderProgram(vert, frag);
         shaderInitialized = true;
         return true;
      } catch (Throwable t) {
         shaderFailed = true;
         t.printStackTrace();
         return false;
      }
   }

   /**
    * Draws a soft elliptical wash clipped by a rounded rectangle, in one analytical pass.
    *
    * <p>The reference's brand area carries a violet glow that peaks at the window's top-left corner and
    * is gone by roughly 230 px across and 90 px down. It cannot be a plain glow primitive: the GUI's
    * only scissor is a rectangle, so anything painted near that corner would spill past the window's own
    * 15 px radius and leave a violet square notch. Evaluating the Gaussian and the round-rect coverage
    * together keeps the wash inside the silhouette.
    *
    * @param cx        glow centre, in the same coordinate space as {@code x/y} but local to the box
    * @param rx,ry     per-axis falloff scale; {@code exp(-exponent)} at that distance
    * @param ex,ey     per-axis exponents, so the wash can die out faster across than down
    * @param argb      glow colour, its alpha being the peak coverage at the centre
    */
   public static void drawRadialGlow(
      float x,
      float y,
      float width,
      float height,
      float radius,
      float cx,
      float cy,
      float rx,
      float ry,
      float ex,
      float ey,
      int argb
   ) {
      if (width <= 0.0F || height <= 0.0F || (argb >>> 24) == 0 || rx <= 0.5F || ry <= 0.5F) {
         return;
      }
      if (!ensureGlowShader()) {
         return;
      }

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
      STATE_SAVER.setTexture2D(false);
      GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

      glowShader.bind();
      glowShader.setUniform2f("size", width, height);
      glowShader.setUniform4f("cornerRadius", radius, radius, radius, radius);
      glowShader.setUniform2f("glowCenter", cx - x, cy - y);
      glowShader.setUniform2f("glowRadius", rx, ry);
      glowShader.setUniform2f("glowExponent", ex, ey);
      glowShader.setUniform1f("smoothness", DEFAULT_SMOOTHNESS);
      glowShader.setUniform4f(
         "glowColor",
         (float)(argb >> 16 & 255) / 255.0F,
         (float)(argb >> 8 & 255) / 255.0F,
         (float)(argb & 255) / 255.0F,
         (float)(argb >>> 24 & 255) / 255.0F
      );

      Tessellator tessellator = Tessellator.getInstance();
      WorldRenderer wr = tessellator.getWorldRenderer();
      wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
      wr.pos((double)x, (double)(y + height), 0.0).tex(0.0, 1.0).endVertex();
      wr.pos((double)(x + width), (double)(y + height), 0.0).tex(1.0, 1.0).endVertex();
      wr.pos((double)(x + width), (double)y, 0.0).tex(1.0, 0.0).endVertex();
      wr.pos((double)x, (double)y, 0.0).tex(0.0, 0.0).endVertex();
      tessellator.draw();

      glowShader.unbind();

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

   private static boolean ensureGlowShader() {
      if (glowShaderInitialized) {
         return true;
      }
      if (glowShaderFailed) {
         return false;
      }
      try {
         String vert = RenderSystemAssetLoader.loadAssetString("impl/glass/glass_primitive.vert");
         String frag = RenderSystemAssetLoader.loadAssetString("impl/glass/glass_glow.frag");
         glowShader = new ShaderProgram(vert, frag);
         glowShaderInitialized = true;
         return true;
      } catch (Throwable t) {
         glowShaderFailed = true;
         t.printStackTrace();
         return false;
      }
   }

   public static void drawRoundedRect(float x, float y, float width, float height, float radius, int fillArgb) {
      drawRoundedBox(x, y, width, height, radius, radius, radius, radius, fillArgb, 0, 0.0F);
   }

   public static void drawRoundedRect(
      float x,
      float y,
      float width,
      float height,
      float topLeft,
      float topRight,
      float bottomRight,
      float bottomLeft,
      int fillArgb
   ) {
      drawRoundedBox(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, fillArgb, 0, 0.0F);
   }

   public static void drawRoundedOutline(float x, float y, float width, float height, float radius, float borderWidth, int borderArgb) {
      drawRoundedBox(x, y, width, height, radius, radius, radius, radius, 0, borderArgb, borderWidth);
   }

   public static void drawRoundedBox(
      float x,
      float y,
      float width,
      float height,
      float radius,
      int fillArgb,
      int borderArgb,
      float borderWidth
   ) {
      drawRoundedBox(x, y, width, height, radius, radius, radius, radius, fillArgb, borderArgb, borderWidth);
   }

   public static void drawRoundedBox(
      float x,
      float y,
      float width,
      float height,
      float topLeft,
      float topRight,
      float bottomRight,
      float bottomLeft,
      int fillArgb,
      int borderArgb,
      float borderWidth
   ) {
      drawPrimitiveInternal(
         x,
         y,
         width,
         height,
         topLeft,
         topRight,
         bottomRight,
         bottomLeft,
         fillArgb,
         0,
         0.0F,
         borderArgb,
         0,
         borderWidth,
         0.0F,
         0.0F,
         -9999.0F,
         -9999.0F,
         0.0F,
         0.0F,
         0,
         0.0F,
         0.0F,
         0.0F,
         0.0F,
         0.0F
      );
   }

   public static void drawRoundedGradientBox(
      float x,
      float y,
      float width,
      float height,
      float cornerRadius,
      int topFillArgb,
      int bottomFillArgb,
      int borderArgb,
      float borderWidth,
      float topHighlightAlpha
   ) {
      drawRoundedGradientBox(
         x,
         y,
         width,
         height,
         cornerRadius,
         cornerRadius,
         cornerRadius,
         cornerRadius,
         topFillArgb,
         bottomFillArgb,
         borderArgb,
         borderWidth,
         topHighlightAlpha
      );
   }

   public static void drawRoundedGradientBox(
      float x,
      float y,
      float width,
      float height,
      float topLeft,
      float topRight,
      float bottomRight,
      float bottomLeft,
      int topFillArgb,
      int bottomFillArgb,
      int borderArgb,
      float borderWidth,
      float topHighlightAlpha
   ) {
      drawRoundedGradientBox(
         x,
         y,
         width,
         height,
         topLeft,
         topRight,
         bottomRight,
         bottomLeft,
         topFillArgb,
         bottomFillArgb,
         borderArgb,
         borderWidth,
         topHighlightAlpha,
         0.0F,
         -9999.0F,
         -9999.0F,
         0.0F,
         0.0F,
         0
      );
   }

   public static void drawRoundedGradientBox(
      float x,
      float y,
      float width,
      float height,
      float topLeft,
      float topRight,
      float bottomRight,
      float bottomLeft,
      int topFillArgb,
      int bottomFillArgb,
      int borderArgb,
      float borderWidth,
      float topHighlightAlpha,
      float innerBottomAlpha,
      float mouseX,
      float mouseY,
      float hoverLightAlpha,
      float hoverLightRadius,
      int hoverLightArgb
   ) {
      int topBorder = borderArgb != 0 ? UiTheme.glassTopBorderArgb(borderArgb) : 0;
      int bottomBorder = borderArgb != 0 ? UiTheme.glassBottomBorderArgb(borderArgb) : 0;
      drawPrimitiveInternal(
         x,
         y,
         width,
         height,
         topLeft,
         topRight,
         bottomRight,
         bottomLeft,
         topFillArgb,
         bottomFillArgb,
         1.0F,
         topBorder,
         bottomBorder,
         borderWidth,
         topHighlightAlpha,
         innerBottomAlpha,
         mouseX - x,
         mouseY - y,
         hoverLightAlpha,
         hoverLightRadius,
         hoverLightArgb,
         0.0F,
         0.0F,
         0.0F,
         0.0F,
         0.0F
      );
   }

   /**
    * The same primitive as {@link #drawRoundedGradientBox}, with its fill gradient rotated: the shape
    * interpolates from {@code leadingFillArgb} on its own left edge to {@code trailingFillArgb} on its
    * right edge instead of from top to bottom. Everything else - the SDF, the anti-aliasing, the corner
    * radii - is the same code path, so a horizontal gradient is a rotation of one shape rather than a
    * second shape that has to be kept in step with the first.
    *
    * <p>WHY it exists: the reference draws its selected sidebar item as a plate that is brightest on its
    * leading edge and settles towards its trailing one. The vertical gradient this renderer already had
    * could not express that, so the alternative was a flat plate or a hand-rolled stack of bands - the
    * first loses the reference's depth cue, the second reintroduces exactly the seams this renderer
    * exists to avoid.
    */
   public static void drawRoundedGradientBoxHorizontal(
      float x,
      float y,
      float width,
      float height,
      float radius,
      int leadingFillArgb,
      int trailingFillArgb,
      float gradientMix
   ) {
      if (width <= 0.0F || height <= 0.0F) {
         return;
      }
      drawPrimitiveInternal(
         x,
         y,
         width,
         height,
         radius,
         radius,
         radius,
         radius,
         leadingFillArgb,
         trailingFillArgb,
         gradientMix,
         0,
         0,
         0.0F,
         0.0F,
         0.0F,
         -9999.0F,
         -9999.0F,
         0.0F,
         0.0F,
         0,
         0.0F,
         0.0F,
         0.0F,
         0.0F,
         1.0F
      );
   }

   /**
    * Draws a single-pass analytical Gaussian SDF drop shadow / glow behind windows, cards, or popups
    * (adapted from CoolClient {@code shape.fsh} {@code erfApprox}).
    */
   public static void drawSoftShadow(
      float x,
      float y,
      float width,
      float height,
      float cornerRadius,
      float blurRadius,
      float offsetY,
      int shadowArgb
   ) {
      drawSoftShadow(x, y, width, height, cornerRadius, cornerRadius, cornerRadius, cornerRadius, blurRadius, 0.0F, offsetY, shadowArgb);
   }

   public static void drawSoftShadow(
      float x,
      float y,
      float width,
      float height,
      float topLeft,
      float topRight,
      float bottomRight,
      float bottomLeft,
      float blurRadius,
      float offsetX,
      float offsetY,
      int shadowArgb
   ) {
      if (width <= 0.0F || height <= 0.0F || blurRadius <= 0.05F || (shadowArgb >>> 24) == 0 || !ensureShader()) {
         return;
      }
      float pad = blurRadius * 2.2F + 2.0F;
      drawPrimitiveInternal(
         x - pad + offsetX,
         y - pad + offsetY,
         width + pad * 2.0F,
         height + pad * 2.0F,
         topLeft,
         topRight,
         bottomRight,
         bottomLeft,
         shadowArgb,
         0,
         0.0F,
         0,
         0,
         0.0F,
         0.0F,
         0.0F,
         -9999.0F,
         -9999.0F,
         0.0F,
         0.0F,
         0,
         blurRadius,
         width,
         height,
         offsetY * 0.15F,
         0.0F
      );
   }

   /**
    * Whether the ClickGUI glass is asking for a blurred backdrop at all. Cards only sample the capture
    * when some blur is enabled; with every blur toggle off they stay on the plain veil path (which is
    * itself coherent, see {@link UiTheme#glassLevelAlpha(int)}), so "BlurWindows/BlurPanels/
    * BlurOptions off" still means "no frosted backdrop".
    */
   private static boolean glassBackdropWanted() {
      if (ClientManagers.moduleManager == null || ClientManagers.moduleManager.clickGui == null) {
         return false;
      }
      return ClientManagers.moduleManager.clickGui.isWindowBlurActive()
         || ClientManagers.moduleManager.clickGui.isPanelBlurActive()
         || ClientManagers.moduleManager.clickGui.isOptionBlurActive();
   }

   /**
    * Draws a nested glass pane (card / hero card) through the <em>same</em> material pipeline as the
    * enclosing window: the same {@link GlassBackdrop} per-frame capture, the same
    * {@link UiTheme#glassTintMix()} tint mix and the same blur radius.
    *
    * <p>This is the fix for "the glass is not applied the same way to the cards as to the window".
    * Previously a card was a flat primitive veil at ~0.75 alpha painted over an already tinted window,
    * so the two tint layers multiplied and only (1-0.75)*(1-0.875) = 3% of the blurred backdrop
    * survived inside a card (see UiTheme's opacity model comment).</p>
    *
    * <p>Here the pane renders its own glass with the shared tint mix and is composited with
    * {@code globalAlpha = UiTheme.glassLevelAlpha(level)}, a bounded crossfade towards its own
    * material. Since both sides of that crossfade show the backdrop at the same (1 - tintMix) ratio,
    * the amount of visible blur inside a card now equals the window's for every GlassOpacity value,
    * and nesting can no longer accumulate opacity.</p>
    *
    * @return true when the pane was drawn; the caller then only adds interaction feedback on top.
    */
   private static boolean drawNestedGlassPane(float x, float y, float width, float height, float radius, int level) {
      if (width <= 0.0F || height <= 0.0F || radius < 0.0F || !glassBackdropWanted()) {
         return false;
      }
      // The one shared coverage transfer: no floor, no per-surface curve (see
      // UiTheme.glassLevelAlpha for the measured contrast that justifies not clamping it). The overlay's
      // own open/close envelope is folded in here so a nested pane fades with the shell it sits on
      // instead of outliving it (see overlayEnvelope).
      float coverage = UiTheme.glassLevelAlpha(level) * overlayEnvelope;
      if (coverage <= 0.02F) {
         return false;
      }
      GlassBackdrop.beginGlassFrame();
      float blurRadius = GlassBackdrop.materialRadius();
      if (!GlassBackdrop.canSample(blurRadius)) {
         return false;
      }
      BlurComponent.drawLiquidGlass(
         x,
         y,
         width,
         height,
         radius,
         radius,
         radius,
         radius,
         blurRadius,
         0.0F,
         0,
         UiTheme.LIQUID_FRESNEL_ALPHA,
         UiTheme.LIQUID_DISTORT_STRENGTH,
         UiTheme.glassTintMixArgb(level, 0.0F, 0.0F, UiTheme.accentColor()),
         UiTheme.FROST_GRAIN_DEFAULT,
         UiTheme.SHEEN_STRENGTH_DEFAULT * 0.85F,
         UiTheme.SATURATION_BOOST_DEFAULT,
         coverage
      );
      return true;
   }

   /**
    * Public entry point for the shared material pane, used by inner controls (button groups,
    * toggles, inputs, sliders and their value fields, dropdowns, selectors, tabs, secondary buttons,
    * popup rows).
    *
    * <p>It is the <em>same</em> method the window, hero and cards go through, so a control samples the
    * frame's single backdrop capture with the frame's single material radius and the one
    * {@link UiTheme#glassTintMix()} transfer. Nothing here is opacity arithmetic: it is the level's
    * coverage and the level's tint colour, both read from UiTheme.</p>
    *
    * <p>Why a control needed it at all. A control that only draws {@code drawRoundedGradientBox}
    * paints a flat translucent veil over its card. The card has already attenuated the blurred
    * backdrop to (1 - mix) = 27.8%, and the control's own 0.86 veil cut that to 3.9% — so the control
    * read as a flat tinted plate while the card behind it read as glass, which is the "the glass only
    * reaches the background and the main cards" complaint. Painting the material first and then
    * letting the veil carry hover/press feedback keeps the card's material visible and makes the
    * control part of the same glass: 10.7% of the blurred backdrop now reaches the eye inside a
    * control instead of 3.9%.</p>
    *
    * @param level one of the {@code UiTheme.LEVEL_*} ids
    * @return true when the material was drawn, so the caller can emit interaction feedback alone
    */
   public static boolean drawGlassMaterialPane(float x, float y, float width, float height, float radius, int level) {
      return drawNestedGlassPane(x, y, width, height, radius, level);
   }

   /**
    * Interaction feedback layer for a nested control: the tiny hover/press overlay that sits on top of
    * the material. Alpha is deliberately small (0.13 hover / 0.09 active) because it no longer has to
    * carry the surface — the material does — and it exits early at rest so a resting control draws no
    * extra geometry.
    */
   private static int interactionFeedbackArgb(int level, float hoverT, float activeT, int accentArgb) {
      float h = UiAnimation.clamp01(hoverT);
      float a = UiAnimation.clamp01(activeT);
      int rgb = UiTheme.glassLevelRgb(level, h, a, accentArgb);
      int alpha = Math.round((h * 0.13F + a * 0.09F) * 255.0F);
      return UiTheme.withAlpha(rgb, alpha);
   }

   /**
    * Draws a layered frosted glass card (Level 3 elevation): the shared liquid glass material plus an
    * interaction layer with a depth gradient, overhead bevel catchlight, bottom AO rim, crisp ambient
    * rim border and a soft animated hover shadow.
    */
   public static void drawGlassCard(
      float x,
      float y,
      float width,
      float height,
      float radius,
      float hoverT,
      float activeT,
      int accentArgb,
      int borderArgb,
      float borderWidth
   ) {
      drawGlassCardWithHover(x, y, width, height, radius, hoverT, activeT, 0.0F, 0.0F, -9999.0F, -9999.0F, accentArgb, borderArgb, borderWidth);
   }

   public static void drawGlassCardWithHover(
      float x,
      float y,
      float width,
      float height,
      float radius,
      float hoverT,
      float activeT,
      float focusT,
      float pressT,
      float mouseX,
      float mouseY,
      int accentArgb,
      int borderArgb,
      float borderWidth
   ) {
      float h = UiAnimation.clamp01(hoverT);
      float a = UiAnimation.clamp01(activeT);
      float f = UiAnimation.clamp01(focusT);
      float p = UiAnimation.clamp01(pressT);

      // Subtle violet accent halo ONLY on active / hover / focus — zero muddy dark halo around resting cards
      if (a > 0.04F || h > 0.08F || f > 0.04F) {
         int accentHalo = UiTheme.withAlpha(accentArgb, Math.round((a * 0.12F + h * 0.08F + f * 0.20F) * 255.0F));
         drawSoftShadow(x, y, width, height, radius, 8.0F + f * 3.0F, 1.0F, accentHalo);
      }

      // 1) Material: same glass as the window behind it.
      boolean body = drawNestedGlassPane(x, y, width, height, radius, UiTheme.LEVEL_CARD);

      // 2) Interaction: with the material drawn, the fill only carries hover/active/press feedback and
      //    the press detent; without a backdrop to sample it falls back to the full veil coverage, which
      //    is what the pre-fix path drew. The ring and the sheen are drawn at full strength either way,
      //    so the card keeps its tactile read without spending opacity on it.
      int topFill;
      if (body) {
         int interactionRgb = UiTheme.glassLevelRgb(UiTheme.LEVEL_CARD, h, Math.max(a, f * 0.7F), accentArgb);
         topFill = UiTheme.withAlpha(interactionRgb, Math.round((h * 0.13F + a * 0.09F) * 255.0F));
      } else {
         topFill = UiTheme.glassCardFillArgb(h, Math.max(a, f * 0.7F), accentArgb);
      }
      if (p > 0.01F) {
         topFill = UiTheme.lerpArgb(topFill, 0xFF090B12, p * 0.18F);
      }
      int bottomFill = body
         ? topFill
         : UiTheme.lerpArgb(topFill, UiTheme.glassBaseRgb(Math.min(1.0F, UiTheme.glassDarkness() + 0.06F)), 0.16F);
      int lightColor = UiTheme.lerpArgb(accentArgb, 0xFFE6E0FF, 0.40F);
      float lightAlpha = (h * 0.12F + f * 0.07F) * (1.0F - p * 0.3F);
      float lightRad = Math.min(200.0F, Math.max(85.0F, Math.max(width, height) * 0.45F));

      drawRoundedGradientBox(
         x,
         y,
         width,
         height,
         radius,
         radius,
         radius,
         radius,
         topFill,
         bottomFill,
         borderArgb,
         borderWidth,
         UiTheme.glassTopHighlightAlpha(Math.max(h, f)),
         UiTheme.glassInnerGlowAlpha(Math.max(h, f)),
         mouseX,
         mouseY,
         lightAlpha,
         lightRad,
         lightColor
      );
   }

   /**
    * Draws an elevated Level 2 Hero Header card with clean 1px glass reflection border.
    */
   public static void drawHeroCard(
      float x,
      float y,
      float width,
      float height,
      float radius,
      float hoverT,
      float enableT,
      float mouseX,
      float mouseY,
      int accentArgb
   ) {
      float h = UiAnimation.clamp01(hoverT);
      float e = UiAnimation.clamp01(enableT);

      drawSoftShadow(x, y, width, height, radius, 10.0F, 2.5F, envelopeArgb(UiTheme.glassShadowArgb(0.18F)));
      if (e > 0.04F || h > 0.04F) {
         drawSoftShadow(
            x, y, width, height, radius, 10.0F, 1.0F,
            envelopeArgb(UiTheme.withAlpha(accentArgb, Math.round((e * 0.14F + h * 0.06F) * 255.0F)))
         );
      }

      // Same self-contained glass material as the window, crossfaded at the hero level's coverage.
      boolean body = drawNestedGlassPane(x, y, width, height, radius, UiTheme.LEVEL_HERO);

      int topFill = UiTheme.glassHeroCardFillArgb(h, e, accentArgb);
      if (body) {
         // Material already carries the tint; the primitive fill only adds enabled/hover emphasis.
         topFill = UiTheme.withAlpha(
            UiTheme.glassLevelRgb(UiTheme.LEVEL_HERO, h, e, accentArgb),
            Math.round((h * 0.08F + e * 0.06F) * 255.0F)
         );
      }
      int bottomFill = UiTheme.lerpArgb(topFill, UiTheme.glassBaseRgb(Math.min(1.0F, UiTheme.glassDarkness() + 0.08F)), body ? 0.0F : 0.22F);
      int borderArgb = envelopeArgb(UiTheme.lerpArgb(UiTheme.GLASS_BORDER, UiTheme.withAlpha(UiTheme.accentSoftGlow(), 175), Math.max(e * 0.55F, h * 0.45F)));

      drawRoundedGradientBox(
         x,
         y,
         width,
         height,
         radius,
         radius,
         radius,
         radius,
         topFill,
         bottomFill,
         borderArgb,
         UiTheme.BORDER_THICKNESS,
         UiTheme.glassTopHighlightAlpha(0.25F + Math.max(h, e) * 0.55F),
         UiTheme.glassInnerGlowAlpha(Math.max(h, e)),
         mouseX,
         mouseY,
         h * 0.10F,
         Math.min(240.0F, width * 0.48F),
         UiTheme.accentSoftGlow()
      );
   }

   /**
    * Draws a Level 4A recessed inset control slot (slider rails, inputs, segmented wells, scroll tracks).
    *
    * <p>Unchanged public behaviour: this is {@link #drawRecessedWell(float, float, float, float, float,
    * float, float, float, float, int, float, int)} pinned to {@link UiTheme#LEVEL_WELL}. The level
    * variant exists so a well nested inside another well (a slider's numeric field, the inner field of
    * a text input, a dropdown row) can declare a deeper recess through the same coverage function.</p>
    */
   public static void drawRecessedWell(
      float x,
      float y,
      float width,
      float height,
      float radius,
      float hoverT,
      float focusT,
      float mouseX,
      float mouseY,
      int accentArgb,
      float borderWidth
   ) {
      drawRecessedWell(x, y, width, height, radius, hoverT, focusT, mouseX, mouseY, accentArgb, borderWidth, UiTheme.LEVEL_WELL);
   }

   /**
    * Draws a recessed inset control through the shared glass material pipeline.
    *
    * <p>The well now paints the level's glass material first (same per-frame capture, same tint mix,
    * same radius as the window and the cards) and then a thin veil scaled to the well's coverage. The
    * caller's {@code borderArgb} is used verbatim, so hover/focus rings and the disabled ring keep
    * working exactly as before.</p>
    *
    * @param level {@link UiTheme#LEVEL_WELL} or {@link UiTheme#LEVEL_WELL_DEEP}
    */
   public static void drawRecessedWell(
      float x,
      float y,
      float width,
      float height,
      float radius,
      float hoverT,
      float focusT,
      float mouseX,
      float mouseY,
      int accentArgb,
      float borderWidth,
      int level
   ) {
      float h = UiAnimation.clamp01(hoverT);
      float f = UiAnimation.clamp01(focusT);

      // 1) Material: the same glass the enclosing window and card are made of.
      boolean body = drawNestedGlassPane(x, y, width, height, radius, level);

      if (f > 0.04F) {
         drawSoftShadow(x, y, width, height, radius, 8.0F, 1.0F, UiTheme.withAlpha(accentArgb, Math.round(58.0F * f)));
      }

      // 2) Tint / interaction: with the material drawn the veil only adds state feedback, exactly the
      //    contract drawGlassCardWithHover already uses. Without a backdrop to sample it keeps the
      //    pre-glass density, which is what UiTheme's glassLevelFillArgb already produced.
      int topFill = body
         ? interactionFeedbackArgb(level, h, f, accentArgb)
         : UiTheme.glassLevelFillArgb(level, h, f, accentArgb);
      int bottomFill = UiTheme.lerpArgb(topFill, UiTheme.glassRecessedDepthRgb(), 0.22F);
      // The reference's wells have NO rim at rest: its edge is a 5-9 px feather whose peak deviation
      // from the surrounding surface is only 4-6 luminance. A full-strength 1 px border reads as an
      // outline the reference does not have, so the idle ring is dropped to 42 % and the hover/focus
      // rings -- which the reference does draw when you point at a control -- are untouched.
      int borderArgb = f > 0.02F
         ? UiTheme.lerpArgb(UiTheme.glassBorderFocusedOrHover(h * 0.65F, accentArgb), UiTheme.withAlpha(UiTheme.accentSoftGlow(), 242), f)
         : UiTheme.scaleAlpha(UiTheme.glassBorderFocusedOrHover(h * 0.65F, accentArgb), 0.42F);

      drawRoundedGradientBox(
         x,
         y,
         width,
         height,
         radius,
         radius,
         radius,
         radius,
         topFill,
         bottomFill,
         borderArgb,
         borderWidth,
         0.04F + f * 0.10F,
         0.0F,
         mouseX,
         mouseY,
         h * 0.09F + f * 0.06F,
         Math.max(70.0F, width * 0.55F),
         UiTheme.accentSoftGlow()
      );
   }

   /**
    * Draws a crisp tactile button / pill surface with localized hover spotlight, instant press detent,
    * and reduced visual emphasis when disabled.
    *
    * <p>Unchanged public behaviour: pinned to {@link UiTheme#LEVEL_CONTROL_RAISED}, the level used by
    * inner controls (buttons, switch tracks, checkboxes, tabs, segmented pills).</p>
    */
   public static void drawInteractiveSurface(
      float x,
      float y,
      float width,
      float height,
      float radius,
      int topFill,
      int bottomFill,
      int borderArgb,
      float borderWidth,
      float topHighlightAlpha,
      float hoverT,
      float pressT,
      boolean enabled,
      float mouseX,
      float mouseY,
      int accentArgb
   ) {
      drawInteractiveSurface(
         x,
         y,
         width,
         height,
         radius,
         topFill,
         bottomFill,
         borderArgb,
         borderWidth,
         topHighlightAlpha,
         hoverT,
         pressT,
         enabled,
         mouseX,
         mouseY,
         accentArgb,
         UiTheme.LEVEL_CONTROL_RAISED
      );
   }

   /**
    * Draws a tactile control surface through the shared glass material pipeline.
    *
    * <p>The control keeps its caller-supplied fills (brand, danger, disabled and press colours are a
    * product decision, not a material decision) but the level's glass material is painted underneath
    * first. The fills are the state layer; the material is the surface. That is the same division of
    * labour {@link #drawGlassCardWithHover} uses, and it is why a button now shows the card's backdrop
    * through it instead of being an opaque tinted plate on top of the glass.</p>
    *
    * <p>When no backdrop is available (every blur toggle off, or BlurIntensity 0) the material call
    * returns false and this reduces to the previous veil draw, so the non-blur path is unchanged.</p>
    *
    * @param level elevation the control belongs to; {@link UiTheme#LEVEL_CONTROL_RAISED} for an inner
    *              control, {@link UiTheme#LEVEL_CONTROL} for a control painted directly on the window
    */
   public static void drawInteractiveSurface(
      float x,
      float y,
      float width,
      float height,
      float radius,
      int topFill,
      int bottomFill,
      int borderArgb,
      float borderWidth,
      float topHighlightAlpha,
      float hoverT,
      float pressT,
      boolean enabled,
      float mouseX,
      float mouseY,
      int accentArgb,
      int level
   ) {
      float h = enabled ? UiAnimation.clamp01(hoverT) : 0.0F;
      float p = enabled ? UiAnimation.clamp01(pressT) : 0.0F;

      // Material under the state layer. Skipped for disabled controls so a locked row stays a clean
      // flat plate (it is not interactive, so it should not read as a live glass surface) and for
      // hairline shapes where a fractal corner radius would exceed the shape itself.
      boolean body = enabled && canPaneFit(width, height, radius)
         ? drawNestedGlassPane(x, y, width, height, radius, level)
         : false;

      int effTop = enabled ? UiTheme.lerpArgb(topFill, UiTheme.glassPressRgb(), p * 0.22F) : UiTheme.glassDisabledFillArgb();
      int effBot = enabled ? UiTheme.lerpArgb(bottomFill, UiTheme.glassPressRgb(), p * 0.22F) : UiTheme.glassDisabledFillArgb();
      int effBorder = enabled ? borderArgb : UiTheme.scaleAlpha(UiTheme.GLASS_BORDER, 0.55F);
      float lightAlpha = enabled ? h * 0.16F * (1.0F - p * 0.35F) : 0.0F;

      // With the material drawn the caller's fill is attenuated to its state contribution only: a
      // resting control (h = 0) must not paint over the material it was just given, otherwise it
      // would re-bury the backdrop exactly like the old flat veil did.
      if (body) {
         float state = Math.max(h, p);
         float keep = 0.18F + 0.82F * state;
         effTop = UiTheme.scaleAlpha(effTop, keep);
         effBot = UiTheme.scaleAlpha(effBot, keep);
      }

      drawRoundedGradientBox(
         x,
         y,
         width,
         height,
         radius,
         radius,
         radius,
         radius,
         effTop,
         effBot,
         effBorder,
         borderWidth,
         enabled ? topHighlightAlpha * (1.0F - p * 0.45F) : 0.03F,
         enabled ? 0.06F : 0.0F,
         mouseX,
         mouseY,
         lightAlpha,
         Math.max(64.0F, Math.max(width, height) * 0.65F),
         UiTheme.lerpArgb(accentArgb, 0xFFE6E0FF, 0.45F)
      );
   }

   /**
    * Whether a glass pane can be painted into this shape: the material shader clamps the corner radii
    * to the shape, so a 4 px slider rail with a 2 px radius still rasterises correctly, but a shape
    * thinner than ~2 px is not worth a blur sample and is skipped.
    */
   private static boolean canPaneFit(float width, float height, float radius) {
      return width >= 2.0F && height >= 2.0F && radius >= 0.0F;
   }

   private static void drawPrimitiveInternal(
      float x,
      float y,
      float width,
      float height,
      float topLeft,
      float topRight,
      float bottomRight,
      float bottomLeft,
      int fillArgb,
      int bottomFillArgb,
      float gradientMix,
      int borderArgb,
      int bottomBorderArgb,
      float borderWidth,
      float topHighlightAlpha,
      float innerBottomAlpha,
      float hoverLocalX,
      float hoverLocalY,
      float hoverLightAlpha,
      float hoverLightRadius,
      int hoverLightArgb,
      float shadowBlur,
      float shadowW,
      float shadowH,
      float shadowOffsetY,
      float gradientAxis
   ) {
      if (width <= 0.0F || height <= 0.0F) {
         return;
      }

      if (!ensureShader()) {
         if (shadowBlur <= 0.01F && (fillArgb >>> 24) > 0) {
            RenderUtil.drawRoundedRect(x, y, x + width, y + height, topLeft, 1, topLeft > 0.0F, topRight > 0.0F, bottomLeft > 0.0F, bottomRight > 0.0F, fillArgb);
         }
         if (shadowBlur <= 0.01F && (borderArgb >>> 24) > 0 && borderWidth > 0.0F) {
            RenderUtil.drawRoundedRectOutline(x, y, x + width, y + height, topLeft, 1, Math.max(1, Math.round(borderWidth)), borderArgb);
         }
         return;
      }

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
      STATE_SAVER.setTexture2D(false);
      GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

      primitiveShader.bind();
      primitiveShader.setUniform2f("size", width, height);
      primitiveShader.setUniform4f("cornerRadius", topLeft, topRight, bottomRight, bottomLeft);
      setColorUniform("fillColor", fillArgb);
      setColorUniform("fillColorBottom", bottomFillArgb);
      primitiveShader.setUniform1f("gradientMix", gradientMix);
      primitiveShader.setUniform1f("gradientAxis", gradientAxis);
      // Sheen and cursor-light weights are opacity, not geometry, and no caller fades them (they are
      // factors, not colours), so the overlay's envelope is applied here - otherwise a card's bevel and
      // hover light survive the fade that just dimmed the card they belong to.
      primitiveShader.setUniform1f("topHighlightAlpha", topHighlightAlpha * overlayEnvelope);
      primitiveShader.setUniform1f("innerBottomAlpha", innerBottomAlpha * overlayEnvelope);
      setColorUniform("borderColor", borderArgb);
      setColorUniform("borderColorBottom", bottomBorderArgb);
      primitiveShader.setUniform1f("borderWidth", borderWidth);
      primitiveShader.setUniform2f("hoverPos", hoverLocalX, hoverLocalY);
      primitiveShader.setUniform1f("hoverLightAlpha", hoverLightAlpha * overlayEnvelope);
      primitiveShader.setUniform1f("hoverLightRadius", hoverLightRadius);
      setColorUniform("hoverLightColor", hoverLightArgb);
      primitiveShader.setUniform1f("shadowBlur", shadowBlur);
      primitiveShader.setUniform2f("shadowSize", shadowW, shadowH);
      primitiveShader.setUniform2f("shadowOffset", 0.0F, shadowOffsetY);
      primitiveShader.setUniform1f("smoothness", DEFAULT_SMOOTHNESS);

      Tessellator tessellator = Tessellator.getInstance();
      WorldRenderer wr = tessellator.getWorldRenderer();
      wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
      wr.pos((double)x, (double)(y + height), 0.0).tex(0.0, 1.0).endVertex();
      wr.pos((double)(x + width), (double)(y + height), 0.0).tex(1.0, 1.0).endVertex();
      wr.pos((double)(x + width), (double)y, 0.0).tex(1.0, 0.0).endVertex();
      wr.pos((double)x, (double)y, 0.0).tex(0.0, 0.0).endVertex();
      tessellator.draw();

      primitiveShader.unbind();

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

   public static void drawCircle(float cx, float cy, float radius, int fillArgb) {
      drawCircle(cx, cy, radius, fillArgb, 0, 0.0F);
   }

   public static void drawCircle(float cx, float cy, float radius, int fillArgb, int borderArgb, float borderWidth) {
      float d = radius * 2.0F;
      drawRoundedBox(cx - radius, cy - radius, d, d, radius, fillArgb, borderArgb, borderWidth);
   }

   /**
    * Draws a frosted Liquid Glass surface with live darkness/opacity tinting, Fresnel rim sheen,
    * top specular catchlight, crisp AA border, and optional open/close alpha.
    */
   public static void drawLiquidGlassSurface(
      float x,
      float y,
      float width,
      float height,
      float topLeft,
      float topRight,
      float bottomRight,
      float bottomLeft,
      float blurRadius,
      float borderWidth,
      int borderArgb
   ) {
      drawLiquidGlassSurface(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, blurRadius, borderWidth, borderArgb, 1.0F);
   }

   public static void drawLiquidGlassSurface(
      float x,
      float y,
      float width,
      float height,
      float topLeft,
      float topRight,
      float bottomRight,
      float bottomLeft,
      float blurRadius,
      float borderWidth,
      int borderArgb,
      float globalAlpha
   ) {
      if (width <= 0.0F || height <= 0.0F || globalAlpha <= 0.005F) {
         return;
      }

      float a = UiAnimation.clamp01(globalAlpha);
      // Owns the one-capture-per-frame invariant for the enclosing surface: everything nested in this
      // window (cards, wells, controls) samples the capture taken here.
      GlassBackdrop.beginGlassFrame();
      drawSoftShadow(
         x,
         y,
         width,
         height,
         topLeft,
         topRight,
         bottomRight,
         bottomLeft,
         18.0F,
         0.0F,
         6.0F,
         UiTheme.glassShadowArgb(0.38F * a)
      );

      // Blur strength is a material property (BlurIntensity), never an animation property. Callers that
      // faded the surface in by scaling the radius with the open alpha are normalised back to the
      // material radius, otherwise this window would rebuild the capture mid-frame and every card drawn
      // after it would sample a *different* generation (already tinted) backdrop.
      float captureRadius = GlassBackdrop.captureRadius(blurRadius, a);
      boolean canBlur = GlassBackdrop.canSample(captureRadius);
      int tintArgb = UiTheme.glassWindowTintArgb(canBlur);
      if (canBlur) {
         BlurComponent.drawLiquidGlass(
            x,
            y,
            width,
            height,
            topLeft,
            topRight,
            bottomRight,
            bottomLeft,
            captureRadius,
            borderWidth,
            UiTheme.scaleAlpha(borderArgb, a),
            UiTheme.LIQUID_FRESNEL_ALPHA,
            UiTheme.LIQUID_DISTORT_STRENGTH,
            tintArgb,
            UiTheme.FROST_GRAIN_DEFAULT,
            UiTheme.SHEEN_STRENGTH_DEFAULT,
            UiTheme.SATURATION_BOOST_DEFAULT,
            a
         );
      } else {
         int scaledTop = UiTheme.scaleAlpha(tintArgb, a);
         int scaledBottom = UiTheme.lerpArgb(scaledTop, UiTheme.glassBaseRgb(Math.min(1.0F, UiTheme.glassDarkness() + 0.14F)), 0.35F);
         drawRoundedGradientBox(
            x,
            y,
            width,
            height,
            topLeft,
            topRight,
            bottomRight,
            bottomLeft,
            scaledTop,
            UiTheme.scaleAlpha(scaledBottom, a),
            UiTheme.scaleAlpha(borderArgb, a),
            borderWidth,
            UiTheme.glassTopHighlightAlpha(0.15F) * a
         );
      }
   }

   /**
    * Draws a frosted glass option/card surface. Kept as a public entry point for the settings panel;
    * it now shares the one card path, which samples the same per-frame backdrop capture as the window.
    */
   public static void drawOptionGlass(float x, float y, float width, float height, float cornerRadius, float borderWidth, int borderArgb) {
      // Previously this was a *second* card implementation that only blurred when no window/panel blur
      // was active, i.e. a card was a flat tint whenever a window blur existed. One path now: a card is
      // always a nested pane of the same material as its window (see drawNestedGlassPane).
      drawGlassCard(x, y, width, height, cornerRadius, 0.0F, 0.0F, envelopeArgb(UiTheme.accentColor()), borderArgb, borderWidth);
   }

   /**
    * Draws a frosted Liquid Glass floating popup (dropdown, context menu, tooltip, dialog).
    */
   public static void drawFloatingGlassPopup(float x, float y, float width, float height, float cornerRadius, float borderWidth, int borderArgb) {
      drawFloatingGlassPopup(x, y, width, height, cornerRadius, borderWidth, borderArgb, 1.0F);
   }

   public static void drawFloatingGlassPopup(
      float x,
      float y,
      float width,
      float height,
      float cornerRadius,
      float borderWidth,
      int borderArgb,
      float globalAlpha
   ) {
      if (width <= 0.0F || height <= 0.0F || globalAlpha <= 0.005F) {
         return;
      }
      float a = UiAnimation.clamp01(globalAlpha);
      drawSoftShadow(x, y, width, height, cornerRadius, 14.0F, 5.0F, UiTheme.glassShadowArgb(0.45F * a));

      boolean menuBlur = ClientManagers.moduleManager != null
         && ClientManagers.moduleManager.clickGui != null
         && ClientManagers.moduleManager.clickGui.isMenuBlurActive();

      if (menuBlur) {
         // Same capture generation as the window: this used to go through the popup variant of the blur
         // draw, which invalidates the cache and re-captures the framebuffer *after* the window was
         // painted. A popup therefore sampled an already-tinted, already-blurred copy of the window
         // (and each further popup sampled a capture containing the previous popups), which is why
         // popup glass never matched the window glass. It also rebuilt the whole pyramid once per popup
         // per frame. Sampling the frame's single capture fixes both.
         GlassBackdrop.beginGlassFrame();
         float popupRadius = GlassBackdrop.materialRadius();
         if (GlassBackdrop.canSample(popupRadius)) {
            BlurComponent.drawLiquidGlass(
               x,
               y,
               width,
               height,
               cornerRadius,
               cornerRadius,
               cornerRadius,
               cornerRadius,
               popupRadius,
               borderWidth,
               UiTheme.scaleAlpha(borderArgb, a),
               UiTheme.LIQUID_FRESNEL_ALPHA + 0.06F,
               UiTheme.LIQUID_DISTORT_STRENGTH,
               UiTheme.glassPopupTintArgb(true),
               UiTheme.FROST_GRAIN_DEFAULT,
               UiTheme.SHEEN_STRENGTH_DEFAULT,
               UiTheme.SATURATION_BOOST_DEFAULT,
               a
            );
            return;
         }
      }

      int tint = UiTheme.scaleAlpha(UiTheme.glassPopupTintArgb(false), a);
      int bottom = UiTheme.lerpArgb(tint, UiTheme.glassBaseRgb(Math.min(1.0F, UiTheme.glassDarkness() + 0.16F)), 0.35F);
      drawRoundedGradientBox(
         x,
         y,
         width,
         height,
         cornerRadius,
         tint,
         UiTheme.scaleAlpha(bottom, a),
         UiTheme.scaleAlpha(borderArgb, a),
         borderWidth,
         UiTheme.glassTopHighlightAlpha(0.25F) * a
      );
   }

   public static void drawSeparator(float x1, float y1, float x2, float y2, float thickness, int argb) {
      float w = Math.abs(x2 - x1);
      float h = Math.abs(y2 - y1);
      if (w >= h) {
         drawRoundedRect(Math.min(x1, x2), y1 - thickness * 0.5F, Math.max(thickness, w), thickness, thickness * 0.5F, argb);
      } else {
         drawRoundedRect(x1 - thickness * 0.5F, Math.min(y1, y2), thickness, Math.max(thickness, h), thickness * 0.5F, argb);
      }
   }

   /** Crisp anti-aliased SDF vector minimize bar (-). */
   public static void drawVectorMinimize(float cx, float cy, float size, int argb) {
      float w = Math.max(7.0F, size);
      float stroke = 1.4F;
      drawRoundedRect(cx - w * 0.5F, cy - stroke * 0.5F, w, stroke, stroke * 0.5F, argb);
   }

   /** Crisp anti-aliased SDF vector maximize square (□). */
   public static void drawVectorMaximize(float cx, float cy, float size, int argb) {
      float s = Math.max(7.5F, size);
      drawRoundedOutline(cx - s * 0.5F, cy - s * 0.5F, s, s, 1.5F, 1.25F, argb);
   }

   /** Crisp anti-aliased SDF vector close icon (×) rendered with rotated capsule strokes. */
   public static void drawVectorClose(float cx, float cy, float size, int argb) {
      float len = Math.max(8.5F, size * 1.15F);
      float stroke = 1.4F;
      GL11.glPushMatrix();
      GlStateManager.translate(cx, cy, 0.0F);
      GlStateManager.rotate(45.0F, 0.0F, 0.0F, 1.0F);
      drawRoundedRect(-len * 0.5F, -stroke * 0.5F, len, stroke, stroke * 0.5F, argb);
      GlStateManager.rotate(-90.0F, 0.0F, 0.0F, 1.0F);
      drawRoundedRect(-len * 0.5F, -stroke * 0.5F, len, stroke, stroke * 0.5F, argb);
      GL11.glPopMatrix();
   }

   /** Crisp anti-aliased SDF vector left/right navigation arrows (<- / ->). */
   public static void drawVectorArrow(float cx, float cy, float size, boolean pointingRight, int argb) {
      // Proportions fitted to the reference's own arrows: the ink is 12.95 x 10.50 (height/width 0.81)
      // with a 2.2 shaft, i.e. a head whose arms sit at 45 degrees and reach 0.455*size back from the
      // tip. The previous arms were 0.34*size, which left our arrows 40 % too short.
      float halfW = size * 0.5F;
      float armLen = size * 0.455F;
      float stroke = size * 0.17F;
      float tipX = pointingRight ? cx + halfW : cx - halfW;
      float stemX = cx - halfW + (pointingRight ? 0.0F : stroke * 0.45F);
      float stemW = halfW * 2.0F - stroke * 0.45F;
      drawRoundedRect(stemX, cy - stroke * 0.5F, stemW, stroke, stroke * 0.5F, argb);

      GL11.glPushMatrix();
      GlStateManager.translate(tipX, cy, 0.0F);
      GlStateManager.rotate(pointingRight ? 135.0F : 45.0F, 0.0F, 0.0F, 1.0F);
      drawRoundedRect(-stroke * 0.5F, -stroke * 0.5F, armLen + stroke * 0.5F, stroke, stroke * 0.5F, argb);
      GL11.glPopMatrix();

      GL11.glPushMatrix();
      GlStateManager.translate(tipX, cy, 0.0F);
      GlStateManager.rotate(pointingRight ? -135.0F : -45.0F, 0.0F, 0.0F, 1.0F);
      drawRoundedRect(-stroke * 0.5F, -stroke * 0.5F, armLen + stroke * 0.5F, stroke, stroke * 0.5F, argb);
      GL11.glPopMatrix();
   }

   private static void setColorUniform(String uniform, int argb) {
      float a = (float)(argb >>> 24 & 255) / 255.0F;
      float r = (float)(argb >> 16 & 255) / 255.0F;
      float g = (float)(argb >> 8 & 255) / 255.0F;
      float b = (float)(argb & 255) / 255.0F;
      primitiveShader.setUniform4f(uniform, r, g, b, a);
   }
}
