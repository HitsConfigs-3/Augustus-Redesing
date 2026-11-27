package net.augustus.clickgui;

import net.augustus.ClientManagers;
import net.augustus.config.ClientSettingsConfigFile;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.render.RenderUtil;
import net.augustus.settings.BlurShadowSettings;
import net.augustus.settings.property.BooleanProperty;
import net.augustus.settings.property.IntegerProperty;
import net.augustus.utils.audio.ClientSounds;
import net.augustus.utils.render.BlurComponent;
import net.augustus.utils.render.BlurShadowManager;
import net.augustus.utils.render.ColorUtil;
import net.augustus.utils.render.ShaderRenderPass;
import java.awt.Color;
import java.io.IOException;
import net.augustus.modules.AbstractFeature;
import net.augustus.modules.render.ClickGui;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.utils.interfaces.MC;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

public class ClickGuiWindow extends AbstractFeature implements MC {
   private int dragStartMouseY;
   private boolean mouseDragging;
   private final int defaultWidth;
   private CustomFontUtil titleFont;
   private String title;
   private final IntegerProperty windowHeight;
   private int lastMouseY;
   private final BooleanProperty visible;
   private final IntegerProperty windowX;
   private final IntegerProperty width;
   private final IntegerProperty height;
   private final BooleanProperty fullScreen;
   private int lastMouseX;
   private final IntegerProperty x;
   private final IntegerProperty windowWidth;
   private int dragStartMouseX;
   public boolean closed;
   private final int defaultHeight;
   private boolean resizing;
   private int dragStartWindowX;
   private boolean draggingTitleBar;
   private final IntegerProperty windowY;
   private int dragStartWindowY;
   private final IntegerProperty y;
   public int cornerRadius;
   private float openAnim = 0.0F;
   /** Whether {@link #applyTransitionZoom()} pushed the overlay-zoom matrix for the frame being drawn. */
   private boolean transitionZoomPushed;

   /**
    * Screen-wide open/close envelope (1 settled open, 0 fully closed), owned by {@link ClickGuiScreen}.
    *
    * <p>WHY read it from the screen instead of animating a second, window-local close here: the window shell
     * is a long-lived singleton that outlives the GuiScreen, and the overlay's fade has to cover the window
     * and the config panel with ONE value, otherwise the two surfaces desynchronise and the last visible
     * frame shows leftover chrome. Folding it into {@link #getOpenProgress()} reuses the alpha plumbing
     * the glass already has (window surface, sidebar tint, panel-blur outline), so no new draw path is added.
    */
   protected static float screenTransition() {
      return ClickGuiScreen.getScreenFade();
   }

   /** {@code argb} with its alpha scaled by the overlay envelope; keeps RGB untouched. */
   protected static int fadedArgb(int argb) {
      return ClickGuiScreen.fadeArgb(argb);
   }

   /**
    * GUI Scale transform of this window: the framebuffer-space position of the scaled shell's
    * top-left corner plus the scale factor itself.
    *
    * <p>WHY one shared value: rendering must apply exactly the transform that mouse input inverts.
    * If the two are computed independently they can drift and clicks land on the wrong widget at any
    * scale other than 1.0, so both paths derive their maths from {@link #getScaleTransform()}.
    */
   public static final class ScaleTransform {
      /** Framebuffer-space x of the scaled shell's top-left corner. */
      public final float left;
      /** Framebuffer-space y of the scaled shell's top-left corner. */
      public final float top;
      /** Window-local pixel -> on-screen pixel multiplier (Small 0.85 / Normal 1.0 / Large 1.25). */
      public final float scale;

      ScaleTransform(float left, float top, float scale) {
         this.left = left;
         this.top = top;
         this.scale = scale;
      }
   }

   /**
    * Interface scale for this window's content. The ClickGUI window overrides this with the module's
    * GUI Scale setting (Small 0.85 / Normal 1.0 / Large 1.25); every other window keeps 1.0 so its
    * existing 1:1 metrics, clip rects and stored geometry stay untouched.
    */
   protected float getContentScaleFactor() {
      return 1.0F;
   }

