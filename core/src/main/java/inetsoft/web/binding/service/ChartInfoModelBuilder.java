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

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.report.composition.graph.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.*;
import inetsoft.web.binding.model.graph.*;
import inetsoft.web.binding.model.graph.aesthetic.*;
import inetsoft.web.binding.service.graph.ChartAestheticService;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;

import java.util.*;
import java.util.stream.Collectors;

public abstract class ChartInfoModelBuilder {
   public ChartInfoModelBuilder(ChartRefModelFactoryService refService,
                                ChartAestheticService aesService,
                                DataRefModelFactoryService dataRefService,
                                VisualFrameModelFactoryService visualService)
   {
      this.refService = refService;
      this.aesService = aesService;
      this.dataRefService = dataRefService;
      this.visualService = visualService;
   }

   public ChartBindingModel createChartBinding(ChartInfo cinfo, PlotDescriptor plot, boolean wizard)
   {
      ChartBindingModel model = new ChartBindingModel();
      model.setType("chart");
      ChartRef[] refs = cinfo.getXFields();
      ChartRef[] rtRefs = cinfo.getRTXFields();

      for(int i = 0; i < refs.length; i++) {
         //Fixed bug #25182 that viewer binding should apply rt fields.
         ChartRef chartRef = refs[i];
         final String refFullName = chartRef != null ? chartRef.getFullName() : null;

         if(refs[i].isDrillVisible() && !(wizard && VSUtil.isPreparedCalcField(refs[i]))) {
            if(refs[i] instanceof VSChartAggregateRef && rtRefs != null) {
               Optional<ChartRef> rtRef = Arrays.stream(rtRefs)
                  .filter(ref -> Tool.equals(ref.getFullName(), refFullName))
                  .findFirst();

               if(rtRef.isPresent()) {
                  chartRef = rtRef.get();
               }
            }

            model.addXField(createRefModel(chartRef, cinfo,
               new OriginalDescriptor(OriginalDescriptor.X_AXIS, i)));
         }
      }

      refs = cinfo.getYFields();
      rtRefs = cinfo.getRTYFields();

      for(int i = 0; i < refs.length; i++) {
         ChartRef chartRef = refs[i];
         final String refFullName = chartRef != null ? chartRef.getFullName() : null;

         if(refs[i].isDrillVisible() && !(wizard && VSUtil.isPreparedCalcField(refs[i]))) {
            if(cinfo instanceof VSChartInfo &&
               ((VSChartInfo) cinfo).getRuntimeDateComparisonRefs() != null &&
               ((VSChartInfo) cinfo).getRuntimeDateComparisonRefs().length > 0 &&
               refs[i] instanceof VSChartAggregateRef && rtRefs != null)
            {
               Optional<ChartRef> rtRef = Arrays.stream(rtRefs)
                  .filter(ref -> Tool.equals(ref.getFullName(), refFullName))
                  .findFirst();

               if(rtRef.isPresent()) {
                  chartRef = rtRef.get();
               }
            }

            model.addYField(createRefModel(chartRef, cinfo,
               new OriginalDescriptor(OriginalDescriptor.Y_AXIS, i)));
         }
      }

      refs = cinfo.getGroupFields();

      for(int i = 0; i < refs.length; i++) {
         model.addGroupField(createRefModel(refs[i], cinfo,
            new OriginalDescriptor(OriginalDescriptor.GROUP, i)));
      }

      if(cinfo.isMultiAesthetic()) {
         List<ChartAggregateRef> aggrs = AllChartAggregateRef.getXYAggregateRefs(cinfo, false);
         HashMap<String, Integer> typeMap = aesService.getAggregateRtType(cinfo);

         if(typeMap != null && !typeMap.isEmpty() && aggrs != null) {
            aggrs = (List<ChartAggregateRef>) Tool.clone(aggrs);
            aggrs.stream()
               .filter(agg -> agg != null)
               .filter(agg -> typeMap.containsKey(agg.getFullName(false)))
               .forEach(agg -> agg.setRTChartType(typeMap.get(agg.getFullName(false))));
         }

         AllChartAggregateRef ref = new AllChartAggregateRef(cinfo, aggrs);
         model.setAllChartAggregate((AllChartAggregateRefModel)
            createRefModel(ref, cinfo,
               new OriginalDescriptor(OriginalDescriptor.ALL)));
      }

      loadVisualFrames(model, cinfo, cinfo);
      model.setMultiStyles(cinfo.isMultiStyles());
      model.setSeparated(cinfo.isSeparatedGraph());
      model.setWaterfall(GraphTypeUtil.isWaterfall(cinfo));
      model.setWordCloud(GraphTypeUtil.isWordCloud(cinfo));
      model.setMapType(cinfo.getMeasureMapType() == null ? "" : cinfo.getMeasureMapType());
      model.setSupportsGroupFields(cinfo.supportsGroupFields());

      if(cinfo instanceof VSChartInfo) {
         model.setMultiStyles(((VSChartInfo) cinfo).isDesignMultiStyles());
         model.setSeparated(((VSChartInfo) cinfo).isDesignSeparated());
         ColumnSelection geoColumns = ((VSChartInfo) cinfo).getGeoColumns();
         int count = geoColumns.getAttributeCount();

         for(int i = 0; i < count; i++) {
            DataRef dataRef = geoColumns.getAttribute(i);

            if(dataRef instanceof ChartRef) {
               ChartRef geoRef = (ChartRef) dataRef;

               model.addGeoCol(createRefModel(geoRef, cinfo,
                  new OriginalDescriptor(OriginalDescriptor.GEO_COL, i)));
            }
            else {
               model.addGeoCol(dataRefService.createDataRefModel(dataRef));
            }
         }
      }

      if(cinfo instanceof MapInfo) {
         ChartRef[] geoRefs = ((MapInfo) cinfo).getGeoFields();

         for(int i = 0; i < geoRefs.length; i++) {
            model.addGeoField(createRefModel(geoRefs[i], cinfo,
               new OriginalDescriptor(OriginalDescriptor.GEO, i)));
         }
      }
      else if(cinfo instanceof CandleChartInfo) {
         CandleChartInfo candleInfo = (CandleChartInfo) cinfo;

         if(candleInfo.getCloseField() != null) {
            model.setCloseField(
               createRefModel(candleInfo.getCloseField(), cinfo,
                              new OriginalDescriptor(OriginalDescriptor.CLOSE)));
         }

         if(candleInfo.getOpenField() != null) {
            model.setOpenField(
               createRefModel(candleInfo.getOpenField(), cinfo,
                              new OriginalDescriptor(OriginalDescriptor.OPEN)));
         }

         if(candleInfo.getHighField() != null) {
            model.setHighField(
               createRefModel(candleInfo.getHighField(), cinfo,
                              new OriginalDescriptor(OriginalDescriptor.HIGH)));
         }

         if(candleInfo.getLowField() != null) {
            model.setLowField(
               createRefModel(candleInfo.getLowField(), cinfo,
                              new OriginalDescriptor(OriginalDescriptor.LOW)));
         }
      }
      else if(cinfo instanceof RelationChartInfo) {
         RelationChartInfo relationInfo = (RelationChartInfo) cinfo;

         if(relationInfo.getSourceField() != null) {
            model.setSourceField(createRefModel(relationInfo.getSourceField(), cinfo,
                                                new OriginalDescriptor(OriginalDescriptor.SOURCE)));
         }

         if(relationInfo.getTargetField() != null) {
            model.setTargetField(createRefModel(relationInfo.getTargetField(), cinfo,
               new OriginalDescriptor(OriginalDescriptor.TARGET)));
         }
      }
      else if(cinfo instanceof GanttChartInfo) {
         GanttChartInfo ganttInfo = (GanttChartInfo) cinfo;

         if(ganttInfo.getStartField() != null) {
            model.setStartField(createRefModel(ganttInfo.getStartField(), cinfo,
                                                new OriginalDescriptor(OriginalDescriptor.START)));
         }

         if(ganttInfo.getEndField() != null) {
            model.setEndField(createRefModel(ganttInfo.getEndField(), cinfo,
               new OriginalDescriptor(OriginalDescriptor.END)));
         }

         if(ganttInfo.getMilestoneField() != null) {
            model.setMilestoneField(createRefModel(ganttInfo.getMilestoneField(), cinfo,
               new OriginalDescriptor(OriginalDescriptor.MILESTONE)));
         }
      }

      model.setSupportsPathField(cinfo.supportsPathField());
      ChartRef pathField = cinfo.getPathField();

      if(pathField != null) {
         model.setPathField(createRefModel(pathField, cinfo,
            new OriginalDescriptor(OriginalDescriptor.PATH)));
      }

      model.setPointLine(plot.isPointLine());
      model.setStackMeasures(plot.isStackMeasures());
      fixColorChanged(model);

      return model;
   }

