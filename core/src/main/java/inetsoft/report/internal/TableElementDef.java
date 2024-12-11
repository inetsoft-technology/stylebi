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

import inetsoft.report.*;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.filter.*;
import inetsoft.report.internal.binding.*;
import inetsoft.report.internal.info.*;
import inetsoft.report.internal.table.*;
import inetsoft.report.lens.*;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.uql.*;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.BorderColors;
import inetsoft.util.*;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.css.CSSConstants;
import inetsoft.util.profile.ProfileUtils;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.ScriptException;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Table element encapsulate the printing of a table.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class TableElementDef extends BaseElement
   implements TableElement, Tabular, TableGroupable, FormulaTable
{
   /**
    * Table format type.
    */
   public static final String TABLE_FORMAT = "format";
   /**
    * Table hyperlink type.
    */
   public static final String TABLE_HYPERLINK = "hyperlink";
   /**
    * Table highlight type.
    */
   public static final String TABLE_HIGHLIGHT = "highlight";
   /**
    * A constant object representing an area break.
    */
   public static final Object AREA_BREAK = new String("AreaBreak");

   /**
    * Create a table element from a table lens.
    */
   public TableElementDef(ReportSheet report, TableLens tablelens) {
      super(report, true);

      setTable(tablelens);
      setLayout(report.autosize);
      setPadding(report.padding);

      this.tableW = report.tableW;
      presenters = (Hashtable) report.presentermap.clone();
      formats = (Hashtable) report.formatmap.clone();

      // default to grow
      setProperty(GROW, "true");
   }

   /**
    * CSS Type to be used when styling this element.
    */
   @Override
   public String getCSSType() {
      return CSSConstants.TABLE;
   }

   /**
    * Set the report this element is contained in.
    */
   @Override
   public void setReport(ReportSheet report) {
      super.setReport(report);

      if(this.table instanceof CalcTableLens) {
         ((CalcTableLens) table).setReport(report);
         ((CalcTableLens) table).setElement(this);
      }
   }

   /**
    * Get script base table.
    */
   @Override
   public TableLens getScriptTable() {
      return scriptTable instanceof CalcTableLens ? null : scriptTable;
   }

   /**
    * Gets the additional script filter tables.
    */
   public TableLens[] getScriptTables() {
      return scriptTables;
   }

   /**
    * Set the table lens.
    */
   @Override
   public void setTable(TableLens table) {
      if(table != null) {
         // for Feature #26586, set report name which will be used when add post processing
         // record when process the filters.
         table.setProperty(XTable.REPORT_NAME, ProfileUtils.getReportSheetName(getReport()));
         table.setProperty(XTable.REPORT_TYPE, ExecutionBreakDownRecord.OBJECT_TYPE_REPORT);
      }

      if(table instanceof SummaryTableLens) {
         summary = (SummaryTableLens) table;
         // @by larryl, the summary is applied just after printTbl so it
         // will work (it's required as the top table)
         table = summary.getTable();
      }
      // calc table lens needs report for script
      else if(table instanceof CalcTableLens) {
         ((CalcTableLens) table).setReport(getReport());
         ((CalcTableLens) table).setElement(this);
      }

      if(!(table instanceof CalcJoinTableLens)) {
         this.table = table;
      }

      this.scriptTable = table;

      if(scriptTable instanceof CalcJoinTableLens) {
         scriptTables = ((CalcJoinTableLens) scriptTable).getTables();
      }
      else {
         scriptTables = null;
      }

      valid = false;
      resetFilter();
   }

   /**
    * Get the base table lens.
    */
   @Override
   public TableLens getBaseTable() {
      return table;
   }

   /**
    * Get the table to be used for printing.
    */
   @Override
   public TableLens getTopTable() {
      return toptable;
   }

   /**
    * Set the table to be used for printing. No additional filtering would
    * be done on this table. Used in scripting.
    */
   public void setTopTable(TableLens lens) {
      setTable(lens);
      toptable = lens;
   }

   /**
    * Get the table lens in this element.
    */
   @Override
   public TableLens getTable() {
      if(toptable != null) {
         return toptable;
      }

      // @by stephenwebster, For bug1430947680293
      // optimization to avoid unnecessary table filter process
      if(cancelled) {
         return getBaseTable();
      }

      // @by larryl, if for whatever reason the table is set to null (e.g. from
      // script), don't process filtering otherwise we may get null pointer
      // exceptions
      if(table == null) {
         return null;
      }

      // not yet apply grouping/crosstab or visibility in engine data process
      TableLens base;

      // engine data process over
      base = getBaseTable();

      // hyperlinks, format, and highlight are all defined on the layout if any
      base = getBaseTable();

      // apply table max row
      base = getMaxRowTable();

      // apply table style
      base = getStyleTable();

      // apply table hyperlink
      base = getStyleTable();

      if(isLeftAlignNumberCols()) {
         TableLens lens = base;

         while(lens != null) {
            if(lens instanceof AbstractTableLens) {
               ((AbstractTableLens) lens).setLeftAlign(true);
            }

            lens = lens instanceof TableFilter ?
               ((TableFilter) lens).getTable() : null;
         }
      }

      // apply table format
      base = getStyleTable();

      // @by larryl, highlight must be applied after table style and table
      // format otherwise the colors may be masked by table styles. It's
      // tricky for highlight condition lists require not yet formatted
      // objects, so when apply highlight, the objects should be gotten from
      // the base table of format table lens
      base = getStyleTable();

      // @by billh, encapsulate the table with an AttributeTableLens for:
      // 1. apply script related attributes,
      // always encapsulate the table doesn't occupy too much resource,
      // but in this way we needn't worry about attribute priorities, script
      // calls, api calls, etc, so I think it's worthwhile
      // @by larryl, we can't always create an AttributeTableLens here.
      // Otherwise if script is changing data[][] and cell level attributes
      // together, the attribute setting will be lost. The attrtable is
      // cleared once when the base data table changes or if the base data
      // table changes
      if(attrtable != null && base != null) {
         attrtable.setTable(base);
      }
      else if(base != null) {
         attrtable = new AttributeTableLens2(base);
      }

      toptable = attrtable;

      return toptable;
   }

   public boolean isLeftAlignNumberCols() {
      return "true".equals(getReport().getProperty("leftAlignNumber"));
   }

   /**
    * Get style table.
    */
   public TableLens getStyleTable() {
      return getMaxRowTable();
   }

   public TableLens getMaxRowTable() {
      TableLens base = getBaseTable();
      int maxRows = Util.getTableOutputMaxrow();

      if(maxRows <= 0) {
         return base;
      }

      //the global max row has set
      if(maxrowtable == null && !isDisposed()) {
         maxrowtable = new MaxRowsTableLens2(base, maxRows);
      }

      return maxrowtable;
   }

   /**
    * Get the print table.
    */
   public TableLens getPrintTable() {
      synchronized(tableReady) {
         while(!tableReady.get()) {
            try {
               if(cancelled) {
                  return null;
               }

               tableReady.wait(10000);
            }
            catch(Exception ex) {
            }
         }

         TableLens lens;
         lens = printTbl != null ? printTbl : getTable();

         if(summary != null) {
            summary.setTable(lens);
            lens = summary;
         }

         return lens;
      }
   }

   /**
    * Set the data in the table element.
    */
   @Override
   public void setData(TableLens model) {
      setTable(model);
   }

   /**
    * Get the data in the table element.
    */
   public TableLens getData() {
      return this.table;
   }

   /**
    * Get the filter info holder of this table.
    */
   @Override
   public BindingAttr getBindingAttr() {
      return ((TableElementInfo) einfo).getBindingAttr();
   }

   /*
    * @by larryl, the header setting was changed to use message format to
    * simulate the effect. Before (6.5) we stores the header setting in
    * TableElementInfo, including the header mapping and header structure
    * for multi-row headers. With the FreehandLayout, the multi-row header
    * will be handled using FreehandLayout. However, we can't completely
    * remove the header mapping for two reasons:
    * 1. Localization still depend on it. The localization logic probably
    * needs to be rewritten to handle FreehandLayout too.
    * 2. More importantly, for ad hoc reports, we should avoid using
    * FreehandLayout to control the headers since FreehandLayout is
    * position based (and has to be position based) and fixed column number.
    * If the columns are changed by users, the table may not be displayed
    * correct. By keeping a simple header mapping, we allow header text to
    * be set at design time, while retain the complete dynamic behavior of
    * regular tables.
    */

   /**
    * Set the column widths embedding option.
    */
   public void setEmbedWidth(boolean fix) {
      ((TableElementInfo) einfo).setEmbedWidth(fix);
   }

   /**
    * Check if column widths are embedded.
    */
   public boolean isEmbedWidth() {
      return ((TableElementInfo) einfo).isEmbedWidth();
   }

   /**
    * Set the column widths.
    */
   @Override
   public void setFixedWidths(int[] ws) {
      setFixedWidths(ws, null);
   }

   /**
    * Sets the column widths.
    *
    * @param ws   the column widths.
    * @param auto the automatic adjustments made to the widths.
    */
   public void setFixedWidths(int[] ws, Integer[] auto) {
      int[] ows = fixedWidths;

      setFixedWidths0(ws, auto);

      if(fixedWidths != null) {
         createFixedWidthMap(ows);
      }
   }

   /**
    * Set the column widths.
    *
    * @param ws   the column widths.
    * @param auto the automatic adjustments made to the column widths.
    */
   public void setFixedWidths0(int[] ws, Integer[] auto) {
      fixedWidths = ws;
      autoWidths = auto;
      fixedWidthMap = null;
      setEmbedWidth(ws != null);
   }

   /**
    * Get the fixed column widths in pixels.
    */
   @Override
   public int[] getFixedWidths() {
      return fixedWidths;
   }

   /**
    * Set the fixed row height. This setting is transient and is not permenently
    * stored in the report file.
    */
   public void setFixedHeights(int[] hs) {
      this.fixedHeights = hs;
   }

   /**
    * Get the column width in pixels.
    */
   @Override
   public float getColWidth(int col) {
      return colWidth[col];
   }

   /**
    * Get the calculated row height in pixels.
    */
   public float getRowHeight(int row) {
      return rowHeight[row];
   }

   /**
    * Get the table layout.
    */
   @Override
   public int getLayout() {
      return ((TableElementInfo) einfo).getLayout();
   }

   /**
    * Set the table layout.
    */
   @Override
   public void setLayout(int layout) {
      if(getLayout() != layout) {
         ((TableElementInfo) einfo).setLayout(layout);
         ((TableElementInfo) einfo).setEmbedWidth(false);
      }
   }

   /**
    * Get cell padding space.
    */
   @Override
   public Insets getPadding() {
      return ((TableElementInfo) einfo).getPadding();
   }

   /**
    * Set cell padding space.
    */
   @Override
   public void setPadding(Insets padding) {
      ((TableElementInfo) einfo).setPadding(padding);
   }

   /**
    * Get the table layout of the table.
    */
   @Override
   public TableLayout getTableLayout() {
      return ((TableElementInfo) einfo).getTableLayout();
   }

   /**
    * Set the table layout of the table.
    */
   @Override
   public void setTableLayout(TableLayout layout) {
      ((TableElementInfo) einfo).setTableLayout(layout);
   }

   /**
    * Set the table widow/orphan control option.
    * @param orphan true to eliminate widow/orphan rows.
    */
   @Override
   public void setOrphanControl(boolean orphan) {
      ((TableElementInfo) einfo).setOrphanControl(orphan);
   }

   /**
    * Check the current widow/orphan control setting.
    * @return widow/orphan control option.
    */
   @Override
   public boolean isOrphanControl() {
      return ((TableElementInfo) einfo).isOrphanControl();
   }

   /**
    * Return the size that is needed for this element.
    */
   @Override
   public Size getPreferredSize() {
      return new Size(400, 50); // used in dnd only
   }

   /**
    * Return true if the element can be broken into segments.
    */
   @Override
   public boolean isBreakable() {
      return ((TableElementInfo) einfo).isBreakable();
   }

   /**
    * Set whether this table should be printed on one page. This is currently only
    * supported for tables in sections.
    */
   public void setBreakable(boolean breakable) {
      ((TableElementInfo) einfo).setBreakable(breakable);
   }

   /**
    * Cancel the printing of the table.
    */
   public void cancel() {
      cancelled = true;
   }

   /**
    * Set the attributes of this element.
    */
   @Override
   public void setContext(ReportElement elem) {
      super.setContext(elem);

      if(elem instanceof Context) {
         Context c = (Context) elem;

         setLayout(c.getTableLayout());
         setPadding(c.getCellPadding());
      }
   }

   /**
    * Ignore the remaining print task if any.
    */
   @Override
   public void reset() {
      super.reset();

      // @by mikec, if this is a calc table,
      // it may have script that needs to be executed per repeat area.
      // We clone it so the tables would be showing the correct data.
      if(isCalc() && table instanceof CalcTableLens) {
         TableLens t2 = (TableLens) ((CalcTableLens) table).clone();
         setTable(t2);
      }

      // always reset column width regardless if it's in section
      colWidth = null;
      colBorderW = null;
   }

   /**
    * Reset onload script to reexecute it.
    */
   @Override
   public void resetOnLoad() {
   }

   /**
    * Reset the printing so the any remaining portion of the table
    * is ignored, and the next call to print start fresh.
    */
   @Override
   public void resetPrint() {
      synchronized(tableReady) {
         super.resetPrint();

         valid = false;
         cancelled = false;
         regions.removeAllElements();
         pgregs.removeAllElements();

         currentRegion = 0;
         printTbl = null;
         rowHeight = null;
         rowBorderH = null;
         spanmap = null;

         sumspan = null;

         // @by larryl, since the wrapping inside multitable may change if printed
         // multiple times, we need to create a new copy for every print otherwise
         // the multitable may be shared across multiple paintables (in section)

          // @by larryl, if a table in a section is printed the second time, we
         // keep the same column width so they lineup
         if(!isInSection()) {
            colWidth = null;
            colBorderW = null;
         }
      }
   }

   /**
    * Cause the next call to print this element to execute the script again.
    */
   @Override
   public void resetScript() {
      super.resetScript();

      // @by larryl, the scope in CalcTable needs to be cleared otherwise it
      // may point to the wrong parent
      if(table instanceof CalcTableLens) {
         ((CalcTableLens) table).invalidate();
      }
   }

   /**
    * Make sure the layout is done on this table.
    */
   public void validate(StylePage pg, ReportSheet report, TableLens table) {
      if(!valid) {
         valid = true;

         try {
            validate0(pg, report, table);
         }
         catch(ScriptException ex) {
            if(table == null) {
               Object[][] data = {
                  {Catalog.getCatalog().getString("Script Failed")},
                  {ex.getLocalizedMessage()}
               };

               setTopTable(new DefaultTableLens(data));
               valid = true;
               validate0(pg, report, table);
            }
         }
      }
   }

   private void validate0(StylePage pg, ReportSheet report, TableLens table) {
      // pick up the current setting in report
      presenters = report.createPresenterMap(presenters);
      formats = report.createFormatMap(formats);
      // validate will calculate the cell width so should expand the calc,
      // for example table set fit content.

      if(table == null) {
         table = preparePrintTableLens(report);
      }

      // @by larryl, empty table, never initialized or table set to null
      // by script
      if(table == null) {
         return;
      }

      table = prepareDesignTimePesudoTable(table, report);

      // if the script change the tablelens, re-do validate
      if(!valid) {
         validate(pg, report, getPrintTable());
         return;
      }

      layout(pg, report, table);
   }

   /**
    * Rewind a paintable. This is call if part of a element is undone
    * (in section). The paintable should be inserted in the next print.
    */
   @Override
   public void rewind(Paintable pt) {
      if(currentRegion > 0 && regions.get(currentRegion - 1).equals(
         ((TablePaintable) pt).getTableRegion()))
      {
         currentRegion--;
      }
   }

   /**
    * Get the pseudo table that used only for design time to avoid empty table.
    */
   private TableLens prepareDesignTimePesudoTable(TableLens tlens,
                                                  ReportSheet report) {
       if (isEmptyTable(tlens) && false) {
           DefaultTableLens lens = new DefaultTableLens(1, 1);
           lens.setObject(0, 0, Catalog.getCatalog().getString("<< Empty Table >>"));
           tlens = lens;
       }

       return tlens;
   }

   /**
    * Prepare a table lens that could be used in print.
    */
   private TableLens preparePrintTableLens(ReportSheet report) {
      TableLens lens = getPrintTable();

      if(lens != null) {
         // @by larryl, if a crosstab and empty (only the empty top-left corner),
         // ignore the table
         if(getBaseTable() instanceof Crosstab &&
            lens.moreRows(1) && !lens.moreRows(2) &&
            lens.getColCount() == 1)
         {
            lens = null;
         }
         else {
            lens = prepareDesignTimePesudoTable(lens, report);
         }
      }

      return lens;
   }

   /**
    * Check if it is an empty table.
    */
   private boolean isEmptyTable(TableLens lens) {
      return !lens.moreRows(0) || lens.getColCount() == 0;
   }

   /**
    * Return true if more is to be printed for this table.
    */
   @Override
   public boolean print(StylePage pg, ReportSheet report) {
      if(cancelled) {
         return false;
      }

      // execute javascript, it needs to be done before layout
      super.print(pg, report);

      if(!checkVisible()) {
         return false;
      }

      TableLens lens = preparePrintTableLens(report);

      // @by larryl, empty table, never initialized or table set to null
      // by script
      if(lens == null) {
         return false;
      }

      // layout() if first time or reset
      validate(pg, report, lens);

      // printtable may changed due to split
      lens = preparePrintTableLens(report);

      // check if table is empty
      if(isEmptyTable(lens) || !hasMore()) {
         return false;
      }

      // support page break inside a table, AREA_BREAK added in layout()
      Object regobj = regions.elementAt(currentRegion++);

      if(regobj == AREA_BREAK) {
         return true;
      }

      Rectangle reg = (Rectangle) regobj;
      boolean lastregion = currentRegion == regions.size();
      TablePaintable area = new TablePaintable(reg, report.printHead.x,
         report.printHead.y, report.printBox, headerW, headerH, trailerH,
         colWidth, rowHeight, lens, this, sumspan, spanmap, presenters,
         formats, getPadding(), rowBorderH, colBorderW, pgregs, lastregion
      );
      pgregs.add(reg);

      // add the next segment to the page
      // the segments are created in layout()
      pg.addPaintable(area);

      report.printHead.y += area.getHeight();
      report.printHead.x = 0;
      return hasMore();
   }

   /**
    * Check if table has more regions to be printed.
    */
   public boolean hasMore() {
      return currentRegion < regions.size();
   }

   /**
    * Get the next region (to print) index.
    */
   public int getNextRegion() {
      return currentRegion;
   }

   /**
    * Check the height of the next region.
    * @return 0 if needs to advance to next page, > 0 if the region fits
    * in the current page, < 0 if the region does not fit in the current
    * page.
    */
   public int fitNext(float avail) {
      if(currentRegion < regions.size()) {
         // check if an area break
         if(regions.elementAt(currentRegion) == AREA_BREAK) {
            currentRegion++;
            return 0;
         }

         float h = headerH;
         Rectangle reg = (Rectangle) regions.elementAt(currentRegion);
         float[] rowHeight = this.rowHeight;

         for(int i = 0; rowHeight != null && i < reg.height &&
            (reg.y + i) < rowHeight.length; i++)
         {
            h += rowHeight[reg.y + i];
         }

         // @by larryl, if only one pixel difference, allow it to accomodate
         // possible rounding errors
         return avail + 1 >= h ? 1 : -1;
      }

      return 1;
   }

   /**
    * Create a proper element info to save the attribute of this element.
    */
   @Override
   protected ElementInfo createElementInfo() {
      return new TableElementInfo();
   }

   /**
    * Create the temporary fixed width map to track width by column header.
    * @param ofixedWidths original fixed width setting.
    */
   private void createFixedWidthMap(int[] ofixedWidths) {
      TableLens vis = getBaseTable();

      fixedWidthMap = new Hashtable();

      vis.moreRows(0);

      for(int i = 0; i < fixedWidths.length && i < vis.getColCount(); i++) {
         Object colkey = getColumnPath(vis, i);

         // if the setting has not changed, don't override an existing
         // setting
         if(fixedWidthMap.get(colkey) != null) {
            if(ofixedWidths != null && i < ofixedWidths.length &&
               ofixedWidths[i] == fixedWidths[i])
            {
               continue;
            }
         }

         fixedWidthMap.put(colkey, fixedWidths[i]);
      }
   }

   /**
    * Get an unique path(s) to identify a column.
    */
   private Object getColumnPath(TableLens vis, int col) {
      TableDataDescriptor desc = vis.getDescriptor();

      Object path = desc.getColDataPath(col);

      if(path != null) {
         return path;
      }

      return col;
   }

   /**
    * Build the summary spane map.
    */
   private void buildSummarySpanMap(final int ncol) {
      // a span map is the span dimension for
      // all span cells (and their covered sub cells)
      sumspan = new Hashtable();

      if(summary != null) {
         for(int i = 0; i < ncol; i++) {
            Dimension span = summary.getSummarySpan(i);

            if(span != null) {
               for(int c = 0; c < span.width; c++) {
                  Rectangle area = new Rectangle(-c, 0, span.width - c, 1);
                  sumspan.put(c + i, area);
               }
            }
         }
      }
   }

   /**
    * Process each cell for the span information.
    */
   private void processSpanForEachCell(TableLens lens, int i, int j,
                                       SpanMap spanmap, final int ncolumn) {
      Dimension span = lens.getSpan(i, j);

      if(span != null) {
         // make sure the span does not go out of bounds
         if(span.width > ncolumn - j) {
            span = new Dimension(span);
            span.width = ncolumn - j;
         }

         spanmap.add(i, j, span.height, span.width);

         // @by larryl, at this point the attrtable has been modified
         // by script. For span cells, we take the border and set it
         // to the outer edge of the span cell since the table paintable
         // does not do this now. The table paintable was changed to
         // allow individual segment of span cell border to be controlled.
         // But if the border is set through script on a span cell, we
         // still like it to be applied to the entire cell for convenience
         // and backward compatibility.
         // only if the border was set in attrtable by script
         if(attrtable != null) {
            Integer rowborder = attrtable.getRowBorder0(i, j);
            Integer colborder = attrtable.getColBorder0(i, j);
            Color rowborderC = attrtable.getRowBorderColor0(i, j);
            Color colborderC = attrtable.getColBorderColor0(i, j);

            if(rowborder != null || colborder != null ||
               rowborderC != null || colborderC != null) {
               for(int si = 0; si < span.height; si++) {
                  for(int sj = 0; sj < span.width; sj++) {
                     if(si == 0 && sj == 0) {
                        continue;
                     }

                     if(rowborder != null) {
                        attrtable.setRowBorder(i + si, j + sj,
                                               rowborder.intValue());
                     }

                     if(colborder != null) {
                        attrtable.setColBorder(i + si, j + sj,
                                               colborder.intValue());
                     }

                     if(rowborderC != null) {
                        attrtable.setRowBorderColor(i + si, j + sj,
                                                    rowborderC);
                     }

                     if(colborderC != null) {
                        attrtable.setColBorderColor(i + si, j + sj,
                                                    colborderC);
                     }
                  }
               }
            }
         }
      }
   }

   /**
    * Calculate the remaining area of current page.
    */
   private Rectangle calcRemainingArea(ReportSheet report, float indw) {
      // area is the remaining area in the current page
      Rectangle area = new Rectangle(report.printBox);
      area.height -= report.printHead.y;

      // indentation can not be outside of parea
      if(indw >= area.width - 10) {
         indw = Math.max(0, area.width - 10);
      }

      area.x += indw;
      area.width -= indw;

      return area;
   }

   /**
    * Build the column width array.
    */
   private void buildColumnWidth(TableLens lens, final float defw, final int  ncol) {
      // calculate the table width, if table width is explicitly specified,
      // it's used, otherwise calculate from the page size and indentation
      // @by mikec, since we do not always subtract 2 pixel from tableWidth
      // the tableWidth will always larger than area.width since area is a int
      // and tableWidth is float, here make them identical.
      // @by larryl, pre-8.0, we always subtract 2 from the tableWidth. Changed
      // so it only substract if the right border exists. This is not completely
      // right but is more compatible with the previous versions, and allow the
      // right side to not have a small gap if user wants to fill to the right.
      // The result is if no right border, fill to right, otherwise leave a
      // single pixel.
      float tableWidth = (tableW > 0) ? (float) (tableW * 72) : defw;

      if(colBorderW[colBorderW.length - 1] > 0) {
         tableWidth -= colBorderW[colBorderW.length - 1] + 1;
      }

      final float eqw = tableWidth / ncol;

      // equal width for all columns
      if(isInSection() && colWidth != null) {
         // reuse the colWidth without re-calc
      }
      else if(getLayout() == ReportSheet.TABLE_EQUAL_WIDTH) {
         colWidth = new float[ncol];

         // @by mikec, logic to deal with col width set to zero by script.
         int realcol = 0;

         for(int i = 0; i < ncol; i++) {
            if(lens.getColWidth(i) != 0) {
               realcol ++;
            }
         }

         realcol = (realcol == 0) ? ncol : realcol;

         final float realeqw = tableWidth / realcol;

         for(int i = 0; i < ncol; i++) {
            if(lens.getColWidth(i) != 0) {
               colWidth[i] = realeqw;
            }
            else {
               colWidth[i] = 0;
            }
         }
      }

      // get width from lens
      if(colWidth == null || colWidth.length != ncol) {
         float[][] prefmin = null;
         colWidth = new float[ncol];
         prefmin = calcColWidth(eqw, lens);

         // calculate colWidth using prefmin
         int rem = 0; // number of remainder columns
         float used = 0; // used width
         float totalpref = 0; // total preferred size
         float totalmin = 0; // total min size
         float totalmore = 0; // total (preferred - minimum)

         // sum the total
         for(int i = 0; i < colWidth.length; i++) {
            if(prefmin[i][0] == StyleConstants.REMAINDER) {
               rem++;

               // remainder columns have preferred width of eqw
               if(getLayout() == ReportSheet.TABLE_FIT_PAGE) {
                  prefmin[i][0] = eqw;
               }
            }

            if(getLayout() == ReportSheet.TABLE_FIT_PAGE) {
               colWidth[i] = prefmin[i][1];
               totalpref += prefmin[i][0];
               totalmin += prefmin[i][1];
               totalmore += prefmin[i][0] - prefmin[i][1];
            }
            // fit size, use preferred size
            else {
               colWidth[i] = prefmin[i][0];
            }

            used += colWidth[i];
         }

         // colWidth contains minimum width of the columns now

         // use the width as proportion
         if(getLayout() == ReportSheet.TABLE_FIT_PAGE) {
            float diff = tableWidth - used;

            // if the table is too small, reduce the column widths
            // proportional to their minimum size
            if(diff < 0) {
               for(int i = 0; i < colWidth.length; i++) {
                  colWidth[i] += prefmin[i][1] * diff / totalmin;
               }
            }
            else {
               // width used to distribute to columns to satisfy the
               // preferred width
               float adj = Math.min(diff, totalmore);

               if(adj > 0 && totalmore > 0) {
                  for(int i = 0; i < colWidth.length; i++) {
                     colWidth[i] += (prefmin[i][0] - prefmin[i][1]) * adj /
                        totalmore;
                  }
               }

               adj = diff - adj;

               if(adj > 0) {
                  // if the table is too large, expand the column widths
                  // proportional to their preferred size
                  for(int i = 0; i < colWidth.length; i++) {
                     colWidth[i] += prefmin[i][0] * adj / totalpref;
                  }
               }
            }
         }
         // fit content, handle remainder
         else if(rem > 0) {
            float remsize = Math.max(eqw / 2, (tableWidth - used) / rem);

            // assign preferred size for colWidth
            for(int i = 0; i < colWidth.length; i++) {
               if(prefmin[i][0] == StyleConstants.REMAINDER) {
                  colWidth[i] = remsize;
               }

               if(isEmbedWidth() && fixedWidths[i] > colWidth[i]) {
                  fixedWidths[i] = (int) colWidth[i];
               }
            }
         }
      }

      // colWidth is the actual width of the columns at this point
   }

   /**
    * Calculate header width.
    */
   private float calcHeaderWidth(ReportSheet report, final int ncolumn,
                                 final int headerc) {
      float hwidth = 0;

      for(int j = 0; j < ncolumn && j < headerc; j++) {
         // calculate header column width
         hwidth += colWidth[j];
      }

      // if header fills up the entire page, shrink the header
      if(hwidth >= report.printBox.width - 1) {
         // reserve 1/6 for content
         float hw = report.printBox.width * 5 / 6.0f;
         float headerW2 = 0;

         for(int k = 0; k < headerc; k++) {
            colWidth[k] = colWidth[k] * hw / hwidth;
            headerW2 += colWidth[k];
         }

         hwidth = headerW2;

         LOG.warn(
            "Header columns are wider than page. Shrink header column width.");
      }

      // if the page is not wide enough to fit a single content cell
      // shrink the column width
      for(int j = headerc; j < ncolumn; j++) {
         if(hwidth + colWidth[j] > report.printBox.width) {
            colWidth[j] = report.printBox.width - hwidth;
         }
      }

      return hwidth;
   }

   /**
    * Calculate the header height.
    */
   private float calcHeaderHeight(StylePage pg, ReportSheet report, TableLens lens,
                                  final int ncolumn, final int headerr,
                                  final float max, final float[] rowht, final float[] rowbh,
                                  final Hashtable phcache,
                                  final float[] remainder)
   {
      float hheight = 0;

      for(int i = 0; lens.moreRows(i) && i < headerr; i++) {
         float rowborderh = calculateRowBorderHeight(lens, i, ncolumn);
         float rowh = calculateRowHeight(fixedHeights, colWidth, remainder,
                                         lens, rowborderh, i, phcache, ncolumn,
                                         false, false, null)[0];

         rowbh[i] = rowborderh;
         rowht[i] = rowh;

         if(rowht[i] > max) {
            rowht[i] = max;
         }

         hheight += rowht[i];
      }

      // if header fills up the entire page, shrink the header
      if(hheight >= report.printBox.height * 7 / 8 && !isInSection()) {
         // reserve 1/8 for content
         float hh = report.printBox.height * 7 / 8;
         float headerH2 = 0;

         for(int i = 0; i < headerr; i++) {
            rowht[i] = rowht[i] * hh / hheight;
            headerH2 += rowht[i];
         }

         hheight = headerH2;

         if(isCalc()) {
            pg.addInfo("Header rows are taller than page. Shrink header row height:" + getID());
         }

         LOG.warn(
            "Header rows are taller than page. Shrink header row height.");
      }

      return hheight;
   }

   /**
    * Get basic line height of the table.
    */
   private float getBasicRowHeight(TableLens lens, int i) {
      Font font = lens.getFont(i, 0);

      if(font == null) {
         font = getFont();
      }

      float fontHeight = Common.getHeight(font);

      // @by davidd bug1323413753347 2011-12-12 Basic row height must obey the
      // minimum row height restriction.
      if(lens.getRowHeight(i) < -1) {
         return Math.max(fontHeight, -lens.getRowHeight(i));
      }

      return fontHeight;
   }

   /**
    * Calculate table column width and row height according to the
    * current font and layout policy. Create regions to print if
    * the table span across rows/pages.
    */
   private void layout(StylePage pg, ReportSheet report, TableLens lens) {
      if(isEmptyTable(lens)) {
         return;
      }

      // @stephenwebster, For bug1417535455177
      // For single page report output, prevent Area Breaks from being added.
      // I put a safeguard in ReportSheet.printNextLine.  The changes below
      // using the singlePage variable are done to ensure the table is seamless,
      // without any gaps.  Without these changes, the entire table may not be printed
      // and a small gap will show between the regions.  There should only be one
      // continuous region.
      // @by stephenwebster, For Bug #1564, treat a case where a table is in a
      // section or does not have auto size enabled, so a partial table gets
      // properly chunked and does not result in endless looping.  Meaning,
      // revert the single page flag in this case.
      boolean useSinglePage =
         !isInSection() || ("true".equals(getProperty(ReportElement.AUTOSIZE)));
      boolean singlePage = pg instanceof ReportGenerator.SingleStylePage
         && useSinglePage;

      float indw = (float) (getIndent() * 72 + getHindent());
      Rectangle area = calcRemainingArea(report, indw);

      // check if the table is a summary table
      if(summary != null) {
         trailerH = summary.getSummaryHeight();
      }

      final int ncolumn = lens.getColCount();

      buildSummarySpanMap(ncolumn);
      final int headerR = lens.getHeaderRowCount();
      final int headerC = lens.getHeaderColCount();

      if(headerC == ncolumn) {
         LOG.warn(
            "All columns are marked as header. This may not work if the " +
            "table is larger than a page.");
      }

      // build span map, a span map is the span dimension for all span
      // cells (and their covered sub cells
      // can't reuse the spanmap in case table is in section and the
      // span map will be shared across table paintables
      spanmap = new SpanMap();

      for(int i = 0; lens.moreRows(i) && i < headerR; i++) {
         for(int j = 0; j < ncolumn; j++) {
            processSpanForEachCell(lens, i, j, spanmap, ncolumn);
         }
      }

      if(cancelled) {
         return;
      }

      buildColBorderWidth(lens, ncolumn, headerC);
      buildColumnWidth(lens, area.width, ncolumn);
      headerW = calcHeaderWidth(report, ncolumn, headerC);

      float remainingSpace = report.printBox.height - report.printHead.y;
      float maxCellFirstPage = Math.max(remainingSpace, MAX_CELL_H);
      float maxCellWholePage = Math.max((float) report.printBox.height, MAX_CELL_H);

      Hashtable phcache = new Hashtable();
      Hashtable oldCache = new Hashtable();
      float[] remainder = new float[] {remainingSpace, 0};

      float[] rowbh = new float[headerR];
      float[] rowht = new float[headerR];
      int rcount = headerR;

      headerH = calcHeaderHeight(pg, report, lens, ncolumn, headerR,
                                 maxCellFirstPage, rowht, rowbh, phcache,
                                 remainder);

      // headerH needs to be subtracted from the max row height
      maxCellWholePage = Math.max(maxCellWholePage - headerH, MAX_CELL_H);
      maxCellFirstPage = Math.max(maxCellFirstPage - headerH, MAX_CELL_H);

      float dynamicMaxCell = maxCellFirstPage;

      BitSet remainders = new BitSet();
      boolean hasRemainder = false;
      boolean tablesplitted = false;

      TableLens printlens = lens;
      TableLens printlens0 = printlens;
      Insets padding = getPadding();
      boolean firstSplittedRow = false;
      int splittedRows = 0;

      try {
         for(int i = headerR; printlens0.moreRows(i) && !cancelled; i++) {
            for(int j = 0; j < ncolumn; j++) {
               // @by humming, fix bug1208768768171, make sure the highlight
               // was calculated before set the other condition table
               printlens0.getFont(i, j);
            }

            float rowborderh = calculateRowBorderHeight(printlens0, i, ncolumn);
            float[] rowhs = calculateRowHeight(fixedHeights, colWidth, remainder,
               printlens0, rowborderh, i, phcache, ncolumn, true, firstSplittedRow, oldCache);
            float rowh = rowhs[0];

            if(rowh == StyleConstants.REMAINDER) {
               remainders.set(i);
               hasRemainder = true;
            }

            if(i >= rowbh.length) {
               float[] tmpb = new float[(int) ((rowbh.length + 10) * 1.5)];
               float[] tmph = new float[(int) ((rowht.length + 10) * 1.5)];

               System.arraycopy(rowbh, 0, tmpb, 0, rowbh.length);
               System.arraycopy(rowht, 0, tmph, 0, rowht.length);

               rowbh = tmpb;
               rowht = tmph;
            }

            boolean splitcell = false;
            float basicRowHeight = getBasicRowHeight(printlens0, i);
            float splitH = Math.min(basicRowHeight * 3, dynamicMaxCell);
            int n = (int) Math.ceil(rowhs[1] / splitH);

             if(!false &&
               (splittedRows > 0 && dynamicMaxCell < rowh ||
                dynamicMaxCell < basicRowHeight || rowhs[1] / n < basicRowHeight))
            {
               dynamicMaxCell = maxCellWholePage;
            }

             if(rowh != StyleConstants.REMAINDER && splittedRows == 0 &&
               rowh > dynamicMaxCell && !false)
            {
               // split cells if necessary to allow large cell to span across
               // pages
               // @by larryl, at design time we should not split the cells since
               // many logic assumes the table structure is fixed after the
               // LayoutTable. If the table is split, the calculation of table
               // row/column and span would be out of sync
               int si = i;

               if(printTbl == null) {
                  printTbl = new SpanTableLens(lens);
               }
               else {
                  si = ((SpanTableLens) printTbl).getBaseRowIndex(i);
               }

               if(((SpanTableLens) printTbl).split(si, n)) {
                  adjustFixedHeights(i, n);
                  tablesplitted = true;
                  splitcell = true;
                  firstSplittedRow = true;
                  i--;
                  clearPHeightCache(phcache, i + 1, n - 1, ncolumn);
                  phcache.putAll(oldCache);
                  splittedRows = n;
                  spanmap.adjust(i + 1, n - 1);
               }

               printlens0 = printTbl;
            }
            else {
               rowbh[i] = rowborderh;
               rowht[i] = rowh;
               rcount++;
               oldCache.clear();

               if(splittedRows > 0) {
                  //@by yanie: for split cell span, only one padding and border
                  //should be considered, for first line, keep padding.top and
                  //for last line, keep padding.bottom and row border height
                  if(firstSplittedRow) {
                     firstSplittedRow = false;
                     rowht[i] = rowht[i] - padding.bottom;
                  }
                  else {
                     rowht[i] = rowht[i] - padding.top;

                     if(splittedRows != 1) {
                        rowht[i] = rowht[i] - rowborderh - padding.bottom;
                        rowbh[i] = 0;
                     }
                  }

                  splittedRows--;
               }
            }

            if(!splitcell) {
               dynamicMaxCell -= rowh;

               if(dynamicMaxCell <= 0) {
                  dynamicMaxCell = maxCellWholePage;
               }

               for(int j = 0; j < ncolumn; j++) {
                  processSpanForEachCell(printlens0, i, j, spanmap, ncolumn);
               }
            }
         }
      }
      finally {
         // complete the print table to generate a swappable row map
         if(printTbl instanceof SpanTableLens) {
            ((SpanTableLens) printTbl).complete();
         }

         if(getTableLayout() != null) {
            LOG.debug(
               "Table " + getID() + " finished processing: " +
                  lens.getRowCount());
         }
      }

      lens = tablesplitted ? printlens0 : printlens;

      if(rcount < rowbh.length) {
         float[] tmpb = new float[rcount];
         float[] tmph = new float[rcount];

         System.arraycopy(rowbh, 0, tmpb, 0, rcount);
         System.arraycopy(rowht, 0, tmph, 0, rcount);

         rowbh = tmpb;
         rowht = tmph;
      }

      if(hasRemainder && remainder[1] > 0 && remainder[0] > 3) {
         for(int i = 0; i < rcount; i++) {
            if(remainders.get(i)) {
               rowht[i] = (remainder[0] - 3) / remainder[1];
            }
         }
      }

      rowHeight = rowht;
      rowBorderH = rowbh;

      // reset the regions
      regions.removeAllElements();
      pgregs.removeAllElements();

      currentRegion = 0;

      // if fit content, we need to handle row wraps
      // find the regions that can fit into pages

      // lu the left-upper corner of the current region
      Point lu = new Point(headerC, headerR);
      int lastH = 0;
      int frameidx = report.currFrame + 1; // next frame
      Rectangle oarea = new Rectangle(area); //backup the old area

      // go through the table and break the table into regions
      // that can fit in one page
      while(lu.x < colWidth.length && lu.y < rowHeight.length && !cancelled) {
         Rectangle reg = new Rectangle(lu.x, lu.y, colWidth.length - lu.x,
                                       rowHeight.length - lu.y);

         Rectangle nextarea = new Rectangle((report.npframes == null ||
            frameidx < report.frames.length) ? report.frames[frameidx % report.frames.length] :
            report.npframes[(frameidx - report.frames.length) % report.npframes.length]);

         // indent
         nextarea.x += indw;
         nextarea.width -= indw;

         // if the top row is no immediately below the header adjust the row
         // height to reflect the different row border (header vs. row on top)
         if(reg.y != headerR && reg.x == headerC) {
            rowHeight[reg.y] -= rowBorderH[reg.y] - rowBorderH[headerR];
         }

         // if the left col is not immediately right of the header adjust the
         // col width to reflect the different column border
         // (header vs. col on left)
         if(reg.x != headerC && reg.y == headerR) {
            colWidth[reg.x] -= colBorderW[reg.x] - colBorderW[headerC];
         }

         float w = headerW;
         // check the page width and break if the table is wider than
         // the page width
         for(int i = lu.x; i < colWidth.length; i++) {
            w += colWidth[i];

            // cut off the table if the width is larger than page
            // @by mikec, use int value to compare to avoid any
            // calculation accumulated diff.
            if((int) w > area.width) {
               reg.width = i - lu.x;

               // restore width value
               w -= colWidth[i];
               break;
            }
         }

         // flag indicates that we find a non-span column to split table
         boolean found = false;
         // old width value
         float ow = w;
         boolean pgbreak = false; // true if break because of in-table break

         // if all columns are in the same span, we do not find a position
         // to split table, then we should restore width value for later use
         if(!found) {
            w = ow;
         }

         // if fit the segment to page width, adjust the width
         // of the last column in the table segment
         // if has column matching, TABLE_FIT_CONTENT_PAGE doesn't work
         if(getLayout() == ReportSheet.TABLE_FIT_CONTENT_PAGE && reg.width > 0) {
            int last = lu.x + reg.width - 1;
            float adj = area.width - w >= 0 ? area.width - w : 0;
            colWidth[last] += adj;
         }

         // if not the first part of a row, use the last number of rows
         if(lu.x != headerC) {
            reg.height = lastH;

            // make sure the table region is not taller than area
            float h = headerH + trailerH;

            for(int i = lu.y; i < reg.y + reg.height; i++) {
               h += rowHeight[i];
               // only add page break element after the whole row is printed,
               // which looks better, especially for page break after group.
               // ignore page break at design time. (50182)
                pgbreak = (lens.getRowBorder(i, 0) & TableLens.BREAK_BORDER) != 0 && !singlePage &&
                  !false;

               if(h > area.height) {
                  reg.height = 0;
                  break;
               }
            }
         }
         else {
            // find the rows to include in this region so they don't
            // go across page
            float h = headerH + trailerH;

            for(int i = lu.y; i < rowHeight.length; i++) {
               h += rowHeight[i];
                pgbreak = (lens.getRowBorder(i, 0) & TableLens.BREAK_BORDER) != 0 && !singlePage &&
                  !false;

               if(h > area.height) {
                  reg.height = lastH = i - lu.y;
                  break;
               }
               else if(pgbreak) {
                  reg.height = lastH = i - lu.y + 1;

                  // only add page break element after the whole row is printed,
                  // which looks better, especially for page break after group
                  pgbreak = lu.x + reg.width == lens.getColCount();
                  break;
               }
            }
         }

         // page too small, cut off the row/column
         if((reg.width == 0 || reg.height == 0) && area.equals(oarea)) {
            if(reg.height == 0 && nextarea.height <= area.height) {
               // resize the row/column to fit the page
               reg.height = Math.max(reg.height, 1);
               float h2 = headerH;

               for(int i = 0; i < reg.height - 1; i++) {
                  h2 += rowHeight[reg.y + i];
               }

               rowHeight[reg.y + reg.height - 1] = area.height - h2;
               LOG.warn("Header rows too tall. Check header row count.");
            }

            if(reg.width == 0 && nextarea.width <= area.width) {
               // resize the row/column to fit the page
               reg.width = Math.max(reg.width, 1);
               float w2 = headerW;

               for(int i = 0; i < reg.width - 1; i++) {
                  w2 += colWidth[reg.x + i];
               }

               colWidth[reg.x + reg.width - 1] = area.width - w2;
               LOG.warn(
                  "Header columns too wide. Check header column count.");
            }
         }

         // check for orphan/widow
         if(isOrphanControl() && !singlePage) {
            if(lu.x == headerC && reg.height == 1 &&
               rowHeight.length - lu.y > 2 &&
               // if subreport, the printHead.y is always 0 regardless where it
               // starts on a page
               report.printHead.y > 0)
            {
               int size = regions.size();

               // only push the engine region to next page if the next page
               // can fit two rows
               if(nextarea.height >= headerH + rowHeight[lu.y] + rowHeight[lu.y + 1] &&
                  (size == 0 || regions.get(size - 1) != AREA_BREAK))
               {
                  reg.height = 0;
                  regions.addElement(AREA_BREAK);
               }
            }
            else {
                if (lu.x == headerC && reg.height > 2 &&
                        rowHeight.length - reg.y - reg.height == 1 && !false) {
                    reg.height--;
                    pgbreak = true;
                }
            }
         }

         // if only room for the header, advance to next page
         if(reg.width > 0 && reg.height > 0) {
            // add a new region
            regions.addElement(reg);

            // signal an area break
            if(pgbreak) {
               regions.addElement(AREA_BREAK);
            }

            // adjust lu
            if(reg.x + reg.width == colWidth.length) {
               lu.x = headerC;
               lu.y += reg.height;
            }
            else {
               lu.x += reg.width;
            }

            // lastH is the number of rows in the last region
            lastH = reg.height;

            // adjust area
            float adj = headerH + trailerH;

            for(int i = reg.y; i < reg.y + reg.height; i++) {
               adj += rowHeight[i];
            }

            area.height -= adj;
            area.y += adj;
         }
         // no space for any rows on the current page
         else {
            // no next page, ignore the table to avoid infinite loop
            if(singlePage) {
               LOG.warn("No space to print table on the table, ignored: {}", getID());
               break;
            }
            // force new page
            else {
               area.height = 0;
            }

            // @by billh, force to go to next page if could not print anything
            if(regions.size() == 0 || regions.get(regions.size() - 1) != AREA_BREAK) {
               regions.addElement(AREA_BREAK);
            }
         }

         int layout = getLayout();
         int ccnt = colWidth.length;
         int rcnt = rowHeight.length;

         // goto new page if at end of page
         // or if layout is TABLE_FIT_CONTENT_1PP
         if(layout == ReportSheet.TABLE_FIT_CONTENT_1PP || pgbreak ||
            lu.y < rowHeight.length &&
            area.height < headerH + rowHeight[lu.y] + 30 && !singlePage)
         {
            // @by billh, force to go to next page if each page one paintable.
            // In this way, table in section could also support this feature
            if(layout == ReportSheet.TABLE_FIT_CONTENT_1PP &&
               ((reg.x + reg.width) < ccnt || (reg.y + reg.height) < rcnt) &&
               regions.size() > 0 &&
               regions.get(regions.size() - 1) != AREA_BREAK)
            {
               regions.addElement(AREA_BREAK);
            }

            // go to next frame
            frameidx++;
            area = nextarea;
            oarea = new Rectangle(area);
         }
      }

      // only header, no body
      if(regions.size() == 0 && (lu.x > 0 || lu.y > 0)) {
         float w = headerW;
         lens.moreRows(XTable.EOT);

         // break up into regions (horizontally) if necessary
         for(int i = lu.x; i < colWidth.length; i++) {
            w += colWidth[i];

            // cut off the table if the width is larger than page
            if(w > area.width) {
               int width = i - lu.x;
               regions.add(new Rectangle(lu.x, lu.y, width, lens.getRowCount() - lu.y));
               w = headerW + colWidth[i];
               lu.x += width;
            }
         }

         if(lu.x <= lens.getColCount()) {
            regions.addElement(new Rectangle(lu.x, lu.y, lens.getColCount() - lu.x,
               lens.getRowCount() - lu.y));
         }
      }

      // @by larryl, set the span map so format can use the cell span
      // information without re-building the spanmap
      if(getStyleTable() instanceof FormatTableLens) {
         FormatTableLens tbl = (FormatTableLens) getStyleTable();
         tbl.clearCellCache();

         if(tbl.getRowCount() == lens.getRowCount() &&
            tbl.getColCount() == lens.getColCount())
         {
            tbl.setSpanMap(spanmap);
         }
         else {
            tbl.buildSpanMap();
         }
      }
   }

   // adjust fixed heights for split rows. (62468)
   // @param i the row index that is split.
   // @n the number of rows the row is split into.
   private void adjustFixedHeights(int i, int n) {
      if(fixedHeights != null && i < fixedHeights.length) {
         for(int k = 0; k < n - 1 && i + k < fixedHeights.length; k++) {
            // if the value is -1, divide by 2 will become 0, which is incorrect. (62709)
            if(fixedHeights[i + k] > 1) {
               fixedHeights[i + k] /= 2;
            }

            fixedHeights = ArrayUtils.insert(i + k, fixedHeights, fixedHeights[i + k]);
         }
      }
   }

   /**
    * Clears the Preferred Height cache when splitting a cell across pages.
    * Excludes cell heights calculated as part of the current span, and adjusts their position.
    */
   private void clearPHeightCache(Hashtable oPHCache, int row, int newRows, int colCount) {
      Hashtable nCache = new Hashtable();

      for(int i = 0; i < colCount; i ++) {
         Rectangle span = spanmap.get(row, i);

         if(span != null) {
            for(int j = 1; j < span.height; j ++) {
               //Adjust the indices of the old calculated phs to account for new inserted rows.
               Object ph = oPHCache.get(new Point(i, row + j));

               if(ph != null) {
                  nCache.put(new Point(i, row + j + newRows), ph);
               }
            }
         }
      }

      oPHCache.clear();
      oPHCache.putAll(nCache);
   }

   /**
    * Calculate the column border width.
    */
   private void buildColBorderWidth(TableLens lens, final int colcount,
                                    final int headerc) {
      if(colBorderW == null || colBorderW.length != colcount) {
         float[] cws = new float[colcount];

         // calculate the border width for each column
         for(int j = 0; lens.moreRows(j) && j < MAX_ROW_LAYOUT; j++) {
            int lastc = headerc - 1;
            float rightB = Common.getLineWidth(lens.getColBorder(j, lastc));

            for(int i = 0; i < cws.length; i++) {
               // border of the previous column take the space in
               // this column, that's why we need to minus one
               cws[i] = Math.max(cws[i],
                  Common.getLineWidth(lens.getColBorder(j, i - 1)));

               // all these adjustment for the left header border is due
               // to the fact we use the left header border if a column
               // becomes the first column due to segmentation of tables
               cws[i] = Math.max(cws[i], rightB);
            }
         }

         colBorderW = cws;
      }
   }

   /**
    * Calculate the column width. If a width is provided by the table
    * lens, it's used. Otherwise, the width is calculated based on
    * the contents in the cells.
    * @param eqw column width if table is divided equally
    * @return arrow of width pairs. Each element on the array has
    * two elements (float[2]), the first is the preferred width,
    * and the second is the minimum width.
    */
   public float[][] calcColWidth(float eqw, TableLens lens) {
      // pwcache is the preferred width of span cell (subcells) during
      // calculation
      Hashtable pwcache = new Hashtable();

      final int ncolumn = lens.getColCount();
      final int headerc = lens.getHeaderColCount();

      float[][] prefmin = new float[ncolumn][2];

      if(colBorderW != null && colBorderW.length != ncolumn) {
         colBorderW = null;
      }

      buildColBorderWidth(lens, ncolumn, headerc);

      int total = 0; // keep total column width
      BitSet hidecols = new BitSet();

      // calculate the width for each column
      for(int i = 0; i < ncolumn; i++) {
         float[] ws = {0, 0};

         if(fixedWidths != null && i < fixedWidths.length) {
            ws[0] = fixedWidths[i];

            // if fixedwidth is set, don't perform other processing
            if(ws[0] > 0) {
               prefmin[i][0] = prefmin[i][1] = ws[0];
               total += ws[0];
               continue;
            }
         }
         else {
            ws[0] = lens.getColWidth(i);
         }

         if(ws[0] == StyleConstants.REMAINDER) {
            prefmin[i][0] = StyleConstants.REMAINDER;
            continue;
         }
         // if script set column width to 0, the column is not shown
         else if(ws[0] == 0) {
            hidecols.set(i);
         }

         // need to calculate
         if(ws[0] < 0) {
            // for large table, we only calculate the first 1000 rows
            // this should be enough assuming the data are similar in
            // the rest of the rows.
            for(int j = 0; lens.moreRows(j) && j < MAX_ROW_LAYOUT && !cancelled; j++) {
               float[] pw;
               float[] pair = (float[]) pwcache.get(new Point(i, j));

               // no cache exist, calculate
               if(pair == null) {
                  Font fn = lens.getFont(j, i);

                  fn = (fn == null) ? getFont() : fn;

                  pw = calcPrefMinWidth(lens.getObject(j, i), fn,
                                        lens.isLineWrap(j, i), eqw);

                  // adjust for cell insets
                  Insets insets = lens.getInsets(j, i);

                  if(insets != null) {
                     pw[0] += insets.left + insets.right;
                     pw[1] += insets.left + insets.right;
                  }

                  // if a span cell, calc the pwidth of all other cells
                  Dimension area = lens.getSpan(j, i);

                  if(area != null && area.width > 0 && area.height > 0) {
                     pw[0] /= area.width;
                     pw[1] /= area.width;

                     for(int r = j; r < j + area.height; r++) {
                        for(int c = i; c < i + area.width; c++) {
                           pwcache.put(new Point(c, r), pw);
                        }
                     }
                  }
               }
               else {
                  pw = pair;
                  // preferred width cache is used for span cells
                  pwcache.remove(new Point(i, j));
               }

               ws[0] = Math.max(ws[0], pw[0]);
               ws[1] = Math.max(ws[1], pw[1]);
            }

            // calculated width can not be 0
            if(ws[0] == 0) {
               // @by larryl, empty columns should not be too small otherwise
               // it's very hard to select
               ws[0] = eqw / 4;
               ws[1] = eqw / 5;
            }
         }

         total += ws[0];

         // adjust for cell padding
         // if column width is set to 0, hide the column and don't add padding
         if(!hidecols.get(i)) {
            if(getPadding() != null) {
               ws[0] += getPadding().left + getPadding().right;
               ws[1] += getPadding().left + getPadding().right;
            }

            prefmin[i][0] = ws[0] + colBorderW[i];
            prefmin[i][1] = ws[1] + colBorderW[i];

            if(maxColWidth > 0) {
               prefmin[i][0] = Math.min(prefmin[i][0], maxColWidth);
               prefmin[i][1] = Math.min(prefmin[i][1], maxColWidth);
            }
         }
      }

      // if all column widths are 0, force to 1 otherwise error in drawing
      if(total == 0) {
         for(int i = 0; i < prefmin.length; i++) {
            if(!hidecols.get(i)) {
               prefmin[i][0] = 1;
               prefmin[i][1] = 1;
            }
         }
      }

      return prefmin;
   }

   /**
    * Calculate the row border heights.
    */
   private float calculateRowBorderHeight(TableLens lens, int row,
                                          final int ncolumn)
   {
      float rowborder = 0;

      for(int j = 0; j < ncolumn; j++) {
         final int rowBorder;

         // border of the previous column take the space in
         // this column, that's why we need i-1
         // @by larryl, ensure the row is at point boundary. For lines have
         // width less than one point, we count it as one. Otherwise the
         // rounding errors could mess up the rendering
         if(lens instanceof SpanTableLens) {
            final int baseRowIndex = ((SpanTableLens) lens).getBaseRowIndex(row);
            rowBorder = lens.getRowBorder(baseRowIndex - 1, j);
         }
         else {
            rowBorder = lens.getRowBorder(row - 1, j);
         }

         float lw = (float) Math.ceil(Common.getLineWidth(rowBorder));
         rowborder = Math.max(rowborder, lw);
      }

      return rowborder;
   }

   /**
    * Calculate the row height. If a height is provided by the table
    * lens, it's used. Otherwise, the height is calculated based on
    * the contents in the cells.
    */
   private float[] calculateRowHeight(int[] fixed, float[] widths,
                                      float[] remainder, TableLens lens,
                                      float rborderh, int row,
                                      final Hashtable phcache,
                                      final int ncolumn,
                                      final boolean allowRemainder,
                                      final boolean splitRow,
                                      final Hashtable oldCache)
   {
      Insets padding = getPadding();

      // if(fixed != null && row < fixed.length) {
      //    return new float[]{fixed[row], fixed[row]};
      // }

      float h = getRowHeight(lens, row);
      float minHeight = 0; // let 0 signify no minimum height

      // @by davidd feature1278912780992, negative row heights will be
      // interpretted as the minimum row height.
      if(h < -1) {
         h = -h;
         minHeight = h;
      }

      float h0 = Float.MIN_VALUE;
      boolean isremainder = h == StyleConstants.REMAINDER;

      if(isremainder && !allowRemainder) {
         h = -1;
         isremainder = false;
      }

      final int[] colborders = new int[ncolumn];

      for(int j = 0; j < ncolumn; j++) {
         colborders[j] = lens.getColBorder(row, j - 1);
      }

      // need to calculate
      if(h == -1 || minHeight > 0) {
         final Font deffont = getFont();

         for(int j = 0; j < ncolumn; j++) {
            Float n = (Float) phcache.get(new Point(j, row));
            float ph = 0; // preferred height
            Font fn = lens.getFont(row, j);
            fn = fn == null ? deffont : fn;
            Object obj = lens.getObject(row, j);

            // no cache
            if(n == null || splitRow) {
               Dimension span = lens.getSpan(row, j);

               // adjsut for insets
               Insets insets = lens.getInsets(row, j);
               float w = (insets == null) ? widths[j] :
                  (widths[j] - insets.left - insets.right);

               // adjust width for span cells
               if(span != null) {
                  int spwidth = span.width;

                  // make sure the span does not go out of bounds
                  if(spwidth > ncolumn - j) {
                     spwidth = ncolumn - j;
                  }

                  for(int k = j + 1; k < j + spwidth; k++) {
                     w += colWidth[k];
                  }
               }


               w -= Common.getLineWidth(colborders[j]);
               ph = calcPreferredHeight(fn, obj, w, lens.isLineWrap(row, j), padding);

               // adjust for cell insets
               if(insets != null) {
                  ph += insets.top + insets.bottom;
               }

               if(splitRow && n != null) {
                  ph = n;
                  Point p = new Point(j, row);

                  if(oldCache != null) {
                     oldCache.put(p, phcache.remove(p));
                  }
                  else {
                     phcache.remove(p);
                  }
               }

               // if a span cell, calc the pheight of all other cells
               if(span != null) {
                  float fheight = Common.getHeight(fn) + getSpacing();
                  float total = ph;

                  // make sure the span cells don't break in middle
                  // of a line, by ensuring each cell has a height
                  // that is multiple of the font height
                  for(int r = row; r < row + span.height; r++) {
                     float ch;

                     // use actual height for presenters, not text height
                     if(obj instanceof PresenterPainter) {
                        ch = Math.round(total / (span.height - r + row));
                     }
                     else {
                        // roundup to put row height at line boundary
                        ch = StyleCore.roundup(total / (span.height - r + row), fheight);
                     }

                     if(r == row) {
                        ph = ch;
                     }

                     for(int c = j; c < j + span.width; c++) {
                        phcache.put(new Point(c, r), ch);
                     }

                     // roundup to put row height at line boundary
                     total = StyleCore.roundup(total - ch, fheight);
                  }
               }
            }
            else {
               ph = n;
               Point p = new Point(j, row);

               if(oldCache != null) {
                  oldCache.put(p, phcache.remove(p));
               }
               else {
                  phcache.remove(p);
               }
            }

            h = Math.max(h, ph);

            // if the calculated height is 0, set it to the font height
            if((h <= 0 || h < Common.getHeight(fn))) {
               h = Common.getHeight(fn);
            }
         }
      }

      if(minHeight > 0) {
         h = Math.max(h, minHeight);
      }

      if(!isremainder) {
         // @by larryl, if row height is set to 0, hide the row
         if(h > 0) {
            // adjust for padding
            h0 = h;

            if(padding != null) {
               h += padding.top + padding.bottom;
            }

            h += rborderh;
         }

         remainder[0] -= h;
      }
      else {
         remainder[1] = remainder[1] + 1;
      }

      float rowh = h;

      if(fixed != null && row < fixed.length && fixed[row] >= 0) {
         rowh = fixed[row];
      }

      // first height is row height, and second is the content height.
      // And how many lines should the cell splits is decided by the content
      // height and the split height.
      return new float[] {rowh, h0 == Float.MIN_VALUE ? h : h0};
   }

   private int getRowHeight(TableLens lens, int row) {
      boolean vsReport = Util.getNestedTable(lens, VSTableLens.class) != null;
      int h = lens.getRowHeight(row);

      // check for user set value
      if((!vsReport || isKeepRowHeightOnPrint()) && h != -1) {
         return h;
      }

      boolean isLineWrap = false;

      for(int col = 0; col < lens.getColCount(); col++) {
         if(lens.isLineWrap(row, col)) {
            isLineWrap = true;
            break;
         }
      }

      h = -1;

      if(!isLineWrap && fixedHeights != null && row < fixedHeights.length &&
         fixedHeights[row] >= 0)
      {
         h = fixedHeights[row];
      }

      return h;
   }

   /**
    * Calculate the preferred width of an object.
    * @param eqw column width if all columns are assigned equal width
    */
   private float[] calcPrefMinWidth(Object v, Font font, boolean wrap, float eqw) {
      if(v == null) {
         return new float[] {0, 0};
      }

      float[] prefmin = new float[2];

      if(v instanceof String) {
         return StyleCore.getPrefMinWidth((String) v, font, wrap, eqw);
      }
      else if(v instanceof Painter) {
         boolean isPresenter = v instanceof PresenterPainter;

         if(isPresenter) {
            ((PresenterPainter) v).getPresenter().setFont(font);
         }

         prefmin[0] = ((Painter) v).getPreferredSize().width;
         prefmin[1] = isPresenter ? Math.min(prefmin[0], eqw) : prefmin[0];
         return prefmin;
      }
      else if(v instanceof Component) {
         prefmin[0] = ((Component) v).getSize().width;
         prefmin[1] = prefmin[0];
         return prefmin;
      }
      else if(v instanceof Image) {
         prefmin[0] = ((Image) v).getWidth(null);
         prefmin[1] = prefmin[0];
         return prefmin;
      }

      return StyleCore.getPrefMinWidth(getReport().toString(v), font, wrap, eqw);
   }

   /**
    * Calculate the preferred height of an object. Width is needed
    * because it impacts the wrapping of text lines therefore the
    * preferred height of text contents.
    * @param wrap true if wrap lines.
    */
   private float calcPreferredHeight(Font font, Object v, float w,
                                     boolean wrap, Insets padding) {
      if(v == null || w <= 0) {
         return 0;
      }

      if(v instanceof Painter) {
         if(v instanceof PresenterPainter) {
            Presenter pre = ((PresenterPainter) v).getPresenter();

            pre.setFont(font);

            if(pre instanceof ExpandablePresenter) {
               return ((ExpandablePresenter) pre).getPreferredSize(
                  ((PresenterPainter) v).getObject(), (int) w).height;
            }
         }

         return ((Painter) v).getPreferredSize().height;
      }
      else if(v instanceof Component) {
         return ((Component) v).getSize().height;
      }
      else if(v instanceof Image) {
         return ((Image) v).getHeight(null);
      }

      // adjust for padding, padding only for text
      if(padding != null) {
         w -= padding.left + padding.right;
      }

      // calculate the height of string
      String str = getReport().toString(v);
      float fontH = Common.getHeight(font);
      float h = 0;
      int idx = str.indexOf('\n');
      int odx = 0;
      String line = null;

      while(odx >= 0) {
         line = (idx >= 0) ? str.substring(odx, idx) : str.substring(odx);

         while(line != null) {
            h += fontH + getSpacing();

            // breakup line if longer than the width
            int br = wrap ? Util.breakLine(line, w, font, true) : -1;

            if(br >= 0 && line.length() > 0) {
               line = line.substring(Math.max(1, br));

               // strip off leading space
               while(line.length() > 0 && line.charAt(0) == ' ') {
                  line = line.substring(1);
               }
            }
            else {
               line = null;
            }
         }

         // reached end
         if(idx < 0) {
            break;
         }

         odx = idx + 1;
         idx = str.indexOf('\n', odx);
      }

      // discount the spacing after the last line
      if(h > 0) {
         h -= getSpacing();
      }

      return Math.max(h, Common.getHeight(font));
   }

   /**
    * Reset cached tables. The next time getTable() is called, all filtering
    * will be re-applied.
    */
   @Override
   public void resetFilter() {
      resetFilter(Integer.MAX_VALUE);
   }

   /**
    * Reset cached tables.
    */
   public void resetFilter(int selector) {
      synchronized(tableReady) {
         if(selector >= DATA_ATTR_TABLE) {
            maxrowtable = null;
         }

         if(selector >= TOP_TABLE) {
            toptable = null;
         }

         printTbl = null;
         valid = false;
      }
   }

   /**
    * Get element type.
    */
   @Override
   public String getType() {
      return "Table";
   }

   /**
    * Get string representation.
    */
   public String toString() {
      return getID();
   }

   /**
    * Clone the element.
    */
   @Override
   public Object clone() {
      TableElementDef elem = (TableElementDef) super.clone();

      if(presenters != null) {
         elem.presenters = (Hashtable) presenters.clone();
      }

      if(formats != null) {
         elem.formats = (Hashtable) formats.clone();
      }

      if(fixedWidths != null) {
         elem.fixedWidths = fixedWidths.clone();
      }

      if(autoWidths != null) {
         elem.autoWidths = autoWidths.clone();
      }

      elem.regions = new Vector();
      elem.pgregs = new Vector<>();

      elem.colWidth = null;
      elem.rowHeight = null;
      elem.rowBorderH = null;
      elem.colBorderW = null;
      elem.spanmap = new SpanMap();

      elem.sumspan = new Hashtable();
      TableLayout nlayout = elem.getTableLayout();
      // clear the table layout avoid hen-egg-hen game when setTable
      elem.setTableLayout(null);

      // @by davyc, because now, for calc table, we won't clear the attribute
      // table, so here we need clone it, fix bug1285410284397
      elem.attrtable = attrtable == null ?
         null : (AttributeTableLens2) attrtable.clone();

      // avoid to make a copy of data if AttributeTableLens, and do not
      // call setData for form element to work
      if(this.table instanceof AttributeTableLens) {
         elem.setTable((TableLens) ((AttributeTableLens) this.table).clone());
      }
      else if(this.table instanceof XNodeTableLens) {
         elem.setTable(((XNodeTableLens) this.table).cloneShared());
      }
      else if(this.table != null) {
         AttributeTableLens tbl2 = new AttributeTableLens(this.table);
         elem.setTable((TableLens) tbl2.clone());
      }

      elem.setTableLayout(nlayout);
      return elem;
   }

   /**
    * Check if this is a calc table.
    */
   public boolean isCalc() {
      return getTableLayout() != null && getTableLayout().isCalc();
   }

   /**
    * Check if explicit height from table lens should be kept when printing a viewsheet.
    */
   public boolean isKeepRowHeightOnPrint() {
      return keepRowHeightOnPrint;
   }

   /**
    * Set if explicit height from table lens should be kept when printing a viewsheet.
    */
   public void setKeepRowHeightOnPrint(boolean keepRowHeightOnPrint) {
      this.keepRowHeightOnPrint = keepRowHeightOnPrint;
   }

   /**
    * Get the source attr.
    */
   @Override
   public XSourceInfo getXSourceInfo() {
      return null;
   }

   /**
    * Get the script environment.
    * @return the script enrironment.
    */
   @Override
   public ScriptEnv getScriptEnv() {
      return getReport().getScriptEnv();
   }

   // expose the explicitly set attributes
   public static class AttributeTableLens2 extends AttributeTableLens {
      public AttributeTableLens2(TableLens table) {
         super(table);
      }

      public Color getRowBorderColor0(int r, int c) {
         return (Color) get(rowborderCmap, r + 1, c + 1);
      }

      public Color getColBorderColor0(int r, int c) {
         return (Color) get(colborderCmap, r + 1, c + 1);
      }

      public Integer getRowBorder0(int r, int c) {
         return (Integer) get(rowbordermap, r + 1, c + 1);
      }

      public Integer getColBorder0(int r, int c) {
         return (Integer) get(colbordermap, r + 1, c + 1);
      }

      private Object get(SparseIndexedMatrix map, int r, int c) {
         if(map == null || map.isEmpty()) { // map might be unavailable
            return null;
         }

         // given the special purpose of this, we are only interested in the
         // attributes set on the cell level, so we don't check the row and
         // column settings
         return map.get(r, c);
      }
   }

   /**
    * Set tablelens to the current table which is converted by the vs tableobj.
    * @param lens VSTablelens which is from a vs table object.
    */
   public void setVSTableLens(VSTableLens lens) {
      vsTableLens = lens;
   }

   /**
    * Get the vstablelens which is from a vs table object.
    */
   public VSTableLens getVSTableLens() {
      return vsTableLens;
   }

   /**
    * Set the border colors.
    * Because report table have no outer border for the whole table,
    * so this is only used in printlayout mode.
    */
   public void setBorderColors(BorderColors bcolors) {
      this.bcolors = bcolors;
   }

   /**
    * Get the border colors.
    * Because report table have no outer border for the whole table,
    * so this is only used in printlayout mode.
    */
   public BorderColors getBorderColors() {
      return bcolors;
   }

   /**
    * Set the individual border line styles.
    * Because report table have no outer border for the whole table,
    * so this is only used in printlayout mode.
    * @param borders line styles.
    */
   public void setBorders(Insets borders) {
      this.borders = borders;
   }

   /**
    * Get the individual border line styles.
    * Because report table have no outer border for the whole table,
    * so this is only used in printlayout mode.
    * @return border line style..
    */
   public Insets getBorders() {
      return borders;
   }

   public float[] getColWidth() {
      return colWidth;
   }

   public void setColWidth(float[] cw) {
      this.colWidth = cw;
   }

   // Just used for the table converted by a vs table when using vs printlayout.
   private VSTableLens vsTableLens = null;
   private BorderColors bcolors = null;
   private Insets borders = null;

   // @by billh, the priority should keep in sync with the apply-process,
   // we'd better merge logic of the two parts later...
   public static final int CALC_TABLE = 10; // expanded calc table
   public static final int DATA_ATTR_TABLE = 4; // data attr related tables
   public static final int TOP_TABLE = 1; // top table

   private SummaryTableLens summary = null;
   private TableLens printTbl = null; // modified table just for printing
   private TableLens toptable; // table lens overrides all filters, cached
   private TableLens maxrowtable; // table lens after apply table max row
   private AttributeTableLens2 attrtable; // top-level attribute table
   private TableLens table; // raw table lens
   private TableLens scriptTable; // raw binding table
   private TableLens[] scriptTables; // additional raw binding tables
   private AtomicBoolean tableReady = new AtomicBoolean(true);

   private double tableW;
   private Hashtable presenters;
   private Hashtable formats;
   private int[] fixedWidths; // column width
   private Integer[] autoWidths;
   private int[] fixedHeights; // row height, runtime only override

   // calculated values
   private boolean valid = false; // false to call format
   private boolean cancelled = false; // if processing is cancelled
   private Vector regions = new Vector(); // vector of Rectangle
   // each page regions
   private Vector<Rectangle> pgregs = new Vector<>();

   private int currentRegion = 0;
   private float[] colWidth; // column width
   private float[] rowHeight; // row height
   private float[] rowBorderH; // row border height, 0 is the top border (-1)
   private float[] colBorderW; // col border width, 0 is the left border (-1)
   private float headerW = 0, headerH = 0; // header col/row width/height
   private float maxColWidth = 0; // maximum column width
   private Hashtable fixedWidthMap = new Hashtable(); // column path -> width

   // set to the summary row height if the table lens is SummaryTableLens
   private float trailerH = 0;

   // spanmap contains the cells that is in a span cell
   // Point -> Rectangle, where x, y is the distance from the left-upper
   // cornor of the span area, and
   // width,height is the cells to the right and below the current
   // cell in the span cell
   private SpanMap spanmap;

   // column number -> Rectangle, same as spanmap
   private Hashtable sumspan;
   private boolean keepRowHeightOnPrint = false;

   private static final float MAX_CELL_H = 100;
   private static int MAX_ROW_LAYOUT = 1000;
   private static final Logger LOG = LoggerFactory.getLogger(TableElementDef.class);
}
