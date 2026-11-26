package net.augustus.ui.modern.font;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import net.augustus.render.BufferedImageTextureUtil;
import net.augustus.utils.render.GlStateSaver;
import net.augustus.utils.render.RenderSystemAssetLoader;
import net.augustus.utils.shader.ShaderProgram;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * Signed Distance Field (SDF) font atlas & GLSL 120 renderer adapted from Solana Render's MSDF
 * typography pipeline for OpenAugustus's Inter font family (Medium 500, SemiBold 600, Bold 700).
 */
public final class SdfFontAtlas {
   private static final int[] MC_COLORS = new int[32];
   private static final GlStateSaver STATE_SAVER = GlStateSaver.create();
   private static final Gson GSON = new Gson();
   private static final Map<String, SdfFontAtlas> ATLAS_CACHE = new ConcurrentHashMap<>();

   private static ShaderProgram sdfShader;
   private static boolean shaderInitialized = false;
   private static boolean shaderFailed = false;

   static {
      for (int i = 0; i < 32; i++) {
         int n = (i >> 3 & 1) * 85;
         int r = (i >> 2 & 1) * 170 + n;
         int g = (i >> 1 & 1) * 170 + n;
         int b = (i & 1) * 170 + n;
         if (i == 6) {
            r += 85;
         }
         if (i >= 16) {
            r /= 4;
            g /= 4;
            b /= 4;
         }
         MC_COLORS[i] = 0xFF000000 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
      }
   }

   private final String resourceBase;
   private final float distanceRange;
   private final float atlasWidth;
   private final float atlasHeight;
   private final float thicknessBias;
   private final float tabularDigitAdvance;
   private final Map<Integer, GlyphMetric> glyphs = new HashMap<>();
   private final ConcurrentHashMap<Long, Float> widthCache = new ConcurrentHashMap<>();
   private int glTextureId = -1;
   private boolean loadAttempted = false;

