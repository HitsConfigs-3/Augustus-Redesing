package net.augustus.utils.shader;

import net.augustus.Augustus;
import net.augustus.utils.render.GlStateSaver;
import net.augustus.utils.render.RenderSystemAssetLoader;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Objects;
import net.minecraft.client.gui.ScaledResolution;
import net.augustus.utils.interfaces.MC;
import org.apache.commons.io.IOUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class BackgroundShader implements MC {
   private ShaderProgram shaderProgram;
   private final String vertexShaderSource;
   private long startTimeMs;
   private String name;
   private boolean selected;
   private final GlStateSaver glStateSaver = GlStateSaver.create();

   public String loadResourceSource(String str) {
      try {
         return IOUtils.toString(Objects.requireNonNull(RenderSystemAssetLoader.class.getResourceAsStream(str)));
      } catch (IOException e) {
         return null;
      }
   }

   public void render(float f) {
      if (this.shaderProgram != null) {
         this.shaderProgram.bind();
         float f2 = (float)(System.currentTimeMillis() - this.startTimeMs) / f;
         String str = "time";
         this.shaderProgram.setUniform1f(str, f2);
         String str2 = "timeDelta";
         this.shaderProgram.setUniform1f(str2, f2);
         if (Augustus.getInstance().getModuleManager().clientSettings.mouseInputsOnBackgrounds.getValue()) {
            ShaderProgram shaderProgram2 = this.shaderProgram;
            float f3 = (float)(-Mouse.getY());
            float x = (float)Mouse.getX();
            shaderProgram2.setUniform2f("mouse", x, f3);
         }

         float displayWidth = (float)mc.displayWidth;
         float displayHeight = (float)mc.displayHeight;
         String str4 = "resolution";
         this.shaderProgram.setUniform2f(str4, displayWidth, displayHeight);
         this.glStateSaver.setAlpha(false);
         float displayWidth2 = (float)mc.displayWidth;
         float displayHeight2 = (float)mc.displayHeight;
         float f6 = 0.0F;
         float f7 = 0.0F;
         this.shaderProgram.drawQuad(f7, f6, displayWidth2, displayHeight2);
         this.glStateSaver.restoreAlpha();
         this.shaderProgram.unbind();
      }
   }

   public boolean isCompiled() {
      return this.shaderProgram != null;
   }

   public boolean renderInBox(float x, float y, float width, float height, float speed) {
      if (this.shaderProgram == null || width <= 1.0F || height <= 1.0F) {
         return false;
      }
      net.minecraft.client.gui.ScaledResolution sr = new net.minecraft.client.gui.ScaledResolution(mc);
      int scale = Math.max(1, sr.getScaleFactor());
      int scissorX = Math.max(0, (int)Math.floor(x * scale));
      int scissorY = Math.max(0, mc.displayHeight - (int)Math.ceil((y + height) * scale));
      int scissorW = Math.max(1, (int)Math.ceil(width * scale));
      int scissorH = Math.max(1, (int)Math.ceil(height * scale));
      float centerPxX = scissorX + scissorW * 0.5F;
      float centerPxY = scissorY + scissorH * 0.5F;

      GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_SCISSOR_BIT | GL11.GL_COLOR_BUFFER_BIT);
      GL11.glDisable(GL11.GL_TEXTURE_2D);
      GL11.glEnable(GL11.GL_BLEND);
      GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GL11.glEnable(GL11.GL_SCISSOR_TEST);
      GL11.glScissor(scissorX, scissorY, scissorW, scissorH);
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

      this.shaderProgram.bind();
      float elapsed = (float)(System.currentTimeMillis() - this.startTimeMs) / speed;
      this.shaderProgram.setUniform1f("time", elapsed);
      this.shaderProgram.setUniform1f("timeDelta", elapsed);
      if (Augustus.getInstance().getModuleManager().clientSettings.mouseInputsOnBackgrounds.getValue()) {
         this.shaderProgram.setUniform2f("mouse", (float)Mouse.getX(), (float)(-Mouse.getY()));
      }
      // Doubling the box center coordinates maps gl_FragCoord center of the preview box to shader (0.5, 0.5) / origin.
      this.shaderProgram.setUniform2f("resolution", centerPxX * 2.0F, centerPxY * 2.0F);
      this.shaderProgram.drawQuad(0.0F, 0.0F, (float)mc.displayWidth, (float)mc.displayHeight);
      this.shaderProgram.unbind();

      GL11.glPopAttrib();
      return true;
   }

   public void loadFromFile(String str, String str2) {
      if (this.shaderProgram == null) {
         try {
            this.shaderProgram = new ShaderProgram(this.vertexShaderSource, this.readFileSource(str));
         } catch (RuntimeException e) {
         }

         this.startTimeMs = System.currentTimeMillis();
      }

      this.name = str2;
   }

   public void setSelected(boolean flag) {
      this.selected = flag;
   }

   public String getName() {
      return this.name;
   }

   public boolean isSelected() {
      return this.selected;
   }

   public void setName(String str) {
      this.name = str;
   }

   public String readFileSource(String str) {

      try {
         StringBuilder sb = new StringBuilder();
         BufferedReader reader = new BufferedReader(new FileReader(str));

         String str2;
         while ((str2 = reader.readLine()) != null) {
            sb.append(str2).append('\n');
         }

         reader.close();
         return sb.toString();
      } catch (IOException e) {
         System.err.println("Fragment shader was not load properly");
         return null;
      }
   }

   public void loadFromResource(String str) {
      if (this.shaderProgram == null) {
         this.shaderProgram = new ShaderProgram(this.vertexShaderSource, this.loadResourceSource(str));
         this.startTimeMs = System.currentTimeMillis();
      }
   }

   public BackgroundShader() {
      this.vertexShaderSource = this.loadResourceSource("/assets/augustus/shaders/shader.vert");
   }


}
