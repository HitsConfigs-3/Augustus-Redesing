package net.augustus.ui.modern.render;

import java.awt.Color;
import net.augustus.ClientManagers;
import net.augustus.modules.render.ClickGui;
import net.augustus.utils.render.ColorUtil;

/**
 * Shared visual tokens for the modernized Frosted Liquid Glass OpenAugustus UI.
 *
 * <p>Provides OneClient-inspired depth, directional top-lit glass edges, and real-time
 * controllable blur intensity, glass darkness (light crystal to obsidian dark), and opacity.</p>
 */
public final class UiTheme {
   public static final float BORDER_THICKNESS = 1.0F;
   public static final float THICK_BORDER_THICKNESS = 1.25F;
   public static final float SEPARATOR_THICKNESS = 1.0F;

   public static final float WINDOW_RADIUS = 12.0F;
   /**
    * Corner radii of everything nested inside a window.
    *
    * <p>The scale was one step tighter at every level (card 8, control 6, pill 6, popup 8) and that is
    * what made a settings page read as a stack of small boxes: at the sizes this UI uses - a 24px toggle,
    * a 26px field, a 34px segment tray - a 6px corner is a quarter of the shape's height, i.e. a rounded
    * rectangle. The scale below keeps the same three-step hierarchy but lands every control at or past a
    * third of its height, so a field, a button, a segmented tray and a popup all read as soft, continuous
    * shapes instead of as rectangles with the corners knocked off. Cards move with them so a card never
    * ends up with a tighter corner than the controls it contains.
    */
   public static final float CARD_RADIUS = 11.0F;
   public static final float CONTROL_RADIUS = 9.0F;
   public static final float PILL_RADIUS = 9.0F;
   public static final float POPUP_RADIUS = 11.0F;

   // Material read (consumed by liquid_glass.frag). Strengthened: the sheen, edge refraction and
   // backdrop saturation are what still read as "glass" once the tint is thin, and the old values
   // were tuned for the old, near-opaque tints where nothing else was visible anyway.
   public static final float LIQUID_FRESNEL_ALPHA = 0.062F;
   public static final float LIQUID_DISTORT_STRENGTH = 0.0024F;
   public static final float FROST_GRAIN_DEFAULT = 0.009F;
   public static final float SHEEN_STRENGTH_DEFAULT = 0.055F;
   public static final float SATURATION_BOOST_DEFAULT = 1.10F;

   public static final int TEXT_PRIMARY = 0xFFF8FAFC;
   public static final int TEXT_SECONDARY = 0xFFE2E8F0;
   public static final int TEXT_MUTED = 0xFF94A3B8;
   public static final int TEXT_DISABLED = 0xFF64748B;
   public static final int TEXT_SHADOW = 0x24020308;
   public static final int TEXT_SHADOW_SUBTLE = 0x28020308;

   public static final int DEFAULT_ACCENT = 0xFF7C3AED;
   public static final int DEFAULT_VIOLET_BRIGHT = 0xFF8B5CF6;
   public static final int DEFAULT_INDIGO_DEEP = 0xFF4C1D95;
   public static final int DEFAULT_LAVENDER_GLOW = 0xFFA78BFA;

   /** Crisp 1px inner crystal reflection border: rgba(255, 255, 255, 0.08) */
   public static final int GLASS_BORDER = 0x14FFFFFF;
   public static final int GLASS_BORDER_STRONG = 0x22FFFFFF;
   public static final int GLASS_DIVIDER = 0x14FFFFFF;
   public static final int SLIDER_TRACK_BORDER = 0x18FFFFFF;
   public static final int DANGER_ACCENT = 0xFFEF4444;

   private static volatile float activeUiScale = 1.0F;

   private UiTheme() {
   }

   public static float uiScale() {
      return activeUiScale;
   }

   public static void setActiveUiScale(float scale) {
      if (Float.isNaN(scale) || Float.isInfinite(scale)) {
         activeUiScale = 1.0F;
      } else {
         activeUiScale = Math.max(0.55F, Math.min(2.0F, scale));
      }
   }

   public static float s(float basePx) {
      return basePx * activeUiScale;
   }

   public static int si(float basePx) {
      return Math.max(1, Math.round(basePx * activeUiScale));
   }

   public static int siSigned(float basePx) {
      return Math.round(basePx * activeUiScale);
   }

   public static int accentColor() {
      if (ClientManagers.moduleManager != null && ClientManagers.moduleManager.clickGui != null) {
         return ensureOpaqueOrMinAlpha(ClientManagers.moduleManager.clickGui.hoverTextColor.getColorInt((byte)0), 235);
      }
      return DEFAULT_ACCENT;
   }

   public static int accentDeep() {
      return lerpArgb(accentColor(), 0xFF28205E, 0.48F);
   }

   public static int accentSoftGlow() {
      return lerpArgb(accentColor(), 0xFFDAD0FF, 0.36F);
   }

   /**
    * Top of the brand mark's vertical gradient: the accent lifted towards white by 18 %.
    *
    * <p>WHY the mark carries its own two anchors instead of reusing {@link #accentSoftGlow()}: the mark is
    * a large, saturated shape, so it has to read as a solid violet that follows the chosen accent, not as
    * the pale wash the glow token is tuned for. These are the only two colours the logo uses, and both
    * derive from the accent, so the mark is never pinned to a hue the user did not pick.
    */
   public static int accentMarkTop() {
      return lerpArgb(accentColor(), 0xFFFFFFFF, 0.18F);
   }

