package net.augustus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Pure-Java port of {@code augoldtolatest.py}: migrates legacy/paid Augustus configs (e.g. b4.20.x)
 * onto the OpenAugustus b4.23.3 schema and encrypted/plain V2 format.
 */
public final class LegacyConfigPort {
   private static final String TEMPLATE_RESOURCE = "/assets/augustus/config_template.json.gz";
   private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

   private static final Map<String, String> MODULE_RENAMES;
   private static final Map<String, String> ENTITY_KEY_ALIASES;
   private static final Map<String, String> TARGET_SORT_REMAPS;
   private static final Map<String, Map<String, String>> STRING_OPTION_REMAPS;
   private static final Map<String, Map<String, List<String>>> SETTING_RENAMES;
   private static final Set<String> COLOR_KEYS;

   private static final List<String> LEGACY_WHOLE_ENCODINGS = Arrays.asList(
      "windows-1251", "windows-1252", "windows-1250", "GB18030", "Shift_JIS", "ISO-8859-1"
   );
   private static final List<String> LEGACY_SINGLE_BYTE_ENCODINGS = Arrays.asList(
      "windows-1251", "windows-1252", "windows-1250", "ISO-8859-1"
   );

   private static JsonObject cachedTemplate;

   static {
      Map<String, String> modRen = new LinkedHashMap<>();
      modRen.put("RenderPotion", "PotionHUD");
      MODULE_RENAMES = Collections.unmodifiableMap(modRen);

      Map<String, String> entAliases = new LinkedHashMap<>();
      entAliases.put("Players", "Player");
      entAliases.put("Mobs", "Mob");
      entAliases.put("Animals", "Animal");
      entAliases.put("ArmorStands", "ArmorStand");
      entAliases.put("Teammates", "Teammate");
      entAliases.put("Villagers", "Villager");
      ENTITY_KEY_ALIASES = Collections.unmodifiableMap(entAliases);

      Map<String, String> sortRemaps = new LinkedHashMap<>();
      sortRemaps.put("UltimateSwitch", "UltimateTarget");
      sortRemaps.put("Ultimate", "UltimateTarget");
      sortRemaps.put("Single", "SingleTarget");
      sortRemaps.put("SingleTarget", "SingleTarget");
      sortRemaps.put("Distance", "Distance");
      sortRemaps.put("Health", "Health");
      sortRemaps.put("Fov", "Fov");
      sortRemaps.put("FOV", "Fov");
      sortRemaps.put("ShortAim", "ShortAim");
      sortRemaps.put("ShortAimSwitch", "ShortAimSwitch");
      TARGET_SORT_REMAPS = Collections.unmodifiableMap(sortRemaps);

      Map<String, Map<String, String>> optRemaps = new LinkedHashMap<>();
      putOptionRemap(optRemaps, "ClientSettings", "BanStatistics", "Left", "Icon");
      putOptionRemap(optRemaps, "FastUse", "PacketMode", "Intave", "Legit");
      putOptionRemap(optRemaps, "NoFall", "Mode", "OnGround", "OnGround2");
      putOptionRemap(optRemaps, "Regen", "Mode", "Intave", "Legit");
      putOptionRemap(optRemaps, "Sneak", "Mode", "Spam", "PrePostSpam");
      putOptionRemap(optRemaps, "TargetHud", "Modes", "Old", "Minecraft");
      putOptionRemap(optRemaps, "TargetHud", "Modes", "New", "Modern");
      STRING_OPTION_REMAPS = Collections.unmodifiableMap(optRemaps);

      Map<String, Map<String, List<String>>> setRen = new LinkedHashMap<>();
      putSettingRename(setRen, "AimAssist", "AimAssistWeapons", "Weapons");
      putSettingRename(setRen, "AimBot", "AimBotWeapons", "Weapons");
      putSettingRename(setRen, "AimBot", "TargetSort", "TargetSorting");
      putSettingRename(setRen, "AntiInvisibles", "Entities", "Targets");
      putSettingRename(setRen, "AutoClicker", "AutoClickerWeapons", "Weapons");
      putSettingRename(setRen, "AutoLeave", "AutoLeaveBool", "AutoLeaveIfHeatsLow");
      putSettingRename(setRen, "AutoLeave", "Heats", "Hearts");
      putSettingRename(setRen, "AutoRegister", "CustomPassword", "Custom password");
      putSettingRename(setRen, "AutoRod", "Fov", "FovYaw", "FovPitch");
      putSettingRename(setRen, "AutoRod", "AutoRodWeaponWhitelist", "WeaponWhitelist");
      putSettingRename(setRen, "AutoRodNew", "Fov", "FovYaw", "FovPitch");
      putSettingRename(setRen, "AutoRodNew", "AutoRodNewWeaponWhitelist", "WeaponWhitelist");
      putSettingRename(setRen, "Blur", "Entities", "Targets");
      putSettingRename(setRen, "BowAimbot", "Entities", "Targets");
      putSettingRename(setRen, "BowAimbot", "Fov", "FovYaw", "FovPitch");
      putSettingRename(setRen, "Chams", "Entities", "Targets");
      putSettingRename(setRen, "ClientSpoofer", "CustomString", "Custom spoof string");
      putSettingRename(setRen, "CustomName", "Cusom", "Custom name");
      putSettingRename(setRen, "DeathCommand", "Message", "Message at death");
      putSettingRename(setRen, "ESP", "Entities", "Targets");
      putSettingRename(setRen, "HUD", "RoudedBackGround", "RoundedBackGround");
      putSettingRename(setRen, "HitBox", "1.8Hitbox", "1.8 Hitbox");
      putSettingRename(setRen, "KillAura", "KillAuraWeapons", "Weapons");
      putSettingRename(setRen, "KillAura", "TargetSort", "TargetSorting");
      putSettingRename(setRen, "KillAura", "CPS", "AdvancedCPS");
      putSettingRename(setRen, "KillAura", "BasicDelay", "AdvancedCPSRecalculateDelay");
      putSettingRename(setRen, "KillAura", "DropMulti", "DropMultiplier");
      putSettingRename(setRen, "KillAura", "SpikeMulti", "SpikeMultiplier");
      putSettingRename(setRen, "KillAura", "ReduceMulti", "ReduceMultiplier");
      putSettingRename(setRen, "KillAura", "ReduceInAir", "ReduceHitsInAir");
      putSettingRename(setRen, "KillMessage", "KillMessageCustom", "CustomKillmessages");
      putSettingRename(setRen, "NoMiningTrace", "NoMiningTraceExcluded weapons", "Excluded weapons");
      putSettingRename(setRen, "Phase", "UpAndDownSteps", "UpwardsSteps", "DownwardsSteps");
      putSettingRename(setRen, "Phase", "UpAndDownTickDelay", "UpwardsTickDelay", "DownwardsTickDelay");
      putSettingRename(setRen, "Shadow", "Entities", "Targets");
      putSettingRename(setRen, "Spammer", "SpammMessage", "Messages");
      putSettingRename(setRen, "TargetESP", "AdjustWidth", "BoxAdjustWidth");
      putSettingRename(setRen, "TargetESP", "AdjustYPos", "BoxAdjustYPos");
      putSettingRename(setRen, "TargetESP", "Height", "BoxHeight");
      putSettingRename(setRen, "TargetESP", "HitColor", "BoxHitColorBoolean");
      putSettingRename(setRen, "TargetESP", "LineWidth", "BoxLineWidth");
      putSettingRename(setRen, "TargetESP", "TargetEspColor", "BoxColor");
      putSettingRename(setRen, "TargetESP", "TargetEspHitColor", "BoxHitColor");
      putSettingRename(setRen, "TargetHud", "BackGroundColor", "MinecraftBackGroundColor");
      putSettingRename(setRen, "TargetHud", "BackGroundColorNew", "ModernBackGroundColor");
      putSettingRename(setRen, "TargetHud", "HeadDamageColorNew", "ModernHeadDamageColor");
      putSettingRename(setRen, "TargetHud", "HealthBackGroundColor", "MinecraftHealthBackGroundColor");
      putSettingRename(setRen, "TargetHud", "HealthBackGroundColorNew", "ModernHealthBackGroundColor");
      putSettingRename(setRen, "TargetHud", "HealthColor", "MinecraftHealthColor");
      putSettingRename(setRen, "TargetHud", "HealthColorNew", "ModernHealthColor");
      putSettingRename(setRen, "TargetHud", "Scale", "MinecraftScale");
      putSettingRename(setRen, "TargetHud", "TextColor", "MinecraftTextColor");
      putSettingRename(setRen, "TargetHud", "TextColorNew", "ModernTextColor");
      putSettingRename(setRen, "ThrowRod", "ThrowRodWeaponWhitelist", "WeaponWhitelist");
      putSettingRename(setRen, "Tracers", "Entities", "Targets");
      putSettingRename(setRen, "Velocity", "Lag", "LagDuration");
      SETTING_RENAMES = Collections.unmodifiableMap(setRen);

      COLOR_KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
         "A", "FA", "R", "F", "AO", "CC", "FC", "CCA", "RS", "RD", "FS", "FD", "AS", "AD", "C"
      )));
   }

   private LegacyConfigPort() {
   }

   private static void putOptionRemap(
      Map<String, Map<String, String>> root, String module, String setting, String fromVal, String toVal
   ) {
      String key = module + "\u0000" + setting;
      Map<String, String> inner = root.get(key);
      if (inner == null) {
         inner = new LinkedHashMap<>();
         root.put(key, inner);
      }
      inner.put(fromVal, toVal);
   }

   private static void putSettingRename(
      Map<String, Map<String, List<String>>> root, String module, String oldSetting, String... newSettings
   ) {
      Map<String, List<String>> inner = root.get(module);
      if (inner == null) {
         inner = new LinkedHashMap<>();
         root.put(module, inner);
      }
      inner.put(oldSetting, Collections.unmodifiableList(Arrays.asList(newSettings)));
   }

   public static final class DecodedInput {
      public final JsonObject document;
      public final boolean wasEncrypted;
      public final String encoding;

      public DecodedInput(JsonObject document, boolean wasEncrypted, String encoding) {
         this.document = document;
         this.wasEncrypted = wasEncrypted;
         this.encoding = encoding;
      }
   }

   public static final class MigrationOptions {
      public String configNameOverride;
      public String authorOverride;
      public boolean editable = true;
      public boolean encryptOutput = true;
      public String fallbackName = "migrated";
   }

   public static final class MigrationResult {
      public final JsonObject migratedDoc;
      public final byte[] jsonBytes;
      public final byte[] outputBytes;
      public final boolean wasEncryptedInput;
      public final String detectedEncoding;
      public final boolean encryptedOutput;
      public final String outputName;
      public final String author;
      public final int migratedModules;
      public final List<String> renamedModules;
      public final List<String> droppedModules;
      public final List<String> newDefaultModules;
      public final int directSettingsCopied;
      public final int renamedSettingsCopied;
      public final List<String> notes;

      public MigrationResult(
         JsonObject migratedDoc,
         byte[] jsonBytes,
         byte[] outputBytes,
         boolean wasEncryptedInput,
         String detectedEncoding,
         boolean encryptedOutput,
         String outputName,
         String author,
         int migratedModules,
         List<String> renamedModules,
         List<String> droppedModules,
         List<String> newDefaultModules,
         int directSettingsCopied,
         int renamedSettingsCopied,
         List<String> notes
      ) {
         this.migratedDoc = migratedDoc;
         this.jsonBytes = jsonBytes;
         this.outputBytes = outputBytes;
         this.wasEncryptedInput = wasEncryptedInput;
         this.detectedEncoding = detectedEncoding;
         this.encryptedOutput = encryptedOutput;
         this.outputName = outputName;
         this.author = author;
         this.migratedModules = migratedModules;
         this.renamedModules = renamedModules;
         this.droppedModules = droppedModules;
         this.newDefaultModules = newDefaultModules;
         this.directSettingsCopied = directSettingsCopied;
         this.renamedSettingsCopied = renamedSettingsCopied;
         this.notes = notes;
      }
   }

   public static synchronized JsonObject getTemplateConfiguration() {
      if (cachedTemplate == null) {
         try (InputStream is = LegacyConfigPort.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
            if (is == null) {
               throw new IllegalStateException("Missing embedded resource " + TEMPLATE_RESOURCE);
            }
            try (
               GZIPInputStream gzip = new GZIPInputStream(is);
               InputStreamReader reader = new InputStreamReader(gzip, StandardCharsets.UTF_8)
            ) {
               JsonElement el = new JsonParser().parse(reader);
               if (el == null || !el.isJsonObject()) {
                  throw new IllegalStateException("Invalid JSON template in " + TEMPLATE_RESOURCE);
               }
               JsonObject obj = el.getAsJsonObject();
               if (obj.has("Configuration") && obj.get("Configuration").isJsonObject()) {
                  obj = obj.getAsJsonObject("Configuration");
               }
               cachedTemplate = obj;
            }
         } catch (Exception e) {
            throw new RuntimeException("Failed to load config template: " + e.getMessage(), e);
         }
      }
      return cachedTemplate.deepCopy();
   }

   public static MigrationResult migrateFile(File inputFile, MigrationOptions options) throws Exception {
      byte[] raw = Files.readAllBytes(inputFile.toPath());
      String baseName = stripJsonExtension(inputFile.getName());
      if (options == null) {
         options = new MigrationOptions();
      }
      if (options.fallbackName == null || options.fallbackName.trim().isEmpty()) {
         options.fallbackName = baseName;
      }
      return migrateBytes(raw, options);
   }

   public static MigrationResult migrateText(String rawText, MigrationOptions options) throws Exception {
      if (rawText == null || rawText.trim().isEmpty()) {
         throw new IllegalArgumentException("Input config text is empty");
      }
      return migrateBytes(rawText.getBytes(StandardCharsets.UTF_8), options);
   }

   public static MigrationResult migrateBytes(byte[] raw, MigrationOptions options) throws Exception {
      if (options == null) {
         options = new MigrationOptions();
      }
      DecodedInput decoded = decodeInputConfigBytes(raw);
      JsonObject templateCfg = getTemplateConfiguration();
      return migrateDocument(decoded, templateCfg, options);
   }

   public static DecodedInput decodeInputConfigBytes(byte[] raw) {
      if (raw == null || raw.length == 0) {
         throw new IllegalArgumentException("Input config is empty (0 bytes)");
      }
      boolean wasEncrypted = ModuleConfigStorage.hasEncryptedHeader(raw);
      byte[] payload = raw;
      if (wasEncrypted) {
         payload = ModuleConfigStorage.decryptConfigRawBytes(raw);
      } else {
         String trimmed = new String(raw, StandardCharsets.UTF_8).trim();
         if (!trimmed.startsWith("{")) {
            String diagText = ModuleConfigStorage.decryptConfigBytes(raw);
            if (diagText != null && diagText.trim().startsWith("{")) {
               payload = diagText.getBytes(StandardCharsets.UTF_8);
               wasEncrypted = true;
            }
         }
      }

      DecodedTextResult textRes = decodeJsonBytesToText(payload);
      JsonObject parsed = parseJsonObjectLenient(textRes.text);
      if (parsed == null) {
         throw new IllegalArgumentException("Could not parse valid JSON object (detected encoding: " + textRes.encoding + ")");
      }
      return new DecodedInput(parsed, wasEncrypted, textRes.encoding);
   }

   private static JsonObject parseJsonObjectLenient(String text) {
      if (text == null) {
         return null;
      }
      String trimmed = text.trim();
      if (trimmed.isEmpty()) {
         return null;
      }
      try {
         JsonReader reader = new JsonReader(new StringReader(trimmed));
         reader.setLenient(true);
         JsonElement el = new JsonParser().parse(reader);
         if (el != null && el.isJsonObject()) {
            return el.getAsJsonObject();
         }
      } catch (Exception ignored) {
      }
      int firstBrace = trimmed.indexOf('{');
      int lastBrace = trimmed.lastIndexOf('}');
      if (firstBrace >= 0 && lastBrace > firstBrace) {
         String sliced = trimmed.substring(firstBrace, lastBrace + 1);
         try {
            JsonReader reader = new JsonReader(new StringReader(sliced));
            reader.setLenient(true);
            JsonElement el = new JsonParser().parse(reader);
            if (el != null && el.isJsonObject()) {
               return el.getAsJsonObject();
            }
         } catch (Exception ignored) {
         }
      }
      return null;
   }

   public static MigrationResult migrateDocument(DecodedInput input, JsonObject templateCfg, MigrationOptions options) {
      JsonObject oldDoc = input.document;
      if (oldDoc == null) {
         throw new IllegalArgumentException("Input config root must be a JSON object");
      }

      JsonObject oldCfg;
      JsonObject oldHeader;
      if (oldDoc.has("Configuration") && oldDoc.get("Configuration").isJsonObject()) {
         oldCfg = oldDoc.getAsJsonObject("Configuration");
         oldHeader = oldDoc.has("Config_Header") && oldDoc.get("Config_Header").isJsonObject()
            ? oldDoc.getAsJsonObject("Config_Header")
            : new JsonObject();
      } else {
         oldCfg = oldDoc;
         oldHeader = new JsonObject();
      }

      Date now = new Date();
      String fallbackName = isBlank(options.fallbackName) ? "migrated" : options.fallbackName.trim();
      String cfgName = !isBlank(options.configNameOverride)
         ? options.configNameOverride.trim()
         : (getStrOrNull(oldHeader, "Config_Name") != null ? getStrOrNull(oldHeader, "Config_Name") : fallbackName);
      String cfgDate = getStrOrNull(oldHeader, "Config_Date");
      if (cfgDate == null) {
         cfgDate = new SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).format(now);
      }
      String cfgTime = getStrOrNull(oldHeader, "Config_Time");
      if (cfgTime == null) {
         cfgTime = new SimpleDateFormat("HH:mm", Locale.ENGLISH).format(now);
      }
      String cfgAuthor = !isBlank(options.authorOverride)
         ? options.authorOverride.trim()
         : (getStrOrNull(oldHeader, "Config_Author") != null ? getStrOrNull(oldHeader, "Config_Author") : "No_Author");
      String cfgEditable = options.editable ? "true" : "false";

      JsonObject newHeader = new JsonObject();
      newHeader.addProperty("Config_Version", Integer.valueOf(2));
      newHeader.addProperty("Config_Name", cfgName);
      newHeader.addProperty("Config_Date", cfgDate);
      newHeader.addProperty("Config_Time", cfgTime);
      newHeader.addProperty("Config_Editable", cfgEditable);
      newHeader.addProperty("Config_Author", cfgAuthor);

      JsonObject newCfg = templateCfg.deepCopy();
      List<String> notes = new ArrayList<>();
      List<String> renamedModules = new ArrayList<>();
      List<String> droppedModules = new ArrayList<>();
      List<String> newDefaultModules = new ArrayList<>();
      int migratedModules = 0;
      int directSettingsCopied = 0;
      int renamedSettingsCopied = 0;

      Map<String, JsonObject> sourceModules = new LinkedHashMap<>();
      for (Map.Entry<String, JsonElement> entry : oldCfg.entrySet()) {
         String rawModName = entry.getKey();
         JsonElement rawModVal = entry.getValue();
         if (rawModVal == null || !rawModVal.isJsonObject()) {
            continue;
         }
         String targetModName = MODULE_RENAMES.containsKey(rawModName) ? MODULE_RENAMES.get(rawModName) : rawModName;
         if (!targetModName.equals(rawModName)) {
            renamedModules.add(rawModName + " -> " + targetModName);
            notes.add("Module renamed: " + rawModName + " -> " + targetModName);
         }
         if (!newCfg.has(targetModName) || !newCfg.get(targetModName).isJsonObject()) {
            droppedModules.add(rawModName);
            continue;
         }
         sourceModules.put(targetModName, rawModVal.getAsJsonObject());
      }

      for (Map.Entry<String, JsonElement> tmplEntry : newCfg.entrySet()) {
         String modName = tmplEntry.getKey();
         if (!tmplEntry.getValue().isJsonObject()) {
            continue;
         }
         JsonObject tmplMod = tmplEntry.getValue().getAsJsonObject();
         JsonObject oldMod = sourceModules.get(modName);
         if (oldMod == null) {
            newDefaultModules.add(modName);
            continue;
         }
         migratedModules++;

         for (Map.Entry<String, JsonElement> settingEntry : oldMod.entrySet()) {
            String oldK = settingEntry.getKey();
            JsonElement oldV = settingEntry.getValue();
            if (tmplMod.has(oldK)) {
               tmplMod.add(oldK, coerceValue(modName, oldK, oldV, tmplMod.get(oldK), notes));
               directSettingsCopied++;
            } else {
               String stripped = oldK.trim();
               if (!stripped.equals(oldK) && tmplMod.has(stripped) && !oldMod.has(stripped)) {
                  tmplMod.add(stripped, coerceValue(modName, stripped, oldV, tmplMod.get(stripped), notes));
                  renamedSettingsCopied++;
               }
            }
         }

         Map<String, List<String>> modRenames = SETTING_RENAMES.get(modName);
         if (modRenames != null) {
            for (Map.Entry<String, List<String>> renameEntry : modRenames.entrySet()) {
               String oldK = renameEntry.getKey();
               if (!oldMod.has(oldK)) {
                  continue;
               }
               JsonElement oldVal = oldMod.get(oldK);
               for (String dstK : renameEntry.getValue()) {
                  if (tmplMod.has(dstK)) {
                     tmplMod.add(dstK, coerceValue(modName, dstK, oldVal, tmplMod.get(dstK), notes));
                     renamedSettingsCopied++;
                     notes.add(modName + ": " + oldK + " -> " + dstK);
                  }
               }
            }
         }

         applySpecialModuleMigrations(modName, oldMod, tmplMod, notes);
      }

      JsonObject resultDoc = new JsonObject();
      resultDoc.add("Config_Header", newHeader);
      resultDoc.add("Configuration", newCfg);

      String jsonStr = PRETTY_GSON.toJson(resultDoc);
      byte[] jsonBytes = jsonStr.getBytes(StandardCharsets.UTF_8);
      byte[] outputBytes = options.encryptOutput ? ModuleConfigStorage.encryptConfigBytes(jsonBytes) : jsonBytes;

      return new MigrationResult(
         resultDoc,
         jsonBytes,
         outputBytes,
         input.wasEncrypted,
         input.encoding,
         options.encryptOutput,
         cfgName,
         cfgAuthor,
         migratedModules,
         renamedModules,
         droppedModules,
         newDefaultModules,
         directSettingsCopied,
         renamedSettingsCopied,
         notes
      );
   }

   private static JsonElement coerceValue(
      String moduleName, String settingName, JsonElement oldVal, JsonElement tmplVal, List<String> notes
   ) {
      if (tmplVal == null || oldVal == null || oldVal.isJsonNull()) {
         return tmplVal == null ? JsonParser.parseString("null") : tmplVal.deepCopy();
      }

      if (!tmplVal.isJsonObject()) {
         if (tmplVal.isJsonPrimitive() && oldVal.isJsonPrimitive()) {
            JsonPrimitive tp = tmplVal.getAsJsonPrimitive();
            JsonPrimitive op = oldVal.getAsJsonPrimitive();
            if (tp.isBoolean()) {
               if (op.isBoolean()) {
                  return new JsonPrimitive(Boolean.valueOf(op.getAsBoolean()));
               }
               if (op.isNumber()) {
                  return new JsonPrimitive(Boolean.valueOf(op.getAsDouble() != 0.0));
               }
               return new JsonPrimitive(Boolean.valueOf(!op.getAsString().isEmpty()));
            }
            if (tp.isNumber()) {
               boolean tmplIsInt = isIntegerToken(tp);
               if (!op.isBoolean()) {
                  try {
                     if (tmplIsInt) {
                        return new JsonPrimitive(Long.valueOf((long)Double.parseDouble(op.getAsString())));
                     }
                     return new JsonPrimitive(Double.valueOf(Double.parseDouble(op.getAsString())));
                  } catch (NumberFormatException ignored) {
                  }
               }
               return tmplVal.deepCopy();
            }
            if (tp.isString() && op.isString()) {
               return new JsonPrimitive(op.getAsString());
            }
         }
         return tmplVal.deepCopy();
      }

      if (!oldVal.isJsonObject()) {
         return tmplVal.deepCopy();
      }

      JsonObject tmplObj = tmplVal.getAsJsonObject();
      JsonObject oldObj = oldVal.getAsJsonObject();
      Set<String> tmplKeys = tmplObj.keySet();
      Set<String> oldKeys = oldObj.keySet();

      if (tmplKeys.isEmpty()) {
         return new JsonObject();
      }

      // 1. TextInputSetting: {"N": str} (was often {"S": str})
      if (tmplKeys.size() == 1 && tmplKeys.contains("N") && isStringMember(tmplObj, "N")) {
         if (isStringMember(oldObj, "N")) {
            return singleProp("N", oldObj.get("N").getAsString());
         }
         if (isStringMember(oldObj, "S")) {
            String s = oldObj.get("S").getAsString();
            notes.add(moduleName + "." + settingName + ": S->N ('" + s + "')");
            return singleProp("N", s);
         }
         return tmplObj.deepCopy();
      }

      // 2. StringValue / NamedOptionSetting: {"S": str}
      if (tmplKeys.size() == 1 && tmplKeys.contains("S") && isStringMember(tmplObj, "S")) {
         if (isStringMember(oldObj, "S")) {
            String val = oldObj.get("S").getAsString();
            if ("TargetSorting".equals(settingName) && TARGET_SORT_REMAPS.containsKey(val)) {
               String mapped = TARGET_SORT_REMAPS.get(val);
               if (!mapped.equals(val)) {
                  notes.add(moduleName + "." + settingName + ": '" + val + "' -> '" + mapped + "'");
               }
               val = mapped;
            }
            Map<String, String> remap = STRING_OPTION_REMAPS.get(moduleName + "\u0000" + settingName);
            if (remap != null && remap.containsKey(val)) {
               String mapped = remap.get(val);
               notes.add(moduleName + "." + settingName + ": '" + val + "' -> '" + mapped + "'");
               val = mapped;
            }
            return singleProp("S", val);
         }
         return tmplObj.deepCopy();
      }

      // 3. DoubleValue: {"V": float}
      if (tmplKeys.size() == 1 && tmplKeys.contains("V") && isNumberMember(tmplObj, "V")) {
         if (isNumberMember(oldObj, "V")) {
            JsonObject out = new JsonObject();
            out.addProperty("V", Double.valueOf(oldObj.get("V").getAsDouble()));
            return out;
         }
         return tmplObj.deepCopy();
      }

      // 4. RangeSetting: {"F": float, "S": float}
      if (tmplKeys.size() == 2 && tmplKeys.contains("F") && tmplKeys.contains("S")
         && isNumberMember(tmplObj, "F") && isNumberMember(tmplObj, "S")) {
         if (isNumberMember(oldObj, "F") && isNumberMember(oldObj, "S")) {
            JsonObject out = new JsonObject();
            out.addProperty("F", Double.valueOf(oldObj.get("F").getAsDouble()));
            out.addProperty("S", Double.valueOf(oldObj.get("S").getAsDouble()));
            return out;
         }
         if (isNumberMember(oldObj, "V")) {
            double v = oldObj.get("V").getAsDouble();
            notes.add(moduleName + "." + settingName + ": V(" + v + ") -> Range(F=" + v + ", S=" + v + ")");
            JsonObject out = new JsonObject();
            out.addProperty("F", Double.valueOf(v));
            out.addProperty("S", Double.valueOf(v));
            return out;
         }
         return tmplObj.deepCopy();
      }

      // 5. BooleanValue: {"B": bool}
      if (tmplKeys.size() == 1 && tmplKeys.contains("B") && isBooleanMember(tmplObj, "B")) {
         if (isBooleanMember(oldObj, "B")) {
            JsonObject out = new JsonObject();
            out.addProperty("B", Boolean.valueOf(oldObj.get("B").getAsBoolean()));
            return out;
         }
         return tmplObj.deepCopy();
      }

      // 6. KeybindSetting: {"K": int}
      if (tmplKeys.size() == 1 && tmplKeys.contains("K") && isNumberMember(tmplObj, "K")) {
         if (isNumberMember(oldObj, "K")) {
            JsonObject out = new JsonObject();
            out.addProperty("K", Integer.valueOf(oldObj.get("K").getAsInt()));
            return out;
         }
         return tmplObj.deepCopy();
      }

      // 7. ColorSetting (15 keys)
      if (tmplKeys.equals(COLOR_KEYS)) {
         JsonObject outColor = tmplObj.deepCopy();
         for (Map.Entry<String, JsonElement> ce : tmplObj.entrySet()) {
            String ck = ce.getKey();
            if (!oldObj.has(ck) || !oldObj.get(ck).isJsonPrimitive() || !ce.getValue().isJsonPrimitive()) {
               continue;
            }
            JsonPrimitive defaultCv = ce.getValue().getAsJsonPrimitive();
            JsonPrimitive ov = oldObj.get(ck).getAsJsonPrimitive();
            if (defaultCv.isBoolean()) {
               boolean b = ov.isBoolean() ? ov.getAsBoolean() : (ov.isNumber() && ov.getAsDouble() != 0.0);
               outColor.addProperty(ck, Boolean.valueOf(b));
            } else if (defaultCv.isNumber() && isIntegerToken(defaultCv)) {
               if (!ov.isBoolean()) {
                  try {
                     outColor.addProperty(ck, Integer.valueOf((int)Double.parseDouble(ov.getAsString())));
                  } catch (NumberFormatException ignored) {
                  }
               }
            } else if (defaultCv.isNumber()) {
               if (!ov.isBoolean()) {
                  try {
                     outColor.addProperty(ck, Double.valueOf(Double.parseDouble(ov.getAsString())));
                  } catch (NumberFormatException ignored) {
                  }
               }
            }
         }
         return outColor;
      }

      // 8. DynamicListSetting: {"L": {"Entry_0": {"UniqueId": 0, "Message_0": {"N": "..."}}}}
      if (tmplKeys.size() == 1 && tmplKeys.contains("L") && tmplObj.get("L").isJsonObject()) {
         if (oldObj.has("L") && oldObj.get("L").isJsonObject()) {
            return oldObj.deepCopy();
         }
         if (isStringMember(oldObj, "S")) {
            String text = oldObj.get("S").getAsString();
            if (!text.isEmpty()) {
               notes.add(moduleName + "." + settingName + ": S('" + text + "') -> DynamicList Entry_0");
               JsonObject entry0 = new JsonObject();
               entry0.addProperty("UniqueId", Integer.valueOf(0));
               entry0.add("Message_0", singleProp("N", text));
               JsonObject listObj = new JsonObject();
               listObj.add("Entry_0", entry0);
               JsonObject wrapper = new JsonObject();
               wrapper.add("L", listObj);
               return wrapper;
            }
         }
         return tmplObj.deepCopy();
      }

      // 9. StringListSetting / StringSetSetting / ClickDistributionSetting
      if (tmplKeys.size() == 1) {
         String onlyK = tmplKeys.iterator().next();
         if (tmplObj.get(onlyK).isJsonArray()) {
            if (oldObj.has(onlyK) && oldObj.get(onlyK).isJsonArray()) {
               JsonObject out = new JsonObject();
               out.add(onlyK, oldObj.getAsJsonArray(onlyK).deepCopy());
               return out;
            }
            return tmplObj.deepCopy();
         }
      }

      // 10. MultiOptionSetting / MultiModeSetting: {"OptA": bool, ...}
      if (isBoolMap(tmplObj)) {
         if (oldKeys.size() == 1 && isStringMember(oldObj, "S")) {
            String selected = oldObj.get("S").getAsString();
            JsonObject outMap = new JsonObject();
            String matchedKey = null;
            for (String k : tmplKeys) {
               outMap.addProperty(k, Boolean.FALSE);
               if (k.equals(selected)) {
                  matchedKey = k;
               }
            }
            if (matchedKey == null) {
               String selLow = selected.toLowerCase(Locale.ROOT);
               for (String k : tmplKeys) {
                  if (k.toLowerCase(Locale.ROOT).equals(selLow)) {
                     matchedKey = k;
                     break;
                  }
               }
            }
            if (matchedKey != null) {
               outMap.addProperty(matchedKey, Boolean.TRUE);
               notes.add(moduleName + "." + settingName + ": S('" + selected + "') -> MultiMode[" + matchedKey + "=True]");
               return outMap;
            }
            notes.add(moduleName + "." + settingName + ": S('" + selected + "') had no matching mode; kept defaults");
            return tmplObj.deepCopy();
         }

         JsonObject outMap = tmplObj.deepCopy();
         Map<String, String> lowerLookup = new HashMap<>();
         for (String k : tmplKeys) {
            lowerLookup.put(k.toLowerCase(Locale.ROOT), k);
         }
         for (Map.Entry<String, JsonElement> e : oldObj.entrySet()) {
            String ok = e.getKey();
            JsonElement ovEl = e.getValue();
            if (ovEl == null || !ovEl.isJsonPrimitive() || !ovEl.getAsJsonPrimitive().isBoolean()) {
               continue;
            }
            boolean ov = ovEl.getAsBoolean();
            if (outMap.has(ok)) {
               outMap.addProperty(ok, Boolean.valueOf(ov));
            } else if (ENTITY_KEY_ALIASES.containsKey(ok) && outMap.has(ENTITY_KEY_ALIASES.get(ok))) {
               String mappedK = ENTITY_KEY_ALIASES.get(ok);
               outMap.addProperty(mappedK, Boolean.valueOf(ov));
               notes.add(moduleName + "." + settingName + ": key '" + ok + "' -> '" + mappedK + "'=" + ov);
            } else if ("AntiStrike".equals(moduleName) && "Components to hide".equals(settingName) && "NameTags".equals(ok)) {
               if (outMap.has("OtherNameTags")) {
                  outMap.addProperty("OtherNameTags", Boolean.valueOf(ov));
               }
               if (outMap.has("PlayerNameTags")) {
                  outMap.addProperty("PlayerNameTags", Boolean.valueOf(ov));
               }
               notes.add(moduleName + "." + settingName + ": NameTags=" + ov + " -> OtherNameTags & PlayerNameTags");
            } else {
               String lowMatch = lowerLookup.get(ok.toLowerCase(Locale.ROOT));
               if (lowMatch != null) {
                  outMap.addProperty(lowMatch, Boolean.valueOf(ov));
               }
            }
         }
         return outMap;
      }

      if (oldKeys.equals(tmplKeys)) {
         return oldObj.deepCopy();
      }
      JsonObject merged = tmplObj.deepCopy();
      for (String k : tmplKeys) {
         if (oldObj.has(k)) {
            merged.add(k, oldObj.get(k).deepCopy());
         }
      }
      return merged;
   }

   private static void applySpecialModuleMigrations(
      String modName, JsonObject oldMod, JsonObject newMod, List<String> notes
   ) {
      if ("AimAssist".equals(modName)) {
         if (Boolean.TRUE.equals(readBoolProp(oldMod, "Single target", "B")) && newMod.has("TargetSorting")) {
            newMod.add("TargetSorting", singleProp("S", "SingleTarget"));
            notes.add("AimAssist: Single target=True -> TargetSorting='SingleTarget'");
         }
      } else if ("AutoBlock".equals(modName)) {
         Boolean idt = readBoolProp(oldMod, "IgnoreDeadTargets", "B");
         if (idt != null && newMod.has("Targets") && newMod.get("Targets").isJsonObject()) {
            JsonObject targets = newMod.getAsJsonObject("Targets");
            if (targets.has("Dead")) {
               targets.addProperty("Dead", Boolean.valueOf(!idt.booleanValue()));
               notes.add("AutoBlock: IgnoreDeadTargets=" + idt + " -> Targets.Dead=" + (!idt.booleanValue()));
            }
         }
      } else if ("AutoClicker".equals(modName) || "KillAura".equals(modName)) {
         String cp = readStrProp(oldMod, "ClickPattern", "S");
         if (cp != null && !cp.isEmpty()) {
            for (String dst : new String[]{"DistributionClickPattern", "RecordedClickPattern"}) {
               if (newMod.has(dst)) {
                  newMod.add(dst, singleProp("S", cp));
               }
            }
            notes.add(modName + ": ClickPattern='" + cp + "' -> DistributionClickPattern & RecordedClickPattern");
         }
      }

      if ("KillAura".equals(modName)) {
         Boolean tw = readBoolProp(oldMod, "ThroughWalls", "B");
         if (tw != null && newMod.has("ThroughWallsMode")) {
            String modeVal = tw.booleanValue() ? "AllBlocks" : "None";
            newMod.add("ThroughWallsMode", singleProp("S", modeVal));
            notes.add("KillAura: ThroughWalls=" + tw + " -> ThroughWallsMode='" + modeVal + "'");
         }
      } else if ("FastBreak".equals(modName)) {
         Boolean nbhd = readBoolProp(oldMod, "NoBlockHitDelay", "B");
         if (nbhd != null) {
            if (newMod.has("CustomBlockHitDelay")) {
               JsonObject bObj = new JsonObject();
               bObj.addProperty("B", nbhd);
               newMod.add("CustomBlockHitDelay", bObj);
            }
            if (nbhd.booleanValue() && newMod.has("BlockHitDelay")) {
               JsonObject vObj = new JsonObject();
               vObj.addProperty("V", Double.valueOf(0.0));
               newMod.add("BlockHitDelay", vObj);
            }
            notes.add("FastBreak: NoBlockHitDelay=" + nbhd + " -> CustomBlockHitDelay=" + nbhd + (nbhd.booleanValue() ? ", BlockHitDelay=0.0" : ""));
         }
      } else if ("Hotbar".equals(modName)) {
         Boolean ch = readBoolProp(oldMod, "CustomHotbar", "B");
         if (ch != null && newMod.has("Mode")) {
            String modeVal = ch.booleanValue() ? "FullBar" : "Vanilla";
            newMod.add("Mode", singleProp("S", modeVal));
            notes.add("Hotbar: CustomHotbar=" + ch + " -> Mode='" + modeVal + "'");
         }
      } else if ("Scaffold".equals(modName)) {
         Boolean flick = readBoolProp(oldMod, "Flick", "B");
         Boolean otherFlick = readBoolProp(oldMod, "OtherFlick", "B");
         if ((flick != null || otherFlick != null) && newMod.has("FlickMode")) {
            boolean fVal = Boolean.TRUE.equals(flick);
            boolean ofVal = Boolean.TRUE.equals(otherFlick);
            String fm = ofVal ? "Jump" : (fVal ? "Simple" : "None");
            newMod.add("FlickMode", singleProp("S", fm));
            notes.add("Scaffold: Flick=" + fVal + ", OtherFlick=" + ofVal + " -> FlickMode='" + fm + "'");
         }
         if (Boolean.TRUE.equals(readBoolProp(oldMod, "IgnoreMaxPitchCorrectionIfBlockClutch", "B"))
            && newMod.has("MaxPitchCorrectionAtBlockClutch")) {
            JsonObject vObj = new JsonObject();
            vObj.addProperty("V", Double.valueOf(180.0));
            newMod.add("MaxPitchCorrectionAtBlockClutch", vObj);
            notes.add("Scaffold: IgnoreMaxPitchCorrectionIfBlockClutch=True -> MaxPitchCorrectionAtBlockClutch=180.0");
         }
         if (Boolean.TRUE.equals(readBoolProp(oldMod, "RemoveRotationDeltaLimitAtBlockClutch", "B"))) {
            for (String k : new String[]{"MaxPitchRotationDeltaToPlaceBlocksAtBlockClutch", "MaxYawRotationDeltaToPlaceBlocksAtBlockClutch"}) {
               if (newMod.has(k)) {
                  JsonObject vObj = new JsonObject();
                  vObj.addProperty("V", Double.valueOf(180.0));
                  newMod.add(k, vObj);
               }
            }
            notes.add("Scaffold: RemoveRotationDeltaLimitAtBlockClutch=True -> MaxPitch/YawRotationDelta=180.0");
         }
      } else if ("Velocity".equals(modName)) {
         String selected = readStrProp(oldMod, "Mode", "S");
         if ("Intave".equals(selected) && newMod.has("Mode") && newMod.get("Mode").isJsonObject()) {
            JsonObject modeObj = newMod.getAsJsonObject("Mode");
            if (Boolean.TRUE.equals(readBoolProp(oldMod, "Jump", "B"))) {
               if (modeObj.has("Legit")) {
                  modeObj.addProperty("Legit", Boolean.TRUE);
                  notes.add("Velocity: old Intave+Jump=True -> enabled Mode.Legit=True");
               }
               String[][] intaveToLegit = new String[][]{
                  {"JumpInInvIntave", "JumpInInv"},
                  {"IntaveJumpDelayMode", "LegitJumpDelayMode"},
                  {"JumpChanceIntave", "JumpChanceLegit"},
                  {"CheckFallDamageIntave", "CheckFallDamageLegit"},
                  {"FallDistanceIntave", "FallDistanceLegit"},
                  {"FallDistanceBufferSizeIntave", "FallDistanceBufferSizeLegit"},
                  {"NotWhileSpeedIntave", "NotWhileSpeedLegit"},
                  {"NotWhileJumpBoostIntave", "NotWhileJumpBoostLegit"}
               };
               for (String[] pair : intaveToLegit) {
                  String srcK = pair[0];
                  String dstK = pair[1];
                  if (oldMod.has(srcK) && newMod.has(dstK) && !oldMod.has(dstK)) {
                     newMod.add(dstK, coerceValue("Velocity", dstK, oldMod.get(srcK), newMod.get(dstK), notes));
                  }
               }
            }
            if (Boolean.TRUE.equals(readBoolProp(oldMod, "Water", "B")) && modeObj.has("Water")) {
               modeObj.addProperty("Water", Boolean.TRUE);
               notes.add("Velocity: old Intave+Water=True -> enabled Mode.Water=True");
            }
            if (Boolean.TRUE.equals(readBoolProp(oldMod, "Ladder", "B")) && modeObj.has("Ladder")) {
               modeObj.addProperty("Ladder", Boolean.TRUE);
               notes.add("Velocity: old Intave+Ladder=True -> enabled Mode.Ladder=True");
            }
         }
      } else if ("AutoDisable".equals(modName)) {
         String[] suffixes = new String[]{
            "", "Forcedisable modules for x ms", "Retoggle modules after x ms", "SettingsGroupygroupy"
         };
         for (Map.Entry<String, String> modRen : MODULE_RENAMES.entrySet()) {
            for (String suffix : suffixes) {
               String srcK = modRen.getKey() + suffix;
               String dstK = modRen.getValue() + suffix;
               if (oldMod.has(srcK) && newMod.has(dstK)) {
                  newMod.add(dstK, coerceValue(modName, dstK, oldMod.get(srcK), newMod.get(dstK), notes));
               }
            }
         }
      }
   }

   // -------------------------------------------------------------------------
   // Encoding detection & mixed legacy decoding (matches augoldtolatest.py)
   // -------------------------------------------------------------------------

   private static final class DecodedTextResult {
      final String text;
      final String encoding;

      DecodedTextResult(String text, String encoding) {
         this.text = text;
         this.encoding = encoding;
      }
   }

   private static DecodedTextResult decodeJsonBytesToText(byte[] raw) {
      if (startsWith(raw, new byte[]{(byte)0xFF, (byte)0xFE, 0x00, 0x00})) {
         return new DecodedTextResult(stripLeadingBom(new String(raw, Charset.forName("UTF-32LE"))), "utf-32le");
      }
      if (startsWith(raw, new byte[]{0x00, 0x00, (byte)0xFE, (byte)0xFF})) {
         return new DecodedTextResult(stripLeadingBom(new String(raw, Charset.forName("UTF-32BE"))), "utf-32be");
      }
      if (startsWith(raw, new byte[]{(byte)0xFF, (byte)0xFE})) {
         return new DecodedTextResult(stripLeadingBom(new String(raw, StandardCharsets.UTF_16LE)), "utf-16le");
      }
      if (startsWith(raw, new byte[]{(byte)0xFE, (byte)0xFF})) {
         return new DecodedTextResult(stripLeadingBom(new String(raw, StandardCharsets.UTF_16BE)), "utf-16be");
      }

      int utf8Offset = startsWith(raw, new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF}) ? 3 : 0;
      String strictUtf8 = tryDecodeStrict(raw, utf8Offset, raw.length - utf8Offset, StandardCharsets.UTF_8);
      if (strictUtf8 != null) {
         return new DecodedTextResult(stripLeadingBom(strictUtf8), utf8Offset > 0 ? "utf-8-bom" : "utf-8");
      }

      if (hasStrongUtf8Runs(raw)) {
         double bestScore = Double.NEGATIVE_INFINITY;
         String bestText = null;
         String bestEnc = null;
         for (String enc : LEGACY_SINGLE_BYTE_ENCODINGS) {
            Charset cs;
            try {
               cs = Charset.forName(enc);
            } catch (Exception e) {
               continue;
            }
            MixedDecodeResult mixed = decodeMixedUtf8(raw, cs);
            if (parseJsonObjectLenient(mixed.text) == null) {
               continue;
            }
            double sc = scoreDecodedText(mixed.fallbackSample, enc);
            if (bestText == null || sc > bestScore) {
               bestScore = sc;
               bestText = mixed.text;
               bestEnc = "mixed-utf8+" + enc;
            }
         }
         if (bestText != null) {
            return new DecodedTextResult(bestText, bestEnc);
         }
      }

      double bestParsedScore = Double.NEGATIVE_INFINITY;
      String bestParsedText = null;
      String bestParsedEnc = null;
      double bestDecodedScore = Double.NEGATIVE_INFINITY;
      String bestDecodedText = null;
      String bestDecodedEnc = null;

      for (String enc : LEGACY_WHOLE_ENCODINGS) {
         Charset cs;
         try {
            cs = Charset.forName(enc);
         } catch (Exception e) {
            continue;
         }
         String text = tryDecodeStrict(raw, 0, raw.length, cs);
         if (text == null) {
            continue;
         }
         double sc = scoreDecodedText(text, enc);
         if (bestDecodedText == null || sc > bestDecodedScore) {
            bestDecodedScore = sc;
            bestDecodedText = text;
            bestDecodedEnc = enc;
         }
         if (parseJsonObjectLenient(text) != null && (bestParsedText == null || sc > bestParsedScore)) {
            bestParsedScore = sc;
            bestParsedText = text;
            bestParsedEnc = enc;
         }
      }

      if (bestParsedText != null) {
         return new DecodedTextResult(stripLeadingBom(bestParsedText), bestParsedEnc);
      }
      if (bestDecodedText != null) {
         return new DecodedTextResult(stripLeadingBom(bestDecodedText), bestDecodedEnc);
      }
      return new DecodedTextResult(stripLeadingBom(new String(raw, StandardCharsets.UTF_8)), "utf-8-lossy");
   }

   private static String tryDecodeStrict(byte[] bytes, int offset, int length, Charset cs) {
      try {
         CharsetDecoder decoder = cs.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
         CharBuffer cb = decoder.decode(ByteBuffer.wrap(bytes, offset, length));
         return cb.toString();
      } catch (Exception ignored) {
         return null;
      }
   }

   private static boolean hasStrongUtf8Runs(byte[] raw) {
      if (startsWith(raw, new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF})) {
         return true;
      }
      int i = 0;
      while (i < raw.length) {
         if ((raw[i] & 0x80) == 0) {
            i++;
            continue;
         }
         int start = i;
         while (i < raw.length && (raw[i] & 0x80) != 0) {
            i++;
         }
         int chunkLen = i - start;
         if (chunkLen >= 3) {
            String decoded = tryDecodeStrict(raw, start, chunkLen, StandardCharsets.UTF_8);
            if (decoded != null) {
               if (decoded.length() >= 2) {
                  return true;
               }
               for (int j = 0; j < decoded.length(); j++) {
                  if (decoded.charAt(j) >= 0x800) {
                     return true;
                  }
               }
            }
         }
      }
      return false;
   }

   private static final class MixedDecodeResult {
      final String text;
      final String fallbackSample;

      MixedDecodeResult(String text, String fallbackSample) {
         this.text = text;
         this.fallbackSample = fallbackSample;
      }
   }

   private static MixedDecodeResult decodeMixedUtf8(byte[] raw, Charset fallbackCs) {
      int pos = startsWith(raw, new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF}) ? 3 : 0;
      StringBuilder out = new StringBuilder(raw.length);
      StringBuilder sample = new StringBuilder();
      while (pos < raw.length) {
         if ((raw[pos] & 0x80) == 0) {
            out.append((char)(raw[pos] & 0x7F));
            pos++;
            continue;
         }
         int start = pos;
         while (pos < raw.length && (raw[pos] & 0x80) != 0) {
            pos++;
         }
         int chunkLen = pos - start;
         String utf8Chunk = tryDecodeStrict(raw, start, chunkLen, StandardCharsets.UTF_8);
         if (utf8Chunk != null) {
            out.append(utf8Chunk);
         } else {
            String fb = new String(raw, start, chunkLen, fallbackCs);
            out.append(fb);
            if (sample.length() > 0) {
               sample.append(' ');
            }
            if (start > 0 && (raw[start - 1] & 0x80) == 0) {
               sample.append((char)(raw[start - 1] & 0x7F));
            }
            sample.append(fb);
            if (pos < raw.length && (raw[pos] & 0x80) == 0) {
               sample.append((char)(raw[pos] & 0x7F));
            }
         }
      }
      return new MixedDecodeResult(out.toString(), sample.toString());
   }

   private static double scoreDecodedText(String text, String encoding) {
      double score = 0.0;
      int len = text.length();
      int i = 0;
      while (i < len) {
         if (text.charAt(i) <= 0x7F) {
            i++;
            continue;
         }
         int start = i;
         while (i < len && text.charAt(i) > 0x7F) {
            i++;
         }
         int end = i;
         boolean prevAsciiAlpha = start > 0 && text.charAt(start - 1) <= 0x7F && isAsciiAlpha(text.charAt(start - 1));
         boolean nextAsciiAlpha = end < len && text.charAt(end) <= 0x7F && isAsciiAlpha(text.charAt(end));

         int rus = 0;
         int obscureCyr = 0;
         int weirdCyrSym = 0;
         int letters = 0;
         int weirdLatinSym = 0;
         int cjk = 0;
         int fullKana = 0;
         int halfKana = 0;

         for (int j = start; j < end; j++) {
            char ch = text.charAt(j);
            if (ch >= 0x80 && ch <= 0x9F) {
               score -= 100.0;
            } else if (ch == '\uFFFD') {
               score -= 20.0;
            }
            if ((ch >= '\u0410' && ch <= '\u044F') || ch == '\u0401' || ch == '\u0451') {
               rus++;
            } else if (ch >= '\u0400' && ch <= '\u04FF') {
               obscureCyr++;
            }
            if ("¤¦¬±µ¶".indexOf(ch) >= 0) {
               weirdCyrSym++;
            }
            if (Character.isLetter(ch)) {
               letters++;
            }
            if ("×÷¤¦¬¯´¸¹²³¼½¾ÐðÞþ".indexOf(ch) >= 0) {
               weirdLatinSym++;
            }
            if (ch >= '\u4E00' && ch <= '\u9FFF') {
               cjk++;
            }
            if (ch >= '\u3040' && ch <= '\u30FF') {
               fullKana++;
            }
            if (ch >= '\uFF61' && ch <= '\uFF9F') {
               halfKana++;
            }
         }

         int runLen = end - start;
         if ("windows-1251".equals(encoding)) {
            if (rus >= 2) {
               score += rus * 3.0;
            } else if (rus == 1 && (prevAsciiAlpha || nextAsciiAlpha)) {
               score -= 6.0;
            }
            score -= (obscureCyr + weirdCyrSym) * 4.0;
         } else if ("windows-1252".equals(encoding) || "windows-1250".equals(encoding)) {
            if (letters >= 3) {
               score -= letters * 5.0;
            } else if ((letters == 1 || letters == 2) && (prevAsciiAlpha || nextAsciiAlpha)) {
               score += letters * 3.0;
            } else if (letters > 0) {
               score += 0.5;
            }
            score -= weirdLatinSym * 5.0;
            if ("windows-1252".equals(encoding)) {
               score += 0.1;
            }
         } else if ("GB18030".equals(encoding)) {
            score += cjk * 2.5 - (runLen - cjk) * 3.0;
         } else if ("Shift_JIS".equals(encoding)) {
            score += fullKana * 4.0 + cjk * 2.0 - halfKana * 3.0;
            score -= (runLen - fullKana - halfKana - cjk) * 3.0;
         } else if ("ISO-8859-1".equals(encoding)) {
            score -= 50.0;
         }
      }
      return score;
   }

   // -------------------------------------------------------------------------
   // Small helpers
   // -------------------------------------------------------------------------

   private static boolean isAsciiAlpha(char c) {
      return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
   }

   private static boolean startsWith(byte[] data, byte[] prefix) {
      if (data.length < prefix.length) {
         return false;
      }
      for (int i = 0; i < prefix.length; i++) {
         if (data[i] != prefix[i]) {
            return false;
         }
      }
      return true;
   }

   private static String stripLeadingBom(String s) {
      return s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF' ? s.substring(1) : s;
   }

   private static boolean isIntegerToken(JsonPrimitive prim) {
      String raw = prim.getAsString();
      return raw.indexOf('.') < 0 && raw.indexOf('e') < 0 && raw.indexOf('E') < 0;
   }

   private static JsonObject singleProp(String key, String value) {
      JsonObject o = new JsonObject();
      o.addProperty(key, value);
      return o;
   }

   private static boolean isStringMember(JsonObject o, String key) {
      return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isString();
   }

   private static boolean isNumberMember(JsonObject o, String key) {
      return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isNumber();
   }

   private static boolean isBooleanMember(JsonObject o, String key) {
      return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isBoolean();
   }

   private static boolean isBoolMap(JsonObject obj) {
      if (obj.size() == 0) {
         return false;
      }
      for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
         if (e.getValue() == null || !e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isBoolean()) {
            return false;
         }
      }
      return true;
   }

   private static Boolean readBoolProp(JsonObject mod, String setting, String field) {
      if (!mod.has(setting) || !mod.get(setting).isJsonObject()) {
         return null;
      }
      JsonObject inner = mod.getAsJsonObject(setting);
      if (isBooleanMember(inner, field)) {
         return Boolean.valueOf(inner.get(field).getAsBoolean());
      }
      return null;
   }

   private static String readStrProp(JsonObject mod, String setting, String field) {
      if (!mod.has(setting) || !mod.get(setting).isJsonObject()) {
         return null;
      }
      JsonObject inner = mod.getAsJsonObject(setting);
      if (isStringMember(inner, field)) {
         return inner.get(field).getAsString();
      }
      return null;
   }

   private static String getStrOrNull(JsonObject o, String key) {
      if (o != null && isStringMember(o, key)) {
         String v = o.get(key).getAsString();
         return isBlank(v) ? null : v;
      }
      return null;
   }

   private static boolean isBlank(String s) {
      return s == null || s.trim().isEmpty();
   }

   public static String stripJsonExtension(String filename) {
      if (filename == null) {
         return "migrated";
      }
      return filename.toLowerCase(Locale.ROOT).endsWith(".json")
         ? filename.substring(0, filename.length() - 5)
         : filename;
   }
}
