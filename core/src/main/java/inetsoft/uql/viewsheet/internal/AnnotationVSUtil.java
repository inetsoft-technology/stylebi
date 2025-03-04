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
package inetsoft.uql.viewsheet.internal;

import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.graph.EGraph;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.PointElement;
import inetsoft.report.*;
import inetsoft.report.composition.RegionTableLens;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.composition.region.TextArea;
import inetsoft.report.composition.region.*;
import inetsoft.report.filter.CrossFilter;
import inetsoft.report.filter.HiddenRowColFilter;
import inetsoft.report.internal.*;
import inetsoft.report.internal.table.*;
import inetsoft.report.io.rtf.RichTextGraphics;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.lens.TextSizeLimitTableLens;
import inetsoft.report.painter.HTMLPresenter;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.report.script.viewsheet.ChartVSAScriptable;
import inetsoft.report.script.viewsheet.GraphCreator;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.UserEnv;
import inetsoft.uql.XNode;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.AssemblyInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.*;
import inetsoft.util.script.ScriptUtil;
import inetsoft.web.binding.dnd.BindingDropTarget;
import inetsoft.web.binding.dnd.TableTransfer;
import inetsoft.web.binding.event.VSDndEvent;
import inetsoft.web.viewsheet.event.ViewsheetEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.text.NumberFormat;
import java.util.List;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Viewsheet annotation utilities.
 *
 * @version 11.4
 * @author InetSoft Technology Corp
 */
public final class AnnotationVSUtil {
   /**
    * Get annotation data values by the row and col index.
    */
   public static AnnotationCellValue getAnnotationDataValue(
                                     ViewsheetSandbox box,
                                     VSAssembly base, int row, int col,
                                     String measureName)
   {
      if(box == null || base == null) {
         return null;
      }

      if(base instanceof TableDataVSAssembly) {
         TableLens lens = null;

         try {
            lens = box.getTableData(base.getAbsoluteName());
         }
         catch(Exception ex) {
            return null;
         }

         if(lens == null) {
            return null;
         }

         if(base instanceof EmbeddedTableVSAssembly) {
            EmbeddedCellValue cellVal = (EmbeddedCellValue)
               AnnotationCellValue.create(AnnotationCellValue.EMBEDDED_TABLE);
            String[] values = new String[] {row + "", col + ""};
            cellVal.setValues(values);

            return cellVal;
         }
         else if(base instanceof TableVSAssembly) {
            TableCellValue cellVal = (TableCellValue)
               AnnotationCellValue.create(AnnotationCellValue.NORMAL_TABLE);
            String[] values = getNormalTableValues(lens, row, col, cellVal);
            cellVal.setValues(values);

            return cellVal;
         }
         else if(base instanceof CrosstabVSAssembly) {
            if(isForCalc(lens)) {
               CrosstabVSAssemblyInfo info = (CrosstabVSAssemblyInfo) base.getInfo();
               TableLens clens = lens;
               VSCrosstabInfo cinfo = info.getVSCrosstabInfo();
               int rcnt = DateComparisonUtil.appliedDateComparison(base.getVSAssemblyInfo()) ?
                  cinfo.getRuntimeHeaderColCountWithPeriod() : cinfo.getHeaderColCountWithPeriod();
               int ccnt = cinfo.getHeaderRowCount();

               boolean isColHeader = row + 1 <= ccnt;
               boolean isRowHeader = col + 1 <= rcnt;
               rcnt = isRowHeader ? col + 1 : rcnt;
               ccnt = isColHeader ? row + 1 : ccnt;

               String[] values = new String[rcnt + ccnt];

               for(int i = 0 ; i < rcnt; i++) {
                  values[i] = Tool.toString(clens.getObject(row, i));
               }

               for(int j = rcnt ; j < rcnt + ccnt; j++) {
                  values[j] = Tool.toString(clens.getObject(j - rcnt, col));
               }

               CrosstabCellValue cellVal = (CrosstabCellValue)
                  AnnotationCellValue.create(AnnotationCellValue.CROSS_TABLE);
               cellVal.setValues(values);
               int orowHCnt = rcnt;
               //int ocolHCnt = values.length - orowHCnt;
               //int tidx = isColHeader ? ocolHCnt : ccnt;

               // @by skyf, the last value is the original row header count,
               // we should use this count to re-position the annotation which
               // is in crosstab headers, for the case: the annotation is in
               // the first row header, then drill down this row header,
               // we should just only check the annotation exists in the first
               // row header, not all row headers.
               cellVal.setSplitIndex(rcnt);
               cellVal.setRowHeaderCell(isRowHeader);
               cellVal.setColHeaderCell(isColHeader);
               String[] tempValues = new String[orowHCnt];
               System.arraycopy(values, 0, tempValues, 0, tempValues.length);
               cellVal.setRepeatedRowIndex(getRepeatedRowIndex(lens, row, col + 1,tempValues, true));
               cellVal.setRepeatedColIndex(getRepeatedColIndex(lens, row, col));

               return cellVal;
            }
            // if crosstab only has dimension or aggregate, treat it as
            // plain table
            else {
               TableCellValue cellVal = (TableCellValue)
                  AnnotationCellValue.create(AnnotationCellValue.CROSS_TABLE);
               String[] values = getNormalTableValues(lens, row, col, cellVal);
               cellVal.setValues(values);

               return cellVal;
            }
         }
         else if(base instanceof CalcTableVSAssembly) {
            TableLens calcLens = lens;

            while(!(calcLens instanceof CalcTableLens) && (calcLens instanceof TableFilter)) {
               calcLens = ((TableFilter) calcLens).getTable();
            }

            CalcTableCellValue cellVal = (CalcTableCellValue)
               AnnotationCellValue.create(AnnotationCellValue.CALC_TABLE);
            String[] values = null;

            if(calcLens instanceof RuntimeCalcTableLens) {
               List<String> datas = new ArrayList<>();
               CalcTableVSAssembly calc = (CalcTableVSAssembly) base;
               RuntimeCalcTableLens rlens = (RuntimeCalcTableLens) calcLens;
               prepareValues(calc, rlens, datas, row, col);
               values = datas.toArray(new String[0]);
            }
            else if(calcLens instanceof CalcTableLens) {
               String obj = Tool.toString(calcLens.getObject(row, col));
               values = new String[] {row + "", col + "", obj};
            }

            if(calcLens instanceof RuntimeCalcTableLens) {
               RuntimeCalcTableLens runtimeCalcTableLens = (RuntimeCalcTableLens) calcLens;
               int repRow = getRuntimeCalcRepeatedRowIndex(runtimeCalcTableLens,
                  (CalcTableVSAssembly) base, row, col, values);
               cellVal.setRepRowIdx(repRow);
            }
            else {
               cellVal.setRepRowIdx(getRepeatedRowIndex(lens, row, col,
                  new String[] {Tool.toString(lens.getObject(row, col))}));
            }

            cellVal.setValues(values);
            return cellVal;
         }
      }
      else if(base instanceof ChartVSAssembly) {
         DataSet chart = AnnotationVSUtil.getDataSet(box, base);

         if(chart == null) {
            return null;
         }

         boolean detail = containsNonAggregatedMeasure((ChartVSAssembly) base);
         boolean separate = isSeparateMeasure((ChartVSAssembly) base);
         List list = new ArrayList();
         int ccnt = chart.getColCount();
         boolean relationChart = isRelationChart((ChartVSAssembly) base);

         for(int i = 0; i < ccnt; i++) {
            String header = chart.getHeader(i);

            if(detail && separate || !chart.isMeasure(header) ||
               (detail && !separate && Tool.equals(measureName, header)))
            {
               list.add(Tool.toString(ScriptUtil.getScriptValue(chart.getData(i, row))));

               if(relationChart && Tool.equals(measureName, header)) {
                  break;
               }
            }
         }

         String[] values = new String[list.size()];
         list.toArray(values);
         ChartDataValue cellVal = (ChartDataValue)
            AnnotationCellValue.create(AnnotationCellValue.CHART);
         cellVal.setValues(values);
         cellVal.setMeasureName(measureName);

         return cellVal;
      }

      return null;
   }

   /**
    * Get the normal table cell annotation values.
    */
   private static String[] getNormalTableValues(
      TableLens lens, int row, int col, TableCellValue cellVal)
   {
      int cols = lens.getColCount();
      String[] values = new String[cols];

      for(int i = 0; i < cols; i++) {
         values[i] = Tool.toString(ScriptUtil.getScriptValue(lens.getObject(row, i)));
      }

      cellVal.setRepeatedRowIndex(getRepeatedRowIndex(lens, row, -1, values));
      String identifier = lens.getColumnIdentifier(col);
      cellVal.setColHeader(identifier == null ?
         Tool.toString(lens.getObject(0, col)) : identifier);
      return values;
   }

