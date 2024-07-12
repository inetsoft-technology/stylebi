/*
 * This file is part of StyleBI.
 *
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
 */
package inetsoft.report.io.viewsheet.excel;

import inetsoft.util.Tool;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import java.awt.Font;
import java.awt.Color;

/**
 * This class contains all the format record.
 *
 * @version 12.2
 * @author InetSoft Technology Corp
 */
public class XSSFFormatRecord {
   /**
    * Create an XSSFFormatRecord.
    */
   public XSSFFormatRecord() {
      super();
   }

   /**
    * Create an XSSFFormatRecord.
    */
   public XSSFFormatRecord(XSSFFormatRecord record) {
      font = record.font;
      topBorderColor = record.topBorderColor;
      leftBorderColor = record.leftBorderColor;
      rightBorderColor = record.rightBorderColor;
      bottomBorderColor = record.bottomBorderColor;
      fillForegroundColor = record.fillForegroundColor;
      borderTop = record.borderTop;
      borderLeft = record.borderLeft;
      borderRight = record.borderRight;
      borderBottom = record.borderBottom;
      alignment = record.alignment;
      verticalAlignment = record.verticalAlignment;
      fillPattern = record.fillPattern;
      dataFormat = record.dataFormat;
      rotation = record.rotation;
      wrapText = record.wrapText;

      jFont = record.jFont;
      jColor = record.jColor;
      jTopBorderColor = record.jTopBorderColor;
      jLeftBorderColor = record.jLeftBorderColor;
      jBottomBorderColor = record.jBottomBorderColor;
      jRightBorderColor = record.jRightBorderColor;
      jFillForegroundColor = record.jFillForegroundColor;
   }

   /**
    * Get the font.
    * @return the font of the record.
    */
   public XSSFFont getFont() {
      return font;
   }

   /**
    * Set the font.
    * @param font the specified font.
    */
   public void setFont(XSSFFont font, Font jFont, Color jColor) {
      this.font = font;
      this.jFont = jFont;
      this.jColor = jColor;
   }

   /**
    * Get the top border color.
    * @return the top border color of the record.
    */
   public XSSFColor getTopBorderColor() {
      return topBorderColor != null ? topBorderColor :
         (jTopBorderColor != null ? new XSSFColor(jTopBorderColor, null) : null);
   }

   /**
    * Set the top border color.
    * @param topBorderColor the specified top border color.
    */
   public void setTopBorderColor(XSSFColor topBorderColor, Color jColor) {
      this.topBorderColor = topBorderColor;
      this.jTopBorderColor = jColor;
   }

   /**
    * Get the left border color.
    * @return the left border color of the record.
    */
   public XSSFColor getLeftBorderColor() {
      return leftBorderColor != null ? leftBorderColor :
         (jLeftBorderColor != null ? new XSSFColor(jLeftBorderColor, null) : null);
   }

   /**
    * Set the left border color.
    * @param leftBorderColor the specified left border color.
    */
   public void setLeftBorderColor(XSSFColor leftBorderColor, Color jColor) {
      this.leftBorderColor = leftBorderColor;
      this.jLeftBorderColor = jColor;
   }

   /**
    * Get the right border color.
    * @return the right border color of the record.
    */
   public XSSFColor getRightBorderColor() {
      return rightBorderColor != null ? rightBorderColor :
         (jRightBorderColor != null ? new XSSFColor(jRightBorderColor, null) : null);
   }

   /**
    * Set the right border color.
    * @param rightBorderColor the specified right border color.
    */
   public void setRightBorderColor(XSSFColor rightBorderColor, Color jColor) {
      this.rightBorderColor = rightBorderColor;
      this.jRightBorderColor = jColor;
   }

   /**
    * Get the bottom border color.
    * @return the bottom border color of the record.
    */
   public XSSFColor getBottomBorderColor() {
      return bottomBorderColor != null ? bottomBorderColor :
         (jBottomBorderColor != null ? new XSSFColor(jBottomBorderColor, null) : null);
   }

   /**
    * Set the bottom border color.
    * @param bottomBorderColor the specified bottom border color.
    */
   public void setBottomBorderColor(XSSFColor bottomBorderColor, Color jColor) {
      this.bottomBorderColor = bottomBorderColor;
      this.jBottomBorderColor = jColor;
   }

   /**
    * Get the fill foreground color.
    * @return the fill foreground color of the record.
    */
   public XSSFColor getFillForegroundColor() {
      return fillForegroundColor != null ? fillForegroundColor :
         (jFillForegroundColor != null ? new XSSFColor(jFillForegroundColor, null) : null);
   }

