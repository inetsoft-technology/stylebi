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
package inetsoft.uql.viewsheet;

import inetsoft.report.StyleConstants;
import inetsoft.report.StyleFont;
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.uql.XConstants;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * VSFormat contains scalar format information. The format information will be
 * applied to format a scalar.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class VSFormat implements XVSFormat {
   /**
    * Date format type.
    */
   public static final String DATE_FORMAT = XConstants.DATE_FORMAT;
   /**
    * Decimal format type.
    */
   public static final String DECIMAL_FORMAT = XConstants.DECIMAL_FORMAT;
   /**
    * Currency format type.
    */
   public static final String CURRENCY_FORMAT = XConstants.CURRENCY_FORMAT;
   /**
    * Percent format type.
    */
   public static final String PERCENT_FORMAT = XConstants.PERCENT_FORMAT;
   /**
    * Message format type.
    */
   public static final String MESSAGE_FORMAT = XConstants.MESSAGE_FORMAT;

   /**
    * None defined.
    */
   public static final int NONE_DEFINED = 0;
   /**
    * Align defined.
    */
   public static final int ALIGN_DEFINED = 1;
   /**
    * Align value defined.
    */
   public static final int ALIGN_VAL_DEFINED = 2;
   /**
    * Background defined.
    */
   public static final int BACKGROUND_DEFINED = 4;
   /**
    * Background value defined.
    */
   public static final int BACKGROUND_VAL_DEFINED = 8;
   /**
    * Foreground defined.
    */
   public static final int FOREGROUND_DEFINED = 0x10;
   /**
    * Foreground value defined.
    */
   public static final int FOREGROUND_VAL_DEFINED = 0x20;
   /**
    * Border color defined.
    */
   public static final int BORDER_COLOR_DEFINED = 0x40;
   /**
    * Border color value defined.
    */
   public static final int BORDER_COLOR_VAL_DEFINED = 0x80;
   /**
    * Borders defined.
    */
   public static final int BORDER_DEFINED = 0x100;
   /**
    * Borders value defined.
    */
   public static final int BORDER_VAL_DEFINED = 0x200;
   /**
    * Font defined.
    */
   public static final int FONT_DEFINED = 0x400;
   /**
    * Font value defined.
    */
   public static final int FONT_VAL_DEFINED = 0x800;
   /**
    * Wrapping defined.
    */
   public static final int WRAPPING_DEFINED = 0x1000;
   /**
    * Wrapping value defined.
    */
   public static final int WRAPPING_VAL_DEFINED = 0x2000;
   /**
    * Alpha defined.
    */
   public static final int TRANS_DEFINED = 0x4000;
   /**
    * Alpha value defined.
    */
   public static final int TRANS_VAL_DEFINED = 0x8000;
   /**
    * Format defined.
    */
   public static final int FORMAT_DEFINED = 0x10000;
   /**
    * Format value defined.
    */
   public static final int FORMAT_VAL_DEFINED = 0x20000;
   /**
    * Presenter defined.
    */
   public static final int PRESENTER_DEFINED = 0x40000;
   /**
    * Presenter value defined.
    */
   public static final int PRESENTER_VAL_DEFINED = 0x80000;
   /**
    * Round corner value defined.
    */
   public static final int ROUND_CORNER_DEFINED = 0x80000;
   /**
    * Round corner value defined.
    */
   public static final int ROUND_CORNER_VAL_DEFINED = 0x80000;

   /**
    * Constructor.
    */
   public VSFormat() {
      super();

      bgval = new DynamicValue();
      bgval.setDataType(XSchema.COLOR);
      fgval = new DynamicValue("0", XSchema.COLOR);
   }

   /**
    * Get the run time alignment (horizontal and vertical).
    * @return the alignment of this format.
    */
   @Override
   public int getAlignment() {
      return getAlignment0(false, false);
   }

   /**
    * Get proper alignment.
    */
   private int getAlignment0(boolean design, boolean persistent) {
      int align = alignValue.getIntValue(design, ALIGN);
      // persietent use the real value
      return !persistent && align == -1 ? ALIGN : align;
   }

   /**
    * Set the run time alignment (horizontal and vertical) to this format.
    * @param align the specified alignment.
    */
   public void setAlignment(int align) {
      setAlignment(align, true);
   }

   /**
    * Set the run time alignment (horizontal and vertical) to this format.
    * @param align the specified alignment.
    */
   public void setAlignment(int align, boolean defined) {
      this.alignValue.setRValue(fixAlignment(align, false) + "");
      alignDefined = defined;
   }

   /**
    * Fix alignment value.
    */
   private int fixAlignment(int align, boolean design) {
      if(design && (align == -1 || align == 0)) {
         return align;
      }

      int halign = StyleConstants.H_LEFT | StyleConstants.H_CENTER | StyleConstants.H_RIGHT;
      int valign = StyleConstants.V_TOP | StyleConstants.V_CENTER | StyleConstants.V_BOTTOM;
      int nalign = 0;

      if((align & halign) != 0) {
         nalign |= (align & halign);
      }

      if((align & valign) != 0) {
         nalign |= (align & valign);
      }

      return nalign;
   }

   /**
    * Get the design time alignment (horizontal and vertical).
    * @return the alignment of this format.
    */
   @Override
   public int getAlignmentValue() {
      return getAlignmentValue(false);
   }

   private int getAlignmentValue(boolean persistent) {
      return getAlignment0(true, persistent);
   }

   /**
    * Set the alignment (horizontal and vertical) to this format.
    * @param align the specified alignment.
    */
   public void setAlignmentValue(int align) {
      setAlignmentValue(align, true);
   }

   /**
    * Set the alignment (horizontal and vertical) to this format.
    * @param align the specified alignment.
    */
   public void setAlignmentValue(int align, boolean defined) {
      this.alignValue.setDValue(fixAlignment(align, true) + "");
      alignValDefined = defined;
   }

   /**
    * Get the background.
    * @return the background of this format.
    */
   @Override
   public Color getBackground() {
      Object rcolor = null;

      if(bgval != null) {
         if(VSUtil.isDynamicValue(bgval.getDValue())) {
            rcolor = bgval.getRuntimeValue(true);
         }
         else {
            rcolor = getColorData(bgval.getRValue());
         }
      }

      return (rcolor instanceof Color) ? (Color) rcolor : bg;
   }

   /**
    * Set the background.
    * @param bg the specified background.
    */
   public void setBackground(Color bg) {
      setBackground(bg, true);
   }

   /**
    * Set the background.
    * @param bg the specified background.
    */
   public void setBackground(Color bg, boolean defined) {
      this.bg = bg;

      if(bgval != null) {
         bgval.setRValue(bg);
      }

      bgDefined = defined;
   }

   /**
    * Get the background value (expression or RGB number).
    * @return the background value of this format.
    */
   @Override
   public String getBackgroundValue() {
      return bgval != null ? bgval.getDValue() : null;
   }

   /**
    * Set the background value (expression or RGB number) to this format.
    * @param bgval the specified background value.
    */
   public void setBackgroundValue(String bgval) {
      setBackgroundValue(bgval, true);
   }

   /**
    * Set the background value (expression or RGB number) to this format.
    * @param bgval the specified background value.
    */
   public void setBackgroundValue(String bgval, boolean defined) {
      if(this.bgval == null) {
         this.bgval = new DynamicValue();
         this.bgval.setDataType(XSchema.COLOR);
      }

      this.bgval.setDValue(bgval);
      bgValDefined = defined;
   }

   /**
    * Set the GradientColor value to this format.
    */
   public void setGradientColorValue(GradientColor gradientColor) {
      setGradientColorValue(gradientColor, true);
   }

   public void setGradientColorValue(GradientColor gradientColor, boolean defined) {
      this.gradientColorVals.setDValue(gradientColor);
      this.gcValDefined = defined;
   }

   /**
    * Get the GradientColor value.
    * @return the GradientColor value of this format.
    */
   @Override
   public GradientColor getGradientColorValue() {
      return gradientColorVals != null ? this.gradientColorVals.getDValue() : null;
   }

   /**
    * Get the GradientColor value.
    * @return the GradientColor value of this format.
    */
   @Override
   public GradientColor getGradientColor() {
      return this.gradientColorVals.getRValue();
   }

   public void setGradientColor(GradientColor gc) {
      setGradientColor(gc, true);
   }

   public void setGradientColor(GradientColor gc, boolean defined) {
      this.gradientColorVals.setRValue(gc);
      gradientColorDefined = defined;
   }

   /**
    * Get the foreground.
    * @return the foreground of this format.
    */
   @Override
   public Color getForeground() {
      Object tempColor = null;

      if(fgval != null) {
         if(VSUtil.isDynamicValue(fgval.getDValue())) {
            tempColor = fgval.getRuntimeValue(true);
         }
         else {
            tempColor = getColorData(fgval.getRValue());
         }
      }

      return tempColor instanceof Color ? (Color) tempColor : fg;
   }

   /**
    * Get the color data.
    */
   private Color getColorData(Object val) {
      if(val instanceof Color) {
         return (Color) val;
      }
      else if(val instanceof String) {
         try {
            return new Color(Integer.decode((String) val));
         }
         catch(Exception ex) {
            return null;
         }
      }

      return null;
   }

   /**
    * Set the foreground.
    * @param fg the specified foreground.
    */
   public void setForeground(Color fg) {
      setForeground(fg, true);
   }

   /**
    * Set the foreground.
    * @param fg the specified foreground.
    */
   public void setForeground(Color fg, boolean defined) {
      this.fg = fg;

      if(fgval != null) {
         fgval.setRValue(fg);
      }

      fgDefined = defined;
   }

   /**
    * Get the foreground value (expression or RGB number).
    * @return the foreground value of this format.
    */
   @Override
   public String getForegroundValue() {
      return fgval != null ? fgval.getDValue() : null;
   }

   /**
    * Set the foreground value (expression or RGB number) to this format.
    * @param fgval the specified foreground value.
    */
   public void setForegroundValue(String fgval) {
      setForegroundValue(fgval, true);
   }

   /**
    * Set the foreground value (expression or RGB number) to this format.
    * @param fgval the specified foreground value.
    */
   public void setForegroundValue(String fgval, boolean defined) {
      if(this.fgval == null) {
         this.fgval = new DynamicValue();
         this.fgval.setDataType(XSchema.COLOR);
      }

      this.fgval.setDValue(fgval);
      fgValDefined = defined;
   }

   /**
    * Get the borders.
    * @return the borders of this format.
    */
   @Override
   public Insets getBorders() {
      return bordersValue.getRValue();
   }

   /**
    * Set the borders to this format.
    * @param borders the specified borders.
    */
   public void setBorders(Insets borders) {
      setBorders(borders, true);
   }

   /**
    * Set the borders to this format.
    * @param borders the specified borders.
    */
   public void setBorders(Insets borders, boolean defined) {
      this.bordersValue.setRValue(borders);
      bordersDefined = defined;
   }

   /**
    * Get the borders value.
    * @return the borders of this format.
    */
   @Override
   public Insets getBordersValue() {
      return bordersValue.getDValue();
   }

   /**
    * Set the borders value to this format.
    * @param borders the specified borders.
    */
   public void setBordersValue(Insets borders) {
      setBordersValue(borders, true);
   }

   /**
    * Set the borders value to this format.
    * @param borders the specified borders.
    */
   public void setBordersValue(Insets borders, boolean defined) {
      this.bordersValue.setDValue(borders);
      bordersValDefined = defined;
   }

   /**
    * Get the border colors.
    * @return the border colors of this format.
    */
   @Override
   public BorderColors getBorderColors() {
      return bcolorsValue.getRValue();
   }

   /**
    * Set the border colors to this format.
    * @param bcolors the specified border colors.
    */
   public void setBorderColors(BorderColors bcolors) {
      setBorderColors(bcolors, true);
   }

   /**
    * Set the border colors to this format.
    * @param bcolors the specified border colors.
    */
   public void setBorderColors(BorderColors bcolors, boolean defined) {
      this.bcolorsValue.setRValue(bcolors);
      bcolorsDefined = defined;
   }

   /**
    * Get the border colors value.
    * @return the border colors of this format.
    */
   @Override
   public BorderColors getBorderColorsValue() {
      return bcolorsValue.getDValue();
   }

   /**
    * Set the border colors to this format.
    * @param bcolors the specified border colors.
    */
   public void setBorderColorsValue(BorderColors bcolors) {
      setBorderColorsValue(bcolors, true);
   }

   /**
    * Set the border colors to this format.
    * @param bcolors the specified border colors.
    */
   public void setBorderColorsValue(BorderColors bcolors, boolean defined) {
      this.bcolorsValue.setDValue(bcolors);
      bcolorsValDefined = defined;
   }

   /**
    * Get the font.
    * @return the font of this format.
    */
   @Override
   public Font getFont() {
      return fontValue.getRValue();
   }

   /**
    * Set the font to this format.
    * @param font the specified font.
    */
   public void setFont(Font font) {
      setFont(font, true);
   }

   /**
    * Set the font to this format.
    */
   public void setFont(Font font, boolean defined) {
      // make sure it's stylefont, otherwise the equals() may not be correct
      // and the shared VSFormat instances may get mixed up
      if(font != null) {
         this.fontValue.setRValue(
            (font instanceof StyleFont) ? font : new StyleFont(font));
         fontDefined = defined;
      }
      else {
         fontValue.setRValue(null);
         // @by jasonshobe, 2015-07-21, Bug #303, if the font is null, force
         // the font defined flag to false
         fontDefined = false;
      }
   }

   /**
    * Get the font value.
    * @return the font of this format.
    */
   @Override
   public Font getFontValue() {
      return fontValue.getDValue();
   }

   /**
    * Set the font value to this format.
    */
   public void setFontValue(Font font) {
      setFontValue(font, true);
   }

   /**
    * Set the font value to this format.
    */
   public void setFontValue(Font font, boolean defined) {
      if(font != null) {
         this.fontValue.setDValue(
            (font instanceof StyleFont) ? font : new StyleFont(font));
         fontValDefined = defined;
      }
      else {
         fontValue.setDValue(null);
         // @by jasonshobe, 2015-07-21, Bug #303, if the font is null, force
         // the font value defined flag to false
         fontValDefined = false;
      }
   }

   /**
    * Get the format option.
    * @return the format option of this format.
    */
   @Override
   public String getFormat() {
      return fmtValue.getRValue() != null ? fmtValue.getRValue() + "" : null;
   }

   /**
    * Set the format option to this format.
    * @param fmt the specified format option.
    */
   public void setFormat(String fmt) {
      setFormat(fmt, true);
   }

   /**
    * Set the format option to this format.
    * @param fmt the specified format option.
    */
   public void setFormat(String fmt, boolean defined) {
      fmtValue.setRValue(fmt);
      formatDefined = defined;
   }

   /**
    * Get the format option value.
    * @return the format option of this format.
    */
   @Override
   public String getFormatValue() {
      return fmtValue.getDValue();
   }

   /**
    * Set the format option value to this format.
    * @param fmt the specified format option.
    */
   public void setFormatValue(String fmt) {
      setFormatValue(fmt, true);
   }

   /**
    * Set the format option value to this format.
    * @param fmt the specified format option.
    */
   public void setFormatValue(String fmt, boolean defined) {
      fmtValue.setDValue(fmt);
      formatValDefined = defined;
   }

   /**
    * Get the format extent (pattern or predefined extent type).
    * @return the format extent of this format.
    */
   @Override
   public String getFormatExtent() {
      return fextValue.getRValue() != null ? fextValue.getRValue() + "" : null;
   }

   /**
    * Set the format extent to this format (pattern or predefined extent type).
    * @param fext the specified format extent.
    */
   public void setFormatExtent(String fext) {
      setFormatExtent(fext, true);
   }

   /**
    * Set the format extent to this format (pattern or predefined extent type).
    * @param fext the specified format extent.
    */
   public void setFormatExtent(String fext, boolean defined) {
      this.fextValue.setRValue(fext);
      formatValDefined = defined;
   }

   /**
    * Get the format extent (pattern or predefined extent type).
    * @return the format extent of this format.
    */
   @Override
   public String getFormatExtentValue() {
      return fextValue.getDValue();
   }

   /**
    * Set the format extent to this format (pattern or predefined extent type).
    * @param fext the specified format extent.
    */
   public void setFormatExtentValue(String fext) {
      setFormatExtentValue(fext, true);
   }

    /**
    * Set the format extent to this format (pattern or predefined extent type).
    * @param fext the specified format extent.
    */
   public void setFormatExtentValue(String fext, boolean defined) {
      this.fextValue.setDValue(fext);
      formatValDefined = defined;
   }

   /**
    * Check if should wrap text.
    * @return <tt>true</tt> if should wrap text, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isWrapping() {
      return Boolean.parseBoolean(wrapValue.getRuntimeValue(true) + "");
   }

   /**
    * Set whether should wrap text.
    * @param wrap <tt>true</tt> if should wrap text, <tt>false</tt> otherwise.
    */
   public void setWrapping(boolean wrap) {
      setWrapping(wrap, true);
   }

   /**
    * Set whether should wrap text.
    * @param wrap <tt>true</tt> if should wrap text, <tt>false</tt> otherwise.
    */
   public void setWrapping(boolean wrap, boolean defined) {
      this.wrapValue.setRValue(wrap);
      wrappingDefined = defined;
   }

   /**
    * Check if should wrap text at design time.
    * @return <tt>true</tt> if should wrap text, <tt>false</tt> otherwise.
    */
   @Override
   public boolean getWrappingValue() {
      return Boolean.parseBoolean(wrapValue.getDValue());
   }

   /**
    * Set whether should wrap text.
    * @param wrap <tt>true</tt> if should wrap text, <tt>false</tt> otherwise.
    */
   public void setWrappingValue(boolean wrap) {
      setWrappingValue(wrap, true);
   }

   /**
    * Set whether should wrap text at design time.
    * @param wrap <tt>true</tt> if should wrap text, <tt>false</tt> otherwise.
    */
   public void setWrappingValue(boolean wrap, boolean defined) {
      this.wrapValue.setDValue(wrap + "");
      wrappingValDefined = defined;
   }


   /**
    * Get the cell span.
    */
   @Override
   public Dimension getSpan() {
      return span;
   }

   /**
    * Set the cell span.
    */
   public void setSpan(Dimension span) {
      this.span = span;
   }

   /**
    * Get the transparentcy.
    */
   @Override
   public int getAlpha() {
      return alphaValue.getIntValue(false, getAlphaValue());
   }

   /**
    * Set the alpha.
    */
   public void setAlpha(int trans) {
      setAlpha(trans, true);
   }

   /**
    * Set the alpha.
    */
   public void setAlpha(int trans, boolean defined) {
      // java.awt.Color keeps the alpha in the range 0-255, but client
      // side keeps the alpha in the range 0-100.java.awt.Color always
      // convert float to integer, if we save the alpha in background
      // color, sometimes alpha loses precision.
      trans = Math.max(trans, 0);
      this.alphaValue.setRValue(Math.min(trans, 100));
      transDefined = defined;
   }

   /**
    * Set the alpha.
    */
   public void setAlphaValue(int trans) {
      setAlphaValue(trans, true);
   }

   /**
    * Set the alpha.
    */
   public void setAlphaValue(int trans, boolean defined) {
      trans = Math.max(trans, 0);
      this.alphaValue.setDValue(Math.min(trans, 100) + "");
      transValDefined = defined;
   }

   /**
    * Get the transparentcy value.
    */
   @Override
   public int getAlphaValue() {
      return alphaValue.getIntValue(true, 100);
   }

   @Override
   public PresenterRef getPresenter() {
      return presenter.getRValue();
   }

   public void setPresenter(PresenterRef presenter) {
      setPresenter(presenter, true);
   }

   public void setPresenter(PresenterRef presenter, boolean defined) {
      if(presenter == null || !Catalog.getCatalog().getString("(none)").equals(presenter.getName())) {
         this.presenter.setRValue(presenter);
         pDefined = defined;
      }
   }

   @Override
   public PresenterRef getPresenterValue() {
      return presenter.getDValue();
   }

   public void setPresenterValue(PresenterRef presenter) {
      setPresenterValue(presenter, true);
   }

   public void setPresenterValue(PresenterRef presenter, boolean defined) {
      if(presenter == null || !Catalog.getCatalog().getString("(none)").equals(presenter.getName())) {
         this.presenter.setDValue(presenter);
         pValDefined = defined;
      }
   }

   /**
    * Check if design time alignment is defined.
    */
   @Override
   public boolean isAlignmentValueDefined() {
      return alignValDefined;
   }

   /**
    * Check if run time alignment is defined.
    */
   @Override
   public boolean isAlignmentDefined() {
      return alignDefined;
   }

   /**
    * Check if background color is defined.
    */
   @Override
   public boolean isBackgroundDefined() {
      return bgDefined;
   }

   /**
    * Check if GradientColor color is defined.
    */
   @Override
   public boolean isGradientColorDefined() {
      return this.gradientColorDefined;
   }

   @Override
   public boolean isGradientColorValueDefined() {
      return this.gcValDefined;
   }

   /**
    * Check if border colors value is defined.
    */
   @Override
   public boolean isBorderColorsValueDefined() {
      return bcolorsValDefined;
   }

   /**
    * Check if border colors is defined.
    */
   @Override
   public boolean isBorderColorsDefined() {
      return bcolorsDefined;
   }

   /**
    * Check if borders value is defined.
    */
   @Override
   public boolean isBordersValueDefined() {
      return bordersValDefined;
   }

   /**
    * Check if borders is defined.
    */
   @Override
   public boolean isBordersDefined() {
      return bordersDefined;
   }

   /**
    * Check if font is defined.
    */
   @Override
   public boolean isFontValueDefined() {
      return fontValDefined;
   }

   /**
    * Check if font is defined.
    */
   @Override
   public boolean isFontDefined() {
      return fontDefined;
   }

   /**
    * Check if foreground color is defined.
    */
   @Override
   public boolean isForegroundDefined() {
      return fgDefined;
   }

   /**
    * Check if text wrap is defined.
    */
   @Override
   public boolean isWrappingDefined() {
      return wrappingDefined;
   }

   /**
    * Check if text wrap value is defined.
    */
   @Override
   public boolean isWrappingValueDefined() {
      return wrappingValDefined;
   }

   /**
    * Check if alpha is defined.
    */
   @Override
   public boolean isAlphaDefined() {
      return transDefined;
   }

   /**
    * Check if alpha value is defined.
    */
   @Override
   public boolean isAlphaValueDefined() {
      return transValDefined;
   }

   @Override
   public boolean isRoundCornerDefined() {
      return roundCornerDefined;
   }

   /**
    * Check if roundCorner value is defined.
    */
   @Override
   public boolean isRoundCornerValueDefined() {
      return roundCornerValDefined;
   }

   /**
    * Check if background value is defined.
    */
   @Override
   public boolean isBackgroundValueDefined() {
      return bgValDefined;
   }

   /**
    * Check if foreground value is defined.
    */
   @Override
   public boolean isForegroundValueDefined() {
      return fgValDefined;
   }

   /**
    * Check if format or format extent is defined.
    */
   public boolean isFormatDefined() {
      return formatDefined;
   }

   /**
    * Check if format value or format extent value is defined.
    */
   public boolean isFormatValueDefined() {
      return formatValDefined;
   }

   @Override
   public boolean isPresenterValueDefined() {
      return pValDefined;
   }

   @Override
   public boolean isPresenterDefined() {
      return pDefined;
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    */
   public void writeData(DataOutputStream output) throws IOException {
      // write attributes
      output.writeInt(getAlignmentValue(true));
      output.writeInt(getAlignment());
      output.writeBoolean(getWrappingValue());
      output.writeBoolean(isWrapping());

      boolean isNull = fontValue == null || getFontValue() == null;
      output.writeBoolean(isNull);

      if(!isNull) {
         output.writeUTF(StyleFont.toString(getFontValue()));
      }

      isNull = fontValue == null || getFont() == null;
      output.writeBoolean(isNull);

      if(!isNull) {
         output.writeUTF(StyleFont.toString(getFont()));
      }

      isNull = fmtValue == null || getFormatValue() == null;
      output.writeBoolean(isNull);

      if(!isNull) {
         output.writeUTF(getFormatValue());
      }

      isNull = fmtValue == null || getFormat() == null;
      output.writeBoolean(isNull);

      if(!isNull) {
         output.writeUTF(getFormat());
      }

      isNull = fextValue == null || getFormatExtentValue() == null;
      output.writeBoolean(isNull);

      if(!isNull) {
         output.writeUTF(getFormatExtentValue());
      }

      isNull = fextValue == null || getFormatExtent() == null;
      output.writeBoolean(isNull);

      if(!isNull) {
         output.writeUTF(getFormatExtent());
      }

      output.writeBoolean(getBackground() == null);

      if(getBackground() != null) {
         // -1 in actionscript is different from 0xffffff
         output.writeInt((int) (getBackground().getRGB() & 0xFFFFFFL));
      }

      output.writeBoolean(getForeground() == null);

      if(getForeground() != null) {
         output.writeInt((int) (getForeground().getRGB() & 0xFFFFFFL));
      }

      output.writeBoolean(span == null);

      if(span != null) {
         output.writeInt(span.width);
         output.writeInt(span.height);
      }

      output.writeInt(getAlphaValue());
      output.writeInt(getAlpha());
      output.writeInt(getRoundCorner());

      // write contents
      isNull = bgval == null || bgval.getDValue() == null;
      output.writeBoolean(isNull);

      if(!isNull) {
         output.writeUTF(bgval.getDValue());
      }

      isNull = fgval == null || fgval.getDValue() == null;
      output.writeBoolean(isNull);

      if(!isNull) {
         output.writeUTF(fgval.getDValue());
      }

      isNull = bordersValue == null || getBordersValue() == null;
      output.writeBoolean(isNull);

      if(!isNull) {
         output.writeInt(getBordersValue().top);
         output.writeInt(getBordersValue().left);
         output.writeInt(getBordersValue().bottom);
         output.writeInt(getBordersValue().right);
      }

      isNull = bordersValue == null || getBorders() == null;
      output.writeBoolean(isNull);

      if(!isNull) {
         output.writeInt(getBorders().top);
         output.writeInt(getBorders().left);
         output.writeInt(getBorders().bottom);
         output.writeInt(getBorders().right);
      }

      isNull = bcolorsValue == null || getBorderColorsValue() == null;
      output.writeBoolean(isNull);

      if(!isNull) {
         output.writeUTF(getBorderColorsValue().getPattern());
      }

      isNull = bcolorsValue == null || getBorderColors() == null;
      output.writeBoolean(isNull);

      if(!isNull) {
         output.writeUTF(getBorderColors().getPattern());
      }

      output.writeBoolean(alignDefined);
      output.writeBoolean(alignValDefined);
      output.writeBoolean(bcolorsDefined);
      output.writeBoolean(bcolorsValDefined);
      output.writeBoolean(bordersDefined);
      output.writeBoolean(bordersValDefined);
      output.writeBoolean(roundCornerDefined);
      output.writeBoolean(roundCornerValDefined);
      output.writeBoolean(fontDefined);
      output.writeBoolean(fontValDefined);
      output.writeBoolean(fgDefined);
      output.writeBoolean(wrappingDefined);
      output.writeBoolean(wrappingValDefined);
      output.writeBoolean(transDefined);
      output.writeBoolean(transValDefined);
      output.writeBoolean(bgDefined);
      output.writeBoolean(fgValDefined);
      output.writeBoolean(bgValDefined);
      output.writeBoolean(formatDefined);
      output.writeBoolean(formatValDefined);

      PresenterRef p = presenter == null ? null : getPresenter();
      output.writeBoolean(p != null);

      if(p != null) {
         writePresenterData(output, p);
      }

      p = presenter == null ? null : getPresenterValue();
      output.writeBoolean(p != null);

      if(p != null) {
         writePresenterData(output, p);
      }

      output.writeBoolean(pDefined);
      output.writeBoolean(pValDefined);
   }

   private void writePresenterData(DataOutputStream output, PresenterRef p)
      throws IOException
   {
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      PrintWriter out = new PrintWriter(bout);
      p.writeXML(out);
      out.flush();
      String str = new String(bout.toByteArray());
      output.writeUTF(str);
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
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<VSFormat class=\"" + getClass().getName()+ "\" ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</VSFormat>");
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" alignValue=\"" + getAlignmentValue(true) + "\"");
      writer.print(" wrapValue=\"" + wrapValue.getDValue() + "\"");
      writer.print(" align=\"" + getAlignment() + "\"");
      writer.print(" wrap=\"" + isWrapping() + "\"");
      writer.print(" alphaValue=\"" + getAlphaValue() + "\"");
      writer.print(" alpha=\"" + getAlpha() + "\"");
      writer.print(" backgroundDefined=\"" + bgDefined + "\"");

      if(fontValue != null && fontValue.getDValue() != null) {
         writer.print(" fontValue=\"" +
            StyleFont.toString(fontValue.getDValue()) + "\"");
      }

      if(fontValue != null && fontValue.getRValue() != null) {
         writer.print(" font=\"" +
            StyleFont.toString(fontValue.getRValue()) + "\"");
      }

      if(getFormat() != null) {
         writer.print(" format=\"" + getFormat() + "\"");
      }

      if(getFormatExtent() != null) {
         writer.print(
            " formatExtent=\"" + Tool.escape(getFormatExtent()) + "\"");
      }

      if(fmtValue != null && fmtValue.getDValue() != null) {
         writer.print(" formatValue=\"" + fmtValue.getDValue() + "\"");
      }

      if(fextValue != null && fextValue.getDValue() != null) {
         writer.print(" formatExtentValue=\"" +
            Tool.escape(fextValue.getDValue()) + "\"");
      }

      if(getBackground() != null) {
         // -1 in actionscript is different from 0xffffff
         writer.print(" bgColor=\"" + (getBackground().getRGB() & 0xFFFFFFL) +
                      "\"");
      }

      if(getForeground() != null) {
         writer.print(" fgColor=\"" + (getForeground().getRGB() & 0xFFFFFFL) +
                      "\"");
      }

      if(span != null) {
         writer.print(" colSpan=\"" + span.width + "\" rowSpan=\"" +
                      span.height + "\"");
      }

      writer.print(" hint=\"" + getPropDefined() + "\"");
      writer.print(" roundCorner=\"" + getRoundCorner() + "\"");
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   protected void parseAttributes(Element elem) {
      String prop = VSUtil.getAttributeStr(elem, "align", null);
      this.alignValue.setDValue(prop);

      prop = VSUtil.getAttributeStr(elem, "wrap", "false");
      this.wrapValue.setDValue(prop);

      prop = VSUtil.getAttributeStr(elem, "font", null);
      this.fontValue.setDValue(StyleFont.decode(prop));

      prop = VSUtil.getAttributeStr(elem, "format", null);
      this.fmtValue.setDValue(prop);

      prop = VSUtil.getAttributeStr(elem, "formatExtent", null);
      this.fextValue.setDValue(prop);

      prop = VSUtil.getAttributeStr(elem, "alpha", "100");
      alphaValue.setDValue(prop);

      prop = VSUtil.getAttributeStr(elem, "roundCorner", "0");
      roundCornerValue.setDValue(prop);

      String colspan = Tool.getAttribute(elem, "colSpan");
      String rowspan = Tool.getAttribute(elem, "rowSpan");

      if(colspan != null && rowspan != null) {
         span = new Dimension(Integer.parseInt(colspan),
                              Integer.parseInt(rowspan));
      }

      if(Tool.getAttribute(elem, "hint") != null) {
         setPropDefined(Integer.parseInt(Tool.getAttribute(elem, "hint")));
      }
      else {
         //@todo this code is from 2010 and causes some problems, remove in the future

         // process bc
         alignValDefined = "true".equals(getAttributeStr("align", elem));
         bgDefined = "true".equals(
            Tool.getAttribute(elem, "backgroundDefined"));
         fgDefined = "true".equals(
            Tool.getAttribute(elem, "foregroundDefined"));
         bcolorsValDefined = "true".equals(
            getAttributeStr("borderColors", elem));
         bordersValDefined = "true".equals(getAttributeStr("borders", elem));
         fontValDefined = "true".equals(getAttributeStr("font", elem));
         wrappingValDefined = "true".equals(getAttributeStr("wrapping", elem));
         transValDefined = "true".equals(getAttributeStr("alpha", elem));
         formatValDefined = "true".equals(getAttributeStr("format", elem));
         bgValDefined = "true".equals(
            Tool.getAttribute(elem, "backgroundValueDefined"));
         fgValDefined = "true".equals(
            Tool.getAttribute(elem, "foregroundValueDefined"));
         formatDefined = "true".equals(
            Tool.getAttribute(elem, "formatDefined"));
         alignDefined = "true".equals(Tool.getAttribute(elem, "alignDefined"));
         bcolorsDefined =
            "true".equals(Tool.getAttribute(elem, "borderColorsDefined"));
         bordersDefined = "true".equals(
            Tool.getAttribute(elem, "bordersDefined"));
         fontDefined = "true".equals(Tool.getAttribute(elem, "fontDefined"));
         wrappingDefined =
            "true".equals(Tool.getAttribute(elem, "wrappingDefined"));
         transDefined = "true".equals(Tool.getAttribute(elem, "alphaDefined"));
      }

      // @by jasonshobe, 2015-05-21, Bug #303, font value flag is set, but font
      // value is not actually set so add this sanity check, this is probably
      // originally caused by passing null to setFontValue() sometime before the
      // format was originally written
      if(fontValDefined && getFontValue() == null) {
         fontValDefined = false;
      }
   }

   /**
    * Get attribute form the element with the specify name.
    */
   private String getAttributeStr(String str, Element elem) {
      String val;

      if((val = Tool.getAttribute(elem, str + "ValDefined")) == null) {
         val = Tool.getAttribute(elem, str + "Defined");
      }

      return val;
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   protected void writeContents(PrintWriter writer) {
      if(bgval != null && bgval.getDValue() != null) {
         writer.print("<background>");
         writer.print("<![CDATA[" + bgval.getDValue() + "]]>");
         writer.println("</background>");
      }

      if(gradientColorVals != null && gradientColorVals.getDValue() != null) {
         GradientColor.ColorStop[] colorStop = gradientColorVals.getDValue().getColors();
         writer.println("<gradientColor direction=\"" + gradientColorVals.getDValue().getDirection()
            + "\" apply=\"" + gradientColorVals.getDValue().isApply()
            + "\" angle=\"" + gradientColorVals.getDValue().getAngle()
            +"\">");

         if(colorStop != null) {
            for(int i = 0; i < colorStop.length; i++) {
               writer.print("<colorStop offset=\"" + colorStop[i].getOffset() + "\" color=\"" +
                     colorStop[i].getColor() + "\">");
               writer.println("</colorStop>");
            }
         }

         writer.println("</gradientColor>");
      }

      if(fgval != null && fgval.getDValue() != null) {
         writer.print("<foreground>");
         writer.print("<![CDATA[" + fgval.getDValue() + "]]>");
         writer.println("</foreground>");
      }

      if(bordersValue != null && bordersValue.getDValue() != null) {
         writer.print("<borderValue top=\"" +
                      bordersValue.getDValue().top + "\"" +
                      " bottom=\"" + bordersValue.getDValue().bottom + "\"" +
                      " left=\"" + bordersValue.getDValue().left + "\"" +
                      " right=\"" + bordersValue.getDValue().right + "\"/>");
      }

      if(bordersValue != null && getBorders() != null) {
         writer.print("<border top=\"" + getBorders().top + "\"" +
                      " bottom=\"" + getBorders().bottom + "\"" +
                      " left=\"" + getBorders().left + "\"" +
                      " right=\"" + getBorders().right + "\"/>");
      }

      if(bcolorsValue != null && getBorderColorsValue() != null) {
         writer.print("<borderColorValue>");
         writer.print("<![CDATA[" +
            getBorderColorsValue().getPattern() + "]]>");
         writer.println("</borderColorValue>");
      }

      if(bcolorsValue != null && bcolorsValue.getRValue() != null) {
         writer.print("<borderColor>");
         writer.print("<![CDATA[" +
            bcolorsValue.getRValue().getPattern() + "]]>");
         writer.println("</borderColor>");
      }

      PresenterRef p = getPresenter();

      if(p != null) {
         writer.print("<presenter>");
         p.writeXML(writer);
         writer.println("</presenter>");
      }

      p = getPresenterValue();

      if(p != null) {
         writer.print("<presenterValue>");
         p.writeXML(writer);
         writer.println("</presenterValue>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      bgval.setDValue(Tool.getChildValueByTagName(elem, "background"));
      fgval.setDValue(Tool.getChildValueByTagName(elem, "foreground"));
      Element gradientColorNode = Tool.getChildNodeByTagName(elem, "gradientColor");

      if(gradientColorNode != null) {
         GradientColor gradientColor = new GradientColor();
         gradientColor.setDirection(Tool.getAttribute(gradientColorNode, "direction"));
         gradientColor.setAngle(Integer.parseInt(Tool.getAttribute(gradientColorNode, "angle")));
         gradientColor.setApply(Boolean.valueOf(Tool.getAttribute(gradientColorNode, "apply")));
         NodeList colorList = Tool.getChildNodesByTagName(gradientColorNode, "colorStop");
         GradientColor.ColorStop[] colorArray = new GradientColor.ColorStop[colorList.getLength()];

         for(int i = 0; i < colorList.getLength(); i++) {
            Element color = (Element) colorList.item(i);
            GradientColor.ColorStop gradulaColor = new GradientColor.ColorStop();
            gradulaColor.setColor(Tool.getAttribute(color, "color"));
            gradulaColor.setOffset(Integer.parseInt(Tool.getAttribute(color, "offset")));
            colorArray[i] = gradulaColor;
         }

         gradientColor.setColors(colorArray);
         setGradientColorValue(gradientColor);
      }

      Element bnode = Tool.getChildNodeByTagName(elem, "borderValue");
      bnode = bnode == null ?
         Tool.getChildNodeByTagName(elem, "border") : bnode;

      if(bnode != null) {
         int top = Integer.parseInt(Tool.getAttribute(bnode, "top"));
         int bottom = Integer.parseInt(Tool.getAttribute(bnode, "bottom"));
         int left = Integer.parseInt(Tool.getAttribute(bnode, "left"));
         int right = Integer.parseInt(Tool.getAttribute(bnode, "right"));
         bordersValue.setDValue(new Insets(top, left, bottom, right));
      }

      Element bcnode = Tool.getChildNodeByTagName(elem, "borderColorValue");
      bcnode = bcnode == null ?
         Tool.getChildNodeByTagName(elem, "borderColor") : bcnode;

      if(bcnode != null) {
         bcolorsValue.setDValue(new BorderColors());
         getBorderColorsValue().parsePattern(Tool.getValue(bcnode));
      }

      Element pnode = Tool.getChildNodeByTagName(elem, "presenterValue");
      pnode = pnode == null ?
         Tool.getChildNodeByTagName(elem, "presenter") : pnode;

      if(pnode != null) {
         PresenterRef p = new PresenterRef();
         p.parseXML(Tool.getChildNodeByTagName(pnode, "presenter"));
         presenter.setDValue(p);
      }
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      parseAttributes(elem);
      parseContents(elem);
   }

   /**
    * Get the string representation.
    * @return the string representation of this object.
    */
   public String toString() {
      return "VSFormat: [" + getAlignmentValue() + ", " + getAlignment() +
         ", " + getBackground() + ", " + bgval +
         ", " + getForeground() + ", " + fgval + ", " +
         bordersValue + ", " + getBorders() + ", " +
         getBorderColorsValue() + ", " + getBorderColors() + ", " +
         fontValue + ", " + getFont() + ", " +
         fmtValue + ", " + getFormat() + ", " +
         getWrappingValue() + ", " + isWrapping() + ", " +
         span + ", " + fextValue + ", " + getFormatExtent() +
         ", " + getAlphaValue() + ", " + getAlpha() + ", " + getRoundCorner() + "]";
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         VSFormat info = (VSFormat) super.clone();

         if(bordersValue != null) {
            info.bordersValue = bordersValue.clone();
         }

         if(bcolorsValue != null) {
            info.bcolorsValue = bcolorsValue.clone();
         }

         if(fgval != null) {
            info.fgval = (DynamicValue) fgval.clone();
         }

         if(bgval != null) {
            info.bgval = (DynamicValue) bgval.clone();
         }

         if(fmtValue != null) {
            info.fmtValue = (DynamicValue) fmtValue.clone();
         }

         if(fextValue != null) {
            info.fextValue = (DynamicValue) fextValue.clone();
         }

         if(fontValue != null) {
            info.fontValue = fontValue.clone();
         }

         if(alignValue != null) {
            info.alignValue = (DynamicValue2) alignValue.clone();
         }

         if(presenter != null) {
            info.presenter = presenter.clone();
         }

         if(alphaValue != null) {
            info.alphaValue = (DynamicValue2) alphaValue.clone();
         }

         if(roundCornerValue != null) {
            info.roundCornerValue = (DynamicValue2) roundCornerValue.clone();
         }

         if(gradientColorVals != null) {
            info.gradientColorVals = gradientColorVals.clone();
         }

         if(wrapValue != null) {
            info.wrapValue = (DynamicValue) wrapValue.clone();
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone VSFormat", ex);
      }

      return null;
   }

   /**
    * Check if equals another objects.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof VSFormat)) {
         return false;
      }

      VSFormat vfmt = (VSFormat) obj;

      return Tool.equals(bgval, vfmt.bgval) &&
             Tool.equals(fgval, vfmt.fgval) &&
             Tool.equals(bordersValue, vfmt.bordersValue) &&
             Tool.equals(getBorders(), vfmt.getBorders()) &&
             Tool.equals(bcolorsValue, vfmt.bcolorsValue) &&
             Tool.equals(getBorderColors(), vfmt.getBorderColors()) &&
             Tool.equals(fontValue, vfmt.fontValue) &&
             Tool.equals(getFont(), vfmt.getFont()) &&
             Tool.equals(fmtValue, vfmt.fmtValue) &&
             Tool.equals(fextValue, vfmt.fextValue) &&
             Tool.equals(getFormat(), vfmt.getFormat()) &&
             Tool.equals(getFormatExtent(), vfmt.getFormatExtent()) &&
             Tool.equals(wrapValue, vfmt.wrapValue) &&
             Tool.equals(span, vfmt.span) &&
             Tool.equals(bg, vfmt.bg) &&
             Tool.equals(fg, vfmt.fg) &&
             Tool.equals(alphaValue, vfmt.alphaValue) &&
             Tool.equals(presenter, vfmt.presenter) &&
             Tool.equals(gradientColorVals, vfmt.gradientColorVals) &&
             Tool.equals(roundCornerValue, vfmt.roundCornerValue) &&
             Tool.equals(alignValue, vfmt.alignValue) &&
             Tool.equals(alignValue.getRValue(), vfmt.alignValue.getRValue()) &&
             (getPropDefined() == vfmt.getPropDefined());
   }

   /**
    * Calculate the hashcode of the format spec.
    */
   public int hashCode() {
      int hash = 0;

      if(alignValue != null) {
         hash += alignValue.hashCode();
      }

      if(wrapValue != null) {
         hash += wrapValue.hashCode();
      }

      if(bordersValue != null) {
         hash += bordersValue.hashCode();
      }

      if(bcolorsValue != null) {
         hash += bcolorsValue.hashCode();
      }

      if(fontValue != null) {
         hash += fontValue.hashCode();
      }

      if(fmtValue != null) {
         hash += fmtValue.hashCode();
      }

      if(fextValue != null) {
         hash += fextValue.hashCode();
      }

      if(fg != null) {
         hash += fg.hashCode();
      }

      if(bg != null) {
         hash += bg.hashCode();
      }

      if(presenter != null) {
         hash += presenter.hashCode();
      }

      if(gradientColorVals != null) {
         hash += gradientColorVals.hashCode();
      }

      if(roundCornerValue != null) {
         hash += roundCornerValue.hashCode();
      }

      return hash;
   }

   /**
    * Get the dynamic values.
    * @return the dynamic values.
    */
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = new ArrayList<>();
      list.add(bgval);
      list.add(fgval);
      list.add(fextValue);
      return list;
   }

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   public void renameDepended(String oname, String nname, Viewsheet vs) {
      VSUtil.renameDynamicValueDepended(oname, nname, bgval, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, fgval, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, fextValue, vs);
   }

   /**
    * Shrink the viewsheet format object for runtime usage.
    */
   public void shrink() {
      if(bgval != null) {
         bg = getBackground();
         bgval = null;
      }

      if(fgval != null) {
         fg = getForeground();
         fgval = null;
      }

      /* shouldn't wipe out runtime value (which is done in setDValue)
      fmtValue.setDValue(null);
      fextValue.setDValue(null);
      presenter.setDValue(null);
       */
   }

   /**
    * Check whether format is defined.
    * @return true if this format has been defined, otherwise false.
    */
   public boolean isDefined() {
      return getPropDefined() != NONE_DEFINED;
   }

   /**
    * Reset runtime values.
    */
   public void resetRuntimeValues() {
      bgval.setRValue(null);
      fgval.setRValue(null);
      alignValue.setRValue(null);
      bordersValue.setRValue(null);
      bcolorsValue.setRValue(null);
      fontValue.setRValue(null);
      fmtValue.setRValue(null);
      fextValue.setRValue(null);
      wrapValue.setRValue(null);
      alphaValue.setRValue(null);
      presenter.setRValue(null);
      gradientColorVals.setRValue(null);
      roundCornerValue.setRValue(null);

      bg = null;
      fg = null;

      alignDefined = false;
      fgDefined = false;
      bgDefined = false;
      bcolorsDefined = false;
      bordersDefined = false;
      fontDefined = false;
      wrappingDefined = false;
      transDefined = false;
      formatDefined = false;
      pDefined = false;
      gradientColorDefined = false;
      roundCornerDefined = false;
   }

   /**
    * Get the properties hint value.
    */
   private int getPropDefined() {
      int hint = NONE_DEFINED;

      if(alignDefined) {
         hint = hint | ALIGN_DEFINED;
      }

      if(alignValDefined) {
         hint = hint | ALIGN_VAL_DEFINED;
      }

      if(bgDefined) {
         hint = hint | BACKGROUND_DEFINED;
      }

      if(bgValDefined) {
         hint = hint | BACKGROUND_VAL_DEFINED;
      }

      if(fgDefined) {
         hint = hint | FOREGROUND_DEFINED;
      }

      if(fgValDefined) {
         hint = hint | FOREGROUND_VAL_DEFINED;
      }

      if(bcolorsDefined) {
         hint = hint | BORDER_COLOR_DEFINED;
      }

      if(bcolorsValDefined) {
         hint = hint | BORDER_COLOR_VAL_DEFINED;
      }

      if(bordersDefined) {
         hint = hint | BORDER_DEFINED;
      }

      if(bordersValDefined) {
         hint = hint | BORDER_VAL_DEFINED;
      }

      if(fontDefined) {
         hint = hint | FONT_DEFINED;
      }

      if(fontValDefined) {
         hint = hint | FONT_VAL_DEFINED;
      }

      if(wrappingDefined) {
         hint = hint | WRAPPING_DEFINED;
      }

      if(wrappingValDefined) {
         hint = hint | WRAPPING_VAL_DEFINED;
      }

      if(transDefined) {
         hint = hint | TRANS_DEFINED;
      }

      if(transValDefined) {
         hint = hint | TRANS_VAL_DEFINED;
      }

      if(formatDefined) {
         hint = hint | FORMAT_DEFINED;
      }

      if(formatValDefined) {
         hint = hint | FORMAT_VAL_DEFINED;
      }

      if(pDefined) {
         hint = hint | PRESENTER_DEFINED;
      }

      if(pValDefined) {
         hint = hint | PRESENTER_VAL_DEFINED;
      }

      if(roundCornerDefined) {
         hint = hint | ROUND_CORNER_DEFINED;
      }

      if(roundCornerValDefined) {
         hint = hint | ROUND_CORNER_VAL_DEFINED;
      }

      return hint;
   }

   /**
    * Set properties defined value.
    */
   private void setPropDefined(int hint) {
      if(hint == NONE_DEFINED) {
         return;
      }

      if((hint & ALIGN_VAL_DEFINED) == ALIGN_VAL_DEFINED) {
         alignValDefined = true;
      }

      if((hint & BACKGROUND_VAL_DEFINED) == BACKGROUND_VAL_DEFINED) {
         bgValDefined = true;
      }

      if((hint & FOREGROUND_VAL_DEFINED) == FOREGROUND_VAL_DEFINED) {
         fgValDefined = true;
      }

      if((hint & BORDER_COLOR_VAL_DEFINED) == BORDER_COLOR_VAL_DEFINED) {
         bcolorsValDefined = true;
      }

      if((hint & BORDER_VAL_DEFINED) == BORDER_VAL_DEFINED) {
         bordersValDefined = true;
      }

      if((hint & FONT_VAL_DEFINED) == FONT_VAL_DEFINED) {
         fontValDefined = true;
      }

      if((hint & WRAPPING_VAL_DEFINED) == WRAPPING_VAL_DEFINED) {
         wrappingValDefined = true;
      }

      if((hint & TRANS_VAL_DEFINED) == TRANS_VAL_DEFINED) {
         transValDefined = true;
      }

      if((hint & FORMAT_DEFINED) == FORMAT_DEFINED) {
         formatDefined = true;
      }

      if((hint & FORMAT_VAL_DEFINED) == FORMAT_VAL_DEFINED) {
         formatValDefined = true;
      }

      if((hint & PRESENTER_VAL_DEFINED) == PRESENTER_VAL_DEFINED) {
         pValDefined = true;
      }

      if((hint & ROUND_CORNER_VAL_DEFINED) == ROUND_CORNER_VAL_DEFINED) {
         roundCornerValDefined = true;
      }

      alignDefined = false;
      fgDefined = false;
      bgDefined = false;
      bcolorsDefined = false;
      bordersDefined = false;
      fontDefined = false;
      wrappingDefined = false;
      transDefined = false;
      formatDefined = false;
      pDefined = false;
      roundCornerDefined = false;
   }

   /**
    * Set if background color is defined.
    */
   public void setBackgroundDefined(boolean isbg) {
      bgDefined = isbg;
   }

   /**
    * Set if GradientColor color is defined.
    */
   public void setGradientColorDefined(boolean isGradientColor) {
      this.gradientColorDefined = isGradientColor;
   }

   /**
    * Set if font color is defined.
    */
   public void setFontDefined(boolean isdefine) {
      fontDefined = isdefine;
   }

   /**
    * Set if foreground color is defined.
    */
   public void setForegroundDefined(boolean isdefine) {
      fgDefined = isdefine;
   }

   /**
    * Set if transfer color is defined.
    */
   public void setTransDefined(boolean isdefine) {
      transDefined = isdefine;
   }

   /**
    * Set if transfer color is defined.
    */
   public void setAlignmentDefined(boolean isdefine) {
      alignDefined = isdefine;
   }

   /**
    * Set if transfer color is defined.
    */
   public void setBorderColorDefined(boolean isdefine) {
      bcolorsDefined = isdefine;
   }

   /**
    * Set if transfer color is defined.
    */
   public void setBorderDefined(boolean isdefine) {
      bordersDefined = isdefine;
   }

   /**
    * Set if transfer color is defined.
    */
   public void setWrappingDefined(boolean isdefine) {
      wrappingDefined = isdefine;
   }

   /**
    * Set if transfer color is defined.
    */
   public void setFormatDefined(boolean isdefine) {
      formatDefined = isdefine;
   }

   /**
    * Set if transfer color is defined.
    */
   public void setPDefined(boolean isdefine) {
      pDefined = isdefine;
   }

   /**
    * Set if round corner is defined.
    */
   public void setRoundCornerDefined(boolean isdefine) {
      roundCornerDefined = isdefine;
   }

   /**
    * Set the runtime rectangle's round corner.
    */
   public void setRoundCorner(int corner) {
      setRoundCorner(corner, true);
   }

   public void setRoundCorner(int corner, boolean defined) {
      roundCornerValue.setRValue(corner);
      roundCornerDefined = defined;
   }

   /**
    * Set the design time rectangle's round corner value.
    */
   public void setRoundCornerValue(int corner) {
      setRoundCornerValue(corner, true);
   }

   public void setRoundCornerValue(int corner, boolean defined) {
      roundCornerValue.setDValue(corner + "");
      roundCornerValDefined = defined;
   }

   /**
    * Get the runtime rectangle's round corner.
    */
   public int getRoundCorner() {
      return roundCornerValue.getIntValue(false, 0);
   }

   /**
    * Get the design time rectangle's round corner.
    */
   public int getRoundCornerValue() {
      return roundCornerValue.getIntValue(true, 0);
   }

   private static final int ALIGN = (StyleConstants.H_LEFT | StyleConstants.V_TOP);
   private DynamicValue bgval;
   private DynamicValue fgval;
   private ClazzHolder<GradientColor> gradientColorVals = new ClazzHolder<>();
   // default align set to -1, when get align, if the align is -1, we convert
   // it to default align(ALIGN), but persistent still use real value, flex
   // is same, so for some cases, if the default align is not top left, cannot
   // set to left align, like crosstab, see bug1337706120427 for detail
   private DynamicValue2 alignValue = new DynamicValue2("-1", XSchema.BOOLEAN);
   private ClazzHolder<Insets> bordersValue = new ClazzHolder<>();
   private ClazzHolder<BorderColors> bcolorsValue = new ClazzHolder<>();
   private DynamicValue2 roundCornerValue = new DynamicValue2("0", XSchema.INTEGER);
   private ClazzHolder<Font> fontValue = new ClazzHolder<>();
   private DynamicValue fmtValue = new DynamicValue();
   private DynamicValue fextValue = new DynamicValue();
   private DynamicValue wrapValue = new DynamicValue("false", XSchema.BOOLEAN);
   private DynamicValue2 alphaValue = new DynamicValue2("100", XSchema.INTEGER);
   private ClazzHolder<PresenterRef> presenter = new ClazzHolder<>();
   private Dimension span;
   // runtime
   private Color bg;
   private Color fg = Color.BLACK;

   private boolean alignDefined = false;
   private boolean bgDefined = false;
   private boolean gradientColorDefined = false;
   private boolean bcolorsDefined = false;
   private boolean bordersDefined = false;
   private boolean roundCornerDefined = false;
   private boolean fontDefined = false;
   private boolean fgDefined = false;
   private boolean wrappingDefined = false;
   private boolean transDefined = false;
   private boolean formatDefined = false;
   private boolean pDefined = false;

   private boolean alignValDefined = false;
   private boolean fgValDefined = false;
   private boolean bgValDefined = false;
   private boolean gcValDefined = false;
   private boolean bcolorsValDefined = false;
   private boolean roundCornerValDefined = false;
   private boolean bordersValDefined = false;
   private boolean fontValDefined = false;
   private boolean wrappingValDefined = false;
   private boolean transValDefined = false;
   private boolean formatValDefined = false;
   private boolean pValDefined = false;

   private static final Logger LOG = LoggerFactory.getLogger(VSFormat.class);
}
