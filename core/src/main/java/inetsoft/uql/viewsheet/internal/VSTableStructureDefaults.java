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
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.viewsheet.BorderColors;
import inetsoft.uql.viewsheet.VSFormat;

import java.awt.Color;

/**
 * Resolves the modern table-structure colors (interior gridline, header background/text) for
 * viewsheet table assemblies from the org-scoped modern-visualization gate. Applied at the shared
 * VSTableLens format layer so the live model and every export path agree.
 *
 * Defaults-only by value equality: a color is remapped only when it still equals the shipped
 * "Default Style" value, so a user- or explicitly-styled cell (any other color) is left untouched.
 * Gate off returns the format unchanged. Mirrors VSDensityDefaults.
 *
 * Limitation vs density (which consults a user-set flag, e.g. isUserDataRowHeight): merged cell
 * colors carry no such flag here, so a color a user deliberately set to the legacy default value
 * is indistinguishable from the default and will be modernized. Accepted as narrow and low-impact
 * for this opt-in pass.
 */
public final class VSTableStructureDefaults {
   private VSTableStructureDefaults() {
   }

   /**
    * Whether modern table structure is active: the modern-visualization gate plus its structure
    * toggle, which defaults on when modern is enabled.
    */
   public static boolean isModern() {
      // default on when the modern gate is on; only an explicit "false" opts out (there is no
      // default-value overload on getBooleanProperty, so read the raw property)
      return VSDensityDefaults.isModern() &&
         !"false".equals(SreeEnv.getProperty("viewsheet.modernTableStructure", false, true));
   }

   /**
    * Remap a cell format's shipped Default Style structure colors to the modern palette. Border
    * colors apply to every cell; background/foreground only to header rows.
    */
   public static void applyModern(VSFormat fmt, boolean header) {
      BorderColors bc = fmt.getBorderColors();

      if(bc != null) {
         bc.topColor = modernGridline(bc.topColor);
         bc.bottomColor = modernGridline(bc.bottomColor);
         bc.leftColor = modernGridline(bc.leftColor);
         bc.rightColor = modernGridline(bc.rightColor);
         fmt.setBorderColors(bc);
      }

      if(header) {
         Color bg = fmt.getBackground();
         Color modernBg = modernHeaderBackground(bg);

         if(modernBg != bg) {
            fmt.setBackground(modernBg);
         }

         Color fg = fmt.getForeground();
         Color modernFg = modernHeaderForeground(fg);

         if(modernFg != fg) {
            fmt.setForeground(modernFg);
         }
      }
   }

   // Default Style uses #E6E6E6 for body interior gridlines and #CCCCCC for the header-row
   // separator and outer frame; unify both onto the single modern gridline token.
   static Color modernGridline(Color c) {
      return sameRGB(c, DEFAULT_GRIDLINE) || sameRGB(c, DEFAULT_BORDER) ? MODERN_GRIDLINE : c;
   }

   static Color modernHeaderBackground(Color c) {
      return sameRGB(c, DEFAULT_HEADER_BG) ? MODERN_HEADER_BG : c;
   }

   static Color modernHeaderForeground(Color c) {
      return sameRGB(c, DEFAULT_HEADER_FG) ? MODERN_HEADER_FG : c;
   }

   // compare on rgb only, matching how BorderColors serializes (alpha is dropped there)
   private static boolean sameRGB(Color c, int rgb) {
      return c != null && (c.getRGB() & 0xFFFFFF) == rgb;
   }

   // shipped Default Style values eligible for modernization
   private static final int DEFAULT_GRIDLINE = 0xE6E6E6; // body interior gridlines
   private static final int DEFAULT_BORDER = 0xCCCCCC;    // header-row separator + outer frame
   private static final int DEFAULT_HEADER_BG = 0xFFFFFF;
   private static final int DEFAULT_HEADER_FG = 0x404040;

   // modern warm-neutral structure palette; light mode only, dark deferred
   private static final Color MODERN_GRIDLINE = new Color(0xE8E5DE);
   private static final Color MODERN_HEADER_BG = new Color(0xF1EFEA);
   private static final Color MODERN_HEADER_FG = new Color(0x6A685F);
}