   // check for default color and set color changed.
   private static void fixColorChanged(ChartBindingModel model) {
      List<ChartRefModel> xfields = model.getXFields().stream()
         .filter(f -> f instanceof ChartAggregateRefModel).collect(Collectors.toList());
      List<ChartRefModel> yfields = model.getYFields().stream()
         .filter(f -> f instanceof ChartAggregateRefModel).collect(Collectors.toList());

      if(yfields.size() > 0) {
         fixColorChanged(model, yfields);
      }
      else {
         fixColorChanged(model, xfields);
      }
   }

   private static void fixColorChanged(ChartBindingModel model, List<ChartRefModel> yfields) {
      final int n = CategoricalColorFrame.COLOR_PALETTE.length;

      for(int i = 0; i < yfields.size(); i++ ) {
         ColorFrameModel colorFrame = ((ChartAggregateRefModel) yfields.get(i)).getColorFrame();

         if(colorFrame instanceof StaticColorModel) {
            String color = ((StaticColorModel) colorFrame).getColor();

            // GraphGenerator.fixShapeFrame checks if color has changed to see if color
            // should be applied on image. this condition matches the logic for applyColor.
            // (61437)
            if(model.isMultiStyles()) {
               if(!color.equals(Tool.toString(CategoricalColorFrame.COLOR_PALETTE[0]))) {
                  colorFrame.setChanged(true);
               }
            }
            else if(!color.equals(Tool.toString(CategoricalColorFrame.COLOR_PALETTE[i % n]))) {
               colorFrame.setChanged(true);
            }
         }
      }
   }

