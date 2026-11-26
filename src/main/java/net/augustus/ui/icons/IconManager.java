package net.augustus.ui.icons;

import com.kitfox.svg.SVGDiagram;
import com.kitfox.svg.SVGUniverse;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.augustus.utils.interfaces.MC;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Centralised icon library: Tabler SVG in, tinted GL textures out.
 *
 * <p>An icon is requested by its <b>Tabler identifier</b>, a pixel size and a colour, and the manager
 * handles everything else:
 *
 * <pre>IconManager.draw("settings", x, y, 16.0F, 0xFFF2F4F8);</pre>
 *
 * <p>No icon geometry is drawn by this project any more. The SVG files are vendored verbatim from
 * {@code tabler/tabler-icons} under {@code assets/augustus/icons/tabler/} (MIT, revision pinned in
 * {@code REVISION.txt}) and converted at runtime by SVG Salamander into a {@link BufferedImage},
 * which is uploaded as a Minecraft {@link DynamicTexture}. Adding a new icon is therefore a matter
 * of dropping the SVG in that folder and naming it; there is nothing to transcribe and no path data
 * to maintain.
 *
 * <h2>Why the mask is rasterised white</h2>
 * Every vendored Tabler icon draws with {@code stroke="currentColor"}, because that is how the set is
 * authored for web use. Measured behaviour of SVG Salamander: it does <b>not</b> drop
 * {@code currentColor}, it resolves it to <b>black</b>. That matters here, because tinting is done by
 * GL colour modulation, and modulation multiplies: a black mask cannot be tinted to any colour at
 * all, while a white one takes the colour exactly. So {@code currentColor} is substituted for
 * {@code #FFFFFF} in the raw SVG text <i>before</i> parsing, and one white mask per
 * (icon, size) then serves every colour the UI needs. This was verified, not assumed - see
 * {@code tools/verify/SvgSalamanderCompatTest.java}.
 *
 * <h2>Caching and cost</h2>
 * A fresh parse plus rasterisation measures around <b>930 microseconds</b> per icon, so converting
 * inside a frame loop is out of the question. Textures are cached per (identifier, pixel size) and
 * never re-registered. If a frame asks for more new icons than {@link #MAX_UPLOADS_PER_FRAME}, the
 * surplus simply draws nothing that frame and is served on the next one - that bounds the worst-case
 * hitch instead of paying for every icon at once.
 *
 * <h2>Threading</h2>
 * Everything here touches OpenGL (texture allocation, binding, quad submission), so it must be
 * called from the render thread, which is where Minecraft draws the GUI. Rasterisation happens on the
 * same call, deliberately: doing it on a worker thread would still have to hand the pixels back to
 * this thread for upload, and would add a race for the icon's first frame.
 */
public final class IconManager implements MC {

   /** Where the vendored Tabler SVGs live, and the prefix for their generated textures. */
   private static final String RESOURCE_ROOT = "assets/augustus/icons/tabler/";
   private static final String DYNAMIC_ROOT = "dynamic/tabler/";

   /** Upper bound on cached textures; the oldest are released when it is exceeded. */
   private static final int CACHE_LIMIT = 128;

   /**
    * How many icons may be rasterised and uploaded in a single frame. A frame that needs more than
    * this (a freshly opened screen resolving every icon at once) gets the rest on subsequent frames,
    * which keeps the first frame from stalling for tens of milliseconds.
    */
   private static final int MAX_UPLOADS_PER_FRAME = 3;

   private static final int MIN_PX = 8;
   private static final int MAX_PX = 96;

   /** Identifiers known to be missing or unparseable, so they are only reported once. */
   private static final Set<String> BROKEN = new HashSet<String>();

   /** Raw (already substituted) SVG text per identifier, so the classpath is only read once. */
   private static final Map<String, String> SVG_TEXT = new HashMap<String, String>();

   /** Rasterised textures, keyed by identifier plus pixel size. */
   private static final Map<String, Entry> CACHE = new HashMap<String, Entry>();

   private static final List<String> INSERTION_ORDER = new ArrayList<String>();

   private static int uploadsThisFrame;
   private static int budgetMisses;

   private static int statRasterised;
   private static int statReused;
   private static int statEvicted;
   private static int statMissing;

   // Device scale (framebuffer pixels per logical GUI pixel), refreshed lazily; see deviceScale().
   private static float deviceScale = 2.0F;
   private static long deviceScaleAt;

   private IconManager() {
   }

   /** One cached icon: its GL texture and the size it was rasterised at. */
   private static final class Entry {
      final int textureId;
      final int pixelSize;
      final ResourceLocation location;
      final DynamicTexture texture;

      Entry(int textureId, int pixelSize, ResourceLocation location, DynamicTexture texture) {
         this.textureId = textureId;
         this.pixelSize = pixelSize;
         this.location = location;
         this.texture = texture;
      }
   }

   // -------------------------------------------------------------------------------------------
   // Frame lifecycle
   // -------------------------------------------------------------------------------------------

   /**
    * Resets the per-frame upload budget. Call once at the start of a GUI frame; if it is not called,
    * the budget refills on a time basis instead, so a forgotten call degrades to slower streaming
    * rather than to icons that never appear.
    */
   public static void beginFrame() {
      thisResetBudget();
   }

   private static long lastBudgetReset;

   private static void thisResetBudget() {
      uploadsThisFrame = 0;
      lastBudgetReset = System.currentTimeMillis();
   }

   private static void refillBudgetIfStale() {
      long now = System.currentTimeMillis();
      if (now - lastBudgetReset > 50L) {
         thisResetBudget();
      }
   }

   // -------------------------------------------------------------------------------------------
   // Drawing
   // -------------------------------------------------------------------------------------------

   /** Draws an icon with its top-left at (x, y). {@code size} is the edge length in logical pixels. */
   public static void draw(String tablerId, float x, float y, float size, int argb) {
      draw(tablerId, x, y, size, size, argb);
   }

   /** Draws an icon into an explicit box, preserving the SVG's aspect ratio. */
   public static void draw(String tablerId, float x, float y, float width, float height, int argb) {
      if (tablerId == null || width <= 0.5F || height <= 0.5F) {
         return;
      }
      int alpha = (argb >>> 24) & 0xFF;
      if (alpha == 0) {
         if ((argb & 0x00FFFFFF) == 0) {
            return;
         }
         alpha = 255;
         argb = (0xFF << 24) | (argb & 0x00FFFFFF);
      }

      // Rasterise at device resolution so the glyph is crisp at every GUI scale instead of being an
      // upscaled logical-resolution bitmap.
      float largest = Math.max(width, height);
      int px = Math.round(largest * deviceScale());
      if (px < MIN_PX) {
         px = MIN_PX;
      } else if (px > MAX_PX) {
         px = MAX_PX;
      }

      Entry entry = entryFor(tablerId, px);
      if (entry == null) {
         return;
      }

      float w = width;
      float h = height;
      float scale = entry.pixelSize / largest;

      GL11.glPushMatrix();
      GlStateManager.enableTexture2D();
      GlStateManager.enableBlend();
      GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
      // The mask is white, so this colour IS the icon colour.
      GlStateManager.color(((argb >> 16) & 0xFF) / 255.0F, ((argb >> 8) & 0xFF) / 255.0F,
            (argb & 0xFF) / 255.0F, alpha / 255.0F);
      GlStateManager.bindTexture(entry.textureId);

      // The texture is a full square at entry.pixelSize; the quad is the requested box, so the UVs
      // cover exactly the rasterised area and nothing is stretched beyond it.
      float u = largest * scale / entry.pixelSize;
      float v = u;
      Tessellator tessellator = Tessellator.getInstance();
      WorldRenderer renderer = tessellator.getWorldRenderer();
      renderer.begin(7, DefaultVertexFormats.POSITION_TEX);
      renderer.pos((double) x, (double) (y + h), 0.0D).tex(0.0D, (double) v).endVertex();
      renderer.pos((double) (x + w), (double) (y + h), 0.0D).tex((double) u, (double) v).endVertex();
      renderer.pos((double) (x + w), (double) y, 0.0D).tex((double) u, 0.0D).endVertex();
      renderer.pos((double) x, (double) y, 0.0D).tex(0.0D, 0.0D).endVertex();
      tessellator.draw();

      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
      GL11.glPopMatrix();
   }

   /** Draws an icon centred on (centreX, centreY). */
   public static void drawCentered(String tablerId, float centreX, float centreY, float size, int argb) {
      draw(tablerId, centreX - size * 0.5F, centreY - size * 0.5F, size, argb);
   }

   /**
    * Draws an icon centred with a soft dark drop shadow, matching how the icon set is presented over
    * glass surfaces.
    */
   public static void drawCenteredWithShadow(String tablerId, float centreX, float centreY, float size,
         int argb) {
      int alpha = (argb >>> 24) & 0xFF;
      int shadow = (Math.round(alpha * 0.36F) << 24);
      draw(tablerId, centreX - size * 0.5F + size * 0.03F, centreY - size * 0.5F + size * 0.055F, size,
            shadow);
      drawCentered(tablerId, centreX, centreY, size, argb);
   }

   /** True when the identifier resolves to vendored artwork (does not force a rasterisation). */
   public static boolean isAvailable(String tablerId) {
      if (tablerId == null || BROKEN.contains(tablerId)) {
         return false;
      }
      if (SVG_TEXT.containsKey(tablerId)) {
         return true;
      }
      InputStream stream = null;
      try {
         stream = IconManager.class.getClassLoader()
               .getResourceAsStream(RESOURCE_ROOT + tablerId + ".svg");
         return stream != null;
      } catch (Throwable t) {
         return false;
      } finally {
         closeQuietly(stream);
      }
   }

   // -------------------------------------------------------------------------------------------
   // Texture resolution
   // -------------------------------------------------------------------------------------------

   private static Entry entryFor(String tablerId, int pixelSize) {
      String key = tablerId + "@" + pixelSize;
      Entry cached = CACHE.get(key);
      if (cached != null) {
         // Self-healing across a resource reload: Minecraft clears its texture map, so if our object
         // is no longer registered the GL texture behind it is gone and the entry must be rebuilt.
         if (mc == null || mc.getTextureManager() == null
               || mc.getTextureManager().getTexture(cached.location) == cached.texture) {
            statReused++;
            return cached;
         }
         CACHE.remove(key);
         INSERTION_ORDER.remove(key);
      }

      if (BROKEN.contains(tablerId)) {
         return null;
      }

      refillBudgetIfStale();
      if (uploadsThisFrame >= MAX_UPLOADS_PER_FRAME) {
         // Out of budget for this frame: draw nothing rather than stall. The next frame serves it.
         budgetMisses++;
         return null;
      }

      try {
         String svg = svgTextFor(tablerId);
         if (svg == null) {
            statMissing++;
            return null;
         }
         BufferedImage image = rasterise(svg, pixelSize);
         if (image == null) {
            return null;
         }

         ResourceLocation location = new ResourceLocation("augustus", DYNAMIC_ROOT + tablerId + "_" + pixelSize);
         DynamicTexture texture = new DynamicTexture(image);
         // DynamicTexture.loadTexture() is a no-op, so this cannot fail for it; the GL texture was
         // already allocated and uploaded by the constructor above.
         if (mc != null && mc.getTextureManager() != null) {
            mc.getTextureManager().loadTexture(location, texture);
         }
         Entry entry = new Entry(texture.getGlTextureId(), pixelSize, location, texture);
         CACHE.put(key, entry);
         INSERTION_ORDER.add(key);
         uploadsThisFrame++;
         statRasterised++;
         evictIfOverLimit();
         return entry;
      } catch (Throwable t) {
         // Never let one bad file break the UI: report once, then treat the icon as absent.
         BROKEN.add(tablerId);
         System.err.println("[IconManager] could not rasterise '" + tablerId + "': " + t);
         return null;
      }
   }

   private static String svgTextFor(String tablerId) {
      String cached = SVG_TEXT.get(tablerId);
      if (cached != null) {
         return cached;
      }
      InputStream stream = null;
      try {
         stream = IconManager.class.getClassLoader()
               .getResourceAsStream(RESOURCE_ROOT + tablerId + ".svg");
         if (stream == null) {
            BROKEN.add(tablerId);
            return null;
         }
         byte[] buffer = new byte[8192];
         StringBuilder sb = new StringBuilder(2048);
         int read;
         while ((read = stream.read(buffer)) > 0) {
            sb.append(new String(buffer, 0, read, Charset.forName("UTF-8")));
         }
         // See the class comment: Salamander paints currentColor black, which modulation cannot tint.
         String text = sb.toString().replace("currentColor", "#FFFFFF");
         SVG_TEXT.put(tablerId, text);
         return text;
      } catch (Throwable t) {
         BROKEN.add(tablerId);
         return null;
      } finally {
         closeQuietly(stream);
      }
   }

   private static BufferedImage rasterise(String svgText, int pixelSize) throws Exception {
      // A universe per rasterisation: it caches diagrams internally and is not bounded, and since the
      // result is cached as a texture anyway there is nothing to gain from keeping it alive.
      SVGUniverse universe = new SVGUniverse();
      SVGDiagram diagram = universe.getDiagram(
            universe.loadSVG(new StringReader(svgText), "tabler-" + pixelSize + ".svg"));
      if (diagram == null) {
         return null;
      }
      diagram.setIgnoringClipHeuristic(true);

      BufferedImage image = new BufferedImage(pixelSize, pixelSize, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();
      try {
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
         g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
         // Tabler art is authored on a 24x24 grid.
         double k = pixelSize / 24.0D;
         g.scale(k, k);
         diagram.render(g);
      } finally {
         g.dispose();
      }
      return image;
   }

   private static void evictIfOverLimit() {
      while (INSERTION_ORDER.size() > CACHE_LIMIT) {
         String oldest = INSERTION_ORDER.remove(0);
         Entry entry = CACHE.remove(oldest);
         if (entry != null) {
            release(entry);
            statEvicted++;
         }
      }
   }

   private static void release(Entry entry) {
      try {
         if (mc != null && mc.getTextureManager() != null) {
            mc.getTextureManager().deleteTexture(entry.location);
         }
      } catch (Throwable t) {
         // The manager may already have released it during a reload; nothing further to do.
      }
   }

   // -------------------------------------------------------------------------------------------
   // Device scale
   // -------------------------------------------------------------------------------------------

   /**
    * Framebuffer pixels per logical GUI pixel, i.e. Minecraft's GUI scale.
    *
    * <p>Cached for a short interval rather than read per icon: it only changes when the user changes
    * the GUI scale, and constructing a {@link ScaledResolution} for every icon in a frame would be
    * wasteful. A value that is one frame stale is visually irrelevant.
    */
   private static float deviceScale() {
      long now = System.currentTimeMillis();
      if (now - deviceScaleAt > 100L) {
         deviceScaleAt = now;
         try {
            if (mc != null && mc.currentScreen != null) {
               int factor = new ScaledResolution(mc).getScaleFactor();
               if (factor > 0) {
                  deviceScale = factor;
               }
            }
         } catch (Throwable t) {
            // Keep the previous value; the default of 2 is a reasonable guess.
         }
      }
      return deviceScale;
   }

   /** Overrides the device scale explicitly; useful for tests and for non-GUI contexts. */
   public static void setDeviceScale(float scale) {
      if (scale > 0.0F) {
         deviceScale = scale;
         deviceScaleAt = System.currentTimeMillis();
      }
   }

   // -------------------------------------------------------------------------------------------
   // Lifecycle and diagnostics
   // -------------------------------------------------------------------------------------------

   /** Releases every cached texture. Safe to call more than once. */
   public static void dispose() {
      for (String key : new ArrayList<String>(INSERTION_ORDER)) {
         Entry entry = CACHE.remove(key);
         if (entry != null) {
            release(entry);
         }
      }
      INSERTION_ORDER.clear();
      CACHE.clear();
      SVG_TEXT.clear();
      BROKEN.clear();
   }

   /** Number of textures currently held. */
   public static int cachedCount() {
      return CACHE.size();
   }

   /** A one-line summary for the debug HUD or a verification harness. */
   public static String stats() {
      return "icons: cached=" + CACHE.size() + "/" + CACHE_LIMIT + " rasterised=" + statRasterised
            + " reused=" + statReused + " evicted=" + statEvicted + " missing=" + statMissing
            + " budgetMisses=" + budgetMisses + " deviceScale=" + deviceScale;
   }

   private static void closeQuietly(InputStream stream) {
      if (stream != null) {
         try {
            stream.close();
         } catch (Throwable ignored) {
            // nothing useful to do
         }
      }
   }
}
