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
package inetsoft.report.internal;

import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;
import inetsoft.report.*;
import inetsoft.report.filter.*;
import inetsoft.report.internal.binding.*;
import inetsoft.report.internal.table.*;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.BorderColors;
import inetsoft.util.*;
import inetsoft.util.graphics.ImageWrapper;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.io.*;
import java.text.Format;
import java.util.*;

/**
 * Class to paint a actual table region.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class TablePaintable extends BasePaintable {

   /**
    * Create a default paintable
    */
   public TablePaintable() {
      super();
   }

   /**
    * Table painter paints particular region of a table
    * @param reg table region.
    * @param x left-upper corner of the table.
    * @param y left-upper corner of the table.
    * @param lastregion if current region is the last region of the table,
    * this is only meaningful in printlayout mode.
    */
   public TablePaintable(Rectangle reg, float x, float y, Rectangle printBox,
                         float headerW, float headerH, float trailerH,
                         float[] colWidth, float[] rowHeight0,
                         TableLens lens, ReportElement elem,
                         Hashtable sumspan, SpanMap spanmap,
                         Hashtable presenters, Hashtable formats,
                         Insets padding, float[] rowBorderH0,
                         float[] colBorderW, Vector<Rectangle> pgregs,
                         boolean lastregion) {
      super(elem);
      this.reg = reg;
      this.x = x;
      this.y = y;
      this.printBox = new Rectangle(printBox);
      this.headerW = headerW;
      this.headerH = headerH;
      this.trailerH = trailerH;
      this.colWidth = colWidth;
      this.lens = lens;
      this.sumspan = sumspan;
      this.spanmap = spanmap;
      this.padding = padding;
      this.colBorderW = colBorderW;
      this.presenters = presenters;
      this.formats = formats;
       ((BaseElement) elem).getReport();
       this.designtime = false;

      // make a sub-array of the row height
      headerR = lens.getHeaderRowCount();
      headerC = this.lens.getHeaderColCount();
      this.rowHeight = copyRowHeight(rowHeight0);
      this.rowBorderH = copyRowHeight(rowBorderH0);
      // clone it
      this.pgregs = copyPGRegs(pgregs);
      this.lastregion = lastregion;
      TableElementDef telem = (TableElementDef) elem;
      this.binding = telem.getBindingAttr();
      TableLayout layout = telem.getTableLayout();
      this.calc = layout == null ? false : layout.isCalc();

      if(layout != null) {
         this.tableType = layout.isCrosstab() ? "crosstab" : this.tableType;
         this.tableType = layout.isCalc() ? "calc" : this.tableType;
      }

      boolean repeatGroupHeader = false;

      // find all targets, we can not do this in init() because init()
      // is called from the parseXML, which would not have this info

      // @by larryl, when checking for the type of table, use the getFilterTable
      // instead of lens because the filter table may be wrapped in table style
      // or other table filters. Since this is in the constructor, the table
      // element should still be accessible (not swapped out yet)
      TableLens filter = telem.getBaseTable();
      // @by jasons don't add group targets for SortFilter or GroupSortFilter
      // when its base table is a SortFilter. We can't just check GroupedTable
      // since the GroupSortFilter and SummaryFilter needs to be rejected
      if(filter instanceof GroupedTable &&
         filter.getDescriptor().getType() == TableDataDescriptor.GROUPED_TABLE)
      {
         GroupedTable group = (GroupedTable) filter;
         BitSet processed = new BitSet();

         for(int r = reg.y; r < reg.y + reg.height; r++) {
            int r0 = TableTool.getBaseRowIndex(lens, group, r);

            if(r0 < 0 || processed.get(r0)) {
               continue;
            }

            processed.set(r0);
         }
      }

      // build row map if table is split internally
      if(lens instanceof SpanTableLens) {
         boolean mapped = false;

         rowmap = new int[reg.height + headerR];

         for(int i = 0; i < rowmap.length; i++) {
            rowmap[i] = ((SpanTableLens) lens).getBaseRowIndex(
               i < headerR ? i : i + reg.y - headerR);
            mapped = mapped || rowmap[i] != i;
         }

         // if row index is not mapped, discard the row mapping
         if(!mapped) {
            rowmap = null;
         }
      }

      boolean hasDrill = lens.containsDrill();

      if(hasDrill) {
         VariableTable vars = getReportParameters();

         // find all cell specific hyperlinks
         for(int r = 0; r < reg.y + reg.height; r++) {
            if(r >= headerR && r < reg.y) {
               continue;
            }

            for(int c = 0; c < reg.x + reg.width; c++) {
               if(c >= headerC && c < reg.x) {
                  continue;
               }

               // @by davyc, r2 and c2 is lens's base table's row and column value,
               // not the lens itself's row and column
               // fix bug1259292809192
               if(hasDrill) {
                  XDrillInfo dinfo = lens.getXDrillInfo(r, c);
                  getDrillHyperlink(r, c, lens, dinfo, vars);
               }
            }
         }
      }

      // convert a summary table lens to a regular table
      if(!designtime && lens instanceof SummaryTableLens) {
         SummaryTableLens summary = (SummaryTableLens) lens;

         this.lens = new PageSummaryLens(summary, reg);
         this.reg = new Rectangle(reg);
         this.reg.height++;
         this.trailerH = 0;

         float[] nrowHeight = new float[rowHeight.length + 1];
         float[] nrowBorderH = new float[rowBorderH.length + 1];
         System.arraycopy(rowHeight, 0, nrowHeight, 0, rowHeight.length);
         System.arraycopy(rowBorderH, 0, nrowBorderH, 0, rowBorderH.length);

         nrowHeight[nrowHeight.length - 1] = trailerH;
         nrowBorderH[nrowBorderH.length - 1] =
            Common.getLineWidth(summary.getSummaryRowBorder(0));

         rowHeight = nrowHeight;
         rowBorderH = nrowBorderH;
      }

      // find the mapped table for looking on logical row for supDup
      for(TableLens tbl = lens; tbl instanceof TableFilter;
          tbl = ((TableFilter) tbl).getTable())
      {
         if(mappedTable == null && tbl instanceof MappedTableLens) {
            mappedTable = (MappedTableLens) tbl;
         }

         if(perTable == null && tbl instanceof PerRegionTableLens) {
            perTable = (PerRegionTableLens) tbl;
         }
      }

      // @by larryl, for FormatTable, the row border may need to be different
      // depending on the region. This works because the row border is saved
      // in init().
      if(perTable != null) {
         perTable.setRegion(reg);
      }

      // this shouldn't be necessary given the summary table is already
      // converted to regular table, remove in 8.5
      if(lens instanceof SummaryTableLens) {
         summary = (SummaryTableLens) lens;
      }

      if(!designtime) {
         // @by larryl, make sure the table data is not changed
         // after this table paintable is created
         this.lens = new PerPageTableLens(this.lens);
         // @by billh, if a null object at the first row, we will check
         // whether it is a group header but not at the first row.
         // For this case, find the object at the first row to show
         TableDataDescriptor desc = lens.getDescriptor();
         TableLens filter2 = telem.getBaseTable();

         if(repeatGroupHeader && filter2 instanceof GroupedTable &&
            (desc.getType() == TableDataDescriptor.GROUPED_TABLE) &&
            reg.height > 0)
         {
            GroupedTable grouped = (GroupedTable) filter2;
            ColumnIndexMap columnIndexMap = new ColumnIndexMap(grouped, true);

            for(int i = 0; i < reg.width; i++) {
               if(lens.getObject(reg.y, reg.x + i) != null) {
                  Rectangle span = spanmap.get(reg.y, reg.x + i);

                  // contains span? this object will be ignored, just ignore it
                  // fix bug1290138903159
                  if(span == null || span.y >= 0) {
                     continue;
                  }
               }

               if(reg.x + i >= grouped.getColCount()) {
                  continue;
               }

               // convert row/column to grouped row/column, for a non-freehand
               // table, its row and column will match the base group filter,
               // for freehand, we need to convert row and column to the group
               // filter's row and column
               int col = reg.x + i;
               int row = reg.y;

               // @by davyc, for each cell, repeat group value, the group cell
               // will be found from current row in layout to the first row in
               // layout, when any cell find with group binding, we will use
               // the group as the repeated group column
               int bcol = TableTool.getBaseGroupColIndex(
                  telem, lens, grouped, columnIndexMap, row, col);
               int brow = TableTool.getBaseRowIndex(lens, grouped, row);

               if(bcol < 0 || brow < 0) {
                  continue;
               }

               int level = grouped.getGroupColLevel(bcol);

               if(level >= 0) {
                  // really start group row
                  int r = grouped.getGroupFirstRow(brow, level);

                  if(r >= 0) {
                     // @by davyc, the row is grouped table's row, it is not
                     // same as lens' row, fix bug1279258834453
                     // Object obj = this.lens.getObject(r, reg.x + i);
                     Object obj = null;

                     // @by davyc, find correct row in lens whose base row is
                     // same as the r in the grouped table, so when get object,
                     // format and value will be same as the group value in the
                     // lens, for example, when apply freehand, the group value
                     // may be not same as the group filter's group value, so
                     // we need to apply the correct group value in the lens
                     // optimize
                     // feature1208260717532
                     if(TableTool.getBaseRowIndex(lens, grouped, r) == r) {
                        row = r;
                     }
                     // this is/must be freehand? try the path
                     else {
                        row = TableTool.getGroupRowIndex(telem, lens, grouped,
                                                         row, col, r);
                     }

                     obj = null;
                     int align;
                     Font font;
                     boolean wrap;
                     Color fcolor;
                     Color bcolor;

                     // column and row are all avaliable?
                     if(row >= 0) {
                        obj = lens.getObject(row, col);
                        align = lens.getAlignment(row, col);
                        font = lens.getFont(row, col);
                        wrap = lens.isLineWrap(row, col);
                        fcolor = lens.getForeground(row, col);
                        bcolor = lens.getBackground(row, col);
                     }
                     // back value
                     else {
                        obj = grouped.getObject(r, bcol);
                        align = grouped.getAlignment(r, bcol);
                        font = grouped.getFont(r, bcol);
                        wrap = grouped.isLineWrap(r, bcol);
                        fcolor = grouped.getForeground(r, bcol);
                        bcolor = grouped.getBackground(r, bcol);
                     }

                     if(obj != null) {
                        PerPageTableLens plens = (PerPageTableLens) this.lens;
                        plens.setObject(reg.y, reg.x + i, obj);
                        plens.setAlignment(reg.y, reg.x + i, align);
                        plens.setFont(reg.y, reg.x + i, font);
                        plens.setLineWrap(reg.y, reg.x + i, wrap);
                        plens.setForeground(reg.y, reg.x + i, fcolor);
                        plens.setBackground(reg.y, reg.x + i, bcolor);
                        Rectangle span = spanmap.get(reg.y, reg.x + i);

                        if(span != null && span.y < 0) {
                           span.y = 0;
                        }

                        groupMap.put((reg.x + i), span);
                     }
                  }
               }
            }
         }
      }

      refreshLastCol(); // for printlayout mode.
      init();
   }

   /**
    * get autodril hyperlink on special row and col.
    * @param r is the row of lens.
    * @param c is the column of lens.
    * @param lens is the tablelens.
    * @param dinfo is the drill information.
    */
   private void getDrillHyperlink(int r, int c, TableLens lens,
      XDrillInfo dinfo, VariableTable vars)
   {
      if(dinfo == null || lens == null) {
         return;
      }

      TableLens dataTable = lens instanceof TableFilter ?
         Util.getDataTable((TableFilter) lens) : lens;
      ColumnIndexMap columnIndexMap = new ColumnIndexMap(dataTable, true);
      int dr = TableTool.getBaseRowIndex(lens, dataTable, r);
      int dc = TableTool.getBaseColIndex(lens, dataTable, c);

      if(dr < 0 || dc < 0) {
         return;
      }

      int size = dinfo.getDrillPathCount();
      Hyperlink.Ref[] refs = new Hyperlink.Ref[size];
      DataRef dcol = dinfo.getColumn();

      for(int k = 0; k < size; k++) {
         DrillPath path = dinfo.getDrillPath(k);
         // @by billh, here we should use (r2, c2) rather than (r, c),
         // for (r2, c2) are from dataTable, and (r, c) are from lens
         // @by davyc, r2 and c2 is not actually dataTable's row column
         refs[k] = new Hyperlink.Ref(path, dataTable, dr, dc);
         DrillSubQuery query = path.getQuery();

         if(query != null) {
            refs[k].setParameter(StyleConstants.SUB_QUERY_PARAM,
                                 dataTable.getObject(r, c));
            int type = dataTable.getDescriptor().getType();
            String queryParam = null;

            if(type == TableDataDescriptor.CROSSTAB_TABLE) {
               CrossFilter crosstab = Util.getCrossFilter(dataTable);
               dr = TableTool.getBaseRowIndex(dataTable, crosstab, dr);
               dc = TableTool.getBaseColIndex(lens, dataTable, c);

               // dr, dc < 0, data is error, so set to 0 is meaningless
               if(dr < 0 || dc < 0) {
                  continue;
               }

               OrderedMap map = new OrderedMap();
               map = (OrderedMap) crosstab.getKeyValuePairs(dr, dc, map);
               Object key = (map.size() > 0) ? map.getKey(map.size() - 1) : null;
               String iden = key == null ? null : key.toString();
               iden = findIdentifierForSubQuery(crosstab, iden);
               key = iden == null ? key : iden;
               Object val = (map.size() > 0) ? map.getValue(map.size()-1) : null;

               if(dcol != null) {
                  queryParam = Util.findSubqueryVariable(query, dcol.getName());
               }

               if(queryParam == null) {
                  String tableHeader = key == null ? null : key.toString();
                  queryParam = Util.findSubqueryVariable(query, tableHeader);
               }

               if(queryParam != null) {
                  refs[k].setParameter(Hyperlink.getSubQueryParamVar(queryParam), val);
               }

               Iterator<String> it = query.getParameterNames();

               while(it.hasNext()) {
                  String qvar = it.next();

                  if(Tool.equals(qvar, queryParam)) {
                     continue;
                  }

                  String header = query.getParameter(qvar);
                  Map map0 = new OrderedMap();
                  map0 = crosstab.getKeyValuePairs(dr, dc, map0);
                  Object val0 = map0.get(header);

                  if(val0 == null) {
                     TableLens drilllens =
                        ParamTableLens.create(map0, crosstab);
                     int cidx = ((ParamTableLens) drilllens).findColumn(header);

                     if(cidx < 0 || Tool.equals(qvar, queryParam)) {
                        continue;
                     }

                     val0 = drilllens.getObject(1, cidx);
                  }

                  if(val0 != null) {
                     refs[k].setParameter(Hyperlink.getSubQueryParamVar(qvar), val0);
                  }
               }
            }
            else {
               if(dcol != null) {
                  queryParam = Util.findSubqueryVariable(query, dcol.getName());
               }

               if(queryParam == null) {
                  String iden = dataTable.getColumnIdentifier(dc);
                  iden = iden == null ? (String) Util.getHeader(dataTable, dc) :
                                        iden;
                  iden = findIdentifierForSubQuery(dataTable, iden);
                  queryParam = Util.findSubqueryVariable(query, iden);
               }

               if(queryParam != null) {
                  refs[k].setParameter(Hyperlink.getSubQueryParamVar(queryParam),
                     dataTable.getObject(dr, dc));
               }

               Iterator<String> it = query.getParameterNames();

               while(it.hasNext()) {
                  String qvar = it.next();

                  if(Tool.equals(qvar, queryParam)) {
                     continue;
                  }

                  String header = query.getParameter(qvar);
                  int col = Util.findColumn(columnIndexMap, header);

                  if(col < 0) {
                     continue;
                  }

                  refs[k].setParameter(Hyperlink.getSubQueryParamVar(qvar), getValue(dataTable, r, col));
               }
            }
         }

         Util.mergeParameters(refs[k], vars);
         Util.updateLink(refs[k]);
      }

      // @by davyc, take care, when we get hyperlink or drill links, the row
      // and column are all base to current paintable's table lens, so
      // here we also should set to the paintable's table lens' row and column,
      // but not the data table's row and column, they are not same
      // fix bug1260945245043
      setDrillHyperlinks(r, c, refs);
   }

   private String findIdentifierForSubQuery(TableLens lens, String key) {
      if(key == null) {
         return null;
      }

      if(idenMap != null && idenMap.containsKey(key)) {
         return idenMap.get(key);
      }

      if(idenMap == null) {
         idenMap = new HashMap<>();
      }

      String iden = Util.findIdentifierForSubQuery(lens, key);
      idenMap.put(key, iden);
      return iden;
   }

   /**
    * Check whether a point is in a table's region.
    */
   public boolean isInRegion(int x, int y) {
      Rectangle reg = getBaseTableRegion();
      TableLens tbl = getTable();

      return
         (x < tbl.getHeaderColCount() || x >= reg.x && x <= reg.x + reg.width)
         &&
         (y < tbl.getHeaderRowCount() || y >= reg.y && y <= reg.y + reg.height);
   }

   /**
    * Get the row index in the base table. The row index used in Paintable
    * may be changed if a table is split using a SpanTableLens to handle
    * large cells. This method can be used to get the real row index in the
    * original table lens.
    * @deprecated this method seems not correct, should use getBaseRow instead.
    */
   @Deprecated
   public int getBaseRowIndex(int row) {
      if(rowmap == null || row >= rowmap.length || row < 0) {
         return row;
      }

      return rowmap[row];
   }

   /**
    * Get the row index in the base table. The row index used in Paintable
    * may be changed if a table is split using a SpanTableLens to handle
    * large cells. This method can be used to get the real row index in the
    * original table lens.
    */
   public int getBaseRow(int row) {
      int r2 = rowmap != null && row >= reg.y ? row - reg.y + headerR : row;
      return getBaseRowIndex(r2);
   }

   /**
    * Get the table region in this paintable, mapped to the original table.
    */
   public Rectangle getBaseTableRegion() {
      Rectangle reg2 = new Rectangle(reg);

      if(rowmap != null) {
         reg2.y = getBaseRowIndex(headerR);
         reg2.height = getBaseRowIndex(headerR + reg.height - 1) - reg2.y + 1;
      }

      return reg2;
   }

   /**
    * Initialize, also called from serialization
    */
   public void init() {
      // convert to the first row/column of the span in the page
      this.pgregs2 = pgregs == null ? new Vector<>() : new Vector<>(pgregs);
      pgregs2.add(this.reg);

      repeatCell = !"false".equalsIgnoreCase(
         SreeEnv.getProperty("table.mergedcell.repeat"));
      printed = false;
      pointlocmap = new Hashtable();
      headerR = lens.getHeaderRowCount();
      headerC = lens.getHeaderColCount();

      // calculate the pixel width of table region
      width = headerW;

      for(int i = reg.x; i < reg.x + reg.width; i++) {
         width += colWidth[i];
      }

      // calculate the pixel height of table region
      height = headerH + trailerH;

      for(int i = reg.y; i < reg.y + reg.height; i++) {
         height += getRowHeight(i);
      }

      // box is the area to paint table region
      box = new Bounds(0, printBox.y + y, width, height);

      // build the map where the borders exists
      // hor[row][col] is true if border should be drawn below the cell
      // ver[row][col] is true if border should be drawn at right of cell
      summaryR = (summary != null) ? 1 : 0;
      hor =
         new int[headerR + reg.height + summaryR + 2][headerC + reg.width + 2];
      ver =
         new int[headerR + reg.height + summaryR + 2][headerC + reg.width + 2];

      linescache = new HashCache();

      cellbounds = new Bounds[headerR + reg.height + summaryR][headerC + reg.width];

      // adjust alignment
      box.x = printBox.x + (float) elem.getIndent() * 72 + 1;

      // @by larryl, optimization, since the spanmap contains the span setting
      // for the entire table, we mark which span is necessary for this
      // paintable (for the span information accessed in init()), and then
      // trim it so only the necessary span info is kept in this paintable.
      spanmap = (SpanMap) this.spanmap.clone();
      spanmap.mark();

      // previous visible row index
      int preVisRow = -1;
      // current row is invisible
      boolean rowInVisible = false;

      Insets tableBorders = elem instanceof TableElementDef ? ((TableElementDef) elem).getBorders()
         : new Insets(0, 0, 0, 0);
      tableBorders = tableBorders == null ? new Insets(0, 0, 0, 0) : tableBorders;

      // populate the matrix of border types
      for(int i = 0; i < hor.length - 1; i++) {
         int row = (i <= headerR) ? (i - 1) : (i - headerR + reg.y - 1);
         boolean isSummary = i == hor.length - 2 && summaryR == 1;
         // don't round so fractional height won't cause lines to not line up. (62585, 62467)
         float rowH = (isSummary || row < 0) ? trailerH : getRowHeight(row);
         // invisible row?
         rowInVisible = row >= 0 && rowH == 0;

         for(int j = 0; j < hor[i].length - 1; j++) {
            int col = (j <= headerC) ? (j - 1) : (j - headerC + reg.x - 1);
            Rectangle area = null;

            // check if this is summary row
            if(isSummary) {
               area = (Rectangle) sumspan.get(col);
            }
            else {
               area = getSpan(row, col);
            }

            if(area != null) {
               // @by larryl, by force all cells of a span cell to share the
               // same border setting of the top-left corner cell, it makes
               // impossible to support controlling of individual cell borders
               // next to the span cell. This logic is already properly
               // implemented in the TableFormatAttr so that the merging of the
               // bottom/top and right/left takes into account of the span and
               // cell next to it. There is a potential for serious backward
               // compatibility here that we may need address by explicitly
               // applying border setting for span cells if the borders and
               // spans are returned directly from the program supplied table
               // lens or set through script.
               /*
               // at top left corner of span cell
               boolean topcorner = (area.y == 0 || i <= headerR + 1) &&
                  (area.x == 0 || j <= headerC + 1 && col + area.x >= headerC);

               // not the left-upper cell of span area
               if(!topcorner) {
                  continue;
               }
               */

               int borderRow = row;
               int borderCol = col;

               if(row < lens.getHeaderRowCount()) {
                  borderRow += area.y;
               }

               if(col < lens.getHeaderColCount()) {
                  borderCol += area.x;
               }

               int rowborder = isSummary ? summary.getSummaryRowBorder(col)
                  : lens.getRowBorder(borderRow, borderCol);
               int colborder = isSummary ? summary.getSummaryColBorder(col)
                  : lens.getColBorder(borderRow, borderCol);

               // @by larryl, this loop basically serves to clear out the inner
               // borders in the span cell. It really only needs to be done
               // once for the top-left cell. But determining the boundary
               // is tricky since a span cell could be broken in to pieces
               // across page. This is a potential optimization spot.
               for(int n = 0; n < area.height && i + n < hor.length - 1; n++) {
                  for(int m = 0; m < area.width && j + m < hor[i].length - 1; m++) {
                     hor[i + n][j + m] = (n == area.height - 1 ||
                        (i + n) == hor.length - 2)
                        ? rowborder : StyleConstants.NO_BORDER;

                     // in printlayout mode, don't paint left border of the
                     // first column but only show table's left border.
                     ver[i + n][j + m] = (j == 0 && tableBorders.left != 0) ?
                        StyleConstants.NO_BORDER : (m == area.width - 1 ||
                        (j + m) == hor[i].length - 2) ? colborder :
                        StyleConstants.NO_BORDER;
                  }
               }
            }
            else {
               hor[i][j] = (j == 0) ? StyleConstants.NO_BORDER :
                  (isSummary ? summary.getSummaryRowBorder(col) :
                   lens.getRowBorder(row, col));
               // in printlayout mode, don't paint left border of the
               // first column but only show table's left border.
               ver[i][j] = (j == 0 && tableBorders.left != 0) ?
                  StyleConstants.NO_BORDER : (i == 0) ?
                  StyleConstants.NO_BORDER : (isSummary ?
                  summary.getSummaryColBorder(col) :
                  lens.getColBorder(row, col));
            }

            // current row is visible?
            if(!rowInVisible && !designtime) {
               // there is invisible row between the two rows
               if(preVisRow >= 0 && preVisRow < i - 1) {
                  // is same span? ignore it
                  if(area != null && i + area.y >= preVisRow) {
                     continue;
                  }

                  mergeBorder(hor, preVisRow, j, i);
               }
            }
         }

         if(!rowInVisible) {
            preVisRow = i;
         }
      }

      // @by larryl, trim and remove the unused spans. Entries are marked by
      // the spanmap.get()
      spanmap.trim();

      // if the region is at the right of the edge, use the border
      // setting of the right most column
      int rcol = hor[0].length - 2;
      boolean hideLeftBorder = isContentMatchTableWidth();

      for(int i = 1; i < hor.length - 1; i++) {
         // in printlayout mode, if content width is match the table
         // width, don't paint the right border of the last column but
         // only show table's right border.
         if(hideLeftBorder && tableBorders.right != 0) {
            ver[i][rcol] = StyleConstants.NO_BORDER;
         }
         else {
            int row = (i <= headerR) ? (i - 1) : (i - headerR + reg.y - 1);
            int s = lens.getColBorder(row, lens.getColCount() - 1);
            ver[i][rcol] = Common.mergeLineStyle(ver[i][rcol], s);
         }
      }

      // if the region is at the bottom of the edge, use the border
      // setting of the bottom most column
      if(summaryR == 0 && lens.getRowCount() > 0) {
         int brow = hor.length - 2;

         for(int j = 1; j < hor[0].length - 1; j++) {
            // in printlayout mode, don't paint the bottom border
            // of the last row border but only show table's bottom border.
            if(lastregion && tableBorders.bottom != 0) {
               hor[brow][j] = 0;
            }
            else {
               int col = (j <= headerC) ? (j - 1) : (j - headerC + reg.x - 1);
               int s = lens.getRowBorder(lens.getRowCount() - 1, col);
               hor[brow][j] = Common.mergeLineStyle(hor[brow][j], s);
            }
         }
      }
   }

   /**
    * Get last invisible row.
    */
   private void mergeBorder(int[][] hor, int i, int j, int firstVisibleRow) {
      if(hor[i][j] == StyleConstants.NO_BORDER) {
         for(int r = firstVisibleRow - 1; r >= i; r--) {
            if(hor[r][j] != StyleConstants.NO_BORDER) {
               hor[i][j] = hor[r][j];
               return;
            }
         }
      }
   }

   /**
    * Get the height of this table region.
    */
   public float getHeight() {
      return height;
   }

   /**
    * Get the width of a column in points.
    */
   public float getColWidth(int col) {
      return colWidth[col];
   }

   /**
    * Get the border width of a column in points.
    */
   public float getColBorderWidth(int col) {
      return col < 0 || col >= colBorderW.length ? 0 : colBorderW[col];
   }

   /**
    * Get x adjust.
    */
   public float getXAdjust(int row, int col) {
      int lastcol = (col == reg.x) ? (headerC - 1) : (col - 1);
      return Common.getLineWidth(lens.getColBorder(row, lastcol));
   }

   /**
    * Get y adjust.
    */
   public float getYAdjust(int row, int col) {
      int lastrow = (row == reg.y) ? (headerR - 1) : (row - 1);
      return Common.getLineWidth(lens.getRowBorder(lastrow, col));
   }

   /**
    * Paint the region.
    */
   @Override
   public void paint(Graphics g) {
      printed = true;
      Rectangle prArea = g.getClipBounds();
      float w2 = Common.getLineAdjustment(g);
      Color oc = g.getColor();
      Font of = g.getFont();
      Color efg = getElement().getForeground();

      // @by larryl, default to black otherwise the previous color setting on
      // different elements may be carried over
      if(efg == null) {
         efg = Color.black;
      }

      if(perTable != null) {
         perTable.setRegion(getTableRegion());
      }

      // preprocess for currency alignment
      pointlocmap.clear();

      for(int i = 0; i < hor.length - 2; i++) {
         int row = (i < headerR) ? i : (i - headerR + reg.y);
         boolean isSummary = i == hor.length - 3 && summaryR == 1;

         for(int j = 0; j < hor[i].length - 2; j++) {
            int col = (j < headerC) ? j : (j - headerC + reg.x);
            Object obj = isSummary ?
               summary.getSummary(col, reg.y, reg.height) :
               lens.getObject(row, col);

            int align = isSummary ?
               summary.getSummaryAlignment(col) :
               lens.getAlignment(row, col);
            Rectangle area = isSummary ? (Rectangle) sumspan.get(col) : getSpan(row, col);

            // if span cell, only paint if at upper-left corner
            // cell or the first cell of the region
            if(area == null ||
               ((area.x == 0 || j == headerC && col + area.x >= headerC) &&
               (area.y == 0 || i == headerR && row + area.y >= headerR)))
            {
               // paint cell content
               Presenter pr = (obj == null) ? null :
                  StyleCore.getPresenter(presenters, obj.getClass());

               if((pr != null) || (obj == null) || (obj instanceof Component) ||
                  (obj instanceof Image) || (obj instanceof Painter))
               {
               }
               else {
                  // look for format of the object
                  obj = formatObject(obj);

                  // preprocess text and setup cache
                  String str = Tool.toString(obj);

                  if((align & StyleConstants.H_CURRENCY) != 0) {
                     Font fn = lens.getFont(row, col);

                     fn = (fn != null) ? fn : elem.getFont();

                     float right = preProcessTextAlign(str, fn);
                     Float rmax = (Float) pointlocmap.get(col);

                     if(rmax == null || rmax.floatValue() < right) {
                        pointlocmap.put(col, right);
                     }
                  }
               }
            }
         }
      }

      // print contents
      content_loop:
      for(int i = 0; i < hor.length - 2; i++) {
         int row = (i < headerR) ? i : (i - headerR + reg.y);
         boolean isSummary = i == hor.length - 3 && summaryR == 1;

         for(int j = 0; j < hor[i].length - 2; j++) {
            int col = (j < headerC) ? j : (j - headerC + reg.x);
            int vrow = row, vcol = col; // adjusted for span
            Color orig = g.getColor();
            boolean wrap = isSummary ?
               summary.isSummaryLineWrap(col) :
               lens.isLineWrap(row, col);
            Rectangle area = isSummary ?
               (Rectangle) sumspan.get(col) :
               getSpan(row, col);

            float xadj = getXAdjust(row, col);
            float yadj = getYAdjust(row, col);

            // if span cell, only paint if at upper-left corner
            // cell or the first cell of the region
            if(area == null ||
               ((area.x == 0 || j == headerC && col + area.x >= headerC) &&
               (area.y == 0 || i == headerR && row + area.y >= headerR)))
            {
               // paint cell content
               Bounds bounds = getCellBounds(row, col, isSummary);

               // not imageable, skip
               if(prArea != null && bounds.y > prArea.y + prArea.height) {
                  break content_loop;
               }
               // top of clip area, continue to next cell (maybe span)
               else if(prArea != null && bounds.y + bounds.height < prArea.y) {
                  // we can not continue to next row if next cell is
                  // span, it may still need to be printed
                  continue;
               }

               Shape oclip = null;
               Bounds cclip = null; // span cell clip

               // span cell, make sure only visible areas are painted
               if(area != null) {
                  vrow = row + area.y;
                  vcol = col + area.x;
                  cclip = getCellClip(row, col, area, bounds, xadj, yadj,
                                      isSummary);

                  oclip = g.getClip();
                  Common.clipRect(g, cclip);
               }

               Object obj = isSummary ?
                  summary.getSummary(vcol, reg.y, reg.height) :
                  getValue(vrow, vcol);
               Color bg = isSummary ?
                  summary.getSummaryBackground(col) :
                  lens.getBackground(vrow, vcol);

               // if bg not defined for a cell, use element background
               if(bg == null && elem instanceof TableElementDef) {
                  bg = elem.getBackground();
               }

               if(bg != null && bounds.width > 0) {
                  g.setColor(bg);
                  // @by davyc, add one pixel to fill the rect fully.
                  // fix bug1279665730090
                  Common.fillRect(g, bounds.x - 0.5f, bounds.y,
                                  bounds.width + 1, bounds.height);
                  g.setColor(orig);
               }

               // get the color and font attributes
               Graphics2D g2d = (Graphics2D) g;
               g2d.setBackground(bg);

               Color fg = isSummary ?
                  summary.getSummaryForeground(col) :
                  lens.getForeground(vrow, vcol);

               g.setColor((fg != null) ? fg : efg);

               Font fn = isSummary ?
                  summary.getSummaryFont(col) :
                  lens.getFont(vrow, vcol);

               g.setFont((fn != null) ? fn : elem.getFont());

               FontMetrics fm = Common.getFractionalFontMetrics(g.getFont());

               // not empty cell
               if(obj != null) {
                  int align = getAlignment(vrow, vcol);
                  Insets insets = isSummary ? summary.getSummaryInsets(col) :
                     lens.getInsets(vrow, vcol);

                  // adjust cell insets
                  if(insets != null) {
                     bounds.x += insets.left;
                     bounds.y += insets.top;
                     bounds.width -= insets.left + insets.right;
                     bounds.height -= insets.top + insets.bottom;
                  }

                  // check if a presenter is registered with object
                  Presenter pr = StyleCore.getPresenter(presenters, obj.getClass());

                  if(pr != null) {
                     Dimension psize = pr.getPreferredSize(obj);
                     Bounds b = bounds;

                     if(!pr.isFill()) {
                        b = Common.alignCell(bounds, new Size(psize), align);
                     }

                     pr.setFont(g.getFont());
                     pr.setBackground(bg);

                     fireEvent(pr, new Rectangle((int) b.x, (int) b.y,
                        (int) b.width, (int) b.height), "start", obj, row, col);
                     pr.paint(g, obj, (int) b.x, (int) b.y, (int) b.width,
                              (int) b.height);
                     fireEvent(null, null, "end", null, row, col);
                  }
                  else if(obj instanceof Component) {
                     Component comp = (Component) obj;
                     Dimension psize = comp.getSize();
                     Bounds b = Common.alignCell(bounds, new Size(psize), align);
                     Shape clip = g.getClip();

                     Common.clipRect(g, b);
                     g.translate((int) b.x, (int) b.y);
                     fireEvent(comp, g.getClipBounds(), "start", null, row, col);
                     comp.printAll(g);
                     fireEvent(null, null, "end", null, row, col);
                     g.translate(-(int) b.x, -(int) b.y);
                     g.setClip(clip);
                  }
                  else if(obj instanceof Image) {
                     Image img = (Image) obj;
                     Dimension psize = new Dimension(img.getWidth(null), img.getHeight(null));
                     Bounds b = Common.alignCell(bounds, new Size(psize), align);
                     fireEvent(img, new Rectangle((int) b.x, (int) b.y,
                        (int) b.width, (int) b.height), "start", img, row, col);
                     Common.drawImage(g, img, b.x, b.y, b.width, b.height, null);
                     fireEvent(null, null, "end", null, row, col);
                  }
                  else if(obj instanceof Painter) {
                     Painter painter = (Painter) obj;
                     boolean fill = false;
                     Bounds b = bounds;

                     if(painter instanceof PresenterPainter) {
                        pr = ((PresenterPainter) painter).getPresenter();

                        pr.setFont(g.getFont());
                        pr.setBackground(bg);
                        fill = pr.isFill();
                     }

                     Dimension psize = painter.getPreferredSize();

                     if(!fill) {
                        b = Common.alignCell(bounds, new Size(psize), align);
                     }

                     float offsetY = (cclip != null) ? cclip.y - bounds.y : 0;
                     float bufh = (cclip != null) ? cclip.height : -1;

                     fireEvent(painter, new Rectangle((int) b.x, (int) b.y,
                        (int) b.width, (int) b.height), "start",
                        new Size(offsetY, bufh), row, col);
                     Common.paint(g, b.x, b.y, b.width, b.height, painter, 0,
                                  0, b.width, b.height, b.width, b.height,
                                  fg, bg, offsetY, bufh);
                     fireEvent(null, null, "end", null, row, col);
                  }
                  else if(obj != null) {
                     CellText info = getCellText(row, col, obj, bounds, align,
                                                 wrap, g.getFont(), fm, area,
                                                 cclip, xadj, yadj);

                     if(info != null) {
                        if(info.clip != null) {
                           Common.clipRect(g, info.clip);
                        }

                        fireEvent(
                           info, new Rectangle((int) info.bounds.x,
                           (int) info.bounds.y, (int) info.bounds.width,
                           (int) info.bounds.height), "startText",
                           new Size(info.bounds.width, info.bounds.height),
                           row, col);

                        if(!paintNotationText(g, info, bounds)) {
                           Common.paintText(g, info.text, info.lines, bounds,
                                            info.bounds, false, info.lineoff,
                                            elem.getSpacing(), true);
                        }

                        fireEvent(null, null, "endText", null, row, col);
                     }
                  }

                  if(designtime && calc) {
                     paintDesignCell((Graphics2D) g, row, col, bounds);
                  }
               }

               g.setColor(orig);

               if(oclip != null) {
                  g.setClip(oclip);
               }
            }
         }
      }

      boolean borderEventFired = false;

      // print the borders
      float x = box.x;
      float y = box.y;
      float ix = x;
      float iy = y;

      // print borders
      for(int i = 0; i < hor.length - 1; i++) {
         int row = (i <= headerR) ? (i - 1) : (i - headerR + reg.y - 1);
         boolean isSummary = i == hor.length - 2 && summaryR == 1;
         // don't round so fractional height won't cause lines to not line up. (62585, 62467)
         float rowH = (isSummary || row < 0) ? trailerH : getRowHeight(row);
         float tiy = iy - rowH; // top y pos

         x = box.x;
         ix = x;

         // if outside of clip area, skip
         // this is primarily for printing in jdk1.2, where a page
         // is printed ~10 scan lines at a time
         if(prArea != null && y - rowH > prArea.y + prArea.height) {
            break;
         }

         // page break ignored at design time, draw a dash line to indicate it. (50182)
         if((hor[i][1] & TableLens.BREAK_BORDER) != 0 && designtime) {
            g.setColor(Color.gray);
            Common.drawHLine(g, iy, 0, x, StyleConstants.DASH_LINE, 0, 0);
         }

         // if inside clip area
         // @by larryl, hide row if row height set to 0
         if(!(row >= 0 && rowH == 0) && (prArea == null || y + 5 > prArea.y)) {
            for(int j = 0; j < hor[i].length - 1; j++) {
               int col = (j <= headerC) ? (j - 1) : (j - headerC + reg.x - 1);
               int h = hor[i][j];
               int v = ver[i][j];
               Color rowcolor = isSummary ?
                  summary.getSummaryRowBorderColor(col) :
                  lens.getRowBorderColor(row, col);
               Color colcolor = isSummary ?
                  summary.getSummaryColBorderColor(col) :
                  lens.getColBorderColor(row, col);

               if(i == hor.length - 2 && !isSummary && lens.getRowCount() > 0) {
                  Color bcolor = lens.getRowBorderColor(lens.getRowCount() - 1,
                                                        col);
                  rowcolor = Util.mergeColor(rowcolor, bcolor);
               }

               // vertical line only exists after first row
               if(i > 0) {
                  g.setColor((colcolor != null) ? colcolor : efg);

                  if(v != StyleConstants.NO_BORDER) {
                     if(!borderEventFired) {
                        borderEventFired = true;
                        fireEvent(null, null, "startBorders", null, -1, -1);
                     }

                     // @by billh, fix customer bug: bug1302633902621
                     // do not leave a hole in cross borders
                     float adj = j > 0 && h != StyleConstants.NO_BORDER ?
                        Common.getLineWidth(h) : 0;
                     // @by davyc, same as corner logic
                     adj = (adj > 1) ? adj - 0.5f : adj / 2f;
                     Common.drawVLine(g, ix + w2, tiy, iy + adj, v,
                                      hor[i - 1][j], hor[i - 1][j + 1]);

                     // fix corner, left-lower
                     if(j == 0 && i == hor.length - 2 &&
                        hor[i][1] != StyleConstants.NO_BORDER)
                     {
                        float yadj = Common.getLineWidth(hor[i][1]);
                        yadj = (yadj > 1) ? yadj - 0.5f : yadj / 2f;

                        Common.drawVLine(g, ix + w2, iy, iy + yadj, v,
                                         StyleConstants.NO_BORDER, hor[i][1]);
                     }
                  }
                  // draw shade if in designer
                  else if(designtime) {
                     if(!borderEventFired) {
                        borderEventFired = true;
                        fireEvent(null, null, "startBorders", null, -1, -1);
                     }

                     g.setColor(Color.LIGHT_GRAY);
                     Common.drawVLine(g, ix + w2, tiy, iy,
                        StyleConstants.THIN_LINE, hor[i - 1][j],
                        hor[i - 1][j + 1]);
                  }
               }

               // horizontal line only exists after first column
               if(j > 0) {
                  g.setColor((rowcolor != null) ? rowcolor : efg);

                  if(h != StyleConstants.NO_BORDER) {
                     // @by larryl, if the column width is 0, it is hidden
                     // and don't draw the h line
                     if(colWidth[col] > 0) {
                        if(!borderEventFired) {
                           borderEventFired = true;
                           fireEvent(null, null, "startBorders", null, -1, -1);
                        }

                        // Gop.drawHLine doesn't make the adjustment for solid line
                        // (see Gop.drawHLine adj value). (62467)
                        /*
                        float adj = Math.min(Common.getLineWidth(ver[i][j - 1]),
                           Common.getLineWidth(ver[i + 1][j - 1]));
                        float x0 = (h & StyleConstants.SOLID_MASK) != 0 ?
                                   x - colWidth[col] + adj : x - colWidth[col];
                        */
                        float x0 = x - colWidth[col];

                        Common.drawHLine(g, iy + w2, x0, ix, h,
                                         ver[i][j - 1], ver[i + 1][j - 1]);
                     }

                     // fix corner, right-upper
                     if(i == 0 && j == hor[i].length - 2 &&
                        ver[1][j] != StyleConstants.NO_BORDER)
                     {
                        if(!borderEventFired) {
                           borderEventFired = true;
                           fireEvent(null, null, "startBorders", null, -1, -1);
                        }

                        float xadj = Common.getLineWidth(ver[1][j]);
                        xadj = (xadj > 1) ? xadj - 0.5f : xadj / 2f;

                        Common.drawHLine(g, iy + w2, ix, ix + xadj,
                                         h, StyleConstants.NO_BORDER,ver[1][j]);
                     }
                  }
                  // draw shade if in designer
                  else if(designtime) {
                     if(!borderEventFired) {
                        borderEventFired = true;
                        fireEvent(null, null, "startBorders", null, -1, -1);
                     }

                     g.setColor(Color.LIGHT_GRAY);
                     Common.drawHLine(g, iy + w2, x - colWidth[col], ix,
                                      StyleConstants.THIN_LINE, ver[i][j - 1],
                                      ver[i + 1][j - 1]);
                  }
               }

               // fix corner, lower-right
               if(i > 0 && i == hor.length - 2 && j > 0 &&
                  j == hor[i].length - 2)
               {
                  if(!borderEventFired) {
                     borderEventFired = true;
                     fireEvent(null, null, "startBorders", null, -1, -1);
                  }

                  float yadj = Common.getLineWidth(hor[i][j]);
                  float xadj = Common.getLineWidth(ver[i][j]);
                  yadj = (yadj > 1) ? yadj - 0.5f : yadj / 2f;
                  xadj = (xadj > 1) ? xadj - 0.5f : xadj / 2f;

                  // Gop.drawHLine adds w/2 to x when drawing in pdf. we extends it for
                  // solid line so it covers the (potential) vline (see Gop.drawHLine adj value).
                  // (62585, 62467)
                  if((h & StyleConstants.SOLID_MASK) != 0 && g instanceof PDFPrinter) {
                     xadj += xadj / 2;
                  }

                  g.setColor((colcolor != null) ? colcolor : efg);
                  Common.drawVLine(g, ix + w2, iy, iy + yadj,
                                   v, hor[i][j], StyleConstants.NO_BORDER);

                  g.setColor((rowcolor != null) ? rowcolor : efg);
                  Common.drawHLine(g, iy + w2, ix, ix + xadj,
                                   h, ver[i][j], StyleConstants.NO_BORDER);
               }

               // advance x
               if(j < hor[i].length - 2) {
                  x += colWidth[(j == headerC) ? (reg.x) : (col + 1)];
                  ix = x;
               }
            }
         }

         // advance y
         if(summaryR == 1 && i == hor.length - 2 - summaryR) {
            y += trailerH;
         }
         else if(i < hor.length - 2 - summaryR) {
            y += getRowHeight((i == headerR) ? (reg.y) : (row + 1));
         }

         iy = y;
      }

      if(borderEventFired) {
         fireEvent(null, null, "endBorders", null, -1, -1);
      }

      // in printlayout mode, paint table outer border for
      // vs converted table.
      paintBorder(g);
      g.setColor(oc);
      g.setFont(of);
   }

   /**
    * Paint table's outer borders
    * Because report table have no outer border for the whole table,
    * so this is only used in printlayout mode.
    */
   private void paintBorder(Graphics g) {
      Rectangle printband = getPrintBandBounds();

      if(printband == null) {
         return;
      }

      TableElementDef table = (TableElementDef) elem;
      BorderColors bcolors = table.getBorderColors();
      Insets borders = table.getBorders();

      if(borders == null) {
         return;
      }

      float linew_l = Common.getLineWidth(borders.left);
      float linew_r = Common.getLineWidth(borders.right);
      float thinlW = Common.getLineWidth(StyleConstants.THIN_LINE);

      Rectangle bounds = getBounds();
      float reg_x = bounds.x; // runtime x of table region.
      float reg_y = bounds.y; // runtime y of table region.
      float reg_w = printband.width; // pixel width setted in vs printlayout.
      float reg_h = bounds.height;   // height of the expanded table region.
      float offsetX = 1; // when alignment is left, table content start at 1.

      Size pageSize = ((BaseElement) elem).getReport().getPageSize();
      Margin margin = ((BaseElement) elem).getReport().getMargin();
      int pwidth = (int) ((pageSize.width - margin.right) * 72.0);

      if(reg_x >= pwidth) {
         return;
      }

      if(reg_x + reg_w > pwidth) {
         reg_w = pwidth - reg_x + offsetX;
      }

      int tableadv = 0;
      // 1. considering the border width, to make sure table border is align
      // with the title border.
      // 2. double line is drawed differently with others lines.
      float lOffset = borders.left == StyleConstants.DOUBLE_LINE ?
          (offsetX - ((linew_l - 1) + thinlW / 2)) : (offsetX - linew_l / 2);
      float rOffset = borders.right == StyleConstants.DOUBLE_LINE ?
         ((linew_l - 1) + thinlW / 2) + thinlW : (linew_r + linew_l) / 2;
      float x0 = reg_x - lOffset;
      float y0 = reg_y;
      float x1 = x0 + reg_w - rOffset;
      float y1 = reg_y + reg_h;
      // if not last region, should add table advance to make sure the outer
      // borders of each region have no gap.
      y1 = lastregion ? y1 : y1 + tableadv;

      // left
      if(borders.left != 0) {
         g.setColor(bcolors.leftColor);
         Common.drawLine(g, x0, y0, x0, y1, borders.left);
      }

      // right
      if(borders.right != 0) {
         g.setColor(bcolors.rightColor);
         Common.drawLine(g, x1, y0, x1, y1, borders.right);
      }

      // bottom
      if(lastregion) {
         if(borders.bottom != 0) {
            g.setColor(bcolors.bottomColor);
            Common.drawLine(g, x0, y1, x1, y1, borders.bottom);
         }
      }
   }

   /**
   * Get the table's printband in section.
   * This is only used for printlayout mode.
   */
   public Rectangle getPrintBandBounds() {
      if(!(elem instanceof TableElementDef)) {
         return null;
      }

      SectionBand band = (SectionBand) ((TableElementDef) elem).getParent();

      if(band == null) {
         return null;
      }
      
      int index = band.getElementIndex(elem);
      return band.getPrintBounds(index);
   }

   /**
    * Paint notation text.
    */
   private boolean paintNotationText(Graphics g, CellText info, Bounds bounds) {
      Map<Integer, Vector<int[]>> specs = getSpec(info);

      if(specs == null || specs.size() <= 0) {
         return false;
      }

      Font font = g.getFont();
      Font sfont = null;

      if(font instanceof StyleFont) {
         sfont = new StyleFont(SPEC_FONT, font.getStyle(), font.getSize(),
                               ((StyleFont) font).getUnderlineStyle(),
                               ((StyleFont) font).getStrikelineStyle());
      }
      else {
         sfont = new Font(SPEC_FONT, font.getStyle(), font.getSize());
      }

      FontMetrics fm = Common.getFontMetrics(font);
      FontMetrics sfm = Common.getFontMetrics(sfont);

      float fnH = Common.getHeight(g.getFont());
      float inc = fnH + elem.getSpacing();
      Bounds nbounds = new Bounds(info.bounds);

      for(int i = 0; i < info.lines.size(); i++) {
         Vector<int[]> specLocs = specs.get(i);
         int[] line = (int[]) info.lines.get(i);
         float off = (Float) info.lineoff.get(i);

         // no special character in current line, draw directly
         if(specLocs == null) {
            Vector lines = new Vector();
            Vector lineoff = new Vector();
            lines.add(line);
            lineoff.add(off);
            Common.paintText(g, info.text, lines, bounds, new Bounds(nbounds),
                             false, lineoff, elem.getSpacing(), true);
         }
         // has special character in current line, draw part
         else {
            for(int j = 0; j < specLocs.size(); j++) {
               line = specLocs.get(j);
               String str = info.text.substring(line[0], line[1]);
               boolean specStr = isSpec(str.charAt(0));
               Vector lines = new Vector();
               lines.add(line);
               Vector lineoff = new Vector();
               lineoff.add(off);

               if(specStr) {
                  g.setFont(sfont);
               }

               Common.paintText(g, info.text, lines, bounds,
                  new Bounds(nbounds), false, lineoff, elem.getSpacing(), true);

               if(specStr) {
                  g.setFont(font);
               }

               off += specStr ? fm.stringWidth(str) : sfm.stringWidth(str);
            }
         }

         nbounds.y = nbounds.y + inc;
      }

      return true;
   }

   /**
    * Collect all spec characters.
    */
   private Map<Integer, Vector<int[]>> getSpec(CellText info) {
      int len = info == null || info.text == null ? 0 : info.text.length();

      if(len <= 0) {
         return null;
      }

      // line -> spec character position
      Map<Integer, Vector<int[]>> specs = new HashMap<>();

      for(int i = 0; i < info.lines.size(); i++) {
         int[] locs = (int[]) info.lines.get(i);
         int start = locs[0];
         int end = locs[1];

         if(start >= len) {
            break;
         }

         int prePos = start;
         boolean preSpec = isSpec(info.text.charAt(start));
         int j = 0;
         Vector<int[]> specLocs = null;

         for(j = start + 1; j < end; j++) {
            boolean spec = isSpec(info.text.charAt(j));

            if(preSpec != spec) {
               specLocs = specs.get(i);

               if(specLocs == null) {
                  specLocs = new Vector<>();
                  specs.put(i, specLocs);
               }

               specLocs.add(new int[] {prePos, j});
               prePos = j;
               preSpec = spec;
            }
         }

         if(specLocs != null) {
            specLocs.add(new int[] {prePos, end});
         }
      }

      return specs;
   }

   /**
    * Check if a character is a spec character.
    */
   private boolean isSpec(char c) {
      return c == DOWN_ARROW || c == RIGHT_ARROW ||
             c == GROUP_SYMBOL || c == SUMMARY_SYMBOL;
   }

   /**
    * Paint the design time titles and borders.
    */
   public void paintDesign(Graphics g, boolean selected) {
      TableElementDef elem = (TableElementDef) getElement();
      TableLayout layout = elem.getTableLayout();
      TableDataDescriptor desc = lens.getDescriptor();

      if(layout == null || layout.isCalc()) {
         return;
      }

      float y2 = box.y;
      float end = box.y;
      FontMetrics fm = Common.getFontMetrics(LFONT);
      float minH = fm.getHeight() * 0.6f;
      Shape oclip = g.getClip();
      // adjust for left margin adjustment (in case margin is < 1")
      int leftX = (int) (elem.getReport().getTopReport().getMargin().left * 72);
      int leftAdj = (int) ((leftX < SectionPaintable.MIN_MARGIN * 72) ?
         (SectionPaintable.MIN_MARGIN * 72 - leftX) : 0f);
      // @by davidd, Fill the background with a non-white, to improve contrast.
      Color fillColor = new Color(235, 250, 250);
      int clearX = -leftAdj;
      int clearY = (int) y2;
      int clearW = leftX + leftAdj;
      int clearH = (int) this.box.height;
      g.setColor(fillColor);
      g.fillRect(clearX, clearY, clearW, clearH);
      g.setFont(LFONT);
      g.setColor(Color.lightGray);
      Rectangle box = g.getClipBounds();

      if(box != null) {
         box.x -= leftAdj;
         box.width = Math.min(box.width, clearW);
         box.width += leftAdj;
         g.setClip(box);
      }

      // level -> headerpos
      Map<Integer,Point> ghpos = new HashMap<>();
      // level -> footerpos
      Map<Integer,Point> gfpos = new HashMap<>();
      int maxlevel = 0;
      int[][] ranges = {{0, lens.getHeaderRowCount()},
                        {reg.y, reg.y + reg.height}};
      TableDataPath lastPath = null;

      for(int i = 0; i < ranges.length; i++) {
         for(int r = ranges[i][0]; r < ranges[i][1]; r++) {
            TableDataPath path = desc.getRowDataPath(r);
            float bandH = getRowHeight(r);

            // @by larryl, ignore row index in comparison
            if(path == null || lastPath != null &&
               path.getType() == lastPath.getType() &&
               path.getLevel() == lastPath.getLevel())
            {
               y2 += bandH;
               continue;
            }

            int level = path.getLevel();

            if(path.getType() == TableDataPath.DETAIL) {
               // find max level for detail
               for(int k = 0; k < ranges.length; k++) {
                  for(int m = ranges[i][0]; m < ranges[i][1]; m++) {
                     TableDataPath path2 = desc.getRowDataPath(m);
                     level = Math.max(level, path2.getLevel());
                  }
               }
            }

            final int INDENT = 4;
            int ind = Math.max(0, level + 1) * INDENT - leftAdj + 2;
            g.setColor(Color.lightGray);
            Common.drawLine(g, ind, (int) y2, leftX, (int) y2);

            // don't draw the title if the band is too short
            if(bandH >= minH) {
               g.setColor(Color.DARK_GRAY);
               g.drawString(toString(path), ind,
                            (int) (y2 + fm.getMaxAscent() + 1));
            }

            if(path.getType() == TableDataPath.GROUP_HEADER) {
               ghpos.put(level, new Point(ind - 1, (int) y2 + fm.getHeight() / 2));
            }
            else if(path.getType() == TableDataPath.SUMMARY) {
               gfpos.put(level, new Point(ind - 1, (int) y2 + fm.getHeight() / 2));
            }

            maxlevel = Math.max(level, maxlevel);
            y2 += bandH;
            lastPath = path;
         }
      }

      // draw the bottom line
      g.setColor(Color.lightGray);
      Common.drawLine(g, 2 - leftAdj, (int) y2, leftX, (int) y2);

      if(!layout.isCrosstab()) {
         g.setColor(new Color(0x999999));

         // draw connectors between header and footer
         for(int i = 0; i <= maxlevel; i++) {
            Point hpos = ghpos.get(i);
            Point fpos = gfpos.get(i);
            int stem = 3;

            if(hpos != null && fpos != null) {
               Common.drawLine(g, hpos.x, hpos.y, hpos.x - stem, hpos.y);
               Common.drawLine(g, fpos.x, fpos.y, fpos.x - stem, fpos.y);
               Common.drawLine(g, hpos.x - stem, hpos.y, fpos.x - stem, fpos.y);
            }
            else if(hpos != null) {
               Common.drawLine(g, hpos.x, hpos.y, hpos.x - stem, hpos.y);
               Common.drawLine(g, hpos.x - stem, hpos.y, hpos.x - stem,
                               hpos.y + stem);
            }
            else if(fpos != null) {
               Common.drawLine(g, fpos.x, fpos.y, fpos.x - stem, fpos.y);
               Common.drawLine(g, fpos.x - stem, fpos.y, fpos.x - stem,
                               fpos.y - stem);
            }
         }
      }

      boolean crosstab = layout.isCrosstab();

      if(crosstab || selected) {
         g.setClip(oclip);
         int[][] xranges = {{ 0, lens.getHeaderColCount() },
            {reg.x, reg.x + reg.width}};
         float yShift = fm.getHeight();
         int x2 = (int) this.box.x;
         float bandW = 0;

         // clear the bounds, so the text will not overlap with others
         clearX = x2 - 2;
         clearY = (int) (end - yShift - 2);
         clearW = (int) (this.box.width + 4);
         clearH = (int) (yShift + 2);
         g.setColor(fillColor);
         g.fillRect(clearX, clearY, clearW, clearH);

         for(int i = 0; i < xranges.length; i++) {
            for(int c = xranges[i][0]; c < xranges[i][1]; c++) {
               g.setColor(Color.lightGray);
               Common.drawLine(g, x2, (int) (end - yShift), x2, (int) end);
               bandW = getColWidth(c);
               String value = Catalog.getCatalog().getString("Column") + c;

               if(crosstab) {
                  TableDataPath path = desc.getColDataPath(c);
                  value = toString(path);
               }

               // draw the title if the band is too long change to xxxxx...
               // and don't draw if the band is too short
               boolean isLonger = false;
               isLonger = fm.stringWidth(value) > bandW;
               float minW = fm.stringWidth(value) * 0.8f;
               g.setColor(Color.DARK_GRAY);

               if(isLonger) {
                  String path_str = "";

                  for(int j = Math.min(5, value.length()); j > 0; j--) {
                     path_str = value.substring(0, j) + OMISSION_STR;

                     if(fm.stringWidth(path_str) <= bandW) {
                        break;
                     }
                  }

                  g.drawString(path_str, x2,
                        (int) (end - yShift + fm.getMaxAscent() + 1));
               }
               else if(bandW >= minW) {
                  g.drawString(value, x2,
                               (int) (end - yShift + fm.getMaxAscent() + 1));
               }

               x2 += bandW;
            }
         }

         // draw the bottom line
         g.setColor(Color.lightGray);
         Common.drawLine(g, x2, (int) (end - yShift), x2, (int) end);
      }

      g.setClip(oclip);
   }

   /**
    * Paint design time decoration on a cell.
    */
   private void paintDesignCell(Graphics2D g, int row, int col,
                                Bounds bounds)
   {
      TableElementDef telem = (TableElementDef) getElement();
      TableLayout layout = telem.getTableLayout();
      CellBindingInfo cinfo = layout.getCellInfo(row, col);
      g.setColor(GDefaults.DEFAULT_LINE_COLOR);

      if(cinfo == null) {
         return;
      }

      Point2D top = null;
      Point2D right = null;

      switch(cinfo.getExpansion()) {
      case GroupableCellBinding.EXPAND_H:
         top = new Point2D.Double(bounds.x + bounds.width - 6, bounds.y + 2);
         right = new Point2D.Double(top.getX() + 5, top.getY());
         break;
      case GroupableCellBinding.EXPAND_V:
         top = new Point2D.Double(bounds.x + bounds.width - 3, bounds.y + 1);
         right = new Point2D.Double(top.getX(), top.getY() + 5);
         break;
      }

      if(right != null) {
         g.draw(new Line2D.Double(top, right));
         GTool.drawArrow(g, top, right, 2);
      }
   }

   /**
    * Return the bounds of this paintable area.
    * @return area bounds or null if element does not occupy an area.
    */
   @Override
   public Rectangle getBounds() {
      int lastC = ver[0].length - 2;
      int lastR = ver.length - 2;
      // right border width
      float rb = Math.max(Common.getLineWidth(ver[0][lastC]),
         Common.getLineWidth(ver[lastR][lastC]));
      // bottom border width
      float bb = Math.max(Common.getLineWidth(hor[lastR][0]),
         Common.getLineWidth(hor[lastR][lastC]));

      return new Rectangle(Math.round(box.x), Math.round(box.y),
         (int) Math.ceil(box.width + rb - 1),
         (int) Math.ceil(box.height + bb - 1));
   }

   /**
    * Return the bounds of this paintable area assigned by container, which
    * will not take border into consideration. It is most likely been used
    * when layouting it's table element, for in most cases, the layouting will
    * not consider borders.
    * @return area bounds or null if element does not occupy an area.
    */
   public Rectangle getBounds2() {
      return new Rectangle((int) box.x, (int) box.y, (int) box.width,
                           (int) box.height);
   }

   /**
    * Set the location of this paintable area. This is used internally
    * for small adjustments during printing.
    * @param loc new location for this paintable.
    */
   @Override
   public void setLocation(Point loc) {
      box.setLocation(loc);

      // @by larryl, if paintable moved, remove cached information that
      // contains positions so the paint would be done at the new location
      clearCache();

      for(int i = 0; i < cellbounds.length; i++) {
         for(int j = 0; j < cellbounds[i].length; j++) {
            cellbounds[i][j] = null;
         }
      }
   }

   /**
    * Get the location of this paintable.
    */
   @Override
   public Point getLocation() {
      return box.getLocation();
   }

   /**
    * Locate the cell where the point falls into.
    * @param xd x coordinate relative to page.
    * @param yd y coordinate relative to page.
    * @return cell location or null if outside of table.
    */
   public Point locate(int xd, int yd) {
      return locate(xd, yd, false);
   }

   /**
    * Locate the cell border where the point falls into.
    * @param xd x coordinate relative to page.
    * @param yd y coordinate relative to page.
    * @return cell location or null if outside of table.
    */
   public Point locateBorder(int xd, int yd) {
      return locateBorder(xd, yd, true, true);
   }

   /**
    * Locate the cell border where the point falls into.
    */
   public Point locateBorder(int xd, int yd, boolean l2r, boolean t2b) {
      return locate(xd, yd, l2r, t2b, true);
   }

   /**
    * Locate the cell where the point falls into.
    * @param xd x coordinate relative to page.
    * @param yd y coordinate relative to page.
    * @param border true to locate the cell border where the
    * point is in, false to locate the entire cell.
    */
   private Point locate(float xd, float yd, boolean border) {
      return locate(xd, yd, true, true, border);
   }

   /**
    * Locate the cell where the point falls into.
    * @param l2r find from left to right.
    * @param t2b find from top to bottom.
    */
   private Point locate(float xd, float yd, boolean l2r, boolean t2b,
                        boolean border) {
      xd -= (int) box.x;
      yd -= (int) box.y;

      if(xd < 0 || xd > box.width + 3 || yd < 0 || yd > box.height + 1 ||
         colWidth == null || colBorderW == null || rowHeight == null ||
         rowBorderH == null || colWidth.length < lens.getColCount() ||
         colBorderW.length < lens.getColCount())
      {
         return null;
      }

      // there could be a racing condition here, need to sync with
      // layout

      Point cell = new Point(-1, -1);
      int headerR = lens.getHeaderRowCount();
      int headerC = lens.getHeaderColCount();
      int lastRAvailable = -1;

      // is it in header rows ?
      for(int i = 0; i < headerR; i++) {
         float mb = getRowHeight(i) +
            ((i < headerR - 1) ? Math.max(R, getRowBorderH(i + 1)) : R);

         if(!border && yd < getRowHeight(i) ||
            border && yd >= getRowHeight(i) && yd <= mb)
         {
            if(t2b) {
               cell.y = i;
               break;
            }
            else {
               lastRAvailable = i;
            }
         }
         else if(!t2b && lastRAvailable >= 0) {
            cell.y = lastRAvailable;
            break;
         }

         yd -= getRowHeight(i);
      }

      int lastCAvailable = -1;

      // is it in header cols ?
      for(int i = 0; i < headerC; i++) {
         float mb = colWidth[i] +
            ((i < headerC - 1) ? Math.max(R, colBorderW[i + 1]) : R);

         if(!border && xd < colWidth[i] ||
            border && xd >= colWidth[i] - 1 && xd <= mb)
         {
            if(l2r) {
               cell.x = i;
               break;
            }
            else {
               lastCAvailable = i;
            }
         }
         else if(!l2r && lastCAvailable >= 0) {
            cell.x = lastCAvailable;
            break;
         }

         xd -= colWidth[i];
      }

      // check in the body
      if(cell.x < 0) {
         int end = reg.x + reg.width;

         cell.x = -1;

         for(int i = reg.x; i < end && i < colWidth.length; i++) {
            float mb = colWidth[i] +
               ((i < colBorderW.length - 1) ?
                Math.max(R, colBorderW[i + 1]) : R);
            // Only enlarge range of last border to resize.
            int offset = i == (end - 1) ? 1 : 0;

            if(!border && xd < colWidth[i] ||
               border && xd >= colWidth[i] - 1 && xd <= mb + offset)
            {
               if(l2r) {
                  cell.x = i;
                  break;
               }
               else {
                  lastCAvailable = i;
               }
            }
            else if(!l2r && lastCAvailable >= 0) {
               cell.x = lastCAvailable;
               break;
            }

            xd -= colWidth[i];
         }
      }

      if(cell.y < 0) {
         int end = reg.y + reg.height;

         cell.y = -1;

         for(int i = reg.y; i < end; i++) {
            float mb = getRowHeight(i) + getRowBorderH(i + 1);

            if(!border && yd < getRowHeight(i) ||
               border && yd >= getRowHeight(i) && yd <= mb)
            {
               if(t2b) {
                  cell.y = i;
                  break;
               }
               else {
                  lastRAvailable = i;
               }
            }
            else if(!t2b && lastRAvailable >= 0) {
               cell.y = lastRAvailable;
               break;
            }

            yd -= getRowHeight(i);
         }
      }

      if(cell.x < 0 && lastCAvailable >= 0) {
         cell.x = lastCAvailable;
      }

      if(cell.y < 0 && lastRAvailable >= 0) {
         cell.y = lastRAvailable;
      }

      return (border && (cell.x < 0 && cell.y < 0) ||
              !border && (cell.x < 0 || cell.y < 0) ||
              cell.x > colWidth.length || cell.y > lens.getRowCount()) ?
         null : cell;
   }

   /**
    * Get the bounding box of the specified cell. Take border into
    * account.
    */
   public Bounds getCellBounds(int row, int col, boolean isSummary) {
      return getCellBounds(row, col, isSummary, true);
   }

   /**
    * Get the bounding box of the specified cell. Take border into
    * account.
    */
   public Bounds getCellBounds(int row, int col, boolean isSummary,
                               boolean includingSpan) {
      int ci = (row < headerR) ? row : (row + headerR - reg.y);
      int cj = (col < headerC) ? col : (col + headerC - reg.x);

      // @by watson, bug1088029660300
      // when the width of table was larger than page width, it was splitted
      // into 2 line, and it was possible to make cj < 0 when col = 1,
      // headerC = 1, but reg.x > 2
      // make sure ci and cj in valid range
      if(ci < 0) {
         ci = row;
      }

      if(cj < 0) {
         cj = col;
      }

      // cached
      if(includingSpan && ci < cellbounds.length &&
         cj < cellbounds[ci].length && cellbounds[ci][cj] != null)
      {
         return new Bounds(cellbounds[ci][cj]);
      }

      Rectangle area = isSummary ? (Rectangle) sumspan.get(col) : getSpan(row, col);

      // @by larryl, optimization. Use the cached accumulative row heights to
      // avoid adding up all rows everytime. This is especially important when
      // generate excel_sheet or excel_data where a single table region can
      // be very large.
      if(arowH == null) {
         arowH = new float[reg.height < 0 ? 0 : reg.height + summaryR];

         for(int i = 0; i < reg.height; i++) {
            if(i == 0) {
               arowH[i] = getRowHeight(i + reg.y);
            }
            else {
               arowH[i] = arowH[i - 1] + getRowHeight(i + reg.y);
            }
         }
      }

      Bounds bds = getCellBounds0(row, col, area, box, reg, arowH,
                                  isSummary, includingSpan);

      if(includingSpan) {
         cellbounds[ci][cj] = new Bounds(bds);
      }

      return bds;
   }

   private Bounds getCellBounds0(int row, int col, Rectangle area, Bounds box,
                                 Rectangle reg, float[] arowH,
                                 boolean isSummary, boolean includingSpan)
   {
      // the border of last row/col take space in this cell, we need
      // to adjust for that
      int lastcol = (col == reg.x) ? (headerC - 1) : (col - 1);
      int lastrow = (row == reg.y) ? (headerR - 1) : (row - 1);

      // make a copy so we don't modify the original
      if(area != null) {
         // if the span covers both header and body cells, the span is only
         // applied on the first table region
         if(row < headerR && reg.y > headerR &&
            area.y + row + area.height > headerR)
         {
            area = null;
         }
         else {
            area = new Rectangle(area);
         }
      }

      float xadj = Common.getLineWidth(lens.getColBorder(row, lastcol));
      float yadj = Common.getLineWidth(lens.getRowBorder(lastrow, col));

      // @by larryl, if it's a span cell, use the least border width so there
      // is no gap in cells with thinner border
      if(area != null) {
         for(int i = 1; i < area.width; i++) {
            float adj2 = Common.getLineWidth(lens.getRowBorder(lastrow, col+i));
            yadj = Math.min(yadj, adj2);
         }

         for(int i = 1; i < area.height && lens.moreRows(row + i); i++) {
            float adj2 = Common.getLineWidth(lens.getColBorder(row+i, lastcol));
            xadj = Math.min(xadj, adj2);
         }
      }

      Bounds bds = new Bounds(box.x + xadj, box.y + yadj, -xadj, -yadj);

      // if in middle of span cell, adjust the bds.x and bds.y to the
      // location of the top-left cell, and change the row,col
      if(includingSpan && area != null && (area.x != 0 || area.y != 0)) {
         // cut off on left, adjust the left edge
         if(col + area.x < reg.x && col + area.x >= headerC) {
            for(int i = col + area.x; i < col; i++) {
               bds.x -= colWidth[i];
            }
         }

         // cut off on top, adjust the top edge
         if(row + area.y < reg.y && row + area.y >= headerR) {
            for(int i = row + area.y; i < row; i++) {
               bds.y -= getRowHeight(i);
            }
         }

         row = row + area.y;
         col = col + area.x;

         area.width -= area.x;
         area.height -= area.y;
      }

      // calculate the x, y of the cell
      // this is not efficient, we can easily cache and accumulate
      // the results, change if it becomes a bottleneck

      // add header height
      if(row >= headerR) {
         bds.y += headerH;
      }
      else {
         for(int i = 0; i < row; i++) {
            bds.y += getRowHeight(i);
         }
      }

      if(arowH == null) {
         arowH = new float[reg.height < 0 ? 0 : reg.height + summaryR];

         for(int i = 0; i < reg.height; i++) {
            if(i == 0) {
               arowH[i] = getRowHeight(i + reg.y);
            }
            else {
               arowH[i] = arowH[i - 1] + getRowHeight(i + reg.y);
            }
         }
      }

      // rows above
      if(row > reg.y) {
         bds.y += arowH[row - reg.y - 1];
      }

      // header width
      if(col >= headerC) {
         bds.x += headerW;
      }
      else {
         for(int i = 0; i < col; i++) {
            bds.x += colWidth[i];
         }
      }

      // columns left of
      for(int i = reg.x; i < col; i++) {
         bds.x += colWidth[i];
      }

      // handle span cell
      if(includingSpan && area != null) {
         // @by larryl, if the including span flag is set, the bounds should
         // reflect the entire span cell size, not limited by the current
         // table region. Otherwise it breaks the contract.
         // int endcol = Math.min(col + area.width, reg.x + reg.width);
         int endcol = col + area.width;

         for(int i = col; i < endcol; i++) {
            // @by larryl, if a header cell is span across multiple pages, the
            // area.x will always be 0. If we add all columns to the side, the
            // text may never be visible. Here we limit the span cell size to
            // the current region. It causes the text to be repeated on each
            // page. Since it's only for header cell, that should be ok.
            // However it breaks the contract and is dangerous.
            if(col < headerC) {
               if(i >= headerC && i < reg.x) {
                  continue;
               }
               else if(i >= reg.x + reg.width) {
                  break;
               }
            }

            bds.width += colWidth[i];
         }

         if(isSummary) {
            bds.height += trailerH;
         }
         else {
            int endrow = row + area.height;

            for(int i = row; i < endrow; i++) {
               bds.height += getRowHeight(i);
            }
         }
      }
      // single cell
      else {
         bds.width += colWidth[col];
         bds.height += isSummary ? trailerH : getRowHeight(row);
      }

      return new Bounds(bds);
   }

   /**
    * Get the bounding box of the specified cell. Take border into
    * account. If middle of span cell, return the printable region
    * of the right/bottom of span cell. Otherwise same as getCellBounds.
    */
   public Bounds getPrintBounds(int row, int col, boolean isSummary) {
      Bounds bds = getCellBounds(row, col, isSummary);
      Rectangle area = isSummary ? (Rectangle) sumspan.get(col) : getSpan(row, col);
      return getPrintBounds0(row, col, isSummary, area, reg, bds);
   }

   private Bounds getPrintBounds0(int row, int col, boolean isSummary,
                                  Rectangle area, Rectangle reg, Bounds bds)
   {
      if(area == null) {
         return bds;
      }

      // @by larryl, area.x and area.y are negative, so the init should be add
      for(int i = col + area.x; i < col; i++) {
         bds.width -= colWidth[i];
         bds.x += colWidth[i];
      }

      for(int i = row + area.y; i < row; i++) {
         bds.height -= getRowHeight(i);
         bds.y += getRowHeight(i);
      }

      /* @by larryl 5.1, the getCellBounds does not add cells to the right
         of the table region, so this code is not needed, it is still adding
         heights, so the next loop is needed. need to check if it is necessary
         (most likely for vertical clipping) so we can make it consistent
         @by larryl 7.0, changed the getCellBounds to treat width and height
         consistently (adding the area to the right of the region).
      */
      // @see getCellBounds comments. The header cell bounds don't include
      // space to the right of the table region, so don't subtract here.
      if(col >= headerC) {
         for(int i = reg.x + reg.width; i < col + area.width; i++) {
            bds.width -= colWidth[i];
         }
      }

      for(int i = reg.y + reg.height; i < row + area.height; i++) {
         bds.height -= getRowHeight(i);
      }

      return bds;
   }

   /**
    * Get the table region this paintable corresponds to.
    * @return table region, where x,y is the column and row number,
    * and width/height are the number of columns/rows.
    */
   public Rectangle getTableRegion() {
      return reg;
   }

   /**
    * Get the table lens of this table region. Only the header rows/columns
    * and the cells in this table region is valid.
    */
   public TableLens getTable() {
      return lens;
   }

   /**
    * Get the hyperlink definition for a cell.
    */
   public Hyperlink.Ref getHyperlink(int r, int c) {
      if(linkmap == null) {
         return null;
      }

      // @see getDrillHyperlinks
      Rectangle rec = getSpan(r, c);

      if(rec != null) {
         r += rec.y;
         c += rec.x;
      }

      Hyperlink.Ref link = (Hyperlink.Ref) linkmap.get(new Point(c, r));
      return link;
   }

   /**
    * Set the hyperlink definition for a column.
    */
   public void setHyperlink(int r, int c, Hyperlink.Ref link) {
      // @see setDrillHyperlinks
      Rectangle span = getSpan(r, c);

      if(span != null) {
         r = span.y + r;
         c = span.x + c;
      }

      if(link == null) {
         if(linkmap != null) {
            linkmap.remove(new Point(c, r));

            if(linkmap.size() == 0) {
               linkmap = null;
            }
         }
      }
      else {
         if(linkmap == null) {
            linkmap = new Hashtable();
         }

         linkmap.put(new Point(c, r), link);
      }
   }

   /**
    * Remove all hyperlink definitions.
    */
   public void removeAllHyperlinks() {
      linkmap = null;
   }

   /**
    * Used internally to get the original span map.
    */
   public SpanMap getSpanMap() {
      return spanmap;
   }

   /**
    * Get summary span map.
    */
   public Hashtable getSumSpan() {
      return sumspan;
   }

   /**
    * Apply any format defined for the object type.
    */
   public Object formatObject(Object obj) {
      Format fmt = StyleCore.getFormat(formats, obj.getClass());

      if(fmt != null) {
         obj = fmt.format(obj);
      }

      return obj;
   }

   /**
    * Calculate cell clipping.
    */
   public Bounds getCellClip(int row, int col, Rectangle area, Bounds bounds,
                             float xadj, float yadj, boolean isSummary)
   {
      // rect is the physical printable area of this cell
      Bounds cclip = (area == null || area.x == 0 && area.y == 0) ?
         new Bounds(bounds) : getPrintBounds(row, col, isSummary);
      return getCellClip0(row, col, bounds, cclip, box, xadj, yadj);
   }

   private Bounds getCellClip0(int row, int col, Bounds bounds, Bounds cclip,
                               Bounds box, float xadj, float yadj)
   {
      float left = box.x + xadj + ((col >= headerC) ? headerW : 0f);
      float right = box.y + yadj + ((row >= headerR) ? headerH : 0f);

      cclip.x = Math.max(cclip.x, left);
      cclip.y = Math.max(cclip.y, right);
      cclip.width = Math.min(cclip.width, box.width + box.x - cclip.x);
      cclip.height = Math.min(cclip.height,
         box.height + box.y - Math.max(box.y + yadj, cclip.y));
      return cclip;
   }

   /**
    * Get the span.
    */
   public Rectangle getSpan(int row, int col) {
      // @by davidd, feature1193169114534 merged headers should be layed out
      // using the full boundaries of the span. LOGIC REMOVED on Dec. 17, 2010
      return getSpan0(row, col);
   }

   /**
    * Check if is a merged cell that requires adjusting.
    */
   private boolean repeatMergedCell(int row, int col) {
      if(!repeatCell || !(row >= headerR || col >= headerC)) {
         return false;
      }

      Rectangle span = getSpan0(row, col);
      return span != null && (span.width - span.x > 1 ||
                              span.height - span.y > 1);
   }

   /**
    * Get cell span.
    */
   private Rectangle getSpan0(int row, int col) {
      return row == reg.y && groupMap.containsKey(col) ?
         groupMap.get(col) : spanmap.get(row, col);
   }

   /**
    * Get a cell value and takes into account of supressing duplicate and
    * suppressing zero.
    */
   public Object getData(int row, int col) {
      Object val = getValue(row, col);

      if(val == null || !(lens instanceof AttributeTableLens)) {
         return val;
      }

      return ((AttributeTableLens) lens).getData(row, col);
   }

   /**
    * Get a cell value and takes into account of supressing duplicate and
    * suppressing zero.
    */
   public Object getValue(int row, int col) {
      return getValue(row, col, false);
   }

   public Object getValue(int row, int col, boolean firstCell) {
      Rectangle span = getSpan(row, col);

      // @by billh, if contains span and not the upper-left cell,
      // we return null for exporting, and it seems more reasonable
      if(!firstCell && span != null && (span.x < 0 || span.y < 0)) {
         return null;
      }

      if(span != null) {
         row = row + span.y;
         col = col + span.x;
      }

      initPaintable(lens.getFont(row, col));
      return getValue(lens, row, col);
   }

   /**
    * Get cell data path.
    */
   public TableDataPath getCellDataPath(int row, int col, boolean firstCell) {
      Rectangle span = getSpan(row, col);

      // @by billh, if contains span and not the upper-left cell,
      // we return null for exporting, and it seems more reasonable
      if(!firstCell && span != null && (span.x < 0 || span.y < 0)) {
         return null;
      }

      if(span != null) {
         row = row + span.y;
         col = col + span.x;
      }

      TableDataDescriptor descriptor = lens.getDescriptor();

      if(descriptor == null) {
         return null;
      }

      return descriptor.getCellDataPath(row, col);
   }

   /**
    * Get a cell value and takes into account of supressing duplicate and
    * suppressing zero.
    */
   private Object getValue(TableLens lens, int row, int col) {
      Object obj = lens.getObject(row, col);
      Object dobj = (lens instanceof AttributeTableLens) ?
         ((AttributeTableLens) lens).getData(row, col) : obj;

      boolean supZero = (lens instanceof AttributeTableLens) ?
         ((AttributeTableLens) lens).isSuppressIfZero(row, col) : false;
      boolean supDup = (lens instanceof AttributeTableLens) ?
         ((AttributeTableLens) lens).isSuppressIfDuplicate(row, col) : false;

      if(supZero) {
         if(dobj instanceof Number && ((Number) dobj).doubleValue() == 0) {
            return null;
         }
         else if(dobj instanceof String) {
            try {
               double d = Double.parseDouble((String) dobj);

               if(d == 0) {
                  return null;
               }
            }
            catch(Exception ex) {
            }
         }
      }

      if(supDup && row > 0) {
         int prow = row - 1;

         // @fix bug bug1275485092707, only when mappedTable and lens have
         // the same row mapping, could we use mappedTable instead of lens
         if(mappedTable != null &&
            TableTool.getBaseRowIndex(lens, (TableLens)mappedTable, row) == row)
         {
            prow = mappedTable.getLastLogicalRow(row);
         }

         if(prow >= 0) {
            Object obj0 = obj;
            Object lobj = lens.getObject(prow, col);

            if(!(lobj instanceof Comparable) && lens instanceof AttributeTableLens) {
               lobj = ((AttributeTableLens) lens).getData(prow, col);
            }

            if(!(obj0 instanceof Comparable)) {
               obj0 = dobj;
            }

            // @by larryl, use compare() so numbers of different types are
            // compared correctly
            if(obj0 != null && Tool.compare(obj0, lobj, false, true) == 0) {
               return null;
            }
         }
      }

      return obj;
   }

   /**
    * Get the alignment of a cell. The alignment may be different from the
    * table lens.
    */
   public int getAlignment(int row, int col) {
      return getAlignment0(row, col, reg);
   }

   private int getAlignment0(int row, int col, Rectangle reg) {
      boolean isLastRow = row == reg.y + reg.height - 1;
      int align = (summary != null && isLastRow) ?
         summary.getSummaryAlignment(col) : lens.getAlignment(row, col);

      return align;
   }

   /**
    * Get the row border. This may be different from table lens.
    */
   public int getRowBorder(int row, int col) {
      int ri = (row <= headerR) ? (row + 1) : (row + headerR - reg.y + 1);
      int ci = (col <= headerC) ? (col + 1) : (col + headerC - reg.x + 1);

      if(ri < hor.length - 1 && ci < hor[ri].length - 1) {
         return hor[ri][ci];
      }
      else {
         return lens.getRowBorder(row, col);
      }
   }

   /**
    * Get the column border. This may be different from table lens.
    */
   public int getColBorder(int row, int col) {
      int ri = (row <= headerR) ? (row + 1) : (row + headerR - reg.y + 1);
      int ci = (col <= headerC) ? (col + 1) : (col + headerC - reg.x + 1);

      if(ri < ver.length - 1 && ci < ver[ri].length - 1) {
         return ver[ri][ci];
      }
      else {
         return lens.getColBorder(row, col);
      }
   }

   /**
    * Clear cache.
    */
   @Override
   public void clearCache() {
      if(linescache != null) {
         linescache.clear();
      }

      if(remaindercache != null) {
         remaindercache.clear();
      }
   }

   /**
    * Get the cell drawing information. The text has been wrapped into
    * individual lines up on return.
    */
   public CellText getCellText(int row, int col, Object obj, Bounds bounds,
                               int align, boolean wrap, Font font,
                               FontMetrics fm, Rectangle area,
                               Bounds cclip, float xadj, float yadj) {
      return getCellText(row, col, obj, bounds, align, wrap, font,
                         fm, area, cclip, xadj, yadj, true);
   }

   private CellText getCellText(int row, int col, Object obj, Bounds bounds,
                                int align, boolean wrap, Font font,
                                FontMetrics fm, Rectangle area, Bounds cclip,
                                float xadj, float yadj, boolean clipStr)
   {
      initPaintable(font);

      // Bug #61145, account for css cell padding in spanned cells
      if(cclip != null) {
         Insets insets = lens.getInsets(row, col);

         if(insets != null) {
            cclip = new Bounds(cclip);
            cclip.x += insets.left;
            cclip.y += insets.top;
            cclip.width -= insets.left + insets.right;
            cclip.height -= insets.top + insets.bottom;
         }
      }

      // if repeat mode, always use cclip to layout,
      // if calculate clip string, use cclip to layout
      if(repeatCell && cclip != null || !clipStr && cclip != null) {
         bounds = cclip;
      }

      // clone it, to make sure the original bounds will not be changed,
      // so export ppt will not lost data
      bounds = applyPadding(new Bounds(bounds));

      // cclip same as bounds for check pre page clipped string
      // fix bug1319747791263
      if(!clipStr && cclip != null) {
         cclip = applyPadding(new Bounds(cclip));
      }

      if(fm == null) {
         fm = Common.getFractionalFontMetrics(font);
      }

      // has been formatted
      if(clipStr) {
         if(obj instanceof DCMergeDatesCell) {
            obj = ((DCMergeDatesCell) obj).getFormatedOriginalDate();
         }

         // look for format of the object
         obj = formatObject(obj);
      }

      // preprocess text and setup cache
      String str = Tool.toString(obj);

      // @by billh, a table paintable could not contain the same table cell
      // twice, so we may use (row,col) as key to cache data regardless of
      // area, otherwise two table cells might have the same key
      // Point ocell = (area == null) ?
      //    new Point(col, row) : new Point(col - area.x, row - area.y);
      Point ocell = new Point(col, row);

      CellText info = clipStr ? (CellText) linescache.get(ocell) : null;
      Bounds nbounds = null;
      Vector lines = null;
      Vector lineoff = null;
      boolean useReaminderCache = true;
      // identical current text is in repeating
      boolean isRepeat = false;

      if(info != null) {
         lines = info.lines;
         lineoff = info.lineoff;
         nbounds = new Bounds(info.bounds);

         // check if the contents changed
         if(lines.size() > 0) {
            int[] range = (int[]) lines.elementAt(lines.size() - 1);

            // changed, recalc
            // @by mikec, changed means they are not exactly match
            // otherwise when content changed by script the clip
            // will not clear.
            if(range[1] != str.length()) {
               lines = null;
               // if script changed text, we should not use the cache
               // in remainder cache
               useReaminderCache = false;
            }
         }
      }

      // @by davyc, why not use cache directly? use directly
      // for paintable will be used in many place, such as
      // export(match layout/...), so every time the bounds
      // used here is not same, we need to recalculate the
      // really text for current bounds to make sure the text
      // is correct for current bounds
      /*
      if(lines != null) {
         return info;
      }
      */

      if(lines == null) {
         nbounds = new Bounds();
         lineoff = new Vector(2);
         Float rmax = (Float) pointlocmap.get(col);
         float right = (rmax == null) ? (float) 0.0 : rmax.floatValue();

         if(repeatCell && clipStr && cclip != null) {
            Object[] res = getClipString(row, col, str, xadj, yadj, null, null,
                                         useReaminderCache);
            str = (String) res[0];
            isRepeat = (Boolean) res[1];
         }

         lines = Common.processText(str, bounds, align, wrap, font, nbounds,
                                    lineoff, elem.getSpacing(), fm, right);
      }

      // fixed nbounds, the Common.processText's problem, cause the nbounds
      // is not the text's preferred size, instead the off set infomations is
      // sotred in lineoff, so here fix the bounds
      Bounds fnbounds = fixBounds(nbounds, lineoff);
      Bounds clip = null;

      // @by larryl, if the painting bounds is completely outside of the
      // clipping, don't attempt to draw anything
      if(cclip != null && (fnbounds.y >= cclip.y + cclip.height ||
         fnbounds.y + fnbounds.height <= cclip.y ||
         fnbounds.x >= cclip.x + cclip.width ||
         fnbounds.x + fnbounds.width <= cclip.x))
      {
         lines.removeAllElements();
      }
      // span cell make sure painting starts at text boundary
      // so it does not start at the middle of a line
      else if(cclip != null && area != null &&
         (!clipStr || area.height - area.y > 1 || area.width - area.x > 1))
      {
         float fheight = Common.getHeight(font);

         // @by davidd, Handle clipped bounds.
         // If bounds are outside of clip then ignore
         if((cclip.y >= fnbounds.y && cclip.y < fnbounds.y + fnbounds.height) ||
            (cclip.y + cclip.height > fnbounds.y &&
             cclip.y + cclip.height <= fnbounds.y + fnbounds.height) ||
            (cclip.x >= fnbounds.x && cclip.x < fnbounds.x + fnbounds.width ||
             cclip.x + cclip.width > fnbounds.x &&
             cclip.x + cclip.width <= fnbounds.x + fnbounds.width))
         {
            // if not repeat cell, we use the whold bounds to layout text,
            // this may be cause some rows just across pages, and then these
            // rows would be lost, here we relayout the text again
            if(!repeatCell && clipStr) {
               // reset bounds to cclip bounds, and layout same as repeatCell
               bounds = applyPadding(new Bounds(cclip));
               Object[] res = getClipString(row, col, str, xadj, yadj, fnbounds,
                                            cclip, useReaminderCache);
               str = (String) res[0];
               isRepeat = (Boolean) res[1];

               if(str != null) {
                  nbounds = new Bounds();
                  lineoff = new Vector(2);
                  Float rmax = (Float) pointlocmap.get(col);
                  float right = (rmax == null) ? (float) 0.0 : rmax.floatValue();
                  lines = Common.processText(str, bounds, align, wrap, font,
                     nbounds, lineoff, elem.getSpacing(), fm, right);
               }
               // already complete?
               else {
                  lines.removeAllElements();
               }
            }

            int si = 0;
            int ei = lines.size();
            // @by larryl, since the yadj (border) is already taken into account
            // by the cell bounds and clipping, doesn't seem necessary to adjust
            // it here. It actually mess up the calculation and cause a line to
            // be completely missed across page
            //float y = nbounds.y + yadj;
            float y = nbounds.y;
            int vlen = padding == null ? 0 : padding.top + padding.bottom;

            for(int n = 0; n < lines.size(); n++) {
               // not visible in this cell, starting at the next line
               // (0.5 is adj for inaccuracy in bounds calculation)
               // @by mikec, seems do not need adjust 0.5 here,
               // otherwise it will cause a line to be repeated across page.
               if(y + fheight <= cclip.y) {
                  si = n + 1;
               }
               // ignore 0.5 adjust from j2d
               //@by yanie: when print, if padding vertical length > 0 and
               //one more line can be draw by reduce the padding.top area,
               //then we will reduce the top to display one more line,
               //so herein we should follow the logic,
               //otherwise the last one will be repeated
               //in next page as the first line.
               else if(y + fheight > cclip.y + cclip.height + 0.5 + vlen) {
                  ei = n;
                  break;
               }

               y += fheight + elem.getSpacing();
            }

            // only partially visible, paint only
            // the visible part so text will not
            // be cut off
            if(si > 0 || ei < lines.size()) {
               Vector nlines = new Vector();
               Vector nlineoff = new Vector();

               // @by davyc, if this cell is the last cell of the span,
               // we force to show the whole remainder text, if there is
               // external space in height, the text will be broken in rows,
               // otherwise the text should not be broken, just show in columns
               // directly, we don't care about if it will be clipped by graphics
               // if this is the last page, force to show all text
               // if this span is repeat the original text, don't force to show
               // all remainder values, to make sure html don't expand too
               // large
               // fix bug1295004650108
               boolean spanEndInPage = clipStr && !isRepeat &&
                  // end row or column go out of current region?
                  !(row + area.height > reg.y + reg.height ||
                     col + area.width > reg.x + reg.width);

               // has external space in height? force break row
               if(spanEndInPage && ei < lines.size()) {
                  if(y + elem.getSpacing() + fheight / 3 <
                     cclip.y + cclip.height + 0.5)
                  {
                     ei = lines.size();
                  }

                  // at least show one row
                  ei = Math.min(lines.size(), Math.max(si + 1, ei));
               }

               for(int n = si; n < ei; n++) {
                  nlines.addElement(lines.elementAt(n));
                  nlineoff.addElement(lineoff.elementAt(n));
               }

               // has no external sapce in height? force break column
               if(spanEndInPage && ei < lines.size() &&
                  nlines.size() >= 1)
               {
                  int[] oend = (int[]) lines.get(lines.size() - 1);
                  int[] nend = (int[]) nlines.get(nlines.size() - 1);
                  nend[1] = oend[1];
               }

               lines = nlines;
               lineoff = nlineoff;
            }
         }

         // always need to relayout y to correct position
         Bounds rect = new Bounds(cclip);
         nbounds.height = lines.size() * fheight;
         // same as Common.processText

         // @by stephenwebster, For Bug #2680
         // Adjust the y position of the text with the padding so the text
         // is placed at the correct location.
         int paddingBottom = padding == null ? 0 : padding.bottom;
         int paddingTop = padding == null ? 0 : padding.top;
         nbounds.y = cclip.y +
            Math.max((paddingTop + cclip.height - paddingBottom - nbounds.height) / 2, 0);

         if(nbounds.height < cclip.height) {
            if((align & Common.V_BOTTOM) != 0) {
               nbounds.y = cclip.y + cclip.height - nbounds.height - 1 - paddingBottom;
            }
            else if((align & Common.V_TOP) != 0) {
               nbounds.y = cclip.y + paddingTop;
            }
         }

         rect.y = Math.max(rect.y, nbounds.y);
         rect.height = cclip.y + cclip.height - rect.y;
         clip = rect;
         nbounds.y = Math.max(nbounds.y, clip.y);
         /*
         // don't do pixel clipping, this achieve
         // character clipping instead
         nbounds = new Bounds(nbounds.x, rect.y, nbounds.width, rect.height);
         */
      }

      info = new CellText(str, lines, lineoff, nbounds, clip);

      if(clipStr) {
         linescache.put(ocell, info);
      }

      return info;
   }

   // if the table paintable is not yet printed, print it to initialize information for exporting
   private void initPaintable(Font font) {
      if(!printed) {
         EmptyGraphics eg = new EmptyGraphics();

         eg.setFont(font);
         paint(eg);
      }
   }

   /**
    * Apply padding for the bounds.
    */
   private Bounds applyPadding(Bounds bounds) {
      // adjust cell padding
      // only apply to string/text cells
      if(padding != null) {
         bounds.x += padding.left;
         bounds.y += padding.top;
         bounds.width -= padding.left + padding.right;
         bounds.height -= padding.top + padding.bottom;
      }

      return bounds;
   }

   /**
    * Fix the bounds.
    */
   private Bounds fixBounds(Bounds bounds, Vector lineoff) {
      float minoff = -1f;

      for(Object obj : lineoff) {
         if(obj instanceof Number) {
            if(minoff < 0) {
               minoff = ((Number) obj).floatValue();
            }
            else {
               minoff = Math.min(minoff, ((Number) obj).floatValue());
            }
         }
      }

      if(minoff > 0) {
         bounds = new Bounds(bounds);
         bounds.x = bounds.x + minoff;
         bounds.width = bounds.width - minoff;
      }

      return bounds;
   }

   /**
    * Check if a point is in the page.
    */
   public boolean inPage(Rectangle reg, int row, int col) {
      return (row >= 0 && row < headerR ||
         row >= reg.y && row < reg.y + reg.height) &&
         (col >= 0 && col < headerC || col >= reg.x && col < reg.x + reg.width);

      /*
      if(row >= reg.y && row < reg.y + reg.height &&
         col >= reg.x && col < reg.x + reg.width)
      {
         return true;
      }

      // first page?
      if(reg.y == headerR && reg.x == headerC) {
         return row >= 0 && row < reg.y + reg.height &&
                col >= 0 && col < reg.x + reg.width;
      }

      // first row page?
      if(reg.y == headerR) {
         return row >= 0 && row < reg.y + reg.height &&
                col >= reg.x && col < reg.x + reg.width;
      }

      // first column pgge?
      if(reg.x == headerC) {
         return row >= reg.y && row < reg.y + reg.height &&
                col >= 0 && col < reg.x + reg.width;
      }

      return false;
      */
   }

   /**
    * Find the start location for the text layout from.
    */
   private Point findStartLoc(int row, int col, Bounds tbounds, Bounds clip,
                              float xadj, float yadj)
   {
      Rectangle area = getSpan0(row, col);

      if(tbounds == null || area == null) {
         return new Point(col, row);
      }

      float ty = tbounds.y;
      float y = clip.y - yadj;
      int startR = row;

      while(startR >= 0 && y > ty) {
         startR--;
         y -= getRowHeight(startR);
      }

      float tx = tbounds.x;
      float x = clip.x - xadj;
      int startC = col;

      while(startC >= 0 && x > tx) {
         startC--;
         x -= getColWidth(startC);
      }

      startR = Math.max(0, startR);
      startC = Math.max(0, startC);
      int mrow = row + area.y;
      int mcol = col + area.x;

      for(int i = pgregs2.size() - 1; i >= 0; i--) {
         Rectangle reg = pgregs2.get(i);

         if(inPage(reg, startR, startC)) {
            int r1 = startR < headerR && reg.y == headerR ? startR : reg.y;
            r1 = Math.max(mrow, r1);
            startR = Math.min(r1, startR);
            int c1 = startC < headerC && reg.x == headerC ? startC : reg.x;
            c1 = Math.max(mcol, c1);
            startC = Math.min(startC, c1);
            break;
         }
      }

      return new Point(startC, startR);
   }

   /**
    * Get clipped string.
    * @param tbounds current text layout bounds in this page.
    * @return an two length's array object, the first value is string, for
    *  the remainder value, the second is a boolean, for check current text
    *  is the repeating text.
    */
   private Object[] getClipString(int row, int col, String str, float xadj0,
                                  float yadj0, Bounds tbounds, Bounds clip,
                                  boolean useReaminderCache)
   {
      Rectangle area = getSpan0(row, col);

      if(pgregs == null || str == null || area == null ||
         repeatCell && !repeatMergedCell(row, col))
      {
         return new Object[] {str, false};
      }

      int startR = row + area.y;
      int startC = col+ area.x;
      Point ocell = new Point(startC, startR);

      if(useReaminderCache && remaindercache != null &&
         remaindercache.containsKey(ocell))
      {
         return remaindercache.get(ocell);
      }

      // not repeat cell? we should find out the text is painted
      // from which pages span
      if(!repeatCell) {
         Point loc = findStartLoc(row, col, tbounds, clip, xadj0, yadj0);
         startR = loc.y;
         startC = loc.x;
      }

      // the span is started in current page?
      if(inPage(reg, startR, startC)) {
         return new Object[] {str, false};
      }

      int pgindex = -1; // default invalid page
      int size = pgregs.size();

      for(int i = size - 1; i >= 0; i--) {
         Rectangle reg = pgregs.get(i);

         if(inPage(reg, startR, startC)) {
            pgindex = i;
            break;
         }
      }

      // invalid page?
      if(pgindex < 0) {
         return new Object[] {str, false};
      }

      int endR = row + area.height;
      int endC = col + area.width;
      String substr = str;
      boolean repeat = false;
      boolean isRepeat = false;

      // find span in each page from the span's first page
      // to current page painted data, if previous page not
      // complete paint the data, then the next page will
      // continue the cut data, otherwise will be repeat the
      // full data
      for(int i = pgindex; i < size; i++) {
         // page size
         Bounds fbox = new Bounds(0, 0, 0, 0);
         Rectangle reg = pgregs.get(i);
         int r1 = reg.y == headerR && startR < headerR ? startR : reg.y;
         int crow = Math.max(r1, startR);
         int c1 = reg.x == headerC && startC < headerC ? startC : reg.x;
         int ccol = Math.max(c1, startC);
         int erow = Math.min(reg.y + reg.height, endR);
         int ecol = Math.min(reg.x + reg.width, endC);

         for(int r = reg.y; r < reg.y + reg.height; r++) {
            fbox.height += getRowHeight(r);
         }

         fbox.height += headerH;

         for(int c = reg.x; c < reg.x + reg.width; c++) {
            fbox.width += getColWidth(c);
         }

         fbox.width += headerW;

         int lastcol = (ccol == reg.x) ? (headerC - 1) : (ccol - 1);
         int lastrow = (crow == reg.y) ? (headerR - 1) : (crow - 1);
         float xadj = Common.getLineWidth(lens.getColBorder(crow, lastcol));
         float yadj = Common.getLineWidth(lens.getRowBorder(lastrow, ccol));

         boolean isSummary = summaryR == 1 && crow == reg.y + reg.height - 1;
         Rectangle span = new Rectangle(0, 0, ecol - ccol, erow - crow);

         // for any bounds of the following, we don't care of its x and y,
         // instead we just need to maintain the width and height correct
         Bounds bds = getCellBounds0(crow, ccol, span, fbox, reg,
                                     null, isSummary, true);
         Bounds pbounds = getPrintBounds0(crow, ccol, isSummary, span, reg, bds);
         Bounds cclip = getCellClip0(crow, ccol, bds, pbounds, fbox, xadj, yadj);
         int align = getAlignment0(crow, ccol, reg);

         /*
         if((align & StyleConstants.H_CURRENCY) == 0) {
            align = align & StyleConstants.H_LEFT;
            align = align & (~StyleConstants.H_CENTER);
            align = align & (~StyleConstants.H_RIGHT);
         }

         align = align & StyleConstants.V_TOP;
         align = align & (~StyleConstants.V_CENTER);
         align = align & (~StyleConstants.V_BOTTOM);
         */

         boolean wrap = isSummary ? summary.isSummaryLineWrap(startC) :
                                    lens.isLineWrap(startR, startC);
         Font fn = isSummary ? summary.getSummaryFont(startC) :
                               lens.getFont(startR, startC);
         fn = fn == null ? elem.getFont() : fn;
         FontMetrics fm = Common.getFractionalFontMetrics(fn);
         CellText tinfo = getCellText(crow, ccol, substr, cclip,
            align, wrap, fn, fm, span, cclip, xadj, yadj, false);

         if(tinfo != null) {
            int max = -1;
            Vector lines = tinfo.lines;

            for(int k = 0; k < lines.size(); k++) {
               int[] offs = (int[]) lines.get(k);
               max = Math.max(max, offs[1]);
            }

            if(max >= 0) {
               isRepeat = max >= substr.length();
               repeat = repeat || isRepeat;

               // @by cehnw, bug1329346939654
               // e.g. the text "abcd\nefg" need to be paint. getCellText cut
               // the string at index 3(abcd). "\n" left to next cell to paint,
               // But the next cell only have one line bounds, so the last line
               // lost. Here ignore character "\n" if the last line cut before
               // "\n" by chance.
               if(!isRepeat && "\n".equals(substr.substring(max, max + 1))) {
                  max++;
               }

               substr = !isRepeat ? substr.substring(max) :
                                    // not repeat? complete
                                    (!repeatCell ? null : str);
            }
         }

         if(substr == null) {
            break;
         }
      }

      if(remaindercache == null) {
         remaindercache = new HashMap<>();
      }

      Object[] vals = new Object[] {substr, repeat};
      remaindercache.put(ocell, vals);
      return vals;
   }

   private void readObject(java.io.ObjectInputStream s)
      throws ClassNotFoundException, java.io.IOException
   {
      s.defaultReadObject();
      elem = new BaseElement();
      ((BaseElement) elem).readObjectMin(s);

      int nrow = s.readInt();
      int ncol = s.readInt();
      int hrow = s.readInt();
      int hcol = s.readInt();
      int nh = Math.min(nrow, reg.y + reg.height + 1);
      int nw = Math.min(ncol, reg.x + reg.width + 1);
      int ry = Math.max(0, reg.y - 1);
      int rx = Math.max(0, reg.x - 1);
      RegionTableLens lens = new RegionTableLens(nrow, ncol, hrow, hcol, reg);
      AttributeTableLens attrLens = new AttributeTableLens(lens);
      this.lens = attrLens;

      // set header row height, then the header will not lost
      for(int i = 0; i < lens.getHeaderRowCount(); i++) {
         lens.setRowHeight(i, (int) getRowHeight(i));
      }

      for(int i = ry; i < nh; i++) {
         lens.setRowHeight(i, s.readInt());
      }

      for(int i = rx; i < nw; i++) {
         lens.setColWidth(i, s.readInt());
      }

      for(int i = reg.x; i < reg.x + reg.width; i++) {
         attrLens.setFormat(i, (Format) s.readObject());
      }

      if(reg.y > 0) {
         for(int i = 0; i < headerR; i++) {
            for(int j = reg.x; j < reg.x + reg.width; j++) {
               readCellCache(s, lens, attrLens, i, j, 0);
            }
         }
      }

      if(reg.x > 0) {
         for(int i = reg.y; i < reg.y + reg.height; i++) {
            for(int j = 0; j < headerC; j++) {
               readCellCache(s, lens, attrLens, i, j, reg.y);
            }
         }
      }

      if(lens.moreRows(0)) {
         // corner cells
         for(int i = 0; i < headerR; i++) {
            for(int j = 0; j < headerC; j++) {
               readCellCache(s, lens, attrLens, i, j, 0);
            }
         }
      }

      if(lens.moreRows(reg.y)) {
         // main region cells
         for(int i = reg.y; i < reg.y + reg.height; i++) {
            for(int j = reg.x; j < reg.x + reg.width; j++) {
               readCellCache(s, lens, attrLens, i, j, reg.y);
               Rectangle span = getSpan(i, j);

               if(span != null && (j == reg.x || span.x == 0) &&
                  (i == reg.y || span.y == 0))
               {
                  readCellCache(s, lens, attrLens, i + span.y, j + span.x, reg.y);
               }
            }
         }
      }

      int[][] rows = {
         {0, headerR + 1},
         {ry, nh},
         {lens.getRowCount() - 1, lens.getRowCount()}
      };

      for(int i = 0; i < rows.length; i++) {
         for(int j = rows[i][0]; j < rows[i][1] && lens.moreRows(j); j++) {
            for(int k = -1; k < lens.getColCount(); k++) {
               if(k >= 0 && k < rx) {
                  k = rx;
               }
               else if(k >= nw) {
                  k = lens.getColCount() - 1;
               }

               lens.setColBorder(j, k, s.readInt());
               lens.setColBorderColor(j, k, (Color) s.readObject());
            }
         }
      }

      int[][] cols = {
         {0, headerC + 1},
         {rx, nw},
         {lens.getColCount() - 1, lens.getColCount()}
      };

      for(int i = 0; i < cols.length; i++) {
         for(int j = cols[i][0]; j < cols[i][1]; j++) {
            for(int k = -1; k < lens.getRowCount(); k++) {
               if(k >= 0 && k < ry) {
                  k = ry;
               }
               else if(k >= nh) {
                  k = lens.getRowCount() - 1;
               }

               lens.setRowBorder(k, j, s.readInt());
               lens.setRowBorderColor(k, j, (Color) s.readObject());
            }
         }
      }

      lens.complete();
      pgregs = (Vector<Rectangle>) s.readObject();

      // @by billh, init method serves two aspects, to construct a
      // table paintable and to deserialize a table paintable, then
      // we should restore box for it might be changed by init method
      Bounds box2 = box == null ? null : (Bounds) box.clone();
      init();
      box = box2 != null ? box2 : box;
   }

   private void writeObject(ObjectOutputStream stream) throws IOException {
      stream.defaultWriteObject();
      ((BaseElement) elem).writeObjectMin(stream);

      stream.writeInt(lens.getRowCount());
      stream.writeInt(lens.getColCount());
      stream.writeInt(headerR);
      stream.writeInt(headerC);

      int nh = Math.min(lens.getRowCount(), reg.y + reg.height + 1);
      int nw = Math.min(lens.getColCount(), reg.x + reg.width + 1);
      int ry = Math.max(0, reg.y - 1);
      int rx = Math.max(0, reg.x - 1);

      for(int i = ry; i < nh; i++) {
         stream.writeInt(lens.getRowHeight(i));
      }

      for(int i = rx; i < nw; i++) {
         stream.writeInt(lens.getColWidth(i));
      }

      boolean isattrlens = lens instanceof AttributeTableLens;
      AttributeTableLens attrlens = null;

      if(isattrlens) {
         attrlens = (AttributeTableLens) lens;
      }

      for(int i = reg.x; i < reg.x + reg.width; i++) {
         // column format is needed for excel exporting
         if(isattrlens) {
            stream.writeObject(attrlens.getFormat(i));
         }
         else {
            stream.writeObject(null);
         }
      }

      AttributeInfo[] infos = new AttributeInfo[reg.width];
      AttributeInfo[] infos2 = attrlens != null && attrlens.moreRows(1) ?
         new AttributeInfo[reg.width] : null;

      for(int i = 0; i < reg.width; i++) {
         infos[i] = new AttributeInfo(attrlens, 0, reg.x + i);

         if(infos2 != null) {
            infos2[i] = new AttributeInfo(attrlens, 1, reg.x + i);
         }
      }

      if(reg.y > 0) {
         for(int i = 0; i < headerR; i++) {
            for(int j = reg.x; j < reg.x + reg.width; j++) {
               writeCellCache(stream, lens, attrlens, i, j, 0, infos, infos2,
                              reg.x);
            }
         }
      }

      infos = new AttributeInfo[headerC];
      infos2 = attrlens.moreRows(reg.y + 1) ? new AttributeInfo[headerC] : null;

      for(int i = 0; i < headerC; i++) {
         infos[i] = new AttributeInfo(attrlens, reg.y, i);

         if(infos2 != null) {
            infos2[i] = new AttributeInfo(attrlens, reg.y + 1, i);
         }
      }

      if(reg.x > 0) {
         for(int i = reg.y; i < reg.y + reg.height; i++) {
            for(int j = 0; j < headerC; j++) {
               writeCellCache(stream, lens, attrlens, i, j, reg.y, infos,
                              infos2, 0);
            }
         }
      }

      if(lens.moreRows(0)) {
         infos = new AttributeInfo[headerC];
         infos2 = attrlens.moreRows(1) ? new AttributeInfo[headerC] : null;

         for(int i = 0; i < headerC; i++) {
            infos[i] = new AttributeInfo(attrlens, 0, i);

            if(infos2 != null) {
               infos2[i] = new AttributeInfo(attrlens, 1, i);
            }
         }

         // corner cells
         for(int i = 0; i < headerR; i++) {
            for(int j = 0; j < headerC; j++) {
               writeCellCache(stream, lens, attrlens, i, j, 0, infos, infos2, 0);
            }
         }
      }

      if(lens.moreRows(reg.y)) {
         infos = new AttributeInfo[reg.width];
         infos2 = attrlens.moreRows(reg.y + 1) ?
            new AttributeInfo[reg.width] : null;

         for(int i = 0; i < reg.width; i++) {
            infos[i] = new AttributeInfo(attrlens, reg.y, reg.x + i);

            if(infos2 != null) {
               infos2[i] = new AttributeInfo(attrlens, reg.y + 1, reg.x + i);
            }
         }

         // main region cells
         for(int i = reg.y; i < reg.y + reg.height; i++) {
            for(int j = reg.x; j < reg.x + reg.width; j++) {
               writeCellCache(stream, lens, attrlens, i, j, reg.y, infos, infos2,
                              reg.x);
               Rectangle span = getSpan(i, j);

               if(span != null && (j == reg.x || span.x == 0) &&
                  (i == reg.y || span.y == 0))
               {
                  writeCellCache(stream, lens, attrlens, i + span.y, j + span.x,
                                 reg.y, infos, infos2, reg.x);
               }
            }
         }
      }

      int[][] rows = {
         {0, headerR + 1},
         {ry, nh},
         {lens.getRowCount() - 1, lens.getRowCount()}
      };

      for(int i = 0; i < rows.length; i++) {
         for(int j = rows[i][0]; j < rows[i][1] && lens.moreRows(j); j++) {
            for(int k = -1; k < lens.getColCount(); k++) {
               if(k >= 0 && k < rx) {
                  k = rx;
               }
               else if(k >= nw) {
                  k = lens.getColCount() - 1;
               }

               stream.writeInt(lens.getColBorder(j, k));
               stream.writeObject(lens.getColBorderColor(j, k));
            }
         }
      }

      int[][] cols = {
         {0, headerC + 1},
         {rx, nw},
         {lens.getColCount() - 1, lens.getColCount()}
      };

      for(int i = 0; i < cols.length; i++) {
         for(int j = cols[i][0]; j < cols[i][1]; j++) {
            for(int k = -1; k < lens.getRowCount(); k++) {
               if(k >= 0 && k < ry) {
                  k = ry;
               }
               else if(k >= nh) {
                  k = lens.getRowCount() - 1;
               }

               stream.writeInt(lens.getRowBorder(k,j));
               stream.writeObject(lens.getRowBorderColor(k, j));
            }
         }
      }

      stream.writeObject(pgregs);
   }

   private void readCell(ObjectInputStream s, RegionTableLens lens,
                         AttributeTableLens attrLens, int i, int j)
         throws ClassNotFoundException, IOException
   {
      lens.setRowBorder(i, j, s.readInt());
      lens.setColBorder(i, j, s.readInt());
      lens.setAlignment(i, j, s.readInt());
      lens.setLineWrap(i, j, s.readBoolean());
      lens.setRowBorderColor(i, j, readColor(s));
      lens.setColBorderColor(i, j, readColor(s));
      lens.setForeground(i, j, readColor(s));
      lens.setBackground(i, j, readColor(s));
      lens.setInsets(i, j, (Insets) s.readObject());
      lens.setSpan(i, j, (Dimension) s.readObject());
      lens.setFont(i, j, readFont(s));
      attrLens.setFormat(i, j, (Format) s.readObject());
      Object obj = s.readObject();

      if(obj instanceof ImageWrapper) {
         obj = ((ImageWrapper) obj).unwrap();
      }

      lens.setObject(i, j, obj);
   }

   private void readCellCache(ObjectInputStream stream, RegionTableLens lens,
                              AttributeTableLens attrLens,
                              int i, int j, int firstRow)
         throws ClassNotFoundException, IOException
   {
      byte flag = stream.readByte();

      if(flag == -1) {
         readCell(stream, lens, attrLens, i, j);
         return;
      }
      else {
         lens.setRowBorderColor(i, j,
                                lens.getRowBorderColor(firstRow + flag, j));
         lens.setColBorderColor(i, j,
                                lens.getColBorderColor(firstRow + flag, j));
         lens.setForeground(i, j, lens.getForeground(firstRow + flag, j));
         lens.setBackground(i, j, lens.getBackground(firstRow + flag, j));
         lens.setRowBorder(i, j, lens.getRowBorder(firstRow + flag, j));
         lens.setColBorder(i, j, lens.getColBorder(firstRow + flag, j));
         lens.setInsets(i, j, lens.getInsets(firstRow + flag, j));
         lens.setSpan(i, j, lens.getSpan(firstRow + flag, j));
         lens.setAlignment(i, j, lens.getAlignment(firstRow + flag, j));
         lens.setFont(i, j, lens.getFont(firstRow + flag, j));
         lens.setLineWrap(i, j, lens.isLineWrap(firstRow + flag, j));
         attrLens.setFormat(i, j, attrLens.getFormat(firstRow + flag, j));
         Object obj = stream.readObject();

         if(obj instanceof ImageWrapper) {
            obj = ((ImageWrapper) obj).unwrap();
         }

         lens.setObject(i, j, obj);
      }
   }

   private void writeCell(ObjectOutputStream stream, TableLens lens,
                          AttributeTableLens attrlens, int i, int j,
                          AttributeInfo cinfo)
         throws IOException
   {
      cinfo = cinfo != null ? cinfo : new AttributeInfo(attrlens, i, j);
      // write primitive types first so objectstream can block them together.
      stream.writeInt(cinfo.rowBorder);
      stream.writeInt(cinfo.colBorder);
      stream.writeInt(cinfo.alignment);
      stream.writeBoolean(cinfo.lineWrap);
      writeColor(stream, cinfo.rowBorderColor);
      writeColor(stream, cinfo.colBorderColor);
      writeColor(stream, cinfo.foreground);
      writeColor(stream, cinfo.background);
      stream.writeObject(cinfo.insets);
      stream.writeObject(cinfo.span);
      writeFont(stream, cinfo.font);

      if(attrlens != null) {
         stream.writeObject(cinfo.format);
      }
      else {
         stream.writeObject(null);
      }

      // make sure suppressZero and suppressDup is applied
      Object obj = getValue(lens, i, j);

      if(obj instanceof Image) {
         obj = new ImageWrapper((Image) obj);
      }

      stream.writeObject(obj);
   }

   private void writeCellCache(ObjectOutputStream stream, TableLens lens,
                               AttributeTableLens attrlens, int i, int j,
                               int firstRow, AttributeInfo[] infos,
                               AttributeInfo[] infos2, int xoff)
      throws IOException
   {
      Color color1;
      Color color2;
      Dimension span1;
      Dimension span2;
      Insets insets1;
      Insets insets2;
      Font font1;
      Font font2;
      Format format1;
      Format format2;
      byte code = -1;

      if(i <= firstRow + 1) {
         stream.writeByte(-1);
         writeCell(stream, lens, attrlens, i, j, null);
         return;
      }

      AttributeInfo cinfo = new AttributeInfo(attrlens, i, j);

      for(int k = firstRow; k <= firstRow + 1; k++) {
         code = (byte) (k - firstRow);
         AttributeInfo tinfo = k == firstRow ?
            infos[j - xoff] : infos2[j - xoff];
         color1 = cinfo.rowBorderColor;
         color2 = tinfo.rowBorderColor;

         if(!((color1 == null && color2 == null) ||
            (color1 != null && color1.equals(color2))))
         {
            code = -1;
            continue;
         }

         color1 = cinfo.colBorderColor;
         color2 = tinfo.colBorderColor;

         if(!((color1 == null && color2 == null) ||
            (color1 != null && color1.equals(color2))))
         {
            code = -1;
            continue;
         }

         color1 = cinfo.foreground;
         color2 = tinfo.foreground;

         if(!((color1 == null && color2 == null) ||
            (color1 != null && color1.equals(color2))))
         {
            code = -1;
            continue;
         }

         color1 = cinfo.background;
         color2 = tinfo.background;

         if(!((color1 == null && color2 == null) ||
            (color1 != null && color1.equals(color2))))
         {
            code = -1;
            continue;
         }

         font1 = cinfo.font;
         font2 = tinfo.font;

         if(!((font1 == null && font2 == null) ||
            (font1 != null && font1.equals(font2))))
         {
            code = -1;
            continue;
         }

         span1 = cinfo.span;
         span2 = tinfo.span;

         if(!((span1 == null && span2 == null) ||
            (span1 != null && span1.equals(span2)))) {
            code = -1;
            continue;
         }

         insets1 = cinfo.insets;
         insets2 = tinfo.insets;

         if(!((insets1 == null && insets2 == null) ||
            (insets1 != null && insets1.equals(insets2))))
         {
            code = -1;
            continue;
         }

         if(cinfo.rowBorder != tinfo.rowBorder ||
            cinfo.colBorder != tinfo.colBorder ||
            cinfo.alignment != tinfo.alignment ||
            cinfo.lineWrap != tinfo.lineWrap)
         {
            code = -1;
            continue;
         }

         format1 = cinfo.format;
         format2 = tinfo.format;

         if(!((format1 == null && format2 == null) ||
              (format1 != null && format1.equals(format2))))
         {
            code = -1;
            continue;
         }

         if(code != -1) {
            break;
         }
      }

      if(code == -1) {
         stream.writeByte(-1);
         writeCell(stream, lens, attrlens, i, j, cinfo);
         return;
      }
      else {
         stream.writeByte(code);
         Object obj = getValue(lens, i, j);

         if(obj instanceof Image) {
            obj = new ImageWrapper((Image) obj);
         }

         stream.writeObject(obj);
      }
   }

   /**
    * get padding
    */
   public Insets getPadding() {
      return padding;
   }

   /**
    * Get the border width of a column in points.
    */
   public float getRowBorderHeight(int row) {
      return row < 0 || row >= rowBorderH.length ? 0 : rowBorderH[row];
   }

   /**
    * Get the row height. Only the @rows in the current region can be accessed.
    */
   public float getRowHeight(int row) {
      int idx = (row < headerR) ?
         row + 1 : row - reg.y + headerR + (int) rowHeight[0] + 1;

      // may be negative, if out of bounds, we just consider it is zero height
      return idx > 0 && idx < rowHeight.length ? rowHeight[idx] : 0;
   }

   /**
    * Set the row height. Only the rows in the current region can be accessed.
    */
   public void setRowHeight(int row, float h) {
      rowHeight[(row < headerR) ?
                row + 1 : row - reg.y + headerR + (int) rowHeight[0] + 1] = h;
   }

   /**
    * Get the row height. Only the rows in the current region can be accessed.
    */
   private float getRowBorderH(int row) {
      int idx = (row < headerR) ? row + 1 :
         row - reg.y + headerR + (int) rowBorderH[0] + 1;

      // this is from previous code, seems the rowBorderH is one size too short
      // don't want to change since no problem has been reported and not
      // sure if this is indeed correct
      if(idx >= rowBorderH.length) {
         return R;
      }

      return Math.max(rowBorderH[idx], R);
   }

   /**
    * Copy a partial page regions.
    */
   private Vector<Rectangle> copyPGRegs(Vector<Rectangle> pgregs) {
      int[] tblr = getTopBottomLeftRight(true);
      int top = tblr[0];
      int left = tblr[2];

      // in current page?
      if(inPage(this.reg, top, left)) {
         return null;
      }

      Vector<Rectangle> regs = new Vector<>();
      int pgindex = -1;
      int size = pgregs.size();

      for(int i = size - 1; i >= 0; i--) {
         Rectangle reg = pgregs.get(i);

         if(inPage(reg, top, left)) {
            pgindex = i;
            break;
         }
      }

      if(pgindex >= 0) {
         for(int i = pgindex; i < size; i++) {
            regs.add(pgregs.get(i));
         }
      }

      return regs;
   }

   /**
    * Copy a partial row height array.
    */
   private float[] copyRowHeight(float[] rowHeight0) {
      int[] tblr = getTopBottomLeftRight(false);
      int top = tblr[0];
      int bottom = tblr[1];
      top = reg.y - top; // number of additional rows to keep above reg
      bottom = bottom - reg.y - reg.height; // additional rows below reg

      float[] arr = new float[headerR + reg.height + top + bottom + 1];
      arr[0] = top;

      for(int i = 0; i < arr.length - 1; i++) {
         int row = (i < headerR) ? i : i + reg.y - headerR - top;

         // this may be out of wack if the span specified is larger than table
         if(row < rowHeight0.length) {
            arr[i + 1] = rowHeight0[row];
         }
      }

      return arr;
   }

   /**
    * Get the top and bottom row index.
    */
   private int[] getTopBottomLeftRight(boolean includeHeader) {
      int top = reg.y;
      int bottom = reg.y + reg.height;
      int left = reg.x;
      int right = reg.x + reg.width;
      // this function may be called before init(), can't use class headerC
      int headerC = lens.getHeaderColCount();
      includeHeader = includeHeader && (reg.y == headerR || reg.x == headerC);

      // find top of spanned cells, and rows below bottom
      for(int i = 0; i < reg.width + headerC; i++) {
         int col = (i < headerC) ? i : reg.x - headerC + i;
         Rectangle area = getSpan(reg.y, col);
         Rectangle area2 = getSpan(reg.y + reg.height - 1, col);
         Rectangle areaHeader = null;

         if(includeHeader) {
            int irow = reg.y == headerR ? 0 : reg.y;
            int icol = reg.x == headerC ? 0 : col;
            areaHeader = getSpan(irow, icol);

            if(areaHeader != null) {
               top = Math.min(top, irow + areaHeader.y);
               left = Math.min(left, icol + areaHeader.x);
            }
         }

         if(area != null) {
            top = Math.min(top, reg.y + area.y);
            left = Math.min(left, col + area.x);
         }

         if(area2 != null) {
            bottom = Math.max(bottom, reg.y + reg.height + area2.height - 1);
            right = Math.max(right, reg.y + reg.height + area2.height - 1);
         }
      }

      return new int[] {top, bottom, left, right};
   }

   /**
    * Process the text for newlines, text wrapping, and alignment.
    * @param str text string.
    * @param fn font for printing.
    */
   private static float preProcessTextAlign(String str, Font fn) {
      int idx = str.lastIndexOf('.');
      float rmax = 0;

      if(idx == -1) {
         rmax = (float) 0.0;
      }
      else {
         String s = str.substring(idx);

         rmax = Common.stringWidth(s, fn);
      }

      return rmax;
   }

   /**
    * Return the string representaion.
    */
   public static String toString(TableDataPath path) {
      switch(path.getType()) {
      case TableDataPath.HEADER:
         return Catalog.getCatalog().getString("Header");
      case TableDataPath.DETAIL:
         return Catalog.getCatalog().getString("Detail");
      case TableDataPath.GROUP_HEADER:
         return Catalog.getCatalog().getString("GH") + (path.getLevel() + 1);
      case TableDataPath.SUMMARY:
         return Catalog.getCatalog().getString("GF") + (path.getLevel() + 1);
      case TableDataPath.GRAND_TOTAL:
         return Catalog.getCatalog().getString("Footer");
      }

      return path.toString();
   }

   // structure to hold cell text painting information
   public static class CellText implements Serializable {
      public CellText(String str, Vector lines, Vector lineoff, Bounds bounds,
                      Bounds clip) {
         this.text = str;
         this.lines = lines;
         this.lineoff = lineoff;
         this.bounds = bounds;
         this.clip = clip;
      }

      /**
       * Get text directly.
       */
      public String getText() {
         return text == null ? "" : text;
      }

      /**
       * Get text as one string by concatenate lines.
       */
      public String getText(String delim) {
         return getText(delim, 0, 0, null);
      }

      /**
       * Get text as one string by concatenate lines.
       * @param w if greater than zero, clip the lines at the width.
       * @param h if greater than zero, clip the lines at the height.
       * @param font must be supplied if clipping is true.
       */
      public String getText(String delim, double w, double h, Font font) {
         StringBuilder buf = new StringBuilder();
         float fontH = Common.getHeight(font);
         float totalH = 0;

         for(int i = 0; i < lines.size(); i++) {
            int[] offs = (int[]) lines.get(i);

            if(i > 0) {
               buf.append(delim);
            }

            String line = text.substring(offs[0], offs[1]);

            if(w > 0) {
               int idx = Util.breakLine(line, w, font, false);

               if(idx >= 0) {
                  line = line.substring(0, idx);
               }
            }

            if(h > 0) {
               totalH += fontH;

               if(totalH >= h && i > 0) {
                  break;
               }
            }

            buf.append(line);
         }

         return buf.toString();
      }

      public Vector getLines() {
         return lines;
      }

      public Bounds getBounds() {
         return bounds;
      }

      private String text; // original text in cell
      private Vector lines; // int[] offsets into str to get the text
      private Vector lineoff; // x offset to draw line
      private Bounds bounds; // bounds of text lines
      private Bounds clip; // clipping area
   }

   /**
    * AttributeInfo stores cell attributes.
    */
   private static class AttributeInfo implements Serializable {
      private AttributeInfo(AttributeTableLens table, int r, int c) {
         super();

         rowBorderColor = table.getRowBorderColor(r, c);
         colBorderColor = table.getColBorderColor(r, c);
         foreground = table.getForeground(r, c);
         background = table.getBackground(r, c);
         font = table.getFont(r, c);
         span = table.getSpan(r, c);
         insets = table.getInsets(r, c);
         rowBorder = table.getRowBorder(r, c);
         colBorder = table.getColBorder(r, c);
         alignment = table.getAlignment(r, c);
         lineWrap = table.isLineWrap(r, c);
         format = table.getFormat(r, c, true);
      }

      private Color rowBorderColor;
      private Color colBorderColor;
      private Color foreground;
      private Color background;
      private Font font;
      private Dimension span;
      private Insets insets;
      private int rowBorder;
      private int colBorder;
      private int alignment;
      private boolean lineWrap;
      private Format format;
   }

   public static class HashCache {
      public HashCache() {
         String prop = SreeEnv.getProperty("table.cachedcell.threshold");

         if(prop != null) {
            threshold = Double.valueOf(prop).intValue();
            threshold = Math.max(5000, threshold);
         }

         currentHolder.clear();
      }

      public void clear() {
         currentHolder.clear();
      }

      public Object get(Object key) {
         return currentHolder.get(key);
      }

      public void put(Object key, Object value) {
         checkUpperThreshold();

         currentHolder.put(key, value);
      }

      private void checkUpperThreshold() {
         if(currentHolder.size() > threshold) {
            currentHolder.clear();
         }
      }

      int threshold;
      Hashtable currentHolder = new Hashtable();
   }

   /**
    * Returns the current BindingAttr.
    */
   public BindingAttr getBindingAttr() {
      return this.binding;
   }

   /**
    * Get all hyperlinks of this element, including hyperlink and drill
    * hyperlinks.
    */
   public Hyperlink.Ref[] getHyperlinks(int r, int c) {
      if(getHyperlink(r, c) == null) {
         return getDrillHyperlinks(r, c);
      }

      Hyperlink.Ref[] dlinks = getDrillHyperlinks(r, c);
      Hyperlink.Ref[] links = new Hyperlink.Ref[dlinks.length + 1];
      links[0] = getHyperlink(r, c);

      for(int i = 0; i < dlinks.length; i++) {
         links[i + 1] = dlinks[i];
      }

      return links;
   }

   /**
    * Get the drill hyperlinks on this element.
    */
   public Hyperlink.Ref[] getDrillHyperlinks(int r, int c) {
      if(dmap == null) {
         return new Hyperlink.Ref[0];
      }

      Rectangle rec = getSpan(r, c);

      if(rec != null) {
         r += rec.y;
         c += rec.x;
      }

      Hyperlink.Ref[] dlinks = (Hyperlink.Ref[]) dmap.get(new Point(c, r));

      if(dlinks == null) {
         dlinks = new Hyperlink.Ref[0];
      }

      return dlinks;
   }

   /**
    * Set the drill hyperlinks of this element.
    */
   protected void setDrillHyperlinks(int r, int c, Hyperlink.Ref[] links) {
      // each time we set a drill, set it to the span position, and get is also
      // get from the span position, to make set/get working same, so get method
      // should make sure the r/c is the region self's r/c
      Rectangle span = getSpan(r, c);

      if(span != null) {
         r = r + span.y;
         c = c + span.x;
      }

      if(dmap == null) {
         dmap = new Hashtable();
      }

      Point key = new Point(c, r);

      if(links == null || links.length == 0) {
         dmap.remove(key);
      }
      // not added, or the span first cell
      else if(span != null && span.x == 0 && span.y == 0 ||
         !dmap.containsKey(key))
      {
         dmap.put(key, links);
      }
   }

   /**
    * Get the format object registered for this class.
    * @param type class to search for.
    * @return the format for this object.
    */
   public Format getFormat(Class type) {
      return StyleCore.getFormat(formats, type);
   }

   /**
    * Fire event.
    */
   private void fireEvent(Object obj, Rectangle rect, String cmd, Object obj2,
                          int row, int col)
   {
      if(listener != null) {
         ObjectInfo info = new ObjectInfo();
         info.obj = obj;
         info.obj2 = obj2;
         info.bounds = rect;
         info.row = row;
         info.col = col;
         ActionEvent event = new ActionEvent(info, 0, cmd);
         listener.actionPerformed(event);
      }
   }

   /**
    * Get report parameters.
    */
   private VariableTable getReportParameters() {
      ReportSheet report = ((BaseElement) elem).getReport();
      return report == null ? null : report.getVariableTable();
   }

   /**
    * Get showGroupMenu.
    */
   public Boolean isShowGroupMenu() {
      return showGroupMenu;
   }

   /**
    * Get showAggregateMenu.
    */
   public Boolean isShowAggregateMenu() {
      return showAggregateMenu;
   }

   /**
    * Get groupImgRec.
    */
   public Rectangle getGroupImgRec() {
      return groupImgRec;
   }

   /**
    * Get aggregateImgRec.
    */
   public Rectangle getAggregateImgRec() {
      return aggregateImgRec;
   }

   /**
    * Object info.
    */
   public static class ObjectInfo {
      public Object obj;
      public Object obj2;
      public Rectangle bounds;
      public int row = -1;
      public int col = -1;
   }

   private static final class TargetKey {
      private final String text;
      private final int level;
      private final int row;

      public TargetKey(String text, int level, int row) {
         this.text = text;
         this.level = level;
         this.row = row;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(!(o instanceof TargetKey)) {
            return false;
         }

         TargetKey targetKey = (TargetKey) o;

         if(level != targetKey.level) {
            return false;
         }

         if(row != targetKey.row) {
            return false;
         }

         if(text != null ?
            !text.equals(targetKey.text) : targetKey.text != null)
         {
            return false;
         }

         return true;
      }

      @Override
      public int hashCode() {
         int result = text != null ? text.hashCode() : 0;
         result = 31 * result + level;
         return result;
      }
   }

   private void refreshLastCol() {
      if(isContentMatchTableWidth()) {
         float[] temp = new float[colWidth.length];
         System.arraycopy(colWidth, 0, temp, 0, colWidth.length);
         colWidth = temp;
         // when alignment is left, table content start at 1, so here we need
         // to minus 1 pixel to make sure table content keep align with title.
         for(int i = reg.x + reg.width - 1; i >= 0; i--) {
            if(colWidth[i] > 0) {
               --colWidth[i];
               return;
            }
         }
      }
   }

   /**
    * Only for printlayout mode.
    * Check if columns width of current region is equal to the width of
    * the table element's printbounds.width.
    */
   private boolean isContentMatchTableWidth() {
      float w = 0;

      for(int i = 0; i < lens.getHeaderColCount(); i++) {
         w += colWidth[i];
      }

      for(int i = 0; i < reg.width; i++) {
         w += colWidth[reg.x + i];
      }

      // refrehsLastCol subtract 1 from colWidth, add at least 1 when checking
      // for content width
      float borderw = Math.max(1, colBorderW[reg.x + reg.width - 1]);
      Rectangle printb = getPrintBandBounds();

      if(printb == null) {
         return false;
      }

      float pw = printb.width;
      return w + borderw >= pw;
   }

   /**
    * check is calc table.
    */
   public boolean isCalc() {
      return this.calc;
   }

   /**
    * Get table type
    */
   public String getTableType() {
      return this.tableType;
   }

   public int getHeaderColCount() {
      return headerC;
   }

   private static final int R = 2;     // min resize width
   private static final Font LFONT = new StyleFont(StyleFont.DEFAULT_FONT_FAMILY, Font.PLAIN, 9);
   private static final String OMISSION_STR = "...";
   private static final char DOWN_ARROW = '\u2193';
   private static final char RIGHT_ARROW = '\u2192';
   private static final char GROUP_SYMBOL = '\u039e';
   private static final char SUMMARY_SYMBOL = '\u2211';
   private static final String SPEC_FONT = "Dialog";

   // border shade in design mode
   private static final Color shadeColor = new Color(214, 214, 214);
   private float height = 0;
   private float width = 0;
   private Rectangle reg;
   private float x;
   private float y;
   private Rectangle printBox;
   // box is the area to paint table region
   private Bounds box;
   private int headerR;
   private int headerC;

   // build the map where the borders exists
   // hor[row][col] is true if border should be drawn below the cell
   // ver[row][col] is true if border should be drawn at right of cell
   private transient int summaryR = 0;
   private transient int[][] hor;
   private transient int[][] ver;
   private transient float[] arowH; // cached accumulative row height
   private transient TableLens lens;
   private transient SummaryTableLens summary;
   private transient Bounds[][] cellbounds; // cached cell bounds
   // Point -> {lines, lineoff, bounds}
   private transient HashCache linescache;
   private transient Map<Point, Object[]> remaindercache;
   private transient boolean designtime;
   private transient Hashtable pointlocmap;
   private transient boolean printed;
   private transient MappedTableLens mappedTable; // for getting last row
   private transient PerRegionTableLens perTable; // for getting last row
   private transient boolean repeatCell;
   private boolean lastregion = false;

   // Point -> Hyperlink.Ref
   private Hashtable linkmap = null;
   // only applicable if the table is split using SpanTableLens
   private int[] rowmap = null; // row index -> base row index
   private float headerW;
   private float headerH;
   private float trailerH;
   private Hashtable sumspan;
   private Hashtable presenters;
   private Hashtable formats;
   private SpanMap spanmap;
   private Vector<Rectangle> pgregs;
   private Vector<Rectangle> pgregs2;

   private Int2ObjectOpenHashMap<Rectangle> groupMap = new Int2ObjectOpenHashMap<>();
   private Insets padding;

   // cols are normally few so they don't need to be saved partially
   private float[] colWidth;
   private float[] colBorderW;

   // rowHeight and rowBorderH is a partial array. It only keeps the
   // information for this region. However, since spanned celled need
   // access to info above this region, covering the span area, the
   // array needs to keep those information as well.
   // As a result, the array is organized as:
   // - The first item is an integer that contains a value
   // or zero. The value of that item is the number of
   // rows the array keeps above this current region
   private float[] rowHeight;
   private float[] rowBorderH; // partial array for current region
   private BindingAttr binding = null; // holds the current data binding
   // bug1253079634578 remove transient for RMI,
   // autodrill map should be serializable.
   // private transient Hashtable dmap = new Hashtable();
   private Hashtable dmap = new Hashtable(); // drill map
   public transient ActionListener listener; // for export
   private boolean calc = false; // is calc table

   private transient Map<String, String> idenMap;
   private Boolean showGroupMenu = false;
   private Boolean showAggregateMenu = false;
   private Rectangle groupImgRec = null;
   private Rectangle aggregateImgRec = null;
   private String tableType = "table";
}
