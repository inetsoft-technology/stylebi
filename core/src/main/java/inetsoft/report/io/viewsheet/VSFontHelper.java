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
}
