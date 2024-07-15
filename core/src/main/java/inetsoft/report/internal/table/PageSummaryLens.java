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
package inetsoft.report.internal.table;

import inetsoft.report.SummaryTableLens;
import inetsoft.report.TableDataDescriptor;
import inetsoft.report.lens.AbstractTableLens;

import java.awt.*;

/**
 * Class to paint a actual table region.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class PageSummaryLens extends AbstractTableLens {
   public PageSummaryLens(SummaryTableLens summary, Rectangle region) {
      this.summary = summary;
      this.region = region;
      summaryH = summary.getSummaryHeight();
      summaryRow = new SummaryCellInfo[summary.getColCount() + 1];

      for(int i = 0; i < summaryRow.length; i++) {
         summaryRow[i] = new SummaryCellInfo(summary, i - 1, region.y,
            region.height);
      }
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      return summary.getDescriptor();
   }

   /**
    * Check if there are more rows. The row index is the row that will be
    * accessed. This method must block until the row is available, or
    * return false if the row does not exist in the table. This method is
    * used to iterate through the table, and allow partial table to be
    * accessed in report processing.
    * @param row row number. If EOT is passed in, this method should wait
    * until the table is fully loaded.
    * @return true if the row exists, or false if no more rows.
    */
   @Override
   public boolean moreRows(int row) {
      return isSummaryRow(row) ? true : summary.moreRows(rowN(row));
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      int rows = summary.getRowCount();

      return (rows < 0) ? rows : rows + 1;
   }

   /**
    * Return the number of columns in the table. The number of columns
    * includes the header columns.
    * @return number of columns in table.
    */
   @Override
   public int getColCount() {
      return summary.getColCount();
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.
    */
   @Override
   public int getHeaderRowCount() {
      return summary.getHeaderRowCount();
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   @Override
   public int getHeaderColCount() {
      return summary.getHeaderColCount();
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as trailer rows.
    * @return number of header rows.
    */
   @Override
   public int getTrailerRowCount() {
      return 1;
   }

   /**
    * Return the number of columns on the right of the table to be
    * treated as trailer columns.
    */
   @Override
   public int getTrailerColCount() {
      return summary.getTrailerColCount();
   }

   /**
    * Get the current row heights setting. The meaning of row heights
    * depends on the table layout policy setting. If the row height
    * is to be calculated by the ReportSheet based on the content,
    * return -1.
    * @return row height.
    */
   @Override
   public int getRowHeight(int row) {
      return isSummaryRow(row) ? summaryH : summary.getRowHeight(rowN(row));
   }

   /**
    * Get the current column width setting. The meaning of column widths
    * depends on the table layout policy setting. If the column width
    * is to be calculated by the ReportSheet based on the content,
    * return -1. A special value, StyleConstants.REMAINDER, can be returned
    * by this method to indicate that width of this column should be
    * calculated based on the remaining space after all other columns'
    * widths are satisfied. If there are more than one column that return
    * REMAINDER as their widths, the remaining space is distributed
    * evenly among these columns.
    * @return column width.
    */
   @Override
   public int getColWidth(int col) {
      return summary.getColWidth(col);
   }

   /**
    * Get the current column content type.
    * @param col column number.
    * @return column type.
    */
   @Override
   public Class getColType(int col) {
      return summary.getColType(col);
   }

   /**
    * Return the color for drawing the row border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getRowBorderColor(int r, int c) {
      if(isSummaryRow(r)) {
         return summaryRow[c + 1].rowBorderC;
      }

      return summary.getRowBorderColor(rowN(r), c);
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      if(isSummaryRow(r)) {
         return summaryRow[c + 1].colBorderC;
      }

      return summary.getColBorderColor(rowN(r), c);
   }

   /**
    * Return the style for bottom border of the specified cell. The flag
    * must be one of the style options defined in the StyleConstants
    * class. If the row number is -1, it's checking the outside ruling
    * on the top.
    * @param r row number.
    * @param c column number.
    * @return ruling flag.
    */
   @Override
   public int getRowBorder(int r, int c) {
      if(isSummaryRow(r)) {
         return summaryRow[c + 1].rowBorder;
      }

      return summary.getRowBorder(rowN(r), c);
   }

   /**
    * Return the style for right border of the specified row. The flag
    * must be one of the style options defined in the StyleConstants
    * class. If the column number is -1, it's checking the outside ruling
    * on the left.
    * @param r row number.
    * @param c column number.
    * @return ruling flag.
    */
   @Override
   public int getColBorder(int r, int c) {
      if(isSummaryRow(r)) {
         return summaryRow[c + 1].colBorder;
      }

      return summary.getColBorder(rowN(r), c);
   }

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c column number.
    * @return cell gap space.
    */
   @Override
   public Insets getInsets(int r, int c) {
      if(isSummaryRow(r)) {
         return summaryRow[c + 1].insets;
      }

      return summary.getInsets(rowN(r), c);
   }

   /**
    * Return the spanning setting for the cell. If the specified cell
    * is not a spanning cell, it returns null. Otherwise it returns
    * a Dimension object with Dimension.width equals to the number
    * of columns and Dimension.height equals to the number of rows
    * of the spanning cell.
    * @param r row number.
    * @param c column number.
    * @return span cell dimension.
    */
   @Override
   public Dimension getSpan(int r, int c) {
      if(isSummaryRow(r)) {
         return null;
      }

      return summary.getSpan(rowN(r), c);
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      if(isSummaryRow(r)) {
         return summaryRow[c + 1].align;
      }

      return summary.getAlignment(rowN(r), c);
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      if(isSummaryRow(r)) {
         return summaryRow[c + 1].font;
      }

      return summary.getFont(rowN(r), c);
   }

   /**
    * Return the per cell line wrap mode. If the line wrap mode is true,
    * lines are wrapped when the text can not fit on one line. Otherwise
    * the wrapping is never done and any overflow text will be truncated.
    * @param r row number.
    * @param c column number.
    * @return true if line wrapping should be done.
    */
   @Override
   public boolean isLineWrap(int r, int c) {
      if(isSummaryRow(r)) {
         return summaryRow[c + 1].wrap;
      }

      return summary.isLineWrap(rowN(r), c);
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
      if(isSummaryRow(r)) {
         return summaryRow[c + 1].foreground;
      }

      return summary.getForeground(rowN(r), c);
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
      if(isSummaryRow(r)) {
         return summaryRow[c + 1].background;
      }

      return summary.getBackground(rowN(r), c);
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      if(isSummaryRow(r)) {
         return summaryRow[c + 1].value;
      }

      return summary.getObject(rowN(r), c);
   }

   /**
    * Set the cell value.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   @Override
   public void setObject(int r, int c, Object v) {
      if(isSummaryRow(r)) {
         summaryRow[c + 1].value = v;
         fireChangeEvent();
      }
      else {
         summary.setObject(rowN(r), c, v);
      }
   }

   /**
    * Dispose the table to clear up temporary resources.
    */
   @Override
   public void dispose() {
      summary.dispose();
   }

   /**
    * Check if a row number is a summary row.
    */
   private boolean isSummaryRow(int r) {
      return r == region.y + region.height;
   }

   /**
    * Map a row number to the original table's row number.
    */
   private int rowN(int r) {
      if(r < region.y + region.height) {
         return r;
      }

      return r - 1;
   }

   /**
    * @return the report/vs name which this filter was created for,
    * and will be used when insert audit record.
    */
   @Override
   public String getReportName() {
      String name = super.getReportName();
      return name != null ? name : summary == null ? null : summary.getReportName();
   }

   /**
    * @return the report type which this filter was created for:
    * ExecutionBreakDownRecord.OBJECT_TYPE_REPORT or
    * ExecutionBreakDownRecord.OBJECT_TYPE_VIEWSHEET
    */
   @Override
   public String getReportType() {
      String type = super.getReportType();
      return type != null ? type : summary == null ? null : summary.getReportType();
   }

   /*
    * Hold information for summary table lens' summary row.
    */
   static class SummaryCellInfo extends TableCellInfo {
      public SummaryCellInfo(SummaryTableLens tbl, int col, int row, int rows) {
         colBorder = tbl.getSummaryColBorder(col);
         colBorderC = tbl.getSummaryColBorderColor(col);

         if(col < 0) {
            return;
         }

         value = tbl.getSummary(col, row, rows);
         foreground = tbl.getSummaryForeground(col);
         background = tbl.getSummaryBackground(col);
         rowBorder = tbl.getSummaryRowBorder(col);
         rowBorderC = tbl.getSummaryRowBorderColor(col);
         align = tbl.getSummaryAlignment(col);
         font = tbl.getSummaryFont(col);
         wrap = tbl.isSummaryLineWrap(col);
         insets = tbl.getSummaryInsets(col);
      }

      Object value;
   }

   SummaryTableLens summary;
   Rectangle region;
   SummaryCellInfo[] summaryRow;
   int summaryH;
}

