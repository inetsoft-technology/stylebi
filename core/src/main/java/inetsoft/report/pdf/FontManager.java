/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.pdf;

import inetsoft.report.internal.AFManager;
import inetsoft.sree.SreeEnv;
import inetsoft.util.*;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;

/**
 * Font manager loads font information from TrueType or AFM files.
 * It can be used to get FontMetrics for a font.
 * The truetype fonts are retrieved from the path specified in the
 * "font.truetype.path" property. The AFM fonts are retrieved from the
 * path specified in the "font.afm.path" property.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class FontManager {
   /**
    * Get a FontManager instance.
    */
   public static synchronized FontManager getFontManager() {
      if(fontMgr == null) {
         return fontMgr = new FontManager();
      }

      if(fontMgr.shouldRefresh()) {
         fontMgr.refresh();
      }

      return fontMgr;
   }

   /**
    * Create a FontManager.
    */
   private FontManager() {
      synchronized(cjkmap) {
         Properties prop = new Properties();

         try {
            InputStream fis = this.getClass().getResourceAsStream(
               "cjkmap.properties");
            prop.load(fis);
         }
         catch(Exception e) {
            LOG.error("Failed to load CJK map properties, " +
               "cjkmap.properties, from: " +
               getClass().getResourceAsStream("cjkmap.properties"), e);
         }


         for(String fname : prop.stringPropertyNames()) {
            String cmap = prop.getProperty(fname).trim();
            String[] cmaparr = cmapmap.get(cmap);
            cjkmap.put(fname.toLowerCase(), cmaparr);
         }
      }

      fontmap.put("dialog", "Helvetica");
      fontmap.put("dialoginput", "Courier");
      fontmap.put("serif", "Times");
      fontmap.put("sansserif", "Helvetica");
      fontmap.put("monospaced", "Courier");
      fontmap.put("timesroman", "Times");
      fontmap.put("courier", "Courier");
      fontmap.put("helvetica", "Helvetica");

      refresh();
   }

   /**
    * Test if should refresh the font manager.
    *
    * @return <code>true</code> if should, <code>false</code> otherwise
    */
   public boolean shouldRefresh() {
      String nttpath = SreeEnv.getProperty("font.truetype.path");
      String nafmpath = SreeEnv.getProperty("font.afm.path");

      if(!nttpath.equals(ttpath)) {
         return true;
      }

      if(nafmpath == null) {
         return afmpath != null;
      }
      else {
         return !nafmpath.equals(afmpath);
      }
   }

   /**
    * Refresh the font manager.
    */
   public void refresh() {
      cache.clear();
      fontlowercasemap.clear();

      ttNameMap.clear();
      ttPsMap.clear();
      ottPsMap.clear();
      ttFullMap.clear();
      afmNameMap.clear();
      afmFullMap.clear();

      ttpath = SreeEnv.getProperty("font.truetype.path");
      boolean found = false;
      StringTokenizer tok = new StringTokenizer(ttpath, ";");
      FileSystemService fileSystemService = FileSystemService.getInstance();
      Path homePath =
         fileSystemService.getPath(ConfigurationContext.getContext().getHome()).toAbsolutePath();
      boolean loaded = false;

      while(tok.hasMoreTokens()) {
         String dir = tok.nextToken();
         Path dirPath = fileSystemService.getPath(dir).toAbsolutePath();

         if(!loaded && dirPath.startsWith(homePath)) {
            loaded = true;

            try {
               syncTrueTypeFonts(dirPath.toFile());
            }
            catch(IOException e) {
               LOG.error("Failed to synchronize fonts from data space to local directory", e);
            }
         }

         File dirfile = dirPath.toFile();

         if(dirfile.exists()) {
            found = true;
            registerTrueTypeFonts(fileSystemService, dirfile);
         }
      }

      // warn only on windows
      if(File.separatorChar == '\\' && !found) {
         LOG.warn("TrueType font directory does not exist: {}", ttpath);
      }

      afmpath = SreeEnv.getProperty("font.afm.path");

      if(afmpath != null) {
         tok = new StringTokenizer(afmpath, ";");

         while(tok.hasMoreTokens()) {
            String dir = tok.nextToken();
            File fontDir = fileSystemService.getFile(dir);

            if(fontDir.exists()) {
               registerFonts(fileSystemService, fontDir);

            }
            else {
               LOG.warn("AFM font directory does not exist: " + dir);
            }
         }
      }
   }

   private void syncTrueTypeFonts(File dir) throws IOException{
      DataSpace dataSpace = DataSpace.getDataSpace();

      if(dataSpace.exists(null, "fonts")) {
         syncTrueTypeFonts(dataSpace, dir, null);
      }
   }

   private void syncTrueTypeFonts(DataSpace space, File root, String path) throws IOException {
      String dsPath;
      File destDir;

      if(path == null) {
         dsPath = "fonts";
         destDir = root;
      }
      else {
         dsPath = path;
         destDir = new File(root, path);
      }

      root.mkdirs();
      String[] dsFiles = space.list(dsPath);

      if(dsFiles != null) {
         for(String dsFile : dsFiles) {
            if(space.isDirectory(dsPath + "/" + dsFile)) {
               syncTrueTypeFonts(space, root, dsPath + "/" + dsFile);
            }
            else {
               File destFile = new File(destDir, dsFile);
               long lastModified = space.getLastModified(dsPath, dsFile);

               if(!destFile.exists() || destFile.lastModified() < lastModified) {
                  try(InputStream input = space.getInputStream(dsPath, dsFile);
                      OutputStream output = new FileOutputStream(destFile))
                  {
                     IOUtils.copy(input, output);
                  }
               }
            }
         }
      }
   }

   private void registerTrueTypeFonts(FileSystemService fileSystemService, File dir) {
      String[] files = dir.list();

      for(String s : files) {
         String fname = s.toLowerCase();
         String[] namearr = {};
         File file = fileSystemService.getFile(dir, s);

         if(file.isDirectory()) {
            registerTrueTypeFonts(fileSystemService, file);
            continue;
         }

         try {
            // check for font collection, the fonts in the collection may
            // not have ascii names. we use the file name here which seems
            // to be safer than trying to parse it out from the font table
            if(fname.toLowerCase().endsWith(".ttc")) {
               ttNameMap.put(fname.substring(0, fname.length() - 4)
                                .toLowerCase(), file);
               namearr = TTFontInfo.getFontNames(file);
            }
            // including CID font in acrobat 4.0
            else if(fname.toLowerCase().endsWith(".ttf") ||
               fname.toLowerCase().endsWith("-acro") ||
               fname.toLowerCase().endsWith(".otf"))
            {
               namearr = TTFontInfo.getFontNames(file);
            }
         }
         catch(Exception e) {
            LOG.warn("Error parsing TrueType font file: " +
                        fileSystemService.getFile(dir, s), e);
         }

         for(int k = 0; k + 2 < namearr.length; k += 3) {
            String[] names = { namearr[k], namearr[k + 1], namearr[k + 2] };

            if(names[0] == null || names[1] == null) {
               LOG.warn(
                  "Missing name in truetype font: " + file + " " +
                     names[0] + " " + names[1]);
               continue;
            }

            ttNameMap.put(names[0].toLowerCase(), file);
            ttFullMap.put(names[1].toLowerCase(), file);

            // ps name not null, construct 'name,BoldItalic' format
            if(names[2] != null) {
               String style = "";

               if(names[2].indexOf("Bold") > 0) {
                  style += "bold";
               }

               if(names[2].indexOf("Italic") > 0) {
                  style += "italic";
               }

               if(style.length() > 0) {
                  style = "," + style;
               }

               ttPsMap.put(names[0].toLowerCase() + style, file);
               ottPsMap.put(names[2].toLowerCase(), file);
            }
         }
      }
   }

   /**
    * Recursively add afm files under a directory
    */
   private void registerFonts(FileSystemService fileSystemService, File fontDir) {
      for(String fileName : fontDir.list()) {
         File file = fileSystemService.getFile(fontDir, fileName);

         if(fileName.toLowerCase().endsWith(".afm")) {
            try {
               String[] names =
                  AFMFontInfo.getFontNames(new FileInputStream(file));

               if(names != null) {
                  afmNameMap.put(names[0].toLowerCase(), file);

                  if(names[1] == null) {
                     afmFullMap.put(names[0].toLowerCase(), file);
                  }
                  else {
                     afmFullMap.put(names[1].toLowerCase(), file);
                  }
               }
            }
            catch(Exception e) {
               LOG.warn("Failed to load AFM file " +
                           fileName + " from directory " + fontDir.getName(), e);
            }
         }
         else if(file.isDirectory()) {
            registerFonts(fileSystemService, file);
         }
      }
   }

   /**
    * Get the FontInfo for the specified font.
    */
   FontInfo getFontInfo(String fontin) {
      // cache the lowercase font for performance
      String font = fontlowercasemap.computeIfAbsent(fontin, String::toLowerCase);
      FontInfo fm = cache.get(font);

      if(fm == null) {
         try {
            fm = loadFontInfo(font);
         }
         catch(Exception e) {
            LOG.warn("Failed to load font info for font: " + font, e);
         }

         // avoid trying to load later if font info does not exists
         cache.put(font, (fm == null) ? new TTFontInfo() : fm);
      }

      return (fm != null && fm.getFamilyName() != null) ? fm : null;
   }

   /**
    * Get the font metrics for the specified font.
    */
   public FontMetrics getFontMetrics(Font font) {
      String javaName = font.getName().toLowerCase();
      String mapped = fontmap.get(javaName);

      if(mapped == null) {
         int dot = javaName.indexOf('.');

         if(dot > 0) {
            mapped = fontmap.get(javaName.substring(0, dot));
         }
      }

      javaName = (mapped == null) ? javaName : mapped;
      String psname = null;
      int javaStyle = font.getStyle();
      String[] psnames = new String[4];

      if((javaStyle & Font.BOLD) != 0 && (javaStyle & Font.ITALIC) != 0) {
         psnames[0] = javaName + "-BoldItalic";
         psnames[1] = javaName + "-BoldOblique";
         psnames[2] = javaName + ",BoldItalic";
      }
      else if((javaStyle & Font.BOLD) != 0) {
         psnames[0] = javaName + "-Bold";
         psnames[1] = javaName + ",Bold";
      }
      else if((javaStyle & Font.ITALIC) != 0) {
         psnames[0] = javaName + "-Italic";
         psnames[1] = javaName + "-Oblique";
         psnames[2] = javaName + ",Italic";
      }
      else {
         psnames[0] = javaName;
         psnames[1] = javaName + "-Roman";
      }

      for(int i = 0; i < psnames.length && psnames[i] != null; i++) {
         if(exists(psnames[i])) {
            psname = psnames[i];
            break;
         }
      }

      psname = (psname != null) ? psname : javaName;

      FontInfo info = getFontInfo(psname);

      if(info != null) {
         return info.getFontMetrics(font);
      }

      FontMetrics fm = AFManager.getFontMetrics(psname, font.getSize());

      if(fm != null) {
         return fm;
      }
      else {
         LOG.info(
            "Font file not found, using Times Roman as default: " + psname +
            ": " + font);
         return AFManager.getFontMetrics("times-roman", font.getSize());
      }
   }

   /**
    * Check whether the font exists in the FontManager.
    */
   public boolean exists(String fontin) {
      // cache the lowercase font for performance
      String font = fontlowercasemap.computeIfAbsent(fontin, String::toLowerCase);

      return ottPsMap.get(font) != null || ttNameMap.get(font) != null ||
         ttFullMap.get(font) != null || ttPsMap.get(font) != null ||
         afmNameMap.get(font) != null || afmFullMap.get(font) != null;
   }

   /**
    * Get the ordering and encoding of a CJK font. If the font is not a
    * CJK font, returns null. Otherwise, the return value is a two-element
    * array. The first element is the font ordering, and the second
    * element is the font encoding.
    */
   String[] getCJKInfo(String fontname) {
      // cache the lowercase font for performance
      String font = fontlowercasemap.computeIfAbsent(fontname, String::toLowerCase);
      return cjkmap.get(font);
   }

   /**
    * Load font information.
    */
   protected FontInfo loadFontInfo(String font) throws IOException {
      File file;

      font = font.toLowerCase();

      if((file = ottPsMap.get(font)) != null) {
         TTFontInfo finfo = new TTFontInfo();

         finfo.parse(file);
         return finfo;
      }

      if((file = ttPsMap.get(font)) != null) {
         TTFontInfo finfo = new TTFontInfo();

         finfo.parse(file);
         return finfo;
      }

      if((file = ttFullMap.get(font)) != null) {
         TTFontInfo finfo = new TTFontInfo();

         finfo.parse(file);
         return finfo;
      }

      if((file = ttNameMap.get(font)) != null) {
         TTFontInfo finfo = new TTFontInfo();

         finfo.parse(file);
         return finfo;
      }

      if((file = afmFullMap.get(font)) != null) {
         AFMFontInfo finfo = new AFMFontInfo();

         finfo.parse(new FileInputStream(file));
         return finfo;
      }

      if((file = afmNameMap.get(font)) != null) {
         AFMFontInfo finfo = new AFMFontInfo();

         finfo.parse(new FileInputStream(file));
         return finfo;
      }

      // @by larryl 2003-9-23, if times roman, add an alias from base14 name
      // this way the base 14 font can find Times New Roman for Times-Roman
      if(font.startsWith("times") && !font.equals("times new roman")) {
         String newfont = "times new roman";
         int dash = font.indexOf('-');

         // capture the bold/italic designation in the font name
         if(dash > 0) {
            String suffix = font.substring(dash + 1);

            if(!suffix.equalsIgnoreCase("roman")) {
               newfont += "," + suffix;
            }
         }

         return loadFontInfo(newfont);
      }

      return null;
   }

   private static FontManager fontMgr = null;

   static String[] jis = {"Japan1", "UniJIS-UCS2-H"};
   static String[] gb = {"GB1", "UniGB-UCS2-H"};
   static String[] b5 = {"CNS1", "UniCNS-UCS2-H"};
   static String[] kor = {"Korea1", "UniKS-UCS2-H"};
   static Hashtable<String, String[]> cmapmap;
   static {
      cmapmap = new Hashtable<>();
      cmapmap.put("jis", jis);
      cmapmap.put("gb", gb);
      cmapmap.put("b5", b5);
      cmapmap.put("kor", kor);
   }

   private String ttpath = null;
   private String afmpath = null;
   private final Hashtable<String, String[]> cjkmap = new Hashtable<>();
   private final Hashtable<String, String> fontmap = new Hashtable<>();
   private final Hashtable<String, String> fontlowercasemap = new Hashtable<>(10);
   private final Hashtable<String, FontInfo> cache = new Hashtable<>(); // name -> FontInfo
   private final Hashtable<String, File> ttNameMap = new Hashtable<>(); // family name -> file
   private final Hashtable<String, File> ttPsMap = new Hashtable<>(); // ps name -> file
   private final Hashtable<String, File> ottPsMap = new Hashtable<>(); // original ps name -> file
   private final Hashtable<String, File> ttFullMap = new Hashtable<>(); // full name -> file
   private final Hashtable<String, File> afmNameMap = new Hashtable<>(); // family name -> file
   private final Hashtable<String, File> afmFullMap = new Hashtable<>(); // full name -> file

   private static final Logger LOG = LoggerFactory.getLogger(FontManager.class);
}
