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
package inetsoft.web.binding.handler;

import inetsoft.report.BaseLayout.Region;
import inetsoft.report.*;
import inetsoft.report.TableLayout.RegionIndex;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.report.internal.binding.TopNInfo;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.util.Tool;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.util.ArrayList;
import java.util.Map;

@Component
public class TableLayoutHandler {
   /** TableTool insertrow/column */
   public void doOperation(CalcTableVSAssemblyInfo info, String op, Rectangle rect, int n) {
      int r = (int) rect.getY();
      int c = (int) rect.getX();
      int w = (int) rect.getWidth();
      int h = (int) rect.getHeight();

      if("insertRow".equals(op)) {
         for(int i = 0; i < n; i++) {
            insertRows(info, r, 1, false);
         }
      }
      else if("appendRow".equals(op)) {
         for(int i = 0; i < n; i++) {
            insertRows(info, r + h - 1, 1, true);
         }
      }
      else if("deleteRow".equals(op)) {
         deleteRows(info, r, n);
      }
      else if("insertCol".equals(op)) {
         for(int i = 0; i < n; i++) {
            insertCols(info, c, 1, false);
         }
      }
      else if("appendCol".equals(op)) {
         for(int i = 0; i < n; i++) {
            insertCols(info, c + w - 1, 1, true);
         }
      }
      else if("deleteCol".equals(op)) {
         deleteCols(info, c, n);
      }
      else if("mergeCells".equals(op)) {
         mergeCells(info, r, c, w, h);
      }
      else if("splitCells".equals(op)) {
         splitCells(info, r, c);
      }
   }

   public void copyOperation(CalcTableVSAssemblyInfo info, String op,
      Rectangle oselection, Rectangle nselection)
   {
      if("remove".equals(op)) {
         removeCell(info, nselection);
      }
      // copy-paste action
      else if("copy".equals(op)) {
         Object[][] content = copy(info, oselection, true, true, true);
         pasteCell(info, nselection, content);
      }
      // cut-paste action
      else if("cut".equals(op)) {
         Object[][] content = copy(info, oselection, true, true, true);
         removeCell(info, oselection);
         pasteCell(info, nselection, content);
      }
   }

   private void pasteCell(CalcTableVSAssemblyInfo info, Rectangle selection,
      Object[][] values)
   {
      int h = selection.height;

      if(values.length > h) {
         h = values.length;
      }

      int w = selection.width;

      if(values.length > 0 && values[0].length > w) {
         w = values[0].length;
      }

      paste(info, selection.y, selection.x, values, h, w, true);
   }

   public void removeCell(CalcTableVSAssemblyInfo info, Rectangle rect) {
      Rectangle reg = convertToLayoutRect(info, rect, true);
      clear(info, reg, true, true);
   }

   private Rectangle convertToLayoutRect(CalcTableVSAssemblyInfo info, Rectangle reg,
      boolean span)
   {
      Rectangle rect = null;

      for(int r = reg.y; r < reg.y + reg.height; r++) {
         for(int c = reg.x; c < reg.x + reg.width; c++) {
            Rectangle lrect = span ? getCellSpan(info.getTableLayout(), r, c) : null;

            if(lrect == null) {
               lrect = new Rectangle(c, r, 1, 1);
            }

            if(rect == null) {
               rect = lrect;
            }

            rect = rect.union(lrect);
         }
      }

      return rect;
   }

   private void insertRows(CalcTableVSAssemblyInfo info, int r, int n, boolean append) {
      TableLayout layout = info.getTableLayout();

      for(int i = 0; i < n; i++) {
         RegionIndex reg = layout.getRegionIndex(r);
         int adj = append ? 1 : 0;
         int start = reg.getRow() + adj;
         int height = layout.getRowCount() - r - adj;
         Object[][] clip = null;
         Rectangle rect = new Rectangle(0, r + adj, layout.getColCount(), height);
         clip = copy(info, rect);
         reg.getRegion().insertRow(start);
         updateHeaderCount(info, 1, r, true, true);

         if(clip != null) {
            clear(info, new Rectangle(0, r + adj, layout.getColCount(), 1));
            paste(info, r + adj + 1, 0, clip, -1, -1, true);
         }

         for(int j = 0; j < layout.getColCount(); j++) {
            Rectangle span = getCellSpan(layout, r, j);

            if(span == null) {
               continue;
            }

            CellInfoHandler cinfo = new CellInfoHandler(info, span.y, span.x);
            cinfo.spreadFormat(info, r, j);
         }

         //paste has update the format path.
         if(clip == null) {
            updateFormatPath(info, n, r, true);
         }

         updateHyperlinkPath(info, n, r, true);
         updateHighlightPath(info, n, r, true);
      }
   }

