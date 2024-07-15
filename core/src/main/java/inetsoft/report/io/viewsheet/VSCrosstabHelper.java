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
package inetsoft.report.io.viewsheet;

import inetsoft.report.StyleFont;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.VSFormatTableLens;
import inetsoft.report.filter.DCMergeDatePartFilter;
import inetsoft.report.filter.DCMergeDatesCell;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.text.Format;

/**
 * Excel exporting helper for crosstab.
 *
 * @version 8.5, 8/16/2006
 * @author InetSoft Technology Corp
 */
public abstract class VSCrosstabHelper extends VSTableDataHelper {
   /**
    * Calculate the columns start position and width.
    * @param info the specified table assembly info.
    */
   @Override
   protected int calculateColumnsPosition(TableDataVSAssemblyInfo info,
                                          VSTableLens lens) {
      if(lens == null) {
         return info.getPixelSize().width;
      }

      int infoVSColumns = info.getPixelSize().width / AssetUtil.defw;
      int infoVSRows = info.getPixelSize().height / AssetUtil.defh;
      int cheader = 0;
      int ncol = 0;
      int colCount = 0;
      int rowCount = 0;

      if(info instanceof CrosstabVSAssemblyInfo) {
         VSCrosstabInfo vsinfo = ((CrosstabVSAssemblyInfo) info).getVSCrosstabInfo();
         boolean multiAggr = vsinfo != null ? vsinfo.getRuntimeAggregates().length > 1: false;
         cheader = vsinfo != null ? vsinfo.getRuntimeRowHeaders().length : 0;

         if(cheader == 0 && multiAggr) {
            cheader = 1;
         }

         ncol = info.getColumnCount() + cheader;
         colCount = Math.max(lens.getColCount(), infoVSColumns);
         rowCount = Math.max(lens.getRowCount(), infoVSRows);
      }
      else if(info instanceof CalcTableVSAssemblyInfo) {
         ncol = colCount = Math.max(lens.getColCount(), infoVSColumns);
      }

      int x = 0;

      if(ncol > 0 && getExporter().isMatchLayout()) {
         colCount = Math.min(colCount, ncol);
      }

      columnStarts = new int[colCount];
      columnWidths = new int[colCount];
      columnHeights = new int[rowCount];

      for(int icol = 0; icol < colCount; icol++) {
         columnWidths[icol] = 1;
         columnStarts[icol] = x;
         x += columnWidths[icol];
      }

      for(int irow = 0; irow < rowCount; irow++) {
         columnHeights[irow] = 1;
      }

      initColumns(info, lens, columnStarts, columnWidths, infoVSColumns - colCount);

      return colCount;
   }