   /** Bottom of the brand mark's vertical gradient: the accent pulled towards black by 14 %. */
   public static int accentMarkBottom() {
      return lerpArgb(accentColor(), 0xFF000000, 0.14F);
   }

   public static int enabledColor() {
      if (ClientManagers.moduleManager != null && ClientManagers.moduleManager.clickGui != null) {
         int configured = ClientManagers.moduleManager.clickGui.enabledColor.getColorInt((byte)0);
         return ensureOpaqueOrMinAlpha(configured, 235);
      }
      return 0xFF22C55E;
   }

   public static int disabledColor() {
      if (ClientManagers.moduleManager != null && ClientManagers.moduleManager.clickGui != null) {
         int configured = ClientManagers.moduleManager.clickGui.disabledColor.getColorInt((byte)0);
         return ensureOpaqueOrMinAlpha(configured, 235);
      }
      return 0xFFEF4444;
   }

   public static int glassBorderColor() {
      if (ClientManagers.moduleManager != null && ClientManagers.moduleManager.clickGui != null) {
         if (!ClientManagers.moduleManager.clickGui.outline.getValue()) {
            // The Outline toggle documents itself as "renders subtle crystal border highlights around
            // windows, cards, and interactive controls". It used to return a visible 1px colour here
            // anyway, so surfaces that use this directly as their border colour (and not every caller
            // gated it) kept painting a ring with the toggle off. Returning 0 makes the toggle real:
            // rings disappear, while hover/focus accents still come back through
            // glassBorderFocusedOrHover().
            return 0;
         }
         int configured = ClientManagers.moduleManager.clickGui.outlineColor.getColorInt((byte)0);
         float[] hsb = ColorUtil.toHsb(configured);
         if (hsb[1] > 0.30F) {
            return withAlpha(configured, 48);
         }
      }
      return GLASS_BORDER;
   }

   public static int glassBorderFocusedOrHover(float hoverT, int accent) {
      return lerpArgb(glassBorderColor(), withAlpha(lerpArgb(accent, 0xFFE2DCFF, 0.20F), 155), UiAnimation.clamp01(hoverT));
   }

   public static int primaryTextColor() {
      if (ClientManagers.moduleManager != null && ClientManagers.moduleManager.clickGui != null) {
         int configured = ClientManagers.moduleManager.clickGui.textColor.getColorInt((byte)0);
         // Boost low-brightness pale defaults only if user hasn't picked a distinct vivid hue, keeping readability high.
         float[] hsb = ColorUtil.toHsb(configured);
         if (hsb[1] < 0.15F && hsb[2] < 0.92F) {
            return TEXT_PRIMARY;
         }
         return ensureOpaqueOrMinAlpha(configured, 245);
      }
      return TEXT_PRIMARY;
   }

   public static int secondaryTextColor() {
      if (ClientManagers.moduleManager != null && ClientManagers.moduleManager.clickGui != null) {
         int configured = ClientManagers.moduleManager.clickGui.textColor.getColorInt((byte)0);
         float[] hsb = ColorUtil.toHsb(configured);
         if (hsb[1] < 0.15F && hsb[2] < 0.92F) {
            return TEXT_SECONDARY;
         }
         return ensureOpaqueOrMinAlpha(configured, 230);
      }
      return TEXT_SECONDARY;
   }

   public static int disabledTextColor() {
      return TEXT_DISABLED;
   }

   public static int mutedTextColor() {
      return TEXT_MUTED;
   }

   public static boolean isBlurActiveWindow() {
      return ClientManagers.moduleManager != null
         && ClientManagers.moduleManager.clickGui != null
         && ClientManagers.moduleManager.clickGui.isWindowBlurActive();
   }

   public static boolean isBlurActivePanel() {
      return ClientManagers.moduleManager != null
         && ClientManagers.moduleManager.clickGui != null
         && ClientManagers.moduleManager.clickGui.isPanelBlurActive();
   }

   public static boolean isBlurActiveOption() {
      return ClientManagers.moduleManager != null
         && ClientManagers.moduleManager.clickGui != null
         && ClientManagers.moduleManager.clickGui.isOptionBlurActive();
   }

   public static boolean isBlurActiveMenu() {
      return ClientManagers.moduleManager != null
         && ClientManagers.moduleManager.clickGui != null
         && ClientManagers.moduleManager.clickGui.isMenuBlurActive();
   }

   public static float blurRadius() {
      if (ClientManagers.moduleManager != null && ClientManagers.moduleManager.clickGui != null) {
         return ClientManagers.moduleManager.clickGui.getBlurRadius();
      }
      return 12.0F;
   }

   public static float glassDarkness() {
      if (ClientManagers.moduleManager != null && ClientManagers.moduleManager.clickGui != null) {
         return ClientManagers.moduleManager.clickGui.getGlassDarkness();
      }
      return 0.78F;
   }

   public static float glassOpacity() {
      if (ClientManagers.moduleManager != null && ClientManagers.moduleManager.clickGui != null) {
         return ClientManagers.moduleManager.clickGui.getGlassOpacity();
      }
      return 0.88F;
   }

   /**
    * Opacity multiplier for the sidebar's tint inside the ClickGUI window.
    *
    * <p>The sidebar is an internal region of one continuous window surface, so this scales the alpha
    * of whatever sidebar tint is drawn rather than switching the sidebar into a separate glass slab.
    * 0 leaves only the window glass; 1 applies the full sidebar tint.
    */
   public static float sidebarOpacity() {
      if (ClientManagers.moduleManager != null && ClientManagers.moduleManager.clickGui != null) {
         return ClientManagers.moduleManager.clickGui.getSidebarOpacity();
      }
      return 1.0F;
   }

