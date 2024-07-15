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

import inetsoft.report.*;
import inetsoft.report.filter.*;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.*;
import inetsoft.report.lens.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.lang.reflect.Method;
import java.text.Format;
import java.util.List;
import java.util.*;

/**
 * TableVSAQuery, the table viewsheet assembly query.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class TableVSAQuery extends DataVSAQuery {
   /**
    * Create a table viewsheet assembly query.
    * @param box the specified viewsheet sandbox.
    * @param table the specified table to be processed.
    * @param detail <tt>true</tt> if show detail, <tt>false</tt> otherwise.
    */
   public TableVSAQuery(ViewsheetSandbox box, String table, boolean detail) {
      super(box, table, detail);
   }

   /**
    * Create a table viewsheet assembly query.
    * @param box the specified viewsheet sandbox.
    * @param table the specified table to be processed.
    * @param detail <tt>true</tt> if show detail, <tt>false</tt> otherwise.
    * @param valueInfo the list data need to be calculated.
    */
   public TableVSAQuery(ViewsheetSandbox box, String table, boolean detail,
                        ListValueInfo valueInfo)
   {
      this(box, table, detail);
      this.valueInfo = valueInfo;
   }

   /**
    * Get the default column selection.
    * @return the default column selection.
    */
   @Override
   public ColumnSelection getDefaultColumnSelection() throws Exception {
      TableVSAssembly tv = (TableVSAssembly) getAssembly();

      if(tv == null) {
         return new ColumnSelection();
      }

      SourceInfo source = tv.getSourceInfo();

      if(source == null || source.isEmpty()) {
         return new ColumnSelection();
      }

      if(source.getType() != SourceInfo.ASSET && source.getType() != SourceInfo.VS_ASSEMBLY) {
         throw new RuntimeException("Unsupported source found: " + source);
      }

      TableAssembly ta;

      if(source.getType() == SourceInfo.VS_ASSEMBLY) {
         ta = createAssemblyTable(source.getSource());
      }
      else {
         ta = getTableAssembly0(false);
      }

      return ta == null ? new ColumnSelection() : ta.getColumnSelection(true);
   }

   /**
    * Get the base table assembly.
    * @param original <tt>true</tt> if original, <tt>false</tt> otherwise.
    * @return the base table assembly.
    */
   private TableAssembly getTableAssembly0(boolean original) throws Exception {
      return getTableAssembly0(original, false);
   }

   /**
    * Get the base table assembly.
    * @param original <tt>true</tt> if original, <tt>false</tt> otherwise.
    * @param temp if this table is for temporary usage, so need not add to ws.
    * @return the base table assembly.
    */
   private TableAssembly getTableAssembly0(boolean original, boolean temp) throws Exception {
      TableVSAssembly tv = (TableVSAssembly) getAssembly();

      if(tv == null) {
         return null;
      }

      String name = tv.getTableName();
      Worksheet ws = getWorksheet();
      TableAssembly table = null;

      if(original) {
         table = ws == null ? null : getAssembly(name);

         if(table != null) {
            table = box.getBoundTable(table, vname, original);

            if(!temp) {
               ws.addAssembly(table);
            }
         }
      }
      else if(ws != null) {
         table = getVSTableAssembly(name);

         if(table != null) {
            table = box.getBoundTable(table, vname, original);
            table.setProperty("post.sort", "true");
            ws.addAssembly(table);
         }
      }

      if(table != null) {
         table.setMaxRows(((TableVSAssemblyInfo) tv.getInfo()).getMaxRows());
      }

      normalizeTable(table);
      return table;
   }

   /**
    * Get the table assembly that contains binding info.
    * @param analysis <tt>true</tt> if is for analysis, <tt>false</tt> for
    * runtime.
    */
   protected TableAssembly getTableAssembly(boolean analysis) throws Exception {
      TableVSAssembly tv = (TableVSAssembly) getAssembly();

      if(tv == null) {
         return null;
      }

      SourceInfo source = tv.getSourceInfo();

      if(source == null || source.isEmpty()) {
         return null;
      }

      if(source.getType() != SourceInfo.ASSET && source.getType() != SourceInfo.VS_ASSEMBLY) {
         throw new RuntimeException("Unsupported source found: " + source);
      }

      boolean summaryTable = isAggregateTable();
      TableAssembly ta;

      if(source.getType() == SourceInfo.VS_ASSEMBLY) {
         ta = createAssemblyTable(source.getSource());
      }
      else {
         ta = getTableAssembly0(isDetail());
      }

      if(summaryTable && isDetail() && ta instanceof MirrorTableAssembly) {
         // if use mv to retrieve detail data, just use it
         if(ta.getRuntimeMV() == null || !box.isMVEnabled(true)) {
            ta = ((MirrorTableAssembly) ta).getTableAssembly();

            if(ta != null) {
               ta = (TableAssembly) ta.clone();
            }
         }
      }

      if(ta == null) {
         return null;
      }

      ConditionList conds = !isDetail() ? null : tv.getDetailConditionList();
      tv.setSummaryTable(summaryTable);
      ColumnSelection vcolumns = tv.getColumnSelection();
      ColumnSelection columns = ta.getColumnSelection(false);
      ColumnSelection ncolumns = new ColumnSelection();

      // only selected column is visible
      for(int i = 0; i < vcolumns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) vcolumns.getAttribute(i);
         // for 10.1 bc, first find from alias
         ColumnRef ncolumn = findColumn(column.getAttribute(), columns);

         if(ncolumn == null) {
            ncolumn = (ColumnRef) columns.getAttribute(column.getAttribute());
         }

         if(ncolumn == null && box != null && box.getViewsheet() != null &&
            box.getViewsheet().getCalcField(tv.getTableName(), column.getName()) != null)
         {
            ncolumn = (ColumnRef) vcolumns.getAttribute(i);
         }

         if(ncolumn != null) {
            ncolumn = ncolumn.clone();
            String alias = column.getAlias();

            if(alias != null && alias.length() > 0) {
               ncolumn.setAlias(alias);
            }

            // @by stephenwebster, For bug1433260574191
            // The alias for the column ref on the highlight condition needs to
            // be in sync with the actual table assembly column ref, otherwise
            // the column will not be found if someone changes a column header
            // on the table assembly.
            if(tv.getInfo() instanceof TableVSAssemblyInfo) {
               fixHighlightAlias(ncolumn,
                  ((TableVSAssemblyInfo) tv.getInfo()).getHighlightAttr());
            }

            ncolumn.setVisible(true);
            ncolumns.addAttribute(ncolumn);
            // validate data type, so viewsheet column's data type
            // will be correct, fix bug1260430744540
            column.setDataType(ncolumn.getDataType());
         }
         else if(!isDetail()) {
            vcolumns.removeAttribute(i);
            i--;
         }
      }

      List<ColumnRef> hidden = VSUtil.getHiddenParameterColumns(columns, tv);

      // add hidden columns
      for(int i = 0; i < hidden.size(); i++) {
         // has been cloned
         if(!ncolumns.containsAttribute(hidden.get(i))) {
            hidden.get(i).setVisible(true);
            ncolumns.addAttribute(hidden.get(i));
         }
      }

      boolean isForm = getAssembly().getInfo() != null &&
            ((TableVSAssemblyInfo) getAssembly().getInfo()).isForm();
      ColumnSelection hiddenCols = isForm ? tv.getHiddenColumns() : new ColumnSelection();

      for(int i = 0; i < hiddenCols.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) hiddenCols.getAttribute(i);
         // for 10.1 bc, first find from alias
         ColumnRef ncolumn = findColumn(column.getAttribute(), columns);

         if(ncolumn == null) {
            ncolumn = (ColumnRef) columns.getAttribute(column.getAttribute());
         }

         if(ncolumn != null) {
            ncolumn = ncolumn.clone();
            ncolumn.setVisible(true);
            ncolumns.addAttribute(ncolumn);
         }
      }

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);
         column = column.clone();

         if(!ncolumns.containsAttribute(column)) {
            column.setVisible(false);
            ncolumns.addAttribute(column);
         }
      }

      ta.setColumnSelection(ncolumns);

      // is detail table? append detail condition and turn off aggregation
      if(conds != null && conds.getSize() > 0) {
         ta.setColumnSelection(columns);
         conds = VSUtil.normalizeConditionList(ncolumns, conds);
         ConditionListWrapper pwrapper = ta.getPreRuntimeConditionList();
         ConditionList preconds = pwrapper == null ? null : pwrapper.getConditionList();
         AggregateInfo ainfo = ta.getAggregateInfo();
         ta.setDistinct(false);

         for(int i = 0; i < conds.getSize(); i += 2) {
            ConditionItem citem = (ConditionItem) conds.getItem(i);
            ColumnRef column = (ColumnRef) citem.getAttribute();
            column = column.clone();
            DataRef ref = column.getDataRef();

            if(ref instanceof DateRangeRef && !columns.containsAttribute(column)) {
               column.setVisible(false);
               columns.addAttribute(column);
            }
         }

         List list = new ArrayList();
         list.add(preconds);
         list.add(conds);
         conds = ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
         ta.setPreRuntimeConditionList(conds);
         ta.setPostRuntimeConditionList(null);
         ta.setAggregateInfo(new AggregateInfo());
         ta.resetColumnSelection();

         // when show detail, we sort the group columns by default
         // for better usability
         SortInfo sinfo = ta.getSortInfo();
         GroupRef[] groups = ainfo.getGroups();

         for(int i = 0; i < groups.length; i++) {
            ColumnRef column = (ColumnRef) groups[i].getDataRef();
            column = VSUtil.getVSColumnRef(column);
            SortRef sort = sinfo.getSort(column);

            if(sort == null) {
               column = (ColumnRef) columns.findAttribute(groups[i]);

               if(column != null) {
                  column = VSUtil.getVSColumnRef(column);
                  sort = new SortRef(column);
                  sort.setOrder(XConstants.SORT_ASC);

                  if(sinfo == null) {
                     sinfo = new SortInfo();
                     sinfo.addSort(sort);
                  }
               }
            }
         }
      }

      if(!analysis && !isDetail()) {
         ChartVSAssembly chart = box.getBrushingChart(vname);
         setSharedCondition(chart, ta);
      }

      // apply sort if not analysis
      if(!analysis) {
         SortInfo sinfo = tv.getSortInfo();

         if(sinfo != null) {
            SortInfo osinfo = ta.getSortInfo();
            SortRef[] sorts = sinfo.getSorts();

            for(int i = 0; i < sorts.length; i++) {
               ColumnRef column = (ColumnRef) columns.getAttribute(sorts[i].getName());

               if(column != null) {
                  sorts[i] = sorts[i].copySortRef(column);
                  osinfo.removeSort(sorts[i]);
               }
            }

            for(int i = sorts.length - 1; i >= 0; i--) {
               osinfo.addSort(0, sorts[i]);
            }

            ta.setSortInfo(osinfo);
         }
      }

      return ta;
   }

   private void fixHighlightAlias(ColumnRef sourceRef, TableHighlightAttr highlight) {
      if(highlight == null) {
         return;
      }

      Enumeration highlightGroups = highlight.getAllHighlights();

      while(highlightGroups.hasMoreElements()) {
         HighlightGroup highlightGroup =
            (HighlightGroup) highlightGroups.nextElement();
         String[] levels = highlightGroup.getLevels();

         for(String level : levels) {
            String[] names = highlightGroup.getNames(level);

            for(String name : names) {
               Highlight texthighlight =
                  highlightGroup.getHighlight(level, name);
               ConditionList conditionlist = texthighlight.getConditionGroup();

               for(int d = 0; d < conditionlist.getSize(); d += 2) {
                  ConditionItem conditionItem =
                     conditionlist.getConditionItem(d);
                  DataRef dataRef = conditionItem.getAttribute();

                  if(dataRef.getAttribute().equals(sourceRef.getAttribute()) &&
                     dataRef instanceof ColumnRef)
                  {
                     ((ColumnRef) dataRef).setAlias(sourceRef.getAlias());
                  }
               }
            }
         }
      }
   }

   /**
    * Get the table.
    * @return the table of the query.
    */
   @Override
   public TableLens getTableLens() throws Exception {
      final ViewsheetSandbox box = this.box;
      box.lockRead();

      try {
         TableAssembly table = getTableAssembly(false);

         if(table == null) {
            return null;
         }

         AggregateInfo fainfo = getAggregateInfo(table);
         TableLens data = getTableLens(table);
         // for a normal table, header should not apply drill info, and if
         // the table assembly in worksheet is a crosstab, the header data
         // path may be not a really normal header path, it may be a crosstab's
         // group header data path, so drill will be applied, here ignore it,
         // but in fact, the whole data path for the table is not same as a
         // really normal table when define hyperlink or highlight, and the
         // solution is to wrap a AsssetTableLens for this situation, but this
         // will be affect efficiency, current now just ignore the drill info
         boolean crosstab = false;
         boolean grouped = false;
         boolean rotated = false;
         boolean calctab = false;
         TableLens base = data;

         if(data == null) {
            return null;
         }

         while(base instanceof TableFilter) {
            if(base instanceof CrossTabFilter) {
               crosstab = true;
               break;
            }

            if(base instanceof GroupedTable || base instanceof TableSummaryFilter) {
               grouped = true;
            }

            if(base instanceof RotatedTableLens) {
               rotated = true;
            }

            if(base instanceof CalcTableLens) {
               calctab = true;
            }

            base = ((TableFilter) base).getTable();
         }

         if(crosstab && data.moreRows(0)) {
            TableDataDescriptor desc = data.getDescriptor();

            if(desc != null) {
               for(int i = 0; i < data.getColCount(); i++) {
                  TableDataPath path = desc.getCellDataPath(0, i);

                  if(path == null) {
                     continue;
                  }

                  XMetaInfo minfo = desc.getXMetaInfo(path);

                  if(minfo != null) {
                     minfo.setXDrillInfo(null);
                  }
               }
            }
         }

         if(crosstab || grouped || rotated || calctab) {
            data = new PlainTable(data);
         }

         syncPath((TableVSAssembly) getAssembly(), fainfo, data);
         int maxRow = Util.getTableOutputMaxrow();

         if(maxRow > 0 && isApplyGlobalMaxRows()) {
            data = new MaxRowsTableLens2(data, maxRow);
         }

         boolean isForm = getAssembly().getInfo() != null &&
            ((TableVSAssemblyInfo) getAssembly().getInfo()).isForm();

         if(isForm) {
            TableVSAssembly tv = (TableVSAssembly) getAssembly();
            ColumnSelection cols = tv.getColumnSelection();
            List headers = new ArrayList();

            for(int i = 0; i < data.getColCount(); i++) {
               ColumnRef column = AssetUtil.findColumn(data, i, cols);

               if(column != null && column.isVisible()) {
                  headers.add(Integer.valueOf(i));
               }
            }

            int[] arr = new int[headers.size()];

            for(int i = 0; i < arr.length; i++) {
               arr[i] = ((Integer) headers.get(i)).intValue();
            }

            return PostProcessor.mapColumn(data, arr);
         }

         TableVSAssembly tableVSAssembly = (TableVSAssembly) getAssembly();

         TableLens result = tableVSAssembly.isShowDetail()
            ? data : getVisibleTable((TableVSAssembly) getAssembly(), data, table);

         if(result.getColCount() > Util.getOrganizationMaxColumn()) {
            return new MaxColsTableLens(result, Util.getOrganizationMaxColumn());
         }

         return result;
      }
      finally {
         box.unlockRead();
      }
   }

   private TableLens getVisibleTable(TableVSAssembly table, TableLens base,
                                     TableAssembly baseTable)
   {
      // if is blank table, just return it
      if(!base.moreRows(0)) {
         return base;
      }

      ColumnSelection cols = table.getColumnSelection();
      String sourceName;

      if(table.getSourceInfo().getType() == SourceInfo.VS_ASSEMBLY &&
         baseTable instanceof MirrorTableAssembly)
      {
         sourceName = ((MirrorTableAssembly) baseTable).getAssemblyName();
      }
      else {
         sourceName = table.getTableName();
      }

      List<Integer> list = new ArrayList<>();

      for(int i = 0; i < base.getColCount(); i++) {
         String cid = base.getColumnIdentifier(i);
         Object cheader = base.getObject(0, i);

         for(int j = 0; j < cols.getAttributeCount(); j++) {
            ColumnRef col = (ColumnRef) cols.getAttribute(j);
            String header = Util.getFieldHeader(col);
            String hname = sourceName + "." + header;
            String refName = col.getAttribute();

            if((hname.equals(cid) || header.equals(cid) || header.equals(cheader)
               || refName.equals(cheader)) && col.isVisible())
            {
               list.add(i);
               break;
            }
         }
      }

      int[] map = new int[list.size()];

      for(int i = 0; i < map.length; i++) {
         map[i] = list.get(i);
      }

      return new ColumnMapFilter(base, map);
   }

   /**
    * Check if apply global max rows.
    */
   protected boolean isApplyGlobalMaxRows() {
      return !isDetail();
   }

   /**
    * Find column from column selection by alias.
    */
   private ColumnRef findColumn(String name, ColumnSelection columns) {
      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);

         if(column != null && name.equals(column.getAlias())) {
            return column;
         }
      }

      return null;
   }

   /**
    * Check if is an aggregate table.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   protected boolean isAggregateTable() throws Exception {
      TableAssembly table = getTableAssembly0(true, true);

      if(table instanceof MirrorTableAssembly) {
         table = ((MirrorTableAssembly) table).getTableAssembly();
      }

      if(table == null) {
         return false;
      }

      AggregateInfo ainfo = table.getAggregateInfo();
      return !ainfo.isEmpty() && !ainfo.isCrosstab();
   }

   /**
    * Get the data of an viewsheet assembly.
    * @return the data of the query.
    */
   public ListData getFormListData() throws Exception {
      int source = valueInfo.getSourceType();
      ListData data = null;

      // no binding?
      if(source == ListInputVSAssembly.NONE_SOURCE) {
         return null;
      }
      // query list data?
      else if(source == ListInputVSAssembly.BOUND_SOURCE) {
         data = getQueryData();
      }
      // embedded list data?
      else if(source == ListInputVSAssembly.EMBEDDED_SOURCE) {
         data = getEmbeddedData();
      }
      // merge list data?
      else if(source == ListInputVSAssembly.MERGE_SOURCE) {
         data = mergeData(getQueryData(), getEmbeddedData());
      }

      return data;
   }

   /**
    * Get query data.
    */
   private ListData getQueryData() throws Exception {
      TableAssembly tassembly = getFormTableAssembly(false);
      ListData data = null;

      if(tassembly != null) {
         ListBindingInfo binding = valueInfo.getListBindingInfo();
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

         int index = vattr == null ? 0 : AssetUtil.findColumn(table, vattr);

         // table changed?
         if(index < 0 || index >= table.getColCount()) {
            throw new RuntimeException("Value column not found: " +
               binding.getValueColumn());
         }

         int lindex = lattr == null ? -1 : AssetUtil.findColumn(table, lattr);

         // if label comes from value, we should apply format, otherwise
         // it seems that we needn't apply viewsheet format for label
         boolean rflag = lindex < 0;
         Object[] pairs = VSAQuery.getValueFormatPairs(table, index, false);
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
   private ListData getEmbeddedData() {
      ListData data = valueInfo.getListData();
      data.setBinding(false);
      return data;
   }

   /**
    * Merge data.
    */
   private ListData mergeData(ListData data1, ListData data2) {
      if(data1 == null || data2 == null) {
         return data1 != null ? data1 : (data2 != null ? data2 : null);
      }

      ListData data = new ListData();
      int labelSize = data1.getLabels().length + data2.getLabels().length;
      int valueSize = data1.getValues().length + data2.getValues().length;
      String[] labels = new String[labelSize];
      Object[] values = new Object[valueSize];

      System.arraycopy(data2.getLabels(), 0, labels,
                       0, data2.getLabels().length);
      System.arraycopy(data2.getValues(), 0, values,
                       0, data2.getValues().length);
      System.arraycopy(data1.getLabels(), 0, labels,
                       data2.getLabels().length, data1.getLabels().length);
      System.arraycopy(data1.getValues(), 0, values,
                       data2.getValues().length, data1.getValues().length);
      data.setLabels(labels);
      data.setValues(values);
      data.setBinding(data1.isBinding());

      return data;
   }

   /**
    * Get the table assembly that contains binding info.
    * @param analysis <tt>true</tt> if is for analysis, <tt>false</tt> for
    * runtime.
    */
   private TableAssembly getFormTableAssembly(boolean analysis) throws Exception {
      AssetQuerySandbox wbox = getAssetQuerySandbox();

      if(valueInfo == null || wbox == null) {
         return null;
      }

      int source = valueInfo.getSourceType();

      // no binding?
      if(source == ListInputVSAssembly.NONE_SOURCE) {
         return null;
      }
      // embedded list data?
      else if(source == ListInputVSAssembly.EMBEDDED_SOURCE) {
         return null;
      }

      ListBindingInfo binding = valueInfo.getListBindingInfo();
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
      String vattrName = binding.getValueColumn().getName();
      String lattrName = binding.getLabelColumn().getName();
      DataRef vattr = getAttribute(vattrName, columns, pubColumns);
      DataRef lattr = getAttribute(lattrName, columns, pubColumns);
      lattr = Tool.equals(lattr, vattr) ? null : lattr; // same?

      if(lattr != null) {
         ncolumns.addAttribute(lattr);
      }

      if(vattr != null) {
         ncolumns.addAttribute(vattr);
         SortInfo sinfo = new SortInfo();
         SortRef sort = new SortRef(vattr);
         sort.setOrder(XConstants.SORT_ASC);
         sinfo.addSort(sort);
         tassembly.setSortInfo(sinfo);
      }

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);

         if(!ncolumns.containsAttribute(column)) {
            column.setVisible(false);
            ncolumns.addAttribute(column);
         }
      }

      tassembly.setDistinct(true);
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
    * Plain table, used to convert any table to a plain table which only
    * have header and detail path.
    */
   private static class PlainTable extends DefaultTableFilter {
      public PlainTable(TableLens table) {
         super(table);
      }

      @Override
      public TableDataDescriptor getDescriptor() {
         if(desc == null) {
            desc = new DefaultTableDataDescriptor2(this);
         }

         return desc;
      }

      @Override
      public int getHeaderRowCount() {
         return 1;
      }

      @Override
      public int getHeaderColCount() {
         return 0;
      }

      @Override
       public int getTrailerRowCount() {
         return 0;
      }

      @Override
      public int getTrailerColCount() {
         return 0;
      }

      private TableDataDescriptor desc;
   }

   private static class DefaultTableDataDescriptor2 extends
      DefaultTableDataDescriptor
   {
      /**
       * Create a default table descriptor.
       * @param table the specified table lens
       */
      public DefaultTableDataDescriptor2(XTable table) {
         super(table);
         this.table = table;

         if(table instanceof PlainTable) {
            base = ((PlainTable) table).getTable();
         }
      }

      /**
       * Get meta info of a specified table data path.
       * @param path the specified table data path
       * @return meta info of the table data path
       */
      @Override
      public XMetaInfo getXMetaInfo(TableDataPath path) {
         if(path != null) {
            if(path.getType() == TableDataPath.HEADER || !table.moreRows(1)) {
               return null;
            }

            String column = path.getPath()[0];
            int row = 1;

            if(columnIndexMap == null) {
               columnIndexMap = new ColumnIndexMap(base, true);
            }

            int col = Util.findColumn(columnIndexMap, column, false);
            TableDataDescriptor desc = base.getDescriptor();
            boolean detail = path.getType() == TableDataPath.DETAIL;

            if(col < 0) {
               return null;
            }

            path = desc.getCellDataPath(row, col);
            TableDataPath path2 = desc.getCellDataPath(base.getRowCount() - 1, col);

            // when using crosstab as data source, and there are multiple header rows, should
            // not use the header row format as the column format (cause problem when crosstab
            // details and header have different types)
            if(detail && (path.getType() == TableDataPath.GROUP_HEADER ||
               path2 != null && path2.getType() == TableDataPath.GROUP_HEADER))
            {
               TableLens crosstab = Util.getCrosstab(base);

               if(crosstab != null && crosstab.getHeaderRowCount() > 1) {
                  return null;
               }
            }

            if(detail) {
               RuntimeCalcTableLens calc = (RuntimeCalcTableLens)
                  Util.getNestedTable(base, RuntimeCalcTableLens.class);

               if(calc != null) {
                  return null;
               }
            }

            XMetaInfo xinfo = desc.getXMetaInfo(path);
            XTable filter = base;

            while(xinfo == null && filter instanceof TableFilter) {
               row = ((TableFilter) filter).getBaseRowIndex(row);
               col = ((TableFilter) filter).getBaseColIndex(col);

               if(row < 0 || col < 0) {
                  break;
               }

               filter = ((TableFilter) filter).getTable();
               desc = filter.getDescriptor();
               path = desc.getCellDataPath(row, col);
               xinfo = desc.getXMetaInfo(path);
            }

            return xinfo;
         }

         return null;
      }

      /**
       * Check if contains format.
       * @return <tt>true</tt> if contains format, <tt>false</tt> otherwise.
       */
      @Override
      public boolean containsFormat() {
         return base.containsFormat();
      }

      /**
       * Check if contains drill.
       * @return <tt>true</tt> if contains drill, <tt>false</tt> otherwise
       */
      @Override
      public boolean containsDrill() {
         return base.containsDrill();
      }

      private TableLens base;
      private XTable table;
      private transient ColumnIndexMap columnIndexMap = null;
   }

   /**
    * Get the first table which contains aggregate info.
    */
   private static AggregateInfo getAggregateInfo(TableAssembly table) {
      if(table == null) {
         return null;
      }

      AggregateInfo ainfo = table.getAggregateInfo();

      if(ainfo != null && !ainfo.isEmpty()) {
         return (AggregateInfo) ainfo.clone();
      }

      table = table instanceof MirrorTableAssembly ?
         ((MirrorTableAssembly) table).getTableAssembly() : null;
      return getAggregateInfo(table);
   }

   /**
    * Sync the table data path.
    */
   private static void syncPath(TableVSAssembly table, AggregateInfo ainfo,
                                TableLens top)
   {
      if(table == null) {
         return;
      }

      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getInfo();

      if(info == null || !info.isPreV113()) {
         return;
      }

      info.clearVersion();

      if(ainfo == null || ainfo.isEmpty()) {
         return;
      }

      if(!ainfo.isCrosstab()) {
         syncGroupPath(info);
      }
      else {
         syncCrossTabPath(info, ainfo, top);
      }
   }

   /**
    * Sync the table data path from grouped table to detail table.
    */
   private static void syncGroupPath(TableVSAssemblyInfo info) {
      FormatInfo finfo = info.getFormatInfo();

      if(finfo != null) {
         syncGroupMap(finfo.getFormatMap());
      }

      TableHyperlinkAttr lattr = info.getHyperlinkAttr();

      if(lattr != null) {
         syncGroupMap(lattr.getHyperlinkMap());
      }

      TableHighlightAttr hattr = info.getHighlightAttr();

      if(hattr != null) {
         syncGroupMap(hattr.getHighlightMap());
      }
   }

   /**
    * Sync the path(s) in the map from grouped to detail.
    */
   private static <V> void syncGroupMap(Map<TableDataPath, V> map) {
      if(map == null || map.size() <= 0) {
         return;
      }

      Set<TableDataPath> keys = map.keySet();
      TableDataPath[] paths = new TableDataPath[keys.size()];
      keys.toArray(paths);

      for(TableDataPath path : paths) {
         //fix all the table cell data path. Header and detail need not,
         //they are valid cells
         if(path.getType() != TableDataPath.SUMMARY &&
            path.getType() != TableDataPath.GROUP_HEADER &&
            path.getType() != TableDataPath.GRAND_TOTAL)
         {
            continue;
         }

         TableDataPath npath = new TableDataPath(-1, TableDataPath.DETAIL,
            path.getDataType(), path.getPath());
         V obj = map.get(path);
         map.remove(path);
         map.put(npath, obj);
      }
   }

   /**
    * Sync the data path from crosstab to detail.
    */
   private static void syncCrossTabPath(TableVSAssemblyInfo info,
                                        AggregateInfo ainfo, TableLens top)
   {
      FormatInfo finfo = info.getFormatInfo();

      if(finfo != null) {
         syncCrossTabMap(finfo.getFormatMap(), ainfo, top);
      }

      TableHyperlinkAttr lattr = info.getHyperlinkAttr();

      if(lattr != null) {
         if(syncCrossTabMap(lattr.getHyperlinkMap(), ainfo, top)) {
            LOG.warn("From version 11.3, for a viewsheet table" +
               " binding with worksheet crosstab table, its data path is" +
               " changed, the hyperlink definitation cannot be converted," +
               " please redefine it.");
         }
      }

      TableHighlightAttr hattr = info.getHighlightAttr();

      if(hattr != null) {
         syncCrossTabMap(hattr.getHighlightMap(), ainfo, top);
         syncCrosstabHighlight(hattr, top);
      }
   }

   /**
    * Sync highlight from defined in crosstab to normal table highlight.
    */
   private static void syncCrosstabHighlight(TableHighlightAttr hattr,
                                             TableLens top)
   {
      if(hattr == null || hattr.isNull()) {
         return;
      }

      Map<String, List<String>> cmap = new HashMap<>();
      CrossTabFilter crosstab = createHeaderColMap(top, cmap);

      if(crosstab == null) {
         return;
      }

      String cheader = crosstab.getColHeader(0);
      String dheader = crosstab.getDataHeader(0);
      Enumeration paths = hattr.getAllDataPaths();

      while(paths.hasMoreElements()) {
         TableDataPath path = (TableDataPath) paths.nextElement();
         String[] tpaths = path.getPath();

         if(tpaths == null || tpaths.length != 1) {
            continue;
         }

         String colheader = tpaths[0];
         boolean header = path.getType() == TableDataPath.HEADER;
         HighlightGroup highlight = hattr.getHighlight(path);
         String[] levels = highlight.getLevels();

         for(int i = 0; i < levels.length; i++) {
            String[] names = highlight.getNames(levels[i]);

            OUTER:
            for(int j = 0; j < names.length; j++) {
               Highlight h = highlight.getHighlight(levels[i], names[j]);
               ConditionList conds = h.getConditionGroup();

               if(conds == null || conds.isEmpty()) {
                  continue;
               }

               // clone it
               conds = (ConditionList) conds.clone();

               // add selection column as well
               for(int k = conds.getSize() - 1; k >= 0; k -= 2) {
                  ConditionItem citem = conds.getConditionItem(k);
                  XCondition xcond = citem.getXCondition();

                  if(!(xcond instanceof Condition)) {
                     continue;
                  }

                  Condition cond = (Condition) xcond;

                  if(cond.getDataRefValues().length > 0) {
                     LOG.warn("From version 11.3, for a" +
                        " viewsheet table binding with worksheet crosstab" +
                        " table, its data path is changed, the highlight '" +
                        names[j] + "' with field condition cannot be" +
                        " converted, please redefine it.");
                     continue OUTER;
                  }

                  DataRef field = citem.getAttribute();
                  String name = field.getName();
                  List<String> headers = cmap.get(name);

                  // valid
                  if(headers == null) {
                     continue;
                  }

                  // for header, we created highlight for each header cell,
                  // so here just replace the field directly
                  // for data field, if use data column as condition, just
                  // replace directly
                  if(header || dheader.equals(name)) {
                     citem.setAttribute(new ColumnRef(
                        new AttributeRef(field.getEntity(), colheader)));
                     continue;
                  }

                  // data header use column header as condition, cannot
                  // convert
                  LOG.warn("From version 11.3, for a viewsheet" +
                     " table binding with worksheet crosstab table, its data" +
                     " path is changed, for highlight '" + names[j] + "'" +
                     " cannot be converted, please redefine it.");
                  continue OUTER;
               }

               h.setConditionGroup(conds);
            }
         }
      }
   }

   /**
    * Create column map.
    */
   private static CrossTabFilter createHeaderColMap(TableLens top,
      Map<String, List<String>> cmap)
   {
      CrossTabFilter crosstab = null;
      TableLens temp = top;

      while(temp != null) {
         if(temp instanceof CrossTabFilter) {
            crosstab = (CrossTabFilter) temp;
            break;
         }

         temp = temp instanceof TableFilter ?
            ((TableFilter) temp).getTable() : null;
      }

      if(crosstab == null) {
         return null;
      }

      String cheader = crosstab.getColHeader(0);
      String dheader = crosstab.getDataHeader(0);

      if(cheader == null || dheader == null) {
         return null;
      }

      List<String> headers = new ArrayList<>();
      int headercolcount = crosstab.getHeaderColCount();

      for(int c = 0; c < top.getColCount(); c++) {
         String header = Util.getHeader(top, c).toString();
         int bc = TableTool.getBaseColIndex(top, crosstab, c);

         if(bc < headercolcount) {
            continue;
         }

         headers.add(header);
      }

      // crosstab cheaderand dheader now should match to normal headers
      cmap.put(cheader, headers);
      cmap.put(dheader, headers);
      return crosstab;
   }

   /**
    * Sync the path in the map from crosstab to detail.
    */
   private static <V> boolean syncCrossTabMap(Map<TableDataPath, V> map, AggregateInfo ainfo, TableLens top) {
      if(map == null || map.size() <= 0) {
         return false;
      }

      Set<TableDataPath> keys = map.keySet();
      TableDataPath[] paths = new TableDataPath[keys.size()];
      keys.toArray(paths);
      Method cloneMethod = null;

      for(TableDataPath path : paths) {
         TableDataPath[] npaths = convertPath(path, ainfo, top);

         if(npaths == null || npaths.length <= 0) {
            continue;
         }

         V obj = map.get(path);
         map.remove(path);

         for(TableDataPath npath : npaths) {
            V nobj = obj;

            if(obj instanceof Cloneable) {
               if(cloneMethod == null) {
                  try {
                     cloneMethod = obj.getClass().getMethod("clone",
                                                            new Class[0]);
                     cloneMethod.setAccessible(true);
                  }
                  catch(Exception ex) {
                     // ignore it
                  }
               }

               if(cloneMethod != null) {
                  try {
                     nobj = (V) cloneMethod.invoke(obj, new Object[0]);
                  }
                  catch(Exception ex) {
                     // ignore it
                  }
               }
            }

            map.put(npath, nobj);
         }
      }

      return true;
   }

   /**
    * Convert a crosstab path to normal table path(s), because worksheet
    * crosstab can only have one column header and one aggregate, so the
    * convertion is possible.
    */
   private static TableDataPath[] convertPath(TableDataPath path,
                                              AggregateInfo ainfo,
                                              TableLens top)
   {
      List<String> names = new ArrayList<>();

      for(int i = 0; i < ainfo.getGroupCount(); i++) {
         GroupRef ref = ainfo.getGroup(i);
         names.add(ref.getName());
      }

      // 1: cross cell?
      // for worksheet crosstab, there will not summary header(s),
      // so it is safe for this checking
      if(path.getType() == TableDataPath.HEADER) {
         return convertCrossCellPath(path, names, top);
      }
      // 2: summary cell
      else if(path.getType() == TableDataPath.SUMMARY) {
         return convertSummaryCellPath(path, names, top);
      }
      // 3: group header
      else if(path.getType() == TableDataPath.GROUP_HEADER) {
         return convertGroupHeaderCellPath(path, names, top);
      }

      return null;
   }

   /**
    * Convert the cells cross the row header and column header.
    */
   private static TableDataPath[] convertCrossCellPath(TableDataPath dpath, List<String> names,
                                                       TableLens top)
   {
      Point loc = TableTool.getVSCalcCellLocation(dpath);

      if(loc == null) {
         return null;
      }

      try {
         int col = loc.x;
         String str = names.get(col + 1);

         for(int c = 0; c < top.getColCount(); c++) {
            String header = Util.getHeader(top, c).toString();

            if(sameName(header, str)) {
               str = header;
            }
         }

         TableDataPath hpath = new TableDataPath(-1, TableDataPath.HEADER,
             dpath.getDataType(), new String[] {str});
         return new TableDataPath[] {hpath};
      }
      catch(Exception ex) {
         // ignore it
      }

      return null;
   }

   /**
    * Convert crosstab summary cell.
    */
   private static TableDataPath[] convertSummaryCellPath(TableDataPath path,
                                                         List<String> names,
                                                         TableLens top)
   {
      return createColPath(path, names, top, 1);
   }

   /**
    * Convert crosstab header cell path.
    */
   private static TableDataPath[] convertGroupHeaderCellPath(TableDataPath path,
                                                             List<String> names,
                                                             TableLens top)
   {
      String[] paths = path.getPath();

      // invalid
      if(paths.length <= 0) {
         return null;
      }

      String header = paths[paths.length - 1];
      String name = names.get(0);
      boolean rowheader = !sameName(name, header);

      if(rowheader) {
         TableDataPath dpath = new TableDataPath(-1, TableDataPath.DETAIL,
             path.getDataType(), new String[] {header});
         return new TableDataPath[] {dpath};
      }

      return createColPath(path, names, top, 0);
   }

   /**
    * Create col cell path(s).
    */
   private static TableDataPath[] createColPath(TableDataPath path,
                                                List<String> names,
                                                TableLens top, int row)
   {
      if(!top.moreRows(row)) {
         return null;
      }

      List<TableDataPath> paths = new ArrayList<>();

      OUTER:
      for(int c = 0; c < top.getColCount(); c++) {
         String header = Util.getHeader(top, c).toString();

         for(int j = 1; j < names.size(); j++) {
            if(sameName(names.get(j), header)) {
               continue OUTER;
            }
         }

         paths.add(top.getDescriptor().getCellDataPath(row, c));
      }

      return paths.toArray(new TableDataPath[0]);
   }

   /**
    * Check same name.
    */
   private static boolean sameName(String name1, String name2) {
      if(name1 == null || name2 == null) {
         return false;
      }

      if(name1.equals(name2)) {
         return true;
      }

      int idx1 = name1.lastIndexOf(".");

      if(idx1 >= 0) {
         name1 = name1.substring(idx1 + 1);

         if(name1.equals(name2)) {
            return true;
         }
      }

      int idx2 = name2.lastIndexOf(".");

      if(idx2 >= 0) {
         name2 = name2.substring(idx2 + 1);

         if(name1.equals(name2)) {
            return true;
         }
      }

      return false;
   }

   private ListValueInfo valueInfo = null;
   private static final Logger LOG =
      LoggerFactory.getLogger(TableVSAQuery.class);
}
