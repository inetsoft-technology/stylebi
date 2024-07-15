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
package inetsoft.analytic.composition;

import inetsoft.report.StyleConstants;
import inetsoft.report.StyleFont;
import inetsoft.report.internal.Common;
import inetsoft.uql.viewsheet.BorderColors;
import inetsoft.uql.viewsheet.internal.XVSFormat;
import inetsoft.util.Tool;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class VSCSSUtil {
   private VSCSSUtil() {
      throw new IllegalStateException("Utility class");
   }

   public static String getForeground(XVSFormat format) {
      return getForegroundColor(format.getForeground());
   }

   public static String getForegroundColor(Color fg) {
      return fg == null ? "" : "#" + Tool.colorToHTMLString(fg);
   }

   public static String getBackground(XVSFormat format) {
      return getBackgroundColor(format.getBackground());
   }

   public static String getBackgroundColor(Color bg) {
      return bg == null ? "" : "#" + Tool.colorToHTMLString(bg);
   }

   public static String getBackgroundRGBA(Color bg) {
      if(bg == null) {
         return "";
      }

      float alpha = bg.getAlpha();

      if(alpha != 0) {
         alpha = alpha / 255f;
      }

      return "rgba(" + bg.getRed() + "," + bg.getGreen() + "," + bg.getBlue() + "," + alpha + ")";
   }

   public static float getAlpha(XVSFormat format) {
      return format.getAlpha() / 100f;
   }

   public static String getBackgroundRGBA(XVSFormat format) {
      Color bg = format.getBackground();
      String alpha = format.getAlpha() / 100f + "";

      return bg == null ? "" :
         "rgba(" + bg.getRed() + "," + bg.getGreen() + "," + bg.getBlue() + "," + alpha + ")";
   }

   public static String getBorder(XVSFormat format, String side) {
      Insets borders = format.getBorders();

      if(borders == null) {
         return null;
      }

      int bstyle;

      switch(side) {
      case "top":
         bstyle = borders.top;
         break;
      case "bottom":
         bstyle = borders.bottom;
         break;
      case "left":
         bstyle = borders.left;
         break;
      default:
         bstyle = borders.right;
         break;
      }

      BorderColors colors = format.getBorderColors();

      if(colors != null) {
         Color color;
         switch(side) {
         case "top":
            color = colors.topColor;
            break;
         case "bottom":
            color = colors.bottomColor;
            break;
         case "left":
            color = colors.leftColor;
            break;
         default:
            color = colors.rightColor;
            break;
         }

         String width = (int) Math.ceil(Common.getLineWidth(bstyle)) + "px ";
         String cssBStyle = getBorderStyle(bstyle);
         boolean raised = (bstyle & StyleConstants.RAISED_MASK) != 0 ||
            (bstyle & StyleConstants.LOWERED_MASK) != 0;

         if(raised && color.equals(Color.BLACK)) {
            color = Color.GRAY;
         }

         String colorStr = "#" + Tool.colorToHTMLString(color);

         return width + cssBStyle + colorStr;
      }

      return "";
   }

   public static String gethAlign(XVSFormat format) {
      int align = format.getAlignment();

      if((align & StyleConstants.H_LEFT) != 0) {
         return  "left";
      }
      else if((align & StyleConstants.H_CENTER) != 0) {
         return  "center";
      }
      else if(((align & StyleConstants.H_RIGHT) != 0) ||
         ((align & StyleConstants.H_CURRENCY) != 0)) {
         return  "right";
      }

      return "";
   }

   public static String getvAlign(XVSFormat format) {
      int align = format.getAlignment();

      if((align & StyleConstants.V_TOP) != 0) {
         return "top";
      }
      else if((align & StyleConstants.V_CENTER) != 0) {
         return "middle";
      }
      else if((align & StyleConstants.V_BOTTOM) != 0) {
         return "bottom";
      }

      // vertical align should default to baseline
      return "baseline";
   }

   public static String getFlexAlignment(String alignment) {
      String flexAlignment;

      switch(alignment) {
         // top/left fallthrough
         case "top":
         case "left":
            flexAlignment = "flex-start";
            break;
         // center/middle fallthrough
         case "middle":
         case "center":
            flexAlignment = "center";
            break;
         // bottom/right fallthrough
         case "bottom":
         case "right":
            flexAlignment = "flex-end";
            break;
         default:
            flexAlignment = "baseline";
            break;
      }

      return flexAlignment;
   }


   public static String getFont(XVSFormat format) {
      return getFont(format.getFont());
   }

   public static String getFont(Font font) {
      if(font == null) {
         return "";
      }

      StringBuilder buffer = new StringBuilder();

      if((font.getStyle() & Font.ITALIC) != 0) {
         buffer.append("italic ");
      }

      if((font.getStyle() & Font.BOLD) != 0) {
         buffer.append("bold ");
      }

      buffer.append(font.getSize());
      buffer.append("px ");
      buffer.append(getFontName(font));
      buffer.append(", roboto, arial, helvetica, sans-serif");

      return buffer.toString();
   }

   public static String getDecoration(XVSFormat format) {
      Font font = format.getFont();
      String tdecoration = "";

      if(font == null) {
         return tdecoration;
      }

      if((font.getStyle() & StyleFont.UNDERLINE) != 0) {
         tdecoration = "underline ";
      }

      if((font.getStyle() & StyleFont.STRIKETHROUGH) != 0) {
         tdecoration += "line-through";
      }

      return tdecoration;
   }

   public static String getFontDecoration(StyleFont font) {
      String tdecoration = "";

      if(font == null) {
         return tdecoration;
      }

      if(font.getUnderlineStyle() != 0) {
         tdecoration = "underline ";
      }

      if(font.getStrikelineStyle() != 0) {
         tdecoration += "line-through";
      }

      return tdecoration;
   }

   private static String getFontName(Font font) {
      //fix feature#5659
      //to fix user defined font name, replace font family with font name
      String fn = font.getName();
      String v = fontnames.get(fn.toLowerCase());

      return (v == null) ? fn : v;
   }

   /**
    * Transform a border style and color into a valid css string
    * @param style    the {@link StyleConstants} border style
    * @param color    the color of the border to translate
    * @return         a css string of the given border style
    */
   public static String getBorderStyle(int style, Color color) {
      String bstyle = getBorderStyle(style);
      String colorStr = Tool.colorToHTMLString(color);
      String width = (int) Math.ceil(Common.getLineWidth(style)) + "px";
      return width + " " + bstyle + " #" + colorStr;
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

   private static final Map<String, String> fontnames = new HashMap<>();
   static {
      fontnames.put("dialog", "Helvetica");
      fontnames.put("dialoginput", "Courier");
      fontnames.put("serif", "Times New Roman");
      fontnames.put("sansserif", "Helvetica");
      fontnames.put("monospaced", "Courier");
      fontnames.put("timesroman", "Times New Roman");
      fontnames.put("courier", "Courier");
      fontnames.put("helvetica", "Helvetica");
   }
}