   private void deleteRows(CalcTableVSAssemblyInfo info, int r, int n) {
      TableLayout layout = info.getTableLayout();

      for(int i = r + n - 1; i >= r; i--) {
         RegionIndex reg = layout.getRegionIndex(r);

         if(reg == null) {
            return;
         }

         Rectangle rect = new Rectangle(0, r + 1, layout.getColCount(),
            reg.getRegion().getRowCount() - reg.getRow() - 1);
         Object[][] clip = copy(info, rect);

         if(clip != null) {
            clear(info, new Rectangle(0, r, layout.getColCount(), 1),
                  true, false);
            clear(info, rect);
         }

         reg.getRegion().removeRow(reg.getRow());

         if(clip != null) {
            paste(info, r, 0, clip, -1, -1, true);
         }
      }

      updateFormatPath(info, n, r, false);
      updateHyperlinkPath(info, n, r, false);
      updateHighlightPath(info, n, r, false);
      updateHeaderCount(info, n, r, true, false);
   }

   private void insertCols(CalcTableVSAssemblyInfo info, int c, int n, boolean append) {
      if(append) {
         c++;
      }

      TableLayout layout = info.getTableLayout();

      for(int i = 0; i < n; i++) {
         Object[][] clip = null;
         Rectangle rect = new Rectangle(c, 0, layout.getColCount() - c, layout.getRowCount());
         clip = copy(info, rect);
         layout.insertColumn(c);

         if(clip != null) {
            clear(info, new Rectangle(c, 0, 1, layout.getRowCount()));
            paste(info, 0, c + 1, clip, -1, -1, true);
         }

         for(int j = 0; j < layout.getRowCount(); j++) {
            Rectangle span = getCellSpan(layout, j, c);

            if(span == null) {
               continue;
            }

            CellInfoHandler cinfo = new CellInfoHandler(info, span.y, span.x);
            cinfo.spreadFormat(info, i, c);
         }

         if(c + 1 < layout.getColCount()) {
            layout.setColWidth(layout.getColWidth(c + 1), c);
         }
         else if(c > 0) {
            layout.setColWidth(layout.getColWidth(c - 1), c);
         }
         else {
            layout.setColWidth(-1, c);
         }
      }

      updateHeaderCount(info, n, c, false, true);
   }

   private void deleteCols(CalcTableVSAssemblyInfo info, int c, int n) {
      TableLayout layout = info.getTableLayout();

      for(int i = n - 1; i >= 0; i--) {
         Rectangle rect = new Rectangle(c + 1, 0, layout.getColCount() - c - 1,
            layout.getRowCount());
         Object[][] clip = copy(info, rect);

         if(clip != null) {
            clear(info, new Rectangle(c, 0, 1, layout.getRowCount()), true, false);
            clear(info, rect);
         }

         layout.removeColumn(c);

         if(clip != null) {
            paste(info, 0, c, clip, -1, -1, true);
         }
      }

      updateHeaderCount(info, n, c, false, false);
   }

   private void mergeCells(CalcTableVSAssemblyInfo info, int r, int c, int w, int h) {
      Dimension span = new Dimension(w, h);
      TableLayout layout = info.getTableLayout();

      if(layout == null) {
         return;
      }

      layout.setSpan(r, c, span);
      Rectangle nspan = getCellSpan(layout, r, c);

      // merge the cell attributes and binding
      if(nspan != null) {
         CellInfoHandler cinfo = new CellInfoHandler(info, nspan.y, nspan.x);

         for(int i = nspan.y; i < nspan.y + nspan.height; i++) {
            for(int j = nspan.x; j < nspan.x + nspan.width; j++) {
               if(i == nspan.y && j == nspan.x) {
                  continue;
               }

               CellInfoHandler ninfo = new CellInfoHandler(info, i, j);
               cinfo.merge(ninfo);
               // clear the merged cell so the contents/attributes would not be
               // duplicated when the cell is later split
               // @by larryl, the behavior seems better to keep the original
               // setting in both cells so when split, the original setting is
               // restored. There is always the possibility that the original
               // blank cell will inherit the merged attributes after split,
               // but not losing the setting seems more reasonable.
               // ninfo.clear(elem, i, j);
            }
         }

         // first update the top-left cell back, then spread others cell
         // otherwise the CellInfo in spreadCell is in old state
         cinfo.set(info, nspan.y, nspan.x);
         spreadCell(info, nspan.y, nspan.x);
      }
   }

