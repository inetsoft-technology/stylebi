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
package inetsoft.report.internal.graph;

import inetsoft.graph.aesthetic.*;
import inetsoft.report.composition.graph.*;
import inetsoft.report.composition.graph.calc.*;
import inetsoft.report.internal.binding.ExpertNamedGroupInfo;
import inetsoft.report.internal.binding.SimpleNamedGroupInfo;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.util.css.CSSParameter;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * Fix information when the chart is changed.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class ChangeChartProcessor {
   /**
    * Fix name group option type.
    */
   public static void fixGroupOption(VSChartInfo cinfo) {
      fixAestheticGroupOption(cinfo);
      ChartRef[] xrefs = cinfo.getXFields();
      ChartRef[] yrefs = cinfo.getYFields();

      for(ChartRef[] refs : new ChartRef[][] {xrefs, yrefs}) {
         for(Object ref : refs) {
            if(ref instanceof ChartAggregateRef) {
               fixAestheticGroupOption((ChartAggregateRef) ref);
            }
         }
      }

      for(int i = 0; i < xrefs.length; i++) {
         if(xrefs[i] instanceof VSDimensionRef) {
            ((VSDimensionRef) xrefs[i]).setGroupType("" + NamedRangeRef.DATA_GROUP);
         }
      }

      for(int i = 0; i < yrefs.length; i++) {
         if(yrefs[i] instanceof VSDimensionRef) {
            ((VSDimensionRef) yrefs[i]).setGroupType("" + NamedRangeRef.DATA_GROUP);
         }
      }

      ChartRef[] grefs = cinfo.getGroupFields();

      for(int i = 0; i < grefs.length; i++) {
         if(grefs[i] instanceof VSDimensionRef) {
            ((VSDimensionRef) grefs[i]).setGroupType("" + NamedRangeRef.DATA_GROUP);
         }
      }
   }

   private static void fixAestheticGroupOption(ChartBindable bindable) {
      AestheticRef color = bindable.getColorField();
      AestheticRef shape = bindable.getShapeField();
      AestheticRef size = bindable.getSizeField();
      AestheticRef text = bindable.getTextField();

      if(color != null && color.getDataRef() instanceof VSDimensionRef) {
         ((VSDimensionRef) color.getDataRef()).setGroupType("" + NamedRangeRef.COLOR_GROUP);
      }

      if(shape != null && shape.getDataRef() instanceof VSDimensionRef) {
         ((VSDimensionRef) shape.getDataRef()).setGroupType("" + NamedRangeRef.SHAPE_GROUP);
      }

      if(size != null && size.getDataRef() instanceof VSDimensionRef) {
         ((VSDimensionRef) size.getDataRef()).setGroupType("" + NamedRangeRef.SIZE_GROUP);
      }

      if(text != null && text.getDataRef() instanceof VSDimensionRef) {
         ((VSDimensionRef) text.getDataRef()).setGroupType("" + NamedRangeRef.TEXTURE_GROUP);
      }

      if(bindable instanceof RelationChartInfo) {
         RelationChartInfo info2 = (RelationChartInfo) bindable;
         color = info2.getNodeColorField();
         size = info2.getNodeSizeField();

         if(color != null && color.getDataRef() instanceof VSDimensionRef) {
            ((VSDimensionRef) color.getDataRef()).setGroupType("" + NamedRangeRef.COLOR_GROUP);
         }

         if(size != null && size.getDataRef() instanceof VSDimensionRef) {
            ((VSDimensionRef) size.getDataRef()).setGroupType("" + NamedRangeRef.SIZE_GROUP);
         }
      }
   }

   private static void fixRTAestheticGroupOption(ChartBindable bindable) {
      AestheticRef color = bindable.getColorField();
      AestheticRef shape = bindable.getShapeField();
      AestheticRef size = bindable.getSizeField();
      AestheticRef text = bindable.getTextField();

      if(color != null && color.getRTDataRef() instanceof VSDimensionRef) {
         ((VSDimensionRef) color.getRTDataRef()).setGroupType("" + NamedRangeRef.COLOR_GROUP);
      }

      if(shape != null && shape.getRTDataRef() instanceof VSDimensionRef) {
         ((VSDimensionRef) shape.getRTDataRef()).setGroupType("" + NamedRangeRef.SHAPE_GROUP);
      }

      if(size != null && size.getRTDataRef() instanceof VSDimensionRef) {
         ((VSDimensionRef) size.getRTDataRef()).setGroupType("" + NamedRangeRef.SIZE_GROUP);
      }
   }

   /**
    * Fix the shape frame for the shape field depending on the chart type.
    * @param info the chart info needed to be fixed.
    * @param type the chart type.
    */
   public void fixShapeField(ChartBindable bindable, ChartInfo info, int type) {
      AestheticRef sfield = bindable.getShapeField();

      if(sfield == null || sfield.getVisualFrame() == null) {
         return;
      }

      VisualFrame sframe = sfield.getVisualFrame();
      VisualFrame newFrame;
      boolean point = GraphTypes.supportsPoint(type, info);
      boolean line = GraphTypes.supportsLine(type, info);
      boolean texture = GraphTypes.supportsTexture(type);

      if(info instanceof GanttChartInfo &&
         bindable.equals(((GanttChartInfo) info).getMilestoneField()))
      {
         point = true;
         texture = false;
      }

      if(GraphUtil.isCategorical(sfield.getDataRef())) {
         if(line) {
            if(!(sframe instanceof CategoricalLineFrame)) {
               newFrame = new CategoricalLineFrame();
               sfield.setVisualFrame(newFrame);
            }
         }
         else if(point) {
            if(!(sframe instanceof CategoricalShapeFrame)) {
               newFrame = new CategoricalShapeFrame();
               sfield.setVisualFrame(newFrame);
            }
         }
         else if(texture) {
            if(!(sframe instanceof CategoricalTextureFrame)) {
               newFrame = new CategoricalTextureFrame();
               sfield.setVisualFrame(newFrame);
            }
         }
      }
      else {
         if(line) {
            if(!(sframe instanceof LinearLineFrame)) {
               newFrame = new LinearLineFrame();
               sfield.setVisualFrame(newFrame);
            }
         }
         else if(point) {
            if(!(sframe instanceof LinearShapeFrame)) {
               newFrame = new FillShapeFrame();
               sfield.setVisualFrame(newFrame);
            }
         }
         else if(texture) {
            if(!(sframe instanceof LinearTextureFrame)) {
               newFrame = new LeftTiltTextureFrame();
               sfield.setVisualFrame(newFrame);
            }
         }
      }
   }

   /**
    * Fix the color frame for the color field depending on the chart type.
    * @param info the chart info needed to be fixed.
    * @param type the chart type.
    */
   public void fixColorField(ChartBindable info, int type) {
      AestheticRef cfield = info.getColorField();

      if(cfield == null || cfield.getVisualFrame() == null) {
         return;
      }

      VisualFrame frame = cfield.getVisualFrame();
      VisualFrame newFrame = null;

      if((GraphUtil.isDimension(cfield.getDataRef()) ||
          XSchema.STRING.equals(cfield.getDataRef().getDataType())) &&
         !(frame instanceof CategoricalFrame))
      {
         newFrame = new CategoricalColorFrame();
      }
      else if(GraphUtil.isMeasure(cfield.getDataRef()) &&
         !(frame instanceof LinearColorFrame))
      {
         newFrame = new BluesColorFrame();
      }

      if(newFrame == null) {
         return;
      }

      cfield.setVisualFrame(newFrame);
   }

   /**
    * Fix the size frame for the size field depending on the chart type.
    * @param info the chart info needed to be fixed.
    * @param type the chart type.
    */
   public void fixSizeField(ChartBindable info, int type) {
      AestheticRef sfield = info.getSizeField();

      if(sfield == null || sfield.getVisualFrame() == null) {
         return;
      }

      // interval requires measure for size
      if(GraphTypes.isInterval(type) && GraphUtil.isDimension(sfield.getDataRef())) {
         info.setSizeField(null);
         return;
      }

      VisualFrame sframe = sfield.getVisualFrame();
      VisualFrame newFrame = null;

      if(!GraphTypes.isInterval(type) && sframe instanceof LinearSizeFrame) {
         LinearSizeFrame sizes = (LinearSizeFrame) sframe;

         // correct special setting used for interval chart
         if(sizes.getSmallest() == sizes.getLargest()) {
            sizes.setSmallest(1);
            sizes.setLargest(sizes.getMax());
         }
      }

      if((GraphUtil.isDimension(sfield.getDataRef()) ||
          XSchema.STRING.equals(sfield.getDataRef().getDataType())) &&
         !(sframe instanceof CategoricalSizeFrame))
      {
         newFrame = new CategoricalSizeFrame();
      }
      else if(GraphUtil.isMeasure(sfield.getDataRef()) &&
         !(sframe instanceof LinearSizeFrame))
      {
         newFrame = new LinearSizeFrame();
      }

      if(newFrame != null) {
         sfield.setVisualFrame(newFrame);
      }
   }

   /**
    * Fix size frame if the sizeframe is not changed.
    */
   public void fixSizeFrame(ChartInfo info) {
      AestheticRef sref = info.getSizeField();
      SizeFrame frame = info.getSizeFrame();
      boolean isDefault = true;

      if(sref != null && sref.getDataRef() != null) {
         frame = (SizeFrame) sref.getVisualFrame();
         isDefault = frame == null;
         frame = isDefault ? info.getSizeFrame() : frame;
      }

      // if size not changed, apply
      if(!info.isSizeChanged()) {
         int chartType = getRTChartType(info);
         ShapeFrame shape = info.getShapeFrame();
         fixSizeFrameValues(info, chartType, shape, frame, info);
      }

      if(isDefault) {
         info.setSizeFrame(frame);
      }

      for(ChartRef ref : info.getModelRefs(false)) {
         String name = ref.getFullName();

         if(!(ref instanceof ChartAggregateRef)) {
            continue;
         }

         ChartAggregateRef mref = (ChartAggregateRef) ref;
         SizeFrameWrapper sframe = mref.getSizeFrameWrapper();

         // fix default size
         if(sframe == null || info.isSizeChanged(name)) {
            continue;
         }

         SizeFrame frame0 = (SizeFrame) sframe.getVisualFrame();

         int chartType = getChartType(info, mref);
         ShapeFrame shape = info.getShapeFrame();
         fixSizeFrameValues(info, chartType, shape, frame0, mref);
      }

      fixAggregatesSizeFrame(info);
   }

   /**
    * Fix size frame of the aggregates of chart.
    */
   private void fixAggregatesSizeFrame(ChartInfo info) {
      ChartRef[] xrefs = info.getXFields();
      ChartRef[] yrefs = info.getYFields();

      for(int i = 0; i < xrefs.length; i++) {
         if(xrefs[i] instanceof ChartAggregateRef) {
            fixAggregateSizeFrame((ChartAggregateRef) xrefs[i], info);
         }
      }

      for(int i = 0; i < yrefs.length; i++) {
         if(yrefs[i] instanceof ChartAggregateRef) {
            fixAggregateSizeFrame((ChartAggregateRef) yrefs[i], info);
         }
      }

      if(info instanceof GanttChartInfo) {
         GanttChartInfo ginfo = (GanttChartInfo) info;
         ChartRef start = ginfo.getStartField();

         if(start instanceof ChartAggregateRef) {
            fixAggregateSizeFrame((ChartAggregateRef) start, info);
         }

         ChartRef milestone = ginfo.getMilestoneField();

         if(milestone instanceof ChartAggregateRef) {
            fixAggregateSizeFrame((ChartAggregateRef) milestone, info);
         }
      }
   }

   /**
    * Fix size frame of a aggregate of the chart.
    */
   private void fixAggregateSizeFrame(ChartAggregateRef agg, ChartInfo info) {
      if(agg == null) {
         return;
      }

      AestheticRef sref = agg.getSizeField();
      SizeFrame frame = agg.getSizeFrame();
      boolean isStatic = true;
      boolean isEmpty = sref == null || sref.getDataRef() == null ||
         Tool.isEmptyString(sref.getDataRef().getAttribute());

      if(sref != null && (!sref.isEmpty() || !isEmpty)) {
         frame = (SizeFrame) sref.getVisualFrame();
         isStatic = frame == null;
         frame = isStatic ? agg.getSizeFrame() : frame;
      }

      if(!agg.isSizeChanged()) {
         int chartType = getChartType(info, agg);
         ShapeFrame shape = agg.getShapeFrame();
         fixSizeFrameValues(info, chartType, shape, frame, agg);
      }
   }

   /**
    * Fix the size frame's values such as size, largest, smallest.
    */
   private void fixSizeFrameValues(ChartInfo info, int chartType,
      ShapeFrame shape, SizeFrame frame, ChartBindable bindable)
   {
      double nsize = 1;
      double min = 1;
      double max = 30;

      switch(chartType) {
      case GraphTypes.CHART_BAR:
      case GraphTypes.CHART_BAR_STACK:
      case GraphTypes.CHART_3D_BAR:
      case GraphTypes.CHART_3D_BAR_STACK:
      case GraphTypes.CHART_WATERFALL:
         // bar defaults to 1/2 of the max size
         nsize = 15;
         min = 10;
         max = 25;
         break;
      case GraphTypes.CHART_INTERVAL:
         nsize = 15;
         min = max = nsize;
         break;
      case GraphTypes.CHART_DONUT:
         nsize = 15;
         break;
      case GraphTypes.CHART_GANTT:
         if(bindable != null && GraphTypes.isPoint(bindable.getChartType())) {
            nsize = 9;
         }
         else {
            nsize = 20;
         }
         break;
      case GraphTypes.CHART_SUNBURST:
      case GraphTypes.CHART_TREEMAP:
      case GraphTypes.CHART_CIRCLE_PACKING:
      case GraphTypes.CHART_ICICLE:
         nsize = 30;
         break;
      case GraphTypes.CHART_PIE:
      case GraphTypes.CHART_3D_PIE:
         // pie defaults to the max size
         nsize = 30;
         break;
      case GraphTypes.CHART_PARETO:
         // bar defaults to 1/2 of the max size
         nsize = 15;
         min = 10;
         max = 25;
         break;
      case GraphTypes.CHART_LINE:
      case GraphTypes.CHART_LINE_STACK:
      case GraphTypes.CHART_STEP:
      case GraphTypes.CHART_STEP_STACK:
      case GraphTypes.CHART_JUMP:
         // line defaults to thin line
         nsize = 3;
         max = 15;
         break;
      case GraphTypes.CHART_POINT:
      case GraphTypes.CHART_POINT_STACK:
      case GraphTypes.CHART_MAP:
         if(shape instanceof LinearShapeFrame) {
            nsize = 3;
         }

         max = 15;
         break;
      case GraphTypes.CHART_BOXPLOT:
         nsize = 5;
         break;
      case GraphTypes.CHART_MEKKO:
         nsize = 30;
         break;
      case GraphTypes.CHART_FUNNEL:
         nsize = 30;
         break;
      case GraphTypes.CHART_SCATTER_CONTOUR:
      case GraphTypes.CHART_MAP_CONTOUR:
         nsize = 15;
         break;
      default:
         max = 15;
         break;
      }

      boolean fake = isFake(info);

      // force to point type
      if(fake) {
         nsize = 1;
         min = 1;
         max = 15;
      }

      if(frame instanceof StaticSizeFrame) {
         ((StaticSizeFrame) frame).setSize(nsize, CompositeValue.Type.DEFAULT);
      }
      else {
         frame.setSmallest(min);
         frame.setLargest(max);
      }
   }

   /**
    * Check if is fake chart.
    */
   public static boolean isFake(ChartInfo info) {
      if(info instanceof MergedChartInfo || GraphTypes.isTreemap(info.getChartType()) ||
         GraphTypes.isRelation(info.getChartType()) || GraphTypes.isContour(info.getChartType()))
      {
         return false;
      }

      ChartRef[] xrefs = info.getXFields();
      xrefs = xrefs == null ? new ChartRef[0] : xrefs;

      for(int i = 0;  i < xrefs.length; i++) {
         if(GraphUtil.isMeasure(xrefs[i])) {
            return false;
         }
      }

      ChartRef[] yrefs = info.getYFields();
      yrefs = yrefs == null ? new ChartRef[0] : yrefs;

      for(int i = 0; i < yrefs.length; i++) {
         if(GraphUtil.isMeasure(yrefs[i])) {
            return false;
         }
      }

      return true;
   }

   /**
    * Get the runtime chart type.
    */
   public int getRTChartType(ChartInfo cinfo) {
      if(!cinfo.isMultiStyles()) {
         return cinfo.getRTChartType();
      }

      cinfo.updateChartType(!cinfo.isMultiStyles(), cinfo.getXFields(),
                            cinfo.getYFields());

      ChartRef[] refs = cinfo.getYFields();
      ChartRef ref = GraphUtil.getLastField(refs);

      if(ref instanceof ChartAggregateRef) {
         return ((ChartAggregateRef) ref).getRTChartType();
      }

      refs = cinfo.getXFields();
      ref = GraphUtil.getLastField(refs);

      if(ref instanceof ChartAggregateRef) {
         return ((ChartAggregateRef) ref).getRTChartType();
      }

      return -1;
   }

   /**
    * If it's waterfall or pie in non-separate graph, only keep one measure in
    * the chart info.
    */
   public void checkMeasureRefs(ChartInfo info) {
      boolean found = false;
      boolean removed = false;

      for(int i = info.getYFieldCount() - 1; i > -1; i--) {
         if(info.getYField(i).isMeasure()) {
            if(i > 0 && info.getYField(i - 1).isMeasure()) {
               info.removeYField(i);
               removed = true;
            }

            found = true;
         }
      }

      for(int i = info.getXFieldCount() - 1; i > -1; i--) {
         if(info.getXField(i).isMeasure() &&
            (found || (i > 0 && info.getXField(i - 1).isMeasure())))
         {
            info.removeXField(i);
            removed = true;
         }
      }

      if(removed) {
         CoreTool.addUserMessage(Catalog.getCatalog().getString(
                                    "em.common.graph.measuresNotSupported"));
      }
   }

   /**
    * Fix chart x/y refs order.
    */
   public void fixXYRefsOrder(ChartInfo cinfo) {
      List xrefs = Arrays.asList(cinfo.getXFields());
      List yrefs = Arrays.asList(cinfo.getYFields());
      Collections.sort(xrefs, vcomparator);
      Collections.sort(yrefs, vcomparator);
      cinfo.removeXFields();
      cinfo.removeYFields();

      for(int i = 0; i < xrefs.size(); i++) {
         cinfo.addXField(i, (ChartRef) xrefs.get(i));
      }

      for(int i = 0; i < yrefs.size(); i++) {
         cinfo.addYField(i, (ChartRef) yrefs.get(i));
      }
   }

   /**
    * Get the available chart type to show a corresponding shape frame.
    */
   protected int getChartType(ChartInfo info, ChartAggregateRef aggr) {
      ChartRef[] xrefs = info.getXFields();
      ChartRef[] yrefs = info.getYFields();
      info.updateChartType(!info.isMultiStyles(), xrefs, yrefs);

      // separated?
      if(!info.isMultiStyles()) {
         return info.getRTChartType();
      }

      if(aggr != null) {
         return aggr.getRTChartType();
      }

      for(ChartRef[] refs : new ChartRef[][] {yrefs, xrefs}) {
         for(int i = 0; i < refs.length; i++) {
            if(refs[i] instanceof ChartAggregateRef) {
               aggr = (ChartAggregateRef) refs[i];
               return aggr.getRTChartType();
            }
         }
      }

      // handle no measure case
      ChartRef xref = xrefs.length == 0 ? null : xrefs[xrefs.length - 1];
      ChartRef yref = yrefs.length == 0 ? null : yrefs[yrefs.length - 1];
      boolean xdim = xref instanceof XDimensionRef;
      boolean ydim = yref instanceof XDimensionRef;

      if(xref == null) {
         return ydim ? GraphTypes.CHART_POINT : GraphTypes.CHART_BAR;
      }
      else if(yref == null) {
         return xdim ? GraphTypes.CHART_POINT : GraphTypes.CHART_BAR;
      }
      else if(xdim && ydim) {
         return GraphTypes.CHART_POINT;
      }

      return GraphTypes.CHART_AUTO;
   }

   /**
    * VComparator sorts dimensions and aggregates.
    */
   public static class VComparator implements Comparator {
      /**
       * Compare two data refs, which should be ChartRef only.
       */
      @Override
      public int compare(Object a, Object b) {
         int v1 = isDimension(a) ? 0 : 1;
         int v2 = isDimension(b) ? 0 : 1;
         return v1 - v2;
      }

      private boolean isDimension(Object obj) {
         if(obj instanceof ChartRef) {
            return !((ChartRef) obj).isMeasure();
         }

         return false;
      }
   }

   /**
    * Synchronized the topN in dimension.
    */
   public void syncTopN(ChartInfo info) {
      // if runtime fields are not available, don't sync
      if(info.getRTFields().length == 0) {
         return;
      }

      ArrayList<VSDataRef> allRefs = new ArrayList<>();
      allRefs.addAll(Arrays.asList(info.getFields()));

      if(info instanceof VSMapInfo) {
         allRefs.addAll(Arrays.asList(((VSMapInfo) info).getGeoFields()));
      }

      VSDataRef[] refs = new VSDataRef[allRefs.size()];
      allRefs.toArray(refs);
      VSDataRef[] rtrefs = info.getRTFields();

      for(int i = 0; i < refs.length; i++) {
         // dimension ref
         if(refs[i] instanceof VSDimensionRef) {
            VSDimensionRef dim = (VSDimensionRef) refs[i];
            String optval = dim.getRankingOptionValue();
            int opt = 0;
            String rankcol = dim.getRankingColValue();

            if(optval != null && !VSUtil.isDynamicValue(optval)) {
               try {
                  opt = Integer.parseInt(dim.getRankingOptionValue());
               }
               catch(Exception ex) {
                  // ignore
               }
            }

            if(opt != 0 && rankcol != null && !VSUtil.isDynamicValue(rankcol)) {
               VSDataRef aref = ((AbstractChartInfo) info).
                  getRTFieldByColName(rankcol);

               if(aref == null) {
                  aref = getFirstMeasure(rtrefs);
               }

               if(aref == null || !(aref instanceof XAggregateRef)) {
                  dim.setRankingOptionValue(XCondition.NONE + "");
                  dim.setRankingColValue(null);
               }
               else {
                  if(aref instanceof VSAggregateRef && ((VSAggregateRef) aref).isVariable()) {
                     dim.setRankingColValue(((VSAggregateRef) aref).getFullNameByDVariable());
                  }
                  else {
                     dim.setRankingColValue(((XAggregateRef) aref).getFullName(false));
                  }
               }
            }

            String ordercol = dim.getSortByColValue();

            if((dim.getOrder() == XConstants.SORT_VALUE_ASC ||
                dim.getOrder() == XConstants.SORT_VALUE_DESC) &&
               ordercol != null && !VSUtil.isDynamicValue(ordercol))
            {
               VSDataRef aref = ((AbstractChartInfo) info).
                  getRTFieldByColName(ordercol);

               if(aref == null) {
                  aref = getFirstMeasure(rtrefs);
               }

               if(aref == null || !(aref instanceof XAggregateRef)) {
                  dim.setOrder(XConstants.SORT_ASC);
                  dim.setSortByColValue(null);
               }
               else {
                  if(aref instanceof VSAggregateRef && ((VSAggregateRef) aref).isVariable()) {
                     dim.setSortByColValue(((VSAggregateRef) aref).getFullNameByDVariable());
                  }
                  else {
                     dim.setSortByColValue(((XAggregateRef) aref).getFullName(false));
                  }
               }
            }
         }
      }
   }

   /**
    * Return the first measure on the list.
    */
   private VSDataRef getFirstMeasure(VSDataRef[] refs) {
      for(VSDataRef ref : refs) {
         if(GraphUtil.isMeasure(ref)) {
            return ref;
         }
      }

      return null;
   }

   /**
    * Fixed the map shape frame.
    */
   private void fixMapShapeFrame(ChartInfo oinfo, ChartInfo ninfo) {
      if(ninfo.getShapeField() == null && ninfo.getSizeField() == null &&
         (oinfo.getShapeField() != null || oinfo.getSizeField() != null ||
         // if old info does not support nil and new info supports nil
         !GraphUtil.supportsNil(null, oinfo) && GraphUtil.supportsNil(null, ninfo) &&
         isFilledCircle(oinfo)))
      {
         StaticShapeFrame frame = (StaticShapeFrame) ninfo.getShapeFrame();

         if(GraphUtil.supportsNil(null, ninfo)) {
            frame.setShape(GShape.NIL);
         }
         else {
            ShapeFrame oframe = oinfo.getShapeFrame();

            if(!(oframe instanceof StaticShapeFrame) ||
               ((StaticShapeFrame) oframe).getShape() == GShape.NIL)
            {
               frame.setShape(GShape.FILLED_CIRCLE);
            }
         }
      }
      else if(GraphTypeUtil.isHeatMap(ninfo)) {
         // don't change shape from nil. (59304)
      }
      // if old info is nil and new info doesn't support nil and is static or
      // bind size field or change static size
      else if(GraphUtil.containsNil(oinfo) && !GraphUtil.supportsNil(null, ninfo) &&
         ninfo.getShapeFrame() instanceof StaticShapeFrame &&
         ninfo.getShapeField() == null ||
         (ninfo.isSizeChanged() || ninfo.getSizeField() != null) &&
         GraphUtil.isNil(ninfo) && !GraphUtil.isNil(oinfo) && !GraphUtil.preferWordCloud(ninfo))
      {
         StaticShapeFrame frame = (StaticShapeFrame) ninfo.getShapeFrame();
         frame.setShape(GShape.FILLED_CIRCLE);
      }
   }

   /**
    * Check if the shape frame is filled circle.
    */
   private static boolean isFilledCircle(ChartInfo info) {
      if(info.getShapeField() != null) {
         return false;
      }

      ShapeFrame shape = info.getShapeFrame();

      return shape instanceof StaticShapeFrame &&
         ((StaticShapeFrame) shape).getShape() == GShape.FILLED_CIRCLE;
   }

   /**
    * Fix the default sorting (for pareto).
    */
   public void fixParetoSorting(ChartInfo info) {
      if(getRTChartType(info) != GraphTypes.CHART_PARETO) {
         return;
      }

      ChartRef[] xfields = info.getXFields();
      ChartRef[] yfields = info.getYFields();
      ChartRef lastDim = getLastRef(xfields, yfields, XDimensionRef.class);
      ChartRef lastAgg = getLastRef(yfields, xfields, XAggregateRef.class);

      if(lastDim == null || lastAgg == null) {
         return;
      }

      // this should match the ChartInfoModelBuilder.getAggregateRefs()
      String lastAggName = ((XAggregateRef) lastAgg).getFullName(false);

      if(lastDim instanceof VSDimensionRef) {
         ((VSDimensionRef) lastDim).setSortByColValue(lastAggName);
      }

      ((XDimensionRef) lastDim).setSortByCol(lastAggName);
      int order = XConstants.SORT_VALUE_DESC;

      // exist namedgroup
      if((((XDimensionRef) lastDim).getOrder() & XConstants.SORT_SPECIFIC) != 0 &&
         ((XDimensionRef) lastDim).getNamedGroupInfo() != null)
      {
         order = ((XDimensionRef) lastDim).getOrder();
      }

      ((XDimensionRef) lastDim).setOrder(order);
   }

   /**
    * Find the last ref of the specified type on the list.
    */
   private ChartRef getLastRef(ChartRef[] f1, ChartRef[] f2, Class type) {
      ChartRef[][] arrs = {f1, f2};

      for(ChartRef[] arr : arrs) {
         for(int i = arr.length - 1; i >= 0; i--) {
            if(type.isAssignableFrom(arr[i].getClass())) {
               return arr[i];
            }
         }
      }

      return null;
   }

   /**
    * Fix map static color frame.
    */
   private void fixMapColorFrame(ChartInfo oinfo, ChartInfo ninfo) {
      ColorFrame color = ninfo.getColorFrame();
      StaticColorFrame scolor = color instanceof StaticColorFrame
         ? (StaticColorFrame) color : null;
      Color ccolor = scolor == null ? null : scolor.getColor();
      Color mapColor = GraphUtil.getMapDefaultColor();
      Color chartColor = CategoricalColorFrame.COLOR_PALETTE[0];

      // if only polygon and color frame is static, fix color to white gray
      if(((ninfo).equals(oinfo) || !containsOnlyPolygon(oinfo)) &&
         containsOnlyPolygon(ninfo) && scolor != null &&
         (ccolor.equals(chartColor) ||
            SreeEnv.getProperty("map.default.color") != null))
      {
         GeoRef geo = GraphUtil.getPolygonField((MapInfo) ninfo);
         scolor.setDefaultColor(mapColor);
      }
      // if not only polygon and color is polygon default color,
      // fix default color
      else if(!containsOnlyPolygon(ninfo) && scolor != null &&
         ccolor.equals(mapColor))
      {
         scolor.setDefaultColor(chartColor);
      }
   }

   /**
    * Check if the specified info contains polygon but does not contain point.
    */
   private boolean containsOnlyPolygon(ChartInfo info) {
      return GraphUtil.containsMapPolygon(info) &&
         !GraphUtil.containsMapPoint(info);
   }

   /**
    * Fix map aesthetic frame.
    */
   public void fixMapFrame(ChartInfo oinfo, ChartInfo ninfo) {
      fixMapShapeFrame(oinfo, ninfo);
      fixMapColorFrame(oinfo, ninfo);
      //fixMapSizeField(ninfo);
   }

   /**
    * Fix map named group if the geo column contains multi mapping.
    */
   public static void fixMapNamedGroup(MapInfo minfo, VSDataRef[] refs) {
      GeoRef[] grefs = getMultiMappingGeoRefs(minfo);

      if(grefs == null || grefs.length == 0) {
         return;
      }

      // merge named group
      for(GeoRef gref : grefs) {
         if(!(gref instanceof XDimensionRef)) {
            continue;
         }

         DataRef base = gref.getDataRef();
         Map<String, List<String>> map = gref.getGeographicOption().getMapping().getDupMapping();

         for(VSDataRef ref : refs) {
            if(!(ref instanceof XDimensionRef)) {
               continue;
            }

            DataRef base2 = ((XDimensionRef) ref).getDataRef();

            if(gref.getName().equals(ref.getName()) || base != null && base.equals(base2)) {
               XDimensionRef gref0 = (XDimensionRef) ref;
               XNamedGroupInfo ninfo = gref0.getNamedGroupInfo();
               SimpleNamedGroupInfo ginfo = new SimpleNamedGroupInfo();
               Iterator<String> keys = map.keySet().iterator();

               while(keys.hasNext()) {
                  String name = keys.next();
                  ginfo.setGroupValue(name, map.get(name));
               }

               if(ninfo == null || ninfo.isEmpty()) {
                  gref0.setNamedGroupInfo(ginfo);
                  ((VSDimensionRef) gref0).setGroupType("" + NamedRangeRef.DATA_GROUP);

                  continue;
               }

               keys = map.keySet().iterator();

               if(ninfo instanceof SNamedGroupInfo) {
                  SNamedGroupInfo sinfo = (SNamedGroupInfo) ninfo;

                  while(keys.hasNext()) {
                     String name = keys.next();
                     String[] groups = sinfo.getGroups();
                     boolean add = true;

                     for(String gname:groups) {
                        List gvalues = sinfo.getGroupValue(gname);

                        // name group contains the multi mapping,
                        // replace the values
                        if(gvalues.contains(name)) {
                           gvalues.remove(name);
                           gvalues.addAll(map.get(name));
                           add = false;
                           break;
                        }

                        List<String> mvalues = map.get(name);

                        if(gvalues.containsAll(mvalues)) {
                           add = false;
                           break;
                        }

                        for(String mvalue:mvalues) {
                           // name group value and the multi mapping value duplicate,
                           // remove the name group
                           if(gvalues.contains(mvalue)) {
                              sinfo.removeGroup(gname);
                              sinfo.setGroupValue(name, map.get(name));
                              add = false;
                              break;
                           }
                        }

                        if(add) {
                           sinfo.setGroupValue(name, map.get(name));
                        }
                     }
                  }
               }
               else if(ninfo instanceof ExpertNamedGroupInfo) {
                  ExpertNamedGroupInfo einfo = (ExpertNamedGroupInfo) ninfo;

                  while(keys.hasNext()) {
                     String name = keys.next();
                     ConditionList clist = ginfo.getGroupCondition(name);
                     einfo.setGroupCondition(name, clist);
                  }
               }
            }
         }
      }

      fixRTAestheticGroupOption(minfo);
   }

   /**
    * Get GeoRef contains multi mapping.
    */
   private static GeoRef[] getMultiMappingGeoRefs(MapInfo minfo) {
      List<GeoRef> list = new ArrayList<>();

      if(minfo != null) {
         ChartRef[] geoRefs = minfo.getGeoFields();

         for(int i = 0; i < geoRefs.length; i++) {
            GeoRef ref = (GeoRef) geoRefs[i];
            Map<String, List<String>> map = ref.getGeographicOption().getMapping().getDupMapping();

            if(map != null && map.size() > 0) {
               list.add(ref);
            }
         }

         GeoRef[] grefs = new GeoRef[list.size()];
         return list.toArray(grefs);
      }

      ColumnSelection columns = ((VSMapInfo) minfo).getGeoColumns();

      if(columns == null || columns.getAttributeCount() == 0) {
         return new GeoRef[0];
      }

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef ref = columns.getAttribute(i);

         if(ref instanceof GeoRef) {
            Map map = ((GeoRef) ref).getGeographicOption().getMapping().getDupMapping();

            if(map != null && map.size() > 0) {
               list.add((GeoRef) ref);
            }
         }
      }

      GeoRef[] grefs = new GeoRef[list.size()];
      return list.toArray(grefs);
   }

   /**
    * Fix the named group for Aesthetic.
    */
   public void fixAestheticNamedGroup(ChartInfo cinfo) {
      syncNamedGroup(cinfo, false);
   }

   /**
    * sync named group
    */
   public void syncNamedGroup(ChartInfo cinfo, boolean isLegend) {
      VSDataRef[] arefs = getAestheticRefs(cinfo);
      VSDataRef[] brefs = getAxisRefs(cinfo);

      if(arefs.length == 0 || brefs.length == 0) {
         return;
      }

      for(VSDataRef aref: arefs) {
         for(VSDataRef bref: brefs) {
            boolean sameCol = Tool.equals(aref.getName(), bref.getName());

            if(sameCol && !nameGroupEquals(aref, bref) && !isLegend) {
               copyNamedGroup((XDimensionRef) bref, (XDimensionRef) aref);
               fixAestheticGroupOption(cinfo);
            }
            else if(sameCol && !nameGroupEquals(aref, bref) && isLegend) {
               copyNamedGroup((XDimensionRef) aref, (XDimensionRef) bref);
            }
         }
      }
   }

   // if the shareColors option changed, copy the color map to/from aesthetic ref to
   // viewsheet. this make sures changing the sharedColors option don't change the
   // rendering of the chart
   public void copyDimensionColors(ChartInfo oinfo, ChartInfo ninfo, Viewsheet vs) {
      AestheticRef[] nrefs = ninfo.getAestheticRefs(false);
      AestheticRef[] orefs = oinfo.getAestheticRefs(false);

      for(AestheticRef nref : nrefs) {
         for(AestheticRef oref : orefs) {
            if(Objects.equals(nref.getDataRef(), oref.getDataRef())) {
               VisualFrameWrapper nwrapper = nref.getVisualFrameWrapper();
               VisualFrameWrapper owrapper = oref.getVisualFrameWrapper();

               if(nwrapper instanceof CategoricalColorFrameWrapper &&
               owrapper instanceof CategoricalColorFrameWrapper)
               {
                  CategoricalColorFrameWrapper ncolor = (CategoricalColorFrameWrapper) nwrapper;
                  CategoricalColorFrameWrapper ocolor = (CategoricalColorFrameWrapper) owrapper;

                  if(ncolor.isShareColors() != ocolor.isShareColors()) {
                     Map<String, Color> globalColors = vs.getDimensionColors(nref.getName());

                     if(ncolor.isShareColors()) {
                        globalColors = new HashMap<>(globalColors);
                        globalColors.putAll(ocolor.getDimensionColors());
                        vs.setDimensionColors(nref.getName(), globalColors);
                     }
                     else {
                        ncolor.setDimensionColors(globalColors);
                     }
                  }
               }
            }
         }
      }
   }

   /**
    * Get the binding refs for the chart.
    */
   private VSDataRef[] getAxisRefs(ChartInfo cinfo) {
      List<VSDataRef> set = new ArrayList();
      VSDataRef[] refs = getBindingRefs(cinfo, false);

      for(int i = 0; i < refs.length; i++) {
         if(refs[i] instanceof XDimensionRef) {
            set.add(refs[i]);
         }
      }

      VSDataRef[] arr = new VSDataRef[set.size()];
      return set.toArray(arr);
   }

   /**
    * Get the Aesthetic refs for the chart.
    */
   private VSDataRef[] getAestheticRefs(ChartInfo cinfo) {
      List<VSDataRef> set = new ArrayList<>();
      AestheticRef[] aes = cinfo.getAestheticRefs(false);

      for(AestheticRef aref : aes) {
         if(aref.getDataRef() instanceof XDimensionRef) {
            set.add((VSDataRef) aref.getDataRef());
         }
      }

      return set.toArray(new VSDataRef[0]);
   }

   /**
    * The same dimension share the named group property.
    */
   public void fixNamedGroup(ChartInfo oinfo, ChartInfo ninfo) {
      if(oinfo == null || ninfo == null) {
         return;
      }

      VSDataRef[] dims = getBindingRefs(ninfo, false, true);
      VSDataRef[] odims = getBindingRefs(oinfo, false, true);
      fixNamedGroup(dims, odims, ninfo);

      dims = getAestheticRefs(ninfo);
      odims = getAestheticRefs(oinfo);
      fixNamedGroup(dims, odims, ninfo);
   }

   private void fixNamedGroup(VSDataRef[] dims, VSDataRef[] odims, ChartInfo ninfo) {
      if(dims.length != odims.length) {
         return;
      }

      XDimensionRef changed = null;

      for(int i = 0; i < dims.length; i++) {
         // fix bug1348020003492
         boolean sameCol = Tool.equals(dims[i].getName(), odims[i].getName());

         if(sameCol && !nameGroupEquals(dims[i], odims[i])) {
            changed = (XDimensionRef) dims[i];
            break;
         }
      }

      if(changed != null) {
         for(int i = 0; i < dims.length; i++) {
            if(changed == dims[i]) {
               continue;
            }

            boolean sameCol = Tool.equals(changed.getName(), dims[i].getName());

            if(changed != dims[i] && sameCol) {
               copyNamedGroup(changed, (XDimensionRef) dims[i]);
            }
         }
      }

      fixGroupOption((VSChartInfo) ninfo);
   }

   /**
    * Check the two XDimensionRef have the same content or not.
    */
   private boolean nameGroupEquals(VSDataRef ref1, VSDataRef ref2) {
      if(ref1 instanceof XDimensionRef) {
         return Tool.equals(((XDimensionRef) ref1).getNamedGroupInfo(),
            ((XDimensionRef) ref2).getNamedGroupInfo());
      }

      return Tool.equals(ref1, ref2);
   }

   /**
    * Fix aggregate ref pecentage property.
    */
   public void fixAggregateRefs(ChartInfo info) {
      fixAggregateRefs(info, null);
   }

   public void fixAggregateRefs(ChartInfo info, ChartInfo oinfo) {
      Set xyset = new HashSet();
      Set all = new HashSet();
      ChartRef[] xbindings = info.getRTXFields();

      for(int i = 0; i < xbindings.length; i++) {
         if(xbindings[i] instanceof XDimensionRef) {
            all.add(xbindings[i].getFullName());
            xyset.add(xbindings[i].getFullName());
         }
      }

      ChartRef[] ybindings = info.getRTYFields();

      for(int i = 0; i < ybindings.length; i++) {
         if(ybindings[i] instanceof XDimensionRef) {
            all.add(ybindings[i].getFullName());
            xyset.add(ybindings[i].getFullName());
         }
      }

      if(info.supportsGroupFields()) {
         ChartRef[] groupby = info.getRTGroupFields();

         for(int i = 0; i < groupby.length; i++) {
            all.add(groupby[i].getFullName());
         }
      }

      AestheticRef color = info.getColorField();
      AestheticRef shape = info.getShapeField();
      AestheticRef size = info.getSizeField();
      AestheticRef text = info.getTextField();

      if(color != null && color.getDataRef() instanceof XDimensionRef) {
         all.add(color.getFullName());
      }

      if(shape != null && shape.getDataRef() instanceof XDimensionRef) {
         all.add(shape.getFullName());
      }

      if(size != null && size.getDataRef() instanceof XDimensionRef) {
         all.add(size.getFullName());
      }

      if(text != null && text.getDataRef() instanceof XDimensionRef) {
         all.add(text.getFullName());
      }

      xbindings = info.getXFields();
      ybindings = info.getYFields();

      for(int i = 0; i < xbindings.length; i++) {
         if(xbindings[i] instanceof ChartAggregateRef) {
            modifiedCalculator((XAggregateRef) xbindings[i], info, oinfo, true);
            fixAggregateRef((ChartAggregateRef) xbindings[i], info);
         }
      }

      for(int i = 0; i < ybindings.length; i++) {
         if(ybindings[i] instanceof ChartAggregateRef) {
            modifiedCalculator((XAggregateRef) ybindings[i], info, oinfo, false);
            fixAggregateRef((ChartAggregateRef) ybindings[i], info);
         }
      }

      modifiedAestheticPercentage(color, info);
      modifiedAestheticPercentage(shape, info);
      modifiedAestheticPercentage(size, info);
      modifiedAestheticPercentage(text, info);
   }

   private void fixAggregateRef(ChartAggregateRef aggr, ChartInfo info) {
      // discrete measure not supposed by boxplot.
      if(GraphTypes.isBoxplot(info.getRTChartType())) {
         aggr.setDiscrete(false);
      }
   }

   /**
    * Fix dimension ref name group property.
    */
   protected void fixMapDimensionRefs(ChartInfo info) {
      if(!(info instanceof MapInfo)) {
         return;
      }

      // map doesn't support named group for geo columns (or the region will not be found).
      clearNamedGroup(((MapInfo) info).getRTGeoFields());
      clearNamedGroup(((MapInfo) info).getGeoFields());
   }

   private static void clearNamedGroup(ChartRef[] geos) {
      for(int i = 0; i < geos.length; i++) {
         clearNamedGroup(geos[i]);
      }
   }

   private static void clearNamedGroup(ChartRef ref) {
      if(ref instanceof XDimensionRef) {
         switch(((XDimensionRef) ref).getOrder()) {
         case XConstants.SORT_ASC:
         case XConstants.SORT_DESC:
         case XConstants.SORT_NONE:
         case XConstants.SORT_ORIGINAL:
            break;
         default:
            ((XDimensionRef) ref).setOrder(XConstants.SORT_ASC);
         }

         ((XDimensionRef) ref).setNamedGroupInfo(null);
         ((XDimensionRef) ref).setManualOrderList(null);
      }
   }

   /**
    * Copy the named group settings from source ref to target ref.
    */
   private void copyNamedGroup(XDimensionRef src, XDimensionRef target) {
      if(src.getRealNamedGroupInfo() != null) {
         target.setOrder(src.getOrder());
      }

      target.setNamedGroupInfo(src.getRealNamedGroupInfo() == null ?
          null : (XNamedGroupInfo) src.getRealNamedGroupInfo().clone());
   }

   /**
    * Modified the percentage option from of-group to of-grand for AestheticRef.
    */
   private void modifiedAestheticPercentage(AestheticRef aref, ChartInfo info) {
      XAggregateRef ref = null;

      if(aref != null && aref.getDataRef() instanceof XAggregateRef) {
         ref = (XAggregateRef) aref.getDataRef();
      }

      modifiedPercentage(ref, info);
   }

   /**
    * Modified the percentage option from of-group to of-grand for AggregateRef.
    */
   private void modifiedPercentage(XAggregateRef ref, ChartInfo info) {
      // not modified percent level, only one group will allowed SUB_TOTAL
      if(true || ref == null) {
         return;
      }

      Calculator calc = ref.getCalculator();

      if(calc != null && calc.getType() == Calculator.PERCENT) {
         PercentCalc pcalc = (PercentCalc) calc;

         if(pcalc.getLevel() == PercentCalc.SUB_TOTAL && noGrpp(info)) {
            pcalc.setLevel(PercentCalc.GRAND_TOTAL);
         }

         if(!findDimRef(pcalc.getColumnName(), info)) {
            pcalc.setColumnName(null);
         }
      }
   }

   /**
    * Check whether current aggregate ref has group.
    */
   private boolean noGrpp(ChartInfo info) {
      Set columns = getGroupColumns(info, false);
      boolean nogrpp = columns.size() <= 1;

      if(!nogrpp) {
         if(!GraphTypes.isPolar(info.getChartStyle())) {
            if((info.getXFields().length == 0 ||
               info.getYFields().length == 0) &&
               columns.size() == getGroupColumns(info, true).size())
            {
               nogrpp = true;
            }
         }
         else {
            nogrpp = true;
            columns = getGroupColumns(info, true);

            VSDataRef[] refs = ((VSChartInfo) info).getRTAestheticFields();

            for(int i = 0; i < refs.length; i++) {
               if(refs[i] instanceof XDimensionRef &&
                  !columns.contains(refs[i].getFullName()))
               {
                  nogrpp = false;
                  break;
               }
            }

            if(info.supportsGroupFields()) {
               ChartRef[] groupby = info.getGroupFields();

               for(int i = 0; i < groupby.length; i++) {
                  if(groupby[i] instanceof XDimensionRef &&
                     !columns.contains(groupby[i].getFullName()))
                  {
                     nogrpp = false;
                     break;
                  }
               }
            }
         }
      }

      return nogrpp;
   }

   /**
    * Get the group columns for aggregate column.
    */
   private Set getGroupColumns(ChartInfo info, boolean onlyxy) {
      Set set = new HashSet();
      ChartRef[] xbindings = info.getXFields();

      for(int i = 0; i < xbindings.length; i++) {
         if(xbindings[i] instanceof XDimensionRef) {
            set.add(xbindings[i].getFullName());
         }
      }

      ChartRef[] ybindings = info.getYFields();

      for(int i = 0; i < ybindings.length; i++) {
         if(ybindings[i] instanceof XDimensionRef) {
            set.add(ybindings[i].getFullName());
         }
      }

      if(!onlyxy) {
         if(info.supportsGroupFields()) {
            ChartRef[] groupby = info.getGroupFields();

            for(int i = 0; i < groupby.length; i++) {
               set.add(groupby[i].getFullName());
            }
         }

         VSDataRef[] refs = ((VSChartInfo) info).getRTAestheticFields();

         for(int i = 0; i < refs.length; i++) {
            if(refs[i] instanceof XDimensionRef) {
               set.add(refs[i].getFullName());
            }
         }
      }

      return set;
   }

   /**
    * Modified the calculator contained in aggregate refs.
    * @param isX indicates whether the given ref is xref.
    */
   private void modifiedCalculator(XAggregateRef ref, ChartInfo info, ChartInfo oinfo, boolean isX) {
      if(ref == null) {
         return;
      }

      Calculator calc =  ref.getCalculator();

      if(calc == null) {
         return;
      }

      int calcType = calc.getType();

      switch(calcType) {
         case Calculator.PERCENT :
            modifiedPercentage(ref, info);
            break;
         case Calculator.RUNNINGTOTAL :
         case Calculator.COMPOUNDGROWTH :
            modifiedRunningTotal((RunningTotalCalc) calc, info, isX);
            break;
         case Calculator.CHANGE :
         case Calculator.VALUE :
            modifiedColumnName((ValueOfCalc) calc, info, oinfo);
            break;
      }
   }

   /**
    * Modified the column name in change calculator.
    */
   private void modifiedColumnName(ValueOfCalc ccalc, ChartInfo info,
                                   ChartInfo oinfo)
   {
      if(oinfo == null) {
         modifiedColumnName(ccalc, info);
         return;
      }

      String columnName = ccalc.getColumnName();
      VSDataRef[] odimensions = getChartDimensionRef(oinfo, false);
      VSDataRef[] dimensions = getChartDimensionRef(info, false);

      for(int i = 0; i < odimensions.length; i++) {
         String dimName = odimensions[i].toView();
         String fullName = odimensions[i].getFullName();

         if(dimName.equals(columnName) || fullName.equals(columnName)) {
            ccalc.setColumnName(dimensions[i].getFullName());
            return;
         }
      }
   }

   /**
    * Modified the column name in change calculator.
    */
   private void modifiedColumnName(ValueOfCalc ccalc, ChartInfo info) {
      String columnName = ccalc.getColumnName();

      if(!Tool.equals(AbstractCalc.ROW_INNER, columnName) &&
         !Tool.equals(AbstractCalc.COLUMN_INNER, columnName) &&
         !findDimRef(columnName, info))
      {
         ccalc.setColumnName(null);
      }
   }

   private boolean findDimRef(String columnName, ChartInfo info) {
      VSDataRef[] dimensions = getChartDimensionRef(info, false);
      boolean find = false;

      if(dimensions != null) {
         for(int i = 0; i < dimensions.length; i++) {
            String dimName = dimensions[i].toView();
            String fullName = dimensions[i].getFullName();
            boolean visible = !(dimensions[i] instanceof VSChartDimensionRef) ||
               ((VSChartDimensionRef) dimensions[i]).isDrillVisible();

            if(visible && (dimName.equals(columnName) || fullName.equals(columnName))) {
               find = true;
               return find;
            }
         }
      }

      return find;
   }

   /**
    * Get dimension refs of current chart.
    */
   private VSDataRef[] getChartDimensionRef(ChartInfo info, boolean runtime) {
      if(info == null) {
         return new VSDataRef[0];
      }

      List<VSDataRef> set = new ArrayList<>();
      VSDataRef[] refs = getBindingRefs(info, runtime);

      for(VSDataRef ref : refs) {
         if(ref instanceof XDimensionRef) {
            set.add(ref);
         }
      }

      AestheticRef[] aes = info.getAestheticRefs(runtime);

      for(AestheticRef aref : aes) {
         if(aref.getDataRef() instanceof XDimensionRef) {
            set.add((VSDataRef) aref.getDataRef());
         }
      }

      return set.toArray(new VSDataRef[0]);
   }

   /**
    * Get chart binding refs.
    */
   private ChartRef[] getBindingRefs(ChartInfo info, boolean runtime) {
      return getBindingRefs(info, runtime, false);
   }

   /**
    * Get chart binding refs.
    * @param runtime <code>true</code>get runtime refs, <code>false</code> get the designed refs.
    * @param justDim <code>true</code> just get dimension refs, <code>false</code> get all refs.
    */
   private ChartRef[] getBindingRefs(ChartInfo info, boolean runtime, boolean justDim) {
      ChartRef[] xbindings = !runtime ? info.getXFields() : info.getRTXFields();
      ChartRef[] ybindings = !runtime ? info.getYFields() : info.getRTYFields();
      ChartRef[][] arrs = null;

      if(info.supportsGroupFields()) {
         ChartRef[] groupby = !runtime ? info.getGroupFields() : info.getRTGroupFields();
         arrs = new ChartRef[][] {xbindings, ybindings, groupby};
      }
      else {
         arrs = new ChartRef[][] {xbindings, ybindings};
      }

      List<ChartRef> refs = new ArrayList<>();

      for(ChartRef[] arr : arrs) {
         if(arr == null) {
            continue;
         }

         if(justDim) {
            Arrays.stream(arr)
               .filter(ref -> ref instanceof XDimensionRef)
               .forEach(ref -> refs.add(ref));
         }
         else {
            Collections.addAll(refs, arr);
         }
      }

      ChartRef path = info.getPathField();

      if(justDim && path != null && path instanceof XDimensionRef || !justDim && path != null) {
         refs.add(path);
      }

      return refs.toArray(new ChartRef[0]);
   }

   /**
    * Modified the resel level option to min avaliable level.
    */
   private void modifiedRunningTotal(RunningTotalCalc rcalc, ChartInfo info, boolean isX) {
      String columnName = rcalc.getColumnName();

      if(!Tool.equals(AbstractCalc.ROW_INNER, columnName) &&
         !Tool.equals(AbstractCalc.COLUMN_INNER, columnName) &&
         !findDimRef(rcalc.getBreakBy(), info))
      {
         rcalc.setBreakBy(null);
      }

      ChartRef[] refs = isX ? info.getYFields() : info.getXFields();
      int dimensionDateLevel = getDimensionInnerDateLevel(refs);

      if(dimensionDateLevel == RunningTotalColumn.NONE) {
         rcalc.setResetLevel(dimensionDateLevel);
      }
      else {
         int dimInnerLevelPri =
            RunningTotalCalc.getDatePriority(dimensionDateLevel);
         int currRefPri=
            RunningTotalCalc.getDatePriority(rcalc.getResetLevel());

         if(dimInnerLevelPri > currRefPri){
            rcalc.setResetLevel(getMinAvailableDate(dimInnerLevelPri));
         }
      }
   }

   /**
    * Get the min date level which has bigger priority than the give level or
    * equal to it.
    */
   private int getMinAvailableDate(int priority) {
      int[] dateTypes = RunningTotalCalc.ALL_RESETAT_TYPES;

      for(int i = dateTypes.length - 1; i >= 0; i--) {
         if(RunningTotalCalc.getDatePriority(dateTypes[i]) >= priority) {
            return dateTypes[i];
         }
      }

      return RunningTotalColumn.NONE;
   }

   /**
    * Get the min date level from inner dimension.
    */
   private int getDimensionInnerDateLevel(ChartRef[] refs) {
      if(refs == null || refs.length == 0) {
         return RunningTotalColumn.NONE;
      }

      for(int i = refs.length - 1; i >= 0; i--) {
         if(refs[i] instanceof XDimensionRef) {
            XDimensionRef dref = (XDimensionRef) refs[i];
            String type = dref.getDataType();
            boolean dateType = XSchema.isDateType(type);

            if(dateType) {
               int level = dref.getDateLevel();
               level = DateRangeRef.isDateTime(level) ?
                  level : DateRangeRef.NONE_DATE_GROUP;
               return transferLevel(level);
            }
         }
      }

      return RunningTotalColumn.NONE;
   }

   /**
    * Transform date's flag in dateRangeRef to flag in RunningTotalColumn.
    */
   private int transferLevel(int dateRangeLevel) {
      switch(dateRangeLevel) {
      case DateRangeRef.YEAR_INTERVAL :
         return RunningTotalColumn.YEAR;
      case DateRangeRef.QUARTER_INTERVAL:
         return RunningTotalColumn.QUARTER;
      case DateRangeRef.MONTH_INTERVAL :
         return RunningTotalColumn.MONTH;
      case DateRangeRef.WEEK_INTERVAL :
         return RunningTotalColumn.MONTH;
      case DateRangeRef.DAY_INTERVAL :
         return RunningTotalColumn.DAY;
      case DateRangeRef.HOUR_INTERVAL :
         return RunningTotalColumn.HOUR;
      case DateRangeRef.MINUTE_INTERVAL :
         return RunningTotalColumn.MINUTE;
      case DateRangeRef.SECOND_INTERVAL :
         return RunningTotalColumn.MINUTE;
      default :
         return RunningTotalColumn.NONE;
      }
   }

   /**
    * Fix map size frame.
    */
   private void fixMapSizeField(ChartInfo info) {
      AestheticRef sfield = info.getSizeField();

      if(sfield != null && !GraphUtil.containsMapPoint(info)) {
         info.setSizeField(null);
      }
   }

   /**
    * If the formula in measure is changed, sync the target field name.
    */
   public static void fixTarget(ChartInfo oinfo, ChartInfo ninfo, ChartDescriptor desc) {
      for(int i = 0; i < desc.getTargetCount(); i++) {
         GraphTarget target = desc.getTarget(i);
         String field = target.getField();
         DataRef ref = ninfo.getFieldByName(field, false);

         // the measure is still there
         if(ref != null) {
            continue;
         }

         // measure changed
         ref = oinfo.getFieldByName(field, false);

         // try to find the measure in ninfo
         if(ref instanceof XAggregateRef) {
            DataRef ref0 = ((XAggregateRef) ref).getDataRef();

            if(ref0 == null) {
               ref0 = new AttributeRef(ref.getName());
            }

            XAggregateRef[] aggrs = ninfo.getAggregates(ref0, false);

            if(aggrs != null && aggrs.length > 0) {
               target.setField(aggrs[0].getFullName());

               if(Tool.equals(field, target.getFieldLabel())) {
                  target.setFieldLabel(aggrs[0].getFullName());
               }
            }
         }
         else if(ref instanceof XDimensionRef) {
            DataRef ref0 = ((XDimensionRef) ref).getDataRef();

            if(ref0 == null) {
               ref0 = new AttributeRef(ref.getName());
            }

            XDimensionRef[] dimensions = ((AbstractChartInfo) ninfo).getAllDimensions(ref0, false);

            if(dimensions != null && dimensions.length > 0) {
               XDimensionRef dimension = dimensions[0];

               if(dimension.isDateTime() && dimension.isTimeSeries()) {
                  target.setField(dimensions[0].getFullName());

                  if(Tool.equals(field, target.getFieldLabel())) {
                     target.setFieldLabel(dimensions[0].getFullName());
                  }
               }
            }
         }
      }
   }

   public void updateColorFrameCSSParentParams(ChartVSAssemblyInfo info, VSChartInfo chartInfo) {
      FormatInfo formatInfo = info.getFormatInfo();
      VSCompositeFormat compositeFormat = formatInfo.getFormat(VSAssemblyInfo.OBJECTPATH);

      if(compositeFormat == null) {
         return;
      }

      ArrayList<CSSParameter> parentParams = new ArrayList<>();
      VSCompositeFormat sheetFormat = formatInfo.getFormat(VSAssemblyInfo.SHEETPATH);

      if(sheetFormat != null) {
         parentParams.add(sheetFormat.getCSSFormat().getCSSParam());
      }

      VSCSSFormat cssFormat = compositeFormat.getCSSFormat();
      parentParams.add(cssFormat.getCSSParam());
      parentParams.trimToSize();

      for(boolean runtime : new boolean[]{ false, true }) {
         for(AestheticRef ref : chartInfo.getAestheticRefs(runtime)) {
            if(ref.getVisualFrame() instanceof CategoricalColorFrame) {
               ((CategoricalColorFrame) ref.getVisualFrame()).setParentParams(parentParams);
            }
            else if(ref.getVisualFrame() instanceof GradientColorFrame) {
               ((GradientColorFrame) ref.getVisualFrame()).setParentParams(parentParams);
            }
            else if(ref.getVisualFrame() instanceof HSLColorFrame) {
               ((HSLColorFrame) ref.getVisualFrame()).setParentParams(parentParams);
            }
         }
      }
   }

   protected static final VComparator vcomparator = new VComparator();
}