   /**
    * Write table lens's data cell.
    */
   protected double writeTableDataCell(int irow, int icol,
                                       SparseMatrix colWritten,
                                       SparseMatrix rowWrittent,
                                       TableDataVSAssemblyInfo info,
                                       VSTableLens lens,
                                       Rectangle tableRange,
                                       VSCompositeFormat parentformat,
                                       Rectangle rec,
                                       double totalHeight, double height)
   {
      VSFormat format = lens.getFormat(irow, icol);

      if("T".equals(colWritten.get(irow, icol)) || "X".equals(rowWrittent.get(irow, icol))) {
         return totalHeight;
      }

      // for the crosstab displays in viewsheet is not absolute
      // correct, it should display same as designer. and because in
      // ViewsheetTableModel.as, the function getSpan() cause class cast
      // problem(Dimension to Rectangle), so displays error. and if
      // fix the problem, then if scroll the crosstab, it will display
      // not same as not scroll it, and there is not have enough
      // infomations to fix the problem. so here not process the span
      // just same as flex, see bug1245830539957
      Dimension bounds = null;
      Dimension span = lens.getSpan(irow, icol);
      Rectangle vsbounds = lens.getVSSpan(irow, icol);

      if(isSummarySideBySide(info) && vsbounds != null) {
         int widthCount = vsbounds.x + vsbounds.width;
         int heightCount = vsbounds.y + vsbounds.height;
         int width = 0;
         int height1 = 0;

         for(int i = irow; i - irow < heightCount; i++) {
            if(i >= lens.getRowCount() || i >= tableRange.height) {
               break;
            }

            height1 += columnHeights[i];

            for(int j = icol; j - icol < widthCount; j++) {
               if(j >= lens.getColCount() || j >= tableRange.width) {
                  break;
               }

               rowWrittent.set(i, j, "X");
               colWritten.set(i, j, "T");
            }
         }

         for(int j = icol; j - icol < widthCount; j++) {
            if(j >= lens.getColCount() || j >= tableRange.width) {
               break;
            }

            width += columnWidths[j];
         }

         bounds = new Dimension(width, height1);
      }
      else if(span != null) {
         bounds = new Dimension(0, span.height);

         if(irow + span.height > tableRange.height) {
            bounds.height = tableRange.height - irow;
         }

         for(int i = 0; i < span.width; i++) {
            bounds.width += columnWidths[icol + i];
         }

         for(int i = 0; i < span.height; i++) {
            for(int j = 0; j < span.width; j++) {
               colWritten.set(irow + i, icol + j, "T");
               rowWrittent.set(irow + i, icol + j, "X");
            }
         }
      }
      else {
         bounds = new Dimension(columnWidths[icol], 1);
      }

      colWritten.set(irow, icol, "T");
      rowWrittent.set(irow, icol, "X");
      Rectangle2D pixelBounds = getPixelBounds(info, irow, icol, bounds, lens);

      if(pixelBounds != null) {
         totalHeight += pixelBounds.getHeight();

         if(totalHeight > height) {
            pixelBounds.setFrame(pixelBounds.getX(), pixelBounds.getY(),
                                 pixelBounds.getWidth(),
                                 pixelBounds.getHeight() -
                                 (totalHeight - height));
         }
      }

      // fix bug1393468405299, excel always return null
      if(pixelBounds != null && pixelBounds.getHeight() <= 0) {
         return totalHeight;
      }

      int infoRows = (int) Math.round((double) (info.getPixelSize().height -
                                                info.getTitleHeight()) / AssetUtil.defh);
      int infoCols = (int) Math.round((double) info.getPixelSize().width / AssetUtil.defw);

      boolean bottom = (span == null) ? irow == infoRows - 1
         : (irow + span.height - 1 == infoRows - 1);
      boolean right = (span == null) ? icol == lens.getColCount() - 1
         : (icol + span.width == lens.getColCount());

      if(info.isShrink()) {
         if(lens.getColCount() < infoCols) {
            right = (span == null) ? icol == lens.getColCount() - 1
               : (icol + span.width == lens.getColCount());
         }

         if(lens.getRowCount() < infoRows) {
            bottom = (span == null) ? irow == lens.getRowCount() - 1
               : (irow + span.height == lens.getRowCount());
         }
      }

      applyTableBorders(format, info, icol == 0, bottom || isBottom(irow, span), right,
         irow == 0 && !info.isTitleVisible());
      VSFormat newformat = (VSFormat) format.clone();

       // bug1398247680341, add underline for hyperlink text
      if(newformat != null && newformat.getFont() != null &&
         getHyperLink(lens, irow, icol) != null)
      {
         StyleFont font = (StyleFont) newformat.getFont();
         font = (StyleFont) font.deriveFont(font.getStyle() | StyleFont.UNDERLINE);
         newformat.setFont(font);
         format = newformat;
      }

      String pattern = getCellFormat(lens, irow, icol);
      Object obj = getObject(lens, irow, icol, pattern);
      String val = getExporter().getFileFormatType() == FileFormatInfo.EXPORT_TYPE_EXCEL && !"@".equals(pattern) ?
         Tool.toString(obj) : lens.getText(irow, icol);

      if(pattern == null) {
         VSFormatTableLens vsfmt =
            (VSFormatTableLens) Util.getNestedTable(lens, VSFormatTableLens.class);

         if(vsfmt != null && vsfmt.getCellFormat(irow, icol) instanceof ExtendedDecimalFormat) {
            ExtendedDecimalFormat exfmt = (ExtendedDecimalFormat) vsfmt.getCellFormat(irow, icol);
            pattern = exfmt.toPattern();
         }
      }

      if(obj instanceof DCMergeDatesCell) {
         obj = val = Tool.toString(((DCMergeDatesCell) obj).getFormatedOriginalDate());
      }
      else if(obj instanceof DCMergeDatePartFilter.MergePartCell) {
         Format cellfmt = TableFormat.getFormat(format.getFormat(), format.getFormatExtent());
         TableDateComparisonFormat dcDatePartFormat = new TableDateComparisonFormat(cellfmt);
         obj = dcDatePartFormat.format(obj);
      }

      VSCompositeFormat cfmt = new VSCompositeFormat();
      cfmt.setUserDefinedFormat(format);
      Insets padding = lens.getInsets(irow, icol);

      // to be implemented by sub class
      writeTableCell(tableRange.x, tableRange.y, bounds, pixelBounds,
                     irow, columnStarts[icol], cfmt, val, obj,
                     getHyperLink(lens, irow, icol), parentformat, rec,
                     pattern, columnPixelW, padding);

      return totalHeight;
   }

