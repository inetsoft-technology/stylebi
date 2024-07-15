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
package inetsoft.web.binding.model.table;

import inetsoft.report.*;
import inetsoft.uql.viewsheet.FormatInfo;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.web.viewsheet.model.VSFormatModel;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CalcTableLayout {
   public CalcTableLayout(CalcTableVSAssemblyInfo info, FormatInfo formatInfo, TableLens lens) {
      TableLayout layout = info.getTableLayout();

      createTableCols(layout);
      createTableRows(layout);
      createTableCells(layout, lens);
      initBaseInfo(layout);
      initFormatInfo(formatInfo, info);
   }

   /**
    * Get rows.
    * @return rows.
    */
   public List<CalcTableRow> getTableRows() {
      return rows;
   }

   /**
    * Add row.
    * @param row the row adding.
    */
   public void addTableRow(CalcTableRow row) {
      rows.add(row);
   }

   /**
    * Get columns.
    * @return columns.
    */
   public List<TableColumn> getTableColumns() {
      return columns;
   }

   /**
    * Add column.
    * @param column to be add.
    */
   public void addTableColumn(TableColumn column) {
      columns.add(column);
   }

   private void createTableCols(TableLayout layout) {
      int c = layout.getColCount();

      for(int i = 0; i < c; i++) {
         TableColumn column = new TableColumn();
         column.setCol(i);
         column.setWidth(layout.getColWidth(i) > 0 ? layout.getColWidth(i) : 100);
         addTableColumn(column);
      }
   }

   private void createTableRows(TableLayout layout) {
      int r = layout.getRowCount();

      for(int i = 0; i < r; i++) {
         CalcTableRow trow = new CalcTableRow();
         trow.setRow(i);
         trow.setHeight(layout.getRowHeight(i) > 0 ? layout.getRowHeight(i) : 20);
         addTableRow(trow);
      }
   }

   private void createTableCells(TableLayout layout, TableLens lens) {
      TableDataDescriptor desc = lens.getDescriptor();
      int rnt = layout.getRowCount();
      int cnt = layout.getColCount();

      for(int r = 0; r < rnt; r++) {
         CalcTableRow row = getTableRows().get(r);

         for(int c = 0; c < cnt; c++) {
            Dimension span = layout.getSpan(r, c);
            TableCellBinding bind = (TableCellBinding) layout.getCellBinding(r, c);
            CalcTableCell cell = new CalcTableCell();
            TableDataPath rpath = desc.getCellDataPath(r, c);

            rpath.setPath(new String[] {"Cell [" + r + "," + c + "]"});
            cell.setCellPath(rpath);
            cell.setRow(r);
            cell.setCol(c);

            if(span != null) {
               cell.setSpan(span);
            }

            if(bind != null) {
               cell.setText(cellContent(bind));
               cell.setBindingType(bind.getBType());
            }

            row.addTableCell(cell);
         }
      }
   }

   private void initFormatInfo(FormatInfo formatInfo, CalcTableVSAssemblyInfo info) {
      for(int i = 0; i < rows.size(); i++) {
         CalcTableRow calcRow = rows.get(i);

         for(int j  = 0; j < calcRow.getTableCells().size(); j++) {
            CalcTableCell cell = calcRow.getTableCells().get(j);
            CalcTableCell baseCell = findBaseCell(cell);
            TableDataPath path = baseCell.getCellPath();
            VSCompositeFormat fmt = formatInfo.getFormat(path, false);

            // if format not defined, get obj/summary format.
            // this should be same as VSFormatTableLens.createCellFormat()
            if(fmt == null) {
               TableDataPath path0 = new TableDataPath(-1, TableDataPath.OBJECT);
               fmt = (VSCompositeFormat) formatInfo.getFormat(path0);
            }

            if(fmt == null && path != null && path.getType() == TableDataPath.DETAIL) {
               TableDataPath temp = new TableDataPath(0, TableDataPath.SUMMARY, path.getDataType(),
                                                      path.getPath());
               fmt = (VSCompositeFormat) formatInfo.getFormat(temp);
            }

            cell.setVsFormat(new VSFormatModel(fmt, info));
         }
      }
   }

   private CalcTableCell findBaseCell(CalcTableCell cell) {
      if(cell.getBaseInfo() == null) {
         return cell;
      }

      int r = (int) cell.getBaseInfo().getX();
      int c = (int) cell.getBaseInfo().getY();

      if(rows == null) {
         return null;
      }

      return rows.get(r).getTableCells().get(c);
   }

   // For vs table/crosstab/calc, using row and column can locate one cell, so the
   // The TableCell's region attribute is useless. And for mreged cells, the frist cell
   // has span(width and height), other cells has basecellinfo(it has the base cell's
   // row, column, width, height).
   private void initBaseInfo(TableLayout layout) {
      int rnt = layout.getRowCount();
      int cnt = layout.getColCount();

      for(int r = 0; r < rnt; r++) {
         for(int c = 0; c < cnt; c++) {
            Dimension span = layout.getSpan(r, c);

            if(span == null) {
               continue;
            }

            int w = span.width;
            int h = span.height;

            // Process span cell, include x span, y span and all span.
            if(w > 1 || h > 1) {
               for(int i = 0; i < h; i++) {
                  CalcTableRow nrow = rows.get(r + i);

                  for(int j = 0; j < w; j++) {
                     // The base cell should not add baseinfo.
                     if(i == 0 && j == 0) {
                        continue;
                     }

                     CalcTableCell base = nrow.getTableCells().get(c + j);
                     base.setBaseInfo(new Rectangle(r, c, w, h));
                     base.setText("");
                     base.setSpan(null);
                  }
               }
            }
         }
      }
   }

   public String cellContent(TableCellBinding bind) {
      if(bind == null) {
         return null;
      }

      if(bind.getType() == TableCellBinding.BIND_FORMULA) {
         return "=" + bind.getValue();
      }

      if(bind.getType() != TableCellBinding.BIND_COLUMN) {
         return bind.getValue();
      }

      String cellValue = bind.getValue();
      cellValue = bind.getBType() == TableCellBinding.GROUP ?
         GROUP_SYMBOL + cellValue :
         bind.getBType() == TableCellBinding.SUMMARY ?
         SUMMARY_SYMBOL + cellValue : cellValue;

      cellValue = "[" + cellValue + "]";
      cellValue = bind.getExpansion() == TableCellBinding.EXPAND_H ?
         RIGHT_ARROW + cellValue :
         bind.getExpansion() == TableCellBinding.EXPAND_V ?
         DOWN_ARROW + cellValue : cellValue;

      return cellValue;
   }

   private static final String DOWN_ARROW = "\u2193";
   private static final String RIGHT_ARROW = "\u2192";
   private static final String GROUP_SYMBOL = "\u039E";
   private static final String SUMMARY_SYMBOL = "\u2211";
   private List<CalcTableRow> rows = new ArrayList<>();
   private List<TableColumn> columns = new ArrayList<>();
}