   public ChartInfo updateChartInfo(ChartBindingModel model, ChartInfo ocinfo, ChartInfo ncinfo,
                                    ChartDescriptor descriptor)
   {
      ncinfo.setMultiStyles(model.isMultiStyles());
      ncinfo.setSeparatedGraph(model.isSeparated());
      ncinfo.setMeasureMapType(model.getMapType());
      ncinfo.removeXFields();
      ncinfo.removeYFields();
      ncinfo.removeGroupFields();
      VSDataRef[] aggs = ocinfo.getAggregateRefs();
      List<XDimensionRef> dims = getAllDimensions(ocinfo);
      Map<String, VSAggregateRef> fixAggVariableInDimMap = new HashMap<>();
      Map<String, String> fixDimVariableInAggMap = new HashMap<>();

      for(ChartRef ref : getChartRefs(model.getXFields(), ocinfo)) {
         VSUtil.fixVariableAggInDim(fixAggVariableInDimMap, aggs, ref);
         VSUtil.fixVariableDimInAgg(fixDimVariableInAggMap, dims, ref);
         ncinfo.addXField(ref);
      }

      for(ChartRef ref : getChartRefs(model.getYFields(), ocinfo)) {
         VSUtil.fixVariableAggInDim(fixAggVariableInDimMap, aggs, ref);
         VSUtil.fixVariableDimInAgg(fixDimVariableInAggMap, dims, ref);
         ncinfo.addYField(ref);
      }

      for(ChartRef ref : getChartRefs(model.getGroupFields(), ocinfo)) {
         VSUtil.fixVariableAggInDim(fixAggVariableInDimMap, aggs, ref);
         VSUtil.fixVariableDimInAgg(fixDimVariableInAggMap, dims, ref);
         ncinfo.addGroupField(ref);
      }

      if(ncinfo instanceof VSChartInfo) {
         VSChartInfo vsChartInfo = (VSChartInfo) ncinfo;
         ColumnSelection columns = vsChartInfo.getGeoColumns();
         columns.removeAllAttributes();
         List<DataRefModel> refModel = model.getGeoCols();

         for(DataRefModel ref : refModel) {
            if(ref instanceof ChartRefModel) {
               columns.addAttribute(refService.pasteChartRef(ocinfo, (ChartRefModel) ref));
            }
            else {
               columns.addAttribute(ref.createDataRef());
            }
         }
      }

      if(ncinfo instanceof MapInfo) {
         MapInfo mapInfo = (MapInfo) ncinfo;
         mapInfo.removeGeoFields();

         for(ChartRef ref : getChartRefs(model.getGeoFields(), ocinfo)) {
            mapInfo.addGeoField(ref);
         }
      }

      if(ncinfo instanceof CandleChartInfo) {
         CandleChartInfo candleInfo = (CandleChartInfo) ncinfo;
         candleInfo.setCloseField(refService.pasteChartRef(ocinfo, model.getCloseField()));
         candleInfo.setOpenField(refService.pasteChartRef(ocinfo, model.getOpenField()));
         candleInfo.setHighField(refService.pasteChartRef(ocinfo, model.getHighField()));
         candleInfo.setLowField(refService.pasteChartRef(ocinfo, model.getLowField()));
      }
      else if(ncinfo instanceof RelationChartInfo) {
         RelationChartInfo relationInfo = (RelationChartInfo) ncinfo;
         relationInfo.setSourceField(refService.pasteChartRef(ocinfo, model.getSourceField()));
         relationInfo.setTargetField(refService.pasteChartRef(ocinfo, model.getTargetField()));
      }
      else if(ncinfo instanceof GanttChartInfo) {
         GanttChartInfo ganttInfo = (GanttChartInfo) ncinfo;
         ganttInfo.setStartField(refService.pasteChartRef(ocinfo, model.getStartField()));
         ganttInfo.setEndField(refService.pasteChartRef(ocinfo, model.getEndField()));
         ganttInfo.setMilestoneField(refService.pasteChartRef(ocinfo, model.getMilestoneField()));
      }

      ncinfo.setPathField(refService.pasteChartRef(ocinfo, model.getPathField()));
      ncinfo.updateChartType(!model.isMultiStyles());
      aesService.updateVisualFrames(model, ocinfo, ncinfo);
      GraphUtil.fixVisualFrames(ncinfo);
      AestheticInfo ainfo = model.getColorField();

      if(ainfo != null) {
         ChartRef color = refService.pasteChartRef(ocinfo, ainfo.getDataInfo());
         VSUtil.fixVariableAggInDim(fixAggVariableInDimMap, aggs, color);
         VSUtil.fixVariableDimInAgg(fixDimVariableInAggMap, dims, color);
         ncinfo.getColorField().setDataRef(color);
      }

      GraphFormatUtil.fixDefaultNumberFormat(descriptor, ncinfo);

      return ncinfo;
   }

