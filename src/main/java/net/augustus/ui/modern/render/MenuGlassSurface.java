package net.augustus.ui.modern.render;

import net.augustus.utils.render.BlurComponent;
import net.augustus.utils.render.KawaseBlurEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * The ClickGUI's glass material, as one reusable pill/panel surface for screens that live outside the
 * ClickGUI (the main menu first of all).
 *
 * <p>WHY it exists. A screen outside the overlay could only draw a flat translucent rectangle: the
 * ClickGUI's material is spread across {@code MainClickGuiWindow}, {@code ModernButton} and
 * {@code GlassRenderer}, each of which assumes the overlay's own frame (a backdrop capture, an
 * open/close envelope, per-component animation fields). A screen that just called
 * {@code drawRoundedRect} with an accent tint therefore read as a different product next to the
 * overlay's pills. This class composes the <em>same</em> tokens into a stateless draw call, so the
 * caller supplies the animation values it already owns and gets the identical material back.</p>
 *
 * <p>WHAT the surface is made of, exactly. Every colour, alpha and thickness is a {@link UiTheme} token,
 * with two deliberate exceptions - the white the accent is lifted towards for the hover light against
 * light, and the frost scrim's cool white - both of which are light rather than accent, and are justified
 * where they are declared:</p>
 * <ol>
 *   <li><b>Depth.</b> A soft accent halo, {@link GlassRenderer#drawSoftShadow} with
 *       {@code UiTheme.accentSoftGlow()} at {@code 0.18 * max(hover, press)}. Drawn only above a small
 *       threshold, and only when the shape shader is actually usable - {@code drawSoftShadow} silently
 *       no-ops without its shader, so gating it here avoids asking for a pass that will not exist.</li>
 *   <li><b>Fill.</b> The same level veil a ClickGUI control is painted with at rest, so this surface and
 *       a ClickGUI pill are the same material read at the same coverage: {@link UiTheme#glassLevelFillArgb}
 *       for the chip ({@code LEVEL_CONTROL_RAISED}) and the panel ({@code LEVEL_CARD}), both through the
 *       one shared {@link UiTheme#glassTintMix()} transfer, so Glass Opacity / Glass Darkness / the client
 *       tint colour move this surface exactly as they move the ClickGUI. The chip reads
 *       {@link UiTheme#glassControlVeilArgb} and the panel {@link UiTheme#glassCardFillArgb} - the named
 *       tokens {@code ModernButton} (Variant.GLASS) and the ClickGUI's cards resolve to - so the surface is
 *       a composition of the published tokens rather than of the level plumbing behind them.</li>
 *   <li><b>Vertical gradient.</b> The bottom of the shape lerps towards
 *       {@link UiTheme#glassRecessedDepthRgb()} (18% on a chip, 22% on a panel) - the same recessed anchor
 *       {@code ModernButton} and {@code drawGlassCardWithHover} use, which is what gives the pill a lit top
 *       and a settled bottom instead of a flat plate.</li>
 *   <li><b>Top catchlight.</b> {@link UiTheme#glassTopHighlightAlpha} for the chip (the button's own
 *       token) and the same token at 85% for the panel, plus {@link UiTheme#glassInnerGlowAlpha} as the
 *       inner bottom rim. Both go into {@link GlassRenderer#drawRoundedGradientBox}, whose shader spreads
 *       the catchlight along the whole top edge, which is what makes the highlight read "wider" on a tall
 *       panel even though the source is one token.</li>
 *   <li><b>Rim.</b> {@link UiTheme#glassBorderFocusedOrHover} at {@code 0.75 * hover}, lerped further
 *       towards {@link UiTheme#accentSoftGlow()} by the interaction amount, at
 *       {@link UiTheme#BORDER_THICKNESS} - the button's rim, which therefore still disappears when the
 *       user turns the ClickGUI's Outline toggle off and comes back as an accent ring on hover.</li>
 *   <li><b>Hover light.</b> The localised accent spotlight inside the shape that
 *       {@link GlassRenderer#drawInteractiveSurface} gives a ClickGUI control: alpha
 *       {@code 0.16 * hover * (1 - 0.35 * press)} (so it dims as the control sinks), colour the accent
 *       lifted towards white by 45% so it reads as light and not as more accent. Unlike the ClickGUI's
 *       control, this light follows the caller's cursor: its centre is the pointer clamped into the shape
 *       inset by its corner radius, and a chip draws it as a core pass at radius {@code max(w, h) * 0.45}
 *       plus a second pass at {@code 1.4} times that radius and a third of that alpha. WHY two passes:
 *       the shader's falloff is a squared smoothstep that reaches zero at its radius, and a radius small
 *       enough to read as a pool that sits somewhere on a 117x18 pill puts that zero on the pill itself,
 *       where the eye resolves it as a blob with an edge; the faint pass moves the composite's tail out
 *       past the point where a boundary is visible. A panel keeps the pre-cursor single centred wash - it
 *       is a tooltip, whose light is a general wash over a large surface rather than a pool that has to
 *       track anything (see {@link #PANEL_LIGHT_RADIUS}).</li>
 *   <li><b>Press sink.</b> The top fill lerps towards {@link UiTheme#glassPressRgb()} by {@code 0.22 * press}
 *       (the {@link GlassRenderer#drawInteractiveSurface} detent), the bottom follows through the
 *       recessed anchor, and the top catchlight is cut by 45% of {@code press} - a surface that is pushed
 *       in darkens and loses its specular edge instead of growing.</li>
 * </ol>
 *
 * <p>WHY it does not paint the nested liquid-glass pane ({@code GlassRenderer.drawGlassMaterialPane}):
 * that pane samples the overlay's per-frame framebuffer capture and crossfades at
 * {@code UiTheme.glassLevelAlpha(level) * GlassRenderer.overlayEnvelope}. Outside the overlay the
 * envelope is 1 and no surface has taken the frame's capture, so a pane there would capture a
 * framebuffer that already contains the screen's own glass, and the coverage would be a second veil on
 * top of this one - the exact stacking the overlay's opacity model exists to prevent. The veil path
 * above is the same path the ClickGUI itself uses whenever no blur is active, so this is the material,
 * not a fallback approximation of it.</p>
 *
 * <p>Frost, and the one rule this class must never break. There are two frost paths and a caller picks
 * deliberately. {@link #chipFrosted} adds the frost <em>look</em> - a cool-white scrim and a strengthened
 * inner rim - on top of the material, and captures nothing: the caller has already painted the blurred
 * backdrop inside the pill itself. {@link #chipBlurred} / {@link #panelBlurred} instead run the live
 * Kawase pyramid over one {@link #captureBackdrop} per frame, then any number of pills sampling that one
 * capture. The capture path is real blur and is kept, but it is opt-in and unused: a per-frame
 * framebuffer capture has frozen this client's AMD driver twice, so nothing on the main-menu path may
 * call {@link #captureBackdrop(float)}, {@link #chipBlurred}, {@link #panelBlurred} or
 * {@link #backdropBlur}, and {@link #chipFrosted} exists precisely so that path is never needed there.</p>
 *
 * <h3>Call order</h3>
 * <p>Per pill: the screen's own pre-baked, blurred backdrop inside the rounded shape, then
 * {@link #chipFrosted} with the cursor (it reduces exactly to the plain chip at {@code frost = 0}), then
 * {@link #zoneGlow} for the part under the cursor, then the label/icon - the zone glow goes underneath
 * the glyphs, never over them, so it can only light the part up and cannot haze the text. The capture
 * path, if a caller ever uses it, is {@link #captureBackdrop(float)} once per frame right after the
 * backdrop and before the first {@link #chipBlurred} / {@link #panelBlurred}. Either way the interplay
 * with the screen's own alpha envelope is just arithmetic: pass it as {@code alpha}, since every token
 * is scaled by it evenly, frost included.</p>
 *
 * <h3>Transitions (this class is stateless; the caller owns the two floats)</h3>
 * <p>Drive them with the shared tokens so a menu pill settles in step with a ClickGUI control:</p>
 * <pre>
 *   hover = UiTheme.smoothState(hover, hovered ? 1F : 0F);   // rate 13.0 - 95% in 230 ms
 *   press = UiTheme.smoothPress(press, hovered &amp;&amp; UiAnimation.isPrimaryMouseDown()); // 32 in / 20 out
 * </pre>
 * <p>{@link UiTheme#smoothState(float, float)} and {@link UiTheme#smoothPress(float, boolean)} are
 * frame-rate independent ({@code v += (target - v) * (1 - exp(-k * dt))}), but they read
 * {@link UiAnimation#dt()}, which only advances if the screen calls {@link UiAnimation#beginFrame()}
 * once per frame - {@code AugustusGuiScreen.drawScreen} already does. A screen that forgets still gets a
 * 16 ms step, i.e. a slower but never broken transition. If the caller prefers the keyed variants
 * ({@code UiTheme.smoothState(String, float)} / {@code smoothPress(String, float, boolean)}) there is no
 * per-pill field to keep; this helper does not drive either form, it only reads the result.</p>
 *
 * <h3>Safety outside the ClickGUI</h3>
 * <p>Everything here is immediate-mode GL with no game logic, no screen switching, no threads and no
 * allocation in the hot path. {@link GlassRenderer#drawSoftShadow} /
 * {@link GlassRenderer#drawRoundedGradientBox} already bracket their own passes with a
 * {@code GlStateSaver} (blend, alpha, depth, cull, lighting, texture 2D) and reset the GL colour, and
 * this class additionally restores the alpha test and the 1,1,1,1 colour on the way out. It is
 * therefore safe to call with no world loaded (the main menu) and from any screen that can draw quads.</p>
 *
 * <p>The hover light follows the pointer when the caller has one
 * ({@link #chip(float, float, float, float, float, int, float, float, float, float, float)},
 * {@link #chipFrosted}) and is centred on the shape when it does not ({@link #chip(float, float, float,
 * float, float, int, float, float, float)}, {@link #panel}, and the blurred variants, which forward
 * {@code Float.NaN}). The centre is the fallback for a caller that cannot supply a cursor, not a claim
 * about where the light belongs - a caller that <em>has</em> the cursor should pass it, because on a
 * 117x18 pill the difference between "under the cursor" and "in the middle" is the difference between the
 * effect existing and not existing. At the old {@code max(64, ...)} radius the light peaked at 0.16 in the
 * shape's centre, was down to 0.016 forty px out and to 0.0004 of the peak at the pill's end - a bright
 * middle on a pill that never moved, which is what "the glass is there but nothing happens when I move
 * over the options" describes.</p>
 */
public final class MenuGlassSurface {
   /** Below this the accent halo is not drawn at all, so a resting pill costs one quad. */
   private static final float GLOW_THRESHOLD = 0.04F;
   /** Blur radii outside this band are clamped: below it the Kawase engine treats blur as disabled. */
   private static final float MIN_BLUR_RADIUS = 1.0F;
   private static final float MAX_BLUR_RADIUS = 20.0F;

   /**
    * The cursor-following spotlight's peak coverage at hover 1, before press and the caller's alpha.
    *
    * <p>0.16 is {@link GlassRenderer#drawInteractiveSurface}'s own control light, kept rather than
    * re-tuned: this class exists so that a menu pill is the same material as a ClickGUI control, and the
    * light is the most visible part of that material. What was wrong was the radius, never the peak (see
    * {@link #LIGHT_CORE_RADIUS}).</p>
    */
   private static final float LIGHT_PEAK = 0.16F;

   /**
    * Core spotlight radius as a fraction of the shape's longer side, with no absolute floor.
    *
    * <p>WHY 0.45 of the long side: the shader's proximity is squared, so the light is already down to 25%
    * of its peak at half this radius and to 0 at 1.0x, which makes the visible pool roughly half the
    * pill's width - enough that the pool reads as a place <em>on</em> the pill rather than as the pill.
    * The previous formula, {@code max(64, max(w, h) * 0.65)}, is right for a ~30 px ClickGUI control,
    * where 64 px is larger than the control and the light fills it; on a 117x18 pill the same 64 px
    * radius put the 0.16 peak in the middle of a pill that never moved, 0.016 forty px out and 0.0004 of
    * the peak at the end - a fixed bright centre, i.e. no cursor light at all. No floor is kept, for the
    * same reason: any absolute floor reintroduces that fixed wash, and a shape small enough to need one
    * is already served by the shader's own {@code max(12.0, radius)}.</p>
    */
   private static final float LIGHT_CORE_RADIUS = 0.45F;

   /**
    * Radius multiple of the faint second pass: 1.4x the core, so the composite's tail ends at 1.4x
    * instead of at 1.0x, where a squared falloff arrives at zero with a resolvable edge.
    */
   private static final float LIGHT_WIDE_RADIUS = 1.4F;

   /**
    * Share of {@link #LIGHT_PEAK} the faint pass carries.
    *
    * <p>WHY a third: at the core's own edge this pass is at {@code (0.16 / 3) * (1 - smoothstep(0, 1.4r,
    * r))^2 = 0.002}, i.e. it does not add a second visible disc around the core, while at half the core
    * radius it contributes {@code 0.053 * 0.50 = 0.027} against the core's 0.040. That is enough to fill
    * the core's mid-range gradient so the falloff stays monotonic, which is the whole point of the pass; a
    * larger share makes two nested discs, the artefact it exists to remove.</p>
    */
   private static final float LIGHT_WIDE_SHARE = 1.0F / 3.0F;

   /**
    * The panel's spotlight, unchanged from before this class had a cursor: {@code max(64, max(w, h) *
    * 0.55)}.
    *
    * <p>A panel is the menu's tooltip - a large surface whose light is a broad wash, not a pool that has
    * to sit under a pointer - so it keeps a floor that a chip must not have, and it is drawn with a single
    * pass. Its appearance is deliberately untouched by the cursor work: a 0.45x radius on a 240x90 tooltip
    * would read as a spot floating on a plate.</p>
    */
   private static final float PANEL_LIGHT_MIN_RADIUS = 64.0F;
   private static final float PANEL_LIGHT_RADIUS = 0.55F;

   /**
    * The frost scrim's colour.
    *
    * <p>WHY a cool blue-white and not a theme token: the scrim is haze sitting on the material, and the
    * shader's own catchlight is already this colour - {@code vec3(0.97, 0.98, 1.0)} in
    * {@code glass_primitive.frag}, i.e. 247/250/255. Deriving the scrim from the accent instead would tint
    * the haze violet and turn the frost into a second halo, which is exactly what the material must not
    * become when the user picks a saturated accent.</p>
    */
   private static final int FROST_SCRIM_ARGB = 0xFFF7FAFF;

   /**
    * The frost scrim's peak alpha at {@code frost = 1}.
    *
    * <p>WHY 0.09: the band this material is specified in is 0.06-0.12 and the middle is used, because
    * neither end has room for the other's failure. The scrim lands on a pill that is already 61% opaque -
    * {@code glassLevelAlpha(LEVEL_CONTROL_RAISED)} is {@code 0.08 + 0.74 * 0.722} at the default Glass
    * Opacity - so its own coverage reaches the eye almost undiminished: at 0.12 of a near-white it is a
    * fifth of the pill's own veil and begins to flatten the vertical gradient the surface is built from,
    * and at 0.06 it is a tenth, which on this client's dark backdrop leaves only a hairline of visible
    * haze. 0.09 stays a scrim at any Glass Opacity. It is also haze rather than a second veil: composited
    * over that pill the combined coverage moves from 0.615 to 0.649, so the blurred backdrop keeps 91% of
    * the light it had and the frost lightens the glass without burying the blur it is meant to show.</p>
    */
   private static final float FROST_SCRIM_ALPHA = 0.09F;

   /**
    * The frost's inner rim alpha at {@code frost = 1}: 1.5x the scrim.
    *
    * <p>WHY stronger than the scrim: this pass repaints the outermost hairline the material already drew,
    * and an edge is read by the contrast across it - a rim at the scrim's own alpha would disappear into
    * the haze it borders. WHY 0.14 and no more: the pill's resting ring is {@link
    * UiTheme#glassBorderColor()}, 0x14 white, i.e. 0.078, so 0.14 is already 1.8x it where frost is 1;
    * past ~0.2 the hairline reads as a hard outline and the pill stops looking like the ClickGUI control
    * it has to match.</p>
    */
   private static final float FROST_RIM_ALPHA = 0.14F;

   /**
    * Alpha of the pale anchor {@link #zoneGlow} blends a caller's accent towards: the same anchor
    * {@link UiTheme#glassBorderFocusedOrHover} mixes into the pill's own hover ring.
    */
   private static final int ZONE_ACCENT_ANCHOR = 0xFFE2DCFF;

   /**
    * How far {@link #zoneGlow} lifts the caller's accent towards {@link #ZONE_ACCENT_ANCHOR}.
    *
    * <p>WHY 0.45: the pill's own hover ring already mixes an accent 20% towards this anchor, so a zone
    * glow at that ratio would be indistinguishable from the ring wherever the two meet - a sub-region
    * that lights up with the same colour the pill already has is not a sub-region highlight. Taking it
    * more than twice as far keeps the accent's hue (the zone is still recognisably the user's colour,
    * blended towards a pale violet-white) while lifting it far enough that the rim reads as light.</p>
    */
   private static final float ZONE_ACCENT_LIFT = 0.45F;

   /**
    * {@link #zoneGlow}'s bloom radius, in GUI pixels.
    *
    * <p>WHY 5: the bloom's softness is {@code blur * 0.55} sigma, so at 5 its coverage has fallen to
    * ~0.03 of the peak 4 px outside the zone - an icon zone inset a few px from the pill's edge therefore
    * cannot leak a visible amount past the pill's rim. Smaller than ~3 the bloom is a hard-edged patch,
    * which is the "selected button" read the zone must not have; much larger and it becomes haze over the
    * whole pill, which would make a part lighting up look like the pill lighting up.</p>
    */
   private static final float ZONE_BLOOM_BLUR = 5.0F;

   /**
    * {@link #zoneGlow}'s bloom peak alpha at {@code intensity = 1}.
    *
    * <p>WHY 0.10: the bloom is the only pass that covers the zone's interior, and the zone usually carries
    * the label or the glyph, so it is held at a sixth of the pill's own veil coverage (0.61 at the default
    * Glass Opacity) - a wash that reads as a lit area but is far below what would read as a plate behind
    * the text. The signal that a part is lit is carried by the rim; the bloom only has to give that rim a
    * soft inner edge.</p>
    */
   private static final float ZONE_BLOOM_ALPHA = 0.10F;

   /**
    * {@link #zoneGlow}'s rim alpha at {@code intensity = 1}.
    *
    * <p>WHY 205: the pill's own hover ring peaks at 155 ({@link
    * UiTheme#glassBorderFocusedOrHover}), and the zone ring has to be distinguishable from it rather than
    * blend into it, so it is set about a third above that and capped there: at 255 a 1 px ring of pale
    * accent is pure white and reads as a focus outline, which is a different control's vocabulary.</p>
    */
   private static final int ZONE_RIM_ALPHA = 205;

   private MenuGlassSurface() {
   }

   /**
    * Enabled rounded glass surface. radius in GUI pixels. hover/press in 0..1. alpha 0..1 multiplies the
    * whole surface. accentArgb tints rim, glow and top highlight (pass UiTheme.accentColor()).
    *
    * <p>The hover light is centred on the shape. A caller that has the cursor should use
    * {@link #chip(float, float, float, float, float, int, float, float, float, float, float)} instead: on
    * a pill this narrow, a centred light and a light under the cursor are not the same picture.</p>
    */
   public static void chip(
      float x,
      float y,
      float width,
      float height,
      float radius,
      int accentArgb,
      float hover,
      float press,
      float alpha
   ) {
      chip(x, y, width, height, radius, accentArgb, hover, press, alpha, Float.NaN, Float.NaN);
   }

   /**
    * The same chip with its hover light following the cursor.
    *
    * <p>The pointer is in the same pixel space as {@code x}/{@code y} - what {@code GuiScreen.drawScreen}
    * receives as {@code mouseX}/{@code mouseY}, not design-space coordinates. It is clamped into the shape
    * inset by its corner radius (see {@link #lightAxis}), so a cursor that has already left the pill, or a
    * caller that forwards the raw mouse position, still lights the pill from inside it rather than from
    * beyond its edge. {@code Float.NaN} on an axis means "no pointer on this axis" and centres the light
    * on the shape; the nine-argument {@code chip(...)} forwards {@code NaN, NaN} for that reason.</p>
    *
    * <p>Everything else - the veil, the rim, the catchlight and the press detent - is identical to
    * {@link #chip(float, float, float, float, float, int, float, float, float)}: a cursor changes one pair
    * of uniforms on the material, not the material. Cost is one extra light-only quad per frame while the
    * pill is hovered (and none at rest, since the light's own weight is the hover amount).</p>
    */
   public static void chip(
      float x,
      float y,
      float width,
      float height,
      float radius,
      int accentArgb,
      float hover,
      float press,
      float alpha,
      float pointerX,
      float pointerY
   ) {
      drawSurface(x, y, width, height, radius, accentArgb, hover, press, alpha, pointerX, pointerY, true);
   }

   /**
    * Same material tuned for a taller/larger panel (softer rim, wider top highlight). Its hover light is
    * the pre-cursor centred wash; see {@link #PANEL_LIGHT_RADIUS} for why a panel does not follow a
    * pointer.
    */
   public static void panel(
      float x,
      float y,
      float width,
      float height,
      float radius,
      int accentArgb,
      float hover,
      float press,
      float alpha
   ) {
      drawSurface(x, y, width, height, radius, accentArgb, hover, press, alpha, Float.NaN, Float.NaN, false);
   }

   /**
    * The chip with the frost <em>look</em> on top of it: a cool-white scrim plus a strengthened inner rim,
    * so the pill reads as frosted glass over the blurred backdrop the caller has already painted under it.
    *
    * <p>WHY this and not {@link #chipBlurred}: the menu screen owns a pre-baked, heavily blurred copy of
    * its backdrop and paints it inside each pill before calling here (the {@code drawFrostPill} path in
    * {@code HomeMainMenuScreen}), so the blur the eye sees under the glass is that copy, sampled with the
    * backdrop's own zoom and pan. A second blur from this class would have to capture the framebuffer, i.e.
    * exactly the per-frame capture that has frozen this client's driver - so {@code frost} is a
    * <em>strength of the frost look</em>, not an amount of blur, and this method never calls
    * {@link #captureBackdrop(float)}, {@link #chipBlurred}, {@link #panelBlurred} or
    * {@link #backdropBlur}. It is the only frost entry point the menu path needs.</p>
    *
    * <p>WHY the scrim is what changes the read: the pre-baked blur under a pill is already correct, but it
    * shows through a veil that is 61% opaque at the default Glass Opacity, so it reads as a dark tinted
    * picture rather than as frost. Frost is haze plus an edge: a thin cool-white scrim carries the blurred
    * light (see {@link #FROST_SCRIM_ALPHA}) and the same white on the outermost hairline keeps the edge
    * crisp instead of letting the scrim soften it (see {@link #FROST_RIM_ALPHA}), which is how etched glass
    * and ice read. No token moves: the fill is still {@link UiTheme#glassControlVeilArgb} at
    * {@link UiTheme#LEVEL_CONTROL_RAISED}, so a frosted pill is the same material as a ClickGUI control,
    * merely hazed.</p>
    *
    * <p>{@code frost = 0} is {@code chip(...)} exactly: the two frost passes are skipped. The caller's
    * {@code alpha} scales them like every other coverage here, so a pill cannot hold its frost while the
    * screen fades out from under it.</p>
    *
    * @param frost 0..1 strength of the frost look; clamped
    */
   public static void chipFrosted(
      float x,
      float y,
      float width,
      float height,
      float radius,
      int accentArgb,
      float hover,
      float press,
      float alpha,
      float pointerX,
      float pointerY,
      float frost
   ) {
      float a = UiAnimation.clamp01(alpha);
      if (width <= 0.0F || height <= 0.0F || a <= 0.004F) {
         return;
      }
      chip(x, y, width, height, radius, accentArgb, hover, press, alpha, pointerX, pointerY);
      float f = UiAnimation.clamp01(frost);
      if (f <= 0.004F) {
         return;
      }
      float r = clampRadius(radius, width, height);

      int prevAlphaFunc = GlStateManager.getAlphaFunc();
      float prevAlphaRef = GlStateManager.getAlphaRef();
      boolean prevAlphaTest = GlStateManager.isAlphaEnabled();
      try {
         // Scrim first, rim second: the rim has to survive the scrim, not be softened by it.
         GlassRenderer.drawRoundedRect(x, y, width, height, r, frostArgb(FROST_SCRIM_ALPHA, f, a));
         GlassRenderer.drawRoundedOutline(
            x,
            y,
            width,
            height,
            r,
            UiTheme.BORDER_THICKNESS,
            frostArgb(FROST_RIM_ALPHA, f, a)
         );
      } finally {
         restoreDrawState(prevAlphaFunc, prevAlphaRef, prevAlphaTest);
      }
   }

   /**
    * A soft highlight for one part of an already drawn surface: the icon zone, the label or the chevron of
    * a pill.
    *
    * <p>WHAT it draws, and what it deliberately does not. Two passes, both accent tinted and both bounded
    * by the rounded rect it is given: a soft bloom (a blurred copy of the zone,
    * {@link GlassRenderer#drawSoftShadow} at {@link #ZONE_BLOOM_ALPHA}) and a 1 px rim
    * ({@link GlassRenderer#drawRoundedOutline} at {@link UiTheme#BORDER_THICKNESS}, the same hairline
    * weight as every other ring in this UI). Both are light: there is no fill pass, and the rim is no
    * thicker than the border token, so at {@code intensity = 1} the zone reads as a part that lit up - not
    * as a second, smaller button, which is what a filled or double-ringed sub-rectangle of a pill reads
    * as.</p>
    *
    * <p>The rim carries the signal and the bloom only gives it a soft inner edge; the bloom is the only
    * coverage over the zone's interior, so it is kept at a sixth of the pill's own veil (see
    * {@link #ZONE_BLOOM_ALPHA}). Draw this <em>before</em> the label or icon, so the glyphs are never
    * hazed by it.</p>
    *
    * <p>WHY a region and not "the pill plus a part id": where a part begins and ends is the screen's
    * business - it is the only thing that knows where a label ended and a chevron started - while this
    * primitive only has to be correct about the shape it is handed. The region may sit anywhere inside the
    * pill; the bloom's spill is small enough (~2 px at {@link #ZONE_BLOOM_BLUR}) that a region flush with
    * the pill's edge softens that edge rather than painting outside it.</p>
    *
    * @param intensity 0..1; at 0 (and below 0.004, the same early-out the surface uses) nothing is drawn.
    *        This is the only weight the call has, so a screen that fades its pills in or out must fold
    *        its own envelope in here ({@code intensity * alpha}) - this class cannot read it.
    */
   public static void zoneGlow(float x, float y, float width, float height, float radius, int accentArgb, float intensity) {
      if (width <= 0.0F || height <= 0.0F) {
         return;
      }
      float t = UiAnimation.clamp01(intensity);
      if (t <= 0.004F) {
         return;
      }
      int zoneRgb = UiTheme.lerpArgb(resolveAccent(accentArgb), ZONE_ACCENT_ANCHOR, ZONE_ACCENT_LIFT);
      float r = clampRadius(radius, width, height);

      int prevAlphaFunc = GlStateManager.getAlphaFunc();
      float prevAlphaRef = GlStateManager.getAlphaRef();
      boolean prevAlphaTest = GlStateManager.isAlphaEnabled();
      try {
         GlassRenderer.drawSoftShadow(
            x,
            y,
            width,
            height,
            r,
            ZONE_BLOOM_BLUR,
            0.0F,
            UiTheme.withAlpha(zoneRgb, Math.round(ZONE_BLOOM_ALPHA * 255.0F * t))
         );
         GlassRenderer.drawRoundedOutline(
            x,
            y,
            width,
            height,
            r,
            UiTheme.BORDER_THICKNESS,
            UiTheme.lerpArgb(UiTheme.withAlpha(zoneRgb, 0), UiTheme.withAlpha(zoneRgb, ZONE_RIM_ALPHA), t)
         );
      } finally {
         restoreDrawState(prevAlphaFunc, prevAlphaRef, prevAlphaTest);
      }
   }

   /**
    * The chip with the ClickGUI's real frost underneath it: the backdrop is sampled through the Kawase
    * pyramid, so what shows through the pill is the <em>blurred</em> frame rather than the frame itself.
    *
    * <p>WHY this is a separate call from {@link #backdropBlur}: the popup variant captures the framebuffer
    * afresh and invalidates the cache afterwards, which is right for one floating panel and ruinous for a
    * menu with twenty pills - it would run a full pyramid per pill. The cached variant
    * ({@link KawaseBlurEngine#drawRoundedBlur}) reuses one capture, so the caller must refresh that capture
    * once per frame, after the screen's background and before its first pill, with
    * {@link #captureBackdrop(float)}. Cost is then one pyramid per frame plus one textured quad per pill.</p>
    *
    * <p>Nothing on the main-menu path may call this: see the frost paragraph in the class javadoc, and use
    * {@link #chipFrosted} with a backdrop the screen has already blurred itself.</p>
    *
    * <p>Degrades silently to the plain chip when the engine is unavailable (no Framebuffer, OptiFine
    * Fast Render / AA / Anisotropic Filtering, a shader that failed to compile) - the pill still reads as
    * the ClickGUI material, it just loses its frost.</p>
    */
   public static void chipBlurred(
      float x,
      float y,
      float width,
      float height,
      float radius,
      int accentArgb,
      float hover,
      float press,
      float alpha
   ) {
      drawCachedBlur(x, y, width, height, radius, alpha);
      drawSurface(x, y, width, height, radius, accentArgb, hover, press, alpha, Float.NaN, Float.NaN, true);
   }

   /** The panel counterpart of {@link #chipBlurred} for taller surfaces. */
   public static void panelBlurred(
      float x,
      float y,
      float width,
      float height,
      float radius,
      int accentArgb,
      float hover,
      float press,
      float alpha
   ) {
      drawCachedBlur(x, y, width, height, radius, alpha);
      drawSurface(x, y, width, height, radius, accentArgb, hover, press, alpha, Float.NaN, Float.NaN, false);
   }

   /**
    * Refreshes the cached Kawase capture for this frame. Call once, after the background is painted and
    * before the first {@link #chipBlurred} / {@link #panelBlurred}.
    *
    * @param strength blur radius in GUI pixels; a value &le; 0 means "use the ClickGUI material radius"
    */
   public static void captureBackdrop(float strength) {
      if (!canBlur()) {
         return;
      }
      float radius = resolveBlurRadius(strength);
      if (!(radius > 0.0F)) {
         return;
      }
      int prevAlphaFunc = GlStateManager.getAlphaFunc();
      float prevAlphaRef = GlStateManager.getAlphaRef();
      boolean prevAlphaTest = GlStateManager.isAlphaEnabled();
      try {
         KawaseBlurEngine.recapture(radius);
      } finally {
         restoreDrawState(prevAlphaFunc, prevAlphaRef, prevAlphaTest);
      }
   }

   /** Draws the cached blurred backdrop through a rounded mask; a no-op when blur is not usable. */
   private static void drawCachedBlur(float x, float y, float width, float height, float radius, float alpha) {
      if (width <= 0.0F || height <= 0.0F || UiAnimation.clamp01(alpha) <= 0.004F || !canBlur()) {
         return;
      }
      float blurRadius = resolveBlurRadius(0.0F);
      if (!(blurRadius > 0.0F)) {
         return;
      }
      KawaseBlurEngine.drawRoundedBlur(x, y, width, height, clampRadius(radius, width, height), blurRadius);
   }

   /** True when the Kawase engine can run in this frame; the cheap guards first, the GL check last. */
   private static boolean canBlur() {
      Minecraft mc = Minecraft.getMinecraft();
      if (mc == null || mc.getFramebuffer() == null) {
         return false;
      }
      // The engine samples whatever framebuffer is bound; with none bound it would capture the world's
      // back buffer instead of this screen, so refuse rather than risk a wrong backdrop.
      // 36006 is GL_FRAMEBUFFER_BINDING, which LWJGL 2's GL11 does not expose (see KawaseBlurEngine).
      if (GL11.glGetInteger(36006) == 0) {
         return false;
      }
      try {
         return KawaseBlurEngine.isAvailable();
      } catch (Throwable t) {
         // A GL-context-less caller (a unit test, an early init) must not take the screen down with it.
         return false;
      }
   }

   /** The material radius, clamped into the band the Kawase engine actually blurs in. */
   private static float resolveBlurRadius(float strength) {
      float blurRadius = strength > 0.0F ? strength : UiTheme.blurRadius();
      if (!(blurRadius > 0.0F)) {
         return 0.0F;
      }
      return Math.max(MIN_BLUR_RADIUS, Math.min(MAX_BLUR_RADIUS, blurRadius));
   }

   /**
    * Optional: a real blurred backdrop behind the rect, if you can make it safe outside the ClickGUI.
    * No-op (not a crash) when unavailable. Call BEFORE chip()/panel().
    *
    * <p>This really does blur, and it does not need any of the ClickGUI's plumbing. The overlay's blur
    * goes through {@code BlurShadowManager} (a deferred render pass with {@code enableBlur()}, a GUI layer
    * and a mask framebuffer), which is state that belongs to the overlay's frame. The Kawase engine behind
    * {@link BlurComponent} is usable on its own, though: {@link KawaseBlurEngine#drawPopupBlur} captures
    * the <em>currently bound</em> framebuffer, runs the whole downsample/upsample pyramid into its own
    * FBOs and restores the GL state, matrix stack, viewport, scissor and framebuffer binding it touched.
    * Nothing is deferred, so it works in the middle of any screen's draw.</p>
    *
    * <p>The popup variant rather than {@link BlurComponent#drawRounded} on purpose: it captures the frame
    * afresh for this shape (a popup-style capture) and invalidates the cached capture afterwards instead
    * of leaving a mid-frame capture behind for later glass to sample, which is the generation desync
    * {@code GlassBackdrop} documents. The cost is one pyramid pass per call, which is why a screen should
    * call it for a few large panes, not for every pill.</p>
    *
    * <p>When it silently draws nothing (no exception, no crash): the client framebuffer is disabled or
    * unavailable; OptiFine has Fast Render, Antialiasing or Anisotropic Filtering on
    * ({@link net.augustus.utils.render.BlurShadowManager#isShadersCompatible()}); the Kawase/liquid-glass
    * GLSL failed to compile or link; the screen draws into no framebuffer; or the resolved radius is not
    * positive. All of those are read through {@link KawaseBlurEngine#isAvailable()} and a null-safe
    * {@code Minecraft} lookup, so no world and no initialised client state are required.</p>
    *
    * @param strength blur radius in GUI pixels; a value &le; 0 means "use the ClickGUI material radius",
    *                 and if that is 0 too (Blur Intensity 0) the call is a no-op
    */
   public static void backdropBlur(float x, float y, float width, float height, float radius, float strength) {
      if (width <= 0.0F || height <= 0.0F) {
         return;
      }
      Minecraft mc = Minecraft.getMinecraft();
      if (mc == null || mc.getFramebuffer() == null) {
         return;
      }
      // The Kawase engine samples whatever framebuffer is bound; with none bound it would capture the
      // world's back buffer instead of this screen, so refuse rather than risk a wrong backdrop.
      // 36006 is GL_FRAMEBUFFER_BINDING, which LWJGL 2's GL11 does not expose (see KawaseBlurEngine).
      if (GL11.glGetInteger(36006) == 0) {
         return;
      }

      float blurRadius = strength > 0.0F ? strength : UiTheme.blurRadius();
      if (!(blurRadius > 0.0F)) {
         return;
      }
      blurRadius = Math.max(MIN_BLUR_RADIUS, Math.min(MAX_BLUR_RADIUS, blurRadius));

      boolean available;
      try {
         available = KawaseBlurEngine.isAvailable();
      } catch (Throwable t) {
         // A GL-context-less caller (a unit test, an early init) must not take the screen down with it.
         available = false;
      }
      if (!available) {
         return;
      }

      int prevAlphaFunc = GlStateManager.getAlphaFunc();
      float prevAlphaRef = GlStateManager.getAlphaRef();
      boolean prevAlphaTest = GlStateManager.isAlphaEnabled();
      try {
         BlurComponent.drawPopup(x, y, width, height, Math.max(0.0F, radius), blurRadius);
      } finally {
         restoreDrawState(prevAlphaFunc, prevAlphaRef, prevAlphaTest);
      }
   }

   /**
    * The one surface implementation behind the two {@code chip(...)} overloads and {@link #panel}.
    *
    * @param pointerX,pointerY cursor in the same pixel space as {@code x}/{@code y}, or {@code Float.NaN}
    *        for "no pointer", which centres the hover light on the shape (see {@link #lightAxis})
    * @param compact true for the chip's tuning (button catchlight, cursor-following spotlight with its
    *        second pass, tighter halo)
    */
   private static void drawSurface(
      float x,
      float y,
      float width,
      float height,
      float radius,
      int accentArgb,
      float hover,
      float press,
      float alpha,
      float pointerX,
      float pointerY,
      boolean compact
   ) {
      if (width <= 0.0F || height <= 0.0F) {
         return;
      }
      float a = UiAnimation.clamp01(alpha);
      if (a <= 0.004F) {
         return;
      }
      float h = UiAnimation.clamp01(hover);
      float p = UiAnimation.clamp01(press);
      int accent = resolveAccent(accentArgb);
      float r = clampRadius(radius, width, height);

      // The light's centre in the shape's local pixel space. The shader compares it against a 0..size
      // pixel position, and drawRoundedGradientBox subtracts the shape's origin again, so the pointer
      // arrives here already local and leaves as an absolute coordinate - one subtraction, one place.
      float lightX = lightAxis(pointerX - x, width, r);
      float lightY = lightAxis(pointerY - y, height, r);
      float lightRadius = compact
         ? Math.max(width, height) * LIGHT_CORE_RADIUS
         : Math.max(PANEL_LIGHT_MIN_RADIUS, Math.max(width, height) * PANEL_LIGHT_RADIUS);

      int prevAlphaFunc = GlStateManager.getAlphaFunc();
      float prevAlphaRef = GlStateManager.getAlphaRef();
      boolean prevAlphaTest = GlStateManager.isAlphaEnabled();
      try {
         drawHalo(x, y, width, height, r, h, p, a);

         // Material first, at full strength, and the caller's fade applied once at the end - fading the
         // top and then lerping a faded colour again would darken the bottom of the shape at the square of
         // the envelope.
         int topFill = fillArgb(accent, h, p, compact);
         int bottomFill = UiTheme.lerpArgb(topFill, UiTheme.glassRecessedDepthRgb(), compact ? 0.18F : 0.22F);
         int rim = rimArgb(accent, h, p, compact);
         int lightColor = UiTheme.lerpArgb(accent, 0xFFE6E0FF, compact ? 0.45F : 0.40F);

         GlassRenderer.drawRoundedGradientBox(
            x,
            y,
            width,
            height,
            r,
            r,
            r,
            r,
            scaled(topFill, a),
            scaled(bottomFill, a),
            scaled(rim, a),
            UiTheme.BORDER_THICKNESS,
            highlightAlpha(h, p, compact) * a,
            UiTheme.glassInnerGlowAlpha(Math.max(h, p)) * a,
            x + lightX,
            y + lightY,
            lightAlpha(h, p) * a,
            lightRadius,
            scaled(lightColor, a)
         );

         if (compact) {
            // The faint wide pass, after the material: drawn first it would sit under the veil and lose
            // most of its coverage to it, and the fill cannot be drawn twice without stacking the veil.
            drawLightPass(
               x,
               y,
               width,
               height,
               r,
               lightX,
               lightY,
               lightAlpha(h, p) * LIGHT_WIDE_SHARE * a,
               lightRadius * LIGHT_WIDE_RADIUS,
               scaled(lightColor, a)
            );
         }
      } finally {
         restoreDrawState(prevAlphaFunc, prevAlphaRef, prevAlphaTest);
      }
   }

   /**
    * A spotlight-only pass over the same shape: {@link GlassRenderer#drawRoundedGradientBox} with every
    * weight but the light at zero.
    *
    * <p>WHY it exists: the primitive resolves the light, the fill, the rim and the catchlight in one pass,
    * so a second light weight means a second call - and a second call that still had its fill set would
    * composite the veil twice (the pill would darken) while one that still had its rim set would thicken
    * it. Zeroing the fill, the rim and both highlight weights leaves the SDF and the light, and the shader
    * still multiplies the light by the shape's coverage, so the spot stays clipped to the rounded
    * silhouette and is discarded where there is nothing to draw. It is private because which weights have
    * to be zero for this to be a light and not a surface is not a caller's decision.</p>
    *
    * @param lightX,lightY spotlight centre in the shape's local pixel space
    */
   private static void drawLightPass(
      float x,
      float y,
      float width,
      float height,
      float radius,
      float lightX,
      float lightY,
      float lightAlpha,
      float lightRadius,
      int lightArgb
   ) {
      if (lightAlpha <= 0.001F || (lightArgb >>> 24) == 0) {
         return;
      }
      GlassRenderer.drawRoundedGradientBox(
         x,
         y,
         width,
         height,
         radius,
         radius,
         radius,
         radius,
         0,
         0,
         0,
         0.0F,
         0.0F,
         0.0F,
         x + lightX,
         y + lightY,
         lightAlpha,
         lightRadius,
         lightArgb
      );
   }

   /**
    * One axis of the hover light's centre, in the shape's local pixel space.
    *
    * <p>{@code NaN} - what the nine-argument {@code chip(...)} forwards when its caller has no cursor - is the
    * shape's own centre, i.e. the shape's centre is a fallback for a caller that cannot supply a pointer,
    * not a claim that the light belongs there. Otherwise the pointer is clamped into the shape inset by its
    * corner radius: that is the rectangle on which the silhouette has surface at every point, so a light
    * centred on a clamped pointer can never end up in a corner the rounded arc has cut away, and a cursor
    * that has already left the pill still lights the pill from inside it. The clamp is a no-op for a
    * pointer that is over the shape, which is the only case that changes what the user sees.
    *
    * @param inset the shape's clamped corner radius, which is never more than half the axis
    */
   private static float lightAxis(float pointer, float size, float inset) {
      float centre = size * 0.5F;
      if (Float.isNaN(pointer)) {
         return centre;
      }
      return Math.max(inset, Math.min(size - inset, pointer));
   }

   /** The corner radius the primitives can draw: never negative, never past half the shorter side. */
   private static float clampRadius(float radius, float width, float height) {
      return Math.max(0.0F, Math.min(radius, Math.min(width, height) * 0.5F));
   }

   /**
    * The accent a surface actually draws with: the caller's, unless it cannot paint a rim.
    *
    * <p>An accent with no RGB, or one so faint it cannot paint a rim, falls back to the theme accent, so a
    * caller that forwards a half-built token still gets the client's colour instead of an invisible rim.
    * Shared by {@link #drawSurface} and {@link #zoneGlow} rather than written twice.</p>
    */
   private static int resolveAccent(int accentArgb) {
      return (accentArgb & 0x00FFFFFF) != 0 && (accentArgb >>> 24) >= 48 ? accentArgb : UiTheme.accentColor();
   }

   /** One frost layer: the cool-white scrim colour at {@code peak} scaled by the frost and the caller's fade. */
   private static int frostArgb(float peak, float frost, float alpha) {
      return scaled(UiTheme.withAlpha(FROST_SCRIM_ARGB, Math.round(peak * 255.0F * frost)), alpha);
   }

   /**
    * The material veil: the exact token a ClickGUI control resolves to.
    *
    * <p>{@link UiTheme#glassControlVeilArgb} is what {@code ModernButton} (Variant.GLASS) paints and
    * {@link UiTheme#glassCardFillArgb} is what the ClickGUI's cards paint; both are thin wrappers over the
    * shared level transfer, so using the named tokens keeps this surface a composition of published
    * material tokens rather than a second opacity formula.</p>
    */
   private static int fillArgb(int accent, float hover, float press, boolean compact) {
      return compact
         ? UiTheme.glassControlVeilArgb(UiTheme.LEVEL_CONTROL_RAISED, hover, press, accent)
         : UiTheme.glassCardFillArgb(hover, press, accent);
   }

   /**
    * The accent halo under the shape: {@link UiTheme#accentSoftGlow()} at an alpha that ramps in with the
    * interaction amount and widens a little on hover, so the pill lifts off the background the way a
    * ClickGUI card does. Skipped entirely at rest.
    */
   private static void drawHalo(
      float x,
      float y,
      float width,
      float height,
      float radius,
      float hover,
      float press,
      float alpha
   ) {
      float amount = Math.max(hover, press);
      if (amount <= GLOW_THRESHOLD) {
         return;
      }
      // The crystal glow rather than the raw accent: it is the lighter, whiter sibling the ClickGUI's own
      // hover halo reads as, so the spill stays a highlight instead of a saturated violet smear.
      int halo = scaled(UiTheme.withAlpha(UiTheme.accentSoftGlow(), Math.round(46.0F * amount)), alpha);
      GlassRenderer.drawSoftShadow(x, y, width, height, radius, 8.0F + hover * 3.0F, 1.0F, halo);
   }

   /**
    * The rim: the button's hover/focus ring when the shape can afford it, and a softened version of it for
    * a panel.
    *
    * <p>Both branches start at {@link UiTheme#glassBorderFocusedOrHover}, which is
    * {@link UiTheme#glassBorderColor()} at rest - i.e. it is already 0 when the user turns the ClickGUI's
    * Outline toggle off. Only a hovered/pressed surface brings an accent ring back, through
    * {@link UiTheme#accentSoftGlow()}.</p>
    */
   private static int rimArgb(int accent, float hover, float press, boolean compact) {
      if (compact) {
         // ModernButton's GLASS rim: hover pulls the hairline towards the crystal glow, press keeps it.
         return UiTheme.lerpArgb(
            UiTheme.glassBorderFocusedOrHover(hover * 0.75F, accent),
            UiTheme.withAlpha(UiTheme.accentSoftGlow(), 155),
            Math.max(hover, press)
         );
      }
      // Softer on a panel: the ring has more edge to cover, so it is held further from full accent and
      // only reaches the crystal glow under interaction (the hero-card ramp).
      int base = UiTheme.scaleAlpha(UiTheme.glassBorderFocusedOrHover(hover * 0.60F, accent), 0.70F);
      return UiTheme.lerpArgb(base, UiTheme.withAlpha(UiTheme.accentSoftGlow(), 150), Math.max(hover * 0.55F, press * 0.80F));
   }

   /**
    * Top catchlight, before the caller's alpha is applied.
    *
    * <p>The chip uses {@link UiTheme#glassTopHighlightAlpha} verbatim - the same token a ClickGUI button
    * hands to {@code drawInteractiveSurface}. The panel lowers it slightly and widens it (the gradient box
    * carries the highlight across the whole top edge), because on a tall surface the button-strength band
    * reads as a hard stripe rather than as a specular edge.</p>
    */
   private static float highlightAlpha(float hover, float press, boolean compact) {
      float base = UiTheme.glassTopHighlightAlpha(hover);
      if (!compact) {
         base *= 0.85F;
      }
      // Same press detent as drawInteractiveSurface: the catchlight recedes as the surface sinks.
      return base * (1.0F - press * 0.45F);
   }

   /**
    * Core hover-spotlight weight, before the caller's alpha is applied. The faint second pass is this
    * value times {@link #LIGHT_WIDE_SHARE}, so the two passes can never drift apart.
    */
   private static float lightAlpha(float hover, float press) {
      return hover * LIGHT_PEAK * (1.0F - press * 0.35F);
   }

   /**
    * {@code argb} with its alpha multiplied by {@code factor} (RGB untouched).
    *
    * <p>{@link UiTheme#scaleAlpha} is deliberately not used here: it lifts a 0 alpha with non-black RGB
    * back to 255, which is right for borders under an Outline toggle but wrong for the caller's fade -
    * a translucent token handed in by a caller would come back opaque.</p>
    */
   private static int scaled(int argb, float factor) {
      return UiTheme.withAlpha(argb, Math.round((float)(argb >>> 24) * UiAnimation.clamp01(factor)));
   }

   /**
    * Puts back the alpha test and the 1,1,1,1 colour after a pass. The blend/alpha/depth/cull/lighting/
    * texture-2D toggles and the GL colour are already restored by the {@code GlStateSaver} inside every
    * {@link GlassRenderer} call and by {@link KawaseBlurEngine}; this covers the alpha function those
    * passes change and guarantees the colour contract even if a pass bails out early (no shader, no
    * framebuffer).
    */
   private static void restoreDrawState(int alphaFunc, float alphaRef, boolean alphaTestEnabled) {
      GlStateManager.alphaFunc(alphaFunc, alphaRef);
      if (alphaTestEnabled) {
         GlStateManager.enableAlpha();
      } else {
         GlStateManager.disableAlpha();
      }
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
   }
}