   /**
    * Resolves the on-screen placement of this window for the current GUI Scale setting.
    *
    * <p>The persisted X/Y/Width/Height keep their original meaning and unit (unscaled window-local
    * framebuffer pixels); the scale is applied on top of them, so saved window geometry, the X/Y/
    * Width/Height/WindowX/WindowY/WindowWidth/WindowHeight keys and the drag maths keep working.
    * The shell is scaled about its own centre and then slid back inside the framebuffer when it fits,
    * so it stays centred and fully on screen at every scale. Growth is capped at the framebuffer so a
    * "Large" shell can never push the sidebar or the window controls off screen.
    */
   public final ScaleTransform getScaleTransform() {
      float scale = this.getContentScaleFactor();
      if (Float.isNaN(scale) || scale <= 0.05F) {
         // Corrupt/hostile setting: fall back to 1:1 instead of collapsing the shell.
         scale = 1.0F;
      }

      float frameWidth = 0.0F;
      float frameHeight = 0.0F;
      if (mc != null && mc.getFramebuffer() != null) {
         frameWidth = (float)mc.getFramebuffer().framebufferWidth;
         frameHeight = (float)mc.getFramebuffer().framebufferHeight;
      }

      int width = this.getWidth();
      int height = this.getHeight();
      if (scale > 1.0F && width > 0 && height > 0 && frameWidth > 0.0F && frameHeight > 0.0F) {
         // The cap only ever limits growth, so a window that already overflows a small framebuffer
         // keeps today's behaviour instead of being shrunk at Normal.
         float fit = Math.min(frameWidth / (float)width, frameHeight / (float)height);
         if (fit >= 1.0F && scale > fit) {
            scale = fit;
         }
      }

      float scaledWidth = (float)width * scale;
      float scaledHeight = (float)height * scale;
      float left = (float)this.getX() + ((float)width - scaledWidth) * 0.5F;
      float top = (float)this.getY() + ((float)height - scaledHeight) * 0.5F;
      // Keep the scaled shell fully on screen. Guarded by scale != 1.0 on purpose: at Normal the
      // placement must stay byte-identical to the pre-scale behaviour (including a window the user
      // dragged partly off screen), so only the GUI Scale setting introduces this correction.
      if (scale != 1.0F) {
         if (frameWidth > 0.0F && scaledWidth <= frameWidth) {
            left = Math.max(0.0F, Math.min(left, frameWidth - scaledWidth));
         }
         if (frameHeight > 0.0F && scaledHeight <= frameHeight) {
            top = Math.max(0.0F, Math.min(top, frameHeight - scaledHeight));
         }
      }

      return new ScaleTransform(left, top, scale);
   }

   /** Window-local pixel x -> on-screen framebuffer x: the forward transform rendering applies. */
   public final float toScreenX(float localX) {
      ScaleTransform transform = this.getScaleTransform();
      return transform.left + localX * transform.scale;
   }

   /** Window-local pixel y -> on-screen framebuffer y: the forward transform rendering applies. */
   public final float toScreenY(float localY) {
      ScaleTransform transform = this.getScaleTransform();
      return transform.top + localY * transform.scale;
   }

   /**
    * Effective on-screen scale currently applied to this window. Clip rects need it to convert
    * window-local sizes (which the layout produces) into the framebuffer pixel sizes glScissor expects.
    */
   public final float getContentScale() {
      return this.getScaleTransform().scale;
   }

   /**
    * On-screen framebuffer x -> window-local pixel x: the exact inverse of {@link #toScreenX}, used by
    * every mouse handler so hit testing happens in the same space the drawing uses.
    */
   public final int toLocalX(float screenX) {
      ScaleTransform transform = this.getScaleTransform();
      return Math.round((screenX - transform.left) / transform.scale);
   }

   /** On-screen framebuffer y -> window-local pixel y: the exact inverse of {@link #toScreenY}. */
   public final int toLocalY(float screenY) {
      ScaleTransform transform = this.getScaleTransform();
      return Math.round((screenY - transform.top) / transform.scale);
   }

   public void setHeight(int n) {
      IntegerProperty height2 = this.height;
      Integer num = n;
      height2.setValue(num);
   }

   public void setY(int n) {
      IntegerProperty integerProperty = this.y;
      Integer num = n;
      integerProperty.setValue(num);
   }

   public boolean isMouseWithinBounds(int n, int n2, int n3, int n4, int n5, int n6) {
      return n5 >= n && n6 >= n2 && n5 < n3 && n6 < n4 && this.isVisible();
   }

   public void setX(int n) {
      IntegerProperty integerProperty = this.x;
      Integer num = n;
      integerProperty.setValue(num);
   }

   public boolean isVisible() {
      return (Boolean)this.visible.getValue();
   }

   public int getWindowHeight() {
      return (Integer)this.windowHeight.getValue();
   }

