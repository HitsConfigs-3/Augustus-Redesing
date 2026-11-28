package net.augustus.modules.misc;

import java.awt.Color;
import net.augustus.Augustus;
import net.augustus.irc.IrcConfigFile;
import net.augustus.irc.IrcManager;
import net.augustus.modules.Categorys;
import net.augustus.modules.Module;
import net.augustus.settings.ActionSetting;
import net.augustus.settings.BooleanValue;
import net.augustus.ui.menu.ClientTabContainerScreen;
import net.augustus.ui.menu.IrcMenuTab;
import net.minecraft.client.gui.GuiScreen;

/**
 * ClickGUI interface for the Augustus IRC, Premium identity auto-sync, exclusive chat, and nametag badges.
 */
public class IRC extends Module {
   public final BooleanValue autoOriginalNick;
   public final BooleanValue exclusiveChat;
   public final BooleanValue showIrcInGlobal;
   public final BooleanValue nametagLogo;
   public final BooleanValue nametagOriginalNick;
   public final BooleanValue nametagLevel;
   public final BooleanValue ownNametagLogo;
   public final BooleanValue tabListBadges;
   public final BooleanValue shareServerStatus;
   public final ActionSetting openIrcMenu;
   public final ActionSetting linkPremiumAccount;

   private boolean syncing;

   public IRC() {
      super("IRC", "AugustusIRC", true, new Color(138, 92, 246), Categorys.MISC);
      IrcConfigFile cfg = IrcManager.getInstance().getConfig();

      final BooleanValue vAutoOriginalNick = new BooleanValue(this, "AutoOriginalNick", cfg.isAutoUseOriginalNick());
      vAutoOriginalNick.withChangeListener(() -> {
         if (!this.syncing) {
            cfg.setAutoUseOriginalNick(vAutoOriginalNick.getValue());
            if (vAutoOriginalNick.getValue()) {
               IrcManager.getInstance().ensureOriginalNickBeforeConnect();
            }
         }
      });
      this.autoOriginalNick = vAutoOriginalNick;

      final BooleanValue vExclusiveChat = new BooleanValue(this, "ExclusiveChat", cfg.isExclusiveChatMode());
      vExclusiveChat.withChangeListener(() -> {
         if (!this.syncing) {
            cfg.setExclusiveChatMode(vExclusiveChat.getValue());
         }
      });
      this.exclusiveChat = vExclusiveChat;

      final BooleanValue vShowIrcInGlobal = new BooleanValue(this, "ShowIRCInGlobal", cfg.isShowIrcInGlobalChat());
      vShowIrcInGlobal.withChangeListener(() -> {
         if (!this.syncing) {
            cfg.setShowIrcInGlobalChat(vShowIrcInGlobal.getValue());
         }
      });
      this.showIrcInGlobal = vShowIrcInGlobal;

      final BooleanValue vNametagLogo = new BooleanValue(this, "NametagLogo", cfg.isShowLogoOnNametags());
      vNametagLogo.withChangeListener(() -> {
         if (!this.syncing) {
            cfg.setShowLogoOnNametags(vNametagLogo.getValue());
         }
      });
      this.nametagLogo = vNametagLogo;

      final BooleanValue vNametagOriginalNick = new BooleanValue(this, "NametagOriginalNick", cfg.isShowOriginalNickOnNametags());
      vNametagOriginalNick.withChangeListener(() -> {
         if (!this.syncing) {
            cfg.setShowOriginalNickOnNametags(vNametagOriginalNick.getValue());
         }
      });
      this.nametagOriginalNick = vNametagOriginalNick;

      final BooleanValue vNametagLevel = new BooleanValue(this, "NametagLevel", cfg.isShowLevelOnNametags());
      vNametagLevel.withChangeListener(() -> {
         if (!this.syncing) {
            cfg.setShowLevelOnNametags(vNametagLevel.getValue());
         }
      });
      this.nametagLevel = vNametagLevel;

      final BooleanValue vOwnNametagLogo = new BooleanValue(this, "OwnNametagLogo", cfg.isShowOwnNametagLogo());
      vOwnNametagLogo.withChangeListener(() -> {
         if (!this.syncing) {
            cfg.setShowOwnNametagLogo(vOwnNametagLogo.getValue());
         }
      });
      this.ownNametagLogo = vOwnNametagLogo;

      final BooleanValue vTabListBadges = new BooleanValue(this, "TabListBadges", cfg.isShowTabBadges());
      vTabListBadges.withChangeListener(() -> {
         if (!this.syncing) {
            cfg.setShowTabBadges(vTabListBadges.getValue());
         }
      });
      this.tabListBadges = vTabListBadges;

      final BooleanValue vShareServerStatus = new BooleanValue(this, "ShareServerStatus", cfg.isShareServerStatus());
      vShareServerStatus.withChangeListener(() -> {
         if (!this.syncing) {
            cfg.setShareServerStatus(vShareServerStatus.getValue());
         }
      });
      this.shareServerStatus = vShareServerStatus;

      this.openIrcMenu = new ActionSetting(this, "Open Augustus IRC", () -> {
         GuiScreen prev = mc.currentScreen;
         int idx = Augustus.getInstance().getClientTabManager().indexOf(IrcMenuTab.class);
         mc.displayGuiScreen(new ClientTabContainerScreen(prev, idx));
      });
      this.linkPremiumAccount = new ActionSetting(this, "Link Microsoft Premium", () -> {
         IrcManager.getInstance().startInteractiveMicrosoftLink();
      });

      this.setToggled(cfg.isIrcEnabled(), false);
   }

   public void syncFromConfig() {
      IrcConfigFile cfg = IrcManager.getInstance().getConfig();
      this.syncing = true;
      try {
         this.autoOriginalNick.setValue(cfg.isAutoUseOriginalNick());
         this.exclusiveChat.setValue(cfg.isExclusiveChatMode());
         this.showIrcInGlobal.setValue(cfg.isShowIrcInGlobalChat());
         this.nametagLogo.setValue(cfg.isShowLogoOnNametags());
         this.nametagOriginalNick.setValue(cfg.isShowOriginalNickOnNametags());
         this.nametagLevel.setValue(cfg.isShowLevelOnNametags());
         this.ownNametagLogo.setValue(cfg.isShowOwnNametagLogo());
         this.tabListBadges.setValue(cfg.isShowTabBadges());
         this.shareServerStatus.setValue(cfg.isShareServerStatus());
         if (this.isToggled() != cfg.isIrcEnabled()) {
            this.setToggled(cfg.isIrcEnabled(), false);
         }
      } finally {
         this.syncing = false;
      }
   }

   @Override
   public void onEnable() {
      IrcManager.getInstance().setEnabled(true);
   }

   @Override
   public void onDisable() {
      IrcManager.getInstance().setEnabled(false);
   }

   @Override
   public void updateEventSubscriptions() {
   }
}