   // Check it for excel exporting.
   protected boolean isBottom(int irow, Dimension span) {
      return false;
   }

   /**
    * Check whether the info property is summary side by side.
    */
   private boolean isSummarySideBySide(TableDataVSAssemblyInfo info) {
      if(!(info instanceof CrosstabVSAssemblyInfo)) {
         return false;
      }

      VSCrosstabInfo cinfo = ((CrosstabVSAssemblyInfo) info).getVSCrosstabInfo();
      return cinfo.isSummarySideBySide();
   }

   /**
    * Write table cells that lens not cover.
    */
   protected void writeTableAssemblyCell(int irow, int icol,
                                         Rectangle tableRange,
                                         VSCompositeFormat parentformat,
                                         Rectangle rec)
   {
   }

   /**
    * Get the table range for export.
    */
   @Override
   protected Rectangle getTableRectangle(TableDataVSAssemblyInfo info,
                                         VSTableLens lens)
   {
      VSExporter exporter = this.getExporter();
      int rowCount = exporter.isMatchLayout() && info.isShrink() ?
         VSTableHelper.getVisibleRowCount(info, lens) : lens.getRowCount();
      int colCount = columnPixelW.length;
      int anchor_x = info.getPixelOffset().x;
      int anchor_y = info.getPixelOffset().y + (info.isTitleVisible() ? info.getTitleHeight() : 0);
      colCount = Math.max(colCount, 1);

      return new Rectangle(anchor_x, anchor_y, colCount, rowCount);
   }

   @Override
   protected double getPixelToPointRatio() {
      return 1;
   }

   protected double getTablePixelHeight(TableDataVSAssemblyInfo info) {
      double ratio = getPixelToPointRatio();
      Rectangle2D pbounds = getPixelBounds(info, 0, 0, new Dimension(1, 1), null);
      double pheight = pbounds == null ? 0 : pbounds.getHeight();

      return getViewsheet().getPixelSize(info).height * ratio - pheight * ratio;
   }

