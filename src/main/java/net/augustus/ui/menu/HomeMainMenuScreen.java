package net.augustus.ui.menu;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.augustus.Augustus;

import net.augustus.font.FontManager;
import net.augustus.ui.modern.font.InterFontUtil;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.MaskedTexture;
import net.augustus.ui.modern.render.MenuGlassSurface;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.utils.MinotarAvatarCache;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.IImageBuffer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.SaveFormatComparator;
import org.lwjgl.opengl.GL11;

/**
 * The redesigned Augustus home screen, laid out to the supplied reference mockup.
 *
 * <h2>The composition</h2>
 * The reference is a 16:9 still (1000x562) of an open landscape with three thin columns of translucent
 * pills standing on it and nothing else: no glass panels, no plate, no rail. Copying it faithfully is
 * therefore mostly a question of <i>restraint</i> - the previous revision framed the same actions in
 * three large glass cards, and most constants here exist to undo exactly that.
 *
 * <p>Two deliberate departures from the mockup. Its backdrop is a blurred warm landscape; this screen
 * plays the clip the redesign shipped with instead (Earth over a lunar horizon), kept <em>sharp</em>,
 * because a blurred frame reads as a rendering bug rather than as art direction. And its right column
 * offers actions that mean nothing in this client (Host World, Social, Wardrobe), so that column carries
 * the client's own pages plus one stock Minecraft one instead.
 *
 * <pre>
 *   left   column x=26  w=117 : Quickplay, then up to four saved servers, each with its favicon
 *   centre column  centred    : brand mark over the wordmark, then four actions in a 2 + 2 grid
 *   right  column x=858 w=116 : the player figure, the account row, then Proxies / Resource Packs / Changelog
 *   top-right strip           : five 18x18 controls, 8 px from the corner
 *   footer                    : brand + version bottom-left, the authors bottom-right
 * </pre>
 *
 * <p>Every pill is a near-black veil of the ClickGUI's own background over the blurred backdrop, ringed with
 * one accent hairline and lit by the ClickGUI's hover spotlight - see {@link #drawPillBox}. The accent is
 * spent on the ring and the glyph and nowhere else, so a column of options reads as the overlay's palette
 * rather than as a wash of the accent.
 *
 * <h2>Design space, and why the side columns are edge-anchored</h2>
 * Every number in this file is a <b>reference pixel</b>: the layout was measured off the mockup, so the
 * integers in the measurement sheet appear here unchanged. One {@link #scale} - window height over
 * {@link #DESIGN_H} - converts them, which keeps the vertical rhythm identical at any GUI scale.
 * Horizontally the two side columns are pinned to the real left and right edges rather than to a
 * letterboxed 1000 px band, because the mockup's own margins are edge margins: on a window that is not
 * exactly 16:9 this keeps the columns where the eye expects them and lets the centre stay centred.
 *
 * <h2>No behaviour is replaced</h2>
 * Every action opens a stock screen: singleplayer, multiplayer, the client's module list, the vanilla
 * options screen, world creation, the client tab pages, and {@code shutdown()}. The conveniences reuse
 * existing paths instead of inventing new ones - a saved server joins through {@link GuiConnecting}, and
 * Quickplay resumes the most recently played world through {@code launchIntegratedServer}.
 */
public final class HomeMainMenuScreen extends AbstractMainMenuScreen {
   // -------------------------------------------------------------------------------------------
   // Design space: the reference mockup's own pixel grid (1000x562)
   // -------------------------------------------------------------------------------------------

   private static final float DESIGN_W = 1000.0F;
   private static final float DESIGN_H = 562.0F;

   /** Distance from the screen edge to the side columns. */
   private static final float MARGIN = 26.0F;
   /** Distance from the top-right corner to the window control strip. */
   private static final float STRIP_MARGIN = 8.0F;
   /** The one corner radius every pill in the reference shares. */
   private static final float RADIUS = 4.5F;

   private static final float LEFT_W = 117.0F;
   private static final float RIGHT_W = 116.0F;
   private static final float CENTER_W = 171.0F;
   private static final float CENTER_SPLIT = 7.0F;

   /** Height of a one-line pill (Quickplay, every centre action, the account row). */
   private static final float PILL_H = 18.0F;

   private static final float QUICK_Y = 194.0F;
   private static final float ROW_TOP = 217.0F;
   private static final float ROW_H = 24.0F;
   private static final float ROW_PITCH = 29.5F;

   /** Vertical pitch of the stacked action pills, measured between their top edges. */
   private static final float PITCH = 23.0F;

   private static final float MARK_TOP = 226.0F;
   private static final float MARK_H = 28.0F;
   private static final float WORD_TOP = 266.0F;

   private static final float ACTION_TOP = 305.0F;
   private static final float ACCOUNT_TOP = 281.0F;
   private static final float SIDE_TOP = 304.0F;

   private static final float BUST_TOP = 199.0F;
   private static final float BUST_H = 66.0F;
   /** The mockup's bust is wider than it is tall (73x66), not a square render. */
   private static final float BUST_ASPECT = 1.10F;

   /**
    * On-screen height of the 3D player figure, and the height its soles rest on.
    *
    * <p>The bust only draws from the head to the waist ({@link MenuPlayerFigure#bustBottomY}), so
    * {@code FIGURE_FEET} no longer marks anything visible - it is the reference the bust's geometry is
    * measured from. What matters on screen is that the waist lands where the user asked it to: "el torzo mas
    * pegado a las opciones".
    *
    * <p>Both numbers are measured off the mockup rather than guessed. Its shirt spans 74 px across and its
    * head 49, which puts its figure at {@code height ~= 118} - this screen was drawing 104, a seventh too
    * small. Its waist then falls {@code 0.375 * height = 44} above the sole line, and the soles are placed
    * so that line lands on the account row's own top edge ({@link #ACCOUNT_TOP}, 281): 281 + 44 = 325. The
    * dissolving tail of the torso therefore reaches the first option instead of stopping a head's width
    * short of it, which is the difference the user was looking at.
    */
   private static final float FIGURE_H = 118.0F;
   private static final float FIGURE_FEET = 325.0F;

   private static final float FOOTER_TOP = 539.0F;

   private static final float STRIP_SIZE = 18.0F;
   private static final float STRIP_PITCH = 21.5F;
   private static final int STRIP_COUNT = 5;

   // Type scale, in reference pixels. The mockup sets its labels at a ~5.5 px cap height, which is
   // 7.8 px of Inter; the wordmark's cap height measures 12 px (~15.5 px of Inter Bold).
   private static final float FS_PILL = 7.5F;
   private static final float FS_SERVER = 7.1F;
   private static final float FS_SUB = 6.4F;
   private static final float FS_WORD = 15.5F;
   private static final float FS_FOOT = 7.0F;
   private static final float WORD_TRACK = 1.5F;
   private static final float LABEL_TRACK = 0.35F;

   /** Icon edge inside a one-line pill, the gap between glyph and label, and the strip's own glyph. */
   private static final float ICON = 7.0F;
   private static final float ICON_GAP = 4.0F;
   private static final float ICON_STRIP = 8.2F;

   // Palette. A pill's body, ring and hover light are all derived in drawPillBox from the ClickGUI's own
   // background colour and the user's accent, so what is left here is the type: the neutral ramp every
   // label, sub-label and footer line is set in, plus the dim chevron on a dropdown. The icons are not in
   // this list because they are not neutral - they carry the accent, which is the one decoration a pill is
   // allowed this round.
   private static final int TEXT_HOT = 0xFFFFFFFF;
   private static final int TEXT_BASE = 0xE6F1F5F9;
   private static final int TEXT_SUB = 0xB3B4BED0;
   private static final int TEXT_STRIP_ICON = 0xF2F2F4FA;
   private static final int TEXT_CHEVRON = 0x99B4BED0;
   private static final int TEXT_FOOT = 0xF2FFFFFF;
   private static final int TEXT_FOOT_DIM = 0x8CD6DEE8;
   private static final MaskedTexture BRAND_MARK = MaskedTexture.of("/assets/augustus/textures/menu/logo_mark.png");

   /**
    * Opacity of the frost layer inside a pill: the backdrop's own pre-baked blur, painted at full strength
    * under the glass. The veil on top is what makes it read as frosted glass rather than as a hole, so this
    * stays at 1 - lowering it would start showing the sharp frame through the blur instead.
    */
   /**
    * How far inside the pill the frosted backdrop is pulled, in device pixels.
    *
    * <p>Sub-pixel by intent, invisible in the middle of a pill and decisive at its edge: see
    * {@link #drawFrostPill} for the measurement that put it here.</p>
    */
   private static final float FROST_EDGE_INSET = 1.25F;

