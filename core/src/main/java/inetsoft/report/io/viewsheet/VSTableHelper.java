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
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import inetsoft.util.SparseMatrix;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * VSTable exporting helper.
 *
 * @version 8.5, 8/21/2006
 * @author InetSoft Technology Corp
 */
public abstract class VSTableHelper extends VSTableDataHelper {
   /**
    * Calculate the columns start position and width.
    * @param info the specified table assembly info.
    */
   @Override
   protected int calculateColumnsPosition(TableDataVSAssemblyInfo info,
                                          VSTableLens lens) {
      Dimension size = getLensSize(lens);
      int count = size == null ? 1 : size.width;
      count = Math.max(count, 1);
      int infoSize = CoordinateHelper.getAssemblySize(info, size).width;
      int infoCols = infoSize == info.getPixelSize().width ? infoSize / AssetUtil.defw : infoSize;
      int totalColumnWidth = 0;
      columnStarts = new int[count];
      columnWidths = new int[count];
      int rcount = size == null ? 1 : size.height;
      rcount = Math.max(rcount, 1);

      for(int icol = 0; icol < count; icol++) {
         columnWidths[icol] = 1;
         columnStarts[icol] = totalColumnWidth;
         totalColumnWidth += columnWidths[icol];
      }

      initColumns(info, lens, columnStarts, columnWidths, infoCols - count);

      return infoCols;
   }

   /**
    * Write table lens's data cell.
    */
   protected void writeTableDataCell(int irow, int icol, SparseMatrix isWritten,
                                     TableDataVSAssemblyInfo info,
                                     VSTableLens lens,
                                     int rowCount, int colCount,
                                     Rectangle tableRange,
                                     VSCompositeFormat parentformat,
                                     Rectangle rec)
   {
      int spanWidth = columnWidths[icol];
      int spanHeight = 1;
      int spanCount = 1;

      if("T".equals(isWritten.get(irow, icol))) {
         return;
      }

      Dimension bounds = lens.getSpan(irow, icol);

      if(bounds != null) {
         for(int m = irow; m < irow + bounds.height && m < rowCount; m++) {
            for(int n = icol ; n < icol + bounds.width && n < colCount; n++) {
               isWritten.set(m, n, "T");
            }
         }

         spanCount = bounds.width;

         if(bounds.height > 1) {
            spanHeight = bounds.height;
         }

         for(int i = 1; i < spanCount; i++) {
            spanWidth += columnWidths[icol + i];
         }
      }
      else {
         isWritten.set(irow, icol, "T");
      }

      Dimension span = new Dimension(spanWidth, spanHeight);
      VSFormat format = lens.getFormat(irow, icol);
      String v = lens.getText(irow, icol);

      if(v == null) {
         v = "";  // if more empty rows needed to be displayed.
      }

      Dimension gridsize = CoordinateHelper.getAssemblySize(info, getLensSize(lens));
      int titleH = info.isTitleVisible() ? info.getTitleHeight() : 0;
      int infoCols = gridsize.height == info.getPixelSize().height ?
         (int) Math.round((double) (gridsize.height - titleH) / AssetUtil.defh) : gridsize.height;

      if(rowLines.get(irow) == null) {
         int lines = 0;

         for(int i = 0; i <= irow; i++) {
            lines += lens.getLineCount(i, getExporter().isMatchLayout());
         }

         rowLines.put(irow, lines);
      }

      boolean bottom = isBottom(irow, infoCols);
      boolean right = icol == columnPixelW.length - 1;
      String pattern = getCellFormat(lens, irow, icol);
      Object obj = getObject(lens, irow, icol, pattern);

      applyTableBorders(format, info, icol == 0, bottom, right,
         irow == 0 && !info.isTitleVisible());
      VSFormat newformat = (VSFormat) format.clone();

      Viewsheet vs = getViewsheet();
      Dimension psize = vs.getPixelSize(info);

      int totalW = 0;
      int[] colsWidth = new int[columnPixelW.length];
      System.arraycopy(columnPixelW, 0, colsWidth, 0, colsWidth.length);

      for(int i = 0; i < colsWidth.length; i++) {
         totalW += colsWidth[i];

         if(i == (icol - 1) && totalW > psize.width) {
            return;
         }

         if(totalW > psize.width) {
            colsWidth[i] = psize.width - (totalW - colsWidth[i]);
         }
      }

      // bug1395799577545, add underline for hyperlink text
      if(newformat != null && newformat.getFont() != null &&
         getHyperLink(lens, info, irow, icol) != null)
      {
         StyleFont font = (StyleFont) newformat.getFont();
         font = (StyleFont) font.deriveFont(font.getStyle() | StyleFont.UNDERLINE);
         newformat.setFont(font);
         format = newformat;
      }

      VSCompositeFormat cfmt = new VSCompositeFormat();
      cfmt.setUserDefinedFormat(format);
      Insets padding = lens.getInsets(irow, icol);
      writeTableCell(tableRange.x, tableRange.y, span,
                     getPixelBounds(info, irow, icol, span, lens),
                     irow, columnStarts[icol], cfmt, v, obj,
                     getHyperLink(lens, info, irow, icol), parentformat, rec,
                     pattern, colsWidth, padding);
   }

