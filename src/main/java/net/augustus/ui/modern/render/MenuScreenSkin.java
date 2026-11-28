package net.augustus.ui.modern.render;

import net.augustus.ui.menu.ClientMenuTab;
import net.augustus.ui.menu.MenuBackdrop;

/**
 * The redesigned menu's own material, handed to the stock Minecraft screens so an option on {@code GuiOptions}
 * and a pill on the home screen are the same object.
 *
 * <p>WHY this class exists at all. {@code GuiButton} used to paint its plate through
 * {@link GlassRenderer#drawInteractiveSurface}, which is the <em>ClickGUI's</em> control material: a tinted
 * raised veil with a lit top edge and an inner bottom glow. That call also hard-codes its two inner-line
 * weights ({@code topHighlightAlpha} from the caller, {@code innerBottomAlpha} literally {@code 0.06F}), so
 * the two bright hairlines the user sees along the top and the bottom of every option on this screen cannot
 * be switched off from a caller. The home screen's pills solved the same problem by hand - see
 * {@code HomeMainMenuScreen.drawPillBox}, which drives {@link GlassRenderer#drawRoundedGradientBox} directly
 * and passes {@code 0, 0} for exactly those two weights. This class is that call, made reusable, plus the
 * three other pieces the stock screens need to stop looking like a different application:</p>
 *
 * <ul>
 *   <li>{@link #drawOptionSurface} - the menu option material (fills, one accent ring, cursor light) with
 *       <b>both inner-line weights at zero</b>, so no surface here can carry a second line of any kind.</li>
 *   <li>{@link #drawSliderKnob} - the slider thumb, painted with the ClickGUI slider's own accent fill
 *       (see {@code ModernSlider}) instead of the old flat dark client-colour bar.</li>
 *   <li>{@link #drawBackdrop} - the menu's baked blurred backdrop, shared by every menu screen.</li>
 *   <li>{@link #LABEL_COLOR} - the menu's resting label colour, so a stock label and a pill label rest on
 *       one value.</li>
 * </ul>
 *
 * <p>The numbers are the menu's, verbatim. {@code HomeMainMenuScreen} keeps its own copies of them as
 * {@code private static final} fields; they are duplicated here rather than promoted to a shared home because
 * that file belongs to another owner this round. If the menu's pill is ever re-tuned, both places have to move
 * together, which is why every constant below names the token it was copied from.</p>
 *
 * <p>Nothing here draws a framebuffer capture of its own: the material pane samples the ClickGUI's existing
 * per-frame capture (the same one the ClickGUI's own controls are made of), and the backdrop is the baked
 * {@code bg_*}/{@code bgb_*} pair. No new capture path is introduced.</p>
 */
public final class MenuScreenSkin {
   // ---------------------------------------------------------------------------------------------
   // The menu's option material (copied from HomeMainMenuScreen's PILL_* tokens)
   // ---------------------------------------------------------------------------------------------

   /** Menu pill fill, top: {@code HomeMainMenuScreen.PILL_FILL_TOP}. */
   public static final int FILL_TOP = 0xFF1A1028;
   /** Menu pill fill, bottom: {@code HomeMainMenuScreen.PILL_FILL_BOTTOM}. */
   public static final int FILL_BOTTOM = 0xFF150D20;
   /** Dynamic theme-aware pill top fill. */
   public static int fillTop() {
      return UiTheme.menuPillFillTopArgb();
   }
   /** Dynamic theme-aware pill bottom fill. */
   public static int fillBottom() {
      return UiTheme.menuPillFillBottomArgb();
   }
   /** Veil strength of that fill over the backdrop: {@code HomeMainMenuScreen.PILL_VEIL}. */
   public static final float VEIL = 0.78F;
   /** How far the ring's colour is pulled from the accent towards {@link #FILL_TOP}. */
   public static final float EDGE_MIX = 0.58F;
   /** Ring alpha at rest, and at full hover. */
   public static final int EDGE_ALPHA = 236;
   public static final int EDGE_HOVER_ALPHA = 255;
   /** Cursor light: peak alpha, radius as a share of the longer side, and its lift from the accent. */
   public static final float LIGHT_PEAK = 0.24F;
   public static final float LIGHT_RADIUS = 0.45F;
   public static final float LIGHT_LIFT = 0.50F;
   /** How much of the veil a full press removes, mirroring {@code drawPillBox}'s {@code 1 - 0.06 * p}. */
   public static final float PRESS_DARKEN = 0.06F;

