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
package inetsoft.web.graph;

import com.goebl.simplify.PointExtractor;
import com.goebl.simplify.Simplify;
import inetsoft.analytic.composition.VSCSSUtil;
import inetsoft.graph.*;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.GeoCoord;
import inetsoft.graph.data.BoxDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.geometry.Geometry;
import inetsoft.graph.geometry.ParaboxPointGeometry;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.visual.*;
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.region.TextArea;
import inetsoft.report.composition.region.*;
import inetsoft.report.internal.*;
import inetsoft.uql.XCube;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.adhoc.model.chart.ChartFormatAttr;
import inetsoft.web.binding.model.BaseFormatModel;
import inetsoft.web.composer.model.vs.HyperlinkModel;
import inetsoft.web.graph.model.*;
import inetsoft.web.viewsheet.handler.BaseDrillHandler;
import inetsoft.web.viewsheet.model.chart.VSChartModel;
import inetsoft.web.viewsheet.model.table.DrillLevel;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.awt.*;
import java.awt.geom.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class GraphBuilder {

   public GraphBuilder(ChartVSAssembly assembly,
                       ChartInfo cinfo,
                       ChartArea chartArea,
                       ChartDescriptor desc,
                       ChartModel model)
   {
      this.assembly = assembly;
      this.cinfo = cinfo;
      this.chartArea = chartArea;
      this.chartDesc = desc;
      this.model = model;
   }

   public ChartModel build() {
      // chartArea is null when adding new chart to composer
      if(chartArea != null) {
         // Create all chart objects if they exist
         this.model.setPlot(
            (Plot) createChartObject(chartArea.getPlotArea(), "plot_area").orElse(null));
         createChartObject(chartArea.getXTitleArea(), "x_title")
            .ifPresent(chartObject -> this.model.getTitles().add((Title) chartObject));
         createChartObject(chartArea.getYTitleArea(), "y_title")
            .ifPresent(chartObject -> this.model.getTitles().add((Title) chartObject));
         createChartObject(chartArea.getX2TitleArea(), "x2_title")
            .ifPresent(chartObject -> this.model.getTitles().add((Title) chartObject));
         createChartObject(chartArea.getY2TitleArea(), "y2_title")
            .ifPresent(chartObject -> this.model.getTitles().add((Title) chartObject));
         createChartObject(chartArea.getBottomXAxisArea(), "bottom_x_axis")
            .ifPresent(chartObject -> this.model.getAxes().add((Axis) chartObject));
         createChartObject(chartArea.getTopXAxisArea(), "top_x_axis")
            .ifPresent(chartObject -> this.model.getAxes().add((Axis) chartObject));
         createChartObject(chartArea.getLeftYAxisArea(), "left_y_axis")
            .ifPresent(chartObject -> this.model.getAxes().add((Axis) chartObject));
         createChartObject(chartArea.getRightYAxisArea(), "right_y_axis")
            .ifPresent(chartObject -> this.model.getAxes().add((Axis) chartObject));
         createChartObject(chartArea.getFacetTLArea(), "facetTL")
            .ifPresent(chartObject -> this.model.getFacets().add((Facet) chartObject));
         createChartObject(chartArea.getFacetTRArea(), "facetTR")
            .ifPresent(chartObject -> this.model.getFacets().add((Facet) chartObject));
         createChartObject(chartArea.getFacetBLArea(), "facetBL")
            .ifPresent(chartObject -> this.model.getFacets().add((Facet) chartObject));
         createChartObject(chartArea.getFacetBRArea(), "facetBR")
            .ifPresent(chartObject -> this.model.getFacets().add((Facet) chartObject));

         // Create legends
         LegendsArea legendsArea = chartArea.getLegendsArea();

         if(legendsArea != null) {
            for(LegendArea legendArea : legendsArea.getLegendAreas()) {
               LegendContainer legendContainer = createLegendContainer(legendArea);
               this.model.getLegends().add(legendContainer);
            }

            // Legend Position
            this.model.setLegendOption(getLegendOption());
            this.model.setLegendsBounds(legendsArea.getRegion().getBounds());
         }

         // get content area bounds, used for some calculations on front end resizing
         ContentArea contentArea = chartArea.getContentArea();

         if(contentArea != null && contentArea.getRegion() != null) {
            this.model.setContentBounds(contentArea.getRegion().getBounds());
         }

         this.model.setChangedByScript(chartArea.isChangedByScript());
      }

      // Certain areas may be hidden
      this.model.setAxisHidden(isAxisHidden());
      this.model.setTitleHidden(isTitleHidden());
      this.model.setHasLegend(hasLegend(cinfo));
      this.model.setLegendHidden(isLegendHidden());
      this.model.setGenTime(System.currentTimeMillis());
      this.model.setMultiStyles(cinfo.isMultiStyles());
      this.model.setShowValues(isShowValues());

      int chartType = cinfo.getChartType();

      // for multi-style, the chart type of the cinfo is meaningless and we should
      // use the actual aggregate chart type (this is needed for donut for selection region)
      if(cinfo.isMultiStyles() && chartType == 0) {
         chartType = Arrays.stream(cinfo.getBindingRefs(true))
            .filter(a -> a instanceof ChartAggregateRef)
            .map(a -> ((ChartAggregateRef) a).getRTChartType())
            .findFirst()
            .orElse(0);
      }

      this.model.setChartType(chartType);
      this.model.setPlotHighlightEnabled(
         !(GraphTypes.isRadarN(cinfo) ||
            GraphTypes.isContour(chartType) ||
            GraphTypes.isFunnel(chartType) && cinfo.getXFieldCount() == 0 ||
            GraphTypes.isMekko(chartType) && cinfo.getYFieldCount() == 0));
      this.model.setWebMap(chartDesc.getPlotDescriptor().isWebMap());
      this.model.setScatterMatrix(GraphTypeUtil.isScatterMatrix(cinfo));
      this.model.setMapInfo(cinfo instanceof MapInfo);

      PlotDescriptor plot = chartDesc.getPlotDescriptor();
      ChartDescriptor rtDesc =
         assembly == null ? null : assembly.getChartInfo().getRTChartDescriptor();

      if(rtDesc != null) {
         PlotDescriptor rtPlot = rtDesc.getPlotDescriptor();
         // don't enable nav bar if pan/zoom changed by script.
         this.model.setNavEnabled(plot.getPanX() == rtPlot.getPanX() &&
                                     plot.getPanY() == rtPlot.getPanY() &&
                                     plot.getZoom() == rtPlot.getZoom() &&
                                     plot.getZoomLevel() == rtPlot.getZoomLevel());
      }

      if(this.model instanceof VSChartModel) {
         VSChartModel vmodel = (VSChartModel) this.model;

         vmodel.setPlotFilterEnabled(isPlotFilterEnabled());
         vmodel.setDateComparisonEnabled(assembly.getChartInfo().isDateComparisonEnabled());
         vmodel.setDateComparisonDefined(
            DateComparisonUtil.isDateComparisonDefined(assembly.getChartInfo()));
         vmodel.setDateComparisonDescription(
            DateComparisonUtil.getDateComparisonDescription(assembly.getChartInfo()));
         vmodel.setAppliedDateComparison(((VSChartInfo) cinfo).isAppliedDateComparison());
         DateComparisonInfo dcInfo = assembly.getChartInfo().getDateComparisonInfo();
         vmodel.setCustomPeriod(dcInfo != null && dcInfo.getPeriods() instanceof CustomPeriods);
      }

      CompositeTextFormat compositeErrorFormat = plot.getErrorFormat();

      // model is sent before chart starts generating so the parent params will not be set
      // initially for this format so set them here
      if(assembly != null && (compositeErrorFormat.getCSSFormat().getParentCSSParams() == null ||
         compositeErrorFormat.getCSSFormat().getParentCSSParams().isEmpty()))
      {
         compositeErrorFormat.getCSSFormat().setParentCSSParams(
            assembly.getChartInfo().getCssParentParameters());
      }

      model.setErrorFormat(new ChartFormatAttr(compositeErrorFormat));

      return this.model;
   }

   private boolean isPlotFilterEnabled() {
      int type = cinfo.getChartType();

      if(GraphTypes.isStock(type) || GraphTypes.isCandle(type) ||
         GraphTypes.isBoxplot(type) || GraphTypes.isMekko(type) ||
         GraphTypes.isMap(type))
      {
         return false;
      }

      if(GraphTypes.isRadar(type)) {
         ChartRef[] yfields = cinfo.getRTYFields();
         ChartRef[] gfields = cinfo.getGroupFields();

         return yfields != null && yfields.length == 1 && gfields != null && gfields.length == 1;
      }

      return true;
   }

   public static XCube getCube(DataVSAssembly assembly) {
      if(assembly == null) {
         return null;
      }

      XCube cube = null;

      if(assembly instanceof CubeVSAssembly) {
         cube = ((CubeVSAssembly) assembly).getXCube();
      }

      if(cube == null) {
         SourceInfo src = assembly.getSourceInfo();

         if(src == null) {
            return null;
         }

         cube = AssetUtil.getCube(src.getPrefix(), src.getSource());
      }

      return cube;
   }

   public static XCube getCube(DataVSAssembly assembly, XCube cube) {
      if(cube == null) {
         SourceInfo src = assembly.getSourceInfo();

         if(src == null) {
            return null;
         }

         cube = AssetUtil.getCube(src.getPrefix(), src.getSource());
      }

      return cube;
   }

   private static List<DrillLevel> getDrillLevels(ChartVSAssembly assembly, AxisArea axis) {
      XCube cube = getCube(assembly);
      List<DrillLevel> drillLevels = new ArrayList<>();
      String[] fields = axis.getAxisField();

      for(String field : fields) {
         VSChartInfo info = assembly.getVSChartInfo();
         ChartRef ref = info.getFieldByName(field, true);
         boolean isPeriod = info.isPeriodRef(field);
         drillLevels.add(isPeriod ? DrillLevel.None : VSUtil.getDrillLevel(ref, cube));
      }

      return drillLevels;
   }

   /**
    * @return if the chart has legend.
    */
   private boolean hasLegend(ChartInfo cinfo) {
      if(hasAesthetic(cinfo)) {
         return true;
      }

      int type = cinfo.getRTChartType();

      if(GraphTypes.isTreemap(type) || GraphTypes.isMap(type) || GraphTypes.isStock(type)) {
         return false;
      }

      if(GraphTypes.isRadar(type)) {
         return this.isRadarChartHasLegend(cinfo);
      }

      if(GraphTypes.isPoint(type)) {
         return this.isPointChartHasLegend(cinfo);
      }

      if(cinfo instanceof RelationChartInfo && isRelationChartHasLegend((RelationChartInfo) cinfo)) {
         return true;
      }

      ChartRef[][] arr = {cinfo.getRTXFields(), cinfo.getRTYFields()};
      Set<Object> aggrs = new HashSet<>();

      for(int i = 0; i < arr.length; i++) {
         for(int j = 0; j < arr[i].length; j++) {
            if(arr[i][j] instanceof ChartAggregateRef) {
               aggrs.add(arr[i][j]);
            }

            if(aggrs.size() > 1) {
               return true;
            }
         }
      }

      if(cinfo.isMultiAesthetic()) {
         for(ChartAggregateRef ref : cinfo.getAestheticAggregateRefs(true)) {
            if(hasAesthetic(ref)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Check whether the radar has legend.
    * @return  is radar chart and is point line and have multi shape on aggs will be true.
    */
   private boolean isRadarChartHasLegend(ChartInfo cinfo) {
      VSDataRef[] aggs = cinfo.getAggregateRefs();
      int type = cinfo.getRTChartType();

      if(GraphTypes.isRadar(type) && aggs != null && chartDesc.getPlotDescriptor() != null &&
         chartDesc.getPlotDescriptor().isPointLine())
      {
         List<ShapeFrame> frames = new ArrayList<>();

         for(VSDataRef agg : aggs) {
            if(!(agg instanceof ChartAggregateRef)) {
               continue;
            }

            ShapeFrame shapeFrame = ((ChartAggregateRef) agg).getShapeFrame();

            if(!(shapeFrame instanceof StaticShapeFrame)) {
               continue;
            }

            StaticShapeFrame staticShapeFrame = (StaticShapeFrame) shapeFrame;

            boolean multi = frames.stream()
               .filter(frame -> frame instanceof StaticShapeFrame)
               .map(frame -> (StaticShapeFrame) frame)
               .anyMatch(frame -> !Tool.equals(frame.getShape(), staticShapeFrame.getShape()));

            if(multi) {
               return true;
            }

            frames.add(staticShapeFrame);
         }
      }

      return false;
   }

   /**
    * Check whether the point chart has legend.
    */
   private boolean isPointChartHasLegend(ChartInfo cinfo) {
      int type = cinfo.getRTChartType();
      VSDataRef[] aggs = cinfo.getAggregateRefs();

      if(!GraphTypes.isPoint(type)) {
         return false;
      }

      if(aggs != null && chartDesc.getPlotDescriptor() != null) {
         List<ShapeFrame> frames = new ArrayList<>();

         for(VSDataRef agg : aggs) {
            if(!(agg instanceof ChartAggregateRef)) {
               continue;
            }

            ShapeFrame shapeFrame = ((ChartAggregateRef) agg).getShapeFrame();

            if(!(shapeFrame instanceof StaticShapeFrame)) {
               continue;
            }

            StaticShapeFrame staticShapeFrame = (StaticShapeFrame) shapeFrame;

            /*
            @ashleystankovits 1/4/23
            Checking if multiple shapes are used in chart
             */
            boolean multi = frames.stream()
               .filter(frame -> frame instanceof StaticShapeFrame)
               .map(frame -> (StaticShapeFrame) frame)
               .anyMatch(frame -> !Tool.equals(frame.getShape(), staticShapeFrame.getShape()));

            if(multi) {
               return true;
            }

            frames.add(staticShapeFrame);
         }
      }

      int xAggregateCount = aggregateCount(cinfo.getRTXFields());
      int yAggregateCount = aggregateCount(cinfo.getRTYFields());

      if(xAggregateCount == 0 && yAggregateCount < 2 || yAggregateCount == 0 && yAggregateCount < 2)
      {
         return false;
      }

      if(xAggregateCount > 1 && yAggregateCount > 0) {
         return false;
      }

      return true;
   }

   private boolean isRelationChartHasLegend(RelationChartInfo cinfo) {
      return cinfo.getNodeColorField() != null &&
         cinfo.getNodeColorField().getLegendDescriptor().isVisible() ||
         cinfo.getNodeSizeField() != null &&
            cinfo.getNodeSizeField().getLegendDescriptor().isVisible();
   }

   /**
    * Get the chart aggregate ref count.
    */
   private int aggregateCount(ChartRef[] refs) {
      int count = 0;

      if(refs == null) {
         return count;
      }

      for(ChartRef ref : refs) {
         if(ref instanceof ChartAggregateRef) {
            count++;
         }
      }

      return count;
   }

   private static boolean hasAesthetic(ChartBindable cinfo) {
      return cinfo.getColorField() != null || cinfo.getShapeField() != null ||
         cinfo.getSizeField() != null || cinfo.getTextField() != null;
   }

   /**
    * Create a {@link ChartObject}
    *
    * @param area     The type of area to create
    * @param areaName A name used to describe the type of region, used in the
    *                 REST call to retrieve the correct image from the server
    *
    * @return an Optional of a ChartObject based off of the type of area
    */
   private Optional<ChartObject> createChartObject(DefaultArea area, String areaName) {
      return createChartObject(area, areaName, null);
   }

   /**
    * Create a {@link ChartObject}
    *
    * @param area     The type of area to create
    * @param areaName A name used to describe the type of region, used in the
    *                 REST call to retrieve the correct image from the server
    * @param parentArea parent area of the area
    *
    * @return an Optional of a ChartObject based off of the type of area
    */
   private Optional<ChartObject> createChartObject(DefaultArea area, String areaName,
                                                   DefaultArea parentArea)
   {
      if(area == null) {
         return Optional.empty();
      }

      Region region = area.getRegion();

      if(region == null) {
         return Optional.empty();
      }

      int rows;
      int cols;
      Rectangle rectangle = region.getBounds();
      List<ChartTile> components = new ArrayList<>();

      if(area instanceof PlotArea) {
         rows = ((PlotArea) area).getRows();
         cols = ((PlotArea) area).getCols();
      }
      else {
         rows = (int) Math.ceil(rectangle.getHeight() / ChartArea.MAX_IMAGE_SIZE);
         cols = (int) Math.ceil(rectangle.getWidth() / ChartArea.MAX_IMAGE_SIZE);
      }

      // Get the tiles that make up an image and store them as ChartComponents
      for(int row = 0; row < rows; row++) {
         for(int col = 0; col < cols; col++) {
            final int tileWidth = (int) Math.min(
               rectangle.getWidth() - col * ChartArea.MAX_IMAGE_SIZE, ChartArea.MAX_IMAGE_SIZE);
            final int tileHeight = (int) Math.min(
               rectangle.getHeight() - row * ChartArea.MAX_IMAGE_SIZE, ChartArea.MAX_IMAGE_SIZE);

            int previousWidth = (int) (col * ChartArea.MAX_IMAGE_SIZE);
            int previousHeight = (int) (row * ChartArea.MAX_IMAGE_SIZE);
            Rectangle componentArea =
               new Rectangle(previousWidth, previousHeight, tileWidth, tileHeight);
            components.add(new ChartTile(componentArea, row, col));

            if(components.size() >= 100000) {
               LOG.warn("Large chart with " + rows + " rows and " + cols + " columns.");
               break;
            }
         }
      }

      // Get layout bounds for plot and axis when these regions are scrollable.
      // Otherwise use region bounds to maintain API compatibility
      RectangularShape layoutBounds = rectangle;
      List<ChartRegion> regions = getChartRegions(area);
      ChartObject chartObject;

      if(area instanceof PlotArea) {
         layoutBounds = ((PlotArea) area).getLayoutBounds();
         final List<Double> xboundaries = ((PlotArea) area).getXboundaries();
         final List<Double> yboundaries = ((PlotArea) area).getYboundaries();
         final PlotDescriptor plotDescriptor = chartDesc.getPlotDescriptor();
         // only show reference line on line/area/point chart
         final boolean referenceLineVisible = plotDescriptor.isReferenceLineVisible();
         Coordinate coord = ((PlotArea) area).getVGraph().getCoordinate();
         BackgroundPainter bg = coord.getPlotSpec().getBackgroundPainter();
         final Double geoPadding = coord instanceof GeoCoord && bg != null
            ? bg.getPadding((GeoCoord) coord) : null;
         chartObject = Plot.builder()
            .areaName(areaName)
            .bounds(rectangle)
            .layoutBounds(layoutBounds)
            .tiles(components)
            .regions(regions)
            .xboundaries(xboundaries)
            .yboundaries(yboundaries)
            .drillLevels(getPlotDrillLevels())
            .drillFilter(hasDrillFilter(area))
            .showReferenceLine(referenceLineVisible)
            .geoPadding(geoPadding)
            .build();
      }
      else if(area instanceof AxisArea) {
         AxisArea axisArea = (AxisArea) area;
         layoutBounds = axisArea.getLayoutBounds();
         String axisType = axisArea.getAxisType();
         String sortOp = axisArea.getSortOp();
         String[] axisOps = axisArea.getAxisOps();
         String[] axisFields = axisArea.getAxisField();
         int[] axisSizes = axisArea.getAxisSize();
         Boolean secondary = axisArea.isSecondary();
         String sortField = axisArea.getSortField();
         List<DrillLevel> levels = new ArrayList<>();

         if(assembly != null) {
            levels = getDrillLevels(assembly, axisArea);
         }

         chartObject = Axis.builder()
            .areaName(areaName)
            .bounds(rectangle)
            .layoutBounds(layoutBounds)
            .tiles(components)
            .regions(regions)
            .axisType(axisType)
            .sortOp(sortOp)
            .axisOps(axisOps)
            .axisFields(axisFields)
            .axisSizes(axisSizes)
            .secondary(secondary)
            .sortField(sortField)
            .drillLevels(levels.toArray(new DrillLevel[0]))
            .drillFilter(hasDrillFilter(area))
            .sortFieldIsCalc(axisArea.isSortFieldIsCalc())
            .containsCustomDcRangeRef(containsCustomDcRangeRef(axisFields))
            .containsCustomDcMergeRef(containsCustomDcMergeRef(axisFields))
            .build();
      }
      else if(area instanceof TitleArea) {
         chartObject = Title.builder()
            .areaName(areaName)
            .bounds(rectangle)
            .layoutBounds(layoutBounds)
            .tiles(components)
            .regions(regions)
            .title((String) area.getChartAreaInfo().getProperty("titlename"))
            .build();
      }
      else if(area instanceof FixedArea) {
         chartObject = Facet.builder()
            .areaName(areaName)
            .bounds(rectangle)
            .layoutBounds(layoutBounds)
            .tiles(components)
            .regions(Optional.ofNullable(regions))
            .build();
      }
      else if(area instanceof LegendTitleArea || area instanceof LegendContentArea) {
         String titleLabel;
         String aestheticType;
         boolean titleVisible = true;
         String[] targetFields;
         String bg;
         String field;
         boolean nodeFrame = false;

         if(parentArea instanceof LegendArea) {
            LegendArea legendArea = (LegendArea) parentArea;
            nodeFrame = GraphUtil.isNodeAestheticFrame(legendArea.getVisualFrame(),
                                                       legendArea.getLegend().getGraphElement());
         }

         if(area instanceof LegendTitleArea) {
            field = ((LegendTitleArea) area).getField();
            titleLabel = ((LegendTitleArea) area).getTitleLabel();
            aestheticType = ((LegendTitleArea) area).getAestheticType();
            targetFields = ((LegendTitleArea) area).getTargetFields();
            bg = VSCSSUtil.getBackgroundRGBA(((LegendTitleArea) area).getBackground());
         }
         else {
            field = ((LegendContentArea) area).getField();
            titleLabel = ((LegendContentArea) area).getTitleLabel();
            aestheticType = ((LegendContentArea) area).getAestheticType();
            titleVisible = ((LegendContentArea) area).isTitleVisible();
            targetFields = ((LegendContentArea) area).getTargetFields();
            bg = VSCSSUtil.getBackgroundRGBA(((LegendContentArea) area).getBackground());
            double borderWidth = ((LegendContentArea) area).getBorderWidth();
            int x = (int) (rectangle.getX() - borderWidth);
            int y = (int) (rectangle.getY() - borderWidth);
            rectangle.setLocation(x, y);
         }

         chartObject = Legend.builder()
            .areaName(areaName)
            .bounds(rectangle)
            .layoutBounds(layoutBounds)
            .tiles(components)
            .regions(Optional.ofNullable(regions))
            .drillLevels(getLegendDrillLevels(area))
            .drillFilter(hasDrillFilter(area))
            .field(field)
            .titleLabel(titleLabel)
            .titleVisible(titleVisible)
            .aestheticType(aestheticType)
            .targetFields(targetFields)
            .background(bg)
            .containsCustomDcRangeRef(containsCustomDcRangeRef(new String[] { field }))
            .containsCustomDcMergeRef(containsCustomDcMergeRef(new String[] { field }))
            .nodeAesthetic(nodeFrame)
            .build();
      }
      else {
         return Optional.empty();
      }

      return Optional.of(chartObject);
   }

   private boolean containsCustomDcRangeRef(String[] fields) {
      if(cinfo == null || fields == null) {
         return false;
      }

      return Arrays.stream(fields).map(field -> cinfo.getRTFieldByFullName(field))
         .filter(VSDimensionRef.class::isInstance)
         .map(VSDimensionRef.class::cast)
         .anyMatch(dim -> dim.isDcRange());
   }

   private boolean containsCustomDcMergeRef(String[] fields) {
      if(cinfo == null || fields == null) {
         return false;
      }

      return Arrays.stream(fields).map(field -> cinfo.getRTFieldByFullName(field))
         .filter(VSDimensionRef.class::isInstance)
         .map(VSDimensionRef.class::cast)
         .anyMatch(dim -> dim.getDcMergeGroup() != null);
   }

   private boolean hasDrillFilter(DefaultArea area) {
      List<String> filterFields = assembly != null
         ? BaseDrillHandler.getDrillFiltersFields(assembly.getViewsheet())
         : null;

      if(CollectionUtils.isEmpty(filterFields)) {
         return false;
      }

      if(area instanceof PlotArea) {
         VSDataRef[] refs = cinfo.getFields();

         for(VSDataRef ref: refs) {
            if(ref instanceof VSDimensionRef && filterFields.contains(ref.getFullName())) {
               return true;
            }
         }

         if(cinfo instanceof MapInfo) {
            boolean filtered = Arrays.stream(((MapInfo) cinfo).getGeoFields())
               .anyMatch(f -> filterFields.contains(f.getFullName()));

            if(filtered) {
               return true;
            }
         }

         if(GraphTypes.isTreemap(model.getChartType()) || GraphTypes.isMap(model.getChartType())) {
            if(model.getPlot() == null || model.getPlot().drillLevels() == null) {
               return false;
            }

            return model.getPlot().drillLevels().stream().anyMatch(
               (drillLevel) -> drillLevel != DrillLevel.None);
         }

         // if filter is defined but the field is not on plot/axis/legend, this means the
         // hierarchy has changed (or been deleted). should allow drill up to remove condition.
         // We need to check the next level ref is or not in drill filter fields, if all refs do not match,
         // that means the chart is without the filter binding ref, which does not support drill up. (56423)
         boolean drillFilter = !this.model.getLegends().stream()
            .anyMatch((LegendContainer legend)-> filterFields.contains(legend.getField()));

         for(VSDataRef ref : refs) {
            if(!(ref instanceof VSDimensionRef)) {
               continue;
            }

            VSDimensionRef next = (VSDimensionRef) ref;

            while(next != null && !filterFields.contains(next.getFullName())) {
               next = VSUtil.getNextLevelRef(next, assembly.getXCube(), true);
            }

            if(drillFilter && next != null && filterFields.contains(next.getFullName())) {
               // if field is dynamic value, we don't support drilling (43261)
               return !((VSChartModel) model).isHasDynamic();
            }
         }
      }
      else if(area instanceof AxisArea) {
         String[] fields = ((AxisArea) area).getAxisField();
         return Arrays.stream(fields).anyMatch((field) -> filterFields.contains(field));
      }
      else if(area instanceof LegendContentArea) {
         return filterFields.stream().anyMatch((field) -> Objects.equals(field, ((LegendContentArea) area).getField()));
      }

      return false;
   }

   private List<DrillLevel> getLegendDrillLevels(DefaultArea area) {
      List<DrillLevel> drillLevels = new ArrayList<>();
      String field;

      if(area instanceof LegendTitleArea) {
         field = ((LegendTitleArea) area).getField();
      }
      else {
         field = ((LegendContentArea) area).getField();
      }

      if(StringUtils.isEmpty(field) || assembly == null) {
         return drillLevels;
      }

      XCube cube = getCube(assembly);

      ChartRef ref = cinfo.getFieldByName(field, true);

      if(ref instanceof VSDimensionRef && ((VSDimensionRef) ref).isDynamic()) {
         return drillLevels;
      }

      AestheticRef[] refs = cinfo.getAestheticRefs(true);
      drillLevels = Arrays.stream(refs)
         .filter(aestheticRef -> aestheticRef instanceof VSAestheticRef && field.equals(aestheticRef.getFullName()))
         .map(aestheticRef -> VSUtil.getDrillLevel(aestheticRef.getDataRef(), cube))
         .distinct()
         .collect(Collectors.toList());

      return drillLevels;
   }

   private List<DrillLevel> getPlotDrillLevels() {
      List<DrillLevel> drillLevels = new ArrayList<>();
      XCube cube = getCube(assembly);

      drillLevels = Arrays.stream(cinfo.getBindingRefs(true))
         .filter(chartRef -> chartRef instanceof VSDimensionRef)
         .map(chartRef -> ((VSChartInfo) cinfo).isPeriodRef(chartRef.getFullName())
            ? DrillLevel.None : VSUtil.getDrillLevel(chartRef, cube)
         )
         .distinct()
         .collect(Collectors.toList());

      if(cinfo instanceof VSMapInfo) {
         VSMapInfo mapInfo = (VSMapInfo) cinfo;
         ChartRef[] georefs = mapInfo.getGeoFields();

         drillLevels.addAll(Arrays.stream(georefs)
            .filter(geoRef -> geoRef instanceof VSDimensionRef)
            .map(geoRef -> VSUtil.getDrillLevel(geoRef, cube))
            .distinct()
            .collect(Collectors.toList()));
      }

      AestheticRef[] refs = cinfo.getAestheticRefs(true);
      drillLevels.addAll(Arrays.stream(refs)
         .filter(aestheticRef -> aestheticRef instanceof VSAestheticRef)
         .filter(aestheticRef -> {
            ChartRef chartRef = cinfo.getFieldByName(aestheticRef.getFullName(), true);
            return !(chartRef instanceof VSDimensionRef && ((VSDimensionRef) chartRef).isDynamic());
         })
         .map(aestheticRef -> VSUtil.getDrillLevel(aestheticRef.getDataRef(), cube))
         .distinct()
         .collect(Collectors.toList()));

      return drillLevels;
   }

   /**
    * Get the regions of a chart area. These are the selectable areas in a ChartObject
    * such as plot areas, axis labels, legend items.
    *
    * @param area the are that contains the regions
    *
    * @return a list of regions with corresponding information
    */
   private List<ChartRegion> getChartRegions(final DefaultArea area) {
      List<ChartRegion> regions = null;

      if(area instanceof ContainerArea || area instanceof LegendTitleArea) {
         regions = new ArrayList<>();
         DefaultArea[] childAreas;

         if(area instanceof ContainerArea) {
            childAreas = ((ContainerArea) area).getAllAreas();
         }
         else {
            // LegendTitleArea selectable region is itself
            childAreas = new DefaultArea[] {area};
         }

         if(childAreas != null) {
            short[][] segTypes = null;

            for(int idx = 0; idx < childAreas.length; idx++) {
               DefaultArea childArea = childAreas[idx];
               Region[] childRegions;

               // if child area is the parent title area, the bounds will be incorrect
               if(area == childArea && childArea instanceof TitleArea) {
                  // Title area returns a rectangle region, need to set its x,y to 0,0
                  // so it will cover the entire parent area
                  Rectangle bounds = area.getRegion().getBounds();
                  bounds.x = 0;
                  bounds.y = 0;
                  childRegions = new Region[] {new RectangleRegion(bounds)};
               }
               else {
                  // do not draw the GShape.NIL for word cloud.
                  if(GraphTypeUtil.isWordCloud(cinfo) && childArea.getVisualizable() instanceof PointVO)
                  {
                     PointVO pointVO = (PointVO) childArea.getVisualizable();
                     Shape[] shapes = pointVO.getShapes();

                     if(shapes == null || shapes.length == 0) {
                        continue;
                     }
                  }

                  childRegions = childArea.getRegions();
               }

               if(childRegions != null) {
                  if(childArea instanceof LegendItemArea) {
                     regions.add(createChartRegion(childArea, childRegions, null, idx, null));
                  }
                  else {
                     ChartRegion region = createChartRegion(childArea, childRegions, null, -1,
                                                            segTypes);
                     regions.add(region);

                     if(area instanceof PlotArea && region.segTypes() != null) {
                        segTypes = region.segTypes();
                     }
                  }
               }
            }
         }

         // need to add axis edges as rectangle regions to handle resizing axis
         if(area instanceof AxisArea) {
            createAxisResizeRegions((AxisArea) area, regions);
         }
      }

      return regions;
   }

   /**
    * Create Regions from axis areas for resizing
    * Logic is taken from ResizeGrid createAxisRegions()
    *
    * @param area       The AxisArea instance
    * @param regions    The list of ChartRegions to add to
    */
   private void createAxisResizeRegions(AxisArea area, List<ChartRegion> regions) {
      float resizeWidth = 4;
      float position = -2;
      int start = 0;
      int end = area.getAxisCount();
      int incr = 1;
      boolean xaxis = area.getAxisType().equals(ChartArea.X_AXIS);
      boolean left = (xaxis && area.isSecondary()) || (!xaxis && !area.isSecondary());
      Rectangle axisBounds = area.getRegion().getBounds();

      if(xaxis) {
         start = end - 1;
         end = -1;
         incr = -1;
      }

      for(int i = start; i != end; i += incr) {
         String fieldName = area.getAxisField(i);
         int axisSize = area.getAxisSize(i);
         RectangleRegion rectangleRegion;

         if(left) {
            position += axisSize;
         }

         // Regions are drawn inside axis so are relevant to axis, not entire chart
         if(xaxis) {
            rectangleRegion = new RectangleRegion(
               "", 0, position, axisBounds.width, resizeWidth);
         }
         else {
            rectangleRegion = new RectangleRegion(
               "", position, 0, resizeWidth, axisBounds.height);
         }

         if(!left) {
            position += axisSize;
         }

         regions.add(createChartRegion(null, new Region[] {rectangleRegion}, fieldName, -1, null));
      }
   }

   private ChartRegion createChartRegion(DefaultArea childArea, Region[] regions,
                                         String axisResizeField, int legendItemIndex,
                                         short[][] prevSegTypes)
   {
      // Get Shapes
      List<Shape> shapes = Arrays.stream(regions)
         .map(region -> {
            if(region instanceof RectangleRegion) {
               return ((RectangleRegion) region).getDoubleBounds();
            }
            else if(region instanceof PolygonRegion) {
               return ((PolygonRegion) region).createScaledShape();

            }

            return region.createShape();
         })
         .collect(Collectors.toList());

      // SubRegion List is 4D double Array for multipolygon geojson
      List<double[][][]> subRegionList = new ArrayList<>();
      List<short[]> segmentTypeLists = new ArrayList<>();
      Point centroid = new Point();
      boolean smallArea = false;
      boolean showReferenceLine = chartDesc.getPlotDescriptor().isReferenceLineVisible();
      boolean hasCurve = false;

      for(int i = 0; i < shapes.size(); i++) {
         Shape shape = shapes.get(i);
         Region region = regions[i];
         final Rectangle2D bounds = shape.getBounds2D();
         centroid.setLocation(bounds.getCenterX(), bounds.getCenterY());

         if(!Double.isNaN(bounds.getY()) && !Double.isNaN(bounds.getX())) {
            List<double[][]> subRegionCoordinates = new ArrayList<>();

            if(shape instanceof Ellipse2D) {
               segmentTypeLists.add(new short[] {ChartRegion.ELLIPSE_PATH});
               subRegionCoordinates.add(new double[][] {
                     new double[] { bounds.getX(), bounds.getY() },
                     new double[] { bounds.getWidth(), bounds.getHeight() }});
            }
            else if(shape instanceof Line2D) {
               segmentTypeLists.add(new short[] {ChartRegion.LINE_PATH});
               Line2D line = (Line2D) shape;
               subRegionCoordinates.add(new double[][] {
                  new double[] { line.getX1(), line.getY1() },
                  new double[] { line.getX2(), line.getY2() }});
            }
            else if(shape instanceof RectangularShape && !(shape instanceof Arc2D)) {
               RectangularShape rect = (RectangularShape) shape;

               // optimization
               if(rect.getHeight() < 3 || rect.getWidth() < 3) {
                  smallArea = true;
               }

               segmentTypeLists.add(new short[] {ChartRegion.RECT_PATH});
               subRegionCoordinates.add(new double[][] {
                     new double[] { rect.getX(), rect.getY() },
                     new double[] { rect.getWidth(), rect.getHeight() }});
            }
            else {
               List<double[]> coordinateList = new ArrayList<>();
               List<Short> segmentTypesList = new ArrayList<>();
               PathIterator pathIterator = shape.getPathIterator(null);
               int scaleFactor = 1;

               if(shape instanceof PolygonRegion.ScaledPolygon) {
                  scaleFactor = ((PolygonRegion.ScaledPolygon) shape).getScaleFactor();
               }

               while(!pathIterator.isDone()) {
                  double[] point = new double[6];
                  int segmentType = pathIterator.currentSegment(point);

                  if(segmentType != PathIterator.SEG_CLOSE) {
                     // optimization, only carry enough values for each path segment.
                     int n = 2;

                     switch(segmentType) {
                     case PathIterator.SEG_CUBICTO:
                        n = 6;
                        break;
                     case PathIterator.SEG_QUADTO:
                        n = 4;
                        break;
                     }

                     double[] parr = new double[n];

                     for(int k = 0; k < parr.length; k++) {
                        parr[k] = point[k] / scaleFactor;
                     }

                     hasCurve = hasCurve || segmentType >= 3;
                     coordinateList.add(parr);
                     segmentTypesList.add((short) segmentType);
                  }

                  pathIterator.next();
               }

               // optimization, truncate the segmentTypes if the last one is the same as previous.
               // the client (ts) will just use the last value if the index is >= last
               while(segmentTypesList.size() > 1) {
                  int last = segmentTypesList.size() - 1;

                  if(!Objects.equals(segmentTypesList.get(last), segmentTypesList.get(last - 1))) {
                     break;
                  }

                  segmentTypesList.remove(last);
               }

               short[] segTyp = new short[segmentTypesList.size()];
               for(int k = 0; k < segTyp.length; k++) {
                  segTyp[k] = segmentTypesList.get(k);
               }

               segmentTypeLists.add(segTyp);
               double[][] coordinates = coordinateList.toArray(new double[0][]);
               coordinateList = null; // release memory

               if(region instanceof PolygonRegion) {
                  coordinates = simplify(coordinates, ((PolygonRegion) region).isArc());
               }

               subRegionCoordinates.add(coordinates);
            }

            double[][][] subRegionCoordinatesArray = subRegionCoordinates.toArray(new double[0][][]);
            subRegionList.add(subRegionCoordinatesArray);
         }
      }

      double[][][][] subRegionsCoordinates = subRegionList.toArray(new double[0][][][]);
      short[][] segmentTypesLists = segmentTypeLists.toArray(new short[0][]);

      // Get tooltips and other necessary strings
      int measureNameIndex = -1;
      String tooltipString = null;
      int dimensionNameIndex = -1;
      int rowIndex = -1;
      int colIndex = -1;
      int valueIndex = -1;
      int axisFieldIndex = -1;
      List<HyperlinkModel> hyperlinks = new ArrayList<>();
      boolean selectable = true;
      boolean grouped = false;
      String dataType = null;
      boolean containsMeasure = false;
      boolean isAggregate = false;
      String areaType = null;
      List<Integer> parentValues = null;
      boolean supportReference = false;
      boolean isPeriod = false;

      // optimization, no need to get much information for very small area
      if(smallArea && childArea instanceof InteractiveArea) {
         InteractiveArea iarea = (InteractiveArea) childArea;
         measureNameIndex = getIndex(GraphUtil.getHyperlinkMeasure(iarea));
         rowIndex = iarea.getRowIndex();
         colIndex = iarea.getColIndex();
      }
      // axisResizeField is not null when creating ChartRegion for axis resize edge
      else if(axisResizeField != null) {
         axisFieldIndex = getIndex(axisResizeField);
         selectable = false;
      }
      else if(childArea instanceof InteractiveArea) {
         InteractiveArea iarea = (InteractiveArea) childArea;
         tooltipString = iarea
            .getToolTipString()
            .replaceAll(ChartToolTip.ENTER, "\r\n");
         measureNameIndex = getIndex(GraphUtil.getHyperlinkMeasure(iarea));
         rowIndex = iarea.getRowIndex();
         colIndex = iarea.getColIndex();
         // Even if the area has a measureName, it may not represent a measure field.
         containsMeasure = cinfo.getFieldByName(BoxDataSet.getBaseName(iarea.getMeasureName()),
             assembly != null && DateComparisonUtil.appliedDateComparison(assembly.getChartInfo())) != null;

         if(iarea.getDrillHyperlinks() != null) {
            hyperlinks = Arrays.stream(iarea.getDrillHyperlinks())
               .map(HyperlinkModel::createHyperlinkModel)
               .collect(Collectors.toList());
         }

         if(iarea.getHyperlink() != null) {
            hyperlinks.add(HyperlinkModel.createHyperlinkModel(iarea.getHyperlink()));
         }

         if(childArea instanceof VisualObjectArea) {
            supportReference = ((VisualObjectArea) childArea).isSupportReference();
         }

         if(childArea instanceof TextArea) {
            Visualizable visual = childArea.getVisualizable();

            if(visual instanceof VOText) {
               GraphElement elem = ((VOText) visual).getGraphElement();

               if(elem.getTextFrame() instanceof StackTextFrame || ((VOText) visual).isStacked()) {
                  rowIndex = -1;
               }
            }
         }
      }
      else if(childArea instanceof DimensionLabelArea) {
         tooltipString = ((DimensionLabelArea) childArea).getTooltip();
         String localizeTooltip = Tool.localizeTextID(tooltipString);
         tooltipString = Objects.toString(localizeTooltip, tooltipString);

         String valueString = ((DimensionLabelArea) childArea).getValue();
         parentValues = ((DimensionLabelArea) childArea).getParentValues().stream()
            .map(str -> getIndex(str)).collect(Collectors.toList());

         if(tooltipString == null) {
            tooltipString = ((DimensionLabelArea) childArea).getLabel();
         }

         valueIndex = getIndex(valueString);
         rowIndex = ((DimensionLabelArea) childArea).getRow();
         colIndex = ((DimensionLabelArea) childArea).getCol();
         String dimensionName = ((DimensionLabelArea) childArea).getDimensionName();
         dimensionNameIndex = getIndex(dimensionName);

         // area with dimension name may actual refer to measure
         DataRef field = cinfo.getFieldByName(dimensionName, true);
         containsMeasure = field == null;
         isAggregate = field instanceof XAggregateRef;

         hyperlinks = Arrays.stream(((DimensionLabelArea) childArea).getHyperlinks())
            .map(HyperlinkModel::createHyperlinkModel)
            .collect(Collectors.toList());
      }
      else if(childArea instanceof MeasureLabelsArea) {
         measureNameIndex = getIndex(((MeasureLabelsArea) childArea).getMeasureName());
      }
      else if(childArea instanceof LegendItemArea) {
         valueIndex = getIndex(((LegendItemArea) childArea).getValue());
         tooltipString = ((LegendItemArea) childArea).getLabel();

         String fieldName = ((LegendItemArea) childArea).getDimensionName();
         fieldName = fieldName == null ?
            ((LegendItemArea) childArea).getTitleLabel() : fieldName;
         AestheticRef aesRef = (AestheticRef) GraphUtil.getChartAesRef(cinfo,
            fieldName, false);

         isAggregate = aesRef != null && aesRef.getDataRef() instanceof XAggregateRef;

         if(((LegendItemArea) childArea).isDimension()) {
            dimensionNameIndex = getIndex(fieldName);
         }
         else {
            measureNameIndex = getIndex(fieldName);
         }
      }
      else if(childArea instanceof LegendContentArea) {
         String fieldName = ((LegendContentArea) childArea).getField();
         fieldName = fieldName == null ?
            ((LegendContentArea) childArea).getTitleLabel() : fieldName;
         AestheticRef aesRef = (AestheticRef) GraphUtil.getChartAesRef(cinfo,
            ((LegendContentArea) childArea).getField(), false);

         if(aesRef != null && aesRef.isMeasure()) {
            measureNameIndex = getIndex(fieldName);
         }
         else {
            dimensionNameIndex = getIndex(fieldName);
         }
      }
      else if(childArea instanceof LegendTitleArea) {
         valueIndex = ((LegendTitleArea) childArea).getTitleNameIdx();
         String fieldName = ((LegendTitleArea) childArea).getField();
         fieldName = fieldName == null ?
            ((LegendTitleArea) childArea).getTitleLabel() : fieldName;
         AestheticRef aesRef = (AestheticRef) GraphUtil.getChartAesRef(cinfo,
            ((LegendTitleArea) childArea).getField(), false);

         if(aesRef != null && aesRef.isMeasure()) {
            measureNameIndex = getIndex(fieldName);
         }
         else {
            dimensionNameIndex = getIndex(fieldName);
         }
      }
      else if(childArea instanceof AxisLineArea) {
         axisFieldIndex = getIndex(((AxisLineArea) childArea).getFieldName());
      }
      else if(childArea instanceof LabelArea) {
         valueIndex = getIndex(((LabelArea) childArea).getLabel());
      }

      if(childArea != null) {
         ChartAreaInfo chartAreaInfo = childArea.getChartAreaInfo();

         if(chartAreaInfo != null) {
            areaType = chartAreaInfo.getAreaType();
         }
      }

      if(dimensionNameIndex > -1) {
         String dimensionName = this.model.getStringDictionary().get(dimensionNameIndex);
         DataRef ref = cinfo.getFieldByName(dimensionName, true);
         // LegendItemArea might need to get the aesthetic ref
         ref = !(ref instanceof AestheticRef) && childArea instanceof LegendItemArea ?
            GraphUtil.getChartAesRef(cinfo, dimensionName, false) : ref;

         if(ref instanceof VSChartDimensionRef) {
            VSChartDimensionRef dimRef = (VSChartDimensionRef) ref;
            SNamedGroupInfo groupInfo = (SNamedGroupInfo)
               dimRef.getNamedGroupInfo();
            String label = (childArea instanceof DimensionLabelArea)
               ? ((DimensionLabelArea) childArea).getValue() : null;
            grouped = groupInfo != null && !(groupInfo instanceof DCNamedGroupInfo) &&
               label != null && groupInfo.getGroupValue(label) != null;
            isPeriod = dimRef.getDates() != null && dimRef.getDates().length >= 2;
         }
         else if(ref instanceof AestheticRef) {
            DataRef dataRef =((AestheticRef) ref).getDataRef();

            if(dataRef instanceof VSChartDimensionRef) {
               SNamedGroupInfo groupInfo =
                  (SNamedGroupInfo) ((VSChartDimensionRef) dataRef).getNamedGroupInfo();
               grouped = groupInfo != null && !(groupInfo instanceof DCNamedGroupInfo) &&
                  groupInfo.getGroupValue(((LegendItemArea) childArea).getValue()) != null;
            }
         }

         if(ref != null) {
            dataType = ref.getDataType();
         }
      }

      // optimization, filled in on the client
      if(prevSegTypes != null && Arrays.deepEquals(prevSegTypes, segmentTypesLists)) {
         segmentTypesLists = null;
      }

      int[] selectRows = null;

      if(childArea instanceof VisualObjectArea) {
         Visualizable vobj = childArea.getVisualizable();

         if(vobj instanceof ElementVO) {
            Geometry gobj = ((ElementVO) vobj).getGeometry();

            if(gobj instanceof ParaboxPointGeometry) {
               selectRows = ((ParaboxPointGeometry) gobj).getRowIndexes();
            }
         }
      }

      Boolean valueText = (Boolean) ((childArea instanceof TextArea)
         ? childArea.getChartAreaInfo().getProperty("valueText") : null);

      return ChartRegion.builder()
         .segTypes(segmentTypesLists)
         .pts(subRegionsCoordinates)
         // optimization, filled on client in ChartTool.fillIndex
         //.index(idx)
         .tipIdx(getIndex(tooltipString))
         .metaIdx(getRegionMetaIndex(new RegionMeta(measureNameIndex, dimensionNameIndex,
                                                    colIndex, areaType, supportReference,
                                                    containsMeasure, dataType,
                                                    axisFieldIndex < 0 ? null : axisFieldIndex,
                                                    valueText)))
         .rowIdx(rowIndex)
         .valIdx(valueIndex >= 0 ? valueIndex : null)
         .hyperlinks(hyperlinks.size() == 0 ? null : hyperlinks)
         .noselect(selectable ? null : true)
         .grouped(grouped ? true : null)
         .isAggr(isAggregate ? true : null)
         // appears not used in ts
         //.boundaryIdx(boundaryIndex)
         // centroid only used if hasCurve or showing reference line on client
         .centroid(hasCurve || showReferenceLine ? centroid : null)
         .parentVals(parentValues)
         .legendItemIdx(legendItemIndex < 0 ? null : legendItemIndex)
         .period(isPeriod ? true : null)
         .selectRows(selectRows)
         .build();
   }

   // reduce edges to reduce the json size
   private double[][] simplify(double[][] pts, boolean arc) {
      final float tolerance = arc ? 0.9f : Math.min(pts.length / 30f, 1.5f);
      Simplify<double[]> simplify = new Simplify<>(
         new double[0][0], new PointExtractor<double[]>() {
            @Override
            public double getX(double[] subRegionsCoordinate) {
               return subRegionsCoordinate[0];
            }

            @Override
            public double getY(double[] subRegionsCoordinate) {
               return subRegionsCoordinate[1];
            }
         });

      try {
         return simplify.simplify(pts, tolerance, false);
      }
      catch(Exception ex) {
         return pts;
      }
   }

   /**
    * Create a container to correlate linked legend content/title areas for
    * positioning and other related values.
    *
    * @param legendArea the legend area that will be transformed into the container
    * @return a new {@link LegendContainer}
    */
   private LegendContainer createLegendContainer(LegendArea legendArea) {
      Region region = legendArea.getRegion();

      // Border style
      LegendSpec legendSpec = legendArea.getVisualFrame().getLegendSpec();
      int borderStyle = legendSpec.getBorder();
      Color borderColor = legendSpec.getBorderColor();
      String border = VSCSSUtil.getBorderStyle(borderStyle, borderColor);
      double borderWidth = GTool.getLineWidth(legendSpec.getBorder());

      // Aesthetic Type (color/texture/shape/size)
      final int legendIndex = legendArea.getLegendIdx();
      String field = legendArea.getField();
      String aestheticType = legendArea.getAestheticType();
      List<String> targetFields = legendArea.getTargetFields();
      List<Legend> legends = new ArrayList<>();
      Dimension minSize = legendArea.getMinSize();

      // Get the legend objects (title and content) and store in list
      Arrays.stream(legendArea.getAreas())
         .map((area) -> {
            String areaName;

            if(area instanceof LegendTitleArea) {
               areaName = "legend_title";
            }
            else {
               areaName = "legend_content";
            }

            return createChartObject(area, areaName, legendArea);
         })
         .forEach(chartObjectOptional -> chartObjectOptional.ifPresent(chartObject -> {
            legends.add((Legend) chartObject);
         }));

      Rectangle bounds = region.getBounds();
      int h = (int) (bounds.getHeight() + borderWidth);

      if(model instanceof VSChartModel &&
         chartDesc.getLegendsDescriptor().getLayout() == LegendsDescriptor.IN_PLACE)
      {
         LegendsDescriptor desc = chartDesc.getLegendsDescriptor();
         LegendDescriptor ldesc = GraphUtil.getLegendDescriptor(
            cinfo, desc, legendArea.getField(),
            legendArea.getTargetFields(), legendArea.getAestheticType(),
            legendArea.isNodeAesthetic());

         if(ldesc != null) {
            Point2D pos = ldesc.getPosition();
            BaseFormatModel baseFormatModel = ((VSChartModel) model).getObjectFormat();

            if(pos != null) {
               int chartHeight = (int) baseFormatModel.getHeight();
               int chartWidth = (int) baseFormatModel.getWidth();
               double left = pos.getX() * chartWidth;
               double top = pos.getY() * chartHeight;

               // possible that charts are in a state where the position is negative (bc)
               bounds.setLocation(Math.max(0, (int) left), Math.max(0, (int) top));
            }

            Rectangle outer = chartArea.getRegions()[0].getBounds();
            int bottom = bounds.y < 1 ? 1 : 0;
            h = Math.min(h, outer.height - bounds.y - bottom);
         }
      }

      bounds.setSize((int) bounds.getWidth(), h);

      return new LegendContainer(legendIndex, region.getBounds(), border, field, targetFields,
         minSize, legends, aestheticType,
         GraphUtil.isNodeAestheticFrame(legendArea.getVisualFrame(), legendArea.getLegend().getGraphElement()));
   }

   // Returns the index of the given string in the chart-wide stringDictionary,
   // inserting it if necessary.
   private int getIndex(String item) {
      int idx = dictMap.getOrDefault(item, -1);

      if(idx != -1) {
         return idx;
      }

      idx = this.model.getStringDictionary().size();
      this.model.getStringDictionary().add(item);
      dictMap.put(item, idx);
      return idx;
   }

   // Returns the index of the given RegionMeta in the chart-wide regionMetaDictionary,
   // inserting it if necessary.
   private Integer getRegionMetaIndex(RegionMeta item) {
      int idx = metaDictMap.getOrDefault(item, -1);

      if(idx < 0) {
         idx = this.model.getRegionMetaDictionary().size();
         this.model.getRegionMetaDictionary().add(item);
         metaDictMap.put(item, idx);
      }

      return idx != 0 ? idx : null;
   }

   /**
    * Check if is show values or not.
    */
   private boolean isShowValues() {
      boolean showValues = false;

      if(chartDesc != null) {
         PlotDescriptor pDescp = chartDesc.getPlotDescriptor();
         showValues = pDescp != null ? pDescp.isValuesVisible() : showValues;
      }

      return showValues;
   }

   /**
    * Check if is have hidden axis or not.
    */
   private boolean isAxisHidden() {
      if(cinfo == null) {
         return false;
      }

      AxisDescriptor axis;
      boolean maxMode = model.isMaxMode();

      if(cinfo instanceof VSChartInfo || GraphTypeUtil.isScatterMatrix(cinfo)) {
         axis = cinfo.getAxisDescriptor();

         if(!maxMode && !axis.isLabelVisible() || maxMode && !axis.isMaxModeLabelVisible()) {
            return true;
         }
      }

      ChartRef[] fields = cinfo.getBindingRefs(true);

      if(cinfo instanceof RadarChartInfo) {
         axis = ((RadarChartInfo) cinfo).getLabelAxisDescriptor();

         if((!maxMode && !axis.isLabelVisible()) || (maxMode && !axis.isMaxModeLabelVisible())) {
            return true;
         }
      }

      if(cinfo.isSeparatedGraph() && !(cinfo instanceof StockChartInfo) &&
         !(cinfo instanceof CandleChartInfo))
      {
         for(int i = 0; i < fields.length; i++) {
            if(fields[i] != null) {
               axis = fields[i].getAxisDescriptor();

               if(isAxisHidden(axis, maxMode)) {
                  return true;
               }
            }
         }

         if(GraphTypes.isMekko(cinfo.getRTChartType())) {
            axis = cinfo.getAxisDescriptor();

            if(isAxisHidden(axis, maxMode)) {
               return true;
            }

            axis = cinfo.getAxisDescriptor2();

            if(isAxisHidden(axis, maxMode)) {
               return true;
            }
         }
      }
      else {
         ChartRef[] realMeasures = cinfo.getModelRefs(false);

         for(int i = 0; i < fields.length; i++) {
            if(fields[i] != null && isRealDimension(fields[i], realMeasures)) {
               axis = fields[i].getAxisDescriptor();

               if(isAxisHidden(axis, maxMode)) {
                  return true;
               }
            }
         }

         axis = cinfo.getAxisDescriptor();

         if(isAxisHidden(axis, maxMode)) {
            return true;
         }

         axis = cinfo.getAxisDescriptor2();

         if(isAxisHidden(axis, maxMode)) {
            return true;
         }
      }

      return false;
   }

   private boolean isAxisHidden(AxisDescriptor axis, boolean maxMode) {
      return (!maxMode && (!axis.isLineVisible() || !axis.isLabelVisible())) ||
         (maxMode && (!axis.isMaxModeLineVisible() || !axis.isMaxModeLabelVisible()));
   }

   /**
    * Check if is have hidden title or not.
    */
   private boolean isTitleHidden() {
      return isTitleHidden(chartDesc, cinfo, model.isMaxMode());
   }

   public static boolean isTitleHidden(ChartDescriptor chartDesc, ChartInfo cinfo, boolean maxMode) {
      TitlesDescriptor titlesDesc = chartDesc.getTitlesDescriptor();
      TitleDescriptor xtitle = titlesDesc.getXTitleDescriptor();
      TitleDescriptor x2title = titlesDesc.getX2TitleDescriptor();
      TitleDescriptor ytitle = titlesDesc.getYTitleDescriptor();
      TitleDescriptor y2title = titlesDesc.getY2TitleDescriptor();

      if(isTitleHidden(xtitle, maxMode, cinfo.getXFieldCount() > 0)) {
         return true;
      }

      if(isTitleHidden(x2title, maxMode, cinfo.getXFieldCount() > 0)) {
         return true;
      }

      if(isTitleHidden(ytitle, maxMode, cinfo.getYFieldCount() > 0)) {
         return true;
      }

      if(isTitleHidden(y2title, maxMode, cinfo.getYFieldCount() > 0)) {
         return true;
      }

      if(cinfo instanceof MergedChartInfo) {
         ChartRef[] fields = cinfo != null ? cinfo.getBindingRefs(true) : null;

         if(fields != null) {
            for(int i = 0; i < fields.length; i++) {
               if(fields[i] instanceof ChartAggregateRef) {
                  TitleDescriptor title = ((ChartAggregateRef) fields[i]).getTitleDescriptor();

                  if(isTitleHidden(title, maxMode, true)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   private static boolean isTitleHidden(TitleDescriptor title, boolean maxMode, boolean hasBinding) {
      // map titles (latitude/longitude) are always hidden. only show title if map facet.
      if(!hasBinding) {
         return false;
      }

      return maxMode ? !title.isMaxModeVisible() : !title.isVisible();
   }

   /**
    * Check if is a real dimension.
    */
   private boolean isRealDimension(ChartRef ref, ChartRef[] realMeasures) {
      if(GraphUtil.isDimension(ref)) {
         return true;
      }

      for(ChartRef realMeasure : realMeasures) {
         if(ref.equals(realMeasure)) {
            return false;
         }
      }

      return true;
   }

   private List<LegendDescriptor> getLegendDescriptors() {
      List<LegendDescriptor> list = new ArrayList<>();

      if(cinfo.isDonut() && cinfo.isMultiStyles()) {
         ChartRef[] refs = cinfo.getYFields();

         if(refs.length > 0) {
            ChartAggregateRef aggr = (ChartAggregateRef) refs[0];

            if(aggr.getColorField() != null) {
               list.add(aggr.getColorField().getLegendDescriptor());
            }

            if(aggr.getShapeField() != null) {
               list.add(aggr.getColorField().getLegendDescriptor());
            }

            if(aggr.getSizeField() != null) {
               list.add(aggr.getColorField().getLegendDescriptor());
            }

            return list;
         }
      }

      LegendsDescriptor legendsDesc = chartDesc.getLegendsDescriptor();
      return GraphUtil.getLegendDescriptors(cinfo, legendsDesc, null);
   }

   private boolean isLegendHidden() {
      return GraphBuilder.isLegendHidden(chartDesc, cinfo, model.isMaxMode());
   }

   /**
    * Check if it has hidden legend or not.
    */
   public static boolean isLegendHidden(ChartDescriptor chartDesc, ChartInfo cinfo, boolean maxMode) {
      if(chartDesc.getLegendsDescriptor().getLayout() == LegendsDescriptor.NO_LEGEND) {
         return true;
      }

      boolean donut = GraphTypes.isPie(cinfo.getChartStyle()) && cinfo.getYFieldCount() > 1
         && cinfo.getYField(0) instanceof ChartAggregateRef;

      if(donut && cinfo.isMultiStyles()) {
         ChartAggregateRef aggr = (ChartAggregateRef) cinfo.getYField(0);
         boolean hidden = isHiddenLegendDescriptor(aggr.getColorField(), maxMode, false);
         hidden = isHiddenLegendDescriptor(aggr.getShapeField(), maxMode, hidden);
         hidden = isHiddenLegendDescriptor(aggr.getSizeField(), maxMode, hidden);

         return hidden;
      }
      else {
         for(LegendDescriptor legend : getLegendDescriptors(cinfo, chartDesc)) {
            if(legend != null && (!maxMode && !legend.isVisible() ||
               maxMode && !legend.isMaxModeVisible()))
            {
               return true;
            }
         }
      }

      return false;
   }

   private static boolean isHiddenLegendDescriptor(AestheticRef aestheticRef, boolean maxMode, boolean hidden) {
      if(aestheticRef == null) {
         return hidden;
      }

      LegendDescriptor legend = aestheticRef.getLegendDescriptor();

      if(legend != null && (!maxMode && !legend.isVisible() || maxMode && !legend.isMaxModeVisible())) {
         hidden = true;
      }

      return hidden;
   }

   public static List<LegendDescriptor> getLegendDescriptors(
           ChartInfo chartInfo, ChartDescriptor chartDescriptor)
   {
      LegendsDescriptor legendsDescriptor = chartDescriptor.getLegendsDescriptor();
      List<LegendDescriptor> legends = new ArrayList<>();
      legends.addAll(GraphUtil.getLegendDescriptors(chartInfo, legendsDescriptor, null));

      if(chartInfo.isMultiAesthetic() && chartInfo instanceof AbstractChartInfo) {
         AggregateInfo aggr = ((AbstractChartInfo) chartInfo).getAggregateInfo();

         if(aggr == null) {
            return legends;
         }

         for(AestheticRef aref : getAestheticRefs(chartInfo)) {
            LegendDescriptor legend = aref.getLegendDescriptor();

            if(legend != null) {
               if(!chartInfo.isDonut() || !aref.getName().contains("Total@")) {
                  legends.add(legend);
               }
            }
         }
      }

      return legends;
   }

   /**
    * Get all AestheticRefs include AestheticRefs of runtime date comparison fields.
    */
   private static List<AestheticRef> getAestheticRefs(ChartInfo chartInfo) {
      List<AestheticRef> list = new ArrayList<>();

      for(AestheticRef aref : chartInfo.getAestheticRefs(false)) {
         list.add(aref);
      }

      if(chartInfo instanceof VSChartInfo) {
         VSChartInfo vinfo = (VSChartInfo) chartInfo;
         ChartRef[] dcRefs = vinfo.getRuntimeDateComparisonRefs();

         for(int i = 0; dcRefs != null && i < dcRefs.length; i++) {
            if(dcRefs[i] instanceof VSChartAggregateRef) {
               list.addAll(VSChartInfo.getAestheticRefs((VSChartAggregateRef) dcRefs[i]));
            }
         }
      }

      return list;
   }

   private int getLegendOption() {
      LegendsDescriptor legendsDescriptor = chartDesc.getLegendsDescriptor();
      int legendOption = LegendsDescriptor.NO_LEGEND;

      if(legendsDescriptor != null) {
         legendOption = legendsDescriptor.getLayout();
      }

      return legendOption;
   }

   private ChartInfo cinfo;
   private ChartArea chartArea;
   private ChartModel model;
   private ChartDescriptor chartDesc;
   private ChartVSAssembly assembly;
   private transient Object2IntOpenHashMap dictMap = new Object2IntOpenHashMap();
   private transient Object2IntOpenHashMap metaDictMap = new Object2IntOpenHashMap();

   private final static Logger LOG = LoggerFactory.getLogger(GraphBuilder.class);
}