   private static final float FROST_STRENGTH = 1.0F;
   /**
    * Number of rows each corner's frost staircase uses. Six leaves at most {@code 0.12 * radius} of the arc
    * uncovered - under half a reference pixel at the 4.5 px corner radius every pill shares - which is the
    * point at which the notch the plain three-rectangle decomposition left stopped being visible.
    */
   private static final int FROST_ARC_STEPS = 12;

   /**
    * The dark half of a pill: the ClickGUI's own near-black glass, carrying a trace of the accent.
    *
    * <p>WHY not the glass veil token itself. {@link MenuGlassSurface#chip} builds a pill out of
    * {@code UiTheme.glassControlVeilArgb}, whose fill carries the accent at {@code glassTintMix()} - roughly
    * a tenth of the fill at the default Glass Opacity. On the ClickGUI that is right, because the panel
    * behind it is already {@code #0E101A}: the accent reads as a tint on black. Here there is no panel
    * behind it, only the blurred backdrop, so the same veil left the pills a bright saturated violet -
    * measurably {@code (73,76,138)} against the mockup's {@code (36,24,43)}, i.e. the accent's hue at
    * nowhere near the accent's value. The user's reading of it was exact: "el color de las opciones no es
    * del mismo color del tema de la clickgui".
    *
    * <p>These two colours are the mockup's own pill, back-solved. Over its bright blurred backdrop
    * {@code (92,81,62)} the pill measures {@code (38,29,41)}; over its dark one {@code (84,67,67)} it
    * measures {@code (36,24,43)}. Solving {@code result = (1 - PILL_VEIL) * backdrop + PILL_VEIL * fill} for
    * both gives the {@code #1A1028 .. #150D20} ramp below to within one level per channel - so a mockup pill
    * is a {@link #PILL_VEIL} veil of this near-black violet, and 0.22 of the blurred backdrop survives
    * underneath it. That surviving fifth is what still makes these pills glass rather than paint; the veil
    * is what makes them the ClickGUI's colour rather than the accent's.
    */
   private static final float PILL_VEIL = 0.78F;

   /**
    * A pill's contour: one uniform accent hairline, and how far it is pulled towards {@link #PILL_FILL_TOP}.
    *
    * <p>WHY one ring at one weight, and not a lit top edge over a dimmer bottom one. An earlier revision
    * gave every pill a brighter horizontal line along its top and a dimmer one along its bottom - which is
    * what the mockup measures - asking the two together to read as a capsule with thickness. On screen at
    * 1.92x they instead read as two extra rules drawn inside the pill, and the user's verdict was "en mi
    * build estan mal: quitalas del todo". So the pill carries one ring, of one colour, all the way round,
    * and no horizontal segment of it is brighter than any other.
    */
   private static final float PILL_EDGE_MIX = 0.58F;
   private static final int PILL_EDGE_ALPHA = 236;
   private static final int PILL_EDGE_HOVER_ALPHA = 255;

   /**
    * The hover light: a radial glow that follows the pointer, clipped to the pill by the fill's own rounded
    * silhouette.
    *
    * <p>WHY a light at all, when the fill is deliberately nearly opaque: a pill this dark loses every
    * internal cue the accent-tinted veil used to give it, so the hover state has to come from somewhere
    * else. This is the same light {@link MenuGlassSurface#chip} resolves - {@code LIGHT_PEAK}, 0.16 - drawn
    * by the same primitive the rest of the UI's hover lights use, so a menu pill hovers like a ClickGUI
    * control and not like a second, local invention. {@code PILL_LIGHT_LIFT} is how far the light's colour
    * leaves the accent towards white: at 0 the spotlight is the accent itself, at 1 it is neutral.
    */
   private static final float PILL_LIGHT_PEAK = 0.16F;
   private static final float PILL_LIGHT_RADIUS = 0.34F;
   private static final float PILL_LIGHT_LIFT = 0.55F;

   private static final int COLUMN_LEFT = 0;
   private static final int COLUMN_CENTER = 1;
   private static final int COLUMN_RIGHT = 2;
   private static final int COLUMN_STRIP = 3;

   private static final int KIND_PILL = 0;
   private static final int KIND_SERVER = 1;
   private static final int KIND_ACCOUNT = 2;
   private static final int KIND_STRIP = 3;

   // -------------------------------------------------------------------------------------------
   // State
   // -------------------------------------------------------------------------------------------

   private final MenuBackdrop backdrop = MenuBackdrop.getShared();
   private final List<Spot> spots = new ArrayList<Spot>();
   private final List<ServerData> servers = new ArrayList<ServerData>();
   private final Map<String, DynamicTexture> serverIcons = new HashMap<String, DynamicTexture>();
   private final List<String> iconAttempted = new ArrayList<String>();

   /** Design-space origin and uniform scale, recomputed on every resize. */
   private float scale = 1.0F;
   private float originX;
   private float originY;
   /** Design-space width of the window, i.e. {@code width / scale}; the side columns are pinned to it. */
   private float designW = DESIGN_W;

   private float mouseDesignX = -9999.0F;
   private float mouseDesignY = -9999.0F;
   private String hoverId;
   private boolean mouseDown;

   /** Screen-wide entrance envelope, advanced exactly once per frame in {@link #drawBackground(int)}. */
   private float open;

   private SaveFormatComparator quickPlay;
   private ResourceLocation avatarLocation;
   private boolean avatarRequested;
   private ResourceLocation bustLocation;
   private boolean bustRequested;
   private float tooltipAlpha;

   // -------------------------------------------------------------------------------------------
   // Layout
   // -------------------------------------------------------------------------------------------

   @Override
   public void initContent() {
      super.initContent();
      this.showCornerInfo = false;
      this.loadQuickPlay();
      this.loadServers();
      this.avatarRequested = false;
      this.avatarLocation = null;
      this.bustRequested = false;
      this.bustLocation = null;
      this.scale = 0.0F;
      this.layout();
      this.backdrop.restart();
      // Replay the entrance envelope so every visit assembles itself once instead of appearing whole.
      UiAnimation.setState("home.open", 0.0F);
      this.open = 0.0F;
   }

   private void layout() {
      float uniform = (float)this.height / DESIGN_H;
      if (uniform <= 0.0F || Float.isNaN(uniform) || Float.isInfinite(uniform)) {
         uniform = 1.0F;
      }
      // A window far from 16:9 would collide the three columns at that scale, so fall back to fitting the
      // reference band on both axes and let the gutters take the difference.
      float byWidth = (float)this.width / DESIGN_W;
      if (byWidth < uniform * 0.78F) {
         uniform = byWidth;
      }
      this.scale = uniform;
      this.designW = (float)this.width / this.scale;
      this.originX = ((float)this.width - this.designW * this.scale) * 0.5F;
      this.originY = ((float)this.height - DESIGN_H * this.scale) * 0.5F;

      float leftX = MARGIN;
      float rightX = this.designW - MARGIN - RIGHT_W;
      float centerX = (this.designW - CENTER_W) * 0.5F;
      float halfW = (CENTER_W - CENTER_SPLIT) * 0.5F;

      this.spots.clear();
      this.layoutStrip();
      this.layoutLeft(leftX);
      this.layoutCenter(centerX, halfW);
      this.layoutRight(rightX);
   }

   /** Top-right control strip: five square pills, right-aligned 8 px from the corner. */
   private void layoutStrip() {
      LucideIcon[] icons = {
         LucideIcon.GLOBE, LucideIcon.PHOTO, LucideIcon.MAXIMIZE, LucideIcon.SETTINGS, LucideIcon.CLOSE
      };
      String[] ids = { "language", "background", "fullscreen", "options", "quit" };
      String[] tips = {
         I18n.format("options.language", new Object[0]),
         "Fondo del men\u00fa",
         I18n.format("options.fullscreen", new Object[0]),
         this.optionsLabel,
         this.quitLabel
      };
      float right = this.designW - STRIP_MARGIN;
      for (int i = 0; i < STRIP_COUNT; i++) {
         float x = right - STRIP_SIZE - (float)(STRIP_COUNT - 1 - i) * STRIP_PITCH;
         this.spots.add(new Spot(ids[i], KIND_STRIP, COLUMN_STRIP, x, STRIP_MARGIN, STRIP_SIZE, STRIP_SIZE, null,
               null, icons[i], tips[i]));
      }
   }

