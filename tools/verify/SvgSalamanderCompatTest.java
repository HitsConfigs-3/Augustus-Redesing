import com.kitfox.svg.SVGDiagram;
import com.kitfox.svg.SVGUniverse;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless compatibility test for SVG Salamander in this project's environment.
 *
 * <p>Answers the question "does SVG Salamander actually work here, on Java 8, for the Tabler
 * artwork?" before any of it is wired into the client, and proves the three things the icon system
 * depends on: that a Tabler SVG rasterises to a non-empty image, that transparency survives, and
 * that the edges are genuinely antialiased rather than hard-stepped.
 *
 * <p>It also exercises the mandatory {@code currentColor} substitution: all 53 vendored Tabler
 * files use {@code stroke="currentColor"}, which SVG Salamander cannot resolve (there is no CSS
 * cascade to resolve it against), so a verbatim parse renders nothing. The substitution is done on
 * the raw text before parsing.
 *
 * Run:
 *   javac -cp &lt;svg-salamander.jar&gt; -d build/svgprobe tools/verify/SvgSalamanderCompatTest.java
 *   java -cp "build/svgprobe;&lt;svg-salamander.jar&gt;" SvgSalamanderCompatTest
 */
public final class SvgSalamanderCompatTest {

   private static int checks;
   private static int failures;

   private static void check(boolean ok, String what) {
      checks++;
      if (!ok) {
         failures++;
      }
      System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
   }

   public static void main(String[] args) throws Exception {
      System.out.println("SVG Salamander compatibility test");
      System.out.println("=================================");
      System.out.println("java.version = " + System.getProperty("java.version"));
      System.out.println("headless     = " + System.getProperty("java.awt.headless"));
      System.out.println();

      String dir = args.length > 0 ? args[0] : "src/main/resources/assets/augustus/icons/tabler";
      List<String> names = new ArrayList<String>();
      for (java.io.File f : new java.io.File(dir).listFiles()) {
         if (f.getName().endsWith(".svg")) {
            names.add(f.getName().substring(0, f.getName().length() - 4));
         }
      }
      java.util.Collections.sort(names);
      System.out.println("tabler icons on disk: " + names.size() + " in " + dir);
      if (names.isEmpty()) {
         System.out.println("no SVGs found; pass the icon directory as argv[0]");
         System.exit(2);
      }

      // ---------------------------------------------------------------- 1. currentColor handling
      // Discovered by measurement: Salamander does NOT drop `currentColor`, it paints it BLACK.
      // That matters for this design, because tinting is done by GL colour modulation at draw time
      // (srcRGB * colour), so a black mask can never be tinted - black multiplied by any colour is
      // still black. Substituting currentColor for white first is therefore mandatory, but for the
      // RGB reason, not because the stroke would be missing.
      String verbatim = new String(
            Files.readAllBytes(Paths.get(dir, "settings.svg")), StandardCharsets.UTF_8);
      check(verbatim.contains("currentColor"), "the vendored SVG uses currentColor as expected");
      BufferedImage verbatimImg = rasterise(verbatim, 32);
      int verbatimCoverage = coverage(verbatimImg);
      int[] verbatimRgb = averagePaintedRgb(verbatimImg);
      System.out.println("  verbatim currentColor: coverage = " + verbatimCoverage
            + " px, average painted RGB = " + verbatimRgb[0] + "," + verbatimRgb[1] + ","
            + verbatimRgb[2]);
      check(verbatimCoverage > 0, "currentColor is not dropped by the parser");
      check(verbatimRgb[0] < 40 && verbatimRgb[1] < 40 && verbatimRgb[2] < 40,
            "verbatim currentColor paints BLACK (" + verbatimRgb[0] + "," + verbatimRgb[1] + ","
                  + verbatimRgb[2] + "), which cannot be tinted by GL_MODULATE -> substitution "
                  + "to white is required");

      // ---------------------------------------------------------------- 2. substitution works
      String substituted = verbatim.replace("currentColor", "#FFFFFF");
      BufferedImage img32 = rasterise(substituted, 32);
      int cov32 = coverage(img32);
      System.out.println();
      System.out.println("settings.svg at 32px: " + img32.getWidth() + "x" + img32.getHeight()
            + ", painted " + cov32 + " px ("
            + String.format(java.util.Locale.ROOT, "%.1f", 100.0 * cov32 / (32.0 * 32.0))
            + "% of the box)");
      check(cov32 > 0, "the substituted SVG rasterises to a non-empty image");
      int[] subRgb = averagePaintedRgb(img32);
      System.out.println("  substituted painted RGB = " + subRgb[0] + "," + subRgb[1] + ","
            + subRgb[2]);
      check(subRgb[0] > 220 && subRgb[1] > 220 && subRgb[2] > 220,
            "the substituted mask is white, so GL_MODULATE can tint it to any colour");

      // ---------------------------------------------------------------- 3. transparency survives
      int transparentCorners = 0;
      int[][] corners = { { 0, 0 }, { 31, 0 }, { 0, 31 }, { 31, 31 } };
      for (int[] c : corners) {
         if (((img32.getRGB(c[0], c[1]) >>> 24) & 0xFF) == 0) {
            transparentCorners++;
         }
      }
      check(transparentCorners == 4,
            "all 4 corners are fully transparent, so alpha is preserved ("
                  + transparentCorners + "/4)");

      // ---------------------------------------------------------------- 4. antialiasing
      int[] hist = alphaHistogram(img32);
      int partial = hist[0]; // count of pixels with 0 < a < 255
      System.out.println("  alpha histogram: opaque=" + hist[2] + ", partial=" + partial
            + ", clear=" + hist[3]);
      check(partial > 40,
            "edges are antialiased: " + partial + " partially transparent pixels (hard-edged "
                  + "rasterisation would leave ~0)");

      // ---------------------------------------------------------------- 5. every icon rasterises
      System.out.println();
      System.out.println("Rasterising every vendored icon at three sizes");
      SVGUniverse universe = new SVGUniverse();
      int failuresHere = 0;
      int blank = 0;
      int totalPartial = 0;
      for (String name : names) {
         String text = new String(Files.readAllBytes(Paths.get(dir, name + ".svg")),
               StandardCharsets.UTF_8).replace("currentColor", "#FFFFFF");
         for (int size : new int[] { 12, 16, 24 }) {
            try {
               BufferedImage img = rasterise(universe, text, name, size);
               int cov = coverage(img);
               if (cov == 0) {
                  blank++;
                  System.out.println("        BLANK: " + name + " @" + size);
               }
               totalPartial += alphaHistogram(img)[0];
            } catch (Exception e) {
               failuresHere++;
               System.out.println("        THREW: " + name + " @" + size + " -> " + e);
            }
         }
      }
      System.out.println("  " + (names.size() * 3) + " rasterisations, " + failuresHere
            + " threw, " + blank + " blank, " + totalPartial + " partial-alpha pixels total");
      check(failuresHere == 0, "no icon threw while rasterising");
      check(blank == 0, "no icon rendered blank");

      // ---------------------------------------------------------------- 6. size responsiveness
      System.out.println();
      BufferedImage small = rasterise(substituted, 12);
      BufferedImage large = rasterise(substituted, 48);
      int cSmall = coverage(small);
      int cLarge = coverage(large);
      System.out.println("  coverage 12px=" + cSmall + "  48px=" + cLarge
            + "  ratio=" + String.format(java.util.Locale.ROOT, "%.1f", (double) cLarge / cSmall));
      check(cLarge > cSmall * 10, "coverage scales with the requested size (vector, not upscaled)");
      check(((small.getRGB(0, 0) >>> 24) & 0xFF) == 0, "the small render is still transparent");

      // ---------------------------------------------------------------- 7. cost per rasterisation
      System.out.println();
      long t0 = System.nanoTime();
      int reps = 200;
      for (int i = 0; i < reps; i++) {
         rasterise(substituted, 16);
      }
      long perMicros = (System.nanoTime() - t0) / 1000L / reps;
      System.out.println("  fresh parse+rasterise at 16px: ~" + perMicros + " microseconds/icon");
      System.out.println("  -> caching is mandatory; converting per frame would be far too slow");

      System.out.println();
      System.out.println(checks + " checks, " + failures + " failure(s)");
      System.out.println(failures == 0 ? "ALL CHECKS PASSED" : "FAILED");
      System.exit(failures == 0 ? 0 : 1);
   }