   /**
    * Resting label colour, i.e. {@code HomeMainMenuScreen.TEXT_BASE}.
    *
    * <p>Not {@link UiTheme#primaryTextColor()}: the menu's own labels rest on this value, and the point of
    * this round is that a label does not change colour between two screens either. Applied to the label at
    * rest only - hover must not touch it (explicit user rule).</p>
    */
   public static final int LABEL_COLOR = 0xE6EDE7F4;

   /** One shared animated backdrop for every menu screen. */
   private static final MenuBackdrop BACKDROP = MenuBackdrop.getShared();

   private MenuScreenSkin() {
   }

   // ---------------------------------------------------------------------------------------------
   // Background
   // ---------------------------------------------------------------------------------------------

   /**
    * Draws the menu's blurred backdrop over the whole screen, in the caller's logical GUI coordinates.
    *
    * <p>Same call, same zoom and same pan the home screen makes - it is the same object, and its clock is the
    * shared {@link UiAnimation} delta, so two menu screens show the sequence at the same rhythm. The opacity
    * is left at 1: a stock screen has no entrance envelope, and fading the backdrop in behind an already
    * interactive option list would only look like a hitch.</p>
    */
   public static void drawBackdrop(float width, float height) {
      BACKDROP.draw(0.0F, 0.0F, width, height, 1.0F);
   }

   /** True while the baked frames could be loaded; callers may want a plain fill otherwise. */
   public static boolean isBackdropAvailable() {
      return BACKDROP.isAvailable();
   }

   // ---------------------------------------------------------------------------------------------
   // Option surface
   // ---------------------------------------------------------------------------------------------

   /**
    * Corner radius of an option: {@code min(h*0.5, OPTION_RADIUS_MAX)}, additionally capped by half the width so
    * an 8 px-wide slider knob cannot be handed a radius wider than itself (the primitive rounds a corner by its
    * own radius and a shape narrower than its radius would bow outwards).
    *
    * <p>WHY the cap is {@link #OPTION_RADIUS_MAX} and not {@link UiTheme#CARD_RADIUS}: the user's note is that the
    * corners "must not be so round". At the card radius a 20 px option is a capsule (radius 10 = half its height)
    * and a 32 px row is almost one, which is a different shape from the home screen's pills (radius 4.5 on 18 px).
    * 5 is the smallest value that still reads as a deliberate radius rather than an aliasing artefact at this
    * size, and it leaves the option, the row, the field and the scrollbar thumb on one shape.</p>
    */
   public static final float OPTION_RADIUS_MAX = 5.0F;

   public static float optionRadius(float width, float height) {
      return Math.max(0.0F, Math.min(Math.min(width, height) * 0.5F, OPTION_RADIUS_MAX));
   }

