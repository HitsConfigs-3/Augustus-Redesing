package net.augustus.clickgui;

import net.augustus.ClientManagers;
import net.augustus.clickgui.components.TextFieldComponent;
import net.augustus.events.listeners.GuiScaleListener;
import net.augustus.events.listeners.RenderGuiBackgroundListener;
import net.augustus.render.RenderUtil;
import net.augustus.ui.config.ConfigSystemPanel;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.utils.audio.ClientSounds;
import net.augustus.utils.render.BlurShadowManager;
import net.augustus.utils.render.GlStateSaver;
import net.augustus.utils.render.KawaseBlurEngine;
import net.augustus.utils.render.ScrollFadeCompositor;
import net.augustus.utils.render.ShaderRenderPass;
import java.awt.Color;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

public class ClickGuiScreen extends GuiScreen {
   /**
    * Used by the one thing in this screen that must never fail silently: the frame-boundary watchdog for the
    * offscreen scroll-fade region. Everything else in the overlay is presentation and reports through the
    * picture, not through the log.
    */
   private static final Logger LOGGER = LogManager.getLogger("OpenAugustus-ClickGui");
   private int centerX;
   private final GlStateSaver glStateSaver;
   private final MainClickGuiWindow mainWindow;
   private int centerY;
   private int framebufferHeight;
   private ClickGuiWindow activeWindow;
   private int framebufferWidth;
   public static boolean configEditable = true;
   private final ConfigSystemPanel configPanel;
   public static boolean protectedConfigCreator = false;
   private float dockLeftHoverAnim = 0.0F;
   private float dockRightHoverAnim = 0.0F;
   private float guiOpenAnim = 0.0F;

   /** Target the open/close envelope travels to: 1 while the overlay is presented, 0 once it is dismissing. */
   private float guiOpenTarget = 1.0F;
   /** True from {@link #beginClose()} until the close envelope has finished and the screen is removed. */
   private boolean closing;
   /** True only when this screen is the one actually on screen; guards the shared singleton's static state. */
   private boolean presented;
   /**
    * True once the ClickGui module has been observed ENABLED during this presentation.
    *
    * <p>The module-watching fade-out must only ever answer a real enabled-to-disabled transition. The overlay
    * can also be presented while the module is already off (handed back from the HUD editor, opened from a
    * command, or by any future caller that simply displays the screen), and an unconditional "the module is
    * off, therefore close" test would dismiss the overlay on its very first tick. Arming by observation
    * instead of by state keeps that impossible while still giving the keybind-off case its fade-out.
    */
   private boolean moduleArmedForAutoClose;

   /**
    * Latest instance handed to {@code displayGuiScreen}, so the window chrome (the title bar X / minimize
    * buttons live in ClickGuiWindow, which no longer hides the shell itself) can route a close request back
    * into the animated lifecycle instead of the screen vanishing on the frame the button is pressed.
    */
   private static ClickGuiScreen activeScreen;

   /**
    * Rate constant of the screen-wide open/close envelope, in 1/s.
    *
    * <p>WHY 16: {@code UiAnimation.smooth} integrates {@code v += (target-v)*(1-exp(-k*dt))}, so the envelope
    * reaches 99% after {@code ln(100)/k = 4.6/16 = 0.288 s} (288 ms). The design tokens cap ordinary
    * transitions at ~240 ms and allow an overlay enter/exit up to ~320 ms; 288 ms sits inside that window and
    * looks deliberate rather than sluggish. It also matches the surrounding UI (window open envelope k=18,
    * dock hover k=16, popups k=22) so the overlay does not fight the components it contains.
    */
   private static final float SCREEN_TRANSITION_SPEED = 16.0F;

   /**
    * Scale the overlay is drawn at when the envelope is still fully closed.
    *
    * <p>WHY 0.965: a 3.5% zoom is large enough to read as the panel settling into place and small enough that
    * text never visibly reflows - the brief asks for a "progresivo y sutil" scale. The value is applied on
    * top of the window's own local scale transform, at the same anchor (see ClickGuiWindow.drawWindow).
    */
   private static final float SCREEN_SCALE_FROM = 0.965F;

   /**
    * Alpha every glass surface and dock element drawn by the overlay has to be multiplied with.
    *
    * <p>WHY this hand-off exists: the transition alpha cannot ride on GL state. The glass surfaces are drawn
    * through a shader that hard-resets {@code GlStateManager.color(1,1,1,1)} before every quad
    * (GlassRenderer primitive pass), and the font renderer resets the colour state after each string, so a
    * global {@code glColor} fade is silently dropped by exactly the two heaviest subsystems. Exposing the
    * envelope as a value lets the window write it into the alpha parameters it already threads through
    * {@code getOpenProgress()} (window shell + sidebar) and lets this class fade the dock explicitly.
    */
   private static float screenFade = 1.0F;

   /**
    * Current alpha of the overlay's open/close envelope, 1 when fully settled open, 0 when fully closed.
    *
    * <p>Read by {@code ClickGuiWindow.getOpenProgress()} and by the dock drawing below. Static because the
    * window objects are long-lived singletons that outlive this GuiScreen instance.
    */
   public static float getScreenFade() {
      return screenFade;
   }

