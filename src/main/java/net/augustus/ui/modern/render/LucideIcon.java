package net.augustus.ui.modern.render;

import java.util.EnumMap;
import java.util.Map;
import net.augustus.modules.Categorys;
import net.augustus.ui.icons.IconManager;
import net.augustus.utils.interfaces.MC;
import net.minecraft.util.ResourceLocation;

/**
 * Registry of the click-GUI icon set, now backed by <b>Tabler Icons</b>.
 *
 * <p>Each constant names one Tabler outline SVG. The artwork itself lives, verbatim, in
 * {@code assets/augustus/icons/tabler/<id>.svg} (MIT, revision pinned in {@code REVISION.txt}) and is
 * converted into a tinted GL texture on demand by {@link IconManager}. Nothing is drawn from path
 * data or polygons at runtime, so adding or swapping an icon is a file drop rather than a code change.
 *
 * <p>This type used to carry Lucide geometry that the project stroked itself. That pipeline was
 * removed: the artwork is now rasterised by SVG Salamander and uploaded as a Minecraft
 * {@code DynamicTexture}, which is both higher fidelity and far less code to maintain.
 *
 * <p>The public shape is unchanged ({@link #forCategory}, {@link #drawCentered}, {@link #draw},
 * {@link #getResourceLocation}), so no call site had to change.
 */
public enum LucideIcon implements MC {
   LAYOUT_GRID("layout-grid"),
   LIST("list"),
   COMPACT_LIST("menu-2"),
   ZAP("bolt"),
   FOLDER("folder"),
   KEYBOARD("keyboard"),
   SETTINGS("settings"),
   SLIDERS_HORIZONTAL("adjustments-horizontal"),
   PALETTE("palette"),
   SPARKLES("sparkles"),
   SEARCH("search"),
   ARROW_LEFT("arrow-left"),
   ARROW_RIGHT("arrow-right"),
   CHEVRON_DOWN("chevron-down"),
   CHEVRON_RIGHT("chevron-right"),
   CHECK("check"),
   PLUS("plus"),
   MINUS("minus"),
   SQUARE("square"),
   CLOSE("x"),
   DOWNLOAD("download"),
   UPLOAD("upload"),
   REFRESH_CW("refresh"),
   TRASH_2("trash"),
   ROTATE_CCW("arrow-back-up"),
   COPY("copy"),
   CLIPBOARD("clipboard"),
   PIPETTE("color-picker"),
   CROSSHAIR("crosshair"),
   SWORDS("swords"),
   WIND("wind"),
   EYE("eye"),
   USER("user"),
   GLOBE("world"),
   BOXES("box-multiple"),
   ACTIVITY("activity"),
   SHIELD("shield"),
   LOCK("lock"),
   EDIT_HUD("layout-dashboard");

   private static final Map<Categorys, LucideIcon> CATEGORY_MAP = new EnumMap<Categorys, LucideIcon>(Categorys.class);

   static {
      CATEGORY_MAP.put(Categorys.COMBAT, SWORDS);
      CATEGORY_MAP.put(Categorys.LEGIT, SHIELD);
      CATEGORY_MAP.put(Categorys.MOVEMENT, WIND);
      CATEGORY_MAP.put(Categorys.RENDER, EYE);
      CATEGORY_MAP.put(Categorys.PLAYER, USER);
      CATEGORY_MAP.put(Categorys.WORLD, GLOBE);
      CATEGORY_MAP.put(Categorys.MISC, BOXES);
      CATEGORY_MAP.put(Categorys.LATENCY, ACTIVITY);
      CATEGORY_MAP.put(Categorys.FUN, SPARKLES);
   }

   private final ResourceLocation resourceLocation;
   private final String tablerId;

   LucideIcon(String tablerId) {
      this.tablerId = tablerId;
      // Provenance: the vendored SVG this glyph is rasterised from. There is no bitmap icon set.
      this.resourceLocation = new ResourceLocation("augustus", "icons/tabler/" + tablerId + ".svg");
   }

   /** The Tabler identifier, i.e. the file name without its extension. */
   public String getTablerId() {
      return this.tablerId;
   }

   public ResourceLocation getResourceLocation() {
      return this.resourceLocation;
   }

   /** True when the artwork for this icon is present in the project. */
   public boolean isAvailable() {
      return IconManager.isAvailable(this.tablerId);
   }

   public static LucideIcon forCategory(Categorys category) {
      if (category == null) {
         return LAYOUT_GRID;
      }
      return CATEGORY_MAP.getOrDefault(category, SLIDERS_HORIZONTAL);
   }

   /** Draws the icon centred at (centerX, centerY) with a crisp subtle shadow for contrast. */
   public void drawCentered(float centerX, float centerY, float size, int argb) {
      draw(centerX - size * 0.5F, centerY - size * 0.5F, size, size, argb, true);
   }

   /** Draws the icon centred at (centerX, centerY) with optional drop shadow. */
   public void drawCentered(float centerX, float centerY, float size, int argb, boolean withShadow) {
      draw(centerX - size * 0.5F, centerY - size * 0.5F, size, size, argb, withShadow);
   }

   /** Draws the icon top-left at (x, y) with optional subtle contrast shadow. */
   public void draw(float x, float y, float size, int argb, boolean withShadow) {
      draw(x, y, size, size, argb, withShadow);
   }

   public void draw(float x, float y, float width, float height, int argb, boolean withShadow) {
      if (withShadow) {
         IconManager.drawCenteredWithShadow(this.tablerId, x + width * 0.5F, y + height * 0.5F,
               Math.min(width, height), argb);
      } else {
         IconManager.draw(this.tablerId, x, y, width, height, argb);
      }
   }

   /** Convenience for callers that only have an icon id and want the reusable library directly. */
   public static void drawId(String tablerId, float x, float y, float size, int argb) {
      IconManager.draw(tablerId, x, y, size, argb);
   }
}
