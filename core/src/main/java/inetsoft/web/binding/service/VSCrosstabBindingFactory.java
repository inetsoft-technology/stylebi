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
package inetsoft.web.binding.service;

import inetsoft.report.StyleConstants;
import inetsoft.report.composition.graph.calc.*;
import inetsoft.report.filter.CrossTabFilterUtil;
import inetsoft.report.internal.binding.SummaryAttr;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.web.binding.model.*;
import inetsoft.web.binding.model.graph.CalculateInfo;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.model.table.CrosstabOptionInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Stream;

@Component
public class VSCrosstabBindingFactory
   extends VSBindingFactory<CrosstabVSAssembly, CrosstabBindingModel>
{
   @Autowired
   public VSCrosstabBindingFactory(DataRefModelFactoryService refModelService) {
      this.refModelService = refModelService;
   }

   /**
    * All crosstab aggregates share same percentage direction, sync percent direction for
    * percent calc by the lastest percentageDirection.
    */
   public static void syncPercentCalcs(VSCrosstabInfo vinfo) {
      if(vinfo == null || vinfo.getAggregates().length == 0) {
         return;
      }

      String percentageDirection = vinfo.getPercentageByValue();
      DataRef[] aggrs = vinfo.getAggregates();

      for(int i = 0; i < aggrs.length; i++) {
         if(!(aggrs[i] instanceof VSAggregateRef)) {
            continue;
         }

         Calculator calc = ((VSAggregateRef) aggrs[i]).getCalculator();

         if(calc instanceof PercentCalc) {
            PercentCalc pcalc = (PercentCalc) calc;
            pcalc.setPercentageByValue(percentageDirection);
         }
      }
   }

   /**
    * Init calcutor default column for crosstab.
    */
   public static void initCalculateInfo(boolean hasRow, boolean hasCol, Calculator calc) {
      if(calc instanceof MovingCalc) {
         if(hasRow) {
            ((MovingCalc) calc).setInnerDim(AbstractCalc.ROW_INNER);
         }
         else {
            ((MovingCalc) calc).setInnerDim(AbstractCalc.COLUMN_INNER);
         }
      }
      else if(calc instanceof RunningTotalCalc) {
         String breakBy = ((RunningTotalCalc) calc).getBreakBy();

         if(breakBy != null && !breakBy.isEmpty()) {
            return;
         }

         if(hasRow) {
            ((RunningTotalCalc) calc).setBreakBy(AbstractCalc.ROW_INNER);
         }
         else {
            ((RunningTotalCalc) calc).setBreakBy(AbstractCalc.COLUMN_INNER);
         }
      }
      else if(calc instanceof ValueOfCalc) {
         if(((ValueOfCalc) calc).getColumnName() != null) {
            return;
         }

         if(hasRow) {
            ((ValueOfCalc) calc).setColumnName(AbstractCalc.ROW_INNER);
         }
         else {
            ((ValueOfCalc) calc).setColumnName(AbstractCalc.COLUMN_INNER);
         }
      }
      else if(calc instanceof PercentCalc) {
         if(((PercentCalc) calc).getColumnName() != null) {
            return;
         }

         int direction = hasCol ? XConstants.PERCENTAGE_BY_COL : XConstants.PERCENTAGE_BY_ROW;
         ((PercentCalc) calc).setPercentageByValue(direction + "");
      }
   }

   @Override
   public Class<CrosstabVSAssembly> getAssemblyClass() {
      return CrosstabVSAssembly.class;
   }

   /**
    * Creates a new model instance for the specified assembly.
    *
    * @param assembly the assembly.
    *
    * @return a new model.
    */
   @Override
   public CrosstabBindingModel createModel(CrosstabVSAssembly assembly) {
      CrosstabBindingModel model = new CrosstabBindingModel();
      model.setType("crosstab");
      VSCrosstabInfo crossInfo = assembly.getVSCrosstabInfo();

      if(crossInfo == null) {
         return model;
      }

      SortOptionModel sortOptionModel = new SortOptionModel(getAggregateRefs(assembly));
      DataRef[] rows = crossInfo.getRowHeaders();

      for(int i = 0; i < rows.length; i++) {
         DataRef row = rows[i];
         BDimensionRefModel bdRef =
            (BDimensionRefModel) refModelService.createDataRefModel(row);
         bdRef.setSortOptionModel(sortOptionModel);
         model.addRow(bdRef);
         model.getSuppressGroupTotal().put(row.getName() + ":rows" + i,
            !"true".equals(((VSDimensionRef) row).getSubTotalVisibleValue()));
      }

      DataRef[] cols = crossInfo.getColHeaders();

      for(int i = 0; i < cols.length; i++) {
         DataRef col = cols[i];
         BDimensionRefModel bdRef =
            (BDimensionRefModel) refModelService.createDataRefModel(col);
         bdRef.setSortOptionModel(sortOptionModel);
         model.addCol(bdRef);
         model.getSuppressGroupTotal().put(col.getName() + ":cols" + i,
            !"true".equals(((VSDimensionRef) col).getSubTotalVisibleValue()));
      }

      DataRef[] aggs = crossInfo.getAggregates();
      ColumnSelection columnSelection = VSUtil.getColumnsForCalc(assembly);
      boolean cube = isCube(assembly);

      for(DataRef agg : aggs) {
         if(VSUtil.isFake(agg)) {
            continue;
         }

         BAggregateRefModel refModel =
            (BAggregateRefModel) refModelService.createDataRefModel(agg);
         refModel.setBuildInCalcs(getBuildInCalcs(crossInfo, cube));
         boolean aggregateStatus = agg.getRefType() == DataRef.AGG_EXPR;

         if(!aggregateStatus) {
            for(int i = columnSelection.getAttributeCount() - 1; i >= 0; i--) {
               if(VSUtil.isAggregateCalc(columnSelection.getAttribute(i)) &&
               refModel.getName().equals(columnSelection.getAttribute(i).getName()))
               {
                  aggregateStatus = true;
                  break;
               }
            }
         }

         FormulaOptionModel fOptionModel = new FormulaOptionModel(aggregateStatus);
         refModel.setSecondaryColumnValue(((VSAggregateRef) agg).
                                              getSecondaryColumnValue());
         refModel.setFormulaOptionModel(fOptionModel);
         model.addAggregate(refModel);
      }

      model.setOption(new CrosstabOptionInfo(assembly));
      model.setHasDateComparison(assembly.getCrosstabInfo().getDateComparisonRef() != null);

      return model;
   }

   /**
    * Update a crosstab vs assembly.
    *
    * @param model the specified crosstab binding model.
    * @param assembly the specified crosstab vs assembly.
    *
    * @return the crosstab vs assembly.
    */
   @Override
   public CrosstabVSAssembly updateAssembly(CrosstabBindingModel model,
                                            CrosstabVSAssembly assembly)
   {
      CrosstabVSAssemblyInfo info = (CrosstabVSAssemblyInfo) assembly.getInfo();
      VSCrosstabInfo crossInfo = info.getVSCrosstabInfo();
      DataRef[] rows = new DataRef[model.getRows().size()];
      DataRef[] cols = new DataRef[model.getCols().size()];
      DataRef[] dataRefRows = crossInfo.getRowHeaders();
      DataRef[] dataRefCols = crossInfo.getColHeaders();
      DataRef[] aggregates = new DataRef[model.getAggregates().size()];
      Map<String, VSAggregateRef> fixAggVariableInDimMap = new HashMap<>();
      Map<String, String> fixDimVariableInAggMap = new HashMap<>();
      DataRef[] aggs = crossInfo.getAggregates();
      List<XDimensionRef> dims = getAllDimensions(crossInfo);

      for(int i = 0; i < rows.length; i++) {
         rows[i] = model.getRows().get(i).createDataRef();
         Boolean suppressGroupTotal =
            model.getSuppressGroupTotal().get(rows[i].getName() + ":rows" + i);

         if(suppressGroupTotal != null) {
            ((VSDimensionRef) rows[i]).setSubTotalVisibleValue(!suppressGroupTotal + "");
         }

         VSUtil.fixVariableAggInDim(fixAggVariableInDimMap, aggs, rows[i]);

         if(i < dataRefRows.length) {
            updateDataRefGroupInfo((VSDimensionRef) rows[i], (VSDimensionRef) dataRefRows[i]);
         }
      }

      for(int i = 0; i < cols.length; i++) {
         cols[i] = model.getCols().get(i).createDataRef();
         Boolean suppress = model.getSuppressGroupTotal().get(cols[i].getName() + ":cols" + i);
         suppress = suppress == null || suppress;
         ((VSDimensionRef) cols[i]).setSubTotalVisibleValue(!suppress + "");

         if(i < dataRefCols.length) {
            updateDataRefGroupInfo((VSDimensionRef) cols[i], (VSDimensionRef) dataRefCols[i]);
         }
      }

      for(int i = 0; i < aggregates.length; i++) {
         aggregates[i] = createAggregateRef(model.getAggregates().get(i));
         VSUtil.fixVariableDimInAgg(fixDimVariableInAggMap, dims, aggregates[i]);
      }

      updateDcRuntimeRefSort(crossInfo.getRuntimeDateComparisonRefs(),
         crossInfo.getDesignRowHeaders(), rows);
      updateDcRuntimeRefSort(crossInfo.getRuntimeDateComparisonRefs(),
         crossInfo.getDesignColHeaders(), cols);
      crossInfo.setDesignRowHeaders(rows);
      crossInfo.setDesignColHeaders(cols);
      crossInfo.setDesignAggregates(aggregates);

      CrosstabOptionInfo option = model.getOption();
      crossInfo.setPercentageByValue(option.getPercentageByValue());
      crossInfo.setRowTotalVisibleValue(option.getRowTotalVisibleValue());
      crossInfo.setColTotalVisibleValue(option.getColTotalVisibleValue());
      syncPercentCalcs(crossInfo);
      VSUtil.updateCalculate(crossInfo, null);
      return assembly;
   }

   private void updateDcRuntimeRefSort(DataRef[] dcRuntimeRefs, DataRef[] oldRefs,
                                       DataRef[] newRefs)
   {
      oldRefs = oldRefs == null ? new DataRef[0] : oldRefs;
      newRefs = newRefs == null ? new DataRef[0] : newRefs;

      if(oldRefs.length != newRefs.length || dcRuntimeRefs == null || dcRuntimeRefs.length == 0) {
         return;
      }

      for(int i = 0; i < oldRefs.length; i++) {
         if(!(oldRefs[i] instanceof VSDimensionRef) || !(newRefs[i] instanceof VSDimensionRef)) {
            return;
         }

         VSDimensionRef oldDim = (VSDimensionRef) oldRefs[i];
         VSDimensionRef newDim = (VSDimensionRef) newRefs[i];

         if(!Tool.equals(oldDim.getFullName(), newDim.getFullName())) {
            return;
         }

         if(oldDim.getOrder() == newDim.getOrder() &&
            Tool.equals(oldDim.getSortByColValue(), newDim.getSortByColValue()))
         {
            continue;
         }

         for(DataRef dcRuntimeRef : dcRuntimeRefs) {
            if(!(dcRuntimeRef instanceof VSDimensionRef)) {
               continue;
            }

            if(!Tool.equals(oldDim.getName(), dcRuntimeRef.getName())) {
               continue;
            }

            ((VSDimensionRef) dcRuntimeRef).setOrder(newDim.getOrder());
            ((VSDimensionRef) dcRuntimeRef).setSortByColValue(newDim.getSortByColValue());
            ((VSDimensionRef) dcRuntimeRef).setSortByCol(newDim.getSortByColValue());
         }
      }
   }

   private List<XDimensionRef> getAllDimensions(VSCrosstabInfo cinfo) {
      List<XDimensionRef> list = new ArrayList<>();
      DataRef[] rows = cinfo.getRuntimeRowHeaders();
      DataRef[] cols = cinfo.getRuntimeColHeaders();

      Stream.concat(Stream.of(rows), Stream.of(cols)).forEach(ref -> {
         if(ref instanceof XDimensionRef) {
            list.add((XDimensionRef) ref);
         }
      });

      return list;
   }

   private DataRef findColumn(ColumnSelection cols, String name) {
      if(name == null) {
         return null;
      }

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         ColumnRef col = (ColumnRef) cols.getAttribute(i);
         DataRef ref = col.getDataRef();

         if(name.equals(ref.getName())) {
            return ref;
         }
      }

      return null;
   }

   private VSAggregateRef createAggregateRef(BAggregateRefModel field) {
      VSAggregateRef aggr = (VSAggregateRef) field.createDataRef();
      DataRef ref = aggr.getDataRef();

      if(field.getFormula() == null) {
         aggr.setFormula(AggregateFormula.getFormula(getDefaultFormula(ref)));
      }

      Calculator calc = aggr.getCalculator();

      // calculate percent calc is too expensive when grand total\group total is not visible,
      // so using the old percent of logic to do percentcalc.
      if(calc instanceof PercentCalc) {
         PercentCalc pcalc = (PercentCalc) calc;
         int level = pcalc.getLevel();

         if(level == PercentCalc.GRAND_TOTAL) {
            aggr.setPercentageOptionValue(StyleConstants.PERCENTAGE_OF_GRANDTOTAL + "");
         }
         else if(level == PercentCalc.SUB_TOTAL) {
            aggr.setPercentageOptionValue(StyleConstants.PERCENTAGE_OF_GROUP + "");
         }
         else {
            aggr.setPercentageOptionValue(StyleConstants.PERCENTAGE_NONE + "");
         }
      }
      else {
         aggr.setPercentageOptionValue(StyleConstants.PERCENTAGE_NONE + "");
      }

      return aggr;
   }

   private String getDefaultFormula(DataRef ref) {
      int refType =  ref.getRefType();

      // measure?
      if(refType == AbstractDataRef.MEASURE) {
         String defFormula = ref.getDefaultFormula();

         if(Objects.equals(defFormula, "None")) {
            return SummaryAttr.NONE_FORMULA;
         }

         return defFormula;
      }
      // dimesion?
      else if(refType == AbstractDataRef.DIMENSION) {
         return SummaryAttr.COUNT_FORMULA;
      }

      String type= ref.getDataType();

      if(AssetUtil.isNumberType(type)) {
         return SummaryAttr.SUM_FORMULA;
      }

      return SummaryAttr.COUNT_FORMULA;
   }

   public boolean changeBoolean(String str) {
      return "true".equals(str);
   }

   private boolean isCube(CrosstabVSAssembly assembly) {
      SourceInfo sinfo = assembly.getSourceInfo();
      String source = sinfo == null ? null : sinfo.getSource();
      return source != null && source.startsWith("___inetsoft_cube_");
   }

   private List<Map<String, String>> getAggregateRefs(CrosstabVSAssembly assembly) {
      VSCrosstabInfo cinfo = assembly.getVSCrosstabInfo();
      DataRef[] dataRefs = cinfo.getRuntimeAggregates();
      List<Map<String, String>> aggregateRefs = new ArrayList<>();
      boolean cube = isCube(assembly);

      for(DataRef ref: dataRefs) {
         VSAggregateRef aggr = (VSAggregateRef) ref;
         DataRef col = aggr.getDataRef();

         if((col instanceof CalculateRef) && ((CalculateRef) col).isFake()) {
            continue;
         }

         Calculator calculator = ((VSAggregateRef) ref).getCalculator();
         boolean calcSupportSort = calculator != null && calculator.supportSortByValue();
         String toView = aggr.toView(calcSupportSort);
         boolean find = aggregateRefs.stream().anyMatch((agg) -> Tool.equals(agg.get("label"), toView));

         if(find) {
            continue;
         }

         Map<String, String> map = new HashMap<>();
         BAggregateRefModel aggregateRefModel = new BAggregateRefModel((XAggregateRef) ref);
         aggregateRefModel.setBuildInCalcs(getBuildInCalcs(cinfo, cube));
         map.put("label", toView);
         map.put("value", CrossTabFilterUtil.getCrosstabRTAggregateName(aggr, calcSupportSort));

         aggregateRefs.add(map);
      }

      return aggregateRefs;
   }

   private List<CalculateInfo> getBuildInCalcs(VSCrosstabInfo cinfo, boolean cube) {
      List<CalculateInfo> list = new ArrayList<>();
      Calculator[] calcs = AbstractCalc.getDefaultCalcs(findDateDimensions(cinfo));

      for(int i = 0; i < calcs.length; i++) {
         if(cube && calcs[i] != null && !(calcs[i] instanceof PercentCalc) &&
            !(calcs[i] instanceof AbstractCalc.CustomCalc))
         {
            continue;
         }

         initCalculateInfo(cinfo, calcs[i]);
         CalculateInfo calcInfo = CalculateInfo.createCalcInfo(calcs[i]);
         list.add(calcInfo);
      }

      return list;
   }

   /**
    * Init calcutor default column for crosstab.
    */
   private void initCalculateInfo(VSCrosstabInfo info, Calculator calc) {
      initCalculateInfo(containsRowHeader(info),
         containsColHeader(info), calc);
   }

   private boolean containsRowHeader(VSCrosstabInfo info) {
      return info.getRowHeaders().length != 0;
   }

   private boolean containsColHeader(VSCrosstabInfo info) {
      return info.getColHeaders().length != 0;
   }

   /**
    * Find year dimension in binding refs.
    */
   private XDimensionRef[] findDateDimensions(VSCrosstabInfo cinfo) {
      List<XDimensionRef> list = new ArrayList<>();
      findDateDimensions(cinfo.getRuntimeRowHeaders(), list);
      findDateDimensions(cinfo.getRuntimeColHeaders(), list);
      return list.toArray(new XDimensionRef[list.size()]);
   }

   private void findDateDimensions(DataRef[] refs, List<XDimensionRef> list) {
      if(refs == null || refs.length == 0) {
         return;
      }

      Arrays.stream(refs).forEach(ref -> {
         if(XUtil.isDateDim(ref) && !((VSDimensionRef) ref).isVariable()) {
            list.add((XDimensionRef) ref);
         }
      });
   }

   private void updateDataRefGroupInfo(VSDimensionRef nref, VSDimensionRef oref) {
      nref.setNamedGroupInfo(
         (XNamedGroupInfo) Tool.clone(oref.getNamedGroupInfo()));
      nref.setGroupType(oref.getGroupType());
      nref.setDataRef((DataRef) Tool.clone(oref.getDataRef()));
   }

   private final DataRefModelFactoryService refModelService;
}
