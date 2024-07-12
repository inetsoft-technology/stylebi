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
package inetsoft.web.adhoc.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.util.Tool;
import inetsoft.web.binding.model.BaseFormatModel;

import java.util.Objects;

public class ReportFormatModel extends BaseFormatModel {
   /**
    * Constructor.
    */
   public ReportFormatModel() {
   }

    /**
    * Constructor.
    */
   public ReportFormatModel(TableFormat tFormat) {
      initBorder();

      if(tFormat == null) {
         return;
      }

      if(tFormat.font != null) {
         font = new FontInfo(tFormat.font);
      }

      if(tFormat.alignment != null) {
         align = new AlignmentInfo(tFormat.alignment.intValue());
      }

      setColor(Tool.toString(tFormat.foreground));
      setBackgroundColor(Tool.toString(tFormat.background));
      this.format = tFormat.format;
      setFormatSpec(tFormat.format_spec);
      setSuppressIfZero(tFormat.suppressIfZero);
      setSuppressIfDuplicate(tFormat.suppressIfDuplicate);
      setLineWrap(tFormat.linewrap);
   }

   private void initBorder() {
      getBorder().setBottom("1px solid #000000");// width style color
      getBorder().setTop("1px solid #000000");
      getBorder().setRight("1px solid #000000");
      getBorder().setLeft("1px solid #000000");
   }

   /**
    * Get suppress if zero.
    * @return true if  suppressIfZero.
    */
   @JsonInclude(JsonInclude.Include.NON_DEFAULT)
   public boolean isSuppressIfZero() {
      return suppressIfZero;
   }

   /**
    * Set suppress if zero.
    * @param suppressIfZero the suppress if zero.
    */
   public void setSuppressIfZero(boolean suppressIfZero) {
      this.suppressIfZero = suppressIfZero;
   }

   /**
    * Get suppress if duplicate.
    * @return suppressIfZero.
    */
   @JsonInclude(JsonInclude.Include.NON_DEFAULT)
   public boolean isSuppressIfDuplicate() {
      return suppressIfDuplicate;
   }

   /**
    * Set suppress if duplicate.
    * @param suppressIfDuplicate the suppress if duplicate.
    */
   public void setSuppressIfDuplicate(boolean suppressIfDuplicate) {
      this.suppressIfDuplicate = suppressIfDuplicate;
   }

   /**
    * Get line wrap.
    * @return line wrap.
    */
   @JsonInclude(JsonInclude.Include.NON_DEFAULT)
   public boolean isLineWrap() {
      return linewrap;
   }

   /**
    * Set line wrap.
    * @param linewrap the line wrap.
    */
   public void setLineWrap(boolean linewrap) {
      this.linewrap = linewrap;
   }

   @JsonInclude(JsonInclude.Include.NON_EMPTY)
   public String getColor() {
      return color;
   }

   public void setColor(String color) {
      this.color = color;
   }

   @JsonInclude(JsonInclude.Include.NON_EMPTY)
   public String getBackgroundColor() {
      return backgroundColor;
   }

   public void setBackgroundColor(String backgroundColor) {
      this.backgroundColor = backgroundColor;
   }

   @JsonInclude(JsonInclude.Include.NON_EMPTY)
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

   @JsonInclude(JsonInclude.Include.NON_EMPTY)
   public String getFormat() {
      return this.format;
   }

   public void setFormat(String format) {
      this.format = format;
   }

   public int getRotate() {
      return this.rotate;
   }

   public void setRotate(int rotate) {
      this.rotate = rotate;
   }

   @JsonInclude(JsonInclude.Include.NON_EMPTY)
   public String getFormatSpec() {
      return formatSpec;
   }

   public void setFormatSpec(String formatSpec) {
      this.formatSpec = formatSpec;
   }

   public Padding getPadding() {
      return this.padding;
   }

   public void setPadding(Padding padding) {
      this.padding = padding;
   }

   public int getHlBorder() {
      return this.hlBorder;
   }

   public void setHlBorder(int hlBorder) {
      this.hlBorder = hlBorder;
   }

   @Override
   public String toString0() {
      return "";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      ReportFormatModel that = (ReportFormatModel) o;
      return hlBorder == that.hlBorder &&
         suppressIfZero == that.suppressIfZero &&
         suppressIfDuplicate == that.suppressIfDuplicate &&
         linewrap == that.linewrap &&
         Objects.equals(color, that.color) &&
         Objects.equals(backgroundColor, that.backgroundColor) &&
         Objects.equals(format, that.format) &&
         Objects.equals(formatSpec, that.formatSpec) &&
         Objects.equals(font, that.font) &&
         Objects.equals(align, that.align) &&
         Objects.equals(padding, that.padding);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), color, backgroundColor, format, formatSpec, font, align,
                          padding, hlBorder, suppressIfZero, suppressIfDuplicate, linewrap);
   }

   private String color;
   private String backgroundColor;
   private String format;
   private String formatSpec;
   private FontInfo font;
   private int rotate = 0;
   private AlignmentInfo align = new AlignmentInfo();
   private Padding padding = new Padding();
   private int hlBorder;

   private boolean suppressIfZero = false;
   private boolean suppressIfDuplicate = false;
   private boolean linewrap = false;

   public static final class Padding {
      public Padding() {
         bottom = "0px";
         top = "0px";
         left = "0px";
         right = "0px";
      }

      @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = ZeroPaddingFilter.class)
      public String getBottom() {
         return bottom;
      }

      @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = ZeroPaddingFilter.class)
      public String getTop() {
         return top;
      }

      @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = ZeroPaddingFilter.class)
      public String getLeft() {
         return left;
      }

      @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = ZeroPaddingFilter.class)
      public String getRight() {
         return right;
      }

      public void setBottom(String bottom) {
         this.bottom = bottom;
      }

      public void setTop(String top) {
         this.top = top;
      }

      public void setLeft(String left) {
         this.left = left;
      }

      public void setRight(String right) {
         this.right = right;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         Padding padding = (Padding) o;
         return Objects.equals(bottom, padding.bottom) &&
            Objects.equals(top, padding.top) &&
            Objects.equals(left, padding.left) &&
            Objects.equals(right, padding.right);
      }

      @Override
      public int hashCode() {
         return Objects.hash(bottom, top, left, right);
      }

      public String toString() {
         return "{bottom:" + bottom + " " +
            "top:" + top + " " +
            "left:" + left + " " +
            "right:" + right + "} ";
      }

      private String bottom;
      private String top;
      private String left;
      private String right;
   }

   /**
    * Filters out 0px-valued padding values from being serialized by Jackson.
    */
   private static class ZeroPaddingFilter {
      @Override
      public boolean equals(Object obj) {
         if(!(obj instanceof String)) {
            return true;
         }

         return "0px".equals(obj);
      }
   }
}
