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
package inetsoft.uql.viewsheet.graph;

import inetsoft.graph.GraphConstants;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;
import inetsoft.uql.XFormatInfo;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;

/**
 * CompositeTextFormat distinguish the hierarchy of user fromat, css format and
 * default format.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class CompositeTextFormat implements AssetObject {
   /**
    * Constructor.
    */
   public CompositeTextFormat() {
      super();
      deffmt = new TextFormat();
      cssfmt = new CSSTextFormat();
      userfmt = new TextFormat();

      deffmt.setColor(GDefaults.DEFAULT_TEXT_COLOR);
      deffmt.setFont(VSUtil.getDefaultFont());
   }

   /**
    * Get the font.
    * @return the font of this format.
    */
   public Font getFont() {
      return userfmt.isFontDefined() ? userfmt.getFont() :
         (!VSUtil.isIgnoreCSSFormat() && cssfmt.isFontDefined()) ?
         cssfmt.getFont() : deffmt.getFont();
   }

   /**
    * Set the font.
    * @param font to set on user defined format.
    */
   public void setFont(Font font) {
      userfmt.setFont(font);
   }

   // @by ChrisSpagnoli bug1426672012884 2015-3-20
   // Adjust CSS for rotated text, so it matches alignment entered by UI.
   /**
    * Adjust CSS alignment for rotated text (ex: Y-Axis of Chart).
    * @param alignCss the raw alignment from the CSS.
    * @return the "adjusted" alignment for the rotated text.
    */
   private int rotateCSS(final int alignCss) {
      if(deffmt.getRotation() != null &&
         deffmt.getRotation().doubleValue() == 90.0)
      {
         return((alignCss & GraphConstants.TOP_ALIGNMENT) > 0 ?
               GraphConstants.RIGHT_ALIGNMENT : 0) +
            ((alignCss & GraphConstants.MIDDLE_ALIGNMENT) > 0 ?
               GraphConstants.CENTER_ALIGNMENT : 0) +
            ((alignCss & GraphConstants.BOTTOM_ALIGNMENT) > 0 ?
               GraphConstants.LEFT_ALIGNMENT : 0) +
            ((alignCss & GraphConstants.RIGHT_ALIGNMENT) > 0 ?
               GraphConstants.TOP_ALIGNMENT : 0) +
            ((alignCss & GraphConstants.CENTER_ALIGNMENT) > 0 ?
               GraphConstants.MIDDLE_ALIGNMENT : 0) +
            ((alignCss & GraphConstants.LEFT_ALIGNMENT) > 0 ?
               GraphConstants.BOTTOM_ALIGNMENT : 0);
      }

      return alignCss;
   }

   /**
    * Get the alignment.
    */
   public int getAlignment() {
      return userfmt.isAlignmentDefined() ? userfmt.getAlignment() :
         (!VSUtil.isIgnoreCSSFormat() && cssfmt.isAlignmentDefined()) ?
         rotateCSS(cssfmt.getAlignment()) : deffmt.getAlignment();
   }

   /**
    * Set the alignment.
    */
   public void setAlignment(int align) {
      userfmt.setAlignment(align);
   }

   /**
    * Get the foreground.
    * @return the foreground of this format.
    */
   public Color getColor() {
      return userfmt.isColorDefined() ? userfmt.getColor() :
         (!VSUtil.isIgnoreCSSFormat() && cssfmt.isColorDefined()) ?
         cssfmt.getColor() : deffmt.getColor();
   }

   /**
    * Set forground color.
    * @param color to set on user defined format.
    */
   public void setColor(Color color) {
      userfmt.setColor(color);
   }

   /**
    * Get the background.
    * @return the background of this format.
    */
   public Color getBackground() {
      return userfmt.isBackgroundDefined() ? userfmt.getBackground() :
         (!VSUtil.isIgnoreCSSFormat() && cssfmt.isBackgroundDefined()) ?
         cssfmt.getBackground() : deffmt.getBackground();
   }

   /**
    * Set background color.
    * @param color to set on user defined format.
    */
   public void setBackground(Color color) {
      userfmt.setBackground(color);
   }

   /**
    * Get the alpha.
    */
   public int getAlpha() {
      return userfmt.isAlphaDefined() ? userfmt.getAlpha() :
         (!VSUtil.isIgnoreCSSFormat() && cssfmt.isAlphaDefined()) ?
         cssfmt.getAlpha() : deffmt.getAlpha();
   }

   /**
    * Set alpha.
    */
   public void setAlpha(int alpha) {
      userfmt.setAlpha(alpha);
   }

   /**
    * Get the background color with alpha applied.
    */
   public Color getBackgroundWithAlpha() {
      Color bg = getBackground();
      return bg != null ? GTool.getColor(bg, getAlpha() / 100.0) : null;
   }

   /**
    * Get the rotation for this text format.
    * @return the text rotation.
    */
   public Number getRotation() {
      return userfmt.isRotationDefined() ? userfmt.getRotation() :
         (!VSUtil.isIgnoreCSSFormat() && cssfmt.isRotationDefined()) ?
         cssfmt.getRotation() : deffmt.getRotation();
   }

   /**
    * Set rotation.
    * @param rotation the rotation to set on user defined format.
    */
   public void setRotation(Number rotation) {
      userfmt.setRotation(rotation);
   }

   /**
    * Get the format option.
    * @return the format option of this format.
    */
   public XFormatInfo getFormat() {
      return userfmt.getFormat() != null && !userfmt.getFormat().isEmpty() ? userfmt.getFormat() :
         deffmt.getFormat();
   }

   /**
    * Set format on user defined format.
    * @param finfo the XFormatInfo to set.
    */
   public void setFormat(XFormatInfo finfo) {
      userfmt.setFormat(finfo);
   }

   /**
    * Get css format.
    */
   public CSSTextFormat getCSSFormat() {
      return cssfmt;
   }

   /**
    * Set css format.
    */
   public void setCSSFormat(CSSTextFormat fmt) {
      cssfmt = fmt;
   }

   /**
    * Get default format.
    */
   public TextFormat getDefaultFormat() {
      return deffmt;
   }

   /**
    * Set default format.
    */
   public void setDefaultFormat(TextFormat fmt) {
      deffmt = fmt;
   }

   /**
    * Get user defined format.
    */
   public TextFormat getUserDefinedFormat() {
      return userfmt;
   }

   /**
    * Set user defined format.
    */
   public void setUserDefinedFormat(TextFormat fmt) {
      userfmt = fmt;
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      Element currElem = Tool.getChildNodeByTagName(elem, "defaultFormat");

      if(currElem != null) {
         deffmt.parseXML(Tool.getChildNodeByTagName(currElem, "textFormat"));
      }

      currElem = Tool.getChildNodeByTagName(elem, "cssFormat");

      if(currElem != null) {
         cssfmt.parseXML(Tool.getChildNodeByTagName(currElem, "cssTextFormat"));
      }

      currElem = Tool.getChildNodeByTagName(elem, "userFormat");

      if(currElem != null) {
         userfmt.parseXML(Tool.getChildNodeByTagName(currElem, "textFormat"));
      }
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<compositeTextFormat class=\"");
      writer.print(getClass().getName());
      writer.print("\">");
      writeContents(writer);
      writer.print("</compositeTextFormat>");
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   private void writeContents(PrintWriter writer) {
      if(deffmt != null) {
         writer.print("<defaultFormat>");
         deffmt.writeXML(writer);
         writer.println("</defaultFormat>");
      }

      if(cssfmt != null && !VSUtil.isIgnoreCSSFormat()) {
         writer.print("<cssFormat>");
         cssfmt.writeXML(writer);
         writer.println("</cssFormat>");
      }

      if(userfmt != null) {
         writer.print("<userFormat>");
         userfmt.writeXML(writer);
         writer.println("</userFormat>");
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public CompositeTextFormat clone() {
      try {
         CompositeTextFormat format = (CompositeTextFormat) super.clone();

         if(deffmt != null) {
            format.setDefaultFormat((TextFormat) deffmt.clone());
         }

         if(cssfmt != null) {
            format.setCSSFormat((CSSTextFormat) cssfmt.clone());
         }

         if(userfmt != null) {
            format.setUserDefinedFormat((TextFormat) userfmt.clone());
         }

         return format;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone CompositeTextFormat", ex);
      }

      return null;
   }

   /**
    * Check if equals another objects.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof CompositeTextFormat)) {
         return false;
      }

      CompositeTextFormat txtfmt = (CompositeTextFormat) obj;

      return Tool.equals(this.cssfmt, txtfmt.cssfmt) &&
         Tool.equals(this.deffmt, txtfmt.deffmt) &&
         Tool.equals(this.userfmt, txtfmt.userfmt);
   }

   private CSSTextFormat cssfmt;
   private TextFormat deffmt;
   private TextFormat userfmt;

   protected static final Logger LOG =
      LoggerFactory.getLogger(CompositeTextFormat.class);
}