   private void layoutLeft(float leftX) {
      String quickTip = this.quickPlay != null ? this.quickPlay.getDisplayName() : "Elegir un mundo";
      this.spots.add(new Spot("quickplay", KIND_PILL, COLUMN_LEFT, leftX, QUICK_Y, LEFT_W, PILL_H, "Quickplay",
            quickTip, LucideIcon.PLAYER_PLAY, null));

      if (this.servers.isEmpty()) {
         this.spots.add(new Spot("manage", KIND_PILL, COLUMN_LEFT, leftX, ROW_TOP, LEFT_W, PILL_H,
               "A\u00f1adir servidor", null, LucideIcon.PLUS, null));
         return;
      }

      for (int i = 0; i < this.servers.size() && i < 4; i++) {
         ServerData data = this.servers.get(i);
         this.spots.add(new Spot("server:" + i, KIND_SERVER, COLUMN_LEFT, leftX, ROW_TOP + (float)i * ROW_PITCH,
               LEFT_W, ROW_H, data.serverName, data.serverIP, null, null));
      }
   }

   private void layoutCenter(float centerX, float halfW) {
      // The fourth action is the client's own options page, named after the client and wearing a gear like
      // every other settings surface - the vanilla options pill next to it takes a different glyph (the
      // sliders) so the two are not mistaken for each other. Its id stays "mods": this is the same slot the
      // screen used before, only the destination and the label changed.
      LucideIcon[] icons = { LucideIcon.USER, LucideIcon.USERS, LucideIcon.SLIDERS_HORIZONTAL, LucideIcon.SETTINGS };
      String[] labels = { this.singleplayerLabel, this.multiplayerLabel, "Settings", this.clientName() };
      String[] ids = { "singleplayer", "multiplayer", "settings", "mods" };
      String[] tips = {
         "Jugar un mundo guardado", "Buscar servidores", this.optionsLabel, "Opciones de Augustus"
      };
      for (int i = 0; i < 4; i++) {
         boolean wide = i < 2;
         float x = !wide && i == 3 ? centerX + halfW + CENTER_SPLIT : centerX;
         float y = ACTION_TOP + (wide ? (float)i : 2.0F) * PITCH;
         this.spots.add(new Spot(ids[i], KIND_PILL, COLUMN_CENTER, x, y, wide ? CENTER_W : halfW, PILL_H,
               labels[i], null, icons[i], tips[i]));
      }
   }

   /**
    * Right column: the account, then three actions that exist in <em>this</em> client.
    *
    * <p>The mockup's Host World / Social / Wardrobe were dropped: the first is world creation, which the
    * Singleplayer pill already reaches far more usefully (the world select screen, with all saved worlds),
    * "Social" was a Discord link that is already in the credits, and this client has no wardrobe - the skin
    * lives in the account page. What is left is the useful pair from each side of the client: two Augustus
    * pages a player opens often (proxies, and what changed in the last update) and one vanilla Minecraft
    * screen that a 1.8.9 player otherwise has to dig into the options for (resource packs).
    */
   private void layoutRight(float rightX) {
      this.spots.add(new Spot("account", KIND_ACCOUNT, COLUMN_RIGHT, rightX, ACCOUNT_TOP, RIGHT_W, PILL_H,
            this.username(), null, LucideIcon.CHEVRON_DOWN, "Cambiar de cuenta"));

      LucideIcon[] icons = { LucideIcon.SHIELD, LucideIcon.PALETTE, LucideIcon.CLIPBOARD };
      String[] labels = { "Proxies", "Resource Packs", "Changelog" };
      String[] ids = { "proxies", "packs", "changelog" };
      String[] tips = {
         "Proxies del cliente",
         "Paquetes de recursos de Minecraft",
         "Cambios de la \u00faltima versi\u00f3n"
      };
      for (int i = 0; i < 3; i++) {
         this.spots.add(new Spot(ids[i], KIND_PILL, COLUMN_RIGHT, rightX, SIDE_TOP + (float)i * PITCH, RIGHT_W,
               PILL_H, labels[i], null, icons[i], tips[i]));
      }
   }

   /** Entrance progress of one element, staggered so the screen assembles from the top down. */
   private float appear(int order) {
      return UiAnimation.easeOutCubic(UiAnimation.clamp01((this.open - (float)order * 0.05F) / 0.7F));
   }

   // -------------------------------------------------------------------------------------------
   // Background
   // -------------------------------------------------------------------------------------------

   /**
    * Draws the baked clip frames instead of the stock dirt plate.
    *
    * <p>Overridden here rather than in {@code drawContent} on purpose: {@code GuiScreen} paints the
    * background first and the screen's content after it, and the pills are translucent - they have to be
    * composited over the frame, not over a plate painted on top of it.
    *
    * <p>Two faint scrims only, and both of them cool: a wash behind the corner strip, which stands on the
    * brightest part of the planet limb, and a floor gradient that carries the footer. They used to be a
    * warm wash solved against a warm landscape; the backdrop is now a night-side Earth over the Moon, so a
    * warm grade would read as a colour cast - and the frames are already graded at bake time, so nothing
    * here needs to tint the picture. The remaining passes only buy legibility.
    */
   @Override
   public void drawBackground(int tint) {
      this.open = UiAnimation.smoothState("home.open", 0.0F, 1.0F, 7.5F);
      float fade = UiAnimation.easeOutCubic(UiAnimation.clamp01(this.open * 1.35F));
      float width = (float)this.width;
      float height = (float)this.height;

      net.augustus.utils.shader.BackgroundShaderManager bsm = Augustus.getInstance().getBackgroundShaderManager();
      if (bsm != null && !bsm.isCustomBackgroundEnabled()) {
         this.backdrop.invalidateDraw();
         this.fillGradient(0.0F, 0.0F, width, height, UiTheme.menuPillFillTopArgb(), 0xFF0A0D16);
      } else if (bsm != null && bsm.isShaderBackdropActive() && bsm.renderActiveShaderFullscreen()) {
         this.backdrop.invalidateDraw();
      } else if (this.backdrop.isAvailable()) {
         this.backdrop.draw(0.0F, 0.0F, width, height, fade);
      } else {
         this.backdrop.invalidateDraw();
         this.fillGradient(0.0F, 0.0F, width, height, 0xFF22304C, 0xFF0B1020);
      }

      this.fillGradient(0.0F, 0.0F, width, height * 0.30F, UiTheme.scaleAlpha(0x4D070A16, fade), 0x00000000);
      float floor = height * 0.40F;
      this.fillGradient(0.0F, height - floor, width, floor, 0x00000000, UiTheme.scaleAlpha(0x4A05080F, fade));
   }

   // -------------------------------------------------------------------------------------------
   // Frame
   // -------------------------------------------------------------------------------------------

   @Override
   public void drawContent(int mouseX, int mouseY, float partialTicks) {
      this.mouseDesignX = this.toDesignX((float)mouseX);
      this.mouseDesignY = this.toDesignY((float)mouseY);
      this.mouseDown = UiAnimation.isPrimaryMouseDown();
      this.hoverId = this.hitTest(this.mouseDesignX, this.mouseDesignY);

      // One Kawase capture per frame, taken here because this is the first moment the framebuffer holds
      // the backdrop and nothing else; every pill below samples that same capture. Capturing per pill
      // would blur each pill over the pills already painted behind it.
      float chrome = this.appear(0);
      this.drawStrip(chrome);
      this.drawColumn(COLUMN_LEFT, chrome, 1);
      this.drawHero(chrome);
      this.drawRight(chrome);
      this.drawFooter(chrome);
      this.drawTooltip();

      super.drawContent(mouseX, mouseY, partialTicks);
   }

   // -------------------------------------------------------------------------------------------
   // Columns
   // -------------------------------------------------------------------------------------------

   /** Draws one side column's pills; the centre column is drawn by {@link #drawHero(float)}. */
   private void drawColumn(int column, float alpha, int order) {
      for (Spot spot : this.spots) {
         if (spot.column != column) {
            continue;
         }
         if (spot.kind == KIND_SERVER) {
            this.drawServerRow(spot, alpha, order + 1);
         } else {
            this.drawLabelPill(spot, alpha, order);
         }
      }
   }