   /**
    * One option surface: the menu's material, one accent ring, and nothing else.
    *
    * <p>The three passes, in the order the tile is readable:</p>
    * <ol>
    *   <li>the shared glass material inside the silhouette (the ClickGUI's capture, so the option frosts the
    *       picture behind it exactly like the menu's pills do),</li>
    *   <li>the menu veil <em>and</em> the cursor light in one primitive call, so the light is clipped by the
    *       fill's own rounded silhouette,</li>
    *   <li>one uniform accent hairline along the same outline.</li>
    * </ol>
    *
    * <p>WHY the ring is a separate {@link GlassRenderer#drawRoundedOutline} and not the primitive's own
    * {@code borderArgb}: the primitive splits its border into a lifted top and a dimmed bottom
    * ({@link UiTheme#glassTopBorderArgb}/{@link UiTheme#glassBottomBorderArgb}), i.e. two hairlines of two
    * weights. This surface is allowed exactly one.</p>
    *
    * <p>WHY {@code topHighlightAlpha} and {@code innerBottomAlpha} are both {@code 0.0F}: they are the inner
    * catchlight and the inner bottom glow, and they are what the user is pointing at on the Options screen -
    * a light line hugging the top edge and another hugging the bottom one. With both at zero the shader emits
    * neither, whatever the hover, press or opacity state is.</p>
     *
     * <p>WHY the material pane is asked for {@code 0, 0} fresnel and sheen ({@link
     * GlassRenderer#drawGlassMaterialPane(float, float, float, float, float, int, float, float)}): the liquid-glass
     * shader carries a top bevel/sheen band and a Fresnel rim of its own, driven by uniforms independent of the two
     * primitive weights above. Left at their ClickGUI values they put a lit bevel just inside this surface's single
     * ring - the same "line hugging the top edge" from a second source - so this material keeps the blur, the tint
     * and the level coverage and drops both edge terms. One ring, one edge, on every option the client draws.</p>
    *
    * <p>Hover changes two things and only two: the cursor light becomes visible and the ring goes from alpha
    * {@link #EDGE_ALPHA} to {@link #EDGE_HOVER_ALPHA}. The caller draws its own label and it must not react.</p>
    *
    * @param radius     already resolved by {@link #optionRadius} (callers pass 0 for square corners)
    * @param enabled    a disabled option keeps its own muted plate and is deliberately not a live surface
    */
   public static void drawOptionSurface(
      float x,
      float y,
      float width,
      float height,
      float radius,
      float hoverT,
      float pressT,
      boolean enabled,
      float mouseX,
      float mouseY,
      int accentArgb
   ) {
      if (width <= 0.0F || height <= 0.0F) {
         return;
      }
      float h = UiAnimation.clamp01(hoverT);
      float p = UiAnimation.clamp01(pressT);
      float r = Math.max(0.0F, Math.min(radius, Math.min(width, height) * 0.5F));

      if (enabled) {
         GlassRenderer.drawGlassMaterialPane(x, y, width, height, r, UiTheme.LEVEL_CONTROL_RAISED, 0.0F, 0.0F);
      }

      float press = 1.0F - PRESS_DARKEN * p;
      int dynamicTop = fillTop();
      int dynamicBottom = fillBottom();
      int top = enabled ? UiTheme.scaleAlpha(dynamicTop, VEIL * press) : UiTheme.glassDisabledFillArgb();
      int bottom = enabled ? UiTheme.scaleAlpha(dynamicBottom, VEIL * press) : top;

      float lightX = Math.max(x + width * 0.15F, Math.min(x + width * 0.85F, mouseX));
      float lightY = Math.max(y + height * 0.25F, Math.min(y + height * 0.75F, mouseY));
      int edgeRgb = UiTheme.lerpArgb(accentArgb, dynamicTop, EDGE_MIX);
      int edgeAlpha = Math.round((float)EDGE_ALPHA + (float)(EDGE_HOVER_ALPHA - EDGE_ALPHA) * h);
      int borderArgb = enabled ? UiTheme.withAlpha(edgeRgb, edgeAlpha) : UiTheme.scaleAlpha(UiTheme.GLASS_BORDER, 0.55F);

      GlassRenderer.drawMenuGlassBox(
         x,
         y,
         width,
         height,
         r,
         top,
         bottom,
         borderArgb,
         UiTheme.BORDER_THICKNESS,
         lightX,
         lightY,
         enabled ? LIGHT_PEAK * h : 0.0F,
         Math.max(width, height) * LIGHT_RADIUS,
         UiTheme.lerpArgb(accentArgb, 0xFFFFFFFF, LIGHT_LIFT)
      );
   }

   // ---------------------------------------------------------------------------------------------
   // List chrome: the selection read and the recessed well
   // ---------------------------------------------------------------------------------------------

   /** Ring weight of a selected row: {@code AccountEntry}'s own 1.5, so a list inside a page matches it. */
   public static final float SELECTION_RING_THICKNESS = 1.5F;
   /** Row height below which a selected row's accent rail is skipped (it would read as a dot, not a rail). */
   private static final float RAIL_MIN_ROW_HEIGHT = 14.0F;

