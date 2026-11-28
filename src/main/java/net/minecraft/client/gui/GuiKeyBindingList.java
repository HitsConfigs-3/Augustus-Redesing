package net.minecraft.client.gui;

import net.augustus.ui.VanillaScreenText;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.EnumChatFormatting;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import org.apache.commons.lang3.ArrayUtils;

public class GuiKeyBindingList extends GuiListExtended {
   private final GuiControls controlsScreen;
   private final Minecraft mc;
   private final GuiListExtended.IGuiListEntry[] listEntries;
   private int maxListLabelWidth = 0;

   @Override
   public int getSize() {
      return this.listEntries.length;
   }

   public static Minecraft accessMc(GuiKeyBindingList guiKeyBindingList) {
      return guiKeyBindingList.mc;
   }

   @Override
   public GuiListExtended.IGuiListEntry getEntry(int n) {
      return this.listEntries[n];
   }

   @Override
   public int getListWidth() {
      return Math.max(252, this.maxListLabelWidth + 168);
   }

   public static GuiControls accessControlsScreen(GuiKeyBindingList guiKeyBindingList) {
      return guiKeyBindingList.controlsScreen;
   }

   @Override
   public int getScrollBarX() {
      return this.left + this.width / 2 + this.getListWidth() / 2 + 4;
   }

   public static int accessMaxListLabelWidth(GuiKeyBindingList guiKeyBindingList) {
      return guiKeyBindingList.maxListLabelWidth;
   }

   public GuiKeyBindingList(GuiControls guiControls, Minecraft mc2) {
      super(mc2, guiControls.width, guiControls.height, 63, guiControls.height - 32, 20);
      this.controlsScreen = guiControls;
      this.mc = mc2;
      KeyBinding[] keyBindings = (KeyBinding[])ArrayUtils.clone(mc2.gameSettings.keyBindings);
      this.listEntries = new GuiListExtended.IGuiListEntry[keyBindings.length + KeyBinding.getKeybinds().size()];
      Arrays.sort(keyBindings);
      int n = 0;
      String str = null;

      for (KeyBinding keyBinding : keyBindings) {
         String keyCategory = keyBinding.getKeyCategory();
         if (!keyCategory.equals(str)) {
            str = keyCategory;
            this.listEntries[n++] = new GuiKeyBindingList.CategoryEntry(this, keyCategory);
         }

         int stringWidth = mc2.fontRendererObj.getStringWidth(I18n.format(keyBinding.getKeyDescription(), new Object[0]));
         if (stringWidth > this.maxListLabelWidth) {
            this.maxListLabelWidth = stringWidth;
         }

         this.listEntries[n++] = new GuiKeyBindingList.KeyEntry(this, keyBinding, null);
      }
   }


   public static class InnerEntry {
   }

   public static class KeyEntry implements GuiListExtended.IGuiListEntry {
      private final KeyBinding keybinding;
      private final String keyDesc;
      private final GuiButton btnChangeKeyBinding;
      private final GuiButton btnReset;
      public final GuiKeyBindingList keyBindingList;

      public KeyEntry(GuiKeyBindingList guiKeyBindingList, KeyBinding keyBinding, GuiKeyBindingList.InnerEntry obj) {
         this(guiKeyBindingList, keyBinding);
      }

