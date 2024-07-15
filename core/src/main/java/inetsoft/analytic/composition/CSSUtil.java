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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.report.StyleConstants;
import inetsoft.report.StyleFont;
import inetsoft.report.internal.Common;
import inetsoft.uql.viewsheet.BorderColors;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.util.Tool;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * CSS related utility methods.
 *
 * @version 11.1
 * @author InetSoft Technology Corp
 */
public class CSSUtil {
   /**
    * Get css object from format.
    */
   public static ObjectNode getCSS(VSCompositeFormat format) {
      if(format == null) {
         return null;
      }

      ObjectNode css = new ObjectMapper().createObjectNode();
      getBordersStyle(css, format);
      getBackgroundAlpha(css, format);
      getBackground(css, format);
      getForeground(css, format);
      getAlignment(css, format);
      getFontStyle(css, format);
      getWrapping(css, format);
      return css;
   }

   /**
    * Get boders css style.
    */
   public static void getLegendBordersStyle(ObjectNode css, int type, Color color) {
      getBorderStyle(css, type, null, color);
   }

   /**
    * Get boders css style.
    */
   public static void getBordersStyle(ObjectNode css, VSCompositeFormat format) {
      getBordersStyle(css, format, true);
   }

   /**
    * Get boders css style.
    */
   public static void getBordersStyle(ObjectNode css, VSCompositeFormat format,
      boolean visible)
   {
      Insets borders = format.getBorders();

      if(borders == null) {
         return;
      }

      int bstyle_top = borders.top;
      int bstyle_bottom = borders.bottom;
      int bstyle_left = borders.left;
      int bstyle_right = borders.right;

      BorderColors colors = format.getBorderColors();

      if(colors != null) {
         Color topColor = colors.topColor;
         Color bottomColor = colors.bottomColor;
         Color leftColor = colors.leftColor;
         Color rightColor = colors.rightColor;

         if(visible) {
            getBorderStyle(css, bstyle_top, "top", topColor);
         }

         getBorderStyle(css, bstyle_bottom, "bottom", bottomColor);
         getBorderStyle(css, bstyle_left, "left", leftColor);
         getBorderStyle(css, bstyle_right, "right", rightColor);
      }
   }

   /**
    * Get background alpha, as opacity affect all it's children, replaced by
    * background color
    */
   public static void getBackgroundAlpha(ObjectNode css, VSCompositeFormat format) {
      css.put("opacity", "");
   }

   /**
    * Get background color.
    */
   public static void getBackground(ObjectNode css, VSCompositeFormat format) {
      Color bg = format.getBackground();

      if(bg != null) {
         css.put(
            "background", "rgba(" + bg.getRed() + "," + bg.getGreen() + "," +
            bg.getBlue() + "," + (format.getAlpha() / 100F) + ")");
      }
   }

   /**
    * Get foreground color.
    */
   public static void getForeground(ObjectNode css, VSCompositeFormat format) {
      Color fg = format.getForeground();

      if(fg != null) {
         css.put("color", "#" + Tool.colorToHTMLString(fg));
      }
   }

   /**
    * Get wrapping.
    */
   public static void getWrapping(ObjectNode css, VSCompositeFormat format) {
      boolean wrap = format.isWrapping();

      if(wrap) {
         css.put("white-space", "normal");
         css.put("word-wrap", "break-word");
         css.put("overflow", "hidden");
      }
      else {
         css.put("white-space", "nowrap");
      }
   }

   /**
    * Get alignment css style.
    */
   public static void getAlignment(ObjectNode css, VSCompositeFormat format) {
      int align = format.getAlignment();

      if((align & StyleConstants.V_TOP) != 0) {
         css.put("vertical-align", "top");
      }
      else if((align & StyleConstants.V_CENTER) != 0) {
         // default middle
         css.put("vertical-align", "middle");
      }
      else if((align & StyleConstants.V_BOTTOM) != 0) {
         css.put("vertical-align", "bottom");
      }

      if((align & StyleConstants.H_CENTER) != 0) {
         css.put("text-align", "center");
      }
      else if(((align & StyleConstants.H_RIGHT) != 0) ||
              ((align & StyleConstants.H_CURRENCY) != 0))
      {
         css.put("text-align", "right");
      }
      else if((align & StyleConstants.H_LEFT) != 0) {
         css.put("text-align", "left");
      }
   }

