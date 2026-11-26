package net.augustus.clickgui;

import net.augustus.Augustus;
import net.augustus.ClientManagers;
import net.augustus.clickgui.components.TextFieldComponent;
import net.augustus.config.ClipboardConfigCodec;
import net.augustus.events.listeners.ClickGuiListener;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.font.TextWrapper;
import net.augustus.modules.Categorys;
import net.augustus.modules.EntityRotationModule;
import net.augustus.modules.Module;
import net.augustus.modules.RotationModule;
import net.augustus.modules.misc.ClientAntiCheat;
import net.augustus.render.RenderUtil;
import net.augustus.settings.Setting;
import net.augustus.settings.SettingChangeListener;
import net.augustus.settings.SettingGroup;
import net.augustus.settings.SettingVisibilityRegistry;
import net.augustus.settings.property.StringProperty;
import net.augustus.ui.config.AbstractConfigListItem;
import net.augustus.ui.config.ConfigSystemPanel;
import net.augustus.ui.modern.components.ModernButton;
import net.augustus.ui.modern.components.ModernSettingsPanel;
import net.augustus.ui.modern.components.ModernTabs;
import net.augustus.ui.modern.components.ModernTextInput;
import net.augustus.ui.modern.components.ModernToggleSwitch;
import net.augustus.ui.modern.popup.PopupHost;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.ui.widgets.SmoothScrollBar;
import net.augustus.utils.ChatUtil;
import net.augustus.utils.TimeHelper;
import net.augustus.utils.audio.ClientSounds;
import net.augustus.utils.input.KeyboardInputUtil;
import net.augustus.utils.render.BlurComponent;
import net.augustus.utils.render.ColorUtil;
import net.augustus.utils.render.ScrollFadeCompositor;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public class MainClickGuiWindow extends ClickGuiWindow {
   private enum SidebarTab {
      MODULES,
      ACTIVE,
      CONFIGS,
      KEYBINDS
   }

   private enum ModuleViewMode {
      LIST("List view", LucideIcon.LIST),
      COMPACT("Compact list", LucideIcon.COMPACT_LIST),
      GRID("Card grid", LucideIcon.LAYOUT_GRID);

      final String label;
      final LucideIcon icon;

      ModuleViewMode(String label, LucideIcon icon) {
         this.label = label;
         this.icon = icon;
      }
   }

   private static final int SIDEBAR_WIDTH = 228;
   private static final int HEADER_HEIGHT = 54;
   private static final int CONTENT_PADDING = 18;
   private static final int CATEGORY_BAR_Y = 64;
   private static final int CATEGORY_BAR_HEIGHT = 30;
   private static final int GRID_TOP_Y = 104;
   private static final int SETTINGS_OFFSET_X = SIDEBAR_WIDTH + CONTENT_PADDING;
   private static final int SETTINGS_OFFSET_Y = 66;
   private static final int FOOTER_HEIGHT = 72;

   // Rail geometry (the Prestige-style navigation column INSIDE the one shell). These numbers are
   // content pixels like every other layout constant in the file, so they zoom with the GUI Scale.
   private static final int SIDEBAR_ITEM_H = 26;
   private static final int SIDEBAR_ITEM_STEP = 28;
   private static final int SIDEBAR_GROUP1_TOP = 78;
   private static final int RAIL_ROW_X = 12;
   private static final int RAIL_ROW_W = SIDEBAR_WIDTH - 24;
   private static final int RAIL_LABEL_GAP = 16;
   private static final int RAIL_GROUP_GAP = 14;
   private static final int RAIL_GROUP_LABEL_ADVANCE = 20;
   /** Vertical band of the rail that scrolls when the rows do not fit (footer stays pinned). */
   private static final int RAIL_VIEW_TOP = 58;

   private static final Queue PENDING_SETTING_LISTENERS = new ConcurrentLinkedQueue();

   public int tooltipX;
   public int tooltipY;
   public SettingGroup contextMenuSetting;
   public Module selectedModule;
   public int contextMenuY;
   public int contextMenuX;

   private CategoryTabButton contextMenuCategory;
   private boolean hoveringModuleContextMenu;
   private boolean hoveringCategoryContextMenu;
   private final Map<Module, float[]> moduleCardAnims = new IdentityHashMap<>();
   private final Map<Module, ModernToggleSwitch> moduleListSwitches = new IdentityHashMap<>();
   private ModuleViewMode moduleViewMode = ModuleViewMode.LIST;
   private StringProperty moduleViewModeProperty;
   private final float[] viewModeBtnX = new float[3];
   private final float[] viewModeBtnW = new float[3];
   private float viewModeTrayY = 13.0F;
   private float viewModeTrayH = 28.0F;
   private boolean viewModeBarVisible = false;
   private ModuleViewMode hoveredViewMode = null;
   private CategoryTabButton selectedCategory;
   private final TimeHelper timeHelper;
   private CustomFontUtil moduleFont;
   private ModuleSettingsPanel settingsPanel;
   private Module hoveredModule;
   private int moduleScrollOffset;
   private Module contextMenuModule;
   private final ArrayList categoryTabs;
   private int categoryScrollOffset;
   private SmoothScrollBar categoryScrollBar;
   private SmoothScrollBar moduleScrollBar;
   private SmoothScrollBar configScrollBar;
   private int configScrollOffset;
   private final StringProperty selectedModuleProperty;
   private final StringProperty selectedCategoryProperty;

   private SidebarTab activeSidebarTab = SidebarTab.MODULES;
   private boolean allCategoriesSelected = true;
   private boolean viewingModuleSettings = false;
   private Module lastViewedModule = null;
   private Module keybindListeningModule = null;

   // Rail scroll state (content pixels) + the single sliding active pill behind the rail rows.
   private final SmoothScrollBar railScrollBar = new SmoothScrollBar();
   private int railScrollOffset;
   private int railContentTop;
   private int railContentBottom;
   private int railGroup2LabelY = SIDEBAR_GROUP1_TOP;
   private float railPillX;
   private float railPillY;
   private float railPillW = RAIL_ROW_W;
   private float railPillH = SIDEBAR_ITEM_H;
   private float railPillVis;
   private boolean railPillInit;

   private String searchQuery = "";
   private boolean searchFocused = false;
   private long searchCursorTime = 0L;
   private final List<Module> filteredModules = new ArrayList<>();
   private final PopupHost popupHost = new PopupHost();
   private final ModernTextInput searchInput = new ModernTextInput();
   private final ModernButton headerToggleButton = new ModernButton();
   private final ModernButton headerImportButton = new ModernButton();
   private final ModernButton headerExportButton = new ModernButton();
   private final ModernButton footerClickGuiButton = new ModernButton();
   private final ModernButton footerColorButton = new ModernButton();
   private float contentTransitionAnim = 1.0F;
   private float contentTransitionDir = 1.0F;
   private final ModernButton[] configBarButtons = new ModernButton[]{
      new ModernButton(), new ModernButton(), new ModernButton(),
      new ModernButton(), new ModernButton(), new ModernButton()
   };

   public PopupHost getPopupHost() {
      return this.popupHost;
   }

   public static PopupHost activePopupHost() {
      if (Augustus.getInstance() != null && Augustus.getInstance().getClickGui() != null && Augustus.getInstance().getClickGui().getMainWindow() != null) {
         return Augustus.getInstance().getClickGui().getMainWindow().getPopupHost();
      }
      return null;
   }

   public static MainClickGuiWindow activeMainWindow() {
      if (Augustus.getInstance() != null && Augustus.getInstance().getClickGui() != null) {
         return Augustus.getInstance().getClickGui().getMainWindow();
      }
      return null;
   }

   public MainClickGuiWindow(String str, int n, int n2, int n3, int n4) {
      super(str, n, n2, n3, n4);
      this.cornerRadius = 12;
      this.categoryTabs = new ArrayList();
      this.moduleScrollBar = new SmoothScrollBar();
      this.categoryScrollBar = new SmoothScrollBar();
      this.configScrollBar = new SmoothScrollBar();
      this.settingsPanel = new ModuleSettingsPanel();
      this.moduleScrollOffset = 0;
      this.categoryScrollOffset = 0;
      this.configScrollOffset = 0;
      this.timeHelper = new TimeHelper();
      this.hoveredModule = null;
      this.contextMenuModule = null;

      StringProperty stringProperty2 = (StringProperty)new StringProperty(this, "SelectedModule", "").withValueSupplier(() -> this.getSelectedModuleName());
      Consumer consumer = obj -> this.setSelectedModule(ClientManagers.moduleManager.getModuleByName((String)obj));
      this.selectedModuleProperty = (StringProperty)stringProperty2.withOnValueChanged(consumer);

      StringProperty stringProperty4 = (StringProperty)new StringProperty(this, "SelectedCategory", "").withValueSupplier(() -> this.getSelectedCategory().getCategorys().name());
      Consumer consumer2 = obj2 -> {
         for (CategoryTabButton categoryTabButton : (Iterable<CategoryTabButton>)(this.getCategoryTabs())) {
            if (categoryTabButton.getCategorys().name().equals(obj2)) {
               this.setSelectedCategory(categoryTabButton);
               break;
            }
         }
      };
      this.selectedCategoryProperty = (StringProperty)stringProperty4.withOnValueChanged(consumer2);

      StringProperty viewModeProp = (StringProperty)new StringProperty(this, "ModuleViewMode", ModuleViewMode.LIST.name())
         .withValueSupplier(() -> this.moduleViewMode.name());
      Consumer viewModeConsumer = val -> {
         if (val instanceof String) {
            try {
               this.moduleViewMode = ModuleViewMode.valueOf(((String)val).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
               this.moduleViewMode = ModuleViewMode.LIST;
            }
         }
      };
      this.moduleViewModeProperty = (StringProperty)viewModeProp.withOnValueChanged(viewModeConsumer);

      this.hoveringModuleContextMenu = false;
      this.hoveringCategoryContextMenu = false;
      this.moduleFont = FontManager.interSemiBold;

      int n5 = 0;
      for (Categorys categorys : Categorys.values()) {
         if (!categorys.hidden) {
            CategoryTabButton categoryTabButton2 = new CategoryTabButton(220 + n5, 50, 11, 7, categorys, this.moduleFont);
            this.categoryTabs.add(categoryTabButton2);
            n5 += categoryTabButton2.getTextWidth() + 30;
         }
      }

      this.selectedCategory = (CategoryTabButton)this.categoryTabs.get(0);
      this.selectedModule = (Module)this.selectedCategory.getModules().get(0);
   }

   @Override
   protected boolean renderDefaultTitleBar() {
      return false;
   }

   @Override
   protected boolean usesPanelBlurLayout() {
      return true;
   }

   /**
    * The shell is deliberately NOT scaled by the GUI Scale setting any more: the outer window keeps a
    * fixed size, so the window-level transform stays identity and {@code getScaleTransform()}
    * degenerates to "the shell sits at (X, Y) with scale 1" (left = getX(), top = getY()).
    *
    * <p>WHY: the GUI Scale setting is consumed by {@link #getContentZoom()} instead, which zooms the
    * content layer drawn inside that fixed frame (modules, options, text, icons, controls and the
    * sidebar rail). Folding the factor in here as well would scale the frame itself, which is exactly
    * what the setting must not do.
    */
   @Override
   protected float getContentScaleFactor() {
      return 1.0F;
   }

   /**
    * GUI Scale consumed as an internal content zoom: Small 0.85 / Normal 1.0 / Large 1.25.
    *
    * <p>Single source of truth for the content layer: the render scale pushed in
    * {@link #drawWindow(int, int, float)} and every inverse used for hit testing and for the scissor
    * rects (which glScissor needs in raw framebuffer pixels) are derived from this one value, so they
    * cannot drift apart.
    */
   private float getContentZoom() {
      if (ClientManagers.moduleManager == null || ClientManagers.moduleManager.clickGui == null) {
         return 1.0F;
      }
      float zoom = ClientManagers.moduleManager.clickGui.getTargetGuiScaleFactor();
      if (Float.isNaN(zoom) || zoom <= 0.05F || zoom > 4.0F) {
         // Corrupt/hostile setting: 1:1 instead of collapsing or exploding the content.
         return 1.0F;
      }
      return zoom;
   }

   /*
    * Content-space transform and its exact inverse.
    *
    * The whole ClickGUI layout is authored in "content pixels" (the same numbers as before the zoom
    * existed) and mapped into the fixed shell by ONE scale about the shell's top-left corner, pushed
    * in drawWindow:
    *
    *    shell-local  = content * zoom                                   (the render transform)
    *    framebuffer  = shellOrigin + content * zoom, shellOrigin = (getX(), getY())
    *
    * The viewport is authored in content pixels as well (contentWidth = shellWidth / zoom), so the
    * composition always fills the fixed shell exactly: zooming out reveals MORE rows/cards, zooming in
    * reveals fewer, larger ones, and the overflow is clipped by the shell scissor + scrolled by the
    * existing SmoothScrollBar machinery.
    *
    * Every hit test must invert exactly that transform:
    *
    *    content = (mouse - shellOrigin) / zoom
    *
    * i.e. shellToContentX/Y for the window-local mouse every input path receives, and
    * contentToScreenX/Y for the clip rects pushed to glScissor (which ignores the modelview matrix,
    * so those rects must be computed from the same transform the drawing uses).
    */

   /** Content x -> on-screen framebuffer x: the forward transform, for glScissor rects. */
   public float contentToScreenX(float contentX) {
      return this.getScaleTransform().left + contentX * this.getContentZoom();
   }

   /** Content y -> on-screen framebuffer y: the forward transform, for glScissor rects. */
   public float contentToScreenY(float contentY) {
      return this.getScaleTransform().top + contentY * this.getContentZoom();
   }

   /** Content-space size -> on-screen size, for scissor/fade regions. */
   public float contentSpanToScreen(float contentSpan) {
      return contentSpan * this.getContentZoom();
   }

   /**
    * Clips everything that follows to the fixed outer frame. The zoomed content can only ever be
    * drawn inside it, so this is the clip that guarantees "nothing leaks outside the window" whatever
    * the GUI Scale; at Normal it is the same rect the code used before the zoom existed.
    */
   private void clipToShell() {
      float left = this.contentToScreenX(0.0F);
      float top = this.contentToScreenY(0.0F);
      RenderUtil.scissorRectScaled(left, top, left + (float)this.getWidth(), top + (float)this.getHeight(), 1.0);
   }

   /** Window-local (shell) x -> content x: the exact inverse of the drawWindow scale. */
   public int shellToContentX(float windowLocalX) {
      return Math.round(windowLocalX / this.getContentZoom());
   }

   /** Window-local (shell) y -> content y: the exact inverse of the drawWindow scale. */
   public int shellToContentY(float windowLocalY) {
      return Math.round(windowLocalY / this.getContentZoom());
   }

   /**
    * Content-space viewport width/height: the fixed shell divided by the zoom. The layout is authored
    * in these units, so the composition always fills the frame exactly and the drawing pass and the
    * hit tests share one definition of "how much room there is".
    */
   public int getContentWidth() {
      return Math.max(1, Math.round((float)this.getWidth() / this.getContentZoom()));
   }

   public int getContentHeight() {
      return Math.max(1, Math.round((float)this.getHeight() / this.getContentZoom()));
   }

   public boolean isSearchFocused() {
      return this.searchFocused;
   }

   public boolean isListeningForKeybind() {
      return this.keybindListeningModule != null;
   }

   public boolean shouldConsumeEscapeOrEnter(int keyCode) {
      if (this.popupHost.hasActivePopup()) {
         return true;
      }
      if (keyCode == 1 && (this.keybindListeningModule != null || this.searchFocused || (this.selectedModule != null && this.selectedModule.isBindingKey()))) {
         return true;
      }
      if ((keyCode == 28 || keyCode == 156) && this.searchFocused) {
         return true;
      }
      return (keyCode == 28 || keyCode == 156 || keyCode == 1) && this.settingsPanel.isAnySettingFocused();
   }

   @Override
   protected int getDragBarHeight() {
      // ClickGuiWindow compares this against the window-local (shell) mouse it derived from
      // toLocalY, while HEADER_HEIGHT is a content-space constant. Folding the zoom in keeps the
      // draggable strip exactly as tall as the header that ends up on screen.
      return Math.round((float)HEADER_HEIGHT * this.getContentZoom());
   }

   @Override
   protected boolean isOverHeaderInteractiveControl(int localX, int localY) {
      // Incoming mouse is window-local; the constants below are content-space, so invert the zoom
      // first (identity at Normal).
      int cx = this.shellToContentX((float)localX);
      int cy = this.shellToContentY((float)localY);
      if (cy >= HEADER_HEIGHT) {
         return false;
      }
      // Sidebar brand area can drag; right header interactive controls cannot.
      if (cx >= SIDEBAR_WIDTH) {
         if (cx <= SIDEBAR_WIDTH + 90) {
            return true; // Back / forward buttons
         }
         // Same 420px band as before, measured in content pixels from the content's right edge.
         int contentRightEdge = this.shellToContentX((float)this.getWidth());
         if (cx >= contentRightEdge - 420) {
            return true; // Search box, view mode toggle, quick actions, and window controls
         }
      }
      return false;
   }

   public int getSettingsOffsetX() {
      return SETTINGS_OFFSET_X;
   }

   public int getSettingsOffsetY() {
      return SETTINGS_OFFSET_Y;
   }

   public static void enqueueSettingChangeListener(SettingChangeListener listener) {
      PENDING_SETTING_LISTENERS.add(listener);
   }

   public static void drawTooltip(CustomFontUtil customFontUtil, String str, int n, int n2) {
      MainClickGuiWindow mw = Augustus.getInstance().getClickGui().getMainWindow();
      TooltipRenderer.drawTooltip(customFontUtil, str, n, n2, Math.max(180, mw.getContentWidth() - SETTINGS_OFFSET_X - mw.cornerRadius - 20));
   }

   @Override
   public void drawWindow(int n, int n2, float f) {
      if (!this.isVisible()) {
         return;
      }

      SettingVisibilityRegistry.registerAll();
      ClickGuiListener.postClickGuiUpdate();

      for (Module module : (Iterable<Module>)(ClientManagers.moduleManager.getModules())) {
         if (module instanceof RotationModule) {
            ((RotationModule)module).initVisibilityConditions();
         } else if (module instanceof EntityRotationModule) {
            ((EntityRotationModule)module).initVisibilityConditions();
         }
      }

      // Close transition: with the envelope at zero nothing of this window is visible any more, and
      // drawing the content would cost a full pass just to composite text at alpha 0. Mirrors the
      // guard ClickGuiWindow.drawWindow applies to the shell itself (checked before any matrix is
      // pushed, so the stack stays balanced).
      if (screenTransition() <= 0.004F) {
         return;
      }

      // The shell itself (glass surface, outline, resize grip) is drawn by the base class in
      // window-local pixels and is intentionally NOT scaled: the outer window keeps its size and only
      // its interior zooms.
      super.drawWindow(n, n2, f);

      ScaledResolution sr = new ScaledResolution(mc);
      GL11.glPushMatrix();
      GL11.glScaled(1.0 / (double)sr.getScaleFactor(), 1.0 / (double)sr.getScaleFactor(), 1.0);
      // Shell placement, in the same order and with the same values the base class uses for the shell itself
      // and mouseClicked uses for the title-bar controls: translate first, then the shell scale, both AFTER the
      // 1/guiScale factor above.
      //
      // WHY it has to be repeated here and cannot be inherited: super.drawWindow() unwinds its own three
      // matrices (plus the transition zoom) before it returns, so this content layer starts from the overlay's
      // full-screen transform. Without this translate the whole layer - rail, header, pills, grid, footer -
      // renders st.left/st.top pixels up and to the left of where its own clip rects are, because every
      // glScissor below is derived from contentToScreenX/Y, which DOES include st.left/st.top. The visible
      // consequence is not a shifted window but a missing one: the rail and header fall entirely outside the
      // shell scissor and disappear, while the module grid survives as a CROP, because ScrollFadeCompositor
      // composites its offscreen capture sampling by screen position (gl_FragCoord), which cancels a
      // translation error instead of showing it.
      ClickGuiWindow.ScaleTransform shellTransform = this.getScaleTransform();
      GL11.glTranslatef(shellTransform.left, shellTransform.top, 0.0F);
      GL11.glScaled((double)shellTransform.scale, (double)shellTransform.scale, 1.0);
      // Shell-local mouse. The window-level transform is identity now (getContentScaleFactor() == 1),
      // so toLocalX/Y just remove the shell origin.
      int shellMouseX = this.toLocalX((float)n * (float)sr.getScaleFactor());
      int shellMouseY = this.toLocalY((float)n2 * (float)sr.getScaleFactor());

      int shellW = this.getWidth();
      int shellH = this.getHeight();

      boolean windowBlur = ClientManagers.moduleManager.clickGui.isWindowBlurActive();
      boolean panelBlur = ClientManagers.moduleManager.clickGui.isPanelBlurActive();
      float blurRadius = ClientManagers.moduleManager.clickGui.getBlurRadius();
      float corner = this.isFullScreen() ? 0.0F : (float)this.cornerRadius;

      // ---- fixed shell layer (identical at every GUI Scale) --------------------------------
      // One continuous window shell: the sidebar rail and the header are internal zones of this single
      // surface. (WHY: the previous three per-region slabs gave the sidebar, the header and the
      // content their own borders and drop shadows, which read as separate divisions of the window.)
      // Drawn border-less on purpose: renderBackgroundPass already draws the panel-blur outline around
      // the whole shell, and drawing a second rim here would double-blend the 1px border.
      if (!windowBlur && panelBlur) {
         GlassRenderer.drawLiquidGlassSurface(
            0.0F,
            0.0F,
            (float)shellW,
            (float)shellH,
            corner,
            corner,
            corner,
            corner,
            blurRadius,
            0.0F,
            0,
            this.getOpenProgress()
         );
      }

      // ---- zoomed content layer ------------------------------------------------------------
      float zoom = this.getContentZoom();
      // Viewport authored in CONTENT pixels: dividing the fixed shell by the zoom means the layout
      // still fills the whole frame (zoom-out reveals more rows/cards, zoom-in fewer, larger ones)
      // while nothing can escape the frame - the shell scissor below clips the overflow and the
      // SmoothScrollBar machinery scrolls it.
      int width = this.getContentWidth();
      int height = this.getContentHeight();
      // Same shell corner expressed in content pixels, so the zoomed rail tint still hugs the fixed
      // outer corner exactly.
      float cornerContent = corner / zoom;
      // Inverse of the render transform pushed right below: everything from here down lays out and hit
      // tests in content pixels.
      n = this.shellToContentX((float)shellMouseX);
      n2 = this.shellToContentY((float)shellMouseY);
      GL11.glPushMatrix();
      GL11.glScaled((double)zoom, (double)zoom, 1.0);

      if (this.searchFocused) {
         TextFieldComponent.anyFieldFocused = true;
      }

      this.popupHost.setBounds(width, height);
      int savedWheelDelta = Augustus.getInstance().getMouseWheelTracker().lastDelta;
      boolean popupConsumedScroll = this.popupHost.hasActivePopup() && savedWheelDelta != 0 && this.popupHost.handleScroll(n, n2, savedWheelDelta);
      if (popupConsumedScroll) {
         Augustus.getInstance().getMouseWheelTracker().lastDelta = 0;
      }

      // Sidebar opacity is applied by UiTheme.glassSidebarTintArgb() (single application point), so the
      // returned ARGB already carries the user's "Sidebar Opacity". Only the open animation is folded
      // in here; the tint itself is never re-scaled by sidebarOpacity() to avoid applying it twice.
      int sidebarTint = UiTheme.glassSidebarTintArgb();
      if ((sidebarTint >>> 24) > 0) {
         this.drawRailTint(UiTheme.scaleAlpha(sidebarTint, this.getOpenProgress()), height, cornerContent);
      }

      // Close/open transition. The envelope cannot ride on GL state (GlassRenderer's primitive pass and
      // the font renderer both reset the colour per call), so it is multiplied into every explicit ARGB
      // this window paints through fadedArgb(...). Colours that already derive from getOpenProgress()
      // (the shell surface, the rail tint) must NOT be faded again or they would fade at the square of
      // the intended rate.
      //
      // Every colour below is drawn by this window's content, so each one carries the transition
      // envelope once. Fading the locals here covers borders, separators, card fills and everything
      // downstream that is handed one of them.
      int accentColor = fadedArgb(UiTheme.accentColor());
      int textColor = fadedArgb(UiTheme.primaryTextColor());
      int outlineColor = fadedArgb(UiTheme.glassBorderColor());
      int dividerColor = fadedArgb(UiTheme.GLASS_DIVIDER);

      // The header rule spans the whole composition instead of stopping at x = SIDEBAR_WIDTH: a rule
      // whose endpoint sits exactly on the rail boundary re-introduces the seam the rail must not have.
      GlassRenderer.drawSeparator(
         (float)CONTENT_PADDING, (float)HEADER_HEIGHT, (float)(width - CONTENT_PADDING), (float)HEADER_HEIGHT, UiTheme.SEPARATOR_THICKNESS, dividerColor
      );

      this.hoveringModuleContextMenu = this.contextMenuModule != null && RenderUtil.isHovered((float)this.contextMenuX, (float)this.contextMenuY, 154.0F, 56.0F, n, n2);
      this.hoveringCategoryContextMenu = this.contextMenuCategory != null && RenderUtil.isHovered((float)this.contextMenuX, (float)this.contextMenuY, 154.0F, 104.0F, n, n2);

      boolean suppressHoverUnderPopup = this.popupHost.hasActivePopup() && this.popupHost.getActivePopup().contains(n, n2);
      int uiMouseX = suppressHoverUnderPopup ? -9999 : n;
      int uiMouseY = suppressHoverUnderPopup ? -9999 : n2;

      GL11.glEnable(GL11.GL_SCISSOR_TEST);
      // glScissor takes raw framebuffer pixels and ignores the modelview matrix, so every clip rect in
      // this file is mapped through the same content zoom the drawing uses (contentToScreenX/Y);
      // otherwise the clip box would stay unscaled while the content moved and scaled.
      //
      // The shell clip uses the SHELL extent, not contentToScreenX(getContentWidth()): the viewport is
      // rounded to whole content pixels, so the mapped edge can sit up to half a content pixel short of
      // the frame and would leave a sliver unclipped. Clipping to the frame itself is both exactly the
      // fixed outer window and strictly tighter than the content viewport.
      this.clipToShell();

      this.drawSidebar(uiMouseX, uiMouseY, width, height, accentColor, textColor, dividerColor);
      this.drawHeader(uiMouseX, uiMouseY, width, accentColor, textColor, outlineColor, windowBlur);

      this.contentTransitionAnim = UiAnimation.smooth(this.contentTransitionAnim, 1.0F, 18.0F);
      float easedTrans = UiAnimation.easeOutCubic(this.contentTransitionAnim);
      float slideX = (1.0F - easedTrans) * 10.0F * this.contentTransitionDir;
      GL11.glPushMatrix();
      GL11.glTranslatef(slideX, 0.0F, 0.0F);

      if (this.viewingModuleSettings && this.selectedModule != null) {
         this.drawSettingsContent(uiMouseX, uiMouseY, width, height);
      } else if (this.activeSidebarTab == SidebarTab.CONFIGS) {
         this.drawConfigsContent(uiMouseX, uiMouseY, width, height, accentColor, textColor, outlineColor);
      } else {
         this.drawCategoryPills(uiMouseX, uiMouseY, width, accentColor, textColor, outlineColor);
         this.drawModuleGrid(uiMouseX, uiMouseY, width, height, accentColor, textColor, outlineColor, windowBlur);
      }

      GL11.glPopMatrix();

      GL11.glDisable(GL11.GL_SCISSOR_TEST);

      if (this.hoveredViewMode != null && !this.popupHost.hasActivePopup()) {
         drawTooltip(FontManager.interMedium, this.hoveredViewMode.label, n, n2 + 12);
      }

      if (this.contextMenuModule != null) {
         this.drawModuleContextMenu(n, n2);
      }
      if (this.contextMenuCategory != null) {
         this.drawCategoryContextMenu(n, n2);
      }
      if (this.contextMenuSetting != null) {
         this.drawSettingContextMenu(n, n2);
      }

      this.popupHost.draw(n, n2);
      if (popupConsumedScroll) {
         Augustus.getInstance().getMouseWheelTracker().lastDelta = savedWheelDelta;
      }

      GL11.glPopMatrix();
      GL11.glPopMatrix();
   }

   private void triggerContentTransition(float dir) {
      this.contentTransitionDir = dir;
      this.contentTransitionAnim = 0.0F;
   }

   /**
    * Paints the rail tint so the rail has no edge of its own: a solid core rounded on the shell's left
    * corners, plus an analytical Gaussian glow whose right edge sits {@code pad} short of the rail
    * boundary, so the tint dissolves to nothing exactly at x = SIDEBAR_WIDTH.
    *
    * <p>WHY: the previous shape was a rectangle ending on a hard 1px edge at x = SIDEBAR_WIDTH with a
    * dark 7px shadow band in front of it - the two together read as a separate slab glued to the
    * window. A falloff keeps exactly one surface and one background, and the rail stays navigable
    * because its rows sit in the fully saturated part.
    *
    * <p>{@code tintArgb} already carries the user's Sidebar Opacity (applied once inside
    * {@link UiTheme#glassSidebarTintArgb()}); this method only shapes it, it never re-scales the alpha.
    */
   private void drawRailTint(int tintArgb, int height, float cornerContent) {
      if ((tintArgb >>> 24) <= 0) {
         return;
      }
      // GlassRenderer.drawSoftShadow pads its quad by blurRadius * 2.2 + 2 on every side and the glow
      // reaches ~0 exactly at that pad, so the solid core has to stop one pad short of the glow's edge
      // to hand the falloff over without a step.
      float blur = 16.0F;
      float pad = blur * 2.2F + 2.0F;
      float glowEdge = (float)SIDEBAR_WIDTH - pad;
      float h = (float)height;
      GlassRenderer.drawRoundedRect(0.0F, 0.0F, glowEdge - pad, h, cornerContent, 0.0F, 0.0F, cornerContent, tintArgb);
      // The glow rect reaches above/below the window so only its right edge falls inside the shell.
      GlassRenderer.drawSoftShadow(0.0F, -pad, glowEdge, h + pad * 2.0F, 0.0F, blur, 0.0F, tintArgb);
   }

   /** One Prestige-style navigation row: icon, label and a right-aligned live count. */
   /**
    * One row of the rail: the aggregate, one sidebar tab, or a shortcut that opens one module's settings.
    *
    * <p>WHY the module registry is not mirrored here any more: the per-category rows duplicated the filter that
    * already exists as the content's category pills (built from the same {@link CategoryTabButton} instances),
    * so the two could disagree about the selection, and the rail grew with the registry instead of staying a
    * fixed navigation column. The pills keep working exactly as they did, so nothing became unreachable.
    */
   private static final class RailRow {
      /** The full module list, with the category filter cleared. */
      static final int KIND_MODULES = 0;
      /** One sidebar tab (Active / Configs / Keybinds). */
      static final int KIND_TAB = 1;
      /** A shortcut that opens a single module's settings. */
      static final int KIND_MODULE = 2;

      final int kind;
      final String label;
      final String count;
      final LucideIcon icon;
      final Module module;
      final SidebarTab tabId;
      int y;

      RailRow(int kind, String label, String count, LucideIcon icon, Module module, SidebarTab tabId) {
         this.kind = kind;
         this.label = label;
         this.count = count;
         this.icon = icon;
         this.module = module;
         this.tabId = tabId;
         this.y = 0;
      }

      String animKey() {
         return "rail_" + this.kind + "_" + this.label;
      }
   }

   /**
    * Builds the rail's rows: the aggregate, the three semantic tabs, and the appearance shortcuts.
    *
    * <p>The rows are navigation only. The module registry is NOT mirrored here (see {@link RailRow}), so the
    * rail cannot disagree with the content's category pills about the selection and does not grow with the
    * registry. y already includes the rail's scroll offset, so the drawing pass and the hit tests share one
    * geometry and cannot drift.
    */
   private List buildRailRows() {
      List rows = new ArrayList();
      int scroll = this.railScrollOffset;

      // Group 1: the full list plus the semantic tabs. Counts are counted from the live module list, never
      // hardcoded, and only where they say something the row does not: how much is in the list, and how much of
      // it is on.
      int y = SIDEBAR_GROUP1_TOP + scroll;
      this.addRailRow(
         rows,
         new RailRow(RailRow.KIND_MODULES, "Modules", String.valueOf(this.countVisibleModules()), LucideIcon.LAYOUT_GRID, null, null),
         y
      );
      y += SIDEBAR_ITEM_STEP;
      int activeNum = this.countActiveModules();
      this.addRailRow(
         rows, new RailRow(RailRow.KIND_TAB, "Active", activeNum > 0 ? String.valueOf(activeNum) : null, LucideIcon.ZAP, null, SidebarTab.ACTIVE), y
      );
      y += SIDEBAR_ITEM_STEP;
      this.addRailRow(rows, new RailRow(RailRow.KIND_TAB, "Configs", null, LucideIcon.FOLDER, null, SidebarTab.CONFIGS), y);
      y += SIDEBAR_ITEM_STEP;
      this.addRailRow(rows, new RailRow(RailRow.KIND_TAB, "Keybinds", null, LucideIcon.KEYBOARD, null, SidebarTab.KEYBINDS), y);
      y += SIDEBAR_ITEM_H;

      // Group 2: appearance shortcuts. These open a module's settings directly, so the appearance work does not
      // have to go through a search for the module first.
      y += RAIL_GROUP_GAP;
      this.railGroup2LabelY = y;
      y += RAIL_GROUP_LABEL_ADVANCE;
      this.addRailRow(
         rows, new RailRow(RailRow.KIND_MODULE, "ClickGUI", null, LucideIcon.SLIDERS_HORIZONTAL, ClientManagers.moduleManager.clickGui, null), y
      );
      y += SIDEBAR_ITEM_STEP;
      this.addRailRow(rows, new RailRow(RailRow.KIND_MODULE, "Blur & Glow", null, LucideIcon.SPARKLES, ClientManagers.moduleManager.blur, null), y);
      y += SIDEBAR_ITEM_STEP;
      this.addRailRow(
         rows, new RailRow(RailRow.KIND_MODULE, "Client Color", null, LucideIcon.PALETTE, ClientManagers.moduleManager.customClientColor, null), y
      );
      y += SIDEBAR_ITEM_H;

      this.railContentTop = SIDEBAR_GROUP1_TOP - RAIL_LABEL_GAP + scroll;
      this.railContentBottom = y;
      return rows;
   }

   private void addRailRow(List rows, RailRow row, int y) {
      row.y = y;
      rows.add(row);
   }

   private boolean isRailRowActive(RailRow row) {
      if (row.kind == RailRow.KIND_MODULES) {
         return this.allCategoriesSelected && this.activeSidebarTab == SidebarTab.MODULES && !this.viewingModuleSettings;
      }
      if (row.kind == RailRow.KIND_TAB) {
         return this.activeSidebarTab == row.tabId && !this.viewingModuleSettings;
      }
      return this.viewingModuleSettings && this.selectedModule == row.module;
   }

   /** Live module count for the aggregate row: the same hidden-in-gui rule the module list itself uses. */
   private int countVisibleModules() {
      int count = 0;
      for (Object obj : ClientManagers.moduleManager.getModules()) {
         Module m = (Module)obj;
         if (!m.isHiddenInGui()) {
            count++;
         }
      }
      return count;
   }

   /** Live count of the modules that are currently on. */
   private int countActiveModules() {
      int count = 0;
      for (Object obj : ClientManagers.moduleManager.getModules()) {
         Module m = (Module)obj;
         if (!m.isHiddenInGui() && m.isToggled()) {
            count++;
         }
      }
      return count;
   }

   private void drawSidebarSectionHeader(String label, float x, float y, float width, int mutedText, int dividerColor) {
      FontManager.interSection.drawStringSpaced(label, x, y + 1.0F, 1.05F, mutedText);
      float labelW = FontManager.interSection.getStringWidthSpaced(label, 1.05F);
      float lineStart = x + labelW + 8.0F;
      float lineEnd = x + width - 4.0F;
      if (lineEnd > lineStart + 10.0F) {
         float lineY = y + 1.0F + FontManager.interSection.getFontHeight() * 0.5F;
         GlassRenderer.drawSeparator(lineStart, lineY, lineEnd, lineY, UiTheme.SEPARATOR_THICKNESS, dividerColor);
      }
   }

   /**
    * Prestige-style rail: brand mark, a caps group label, rows with icon + label + right-aligned
    * count, a second group, and one soft filled pill (drawn behind the rows so it can slide) for the
    * active row instead of a bright block.
    */
   private void drawSidebar(int mouseX, int mouseY, int width, int height, int accentColor, int textColor, int dividerColor) {
      // Brand header: wordmark only, in uppercase. (The emblem icon and the "Augustus / Redesign"
      // two-tone wordmark were removed on request; no icon is drawn before the text any more, so the
      // wordmark keeps a plain left padding instead of an icon-sized indent.)
      float brandX = 16.0F;
      float brandLineHeight = 22.0F;
      CustomFontUtil brandFont = FontManager.interSemiBold;
      String brand = "AUGUSTUS REDESING";
      float maxBrandW = (float)SIDEBAR_WIDTH - brandX - 16.0F;
      if (brandFont.getStringWidth(brand) > maxBrandW) {
         // Keep the wordmark inside the sidebar, exactly like the previous two-tone brand did.
         brandFont = FontManager.interMedium;
      }
      float by = 13.0F + (brandLineHeight - brandFont.getFontHeight()) * 0.5F;
      brandFont.drawString(brand, (double)brandX, (double)by, fadedArgb(UiTheme.TEXT_PRIMARY));

      if (!ClickGuiScreen.configEditable || ClickGuiScreen.protectedConfigCreator) {
         float lockW = 86.0F;
         GlassRenderer.drawRoundedBox(
            brandX,
            35.0F,
            lockW,
            13.0F,
            4.0F,
            fadedArgb(UiTheme.withAlpha(UiTheme.enabledColor(), 40)),
            fadedArgb(UiTheme.withAlpha(UiTheme.enabledColor(), 145)),
            1.0F
         );
         LucideIcon.LOCK.drawCentered(brandX + 7.5F, 41.5F, 8.5F, fadedArgb(UiTheme.enabledColor()));
         FontManager.interSmall.drawVerticallyCenteredString("Protected", (double)(brandX + 14.0F), 35.0, 13.0, fadedArgb(UiTheme.enabledColor()));
      }

      int mutedText = fadedArgb(UiTheme.mutedTextColor());
      List rows = this.buildRailRows();

      // Scrollable viewport: the rows clip against the band between the header rule and the pinned
      // profile footer, and scroll inside it when the zoom (or a short window) leaves too little room.
      // The band is fully inside the shell, so it can replace the shell clip for the duration of the
      // rows; the shell rect is re-issued afterwards (glScissor has no stack of its own).
      int viewTop = RAIL_VIEW_TOP;
      int viewBottom = height - FOOTER_HEIGHT - 4;
      int contentH = this.railContentBottom - this.railContentTop;
      RenderUtil.scissorRectScaled(
         this.contentToScreenX(0.0F), this.contentToScreenY((float)viewTop), this.contentToScreenX((float)SIDEBAR_WIDTH), this.contentToScreenY((float)viewBottom), 1.0
      );

      // One animated soft pill behind the active row.
      RailRow activeRow = null;
      for (Object obj : rows) {
         RailRow row = (RailRow)obj;
         if (this.isRailRowActive(row)) {
            activeRow = row;
            break;
         }
      }
      this.railPillVis = UiAnimation.smooth(this.railPillVis, activeRow != null ? 1.0F : 0.0F, 16.0F);
      if (activeRow != null) {
         float tx = (float)RAIL_ROW_X;
         float ty = (float)activeRow.y;
         if (this.railPillInit) {
            this.railPillX = UiAnimation.smooth(this.railPillX, tx, 20.0F);
            this.railPillY = UiAnimation.smooth(this.railPillY, ty, 20.0F);
         } else {
            this.railPillX = tx;
            this.railPillY = ty;
            this.railPillW = (float)RAIL_ROW_W;
            this.railPillH = (float)SIDEBAR_ITEM_H;
            this.railPillInit = true;
         }
      }
      if (this.railPillVis > 0.02F) {
         float a = this.railPillVis;
         // Soft filled pill, never a bright block: a low-alpha accent fill plus a hairline rim.
         // accentColor arrives already faded from drawWindow, so nothing is applied twice.
         GlassRenderer.drawRoundedRect(
            this.railPillX, this.railPillY, this.railPillW, this.railPillH, UiTheme.PILL_RADIUS, UiTheme.withAlpha(accentColor, Math.round(44.0F * a))
         );
         GlassRenderer.drawRoundedOutline(
            this.railPillX,
            this.railPillY,
            this.railPillW,
            this.railPillH,
            UiTheme.PILL_RADIUS,
            UiTheme.BORDER_THICKNESS,
            fadedArgb(UiTheme.withAlpha(UiTheme.accentSoftGlow(), Math.round(115.0F * a)))
         );
      }

      this.drawSidebarSectionHeader(
         "MODULES & CONFIG", 16.0F, (float)(SIDEBAR_GROUP1_TOP - RAIL_LABEL_GAP + this.railScrollOffset), (float)(SIDEBAR_WIDTH - 28), mutedText, dividerColor
      );
      this.drawSidebarSectionHeader("APPEARANCE", 16.0F, (float)this.railGroup2LabelY, (float)(SIDEBAR_WIDTH - 28), mutedText, dividerColor);

      for (Object obj : rows) {
         RailRow row = (RailRow)obj;
         if (row.y + SIDEBAR_ITEM_H < viewTop || row.y > viewBottom) {
            continue; // clipped away: drawn neither here nor clickable (see railRowAt)
         }
         this.drawRailRow(row, mouseX, mouseY, row == activeRow, accentColor);
      }

      // Restore the shell clip for everything that follows (footer, context menus, popups, tooltips).
      this.clipToShell();

      int accent = UiTheme.accentColor();
      this.railScrollBar.drawVerticalBar(SIDEBAR_WIDTH - 9, viewTop, SIDEBAR_WIDTH - 3, viewBottom, this.railContentTop - this.railScrollOffset, contentH, fadedArgb(accent));
      this.railScrollOffset = this.railScrollBar.updateScrollOffset(
         contentH, viewTop, viewBottom, this.railScrollOffset, 0, viewTop, SIDEBAR_WIDTH, viewBottom - viewTop, mouseX, mouseY
      );

      // Bottom profile footer
      int footerY = height - FOOTER_HEIGHT;
      GlassRenderer.drawSeparator(14.0F, (float)footerY, (float)(SIDEBAR_WIDTH - 14), (float)footerY, UiTheme.SEPARATOR_THICKNESS, dividerColor);
      this.drawProfileFooter(footerY, accentColor, textColor, mutedText, mouseX, mouseY);
   }

   /**
    * Draws one row with this project's own tokens. The active state is carried by the sliding pill
    * drawn behind the rows; here the icon and the label pick up the accent, and the count stays a
    * quiet right-aligned tabular number.
    */
   private void drawRailRow(RailRow row, int mouseX, int mouseY, boolean active, int accentColor) {
      float x = (float)RAIL_ROW_X;
      float y = (float)row.y;
      float w = (float)RAIL_ROW_W;
      float h = (float)SIDEBAR_ITEM_H;
      boolean hovered = RenderUtil.isHovered(x, y, w, h, mouseX, mouseY) && !this.hoveringCategoryContextMenu;
      float hovT = UiAnimation.smoothState(row.animKey() + "_hov", hovered ? 1.0F : 0.0F, 16.0F);
      float actT = UiAnimation.smoothState(row.animKey() + "_act", active ? 1.0F : 0.0F, 18.0F);

      if (hovT > 0.03F && actT < 0.5F) {
         GlassRenderer.drawRoundedRect(x, y, w, h, UiTheme.PILL_RADIUS, UiTheme.withAlpha(accentColor, Math.round(18.0F * hovT)));
      }

      // Fade once, on the final RGB-resolved colour: fading an input of a later lerp would apply the
      // envelope twice and darken the accent ramp as well as the alpha.
      int idleFg = UiTheme.secondaryTextColor();
      int fg = UiTheme.lerpArgb(idleFg, UiTheme.TEXT_PRIMARY, Math.max(actT, hovT * 0.85F));
      int iconColor = fadedArgb(UiTheme.lerpArgb(fg, UiTheme.accentSoftGlow(), actT));
      int labelColor = fadedArgb(UiTheme.lerpArgb(fg, UiTheme.accentSoftGlow(), actT * 0.9F));

      if (row.icon != null) {
         row.icon.drawCentered(x + 15.0F, y + h * 0.5F, 13.5F, iconColor);
      }

      // The count is drawn only where the row's meaning needs it (how much is in the list, how much of it is
      // on) and is tabular, so the column cannot jitter as the numbers change.
      float countW = row.count == null ? 0.0F : FontManager.interSmall.getStringWidthTabular(row.count);
      float labelMaxW = Math.max(36.0F, w - 30.0F - (row.count == null ? 6.0F : countW + 12.0F));
      String safeLabel = ModernSettingsPanel.trimToWidth(FontManager.interMedium, row.label, labelMaxW);
      FontManager.interMedium.drawVerticallyCenteredString(safeLabel, (double)(x + 28.0F), (double)y, (double)h, labelColor);

      if (row.count != null) {
         float countY = y + (h - FontManager.interSmall.getFontHeight()) * 0.5F;
         int countColor = fadedArgb(UiTheme.lerpArgb(UiTheme.mutedTextColor(), UiTheme.accentSoftGlow(), actT));
         FontManager.interSmall.drawStringTabular(row.count, x + w - 10.0F - countW, countY, countColor);
      }
   }

   /** Row under the mouse, using the same geometry (including scroll offset) the rail drew with. */
   private RailRow railRowAt(int mouseX, int mouseY, int height) {
      int viewTop = RAIL_VIEW_TOP;
      int viewBottom = height - FOOTER_HEIGHT - 4;
      if (mouseY < viewTop || mouseY > viewBottom) {
         return null; // clipped rows are not clickable
      }
      for (Object obj : this.buildRailRows()) {
         RailRow row = (RailRow)obj;
         if (RenderUtil.isHovered((float)RAIL_ROW_X, (float)row.y, (float)RAIL_ROW_W, (float)SIDEBAR_ITEM_H, mouseX, mouseY)) {
            return row;
         }
      }
      return null;
   }

   /** Navigates like the content's own controls, so the rail cannot invent new semantics. */
   private void activateRailRow(RailRow row) {
      this.popupHost.close();
      if (row.kind == RailRow.KIND_MODULE) {
         this.openModuleSettings(row.module);
         return;
      }

      if (row.kind == RailRow.KIND_MODULES) {
         // "Modules" means the whole list, so it clears the category filter as well as the tab. WHY the tab is
         // set here too: nothing else in the window ever assigns SidebarTab.MODULES, so without this the tab
         // rows would be a one-way door - once Active/Configs/Keybinds was picked, the unfiltered list could
         // never be reached again.
         this.allCategoriesSelected = true;
         this.activeSidebarTab = SidebarTab.MODULES;
      } else {
         this.activeSidebarTab = row.tabId;
         if (row.tabId == SidebarTab.CONFIGS && Augustus.getInstance().getClickGui() != null) {
            Augustus.getInstance().getClickGui().getConfigPanel().onPanelOpened();
         }
      }

      this.viewingModuleSettings = false;
      this.moduleScrollOffset = 0;
      this.moduleScrollBar.resetPendingScroll();
      this.triggerContentTransition(1.0F);
   }

   private void drawProfileFooter(int footerY, int accentColor, int textColor, int mutedText, int mouseX, int mouseY) {
      float cardX = 12.0F;
      float cardY = (float)(footerY + 10);
      float cardW = (float)(SIDEBAR_WIDTH - 24);
      float cardH = 52.0F;
      boolean cardHover = RenderUtil.isHovered(cardX, cardY, cardW, cardH, mouseX, mouseY);
      float cardHoverT = UiAnimation.smoothState("profile_card_hov", cardHover ? 1.0F : 0.0F, 14.0F);
      GlassRenderer.drawGlassCardWithHover(cardX, cardY, cardW, cardH, UiTheme.CARD_RADIUS, cardHoverT * 0.65F, 0.0F, 0.0F, 0.0F, (float)mouseX, (float)mouseY, accentColor, fadedArgb(UiTheme.GLASS_BORDER), UiTheme.BORDER_THICKNESS);

      int avatarX = (int)(cardX + 10.0F);
      int avatarY = (int)(cardY + 10.0F);
      int avatarSize = 32;
      GlassRenderer.drawSoftShadow((float)avatarX, (float)avatarY, (float)avatarSize, (float)avatarSize, UiTheme.CONTROL_RADIUS, 6.0F, 1.0F, UiTheme.withAlpha(accentColor, 42));
      GlassRenderer.drawRoundedBox((float)avatarX, (float)avatarY, (float)avatarSize, (float)avatarSize, UiTheme.CONTROL_RADIUS, UiTheme.glassRecessedFillArgb(0.0F, 0.0F, accentColor), fadedArgb(UiTheme.withAlpha(UiTheme.accentSoftGlow(), 160)), UiTheme.BORDER_THICKNESS);

      if (mc.thePlayer != null && mc.thePlayer.getLocationSkin() != null) {
         GlStateManager.enableTexture2D();
         GlStateManager.enableBlend();
         GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
         mc.getTextureManager().bindTexture(mc.thePlayer.getLocationSkin());
         Gui.drawScaledCustomSizeModalRect(avatarX + 2, avatarY + 2, 8.0F, 8.0F, 8, 8, avatarSize - 4, avatarSize - 4, 64.0F, 64.0F);
         Gui.drawScaledCustomSizeModalRect(avatarX + 2, avatarY + 2, 40.0F, 8.0F, 8, 8, avatarSize - 4, avatarSize - 4, 64.0F, 64.0F);
      } else {
         String user = mc.getSession() != null && mc.getSession().getUsername() != null ? mc.getSession().getUsername() : "A";
         String initial = user.isEmpty() ? "A" : user.substring(0, 1).toUpperCase(Locale.ROOT);
         FontManager.interSemiBold.drawCenteredStringInBox(initial, (double)avatarX, (double)avatarY, (double)avatarSize, (double)avatarSize, accentColor);
      }

      // Live online status dot cleanly anchored to bottom-right of avatar frame
      float dotX = (float)(avatarX + avatarSize - 2);
      float dotY = (float)(avatarY + avatarSize - 2);
      GlassRenderer.drawCircle(dotX, dotY, 4.3F, fadedArgb(0xFF090B10));
      GlassRenderer.drawCircle(dotX, dotY, 2.9F, fadedArgb(UiTheme.enabledColor()));

      float btnSize = 20.0F;
      float btnY = cardY + (cardH - btnSize) * 0.5F;
      float colorBtnX = cardX + cardW - 9.0F - btnSize;
      float guiBtnX = colorBtnX - 5.0F - btnSize;
      boolean viewingGui = this.viewingModuleSettings && this.selectedModule == ClientManagers.moduleManager.clickGui;
      boolean viewingColor = this.viewingModuleSettings && this.selectedModule == ClientManagers.moduleManager.customClientColor;

      this.footerClickGuiButton.render(guiBtnX, btnY, btnSize, btnSize, null, LucideIcon.SLIDERS_HORIZONTAL, viewingGui ? ModernButton.Variant.PRIMARY : ModernButton.Variant.GLASS, mouseX, mouseY);
      this.footerColorButton.render(colorBtnX, btnY, btnSize, btnSize, null, LucideIcon.PALETTE, viewingColor ? ModernButton.Variant.PRIMARY : ModernButton.Variant.GLASS, mouseX, mouseY);

      float infoX = (float)(avatarX + avatarSize + 10);
      float infoMaxW = Math.max(52.0F, guiBtnX - infoX - 8.0F);
      String username = mc.getSession() != null && mc.getSession().getUsername() != null ? mc.getSession().getUsername() : "Player";
      String safeUser = ModernSettingsPanel.trimToWidth(FontManager.interMedium, username, infoMaxW);
      float userY = cardY + 10.0F;
      FontManager.interMedium.drawString(safeUser, (double)infoX, (double)userY, fadedArgb(UiTheme.TEXT_PRIMARY));
      String sub = ModernSettingsPanel.trimToWidth(FontManager.interRegular, "Augustus 1.8.9", infoMaxW);
      float subY = userY + FontManager.interMedium.getFontHeight() + 4.5F;
      FontManager.interRegular.drawString(sub, (double)infoX, (double)subY, mutedText);
   }

   private void drawHeader(int mouseX, int mouseY, int width, int accentColor, int textColor, int outlineColor, boolean windowBlur) {
      int contentStartX = SIDEBAR_WIDTH + CONTENT_PADDING;
      // Sleek recessed/hover nav arrow pills (< >)
      boolean canBack = this.viewingModuleSettings;
      boolean canForward = !this.viewingModuleSettings && this.lastViewedModule != null;
      float backX = (float)contentStartX;
      float fwdX = backX + 30.0F;
      boolean backHover = canBack && RenderUtil.isHovered(backX, 14.0F, 26.0F, 26.0F, mouseX, mouseY);
      boolean fwdHover = canForward && RenderUtil.isHovered(fwdX, 14.0F, 26.0F, 26.0F, mouseX, mouseY);
      float backHovT = UiAnimation.smoothState("hdr_back_hov", backHover ? 1.0F : 0.0F, 16.0F);
      float fwdHovT = UiAnimation.smoothState("hdr_fwd_hov", fwdHover ? 1.0F : 0.0F, 16.0F);
      float backPressT = UiAnimation.smoothState("hdr_back_prs", backHover && UiAnimation.isPrimaryMouseDown() ? 1.0F : 0.0F, 30.0F);
      float fwdPressT = UiAnimation.smoothState("hdr_fwd_prs", fwdHover && UiAnimation.isPrimaryMouseDown() ? 1.0F : 0.0F, 30.0F);

      if (canBack || backHovT > 0.02F) {
         int backFill = UiTheme.glassControlFillArgb(backHovT, 0.0F, accentColor);
         GlassRenderer.drawInteractiveSurface(
            backX,
            14.0F,
            26.0F,
            26.0F,
            UiTheme.CONTROL_RADIUS,
            UiTheme.scaleAlpha(backFill, 0.45F + backHovT * 0.55F),
            UiTheme.scaleAlpha(UiTheme.lerpArgb(backFill, 0xFF0A0A14, 0.3F), 0.45F + backHovT * 0.55F),
            backHover ? fadedArgb(UiTheme.withAlpha(UiTheme.accentSoftGlow(), 220)) : fadedArgb(UiTheme.GLASS_BORDER),
            UiTheme.BORDER_THICKNESS,
            UiTheme.glassTopHighlightAlpha(backHovT),
            backHovT,
            backPressT,
            canBack,
            (float)mouseX,
            (float)mouseY,
            accentColor
         );
      }
      if (canForward || fwdHovT > 0.02F) {
         int fwdFill = UiTheme.glassControlFillArgb(fwdHovT, 0.0F, accentColor);
         GlassRenderer.drawInteractiveSurface(
            fwdX,
            14.0F,
            26.0F,
            26.0F,
            UiTheme.CONTROL_RADIUS,
            UiTheme.scaleAlpha(fwdFill, 0.45F + fwdHovT * 0.55F),
            UiTheme.scaleAlpha(UiTheme.lerpArgb(fwdFill, 0xFF0A0A14, 0.3F), 0.45F + fwdHovT * 0.55F),
            fwdHover ? fadedArgb(UiTheme.withAlpha(UiTheme.accentSoftGlow(), 220)) : fadedArgb(UiTheme.GLASS_BORDER),
            UiTheme.BORDER_THICKNESS,
            UiTheme.glassTopHighlightAlpha(fwdHovT),
            fwdHovT,
            fwdPressT,
            canForward,
            (float)mouseX,
            (float)mouseY,
            accentColor
         );
      }

      int backColor = fadedArgb(canBack ? (backHover ? UiTheme.TEXT_PRIMARY : UiTheme.secondaryTextColor()) : UiTheme.disabledTextColor());
      int fwdColor = fadedArgb(canForward ? (fwdHover ? UiTheme.TEXT_PRIMARY : UiTheme.secondaryTextColor()) : UiTheme.disabledTextColor());
      GlassRenderer.drawVectorArrow(backX + 13.0F, 27.0F + backPressT * 0.4F, 11.0F, false, backColor);
      GlassRenderer.drawVectorArrow(fwdX + 13.0F, 27.0F + fwdPressT * 0.4F, 11.0F, true, fwdColor);

      // Two-tier breadcrumb & view title hierarchy
      float titleX = fwdX + 38.0F;
      if (this.viewingModuleSettings && this.selectedModule != null) {
         float modMaxRight = (float)(width - 418);
         String cat = this.formatCategoryName(this.selectedModule.getCategorys());
         String sep = " / ";
         FontManager.interMedium.drawVerticallyCenteredString(cat, (double)titleX, 14.0, 26.0, fadedArgb(UiTheme.TEXT_SECONDARY));
         float sepX = titleX + FontManager.interMedium.getStringWidth(cat);
         FontManager.interMedium.drawVerticallyCenteredString(sep, (double)sepX, 14.0, 26.0, fadedArgb(UiTheme.mutedTextColor()));
         float modX = sepX + FontManager.interMedium.getStringWidth(sep);
         String mod = ModernSettingsPanel.trimToWidth(FontManager.interSemiBoldLarge, this.selectedModule.getModuleName(), Math.max(60.0F, modMaxRight - modX));
         FontManager.interSemiBoldLarge.drawVerticallyCenteredString(mod, (double)modX, 14.0, 26.0, fadedArgb(UiTheme.TEXT_PRIMARY));
      } else {
         String headerTitle;
         if (this.activeSidebarTab == SidebarTab.ACTIVE) {
            headerTitle = "Active Modules";
         } else if (this.activeSidebarTab == SidebarTab.CONFIGS) {
            headerTitle = "Configs";
         } else if (this.activeSidebarTab == SidebarTab.KEYBINDS) {
            headerTitle = "Keybinds";
         } else {
            headerTitle = "Modules";
         }
         FontManager.interSemiBoldLarge.drawVerticallyCenteredString(headerTitle, (double)titleX, 14.0, 26.0, fadedArgb(UiTheme.TEXT_PRIMARY));
      }

      // Unified window controls capsule (- [] x) at top-right with crisp vector strokes
      int capsuleW = 88;
      int capsuleH = 26;
      int capsuleX = width - 16 - capsuleW;
      int ctrlY = 14;
      GlassRenderer.drawRecessedWell((float)capsuleX, (float)ctrlY, (float)capsuleW, (float)capsuleH, UiTheme.CARD_RADIUS, 0.0F, 0.0F, (float)mouseX, (float)mouseY, accentColor, UiTheme.BORDER_THICKNESS);

      int closeX = capsuleX + 60;
      int maxX = capsuleX + 32;
      int minX = capsuleX + 4;
      int ctrlSize = 24;
      int btnY = ctrlY + 1;

      boolean closeHover = RenderUtil.isHovered((float)closeX, (float)btnY, (float)ctrlSize, (float)ctrlSize, mouseX, mouseY);
      boolean maxHover = RenderUtil.isHovered((float)maxX, (float)btnY, (float)ctrlSize, (float)ctrlSize, mouseX, mouseY);
      boolean minHover = RenderUtil.isHovered((float)minX, (float)btnY, (float)ctrlSize, (float)ctrlSize, mouseX, mouseY);
      float closeHovT = UiAnimation.smoothState("win_close_hov", closeHover ? 1.0F : 0.0F, 18.0F);
      float maxHovT = UiAnimation.smoothState("win_max_hov", maxHover ? 1.0F : 0.0F, 18.0F);
      float minHovT = UiAnimation.smoothState("win_min_hov", minHover ? 1.0F : 0.0F, 18.0F);
      float closePress = UiAnimation.smoothState("win_close_prs", closeHover && UiAnimation.isPrimaryMouseDown() ? 1.0F : 0.0F, 30.0F);
      float maxPress = UiAnimation.smoothState("win_max_prs", maxHover && UiAnimation.isPrimaryMouseDown() ? 1.0F : 0.0F, 30.0F);
      float minPress = UiAnimation.smoothState("win_min_prs", minHover && UiAnimation.isPrimaryMouseDown() ? 1.0F : 0.0F, 30.0F);

      if (closeHovT > 0.02F) {
         GlassRenderer.drawSoftShadow((float)closeX, (float)btnY, (float)ctrlSize, (float)ctrlSize, UiTheme.CONTROL_RADIUS, 6.0F, 0.5F, fadedArgb(UiTheme.withAlpha(UiTheme.DANGER_ACCENT, Math.round(95.0F * closeHovT))));
         GlassRenderer.drawInteractiveSurface((float)closeX, (float)btnY, (float)ctrlSize, (float)ctrlSize, UiTheme.CONTROL_RADIUS, fadedArgb(UiTheme.withAlpha(UiTheme.DANGER_ACCENT, Math.round(215.0F * closeHovT))), fadedArgb(UiTheme.withAlpha(0xFF8C1D24, Math.round(215.0F * closeHovT))), fadedArgb(UiTheme.withAlpha(0xFFFFD0D2, Math.round(180.0F * closeHovT))), UiTheme.BORDER_THICKNESS, 0.16F * closeHovT, closeHovT, closePress, true, (float)mouseX, (float)mouseY, UiTheme.DANGER_ACCENT);
      }
      if (maxHovT > 0.02F) {
         GlassRenderer.drawInteractiveSurface((float)maxX, (float)btnY, (float)ctrlSize, (float)ctrlSize, UiTheme.CONTROL_RADIUS, UiTheme.withAlpha(accentColor, Math.round(90.0F * maxHovT)), fadedArgb(UiTheme.withAlpha(UiTheme.accentDeep(), Math.round(80.0F * maxHovT))), fadedArgb(UiTheme.withAlpha(UiTheme.accentSoftGlow(), Math.round(150.0F * maxHovT))), UiTheme.BORDER_THICKNESS, 0.14F * maxHovT, maxHovT, maxPress, true, (float)mouseX, (float)mouseY, accentColor);
      }
      if (minHovT > 0.02F) {
         GlassRenderer.drawInteractiveSurface((float)minX, (float)btnY, (float)ctrlSize, (float)ctrlSize, UiTheme.CONTROL_RADIUS, UiTheme.withAlpha(accentColor, Math.round(90.0F * minHovT)), fadedArgb(UiTheme.withAlpha(UiTheme.accentDeep(), Math.round(80.0F * minHovT))), fadedArgb(UiTheme.withAlpha(UiTheme.accentSoftGlow(), Math.round(150.0F * minHovT))), UiTheme.BORDER_THICKNESS, 0.14F * minHovT, minHovT, minPress, true, (float)mouseX, (float)mouseY, accentColor);
      }

      GlassRenderer.drawVectorClose(
         (float)closeX + (float)ctrlSize * 0.5F, (float)btnY + (float)ctrlSize * 0.5F + closePress * 0.4F, 8.5F, fadedArgb(closeHover ? UiTheme.TEXT_PRIMARY : UiTheme.secondaryTextColor())
      );
      GlassRenderer.drawVectorMaximize(
         (float)maxX + (float)ctrlSize * 0.5F, (float)btnY + (float)ctrlSize * 0.5F + maxPress * 0.4F, 8.5F, fadedArgb(maxHover ? UiTheme.TEXT_PRIMARY : UiTheme.secondaryTextColor())
      );
      GlassRenderer.drawVectorMinimize(
         (float)minX + (float)ctrlSize * 0.5F, (float)btnY + (float)ctrlSize * 0.5F + minPress * 0.4F, 9.0F, fadedArgb(minHover ? UiTheme.TEXT_PRIMARY : UiTheme.secondaryTextColor())
      );

      int actionsRight = capsuleX - 14;
      GlassRenderer.drawSeparator((float)(capsuleX - 7), 18.0F, (float)(capsuleX - 7), 36.0F, 1.0F, fadedArgb(UiTheme.GLASS_DIVIDER));
      if (this.viewingModuleSettings && this.selectedModule != null) {
         this.viewModeBarVisible = false;
         this.drawModuleSettingsHeaderActions(mouseX, mouseY, actionsRight, accentColor, textColor, outlineColor);
      } else if (this.activeSidebarTab != SidebarTab.CONFIGS) {
         int searchLeft = this.drawSearchBar(mouseX, mouseY, actionsRight, accentColor, textColor, outlineColor, windowBlur);
         this.drawModuleViewModeGroup(mouseX, mouseY, searchLeft >= SIDEBAR_WIDTH + 210 ? searchLeft : actionsRight, accentColor);
      } else {
         this.viewModeBarVisible = false;
      }
   }

   private void drawModuleSettingsHeaderActions(int mouseX, int mouseY, int rightEdge, int accentColor, int textColor, int outlineColor) {
      int btnY = 14;
      int btnH = 26;
      float pillRadius = (float)btnH * 0.5F;
      int pillGap = 10;

      if (Augustus.getInstance().isConfigEditingEnabled()) {
         int exportW = Math.round(Math.max(84.0F, FontManager.interMedium.getStringWidth("Export") + 42.0F));
         int exportX = rightEdge - exportW;
         this.headerExportButton.renderPill((float)exportX, (float)btnY, (float)exportW, (float)btnH, "Export", LucideIcon.DOWNLOAD, ModernButton.Variant.GLASS, mouseX, mouseY);

         int importW = Math.round(Math.max(84.0F, FontManager.interMedium.getStringWidth("Import") + 42.0F));
         int importX = exportX - pillGap - importW;
         this.headerImportButton.renderPill((float)importX, (float)btnY, (float)importW, (float)btnH, "Import", LucideIcon.UPLOAD, ModernButton.Variant.GLASS, mouseX, mouseY);

         rightEdge = importX - pillGap;
      }

      // Interactive capsule status pill button with glowing state dot and generous internal padding
      boolean enabled = this.selectedModule.isToggled();
      String statusLabel = enabled ? "Enabled" : "Disabled";
      int toggleW = Math.round(Math.max(96.0F, FontManager.interMedium.getStringWidth("Disabled") + 42.0F));
      int toggleX = rightEdge - toggleW;
      boolean toggleHover = RenderUtil.isHovered((float)toggleX, (float)btnY, (float)toggleW, (float)btnH, mouseX, mouseY);
      float actT = UiAnimation.smoothState("hdr_status_act", enabled ? 1.0F : 0.0F, 18.0F);
      float hovT = UiAnimation.smoothState("hdr_status_hov", toggleHover ? 1.0F : 0.0F, 18.0F);
      float prsT = UiAnimation.smoothState("hdr_status_prs", toggleHover && UiAnimation.isPrimaryMouseDown() ? 1.0F : 0.0F, 30.0F);

      int activeBadgeTop = UiTheme.withAlpha(UiTheme.lerpArgb(accentColor, 0xFF282058, 0.35F), 168);
      int activeBadgeBot = UiTheme.withAlpha(UiTheme.lerpArgb(UiTheme.accentDeep(), 0xFF14112E, 0.45F), 158);
      int offTop = UiTheme.glassRecessedFillArgb(hovT, 0.0F, UiTheme.accentColor());
      int offBot = UiTheme.lerpArgb(offTop, 0xFF0A0A14, 0.25F);
      int topF = fadedArgb(UiTheme.lerpArgb(offTop, activeBadgeTop, actT));
      int botF = fadedArgb(UiTheme.lerpArgb(offBot, activeBadgeBot, actT));
      int borderF = fadedArgb(UiTheme.lerpArgb(UiTheme.glassBorderFocusedOrHover(hovT * 0.7F, accentColor), UiTheme.withAlpha(UiTheme.accentSoftGlow(), 210), actT));

      if (actT > 0.04F) {
         GlassRenderer.drawSoftShadow((float)toggleX, (float)btnY, (float)toggleW, (float)btnH, pillRadius, 7.0F, 1.0F, UiTheme.withAlpha(accentColor, Math.round(52.0F * actT)));
      }
      GlassRenderer.drawInteractiveSurface((float)toggleX, (float)btnY, (float)toggleW, (float)btnH, pillRadius, topF, botF, borderF, UiTheme.BORDER_THICKNESS, UiTheme.glassTopHighlightAlpha(Math.max(hovT, actT)), hovT, prsT, true, (float)mouseX, (float)mouseY, accentColor);

      float dotX = (float)(toggleX + 15);
      float dotY = (float)btnY + (float)btnH * 0.5F + prsT * 0.3F;
      int dotColor = fadedArgb(UiTheme.lerpArgb(UiTheme.mutedTextColor(), UiTheme.enabledColor(), actT));
      if (actT > 0.05F) {
         GlassRenderer.drawCircle(dotX, dotY, 4.8F, fadedArgb(UiTheme.withAlpha(UiTheme.enabledColor(), Math.round(70.0F * actT))));
      }
      GlassRenderer.drawCircle(dotX, dotY, 2.7F, dotColor);

      FontManager.interMedium.drawVerticallyCenteredString(statusLabel, (double)(toggleX + 25), (double)btnY + prsT * 0.3F, (double)btnH, fadedArgb(enabled || toggleHover ? UiTheme.TEXT_PRIMARY : UiTheme.secondaryTextColor()));
      this.headerToggleButton.updateHitBounds((float)toggleX, (float)btnY, (float)toggleW, (float)btnH);
   }

   private int drawSearchBar(int mouseX, int mouseY, int rightEdge, int accentColor, int textColor, int outlineColor, boolean windowBlur) {
      int searchW = Math.min(220, Math.max(130, rightEdge - (SIDEBAR_WIDTH + 300)));
      int searchX = rightEdge - searchW;
      int searchY = 13;
      int searchH = 28;
      if (searchX < SIDEBAR_WIDTH + 210) {
         return -1;
      }

      this.searchInput.renderString(
         (float)searchX,
         (float)searchY,
         (float)searchW,
         (float)searchH,
         this.searchQuery,
         this.searchFocused,
         "Search modules...",
         LucideIcon.SEARCH,
         true,
         mouseX,
         mouseY,
         this.searchCursorTime,
         FontManager.interMedium
      );
      return searchX;
   }

   private void drawModuleViewModeGroup(int mouseX, int mouseY, int rightAnchorX, int accentColor) {
      ModuleViewMode[] modes = ModuleViewMode.values();
      float trayPad = 3.0F;
      float trayH = 28.0F;
      float trayY = 13.0F;
      float btnH = trayH - trayPad * 2.0F;

      // Always icon-only mode
      float rightEdge = (float)(rightAnchorX - 10);
      float trayW = trayPad * 2.0F + modes.length * 28.0F;
      if (rightEdge - trayW < (float)(SIDEBAR_WIDTH + 170)) {
         this.viewModeBarVisible = false;
         this.hoveredViewMode = null;
         return;
      }

      float trayX = rightEdge - trayW;
      this.viewModeTrayY = trayY;
      this.viewModeTrayH = trayH;
      this.viewModeBarVisible = true;

      GlassRenderer.drawRecessedWell(trayX, trayY, trayW, trayH, UiTheme.CARD_RADIUS, 0.0F, 0.0F, (float)mouseX, (float)mouseY, accentColor, UiTheme.BORDER_THICKNESS);

      float curX = trayX + trayPad;
      int activeIdx = this.moduleViewMode.ordinal();
      for (int i = 0; i < modes.length; i++) {
         float bw = 28.0F;
         this.viewModeBtnX[i] = curX;
         this.viewModeBtnW[i] = bw;
         curX += bw;
      }

      // Sliding active pill indicator
      float targetX = this.viewModeBtnX[activeIdx];
      float targetW = this.viewModeBtnW[activeIdx];
      float slideX = UiAnimation.smoothState("mod_view_slide_x", targetX, targetX, 20.0F);
      float slideW = UiAnimation.smoothState("mod_view_slide_w", targetW, targetW, 20.0F);
      float slideY = trayY + trayPad;

      boolean actHov = RenderUtil.isHovered(targetX, slideY, targetW, btnH, mouseX, mouseY);
      float actHovT = UiAnimation.smoothState("mod_view_act_hov", actHov ? 1.0F : 0.0F, 16.0F);
      float actPrsT = UiAnimation.smoothState("mod_view_act_prs", actHov && UiAnimation.isPrimaryMouseDown() ? 1.0F : 0.0F, 30.0F);

      GlassRenderer.drawSoftShadow(slideX, slideY, slideW, btnH, 5.5F, 6.0F, 1.0F, UiTheme.withAlpha(accentColor, 64));
      int pillTop = UiTheme.withAlpha(UiTheme.lerpArgb(UiTheme.accentColor(), 0xFFEAE5FF, 0.12F + 0.10F * actHovT), Math.round(220.0F + 24.0F * actHovT));
      int pillBot = UiTheme.withAlpha(UiTheme.lerpArgb(UiTheme.accentDeep(), 0xFF13102B, 0.30F), Math.round(215.0F + 20.0F * actHovT));
      GlassRenderer.drawInteractiveSurface(
         slideX,
         slideY,
         slideW,
         btnH,
         5.5F,
         pillTop,
         pillBot,
         fadedArgb(UiTheme.withAlpha(UiTheme.accentSoftGlow(), 220)),
         1.0F,
         0.22F,
         actHovT,
         actPrsT,
         true,
         (float)mouseX,
         (float)mouseY,
         accentColor
      );

      boolean anyBtnHovered = false;
      for (int i = 0; i < modes.length; i++) {
         ModuleViewMode mode = modes[i];
         float bx = this.viewModeBtnX[i];
         float bw = this.viewModeBtnW[i];
         boolean active = i == activeIdx;
         boolean hovered = RenderUtil.isHovered(bx, slideY, bw, btnH, mouseX, mouseY);
         boolean pressed = hovered && UiAnimation.isPrimaryMouseDown();
         float hovT = UiAnimation.smoothState("mod_view_hov_" + i, hovered ? 1.0F : 0.0F, 16.0F);
         float prsT = UiAnimation.smoothState("mod_view_prs_" + i, pressed ? 1.0F : 0.0F, 30.0F);
         float actT = UiAnimation.smoothState("mod_view_act_" + i, active ? 1.0F : 0.0F, 18.0F);

         if (!active && hovT > 0.03F) {
            int hovFill = UiTheme.withAlpha(0xFF282644, Math.round(92.0F * hovT));
            int hovBot = UiTheme.lerpArgb(hovFill, 0xFF100F1C, 0.30F);
            GlassRenderer.drawInteractiveSurface(
               bx,
               slideY,
               bw,
               btnH,
               5.5F,
               hovFill,
               hovBot,
               fadedArgb(UiTheme.withAlpha(UiTheme.accentSoftGlow(), Math.round(130.0F * hovT))),
               1.0F,
               UiTheme.glassTopHighlightAlpha(hovT) * hovT,
               hovT,
               prsT,
               true,
               (float)mouseX,
               (float)mouseY,
               accentColor
            );
         }

         float yNudge = prsT * 0.4F;
         int fg = fadedArgb(UiTheme.lerpArgb(UiTheme.secondaryTextColor(), UiTheme.TEXT_PRIMARY, Math.max(actT, hovT)));
         mode.icon.drawCentered(bx + bw * 0.5F, slideY + btnH * 0.5F + yNudge, 13.0F, fg);

         // Track hovered mode for tooltip
         if (hovered) {
            this.hoveredViewMode = mode;
            anyBtnHovered = true;
         }
      }
      if (!anyBtnHovered) {
         this.hoveredViewMode = null;
      }
   }

   private void drawCategoryPills(int mouseX, int mouseY, int width, int accentColor, int textColor, int outlineColor) {
      int barLeft = SIDEBAR_WIDTH + CONTENT_PADDING;
      int barRight = width - CONTENT_PADDING;
      GlassRenderer.drawSeparator((float)barLeft, (float)(CATEGORY_BAR_Y + CATEGORY_BAR_HEIGHT + 4), (float)barRight, (float)(CATEGORY_BAR_Y + CATEGORY_BAR_HEIGHT + 4), UiTheme.SEPARATOR_THICKNESS, UiTheme.GLASS_DIVIDER);
      RenderUtil.scissorRectScaled(this.contentToScreenX((float)barLeft), this.contentToScreenY((float)(CATEGORY_BAR_Y - 2)), this.contentToScreenX((float)barRight), this.contentToScreenY((float)(CATEGORY_BAR_Y + CATEGORY_BAR_HEIGHT + 6)), 1.0);

      int cursorX = barLeft + this.categoryScrollOffset;
      int y = CATEGORY_BAR_Y;
      int pillH = 26;

      // "All" pill
      int allW = (int)FontManager.interMedium.getStringWidth("All") + 38;
      boolean allHover = RenderUtil.isHovered((float)cursorX, (float)y, (float)allW, (float)pillH, mouseX, mouseY) && !this.hoveringCategoryContextMenu;
      ModernTabs.drawPillTab((float)cursorX, (float)y, (float)allW, (float)pillH, "All", LucideIcon.LAYOUT_GRID, this.allCategoriesSelected, allHover, mouseX, mouseY);
      cursorX += allW + 8;

      int contentRight = allW + 8;
      for (CategoryTabButton tab : (Iterable<CategoryTabButton>)(this.categoryTabs)) {
         String name = this.formatCategoryName(tab.getCategorys());
         int pillW = (int)FontManager.interMedium.getStringWidth(name) + 38;
         tab.left = cursorX;
         tab.top = y;
         tab.right = cursorX + pillW;
         tab.bottom = y + pillH;
         tab.width = pillW;
         tab.height = pillH;

         boolean selected = !this.allCategoriesSelected && tab == this.selectedCategory;
         boolean hovered = RenderUtil.isHovered((float)cursorX, (float)y, (float)pillW, (float)pillH, mouseX, mouseY) && !this.hoveringCategoryContextMenu;
         ModernTabs.drawPillTab((float)cursorX, (float)y, (float)pillW, (float)pillH, name, LucideIcon.forCategory(tab.getCategorys()), selected, hovered, mouseX, mouseY);
         cursorX += pillW + 8;
         contentRight += pillW + 8;
      }

      int visibleW = barRight - barLeft;
      this.categoryScrollOffset = this.categoryScrollBar.updateScrollOffset(
         contentRight, barLeft, barRight, this.categoryScrollOffset, barLeft, CATEGORY_BAR_Y, visibleW, pillH, mouseX, mouseY
      );
   }

   private void drawModuleGrid(int mouseX, int mouseY, int width, int height, int accentColor, int textColor, int outlineColor, boolean windowBlur) {
      int areaLeft = SIDEBAR_WIDTH + CONTENT_PADDING;
      int areaRight = width - CONTENT_PADDING;
      int areaTop = GRID_TOP_Y;
      int areaBottom = height - 16;
      int areaW = Math.max(200, areaRight - areaLeft);
      int areaH = Math.max(100, areaBottom - areaTop);

      this.rebuildFilteredModules();

      RenderUtil.scissorRectScaled(this.contentToScreenX((float)(areaLeft - 2)), this.contentToScreenY((float)areaTop), this.contentToScreenX((float)(areaRight + 2)), this.contentToScreenY((float)areaBottom), 1.0);

      int totalContentH = 0;
      Module nextHovered = null;
      boolean inBounds = RenderUtil.isHovered((float)areaLeft, (float)areaTop, (float)(areaW - 12), (float)areaH, mouseX, mouseY);
      boolean fadeCaptured = ScrollFadeCompositor.beginRegion(
         this.contentToScreenX((float)(areaLeft - 4)), this.contentToScreenY((float)areaTop), this.contentSpanToScreen((float)(areaW + 8)), this.contentSpanToScreen((float)areaH)
      );

      try {
         if (this.moduleViewMode == ModuleViewMode.GRID) {
            int gap = 12;
            int cols = Math.max(2, Math.min(4, (areaW - 14 + gap) / (212 + gap)));
            int cardW = (areaW - 14 - gap * (cols - 1)) / cols;
            int cardH = 104;
            int totalRows = (this.filteredModules.size() + cols - 1) / cols;
            totalContentH = totalRows * (cardH + gap);

            for (int i = 0; i < this.filteredModules.size(); i++) {
               Module module = this.filteredModules.get(i);
               int col = i % cols;
               int row = i / cols;
               int cardX = areaLeft + col * (cardW + gap);
               int cardY = areaTop + row * (cardH + gap) + this.moduleScrollOffset;
               if (cardY + cardH < areaTop - 10 || cardY > areaBottom + 10) {
                  continue;
               }
               boolean cardHover = inBounds && !this.hoveringModuleContextMenu && !this.hoveringCategoryContextMenu
                  && RenderUtil.isHovered((float)cardX, (float)cardY, (float)cardW, (float)cardH, mouseX, mouseY);
               if (cardHover) {
                  nextHovered = module;
               }
               this.drawModuleGridCard(module, cardX, cardY, cardW, cardH, cardHover, mouseX, mouseY, accentColor, outlineColor, windowBlur);
            }
         } else {
            boolean compact = this.moduleViewMode == ModuleViewMode.COMPACT;
            int rowH = compact ? 34 : 58;
            int gap = compact ? 5 : 8;

            if (compact) {
               // Two-column compact layout
               int colGap = 10;
               int colW = (areaW - 14 - colGap) / 2;
               int totalRows = (this.filteredModules.size() + 1) / 2;
               totalContentH = totalRows * (rowH + gap);

               for (int i = 0; i < this.filteredModules.size(); i++) {
                  Module module = this.filteredModules.get(i);
                  int col = i % 2;
                  int row = i / 2;
                  int colX = areaLeft + col * (colW + colGap);
                  int rowY = areaTop + row * (rowH + gap) + this.moduleScrollOffset;
                  if (rowY + rowH < areaTop - 10 || rowY > areaBottom + 10) {
                     continue;
                  }
                  boolean rowHover = inBounds && !this.hoveringModuleContextMenu && !this.hoveringCategoryContextMenu
                     && RenderUtil.isHovered((float)colX, (float)rowY, (float)colW, (float)rowH, mouseX, mouseY);
                  if (rowHover) {
                     nextHovered = module;
                  }
                  this.drawModuleListOrCompactRow(module, colX, rowY, colW, rowH, compact, rowHover, mouseX, mouseY, accentColor, outlineColor, windowBlur);
               }
            } else {
               // Single-column list layout
               int rowW = areaW - 14;
               totalContentH = this.filteredModules.size() * (rowH + gap);

               for (int i = 0; i < this.filteredModules.size(); i++) {
                  Module module = this.filteredModules.get(i);
                  int rowY = areaTop + i * (rowH + gap) + this.moduleScrollOffset;
                  if (rowY + rowH < areaTop - 10 || rowY > areaBottom + 10) {
                     continue;
                  }
                  boolean rowHover = inBounds && !this.hoveringModuleContextMenu && !this.hoveringCategoryContextMenu
                     && RenderUtil.isHovered((float)areaLeft, (float)rowY, (float)rowW, (float)rowH, mouseX, mouseY);
                  if (rowHover) {
                     nextHovered = module;
                  }
                  this.drawModuleListOrCompactRow(module, areaLeft, rowY, rowW, rowH, compact, rowHover, mouseX, mouseY, accentColor, outlineColor, windowBlur);
               }
            }
         }

         if (this.filteredModules.isEmpty()) {
            String emptyMsg = "No modules match the current filter.";
            float ew = FontManager.interMedium.getStringWidth(emptyMsg);
            FontManager.interMedium.drawString(emptyMsg, (double)((float)areaLeft + ((float)areaW - ew) * 0.5F), (double)(areaTop + 40), fadedArgb(UiTheme.secondaryTextColor()));
         }
      } finally {
         if (fadeCaptured) {
            float topFade = this.moduleScrollOffset < -4 ? 14.0F : 0.0F;
            float bottomFade = (totalContentH + this.moduleScrollOffset) > (areaH - 2) ? 26.0F : 0.0F;
            ScrollFadeCompositor.endAndComposite((float)(areaLeft - 14), (float)areaTop, (float)(areaW + 28), (float)areaH, topFade, bottomFade);
         }
      }

      this.moduleScrollBar.drawVerticalBar(areaRight - 8, areaTop, areaRight, areaBottom, areaTop - this.moduleScrollOffset, totalContentH, accentColor);
      this.moduleScrollOffset = this.moduleScrollBar.updateScrollOffset(
         totalContentH, areaTop, areaBottom, this.moduleScrollOffset, areaLeft, areaTop, areaW, areaH, mouseX, mouseY
      );

      // Tooltip tracking on hovered module
      if (ClientManagers.moduleManager.clickGui.moduleDescriptions.getValue()) {
         if (nextHovered == null) {
            this.timeHelper.reset();
            this.tooltipX = -1337;
            this.tooltipY = -1337;
            this.hoveredModule = null;
         } else {
            if (this.hoveredModule != nextHovered) {
               this.timeHelper.reset();
               this.tooltipX = -1337;
               this.tooltipY = -1337;
               this.hoveredModule = nextHovered;
            }
            if (!nextHovered.getDescription().isEmpty() && this.timeHelper.reached(750L)) {
               if (this.tooltipX == -1337 && this.tooltipY == -1337) {
                  this.tooltipX = mouseX;
                  this.tooltipY = mouseY;
               }
               GL11.glDisable(GL11.GL_SCISSOR_TEST);
               drawTooltip(FontManager.interMedium, nextHovered.getDescription(), this.tooltipX, this.tooltipY);
               GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
         }
      }
   }

   private void drawModuleSurface(
      float x,
      float y,
      float w,
      float h,
      float radius,
      float hoverT,
      float enableT,
      float pressT,
      boolean hovered,
      boolean listeningBind,
      int mouseX,
      int mouseY,
      int accentColor,
      int outlineColor,
      boolean windowBlur
   ) {
      int idleOrActiveBorder = UiTheme.lerpArgb(UiTheme.glassBorderColor(), UiTheme.withAlpha(UiTheme.accentSoftGlow(), 195), enableT);
      int cardBorder = fadedArgb(listeningBind ? 0xFFF59E0B : UiTheme.lerpArgb(idleOrActiveBorder, UiTheme.withAlpha(UiTheme.accentSoftGlow(), 240), hoverT));
      float borderW = hovered || listeningBind ? UiTheme.THICK_BORDER_THICKNESS : UiTheme.BORDER_THICKNESS;
      float blurR = ClientManagers.moduleManager.clickGui.getBlurRadius();
      if (!windowBlur && ClientManagers.moduleManager.clickGui.isOptionBlurActive() && blurR > 0.05F) {
         GlassRenderer.drawOptionGlass(x, y, w, h, radius, borderW, cardBorder);
      } else {
         GlassRenderer.drawGlassCardWithHover(x, y, w, h, radius, hoverT, enableT, listeningBind ? 1.0F : 0.0F, pressT, (float)mouseX, (float)mouseY, accentColor, cardBorder, borderW);
      }
   }

   private ModernToggleSwitch getModuleRowSwitch(Module module) {
      return this.moduleListSwitches.computeIfAbsent(module, m -> new ModernToggleSwitch());
   }

   private boolean shouldShowRowKeybind(Module module, boolean listeningBind) {
      return listeningBind || module.getKeyCode() != 0 || this.activeSidebarTab == SidebarTab.KEYBINDS;
   }

   private float computeRowKeybindWidth(Module module, boolean listeningBind) {
      String keyLabel = listeningBind ? "Key..." : KeyboardInputUtil.getKeyDisplayName(module.getKeyCode());
      return Math.max(30.0F, FontManager.interSmall.getStringWidth(keyLabel) + 14.0F);
   }

   private void drawModuleListOrCompactRow(
      Module module,
      int rowX,
      int rowY,
      int rowW,
      int rowH,
      boolean compact,
      boolean rowHover,
      int mouseX,
      int mouseY,
      int accentColor,
      int outlineColor,
      boolean windowBlur
   ) {
      boolean enabled = module.isToggled();
      boolean listeningBind = this.keybindListeningModule == module;
      float[] anim = this.getCardAnimState(module, rowHover, enabled);
      float hoverT = anim[0];
      float enableT = anim[1];
      float pressT = anim[2];

      float radius = compact ? UiTheme.CONTROL_RADIUS : UiTheme.CARD_RADIUS;
      this.drawModuleSurface((float)rowX, (float)rowY, (float)rowW, (float)rowH, radius, hoverT, enableT, pressT, rowHover, listeningBind, mouseX, mouseY, accentColor, outlineColor, windowBlur);

      // Left active accent indicator bar
      if (enableT > 0.04F) {
         float barH = ((float)rowH - (compact ? 12.0F : 18.0F)) * enableT;
         float barY = (float)rowY + ((float)rowH - barH) * 0.5F;
         GlassRenderer.drawRoundedRect((float)rowX + 3.0F, barY, 3.0F, barH, 1.5F, fadedArgb(UiTheme.withAlpha(UiTheme.accentSoftGlow(), Math.round(235.0F * enableT))));
      }

      // Right controls cluster: [Keybind pill] [Settings gear button] [Toggle switch]
      float rightPad = compact ? 11.0F : 14.0F;
      float switchW = compact ? 32.0F : 36.0F;
      float switchH = compact ? 16.0F : 18.0F;
      float switchX = (float)(rowX + rowW) - rightPad - switchW;
      float switchY = (float)rowY + ((float)rowH - switchH) * 0.5F;
      this.getModuleRowSwitch(module).render(switchX, switchY, switchW, switchH, enabled, mouseX, mouseY);

      float gearSize = compact ? 22.0F : 26.0F;
      float gearX = switchX - (compact ? 6.0F : 8.0F) - gearSize;
      float gearY = (float)rowY + ((float)rowH - gearSize) * 0.5F;
      boolean gearHover = rowHover && RenderUtil.isHovered(gearX, gearY, gearSize, gearSize, mouseX, mouseY);
      float gearHovT = UiAnimation.smoothState("row_gear_" + module.getName(), gearHover ? 1.0F : 0.0F, 18.0F);
      if (gearHovT > 0.03F) {
         int gFill = UiTheme.glassControlFillArgb(gearHovT, 0.0F, accentColor);
         GlassRenderer.drawInteractiveSurface(
            gearX, gearY, gearSize, gearSize, 6.0F,
            UiTheme.scaleAlpha(gFill, gearHovT),
            UiTheme.scaleAlpha(UiTheme.lerpArgb(gFill, 0xFF0A0A14, 0.25F), gearHovT),
            UiTheme.withAlpha(UiTheme.accentSoftGlow(), Math.round(180.0F * gearHovT)),
            1.0F, 0.14F * gearHovT, gearHovT, 0.0F, true, (float)mouseX, (float)mouseY, accentColor
         );
      }
      int gearCol = fadedArgb(UiTheme.lerpArgb(UiTheme.mutedTextColor(), UiTheme.accentSoftGlow(), Math.max(gearHovT, hoverT * 0.35F)));
      LucideIcon.SETTINGS.drawCentered(gearX + gearSize * 0.5F, gearY + gearSize * 0.5F, compact ? 12.5F : 14.0F, gearCol);

      float clusterLeft = gearX;
      if (this.shouldShowRowKeybind(module, listeningBind)) {
         String keyLabel = listeningBind ? "Key..." : KeyboardInputUtil.getKeyDisplayName(module.getKeyCode());
         float badgeW = this.computeRowKeybindWidth(module, listeningBind);
         float badgeH = compact ? 18.0F : 22.0F;
         float badgeX = gearX - (compact ? 6.0F : 8.0F) - badgeW;
         float badgeY = (float)rowY + ((float)rowH - badgeH) * 0.5F;
         boolean bindHover = rowHover && RenderUtil.isHovered(badgeX, badgeY, badgeW, badgeH, mouseX, mouseY);
         int badgeBorder = fadedArgb(listeningBind ? 0xFFF59E0B : (bindHover ? UiTheme.withAlpha(UiTheme.accentSoftGlow(), 210) : UiTheme.GLASS_BORDER));
         int badgeFill = fadedArgb(listeningBind ? UiTheme.withAlpha(0xF59E0B, 60) : UiTheme.glassRecessedFillArgb(bindHover ? 0.7F : hoverT * 0.3F, 0.0F, accentColor));
         GlassRenderer.drawRoundedBox(badgeX, badgeY, badgeW, badgeH, 5.5F, badgeFill, badgeBorder, 1.1F);
         FontManager.interSmall.drawCenteredStringInBox(keyLabel, (double)badgeX, (double)badgeY, (double)badgeW, (double)badgeH, fadedArgb(listeningBind ? 0xFFFDE68A : (bindHover ? UiTheme.TEXT_PRIMARY : UiTheme.secondaryTextColor())));
         clusterLeft = badgeX;
      }

      // Left icon & text block
      float textLeft;
      if (compact) {
         // No icon in compact mode — text starts directly
         textLeft = (float)rowX + 14.0F;
      } else {
         // No icon in list mode — text starts with left padding
         textLeft = (float)rowX + 16.0F;
      }

      float textMaxW = Math.max(60.0F, clusterLeft - 14.0F - textLeft);
      int nameColor = fadedArgb(UiTheme.lerpArgb(UiTheme.secondaryTextColor(), UiTheme.TEXT_PRIMARY, Math.max(enableT, hoverT)));
      String catLabel = this.formatCategoryName(module.getCategorys());
      float catPillW = FontManager.interSmall.getStringWidth(catLabel) + 12.0F;

      if (compact) {
         float maxTitleW = Math.max(50.0F, textMaxW - catPillW - 10.0F);
         String safeTitle = ModernSettingsPanel.trimToWidth(FontManager.interSemiBold, module.getModuleName(), maxTitleW);
         FontManager.interSemiBold.drawVerticallyCenteredString(safeTitle, (double)textLeft, (double)rowY + pressT * 0.3, (double)rowH, nameColor);

         float pillX = textLeft + FontManager.interSemiBold.getStringWidth(safeTitle) + 8.0F;
         if (pillX + catPillW <= textLeft + textMaxW) {
            float pillH = 16.0F;
            float pillY = (float)rowY + ((float)rowH - pillH) * 0.5F;
            GlassRenderer.drawRoundedBox(pillX, pillY, catPillW, pillH, 4.5F, fadedArgb(UiTheme.glassRecessedFillArgb(0.0F, 0.0F, accentColor)), fadedArgb(UiTheme.GLASS_BORDER), 0.9F);
            FontManager.interSmall.drawCenteredStringInBox(catLabel, (double)pillX, (double)pillY, (double)catPillW, (double)pillH, fadedArgb(UiTheme.mutedTextColor()));
         }
      } else {
         float titleY = (float)rowY + 11.0F + pressT * 0.3F;
         float maxTitleW = Math.max(60.0F, textMaxW - catPillW - 10.0F);
         String safeTitle = ModernSettingsPanel.trimToWidth(FontManager.interSemiBold, module.getModuleName(), maxTitleW);
         FontManager.interSemiBold.drawString(safeTitle, (double)textLeft, (double)titleY, nameColor);

         float pillX = textLeft + FontManager.interSemiBold.getStringWidth(safeTitle) + 8.0F;
         float pillY = titleY + (FontManager.interSemiBold.getFontHeight() - 16.0F) * 0.5F;
         if (pillX + catPillW <= textLeft + textMaxW) {
            GlassRenderer.drawRoundedBox(pillX, pillY, catPillW, 16.0F, 4.5F, UiTheme.withAlpha(accentColor, 34), fadedArgb(UiTheme.withAlpha(UiTheme.accentSoftGlow(), 125)), 0.95F);
            FontManager.interSmall.drawCenteredStringInBox(catLabel, (double)pillX, (double)pillY, (double)catPillW, 16.0, fadedArgb(UiTheme.accentSoftGlow()));
         }

         String rawDesc = module.getDescription() != null ? module.getDescription().trim() : "";
         String sub = rawDesc.isEmpty() ? catLabel + " module — Click to toggle" : rawDesc;
         String safeSub = ModernSettingsPanel.trimToWidth(FontManager.interRegular, sub, textMaxW);
         FontManager.interRegular.drawString(safeSub, (double)textLeft, (double)(titleY + FontManager.interSemiBold.getFontHeight() + 4.0F), fadedArgb(UiTheme.mutedTextColor()));
      }
   }

   private void drawModuleGridCard(
      Module module,
      int cardX,
      int cardY,
      int cardW,
      int cardH,
      boolean cardHover,
      int mouseX,
      int mouseY,
      int accentColor,
      int outlineColor,
      boolean windowBlur
   ) {
      boolean enabled = module.isToggled();
      boolean listeningBind = this.keybindListeningModule == module;
      float[] anim = this.getCardAnimState(module, cardHover, enabled);
      float hoverT = anim[0];
      float enableT = anim[1];
      float pressT = anim[2];

      this.drawModuleSurface((float)cardX, (float)cardY, (float)cardW, (float)cardH, UiTheme.CARD_RADIUS, hoverT, enableT, pressT, cardHover, listeningBind, mouseX, mouseY, accentColor, outlineColor, windowBlur);

      // Subtle left active edge indicator
      if (enableT > 0.04F) {
         float barH = ((float)cardH - 24.0F) * enableT;
         float barY = (float)cardY + ((float)cardH - barH) * 0.5F;
         GlassRenderer.drawRoundedRect((float)cardX + 3.0F, barY, 2.5F, barH, 1.25F, fadedArgb(UiTheme.withAlpha(UiTheme.accentSoftGlow(), Math.round(230.0F * enableT))));
      }

      float padX = 14.0F;
      float textLeft = (float)cardX + padX;
      int gearX = cardX + cardW - 36;
      int gearY = cardY + 10;
      float gearSize = 24.0F;
      boolean gearHover = cardHover && RenderUtil.isHovered((float)gearX, (float)gearY, gearSize, gearSize, mouseX, mouseY);
      float gearHovT = UiAnimation.smoothState("grid_gear_" + module.getName(), gearHover ? 1.0F : 0.0F, 18.0F);
      float gearVisT = Math.max(hoverT, gearHovT);

      // Clear hover settings control (quiet at rest so top corners stay clean and minimal)
      if (gearVisT > 0.04F) {
         int gFill = UiTheme.scaleAlpha(UiTheme.glassControlFillArgb(gearHovT, 0.0F, accentColor), gearVisT);
         int gBot = UiTheme.scaleAlpha(UiTheme.lerpArgb(gFill, 0xFF0A0C12, 0.28F), gearVisT);
         int gBorder = fadedArgb(UiTheme.lerpArgb(UiTheme.withAlpha(UiTheme.GLASS_BORDER, Math.round(145.0F * gearVisT)), UiTheme.withAlpha(UiTheme.accentSoftGlow(), 215), gearHovT));
         GlassRenderer.drawInteractiveSurface(
            (float)gearX,
            (float)gearY,
            gearSize,
            gearSize,
            UiTheme.CONTROL_RADIUS,
            gFill,
            gBot,
            gBorder,
            UiTheme.BORDER_THICKNESS,
            0.12F * gearVisT,
            gearHovT,
            0.0F,
            true,
            (float)mouseX,
            (float)mouseY,
            accentColor
         );
         int gearCol = fadedArgb(UiTheme.scaleAlpha(UiTheme.lerpArgb(UiTheme.secondaryTextColor(), UiTheme.accentSoftGlow(), gearHovT), gearVisT));
         LucideIcon.SETTINGS.drawCentered((float)gearX + gearSize * 0.5F, (float)gearY + gearSize * 0.5F, 13.0F, gearCol);
      }

      // Header: Module Title (SemiBold)
      float titleMaxW = Math.max(60.0F, (float)gearX - textLeft - 8.0F);
      String safeName = ModernSettingsPanel.trimToWidth(FontManager.interSemiBold, module.getModuleName(), titleMaxW);
      int titleColor = fadedArgb(UiTheme.lerpArgb(UiTheme.TEXT_SECONDARY, UiTheme.TEXT_PRIMARY, Math.max(enableT, hoverT)));
      float titleY = (float)cardY + 12.0F + pressT * 0.35F;
      FontManager.interSemiBold.drawString(safeName, (double)textLeft, (double)titleY, titleColor);

      // Middle: Clean 2-line description (Regular #94A3B8)
      String catLabel = this.formatCategoryName(module.getCategorys());
      String rawDesc = module.getDescription() != null ? module.getDescription().trim() : "";
      String descSource = rawDesc.isEmpty() ? catLabel + " module" : rawDesc;
      float descMaxW = Math.max(80.0F, (float)cardW - padX * 2.0F);
      List<String> lines = TextWrapper.wrapText(descSource, (int)descMaxW, FontManager.interRegular);
      float descY = titleY + FontManager.interSemiBold.getFontHeight() + 5.0F;
      float lineStep = FontManager.interRegular.getFontHeight() + 2.5F;
      int descCol = fadedArgb(UiTheme.lerpArgb(UiTheme.mutedTextColor(), UiTheme.TEXT_SECONDARY, hoverT * 0.45F));
      for (int li = 0; li < Math.min(2, lines.size()); li++) {
         String lineText = lines.get(li);
         if (li == 1 && lines.size() > 2) {
            lineText = ModernSettingsPanel.trimToWidth(FontManager.interRegular, lineText + "...", descMaxW);
         } else {
            lineText = ModernSettingsPanel.trimToWidth(FontManager.interRegular, lineText, descMaxW);
         }
         FontManager.interRegular.drawString(lineText, (double)textLeft, (double)(descY + (float)li * lineStep), descCol);
      }

      // Bottom footer: [Category pill] [Optional Keybind pill]           [Active status indicator]
      float footerH = 18.0F;
      float footerY = (float)(cardY + cardH) - 11.0F - footerH;
      float cursorX = textLeft;

      float catW = FontManager.interSmall.getStringWidth(catLabel) + 12.0F;
      int catFill = fadedArgb(UiTheme.lerpArgb(UiTheme.glassRecessedFillArgb(0.0F, 0.0F, accentColor), UiTheme.withAlpha(accentColor, 42), enableT));
      int catBorder = fadedArgb(UiTheme.lerpArgb(UiTheme.GLASS_BORDER, UiTheme.withAlpha(UiTheme.accentSoftGlow(), 135), enableT));
      int catFg = fadedArgb(UiTheme.lerpArgb(UiTheme.mutedTextColor(), UiTheme.accentSoftGlow(), enableT));
      GlassRenderer.drawRoundedBox(cursorX, footerY, catW, footerH, 5.0F, catFill, catBorder, UiTheme.BORDER_THICKNESS);
      FontManager.interSmall.drawCenteredStringInBox(catLabel, (double)cursorX, (double)footerY, (double)catW, (double)footerH, catFg);
      cursorX += catW + 6.0F;

      if (this.shouldShowRowKeybind(module, listeningBind)) {
         String keyLabel = listeningBind ? "..." : KeyboardInputUtil.getKeyDisplayName(module.getKeyCode());
         float badgeW = Math.max(24.0F, FontManager.interSmall.getStringWidth(keyLabel) + 10.0F);
         if (cursorX + badgeW <= (float)(cardX + cardW) - 70.0F) {
            int badgeBorder = fadedArgb(listeningBind ? 0xFFF59E0B : UiTheme.GLASS_BORDER);
            int badgeFill = fadedArgb(listeningBind ? UiTheme.withAlpha(0xF59E0B, 55) : UiTheme.glassRecessedFillArgb(hoverT * 0.4F, 0.0F, accentColor));
            GlassRenderer.drawRoundedBox(cursorX, footerY, badgeW, footerH, 5.0F, badgeFill, badgeBorder, UiTheme.BORDER_THICKNESS);
            FontManager.interSmall.drawCenteredStringInBox(keyLabel, (double)cursorX, (double)footerY, (double)badgeW, (double)footerH, fadedArgb(listeningBind ? 0xFFFDE68A : UiTheme.secondaryTextColor()));
         }
      }

      // Clean subtle status indicator on bottom-right
      String statusText = enabled ? "Active" : (cardHover ? "Enable" : "Off");
      float statusTextW = FontManager.interSmall.getStringWidth(statusText);
      float statusRight = (float)(cardX + cardW) - padX;
      float dotR = 2.6F;
      float dotCx = statusRight - statusTextW - 7.0F - dotR;
      float dotCy = footerY + footerH * 0.5F;
      int dotCol = fadedArgb(UiTheme.lerpArgb(UiTheme.withAlpha(UiTheme.mutedTextColor(), 130), UiTheme.enabledColor(), enableT));
      if (enableT > 0.05F) {
         GlassRenderer.drawCircle(dotCx, dotCy, 4.4F, fadedArgb(UiTheme.withAlpha(UiTheme.enabledColor(), Math.round(54.0F * enableT))));
      }
      GlassRenderer.drawCircle(dotCx, dotCy, dotR, dotCol);
      int statusFg = fadedArgb(UiTheme.lerpArgb(UiTheme.lerpArgb(UiTheme.disabledTextColor(), UiTheme.secondaryTextColor(), hoverT), UiTheme.TEXT_PRIMARY, enableT));
      FontManager.interSmall.drawVerticallyCenteredString(statusText, (double)(statusRight - statusTextW), (double)footerY, (double)footerH, statusFg);
   }

   private float[] getCardAnimState(Module module, boolean hovered, boolean enabled) {
      float[] state = this.moduleCardAnims.get(module);
      if (state == null) {
         state = new float[]{hovered ? 1.0F : 0.0F, enabled ? 1.0F : 0.0F, 0.0F};
         this.moduleCardAnims.put(module, state);
      }
      boolean pressed = hovered && UiAnimation.isPrimaryMouseDown();
      state[0] = UiAnimation.smooth(state[0], hovered ? 1.0F : 0.0F, 16.0F);
      state[1] = UiAnimation.smooth(state[1], enabled ? 1.0F : 0.0F, 18.0F);
      state[2] = UiAnimation.smooth(state[2], pressed ? 1.0F : 0.0F, pressed ? 32.0F : 20.0F);
      return state;
   }

   private static int dividerColorForCard(boolean hovered, int accent) {
      return hovered ? UiTheme.withAlpha(accent, 190) : UiTheme.GLASS_DIVIDER;
   }

   private void drawSettingsContent(int mouseX, int mouseY, int width, int height) {
      int panelX = SETTINGS_OFFSET_X;
      int panelY = SETTINGS_OFFSET_Y;
      int panelW = width - SETTINGS_OFFSET_X - 16;
      int panelH = height - SETTINGS_OFFSET_Y - 16;

      GL11.glPushMatrix();
      RenderUtil.scissorRectScaled(
         this.contentToScreenX((float)(panelX - 4)),
         this.contentToScreenY((float)panelY),
         this.contentToScreenX((float)(panelX + panelW)),
         this.contentToScreenY((float)(panelY + panelH)),
         1.0
      );
      if (Augustus.getInstance().getMouseWheelTracker().lastDelta != 0
         && RenderUtil.isHovered((float)panelX, (float)panelY, (float)panelW, (float)panelH, mouseX, mouseY)) {
         this.contextMenuSetting = null;
      }

      GL11.glTranslatef((float)panelX, (float)panelY, 0.0F);
      // The panel is handed its origin in CONTENT coordinates (not framebuffer): its own clip helpers
      // map those through the same content zoom this drawing uses, which is what keeps the settings
      // clip rects correct at every GUI Scale.
      this.settingsPanel.drawPanel(
         mouseX - panelX,
         mouseY - panelY,
         panelW,
         panelH,
         panelX,
         panelY,
         this.cornerRadius,
         this.selectedModule,
         this.hoveringModuleContextMenu
      );
      GL11.glPopMatrix();
   }

   private void drawConfigsContent(int mouseX, int mouseY, int width, int height, int accentColor, int textColor, int outlineColor) {
      ConfigSystemPanel configPanel = Augustus.getInstance().getClickGui().getConfigPanel();
      configPanel.ensureConfigsLoaded();

      int areaLeft = SIDEBAR_WIDTH + CONTENT_PADDING;
      int areaRight = width - CONTENT_PADDING;
      int topBarY = 66;
      String[] labels = new String[]{"Create", "Load", "Save", "Folder", "Refresh", "Delete"};
      LucideIcon[] icons = new LucideIcon[]{
         LucideIcon.PLUS, LucideIcon.UPLOAD, LucideIcon.DOWNLOAD, LucideIcon.FOLDER, LucideIcon.REFRESH_CW, LucideIcon.TRASH_2
      };
      int btnX = areaLeft;
      for (int i = 0; i < labels.length; i++) {
         int btnW = (int)FontManager.interMedium.getStringWidth(labels[i]) + 34;
         ModernButton.Variant variant = i == 0 ? ModernButton.Variant.PRIMARY : (i == 5 ? ModernButton.Variant.DANGER : ModernButton.Variant.GLASS);
         this.configBarButtons[i].render((float)btnX, (float)topBarY, (float)btnW, 26.0F, labels[i], icons[i], variant, mouseX, mouseY);
         btnX += btnW + 8;
      }

      int listTop = 104;
      int listBottom = height - 16;
      int listW = areaRight - areaLeft;
      int listH = listBottom - listTop;
      RenderUtil.scissorRectScaled(this.contentToScreenX((float)areaLeft), this.contentToScreenY((float)listTop), this.contentToScreenX((float)areaRight), this.contentToScreenY((float)listBottom), 1.0);

      boolean fadeCaptured = ScrollFadeCompositor.beginRegion(
         this.contentToScreenX((float)areaLeft), this.contentToScreenY((float)listTop), this.contentSpanToScreen((float)listW), this.contentSpanToScreen((float)listH)
      );
      int rowH = 34;
      int gap = 6;
      int y = listTop + this.configScrollOffset;
      int totalH = 0;
      try {
         for (AbstractConfigListItem item : configPanel.userConfigs) {
            boolean selected = item.equals(configPanel.getSelectedConfig());
            boolean hovered = RenderUtil.isHovered((float)areaLeft, (float)y, (float)(listW - 14), (float)rowH, mouseX, mouseY);
            float selT = UiAnimation.smoothState("cfg_sel_" + item.getConfigName(), selected ? 1.0F : 0.0F, 16.0F);
            float hovT = UiAnimation.smoothState("cfg_hov_" + item.getConfigName(), hovered ? 1.0F : 0.0F, 16.0F);
            int rowBorder = UiTheme.lerpArgb(outlineColor, fadedArgb(UiTheme.withAlpha(UiTheme.accentSoftGlow(), 230)), Math.max(selT, hovT * 0.7F));
            GlassRenderer.drawGlassCardWithHover((float)areaLeft, (float)y, (float)(listW - 14), (float)rowH, UiTheme.CARD_RADIUS, hovT, selT, 0.0F, 0.0F, (float)mouseX, (float)mouseY, accentColor, rowBorder, UiTheme.BORDER_THICKNESS);
            if (selT > 0.05F) {
               GlassRenderer.drawRoundedRect((float)(areaLeft + 3), (float)(y + 7), 3.0F, (float)(rowH - 14), 1.5F, UiTheme.withAlpha(accentColor, Math.round(240.0F * selT)));
            }
            FontManager.interMedium.drawVerticallyCenteredString(item.getConfigName(), (double)(areaLeft + 14 + selT * 4.0F), (double)y, (double)rowH, selected || hovered ? UiTheme.TEXT_PRIMARY : textColor);
            y += rowH + gap;
            totalH += rowH + gap;
         }
      } finally {
         if (fadeCaptured) {
            float topFade = this.configScrollOffset < -4 ? 14.0F : 0.0F;
            float bottomFade = (totalH + this.configScrollOffset) > (listH - 2) ? 24.0F : 0.0F;
            ScrollFadeCompositor.endAndComposite((float)(areaLeft - 12), (float)listTop, (float)(listW + 24), (float)listH, topFade, bottomFade);
         }
      }

      this.configScrollBar.drawVerticalBar(areaRight - 8, listTop, areaRight, listBottom, listTop - this.configScrollOffset, totalH, accentColor);
      this.configScrollOffset = this.configScrollBar.updateScrollOffset(totalH, listTop, listBottom, this.configScrollOffset, areaLeft, listTop, listW, listH, mouseX, mouseY);
   }

   private void rebuildFilteredModules() {
      this.filteredModules.clear();
      String query = this.searchQuery.trim().toLowerCase(Locale.ROOT);
      List sourceList;
      if (this.allCategoriesSelected) {
         sourceList = new ArrayList();
         for (CategoryTabButton tab : (Iterable<CategoryTabButton>)(this.categoryTabs)) {
            sourceList.addAll(tab.getModules());
         }
      } else {
         sourceList = this.selectedCategory.getModules();
      }

      for (Object obj : sourceList) {
         Module module = (Module)obj;
         if (module.isHiddenInGui()) {
            continue;
         }
         if (this.activeSidebarTab == SidebarTab.ACTIVE && !module.isToggled()) {
            continue;
         }
         if (!query.isEmpty()) {
            String name = module.getModuleName().toLowerCase(Locale.ROOT);
            String desc = module.getDescription() != null ? module.getDescription().toLowerCase(Locale.ROOT) : "";
            if (!name.contains(query) && !desc.contains(query)) {
               continue;
            }
         }
         this.filteredModules.add(module);
      }
   }

   private String formatCategoryName(Categorys category) {
      String raw = category.name().toLowerCase(Locale.ROOT);
      return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
   }

   private void openModuleSettings(Module module) {
      if (module != null) {
         this.popupHost.close();
         this.selectedModule = module;
         this.lastViewedModule = module;
         this.viewingModuleSettings = true;
         this.contextMenuModule = null;
         this.contextMenuCategory = null;
         this.contextMenuSetting = null;
         this.triggerContentTransition(1.0F);
      }
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      if (!this.isVisible()) {
         return;
      }

      super.mouseClicked(mouseX, mouseY, mouseButton);
      ScaledResolution sr = new ScaledResolution(mc);
      // Inverse of the content zoom: every hit test below compares against content-space drawing
      // coordinates, so the raw screen mouse is mapped back through toLocalX/Y (shell-local) and then
      // shellToContentX/Y (the exact inverse of the GL11.glScaled pushed in drawWindow).
      mouseX = this.shellToContentX(this.toLocalX((float)mouseX * (float)sr.getScaleFactor()));
      mouseY = this.shellToContentY(this.toLocalY((float)mouseY * (float)sr.getScaleFactor()));

      int width = this.getContentWidth();
      int height = this.getContentHeight();
      if (!this.isMouseOver(0, 0, width, height, mouseX, mouseY)) {
         this.searchFocused = false;
         this.popupHost.close();
         return;
      }

      if (this.popupHost.hasActivePopup()) {
         if (this.popupHost.mouseClicked(mouseX, mouseY, mouseButton)) {
            return;
         }
      }

      try {
         this.handleWindowMouseClick(mouseX, mouseY, mouseButton, width, height);
      } finally {
         this.popupHost.clearClickState();
      }
   }

   private void handleWindowMouseClick(int mouseX, int mouseY, int mouseButton, int width, int height) throws IOException {
      if (mouseButton == 0 || mouseButton == 1) {
         if (!this.hoveringCategoryContextMenu) {
            this.contextMenuCategory = null;
         }
         if (!this.hoveringModuleContextMenu) {
            this.contextMenuModule = null;
         }
      }

      if (this.keybindListeningModule != null) {
         if (mouseButton > 1) {
            this.keybindListeningModule.setKeyCode(mouseButton + 10000);
         }
         this.keybindListeningModule = null;
         ClickGuiListener.postClickGuiRender();
         return;
      }

      // Header window controls capsule
      int capsuleW = 88;
      int capsuleX = width - 16 - capsuleW;
      int closeX = capsuleX + 60;
      int maxX = capsuleX + 32;
      int minX = capsuleX + 4;
      int btnY = 15;
      if (mouseButton == 0 && RenderUtil.isHovered((float)closeX, (float)btnY, 24.0F, 24.0F, mouseX, mouseY)) {
         this.closeWindow();
         this.closed = true;
         this.setFullScreen(false);
         ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE);
         return;
      }
      if (mouseButton == 0 && RenderUtil.isHovered((float)maxX, (float)btnY, 24.0F, 24.0F, mouseX, mouseY)) {
         this.toggleFullScreenMode();
         ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE);
         return;
      }
      if (mouseButton == 0 && RenderUtil.isHovered((float)minX, (float)btnY, 24.0F, 24.0F, mouseX, mouseY)) {
         this.closeWindow();
         ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE);
         return;
      }

      // Back / Forward header arrows
      float backX = (float)(SIDEBAR_WIDTH + CONTENT_PADDING);
      float fwdX = backX + 30.0F;
      if (mouseButton == 0 && RenderUtil.isHovered(backX, 14.0F, 26.0F, 26.0F, mouseX, mouseY) && this.viewingModuleSettings) {
         this.popupHost.close();
         this.viewingModuleSettings = false;
         this.contextMenuSetting = null;
         this.triggerContentTransition(-1.0F);
         return;
      }
      if (mouseButton == 0 && RenderUtil.isHovered(fwdX, 14.0F, 26.0F, 26.0F, mouseX, mouseY) && !this.viewingModuleSettings && this.lastViewedModule != null) {
         this.openModuleSettings(this.lastViewedModule);
         return;
      }

      // View mode segmented button group click
      if (mouseButton == 0 && this.viewModeBarVisible && !this.viewingModuleSettings && this.activeSidebarTab != SidebarTab.CONFIGS) {
         ModuleViewMode[] modes = ModuleViewMode.values();
         for (int i = 0; i < modes.length; i++) {
            if (RenderUtil.isHovered(this.viewModeBtnX[i], this.viewModeTrayY + 3.0F, this.viewModeBtnW[i], this.viewModeTrayH - 6.0F, mouseX, mouseY)) {
               if (this.moduleViewMode != modes[i]) {
                  float dir = modes[i].ordinal() > this.moduleViewMode.ordinal() ? 1.0F : -1.0F;
                  this.moduleViewMode = modes[i];
                  this.moduleScrollOffset = 0;
                  this.moduleScrollBar.resetPendingScroll();
                  this.triggerContentTransition(dir);
               }
               ClickGuiListener.postClickGuiRender();
               return;
            }
         }
      }

      // Search box click
      int actionsRight = capsuleX - 14;
      int searchW = Math.min(220, Math.max(130, actionsRight - (SIDEBAR_WIDTH + 300)));
      int searchX = actionsRight - searchW;
      if (!this.viewingModuleSettings && this.activeSidebarTab != SidebarTab.CONFIGS && searchX >= SIDEBAR_WIDTH + 210
         && RenderUtil.isHovered((float)searchX, 13.0F, (float)searchW, 28.0F, mouseX, mouseY)) {
         if (mouseButton == 0 && !this.searchQuery.isEmpty() && this.searchInput.isClearButtonHovered(mouseX, mouseY)) {
            this.searchQuery = "";
            this.moduleScrollOffset = 0;
         }
         this.searchFocused = true;
         this.searchCursorTime = System.currentTimeMillis();
         return;
      } else {
         this.searchFocused = false;
      }

      // Settings header action buttons
      if (mouseButton == 0 && this.viewingModuleSettings && this.selectedModule != null) {
         if (Augustus.getInstance().isConfigEditingEnabled()) {
            if (this.headerExportButton.isHovered(mouseX, mouseY)) {
               if (!ClipboardConfigCodec.exportModule(this.selectedModule)) {
                  ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE6);
                  ChatUtil.sendChat("Failed to export " + this.selectedModule.getName() + " to clipboard");
               }
               return;
            }
            if (this.headerImportButton.isHovered(mouseX, mouseY)) {
               if (!ClipboardConfigCodec.importModule(this.selectedModule)) {
                  ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE6);
                  ChatUtil.sendChat("Failed to import " + this.selectedModule.getName() + " to clipboard");
               }
               return;
            }
         }
         if (this.headerToggleButton.isHovered(mouseX, mouseY)) {
            this.selectedModule.toggle();
            ClickGuiListener.postClickGuiRender();
            return;
         }
      }

      // Sidebar rail clicks. The row rects come from the same live row list (and the same scroll
      // offset) the rail drew with, so a click always lands on the row under the cursor at any zoom.
      if (mouseButton == 0 && mouseX < SIDEBAR_WIDTH) {
         if (this.footerClickGuiButton.isHovered(mouseX, mouseY)) {
            this.openModuleSettings(ClientManagers.moduleManager.clickGui);
            return;
         }
         if (this.footerColorButton.isHovered(mouseX, mouseY)) {
            this.openModuleSettings(ClientManagers.moduleManager.customClientColor);
            return;
         }
         this.railScrollBar.mouseClicked(mouseX, mouseY, mouseButton);
         RailRow row = this.railRowAt(mouseX, mouseY, height);
         if (row != null) {
            this.activateRailRow(row);
         }
         return;
      }

      if (this.hoveringCategoryContextMenu && mouseButton == 0) {
         this.handleCategoryContextMenuClick(mouseX, mouseY);
         ClickGuiListener.postClickGuiRender();
         return;
      }
      if (this.hoveringModuleContextMenu && mouseButton == 0) {
         this.handleModuleContextMenuClick(mouseX, mouseY);
         ClickGuiListener.postClickGuiRender();
         return;
      }

      if (this.viewingModuleSettings && this.selectedModule != null) {
         int panelX = SETTINGS_OFFSET_X;
         int panelY = SETTINGS_OFFSET_Y;
         int panelW = width - SETTINGS_OFFSET_X - 16;
         int panelH = height - SETTINGS_OFFSET_Y - 16;
         int localX = mouseX - panelX;
         int localY = mouseY - panelY;
         int contentW = panelW - (this.settingsPanel.verticalScrollVisible ? 15 : 0);
         int contentH = panelH - (this.settingsPanel.horizontalScrollVisible ? 15 : 0);
         if (RenderUtil.isHovered(0.0F, 0.0F, (float)contentW, (float)contentH, localX, localY)) {
            boolean handled = this.contextMenuSetting != null && this.contextMenuSetting.mouseClicked(localX, localY, mouseButton);
            if (!handled) {
               this.settingsPanel.mouseClicked(localX, localY, mouseButton);
            }
         }
         if (RenderUtil.isHovered(0.0F, 0.0F, (float)panelW, (float)panelH, localX, localY)) {
            this.settingsPanel.handleScrollBarClick(localX, localY, mouseButton);
         }
         ClickGuiListener.postClickGuiRender();
         return;
      }

      if (this.activeSidebarTab == SidebarTab.CONFIGS) {
         this.handleConfigsClick(mouseX, mouseY, mouseButton, width, height);
         ClickGuiListener.postClickGuiRender();
         return;
      }

      // Category filter pills click
      int barLeft = SIDEBAR_WIDTH + CONTENT_PADDING;
      int barRight = width - CONTENT_PADDING;
      if (RenderUtil.isHovered((float)barLeft, (float)CATEGORY_BAR_Y, (float)(barRight - barLeft), (float)CATEGORY_BAR_HEIGHT, mouseX, mouseY)) {
         int allW = (int)FontManager.interMedium.getStringWidth("All") + 38;
         int allX = barLeft + this.categoryScrollOffset;
         if (mouseButton == 0 && RenderUtil.isHovered((float)allX, (float)CATEGORY_BAR_Y, (float)allW, 26.0F, mouseX, mouseY)) {
            this.allCategoriesSelected = true;
            this.moduleScrollOffset = 0;
            this.moduleScrollBar.resetPendingScroll();
            this.triggerContentTransition(-1.0F);
            return;
         }

         for (CategoryTabButton tab : (Iterable<CategoryTabButton>)(this.categoryTabs)) {
            if (RenderUtil.isHovered((float)tab.left, (float)tab.top, (float)tab.width, (float)tab.height, mouseX, mouseY)) {
               if (mouseButton == 1) {
                  this.contextMenuCategory = tab;
                  this.contextMenuX = mouseX;
                  this.contextMenuY = mouseY;
                  this.contextMenuModule = null;
               } else if (mouseButton == 0) {
                  this.allCategoriesSelected = false;
                  this.selectedCategory.setScrollOffset(this.moduleScrollOffset);
                  this.selectedCategory = tab;
                  this.moduleScrollOffset = tab.getScrollOffset();
                  this.moduleScrollBar.resetPendingScroll();
                  this.triggerContentTransition(1.0F);
               }
               return;
            }
         }
      }

      // Module items click (List / Compact List / Grid)
      int areaLeft = SIDEBAR_WIDTH + CONTENT_PADDING;
      int areaRight = width - CONTENT_PADDING;
      int areaTop = GRID_TOP_Y;
      int areaBottom = height - 16;
      int areaW = Math.max(200, areaRight - areaLeft);
      int areaH = Math.max(100, areaBottom - areaTop);
      if (RenderUtil.isHovered((float)areaLeft, (float)areaTop, (float)areaW, (float)areaH, mouseX, mouseY)) {
         this.moduleScrollBar.mouseClicked(mouseX, mouseY, mouseButton);

         if (this.moduleViewMode == ModuleViewMode.GRID) {
            int gap = 12;
            int cols = Math.max(2, Math.min(4, (areaW - 14 + gap) / (212 + gap)));
            int cardW = (areaW - 14 - gap * (cols - 1)) / cols;
            int cardH = 104;

            for (int i = 0; i < this.filteredModules.size(); i++) {
               Module module = this.filteredModules.get(i);
               int col = i % cols;
               int row = i / cols;
               int cardX = areaLeft + col * (cardW + gap);
               int cardY = areaTop + row * (cardH + gap) + this.moduleScrollOffset;

               if (RenderUtil.isHovered((float)cardX, (float)cardY, (float)cardW, (float)cardH, mouseX, mouseY)) {
                  int gearX = cardX + cardW - 36;
                  int gearY = cardY + 10;
                  if (mouseButton == 1 || (mouseButton == 0 && RenderUtil.isHovered((float)gearX, (float)gearY, 24.0F, 24.0F, mouseX, mouseY))) {
                     this.openModuleSettings(module);
                  } else if (mouseButton == 0) {
                     if (this.activeSidebarTab == SidebarTab.KEYBINDS) {
                        this.keybindListeningModule = module;
                     } else {
                        module.toggle();
                     }
                  }
                  break;
               }
            }
         } else {
            boolean compact = this.moduleViewMode == ModuleViewMode.COMPACT;
            int rowH = compact ? 34 : 58;
            int gap = compact ? 5 : 8;
            float rightPad = compact ? 11.0F : 14.0F;
            float switchW = compact ? 32.0F : 36.0F;
            float gearSize = compact ? 22.0F : 26.0F;

            for (int i = 0; i < this.filteredModules.size(); i++) {
               Module module = this.filteredModules.get(i);
               int hitX, hitW, rowY;
               if (compact) {
                  int colGap = 10;
                  int colW = (areaW - 14 - colGap) / 2;
                  int col = i % 2;
                  hitX = areaLeft + col * (colW + colGap);
                  hitW = colW;
                  rowY = areaTop + (i / 2) * (rowH + gap) + this.moduleScrollOffset;
               } else {
                  hitX = areaLeft;
                  hitW = areaW - 14;
                  rowY = areaTop + i * (rowH + gap) + this.moduleScrollOffset;
               }
               if (RenderUtil.isHovered((float)hitX, (float)rowY, (float)hitW, (float)rowH, mouseX, mouseY)) {
                  float switchX = (float)(hitX + hitW) - rightPad - switchW;
                  float gearX = switchX - (compact ? 6.0F : 8.0F) - gearSize;
                  float gearY = (float)rowY + ((float)rowH - gearSize) * 0.5F;

                  boolean listeningBind = this.keybindListeningModule == module;
                  boolean clickedGear = mouseButton == 0 && RenderUtil.isHovered(gearX - 2.0F, gearY - 2.0F, gearSize + 4.0F, gearSize + 4.0F, mouseX, mouseY);
                  boolean clickedBind = false;
                  if (mouseButton == 0 && this.shouldShowRowKeybind(module, listeningBind)) {
                     float badgeW = this.computeRowKeybindWidth(module, listeningBind);
                     float badgeH = compact ? 18.0F : 22.0F;
                     float badgeX = gearX - (compact ? 6.0F : 8.0F) - badgeW;
                     float badgeY = (float)rowY + ((float)rowH - badgeH) * 0.5F;
                     clickedBind = RenderUtil.isHovered(badgeX, badgeY, badgeW, badgeH, mouseX, mouseY);
                  }

                  if (mouseButton == 1 || clickedGear) {
                     this.openModuleSettings(module);
                  } else if (mouseButton == 0) {
                     if (clickedBind || this.activeSidebarTab == SidebarTab.KEYBINDS) {
                        this.keybindListeningModule = module;
                     } else {
                        module.toggle();
                     }
                  }
                  break;
               }
            }
         }
      }

      ClickGuiListener.postClickGuiRender();
   }

   private void handleConfigsClick(int mouseX, int mouseY, int mouseButton, int width, int height) throws IOException {
      if (mouseButton != 0) {
         return;
      }
      ConfigSystemPanel configPanel = Augustus.getInstance().getClickGui().getConfigPanel();
      int areaLeft = SIDEBAR_WIDTH + CONTENT_PADDING;
      int areaRight = width - CONTENT_PADDING;
      int topBarY = 66;
      String[] labels = new String[]{"Create", "Load", "Save", "Folder", "Refresh", "Delete"};
      int btnX = areaLeft;
      for (int i = 0; i < labels.length; i++) {
         int btnW = (int)FontManager.interMedium.getStringWidth(labels[i]) + 34;
         if (RenderUtil.isHovered((float)btnX, (float)topBarY, (float)btnW, 26.0F, mouseX, mouseY)) {
            switch (i) {
               case 0:
                  configPanel.openCreateConfigScreen();
                  return;
               case 1:
                  configPanel.loadSelectedConfig();
                  return;
               case 2:
                  configPanel.saveSelectedConfig();
                  return;
               case 3:
                  configPanel.openConfigsFolder();
                  return;
               case 4:
                  configPanel.refreshConfigsAsync();
                  return;
               case 5:
                  configPanel.deleteSelectedConfig();
                  return;
            }
         }
         btnX += btnW + 8;
      }

      int listTop = 104;
      int listBottom = height - 16;
      int listW = areaRight - areaLeft;
      this.configScrollBar.mouseClicked(mouseX, mouseY, mouseButton);

      int rowH = 34;
      int gap = 6;
      int y = listTop + this.configScrollOffset;
      if (RenderUtil.isHovered((float)areaLeft, (float)listTop, (float)listW, (float)(listBottom - listTop), mouseX, mouseY)) {
         for (AbstractConfigListItem item : configPanel.userConfigs) {
            if (RenderUtil.isHovered((float)areaLeft, (float)y, (float)(listW - 14), (float)rowH, mouseX, mouseY)) {
               configPanel.setSelectedConfig(item);
               return;
            }
            y += rowH + gap;
         }
      }
   }

   @Override
   public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
      super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
      ScaledResolution sr = new ScaledResolution(mc);
      // Same inverse of the content zoom as mouseClicked (drag/scroll must not drift at any scale).
      int localX = this.shellToContentX(this.toLocalX((float)mouseX * (float)sr.getScaleFactor()));
      int localY = this.shellToContentY(this.toLocalY((float)mouseY * (float)sr.getScaleFactor()));
      if (this.popupHost.hasActivePopup()) {
         this.popupHost.mouseClickMove(localX, localY, clickedMouseButton, timeSinceLastClick);
         return;
      }
      if (!this.viewingModuleSettings) {
         return;
      }
      if (!this.hoveringModuleContextMenu) {
         this.settingsPanel.mouseClickMove(localX - SETTINGS_OFFSET_X, localY - SETTINGS_OFFSET_Y, clickedMouseButton, timeSinceLastClick);
      }
   }

   @Override
   public void mouseReleased(int mouseX, int mouseY, int state) {
      super.mouseReleased(mouseX, mouseY, state);
      ScaledResolution sr = new ScaledResolution(mc);
      // Same inverse of the content zoom as mouseClicked, so a release lands on the widget the press
      // started on at every GUI Scale.
      int localX = this.shellToContentX(this.toLocalX((float)mouseX * (float)sr.getScaleFactor()));
      int localY = this.shellToContentY(this.toLocalY((float)mouseY * (float)sr.getScaleFactor()));
      this.popupHost.mouseReleased(localX, localY, state);
      this.categoryScrollBar.mouseReleased(localX, localY, state);
      this.moduleScrollBar.mouseReleased(localX, localY, state);
      this.configScrollBar.mouseReleased(localX, localY, state);
      this.railScrollBar.mouseReleased(localX, localY, state);
      if (this.viewingModuleSettings) {
         this.settingsPanel.mouseReleased(localX - SETTINGS_OFFSET_X, localY - SETTINGS_OFFSET_Y, state);
      }
      ClickGuiListener.postClickGuiRender();
   }

   @Override
   public void keyTyped(char typedChar, int keyCode) throws IOException {
      if (this.popupHost.hasActivePopup() && this.popupHost.keyTyped(typedChar, keyCode)) {
         return;
      }
      super.keyTyped(typedChar, keyCode);
      if (this.keybindListeningModule != null) {
         this.keybindListeningModule.setKeyCode(keyCode == Keyboard.KEY_ESCAPE ? 0 : keyCode);
         this.keybindListeningModule = null;
         ClickGuiListener.postClickGuiRender();
         return;
      }

      if ((Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) && keyCode == Keyboard.KEY_F && !this.viewingModuleSettings) {
         this.searchFocused = true;
         this.searchCursorTime = System.currentTimeMillis();
         return;
      }

      if (this.searchFocused) {
         if (keyCode == Keyboard.KEY_ESCAPE) {
            if (!this.searchQuery.isEmpty()) {
               this.searchQuery = "";
            } else {
               this.searchFocused = false;
            }
            return;
         }
         if (keyCode == Keyboard.KEY_RETURN) {
            this.searchFocused = false;
            return;
         }
         if (keyCode == Keyboard.KEY_BACK) {
            if (!this.searchQuery.isEmpty()) {
               this.searchQuery = this.searchQuery.substring(0, this.searchQuery.length() - 1);
               this.moduleScrollOffset = 0;
            }
            return;
         }
         if (typedChar >= 32 && typedChar != 127) {
            this.searchQuery += typedChar;
            this.moduleScrollOffset = 0;
            this.searchCursorTime = System.currentTimeMillis();
            return;
         }
      }

      if (this.viewingModuleSettings) {
         this.settingsPanel.keyTyped(typedChar, keyCode);
      }
      ClickGuiListener.postClickGuiRender();
   }

   public void updatePanel() {
      while (!PENDING_SETTING_LISTENERS.isEmpty()) {
         SettingChangeListener listener = (SettingChangeListener)PENDING_SETTING_LISTENERS.poll();
         if (listener != null) {
            listener.onChanged();
         }
      }

      for (Setting setting : (Iterable<Setting>)(this.selectedModule.getSettings())) {
         setting.updateVisibility();
      }
   }

   public void onClose() {
      this.popupHost.close();
      this.settingsPanel.onClose();
      this.contextMenuModule = null;
      this.contextMenuCategory = null;
      this.searchFocused = false;
      this.keybindListeningModule = null;
   }

   private void handleCategoryContextMenuClick(int n, int n2) {
      if (this.contextMenuCategory != null && Augustus.getInstance().isConfigEditingEnabled()) {
         if (RenderUtil.isHovered((float)this.contextMenuX, (float)(this.contextMenuY + 4), 154.0F, 24.0F, n, n2)) {
            Categorys categorys = this.contextMenuCategory.getCategorys();
            if (!ClipboardConfigCodec.importCategory(categorys)) {
               ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE6);
               ChatUtil.sendChat("Failed to import " + categorys.name() + " to clipboard");
            }
         } else if (RenderUtil.isHovered((float)this.contextMenuX, (float)(this.contextMenuY + 28), 154.0F, 24.0F, n, n2)) {
            if (!ClipboardConfigCodec.exportCategory(this.contextMenuCategory.getCategorys())) {
               ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE6);
               ChatUtil.sendChat("Failed to export " + this.contextMenuCategory.getCategorys().name() + " to clipboard");
            }
         } else if (RenderUtil.isHovered((float)this.contextMenuX, (float)(this.contextMenuY + 52), 154.0F, 24.0F, n, n2)) {
            List modulesByCategory = ClientManagers.moduleManager.getModulesByCategory(this.contextMenuCategory.getCategorys());
            int count = 0;
            for (Module module : (Iterable<Module>)(modulesByCategory)) {
               count += module.isLoadFromConfig() ? 1 : 0;
            }
            boolean flag3 = (double)count / (double)modulesByCategory.size() < 0.5;
            for (Module module2 : (Iterable<Module>)(modulesByCategory)) {
               module2.setLoadFromConfig(flag3);
            }
         } else if (RenderUtil.isHovered((float)this.contextMenuX, (float)(this.contextMenuY + 76), 154.0F, 24.0F, n, n2)) {
            List modulesByCategory2 = ClientManagers.moduleManager.getModulesByCategory(this.contextMenuCategory.getCategorys());
            int count = 0;
            for (Module module3 : (Iterable<Module>)(modulesByCategory2)) {
               count += module3.isShownInArrayList() ? 1 : 0;
            }
            boolean flag4 = (double)count / (double)modulesByCategory2.size() < 0.5;
            for (Module module4 : (Iterable<Module>)(modulesByCategory2)) {
               module4.setShowInArrayList(flag4);
            }
         }
      }
   }

   private void handleModuleContextMenuClick(int n, int n2) {
      if (this.contextMenuModule != null && Augustus.getInstance().isConfigEditingEnabled()) {
         if (RenderUtil.isHovered((float)this.contextMenuX, (float)(this.contextMenuY + 4), 154.0F, 24.0F, n, n2)) {
            if (!ClipboardConfigCodec.importModule(this.contextMenuModule)) {
               ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE6);
               ChatUtil.sendChat("Failed to import " + this.contextMenuModule.getName() + " to clipboard");
            }
         } else if (RenderUtil.isHovered((float)this.contextMenuX, (float)(this.contextMenuY + 28), 154.0F, 24.0F, n, n2)) {
            if (!ClipboardConfigCodec.exportModule(this.contextMenuModule)) {
               ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE6);
               ChatUtil.sendChat("Failed to export " + this.contextMenuModule.getName() + " to clipboard");
            }
         }
      }
   }

   private void drawContextMenuBackground(int x, int y, int width, int height, int fallbackB) {
      GlassRenderer.drawFloatingGlassPopup((float)x, (float)y, (float)width, (float)height, UiTheme.POPUP_RADIUS, UiTheme.THICK_BORDER_THICKNESS, fadedArgb(UiTheme.GLASS_BORDER_STRONG));
   }

   private void drawContextMenuItem(String text, LucideIcon icon, int x, int y, int w, int h, int mouseX, int mouseY) {
      boolean hovered = RenderUtil.isHovered((float)x + 3.0F, (float)y + 1.0F, (float)w - 6.0F, (float)h - 2.0F, mouseX, mouseY);
      boolean pressed = hovered && UiAnimation.isPrimaryMouseDown();
      float hovT = UiAnimation.smoothState("ctx_hov_" + text, hovered ? 1.0F : 0.0F, 18.0F);
      float prsT = UiAnimation.smoothState("ctx_prs_" + text, pressed ? 1.0F : 0.0F, 30.0F);
      int accent = UiTheme.accentColor();

      if (hovT > 0.02F) {
         int topFill = fadedArgb(UiTheme.withAlpha(UiTheme.lerpArgb(accent, 0xFF282058, 0.30F), Math.round(140.0F * hovT)));
         int botFill = fadedArgb(UiTheme.withAlpha(UiTheme.accentDeep(), Math.round(125.0F * hovT)));
         int border = fadedArgb(UiTheme.withAlpha(UiTheme.accentSoftGlow(), Math.round(190.0F * hovT)));
         GlassRenderer.drawInteractiveSurface(
            (float)(x + 4),
            (float)(y + 1),
            (float)(w - 8),
            (float)(h - 2),
            5.5F,
            topFill,
            botFill,
            border,
            1.0F,
            UiTheme.glassTopHighlightAlpha(hovT),
            hovT,
            prsT,
            true,
            (float)mouseX,
            (float)mouseY,
            accent
         );
      }

      float pressOffset = prsT * 0.4F;
      int fg = fadedArgb(UiTheme.lerpArgb(UiTheme.secondaryTextColor(), UiTheme.TEXT_PRIMARY, hovT));
      if (icon != null) {
         icon.drawCentered((float)(x + 16), (float)y + (float)h * 0.5F + pressOffset, 12.0F, fadedArgb(hovered ? UiTheme.accentSoftGlow() : fg));
      }
      float textX = (float)(x + (icon != null ? 28 : 10));
      FontManager.interMedium.drawVerticallyCenteredString(text, (double)textX, (double)y + pressOffset, (double)h, fg);
   }

   private void drawSettingContextMenu(int n, int n2) {
      if (Augustus.getInstance().isConfigEditingEnabled()) {
         this.drawContextMenuBackground(this.contextMenuX, this.contextMenuY, 154, 56, 3);
         this.drawContextMenuItem("Import Settings", LucideIcon.UPLOAD, this.contextMenuX, this.contextMenuY + 4, 154, 24, n, n2);
         this.drawContextMenuItem("Export Settings", LucideIcon.DOWNLOAD, this.contextMenuX, this.contextMenuY + 28, 154, 24, n, n2);
      }
   }

   private void drawModuleContextMenu(int n, int n2) {
      if (Augustus.getInstance().isConfigEditingEnabled()) {
         this.drawContextMenuBackground(this.contextMenuX, this.contextMenuY, 154, 56, 3);
         this.drawContextMenuItem("Import Settings", LucideIcon.UPLOAD, this.contextMenuX, this.contextMenuY + 4, 154, 24, n, n2);
         this.drawContextMenuItem("Export Settings", LucideIcon.DOWNLOAD, this.contextMenuX, this.contextMenuY + 28, 154, 24, n, n2);
      }
   }

   private void drawCategoryContextMenu(int n, int n2) {
      if (Augustus.getInstance().isConfigEditingEnabled()) {
         this.drawContextMenuBackground(this.contextMenuX, this.contextMenuY, 154, 104, 39);
         this.drawContextMenuItem("Import Settings", LucideIcon.UPLOAD, this.contextMenuX, this.contextMenuY + 4, 154, 24, n, n2);
         this.drawContextMenuItem("Export Settings", LucideIcon.DOWNLOAD, this.contextMenuX, this.contextMenuY + 28, 154, 24, n, n2);
         this.drawContextMenuItem("LoadFromConfig", LucideIcon.FOLDER, this.contextMenuX, this.contextMenuY + 52, 154, 24, n, n2);
         this.drawContextMenuItem("ShowInArrayList", LucideIcon.EYE, this.contextMenuX, this.contextMenuY + 76, 154, 24, n, n2);
      }
   }

   public CustomFontUtil getModuleFont() {
      return this.moduleFont;
   }

   public void setSelectedCategory(CategoryTabButton categoryTabButton) {
      if (categoryTabButton != null) {
         this.selectedCategory = categoryTabButton;
         this.allCategoriesSelected = false;
      }
   }

   public ModuleSettingsPanel getSettingsPanel() {
      return this.settingsPanel;
   }

   public CategoryTabButton getSelectedCategory() {
      return this.selectedCategory;
   }

   public boolean isHoveringModuleContextMenu() {
      return this.hoveringModuleContextMenu;
   }

   public Module getSelectedModule() {
      return this.selectedModule;
   }

   private String getSelectedModuleName() {
      return this.getSelectedModule().getName();
   }

   public ArrayList getCategoryTabs() {
      return this.categoryTabs;
   }

   public void setSelectedModule(Module module) {
      if (module != null) {
         this.selectedModule = module;
      }
   }
}