   /** {@code argb} with its alpha multiplied by the current transition envelope (keeps RGB intact). */
   public static int fadeArgb(int argb) {
      return UiTheme.scaleAlpha(argb, screenFade);
   }

   /**
    * Forwards a close request from the window chrome into the animated lifecycle.
    *
    * <p>Called by {@code ClickGuiWindow.mouseClicked} when the title bar X / minimize button is pressed and by
    * the ESC path in {@link #keyTyped}. No-op unless this screen is the presented one, because the ClickGUI
    * screen is a singleton that also exists (unused) while another screen owns the view.
    */
   public static void requestCloseFromWindowChrome() {
      ClickGuiScreen screen = activeScreen;
      if (screen != null && screen.presented) {
         screen.beginClose();
      }
   }

   /**
    * Starts the fade-out. Deliberately NOT a removal: the screen stays current so
    * {@link #drawScreen} keeps painting while the envelope travels to 0, and the overlay only leaves the
    * stack from {@code finishClose()} once the last frame with alpha 0 has been drawn. Calling it twice (ESC
    * plus the window button, or ESC plus the module toggling itself off) is a no-op and cannot rewind a
    * running close, so no jump can be introduced by a double trigger.
    */
   public void beginClose() {
      if (!this.closing) {
         this.closing = true;
         this.guiOpenTarget = 0.0F;
         // Switch the module off as part of dismissing the overlay: the module's toggle IS the "the overlay is
         // open" state, and leaving it on while the overlay fades makes updateScreen() undo the close on the
         // very next tick - it reads "closing + module still on" as the module having been switched back on and
         // reverses the envelope, so ESC, the title-bar X and the keybind all appeared to do nothing.
         //
         // WHY here and not in each caller: every dismissal path funnels through this method, so the invariant
         // holds for all of them at once. The guarded call keeps the auto-close path (the module is already off
         // when updateScreen() answers an external toggle) a no-op instead of re-toggling the module back on.
         if (ClientManagers.moduleManager != null
            && ClientManagers.moduleManager.clickGui != null
            && ClientManagers.moduleManager.clickGui.isToggled()) {
            ClientManagers.moduleManager.clickGui.setToggled(false);
         }

         // A drag/resize may be in flight exactly at the moment the close starts (ESC pressed with the title
         // bar held). The release would then never reach the window, so the shell would come back stuck to the
         // cursor on the next presentation. Cancelled here, on the transition only.
         this.cancelActiveInteraction();
      }
   }

   /**
    * Aborts a close that has not finished yet and re-runs the open envelope from wherever it is.
    *
    * <p>WHY this is needed: the overlay no longer disappears the instant the module is toggled, so a user can
    * press the keybind again during the ~288 ms fade-out. Minecraft only calls {@code initGui()} when the
    * screen is handed over again, and in that case the screen never left the stack - so without an explicit
    * abort the envelope would keep travelling to 0 and the overlay would close despite the module being back
    * on. Nothing is reset to 0: the envelope reverses from its current value, so re-opening mid-close is a
    * continuation, never a jump.
    */
   public void reopenIfClosing() {
      if (this.closing) {
         this.closing = false;
         this.guiOpenTarget = 1.0F;
      }
   }

   /** Whether the overlay is currently dismissing (blocks all window input until it is gone). */
   public boolean isClosing() {
      return this.closing;
   }

   /**
    * Whether the overlay has finished opening and may accept input.
    *
    * <p>Input is refused for the whole transition in BOTH directions. While closing this is the important one:
    * without it the window would still take clicks after the user asked for it to go away, and the mouse
    * handlers would run against a shell that is already 80% transparent.
    */
   private boolean isTransitionSettled() {
      return !this.closing && this.guiOpenAnim >= 0.999F;
   }

   private boolean isMouseOver(int n, int n2, int n3, int n4, int n5, int n6) {
      return n5 >= n && n6 >= n2 && n5 < n + n3 && n6 < n2 + n4;
   }

   /**
    * Window hit test in raw framebuffer pixels.
    *
    * <p>WHY not the stored rect: the GUI Scale setting moves and resizes the on-screen shell, so the
    * exact same transform the window uses for rendering and for its own mouse mapping
    * ({@link ClickGuiWindow#getScaleTransform()}) has to be used here as well. Comparing the raw mouse
    * against the unscaled X/Y/Width/Height would desynchronise window activation and the dock hover
    * test from what is actually drawn.
    */
   private boolean isOverWindow(ClickGuiWindow window, int framebufferMouseX, int framebufferMouseY) {
      ClickGuiWindow.ScaleTransform transform = window.getScaleTransform();
      return RenderUtil.isHovered(
         transform.left,
         transform.top,
         (float)window.getWidth() * transform.scale,
         (float)window.getHeight() * transform.scale,
         framebufferMouseX,
         framebufferMouseY
      );
   }

