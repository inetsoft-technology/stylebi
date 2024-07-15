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
package inetsoft.report.composition.execution;

import inetsoft.report.TableDataPath;
import inetsoft.report.TableLens;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.report.lens.DistinctTableLens;
import inetsoft.report.lens.TextSizeLimitTableLens;
import inetsoft.report.script.viewsheet.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.ListInputVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.profile.ProfileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Format;
import java.util.*;

/**
 * InputVSAQuery, the input viewsheet assembly query.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class InputVSAQuery extends VSAQuery {
   /**
    * Create an input viewsheet assembly query.
    * @param box the specified viewsheet sandbox.
    * @param vname the specified viewsheet assembly to be processed.
    */
   public InputVSAQuery(ViewsheetSandbox box, String vname) {
      super(box, vname);
   }

   /**
    * Get the table assembly that contains binding info.
    * @param analysis <tt>true</tt> if is for analysis, <tt>false</tt> for
    * runtime.
    */
   private TableAssembly getTableAssembly(boolean analysis) throws Exception {
      InputVSAssembly assembly = (InputVSAssembly) getAssembly();
      AssetQuerySandbox wbox = getAssetQuerySandbox();

      if(!(assembly instanceof ListInputVSAssembly) || wbox == null) {
         return null;
      }

      ListInputVSAssembly lassembly = (ListInputVSAssembly) assembly;
      int source = lassembly.getSourceType();

      // no binding?
      if(source == ListInputVSAssembly.NONE_SOURCE) {
         return null;
      }
      // embedded list data?
      else if(source == ListInputVSAssembly.EMBEDDED_SOURCE) {
         return null;
      }

      ListBindingInfo binding = lassembly.getListBindingInfo();
      Worksheet ws = getWorksheet();
      String tname = binding == null ? null : binding.getTableName();

      if(tname == null || tname.length() == 0) {
         return null;
      }

      TableAssembly tassembly = getVSTableAssembly(tname);

      if(tassembly == null) {
         throw new RuntimeException("Table doesn't exist: " + tname);
      }

      tassembly = box.getBoundTable(tassembly, vname, isDetail());
      ColumnSelection columns = tassembly.getColumnSelection(false);
      ColumnSelection pubColumns = tassembly.getColumnSelection(true);
      ColumnSelection ncolumns = new ColumnSelection();

      if(binding.getValueColumn() == null) {
         return null;
      }

      String vattrName = binding.getValueColumn().getName();
      String lattrName = binding.getLabelColumn() == null ?
         null : binding.getLabelColumn().getName();
      DataRef vattr = getAttribute(vattrName, columns, pubColumns);
      DataRef lattr = getAttribute(lattrName, columns, pubColumns);
      lattr = Tool.equals(lattr, vattr) ? null : lattr; // same?

      /*
      TableLens table = getTableLens(tassembly);
      int index = vattr == null ? -1 : AssetUtil.findColumn(table, vattr);

      if(index < 0) {
         throw new RuntimeException(Catalog.getCatalog().getString(
            "common.invalidTableColumn", binding.getValueColumn()));
      }
      */

      if(lattr != null) {
         ncolumns.addAttribute(lattr);
      }

      if(vattr != null) {
         ncolumns.addAttribute(vattr);
      }

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);

         if(!ncolumns.containsAttribute(column)) {
            column.setVisible(false);
            ncolumns.addAttribute(column);
         }
      }

      // @by stephenwebster For Bug #10259
      // The default result of a distinct table from AssetQuery is to resort
      // the data which causes the inability to show the distinct list based
      // on its original row ordering.
      // tassembly.setDistinct(true);
      tassembly.setColumnSelection(ncolumns);

      normalizeTable(tassembly);
      ws.addAssembly(tassembly);
      return tassembly;
   }

   /**
    * Get attribute from column selection.
    * @param name attribute name.
    * @param priColumns private column selection.
    * @param pubColumns public column selection.
    */
   private DataRef getAttribute(String name, ColumnSelection priColumns,
                                ColumnSelection pubColumns)
   {
      if(name == null) {
         return null;
      }

      DataRef ref = priColumns.getAttribute(name);

      if(ref == null) {
         ref = pubColumns.getAttribute(name);
      }

      return ref;
   }

   /**
    * Get the data of an input viewsheet assembly.
    * @return the data of the query.
    */
   @Override
   public Object getData() throws Exception {
      InputVSAssembly assembly = (InputVSAssembly) getAssembly();
      AssetQuerySandbox wbox = getAssetQuerySandbox();

      // scalar input viewsheet assembly? fetch default value if any
      if(!(assembly instanceof ListInputVSAssembly)) {
         String tname = assembly.getTableName();
         Object def = null;

         if(wbox != null && tname != null) {
            Worksheet ws = getWorksheet();
            EmbeddedTableAssembly eassembly =
               (EmbeddedTableAssembly) ws.getAssembly(tname);

            if(eassembly != null) {
               DataRef column = assembly.getColumn();
               int row = assembly.getRow();

               if(column != null && row > 0) {
                  XEmbeddedTable edata = eassembly.getEmbeddedData();
                  int col = AssetUtil.findColumn(edata, column);

                  if(col >= 0 && row < edata.getRowCount()) {
                     def = edata.getObject(row, col);
                  }
               }
            }
         }

         return def;
      }

      ListInputVSAssembly lassembly = (ListInputVSAssembly) assembly;
      int source = lassembly.getSourceType();
      ListData data = null;

      // no binding?
      if(source == ListInputVSAssembly.NONE_SOURCE) {
         //do nothing
      }
      // query list data?
      else if(source == ListInputVSAssembly.BOUND_SOURCE) {
         data = getQueryData(lassembly);
      }
      // embedded list data?
      else if(source == ListInputVSAssembly.EMBEDDED_SOURCE) {
         data = getEmbeddedData(lassembly);
      }
      // merge list data?
      else if(source == ListInputVSAssembly.MERGE_SOURCE) {
         data = mergeData(getQueryData(lassembly), getEmbeddedData(lassembly));
      }

      data = data == null ? new ListData() : (ListData) data.clone();
      lassembly.setRListData(data);

      return lassembly.getRListData();
   }

   /**
    * Refresh view if view changed.
    */
   public void refreshView(ListData data) throws Exception {
      // for Feature #26586, add execution breakdown record.
      ProfileUtils.addExecutionBreakDownRecord(getID(),
         ExecutionBreakDownRecord.UI_PROCESSING_CYCLE, args -> {
            VSCompositeFormat fmt = getCellFormat();
            sortListData(getAssembly(), data);
            refreshListData(data, fmt);
         });
   }

   /**
    * Sort the list data with the specified order.
    * @param assembly the assembly
    * @param ldata the specified list data.
    */
   private void sortListData(VSAssembly assembly, ListData ldata) throws Exception {
      if(!(assembly instanceof ListInputVSAssembly)) {
         return;
      }

      ListInputVSAssembly lassembly = (ListInputVSAssembly) assembly;

      if(lassembly.getSortType() != XConstants.SORT_ASC &&
         lassembly.getSortType() != XConstants.SORT_DESC)
      {
         if(lassembly.getSourceType() != ListInputVSAssembly.MERGE_SOURCE) {
            return;
         }

         ListData embeddedData = getEmbeddedData(lassembly);
         ldata.setQueryDataIndex(embeddedData.getLabels().length);

         if(lassembly.isEmbeddedDataDown()) {
            listEmbeddedDataBottom(ldata, ldata.getQueryDataIndex());
         }

         return;
      }

      boolean sortByValue = lassembly.isSortByValue();
      int sortType = lassembly.getSortType();
      Object[] oriLabels = new Object[ldata.getLabels().length];
      System.arraycopy(ldata.getLabels(), 0, oriLabels, 0,
                       ldata.getLabels().length);
      Object[] oriValues = new Object[ldata.getValues().length];
      System.arraycopy(ldata.getValues(), 0, oriValues, 0,
                       ldata.getValues().length);
      Object[] oriFormats = new Object[ldata.getFormats().length];
      System.arraycopy(ldata.getFormats(), 0, oriFormats, 0,
         ldata.getFormats().length);

      sortArray(sortByValue ? ldata.getValues() : ldata.getLabels(), sortType,
                sortByValue);

      Object[] newOrders = sortByValue ? ldata.getValues() : ldata.getLabels();
      Object[] values = sortByValue ? ldata.getLabels() : ldata.getValues();
      Object[] oldOrders = sortByValue ? oriValues : oriLabels;
      Object[] oldValues = sortByValue ? oriLabels : oriValues;
      Object[] formats = ldata.getFormats();

      // create list in order to distinguish same labels
      ArrayList<Integer> distinguish = new ArrayList<>();
      // sort values based on the sorted label
      for(int i = 0; i < newOrders.length && values.length == newOrders.length;
         i++)
      {
         for(int j = 0; j < oldOrders.length; j++) {
            if(distinguish.contains(j) ||
               !Tool.equals(newOrders[i], oldOrders[j]))
            {
               continue;
            }

            values[i] = oldValues[j];

            if(formats.length == newOrders.length) {
               formats[i] = oriFormats[j];
            }

            distinguish.add(j);
            break;
         }
      }
   }

   /**
    * Sort arrays with specified type.
    */
   private void sortArray(Object[] objs, int sortType, boolean sortByValue) {
      final int stype = sortType;
      List<Object> nonSortArr = new ArrayList<>();
      List<Object> sortArr = new ArrayList<>();

      for(int i = 0; i < objs.length; i++) {
         if(objs[i] == null || "".equals(("" + objs[i]).trim())) {
            nonSortArr.add(objs[i]);
         }
         else {
            sortArr.add(objs[i]);
         }
      }

      Object[] sort = sortArr.toArray(new Object[0]);
      final boolean isNumber = !sortByValue && isNumber(sort);

      Arrays.sort(sort, new Comparator() {
         @Override
         public int compare(Object o1, Object o2) {
            int v = 0;

            if(isNumber) {
               o1 = Double.valueOf((String) o1);
               o2 = Double.valueOf((String) o2);
            }

            if(stype == XConstants.SORT_ASC || stype == XConstants.SORT_DESC) {
               v = Tool.compare(o1, o2);
               v = stype == XConstants.SORT_ASC ? v : -v;
            }

            return v;
         }
      });

      Object[] nonSort = nonSortArr.toArray(new Object[0]);

      if(stype == XConstants.SORT_ASC) {
         System.arraycopy(sort, 0, objs, 0, sort.length);
         System.arraycopy(nonSort, 0, objs, sort.length, nonSort.length);
      }
      else {
         System.arraycopy(nonSort, 0, objs, 0, nonSort.length);
         System.arraycopy(sort, 0, objs, nonSort.length, sort.length);
      }
   }

   /**
    * List the embedded Data after the query data.
    */
   private void listEmbeddedDataBottom(ListData ldata, int idx) {
      Object[] labels = ldata.getLabels();
      Object[] valuses = ldata.getValues();

      Object[] embeddedDatas = new Object[idx];
      System.arraycopy(labels, 0, embeddedDatas, 0, idx);
      System.arraycopy(labels, idx, labels, 0, labels.length - idx);
      System.arraycopy(embeddedDatas, 0, labels, labels.length - idx, idx);

      embeddedDatas = new Object[idx];
      System.arraycopy(valuses, 0, embeddedDatas, 0, idx);
      System.arraycopy(valuses, idx, valuses, 0, valuses.length - idx);
      System.arraycopy(embeddedDatas, 0, valuses, valuses.length - idx, idx);
   }

   /**
    * Check if the array contains numbers only.
    */
   private boolean isNumber(Object[] objs) {
      for(int i = 0; i < objs.length; i++) {
         if(objs[i] == null) {
            return false;
         }

         String str = "" + objs[i];

         if(str.indexOf("-") == 0) {
            str = str.substring(1);
         }

         if(!(str.matches("^([0-9]\\d*)$") ||
            str.matches("^([0-9]\\d*)\\.(\\d+)$")))
         {
            return false;
         }
      }

      return true;
   }

   /**
    * Get the cell format.
    * @return the cell format.
    */
   private VSCompositeFormat getCellFormat() {
      InputVSAssembly assembly = (InputVSAssembly) getAssembly();

      if(assembly instanceof CompositeVSAssembly) {
         FormatInfo finfo = assembly.getFormatInfo();
         TableDataPath path = new TableDataPath(-1, TableDataPath.DETAIL);
         return (finfo == null) ? null : finfo.getFormat(path, false);
      }

      return assembly.getVSAssemblyInfo().getFormat();
   }

   /**
    * Refresh the list data.
    * @param data the specified list data.
    * @param fmt the specified viewsheet format.
    */
   private void refreshListData(ListData data, VSCompositeFormat fmt) throws Exception {
      if(data == null) {
         return;
      }

      InputVSAssembly iassembly = (InputVSAssembly) getAssembly();
      boolean rflag = data.isBinding();
      fmt = fmt == null ? new VSCompositeFormat() : fmt;
      Object[] vals = data.getValues();
      String[] labels = new String[vals.length];
      // the cell format for all data is apply the same format, default format
      // or the data path format, so here just add one cell format to reduce
      // xml file size, see bug1249554532182
      VSCompositeFormat[] fmts = new VSCompositeFormat[vals.length];//vals.length];
      ViewsheetScope scope = box.getScope();
      VSAScriptable scriptable = scope.getVSAScriptable(vname);
      Format[] dfmts = data.getDefaultFormats();
      Format dfmt = dfmts != null && dfmts.length > 0 ? dfmts[0] : null;

      for(int i = 0; i < vals.length; i++) {
         Object obj = vals[i];
         SelectionValue sval = refreshFormat(obj, fmt, dfmt, scriptable, rflag);
         labels[i] = sval.getLabel();
         fmts[i] = sval.getFormat();
      }

      if(rflag) {
         data.setLabels(labels);
      }

      data.setFormats(fmts);

      if(scriptable instanceof CompositeVSAScriptable) {
         if(iassembly instanceof SingleInputVSAssembly) {
            ((CompositeVSAScriptable) scriptable)
               .setCellValue(((SingleInputVSAssembly) iassembly).getSelectedObject());
         }
         // checkbox should always be array so script don't need to check for different types
         else if(iassembly instanceof CompositeInputVSAssembly) {
            ((CompositeVSAScriptable) scriptable)
               .setCellValue(((CompositeInputVSAssembly) iassembly).getSelectedObjects());
         }
      }
   }

   /**
    * Get the format of a cell.
    * @param obj the specified cell object.
    * @param vfmt the specified viewsheet format.
    * @param dfmt the specified default viewsheet format.
    * @param rflag <tt>true</tt> if binding, <tt>false</tt> otherwise.
    */
   private SelectionValue refreshFormat(Object obj, VSCompositeFormat vfmt, Format dfmt,
                                        VSAScriptable scriptable, boolean rflag)
      throws Exception
   {
      SelectionValue svalue = new SelectionValue();
      boolean dynamic = isDynamic(vfmt.getUserDefinedFormat());
      vfmt = dynamic ? vfmt.clone() : vfmt;

      if(dynamic) {
         if(scriptable instanceof CompositeVSAScriptable) {
            ((CompositeVSAScriptable) scriptable).setCellValue(obj);
         }

         List<DynamicValue> dvals = vfmt.getDynamicValues();

         for(DynamicValue dval : dvals) {
            box.executeDynamicValue(dval, scriptable);
         }

         if(vfmt != null) {
            vfmt.shrink();
         }
      }

      svalue.setFormat(vfmt);
      Format fmt = TableFormat.getFormat(vfmt.getFormat(),
                                         vfmt.getFormatExtent(), locale);
      fmt = fmt == null ? dfmt : fmt;

      if(rflag) {
         String label = null;

         if(obj == null) {
            label = "";
         }
         else {
            try {
               label = fmt == null ? null : fmt.format(obj);
            }
            catch(Exception ex) {
               LOG.warn("Failed to format label value: " + obj, ex);
            }

            if(label == null) {
               label = Tool.toString(obj);
            }
         }

         svalue.setLabel(label);
      }

      return svalue;
   }

   /**
    * Reset the embedded data.
    * @param initing <tt>true</tt> if initing the viewsheet.
    */
   public void resetEmbeddedData(boolean initing) {
      resetEmbeddedData(initing, (InputVSAssembly) getAssembly());
   }

   /**
    * Reset the embedded data.
    * @param initing <tt>true</tt> if initing the viewsheet.
    */
   public void resetEmbeddedData(boolean initing, InputVSAssembly assembly) {
      Worksheet ws = getWorksheet();
      String name = assembly.getTableName();
      Assembly wsobj = ws == null ? null : ws.getAssembly(name);

      if(!(wsobj instanceof EmbeddedTableAssembly) ||
         wsobj instanceof SnapshotEmbeddedTableAssembly)
      {
         return;
      }

      EmbeddedTableAssembly et = (EmbeddedTableAssembly) wsobj;
      DataRef column = assembly.getColumn();
      int row = assembly.getRow();

      if(et == null || column == null || row <= 0) {
         return;
      }

      XEmbeddedTable edata2 = et.getOriginalEmbeddedData();
      XEmbeddedTable edata = et.getEmbeddedData();
      int col = AssetUtil.findColumn(edata, column);

      if(col >= 0 && row < edata.getRowCount()) {
         edata.setObject(row, col, edata2.getObject(row, col));
      }

      if(!initing) {
         EmbeddedTableVSAQuery.syncData(et, getViewsheet());
      }
   }

   /**
    * Get query data.
    */
   private ListData getQueryData(InputVSAssembly assembly) throws Exception {
      ListInputVSAssembly lassembly = (ListInputVSAssembly) assembly;
      TableAssembly tassembly = getTableAssembly(false);
      ListData data = null;

      if(tassembly != null) {
         TableAssembly base = null;
         ConditionListWrapper conds = null;
         ListInputVSAssemblyInfo info = (ListInputVSAssemblyInfo)
            lassembly.getVSAssemblyInfo();

         // ignore runtime condition list for form input
         if(info.isForm() && tassembly instanceof MirrorTableAssembly) {
            base = ((MirrorTableAssembly) tassembly).getTableAssembly();
            conds = base.getPreRuntimeConditionList();
            base.setPreRuntimeConditionList(null);
         }

         ListBindingInfo binding = lassembly.getListBindingInfo();
         ColumnSelection columns = tassembly.getColumnSelection(false);
         ColumnSelection pubColumns = tassembly.getColumnSelection(true);
         String vattrName = binding.getValueColumn().getName();
         String lattrName = binding.getLabelColumn().getName();
         DataRef vattr = getAttribute(vattrName, columns, pubColumns);
         DataRef lattr = getAttribute(lattrName, columns, pubColumns);
         vattr = vattr == null ? null : columns.getAttribute(vattr.getName());
         lattr = lattr == null ? null : columns.getAttribute(lattr.getName());
         lattr = Tool.equals(lattr, vattr) ? null : lattr;
         TableLens table = getTableLens(tassembly);

         if(table == null) {
            return null;
         }

         // @by stephenwebster, For Bug #10259
         // Apply distinct table lens after getting input assembly result
         // so we can create a distinct list based on the original order of the
         // data.
         table = new DistinctTableLens(table, null, true);

         // roll back runtime condition list, since the base table is shared
         if(base != null) {
            base.setPreRuntimeConditionList(conds);
         }

         int index = vattr == null ? -1 : AssetUtil.findColumn(table, vattr);

         // table changed?
         if(index < 0 || index >= table.getColCount()) {
            /* column may be hidden, don't throw exception
            throw new RuntimeException("Value column not found: " +
               binding.getValueColumn());
            */
            LOG.warn("Value column not found: " + binding.getValueColumn());
            return null;
         }

         int lindex = lattr == null ? -1 : AssetUtil.findColumn(table, lattr);

         if(table instanceof TableLens) {
            table = new TextSizeLimitTableLens(table, Util.getOrganizationMaxCellSize());
         }

         // if label comes from value, we should apply format, otherwise
         // it seems that we needn't apply viewsheet format for label
         boolean rflag = lindex < 0;
         Object[] pairs = VSAQuery.getValueFormatPairs(table, index, true);
         Object[] values = (Object[]) pairs[0];
         Format dfmt = (Format) pairs[1];
         Object[] labels = lindex < 0 ? values :
            // if label binding another data ref, use default format to format
            // labels, because in refreshView will not format the label
            Util.getXTableLabels(table, lindex);
         data = new ListData();
         data.setDataType(Tool.getDataType(table.getColType(index)));
         data.setValues(values);

         if(dfmt != null) {
            data.setDefaultFormats(new Format[] {dfmt});
         }

         String[] labels2 = new String[labels.length];

         for(int i = 0; i < labels2.length; i++) {
            labels2[i] = Tool.toString(labels[i]);
         }

         data.setLabels(labels2);
         data.setBinding(rflag);
      }

      return data;
   }

   /**
    * Get embedded data.
    */
   private ListData getEmbeddedData(InputVSAssembly assembly) {
      ListInputVSAssembly lassembly = (ListInputVSAssembly) assembly;
      ListData data = lassembly.getListData();

      if(data != null) {
         String[] labels = data.getLabels();
         Object[] values = data.getValues();
         data.setBinding(Tool.equals(labels, values));
      }

      return data;
   }

   /**
    * Merge data.
    */
   private ListData mergeData(ListData qdata, ListData edata) {
      if(qdata == null || edata == null) {
         return qdata != null ? qdata : edata;
      }

      ListData data = new ListData();
      int labelSize = qdata.getLabels().length + edata.getLabels().length;
      int valueSize = qdata.getValues().length + edata.getValues().length;
      String[] labels = new String[labelSize];
      Object[] values = new Object[valueSize];

      System.arraycopy(edata.getLabels(), 0, labels,
                       0, edata.getLabels().length);
      System.arraycopy(edata.getValues(), 0, values,
                       0, edata.getValues().length);
      System.arraycopy(qdata.getLabels(), 0, labels,
                       edata.getLabels().length, qdata.getLabels().length);
      System.arraycopy(qdata.getValues(), 0, values,
                       edata.getValues().length, qdata.getValues().length);
      data.setLabels(labels);
      data.setValues(values);
      data.setBinding(qdata.isBinding() && edata.isBinding());
      data.setQueryDataIndex(edata.getLabels().length);
      data.setDefaultFormats(qdata.getDefaultFormats());

      return data;
   }

   private static final Logger LOG = LoggerFactory.getLogger(InputVSAQuery.class);
}