   /**
    * Prepare calc table values.
    */
   public static void prepareValues(CalcTableVSAssembly calc,
                                    RuntimeCalcTableLens lens,
                                    List<String> datas,
                                    int row, int col)
   {
      if(row >= lens.getRowCount() || col >= lens.getColCount()) {
         return;
      }

      TableLayout layout = calc.getTableLayout();
      int drow = lens.getRow(row);
      int dcol = lens.getCol(col);
      boolean emptyRowGroup = false;
      boolean emptyColGroup = false;
      TableCellBinding cell =
         (TableCellBinding) layout.getCellBinding(drow, dcol);
      XNode tree = LayoutTool.buildTree((TableLayout) layout.clone());
      XNode rroot = tree.getChild(0);
      XNode croot = tree.getChild(1);
      prepareValues(layout, lens, cell, rroot, row, col, datas);
      emptyRowGroup = datas.size() == 0;

      List<String> colGroups = new ArrayList<>();
      prepareValues(layout, lens, cell, croot, row, col, colGroups);
      emptyColGroup = colGroups.size() == 0;
      datas.addAll(colGroups);

      // if have no group, shoule remeber the cell value to locate
      // annotation position
      if(emptyRowGroup || emptyColGroup) {
         Object obj = lens.getObject(row, col);

         if(obj instanceof Object[]) {
            Object[] objs = (Object[]) obj;
            StringBuilder sb = new StringBuilder();

            for(int i = 0; i < objs.length; i++) {
               sb.append(Tool.toString(objs[i]));

               if(i < objs.length - 1) {
                  sb.append(",");
               }
            }

            datas.add(Tool.toString(sb));
         }
         else {
            datas.add(Tool.toString(obj));
         }
      }

      if(cell != null) {
         datas.add(cell.getValue());

         if(cell.getType() == CellBinding.BIND_TEXT) {
            CalcCellContext context = lens.getCellContext(row, col);
            datas.add(context.getIdentifier());
         }
      }
   }