   private void splitCells(CalcTableVSAssemblyInfo info, int r, int c) {
      TableLayout layout = info.getTableLayout();

      for(int i = 0; i < layout.getRowCount(); i++) {
         for(int j = 0; j < layout.getColCount(); j++) {
            Dimension span = layout.getSpan(i, j);

            if(span == null) {
               continue;
            }

            Rectangle rect = new Rectangle(j, i, span.width, span.height);

            if(rect.contains(c, r)) {
               spreadCell(info, i, j);
               layout.setSpan(i, j, null);
            }
         }
      }
   }

   /**
    * Spread cell.
    */
   public void spreadCell(CalcTableVSAssemblyInfo info, int row, int col) {
      TableLayout layout = info.getTableLayout();
      Rectangle nspan = getCellSpan(layout, row, col);

      // no span or not the first span cell
      if(nspan == null) {
         return;
      }

      CellInfoHandler cinfo = new CellInfoHandler(info, row, col);

       // @by yuz, fix bug1201515840437, spread format to merged cells
      for(int i = nspan.y; i < nspan.y + nspan.height; i++) {
         for(int j = nspan.x; j < nspan.x + nspan.width; j++) {
            if(i == row && j == col) {
               continue;
            }

            cinfo.spreadFormat(info, i, j);
            cinfo.spreadValue(info, i, j);
         }
      }
   }

   /**
    * Drag and drop a cell.
    */
   public void dropCell(CalcTableVSAssemblyInfo info, Rectangle opos,
      Rectangle dropRect, boolean move)
   {
      TableLayout layout = info.getTableLayout();
      int maxRow = layout.getRowCount();
      int maxCol = layout.getColCount();

      // Detect dropping beyond the table bounds.
      Insets dropInsets = new Insets(dropRect.y, dropRect.x,
         dropRect.y + dropRect.height, dropRect.x + dropRect.width);

      int rowOffset = (dropInsets.top >= 0) ? 0 : -(dropInsets.top);
      int colOffset = (dropInsets.left >= 0) ? 0 : -(dropInsets.left);
      int heightOffset = ((dropInsets.bottom <= maxRow) ?
         0 : -(dropInsets.bottom - maxRow)) - rowOffset;
      int widthOffset = ((dropInsets.right <= maxCol) ?
         0 : -(dropInsets.right - maxCol)) - colOffset;

      // If dropping beyond bounds, then trim the drop AND source bounds.
      Rectangle destination = new Rectangle(
         dropRect.x, dropRect.y, dropRect.width, dropRect.height);
      destination.x += colOffset;
      destination.y += rowOffset;
      destination.width += widthOffset;
      destination.height += heightOffset;

      Rectangle source = new Rectangle(opos.x, opos.y, opos.width, opos.height);
      source.x += colOffset;
      source.y += rowOffset;
      source.width += widthOffset;
      source.height += heightOffset;

      Object[][] clip = copy(info, source);

      if(clip == null || clip.length <= 0 || clip[0].length <= 0) {
         return;
      }

      if(move) {
         clear(info, opos);
      }

      int w = Math.max(destination.width, clip[0].length);
      int h = Math.max(destination.height, clip.length);

      paste(info, destination.y, destination.x, clip, h, w, true);
   }

   public TableCellBinding createDefalutCellBinding(boolean group, String value) {
      TableCellBinding binding = new TableCellBinding(
         TableCellBinding.BIND_COLUMN, value);
      binding.setRowGroup(TableCellBinding.DEFAULT_GROUP);
      binding.setColGroup(TableCellBinding.DEFAULT_GROUP);
      binding.setMergeRowGroup(TableCellBinding.DEFAULT_GROUP);
      binding.setMergeColGroup(TableCellBinding.DEFAULT_GROUP);
      binding.setExpansion(group ? TableCellBinding.EXPAND_V : TableCellBinding.EXPAND_NONE);

      return binding;
   }

