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
package inetsoft.report.filter.style;

import inetsoft.report.TableFilter;
import inetsoft.report.TableLens;
import inetsoft.report.filter.GroupedTable;
import inetsoft.report.style.TableStyle;

import java.awt.*;
import java.text.Format;

/**
 * GroupStyle is a table style for grouped table filters. It allows
 * controlling of attributes specifically for grouped tables, such
 * as the group header, summarization, etc.. This is the base class
 * for all other group table styles. This class can be used directly
 * to create a table style, and set the display attributes for each
 * type of rows.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class GroupStyle extends TableStyle {
   /**
    * Create an empty style. The setTable() method must be called before
    * it can be used.
    */
   public GroupStyle() {
   }

   /**
    * Create a style to decorate the specified table.
    * @param table table lens.
    */
   public GroupStyle(GroupedTable table) {
      super(table);
      gtable = table;
   }

   /**
    * Set the table to be decorated.
    * @param table the table to decorate.
    */
   @Override
   public void setTable(TableLens table) {
      if(!(table instanceof GroupedTable) || !(table instanceof TableFilter)) {
         throw new IllegalArgumentException("Only GroupedTable filter can be " +
            " added to a group style");
      }

      super.setTable(table);
      gtable = (GroupedTable) table;
   }

   /**
    * Get the user specified header foreground color.
    * @return foreground color or null if not set.
    */
   public Color getHeaderForeground() {
      return headerFG;
   }

   /**
    * Set the header foreground color.
    * @param fg foreground color or null to unset.
    */
   public void setHeaderForeground(Color fg) {
      headerFG = fg;
   }

   /**
    * Get the user specified header background color.
    * @return background color or null if not set.
    */
   public Color getHeaderBackground() {
      return headerBG;
   }

   /**
    * Set the header background color.
    * @param bg background color or null to unset.
    */
   public void setHeaderBackground(Color bg) {
      headerBG = bg;
   }

   /**
    * Get the user specified header font.
    * @return font or null if not set.
    */
   public Font getHeaderFont() {
      return headerFont;
   }

   /**
    * Set the header font.
    * @param font font or null to unset.
    */
   public void setHeaderFont(Font font) {
      headerFont = font;
   }

   /**
    * Get the user specified header insets.
    * @return insets or null if not set.
    */
   public Insets getHeaderInsets() {
      return headerInsets;
   }

   /**
    * Set the header insets.
    * @param insets insets or null to unset.
    */
   public void setHeaderInsets(Insets insets) {
      headerInsets = insets;
   }

   /**
    * Get the user specified header alignment.
    * @return alignment or null if not set.
    */
   public Integer getHeaderAlignment() {
      return headerAlign;
   }

   /**
    * Set the header alignment.
    * @param align alignment or null to unset.
    */
   public void setHeaderAlignment(Integer align) {
      headerAlign = align;
   }

   /**
    * Get the user specified summary foreground color.
    * @return foreground color or null if not set.
    */
   public Color getSummaryForeground() {
      return sumFG;
   }

   /**
    * Set the summary foreground color.
    * @param fg foreground color or null to unset.
    */
   public void setSummaryForeground(Color fg) {
      sumFG = fg;
   }

   /**
    * Get the user specified summary background color.
    * @return background color or null if not set.
    */
   public Color getSummaryBackground() {
      return sumBG;
   }

   /**
    * Set the summary background color.
    * @param bg background color or null to unset.
    */
   public void setSummaryBackground(Color bg) {
      sumBG = bg;
   }

   /**
    * Get the user specified summary font.
    * @return font or null if not set.
    */
   public Font getSummaryFont() {
      return sumFont;
   }

   /**
    * Set the summary font.
    * @param font font or null to unset.
    */
   public void setSummaryFont(Font font) {
      sumFont = font;
   }

   /**
    * Get the user specified summary insets.
    * @return insets or null if not set.
    */
   public Insets getSummaryInsets() {
      return sumInsets;
   }

   /**
    * Set the summary insets.
    * @param insets insets or null to unset.
    */
   public void setSummaryInsets(Insets insets) {
      sumInsets = insets;
   }

   /**
    * Get the user specified summary alignment.
    * @return alignment or null if not set.
    */
   public Integer getSummaryAlignment() {
      return sumAlign;
   }

   /**
    * Set the summary alignment.
    * @param align alignment or null to unset.
    */
   public void setSummaryAlignment(Integer align) {
      sumAlign = align;
   }

   /**
    * Get the summary number format.
    */
   public Format getSummaryFormat() {
      return sumFormat;
   }

   /**
    * Set the summary number format.
    */
   public void setSummaryFormat(Format fmt) {
      sumFormat = fmt;
   }

   /**
    * Get the user specified grand total foreground color.
    * @return foreground color or null if not set.
    */
   public Color getGrandForeground() {
      return grandFG;
   }

   /**
    * Set the grand total foreground color.
    * @param fg foreground color or null to unset.
    */
   public void setGrandForeground(Color fg) {
      grandFG = fg;
   }

   /**
    * Get the user specified grand total background color.
    * @return background color or null if not set.
    */
   public Color getGrandBackground() {
      return grandBG;
   }

   /**
    * Set the grand total background color.
    * @param bg background color or null to unset.
    */
   public void setGrandBackground(Color bg) {
      grandBG = bg;
   }

   /**
    * Get the user specified grand total font.
    * @return font or null if not set.
    */
   public Font getGrandFont() {
      return grandFont;
   }

   /**
    * Set the grand total font.
    * @param font font or null to unset.
    */
   public void setGrandFont(Font font) {
      grandFont = font;
   }

   /**
    * Get the user specified grand total insets.
    * @return insets or null if not set.
    */
   public Insets getGrandInsets() {
      return grandInsets;
   }

   /**
    * Set the grand total insets.
    * @param insets insets or null to unset.
    */
   public void setGrandInsets(Insets insets) {
      grandInsets = insets;
   }

   /**
    * Get the user specified grand total alignment.
    * @return alignment or null if not set.
    */
   public Integer getGrandAlignment() {
      return grandAlign;
   }

   /**
    * Set the grand total alignment.
    * @param align alignment or null to unset.
    */
   public void setGrandAlignment(Integer align) {
      grandAlign = align;
   }

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c column number.
    * @return cell gap space.
    */
   @Override
   public Insets getInsets(int r, int c) {
      if(grandInsets != null && r == gtable.getRowCount() - 1) {
         return grandInsets;
      }
      else if(sumInsets != null && gtable.isSummaryRow(r)) {
         return sumInsets;
      }
      else if(headerInsets != null && gtable.isGroupHeaderRow(r)) {
         return headerInsets;
      }

      return super.getInsets(r, c);
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      if(grandAlign != null && r == gtable.getRowCount() - 1) {
         return grandAlign.intValue();
      }
      else if(sumAlign != null && gtable.isSummaryRow(r)) {
         return sumAlign.intValue();
      }
      else if(headerAlign != null && gtable.isGroupHeaderRow(r)) {
         return headerAlign.intValue();
      }

      return super.getAlignment(r, c);
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      if(grandFont != null && r == gtable.getRowCount() - 1) {
         return grandFont;
      }
      else if(sumFont != null && gtable.isSummaryRow(r)) {
         return sumFont;
      }
      else if(headerFont != null && gtable.isGroupHeaderCell(r, c)) {
         return headerFont;
      }

      return super.getFont(r, c);
   }

   /**
    * Return the per cell foreground color. Return null to use default
    * color.
    * @param r row number.
    * @param c column number.
    * @return foreground color for the specified cell.
    */
   @Override
   public Color getForeground(int r, int c) {
      if(grandFG != null && r == gtable.getRowCount() - 1) {
         return grandFG;
      }
      else if(sumFG != null && gtable.isSummaryRow(r)) {
         return sumFG;
      }
      else if(headerFG != null && gtable.isGroupHeaderRow(r)) {
         return headerFG;
      }

      return super.getForeground(r, c);
   }

   /**
    * Return the per cell background color. Return null to use default
    * color.
    * @param r row number.
    * @param c column number.
    * @return background color for the specified cell.
    */
   @Override
   public Color getBackground(int r, int c) {
      if(grandBG != null && r == gtable.getRowCount() - 1) {
         return grandBG;
      }
      else if(sumBG != null && gtable.isSummaryRow(r)) {
         return sumBG;
      }
      else if(headerBG != null && gtable.isGroupHeaderRow(r)) {
         return headerBG;
      }

      return super.getBackground(r, c);
   }

   @Override
   public Object getObject(int r, int c) {
      Object val = super.getObject(r, c);

      try {
         if(sumFormat != null && val != null && gtable.isSummaryRow(r)) {
            return sumFormat.format(val);
         }
      }
      catch(IllegalArgumentException e) {
      }

      return val;
   }

   // sum attributes
   Color headerFG;
   Color headerBG;
   Font headerFont;
   Insets headerInsets;
   Integer headerAlign;
   // summary attributes
   Color sumFG;
   Color sumBG;
   Font sumFont;
   Insets sumInsets;
   Integer sumAlign;
   Format sumFormat;
   // grand summary attributes
   Color grandFG;
   Color grandBG;
   Font grandFont;
   Insets grandInsets;
   Integer grandAlign;
   // grouped base table
   protected GroupedTable gtable;
}

