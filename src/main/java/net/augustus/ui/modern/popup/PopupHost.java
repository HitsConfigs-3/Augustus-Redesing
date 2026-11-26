package net.augustus.ui.modern.popup;

/**
 * Coordinates the currently active floating popup inside a ClickGUI window
 * so dropdowns and pickers render above clipped scroll containers and capture input first.
 */
public final class PopupHost {
   private FloatingPopup activePopup;
   private Object activeOwner;
   private Object dismissedOwnerInCurrentClick;
   private int clipWidth = Integer.MAX_VALUE;
   private int clipHeight = Integer.MAX_VALUE;

   public void setBounds(int width, int height) {
      this.clipWidth = width;
      this.clipHeight = height;
   }

   public int getClipWidth() {
      return this.clipWidth;
   }

   public int getClipHeight() {
      return this.clipHeight;
   }

   public boolean isOpenFor(Object owner) {
      return this.activePopup != null && this.activeOwner == owner;
   }

   public boolean hasActivePopup() {
      return this.activePopup != null;
   }

   public FloatingPopup getActivePopup() {
      return this.activePopup;
   }

   public void open(Object owner, FloatingPopup popup) {
      if (this.activePopup != null && this.activePopup != popup) {
         this.activePopup.onClosed();
      }
      this.activeOwner = owner;
      this.activePopup = popup;
   }

   public void toggle(Object owner, FloatingPopup popup) {
      if (owner != null && this.dismissedOwnerInCurrentClick == owner) {
         this.dismissedOwnerInCurrentClick = null;
         return;
      }
      this.dismissedOwnerInCurrentClick = null;
      if (this.isOpenFor(owner)) {
         this.close();
      } else {
         this.open(owner, popup);
      }
   }

   public void clearClickState() {
      this.dismissedOwnerInCurrentClick = null;
   }

   public void close() {
      if (this.activePopup != null) {
         this.activePopup.onClosed();
         this.activePopup = null;
         this.activeOwner = null;
      }
   }

   public void draw(int mouseX, int mouseY) {
      if (this.activePopup != null) {
         this.activePopup.draw(mouseX, mouseY);
      }
   }

   /**
    * Routes a mouse click to the active popup. Closes the popup if clicked outside.
    * @return true if the active popup handled the click inside its bounds
    */
   public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
      this.dismissedOwnerInCurrentClick = null;
      if (this.activePopup == null) {
         return false;
      }
      if (this.activePopup.contains(mouseX, mouseY)) {
         this.activePopup.mouseClicked(mouseX, mouseY, mouseButton);
         return true;
      }
      this.dismissedOwnerInCurrentClick = this.activeOwner;
      this.close();
      return false;
   }

   public void mouseClickMove(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) {
      if (this.activePopup != null) {
         this.activePopup.mouseClickMove(mouseX, mouseY, mouseButton, timeSinceLastClick);
      }
   }

   public void mouseReleased(int mouseX, int mouseY, int state) {
      if (this.activePopup != null) {
         this.activePopup.mouseReleased(mouseX, mouseY, state);
      }
   }

   public boolean keyTyped(char typedChar, int keyCode) {
      if (this.activePopup == null) {
         return false;
      }
      if (keyCode == 1) {
         this.close();
         return true;
      }
      return this.activePopup.keyTyped(typedChar, keyCode);
   }

   public boolean handleScroll(int mouseX, int mouseY, int delta) {
      if (this.activePopup != null && delta != 0 && this.activePopup.contains(mouseX, mouseY)) {
         return this.activePopup.handleScroll(delta);
      }
      return false;
   }
}
