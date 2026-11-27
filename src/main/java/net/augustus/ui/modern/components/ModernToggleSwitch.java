package net.augustus.ui.modern.components;

import net.augustus.clickgui.ClickGuiScreen;
import net.augustus.font.FontManager;
import net.augustus.render.RenderUtil;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;

/**
 * Reusable Liquid Glass toggle switch pill with smooth animated thumb and visible crisp border.
 */
public class ModernToggleSwitch {
   private float stateAnim = 0.0F;
   private float hoverAnim = 0.0F;
   private float pressAnim = 0.0F;
   private boolean enabled = true;
   private boolean initialized = false;

   private float lastX;
   private float lastY;
   private float lastW = 36.0F;
   private float lastH = 19.0F;

   /**
    * Applies the ClickGUI open/close envelope to one ARGB at the point of draw.
    *
    * <p>WHY per colour: the envelope cannot ride on GL state ({@code GlassRenderer} resets the GL colour per
    * quad, the font renderer per string), so the track, the knob and the shadow each carry the value
    * themselves. Only alpha moves - this is a fade, not a resize.
    *
    * <p>Each colour is wrapped exactly once, at the draw call, so nothing is faded twice.
    */
   private static int faded(int argb) {
      // Settled overlay (everything except the ~288 ms open/close transition): skip the arithmetic.
      return ClickGuiScreen.getScreenFade() >= 0.999F ? argb : ClickGuiScreen.fadeArgb(argb);
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public void render(float x, float y, float width, float height, boolean checked, int mouseX, int mouseY) {
      if (!this.initialized) {
         this.stateAnim = checked ? 1.0F : 0.0F;
         this.initialized = true;
      }

      this.lastX = x;
      this.lastY = y;
      this.lastW = width;
      this.lastH = height;

      boolean hovered = this.enabled && RenderUtil.isHovered(x - 2.0F, y - 2.0F, width + 4.0F, height + 4.0F, mouseX, mouseY);
      boolean pressed = hovered && UiAnimation.isPrimaryMouseDown();
      this.stateAnim = UiTheme.smoothState(this.stateAnim, checked ? 1.0F : 0.0F);
      this.hoverAnim = UiTheme.smoothState(this.hoverAnim, hovered ? 1.0F : 0.0F);
      this.pressAnim = UiTheme.smoothPress(this.pressAnim, pressed);

      int accent = UiTheme.accentColor();
      float radius = height * 0.5F;

      if (this.enabled && (this.stateAnim > 0.04F || this.hoverAnim > 0.04F)) {
         int glowAlpha = Math.round((this.stateAnim * 62.0F + this.hoverAnim * 26.0F));
         GlassRenderer.drawSoftShadow(x, y, width, height, radius, 8.0F, 1.0F, faded(UiTheme.withAlpha(accent, glowAlpha)));
      }

      // Track fill: the same raised level veil as a button when OFF, crossfaded towards the vibrant
      // indigo-violet "on" gradient. Both ends go through the level transfer, so the track shows the
      // card's glass through it instead of covering it with an opaque pill.
      int offFill = UiTheme.glassControlVeilArgb(UiTheme.LEVEL_CONTROL_RAISED, this.hoverAnim, 0.0F, accent);
      int onFillTop = UiTheme.glassControlTintArgb(
         UiTheme.LEVEL_CONTROL_RAISED, this.hoverAnim, this.stateAnim, UiTheme.lerpArgb(accent, 0xFFEAE5FF, 0.12F + 0.10F * this.hoverAnim), 0.90F
      );
      int onFillBottom = UiTheme.glassControlTintArgb(
         UiTheme.LEVEL_CONTROL_RAISED, this.hoverAnim, this.stateAnim, UiTheme.lerpArgb(UiTheme.accentDeep(), 0xFF100E24, 0.25F), 0.90F
      );
      int topFill = UiTheme.lerpArgb(offFill, onFillTop, this.stateAnim);
      int bottomFill = UiTheme.lerpArgb(UiTheme.lerpArgb(offFill, UiTheme.glassPressRgb(), 0.25F), onFillBottom, this.stateAnim);

      int borderOff = UiTheme.lerpArgb(UiTheme.GLASS_BORDER, UiTheme.withAlpha(UiTheme.accentSoftGlow(), 175), this.hoverAnim * 0.65F);
      int borderArgb = UiTheme.lerpArgb(borderOff, UiTheme.withAlpha(0xFFEAE5FF, 190), this.stateAnim);

      GlassRenderer.drawInteractiveSurface(
         x,
         y,
         width,
         height,
         radius,
         faded(topFill),
         faded(bottomFill),
         faded(borderArgb),
         UiTheme.BORDER_THICKNESS,
         UiTheme.glassTopHighlightAlpha(Math.max(this.hoverAnim, this.stateAnim * 0.6F)),
         this.hoverAnim,
         this.pressAnim,
         this.enabled,
         (float)mouseX,
         (float)mouseY,
         faded(accent)
      );

      // Smooth sliding pill knob with discrete travel stretch + hover/press micro-physics.
      //
      // Measured off the target: inside a 42.4 x 23.8 track the knob is a 19.9 circle sitting 2.4 in from
      // the track's edges, so the pad is 2.4 rather than the 3.0 that used to make the knob look thin in
      // a wide track.
      float pad = 2.4F;
      float knobDiameter = Math.max(8.0F, height - pad * 2.0F);
      float easedState = UiAnimation.easeOutCubic(this.stateAnim);
      float travelStretch = 4.0F * easedState * (1.0F - easedState) * 2.4F + this.pressAnim * 1.6F;
      float knobW = knobDiameter + travelStretch;
      float knobH = knobDiameter + this.hoverAnim * 0.5F - this.pressAnim * 0.4F;
      float minCx = x + pad + knobDiameter * 0.5F;
      float maxCx = x + width - pad - knobDiameter * 0.5F;
      float cx = minCx + (maxCx - minCx) * easedState;
      float cy = y + height * 0.5F;
      float kx = cx - knobW * 0.5F;
      float ky = cy - knobH * 0.5F;
      float kr = Math.min(knobW, knobH) * 0.5F;

      if (this.enabled) {
         GlassRenderer.drawSoftShadow(kx, ky, knobW, knobH, kr, 4.5F, 1.0F, faded(0x52000000));
      }
      // Knob: the one control that stays optically solid, on purpose. A translucent knob over a dark
      // "on" track loses the read of which side the switch is on, so this keeps the near-opaque white
      // pill — but it is painted over the track's glass material (drawInteractiveSurface above), so the
      // card's backdrop still shows around it and the knob gets the same rim treatment as everything
      // else instead of a hand-written dark outline.
      int activeKnob = UiTheme.lerpArgb(0xFFE2E6F4, 0xFFFFFFFF, Math.max(this.stateAnim, this.hoverAnim));
      int knobFill = this.enabled ? activeKnob : UiTheme.withAlpha(0xFF9AA2BA, 165);
      int knobBorder = this.enabled
         ? UiTheme.withAlpha(0xFF5A6076, 90)
         : UiTheme.withAlpha(0xFF000000, 58);
      GlassRenderer.drawRoundedBox(kx, ky, knobW, knobH, kr, faded(knobFill), faded(knobBorder), 0.75F);
   }

   public void drawLabeledRow(float x, float y, float width, String label, boolean checked, int mouseX, int mouseY) {
      FontManager.interMedium.drawVerticallyCenteredString(label, (double)x, (double)y, 24.0, faded(UiTheme.primaryTextColor()));
      float switchW = 36.0F;
      float switchH = 18.0F;
      this.render(x + width - switchW, y + (24.0F - switchH) * 0.5F, switchW, switchH, checked, mouseX, mouseY);
   }

   public boolean isHovered(int mouseX, int mouseY) {
      return RenderUtil.isHovered(this.lastX - 3.0F, this.lastY - 3.0F, this.lastW + 6.0F, this.lastH + 6.0F, mouseX, mouseY);
   }

   public float getX() {
      return this.lastX;
   }

   public float getY() {
      return this.lastY;
   }

   public float getWidth() {
      return this.lastW;
   }

   public float getHeight() {
      return this.lastH;
   }
}