   public boolean isFullScreen() {
      return (Boolean)this.fullScreen.getValue();
   }

   protected boolean renderDefaultTitleBar() {
      return true;
   }

   protected boolean usesPanelBlurLayout() {
      return false;
   }

   protected int getDragBarHeight() {
      return 34;
   }

   protected boolean isOverHeaderInteractiveControl(int localX, int localY) {
      return localX >= this.getWidth() - 138 && localY < 34;
   }

   public int getWindowX() {
      return (Integer)this.windowX.getValue();
   }

   public void keyTyped(char typedChar, int keyCode) throws IOException {
   }

   public void drawWindow(int n, int n2, float f) {
      if (this.isVisible()) {
         this.openAnim = UiAnimation.smooth(this.openAnim, 1.0F, 18.0F);
         // Overlay envelope. Sampled as a local rather than multiplied into every colour call so the whole
         // chrome fades with one value, and so the shell is skipped outright once it is invisible: drawing a
         // fully transparent shell would still cost the blur pass and - worse - would leave the text and the
         // vector icons (which fade through their own ARGB, not through the shader) painted on nothing.
         float overlay = screenTransition();
         if (overlay <= 0.004F) {
            return;
         }

         ScaledResolution sr = new ScaledResolution(mc);
         // Raw on-screen mouse: dragging and resizing follow the cursor 1:1 in screen pixels, which is
         // what the user expects whatever the GUI Scale setting is.
         float screenMouseX = (float)n * (float)sr.getScaleFactor();
         float screenMouseY = (float)n2 * (float)sr.getScaleFactor();
         GL11.glPushMatrix();
         GL11.glScaled(1.0 / (double)sr.getScaleFactor(), 1.0 / (double)sr.getScaleFactor(), 1.0);
         GL11.glPushMatrix();
         if (this.mouseDragging && !this.isFullScreen()) {
            if (this.draggingTitleBar) {
               // A centre-anchored scale moves the shell one screen pixel per unit of X, so plain
               // screen-space deltas track the cursor exactly (identical to the historical maths at
               // scale 1.0, where screenMouseX == the old framebuffer mouse and the scale is 1).
               this.setX(Math.round((float)this.dragStartWindowX + (screenMouseX - (float)this.dragStartMouseX)));
               this.setY(Math.round((float)this.dragStartWindowY + (screenMouseY - (float)this.dragStartMouseY)));
               this.setWindowX(this.getX());
               this.setWindowY(this.getY());
            }

            if (this.resizing) {
               // The on-screen right/bottom edge has to follow the cursor. Solving
               // left + width*scale == mouse for the centre-anchored shell gives
               // width = 2 * (mouse - X) / (1 + scale), which collapses to the historical mouse - X
               // at scale 1.0.
               float inverseSpan = 2.0F / (1.0F + this.getContentScale());
               this.setWidth(Math.max(550, Math.round((screenMouseX - (float)this.dragStartWindowX) * inverseSpan)));
               this.setHeight(Math.max(250, Math.round((screenMouseY - (float)this.dragStartWindowY) * inverseSpan)));
            }
         }

         // Resolved AFTER the drag/resize update so the shell and its content share the geometry of
         // this frame instead of tearing apart by one frame while the window is being moved.
         ScaleTransform st = this.getScaleTransform();
         // Inverse of the render transform: from here down everything draws in window-local pixels, so
         // the hover hit tests below must be fed the inverse-mapped mouse, not the raw screen mouse.
         int localMouseX = this.toLocalX(screenMouseX);
         int localMouseY = this.toLocalY(screenMouseY);

         ClickGui clickGui = ClientManagers.moduleManager.clickGui;
         boolean windowBlur = clickGui.isWindowBlurActive();
         n = localMouseX;
         n2 = localMouseY;
         // Progressive scale, applied INSIDE the transform this window already uses for both drawing and
         // clipping (st.left/st.scale, the same values toScreenX/toScreenY - and therefore every glScissor
         // rect in MainClickGuiWindow - are derived from).
         //
         // WHY it is a pure matrix here rather than a change to getScaleTransform(): that transform is
         // simultaneously the render transform, the mouse inverse map and the clip-rect source. Feeding the
         // animation into it would move the shell's placement (scale is applied about the framebuffer centre
         // and slid back on screen), so the shell would drift ~20 px while its own clip rects followed a
         // different path. Composing the zoom about the shell's own centre leaves st.left/st.scale untouched,
         // so scissor rect and drawn content stay on exactly the same transform - the bug class this project
         // already hit when clip rects were computed from final geometry under a mid-transition scale. It also
         // keeps toLocalX/toLocalY exact: the zoom is a visual-only inset, and geometry is restored to exactly
         // 1.0F the frame the envelope settles, so no surviving scale can offset hit testing.
         GL11.glPushMatrix();
         // GUI Scale: place the shell's scaled top-left corner on screen, then scale so that the
         // window-local drawing below lands exactly where toLocalX/toLocalY expect it.
         GL11.glTranslatef(st.left, st.top, 0.0F);
         GL11.glScaled((double)st.scale, (double)st.scale, 1.0);
         this.applyTransitionZoom();
         BlurShadowManager.enableOneShotPass(false, clickGui.clickGui.isShadowActive());
         int colorInt = clickGui.backgroundColor.getColorInt((byte)0);
         BlurShadowManager.submitRenderTask(false, shaderRenderPass -> this.renderBackgroundPass(colorInt, clickGui, shaderRenderPass));
         int colorInt2 = clickGui.headerColor.getColorInt((byte)0);
         if (!this.isFullScreen()) {
            // Grip and separators fade through their own ARGB: they are raw tessellator draws, which bypass
            // the shader's alpha path, so each one is scaled explicitly by the overlay envelope.
            int gripColor = fadedArgb(UiTheme.GLASS_BORDER);
            GlassRenderer.drawSeparator((float)(this.getWidth() - 13), (float)(this.getHeight() - 4), (float)(this.getWidth() - 4), (float)(this.getHeight() - 13), 1.4F, gripColor);
            GlassRenderer.drawSeparator((float)(this.getWidth() - 8), (float)(this.getHeight() - 4), (float)(this.getWidth() - 4), (float)(this.getHeight() - 8), 1.4F, gripColor);
         }

         float width3 = (float)this.getWidth();
         float f8 = this.isFullScreen() ? 0.0F : (float)this.cornerRadius;
         if (this.renderDefaultTitleBar()) {
            if (!windowBlur) {
               // Title bar, not sidebar: use the unscaled level-1 tint so lowering Sidebar Opacity
               // never dims this header.
               int hdrFill = fadedArgb(UiTheme.glassTitleBarTintArgb());
               GlassRenderer.drawRoundedGradientBox(0.0F, 0.0F, width3, 34.0F, f8, f8, 0.0F, 0.0F, hdrFill, UiTheme.lerpArgb(hdrFill, 0xFF0A0A14, 0.25F), 0, 0.0F, 0.08F);
            }
            GlassRenderer.drawSeparator(0.0F, 34.0F, width3, 34.0F, UiTheme.SEPARATOR_THICKNESS, fadedArgb(UiTheme.GLASS_DIVIDER));

            int capsuleW = 84;
            int capsuleX = this.getWidth() - 10 - capsuleW;
            GlassRenderer.drawRecessedWell((float)capsuleX, 5.0F, (float)capsuleW, 24.0F, UiTheme.CARD_RADIUS, 0.0F, 0.0F, (float)n, (float)n2, UiTheme.accentColor(), overlay);

            int closeX = capsuleX + 58;
            int maxX = capsuleX + 31;
            int minX = capsuleX + 4;
            boolean closeHover = this.isMouseOver(closeX, 6, 22, 22, n, n2);
            boolean maxHover = this.isMouseOver(maxX, 6, 22, 22, n, n2);
            boolean minHover = this.isMouseOver(minX, 6, 22, 22, n, n2);

            float cHov = UiAnimation.smoothState("cw_c_" + this.title, closeHover ? 1.0F : 0.0F, 18.0F);
            float xHov = UiAnimation.smoothState("cw_x_" + this.title, maxHover ? 1.0F : 0.0F, 18.0F);
            float mHov = UiAnimation.smoothState("cw_m_" + this.title, minHover ? 1.0F : 0.0F, 18.0F);

            if (cHov > 0.02F) {
               GlassRenderer.drawRoundedBox((float)closeX, 6.0F, 22.0F, 22.0F, UiTheme.CONTROL_RADIUS, UiTheme.withAlpha(UiTheme.DANGER_ACCENT, Math.round(215.0F * cHov * overlay)), UiTheme.withAlpha(0xFFFFD0D2, Math.round(160.0F * cHov * overlay)), 1.0F);
            }
            if (xHov > 0.02F) {
               GlassRenderer.drawRoundedBox((float)maxX, 6.0F, 22.0F, 22.0F, UiTheme.CONTROL_RADIUS, UiTheme.withAlpha(UiTheme.accentColor(), Math.round(85.0F * xHov * overlay)), UiTheme.withAlpha(UiTheme.accentSoftGlow(), Math.round(145.0F * xHov * overlay)), 1.0F);
            }
            if (mHov > 0.02F) {
               GlassRenderer.drawRoundedBox((float)minX, 6.0F, 22.0F, 22.0F, UiTheme.CONTROL_RADIUS, UiTheme.withAlpha(UiTheme.accentColor(), Math.round(85.0F * mHov * overlay)), UiTheme.withAlpha(UiTheme.accentSoftGlow(), Math.round(145.0F * mHov * overlay)), 1.0F);
            }

            GlassRenderer.drawVectorClose((float)(closeX + 11), 17.0F, 9.0F, fadedArgb(closeHover ? UiTheme.TEXT_PRIMARY : UiTheme.secondaryTextColor()));
            GlassRenderer.drawVectorMaximize((float)(maxX + 11), 17.0F, 8.5F, fadedArgb(maxHover ? UiTheme.TEXT_PRIMARY : UiTheme.secondaryTextColor()));
            GlassRenderer.drawVectorMinimize((float)(minX + 11), 17.0F, 9.0F, fadedArgb(minHover ? UiTheme.TEXT_PRIMARY : UiTheme.secondaryTextColor()));

            this.titleFont = FontManager.interBold;
            this.titleFont.drawVerticallyCenteredString(this.title, 14.0, 0.0, 34.0, fadedArgb(UiTheme.TEXT_PRIMARY));
         }
         this.lastMouseX = n;
         this.lastMouseY = n2;
         // Unwind in the exact reverse order of the pushes above, and pop EXACTLY as many matrices as were
         // pushed: three unconditionally (screen-space scale, window-local scale, shell transform) plus the
         // transition zoom when it is active.
         //
         // WHY the counts are spelled out per push instead of a fixed "two pops" tail: this method used to push
         // twice and pop twice, and the shell placement transform that the GUI Scale work introduced added a
         // third push without a third pop. The consequence is not a misplaced frame: it leaks one matrix per
         // window per frame, so the fixed-function modelview stack (depth 32) fills in ~16 frames and reports
         // GL_STACK_OVERFLOW (1283) on pushes for the rest of the session. From then on a failed push makes the
         // following glScaled() write into the PREVIOUS matrix, so the glued scale compounds every frame and the
         // overlay's content collapses to nothing while its backdrop - drawn through the deferred blur pass in a
         // different matrix state - keeps painting exactly as before. That is the "empty glass rectangle that
         // closes itself" report, and it is why the balance must stay exact on every path out of this method.
         if (this.transitionZoomPushed) {
            // The zoom is the innermost push (applyTransitionZoom), so it is popped first. Popped here - the
            // single exit of the drawing block - rather than inside applyTransitionZoom so the push/pop pair
            // always encloses the whole shell (blur pass included).
            this.transitionZoomPushed = false;
            GL11.glPopMatrix();
         }
         GL11.glPopMatrix();  // shell placement transform (getScaleTransform: st.left/st.top/st.scale)
         GL11.glPopMatrix();  // window-local drawing transform
         GL11.glPopMatrix();  // full-screen transform (1 / guiScaleFactor)
      }
   }

