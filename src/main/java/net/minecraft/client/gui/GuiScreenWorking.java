package net.minecraft.client.gui;

import net.augustus.ui.VanillaScreenText;
import net.minecraft.util.IProgressUpdate;
import net.optifine.CustomLoadingScreen;
import net.optifine.CustomLoadingScreens;

public class GuiScreenWorking extends GuiScreen implements IProgressUpdate {
   private String title = "";
   private String stage = "";
   private int progress;
   private boolean doneWorking;
   private CustomLoadingScreen customLoadingScreen = CustomLoadingScreens.getCustomLoadingScreen();

   public void setDoneWorking() {
      this.doneWorking = true;
   }

   @Override
   public void drawScreen(int mouseX, int mouseY, float partialTicks) {
      if (this.doneWorking) {
         if (!this.mc.isConnectedToRealms()) {
            GuiScreen screen = (GuiScreen)null;
            this.mc.displayGuiScreen(screen);
         }
      } else {
         if (this.customLoadingScreen != null && this.mc.theWorld == null) {
            this.customLoadingScreen.drawBackground(this.width, this.height);
         } else {
            this.drawDefaultBackground();
         }

         if (this.progress > 0) {
            VanillaScreenText.drawCenteredOnScreen(this.width, this.title, 70, 16777215);
            VanillaScreenText.drawCenteredOnScreen(this.width, this.stage + " " + this.progress + "%", 90, 16777215);
         }

         VanillaScreenText.drawCenteredOnScreen(this.width, this.title, 70, 16777215);
         VanillaScreenText.drawCenteredOnScreen(this.width, this.stage + " " + this.progress + "%", 90, 16777215);
         super.drawScreen(mouseX, mouseY, partialTicks);
      }
   }

   public void setLoadingProgress(int n) {
      this.progress = n;
   }

   public void displayLoadingString(String str) {
      this.stage = str;
      this.setLoadingProgress(0);
   }

   public void displaySavingString(String str) {
      this.resetProgressAndMessage(str);
   }

   public void resetProgressAndMessage(String str) {
      this.title = str;
      this.displayLoadingString("Working...");
   }

}