   @Override
   public void handleKeyboardInput() throws IOException {
      this.handleKeybindClose();
      this.mainWindow.handleKeyboardInput();
      this.configPanel.handleKeyboardInput();
      super.handleKeyboardInput();
   }

   /**
    * Closes the overlay when the keybind that opens it is pressed again while it is presented.
    *
    * <p>WHY the overlay owns this itself: the ClickGui module is in {@code GuiScreen.NO_TOGGLE_SCREENS} for as
    * long as this screen is on the stack, so {@code ToggleUtil} deliberately ignores the module's press while
    * the overlay is up - and the module's "hold the key to toggle off" release path is disabled for it too,
    * because that path used to switch the module off behind an already-open overlay. Without an explicit
    * handler here the keybind would open the overlay and then do nothing, which reads as "the ClickGUI cannot
    * be closed". Handled only once the open envelope has settled, so the press that opened the overlay can
    * never be mistaken for a close request.
    */
   private void handleKeybindClose() {
      if (this.closing || !this.isTransitionSettled() || ClientManagers.moduleManager == null) {
         return;
      }

      if (ClientManagers.moduleManager.clickGui == null) {
         return;
      }

      int key = ClientManagers.moduleManager.clickGui.getKeyCode();
      if (key != 0 && Keyboard.getEventKeyState() && Keyboard.getEventKey() == key) {
         this.beginClose();
      }
   }

   @Override
   public void keyTyped(char typedChar, int keyCode) throws IOException {
      if (this.mainWindow.getPopupHost().hasActivePopup() && keyCode == 1) {
         this.mainWindow.keyTyped(typedChar, keyCode);
         return;
      }
      if (!this.mainWindow.shouldConsumeEscapeOrEnter(keyCode)) {
         // ESC used to hand the screen straight to Minecraft, which removed it on this very frame and threw
         // the whole open/close envelope away. Intercepting it here is what lets the fade-out be seen to
         // completion; the actual removal happens in finishClose() once the envelope has reached 0. The
         // popup and focused-field cases above are untouched, so ESC still means "close the dropdown first".
         if (keyCode == 1 && !this.closing && this.guiOpenTarget != 0.0F) {
            this.beginClose();
            return;
         }

         super.keyTyped(typedChar, keyCode);
      }

      this.mainWindow.keyTyped(typedChar, keyCode);
      this.configPanel.keyTyped(typedChar, keyCode);
   }

   public ConfigSystemPanel getConfigPanel() {
      return this.configPanel;
   }

   public MainClickGuiWindow getMainWindow() {
      return this.mainWindow;
   }

   @Override
   public void initGui() {
      super.initGui();
      this.allowUserInput = true;
      if (this.mainWindow.getX() < 0 || this.mainWindow.getY() < 0 || this.configPanel.getX() < 0 || this.configPanel.getY() < 0) {
         this.mainWindow.resetWindow(this.mainWindow.getName());
         this.configPanel.resetWindow(this.configPanel.getName());
         this.configPanel.setVisible(false);
      }

      this.configPanel.onPanelOpened();
      this.activeWindow = null;
      // Open transition. initGui() is the hook Minecraft calls every time this screen is handed to
      // displayGuiScreen (including a re-open), which is exactly when the open animation has to start over:
      // resetting here - and NOT from drawScreen - is what makes the very first rendered frame draw at
      // progress 0 with a properly primed clock (UiAnimation.beginFrame ran at the start of that same frame),
      // instead of showing one frame of the previous, fully-open state and then jumping back to 0.
      this.guiOpenAnim = 0.0F;
      this.guiOpenTarget = 1.0F;
      this.closing = false;
      this.presented = true;
      // A presentation starts unarmed: updateScreen() arms it as soon as it sees the module on.
      this.moduleArmedForAutoClose = false;
      activeScreen = this;
      screenFade = 0.0F;
      UiAnimation.setState("dock_left_act", 0.0F);
      UiAnimation.setState("dock_right_act", 0.0F);
      GuiScreen.NO_TOGGLE_SCREENS.add(Minecraft.getMinecraft().currentScreen);
   }

