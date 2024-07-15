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

import com.fasterxml.jackson.annotation.JsonInclude;
import inetsoft.report.StyleConstants;
import inetsoft.report.StyleFont;
import inetsoft.util.Tool;

import java.awt.*;
import java.util.Objects;

public class FontInfo {
   public FontInfo() {
   }

   public FontInfo(Font font) {
      fixFontInfo(font);
   }

   public void fixFontInfo(Font font) {
      if(font == null) {
         return;
      }

      if(StyleFont.isDefaultFont(font)) {
         setFontFamily(StyleFont.DEFAULT_FONT_FAMILY);
      }
      else {
         //fix feature#5659
         //to fix user defined font name, replace font family with font name
         setFontFamily(font.getName());
      }

      setFontStyle(getFontStyle(font.getStyle()));
      setFontUnderline(getFontUnderline(font.getStyle()));
      setFontStrikethrough(getFontStrikethrough(font.getStyle()));
      setFontWeight(getFontWeight(font.getStyle()));
      setFontSize(font.getSize() + "");
      setSmallCaps(getSmallCaps(font.getStyle()));
      setAllCaps(getAllCaps(font.getStyle()));
      setSubScript(getSubScript(font.getStyle()));
      setSupScript(getSupScript(font.getStyle()));
      setShadow(getShadow(font.getStyle()));
   }

   public String getFontFamily() {
      return fontFamily;
   }

   public void setFontFamily(String fontFamily) {
      this.fontFamily = fontFamily;
   }

   public String getFontStyle() {
      return fontStyle;
   }

   public void setFontStyle(String fontStyle) {
      this.fontStyle = fontStyle;
   }

   public String getFontWeight() {
      return fontWeight;
   }

   public void setFontWeight(String fontWeight) {
      this.fontWeight = fontWeight;
   }

   public String getFontSize() {
      return fontSize;
   }

   public void setFontSize(String fontSize) {
      this.fontSize = fontSize;
   }

   public String getFontUnderline() {
      return fontUnderline;
   }

   public void setFontUnderline(String fontUnderline) {
      this.fontUnderline = fontUnderline;
   }

   public String getFontStrikethrough() {
      return fontStrikethrough;
   }

   public void setFontStrikethrough(String fontStrikethrough) {
      this.fontStrikethrough = fontStrikethrough;
   }

   @JsonInclude(JsonInclude.Include.NON_EMPTY)
   public String getSmallCaps() {
      return this.smallCaps;
   }

   public void setSmallCaps(String smallCaps) {
      this.smallCaps = smallCaps;
   }

   @JsonInclude(JsonInclude.Include.NON_EMPTY)
   public String getAllCaps() {
      return allCaps;
   }

   public void setAllCaps(String allCaps) {
      this.allCaps = allCaps;
   }

   @JsonInclude(JsonInclude.Include.NON_EMPTY)
   public String getSubScript() {
      return subScript;
   }

   public void setSubScript(String subScript) {
      this.subScript = subScript;
   }

   @JsonInclude(JsonInclude.Include.NON_EMPTY)
   public String getSupScript() {
      return supScript;
   }

   public void setSupScript(String supScript) {
      this.supScript = supScript;
   }

   @JsonInclude(JsonInclude.Include.NON_EMPTY)
   public String getShadow() {
      return shadow;
   }

   public void setShadow(String shadow) {
      this.shadow = shadow;
   }

   public String getFontStyle(int style) {
      return (style & Font.ITALIC) != 0 ? "italic" : "normal";
   }

   public String getFontWeight(int style) {
      return (style & Font.BOLD) != 0 ? "bold" : "normal";
   }

   public String getFontUnderline(int style) {
      return (style & StyleFont.UNDERLINE) != 0 ? "underline" : "normal";
   }

   public String getFontStrikethrough(int style) {
      return (style & StyleFont.STRIKETHROUGH) != 0 ? "strikethrough" : "normal";
   }

   public String getSmallCaps(int style) {
      return (style & StyleFont.SMALLCAPS) != 0 ? "smallCaps" : "";
   }

   public String getSubScript(int style) {
      return (style & StyleFont.SUBSCRIPT) != 0 ? "sub" : "";
   }

   public String getSupScript(int style) {
      return (style & StyleFont.SUPERSCRIPT) != 0 ? "sup" : "";
   }

   public String getShadow(int style) {
      return (style & StyleFont.SHADOW) != 0 ? "shadow" : "";
   }

   public String getAllCaps(int style) {
      return (style & StyleFont.ALLCAPS) != 0 ? "allCaps" : "";
   }

   public Font toFont() {
      if(fontFamily == null) {
         return null;
      }

      int style = Font.PLAIN;

      if(fontWeight != null && fontWeight.toLowerCase().contains("bold")) {
         style |= Font.BOLD;
      }

      if(fontStyle != null && fontStyle.contains("italic")) {
         style |= Font.ITALIC;
      }

      int underline = 0;
      int strikeline = 0;

      if(fontUnderline != null && fontUnderline.contains("underline")) {
         style |= StyleFont.UNDERLINE;
         underline = StyleConstants.THIN_LINE;
      }

      if(fontStrikethrough != null && fontStrikethrough.contains("strikethrough")) {
         style |= StyleFont.STRIKETHROUGH;
         strikeline = StyleConstants.THIN_LINE;
      }

      int size = fontSize == null ? 11 : Integer.parseInt(fontSize);

      return new StyleFont(fontFamily, style, size, underline, strikeline);
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof FontInfo)) {
         return false;
      }

      FontInfo info = (FontInfo) obj;

      return Tool.equals(fontFamily, info.fontFamily) &&
         Tool.equals(fontStyle, info.fontStyle) &&
         Tool.equals(fontSize, info.fontSize) &&
         Tool.equals(fontUnderline, info.fontUnderline) &&
         Tool.equals(fontStrikethrough, info.fontStrikethrough) &&
         Tool.equals(fontWeight, info.fontWeight);
   }

   @Override
   public int hashCode() {
      return Objects.hash(fontFamily, fontStyle, fontSize, fontUnderline, fontStrikethrough,
                          fontWeight, smallCaps, allCaps, subScript, supScript, shadow);
   }

   public String toString() {
      return "FontInfo[" + fontFamily + "," + fontStyle + "," + fontSize + "," +
         fontUnderline + "," + fontStrikethrough + "," + fontWeight + "]";
   }

   private String fontFamily;
   private String fontStyle;
   private String fontSize;
   private String fontUnderline;
   private String fontStrikethrough;
   private String fontWeight;
   private String smallCaps;
   private String allCaps;
   private String subScript;
   private String supScript;
   private String shadow;
}
