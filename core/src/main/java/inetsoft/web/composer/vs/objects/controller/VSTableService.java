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
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.Util;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Catalog;
import inetsoft.util.CoreTool;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.awt.*;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;

@Component
public class VSTableService {
   public static DataRef getCrosstabCellDataRef(VSCrosstabInfo cinfo, TableDataPath tpath,
                                                int row, int col, boolean rtRef)
   {
      DataRef[] aggrs = rtRef ? cinfo.getRuntimeAggregates() : cinfo.getAggregates();

      if(rtRef && cinfo.getRuntimeAggregates().length == 0 && cinfo.getAggregates().length > 0) {
         aggrs = cinfo.getAggregates();
      }

      DataRef[] rtRowHeaders = cinfo.getPeriodRuntimeRowHeaders();
      DataRef[] rowHeaders = cinfo.getRowHeaders();
      DataRef[] rtColHeaders = cinfo.getRuntimeColHeaders();
      DataRef[] colHeaders = cinfo.getColHeaders();
      // if there are dynamic column binding, the index would be wrong if we use the design
      // time columns. use runtime and the VSCrosstabInfo.copyRuntimeProperties will maintain
      // the settings. (49736)
      boolean rtRows = rtRef || hasDynamic(rowHeaders);
      boolean rtCols = rtRef || hasDynamic(colHeaders);
      DataRef[] rcs = rtRows ? rtRowHeaders : rowHeaders;
      DataRef[] ccs = rtCols ? rtColHeaders : colHeaders;

      // if period comparison column exists, shift the column index since the period comparison
      // dimension doesn't exist in non-runtime row headers.
      if(!rtRows && rtRowHeaders.length > cinfo.getRuntimeRowHeaders().length) {
         col--;
      }

      int rcnt = rcs == null ? 0 : rcs.length;
      int ccnt = ccs == null ? 0 : ccs.length;
      int hrows = cinfo.getHeaderRowCount();
      int hcols = rtRows ? cinfo.getRuntimeHeaderColCountWithPeriod() : cinfo.getHeaderColCount();
      int hcols0 = rtRows ? cinfo.getRuntimeHeaderColCountWithPeriod() : hcols;
      boolean hasAgg = aggrs != null && aggrs.length > 0;

      // Column header cell.
      if(col < hcols0 && tpath != null && tpath.getType() != TableDataPath.DETAIL) {
         return getHeaderCellRef(cinfo, tpath, row, col, hrows, true, rtRows);
      }
      // Row header cell.
      else if(row < hrows && tpath != null && tpath.getType() != TableDataPath.DETAIL) {
         return getHeaderCellRef(cinfo, tpath, row, col, hcols, false, rtCols);
      }
      // Aggregate Cell.
      else if(tpath != null &&
         (tpath.getType() == TableDataPath.SUMMARY ||
            tpath.getType() == TableDataPath.GRAND_TOTAL ||
            tpath.getType() == TableDataPath.DETAIL))
      {
         if(hasAgg) {
            int ai = tpath.getType() == TableDataPath.DETAIL ?
               col % aggrs.length : cinfo.isSummarySideBySide() ?
               (col - hcols) % aggrs.length : (row - hrows) % aggrs.length;
            return ai >= 0 && ai < aggrs.length ? aggrs[ai] : null;
         }
         else {
            if(col < rcnt) {
               return rcs[col];
            }
            else if(col < rcnt + ccnt) {
               return ccs[col - rcnt];
            }
         }
      }

      return null;
   }

   private static boolean hasDynamic(DataRef[] headers) {
      if(headers == null) {
         return false;
      }

      return Arrays.stream(headers).anyMatch(header ->
         header instanceof VSDimensionRef && VSUtil.isDynamicValue(((VSDimensionRef) header).getGroupColumnValue()));
   }

