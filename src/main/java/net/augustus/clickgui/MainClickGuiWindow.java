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
import net.augustus.render.ClasspathTexture;
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
import net.augustus.ui.modern.font.InterFontUtil;
import net.augustus.ui.modern.popup.PopupHost;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.MaskedTexture;
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
import java.util.HashMap;
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

   // ------------------------------------------------------------------------------------------
   // Sidebar / rail metrics.
   //
   // WHY one block with a documented ladder instead of the old scattered literals: the sidebar had
   // grown a 12 / 13 / 14 / 15 / 16 / 26 / 28 / 52 / 72 mix of paddings that no two surfaces shared,
   // which is exactly what makes a rail read as hand-assembled. The numbers below are the only
   // spacing ladder the rail uses, and every one of them is a content pixel like the rest of this
   // file, so they zoom with the GUI Scale and the rail's optical rhythm survives at any zoom.
   //
   //   padding:  4 (inner controls)  ->  10 (row gutters, card insets)  ->  16 (group gutter)
   //   rhythm:   row 26 high on a 28 step  ->  4 between rows  ->  8 before a group
   //   type:     group caps -> Inter small spaced | row label -> Inter medium | count -> Inter small
   // ------------------------------------------------------------------------------------------
   private static final int SIDEBAR_WIDTH = 228;
   /** Width the rail collapses to: one icon column wide, with the fold control still clear above it. */
   private static final int SIDEBAR_COLLAPSED_WIDTH = 56;
   private static final int HEADER_HEIGHT = 58;
   /**
    * The header's single control band. Measured off the reference's own wells (the arrow well, the
    * search field and the window controls all share it): top 12.8, height 35.6, and a 10.5 corner
    * radius -- the fit against the reference's left-edge inset profile gives r = 13 image px, which is
    * 10.5 content px, NOT the half-of-35.6 pill the controls first looked like.
    */
   private static final float HEADER_WELL_Y = 12.8F;
   private static final float HEADER_WELL_H = 35.6F;
   private static final float HEADER_WELL_RADIUS = 10.5F;
   /**
    * The header's own insets, which are NOT the content column's. The content starts 22 in from the
    * rail and stops 35 short of the right edge, but the reference's header runs wider: its arrow well
    * opens ~6.8 after the rail and its window-controls well closes only ~10 from the panel edge.
    */
   private static final float HEADER_LEADING_PADDING = 6.8F;
   private static final float HEADER_TRAILING_PADDING = 9.9F;
   /** The two flanking wells are rounder than the search field: 15.5 and 14.0 against its 10.5. */
   private static final float ARROW_WELL_RADIUS = 15.5F;
   private static final float WINDOW_WELL_RADIUS = 14.0F;
   /** Gutter between the rail boundary and the content column. Measured: the target's cards start
    * at content x 249.9 and its rail ends at 228.3, so the gutter is 21.6. */
   private static final int CONTENT_PADDING = 22;
   /** Gutter between the content column and the panel's trailing edge. Measured: 34.7. */
   private static final int CONTENT_TRAILING_PADDING = 35;
   /** Top of the category chip band. Measured: the target's chip row starts at content y 66.4. */
   private static final int CATEGORY_BAR_Y = 66;
   /** Height of the category chip band. Measured: 34.9 (a full pill, so the radius is half of it). */
   private static final int CATEGORY_BAR_HEIGHT = 35;
   /** Top of the first module card. Measured: content y 119.2. */
   private static final int GRID_TOP_Y = 119;
   private static final int SETTINGS_OFFSET_Y = 66;
   /**
    * Pinned profile footer band.
    *
    * <p>Measured: in the target the footer's hairline sits at content y 572 and the panel's own bottom
    * edge is at 636, so the band is 64 tall and its content (avatar, name, shortcuts) is centred in it.
    */
   private static final int FOOTER_HEIGHT = 67;

   /**
    * Horizontal gutter between the rail boundary and a rail row: the rail's single outer inset.
    */
   private static final int RAIL_ROW_X = 12;
   /**
    * Hit-box height of one rail row; also the height the sliding active item travels in.
    *
    * <p>Measured against the target (tools/verify/measure_rail_detail.py, which reads the reference's
    * own scale from its rail width): row centres sit 42.2 content px apart and the floating selected
    * plate is 45 tall, so the row box has to be airy enough that the plate's 3.5px overhang still leaves
    * the rows themselves untouching. 38 on a 42 step does that, and keeps the box and the plate one
    * honest pair instead of a 32 hit target hiding under a 45 plate.
    */
   private static final int SIDEBAR_ITEM_H = 38;
   /** Row pitch. The 4px difference to {@link #SIDEBAR_ITEM_H} is the row's own vertical air. */
   private static final int SIDEBAR_ITEM_STEP = 42;
   /** Top of the first group's header, below the brand block. Puts its caps label at y=112, as measured. */
   private static final int SIDEBAR_GROUP1_TOP = 104;
   /**
    * Gap between a group header's box and the first row under it.
    *
    * <p>Measured, not chosen: caps label centre -> first row centre is 38.1 in the target, which is
    * half a header (10) + this gap + half a row box (19).
    */
   private static final int RAIL_GROUP_LABEL_GAP = 9;
   /**
    * Distance from a group header's box top up to its separator rule.
    *
    * <p>Measured: the rule above APPEARANCE sits at content y 312.5 while the header's caps label is
    * centred at 335, i.e. 12.5 above the header box. The rule reads as the section's own start, not as
    * the previous section's end, which is why it belongs to the header that follows it.
    */
   private static final float RAIL_GROUP_RULE_OFFSET = 12.5F;
   /**
    * Gap between a group's last row box and the next group's header box.
    *
    * <p>Measured, not chosen: in the target the distance from the last row's centre to the next group's
    * caps label is 57.6 content px and from that label to its first row 37.3. Those two numbers are what
    * this gap plus {@link #RAIL_GROUP_LABEL_GAP} have to reproduce, and they are why the gap is much
    * larger than the label gap -- a group change has to read as a bigger break than a row step.
    */
   private static final int RAIL_GROUP_GAP = 30;
   /** Height of a group header hit box (icon-free caps label + disclosure chevron). */
   private static final int RAIL_GROUP_HEADER_H = 20;
   /** Vertical band of the rail that scrolls when the rows do not fit (footer stays pinned). */
   private static final int RAIL_VIEW_TOP = 58;

   /*
    * ---- Row internals, all measured off the target in content px -------------------------------
    *
    * The rail's vertical rhythm being right is not enough on its own: the reference's rows are wide
    * and airy -- a large icon in its own column, then a long gap, then the label at a third of the
    * rail's width. tools/verify/measure_sidebar_detail2.py reads those columns straight out of the
    * screenshot (per-column ink profiles, so the numbers are the glyphs' own edges), and these are
    * them. They are the single most visible difference a "close but not the same" rail has: with a
    * 14.5px icon at x=27 and the label at x=40 the column reads as a dense list, while the target
    * reads as a roomy index.
    */
   /** Optical centre of the row icon column. The target's icons are centred here to within 0.5px. */
   private static final float RAIL_ICON_CENTER_X = 41.3F;
   /**
    * Icon box. Measured: the reference's row-icon INK is 19.4-19.5 content px wide, and a Tabler glyph
    * paints about 83 % of its box, which puts the box at 24 -- the same size every other icon in the
    * window uses, so the rail and the module rows stay on one icon scale.
    */
   private static final float RAIL_ICON_SIZE = 24.0F;
   /** Left edge of a row's label. Measured: every row's label starts at the same column (78.1). */
   private static final float RAIL_LABEL_X = 78.1F;
   /**
    * Centre of the row's count column, badge and plain alike.
    *
    * <p>Measured off the reference by reading the counter's own ink inside the active row: the digits
    * span content x 180.2..200.5, so the column is centred at 190.3 -- near the rail's trailing end but
    * not on it. Both the chip and a bare count centre here, so a row that gains or loses its chip does
    * not move the number sideways.
    */
   private static final float RAIL_COUNT_CENTER_X = 190.3F;
   /**
    * How far the floating selected plate overhangs its row box, top and bottom.
    *
    * <p>Measured: the plate is 45 content px tall around a row whose centre it shares, so it is a
    * deliberately larger object than the row it marks -- the air above and below is what makes it read
    * as floating instead of as a filled row.
    */
   private static final float RAIL_PLATE_OVERHANG = 3.5F;

   /** Corner radius of the selected/hover plate. Measured off the reference's own Active Item. */
   private static final float RAIL_PLATE_RADIUS = 8.9F;
   /**
    * Channel percentages that turn the live client colour into the selected plate's two stops.
    *
    * <p>WHY ratios and not two hex literals: the plate used to hold two colours sampled straight off the
    * reference, so it kept the guide's violet no matter what Client Color said - it was the one surface of
    * the rail that ignored the user's own accent, which is what "cuando cambio de color el hover y selección
    * del sidebar no cambian de color" was. The percentages below reproduce those two sampled stops exactly
    * at the default accent ({@code #7C3AED} -> {@code #4622A3} and {@code #302063}) while carrying a custom
    * one, the same way {@link #brandWashColor()} already carries the brand wash.
    */
   private static final int RAIL_PLATE_LEADING_R_PERCENT = 57;
   private static final int RAIL_PLATE_LEADING_G_PERCENT = 59;
   private static final int RAIL_PLATE_LEADING_B_PERCENT = 69;
   private static final int RAIL_PLATE_TRAILING_R_PERCENT = 39;
   private static final int RAIL_PLATE_TRAILING_G_PERCENT = 55;
   private static final int RAIL_PLATE_TRAILING_B_PERCENT = 42;
   /** Alpha the selected plate's violet state layer is painted at over the glass material beneath it. */
   private static final float RAIL_PLATE_ALPHA = 172.0F;
   /**
    * Fraction of the plate's alpha a hovered (but not selected) row gets.
    *
    * <p>WHY the same plate at lower strength instead of a different object: the reference only ever shows
    * one plate, so a hover that invented a second surface (a glass pane with its own rim and its own
    * leading spine) read as a different widget. Sharing the geometry and the gradient makes hover a
    * preview of selection; only the strength changes.
    */
   private static final float RAIL_HOVER_ALPHA_SCALE = 0.62F;
   /**
    * Radius, in content px, of the ring the selected row's bloom is tapped around.
    *
    * <p>WHY a ring and not a blur pass: a glyph has no silhouette the glass SDF could follow, so an outer
    * glow has to be built out of the glyph itself. Eight copies on a small circle, each at a low alpha, sum
    * to a soft halo whose shape IS the letterform (and the icon's own outline), which is what makes the
    * letters look lit rather than sitting in front of a blob. 2.2px keeps the halo attached to the glyph at
    * every size the rail draws while still being wide enough to read as a glow through the white stroke.
    */
   private static final float RAIL_GLOW_RADIUS = 2.2F;
   /** Per-tap alpha of that bloom, before the row's own selection/animation weight. */
   private static final float RAIL_GLOW_TAP_ALPHA = 40.0F;

   /** Leading stop of the selected plate: the client colour at the guide's own channel ratios. */
   private static int railPlateLeadingRgb() {
      return accentScaled(RAIL_PLATE_LEADING_R_PERCENT, RAIL_PLATE_LEADING_G_PERCENT, RAIL_PLATE_LEADING_B_PERCENT);
   }

   /** Trailing stop of the selected plate, same rule. */
   private static int railPlateTrailingRgb() {
      return accentScaled(RAIL_PLATE_TRAILING_R_PERCENT, RAIL_PLATE_TRAILING_G_PERCENT, RAIL_PLATE_TRAILING_B_PERCENT);
   }

   /**
    * The selected row's count chip: the plate's leading stop lifted towards white, so the chip is always the
    * same hue family as the plate it sits on and follows Client Color with it.
    */
   private static int railCountChipRgb() {
      return UiTheme.lerpArgb(railPlateLeadingRgb(), 0xFFFFFFFF, 0.12F);
   }

   /** The client accent with each channel scaled by a percentage and clamped back into the byte range. */
   private static int accentScaled(int rPercent, int gPercent, int bPercent) {
      int accent = UiTheme.accentColor();
      int r = Math.min(255, (accent >> 16 & 255) * rPercent / 100);
      int g = Math.min(255, (accent >> 8 & 255) * gPercent / 100);
      int b = Math.min(255, (accent & 255) * bPercent / 100);
      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   /** Group ids for the two collapsible rail groups. Stable keys: they persist in the config. */
   private static final String RAIL_GROUP_MODULES = "modules";
   private static final String RAIL_GROUP_GENERAL = "general";
   /** Caption of each group header, in the caps + letterspacing style the section labels share. */
   private static final String RAIL_GROUP_MODULES_LABEL = "MODULES & CONFIG";
   private static final String RAIL_GROUP_GENERAL_LABEL = "APPEARANCE";

   private static final Queue PENDING_SETTING_LISTENERS = new ConcurrentLinkedQueue();

   /** Bundled Augustus "A" mark for the sidebar header; sized by height only, so it keeps its proportions. */
   private static final MaskedTexture BRAND_MARK = MaskedTexture.of("/assets/augustus/branding/augustus-mark-reference.png");
   /**
    * Row box the wordmark, the version label and the fold control all centre on, so their optical centres
    * line up. The mark is placed on the same box, which is what aligns it with the capitals by
    * construction instead of with a magic offset, at any font size.
    */
   private static final float BRAND_ROW_HEIGHT = 30.0F;
   /**
    * Logo height. Measured against the target: the mark fills the header's left column, roughly 40
    * content px tall and centred on the wordmark's cap row (its ink spans y 21..62 there), so it is a
    * full-size logo beside the wordmark, not a 16px glyph.
    */
   private static final float BRAND_MARK_HEIGHT = 52.64F;
   private static final float BRAND_MARK_GAP = 19.1F;
   /** Top of the mark's box; the reference's mark ink starts at content y 22.73 and the file has a 1.6px margin. */
   private static final float BRAND_MARK_Y = 21.11F;
   /** Width over which the mark ramps out as the rail narrows; see drawSidebar. */
   private static final float RAIL_BRAND_MARK_FADE_BAND = 18.0F;
   /**
    * Brand wash: the violet glow the reference lays into the window's top-left corner, under the mark
    * and the wordmark. It is anchored on the CORNER, not on the logo: the peak sits at content (9,6)
    * while the mark's centre, at (52,47), only reaches ~35% of it. Fitted against the target it is a
    * separable super-Gaussian, {@code u = exp(-(x/120)^2.5 - (y/78)^1.5)}, which reproduces the
    * measured 50% contour (x 104, y 57) and 10% contour (x 190, y 140) to within a few levels.
    */
   private static final float BRAND_WASH_CX = 0.0F;
   private static final float BRAND_WASH_CY = 0.0F;
   private static final float BRAND_WASH_RX = 178.0F;
   private static final float BRAND_WASH_RY = 46.0F;
   private static final float BRAND_WASH_EX = 1.5F;
   private static final float BRAND_WASH_EY = 1.3F;
   /**
    * Channel ratios that turn the live accent into the wash. The reference's corner colour is
    * {@code #41328F} against a default accent of {@code #A44AFD} -- i.e. the accent's own red at 40%,
    * green at 68% and blue at 57% -- so a custom client colour still carries through, just as a deeper,
    * bluer version of itself.
    */
   private static final int BRAND_WASH_R_PERCENT = 40;
   private static final int BRAND_WASH_G_PERCENT = 68;
   private static final int BRAND_WASH_B_PERCENT = 57;

   /**
    * The cursor light the window's own glass carries, in content pixels of falloff radius and 0-255 of
    * peak coverage.
    *
    * <p>WHY the shell needs one of its own: every card, well and control is painted by the shape primitive,
    * which has a cursor light built in ({@code hoverLight}), but the window shell is a material pass and had
    * none. The result was that the one glass surface everything else is built on was also the only one that
    * ignored the pointer. The glow program evaluates the same light over the whole shell and clips it to the
    * shell's rounded silhouette, and it is drawn before the content, so it lights the glass the cards sit on
    * rather than washing over the cards themselves. The radius/exponent pair gives a soft spotlight that is
    * effectively gone ~2.2 radii out, and the alpha is deliberately below the cards' own light so the shell
    * never out-shines what is on it.
    */
   private static final float CURSOR_LIGHT_RADIUS = 145.0F;
   private static final float CURSOR_LIGHT_EXPONENT = 2.0F;
   private static final int CURSOR_LIGHT_ALPHA = 30;

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
   /** Trailing view-mode button at the right end of the category band (see drawCategoryPills). */
   private float viewModeButtonX;
   private float viewModeButtonY;
   private float viewModeButtonW;
   private float viewModeButtonH;
   private boolean viewModeButtonVisible;
   private ModuleViewMode hoveredViewMode = null;
   private CategoryTabButton selectedCategory;
   private final TimeHelper timeHelper;
   private CustomFontUtil moduleFont;
   /** Lazily derived face for the sidebar header's version label (see {@link #brandVersionFont()}). */
   private CustomFontUtil brandVersionFont;
   /** Lazily derived faces for the module rows; see {@link #cardTitleFont()} / {@link #cardDescFont()}. */
   private CustomFontUtil cardTitleFont;
   private CustomFontUtil cardDescFont;
   /** Lazily derived face for the header's view title (see {@link #headerTitleFont()}). */
   private CustomFontUtil headerTitleFont;
   /** Lazily derived face for the sidebar header's wordmark (see {@link #brandWordmarkFont()}). */
   private CustomFontUtil brandWordmarkFont;
   /** Lazily derived face for the rail's group headers (see {@link #railHeaderFont()}). */
   private InterFontUtil railHeaderFont;
   /** Lazily derived face for the rail's row labels (see {@link #railLabelFont()}). */
   private InterFontUtil railLabelFont;
   /** Lazily derived face for the rail's row counts (see {@link #railCountFont()}). */
   private InterFontUtil railCountFont;
   /** Lazily derived face for the footer's player name (see {@link #footerNameFont()}). */
   private InterFontUtil footerNameFont;
   /** Lazily derived face for the footer's status line (see {@link #footerStatusFont()}). */
   private InterFontUtil footerStatusFont;
   /** Version lines for the header's second row, longest first (see {@link #versionLines()}). */
   private String[] versionLines;
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

   // Rail scroll state (content pixels) + the single sliding active item behind the rail rows.
   //
   // The item is a floating glass plate, not a solid highlight: it keeps the rail's own material
   // visible underneath and only tints it towards the accent, which is what makes it read as an
   // object sitting on the rail rather than a hole cut into it (see drawRailRow).
   private final SmoothScrollBar railScrollBar = new SmoothScrollBar();
   private int railScrollOffset;
   private int railContentTop;
   private int railContentBottom;
   /** Top of the first (MODULES) group header, published by buildRailRows for drawing and hit tests. */
   private int railGroupModulesHeaderY = SIDEBAR_GROUP1_TOP;
   /** Top of the second (GENERAL) group header, published by buildRailRows for drawing and hit tests. */
   private int railGroup2HeaderY = SIDEBAR_GROUP1_TOP;
   private float railPillX = (float)RAIL_ROW_X;
   private float railPillY = (float)SIDEBAR_GROUP1_TOP + RAIL_GROUP_HEADER_H + RAIL_GROUP_LABEL_GAP - RAIL_PLATE_OVERHANG;
   private float railPillW = (float)(SIDEBAR_WIDTH - 2 * RAIL_ROW_X);
   private float railPillH = (float)SIDEBAR_ITEM_H + 2.0F * RAIL_PLATE_OVERHANG;
   private float railPillVis;
   /** Row box the rail laid out this frame, so drawing and hit testing share one geometry. */
   private float railRowX = (float)RAIL_ROW_X;
   private float railRowW = (float)(SIDEBAR_WIDTH - 2 * RAIL_ROW_X);

   // ---- Top-right corner control -------------------------------------------------------------
   /**
    * Brand mark ascent from the rail's leading gutter. Measured: the target's mark ink starts at 29.
    *
    * <p>This row used to also hold the rail's fold control (a 20px square at the trailing edge); that
    * control has been removed, so the wordmark block now owns the whole row and there is no square to keep
    * clear of.
    */
   private static final float RAIL_BRAND_X = 25.45F;

   /*
    * ---- Profile footer internals, measured off the target -----------------------------------------
    *
    * The reference's footer is not a card: it is a hairline, then an avatar, a name and a status line
    * on the rail's own material, with two icon-only shortcuts at the trailing edge. The numbers below
    * are that layout, in content px.
    */
   /**
    * Avatar box. Measured off the target: the head is 35 x 35 at content (17.8, 589.4), and the status
    * dot sits on its bottom-right corner.
    */
   private static final int FOOTER_AVATAR_SIZE = 35;
   private static final float FOOTER_AVATAR_X = 17.8F;
   /** Left edge of the name/status column: measured at content x 67.2 for the name and 66.3 for the status. */
   private static final float FOOTER_INFO_X = 66.8F;
   /** Pitch between the two shortcuts' centres. Measured: 26.7 between the two footer glyphs. */
   private static final float FOOTER_SHORTCUT_STEP = 26.7F;
   /**
    * Where the shared capsule ends, measured from the rail's trailing edge, and where its first glyph
    * sits inside it. Everything in the shortcut group cascades from these two so the capsule box, the
    * glyphs and the measured target cannot drift apart.
    */
   private static final float FOOTER_SHORTCUT_CAPSULE_END = 5.3F;
   private static final float FOOTER_SHORTCUT_FIRST_DX = 15.3F;
   /** Glyph size of a footer shortcut, and the square that is hit tested around it. */
   private static final float FOOTER_SHORTCUT_GLYPH = 16.0F;
   private static final float FOOTER_SHORTCUT_BOX = 26.0F;
   /**
    * The shared capsule the two shortcuts sit in. Measured off the target: x 166.1..223.0, 32.5 tall,
    * with a 12.2 corner radius -- a rounded rectangle, not a full pill (12.2 is less than half of 32.5).
    * The chrome is a white veil at ~4.5% over the rail plus one hairline of the same hue at ~7%.
    */
   private static final float FOOTER_SHORTCUT_PILL_H = 32.5F;
   private static final float FOOTER_SHORTCUT_PILL_W = 57.0F;
   /** Corner radius of that capsule, measured as 12.2. */
   private static final float FOOTER_SHORTCUT_PILL_RADIUS = 12.2F;
   /**
    * Columns the fold control plus its gutter occupy at the trailing edge. The brand text is laid out
    * left of this and the mark fades out where it would cross into it, which is what keeps the header
    * collision-free at every phase of the fold rather than only at its two endpoints.
    */

   // ---- Collapsible sidebar groups ---------------------------------------------------------
   /**
    * Steady-state disclosure per group. Every geometry decision (scroll bounds, group heights, the
    * position of the next group's header) is made from these flags, never from the animation phase,
    * so the layout a click is tested against is the layout the transition is heading towards.
    */
   private final Map railGroupExpanded = new HashMap();
   /**
    * Rows published by the last {@link #buildRailRows()}: each row's {@code targetY} is where it sits
    * once every transition has settled. Drawing eases the live y towards it, so collapsing a group
    * above a row does not teleport the rows below it -- they travel, and by the same animation the
    * group itself is opening or closing with.
    */
   private final List railRows = new ArrayList();

   // ---- Sidebar width ----------------------------------------------------------------------
   /** Displayed width, eased towards the steady state; the whole layout rides on this value. */
   private float railColumnAnimW = (float)SIDEBAR_WIDTH;
   /**
    * Rail-only column animation (same value, kept separate so a caller that only needs the rail's own
    * collapse amount does not have to subtract the gutter back out).
    */
   private float railCollapse; // 0 = expanded, 1 = collapsed
   /** Steady-state collapse flag; the draw pass reads this so geometry never follows the easing. */
   private boolean railCollapsed;

   /**
    * Hover bookkeeping for the delayed tooltips covering the rail's icon-only elements. A tooltip
    * appears only after the pointer has rested on the same element for {@link #TOOLTIP_DELAY_MS}:
    * a pointer sweeping across the rail must not trail a queue of popups behind it.
    */
   private static final long TOOLTIP_DELAY_MS = 420L;
   private String tooltipHoverKey;
   /** Text drawn once the hover key above has been held long enough. */
   private String tooltipPendingLabel;
   private long tooltipHoverSince;
   /** What the mouse was actually over during the last draw pass, for the header/footer/rail. */
   private String hoverItemKey;
   private String hoverItemLabel;

   private String searchQuery = "";
   private boolean searchFocused = false;
   private long searchCursorTime = 0L;
   private final List<Module> filteredModules = new ArrayList<>();
   private final PopupHost popupHost = new PopupHost();
   private final ModernTextInput searchInput = new ModernTextInput();
   private final ModernButton headerToggleButton = new ModernButton();
   private final ModernButton headerImportButton = new ModernButton();
   private final ModernButton headerExportButton = new ModernButton();
   // Boxes the footer's two icon shortcuts were LAST DRAWN in. They are the same values the click path
   // tests, so a shortcut can never be clickable where it is not visible -- and the visible flags keep
   // the collapsed rail's hidden shortcuts from answering clicks from a stale box.
   private float footerShortcutGuiX;
   private float footerShortcutGuiY;
   private boolean footerShortcutGuiVisible;
   private float footerShortcutColorX;
   private float footerShortcutColorY;
   private boolean footerShortcutColorVisible;
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
      // Measured off the reference: the panel's corner arc is 15 content px, fitted on four points of
      // the arc with under 0.05px of error, so this is not a taste call.
      this.cornerRadius = 15;
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

      // Both rail groups open by default: the sidebar's job is to show where everything is, and a
      // collapsed group on first open would hide half of that behind a chevron nobody knows to click.
      this.railGroupExpanded.put(RAIL_GROUP_MODULES, Boolean.TRUE);
      this.railGroupExpanded.put(RAIL_GROUP_GENERAL, Boolean.TRUE);
      // Seed the disclosure trackers at their settled values so the first frame draws the rail open
      // instead of animating it open (the window's own open envelope already covers that entrance).
      UiAnimation.setState("rail_group_" + RAIL_GROUP_MODULES, 1.0F);
      UiAnimation.setState("rail_group_" + RAIL_GROUP_GENERAL, 1.0F);
      this.railCollapse = 0.0F;
      this.railColumnAnimW = (float)SIDEBAR_WIDTH;
      this.railCollapsed = false;
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
      // Sidebar brand area can drag; right header interactive controls cannot. The rail's fold control
      // is interactive too, so it is excluded from the drag strip.
      int colW = this.railColumnW();
      if (cx >= colW) {
         if (cx <= this.layoutOffsetX() + 90) {
            return true; // Back / forward buttons
         }
         // Same 420px band as before, measured in content pixels from the content's right edge.
         int contentRightEdge = this.shellToContentX((float)this.getWidth());
         if (cx >= contentRightEdge - 420) {
            return true; // Search box, view mode toggle, quick actions, and window controls
         }
         return false;
      }
      // Group headers toggle their section's disclosure and stay inside the title bar's drag strip.
      return this.railGroupHeaderAt(cx, cy, this.getContentHeight()) != null;
   }

   public int getSettingsOffsetX() {
      // The settings panel, and every setting that maps its own local space back to the window through
      // these offsets, follows the live sidebar width. That is the whole reason the rail can collapse
      // without any setting, popup or slider in the content being taught about it.
      return this.layoutOffsetX();
   }

   public int getSettingsOffsetY() {
      return SETTINGS_OFFSET_Y;
   }

   public static void enqueueSettingChangeListener(SettingChangeListener listener) {
      PENDING_SETTING_LISTENERS.add(listener);
   }

   public static void drawTooltip(CustomFontUtil customFontUtil, String str, int n, int n2) {
      MainClickGuiWindow mw = Augustus.getInstance().getClickGui().getMainWindow();
      TooltipRenderer.drawTooltip(customFontUtil, str, n, n2, Math.max(180, mw.getContentWidth() - mw.getSettingsOffsetX() - mw.cornerRadius - 20));
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

      // Sidebar transitions (the rail's own width, each group's disclosure, each row's eased y) are
      // advanced exactly once here, before anything reads their values, so every consumer in this
      // frame -- layout, drawing and the tooltip dwell below -- sees one consistent state. Nothing
      // else in this window advances them, which is what keeps the animations frame-rate independent
      // instead of advancing once per read.
      this.updateRailAnimations();
      // What the pointer is over is republished below by whatever it lands on; clearing it here means
      // a pointer that left the rail stops offering a tooltip in the same frame it left.
      this.hoverItemKey = null;
      this.hoverItemLabel = null;

      // Sidebar opacity is applied by UiTheme.glassSidebarTintArgb() (single application point), so the
      // returned ARGB already carries the user's "Sidebar Opacity". Only the open animation is folded
      // in here; the tint itself is never re-scaled by sidebarOpacity() to avoid applying it twice.
      int sidebarTint = UiTheme.glassSidebarTintArgb();
      if ((sidebarTint >>> 24) > 0) {
         this.drawRailTint(UiTheme.scaleAlpha(sidebarTint, this.getOpenProgress()), height, cornerContent);
      }

      // ---- brand wash -------------------------------------------------------------------------
      // Under the rail's own rows and the header, above the rail's tint: the reference's violet corner
      // glow reads through the sidebar, so it has to land on top of the sidebar veil rather than under
      // it. Clipped by the window's silhouette inside the shader (see GlassRenderer.drawRadialGlow).
      GlassRenderer.drawRadialGlow(
         0.0F,
         0.0F,
         (float)width,
         (float)height,
         cornerContent,
         BRAND_WASH_CX,
         BRAND_WASH_CY,
         BRAND_WASH_RX,
         BRAND_WASH_RY,
         BRAND_WASH_EX,
         BRAND_WASH_EY,
         UiTheme.scaleAlpha(brandWashColor(), this.getOpenProgress())
      );

      // ---- cursor light on the window's own glass -----------------------------------------------
      // Same light the cards, wells and controls already carry, evaluated over the whole shell and clipped
      // to its rounded silhouette by the glow program. Drawn here - after the shell material, the rail tint
      // and the brand wash, before any content - so it lights the glass everything sits on instead of
      // washing over the cards on top of it.
      GlassRenderer.drawRadialGlow(
         0.0F,
         0.0F,
         (float)width,
         (float)height,
         cornerContent,
         (float)n,
         (float)n2,
         CURSOR_LIGHT_RADIUS,
         CURSOR_LIGHT_RADIUS,
         CURSOR_LIGHT_EXPONENT,
         CURSOR_LIGHT_EXPONENT,
         UiTheme.scaleAlpha(
            UiTheme.withAlpha(UiTheme.lerpArgb(UiTheme.accentColor(), 0xFFE6E0FF, 0.45F), CURSOR_LIGHT_ALPHA),
            this.getOpenProgress()
         )
      );

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

      // The header rule covers the content column only. The reference's rule runs from x 249.1 to 1136.8
      // -- it starts where the content starts and stops where the cards stop. Running it under the rail
      // as well drew a line straight through the wordmark, which is what made it read as a stray rule
      // "from the logo to the far right" instead of as the header's own divider.
      GlassRenderer.drawSeparator(
         (float)this.layoutOffsetX(), (float)HEADER_HEIGHT, (float)(width - CONTENT_TRAILING_PADDING), (float)HEADER_HEIGHT, UiTheme.SEPARATOR_THICKNESS, dividerColor
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

      // The delayed tooltips for the icon-only controls (the rail's rows when it is folded, its group
      // headers, the fold control, the profile buttons) are drawn LAST and outside the content scissor:
      // a tooltip that the rail's own clip rect cut off would be worse than no tooltip at all.
      if (!suppressHoverUnderPopup) {
         this.drawPendingTooltip(n, n2);
      }

      GL11.glPopMatrix();
      GL11.glPopMatrix();
   }

   private void triggerContentTransition(float dir) {
      this.contentTransitionDir = dir;
      this.contentTransitionAnim = 0.0F;
   }

   /**
    * The brand wash colour for the current client colour: the accent with each channel scaled by the
    * measured ratios (see {@code BRAND_WASH_*_PERCENT}), which lands on the reference's {@code #41328F}
    * at the default accent while still following a custom one.
    */
   private static int brandWashColor() {
      int accent = UiTheme.accentColor();
      int r = (accent >> 16 & 255) * BRAND_WASH_R_PERCENT / 100;
      int g = (accent >> 8 & 255) * BRAND_WASH_G_PERCENT / 100;
      int b = (accent & 255) * BRAND_WASH_B_PERCENT / 100;
      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   /**
    * Paints the rail tint as ONE flat column that ends exactly on the rail's own boundary.
    *
    * <p>WHY one primitive with a crisp edge, and not the two-layer "solid core plus gaussian glow" this
    * used to be: the core stopped a whole shadow pad short of the rail boundary (at 228 - 37.2 = 190.8
    * content px) and the glow only reached full strength <em>inside</em> that core, so raising Sidebar
    * Opacity painted a hard alpha step in the middle of the row area with a second, differently placed
    * edge to the right of it - the "todo descuadrado" the tint showed. Two overlapping translucent quads
    * also double-blended through the crossover, so the seam was an over-alpha band rather than a clean
    * hand-off. A single quad has neither problem by construction: there is exactly one shape, it is the
    * shape the rows use, and it therefore cannot disagree with them at any opacity.
    *
    * <p>WHY the edge is crisp rather than feathered: that is what the reference does. Sampling its rail
    * boundary shows the tint's own luminance held flat right up to a step of one or two pixels, so a
    * wide gaussian falloff was never in the target - and a falloff that reaches zero at x = 228 leaves
    * the last ~20px of every row at half strength, which is a second misalignment. The rounded left
    * corners keep the tint inside the shell's own silhouette (the shell clip is a plain scissor rect, so
    * the corner has to come from the geometry).
    *
    * <p>{@code tintArgb} already carries the user's Sidebar Opacity (applied once inside
    * {@link UiTheme#glassSidebarTintArgb()}); this method only shapes it, it never re-scales the alpha.
    */
   private void drawRailTint(int tintArgb, int height, float cornerContent) {
      if ((tintArgb >>> 24) <= 0) {
         return;
      }
      // The tint is laid out in EXPANDED rail widths and never follows the collapse: it is a backdrop
      // painted once per frame, and animating its shape would be a moving large surface for every frame
      // of the transition -- exactly the kind of effect this UI avoids. The rail's content narrows
      // inside it instead, which is the motion the eye actually reads.
      //
      // All four corners are rounded, not just the two that sit on the shell: the tint is a surface in its
      // own right, and a column with two live corners and two square ones reads as a clipped rectangle
      // rather than as a panel. The left pair still uses the shell's own radius, so the tint keeps hugging
      // the window's silhouette in the corners it shares with it.
      GlassRenderer.drawRoundedRect(
         0.0F, 0.0F, (float)SIDEBAR_WIDTH, (float)height,
         cornerContent, cornerContent, cornerContent, cornerContent, tintArgb
      );
   }

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
      /** Group this row belongs to; the row is only laid out while that group is disclosed. */
      final String group;
      /**
       * Where the row sits once every transition has settled. The drawing pass eases the live y
       * towards this, so a row whose group is collapsing glides out instead of jumping, and the rows
       * below it do not teleport when the group above them changes height.
       */
      float targetY;
      /** Live y actually drawn and hit tested this frame; published by the draw pass. */
      float drawY;

      RailRow(int kind, String label, String count, LucideIcon icon, Module module, SidebarTab tabId, String group) {
         this.kind = kind;
         this.label = label;
         this.count = count;
         this.icon = icon;
         this.module = module;
         this.tabId = tabId;
         this.group = group;
         this.targetY = 0.0F;
         this.drawY = 0.0F;
      }

      String animKey() {
         return "rail_" + this.kind + "_" + this.label;
      }
   }

   // ------------------------------------------------------------------------------------------
   // Sidebar state helpers. Everything the rail draws and every hit test that lands on it reads its
   // geometry from these, so the sidebar, the content gutter and the mouse can never disagree.
   // ------------------------------------------------------------------------------------------

   /** Steady-state width of the whole sidebar column (rail + its gutter), in content pixels. */
   private int railColumnTargetW() {
      // The rail is always the expanded column. It used to be foldable down to an icon strip, and that
      // fold control has been removed (the brief asks for it), so the target width is simply the full
      // column. The fold machinery below is kept because the drawing pass reads `railCollapse`; with only
      // one target that value settles at 0 and every fold-aware branch stays on the expanded path.
      return SIDEBAR_WIDTH;
   }

   /**
    * Displayed sidebar width. Every layout caller uses this instead of the constant, which is what
    * lets the rail collapse without the module grid, the settings panel or the category bar being
    * told about it separately: they all read one value and therefore reflow together.
    */
   private int railColumnW() {
      return Math.round(this.railColumnAnimW);
   }

   /** X where the content region starts, i.e. where the sidebar ends plus the content gutter. */
   private int layoutOffsetX() {
      return this.railColumnW() + CONTENT_PADDING;
   }

   /** True while the rail is (or is on its way to being) the compact icon column. */
   private boolean railIsCompact() {
      return this.railCollapse > 0.5F;
   }

   /**
    * Whether a group's rows are laid out at all. Read from the steady-state flag so scroll bounds and
    * group heights settle immediately when the chevron is clicked; only the *visual* reveal is
    * animated (see {@link #railGroupReveal}).
    */
   private boolean railGroupExpanded(String group) {
      Object v = this.railGroupExpanded.get(group);
      return v == null || Boolean.TRUE.equals(v);
   }

   /**
    * Animated disclosure of a group in 0..1, driven here and consumed by the drawing pass.
    *
    * <p>Driven from one place on purpose: an eased reveal read through a lazy accessor would advance
    * once per read, so the rows of a group would fade differently depending on how many times they
    * happened to ask. One update per frame keeps every row of the group in lockstep.
    */
   private float railGroupReveal(String group) {
      return UiAnimation.getState("rail_group_" + group, 1.0F);
   }

   /** Releases or collapses one group and republishes the rail geometry for the next click test. */
   private void toggleRailGroup(String group) {
      boolean open = !this.railGroupExpanded(group);
      this.railGroupExpanded.put(group, open ? Boolean.TRUE : Boolean.FALSE);
      // The scroll offset is a distance into a list that just changed height; keeping it would leave
      // the rail scrolled past its own content when a group closes.
      this.railScrollOffset = 0;
      this.railScrollBar.resetPendingScroll();
      this.buildRailRows();
   }

   /** Advances the sidebar's own transitions once per frame and republishes the rail geometry. */
   private void updateRailAnimations() {
      this.railColumnAnimW = UiAnimation.smooth(this.railColumnAnimW, (float)this.railColumnTargetW(), 15.0F);
      // Collapse amount derives from the same eased width, so the icon/label crossfade and the column
      // widening are literally the same motion instead of two animations that can drift apart.
      float span = (float)(SIDEBAR_WIDTH - SIDEBAR_COLLAPSED_WIDTH);
      this.railCollapse = UiAnimation.clamp01(((float)SIDEBAR_WIDTH - this.railColumnAnimW) / (span <= 0.0F ? 1.0F : span));
      this.railCollapsed = this.railColumnTargetW() == SIDEBAR_COLLAPSED_WIDTH;

      UiAnimation.smoothState("rail_group_" + RAIL_GROUP_MODULES, this.railGroupExpanded(RAIL_GROUP_MODULES) ? 1.0F : 0.0F, 15.0F);
      UiAnimation.smoothState("rail_group_" + RAIL_GROUP_GENERAL, this.railGroupExpanded(RAIL_GROUP_GENERAL) ? 1.0F : 0.0F, 15.0F);

      this.buildRailRows();
   }

   /**
    * Builds the rail's rows and publishes their settled geometry.
    *
    * <p>The rows are navigation only. The module registry is NOT mirrored here (see {@link RailRow}), so the
    * rail cannot disagree with the content's category pills about the selection and does not grow with the
    * registry.
    *
    * <p>Group heights are taken from {@link #railGroupExpanded} (the steady state) even while a group is
    * mid-transition, so the scroll bounds and the next group's header position are already where the
    * animation is heading. Only the reveal and each row's own y are animated, which is what keeps a
    * collapse from making the list below it jump.
    */
   private List buildRailRows() {
      List rows = this.railRows;
      rows.clear();

      // Header position is computed first and the rows only start below it, so a collapsed group still
      // shows its own label and its own chevron -- otherwise a collapsed group would be unreachable,
      // which is the one thing a collapsing section must never be.
      int y = SIDEBAR_GROUP1_TOP;
      this.railGroupModulesHeaderY = y;
      y += RAIL_GROUP_HEADER_H;
      if (this.railGroupExpanded(RAIL_GROUP_MODULES)) {
         y += RAIL_GROUP_LABEL_GAP;
         y = this.addRailGroupRow(rows, new RailRow(
            RailRow.KIND_MODULES, "Modules", String.valueOf(this.countVisibleModules()), LucideIcon.LAYOUT_GRID, null, null, RAIL_GROUP_MODULES
         ), y, true);
         int activeNum = this.countActiveModules();
         y = this.addRailGroupRow(rows, new RailRow(
            RailRow.KIND_TAB, "Active", activeNum > 0 ? String.valueOf(activeNum) : null, LucideIcon.ZAP, null, SidebarTab.ACTIVE, RAIL_GROUP_MODULES
         ), y, true);
         y = this.addRailGroupRow(rows, new RailRow(
            RailRow.KIND_TAB, "Configs", null, LucideIcon.FOLDER, null, SidebarTab.CONFIGS, RAIL_GROUP_MODULES
         ), y, true);
         y = this.addRailGroupRow(rows, new RailRow(
            RailRow.KIND_TAB, "Keybinds", null, LucideIcon.KEYBOARD, null, SidebarTab.KEYBINDS, RAIL_GROUP_MODULES
         ), y, false);
      }

      y += RAIL_GROUP_GAP;
      this.railGroup2HeaderY = y;
      y += RAIL_GROUP_HEADER_H;
      if (this.railGroupExpanded(RAIL_GROUP_GENERAL)) {
         y += RAIL_GROUP_LABEL_GAP;
         y = this.addRailGroupRow(rows, new RailRow(
            RailRow.KIND_MODULE, "ClickGUI", null, LucideIcon.SLIDERS_HORIZONTAL, ClientManagers.moduleManager.clickGui, null, RAIL_GROUP_GENERAL
         ), y, true);
         y = this.addRailGroupRow(rows, new RailRow(
            RailRow.KIND_MODULE, "Blur & Glow", null, LucideIcon.SPARKLES, ClientManagers.moduleManager.blur, null, RAIL_GROUP_GENERAL
         ), y, true);
         y = this.addRailGroupRow(rows, new RailRow(
            RailRow.KIND_MODULE, "Client Color", null, LucideIcon.PALETTE, ClientManagers.moduleManager.customClientColor, null, RAIL_GROUP_GENERAL
         ), y, false);
      }

      // The scrollable extent starts at the first group header, not at the first row: a header is the
      // only control that can bring its own group back, so it has to be reachable by scrolling too.
      // Published in scroll-independent content space so a drag cannot make the track measure itself.
      this.railContentTop = SIDEBAR_GROUP1_TOP;
      this.railContentBottom = y;
      return rows;
   }

   /** Publishes one row at the settled offset {@code y} and returns the offset of the next row. */
   private int addRailGroupRow(List rows, RailRow row, int y, boolean moreAfter) {
      row.targetY = (float)y;
      row.drawY = (float)y;
      rows.add(row);
      return y + (moreAfter ? SIDEBAR_ITEM_STEP : SIDEBAR_ITEM_H);
   }

   /** Group header under the mouse, using the geometry the rail last published. */
   private String railGroupHeaderAt(int mouseX, int mouseY, int height) {
      if (mouseX < RAIL_ROW_X || mouseX > this.railColumnW() - RAIL_ROW_X) {
         return null;
      }
      if (mouseY < RAIL_VIEW_TOP || mouseY > height - FOOTER_HEIGHT - 4) {
         return null;
      }
      // Header boxes are published in scroll-independent content space; the mouse is in the scrolled
      // space the rail drew in, so the offset is removed here and nowhere else.
      float w = (float)(this.railColumnW() - 2 * RAIL_ROW_X);
      if (RenderUtil.isHovered(
         (float)RAIL_ROW_X, (float)(this.railGroupModulesHeaderY - this.railScrollOffset), w, (float)RAIL_GROUP_HEADER_H, mouseX, mouseY
      )) {
         return RAIL_GROUP_MODULES;
      }
      if (RenderUtil.isHovered(
         (float)RAIL_ROW_X, (float)(this.railGroup2HeaderY - this.railScrollOffset), w, (float)RAIL_GROUP_HEADER_H, mouseX, mouseY
      )) {
         return RAIL_GROUP_GENERAL;
      }
      return null;
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

   /**
    * Version label face: Inter Medium at 12px, derived once. Deriving a size builds a new face (atlas handle
    * plus AWT metrics) on every call, so the header must never derive it per frame.
    */
   private CustomFontUtil brandVersionFont() {
      if (this.brandVersionFont == null) {
         this.brandVersionFont = FontManager.interMedium.deriveSize(9.86F);
      }
      return this.brandVersionFont;
   }

   /**
    * Wordmark face: Inter SemiBold at 17px, derived once.
    *
    * <p>Measured off the target, whose wordmark caps are 12.3 content px tall (a ~17px face). Same
    * one-derivation rule as {@link #brandVersionFont()}: deriving a size builds a new face, so the draw
    * path must never do it per frame.
    */
   private CustomFontUtil brandWordmarkFont() {
      if (this.brandWordmarkFont == null) {
         this.brandWordmarkFont = FontManager.interSemiBold.deriveSize(15.2F);
      }
      return this.brandWordmarkFont;
   }

   /**
    * Header title face: Inter SemiBold at 17.5px, derived once.
    *
    * <p>Measured off the reference, whose "Modules" has 14.55-tall caps and 76.0 of ink; the shared
    * {@code interSemiBoldLarge} face is 19.5 and renders 16.25-tall caps, which is a full step too
    * loud for a header label. Same one-derivation rule as the wordmark: deriving a size builds a new
    * face, so the draw path must never do it per frame.
    */
   private CustomFontUtil headerTitleFont() {
      if (this.headerTitleFont == null) {
         this.headerTitleFont = FontManager.interSemiBoldLarge.deriveSize(17.5F);
      }
      return this.headerTitleFont;
   }

   /**
    * Module row name face: Inter SemiBold at 15.5px, derived once.
    *
    * <p>Measured off the reference, whose row names have 13.4-tall caps and whose ink starts 17.0 below
    * the card's top edge. The shared {@code interSemiBold} face is 18.5 and renders 15.7-tall caps,
    * which is a visible step too loud: the whole text block reads oversized against the 61 px card.
    */
   private CustomFontUtil cardTitleFont() {
      if (this.cardTitleFont == null) {
         this.cardTitleFont = FontManager.interSemiBold.deriveSize(15.5F);
      }
      return this.cardTitleFont;
   }

   /** Module row description face: Inter Regular at 11.9px, matching the reference's 10.1-tall caps. */
   private CustomFontUtil cardDescFont() {
      if (this.cardDescFont == null) {
         this.cardDescFont = FontManager.interRegular.deriveSize(11.9F);
      }
      return this.cardDescFont;
   }

   /**
    * Group header face: Inter Medium at 11.4px, letter-spaced by the caller.
    *
    * <p>Measured off the target: its "MODULES & CONFIG" caps are 8.1 content px tall, which is an
    * ~11.2px face, and its strokes are the same weight as a row label's -- a Bold header at 12px
    * (what this used to be) reads a full step heavier than everything under it. Derived once, for the
    * same reason as {@link #brandVersionFont()}.
    */
   private InterFontUtil railHeaderFont() {
      if (this.railHeaderFont == null) {
         this.railHeaderFont = FontManager.interMedium.deriveSize(11.4F);
      }
      return this.railHeaderFont;
   }

   /**
    * Row label face: Inter Medium at 13.4px, derived once.
    *
    * <p>Measured off the target: row label caps are 9.74 content px and "Client Color" inks 72.2
    * content px wide, which agree on a 13.4px face (Inter Medium at 13.4 predicts 9.75 caps and 72.9
    * width). Inter Medium's own 16px default is a fifth larger than that on both axes.
    */
   private InterFontUtil railLabelFont() {
      if (this.railLabelFont == null) {
         this.railLabelFont = FontManager.interMedium.deriveSize(13.4F);
      }
      return this.railLabelFont;
   }

   /**
    * Row count face: Inter Medium at 12.25px, derived once.
    *
    * <p>Measured off the target: count digits are 8.92 content px tall (cap height) and "248" inks
    * 20.3 content px wide, which agree on a ~12.3px face. Tabular figures are what keep the column
    * from jittering, and the derived face keeps them.
    */
   private InterFontUtil railCountFont() {
      if (this.railCountFont == null) {
         this.railCountFont = FontManager.interMedium.deriveSize(12.25F);
      }
      return this.railCountFont;
   }

   /**
    * Footer name face: Inter SemiBold at 13.4px, derived once.
    *
    * <p>The target inks the player name 13 ref px tall -- the same height as a row label -- so the
    * footer's hierarchy is carried by weight, not by size. Inter Bold at 20.5px (what this used to
    * be) made the name the largest text in the rail, which the reference never does.
    */
   private InterFontUtil footerNameFont() {
      if (this.footerNameFont == null) {
         this.footerNameFont = FontManager.interSemiBold.deriveSize(13.4F);
      }
      return this.footerNameFont;
   }

   /**
    * Footer status face: Inter Regular at 11.2px, derived once.
    *
    * <p>Measured: the status line's caps are ~8.1 content px, one step under the name, and its ink
    * sits 18 content px below the name's baseline.
    */
   private InterFontUtil footerStatusFont() {
      if (this.footerStatusFont == null) {
         this.footerStatusFont = FontManager.interRegular.deriveSize(11.2F);
      }
      return this.footerStatusFont;
   }

   /**
    * Version lines, longest first, composed once: "BETA RELEASE <v>", "BETA <v>", "<v>".
    *
    * <p>Composed once because the string never changes and the header runs every frame: a per-frame
    * concat here would allocate on the draw path, which this window does not do.
    */
   private String[] versionLines() {
      if (this.versionLines == null) {
         String version = Augustus.getInstance() != null ? Augustus.getInstance().getVersion() : null;
         if (version == null || version.isEmpty()) {
            this.versionLines = new String[0];
         } else {
            String upper = version.toUpperCase(Locale.ROOT);
            this.versionLines = new String[]{"BETA RELEASE " + upper, "BETA " + upper, upper};
         }
      }
      return this.versionLines;
   }

   /** True when the two rail groups at their settled sizes still fit the scrollable band. */
   private boolean railContentFits(int viewTop, int viewBottom) {
      return this.railContentBottom - this.railContentTop <= viewBottom - viewTop;
   }

   /**
    * Records what the pointer is over so a tooltip can be shown once it has rested there.
    *
    * <p>The delay lives here, in one place, for every icon-only element in the window: the rail in its
    * collapsed state, the profile footer's buttons and the header's segmented controls. A pointer
    * sweeping across the window therefore produces no tooltips at all, which is the whole point.
    */
   private void publishRailTooltip(String key, String label) {
      this.hoverItemKey = key;
      this.hoverItemLabel = label;
   }

   /**
    * Draws the pending tooltip, if any, for the element the pointer has been resting on. Called after
    * the content pass so the tooltip is never clipped by a region scissor.
    */
   private void drawPendingTooltip(int mouseX, int mouseY) {
      String key = this.hoverItemKey;
      if (key == null || this.hoverItemLabel == null) {
         this.tooltipHoverKey = null;
         this.tooltipPendingLabel = null;
         return;
      }
      if (this.popupHost.hasActivePopup() || this.hoveringCategoryContextMenu || this.hoveringModuleContextMenu) {
         return;
      }
      if (!key.equals(this.tooltipHoverKey)) {
         // Pointer moved to a different element: restart the dwell so a tooltip never appears for an
         // element the pointer merely crossed on its way to this one.
         this.tooltipHoverKey = key;
         this.tooltipPendingLabel = null;
         this.tooltipHoverSince = System.currentTimeMillis();
         return;
      }
      if (this.tooltipPendingLabel != null) {
         TooltipRenderer.drawTooltip(FontManager.interMedium, this.tooltipPendingLabel, mouseX, mouseY);
         return;
      }
      if (System.currentTimeMillis() - this.tooltipHoverSince >= TOOLTIP_DELAY_MS) {
         this.tooltipPendingLabel = this.hoverItemLabel;
      }
   }

   /**
    * Prestige-style rail: brand mark, two collapsible groups of navigation rows, a floating glass item
    * behind the selected row, and the profile footer pinned at the bottom.
    */
   private void drawSidebar(int mouseX, int mouseY, int width, int height, int accentColor, int textColor, int dividerColor) {
      float colW = this.railColumnAnimW;
      // The brand block now owns the whole rail: the fold control that used to reserve this row's trailing
      // end is gone, so the wordmark and the version line stop being truncated against a control that no
      // longer exists.
      float rightEdge = Math.max(RAIL_BRAND_X + 18.0F, colW - (float)RAIL_ROW_X);
      float compact = UiAnimation.easeOutCubic(this.railCollapse);
      float reveal = 1.0F - compact;

      // Wordmark row and the version row under it. Measured: the target's wordmark caps are centred at
      // content y 39 and its version line at 58.5, so the two rows are 20 apart and the whole block
      // starts low enough that the mark's ink clears the panel's own top rounding.
      float brandY = 25.56F;
      float subY = brandY + 19.45F;

      // Brand mark: the reference's own "A", lifted out of the reference render at 6x because the
      // artwork that used to ship here was a different drawing (0.73 silhouette IoU against the
      // reference, versus 0.95 for this one). Its ink is 50.2 x 49.4 at content (27.1, 22.7), so the
      // file -- which carries a ~1.6px margin -- is drawn at 53.5 x 52.6 from (25.45, 21.11).
      //
      // WHY the mark is only faded, never rescaled: alpha does not shrink geometry. A mark drawn at 20 % still
      // spans its full width, so a linear fade would smear the logo across the text block. It stays at full
      // strength while it has clearance, ramps out over a short band, and is dropped once the block cannot
      // hold it.
      boolean hasMark = BRAND_MARK.isReady();
      float markH = BRAND_MARK_HEIGHT;
      float markW = hasMark ? markH * BRAND_MARK.getAspect() : 0.0F;
      float markRoom = rightEdge - RAIL_BRAND_X;
      float markAlpha = hasMark ? UiAnimation.clamp01((markRoom - markW) / RAIL_BRAND_MARK_FADE_BAND) : 0.0F;
      boolean markShown = hasMark && markAlpha > 0.02F;
      if (markShown) {
         int markAlpha255 = Math.round(255.0F * markAlpha);
         BRAND_MARK.draw(
            RAIL_BRAND_X,
            BRAND_MARK_Y,
            markW,
            markH,
            UiTheme.accentColor(),
            fadedArgb(scaleAlphaOf(0xFFFFFFFF, markAlpha255)),
            fadedArgb(scaleAlphaOf(0xFFFFFFFF, markAlpha255))
         );
      }

      // Name and version: the name carries the hierarchy, the version is metadata one step down the
      // type scale and a register quieter, never a second wordmark. Both keep the same left column as
      // the mark, so the block has one edge instead of three.
      float brandTextX = RAIL_BRAND_X + (markShown ? markW + BRAND_MARK_GAP : 0.0F);
      // The protected badge shares the version row, so the version line has to fit beside it.
      boolean protectedBadge = (!ClickGuiScreen.configEditable || ClickGuiScreen.protectedConfigCreator) && rightEdge - brandTextX >= 86.0F;
      if (compact < 0.99F && brandTextX < rightEdge - 12.0F) {
         // Title case, not caps: the reference sets its wordmark "Augustus" and keeps the uppercase
         // register for the version line underneath, so the two rows differ by case as well as size.
         String brand = "Augustus";
         // 17px semibold: measured off the target, whose wordmark caps are 12.3 content px tall and
         // whose ink is 71.4 content px wide, both of which put it at ~17px.
         CustomFontUtil brandFont = this.brandWordmarkFont();
         float brandWidth = brandFont.getStringWidth(brand);
         if (brandTextX + brandWidth > rightEdge) {
            brandFont = FontManager.interMedium;
            brandWidth = brandFont.getStringWidth(brand);
         }
         if (brandTextX + brandWidth <= rightEdge) {
            brandFont.drawVerticallyCenteredString(
               brand, (double)brandTextX, (double)brandY, (double)BRAND_ROW_HEIGHT,
               fadedArgb(scaleAlphaOf(UiTheme.TEXT_PRIMARY, Math.round(255.0F * reveal)))
            );
         }

         // The reference's version row is a stage line, not a bare number: "BETA RELEASE 2.8.0" in caps,
         // one step down the type scale and a register quieter. The number is the client's real version,
         // so this line tracks the build instead of drifting from it, and the prefix steps down to the
         // longest form that clears the fold control and the protected badge.
         float versionRight = protectedBadge ? rightEdge - 94.0F : rightEdge;
         CustomFontUtil versionFont = this.brandVersionFont();
         String[] versionLines = this.versionLines();
         for (int i = 0; i < versionLines.length; ++i) {
            String versionLine = versionLines[i];
            if (brandTextX + versionFont.getStringWidth(versionLine) <= versionRight) {
               versionFont.drawVerticallyCenteredString(
                  versionLine, (double)brandTextX, (double)subY, (double)BRAND_ROW_HEIGHT,
                  fadedArgb(scaleAlphaOf(UiTheme.mutedTextColor(), Math.round(235.0F * reveal)))
               );
               break;
            }
         }
      }

      // Protected-configuration badge, in the second brand row, only where the reserve leaves room.
      if (protectedBadge) {
         GlassRenderer.drawRoundedBox(
            brandTextX, subY + 1.0F, 86.0F, 13.0F, 4.0F,
            fadedArgb(UiTheme.withAlpha(UiTheme.enabledColor(), 40)),
            fadedArgb(UiTheme.withAlpha(UiTheme.enabledColor(), 145)),
            1.0F
         );
         LucideIcon.LOCK.drawCentered(brandTextX + 7.5F, subY + 7.5F, 8.5F, fadedArgb(UiTheme.enabledColor()));
         FontManager.interSmall.drawVerticallyCenteredString(
            "Protected", (double)(brandTextX + 14.0F), (double)(subY + 1.0F), 13.0, fadedArgb(UiTheme.enabledColor())
         );
      }

      int mutedText = fadedArgb(UiTheme.mutedTextColor());

      // Scrollable viewport: the group headers and rows clip against the band between the brand block
      // and the pinned profile footer, and scroll inside it when the zoom (or a short window) leaves
      // too little room. The band is fully inside the shell, so it can replace the shell clip for the
      // duration of the rail; the shell rect is re-issued afterwards (glScissor has no stack of its own).
      int viewTop = RAIL_VIEW_TOP;
      int viewBottom = height - FOOTER_HEIGHT - 4;
      int contentH = Math.max(1, this.railContentBottom - this.railContentTop);
      // Row box for this frame: one value, read by the drawing pass and by the hit test. It widens with
      // the column, so the row is never wider than the rail it sits in -- at the narrow end of the fold
      // the row would otherwise hang out over the content region and stay clickable out there.
      this.railRowX = (float)RAIL_ROW_X;
      this.railRowW = Math.max(1.0F, colW - (float)(2 * RAIL_ROW_X));
      RenderUtil.scissorRectScaled(
         this.contentToScreenX(0.0F), this.contentToScreenY((float)viewTop), this.contentToScreenX(Math.max(1.0F, colW)), this.contentToScreenY((float)viewBottom), 1.0
      );

      // Group headers first: the selected item's plate is drawn behind the rows, and both groups' rows
      // need their settled geometry published before the plate can travel to the right one. Only the
      // second header carries a rule; the first is already delimited by the brand block above it.
      this.drawRailGroupHeader(
         RAIL_GROUP_MODULES, (float)this.railGroupModulesHeaderY - (float)this.railScrollOffset, mouseX, mouseY, dividerColor, accentColor, false
      );
      this.drawRailGroupHeader(
         RAIL_GROUP_GENERAL, (float)this.railGroup2HeaderY - (float)this.railScrollOffset, mouseX, mouseY, dividerColor, accentColor, true
      );

      // Rows compute their eased y in this pass, so the selected item's plate can be drawn behind them
      // at the exact y the selected row ended up at -- with one plate, one easing, and no ordering
      // dependency left between the two.
      RailRow selected = null;
      for (Object obj : this.railRows) {
         RailRow row = (RailRow)obj;
         row.drawY = UiAnimation.smoothState(row.animKey() + "_y", row.targetY, 16.0F);
         if (selected == null && this.isRailRowActive(row)) {
            selected = row;
         }
      }

      this.drawActiveRailItem(selected, 1.0F - 0.35F * compact);

      for (Object obj : this.railRows) {
         RailRow row = (RailRow)obj;
         float revealGroup = UiAnimation.easeOutCubic(this.railGroupReveal(row.group));
         if (revealGroup <= 0.01F) {
            continue; // fully closed group: nothing to draw, and (see buildRailRows) nothing to hit
         }
         if (row.drawY + SIDEBAR_ITEM_H < viewTop || row.drawY > viewBottom) {
            continue; // clipped away: drawn neither here nor clickable
         }
         this.drawRailRow(row, mouseX, mouseY, this.isRailRowActive(row));
      }

      // Restore the shell clip for everything that follows (footer, context menus, popups, tooltips).
      this.clipToShell();

      // The scroll track only earns its place when the rail actually overflows; drawing it on a rail
      // that fits is visual noise and a false affordance.
      if (!this.railContentFits(viewTop, viewBottom)) {
         int trackX = this.railColumnW() - 6;
         int accent = UiTheme.accentColor();
         this.railScrollBar.drawVerticalBar(trackX - 3, viewTop, trackX, viewBottom, -this.railScrollOffset, contentH, fadedArgb(accent));
         this.railScrollOffset = this.railScrollBar.updateScrollOffset(
            contentH, viewTop, viewBottom, this.railScrollOffset, 0, viewTop, this.railColumnW(), viewBottom - viewTop, mouseX, mouseY
         );
      } else if (this.railScrollOffset != 0) {
         this.railScrollOffset = 0;
      }
      // A folded rail fits its content by construction (see railContentFits), so the scroll state has
      // to be cleared with the fold or it would persist across the expansion and hold the rail scrolled
      // past its own first row the next time it opens.
      if (this.railIsCompact()) {
         this.railScrollOffset = 0;
      }

      // Bottom profile footer: pinned, and the last thing the rail paints, so nothing can overlap it.
      int footerY = height - FOOTER_HEIGHT;
      GlassRenderer.drawSeparator(
         (float)RAIL_ROW_X, (float)footerY, colW - (float)RAIL_ROW_X, (float)footerY, UiTheme.SEPARATOR_THICKNESS, dividerColor
      );
      this.drawProfileFooter(footerY, accentColor, textColor, mutedText, mouseX, mouseY);
   }

   /**
    * ONE rail row: icon, label, optional right-aligned count, and the states it can be in.
    *
    * <p>The selected row is carried by {@link #drawActiveRailItem} -- a floating glass plate behind the
    * rows -- so this method only paints the row's <em>content</em> on top of it, plus a hover veil for
    * rows that are not the selected one. That split is what lets one plate travel between rows while
    * each row's content is simply drawn at its own place.
    */
   private void drawRailRow(RailRow row, int mouseX, int mouseY, boolean active) {
      float compact = UiAnimation.easeOutCubic(this.railCollapse);
      float reveal = UiAnimation.easeOutCubic(this.railGroupReveal(row.group));
      float w = this.railRowW;
      float x = this.railRowX;
      // Live y read from the row: the rail's own pass already eased it this frame (see drawSidebar), so
      // the row, the plate behind it and the hit test all use one value and cannot drift apart.
      float y = row.drawY;
      float h = (float)SIDEBAR_ITEM_H;

      boolean pointerInRow = this.railGroupPointerInRow(row, mouseX, mouseY);
      // Collapsed rail: the label is gone, so the row's own name becomes its tooltip. This is what
      // makes the compact mode usable at all -- without it the rail would be a column of glyphs.
      if (this.railIsCompact() && pointerInRow) {
         this.publishRailTooltip(row.animKey(), row.label);
      }

      boolean hovered = !this.railIsCompact()
         && pointerInRow
         && !this.hoveringCategoryContextMenu
         && !this.hoveringModuleContextMenu;
      float hovT = UiAnimation.smoothState(row.animKey() + "_hov", hovered ? 1.0F : 0.0F, 16.0F);
      float actT = UiAnimation.smoothState(row.animKey() + "_act", active ? 1.0F : 0.0F, 18.0F);

      // Hover plate: the selected item's own plate at a fraction of its strength, on the same material.
      //
      // WHY not a separate glass control any more: a hover that paints a different surface (a raised glass
      // control with its own rim, its own sheen and its own tint maths) is a different object from the plate
      // the reference uses for selection, so the rail showed two unrelated hover languages - "you are here"
      // and "you could be here" looked like widgets from different designs. The reference only ever
      // publishes one plate, so hover is that exact plate - same geometry, same horizontal gradient, same
      // material - at a fraction of its state-layer alpha.
      float hovVis = hovT * (1.0F - actT) * reveal;
      if (hovVis > 0.03F) {
         float hovY = y - RAIL_PLATE_OVERHANG;
         float hovH = h + 2.0F * RAIL_PLATE_OVERHANG;
         float hovAlpha = RAIL_PLATE_ALPHA * RAIL_HOVER_ALPHA_SCALE * hovVis;
         GlassRenderer.drawGlassMaterialPane(x, hovY, w, hovH, RAIL_PLATE_RADIUS, UiTheme.LEVEL_CONTROL_RAISED);
         GlassRenderer.drawRoundedGradientBoxHorizontal(
            x,
            hovY,
            w,
            hovH,
            RAIL_PLATE_RADIUS,
            fadedArgb(UiTheme.withAlpha(railPlateLeadingRgb(), Math.round(hovAlpha))),
            fadedArgb(UiTheme.withAlpha(railPlateTrailingRgb(), Math.round(hovAlpha))),
            1.0F
         );
      }

      // Content is eased, never snapped: the icon glides from its gutter position to the compact rail's
      // optical centre while the label fades out underneath it, so the collapse reads as one motion
      // instead of the icon jumping to the middle on the last frame. The two gutter positions are the
      // measured ones, expressed relative to the row box so they follow RAIL_ROW_X.
      float iconRel = RAIL_ICON_CENTER_X - x + (w * 0.5F - (RAIL_ICON_CENTER_X - x)) * compact;
      float labelRel = RAIL_LABEL_X - x + (w * 0.5F - (RAIL_LABEL_X - x)) * compact;
      // Centre of the count column, row-local, so the badge and a bare count share one column.
      float countCx = RAIL_COUNT_CENTER_X - x;
      float slide = hovered ? UiAnimation.easeOutCubic(hovT) * 2.0F : 0.0F;
      int contentFade = Math.round(255.0F * UiAnimation.clamp01(reveal * (1.0F - compact)));

      // Fade once, on the final RGB-resolved colour: fading an input of a later lerp would apply the
      // envelope twice and darken the accent ramp as well as the alpha.
      //
      // WHY selection resolves to TEXT_PRIMARY and not to the accent: the reference's selected row is a
      // WHITE label and a WHITE icon on the violet plate - the plate is where the accent lives. Tinting
      // the content with the accent as well spent the accent twice and left the label at roughly 60% of
      // the plate's luminance, which is the "the selected row is not the same" the row showed.
      int idleFg = UiTheme.secondaryTextColor();
      int fg = UiTheme.lerpArgb(idleFg, UiTheme.TEXT_PRIMARY, Math.max(actT, hovT * 0.85F));
      int iconColor = fadedArgb(scaleAlphaOf(UiTheme.lerpArgb(fg, UiTheme.TEXT_PRIMARY, actT), contentFade));
      int labelColor = fadedArgb(scaleAlphaOf(UiTheme.lerpArgb(fg, UiTheme.TEXT_PRIMARY, actT), contentFade));

      // The label's box has to be resolved before anything is painted: the selected row's bloom below needs
      // the exact trimmed string the crisp pass is about to draw, and trimming twice with the same width
      // would only be two chances to disagree.
      float countW = row.count == null ? 0.0F : this.railCountFont().getStringWidthTabular(row.count);
      boolean badge = actT > 0.35F && row.count != null;
      float badgeW = countW + 14.0F;
      float countHalf = row.count == null ? 0.0F : (badge ? badgeW : countW) * 0.5F;
      float labelMaxW = Math.max(36.0F, countCx - countHalf - 8.0F - labelRel);
      String safeLabel = ModernSettingsPanel.trimToWidth(this.railLabelFont(), row.label, labelMaxW);
      boolean showLabel = compact < 0.99F;

      // ---- selected row bloom -------------------------------------------------------------------
      //
      // The active item's label and icon glow in the client colour. The glyph is its own silhouette, so the
      // halo is built from the glyph: eight copies on a small ring, each far too faint to read on its own,
      // whose overlap is the bloom. Nothing is drawn for an unselected row, so the rail pays for this at
      // most nine extra draws per frame - one letterform and one glyph.
      if (actT > 0.02F) {
         int glowArgb = fadedArgb(scaleAlphaOf(
            UiTheme.lerpArgb(UiTheme.accentColor(), 0xFFFFFFFF, 0.55F),
            Math.round(RAIL_GLOW_TAP_ALPHA * actT * ((float)contentFade / 255.0F))
         ));
         for (int tap = 0; tap < 8; tap++) {
            double angle = Math.PI * 0.25D * (double)tap;
            float offsetX = (float)Math.cos(angle) * RAIL_GLOW_RADIUS;
            float offsetY = (float)Math.sin(angle) * RAIL_GLOW_RADIUS;
            if (row.icon != null) {
               row.icon.drawCentered(
                  x + iconRel + slide + offsetX, y + h * 0.5F + offsetY, RAIL_ICON_SIZE, glowArgb, false
               );
            }
            if (showLabel) {
               this.railLabelFont().drawVerticallyCenteredString(
                  safeLabel, (double)(x + labelRel + slide + offsetX), (double)(y + offsetY), (double)h, glowArgb
               );
            }
         }
      }

      if (row.icon != null) {
         // No accent pool behind the selected icon any more: the reference's plate is flat behind the
         // glyph, and a coloured disc there read as a badge hanging off the icon rather than as the
         // plate catching light. The bloom above is what carries the "lit" read instead.
         row.icon.drawCentered(x + iconRel + slide, y + h * 0.5F, RAIL_ICON_SIZE, iconColor);
      }

      if (!showLabel) {
         return; // fully compact: no label or count layer is painted at all
      }

      this.railLabelFont().drawVerticallyCenteredString(safeLabel, (double)(x + labelRel + slide), (double)y, (double)h, labelColor);

      if (row.count != null) {
         int countColor = fadedArgb(scaleAlphaOf(UiTheme.lerpArgb(UiTheme.mutedTextColor(), UiTheme.TEXT_PRIMARY, actT), contentFade));
         if (badge) {
            float bh = 21.0F;
            float bw = badgeW;
            float bx = x + countCx - bw * 0.5F;
            float by = y + (h - bh) * 0.5F;
            // Matches the row content's own envelope, so the chip can never outlive the label it
            // belongs to while a group is closing or the rail is folding.
            float badgeVis = actT * UiAnimation.clamp01((float)contentFade / 255.0F);
            // Solid muted-violet capsule, no ring and white digits - the reference's own chip. The ring
            // this used to have made the count read as a second control (an outlined button) sitting on
            // the plate instead of as a quiet counter.
            GlassRenderer.drawRoundedRect(
               bx, by, bw, bh, bh * 0.5F,
               fadedArgb(UiTheme.withAlpha(railCountChipRgb(), Math.round(238.0F * badgeVis)))
            );
            this.railCountFont().drawCenteredStringInBoxTabular(
               row.count, (double)bx, (double)by, (double)bw, (double)bh, fadedArgb(scaleAlphaOf(0xFFFFFFFF, contentFade))
            );
         } else {
            float countY = y + (h - this.railCountFont().getFontHeight()) * 0.5F;
            // Centred on the count column, so switching a row between a bare count and its chip never
            // shifts the number sideways.
            this.railCountFont().drawStringTabular(row.count, x + countCx - countW * 0.5F, countY, countColor);
         }
      }
   }

   /** Whether the pointer is inside one rail row's box, at the position the row was last drawn at. */
   private boolean railGroupPointerInRow(RailRow row, int mouseX, int mouseY) {
      return RenderUtil.isHovered(this.railRowX, row.drawY, this.railRowW, (float)SIDEBAR_ITEM_H, mouseX, mouseY);
   }

   /**
    * The row the pointer is over, using the exact geometry {@link #drawRailRow} drew with -- including
    * the eased y and the collapsed rail's box -- so a click can never land on a row the pointer is not
    * visibly over. Compact rows are still clickable: the rail is navigation, not decoration.
    */
   private RailRow railRowAt(int mouseX, int mouseY, int height) {
      int viewTop = RAIL_VIEW_TOP;
      int viewBottom = height - FOOTER_HEIGHT - 4;
      if (mouseY < viewTop || mouseY > viewBottom) {
         return null; // clipped rows are not clickable
      }
      for (Object obj : this.railRows) {
         RailRow row = (RailRow)obj;
         if (RenderUtil.isHovered(this.railRowX, row.drawY, this.railRowW, (float)SIDEBAR_ITEM_H, mouseX, mouseY)) {
            return row;
         }
      }
      return null;
   }

   /**
    * The floating selected item: one solid plate drawn behind the rows that slides to whichever row is
    * selected, so the rail has a single moving object instead of a highlight that blinks between rows.
    *
    * <p>WHY it is NOT the glass pill it once was, and NOT the opaque plate it briefly became: the pill had a
    * rim, a leading spine and its own shadow, none of which the reference's Active Item has - the spine put a
    * second, brighter vertical edge 5px inside the plate's own edge and the rim drew an outline around a
    * block the reference leaves unbordered. Removing all three fixed the shape, but painting the plate as a
    * flat opaque fill lost the thing that makes it belong to this UI: it stopped being glass. What is left
    * here is the reference's construction (one rounded block whose fill runs from a brighter violet on the
    * leading edge to a duller one on the trailing edge) painted ON the shared raised-control material, so
    * the rail's own backdrop still reads through it, and no rim, spine or shadow at all.
    *
    * @param activeRow the selected row, already eased for this frame, or null when nothing is selected
    */
   private void drawActiveRailItem(RailRow activeRow, float revealOverall) {
      this.railPillVis = UiAnimation.smooth(this.railPillVis, activeRow != null ? 1.0F : 0.0F, 16.0F);
      if (activeRow != null) {
         // x and width are eased (the rail is widening or narrowing under the plate), but y is taken
         // straight from the row. The row already eases its own y, so the plate riding on it *is* the
         // motion; a second easing here would only add a lag on top of it.
         this.railPillX = UiAnimation.smooth(this.railPillX, this.railRowX, 20.0F);
         this.railPillW = UiAnimation.smooth(this.railPillW, this.railRowW, 20.0F);
         // The plate is taller than the row box it marks (measured: 45 against 38), and it stays
         // centred on it, so the row needs no vertical easing of its own.
         this.railPillY = activeRow.drawY - RAIL_PLATE_OVERHANG;
         this.railPillH = (float)SIDEBAR_ITEM_H + 2.0F * RAIL_PLATE_OVERHANG;
      }

      float a = this.railPillVis * revealOverall;
      if (a <= 0.02F) {
         return;
      }

      float px = this.railPillX;
      float py = this.railPillY;
      float pw = this.railPillW;
      float ph = this.railPillH;

      // The plate is the reference's own horizontal violet gradient, but as GLASS and not as a solid block:
      // the raised-control material is painted first, so the row's backdrop reads through the plate exactly
      // as it does through every other nested surface of this UI, and the violet is the state layer on top
      // of that material rather than the surface itself. Alpha carries the density a selected row needs
      // while still leaving the material visible. There is deliberately no rim, no leading spine and no
      // drop shadow under it - those three were what made the old pill a different object from the
      // reference's plate.
      GlassRenderer.drawGlassMaterialPane(px, py, pw, ph, RAIL_PLATE_RADIUS, UiTheme.LEVEL_CONTROL_RAISED);
      GlassRenderer.drawRoundedGradientBoxHorizontal(
         px,
         py,
         pw,
         ph,
         RAIL_PLATE_RADIUS,
         fadedArgb(UiTheme.withAlpha(railPlateLeadingRgb(), Math.round(RAIL_PLATE_ALPHA * a))),
         fadedArgb(UiTheme.withAlpha(railPlateTrailingRgb(), Math.round(RAIL_PLATE_ALPHA * a))),
         1.0F
      );
   }

   /** Scales an ARGB's alpha to an absolute 0..255 value, keeping its RGB. */
   private static int scaleAlphaOf(int argb, int alpha0to255) {
      return (Math.max(0, Math.min(255, alpha0to255)) << 24) | (argb & 0x00FFFFFF);
   }

   /** Reads an ARGB's alpha byte. */
   private static int alphaOf(int argb) {
      return (argb >>> 24) & 255;
   }

   /**
    * One collapsible group header: a full-width separator, a caps label, and the disclosure chevron.
    *
    * <p>The header is a control, not a caption, so it gets a hit box, a hover state and a press detent
    * like every other control -- and it stays visible while its group is closed, which is the only way
    * a collapsed group can be opened again. The chevron is always drawn (at a low alpha when idle)
    * because the affordance is the point: a section that only reveals its toggle on hover is a section
    * nobody discovers.
    *
    * <p>WHY the separator sits ABOVE the label and spans the whole rail: that is the grouping idiom the
    * target uses, and it is the one that survives a fold. A rule that runs from the label to the
    * chevron draws a line between two things that belong together and then disappears with the label.
    */
   private void drawRailGroupHeader(String group, float y, int mouseX, int mouseY, int dividerColor, int accentColor, boolean withSeparator) {
      float x = (float)RAIL_ROW_X;
      float w = this.railRowW;
      float h = (float)RAIL_GROUP_HEADER_H;
      String label = RAIL_GROUP_MODULES.equals(group) ? RAIL_GROUP_MODULES_LABEL : RAIL_GROUP_GENERAL_LABEL;
      float compact = UiAnimation.easeOutCubic(this.railCollapse);

      // Separator: the group's own rule, measured 12.5px above the header box, so it reads as "the
      // section starts here" rather than "the previous section ends here".
      if (withSeparator) {
         float ruleY = y - RAIL_GROUP_RULE_OFFSET;
         float ruleRight = Math.max(x + 8.0F, this.railColumnAnimW - (float)RAIL_ROW_X);
         GlassRenderer.drawSeparator(x, ruleY, ruleRight, ruleY, UiTheme.SEPARATOR_THICKNESS, dividerColor);
      }

      boolean hovered = RenderUtil.isHovered(x, y, w, h, mouseX, mouseY) && !this.hoveringCategoryContextMenu && !this.hoveringModuleContextMenu;
      if (hovered) {
         this.publishRailTooltip("rail_group_" + group, (this.railGroupExpanded(group) ? "Collapse " : "Expand ") + label);
      }
      float hovT = UiTheme.smoothStateAt("rail_grp_hov_" + group, hovered ? 1.0F : 0.0F, 16.0F);
      float pressT = UiTheme.smoothPress("rail_grp_prs_" + group, 0.0F, hovered && UiAnimation.isPrimaryMouseDown());

      if (hovT > 0.03F && compact < 0.5F) {
         // Group headers are quiet targets: hover is a whisper of the accent, not a button.
         GlassRenderer.drawRoundedRect(
            x, y, w, h, UiTheme.CONTROL_RADIUS, fadedArgb(UiTheme.withAlpha(accentColor, Math.round(16.0F * hovT * (1.0F - compact))))
         );
      }

      if (compact < 0.99F) {
         int labelColor = fadedArgb(scaleAlphaOf(
            UiTheme.lerpArgb(UiTheme.mutedTextColor(), UiTheme.TEXT_SECONDARY, hovT * 0.9F), Math.round(255.0F * (1.0F - compact))
         ));
         // Measured off the target: the caps label's ink starts at content x 17.5, i.e. 5.5 in from the
         // row gutter. The disclosure glyph the reference does NOT have is gone (see the class note on
         // the rail's own chrome), so the label is the whole header and its column is the only cue.
         this.railHeaderFont().drawStringSpaced(
            label, x + 5.5F, y + (h - this.railHeaderFont().getFontHeight()) * 0.5F, 1.0F, labelColor
         );
      }
   }

   /** Small icon control that folds the rail down to the compact icon column, and back out of it. */
   /** Float-rect hit test, the same rule {@link net.augustus.utils.render.RenderUtil#isHovered} uses. */
   private boolean isHovered(float x, float y, float width, float height, int mouseX, int mouseY) {
      return (float)mouseX >= x && (float)mouseY >= y && (float)mouseX < x + width && (float)mouseY < y + height;
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

   /**
    * Pinned profile footer: the player block and the two settings shortcuts.
    *
    * <p>Measured off the target, this is deliberately NOT a card. The reference puts its hairline (drawn
    * by the caller), then the avatar, the name and the status line straight on the rail's own material,
    * with the two icon-only shortcuts sharing a single capsule at the trailing edge -- no panel, no
    * second border around the block, no per-icon wells. A carded footer is what made the rail read as
    * panels inside a panel, which is exactly the density the reference avoids, and it also spent
    * vertical room the taller rows now need.
    */
   private void drawProfileFooter(int footerY, int accentColor, int textColor, int mutedText, int mouseX, int mouseY) {
      float colW = this.railColumnAnimW;
      float compact = UiAnimation.easeOutCubic(this.railCollapse);
      // Everything in the band is centred on its middle, which is what keeps the avatar, the name and
      // the shortcuts on one optical line at any footer height.
      float bandCentre = (float)footerY + (float)FOOTER_HEIGHT * 0.5F;

      int avatarSize = FOOTER_AVATAR_SIZE;
      // Compact rail: the avatar slides to the middle of the narrow column instead of hugging a gutter
      // that no longer has anything beside it.
      float centredX = (colW - (float)avatarSize) * 0.5F;
      float avatarX = FOOTER_AVATAR_X + (centredX - FOOTER_AVATAR_X) * compact;
      // Measured off the target: the avatar box is y 589.4..624.7 inside a band whose centre is 607.5, so
      // its top is 0.6 ABOVE the arithmetic centre line rather than 2.5 below it.
      float avatarY = bandCentre - (float)avatarSize * 0.5F - 0.6F;
      int avatarXi = Math.round(avatarX);
      int avatarYi = Math.round(avatarY);
      GlassRenderer.drawSoftShadow(avatarX, avatarY, (float)avatarSize, (float)avatarSize, UiTheme.CONTROL_RADIUS, 6.0F, 1.0F, UiTheme.withAlpha(accentColor, 42));
      GlassRenderer.drawRoundedBox(avatarX, avatarY, (float)avatarSize, (float)avatarSize, UiTheme.CONTROL_RADIUS, UiTheme.glassRecessedFillArgb(0.0F, 0.0F, accentColor), fadedArgb(UiTheme.withAlpha(UiTheme.accentSoftGlow(), 160)), UiTheme.BORDER_THICKNESS);

      if (mc.thePlayer != null && mc.thePlayer.getLocationSkin() != null) {
         GlStateManager.enableTexture2D();
         GlStateManager.enableBlend();
         GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
         mc.getTextureManager().bindTexture(mc.thePlayer.getLocationSkin());
         Gui.drawScaledCustomSizeModalRect(avatarXi + 2, avatarYi + 2, 8.0F, 8.0F, 8, 8, avatarSize - 4, avatarSize - 4, 64.0F, 64.0F);
         Gui.drawScaledCustomSizeModalRect(avatarXi + 2, avatarYi + 2, 40.0F, 8.0F, 8, 8, avatarSize - 4, avatarSize - 4, 64.0F, 64.0F);
      } else {
         String user = mc.getSession() != null && mc.getSession().getUsername() != null ? mc.getSession().getUsername() : "A";
         String initial = user.isEmpty() ? "A" : user.substring(0, 1).toUpperCase(Locale.ROOT);
         FontManager.interSemiBold.drawCenteredStringInBox(initial, (double)avatarX, (double)avatarY, (double)avatarSize, (double)avatarSize, accentColor);
      }

      // Live online status dot, anchored to the avatar's bottom-right corner.
      float dotX = avatarX + (float)avatarSize - 2.0F;
      float dotY = avatarY + (float)avatarSize - 2.0F;
      GlassRenderer.drawCircle(dotX, dotY, 4.3F, fadedArgb(0xFF090B10));
      GlassRenderer.drawCircle(dotX, dotY, 2.9F, fadedArgb(UiTheme.enabledColor()));

      // Shortcuts are anchored to the rail's TRAILING edge, not to a fixed x, so they glide inwards with
      // the fold instead of hanging outside the column mid-transition.
      float textAlpha = 1.0F - compact;
      float glyphCy = bandCentre - 2.0F;
      // The shared capsule comes first, because its box is what the target measures cleanly: x 166.1..223.0,
      // i.e. 57 x 32.5 with a 12.2 radius, 5.3 in from the rail's trailing edge. The two glyph centres
      // then sit inside it -- 15.3 in from its left edge and 26.7 apart -- which is where the reference
      // puts them (x 181.4 / 208.1).
      float pillW = FOOTER_SHORTCUT_PILL_W;
      float pillX = colW - FOOTER_SHORTCUT_CAPSULE_END - pillW;
      float guiCx = pillX + FOOTER_SHORTCUT_FIRST_DX;
      float colourCx = guiCx + FOOTER_SHORTCUT_STEP;
      // A folded rail cannot hold a text column beside the shortcuts, so they fade out with it (and stop
      // being hit tested, see mouseClicked): the rail's own ClickGUI / Client Color rows stay the way
      // to those two modules.
      boolean shortcutsFit = pillX >= FOOTER_INFO_X + 40.0F;
      float shownText = shortcutsFit ? textAlpha : 0.0F;
      if (shownText > 0.02F) {
         GlassRenderer.drawRoundedBox(
            pillX, glyphCy - FOOTER_SHORTCUT_PILL_H * 0.5F, pillW, FOOTER_SHORTCUT_PILL_H, FOOTER_SHORTCUT_PILL_RADIUS,
            fadedArgb(UiTheme.withAlpha(0xFFCFC8FF, Math.round(12.0F * shownText))),
            fadedArgb(UiTheme.withAlpha(0xFFE4E0FF, Math.round(18.0F * shownText))),
            UiTheme.BORDER_THICKNESS
         );
         boolean viewingGui = this.viewingModuleSettings && this.selectedModule == ClientManagers.moduleManager.clickGui;
         // The reference's two footer glyphs are a gear and an exit arrow, so that is what we draw: the
         // gear still opens ClickGUI's own settings (it just says "settings" now instead of repeating
         // the module's sliders glyph), and the exit closes the GUI -- the action an exit arrow
         // promises. Client Color keeps its rail row; the footer no longer duplicates it.
         this.drawFooterShortcut(guiCx, glyphCy, "footer_clickgui", LucideIcon.SETTINGS, viewingGui, "ClickGUI settings", accentColor, mutedText, mouseX, mouseY, shownText, true);
         this.drawFooterShortcut(colourCx, glyphCy, "footer_color", LucideIcon.LOGOUT, false, "Close ClickGUI", accentColor, mutedText, mouseX, mouseY, shownText, false);
      } else {
         // Not drawn this frame, so not clickable this frame either.
         this.footerShortcutGuiVisible = false;
         this.footerShortcutColorVisible = false;
      }

      if (shownText <= 0.02F) {
         return; // compact rail: the avatar is the whole footer
      }

      // Name and status: the name carries the hierarchy at Inter SemiBold, the status is one step down
      // the type scale in the muted register. Measured: name ink at y+19.5, status ink at y+39.8, and the
      // column starts 14 clear of the avatar's trailing edge.
      float infoX = FOOTER_INFO_X;
      float infoMaxW = Math.max(0.0F, pillX - 8.0F - infoX) * shownText;
      String username = mc.getSession() != null && mc.getSession().getUsername() != null ? mc.getSession().getUsername() : "Player";
      String safeUser = ModernSettingsPanel.trimToWidth(this.footerNameFont(), username, infoMaxW);
      float nameY = bandCentre - 15.0F;
      this.footerNameFont().drawString(
         safeUser, (double)infoX, (double)nameY, fadedArgb(scaleAlphaOf(UiTheme.TEXT_PRIMARY, Math.round(255.0F * shownText)))
      );
      // The status line carries the real client version, the same string the brand header shows, so the
      // two can never drift apart.
      String version = Augustus.getInstance() != null ? Augustus.getInstance().getVersion() : null;
      String status = ModernSettingsPanel.trimToWidth(
         this.footerStatusFont(), "Augustus" + (version == null || version.isEmpty() ? "" : " " + version), infoMaxW
      );
      this.footerStatusFont().drawString(
         status, (double)infoX, (double)(nameY + 20.5F), fadedArgb(scaleAlphaOf(mutedText, Math.round(255.0F * shownText)))
      );
   }

   /**
    * One footer shortcut: an icon-only control on the rail's own material.
    *
    * <p>The target draws these as bare glyphs inside one shared capsule -- no well per icon, no border,
    * no fill at rest -- so the chrome here is the hover veil and nothing else. They stay discoverable
    * through the veiling plus their tooltip, which is the same rule every other icon-only control in
    * the rail follows.
    */
   private void drawFooterShortcut(
      float cx,
      float cy,
      String key,
      LucideIcon icon,
      boolean active,
      String tooltip,
      int accentColor,
      int mutedText,
      int mouseX,
      int mouseY,
      float alpha,
      boolean isGuiShortcut
   ) {
      float size = FOOTER_SHORTCUT_BOX;
      float bx = cx - size * 0.5F;
      float by = cy - size * 0.5F;
      boolean hovered = RenderUtil.isHovered(bx, by, size, size, mouseX, mouseY)
         && !this.hoveringCategoryContextMenu && !this.hoveringModuleContextMenu;
      // Publish the box before anything else: the click path reads it, and it must match what is drawn.
      if (isGuiShortcut) {
         this.footerShortcutGuiX = bx;
         this.footerShortcutGuiY = by;
         this.footerShortcutGuiVisible = true;
      } else {
         this.footerShortcutColorX = bx;
         this.footerShortcutColorY = by;
         this.footerShortcutColorVisible = true;
      }
      if (hovered) {
         this.publishRailTooltip(key, tooltip);
      }
      float hovT = UiTheme.smoothStateAt(key + "_hov", hovered ? 1.0F : 0.0F, 16.0F);
      float pressT = UiTheme.smoothPress(key + "_prs", 0.0F, hovered && UiAnimation.isPrimaryMouseDown());

      float vis = UiAnimation.clamp01(alpha);
      if (hovT > 0.03F) {
         GlassRenderer.drawRoundedRect(
            bx, by, size, size, UiTheme.CONTROL_RADIUS,
            fadedArgb(UiTheme.withAlpha(accentColor, Math.round(20.0F * hovT * vis)))
         );
      }
      int glyphColor = UiTheme.lerpArgb(
         UiTheme.lerpArgb(mutedText, UiTheme.TEXT_PRIMARY, Math.max(hovT, active ? 1.0F : 0.0F)),
         UiTheme.accentSoftGlow(),
         active ? 0.55F : 0.0F
      );
      icon.drawCentered(cx, cy + pressT * 0.4F, FOOTER_SHORTCUT_GLYPH, fadedArgb(scaleAlphaOf(glyphColor, Math.round(235.0F * vis))));
   }

   private void drawHeader(int mouseX, int mouseY, int width, int accentColor, int textColor, int outlineColor, boolean windowBlur) {
      // ---- header ------------------------------------------------------------------------------
      // Measured against the reference: every header control is a rounded WELL on one band, y 12.8..48.4
      // (35.6 tall), with a 10.5 radius. The arrows share one well 78.6 wide, the search field is
      // 252.4 wide, and the window controls share a third. The title's caps centre sits at y 30.6.
      float wellY = HEADER_WELL_Y;
      float wellH = HEADER_WELL_H;
      float wellRadius = HEADER_WELL_RADIUS;
      float titleCy = wellY + wellH * 0.5F;

      int contentStartX = this.railColumnW() + Math.round(HEADER_LEADING_PADDING);
      boolean canBack = this.viewingModuleSettings;
      boolean canForward = !this.viewingModuleSettings && this.lastViewedModule != null;
      // The two arrows share ONE well 76.6 wide, and their centres are 38.6 apart: ~20.1 in from the
      // leading end. Hit boxes are half the well, so the whole well is live.
      float arrowWellW = 76.6F;
      float arrowWellX = (float)contentStartX;
      float arrowHitW = arrowWellW * 0.5F;
      float backX = arrowWellX;
      float fwdX = arrowWellX + arrowHitW;
      float backCx = arrowWellX + 20.1F;
      float fwdCx = backCx + 38.6F;
      boolean backHover = canBack && RenderUtil.isHovered(backX, wellY, arrowHitW, wellH, mouseX, mouseY);
      boolean fwdHover = canForward && RenderUtil.isHovered(fwdX, wellY, arrowHitW, wellH, mouseX, mouseY);
      float backHovT = UiTheme.smoothStateAt("hdr_back_hov", backHover ? 1.0F : 0.0F, 16.0F);
      float fwdHovT = UiTheme.smoothStateAt("hdr_fwd_hov", fwdHover ? 1.0F : 0.0F, 16.0F);
      float backPressT = UiTheme.smoothPress("hdr_back_prs", 0.0F, backHover && UiAnimation.isPrimaryMouseDown());
      float fwdPressT = UiTheme.smoothPress("hdr_fwd_prs", 0.0F, fwdHover && UiAnimation.isPrimaryMouseDown());

      // The well behind both arrows. The reference has no rim: it is the glass, one level darker, so
      // drawRecessedWell is exactly the right primitive.
      GlassRenderer.drawRecessedWell(
         arrowWellX, wellY, arrowWellW, wellH, ARROW_WELL_RADIUS, 0.0F, 0.0F, (float)mouseX, (float)mouseY, accentColor, 0.0F
      );

      if (backHovT > 0.02F) {
         GlassRenderer.drawRoundedRect(
            backX + 1.0F, wellY + 1.0F, arrowHitW - 2.0F, wellH - 2.0F, ARROW_WELL_RADIUS - 1.0F,
            fadedArgb(UiTheme.withAlpha(accentColor, Math.round(34.0F * backHovT)))
         );
      }
      if (fwdHovT > 0.02F) {
         GlassRenderer.drawRoundedRect(
            fwdX + 1.0F, wellY + 1.0F, arrowHitW - 2.0F, wellH - 2.0F, ARROW_WELL_RADIUS - 1.0F,
            fadedArgb(UiTheme.withAlpha(accentColor, Math.round(34.0F * fwdHovT)))
         );
      }

      // Arrow glyphs, measured: 12.8 x 10.4 of ink, in a muted lavender rather than white.
      int backColor = fadedArgb(canBack ? (backHover ? UiTheme.TEXT_PRIMARY : 0xFFA4AEDD) : UiTheme.disabledTextColor());
      int fwdColor = fadedArgb(canForward ? (fwdHover ? UiTheme.TEXT_PRIMARY : 0xFF9CA2D0) : UiTheme.disabledTextColor());
      GlassRenderer.drawVectorArrow(backCx, wellY + wellH * 0.5F + backPressT * 0.4F, 12.95F, false, backColor);
      GlassRenderer.drawVectorArrow(fwdCx, wellY + wellH * 0.5F + fwdPressT * 0.4F, 12.95F, true, fwdColor);

      // Two-tier breadcrumb & view title hierarchy
      //
      // Measured: the title's ink starts 17.6 after the arrow well's trailing edge and its caps centre
      // sits at content y 29.9 -- i.e. the title is vertically centred on the control band, not on a
      // separate title row.
      float titleX = arrowWellX + arrowWellW + 17.6F;
      // Measured: caps 14.55 tall, which is Inter SemiBold at 17.5 px -- the shared large face is 19.5.
      CustomFontUtil titleFont = this.headerTitleFont();
      float titleTop = titleCy - 13.0F;
      if (this.viewingModuleSettings && this.selectedModule != null) {
         float modMaxRight = (float)(width - 418);
         String cat = this.formatCategoryName(this.selectedModule.getCategorys());
         String sep = " / ";
         FontManager.interMedium.drawVerticallyCenteredString(cat, (double)titleX, (double)titleTop, 26.0, fadedArgb(UiTheme.TEXT_SECONDARY));
         float sepX = titleX + FontManager.interMedium.getStringWidth(cat);
         FontManager.interMedium.drawVerticallyCenteredString(sep, (double)sepX, (double)titleTop, 26.0, fadedArgb(UiTheme.mutedTextColor()));
         float modX = sepX + FontManager.interMedium.getStringWidth(sep);
         String mod = ModernSettingsPanel.trimToWidth(titleFont, this.selectedModule.getGuiTitle(), Math.max(60.0F, modMaxRight - modX));
         titleFont.drawVerticallyCenteredString(mod, (double)modX, (double)titleTop, 26.0, fadedArgb(UiTheme.TEXT_PRIMARY));
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
         titleFont.drawVerticallyCenteredString(headerTitle, (double)titleX, (double)titleTop, 26.0, fadedArgb(UiTheme.TEXT_PRIMARY));
      }

      // Window controls: the reference puts the three glyphs in their own recessed well that closes only
      // 10 off the panel's right edge -- NOT 35, which is where the content column stops. Measured:
      // well 1045.2..1163.1 (117.1 wide, 14.0 radius), glyph centres at 1067.4 / 1106.3 / 1143.6, i.e.
      // 38.6 apart with the close 4.5 in from the well's trailing end.
      int capsuleW = 117;
      int capsuleX = width - Math.round(HEADER_TRAILING_PADDING) - capsuleW;
      GlassRenderer.drawRecessedWell((float)capsuleX, wellY, (float)capsuleW, wellH, WINDOW_WELL_RADIUS, 0.0F, 0.0F, (float)mouseX, (float)mouseY, accentColor, 0.0F);

      int ctrlSize = 30;
      int btnY = Math.round(wellY) + 3;
      int closeX = capsuleX + capsuleW - 35;
      int maxX = closeX - 37;
      int minX = maxX - 39;

      boolean closeHover = RenderUtil.isHovered((float)closeX, (float)btnY, (float)ctrlSize, (float)ctrlSize, mouseX, mouseY);
      boolean maxHover = RenderUtil.isHovered((float)maxX, (float)btnY, (float)ctrlSize, (float)ctrlSize, mouseX, mouseY);
      boolean minHover = RenderUtil.isHovered((float)minX, (float)btnY, (float)ctrlSize, (float)ctrlSize, mouseX, mouseY);
      float closeHovT = UiTheme.smoothStateAt("win_close_hov", closeHover ? 1.0F : 0.0F, 16.0F);
      float maxHovT = UiTheme.smoothStateAt("win_max_hov", maxHover ? 1.0F : 0.0F, 16.0F);
      float minHovT = UiTheme.smoothStateAt("win_min_hov", minHover ? 1.0F : 0.0F, 16.0F);
      float closePress = UiTheme.smoothPress("win_close_prs", 0.0F, closeHover && UiAnimation.isPrimaryMouseDown());
      float maxPress = UiTheme.smoothPress("win_max_prs", 0.0F, maxHover && UiAnimation.isPrimaryMouseDown());
      float minPress = UiTheme.smoothPress("win_min_prs", 0.0F, minHover && UiAnimation.isPrimaryMouseDown());

      if (closeHovT > 0.02F) {
         GlassRenderer.drawSoftShadow((float)closeX, (float)btnY, (float)ctrlSize, (float)ctrlSize, 8.0F, 6.0F, 0.5F, fadedArgb(UiTheme.withAlpha(UiTheme.DANGER_ACCENT, Math.round(95.0F * closeHovT))));
         GlassRenderer.drawInteractiveSurface((float)closeX, (float)btnY, (float)ctrlSize, (float)ctrlSize, 8.0F, fadedArgb(UiTheme.withAlpha(UiTheme.DANGER_ACCENT, Math.round(215.0F * closeHovT))), fadedArgb(UiTheme.withAlpha(0xFF8C1D24, Math.round(215.0F * closeHovT))), fadedArgb(UiTheme.withAlpha(0xFFFFD0D2, Math.round(180.0F * closeHovT))), UiTheme.BORDER_THICKNESS, 0.16F * closeHovT, closeHovT, closePress, true, (float)mouseX, (float)mouseY, UiTheme.DANGER_ACCENT);
      }
      if (maxHovT > 0.02F) {
         GlassRenderer.drawInteractiveSurface((float)maxX, (float)btnY, (float)ctrlSize, (float)ctrlSize, 8.0F, UiTheme.withAlpha(accentColor, Math.round(90.0F * maxHovT)), fadedArgb(UiTheme.withAlpha(UiTheme.accentDeep(), Math.round(80.0F * maxHovT))), fadedArgb(UiTheme.withAlpha(UiTheme.accentSoftGlow(), Math.round(150.0F * maxHovT))), UiTheme.BORDER_THICKNESS, 0.14F * maxHovT, maxHovT, maxPress, true, (float)mouseX, (float)mouseY, accentColor);
      }
      if (minHovT > 0.02F) {
         GlassRenderer.drawInteractiveSurface((float)minX, (float)btnY, (float)ctrlSize, (float)ctrlSize, 8.0F, UiTheme.withAlpha(accentColor, Math.round(90.0F * minHovT)), fadedArgb(UiTheme.withAlpha(UiTheme.accentDeep(), Math.round(80.0F * minHovT))), fadedArgb(UiTheme.withAlpha(UiTheme.accentSoftGlow(), Math.round(150.0F * minHovT))), UiTheme.BORDER_THICKNESS, 0.14F * minHovT, minHovT, minPress, true, (float)mouseX, (float)mouseY, accentColor);
      }

      // Glyph sizes measured off the target: minimize bar 11.6 x 1.8, maximize square 11.9, close 9.4.
      GlassRenderer.drawVectorClose(
         (float)closeX + (float)ctrlSize * 0.5F, (float)btnY + (float)ctrlSize * 0.5F + closePress * 0.4F, 9.2F, fadedArgb(closeHover ? UiTheme.TEXT_PRIMARY : 0xFFD9E1FD)
      );
      GlassRenderer.drawVectorMaximize(
         (float)maxX + (float)ctrlSize * 0.5F, (float)btnY + (float)ctrlSize * 0.5F + maxPress * 0.4F, 11.5F, fadedArgb(maxHover ? UiTheme.TEXT_PRIMARY : 0xFFD6D6FD)
      );
      GlassRenderer.drawVectorMinimize(
         (float)minX + (float)ctrlSize * 0.5F, (float)btnY + (float)ctrlSize * 0.5F + minPress * 0.4F, 10.4F, fadedArgb(minHover ? UiTheme.TEXT_PRIMARY : 0xFFDAE0FF)
      );

      int actionsRight = capsuleX - 14;
      if (this.viewingModuleSettings && this.selectedModule != null) {
         this.viewModeBarVisible = false;
         this.drawModuleSettingsHeaderActions(mouseX, mouseY, actionsRight, accentColor, textColor, outlineColor);
      } else if (this.activeSidebarTab != SidebarTab.CONFIGS) {
         // The reference's header carries the search field and nothing else on the right. The view-mode
         // switcher moved out of this band to the trailing slot of the category row, which is where the
         // reference actually puts the one control of that kind (see drawCategoryPills).
         this.viewModeBarVisible = false;
         this.drawSearchBar(mouseX, mouseY, actionsRight, accentColor, textColor, outlineColor, windowBlur);
      } else {
         this.viewModeBarVisible = false;
      }
   }

   private void drawModuleSettingsHeaderActions(int mouseX, int mouseY, int rightEdge, int accentColor, int textColor, int outlineColor) {
      int btnY = 14;
      int btnH = 26;
      // Full capsule, the same corner rule as the Export/Import pills beside it: half the height, not the
      // fixed PILL_RADIUS, which read as a rounded rectangle next to two real pills.
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
      float actT = UiTheme.smoothStateAt("hdr_status_act", enabled ? 1.0F : 0.0F, 16.0F);
      float hovT = UiTheme.smoothStateAt("hdr_status_hov", toggleHover ? 1.0F : 0.0F, 16.0F);
      float prsT = UiTheme.smoothPress("hdr_status_prs", 0.0F, toggleHover && UiAnimation.isPrimaryMouseDown());

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
      // Measured off the target: the field is 252.4 wide on the header's control band (y 12.8, 35.6 tall)
      // with a 10.5 radius, and it ends where the window-controls well begins.
      int searchW = Math.min(250, Math.max(150, rightEdge - (this.railColumnW() + 300)));
      int searchX = rightEdge - searchW;
      float searchY = HEADER_WELL_Y;
      float searchH = HEADER_WELL_H;
      if (searchX < this.layoutOffsetX() + 210) {
         return -1;
      }

      this.searchInput.renderString(
         (float)searchX,
         searchY,
         (float)searchW,
         searchH,
         this.searchQuery,
         this.searchFocused,
         "Search modules...",
         LucideIcon.SEARCH,
         true,
         mouseX,
         mouseY,
         this.searchCursorTime,
         FontManager.interMedium,
         HEADER_WELL_RADIUS
      );
      return searchX;
   }

   /**
    * Retained but no longer called: the view-mode switcher moved to the trailing slot of the category
    * band (see {@link #drawCategoryPills}), which is where the reference puts its one control of that
    * kind. Kept because the geometry it publishes is what a header-mounted group would need if the
    * switcher ever moves back.
    */
   private void drawModuleViewModeGroup(int mouseX, int mouseY, int rightAnchorX, int accentColor) {
      // The reference's header holds exactly three controls on one band: the arrow well, the search
      // field and the window-controls well -- every one of them a 35.6-tall recessed well with a 10.5
      // radius and no ring. The view-mode switcher is our own addition (the reference has no such
      // control), so it is drawn in that same language rather than as a second, differently-rounded
      // tray: same band, same height, same radius, one well per mode.
      ModuleViewMode[] modes = ModuleViewMode.values();
      float btnH = HEADER_WELL_H;
      float btnY = HEADER_WELL_Y;
      float btnW = 35.6F;
      float btnGap = 6.0F;

      float rightEdge = (float)(rightAnchorX - 10);
      float groupW = modes.length * btnW + (modes.length - 1) * btnGap;
      if (rightEdge - groupW < (float)(this.layoutOffsetX() + 170)) {
         this.viewModeBarVisible = false;
         this.hoveredViewMode = null;
         return;
      }

      float curX = rightEdge - groupW;
      int activeIdx = this.moduleViewMode.ordinal();
      for (int i = 0; i < modes.length; i++) {
         this.viewModeBtnX[i] = curX;
         this.viewModeBtnW[i] = btnW;
         curX += btnW + btnGap;
      }

      this.viewModeTrayY = btnY;
      this.viewModeTrayH = btnH;
      this.viewModeBarVisible = true;

      boolean anyBtnHovered = false;
      for (int i = 0; i < modes.length; i++) {
         ModuleViewMode mode = modes[i];
         float bx = this.viewModeBtnX[i];
         boolean hovered = RenderUtil.isHovered(bx, btnY, btnW, btnH, mouseX, mouseY);
         float hovT = UiTheme.smoothStateAt("mod_view_hov_" + i, hovered ? 1.0F : 0.0F, 16.0F);
         float actT = UiTheme.smoothStateAt("mod_view_act_" + i, i == activeIdx ? 1.0F : 0.0F, 16.0F);
         float prsT = UiTheme.smoothPress("mod_view_prs_" + i, 0.0F, hovered && UiAnimation.isPrimaryMouseDown());

         GlassRenderer.drawRecessedWell(bx, btnY, btnW, btnH, HEADER_WELL_RADIUS, 0.0F, 0.0F, (float)mouseX, (float)mouseY, accentColor, 0.0F);
         if (actT > 0.03F) {
            // Selected mode: the same two-stop purple the active category chip uses, at chip-free
            // scale, so the two "selected" states read as one material.
            GlassRenderer.drawRoundedGradientBox(
               bx + 1.5F,
               btnY + 1.5F,
               btnW - 3.0F,
               btnH - 3.0F,
               HEADER_WELL_RADIUS - 1.5F,
               fadedArgb(scaleAlphaOf(UiTheme.lerpArgb(accentColor, 0xFF2C1064, 0.35F), Math.round(230.0F * actT))),
               fadedArgb(scaleAlphaOf(UiTheme.lerpArgb(UiTheme.accentDeep(), 0xFF1E0A46, 0.30F), Math.round(230.0F * actT))),
               0,
               0.0F,
               0.0F
            );
         } else if (hovT > 0.03F) {
            GlassRenderer.drawRoundedBox(
               bx + 1.5F,
               btnY + 1.5F,
               btnW - 3.0F,
               btnH - 3.0F,
               HEADER_WELL_RADIUS - 1.5F,
               fadedArgb(UiTheme.withAlpha(accentColor, Math.round(30.0F * hovT))),
               0,
               0.0F
            );
         }

         float yNudge = prsT * 0.4F;
         int fg = fadedArgb(UiTheme.lerpArgb(0xFFC7CFE6, 0xFFFFFFFF, Math.max(actT, hovT)));
         mode.icon.drawCentered(bx + btnW * 0.5F, btnY + btnH * 0.5F + yNudge, 15.0F, fg);

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
      int barLeft = this.layoutOffsetX();
      int barRight = width - CONTENT_TRAILING_PADDING;
      // The chip band stops 12 short of the trailing view-mode button, exactly as the reference's does:
      // its last chip ends 16.9 before that button. Without the reserve the scrolling row simply ran
      // under the button and its last chip was silently clipped in half.
      int viewBtnW = 52;
      int viewBtnX = barRight - viewBtnW;
      int chipsRight = viewBtnX - 12;
      // No rule under the chips: the target has exactly one hairline in the content column (the one under
      // the header, drawn by drawWindow) and a flat band between the chips and the first card. Adding a
      // second rule here is what made the band read as a separate bar.
      // The scissor exists to clip the chips that scroll out of the band horizontally, so it only has to be
      // tight on the left and right. It must NOT be tight vertically: the selected chip's accent glow is a
      // real gaussian halo with a pad of blurRadius * 2.2 + 2 (= 21.8 at the 9 used below), and a band that
      // stopped 2px above and 6px below the chip cut that halo into a rectangle - which is exactly how it
      // read on screen: a round chip sitting on a violet block with square corners. The band is opened by
      // the halo's own pad, so the glow reaches its own zero before the scissor can touch it.
      float glowPad = 9.0F * 2.2F + 2.0F;
      RenderUtil.scissorRectScaled(
         this.contentToScreenX((float)barLeft),
         this.contentToScreenY((float)(CATEGORY_BAR_Y) - glowPad),
         this.contentToScreenX((float)chipsRight),
         this.contentToScreenY((float)(CATEGORY_BAR_Y + CATEGORY_BAR_HEIGHT) + glowPad),
         1.0
      );

      int cursorX = barLeft + this.categoryScrollOffset;
      int y = CATEGORY_BAR_Y;
      // Measured: the target's chip band is 33.1-34.9 tall and its chips are full pills.
      int pillH = 34;
      // Measured: the gap between chips is 6.4.
      int pillGap = 6;

      // "All" pill
      int allW = (int)ModernTabs.chipLabelFont().getStringWidth("All") + 44;
      boolean allHover = RenderUtil.isHovered((float)cursorX, (float)y, (float)allW, (float)pillH, mouseX, mouseY) && !this.hoveringCategoryContextMenu;
      ModernTabs.drawPillTab((float)cursorX, (float)y, (float)allW, (float)pillH, "All", LucideIcon.LAYOUT_GRID, this.allCategoriesSelected, allHover, mouseX, mouseY);
      cursorX += allW + pillGap;

      int contentRight = allW + pillGap;
      for (CategoryTabButton tab : (Iterable<CategoryTabButton>)(this.categoryTabs)) {
         String name = this.formatCategoryName(tab.getCategorys());
         int pillW = (int)ModernTabs.chipLabelFont().getStringWidth(name) + 44;
         tab.left = cursorX;
         tab.top = y;
         tab.right = cursorX + pillW;
         tab.bottom = y + pillH;
         tab.width = pillW;
         tab.height = pillH;

         boolean selected = !this.allCategoriesSelected && tab == this.selectedCategory;
         boolean hovered = RenderUtil.isHovered((float)cursorX, (float)y, (float)pillW, (float)pillH, mouseX, mouseY) && !this.hoveringCategoryContextMenu;
         ModernTabs.drawPillTab((float)cursorX, (float)y, (float)pillW, (float)pillH, name, LucideIcon.forCategory(tab.getCategorys()), selected, hovered, mouseX, mouseY);
         cursorX += pillW + pillGap;
         contentRight += pillW + pillGap;
      }

      int visibleW = chipsRight - barLeft;
      this.categoryScrollOffset = this.categoryScrollBar.updateScrollOffset(
         contentRight, barLeft, chipsRight, this.categoryScrollOffset, barLeft, CATEGORY_BAR_Y, visibleW, pillH, mouseX, mouseY
      );

      // Trailing view-mode button, pinned at the right end of the chip band.
      //
      // The reference has no view switcher in the header -- that band holds exactly three controls --
      // but it does carry one control out here, at the end of the category row: a 52 x 34.9 well with a
      // ~11 radius and a sliders glyph. That is where our view-mode switcher lives now, drawn to the
      // reference's measurements and with the reference's own glyph, cycling the mode on click (the
      // icon is fixed so the control matches the reference at rest; the tooltip names the current mode).
      this.clipToShell();
      float viewBtnY = (float)y;
      float viewBtnH = (float)pillH;
      boolean viewBtnHover = RenderUtil.isHovered((float)viewBtnX, viewBtnY, (float)viewBtnW, viewBtnH, mouseX, mouseY) && !this.hoveringCategoryContextMenu;
      this.viewModeButtonX = (float)viewBtnX;
      this.viewModeButtonY = viewBtnY;
      this.viewModeButtonW = (float)viewBtnW;
      this.viewModeButtonH = viewBtnH;
      this.viewModeButtonVisible = true;
      float viewBtnHovT = UiTheme.smoothStateAt("cat_view_hov", viewBtnHover ? 1.0F : 0.0F, 16.0F);
      float viewBtnPrsT = UiTheme.smoothPress("cat_view_prs", 0.0F, viewBtnHover && UiAnimation.isPrimaryMouseDown());
      GlassRenderer.drawRecessedWell(
         (float)viewBtnX, viewBtnY, (float)viewBtnW, viewBtnH, 11.0F, viewBtnHovT, 0.0F, (float)mouseX, (float)mouseY, accentColor, 0.0F
      );
      LucideIcon.SLIDERS_HORIZONTAL.drawCentered(
         (float)viewBtnX + (float)viewBtnW * 0.5F,
         viewBtnY + viewBtnH * 0.5F + viewBtnPrsT * 0.4F,
         16.0F,
         fadedArgb(UiTheme.lerpArgb(0xFFB6BEDB, 0xFFFFFFFF, viewBtnHovT))
      );
      if (viewBtnHover) {
         this.publishRailTooltip("cat_view_mode", "View: " + this.moduleViewMode.name().toLowerCase(java.util.Locale.ROOT));
      }
   }

   private void drawModuleGrid(int mouseX, int mouseY, int width, int height, int accentColor, int textColor, int outlineColor, boolean windowBlur) {
      int areaLeft = this.layoutOffsetX();
      int areaRight = width - CONTENT_TRAILING_PADDING;
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
            int rowH = compact ? 34 : 61;
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

   /**
    * Category badge ("Legit", "Movement", "Combat"...) for a module row or card, in ONE shape and ONE
    * material for all three view modes.
    *
    * <p>WHY it can no longer be a recessed well: {@code UiTheme.glassRecessedFillArgb} resolves to the
    * theme's near-black well colour, so in Compact and Grid the chip read as a black pill while List showed
    * a client-coloured one - three views, two different objects, and the black one looked like a bug. A
    * badge is a chip, so it is painted like the category band's own pills: a translucent accent-tinted
    * glass pane (the row's material still reads through it) with the accent in the rim and the label.</p>
    */
   private void drawCategoryBadge(float x, float y, float width, float height, String label) {
      // Built from the RAW accent and faded once at the draw: handing an already-faded accent in here
      // would be lerped against opaque stops and claw the fade back.
      int accent = UiTheme.accentColor();
      float radius = height * 0.5F;
      int topFill = UiTheme.withAlpha(UiTheme.lerpArgb(accent, 0xFF2C1064, 0.52F), 130);
      int bottomFill = UiTheme.withAlpha(UiTheme.lerpArgb(accent, 0xFF1E0A46, 0.66F), 146);
      GlassRenderer.drawRoundedGradientBox(
         x,
         y,
         width,
         height,
         radius,
         radius,
         radius,
         radius,
         fadedArgb(topFill),
         fadedArgb(bottomFill),
         fadedArgb(UiTheme.withAlpha(UiTheme.accentSoftGlow(), 118)),
         0.9F,
         0.10F,
         0.0F,
         -9999.0F,
         -9999.0F,
         0.0F,
         0.0F,
         0
      );
      FontManager.interSmall.drawCenteredStringInBox(label, (double)x, (double)y, (double)width, (double)height, fadedArgb(0xFFFFFFFF));
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

      // The row card follows the theme's card radius, so it stays in the same corner family as the cards,
      // fields and buttons inside the settings page it opens.
      float radius = compact ? UiTheme.CONTROL_RADIUS : UiTheme.CARD_RADIUS;
      this.drawModuleSurface((float)rowX, (float)rowY, (float)rowW, (float)rowH, radius, hoverT, enableT, pressT, rowHover, listeningBind, mouseX, mouseY, accentColor, outlineColor, windowBlur);

      // Left active accent indicator bar
      if (enableT > 0.04F) {
         float barH = ((float)rowH - (compact ? 12.0F : 18.0F)) * enableT;
         float barY = (float)rowY + ((float)rowH - barH) * 0.5F;
         GlassRenderer.drawRoundedRect((float)rowX + 3.0F, barY, 3.0F, barH, 1.5F, fadedArgb(UiTheme.withAlpha(UiTheme.accentSoftGlow(), Math.round(235.0F * enableT))));
      }

      // Right controls cluster: [Keybind pill] [Settings gear button] [Toggle switch]
      //
      // Every number here is measured off the target's own module rows: the toggle track is a 42.4 x 23.8
      // pill sitting 14.0 in from the row's trailing edge, the gear is a 16.8 glyph whose centre is 91.3
      // in from that edge (22.5 clear of the toggle), and the row's text column starts 13.5 in.
      float rightPad = compact ? 11.0F : 14.0F;
      float switchW = compact ? 32.0F : 42.4F;
      float switchH = compact ? 16.0F : 23.8F;
      float switchX = (float)(rowX + rowW) - rightPad - switchW;
      float switchY = (float)rowY + ((float)rowH - switchH) * 0.5F;
      this.getModuleRowSwitch(module).render(switchX, switchY, switchW, switchH, enabled, mouseX, mouseY);

      // The gear is hit tested over a 24px square even though its glyph is 16.8: the target puts no
      // button behind it, so the visible control is the glyph and the hit box is the reasonable target.
      float gearSize = compact ? 22.0F : 24.0F;
      float gearX = switchX - (compact ? 6.0F : 14.0F) - gearSize;
      float gearY = (float)rowY + ((float)rowH - gearSize) * 0.5F;
      boolean gearHover = rowHover && RenderUtil.isHovered(gearX, gearY, gearSize, gearSize, mouseX, mouseY);
      float gearHovT = UiTheme.smoothStateAt("row_gear_" + module.getName(), gearHover ? 1.0F : 0.0F, 16.0F);
      // Hover only, and never a resting badge: the gear is a bare glyph whose plate appears under the
      // pointer and nowhere else. Its corners follow the card radius so it belongs to the row it opens.
      if (gearHovT > 0.03F) {
         int gFill = UiTheme.glassControlFillArgb(gearHovT, 0.0F, accentColor);
         GlassRenderer.drawInteractiveSurface(
            gearX, gearY, gearSize, gearSize, UiTheme.CARD_RADIUS,
            fadedArgb(UiTheme.scaleAlpha(gFill, gearHovT)),
            fadedArgb(UiTheme.scaleAlpha(UiTheme.lerpArgb(gFill, 0xFF0A0A14, 0.25F), gearHovT)),
            fadedArgb(UiTheme.withAlpha(UiTheme.accentSoftGlow(), Math.round(180.0F * gearHovT))),
            1.0F, 0.14F * gearHovT, gearHovT, 0.0F, true, (float)mouseX, (float)mouseY, accentColor
         );
      }
      int gearCol = fadedArgb(UiTheme.lerpArgb(UiTheme.mutedTextColor(), UiTheme.accentSoftGlow(), Math.max(gearHovT, hoverT * 0.35F)));
      LucideIcon.SETTINGS.drawCentered(gearX + gearSize * 0.5F, gearY + gearSize * 0.5F, compact ? 12.5F : 16.8F, gearCol);

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
         GlassRenderer.drawRoundedBox(badgeX, badgeY, badgeW, badgeH, badgeH * 0.5F, badgeFill, badgeBorder, 1.1F);
         FontManager.interSmall.drawCenteredStringInBox(keyLabel, (double)badgeX, (double)badgeY, (double)badgeW, (double)badgeH, fadedArgb(listeningBind ? 0xFFFDE68A : (bindHover ? UiTheme.TEXT_PRIMARY : UiTheme.secondaryTextColor())));
         clusterLeft = badgeX;
      }

      // Left icon & text block
      float textLeft;
      if (compact) {
         // No icon in compact mode — text starts directly
         textLeft = (float)rowX + 14.0F;
      } else {
         // No icon in list mode — text starts at the target's own 13.5px inner padding, which puts the
         // module name's ink on content x 264 in the reference.
         textLeft = (float)rowX + 13.5F;
      }

      float textMaxW = Math.max(60.0F, clusterLeft - 14.0F - textLeft);
      int nameColor = fadedArgb(UiTheme.lerpArgb(UiTheme.secondaryTextColor(), UiTheme.TEXT_PRIMARY, Math.max(enableT, hoverT)));
      String catLabel = this.formatCategoryName(module.getCategorys());
      float catPillW = FontManager.interSmall.getStringWidth(catLabel) + 12.0F;

      if (compact) {
         float maxTitleW = Math.max(50.0F, textMaxW - catPillW - 10.0F);
         String safeTitle = ModernSettingsPanel.trimToWidth(FontManager.interSemiBold, module.getGuiTitle(), maxTitleW);
         FontManager.interSemiBold.drawVerticallyCenteredString(safeTitle, (double)textLeft, (double)rowY + pressT * 0.3, (double)rowH, nameColor);

         float pillX = textLeft + FontManager.interSemiBold.getStringWidth(safeTitle) + 8.0F;
         if (pillX + catPillW <= textLeft + textMaxW) {
            float pillH = 16.0F;
            float pillY = (float)rowY + ((float)rowH - pillH) * 0.5F;
            // A capsule, not a rounded rectangle: the category badge is a label-sized object, and half its
            // own height as its radius is what makes it read as a pill instead of as a small box. Same rule
            // for every size of it (list, compact and grid card) so the three never disagree - and the same
            // glass material, which is what the shared helper guarantees.
            this.drawCategoryBadge(pillX, pillY, catPillW, pillH, catLabel);
         }
      } else {
         // Measured off the target: the name's cap centre sits 23.1 below the card's top edge (card top
         // 119.2, name centre 142.2), so with a 61px card the title's top lands here.
         float titleY = (float)rowY + 14.0F + pressT * 0.3F;
         float maxTitleW = Math.max(60.0F, textMaxW - catPillW - 10.0F);
          CustomFontUtil nameFace = this.cardTitleFont();
          String safeTitle = ModernSettingsPanel.trimToWidth(nameFace, module.getGuiTitle(), maxTitleW);
          nameFace.drawString(safeTitle, (double)textLeft, (double)titleY, nameColor);

         // Category chip, measured off the target: 20.4 tall, a 5.5 radius (a rounded rectangle, not a
         // pill), centred on the name's own line and 17.3 clear of the title's ink.
         float pillH = 20.4F;
          float pillX = textLeft + nameFace.getStringWidth(safeTitle) + 17.3F;
          float pillY = titleY + (nameFace.getFontHeight() - pillH) * 0.5F;
         if (pillX + catPillW <= textLeft + textMaxW) {
            this.drawCategoryBadge(pillX, pillY, catPillW, pillH, catLabel);
         }

         String rawDesc = module.getDescription() != null ? module.getDescription().trim() : "";
         String sub = rawDesc.isEmpty() ? catLabel + " module — Click to toggle" : rawDesc;
         CustomFontUtil descFace = this.cardDescFont();
          String safeSub = ModernSettingsPanel.trimToWidth(descFace, sub, textMaxW);
         descFace.drawString(safeSub, (double)textLeft, (double)(titleY + nameFace.getFontHeight() + 6.0F), fadedArgb(UiTheme.mutedTextColor()));
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
      float gearHovT = UiTheme.smoothStateAt("grid_gear_" + module.getName(), gearHover ? 1.0F : 0.0F, 16.0F);
      // The plate is the GEAR's own hover and nothing else. It used to be gated on
      // max(cardHover, gearHover), so simply moving the pointer onto a card - which is how the card is
      // opened - painted a badge over the card's top corner whether or not the gear was the target.
      // The glyph itself stays visible at rest; only the plate fades in.
      if (gearHovT > 0.03F) {
         int gFill = UiTheme.scaleAlpha(UiTheme.glassControlFillArgb(gearHovT, 0.0F, accentColor), gearHovT);
         int gBot = UiTheme.scaleAlpha(UiTheme.lerpArgb(gFill, 0xFF0A0C12, 0.28F), gearHovT);
         int gBorder = fadedArgb(UiTheme.lerpArgb(UiTheme.withAlpha(UiTheme.GLASS_BORDER, Math.round(145.0F * gearHovT)), UiTheme.withAlpha(UiTheme.accentSoftGlow(), 215), gearHovT));
         GlassRenderer.drawInteractiveSurface(
            (float)gearX,
            (float)gearY,
            gearSize,
            gearSize,
            UiTheme.CARD_RADIUS,
            fadedArgb(gFill),
            fadedArgb(gBot),
            gBorder,
            UiTheme.BORDER_THICKNESS,
            0.12F * gearHovT,
            gearHovT,
            0.0F,
            true,
            (float)mouseX,
            (float)mouseY,
            accentColor
         );
      }
      int gearCol = fadedArgb(UiTheme.lerpArgb(UiTheme.lerpArgb(UiTheme.mutedTextColor(), UiTheme.secondaryTextColor(), hoverT), UiTheme.accentSoftGlow(), gearHovT));
      LucideIcon.SETTINGS.drawCentered((float)gearX + gearSize * 0.5F, (float)gearY + gearSize * 0.5F, 13.0F, gearCol);

      // Header: Module Title (SemiBold)
      float titleMaxW = Math.max(60.0F, (float)gearX - textLeft - 8.0F);
      String safeName = ModernSettingsPanel.trimToWidth(FontManager.interSemiBold, module.getGuiTitle(), titleMaxW);
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
      // Same chip material as the list and compact rows: a grid card is the same object with a
      // different shell, so its category badge may not be the only black one.
      this.drawCategoryBadge(cursorX, footerY, catW, footerH, catLabel);
      cursorX += catW + 6.0F;

      if (this.shouldShowRowKeybind(module, listeningBind)) {
         String keyLabel = listeningBind ? "..." : KeyboardInputUtil.getKeyDisplayName(module.getKeyCode());
         float badgeW = Math.max(24.0F, FontManager.interSmall.getStringWidth(keyLabel) + 10.0F);
         if (cursorX + badgeW <= (float)(cardX + cardW) - 70.0F) {
            int badgeBorder = fadedArgb(listeningBind ? 0xFFF59E0B : UiTheme.GLASS_BORDER);
            int badgeFill = fadedArgb(listeningBind ? UiTheme.withAlpha(0xF59E0B, 55) : UiTheme.glassRecessedFillArgb(hoverT * 0.4F, 0.0F, accentColor));
            GlassRenderer.drawRoundedBox(cursorX, footerY, badgeW, footerH, footerH * 0.5F, badgeFill, badgeBorder, UiTheme.BORDER_THICKNESS);
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
      int panelX = this.layoutOffsetX();
      int panelY = SETTINGS_OFFSET_Y;
      int panelW = width - this.layoutOffsetX() - 16;
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

      int areaLeft = this.layoutOffsetX();
      int areaRight = width - CONTENT_TRAILING_PADDING;
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
            float selT = UiTheme.smoothStateAt("cfg_sel_" + item.getConfigName(), selected ? 1.0F : 0.0F, 16.0F);
            float hovT = UiTheme.smoothStateAt("cfg_hov_" + item.getConfigName(), hovered ? 1.0F : 0.0F, 16.0F);
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
            String name = (module.getModuleName() + " " + module.getGuiTitle()).toLowerCase(Locale.ROOT);
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

      // Header window controls: the same well drawHeader paints (117 wide, 30px squares 38 apart), so a
      // click can never land on a glyph the pointer is not visibly over.
      int capsuleW = 117;
      int capsuleX = width - CONTENT_TRAILING_PADDING - capsuleW;
      int ctrlSize = 30;
      int btnY = 15;
      int closeX = capsuleX + capsuleW - 12 - ctrlSize;
      int maxX = closeX - 38;
      int minX = maxX - 38;
      if (mouseButton == 0 && RenderUtil.isHovered((float)closeX, (float)btnY, (float)ctrlSize, (float)ctrlSize, mouseX, mouseY)) {
         this.closeWindow();
         this.closed = true;
         this.setFullScreen(false);
         ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE);
         return;
      }
      if (mouseButton == 0 && RenderUtil.isHovered((float)maxX, (float)btnY, (float)ctrlSize, (float)ctrlSize, mouseX, mouseY)) {
         this.toggleFullScreenMode();
         ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE);
         return;
      }
      if (mouseButton == 0 && RenderUtil.isHovered((float)minX, (float)btnY, (float)ctrlSize, (float)ctrlSize, mouseX, mouseY)) {
         this.closeWindow();
         ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE);
         return;
      }

      // Back / Forward header arrows: one shared 78.6 well, each arrow owning half of it.
      float arrowWellX = (float)this.layoutOffsetX();
      float arrowHalfW = 78.6F * 0.5F;
      float backX = arrowWellX;
      float fwdX = arrowWellX + arrowHalfW;
      if (mouseButton == 0 && RenderUtil.isHovered(backX, HEADER_WELL_Y, arrowHalfW, HEADER_WELL_H, mouseX, mouseY) && this.viewingModuleSettings) {
         this.popupHost.close();
         this.viewingModuleSettings = false;
         this.contextMenuSetting = null;
         this.triggerContentTransition(-1.0F);
         return;
      }
      if (mouseButton == 0 && RenderUtil.isHovered(fwdX, HEADER_WELL_Y, arrowHalfW, HEADER_WELL_H, mouseX, mouseY) && !this.viewingModuleSettings && this.lastViewedModule != null) {
         this.openModuleSettings(this.lastViewedModule);
         return;
      }

      // View-mode trailing button click (the reference's own control, at the end of the category band)
      if (mouseButton == 0 && this.viewModeButtonVisible && !this.viewingModuleSettings && this.activeSidebarTab != SidebarTab.CONFIGS
         && RenderUtil.isHovered(this.viewModeButtonX, this.viewModeButtonY, this.viewModeButtonW, this.viewModeButtonH, mouseX, mouseY)) {
         ModuleViewMode[] modes = ModuleViewMode.values();
         ModuleViewMode next = modes[(this.moduleViewMode.ordinal() + 1) % modes.length];
         float dir = next.ordinal() > this.moduleViewMode.ordinal() ? 1.0F : -1.0F;
         this.moduleViewMode = next;
         this.moduleScrollOffset = 0;
         this.moduleScrollBar.resetPendingScroll();
         this.triggerContentTransition(dir);
         ClickGuiListener.postClickGuiRender();
         return;
      }
      // Search box click. The search bar is drawn relative to HEADER_TRAILING_PADDING (not the wider
      // CONTENT_TRAILING_PADDING), so the hit-test must use the same geometry or the clear button
      // and trailing region are unreachable.
      int headerCapsuleX = width - Math.round(HEADER_TRAILING_PADDING) - 117;
      int actionsRight = headerCapsuleX - 14;
      int searchW = Math.min(250, Math.max(150, actionsRight - (this.railColumnW() + 300)));
      int searchX = actionsRight - searchW;
      if (!this.viewingModuleSettings && this.activeSidebarTab != SidebarTab.CONFIGS && searchX >= this.layoutOffsetX() + 210
         && RenderUtil.isHovered((float)searchX, HEADER_WELL_Y, (float)searchW, HEADER_WELL_H, mouseX, mouseY)) {
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
      if (mouseButton == 0 && mouseX < this.railColumnW()) {
         // Group headers: collapsing/expanding is the only thing they do, and they are hit tested from
         // the geometry the rail published, so the chevron the pointer aims at is the chevron it hits.
         String group = this.railGroupHeaderAt(mouseX, mouseY, height);
         if (group != null) {
            this.toggleRailGroup(group);
            ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE);
            return;
         }

         // The two footer shortcuts are hidden while the rail is folded (they cannot fit beside the text
         // column), so they must not answer clicks there either -- a control that is not drawn must not
         // be clickable.
         if (this.footerShortcutGuiVisible && this.isHovered(
            this.footerShortcutGuiX, this.footerShortcutGuiY, FOOTER_SHORTCUT_BOX, FOOTER_SHORTCUT_BOX, mouseX, mouseY
         )) {
            this.openModuleSettings(ClientManagers.moduleManager.clickGui);
            return;
         }
         if (this.footerShortcutColorVisible && this.isHovered(
            this.footerShortcutColorX, this.footerShortcutColorY, FOOTER_SHORTCUT_BOX, FOOTER_SHORTCUT_BOX, mouseX, mouseY
         )) {
            // Exit: the glyph promises "leave", so it leaves through the same animated close the
            // window-controls button uses.
            ClickGuiScreen.requestCloseFromWindowChrome();
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
         int panelX = this.layoutOffsetX();
         int panelY = SETTINGS_OFFSET_Y;
         int panelW = width - this.layoutOffsetX() - 16;
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
      int barLeft = this.layoutOffsetX();
      int barRight = width - CONTENT_TRAILING_PADDING;
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
      int areaLeft = this.layoutOffsetX();
      int areaRight = width - CONTENT_TRAILING_PADDING;
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
            int rowH = compact ? 34 : 61;
            int gap = compact ? 5 : 8;
            float rightPad = compact ? 11.0F : 14.0F;
            // Same measured controls the draw path uses (42.4 x 23.8 toggle, 24px gear square), so a click
            // can never land on a control the pointer is not visibly over.
            float switchW = compact ? 32.0F : 42.4F;
            float gearSize = compact ? 22.0F : 24.0F;

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
                  float gearX = switchX - (compact ? 6.0F : 14.0F) - gearSize;
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
      int areaLeft = this.layoutOffsetX();
      int areaRight = width - CONTENT_TRAILING_PADDING;
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
         this.settingsPanel.mouseClickMove(localX - this.layoutOffsetX(), localY - SETTINGS_OFFSET_Y, clickedMouseButton, timeSinceLastClick);
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
         this.settingsPanel.mouseReleased(localX - this.layoutOffsetX(), localY - SETTINGS_OFFSET_Y, state);
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
      float hovT = UiTheme.smoothStateAt("ctx_hov_" + text, hovered ? 1.0F : 0.0F, 16.0F);
      float prsT = UiTheme.smoothPress("ctx_prs_" + text, 0.0F, pressed);
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
            UiTheme.PILL_RADIUS,
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