   /**
    * Interpolates glass base RGB smoothly from icy light crystal (0.0) through deep indigo obsidian
    * rgb(14, 16, 26) around default darkness (0.78) to ultra-deep ink (1.0), then applies the user's
    * Client Colour as a hue/saturation tint.
    */
   public static int glassBaseRgb(float darkness) {
      float d = UiAnimation.clamp01(darkness);
      int neutral;
      if (d <= 0.42F) {
         neutral = lerpArgb(0xFFE7ECF8, 0xFF161929, d / 0.42F);
      } else if (d <= 0.78F) {
         neutral = lerpArgb(0xFF161929, 0xFF0E101A, (d - 0.42F) / 0.36F);
      } else {
         neutral = lerpArgb(0xFF0E101A, 0xFF07080E, (d - 0.78F) / 0.22F);
      }
      return applyClientGlassTint(neutral);
   }

   /**
    * Colour the glass is tinted with: the Client Color module's colour (RGB only), or -1 when the module is
    * not available yet (startup, tests). A negative sentinel rather than 0 so that a picked pure black
    * ({@code #000000}) is a real tint instead of reading as "no colour".
    *
    * <p>The colour's own alpha is deliberately ignored: strength is the Opacity slider's job
    * ({@link #glassTintMix()}), so the picker's alpha can never silently switch the tint off.</p>
    */
   public static int clientGlassTintRgb() {
      if (ClientManagers.moduleManager != null
         && ClientManagers.moduleManager.customClientColor != null
         && ClientManagers.moduleManager.customClientColor.color2 != null) {
         return ClientManagers.moduleManager.customClientColor.color2.getColorInt((byte)0) & 0x00FFFFFF;
      }
      return -1;
   }

   /**
    * Highest saturation a glass tint may take. The sources are brand colours (Cyan and Emerald sit at 0.9),
    * and at 0.9 the elevated levels read as neon plastic instead of tinted glass; 0.80 keeps them glass.
    */
   private static final float GLASS_TINT_SATURATION_CAP = 0.80F;
   /** Tint the current glass colour was resolved from, with its cached HSB so the hot path stays cheap. */
   private static int cachedGlassTintRgb = -1;
   private static float cachedGlassTintHue = 0.75F;
   private static float cachedGlassTintSaturation = 0.75F;
   private static boolean cachedGlassTintNeutral = false;

   /**
    * Tints a glass colour with the Client Colour: hue and saturation come from the user's colour, brightness
    * stays the one the material had.
    *
    * <p>WHY this is the whole Glass tint feature in one function: every surface of the ClickGUI derives its
    * resting colour from {@link #glassBaseRgb} or {@link #glassLevelRgb} (window shell through
    * {@link #glassWindowTintArgb} and the liquid-glass shader's {@code glassTint} uniform, the sidebar, every
    * card, well, control and popup), so re-tinting here re-tints all of it in the same frame and no caller has
    * to be taught about the colour.</p>
    *
    * <p>WHY only hue and saturation: brightness is the elevation ladder (raised lighter, recessed darker), the
    * Glass Darkness ramp and the contrast budget the whole glass model is tuned around. Keeping it also means a
    * tint can never turn a surface into a flat solid colour: the tint colour's alpha is never read here, so
    * {@link #glassTintMix()} stays the only thing that decides how much blurred backdrop survives.</p>
    *
    * <p>WHY the level colour is re-tinted after its luminance shift instead of only at the base: the elevation
    * shift lerps towards a near-white / near-black reference, which drains most of the hue out of the elevated
    * levels. Re-applying the client hue at each level's own brightness keeps cards, controls and popups reading
    * as the chosen colour while the ladder stays intact.</p>
    */
   private static int applyClientGlassTint(int rgb) {
      int tint = clientGlassTintRgb();
      if (tint < 0) {
         return rgb;
      }
      refreshGlassTintCache(tint);

      int red = rgb >> 16 & 255;
      int green = rgb >> 8 & 255;
      int blue = rgb & 255;
      // HSB brightness is max(r,g,b)/255, so the hot path needs no conversion at all.
      float brightness = Math.max(0.02F, (float)Math.max(red, Math.max(green, blue)) / 255.0F);
      if (!cachedGlassTintNeutral) {
         return 0xFF000000 | (Color.HSBtoRGB(cachedGlassTintHue, cachedGlassTintSaturation, brightness) & 0x00FFFFFF);
      }

      // A near-grey colour (the Neutral preset) keeps a hint of the material's own hue instead of washing the
      // glass out to a flat grey.
      float[] baseHsb = ColorUtil.toHsb(rgb);
      float saturation = Math.min(GLASS_TINT_SATURATION_CAP, baseHsb[1] * 0.35F);
      return 0xFF000000 | (Color.HSBtoRGB(baseHsb[0], saturation, brightness) & 0x00FFFFFF);
   }

   private static void refreshGlassTintCache(int tint) {
      if (tint == cachedGlassTintRgb) {
         return;
      }
      float[] hsb = ColorUtil.toHsb(ColorUtil.withFullAlpha(tint));
      cachedGlassTintRgb = tint;
      cachedGlassTintHue = hsb[0];
      cachedGlassTintSaturation = Math.min(GLASS_TINT_SATURATION_CAP, hsb[1]);
      // Threshold below the Neutral swatch on purpose: #94A3B8 is HSL-slate at saturation 0.196, so a lower
      // cut-off would leave Neutral as a faint blue rather than a near-neutral glass.
      cachedGlassTintNeutral = hsb[1] < 0.22F;
   }