   private void drawHero(float alpha) {
      float lift = (1.0F - alpha) * 12.0F;
      float markW = MARK_H * 512.0F / 398.0F;
      float markAlpha = UiAnimation.clamp01(alpha * 1.1F);
      int markTint = UiTheme.scaleAlpha(0xFFFFFFFF, markAlpha);
      BRAND_MARK.draw(this.X((this.designW - markW) * 0.5F), this.Y(MARK_TOP + lift), this.S(markW),
            this.S(MARK_H), UiTheme.accentColor(), markTint, markTint);

      this.trackedCentre(FontManager.interBold, this.brandWordmark(), this.designW * 0.5F, WORD_TOP + lift, FS_WORD,
            WORD_TRACK, UiTheme.scaleAlpha(TEXT_FOOT, alpha));

      this.drawColumn(COLUMN_CENTER, alpha, 3);
   }

   private void drawRight(float alpha) {
      float appear = this.appear(2);
      float content = alpha * appear;
      if (content <= 0.004F) {
         return;
      }
      float rightX = this.designW - MARGIN - RIGHT_W;
      this.drawFigure(rightX, content);

      for (Spot spot : this.spots) {
         if (spot.column != COLUMN_RIGHT) {
            continue;
         }
         if (spot.kind == KIND_ACCOUNT) {
            this.drawAccountPill(spot, content);
         } else {
            this.drawLabelPill(spot, alpha, 4);
         }
      }
   }

   private void drawStrip(float alpha) {
      for (Spot spot : this.spots) {
         if (spot.kind != KIND_STRIP) {
            continue;
         }
         float hover = this.hover(spot);
         float press = this.press(spot);
         float x = this.X(spot.x);
         float y = this.Y(spot.y);
         float size = this.S(spot.width);
         this.drawPillBox(x, y, size, size, this.S(RADIUS), hover, press, alpha);

         if (spot.icon != null) {
            spot.icon.drawCentered(x + size * 0.5F, y + size * 0.5F, this.S(ICON_STRIP),
                  UiTheme.scaleAlpha(mix(TEXT_STRIP_ICON, TEXT_HOT, hover), alpha));
         }
      }
   }

   // -------------------------------------------------------------------------------------------
   // Pills
   // -------------------------------------------------------------------------------------------

   /** The one pill primitive: frost, then the ClickGUI's near-black glass, then the accent contour.
    *
    * <p>Three passes, in this order, and the order is the whole design:
    *
    * <ol>
    *   <li>the blurred backdrop, clipped into the rounded pill (see {@link #drawFrostPill});</li>
    *   <li>the dark veil and the hover light, <em>in one pass</em>, by the same rounded-SDF primitive the
    *       rest of this UI draws its surfaces with - so the light is clipped to the silhouette by the fill
    *       itself rather than by a rectangle, and nothing has to be scissored;</li>
    *   <li>one uniform accent hairline along the same outline.</li>
    * </ol>
    *
    * <p>WHY the veil and the light are one call and not two: the primitive resolves fill, gradient, rim,
    * catchlight and spotlight together, so a second call that still had a fill would composite the veil
    * twice and darken the pill, while one that still had a rim would thicken it. Its two inner-line
    * weights - the top catchlight and the bottom glow - are passed as zero for the same reason the lit top
    * edge was removed: this pill is allowed one ring, and only one.
    *
    * <p>{@code hover} and {@code press} stay the animated 0..1 amounts this screen keeps per spot; they no
    * longer reach a veil, they reach the light and the contour's alpha.
    */
   private void drawPillBox(float x, float y, float width, float height, float radius, float hover, float press,
         float alpha) {
      if (width <= 0.0F || height <= 0.0F) {
         return;
      }
      float h = UiAnimation.clamp01(hover);
      float p = UiAnimation.clamp01(press);
      float a = UiAnimation.clamp01(alpha);
      if (a <= 0.004F) {
         return;
      }
      this.drawFrostPill(x, y, width, height, radius, a);

      int accent = UiTheme.accentColor();
      int pillFillTop = UiTheme.menuPillFillTopArgb();
      int pillFillBottom = UiTheme.menuPillFillBottomArgb();
      // The light follows the pointer, but stays inside the pill: on a 117 px capsule a cursor that has
      // already left it, or a caller forwarding the raw mouse position, must still light it from within
      // rather than from beyond its edge.
      float lightX = Math.max(x + width * 0.15F, Math.min(x + width * 0.85F, this.X(this.mouseDesignX)));
      float lightY = Math.max(y + height * 0.25F, Math.min(y + height * 0.75F, this.Y(this.mouseDesignY)));
      float r = Math.min(radius, Math.min(width, height) * 0.5F);

      GlassRenderer.drawRoundedGradientBox(x, y, width, height, r, r, r, r,
            UiTheme.scaleAlpha(pillFillTop, PILL_VEIL * a * (1.0F - 0.06F * p)),
            UiTheme.scaleAlpha(pillFillBottom, PILL_VEIL * a * (1.0F - 0.06F * p)),
            0,
            UiTheme.BORDER_THICKNESS,
            0.0F,
            0.0F,
            lightX,
            lightY,
            PILL_LIGHT_PEAK * h * a,
            Math.max(width, height) * PILL_LIGHT_RADIUS,
            UiTheme.scaleAlpha(UiTheme.lerpArgb(accent, 0xFFFFFFFF, PILL_LIGHT_LIFT), a));

      int edgeRgb = UiTheme.lerpArgb(accent, pillFillTop, PILL_EDGE_MIX);
      int edgeAlpha = Math.round(PILL_EDGE_ALPHA + (PILL_EDGE_HOVER_ALPHA - PILL_EDGE_ALPHA) * h);
      GlassRenderer.drawRoundedOutline(x, y, width, height, r, UiTheme.BORDER_THICKNESS,
            UiTheme.scaleAlpha(UiTheme.withAlpha(edgeRgb, edgeAlpha), a));
   }

   /**
    * Paints the backdrop's pre-baked blur inside a rounded pill, under the glass.
    *
    * <p>WHY baked rather than captured: {@code MenuGlassSurface.chipBlurred} can blur the live framebuffer
    * through {@code KawaseBlurEngine}, and one capture per pill per frame froze this client's driver twice
    * (the window stopped presenting entirely). {@link MenuBackdrop} already keeps a blurred twin of every
    * frame it uploads, so the frost costs a textured quad and no capture - and it cannot drift out of step
    * with the picture behind it, because it <em>is</em> a blur of that picture, sampled through the
    * backdrop's own live zoom and pan.
    *
    * <p>WHY a staircase of rows and not three rectangles: three rectangles cover a rounded rectangle
    * everywhere except its four {@code radius}-square corners, and on this backdrop those corners landed on
    * the planet's limb, where the sharp pixels left uncovered read as a bright notch bitten out of the
    * blur. Each row is instead inset to where the outline actually is on its outer edge, so a quad never
    * reaches outside the pill and what is left uncovered is a sub-pixel sliver hugging the arc.
    */
   private void drawFrostPill(float x, float y, float width, float height, float radius, float alpha) {
      net.augustus.utils.shader.BackgroundShaderManager bsm = Augustus.getInstance().getBackgroundShaderManager();
      if (bsm == null || !bsm.isAnimatedSceneActive()) {
         return;
      }
      float strength = FROST_STRENGTH * UiAnimation.clamp01(alpha);
      if (strength <= 0.004F || width < 1.0F || height < 1.0F) {
         return;
      }
      // Pull the whole frosted shape inside the pill by a hair. WHY this is not paranoia: the frost is drawn
      // as rectangles and the veil and the ring are drawn as rounded SDFs, and the two rasterize their edges
      // differently - the rectangle covers a fraction of the boundary pixel that the SDF leaves at partial
      // coverage, and whatever frost is left there is not veiled, so it keeps its raw (much lighter) blurred
      // value. Measured on the shipped frame at the strip's top-right chip: 23 device px of (158,165,184)
      // on the first row of the chip against a backdrop of (114,120,137) and a fill of (54,48,70), mirrored
      // under the bottom edge, plus 1-4 px specks along the corner arcs - i.e. exactly the lines the user
      // keeps reporting. Insetting the frost by FROST_EDGE_INSET puts every row of it at least that far
      // inside the veil, where the veil's coverage is complete and the seam cannot show.
      float inset = Math.min(FROST_EDGE_INSET, Math.min(width, height) * 0.24F);
      float fx = x + inset;
      float fy = y + inset;
      float fw = width - inset * 2.0F;
      float fh = height - inset * 2.0F;
      float r = Math.min(radius - inset, Math.min(fw, fh) * 0.5F);
      if (r < 0.5F) {
         this.backdrop.drawFrost(fx, fy, fw, fh, strength);
         return;
      }
      // The straights: one full-width band between the two corner regions.
      this.backdrop.drawFrost(fx, fy + r, fw, fh - r * 2.0F, strength);
      // The arcs: rows marching in from the top and, mirrored, from the bottom, each inset to the arc's
      // position at its own outer edge. FROST_ARC_STEPS is high enough that a row is well under a device
      // pixel tall, so the staircase's own error stays sub-pixel at any GUI scale.
      for (int i = 0; i < FROST_ARC_STEPS; i++) {
         float outer = r * (float)i / (float)FROST_ARC_STEPS;
         float inner = r * (float)(i + 1) / (float)FROST_ARC_STEPS;
         float step = arcInset(r, outer);
         float rowW = fw - step * 2.0F;
         if (rowW <= 0.0F) {
            continue;
         }
         this.backdrop.drawFrost(fx + step, fy + outer, rowW, inner - outer, strength);
         this.backdrop.drawFrost(fx + step, fy + fh - inner, rowW, inner - outer, strength);
      }
   }

