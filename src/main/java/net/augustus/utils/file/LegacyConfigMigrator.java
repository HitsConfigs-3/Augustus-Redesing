package net.augustus.utils.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.augustus.Augustus;
import net.augustus.utils.OSUtil;
import net.minecraft.client.Minecraft;

/**
 * Automatically carries over existing Augustus configurations (and vanilla {@code options.txt} /
 * {@code servers.dat}) when launching a newly installed JAR or isolated version directory that does
 * not yet contain user configs. If no prior installation exists, nothing is created prematurely and
 * normal first-run initialization proceeds as usual.
 */
public final class LegacyConfigMigrator {
   private static final int MAX_SIBLING_DIRS = 80;

   private LegacyConfigMigrator() {
   }

   public static void migrateIfNeeded() {
      try {
         if (Boolean.getBoolean("augustus.skipMigration")) {
            return;
         }

         File targetGameDir = resolveTargetGameDir();
         if (targetGameDir == null || isDevRunDirectory(targetGameDir)) {
            return;
         }

         String clientDirName = Augustus.getInstance().getName().toLowerCase(Locale.ROOT);
         File targetAugDir = canonical(new File(targetGameDir, clientDirName));
         if (hasUserAugustusData(targetAugDir)) {
            return;
         }

         File sourceAugDir = findBestSourceAugustusDir(targetGameDir, targetAugDir, clientDirName);
         if (sourceAugDir == null) {
            return;
         }

         System.out.println("[Augustus] Migrating existing configs from " + sourceAugDir.getAbsolutePath() + " -> " + targetAugDir.getAbsolutePath());
         copyMissingTree(sourceAugDir.toPath(), targetAugDir.toPath());

         File sourceGameDir = sourceAugDir.getParentFile();
         if (sourceGameDir != null) {
            copyIfMissing(new File(sourceGameDir, "options.txt"), new File(targetGameDir, "options.txt"));
            copyIfMissing(new File(sourceGameDir, "servers.dat"), new File(targetGameDir, "servers.dat"));
         }
      } catch (Throwable t) {
         System.err.println("[Augustus] Config auto-migration skipped due to error: " + t.getMessage());
      }
   }

   private static File resolveTargetGameDir() {
      try {
         Minecraft mc = Minecraft.getMinecraft();
         if (mc != null && mc.mcDataDir != null) {
            return canonical(mc.mcDataDir);
         }
      } catch (Throwable ignored) {
      }
      return canonical(new File(ClientFileUtil.WORKING_DIR));
   }

   private static boolean isDevRunDirectory(File gameDir) {
      if (gameDir == null) {
         return false;
      }
      File parent = gameDir.getParentFile();
      return "run".equals(gameDir.getName()) && parent != null && new File(parent, "build.gradle").isFile();
   }

   private static boolean hasUserAugustusData(File augDir) {
      if (augDir == null || !augDir.isDirectory()) {
         return false;
      }
      if (new File(augDir, "visuals.json").isFile()
         || new File(augDir, "settings/ModuleSettings.json").isFile()
         || new File(augDir, "settings/ClientSettings.json").isFile()
         || new File(augDir, "settings/Accounts.json").isFile()
         || new File(augDir, "settings/Proxys.json").isFile()) {
         return true;
      }
      File configsDir = new File(augDir, "configs");
      if (configsDir.isDirectory()) {
         File[] entries = configsDir.listFiles();
         if (entries != null) {
            for (File entry : entries) {
               if (entry.isFile() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
                  return true;
               }
            }
         }
      }
      return false;
   }

   private static File findBestSourceAugustusDir(File targetGameDir, File targetAugDir, String clientDirName) {
      Set<File> candidates = new LinkedHashSet<>();

      // 1. Standard system .minecraft/augustus (e.g. when a launcher uses an isolated --gameDir)
      try {
         File osMcDir = new File(OSUtil.detectOS().getMinecraftDirectory());
         addCandidate(candidates, new File(osMcDir, clientDirName));
         addVersionChildren(candidates, new File(osMcDir, "versions"), clientDirName);
      } catch (Throwable ignored) {
      }

      // 2. Launch working directory (e.g. upgrading from a previous standalone JAR / launcher/ folder)
      String userDir = System.getProperty("user.dir");
      if (userDir != null && !userDir.isEmpty()) {
         File launchDir = new File(userDir);
         addCandidate(candidates, new File(launchDir, clientDirName));
         if (launchDir.getParentFile() != null) {
            addCandidate(candidates, new File(launchDir.getParentFile(), clientDirName));
         }
      }

      // 3. Sibling version / instance folders around the active gameDir
      addSiblingInstanceCandidates(candidates, targetGameDir, clientDirName);

      // 4. Standalone launcher data roots in AppData / user.home if present
      String appData = System.getenv("APPDATA");
      if (appData != null && !appData.isEmpty()) {
         addCandidate(candidates, new File(appData, "Augustus"));
         addCandidate(candidates, new File(appData, ".augustus"));
      }
      String userHome = System.getProperty("user.home");
      if (userHome != null && !userHome.isEmpty()) {
         addCandidate(candidates, new File(userHome, ".augustus"));
      }

      File best = null;
      long bestScore = -1L;
      for (File candidate : candidates) {
         if (candidate.equals(targetAugDir) || isDevRunDirectory(candidate.getParentFile())) {
            continue;
         }
         if (!hasUserAugustusData(candidate)) {
            continue;
         }
         long ts = latestUserConfigTimestamp(candidate);
         if (best == null || ts > bestScore) {
            best = candidate;
            bestScore = ts;
         }
      }
      return best;
   }