   /**
    * Applies the overlay's progressive scale to this shell, about its own centre, on top of the transform the
    * caller just pushed.
    *
    * <p>Called from inside the window transform (after translate + {@code st.scale}). Two properties matter:
    *
    * <ul>
    *   <li><b>It never touches the geometry.</b> {@code st.left}/{@code st.scale} - the values rendering, the
    *       mouse inverse ({@code toLocalX}) and every {@code glScissor} rect in MainClickGuiWindow all derive
    *       from - keep describing the settled shell, so a clip rect can never end up computed for one scale
    *       while the content is drawn at another. This is exactly the failure mode the brief warns about, and
    *       it is why the zoom is a pure matrix rather than a change to {@code getScaleTransform()}.</li>
    *   <li><b>It is a no-op whenever the overlay is settled</b> ({@code screenTransition() == 1.0F}): nothing is
    *       pushed at all in that case, so ordinary frames keep the historical matrix stack and the exact
    *       historical hit testing.</li>
    * </ul>
    *
    * <p>The matrix it may push is popped by the matching pop at the end of {@link #drawWindow}, which runs on
    * every path (there is no early return between the two), so GL state stays balanced.
    */
   private void applyTransitionZoom() {
      float t = screenTransition();
      if (t >= 0.999F) {
         return;
      }

      // 3.5% of zoom at the closed end of the envelope: enough to read as the shell settling into place,
// small enough that no text reflows. This is the ONLY overlay-wide scale - ClickGuiScreen has none, so
// every window here grows with the same curve and nothing has to be kept in step with a second transform.
      float scale = 0.965F + (1.0F - 0.965F) * UiAnimation.easeOutCubic(t);
      float halfW = (float)this.getWidth() * 0.5F;
      float halfH = (float)this.getHeight() * 0.5F;
      this.transitionZoomPushed = true;
      GL11.glPushMatrix();
      GL11.glTranslatef(halfW, halfH, 0.0F);
      GL11.glScaled((double)scale, (double)scale, 1.0);
      GL11.glTranslatef(-halfW, -halfH, 0.0F);
   }

