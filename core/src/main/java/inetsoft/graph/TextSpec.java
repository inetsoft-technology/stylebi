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
package inetsoft.graph;

import com.inetsoft.build.tern.TernClass;
import com.inetsoft.build.tern.TernMethod;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.geometry.Geometry;
import inetsoft.graph.internal.GDefaults;
import inetsoft.report.StyleFont;
import inetsoft.util.CoreTool;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.awt.*;
import java.io.Serializable;
import java.text.Format;
import java.util.Map;
import java.util.Objects;

/**
 * This class contains the formatting attributes for text labels.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=TextSpec")
public class TextSpec implements Cloneable, Serializable {
   /**
    * Set the text color.
    */
   @TernMethod
   public void setColor(Color color) {
      this.color = color;
   }

   /**
    * Get the text color.
    */
   @TernMethod
   public Color getColor() {
      return (color == null) ? GDefaults.DEFAULT_TEXT_COLOR : color;
   }

   /**
    * Set the text color for the specific value.
    */
   @TernMethod
   public synchronized void setColor(Object val, Color color) {
      if(colormap == null) {
         colormap = new Object2ObjectOpenHashMap<>();
      }

      colormap.put(val, color);
   }

   /**
    * Get the text color for the specific value.
    */
   @TernMethod
   public synchronized Color getColor(Object val) {
      Color color = colormap != null ? colormap.get(val) : null;
      return (color == null) ? getColor() : color;
   }

   /**
    * Set the text font.
    */
   @TernMethod
   public void setFont(Font font) {
      this.font = font;
   }

   /**
    * Get the text font.
    */
   @TernMethod
   public Font getFont() {
      return (font == null) ? GDefaults.DEFAULT_TEXT_FONT : font;
   }

   /**
    * Set the text font for the specific value.
    */
   @TernMethod
   public synchronized void setFont(Object val, Font font) {
      if(fontmap == null) {
         fontmap = new Object2ObjectOpenHashMap<>();
      }

      fontmap.put(val, font);
   }

   /**
    * Get the text font for the specific value.
    */
   @TernMethod
   public synchronized Font getFont(Object val) {
      Font font = fontmap != null ? fontmap.get(val) : null;
      return (font == null) ? getFont() : font;
   }

   /**
    * Set the background fill color.
    */
   @TernMethod
   public void setBackground(Color bg) {
      this.bg = bg;
   }

   /**
    * Get the background fill color.
    */
   @TernMethod
   public Color getBackground() {
      return bg;
   }

   /**
    * Set the format for converting an object to a text string.
    */
   @TernMethod
   public void setFormat(Format fmt) {
      this.fmt = fmt;
   }

   /**
    * Get the format for converting an object to a text string.
    */
   @TernMethod
   public Format getFormat() {
      return fmt;
   }

   /**
    * Set the text rotation in degrees.
    */
   @TernMethod
   public void setRotation(double rotation) {
      this.rotation = rotation;
   }

   /**
    * Get the text rotation in degrees.
    */
   @TernMethod
   public double getRotation() {
      return rotation;
   }

   /**
    * Get the alignment of the text.
    */
   @TernMethod
   public int getAlignment() {
      return align;
   }

   /**
    * Set the alignment of the text.
    * @param align alignment defined in GraphConstants.
    */
   @TernMethod
   public void setAlignment(int align) {
      this.align = align;
   }

   /**
    * Check if lines should be printed in reverse order.
    */
   @TernMethod
   public boolean isReverseLines() {
      return reverseLines;
   }

   /**
    * Set whether lines should be printed in reverse order.
    */
   @TernMethod
   public void setReverseLines(boolean reverseLines) {
      this.reverseLines = reverseLines;
   }

   /**
    * Get the line spacing ratio. No special spacing if the value is NaN.
    */
   @TernMethod
   public double getLineSpacing() {
      return lineSpacing;
   }

   /**
    * Set whether lines should be spaced out evenly by filling the allocated label height
    * (or width if label is vertical). This is only applied for horizontal or vertical labels.
    * @param lineSpacing this is the ratio of the gaps between lines. Each line is centered
    *                    in the space between gaps.
    */
   @TernMethod
   public void setLineSpacing(double lineSpacing) {
      this.lineSpacing = lineSpacing;
   }

   /**
    * Return a new text spec if the setting depends on data.
    */
   public TextSpec evaluate(DataSet data, int row, Geometry gobj, String measure, Object value) {
      return this;
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof TextSpec)) {
         return false;
      }

      TextSpec txtfmt = (TextSpec) obj;

      return CoreTool.equals(this.color, txtfmt.color) &&
         CoreTool.equals(this.font, txtfmt.font)&&
         this.rotation == txtfmt.rotation &&
         CoreTool.equals(this.fmt, txtfmt.fmt) &&
         CoreTool.equals(this.bg, txtfmt.bg) &&
         this.align == txtfmt.align &&
         this.reverseLines == txtfmt.reverseLines &&
         Double.valueOf(this.lineSpacing).equals(txtfmt.lineSpacing) &&
         Objects.equals(colormap, txtfmt.colormap) &&
         Objects.equals(fontmap, txtfmt.fontmap);
   }

   /**
    * Create a clone of this object.
    */
   @Override
   public TextSpec clone() {
      try {
         TextSpec spec = (TextSpec) super.clone();

         /*
         if(font != null) {
            try {
               // for StyleFont
               Method func = font.getClass().getMethod("clone", new Class[0]);

               if(func != null) {
                  spec.font = (Font) func.invoke(font, new Object[0]);
               }
            }
            catch(Exception ex) {
               // ignore, font doesn't have clone
            }
         }
         */
         // the reflection was done to avoid dependency to StyleFont
         // but could have a performance hit
         if(font instanceof StyleFont) {
            font = (Font) ((StyleFont) font).clone();
         }

         if(this.colormap != null) {
            spec.colormap = new Object2ObjectOpenHashMap<>(this.colormap);
         }

         if(this.fontmap != null) {
            spec.fontmap = new Object2ObjectOpenHashMap<>(this.fontmap);
         }

         return spec;
      }
      catch(Exception e) {
         return null;
      }
   }

   @Override
   public String toString() {
      String str = super.toString() + "[";

      if(color != null) {
         str += "color: " + color + ",";
      }

      if(bg != null) {
         str += "bg: " + bg + ",";
      }

      if(font != null) {
         str += "font: " + font + ",";
      }

      if(fmt != null) {
         str += "fmt: " + fmt + ",";
      }

      if(rotation != 0) {
         str += "rotation: " + rotation + ",";
      }

      if(align != 0) {
         str += "align: " + align + ",";
      }

      if(colormap != null && !colormap.isEmpty()) {
         str += "colormap: " + colormap + ",";
      }

      if(fontmap != null && !fontmap.isEmpty()) {
         str += "fontmap: " + fontmap + ",";
      }

      return str + "]";
   }

   private Color color;
   private Color bg;
   private Font font;
   private Format fmt;
   private double rotation;
   private int align = GraphConstants.AUTO; // auto alignment
   private Map<Object,Color> colormap;
   private Map<Object,Font> fontmap;
   private boolean reverseLines;
   private double lineSpacing = Double.NaN;

   private static final long serialVersionUID = 1L;
}
