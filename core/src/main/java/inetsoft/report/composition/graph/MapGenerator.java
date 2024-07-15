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
package inetsoft.report.composition.graph;

import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.*;
import inetsoft.graph.geo.*;
import inetsoft.graph.geo.service.WebMapPainter;
import inetsoft.graph.geo.service.WebMapService;
import inetsoft.graph.geo.solver.*;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.report.composition.command.MessageCommand;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.graph.MapData;
import inetsoft.report.internal.graph.MapHelper;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.ColumnNotFoundException;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.*;
import inetsoft.util.log.LogLevel;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.*;

/**
 * MapGenerator generates polygon and point element graph.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public class MapGenerator extends MergedGraphGenerator {
   /**
    * Background field name.
    */
   private static final String BACKGROUND_FIELD = "BackgroundShape";

   /**
    * Constructor.
    * @param chart the specified chart.
    * @param adata the data generated without brushing, null if no brushing.
    * @param data the data generated with brushing.
    * @param vars the specified variable table.
    */
   public MapGenerator(ChartVSAssemblyInfo chart, DataSet adata, DataSet data, VariableTable vars,
                       DataSet vdata, int sourceType, Dimension size)
   {
      super(chart, adata, data, vars, vdata, sourceType, size);
      initColorFrame(false);
   }

   /**
    * Constructor.
    * @param info the specified chart info.
    * @param desc the specified chart descriptor.
    * @param adata the data generated without brushing, null if no brushing.
    * @param data the data generated with brushing.
    * @param vars the specified variable table.
    * @param vsrc the source (worksheet/tbl, query) of the chart.
    */
   public MapGenerator(ChartInfo info, ChartDescriptor desc, DataSet adata, DataSet data,
                       VariableTable vars, String vsrc, int sourceType, Dimension size)
   {
      super(info, desc, adata, data, vars, vsrc, sourceType, size);
      initColorFrame(false);
   }

   /**
    * Apply highlight.
    */
   @Override
   protected void applyHighlight() {
      if(adata == null) {
         if(!isBrushingSource()) {
            ColorFrame ocolor = (ColorFrame) getColorFrame(null).clone();
            initColorFrame(true);
            setColorFrame(applyHighlight(getColorFrame(null), false));
            pocolor = applyHighlight(pocolor, true);
            ptcolor = applyHighlight(ptcolor, false);
         }

         setAllColorFrame(null);
         aptcolor = null;
         apocolor = null;
      }
   }

   /**
    * Apply highlight.
    */
   private ColorFrame applyHighlight(ColorFrame color, boolean polygon) {
      HighlightGroup group = info.getHighlightGroup();

      if(group == null || polygon && GraphUtil.containsMapPoint(oinfo) ||
         // highlight not supported in contour
         !polygon && GraphTypes.isContour(info.getChartType()))
      {
         return color;
      }

      group = group.clone();
      replaceHLVariables(group, vars);

      HLColorFrame frame = new HLColorFrame(null, group, data);

      if(!frame.isEmpty()) {
         CompositeColorFrame cframe = new CompositeColorFrame();

         // polygon extral data always use defaut color
         if(polygon) {
            GeoColorFrame eframe = new GeoColorFrame(null,
               GraphUtil.getMapEmptyColor(getPlotDescriptor()));
            cframe.addFrame(eframe);
         }

         frame.setQuerySandbox(getQuerySandbox());
         cframe.addFrame(frame);

         if(color != null) {
            cframe.addFrame(color);
         }

         if(cframe.getFrameCount() == 1) {
            color = (ColorFrame) cframe.getFrame(0);
         }
         else {
            color = cframe;
         }
      }

      return color;
   }

   private PlotDescriptor getPlotDescriptor() {
      if(desc != null) {
         return desc.getPlotDescriptor();
      }

      return null;
   }

   /**
    * Darken a color.
    */
   private Color getDarkColor(Color color) {
      GradientColorFrame frame = new GradientColorFrame();
      frame.setUserFromColor(color);
      frame.setUserToColor(Color.black);

      return frame.getColor(0.5);
   }

   /**
    * Apply brush.
    */
   @Override
   protected void applyBrushing() {
      // not brushing target?
      if(adata == null) {
         // brushing source?
         if(isBrushingSource()) {
            ColorFrame ocolor = (ColorFrame) getColorFrame(null).clone();
            setColorFrame(applyBrushing(getColorFrame(null), odata, false));
            pocolor = applyBrushing(ocolor, odata, true);
            ptcolor = applyBrushing(ocolor, odata, false);
            voComparator = new BrushingComparator();
         }

         setAllColorFrame(null);
         aptcolor = null;
         apocolor = null;
      }
      // brushing target?
      else {
         // create all color frame - gray
         setAllColorFrame(applyBrushing(getColorFrame(null), brushDimColor, false));
         apocolor = applyBrushing(getColorFrame(null), brushDimColor, true);
         aptcolor = applyBrushing(getColorFrame(null), brushDimColor, false);

         // create brushing color frame - red
         ColorFrame ocolor = (ColorFrame) getColorFrame(null).clone();
         setColorFrame(applyBrushing(getColorFrame(null), brushHLColor, false));
         pocolor = applyBrushing(ocolor, brushHLColor, true);
         ptcolor = applyBrushing(ocolor, brushHLColor, false);
      }
   }

   /**
    * Apply all element brushing.
    */
   private ColorFrame applyBrushing(ColorFrame color, Color c, boolean polygon) {
      // geo color frame
      boolean dark = !polygon && GraphUtil.containsMapPolygon(oinfo);
      c = dark ? getDarkColor(c) : c;
      ColorFrame cframe = new GeoColorFrame(c, null);

      // map polygon default color
      if(polygon) {
         cframe = applyBrushing(getMapDefColorFrame(), cframe);
      }

      cframe = applyBrushing(color, cframe);
      return cframe;
   }

   /**
    * Apply brushing.
    */
   private ColorFrame applyBrushing(ColorFrame color, DataSet data, boolean polygon) {
      // brush source extral data color frame
      GeoColorFrame eframe = new GeoColorFrame(null,
         GraphUtil.getMapEmptyColor(getPlotDescriptor()));

      // highlight color frame
      boolean dark = !polygon && GraphUtil.containsMapPolygon(oinfo);
      Color rc = dark ? getDarkColor(brushHLColor) : brushHLColor;
      HLColorFrame hframe = getHLColorFrame(rc);
      hframe.setDefaultColor(null);

      // geo color frame
      Color gc = dark ? getDarkColor(brushDimColor) : brushDimColor;
      Color defColor = !polygon ? brushDimColor : null;

      GeoColorFrame gframe = new GeoColorFrame(gc, defColor);

      // composite color frame
      CompositeColorFrame cframe = new CompositeColorFrame();
      cframe.addFrame(eframe);
      cframe.addFrame(hframe);
      cframe.addFrame(gframe);

      // map polygon default color
      if(polygon) {
         cframe.addFrame(getMapDefColorFrame());
      }

      if(color != null) {
         cframe.addFrame(color);
      }

      return cframe;
   }

   /**
    * Get the axis descriptor of a column.
    */
   @Override
   protected AxisDescriptor getAxisDescriptor(String col) {
      // for map, the axis descriptors for measures are stored in
      // ChartAggregateRef; the axis descriptors for dimensions are stored in
      // DimensionRef
      ChartRef ref = getChartRef(col, true, null);
      return ref == null ? new AxisDescriptor() : ref.getAxisDescriptor();
   }

   /**
    * Create element graph internally.
    */
   @Override
   protected void createEGraph0() {
      int pointType = getMapInfo().getPathField() != null && !GraphTypes.isContour(getChartType())
         ? GraphTypes.CHART_LINE : GraphTypes.CHART_POINT;

      // 1. add fields
      addYDFields();       // add y dimension fields
      addXDFields();       // add x dimension fields
      addYMFields(true);   // add explicit y measures
      addXMFields(true);   // add explicit x measures
      fixMFields();        // remove unpaired explicit measures
      eymeasures = (List<String>) ymeasures.clone(); // explicit y measures
      exmeasures = (List<String>) xmeasures.clone(); // explicit x measures
      addGMFields();       // add generated measures
      fixGMScales(xmeasures, gxmeasures); // fix x measure scale
      fixGMScales(ymeasures, gymeasures); // fix y measure scale

      // 2. add elements
      // 2.1 use explicit measures to create point
      int cnt = Math.min(exmeasures.size(), eymeasures.size());

      for(int i = 0; i < cnt; i++) {
         String dim = exmeasures.get(exmeasures.size() - i - 1);
         String var = eymeasures.get(eymeasures.size() - i - 1);

         createElement(pointType, var, dim);
         fixMScale(var, pointType);
         fixMScale(dim, pointType);
      }

      // 2.2 use generated measures to create point or polygon
      for(int i = 0; i < gxmeasures.size(); i++) {
         String dim = gxmeasures.get(i);
         String var = gymeasures.get(i);
         int type = types.get(i);

         createElement(type, var, dim);
         fixMScale(var, type);
         fixMScale(dim, type);
      }

      // 2.3 add achor point element
      if(GraphUtil.containsAnchorPoint(getMapInfo()) ||
         // contour needs point element if there is no point on the map
         GraphTypes.isContour(getChartType()) && !GraphUtil.containsMapPoint(getMapInfo()))
      {
         GeoRef geoRef = GraphUtil.getPolygonField(getMapInfo());
         String name = GraphUtil.MAPPED_HEADER_PREFIX + geoRef.getName();
         String dim = GeoDataSet.getLongitudeField(name);
         String var = GeoDataSet.getLatitudeField(name);

         createElement(pointType, var, dim);
         fixMScale(var, pointType);
         fixMScale(dim, pointType);
      }

      // 2.4 sort elements
      cnt = graph.getElementCount();
      GraphElement[] elems = new GraphElement[cnt];
      boolean pointAdded = false;
      String[] gfields = this.gfields.toArray(new String[0]);

      for(int i = 0; i < cnt; i++) {
         elems[i] = graph.getElement(i);
         elems[i].setHint("geofields", gfields);

         if(elems[i] instanceof PolygonElement) {
            String var = elems[i].getVarCount() > 0 ? elems[i].getVars()[0] :
               null;
            String postfix = GeoDataSet.getLatitudeField("");
            String fld = var != null ? var.substring(0, var.length() - postfix.length()) : null;
            GeoShapeFrame frame = MapData.createShapeFrame(fld);
            elems[i].setShapeFrame(frame);
         }
         else if(elems[i] instanceof LineElement) {
            pointAdded = true;
         }
      }

      if(!pointAdded && getMapInfo().getPathField() != null) {
         Tool.addUserMessage(Catalog.getCatalog().getString("em.common.graph.pathNoPoint"));
      }

      Arrays.sort(elems, new ElementComparator());
      graph.clearElements();

      for(int i = 0; i < cnt; i++) {
         graph.addElement(elems[i]);
         setFramesDataSelector(elems[i]);
      }

      // 2.5 fix dim order, actually the first dim is var, move it to
      // the last to make tuple is right
      for(int i = 0; i < cnt; i++) {
         GraphElement elem = elems[i];
         String dim = elem.getDim(0);

         if(dim != null) {
            elem.removeDim(0);
            elem.addDim(dim);
         }
      }

      // 2.6 fix color frame
      for(int i = 0; i < cnt; i++) {
         fixColorFrame(elems[i]);
      }

      // 3.create coord
      LinearScale xscale = getScale(xmeasures);
      LinearScale yscale = getScale(ymeasures);
      GeoCoord coord = null;

      if(xscale != null && yscale != null) {
         PlotDescriptor plotdesc = desc.getPlotDescriptor();

         coord = new GeoCoord();
         coord.setXScale(xscale);
         coord.setYScale(yscale);
         coord.setFullMap(!isZooming());
         coord.setZoom(plotdesc.getZoom());
         coord.setPanX(plotdesc.getPanX());
         coord.setPanY(plotdesc.getPanY());

         if(isWebMap()) {
            coord.setProjection(new WebMercatorProjection());

            // if showing an initial map but only with bounds from data points, add some
            // padding so the points are not on the edges.
            if(!isZooming() && coord.getZoom() == 1 && coord.getPanX() == 0 &&
               coord.getPanY() == 0)
            {
               coord.setPadding(0.05);
            }
         }
         // for facet, reserve some space so map doesn't overlap or facet grid lines. (58027)
         else if(info.isFacet()) {
            coord.setPadding(0.01);
         }
      }

      if(coord == null && xmeasures.size() > 0 && ymeasures.size() > 0) {
         throw new MessageException(Catalog.getCatalog().getString(
            "em.common.graph.convertFailed"), LogLevel.WARN, false,
                                    MessageCommand.INFO);
      }

      Coordinate graphCoord = createCoord(coord, GraphTypes.CHART_MAP);
      graphCoord = graphCoord == null ? new RectCoord() : graphCoord;
      graph.setCoordinate(graphCoord);

      applyWebMap(graphCoord);

      if(oinfo != null) {
         oinfo.setFacet(info.isFacet());
      }
   }

   // set appropriate data selector
   private void setFramesDataSelector(GraphElement elem) {
      VisualFrame[] frames = { elem.getColorFrame(), elem.getTextureFrame(), elem.getShapeFrame(),
                               elem.getSizeFrame() };

      for(VisualFrame frame : frames) {
         setFrameDataSelector(frame);
      }
   }

   private void setFrameDataSelector(VisualFrame frame) {
      if(frame != null) {
         String geoField = GeoRef.wrapGeoName(frame.getField());

         // if topN is defined on geo column, color of the same dim should not show items
         // in the Others group. (60448)
         if(getMapInfo().isGeoRef(geoField)) {
            frame.setGraphDataSelector((data, row, fields) ->
                !Catalog.getCatalog().getString("Others").equals(data.getData(geoField, row)));
            frame.init(data);
         }
      }
   }

   protected VSFrameVisitor.FrameInitializer getFrameInitializer() {
      // need to apply the same filter for sync color as when it's actually displayed.
      return frame -> setFrameDataSelector(frame);
   }

   /**
    * Init color frame if there is no highlight and brush, color frame for
    * highlight and brush is in applyHighlight and applyBrush method.
    */
   private void initColorFrame(boolean forced) {
      PlotDescriptor plotdesc = desc.getPlotDescriptor();
      boolean showPolygonColor = plotdesc.isPolygonColor();
      boolean isBrushing = adata != null || isBrushingSource();
      HighlightGroup group = info.getHighlightGroup();
      boolean isHighlight = group != null && !group.isAllLevelsEmpty();

      if(!isBrushing && !isHighlight || forced) {
         boolean point = GraphUtil.containsMapPoint(info);
         boolean polygon = GraphUtil.containsMapPolygon(info);
         ColorFrame colorFrame = getColorFrame(null);

         if(point || showPolygonColor || GraphTypes.isContour(getChartType())) {
            ptcolor = colorFrame;
         }

         if(polygon) {
            if(point && !showPolygonColor || GraphTypes.isContour(getChartType())) {
               ColorFrame gframe = new GeoColorFrame(
                  GraphUtil.getMapDefaultColor(), GraphUtil.getMapEmptyColor(getPlotDescriptor()));
               pocolor = gframe;
            }
            else if(colorFrame instanceof StaticColorFrame) {
               StaticColorFrame scolor = (StaticColorFrame) getColorFrame(null);
               ColorFrame gframe = new GeoColorFrame(
                  scolor.getColor(), GraphUtil.getMapEmptyColor(plotdesc));
               pocolor = gframe;
            }
            else if(colorFrame instanceof CategoricalColorFrame) {
               CompositeColorFrame cframe = new CompositeColorFrame();
               ColorFrame gframe = new GeoColorFrame(null,
                  GraphUtil.getMapEmptyColor(getPlotDescriptor()));
               cframe.addFrame(gframe);
               cframe.addFrame(getColorFrame(null));
               pocolor = cframe;
            }
            else {
               CompositeColorFrame cframe = new CompositeColorFrame();

               if(colorFrame instanceof ColorValueColorFrame) {
                  ((ColorValueColorFrame) colorFrame).setDefaultColor(null);
               }

               cframe.addFrame(colorFrame);
               cframe.addFrame(getMapDefColorFrame());
               pocolor = cframe;
            }

            if(!forced) {
               setColorFrame(pocolor);
            }
         }
      }
   }

   /**
    * Get map default color frame.
    */
   private StaticColorFrame getMapDefColorFrame() {
      StaticColorFrame scolor = new StaticColorFrame();
      scolor.setDefaultColor(GraphUtil.getMapEmptyColor(getPlotDescriptor()));

      return scolor;
   }

   /**
    * Fix color frame.
    */
   private void fixColorFrame(GraphElement elem) {
      boolean point = !(elem instanceof PolygonElement);
      boolean all = isAllElem(elem);

      ColorFrame frame = null;

      // all point?
      if(point && all) {
         frame = aptcolor;
      }
      // point?
      else if(point && !all) {
         frame = ptcolor;
      }
      // all polygon?
      else if(all) {
         frame = apocolor;
      }
      // polygon?
      else {
         frame = pocolor;
      }

      elem.setColorFrame(frame);
      // applyColor depends on color frame setting.
      fixShapeFrame(elem.getShapeFrame(), elem);
   }

   /**
    * Get measure scale.
    */
   private LinearScale getScale(List measures) {
      for(int i = 0; i < measures.size(); i++) {
         String measure = (String) measures.get(i);
         Object scale = scales.get(measure);

         if(scale instanceof LinearScale) {
            return (LinearScale) scale;
         }
      }

      return null;
   }

   /**
    * Fix explicit measure fields, remove unpaired explicit measures, xmeasures,
    * ymeasures, scales map and scale should be fixed.
    */
   private void fixMFields() {
      int cnt = xmeasures.size() - ymeasures.size();

      if(cnt == 0) {
         return;
      }

      List measures = cnt > 0 ? xmeasures : ymeasures;
      cnt = Math.abs(cnt);

      for(int i = cnt - 1; i >= 0; i--) {
         String measure = (String) measures.remove(i);
         scales.remove(measure);
         fixScaleField(measure);
      }
   }

   /**
    * Fix scale, remove specified measure from scale fields.
    */
   private void fixScaleField(String measure) {
      ArrayList list = new ArrayList(scales.values());

      for(int i = 0; i < list.size(); i++) {
         Scale scale = (Scale) list.get(i);
         fixScaleField(scale, measure);
      }
   }

   /**
    * Fix scale, remove specified measure from speicifec scale.
    */
   private void fixScaleField(Scale scale, String measure) {
      String[] flds = scale.getFields();
      int idx = 0;

      for(; idx < flds.length; idx++) {
         if(measure.equals(flds[idx])) {
            break;
         }
      }

      if(idx >= flds.length) {
         return;
      }

      String[] nflds = new String[flds.length - 1];
      System.arraycopy(flds, 0, nflds, 0, idx);
      System.arraycopy(flds, idx + 1, nflds, idx, flds.length - idx - 1);

      scale.setFields(nflds);
   }

   /**
    * Add generated measures.
    */
   private void addGMFields() {
      ChartRef[] geoFlds = getMapInfo().getRTGeoFields();
      int pointType = getMapInfo().getPathField() != null && !GraphTypes.isContour(getChartType())
         ? GraphTypes.CHART_LINE : GraphTypes.CHART_POINT;

      for(int i = 0; i < geoFlds.length; i++) {
         GeoRef geoFld = (GeoRef) geoFlds[i];
         String name = GraphUtil.MAPPED_HEADER_PREFIX + geoFld.getName();
         String gxmeasure = GeoDataSet.getLongitudeField(name);
         String gymeasure = GeoDataSet.getLatitudeField(name);
         int layer = geoFld.getGeographicOption().getLayer();
         int type = MapData.isPointLayer(layer) ? pointType : GraphTypes.CHART_POLYGON;

         gfields.add(geoFld.getName());
         gxmeasures.add(gxmeasure);
         xmeasures.add(gxmeasure);
         gymeasures.add(gymeasure);
         ymeasures.add(gymeasure);
         types.add(type);
      }
   }

   /**
    * Fix measure scale, add genetaed measures to scale.
    */
   private void fixGMScales(List<String> ameasures, List<String> gmeasures) {
      if(gmeasures.size() == 0) {
         return;
      }

      String measure = ameasures.size() > 0 ? ameasures.get(0) : null;
      Scale scale = measure == null ? null : scales.get(measure);
      int cnt = gmeasures.size();
      String[] cols = new String[cnt];

      for(int i = 0; i < cnt; i++) {
         cols[i] = gmeasures.get(i);
      }

      // explicit measures exists?
      if(scale != null) {
         String[] flds = scale.getFields();
         String[] nflds = new String[flds.length + gmeasures.size()];
         System.arraycopy(flds, 0, nflds, 0, flds.length);

         for(int i = 0; i < cnt; i++) {
            nflds[flds.length + i] = cols[i];
         }

         Scale scale2 = (Scale) scale.clone();
         scale2.setFields(nflds);
         scale2.setDataFields(nflds);

         List<String> keys = new ArrayList<>(scales.keySet());

         for(int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            Object val = scales.get(key);

            if(val.equals(scale)) {
               scales.put(key, scale2);
            }
         }

         for(int i = 0; i < cnt; i++) {
            scales.put(cols[i], scale2);
         }
      }
      else {
         scale = new LinearScale(cols);

         for(int i = 0; i < cnt; i++) {
            scales.put(cols[i], scale);
         }
      }
   }

   /**
    * Check geographic ref validity.
    */
   private void checkGeoValidity() {
      // only 1 polygon field is supported
      GeoRef[] polygons = GraphUtil.getPolygonFields(getMapInfo());

      if(polygons.length > 1) {
         throw new MessageException(Catalog.getCatalog().getString(
            "em.common.graph.multiPolygons"), LogLevel.WARN, false,
            MessageCommand.INFO);
      }

      // type and layer compatible
      String type = getMapType(getMapInfo());
      ChartRef[] geoFlds = getMapInfo().getRTGeoFields();

      if(getMapInfo() instanceof VSMapInfo) {
         VSMapInfo vmap = (VSMapInfo) getMapInfo();
         ColumnSelection geoCols = vmap.getGeoColumns();
         String otype = "";
         ChartRef[] geoFlds0 = getMapInfo().getGeoFields();

         for(int i = 0; i < geoFlds0.length; i++) {
            GeoRef geoFld = (GeoRef) geoFlds0[i];
            GeoRef geo = (GeoRef)geoCols.getAttribute(geoFld.getName());
            String colType = geo != null && geo.getGeographicOption() != null ?
               geo.getGeographicOption().getMapping().getType() : "";

            if(i > 0 && !otype.equals(colType)) {
               throw new MessageException(Catalog.getCatalog().getString(
                  "composer.graph.mapType.doNotSupport"), LogLevel.WARN, false,
                  MessageCommand.INFO);
            }

            otype = colType;
         }
      }

      for(int i = 0; i < geoFlds.length; i++) {
         GeoRef geoFld = (GeoRef) geoFlds[i];
         int layer = geoFld.getGeographicOption().getLayer();
         boolean supported = MapHelper.isLayerSupported(type, layer);
         boolean background = GeoRef.getBaseName(geoFld.getFullName()).equals(BACKGROUND_FIELD);

         if(!supported && !background) {
            String layerName = MapHelper.getLayerName(type, layer);

            if(layerName == null) {
               layerName = "Undefined (" + geoFld.getName() + ")";
            }
            else {
               layerName = Catalog.getCatalog().getString(layerName);
            }

            String typeName = Catalog.getCatalog().getString(type);
            throw new MessageException(Catalog.getCatalog().getString(
               "em.common.graph.layerNotSupported", layerName, typeName),
                                       LogLevel.WARN, false, MessageCommand.INFO);
         }
      }
   }

   /**
    * Get brush data set.
    * @param adata the data generated without brushing, null if no brushing.
    * @param data the data generated with brushing.
    */
   @Override
   protected DataSet getBrushDataSet(DataSet adata, DataSet data) {
      if(adata == null) {
         return getDataSet(data);
      }

      DataSet madata = getMappedDataSet(adata);
      DataSet mdata = getMappedDataSet(data);
      prepareForBrushDataSet(mdata, madata);
      boolean[] flags = new boolean[mdata.getColCount()];

      for(int i = 0; i < flags.length; i++) {
         String header = mdata.getHeader(i);
         String header0 = header;

         if(isMappedGeoRef(header0)) {
            header0 = header0.substring(GraphUtil.MAPPED_HEADER_PREFIX.length(),
               header0.length());
            header0 = MapHelper.getGeoRefFullName(getMapInfo(), header0);
         }

         boolean isAggRef = isMeasure(mdata, header0);
         boolean isGeoRef = isMappedGeoRef(header);
         flags[i] = isAggRef || isGeoRef;
      }

      BrushDataSet bdata = new BrushDataSet(madata, mdata, flags);
      return getGeoDataSet(bdata);
   }

   /**
    * Check if the header is measure.
    */
   private boolean isMeasure(DataSet data, String header) {
      if(header.startsWith(GraphUtil.MAPPED_HEADER_PREFIX)) {
         header = header.substring(GraphUtil.MAPPED_HEADER_PREFIX.length());
      }

      return data.isMeasure(header);
   }

   /**
    * Check if the header is mapped geographic ref.
    */
   private boolean isMappedGeoRef(String header) {
      if(!header.startsWith(GraphUtil.MAPPED_HEADER_PREFIX)) {
         return false;
      }

      header = header.substring(GraphUtil.MAPPED_HEADER_PREFIX.length());
      return getMapInfo().isGeoRef(header);
   }

   /**
    * Get data set.
    * @param data the data set.
    */
   @Override
   protected DataSet getDataSet(DataSet data) {
      if(data == null) {
         return null;
      }

      return getGeoDataSet(getMappedDataSet(data));
   }

   /**
    * Get mapped data set.
    */
   private DataSet getMappedDataSet(DataSet data) {
      if(!checked) {
         checkGeoValidity();
         checked = true;
      }

      ChartRef[] geoFlds = getMapInfo().getRTGeoFields();
      ArrayList list = new ArrayList();

      for(ChartRef fld : geoFlds) {
         String fldName = MapHelper.isNamedGroupGeoRef((GeoRef) fld) ?
            fld.getFullName() : fld.getName();

         if(BACKGROUND_FIELD.equals(fld.getName())) {
            continue;
         }

         if(GraphUtil.indexOfHeader(data, fldName) >= 0) {
            list.add(fld);
         }
      }

      int cnt = list.size();
      ChartRef[] ageoFlds = new ChartRef[cnt];
      list.toArray(ageoFlds);

      if(cnt == 0) {
         return data;
      }

      GeoRef pref = null;
      GeoRef cref = null;
      int clayer = Integer.MIN_VALUE;
      int player = Integer.MAX_VALUE;

      for(int i = 0; i < cnt; i++) {
         GeoRef ref = (GeoRef) ageoFlds[i];
         GeographicOption opt = ref.getGeographicOption();
         int layer = opt.getLayer();

         if(layer > clayer) {
            clayer = layer;
            cref = ref;
         }

         if(layer < player) {
            player = layer;
            pref = ref;
         }
      }

      String type = getMapType(getMapInfo());
      boolean pexist = pref != null && cref != null && pref != cref &&
         !pref.getName().equals(BACKGROUND_FIELD);
      String pname = MapHelper.isNamedGroupGeoRef(pref) ?
            pref.getFullName() : pref.getName();
      int psourceCol = pexist ?
         GraphUtil.indexOfHeader(data, pname) : -1;
      int pnameColumn = pexist ? MapHelper.getBestNameColumn(data,
         psourceCol, type, clayer, player) : -1;
      String [] names = new String[cnt];
      NameMatcher[] matchers = new NameMatcher[cnt];
      ExactAlgorithm algorithm = new ExactAlgorithm();

      for(int i = 0; i < cnt; i++) {
         GeoRef ref = (GeoRef) ageoFlds[i];
         GeographicOption opt = ref.getGeographicOption();
         int layer = opt.getLayer();
         String refName = MapHelper.isNamedGroupGeoRef(ref) ? ref.getFullName() : ref.getName();
         int sourceCol = GraphUtil.indexOfHeader(data, refName);

         if(sourceCol == -1) {
            throw new ColumnNotFoundException(Catalog.getCatalog().getString
                  ("common.viewsheet.groupInvalid", refName));
         }

         boolean isChild = pexist && layer > player;
         int[] sourceCols = isChild ? new int[] {sourceCol, psourceCol} : new int[] {sourceCol};
         NameTable nameTable = MapData.getNameTable(layer);
         int[] childCols = MapData.getNameColumns(layer);
         int[] parentCols = isChild ? MapData.getNameColumns(layer, player) : new int[0];
         int cnt0 = isChild ? childCols.length * parentCols.length + 1 : childCols.length + 1;
         NameMatcher[] matchers0 = new NameMatcher[cnt0];
         // manual mapping
         String[][] mappings = MapHelper.getMappings(mergeMultiMapping(opt));
         int idx = 0;
         matchers0[idx] = new StaticNameMatcher(new int[]{sourceCol}, mappings);
         idx++;

         // auto mapping
         if(isChild) {
            for(int j = 0; j < childCols.length; j++) {
               for(int k = 0; k < parentCols.length; k++) {
                  int[] nameColumns = new int[] {childCols[j], parentCols[k]};
                  NameTable pnameTable = MapData.getNameTable(player);
                  NameTable[] nameTables = new NameTable[] {nameTable, pnameTable};
                  NameMatcher[] smatchers = new StaticNameMatcher[2];
                  smatchers[0] = null;
                  int[] columns = new int[] {psourceCol};
                  FeatureMapping fm = mergeMultiMapping(pref.getGeographicOption());
                  String[][] mapping = MapHelper.getMappings(fm);
                  smatchers[1] = new StaticNameMatcher(columns, mapping);
                  matchers0[idx] = new ExactNameMatcher(nameTables, sourceCols,
                     nameColumns, smatchers);
                  idx++;
               }
            }
         }
         else {
            for(int j = 0; j < childCols.length; j++) {
               matchers0[idx] = new MatchingNameMatcher(algorithm,
                  nameTable, sourceCols, new int[] {childCols[j]});
               idx++;
            }
         }

         names[i] = GraphUtil.MAPPED_HEADER_PREFIX + ref.getName();
         matchers[i] = new CombinedNameMatcher(matchers0, refName);
      }

      return new MappedDataSet(data, names, matchers);
   }

   /**
    * Merge the multi mapping to mappings.
    */
   private FeatureMapping mergeMultiMapping(GeographicOption opt) {
      FeatureMapping fmap = opt.getMapping();
      Map<String, List<String>> map = opt.getMapping().getDupMapping();

      if(map == null || map.size() == 0) {
         return fmap;
      }

      fmap = (FeatureMapping) fmap.clone();
      Map<String, String> mapping =  fmap.getMappings();
      Iterator<String> keys = map.keySet().iterator();

      while(keys.hasNext()) {
         String name = keys.next();
         String value = map.get(name).get(0);
         fmap.addMapping(name, mapping.get(value));
      }

      return fmap;
   }

   /**
    * Get geographic data set.
    */
   private DataSet getGeoDataSet(DataSet data) {
      // area names
      boolean isBrush = data instanceof BrushDataSet;
      GeoRef polyRef = GraphUtil.getPolygonField(getMapInfo());
      String bprefix = BrushDataSet.ALL_HEADER_PREFIX;
      String mprefix = GraphUtil.MAPPED_HEADER_PREFIX;
      String areaName = polyRef == null ? null : mprefix + polyRef.getName();
      String[] areaNames = polyRef == null ? new String[0] : isBrush
         ? new String [] {areaName, bprefix + areaName}
         : new String [] {areaName};

      // point names
      GeoRef[] ptRefs = GraphUtil.getPointFields(getMapInfo());
      List<String> list = new ArrayList<>();

      for(int i = 0; i < ptRefs.length; i++) {
         list.add(GraphUtil.MAPPED_HEADER_PREFIX + ptRefs[i].getName());
      }

      if(isBrush) {
         for(int i = 0; i < ptRefs.length; i++) {
            list.add(bprefix + mprefix + ptRefs[i].getName());
         }
      }

      String[] pointNames = new String[list.size()];
      list.toArray(pointNames);

      if(areaNames.length == 0 && pointNames.length == 0) {
         return data;
      }

      String type = getMapType(getMapInfo());
      GeoMap gmap = (areaNames.length == 0) ? null
         : MapData.getGeoMap(type, polyRef.getGeographicOption().getLayer(), isWebMap());
      GeoPoints[] gpoints = new GeoPoints[pointNames.length];

      for(int i = 0; i < gpoints.length; i++) {
         int layer = ptRefs[i % ptRefs.length].getGeographicOption().getLayer();
         gpoints[i] = MapData.getGeoPoints(type, layer);
      }

      GeoDataSet gdata = new GeoDataSet(data, gmap, areaNames, gpoints, pointNames);
      return gdata;
   }

   /**
    * Add graph element dimensions.
    * @param element, the graph element need to add dimensions.
    * @param chartType the chart style.
    * @param xname the xvariable name.
    */
   @Override
   protected void initElementDimension(GraphElement element, int chartType,
                                       String xname, boolean all)
   {
      xname = all ? BrushDataSet.ALL_HEADER_PREFIX + xname : xname;
      element.addDim(xname);

      for(int i = 0; i < xdims.size(); i++) {
         element.addDim(xdims.get(i));
      }

      for(int i = 0; i < ydims.size(); i++) {
         element.addDim(ydims.get(i));
      }
   }

   /**
    * Fix the egraph of the specified coord, set the attribute from desc and
    * info.
    */
   @Override
   protected void fixGraphProperties() {
      super.fixGraphProperties();

      // show labels
      PlotDescriptor plotdesc = desc.getPlotDescriptor();
      // make sure we don't repeat geo labels across different elements (e.g. polygon and shape)
      final Set<String> usedLabels = new HashSet<>();

      for(int i = 0; i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);

         // pan will make elements outside of viewport, don't force them back in.
         if(isWebMap() || plotdesc.hasPanZoom()) {
            elem.setInPlot(false);
         }

         if(plotdesc.isValuesVisible()) {
            if(!supportShowLabels(elem) && elem instanceof PolygonElement) {
               elem.setTextFrame(null);
            }
            else {
               // show labels in line element if path field exist
               if(elem instanceof PointElement && info.getPathField() != null &&
                  plotdesc.isPointLine())
               {
                  elem.setTextFrame(null);
                  continue;
               }

               String var = elem.getVarCount() == 0 ? null : elem.getVar(0);
               var = GraphUtil.getGeoField(var);
               final String fld = MapHelper.getGeoRefFullName(getMapInfo(), var);

               // only add geo field to text if the text is not already bound
               // to the geo field
               if(!Tool.equals(var, info.getTextField() + "") && !BACKGROUND_FIELD.equals(var)) {
                  TextFrame text = null;
                  final Map<GeoShape, String> tuples = new HashMap<>();

                  if(elem instanceof PolygonElement) {
                     if(data instanceof GeoDataSet) {
                        final GeoMap geoMap = ((GeoDataSet) data).getGeoMap();

                        try {
                           final Collection<String> names = geoMap.getNames();

                           for(String name : names) {
                              if(!usedLabels.contains(name)) {
                                 final GeoShape shape = geoMap.getShape(name);
                                 tuples.put(shape, name);
                              }
                           }

                           usedLabels.addAll(names);
                        }
                        catch(Exception ignored) {
                        }
                     }

                     text = new GeoTextFrame(fld, tuples);
                  }

                  if(text == null) {
                     text = new DefaultTextFrame(fld);
                  }

                  // don't break line by fld
                  // text.setField(fld);
                  if(info.getTextField() == null) {
                     elem.setTextFrame(text);
                  }
                  else {
                     CompositeTextFormat textFmt = GraphFormatUtil.getTextFormat(info, null, plotdesc);
                     CompositeTextFormat valueFormat = plotdesc.getTextFormat();
                     ChartRef geoRef = info.getFieldByName(fld, true);

                     // show-values use geo column format, same as
                     // ChartFormatController.getPlotFormat().
                     if(geoRef != null) {
                        valueFormat = geoRef.getTextFormat();
                     }

                     elem.setTextFrame(new ValueTextFrame(
                        elem.getTextFrame(), text, GraphUtil.getFormat(textFmt),
                        GraphUtil.getFormat(valueFormat)));
                  }

                  fixTextFrame(plotdesc, elem, getMapInfo().getGeoFieldByName(var, true, true));
               }
            }
         }

         if(isZooming() && elem instanceof PolygonElement) {
            elem.setInPlot(false);
         }
      }
   }

   @Override
   protected void applyAlpha(PlotDescriptor plotdesc, GraphElement elem) {
      double alpha = plotdesc.getAlpha();

      if(isWebMap() && elem instanceof PolygonElement && alpha < 0) {
         alpha = 0.7;
      }

      if(alpha >= 0) {
         // apply alpha on point on map if not webmap or if there is no polygon binding,
         // or map itself if there is no point or web map. (54260)
         boolean applyPoint = !isWebMap() || !GraphUtil.containsMapPolygon(info);
         boolean applyPolygon = GraphUtil.containsOnlyPolygon(info) || isWebMap();

         if(elem instanceof PointElement && applyPoint ||
            elem instanceof PolygonElement && applyPolygon)
         {
            elem.setHint(GraphElement.HINT_ALPHA, alpha);
         }
      }
   }

   private boolean isWebMap() {
      return isWebMap(info, desc, graphSize);
   }

   public static boolean isWebMap(ChartInfo info, ChartDescriptor desc, Dimension graphSize) {
      if(info.isFacet()) {
         return false;
      }

      String suspend = SreeEnv.getProperty("webmap.suspend.until");

      if(suspend != null) {
         long suspendTS = Long.parseLong(suspend);

         if(suspendTS > System.currentTimeMillis()) {
            return false;
         }

         SreeEnv.setProperty("webmap.suspend.until", null);
      }

      String service = SreeEnv.getProperty("webmap.service");
      String mapboxUser = SreeEnv.getProperty("mapbox.user");
      String mapboxToken = SreeEnv.getProperty("mapbox.token");
      String mapboxStyle = SreeEnv.getProperty("mapbox.style");
      String googleMapsKey = SreeEnv.getProperty("google.maps.key");

      boolean webmap = desc.getPlotDescriptor().isWebMap() &&
         (MapInfo.MAPBOX.equals(service) && mapboxUser != null && mapboxToken != null &&
            mapboxStyle != null || MapInfo.GOOGLE.equals(service) && googleMapsKey != null);

      if(webmap && graphSize != null) {
         WebMapService mapService = getWebMapService(desc);
         int maxsize = mapService.getMaxSize();

         if(graphSize.width > maxsize || graphSize.height > maxsize) {
            webmap = false;
            CoreTool.addUserMessage(Catalog.getCatalog().getString(
               "viewer.webmap.sizeLimit", service, maxsize));
         }
      }

      return webmap;
   }

   private static WebMapService getWebMapService(ChartDescriptor desc) {
      return WebMapService.getWebMapService(desc.getPlotDescriptor().getWebMapStyle());
   }

   private void applyWebMap(Coordinate coord) {
      if(isWebMap() && data instanceof GeoDataSet) {
         PlotDescriptor plot = desc.getPlotDescriptor();
         WebMapService mapService = getWebMapService(desc);
         coord.getPlotSpec().setBackgroundPainter(new WebMapPainter(mapService));
         GeoCoord geoCoord = (GeoCoord) coord;

         mapService.setZoomLevel(plot.getZoomLevel());
         ((GeoDataSet) data).setLoadAll(false);
         geoCoord.setMinTile(mapService.getTileSize());
         geoCoord.setFullMap(false);

         // restore original lon/lat range so the map will not shift when filtering data.
         if(plot.getLonLat() != null) {
            Rectangle2D lonlat = plot.getLonLat();
            geoCoord.setExtent(lonlat.getMinX(), lonlat.getMinY(), lonlat.getMaxX(),
                               lonlat.getMaxY());
         }
      }
   }

   /**
    * Check if the specified element support show labels.
    */
   private boolean supportShowLabels(GraphElement element) {
      MapInfo info = getMapInfo();

      // if point exists, polygon does not support show labels
      if(element instanceof PolygonElement && (GraphUtil.containsMapPoint(info) ||
         GraphTypes.isContour(info.getChartType())))
      {
         return false;
      }

      // brush target all element does not support show labels
      if(isAllElem(element) && !isBrushingSource()) {
         return false;
      }

      // feature #40940 always draw labels for polygons
      /*
      String var = element.getVarCount() == 0 ? null : element.getVar(0);
      // only generated lon/lat supports show labels, since explicit lon/lat has
      // no corresponding label column
      if(!GraphUtil.isLatitudeField(var)) {
         return false;
      }
      */

      return true;
   }

   /**
    * Get map info.
    */
   private MapInfo getMapInfo() {
      return (MapInfo) info;
   }

   /**
    * Get displayed measure name in default title.
    */
   @Override
   protected String getRefLabel(String measure) {
      measure = super.getRefLabel(measure);
      measure = CoreTool.replaceAll(measure, GraphUtil.MAPPED_HEADER_PREFIX,
         "");
      String lon = GeoDataSet.getLongitudeField("");
      String lat = GeoDataSet.getLatitudeField("");
      measure = CoreTool.replaceAll(measure, lon, " longitude");
      measure = CoreTool.replaceAll(measure, lat, " latitude");
      measure = measure.trim();

      return measure;
   }

   /**
    * Check if the element is all.
    */
   private static boolean isAllElem(GraphElement elem) {
      return elem.getVarCount() > 0 &&
             elem.getVar(0).startsWith(BrushDataSet.ALL_HEADER_PREFIX);
   }

   /**
    * Graph element comparator to make sure brushed element is above all element
    * point element is above polygon element.
    */
   private static class ElementComparator implements Comparator {
      /**
       * Compare two graph element.
       */
      @Override
      public int compare(Object obj1, Object obj2) {
         if(obj1 instanceof GraphElement && obj2 instanceof GraphElement) {
            GraphElement elem1 = (GraphElement) obj1;
            GraphElement elem2 = (GraphElement) obj2;

            return getZIndex(elem1) - getZIndex(elem2);
         }

         return 0;
      }

      /**
       * Get z index.
       */
      private int getZIndex(GraphElement elem) {
         int zindex = 0;

         // is point?
         if(elem instanceof PointElement) {
            zindex += 2;
         }

         // is brush?
         if(!isAllElem(elem)) {
            zindex++;
         }

         return zindex;
      }
   }

   /**
    * Get fixed dataset.
    */
   @Override
   protected DataSet getFixedDataSet(ChartInfo info, DataSet data, boolean all)
   {
      boolean containsPolygon = GraphUtil.containsMapPolygon(info);

      if(containsPolygon || all && data == null) {
         return data;
      }

      if(data == null) {
         data = new DefaultDataSet(new Object[0][0]);
      }

      ExpandableDataSet edata = new ExpandableDataSet(data);
      edata.addDimension(BACKGROUND_FIELD, null);

      return edata;
   }

   /**
    * Get fixed chart info, if only point add polygon field as background.
    */
   @Override
   protected ChartInfo getFixedInfo(ChartInfo info, DataSet data) {
      boolean containsPolygon = GraphUtil.containsMapPolygon(info);
      oinfo = (MapInfo) info;

      if(!containsPolygon) {
         MapInfo ninfo = (MapInfo) info.clone();
         String refName = BACKGROUND_FIELD;

         AttributeRef aref = new AttributeRef(refName);
         ColumnRef cref = new ColumnRef(aref);
         VSChartGeoRef vgref = new VSChartGeoRef(cref);
         GeoRef gref = vgref;

         String mapType = getMapType(ninfo);

         if("".equals(mapType)) {
            mapType = "World";
            ninfo.setMeasureMapType(mapType);
         }

         String layerValue = MapHelper.getPolygonLayer(mapType) + "";
         GeographicOption opt = gref.getGeographicOption();
         opt.setLayerValue(layerValue);

         ((VSMapInfo) ninfo).addRTGeoField(gref);
         return ninfo;
      }

      return info;
   }

   /**
    * Get the chart map type.
    * @return the chart map type.
    */
   private String getMapType(MapInfo info) {
      if(info instanceof VSMapInfo) {
         VSMapInfo vinfo = (VSMapInfo) info;
         String mapType = vinfo.getRTMapType();
         mapType = mapType == null ? vinfo.getMapType() : mapType;

         return mapType;
      }

      return info == null ? "" : info.getMapType();
   }

   // generated x measures
   private List<String> gxmeasures = new ArrayList<>();
   // generated y measures
   private List<String> gymeasures = new ArrayList<>();
   // explicit x measures
   private List<String> exmeasures = new ArrayList<>();
   // explicit y measures
   private List<String> eymeasures = new ArrayList<>();
   // geo field names
   private List<String> gfields = new ArrayList<>();
   private List<Integer> types = new ArrayList<>(); // element types
   private ColorFrame ptcolor;                // point color frame
   private ColorFrame pocolor;                // polygon color frame
   private ColorFrame aptcolor;               // all point color frame
   private ColorFrame apocolor;               // all polygon color frame
   private boolean checked = false;
   private MapInfo oinfo;
}
