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
package inetsoft.web.adhoc.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import inetsoft.graph.GraphConstants;
import inetsoft.graph.internal.GTool;
import inetsoft.report.StyleConstants;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.XConstants;
import inetsoft.util.Tool;

@JsonTypeInfo(
   use = JsonTypeInfo.Id.CLASS,
   property = "type")
public class FormatInfoModel {
   public FormatInfoModel() {
   }

   public FormatInfoModel(TableFormat tformat) {
      setBorderBottomStyle("solid");
      setBorderRightStyle("solid");
      setBorderTopStyle("solid");
      setBorderLeftStyle("solid");
      setBorderBottomWidth("1px");
      setBorderRightWidth("1px");
      setBorderTopWidth("1px");
      setBorderLeftWidth("1px");
      setBorderBottomColor("#000000");
      setBorderRightColor("#000000");
      setBorderTopColor("#000000");
      setBorderLeftColor("#000000");

      setBorderTopCSS("solid 1px #000000");
      setBorderLeftCSS("solid 1px #000000");
      setBorderBottomCSS("solid 1px #000000");
      setBorderRightCSS("solid 1px #000000");

      if(tformat == null) {
         return;
      }

      setColor(Tool.toString(tformat.foreground));
      setBackgroundColor(Tool.toString(tformat.background));

      if(tformat.font != null) {
         font = new FontInfo(tformat.font);
      }

      if(tformat.alignment != null) {
         align = new AlignmentInfo(tformat.alignment.intValue());
      }

      setFormat(tformat.format);
      setFormatSpec(tformat.format_spec);
      fixDateSpec(tformat.format, tformat.format_spec);
   }

   public void fixDateSpec(String format, String format_spec) {
      if(!TableFormat.DATE_FORMAT.equals(format)) {
         return;
      }

      boolean custom = !"FULL".equals(format_spec)
         && !"LONG".equals(format_spec) && !"MEDIUM".equals(format_spec)
         && !"SHORT".equals(format_spec);

      if(custom) {
         setDateSpec("Custom");
      }
      else {
         setDateSpec(format_spec);
         setFormatSpec(null);
      }
   }

   public String getColor() {
      return color;
   }

   public void setColor(String color) {
      this.color = color;
   }

   public String getFormat() {
      return format;
   }

   public void setFormat(String fmt) {
      if(XConstants.DURATION_FORMAT_PAD_NON.equals(fmt)) {
         fmt = XConstants.DURATION_FORMAT;
         durationPadZeros = false;
      }

      this.format = fmt;
   }

   public String getDateSpec() {
      return dateSpec;
   }

   public void setDateSpec(String fmt) {
      this.dateSpec = fmt;
   }

   public String getFormatSpec() {
      return formatSpec;
   }

   public void setFormatSpec(String fmt) {
      this.formatSpec = fmt;
   }

   public String getBackgroundColor() {
      return backgroundColor;
   }

   public void setBackgroundColor(String backgroundColor) {
      this.backgroundColor = backgroundColor;
   }

   public String getBorderTopStyle() {
      return borderTopStyle;
   }

   public void setBorderTopStyle(String borderTopStyle) {
      this.borderTopStyle = borderTopStyle;
   }

   public String getBorderTopColor() {
      return borderTopColor;
   }

   public void setBorderTopColor(String borderTopColor) {
      this.borderTopColor = borderTopColor;
   }

   public String getBorderTopWidth() {
      return borderTopWidth;
   }

   public void setBorderTopWidth(String borderTopWidth) {
      this.borderTopWidth = borderTopWidth;
   }

   public String getBorderLeftStyle() {
      return borderLeftStyle;
   }

   public void setBorderLeftStyle(String borderLeftStyle) {
      this.borderLeftStyle = borderLeftStyle;
   }

   public String getBorderLeftColor() {
      return borderLeftColor;
   }

   public void setBorderLeftColor(String borderLeftColor) {
      this.borderLeftColor = borderLeftColor;
   }

   public String getBorderLeftWidth() {
      return borderLeftWidth;
   }

   public void setBorderLeftWidth(String borderLeftWidth) {
      this.borderLeftWidth = borderLeftWidth;
   }

   public String getBorderBottomStyle() {
      return borderBottomStyle;
   }

   public void setBorderBottomStyle(String borderBottomStyle) {
      this.borderBottomStyle = borderBottomStyle;
   }

   public String getBorderBottomColor() {
      return borderBottomColor;
   }

   public void setBorderBottomColor(String borderBottomColor) {
      this.borderBottomColor = borderBottomColor;
   }

   public String getBorderBottomWidth() {
      return borderBottomWidth;
   }