   private SdfFontAtlas(String resourceBase, float thicknessBias) {
      this.resourceBase = resourceBase;
      this.thicknessBias = thicknessBias;
      float range = 8.0F;
      float aw = 1024.0F;
      float ah = 1024.0F;

      try (InputStream in = SdfFontAtlas.class.getResourceAsStream("/assets/augustus/fonts/sdf/" + resourceBase + ".json")) {
         if (in != null) {
            AtlasJson data = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), AtlasJson.class);
            if (data != null && data.atlas != null) {
               range = data.atlas.distanceRange > 0.0F ? data.atlas.distanceRange : 8.0F;
               aw = data.atlas.width > 0.0F ? data.atlas.width : 1024.0F;
               ah = data.atlas.height > 0.0F ? data.atlas.height : 1024.0F;
            }
            if (data != null && data.glyphs != null) {
               for (GlyphJson g : data.glyphs) {
                  this.glyphs.put(g.unicode, new GlyphMetric(g, aw, ah));
               }
            }
         }
      } catch (Exception e) {
         e.printStackTrace();
      }

      float maxDigitAdv = 0.62F;
      for (int c = '0'; c <= '9'; c++) {
         GlyphMetric dm = this.glyphs.get(c);
         if (dm != null && dm.advance > maxDigitAdv) {
            maxDigitAdv = dm.advance;
         }
      }
      this.tabularDigitAdvance = maxDigitAdv;
      this.distanceRange = range;
      this.atlasWidth = aw;
      this.atlasHeight = ah;
   }

   public static SdfFontAtlas get(String resourceBase, float thicknessBias) {
      return ATLAS_CACHE.computeIfAbsent(resourceBase + ":" + thicknessBias, k -> new SdfFontAtlas(resourceBase, thicknessBias));
   }

   private static boolean ensureShader() {
      if (shaderInitialized) {
         return true;
      }
      if (shaderFailed) {
         return false;
      }
      try {
         String vert = RenderSystemAssetLoader.loadAssetString("impl/font/sdf_font.vert");
         String frag = RenderSystemAssetLoader.loadAssetString("impl/font/sdf_font.frag");
         sdfShader = new ShaderProgram(vert, frag);
         shaderInitialized = true;
         return true;
      } catch (Throwable t) {
         shaderFailed = true;
         t.printStackTrace();
         return false;
      }
   }

   private int ensureTexture() {
      if (this.glTextureId > 0) {
         return this.glTextureId;
      }
      if (this.loadAttempted) {
         return -1;
      }
      this.loadAttempted = true;
      try (InputStream in = SdfFontAtlas.class.getResourceAsStream("/assets/augustus/fonts/sdf/" + this.resourceBase + ".png")) {
         if (in != null) {
            BufferedImage img = ImageIO.read(in);
            if (img != null) {
               this.glTextureId = BufferedImageTextureUtil.uploadTexture(img);
            }
         }
      } catch (Exception e) {
         e.printStackTrace();
      }
      return this.glTextureId;
   }

   public boolean supportsAllGlyphs(String text) {
      if (text == null || text.isEmpty()) {
         return true;
      }
      boolean skipNext = false;
      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (skipNext) {
            skipNext = false;
            continue;
         }
         if (c == '\u00a7') {
            skipNext = true;
            continue;
         }
         if (c == '\n' || c == '\r' || c == '\t') {
            continue;
         }
         if (!this.glyphs.containsKey((int)c)) {
            return false;
         }
      }
      return true;
   }

   public float getStringWidth(String text, float size) {
      return this.getStringWidth(text, size, 0.0F, false);
   }

   public float getStringWidth(String text, float size, float letterSpacing, boolean tabularNums) {
      if (text == null || text.isEmpty()) {
         return 0.0F;
      }
      if (letterSpacing == 0.0F && !tabularNums) {
         long key = ((long)text.hashCode() & 0xFFFFFFFFL) ^ ((long)Float.floatToIntBits(size) << 32);
         Float cached = this.widthCache.get(key);
         if (cached != null) {
            return cached;
         }
         float w = this.computeStringWidthInternal(text, size, 0.0F, false);
         if (this.widthCache.size() > 4096) {
            this.widthCache.clear();
         }
         this.widthCache.put(key, w);
         return w;
      }
      return this.computeStringWidthInternal(text, size, letterSpacing, tabularNums);
   }

   private float computeStringWidthInternal(String text, float size, float letterSpacing, boolean tabularNums) {
      float w = 0.0F;
      boolean skipNext = false;
      int visibleChars = 0;
      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (skipNext) {
            skipNext = false;
            continue;
         }
         if (c == '\u00a7') {
            skipNext = true;
            continue;
         }
         GlyphMetric gm = this.glyphs.get((int)c);
         if (gm != null) {
            if (visibleChars > 0 && letterSpacing != 0.0F) {
               w += letterSpacing;
            }
            float adv = tabularNums && c >= '0' && c <= '9' ? this.tabularDigitAdvance : gm.advance;
            w += adv * size;
            visibleChars++;
         }
      }
      return w;
   }

   public float getVisualHeight(float size) {
      return size * 0.90F;
   }

   public float drawString(String text, float x, float y, float size, int argb, int shadowArgb) {
      return this.drawString(text, x, y, size, argb, shadowArgb, 0.0F, false);
   }

   public float drawString(String text, float x, float y, float size, int argb, int shadowArgb, float letterSpacing, boolean tabularNums) {
      return this.drawRunInternal(text, x, y, size, argb, argb, argb, false, shadowArgb, letterSpacing, tabularNums);
   }

   public float drawGradientString(
      String text,
      float x,
      float y,
      float size,
      int leftArgb,
      int midArgb,
      int rightArgb,
      int shadowArgb,
      float letterSpacing
   ) {
      return this.drawRunInternal(text, x, y, size, leftArgb, midArgb, rightArgb, true, shadowArgb, letterSpacing, false);
   }

   private float drawRunInternal(
      String text,
      float x,
      float y,
      float size,
      int argb,
      int midArgb,
      int rightArgb,
      boolean gradient,
      int shadowArgb,
      float letterSpacing,
      boolean tabularNums
   ) {
      if (text == null || text.isEmpty()) {
         return x;
      }
      int texId = this.ensureTexture();
      if (texId <= 0 || !ensureShader()) {
         return x;
      }

      int baseA = (argb >>> 24) & 255;
      if (baseA == 0 && (argb & 0x00FFFFFF) != 0) {
         baseA = 255;
      }
      float totalW = gradient ? Math.max(1.0F, this.getStringWidth(text, size, letterSpacing, tabularNums)) : 1.0F;

      int prevBlendSrc = GlStateManager.getBlendSrcFactor();
      int prevBlendDst = GlStateManager.getBlendDstFactor();
      int prevBlendSrcAlpha = GlStateManager.getBlendSrcFactorAlpha();
      int prevBlendDstAlpha = GlStateManager.getBlendDstFactorAlpha();
      int prevAlphaFunc = GlStateManager.getAlphaFunc();
      float prevAlphaRef = GlStateManager.getAlphaRef();

      STATE_SAVER.setBlend(true);
      STATE_SAVER.setAlpha(false);
      STATE_SAVER.setDepth(false);
      STATE_SAVER.setCull(false);
      STATE_SAVER.setLighting(false);
      STATE_SAVER.setTexture2D(true);

      GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
      int prevActiveTex = GlStateManager.getActiveTextureUnit();
      GlStateManager.setActiveTexture(33984);
      int prevTex = GlStateManager.getBoundTexture();
      GlStateManager.bindTexture(texId);

      float sr = (float)(shadowArgb >> 16 & 255) / 255.0F;
      float sg = (float)(shadowArgb >> 8 & 255) / 255.0F;
      float sb = (float)(shadowArgb & 255) / 255.0F;
      float sa = (float)(shadowArgb >>> 24 & 255) / 255.0F;

      sdfShader.bind();
      sdfShader.setUniform1i("u_tex", 0);
      sdfShader.setUniform1f("u_range", this.distanceRange);
      sdfShader.setUniform2f("u_atlasSize", this.atlasWidth, this.atlasHeight);
      sdfShader.setUniform1f("u_thickness", this.thicknessBias);
      sdfShader.setUniform1f("u_softness", 0.52F);
      sdfShader.setUniform4f("u_shadowColor", sr, sg, sb, sa);

      float cursorX = x;
      float baselineY = y + size * 0.80F;
      int currentColor = (baseA << 24) | (argb & 0x00FFFFFF);

      Tessellator tessellator = Tessellator.getInstance();
      WorldRenderer wr = tessellator.getWorldRenderer();
      wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);

      boolean hasVertices = false;
      int visibleChars = 0;
      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c == '\u00a7' && i + 1 < text.length()) {
            int codeIdx = "0123456789abcdefklmnor".indexOf(Character.toLowerCase(text.charAt(i + 1)));
            if (codeIdx >= 0 && codeIdx < 16) {
               currentColor = (baseA << 24) | (MC_COLORS[codeIdx] & 0x00FFFFFF);
            } else if (codeIdx == 21) {
               currentColor = (baseA << 24) | (argb & 0x00FFFFFF);
            }
            i++;
            continue;
         }

         GlyphMetric gm = this.glyphs.get((int)c);
         if (gm == null) {
            continue;
         }
         if (visibleChars > 0 && letterSpacing != 0.0F) {
            cursorX += letterSpacing;
         }

         boolean isDigit = tabularNums && c >= '0' && c <= '9';
         float cellAdvance = isDigit ? this.tabularDigitAdvance : gm.advance;
         float centerNudge = isDigit ? (this.tabularDigitAdvance - gm.advance) * 0.5F * size : 0.0F;

         if (gm.hasQuad) {
            float gx = cursorX + centerNudge + gm.left * size;
            float gy = baselineY - gm.top * size;
            float gw = gm.width * size;
            float gh = gm.height * size;

            int leftC = currentColor;
            int rightC = currentColor;
            if (gradient) {
               float tL = clamp01((gx - x) / totalW);
               float tR = clamp01((gx + gw - x) / totalW);
               leftC = (baseA << 24) | (sampleThreeStopRgb(argb, midArgb, rightArgb, tL) & 0x00FFFFFF);
               rightC = (baseA << 24) | (sampleThreeStopRgb(argb, midArgb, rightArgb, tR) & 0x00FFFFFF);
            }

            int rL = (leftC >> 16) & 255;
            int gL = (leftC >> 8) & 255;
            int bL = leftC & 255;
            int aL = (leftC >>> 24) & 255;

            int rR = (rightC >> 16) & 255;
            int gR = (rightC >> 8) & 255;
            int bR = rightC & 255;
            int aR = (rightC >>> 24) & 255;

            wr.pos((double)gx, (double)(gy + gh), 0.0).tex((double)gm.minU, (double)gm.maxV).color(rL, gL, bL, aL).endVertex();
            wr.pos((double)(gx + gw), (double)(gy + gh), 0.0).tex((double)gm.maxU, (double)gm.maxV).color(rR, gR, bR, aR).endVertex();
            wr.pos((double)(gx + gw), (double)gy, 0.0).tex((double)gm.maxU, (double)gm.minV).color(rR, gR, bR, aR).endVertex();
            wr.pos((double)gx, (double)gy, 0.0).tex((double)gm.minU, (double)gm.minV).color(rL, gL, bL, aL).endVertex();
            hasVertices = true;
         }

         cursorX += cellAdvance * size;
         visibleChars++;
      }

      if (hasVertices) {
         tessellator.draw();
      } else {
         wr.finishDrawing();
         wr.reset();
      }

      sdfShader.unbind();
      GlStateManager.bindTexture(prevTex);
      GlStateManager.setActiveTexture(prevActiveTex);
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
      GlStateManager.alphaFunc(prevAlphaFunc, prevAlphaRef);
      GlStateManager.tryBlendFuncSeparate(prevBlendSrc, prevBlendDst, prevBlendSrcAlpha, prevBlendDstAlpha);
      STATE_SAVER.restoreTexture2D();
      STATE_SAVER.restoreLighting();
      STATE_SAVER.restoreCull();
      STATE_SAVER.restoreDepth();
      STATE_SAVER.restoreAlpha();
      STATE_SAVER.restoreBlend();

      return cursorX;
   }

   private static float clamp01(float v) {
      return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
   }

   private static int sampleThreeStopRgb(int c0, int c1, int c2, float t) {
      if (t <= 0.5F) {
         return lerpRgb(c0, c1, t * 2.0F);
      }
      return lerpRgb(c1, c2, (t - 0.5F) * 2.0F);
   }

   private static int lerpRgb(int a, int b, float t) {
      int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
      int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
      int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
      return (r << 16) | (g << 8) | bl;
   }

   private static final class GlyphMetric {
      final float advance;
      final float left;
      final float top;
      final float width;
      final float height;
      final float minU;
      final float maxU;
      final float minV;
      final float maxV;
      final boolean hasQuad;

      GlyphMetric(GlyphJson g, float atlasW, float atlasH) {
         this.advance = g.advance;
         if (g.planeBounds != null && g.atlasBounds != null) {
            this.hasQuad = true;
            this.left = g.planeBounds.left;
            this.top = g.planeBounds.top;
            this.width = g.planeBounds.right - g.planeBounds.left;
            this.height = g.planeBounds.top - g.planeBounds.bottom;
            this.minU = g.atlasBounds.left / atlasW;
            this.maxU = g.atlasBounds.right / atlasW;
            this.minV = 1.0F - g.atlasBounds.top / atlasH;
            this.maxV = 1.0F - g.atlasBounds.bottom / atlasH;
         } else {
            this.hasQuad = false;
            this.left = 0.0F;
            this.top = 0.0F;
            this.width = 0.0F;
            this.height = 0.0F;
            this.minU = 0.0F;
            this.maxU = 0.0F;
            this.minV = 0.0F;
            this.maxV = 0.0F;
         }
      }
   }

   private static final class AtlasJson {
      AtlasHeader atlas;
      MetricsHeader metrics;
      List<GlyphJson> glyphs;
   }

   private static final class AtlasHeader {
      @SerializedName("distanceRange")
      float distanceRange;
      float size;
      float width;
      float height;
   }

   private static final class MetricsHeader {
      float lineHeight;
      float ascender;
      float descender;
   }

   private static final class GlyphJson {
      int unicode;
      float advance;
      BoundsJson planeBounds;
      BoundsJson atlasBounds;
   }

   private static final class BoundsJson {
      float left;
      float bottom;
      float right;
      float top;
   }
}
