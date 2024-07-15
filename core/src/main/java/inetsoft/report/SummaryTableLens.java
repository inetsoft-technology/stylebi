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
package inetsoft.report;

import inetsoft.report.lens.AttributeTableLens;
import inetsoft.report.painter.PresenterPainter;

import java.awt.*;
import java.text.Format;

/**
 * This table lens allows a summary row to be added to each table segment.
 * The getSummary() method must be implemented to calculate the summary
 * for each column. The method is called at the end of each page to
 * calculate the summary for the table segment on that page.
 * This table lens must be the top-level table lens. It
 * can not be wrapped around by a table style or any other table lens.
 * If a table style is to be applied to a table, it must be applied
 * to the base table of the SummaryTableLens, and NOT applied to the
 * SummaryTableLens object.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public abstract class SummaryTableLens extends AttributeTableLens {
   /**
    * Calculate the summary value for the specified column and rows.
    * This method must be implemented to supply the actual logic to
    * summarize a column.
    * @param col column to summarize.
    * @param row starting row number.
    * @param nrows number of rows to summarize.
    * @return summary value or null if no summary is defined for the column.
    */
   public abstract Object getSummary(int col, int row, int nrows);

   /**
    * The setTable() method must be called before this can be used.
    */
   public SummaryTableLens() {
   }

   /**
    * Create a SummaryTableLens that add summarization information (rows)
    * to the original table.
    * @param table table to summarize.
    */
   public SummaryTableLens(TableLens table) {
      super(table);
   }

   /**
    * Apply appropriate format and presenter if any is register for this
    * column.
    * @param obj object value to render.
    * @param c column number.
    */
   public Object render(Object obj, int c) {
      if(obj != null) {
         Presenter p = getPresenter(c);

         if(p != null && p.isPresenterOf(obj.getClass())) {
            return new PresenterPainter(obj, p);
         }

         Format fm = getFormat(c);

         if(fm != null) {
            try {
               return fm.format(obj);
            }
            catch(Exception e) {
            }
         }
      }

      return obj;
   }

   /**
    * Return the summary row height. It defaults to 20.
    * @return row height in pixels.
    */
   public int getSummaryHeight() {
      return 20;
   }

   /**
    * Get the summary row border color. It defaults to the same value as
    * the last row in the original table.
    * @param col column number.
    * @return row border color.
    */
   public Color getSummaryRowBorderColor(int col) {
      return table.getRowBorderColor(table.getRowCount() - 1, col);
   }

   /**
    * Get the summary column border color. It defaults to the same value as
    * the last row in the original table.
    * @param col column number.
    * @return col border color.
    */
   public Color getSummaryColBorderColor(int col) {
      return table.getColBorderColor(table.getRowCount() - 1, col);
   }

   /**
    * Get the summary row border style. It defaults to the same value as
    * the last row in the original table.
    * @param col column number.
    * @return row border style.
    */
   public int getSummaryRowBorder(int col) {
      return table.getRowBorder(table.getRowCount() - 1, col);
   }

   /**
    * Get the summary column border style. It defaults to the same value as
    * the last row in the original table.
    * @param col column number.
    * @return column border style.
    */
   public int getSummaryColBorder(int col) {
      return table.getColBorder(table.getRowCount() - 1, col);
   }

   /**
    * Return the cell gap space.
    * It defaults to the same value as the last row in the original table.
    * @param c column number.
    * @return cell gap space.
    */
   public Insets getSummaryInsets(int c) {
      return table.getInsets(table.getRowCount() - 1, c);
   }

   /**
    * Return the spanning setting for the cell. If the specified cell
    * is not a spanning cell, it returns null. Otherwise it returns
    * a Dimension object with Dimension.width equals to the number
    * of columns and Dimension.height equals to the number of rows
    * of the spanning cell. It defaults to no span.
    * @param c column number.
    * @return span cell dimension.
    */
   public Dimension getSummarySpan(int c) {
      return null;
   }

   /**
    * Return the per cell alignment.
    * It defaults to the same value as the last row in the original table.
    * @param c column number.
    * @return cell alignment.
    */
   public int getSummaryAlignment(int c) {
      return table.getAlignment(table.getRowCount() - 1, c);
   }

   /**
    * Return the per cell font. Return null to use default font.
    * It defaults to the same value as the last row in the original table.
    * @param c column number.
    * @return font for the specified cell.
    */
   public Font getSummaryFont(int c) {
      return table.getFont(table.getRowCount() - 1, c);
   }

   /**
    * Return the per cell line wrap mode. If the line wrap mode is true,
    * lines are wrapped when the text can not fit on one line. Otherwise
    * the wrapping is never done and any overflow text will be truncated.
    * @param c column number.
    * @return true if line wrapping should be done.
    */
   public boolean isSummaryLineWrap(int c) {
      return table.isLineWrap(table.getRowCount() - 1, c);
   }

   /**
    * Return the per cell foreground color. Return null to use default
    * color.
    * It defaults to the same value as the last row in the original table.
    * @param c column number.
    * @return foreground color for the specified cell.
    */
   public Color getSummaryForeground(int c) {
      return table.getForeground(table.getRowCount() - 1, c);
   }

   /**
    * Return the per cell background color. Return null to use default
    * color.
    * It defaults to the same value as the last row in the original table.
    * @param c column number.
    * @return background color for the specified cell.
    */
   public Color getSummaryBackground(int c) {
      return table.getBackground(table.getRowCount() - 1, c);
   }
}

