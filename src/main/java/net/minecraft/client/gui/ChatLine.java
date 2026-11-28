package net.minecraft.client.gui;

import net.minecraft.util.IChatComponent;

public class ChatLine {
   private final int updateCounterCreated;
   private final IChatComponent lineString;
   private final int chatLineID;
   private final boolean ircMessage;

   public IChatComponent getChatComponent() {
      return this.lineString;
   }

   public int getUpdatedCounter() {
      return this.updateCounterCreated;
   }

   public int getChatLineID() {
      return this.chatLineID;
   }

   public boolean isIrcMessage() {
      return this.ircMessage;
   }

   public ChatLine(int n, IChatComponent component, int n2) {
      this(n, component, n2, false);
   }

   public ChatLine(int n, IChatComponent component, int n2, boolean ircMessage) {
      this.lineString = component;
      this.updateCounterCreated = n;
      this.chatLineID = n2;
      this.ircMessage = ircMessage;
   }
}
