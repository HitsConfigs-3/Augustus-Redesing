package net.augustus.modules.render;

import java.awt.Color;
import net.augustus.modules.Categorys;
import net.augustus.modules.Module;
import net.augustus.Augustus;
import net.augustus.settings.BooleanValue;
import org.lwjgl.opengl.Display;

public class InvisMode extends Module {
   private BooleanValue disableCustomEscapeMenu;
   private BooleanValue disableCustomMainMenu;

   public InvisMode() {
       super("InvisMode", "UnsichtbarModus", new Color(3, 252, 227), Categorys.RENDER);
      this.disableCustomEscapeMenu = new BooleanValue(this, "Disable custom escape menu", true);
      this.disableCustomMainMenu = new BooleanValue(this, "Disable custom main menu", false);
   }

   @Override
   public void onEnable() {
      Display.setTitle("Minecraft 1.8.9");
      mc.setWindowIcon(true);
   }

   @Override
   public void onDisable() {
      // Restores exactly what createDisplay() installed, so toggling invisibility cannot leave the window
      // branded with the old "Augustus b4.23.3" title.
      Display.setTitle(Augustus.getInstance().getWindowTitle());
      mc.setWindowIcon(false);
   }

   @Override
   public void updateEventSubscriptions() {
   }

   public boolean shouldDisableCustomEscapeMenu() {
      return this.disableCustomEscapeMenu.getValue();
   }

   public boolean shouldDisableCustomMainMenu() {
      return this.disableCustomMainMenu.getValue();
   }

}
