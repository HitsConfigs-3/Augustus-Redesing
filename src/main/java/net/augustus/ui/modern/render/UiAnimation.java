package net.augustus.ui.modern.render;

import java.util.HashMap;
import java.util.Map;
import org.lwjgl.input.Mouse;

/**
 * Framerate-independent UI animation helpers & lightweight state tracking
 * adapted from CoolClient, Blur, and Solana Render.
 */
public final class UiAnimation {
   private static final Map<String, Float> NAMED_STATES = new HashMap<>();
   private static long lastFrameNanos = System.nanoTime();
   private static long lastFrameIdNanos = System.nanoTime();
   private static float frameDeltaSeconds = 0.016F;
   private static boolean primaryMouseDown = false;
   private static int frameId = 0;

   private UiAnimation() {
   }

   public static void beginFrame() {
      long now = System.nanoTime();
      float dt = (float)((double)(now - lastFrameNanos) / 1.0E9);
      lastFrameNanos = now;
      if (dt < 0.0F || dt > 0.1F) {
         dt = 0.016F;
      }
      frameDeltaSeconds = dt;

      // Frame identity for the glass backdrop capture (see GlassBackdrop). It has to advance exactly
      // once per rendered frame: a second beginFrame() inside the same frame (two subsystems drawing
      // in one frame) must NOT create a new id, because a new id makes GlassBackdrop refresh the
      // blur capture and the framebuffer already contains the glass drawn earlier in that frame --
      // re-capturing it would bake that glass into the backdrop and tint it twice.
      if ((now - lastFrameIdNanos) / 1000000L >= 1L) {
         frameId++;
         lastFrameIdNanos = now;
      }

      try {
         primaryMouseDown = Mouse.isCreated() && Mouse.isButtonDown(0);
      } catch (Throwable ignored) {
         primaryMouseDown = false;
      }
   }

   /** Monotonic id of the frame currently being drawn; the glass backdrop is captured once per id. */
   public static int frameId() {
      return frameId;
   }

   public static boolean isPrimaryMouseDown() {
      return primaryMouseDown;
   }

   public static float smoothState(String key, float target, float speed) {
      if (key == null) {
         return target;
      }
      if (NAMED_STATES.size() > 4096) {
         NAMED_STATES.clear();
      }
      Float cur = NAMED_STATES.get(key);
      float next = cur == null ? target : smooth(cur.floatValue(), target, speed);
      NAMED_STATES.put(key, next);
      return next;
   }

   public static float smoothState(String key, float initialValue, float target, float speed) {
      if (key == null) {
         return target;
      }
      if (NAMED_STATES.size() > 4096) {
         NAMED_STATES.clear();
      }
      Float cur = NAMED_STATES.get(key);
      float next = cur == null ? initialValue : smooth(cur.floatValue(), target, speed);
      NAMED_STATES.put(key, next);
      return next;
   }

   public static void setState(String key, float value) {
      if (key != null) {
         if (NAMED_STATES.size() > 4096) {
            NAMED_STATES.clear();
         }
         NAMED_STATES.put(key, value);
      }
   }

   public static float getState(String key, float fallback) {
      Float v = key != null ? NAMED_STATES.get(key) : null;
      return v != null ? v.floatValue() : fallback;
   }

   public static float dt() {
      return frameDeltaSeconds;
   }

   public static float smooth(float current, float target, float speed) {
      float factor = 1.0F - (float)Math.exp((double)(-speed * frameDeltaSeconds));
      float next = current + (target - current) * factor;
      if (Math.abs(target - next) < 0.001F) {
         return target;
      }
      return next;
   }

   public static float moveTowards(float current, float target, float ratePerSecond) {
      float step = ratePerSecond * frameDeltaSeconds;
      if (Math.abs(target - current) <= step) {
         return target;
      }
      return current < target ? current + step : current - step;
   }

   public static float lerp(float a, float b, float t) {
      return a + (b - a) * clamp01(t);
   }

   public static float clamp01(float v) {
      return Math.max(0.0F, Math.min(1.0F, v));
   }

   public static float easeOutCubic(float t) {
      float u = 1.0F - clamp01(t);
      return 1.0F - u * u * u;
   }

   public static float easeInOutCubic(float t) {
      float x = clamp01(t);
      if (x < 0.5F) {
         return 4.0F * x * x * x;
      }
      float u = -2.0F * x + 2.0F;
      return 1.0F - u * u * u * 0.5F;
   }

   public static float easeOutQuint(float t) {
      float u = 1.0F - clamp01(t);
      float u2 = u * u;
      return 1.0F - u2 * u2 * u;
   }

   public static float easeOutExpo(float t) {
      float x = clamp01(t);
      return x >= 1.0F ? 1.0F : 1.0F - (float)Math.pow(2.0, (double)(-10.0F * x));
   }

   public static float easeOutBack(float t) {
      float x = clamp01(t);
      float c1 = 1.45F;
      float c3 = c1 + 1.0F;
      float u = x - 1.0F;
      return 1.0F + c3 * u * u * u + c1 * u * u;
   }

   /**
    * Simple elapsed-time eased progress tracker adapted from CoolClient Animation & Blur's Progress.
    */
   public static final class SmoothProgress {
      private float progress;
      private boolean forward;

      public SmoothProgress(float initial) {
         this.progress = clamp01(initial);
         this.forward = initial >= 0.5F;
      }

      public void reset(float value, boolean forward) {
         this.progress = clamp01(value);
         this.forward = forward;
      }

      public void setForward(boolean forward) {
         this.forward = forward;
      }

      public boolean isForward() {
         return this.forward;
      }

      public float advance(float durationSeconds) {
         float rate = durationSeconds > 0.001F ? 1.0F / durationSeconds : 100.0F;
         this.progress = moveTowards(this.progress, this.forward ? 1.0F : 0.0F, rate);
         return this.progress;
      }

      public float raw() {
         return this.progress;
      }

      public float eased() {
         return easeOutCubic(this.progress);
      }

      public float easedPop() {
         return this.forward ? easeOutBack(this.progress) : easeOutCubic(this.progress);
      }
   }
}
