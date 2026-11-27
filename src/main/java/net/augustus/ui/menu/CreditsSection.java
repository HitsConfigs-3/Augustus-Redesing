package net.augustus.ui.menu;

import java.util.List;
import net.augustus.font.FontManager;
import net.minecraft.client.Minecraft;

public abstract class CreditsSection {
   private final String sectionTitle;

   public abstract List getSocialLinks();

   public int calculateHeight() {
      // Row pitch of the Inter body face (the face the credits are painted with), not the bitmap font's
      // 9 px box - otherwise the card would be shorter than its own text.
      int n = Math.round(FontManager.bodyLineHeight());
      int n2 = 0;
      n2 += n * 2;
      n2 += n;
      n2 += this.getMemberLines().size() * n;
      n2 += n;
      n2 += n;
      return n2 + this.getSocialLinks().size() * n;
   }

   public CreditsSection(String str) {
      this.sectionTitle = str;
   }

   public abstract List getMemberLines();

   public String getSectionTitle() {
      return this.sectionTitle;
   }



}
