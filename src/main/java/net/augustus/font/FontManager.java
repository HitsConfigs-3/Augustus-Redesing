package net.augustus.font;

import net.augustus.Augustus;
import net.augustus.ui.modern.font.InterFontUtil;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.utils.file.ClientFileUtil;
import java.awt.Font;
import java.awt.FontFormatException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

public class FontManager {
   public static CustomFontUtil consolas30MultiMode;
   public static CustomFontUtil consolas30Booleans;
   public static CustomFontUtil consolas30MultiOption;
   public static DynamicFontUtil tahomaBoldArrayList;
   public static DynamicFontUtil verdanaArrayList;
   public static DynamicFontUtil espArrayList;
   public static CustomFontUtil esp32;
   public static CustomFontUtil consolas30;
   public static CustomFontUtil consolas30BooleanValue;
   public static CustomFontUtil consolas30TextField;
   public static CustomFontUtil rubikBold32;
   public static HashMap customFormattedFonts;
   public static DynamicFontUtil tahomaArrayList;
   public static DynamicFontUtil productSansArrayList;
   public static DynamicFontUtil rubikArrayList;
   public static DynamicFontUtil robotoArrayList;
   public static CustomFontUtil consolas40;
   public static CustomFontUtil esp44;
   public static DynamicFontUtil rubikBoldHud;
   public static DynamicFontUtil arialArrayList;
   public static DynamicFontUtil borelArrayList;
   private static HashSet loadedFontFiles;
   public static CustomFontUtil consolas30Default;
   public static CustomFontUtil esp60;
   public static DynamicFontUtil rubikBoldArrayList;
   public static DynamicFontUtil museoSansArrayList;
   public static CustomFontUtil esp16;
   public static DynamicFontUtil goldBillArrayList;
   public static DynamicFontUtil jelloArrayList;
   public static HashMap customDynamicFonts;
   public static CustomFontUtil consolas32;
   public static InterFontUtil interSection;
   public static InterFontUtil interRegular;
   public static InterFontUtil interSmall;
   public static InterFontUtil interMedium;
   public static InterFontUtil interSemiBold;
   public static InterFontUtil interSemiBoldLarge;
   public static InterFontUtil interBold;
   public static InterFontUtil interBoldTitle;
   public static final String CLIENT_DATA_DIR;
   public static DynamicFontUtil comfortaaArrayList;

   public static void initFonts() {
      esp44 = new CustomFontUtil("Esp", loadFontResource("esp.ttf", 44));
      esp16 = new CustomFontUtil("Esp", loadFontResource("esp.ttf", 16));
      verdanaArrayList = new DynamicFontUtil("verdanaArrayList", "verdana.ttf", 32, 1);
      robotoArrayList = new DynamicFontUtil("robotoArrayList", "roboto.ttf", 32, 1);
      arialArrayList = new DynamicFontUtil("arialArrayList", "arial.ttf", 32, 1);
      comfortaaArrayList = new DynamicFontUtil("comfortaaArrayList", "comfortaa.ttf", 32, 1);
      espArrayList = new DynamicFontUtil("espArrayList", "esp.ttf", 32, 1);
      goldBillArrayList = new DynamicFontUtil("goldBilArrayList", "goldbill-xsextrabold.ttf", 32, 1);
      jelloArrayList = new DynamicFontUtil("jelloArrayList", "jello.ttf", 32, 1);
      productSansArrayList = new DynamicFontUtil("productSansArrayList", "productsans.ttf", 32, 1);
      museoSansArrayList = new DynamicFontUtil("museosansarraylist", "museosans.ttf", 32, 1);
      borelArrayList = new DynamicFontUtil("borelarraylist", "borel.ttf", 32, 1);
      rubikArrayList = new DynamicFontUtil("rubikarraylist", "rubik.ttf", 32, 1);
      rubikBoldArrayList = new DynamicFontUtil("rubikboldarraylist", "rubik-bold.ttf", 32, 1);
      tahomaArrayList = new DynamicFontUtil("tahomaarraylist", "tahoma.ttf", 32, 1);
      tahomaBoldArrayList = new DynamicFontUtil("tahomaboldarraylist", "tahoma-bold.ttf", 32, 1);
      rubikBoldHud = new DynamicFontUtil("rubikboldhud", "rubik-bold.ttf", 32, 1);
      esp60 = new CustomFontUtil("Esp", loadFontResource("esp.ttf", 60));
      // Clear typographic hierarchy tuned for crisp high-contrast dark frosted glass (clean AA, no heavy stroke inflation).
      interSection = new InterFontUtil("InterBoldSection12", "inter_bold", "inter-bold.ttf", 12.0F, 0.0F, 0);
      interRegular = new InterFontUtil("InterRegular14", "inter_medium", "inter-medium.ttf", 14.5F, -0.025F, 0);
      interSmall = new InterFontUtil("InterMedium13", "inter_medium", "inter-medium.ttf", 13.5F, 0.0F, 0);
      interMedium = new InterFontUtil("InterMedium16", "inter_medium", "inter-medium.ttf", 16.0F, 0.0F, 0);
      interSemiBold = new InterFontUtil("InterSemiBold18", "inter_semibold", "inter-semibold.ttf", 18.5F, 0.0F, UiTheme.TEXT_SHADOW_SUBTLE);
      interSemiBoldLarge = new InterFontUtil("InterSemiBold19", "inter_semibold", "inter-semibold.ttf", 19.5F, 0.0F, UiTheme.TEXT_SHADOW_SUBTLE);
      interBold = new InterFontUtil("InterBold20", "inter_bold", "inter-bold.ttf", 20.5F, 0.0F, UiTheme.TEXT_SHADOW_SUBTLE);
      interBoldTitle = new InterFontUtil("InterBold22", "inter_bold", "inter-bold.ttf", 22.0F, 0.0F, UiTheme.TEXT_SHADOW_SUBTLE);

      // Route ClickGUI & Settings typography cleanly to crisp Inter SDF variants (Regular 400, Medium 500, SemiBold 600, Bold 700).
      consolas30 = interMedium;
      consolas40 = interBold;
      consolas30Booleans = interMedium;
      consolas30Default = interMedium;
      consolas30MultiMode = interMedium;
      consolas30MultiOption = interMedium;
      consolas30BooleanValue = interMedium;
      consolas30TextField = interMedium;
      consolas32 = interSemiBoldLarge;
      esp32 = interBold;
      rubikBold32 = interBoldTitle;
      reloadCustomFonts();
   }