   /**
    * Marks a row as selected the way this client already marks one: <b>one</b> accent ring and the accent rail,
    * and nothing else.
    *
    * <p>WHY no fill and no second line. The stock selection read is two nested rectangles - a grey plate and a
    * black one inside it - which draws two horizontal rules across the row and is exactly the kind of built-in
    * edge the user keeps reporting as "lineas" in the lists. The client's own account list already answered
    * this question ({@code AccountEntry.drawEntry}: ring + rail, see also {@code ClientMenuTab.menuAccentRail}),
    * so a stock list and a client list now say "selected" the same way. A selection is not a darker plate; it
    * is the accent outline plus the rail marker.</p>
    *
    * <p>The ring is the accent at full alpha ({@link UiTheme#withAlpha(int, int)} 255), never grey, and it is
    * drawn at the row's own outline - the stock rows themselves draw 2 px inside it, so the ring reads as the
    * row's frame rather than as a line over its content.</p>
    *
    * @param radius caller-resolved; pass {@link #optionRadius(float, float)} for a list row
    */
   public static void drawSelectionRing(float x, float y, float width, float height, float radius) {
      if (width <= 0.0F || height <= 0.0F) {
         return;
      }
      float r = Math.max(0.0F, Math.min(radius, Math.min(width, height) * 0.5F));
      int accent = UiTheme.accentColor();
      GlassRenderer.drawRoundedOutline(x, y, width, height, r, SELECTION_RING_THICKNESS, UiTheme.withAlpha(accent, 255));
      if (height >= RAIL_MIN_ROW_HEIGHT) {
         // The same rail the client's own pages use to mark "you are here"; 4 px in, vertically centred by
         // menuAccentRail itself. Skipped on a short row, where 62 % of the height would be a speck.
         ClientMenuTab.menuAccentRail(x + 4.0F, y, height, 0.95F);
      }
   }

   /**
    * The menu's recessed well: a text field's interior, a list's scroll rail, a slider's groove.
    *
    * <p>Same three passes as every other surface in this skin, in the same order: the shared glass material
    * inside the silhouette ({@link UiTheme#LEVEL_WELL}, a deeper recess than a card), then the well level's own
    * veil with its bottom pulled towards {@link UiTheme#glassRecessedDepthRgb()} - the recipe
    * {@code GlassRenderer.drawRecessedWell} uses, minus its {@code 0.04 + f * 0.10} top catchlight, which this
    * round removes from every surface - and finally <b>one</b> ring: the accent at full alpha when the control
    * holds the keyboard, the resting glass hairline otherwise. Both inner-line weights of the primitive are
    * passed as zero, so a well can never grow the lit top edge or the inner bottom glow.</p>
    *
    * @param focusT 0 at rest, 1 when the control is focused (the ring's only state change)
    */
   public static void drawWellSurface(float x, float y, float width, float height, float radius, float focusT, int accentArgb) {
      drawWellSurface(x, y, width, height, radius, 0.0F, focusT, -9999.0F, -9999.0F, accentArgb);
   }

   public static void drawWellSurface(
      float x,
      float y,
      float width,
      float height,
      float radius,
      float hoverT,
      float focusT,
      float mouseX,
      float mouseY,
      int accentArgb
   ) {
      if (width <= 0.0F || height <= 0.0F) {
         return;
      }
      float h = UiAnimation.clamp01(hoverT);
      float f = UiAnimation.clamp01(focusT);
      float r = Math.max(0.0F, Math.min(radius, Math.min(width, height) * 0.5F));
      GlassRenderer.drawGlassMaterialPane(x, y, width, height, r, UiTheme.LEVEL_WELL, 0.0F, 0.0F);

      int top = UiTheme.glassRecessedFillArgb(h * 0.45F, f, accentArgb);
      int bottom = UiTheme.lerpArgb(top, UiTheme.glassRecessedDepthRgb(), 0.25F);
      int resting = UiTheme.scaleAlpha(UiTheme.GLASS_BORDER_STRONG, 0.72F);
      int hoverRing = UiTheme.withAlpha(UiTheme.lerpArgb(accentArgb, fillTop(), 0.42F), 210);
      int border = UiTheme.lerpArgb(UiTheme.lerpArgb(resting, hoverRing, h), UiTheme.withAlpha(accentArgb, 255), f);

      float lightX = Float.isNaN(mouseX) || mouseX < -9000.0F ? x + width * 0.5F : Math.max(x + 6.0F, Math.min(x + width - 6.0F, mouseX));
      float lightY = Float.isNaN(mouseY) || mouseY < -9000.0F ? y + height * 0.5F : Math.max(y + 4.0F, Math.min(y + height - 4.0F, mouseY));

      GlassRenderer.drawMenuGlassBox(
         x,
         y,
         width,
         height,
         r,
         top,
         bottom,
         border,
         UiTheme.BORDER_THICKNESS,
         lightX,
         lightY,
         h * 0.14F + f * 0.06F,
         Math.max(width, height) * 0.45F,
         UiTheme.lerpArgb(accentArgb, 0xFFFFFFFF, LIGHT_LIFT)
      );
   }

   // ---------------------------------------------------------------------------------------------
   // Slider thumb
   // ---------------------------------------------------------------------------------------------

