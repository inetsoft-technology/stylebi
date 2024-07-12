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
package inetsoft.report.internal.table;

import inetsoft.report.internal.Cacheable;

import java.awt.*;
import java.io.Serializable;

/**
 * Table cell attributes.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class TableCellInfo implements Cacheable, Serializable {
   public int hashCode() {
      int hash = rowBorder + colBorder + align;

      if(foreground != null) {
         hash += foreground.getRGB();
      }

      if(background != null) {
         hash += background.getRGB();
      }

      if(rowBorderC != null) {
         hash += rowBorderC.getRGB();
      }

      if(colBorderC != null) {
         hash += colBorderC.getRGB();
      }

      if(font != null) {
         hash += font.getName().hashCode();
         hash += font.getStyle();
      }

      if(span != null) {
         hash += span.hashCode();
      }

      if(insets != null) {
         hash += insets.hashCode();
      }

      return hash;
   }

   public boolean equals(Object obj) {
      if(obj instanceof TableCellInfo) {
         TableCellInfo cell = (TableCellInfo) obj;

         return rowBorder == cell.rowBorder && colBorder == cell.colBorder &&
            align == cell.align && wrap == cell.wrap &&
            (foreground == cell.foreground ||
            foreground != null && cell.foreground != null &&
            foreground.equals(cell.foreground)) &&
            (background == cell.background ||
            background != null && cell.background != null &&
            background.equals(cell.background)) &&
            (rowBorderC == cell.rowBorderC ||
            rowBorderC != null && cell.rowBorderC != null &&
            rowBorderC.equals(cell.rowBorderC)) &&
            (colBorderC == cell.colBorderC ||
            colBorderC != null && cell.colBorderC != null &&
            colBorderC.equals(cell.colBorderC)) &&
            (font == cell.font ||
            font != null && cell.font != null && font.equals(cell.font)) &&
            (span == cell.span ||
            span != null && cell.span != null && span.equals(cell.span)) &&
            (insets == cell.insets ||
            insets != null && cell.insets != null &&
            insets.equals(cell.insets));
      }

      return false;
   }

   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
      }

      return this;
   }

   public Color foreground;
   public Color background;
   public int rowBorder;
   public int colBorder;
   public Color rowBorderC;
   public Color colBorderC;
   public int align;
   public Font font;
   public boolean wrap;
   public Dimension span;
   public Insets insets;
}