   public ClickGuiWindow(String str, int n, int n2, int n3, int n4) {
       super(ClientSettingsConfigFile.abstractFeature, str);
      this.lastMouseX = 0;
      this.lastMouseY = 0;
      this.dragStartMouseX = 0;
      this.dragStartMouseY = 0;
      this.dragStartWindowX = 0;
      this.dragStartWindowY = 0;
      this.x = new IntegerProperty(this, "X", 0);
      this.y = new IntegerProperty(this, "Y", 0);
      this.width = new IntegerProperty(this, "Width", 0);
      this.height = new IntegerProperty(this, "Height", 0);
      this.windowWidth = new IntegerProperty(this, "WindowWidth", 0);
      this.windowHeight = new IntegerProperty(this, "WindowHeight", 0);
      this.windowX = new IntegerProperty(this, "WindowX", 0);
      this.windowY = new IntegerProperty(this, "WindowY", 0);
      this.visible = new BooleanProperty(this, "Visible", false);
      this.fullScreen = new BooleanProperty(this, "FullScreen", false);
      this.mouseDragging = false;
      this.draggingTitleBar = false;
      this.resizing = false;
      this.closed = false;
      this.title = str;
      this.setWindowWidth(n3);
      this.setWindowHeight(n4);
      this.defaultWidth = n3;
      this.defaultHeight = n4;
      this.setWidth(this.getWindowWidth());
      this.setHeight(this.getWindowHeight());
      this.cornerRadius = (int)UiTheme.WINDOW_RADIUS;
      this.setWindowX((int)((float)n - (float)n3 / 2.0F));
      this.setWindowY((int)((float)n2 - (float)n4 / 2.0F));
      this.setX(this.getWindowX());
      this.setY(this.getWindowY());
      this.setFullScreen(false);
      this.titleFont = FontManager.interBold;
      this.openWindow();
   }