   /**
    * Replace cell binding.
    */
   public void replaceCellBindings(CalcTableVSAssemblyInfo info, int r, int c,
      DataRef ref, boolean isDimension)
   {
      TableLayout layout = info.getTableLayout();

      if(c >= layout.getColCount()) {
         insertCols(info, layout.getColCount(), 1, false);
         c = layout.getColCount() - 1;
      }

      String name = getRefName(ref);
      TableCellBinding binding = createDefalutCellBinding(isDimension, name);
      binding.setBType(isDimension ?
         TableCellBinding.GROUP : TableCellBinding.SUMMARY);

      if(isDimension) {
         binding.setExpansion(TableCellBinding.EXPAND_V);

         if(XSchema.isDateType(ref.getDataType())) {
            if(XSchema.TIME.equals(ref.getDataType())) {
               binding.getOrderInfo(true).setInterval(1, DateRangeRef.HOUR_INTERVAL);
            }
            else {
               binding.getOrderInfo(true).setInterval(1, DateRangeRef.YEAR_INTERVAL);
            }
         }

         LayoutTool.fixDuplicateName(layout, binding);
      }

      if(!isDimension) {
         binding.setFormula(getFormula(ref).getFormulaName());

         if(ref.getRefType() == AbstractDataRef.AGG_CALC) {
            //binding.setExpression(cell.expression);
         }
      }

      setCellBinding(info, r, c, binding);
   }

   private String getRefName(DataRef ref) {
      if(ref instanceof VSAggregateRef) {
         VSAggregateRef agg = (VSAggregateRef) ref;
         return agg.getColumnValue();
      }
      else if(ref instanceof VSDimensionRef) {
         VSDimensionRef dim = (VSDimensionRef) ref;
         return dim.getGroupColumnValue();
      }

      return ref.getName();
   }

   /**
    * Get the default formula for a aggregate.
    */
   private AggregateFormula getFormula(DataRef ref) {
      if(ref == null) {
         return AggregateFormula.COUNT_ALL;
      }

      int rtype = ref.getRefType();
      String name = null;

      if(rtype == AbstractDataRef.AGG_CALC) {
         return AggregateFormula.NONE;
      }

      if(ref instanceof VSAggregateRef) {
         AggregateFormula formula = ((VSAggregateRef)ref).getFormula();
         name = formula == null ? null : formula.getName();

         if(formula != null && !"None".equalsIgnoreCase(name)) {
            return formula;
         }
      }
      else if(ref instanceof ColumnRef) {
         AttributeRef attr = (AttributeRef)((ColumnRef) ref).getDataRef();
         name = attr.getDefaultFormula();

         if(name != null && !"None".equalsIgnoreCase(name)) {
            return AggregateFormula.getFormula(name);
         }
      }

      String dtype = ref.getDataType();

      if(dtype != null && XSchema.isNumericType(dtype)) {
         return AggregateFormula.SUM;
      }

      return AggregateFormula.COUNT_ALL;
   }

   /**
    * Set the cell binding at the specified cell.
    * @param r the really row index in the table lens.
    */
   public void setCellBinding(CalcTableVSAssemblyInfo info, int r, int c,
      TableCellBinding binding)
   {
      TableLayout layout = info.getTableLayout();
      RegionIndex index = layout.getRegionIndex(r);
      Region region = index.getRegion();
      TableCellBinding obinding =
         (TableCellBinding)region.getCellBinding(index.getRow(), c);

      if(binding != null && obinding != null && binding.equals(obinding)) {
         return;
      }

      TableLayout olayout = (TableLayout)layout.clone();
      region.setCellBinding(index.getRow(), c, binding);
      spreadCell(info, r, c);

      // layout change, sync the topn info/ order info
      syncInfo(info, olayout);
   }