   /**
    * Minimal uniform tint (keeps corners completely clean without irregular color blobs).
    */
   public static int windowAmbientTintArgb() {
      return 0x00000000;
   }

   // ---------------------------------------------------------------------------------------------
   // One opacity model for every glass level.
   //
   // WHY this replaced the old per-method arithmetic: the nine glass tints each applied a *different*
   // transfer function to the same GlassOpacity value -- pow(op,0.72)*0.96 for the window,
   // 0.46+pow(op,0.65)*0.46 for the sidebar, 0.38+op*0.42 for a card, 0.44+op*0.44 for a well,
   // 0.42+op*0.42 for a control, 0.14+op*0.22 for a disabled row, 0.32+op*0.56 for a popup. No single
   // slider position could make any two of them agree, so "one Glass Opacity = one glass" was
   // arithmetically impossible.
   //
   // And because a card/well/control is painted *on top of* the already tinted window, its veil
   // stacked with the window tint. With the old numbers the backdrop visibility inside a card was
   //     (1 - aCard) * (1 - aWindow) = (1 - 0.75) * (1 - 0.875) = 0.031   at the defaults,
   // i.e. only ~3% of the blurred backdrop survived inside a card; that is exactly why cards read as
   // flat dark plates and why dragging GlassOpacity down to the minimum was the only way to make them
   // look like glass -- at the price of making the whole client far too transparent.
   //
   // The model below fixes both at once:
   //   * glassTintMix() is the ONLY opacity transfer. It is the mix factor between the blurred
   //     backdrop and the tint colour (the shader's `glassTint.a`), i.e. the tint strength of the
   //     material, and every level reads it. Levels can no longer be tuned out of agreement.
   //   * glassLevelAlpha(level) is a level's *coverage* over the material beneath it, expressed as a
   //     bounded fraction of that same mix. Nested surfaces crossfade towards their own material
   //     instead of stacking a second full-strength veil, and since a nested pane re-renders the
   //     backdrop with the same mix factor, the visible blur stays (1 - glassTintMix()) on both sides
   //     of the crossfade. Nesting therefore cannot crush the backdrop any more.
   //   * the tint COLOUR carries the hierarchy (elevated solid is lighter, recessed solid is darker),
   //     which is a physical property of the material and independent of the opacity slider.
   // ---------------------------------------------------------------------------------------------

   /** Level ids. A level is a role (window, sidebar, card, well, popup, ...), not a surface. */
   public static final int LEVEL_WINDOW = 0;
   public static final int LEVEL_SIDEBAR = 1;
   public static final int LEVEL_HERO = 2;
   public static final int LEVEL_CARD = 3;
   public static final int LEVEL_WELL = 4;
   public static final int LEVEL_CONTROL = 5;
   public static final int LEVEL_POPUP = 6;
   public static final int LEVEL_DISABLED = 7;
   /**
    * Level 4C: raised inner control surface (button, toggle/switch track, checkbox, tab, segmented
    * pill). A new *level*, not a new curve: it reads {@link #glassTintMix()},
    * {@link #glassLevelAlpha(int)} and {@link #glassLevelRgb(int, float, float, int)} like every
    * other level.
    *
    * <p>Why a level was needed at all. Before this, a control inside a card painted a flat
    * translucent veil of base RGB (alpha ~0.86 at the default GlassOpacity) straight onto the card,
    * so the card's glass material was buried under the control and the two surfaces disagreed about
    * what the material was. Measured, that veil left only (1 - 0.72) * (1 - 0.86) = 3.9% of the
    * blurred backdrop visible inside a button — the same stacking bug the card/window fix removed one
    * level up. Now a control keeps the card material visible and only tints it by its own coverage,
    * and its elevation is expressed by a slightly brighter tint
    * (see {@link #levelLuminanceShift(int)}) plus a slightly lower coverage than a card, which is the
    * physical read of "a raised control on a card".</p>
    */
   public static final int LEVEL_CONTROL_RAISED = 8;
   /**
    * Level 4D: inset numeric/clip field inside a well (slider value chip, input inner field). A
    * deeper recess than {@link #LEVEL_WELL} (darker tint, quieter edge) so nesting a field inside a
    * well is still readable as inset.
    */
   public static final int LEVEL_WELL_DEEP = 9;

   /**
    * Tint mix ceiling and curve. The ceiling is where "how much blurred world is still visible" is
    * decided: 0.78 leaves 22% of the backdrop even at maximum tint (the old window reached 0.875 and
    * left ~12%, and cards left ~3%). The curve keeps the middle of the slider usable.
    */
   private static final float TINT_MIX_CEILING = 0.78F;
   private static final float TINT_MIX_CURVE = 0.60F;
   /** Coverage floor for nested levels, so a level never vanishes at GlassOpacity 0. */
   private static final float LEVEL_COVERAGE_FLOOR = 0.08F;
   private static final int ELEVATED_REFERENCE_RGB = 0xFFEAF0FF;
   private static final int RECESSED_REFERENCE_RGB = 0xFF05060B;

