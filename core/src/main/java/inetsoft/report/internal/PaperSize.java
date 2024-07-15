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
package inetsoft.report.internal;

import inetsoft.report.Size;
import inetsoft.report.StyleConstants;

import java.util.Hashtable;

/**
 * This class has the mapping from a paper size name to the actual size.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class PaperSize {
   /**
    * Get a list of pre-defined paper size names.
    */
   public static String[] getList() {
      return sizestrs;
   }

   /**
    * Get the paper size at the position of a pre-defined list.
    */
   public static Size getSize(int idx) {
      return (Size) sizes[idx][1];
   }

   /**
    * Get the size of a named paper size. The name can be either a paper
    * size name from the list, or a width and height defined as
    * "widthxheight" in inches.
    * @return paper size in inches, or null if not a valid name.
    */
   public static Size getSize(String name) {
      if(name == null) {
         return null;
      }

      Size size = (Size) map.get(name);

      // try widthxheight
      if(size == null) {
         int pos = name.indexOf('x');

         if(pos > 0) {
            size = new Size();

            try {
               int unitIdx = name.indexOf(':');
               size.width =
                  Float.valueOf(name.substring(unitIdx + 1, pos)).floatValue();
               size.height =
                  Float.valueOf(name.substring(pos + 1)).floatValue();
            }
            catch(Exception e) {
               return null;
            }
         }
      }

      return size;
   }

   /**
    * Get the index of the paper size in the list.
    */
   public static int getIndex(String name) {
      for(int i = 0; i < sizes.length; i++) {
         if(sizes[i][0].equals(name)) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Get the index of the paper size in the list.
    */
   public static int getIndex(Size size) {
      for(int i = 0; i < sizes.length; i++) {
         // @by henryh, little difference allowed
         Size psize = (Size) sizes[i][1];

         if(Math.abs(psize.width - size.width) < 0.02 &&
            Math.abs(psize.height - size.height) < 0.02)
         {
            return i;
         }
      }

      return -1;
   }

   /**
    * Get the text name assigned to a predefined size.
    */
   public static String getName(Size size) {
      int idx = getIndex(size);

      return (idx >= 0) ? (String) sizes[idx][0] : size.toString();
   }

   /**
    * Convert orientation name to option number.
    */
   public static int getOrientation(String name) {
      return (name != null &&
         (name.equalsIgnoreCase("landscape") || name.equals("0"))) ?
         StyleConstants.LANDSCAPE :
         StyleConstants.PORTRAIT;
   }

   static final Object[][] sizes = {
      {"Letter [8.5x11 in]", new Size(8.5, 11)},
      {"Legal [8.5x14 in]", new Size(8.5, 14)},
      {"A2 [420x594 mm]", new Size(16.535433, 23.385827)},
      {"A3 [297x420 mm]", new Size(11.692913, 16.535433)},
      {"A4 [210x297 mm]", new Size(8.2677165, 11.692913)},
      {"A5 [148x210 mm]", new Size(5.82677165, 8.2677165)},
      {"Executive [7.25x10.5 in]", new Size(7.25, 10.5)},
      {"Tabloid [11x17 in]", new Size(11, 17)},
      {"Ledger [17x11 in]", new Size(17, 11)},
      {"Statement [5.5x8.5 in]", new Size(5.5, 8.5)},
      {"B4 [250x353 mm]", new Size(9.84252, 13.89764)},
      {"B5 [182x257 mm]", new Size(7.165354, 10.11811)},
      {"Folio [8.5x13 in]", new Size(8.5, 13)},
      {"Quarto [215x275 mm]", new Size(8.464567, 10.82677)}, };

   static String[] sizestrs;
   static Hashtable map = new Hashtable();

   static {
      sizestrs = new String[sizes.length];
      for(int i = 0; i < sizes.length; i++) {
         sizestrs[i] = (String) sizes[i][0];
         map.put(sizes[i][0], sizes[i][1]);
      }
   }
}