   @Override
   public void drawScreen(int mouseX, int mouseY, float partialTicks) {
      // ONE clock for the whole overlay. UiAnimation.beginFrame() latches a monotonic delta-time once per
      // rendered frame (clamped to 16 ms on a stall), and every envelope below integrates that delta - the
      // screen envelope here, the window and popup envelopes inside drawWindow. partialTicks is deliberately
      // not used by any of them: mixing a tick-interpolated value with frame deltas is the classic source of
      // stutter and of an animation that runs at a different speed on 60 Hz and 200 Hz. beginFrame() used to
      // be called by WorldLoadWarmup right before its off-screen drawScreen(), so it lives at the top of this
      // method to keep the frame id and the delta advancing exactly once per frame.
      UiAnimation.beginFrame();
      // Frame-boundary watchdog for the offscreen scroll-fade region. While such a region is active the
      // compositor owns the framebuffer binding, so a region lost mid-frame (early return, exception outside a
      // finally, re-entrant call) sends every later draw call into an offscreen texture: the overlay then looks
      // like "only one patch of the window renders". Nothing is allowed to own the binding across frames.
      if (ScrollFadeCompositor.isActive()) {
         ScrollFadeCompositor.forceAbort();
         LOGGER.warn(
            "Aborted a scroll-fade region that outlived its frame. The region was force-closed so the overlay cannot keep the framebuffer binding for the rest of the session; if this line appears, a region was lost mid-frame (early return, exception outside a finally, or a re-entrant call)."
         );
      }
      // Commit a pending close here: beginClose() only records the intent, the envelope starts travelling on
      // the next frame, so the mouse handler can never reshape the transition mid-input.
      if (this.closing && this.guiOpenTarget != 0.0F) {
         this.guiOpenTarget = 0.0F;
      }
      // Close completion is checked from drawScreen rather than updateScreen(): drawScreen is the one
      // callback that provably runs once per frame for the current screen, so the removal cannot be lost to a
      // skipped tick, and the check runs before the finished envelope is painted a second time.
      if (this.closing && this.guiOpenAnim <= 0.004F) {
         this.finishClose();
         return;
      }

      // Sample the envelope BEFORE advancing it, so the frame that initGui() primed draws at exactly 0 and
      // the first visible frame is the first step of the fade-in. Nothing is drawn "at the old value" and
      // then corrected on the next frame: the sampled value IS this frame's value.
      float fade = this.guiOpenAnim;
      screenFade = fade;
      this.guiOpenAnim = UiAnimation.smooth(this.guiOpenAnim, this.guiOpenTarget, SCREEN_TRANSITION_SPEED);
      // Snap the tail instead of integrating forever: UiAnimation.smooth stops at 1e-3, and the input gate
      // below compares against a 0.999F threshold, so pin the envelope once it is visually done. This also
      // prevents a long-tail "rolling" alpha that would keep interaction disabled for seconds.
      if (Math.abs(this.guiOpenTarget - this.guiOpenAnim) < 0.01F) {
         this.guiOpenAnim = this.guiOpenTarget;
      }

      KawaseBlurEngine.invalidateFrame();
      this.glStateSaver.setAlpha(false);
      TextFieldComponent.anyFieldFocused = false;
      BlurShadowManager.disableGuiLayer();
      ScaledResolution sr = new ScaledResolution(this.mc);
      // Resolve the framebuffer and its centre BEFORE any transform is applied: the transition scale is
      // anchored on this centre and must not depend on the current matrix. (The dock drawing below still
      // reassigns these from the same source, so nothing else changes.)
      this.framebufferWidth = this.mc.getFramebuffer().framebufferWidth;
      this.framebufferHeight = this.mc.getFramebuffer().framebufferHeight;
      this.centerX = (int)((float)this.framebufferWidth / 2.0F);
      this.centerY = (int)((float)this.framebufferHeight / 2.0F);

      // Subtle progressive scale, anchored on the framebuffer centre and driven by the same envelope as the
      // fade, so the overlay grows/moves as one piece.
      //
      // WHY the scale lives here and NOT in ClickGuiWindow.getScaleTransform(): that transform is also the
      // mouse inverse map (toLocalX/toLocalY) and the source of every glScissor rect in MainClickGuiWindow.
      // Deforming it mid-transition would make every clip rect drift away from the drawn content - the exact
      // bug class the brief calls out (scissor computed from final geometry while the visual scale is
      // animating). Scaling only the dock, which owns no clip bound and no input mapping of its own, keeps
      // every clipping surface in the project on the transform its own geometry was computed with. The
      // window shell gets its own equivalent, equally clip-safe zoom in ClickGuiWindow.drawWindow, applied
      // INSIDE the transform the window already uses for drawing and clipping.
      float eased = UiAnimation.easeOutCubic(fade);
      float scale = SCREEN_SCALE_FROM + (1.0F - SCREEN_SCALE_FROM) * eased;
      // Whether the overlay is allowed to react. Captured from the SAME sampled envelope the alpha and scale
      // use above and below, so hover/press feedback cannot contradict the click gate in mouseClicked(): a
      // dock button can never look pressable while clicks are being refused.
      boolean interactive = !this.closing && fade >= 0.999F;
      GlStateManager.pushMatrix();
      GlStateManager.translate((double)this.centerX, (double)this.centerY, 0.0);
      GlStateManager.scale((double)scale, (double)scale, 1.0);
      GlStateManager.translate((double)(-this.centerX), (double)(-this.centerY), 0.0);
      if (this.mainWindow.isVisible() || this.configPanel.isVisible()) {
         // Inside the transition, so the world dim rides the same envelope as the overlay and cannot be
         // left painted at full strength on the last faded frame. It is drawn after pushMatrix above and
         // before the dock's own 1/guiScale matrix, i.e. still in full-screen framebuffer space, so the
         // scrim keeps covering the whole screen instead of shrinking with the dock.
         RenderGuiBackgroundListener.postRenderGuiBackground(this);
      }

      GlStateManager.pushMatrix();
      GlStateManager.scale(1.0 / (double)sr.getScaleFactor(), 1.0 / (double)sr.getScaleFactor(), 1.0);
      int n = mouseX;
      int n2 = mouseY;
      mouseX *= sr.getScaleFactor();
      mouseY *= sr.getScaleFactor();
      byte b = 6;
      if (!this.mainWindow.isFullScreen() && !this.configPanel.isFullScreen()) {
         BlurShadowManager.enableOneShotPass(false, ClientManagers.moduleManager.clickGui.clickGui.isShadowActive());
         BlurShadowManager.submitRenderTask(false, shaderRenderPass -> this.renderDockBackgroundPass(b, shaderRenderPass));
         boolean overLeftDock = this.isMouseOver(this.centerX - 43 - b, (byte)0, (byte)42, 37 + b, mouseX, mouseY)
            && (!this.isOverWindow(this.configPanel, mouseX, mouseY) || !this.configPanel.isVisible())
            && (!this.isOverWindow(this.mainWindow, mouseX, mouseY) || !this.mainWindow.isVisible())
            && interactive;
         boolean overRightDock = this.isMouseOver(this.centerX + 1, (byte)0, (byte)42, 37 + b, mouseX, mouseY)
            && (!this.isOverWindow(this.configPanel, mouseX, mouseY) || !this.configPanel.isVisible())
            && (!this.isOverWindow(this.mainWindow, mouseX, mouseY) || !this.mainWindow.isVisible())
            && interactive;

         int accent = UiTheme.accentColor();
         boolean leftVis = this.mainWindow.isVisible();
         boolean rightVis = this.configPanel.isVisible();
         // Raw button state is read for the press animation only while the overlay is interactive, so the press
         // highlight can never contradict the click gate in mouseClicked(). overLeftDock/overRightDock already
         // carry that gate plus the geometry test.
         boolean leftPressed = overLeftDock && UiAnimation.isPrimaryMouseDown();
         boolean rightPressed = overRightDock && UiAnimation.isPrimaryMouseDown();
         this.dockLeftHoverAnim = UiAnimation.smooth(this.dockLeftHoverAnim, overLeftDock ? 1.0F : 0.0F, 16.0F);
         this.dockRightHoverAnim = UiAnimation.smooth(this.dockRightHoverAnim, overRightDock ? 1.0F : 0.0F, 16.0F);
         float leftActiveT = UiAnimation.smoothState("dock_left_act", leftVis ? 1.0F : 0.0F, 16.0F);
         float rightActiveT = UiAnimation.smoothState("dock_right_act", rightVis ? 1.0F : 0.0F, 16.0F);
         float leftPressT = UiAnimation.smoothState("dock_left_prs", leftPressed ? 1.0F : 0.0F, 28.0F);
         float rightPressT = UiAnimation.smoothState("dock_right_prs", rightPressed ? 1.0F : 0.0F, 28.0F);

         float dockBtnY = 3.0F;
         float dockBtnH = (float)(37 + b - 7);
         float leftBtnX = (float)(this.centerX - 43 - b + 3);
         float rightBtnX = (float)(this.centerX + 3);
         float dockBtnW = 42.0F;

         if (this.dockLeftHoverAnim > 0.02F || leftActiveT > 0.02F) {
            float mixL = Math.max(leftActiveT * 0.85F, this.dockLeftHoverAnim);
            int topL = UiTheme.lerpArgb(UiTheme.glassControlFillArgb(this.dockLeftHoverAnim, 0.0F, accent), UiTheme.withAlpha(UiTheme.lerpArgb(accent, 0xFF282058, 0.28F), 200), leftActiveT);
            int botL = UiTheme.lerpArgb(UiTheme.lerpArgb(topL, 0xFF0A0A14, 0.25F), UiTheme.withAlpha(UiTheme.accentDeep(), 190), leftActiveT);
            int borderL = UiTheme.lerpArgb(UiTheme.GLASS_BORDER, UiTheme.withAlpha(UiTheme.accentSoftGlow(), 220), mixL);
            if (leftActiveT > 0.05F) {
               GlassRenderer.drawSoftShadow(leftBtnX, dockBtnY, dockBtnW, dockBtnH, 7.0F, 7.0F, 1.0F, UiTheme.withAlpha(accent, Math.round(58.0F * leftActiveT)));
            }
            GlassRenderer.drawInteractiveSurface(leftBtnX, dockBtnY, dockBtnW, dockBtnH, 7.0F, topL, botL, borderL, UiTheme.BORDER_THICKNESS, UiTheme.glassTopHighlightAlpha(mixL), this.dockLeftHoverAnim, leftPressT, true, (float)mouseX, (float)mouseY, accent);
         }
         if (this.dockRightHoverAnim > 0.02F || rightActiveT > 0.02F) {
            float mixR = Math.max(rightActiveT * 0.85F, this.dockRightHoverAnim);
            int topR = UiTheme.lerpArgb(UiTheme.glassControlFillArgb(this.dockRightHoverAnim, 0.0F, accent), UiTheme.withAlpha(UiTheme.lerpArgb(accent, 0xFF282058, 0.28F), 200), rightActiveT);
            int botR = UiTheme.lerpArgb(UiTheme.lerpArgb(topR, 0xFF0A0A14, 0.25F), UiTheme.withAlpha(UiTheme.accentDeep(), 190), rightActiveT);
            int borderR = UiTheme.lerpArgb(UiTheme.GLASS_BORDER, UiTheme.withAlpha(UiTheme.accentSoftGlow(), 220), mixR);
            if (rightActiveT > 0.05F) {
               GlassRenderer.drawSoftShadow(rightBtnX, dockBtnY, dockBtnW, dockBtnH, 7.0F, 7.0F, 1.0F, UiTheme.withAlpha(accent, Math.round(58.0F * rightActiveT)));
            }
            GlassRenderer.drawInteractiveSurface(rightBtnX, dockBtnY, dockBtnW, dockBtnH, 7.0F, topR, botR, borderR, UiTheme.BORDER_THICKNESS, UiTheme.glassTopHighlightAlpha(mixR), this.dockRightHoverAnim, rightPressT, true, (float)mouseX, (float)mouseY, accent);
         }

         GlassRenderer.drawSeparator((float)this.centerX, 7.0F, (float)this.centerX, (float)(37 + b - 7), 1.0F, UiTheme.scaleAlpha(UiTheme.GLASS_DIVIDER, fade));
         int leftIconCol = fadeArgb(UiTheme.lerpArgb(UiTheme.secondaryTextColor(), UiTheme.TEXT_PRIMARY, Math.max(leftActiveT, this.dockLeftHoverAnim)));
         int rightIconCol = fadeArgb(UiTheme.lerpArgb(UiTheme.secondaryTextColor(), UiTheme.TEXT_PRIMARY, Math.max(rightActiveT, this.dockRightHoverAnim)));
         LucideIcon.SLIDERS_HORIZONTAL.drawCentered(leftBtnX + dockBtnW * 0.5F, dockBtnY + dockBtnH * 0.5F + leftPressT * 0.5F - leftActiveT * 1.0F, 17.5F, leftIconCol);
         LucideIcon.FOLDER.drawCentered(rightBtnX + dockBtnW * 0.5F, dockBtnY + dockBtnH * 0.5F + rightPressT * 0.5F - rightActiveT * 1.0F, 17.5F, rightIconCol);
         if (leftActiveT > 0.04F) {
            float barW = 14.0F * leftActiveT;
            GlassRenderer.drawRoundedRect(leftBtnX + (dockBtnW - barW) * 0.5F, dockBtnY + dockBtnH - 4.0F, barW, 2.2F, 1.1F, UiTheme.withAlpha(UiTheme.accentSoftGlow(), Math.round(235.0F * leftActiveT)));
         }
         if (rightActiveT > 0.04F) {
            float barW = 14.0F * rightActiveT;
            GlassRenderer.drawRoundedRect(rightBtnX + (dockBtnW - barW) * 0.5F, dockBtnY + dockBtnH - 4.0F, barW, 2.2F, 1.1F, UiTheme.withAlpha(UiTheme.accentSoftGlow(), Math.round(235.0F * rightActiveT)));
         }
      }

      // Closes the transition-scale matrix opened before the dock. Paired with the pushMatrix above, so the
      // stack is balanced even when the dock branch is skipped (full-screen window).
      GlStateManager.popMatrix();
      GlStateManager.popMatrix();
      if (this.activeWindow != null) {
         if (this.activeWindow.equals(this.mainWindow)) {
            if (!this.mainWindow.isFullScreen()) {
               this.configPanel.drawWindow(n, n2, partialTicks);
            }

            this.mainWindow.drawWindow(n, n2, partialTicks);
         } else {
            if (!this.configPanel.isFullScreen()) {
               this.mainWindow.drawWindow(n, n2, partialTicks);
            }

            this.configPanel.drawWindow(n, n2, partialTicks);
         }
      } else {
         this.configPanel.drawWindow(n, n2, partialTicks);
         this.mainWindow.drawWindow(n, n2, partialTicks);
      }

      BlurShadowManager.enableGuiLayer();
      this.glStateSaver.restoreAlpha();
      super.drawScreen(n, n2, partialTicks);
   }