   protected boolean isBottom(int irow, int infoCols) {
      return rowLines.get(irow) == infoCols;
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
    * Gets the number of visible rows, for match exact layouts.
    */
   protected static int getVisibleRowCount(TableDataVSAssemblyInfo info,
                                           VSTableLens lens)
   {
      Dimension size = CoordinateHelper.getAssemblySize(
         info, CoordinateHelper.getLensSize(lens, true));
      int infoRows = 0;

      if(size.height == info.getPixelSize().height) {
         int tableRowsHeight = size.height - (info.isTitleVisible() ? info.getTitleHeight() : 0)
            - info.getViewsheet().getDisplayRowHeight(true, info.getName());
         int displayRowHeight = info.getViewsheet().getDisplayRowHeight(false, info.getName());
         displayRowHeight += lens.getCSSRowPadding(lens.getHeaderRowCount());
         infoRows = (int) Math.round((double) tableRowsHeight / displayRowHeight) + 2;
      }
      else {
         infoRows = size.height;
      }

      final int titleRow = (info instanceof CrosstabVSAssemblyInfo) ? 0 : 1;
      final int visibleSize = infoRows - titleRow;

      int visibleRowCount = 0;
      int rowCounter = 0;

      if(lens.getHeaderRowCount() == lens.getRowCount()) {
         return lens.getHeaderRowCount();
      }

      while(rowCounter < visibleSize && visibleRowCount < lens.getRowCount()) {
         // @by gregm get the line count, accounting for flash wrapping because
         // this is match exact layout
         int lines = lens.getLineCount(visibleRowCount, true);
         rowCounter += lines;
         visibleRowCount++;
      }

      return visibleRowCount;
   }

   /**
    * Get the table range for export.
    */
   @Override
   protected Rectangle getTableRectangle(TableDataVSAssemblyInfo info,
                                         VSTableLens lens)
   {
      VSExporter exporter = this.getExporter();

      // @by gregm ensure we do not print too many rows because of wrapping.
      int rowCount = exporter.isMatchLayout() ?
            getVisibleRowCount(info, lens) : lens.getRowCount();
      rowCount = Math.min(rowCount, lens.getRowCount());

      int colCount = lens.getColCount();
      int anchor_x = info.getPixelOffset().x;
      int anchor_y = info.getPixelOffset().y + (lens.getRowCount() == 0 ?
         AssetUtil.defh : (info.isTitleVisible() ? info.getTitleHeight() : 0));
      colCount = Math.max(colCount, 1);

      return new Rectangle(anchor_x, anchor_y, colCount, rowCount);
   }

   /**
    * Write the data for table assembly.
    * @param info the specified table assembly info.
    * @param lens the specified VSTableLens.
    */
   @Override
   protected void writeData(TableDataVSAssemblyInfo info, VSTableLens lens) {
      Rectangle tableRange = getTableRectangle(info, lens);
      Rectangle vsBounds = getViewsheetBounds();

      if(vsBounds != null) {
         tableRange.x -= vsBounds.x;
         tableRange.y -= vsBounds.y;
      }

      int rowCount = getRowCount(lens);
      int colCount = lens.getColCount();
      rowCount = colCount == 0 ? 0 : rowCount;
      colCount = Math.max(colCount, 1);
      SparseMatrix isWritten = new SparseMatrix();
      VSCompositeFormat parentformat = info.getFormat();
      Rectangle rec = new Rectangle(0, 0, tableRange.width, tableRange.height);
      int lastColStarts = columnStarts[colCount - 1] + columnWidths[colCount - 1];

      for(int irow = 0; irow < tableRange.height; irow++) {
         for(int icol = 0; icol < tableRange.width; icol++) {
            if(irow < rowCount) {
               if(icol < colCount) {
                  writeTableDataCell(irow, icol, isWritten, info, lens,
                                     rowCount, colCount,
                                     tableRange, parentformat, rec);
               }
            }
            else {
               int col = lens.getColCount() == 0 ? icol :
                         (icol < colCount ? columnStarts[icol] :
                                            (lastColStarts + icol - colCount));
               Rectangle bounds = new Rectangle(tableRange);
               bounds.height = 1;
               bounds.width = lens.getColCount() != 0 && icol < colCount ?
                  (icol == colCount - 1 ?
                     columnWidths[icol] :
                     columnStarts[icol + 1] - columnStarts[icol]) : 0;
               Rectangle tableBounds = new Rectangle(rec);
               tableBounds.width = icol == colCount - 1 ?
                  icol + bounds.width : tableBounds.width;

               if(col < tableRange.width) {
                  writeTableAssemblyCell(irow, col, bounds, parentformat, tableBounds);
               }
            }
         }
      }
   }

   /**
    * Check need distribute width or not.
    */
   @Override
   protected Boolean needDistributeWidth() {
      return false;
   }

   // optimization, cache lens size, it's very expensive to calculate.
   private Dimension getLensSize(VSTableLens lens) {
      if(lensSize == null) {
         lensSize = CoordinateHelper.getLensSize(lens, getExporter().isMatchLayout());
      }

      return lensSize;
   }

   protected int[] columnStarts = null;
   protected int[] columnWidths = null;
   private Dimension lensSize = null;
   private Map<Integer, Integer> rowLines = new HashMap<>();
}
