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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.graph.*;
import inetsoft.sree.AnalyticRepository;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.binding.dnd.*;
import inetsoft.web.viewsheet.handler.VSDrillHandler;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

@Component
public class VSChartBindingHandler {

   @Autowired
   public VSChartBindingHandler(ChartDndHandler dndHandler,
                                VSDrillHandler drillHandler,
                                VSChartDataHandler dataHandler,
                                AnalyticRepository analyticRepository)
   {
      this.dndHandler = dndHandler;
      this.dataHandler = dataHandler;
      this.drillHandler = drillHandler;
      this.analyticRepository = analyticRepository;
   }

   /**
    * Process the drag & drop action.
    *
    * @param assembly the vsassembly.
    * @param transfer the Dnd transfer.
    */
   public void addRemoveColumns(RuntimeViewsheet rvs, ChartVSAssembly assembly,
                                DataTransfer transfer, DropTarget target)
      throws Exception
   {
      ChartVSAssemblyInfo info = assembly.getChartInfo();
      VSChartInfo vinfo = info.getVSChartInfo();
      VSChartInfo oinfo = vinfo.clone();
      ChartTransfer ctransfer = (ChartTransfer) transfer;
      BindingDropTarget btarget = (BindingDropTarget) target;
      // drop to binding editor.
      List<ChartRef> refs = dndHandler.getChartRefs(ctransfer, vinfo);
      dropToChartEditor(refs, info, ctransfer, btarget, rvs);
      GraphDefault.setDefaultFormulas(oinfo, vinfo);
      GraphFormatUtil.fixDefaultNumberFormat(assembly.getChartDescriptor(), vinfo);
   }

   /**
    * Process the drag & drop from tree action.
    *
    * @param assembly the vsassembly.
    */
   public void addColumns(RuntimeViewsheet rvs, ChartVSAssembly assembly,
                          AssetEntry[] entries, DropTarget target,
                          CommandDispatcher dispatcher, String linkUri)
      throws Exception
   {
      ChartVSAssemblyInfo info = assembly.getChartInfo();
      BindingDropTarget btarget = (BindingDropTarget) target;
      VSChartInfo vinfo = info.getVSChartInfo();
      VSChartInfo oinfo = vinfo.clone();
      List<ChartRef> refs = createChartRefs(entries, assembly, btarget.getDropType(),
                                            btarget.getDropIndex(), rvs);

      List<ChartRef> removeRefs = null;

      // drop to chart view.
      if(target instanceof ChartViewDropTarget) {
         removeRefs = new ArrayList<>();

         for(ChartRef ref : refs) {
            removeRefs.add(dropToChartView(ref, info, null, btarget, rvs));
         }
      }
      // drop to binding editor.
      else {
         removeRefs = dropToChartEditor(refs, info, null, btarget, rvs);
      }

      removeDrillFilter(rvs, assembly, dispatcher, linkUri, removeRefs);
      GraphDefault.setDefaultFormulas(oinfo, vinfo);
      GraphFormatUtil.fixDefaultNumberFormat(assembly.getChartDescriptor(), vinfo);
   }

   private void removeDrillFilter(RuntimeViewsheet rvs, ChartVSAssembly assembly,
                                  CommandDispatcher dispatcher, String linkUri,
                                  List<ChartRef> removeRefs)
      throws Exception
   {
      if(removeRefs != null) {
         for(ChartRef removeRef : removeRefs) {
            if(!(removeRef instanceof VSChartAggregateRef)) {
               drillHandler.removeDrillFilter(rvs, assembly, removeRef, dispatcher, linkUri);
               // drill level tracks runtime drill. change binding should clear it. (58091)
               assembly.getVSChartInfo().setDrillLevel(0);
            }
         }
      }
   }

   /**
    * Process the drag & drop to tree action.
    *  @param assembly the vsassembly.
    * @param transfer the Dnd transfer.
    */
   public void removeColumns(RuntimeViewsheet rvs, ChartVSAssembly assembly,
                             DataTransfer transfer, CommandDispatcher dispatcher,
                             String linkUri)
      throws Exception
   {
      ChartVSAssemblyInfo info = assembly.getChartInfo();
      VSChartInfo vinfo = info.getVSChartInfo();
      VSChartInfo oinfo = vinfo.clone();
      removeBindingRefs(assembly, (ChartTransfer) transfer, rvs, dispatcher, linkUri);
      GraphDefault.setDefaultFormulas(oinfo, vinfo);
      vinfo.clearRuntime();
   }

