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

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.mv.MVManager;
import inetsoft.mv.trans.AbstractTransformer;
import inetsoft.mv.trans.TransformationDescriptor;
import inetsoft.report.TableDataPath;
import inetsoft.report.TableLens;
import inetsoft.report.filter.Formula;
import inetsoft.report.filter.NoneFormula;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.report.script.viewsheet.CompositeVSAScriptable;
import inetsoft.report.script.viewsheet.VSAScriptable;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.xmla.XMLAUtil;
import inetsoft.util.*;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.profile.ProfileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.awt.*;
import java.text.DateFormat;
import java.text.Format;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AbstractSelectionVSAQuery, the super class of selection viewsheet assembly
 * query.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class AbstractSelectionVSAQuery extends VSAQuery implements SelectionVSAQuery {
   /**
    * Create a selection list viewsheet assembly query.
    * @param box the specified viewsheet sandbox.
    * @param vname the specified viewsheet assembly to be processed.
    */
   public AbstractSelectionVSAQuery(ViewsheetSandbox box, String vname) {
      super(box, vname);
   }

   /**
    * Get the table assembly that contains binding info.
    * @param analysis <tt>true</tt> if is for analysis, <tt>false</tt> for
    * runtime.
    */
   private TableAssembly getTableAssembly(boolean analysis) throws Exception {
      Worksheet ws = getWorksheet();

      // worksheet not found?
      if(ws == null) {
         return null;
      }

      SelectionVSAssembly assembly = (SelectionVSAssembly) getAssembly();
      String tname = assembly.getSelectionTableName();

      // not yet defined?
      if(tname == null || tname.length() == 0) {
         return null;
      }

      TableAssembly tassembly = getVSTableAssembly(tname);

      if(tassembly == null) {
         LOG.warn("Table assembly not found: " + tname);
         return null;
      }

      tassembly = box.getBoundTable(tassembly, vname, isDetail());
      normalizeTable(tassembly);
      ws.addAssembly(tassembly);
      DataRef[] refs = assembly.getDataRefs();

      if(tassembly instanceof CubeTableAssembly) {
         SourceInfo sinfo = ((CubeTableAssembly) tassembly).getSourceInfo();
         sortRefsByLevel(refs, sinfo);
         tassembly.setProperty("noEmpty", "false");
      }

      tassembly.setPreRuntimeConditionList(null);
      ColumnSelection columns = tassembly.getColumnSelection(false);
      ColumnSelection columns2 = (ColumnSelection) columns.clone();
      columns.clear();
      SortInfo sinfo = new SortInfo();

      for(DataRef ref : refs) {
         ColumnRef column = VSUtil.getVSColumnRef((ColumnRef) ref);
         column = (ColumnRef) columns2.getAttribute(column.getName());

         if(column != null) {
            column.setVisible(true);
            columns.addAttribute(column);

            if(ref != null) {
               ((ColumnRef) ref).setDataType(column.getDataType());
            }

            SortRef sort = new SortRef(column);
            boolean dim = (ref.getRefType() & DataRef.CUBE_TIME_DIMENSION)
               == DataRef.CUBE_TIME_DIMENSION;
            int order = dim ? XConstants.SORT_NONE : XConstants.SORT_ASC;
            sort.setOrder(order);
            sinfo.addSort(sort);
         }
      }

      for(int i = 0; i < columns2.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns2.getAttribute(i);

         if(!columns.containsAttribute(column)) {
            column.setVisible(false);
            columns.addAttribute(column);
         }
      }

      tassembly.setColumnSelection(columns);
      tassembly.setDistinct(true);
      tassembly.setSortInfo(sinfo);

      return tassembly;
   }

   // check if dynamic (var) condition on table or sub tables
   protected static boolean isDynamicFilter(TableAssembly table, VariableTable vars) {
      TransformationDescriptor desc = new TransformationDescriptor();

      if(AbstractTransformer.isDynamicFilter(desc, table.getPreConditionList(), vars) ||
         AbstractTransformer.isDynamicFilter(desc, table.getPostConditionList(), vars))
      {
         return true;
      }

      // if calendar/rangeSlider is restricted by another selection, don't use the
      // MV meta data (min/max) for range. (60258)
      if(!isEmptyCondition(table.getPreRuntimeConditionList()) ||
         !isEmptyCondition(table.getPostRuntimeConditionList()))
      {
         return true;
      }

      if(table instanceof ComposedTableAssembly) {
         for(TableAssembly tbl : ((ComposedTableAssembly) table).getTableAssemblies(true)) {
            if(isDynamicFilter(tbl, vars)) {
               return true;
            }
         }
      }

      return false;
   }

   private static boolean isEmptyCondition(ConditionListWrapper conds) {
      return conds == null || conds.isEmpty();
   }

   /**
    * Sort the data refs by dimension level.
    */
   protected void sortRefsByLevel(DataRef[] refs, SourceInfo info) {
      if(info == null) {
         return;
      }

      // only selection tree has multiple refs
      XMLAUtil.sortRefsByLevel(refs, info.getPrefix(), info.getSource());
   }

   /**
    * Get the data.
    * @return the data of the query.
    */
   @Override
   public Object getData() throws Exception {
      VSAssemblyInfo info = getAssembly().getVSAssemblyInfo();

      // @temp yanie: if script contains binding setting, process script before
      // execute data
      if(info != null && info.getScript() != null && info.isScriptEnabled()) {
         String script = info.getScript();

         if(script.contains("query") || script.contains("fields")) {
            box.executeScript(getAssembly());
         }
      }

      TableAssembly tassembly = getTableAssembly(false);

      if(tassembly == null) {
         return null;
      }

      SelectionVSAssembly assembly = (SelectionVSAssembly) getAssembly();
      DataRef[] refs = assembly.getDataRefs();

      // optimization, check if we can get the data from meta data. This
      // avoids running a query everytime a selection list is refreshed.
      // If a query takes 1-2 seconds. With 10 selections on a viewsheet,
      // the query time can quickly add up.
      TableMetaData tmeta = box.getTableMetaData(assembly.getName());

      if(tmeta != null) {
         try {
            XTable tbl = tmeta.getColumnTable(assembly.getName(), refs);

            if(tbl != null) {
               return tbl;
            }
         }
         catch(MessageException | ConfirmException e) {
            throw e;
         }
         catch(Exception ex) {
            // handle cancelled query. If failed to get column table
            // from table metadata, get table lens by the query itself
            if(LOG.isDebugEnabled()) {
               LOG.warn("Failed to get column table from meta-data", ex);
            }
            else {
               LOG.warn("Failed to get column table from meta-data: {}", ex.getMessage());
            }
         }
      }

      return getTableLens(tassembly);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void refreshSelectionValue(XTable data,
                                     Map<String, Map<String, Collection<Object>>> allSelections,
                                     Map<String, Map<String, Collection<Object>>> appliedSelections,
                                     Map<String, Set<Object>> values,
                                     SelectionMeasureAggregation measureAggregation)
      throws Exception
   {
      // for Feature #26586, add execution breakdown record.

      ProfileUtils.addExecutionBreakDownRecord(getID(),
         ExecutionBreakDownRecord.UI_PROCESSING_CYCLE, args -> {
            checkAndRunCalcFieldMeasureQuery(measureAggregation);

            try {
               CoreTool.useDatetimeWithMillisFormat.set(Tool.isDatabricks((BindableVSAssembly) getAssembly()));
               refreshSelectionValue0(data, allSelections, appliedSelections, values,
                                      measureAggregation);
            }
            finally {
               CoreTool.useDatetimeWithMillisFormat.set(false);
            }
         });
   }

   /**
    * Refresh the selection value of a selection viewsheet assembly.
    *
    * @param data               the specified data.
    * @param allSelections      all selections including excluded selected values.
    * @param selections         selected values not including excluded values.
    * @param values             the specified associated value map.
    * @param measureAggregation selection measure aggregation.
    */
   protected void refreshSelectionValue0(XTable data,
                                         Map<String, Map<String, Collection<Object>>> allSelections,
                                         Map<String, Map<String, Collection<Object>>> selections,
                                         Map<String, Set<Object>> values,
                                         SelectionMeasureAggregation measureAggregation)
      throws Exception
   {
   }

   /**
    * Refresh the view selection value.
    */
   @Override
   public void refreshViewSelectionValue() throws Exception {
      // for Feature #26586, add execution breakdown record.

      ProfileUtils.addExecutionBreakDownRecord(getID(),
         ExecutionBreakDownRecord.UI_PROCESSING_CYCLE, args -> {
            refreshViewSelectionValue0();
         });

      //refreshViewSelectionValue0();
   }

   /**
    * Refresh the view selection value.
    */
   protected void refreshViewSelectionValue0() throws Exception {
      box.executeScript(getAssembly());
   }

   /**
    * Evaluate the query hits mv or not.
    */
   @Override
   public boolean hitsMV() throws Exception {
      if(!box.isMVEnabled()) {
         return false;
      }

      SelectionVSAssembly assembly = (SelectionVSAssembly) getAssembly();

      if(assembly == null) {
         return false;
      }

      String tname = assembly.getTableName();
      AssetEntry entry = box.getAssetEntry();
      XPrincipal user = (XPrincipal) box.getUser();
      return MVManager.getManager().findMV(entry, tname, user, null, box.getParentVsIds()) != null;
   }

   /**
    * Refresh the format of a selection value.
    * @param svalue the specified selection value.
    * @param ref the specified data ref.
    * @param vfmt the specified viewsheet format.
    * @param scriptable the specified composite viewsheet assembly scriptable.
    */
   protected final void refreshFormat(SelectionValue svalue, DataRef ref,
                                      VSCompositeFormat vfmt,
                                      CompositeVSAScriptable scriptable,
                                      boolean dynamic, int rtype)
      throws Exception
   {
      if(ref == null || vfmt == null) {
         return;
      }

      Object obj = getSelectionValueData(svalue, ref, rtype);

      // if the format doesn't have dynamic value, no need to make a copy
      if(dynamic) {
         vfmt = vfmt.clone();
         scriptable.setCellValue(obj);

         List<DynamicValue> dvals = vfmt.getDynamicValues();

         for(DynamicValue dval : dvals) {
            box.executeDynamicValue(dval, (VSAScriptable) scriptable);
         }

         vfmt.shrink();
      }

      Format fmt = TableFormat.getFormat(vfmt.getFormat(), vfmt.getFormatExtent(), locale);
      fmt = fmt == null ? svalue.getDefaultFormat() : fmt;

      // format value into a label
      if(fmt == null ||
         (fmt instanceof DateFormat && !(obj instanceof Date)))
      {
         String label = Tool.toString(getSelectionValueData(svalue, ref, rtype));

         if(!"".equals(label.trim())) {
            svalue.setLabel(label);
         }
      }
      else if(obj != null) {
         try {
            String label = null;

            try {
               label = fmt.format(obj);
            }
            catch(NumberFormatException ex) {
               label = Tool.toString(obj);
            }

            svalue.setLabel(label);
         }
         catch(Exception ex) {
            LOG.info("Failed to format label value: " + obj, ex);
         }
      }

      svalue.setFormat(vfmt);
   }

   /**
    * Get selection value data by the data type.
    */
   protected Object getSelectionValueData(SelectionValue svalue, DataRef ref, int rtype) {
      String dtype = ref.getDataType();
      String value = null;

      if((rtype & DataRef.CUBE) == 0) {
         value = svalue.getValue();
      }
      else {
         value = svalue.getOriginalLabel();
      }

      return Tool.getData(dtype, value, true);
   }

   /**
    * Copy parent format to specify value.
    */
   protected void copyUserDefinedFormat(VSFormat fmt, VSCompositeFormat pfmt) {
      // @by: ChrisSpagnoli bug1426141766159 2015-3-17
      // Removed code which (incorrectly) copied *CSS* formatting from pfmt
      // into *User-Defined* formatting in fmt.  Which would override any CSS
      // formatting that should have been applied directly to those elements.

      VSUtil.copyFormat(fmt, pfmt);
   }

   /**
    * Add detail calc to S_ table to runtime hit mv.
    */
   protected final void addCalcToSelectionTable(Worksheet ws) {
      SelectionVSAssembly assembly = (SelectionVSAssembly) getAssembly();
      TableAssembly sassembly =
         (TableAssembly) ws.getAssembly(assembly.getSelectionTableName());

      if(sassembly != null) {
         appendDetailCalcField(sassembly, assembly.getTableName());
      }
   }

   /**
    * Refresh measure format.
    */
   protected void refreshMeasureFormat() {
      VSAssembly assembly = getAssembly();
      FormatInfo finfo = assembly.getFormatInfo();

      for(int i = 0; finfo != null && i < 5; i++) {
         TableDataPath path = SelectionBaseVSAssemblyInfo.getMeasureTextPath(i);
         finfo.getFormat(path, false);
         path = SelectionBaseVSAssemblyInfo.getMeasureBarPath(i);
         initMearsureBarDefaultFormat(path, finfo);
         finfo.getFormat(path, false);
         path = SelectionBaseVSAssemblyInfo.getMeasureNBarPath(i);
         initMearsureNBarDefaultFormat(path, finfo);
         finfo.getFormat(path, false);
      }
   }

   private void initMearsureBarDefaultFormat(TableDataPath tpath, FormatInfo finfo) {
      VSCompositeFormat format = finfo.getFormat(tpath, false);
      VSCompositeFormat objfmt = finfo.getFormat(VSAssemblyInfo.OBJECTPATH);

      if(format == null || objfmt == null) {
         return;
      }

      if(!objfmt.getUserDefinedFormat().isForegroundValueDefined()) {
         format.getDefaultFormat().setForegroundValue(
            CategoricalColorFrame.COLOR_PALETTE[0].getRGB() + "");
      }
   }

   private void initMearsureNBarDefaultFormat(TableDataPath tpath, FormatInfo finfo) {
      VSCompositeFormat format = finfo.getFormat(tpath, false);
      VSCompositeFormat objfmt = finfo.getFormat(VSAssemblyInfo.OBJECTPATH);

      if(format == null || objfmt == null) {
         return;
      }

      if(!objfmt.getUserDefinedFormat().isForegroundValueDefined()) {
         format.getDefaultFormat().setForegroundValue(
            SOFT_RED.getRGB() + "");
      }
   }

   /**
    * @param sassembly  the assembly whose tables will be used to find the intersection
    * @param selections the table selections to find the intersection of
    *
    * @return the intersection of the matching table collections in <code>selections</code>
    */
   protected Map<String, Collection<Object>> getColumnMapIntersection(
      SelectionVSAssembly sassembly,
      Map<String, Map<String, Collection<Object>>> selections)
   {
      final HashMap<String, Collection<Object>> columnMap = new HashMap<>();

      final List<Map<String, Collection<Object>>> columnMaps = sassembly.getTableNames().stream()
         .map(selections::get)
         .filter(Objects::nonNull)
         .collect(Collectors.toList());

      if(columnMaps.size() > 0) {
         final Map<String, Collection<Object>> firstMap = columnMaps.get(0);
         final HashSet<String> columns = new HashSet<>(firstMap.keySet());

         columnMaps.stream()
            .skip(1) // skip first map
            .map(Map::keySet)
            .forEach(columns::retainAll); // keep mutual columns

         for(String col : columns) {
            final Collection<Object> selectionValues = firstMap.get(col);
            final Collection<Object> newValues;

            if(selectionValues instanceof CubeSelectionSet) {
               newValues = new CubeSelectionSet(selectionValues);
            }
            else if(selectionValues instanceof SelectionSet) {
               newValues = new SelectionSet(selectionValues);
            }
            else if(selectionValues instanceof Vector) {
               newValues = new Vector<>(selectionValues);
            }
            else if(selectionValues instanceof List) {
               newValues = new ArrayList<>(selectionValues);
            }
            else {
               newValues = new HashSet<>(selectionValues);
            }

            columnMaps.stream()
               .skip(1)
               .map((map) -> map.get(col))
               .forEach(newValues::retainAll); // keep mutual values

            if(!newValues.isEmpty()) {
               columnMap.put(col, newValues);
            }
         }
      }

      return columnMap;
   }

   /**
    * Sync the selection states in the assembly and the applied selection maps.
    */
   protected void syncSelections(SelectionVSAssembly assembly,
                                 Map<String, Map<String, Collection<Object>>> selections,
                                 boolean applied)
   {
      assembly.getSelection(selections, applied);

      for(String tableName : assembly.getTableNames()) {
         final Map<String, Collection<Object>> tableAppliedSelections = selections.get(tableName);

         if(tableAppliedSelections != null) {
            tableAppliedSelections.values().removeIf(Collection::isEmpty);
         }
      }
   }

   /**
    * Get the sorting type for the selection list.
    */
   protected int getSortType(SelectionBaseVSAssemblyInfo info) {
      return VSUtil.getSortType(info);
   }

   /**
    * Get column value from calculate ref.
    */
   private String getColumnValue(CalculateRef calculateRef) {
      if(calculateRef == null) {
         return "";
      }

      String columnValue = calculateRef.getName();
      String attribute = calculateRef.getAttribute();

      if(attribute != null) {
         String entity = calculateRef.getEntity();
         columnValue = (entity != null ?  entity + "." : "") + attribute;
      }

      return columnValue;
   }

   /**
    * Check if a calc field is selected as the measure for this selection assembly and if so then
    * run the calc field query in order to get the values for the measure
    */
   private void checkAndRunCalcFieldMeasureQuery(SelectionMeasureAggregation measureAggregation)
      throws Exception
   {
      Viewsheet origViewsheet = box.getViewsheet();
      Viewsheet vs = origViewsheet.clone();

      try {
         box.setViewsheet(vs, false);
         AbstractSelectionVSAssembly assembly = (AbstractSelectionVSAssembly) getAssembly();

         for(String btable : assembly.getTableNames()) {
            CalculateRef calc = vs.getCalcField(btable, assembly.getMeasure());

            // no calc as measure then don't need to do anything
            if(calc == null || calc.isBaseOnDetail()) {
               continue;
            }

            VSAggregateRef vsAggregateRef = new VSAggregateRef();

            if((calc.getRefType() & DataRef.AGG_CALC) == DataRef.AGG_CALC) {
               vsAggregateRef.setDataRef(calc);
               vsAggregateRef.setColumnValue(calc.getName());
               vsAggregateRef.setRefType(calc.getRefType());
            }
            else {
               vsAggregateRef.setDataRef(calc);
               vsAggregateRef.setColumnValue(getColumnValue(calc));
               vsAggregateRef.setFormulaValue(assembly.getFormula());
               vsAggregateRef.setRefType(calc.getRefType());
            }

            List<VSDimensionRef> dimRefs = new ArrayList<>();

            // need to run a separate query for every data ref
            for(DataRef ref : assembly.getDataRefs()) {
               VSDimensionRef vsDimensionRef = new VSDimensionRef();
               vsDimensionRef.setDataRef(ref);
               vsDimensionRef.setGroupColumnValue(ref.getName());
               vsDimensionRef.setDataType(ref.getDataType());
               vsDimensionRef.setDateLevel(DateRangeRef.NONE);
               dimRefs.add(vsDimensionRef);

               VSCrosstabInfo cinfo = new VSCrosstabInfo();
               cinfo.setDesignAggregates(new DataRef[]{ vsAggregateRef });
               cinfo.setDesignRowHeaders(dimRefs.toArray(new DataRef[0]));

               // always invisible crosstab
               CrosstabVSAssembly crosstab = new CrosstabVSAssembly(
                  getViewsheet(), CalcTableVSAQuery.TEMP_ASSEMBLY_PREFIX + assembly.getName() + "_Crosstab") {
                  @Override
                  protected VSAssemblyInfo createInfo() {
                     return new CrosstabVSAssemblyInfo() {
                        @Override
                        public boolean isVisible(boolean print) {
                           return false;
                        }
                     };
                  }

                  @Override
                  public AssemblyRef[] getDependedWSAssemblies() {
                     return assembly.getDependedWSAssemblies();
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

               // get the source info from selection list
               SourceInfo sourceInfo = new SourceInfo();
               sourceInfo.setType(SourceInfo.MODEL);
               sourceInfo.setSource(btable);

               crosstab.setSourceInfo(sourceInfo);
               crosstab.setVSCrosstabInfo(cinfo);

               try {
                  vs.removeAssembly(crosstab.getName(), false);
                  vs.addAssembly(crosstab, false, false);

                  // clear the selections of selection assemblies with the same data ref including
                  // this assembly so that we get the measures for all the values
                  for(Assembly vsAssembly : vs.getAssemblies()) {
                     if(vsAssembly instanceof AssociatedSelectionVSAssembly) {
                        AssociatedSelectionVSAssembly selectionVSAssembly = (AssociatedSelectionVSAssembly) vsAssembly;
                        DataRef[] selectedRefs = selectionVSAssembly.getDataRefs();

                        if(selectedRefs != null && selectedRefs.length > 0 &&
                           selectedRefs[0].equals(assembly.getDataRefs()[0]))
                        {
                           selectionVSAssembly.resetSelection();
                        }
                     }
                     else if(vsAssembly instanceof CrosstabVSAssembly) {
                        CrosstabVSAssembly cross = (CrosstabVSAssembly) vsAssembly;
                        ((CrosstabVSAssemblyInfo) cross.getInfo()).setDrillFilterInfo(
                           new DrillFilterInfo());
                     }
                     else if(vsAssembly instanceof ChartVSAssembly) {
                        ChartVSAssembly chart = (ChartVSAssembly) vsAssembly;
                        ((ChartVSAssemblyInfo) chart.getInfo()).setDrillFilterInfo(
                           new DrillFilterInfo());
                     }
                  }

                  // to make sure that the correct conditions are applied when executing the query
                  box.refreshRuntimeConditionList(btable, false, new HashSet<>());
                  // generate runtime aggregates and row headers
                  box.updateAssembly(crosstab.getName());

                  CrosstabVSAQuery cquery = new CrosstabVSAQuery(box, crosstab.getName(), false);
                  TableLens lens = cquery.getTableLens(false);

                  if(lens != null) {
                     final Map<SelectionSet.Tuple, Formula> formulaMap =
                        measureAggregation.getFormulas();
                     int row = 1;

                     while(lens.moreRows(row)) {
                        Object[] values = new Object[dimRefs.size()];

                        for(int i = 0; i < dimRefs.size(); i++) {
                           values[i] = lens.getObject(row, i);

                           if(StringUtils.isEmpty(values[i])) {
                              values[i] = null;
                           }

                           values[i] = SelectionSet.normalize(values[i]);
                        }

                        SelectionSet.Tuple setval = new SelectionSet.Tuple(values, dimRefs.size());
                        Formula formula = formulaMap.get(setval);

                        if(formula == null) {
                           formula = new NoneFormula();
                           formulaMap.put(setval, formula);
                        }

                        formula.addValue(lens.getObject(row, dimRefs.size()));
                        row++;
                     }
                  }
               }
               finally {
                  vs.removeAssembly(crosstab.getName(), false);
               }
            }
         }

         // update bounds once we get all the values
         measureAggregation.updateBounds();
      }
      finally {
         box.setViewsheet(origViewsheet, false);
      }
   }

   private static final Color SOFT_RED = new Color(0xFF4040);
   private static final Logger LOG = LoggerFactory.getLogger(AbstractSelectionVSAQuery.class);
}
