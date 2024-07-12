/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.internal.graph;

import inetsoft.graph.aesthetic.*;
import inetsoft.report.composition.graph.*;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.*;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Fix information when the chart type is changed.
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class ChangeChartTypeProcessor extends ChangeChartProcessor {
   /**
    * Constructor.
    */
   public ChangeChartTypeProcessor() {
      super();
   }

   /**
    * Constructor.
    */
   public ChangeChartTypeProcessor(int oldType, int newType,
                                   ChartAggregateRef ref,
                                   ChartInfo info) {
      this(oldType, newType, false, false, ref, info, true, null);
   }

   /**
    * Constructor.
    */
   public ChangeChartTypeProcessor(int oldType, int newType,
                                   boolean omulti, boolean nmulti,
                                   ChartAggregateRef ref, ChartInfo info,
                                   boolean forced, ChartDescriptor desc)
   {
      super();

      // if only multi changed, the types are same
      if(omulti != nmulti && newType == -1) {
         newType = oldType;
      }

      this.omulti = omulti;
      this.nmulti = nmulti;
      this.oldType = oldType;
      this.newType = newType;
      this.ref = ref;
      this.info = info;
      this.oinfo = info;
      this.forced = forced;
      this.desc = desc;
   }

   /**
    * Process.
    */
   public ChartInfo process() {
      processMultiChanged();
      info = fixChartInfo();

      if(!GraphTypes.isTreemap(oldType) && GraphTypes.isTreemap(newType)) {
         copyToTreemap();
      }
      else if(!GraphTypes.isMekko(oldType) && GraphTypes.isMekko(newType)) {
         copyToMekko();
      }
      else if(!GraphTypes.isRelation(oldType) && GraphTypes.isRelation(newType)) {
         copyToRelation();
      }
      else if(GraphTypes.isRelation(oldType) && !GraphTypes.isRelation(newType)) {
         copyFromRelation();
      }
      else if(!GraphTypes.isGantt(oldType) && GraphTypes.isGantt(newType)) {
         copyToGantt();
      }
      else if(GraphTypes.isGantt(oldType) && !GraphTypes.isGantt(newType)) {
         copyFromGantt();
      }
      else if(!GraphTypes.isRadar(oldType) && GraphTypes.isRadar(newType)) {
         copyToRadar();
      }
      else if(GraphTypes.isRadar(oldType) && !GraphTypes.isRadar(newType)) {
         copyFromRadar();
      }
      else if(!GraphTypes.isFunnel(oldType) && GraphTypes.isFunnel(newType)) {
         copyToFunnel();
      }

      if(GraphTypes.isContour(newType) && newType != oldType) {
         copyToContour();
      }

      ChangeChartDataProcessor.sortRefs(info, false);

      // For box plot, do not support named group, so should clear all named group sorting.
      if(GraphTypes.isBoxplot(newType) || GraphTypes.isGantt(newType) ||
         GraphTypes.isRelation(newType))
      {
         clearNamedGroup();
      }

      fixColorFrame(oldType, newType);
      GraphUtil.fixVisualFrames(info);
      info.setTooltipVisible(oinfo.isTooltipVisible());

      if(!GraphTypes.supportsMultiStyles(newType)) {
         info.setSeparatedGraph(true);
         info.setMultiStyles(false);
      }

      return info;
   }

   private void clearNamedGroup() {
      VSDataRef[] arr = info.getFields();

      for(VSDataRef dim : arr) {
         if(dim instanceof ChartDimensionRef) {
            ChartDimensionRef cdim = (ChartDimensionRef) dim;

            if(cdim.getRealNamedGroupInfo() != null) {
               cdim.setNamedGroupInfo(null);

               if(cdim.getOrder() > XConstants.SORT_SPECIFIC) {
                  cdim.setOrder(cdim.getOrder() - XConstants.SORT_SPECIFIC);
               }
            }
         }
      }
   }

   private void fixColorFrame(int oldType, int newType) {
      if(GraphTypes.isContour(newType)) {
         if(!(info.getColorFrame() instanceof LinearColorFrame)) {
            info.setColorFrame(new BluesColorFrame());
         }
      }
      else if(!(info.getColorFrame() instanceof StaticColorFrame)) {
         info.setColorFrame(new StaticColorFrame());
      }
   }

   public void processMultiChanged() {
      processMultiChanged(false);
   }

   public void processMultiChanged(boolean rt) {
      if(omulti == nmulti) {
         return;
      }

      AllChartAggregateRef all = new AllChartAggregateRef(
         info, info.getAestheticAggregateRefs(rt));

      // copy global to individual aggregates
      if(nmulti) {
         copyAesthetic(info, all);
      }
      // copy the current aggregates to global
      else {
         copyAesthetic(all, info);
      }
   }

   /**
    * Copy the aesthetic settings.
    */
   private void copyAesthetic(ChartBindable from, ChartBindable to) {
      if(to != null && from != null) {
         to.setColorField(from.getColorField());
         to.setShapeField(from.getShapeField());
         to.setSizeField(from.getSizeField());
         to.setTextField(from.getTextField());

         if(to instanceof RelationChartInfo && from instanceof RelationChartInfo) {
            ((RelationChartInfo) to).setNodeColorField(((RelationChartInfo) from).getNodeColorField());
            ((RelationChartInfo) to).setNodeSizeField(((RelationChartInfo) from).getNodeSizeField());
         }
      }
   }

   /**
    * Check if the two refs equals.
    */
   private boolean isRefEquals(ChartAggregateRef aref, ChartRef ref) {
      // shouldn't use equalsContents otherwise the aesthetic fields
      // would be different and causes the ref to not match
      return Tool.equals(aref.getFullName(), ref.getFullName());
   }

   /**
    * Fix the chart info.
    */
   private ChartInfo fixChartInfo() {
      // if both are not merged graph type, just set chart type back
      if(!GraphTypes.isMergedGraphType(oldType) && !GraphTypes.isMergedGraphType(newType)) {
         if(ref != null) {
            int i;
            boolean isResetChartType = true;

            for(i = 0; i < info.getXFieldCount(); i++) {
               if(isRefEquals(ref, info.getXField(i))) {
                  ((ChartAggregateRef) info.getXField(i)).setChartType(newType);
                  break;
               }
            }

            if(GraphTypes.isWaterfall(newType) && i < info.getXFieldCount()) {
               ChartRef chartRef = info.getXField(i);
               int cnt = info.getXFieldCount();
               info.removeXFields();
               info.addXField(chartRef);

               if(cnt > 1) {
                  CoreTool.addUserMessage(Catalog.getCatalog().getString(
                     "em.common.graph.measuresNotSupported"));
               }
            }

            for(i = 0; i < info.getYFieldCount(); i++) {
               if(isRefEquals(ref, info.getYField(i))) {
                  ((ChartAggregateRef) info.getYField(i)).setChartType(newType);
                  break;
               }
            }

            if(GraphTypes.isWaterfall(newType) && i < info.getYFieldCount()) {
               ChartRef chartRef = info.getYField(i);
               int cnt = info.getYFieldCount();
               info.removeYFields();
               info.addYField(chartRef);

               if(cnt > 1) {
                  CoreTool.addUserMessage(Catalog.getCatalog().getString(
                     "em.common.graph.measuresNotSupported"));
               }
            }

            // clear rt charttype so the old type won't be copied over
            for(VSDataRef vref : info.getRTFields()) {
               for(VSDataRef vref0: info.getFields()) {
                  if(vref0 == vref) {
                     isResetChartType = false;
                     break;
                  }
               }

               if(vref instanceof ChartAggregateRef && isResetChartType) {
                  ((ChartAggregateRef) vref).setChartType(0);
               }
            }
         }
         else {
            info.setChartType(newType);

            if(info instanceof  VSChartInfo) {
               if(newType != GraphTypes.CHART_LINE &&
                  newType != GraphTypes.CHART_LINE_STACK &&
                  newType != GraphTypes.CHART_STEP &&
                  newType != GraphTypes.CHART_STEP_STACK &&
                  newType != GraphTypes.CHART_JUMP &&
                  newType != GraphTypes.CHART_STEP_AREA &&
                  newType != GraphTypes.CHART_STEP_AREA_STACK &&
                  newType != GraphTypes.CHART_AREA &&
                  newType != GraphTypes.CHART_AREA_STACK)
               {
                  ((VSChartInfo) info).setCombinedToolTipValue(false);
               }
            }

            // if it's waterfall, only keep one measure in chart info
            if(GraphTypes.isWaterfall(newType)) {
               checkMeasureRefs(info);
            }
         }

         validateInvertedChart(info);
         ChartRef ref0 = (ref != null) ? info.getFieldByName(ref.getFullName(), false) : null;

         if(ref0 instanceof ChartAggregateRef) {
            ChartAggregateRef aggr = (ChartAggregateRef) ref0;
            fixShapeField(aggr, info, getChartType(info, aggr));
            fixSizeField(aggr, getChartType(info, aggr));
         }
         else {
            fixShapeField(info, info, getChartType(info, null));
            fixSizeField(info, getChartType(info, null));
         }

         fixSizeFrame(info);
         fixPieDimensions(info);

         if(!info.supportsGroupFields()) {
            info.removeGroupFields();
         }

         fixAggregateRefs(info);
         fixMapDimensionRefs(info);
         return info;
      }

      // change between filled radar and normal radar
      if(GraphTypes.isRadar(newType) && GraphTypes.isRadar(oldType)) {
         info.setChartType(newType);
         // the runtime chart type should also be updated
         info.updateChartType(!info.isMultiStyles());
         fixAggregateRefs(info);
         fixMapDimensionRefs(info);
         return info;
      }

      ChartInfo newInfo;
      switch(newType) {
      case GraphTypes.CHART_RADAR:
      case GraphTypes.CHART_FILL_RADAR:
         newInfo = new RadarVSChartInfo();
         break;
      case GraphTypes.CHART_STOCK:
         newInfo = new StockVSChartInfo();
         break;
      case GraphTypes.CHART_CANDLE:
         newInfo = new CandleVSChartInfo();
         break;
      case GraphTypes.CHART_TREE:
      case GraphTypes.CHART_NETWORK:
      case GraphTypes.CHART_CIRCULAR:
         if(info instanceof RelationChartInfo) {
            newInfo = info;
         }
         else {
            newInfo = new RelationVSChartInfo();
         }
         break;
      case GraphTypes.CHART_GANTT:
         newInfo = new GanttVSChartInfo();
         break;
      case GraphTypes.CHART_MAP:
      case GraphTypes.CHART_MAP_CONTOUR:
         if(info instanceof MapInfo) {
            newInfo = info;
         }
         else {
            newInfo = new VSMapInfo();
         }
         break;
      default:
         newInfo = new DefaultVSChartInfo();
         break;
      }

      // user may be select auto chart and multi style one time
      // so here keep original multi style property
      // fix bug1359544427025
      if(GraphTypes.supportsMultiStyles(newType)) {
         newInfo.setMultiStyles(info.isMultiStyles());
      }

      if(info != newInfo) {
         copyFields(info, newInfo);
      }

      if(newInfo.getChartDescriptor() == null) {
         newInfo.setChartDescriptor(info.getChartDescriptor());
      }

      validateInvertedChart(newInfo);
      copyGeneralValues(info, newInfo);
      fixShapeField(newInfo, newInfo, getChartType(newInfo, ref));
      fixSizeField(newInfo, getChartType(newInfo, ref));
      fixSizeFrame(newInfo);
      fixPieDimensions(info);
      fixAggregateRefs(newInfo);
      fixMapDimensionRefs(newInfo);
      GraphDefault.setDefaultFormulas(info, newInfo);

      return newInfo;
   }

   /**
    * Fix the dimension for pie chart.
    */
   private void fixPieDimensions(ChartInfo info) {
      ChartRef ref0 = (ref != null) ? info.getFieldByName(ref.getFullName(), false) : null;
      ChartBindable bindable = (ref0 instanceof ChartAggregateRef) ? (ChartBindable) ref0 : info;

      // change to pie, move a dimension to aesthetic
      if(GraphTypes.isPie(newType) && !GraphTypes.isPie(oldType)) {
         AestheticRef colorFld = bindable.getColorField();
         AestheticRef shapeFld = bindable.getShapeField();
         AestheticRef sizeFld = bindable.getSizeField();
         boolean hasDim = colorFld != null &&
            GraphUtil.isDimension(colorFld.getDataRef()) ||
            shapeFld != null &&
            GraphUtil.isDimension(shapeFld.getDataRef()) ||
            sizeFld != null &&
            GraphUtil.isDimension(sizeFld.getDataRef());

         if(!hasDim) {
            int xcnt = info.getXFieldCount();
            int ycnt = info.getYFieldCount();
            DataRef dim = null;

            if(xcnt > 0 && GraphUtil.isDimension(info.getXField(xcnt - 1))) {
               dim = info.getXField(xcnt - 1);
               info.removeXField(xcnt - 1);
            }
            else if(ycnt > 0 && GraphUtil.isDimension(info.getYField(ycnt - 1))) {
               dim = info.getYField(ycnt - 1);
               info.removeYField(ycnt - 1);
            }

            if(dim != null) {
               colorFld = createAestheticRef(dim);
               colorFld.setVisualFrame(new CategoricalColorFrame());
               bindable.setColorField(colorFld);
            }
         }
      }
      // reverse the pie operation
      if(!GraphTypes.isPie(newType) && !GraphTypes.isMap(newType) &&
         GraphTypes.isPie(oldType))
      {
         int xcnt = info.getXFieldCount();
         int ycnt = info.getYFieldCount();
         boolean xdim = xcnt > 0 && GraphUtil.isDimension(info.getXField(xcnt - 1));
         boolean ydim = ycnt > 0 && GraphUtil.isDimension(info.getYField(ycnt - 1));
         boolean xagg = xcnt > 0 && GraphUtil.isMeasure(info.getXField(xcnt - 1));
         AestheticRef colorFld = bindable.getColorField();

         if(!xdim && !ydim &&
            colorFld != null && GraphUtil.isDimension(colorFld.getDataRef()))
         {
            bindable.setColorField(null);

            if(xagg) {
               info.addYField((ChartRef) colorFld.getDataRef());
            }
            else {
               info.addXField((ChartRef) colorFld.getDataRef());
            }
         }
      }
   }

   /**
    * Copy the general values from the old chart info to the new chart info.
    * @param oinfo the old chart info
    * @param ninfo the new chart info
    */
   private void copyGeneralValues(ChartInfo oinfo, ChartInfo ninfo) {
      // if both are merged graph type, copy highlight and hyperlink information
      if(GraphTypes.isMergedGraphType(oldType) && GraphTypes.isMergedGraphType(newType) &&
         oinfo instanceof MergedChartInfo && ninfo instanceof MergedChartInfo)
      {
         MergedChartInfo newtemp = ((MergedChartInfo) ninfo);
         newtemp.setHighlightGroup(oinfo.getHighlightGroup());
         newtemp.setHyperlink(oinfo.getHyperlink());
      }

      if(ninfo instanceof VSChartInfo) {
         ((VSChartInfo) ninfo).setAggregateInfo(((VSChartInfo) oinfo).getAggregateInfo());
      }

      // set aestheic field
      AestheticRef temp = oinfo.getColorField();

      if(temp != null && GraphUtil.isDimension(temp.getDataRef()) &&
         !GraphTypes.supportsDimensionAesthetic(newType, ChartConstants.AESTHETIC_COLOR))
      {
         temp = null;
      }

      ninfo.setColorField(temp);
      temp = oinfo.getShapeField();

      if(!GraphTypes.supportsShape(newType) ||
         (temp != null && temp.getDataRef() instanceof AestheticRef &&
         !GraphTypes.supportsDimensionAesthetic(newType, ChartConstants.AESTHETIC_SHAPE)))
      {
         temp = null;
      }

      ninfo.setShapeField(temp);
      temp = oinfo.getSizeField();

      if(!GraphTypes.supportsSize(newType) ||
         (temp != null && temp.getDataRef() instanceof AestheticRef &&
         !GraphTypes.supportsDimensionAesthetic(newType, ChartConstants.AESTHETIC_SIZE)))
      {
         temp = null;
      }

      ninfo.setSizeField(temp);
      temp = oinfo.getTextField();

      if(temp != null && temp.getDataRef() instanceof AestheticRef &&
         !GraphTypes.supportsDimensionAesthetic(newType, ChartConstants.AESTHETIC_TEXT))
      {
         temp = null;
      }

      ninfo.setTextField(temp);
      ninfo.setChartType(newType);

      // chart to map, move x/y measures to aesthetic field
      if(ninfo instanceof MapInfo && !(oinfo instanceof MapInfo)) {
         changeToMap(oinfo, ninfo);
      }
      else if(!(ninfo instanceof MapInfo) && (oinfo instanceof MapInfo)) {
         changeFromMap();
      }

      // copy other fields to aesthetic
      if(ninfo instanceof MapInfo && (GraphTypes.isCandle(oldType) || GraphTypes.isStock(oldType)))
      {
         copyOtherFields(oinfo, ninfo);
      }

      ninfo.setMeasureMapType(oinfo.getMapType());
      ninfo.setAxisDescriptor(info.getAxisDescriptor());
      ninfo.setAxisDescriptor2(info.getAxisDescriptor2());
   }

   /**
    * Called when changing from a regular chart to a map.
    */
   private void changeToMap(ChartInfo oinfo, ChartInfo ninfo) {
      for(int i = 0; i < oinfo.getXFieldCount(); i++) {
         ChartRef cref = (ChartRef) oinfo.getXField(i).clone();

         if(GraphUtil.isMeasure(cref)) {
            addAestheticField(ninfo, cref);
         }
      }

      for(int i = 0; i < oinfo.getYFieldCount(); i++) {
         ChartRef cref = (ChartRef) oinfo.getYField(i).clone();

         if(GraphUtil.isMeasure(cref)) {
            addAestheticField(ninfo, cref);
         }
      }

      if(desc != null) {
         desc.getTitlesDescriptor().getXTitleDescriptor().setVisible(false);
         desc.getTitlesDescriptor().getX2TitleDescriptor().setVisible(false);
         desc.getTitlesDescriptor().getYTitleDescriptor().setVisible(false);
         desc.getTitlesDescriptor().getY2TitleDescriptor().setVisible(false);
      }
   }

   /**
    * Called when changing from a map to a regular chart.
    */
   private void changeFromMap() {
      if(desc != null) {
         desc.getTitlesDescriptor().getXTitleDescriptor().setVisible(true);
         desc.getTitlesDescriptor().getX2TitleDescriptor().setVisible(true);
         desc.getTitlesDescriptor().getYTitleDescriptor().setVisible(true);
         desc.getTitlesDescriptor().getY2TitleDescriptor().setVisible(true);
      }
   }

   /**
    * Copy all binding fields from the old chart info to the new chart info.
    * @param oinfo the old chart info
    * @param ninfo the new chart info
    */
   private void copyFields(ChartInfo oinfo, ChartInfo ninfo) {
      boolean chartToMap = !(oinfo instanceof MapInfo) && ninfo instanceof MapInfo;
      boolean mapToChart = oinfo instanceof MapInfo && !(ninfo instanceof MapInfo);
      MapInfo ominfo = oinfo instanceof MapInfo ? (MapInfo) oinfo : null;
      MapInfo nminfo = ninfo instanceof MapInfo ? (MapInfo) ninfo : null;
      boolean copied = false; // check if has copied field to merged info

      // copy fields from y
      for(int i = 0; i < oinfo.getYFieldCount(); i++) {
         ChartRef cref = (ChartRef) oinfo.getYField(i).clone();

         if(GraphUtil.isMeasure(cref)) {
            // if it's waterfall or pie in non-separate graph, only keep one
            // measure in chart info
            if(GraphTypes.isWaterfall(newType)) {
               if(hasMeasure(ninfo)) {
                  CoreTool.addUserMessage(Catalog.getCatalog().getString(
                     "em.common.graph.measuresNotSupported"));
                  continue;
               }
            }
            // if chart to map, move y measures to aesthetic later
            else if(chartToMap) {
               continue;
            }
            // if the new type does not support 'separate', copy the measures
            // from y to merged info, like the high field, the low field, ect.
            else if(GraphTypes.isMergedGraphType(newType)) {
               // if the old type supports 'separate', reset the values of
               // highlight, hyperlink and chart type
               if(!GraphTypes.isMergedGraphType(oldType)) {
                  ChartAggregateRef aref = (ChartAggregateRef) cref;
                  aref.setHighlightGroup(null);
                  aref.setTextHighlightGroup(null);
                  aref.setHyperlink(null);
                  aref.setChartType(GraphTypes.CHART_AUTO);
               }

               copyFieldToMergedInfo(ninfo, cref, null, true);
               copied = true;
               continue;
            }
         }

         ninfo.addYField(cref);
      }

      // copy fields from x
      for(int i = 0; i < oinfo.getXFieldCount(); i++) {
         ChartRef cref = (ChartRef) oinfo.getXField(i).clone();

         if(GraphUtil.isMeasure(cref)) {
            // if it's waterfall or pie in non-separate graph, only keep one
            // measure in chart info
            if(GraphTypes.isWaterfall(newType)) {
               if(hasMeasure(ninfo)) {
                  CoreTool.addUserMessage(Catalog.getCatalog().getString(
                     "em.common.graph.measuresNotSupported"));
                  continue;
               }
            }
            // if chart to map, move y measures to aesthetic later
            else if(chartToMap) {
               continue;
            }
            else if(GraphTypes.isMergedGraphType(newType)) {
               // if have copied from y, don't copy from x.
               if(copied) {
                  continue;
               }

               // if the old type supports 'separate', reset the values of
               // highlight, hyperlink and chart type
               if(!GraphTypes.isMergedGraphType(oldType)) {
                  ChartAggregateRef aref = (ChartAggregateRef) cref;
                  // even if the setting is not applied, we shouldn't clear it so it's not
                  // lost when it's switched back (or if it does apply, e.g. funnel) (47364)
                  //aref.setHighlightGroup(null);
                  //aref.setHyperlink(null);
                  aref.setChartType(GraphTypes.CHART_AUTO);
               }

               copyFieldToMergedInfo(ninfo, cref, null, false);
               continue;
            }

         }

         ninfo.addXField(ninfo.getXFieldCount(), cref);
      }

      // copy fields from group
      for(int i = 0; i < oinfo.getGroupFieldCount(); i++) {
         ChartRef cref = (ChartRef) oinfo.getGroupField(i).clone();
         ninfo.addGroupField(cref);
      }

      // chart to map, if no geo field, move inner most x to geo
      if(chartToMap && nminfo.getGeoFieldCount() == 0) {
         boolean containsGeo = false;
         ChartRef[] rtflds = oinfo.getRTXFields();
         int cnt = rtflds.length;

         for(int i = 0; i < cnt; i++) {
            if(isGeoField(oinfo, ninfo, rtflds[i])) {
               containsGeo = true;
               break;
            }
         }

         // if contains geo field, move all geo fields to geo
         if(containsGeo) {
            ArrayList<ChartRef> list = new ArrayList<>();

            for(int i = ninfo.getXFieldCount() - 1; i >= 0; i--) {
               ChartRef fld = ninfo.getXField(i);
               ChartRef rtfld = rtflds[i];

               if(isGeoField(oinfo, ninfo, rtfld)) {
                  addGeoField(nminfo, fld);
               }
               else {
                  list.add(fld);
               }
            }

            ninfo.removeXFields();

            for(int i = list.size() - 1; i >= 0; i--) {
               ninfo.addXField(list.get(i));
            }
         }
         // if does not contain geo field, move first non date dim to geo
         else {
            for(int i = cnt - 1; i >= 0; i--) {
               ChartRef cref = ninfo.getXField(i);

               if(cref == null) {
                  continue;
               }

               int refType = cref.getRefType();

               if(cref instanceof XDimensionRef &&
                  !XSchema.isDateType(cref.getDataType()) &&
                  refType != DataRef.CUBE_TIME_DIMENSION &&
                  refType != DataRef.CUBE_MODEL_TIME_DIMENSION)
               {
                  addGeoField(nminfo, cref);
                  ninfo.removeXField(i);
                  break;
               }
            }
         }
      }

      // if map to chart, move geo fields to x fields
      if(mapToChart) {
         for(int i = 0; i < ominfo.getGeoFieldCount(); i++) {
            ChartRef cref = ominfo.getGeoFieldByName(i);

            cref = (ChartRef) cref.clone();
            ninfo.addXField(cref);
         }

         if(!GraphTypes.isCandle(newType) && !GraphTypes.isStock(newType)) {
            copyAestheticToY(oinfo, ninfo);
         }
      }

      new ChangeChartDataProcessor(ninfo).sortRefs(ninfo);

      // if the old doesn't support 'separate', copy the other special fields
      // to info, like the high field, the low field, ect.
      if(GraphTypes.isMergedGraphType(oldType)) {
         copyOtherFields(oinfo, ninfo);
      }

      // copy geo column selection
      copyGeoCols(oinfo, ninfo);
   }

   /**
    * Check if the specified ref is geographic.
    */
   private boolean isGeoField(ChartInfo oinfo, ChartInfo ninfo, ChartRef field) {
      if(ninfo instanceof VSChartInfo) {
         VSChartInfo vsinfo = (VSChartInfo) oinfo;

         return field instanceof VSDimensionRef &&
            vsinfo.isGeoColumn(field.getName());
      }
      else {
         return field instanceof GeoRef;
      }
   }

   /**
    * Add geographic field.
    */
   private void addGeoField(MapInfo ninfo, ChartRef ref) {
      ChartRef geoField;

      if(ref instanceof GeoRef) {
         geoField = ref;
      }
      else {
         VSChartDimensionRef dref = (VSChartDimensionRef) ref;
         geoField = new VSChartGeoRef(dref);
      }

      ninfo.addGeoField(0, geoField);
   }

   /**
    * Copy geo column selection.
    */
   private void copyGeoCols(ChartInfo oinfo, ChartInfo ninfo) {
      if(oinfo instanceof VSChartInfo && ninfo instanceof VSChartInfo) {
         VSChartInfo ocinfo = (VSChartInfo) oinfo;
         VSChartInfo ncinfo = (VSChartInfo) ninfo;
         ColumnSelection cols = (ColumnSelection) ocinfo.getGeoColumns().clone();
         ncinfo.setGeoColumns(cols);
      }
   }

   /**
    * Copy one of aesthetic measure field to Y.
    */
   private void copyAestheticToY(ChartInfo oinfo, ChartInfo ninfo) {
      AestheticRef color = oinfo.getColorField();
      AestheticRef shape = oinfo.getShapeField();
      AestheticRef size = oinfo.getSizeField();
      AestheticRef text = oinfo.getTextField();
      DataRef dataRef = null;

      if(text != null && GraphUtil.isMeasure(text.getDataRef())) {
         dataRef = text.getDataRef();
         oinfo.setTextField(null);
      }
      else if(size != null && GraphUtil.isMeasure(size.getDataRef())) {
         dataRef = size.getDataRef();
         oinfo.setSizeField(null);
      }
      else if(shape != null && GraphUtil.isMeasure(shape.getDataRef())) {
         dataRef = shape.getDataRef();
         oinfo.setShapeField(null);
      }
      else if(color != null && GraphUtil.isMeasure(color.getDataRef())) {
         dataRef = color.getDataRef();
         oinfo.setColorField(null);
      }

      if(dataRef != null) {
         VSChartAggregateRef cref = new VSChartAggregateRef();
         VSAggregateRef aref = (VSAggregateRef) dataRef;
         cref.setRefType(aref.getRefType());
         cref.setColumnValue(aref.getColumnValue());
         cref.setSecondaryColumnValue(aref.getSecondaryColumnValue());
         cref.setFormulaValue(aref.getFormulaValue());
         cref.setPercentageOptionValue(aref.getPercentageOptionValue());
         cref.setOriginalDataType(aref.getOriginalDataType());
         ninfo.addYField(cref);
      }
   }

   /**
    * Add the specfied field to the map aesthetic fields.
    */
   private void addAestheticField(ChartInfo info, ChartRef cref) {
      AestheticRef color = info.getColorField();
      AestheticRef shape = info.getShapeField();
      AestheticRef size = info.getSizeField();
      AestheticRef text = info.getTextField();
      boolean measure = cref.isMeasure();

      if(color == null) {
         color = createAestheticRef(cref);
         color.setVisualFrame(measure ? new BluesColorFrame() : new CategoricalColorFrame());
         info.setColorField(color);
      }
      else if(shape == null) {
         shape = createAestheticRef(cref);
         shape.setVisualFrame(measure ? new FillShapeFrame() : new CategoricalShapeFrame());
         info.setShapeField(shape);
      }
      // now geo ref is not auto detected, we can not know if supported, size
      // frame will be fixed in ChangeChartTypeEvent after auto detect
      else if(size == null) {
         size = createAestheticRef(cref);
         size.setVisualFrame(measure ? new LinearSizeFrame() : new CategoricalSizeFrame());
         info.setSizeField(size);
      }
      else if(text == null) {
         text = createAestheticRef(cref);
         text.setVisualFrame(new DefaultTextFrame());
         info.setTextField(text);
      }
   }

   /**
    * Create a AestheticRef wrapping the specified ref.
    */
   private AestheticRef createAestheticRef(DataRef cref) {
      AestheticRef aestheticRef = new VSAestheticRef();
      aestheticRef.setDataRef(cref);
      return aestheticRef;
   }

   /**
    * Check if there is any measure bound in chart info.
    */
   private boolean hasMeasure(ChartInfo info) {
      for(int i = 0; i < info.getYFieldCount(); i++) {
         if(info.getYField(i) instanceof ChartAggregateRef) {
            return true;
         }
      }

      for(int i = 0; i < info.getXFieldCount(); i++) {
         if(info.getXField(i) instanceof ChartAggregateRef) {
            return true;
         }
      }

      return false;
   }

   /**
    * Copy high, low, open, close fields to the new chart info.
    * @param oinfo the old chart info
    * @param ninfo the new chart info
    */
   private void copyOtherFields(ChartInfo oinfo, ChartInfo ninfo) {
      if(oinfo instanceof CandleChartInfo) {
         CandleChartInfo temp = (CandleChartInfo) oinfo;
         ChartRef aref = temp.getHighField();

         if(aref != null) {
            copyFieldToMergedInfo(ninfo, (ChartRef) aref.clone(), "high", true);
         }

         aref = temp.getCloseField();

         if(aref != null) {
            copyFieldToMergedInfo(ninfo, (ChartRef) aref.clone(), "close", true);
         }

         aref = temp.getLowField();

         if(aref != null) {
            copyFieldToMergedInfo(ninfo, (ChartRef) aref.clone(), "low", true);
         }

         aref = temp.getOpenField();

         if(aref != null) {
            copyFieldToMergedInfo(ninfo, (ChartRef) aref.clone(), "open", true);
         }
      }
   }

   /**
    * Copy a specified field to a merged chart info.
    * @param ninfo the new chart info
    * @param aref the specified field
    * @param type the specified field type, used by Stock and Candle.
    */
   private void copyFieldToMergedInfo(ChartInfo ninfo, ChartRef aref, String type, boolean y) {
      // to candle
      if(ninfo instanceof CandleChartInfo) {
         CandleChartInfo temp = (CandleChartInfo) ninfo;

         if(temp.getHighField() == null && (type == null || "high".equals(type))) {
            temp.setHighField(aref);
         }
         else if(temp.getCloseField() == null && (type == null || "close".equals(type))) {
            temp.setCloseField(aref);
         }
         else if(temp.getLowField() == null && (type == null || "low".equals(type))) {
            temp.setLowField(aref);
         }
         else if(temp.getOpenField() == null) {
            temp.setOpenField(aref);
         }

         if(aref instanceof ChartAggregateRef) {
            GraphFormatUtil.setDefaultNumberFormat(desc, ninfo, aref.getDataType(),
               (ChartAggregateRef) aref, GraphUtil.getHighLowRegionType(type));
         }
      }
      else if(ninfo instanceof MapInfo) {
         addAestheticField(ninfo, aref);
      }
      // to others
      else {
         // if it's waterfall or pie in non-separate graph, only keep one
         // measure in chart info
         if(GraphTypes.isWaterfall(newType) && hasMeasure(ninfo)) {
            CoreTool.addUserMessage(Catalog.getCatalog().getString(
               "em.common.graph.measuresNotSupported"));
            return;
         }

         if(y) {
            ninfo.addYField(aref);
         }
         else {
            ninfo.addXField(aref);
         }

         GraphUtil.fixStaticColorFrame(aref, ninfo, (ChartAggregateRef) aref);
      }
   }

   /**
    * Validate if it's an inverted Chart.
    * @param info the chart info needed to be fixed
    */
   private void validateInvertedChart(ChartInfo info) {
      // if the new type doesn't support inverted chart, lose all measures
      // in x coord
      if(!GraphTypes.supportsInvertedChart(newType)) {
         for(int i = info.getXFieldCount() - 1; i > -1; i--) {
            if(info.getXField(i).isMeasure()) {
               info.removeXField(i);
            }
         }

         if(info.getDefaultMeasure() == null && forced &&
            !GraphTypes.isMap(info.getChartType()))
         {
            info.setChartType(GraphTypes.CHART_AUTO);
         }
      }
   }

   /**
    * Get the available chart type to show a corresponding shape frame.
    */
   @Override
   protected int getChartType(ChartInfo info, ChartAggregateRef aggr) {
      ChartRef[] xrefs = info.getXFields();
      ChartRef[] yrefs = info.getYFields();
      info.updateChartType(!info.isMultiStyles(), xrefs, yrefs);

      if(aggr != null && info.isMultiStyles()) {
         return aggr.getRTChartType();
      }

      // if both are not merged graph and ref exists, get chart type from
      // the first field of Y coord, if there is no measure in Y, then get it
      // from X
      if(!GraphTypes.isMergedGraphType(oldType) &&
         !GraphTypes.isMergedGraphType(newType) && ref != null)
      {
         for(int i = 0; i < info.getYFieldCount(); i++) {
            if(info.getYField(i) instanceof ChartAggregateRef) {
               int type =
                  getFieldChartType((ChartAggregateRef) info.getYField(i));

               if(type == GraphTypes.CHART_AUTO) {
                  return info.getRTChartType(type,
                                             getLastField(info.getXFields()),
                                             info.getYField(i), -1);
               }

               return type;
            }
         }

         for(int i = 0; i < info.getXFieldCount(); i++) {
            if(info.getXField(i) instanceof ChartAggregateRef) {
               int type =
                  getFieldChartType((ChartAggregateRef) info.getXField(i));

               if(type == GraphTypes.CHART_AUTO) {
                  return info.getRTChartType(type,
                                             info.getXField(i),
                                             getLastField(info.getYFields()), -1);
               }

               return type;
            }
         }
      }
      else {
         if(newType == GraphTypes.CHART_AUTO) {
            ChartRef xref = getLastField(info.getXFields());
            ChartRef yref = getLastField(info.getYFields());
            return info.getRTChartType(newType, xref, yref, -1);
         }

         return newType;
      }

      return GraphTypes.CHART_AUTO;
   }

   /**
    * Get the last chart ref.
    */
   private ChartRef getLastField(ChartRef[] refs) {
      return refs == null || refs.length == 0 ? null : refs[refs.length - 1];
   }

   /**
    * Get the chart type from the field. If the chart type is auto, try to get
    * the runtime chart type. If the runtime chart type is empty, just return
    * the auto back.
    */
   private int getFieldChartType(ChartAggregateRef aref) {
      int type = aref.getChartType();
      int rtype = aref.getRTChartType();
      return (type == GraphTypes.CHART_AUTO) ? rtype : type;
   }

   // copy x/y dimensions to tree-dims, and measure to size
   private void copyToTreemap() {
      ChartRef[] xfields = info.getXFields();
      ChartRef[] yfields = info.getYFields();
      ChartRef[] groupfields = info.getGroupFields();

      List<ChartRef> treeDims = new ArrayList<>();
      // all measures from x/y/g
      List<ChartRef> measures = new ArrayList<>();
      // all dimensions from x
      List<ChartRef> xdims = new ArrayList<>();
      // all dimensions from y
      List<ChartRef> ydims = new ArrayList<>();

      for(ChartRef field : xfields) {
         if(field instanceof XAggregateRef) {
            measures.add(field);
         }
         else {
            xdims.add(field);
         }
      }

      for(ChartRef field : yfields) {
         if(field instanceof XAggregateRef) {
            measures.add(field);
         }
         else {
            ydims.add(field);
         }
      }

      if(oinfo instanceof RelationChartInfo) {
         treeDims.add(((RelationChartInfo) oinfo).getSourceField());
         treeDims.add(((RelationChartInfo) oinfo).getTargetField());
      }

      for(ChartRef field : groupfields) {
         if(field instanceof XAggregateRef) {
            measures.add(field);
         }
         else {
            treeDims.add(field);
         }
      }

      if(treeDims.isEmpty()) {
         if(xdims.size() > 0) {
            treeDims.addAll(xdims);
            xdims.clear();
         }
         else if(ydims.size() > 0) {
            treeDims.addAll(ydims);
            ydims.clear();
         }
      }

      info.removeXFields();
      info.removeYFields();
      info.removeGroupFields();

      xdims.stream().filter(f -> f != null).forEach(d -> info.addXField(d));
      ydims.stream().filter(f -> f != null).forEach(d -> info.addYField(d));
      treeDims.stream().filter(f -> f != null).forEach(d -> info.addGroupField(d));

      if(info.getSizeField() == null && measures.size() > 0) {
         AestheticRef aref = createAestheticRef(measures.get(0));
         aref.setVisualFrame(new LinearSizeFrame());
         info.setSizeField(aref);
         measures.remove(0);
      }

      if(info.getColorField() == null && measures.size() > 0) {
         AestheticRef aref = createAestheticRef(measures.get(0));
         aref.setVisualFrame(new BluesColorFrame());
         info.setColorField(aref);
         measures.remove(0);
      }

      if(info.getShapeField() == null && measures.size() > 0) {
         AestheticRef aref = createAestheticRef(measures.get(0));
         aref.setVisualFrame(new GridTextureFrame());
         info.setShapeField(aref);
         measures.remove(0);
      }
   }

   // copy dimensions to x/group, and measure to y
   private void copyToMekko() {
      ChartRef[] xfields = info.getXFields();
      ChartRef[] yfields = info.getYFields();
      ChartRef[] groupfields = info.getGroupFields();

      // all measures from x/y/g
      List<ChartRef> measures = new ArrayList<>();
      // all dimensions from x/y/g
      List<ChartRef> dims = new ArrayList<>();

      ChartRef[][] allfields = {xfields, yfields, groupfields};

      for(ChartRef[] fields : allfields) {
         for(ChartRef field : fields) {
            if(field instanceof XAggregateRef) {
               measures.add(field);
            }
            else {
               dims.add(field);
            }
         }
      }

      info.removeXFields();
      info.removeYFields();
      info.removeGroupFields();

      if(dims.size() > 0) {
         info.addXField(dims.remove(0));
      }

      if(dims.size() > 0) {
         info.addGroupField(dims.remove(0));
      }

      if(measures.size() > 0) {
         info.addYField(measures.remove(0));
      }

      if(info.getGroupFieldCount() == 0) {
         if(info.getSizeField() != null &&
            info.getSizeField().getDataRef() instanceof VSDimensionRef)
         {
            info.addGroupField((ChartRef) info.getSizeField().getDataRef());
         }
         else if(info.getShapeField() != null &&
                 info.getShapeField().getDataRef() instanceof VSDimensionRef)
         {
            info.addGroupField((ChartRef) info.getShapeField().getDataRef());
         }
         else if(info.getColorField() != null &&
                 info.getColorField().getDataRef() instanceof VSDimensionRef)
         {
            info.addGroupField((ChartRef) info.getColorField().getDataRef());
         }
      }

      if(info.getColorField() == null && (dims.size() > 0 || measures.size() > 0)) {
         ChartRef ref = dims.size() > 0 ? dims.remove(0) : measures.remove(0);
         AestheticRef aref = createAestheticRef(ref);
         aref.setVisualFrame(ref.isMeasure() ? new BluesColorFrame() : new CategoricalColorFrame());
         info.setColorField(aref);
      }

      if(info.getShapeField() == null && (dims.size() > 0 || measures.size() > 0)) {
         ChartRef ref = dims.size() > 0 ? dims.remove(0) : measures.remove(0);
         AestheticRef aref = createAestheticRef(ref);
         aref.setVisualFrame(ref.isMeasure() ? new GridTextureFrame()
                                : new CategoricalTextureFrame());
         info.setShapeField(aref);
      }

      if(info.getSizeField() == null && (dims.size() > 0 || measures.size() > 0)) {
         ChartRef ref = dims.size() > 0 ? dims.remove(0) : measures.remove(0);
         AestheticRef aref = createAestheticRef(ref);
         aref.setVisualFrame(ref.isMeasure() ? new LinearSizeFrame() : new CategoricalSizeFrame());
         info.setSizeField(aref);
      }
   }

   // copy x/y dimensions to source/target, and measure to size
   private void copyToRelation() {
      ChartRef[] xfields = info.getXFields();
      ChartRef[] yfields = info.getYFields();
      ChartRef[] groupfields = info.getGroupFields();

      List<ChartRef> treeDims = new ArrayList<>();
      // all measures from x/y/g
      List<ChartRef> measures = new ArrayList<>();
      // all dimensions from x
      List<ChartRef> xdims = new ArrayList<>();
      // all dimensions from y
      List<ChartRef> ydims = new ArrayList<>();

      for(ChartRef field : xfields) {
         if(field instanceof XAggregateRef) {
            measures.add(field);
         }
         else {
            xdims.add(field);
         }
      }

      for(ChartRef field : yfields) {
         if(field instanceof XAggregateRef) {
            measures.add(field);
         }
         else {
            ydims.add(field);
         }
      }

      for(ChartRef field : groupfields) {
         if(field instanceof XAggregateRef) {
            measures.add(field);
         }
         else {
            treeDims.add(field);
         }
      }

      while(treeDims.size() < 2 && (xdims.size() > 0 || ydims.size() > 0)) {
         if(xdims.size() > 0) {
            treeDims.add(xdims.remove(0));
         }
         else {
            treeDims.add(ydims.remove(0));
         }
      }

      info.removeXFields();
      info.removeYFields();
      info.removeGroupFields();

      xdims.stream().forEach(d -> info.addXField(d));
      ydims.stream().forEach(d -> info.addYField(d));

      if(treeDims.size() > 0) {
         ((RelationChartInfo) info).setSourceField(treeDims.remove(0));
      }

      if(treeDims.size() > 0) {
         ((RelationChartInfo) info).setTargetField(treeDims.remove(0));
      }

      if(info.getSizeField() == null && measures.size() > 0) {
         AestheticRef aref = createAestheticRef(measures.get(0));
         aref.setVisualFrame(new LinearSizeFrame());
         info.setSizeField(aref);
         measures.remove(0);
      }

      if(info.getColorField() == null && measures.size() > 0) {
         AestheticRef aref = createAestheticRef(measures.get(0));
         aref.setVisualFrame(new BluesColorFrame());
         info.setColorField(aref);
         measures.remove(0);
      }

      if(info.getShapeField() == null && measures.size() > 0) {
         AestheticRef aref = createAestheticRef(measures.get(0));
         aref.setVisualFrame(new GridTextureFrame());
         info.setShapeField(aref);
         measures.remove(0);
      }
   }

   // copy source/target dimensions to x/y.
   private void copyFromRelation() {
      if(oinfo instanceof RelationChartInfo) {
         RelationChartInfo info2 = (RelationChartInfo) oinfo;

         if(info2.getSourceField() != null) {
            info.addGroupField(info2.getSourceField());
         }

         if(info2.getTargetField() != null) {
            info.addGroupField(info2.getTargetField());
         }
      }
   }

   // copy x/y date dimensions to start/end/milestone, and measure to color
   private void copyToGantt() {
      ChartRef[][] allfields = {info.getXFields(), info.getYFields(), info.getGroupFields()};

      List<ChartRef> dateDims = new ArrayList<>();
      // all measures from x/y/g
      List<ChartRef> measures = new ArrayList<>();
      // all non-date dimensions from x/y/group
      List<ChartRef> otherdims = new ArrayList<>();

      for(ChartRef[] fields: allfields) {
         for(ChartRef field : fields) {
            addFieldForGantt(dateDims, measures, otherdims, field);
         }
      }

      for(AestheticRef aref : oinfo.getAestheticRefs(false)) {
         DataRef ref = aref.getDataRef();
         addFieldForGantt(dateDims, measures, otherdims, (ChartRef) ref);
      }

      info.removeXFields();
      info.removeYFields();
      info.removeGroupFields();

      GanttChartInfo ginfo = (GanttChartInfo) info;

      // add dates to start/end/milestone
      if(dateDims.size() > 0) {
         ginfo.setStartField(dateDims.remove(0));
      }

      if(dateDims.size() > 0) {
         ginfo.setEndField(dateDims.remove(0));
      }

      if(dateDims.size() > 0) {
         ginfo.setMilestoneField(dateDims.remove(0));
      }

      otherdims.addAll(dateDims);
      otherdims.stream().forEach(d -> info.addYField(d));

      ChartAggregateRef startField = (ChartAggregateRef) ginfo.getStartField();

      if(startField != null) {
         if(startField.getColorField() == null && measures.size() > 0) {
            AestheticRef aref = createAestheticRef(measures.get(0));
            aref.setVisualFrame(new BluesColorFrame());
            startField.setColorField(aref);
            measures.remove(0);
         }

         if(startField.getShapeField() == null && measures.size() > 0) {
            AestheticRef aref = createAestheticRef(measures.get(0));
            aref.setVisualFrame(new GridTextureFrame());
            startField.setShapeField(aref);
            measures.remove(0);
         }

         if(startField.getSizeField() == null && measures.size() > 0) {
            AestheticRef aref = createAestheticRef(measures.get(0));
            aref.setVisualFrame(new LinearSizeFrame());
            startField.setSizeField(aref);
            measures.remove(0);
         }
      }

      GraphUtil.fixVisualFrames(info);
   }

   private void addFieldForGantt(List<ChartRef> dateDims, List<ChartRef> measures,
                                 List<ChartRef> otherdims, ChartRef field)
   {
      if(field instanceof ChartDimensionRef) {
         ChartDimensionRef dim = (ChartDimensionRef) field;

         if(dim.getRealNamedGroupInfo() != null)  {
            dim.setNamedGroupInfo(null);
         }
      }

      if(field instanceof XAggregateRef) {
         if(XSchema.isDateType(((XAggregateRef) field).getOriginalDataType())) {
            dateDims.add(field);
            ((XAggregateRef) field).setFormula(AggregateFormula.NONE);
         }
         else {
            measures.add(field);
         }
      }
      else if(XSchema.isDateType(field.getDataType())) {
         otherdims.add(field);
      }
      else {
         otherdims.add(field);
      }
   }

   // copy source/target dimensions to x/y.
   private void copyFromGantt() {
      if(oinfo instanceof GanttChartInfo) {
         GanttChartInfo info2 = (GanttChartInfo) oinfo;
         int maxY = Integer.MAX_VALUE;

         if(info instanceof VSChartInfo) {
            if(GraphTypes.isFunnel(info.getChartType()) ||
               GraphTypes.isTreemap(info.getChartType()) ||
               GraphTypes.isRelation(info.getChartType()) ||
               GraphTypes.isCandle(info.getChartType()) ||
               GraphTypes.isStock(info.getChartType()) ||
               GraphTypes.isMap(info.getChartType()))
            {
               maxY = 0;
            }
            else if(GraphTypes.isMekko(info.getChartType()) ||
               GraphTypes.isWaterfall(info.getChartType()))
            {
               maxY = 1;
            }
         }

         List<ChartRef> ganttFields = new ArrayList<>(
            Arrays.asList(info2.getStartField(), info2.getEndField(), info2.getMilestoneField()));

         while(!ganttFields.isEmpty()) {
            ChartRef ref = ganttFields.remove(0);

            if(ref != null) {
               if(maxY == 0) {
                  addToAesthetic(createAestheticRef(ref));
               }
               else {
                  info.addYField(ref);

                  maxY--;
               }
            }
         }

         for(AestheticRef aref : oinfo.getAestheticRefs(false)) {
            addToAesthetic(aref);
         }

         GraphUtil.fixVisualFrames(info);
      }
   }

   private void addToAesthetic(AestheticRef aref) {
      if(info.getColorField() == null) {
         info.setColorField(aref);
      }
      else if(info.getShapeField() == null) {
         info.setShapeField(aref);
      }
      else if(info.getSizeField() == null) {
         info.setSizeField(aref);
      }
      else if(info.getTextField() == null) {
         info.setTextField(aref);
      }
   }

   private void copyToRadar() {
      if(info.getGroupFieldCount() == 0 && info.getXFieldCount() > 0 &&
         info.getXField(info.getXFieldCount() - 1) instanceof XDimensionRef)
      {
         info.addGroupField(info.getXField(info.getXFieldCount() - 1));
         info.removeXField(info.getXFieldCount() - 1);
      }
   }

   private void copyFromRadar() {
      if(info.getGroupFieldCount() == 1) {
         info.addXField(info.getGroupField(info.getGroupFieldCount() - 1));
         info.removeGroupField(info.getGroupFieldCount() - 1);
      }
   }

   private void copyToFunnel() {
      for(int i = info.getYFieldCount() - 1; i >= 0; i--) {
         if(info.getYField(i) instanceof ChartAggregateRef) {
            // trend/comparison not supported
            ((ChartAggregateRef) info.getYField(i)).setCalculator(null);
            info.addXField(info.getYField(i));
            info.removeYField(i);
         }
      }

      if(info.getYFieldCount() == 0) {
         for(int i = info.getXFieldCount() - 1; i >= 0; i--) {
            if(info.getXField(i) instanceof ChartDimensionRef) {
               info.addYField(info.getXField(i));
               info.removeXField(i);
               break;
            }
         }
      }
   }

   private void copyToContour() {
      // no sense combining measures on one since they are indistinuishable.
      info.setSeparatedGraph(true);

      if(info.getColorField() != null) {
         AestheticRef cfield = info.getColorField();
         info.setColorField(null);

         if(info.getSizeField() == null && cfield.getDataRef() instanceof ChartAggregateRef) {
            info.setSizeField(cfield);
            cfield.setVisualFrameWrapper(new LinearSizeFrameWrapper());
         }
      }

      // shape and color not supported
      info.setShapeField(null);

      if(!(info.getColorFrameWrapper() instanceof LinearColorFrameWrapper)) {
         if(oinfo.getColorFrameWrapper() instanceof LinearColorFrameWrapper) {
            info.setColorFrameWrapper(oinfo.getColorFrameWrapper());
         }
         else {
            info.setColorFrameWrapper(new BluesColorFrameWrapper());
         }
      }

      // trend/comparison not supported, and should show scattered plot for contour
      for(int i = info.getYFieldCount() - 1; i >= 0; i--) {
         if(info.getYField(i) instanceof ChartAggregateRef) {
            ((ChartAggregateRef) info.getYField(i)).setCalculator(null);
            ((ChartAggregateRef) info.getYField(i)).setFormula(AggregateFormula.NONE);
            ((ChartAggregateRef) info.getYField(i)).setDiscrete(false);
         }
      }

      for(int i = info.getXFieldCount() - 1; i >= 0; i--) {
         if(info.getXField(i) instanceof ChartAggregateRef) {
            ((ChartAggregateRef) info.getXField(i)).setCalculator(null);
            ((ChartAggregateRef) info.getXField(i)).setFormula(AggregateFormula.NONE);
            ((ChartAggregateRef) info.getXField(i)).setDiscrete(false);
         }
      }
   }

   private boolean nmulti;
   private boolean omulti;
   private int newType;
   private int oldType;
   private ChartAggregateRef ref;
   private ChartInfo info, oinfo;
   private ChartDescriptor desc;
   private boolean forced = true; // forced change to auto if without measure

   private static final Logger LOG =
      LoggerFactory.getLogger(ChangeChartTypeProcessor.class);
}
