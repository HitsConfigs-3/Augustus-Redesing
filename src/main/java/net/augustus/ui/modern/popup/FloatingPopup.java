package net.augustus.ui.modern.popup;

/**
 * Contract for floating overlay popups (dropdowns, color pickers, context menus)
 * rendered on top of scrolled panels in absolute window-local coordinates.
 */
public interface FloatingPopup {
   /** Draws the popup overlay (called outside scroll scissor clipping). */
   void draw(int mouseX, int mouseY);

   /** Returns whether (mouseX, mouseY) in window-local coordinates lies inside the popup. */
   boolean contains(int mouseX, int mouseY);

   /** Handles mouse click inside the popup. Returns true if consumed. */
   boolean mouseClicked(int mouseX, int mouseY, int mouseButton);

   default void mouseClickMove(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) {
   }

   default void mouseReleased(int mouseX, int mouseY, int state) {
   }

   default boolean keyTyped(char typedChar, int keyCode) {
      return false;
   }

   /** Handles mouse wheel while over the popup. Returns true if consumed. */
   default boolean handleScroll(int delta) {
      return false;
   }

   /** Called when closed cleanly. */
   default void onClosed() {
   }
}