   /**
    * Set header cell data ref.
    */
   private static DataRef getHeaderCellRef(VSCrosstabInfo cinfo, TableDataPath tpath,
      int row, int col, int cnt, boolean isCol, boolean rt)
   {
      boolean cornerHeader = false;

      if(isCol && row < cnt) {
         cornerHeader = true;
      }

      int idx = isCol || cornerHeader ? col : row;
      DataRef[] refs;

      if(tpath.getType() == TableDataPath.GROUP_HEADER || cornerHeader) {
         refs = isCol || cornerHeader ?
            rt ? cinfo.getPeriodRuntimeRowHeaders() : cinfo.getRowHeaders() :
            rt ? cinfo.getRuntimeColHeaders() : cinfo.getColHeaders();
         DataRef dataRef = findDataRef(cinfo, tpath, refs, idx, isCol, cornerHeader);

         if(dataRef != null) {
            return dataRef;
         }
         else {
            refs = rt ? cinfo.getRuntimeAggregates() : cinfo.getAggregates();

            if(refs != null && refs.length != 0) {
               int idx0 = ((isCol || cornerHeader ? row : col) - cnt) % refs.length;
               dataRef = idx0 >=0 && idx0 < refs.length ? refs[idx0] : null;

               if(dataRef == null) {
                  return null;
               }
               else {
                  return dataRef;
               }
            }
         }
      }
      else if(tpath.getType() == TableDataPath.HEADER) {
         refs = rt ? cinfo.getRuntimeAggregates() : cinfo.getAggregates();

         if(refs != null && refs.length != 0) {
            int index = ((isCol ? row : col) - cnt) % refs.length;

            return index >= 0 && index < refs.length ? refs[((isCol ? row : col) - cnt) % refs.length]: null;
         }
      }

      return null;
   }

   private static DataRef findDataRef(VSCrosstabInfo cinfo, TableDataPath tpath,
      DataRef[] refs, int idx, boolean isCol, boolean cornerHeader)
   {
//      if(isCol && cinfo != null && cinfo.isPeriod()) {
//         idx--;
//      }

      if(idx >= 0 && refs != null && refs.length > idx) {
         VSDataRef dataRef = (VSDataRef) refs[idx];
         int refType = dataRef.getRefType();

         if((refType & AbstractDataRef.CUBE_DIMENSION) == AbstractDataRef.CUBE_DIMENSION ||
            cornerHeader)
         {
            return dataRef;
         }
         else if(tpath.getPath().length > 0) {
            String name = dataRef.getFullName();
            String path = tpath.getPath()[tpath.getPath().length - 1];

            if(!name.equals(path) && dataRef instanceof VSDimensionRef &&
               ((VSDimensionRef) dataRef).isDate() &&
               ((VSDimensionRef) dataRef).getDateLevel() == DateRangeRef.NONE_INTERVAL)
            {
               name = dataRef.getName();
            }

            if(name.equals(path)) {
               return dataRef;
            }
            // handle path where duplicat header exists, e.g. header.1 (in crosstab)
            else if(path.startsWith(name)) {
               String suffix = path.substring(name.length());

               try {
                  if(suffix.startsWith(".") && Integer.parseInt(suffix.substring(1)) > 0) {
                     return dataRef;
                  }
               }
               catch(Exception ex) {
                  // ignore
               }
            }
         }
      }

      return null;
   }

   /**
    * Get binding data name.
    */
   public String getColumnName(DataRef dataRef, VSAssembly assembly) {
      String refName = dataRef.getName();

      if(dataRef instanceof VSDimensionRef) {
         DataRef ref0 = ((VSDimensionRef) dataRef).getDataRef();
         refName = ref0 == null ? refName : ref0.getName();
      }
      else if(dataRef instanceof VSAggregateRef) {
         refName = ((VSAggregateRef) dataRef).getFullName();

         if(refName.indexOf("discrete_") == 0) {
            refName = refName.substring(9);
         }

         if(!(assembly instanceof ChartVSAssembly)) {
            int idx1 = refName.indexOf("(");

            if(idx1 >= 0) {
               int idx2 = refName.indexOf("(", idx1 + 1);
               int idx3 = refName.lastIndexOf(")");

               if(idx2 > 0 && idx3 > idx2) {
                  refName = refName.substring(idx1 + 1, idx3);

                  // with 2nd col, e.g.
                  // First(First(Quantity Purchased, Category), Category) =>
                  // First(Quantity Purchased, Category), Category
                  idx2 = refName.indexOf("(", 1);
                  idx3 = refName.lastIndexOf("),");

                  if(idx2 > 0 && idx3 > idx2) {
                     refName = refName.substring(0, idx3 + 1);
                  }
               }
            }
         }
      }
      else if(VSUtil.isVariableValue(refName)) {
         refName = ((VSDataRef) dataRef).getFullName();
      }
      else if(isExpressionValue(refName) && dataRef instanceof VSAggregateRef) {
         DataRef colref = ((VSAggregateRef) dataRef).getDataRef();
         refName = colref == null ? refName : colref.getName();
      }

      if(assembly instanceof TableVSAssembly && dataRef instanceof ColumnRef) {
         DataRef dref = ((ColumnRef) dataRef).getDataRef();
         refName = dref == null ? refName : dref.getName();
      }

      return refName;
   }

