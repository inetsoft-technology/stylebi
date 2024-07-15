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
package inetsoft.web.binding.handler;

import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.sree.AnalyticRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CrosstabTree;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.handler.VSDrillHandler;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.apache.commons.lang.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class VSCrosstabBindingHandler {

   @Autowired
   public VSCrosstabBindingHandler(VSDrillHandler drillHandler,
                                   AnalyticRepository analyticRepository)
   {
      this.drillHandler = drillHandler;
      this.analyticRepository = analyticRepository;
   }

   /**
    * Process the drag & drop action.
    */
   public void addRemoveColumns(RuntimeViewsheet rvs, CrosstabVSAssembly assembly,
                                String removeType, int removeIndex, String addType, int addIndex,
                                boolean replace, CommandDispatcher dispatcher, String linkUri)
      throws Exception
   {
      VSCrosstabInfo cinfo = assembly.getVSCrosstabInfo();
      DataRef ref = getDataRef(assembly, removeType, removeIndex);
      markColumn(removeIndex, getTargetArr(cinfo, removeType));
      addDataRef(rvs, assembly, ref, addType, addIndex, replace, dispatcher, linkUri, removeType);
      assembly.getCrosstabInfo().clearHiddenColumns();

      // don't clear alias if moving between rows/cols.
      if(!Objects.equals(removeType, addType) && (!isRowCol(removeType) || !isRowCol(addType))) {
         ClearTableHeaderAliasHandler.clearHeaderAlias(ref, assembly.getFormatInfo(), removeIndex);
      }
   }

   private static boolean isRowCol(String type) {
      return "rows".equals(type) || "cols".equals(type);
   }

   /**
    * Process the drag & drop from tree action.
    */
   public void addColumns(CrosstabVSAssembly assembly, AssetEntry[] entries,
                          String addType, int addIndex, boolean replace,
                          RuntimeViewsheet rvs, CommandDispatcher dispatcher,
                          String linkUri)
      throws Exception
   {
      VSCrosstabInfo cinfo = assembly.getVSCrosstabInfo();
      SourceInfo sinfo = assembly.getSourceInfo();
      CrosstabTree cTree = assembly.getCrosstabTree();

      if(cinfo == null) {
         cinfo = new VSCrosstabInfo();
         assembly.setVSCrosstabInfo(cinfo);
      }

      List<DataRef> refs = new ArrayList<>();

      for(int i = entries.length - 1; i >= 0; i--) {
         DataRef ref = createDataRef(assembly, entries[i], sinfo, rvs);
         DataRef[] targetRefs = getTargetArr(cinfo, addType);

         if(ref instanceof VSDimensionRef && ((VSDimensionRef) ref).isDateTime()) {
            setDateLevel((VSDimensionRef) ref, targetRefs, addIndex + i);
         }

         if(needConvert(addType, ref)) {
            String convertColumnValue = null;
            AggregateInfo aggregateInfo = assembly.getAggregateInfo();

            if(ref instanceof VSDimensionRef) {
               convertColumnValue = ((VSDimensionRef) ref).getGroupColumnValue();
               VSEventUtil.fixAggInfoByConvertRef(aggregateInfo, VSEventUtil.CONVERT_TO_MEASURE,
                  convertColumnValue);
               ref = aggregateInfo.getAggregate(ref);
            }
            else if(ref instanceof VSAggregateRef) {
               convertColumnValue = ((VSAggregateRef) ref).getColumnValue();
               VSEventUtil.fixAggInfoByConvertRef(aggregateInfo, VSEventUtil.CONVERT_TO_DIMENSION,
                  convertColumnValue);
               ref = aggregateInfo.getGroup(convertColumnValue);
            }
         }

         refs.add(ref);
      }

      for(DataRef ref : refs) {
         addDataRef(rvs, assembly, ref, addType, addIndex, replace, dispatcher, linkUri);
      }

      assembly.getCrosstabInfo().clearHiddenColumns();
   }

   public List<String> getNeedConvertRefNames(CrosstabVSAssembly assembly, AssetEntry[] entries,
                                              String addType, RuntimeViewsheet rvs)
      throws Exception
   {
      SourceInfo sinfo = assembly.getSourceInfo();
      List<String> needConvertRefNames = new ArrayList<>();

      for(int i = entries.length - 1; i >= 0; i--) {
         DataRef ref = createDataRef(assembly, entries[i], sinfo, rvs);

         if(needConvert(addType, ref)) {
            if(ref instanceof VSDimensionRef) {
               needConvertRefNames.add(((VSDimensionRef) ref).getGroupColumnValue());
            }
            else if(ref instanceof VSAggregateRef) {
               needConvertRefNames.add(((VSAggregateRef) ref).getColumnValue());
            }
         }
      }

      return needConvertRefNames;
   }

   private boolean needConvert(String addType, DataRef ref) {
      String[] dimType = new String[]{ "rows", "cols"};
      String[] meaType = new String[]{ "aggregates" };

      return ref instanceof VSDimensionRef && ArrayUtils.contains(meaType, addType) ||
         ref instanceof VSAggregateRef && ArrayUtils.contains(dimType, addType);
   }

   public List<String> getConvertedEntriesPath(AssetEntry[] entries, String addType) {
      List<String> convertedPaths = new ArrayList<>();
      Catalog catalog = Catalog.getCatalog();

      for(AssetEntry entry : entries) {
         boolean isDim = isDimension(entry);

         if(isDim) {
            if("aggregates".equals(addType)) {
               convertedPaths.add(entry.getPath().replace("/" + catalog.getString("Dimensions") + "/",
                  "/" + catalog.getString("Measures") + "/"));
            }
            else {
               convertedPaths.add(entry.getPath());
            }
         }
         else {
            if(!"aggregates".equals(addType)) {
               convertedPaths.add(entry.getPath().replace("/" + catalog.getString("Measures") + "/",
                  "/" + catalog.getString("Dimensions") + "/"));
            }
            else {
               convertedPaths.add(entry.getPath());
            }
         }
      }

      return convertedPaths;
   }

   /**
    * Process the drag & drop to tree action.
    */
   public DataRef removeColumns(RuntimeViewsheet rvs, CrosstabVSAssembly assembly,
                             String removeType, int removeIndex,
                             CommandDispatcher dispatcher, String linkUri)
      throws Exception
   {
      VSCrosstabInfo cinfo = assembly.getVSCrosstabInfo();
      DataRef removeRef = markColumn(removeIndex, getTargetArr(cinfo, removeType));
      trimColumns(cinfo);

      if(removeRef != null) {
         drillHandler.removeDrillFilter(rvs, assembly, removeRef, dispatcher, linkUri);
         ClearTableHeaderAliasHandler.clearHeaderAlias(
            removeRef, assembly.getFormatInfo(), removeIndex);

         if("aggregates".equals(removeType)) {
            fixSortByValue(cinfo.getRowHeaders(), removeRef, cinfo.getAggregates());
            fixSortByValue(cinfo.getColHeaders(), removeRef, cinfo.getAggregates());
         }
      }

      assembly.getCrosstabInfo().clearHiddenColumns();

      return removeRef;
   }

   private void fixSortByValue(DataRef[] rows, DataRef ref, DataRef[] aggs) {
      if(!(ref instanceof VSAggregateRef) || aggs.length == 0) {
         return;
      }

      String name = ((VSAggregateRef) ref).getFullName();
      String aggName = ((VSAggregateRef) aggs[0]).getFullName();

      for(int i = 0; i < rows.length; i++) {
         if(rows[i] instanceof VSDimensionRef) {
            VSDimensionRef dim = (VSDimensionRef) rows[i];

            if(Tool.equals(name, dim.getSortByColValue())) {
               dim.setSortByColValue(aggName);
            }

            if(Tool.equals(name, dim.getRankingColValue())) {
               dim.setRankingColValue(aggName);
            }
         }
      }
   }

   private int getBindingColumnType(String type) {
      return "aggregates".equals(type) ? DataRef.MEASURE : DataRef.DIMENSION;
   }

   private void addDataRef(RuntimeViewsheet rvs, CrosstabVSAssembly assembly, DataRef ref,
                           String type, int index, boolean replace,
                           CommandDispatcher dispatcher, String linkUri)
      throws Exception
   {
      addDataRef(rvs, assembly, ref, type, index, replace, dispatcher, linkUri, null);
   }

   private void addDataRef(RuntimeViewsheet rvs, CrosstabVSAssembly assembly, DataRef ref,
                           String type, int index, boolean replace,
                           CommandDispatcher dispatcher, String linkUri, String removeType)
      throws Exception
   {
      VSCrosstabInfo cinfo = assembly.getVSCrosstabInfo();
      CrosstabTree cTree = assembly.getCrosstabTree();

      boolean adjustDiffType = false;

      if(removeType != null) {
         adjustDiffType = getBindingColumnType(removeType) != getBindingColumnType(type);
      }

      // mark the existed column to null. only mark column for dimensions not date type.
      // date dimensions and aggregate can support multi column.
      if(ref instanceof VSDimensionRef && !((VSDimensionRef) ref).isDateTime() || adjustDiffType) {
         markExistedColumn(ref, cinfo);
      }

      DataRef[] targetRefs = getTargetArr(cinfo, type);

      if(targetRefs == null) {
         return;
      }

      DataRef removeRef = null;

      //mark replaced column
      if(replace) {
         removeRef = markColumn(index, targetRefs);
      }

      List<DataRef> list = new ArrayList<>();

      if(index < 0 || index > targetRefs.length) {
         return;
      }

      Collections.addAll(list, targetRefs);

      ref = convertRef(ref, type);

      if(index >= targetRefs.length) {
         list.add(ref);
      }
      else {
         list.add(index, ref);
      }

      DataRef[] newArr = list.stream()
         // remove fake aggregate (which is added when aggrs is empty).
         .filter(a -> !(a instanceof VSAggregateRef &&
            ((VSAggregateRef) a).getDataRef() instanceof CalculateRef &&
            ((CalculateRef) ((VSAggregateRef) a).getDataRef()).isFake()))
         .toArray(DataRef[]::new);
      setTargetArr(cinfo, type, newArr);
      trimColumns(cinfo);

      if(removeRef != null) {
         drillHandler.removeDrillFilter(rvs, assembly, removeRef, dispatcher, linkUri);
         int colIndex = index;

         if(VSUtil.getPeriodCalendar(assembly.getViewsheet(), assembly.getTableName()) != null) {
            colIndex++;
         }

         ClearTableHeaderAliasHandler.clearHeaderAlias(removeRef, assembly.getFormatInfo(), colIndex);

         if(removeRef instanceof VSAggregateRef && ref instanceof VSAggregateRef) {
            syncSortByColumn(cinfo.getRowHeaders(), (VSAggregateRef) ref, (VSAggregateRef) removeRef);
            syncSortByColumn(cinfo.getColHeaders(), (VSAggregateRef) ref, (VSAggregateRef) removeRef);
         }
      }
   }

   private void syncSortByColumn(DataRef[] groups, VSAggregateRef nref, VSAggregateRef oref) {
      if(nref == null || oref == null || groups == null || groups.length == 0) {
         return;
      }

      VSDimensionRef dim = null;

      for(int i = 0; i < groups.length; i++) {
         if(!(groups[i] instanceof VSDimensionRef)) {
            continue;
         }

         dim = (VSDimensionRef) groups[i];

         if(Tool.equals(dim.getSortByCol(), oref.getFullName(false))) {
            dim.setSortByColValue(nref.getFullName(false));
         }

         if(Tool.equals(dim.getRankingCol(), oref.getFullName(false))) {
            dim.setRankingColValue(nref.getFullName(false));
         }
      }
   }

   private DataRef convertRef(DataRef ref, String type) {
      if(ref instanceof VSAggregateRef && ("rows".equals(type) || "cols".equals(type))) {
         VSAggregateRef agg = (VSAggregateRef) ref;
         VSDimensionRef dim = new VSDimensionRef();
         dim.setGroupColumnValue(agg.getColumnValue());

         return dim;
      }
      else if(ref instanceof VSDimensionRef && "aggregates".equals(type)) {
         VSDimensionRef dim = (VSDimensionRef) ref;
         VSAggregateRef agg = new VSAggregateRef();
         agg.setColumnValue(dim.getGroupColumnValue());
         agg.setFormula(AggregateFormula.getDefaultFormula(dim.getDataType()));

         return agg;
      }

      return ref;
   }

   private void setDateLevel(VSDimensionRef ref, DataRef[] refs, int dropIndex) {
      List<DataRef> list = new ArrayList<>();

      for(DataRef dataRef : refs) {
         if(dataRef != null) {
            list.add(dataRef);
         }
      }

      int level = (XSchema.TIME.equals(ref.getDataType()) ?
            DateRangeRef.HOUR_INTERVAL : DateRangeRef.YEAR_INTERVAL);
      ref.setDateLevelValue(String.valueOf(level));
      level = GraphUtil.getNextDateLevelValue(ref, ref.getGroupColumnValue(),
         list, dropIndex);
      ref.setDateLevelValue(String.valueOf(level));
   }

   /**
    * Create a dataref by assetentry.
    */
   private DataRef createDataRef(CrosstabVSAssembly assembly, AssetEntry entry,
                                 SourceInfo sinfo, RuntimeViewsheet rvs)
      throws Exception
   {
      return isDimension(entry) ? createDim(entry) : createAgg(assembly, entry, sinfo, rvs);
   }

   /**
    * Check if the entry is dimension.
    */
   private boolean isDimension(AssetEntry entry) {
      String refType = entry.getProperty("refType");
      int rtype = refType == null ? AbstractDataRef.NONE : Integer.parseInt(refType);
      String cubeTypeStr = entry.getProperty(AssetEntry.CUBE_COL_TYPE);
      int ctype = cubeTypeStr == null ? 0 : Integer.parseInt(cubeTypeStr);

      return (rtype & AbstractDataRef.DIMENSION) != 0 || (ctype & 1) == 0;
   }

   /**
    * Create a dimension ref.
    */
   private VSDimensionRef createDim(AssetEntry entry) {
      VSDimensionRef dim = new VSDimensionRef();
      dim.setGroupColumnValue(getColumnValue(entry));
      dim.setDataType(entry.getProperty("dtype"));

      if(entry.getProperty("refType") != null) {
         dim.setRefType(Integer.parseInt(entry.getProperty("refType")));
      }

      return dim;
   }

   /**
    * Create a aggregate ref.
    */
   private VSAggregateRef createAgg(CrosstabVSAssembly assembly, AssetEntry entry,
                                    SourceInfo sinfo, RuntimeViewsheet rvs)
      throws Exception
   {
      VSAggregateRef agg = new VSAggregateRef();
      agg.setColumnValue(getColumnValue(entry));
      //agg.setDataType(entry.getProperty("dtype"));
      int rtype = Integer.parseInt(entry.getProperty("refType"));
      agg.setRefType(rtype);

      if((rtype & AbstractDataRef.AGG_CALC) == AbstractDataRef.AGG_CALC ||
         (rtype & AbstractDataRef.AGG_EXPR) == AbstractDataRef.AGG_EXPR)
      {
         agg.setFormulaValue("None");
      }
      else if((rtype & AbstractDataRef.CUBE_MEASURE) == AbstractDataRef.CUBE_MEASURE) {
         if("true".equals(entry.getProperty("sqlServer"))) {
            if(entry.getProperty("expression") != null) {
               agg.setFormulaValue("Sum");
            }
            else {
               agg.setFormulaValue("None");
            }
         }
         else {
            agg.setFormulaValue("None");
         }
      }
      else if((rtype & AbstractDataRef.AGG_CALC) == 0) {
         String formula = VSBindingHelper.getModelDefaultFormula(entry, sinfo, rvs,
                                                                 analyticRepository);

         if(formula != null) {
            agg.setFormula(AggregateFormula.getFormula(formula));
         }
         else {
            agg.setFormula(AggregateFormula.getDefaultFormula(entry.getProperty("dtype")));
         }
      }

      return agg;
   }

   /**
    * Get the source data ref from vsassembly.
    */
   public DataRef getDataRef(CrosstabVSAssembly assembly, String type, int index) {
      VSCrosstabInfo cinfo = assembly.getVSCrosstabInfo();
      DataRef[] refs = getTargetArr(cinfo, type);

      if(index < 0 || index >= refs.length) {
         return null;
      }

      return refs[index];
   }

   /**
    *  Mark existed column to null column, and after add dropped column, remove the null.
    */
   private void markExistedColumn(DataRef ref, VSCrosstabInfo cinfo) {
      markColumn(ref, cinfo.getRowHeaders());
      markColumn(ref, cinfo.getColHeaders());
      markColumn(ref, cinfo.getAggregates());
   }

   private void markColumn(DataRef ref, DataRef[] refs) {
      String columnValue = getColumnValue(ref);

      if(columnValue == null) {
         return;
      }

      for(int i = 0; i < refs.length; i++) {
         String colValue = getColumnValue(refs[i]);

         if(columnValue.equals(colValue)) {
            refs[i] = null;
         }
      }
   }

   private DataRef markColumn(int index, DataRef[] refs) {
      if(refs == null || index < 0 || index > refs.length - 1) {
         return null;
      }

      DataRef ref = refs[index];
      refs[index] = null;

      return ref;
   }

   /**
    * Get column value from entry.
    */
   private String getColumnValue(AssetEntry entry) {
      if(entry == null) {
         return "";
      }

      String cvalue = entry.getName();
      String attribute = entry.getProperty("attribute");

      // normal chart entry not set entity and attribute properties,
      // cube entry set, the name should use entity + attribute
      if(attribute != null) {
         String entity = entry.getProperty("entity");
         cvalue = (entity != null ?  entity + "." : "") + attribute;
      }

     return cvalue;
   }

   /**
    * Get column value from DataRef.
    */
   private String getColumnValue(DataRef ref) {
      if(ref instanceof VSDimensionRef) {
         return ((VSDimensionRef)ref).getGroupColumnValue();
      }

      if(ref instanceof VSAggregateRef) {
         return ((VSAggregateRef)ref).getColumnValue();
      }

      return null;
   }

   /**
    * Get target array.
    */
   public DataRef[] getTargetArr(VSCrosstabInfo cinfo, String dropType) {
      DataRef[] target = null;

      if("rows".equals(dropType)) {
         target = cinfo.getRowHeaders();
      }
      else if("cols".equals(dropType)) {
         target = cinfo.getColHeaders();
      }
      else if("aggregates".equals(dropType)) {
         target = cinfo.getAggregates();
      }

      return target;
   }

   /**
    * Set target array.
    */
   private void setTargetArr(VSCrosstabInfo cinfo, String dropType, DataRef[] newArr) {
      if("rows".equals(dropType)) {
         cinfo.setDesignRowHeaders(newArr);
      }
      else if("cols".equals(dropType)) {
         cinfo.setDesignColHeaders(newArr);
      }
      else if("aggregates".equals(dropType)) {
         cinfo.setDesignAggregates(newArr);
      }
   }

   /**
    * Remove all of the null columns in crosstab.
    */
   private void trimColumns(VSCrosstabInfo cinfo) {
      cinfo.setDesignRowHeaders(trimTargetArr(cinfo.getRowHeaders()));
      cinfo.setDesignColHeaders(trimTargetArr(cinfo.getColHeaders()));
      cinfo.setDesignAggregates(trimTargetArr(cinfo.getAggregates()));
      cinfo.setPeriodRuntimeRowHeaders(null);
   }

   /**
    * Remove the null column in crosstab.
    */
   private DataRef[] trimTargetArr(DataRef[] refs) {
      List<DataRef> list = new ArrayList<>();

      for(DataRef ref : refs) {
         if(ref != null) {
            list.add(ref);
         }
      }

      return list.toArray(new DataRef[0]);
   }

   private final VSDrillHandler drillHandler;
   private final AnalyticRepository analyticRepository;
}
