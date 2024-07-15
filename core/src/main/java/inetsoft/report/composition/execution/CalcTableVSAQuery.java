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
package inetsoft.report.composition.execution;

import inetsoft.report.*;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.report.internal.binding.TopNInfo;
import inetsoft.report.internal.table.CalcAttr;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.lens.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CalcTableVSAQuery, the formula table viewsheet assembly query.
 *
 * @version 11.3
 * @author InetSoft Technology Corp
 */
public class CalcTableVSAQuery extends DataVSAQuery {
   /**
    * Create a formula table viewsheet assembly query.
    * @param box the specified viewsheet sandbox.
    * @param table the specified table to be processed.
    * @param detail <tt>true</tt> if show detail, <tt>false</tt> otherwise.
    */
   public CalcTableVSAQuery(ViewsheetSandbox box, String table, boolean detail) {
      super(box, table, detail);
   }

   /**
    * Get the table.
    * @return the table of the query.
    */
   @Override
   public TableLens getTableLens() throws Exception {
      final long startTime = System.nanoTime();
      final ViewsheetSandbox box = this.box;

      box.lockWrite();

      try {
         Viewsheet vs = getViewsheet();
         CalcTableVSAssembly cassembly = (CalcTableVSAssembly) getAssembly();
         //clone it, so we will not modify original assembly
         CalcTableVSAssembly cassemblyCopy = (CalcTableVSAssembly) cassembly.clone();
         CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) cassembly.getInfo();
         // @by ChrisSpagnoli feature1414607346853 2014-10-27
         // createCrosstabAssemblies() can now return multiple crosstabs.
         List<CrosstabVSAssembly> crosstabs = new ArrayList<>();
         // @by ChrisSpagnoli feature1414607346853 2014-10-27
         // If createCrosstabAssemblies() returns multiple crosstabs, it also
         // returns the multiple sub-assemblys which were used to contruct
         // those crosstabs, as these are needed to later process the data
         // returned from each.
         List<CalcTableVSAssembly> cassemblys = new ArrayList<>();
         // @by ChrisSpagnoli feature1414607346853 2014-11-19
         // createCrosstabAssemblies() needs to return the number of columns
         // in the "header" for the rejoin later.
         AtomicInteger headerCols = new AtomicInteger();

         if(!isDetail()) {
            try {
               crosstabs = createCrosstabAssemblies(cassemblyCopy, cassemblys, headerCols);
               LOG.debug("getTableLens() crosstabs.size(): " +
                  (crosstabs == null ? 0 : crosstabs.size()) + " headerCols: " + headerCols.get());

               if(crosstabs == null) {
                  removeTempAssembly(cassembly.getName());
               }
            }
            catch(Exception ex) {
               removeTempAssembly(cassembly.getName());
            }
         }

         // @by ChrisSpagnoli feature1414607346853 2014-10-27
         // Multiple crosstabs return multiple sets of data.
         List<TableLens> datas = new ArrayList<>();
         int maxRow = Util.getTableOutputMaxrow();

         LOGCALCCROSSTAB.debug("Use " +
                     ((crosstabs != null && crosstabs.size() > 0) ?
                      crosstabs.size() + " crosstab(s)" : "detail table") +
                     " to support formula table \"" + cassembly.getName() + "\".");

         if(crosstabs == null || crosstabs.size() == 0) {
            TableAssembly table = getTableAssembly();

            if(table == null) {
               VSLayoutTool.createCalcLens(cassembly, null, box.getVariableTable(), false);
               CalcTableLens clens = (CalcTableLens) cassembly.getBaseTable();
               TableLayout layout = cassembly.getTableLayout();

               if(hasFormulaBinding(layout)) {
                  cassembly.setScriptTable(null);
                  cassembly.setScriptEnv(box.getScope().getScriptEnv());
                  clens.setFillBlankWithZero(info.isFillBlankWithZero());
                  return clens.process();
               }

               return clens;
            }

            datas.add(getTableLens(table));
            // @by yanie: bug1418871615213
            // if crosstabs is null or 0-sized, use original calctable
            cassemblys.clear();
            cassemblys.add(cassembly);
         }
         else {
            cassembly.setTable(null);

            // @by ChrisSpagnoli feature1414607346853 2014-10-27
            // If there are multiple crosstabs, evaluate each and store the
            // resulting data for further processing.
            for(int ct = 0; ct < crosstabs.size(); ct++) {
               CrosstabVSAssembly crosstabChild = crosstabs.get(ct);
               String cname = crosstabChild.getName();

               try {
                  CrosstabVSAQuery cquery = new CrosstabVSAQuery(box, cname, false, false);
                  datas.add(cquery.getTableLens(false));
               }
               finally {
                  vs.removeAssembly(crosstabChild.getName(), false);
               }
            }
         }

         if(datas.size() == 0 || datas.contains(null)) {
            VSLayoutTool.createCalcLens(cassembly, null, box.getVariableTable(), false);
            return cassembly.getBaseTable();
         }

         if(isDetail()) {
            return datas.get(0);
         }

         // really create calc expression
         if(datas.size() > 0) {
            for(int i = 0; i < datas.size(); i++) {
               CalcTableVSAssembly cassemblyChild = cassemblys.get(i);
               VSLayoutTool.createCalcLens(cassemblys.get(i), null, box.getVariableTable(),
                                           (crosstabs != null && crosstabs.size() > 0));
               // copy back
               cassemblyChild.setTable(cassemblyChild.getBaseTable());
            }
         }

         try {
            // @by ChrisSpagnoli feature1414607346853 2014-10-27
            // Process the resulting data for each crosstab fully before
            // combining them back together.
            RuntimeCalcTableLens rlensJoined = null;
            LOG.debug("getTableLens() datas.size(): " +
                           datas.size() + " cassemblys.size(): " + cassemblys.size());

            for(int i = 0; i < datas.size(); i++) {
               TableLens dataChild = datas.get(i);
               CalcTableVSAssembly cassemblyChild = cassemblys.get(i);
               cassemblyChild.setScriptTable(dataChild);
               cassemblyChild.setScriptEnv(box.getScope().getScriptEnv());
               CalcTableLens clens = (CalcTableLens) cassemblyChild.getBaseTable();
               clens.setScriptTable(dataChild);
               clens.setHeaderRowCount(info.getHeaderRowCount());
               clens.setHeaderColCount(info.getHeaderColCount());
               clens.setTrailerRowCount(info.getTrailerRowCount());
               clens.setTrailerColCount(info.getTrailerColCount());
               clens.setProperty(XTable.REPORT_NAME, box.getID());
               clens.setProperty(XTable.REPORT_TYPE,
                                 ExecutionBreakDownRecord.OBJECT_TYPE_VIEWSHEET);
               clens.setFillBlankWithZero(info.isFillBlankWithZero());

               TableLayout layout = cassemblyCopy.getTableLayout();

               // 2. process CalcTableLens to generate RuntimeCalcTableLens
               RuntimeCalcTableLens rlens = clens.process();
               ColumnIndexMap columnIndexMap = new ColumnIndexMap(dataChild, true);

               for(int r0 = 0; r0 < layout.getRowCount(false); r0++) {
                  for(int c0 = 0; c0 < layout.getColCount(); c0++) {
                     CellBindingInfo cell = layout.getCellInfo(r0, c0);
                     TableCellBinding binding = ((TableLayout.TableCellBindingInfo) cell).getCellBinding();

                     if(binding != null && binding.getType() == CellBinding.BIND_COLUMN) {
                        String header = binding.getValue();
                        final int col = Util.findColumn(columnIndexMap, header);
                        final Class<?> type = col >= 0 ? dataChild.getColType(col) : null;
                        TableDataPath colPath = new TableDataPath(-1, TableDataPath.DETAIL,
                                  Util.getDataType(type), new String[]{ header });
                        XMetaInfo minfo = dataChild.getDescriptor().getXMetaInfo(colPath);

                        if(minfo != null) {
                           minfo = minfo.clone();

                           // if aggregated, clear default format if data type changed
                           if(binding.getFormula() != null) {
                              String formula = binding.getFormula();
                              AggregateFormula aformula = AggregateFormula.getFormula(formula);

                              if("true".equals(minfo.getProperty("autoCreatedFormat")) ||
                                 aformula != null && aformula.getDataType() != null && type != null
                                 && !isSameType(aformula.getDataType(), Tool.getDataType(type)) ||
                                 isChangeMeaningFormula(aformula))
                              {
                                 minfo.setXFormatInfo(null);
                              }
                           }
                           else if(binding.getDateOption() > 0) {
                              XFormatInfo finfo = rlens.createXFormatInfo(r0, c0);
                              finfo = finfo == null ? new XFormatInfo() : finfo;
                              minfo.setXFormatInfo(finfo);
                           }

                           rlens.setXMetaInfo(clens.getDescriptor().getCellDataPath(r0, c0), minfo);
                        }
                     }
                  }
               }

               // @by ChrisSpagnoli feature1414607346853 2014-10-27
               // Combine the fully processed crosstab data back together.
               if(rlensJoined == null) {
                  rlensJoined = rlens;

                  // If there will be multiple crosstabs combined, put original
                  // layout into rlensJoined, replacing the layout fragment.
                  if(datas.size() > 0) {
                     rlensJoined.getCalcTableLens().getElement().setTableLayout(layout);
                  }
               }
               else {
                  // Copy over the elements from the second+ child crosstabs
                  mergeCrosstabs(rlensJoined, rlens, i, headerCols.get());
               }
            }

            LOG.debug("getTableLens() rlensJoined:" +
                         (rlensJoined != null ? rlensJoined.getRowCount() : 0) +
                         " x " + (rlensJoined != null ? rlensJoined.getColCount() : 0));

            TableLens returnLens = maxRow > 0
               ? new MaxRowsTableLens2(rlensJoined, maxRow) : rlensJoined;
            final long endTime = System.nanoTime();
            final long elapasedTime = (endTime - startTime) / 1000000L;
            LOG.debug("getTableLens() elapsed:" + elapasedTime + "ms.");

            if(returnLens.getColCount() > Util.getOrganizationMaxColumn()) {
               Tool.addUserMessage(DataVSAQuery.getColumnLimitNotification());

               return new MaxColsTableLens(returnLens, Util.getOrganizationMaxColumn());
            }

            return returnLens;
         }
         catch(Exception e) {
            LOG.error("Failed to create calc table: " + e, e);
         }

         return null;
      }
      finally {
         box.unlockWrite();
      }
   }

   private boolean isChangeMeaningFormula(AggregateFormula formula) {
      if(formula == null) {
         return false;
      }

      AggregateFormula[] formulas = new AggregateFormula[] { AggregateFormula.COUNT_ALL,
         AggregateFormula.COUNT_DISTINCT, AggregateFormula.PRODUCT, AggregateFormula.CONCAT };

      for(int i = 0; i < formulas.length; i++) {
         if(Tool.equals(formula.getFormulaName(), formulas[i].getFormulaName())) {
            return true;
         }
      }

      return false;
   }

   private boolean isSameType(String otype, String ntype) {
      if(XSchema.isNumericType(otype) && XSchema.isNumericType(ntype)) {
         return true;
      }

      return !Objects.equals(otype, ntype);
   }

   // @by ChrisSpagnoli feature1414607346853 2014-10-27
   /**
    * Merge the multiple resulting crosstables into a single crosstable.
    * Assumes that all crosstables will have the same number of rows.
    */
   private void mergeCrosstabs(RuntimeCalcTableLens rlensJoined,
                               RuntimeCalcTableLens rlens, int i, int headerCols)
   {
      final int origCols = rlensJoined.getColCount();
      rlens.moreRows(Integer.MAX_VALUE);
      mergeCalcAttrs(rlensJoined, rlens, i);

      for(int c = headerCols; c < rlens.getColCount(); c++) {
         final int cj = origCols + c - headerCols;
         rlensJoined.addCalcColumn(i + 1, rlens.getColumnCellContext(c));
         final int w = rlens.getColWidth(c);

         if(w >= 0) {
            rlensJoined.setColWidth(cj, w);
         }

         for(int r = 0; r < rlens.getRowCount(); r++) {
            rlensJoined.setObject(r, cj, rlens.getObject(r, c));
            rlensJoined.setColBorder(r, cj, rlens.getColBorder(r, c));
            rlensJoined.setColBorderColor(r, cj, rlens.getColBorderColor(r, c));
            rlensJoined.setRowBorder(r, cj, rlens.getRowBorder(r, c));
            rlensJoined.setRowBorderColor(r, cj, rlens.getRowBorderColor(r, c));
            rlensJoined.setSpan(r, cj, rlens.getSpan(r, c));
            rlensJoined.setAlignment(r, cj, rlens.getAlignment(r, c));
            rlensJoined.setFont(r, cj, rlens.getFont(r, c));
            rlensJoined.setLineWrap(r, cj, rlens.isLineWrap(r, c));
            rlensJoined.setForeground(r, cj, rlens.getForeground(r, c));
            rlensJoined.setBackground(r, cj, rlens.getBackground(r, c));
            TableDataDescriptor desc = rlens.getDescriptor();
            TableDataPath path = desc.getCellDataPath(rlens.getRow(r), i+1);
            rlensJoined.setXMetaInfo(r, cj, desc.getXMetaInfo(path));
         }
      }
   }

   /**
    * Merge calcattrs to the joined calclens.
    * @param index the index of the child calctablelens.
    */
   private void mergeCalcAttrs(RuntimeCalcTableLens rlensJoined,
                               RuntimeCalcTableLens rlens, int index)
   {
      FormulaTable elem = rlensJoined.getElement();
      TableLayout layout = elem == null ? null : elem.getTableLayout();

      if(layout == null) {
         return;
      }

      for(int i = 0; i < layout.getRegionCount(); i++) {
         BaseLayout.Region tregion = layout.getRegion(i);

         if(!tregion.isVisible()) {
            continue;
         }

         for(int rr = 0; rr < tregion.getRowCount(); rr++) {
            int r = LayoutTool.convertToGlobalRow(layout, tregion, rr, true);

            for(int j = 0; j < layout.getColCount(); j++) {
               CalcAttr attr = rlens.getCalcAttr(r, j);

               if(attr == null || Tool.equals(attr + "", rlensJoined.getCalcAttr(r, j) + "")) {
                  continue;
               }

               CalcAttr nattr = (CalcAttr) attr.clone();
               nattr.setCol(j + index);
               rlensJoined.addCalcAttr(nattr);
            }
         }
      }
   }

   /**
    * Check if the layout has formula binding.
    * @return true if layout has formula binding, else otherwise.
    */
   private boolean hasFormulaBinding(TableLayout layout) {
      if(layout == null) {
         return false;
      }

      List<CellBindingInfo> infos = layout.getCellInfos(true);

      for(int i = 0; i < infos.size(); i++) {
         CellBindingInfo cell = infos.get(i);

         if(cell.getType() == CellBinding.BIND_FORMULA ||
            cell.getValue() != null)
         {
            return true;
         }
      }

      return true;
   }

   private void removeTempAssembly(String assemblyName) {
      Viewsheet vs = getViewsheet();

      for(int ct = 0; true; ct++) {
         String name = TEMP_ASSEMBLY_PREFIX + assemblyName + "_Crosstab_" + ct;

         if(vs.getAssembly(name) == null) {
            break;
         }
         else {
            vs.removeAssembly(name, false);
         }
      }
   }

   /**
    * Get the default column selection.
    * @return the default column selection.
    */
   @Override
   public ColumnSelection getDefaultColumnSelection() throws Exception {
      TableAssembly ta = getTableAssembly();

      if(ta == null) {
         return new ColumnSelection();
      }

      return VSUtil.getVSColumnSelection(ta.getColumnSelection(false));
   }

   /**
    * Get the base table assembly.
    * @return the base table assembly.
    */
   public TableAssembly getTableAssembly() throws Exception {
      CalcTableVSAssembly cassembly = (CalcTableVSAssembly) getAssembly();

      if(cassembly == null) {
         return null;
      }

      SourceInfo sinfo = cassembly.getSourceInfo();

      if(sinfo == null || sinfo.isEmpty()) {
         return null;
      }

      if(sinfo.getType() != SourceInfo.ASSET && sinfo.getType() != SourceInfo.VS_ASSEMBLY) {
         throw new RuntimeException("Unsupported source found: " + sinfo);
      }

      Worksheet ws = getWorksheet();
      String name = cassembly.getTableName();
      TableAssembly table;

      if(sinfo.getType() == SourceInfo.VS_ASSEMBLY) {
         table = createAssemblyTable(sinfo.getSource());

         if(table == null) {
            throw new BoundTableNotFoundException(Catalog.getCatalog().getString
               ("common.notTable", name));
         }
      }
      else {
         table = getVSTableAssembly(name);

         if(table == null) {
            throw new BoundTableNotFoundException(Catalog.getCatalog().getString
               ("common.notTable", name));
         }

         table = box.getBoundTable(table, vname, isDetail());
         table.setProperty("post.sort", "true");
      }

      ws.addAssembly(table);
      normalizeTable(table);

      ColumnSelection ncolumn = null;

      if(!getViewsheet().isLMSource()) {
         ncolumn = new ColumnSelection();
         ColumnSelection ocolumn = table.getColumnSelection();
         DataRef[] refs = cassembly.getBindingRefs();

         for(int i = 0; i < ocolumn.getAttributeCount(); i++) {
            DataRef ref = ocolumn.getAttribute(i);

            if(ncolumn.containsAttribute(ref)) {
               continue;
            }

            for(int j = 0; j < refs.length; j++) {
               if(refs[j].getAttribute().equals(ref.getAttribute())) {
                  ncolumn.addAttribute(ref);
               }
            }
         }

         ocolumn.copyPropertiesTo(ncolumn);
      }

      ConditionList details = !isDetail() ? null :
         cassembly.getDetailConditionList();

      if(details != null && !details.isEmpty()) {
         ncolumn = table.getColumnSelection();
         details = VSUtil.normalizeConditionList(ncolumn, details, true);

         if(details != null && !details.isEmpty()) {
            ConditionListWrapper wrapper = table.getPreRuntimeConditionList();
            ConditionList conds = wrapper == null ? null :
               wrapper.getConditionList();
            List list = new ArrayList();
            list.add(conds);
            list.add(details);
            conds = ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
            table.setPreRuntimeConditionList(conds);
         }

         table.setProperty("showDetail", "true");
      }

      if(!isDetail()) {
         ChartVSAssembly chart = box.getBrushingChart(vname);
         setSharedCondition(chart, table);
      }

      SortInfo sortInfo = cassembly.getSortInfo();

      if(sortInfo != null) {
         ncolumn = table.getColumnSelection();
         SortInfo osinfo = table.getSortInfo();
         SortRef[] sorts = sortInfo.getSorts();

         for(int i = 0; i < sorts.length; i++) {
            ColumnRef column =
               (ColumnRef) ncolumn.getAttribute(sorts[i].getName());

            if(column != null) {
               sorts[i] = sorts[i].copySortRef(column);
               osinfo.removeSort(sorts[i]);
            }
         }

         for(int i = sorts.length - 1; i >= 0; i--) {
            osinfo.addSort(0, sorts[i]);
         }

         table.setSortInfo(osinfo);
      }

      return table;
   }

   /**
    * Try to create crosstab assembly to support calc.
    */
   private List<CrosstabVSAssembly> createCrosstabAssemblies(
      CalcTableVSAssembly cassembly, List<CalcTableVSAssembly> cassemblys,
      AtomicInteger headerCols) throws Exception
   {
      List<CrosstabVSAssembly> list = createCrosstabAssemblies0(cassembly, cassemblys, headerCols);

      if(list != null && list.size() > 0) {
         CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) cassembly.getInfo();
         TableLayout parentLayout = info.getTableLayout();
         VSCrosstabInfo crosstab = list.get(0).getVSCrosstabInfo();
         int hcols = crosstab.getDesignRowHeaders().length;

         if(crosstab.getDesignAggregates().length > 1 && !crosstab.isSummarySideBySide()) {
            hcols++;
         }

         // even if there is no row header, a column is added to show
         // aggregate name as a header column
         hcols = Math.max(hcols, 1);

         // if there are columns that will be skipped in merging, we shouldn't
         // merge it otherwise the columns will be missing. we can remove
         // this check if we enhance the merging logic to not throw away
         // these columns in the future
         Set<Integer> skipped = getSkipCols(parentLayout);

         // skipped columns in the header region are not lost, so it's ok
         for(int i = 0; i < hcols; i++) {
            skipped.remove(i);
         }

         // skipped columns at the end are kept, so it's ok
         for(int i = parentLayout.getColCount() - 1; i >= hcols; i--) {
            if(skipped.contains(i)) {
               skipped.remove(i);
            }
            else {
               break;
            }
         }

         // columns skipped in the middle, don't use crosstab
         if(!skipped.isEmpty()) {
            return null;
         }
      }

      return list;
   }

   private List<CrosstabVSAssembly> createCrosstabAssemblies0(
      CalcTableVSAssembly cassembly, List<CalcTableVSAssembly> cassemblys,
      AtomicInteger headerCols) throws Exception
   {
      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) cassembly.getInfo();
      TableLayout parentLayout = info.getTableLayout();

      if(!quickCheck(parentLayout)) {
         return null;
      }

      // this method may modify the table layout to use the result of crosstab. this
      // change should not be permanent.
      cassembly = (CalcTableVSAssembly) cassembly.clone();
      cassembly.setTableLayout(parentLayout = (TableLayout) parentLayout.clone());

      XNode parentCroot = LayoutTool.buildTree(parentLayout).getChild(1);

      // if a column is bound to two cells with one as the parent group of the other,
      // the generated script won't work with a crosstab as base data. (61692)
      if(hasNestedSameGroup(parentCroot, parentLayout)) {
         return null;
      }

      List<TableLayout> layouts = new ArrayList<>();

      // @by ChrisSpagnoli feature1414607346853 2014-10-27
      // If the table has multiple groups, break it into columns, and
      // create one crosstab per column (for better performance).
      if(hasMultiGroup(parentLayout, parentCroot)) {
         layouts.addAll(splitLayoutByColumns(parentLayout, parentCroot, cassembly, cassemblys,
                                             headerCols));
      }
      else {
         layouts.add(parentLayout);
         cassemblys.add(cassembly);
      }

      List<CrosstabVSAssembly> crosstabs = new ArrayList<>();

      // @by ChrisSpagnoli feature1414607346853 2014-10-27
      // Evaluate each crosstab fully.  If any fail, then all fail.
      for(int ct = 0; ct < layouts.size(); ct++) {
         TableLayout layout = layouts.get(ct);

         if(!quickCheck(layout)) {
            LOG.debug("createCrosstabAssemblies() !quickCheck(layout) FAIL");
            // @by ChrisSpagnoli feature1414607346853 2014-11-19
            // For any failure, set the layouts and cassemblys to the 1 parent
            // before returning out
            layouts.set(0, parentLayout);
            cassemblys.set(0, cassembly);
            return null;
         }

         TableAssembly wtable = getTableAssembly();

         if(wtable == null) {
            LOG.debug("createCrosstabAssemblies() getTableAssembly() FAIL");
            layouts.set(0, parentLayout);
            cassemblys.set(0, cassembly);
            return null;
         }

         VSCrosstabInfo cinfo = new VSCrosstabInfo();
         /*
         // col/row total always visible
         // @temp davyc, to be enhance
         cinfo.setColTotalVisibleValue("true");
         cinfo.setRowTotalVisibleValue("true");
         */
         AggregateInfo ainfo = new AggregateInfo();
         Viewsheet vs = getViewsheet();
         String btable = cassembly.getTableName();

         if(vs == null || btable == null) {
            LOG.debug("createCrosstabAssemblies() "+
               "getViewsheet() || cassembly.getTableName() FAIL");
            layouts.set(0, parentLayout);
            cassemblys.set(0, cassembly);
            return null;
         }

         CalculateRef[] calcs = vs.getCalcFields(btable);

         if(calcs != null) {
            for(CalculateRef calc : calcs) {
               if(!calc.isBaseOnDetail()) {
                  ainfo.addAggregate(new AggregateRef(calc, AggregateFormula.NONE));
               }
            }
         }

         cinfo.setAggregateInfo(ainfo);
         XNode root = LayoutTool.buildTree(layout);
         // create row headers
         XNode rroot = root.getChild(0);
         XNode croot = root.getChild(1);

         // row and column header with same group binding
         if(hasSameGroupNode(rroot, croot, layout)) {
            LOG.debug("createCrosstabAssemblies() "+
               "hasSameGroupNode(rroot, croot, layout) FAIL");
            layouts.set(0, parentLayout);
            cassemblys.set(0, cassembly);
            return null;
         }

         List<TableCellBinding> rgroups = new ArrayList<>();

         if(!isTreeValid(layout, rroot, rgroups)) {
            LOG.debug("createCrosstabAssemblies() "+
               "!isTreeValid(layout, rroot, rgroups) FAIL");
            layouts.set(0, parentLayout);
            cassemblys.set(0, cassembly);
            return null;
         }

         VSDimensionRef[] rows = createCrosstabHeaders(wtable, rgroups);

         // non-date column should only use once
         if(rows == null || duplicate(rows)) {
            LOG.debug("createCrosstabAssemblies() "+
               "createCrosstabHeaders(wtable, layout, rgroups) || "+
               "duplicate(rows) FAIL");
            layouts.set(0, parentLayout);
            cassemblys.set(0, cassembly);
            return null;
         }

         cinfo.setDesignRowHeaders(rows);

         // create col headers
         List<TableCellBinding> cgroups = new ArrayList<>();

         if(!isTreeValid(layout, croot, cgroups)) {
            LOG.debug("createCrosstabAssemblies() !isTreeValid(layout, croot, cgroups) FAIL");
            layouts.set(0, parentLayout);
            cassemblys.set(0, cassembly);
            return null;
         }

         VSDimensionRef[] cols = createCrosstabHeaders(wtable, cgroups);

         // non-date column should only use once
         if(cols == null || duplicate(cols)) {
            LOG.debug("createCrosstabAssemblies() cols == null || duplicate(cols) FAIL");
            layouts.set(0, parentLayout);
            cassemblys.set(0, cassembly);
            return null;
         }

         cinfo.setDesignColHeaders(cols);

         // if row header and column header with duplicate ref, but it is not
         // date type, not supported
         if(duplicate(rows, cols)) {
            LOG.debug("createCrosstabAssemblies() duplicate(rows, cols) FAIL");
            layouts.set(0, parentLayout);
            cassemblys.set(0, cassembly);
            return null;
         }

         // this is not a valid crosstab, so the table is not crosstabfilter,
         // if we support, the calc expression will need to be changed
         if(rows.length == 0 && cols.length == 0) {
            LOG.debug("createCrosstabAssemblies() "+
               "rows.length == 0 && cols.length == 0 FAIL");
            layouts.set(0, parentLayout);
            cassemblys.set(0, cassembly);
            return null;
         }

         // create aggregates
         List<TableCellBinding> allaggs = new ArrayList<>();
         Map<String, List<TableCellBinding>> f2aggs = new LinkedHashMap();
         // aggregate may be not expandable
         listAggs(layout, allaggs);
         VSAggregateRef[] aggrs = createCrosstabAggs(wtable, layout, allaggs, f2aggs);

         // crosstab aggregate and group should not same
         if(aggrs.length <= 0 || duplicate(aggrs, rows) || duplicate(aggrs, cols)) {
            LOG.debug("createCrosstabAssemblies() "+
               "(aggrs.length <= 0 || duplicate(aggrs, rows) || "+
               "duplicate(aggrs, cols)) FAIL");
            layouts.set(0, parentLayout);
            cassemblys.set(0, cassembly);
            return null;
         }

         List<List<TableCellBinding>> aggbindings = new ArrayList<>(f2aggs.values());

         cinfo.setDesignAggregates(aggrs);
         int perby = getPercentageBy(allaggs);

         // perby should only be one of row or total
         if(perby == -1) {
            LOG.debug("createCrosstabAssemblies() perby == -1 FAIL");
            layouts.set(0, parentLayout);
            cassemblys.set(0, cassembly);
            return null;
         }

         cinfo.setPercentageByValue(perby + "");

         if(!allCellUsed(layout, rroot, croot)) {
            LOG.debug("createCrosstabAssemblies() !allCellUsed(layout, rroot, croot)");
            layouts.set(0, parentLayout);
            cassemblys.set(0, cassembly);
            return null;
         }

         // always invisible crosstab
         CrosstabVSAssembly crosstab = new CrosstabVSAssembly(
            getViewsheet(), TEMP_ASSEMBLY_PREFIX + cassembly.getName() + "_Crosstab_" + ct)
         {
            @Override
            protected VSAssemblyInfo createInfo() {
               CrosstabVSAssemblyInfo crosstabInfo = new CrosstabVSAssemblyInfo() {
                  @Override
                  public boolean isVisible(boolean print) {
                     return false;
                  }
               };

               crosstabInfo.getVSCrosstabInfo().setCalcTableTempCrosstab(true);

               return crosstabInfo;
            }

            @Override
            public boolean isVisible() {
               return false;
            }

            @Override
            public boolean isAggregateTopN() {
               return true;
            }

            @Override
            public boolean supportPeriod() {
               return false;
            }
         };

         cinfo.setFillBlankWithZeroValue(info.isFillBlankWithZero());
         cinfo.setSortOthersLastValue(info.isSortOthersLast());
         crosstab.setSourceInfo((SourceInfo) cassembly.getSourceInfo().clone());
         crosstab.setVSCrosstabInfo(cinfo);
         crosstab.setPreConditionList(cassembly.getPreConditionList());
         crosstab.setTipConditionList(cassembly.getTipConditionList());
         getViewsheet().removeAssembly(crosstab.getName(), false);
         getViewsheet().addAssembly(crosstab, false, false);
         // update, so that full name will be maintain correct
         box.updateAssembly(crosstab.getName());

         // rewrite group cell value
         List<TableCellBinding> bindings = new ArrayList<>();
         bindings.addAll(rgroups);
         bindings.addAll(cgroups);

         List<VSDimensionRef> refs = new ArrayList<>();

         for(DataRef ref : cinfo.getDesignRowHeaders()) {
            refs.add((VSDimensionRef) ref);
         }

         for(DataRef ref: cinfo.getDesignColHeaders()) {
            refs.add((VSDimensionRef) ref);
         }

         DataRef[] aggs = cinfo.getDesignAggregates();
         boolean hasPercent = hasPercentRef(aggs, refs);

         // check row and column grand total visible
         // if there exist any aggregate cell in the
         // row/column tree, set grand total visible
         if(hasPercent || hasGrandTotal(layout, rroot) ||
            hasGrandTotal(layout, croot) || hasSubTotal(layout, rroot) ||
            hasSubTotal(layout, croot))
         {
            cinfo.setColTotalVisibleValue("true");
            cinfo.setRowTotalVisibleValue("true");

            for(VSDimensionRef ref : refs) {
               ref.setSubTotalVisibleValue("true");
            }
         }
         else {
            cinfo.setColTotalVisibleValue("false");
            cinfo.setRowTotalVisibleValue("false");

            for(VSDimensionRef ref : refs) {
               ref.setSubTotalVisibleValue("false");
            }
         }

         Map<String, Integer> dups = new HashMap<>();
         List<String> names = new ArrayList<>();
         boolean handleNoneDateLevel = !isPushDownFormula(cinfo.getRuntimeAggregates()) &&
            !supportsAOA(cinfo);

         for(int i = 0; i < refs.size(); i++) {
            VSDimensionRef ref = refs.get(i);
            String value = ref.getFullName();

            if(handleNoneDateLevel && ref.isDate() && ref.getDateLevel() == DateRangeRef.NONE) {
               value = ref.getName();
            }

            TableCellBinding binding = bindings.get(i);
            Integer dup = dups.get(value);

            if(dup == null) {
               dups.put(value, 1);
            }
            else {
               dups.put(value, dup + 1);
               value = value + "." + dup;
            }

            // for cell value changed, the runtime cell name will be
            // changed, so here make sure the name is same before and
            // after cell value changed
            binding.setValue(value);
            String cellname = layout.getRuntimeCellName(binding);
            // binding.setCellName(cellname);
            names.add(cellname);
         }

         for(int i = 0; i < bindings.size(); i++) {
            bindings.get(i).setCellName(names.get(i));
         }

         // rewrite aggregate cell value

         String[] anames = new String[aggs.length];
         List<String>[] aggnames = new List[aggbindings.size()];
         dups = new HashMap<>();

         for(int i = 0; i < aggbindings.size(); i++) {
            String value = ((VSAggregateRef) aggs[i]).getFullName();
            Integer dup = dups.get(value);

            if(dup == null) {
               dups.put(value, 1);
            }
            else {
               dups.put(value, dup + 1);
               value = value + "." + dup;
            }

            List<TableCellBinding> bs = aggbindings.get(i);
            aggnames[i] = new ArrayList<>();

            for(TableCellBinding bind : bs) {
               // bind.setValue(value);
               String cellname = layout.getRuntimeCellName(bind);
               // bind.setCellName(cellname);
               aggnames[i].add(cellname);
            }

            anames[i] = value;
         }

         dups = new HashMap<>();

         for(int i = 0; i < aggbindings.size(); i++) {
            String value = ((VSAggregateRef) aggs[i]).getFullName();
            Integer dup = dups.get(value);

            if(dup == null) {
               dups.put(value, 1);
            }
            else {
               dups.put(value, dup + 1);
               value = value + "." + dup;
            }

            for(int j = 0; j < aggbindings.get(i).size(); j++) {
               aggbindings.get(i).get(j).setCellName(aggnames[i].get(j));
               //for feature1370459255702 set value after setting cell name
               aggbindings.get(i).get(j).setValue(value);
            }
         }

         // change ranking col and sort by value col
         indexToValue(anames, cinfo.getDesignRowHeaders());
         indexToValue(anames, cinfo.getDesignColHeaders());
         // update, so that runtime refs will be correct
         box.updateAssembly(crosstab.getName());
         crosstabs.add(crosstab);
         LOG.debug("createCrosstabAssemblies() "+
            "Evaluation of calc child #"+ct+" for crosstab is PASS");
      }

      return crosstabs;
   }

   // check if any node is bound to the same column as it's parent group.
   private boolean hasNestedSameGroup(XNode node, TableLayout layout) {
      CalcAttr attr = (CalcAttr) node.getValue();

      if(attr != null && node.getParent() != null) {
         CellBindingInfo cell = layout.getCellInfo(attr.getRow(), attr.getCol());

         if(cell != null && cell.getType() == CellBinding.BIND_COLUMN &&
            cell.getBType() == CellBinding.GROUP && cell.getValue() != null)
         {
            return isParentSameGroup(node.getParent(), cell.getValue(), layout);
         }
      }

      for(int i = 0; i < node.getChildCount(); i++) {
         if(hasNestedSameGroup(node.getChild(i), layout)) {
            return true;
         }
      }

      return false;
   }

   private boolean isParentSameGroup(XNode parent, String col, TableLayout layout) {
      CalcAttr attr = (CalcAttr) parent.getValue();

      if(attr != null) {
         CellBindingInfo cell = layout.getCellInfo(attr.getRow(), attr.getCol());

         if(cell != null && cell.getType() == CellBinding.BIND_COLUMN &&
            cell.getBType() == CellBinding.GROUP && col.equals(cell.getValue()))
         {
            return true;
         }
      }

      if(parent.getParent() != null) {
         isParentSameGroup(parent.getParent(), col, layout);
      }

      return false;
   }

   private boolean hasPercentRef(DataRef[] aggs, List<VSDimensionRef> refs) {
      boolean hasPercent = false;

      for(DataRef agg : aggs) {
         if(((VSAggregateRef) agg).getPercentageOption() != XConstants.PERCENTAGE_NONE) {
            hasPercent = true;
            break;
         }
      }

      if(hasPercent) {
         hasPercent = false;

         for(DataRef ref : refs) {
            VSDimensionRef dim = (VSDimensionRef) ref;

            if((dim.getRankingOption() == XCondition.BOTTOM_N ||
               dim.getRankingOption() == XCondition.TOP_N) &&
               dim.getRankingN() > 0)
            {
               hasPercent = true;
               break;
            }
         }
      }

      return hasPercent;
   }

   /**
    * Check if the target aggregates support pushdown when execute CrosstabVSAQuery.
    *
    * When not pushdown aggregates, only not none level date range ref be added to columnselections,
    * so the binding cell name which like None(Date) cannot match the Date column header in
    * tablelens, so here we need to use column name without date level prefix to make sure
    * the column can be matched.
    */
   private boolean isPushDownFormula(DataRef[] aggrs) {
      if(aggrs == null || aggrs.length == 0) {
         return false;
      }

      AggregateRef[] allaggs =
         VSUtil.getAggregates(getViewsheet(), getSourceTable(), false);

      for(int i = 0; i < aggrs.length; i++) {
         if(aggrs[i] instanceof IAggregateRef &&
            !isPushDownFormula(((IAggregateRef) aggrs[i]), allaggs))
         {
            return false;
         }
      }

      return true;
   }

   /**
    * Check if the target aggregates support pushdown when execute CrosstabVSAQuery.
    *
    * When not pushdown aggregates, only not none level date range ref be added to columnselections,
    * so the binding cell name which like None(Date) cannot match the Date column header in
    * tablelens, so here we need to use column name without date level prefix to make sure
    * the column can be matched.
    */
   private boolean isPushDownFormula(IAggregateRef aggr, AggregateRef[] allaggs) {
      // add all aggregates
      if(VSUtil.isAggregateCalc(aggr.getDataRef())) {
         CalculateRef cref = (CalculateRef) aggr.getDataRef();
         ExpressionRef eref = (ExpressionRef) cref.getDataRef();
         List<String> names = new ArrayList<>();
         List<AggregateRef> subs = VSUtil.findAggregate(allaggs, names, eref.getExpression());

         for(int i = 0; subs != null && i < subs.size(); i++) {
            if(!isPushDownFormula(subs.get(i), allaggs)) {
               return false;
            }
         }
      }

      AggregateFormula formula = aggr.getFormula();

      if(formula.isTwoColumns() || formula.hasN()) {
         return false;
      }

      return true;
   }

   /**
    * Check if supports aggregate on aggregate.
    */
   private boolean supportsAOA(VSCrosstabInfo cinfo) {
      DataRef[] aggrs = cinfo.getRuntimeAggregates();
      AggregateRef[] allaggs =
         VSUtil.getAggregates(getViewsheet(), getSourceTable(), false);

      // add all aggregates
      for(int i = 0; i < aggrs.length; i++) {
         if(aggrs[i] instanceof VSAggregateRef) {
            if(!VSUtil.supportsAOA((VSAggregateRef) aggrs[i], allaggs)) {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Check if there exist grand total aggregate.
    */
   private boolean hasGrandTotal(TableLayout layout, XNode root) {
      boolean hasAgg = false;
      boolean hasGrp = false;

      if(root != null) {
         for(int i = 0; i < root.getChildCount(); i++) {
            XNode child = root.getChild(i);
            CalcAttr cattr = child == null ? null : (CalcAttr) child.getValue();

            if(cattr == null) {
               continue;
            }

            CellBinding bind = layout.getCellBinding(cattr.getRow(),
                                                     cattr.getCol());

            if(bind != null && bind.getType() == CellBinding.BIND_COLUMN &&
               !bind.isEmpty())
            {
               if(bind.getBType() == CellBinding.SUMMARY) {
                  hasAgg = true;
               }
               else if(bind.getBType() == CellBinding.GROUP) {
                  hasGrp = true;
               }
            }
         }
      }

      return hasGrp && hasAgg;
   }

   /**
    * Check if there exist any sub total aggregate.
    */
   private boolean hasSubTotal(TableLayout layout, XNode node) {
      Set<Integer> glevels = new HashSet();
      Set<Integer> alevels = new HashSet();
      collectLevels(layout, node, 0, glevels, alevels);

      if(alevels.size() <= 0 || glevels.size() <= 0) {
         return false;
      }

      // if no sub total, all agregates should in last level
      if(alevels.size() > 1) {
         return true;
      }

      // aggregate level should in the lowest level
      int alevel = toArray(alevels)[0];

      for(int glevel : glevels) {
         if(alevel <= glevel) {
            return true;
         }
      }

      return false;
   }

   private int[] toArray(Set<Integer> set) {
      int[] arr = new int[set.size()];
      Iterator<Integer> ite = set.iterator();
      int idx = 0;

      while(ite.hasNext()) {
         arr[idx++] = ite.next();
      }

      return arr;
   }

   private void collectLevels(TableLayout layout, XNode node, int level,
                              Set<Integer> glevels, Set<Integer> alevels)
   {
      for(int i = 0; i < node.getChildCount(); i++) {
         XNode child = node.getChild(i);
         CalcAttr cattr = child == null ? null : (CalcAttr) child.getValue();

         if(cattr == null) {
            continue;
         }

         CellBinding bind = layout.getCellBinding(cattr.getRow(),
                                                  cattr.getCol());

         if(bind != null && bind.getType() == CellBinding.BIND_COLUMN &&
            !bind.isEmpty())
         {
            if(bind.getBType() == CellBinding.SUMMARY) {
               alevels.add(level);
            }
            else if(bind.getBType() == CellBinding.GROUP) {
               glevels.add(level);
            }
         }
      }

      for(int i = 0; i < node.getChildCount(); i++) {
         XNode child = node.getChild(i);
         collectLevels(layout, child, level + 1, glevels, alevels);
      }
   }

   /**
    * Convert sort by value and ranking col index to col name.
    */
   private void indexToValue(String[] anames, DataRef[] refs) {
      for(DataRef ref : refs) {
         VSDimensionRef dim = (VSDimensionRef) ref;
         String rindex = dim.getRankingColValue();

         try {
            int idx = Integer.parseInt(rindex);
            String name = idx >= 0 && idx < anames.length ? anames[idx] : null;
            dim.setRankingColValue(name);
         }
         catch(Exception ex) {
            // ignore it
         }

         String sindex = dim.getSortByColValue();

         try {
            int idx = Integer.parseInt(sindex);
            String name = idx >= 0 && idx < anames.length ? anames[idx] : null;
            dim.setSortByColValue(name);
         }
         catch(Exception ex) {
            // ignore it
         }
      }
   }

   /**
    * Create row/col header columns.
    */
   private VSDimensionRef[] createCrosstabHeaders(TableAssembly wtable, List<TableCellBinding> groups) {
      VSDimensionRef[] dims = new VSDimensionRef[groups.size()];
      ColumnSelection cols = wtable.getColumnSelection();

      for(int i = 0; i < groups.size(); i++) {
         TableCellBinding bind = groups.get(i);
         ColumnRef column = (ColumnRef) cols.getAttribute(bind.getValue());
         String col = bind.getValue();

         if(column == null) {
            col = VSLayoutTool.getOriginalColumn(bind.getValue());
            column = (ColumnRef) cols.getAttribute(col);
         }

         // invalid
         if(column == null) {
            return null;
         }

         OrderInfo order = bind.getOrderInfo(false);
         TopNInfo topn = bind.getTopN(false);
         VSDimensionRef dim = new VSDimensionRef();
         dim.setGroupColumnValue(col);
         dim.setDataType(column.getDataType());
         dim.setTimeSeries(bind.isTimeSeries());
         /*
         // @temp davyc, to be enhance
         dim.setSubTotalVisibleValue("true");
         */

         if(topn != null && topn.getTopN() > 0 && topn.getTopNSummaryCol() >= 0) {
            dim.setRankingOptionValue("" + (topn.isTopNReverse() ?
               XCondition.BOTTOM_N : XCondition.TOP_N));
            dim.setRankingNValue(topn.getTopN() + "");
            // @by davyc, ranking col and sort by col need to be changed
            // to the name of the aggregate, not index, will be fixed later
            dim.setRankingColValue(topn.getTopNSummaryCol() + "");
            dim.setGroupOthersValue(topn.isOthers() + "");

            // only inner most support others
            if(topn.isOthers() && i != groups.size() - 1) {
               return null;
            }
         }

         if(order != null) {
            dim.setSortByColValue(order.getSortByCol() + "");
            dim.setDateLevelValue(order.getOption() + "");

            // crosstab doesn't support named group
            if(order.getNamedGroupInfo() != null || order.getInterval() > 1) {
               return null;
            }

            // dim.setNamedGroupInfo(order.getNamedGroupInfo());
            dim.setOrder(order.getOrder());
         }

         dims[i] = dim;
      }

      return dims;
   }

   /**
    * Create crosstab aggregates.
    */
   private VSAggregateRef[] createCrosstabAggs(TableAssembly wtable,
                                               TableLayout layout, List<TableCellBinding> allaggs,
                                               Map<String, List<TableCellBinding>> f2aggs)
   {
      List<VSAggregateRef> aggs = new ArrayList<>();

      for(TableCellBinding bind : allaggs) {
         String formula = bind.getFormula();
         String key = bind.getValue() + "-" + formula;
         List<TableCellBinding> temp = f2aggs.get(key);
         boolean created = temp != null;

         if(temp == null) {
            temp = new ArrayList<>();
            f2aggs.put(key, temp);
         }

         temp.add(bind);

         if(created) {
            continue;
         }

         LayoutTool.FormulaStorage f = LayoutTool.parseFormula(formula);
         VSAggregateRef agg = new VSAggregateRef();
         ColumnSelection columnSelection = wtable.getColumnSelection();
         String value = columnSelection.getAttribute(bind.getValue()) != null ? bind.getValue()
                 : VSLayoutTool.getOriginalColumn(bind.getValue());

         agg.setColumnValue(value);
         agg.setSecondaryColumnValue(f.secondf);
         agg.setFormulaValue(f.formula);

         if("countDistinct".equals(f.formula)) {
            agg.setFormulaValue("DistinctCount");
         }

         int percent = -1;

         if(f.percent == XConstants.PERCENTAGE_NONE || f.percent == -1) {
            percent = XConstants.PERCENTAGE_NONE;
         }
         else if(f.percent == XConstants.PERCENTAGE_OF_ROW_GROUP ||
            f.percent == XConstants.PERCENTAGE_OF_COL_GROUP)
         {
            percent = XConstants.PERCENTAGE_OF_GROUP;
         }
         else {
            percent = XConstants.PERCENTAGE_OF_GRANDTOTAL;
         }

         agg.setPercentageOptionValue(percent + "");

         try {
            int n = Integer.parseInt(f.n);
            agg.setNValue(Math.max(1, n) + "");
         }
         catch(Exception ex) {
            agg.setNValue(f.n);
         }

         aggs.add(agg);
      }

      return aggs.toArray(new VSAggregateRef[0]);
   }

   /**
    * Check if duplicate ref exist.
    */
   private boolean duplicate(VSDimensionRef[] refs) {
      for(int i = 0; i < refs.length; i++) {
         if(refs[i].isDateTime()) {
            continue;
         }

         String gvalue = refs[i].getGroupColumnValue();

         for(int j = i + 1; j < refs.length; j++) {
            if(gvalue.equals(refs[j].getGroupColumnValue())) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Check if duplicate ref exist.
    */
   private boolean duplicate(VSDimensionRef[] rows, VSDimensionRef[] cols) {
      for(VSDimensionRef rcol : rows) {
         if(rcol.isDateTime()) {
            continue;
         }

         DataRef ref = rcol.getDataRef();
         String gvalue = rcol.getGroupColumnValue();

         for(VSDimensionRef ccol : cols) {
            if(gvalue.equals(ccol.getGroupColumnValue())) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Check if duplicate ref exist.
    */
   private boolean duplicate(VSAggregateRef[] aggs, VSDimensionRef[] dims) {
      for(VSAggregateRef agg : aggs) {
         String value = agg.getColumnValue();

         for(VSDimensionRef dim : dims) {
            if(value.equals(dim.getGroupColumnValue())) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Collect all aggregates.
    */
   private void listAggs(TableLayout layout, List<TableCellBinding> aggs) {
      for(BaseLayout.Region region : layout.getRegions()) {
         for(int r = 0; r < region.getRowCount(); r++) {
            for(int c = 0; c < region.getColCount(); c++) {
               TableCellBinding cell = (TableCellBinding)
                  region.getCellBinding(r, c);

               if(cell != null && cell.getType() == CellBinding.BIND_COLUMN &&
                  cell.getBType() == CellBinding.SUMMARY)
               {
                  aggs.add(cell);
               }
            }
         }
      }
   }

   /**
    * Check if there exists same group node in the tree.
    */
   private boolean hasSameGroupNode(XNode node1, XNode node2,
                                    TableLayout layout) {
      List<String> names1 = new ArrayList();
      buildGroupNames(node1, layout, names1);
      List<String> names2 = new ArrayList();
      buildGroupNames(node2, layout, names2);

      for(String name : names1) {
         if(names2.contains(name)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Build group node names.
    */
   private void buildGroupNames(XNode node, TableLayout layout,
                                List<String> names)
   {
      for(int i = 0; i < node.getChildCount(); i++) {
         XNode child = node.getChild(i);
         CalcAttr cattr = (CalcAttr) child.getValue();
         CellBinding bind = layout.getCellBinding(cattr.getRow(),
                                                  cattr.getCol());

         if(bind != null && bind.getType() == CellBinding.BIND_COLUMN &&
            bind.getBType() == CellBinding.GROUP)
         {
            names.add(child.getName());
         }

         buildGroupNames(child, layout, names);
      }
   }

   /**
    * Check if the tree is valid, for crosstab, for any grouped node, its
    * grouped child node(s) should less than 1.
    */
   private boolean isTreeValid(TableLayout layout, XNode node, List<TableCellBinding> groups) {
      // or each group(row or column header), it must only have one group child
      if(hasMultiGroup(layout, node)) {
         return false;
      }

      int ccnt = node.getChildCount();

      for(int i = 0; i < ccnt; i++) {
         XNode child = node.getChild(i);
         CalcAttr attr = (CalcAttr) child.getValue();

         if(attr == null) {
            continue;
         }

         TableCellBinding bind = (TableCellBinding) layout.getCellBinding(
            attr.getRow(), attr.getCol());

         // text binding's child should all text binding
         if(bind == null || bind.getType() == CellBinding.BIND_TEXT) {
            if(!isTextSubNodes(layout, child)) {
               return false;
            }

            continue;
         }

         // only support text and column binding in vs
         if(bind.getType() != CellBinding.BIND_COLUMN) {
            return false;
         }

         // summary cell? should not children or with text binding
         if(bind.getBType() == CellBinding.SUMMARY) {
            if(!isTextSubNodes(layout, child)) {
               return false;
            }

            continue;
         }

         // only support group, aggregate for crosstab
         if(bind.getBType() != CellBinding.GROUP) {
            return false;
         }

         groups.add(bind);

         // group node
         if(!isTreeValid(layout, child, groups)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Check if mulit groups exist in the direct children of the node.
    */
   private boolean hasMultiGroup(TableLayout layout, XNode node) {
      boolean findGroup = false;
      int ccnt = node.getChildCount();

      for(int i = 0; i < ccnt; i++) {
         CalcAttr attr = (CalcAttr) node.getChild(i).getValue();

         if(attr != null) {
            CellBinding bind = layout.getCellBinding(attr.getRow(), attr.getCol());

            if(bind != null && bind.getType() == CellBinding.BIND_COLUMN &&
               bind.getBType() == CellBinding.GROUP)
            {
               if(findGroup) {
                  return true;
               }

               findGroup = true;
            }
         }
      }

      return false;
   }

   // @by ChrisSpagnoli feature1414607346853 2014-10-27
   // Added to support splitting a calc table into multiple crosstabs.
   /**
    * Splits a table layout into one table per column.
    */
   private List<TableLayout> splitLayoutByColumns(
      TableLayout parentLayout, XNode croot,
      CalcTableVSAssembly cassemblyParent,
      List<CalcTableVSAssembly> cassemblyChildren,
      AtomicInteger headerCols)
   {
      List<TableLayout> layouts = new ArrayList<>();
      final int ccnt = croot.getChildCount();
      Set<Integer> skipCols = getSkipCols(parentLayout);
      LOG.debug("splitLayoutByColumns() "+
         " parentLayout.getColCount():" + parentLayout.getColCount() +
         " croot.getChildCount():" + croot.getChildCount()+
         " skipCols():" + skipCols.size()+skipCols);
      headerCols.set(parentLayout.getColCount() - ccnt - skipCols.size());

      for(int c = 0, i = 0; c < ccnt; c++, i++)  {
         while(skipCols.contains(i + headerCols.get())) {
            i++;
         }

         final int leaveCol = i + headerCols.get();
         LOG.debug("splitLayoutByColumns() "+
            "calc child #" + c +
            " = " + headerCols.get() + " headerColumns" +
            " + dataColumn #" + leaveCol);
         TableLayout childLayout = splitLayoutByColumnsOnColumns(
            parentLayout, headerCols.get(), leaveCol);
         layouts.add(childLayout);
         CalcTableVSAssembly cassemblyChild =
            (CalcTableVSAssembly)cassemblyParent.clone();
         cassemblyChild.setTableLayout(childLayout);
         cassemblyChildren.add(cassemblyChild);
      }

      return layouts;
   }

   // @by ChrisSpagnoli feature1414607346853 2014-10-27
   // Added to support crosstabb-ing a calc table which has "skipped" columns.
   /**
    * Determines which columns in the crosstab are unused, if any.
    */
   private Set<Integer> getSkipCols(final TableLayout parentLayout) {
      Set<Integer> skipCols = new HashSet<>();

      for(int j = 0; j < parentLayout.getColCount(); j++) {
         skipCols.add(j);
      }

      for(int i = 0; i < parentLayout.getRegionCount(); i++) {
         BaseLayout.Region tregion = parentLayout.getRegion(i);

         if(!tregion.isVisible()) {
            continue;
         }

         for(int rr = 0; rr < tregion.getRowCount(); rr++) {
            for(int j = 0; j < parentLayout.getColCount(); j++) {
               TableCellBinding bind =
                  (TableCellBinding) tregion.getCellBinding(rr, j);

               if(bind != null && !bind.isEmpty() &&
                  bind.getType() == CellBinding.BIND_COLUMN)
               {
                  skipCols.remove(j);
               }
            }
         }
      }

      return skipCols;
   }

   // @by ChrisSpagnoli feature1414607346853 2014-10-27
   // Added to support splitting a calc table into multiple crosstabs.
   /**
    * Splits a table layout into columns, by cloning the parent and deleting
    * out the unneeded colummns.  This way everything else is preserved.
    */
   private TableLayout splitLayoutByColumnsOnColumns(
      TableLayout parentLayout, int headerCols, int leaveCol)
   {
      TableLayout childLayout = (TableLayout)parentLayout.clone();

      for(int c = childLayout.getColCount() - 1; c > leaveCol; c--) {
         childLayout.removeColumn(c);
      }

      for(int c = leaveCol - 1; c >= headerCols; c--) {
         childLayout.removeColumn(c);
      }

      return childLayout;
   }

   /**
    * Check if all children with text binding.
    */
   private boolean isTextSubNodes(TableLayout layout, XNode node) {
      int ccnt = node.getChildCount();

      if(ccnt <= 0) {
         return true;
      }

      for(int i = 0; i < ccnt; i++) {
         XNode child = node.getChild(i);
         CalcAttr attr = (CalcAttr) child.getValue();

         if(attr == null) {
            continue;
         }

         CellBinding bind = layout.getCellBinding(attr.getRow(),
                                                  attr.getCol());

         if(bind != null && bind.getType() != CellBinding.BIND_TEXT) {
            return false;
         }

         if(!isTextSubNodes(layout, child)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Check if all cells in layout are used.
    */
   private boolean allCellUsed(TableLayout layout, XNode rroot, XNode croot) {
      boolean hasRowTopN = false;
      boolean hasColTopN = false;
      List<TableCellBinding> aggs = new ArrayList<>();

      for(BaseLayout.Region region : layout.getRegions()) {
         for(int r = 0; r < region.getRowCount(); r++) {
            for(int c = 0; c < region.getColCount(); c++) {
               Rectangle span = region.findSpan(r, c);

               if(span != null && (span.x != 0 || span.y != 0)) {
                  continue;
               }

               TableCellBinding b =
                  (TableCellBinding) region.getCellBinding(r, c);

               // for text binding, ignore it, for aggregate cell, all used
               if(b == null || b.getType() == CellBinding.BIND_TEXT ||
                  b.getBType() == CellBinding.DETAIL)
               {
                  continue;
               }

               if(b.getBType() == CellBinding.GROUP) {
                  boolean usedInRow = inUsed(layout, rroot, b);
                  boolean usedInCol = inUsed(layout, croot, b);
                  boolean hasTopN = false;

                  if(!usedInRow && !usedInCol) {
                     return false;
                  }

                  TopNInfo topN = b.getTopN(false);

                  if(topN != null && !topN.isBlank()) {
                     hasTopN = true;
                  }

                  if(hasTopN && usedInRow && usedInCol) {
                     hasRowTopN = true;
                     hasColTopN = true;
                  }
                  else if(hasTopN && usedInRow) {
                     hasRowTopN = true;
                  }
                  else if(hasTopN && usedInCol) {
                     hasColTopN = true;
                  }
               }
               else if(b.getBType() == CellBinding.SUMMARY) {
                  aggs.add(b);
               }
            }
         }
      }

      Iterator<TableCellBinding> iter = aggs.iterator();

      while(iter.hasNext()) {
         TableCellBinding b = iter.next();
         boolean usedInRow = inUsed(layout, rroot, b);
         boolean usedInCol = inUsed(layout, croot, b);

         if((usedInRow && !usedInCol && hasColTopN) ||
            (usedInCol && !usedInRow && hasRowTopN) ||
            (!usedInRow && !usedInCol && (hasColTopN || hasRowTopN)))
         {
            return false;
         }
      }

      return true;
   }

   private boolean inUsed(TableLayout layout, XNode node, CellBinding bind) {
      for(int i = 0; i < node.getChildCount(); i++) {
         XNode child = node.getChild(i);
         CalcAttr attr = (CalcAttr) child.getValue();
         CellBinding tmp = layout.getCellBinding(attr.getRow(), attr.getCol());

         if(tmp == bind) {
            return true;
         }

         if(inUsed(layout, child, bind)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check percentage by.
    */
   private int getPercentageBy(List<TableCellBinding> aggs) {
      int perBy = 0;

      for(TableCellBinding agg : aggs) {
         int percent = LayoutTool.parseFormula(agg.getFormula()).percent;

         if(percent == XConstants.PERCENTAGE_NONE || percent == -1) {
            continue;
         }

         percent = percent == XConstants.PERCENTAGE_OF_ROW_GROUP ||
            percent == XConstants.PERCENTAGE_OF_ROW_GRANDTOTAL ?
            XConstants.PERCENTAGE_BY_ROW : XConstants.PERCENTAGE_BY_COL;

         if(perBy == 0) {
            perBy = percent;
         }

         if(perBy != percent) {
            return -1;
         }
      }

      return perBy;
   }

   /**
    * Check if calc table can be processed as a set of crosstabs.
    */
   private boolean quickCheck(TableLayout layout) {
      for(BaseLayout.Region region : layout.getRegions()) {
         for(int r = 0; r < region.getRowCount(); r++) {
            for(int c = 0; c < region.getColCount(); c++) {
               TableCellBinding bind = (TableCellBinding) region.getCellBinding(r, c);

               // column binding should be summary or group
               if(bind != null && bind.getType() == CellBinding.BIND_COLUMN &&
                  bind.getBType() != CellBinding.SUMMARY &&
                  bind.getBType() != CellBinding.GROUP)
               {
                  return false;
               }

               // column always sorted in crosstab
               if(bind != null && bind.getType() == CellBinding.BIND_COLUMN &&
                  bind.getBType() == CellBinding.GROUP &&
                  bind.getOrderInfo(true).getOrder() == 0)
               {
                  return false;
               }

               if(bind != null && bind.getType() == CellBinding.BIND_FORMULA) {
                  return false;
               }

               // group cell should expand
               if(bind != null && bind.getType() == CellBinding.BIND_COLUMN &&
                  bind.getBType() == CellBinding.GROUP &&
                  bind.getExpansion() != TableCellBinding.EXPAND_H &&
                  bind.getExpansion() != TableCellBinding.EXPAND_V)
               {
                  return false;
               }
            }
         }
      }

      return true;
   }

   public static final String TEMP_ASSEMBLY_PREFIX = "__Temp_CalcTableVSAQuery__";
   private static final Logger LOG =
      LoggerFactory.getLogger(CalcTableVSAQuery.class);
   private static final Logger LOGCALCCROSSTAB =
      LoggerFactory.getLogger("inetsoft.report.composition.execution.DebugCalcCrosstabCheck");

}