   private boolean isExpressionValue(String val) {
      return val != null && val.indexOf("=\"") == 0 &&
         val.lastIndexOf("\"") == (val.length() - 1);
   }

   /**
    * Create selection vs assembly.
    */
   public SelectionVSAssembly createSelectionVSAssembly(Viewsheet vs, int type,
                                                        String dtype, String name,
                                                        List<String> tableNames,
                                                        ColumnSelection columns)
   {
      return createSelectionVSAssembly(vs, type, dtype, name, tableNames, columns, null);
   }

   /**
    * Create selection vs assembly.
    */
   public SelectionVSAssembly createSelectionVSAssembly(Viewsheet vs, int type,
                                                        String dtype, String name,
                                                        List<String> tableNames,
                                                        ColumnSelection columns, String title)
   {
      final SelectionVSAssembly vsassembly;

      if(type == AbstractSheet.SELECTION_LIST_ASSET) {
         SelectionListVSAssembly list = new SelectionListVSAssembly(vs, name);

         vsassembly = list;
         list.setDataRef(columns.getAttribute(0));
         list.setTitleValue(!StringUtils.isEmpty(title) ? title : getTitle(columns.getAttribute(0)));
      }
      else if(type == AbstractSheet.SELECTION_TREE_ASSET) {
         SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(vs, name);

         vsassembly = tree;
         tree.setDataRefs(toArray(columns));
         tree.setTitleValue(!StringUtils.isEmpty(title) ? title : getTitle(columns.getAttribute(0)));
      }
      else {
         TimeSliderVSAssembly slider = new TimeSliderVSAssembly(vs, name);

         vsassembly = slider;
         DataRef ref = columns.getAttribute(0);
         int reftype = ref.getRefType();
         SingleTimeInfo tinfo = new SingleTimeInfo();
         tinfo.setDataRef(ref);

         if((reftype & DataRef.CUBE_DIMENSION) != 0 && !XSchema.isDateType(dtype)) {
            tinfo.setRangeTypeValue(TimeInfo.MEMBER);
         }
         else if(XSchema.isNumericType(dtype)) {
            // let TimeSliderVSAQuery to set the range size from data
            tinfo.setRangeTypeValue(TimeInfo.NUMBER);
         }
         else if(XSchema.TIME.equals(dtype)) {
            tinfo.setRangeTypeValue(TimeInfo.MINUTE_OF_DAY);
         }
         else {
            tinfo.setRangeTypeValue(TimeInfo.MONTH);
         }

         slider.setTimeInfo(tinfo);
         slider.setTitleValue(!StringUtils.isEmpty(title) ? title : getTitle(columns.getAttribute(0)));

         // @damianwysocki, Bug #9543
         // Removed grid, use pixelSize
         // don't use pixel size in container
//         if(cassembly != null) {
//            slider.setPixelSize(null);
//         }
      }

      vsassembly.setTableNames(tableNames);
      return vsassembly;
   }

   /**
    * Get column title.
    */
   private String getTitle(DataRef ref) {
      if((ref.getRefType() & DataRef.CUBE) == 0) {
         return VSUtil.trimEntity(ref.getAttribute(), null);
      }

      ref = DataRefWrapper.getBaseDataRef(ref);
      return ref.toView();
   }

   /**
    * Convert column selection list to an array.
    */
   private DataRef[] toArray(ColumnSelection columns) {
      DataRef[] arr = new DataRef[columns.getAttributeCount()];

      for(int i = 0; i < arr.length; i++) {
         arr[i] = columns.getAttribute(i);
      }

      return arr;
   }