   /**
    * Sync topn and orderinfo.
    */
   private void syncInfo(CalcTableVSAssemblyInfo info, TableLayout olayout) {
      TableLayout layout = info.getTableLayout();
      TableCellBinding[] oaggs = getTableCellBindings(olayout, TableCellBinding.SUMMARY);
      TableCellBinding[] groups = getTableCellBindings(layout, TableCellBinding.GROUP);
      TableCellBinding[] naggs = getTableCellBindings(layout, TableCellBinding.SUMMARY);

      for(int i = 0; i < groups.length; i++) {
         TableCellBinding group = groups[i];

         if(group == null) {
            continue;
         }

         OrderInfo order = group.getOrderInfo(true);
         int idx = order.getSortByCol();
         TableCellBinding cell = null;
         boolean find = false;

         // check if sort-by-col exists
         if(idx >= 0) {
            if(idx < oaggs.length) {
               cell = oaggs[idx];
            }

            for(int j = 0; idx < naggs.length && j < naggs.length; j++) {
               if(Tool.equals(cell, naggs[j])) {
                  order.setSortByCol(j);
                  find = true;
                  break;
               }
            }

            if(!find) {
               order.setSortByCol(-1);

               if((order.getOrder() & OrderInfo.SORT_SPECIFIC) ==
                  OrderInfo.SORT_SPECIFIC) {
                  order.setOrder(OrderInfo.SORT_ASC | OrderInfo.SORT_SPECIFIC);
               }
               else {
                  order.setOrder(OrderInfo.SORT_ASC);
               }
            }
         }

         find = false;
         TopNInfo topN = group.getTopN(true);
         idx = topN.getTopNSummaryCol();

         if(idx >= 0 && idx < oaggs.length) {
            cell = oaggs[idx];
         }

         for(int j = 0; idx < naggs.length && j < naggs.length; j++) {
            if(Tool.equals(cell, naggs[j])) {
               topN.setTopNSummaryCol(j);
               find = true;
               break;
            }
         }

         if(!find) {
            topN.setTopN(0);
         }
      }
   }

   private TableCellBinding[] getTableCellBindings(TableLayout layout, int btype) {
      ArrayList<TableCellBinding> cells = new ArrayList<>();
      int rnt = layout.getRowCount();
      int cnt = layout.getColCount();

      for(int r = 0; r < rnt; r++) {
         for(int c = 0; c < cnt; c++) {
            TableCellBinding binding = (TableCellBinding) layout.getCellBinding(r, c);

            if(binding != null && binding.getType() == CellBinding.BIND_COLUMN &&
               binding.getBType() == btype)
            {
               cells.add(binding);
            }
         }
      }

      return cells.toArray(new TableCellBinding[cells.size()]);
   }

   /**
    * Update format info path.
    */
   private void updateFormatPath(CalcTableVSAssemblyInfo info,
      int cnt, int row, boolean add)
   {
      updatePaths(info, cnt, row, add, info.getFormatInfo().getPaths(),
         info.getFormatInfo().getFormatMap());
   }

   /**
    * Update hyperlink path.
    */
   private void updateHyperlinkPath(CalcTableVSAssemblyInfo info,
      int cnt, int row, boolean add)
   {
      if(info.getHyperlinkAttr() != null) {
         updatePaths(info, cnt, row, add,
            info.getHyperlinkAttr().getHyperlinkMap().keySet().toArray(new TableDataPath[0]),
            info.getHyperlinkAttr().getHyperlinkMap());
      }
   }

   /**
    * Update highlight path.
    */
   private void updateHighlightPath(CalcTableVSAssemblyInfo info,
      int cnt, int row, boolean add)
   {
      if(info.getHighlightAttr() != null) {
         updatePaths(info, cnt, row, add,
            info.getHighlightAttr().getHighlightMap().keySet().toArray(new TableDataPath[0]),
            info.getHighlightAttr().getHighlightMap());
      }
   }

   /**
    * Update calc table header row/col count.
    */
   private void updateHeaderCount(CalcTableVSAssemblyInfo info,
      int cnt, int idx, boolean row, boolean append)
   {
      boolean isHeader = row ? idx < info.getHeaderRowCount() :
                                   idx < info.getHeaderColCount();

      if(!isHeader) {
         return;
      }

      cnt = append ? cnt : -cnt;

      if(row) {
         info.setHeaderRowCount(Math.max(0, info.getHeaderRowCount() + cnt));
      }
      else {
         info.setHeaderColCount(Math.max(0, info.getHeaderColCount() + cnt));
      }
   }