   public void setBorderBottomWidth(String borderBottomWidth) {
      this.borderBottomWidth = borderBottomWidth;
   }

   public String getBorderRightStyle() {
      return borderRightStyle;
   }

   public void setBorderRightStyle(String borderRightStyle) {
      this.borderRightStyle = borderRightStyle;
   }

   public String getBorderRightColor() {
      return borderRightColor;
   }

   public void setBorderRightColor(String borderRightColor) {
      this.borderRightColor = borderRightColor;
   }

   public String getBorderRightWidth() {
      return borderRightWidth;
   }

   public void setBorderRightWidth(String borderRightWidth) {
      this.borderRightWidth = borderRightWidth;
   }

   private String getBorderStyle(int border) {
      if((border & StyleConstants.DOUBLE_MASK) != 0) {
         return "double";
      }

      if((border & StyleConstants.DASH_MASK) != 0) {
         float dlen = (border & GraphConstants.DASH_MASK) >> 4;
         return (dlen > 1) ? "dashed" : "dotted";
      }

      return "solid";
   }

   private String getBorderWidth(int border) {
      return ((int) GTool.getLineWidth(border)) + "px";
   }

   public String getBorderColor() {
      return borderColor;
   }

   public void setBorderColor(String color) {
      this.borderColor = color;
   }

   public FontInfo getFont() {
      return font;
   }

   public void setFont(FontInfo font) {
      this.font = font;
   }

   public AlignmentInfo getAlign() {
      return align;
   }

   public void setAlign(AlignmentInfo align) {
      this.align = align;
   }

   public boolean isHAlignmentEnabled() {
      return halignmentEnabled;
   }

   public void setHAlignmentEnabled(boolean alignEnabled) {
      this.halignmentEnabled = alignEnabled;
   }

   public boolean isVAlignmentEnabled() {
      return valignmentEnabled;
   }

   public void setVAlignmentEnabled(boolean alignEnabled) {
      this.valignmentEnabled = alignEnabled;
   }

   public boolean isFormatEnabled() {
      return formatEnabled;
   }

   public void setFormatEnabled(boolean formatEnabled) {
      this.formatEnabled = formatEnabled;
   }

   public String[] getDecimalFmts() {
      return decimalFmts;
   }

   public void setDecimalFmts(String[] decimalFmts) {
      this.decimalFmts = decimalFmts;
   }

   public String getBorderTopCSS() {
      return borderTopCSS;
   }

   public void setBorderTopCSS(String borderTopCSS) {
      this.borderTopCSS = borderTopCSS;
   }

   public String getBorderLeftCSS() {
      return borderLeftCSS;
   }

   public void setBorderLeftCSS(String borderLeftCSS) {
      this.borderLeftCSS = borderLeftCSS;
   }

   public String getBorderBottomCSS() {
      return borderBottomCSS;
   }

   public void setBorderBottomCSS(String borderBottomCSS) {
      this.borderBottomCSS = borderBottomCSS;
   }

   public String getBorderRightCSS() {
      return borderRightCSS;
   }

   public void setBorderRightCSS(String borderRightCSS) {
      this.borderRightCSS = borderRightCSS;
   }

   public boolean isDurationPadZeros() {
      return durationPadZeros;
   }

   public void setDurationPadZeros(boolean durationPadZeros) {
      this.durationPadZeros = durationPadZeros;
   }

   /**
    * Get the real duration format.
    * @param format format from format model.
    * @param durationPadZeros whether pad with zeros.
    *
    * @return format if the format is not duration else real duration format.
    */
   public static String getDurationFormat(String format, boolean durationPadZeros) {
      if(XConstants.DURATION_FORMAT.equals(format)) {
         return durationPadZeros ? XConstants.DURATION_FORMAT : XConstants.DURATION_FORMAT_PAD_NON;
      }

      return format;
   }

   private String color = "#000000";
   private String backgroundColor;
   private String borderColor;
   private String borderTopStyle;
   private String borderTopColor;
   private String borderTopWidth;
   private String borderLeftStyle;
   private String borderLeftColor;
   private String borderLeftWidth;
   private String borderBottomStyle;
   private String borderBottomColor;
   private String borderBottomWidth;
   private String borderRightStyle;
   private String borderRightColor;
   private String borderRightWidth;

   private String borderTopCSS;
   private String borderLeftCSS;
   private String borderBottomCSS;
   private String borderRightCSS;
   private String format;
   private String formatSpec;
   private String dateSpec;
   private FontInfo font = null;
   private AlignmentInfo align = null;
   private boolean halignmentEnabled = true;
   private boolean valignmentEnabled = true;
   private boolean formatEnabled = true;
   private String[] decimalFmts;
   private boolean durationPadZeros = true;
}