   /**
    * THE opacity transfer of the whole UI: the mix factor between the blurred backdrop and the tint
    * colour, i.e. how strongly the material is tinted. Straight, monotonic and shared by every level.
    *
    * <p>0 -> 0.0 (pure transparent liquid glass: backdrop and material read only), 0.88 -> 0.722,
    * 1.0 -> 0.78. Previously the window used pow(op,0.72)*0.96, which already reached 0.876 at the
    * default and buried the backdrop under the tint, while a card added a second 0.75 veil on top of
    * it; the shared curve plus the coverage model is what keeps the blur visible on every level.</p>
    */
   public static float glassTintMix() {
      float op = UiAnimation.clamp01(glassOpacity());
      return TINT_MIX_CEILING * (float)Math.pow((double)op, (double)TINT_MIX_CURVE);
   }

   /**
    * How strongly a level covers the material beneath it, as a fraction of {@link #glassTintMix()}.
    * This is a *bounded* crossfade weight, not a second independent opacity.
    */
   private static float levelCoverage(int level) {
      switch (level) {
         case LEVEL_SIDEBAR:
            return 0.55F;
         case LEVEL_HERO:
            return 0.95F;
         case LEVEL_CARD:
            return 0.82F;
         case LEVEL_WELL:
            return 1.05F;
         case LEVEL_CONTROL:
            return 0.90F;
         case LEVEL_CONTROL_RAISED:
            // Between the card (0.82) and the flat control veil it replaces: the control stays
            // *inside* the card material instead of covering it, so it must not exceed the card.
            return 0.74F;
         case LEVEL_WELL_DEEP:
            // Deeper than the well (1.05) so a field nested in a well still reads as inset.
            return 1.18F;
         case LEVEL_DISABLED:
            return 0.45F;
         case LEVEL_POPUP:
         case LEVEL_WINDOW:
         default:
            return 1.00F;
      }
   }

   /**
    * Coverage of a level over its parent material (0.08 floor + levelCoverage * tint mix).
    *
    * <p>This is the only coverage transfer, and it is shared by every surface including the inner
    * controls. A legibility floor was prototyped here (a clamp that lifted a control's coverage when
    * the shared value was low) and then removed, because the measurement did not support it:</p>
    *
    * <pre>
    *   Brightest blurred backdrop a control's pane can actually sample, and the resulting contrast
    *   of TEXT_SECONDARY against it, through the real window -&gt; card attenuation:
    *
    *   level            level cov   mixed cov   bg over a white capture   contrast
    *   card                0.82       0.672        (43, 45, 55)            11.11:1
    *   raised control      0.74       0.615        (47, 50, 60)            10.41:1
    *   well                1.05       0.839        (39, 41, 51)            ~11.9:1
    * </pre>
    *
    * <p>Even in the absolute worst case — the blur capture is pure white, which cannot happen because
    * the capture is taken before any glass is painted and this client has no full-screen white — the
    * raised control stays at 10.41:1. The physical reason is that a control's pane never sees the
    * world directly: the window (0.802) and the card (0.672) have already attenuated the backdrop
    * above it, so the contrast budget that was computed for the flat, veil-only control (which did
    * stack its own 0.86 veil on top and therefore fell to ~4.0:1 in a bright blur) is no longer
    * spent. The savings go to transparency instead: 10.7% of the blurred backdrop now reaches the
    * eye inside a control where the old flat veil left 2.8%, a 3.8x improvement, with no legibility
    * cost at any GlassOpacity at or above 0.25. Below that the user has explicitly asked for a nearly
    * transparent window, and at GlassOpacity 0 the whole model (window, cards and controls alike)
    * is a 1.3:1 veil on a white backdrop — that is the GlassOpacity contract, not a control defect.</p>
    */
   public static float glassLevelAlpha(int level) {
      return UiAnimation.clamp01(LEVEL_COVERAGE_FLOOR + levelCoverage(level) * glassTintMix());
   }

   /**
    * Luminance shift of a level's tint colour toward the elevated-crystal / recessed-obsidian
    * reference, as a fraction of the remaining headroom.
    */
   private static float levelLuminanceShift(int level) {
      switch (level) {
         case LEVEL_SIDEBAR:
            return -0.45F;
         case LEVEL_HERO:
            return 0.16F;
         case LEVEL_CARD:
            return 0.12F;
         case LEVEL_WELL:
            return -0.35F;
         case LEVEL_CONTROL:
            return 0.20F;
         case LEVEL_CONTROL_RAISED:
            // Raised, but still nested in the card material, so it sits between card (0.12) and the
            // flat control (0.20): a control must never out-shine the card it is drawn on.
            return 0.15F;
         case LEVEL_WELL_DEEP:
            return -0.52F;
         case LEVEL_POPUP:
            return 0.08F;
         case LEVEL_DISABLED:
            return 0.04F;
         case LEVEL_WINDOW:
         default:
            return 0.0F;
      }
   }

   /**
    * Moves a tint along the dark/light axis by {@code t} of the remaining headroom.
    *
    * <p>A plain multiplier (what the old code used for dark tints) cannot lift near-black indigo into
    * anything visible: 0xFF0E101A * 1.22 is still 0xFF11141F, a 3/255 step. Measuring the step as
    * headroom instead gives the same visible elevation at every GlassDarkness value, in the dark
    * obsidian theme and in the light crystal theme alike.</p>
    */
   public static int shiftLuminance(int rgb, float t) {
      int target = t >= 0.0F ? ELEVATED_REFERENCE_RGB : RECESSED_REFERENCE_RGB;
      return lerpArgb(rgb, target, Math.min(1.0F, Math.abs(t)));
   }