   private void updatePaths(CalcTableVSAssemblyInfo info, int cnt,
                            int row, boolean add, TableDataPath[] paths,
                            Map savePathMap)
   {
      boolean isHeader = info.getHeaderRowCount() >= row + 1;

      if(!isHeader) {
         return;
      }

      for(int i = 0; i < paths.length; i++) {
         TableDataPath path = paths[i];

         if(path == null || path.getType() != TableDataPath.DETAIL) {
            continue;
         }

         String[] arr = path.getPath();

         for(int j = 0; j < arr.length; j ++) {
            int l = arr[j].indexOf("[");
            int r = arr[j].indexOf("]");
            int dot = arr[j].indexOf(",");

            if(l < 0 || r < 0 || dot < 0) {
               continue;
            }

            int ridx = Integer.parseInt(arr[j].substring(l + 1, dot));
            int cidx = Integer.parseInt(arr[j].substring(dot + 1, r));
            Object oldValue = null;

            if(savePathMap != null && savePathMap.containsKey(path)) {
               oldValue = savePathMap.get(path);
               savePathMap.remove(path);
            }

            path = (TableDataPath) path.clone();
            path.getPath()[j] = "Cell ["
               + (add ? ridx + cnt : ridx - cnt) + "," + cidx + "]";

            if(oldValue != null) {
               savePathMap.put(path, oldValue);
            }
         }
      }
   }

   private Rectangle getCellSpan(TableLayout layout, int row, int col) {
      for(int i = 0; i < layout.getRowCount(); i++) {
         for(int j = 0; j < layout.getColCount(); j++) {
            Dimension span = layout.getSpan(i, j);

            if(span == null) {
               continue;
            }

            Rectangle rect = new Rectangle(j, i, span.width, span.height);

            if(rect.contains(col, row)) {
               return rect;
            }
         }
      }

      return null;
   }

   private Object[][] copy(CalcTableVSAssemblyInfo info, Rectangle rect) {
      return copy(info, rect, true, false, false);
   }

   private Object[][] copy(CalcTableVSAssemblyInfo info, Rectangle reg, boolean full,
      boolean span, boolean action)
   {
      if(reg.width <= 0 || reg.height <= 0) {
         return new Object[0][0];
      }

      if(action) {
         reg = convertToLayoutRect(info, reg, span);
      }

      TableLayout layout = info.getTableLayout();
      CellInfoHandler[][] vals = new CellInfoHandler[reg.height][reg.width];
      boolean isrow = reg.x == 0 && reg.width == layout.getColCount();
      boolean iscol = reg.y == 0 && reg.height == layout.getRowCount();

      for(int i = reg.y, r = 0; i < reg.y + reg.height; i++, r++) {
         for(int j = reg.x, c = 0; j < reg.x + reg.width; j++, c++) {
            vals[r][c] = new CellInfoHandler(info, i, j, full);

            if(isrow && c == 0) {
               vals[r][c].copyRow(info);
            }

            if(iscol && r == 0) {
               vals[r][c].copyColumn(info);
            }
         }
      }

      return vals;
   }

   public void clear(CalcTableVSAssemblyInfo info, Rectangle rect) {
      clear(info, rect, true, true);
   }

   private void clear(CalcTableVSAssemblyInfo info, Rectangle reg, boolean full,
      boolean cleanSpan)
   {
      TableLayout layout = info.getTableLayout();

      if(reg == null) {
         reg = new Rectangle(0, 0, layout.getColCount(), layout.getRowCount());
      }

      boolean isrow = reg.x == 0 && reg.width == layout.getColCount();
      boolean iscol = reg.y == 0 && reg.height == layout.getRowCount();

      for(int i = reg.y; i < reg.y + reg.height; i++) {
         for(int j = reg.x; j < reg.x + reg.width; j++) {
            CellInfoHandler.clear(info, i, j, full, cleanSpan);

            if(isrow && j == reg.x) {
               CellInfoHandler.clearRow(info, i);
            }

            if(iscol && i == reg.y) {
               CellInfoHandler.clearColumn(info, j);
            }
         }
      }
   }

   private void paste(CalcTableVSAssemblyInfo info, int row, int col,
                      Object[][] values, int nrow, int ncol, boolean full)
   {
      TableLayout layout = info.getTableLayout();

      int rowmax = (nrow >= 0) ? nrow :
         Math.min(values.length, layout.getRowCount() - row);
      rowmax = Math.min(rowmax, layout.getRowCount() - row);

      for(int i = 0; i < rowmax; i++) {
         int colmax = (ncol >= 0) ? ncol :
            Math.min(values[i].length, layout.getColCount() - col);
         colmax = Math.min(colmax, layout.getColCount() - col);

         for(int j = 0; j < colmax; j++) {
            int i2 = i % values.length;
            Object val = values[i2][j % values[i2].length];

            if(val instanceof CellInfoHandler) {
               ((CellInfoHandler) val).set(info, i + row, j + col, full);
            }
         }
      }
   }

