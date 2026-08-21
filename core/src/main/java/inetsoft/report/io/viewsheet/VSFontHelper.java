/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.report.io.viewsheet;

import inetsoft.uql.viewsheet.internal.VSUtil;

import java.awt.*;
import java.util.Hashtable;
import java.util.Locale;

/**
 * Viewsheet font exporting helper.
 * Defines uniformed default font and font size conversion.
 *
 * @version 8.5, 11/15/2006
 * @author InetSoft Technology Corp
 */
public class VSFontHelper {
   /**
    * Get the default font.
    */
   public static Font getDefaultFont() {
      return VSUtil.getDefaultFont();
   }

   /**
    * Get the font family name to write out when exporting. The family (not the face)
    * name is what office formats expect, since bold/italic are recorded separately -- a
    * face name such as "Roboto Bold" cannot be resolved by the viewer, which then
    * substitutes a font whose metrics differ from the ones used to lay the text out.
    * See bug #75992.
    * @param font the specified font.
    * @return the font family to use when exporting.
    */
   public static String getExportFontFamily(Font font) {
      if(font == null) {
         return null;
      }

      String family = font.getFamily();

      if(family == null || !exportFamilies.containsKey(key(family))) {
         return family;
      }

      // the family collapsed to a java logical family, which means the server could not
      // resolve the requested font. Write out the name that was actually asked for, so a
      // client that does have it renders the same as the browser; only fall back to the
      // mapping when the requested name is itself a logical name, since office cannot
      // resolve those either.
      String name = font.getName();

      if(name != null && !exportFamilies.containsKey(key(name))) {
         return name;
      }

      return exportFamilies.get(key(family));
   }

   private static String key(String family) {
      return family.toLowerCase(Locale.ROOT);
   }

   /**
    * Get the font size.
    * @param font the specified font.
    * @return the font size when exporting.
    */
   public static int getFontSize(Font font) {
      if(font == null) {
         return 9;
      }

      double rate = getFontRate(font);
      return Math.max(1, (int) (font.getSize() * rate));
   }

   /**
    * Get the font conversion rate. (same as FontBiffElement)
    * @param font the specified Font.
    */
   private static double getFontRate(Font font) {
      Double rate = font == null ? null :
         fontrates.get(font.getName().toLowerCase());

      if(rate != null) {
         return rate;
      }

      return OTHER_FONT_RATE;
   }

   private static final Double OTHER_FONT_RATE = 0.85;
   private static final Double DEFAULT_FONT_RATE = 0.9;
   private static Hashtable<String, Double> fontrates = new Hashtable<>();
   static {
      fontrates.put("dialog", DEFAULT_FONT_RATE);
      fontrates.put("comic sans ms", DEFAULT_FONT_RATE);
   }

   // java logical font family -> family that office applications can resolve. The
   // pdf equivalent (FontManager) maps to the base-14 postscript names, which office
   // does not have, so the values here are intentionally different.
   private static final Hashtable<String, String> exportFamilies = new Hashtable<>();
   static {
      exportFamilies.put("dialog", "Arial");
      exportFamilies.put("sansserif", "Arial");
      exportFamilies.put("sans-serif", "Arial");
      exportFamilies.put("serif", "Times New Roman");
      exportFamilies.put("monospaced", "Courier New");
      exportFamilies.put("dialoginput", "Courier New");
   }
}