   /**
    * Write the data for crosstab assembly.
    * @param info the specified CrosstabVSAssemblyInfo.
    * @param lens the specified VSTableLens.
    */
   @Override
   protected void writeData(TableDataVSAssemblyInfo info, VSTableLens lens) {
      Rectangle tableRange = getTableRectangle(info, lens);

      //if table is inside of embedded viewsheet, we must offset it by the bounds of the
      //viewsheet
      Rectangle vsBounds = getViewsheetBounds();

      if(vsBounds != null) {
         tableRange.x -= vsBounds.x;
         tableRange.y -= vsBounds.y;
      }

      SparseMatrix colWritten = new SparseMatrix();
      SparseMatrix rowWritten = new SparseMatrix();
      int rowCount = getRowCount(lens);
      int colCount = Math.min(lens.getColCount(), columnWidths.length);
      colCount = Math.max(colCount, 1);

      double[] totalHeights = new double[tableRange.width];
      VSCompositeFormat parentformat = info.getFormat();
      Rectangle rec = new Rectangle(0, 0, tableRange.width, tableRange.height);
      int lastColStarts = columnStarts[colCount - 1] + columnWidths[colCount - 1];
      Viewsheet vs = getViewsheet();
      Dimension psize = vs.getPixelSize(info);
      double height = Math.max(getTablePixelHeight(info), psize.getHeight());

      if(info.isTitleVisible()) {
         height -= info.getTitleHeight();
      }

      for(int irow = 0; irow < tableRange.height; irow++) {
         for(int icol = 0; icol < tableRange.width; icol++) {
            if(irow < rowCount) {
               if(icol < colCount) {
                  totalHeights[icol] = writeTableDataCell(irow, icol,
                          colWritten, rowWritten, info, lens, tableRange, parentformat,
                        rec, totalHeights[icol], height);
               }
            }
            else if(irow < rowCount && icol >= colCount) {
               // do nothing.
            }
            else {
               int col = icol < colCount ? columnStarts[icol] : (lastColStarts + icol - colCount);
               Rectangle tableBounds = new Rectangle(rec);
               tableBounds.width = icol == colCount - 1 ? icol + 1 : tableBounds.width;

               if(col < colCount) {
                  writeTableAssemblyCell(irow, col, tableRange, parentformat, tableBounds);
               }
            }
         }

         boolean more = false;

         // check if there are more cells to be printed
         for(double colh : totalHeights) {
            if(colh < height) {
               more = true;
               break;
            }
         }

         if(!more) {
            break;
         }
      }
   }

   // get number of header rows
   private int getHeaderRowCount(TableDataVSAssemblyInfo info) {
      int hrow = 0;

      if(info instanceof CrosstabVSAssemblyInfo) {
         VSCrosstabInfo cinfo = ((CrosstabVSAssemblyInfo) info).getVSCrosstabInfo();
         hrow = cinfo == null ? 0 : Math.max(1, cinfo.getRuntimeColHeaders().length);
      }

      if(info instanceof CalcTableVSAssemblyInfo) {
         hrow = ((CalcTableVSAssemblyInfo) info).getHeaderRowCount();
      }

      return hrow;
   }

   // check if text wrapping is enabled, matches Crosstab.as
   @Override
   protected boolean isCellWrapping(TableDataVSAssemblyInfo info) {
      return info.getFormatInfo().getFormats().anyMatch(f -> f.isWrapping());
   }

   // get the height with cell wrapping, matches Crosstab.as
   @Override
   protected int getWrappingCellHeight(TableDataVSAssemblyInfo info, VSTableLens lens,
                                       int row, int lines)
   {
      int headerH = 0;

      if(row < getHeaderRowCount(info)) {
         int rcnt = lines;
         int preCnt = getWrappingHeaderRowCount(row, lens);

         for(int i = 0; i < rcnt; i++) {
            headerH += getCellHeight(getViewsheet(), info, i + preCnt + 1, lens);
         }

         return headerH;
      }

      int detailH = getCellHeight(
         getViewsheet(), info,
         getWrappingHeaderRowCount(getHeaderRowCount(info), lens) + 1, lens);
      headerH = lines * detailH;

      return headerH;
   }

   // get number of lines the header rows wrap into
   private int getWrappingHeaderRowCount(int row, VSTableLens lens) {
      int count = 0;

      for(int i = 0; i < row; i++) {
         count += lens.getLineCount(i);
      }

      return count;
   }

   /**
    * Check need distribute width or not.
    */
   @Override
   protected Boolean needDistributeWidth() {
      return false;
   }

   // @by ankitmathur, This method should return true for Crosstabs because
   // the last column of a Crosstab always fills the entire Table Assembly
   // regardless of size. However, this is not the case for Freehand tables,
   // so if that functionally changes for Freehand tables as well, this
   // method will should no longer be needed.
   // 1-06-2015, removing this method because the change for bug1418832574870,
   // now makes Freehand tables behave the same as Crosstabs.
   ///**
   // * Check whether to have the columns fill the whole table width.
   // */
   // protected boolean isFillColumns(TableDataVSAssemblyInfo info) {
     // return !(info instanceof CalcTableVSAssemblyInfo);
   // }

   private int[] columnStarts = null;
   private int[] columnWidths = null;
   private int[] columnHeights = null;
}