   /**
    * Get chart data.
    */
   private static DataSet getDataSet(ViewsheetSandbox box, VSAssembly base) {
      if(box == null) {
         return null;
      }

      try {
         VGraphPair pair = null;
         pair = box.getVGraphPair(base.getAbsoluteName());

         if(pair != null) {
            return pair.getData();
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to get data set: " + base, e);
      }

      DataSet data = null;

      try {
         String name = base.getAbsoluteName();
         data = (VSDataSet) box.getData(name);

         if(data == null) {
            ChartVSAssembly ass = (ChartVSAssembly) base.clone();
            ChartVSAScriptable scriptable =
               (ChartVSAScriptable) box.getScope().getVSAScriptable(name);
            String script = ((VSAssemblyInfo) ass.getInfo()).getScript();

            if(script != null && script.length() > 0) {
               scriptable.setGraphCreator(new GraphCreator() {
                  @Override
                  public EGraph createGraph() {
                     return null;
                  }

                  @Override
                  public DataSet getGraphDataSet() {
                     return null;
                  }
               });

               box.getScope().execute(script, scriptable);
               data = (DataSet) scriptable.get("dataset", scriptable);
               box.getScope().resetChartScriptable(ass);
            }
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to get data set: " + base, e);
      }

      return data;
   }

   /**
    * Prepare calc table values.
    */
   private static void prepareValues(TableLayout layout,
                                     RuntimeCalcTableLens lens,
                                     TableCellBinding binding, XNode root,
                                     int row, int col, List<String>datas)
   {
      XNode parent = VSLayoutTool.getParentNode(root, layout, binding);

      if(parent == null || parent == root || layout == null) {
         return;
      }

      CalcAttr cattr = (CalcAttr) parent.getValue();
      TableCellBinding cell = (TableCellBinding) layout.getCellBinding(
         cattr.getRow(), cattr.getCol());
      String cellName = layout.getRuntimeCellName(cell);
      Point point = lens.getFieldRowCol(cellName, row, col);
      String value = Tool.toString(lens.getObject(point.x, point.y));
      prepareValues(layout, lens, cell, root, row, col, datas);
      datas.add(value);
   }

   /**
    * Get the repeated row index with same row values.
    */
   private static int getRuntimeCalcRepeatedRowIndex(RuntimeCalcTableLens calcLens,
                                                     CalcTableVSAssembly calc, int row, int col,
                                                     String[] values)
   {

      if(calcLens instanceof RuntimeCalcTableLens) {
         TableLayout layout = calc.getTableLayout();
         int drow = calcLens.getRow(row);
         int dcol = calcLens.getCol(col);
         TableCellBinding cell =
            (TableCellBinding) layout.getCellBinding(drow, dcol);

         if(cell.getType() == CellBinding.BIND_TEXT) {
            boolean emptyRowGroup = false;
            boolean emptyColGroup = false;
            XNode tree = LayoutTool.buildTree((TableLayout) layout.clone());
            XNode rroot = tree.getChild(0);
            XNode croot = tree.getChild(1);
            List<String> rowGroups = new ArrayList<>();
            prepareValues(layout, calcLens, cell, rroot, row, col, rowGroups);
            emptyRowGroup = rowGroups.size() == 0;
            List<String> colGroups = new ArrayList<>();
            prepareValues(layout, calcLens, cell, croot, row, col, colGroups);
            emptyColGroup = colGroups.size() == 0;
            CalcCellContext context = calcLens.getCellContext(row, col);

            if((emptyRowGroup || emptyColGroup) && Tool.isEmptyString(context.getIdentifier())) {
               int colCount = calcLens.getColCount();
               int idx = 1;

               for(int i = 0; i < row; i++) {
                  for(int j = 0; j < colCount; j++) {
                     List<String> datas = new ArrayList<>();
                     prepareValues(calc, calcLens, datas, i, j);

                     if(cellValuesMatch(values, datas.toArray(new String[0]))) {
                        idx++;
                     }
                  }
               }

               return idx;
            }
         }
      }

      if(row == 0) {
         return 1;
      }

      int rcnt = calcLens.getRowCount();
      int idx = 1;

      for(int i = 0; i < rcnt; i++) {
         List<String> datas = new ArrayList<>();
         prepareValues(calc, calcLens, datas, i, col);
         String[] temp = datas.toArray(new String[0]);

         if(Tool.equals(values, temp)) {
            if(row == i) {
               return idx;
            }

            idx++;
         }
      }

      return 1;
   }

   /**
    * Get the repeated row index with same row values.
    */
   private static int getRepeatedRowIndex(TableLens lens, int row, int col,
                                          String[] values)
   {
      return getRepeatedRowIndex(lens, row, col, values, false);
   }

   /**
    * Get the repeated row index with same row values.
    */
   private static int getRepeatedRowIndex(TableLens lens, int row, int endCol,
                                          String[] values, boolean crosstab)
   {
      if(row == 0) {
         return 1;
      }

      int rcnt = lens.getRowCount();
      int ccnt = endCol > -1 ? crosstab ? endCol : 1 : lens.getColCount();
      int idx = 1;

      for(int i = 0; i < rcnt; i++) {
         String[] temp = new String[ccnt];

         for(int j = 0; j < ccnt; j++) {
            temp[j] = Tool.toString(lens.getObject(i, endCol > -1 ? crosstab ? j : endCol : j));
         }

         if(Tool.equals(values, temp)) {
            if(row == i) {
               return idx;
            }

            idx++;
         }
      }

      return 1;
   }

   /**
    * Get the repeated col index with same col value.
    */
   private static int getRepeatedColIndex(TableLens lens, int row, int col) {
      if(col == 0) {
         return 1;
      }

      int hrcnt = lens.getHeaderRowCount();
      int idx = 1;

      for(int i = col - 1; i >= 0; i--) {
         boolean check = false;

         for(int r = 0; r < hrcnt; r++) {
            if(Tool.equals(Tool.toString(lens.getObject(r, col)),
               Tool.toString(lens.getObject(r, i))))
            {
               check = true;
            }
            else {
               check = false;
            }
         }

         if(check) {
            idx++;
         }
         else if(i == 0) {
            return idx;
         }
      }

      return 1;
   }

   /**
    * Get runtime row and col index by the annotation values.
    */
   public static int[] getRuntimeIndex(ViewsheetSandbox box, VSAssembly base,
                                       TableLens lens,
                                       AnnotationVSAssemblyInfo ainfo)
   {
      if(ainfo != null && ainfo.getValue() != null &&
         ainfo.getType() == AnnotationVSAssemblyInfo.DATA)
      {
         AnnotationCellValue value = ainfo.getValue();
         String[] values = value.getValues();

         try {
            lens = lens == null ?
               box.getTableData(base.getAbsoluteName()) : lens;
         }
         catch(Exception ex) {
            return null;
         }

         if(lens == null || values == null) {
            return null;
         }

         if(base instanceof EmbeddedTableVSAssembly) {
            int row = Integer.parseInt(values[0]);
            int col = Integer.parseInt(values[1]);

            if(lens.getRowCount() > row && lens.getColCount() > col) {
               return new int[] {row, col};
            }
         }
         else if(base instanceof TableVSAssembly) {
            return findNormalTableIndex(lens, (TableCellValue) value);
         }
         else if(base instanceof CrosstabVSAssembly) {
            TableLens table = lens;
            int rcnt = lens.getRowCount();
            int ccnt = lens.getColCount();

            // bug1419398216008, In the case when match-layout is not selected
            // during Viewsheet Export, we need to get the nested table for
            // VSTableLens as well.
            // NOTE: If match-layout is selected, lens will be an instance of
            // RegionTableLens. However, if this option is not selected, lens
            // will only be a VSTableLens.
            if(lens instanceof VSTableLens) {
               HiddenRowColFilter hide = (HiddenRowColFilter) Util.getNestedTable(table,
                  HiddenRowColFilter.class);

               if(hide == null) {
                  table = Util.getCrossFilter(lens);
               }
               else {
                  table = hide;
               }
            }

            if(isForCalc(table)) {
               TableLens clens =  table;
               CrosstabVSAssemblyInfo info =
                  (CrosstabVSAssemblyInfo) base.getInfo();
               VSCrosstabInfo cinfo = info.getVSCrosstabInfo();
               int rhcnt = DateComparisonUtil.appliedDateComparison(info) ?
                  cinfo.getRuntimeHeaderColCountWithPeriod() : cinfo.getHeaderColCountWithPeriod();
               int chcnt = cinfo.getHeaderRowCount();

               boolean isRowHeaderCell =
                  ((CrosstabCellValue) value).isRowHeaderCell();
               boolean isColHeaderCell =
                  ((CrosstabCellValue) value).isColHeaderCell();
               int orowHCnt = ((CrosstabCellValue) value).getSplitIndex();
               int ocolHCnt = values.length - orowHCnt;

               if(orowHCnt > rhcnt || ocolHCnt > chcnt) {
                  return null;
               }
               else {
                  int ridx = -1;
                  int cidx = -1;
                  int tidx = isColHeaderCell ? ocolHCnt : chcnt;
                  int repColIdx = ((CrosstabCellValue) value).getRepeatedColIndex();
                  int colCount = 1;

                  // find col index
                  for(int i = 0; i < ccnt; i++) {
                     String[] tempValues = new String[ocolHCnt];
                     System.arraycopy(values, orowHCnt, tempValues, 0,
                                      tempValues.length);

                     String[] temp = new String[tidx];

                     for(int j = 0; j < tidx; j++) {
                        temp[j] = Tool.toString(clens.getObject(j, i));
                     }

                     if(Tool.equals(temp, tempValues)) {
                        if(colCount == repColIdx) {
                           cidx = i;
                           break;
                        }

                        colCount++;
                     }
                  }

                  tidx = isRowHeaderCell ? orowHCnt : rhcnt;
                  List<Integer> tempIdx = new ArrayList<>();

                  // find row index
                  for(int i = 0; i < rcnt; i++) {
                     String[] tempValues = new String[orowHCnt];
                     System.arraycopy(values, 0, tempValues, 0,
                                      tempValues.length);

                     Object[] temp = new Object[tidx];

                     for(int j = 0; j < tidx; j++) {
                        Object object = clens.getObject(i, j);
                        temp[j] = object;
                     }

                     if(cellValuesMatch(tempValues, temp)) {
                        tempIdx.add(i);
                     }
                  }

                  if(tempIdx.size() == 1) {
                     ridx = (Integer) tempIdx.get(0);
                  }
                  else if(tempIdx.size() > 1 && cidx != -1) {
                     if(!isRowHeaderCell && !isColHeaderCell) {
                        ridx = (Integer) tempIdx.get(0);
                     }
                     else {
                        // if the annotation is in col header, sometimes some
                        // col headers may have same annotation values for
                        // finding row index, so we should use the col header
                        // value to find correct runtime row index.
                        String lastColValue = cidx + 1 <= rhcnt ?
                           values[orowHCnt - 1] : values[values.length - 1];
                        int repIdx =
                           ((CrosstabCellValue) value).getRepeatedRowIndex();
                        int repeatedIdx = 1;

                        for(int i = 0; i < tempIdx.size(); i++) {
                           int idx = tempIdx.get(i);

                           if(cellValueMatch(lastColValue, clens.getObject(idx, cidx))) {
                              if(repeatedIdx == repIdx) {
                                 ridx = idx;
                                 break;
                              }

                              repeatedIdx++;
                           }
                        }
                     }
                  }

                  return ridx != -1 && cidx != -1 ? new int[] {ridx, cidx} :
                     null;
               }
            }
            else {
               return findNormalTableIndex(lens, (TableCellValue) value);
            }
         }
         else if(base instanceof CalcTableVSAssembly) {
            TableLens table = null;
            int rcnt = lens.getRowCount();
            int ccnt = lens.getColCount();

            if(lens instanceof RegionTableLens) {
               table = (RuntimeCalcTableLens)
                  Util.getNestedTable(lens, RuntimeCalcTableLens.class);
            }

            if(table == null) {
               table = (CalcTableLens)
                  Util.getNestedTable(lens, CalcTableLens.class);
            }

            if(table == null) {
               table = lens;
            }

            if(table instanceof RuntimeCalcTableLens) {
               CalcTableVSAssembly calc = (CalcTableVSAssembly) base;
               RuntimeCalcTableLens rlens = (RuntimeCalcTableLens) table;
               final int row = ainfo.getRow();
               final int col = ainfo.getCol();
               rlens.moreRows(row);
               CalcCellContext cellContext = null;
               Map<String, Integer> valueidx = null;

               if(row > -1 && col > -1) {
                  cellContext = rlens.getCellContext(row, col);

                  if(cellContext != null) {
                     valueidx = cellContext.getValueidx();

                     if(valuesMatch(values, calc, rlens, row, col)) {
                        return new int[]{ row, col };
                     }
                  }
               }

               Integer repeatedRowCount = ((CalcTableCellValue) value).getRepRowIdx();
               int matchedRowCount = 1;

               // find a matching cell on the rest of the table
               for(int r = 0; r < rcnt && rlens.moreRows(r); r++) {
                  for(int c = 0; c < ccnt; c++) {
                     final CalcCellContext currentContext = rlens.getCellContext(r, c);
                     final Map<String, Integer> currentValueidx = currentContext.getValueidx();

                     if(valuesMatch(values, calc, rlens, r, c) &&
                        (cellContext == null || currentValueidx.equals(valueidx)))
                     {

                        if(repeatedRowCount == null || repeatedRowCount.equals(matchedRowCount)) {
                           return new int[] { r, c };
                        }

                        matchedRowCount++;
                     }
                  }
               }

               return null;
            }
            else if(table instanceof CalcTableLens) {
               int row = Integer.parseInt(values[0]);
               int col = Integer.parseInt(values[1]);
               String data = values[2];

               if(Tool.equals(Tool.toString(table.getObject(row, col)), data)) {
                  return new int[] {row, col};
               }
            }
         }
      }

      return null;
   }

   /**
    * Check if the annotation values match the table cell value
    */
   private static boolean cellValuesMatch(String[] values, Object[] objs) {
      if(values.length != objs.length ) {
         return false;
      }

      for(int i = 0; i < values.length; i++) {
         if(!cellValueMatch(values[i], objs[i])) {
            return false;
         }
      }

      return true;
   }

   private static boolean cellValueMatch(String value, Object obj) {
      boolean match = Tool.equals(Tool.toString(obj), value);

      if(!match && (obj instanceof java.sql.Date || obj instanceof java.sql.Time)) {
         match = Tool.equals(Tool.toString(new java.util.Date(((java.util.Date) obj).getTime())), value);
      }

      return match;
   }

   /**
    * Check if the annotation values match the table cell value
    */
   private static boolean valuesMatch(String[] values,
                                         CalcTableVSAssembly calc,
                                         RuntimeCalcTableLens rlens, int r, int c)
   {
      final List<String> datas = new ArrayList<>();
      prepareValues(calc, rlens, datas, r, c);
      return Tool.equals(datas.toArray(new String[0]), values);

   }

   /**
    * Find the runtime row and col index for normal table.
    */
   private static int[] findNormalTableIndex(TableLens lens,
                                             TableCellValue value)
   {
      int colIdx = Util.findColumn(lens, value.getColHeader());

      if(colIdx == -1) {
         return null;
      }

      String[] values = value.getValues();
      Tool.qsort(values, true);

      int ccnt = lens.getColCount();
      int rcnt = lens.getRowCount();
      int idx = 1;

      for(int i = 0; lens.moreRows(i); i++) {
         String[] temp = new String[ccnt];

         for(int j = 0; j < ccnt; j++) {
            Object obj = lens instanceof DataTableLens
               ? ((DataTableLens) lens).getData(i, j) : lens.getObject(i, j);
            temp[j] = Tool.toString(obj);
         }

         Tool.qsort(temp, true);

         // match the row data values
         if(Tool.equals(values, temp)) {
            // match the repeated row index
            if(idx == value.getRepeatedRowIndex()) {
               return new int[] {i, colIdx};
            }

            idx++;
         }
      }

      return null;
   }

   /**
    * Reset chart data point annotation visible and position.
    */
   public static void resetDataAnnotation(RuntimeViewsheet rvs, VSAssembly assembly,
                                          DataSet chart, ChartArea area,
                                          CommandDispatcher dispatcher,
                                          CoreLifecycleService coreLifecycleService,
                                          boolean maxMode, boolean checkMaxOrTip)
      throws Exception
   {
      if(!rvs.isRuntime() || assembly == null) {
         return;
      }

      Viewsheet vs = rvs.getViewsheet();
      VSAssemblyInfo cinfo = VSEventUtil.getAssemblyInfo(rvs, assembly);
      boolean visible = cinfo.isVisible() && VSEventUtil.isVisibleInTab(cinfo);
      String cname = assembly.getName();
      DataSet data = chart;
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(visible && data == null) {
         data = AnnotationVSUtil.getDataSet(box, assembly);
      }

      if(!(cinfo instanceof ChartVSAssemblyInfo) || data == null && visible) {
         return;
      }

      List<String> list = ((BaseAnnotationVSAssemblyInfo) cinfo).getAnnotations();
      Map<ChartToolTip, List<String>> notesMap = new HashMap<>();

      for(int i = 0; i < list.size(); i++) {
         String name = list.get(i);
         VSAssembly annotation = (VSAssembly) vs.getAssembly(name);

         if(annotation != null) {
            AnnotationVSAssemblyInfo ainfo =
               (AnnotationVSAssemblyInfo) annotation.getInfo();

            if(ainfo.getType() == AnnotationVSAssemblyInfo.ASSEMBLY) {
               if(!Tool.equals(visible && cinfo.isEnabled(), ainfo.isVisible()))
               {
                  resetAnnotation(
                     (AnnotationVSAssembly) annotation, rvs, dispatcher, coreLifecycleService,
                     visible && cinfo.isEnabled(), box);
               }
               else if(Tool.equals(maxMode || rvs.isTipView(cname) ||
                                      rvs.isPopComponent(cname), ainfo.isVisible()) && checkMaxOrTip)
               {
                  resetAnnotation(
                     (AnnotationVSAssembly) annotation, rvs, dispatcher, coreLifecycleService, !maxMode &&
                     !rvs.isTipView(cname) && !rvs.isPopComponent(cname), box);
               }
            }
            else if(ainfo.getType() == AnnotationVSAssemblyInfo.DATA) {
               String line = ainfo.getLine();
               String rect = ainfo.getRectangle();
               VSAssembly lineAssembly = vs.getAssembly(line);
               VSAssemblyInfo lineInfo = (VSAssemblyInfo) lineAssembly.getInfo();
               VSAssembly rectAssembly = vs.getAssembly(rect);
               AnnotationRectangleVSAssemblyInfo rectInfo =
                  (AnnotationRectangleVSAssemblyInfo) rectAssembly.getInfo();
               int row = visible ? getRuntimeRowIndex(data, ainfo,
                                                      (ChartVSAssembly) assembly) : -1;
               boolean found = true;

               if(row > -1) {
                  ainfo.setRow(row);
                  ainfo.setAvailable(true);
                  found = resetAnnotationPosition(rvs.getViewsheet(), assembly,
                                                  area, ainfo, row, notesMap);
               }
               else {
                  ainfo.setRow(-1);
                  ainfo.setAvailable(false);
               }

               boolean vis = row > -1 && found && visible &&
                  cinfo.isEnabled() && !rvs.isTipView(cname) &&
                  !rvs.isPopComponent(cname);
               lineInfo.setVisible(
                  "show".equals(lineInfo.getVisibleValue()) && vis);
               rectInfo.setVisible(vis);
               ainfo.setVisible(vis);

               // @by skyf, when chart refreshed, we should also refresh
               // its related annotation assemblies for re-position.
               if(coreLifecycleService != null) {
                  coreLifecycleService.refreshVSObject(annotation, rvs, null, box, dispatcher);
                  coreLifecycleService.refreshVSObject(lineAssembly, rvs, null, box, dispatcher);
                  coreLifecycleService.refreshVSObject(rectAssembly, rvs, null, box, dispatcher);
               }
            }
         }
      }

      final boolean showAnnotations =
         "true".equals(UserEnv.getProperty(rvs.getUser(), "annotation", "true"));
      VSAssemblyInfo viewsheetInfo = vs.getVSAssemblyInfo();

      if((!showAnnotations || !viewsheetInfo.isActionVisible("Bookmark"))
         && !notesMap.isEmpty())
      {
         Iterator keys = notesMap.keySet().iterator();

         while(keys.hasNext()) {
            ChartToolTip tip = (ChartToolTip) keys.next();
            String customTip = tip.getCustomToolTip();
            List notes = (List) notesMap.get(tip);
            String content = "";

            for(int i = 0; i < notes.size(); i++) {
               if(content.length() > 0) {
                  content += "<br/>";
               }

               content += notes.get(i) == null ? "" : notes.get(i);
            }

            String newTip = "<B>" + catalog.getString("Note") + ":</B>\n" +
               content + "<B>" + catalog.getString("ToolTip") + ":</B>";

            tip.setCustomToolTip(customTip != null ? newTip + "\n" + customTip :
                                    newTip);
         }
      }
   }

   /**
    * Get the runtime row index by the annotation values.
    */
   public static int getRuntimeRowIndex(DataSet chart,
      AnnotationVSAssemblyInfo ainfo, ChartVSAssembly assembly)
   {
      AnnotationCellValue value = ainfo.getValue();

      if(!(value instanceof ChartDataValue)) {
         return -1;
      }

      String[] values = value.getValues();
      values = values == null ? new String[0] : values;
      String measureName = ((ChartDataValue) value).getMeasureName();

      if(chart == null || values.length == 0 && measureName != null &&
         (chart.indexOfHeader(measureName) == -1 ||
         !chart.isMeasure(measureName)))
      {
         return -1;
      }

      boolean detail = containsNonAggregatedMeasure(assembly);
      boolean separate = isSeparateMeasure(assembly);
      boolean relationChart = isRelationChart(assembly);

      for(int i = 0; i < chart.getRowCount(); i++) {
         List list = new ArrayList();

         for(int j = 0; j < chart.getColCount(); j++) {
            String header = chart.getHeader(j);

            if(detail && separate || !chart.isMeasure(header) ||
               (detail && !separate && Tool.equals(measureName, header)))
            {
               list.add(Tool.toString(ScriptUtil.getScriptValue( chart.getData(j, i))));

               if(relationChart && Tool.equals(measureName, header)) {
                  break;
               }
            }
         }

         String[] temp = new String[list.size()];
         list.toArray(temp);

         if(Tool.equals(temp, values)) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Check if contains non-aggregated measure.
    */
   private static boolean containsNonAggregatedMeasure(ChartVSAssembly base) {
      if(base == null) {
         return false;
      }

      ChartVSAssemblyInfo chartInfo = (ChartVSAssemblyInfo) base.getInfo();
      VSChartInfo cinfo = chartInfo.getVSChartInfo();
      VSDataRef[] arefs = cinfo.getAggregateRefs();
      VSDataRef[] candleRefs = cinfo.getStockOrCandleFields();
      Object[] refs = Tool.mergeArray(arefs, candleRefs);

      for(int i = 0; i < refs.length; i++) {
         if(refs[i] instanceof VSAggregateRef &&
            !((VSAggregateRef) refs[i]).isAggregateEnabled())
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if measure is bound to x, y, group, aesthetic separately.
    */
   private static boolean isSeparateMeasure(ChartVSAssembly base) {
      if(base == null) {
         return false;
      }

      ChartVSAssemblyInfo chartInfo = (ChartVSAssemblyInfo) base.getInfo();
      VSChartInfo info = chartInfo.getVSChartInfo();

      if(info instanceof CandleChartInfo) {
         return true;
      }

      VSDataRef[][] fields = {info.getRTXFields(), info.getRTYFields(),
         info.getRTGroupFields(), info.getRTAestheticFields()};
      List measures = new ArrayList();

      for(VSDataRef[] flds : fields) {
         if(flds == null || flds.length == 0) {
            continue;
         }

         if(flds[flds.length - 1] instanceof VSAggregateRef) {
            measures.add(flds[flds.length - 1]);
         }
      }

      return measures.size() > 1;
   }

   /**
    * Check if chart is a relation chart
    */
   private static boolean isRelationChart(ChartVSAssembly base) {
      if(base == null) {
         return false;
      }

      ChartVSAssemblyInfo chartInfo = (ChartVSAssemblyInfo) base.getInfo();
      VSChartInfo info = chartInfo.getVSChartInfo();

      return info instanceof RelationVSChartInfo;
   }

   /**
    * Reset data point annotation position.
    */
   public static boolean resetAnnotationPosition(
      Viewsheet topvs, VSAssembly chart, ChartArea chartArea,
      AnnotationVSAssemblyInfo ainfo, int row, Map<ChartToolTip, List<String>> notesMap)
   {
      if(!(chart instanceof ChartVSAssembly) || chartArea == null) {
         return false;
      }

      PlotArea plot = chartArea.getPlotArea();
      DefaultArea[] childs = plot.getOriginalAreas();
      AnnotationCellValue value = ainfo.getValue();
      String measure = ((ChartDataValue) value).getMeasureName();
      ChartVSAssemblyInfo chartInfo = (ChartVSAssemblyInfo) chart.getInfo();
      VSChartInfo cinfo = chartInfo.getVSChartInfo();
      boolean isMap = GraphTypes.isMap(cinfo.getRTChartType());
      Insets padding = chartInfo.getPadding();
      EGraph graph = chartArea.getEGraph();
      boolean wordCloud = graph.getElement(0) instanceof PointElement &&
         ((PointElement) graph.getElement(0)).isWordCloud();
      int titleH = 0;

      if(chartInfo.isTitleVisible()) {
         titleH = chartInfo.getTitleHeight();
      }

      for(DefaultArea child : childs) {
         if(child instanceof VisualObjectArea || child instanceof TextArea && wordCloud) {
            InteractiveArea visual = (InteractiveArea) child;
            int rowIdx = visual.getRowIndex();
            String measureName = visual.getMeasureName();

            if(rowIdx != row || !wordCloud && !Tool.equals(measureName, measure)) {
               continue;
            }

            Point oldPt = topvs.getPixelPositionInViewsheet(ainfo);
            String line = ainfo.getLine();
            Assembly lineobj = topvs.getAssembly(line);
            LineVSAssemblyInfo linfo = null;
            int endYPos = 0;
            int endXPos = 0;

            if(lineobj != null) {
               linfo = (LineVSAssemblyInfo) lineobj.getInfo();
               endYPos = linfo.getEndPos().y;
               endXPos = linfo.getEndPos().x;
            }

            Rectangle plotBounds = getAreaBounds(plot);
            Rectangle voRect = getAreaBounds(visual);
            int centerX = voRect.x + voRect.width / 2;
            int centerY = voRect.y + voRect.height / 2;

            Region[] regions = visual.getRegions();

            if(isMap && regions != null && regions.length > 0 &&
               regions[0] instanceof PolygonRegion) {
               PolygonRegion pregion = getMaxRegion(regions);
               Shape shape = pregion.createShape();

               if(shape instanceof Polygon) {
                  Point center = getPolygonCenter((Polygon) shape);

                  if(center != null) {
                     centerX = center.x;
                     centerY = center.y;
                  }
               }
            }

            // x and y represent the pixel position from the top-left of
            // the chart to the center position of the data.
            int x = plotBounds.x + centerX + padding.left;
            int y = plotBounds.y + centerY + padding.top + titleH;

            // new annotation position
            // @by skyf, we should make the end point of line to point at
            // bar vo center, not the annotation left-top,
            // so here subtract the line end position.
            Point newPt = new Point(x - endXPos, y - endYPos);
            Point pixelPt = topvs.getPixelPosition(newPt);

            ainfo.setPixelOffset(pixelPt);
            ainfo.setScaledPosition(pixelPt);

            // new annotation line position
            if(linfo != null) {
               linfo.setPixelOffset(pixelPt);
               linfo.setScaledPosition(pixelPt);
            }

            // new annotation rectangle position
            String rect = ainfo.getRectangle();
            Assembly rectobj = topvs.getAssembly(rect);

            if(rectobj != null) {
               AnnotationRectangleVSAssemblyInfo rinfo =
                  (AnnotationRectangleVSAssemblyInfo) rectobj.getInfo();
               Point rectPt = rinfo.getLayoutPosition() != null ? rinfo.getLayoutPosition() :
                  topvs.getPixelPositionInViewsheet(rinfo);
               int changeX = newPt.x - oldPt.x;
               int changeY = newPt.y - oldPt.y;
               Point newRectPt =
                  new Point(rectPt.x + changeX, rectPt.y + changeY);
               Point rectPixelPt = topvs.getPixelPosition(newRectPt);

               rinfo.setPixelOffset(rectPixelPt);
               rinfo.setScaledPosition(rectPixelPt);
               ChartToolTip tip = visual.getToolTip();

               if(notesMap != null) {
                  if(!notesMap.containsKey(tip)) {
                     List<String> list = new ArrayList<>();
                     list.add(rinfo.getContent());
                     notesMap.put(tip, list);
                  }
                  else {
                     notesMap.get(tip).add(rinfo.getContent());
                  }
               }
            }

            // if chart fill time-series gaps with null, this area x or y
            // coordinate maybe NaN, and this area can't display, ignore it.
            boolean areaExist = true;
            Region region = visual.getRegion();

            if(region instanceof EllipseRegion) {
               EllipseRegion eregion = (EllipseRegion) region;
               Shape shape = eregion.createShape();

               if(shape instanceof DEllipse2D &&
                  (Double.isNaN(((DEllipse2D) shape).getX()) ||
                  Double.isNaN(((DEllipse2D) shape).getY())))
               {
                  areaExist = false;
               }
            }

            return areaExist;
         }
      }

      ainfo.setAvailable(false);
      return false;
   }

   /**
    * Get the max area region.
    */
   private static PolygonRegion getMaxRegion(Region[] regions) {
      int idx = 0;
      int cnt = 0;

      for(int i = 0; i < regions.length; i++) {
         if(!(regions[i] instanceof PolygonRegion)) {
            continue;
         }

         Shape shape = ((PolygonRegion) regions[i]).createShape();

         if(shape instanceof Polygon) {
            Polygon polygon = (Polygon) shape;

            if(polygon.npoints > cnt) {
               cnt = polygon.npoints;
               idx = i;
            }
         }
      }

      return (PolygonRegion) regions[idx];
   }

   /**
    * Get polygon center point.
    */
   private static Point getPolygonCenter(Polygon polygon) {
      if(polygon == null) {
         return null;
      }

      int[] xp = polygon.xpoints;
      int[] yp = polygon.ypoints;
      int cnt = polygon.npoints;

      if(xp == null || yp == null) {
         return null;
      }

      int area = 0;

      for(int i = 0; i < cnt - 1; i++) {
         area += xp[i] * yp[i + 1] - xp[i + 1] * yp[i];
      }

      area = area / 2;

      int x = 0;

      for(int i = 0; i < cnt - 1; i++) {
         x += (xp[i] + xp[i + 1]) * (xp[i] * yp[i + 1] - xp[i + 1] * yp[i]);
      }

      x = x / (6 * area);

      int y = 0;

      for(int i = 0; i < cnt - 1; i++) {
         y += (yp[i] + yp[i + 1]) * (xp[i] * yp[i + 1] - xp[i + 1] * yp[i]);
      }

      y = y / (6 * area);

      return new Point(x, y);
   }

   /**
    * Get the bounds of this area.
    */
   private static Rectangle getAreaBounds(AbstractArea area) {
      if(area == null) {
         return new Rectangle(0, 0, 0, 0);
      }

      return area.getRegion().getBounds();
   }

   /**
    * Get the parent annotation assembly.
    * @param vs the specified viewsheet.
    * @param name the child line or rectangle name of annotation assembly.
    */
   public static Assembly getAnnotationAssembly(Viewsheet vs, String name) {
      if(vs == null || name == null) {
         return null;
      }

      Assembly[] assemblies = vs.getAssemblies(true, false);

      for(int i = 0; i < assemblies.length; i++) {
         Assembly assembly = assemblies[i];

         if(assembly instanceof AnnotationVSAssembly) {
            AnnotationVSAssembly assem = (AnnotationVSAssembly) assembly;
            AnnotationVSAssemblyInfo info =
               (AnnotationVSAssemblyInfo) assem.getVSAssemblyInfo();

            if(name.equals(info.getLine()) || name.equals(info.getRectangle()))
            {
               return assem;
            }
         }
      }

      return null;
   }

   /**
    * Get the annotation related object name.
    * @param vs the specified viewsheet.
    * @param name the specified annotation name.
    */
   public static String getAnnotationParentName(Viewsheet vs, String name) {
      if(vs == null || name == null) {
         return null;
      }

      Assembly anno = vs.getAssembly(name);

      if(anno == null) {
         return null;
      }

      Assembly[] assemblies = vs.getAssemblies(true, false);

      for(int i = 0; i < assemblies.length; i++) {
         Assembly assembly = assemblies[i];
         AssemblyInfo info = assembly.getInfo();

         if(info instanceof BaseAnnotationVSAssemblyInfo) {
            List<String> annotations =
               ((BaseAnnotationVSAssemblyInfo) info).getAnnotations();

            for(String aname : annotations) {
               if(name.equals(aname)) {
                  return assembly.getAbsoluteName();
               }
            }
         }
      }

      return null;
   }

   /**
    * Get the base annotation assembly which contains this annotatoin.
    * @param vs the specified viewsheet.
    * @param name the annotation assembly name.
    */
   public static Assembly getBaseAssembly(Viewsheet vs, String name) {
      if(vs == null || name == null) {
         return null;
      }

      Assembly[] assemblies = vs.getAssemblies(true, false);

      for(int i = 0; i < assemblies.length; i++) {
         Assembly assembly = assemblies[i];

         if(assembly.getInfo() instanceof BaseAnnotationVSAssemblyInfo) {
            BaseAnnotationVSAssemblyInfo binfo =
               (BaseAnnotationVSAssemblyInfo) assembly.getInfo();
            List<String> annos = binfo.getAnnotations();

            for(int j = 0; j < annos.size(); j++) {
               String annoName = annos.get(j);

               if(Tool.equals(annoName, name)) {
                  return assembly;
               }
            }
         }
      }

      return null;
   }

   public static String getAnnotationHTMLContent(Viewsheet vs,
                                                 AnnotationRectangleVSAssemblyInfo info,
                                                 Rectangle2D bounds)
   {
      String content = info.getContent();

      if(content == null || "".equals(content)) {
         return "";
      }

      bounds.setRect(bounds.getX() + 1, bounds.getY() + 1,
                     bounds.getWidth() - 2, bounds.getHeight() - 2);

      Pattern p = Pattern.compile("SIZE=\"(\\d+)\"");
      Matcher m = p.matcher(content);

      while(m.find()) {
         String str = "STYLE=\"FONT-SIZE:" + m.group(1) + "px\"";
         content = content.replaceAll(m.group(), str);
      }

      try {
         org.jsoup.nodes.Document document = Jsoup.parse(content);
         org.jsoup.nodes.Element documentElement = document.root();

         if(documentElement != null) {
            replaceHslColorToRgbColor(documentElement);
         }

         content = document.html();
      }
      catch(Exception ignore) {
      }

      return content;
   }

   /**
    * Get annotation's content.
    */
   public static List getAnnotationContent(Viewsheet vs,
      AnnotationRectangleVSAssemblyInfo info, Rectangle2D bounds)
   {
      String content = info.getContent();

      if(content == null || "".equals(content)) {
         return new ArrayList();
      }

      bounds.setRect(bounds.getX() + 1, bounds.getY() + 1,
         bounds.getWidth() - 2, bounds.getHeight() - 2);

      Pattern p = Pattern.compile("SIZE=\"(\\d+)\"");
      Matcher m = p.matcher(content);

      while(m.find()) {
         String str = "STYLE=\"FONT-SIZE:" + m.group(1) + "px\"";
         content = content.replaceAll(m.group(), str);
      }

      return getHTMLContent(content);
   }

   private static void replaceHslColorToRgbColor(org.jsoup.nodes.Element element) throws Exception {
      List<org.jsoup.nodes.Node> childNodes = element.childNodes();

      for(int i = 0; i < childNodes.size(); i++) {
         org.jsoup.nodes.Node item = childNodes.get(i);

         if(!(item instanceof org.jsoup.nodes.Element)) {
            continue;
         }

         org.jsoup.nodes.Element child = (org.jsoup.nodes.Element) item;
         String styles = child.attr("style");

         if(styles != null) {
            String[] split = styles.split(";");

            for(int j = 0; j < split.length; j++) {
               String style = split[j];

               if(style == null) {
                  continue;
               }

               int hsl = style.indexOf("hsl");

               if(hsl <= 0) {
                  continue;
               }

               String[] styleKeyValue = style.split(":");

               for(int k = 1; k < styleKeyValue.length; k++) {
                  String styleValue = styleKeyValue[k];
                  Matcher matcher = pattern.matcher(styleValue);

                  if(matcher.matches()) {
                     String h = matcher.group(1);
                     String s = matcher.group(2);
                     String l = matcher.group(3);
                     Number hue = NumberFormat.getInstance().parse(h);
                     Number saturation = NumberFormat.getPercentInstance().parse(s);
                     Number lightness = NumberFormat.getPercentInstance().parse(l);
                     String htmlColorString = hslToColorHtmlString(hue.floatValue() / 360,
                        saturation.floatValue(), lightness.floatValue());
                     styleKeyValue[k] = htmlColorString;

                  }
               }

               split[j] = String.join(":", styleKeyValue);
            }

            styles = String.join(";", split);
            child.attr("style", styles);
         }

         replaceHslColorToRgbColor(child);
      }
   }

   /**
    * Converts an HSL color value to RGB.
    * Assumes h, s, and l are contained in the set [0, 1] and
    * returns r, g, and b in the set [0, 255].
    *
    * @param h       The hue
    * @param s       The saturation
    * @param l       The c
    * @return int array, the RGB representation
    */
   public static String hslToColorHtmlString(float h, float s, float l){
      float r, g, b;

      if (s == 0f) {
         r = g = b = l; // achromatic
      }
      else {
         float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
         float p = 2 * l - q;
         r = hueToRgb(p, q, h + 1f/3f);
         g = hueToRgb(p, q, h);
         b = hueToRgb(p, q, h - 1f/3f);
      }

      return "#" + Tool.colorToHTMLString(new Color(to255(r), to255(g), to255(b)));
   }

   private static int to255(float v) {
      return (int)Math.min(255, 256*v);
   }

   /** Helper method that converts hue to rgb */
   private static float hueToRgb(float p, float q, float t) {
      if(t < 0f) {
         t += 1f;
      }

      if(t > 1f) {
         t -= 1f;
      }

      if(t < 1f/6f) {
         return p + (q - p) * 6f * t;
      }

      if(t < 1f/2f) {
         return q;
      }

      if(t < 2f/3f) {
         return p + (q - p) * (2f/3f - t) * 6f;
      }

      return p;
   }

   public static int getAnnotationTextAlignment(String content) {

      if(content == null) {
         return StyleConstants.H_LEFT;
      }

      Pattern p = Pattern.compile("text-align: ([a-z]+);\"");
      Matcher m = p.matcher(content);
      String textAlign;

      if(m.find()) {
         textAlign = m.group(1);
      }
      else {
         textAlign = "left";
      }

      switch(textAlign) {
      case "left":
         return StyleConstants.H_LEFT;
      case "center":
         return StyleConstants.H_CENTER;
      case "right":
         return StyleConstants.H_RIGHT;
      default:
         return StyleConstants.H_LEFT;
      }
   }

   public static List getHTMLContent(String str) {
      HTMLPresenter presenter = new HTMLPresenter();
      PresenterPainter painter = new PresenterPainter(str, presenter);
      Image img = Tool.createImage(1000, 1000, false);
      Graphics g = img.getGraphics();
      g.setFont(VSUtil.getDefaultFont());
      RichTextGraphics rtg = new RichTextGraphics((Graphics2D) g);
      painter.paint(rtg, 0, 0, 1000, 1000);
      rtg.dispose();

      return rtg.getRichTexts();
   }

   /**
    * Refresh all related annotations include assemblies in container.
    */
   public static void refreshAllAnnotations(RuntimeViewsheet rvs, VSAssembly assembly,
                                            CommandDispatcher dispatcher,
                                            CoreLifecycleService coreLifecycleService)
      throws Exception
   {
      Viewsheet vs =  rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      if(assembly instanceof ContainerVSAssembly) {
         List<Assembly> list = new ArrayList<>();
         VSEventUtil.getAssembliesInContainer(vs, assembly, list);

         for(int i = 0; i < list.size(); i++) {
            VSAssembly child = (VSAssembly) list.get(i);
            refreshAnnotations(rvs, vs, child, dispatcher, coreLifecycleService);
         }
      }
      else if(assembly instanceof Viewsheet) {
         ViewsheetVSAssemblyInfo vinfo = (ViewsheetVSAssemblyInfo) assembly.getInfo();
         List objList = vinfo.getChildAssemblies();

         for(int i = 0; i < objList.size(); i++) {
            VSAssembly sassembly = (VSAssembly) objList.get(i);
            refreshAnnotations(rvs, vs, sassembly, dispatcher, coreLifecycleService);
         }
      }
      else {
         refreshAnnotations(rvs, vs, assembly, dispatcher, coreLifecycleService);
      }
   }

   /**
    * Refresh related annotations.
    */
   public static void refreshAnnotations(RuntimeViewsheet rvs, Viewsheet vs,
                                         VSAssembly assembly,
                                         CommandDispatcher dispatcher,
                                         CoreLifecycleService coreLifecycleService)
      throws Exception
   {
      VSAssemblyInfo info = VSEventUtil.getAssemblyInfo(rvs, assembly);

      if(info instanceof BaseAnnotationVSAssemblyInfo && rvs.isRuntime()) {
         ViewsheetSandbox box = rvs.getViewsheetSandbox();
         List<String> list = ((BaseAnnotationVSAssemblyInfo) info).getAnnotations();

         if(list.isEmpty()) {
            return;
         }

         if(info instanceof ChartVSAssemblyInfo) {
            try {
               String aname = assembly.getAbsoluteName();
               VSAssemblyInfo vinfo =
                  VSEventUtil.getAssemblyInfo(rvs, assembly);
               boolean visible = vinfo.isVisible() &&
                  VSEventUtil.isVisibleInTab(vinfo);

               DataSet data = null;
               ChartArea area = null;

               if(visible) {
                  VSChartInfo cinfo =
                     ((ChartVSAssembly) assembly).getVSChartInfo();
                  VGraphPair pair = box.getVGraphPair(aname);
                  data = pair != null ? pair.getData() : (DataSet) box.getData(aname);
                  area = pair == null || !pair.isCompleted()
                     ? null : new ChartArea(pair, null, cinfo, null, false, true);
               }

               resetDataAnnotation(
                  rvs, assembly, data, area, dispatcher, coreLifecycleService, false,
                  false);
            }
            catch(Exception ex) {
               // chart area maybe not ready, just ignore it, we will
               // re-position the annotation in GetChartAreaEvent
            }
         }
         else {
            for(int i = 0; i < list.size(); i++) {
               String name = list.get(i);
               AnnotationVSAssembly annotation = (AnnotationVSAssembly) vs.getAssembly(name);

               if(annotation == null) {
                  continue;
               }

               AnnotationVSAssemblyInfo ainfo =
                  (AnnotationVSAssemblyInfo) annotation.getInfo();
               boolean visible =
                  info.isVisible() && VSEventUtil.isVisibleInTab(info);

               // reset the data annotation position
               if(ainfo.getType() == AnnotationVSAssemblyInfo.DATA) {
                  boolean isForm = info instanceof TableVSAssemblyInfo &&
                     ((TableVSAssemblyInfo) info).isForm();

                  if(!isForm && visible && info.isEnabled()) {
                     int[] idx = getRuntimeIndex(box, assembly, null, ainfo);
                     ainfo.setAvailable(idx != null ? true : false);

                     if(idx != null) {
                        ainfo.setRow(idx[0]);
                        ainfo.setCol(idx[1]);

                        // annotation relies on refresh to get the correct
                        // target (cell) information and must be executed
                        final String annotationParent = getAnnotationParentName(vs, name);
                        final VSAssembly parentAssembly =
                           (VSAssembly) vs.getAssembly(annotationParent);
                        resetAnnotation(
                           annotation, rvs, dispatcher, coreLifecycleService, true, box);

                        if(coreLifecycleService != null) {
                           coreLifecycleService.refreshVSObject(
                                   parentAssembly, rvs, null, box, dispatcher);
                        }
                     }
                     else {
                        ainfo.setRow(-1);
                        ainfo.setCol(-1);

                        resetAnnotation(
                           annotation, rvs, dispatcher, coreLifecycleService, false, box);
                     }
                  }
                  else {
                     ainfo.setRow(-1);
                     ainfo.setCol(-1);

                     resetAnnotation(
                        annotation, rvs, dispatcher, coreLifecycleService, false, box);
                  }
               }
               else if(!Tool.equals(visible && info.isEnabled(),
                                    ainfo.isVisible()))
               {
                  resetAnnotation(
                     annotation, rvs, dispatcher, coreLifecycleService,
                     visible && info.isEnabled(), box);
               }
            }
         }
      }
   }

   /**
    * Show or hide annotation component.
    */
   public static void resetAnnotation(AnnotationVSAssembly annotation, RuntimeViewsheet rvs,
                                      CommandDispatcher dispatcher,
                                      CoreLifecycleService coreLifecycleService, boolean vis,
                                      ViewsheetSandbox box)
   {
      Viewsheet vs = rvs.getViewsheet();
      AnnotationVSAssemblyInfo ainfo = (AnnotationVSAssemblyInfo) annotation.getVSAssemblyInfo();
      boolean oldVisible = ainfo.isVisible();
      ainfo.setVisible(vis);

      if(coreLifecycleService != null) {
         coreLifecycleService.refreshVSObject(annotation, rvs, null, box, dispatcher);
      }

      String line = ainfo.getLine();
      VSAssembly lass = (VSAssembly) vs.getAssembly(line);

      if(lass != null) {
         VSAssemblyInfo linfo = (VSAssemblyInfo) lass.getInfo();
         linfo.setVisible(vis);

         if(coreLifecycleService != null) {
            coreLifecycleService.refreshVSObject(lass, rvs, null, box, dispatcher);
         }
      }

      String rect = ainfo.getRectangle();
      VSAssembly rass = (VSAssembly) vs.getAssembly(rect);

      if(rass != null) {
         VSAssemblyInfo rinfo = (VSAssemblyInfo) rass.getInfo();
         rinfo.setVisible(vis);

         if(coreLifecycleService != null) {
            coreLifecycleService.refreshVSObject(rass, rvs, null, box, dispatcher);
         }
      }
   }

   /**
    * If already contains LoadTableLensCommand, we don't need refresh all
    * annotations, because after load tablelens, we will re-position all
    * annotations.
    */
   public static boolean needRefreshAnnotation(VSAssemblyInfo info,
                                               CommandDispatcher dispatcher)
   {
      if(info instanceof BaseAnnotationVSAssemblyInfo) {
         BaseAnnotationVSAssemblyInfo binfo =
            (BaseAnnotationVSAssemblyInfo) info;
         List annos = binfo.getAnnotations();

         if(annos == null || annos.size() == 0) {
            return false;
         }
      }

      if(!(info instanceof TableDataVSAssemblyInfo)) {
         return true;
      }

      for(CommandDispatcher.Command command : dispatcher) {
         if("LoadTableLensCommand".equals(command.getType()) &&
            info.getAbsoluteName().equals(command.getAssembly()))
         {
            return false;
         }
      }

      return true;
   }

   /**
    * Parse annotations.
    */
   public static void parseAllAnnotations(Element elem, Viewsheet vs)
      throws Exception
   {
      // remove all annotations firstly
      Assembly[] all = vs.getAssemblies();

      for(int i = 0; i < all.length; i++) {
         if(isAnnotation((VSAssembly) all[i])) {
            VSAssemblyInfo info = (VSAssemblyInfo) all[i].getInfo();
            vs.removeAssembly(info.getAbsoluteName());
         }
      }

      NodeList anodes = Tool.getChildNodesByTagName(elem, "assembly");

      for(int i = 0; i < anodes.getLength(); i++) {
         Element anode = (Element) anodes.item(i);
         String cls = Tool.getAttribute(anode, "class");

         if("inetsoft.uql.viewsheet.AnnotationVSAssembly".equals(cls) ||
            "inetsoft.uql.viewsheet.AnnotationRectangleVSAssembly".equals(cls))
         {
            Element nnode = Tool.getChildNodeByTagName(anode,
                                                       "assemblyInfo");

            if(nnode != null) {
               VSAssembly assembly =
                  AbstractVSAssembly.createVSAssembly(anode, vs);

               if(assembly == null) {
                  continue;
               }

               vs.addAssembly(assembly);
               continue;
            }
         }

         Element nnode = Tool.getChildNodeByTagName(anode, "name");
         String name = Tool.getValue(nnode);
         VSAssembly assembly = (VSAssembly) vs.getAssembly(name);

         if(assembly != null &&
            assembly.getInfo() instanceof BaseAnnotationVSAssemblyInfo)
         {
            ((AbstractVSAssembly) assembly).parseAnnotations(anode,
               (VSAssemblyInfo) assembly.getInfo());
         }
         else if(assembly instanceof Viewsheet) {
            parseAllAnnotations(anode, (Viewsheet) assembly);
         }
      }
   }

   /**
    * Check whether is annotation component.
    */
   public static boolean isAnnotation(VSAssembly vsassembly) {
      return vsassembly instanceof AnnotationVSAssembly ||
         vsassembly instanceof AnnotationLineVSAssembly ||
         vsassembly instanceof AnnotationRectangleVSAssembly;
   }

   /**
    * Get the annotation type.
    */
   public static int getAnnoType(Viewsheet vs, VSAssembly ass) {
      if(ass instanceof AnnotationVSAssembly) {
         AnnotationVSAssemblyInfo ainfo =
            (AnnotationVSAssemblyInfo) ass.getInfo();
         return ainfo.getType();
      }
      else if(ass instanceof AnnotationLineVSAssembly ||
         ass instanceof AnnotationRectangleVSAssembly)
      {
         Assembly anno = getAnnotationAssembly(vs, ass.getAbsoluteName());
         return getAnnoType(vs, (VSAssembly) anno);
      }

      return -1;
   }

   /**
    * Remove the over time annotation.
    */
   public static boolean isOvertime(Assembly nass) {
      if(nass instanceof AnnotationVSAssembly) {
         AnnotationVSAssemblyInfo ainfo =
            (AnnotationVSAssemblyInfo) nass.getInfo();
         String days =
            SreeEnv.getProperty("vs.annotation.cleanup.interval", "7");
         int day = 7;

         try {
            day = Integer.parseInt(days);
         }
         catch(Exception ex) {
            day = 7;
         }

         long times = day * 24 * 3600000l;

         //for new annotation, lastDisplay is 0
         if(times > 0 && ainfo.getLastDisplay() > 0 &&
            System.currentTimeMillis() - ainfo.getLastDisplay() > times)
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if any annotation exists in the viewsheet.
    */
   public static boolean isAnnotated(Viewsheet vs) {
      for(Assembly obj : vs.getAssemblies(true)) {
         AssemblyInfo info = obj.getInfo();

         if(info instanceof AnnotationVSAssemblyInfo) {
            return true;
         }
         else if(info instanceof BaseAnnotationVSAssemblyInfo) {
            BaseAnnotationVSAssemblyInfo binfo =
               (BaseAnnotationVSAssemblyInfo) info;

            if(binfo.getAnnotations().size() > 0) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Refresh the assembly annotation position by the relative offset.
    */
   public static void refreshAssemblyAnnoPosition(Viewsheet vs, AnnotationVSAssemblyInfo ainfo) {
      if(vs == null || ainfo == null) {
         return;
      }

      final String annotationName = ainfo.getAbsoluteName();
      final String parentName = getAnnotationParentName(vs, annotationName);
      final Assembly parentAssembly = vs.getAssembly(parentName);

      if(parentAssembly == null) {
         return;
      }

      final Viewsheet pvs = ((VSAssembly) parentAssembly).getViewsheet();
      final AssemblyInfo parentInfo = parentAssembly.getInfo();
      final Point parentPos = pvs.getPixelPosition(parentInfo);
      final Point annoOffset = ainfo.getPixelOffset();
      final Point newPos = new Point(parentPos.x + annoOffset.x,
                                     parentPos.y + annoOffset.y);
      refreshAnnoPosition(vs, ainfo, newPos);
   }

   /**
    * Refresh the annotation position.
    */
   public static void refreshAnnoPosition(Viewsheet vs, VSAssemblyInfo info, Point newPos) {
      if(!(info instanceof AnnotationVSAssemblyInfo)) {
         return;
      }

      AnnotationVSAssemblyInfo ainfo = (AnnotationVSAssemblyInfo) info;
      Point annoPos = vs.getPixelPosition(ainfo);

      if(newPos.x != annoPos.x || newPos.y != annoPos.y) {
         //Dimension preferedDim = vs.getPreferredSize(true, false, true);
         Point vsPixelPos = vs.getPixelPosition(newPos);
         ainfo.setPixelOffset(vsPixelPos);

         if(ainfo.isScaled()) {
            ainfo.setScaledPosition(vsPixelPos);
         }

         Assembly lineobj = vs.getAssembly(ainfo.getLine());

         if(lineobj != null) {
            LineVSAssemblyInfo linfo = (LineVSAssemblyInfo) lineobj.getInfo();
            linfo.setPixelOffset(ainfo.getPixelOffset());

            if(linfo.isScaled()) {
               linfo.setScaledPosition(ainfo.getPixelOffset());
            }
         }

         Assembly rectobj = vs.getAssembly(ainfo.getRectangle());
         VSAssembly base = (VSAssembly)
                              AnnotationVSUtil.getBaseAssembly(vs, ainfo.getAbsoluteName());

         if(rectobj != null) {
            final ShapeVSAssemblyInfo rinfo = (ShapeVSAssemblyInfo) rectobj.getInfo();
            final Point rectPt = vs.getPixelPosition(rinfo);
            final int changeX = newPos.x - annoPos.x;
            final int changeY = newPos.y - annoPos.y;
            // don't move annotation inside since the bounds would include annotation already
            //final Dimension pixelSize = rectobj.getPixelSize();
            //int minX = Math.min(rectPt.x + changeX, preferedDim.width - pixelSize.width);
            //int minY = Math.min(rectPt.y + changeY, preferedDim.height - pixelSize.height);
            int minX = rectPt.x + changeX;
            int minY = rectPt.y + changeY;
            Point rectPixelPt = vs.getPixelPosition(new Point(minX, minY));
            rinfo.setPixelOffset(rectPixelPt);

            if(rinfo.isScaled()) {
               rinfo.setScaledPosition(rectPixelPt);
            }
         }
      }
   }

   /**
    * Get new annotation position.
    */
   public static Point getNewPos(Viewsheet vs, VSAssemblyInfo info, int x,
                                 int y)
   {
      if(vs == null || !(info instanceof AnnotationVSAssemblyInfo)) {
         return null;
      }

      AnnotationVSAssemblyInfo ainfo = (AnnotationVSAssemblyInfo) info;
      Assembly lineobj = vs.getAssembly(ainfo.getLine());

      if(lineobj == null) {
         return null;
      }

      LineVSAssemblyInfo linfo = (LineVSAssemblyInfo) lineobj.getInfo();
      int endYPos = linfo.getEndPos().y;
      int endXPos = linfo.getEndPos().x;
      int nx = x - endXPos;
      int ny = y - endYPos;

      return new Point(nx, ny);
   }

   /**
    * Adjust annotation position if chart plot is scaled.
    */
   public static void adjustAnnotationPosition(Viewsheet vs, AnnotationVSAssemblyInfo ainfo,
                                               VGraphPair pair)
   {
      if(vs == null || ainfo == null || pair == null) {
         return;
      }

      VGraph vgraph = pair.getRealSizeVGraph();
      VGraph evgraph = pair.getExpandedVGraph();
      Rectangle2D vpb = null;
      Rectangle2D evpb = null;

      if(vgraph != null) {
         vpb = vgraph.getPlotBounds();
      }

      if(evgraph != null) {
         evpb = evgraph.getPlotBounds();
      }

      if(vpb != null && evpb != null) {
         double hratio = vpb.getHeight() / evpb.getHeight();
         double wratio = vpb.getWidth() / evpb.getWidth();
         double y = ainfo.getPixelOffset().getY();
         double x = ainfo.getPixelOffset().getX();
         y -= vpb.getY();
         x -= vpb.getX();
         int h = (int) Math.ceil(y * hratio + vpb.getY());
         int w = (int) Math.ceil(x * wratio + vpb.getX());

         AnnotationVSUtil.refreshAnnoPosition(vs, ainfo, new Point(w, h));
      }
   }

   /**
    * Remove useless assemblies.
    */
   public static void removeUselessAssemblies(Assembly[] oldAss,
                                              Assembly[] newAss,
                                              CommandDispatcher dispatcher)
   {
      for(Assembly oass : oldAss) {
         boolean found = false;
         final boolean isAnnotation = oass instanceof AnnotationVSAssembly;

         for(Assembly nass : newAss) {
            // same name, same view?
            if(oass.getAbsoluteName().equals(nass.getAbsoluteName()) &&
               oass.getClass().getName().equals(nass.getClass().getName()))
            {
               found = true;
               break;
            }
         }

         // always remove annotations - add commands are not generated for data and
         // assembly annotations so sharing the same name does not guarantee that they
         // will share the same view
         if(!found || isAnnotation) {
            inetsoft.web.viewsheet.command.RemoveVSObjectCommand command =
               new inetsoft.web.viewsheet.command.RemoveVSObjectCommand();
            command.setName(oass.getAbsoluteName());

            if(((VSAssembly) oass).getContainer() != null &&
               ((VSAssembly) oass).getContainer() instanceof CurrentSelectionVSAssembly)
            {
               //If oass is in selection container, send event to selection container to remove child object
               dispatcher.sendCommand(((VSAssembly) oass).getContainer()
                                                         .getAbsoluteName(), command);
            }
            else {
               dispatcher.sendCommand(command);
            }
         }
      }
   }

   /**
    * For embedded table, update the cell value of annotation after dnd.
    */
   public static void fixAnnotationCellValue(RuntimeViewsheet rvs, EmbeddedTableVSAssembly assembly,
                                             ViewsheetEvent evt)
      throws Exception
   {
      if(!(evt instanceof VSDndEvent) || rvs == null || assembly == null ||
         !(assembly.getVSAssemblyInfo() instanceof BaseAnnotationVSAssemblyInfo))
      {
         return;
      }

      TableTransfer transfer = (TableTransfer) ((VSDndEvent) evt).getTransfer();
      BindingDropTarget dropTarget = (BindingDropTarget) ((VSDndEvent) evt).getDropTarget();
      int sourceIndex = transfer != null ? transfer.getDragIndex() : -1;
      int targetIndex = dropTarget != null ? dropTarget.getDropIndex() : -1;

      if(targetIndex > sourceIndex) {
         targetIndex -= 1;
      }

      if(sourceIndex == -1 || targetIndex == -1) {
         return;
      }

      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      VSAssemblyInfo info = VSEventUtil.getAssemblyInfo(rvs, assembly);
      List<String> annotations = ((BaseAnnotationVSAssemblyInfo) info).getAnnotations();

      if(annotations.isEmpty()) {
         return;
      }

      for(int i = 0; i < annotations.size(); i++) {
         String annoName = annotations.get(i);
         AnnotationVSAssembly annotation = (AnnotationVSAssembly) vs.getAssembly(annoName);

         if(annotation == null) {
            continue;
         }

         AnnotationVSAssemblyInfo annotationInfo = (AnnotationVSAssemblyInfo) annotation.getInfo();
         EmbeddedCellValue value = (EmbeddedCellValue) annotationInfo.getValue();
         int row = Integer.parseInt(value.getValues()[0]);
         int col = Integer.parseInt(value.getValues()[1]);

         if(col == sourceIndex) {
            annotationInfo.setValue(getAnnotationDataValue(box, assembly, row, targetIndex, null));
         }
      }
   }

   private static boolean isForCalc(TableLens table) {
      boolean forCalc = table instanceof CrossFilter || table instanceof HiddenRowColFilter;

      if(!forCalc && table instanceof TextSizeLimitTableLens) {
         TableLens subTable = ((TextSizeLimitTableLens) table).getTable();
         forCalc = subTable instanceof CrossFilter || subTable instanceof HiddenRowColFilter;
      }

      return forCalc;
   }

   private static Pattern pattern = Pattern.compile("^hsl\\((\\d+),\\s*(\\d+%),\\s*(\\d+%)\\)$");
   private static Catalog catalog = Catalog.getCatalog();

   private static final Logger LOG = LoggerFactory.getLogger(AnnotationVSUtil.class);
}