   private void removeBindingRefs(ChartVSAssembly chart, ChartTransfer transfer,
                                  RuntimeViewsheet rvs, CommandDispatcher dispatcher,
                                  String linkUri)
      throws Exception
   {
      ChartVSAssemblyInfo info = chart.getChartInfo();
      VSChartInfo cinfo = info.getVSChartInfo();
      VSChartInfo oinfo = cinfo.clone();
      List<ChartRef> removeRefs = dndHandler.removeBindingRefs(cinfo, transfer);

      if(ChartDndHandler.isAestheticRegion(transfer.getDragType())) {
         dataHandler.changeChartAesthetic(rvs, info);
      }

      // aesthetic fields not added to removeRefs so add them here (45428).

      if(cinfo.getColorField() == null && oinfo.getColorField() != null) {
         removeRefs.add((ChartRef) oinfo.getColorField().getDataRef());
      }

      if(cinfo.getShapeField() == null && oinfo.getShapeField() != null) {
         removeRefs.add((ChartRef) oinfo.getShapeField().getDataRef());
      }

      if(cinfo.getSizeField() == null && oinfo.getSizeField() != null) {
         removeRefs.add((ChartRef) oinfo.getSizeField().getDataRef());
      }

      removeDrillFilter(rvs, chart, dispatcher, linkUri, removeRefs);
   }

   private List<ChartRef> dropToChartEditor(List<ChartRef> refs, ChartVSAssemblyInfo info,
                                            ChartTransfer transfer, BindingDropTarget target,
                                            RuntimeViewsheet rvs)
      throws Exception
   {
      if(dndHandler.isChartEditorRegion(target.getDropType()) ||
         dndHandler.isPathRegion(target.getDropType()))
      {
         return dropToXY(refs, info, transfer, target, rvs);
      }
      else if(ChartDndHandler.isAestheticRegion(target.getDropType())) {
         dropToAesthetic(refs.get(0), info, transfer, (ChartAestheticDropTarget) target, rvs);
      }
      else if(dndHandler.isHighLowRegion(target.getDropType())) {
         dropToHighLow(refs.get(0), info, transfer, target, rvs);
      }
      else if(dndHandler.isTreeRegion(target.getDropType())) {
         dropToTree(refs.get(0), info, transfer, target, rvs);
      }
      else if(dndHandler.isGanttRegion(target.getDropType())) {
         dropToGantt(refs.get(0), info, transfer, target, rvs);
      }

      return null;
   }

   private List<ChartRef> dropToXY(List<ChartRef> refs, ChartVSAssemblyInfo info,
                                   ChartTransfer transfer, BindingDropTarget target,
                                   RuntimeViewsheet rvs)
      throws Exception
   {
      VSChartInfo cinfo = info.getVSChartInfo();
      List<ChartRef> removeRefs = dndHandler.dropToXY(refs, cinfo, transfer, target);

      cinfo.clearRuntime();

      if(transfer != null && ChartDndHandler.isAestheticRegion(transfer.getDragType())) {
         dataHandler.changeChartAesthetic(rvs, info);
      }

      return removeRefs;
   }

   private void dropToAesthetic(ChartRef ref, ChartVSAssemblyInfo info,
      ChartTransfer transfer, ChartAestheticDropTarget target,
      RuntimeViewsheet rvs) throws Exception
   {
      VSChartInfo cinfo = info.getVSChartInfo();
      dndHandler.dropToAesthetic(ref, cinfo, transfer, target);
      ChartDndHandler.fixLegendFormats(info.getChartDescriptor(), transfer, target);
      cinfo.clearRuntime();
      dataHandler.changeChartAesthetic(rvs, info);
   }

   private void dropToHighLow(ChartRef ref, ChartVSAssemblyInfo info,
      ChartTransfer transfer, BindingDropTarget target,
      RuntimeViewsheet rvs) throws Exception
   {
      if(ref == null || !ref.isMeasure()) {
         return;
      }

      VSChartInfo cinfo = info.getVSChartInfo();
      dndHandler.dropToHighLow(ref, cinfo, transfer, target);

      if(transfer != null && ChartDndHandler.isAestheticRegion(transfer.getDragType())) {
         dataHandler.changeChartAesthetic(rvs, info);
      }
   }

   private void dropToTree(ChartRef ref, ChartVSAssemblyInfo info,
                              ChartTransfer transfer, BindingDropTarget target,
                              RuntimeViewsheet rvs) throws Exception
   {
      if(ref == null || ref.isMeasure()) {
         return;
      }

      VSChartInfo cinfo = info.getVSChartInfo();
      dndHandler.dropToTree(ref, cinfo, transfer, target);
   }

   private void dropToGantt(ChartRef ref, ChartVSAssemblyInfo info,
                              ChartTransfer transfer, BindingDropTarget target,
                              RuntimeViewsheet rvs) throws Exception
   {
      if(ref == null || !ref.isMeasure()) {
         return;
      }

      VSChartInfo cinfo = info.getVSChartInfo();
      dndHandler.dropToGantt(ref, cinfo, transfer, target);
   }