   /**
    * Horizontal inset of a rounded outline at {@code distance} from its nearest horizontal edge: the full
    * radius at the very edge (where the outline is only the flat top or bottom edge, {@code x + r} to
    * {@code x + w - r}) falling to 0 once the straight sides are reached.
    *
    * <p>WHY the {@code distance == 0} row is the one that matters, and why the old guard was wrong: the
    * previous version special-cased a zero square root to an inset of 0, which is the value for the
    * <em>straight</em> part - so the outermost frost row of every rounded chip was painted at full width
    * instead of inset by {@code radius}. The quad then covered the two corner slivers that lie outside the
    * outline, and because the veil and the ring are drawn on the outline itself, those slivers kept their
    * raw frosted backdrop: a bright one-pixel line running along the top and the bottom of every pill, the
    * strip chips included, wider than the chip and lighter than both the fill and the backdrop. Measured on
    * the shipped 1920x1080 frame at the strip's top-right chip: row 15 at (158,165,184) against a backdrop
    * of (114,120,137) and a fill of (54,48,70), full chip width, with the mirror image at row 49. The
    * correct value there is {@code radius}, never 0, so the row stays inside the silhouette and the veil
    * covers it like every other row.</p>
    */
   private static float arcInset(float radius, float distance) {
      float d = Math.max(0.0F, Math.min(radius, distance));
      float k = radius - d;
      float square = radius * radius - k * k;
      return radius - (float)Math.sqrt((double)Math.max(0.0F, square));
   }

   /** A one-line action pill: glyph and label as one group, centred in the pill. */
   private void drawLabelPill(Spot spot, float alpha, int order) {
      float appear = this.appear(order);
      float opacity = alpha * appear;
      if (opacity <= 0.004F) {
         return;
      }
      float hover = this.hover(spot);
      float press = this.press(spot);
      float x = this.X(spot.x);
      float y = this.Y(spot.y) + this.S(press * 0.6F);
      float w = this.S(spot.width);
      float h = this.S(spot.height);
      this.drawPillBox(x, y, w, h, this.S(RADIUS), hover, press, opacity);

      InterFontUtil face = FontManager.interSemiBold;
      float iconPx = this.S(ICON);
      float gap = this.S(ICON_GAP);
      // Neither the glyph nor the label reacts to hover. The user's rule for this round was exact - "no debe
      // aparecer ese hover de seleccion en el icono ni las letras ya que es un boton la opcion": the option
      // is a button, so the button is what answers the pointer. That answer is the accent ring brightening
      // and the light that follows the cursor inside the fill, both in drawPillBox; recolouring the parts
      // reads as a selection state on a widget that has none, and it made a row flicker in two pieces.
      int tint = UiTheme.lerpArgb(UiTheme.accentColor(), 0xFFFFFFFF, 0.14F);
      int labelColor = UiTheme.scaleAlpha(TEXT_BASE, opacity);
      float textY = y + (h - this.textHeight(face, FS_PILL)) * 0.5F - this.S(0.5F);

      if ("quickplay".equals(spot.id)) {
         // The mockup's Quickplay is a dropdown rather than a centred button: the glyph is pinned to the
         // left inset, the label sits on the pill's own centre line, and a caret marks the right edge.
         if (spot.icon != null) {
            spot.icon.draw(x + this.S(6.0F), y + (h - iconPx) * 0.5F, iconPx, UiTheme.scaleAlpha(tint, opacity),
                  false);
         }
         float labelW = this.textWidth(face, spot.label, FS_PILL);
         float minCentre = x + this.S(6.0F) + iconPx + gap + labelW * 0.5F;
         this.trackedCentre(face, spot.label, this.toDesignX(Math.max(x + w * 0.5F, minCentre)),
               this.toDesignY(textY), FS_PILL, LABEL_TRACK, labelColor);
         this.drawChevron(LucideIcon.CHEVRON_DOWN, x + w - this.S(11.0F), y + h * 0.5F, 0.0F, opacity);
         return;
      }

      float textW = this.textWidth(face, spot.label, FS_PILL);
      float total = iconPx + gap + textW;
      float startX = x + (w - total) * 0.5F;

      if (spot.icon != null) {
         spot.icon.draw(startX, y + (h - iconPx) * 0.5F, iconPx, UiTheme.scaleAlpha(tint, opacity), false);
      }
      this.trackedLeft(face, spot.label, this.toDesignX(startX + iconPx + gap), this.toDesignY(textY), FS_PILL,
            LABEL_TRACK, labelColor);
   }

   /** Saved-server row: favicon, name, address, and a chevron once hovered. */
   private void drawServerRow(Spot spot, float alpha, int order) {
      float appear = this.appear(order);
      float opacity = alpha * appear;
      if (opacity <= 0.004F) {
         return;
      }
      float hover = this.hover(spot);
      float press = this.press(spot);
      float x = this.X(spot.x);
      float y = this.Y(spot.y);
      float w = this.S(spot.width);
      float h = this.S(spot.height);
      this.drawPillBox(x, y, w, h, this.S(RADIUS), hover, press, opacity);

      float box = this.S(15.0F);
      float bx = x + this.S(5.0F);
      float by = y + (h - box) * 0.5F;
      GlassRenderer.drawRoundedRect(bx, by, box, box, this.S(3.5F), UiTheme.scaleAlpha(0x33FFFFFF, opacity));

      DynamicTexture icon = spot.detail == null ? null : this.serverIcons.get(spot.detail);
      if (icon != null) {
         this.drawTextureQuad(icon.getGlTextureId(), bx, by, box, box, opacity);
      } else if (LucideIcon.SERVER != null) {
         LucideIcon.SERVER.drawCentered(bx + box * 0.5F, by + box * 0.5F, this.S(9.0F),
               UiTheme.scaleAlpha(TEXT_SUB, opacity));
      }

      float textX = spot.x + 24.0F;
      String name = this.trimToWidth(FontManager.interSemiBold, spot.label, FS_SERVER, LEFT_W - 42.0F);
      this.trackedLeft(FontManager.interSemiBold, name, textX, this.toDesignY(y + h * 0.20F), FS_SERVER, 0.1F,
            UiTheme.scaleAlpha(TEXT_BASE, opacity));

      String detail = this.trimToWidth(FontManager.body(), spot.detail, FS_SUB, LEFT_W - 42.0F);
      this.text(FontManager.body(), detail, textX, this.toDesignY(y + h * 0.57F), FS_SUB,
            UiTheme.scaleAlpha(TEXT_SUB, opacity));

      // The mockup keeps the caret visible on every row; like the pill glyphs it does not recolour on hover.
      this.drawChevron(LucideIcon.CHEVRON_RIGHT, x + w - this.S(11.0F), y + h * 0.5F, 0.0F, opacity);
   }