   /**
    * Ends the close transition: clears the shared transition state and really dismisses the screen.
    *
    * <p>WHY a single exit point: the state that the dock and the window shell read through
    * {@link #getScreenFade()} is static (it outlived this GuiScreen by design), so leaving the overlay
    * without restoring it to 1.0F would permanently fade out the next presentation. Resetting it here - and
    * in {@link #initGui} for the re-open path - keeps the envelope symmetric.
    */
   private void finishClose() {
      this.presented = false;
      screenFade = 1.0F;
      if (Minecraft.getMinecraft().currentScreen == this) {
         Minecraft.getMinecraft().displayGuiScreen((GuiScreen)null);
      }
   }

   @Override
   public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
      // A drag that was already in flight must be able to finish (and be released) while the overlay is
      // fading, otherwise a window would be left "stuck to the cursor" after re-opening. New interaction is
      // impossible because mouseClicked ignores input while the transition runs.
      if (this.presented) {
         this.mainWindow.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
         this.configPanel.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
      }

      super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
   }

   /**
    * Cancels a drag/resize that was in flight when the transition started.
    *
    * <p>WHY: the close can begin between the press and the release (ESC, or the module being toggled off by
    * keybind), so the release would never reach the window and re-opening would show a shell still following
    * the cursor. Applied only to the transition, never during normal use.
    */
   private void cancelActiveInteraction() {
      this.mainWindow.mouseReleased(-1, -1, -1);
      this.configPanel.mouseReleased(-1, -1, -1);
   }

   private void renderDockBackgroundPass(int n, ShaderRenderPass shaderRenderPass) {
      if (shaderRenderPass == ShaderRenderPass.SHADOW) {
         RenderUtil.drawRoundedRect((float)(this.centerX - 43 - n), (float)(-n), (float)(this.centerX + 43 + n), (float)(37 + n), (float)n, 1, ClientManagers.moduleManager.clickGui.clickGui.resolveShadowColor(Color.black.getRGB()));
      } else if (ClientManagers.moduleManager.clickGui.isPanelBlurActive()) {
         GlassRenderer.drawLiquidGlassSurface(
            (float)(this.centerX - 43 - n),
            (float)(-n),
            (float)(86 + n * 2),
            (float)(37 + n * 2),
            0.0F,
            0.0F,
            (float)(n + 4),
            (float)(n + 4),
            ClientManagers.moduleManager.clickGui.getBlurRadius(),
            UiTheme.THICK_BORDER_THICKNESS,
            UiTheme.GLASS_BORDER_STRONG
         );
      } else {
         GlassRenderer.drawLiquidGlassSurface(
            (float)(this.centerX - 43 - n),
            (float)(-n),
            (float)(86 + n * 2),
            (float)(37 + n * 2),
            0.0F,
            0.0F,
            (float)(n + 4),
            (float)(n + 4),
            0.0F,
            UiTheme.THICK_BORDER_THICKNESS,
            UiTheme.GLASS_BORDER_STRONG
         );
      }
   }

   @Override
   public void actionPerformed(GuiButton button) throws IOException {
      super.actionPerformed(button);
      if (button.id == 1) {
         GuiScaleListener.postGuiScale();
      }
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      super.mouseClicked(mouseX, mouseY, mouseButton);
      // Input is refused for the whole transition, in both directions. While closing this matters most: the
      // shell is fully transparent long before it is removed, and without this gate a click would still
      // activate the dock or a button the user has already dismissed. While opening it also keeps the very
      // first frames - drawn at alpha 0 - from swallowing a click aimed at the game behind them.
      if (!this.isTransitionSettled()) {
         return;
      }

      int n = mouseX;
      int n2 = mouseY;
      ScaledResolution sr = new ScaledResolution(this.mc);
      mouseX *= sr.getScaleFactor();
      mouseY *= sr.getScaleFactor();
      if (this.activeWindow == null) {
         if (this.isOverWindow(this.mainWindow, mouseX, mouseY)) {
            this.activeWindow = this.mainWindow;
         } else if (this.isOverWindow(this.configPanel, mouseX, mouseY)) {
            this.activeWindow = this.configPanel;
         }
      } else if (this.activeWindow.equals(this.mainWindow)) {
         if (!this.isOverWindow(this.mainWindow, mouseX, mouseY) && this.isOverWindow(this.configPanel, mouseX, mouseY)) {
            this.activeWindow = this.configPanel;
         }
      } else if (this.activeWindow.equals(this.configPanel)
         && !this.isOverWindow(this.configPanel, mouseX, mouseY)
         && this.isOverWindow(this.mainWindow, mouseX, mouseY)) {
         this.activeWindow = this.mainWindow;
      }

      if (!this.mainWindow.isVisible() && this.configPanel.isVisible()) {
         this.activeWindow = this.configPanel;
      }

      if (!this.configPanel.isVisible() && this.mainWindow.isVisible()) {
         this.activeWindow = this.mainWindow;
      }

      if (this.activeWindow != null) {
         if (this.activeWindow.equals(this.mainWindow)) {
            this.mainWindow.mouseClicked(n, n2, mouseButton);
         } else {
            this.configPanel.mouseClicked(n, n2, mouseButton);
         }
      }

      if (mouseButton == 0 && (!this.configPanel.isFullScreen() || !this.configPanel.isVisible()) && (!this.mainWindow.isFullScreen() || !this.mainWindow.isVisible())) {
         byte b = 6;
         if (this.isMouseOver(this.centerX - 43 - b, (byte)0, (byte)42, 37 + b, mouseX, mouseY)) {
            if (!this.isOverWindow(this.configPanel, mouseX, mouseY) || !this.configPanel.isVisible()) {
               if (!this.isOverWindow(this.mainWindow, mouseX, mouseY) || !this.mainWindow.isVisible()) {
                  if (!this.mainWindow.isVisible() && this.mainWindow.closed) {
                     this.activeWindow = this.mainWindow;
                  }

                  String str = "ClickGui";
                  this.mainWindow.toggleWindow(str);
                  this.activeWindow = this.mainWindow;
                  ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE);
               }
            }
         }

         if (this.isMouseOver(this.centerX + 1, (byte)0, (byte)42, 37 + b, mouseX, mouseY)) {
            if (!this.isOverWindow(this.configPanel, mouseX, mouseY) || !this.configPanel.isVisible()) {
               if (!this.isOverWindow(this.mainWindow, mouseX, mouseY) || !this.mainWindow.isVisible()) {
                  if (!this.configPanel.isVisible() && this.configPanel.closed) {
                     this.activeWindow = this.configPanel;
                  }

                  String str2 = "Configs";
                  this.configPanel.toggleWindow(str2);
                  this.activeWindow = this.configPanel;
                  ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE);
               }
            }
         }
      }
   }

   @Override
   public void updateScreen() {
      super.updateScreen();
      this.mainWindow.updatePanel();
      // Safety net for the dismissal path that cannot be intercepted from here: the ClickGui module is
      // switched off elsewhere, and by the time the tick runs mc.currentScreen is still this screen (Minecraft
      // does not clear the screen just because the module changed). Watching the module here lets the fade-out
      // start immediately, so a module-off and ESC both produce the same closing animation.
      //
      // Guarded by presented: this GuiScreen is a singleton that WorldLoadWarmup also drives directly
      // (drawScreen/updateScreen on a screen that is not on the stack), and without the guard that warm-up
      // tick would start closing an overlay nobody opened.
      //
      // Armed by observation: the fade-out only answers a transition the overlay actually witnessed. Presenting
      // the screen while the module is already off (HUD editor hand-back, a command, a direct displayGuiScreen
      // call) must NOT dismiss it - that is a ClickGUI that "opens and closes on its own".
      boolean moduleEnabled = this.isModuleEnabledExternally();
      if (moduleEnabled) {
         this.moduleArmedForAutoClose = true;
      }

      if (this.presented && this.closing && moduleEnabled) {
         // The module was toggled back on while the fade-out was still running (the overlay is still the
         // current screen, so Minecraft will NOT call initGui() again). Reverse the envelope instead of
         // finishing the close.
         this.reopenIfClosing();
      } else if (this.presented
         && !this.closing
         && this.moduleArmedForAutoClose
         && !moduleEnabled
         && this.guiOpenTarget != 0.0F) {
         this.beginClose();
      }
   }

   /**
    * Whether the ClickGui module is currently enabled, i.e. whether the overlay is "owned" by the module.
    *
    * <p>Read through the manager rather than the module class to keep this free of any dependency on the
    * module's own state machine; a null manager (early boot, warm-up) simply reports "not enabled", which
    * leaves the auto-close unarmed.
    */
   private boolean isModuleEnabledExternally() {
      return ClientManagers.moduleManager != null
         && ClientManagers.moduleManager.clickGui != null
         && ClientManagers.moduleManager.clickGui.isToggled();
   }

   @Override
   public void onGuiClosed() {
      // onGuiClosed() only runs once the screen really leaves the stack, i.e. after the close animation has
      // been played out (or when another screen replaces us outright). Everything that has to happen exactly
      // once per presentation is released here.
      this.mainWindow.onClose();
      this.presented = false;
      this.closing = false;
      // Hand the shared envelope back at full strength: the dock and the window shell read it statically, so
      // the next presentation must not start from a faded value.
      screenFade = 1.0F;
      if (activeScreen == this) {
         activeScreen = null;
      }

      super.onGuiClosed();
   }

   public ClickGuiScreen() {
       super();
      this.mainWindow = new MainClickGuiWindow("ClickGui", -1, -1, 1154, 680);
      this.configPanel = (new ConfigSystemPanel("Configs", -1, -1, 872, 380)).initPanel();
      this.glStateSaver = GlStateSaver.create();
      this.activeWindow = null;
      this.framebufferWidth = 0;
      this.framebufferHeight = 0;
      this.centerX = 0;
      this.centerY = 0;
      // The envelope is shared static state (see getScreenFade()). Starting it at full strength here means a
      // freshly constructed screen can never inherit a half-faded value from a previous profile/session, and
      // the window shell stays fully visible if anything draws it before initGui() runs.
      screenFade = 1.0F;
   }

   public void setMc(Minecraft mc2) {
      this.mc = mc2;
   }

   @Override
   public void mouseReleased(int mouseX, int mouseY, int state) {
      if (!this.isTransitionSettled()) {
         // Ends any drag/resize that started before the transition without feeding the stale release
         // coordinates into the windows (see cancelActiveInteraction). The release itself is still forwarded
         // so the windows never keep a pressed/dragging flag set.
         this.cancelActiveInteraction();
      }

      this.mainWindow.mouseReleased(mouseX, mouseY, state);
      this.configPanel.mouseReleased(mouseX, mouseY, state);
      super.mouseReleased(mouseX, mouseY, state);
   }

}
