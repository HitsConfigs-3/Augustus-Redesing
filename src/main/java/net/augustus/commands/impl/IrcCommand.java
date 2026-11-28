package net.augustus.commands.impl;

import java.util.List;
import net.augustus.commands.Command;
import net.augustus.irc.ClientLevelSystem;
import net.augustus.irc.IrcConfigFile;
import net.augustus.irc.IrcManager;
import net.augustus.irc.IrcUserRecord;

/**
 * CLI access to Augustus IRC chat, exclusive mode toggle, user original-nick lookup, level stats, and linking.
 */
public class IrcCommand extends Command {
   public IrcCommand() {
      super("irc");
   }

   @Override
   public void commandAction(String[] args, String rawMessage) {
      IrcManager irc = IrcManager.getInstance();
      IrcConfigFile cfg = irc.getConfig();
      if (args.length < 2) {
         this.helpMessage();
         return;
      }

      String sub = args[1].toLowerCase();
      if ("chat".equals(sub) || "mode".equals(sub)) {
         boolean next = !cfg.isExclusiveChatMode();
         cfg.setExclusiveChatMode(next);
         this.sendChat("Chat exclusivo de Augustus IRC: " + (next ? "§aActivado" : "§cDesactivado"));
         return;
      }

      if ("list".equals(sub) || "users".equals(sub)) {
         List<IrcUserRecord> users = irc.getOnlineUsersSorted();
         this.sendChat("§dUsuarios en Augustus IRC (" + users.size() + ") §7[" + irc.getConnectionStateLabel() + "§7]:");
         for (IrcUserRecord user : users) {
            String orig = user.getDisplayOriginalNick();
            String lvlColor = ClientLevelSystem.minecraftColorCode(user.getLevel());
            String altSuffix = user.usesDifferentServerNick() ? " §8(en servidor: §7" + user.getCurrentNick() + "§8)" : "";
            String badge = user.isPremiumLinked() ? "§a✔" : "§7•";
            this.sendChat(badge + " §f" + orig + " " + lvlColor + "[Lv." + user.getLevel() + "]" + altSuffix);
         }
         return;
      }

      if ("who".equals(sub) && args.length >= 3) {
         IrcUserRecord match = irc.resolveUserByNameOrUuid(args[2], "");
         if (match == null) {
            this.sendChat("§c" + args[2] + " §7no está conectado al IRC de Augustus.");
         } else {
            this.sendChat(
               "§f" + match.getCurrentNick() + " §7-> Nick Original: §d" + match.getDisplayOriginalNick()
                  + " §8| §7Nivel: " + ClientLevelSystem.minecraftColorCode(match.getLevel()) + match.getLevel()
                  + " §8| " + (match.isPremiumLinked() ? "§aPremium Vinculada" : "§7Sin vincular")
            );
         }
         return;
      }

      if ("level".equals(sub) || "lvl".equals(sub)) {
         long ms = irc.getPlayTimeMillis();
         int lvl = irc.getCurrentLevel();
         long rem = ClientLevelSystem.millisToNextLevel(ms);
         this.sendChat(
            "§dNivel " + lvl + " §7| Uso total: §f" + ClientLevelSystem.formatDuration(ms)
               + " §7| Siguiente nivel en: §f" + ClientLevelSystem.formatDuration(rem)
         );
         return;
      }

      if ("link".equals(sub)) {
         irc.startInteractiveMicrosoftLink();
         this.sendChat("Iniciando vinculación de cuenta Premium Microsoft...");
         return;
      }

      if ("nick".equals(sub) || "autonick".equals(sub)) {
         boolean next = !cfg.isAutoUseOriginalNick();
         cfg.setAutoUseOriginalNick(next);
         this.sendChat("Usar nickname original automáticamente: " + (next ? "§aActivado" : "§cDesactivado"));
         if (next) {
            irc.ensureOriginalNickBeforeConnect();
         }
         return;
      }

      if ("local".equals(sub)) {
         cfg.setTransportMode("Local Hub");
         cfg.setIrcHost("");
         irc.reconnect();
         this.sendChat("Conectando al Hub local/LAN de Augustus IRC...");
         return;
      }

      if ("server".equals(sub) && args.length >= 3) {
         cfg.setTransportMode("External IRC");
         cfg.setIrcHost(args[2]);
         if (args.length >= 4) {
            try {
               cfg.setIrcPort(Integer.parseInt(args[3]));
            } catch (NumberFormatException ignored) {
            }
         }
         if (args.length >= 5) {
            cfg.setIrcChannel(args[4]);
         }
         irc.reconnect();
         this.sendChat("Conectando a servidor IRC §d" + cfg.getIrcHost() + ":" + cfg.getIrcPort() + " §7(" + cfg.getIrcChannel() + ")");
         return;
      }

      String prefix = this.getCommand();
      String msg = rawMessage.length() > prefix.length() ? rawMessage.substring(prefix.length()).trim() : "";
      if (!msg.isEmpty()) {
         irc.sendIrcChat(msg);
      }
   }

   @Override
   public void helpMessage() {
      this.sendChat("§c" + this.getCommand() + "§7 <mensaje | chat | list | who <nick> | level | link | nick>");
   }
}
