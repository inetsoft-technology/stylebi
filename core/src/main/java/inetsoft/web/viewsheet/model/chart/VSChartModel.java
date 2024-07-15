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
package inetsoft.web.viewsheet.model.chart;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.MessageException;
import inetsoft.web.adhoc.model.FormatInfoModel;
import inetsoft.web.graph.GraphBuilder;
import inetsoft.web.graph.model.*;
import inetsoft.web.viewsheet.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.geom.RectangularShape;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VSChartModel extends VSObjectModel<ChartVSAssembly> implements ChartModel {
   public VSChartModel(ChartVSAssembly assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);

      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) assembly.getInfo();
      TableDataPath titlePath = new TableDataPath(-1, TableDataPath.TITLE);
      VSCompositeFormat compositeFormat = info.getFormatInfo().getFormat(titlePath, false);

      int titleHeight = info.getTitleHeight();

      this.titleFormat = new VSFormatModel(compositeFormat, info);
      this.titleFormat.setPositions(new Point(0, 0),
         new Dimension((int) this.getObjectFormat().getWidth(), titleHeight));

      this.titleVisible = info.isTitleVisible();
      this.title = info.getTitle();

      this.showValues = info.getChartDescriptor().getPlotDescriptor().isValuesVisible();
      this.empty = info.getTableName() == null;
      //Edited only through wizard
      this.editedByWizard = info.isEditedByWizard();
      this.paddingTop = info.getPadding().top;
      this.paddingLeft = info.getPadding().left;
      this.paddingBottom = info.getPadding().bottom;
      this.paddingRight = info.getPadding().right;
      setHasDynamic(containsDynamic(assembly));
      this.isWordCloud = GraphTypeUtil.isWordCloud(assembly.getVSChartInfo());
      this.scatterMatrix = GraphTypeUtil.isScatterMatrix(assembly.getVSChartInfo());
      this.noData = info.isNoData();
      this.summarySortCol = info.getSummarySortCol();
      this.summarySortVal = info.getSummarySortVal();
   }

   private boolean containsDynamic(ChartVSAssembly assembly) {
      return assembly.getVSChartInfo().getDynamicValues().stream()
         .anyMatch(VSUtil::isDynamic);
   }

   @Override
   public int getChartType() {
      return chartType;
   }

   @Override
   public void setChartType(int type) {
      this.chartType = type;
   }

   @Override
   public List<Axis> getAxes() {
      return axes;
   }

   @Override
   public void setAxes(List<Axis> axes) {
      this.axes = axes;
   }

   @Override
   public List<Facet> getFacets() {
      return facets;
   }

   @Override
   public void setFacets(List<Facet> facets) {
      this.facets = facets;
   }

   @Override
   public List<LegendContainer> getLegends() {
      return legends;
   }

   @Override
   public void setLegends(List<LegendContainer> legends) {
      this.legends = legends;
   }

   @Override
   public Plot getPlot() {
      return plot;
   }

   @Override
   public void setPlot(Plot plot) {
      this.plot = plot;
   }

   @Override
   public List<Title> getTitles() {
      return titles;
   }

   @Override
   public void setTitles(List<Title> titles) {
      this.titles = titles;
   }

   @Override
   public ArrayList<String> getStringDictionary() {
      return stringDictionary;
   }

   @Override
   public void setStringDictionary(ArrayList<String> stringDictionary) {
      this.stringDictionary = stringDictionary;
   }

   @Override
   public boolean isAxisHidden() {
      return axisHidden;
   }

   @Override
   public void setAxisHidden(boolean axisHidden) {
      this.axisHidden = axisHidden;
   }

   @Override
   public boolean isTitleHidden() {
      return titleHidden;
   }

   @Override
   public void setTitleHidden(boolean titleHidden) {
      this.titleHidden = titleHidden;
   }

   @Override
   public boolean isLegendHidden() {
      return legendHidden;
   }

   @Override
   public void setLegendHidden(boolean legendHidden) {
      this.legendHidden = legendHidden;
   }

   @Override
   public int getLegendOption() {
      return legendOption;
   }

   @Override
   public void setLegendOption(int legendOption) {
      this.legendOption = legendOption;
   }

   @Override
   public boolean isMaxMode() {
      return maxMode;
   }

   @Override
   public void setMaxMode(boolean maxMode) {
      this.maxMode = maxMode;
   }

   @Override
   public boolean isShowValues() {
      return showValues;
   }

   @Override
   public void setShowValues(boolean showValues) {
      this.showValues = showValues;
   }

   @Override
   public RectangularShape getLegendsBounds() {
      return this.legendsBounds;
   }

   @Override
   public void setLegendsBounds(RectangularShape bounds) {
      this.legendsBounds = bounds;
   }

   @Override
   public RectangularShape getContentBounds() {
      return contentBounds;
   }

   @Override
   public void setContentBounds(RectangularShape contentBounds) {
      this.contentBounds = contentBounds;
   }

   public boolean isBrushed() {
      return brushed;
   }

   public void setBrushed(boolean brushed) {
      this.brushed = brushed;
   }

   public boolean isZoomed() {
      return zoomed;
   }

   public void setZoomed(boolean zoomed) {
      this.zoomed = zoomed;
   }

   public boolean isHasFlyovers() {
      return hasFlyovers;
   }

   public boolean isFlyOnClick() {
      return flyOnClick;
   }

   public void setHasFlyovers(boolean hasFlyovers) {
      this.hasFlyovers = hasFlyovers;
   }

   public boolean isPlotHighlightEnabled() {
      return plotHighlightEnabled;
   }

   public void setPlotHighlightEnabled(boolean plotHighlightEnabled) {
      this.plotHighlightEnabled = plotHighlightEnabled;
   }

   public boolean isPlotFilterEnabled() {
      return plotFilterEnabled;
   }

   public void setPlotFilterEnabled(boolean plotFilterEnabled) {
      this.plotFilterEnabled = plotFilterEnabled;
   }

   @Override
   public boolean isMapInfo() {
      return mapInfo;
   }

   @Override
   public void setMapInfo(boolean mapInfo) {
      this.mapInfo = mapInfo;
   }

   /**
    * Check if multistyle.
    */
   public boolean isMultiStyles() {
      return multiStyles;
   }

   /**
    * Set if multistyle.
    */
   public void setMultiStyles(boolean multi) {
      this.multiStyles = multi;
   }

   public boolean isEnableAdhoc() { return enableAdhoc; }

   public boolean isShowResizePreview() {
      return showResizePreview;
   }

   public VSFormatModel getTitleFormat() {
      return titleFormat;
   }

   public boolean isTitleVisible() {
      return titleVisible;
   }

   public String getTitle() {
      return title;
   }

   public void setTitleFormat(VSFormatModel titleFormat) {
      this.titleFormat = titleFormat;
   }

   public void setTitleVisible(boolean titleVisible) {
      this.titleVisible = titleVisible;
   }

   public void setTitle(String title) {
      this.title = title;
   }

   public void setInvalid(boolean invalid) {
      this.invalid = invalid;
   }

   public boolean isInvalid() {
      return this.invalid;
   }

   public boolean isEmpty() {
      return empty;
   }

   public void setEmpty(boolean empty) {
      this.empty = empty;
   }

   @Override
   public boolean isChangedByScript() {
      return changedByScript;
   }

   @Override
   public void setChangedByScript(boolean changedByScript) {
      this.changedByScript = changedByScript;
   }

   @Override
   public boolean isHasLegend() {
      return hasLegend;
   }

   @Override
   public void setHasLegend(boolean hasLegend) {
      this.hasLegend = hasLegend;
   }

   public boolean isEditedByWizard() {
      return editedByWizard;
   }

   public void setEditedByWizard(boolean editedByWizard) {
      this.editedByWizard = editedByWizard;
   }

   public int getPaddingTop() {
      return paddingTop;
   }

   public int getPaddingLeft() {
      return paddingLeft;
   }

   public int getPaddingBottom() {
      return paddingBottom;
   }

   public int getPaddingRight() {
      return paddingRight;
   }

   public List<String> getAxisFields() {
      return axisFields;
   }

   public void setAxisFields(List<String> axisFields) {
      this.axisFields = axisFields;
   }

   @Override
   public ArrayList<RegionMeta> getRegionMetaDictionary() {
      return regionMetaDictionary;
   }

   public boolean isWordCloud() {
      return isWordCloud;
   }

   @Override
   public boolean isScatterMatrix() {
      return scatterMatrix;
   }

   @Override
   public void setScatterMatrix(boolean scatterMatrix) {
      this.scatterMatrix = scatterMatrix;
   }

   @Override
   public boolean isWebMap() {
      return webMap;
   }

   @Override
   public void setWebMap(boolean webMap) {
      this.webMap = webMap;
   }

   @Override
   public boolean isNavEnabled() {
      return navEnabled;
   }

   @Override
   public void setNavEnabled(boolean navEnabled) {
      this.navEnabled = navEnabled;
   }

   public boolean isDateComparisonEnabled() {
      return dateComparisonEnabled;
   }

   public void setDateComparisonEnabled(boolean dateComparisonEnabled) {
      this.dateComparisonEnabled = dateComparisonEnabled;
   }

   public boolean isDateComparisonDefined() {
      return dateComparisonDefined;
   }

   public void setDateComparisonDefined(boolean dateComparisonDefined) {
      this.dateComparisonDefined = dateComparisonDefined;
   }

   public boolean isCustomPeriod() {
      return customPeriod;
   }

   public void setCustomPeriod(boolean customPeriod) {
      this.customPeriod = customPeriod;
   }

   public String getDateComparisonDescription() {
      return dateComparisonDescription;
   }

   public void setDateComparisonDescription(String dateComparisonDescription) {
      this.dateComparisonDescription = dateComparisonDescription;
   }

   @Override
   public boolean isNoData() {
      return noData;
   }

   @Override
   public void setNoData(boolean noData) {
      this.noData = noData;
   }

   public int getSummarySortCol() {
      return summarySortCol;
   }

   public void setSummarySortCol(int sortCol) {
      this.summarySortCol = sortCol;
   }

   public int getSummarySortVal() {
      return summarySortVal;
   }

   public void setSummarySortVal(int sortVal) {
      this.summarySortVal = sortVal;
   }

   public boolean isAppliedDateComparison() {
      return appliedDateComparison;
   }

   public void setAppliedDateComparison(boolean appliedDateComparison) {
      this.appliedDateComparison = appliedDateComparison;
   }

   public FormatInfoModel getErrorFormat() {
      return errorFormat;
   }

   public void setErrorFormat(FormatInfoModel errorFormat) {
      this.errorFormat = errorFormat;
   }

   private int chartType = GraphTypes.CHART_AUTO;
   private List<Axis> axes = new ArrayList<>();
   private List<Facet> facets = new ArrayList<>();
   private List<LegendContainer> legends = new ArrayList<>();
   private Plot plot;
   private List<Title> titles = new ArrayList<>();
   private ArrayList<String> stringDictionary = new ArrayList<>();
   private ArrayList<RegionMeta> regionMetaDictionary = new ArrayList<>();
   private boolean multiStyles = false;
   private boolean axisHidden = false;
   private boolean titleHidden = false;
   private boolean legendHidden = false;
   private int legendOption = LegendsDescriptor.NO_LEGEND;
   private boolean maxMode = false;
   private boolean showValues = false;
   private boolean brushed = false;
   private boolean zoomed = false;
   private boolean hasFlyovers = false;
   private boolean flyOnClick = false;
   private boolean plotHighlightEnabled = false;
   private boolean plotFilterEnabled = false;
   private boolean mapInfo = false;
   private boolean enableAdhoc = false;
   private RectangularShape legendsBounds;
   private RectangularShape contentBounds;
   private boolean showResizePreview;
   private VSFormatModel titleFormat;
   private boolean titleVisible;
   private boolean invalid;
   private String title;
   private boolean empty;
   private boolean changedByScript;
   private boolean editedByWizard = true; //Not edited by binding
   private int paddingTop, paddingLeft, paddingBottom, paddingRight;
   private boolean hasLegend;
   private List<String> axisFields = new ArrayList<>();
   private static final Logger LOG = LoggerFactory.getLogger(VSChartModel.class);
   private boolean isWordCloud;
   private boolean scatterMatrix;
   private boolean webMap;
   private boolean navEnabled = true;
   private boolean dateComparisonEnabled;
   private boolean dateComparisonDefined;
   private boolean appliedDateComparison;
   private boolean customPeriod;
   private String dateComparisonDescription;
   private boolean noData;
   private int summarySortCol;
   private int summarySortVal;
   private FormatInfoModel errorFormat;

   @Component
   public static final class VSChartModelFactory
      extends VSObjectModelFactory<ChartVSAssembly, VSChartModel>
   {
      public VSChartModelFactory() {
         super(ChartVSAssembly.class);
      }

      @Override
      public VSChartModel createModel(ChartVSAssembly assembly, RuntimeViewsheet rvs) {
         final VSChartInfo cinfo = assembly.getVSChartInfo();
         final ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) assembly.getInfo();
         final Dimension maxSize = info.getMaxSize();

         final ViewsheetSandbox box = rvs.getViewsheetSandbox();

         if(box == null) {
            return null;
         }

         final Viewsheet viewsheet =
            assembly.isEmbedded() ? info.getViewsheet() : box.getViewsheet();

         if(viewsheet == null) {
            return null;
         }

         cinfo.setLocalMap(VSUtil.getLocalMap(viewsheet, assembly.getName()));
         final VSChartModel model = new VSChartModel(assembly, rvs);
         model.setInvalid(true);

         if(maxSize != null) {
            final VSFormatModel objectFormat = model.getObjectFormat();
            objectFormat.setPositions(new Point(0, 0), maxSize);
            objectFormat.setzIndex(info.getMaxModeZIndex());
            model.maxMode = true;
         }

         try {
            final int chartType = cinfo.getRTChartType();
            model.showResizePreview = GraphTypes.supportResizePreview(chartType);
            model.enableAdhoc = cinfo.isAdhocEnabled();
            model.brushed = assembly.containsBrushSelection();

            VSSelection zoomSelection = info.getZoomSelection();
            VSSelection excludeSelection = info.getExcludeSelection();
            model.zoomed = zoomSelection != null && zoomSelection.getPointCount() > 0 ||
               excludeSelection != null && excludeSelection.getPointCount() > 0;
            String[] flyovers =
               VSUtil.getValidFlyovers(info.getFlyoverViews(), assembly.getViewsheet());
            model.hasFlyovers = flyovers != null && flyovers.length > 0;
            model.flyOnClick = info.isFlyOnClick();
            model.plotHighlightEnabled = !GraphTypes.isRadarN(cinfo);
            model.mapInfo = cinfo instanceof VSMapInfo;
            model.setInvalid(!GraphUtil.isValidChart(cinfo));
         }
         catch(MessageException | ConfirmException e) {
            throw e;
         }
         catch(Exception ex) {
            // @ankitmathur, For Feature #1597, If the chart cannot be properly
            // rendered due to a user binding error, prevent the chart from getting
            // completely removed from the Viewsheet and send back the model in it's
            // current state.
            LOG.error("Failed to properly render chart", ex);
            return model;
         }

         ChartDescriptor desc = info.getChartDescriptor();
         VSChartModel chartModel =
            (VSChartModel) new GraphBuilder(assembly, cinfo, null, desc, model).build();
         model.setAxisFields(Arrays.stream(cinfo.getBindingRefs(false))
                                .map(a -> a.getFullName()).collect(Collectors.toList()));

         return chartModel;
      }
   }
}
