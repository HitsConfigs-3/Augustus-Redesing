package net.augustus.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.augustus.Augustus;
import net.augustus.config.JsonConfigFile;
import net.augustus.modules.render.CustomClientColor;
import net.augustus.ui.modern.render.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Minimalist dark-obsidian startup splash screen modeled after clean native client loaders:
 * centered white Augustus emblem + tracked "AUGUSTUS REDESING" wordmark above an accent progress bar
 * driven by real client initialization milestones.
 */
public final class StartupSplashRenderer {
   private static final int BG_COLOR = 0xFF121519;
   private static final int TRACK_COLOR = 0xFF252A32;
   private static final int FILL_COLOR = 0xFF7C3AED;

   private static final String MARK_RESOURCE = "assets/augustus/textures/menu/logo_mark.png";
   private static final String FONT_RESOURCE = "assets/augustus/fonts/inter-bold.ttf";
   private static final String WORDMARK = "AUGUSTUS REDESING";

   private static int cachedEarlyThemeColor = 0;
   private static boolean earlyThemeChecked = false;
   private static int lockupGlTex = -1;
   private static int lockupWidthPx;
   private static int lockupHeightPx;
   private static float displayedProgress = 0.0F;
   private static long lastRenderMs = 0L;
   private static long startTimeMs = System.currentTimeMillis();
   private static boolean disposed = false;

   private StartupSplashRenderer() {
   }

   /** Resets splash state and immediately paints the initial frame after Display creation. */
   public static void showInitial() {
      disposed = false;
      displayedProgress = 0.05F;
      startTimeMs = System.currentTimeMillis();
      lastRenderMs = 0L;
      ensureLockupUploaded();
      renderFrame(displayedProgress);
   }

   /**
    * Smoothly creeps progress inside heavy blocking startup loops (resource/texture/shader loading)
    * so the loading bar keeps moving continuously at ~50fps without overshooting before startup ends.
    */
   public static void pulse(float floorProgress, float ceilProgress) {
      if (disposed || lockupGlTex <= 0 || !Display.isCreated()) {
         return;
      }
      long now = System.currentTimeMillis();
      if (now - lastRenderMs < 18L) {
         return;
      }
      if (displayedProgress < floorProgress) {
         displayedProgress = floorProgress;
      } else if (displayedProgress < ceilProgress) {
         float step = Math.max(0.0035F, (ceilProgress - displayedProgress) * 0.075F);
         displayedProgress = Math.min(ceilProgress, displayedProgress + step);
      }
      renderFrame(displayedProgress);
   }

