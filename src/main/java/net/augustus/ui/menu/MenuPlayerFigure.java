package net.augustus.ui.menu;

import java.nio.IntBuffer;
import net.augustus.ui.AugustusGuiScreen;
import net.augustus.utils.interfaces.MC;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * The local player rendered as a real 3D, self-animating Minecraft player model inside the main menu.
 *
 * <p>WHY this does not go through {@code RenderPlayer} / {@code RendererLivingEntity} / {@code RenderManager}:
 * on the main menu {@code Minecraft.theWorld} is null and {@code Minecraft.thePlayer} is usually null too, and
 * every one of those routes is world- or entity-bound. {@code RenderPlayer.doRender} needs an
 * {@code AbstractClientPlayer} (whose constructor dereferences a {@code World} and starts cape/profile
 * downloads), {@code RendererLivingEntity.doRender} reads {@code entity.isRiding()}, {@code isChild()},
 * {@code getSwingProgress()}, {@code limbSwing}, {@code renderYawOffset} and the hurt/death timers, and it runs
 * the chams / emissive-texture / brightness passes on the way. Reaching any of that from the menu means
 * fabricating a world and an entity every frame - which is exactly where a menu starts crashing.
 *
 * <p>So this class owns the smallest possible slice of that pipeline instead: a plain {@link ModelPlayer}
 * (its constructor only builds {@code ModelRenderer} boxes - no world, no entity, no client managers), the
 * skin bound straight through the {@link TextureManager}, and its own GUI transform. The model parts are
 * rendered directly rather than through {@link ModelPlayer#render}, because that override dereferences its
 * entity argument ({@code entity.isSneaking()}) and can hand the draw off to {@code ObjModelLoader}; both are
 * incorrect in a GUI and the first one throws when the entity is null.
 *
 * <p>{@link ModelPlayer#setRotationAngles} accepts that null entity safely: the {@code ModelBiped}
 * implementation only uses its float arguments, so the pose is fully driven by numbers here.
 *
 * <p>What is drawn is a <b>bust</b>, not a whole player. The legs and both leg wear layers are never
 * rendered, so the lowest thing on screen is the torso, and the bottom {@link #DISSOLVE_FRACTION} of the
 * figure - the torso, and with it the arms from just under the shoulder down - is faded out in horizontal
 * scissor bands until it reaches zero at the waist. The head and the strip of shoulder above that ramp are
 * drawn opaque, in one pass, so the face never fades.
 *
 * <p>The ramp is not a plain opacity fade: each band is drawn {@link #FEATHER_TAPS} times, the taps displaced
 * sideways by one to three device pixels, so the outline of the torso is genuinely spread into a soft edge
 * instead of just becoming see-through. The taps are weighted so the body's own coverage still lands exactly
 * on the band's alpha (see {@link #renderBust}): the feather changes the edge, not the fade.
 *
 * <p>The fade is plain alpha blending in scissor bands: no framebuffer, no shader and no blur engine. A
 * framebuffer pass on this screen has frozen the client's driver twice, so the whole effect stays on the
 * fixed-function path the menu already renders with.
 *
 * <p>Nothing in this class touches game state: no ticking, no events, no screen switching. It only reads
 * {@code thePlayer}'s already-downloaded skin and writes GL state, which it restores.
 */
public final class MenuPlayerFigure implements MC {
   /** The stock skin required as the fallback; shipped in {@code assets/minecraft/textures/entity/steve.png}. */
   private static final ResourceLocation FALLBACK_SKIN = new ResourceLocation("textures/entity/steve.png");

   /**
    * Model-space extent of a player figure. {@code ModelBiped} puts the top of the head at y=-8 and hangs both
    * legs from y=12, each 12 units tall, so the soles sit at y=24: 32 model pixels head-to-toe.
    *
    * <p>Those are <b>pixels</b>, not blocks: every {@code ModelRenderer.render} call takes vanilla's
    * {@link #MODEL_SCALE} and multiplies the box coordinates by it, so the drawn extent is
    * {@code 32 * 0.0625 = 2.0} units. The caller's {@code height} therefore divides 2.0, not 32 - scaling by
    * {@code height / 32} on top of the 0.0625 render scale would draw a figure 16 times too small to see.
    */
   private static final float MODEL_PIXELS_HEIGHT = 32.0F;
   /** On-screen extent of the model in the units the transform below actually works in. */
   private static final float MODEL_HEIGHT = MODEL_PIXELS_HEIGHT * 0.0625F;
   /** Same conversion for the soles, which is where {@link #draw}'s {@code feetY} anchors. */
   private static final float MODEL_FEET_Y = 24.0F * 0.0625F;
   /** Vanilla's model unit scale (1/16); every {@code ModelRenderer.render} call takes it. */
   private static final float MODEL_SCALE = 0.0625F;

   /**
    * Model-space y of the waist: the bottom of {@code bipedBody} / {@code bipedBodyWear}, the top of both
    * legs, and at rest the bottom of both arms and their wear layers ({@code ModelBiped} lines 30, 33, 40;
    * {@code ModelPlayer} line 61).
    *
    * <p>It is this class's lower edge, because those boxes are the whole visible bust: nothing drawn here
    * reaches below y=12, and everything below it is the legs, which are no longer drawn at all.
    */
   private static final float MODEL_TORSO_BOTTOM_Y = 12.0F * MODEL_SCALE;

   /**
    * Where the bust ends, as a fraction of the caller's {@code height} above {@code feetY}: the waist, 12 of
    * the model's 32 pixels above the soles.
    *
    * <p>{@code feetY} stays the sole line of the whole player - the menu lays the figure out with it and the
    * head keeps the size and position it was measured at - so the 0.375 * height between the waist and
    * {@code feetY} is simply empty space, and the caller's contact shadow belongs at this line instead.
    */
   private static final float BUST_BOTTOM_FRACTION = (MODEL_FEET_Y - MODEL_TORSO_BOTTOM_Y) / MODEL_HEIGHT;

   /**
    * Length of the dissolve ramp as a fraction of {@code height}: 0.35, which is the torso.
    *
    * <p>The torso is exactly {@link #BUST_BOTTOM_FRACTION} = 0.375 of the figure tall, so this makes the ramp
    * the torso itself - alpha is still 1 at the shoulders and reaches 0 at the waist, which is what "the model
    * starts at the torso and fades away from there" looks like on screen. It leaves the head (the top 0.25)
    * and the strip of shoulder above the ramp fully opaque, so the face never fades.
    */
   private static final float DISSOLVE_FRACTION = 0.35F;

   /** GUI y of the top of the ramp, as a fraction of {@code height} above {@code feetY}: 0.375 + 0.35. */
   private static final float DISSOLVE_TOP_FRACTION = BUST_BOTTOM_FRACTION + DISSOLVE_FRACTION;

   /**
    * Horizontal scissor bands the ramp is drawn in.
    *
    * <p>A band carries one constant alpha, so the ramp is a staircase whose step is the ramp's own slope times
    * the band pitch, and the pitch is the only thing this number decides: the loop below takes the band count
    * as {@code min(DISSOLVE_BANDS, rampRows)}. The ramp is {@link #DISSOLVE_FRACTION} of {@code height} tall,
    * about 40 GUI px at the height this screen uses, so the numbers to beat are small.
    *
    * <p>Twelve bands put a boundary every 3.4 GUI px and a step of 0.083 in alpha. Twenty-four halved both, and
    * an independent measurement of what was left found plateaus of three identical device rows cut by
    * single-row jumps of up to 26 levels through the shirt - on boundary rows that were identical across five
    * separate captures, so fixed in screen space and therefore the ramp, not the skin. The ramp's own texel
    * noise is 6.7 device px per texel, which leaves the eye nothing at that frequency to hide behind.
    *
    * <p>So the ceiling is now high enough that the {@code min} always binds to {@code rampRows}, which puts one
    * band on every row - the finest a per-band constant alpha can be. Ninety-six rather than forty so that a
    * larger GUI scale, which lengthens {@code rampRows} proportionally, still lands under one device row per
    * band up to roughly a 2.5x scale. {@link #DISSOLVE_EXPONENT} does the rest: an exponent above one pulls the
    * absolute step at the faint end down faster than the relative one grows, and the faint end is where a step
    * is most visible. Cost stays bounded by the tap rule below rather than by this count, so a band that is
    * barely there is still drawn, and only cheaply.
    */
   private static final int DISSOLVE_BANDS = 96;

   /**
    * Shape of the ramp: {@code alpha = (1 - t) ^ DISSOLVE_EXPONENT} from the shoulders down to the waist.
    *
    * <p>WHY not the straight line the bands started with. A linear ramp spends half its length above alpha 0.5,
    * which puts the worn, mostly-transparent part of the torso right where a band boundary is easiest to see -
    * the low-alpha end. An exponent above one gives the torso away sooner and leaves a long, very faint tail:
    * the silhouette is gone by roughly two thirds of the ramp, and what is left below it is a wash thin enough
    * that its steps are under 3/255 and cannot be picked out at all. It also reads better against the backdrop,
    * because a bust that thins out and drifts down towards the option column is what the mockup draws, while a
    * bust that holds half its opacity until the last band and then stops is what a hard-edged cut-out looks
    * like.
    */
   private static final float DISSOLVE_EXPONENT = 1.5F;

   /**
    * Horizontal taps each ramp band is drawn with, and the spacing between them in device pixels.
    *
    * <p>Four taps at two device pixels apart put the silhouette's copies at +-1 and +-3 device px, so the
    * outline of the torso is spread over six device px - at GUI scale 2 that is three GUI px on a hundred-GUI-px
    * figure, which reads as a soft edge next to the crisp head still drawn above it.
    *
    * <p>Why horizontal only: the ramp starts just below the shoulders and ends at the waist, so every outline
    * still visible inside it is a vertical one - the sides of the torso and the arms. A vertical tap component
    * would be the wrong tool twice over: the band clipping would cut the up/down taps off asymmetrically and
    * print a dim row at every band boundary, and the vertical gradient is already the bands' own job.
    *
    * <p>Why four: fewer gives too coarse an edge. With N taps at alpha a the coverage of a pixel covered by k
    * of them is {@code 1 - (1 - a)^k}, so N taps produce N + 1 outline levels; three levels spread over a
    * couple of pixels reads as a ghost copy, four or more reads as a gradient.
    */
   private static final int FEATHER_TAPS = 4;

   /**
    * Taps a band gets once its own alpha has fallen below {@link #FEATHER_FULL_ALPHA}, and that threshold.
    *
    * <p>Two taps put the silhouette's copies at +-1 device px, which is four levels of outline instead of six -
    * a shallower feather, which on a band that is already this faint is indistinguishable from the full one
    * while costing half the draws. The threshold is set where the feather stops being a soft edge and starts
    * being a ghost: above about an eighth of an alpha the extra taps are still visible work.
    */
   private static final int FEATHER_TAPS_FAINT = 2;
   private static final float FEATHER_FULL_ALPHA = 0.13F;

   /** Spacing between feather taps, in device pixels: the blur radius must not change with the GUI scale. */
   private static final float FEATHER_STEP_DEVICE_PX = 2.0F;

   /**
    * Per-tap alpha under which a band is not worth feathering, and is drawn once at its own alpha instead.
    *
    * <p>A tap's fragments carry its alpha times the texel's, so a tap whose alpha has fallen to the alpha test
    * reference is discarded rather than blended and the band's outline would come out jagged in a way the
    * intact bands above it are not. The bands that hit this are the last of the ramp, at 5% coverage or less,
    * where a single hard-edged pass cannot be told apart from a feathered one - and drawing them once keeps
    * their alphas exact, which is what makes the tail land softly.
    */
   private static final float FEATHER_MIN_TAP_ALPHA = 0.02F;

   /**
    * Alpha test comparison the bust's own (unbanded) pixels are drawn under: the threshold the client's entity
    * rendering uses, and the one the {@link #draw} entry guard is keyed to.
    *
    * <p>It has to be that same value in both places: a fragment is discarded below it, so a figure faded in
    * under it would be invisible while the ramp bands, drawn under the weaker
    * {@link #FADE_ALPHA_REFERENCE}, would still show - a torso with no head. The guard therefore waits for the
    * caller's opacity to clear this reference before drawing anything at all.
    */
   private static final float ALPHA_TEST_REFERENCE = 0.1F;

   /**
    * Weakest band still worth drawing.
    *
    * <p>0.02 of the figure's own colour over the backdrop is invisible, so finishing the ramp there costs
    * nothing; the per-tap rule in {@link #renderBust} keeps every pass of the bands above it clear of the alpha
    * test reference. The floor here is 0.012 rather than that 0.02 because {@link #DISSOLVE_EXPONENT} thins the
    * tail out much faster: stopping at 0.02 would cut the ramp at 93 % of its length and leave the bust hanging
    * above the option column instead of reaching it, which is the one thing this round was asked to fix. It has
    * to stay clear of {@link #FADE_ALPHA_REFERENCE} - a band under the test reference is culled, not faded.
    */
   private static final float MIN_BAND_ALPHA = 0.012F;

   /**
    * Alpha test comparison used while the ramp bands are drawn; vanilla's 0.1 is used everywhere else.
    *
    * <p>A fragment's alpha is the pass's alpha times the texel's, so under a 0.1 reference the ramp would not
    * fade but <i>stop</i>: every band from 0.1 down would be discarded whole and leave a hard edge over the
    * backdrop where the backdrop has to show through. 0.01 is still above zero, so a fully transparent texel -
    * what the outer layer is outside the sleeves, and the one thing that must not write depth - is still
    * culled, and it sits two times below {@link #FEATHER_MIN_TAP_ALPHA}, the faintest pass the ramp ever draws,
    * so no pass is discarded instead of blended.
    */
   private static final float FADE_ALPHA_REFERENCE = 0.01F;

   /**
    * How far the bust can project from its centre, in model pixels, whatever its yaw.
    *
    * <p>The widest boxes are the arms and their wear layers, 8.25 model px off centre and 2.25 px deep, so the
    * projection stays inside {@code sqrt(8.25^2 + 2.25^2) = 8.55}. A scissor band only has to be wide enough
    * not to cut the figure, and 9 model px is 0.28 * height either side of the centre.
    */
   private static final float BUST_HALF_WIDTH_MODEL_PX = 9.0F;

   /**
    * How far above the model's own top the opaque band starts, as a factor of {@code height}.
    *
    * <p>The figure is drawn slightly taller than the layout: breathing scales it by up to 1.006 about the
    * soles (see {@link #BREATH_AMPLITUDE}), the head wear layer is a 0.5 model px inflated shell above the
    * head's own crown (1.6% of the height), and a 5 degree head pitch lifts it by about 1.2% more. 4% covers
    * all three, so the crown of the head is never clipped by the band it sits in.
    */
   private static final float OPAQUE_BAND_CLEARANCE = 1.04F;

   /** Breathing amplitude as a fraction of the figure's height, so it scales with the on-screen size. */
   private static final float BREATH_AMPLITUDE = 0.006F;

   /**
    * Base yaw of the bust, in degrees.
    *
    * <p>Vanilla turns a living entity by {@code 180 - renderYawOffset} ({@code RendererLivingEntity.rotateCorpse},
    * line 311), so 180 is the orientation of a player standing still: a {@code ModelBiped} faces -z and this
    * menu's camera is at +z looking down it, exactly like every other GL camera. Drawing with no base rotation
    * at all - what this class used to do - is what put the back of the player's head on the menu.
    */
   private static final float YAW_BASE = 180.0F;

   /**
    * Amplitude of the idle yaw drift around {@link #YAW_BASE}, in degrees.
    *
    * <p>A drift, not the continuous turn this class used to run: that went through a full 360 in 24 seconds
    * and so showed the player's back - and hid the face - for half of every cycle, which is the defect here.
    * Sixteen degrees plus the smaller {@link #YAW_DRIFT_WOBBLE} keeps the figure face-on and still moving.
    */
   private static final float YAW_DRIFT = 16.0F;

   /** Faster, smaller second term of the drift, so the turn does not read as a metronome. */
   private static final float YAW_DRIFT_WOBBLE = 3.0F;

   /**
    * Far end of the depth range the figure is drawn in.
    *
    * <p>A menu backdrop is a flat quad that writes the same depth everywhere, so drawing the model in the
    * normal range would put half of it behind the backdrop. Squeezing the model's depth into the near part of
    * the range keeps every part of the figure in front of the backdrop while still sorting the body parts
    * against each other, which is all the depth test is needed for here.
    */
   private static final double MODEL_DEPTH_FAR = 0.4;

   private static ModelPlayer playerModel;
   private static ResourceLocation cachedSkin;
   /** First-draw timestamp for the idle clock; {@code System.nanoTime} so it cannot be affected by the tick rate. */
   private static long startNanos;
   private static boolean ready;
   /**
    * Scratch for {@code GL_SCISSOR_BOX}, which the driver returns as four ints; kept so that drawing the
    * figure allocates nothing per frame.
    */
   private static final IntBuffer SCISSOR_BOX = BufferUtils.createIntBuffer(16);

   private MenuPlayerFigure() {
   }

   /**
    * Draws the bust with its horizontal centre on centerX; height is the on-screen height of a whole player in
    * GUI pixels and feetY is the line that player's soles would rest on.
    *
    * <p>Both are the layout contract the menu sizes the figure with, and nothing below feetY or above
    * {@code feetY - height} moved: the head is drawn exactly where it was measured. What the caller sees
    * instead is a bust - head, arms and torso, never the legs - whose bottom {@link #DISSOLVE_FRACTION} ramps
    * from opaque at {@link #dissolveTopY(float, float)} to nothing at {@link #bustBottomY(float, float)}. The
    * ramp covers the torso, so the figure dissolves where the torso starts rather than standing in the frame,
    * and it is feathered sideways ({@link #FEATHER_TAPS} taps, {@link #FEATHER_STEP_DEVICE_PX} apart) so the
    * body's outline blurs into the backdrop instead of only becoming transparent.
    *
    * <p>Every position this class works with is derived from these two arguments on the spot, so the bust, the
    * ramp and the feather all stay on the torso wherever the caller puts the figure.
    *
    * <p>The fade is alpha blending in horizontal scissor bands, nothing else: no framebuffer, no shader and no
    * blur engine is touched here, and the backdrop stays the caller's business.
    *
    * <p>Safe with a null world and a null player: it reads no game state beyond the player's already-loaded skin
    * location, and every failure path leaves the GUI exactly as it found it.
    *
    * <p>{@code partialTicks} is accepted for symmetry with the other menu drawing code but deliberately unused:
    * the animation is driven by {@link System#nanoTime()} so it stays smooth and frame-rate independent even
    * though the menu has no fixed tick the value would relate to.
    */
   public static void draw(float centerX, float feetY, float height, float partialTicks, float alpha) {
      // Under the alpha test reference nothing of the figure can be seen anyway (see ALPHA_TEST_REFERENCE), and
      // the fade bands are compared against a weaker one, so drawing there would show a torso with no head.
      if (alpha <= ALPHA_TEST_REFERENCE || height <= 0.0F) {
         return;
      }

      ModelPlayer model = model();
      if (mc == null) {
         return;
      }
      TextureManager textureManager = mc.getTextureManager();
      if (model == null || textureManager == null) {
         return;
      }

      ResourceLocation skin = skin(textureManager);
      if (skin == null) {
         return;
      }

      long now = System.nanoTime();
      if (startNanos == 0L) {
         startNanos = now;
      }

      // Kept in a double so the idle motion stays precise no matter how long the client has been open; the
      // final angles are narrowed to float only when they are handed to the model and GL.
      double seconds = (now - startNanos) / 1.0E9;
      if (seconds < 0.0) {
         seconds = 0.0;
      }

      // Snapshot everything this method changes. pushAttrib/popAttrib covers raw GL, but GlStateManager keeps
      // its own shadow copy of the state, so the values are also re-asserted through it afterwards to keep the
      // two in agreement for whatever draws next.
      boolean wasLighting = GlStateManager.isLightingEnabled();
      boolean wasDepth = GlStateManager.isDepthEnabled();
      boolean wasTexture2D = GlStateManager.isTexture2DEnabled();
      boolean wasCull = GlStateManager.isCullEnabled();
      boolean wasBlend = GlStateManager.isBlendEnabled();
      boolean wasAlphaTest = GlStateManager.isAlphaEnabled();
      int wasDepthFunc = GlStateManager.getDepthFunc();
      boolean wasDepthMask = GlStateManager.isDepthMaskEnabled();
      int wasAlphaFunc = GlStateManager.getAlphaFunc();
      int wasBlendSrc = GlStateManager.getBlendSrcFactor();
      int wasBlendDst = GlStateManager.getBlendDstFactor();
      int wasBlendSrcAlpha = GlStateManager.getBlendSrcFactorAlpha();
      int wasBlendDstAlpha = GlStateManager.getBlendDstFactorAlpha();
      int wasBoundTexture = GlStateManager.getBoundTexture();

      GlStateManager.pushAttrib();
      GlStateManager.pushMatrix();

      try {
         // Unlit and un-culled: the model is textured and lit flat, and a living-entity model is drawn with
         // culling off so no face is lost if a skin relies on the outer layer's open shells.
         GlStateManager.disableLighting();
         GlStateManager.disableCull();
         GlStateManager.enableTexture2D();
         // Depth testing is what makes the figure read as a solid body: a closed box draws its +z face after
         // its -z face, so without it the back of every cube would paint over the front. GL_LEQUAL compares
         // equal depths as visible (the backdrop sits at one flat depth), and the compressed range below keeps
         // the whole figure in front of that backdrop.
         GlStateManager.enableDepth();
         GlStateManager.depthFunc(GL11.GL_LEQUAL);
         GlStateManager.depthMask(true);
         GL11.glDepthRange(0.0, MODEL_DEPTH_FAR);
         GlStateManager.enableBlend();
         GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
         // Alpha testing discards the skin's fully transparent texels instead of letting them write depth:
         // the outer layer is an open shell, and a transparent sleeve that wrote depth would punch a hole in
         // whatever sits behind it. The comparison has to be set explicitly - it defaults to GL_ALWAYS, and
         // on a fresh start nothing has rendered a world yet to leave a stricter one behind. The reference is
         // ALPHA_TEST_REFERENCE, the same threshold the client's own entity renderer uses.
         GlStateManager.enableAlpha();
         GlStateManager.alphaFunc(GL11.GL_GREATER, ALPHA_TEST_REFERENCE);
         // The skin's alpha comes from the texture; this only scales the whole figure's opacity.
         GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);

         textureManager.bindTexture(skin);
         ready = true;

         // GUI space has y pointing down and the model's +y points down too (head at -8, soles at +24), so no
         // axis flip is needed. The yaw is a constant 180 base with the drift subtracted, which is vanilla's
         // composition: RendererLivingEntity.rotateCorpse applies rotate(180 - renderYawOffset) to a player, and
         // a bust has no world yaw, so the only term left is the drift. With no base at all the model's -z face
         // - the one the head's own skin block is painted on (ModelBox quad 4 takes the head's (8,8) block for
         // its -z face) - pointed away from the camera, which is the back of the head the menu used to show.
         // The drift is bounded, so unlike the old full turn the angle never needs reducing modulo a turn.
         float drift = (float)(Math.sin(seconds * 0.23) * YAW_DRIFT + Math.sin(seconds * 0.71) * YAW_DRIFT_WOBBLE);
         float yaw = YAW_BASE - drift;
         float breathing = (float)(1.0 + BREATH_AMPLITUDE * Math.sin(seconds * 1.1));
         float scale = height / MODEL_HEIGHT * breathing;

         // ageInTicks drives the vanilla idle sway; the head angles are in degrees.
         float ageInTicks = (float)(seconds * 20.0);
         float headYaw = (float)(Math.sin(seconds * 0.45) * 9.0);
         float headPitch = (float)(Math.sin(seconds * 0.31) * 3.0 - 2.0);
         model.setRotationAngles(0.0F, 0.0F, ageInTicks, headYaw, headPitch, MODEL_SCALE, null);

         // The built-in sway is very subtle; add a touch and re-sync the wear layer so the sleeves follow.
         float sway = (float)(Math.sin(seconds * 0.9) * 0.05);
         model.bipedRightArm.rotateAngleX += sway;
         model.bipedLeftArm.rotateAngleX -= sway;
         ModelBase.copyModelAngles(model.bipedRightArm, model.bipedRightArmwear);
         ModelBase.copyModelAngles(model.bipedLeftArm, model.bipedLeftArmwear);

         // The model transform itself is not set up here: every feather tap of the ramp moves the finished
         // figure sideways, so drawFigure applies it per draw instead.
         renderBust(model, centerX, feetY, height, alpha, yaw, scale);
      } catch (Throwable ignored) {
         // A menu decoration must never take the client down; the next frame simply tries again.
      } finally {
         GlStateManager.popMatrix();
         GlStateManager.popAttrib();

         // popAttrib restores raw GL but not GL_CURRENT_BIT, GL_DEPTH_BUFFER_BIT or GlStateManager's shadow
         // copy, so the colour, the depth range and every toggled capability are re-asserted here through
         // the wrapper.
         GL11.glDepthRange(0.0, 1.0);
         GlStateManager.depthFunc(wasDepthFunc);
         GlStateManager.depthMask(wasDepthMask);
         GlStateManager.tryBlendFuncSeparate(wasBlendSrc, wasBlendDst, wasBlendSrcAlpha, wasBlendDstAlpha);
         // glAlphaFunc rides in GL_COLOR_BUFFER_BIT, which GlStateManager's pushAttrib does not cover - it
         // saves the enable and lighting groups only, 8256 (GlStateManager line 251) - so the comparison is put
         // back by hand, and now unconditionally: the ramp pass lowers the reference to FADE_ALPHA_REFERENCE,
         // and leaving that cached would weaken the comparison for everything drawn after the figure.
         GlStateManager.alphaFunc(wasAlphaFunc, ALPHA_TEST_REFERENCE);
         reassert(wasLighting, wasDepth, wasTexture2D, wasCull, wasBlend, wasAlphaTest);
         if (wasBoundTexture != 0) {
            GlStateManager.bindTexture(wasBoundTexture);
         }
         GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
      }
   }

   /**
    * GUI y of the top of the dissolve ramp: the highest row drawn at less than full alpha.
    *
    * <p>Exposed for the caller, which paints its own frost ramp - this class draws no backdrop of its own.
    * Above this line the bust is opaque; below it the alpha falls linearly to zero at
    * {@link #bustBottomY(float, float)}, over {@link #DISSOLVE_FRACTION} * {@code height} of GUI pixels.
    */
   public static float dissolveTopY(float feetY, float height) {
      return feetY - DISSOLVE_TOP_FRACTION * height;
   }

   /**
    * GUI y of the lower edge of the bust: the waist, where the ramp reaches zero and the figure stops.
    *
    * <p>This is {@link #BUST_BOTTOM_FRACTION} * {@code height} above {@code feetY}, and it is the line the
    * caller's contact shadow belongs on: nothing is drawn below it, so a shadow left on {@code feetY} sits in
    * the empty strip under the bust.
    */
   public static float bustBottomY(float feetY, float height) {
      return feetY - BUST_BOTTOM_FRACTION * height;
   }

   /** True once a skin texture (real or fallback) is usable. */
   public static boolean isReady() {
      return ready;
   }

   /**
    * Draws the bust: one pass for everything above the ramp, then {@link #DISSOLVE_BANDS} horizontal scissor
    * bands for the ramp itself, each drawn {@link #FEATHER_TAPS} times to feather its outline.
    *
    * <p>The bands are what every draw shares, so a band at full alpha is still pixel-identical to a single
    * unbanded draw and the opaque part of the bust is unchanged; inside a band the model is still depth-sorted
    * against itself, exactly as the one-draw version was. What the bands must not do is overlap or leave a row
    * unclaimed, and they do neither: an overlapping row would be blended twice and show as a bright line, a
    * missed one as a transparent line straight across the figure. The clip below the waist is the framing
    * itself - it is why the legs, which are never drawn, cannot leave a solid stub of arm below the ramp
    * either, and it also keeps each band's feather taps from bleeding into its neighbours'.
    *
    * <p>The feather is a weighted multi-tap: the same torso drawn {@link #FEATHER_TAPS} times, each pass
    * displaced sideways by one to three device pixels, so the outline of the body is spread over a few
    * device pixels instead of stopping dead. Source-over accumulation is geometric, so N taps at alpha a cover
    * the middle of the body {@code 1 - (1 - a)^N} of the way; the loop solves that for the band's own alpha and
    * draws every tap at the result. The body therefore keeps exactly the alpha the ramp gives it - the seam
    * with the opaque part and the fade itself are untouched - while a pixel that only k of the taps reach gets
    * {@code 1 - (1 - a)^k}, which is the soft edge.
    *
    * <p>The alpha test and the depth writes stay on for every band. The reference is weakened for the ramp
    * ({@link #FADE_ALPHA_REFERENCE}) because a band under vanilla's 0.1 would be discarded whole - the ramp
    * would stop at 0.1 instead of fading to nothing.
    */
   private static void renderBust(ModelPlayer model, float centerX, float feetY, float height, float alpha, float yaw, float scale) {
      float dissolveTop = dissolveTopY(feetY, height);
      float bustBottom = bustBottomY(feetY, height);
      float rampLength = bustBottom - dissolveTop;

      // A band has to span the whole figure, which is drawn at height / 32 GUI px per model pixel and never
      // reaches further than 9 model px either side of the centre (BUST_HALF_WIDTH_MODEL_PX, breathing
      // included), plus the feather's own reach. Rounded outwards, so the clip is a little wider than the
      // figure and never thinner.
      float tapStep = FEATHER_STEP_DEVICE_PX / new ScaledResolution(mc).getScaleFactor();
      float tapReach = tapStep * (FEATHER_TAPS - 1) * 0.5F;
      int halfWidth = MathHelper.ceiling_float_int(BUST_HALF_WIDTH_MODEL_PX * height / MODEL_PIXELS_HEIGHT + tapReach);
      int left = MathHelper.floor_float(centerX) - halfWidth;
      int right = MathHelper.ceiling_float_int(centerX) + halfWidth;

      // Rows are assigned by where their centre falls: above the ramp they are opaque, inside it they carry the
      // ramp, and at and below the waist they are outside the bust and are not drawn at all. The top of the
      // opaque band sits just above the model's own top (OPAQUE_BAND_CLEARANCE) so the animated figure can
      // never grow past its own clip, and one row more is taken for the rounding.
      int opaqueTop = MathHelper.floor_float(feetY - height * OPAQUE_BAND_CLEARANCE) - 1;
      int rampTop = MathHelper.ceiling_float_int(dissolveTop - 0.5F);
      int rampBottom = MathHelper.ceiling_float_int(bustBottom - 0.5F);
      int rampRows = rampBottom - rampTop;
      int bands = rampRows <= 0 ? 0 : Math.min(DISSOLVE_BANDS, rampRows);

      boolean scissorWasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
      SCISSOR_BOX.clear();
      GL11.glGetInteger(GL11.GL_SCISSOR_BOX, SCISSOR_BOX);

      try {
         // Opaque part of the bust in a single draw of the whole model. A figure too small to hold a one-row
         // ramp has no ramp to draw, and then the bust simply ends at its lower edge.
         int opaqueBottom = bands > 0 ? rampTop : rampBottom;
         if (opaqueBottom > opaqueTop) {
            AugustusGuiScreen.enableScissor(left, opaqueTop, right, opaqueBottom);
            GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
            drawFigure(model, centerX, feetY, scale, yaw, 0.0F, true);
         }

         if (bands > 0) {
            GlStateManager.alphaFunc(GL11.GL_GREATER, FADE_ALPHA_REFERENCE);

            for (int band = 0; band < bands; band++) {
               // Integer bounds, so the bands tile the ramp exactly and no row falls in two of them.
               int bandTop = rampTop + rampRows * band / bands;
               int bandBottom = rampTop + rampRows * (band + 1) / bands;
               if (bandBottom <= bandTop) {
                  continue;
               }

               // The ramp's shape is sampled at the band's centre, where its average sits for a curve this
               // gentle; sampling the top or the bottom would bias every band the same way and bend the fade.
               float ramp = (bustBottom - (bandTop + bandBottom) * 0.5F) / rampLength;
               ramp = MathHelper.clamp_float(ramp, 0.0F, 1.0F);
               float bandAlpha = alpha * (float)Math.pow((double)ramp, (double)DISSOLVE_EXPONENT);
               // Fainter than anyone can see, and the loop runs top to bottom, so this is the start of a suffix
               // and never a hole in the middle of the ramp. The floor is set by the alpha test rather than by
               // taste: FADE_ALPHA_REFERENCE discards a pass below it, so a band thinner than the floor would
               // be culled whole and punch the hole in the tail that it exists to extend.
               if (bandAlpha <= MIN_BAND_ALPHA) {
                  break;
               }

               // Feather taps are spent where they can be seen. Four taps spread the outline over six device
               // px, which is worth paying for on a torso that is still half there; on the tail, where the
               // whole band is under a tenth of an alpha, one pass already lands as a soft row and the other
               // three would only cost draws - the band's own step is what is invisible down there, so there
               // is nothing left for the extra taps to soften.
               int taps = bandAlpha > FEATHER_FULL_ALPHA ? FEATHER_TAPS : FEATHER_TAPS_FAINT;
               float tapAlpha = 1.0F - (float)Math.pow(1.0F - bandAlpha, 1.0 / taps);
               if (tapAlpha <= FEATHER_MIN_TAP_ALPHA) {
                  // The last rows of the ramp are too faint to feather without the alpha test eating the taps;
                  // a single pass at the band's own alpha lands them softly instead (FEATHER_MIN_TAP_ALPHA).
                  taps = 1;
                  tapAlpha = bandAlpha;
               }

               AugustusGuiScreen.enableScissor(left, bandTop, right, bandBottom);
               GlStateManager.color(1.0F, 1.0F, 1.0F, tapAlpha);
               for (int tap = 0; tap < taps; tap++) {
                  float offsetX = (tap - (taps - 1) * 0.5F) * tapStep;
                  drawFigure(model, centerX, feetY, scale, yaw, offsetX, false);
               }
            }
         }
      } finally {
         AugustusGuiScreen.disableScissor();
         // The clip this class found is put back by hand: neither the scissor nor its enable bit is part of the
         // 8256 mask pushAttrib saves, so a caller that was clipping the figure itself still is afterwards.
         GL11.glScissor(SCISSOR_BOX.get(0), SCISSOR_BOX.get(1), SCISSOR_BOX.get(2), SCISSOR_BOX.get(3));
         if (scissorWasEnabled) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
         } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
         }
      }
   }

   /**
    * Draws the figure once through its model transform, displaced by offsetX GUI pixels.
    *
    * <p>The transform is rebuilt per draw rather than set up once for the frame, because a feather tap has to
    * move the finished figure sideways by up to a device pixel and a half: composing the offset into the base
    * translation keeps it in the GUI space {@link #draw}'s arguments live in. The same offset applied inside
    * the model transform would be multiplied by the figure's scale, which is about fifty at the menu's size.
    *
    * @param head whether the head and its wear layer are part of this draw. The ramp bands pass false: the
    *             head's lowest pixel - the 0.5 model px inflated wear shell under a head pitch of at most five
    *             degrees - sits 0.75 * height above feetY while the ramp starts at 0.725 * height, so all it
    *             could reach is the first ramp row, by a fraction of a GUI pixel, where the band is already
    *             drawing at 95% of the figure's own alpha.
    */
   private static void drawFigure(ModelPlayer model, float centerX, float feetY, float scale, float yaw, float offsetX, boolean head) {
      GlStateManager.pushMatrix();
      try {
         GlStateManager.translate(centerX + offsetX, feetY, 0.0F);
         GlStateManager.rotate(yaw, 0.0F, 1.0F, 0.0F);
         GlStateManager.scale(scale, scale, scale);
         // Origin at the soles, so breathing grows the figure upward instead of sinking it into the floor.
         GlStateManager.translate(0.0F, -MODEL_FEET_Y, 0.0F);
         renderParts(model, head);
      } finally {
         // Balanced even if a part throws: this runs once per feather tap, so a leaked matrix level would
         // overflow the GL stack within a second and drag every later draw with it.
         GlStateManager.popMatrix();
      }
   }

   /**
    * Draws every part the bust is made of, in the order the vanilla player renderer sorts them in.
    *
    * <p>The legs and both leg wear layers are deliberately absent: this class draws a bust, so no part below the
    * waist exists on screen at all. Leaving them out is also four fewer parts per pass, and the framing does not
    * then have to hide a whole second half of the model behind the ramp.
    */
   private static void renderParts(ModelPlayer model, boolean head) {
      model.bipedBody.render(MODEL_SCALE);
      if (head) {
         model.bipedHead.render(MODEL_SCALE);
         model.bipedHeadwear.render(MODEL_SCALE);
      }

      model.bipedBodyWear.render(MODEL_SCALE);
      model.bipedLeftArm.render(MODEL_SCALE);
      model.bipedLeftArmwear.render(MODEL_SCALE);
      model.bipedRightArm.render(MODEL_SCALE);
      model.bipedRightArmwear.render(MODEL_SCALE);
   }

   private static void reassert(boolean lighting, boolean depth, boolean texture2D, boolean cull, boolean blend, boolean alphaTest) {
      if (lighting) {
         GlStateManager.enableLighting();
      } else {
         GlStateManager.disableLighting();
      }
      if (depth) {
         GlStateManager.enableDepth();
      } else {
         GlStateManager.disableDepth();
      }
      if (texture2D) {
         GlStateManager.enableTexture2D();
      } else {
         GlStateManager.disableTexture2D();
      }
      if (cull) {
         GlStateManager.enableCull();
      } else {
         GlStateManager.disableCull();
      }
      if (blend) {
         GlStateManager.enableBlend();
      } else {
         GlStateManager.disableBlend();
      }
      if (alphaTest) {
         GlStateManager.enableAlpha();
      } else {
         GlStateManager.disableAlpha();
      }
   }

   private static ModelPlayer model() {
      if (playerModel == null) {
         ModelPlayer model = new ModelPlayer(0.0F, false);
         // ModelBase defaults isChild to true; a menu figure is always an adult and must never take the
         // child scaling path if a caller ever routes through ModelPlayer#render.
         model.isChild = false;
         model.isRiding = false;
         model.isSneak = false;
         model.aimedBow = false;
         model.heldItemLeft = 0;
         model.heldItemRight = 0;
         model.swingProgress = 0.0F;
         model.bipedHead.showModel = true;
         model.bipedHeadwear.showModel = true;
         model.bipedBody.showModel = true;
         model.bipedBodyWear.showModel = true;
         model.bipedLeftArm.showModel = true;
         model.bipedRightArm.showModel = true;
         model.bipedLeftArmwear.showModel = true;
         model.bipedRightArmwear.showModel = true;
         // The legs stay switched on at the model level on purpose: this is a complete player model, and which
         // part of it the menu shows is renderParts' business - one decision, in one place.
         model.bipedLeftLeg.showModel = true;
         model.bipedRightLeg.showModel = true;
         model.bipedLeftLegwear.showModel = true;
         model.bipedRightLegwear.showModel = true;
         playerModel = model;
      }

      return playerModel;
   }

   /**
    * Resolves the skin to bind, preferring the local player's own skin and falling back to steve.
    *
    * <p>Only skins that are already resident are accepted for the player: binding a location whose download
    * has not finished would make {@code TextureManager.loadTexture} try to load it, log a warning and cache
    * the missing-texture stand-in. Checking first keeps the menu silent and crash-free while a skin is still
    * in flight, and the fallback covers the fresh-start case where there is no player at all.
    */
   private static ResourceLocation skin(TextureManager textureManager) {
      if (cachedSkin != null && cachedSkin != FALLBACK_SKIN && isUsable(textureManager, cachedSkin)) {
         return cachedSkin;
      }

      AbstractClientPlayer player = mc.thePlayer;
      // getLocationSkin() reaches Minecraft#getNetHandler(), which is null on the main menu.
      if (player != null && mc.getNetHandler() != null) {
         ResourceLocation location;
         try {
            location = player.getLocationSkin();
         } catch (Throwable ignored) {
            location = null;
         }

         if (location != null && isUsable(textureManager, location)) {
            cachedSkin = location;
            return location;
         }
      }

      cachedSkin = FALLBACK_SKIN;
      return FALLBACK_SKIN;
   }

   private static boolean isUsable(TextureManager textureManager, ResourceLocation location) {
      Object texture = textureManager.getTexture(location);
      if (texture == null) {
         return false;
      }

      // A downloaded skin reports when its image actually arrived; before that the GL texture is empty and
      // the figure would render blank.
      if (texture instanceof ThreadDownloadImageData) {
         return ((ThreadDownloadImageData)texture).imageFound == Boolean.TRUE;
      }

      return true;
   }
}