   private List<XDimensionRef> getAllDimensions(ChartInfo info) {
      List<XDimensionRef> list = new ArrayList<>();
      getAllDimensions(info.getBindingRefs(true), list);
      getAllDimensions(info.getAestheticRefs(true), list);

      return list;
   }

   private void getAllDimensions(DataRef[] refs, List<XDimensionRef> list) {
      for(DataRef ref : refs) {
         if(ref instanceof XDimensionRef) {
            list.add((XDimensionRef) ref);
         }
         else if(ref instanceof AestheticRef) {
            DataRef ref0 = ((AestheticRef) ref).getDataRef();

            if(ref0 instanceof XDimensionRef) {
               list.add((XDimensionRef) ref0);
            }
         }
      }
   }

   /**
    * Get the chart refs
    * @param refModels the chart data infos of the model.
    * @param cinfo the chart info.
    * @return the chart ref list.
    */
   private List<ChartRef> getChartRefs(List<ChartRefModel> refModels, ChartInfo cinfo) {
      return refModels.stream()
         .map((refModel) -> refService.pasteChartRef(cinfo, refModel))
         .collect(Collectors.toList());
   }

   private ChartRefModel createRefModel(ChartRef ref,
                                        ChartInfo cinfo,
                                        OriginalDescriptor desc)
   {
      ChartRefModel cmodel = refService.createRefModel(ref, cinfo, desc);
      loadOptionModel(cmodel);

      if(ref instanceof ChartAggregateRef) {
         ChartAggregateRefModel aggModel = (ChartAggregateRefModel) cmodel;
         ChartAggregateRef aggRef = (ChartAggregateRef) ref;
         loadVisualFrames(aggModel, cinfo, aggRef);

         if(GraphTypeUtil.isWaterfall(cinfo)) {
            ColorFrameModel csummary = visualService.createVisualFrameModel(
               aggRef.getSummaryColorFrameWrapper());
            csummary.setSummary(true);
            aggModel.setSummaryColorFrame(csummary);

            TextureFrameModel tsummary = visualService.createVisualFrameModel(
               aggRef.getSummaryTextureFrameWrapper());
            tsummary.setSummary(true);
            aggModel.setSummaryTextureFrame(tsummary);
         }
      }

      return cmodel;
   }