   /** Advances the functional progress bar smoothly to {@code targetProgress} (clamped to 0..1). */
   public static void advance(float targetProgress) {
      if (disposed || !Display.isCreated()) {
         return;
      }
      float clamped = Math.max(displayedProgress, Math.min(1.0F, targetProgress));
      ensureLockupUploaded();

      if (clamped - displayedProgress <= 0.01F) {
         displayedProgress = clamped;
         renderFrame(displayedProgress);
         return;
      }

      int steps = clamped >= 0.99F ? 10 : 7;
      float start = displayedProgress;
      for (int i = 1; i <= steps; i++) {
         float t = (float)i / (float)steps;
         float eased = 1.0F - (1.0F - t) * (1.0F - t);
         displayedProgress = start + (clamped - start) * eased;
         renderFrame(displayedProgress);
         if (i < steps) {
            try {
               Thread.sleep(8L);
            } catch (InterruptedException ignored) {
               Thread.currentThread().interrupt();
               break;
            }
         }
      }
      if (clamped >= 0.999F) {
         try {
            Thread.sleep(70L);
         } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
         }
      }
   }

   /** Frees the temporary lockup GL texture once startup completes and GuiMainMenu opens. */
   public static void dispose() {
      disposed = true;
      if (lockupGlTex > 0) {
         TextureUtil.deleteTexture(lockupGlTex);
         lockupGlTex = -1;
      }
   }

   private static void ensureLockupUploaded() {
      if (lockupGlTex > 0 || !Display.isCreated()) {
         return;
      }
      BufferedImage img = buildLockupImage();
      if (img == null) {
         return;
      }
      lockupWidthPx = img.getWidth();
      lockupHeightPx = img.getHeight();
      int texId = TextureUtil.glGenTextures();
      TextureUtil.uploadTextureImageAllocate(texId, img, true, false);
      GlStateManager.bindTexture(texId);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
      lockupGlTex = texId;
   }

   /**
    * Pre-renders the centered white emblem + crisp wide-tracked Inter Bold wordmark at 2x resolution
    * so curves and typography appear razor-sharp before Minecraft's font atlas is loaded.
    */
   private static BufferedImage buildLockupImage() {
      try {
         BufferedImage rawMark = loadMarkAsWhite();
         Font baseFont = loadInterBoldFont(68.0F);
         Map<TextAttribute, Object> attrs = new HashMap<>();
         attrs.put(TextAttribute.TRACKING, Float.valueOf(0.16F));
         Font trackedFont = baseFont.deriveFont(attrs);

         BufferedImage probe = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
         Graphics2D pg = probe.createGraphics();
         pg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
         pg.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
         pg.setFont(trackedFont);
         java.awt.font.FontRenderContext frc = pg.getFontRenderContext();
         java.awt.font.GlyphVector gv = trackedFont.createGlyphVector(frc, WORDMARK);
         java.awt.geom.Rectangle2D vb = gv.getVisualBounds();
         int textW = Math.max(1, (int)Math.ceil(vb.getWidth()));
         int textH = Math.max(1, (int)Math.ceil(vb.getHeight()));
         pg.dispose();

         int markH = Math.round((float)textH * 2.15F);
         int markW = rawMark != null
            ? Math.max(1, Math.round((float)markH * (float)rawMark.getWidth() / (float)Math.max(1, rawMark.getHeight())))
            : 0;
         int gap = rawMark != null ? 24 : 0;
         int padX = 6;
         int padY = 10;
         int contentH = Math.max(markH, textH);
         int width = padX * 2 + markW + gap + textW;
         int height = padY * 2 + contentH;

         BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
         Graphics2D g = out.createGraphics();
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
         g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
         g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
         g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

         g.setColor(new Color(18, 21, 25, 255));
         g.fillRect(0, 0, width, height);

         if (rawMark != null) {
            int markY = (height - markH) / 2;
            g.drawImage(rawMark, padX, markY, markW, markH, null);
         }

         g.setFont(trackedFont);
         g.setColor(new Color(248, 250, 252, 255));
         int textX = padX + markW + gap - (int)Math.round(vb.getX());
         // Align the visual center of uppercase glyphs horizontally with the mark's center.
         float textBaseline = (float)height * 0.5F - (float)(vb.getY() + vb.getHeight() * 0.5);
         g.drawString(WORDMARK, (float)textX, textBaseline);
         g.dispose();
         return out;
      } catch (Exception e) {
         return null;
      }
   }

   private static BufferedImage loadMarkAsWhite() {
      InputStream in = StartupSplashRenderer.class.getClassLoader().getResourceAsStream(MARK_RESOURCE);
      if (in == null) {
         return null;
      }
      try {
         BufferedImage src = ImageIO.read(in);
         if (src == null) {
            return null;
         }
         int w = src.getWidth();
         int h = src.getHeight();
         int minX = w;
         int minY = h;
         int maxX = -1;
         int maxY = -1;
         int[] px = src.getRGB(0, 0, w, h, null, 0, w);
         for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
               int argb = px[y * w + x];
               int a = (argb >>> 24) & 0xFF;
               if (a == 0) {
                  px[y * w + x] = 0;
                  continue;
               }
               if (x < minX) minX = x;
               if (x > maxX) maxX = x;
               if (y < minY) minY = y;
               if (y > maxY) maxY = y;
               int r = (argb >> 16) & 0xFF;
               int g = (argb >> 8) & 0xFF;
               int b = argb & 0xFF;
               float lum = (0.299F * r + 0.587F * g + 0.114F * b) / 255.0F;
               float verticalFade = 1.0F - ((float)y / (float)Math.max(1, h - 1));
               int tone = Math.max(190, Math.min(255, Math.round(196.0F + lum * 42.0F + verticalFade * 17.0F)));
               px[y * w + x] = (a << 24) | (tone << 16) | (tone << 8) | Math.min(255, tone + 2);
            }
         }
         BufferedImage white = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
         white.setRGB(0, 0, w, h, px, 0, w);
         if (maxX >= minX && maxY >= minY) {
            return white.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
         }
         return white;
      } catch (Exception ignored) {
         return null;
      } finally {
         try {
            in.close();
         } catch (Exception ignored) {
         }
      }
   }

   private static Font loadInterBoldFont(float size) {
      InputStream in = StartupSplashRenderer.class.getClassLoader().getResourceAsStream(FONT_RESOURCE);
      if (in != null) {
         try {
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            return font.deriveFont(Font.BOLD, size);
         } catch (Exception ignored) {
         } finally {
            try {
               in.close();
            } catch (Exception ignored) {
            }
         }
      }
      return new Font("SansSerif", Font.BOLD, Math.round(size));
   }

   private static void renderFrame(float progress) {
      if (!Display.isCreated()) {
         return;
      }
      Minecraft mc = Minecraft.getMinecraft();
      int winW = Math.max(1, Display.getWidth() > 0 ? Display.getWidth() : (mc != null ? mc.displayWidth : 1366));
      int winH = Math.max(1, Display.getHeight() > 0 ? Display.getHeight() : (mc != null ? mc.displayHeight : 768));

      if (OpenGlHelper.isFramebufferEnabled()) {
         OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);
      }
      GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_VIEWPORT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
      GlStateManager.viewport(0, 0, winW, winH);
      GlStateManager.matrixMode(GL11.GL_PROJECTION);
      GL11.glPushMatrix();
      GlStateManager.loadIdentity();
      GlStateManager.ortho(0.0D, (double)winW, (double)winH, 0.0D, -1.0D, 1.0D);
      GlStateManager.matrixMode(GL11.GL_MODELVIEW);
      GL11.glPushMatrix();
      GlStateManager.loadIdentity();

      GL11.glDisable(GL11.GL_DEPTH_TEST);
      GL11.glDisable(GL11.GL_LIGHTING);
      GL11.glDisable(GL11.GL_FOG);
      GL11.glDisable(GL11.GL_CULL_FACE);
      GL11.glDisable(GL11.GL_ALPHA_TEST);
      GL11.glEnable(GL11.GL_BLEND);
      GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

      // 1. Matte dark obsidian background matching reference (#121519)
      drawSolidRect(0.0F, 0.0F, (float)winW, (float)winH, BG_COLOR);

      // 2. Centered lockup + sleek bottom progress bar proportions matching 1366x768 & any resized window
      float scale = Math.max(0.70F, Math.min(1.65F, (float)winH / 768.0F));
      float barW = Math.max(320.0F, Math.min((float)winW * 0.56F, 580.0F * scale));
      float barH = Math.max(3.0F, 3.5F * scale);
      float barY = (float)winH * 0.5F + 50.0F * scale;

      if (lockupGlTex > 0 && lockupWidthPx > 0 && lockupHeightPx > 0) {
         float drawW = (float)lockupWidthPx * 0.55F * scale;
         float drawH = (float)lockupHeightPx * 0.55F * scale;
         float maxLockupW = (float)winW * 0.78F;
         if (drawW > maxLockupW) {
            float fit = maxLockupW / drawW;
            drawW *= fit;
            drawH *= fit;
         }
         barW = drawW;
         float lockupX = ((float)winW - drawW) * 0.5F;
         float lockupY = (float)winH * 0.5F - drawH * 0.5F - 14.0F * scale;

         GL11.glEnable(GL11.GL_TEXTURE_2D);
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, lockupGlTex);
         GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
         Tessellator tess = Tessellator.getInstance();
         WorldRenderer wr = tess.getWorldRenderer();
         double uPad = 1.0D / (double)Math.max(2, lockupWidthPx);
         double vPad = 1.0D / (double)Math.max(2, lockupHeightPx);
         wr.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
         wr.pos((double)lockupX, (double)(lockupY + drawH), 0.0D).tex(uPad, 1.0D - vPad).color(255, 255, 255, 255).endVertex();
         wr.pos((double)(lockupX + drawW), (double)(lockupY + drawH), 0.0D).tex(1.0D - uPad, 1.0D - vPad).color(255, 255, 255, 255).endVertex();
         wr.pos((double)(lockupX + drawW), (double)lockupY, 0.0D).tex(1.0D - uPad, vPad).color(255, 255, 255, 255).endVertex();
         wr.pos((double)lockupX, (double)lockupY, 0.0D).tex(uPad, vPad).color(255, 255, 255, 255).endVertex();
         tess.draw();
      }
      float barX = ((float)winW - barW) * 0.5F;

      // 3. Sleek dark track + solid theme-matched Augustus accent fill bar
      drawSolidRect(barX, barY, barW, barH, TRACK_COLOR);
      float fillW = barW * Math.max(0.0F, Math.min(1.0F, progress));
      if (fillW >= 1.0F) {
         drawSolidRect(barX, barY, fillW, barH, resolveThemeBarColor());
      }

      lastRenderMs = System.currentTimeMillis();

      GlStateManager.enableTexture2D();
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
      GlStateManager.matrixMode(GL11.GL_MODELVIEW);
      GL11.glPopMatrix();
      GlStateManager.matrixMode(GL11.GL_PROJECTION);
      GL11.glPopMatrix();
      GlStateManager.matrixMode(GL11.GL_MODELVIEW);
      GL11.glPopAttrib();

      Display.processMessages();
      Display.update();
   }

   private static int resolveThemeBarColor() {
      // Never touch ClientManagers before Augustus.initializeClient() finishes ClientManagers.init().
      Augustus aug = Augustus.getInstance();
      if (aug != null && aug.getModuleManager() != null && aug.getModuleManager().clickGui != null && aug.getConfigManager() != null) {
         return 0xFF000000 | (UiTheme.accentColor() & 0x00FFFFFF);
      }
      if (!earlyThemeChecked) {
         earlyThemeChecked = true;
         cachedEarlyThemeColor = readSavedThemeColor();
      }
      return cachedEarlyThemeColor != 0 ? cachedEarlyThemeColor : FILL_COLOR;
   }

   private static int readSavedThemeColor() {
      try {
         Minecraft mc = Minecraft.getMinecraft();
         File visuals = new File(JsonConfigFile.BASE_CONFIG_DIR + File.separator + "visuals.json");
         if (!visuals.isFile() && mc != null && mc.mcDataDir != null) {
            visuals = new File(mc.mcDataDir, "augustus" + File.separator + "visuals.json");
         }
         if (!visuals.isFile()) {
            return 0;
         }
         String text = new String(Files.readAllBytes(visuals.toPath()), StandardCharsets.UTF_8);
         JsonElement rootEl = new JsonParser().parse(text);
         if (rootEl == null || !rootEl.isJsonObject()) {
            return 0;
         }
         JsonObject root = rootEl.getAsJsonObject();
         if (root.has("ClickGui") && root.get("ClickGui").isJsonObject()) {
            JsonObject cg = root.getAsJsonObject("ClickGui");
            if (cg.has("TextColorHover") && cg.get("TextColorHover").isJsonObject()) {
               JsonObject hover = cg.getAsJsonObject("TextColorHover");
               boolean cc = hover.has("CC") && hover.get("CC").isJsonPrimitive() && hover.get("CC").getAsBoolean();
               if (!cc && hover.has("C") && hover.get("C").isJsonPrimitive()) {
                  return 0xFF000000 | (hover.get("C").getAsInt() & 0x00FFFFFF);
               }
            }
         }
         if (root.has("ClientColor") && root.get("ClientColor").isJsonObject()) {
            JsonObject ccObj = root.getAsJsonObject("ClientColor");
            if (ccObj.has("Color") && ccObj.get("Color").isJsonObject()) {
               JsonObject colorObj = ccObj.getAsJsonObject("Color");
               if (colorObj.has("C") && colorObj.get("C").isJsonPrimitive()) {
                  return 0xFF000000 | (colorObj.get("C").getAsInt() & 0x00FFFFFF);
               }
            }
            if (ccObj.has("Theme") && ccObj.get("Theme").isJsonPrimitive()) {
               Color c = CustomClientColor.presetColorFor(ccObj.get("Theme").getAsString());
               return 0xFF000000 | (c.getRGB() & 0x00FFFFFF);
            }
         }
      } catch (Throwable ignored) {
      }
      return 0;
   }

   private static void drawSolidRect(float x, float y, float w, float h, int argb) {
      drawHorizontalGradientRect(x, y, w, h, argb, argb);
   }

   private static void drawHorizontalGradientRect(float x, float y, float w, float h, int leftArgb, int rightArgb) {
      GL11.glDisable(GL11.GL_TEXTURE_2D);
      int la = (leftArgb >>> 24) & 0xFF;
      int lr = (leftArgb >> 16) & 0xFF;
      int lg = (leftArgb >> 8) & 0xFF;
      int lb = leftArgb & 0xFF;

      int ra = (rightArgb >>> 24) & 0xFF;
      int rr = (rightArgb >> 16) & 0xFF;
      int rg = (rightArgb >> 8) & 0xFF;
      int rb = rightArgb & 0xFF;

      Tessellator tess = Tessellator.getInstance();
      WorldRenderer wr = tess.getWorldRenderer();
      wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
      wr.pos((double)x, (double)(y + h), 0.0D).color(lr, lg, lb, la).endVertex();
      wr.pos((double)(x + w), (double)(y + h), 0.0D).color(rr, rg, rb, ra).endVertex();
      wr.pos((double)(x + w), (double)y, 0.0D).color(rr, rg, rb, ra).endVertex();
      wr.pos((double)x, (double)y, 0.0D).color(lr, lg, lb, la).endVertex();
      tess.draw();
   }
}