   /** Tint colour (alpha 255) of a level, including hover/active accent feedback. */
   public static int glassLevelRgb(int level, float hoverT, float activeT, int accentArgb) {
      float h = UiAnimation.clamp01(hoverT);
      float a = UiAnimation.clamp01(activeT);
      float shift = levelLuminanceShift(level);
      int rgb = shiftLuminance(glassBaseRgb(glassDarkness()), shift);
      float interactionLift = h * 0.10F + a * 0.06F;
      if (interactionLift > 0.001F) {
         // Hover always brightens the pane, on every level, whatever its resting shift was.
         rgb = shiftLuminance(rgb, Math.max(0.0F, shift) + interactionLift);
      }
      // Re-tint at this level's own brightness, then let the accent feedback on top: the elevation shift
      // above lerps towards a near-white reference, which would otherwise drain the user's tint out of
      // every elevated surface (see applyClientGlassTint).
      rgb = applyClientGlassTint(rgb);
      if (h > 0.001F || a > 0.001F) {
         rgb = lerpArgb(rgb, accentArgb, h * 0.05F + a * 0.10F);
      }
      return 0xFF000000 | (rgb & 0x00FFFFFF);
   }

   /**
    * Shader-ready tint for a level: the level colour carrying the shared tint mix as its alpha. This
    * is what goes into a glass shader's {@code glassTint} uniform, so every level mixes the backdrop
    * and its tint in the same proportion.
    */
   public static int glassTintMixArgb(int level, float hoverT, float activeT, int accentArgb) {
      return withAlpha(
         glassLevelRgb(level, hoverT, activeT, accentArgb),
         Math.round(UiAnimation.clamp01(glassTintMix()) * 255.0F)
      );
   }

   /** Coverage ARGB drawn by the primitive shader for a nested level (veil path). */
   public static int glassLevelFillArgb(int level, float hoverT, float activeT, int accentArgb) {
      float h = UiAnimation.clamp01(hoverT);
      float a = UiAnimation.clamp01(activeT);
      float coverage = UiAnimation.clamp01(glassLevelAlpha(level) + h * 0.13F + a * 0.09F);
      return withAlpha(glassLevelRgb(level, hoverT, activeT, accentArgb), Math.round(coverage * 255.0F));
   }

   /**
    * Level 0: the enclosing window / panel surface.
    *
    * <p>Alpha is the shared tint mix (the shader mixes backdrop -> tint by it), not a coverage: the
    * window is the self-contained pane everything else is nested in.</p>
    */
   public static int glassWindowTintArgb(boolean blurActive) {
      float mix = glassTintMix();
      if (!blurActive) {
         // No sampled backdrop in this path, so the tint has to carry the surface on its own. The
         // boost keeps this branch at roughly the density it had before the opacity model changed.
         mix = Math.min(0.96F, 0.10F + mix * 1.10F);
      }
      return withAlpha(
         glassLevelRgb(LEVEL_WINDOW, 0.0F, 0.0F, accentColor()),
         Math.round(UiAnimation.clamp01(mix) * 255.0F)
      );
   }

   /**
    * Level 1: recessed sidebar rail tint.
    *
    * <p>The sidebar is an internal region of the one continuous window surface, so this is the single
    * definition of its tint and the single place where Sidebar Opacity is applied -- exactly once, as
    * a straight linear alpha scale (no curve). {@code sidebarOpacity() == 0} therefore multiplies the
    * coverage by 0 and returns alpha 0, which leaves the region as pure window glass with no residual
    * tint; {@code == 1} (default) leaves the factor at 1.0, i.e. the sidebar's normal appearance.</p>
    */
   public static int glassSidebarTintArgb() {
      return applyCoverage(sidebarTintArgb(), sidebarOpacity());
   }

   /**
    * The same level-1 tint <em>without</em> the Sidebar Opacity factor.
    *
    * <p>Level 1 is also the fill historically used for a plain window title bar
    * ({@code ClickGuiWindow} when its window blur is off). A title bar is not the sidebar, so it must
    * not dim when the user turns Sidebar Opacity down; it keeps the pre-feature appearance.</p>
    */
   public static int glassTitleBarTintArgb() {
      return sidebarTintArgb();
   }

   /** The unscaled level-1 tint: colour plus the level's base coverage, no sidebar factor. */
   private static int sidebarTintArgb() {
      int coverage = Math.round(UiAnimation.clamp01(glassLevelAlpha(LEVEL_SIDEBAR)) * 255.0F);
      return withAlpha(glassLevelRgb(LEVEL_SIDEBAR, 0.0F, 0.0F, accentColor()), coverage);
   }

   /** Scales an ARGB colour's alpha by a linear 0..1 factor. */
   private static int applyCoverage(int argb, float factor) {
      int alpha = Math.round((float)(argb >>> 24) * UiAnimation.clamp01(factor));
      return withAlpha(argb, Math.max(0, Math.min(255, alpha)));
   }

   /** Level 2: elevated Hero Card (module header / featured panel). */
   public static int glassHeroCardFillArgb(float hoverT, float enableT, int accentArgb) {
      float h = UiAnimation.clamp01(hoverT);
      float e = UiAnimation.clamp01(enableT);
      float coverage = UiAnimation.clamp01(glassLevelAlpha(LEVEL_HERO) + h * 0.08F + e * 0.06F);
      return withAlpha(glassLevelRgb(LEVEL_HERO, hoverT, enableT, accentArgb), Math.round(coverage * 255.0F));
   }

