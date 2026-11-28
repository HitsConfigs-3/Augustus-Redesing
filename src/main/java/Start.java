import java.io.File;
import java.util.Arrays;
import net.augustus.utils.OSUtil;
import net.minecraft.client.main.Main;

public class Start {

   public static Object[] concat(Object[] array, Object[] array2) {
      Object[] array3 = Arrays.copyOf(array, array.length + array2.length);
      System.arraycopy(array2, 0, array3, array.length, array2.length);
      return array3;
   }

   private static boolean hasArgument(String[] args, String flag) {
      if (args == null) {
         return false;
      }
      for (String arg : args) {
         if (flag.equalsIgnoreCase(arg)) {
            return true;
         }
      }
      return false;
   }

   private static String resolveAssetsDir() {
      if (new File("assets/indexes").isDirectory()) {
         return "assets";
      }
      if (new File("run/assets/indexes").isDirectory()) {
         return "run/assets";
      }
      try {
         File mcAssets = new File(OSUtil.detectOS().getMinecraftDirectory(), "assets");
         if (new File(mcAssets, "indexes").isDirectory()) {
            return mcAssets.getAbsolutePath();
         }
      } catch (Exception ignored) {
      }
      return "assets";
   }

   /**
    * Resolves the persistent Minecraft game directory when running the standalone JAR without an
    * explicit {@code --gameDir}. Using the system {@code .minecraft} directory ensures upgrades to
    * newer JAR releases preserve existing Augustus configs, accounts, proxies, and vanilla settings.
    */
   private static String resolveGameDir() {
      if (new File("build.gradle").isFile() && new File("run").isDirectory()) {
         return new File("run").getAbsolutePath();
      }
      try {
         File mcDir = new File(OSUtil.detectOS().getMinecraftDirectory());
         if (!mcDir.exists()) {
            mcDir.mkdirs();
         }
         return mcDir.getAbsolutePath();
      } catch (Exception ignored) {
      }
      return ".";
   }

   public static void main(String[] args) {
      String[] baseArgs = hasArgument(args, "--gameDir")
         ? new String[]{
            "--version", "1.8.9", "--accessToken", "0", "--assetsDir", resolveAssetsDir(), "--assetIndex", "1.8", "--userProperties", "{}"
         }
         : new String[]{
            "--version",
            "1.8.9",
            "--accessToken",
            "0",
            "--gameDir",
            resolveGameDir(),
            "--assetsDir",
            resolveAssetsDir(),
            "--assetIndex",
            "1.8",
            "--userProperties",
            "{}"
         };
      Main.main((String[])concat(baseArgs, args));
   }
}
