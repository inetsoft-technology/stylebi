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
package inetsoft.uql.viewsheet.graph;

import inetsoft.report.StyleFont;
import inetsoft.uql.XFormatInfo;
import inetsoft.uql.asset.AssetObject;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;

/**
 * TextFormat holds the attributes to format text.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class TextFormat implements AssetObject {
   /**
    * Constructor.
    */
   public TextFormat() {
      super();
   }

   /**
    * Check if equals another objects.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof TextFormat)) {
         return false;
      }

      TextFormat txtfmt = (TextFormat) obj;
      return Tool.equals(this.color, txtfmt.color) &&
         Tool.equals(this.bg, txtfmt.bg) &&
         Tool.equals(this.alpha, txtfmt.alpha) &&
         Tool.equals(this.font, txtfmt.font)&&
         Tool.equals(this.align, txtfmt.align)&&
         Tool.equals(this.rotation, txtfmt.rotation) &&
         Tool.equals(this.fmt, txtfmt.fmt);
   }

   /**
    * Set the color for this text format.
    * @param color color format information.
    */
   public void setColor(Color color) {
      setColor(color, true);
   }

   /**
    * Set the color for this text format.
    * @param color color format information.
    */
   public void setColor(Color color, boolean defined) {
      this.color = color;
      colorDefined = defined;
   }

   /**
    * Get the color for this text format.
    * @return the color format information.
    */
   public Color getColor() {
      return color;
   }

   /**
    * Set the background for this text format.
    */
   public void setBackground(Color bg) {
      setBackground(bg, true);
   }

   /**
    * Set the background for this text format.
    */
   public void setBackground(Color bg, boolean defined) {
      this.bg = bg;
      bgDefined = defined;
   }

   /**
    * Get the background for this text format.
    */
   public Color getBackground() {
      return bg;
   }

   /**
    * Set the alpha of the fill color.
    * @param alpha 0-100 (100 == opaque).
    */
   public void setAlpha(int alpha) {
      setAlpha(alpha, true);
   }

   /**
    * Set the alpha of the fill color.
    */
   public void setAlpha(int alpha, boolean defined) {
      this.alpha = alpha;
      alphaDefined = defined;
   }

   /**
    * Get the alpha of the fill color.
    */
   public int getAlpha() {
      return alpha;
   }

   /**
    * Set the font for this text format.
    * @param font font format information
    */
   public void setFont(Font font) {
      setFont(font, true);
   }

   /**
    * Set the font for this text format.
    * @param font font format information
    */
   public void setFont(Font font, boolean defined) {
      this.font = font;
      fontDefined = defined;
   }

   /**
    * Get the font for this text format.
    * @return the font format information.
    */
   public Font getFont() {
      return font;
   }

   /**
    * Set the format information for this text format.
    * @param fmt represents a column's format information.
    */
   public void setFormat(XFormatInfo fmt) {
      this.fmt = (fmt == null) ? new XFormatInfo() : fmt;
   }

   /**
    * Get the XFormatInfo for this processor.
    * @return the format information.
    */
   public XFormatInfo getFormat() {
      return fmt;
   }

   /**
    * Set the rotation for this text format.
    * @param rotation presents text whirling angle.
    */
   public void setRotation(Number rotation) {
      setRotation(rotation, true);
   }

   /**
    * Set the rotation for this text format.
    * @param rotation presents text whirling angle.
    */
   public void setRotation(Number rotation, boolean defined) {
      this.rotation = rotation;
      rotationDefined = defined;
   }

   /**
    * Get the rotation for this text format.
    * @return the text rotation.
    */
   public Number getRotation() {
      return rotation;
   }

   /**
    * Set the alignment for this text format.
    */
   public void setAlignment(int align) {
      setAlignment(align, true);
   }

   /**
    * Set the align for this text format.
    */
   public void setAlignment(int align, boolean defined) {
      this.align = align;
      alignDefined = defined;
   }

   /**
    * Get the align for this text format.
    */
   public int getAlignment() {
      return align;
   }

   /**
    * Generate the XML segment to represent this text format.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<textFormat class=\"");
      writer.print(getClass().getName());
      writer.print("\"");

      if(color != null) {
         writer.print(" color =\"");
         writer.print(color.getRGB() & 0xFFFFFFl);
         writer.print("\"");
      }

      if(bg != null) {
         writer.print(" bg =\"");
         writer.print(bg.getRGB() & 0xFFFFFFl);
         writer.print("\"");
      }

      if(font != null) {
         writer.print(" font =\"");
         writer.print(StyleFont.toString(font));
         writer.print("\"");
      }

      if(rotation != null) {
         writer.print(" rotation= \"");
         writer.print(rotation);
         writer.print("\"");
      }

      if(align != 0) {
         writer.print(" align =\"" + align + "\"");
      }

      // optimization, don't need to write if false (it's the default)
      if(fontDefined) {
         writer.print(" fontDefined=\"true\"");
      }

      if(alignDefined) {
         writer.print(" alignDefined=\"true\"");
      }

      if(colorDefined) {
         writer.print(" colorDefined=\"true\"");
      }

      if(bgDefined) {
         writer.print(" bgDefined=\"true\"");
      }

      if(rotationDefined) {
         writer.print(" rotationDefined=\"true\"");
      }

      if(alphaDefined) {
         writer.print(" alphaDefined=\"true\"");
      }

      writer.print(" alpha=\"" + alpha + "\"");
      writer.println(">");

      if(fmt != null) {
         fmt.writeXML(writer);
      }

      writer.println("</textFormat>");
   }

   /**
    * Parse the XML element that contains information on this text format.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      String prop;

      fontDefined = "true".equals(Tool.getAttribute(tag, "fontDefined"));
      alignDefined = "true".equals(Tool.getAttribute(tag, "alignDefined"));
      colorDefined = "true".equals(Tool.getAttribute(tag, "colorDefined"));
      bgDefined = "true".equals(Tool.getAttribute(tag, "bgDefined"));
      rotationDefined =
         "true".equals(Tool.getAttribute(tag, "rotationDefined"));
      alphaDefined = "true".equals(Tool.getAttribute(tag, "alphaDefined"));

      if((prop = Tool.getAttribute(tag, "color")) != null) {
         color = new Color(Integer.parseInt(prop));
      }

      if((prop = Tool.getAttribute(tag, "bg")) != null) {
         bg = new Color(Integer.parseInt(prop));
      }

      if((prop = Tool.getAttribute(tag, "alpha")) != null) {
         alpha = Integer.parseInt(prop);
      }

      if((prop = Tool.getAttribute(tag, "font")) != null) {
         font = StyleFont.decode(prop);
      }

      if((prop = Tool.getAttribute(tag, "align")) != null) {
         align = Integer.parseInt(prop);
      }

      if((prop = Tool.getAttribute(tag, "rotation")) != null) {
         rotation = Double.valueOf(prop);
      }

      Element fmtnode = Tool.getChildNodeByTagName(tag, "XFormatInfo");

      if(fmtnode != null) {
         fmt = new XFormatInfo();
         fmt.parseXML(fmtnode);
      }
   }

   /**
    * Create a clone of this object.
    */
   @Override
   public Object clone() {
      try {
         TextFormat obj = (TextFormat) super.clone();

         if(font instanceof StyleFont) {
            obj.font = (Font) ((StyleFont) font).clone();
         }

         if(fmt != null) {
            obj.fmt = (XFormatInfo) fmt.clone();
         }

         return obj;
      }
      catch(Exception e) {
         LOG.error("Failed to clone Textformat", e);
         return null;
      }
   }

   /**
    * Check if font defined.
    */
   public boolean isFontDefined() {
      return fontDefined;
   }

   /**
    * Check if alignment defined.
    */
   public boolean isAlignmentDefined() {
      return alignDefined;
   }

   /**
    * Check if color defined.
    */
   public boolean isColorDefined() {
      return colorDefined;
   }

   /**
    * Check if background defined.
    */
   public boolean isBackgroundDefined() {
      return bgDefined;
   }

   /**
    * Check if rotation defined.
    */
   public boolean isRotationDefined() {
      return rotationDefined;
   }

   /**
    * Check if alpha is defined.
    */
   public boolean isAlphaDefined() {
      return alphaDefined;
   }

   private Color color;
   private Color bg;
   private int alpha = 100;
   private Font font;
   private int align = 0;
   // add default value, in as, it always write a XFormatInfo node back
   private XFormatInfo fmt = new XFormatInfo();
   private Number rotation;
   private boolean fontDefined = false;
   private boolean alignDefined = false;
   private boolean colorDefined = false;
   private boolean bgDefined = false;
   private boolean rotationDefined = false;
   private boolean alphaDefined = false;

   protected static final Logger LOG = LoggerFactory.getLogger(TextFormat.class);
}
