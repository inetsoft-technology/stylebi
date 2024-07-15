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
package inetsoft.web.composer.tablestyle.css;

import inetsoft.analytic.composition.VSCSSUtil;
import inetsoft.report.StyleConstants;
import inetsoft.report.internal.Common;
import inetsoft.util.Tool;
import inetsoft.web.adhoc.model.AlignmentInfo;
import inetsoft.web.adhoc.model.FontInfo;

import java.awt.*;
import java.util.Hashtable;

public final class CSSUtil {
   public static String getFontString(Object font) {
      if(font instanceof FontInfo) {
         return VSCSSUtil.getFont(((FontInfo) font).toFont());
      }

      return null;
   }

   public static String getBorderStyle2(int style, Color color) {
      String key = style + "";

      if(color != null) {
         key += color.getRGB();
      }

      String styleStr = stylecache.get(key);

      // try cache, optimization
      if(styleStr != null) {
         return styleStr;
      }

      StringBuilder str = new StringBuilder();

      int width = (int) Math.ceil(Common.getLineWidth(style));
      str.append(width).append("px ");
      str.append(getBorderStyle(style)).append(" ");

      if(color != null) {
         boolean raised = (style & StyleConstants.RAISED_MASK) != 0 ||
                 (style & StyleConstants.LOWERED_MASK) != 0;

         if(raised && color.equals(Color.BLACK)) {
            color = Color.GRAY;
         }

         str.append("#").append(Tool.colorToHTMLString(color));
      }

      styleStr = str.toString();
      stylecache.put(key, styleStr);

      return styleStr;
   }

   public static String getBorderStyle(Object border, Object color) {
      if(border instanceof Integer && color instanceof Color) {
         return getBorderStyle2(((Integer) border).intValue(), (Color) color);
      }

      return null;
   }

   public static String getBorderStyle(int borderStyle) {
      String borderString;

      switch(borderStyle) {
         case StyleConstants.THIN_LINE:
            borderString = "solid ";
            break;
         case StyleConstants.MEDIUM_LINE:
            borderString = "solid ";
            break;
         case StyleConstants.THICK_LINE:
            borderString = "solid ";
            break;
         case StyleConstants.DOUBLE_LINE:
            borderString = "double ";
            break;
         case StyleConstants.RAISED_3D:
            borderString = "ridge ";
            break;
         case StyleConstants.LOWERED_3D:
            borderString = "groove ";
            break;
         case StyleConstants.DOUBLE_3D_RAISED:
            borderString = "outset ";
            break;
         case StyleConstants.DOUBLE_3D_LOWERED:
            borderString = "inset ";
            break;
         case StyleConstants.DOT_LINE:
            borderString = "dotted ";
            break;
         case HEAVY_DOT:
            borderString = "dotted ";
            break;
         case StyleConstants.DASH_LINE:
            borderString = "dashed ";
            break;
         case StyleConstants.MEDIUM_DASH:
            borderString = "dashed ";
            break;
         case StyleConstants.LARGE_DASH:
            borderString = "dashed ";
            break;
         case StyleConstants.NO_BORDER:
            borderString = "none ";
            break;
         default:
            borderString = "solid ";
            break;
      }

      return borderString;
   }

   public static AlignmentInfo getAlignment(Object alignment) {
      if(alignment instanceof Integer) {
         return new AlignmentInfo((Integer) alignment);
      }

      return null;
   }

   public static String getColorString(Object color) {
      if(color instanceof Color) {
         return "#" + Tool.colorToHTMLString((Color) color);
      }

      return null;
   }

   // Draw heavy dotted lines.
   private static final int HEAVY_DOT = 2 + StyleConstants.SOLID_MASK + 0x10;
   private static final Hashtable<String, String> stylecache = new Hashtable<>(); // caching styles
}