      @Override
      public void drawEntry(int n, int n2, int n3, int n4, int n5, int n6, int n7, boolean flag) {
         boolean flag2 = GuiKeyBindingList.accessControlsScreen(this.keyBindingList).buttonId == this.keybinding;
         // The key's name rests on the menu ink and on the client face, like every other label of this round.
         //
         // WHY this row gets no plate of its own: the row's whole box is 16 px tall (slotHeight - 4, what
         // GuiListExtended hands an entry) while the two option plates that fill it - the key button and the
         // Reset button, both 20 px tall in a 20 px slot - are exactly the height of the row itself. A plate
         // drawn behind them would be shorter than the controls it is meant to carry (its ring would be
         // crossed by their bottom corners) or, at their height, would touch the ring of the row above. The
         // label's own box starts left of the list on the widest binding (it is right-aligned at n2 + 90), so
         // a plate around the label alone would hang outside the list. The two buttons already are the
         // option material; this row is the one list whose content is controls rather than text.
         VanillaScreenText
            .drawString(
               this.keyDesc,
               n2 + 4,
               n3 + n5 / 2 - GuiKeyBindingList.accessMc(this.keyBindingList).fontRendererObj.FONT_HEIGHT / 2,
               16777215
            );
         this.btnReset.xPosition = n2 + n4 - 56;
         this.btnReset.yPosition = n3;
         this.btnReset.enabled = this.keybinding.getKeyCode() != this.keybinding.getKeyCodeDefault();
         this.btnReset.drawButton(GuiKeyBindingList.accessMc(this.keyBindingList), n6, n7);
         this.btnChangeKeyBinding.xPosition = this.btnReset.xPosition - 82;
         this.btnChangeKeyBinding.yPosition = n3;
         this.btnChangeKeyBinding.displayString = GameSettings.getKeyDisplayString(this.keybinding.getKeyCode());
         boolean flag3 = false;
         if (this.keybinding.getKeyCode() != 0) {
            for (KeyBinding keyBinding : GuiKeyBindingList.accessMc(this.keyBindingList).gameSettings.keyBindings) {
               if (keyBinding != this.keybinding && keyBinding.getKeyCode() == this.keybinding.getKeyCode()) {
                  flag3 = true;
                  break;
               }
            }
         }

         if (flag2) {
            this.btnChangeKeyBinding.displayString = EnumChatFormatting.WHITE
               + "> "
               + EnumChatFormatting.YELLOW
               + this.btnChangeKeyBinding.displayString
               + EnumChatFormatting.WHITE
               + " <";
         } else if (flag3) {
            this.btnChangeKeyBinding.displayString = EnumChatFormatting.RED + this.btnChangeKeyBinding.displayString;
         }

         this.btnChangeKeyBinding.drawButton(GuiKeyBindingList.accessMc(this.keyBindingList), n6, n7);
      }

      @Override
      public boolean mousePressed(int n, int n2, int n3, int n4, int n5, int n6) {
         if (this.btnChangeKeyBinding.mousePressed(GuiKeyBindingList.accessMc(this.keyBindingList), n2, n3)) {
            GuiKeyBindingList.accessControlsScreen(this.keyBindingList).buttonId = this.keybinding;
            return true;
         } else if (this.btnReset.mousePressed(GuiKeyBindingList.accessMc(this.keyBindingList), n2, n3)) {
            GuiKeyBindingList.accessMc(this.keyBindingList).gameSettings.setOptionKeyBinding(this.keybinding, this.keybinding.getKeyCodeDefault());
            KeyBinding.resetKeyBindingArrayAndHash();
            return true;
         } else {
            return false;
         }
      }

      @Override
      public void mouseReleased(int n, int n2, int n3, int n4, int n5, int n6) {
         this.btnChangeKeyBinding.mouseReleased(n2, n3);
         this.btnReset.mouseReleased(n2, n3);
      }

      private KeyEntry(GuiKeyBindingList guiKeyBindingList, KeyBinding keyBinding) {
         this.keyBindingList = guiKeyBindingList;
         this.keybinding = keyBinding;
         this.keyDesc = I18n.format(keyBinding.getKeyDescription(), new Object[0]);
         this.btnChangeKeyBinding = new GuiButton(0, 0, 0, 75, 20, I18n.format(keyBinding.getKeyDescription(), new Object[0]));
         this.btnReset = new GuiButton(0, 0, 0, 50, 20, I18n.format("controls.reset", new Object[0]));
      }

      @Override
      public void setSelected(int n, int n2, int n3) {
      }
   }

   public static class CategoryEntry implements GuiListExtended.IGuiListEntry {
      private final String labelText;
      private final int labelWidth;
      public final GuiKeyBindingList keyBindingList;

      @Override
      public void mouseReleased(int n, int n2, int n3, int n4, int n5, int n6) {
      }

      @Override
      public void drawEntry(int n, int n2, int n3, int n4, int n5, int n6, int n7, boolean flag) {
         // The category label is a heading, so it is set in the face the client's own pages use (Inter through
         // VanillaScreenText), measured with that same face so the centring still lands on the screen centre.
         VanillaScreenText.drawCenteredOnScreen(
            GuiKeyBindingList.accessMc(this.keyBindingList).currentScreen.width,
            this.labelText,
            n3 + n5 - VanillaScreenText.getFontHeight() - 1,
            16777215
         );
      }

      @Override
      public void setSelected(int n, int n2, int n3) {
      }

      @Override
      public boolean mousePressed(int n, int n2, int n3, int n4, int n5, int n6) {
         return false;
      }

      public CategoryEntry(GuiKeyBindingList guiKeyBindingList, String str) {
         this.keyBindingList = guiKeyBindingList;
         this.labelText = I18n.format(str, new Object[0]);
         this.labelWidth = VanillaScreenText.getStringWidth(this.labelText);
      }
   }
}