   private static FormattedUnicodeFontRenderer createFormattedFont(Font font, String str) {
      return new FormattedUnicodeFontRenderer(font.deriveFont(16.0F), 16.0F);
   }

   public static Font loadRawFontResource(String str) throws IOException, FontFormatException {
      try (InputStream resourceAsStream = FontManager.class.getClassLoader().getResourceAsStream("assets/augustus/fonts/" + str)) {
         return Font.createFont(0, resourceAsStream);
      }
   }

   public static Font loadRawFontFile(File file) throws IOException, FontFormatException {
      try (InputStream in = Files.newInputStream(file.toPath())) {
         return Font.createFont(0, in);
      }
   }

   public static Font loadFontResource(String str, int n) {

      try {
         return deriveFontSize(loadRawFontResource(str), n);
      } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
   }

   public static void reloadCustomFonts() {

      try {
         File file = new File(CLIENT_DATA_DIR, "fonts");
         if (!file.exists()) {
            file.mkdirs();
         }

         File[] files = file.listFiles((File file2) -> {
            return isVisibleDirectory(file, file2);
         });
         if (files == null) {
            return;
         }

         for (File file3 : files) {
            if (!loadedFontFiles.contains(file3)) {
               try {
                  Font font = loadRawFontFile(file3);
                  String fontName = font.getFontName();
                  customDynamicFonts.computeIfAbsent(fontName, (obj) -> {
                     return createCustomDynamicFont(file3, (String)obj);
                  });
                  customFormattedFonts.computeIfAbsent(fontName, (obj2) -> {
                     return createFormattedFont(font, (String)obj2);
                  });
               } catch (Throwable e) {
                  e.printStackTrace();
               }

               loadedFontFiles.add(file3);
            }
         }
      } catch (Throwable e2) {
         e2.printStackTrace();
      }
   }

   public static DynamicFontUtil getCustomDynamicFont(String str) {
      return (DynamicFontUtil)customDynamicFonts.get(str);
   }

   public static Font deriveFontSize(Font font, int n) {
      try {
         return font.deriveFont(0, (float)n);
      } catch (Exception e) {
         e.printStackTrace();
         System.err.println("Error loading font");
         return new Font("default", 0, 10);
      }
   }

   private static boolean isVisibleDirectory(File file, File file2) {
      return file.isDirectory() && !file.isHidden();
   }

   public static Font loadFontFile(File file, int n) {

      try {
         return deriveFontSize(loadRawFontFile(file), n);
      } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
   }

   private static DynamicFontUtil createCustomDynamicFont(File file, String str) {
      return new DynamicFontUtil(str, file, 32, 1);
   }



   static {
      customDynamicFonts = new HashMap();
      customFormattedFonts = new HashMap();
      loadedFontFiles = new HashSet();
      CLIENT_DATA_DIR = ClientFileUtil.WORKING_DIR + Augustus.getInstance().getName().toLowerCase(Locale.ROOT);
   }
}
