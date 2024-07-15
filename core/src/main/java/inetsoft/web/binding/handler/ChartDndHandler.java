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

import inetsoft.graph.aesthetic.*;
import inetsoft.report.StyleConstants;
import inetsoft.report.composition.graph.*;
import inetsoft.report.composition.graph.calc.RunningTotalCalc;
import inetsoft.report.composition.graph.calc.ValueOfCalc;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticShapeFrameWrapper;
import inetsoft.util.*;
import inetsoft.web.binding.dnd.*;
import inetsoft.web.binding.model.graph.*;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public final class ChartDndHandler {
   public ChartDndHandler(ChartRefModelFactoryService chartRefService) {
      this.chartRefService = chartRefService;
   }

   // called when aesthetic binding is changed to set legend formats.
   public static void fixLegendFormats(ChartDescriptor chartDescriptor, ChartTransfer transfer,
                                       BindingDropTarget target)
   {
      LegendsDescriptor legendsDesc = chartDescriptor.getLegendsDescriptor();
      int drop = Integer.parseInt(target.getDropType());
      LegendDescriptor dropDesc = getAestheticLegendDescriptor(drop, legendsDesc);

      if(transfer != null) {
         int drag = Integer.parseInt(transfer.getDragType());

         // keep format if dragged from one aesthetic field to another
         if(isAestheticRegion(drag) && isAestheticRegion(drop)) {
            LegendDescriptor dragDesc = getAestheticLegendDescriptor(drag, legendsDesc);

            if(dragDesc != null && dropDesc != null) {
               CompositeTextFormat oldFmt = dragDesc.getContentTextFormat();
               dropDesc.setContentTextFormat(oldFmt);
            }
         }
      }

      // clear out default format when target changed (62215)

      if(dropDesc != null) {
         dropDesc.getContentTextFormat().getDefaultFormat().setFormat(null);
      }
   }

   static LegendDescriptor getAestheticLegendDescriptor(int type, LegendsDescriptor legendsDesc) {
      switch(type) {
      case ChartConstants.DROP_REGION_COLOR:
         return legendsDesc.getColorLegendDescriptor();
      case ChartConstants.DROP_REGION_SHAPE:
         return legendsDesc.getShapeLegendDescriptor();
      case ChartConstants.DROP_REGION_SIZE:
         return legendsDesc.getSizeLegendDescriptor();
      default:
         return  null;
      }
   }

   /**
    * Get chart refs from transfer.
    */
   public List<ChartRef> getChartRefs(ChartTransfer transfer, ChartInfo info) throws Exception {
      List<ChartRef> refs = new ArrayList<>();
      List<ChartRefModel> refModel = transfer.getRefs();

      for(ChartRefModel model: refModel) {
         refs.add(chartRefService.pasteChartRef(info, model));
      }

      return refs;
   }

   /**
    * Fix map formula to none.
    */
   private List<ChartRef> fixMapFormula(List<ChartRef> refs) {
      for(ChartRef ref : refs) {
         if(ref instanceof XAggregateRef) {
            ((XAggregateRef) ref).setFormula(AggregateFormula.NONE);
         }
      }

      return refs;
   }

   private List<ChartRef> convertToGeoRefs(List<ChartRef> refs) {
      List<ChartRef> georefs = new ArrayList<>();

      for(ChartRef ref : refs) {
         if(ref instanceof VSChartDimensionRef && !(ref instanceof VSChartGeoRef)) {
            georefs.add(new VSChartGeoRef((VSChartDimensionRef) ref));
         }
         else if(ref instanceof GeoRef) {
            georefs.add(ref);
         }
      }

      return georefs;
   }

   public List<ChartRef> dropToXY(List<ChartRef> refs, ChartInfo cinfo,
                                  ChartTransfer transfer, BindingDropTarget target)
   {
      List<ChartRef> removeRefs = new ArrayList<>();
      Integer dragType = transfer == null ? null : Integer.parseInt(transfer.getDragType());
      refs = getFixedXYRef(cinfo, refs, target.getDropType(),
                           transfer != null ? transfer.getDragType() : null);

      // 1) if dimension, set dateLevel and timeSeries.
      //    if measure, set formula, discrete.
      //    if measure, set default chartType when chartType is multiStyles.
      AllChartAggregateRef all = getAllChartAggregateRef(cinfo);
      int size = all.getChartAggregateRefs().size();

      if(refs.size() == 1 && refs.get(0) instanceof ChartAggregateRef &&
         cinfo.isMultiStyles() && size > 0  && transfer != null &&
         dragType != ChartConstants.DROP_REGION_Y)
      {
         ChartAggregateRef aggRef = all.getChartAggregateRefs().get(size - 1);
         ((ChartAggregateRef) refs.get(0)).setChartType(aggRef.getChartType());
      }

      for(ChartRef ref : refs) {
         if(ref instanceof ChartDimensionRef) {
            fixDimension((ChartDimensionRef) ref, cinfo, target);
         }
         else if(ref instanceof ChartAggregateRef) {
            fixAggregate((ChartAggregateRef) ref, cinfo, transfer, target);
         }
      }

      // 3) If drag from X/Y/group/geo, mark the drag fields.
      if(transfer != null && isChartEditorRegion(transfer.getDragType())) {
         List<ChartRefModel> refsModel = transfer.getRefs();
         markFields(transfer, refsModel, cinfo);
      }

      // 4) add fields.
      if(isPathRegion(target.getDropType())) {
         cinfo.setPathField(refs.get(0));
      }
      else {
         int dropType = Integer.parseInt(target.getDropType());
         int dropIndex = target.getDropIndex();
         ChartRef[] orefs = null;

         if(dropType == ChartConstants.DROP_REGION_X) {
            orefs = cinfo.getXFields();
         }
         else if(dropType == ChartConstants.DROP_REGION_Y) {
            orefs = cinfo.getYFields();
         }
         else if(dropType == ChartConstants.DROP_REGION_GROUP) {
            orefs = cinfo.getGroupFields();
         }
         else if(dropType == ChartConstants.DROP_REGION_GEO) {
            orefs = ((MapInfo) cinfo).getGeoFields();
         }

         // 5) Copy options.
         for(int i = 0; i < refs.size() && dropIndex < orefs.length; i++) {
            if(target.getReplace() && i == 0) {
               ChartRef chartRef = orefs[dropIndex];
               boolean newref = transfer == null;

               if(newref && chartRef != null && refs.get(i) instanceof XDimensionRef &&
                  chartRef instanceof XDimensionRef)
               {
                  ((XDimensionRef) (refs.get(i))).copyOptions((XDimensionRef) chartRef);
               }
            }
         }

         Integer multiChartType = null;

         if(!refs.isEmpty() && orefs != null && orefs.length > 0 && cinfo.isMultiStyles() &&
            transfer == null && (dropType == ChartConstants.DROP_REGION_Y ||
                                 dropType == ChartConstants.DROP_REGION_Y2))
         {
            ChartRef chartRef = orefs[orefs.length - 1];

            if(chartRef instanceof ChartAggregateRef) {
               multiChartType = ((ChartAggregateRef) chartRef).getChartType();
            }
         }

         for(int i = 0; i < refs.size(); i++) {
            ChartRef chartRef = refs.get(i);

            if(target.getReplace() && i == 0 && orefs.length > 0) {
               removeRefs.add(replaceChartRef(dropType, dropIndex, chartRef, cinfo));
            }
            else {
               if(multiChartType != null && (chartRef instanceof ChartAggregateRef)) {
                  ((ChartAggregateRef) chartRef).setChartType(multiChartType);
               }

               addChartRef(dropType, dropIndex + i, chartRef, cinfo);
            }
         }
      }

      // 6) Remove drag fields.
      removeDragFields(transfer, cinfo, target);

      return removeRefs;
   }

   public void dropToAesthetic(ChartRef ref, ChartInfo cinfo, ChartTransfer transfer,
                               ChartAestheticDropTarget target)
   {
      ChartBindable bindable = null;

      if(cinfo.isMultiAesthetic()) {
         ChartRefModel refModel = target.getAggr();

         if(refModel == null) {
            bindable = cinfo;
         }
         else {
            OriginalDescriptor descriptor = refModel.getOriginal();

            if(OriginalDescriptor.X_AXIS.equals(descriptor.getSource())) {
               bindable = (ChartAggregateRef) cinfo.getXField(descriptor.getIndex());
            }
            else if(OriginalDescriptor.Y_AXIS.equals(descriptor.getSource())) {
               bindable = (ChartAggregateRef) cinfo.getYField(descriptor.getIndex());
            }
            else if(OriginalDescriptor.START.equals(descriptor.getSource())) {
               bindable = (ChartAggregateRef) ((GanttChartInfo) cinfo).getStartField();
            }
            else if(OriginalDescriptor.MILESTONE.equals(descriptor.getSource())) {
               bindable = (ChartAggregateRef) ((GanttChartInfo) cinfo).getMilestoneField();
            }
            else if(OriginalDescriptor.ALL.equals(descriptor.getSource())) {
               bindable = getAllChartAggregateRef(cinfo);
            }
         }
      }
      else {
         bindable = cinfo;
      }

      // 1) Fix ref.
      fixChartRef(ref, transfer);

      if(ref instanceof ChartAggregateRef) {
         fixAggregate((ChartAggregateRef) ref, cinfo, transfer, target);
      }

      // 2) Check source change. ignore temporarily.

      // 3) If drag from X/Y/group/geo pane, mark the drag fields.
      if(transfer != null && isChartEditorRegion(transfer.getDragType())) {
         List<ChartRefModel> refsModel = transfer.getRefs();
         markFields(transfer, refsModel, cinfo);
      }

      // 4) Copy options.
      int dropType = Integer.parseInt(target.getDropType());
      AestheticRef aesRef = getAestheticRef(dropType, bindable, target.getTargetField());

      boolean newref = transfer == null;

      if(newref && aesRef != null) {
         if(ref instanceof XDimensionRef && aesRef.getDataRef() instanceof XDimensionRef) {
            ((XDimensionRef) ref).copyOptions((XDimensionRef) aesRef.getDataRef());
         }
      }

      // 5) Add aesthetic field.
      //fixed bug#20290 that need new aesRef when drag from others
      //Then can keep a new aesRef.

      int dragType = transfer != null ? Integer.parseInt(transfer.getDragType()) : -1;

      // if dragging from color to node color, keep the same ref so settings
      // (e.g. color palette) are not lost. (57383)
      if(dragType == dropType && transfer instanceof ChartAestheticTransfer) {
         aesRef = getAestheticRef(dragType, bindable,
                                  ((ChartAestheticTransfer) transfer).getTargetField());
      }
      else {
         aesRef = createAestheticRef(cinfo);
         aesRef.setDataRef(ref);
      }

      setAestheticRef(dropType, bindable, aesRef, cinfo, target.getTargetField());

      if(dropType == ChartConstants.DROP_REGION_SIZE &&
         GraphUtil.isNil(cinfo) && GraphTypeUtil.isMap(cinfo))
      {
         StaticShapeFrameWrapper filled_circle = new StaticShapeFrameWrapper();
         filled_circle.setShape(StyleConstants.FILLED_CIRCLE + "");
         cinfo.setShapeFrameWrapper(filled_circle);
      }

      // 6) Remove drag fields.
      removeDragFields(transfer, cinfo, target);
   }

   public ChartRef dropToChartView(ChartRef ref, ChartInfo cinfo, ChartTransfer transfer,
                                   BindingDropTarget target)
   {
      int dropType = Integer.parseInt(target.getDropType());

      if(dropType == ChartConstants.DROP_REGION_PLOT) {
         int atype = getAestheticDropType(ref, cinfo);

         if(atype > 0) {
            dropAesthetic(atype, ref, cinfo);
         }

         return null;
      }
      else if(isAestheticRegion(target.getDropType())) {
         dropAesthetic(dropType, ref, cinfo);
         return null;
      }

      ChartRef[] refs = new ChartRef[0];

      if(dropType == ChartConstants.DROP_REGION_X ||
         dropType == ChartConstants.DROP_REGION_X2)
      {
         refs = cinfo.getXFields();
      }
      else if(dropType == ChartConstants.DROP_REGION_Y ||
         dropType == ChartConstants.DROP_REGION_Y2)
      {
         refs = cinfo.getYFields();
      }

      boolean supportsY2 = !GraphTypes.isFunnel(cinfo.getChartType()) &&
         !GraphTypes.isContour(cinfo.getChartType());
      boolean y2 = supportsY2 && (cinfo.isInvertedGraph()
         ? dropType == ChartConstants.DROP_REGION_X2
         : dropType == ChartConstants.DROP_REGION_Y2);

      ChartRef removeRef = null;

      if(ref instanceof ChartAggregateRef) {
         if(GraphTypes.isFunnel(cinfo.getChartType()) && dropType == ChartConstants.DROP_REGION_Y) {
            return null;
         }

         boolean found = false;
         ChartAggregateRef aggr = (ChartAggregateRef) ref;
         aggr.setSecondaryY(y2);

         for(int i = 0; i < refs.length; i++) {
            if(refs[i] instanceof ChartAggregateRef) {
               if(((ChartAggregateRef) refs[i]).isSecondaryY() == y2) {
                  replaceChartRef(dropType, i, aggr, cinfo);
                  found = true;
                  break;
               }
            }
         }

         if(!found) {
            addChartRef(dropType, -1, aggr, cinfo);
         }
      }
      else if(refs.length == 0) {
         addChartRef(dropType, -1, ref, cinfo);
      }
      // dimension to Y
      else if(dropType == ChartConstants.DROP_REGION_Y) {
         // replace or insert at the top
         if(refs[0] instanceof ChartDimensionRef) {
            removeRef = replaceChartRef(dropType, 0, ref, cinfo);
         }
         else {
            addChartRef(dropType, -1, ref, cinfo);
         }
      }
      // secondary always append to end, primary replace 1st
      else if(dropType == ChartConstants.DROP_REGION_Y2) {
         addChartRef(dropType, -1, ref, cinfo);
      }
      // dimension to X
      else {
         // replace or insert at the top
         if(dropType == ChartConstants.DROP_REGION_X2) {
            if(refs[0] instanceof ChartDimensionRef && refs.length > 1) {
               removeRef = replaceChartRef(dropType, 0, ref, cinfo);
            }
            else {
               addChartRef(dropType, 0, ref, cinfo);
            }
         }
         // replace the ref at bottom x
         else if(refs[refs.length - 1] instanceof ChartDimensionRef) {
            removeRef = replaceChartRef(dropType, refs.length - 1, ref, cinfo);
         }
         else {
            for(int i = 0; i < refs.length; i++) {
               if(refs[i] instanceof ChartDimensionRef) {
                  addChartRef(dropType, i + 1, ref, cinfo);
                  break;
               }
            }
         }
      }

      // secondary axis is only used on merged graph
      if(y2 && ref instanceof ChartAggregateRef) {
         cinfo.setSeparatedGraph(false);
      }

      return removeRef;
   }

    /**
    * Get drop type for aesthetic.
    */
   public int getAestheticDropType(ChartRef ref, ChartInfo cinfo) {
      if(cinfo == null) {
         return -1;
      }

      int chartType = GraphTypeUtil.getChartType(cinfo);
      ChartBindable bindable = cinfo;

      if(cinfo.isMultiAesthetic()) {
         List<XAggregateRef> measures = GraphUtil.getMeasures(cinfo.getBindingRefs(true));

         if(measures.size() > 0) {
            bindable = (ChartBindable) measures.get(0);
         }
      }

      if(GraphTypes.supportsPoint(chartType, cinfo)) {
         if(bindable != null && bindable.getColorField() == null) {
            return ChartConstants.DROP_REGION_COLOR;
         }
         else if(bindable != null && bindable.getShapeField() == null) {
            return ChartConstants.DROP_REGION_SHAPE;
         }
         else if(bindable != null && bindable.getSizeField() == null) {
            return ChartConstants.DROP_REGION_SIZE;
         }
         else if(bindable != null && bindable.getTextField() == null) {
            return ChartConstants.DROP_REGION_TEXT;
         }
      }
      else if(bindable != null) {
         boolean interval = GraphTypes.isInterval(chartType);

         if(GraphTypes.supportsColor(chartType) && bindable.getColorField() == null) {
            return ChartConstants.DROP_REGION_COLOR;
         }
         else if((GraphTypes.supportsTexture(chartType) || GraphTypes.supportsShape(chartType) ||
            GraphTypes.supportsLine(chartType, cinfo)) && bindable.getShapeField() == null)
         {
            return ChartConstants.DROP_REGION_SHAPE;
         }
         else if(GraphTypes.supportsSize(chartType) && bindable.getSizeField() == null &&
            (!interval || ref instanceof ChartAggregateRef))
         {
            return ChartConstants.DROP_REGION_SIZE;
         }
         else if(bindable.getTextField() == null) {
            return ChartConstants.DROP_REGION_TEXT;
         }
      }

      return ChartConstants.DROP_REGION_COLOR;
   }

   /**
    * Drop a ref to an aesthetic field.
    */
   private void dropAesthetic(int dropType, ChartRef ref, ChartInfo cinfo) {
      fixChartRef(ref, null);
      AestheticRef aesRef = createAestheticRef(cinfo);
      aesRef.setDataRef(ref);
      List<ChartBindable> bindables = getChartInfoBindables(cinfo);

      for(ChartBindable bindable: bindables) {
         setAestheticRef(dropType, bindable, aesRef, cinfo, null);
      }
   }

   private AestheticRef createAestheticRef(ChartInfo cinfo) {
      return new VSAestheticRef();
   }

   private List<ChartBindable> getChartInfoBindables(ChartInfo cinfo) {
      List<ChartBindable> bindables = new ArrayList<>();

      if(cinfo.isMultiAesthetic()){
         //Fixed bug #19754,drop to plot area,chart view display incorrectness
         for(XAggregateRef measure: GraphUtil.getMeasures(cinfo.getBindingRefs(true))) {
            bindables.add((ChartBindable) measure);
         }

         //fixed bug #19379 that load col in visual pane(all) at multistyles
         bindables.add(getAllChartAggregateRef(cinfo));
      }
      else {
         bindables.add(cinfo);
      }

      return bindables;
   }

   private AllChartAggregateRef getAllChartAggregateRef(ChartInfo cinfo) {
      return new AllChartAggregateRef(cinfo, AllChartAggregateRef.getXYAggregateRefs(cinfo, false));
   }

   public void dropToHighLow(ChartRef ref, ChartInfo cinfo, ChartTransfer transfer,
                             BindingDropTarget target)
   {
      if(ref == null || !ref.isMeasure()) {
         return;
      }

      int dropType = Integer.parseInt(target.getDropType());
      ChartAggregateRef aref = (ChartAggregateRef) ref;
      aref.setDiscrete(false);
      fixAggregate(aref, cinfo, transfer, target);

      if(transfer != null) {
         GraphUtil.fixStaticColorFrame(aref, cinfo, null);
      }

      // Check source change, ignore temporarily.
      // if(sourceChanged()) {
      // }

      // If drag from X/Y/group/geo pane, marke drag fields.
      if(transfer != null && isChartEditorRegion(transfer.getDragType())) {
         List<ChartRefModel> refsModel = transfer.getRefs();
         markFields(transfer, refsModel, cinfo);
      }

      if(dropType == ChartConstants.DROP_REGION_HIGH) {
         ((CandleChartInfo) cinfo).setHighField(aref);
      }
      else if(dropType == ChartConstants.DROP_REGION_LOW) {
         ((CandleChartInfo) cinfo).setLowField(aref);
      }
      else if(dropType == ChartConstants.DROP_REGION_OPEN) {
         ((CandleChartInfo) cinfo).setOpenField(aref);
      }
      else {
         ((CandleChartInfo) cinfo).setCloseField(aref);
      }

      removeDragFields(transfer, cinfo);
   }

   public void dropToTree(ChartRef ref, ChartInfo cinfo, ChartTransfer transfer,
                             BindingDropTarget target)
   {
      if(ref == null || ref.isMeasure()) {
         return;
      }

      int dropType = Integer.parseInt(target.getDropType());

      // If drag from X/Y/group/geo pane, marke drag fields.
      if(transfer != null && isChartEditorRegion(transfer.getDragType())) {
         List<ChartRefModel> refsModel = transfer.getRefs();
         markFields(transfer, refsModel, cinfo);
      }

      RelationChartInfo cinfo2 = (RelationChartInfo) cinfo;

      if(dropType == ChartConstants.DROP_REGION_SOURCE) {
         cinfo2.setSourceField(ref);

         // don't support same field on source and target
         // date levels can be changed later so allow it.
         if(cinfo2.getTargetField() != null && !XSchema.isDateType(ref.getDataType()) &&
            ref.getFullName().equals(cinfo2.getTargetField().getFullName()))
         {
            cinfo2.setTargetField(null);
         }
      }
      else {
         cinfo2.setTargetField(ref);

         // don't support same field on source and source
         if(cinfo2.getSourceField() != null && !XSchema.isDateType(ref.getDataType()) &&
            ref.getFullName().equals(cinfo2.getSourceField().getFullName()))
         {
            cinfo2.setSourceField(null);
         }
      }

      removeDragFields(transfer, cinfo);
   }

   public void dropToGantt(ChartRef ref, ChartInfo cinfo, ChartTransfer transfer,
                             BindingDropTarget target)
   {
      if(ref == null || !ref.isMeasure()) {
         return;
      }

      if(ref instanceof ChartAggregateRef) {
         if(!XSchema.isDateType(((ChartAggregateRef) ref).getOriginalDataType())) {
            throw new MessageException(
               Catalog.getCatalog().getString("common.DataEditor.ganttDateField"));
         }

         ((ChartAggregateRef) ref).setFormula(AggregateFormula.NONE);
      }

      int dropType = Integer.parseInt(target.getDropType());

      // If drag from X/Y/group/geo pane, marke drag fields.
      if(transfer != null && isChartEditorRegion(transfer.getDragType())) {
         List<ChartRefModel> refsModel = transfer.getRefs();
         markFields(transfer, refsModel, cinfo);
      }

      GanttChartInfo cinfo2 = (GanttChartInfo) cinfo;

      if(dropType == ChartConstants.DROP_REGION_START) {
         cinfo2.setStartField(ref);

         // don't support same field on source and target
         if(cinfo2.getEndField() != null &&
            ref.getFullName().equals(cinfo2.getEndField().getFullName()))
         {
            cinfo2.setEndField(null);
         }
      }
      else if(dropType == ChartConstants.DROP_REGION_END) {
         cinfo2.setEndField(ref);

         // don't support same field on start and start
         if(cinfo2.getStartField() != null &&
            ref.getFullName().equals(cinfo2.getStartField().getFullName()))
         {
            cinfo2.setStartField(null);
         }
      }
      else {
         cinfo2.setMilestoneField(ref);

         if(ref instanceof ChartAggregateRef) {
            ShapeFrame sframe = ((ChartAggregateRef) ref).getShapeFrame();

            if(sframe instanceof StaticShapeFrame) {
               ((StaticShapeFrame) sframe).setShape(GShape.FILLED_DIAMOND);
            }
         }
      }

      removeDragFields(transfer, cinfo);
   }

   public List<ChartRef> removeBindingRefs(ChartInfo cinfo, ChartTransfer transfer) {
      ChartInfo oinfo = (ChartInfo) cinfo.clone();
      List<ChartRef> removeRefs = new ArrayList<>();

      if(isChartEditorRegion(transfer.getDragType())) {
         List<ChartRefModel> refsModel = transfer.getRefs();
         removeRefs = markFields(transfer, refsModel, cinfo);
      }

      removeDragFields(transfer, cinfo);
      GraphDefault.setDefaultFormulas(oinfo, cinfo);
      removeByValueSorts(cinfo);
      List<String> removeFields = new ArrayList<>();
      removeRefs.stream().forEach(r -> removeFields.add(r.getFullName()));
      ArrayList<DataRef> list = new ArrayList<>();
      list.addAll(Arrays.asList(cinfo.getAggregateRefs()));
      list.addAll(Arrays.asList(cinfo.getBindingRefs(false)));
      updateAggregateColNames(list, removeFields);
      return removeRefs;
   }

   private void removeByValueSorts(ChartInfo cinfo) {
      if(cinfo.getAggregateRefs().length != 0) {
         return;
      }

      for(Object ref : cinfo.getRTFields()) {
         if(!(ref instanceof ChartDimensionRef)) {
            continue;
         }

         ChartDimensionRef cref = (ChartDimensionRef) ref;
         boolean hasNamedGroupInfo = cref.getRealNamedGroupInfo() != null;
         int defaultOrder = hasNamedGroupInfo ?
            XConstants.SORT_ASC + XConstants.SORT_SPECIFIC :  XConstants.SORT_ASC;
         int order = cref.getOrder();

         if(hasNamedGroupInfo) {
            order &= ~XConstants.SORT_SPECIFIC;
         }

         if(order == XConstants.SORT_VALUE_ASC || order == XConstants.SORT_VALUE_DESC) {
            ((ChartDimensionRef) ref).setOrder(defaultOrder);
         }
      }
   }


   /**
    * Updates the aggregate column values for aggregates affected by a dLevel change
    */
   public void updateAggregateColNames(ArrayList<DataRef> refs, List<String> removeFields) {
      for(DataRef ref : refs) {
         if(!(ref instanceof XAggregateRef)) {
            continue;
         }

         Calculator calc = ((XAggregateRef) ref).getCalculator();

         if(calc instanceof ValueOfCalc) {
            ValueOfCalc valueOfCalc = (ValueOfCalc) calc;

            if(removeFields.contains(valueOfCalc.getColumnName())) {
               valueOfCalc.setColumnName(null);

               final int from = valueOfCalc.getFrom();

               if(from == ValueOfCalc.PREVIOUS_YEAR || from == ValueOfCalc.PREVIOUS_QUARTER ||
                  from == ValueOfCalc.PREVIOUS_WEEK || from == ValueOfCalc.PREVIOUS_MONTH ||
                  from == ValueOfCalc.PREVIOUS_RANGE)
               {
                  valueOfCalc.setFrom(ValueOfCalc.PREVIOUS);
               }
            }
         }

         if(calc instanceof RunningTotalCalc){
            RunningTotalCalc runningTotalCalc = (RunningTotalCalc) calc;

            if(removeFields.contains(runningTotalCalc.getBreakBy())) {
               runningTotalCalc.setBreakBy(null);
            }
         }
      }
   }

   private void fixChartRef(ChartRef ref, ChartTransfer transfer) {
      if(ref instanceof VSChartDimensionRef) {
         final VSChartDimensionRef chartDimRef = (VSChartDimensionRef) ref;
         final String dateLevelValue = chartDimRef.getDateLevelValue();

         if(dateLevelValue != null && Integer.parseInt(dateLevelValue) <= 0) {
            final int num = GraphUtil.getNextDateLevelValue(ref, new ArrayList<>(), 0);
            chartDimRef.setDateLevelValue(String.valueOf(num));
         }

         chartDimRef.setTimeSeries(false);
      }
      else if(ref instanceof ChartAggregateRef) {
         ChartAggregateRef ref0 = (ChartAggregateRef) ref;
         boolean existing = transfer != null;

         if(!ref0.isAggregateEnabled() && !existing && !ref0.isDiscrete()) {
            AggregateFormula formula = AssetUtil.getDefaultFormula(ref0);
            String aggformula;

            if(ref0 instanceof VSChartAggregateRef) {
               aggformula = ((VSChartAggregateRef) ref0).getFormulaValue();
            }
            else {
               aggformula = ref0.getFormula() + "";
            }

            if((ref0.getRefType() & AbstractDataRef.CUBE_MEASURE) ==
               AbstractDataRef.CUBE_MEASURE && "None".equalsIgnoreCase(aggformula))
            {
               formula = AggregateFormula.NONE;
            }

            ref0.setFormula(formula);
         }

         if(AssetUtil.isStringAggCalcField(ref0) && existing) {
            ref0.setDiscrete(true);
         }
      }
   }

   /**
    * Get aesthetic type.
    */
   public int getAestheticType(int dropType) {
      int atype = -1;

      if(dropType == ChartConstants.DROP_REGION_COLOR) {
         atype = ChartConstants.AESTHETIC_COLOR;
      }
      else if(dropType == ChartConstants.DROP_REGION_SHAPE) {
         atype = ChartConstants.AESTHETIC_SHAPE;
      }
      else if(dropType == ChartConstants.DROP_REGION_SIZE) {
         atype = ChartConstants.AESTHETIC_SIZE;
      }
      else if(dropType == ChartConstants.DROP_REGION_TEXT) {
         atype = ChartConstants.AESTHETIC_TEXT;
      }

      return atype;
   }

   private void setAestheticRef(int dropType, ChartBindable bindable,
         AestheticRef ref, ChartInfo cinfo, String targetField)
   {
      if(bindable instanceof RelationChartInfo && "nodeColorField".equals(targetField)) {
         ((RelationChartInfo) bindable).setNodeColorField(ref);
      }
      else if(bindable instanceof RelationChartInfo && "nodeSizeField".equals(targetField)) {
         ((RelationChartInfo) bindable).setNodeSizeField(ref);
      }
      else if(dropType == ChartConstants.DROP_REGION_COLOR) {
         bindable.setColorField(ref);
      }
      else if(dropType == ChartConstants.DROP_REGION_SHAPE) {
         bindable.setShapeField(ref);
      }
      else if(dropType == ChartConstants.DROP_REGION_SIZE) {
         bindable.setSizeField(ref);
      }
      else if(dropType == ChartConstants.DROP_REGION_TEXT) {
         bindable.setTextField(ref);
      }

      fixVisualFrame(dropType, bindable, cinfo, ref);
   }

   private void fixVisualFrame(int dropType, ChartBindable bindable, ChartInfo cinfo,
                               AestheticRef aesRef)
   {
      int atype = getAestheticType(dropType);

      if(bindable instanceof AllChartAggregateRef) {
         for(Object aggr : ((AllChartAggregateRef) bindable).getChartAggregateRefs()) {
            ChartAggregateRef aref = (ChartAggregateRef) aggr;
            AestheticRef ref = getAestheticRef(dropType, aref, null);
            GraphUtil.fixVisualFrame(ref, atype, getChartType(aref), cinfo);
         }
      }
      else {
         GraphUtil.fixVisualFrame(aesRef, atype, getChartType(bindable), cinfo);
      }
   }

   private int getChartType(ChartBindable bindable) {
      return bindable.getChartType() == GraphTypes.CHART_AUTO ?
         bindable.getRTChartType() : bindable.getChartType();
   }

   public AestheticRef getAestheticRef(int dropType, ChartBindable bindable, String targetField) {
      AestheticRef ref = null;

      if(bindable instanceof RelationChartInfo && "nodeColorField".equals(targetField)) {
         ref = ((RelationChartInfo) bindable).getNodeColorField();
      }
      else if(bindable instanceof RelationChartInfo && "nodeSizeField".equals(targetField)) {
         ref = ((RelationChartInfo) bindable).getNodeSizeField();
      }
      else if(dropType == ChartConstants.DROP_REGION_COLOR) {
         ref = bindable.getColorField();
      }
      else if(dropType == ChartConstants.DROP_REGION_SHAPE) {
         ref = bindable.getShapeField();
      }
      else if(dropType == ChartConstants.DROP_REGION_SIZE) {
         ref = bindable.getSizeField();
      }
      else if(dropType == ChartConstants.DROP_REGION_TEXT) {
         ref = bindable.getTextField();
      }

      return ref;
   }

   public void removeDragFields(ChartTransfer transfer, ChartInfo cinfo) {
      removeDragFields(transfer, cinfo, null);
   }

   public void removeDragFields(ChartTransfer transfer, ChartInfo cinfo, BindingDropTarget target) {
      // Drag from tree, need not remove drag fields.
      if(transfer == null) {
         return;
      }

      // If drag from X/Y/group/geo pane, remove marked fields.
      if(isChartEditorRegion(transfer.getDragType())) {
         removeMarkedFields(transfer, cinfo);
      }
      // If drag from high/low pane, remove high/low field.
      else if(isHighLowRegion(transfer.getDragType())) {
         removeHighLowField(transfer, (CandleChartInfo) cinfo);
      }
      // If drag from aesthetic pane, remove aesthetic field.
      else if(isAestheticRegion(transfer.getDragType())) {
         removeAestheticField(transfer, cinfo, target);
      }
      else if(isPathRegion(transfer.getDragType())) {
         cinfo.setPathField(null);
      }
      else if(isSourceRegion(transfer.getDragType())) {
         ((RelationChartInfo) cinfo).setSourceField(null);
      }
      else if(isTargetRegion(transfer.getDragType())) {
         ((RelationChartInfo) cinfo).setTargetField(null);
      }
      else if(isStartRegion(transfer.getDragType())) {
         ((GanttChartInfo) cinfo).setStartField(null);
      }
      else if(isEndRegion(transfer.getDragType())) {
         ((GanttChartInfo) cinfo).setEndField(null);
      }
      else if(isMilestoneRegion(transfer.getDragType())) {
         ((GanttChartInfo) cinfo).setMilestoneField(null);
      }
   }

   /**
    * Remove marked fields.
    */
   public void removeMarkedFields(ChartTransfer transfer, ChartInfo cinfo) {
      // Drag from tree, need not remove drag fields.
      if(transfer == null) {
         return;
      }

      String dType = transfer.getDragType();
      int dragType = Integer.parseInt(dType);

      if(dragType == ChartConstants.DROP_REGION_X) {
         for(int i = 0; i < cinfo.getXFieldCount(); i++) {
            if(cinfo.getXField(i) == null) {
               cinfo.removeXField(i);
            }
         }
      }
      else if(dragType == ChartConstants.DROP_REGION_Y) {
         for(int i = 0; i < cinfo.getYFieldCount(); i++) {
            if(cinfo.getYField(i) == null) {
               cinfo.removeYField(i);
            }
         }
      }
      else if(dragType == ChartConstants.DROP_REGION_GROUP) {
         for(int i = 0; i < cinfo.getGroupFieldCount(); i++) {
            if(cinfo.getGroupField(i) == null) {
               cinfo.removeGroupField(i);
            }
         }
      }
      else if(dragType == ChartConstants.DROP_REGION_GEO) {
         for(int i = 0; i < ((MapInfo) cinfo).getGeoFieldCount(); i++) {

            if(((MapInfo) cinfo).getGeoFieldByName(i) == null) {
               ((MapInfo) cinfo).removeGeoField(i);
            }
         }
      }
   }

   public void removeAestheticField(ChartTransfer transfer, ChartInfo cinfo,
                                    BindingDropTarget target)
   {
      ChartAestheticTransfer aestheticTransfer = (ChartAestheticTransfer) transfer;
      ChartBindable bindable = null;

      if(cinfo.isMultiAesthetic()) {
         ChartRefModel refModel = aestheticTransfer.getAggr();

         if(refModel == null) {
            bindable = cinfo;
         }
         else if(refModel instanceof AllChartAggregateRefModel) {
            bindable = getAllChartAggregateRef(cinfo);
         }
         else {
            OriginalDescriptor descriptor = refModel.getOriginal();
            int index = descriptor.getIndex();

            if(OriginalDescriptor.X_AXIS.equals(descriptor.getSource())) {
               bindable = (ChartAggregateRef) cinfo.getXField(index);
            }
            else if(OriginalDescriptor.Y_AXIS.equals(descriptor.getSource())) {
               bindable = (ChartAggregateRef) cinfo.getYField(index);
            }
            else if(OriginalDescriptor.START.equals(descriptor.getSource())) {
               bindable = (ChartBindable) ((GanttChartInfo) cinfo).getStartField();
            }
            else if(OriginalDescriptor.MILESTONE.equals(descriptor.getSource())) {
               bindable = (ChartBindable) ((GanttChartInfo) cinfo).getMilestoneField();
            }
         }
      }
      else {
         bindable = cinfo;
      }

      if(bindable != null) {
         int dragType = Integer.parseInt(transfer.getDragType());

         if(bindable instanceof RelationChartInfo &&
            "nodeColorField".equals(aestheticTransfer.getTargetField()))
         {
            ((RelationChartInfo) bindable).setNodeColorField(null);
         }
         else if(bindable instanceof RelationChartInfo &&
            "nodeSizeField".equals(aestheticTransfer.getTargetField()))
         {
            ((RelationChartInfo) bindable).setNodeSizeField(null);
         }
         else if(dragType == ChartConstants.DROP_REGION_COLOR) {
            bindable.setColorField(null);
         }
         else if(dragType == ChartConstants.DROP_REGION_SHAPE) {
            bindable.setShapeField(null);
         }
         else if(dragType == ChartConstants.DROP_REGION_SIZE) {
            bindable.setSizeField(null);
         }
         else {
            bindable.setTextField(null);
         }
      }
   }

   public void removeHighLowField(ChartTransfer transfer, CandleChartInfo cinfo) {
      int dragType = Integer.parseInt(transfer.getDragType());

      if(dragType == ChartConstants.DROP_REGION_HIGH) {
         cinfo.setHighField(null);
      }
      else if(dragType == ChartConstants.DROP_REGION_LOW) {
         cinfo.setLowField(null);
      }
      else if(dragType == ChartConstants.DROP_REGION_OPEN) {
         cinfo.setOpenField(null);
      }
      else {
         cinfo.setCloseField(null);
      }
   }

   /**
    * Replace chart ref.
    */
   public ChartRef replaceChartRef(int dropType, int repIdx, ChartRef ref, ChartInfo cinfo) {
      ChartRef removeRef = null;

      if(dropType == ChartConstants.DROP_REGION_X ||
         dropType == ChartConstants.DROP_REGION_X2)
      {
         removeRef = cinfo.getXField(repIdx);
         fixStaticColorFrame(ref, ref, cinfo);
         cinfo.setXField(Math.min(repIdx, cinfo.getXFieldCount()), ref);
      }
      else if(dropType == ChartConstants.DROP_REGION_Y ||
         dropType == ChartConstants.DROP_REGION_Y2)
      {
         //Fixed bug #19566 that only has one measure on y pane,should not to fix.
         if(getYPaneAggrCount(cinfo) > 1) {
            fixStaticColorFrame(ref, ref, cinfo);
         }

         removeRef = cinfo.getYField(repIdx);
         cinfo.setYField(Math.min(repIdx, cinfo.getYFieldCount()), ref);
      }
      else if(dropType == ChartConstants.DROP_REGION_GROUP) {
         removeRef = cinfo.getGroupField(repIdx);
         cinfo.setGroupField(Math.min(repIdx, cinfo.getGroupFieldCount() - 1), ref);
      }
      else if(dropType == ChartConstants.DROP_REGION_GEO) {
         ((MapInfo) cinfo).setGeoField(Math.min(repIdx, ((MapInfo) cinfo).getGeoFieldCount()), ref);
      }

      return removeRef;
   }

   /**
    * Add chart ref to X/Y fields.
    */
   public void addChartRef(int dropType, int addIdx, ChartRef ref, ChartInfo cinfo) {
      if(dropType == ChartConstants.DROP_REGION_X ||
         dropType == ChartConstants.DROP_REGION_X2 ||
         dropType == ChartConstants.DROP_REGION_Y ||
         dropType == ChartConstants.DROP_REGION_Y2)
      {
         fixStaticColorFrame(ref, null, cinfo);
      }

      // mekko only support x/y and one group (inner dim)
      boolean single = GraphTypes.isMekko(cinfo.getChartType());

      if(dropType == ChartConstants.DROP_REGION_X || dropType == ChartConstants.DROP_REGION_X2) {
         if(single) {
            cinfo.removeXFields();
         }

         cinfo.addXField(addIdx, ref);
      }
      else if(dropType == ChartConstants.DROP_REGION_Y ||
         dropType == ChartConstants.DROP_REGION_Y2)
      {
         if(single) {
            cinfo.removeYFields();
         }

         cinfo.addYField(addIdx, ref);
      }
      else if(dropType == ChartConstants.DROP_REGION_GROUP) {
         if(single) {
            cinfo.removeGroupFields();
         }

         cinfo.addGroupField(addIdx, ref);
      }
      else if(dropType == ChartConstants.DROP_REGION_GEO) {
         ((MapInfo) cinfo).addGeoField(addIdx, ref);
      }
   }

   /**
    * Fix static color frame for the new added xy chart aggregate, get the color from
    * CategoricalColorFrame.COLOR_PALETTE.
    * If replace an existing field, don't thinks about the static color
    * in that field
    * @param ref the chartref which should be fixed.
    * @param oldRef the ref which will be replace by the new add chart ref.
    * @param cinfo current chart info.
    */
   private void fixStaticColorFrame(ChartRef ref, ChartRef oldRef, ChartInfo cinfo) {
      if(!(ref instanceof ChartAggregateRef)) {
         return;
      }

      ChartAggregateRef movedRef = oldRef instanceof ChartAggregateRef ? (ChartAggregateRef) oldRef : null;
      GraphUtil.fixStaticColorFrame(ref, cinfo, movedRef);
   }

   private void fixDimension(ChartDimensionRef ref, ChartInfo cinfo, BindingDropTarget target) {
      if(XSchema.isDateType(ref.getDataType())) {
         int level = -1;

         if(ref instanceof VSChartDimensionRef) {
            VSChartDimensionRef vref = (VSChartDimensionRef) ref;

            try {
               level = vref.getDateLevelValue() == null ?
                  -1 : Integer.parseInt(vref.getDateLevelValue());
            }
            catch(NumberFormatException formatException) {
               LOG.debug("Cannot determine dateLevel value for {}", vref.getName(), formatException);
            }
         }
         else {
            level = ref.getDateLevel();
         }

         int dropType = Integer.parseInt(target.getDropType());

         if((ref.getOrder() & XConstants.SORT_SPECIFIC) == 0) {
            ref.setTimeSeries(DateRangeRef.isDateTime(level) &&
                              (dropType == ChartConstants.DROP_REGION_X ||
                               dropType == ChartConstants.DROP_REGION_Y));
         }
      }
   }

   private void fixAggregate(ChartAggregateRef ref, ChartInfo cinfo,
                             ChartTransfer transfer, BindingDropTarget target)
   {
      if(!GraphTypes.isGeo(cinfo.getChartType())) {
         if(ref instanceof VSAggregateRef && ref.getFormula() != null &&
            ref.getFormula().isTwoColumns() &&
            ((VSAggregateRef) ref).getSecondaryColumnValue() == null)
         {
            ref.setFormula(AggregateFormula.getFormulas()[0]);
         }

         String formula;

         if(ref instanceof VSChartAggregateRef) {
            formula = ((VSChartAggregateRef) ref).getFormulaValue();
         }
         else {
            formula = ref.getFormula() + "";
         }

         //set the default formula
         AggregateFormula ff = AssetUtil.getDefaultFormula(ref);
         boolean existing = transfer != null;

         /* if dnd from x to y or vise versa, it doesn't seem to make sense to change
            the formula. a user won't expect the definition of an aggregate to change
            when moving bewteen fields
         if(existing) {
            //from another to x pane, change formula to null
            if(Integer.parseInt(target.getDropType()) == ChartConstants.DROP_REGION_X &&
               !"None".equals(formula) &&
               !(getYPaneAggrCount(cinfo) == 0 ||
                 Integer.parseInt(transfer.getDragType()) == ChartConstants.DROP_REGION_Y &&
                 getYPaneAggrCount(cinfo) == 1))
            {
               ff = AggregateFormula.NONE;
               ref.setFormula(ff);
            }
            else {
               //from x to y, change formula to default when formula is null
               ref.setFormula(formula == null ? ff : ref.getFormula());
            }
         }
         */

         // if the aggregate is moved here from another binding,
         // don't change the formula
         if(!ref.isAggregateEnabled() && !existing) {
            if((ref.getRefType() & AbstractDataRef.CUBE_MEASURE) != 0 &&
               "None".equalsIgnoreCase(formula))
            {
               ff = AggregateFormula.NONE;
            }

            ref.setFormula(ff);
         }

         if(cinfo instanceof GanttChartInfo) {
            ref.setFormula(AggregateFormula.NONE);
         }

         if(isXYRegion(target.getDropType()) && !existing) {
            if(AssetUtil.isStringAggCalcField(ref) && transfer != null) {
               ref.setDiscrete(true);
            }

            AllChartAggregateRef all = getAllChartAggregateRef(cinfo);

            if(!isAestheticRegion(target.getDropType())) {
               ref.setColorField(all.getColorField());
               ref.setShapeField(all.getShapeField());
               ref.setSizeField(all.getSizeField());
               ref.setTextField(all.getTextField());
            }
         }
      }

      if(ref.getRefType() == DataRef.AGG_EXPR) {
         ref.setFormula(AggregateFormula.NONE);
      }
   }

   /**
   * according to ChartInfo get ypane ChartAggregateRef count
   */
   private int getYPaneAggrCount(ChartInfo cinfo) {
      ArrayList<ChartRef> list = new ArrayList<>();

      for(ChartRef cref : cinfo.getYFields()) {
         if(cref instanceof ChartAggregateRef) {
            list.add(cref);
         }
      }

      return list.size();
   }

    /**
    * Mark remove fields.
    * Set remove fields to null.
     */
   public List<ChartRef> markFields(ChartTransfer transfer, List<ChartRefModel> refsModel,
                                    ChartInfo cinfo)
   {
      // Drag from tree, need not remove drag fields.
      if(transfer == null) {
         return null;
      }

      String dType = transfer.getDragType();
      int dragType = Integer.parseInt(dType);
      List<ChartRef> removeRefs = new ArrayList<>();

      if(dragType == ChartConstants.DROP_REGION_X) {
         for(ChartRefModel refModel : refsModel) {
            int index = refModel.getOriginal().getIndex();

            if(index >= 0 && index < cinfo.getXFieldCount()) {
               removeRefs.add(cinfo.getXField(index));
               cinfo.setXField(index, null);
            }
         }
      }
      else if(dragType == ChartConstants.DROP_REGION_Y) {
         for(ChartRefModel refModel : refsModel) {
            int index = refModel.getOriginal().getIndex();

            if(index >= 0 && index < cinfo.getYFieldCount()) {
               removeRefs.add(cinfo.getYField(index));
               cinfo.setYField(index, null);
            }
         }
      }
      else if(dragType == ChartConstants.DROP_REGION_GROUP) {
         for(ChartRefModel refModel : refsModel) {
            int index = refModel.getOriginal().getIndex();

            if(index >= 0&& index < cinfo.getGroupFieldCount()) {
               removeRefs.add(cinfo.getGroupField(index));
               cinfo.setGroupField(index, null);
            }
         }
      }
      else if(dragType == ChartConstants.DROP_REGION_GEO) {
         for(ChartRefModel refModel : refsModel) {
            int index = refModel.getOriginal().getIndex();

            if(index >= 0 && index < ((MapInfo) cinfo).getGeoFieldCount()) {
               removeRefs.add(((MapInfo) cinfo).getGeoFieldByName(index));
               ((MapInfo) cinfo).setGeoField(index, null);
            }
         }
      }
      else if(dragType == ChartConstants.DROP_REGION_SOURCE) {
         removeRefs.add(((RelationChartInfo) cinfo).getSourceField());
         ((RelationChartInfo) cinfo).setSourceField(null);
      }
      else if(dragType == ChartConstants.DROP_REGION_TARGET) {
         removeRefs.add(((RelationChartInfo) cinfo).getTargetField());
         ((RelationChartInfo) cinfo).setTargetField(null);
      }
      else if(dragType == ChartConstants.DROP_REGION_START) {
         removeRefs.add(((GanttChartInfo) cinfo).getStartField());
         ((GanttChartInfo) cinfo).setStartField(null);
      }
      else if(dragType == ChartConstants.DROP_REGION_END) {
         removeRefs.add(((GanttChartInfo) cinfo).getEndField());
         ((GanttChartInfo) cinfo).setEndField(null);
      }
      else if(dragType == ChartConstants.DROP_REGION_MILESTONE) {
         removeRefs.add(((GanttChartInfo) cinfo).getMilestoneField());
         ((GanttChartInfo) cinfo).setMilestoneField(null);
      }

      return removeRefs;
   }

   /**
    * Check if drag or drop type is X/Y, group, geo regions.
    */
   public boolean isChartEditorRegion(String type) {
      return isXYRegion(type) || isGroupRegion(type) || isGeoRegion(type);
   }

   /**
    * Check if drag or drop type is X/Y region.
    */
   public boolean isXYRegion(String type) {
      try {
         int type0 = Integer.parseInt(type);

         return type0 == ChartConstants.DROP_REGION_X ||
            type0 == ChartConstants.DROP_REGION_X2 ||
            type0 == ChartConstants.DROP_REGION_Y ||
            type0 == ChartConstants.DROP_REGION_Y2;
      }
      catch(NumberFormatException e) {
         return false;
      }
   }

   /**
    * Check if drag or drop type is aesthetic region.
    */
   public static boolean isAestheticRegion(String type) {
      try {
         int type0 = Integer.parseInt(type);

         return isAestheticRegion(type0);
      }
      catch(NumberFormatException e) {
         return false;
      }
   }

   /**
    * Check if drag or drop type is aesthetic region.
    */
   public static boolean isAestheticRegion(int type0) {
      return type0 == ChartConstants.DROP_REGION_COLOR ||
         type0 == ChartConstants.DROP_REGION_SHAPE ||
         type0 == ChartConstants.DROP_REGION_SIZE ||
         type0 == ChartConstants.DROP_REGION_TEXT;
   }

   /**
    * Check if drag or drop type is high/low/open/close region.
    */
   public boolean isHighLowRegion(String type) {
      try {
         return GraphUtil.isHighLowRegion(Integer.parseInt(type));
      }
      catch(NumberFormatException e) {
         return false;
      }
   }

   public boolean isTreeRegion(String type) {
      try {
         return GraphUtil.isTreeRegion(Integer.parseInt(type));
      }
      catch(NumberFormatException e) {
         return false;
      }
   }

   public boolean isGanttRegion(String type) {
      try {
         return GraphUtil.isGanttRegion(Integer.parseInt(type));
      }
      catch(NumberFormatException e) {
         return false;
      }
   }

   /**
    * Check if drag or drop type is group region.
    */
   public boolean isGroupRegion(String type) {
      try {
         return Integer.parseInt(type) == ChartConstants.DROP_REGION_GROUP;
      }
      catch(NumberFormatException e) {
         return false;
      }
   }

   /**
    * Check if drag or drop type is path region.
    */
   public boolean isPathRegion(String type) {
      try {
         return Integer.parseInt(type) == ChartConstants.DROP_REGION_PATH;
      }
      catch(NumberFormatException e) {
         return false;
      }
   }

   /**
    * Check if drag or drop type is geo region for map chart.
    */
   private boolean isGeoRegion(String type) {
      try {
         return Integer.parseInt(type) == ChartConstants.DROP_REGION_GEO;
      }
      catch(NumberFormatException e) {
         return false;
      }
   }

   private boolean isSourceRegion(String type) {
      try {
         return Integer.parseInt(type) == ChartConstants.DROP_REGION_SOURCE;
      }
      catch(NumberFormatException e) {
         return false;
      }
   }

   private boolean isTargetRegion(String type) {
      try {
         return Integer.parseInt(type) == ChartConstants.DROP_REGION_TARGET;
      }
      catch(NumberFormatException e) {
         return false;
      }
   }

   private boolean isStartRegion(String type) {
      try {
         return Integer.parseInt(type) == ChartConstants.DROP_REGION_START;
      }
      catch(NumberFormatException e) {
         return false;
      }
   }

   private boolean isEndRegion(String type) {
      try {
         return Integer.parseInt(type) == ChartConstants.DROP_REGION_END;
      }
      catch(NumberFormatException e) {
         return false;
      }
   }

   private boolean isMilestoneRegion(String type) {
      try {
         return Integer.parseInt(type) == ChartConstants.DROP_REGION_MILESTONE;
      }
      catch(NumberFormatException e) {
         return false;
      }
   }

   /**
    * Change all object to dimension.
    */
   public List<ChartRef> getDimensions(List<ChartRef> refs) {
      List<ChartRef> dims = new ArrayList<>();
      ChartRef[] chartRefs = refs.toArray(new ChartRef[0]);

      for(ChartRef chartRef : chartRefs) {
         if(GraphUtil.isDimension(chartRef)) {
            dims.add(chartRef);
         }
      }

      return dims;
   }

   private List<ChartRef> getAggregates(List<ChartRef> refs) {
      List<ChartRef> aggrs = new ArrayList<>();

      for(ChartRef ref : refs) {
         aggrs.add(ref);
      }

      return aggrs;
   }

   public List<ChartRef> getFixedXYRef(ChartInfo cinfo, List<ChartRef> refs,
                                       String dtype, String stype)
   {
      int dropType = Integer.parseInt(dtype);

      if(dropType != ChartConstants.DROP_REGION_PLOT &&
      (GraphTypes.isCandle(cinfo.getChartType()) || GraphTypes.isStock(cinfo.getChartType()) ||
         GraphTypes.isRelation(cinfo.getChartType()) || GraphTypes.isGantt(cinfo.getChartType())))
      {
         return getDimensions(refs);
      }
      else if(GraphTypes.isGeo(cinfo.getChartType())) {
         if(isGeoRegion(dtype)) {
            return convertToGeoRefs(refs);
         }
         else if((dropType == ChartConstants.DROP_REGION_X ||
                  dropType == ChartConstants.DROP_REGION_X2 ||
                  dropType == ChartConstants.DROP_REGION_Y ||
                  dropType == ChartConstants.DROP_REGION_Y2) &&
                  !isAestheticRegion(stype))
         {
            return fixMapFormula(refs);
         }
      }
      else if(GraphTypes.isTreemap(cinfo.getChartType())) {
         if(dropType == ChartConstants.DROP_REGION_X ||
            dropType == ChartConstants.DROP_REGION_Y ||
            dropType == ChartConstants.DROP_REGION_X2 ||
            dropType == ChartConstants.DROP_REGION_Y2 ||
            dropType == ChartConstants.DROP_REGION_GROUP)
         {
            return getDimensions(refs);
         }
      }
      else if(GraphTypes.isFunnel(cinfo.getChartType())) {
         if(dropType == ChartConstants.DROP_REGION_Y) {
            return getDimensions(refs);
         }
      }

      if(dropType == ChartConstants.DROP_REGION_Y) {
         if(GraphTypes.isMekko(cinfo.getChartType())) {
            return getAggregates(refs);
         }

         return refs;
      }
      else if(dropType == ChartConstants.DROP_REGION_X) {
         if(GraphTypeUtil.supportsInvertedChart(cinfo)) {
            return refs;
         }

         return getDimensions(refs);
      }
      else if(dropType == ChartConstants.DROP_REGION_GROUP) {
         if(GraphTypes.isMekko(cinfo.getChartType())) {
            return getDimensions(refs);
         }
      }

      return refs;
   }

   public int getNextDateLevelValue(ChartInfo cinfo, XDimensionRef dim, String name,
      String dropType, int dropIndex)
   {
      int level = -1;

      if(isAestheticRegion(dropType)) {
         level = GraphUtil.getNextDateLevelValue(dim, new ArrayList<>(), 0);
      }
      else if(isXYRegion(dropType) || isGroupRegion(dropType) || isPathRegion(dropType) ||
         isTreeRegion(dropType))
      {
         List<ChartRef> list = getChartRefs(cinfo, dropType);
         level = GraphUtil.getNextDateLevelValue(dim, name, list, dropIndex);
      }

      return level;
   }

   private List<ChartRef> getChartRefs(ChartInfo cinfo, String dropType) {
      int type = Integer.parseInt(dropType);
      ChartRef[] refs = null;

      if(type == ChartConstants.DROP_REGION_X ||
         type == ChartConstants.DROP_REGION_X2)
      {
         refs = cinfo.getXFields();
      }
      else if(type == ChartConstants.DROP_REGION_Y ||
         type == ChartConstants.DROP_REGION_Y2)
      {
         refs = cinfo.getYFields();
      }
      else if(type == ChartConstants.DROP_REGION_GROUP) {
         refs = cinfo.getGroupFields();
      }
      else if(type == ChartConstants.DROP_REGION_PATH) {
         refs = new ChartRef[1];
         refs[0] = cinfo.getPathField();
      }
      else if(type == ChartConstants.DROP_REGION_SOURCE && cinfo instanceof RelationChartInfo) {
         refs = new ChartRef[1];
         refs[0] = ((RelationChartInfo) cinfo).getSourceField();
      }
      else if(type == ChartConstants.DROP_REGION_TARGET) {
         refs = new ChartRef[1];
         refs[0] = ((RelationChartInfo) cinfo).getTargetField();
      }

      return Arrays.asList(refs);
   }

   private final ChartRefModelFactoryService chartRefService;

   private static final Logger LOG = LoggerFactory.getLogger(ChartDndHandler.class);
}
