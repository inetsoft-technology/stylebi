/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.util.css.CSSDictionary;
import inetsoft.util.css.CSSParameter;

import java.awt.*;

/**
 * Brushing colors.
 *
 * @version 12.3
 * @author InetSoft Technology
 */
public class BrushingColor {
   /**
    * Get the highlight color for brushing.
    */
   public static Color getHighlightColor() {
      CSSDictionary dict = getDictionary();
      Color clr = null;

      if(dict.checkPresent(".brush-highlight-color")) {
         clr = dict.getForeground(new CSSParameter("Chart", null, "brush-highlight-color", null));
      }

      return (clr != null) ? clr : Color.red;
   }

   /**
    * Get the dimmed color for brushing.
    */
   public static Color getDimColor() {
      CSSDictionary dict = getDictionary();
      Color clr = null;

      if(dict.checkPresent(".brush-dim-color")) {
         clr = dict.getForeground(new CSSParameter("Chart", null, "brush-dim-color", null));
      }

      return (clr != null) ? clr : Color.gray;
   }

   private static CSSDictionary getDictionary() {
      if(dict == null || dict.getLastModifiedTime() > lastModified) {
         dict = CSSDictionary.getDictionary();
      }

      lastModified = dict.getLastModifiedTime();
      return dict;
   }

   private static long lastModified;
   private static CSSDictionary dict;
}