   /** Level 3: elevated setting card & grid tile surface. */
   public static int glassCardFillArgb(float hoverT, float activeT, int accentArgb) {
      return glassLevelFillArgb(LEVEL_CARD, hoverT, activeT, accentArgb);
   }

   /** Level 4A: recessed inset control well (slider rail, text input, segmented well, dropdown trigger). */
   public static int glassRecessedFillArgb(float hoverT, float focusT, int accentArgb) {
      return glassLevelFillArgb(LEVEL_WELL, hoverT, focusT, accentArgb);
   }

   /** Level 4D: field nested inside a well (slider value chip, input inner field). */
   public static int glassDeepInsetFillArgb(float hoverT, float focusT, int accentArgb) {
      return glassLevelFillArgb(LEVEL_WELL_DEEP, hoverT, focusT, accentArgb);
   }

   /** Level 4C: the veil of a raised inner control (button, toggle track, checkbox, tab). */
   public static int glassRaisedControlFillArgb(float hoverT, float pressT, int accentArgb) {
      return glassLevelFillArgb(LEVEL_CONTROL_RAISED, hoverT, pressT, accentArgb);
   }

   /** Level 4B: raised interactive control pill / button. */
   public static int glassControlFillArgb(float hoverT, float focusT, int accentArgb) {
      return glassLevelFillArgb(LEVEL_CONTROL, hoverT, focusT, accentArgb);
   }

   /**
    * The control veil a caller-facing <em>level</em> produces for a given interaction state, so every
    * component family reads one transfer instead of hand-writing fill arithmetic.
    *
    * @param level  one of the {@code LEVEL_*} ids
    * @param hoverT unbounded hover animation value; clamped here
    */
   public static int glassControlVeilArgb(int level, float hoverT, float activeT, int accentArgb) {
      return glassLevelFillArgb(level, clampT(hoverT), clampT(activeT), accentArgb);
   }

   /**
    * Accent-weighted variant of {@link #glassControlVeilArgb(int, float, float, int)}: the level's own
    * veil pulled {@code mix} of the way towards a caller-supplied tint.
    *
    * <p>Brand fills (a primary button's accent, a danger row) stay brand-coloured, but they are
    * expressed as a mix <em>of the level veil</em> rather than as an absolute ARGB written by the
    * component. That keeps the tint transfer in one place while letting a component choose its
    * colour. {@code mix} is clamped to 1, so even a fully branded fill keeps the level's alpha.</p>
    */
   public static int glassControlTintArgb(int level, float hoverT, float activeT, int tintArgb, float mix) {
      return lerpArgb(glassControlVeilArgb(level, hoverT, activeT, tintArgb), tintArgb, clampT(mix));
   }

   /**
    * State-aligned fill for a control: the level veil pulled towards the accent by the interaction
    * amount, i.e. the same value the pre-glass callers used to hand-roll.
    */
   public static int glassControlAccentFillArgb(int level, float hoverT, float activeT, int accentArgb, float accentMix) {
      return glassControlTintArgb(level, hoverT, activeT, accentArgb, accentMix * Math.max(clampT(hoverT), clampT(activeT)));
   }

   /**
    * The one motion token for ordinary control transitions (hover, focus, selection), expressed as an
    * exponential rate for {@code v += (target - v) * (1 - exp(-k * dt))}.
    *
    * <p>k = 13 reaches 95% of the target in ln(20)/13 = 230 ms, which keeps every control inside the
    * project's ~240 ms ordinary-transition budget. The previous per-component rates (14/16/18/20/22)
    * gave 214/187/166/150/136 ms, so controls settled at visibly different moments; one token fixes
    * that without changing the animation law itself (still frame-rate independent, never snapping).</p>
    */
   public static float stateSpeed() {
      return 13.0F;
   }

   /** Press-in rate (fast, ~90 ms) and release rate (~150 ms) for the press detent. */
   public static float pressSpeedIn() {
      return 32.0F;
   }

   public static float pressSpeedOut() {
      return 20.0F;
   }

   /**
    * Frame-rate independent control state helper: {@code smooth(current, target, speed)} with the
    * shared motion tokens. Values above 1 are tolerated (a component may stack its own hover and
    * press weights) and clamped at the point of use, so callers never have to pre-scale.
    */
   public static float smoothControlState(float current, float target, float speed) {
      return UiAnimation.smooth(current, target, speed);
   }

   /** Shared hover/focus/selection transition. */
   public static float smoothState(float current, float target) {
      return UiAnimation.smooth(current, target, stateSpeed());
   }

   /** Shared press detent transition (in on {@code pressed}, out otherwise). */
   public static float smoothPress(float current, boolean pressed) {
      return UiAnimation.smooth(current, pressed ? 1.0F : 0.0F, pressed ? pressSpeedIn() : pressSpeedOut());
   }

   /**
    * Keyed variant of {@link #smoothPress(float, boolean)} for drawers that hold no per-row state
    * (popup rows, static tab helpers). Uses the same press rates, so a keyed press and an owned press
    * settle identically.
    */
   public static float smoothPress(String key, float current, boolean pressed) {
      return UiAnimation.smoothState(key, pressed ? 1.0F : 0.0F, pressed ? pressSpeedIn() : pressSpeedOut());
   }

   /** Named-state variant of {@link #smoothState(float, float)} for stateless/static drawers. */
   public static float smoothState(String key, float target) {
      return UiAnimation.smoothState(key, target, stateSpeed());
   }

