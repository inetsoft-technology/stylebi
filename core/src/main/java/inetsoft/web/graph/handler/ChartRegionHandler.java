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
package inetsoft.web.graph.handler;

import inetsoft.graph.aesthetic.GradientColorFrame;
import inetsoft.graph.aesthetic.LinearColorFrame;
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.region.*;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.adhoc.model.FormatInfoModel;
import inetsoft.web.adhoc.model.chart.ChartFormatConstants;
import inetsoft.web.graph.model.dialog.AxisPropertyDialogModel;
import inetsoft.web.graph.model.dialog.LegendFormatDialogModel;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ChartRegionHandler {
   public AxisDescriptor getAxisDescriptor(ChartInfo info, String columnName,
                                           String axisType, AtomicBoolean linear)
   {
      String column = columnName;
      ChartRef ref = getChartRef(info, axisType, column);
      AxisDescriptor axisDes;
      linear.set(ref instanceof ChartAggregateRef);
      // if measures on both x and y, x axis descriptor is from aggregateRef.
      // see DefaultGraphGenerator.getAxisDescriptor().
      boolean asDim = "bottom_x_axis".equals(axisType) &&
         Arrays.stream(info.getRTYFields())
            .anyMatch(a -> a instanceof ChartAggregateRef && !((ChartAggregateRef) a).isDiscrete());

      if("_Parallel_Label_".equals(column) && GraphTypes.isRadarN(info)) {
         axisDes = ((RadarChartInfo) info).getLabelAxisDescriptor();
         linear.set(false);
      }
      else if(ref != null && (info instanceof RadarChartInfo ||
                              ref instanceof ChartAggregateRef && (asDim || info.isSeparatedGraph())))
      {
         axisDes = ref.getAxisDescriptor();
      }
      else if(ref != null && info.isSeparatedGraph() &&
              !(info instanceof StockChartInfo) && !(info instanceof CandleChartInfo))
      {
         axisDes = ref.getAxisDescriptor();
      }
      else if(ref instanceof ChartAggregateRef && ((ChartAggregateRef) ref).isSecondaryY()) {
         axisDes = info.getAxisDescriptor2();
      }
      // if inseparate graph or candle, stock chart, get dimension descriptor
      // from ref, get shared measure descriptor from info
      else if(ref != null && (info.isSeparatedGraph() || ref instanceof XDimensionRef ||
         GraphUtil.isDiscrete(ref)))
      {
         axisDes = ref.getAxisDescriptor();
      }
      else {
         axisDes = info.getAxisDescriptor();
         linear.set(true);
      }

      return axisDes;
   }

   public static ChartRef getChartRef(ChartInfo info, String axisType, String column) {
      ChartRef ref = null;
      boolean mekko = info.getRTChartType() == GraphTypes.CHART_MEKKO;

      if(info instanceof VSChartInfo && ((VSChartInfo) info).isPeriodRef(column)) {
         ref = ((VSChartInfo) info).getPeriodField();
      }
      else if(ChartFormatConstants.LEFT_Y_AXIS.equals(axisType) ||
              ChartFormatConstants.RIGHT_Y_AXIS.equals(axisType) ||
              ChartFormatConstants.TOP_X_AXIS.equals(axisType) && mekko ||
              ChartFormatConstants.AXIS.equals(axisType) && !GraphTypes.isRadarOne(info))
      {
         // scattered matrix has the measures on both x and y, and x AxisDescriptor is
         // used in GraphGenerator (see GraphGenerator.getChartRef()).
         boolean scatter = GraphTypeUtil.isScatterMatrix(info);
         ref = findDataRef(info, column, !scatter);
      }
      else if(ChartFormatConstants.TOP_X_AXIS.equals(axisType) ||
              ChartFormatConstants.BOTTOM_X_AXIS.equals(axisType))
      {
         ref = findDataRef(info, column, false);
      }
      else if("y".equals(axisType) || "x".equals(axisType)) {
         boolean y = "y".equals(axisType);
         ref = findDataRef(info, column, y);
      }
      else if("_Parallel_Label_".equals(axisType) && !GraphTypes.isRadarOne(info)) {
         ref = findDataRef(info, column, false);

         if(ref == null) {
            ref = findDataRef(info, column, true);
         }
      }
      else if(GraphTypes.isRadarOne(info)) {
         ref = info.getFieldByName(column, false);
      }

      // mekko top x is a y field. (57876)
      if(ref == null && GraphTypes.isMekko(info.getChartType()) && "x".equals(axisType)) {
         ref = ChartRegionHandler.getChartRef(info, "y", column);
      }

      return ref;
   }

   public static String getXfieldsIndex(ChartInfo info, String columnName, boolean isY) {
      String column = columnName;
      ChartRef[] fields = isY ? info.getYFields() : info.getXFields();
      int axisIndex = 0;

      while(axisIndex < fields.length) {
         if(fields[axisIndex].getFullName().equals(column)) {
            if(!(info instanceof RadarChartInfo) && fields[axisIndex] instanceof ChartAggregateRef) {
               if(((ChartAggregateRef) fields[axisIndex]).isSecondaryY()) {
                  axisIndex = 1;
               }
               else {
                  axisIndex = 0;
               }
            }

            break;
         }

         axisIndex++;
      }

      return (isY ? "y" : "x") + (axisIndex == 0 ? "" : axisIndex + 1);
   }

   private static ChartRef findDataRef(ChartInfo info, String column, boolean y) {
      ChartRef ref = null;

      if(info instanceof VSChartInfo) {
         ChartRef[] refs = ((VSChartInfo) info).getRuntimeDateComparisonRefs();

         ref = Arrays.stream(refs)
            .filter(chartRef -> chartRef.getFullName().equals(column))
            .findAny()
            .orElse(null);
      }

      ChartRef[] fields = y ? info.getYFields() : info.getXFields();

      if(ref == null) {
         ref = Arrays.stream(fields)
            .filter(chartRef -> chartRef.getFullName().equals(column))
            .findAny()
            .orElse(null);
      }

      // if not found in design field, it could be because the design field full name
      // not the same as runtime full name. this can be caused when a (non-detail)
      // calculcated field is used as aggregate, so the formula (e.g. Sum) is not added
      // to the full name. but this logic is only applied if the inner ref is set, which
      // is only in runtime ref
      if(ref == null) {
         ref = Arrays.stream(fields)
            .filter(chartRef -> chartRef.getName().equals(column))
            .findAny()
            .orElse(null);
      }

      // if the field is dynamically created, get the runtime field, which has the
      // AxisDescriptor set from the parent VSChartDimensionRef. (42152)
      if(ref == null && info instanceof VSChartInfo) {
         ChartRef[] fields2 = y ? info.getRTYFields() : info.getRTXFields();
         ref = Arrays.stream(fields2)
            .filter(chartRef -> chartRef.getFullName().equals(column))
            .findAny()
            .orElse(null);
      }

      if(ref == null && info instanceof GanttChartInfo) {
         GanttChartInfo ginfo = (GanttChartInfo) info;

         if(ginfo.getStartField() != null && ginfo.getStartField().getFullName().equals(column)) {
            return ginfo.getStartField();
         }

         if(ginfo.getMilestoneField() != null &&
            ginfo.getMilestoneField().getFullName().equals(column))
         {
            return ginfo.getMilestoneField();
         }

         if(ginfo.getEndField() != null && ginfo.getEndField().getFullName().equals(column)) {
            return ginfo.getEndField();
         }
      }

      return ref;
   }

   public AxisDescriptor getAxisDescriptor(ChartInfo info, ChartArea chartArea,
                                           String axisType, int index, String field)
         throws Exception
   {
      String columnName = field != null ? field
         : getAxisColumnName(info, chartArea, axisType, index);
      return getAxisDescriptor(info, columnName, axisType, new AtomicBoolean());
   }

   /**
    * Gets the title area.
    */
   private AxisArea getAxisArea(ChartArea chartArea, String axisType) {
      if(chartArea == null) {
         return null;
      }

      AxisArea axisArea = null;

      if(ChartFormatConstants.X_TITLE.equals(axisType) ||
         ChartFormatConstants.BOTTOM_X_AXIS.equals(axisType))
      {
         axisArea = chartArea.getBottomXAxisArea();
      }
      else if(ChartFormatConstants.X2_TITLE.equals(axisType) ||
         ChartFormatConstants.TOP_X_AXIS.equals(axisType))
      {
         axisArea = chartArea.getTopXAxisArea();
      }
      else if(ChartFormatConstants.Y_TITLE.equals(axisType) ||
         ChartFormatConstants.LEFT_Y_AXIS.equals(axisType))
      {
         axisArea = chartArea.getLeftYAxisArea();
      }
      else if(ChartFormatConstants.Y2_TITLE.equals(axisType) ||
         ChartFormatConstants.RIGHT_Y_AXIS.equals(axisType))
      {
         axisArea = chartArea.getRightYAxisArea();
      }

      return axisArea;
   }

   public AxisPropertyDialogModel createAxisPropertyDialogModel(
      ChartInfo info, ChartArea chartArea, String axisType, int axisIdx, String field,
      boolean isFacetGrid, boolean maxMode) throws Exception
   {
      String columnName = field;
      boolean isOuter = false;
      AtomicBoolean isLinear = new AtomicBoolean(false);
      AxisDescriptor axisDesc = null;
      AxisArea axisArea = getAxisArea(chartArea, axisType);

      if(axisIdx >= 0 && axisArea != null) {
         DefaultArea[] areas = axisArea.getAllAreas();

         if(areas.length > 0 && axisIdx < areas.length) {
            DefaultArea area = areas[axisIdx];

            if(area instanceof AxisLineArea) {
               AxisLineArea lineArea = (AxisLineArea) area;
               columnName = lineArea.getFieldName();
               isOuter = false;
               isLinear.set(true);
            }
            else if(area instanceof DimensionLabelArea) {
               DimensionLabelArea dimensionLabelArea = (DimensionLabelArea) area;
               columnName = dimensionLabelArea.getDimensionName();
               isOuter = dimensionLabelArea.isOuter();
               isLinear.set(false);
            }
            else if(area instanceof MeasureLabelsArea) {
               MeasureLabelsArea measureLabelsArea = (MeasureLabelsArea) area;
               columnName = measureLabelsArea.getMeasureName();
               isOuter = false;
               isLinear.set(true);
            }
         }

         axisDesc = getAxisDescriptor(info, chartArea, axisType, axisIdx, field);
      }
      else {
         axisDesc = getAxisDescriptor(info, columnName, axisType, isLinear);
      }

      return new AxisPropertyDialogModel(info, axisDesc, isFacetGrid, chartArea, columnName,
                                         isOuter, isLinear.get(), axisType, maxMode);
   }

   public String getAxisColumnName(ChartInfo info, ChartArea chartArea,
      String axisType, int index)
   {
      if("_Parallel_Label_".equals(axisType)) {
         return "_Parallel_Label_";
      }

      if(chartArea == null) {
         return null;
      }

      AxisArea axisArea = getAxisArea(chartArea, axisType);
      DefaultArea area = axisArea.getAllAreas()[index];
      String columnName = null;

      if(area instanceof AxisLineArea) {
         AxisLineArea lineArea = (AxisLineArea) area;
         columnName = lineArea.getFieldName();
      }
      else if(area instanceof DimensionLabelArea) {
         DimensionLabelArea dimensionLabelArea = (DimensionLabelArea) area;
         columnName = dimensionLabelArea.getDimensionName();
      }
      else if(area instanceof MeasureLabelsArea) {
         MeasureLabelsArea measureLabelsArea = (MeasureLabelsArea) area;
         columnName = measureLabelsArea.getMeasureName();
      }

      return columnName;
   }

   public void updateAxisPropertyDialogModel(AxisPropertyDialogModel model, ChartInfo info,
                                             ChartArea chartArea, String axisType, int index,
                                             String field, boolean maxMode) throws Exception
   {
      String columnName = field;

      if(field == null || field.isEmpty()) {
         columnName = getAxisColumnName(info, chartArea, axisType, index);
      }

      AxisDescriptor axisDesc = getAxisDescriptor(info, columnName, axisType, new AtomicBoolean());
      model.updateAxisPropertyDialogModel(axisDesc, columnName, axisType, maxMode);
   }

   public LegendFormatDialogModel createLegendFormatDialogModel(ChartInfo info,
      ChartArea chartArea, ChartDescriptor descriptor, int legendIdx)
   {
      LegendsArea legendsArea = chartArea.getLegendsArea();
      LegendArea legendArea = legendsArea.getLegendAreas()[legendIdx];
      List<String> targetFields = legendArea.getTargetFields();
      String titleName = legendArea.getVisualFrame() != null ?
         legendArea.getVisualFrame().getTitle() : null;
      String aestheticType = legendArea.getAestheticType();
      String field = legendArea.getField();
      boolean isDimension = legendArea.isCategorical();
      boolean isTime = legendArea.isTime();
      LegendsDescriptor legendsDesc = descriptor.getLegendsDescriptor();
      boolean isNode = legendArea.isNodeAesthetic();
      LegendDescriptor legendDesc = GraphUtil.getLegendDescriptor(info, legendsDesc,
         field, targetFields, aestheticType, isNode);

      return new LegendFormatDialogModel(info, legendsDesc, legendDesc, chartArea,
         targetFields, aestheticType, field, titleName, isDimension, isTime, isNode);
   }

   public void updateLegendFormatDialogModel(LegendFormatDialogModel model,
      ChartInfo info, ChartArea chartArea, ChartDescriptor descriptor, int legendIdx)
   {
      LegendsArea legendsArea = chartArea.getLegendsArea();
      LegendArea legendArea = legendsArea.getLegendAreas()[legendIdx];
      List<String> targetFields = legendArea.getTargetFields();
      String titleName = legendArea.getVisualFrame() != null ?
         legendArea.getVisualFrame().getTitle() : null;
      String aestheticType = legendArea.getAestheticType();
      String field = legendArea.getField();
      LegendsDescriptor legendsDesc = descriptor.getLegendsDescriptor();
      updateLegendProperties(model, info, legendsDesc, field, targetFields, aestheticType,
                             titleName, model.isNode());

      // if node and line binds same field on color, the legend is shared so any change
      // should be applied to both. (61630)
      if(info instanceof RelationChartInfo) {
         updateLegendProperties(model, info, legendsDesc, field, targetFields, aestheticType,
                                titleName, !model.isNode());
      }
   }

   private static void updateLegendProperties(LegendFormatDialogModel model, ChartInfo info,
                                              LegendsDescriptor legendsDesc, String field,
                                              List<String> targetFields, String aestheticType,
                                              String titleName, boolean nodeAesthetics)
   {
      LegendDescriptor legendDesc = GraphUtil.getLegendDescriptor(
         info, legendsDesc, field, targetFields, aestheticType, nodeAesthetics);

      if(legendDesc != null) {
         model.updateLegendFormatDialogModel(info, legendsDesc, legendDesc, titleName);
      }
   }

   public void showAxis(AxisDescriptor axis) {
      axis.setLineVisible(true);
      axis.setLabelVisible(true);
   }

   /**
    * Gets the descriptor of the title.
    */
   public TitleDescriptor getTitleDescriptor(ChartDescriptor desc, String titleType) {
      TitlesDescriptor titlesDesc = desc.getTitlesDescriptor();
      TitleDescriptor titleDesc = null;

      if(ChartFormatConstants.X_TITLE.equals(titleType)) {
         titleDesc = titlesDesc.getXTitleDescriptor();
      }
      else if(ChartFormatConstants.X2_TITLE.equals(titleType)) {
         titleDesc = titlesDesc.getX2TitleDescriptor();
      }
      else if(ChartFormatConstants.Y_TITLE.equals(titleType)) {
         titleDesc = titlesDesc.getYTitleDescriptor();
      }
      else if(ChartFormatConstants.Y2_TITLE.equals(titleType)) {
         titleDesc = titlesDesc.getY2TitleDescriptor();
      }

      return titleDesc;
   }

   /**
    * Gets the title area.
    */
   public TitleArea getTitleArea(ChartArea chartArea, String titleType) {
      TitleArea titleArea = null;

      if(ChartFormatConstants.X_TITLE.equals(titleType)) {
         titleArea = chartArea.getXTitleArea();
      }
      else if(ChartFormatConstants.X2_TITLE.equals(titleType)) {
         titleArea = chartArea.getX2TitleArea();
      }
      else if(ChartFormatConstants.Y_TITLE.equals(titleType)) {
         titleArea = chartArea.getYTitleArea();
      }
      else if(ChartFormatConstants.Y2_TITLE.equals(titleType)) {
         titleArea = chartArea.getY2TitleArea();
      }

      return titleArea;
   }

   public DefaultArea getChartRegionArea(ChartArea chartArea, String areaType, int areaIdx) {
      if(ChartFormatConstants.LEGEND_CONTENT.equals(areaType) && chartArea != null) {
         LegendsArea legendsArea = chartArea.getLegendsArea();

         if(legendsArea != null) {
            return legendsArea.getLegendAreas()[areaIdx];
         }

         return null;
      }

      AxisArea axisArea = getAxisArea(chartArea, areaType);
      areaIdx = areaIdx == -1 ? 0 : areaIdx;

      if(axisArea != null && areaIdx < axisArea.getAllAreas().length) {
         return axisArea.getAllAreas()[areaIdx];
      }

      return null;
   }

   /**
    * Set alignment enabled information to format info model for different chart area.
    * @param area, the chartarea of the target chart.
    * @param areaType, the target area type.
    * @param areaIdx, the target area index(e.g. legend index, axis label index).
    * @param fmt, the formatinfo model which need to set alignment enabled information.
    */
   public void setFormatsEnabled(ChartArea area, String areaType, int areaIdx,
                                 FormatInfoModel fmt, ChartInfo cinfo)
   {
      setFormatsEnabled(area, areaType, areaIdx, fmt, cinfo, false);
   }

   /**
    * Set alignment enabled information to format info model for different chart area.
    * @param area, the chartarea of the target chart.
    * @param areaType, the target area type.
    * @param areaIdx, the target area index(e.g. legend index, axis label index).
    * @param fmt, the formatinfo model which need to set alignment enabled information.
    */
   public void setFormatsEnabled(ChartArea area, String areaType, int areaIdx,
      FormatInfoModel fmt, ChartInfo cinfo, boolean isTextField)
   {
      DefaultArea regionArea = getChartRegionArea(area, areaType, areaIdx);

      if(GraphTypes.isMekko(cinfo.getChartType()) &&
         ChartFormatConstants.LEFT_Y_AXIS.equals(areaType))
      {
         fmt.setFormatEnabled(false);
         fmt.setHAlignmentEnabled(false);
         fmt.setVAlignmentEnabled(false);
      }
      else if(regionArea instanceof LegendArea &&
         ((LegendArea) regionArea).getVisualFrame() instanceof LinearColorFrame)
      {
         fmt.setHAlignmentEnabled(false);
         fmt.setVAlignmentEnabled(false);
      }
      else if(ChartFormatConstants.LEGEND_TITLE.equals(areaType) ||
         ChartFormatConstants.LEGEND_CONTENT.equals(areaType) ||
         ChartFormatConstants.TEXT.equals(areaType) ||
         ChartFormatConstants.TEXT_FIELD.equals(areaType) ||
         ChartFormatConstants.X_TITLE.equals(areaType) ||
         ChartFormatConstants.X2_TITLE.equals(areaType))
      {
         fmt.setHAlignmentEnabled(true);
         fmt.setVAlignmentEnabled(false);
      }
      else if(ChartFormatConstants.Y_TITLE.equals(areaType) ||
         ChartFormatConstants.Y2_TITLE.equals(areaType))
      {
         fmt.setHAlignmentEnabled(false);
         fmt.setVAlignmentEnabled(true);
         fmt.getAlign().convertToValign();
      }
      else if(regionArea instanceof DimensionLabelArea) {
         DimensionLabelArea dimLabelArea = (DimensionLabelArea) regionArea;
         fmt.setHAlignmentEnabled(dimLabelArea.isHAlignmentEnabled());
         fmt.setVAlignmentEnabled(dimLabelArea.isVAlignmentEnabled());
      }
      else if(regionArea instanceof MeasureLabelsArea ||
         regionArea instanceof LegendArea &&
         ((LegendArea) regionArea).getVisualFrame() instanceof GradientColorFrame)
      {
         fmt.setHAlignmentEnabled(false);
         fmt.setVAlignmentEnabled(false);
      }
      else {
         fmt.setHAlignmentEnabled(false);
         fmt.setVAlignmentEnabled(false);
      }

      if(ChartFormatConstants.X_TITLE.equals(areaType) ||
         ChartFormatConstants.X2_TITLE.equals(areaType) ||
         ChartFormatConstants.Y_TITLE.equals(areaType) ||
         ChartFormatConstants.Y2_TITLE.equals(areaType) ||
         ChartFormatConstants.LEGEND_TITLE.equals(areaType) ||
         ChartFormatConstants.VO.equals(areaType) &&
            (cinfo.getChartType() != GraphTypes.CHART_CIRCLE_PACKING || !isTextField))
      {
         fmt.setFormatEnabled(false);
      }
   }

   /**
    * Get the bindable for getting aesthetic fields.
    */
   public ChartBindable getChartBindable(ChartInfo info, String aggr) {
      ChartRef ref = info.getFieldByName(aggr, false);

      if(ref == null && info instanceof VSChartInfo &&
         ((VSChartInfo) info).isAppliedDateComparison())
      {
         ref = info.getFieldByName(aggr, true);
      }

      if(ref instanceof ChartBindable) {
         return (ChartBindable) ref;
      }

      if(!info.isMultiAesthetic()) {
         return info;
      }

      List aggrs = AllChartAggregateRef.getXYAggregateRefs(info, false);
      return new AllChartAggregateRef(info, aggrs);
   }
}
