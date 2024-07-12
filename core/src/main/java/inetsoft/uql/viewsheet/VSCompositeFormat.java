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
package inetsoft.uql.viewsheet;

import inetsoft.report.internal.table.PresenterRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.internal.XVSFormat;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.*;
import java.util.List;

/**
 * VSCompositeFormat distinguish the hierarchy of user format, css format and
 * default format.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class VSCompositeFormat implements XVSFormat {
   /**
    * Constructor.
    */
   public VSCompositeFormat() {
      super();
      deffmt = new VSFormat();
      cssfmt = new VSCSSFormat();
      cssfmt.setVSCompositeFormat(this);
      userfmt = new VSFormat();
   }

   /**
    * Get the run time alignment (horizontal and vertical).
    * @return the alignment of this format.
    */
   @Override
   public int getAlignment() {
      return (userfmt.isAlignmentDefined() || userfmt.isAlignmentValueDefined())
         ? userfmt.getAlignment()
         : cssfmt.isAlignmentValueDefined() ? cssfmt.getAlignmentValue()
         : deffmt.getAlignment();
   }

   /**
    * Get the design time alignment (horizontal and vertical).
    * @return the alignment of this format.
    */
   @Override
   public int getAlignmentValue() {
      return userfmt.isAlignmentValueDefined() ? userfmt.getAlignmentValue() :
         cssfmt.isAlignmentValueDefined() ? cssfmt.getAlignmentValue() :
         deffmt.getAlignmentValue();
   }

   /**
    * Get the background.
    * @return the background of this format.
    */
   @Override
   public Color getBackground() {
      Color bg = (Color) Tool.getData(XSchema.COLOR, cssfmt.getBackgroundValue());

      Color color = (userfmt.isBackgroundDefined() ||
                     userfmt.isBackgroundValueDefined())
         ? userfmt.getBackground()
         : cssfmt.isBackgroundValueDefined() ? bg:
         deffmt.getBackground();

      if(color != null) {
         color = new Color(color.getRed(), color.getGreen(), color.getBlue(),
            Math.round(getAlpha() * 255f / 100));
      }

      return color;
   }

   /**
    * Get the background value (expression or RGB number).
    * @return the background value of this format.
    */
   @Override
   public String getBackgroundValue() {
      return userfmt.isBackgroundValueDefined() ? userfmt.getBackgroundValue() :
         cssfmt.isBackgroundValueDefined() ? cssfmt.getBackgroundValue() :
         deffmt.getBackgroundValue();
   }

   /**
    * Get the border colors.
    * @return the border colors of this format.
    */
   @Override
   public BorderColors getBorderColors() {
      return (userfmt.isBorderColorsDefined() ||
              userfmt.isBorderColorsValueDefined())
         ? userfmt.getBorderColors()
         : cssfmt.isBorderColorsValueDefined() ? cssfmt.getBorderColorsValue() :
         deffmt.getBorderColors();
   }

    /**
    * Get the border colors value.
    * @return the border colors of this format.
    */
   @Override
   public BorderColors getBorderColorsValue() {
      return userfmt.isBorderColorsValueDefined() ?
         userfmt.getBorderColorsValue() : cssfmt.isBorderColorsValueDefined() ?
         cssfmt.getBorderColorsValue() : deffmt.getBorderColorsValue();
   }

   /**
    * Get the borders.
    * @return the borders of this format.
    */
   @Override
   public Insets getBorders() {
      return (userfmt.isBordersDefined() || userfmt.isBordersValueDefined())
         ? userfmt.getBorders()
         : cssfmt.isBordersValueDefined() ? cssfmt.getBordersValue()
         : deffmt.getBorders();
   }

   /**
    * Get the borders value.
    * @return the borders of this format.
    */
   @Override
   public Insets getBordersValue() {
      return userfmt.isBordersValueDefined() ? userfmt.getBordersValue() :
         cssfmt.isBordersValueDefined() ? cssfmt.getBordersValue() :
         deffmt.getBordersValue();
   }

   /**
    * Get the font.
    * @return the font of this format.
    */
   @Override
   public Font getFont() {
      Font font = (userfmt.isFontDefined() || userfmt.isFontValueDefined()) ?
         userfmt.getFont() : cssfmt.isFontValueDefined() ?
         cssfmt.getFontValue() : deffmt.getFont();

      return font != null ? font.deriveFont((float) (font.getSize() * rscaleFont)) : font;
   }

   /**
    * Get the font value.
    * @return the font of this format.
    */
   @Override
   public Font getFontValue() {
      return userfmt.isFontValueDefined() ? userfmt.getFontValue() :
         cssfmt.isFontValueDefined() ? cssfmt.getFontValue() :
         deffmt.getFontValue();
   }

   @Override
   public GradientColor getGradientColorValue() {
      return userfmt.isGradientColorValueDefined() ? this.userfmt.getGradientColorValue() :
         cssfmt.isGradientColorValueDefined() ? cssfmt.getGradientColorValue() :
         deffmt.getGradientColorValue();
   }

   @Override
   public GradientColor getGradientColor() {
      return (userfmt.isGradientColorDefined() || userfmt.isGradientColorValueDefined())
         ? this.userfmt.getGradientColor()
         : cssfmt.isGradientColorDefined()
            ? cssfmt.getGradientColor()
            : deffmt.getGradientColor();
   }

   /**
    * Get the foreground.
    * @return the foreground of this format.
    */
   @Override
   public Color getForeground() {
      Color fg = (Color) Tool.getData(XSchema.COLOR, cssfmt.getForegroundValue());

      return (userfmt.isForegroundDefined() ||
         userfmt.isForegroundValueDefined()) ? userfmt.getForeground()
         : cssfmt.isForegroundValueDefined() ? fg :
         deffmt.getForeground();
   }

   /**
    * Get the foreground value (expression or RGB number).
    * @return the foreground value of this format.
    */
   @Override
   public String getForegroundValue() {
      return userfmt.isForegroundValueDefined() ? userfmt.getForegroundValue() :
         cssfmt.isForegroundValueDefined() ? cssfmt.getForegroundValue() :
         deffmt.getForegroundValue();
   }

   /**
    * Get the format option.
    * @return the format option of this format.
    */
   @Override
   public String getFormat() {
      return (userfmt.isFormatValueDefined() || userfmt.isFormatDefined()) ?
         userfmt.getFormat() : deffmt.getFormat();
   }

   /**
    * Get the format option value.
    * @return the format option of this format.
    */
   @Override
   public String getFormatValue() {
      return userfmt.isFormatValueDefined() ? userfmt.getFormatValue() :
         deffmt.getFormatValue();
   }

   /**
    * Get the format extent (pattern or predefined extent type).
    * @return the format extent of this format.
    */
   @Override
   public String getFormatExtent() {
      return (userfmt.isFormatValueDefined() || userfmt.isFormatDefined()) ?
         userfmt.getFormatExtent() : deffmt.getFormatExtent();
   }

   /**
    * Get the format extent (pattern or predefined extent type).
    * @return the format extent of this format.
    */
   @Override
   public String getFormatExtentValue() {
      return userfmt.isFormatValueDefined() ? userfmt.getFormatExtentValue() :
         deffmt.getFormatExtentValue();
   }

   /**
    * Check if should wrap text at run time.
    * @return true if should wrap text, false otherwise.
    */
   @Override
   public boolean isWrapping() {
      return (userfmt.isWrappingDefined() || userfmt.isWrappingValueDefined())
         ? userfmt.isWrapping()
         : cssfmt.isWrappingValueDefined() ? cssfmt.getWrappingValue()
         : deffmt.isWrapping();
   }

   /**
    * Check if should wrap text at design time.
    * @return true if should wrap text, false otherwise.
    */
   @Override
   public boolean getWrappingValue() {
      return userfmt.isWrappingValueDefined() ? userfmt.getWrappingValue() :
         cssfmt.isWrappingValueDefined() ? cssfmt.getWrappingValue() :
         deffmt.getWrappingValue();
   }

   /**
    * Get the cell span.
    */
   @Override
   public Dimension getSpan() {
      return userfmt.getSpan() != null ? userfmt.getSpan() : deffmt.getSpan();
   }

   /**
    * Get alpha.
    */
   @Override
   public int getAlpha() {
      return (userfmt.isAlphaDefined() || userfmt.isAlphaValueDefined())
         ? userfmt.getAlpha()
         : cssfmt.isAlphaValueDefined() ? cssfmt.getAlphaValue()
         : deffmt.getAlpha();
   }

   /**
    * Get alpha value.
    */
   @Override
   public int getAlphaValue() {
      return userfmt.isAlphaValueDefined() ? userfmt.getAlphaValue() :
         cssfmt.isAlphaValueDefined() ? cssfmt.getAlphaValue() :
         deffmt.getAlphaValue();
   }

   @Override
   public int getRoundCorner() {
      return (userfmt.isRoundCornerDefined() || userfmt.isRoundCornerValueDefined())
         ? userfmt.getRoundCorner()
         : cssfmt.isRoundCornerValueDefined() ? cssfmt.getRoundCornerValue()
         : deffmt.getRoundCorner();
   }

   /**
    * Get roundCorner value.
    */
   @Override
   public int getRoundCornerValue() {
      return userfmt.isRoundCornerValueDefined() ? userfmt.getRoundCornerValue() :
         cssfmt.isRoundCornerValueDefined() ? cssfmt.getRoundCornerValue() :
         deffmt.getRoundCornerValue();
   }

   @Override
   public PresenterRef getPresenter() {
      return (userfmt.isPresenterDefined() || userfmt.isPresenterValueDefined())
         ? userfmt.getPresenter()
         : cssfmt.isPresenterValueDefined() ? cssfmt.getPresenterValue()
         : deffmt.getPresenter();
   }

   @Override
   public PresenterRef getPresenterValue() {
      return userfmt.isPresenterValueDefined() ? userfmt.getPresenterValue() :
         cssfmt.isPresenterValueDefined() ? cssfmt.getPresenterValue() :
         deffmt.getPresenterValue();
   }

   /**
    * Check if alignment is defined.
    */
   @Override
   public boolean isAlignmentDefined() {
      return true;
   }

   /**
    * Check if background color is defined.
    */
   @Override
   public boolean isBackgroundDefined() {
      return true;
   }

   /**
    * Check if GradientColor color is defined.
    */
   @Override
   public boolean isGradientColorDefined() {
      return true;
   }

   /**
    * Check if border colors is defined.
    */
   @Override
   public boolean isBorderColorsDefined() {
      return true;
   }

   /**
    * Check if borders is defined.
    */
   @Override
   public boolean isBordersDefined() {
      return true;
   }

   /**
    * Check if font is defined.
    */
   @Override
   public boolean isFontDefined() {
      return true;
   }

   /**
    * Check if foreground color is defined.
    */
   @Override
   public boolean isForegroundDefined() {
      return true;
   }

   /**
    * Check if text wrap is defined.
    */
   @Override
   public boolean isWrappingDefined() {
      return true;
   }

   /**
    * Check if alpha is defined.
    */
   @Override
   public boolean isAlphaDefined() {
      return true;
   }

   @Override
   public boolean isRoundCornerDefined() {
      return true;
   }

   /**
    * Check if background value is defined.
    */
   @Override
   public boolean isBackgroundValueDefined() {
      return true;
   }

   @Override
   public boolean isGradientColorValueDefined() {
      return true;
   }

   /**
    * Check if foreground value is defined.
    */
   @Override
   public boolean isForegroundValueDefined() {
      return true;
   }

   /**
    * Check if design time alignment is defined.
    */
   @Override
   public boolean isAlignmentValueDefined() {
      return true;
   }

   /**
    * Check if border colors value is defined.
    */
   @Override
   public boolean isBorderColorsValueDefined() {
      return true;
   }

   /**
    * Check if borders value is defined.
    */
   @Override
   public boolean isBordersValueDefined() {
      return true;
   }

   /**
    * Check if text wrap is defined at design time.
    */
   @Override
   public boolean isWrappingValueDefined() {
      return true;
   }

   /**
    * Check if alpha value is defined.
    */
   @Override
   public boolean isAlphaValueDefined() {
      return true;
   }

   @Override
   public boolean isRoundCornerValueDefined() {
      return true;
   }

   /**
    * Check if font is defined.
    */
   @Override
   public boolean isFontValueDefined() {
      return true;
   }

   @Override
   public boolean isPresenterDefined() {
      return true;
   }

   @Override
   public boolean isPresenterValueDefined() {
      return true;
   }

   /**
    * Get css format.
    */
   public VSCSSFormat getCSSFormat() {
      return cssfmt;
   }

   /**
    * Set css format.
    */
   public void setCSSFormat(VSCSSFormat fmt) {
      cssfmt = fmt;
      cssfmt.setVSCompositeFormat(this);
   }

   /**
    * Get default format.
    */
   public VSFormat getDefaultFormat() {
      return deffmt;
   }

   /**
    * Set default format.
    */
   public void setDefaultFormat(VSFormat fmt) {
      deffmt = fmt;
   }

   /**
    * Get user defined format.
    */
   public VSFormat getUserDefinedFormat() {
      return userfmt;
   }

   /**
    * Get the dynamic values.
    * @return the userfmt's dynamic values.
    */
   public List<DynamicValue> getDynamicValues() {
      return userfmt.getDynamicValues();
   }

   /**
    * Shrink the viewsheet format object for runtime usage.
    */
   public void shrink() {
      userfmt.shrink();
   }

   /**
    * Set user defined format.
    */
   public void setUserDefinedFormat(VSFormat fmt) {
      userfmt = (fmt == null) ? new VSFormat() : fmt;
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      Element defElem = Tool.getChildNodeByTagName(elem, "defaultFormat");
      Element cssElem = Tool.getChildNodeByTagName(elem, "cssFormat");
      Element userElem = Tool.getChildNodeByTagName(elem, "userFormat");

      tblRuntimeMode = "true".equals(Tool.getAttribute(elem, "runtime"));
      String scalefont = Tool.getAttribute(elem, "rscaleFont");

      if(scalefont != null) {
         rscaleFont = Float.parseFloat(scalefont);
      }

      if(defElem != null) {
         deffmt.parseXML(Tool.getChildNodeByTagName(defElem, "VSFormat"));
      }

      if(cssElem != null) {
         cssfmt.parseXML(Tool.getChildNodeByTagName(cssElem, "VSCSSFormat"));
         cssfmt.setVSCompositeFormat(this);
      }

      if(userElem != null) {
         userfmt.parseXML(Tool.getChildNodeByTagName(userElem, "VSFormat"));
      }

      // @by larryl, backward compatibility (10.1)
      if(defElem == null && cssElem == null && userElem == null) {
         deffmt.parseXML(elem);
      }
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<VSCompositeFormat class=\"" + getClass().getName()+ "\"");

      if(rscaleFont != 1) {
         writer.print(" rscaleFont=\"" + rscaleFont + "\"");
      }

      writer.print(" runtime=\"" + tblRuntimeMode + "\">");
      writeContents(writer);
      writer.print("</VSCompositeFormat>");
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

      if(cssfmt != null) {
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
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    */
   public void writeData(DataOutputStream output) throws IOException {
      output.writeBoolean(tblRuntimeMode);
      output.writeFloat(rscaleFont);
      userfmt.writeData(output);
      cssfmt.writeData(output);
      deffmt.writeData(output);
   }

   /**
    * Parse data from an InputStream.
    * @param input the source DataInputStream.
    * @return <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
    */
   public boolean parseData(DataInputStream input) {
      return true;
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public VSCompositeFormat clone() {
      VSCompositeFormat format = new VSCompositeFormat();
      format.tblRuntimeMode = tblRuntimeMode;
      format.rscaleFont = rscaleFont;

      if(deffmt != null) {
         format.setDefaultFormat((VSFormat) deffmt.clone());
      }

      if(cssfmt != null) {
         format.setCSSFormat((VSCSSFormat) cssfmt.clone());
         format.getCSSFormat().setVSCompositeFormat(format);
      }

      if(userfmt != null) {
         format.setUserDefinedFormat((VSFormat) userfmt.clone());
      }

      format.setFormatInfo(formatInfo);
      return format;
   }

   /**
    * Get the string representation.
    * @return the string representation of this object.
    */
   public String toString() {
      return "VSCompositeFormat: [[Default: " + deffmt.toString() +
         "], [CSSFile: " + cssfmt.toString() +
         "], [UserDefined: " + userfmt.toString() + "]]" + this.hashCode();
   }

   /**
    * Set table runtime mode.
    */
   public void setTableRuntimeMode(boolean rtMode) {
      tblRuntimeMode = rtMode;
   }

   /**
    * Get table runtime mode.
    */
   public boolean isTableRuntimeMode() {
      return tblRuntimeMode;
   }

   /**
    * Check two objects equals.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof VSCompositeFormat)) {
         return false;
      }

      VSCompositeFormat fmt2 = (VSCompositeFormat) obj;

      return Tool.equals(cssfmt, fmt2.cssfmt) &&
         Tool.equals(deffmt, fmt2.deffmt) &&
         Tool.equals(userfmt, fmt2.userfmt) &&
         Tool.equals(tblRuntimeMode, fmt2.tblRuntimeMode) &&
         Tool.equals(rscaleFont, fmt2.rscaleFont);
   }

   public boolean isDefined() {
      return cssfmt.isCSSDefined() || userfmt.isDefined();
   }

   /**
    * Calculate the hashcode of the format.
    */
   public int hashCode() {
      int hash = 0;

      if(cssfmt != null) {
         hash += cssfmt.hashCode();
      }

      if(deffmt != null) {
         hash += deffmt.hashCode();
      }

      if(userfmt != null) {
         hash += userfmt.hashCode();
      }

      return hash;
   }

   /**
    * Set run time scale font.
    */
   public void setRScaleFont(float scale) {
      this.rscaleFont = scale;
   }

   /**
    * Get run time scale font.
    */
   public float getRScaleFont() {
      return rscaleFont;
   }

   public FormatInfo getFormatInfo() {
      return formatInfo;
   }

   public void setFormatInfo(FormatInfo formatInfo) {
      this.formatInfo = formatInfo;
   }

   private VSCSSFormat cssfmt;
   private VSFormat deffmt;
   private VSFormat userfmt;
   private boolean tblRuntimeMode = false;
   private float rscaleFont = 1;
   private FormatInfo formatInfo;
}