   /** Account row: head, name, chevron - the mockup draws the head as a small framed face. */
   private void drawAccountPill(Spot spot, float alpha) {
      float hover = this.hover(spot);
      float press = this.press(spot);
      float x = this.X(spot.x);
      float y = this.Y(spot.y);
      float w = this.S(spot.width);
      float h = this.S(spot.height);
      this.drawPillBox(x, y, w, h, this.S(RADIUS), hover, press, alpha);

      float head = this.S(13.0F);
      float hx = x + this.S(3.0F);
      float hy = y + (h - head) * 0.5F;
      int texture = this.headTextureId();
      if (texture > 0) {
         this.drawTextureQuad(texture, hx, hy, head, head, alpha);
      } else {
         GlassRenderer.drawRoundedRect(hx, hy, head, head, this.S(2.5F), UiTheme.scaleAlpha(0x33FFFFFF, alpha));
      }

      String name = this.trimToWidth(FontManager.interSemiBold, spot.label, FS_PILL, RIGHT_W - 48.0F);
      float textY = y + (h - this.textHeight(FontManager.interSemiBold, FS_PILL)) * 0.5F - this.S(0.5F);
      // The mockup centres the account name between the head and the caret, it does not hang it off the head.
      float nameW = this.textWidth(FontManager.interSemiBold, name, FS_PILL) / this.scale;
      this.text(FontManager.interSemiBold, name,
            Math.max(spot.x + 21.0F, spot.x + spot.width * 0.5F - nameW * 0.5F), this.toDesignY(textY), FS_PILL,
            UiTheme.scaleAlpha(TEXT_BASE, alpha));

      this.drawChevron(LucideIcon.CHEVRON_DOWN, x + w - this.S(11.0F), y + h * 0.5F, 0.0F, alpha);
   }

   /**
    * The player figure above the account row: a real 3D player model, turning slowly on the spot.
    *
    * <p>WHY a live model and not a bust render: the mockup's framed head-and-shoulders was a still image,
    * and the first revision of this screen matched it with a downloaded minotar bust. A menu whose only
    * human element is a JPEG reads as a web page; a model that breathes, looks around and turns reads as a
    * game. {@link MenuPlayerFigure} owns the whole rendering problem (no {@code World} is loaded here, so
    * nothing in the vanilla entity pipeline can be used), including its skin resolution and its GL state.
    *
    * <p>The bust is kept only as a first-frames fallback: {@code MenuPlayerFigure} reports {@code isReady()}
    * once it has actually bound a skin, and until then the flat bust keeps the column from having a hole.
    */
   private void drawFigure(float rightX, float alpha) {
      float centre = rightX + RIGHT_W * 0.5F;
      // The bust ends at the waist now - the torso dissolves from the shoulders down - so the shadow that
      // grounds it follows the last drawn pixel instead of the soles, which sit a torso below it.
      float bottom = MenuPlayerFigure.bustBottomY(this.Y(FIGURE_FEET), this.S(FIGURE_H));
      float shadowW = FIGURE_H * 0.56F;
      float shadowH = 7.0F;
      GlassRenderer.drawSoftShadow(this.X(centre - shadowW * 0.5F), bottom - this.S(shadowH * 0.5F),
            this.S(shadowW), this.S(shadowH), this.S(2.5F), this.S(9.0F), 0.0F,
            UiTheme.scaleAlpha(0x59000000, alpha));

      MenuPlayerFigure.draw(this.X(centre), this.Y(FIGURE_FEET), this.S(FIGURE_H), 0.0F, alpha);
      if (!MenuPlayerFigure.isReady()) {
         this.drawBust(rightX, alpha);
      }
   }

   /**
    * The head-and-shoulders fallback: a downloaded bust render, or failing that the player's head.
    *
    * <p>WHY a downloaded image at all: the main menu is drawn with no {@code World} loaded, and every route
    * to a rendered player model needs one - constructing an entity there is where a menu starts crashing.
    * {@link MenuPlayerFigure} solves that properly; this stays as the frame-zero placeholder and as the
    * degraded path when no skin can be resolved at all.
    */
   private void drawBust(float rightX, float alpha) {
      float boxW = BUST_H * BUST_ASPECT;
      float x = this.X(rightX + (RIGHT_W - boxW) * 0.5F);
      float y = this.Y(BUST_TOP);
      float w = this.S(boxW);
      float h = this.S(BUST_H);

      int bust = this.bustTextureId();
      if (bust > 0) {
         this.drawTextureQuad(bust, x, y, w, h, alpha);
         return;
      }
      int head = this.headTextureId();
      if (head > 0) {
         float side = this.S(BUST_H * 0.74F);
         this.drawTextureQuad(head, x + (w - side) * 0.5F, y + this.S(4.0F), side, side, alpha);
      }
   }

   private void drawChevron(LucideIcon icon, float x, float centerY, float hover, float alpha) {
      if (icon == null) {
         return;
      }
      float size = this.S(7.0F);
      icon.draw(x, centerY - size * 0.5F, size, UiTheme.scaleAlpha(mix(TEXT_CHEVRON, TEXT_HOT, hover), alpha), false);
   }

   private void drawFooter(float alpha) {
      float opacity = alpha * this.appear(5);
      if (opacity <= 0.004F) {
         return;
      }
      float y = FOOTER_TOP;
      float markH = 8.5F;
      float markW = markH * 512.0F / 398.0F;
      int footerMarkTint = UiTheme.scaleAlpha(0xFFFFFFFF, opacity);
      BRAND_MARK.draw(this.X(MARGIN - 1.0F), this.Y(y + 0.6F), this.S(markW), this.S(markH),
            UiTheme.accentColor(), footerMarkTint, footerMarkTint);

      float textX = MARGIN + markW + 4.0F;
      this.trackedLeft(FontManager.interBold, this.brandWordmark(), textX, y, FS_FOOT, 0.8F,
            UiTheme.scaleAlpha(TEXT_FOOT, opacity));
      float wordW = this.trackedWidth(FontManager.interBold, this.brandWordmark(), FS_FOOT, 0.8F) / this.scale;
      this.text(FontManager.body(), Augustus.getInstance().getVersion(), textX + wordW + 5.0F, y + 0.1F, FS_FOOT,
            UiTheme.scaleAlpha(TEXT_FOOT_DIM, opacity));

      String authors = "Created by " + Augustus.getInstance().getAuthors();
      float width = this.textWidth(FontManager.body(), authors, FS_FOOT);
      this.text(FontManager.body(), authors, this.designW - MARGIN - width / this.scale, y + 0.1F, FS_FOOT,
            UiTheme.scaleAlpha(TEXT_FOOT_DIM, opacity));
   }

   private void drawTooltip() {
      String label = null;
      for (Spot spot : this.spots) {
         if (spot.id != null && spot.id.equals(this.hoverId) && spot.tooltip != null) {
            label = spot.tooltip;
         }
      }
      boolean show = label != null && this.open > 0.9F;
      this.tooltipAlpha = UiAnimation.smoothState("home.tip", show ? 1.0F : 0.0F, 18.0F);
      if (!show || this.tooltipAlpha <= 0.02F || label == null) {
         return;
      }
      InterFontUtil face = FontManager.body();
      if (face == null) {
         return;
      }
      float padX = this.S(7.0F);
      float padY = this.S(4.0F);
      float boxW = this.textWidth(face, label, FS_PILL) + padX * 2.0F;
      // The badge is sized from the face's own line box rather than from a multiple of the font size. The
      // old 1.55x guess was an estimate of the ascent/descent split, so wherever it was wrong the label sat
      // off-centre by the difference; with the box built out of the measured height instead, the equal
      // padding above and below is a property of the construction rather than a number that has to be tuned.
      float textH = this.textHeight(face, FS_PILL);
      float boxH = textH + padY * 2.0F;
      float x = this.X(this.mouseDesignX) + this.S(11.0F);
      float y = this.Y(this.mouseDesignY) + this.S(15.0F);
      if (x + boxW > (float)this.width - this.S(4.0F)) {
         x -= boxW + this.S(22.0F);
      }
      float radius = this.S(4.0F);
      // The ClickGUI tooltip material, plus one dark scrim underneath: the pill glass is meant to sit on the
      // overlay's captured backdrop, and out here it can land on the sunlit limb of the planet, where a
      // 7 px label would wash out. The scrim keeps it legible; the surface on top keeps it on-theme. Both
      // are frosted for the same reason the pills are - a glass badge over a sharp frame is the one thing
      // this menu's own material never looks like.
      this.drawFrostPill(x, y, boxW, boxH, radius, this.tooltipAlpha);
      GlassRenderer.drawRoundedRect(x, y, boxW, boxH, radius, UiTheme.scaleAlpha(UiTheme.menuTooltipScrimArgb(), this.tooltipAlpha));
      MenuGlassSurface.panel(x, y, boxW, boxH, radius, UiTheme.accentColor(), 0.0F, 0.0F, this.tooltipAlpha);
      this.text(face, label, this.toDesignX(x + padX), this.toDesignY(y + padY), FS_PILL,
            UiTheme.scaleAlpha(TEXT_HOT, this.tooltipAlpha));
   }