   /**
    * Named-state transition with an explicit exponential rate, for the sliding-indicator style
    * animations that were already using a faster rate than ordinary hover (a selection pill that
    * travels should settle a little sooner than a hover tint, or it trails behind the pointer).
    */
   public static float smoothStateAt(String key, float target, float speed) {
      return UiAnimation.smoothState(key, target, speed);
   }

   private static float clampT(float v) {
      return UiAnimation.clamp01(v);
   }

   /** Muted low-emphasis surface for disabled buttons / locked controls. */
   public static int glassDisabledFillArgb() {
      return glassLevelFillArgb(LEVEL_DISABLED, 0.0F, 0.0F, accentColor());
   }

   /**
    * RGB the bottom of a recessed well darkens towards, and the RGB a pressed control darkens towards.
    *
    * <p>These were the literals {@code 0xFF1A192D} and {@code 0xFF070710} inside GlassRenderer. They
    * are glass-tint colours, not control colours, so they belong next to {@link #glassBaseRgb(float)}
    * where they can be seen together with the rest of the material: a bottom rim is the level tint
    * pulled towards the recessed reference by the same {@link #shiftLuminance(int, float)} used by
    * every level, and the press colour is simply the deepest ink of the current GlassDarkness. Neither
    * is a per-surface opacity curve — both are only anchor colours for the existing transfer.</p>
    */
   public static int glassRecessedDepthRgb() {
      return shiftLuminance(glassBaseRgb(glassDarkness()), -0.28F) & 0x00FFFFFF;
   }

   public static int glassPressRgb() {
      return shiftLuminance(glassBaseRgb(1.0F), -0.18F) & 0x00FFFFFF;
   }

   /**
    * Level 5: floating popup / tooltip surface.
    *
    * <p>Alpha is the shared tint mix (shader cutout), exactly like the window, so a popup and the
    * window behind it mix the same single backdrop capture in the same proportion.</p>
    */
   public static int glassPopupTintArgb(boolean blurActive) {
      float mix = glassTintMix();
      if (!blurActive) {
         mix = Math.min(0.96F, 0.10F + mix * 1.10F);
      }
      return withAlpha(
         glassLevelRgb(LEVEL_POPUP, 0.0F, 0.0F, accentColor()),
         Math.round(UiAnimation.clamp01(mix) * 255.0F)
      );
   }

   /**
    * Top-edge crystal catchlight. Deliberately not scaled to nothing by the tint mix: the specular
    * read is what makes glass glass, so it keeps a floor when the user turns the tint down.
    */
   public static float glassTopHighlightAlpha(float hoverT) {
      float op = UiAnimation.clamp01(glassOpacity());
      return (0.050F + 0.070F * UiAnimation.clamp01(hoverT)) * (0.60F + 0.40F * op);
   }

   /** Inner bottom AO / glow rim, passed to the primitive shader as {@code innerBottomAlpha}. */
   public static float glassInnerGlowAlpha(float hoverT) {
      float op = UiAnimation.clamp01(glassOpacity());
      return (0.045F + 0.045F * UiAnimation.clamp01(hoverT)) * (0.60F + 0.40F * op);
   }

   public static int glassTopBorderArgb(int baseBorder) {
      int a = (baseBorder >>> 24) & 255;
      int liftedRgb = lerpArgb(baseBorder, 0xFFFFFFFF, 0.35F) & 0x00FFFFFF;
      return (Math.min(255, Math.round(a * 1.15F)) << 24) | liftedRgb;
   }

   public static int glassBottomBorderArgb(int baseBorder) {
      return scaleAlpha(baseBorder, 0.85F);
   }

   public static int glassShadowArgb(float intensity) {
      return withAlpha(0xFF030408, Math.round(UiAnimation.clamp01(intensity) * 255.0F));
   }

   public static int withAlpha(int argb, int alpha0to255) {
      int a = Math.max(0, Math.min(255, alpha0to255));
      return (a << 24) | (argb & 0x00FFFFFF);
   }

   public static int scaleAlpha(int argb, float factor) {
      int a = (argb >>> 24) & 255;
      if (a == 0 && (argb & 0x00FFFFFF) != 0) {
         a = 255;
      }
      return withAlpha(argb, Math.round(a * Math.max(0.0F, Math.min(1.0F, factor))));
   }

   public static int lerpArgb(int fromArgb, int toArgb, float t) {
      float clamped = Math.max(0.0F, Math.min(1.0F, t));
      int a1 = (fromArgb >>> 24) & 255;
      int r1 = (fromArgb >> 16) & 255;
      int g1 = (fromArgb >> 8) & 255;
      int b1 = fromArgb & 255;

      int a2 = (toArgb >>> 24) & 255;
      int r2 = (toArgb >> 16) & 255;
      int g2 = (toArgb >> 8) & 255;
      int b2 = toArgb & 255;

      int a = Math.round(a1 + (a2 - a1) * clamped);
      int r = Math.round(r1 + (r2 - r1) * clamped);
      int g = Math.round(g1 + (g2 - g1) * clamped);
      int b = Math.round(b1 + (b2 - b1) * clamped);
      return (a << 24) | (r << 16) | (g << 8) | b;
   }

   private static int ensureOpaqueOrMinAlpha(int argb, int minAlpha) {
      int a = (argb >>> 24) & 255;
      if (a == 0) {
         a = 255;
      }
      return withAlpha(argb, Math.max(a, minAlpha));
   }

   public static ClickGui clickGui() {
      return ClientManagers.moduleManager != null ? ClientManagers.moduleManager.clickGui : null;
   }
}