   public void setWidth(int n) {
      IntegerProperty width2 = this.width;
      Integer num = n;
      width2.setValue(num);
   }

   public int getY() {
      return (Integer)this.y.getValue();
   }

   public void setWindowWidth(int n) {
      IntegerProperty windowWidth2 = this.windowWidth;
      Integer num = n;
      windowWidth2.setValue(num);
   }

   public int getWindowWidth() {
      return (Integer)this.windowWidth.getValue();
   }

   public int getHeight() {
      return (Integer)this.height.getValue();
   }

   public int getX() {
      return (Integer)this.x.getValue();
   }

   public void closeWindow() {
      this.setVisible(false);
      this.setFullScreen(false);
      this.setWidth(this.getWindowWidth());
      this.setHeight(this.getWindowHeight());
      this.setX(this.getWindowX());
      this.setY(this.getWindowY());
   }

   public boolean isMouseOver(int n, int n2, int n3, int n4, int n5, int n6) {
      return n5 >= n && n6 >= n2 && n5 < n + n3 && n6 < n2 + n4 && this.isVisible();
   }

   protected float getOpenProgress() {
      // The window's own open envelope (smoothstep, delta-driven, restarted by openWindow()/setVisible) is
      // multiplied by the overlay envelope. At rest both are exactly 1.0F, so every normal frame is unchanged;
      // during a transition the product fades the shell - and everything on it that already multiplies this
      // value in - with the panel instead of popping out at the end.
      return UiAnimation.easeOutCubic(this.openAnim) * screenTransition();
   }

