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
package inetsoft.web.graph.model.dialog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.graph.data.PairsDataSet;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.region.ChartArea;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Tool;

import java.awt.*;
import java.io.Serializable;
import java.util.List;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AxisPropertyDialogModel implements Serializable {
   public AxisPropertyDialogModel() {
   }

   public AxisPropertyDialogModel(ChartInfo cInfo, AxisDescriptor axisDesc,
      boolean isFacetGrid, ChartArea area, String columnName, boolean outer,
      boolean linear, String axisType, boolean maxMode)
   {
      this.linear = linear;
      this.outer = outer;
      this.aliasSupported = !(GraphTypes.isMekko(cInfo.getChartType()) &&
         "top_x_axis".equals(axisType));
      ChartRef ref = cInfo.getFieldByName(columnName, true);

      this.timeSeries = ref instanceof XDimensionRef && !outer &&
         ((XDimensionRef) ref).isTimeSeries() && GraphUtil.isTimeSeriesVisible(cInfo, ref);

      // create AxisLinePaneModel.
      axisLinePaneModel = new AxisLinePaneModel();
      axisLinePaneModel.setAxisType(axisType);

      boolean isShowAxisLine = maxMode ? axisDesc.isMaxModeLineVisible() : axisDesc.isLineVisible();
      boolean isShowAxisLineEnabled =
         GraphUtil.isShowAxisLineEnabled(cInfo, isFacetGrid, columnName, outer, axisType, area);
      axisLinePaneModel.setShowAxisLine(isShowAxisLine);
      axisLinePaneModel.setShowAxisLineEnabled(isShowAxisLineEnabled);
      axisLinePaneModel.setShowTicks(axisDesc.isTicksVisible());
      boolean lineColorEnabled = isLineColorEnabled(cInfo, columnName, axisType, outer,
                                                    isShowAxisLineEnabled, isFacetGrid);
      axisLinePaneModel.setLineColorEnabled(lineColorEnabled);
      axisLinePaneModel.setMinimum(axisDesc.getMinimum() == null ?
         "" : axisDesc.getMinimum() + "");
      axisLinePaneModel.setMaximum(axisDesc.getMaximum() == null ?
         "" : axisDesc.getMaximum() + "");
      axisLinePaneModel.setMinorIncrement(axisDesc.getMinorIncrement() == null ?
         "" : axisDesc.getMinorIncrement() + "");
      axisLinePaneModel.setIncrement(
         axisDesc.getIncrement() ==  null ? "" : axisDesc.getIncrement() + "");
      axisLinePaneModel.setFakeScale(GraphTypes.isMekko(cInfo.getChartType()) &&
                                        "left_y_axis".equals(axisType));

      if(axisDesc.getLineColor() != null) {
         axisLinePaneModel.setLineColor(
            "#" + Tool.colorToHTMLString(axisDesc.getLineColor()));
      }

      if(!linear) {
         axisLinePaneModel.setIgnoreNull(axisDesc.isNoNull());
         axisLinePaneModel.setTruncate(axisDesc.isTruncate());
      }
      else {
         axisLinePaneModel.setLogarithmicScale(axisDesc.isLogarithmicScale());
         axisLinePaneModel.setShared(axisDesc.isSharedRange());
         axisLinePaneModel.setReverse(axisDesc.isReversed());
      }

      boolean appliedDC = cInfo instanceof VSChartInfo && ((VSChartInfo) cInfo).isAppliedDateComparison();
      boolean showAxisLabelEnabled = isShowAxisLabelEnabled(cInfo, columnName, axisType, appliedDC);

      // create AxisLabelPaneModel.
      axisLabelPaneModel = new AxisLabelPaneModel();
      axisLabelPaneModel.setShowAxisLabel(
         maxMode ? axisDesc.isMaxModeLabelVisible() : axisDesc.isLabelVisible());
      axisLabelPaneModel.setShowAxisLabelEnabled(showAxisLabelEnabled);

      RotationRadioGroupModel rotationRadioGroupModel = new RotationRadioGroupModel();
      CompositeTextFormat textFormat;

      if(columnName != null) {
         textFormat = axisDesc.getColumnLabelTextFormat(columnName);

         if(textFormat == null) {
            textFormat = (CompositeTextFormat) axisDesc.getAxisLabelTextFormat().clone();
            axisDesc.setColumnLabelTextFormat(columnName, textFormat);
         }
      }
      else {
         textFormat = axisDesc.getAxisLabelTextFormat();
      }

      Number rotation = textFormat.getRotation();
      rotationRadioGroupModel.setRotation(rotation == null ? "auto" : rotation + "");
      axisLabelPaneModel.setRotationRadioGroupModel(rotationRadioGroupModel);

      // create AliasPaneModel.
      aliasPaneModel = new AliasPaneModel();
      String[][] axisItems = GraphUtil.getAxisItems(columnName, area);
      List<ModelAlias> aliasList = new ArrayList<>();
      Set added = new HashSet();

      for(String[] pair : axisItems) {
         if(pair == null || added.contains(pair[0])) {
            continue;
         }

         String item = pair[0];
         String alias = axisDesc.getLabelAlias(item == null ? "" : item);
         String text = pair[1];

         if(alias == null || "".equals(alias) || "null".equals(alias) && text != null) {
            alias = text == null ? "" : text;
         }

         added.add(item);
         aliasList.add(new ModelAlias(text, item, alias));
      }

      aliasPaneModel.setAliasList(aliasList.toArray(new ModelAlias[aliasList.size()]));
   }

   public static boolean isShowAxisLabelEnabled(ChartInfo cInfo, String columnName,
                                                String axisType, boolean appliedDC)
   {
      boolean showAxisLabelEnabled = true;
      ChartRef chartRef = cInfo.getFieldByName(columnName, appliedDC);

      if(chartRef == null && !("_Parallel_Label_".equals(columnName) ||
         PairsDataSet.XMEASURE_NAME.equals(columnName) || PairsDataSet.YMEASURE_NAME.equals(columnName) ||
         (GraphTypes.isMekko(cInfo.getChartType()) && "left_y_axis".equals(axisType))))
      {
         showAxisLabelEnabled = false;
      }
      return showAxisLabelEnabled;
   }

   public static boolean isLineColorEnabled(ChartInfo cInfo, String columnName, String axisType,
                                            boolean outer, boolean axisLineEnabled, boolean facetGrid)
   {
      boolean innerXFacet = false;

      for(int i = cInfo.getXFieldCount() - 1; i >= 0; i--) {
         if(GraphUtil.isDimension(cInfo.getXField(i))) {
            innerXFacet = cInfo.getXField(i).getFullName().equals(columnName);
            break;
         }
      }

      // y2 could be on top in rotated chart.
      boolean measure = !GraphUtil.isDimension(cInfo.getFieldByName(columnName, true));
      // top axis line color determined by the bottom (primary) x axis, so it's color
      // should not be enabled. (54651)
      boolean topFacetX = "top_x_axis".equals(axisType) || "x".equals(axisType) && outer;
      boolean lineColorEnabled = (!topFacetX && axisLineEnabled || facetGrid || measure) ||
          !GraphTypes.isRect(cInfo.getRTChartType()) && innerXFacet ||
          GraphTypes.isMekko(cInfo.getRTChartType()) ||
          GraphTypes.isFunnel(cInfo.getRTChartType());
      return lineColorEnabled;
   }

   public void updateAxisPropertyDialogModel(AxisDescriptor axisDesc, String columnName,
                                             String axisType, boolean maxMode)
   {
      CompositeTextFormat cfmt = null;

      if(columnName != null && !"parallel".equals(axisType)) {
         cfmt = axisDesc.getColumnLabelTextFormat(columnName);

         if(cfmt == null) {
            cfmt = (CompositeTextFormat) axisDesc.getAxisLabelTextFormat().clone();
            axisDesc.setColumnLabelTextFormat(columnName, cfmt);
         }
      }
      else {
         cfmt = axisDesc.getAxisLabelTextFormat();
      }

      if(maxMode) {
         axisDesc.setMaxModeLineVisible(axisLinePaneModel.isShowAxisLine());
      }
      else {
         axisDesc.setLineVisible(axisLinePaneModel.isShowAxisLine());
      }

      Color color = Tool.getColorFromHexString(axisLinePaneModel.getLineColor());
      axisDesc.setLineColor(color);
      axisDesc.setTicksVisible(axisLinePaneModel.isShowTicks());

      if(this.linear || this.timeSeries && !this.outer) {
         String increment = axisLinePaneModel.getIncrement();
         axisDesc.setIncrement(increment == null || "".equals(increment) ? null
                               : Float.valueOf(increment).floatValue());
      }

      if(this.linear) {
         String maximum = axisLinePaneModel.getMaximum();
         String minimum = axisLinePaneModel.getMinimum();
         String minorIncrement = axisLinePaneModel.getMinorIncrement();

         axisDesc.setMaximum(maximum == null || "".equals(maximum)
                             ? null : Float.valueOf(maximum).floatValue());
         axisDesc.setMinimum(minimum == null || "".equals(minimum)
                             ? null : Float.valueOf(minimum).floatValue());
         axisDesc.setMinorIncrement(minorIncrement == null || "".equals(minorIncrement)
                                    ? null : Float.parseFloat(minorIncrement));
         axisDesc.setLogarithmicScale(axisLinePaneModel.isLogarithmicScale());
         axisDesc.setReversed(axisLinePaneModel.isReverse());
         axisDesc.setSharedRange(axisLinePaneModel.isShared());
      }
      else {
         axisDesc.setNoNull(axisLinePaneModel.isIgnoreNull());
         axisDesc.setTruncate(axisLinePaneModel.isTruncate());
      }

      if(maxMode) {
         axisDesc.setMaxModeLabelVisible(axisLabelPaneModel.isShowAxisLabel());
      }
      else {
         axisDesc.setLabelVisible(axisLabelPaneModel.isShowAxisLabel());
      }

      RotationRadioGroupModel rotationRadioGroupModel =
         axisLabelPaneModel.getRotationRadioGroupModel();
      String rotation = rotationRadioGroupModel.getRotation();
      CompositeTextFormat labelFormat = axisDesc.getColumnLabelTextFormat(columnName);

      if("auto".equals(rotation)) {
         if(null != cfmt.getUserDefinedFormat().getRotation()){
            cfmt.getUserDefinedFormat().setRotation(null);
            axisDesc.getAxisLabelTextFormat().getUserDefinedFormat().setRotation(null);

            if(labelFormat != null) {
               labelFormat.getUserDefinedFormat().setRotation(null);
            }
         }
      }
      else if(!Tool.equals(cfmt.getRotation(), Float.parseFloat(rotation))) {
         cfmt.getUserDefinedFormat().setRotation(Float.parseFloat(rotation));
         axisDesc.getAxisLabelTextFormat().getUserDefinedFormat()
            .setRotation(Float.parseFloat(rotation));

         if(labelFormat != null) {
            labelFormat.getUserDefinedFormat().setRotation(Float.parseFloat(rotation));
         }
      }

      ModelAlias[] aliasList = aliasPaneModel.getAliasList();

      for(int i = 0; i < aliasList.length; i++) {
         ModelAlias item = aliasList[i];
         String alias = item.getAlias().trim();
         String label = item.getLabel();
         String value = item.getValue();

         alias = (alias == null || alias.length() == 0 || Tool.equals(label, alias)) ? null : alias;
         axisDesc.setLabelAlias(value, alias);
      }
   }

   public AxisLinePaneModel getAxisLinePaneModel() {
      return axisLinePaneModel;
   }

   public void setAxisLinePaneModel(AxisLinePaneModel axisLinePaneModel) {
      this.axisLinePaneModel = axisLinePaneModel;
   }

   public AxisLabelPaneModel getAxisLabelPaneModel() {
      return axisLabelPaneModel;
   }

   public void setAxisLabelPaneModel(AxisLabelPaneModel axisLabelPaneModel) {
      this.axisLabelPaneModel = axisLabelPaneModel;
   }

   public AliasPaneModel getAliasPaneModel() {
      return aliasPaneModel;
   }

   public void setAliasPaneModel(AliasPaneModel aliasPaneModel) {
      this.aliasPaneModel = aliasPaneModel;
   }


   public boolean getLinear() {
      return this.linear;
   }

   public void setLinear(boolean linear) {
      this.linear = linear;
   }

   public boolean getTimeSeries() {
      return this.timeSeries;
   }

   public void setTimeSeries(boolean timeSeries) {
      this.timeSeries = timeSeries;
   }

   public boolean getOuter() {
      return this.outer;
   }

   public void setOuter(boolean outer) {
      this.outer = outer;
   }

   public boolean isAliasSupported() {
      return aliasSupported;
   }

   public void setAliasSupported(boolean aliasSupported) {
      this.aliasSupported = aliasSupported;
   }

   public int getIndexByName(String[] arr, String name) {
      int index = 0;

      for(int i = 0; i < arr.length; i++) {
         if(name.equals(arr[i])) {
            index = i;
         }
      }

      return index;
   }

   private AxisLinePaneModel axisLinePaneModel;
   private AxisLabelPaneModel axisLabelPaneModel;
   private AliasPaneModel aliasPaneModel;
   private boolean timeSeries;
   private boolean linear;
   private boolean outer;
   private boolean aliasSupported;
}
