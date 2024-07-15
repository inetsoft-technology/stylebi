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
package inetsoft.report.composition.region;

import inetsoft.graph.*;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.FacetCoord;
import inetsoft.graph.data.*;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.axis.*;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.*;
import inetsoft.graph.visual.GraphVO;
import inetsoft.report.Hyperlink;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.internal.Region;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XCube;
import inetsoft.uql.viewsheet.SelectionVSAssembly;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.*;
import java.text.Format;
import java.util.List;
import java.util.*;

/**
 * ChartArea defines the method of write all chart components to an OutputStream
 * and parse it from an InputStream.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class ChartArea extends DefaultArea {
   /**
    * Color legend.
    */
   public static final String COLOR_LEGEND = "Color";
   /**
    * Shape legend.
    */
   public static final String SHAPE_LEGEND = "Shape";
   /**
    * Texture legend.
    */
   public static final String TEXTURE_LEGEND = "Texture";
   /**
    * Line legend.
    */
   public static final String LINE_LEGEND = "Line";
   /**
    * Size legend.
    */
   public static final String SIZE_LEGEND = "Size";

   /**
    * X title.
    */
   public static final String X_TITLE = "x";
   /**
    * Secondary X title.
    */
   public static final String X2_TITLE = "x2";
   /**
    * Y title.
    */
   public static final String Y_TITLE = "y";
   /**
    * Y2 title.
    */
   public static final String Y2_TITLE = "y2";
   /**
    * X axis.
    */
   public static final String X_AXIS = "x";
   /**
    * Y axis.
    */
   public static final String Y_AXIS = "y";

   /**
    * Drop plot region
    */
   public static final int DROP_REGION_PLOT = 1;
   /**
    * Drop x region.
    */
   public static final int DROP_REGION_X = 2;
   /**
    * Drop y region.
    */
   public static final int DROP_REGION_Y = 3;
   /**
    * Horizontal direction.
    */
   public static final String HORIZONTAL_DIRECTION = "Horizontal";
   /**
    * Vertical direction.
    */
   public static final String VERTICAL_DIRECTION = "Vertical";
   /**
    * The flash can accept the max image width or height.
    */
   public static final double MAX_IMAGE_SIZE = 1024.0;

   /**
    * Constructor.
    */
   public ChartArea(VGraph vgraph, EGraph egraph, DataSet data, String linkURI, ChartInfo cinfo) {
      this(vgraph, vgraph, egraph, data, linkURI, cinfo, null, false, false, null);
   }

   public ChartArea(VGraph vgraph, EGraph egraph, DataSet data, String linkURI,
                    ChartInfo cinfo, String chartName)
   {
      this(vgraph, vgraph, egraph, data, linkURI, cinfo, null, false, false, chartName);
   }

   /**
    * Constructor.
    */
   public ChartArea(VGraphPair graphPair, String linkURI, ChartInfo cinfo,
                    XCube cube, boolean drill)
   {
      this(graphPair, linkURI, cinfo, cube, drill, false);
   }

   /**
    * Constructor.
    */
   public ChartArea(VGraphPair graphPair, String linkURI, ChartInfo cinfo,
                    XCube cube, boolean drill, boolean lightWeight)
   {
      this(graphPair, linkURI, cinfo, cube, drill, lightWeight, null);
   }

   /**
    * Constructor.
    */
   public ChartArea(VGraphPair graphPair, String linkURI, ChartInfo cinfo,
                    XCube cube, boolean drill, boolean lightWeight, String chartName)
   {
      this(graphPair.getRealSizeVGraph(), graphPair.getExpandedVGraph(),
           graphPair.getEGraph(), graphPair.getData(), linkURI, cinfo, cube,
           drill, lightWeight, chartName);
      this.cscript = graphPair.isChangedByScript();
   }

   /**
    * Constructor.
    * @param vgraph the real size vgraph.
    * @param evgraph the expanded size vgraph.
    * @param data the data set for the chart.
    * @param linkURI the linked url.
    * @param cinfo the chart binding info, used to process dimension hyperlink.
    */
   private ChartArea(VGraph vgraph, VGraph evgraph, EGraph egraph, DataSet data,
                     String linkURI, ChartInfo cinfo, XCube cube, boolean drill,
                     boolean lightWeight, String chartName)
   {
      super(vgraph, GTool.getFlipYTransform(vgraph));

      this.setLightWeight(lightWeight);
      this.cube = cube;
      this.vgraph = vgraph;
      this.evgraph = evgraph;
      this.drill = drill;
      this.egraph = egraph;
      this.chartName = chartName;
      init(egraph, data, linkURI, cinfo);
   }

   public EGraph getEGraph() {
      return egraph;
   }

   /**
    * Get dimension label area links.
    */
   public Map<Shape,Hyperlink.Ref> getDimensionLinks() {
      return dlinks;
   }

   /**
    * Get drill hyperlinks.
    */
   public Map<Shape,Hyperlink.Ref[]> getDrillLinks() {
      return drillLinks;
   }

   /**
    * Init hyperlink properties for VSChartDimensionRef.
    */
   private void initDimensionHyperlink(ChartInfo cinfo, Map<String,Hyperlink> links) {
      addDimensionHyperlink(cinfo.getRTXFields(), links);
      addDimensionHyperlink(cinfo.getRTYFields(), links);

      if(cinfo instanceof GanttChartInfo) {
         ChartRef start = ((GanttChartInfo) cinfo).getRTStartField();

         if(start != null) {
            ChartRef[] refs = new ChartRef[1];
            refs[0] = start;
            addDimensionHyperlink(refs, links);
         }
      }
   }

   /**
    * Add dimension hyperlink.
    */
   private void addDimensionHyperlink(ChartRef[] refs, Map<String,Hyperlink> links) {
      if(refs == null || refs.length <= 0) {
         return;
      }

      for(int i = 0; i < refs.length; i++) {
         if(refs[i] instanceof HyperlinkRef) {
            HyperlinkRef dim = (HyperlinkRef) refs[i];
            Hyperlink hyper = dim.getHyperlink();

            if(hyper != null) {
               links.put(((ChartRef) dim).getFullName(), hyper);
            }
         }
      }
   }

   /**
    * Init the area.
    */
   private void init(EGraph egraph, DataSet data, String linkURI, ChartInfo cinfo) {
      this.linkURI = linkURI;
      this.cinfo = cinfo;
      this.data = data instanceof DataSetFilter ? ((DataSetFilter) data).getRootDataSet() : data;

      // dimension hyperlink exist?
      // light weight? hyperlink is useless
      if(cinfo != null && !isLightWeight()) {
         initDimensionHyperlink(cinfo, dimHyperlinks);
      }

      mapCategoricalScaleToCoordinate(evgraph.getCoordinate(), coordsMap);
      palette = new IndexedSet<>();
      isWaterfall = isWaterfallData(data);

      AffineTransform trans = GTool.getFlipYTransform(vgraph);
      AffineTransform etrans = GTool.getFlipYTransform(evgraph);
      Coordinate coord = vgraph.getCoordinate();

      innerfields = new HashSet<>();

      if(coord instanceof FacetCoord) {
         FacetCoord facet = (FacetCoord) coord;
         Coordinate[][] coords = facet.getExpandedInnerCoords();

         for(int i = 0; i < coords.length; i++) {
            for(int j = 0; j < coords[i].length; j++) {
               addInnerFields(innerfields, coords[i][j]);
            }
         }
      }
      else {
         addInnerFields(innerfields, coord);
      }

      if(vgraph.getLegendGroup() != null) {
         this.legendsArea = new LegendsArea(egraph, vgraph.getLegendGroup(),
                                            trans, data, palette);
      }

      this.plotArea = new PlotArea(vgraph, evgraph, egraph, getPlotBounds(),
                                   etrans, palette, cinfo, isLightWeight(), chartName);

      // get topXAxisArea
      DefaultAxis[][] axes = groupAxes(evgraph.getAxesAt(Coordinate.TOP_AXIS));
      AbstractArea[] outerAreas = new AbstractArea[axes.length];

      for(int i = 0; i < axes.length; i++) {
         DefaultAxis[] axis = axes[i];
         outerAreas[i] = getSortedContainerArea(
            axis, VERTICAL_DIRECTION, etrans, Coordinate.TOP_AXIS, cinfo, i < axes.length - 1);
      }

      GraphBounds gbounds = new GraphBounds(evgraph, vgraph, cinfo);
      String dir = getDirection(outerAreas);
      SortedContainerArea topsort = new SortedContainerArea(outerAreas, dir, false, etrans);
      this.topXAxisArea = new AxisArea(
         new AbstractArea[] {topsort}, VERTICAL_DIRECTION,
         false, DROP_REGION_X, getAxisBounds(Coordinate.TOP_AXIS),
         getAxisLayoutBounds(Coordinate.TOP_AXIS), X_AXIS, etrans,
         getEAxisBounds(Coordinate.TOP_AXIS), true, cinfo, cube,
         evgraph.getAxesAt(Coordinate.TOP_AXIS), drill, egraph);

      // init judge is waterfall or not
      axes = groupAxes(evgraph.getAxesAt(Coordinate.LEFT_AXIS));

      // get bottomXAxisArea
      axes = groupAxes(evgraph.getAxesAt(Coordinate.BOTTOM_AXIS));
      outerAreas = new AbstractArea[axes.length];

      for(int i = 0; i < axes.length; i++) {
         DefaultAxis[] axis = axes[i];
         outerAreas[i] = getSortedContainerArea(
            axis, VERTICAL_DIRECTION, etrans, Coordinate.BOTTOM_AXIS, cinfo, i < axes.length);
      }

      dir = getDirection(outerAreas);
      SortedContainerArea bottomsort = new SortedContainerArea(outerAreas, dir, false, etrans);
      this.bottomXAxisArea = new AxisArea(
         new AbstractArea[] {bottomsort}, VERTICAL_DIRECTION,
         false, DROP_REGION_X, getAxisBounds(Coordinate.BOTTOM_AXIS),
         getAxisLayoutBounds(Coordinate.BOTTOM_AXIS), X_AXIS, etrans,
         getEAxisBounds(Coordinate.BOTTOM_AXIS), false, cinfo, cube,
         evgraph.getAxesAt(Coordinate.BOTTOM_AXIS), drill, egraph);

      // get leftYAxisArea
      axes = groupAxes(evgraph.getAxesAt(Coordinate.LEFT_AXIS));
      outerAreas = new AbstractArea[axes.length];

      for(int i = 0; i < axes.length; i++) {
         DefaultAxis[] axis = axes[i];
         outerAreas[i] = getSortedContainerArea(
            axis, HORIZONTAL_DIRECTION, etrans, Coordinate.LEFT_AXIS, cinfo,
            i < axes.length - 1);
      }

      dir = getDirection(outerAreas);
      SortedContainerArea y1 = new SortedContainerArea(outerAreas, dir, false, etrans);
      this.leftYAxisArea = new AxisArea(
         new DefaultArea[] {y1}, HORIZONTAL_DIRECTION,
         false, DROP_REGION_Y, getAxisBounds(Coordinate.LEFT_AXIS),
         getAxisLayoutBounds(Coordinate.LEFT_AXIS), Y_AXIS, etrans,
         getEAxisBounds(Coordinate.LEFT_AXIS), false, cinfo, cube,
         evgraph.getAxesAt(Coordinate.LEFT_AXIS), drill, egraph);

      // get rightYAxisArea
      axes = groupAxes(evgraph.getAxesAt(Coordinate.RIGHT_AXIS));
      outerAreas = new AbstractArea[axes.length];

      for(int i = 0; i < axes.length; i++) {
         DefaultAxis[] axis = axes[i];
         outerAreas[i] = getSortedContainerArea(
            axis, HORIZONTAL_DIRECTION, etrans, Coordinate.RIGHT_AXIS, cinfo,
            i < axes.length - 1);
      }

      dir = getDirection(outerAreas);
      SortedContainerArea y2 = new SortedContainerArea(outerAreas, dir, false,
                                                       etrans);
      this.rightYAxisArea = new AxisArea(
         new DefaultArea[] {y2}, HORIZONTAL_DIRECTION,
         false, DROP_REGION_Y, getAxisBounds(Coordinate.RIGHT_AXIS),
         getAxisLayoutBounds(Coordinate.RIGHT_AXIS), Y_AXIS, etrans,
         getEAxisBounds(Coordinate.RIGHT_AXIS), true, cinfo, cube,
         evgraph.getAxesAt(Coordinate.RIGHT_AXIS), drill, egraph);

      if(vgraph.getXTitle() != null || gbounds.hasFacetBorder(Coordinate.BOTTOM_AXIS)) {
         this.xTitleArea = new TitleArea(vgraph.getXTitle(), "x",
                                         DROP_REGION_X, trans,
                                         getTitleBounds("x"));
      }

      if(vgraph.getX2Title() != null || gbounds.hasFacetBorder(Coordinate.TOP_AXIS)) {
         this.x2TitleArea = new TitleArea(vgraph.getX2Title(), "x2",
                                          DROP_REGION_X, trans,
                                          getTitleBounds("x2"));
      }

      if(vgraph.getYTitle() != null || gbounds.hasFacetBorder(Coordinate.LEFT_AXIS)) {
         this.yTitleArea = new TitleArea(vgraph.getYTitle(), "y", DROP_REGION_Y, trans,
                                         getTitleBounds("y"));
      }

      if(vgraph.getY2Title() != null || gbounds.hasFacetBorder(Coordinate.RIGHT_AXIS)) {
         this.y2TitleArea = new TitleArea(vgraph.getY2Title(), "y2",
                                          DROP_REGION_Y, trans,
                                          getTitleBounds("y2"));
      }

      if(gbounds.isScrollable()) {
         facetTLArea = new FixedArea(getFacetCornerBounds("TL"));
         facetTRArea = new FixedArea(getFacetCornerBounds("TR"));
         facetBLArea = new FixedArea(getFacetCornerBounds("BL"));
         facetBRArea = new FixedArea(getFacetCornerBounds("BR"));
      }

      this.contentArea = new ContentArea(vgraph, trans);
      this.hasLegend = egraph.getVisualFrames().length > 0;
   }

   /**
    * Get dimension labels.
    * @return vlabel.
    */
   public VDimensionLabel[] getVDimensionLabels() {
      if(vlabels != null) {
         return vlabels;
      }

      ArrayList<VDimensionLabel> labels = new ArrayList<>();
      getVDimensionLabels(vgraph, labels);
      return labels.toArray(new VDimensionLabel[0]);
   }

   private void getVDimensionLabels(VGraph vgraph, Collection<VDimensionLabel> labels) {
      for(int i = 0; i < vgraph.getAxisCount(); i++) {
         Axis axis = vgraph.getAxis(i);

         if(axis.isLabelVisible()) {
            getVDimensionLabels(axis.getScale(), labels);
         }
      }
      vgraph.stream()
         .filter(v -> v instanceof GraphVO)
         .forEach(v -> getVDimensionLabels(((GraphVO) v).getVGraph(), labels));
   }

   private void getVDimensionLabels(Scale scale, Collection<VDimensionLabel> labels) {
      Object[] values = scale.getValues();
      String[] fields = scale.getFields();
      String field = fields.length > 0 ? fields[0] : null;

      if(!(scale instanceof CategoricalScale) && !(scale instanceof TimeScale)
         || "fake".equals(field))
      {
         return;
      }

      AxisSpec spec = scale.getAxisSpec();
      Format fmt = spec.getTextSpec().getFormat();

      for(int j = 0; j < values.length; j++) {
         Object vtext = isLightWeight() || values[j] == null ? values[j] : formatItemValue(fmt, values[j]);
         labels.add(new VDimensionLabel(values[j], null, field, null, vtext, 0, null));
      }
   }

   /**
    * Group axes by fields.
    */
   private DefaultAxis[][] groupAxes(DefaultAxis[] arr) {
      Map<String, List<DefaultAxis>> map = new HashMap<>();

      for(int i = 0; i < arr.length; i++) {
         if(!arr[i].isLabelVisible() && !arr[i].isLineVisible()) {
            continue;
         }

         String[] fields = arr[i].getScale().getFields();
         String key = Tool.arrayToString(fields);

         if(arr[i].getScale() instanceof LinearScale) {
            key = LINEARSCALE_KEY;
         }

         map.computeIfAbsent(key, k -> new ArrayList<>()).add(arr[i]);
      }

      return map.values().stream()
         .map(v -> v.toArray(new DefaultAxis[0]))
         .toArray(DefaultAxis[][]::new);
   }

   /**
    * Get linkable shapes. The key is a data point. The value is shapes.
    */
   public Map<Point, Set<Shape>> getShapes() {
      Map<Point, Set<Shape>> pshapes = plotArea.getShapes();

      // add all dimension label area shapes
      for(Map.Entry<Point, Set<Shape>> e : dimShapes.entrySet()) {
         Point p = e.getKey();
         Set<Shape> s1 = e.getValue() == null ? Collections.emptySet() : e.getValue();
         pshapes.computeIfAbsent(p, pt -> new ObjectOpenHashSet<>()).addAll(s1);
      }

      return pshapes;
   }

   /**
    * Get tooltips map. The key is a data point. The value is tooltip string.
    */
   public Map<Point, String> getTooltips() {
      Map<Point, String> ptips = getPlotArea().getTooltips();

      for(Point p : dimTips.keySet()) {
         String tip1 = dimTips.get(p);
         ptips.putIfAbsent(p, tip1);
      }

      return ptips;
   }

   /**
    * Write data to a DataOutputStream.
    * @param output0 the destination DataOutputStream.
    * @throws IOException
    */
   @Override
   public void writeData(DataOutputStream output0) throws IOException {
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      DataOutputStream output = new DataOutputStream(buf);

      super.writeData(output);
      palette.writeData(output);
      output.writeBoolean(bottomXAxisArea == null);

      if(bottomXAxisArea != null) {
         bottomXAxisArea.writeData(output);
      }

      output.writeBoolean(hasLegend);
      output.writeBoolean(cscript);
      output.writeBoolean(legendsArea == null);

      if(legendsArea != null) {
         legendsArea.writeData(output);
      }

      output.writeBoolean(topXAxisArea == null);

      if(topXAxisArea != null) {
         topXAxisArea.writeData(output);
      }

      output.writeBoolean(xTitleArea == null);

      if(xTitleArea != null) {
         xTitleArea.writeData(output);
      }

      output.writeBoolean(x2TitleArea == null);

      if(x2TitleArea != null) {
         x2TitleArea.writeData(output);
      }

      output.writeBoolean(leftYAxisArea == null);

      if(leftYAxisArea != null) {
         leftYAxisArea.writeData(output);
      }

      output.writeBoolean(rightYAxisArea == null);

      if(rightYAxisArea != null) {
         rightYAxisArea.writeData(output);
      }

      output.writeBoolean(yTitleArea == null);

      if(yTitleArea != null) {
         yTitleArea.writeData(output);
      }

      output.writeBoolean(y2TitleArea == null);

      if(y2TitleArea != null) {
         y2TitleArea.writeData(output);
      }

      output.writeBoolean(contentArea == null);

      if(contentArea != null) {
         contentArea.writeData(output);
      }

      output.writeBoolean(facetTLArea == null);

      if(facetTLArea != null) {
         facetTLArea.writeData(output);
      }

      output.writeBoolean(facetTRArea == null);

      if(facetTRArea != null) {
         facetTRArea.writeData(output);
      }

      output.writeBoolean(facetBLArea == null);

      if(facetBLArea != null) {
         facetBLArea.writeData(output);
      }

      output.writeBoolean(facetBRArea == null);

      if(facetBRArea != null) {
         facetBRArea.writeData(output);
      }

      output.writeBoolean(plotArea == null);

      if(plotArea != null) {
         plotArea.writeData(output);
      }

      byte[] arr = buf.toByteArray();
      output0.writeInt(arr.length);
      output0.write(arr, 0, arr.length);
   }

   /**
    * Check if a data set is for a waterfall graph.
    */
   private boolean isWaterfallData(DataSet data) {
      if(data instanceof DataSetFilter) {
         while(data instanceof DataSetFilter) {
            if(data instanceof SumDataSet) {
               return true;
            }

            data = ((DataSetFilter) data).getDataSet();
         }
      }

      return false;
   }

   /**
    * Get SortedContainerArea.
    */
   private SortedContainerArea getSortedContainerArea(
      DefaultAxis[] axis, String direction, AffineTransform trans, int pos,
      ChartInfo cinfo, boolean facet)
   {
      VariableTable vtable = null;
      Hashtable<String, SelectionVSAssembly> selections = null;

      if(cinfo instanceof VSChartInfo) {
         vtable = ((VSChartInfo) cinfo).getLinkVarTable();
         selections = ((VSChartInfo) cinfo).getLinkSelections();
      }

      if(axis == null || axis.length == 0 || axis[0] == null) {
         return null;
      }

      if(axis[0].getScale() instanceof LinearScale) {
         return getLinearArea(axis, direction, trans,
                              pos == Coordinate.RIGHT_AXIS ||
                              pos == Coordinate.TOP_AXIS, false);
      }
      else {
         return getCategoricalArea(axis, direction, trans, pos,
                                   vtable, selections, facet);
      }
   }

   /**
    * Get the sorted container area when the scale is LinearScale.
    */
   private SortedContainerArea getLinearArea(DefaultAxis[] axis,
                                             String direction,
                                             AffineTransform trans,
                                             boolean secondary,
                                             boolean facet)
   {
      SortedContainerArea[] sorts = new SortedContainerArea[axis.length];

      for(int i = 0; i < axis.length; i++) {
         AxisLine line = axis[i].getAxisLine();
         String[] fields = axis[i].getScale().getFields();
         AxisLineArea larea = (line == null || fields.length == 0) ?
            null : new AxisLineArea(line, trans, palette, secondary, facet);
         boolean hide = !axis[i].isLabelVisible() || fields == null ||
                        fields.length == 0;
         MeasureLabelsArea marea = hide ? null :
            new MeasureLabelsArea(axis[i], trans, palette, secondary);
         AbstractArea[] areas;

         if(larea == null && marea == null) {
            return null;
         }
         else if(larea != null && marea != null) {
            areas = new AbstractArea[2];

            if(direction.equals(HORIZONTAL_DIRECTION)) {
               if(secondary) {
                  areas[0] = larea;
                  areas[1] = marea;
               }
               else {
                  areas[0] = marea;
                  areas[1] = larea;
               }
            }
            else {
               if(secondary) {
                  areas[0] = marea;
                  areas[1] = larea;
               }
               else {
                  areas[0] = larea;
                  areas[1] = marea;
               }
            }
         }
         else {
            areas = new AbstractArea[2];
            areas[0] = marea;
            areas[1] = larea;
         }

         sorts[i] = new SortedContainerArea(areas, direction, false, trans);
      }

      String dir = getDirection(sorts);
      return new SortedContainerArea(sorts, dir, false, trans);
   }

   /**
    * Get the sorted container area when the scale is not LinearScale.
    */
   private SortedContainerArea getCategoricalArea(DefaultAxis[] axis,
                                                  String direction,
                                                  AffineTransform trans,
                                                  int pos, VariableTable vtable,
                                                  Hashtable<String, SelectionVSAssembly> selections,
                                                  boolean facet)
   {
      AbstractArea lineareas = getAxisLineAreas(axis, direction, trans,
                                                pos == Coordinate.RIGHT_AXIS,
                                                facet);
      AbstractArea labelareas = getLabelsAreas(axis, direction, trans, pos,
                                               vtable, selections);
      ArrayList list = new ArrayList();

      if(lineareas != null) {
         Region[] regions = lineareas.getRegions();

         if(!(regions == null || regions.length == 0)) {
            Rectangle rect = regions[0].getBounds();

            if(!(rect.width <= 0 || rect.height <= 0)) {
               list.add(lineareas);
            }
         }
      }

      if(labelareas != null) {
         Region[] regions = labelareas.getRegions();

         if(!(regions == null || regions.length == 0)) {
            Rectangle rect = regions[0].getBounds();

            if(!(rect.width <= 0 || rect.height <= 0)) {
               list.add(labelareas);
            }
         }
      }

      AbstractArea[] areas = new AbstractArea[list.size()];
      list.toArray(areas);

      Arrays.sort(areas, new AxisComparator());
      direction = getDirection(areas);
      return new SortedContainerArea(areas, direction, false, trans);
   }

   /**
    * Get the axis which include label.
    */
   private DefaultAxis getAvailableAxis(DefaultAxis[] axies) {
      for(DefaultAxis axis : axies) {
         VLabel[] labels = axis.getLabels();

         if(labels.length != 0 && labels[0] != null) {
            return axis;
         }
      }

      return null;
   }

   /**
    * Get axis labels area.
    */
   private SortedContainerArea getLabelsAreas(DefaultAxis[] axis,
      String direction, AffineTransform trans, int pos,
      VariableTable vtable, Hashtable<String, SelectionVSAssembly> selections)
   {
      if(getAvailableAxis(axis) == null) {
         return new SortedContainerArea(new DefaultArea[0], direction, false, trans);
      }

      VLabel[] labels = getAvailableAxis(axis).getLabels();
      int rotation = (int) labels[0].getTextSpec().getRotation();
      boolean isRotated = (rotation % 90) != 0;
      ArrayList<DimensionLabelArea> list = new ArrayList<>();
      // @by davyc, for facet, a column may create many axes, for each label in
      // the axis, they should be from different row, and all the axes must be
      // all in same level same position(top, left, bottom or right)
      Map<String, Integer> rows = new HashMap<>();

      for(int i = 0; i < axis.length; i++) {
         String[] fields = axis[i].getScale().getFields();
         String field = fields.length == 0 ? null: fields[0];
         boolean isOuter = !innerfields.contains(field);
         // @by davyc, the col should be correct, but for row, we shold just
         // make sure the dimension labels in same axis have different row index
         // and have same column index, dimension labels in different axis have
         // different column index, tip is same, because the row index seems not
         // so important
         Integer r = rows.get(field);
         int row = r == null ? 0 : r;
         int col = data.indexOfHeader(field);
         labels = axis[i].getLabels();

         for(int j = 0; j < labels.length; j++) {
            if(labels[j] == null ||
               // labels[j].getZIndex() < 0 ||
               // the removeOverlappedLabels in DefaultAxis has be changed to be called
               // on-demand up on rendering. so when AxisArea is created before axis is
               // rendered, all labels are visible. The visibility (zindex) will be changed
               // when svg is generated. therefore, we should include all (invisible) labels
               // otherwise the area index should shift between the index recorded before
               // the rendering and after.
               (isWaterfall && !isOuter && j == labels.length - 1))
            {
               continue;
            }

            // empty label messes up mouse selection detection. (57854)
            if(labels[j].getBounds().isEmpty()) {
               continue;
            }

            VDimensionLabel vlbl = (VDimensionLabel) labels[j];
            Map<String, Object> param = GraphUtil.getHyperlinkParam(axis[i], vlbl, pos, egraph, data);
            Hyperlink.Ref hyper = getHyperlink(axis[i], field, param);
            Hyperlink.Ref[] drills = GraphUtil.getDrillLinks(field, param, data, isLightWeight());
            List<Hyperlink.Ref> alinks = new ArrayList<>();

            if(hyper != null) {
               GraphUtil.addDashboardParameters(hyper, vtable, selections);
               alinks.add(hyper);
            }

            if(drills != null) {
               for(int k = 0; k < drills.length; k++) {
                  if(drills[k] != null) {
                     GraphUtil.addDashboardParameters(drills[k], vtable, selections);
                     alinks.add(drills[k]);
                  }
               }
            }

            DimensionLabelArea dArea = new DimensionLabelArea(
               axis[i], vlbl, isOuter, trans, palette,
               alinks.toArray(new Hyperlink.Ref[0]), linkURI);
            dArea.setRow(row);
            dArea.setCol(col);

            Shape shape = dArea.getRegion().getBounds();
            dArea.setHyperlinkExists(hyper != null);

            list.add(dArea);
            dlinks.put(shape, hyper);
            drillLinks.put(shape, drills);
            HashSet<Shape> sets = new HashSet<>();
            Point p = new Point(row, col);
            dimShapes.put(p, sets);
            String tip = dArea.getLabel();
            dimTips.put(p, Tool.encodeXML(tip));
            sets.add(shape);
            row++;
         }

         rows.put(field, row);
      }

      AbstractArea[] areas = new AbstractArea[list.size()];
      list.toArray(areas);
      Arrays.sort(areas, new AxisComparator());

      direction = direction.equals(VERTICAL_DIRECTION) ? HORIZONTAL_DIRECTION : VERTICAL_DIRECTION;
      return new SortedContainerArea(areas, direction, isRotated, trans);
   }

   /**
    * Get axis line areas.
    */
   private SortedContainerArea getAxisLineAreas(DefaultAxis[] axis,
                                                String direction,
                                                AffineTransform trans,
                                                boolean secondary,
                                                boolean facet)
   {
      ArrayList<AxisLineArea> list = new ArrayList<>();

      for(int i = 0; i < axis.length; i++) {
         if(axis[i].getAxisLine() == null) {
            continue;
         }

         list.add(new AxisLineArea(axis[i].getAxisLine(), trans, palette,
                                   secondary, facet));
      }

      AbstractArea[] areas = new AbstractArea[list.size()];
      list.toArray(areas);
      Arrays.sort(areas, new AxisComparator());

      direction = direction.equals(VERTICAL_DIRECTION) ? HORIZONTAL_DIRECTION : VERTICAL_DIRECTION;
      return new SortedContainerArea(areas, direction, false, trans);
   }

   /**
    * First sort the areas then to return the direction for the specified areas.
    * @return the direction.
    */
   private String getDirection(AbstractArea[] areas) {
      if(areas == null || areas.length <= 1) {
         return HORIZONTAL_DIRECTION;
      }

      // sort areas, for small x or y to large x or y
      Arrays.sort(areas, new AxisComparator());

      int idx = 0;

      if(areas[0] == null) {
         idx = 1;
      }

      if(idx + 1 >= areas.length) {
         return HORIZONTAL_DIRECTION;
      }

      Rectangle rect1 = areas[idx].getRegions()[0].getBounds();
      Rectangle rect2 = areas[idx + 1].getRegions()[0].getBounds();

      if(rect1.getX() + rect1.getWidth() <= rect2.getX()) {
         return HORIZONTAL_DIRECTION;
      }
      else if(rect1.getY() + rect1.getHeight() <= rect2.getY()) {
         return VERTICAL_DIRECTION;
      }
      else if(rect1.getY() == rect2.getY()) {
         return HORIZONTAL_DIRECTION;
      }
      else if(rect1.getX() == rect2.getX()) {
         return VERTICAL_DIRECTION;
      }
      else if(Math.abs(rect1.getX() - rect2.getX()) >
         Math.abs(rect1.getY() - rect2.getY()))
      {
         return VERTICAL_DIRECTION;
      }
      else {
         return VERTICAL_DIRECTION;
      }
   }

   /**
    * Add inner most coord fields.
    */
   private void addInnerFields(Set<String> outers, Coordinate coord) {
      Scale[] scales = coord.getScales();

      for(Scale scale : scales) {
         String[] flds = scale.getFields();
         Collections.addAll(outers, flds);
      }
   }

   /**
    * Get plot bounds, for layout the component in as.
    */
   private Rectangle2D getPlotBounds() {
      GraphBounds gbounds = new GraphBounds(vgraph, vgraph, cinfo);
      Rectangle2D abounds = gbounds.getPlotBounds();
      abounds = GTool.transform(abounds, GTool.getFlipYTransform(vgraph));
      return abounds;
   }

   /**
    * Get axis bounds, for layout the component in as.
    */
   private Rectangle2D getAxisBounds(int axis) {
      GraphBounds gbounds = new GraphBounds(vgraph, vgraph, cinfo);
      GraphBounds egbounds = new GraphBounds(evgraph, vgraph, cinfo);
      Rectangle2D abounds = gbounds.getAxisBounds(axis);
      Rectangle2D eabounds = egbounds.getAxisBounds(axis);
      abounds = GTool.transform(abounds, GTool.getFlipYTransform(vgraph));
      eabounds = GTool.transform(eabounds, GTool.getFlipYTransform(evgraph));

      return new Rectangle2D.Double(abounds.getX(), abounds.getY(),
         eabounds.getWidth(), eabounds.getHeight());
   }

   /**
    * Get axis bounds, for layout the component in as.
    */
   private Rectangle2D getAxisLayoutBounds(int axis) {
      GraphBounds gbounds = new GraphBounds(vgraph, vgraph, cinfo);
      Rectangle2D abounds = gbounds.getAxisBounds(axis);
      abounds = GTool.transform(abounds, GTool.getFlipYTransform(vgraph));
      return abounds;
   }

   /**
    * Get evgraph axis bounds, for set related point in axis.
    */
   private Rectangle2D getEAxisBounds(int axis) {
      GraphBounds egbounds = new GraphBounds(evgraph, vgraph, cinfo);
      Rectangle2D eabounds = egbounds.getAxisBounds(axis);
      eabounds = GTool.transform(eabounds, GTool.getFlipYTransform(evgraph));
      return eabounds;
   }

   /**
    * Get title bounds, for layout the component in as.
    */
   private Rectangle2D getTitleBounds(String title) {
      GraphBounds gbounds = new GraphBounds(vgraph, vgraph, cinfo);
      Rectangle2D abounds = null;

      if("x".equals(title)) {
         abounds = gbounds.getXTitleBounds();
      }
      else if("x2".equals(title)) {
         abounds = gbounds.getX2TitleBounds();
      }
      else if("y".equals(title)) {
         abounds = gbounds.getYTitleBounds();
      }
      else if("y2".equals(title)) {
         abounds = gbounds.getY2TitleBounds();
      }

      return GTool.transform(abounds, GTool.getFlipYTransform(vgraph));
   }

   /**
    * Get facet corder bounds.
    */
   private Rectangle2D getFacetCornerBounds(String pos) {
      GraphBounds gbounds = new GraphBounds(vgraph, vgraph, cinfo);
      Rectangle2D abounds = null;

      if("TL".equals(pos)) {
         abounds = gbounds.getFacetTLBounds();
      }
      else if("TR".equals(pos)) {
         abounds = gbounds.getFacetTRBounds();
      }
      else if("BL".equals(pos)) {
         abounds = gbounds.getFacetBLBounds();
      }
      else if("BR".equals(pos)) {
         abounds = gbounds.getFacetBRBounds();
      }

      return GTool.transform(abounds, GTool.getFlipYTransform(vgraph));
   }

   // comparable, for FancetCoord's y and x axis, and dimension labels
   private static class AxisComparator implements Comparator<AbstractArea> {
      @Override
      public int compare(AbstractArea a, AbstractArea b) {
         // handle null
         if(a == b) {
            return 0;
         }
         else if(a == null) {
            return -1;
         }
         else if(b == null) {
            return 1;
         }

         Rectangle ra = a.getRegions()[0].getBounds();
         Rectangle rb = b.getRegions()[0].getBounds();

         // current now the Line and Label area get bounds is not
         // exactitude enough
         if(ra.getX() + ra.getWidth() <= rb.getX() + 1 ||
            rb.getX() + rb.getWidth() <= ra.getX() + 1)
         {
            double xinc = ra.getX() - rb.getX();

            return xinc == 0 ? 0 : (xinc > 0 ? 1 : -1);
         }
         else {
            double yinc = ra.getY() - rb.getY();

            return yinc == 0 ? 0 : (yinc > 0 ? 1 : -1);
         }
      }
   }

   /**
    * Get the legends area.
    * @return legends area.
    */
   public LegendsArea getLegendsArea() {
      return legendsArea;
   }

   /**
    * Get the plot area.
    * @return plot area.
    */
   public PlotArea getPlotArea() {
      return plotArea;
   }

   /**
    * Get the top x axis area.
    * @return top x axis area.
    */
   public AxisArea getTopXAxisArea() {
      return topXAxisArea;
   }

   /**
    * Get the bottom x axis area.
    * @return bottom x axis area.
    */
   public AxisArea getBottomXAxisArea() {
      return bottomXAxisArea;
   }

   /**
    * Get the left y axis area.
    */
   public AxisArea getLeftYAxisArea() {
      return leftYAxisArea;
   }

   /**
    * Get the right y axis area.
    */
   public AxisArea getRightYAxisArea() {
      return rightYAxisArea;
   }

   /**
    * Get the x title area.
    */
   public TitleArea getXTitleArea() {
      return xTitleArea;
   }

   /**
    * Get the x2 title area.
    */
   public TitleArea getX2TitleArea() {
      return x2TitleArea;
   }

   /**
    * Get the y title area.
    */
   public TitleArea getYTitleArea() {
      return yTitleArea;
   }

   /**
    * Get the 2nd y title area.
    */
   public TitleArea getY2TitleArea() {
      return y2TitleArea;
   }

   public FixedArea getFacetTLArea() {
      return facetTLArea;
   }

   public FixedArea getFacetTRArea() {
      return facetTRArea;
   }

   public FixedArea getFacetBLArea() {
      return facetBLArea;
   }

   public FixedArea getFacetBRArea() {
      return facetBRArea;
   }

   /**
    * Get the content area.
    * @return the content area.
    */
   public ContentArea getContentArea() {
      return contentArea;
   }

   /**
    * Check if the chart is changed by script (element, data, etc).
    */
   public boolean isChangedByScript() {
      return cscript;
   }

   /**
    * Map all categorical axis to coordinate.
    */
   private void mapCategoricalScaleToCoordinate(Coordinate coord,
                                                Map<Axis, Coordinate> cmap) {
      Axis[] axes = coord.getAxes(true);

      if(axes != null && axes.length > 0) {
         for(int i = 0; i < axes.length; i++) {
            if(axes[i].isLabelVisible()) {
               cmap.put(axes[i], coord);
            }
         }
      }
   }

   /**
    * Get hyperlink.
    */
   private Hyperlink.Ref getHyperlink(DefaultAxis axis, String field, Map<String, Object> param) {
      if(param == null || dimHyperlinks.size() <= 0) {
         return null;
      }

      // hyperlink not allowed on top axis of mekko, same as gui. (49677)
      if(GraphTypes.isMekko(cinfo.getChartType()) && axis.getPrimaryAxis() != null) {
         return null;
      }

      Hyperlink link = dimHyperlinks.get(field);

      if(link == null) {
         return null;
      }

      ChartRef ref = cinfo.getFieldByName(field, false);

      // if dimension drilled, and a paramter is no longer available, ignore the
      // hyperlink. (42830)
      if(ref instanceof VSChartDimensionRef && ((VSChartDimensionRef) ref).isDateTime() &&
         !((VSChartDimensionRef) ref).isDynamic())
      {
         String dlevel1 = ((VSChartDimensionRef) ref).getDateLevelValue();
         int runtimeLevel = ((VSChartDimensionRef) ref).getDateLevel();
         String dlevel2 = runtimeLevel + "";
         boolean drilled = !Objects.equals(dlevel1, dlevel2);

         if(drilled) {
            // missing parameter, ignore
            if(link.getParameterNames().stream().anyMatch(name -> !param.containsKey(name))) {
               return null;
            }
         }
      }

      return new Hyperlink.Ref(link, param);
   }

   private static final String LINEARSCALE_KEY = "__LinearScale__";
   private final VGraph vgraph;
   private final VGraph evgraph;
   private final EGraph egraph;
   private LegendsArea legendsArea;
   private PlotArea plotArea;
   private AxisArea topXAxisArea;
   private AxisArea bottomXAxisArea;
   private AxisArea leftYAxisArea;
   private AxisArea rightYAxisArea;
   private TitleArea xTitleArea;
   private TitleArea x2TitleArea;
   private TitleArea yTitleArea;
   private TitleArea y2TitleArea;
   private ContentArea contentArea;
   private FixedArea facetTLArea;
   private FixedArea facetTRArea;
   private FixedArea facetBLArea;
   private FixedArea facetBRArea;
   private boolean hasLegend;
   private boolean cscript;
   private boolean drill = true;
   private IndexedSet<String> palette;
   private String linkURI;
   // dimension name --> hyperlink
   private final Map<String, Hyperlink> dimHyperlinks = new HashMap<>();
   private final Map<Axis, Coordinate> coordsMap = new HashMap<>();
   // dimension label shape -> hyperlink
   private final Map<Shape, Hyperlink.Ref> dlinks = new HashMap<>();
   // drill hyperlinks
   private final Map<Shape, Hyperlink.Ref[]> drillLinks = new HashMap<>();
   // dimension label row/col -> dimension label shape
   private final Map<Point, Set<Shape>> dimShapes = new HashMap<>();
   // dimension label row/col -> its label
   private final Map<Point, String> dimTips = new HashMap<>();
   private DataSet data;
   private boolean isWaterfall;
   private final XCube cube;
   private Set<String> innerfields;
   private ChartInfo cinfo;
   private VDimensionLabel[] vlabels;
   private String chartName;
}