   private void renderBackgroundPass(int n, ClickGui clickGui, ShaderRenderPass shaderRenderPass) {
      float width2 = (float)this.getWidth();
      float height2 = (float)this.getHeight();
      float radius = this.isFullScreen() ? 0.0F : (float)this.cornerRadius;
      boolean outlineOn = clickGui.outline.getValue();
      int borderArgb = outlineOn ? UiTheme.glassBorderColor() : 0;
      float borderW = outlineOn ? UiTheme.BORDER_THICKNESS : 0.0F;
      float openAlpha = this.getOpenProgress();
      if (shaderRenderPass != ShaderRenderPass.SHADOW) {
         if (clickGui.isWindowBlurActive()) {
            GlassRenderer.drawLiquidGlassSurface(
               0.0F,
               0.0F,
               width2,
               height2,
               radius,
               radius,
               radius,
               radius,
               clickGui.getBlurRadius() * openAlpha,
               borderW,
               borderArgb,
               openAlpha
            );
         } else if (clickGui.isPanelBlurActive() && this.usesPanelBlurLayout()) {
            if (outlineOn) {
               GlassRenderer.drawRoundedOutline(0.0F, 0.0F, width2, height2, radius, borderW, UiTheme.scaleAlpha(borderArgb, openAlpha));
            }
         } else {
            GlassRenderer.drawLiquidGlassSurface(
               0.0F,
               0.0F,
               width2,
               height2,
               radius,
               radius,
               radius,
               radius,
               0.0F,
               borderW,
               borderArgb,
               openAlpha
            );
         }
      } else {
         int n3 = clickGui.clickGui.resolveShadowColor(ColorUtil.resolvePassColor(shaderRenderPass, n));
         // The drop shadow is part of the shell, so it rides the same envelope: at full strength it
         // survived the whole close and left a soft rounded-rectangle ghost of the window for the last
         // frames of the fade, after the glass it belongs to had already gone.
         RenderUtil.drawRoundedRect(0.0F, 0.0F, width2, height2, radius, (byte)1, UiTheme.scaleAlpha(n3, openAlpha));
      }
   }

   public void setWindowY(int n) {
      IntegerProperty windowY2 = this.windowY;
      Integer num = n;
      windowY2.setValue(num);
   }

   public void setWindowX(int n) {
      IntegerProperty windowX2 = this.windowX;
      Integer num = n;
      windowX2.setValue(num);
   }

   public void setWindowHeight(int n) {
      IntegerProperty windowHeight2 = this.windowHeight;
      Integer num = n;
      windowHeight2.setValue(num);
   }

   public int getWindowY() {
      return (Integer)this.windowY.getValue();
   }

   public void setVisible(boolean flag) {
      BooleanProperty visible2 = this.visible;
      boolean wasVisible = Boolean.TRUE.equals(visible2.getValue());
      Boolean bool = flag;
      visible2.setValue(bool);
      if (flag && !wasVisible) {
         this.openAnim = 0.0F;
      }
   }

   public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      ScaledResolution sr = new ScaledResolution(mc);
      ScaleTransform st = this.getScaleTransform();
      // Raw on-screen mouse for the drag/resize maths (see drawWindow).
      this.dragStartMouseX = mouseX * sr.getScaleFactor();
      this.dragStartMouseY = mouseY * sr.getScaleFactor();
      // Hit testing uses the inverse of the render transform, so a click lands on the widget that was
      // drawn under the cursor at any GUI Scale.
      int localMouseX = this.toLocalX((float)this.dragStartMouseX);
      int localMouseY = this.toLocalY((float)this.dragStartMouseY);
      int dragBarHeight = this.getDragBarHeight();
      // The drag bar and the resize grip live in window-local drawing coordinates, which is exactly the
      // space toLocalX/toLocalY return. (Equivalent to the historical absolute-rect test at scale 1.)
      this.draggingTitleBar = this.isMouseOver(0, 0, this.getWidth(), dragBarHeight, localMouseX, localMouseY)
         && !this.isOverHeaderInteractiveControl(localMouseX, localMouseY);
      this.resizing = this.isMouseOver(this.getWidth() - 15, this.getHeight() - 15, (byte)15, (byte)15, localMouseX, localMouseY);
      this.dragStartWindowX = this.getX();
      this.dragStartWindowY = this.getY();
      if (!this.renderDefaultTitleBar()) {
         return;
      }