   /**
    * Get dimension name from data ref.
    */
   public VSAssembly createTable(
      RuntimeViewsheet rvs, ViewsheetService viewsheetService, AssetEntry entry, int x, int y)
   {
      Viewsheet viewsheet = rvs.getViewsheet();
      Worksheet ws = viewsheet.getBaseWorksheet();

      if(!VSEventUtil.BASE_WORKSHEET.equals(entry.getProperty("source"))) {
         return null;
      }

      String tableName = entry.getName();

      if(isModelEntry(entry)) {
         tableName = entry.getProperty("table");
      }

      tableName = VSUtil.getTableName(tableName);
      viewsheet.convertToEmbeddedTable(ws, tableName);
      TableAssembly tassembly = (TableAssembly) ws.getAssembly(tableName);

      if(tassembly == null) {
         return null;
      }

      SourceInfo sinfo = new SourceInfo();
      sinfo.setType(SourceInfo.ASSET);
      sinfo.setSource(tassembly.getName());
      ColumnSelection columns = getColumnSelection(tassembly, entry, rvs, viewsheetService);
      // add calculate fields.
      CalculateRef[] calcs = viewsheet.getCalcFields(
               entry.isTable() ? entry.getName() : tassembly.getName());

      if(calcs != null) {
         for(CalculateRef calc : calcs) {
            if(calc.isBaseOnDetail() && !calc.isDcRuntime()) {
               ColumnRef col = new ColumnRef();
               AttributeRef aref = new AttributeRef(null, calc.getName());
               col.setDataRef(aref);
               columns.addAttribute(col);
            }
         }
      }

      columns = VSUtil.getVSColumnSelection(columns);
      final int maxCols =  Util.getOrganizationMaxColumn();

      if(columns.getAttributeCount() > maxCols) {
         while(columns.getAttributeCount() > maxCols) {
            columns.removeAttribute(maxCols);
         }

         CoreTool.addUserMessage(Catalog.getCatalog().getString(
                                    "viewer.viewsheet.newTable.maxCols", maxCols));
      }

      columns = filterPreparedCalcField(columns);

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef col = (ColumnRef) columns.getAttribute(i);
         col.setAlias(VSUtil.trimEntity(col.getAttribute(), null));
      }

      String name = AssetUtil.getNextName(viewsheet, AbstractSheet.TABLE_VIEW_ASSET);
      TableVSAssembly tvassembly = new TableVSAssembly(viewsheet, name);

      tvassembly.setTitleValue(isModelEntry(entry) ?
                                  entry.getName() : tassembly.getName().replace("^_^", "."));
      tvassembly.setSourceInfo(sinfo);
      tvassembly.setColumnSelection(columns);
      Dimension size = calculateSize(tassembly, columns, entry);

      size = new Dimension(size.width, Math.max(size.height, 6 * AssetUtil.defh));
      tvassembly.setPixelSize(size);
      tvassembly.getVSAssemblyInfo().setPixelOffset(new Point(x, y));
      tvassembly.initDefaultFormat();

      return tvassembly;
   }

   /**
    * Check if the entry is from logical model.
    */
   private static boolean isModelEntry(AssetEntry entry) {
      return (XSourceInfo.MODEL + "").equals(entry.getProperty("originType")) ||
              "LOGIC_MODEL".equals(entry.getProperty("sourceType"));
   }

   /**
    * Calculate the size of the table.
    */
   private static Dimension calculateSize(TableAssembly table, ColumnSelection columns,
                                          AssetEntry entry)
   {
      return new Dimension(columns.getAttributeCount() * AssetUtil.defw, 4 * AssetUtil.defh);
   }

   /**
    * Filter out the prepared calcfields from target columnselection.
    */
   private static ColumnSelection filterPreparedCalcField(ColumnSelection columns) {
      if(columns == null || columns.getAttributeCount() == 0) {
         return columns;
      }

      ColumnSelection cols = new ColumnSelection();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef ref = columns.getAttribute(i);

         if(!VSUtil.isPreparedCalcField(ref)) {
            cols.addAttribute(ref);
         }
      }

      return cols;
   }

   /**
    * Get the ColumnSelection.
    */
   private static ColumnSelection getColumnSelection(TableAssembly table,
                                                     AssetEntry entry,
                                                     RuntimeViewsheet rvs,
                                                     ViewsheetService viewsheetService)
   {
      if(!isLogicalModel(entry)) {
         return table.getColumnSelection(true);
      }

      AssetRepository engine = viewsheetService.getAssetRepository();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null || box == null) {
         return new ColumnSelection();
      }

      Principal user = box.getUser();
      List<TableAssembly> list = VSEventUtil.createPseudoAssemblies(engine,
                                                                    vs.getBaseEntry(), user);

      for(TableAssembly tassembly : list) {
         if(tassembly.getName().equals(entry.getName())) {
            return (ColumnSelection) tassembly.getColumnSelection().clone();
         }
      }

      return new ColumnSelection();
   }

   /**
    * Check if the entry is from logical model.
    */
   public static boolean isLogicalModel(AssetEntry entry) {
      if("LOGIC_MODEL".equals(entry.getProperty("sourceType"))) {
         return true;
      }

      String type = entry.getProperty("originType");
      return type != null && Integer.parseInt(type) == XSourceInfo.MODEL;
   }
}
