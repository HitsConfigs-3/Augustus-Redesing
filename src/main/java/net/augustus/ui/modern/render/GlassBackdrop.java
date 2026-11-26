package net.augustus.ui.modern.render;

import net.augustus.utils.render.KawaseBlurEngine;

/**
 * Single owner of the glass backdrop capture: "one capture per frame, sampled by every surface at
 * one scale".
 *
 * <p>Why this class exists. The blur pipeline ({@code KawaseBlurEngine}, a dual-filter Kawase
 * downsample/upsample pyramid ported from Solana Render) caches its result and identifies that cache
 * by <em>(source texture, source size, blur radius)</em>. Two consequences matter for the UI:</p>
 *
 * <ul>
 *   <li>Every surface that asks for the cache with the <em>same</em> radius reuses the capture taken
 *       before anything was painted, so window, cards and popups all see the same backdrop.</li>
 *   <li>Any surface that asks with a <em>different</em> radius forces a fresh capture in the middle of
 *       the frame -- from a framebuffer that already contains the surfaces drawn before it. That
 *       surface then samples an already-tinted, already-blurred image (the "different capture
 *       generation" desync): its glass no longer matches the window it sits on.</li>
 * </ul>
 *
 * <p>The old code hit the second case twice per frame: {@code ClickGuiWindow} scaled its blur radius
 * by the open animation alpha (so the window asked for {@code radius * openProgress} while every card
 * asked for {@code radius}), and popups used the popup variant of the blur draw, which actively
 * invalidates and re-captures after the window has been painted.</p>
 *
 * <p>So this class does two things and nothing else:</p>
 * <ol>
 *   <li>{@link #beginGlassFrame()} refreshes the capture once per frame id, so no glass surface can
 *       ever sample a backdrop captured while glass was already on screen.</li>
 *   <li>{@link #captureRadius(float, float)} keeps blur strength a property of the <em>material</em>
 *       ({@code BlurIntensity}) instead of a property of an animation alpha, so all surfaces in a
 *       frame agree on one radius and therefore share one capture.</li>
 * </ol>
 *
 * <p>Technique source: {@code refs/Blur} blurs the framebuffer once per frame through a single radius
 * uniform and lets every GUI draw sample that one texture; {@code refs/swing-glassmorphism-icon}
 * ({@code GlassIcon.java}) keeps the blur radius in the material config and masks one blurred copy per
 * shape. Both are adopted here as the "one capture, many samplers, radius belongs to the material"
 * invariant.</p>
 */
public final class GlassBackdrop {
   /** Radii at or below this are treated as "blur disabled" by the Kawase engine. */
   public static final float MIN_RADIUS = 0.05F;

   private static int lastFrameId = -1;

   private GlassBackdrop() {
   }

   /**
    * Called before any glass surface is drawn. Advances the capture generation once per frame.
    */
   public static void beginGlassFrame() {
      int id = UiAnimation.frameId();
      if (id == lastFrameId) {
         return;
      }
      lastFrameId = id;
      KawaseBlurEngine.invalidateFrame();
   }

   /**
    * Resolves the radius a glass surface should sample with.
    *
    * <p>Callers that fade a surface in by scaling its blur radius (the window open animation scaled
    * both the alpha and the radius by the same {@code openProgress}) are normalised back to the
    * material radius, because a radius that changes with an animation makes window and cards request
    * different capture generations and rebuild the pyramid mid-frame.</p>
    *
    * @param requestedRadius radius the caller asked for
    * @param callerAlpha     global alpha the caller is drawing with
    * @return the radius to sample with; 0 when blur is disabled
    */
   public static float captureRadius(float requestedRadius, float callerAlpha) {
      float base = UiTheme.blurRadius();
      if (!(base > MIN_RADIUS)) {
         // BlurIntensity == 0 (or unavailable) disables blur for every surface, uniformly.
         return 0.0F;
      }
      float alpha = UiAnimation.clamp01(callerAlpha);
      if (alpha < 0.995F) {
         float animated = base * alpha;
         // Only snap when the request really is "material radius scaled by the animation alpha";
         // an intentionally different radius is passed through untouched.
         if (Math.abs(requestedRadius - animated) <= Math.max(0.35F, base * 0.08F)) {
            return base;
         }
      }
      return Math.max(0.0F, requestedRadius);
   }

   /** Whether a capture can be sampled at this radius right now. */
   public static boolean canSample(float radius) {
      return radius > MIN_RADIUS && KawaseBlurEngine.isAvailable();
   }

   /** The material blur radius every surface must share inside a frame. */
   public static float materialRadius() {
      return captureRadius(UiTheme.blurRadius(), 1.0F);
   }
}