   public CalcAggregate[] getCalcAggregateFields(CalcTableVSAssembly calc,
      ColumnSelection selection)
   {
      TableLayout layout = calc.getTableLayout();
      TableCellBinding[] cells = getTableCellBindings(layout, TableCellBinding.SUMMARY);
      ArrayList<AggregateRef> aggs = new ArrayList<>();

      for(int i = 0; i < cells.length; i++) {
         AggregateRef agg = createAggregateField(cells[i], selection);

         if(agg != null) {
            aggs.add(agg);
         }
      }

      ArrayList<AggregateRef> naggs = new ArrayList<>();

      for(int i = 0; i < aggs.size(); i++) {
         DataRef ref = aggs.get(i);
         AggregateRef agg =  aggs.get(i);

         if(ref instanceof CalculateRef && !((CalculateRef)ref).isBaseOnDetail() &&
            !isUsedInSort(aggs, ref, layout) || agg.getDataRef() == null)
         {
            continue;
         }

         boolean found = false;

         for(int j = 0; j < naggs.size(); j++) {
            CalcAggregate nagg = naggs.get(j);

            if(agg.equalsAggregate(nagg)) {
               found = true;
            }
         }

         if(!found && agg.getDataRef() != null) {
            naggs.add(agg);
         }
      }

      return naggs.toArray(new AggregateRef[naggs.size()]);
   }

   private AggregateRef createAggregateField(TableCellBinding bind,
                                             ColumnSelection columns)
   {
      DataRef ref = findAttribute(columns, bind.getValue());

      if(ref instanceof CalculateRef) {
         return null;
      }

      LayoutTool.FormulaStorage finfo = LayoutTool.parseFormula(bind.getFormula());
      AggregateFormula formula =
         AggregateFormula.getFormula(finfo != null ? finfo.formula : "none");

      if("countDistinct".equals(finfo.formula)) {
         formula = AggregateFormula.COUNT_DISTINCT;
      }

      AggregateRef aggr = new AggregateRef(ref, formula);

      if(finfo != null) {
         if(finfo.n != null) {
            aggr.setN(Integer.parseInt(finfo.n));
         }

         if(finfo.secondf != null) {
            aggr.setSecondaryColumn(new AttributeRef(finfo.secondf));
         }
      }

      return aggr;
   }

   private DataRef findAttribute(ColumnSelection cols, String val) {
      if(cols != null) {
         for(int i = 0; i < cols.getAttributeCount(); i++) {
            DataRef ref = cols.getAttribute(i);
            String attr = ref.getAttribute();

            if(Tool.equals(attr, val)) {
               if(ref instanceof ColumnRef && !(ref instanceof CalculateRef)) {
                  return ((ColumnRef) ref).getDataRef();
               }

               return ref;
            }
         }
      }

      return null;
   }

   private Boolean isUsedInSort(ArrayList<AggregateRef> aggs,
                                DataRef ref, TableLayout layout)
   {
      TableCellBinding[] cells= getTableCellBindings(layout, TableCellBinding.GROUP);
      DataRef ref0 = null;

      for(int i = 0; i < cells.length; i++) {
         TableCellBinding cell = (TableCellBinding)cells[i];

         if(cell == null) {
            continue;
         }

         OrderInfo order = cell.getOrderInfo(true);

         if((order.getOrder() == OrderInfo.SORT_VALUE_ASC ||
            order.getOrder() == OrderInfo.SORT_VALUE_DESC) &&
            order.getSortByCol() >= 0)
         {
            ref0 = aggs.get(order.getSortByCol());

            if(Tool.equalsContent(ref0, ref)) {
               return true;
            }
         }

         TopNInfo topN = cell.getTopN(true);

         if(topN.getTopN() > 0 && topN.getTopNSummaryCol() >= 0) {
            ref0 = aggs.get(topN.getTopNSummaryCol());

            if(Tool.equalsContent(ref0, ref)) {
               return true;
            }
         }
      }

      return true;
   }
}