   // -------------------------------------------------------------------------------------------
   // Input
   // -------------------------------------------------------------------------------------------

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
      super.mouseClicked(mouseX, mouseY, mouseButton);
      if (mouseButton != 0 || this.open < 0.8F) {
         return;
      }
      String id = this.hitTest(this.toDesignX((float)mouseX), this.toDesignY((float)mouseY));
      if (id != null) {
         this.activate(id);
      }
   }

   private String hitTest(float designX, float designY) {
      for (int i = this.spots.size() - 1; i >= 0; i--) {
         Spot spot = this.spots.get(i);
         if (designX >= spot.x && designX <= spot.x + spot.width && designY >= spot.y
               && designY <= spot.y + spot.height) {
            return spot.id;
         }
      }
      return null;
   }

   private void activate(String id) {
      if ("singleplayer".equals(id)) {
         this.openScreen(new GuiSelectWorld(this));
      } else if ("multiplayer".equals(id)) {
         this.openScreen(new GuiMultiplayer(this));
      } else if ("mods".equals(id)) {
         // The client's own option pages, not the module list: this pill is the client's options, and the ClickGUI
         // is a separate tool the player opens deliberately rather than through the menu.
         // Tab 0, because that is the page the client's other layouts already use for this button
         // (ClassicMainMenuScreen:19, SideMainMenuScreen:59) - and because tab 2 is the background page, which
         // the photo icon in the strip above already owns, so sharing it made two different buttons land on
         // the same screen.
         this.openScreen(new ClientTabContainerScreen(this, 0));
      } else if ("settings".equals(id) || "options".equals(id)) {
         this.openScreen(new GuiOptions(this, this.mc.gameSettings));
      } else if ("language".equals(id)) {
         this.openScreen(new GuiLanguage(this, this.mc.gameSettings, this.mc.getLanguageManager()));
      } else if ("background".equals(id)) {
         this.openScreen(new ClientTabContainerScreen(this, Augustus.getInstance().getClientTabManager().indexOf(BackgroundMenuTab.class)));
      } else if ("fullscreen".equals(id)) {
         try {
            this.mc.toggleFullscreen();
         } catch (Throwable ignored) {
         }
         this.scale = 0.0F;
         this.layout();
      } else if ("quit".equals(id)) {
         this.mc.shutdown();
      } else if ("account".equals(id)) {
         this.openScreen(new ClientTabContainerScreen(this, Augustus.getInstance().getClientTabManager().indexOf(net.augustus.ui.account.AccountManagerScreen.class)));
      } else if ("proxies".equals(id)) {
         this.openScreen(new ClientTabContainerScreen(this, Augustus.getInstance().getClientTabManager().indexOf(net.augustus.ui.proxy.ProxyManagerScreen.class)));
      } else if ("changelog".equals(id)) {
         this.openScreen(new ClientTabContainerScreen(this, Augustus.getInstance().getClientTabManager().indexOf(ChangelogMenuTab.class)));
      } else if ("packs".equals(id)) {
         this.openScreen(new GuiScreenResourcePacks(this));
      } else if ("quickplay".equals(id)) {
         if (this.quickPlay != null) {
            this.mc.launchIntegratedServer(this.quickPlay.getFileName(), this.quickPlay.getDisplayName(),
                  (WorldSettings)null);
         } else {
            this.openScreen(new GuiSelectWorld(this));
         }
      } else if ("manage".equals(id)) {
         this.openScreen(new GuiMultiplayer(this));
      } else if (id.startsWith("server:")) {
         int index = Integer.parseInt(id.substring(7));
         if (index >= 0 && index < this.servers.size()) {
            ServerData data = this.servers.get(index);
            this.mc.displayGuiScreen(new GuiConnecting(new GuiMultiplayer(this), this.mc, data));
         }
      }
   }

   private float hover(Spot spot) {
      boolean active = spot.id != null && spot.id.equals(this.hoverId);
      return UiAnimation.smoothState("home.hover." + spot.id, active ? 1.0F : 0.0F, 15.0F);
   }

   private float press(Spot spot) {
      boolean active = spot.id != null && spot.id.equals(this.hoverId) && this.mouseDown;
      return UiAnimation.smoothState("home.press." + spot.id, active ? 1.0F : 0.0F, 22.0F);
   }

   // -------------------------------------------------------------------------------------------
   // Data
   // -------------------------------------------------------------------------------------------

   /** The most recently played singleplayer world, if any - the "continue" target of Quickplay. */
   private void loadQuickPlay() {
      this.quickPlay = null;
      try {
         List list = this.mc.getSaveLoader().getSaveList();
         SaveFormatComparator best = null;
         for (Object element : list) {
            SaveFormatComparator candidate = (SaveFormatComparator)element;
            if (best == null || candidate.getLastTimePlayed() > best.getLastTimePlayed()) {
               best = candidate;
            }
         }
         this.quickPlay = best;
      } catch (Throwable ignored) {
         this.quickPlay = null;
      }
   }

   private void loadServers() {
      this.servers.clear();
      this.serverIcons.clear();
      this.iconAttempted.clear();
      try {
         File file = new File(this.mc.mcDataDir, "servers.dat");
         if (!file.isFile()) {
            return;
         }
         NBTTagCompound tag = CompressedStreamTools.read(file);
         if (tag == null) {
            return;
         }
         NBTTagList list = tag.getTagList("servers", 10);
         for (int i = 0; i < list.tagCount() && this.servers.size() < 4; i++) {
            this.servers.add(ServerData.getServerDataFromNBTCompound(list.getCompoundTagAt(i)));
         }
         for (ServerData data : this.servers) {
            this.ensureServerIcon(data);
         }
      } catch (Throwable ignored) {
      }
   }

   private void ensureServerIcon(ServerData data) {
      if (data == null || data.serverIP == null || this.iconAttempted.contains(data.serverIP)) {
         return;
      }
      this.iconAttempted.add(data.serverIP);
      try {
         if (data.getBase64EncodedIconData() == null) {
            return;
         }
         byte[] raw = Base64.getDecoder().decode(data.getBase64EncodedIconData());
         BufferedImage image = TextureUtil.readBufferedImage(new ByteArrayInputStream(raw));
         if (image == null || image.getWidth() != 64 || image.getHeight() != 64) {
            return;
         }
         DynamicTexture texture = new DynamicTexture(image);
         texture.setBlurMipmap(true, false);
         this.serverIcons.put(data.serverIP, texture);
      } catch (Throwable ignored) {
      }
   }

   private int headTextureId() {
      if (!this.avatarRequested) {
         this.avatarRequested = true;
         try {
            this.avatarLocation = MinotarAvatarCache.getAvatar(this.username(), 64);
         } catch (Throwable ignored) {
            this.avatarLocation = null;
         }
      }
      return this.textureIfLoaded(this.avatarLocation);
   }

   private int bustTextureId() {
      if (!this.bustRequested) {
         this.bustRequested = true;
         try {
            String name = this.username();
            ResourceLocation location = new ResourceLocation("augustus", "bust/" + name.toLowerCase());
            ThreadDownloadImageData download = new ThreadDownloadImageData((File)null,
                  "https://minotar.net/bust/" + name + "/256.png", (ResourceLocation)null, (IImageBuffer)null);
            this.mc.getTextureManager().loadTexture(location, download);
            this.bustLocation = location;
         } catch (Throwable ignored) {
            this.bustLocation = null;
         }
      }
      return this.textureIfLoaded(this.bustLocation);
   }

   /** The GL id of a downloaded texture, but only once its bytes actually arrived. */
   private int textureIfLoaded(ResourceLocation location) {
      if (location == null) {
         return -1;
      }
      try {
         ITextureObject texture = this.mc.getTextureManager().getTexture(location);
         if (texture instanceof ThreadDownloadImageData
               && Boolean.TRUE.equals(((ThreadDownloadImageData)texture).imageFound)) {
            return texture.getGlTextureId();
         }
      } catch (Throwable ignored) {
      }
      return -1;
   }

   private String username() {
      String name = Augustus.getInstance().getUsername();
      return name == null || name.isEmpty() ? "Player" : name;
   }

   /** The product name in the client's own casing: the wordmark is set in caps, a menu label is not. */
   private String clientName() {
      String name = Augustus.getInstance().getName();
      return name == null || name.isEmpty() ? "Augustus" : name;
   }

   private String brandWordmark() {
      String name = Augustus.getInstance().getName();
      return name == null || name.isEmpty() ? "AUGUSTUS" : name.toUpperCase();
   }

   private static ResourceLocation logoLocation() {
      return new ResourceLocation("augustus", "textures/menu/logo_mark.png");
   }

   // -------------------------------------------------------------------------------------------
   // Coordinates and primitives
   // -------------------------------------------------------------------------------------------

   private float X(float design) {
      return this.originX + design * this.scale;
   }

   private float Y(float design) {
      return this.originY + design * this.scale;
   }

   private float S(float design) {
      return design * this.scale;
   }

   private float toDesignX(float screen) {
      return this.scale <= 0.0F ? 0.0F : (screen - this.originX) / this.scale;
   }

   private float toDesignY(float screen) {
      return this.scale <= 0.0F ? 0.0F : (screen - this.originY) / this.scale;
   }

   private void text(InterFontUtil face, String value, float designX, float designY, float size, int argb) {
      if (face == null || value == null || value.isEmpty()) {
         return;
      }
      face.drawStringAtSize(value, this.X(designX), this.Y(designY), this.S(size), argb, true);
   }

   /** Width of a letter-spaced run, in screen pixels. */
   private float trackedWidth(InterFontUtil face, String value, float size, float tracking) {
      if (face == null || value == null || value.isEmpty()) {
         return 0.0F;
      }
      float unit = this.S(size) / face.getEffectiveSize();
      float trackingPx = this.S(tracking);
      float total = 0.0F;
      for (int i = 0; i < value.length(); i++) {
         total += face.getStringWidth(value.substring(i, i + 1)) * unit;
         if (i < value.length() - 1) {
            total += trackingPx;
         }
      }
      return total;
   }

   private void trackedLeft(InterFontUtil face, String value, float leftDesignX, float designY, float size,
         float tracking, int argb) {
      if (face == null || value == null || value.isEmpty()) {
         return;
      }
      float unit = this.S(size) / face.getEffectiveSize();
      float trackingPx = this.S(tracking);
      float drawSize = this.S(size);
      float pen = this.X(leftDesignX);
      float y = this.Y(designY);
      for (int i = 0; i < value.length(); i++) {
         String glyph = value.substring(i, i + 1);
         face.drawStringAtSize(glyph, pen, y, drawSize, argb, false);
         pen += face.getStringWidth(glyph) * unit + trackingPx;
      }
   }

   private void trackedCentre(InterFontUtil face, String value, float centreDesignX, float designY, float size,
         float tracking, int argb) {
      float width = this.trackedWidth(face, value, size, tracking);
      this.trackedLeft(face, value, centreDesignX - width / this.scale * 0.5F, designY, size, tracking, argb);
   }

   private float textWidth(InterFontUtil face, String value, float size) {
      if (face == null || value == null || value.isEmpty()) {
         return 0.0F;
      }
      return face.getStringWidth(value) * (this.S(size) / face.getEffectiveSize());
   }

   private float textHeight(InterFontUtil face, float size) {
      return face == null ? 0.0F : face.getFontHeight() * (this.S(size) / face.getEffectiveSize());
   }

   private String trimToWidth(InterFontUtil face, String value, float size, float maxDesignWidth) {
      if (face == null || value == null) {
         return "";
      }
      float limit = this.S(maxDesignWidth);
      if (this.textWidth(face, value, size) <= limit) {
         return value;
      }
      int cut = value.length();
      while (cut > 1 && this.textWidth(face, value.substring(0, cut) + "...", size) > limit) {
         cut--;
      }
      return value.substring(0, cut) + "...";
   }

   private void drawTexture(ResourceLocation location, float x, float y, float width, float height, float alpha) {
      if (location == null || alpha <= 0.004F) {
         return;
      }
      try {
         this.mc.getTextureManager().bindTexture(location);
         ITextureObject texture = this.mc.getTextureManager().getTexture(location);
         if (texture != null) {
            this.drawTextureQuad(texture.getGlTextureId(), x, y, width, height, alpha);
         }
      } catch (Throwable ignored) {
      }
   }

   private void drawTextureQuad(int glTextureId, float x, float y, float width, float height, float alpha) {
      GlStateManager.enableTexture2D();
      GlStateManager.enableBlend();
      GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
      GlStateManager.color(1.0F, 1.0F, 1.0F, UiAnimation.clamp01(alpha));
      GlStateManager.bindTexture(glTextureId);
      Tessellator tessellator = Tessellator.getInstance();
      WorldRenderer renderer = tessellator.getWorldRenderer();
      renderer.begin(7, DefaultVertexFormats.POSITION_TEX);
      renderer.pos((double)x, (double)(y + height), 0.0D).tex(0.0D, 1.0D).endVertex();
      renderer.pos((double)(x + width), (double)(y + height), 0.0D).tex(1.0D, 1.0D).endVertex();
      renderer.pos((double)(x + width), (double)y, 0.0D).tex(1.0D, 0.0D).endVertex();
      renderer.pos((double)x, (double)y, 0.0D).tex(0.0D, 0.0D).endVertex();
      tessellator.draw();
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
   }

   private void fillGradient(float x, float y, float width, float height, int topArgb, int bottomArgb) {
      float topA = (float)(topArgb >>> 24 & 255) / 255.0F;
      float bottomA = (float)(bottomArgb >>> 24 & 255) / 255.0F;
      if (topA <= 0.002F && bottomA <= 0.002F) {
         return;
      }
      GlStateManager.disableTexture2D();
      GlStateManager.enableBlend();
      GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
      // GL_SMOOTH (0x1B01) for the duration: the shade model is global state that Gui.drawGradientRect
      // leaves on GL_FLAT after it restores it, and under flat shading the four per-vertex colours above are
      // ignored in favour of the last one received - a vertical gradient then paints as a single flat
      // rectangle with a hard cut at its edge, which is exactly what this band looked like before.
      int prevShade = GL11.glGetInteger(2900);
      GlStateManager.shadeModel(7425);
      Tessellator tessellator = Tessellator.getInstance();
      WorldRenderer renderer = tessellator.getWorldRenderer();
      renderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
      renderer.pos((double)x, (double)(y + height), 0.0D)
            .color(bottomArgb >> 16 & 255, bottomArgb >> 8 & 255, bottomArgb & 255, Math.round(bottomA * 255.0F))
            .endVertex();
      renderer.pos((double)(x + width), (double)(y + height), 0.0D)
            .color(bottomArgb >> 16 & 255, bottomArgb >> 8 & 255, bottomArgb & 255, Math.round(bottomA * 255.0F))
            .endVertex();
      renderer.pos((double)(x + width), (double)y, 0.0D)
            .color(topArgb >> 16 & 255, topArgb >> 8 & 255, topArgb & 255, Math.round(topA * 255.0F)).endVertex();
      renderer.pos((double)x, (double)y, 0.0D)
            .color(topArgb >> 16 & 255, topArgb >> 8 & 255, topArgb & 255, Math.round(topA * 255.0F)).endVertex();
      tessellator.draw();
      GlStateManager.shadeModel(prevShade);
      GlStateManager.enableTexture2D();
   }

   /** Straight two-colour mix, so the screen's whole palette stays in the constants above. */
   private static int mix(int from, int to, float t) {
      float k = t < 0.0F ? 0.0F : (t > 1.0F ? 1.0F : t);
      int a = (int)((from >>> 24 & 255) + ((to >>> 24 & 255) - (from >>> 24 & 255)) * k);
      int r = (int)((from >> 16 & 255) + ((to >> 16 & 255) - (from >> 16 & 255)) * k);
      int g = (int)((from >> 8 & 255) + ((to >> 8 & 255) - (from >> 8 & 255)) * k);
      int b = (int)((from & 255) + ((to & 255) - (from & 255)) * k);
      return a << 24 | r << 16 | g << 8 | b;
   }

   /** One interactive region, in design (reference pixel) coordinates. */
   private static final class Spot {
      private final String id;
      private final int kind;
      private final int column;
      private final float x;
      private final float y;
      private final float width;
      private final float height;
      private final String label;
      private final String detail;
      private final LucideIcon icon;
      private final String tooltip;

      private Spot(String id, int kind, int column, float x, float y, float width, float height, String label,
            String detail, LucideIcon icon, String tooltip) {
         this.id = id;
         this.kind = kind;
         this.column = column;
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
         this.label = label;
         this.detail = detail;
         this.icon = icon;
         this.tooltip = tooltip;
      }
   }
}