   /**
    * The stock sliders' 8x20 handle, painted with the ClickGUI slider's active fill instead of the old flat
    * bar ({@code darkenColor(clientColor, -0.3)}), which belonged to no surface in the current system.
    *
    * <p>Colours are {@code ModernSlider}'s own: the same violet the ClickGUI paints along the filled part of
    * its rail, at the same alphas - so the stock slider and the ClickGUI slider are the same material while
    * the knob stays the vertical bar the 20 px row has always had (this is a drawing change, not a hitbox
    * change: the handle still occupies the same 8x20 rectangle).</p>
    *
    * @param rounded whether {@code ClientSettings.roundedButtons} is on; the square variant keeps the stock
    *                filled rectangle, so the setting still means what it says
    */
   public static void drawGlassContainer(float x, float y, float width, float height, float radius) {
      if (width <= 2.0F || height <= 2.0F) {
         return;
      }
      float r = Math.max(0.0F, Math.min(radius, Math.min(width, height) * 0.5F));
      GlassRenderer.drawSoftShadow(x, y, width, height, r, 14.0F, 4.0F, 0x52030408);
      GlassRenderer.drawMenuGlassBox(
         x,
         y,
         width,
         height,
         r,
         UiTheme.menuContainerTopArgb(),
         UiTheme.menuContainerBottomArgb(),
         UiTheme.GLASS_BORDER_STRONG,
         UiTheme.BORDER_THICKNESS,
         x + width * 0.5F,
         y + 6.0F,
         0.06F,
         Math.max(width, height) * 0.65F,
         UiTheme.accentSoftGlow()
      );
   }

   public static void drawSliderTrack(float x, float y, float width, float height, float progress, boolean rounded, int accentArgb) {
      if (width <= 8.0F || height <= 6.0F) {
         return;
      }
      float p = UiAnimation.clamp01(progress);
      float r = rounded ? optionRadius(width, height) : 0.0F;
      float inset = 5.0F;
      float trackW = Math.max(4.0F, width - inset * 2.0F);
      float fillW = trackW * p;

      if (fillW > 2.0F) {
         GlassRenderer.drawRoundedBox(
            x + 1.0F,
            y + 1.0F,
            Math.max(4.0F, (width - 2.0F) * p),
            height - 2.0F,
            Math.max(0.0F, r - 1.0F),
            UiTheme.withAlpha(accentArgb, 34),
            0,
            0.0F
         );
      }

      float railH = 2.5F;
      float railY = y + height - railH - 2.5F;
      GlassRenderer.drawRoundedBox(x + inset, railY, trackW, railH, railH * 0.5F, UiTheme.withAlpha(0xFF000000, 90), 0, 0.0F);
      if (fillW > 1.0F) {
         int leftCol = UiTheme.lerpArgb(accentArgb, UiTheme.accentSoftGlow(), 0.15F);
         int rightCol = UiTheme.lerpArgb(accentArgb, 0xFFFFFFFF, 0.28F);
         GlassRenderer.drawRoundedGradientBoxHorizontal(x + inset, railY, Math.max(railH, fillW), railH, railH * 0.5F, leftCol, rightCol, 1.0F);
      }

      float knobX = x + inset + fillW;
      float knobY = railY + railH * 0.5F;
      GlassRenderer.drawCircle(knobX, knobY, 4.2F, UiTheme.withAlpha(accentArgb, 95));
      GlassRenderer.drawCircle(knobX, knobY, 2.5F, 0xFFF8FAFC);
   }

   public static void drawSliderKnob(float x, float y, float width, float height, boolean rounded, int accentArgb) {
      if (width <= 0.0F || height <= 0.0F) {
         return;
      }
      int fillLeft = UiTheme.lerpArgb(accentArgb, UiTheme.accentSoftGlow(), 0.12F);
      int fillRight = UiTheme.withAlpha(UiTheme.accentSoftGlow(), 226);
      float r = rounded ? optionRadius(width, height) : 0.0F;
      // No ring on the handle, and both inner-line weights at zero: the shaft it sits on already carries the
      // option's single accent ring, and a 8 px bar with a lit top edge would read as a seam on the button.
      GlassRenderer.drawRoundedGradientBox(
         x, y, width, height, r, UiTheme.withAlpha(fillLeft, 214), fillRight, 0, 0.0F, 0.0F
      );
   }
}