   private void loadVisualFrames(ChartAestheticModel model, ChartInfo cinfo,
                                 ChartBindable bindable)
   {
      aesService.loadVisualFrames(model, cinfo, bindable);

      loadVisualFrameModel(model.getColorField());
      loadVisualFrameModel(model.getShapeField());
      loadVisualFrameModel(model.getSizeField());
      loadVisualFrameModel(model.getTextField());

      if(GraphTypes.isRelation(cinfo.getChartType())) {
         loadVisualFrameModel(model.getNodeColorField());
         loadVisualFrameModel(model.getNodeSizeField());
      }
   }

   private void loadVisualFrameModel(AestheticInfo aesInfo) {
      if(aesInfo == null || aesInfo.getDataInfo() == null) {
         return;
      }

      loadOptionModel(aesInfo.getDataInfo());
   }

   private void loadOptionModel(ChartRefModel cmodel) {
      this.sortOptionModel = sortOptionModel == null ? createSortOptionModel() : sortOptionModel;

      if(cmodel instanceof ChartDimensionRefModel) {
         ((ChartDimensionRefModel) cmodel).setSortOptionModel(this.sortOptionModel);
      }
      else if(cmodel instanceof ChartAggregateRefModel) {
         FormulaOptionModel formulaOptionModel
            = createFormulaOptionModel((BAggregateRefModel) cmodel);
         ((ChartAggregateRefModel) cmodel).setFormulaOptionModel(formulaOptionModel);
      }
   }

   /**
    * chart info
    * vs: need runtime aggrs---adhoc side: don't need runtime aggrs
    */
   protected final List<Map<String, String>> getAggregateRefs(ChartInfo cinfo) {
      List<Map<String, String>> aggregateRefs = new ArrayList();
      HashMap<String, Integer> typeMap = aesService.getAggregateRtType(cinfo);
      DataRef[] dataRefs =  cinfo.getFields();

      for(DataRef ref : dataRefs) {
         if(!(ref instanceof XAggregateRef)) {
            continue;
         }

         Map<String, String> map = new HashMap<>();
         ChartAggregateRefModel aggregateRefModel =
            new ChartAggregateRefModel((ChartAggregateRef) ref, cinfo);
         String fullName = ((XAggregateRef) ref).getFullName(false);

         if(typeMap != null && typeMap.containsKey(fullName)) {
            aggregateRefModel.setRTChartType(typeMap.get(fullName));
         }

         String label = !"".equals(aggregateRefModel.getOriFullName()) &&
            aggregateRefModel.getOriFullName() != null ? aggregateRefModel.getOriFullName() :
            aggregateRefModel.getFullName();
         map.put("label", label);
         map.put("value", label);

         if(!aggregateRefs.contains(map)) {
            aggregateRefs.add(map);
         }
      }

      return aggregateRefs;
   }

   protected abstract SortOptionModel createSortOptionModel();

   protected abstract FormulaOptionModel createFormulaOptionModel(BAggregateRefModel cmodel);

   protected final ChartRefModelFactoryService refService;
   protected final ChartAestheticService aesService;
   private final DataRefModelFactoryService dataRefService;
   private final VisualFrameModelFactoryService visualService;
   private SortOptionModel sortOptionModel;
}