   // ---------------------------------------------------------------------------------------------

   private static BufferedImage rasterise(String svgText, int size) throws Exception {
      return rasterise(new SVGUniverse(), svgText, "icon", size);
   }

   private static BufferedImage rasterise(SVGUniverse universe, String svgText, String key, int size)
         throws Exception {
      Reader reader = new StringReader(svgText);
      java.net.URI uri = universe.loadSVG(reader, key + "-" + size + ".svg");
      SVGDiagram diagram = universe.getDiagram(uri);
      if (diagram == null) {
         throw new IllegalStateException("getDiagram returned null");
      }
      diagram.setIgnoringClipHeuristic(true);

      BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      try {
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
         g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
               RenderingHints.VALUE_STROKE_PURE);
         // Tabler's artwork grid is 24x24; scale it onto the requested pixel box.
         double k = size / 24.0D;
         g.scale(k, k);
         diagram.render(g);
      } finally {
         g.dispose();
      }
      return img;
   }

   private static int coverage(BufferedImage img) {
      int n = 0;
      for (int y = 0; y < img.getHeight(); y++) {
         for (int x = 0; x < img.getWidth(); x++) {
            if (((img.getRGB(x, y) >>> 24) & 0xFF) > 8) {
               n++;
            }
         }
      }
      return n;
   }

   /** Mean RGB over the painted (alpha &gt; 200) pixels, to tell a white mask from a black one. */
   private static int[] averagePaintedRgb(BufferedImage img) {
      long r = 0;
      long g = 0;
      long b = 0;
      long n = 0;
      for (int y = 0; y < img.getHeight(); y++) {
         for (int x = 0; x < img.getWidth(); x++) {
            int argb = img.getRGB(x, y);
            if (((argb >>> 24) & 0xFF) > 200) {
               r += (argb >> 16) & 0xFF;
               g += (argb >> 8) & 0xFF;
               b += argb & 0xFF;
               n++;
            }
         }
      }
      if (n == 0) {
         return new int[] { -1, -1, -1 };
      }
      return new int[] { (int) (r / n), (int) (g / n), (int) (b / n) };
   }

   /** {partial (0&lt;a&lt;255), nearlyClear, opaque, fullyClear} */
   private static int[] alphaHistogram(BufferedImage img) {
      int partial = 0;
      int barely = 0;
      int opaque = 0;
      int clear = 0;
      for (int y = 0; y < img.getHeight(); y++) {
         for (int x = 0; x < img.getWidth(); x++) {
            int a = (img.getRGB(x, y) >>> 24) & 0xFF;
            if (a == 0) {
               clear++;
            } else if (a == 255) {
               opaque++;
            } else {
               partial++;
               if (a < 24) {
                  barely++;
               }
            }
         }
      }
      return new int[] { partial, barely, opaque, clear };
   }
}