      GL11.glPushMatrix();
      try {
         GL11.glScaled(1.0 / (double)sr.getScaleFactor(), 1.0 / (double)sr.getScaleFactor(), 1.0);
         GL11.glPushMatrix();
         try {
            int localX = localMouseX;
            int localY = localMouseY;
            GL11.glTranslatef(st.left, st.top, 0.0F);
            GL11.glScaled((double)st.scale, (double)st.scale, 1.0);
            int capsuleW = 84;
            int capsuleX = this.getWidth() - 10 - capsuleW;
            int closeX = capsuleX + 58;
            int maxX = capsuleX + 31;
            int minX = capsuleX + 4;
            if (this.isMouseOver(closeX, 5, 24, 24, localX, localY)) {
               // The title bar X dismisses the ClickGUI, so it has to travel through the overlay's animated
               // lifecycle instead of hiding this window on the press frame. Routing it through the screen
               // keeps the open/close fade symmetric for the two ways users actually close the GUI (this
               // button and ESC), and it is a no-op when another screen owns the view.
               ClickGuiScreen.requestCloseFromWindowChrome();
               ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE);
            } else if (this.isMouseOver(maxX, 5, 24, 24, localX, localY)) {
               this.toggleFullScreenMode();
               ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE);
            } else if (this.isMouseOver(minX, 5, 24, 24, localX, localY)) {
               // Minimize still collapses just this shell, so this one must NOT start the screen close. The
               // shell is brought back by the next presentation (see ClickGuiScreen.initGui), which is what
               // replaced the centre dock as the way to restore a minimized window.
               this.closeWindow();
               this.closed = true;
               ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE);
            }
         } finally {
            GL11.glPopMatrix();
         }
      } finally {
         GL11.glPopMatrix();
      }
   }

   public void handleKeyboardInput() throws IOException {
   }

   public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
      this.mouseDragging = true;
   }

   public void setFullScreen(boolean flag) {
      BooleanProperty fullScreen2 = this.fullScreen;
      Boolean bool = flag;
      fullScreen2.setValue(bool);
   }

   public int getWidth() {
      return (Integer)this.width.getValue();
   }

   public void resetWindow(String str) {
      this.setX((byte)0);
      this.setY((byte)0);
      this.setWidth((byte)0);
      this.setHeight((byte)0);
      this.mouseDragging = false;
      this.draggingTitleBar = false;
      this.resizing = false;
      this.setFullScreen(false);
      this.setVisible(false);
      this.closed = false;
      this.title = str;
      float framebufferWidth = (float)mc.getFramebuffer().framebufferWidth;
      float framebufferHeight = (float)mc.getFramebuffer().framebufferHeight;
      int n = (int)(framebufferWidth / 2.0F);
      int n2 = (int)(framebufferHeight / 2.0F);
      this.setWindowWidth(n);
      this.setWindowHeight(n2);
      this.setWidth(this.defaultWidth);
      this.setHeight(this.defaultHeight);
      this.cornerRadius = (int)UiTheme.WINDOW_RADIUS;
      this.setWindowX((int)((float)n - (float)this.getWidth() / 2.0F));
      this.setWindowY((int)((float)n2 - (float)this.getHeight() / 2.0F));
      this.setX(this.getWindowX());
      this.setY(this.getWindowY());
      this.setFullScreen(false);
      this.titleFont = FontManager.interBold;
      this.openWindow();
   }

   public void mouseReleased(int mouseX, int mouseY, int state) {
      this.mouseDragging = false;
      this.draggingTitleBar = false;
      if (this.resizing) {
         this.setWindowWidth(this.getWidth());
         this.setWindowHeight(this.getHeight());
      }

      this.resizing = false;
   }

   public void openWindow() {
      this.openAnim = 0.0F;
      this.setVisible(true);
   }

   public void toggleWindow(String str) {
      if (this.closed) {
         this.resetWindow(str);
      } else {
         this.setVisible(!this.isVisible());
      }
   }

   public void toggleFullScreenMode() {
      if (this.isFullScreen()) {
         this.setWidth(this.getWindowWidth());
         this.setHeight(this.getWindowHeight());
         this.setX(this.getWindowX());
         this.setY(this.getWindowY());
      } else {
         this.setWidth(mc.getFramebuffer().framebufferWidth);
         this.setHeight(mc.getFramebuffer().framebufferHeight);
         this.setX((byte)0);
         this.setY((byte)0);
      }
      this.setFullScreen(!this.isFullScreen());
   }




}