   private ChartRef dropToChartView(ChartRef ref, ChartVSAssemblyInfo info,
                                    ChartTransfer transfer, BindingDropTarget target,
                                    RuntimeViewsheet rvs) throws Exception
   {
      VSChartInfo cinfo = info.getVSChartInfo();
      ChartRef removeRef = dndHandler.dropToChartView(ref, cinfo, transfer, target);

      if(ChartDndHandler.isAestheticRegion(target.getDropType())) {
         dataHandler.changeChartAesthetic(rvs, info);
      }

      return removeRef;
   }

   /**
    * Get chart refs from transfer.
    */
   private List<ChartRef> createChartRefs(AssetEntry[] entries, ChartVSAssembly assembly,
                                          String dropType, int dropIndex, RuntimeViewsheet rvs)
         throws RemoteException
   {
      List<ChartRef> refs = new ArrayList<>();

      for(int i = 0; i < entries.length; i++) {
         ChartRef ref = createChartRef(entries[i], assembly, dropType, dropIndex + i, rvs);
         refs.add(ref);
      }

      return refs;
   }

   /**
    * Create chart ref by asset entry.
    * @param entry the specified asset entry.
    * @param assembly the specified vs chart.
    * @return new chart ref.
    */
   private ChartRef createChartRef(AssetEntry entry, ChartVSAssembly assembly,
                                   String dropType, int dropIndex, RuntimeViewsheet rvs) throws RemoteException
   {
      ChartVSAssemblyInfo info = assembly.getChartInfo();
      VSChartInfo cinfo = info.getVSChartInfo();
      SourceInfo sinfo = info.getSourceInfo();
      String columnValue = getColumnValue(entry);
      String tname = entry.getProperty("assembly");
      String dtype = entry.getProperty("dtype");
      String rtype = entry.getProperty("refType");
      String caption = entry.getProperty("caption");
      int refType = rtype == null ? AbstractDataRef.NONE : Integer.parseInt(rtype);

      if(isDimension(entry)) {
         VSChartGeoRef gRef = (VSChartGeoRef) findGeoColByName(columnValue, cinfo);
         gRef = sinfo == null || !tname.equals(sinfo.getSource()) ? null : gRef;
         boolean isGeoRef = gRef != null;
         VSChartDimensionRef dim = isGeoRef ? gRef.clone() : new VSChartDimensionRef();

         dim.setGroupColumnValue(columnValue);
         dim.setDataType(dtype);
         dim.setRefType(refType);

         if(caption != null) {
            dim.setCaption(caption);
         }

         // if it's from predefined info, set the date level
         if(XSchema.isDateType(dim.getDataType())) {
            if((refType & AbstractDataRef.CUBE) != 0) {
               dim.setDateLevel(DateRangeRef.DAY_INTERVAL);
            }
            else {
               setDefaultDateLevel(dim, entry.getName(), cinfo, dropType, dropIndex);
            }
         }

         return dim;
      }
      else {
         VSChartAggregateRef agg = new VSChartAggregateRef();
         agg.setRefType(refType);
         agg.setColumnValue(columnValue);
         agg.setOriginalDataType(dtype);
         agg.setAggregated(cinfo.shouldAggregate(agg));

         if(caption != null) {
            agg.setCaption(caption);
         }

         if((refType & AbstractDataRef.AGG_CALC) == AbstractDataRef.AGG_CALC ||
            (refType & AbstractDataRef.AGG_EXPR) == AbstractDataRef.AGG_EXPR)
         {
            agg.setFormulaValue("None");
         }
         else if((refType & AbstractDataRef.CUBE_MEASURE) == AbstractDataRef.CUBE_MEASURE) {
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
         // if it's from predefined info, set formula and secondary column
         else {
            String formula = VSBindingHelper.getModelDefaultFormula(entry, sinfo, rvs,
                                                                    analyticRepository);

            if(formula != null) {
               agg.setFormulaValue(formula);
            }
            else {
               setDefaultFormula(agg, entry.getName(), dtype, cinfo, dropType);
            }

            GraphFormatUtil.setDefaultNumberFormat(assembly.getChartDescriptor(),
               assembly.getVSChartInfo(), dtype, agg, Integer.parseInt(dropType));
         }

         return agg;
      }
   }

   /**
    * Return if the asset entry should used to create a dimension chartref.
    */
   private boolean isDimension(AssetEntry entry) {
      String cubecoltype = entry.getProperty(AssetEntry.CUBE_COL_TYPE);

      if(cubecoltype != null) {
         return (Integer.parseInt(cubecoltype) & AssetEntry.MEASURES) == 0;
      }

      return !XSchema.isNumericType(entry.getProperty("dtype"));
   }

   /**
    * Find the contained geo ref in geo column slection.
    * @param name the specified attribute's name.
    * @param cinfo the specified vs chart info.
    * @return the contained attribute equals to the attribute, <tt>null</tt>
    * not found.
    */
   private DataRef findGeoColByName(String name, VSChartInfo cinfo) {
      int index = -1;

      if(cinfo == null) {
         return null;
      }

      ColumnSelection col = cinfo.getGeoColumns();

      for(int i = 0; col != null && i < col.getAttributeCount(); i++) {
         if(name.equals(col.getAttribute(i).getName())) {
            index = i;
            break;
         }
      }

      return index == -1 ? null : col.getAttribute(index);
   }

   /**
    * Get property name from the entity, if the entity is a cube member, should
    * use entity + attribute as the name.
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
    * Set the default formula from the aggregate info.
    */
   private void setDefaultDateLevel(VSChartDimensionRef dim, String name,
                                    VSChartInfo cinfo, String dropType, int dropIndex)
   {
      int level = (XSchema.TIME.equals(dim.getDataType()) ?
         DateRangeRef.HOUR_INTERVAL : DateRangeRef.YEAR_INTERVAL);
      dim.setDateLevelValue(String.valueOf(level));
      level = dndHandler.getNextDateLevelValue(cinfo, dim, name, dropType, dropIndex);

      if(level != -1) {
         dim.setDateLevelValue(String.valueOf(level));
      }
   }

   /**
    * Find the data ref from the specified aggregate info.
    */
   private AggregateRef findAggregateRef(AggregateInfo ainfo, String name) {
      for(int i = 0; ainfo != null && i < ainfo.getAggregateCount(); i++) {
         AggregateRef aggregate = ainfo.getAggregate(i);

         if(aggregate.getDataRef() != null &&
            name.equals(aggregate.getDataRef().getName()))
         {
            return aggregate;
         }
      }

      return null;
   }

   /**
    * Set the default formula from the aggregate info.
    */
   private void setDefaultFormula(VSChartAggregateRef agg, String name, String dtype,
                                  VSChartInfo cinfo, String dropType)
   {
      //new aggregateinfo same with new create aggr.
      AggregateInfo ainfo = cinfo.getAggregateInfo();
      AggregateRef ref = findAggregateRef(ainfo, name);
      ref = (AggregateRef) Tool.clone(ref);

      if(ref != null) {
         //Fixed bug #19499 that drag measure col should be same with
         //last modify formula's same col.
         //here, should get all aggregates that be same with ref col.
         VSDataRef[] aggrs = cinfo.getAggregates(ref.getDataRef(), true);
         Boolean hasDefaultFormula = false;

         for(int i = 0; i < aggrs.length; i++) {
            if(name.equals(aggrs[i].getName())) {
               VSChartAggregateRef aggr = (VSChartAggregateRef) aggrs[i];
               agg.setFormulaValue(aggr.getFormulaValue());

               if(aggr.getFormula() != null && aggr.getFormula().isTwoColumns()) {
                  agg.setSecondaryColumnValue(aggr.getSecondaryColumnValue());
               }

               hasDefaultFormula = true;
            }
         }

         if(hasDefaultFormula) {
            return;
         }

         if(AssetUtil.isNumberType(dtype) && (GraphTypes.isCandle(cinfo.getRTChartType()) ||
            GraphTypes.isStock(cinfo.getRTChartType())))
         {
            AggregateFormula formula = GraphUtil.getDefaultFormula(cinfo, Integer.parseInt(dropType));

            if(formula != null) {
               ref.setFormula(formula);

               if(formula.isTwoColumns()) {
                  DataRef secCol = ref.getSecondaryColumn();
                  List<ChartDimensionRef> dateDims = GraphUtil.getXYDateDimensionsRef(cinfo);
                  ref.setSecondaryColumn(dateDims != null && dateDims.size() > 0 ?
                     dateDims.get(0).getDataRef() : secCol);
               }
            }
         }

         if(ref.getFormula() == null) {
            agg.setFormulaValue("None");
         }
         else {
            agg.setFormulaValue(ref.getFormula().getFormulaName());
         }

         if(ref.getFormula() != null && ref.getFormula().isTwoColumns()) {
            DataRef ref2 = ref.getSecondaryColumn();

            if(ref2 != null) {
               agg.setSecondaryColumnValue(ref2.getName());
            }
         }
      }
      else {
         agg.setFormulaValue(AggregateFormula.getDefaultFormula(dtype).getFormulaName());
      }
   }

   private final ChartDndHandler dndHandler;
   private final VSDrillHandler drillHandler;
   private final VSChartDataHandler dataHandler;
   private final AnalyticRepository analyticRepository;

   private static final Logger LOGGER = LoggerFactory.getLogger(VSChartBindingHandler.class);
}
