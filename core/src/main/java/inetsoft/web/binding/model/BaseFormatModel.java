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
package inetsoft.web.binding.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.awt.*;
import java.util.Objects;

public abstract class BaseFormatModel {
   public Border getBorder() {
      return border;
   }

   public void setBorder(Border border) {
      this.border = border;
   }

   public Wrapping getWrapping() {
      return wrapping;
   }

   public void setWrapping(Wrapping wrapping) {
      this.wrapping = wrapping;
   }

   public double getTop() {
      return top;
   }

   public void setTop(double top) {
      this.top = top;
   }

   public double getLeft() {
      return left;
   }

   public void setLeft(double left) {
      this.left = left;
   }

   public double getWidth() {
      return width;
   }

   public void setWidth(double width) {
      this.width = width;
   }

   public double getHeight() {
      return height;
   }

   public void setHeight(double height) {
      this.height = height;
   }

   public int getzIndex() {
      return zIndex;
   }

   public void setzIndex(int zIndex) {
      this.zIndex = zIndex;
   }

   public void setPositions(Point pos, Dimension size) {
      this.setPositions(pos.getX(), pos.getY(), size.getWidth(), size.getHeight());
   }

   public void setPositions(double left, double top, double width, double height) {
      this.left = left;
      this.top = top;
      this.width = width;
      this.height = height;
   }

   public void setSize(Dimension size) {
      width = size.getWidth();
      height = size.getHeight();
   }

   @JsonInclude(JsonInclude.Include.NON_EMPTY)
   public String getPosition() {
      return position;
   }

   public void setPosition(String position) {
      this.position = position;
   }

   public String toString() {
      return "{" + toString0() + " " +
         "top:" + top + " " +
         "left:" + left + " " +
         "height:" + height + " " +
         "width:" + width + " " +
         "zIndex:" + zIndex + " " +
         "border:" + border + " " +
         "wrapping:" + wrapping + " " +
         "position:" + position + " " +
         "} ";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }
      if(o == null || getClass() != o.getClass()) {
         return false;
      }
      BaseFormatModel that = (BaseFormatModel) o;
      return Double.compare(that.top, top) == 0 &&
         Double.compare(that.left, left) == 0 &&
         Double.compare(that.height, height) == 0 &&
         Double.compare(that.width, width) == 0 &&
         zIndex == that.zIndex &&
         Objects.equals(border, that.border) &&
         Objects.equals(wrapping, that.wrapping) &&
         Objects.equals(position, that.position);
   }

   @Override
   public int hashCode() {
      return Objects.hash(border, wrapping, top, left, height, width, zIndex, position);
   }

   public abstract String toString0();

   private Border border = new Border(); // contains css border values
   private Wrapping wrapping = new Wrapping(); // contains css wrap values
   private double top = 0;
   private double left = 0;
   private double height;
   private double width;
   private int zIndex;
   private String position;

   public static final class Border {
      @JsonInclude(JsonInclude.Include.NON_EMPTY)
      public String getBottom() {
         return bottom;
      }

      @JsonInclude(JsonInclude.Include.NON_EMPTY)
      public String getTop() {
         return top;
      }

      @JsonInclude(JsonInclude.Include.NON_EMPTY)
      public String getLeft() {
         return left;
      }

      @JsonInclude(JsonInclude.Include.NON_EMPTY)
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

      public String toString() {
         return "{bottom:" + bottom + " " +
            "top:" + top + " " +
            "left:" + left + " " +
            "right:" + right + "} ";
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }
         if(o == null || getClass() != o.getClass()) {
            return false;
         }
         Border border = (Border) o;
         return Objects.equals(bottom, border.bottom) &&
            Objects.equals(top, border.top) &&
            Objects.equals(left, border.left) &&
            Objects.equals(right, border.right);
      }

      @Override
      public int hashCode() {
         return Objects.hash(bottom, top, left, right);
      }

      private String bottom;
      private String top;
      private String left;
      private String right;
   }

   public static final class Wrapping {
      public Wrapping() {
      }

      public Wrapping(boolean wrap) {
         if(wrap) {
            whiteSpace = "normal";
            wordWrap = "break-word";
            overflow = "hidden";
         }
         else {
            whiteSpace = "nowrap";
         }
      }

      @JsonInclude(JsonInclude.Include.NON_EMPTY)
      public String getWhiteSpace() {
         return whiteSpace;
      }

      @JsonInclude(JsonInclude.Include.NON_EMPTY)
      public String getWordWrap() {
         return wordWrap;
      }

      @JsonInclude(JsonInclude.Include.NON_EMPTY)
      public String getOverflow() {
         return overflow;
      }

      public void setWhiteSpace(String whiteSpace) {
         this.whiteSpace = whiteSpace;
      }

      public void setWordWrap(String wordWrap) {
         this.wordWrap = wordWrap;
      }

      public void setOverflow(String overflow) {
         this.overflow = overflow;
      }

      public String toString() {
         return "{whiteSpace:" + whiteSpace + " " +
            "wordWrap:" + wordWrap + " " +
            "overflow:" + overflow + "} ";
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }
         if(o == null || getClass() != o.getClass()) {
            return false;
         }
         Wrapping wrapping = (Wrapping) o;
         return Objects.equals(whiteSpace, wrapping.whiteSpace) &&
            Objects.equals(wordWrap, wrapping.wordWrap) &&
            Objects.equals(overflow, wrapping.overflow);
      }

      @Override
      public int hashCode() {
         return Objects.hash(whiteSpace, wordWrap, overflow);
      }

      private String whiteSpace;
      private String wordWrap;
      private String overflow;
   }
}