   /**
    * Set the fill foreground color.
    * @param fillForegroundColor the specified fill foreground color.
    */
   public void setFillForegroundColor(XSSFColor fillForegroundColor, Color jColor) {
      this.fillForegroundColor = fillForegroundColor;
      this.jFillForegroundColor = jColor;
   }

   /**
    * Get the border top.
    * @return the border top of the record.
    */
   public BorderStyle getBorderTop() {
      return borderTop;
   }

   /**
    * Set the border top.
    * @param borderTop the specified border top.
    */
   public void setBorderTop(BorderStyle borderTop) {
      this.borderTop = borderTop;
   }

   /**
    * Get the border left.
    * @return the border left of the record.
    */
   public BorderStyle getBorderLeft() {
      return borderLeft;
   }

   /**
    * Set the border left.
    * @param borderLeft the specified border left.
    */
   public void setBorderLeft(BorderStyle borderLeft) {
      this.borderLeft = borderLeft;
   }

   /**
    * Get the border right.
    * @return the border right of the record.
    */
   public BorderStyle getBorderRight() {
      return borderRight;
   }

   /**
    * Set the border right.
    * @param borderRight the specified border right.
    */
   public void setBorderRight(BorderStyle borderRight) {
      this.borderRight = borderRight;
   }

   /**
    * Get the border bottom.
    * @return the border bottom of the record.
    */
   public BorderStyle getBorderBottom() {
      return borderBottom;
   }

   /**
    * Set the border bottom.
    * @param borderBottom the specified border bottom.
    */
   public void setBorderBottom(BorderStyle borderBottom) {
      this.borderBottom = borderBottom;
   }

   /**
    * Get if wrap text.
    * @return wrap text of the record.
    */
   public boolean isWrapText() {
      return wrapText;
   }

   /**
    * Set if wrap text.
    * @param wrapText the specified wrap text.
    */
   public void setWrapText(boolean wrapText) {
      this.wrapText = wrapText;
   }

   /**
    * Get alignment.
    * @return alignment of the record.
    */
   public HorizontalAlignment getAlignment() {
      return alignment;
   }

   /**
    * Set alignment.
    * @param alignment the specified alignment.
    */
   public void setAlignment(HorizontalAlignment alignment) {
      this.alignment = alignment;
   }

   /**
    * Get vertical alignment.
    * @return vertical alignment of the record.
    */
   public VerticalAlignment getVerticalAlignment() {
      return verticalAlignment;
   }

   /**
    * Set vertical alignment.
    * @param verticalAlignment the specified vertical alignment.
    */
   public void setVerticalAlignment(VerticalAlignment verticalAlignment) {
      this.verticalAlignment = verticalAlignment;
   }

   /**
    * Get fill pattern.
    * @return fill pattern of the record.
    */
   public FillPatternType getFillPattern() {
      return fillPattern;
   }

   /**
    * Set fill pattern.
    * @param fillPattern the specified fill pattern.
    */
   public void setFillPattern(FillPatternType fillPattern) {
      this.fillPattern = fillPattern;
   }

   /**
    * Get data format.
    * @return data format of the record.
    */
   public short getDataFormat() {
      return dataFormat;
   }

   /**
    * Set data format.
    * @param dataFormat the specified data format.
    */
   public void setDataFormat(short dataFormat) {
      this.dataFormat = dataFormat;
   }

   /**
    * Get rotation.
    * @return rotation of the record.
    */
   public short getRotation() {
      return rotation;
   }

   /**
    * Set rotation.
    * @param rotation the specified rotation.
    */
   public void setRotation(short rotation) {
      this.rotation = rotation;
   }

   public void setJustify(boolean isJustify) {
      alignment = isJustify ? HorizontalAlignment.JUSTIFY : alignment;
   }

   public void exchangeAlignment() {
      // H_ALIGN V_ALIGN
      // LEFT -> BOTTOM
      // RIGHT -> TOP

      VerticalAlignment v = null;

      if(alignment == HorizontalAlignment.LEFT) {
         v = VerticalAlignment.BOTTOM;
      }
      else if(alignment == HorizontalAlignment.RIGHT) {
         v = VerticalAlignment.TOP;
      }

      // V_ALIGN H_ALIGN
      // TOP -> LEFT
      // BOTTOM -> RIGHT
      HorizontalAlignment h = null;

      if(verticalAlignment == VerticalAlignment.TOP) {
         h = HorizontalAlignment.LEFT;
      }
      else if(verticalAlignment == VerticalAlignment.BOTTOM) {
         h = HorizontalAlignment.RIGHT;
      }

      if(v != null) {
         verticalAlignment = v;
      }

      if(h != null) {
         alignment = h;
      }
   }