   private static void addSiblingInstanceCandidates(Set<File> candidates, File targetGameDir, String clientDirName) {
      if (targetGameDir == null) {
         return;
      }
      File parent = targetGameDir.getParentFile();
      if (parent != null && parent.isDirectory()) {
         addVersionChildren(candidates, parent, clientDirName);
         File grandParent = parent.getParentFile();
         if (grandParent != null && grandParent.isDirectory()) {
            addVersionChildren(candidates, grandParent, clientDirName);
         }
      }
   }

   private static void addVersionChildren(Set<File> candidates, File containerDir, String clientDirName) {
      if (containerDir == null || !containerDir.isDirectory()) {
         return;
      }
      File[] children = containerDir.listFiles();
      if (children == null) {
         return;
      }
      int checked = 0;
      for (File child : children) {
         if (!child.isDirectory()) {
            continue;
         }
         if (++checked > MAX_SIBLING_DIRS) {
            break;
         }
         addCandidate(candidates, new File(child, clientDirName));
         addCandidate(candidates, new File(new File(child, ".minecraft"), clientDirName));
         addCandidate(candidates, new File(new File(child, "minecraft"), clientDirName));
      }
   }

   private static void addCandidate(Set<File> candidates, File dir) {
      if (dir != null && dir.isDirectory()) {
         candidates.add(canonical(dir));
      }
   }

   private static long latestUserConfigTimestamp(File augDir) {
      List<File> probes = new ArrayList<>();
      probes.add(new File(augDir, "visuals.json"));
      probes.add(new File(augDir, "settings/ModuleSettings.json"));
      probes.add(new File(augDir, "settings/ClientSettings.json"));
      probes.add(new File(augDir, "settings/Accounts.json"));
      probes.add(new File(augDir, "settings/Proxys.json"));

      File configsDir = new File(augDir, "configs");
      if (configsDir.isDirectory()) {
         File[] entries = configsDir.listFiles();
         if (entries != null) {
            for (File f : entries) {
               if (f.isFile()) {
                  probes.add(f);
               }
            }
         }
      }

      long max = 0L;
      for (File f : probes) {
         if (f.isFile()) {
            max = Math.max(max, f.lastModified());
         }
      }
      return max;
   }

   private static void copyMissingTree(Path sourceRoot, Path targetRoot) throws IOException {
      Files.walkFileTree(sourceRoot, new SimpleFileVisitor<Path>() {
         @Override
         public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            Path rel = sourceRoot.relativize(dir);
            Path dst = targetRoot.resolve(rel);
            if (!Files.exists(dst)) {
               Files.createDirectories(dst);
            }
            return FileVisitResult.CONTINUE;
         }

         @Override
         public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".lck")) {
               return FileVisitResult.CONTINUE;
            }
            Path rel = sourceRoot.relativize(file);
            Path dst = targetRoot.resolve(rel);
            if (!Files.exists(dst)) {
               if (dst.getParent() != null && !Files.exists(dst.getParent())) {
                  Files.createDirectories(dst.getParent());
               }
               Files.copy(file, dst, StandardCopyOption.COPY_ATTRIBUTES);
            }
            return FileVisitResult.CONTINUE;
         }
      });
   }

   private static void copyIfMissing(File src, File dst) throws IOException {
      if (src != null && dst != null && src.isFile() && !dst.exists()) {
         if (dst.getParentFile() != null && !dst.getParentFile().exists()) {
            dst.getParentFile().mkdirs();
         }
         Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
      }
   }

   private static File canonical(File file) {
      try {
         return file.getCanonicalFile();
      } catch (IOException e) {
         return file.getAbsoluteFile();
      }
   }
}