   /**
    * Get the CSS font style string for the font style (bold, italic).
    */
   public static void getFontStyle(ObjectNode css, VSCompositeFormat format) {
      Font font = format.getFont();
      getFontStyle(css, font);
   }

   /**
    * Get the CSS font style string for the font style (bold, italic).
    */
   public static void getFontStyle(ObjectNode css, Font font) {
      if(font == null) {
         return;
      }

      String ft = getFontName(font);
      ft += ", arial, helvetica, sans-serif";
      css.put("font-family", ft);
      css.put("font-size", font.getSize() + "px");

      if((font.getStyle() & Font.BOLD) != 0) {
         css.put("font-weight", "bold");
      }
      else {
         // set the font-weight and style to normal to avoid the style
         // on parent (e.g. SelectionList) being cascaded to a
         // child (e.g. title)
         css.put("font-weight", "normal");
      }

      if((font.getStyle() & Font.ITALIC) != 0) {
         css.put("font-style", "italic");
      }
      else {
         css.put("font-style", "normal");
      }

      String tdecoration = "";

      if((font.getStyle() & StyleFont.UNDERLINE) != 0) {
         tdecoration = "underline ";
      }

      if((font.getStyle() & StyleFont.STRIKETHROUGH) != 0) {
         tdecoration += "line-through";
      }

      if(tdecoration.length() > 0) {
         css.put("text-decoration", tdecoration);
      }
   }

   /**
    * Get all of real optional font names.
    */
   public static String getOptionalFontName(Font font) {
      String fn = font.getName();
      Object v = OPTION_FONT_MAPPING.get(fn.toLowerCase());

      return (v == null) ? fn : (String) v;
   }

   private static String getFontName(Font font) {
      String fn = font.getName();
      Object v = FONT_NAMES.get(fn.toLowerCase());

      return (v == null) ? fn : (String) v;
   }

   private static void getBorderStyle(ObjectNode css, int style, String side, Color color) {
      String property = "border";

      if(side != null) {
         property = property + "-" + side;
      }

      String bstyle = getBorderStyle(style);
      String colorStr = Tool.colorToHTMLString(color);
      String width = (int) Math.ceil(Common.getLineWidth(style)) + "px";
      css.put(property, width + " " + bstyle + " #" + colorStr);
   }

   private static String getBorderStyle(int borderStyle) {
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

   private static final Map<String, String> FONT_NAMES = new HashMap<>();
   private static final Map<String, String> OPTION_FONT_MAPPING = new HashMap<>();

   static {
      FONT_NAMES.put("dialog", "Helvetica");
      FONT_NAMES.put("dialoginput", "Courier");
      FONT_NAMES.put("serif", "Times New Roman");
      FONT_NAMES.put("sansserif", "Helvetica");
      FONT_NAMES.put("monospaced", "Courier");
      FONT_NAMES.put("timesroman", "Times New Roman");
      FONT_NAMES.put("courier", "Courier");
      FONT_NAMES.put("helvetica", "Helvetica");

      // the optional font mapping
      OPTION_FONT_MAPPING.put("dialog", "Arial, Helvetica, sans-serif");
      OPTION_FONT_MAPPING.put("dialoginput", "Courier New, Courier, monospace");
      OPTION_FONT_MAPPING.put("serif", "Times New Roman, Times, serif");
      OPTION_FONT_MAPPING.put("sansserif", "Arial, Helvetica, sans-serif");
      OPTION_FONT_MAPPING.put("monospaced", "Courier New, Courier, monospace");
      OPTION_FONT_MAPPING.put("timesroman", "Times New Roman, Times, serif");
      OPTION_FONT_MAPPING.put("courier", "Courier New, Courier, monospace");
      OPTION_FONT_MAPPING.put("helvetica", "Helvetica, Arial, sans-serif");
   }
}