   public boolean equals(Object obj) {
      if(!(obj instanceof XSSFFormatRecord)) {
         return false;
      }

      XSSFFormatRecord record = (XSSFFormatRecord) obj;

      return Tool.equals(jFont, record.jFont) &&
         Tool.equals(jColor, record.jColor) &&
         Tool.equals(jTopBorderColor, record.jTopBorderColor) &&
         Tool.equals(jLeftBorderColor, record.jLeftBorderColor) &&
         Tool.equals(jBottomBorderColor, record.jBottomBorderColor) &&
         Tool.equals(jRightBorderColor, record.jRightBorderColor) &&
         Tool.equals(jFillForegroundColor, record.jFillForegroundColor) &&
         borderTop == record.borderTop &&
         borderLeft == record.borderLeft &&
         borderRight == record.borderRight &&
         borderBottom == record.borderBottom &&
         alignment == record.alignment &&
         verticalAlignment == record.verticalAlignment &&
         fillPattern == record.fillPattern &&
         dataFormat == record.dataFormat &&
         rotation == record.rotation &&
         wrapText == record.wrapText;
   }

   public int hashCode() {
      int hash = 0;

      if(jFont != null) {
	      hash += jFont.hashCode();
      }

      if(jColor != null) {
         hash += jColor.hashCode();
      }
      
      if(jTopBorderColor != null) {
         hash += jTopBorderColor.hashCode();
      }

      if(jLeftBorderColor != null) {
         hash += jLeftBorderColor.hashCode();
      }

      if(jBottomBorderColor != null) {
         hash += jBottomBorderColor.hashCode();
      }

      if(jRightBorderColor != null) {
         hash += jRightBorderColor.hashCode();
      }
      
      if(jFillForegroundColor != null) {
         hash += jFillForegroundColor.hashCode();
      }
      
      hash += borderTop.getCode();
      hash += borderLeft.getCode();
      hash += borderRight.getCode();
      hash += borderBottom.getCode();
      hash += alignment.getCode();
      hash += verticalAlignment.getCode();
      hash += fillPattern.getCode();
      hash += dataFormat;
      hash += rotation;

      return hash;
   }

   public String toString() {
      return "XSSFFormatRecord[" +
         "font: " + jFont + " " + jColor + 
         " topBorderColor: " + jTopBorderColor +
         " leftBorderColor: " + jLeftBorderColor +
         " rightBorderColor: " + jRightBorderColor +
         " bottomBorderColor: " + jBottomBorderColor +
         " fillForegroundColor: " + jFillForegroundColor +
         " borderTop: " + borderTop +
         " borderLeft: " + borderLeft +
         " borderRight: " + borderRight +
         " borderBottom: " + borderBottom +
         " alignment: " + alignment +
         " verticalAlignment: " + verticalAlignment +
         " fillPattern: " + fillPattern +
         " dataFormat: " + dataFormat +
         " rotation: " + rotation +
         " wrapText: " + wrapText + "]";
   }

   // optimization, XSSFFont equals/hashCode is very expensive, store
   // java font/color for comparison
   private Font jFont;
   private Color jColor;
   private Color jTopBorderColor;
   private Color jLeftBorderColor;
   private Color jRightBorderColor;
   private Color jBottomBorderColor;
   private Color jFillForegroundColor;

   XSSFFont font;
   XSSFColor topBorderColor;
   XSSFColor leftBorderColor;
   XSSFColor rightBorderColor;
   XSSFColor bottomBorderColor;
   XSSFColor fillForegroundColor;
   BorderStyle borderTop = BorderStyle.NONE;
   BorderStyle borderLeft = BorderStyle.NONE;
   BorderStyle borderRight = BorderStyle.NONE;
   BorderStyle borderBottom = BorderStyle.NONE;
   HorizontalAlignment alignment = HorizontalAlignment.LEFT;
   VerticalAlignment verticalAlignment = VerticalAlignment.TOP;
   FillPatternType fillPattern = FillPatternType.NO_FILL;
   short dataFormat = 0;
   short rotation = 0;
   boolean wrapText = false;
}
