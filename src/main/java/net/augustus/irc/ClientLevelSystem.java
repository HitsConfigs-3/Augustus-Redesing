package net.augustus.irc;

/**
 * Computes Augustus client levels and formatted durations from accumulated client usage time.
 */
public final class ClientLevelSystem {
   private ClientLevelSystem() {
   }

   /**
    * Cumulative usage time required to reach {@code level}.
    * Level 1 starts at 0m, Level 2 at 15m, Level 3 at 45m, Level 4 at 1h 30m, scaling smoothly.
    */
   public static long requiredMillisForLevel(int level) {
      if (level <= 1) {
         return 0L;
      }
      long n = (long)(level - 1);
      return 15L * 60_000L * n * (n + 1L) / 2L;
   }

   public static int levelForPlayTime(long playTimeMillis) {
      long safe = Math.max(0L, playTimeMillis);
      int level = 1;
      while (level < 500 && safe >= requiredMillisForLevel(level + 1)) {
         level++;
      }
      return level;
   }

   public static float progressWithinLevel(long playTimeMillis) {
      long safe = Math.max(0L, playTimeMillis);
      int level = levelForPlayTime(safe);
      long floor = requiredMillisForLevel(level);
      long next = requiredMillisForLevel(level + 1);
      long span = Math.max(1L, next - floor);
      float raw = (float)(safe - floor) / (float)span;
      return raw < 0.0F ? 0.0F : (raw > 1.0F ? 1.0F : raw);
   }

   public static long millisToNextLevel(long playTimeMillis) {
      long safe = Math.max(0L, playTimeMillis);
      int level = levelForPlayTime(safe);
      return Math.max(0L, requiredMillisForLevel(level + 1) - safe);
   }

   public static String formatDuration(long millis) {
      long totalSeconds = Math.max(0L, millis / 1000L);
      long hours = totalSeconds / 3600L;
      long minutes = (totalSeconds % 3600L) / 60L;
      long seconds = totalSeconds % 60L;
      if (hours > 0L) {
         return hours + "h " + minutes + "m";
      }
      if (minutes > 0L) {
         return minutes + "m " + seconds + "s";
      }
      return seconds + "s";
   }

   public static String minecraftColorCode(int level) {
      if (level >= 35) {
         return "§c";
      }
      if (level >= 20) {
         return "§6";
      }
      if (level >= 10) {
         return "§d";
      }
      if (level >= 5) {
         return "§b";
      }
      return "§7";
   }

   public static int badgeAccentArgb(int level, int themeAccentArgb) {
      if (level >= 35) {
         return 0xFFF43F5E;
      }
      if (level >= 20) {
         return 0xFFFBBF24;
      }
      if (level >= 10) {
         return 0xFF000000 | (themeAccentArgb & 0x00FFFFFF);
      }
      if (level >= 5) {
         return 0xFF38BDF8;
      }
      return 0xFFCBD5E1;
   }
}
