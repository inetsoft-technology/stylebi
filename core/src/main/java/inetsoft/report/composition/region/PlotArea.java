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
package inetsoft.report.composition.region;

import inetsoft.graph.*;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.*;
import inetsoft.graph.data.*;
import inetsoft.graph.element.*;
import inetsoft.graph.geometry.*;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.axis.*;
import inetsoft.graph.guide.legend.*;
import inetsoft.graph.internal.*;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.visual.*;
import inetsoft.report.Hyperlink;
import inetsoft.report.TableLens;
import inetsoft.report.composition.graph.*;
import inetsoft.report.composition.graph.calc.PercentCalc;
import inetsoft.report.filter.*;
import inetsoft.report.internal.*;
import inetsoft.report.internal.graph.MapHelper;
import inetsoft.report.lens.DataSetTable;
import inetsoft.uql.VariableTable;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.DateComparisonFormat;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.*;

import java.awt.*;
import java.awt.geom.*;
import java.io.DataOutputStream;
import java.io.IOException;
import java.text.*;
import java.util.List;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PlotArea defines the method of write data to an OutputStream and parse it
 * from an InputStream.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class PlotArea extends GridContainerArea implements GraphComponentArea, RollOverArea {
   public PlotArea(VGraph vgraph, VGraph evgraph, EGraph egraph,
                   Rectangle2D layoutbounds, AffineTransform trans,
                   IndexedSet<String> palette, ChartInfo chartInfo, boolean lightWeight,
                   String chartName)
   {
      super(evgraph, trans, palette);

      setLightWeight(lightWeight);
      this.egraph = egraph;
      this.vgraph = vgraph;
      this.layoutbounds = layoutbounds;
      this.chartInfo = chartInfo;
      this.linkcache = new FixedSizeSparseMatrix();
      this.chartName = chartName;
      boolean scrollable = GraphUtil.isScrollable(vgraph, chartInfo);
      boolean facet = evgraph.getCoordinate() instanceof FacetCoord;

      // scrolling not supported in adhoc
      if(scrollable && chartInfo instanceof VSChartInfo) {
         DefaultAxis[] lefts = evgraph.getAxesAt(Coordinate.LEFT_AXIS);
         DefaultAxis[] tops = evgraph.getAxesAt(Coordinate.TOP_AXIS);
         GraphBounds gbounds = new GraphBounds((VGraph) vobj, (VGraph) vobj, chartInfo);
         Rectangle2D vbounds = vobj.getBounds();
         Rectangle2D ebounds = gbounds.getPlotBounds();

         // resize grid, if the outer axis has no binding, then
         // we should resize the inner axis
         // the grid lines (between the subgraphcs) contains at least 2
         // (the left edge of chart and right edge or chart)
         if(facet && lefts.length > 0 && lefts[0].getGridLineCount() > 2) {
            for(int i = 0; i < lefts[0].getGridLineCount(); i++) {
               GridLine line = lefts[0].getGridLine(i);
               double lineY = line.getShape().getBounds2D().getY();
               yboundaries.add(vbounds.getHeight() - lineY);
            }
         }
         else if(GraphUtil.isVScrollable(vgraph, null)) {
            int cnt = GTool.getUnitCount(vgraph.getCoordinate(), Coordinate.LEFT_AXIS, false);
            double max = ebounds.getMaxY() + 1;
            double inc = getUnitSize(ebounds.getHeight() / Math.min(cnt, 100000));

            for(double v = ebounds.getY(); v < max; v += inc) {
               yboundaries.add(vbounds.getHeight() - v);
            }
         }

         // in ascending order
         Collections.reverse(yboundaries);

         // see comments above
         if(facet && tops.length > 0 && tops[0].getGridLineCount() > 2) {
            for(int i = 0; i < tops[0].getGridLineCount(); i++) {
               GridLine line = tops[0].getGridLine(i);
               xboundaries.add(line.getShape().getBounds2D().getX());
            }
         }
         else if(GraphUtil.isHScrollable(vgraph, null)) {
            int cnt = GTool.getUnitCount(vgraph.getCoordinate(), Coordinate.TOP_AXIS, false);
            double max = ebounds.getMaxX() + 1;
            double inc = getUnitSize(ebounds.getWidth() / Math.min(cnt, 100000));

            for(double v = ebounds.getX(); v < max; v += inc) {
               xboundaries.add(v);
            }
         }
      }

      // depending on the coord (e.g. GeoCoord), the boundaries may contain
      // duplicate values, which could cause problem in sub-graph resizing
      distinctList(xboundaries);
      distinctList(yboundaries);

      fmts = new InternalGraphFormats(egraph);
      shapesContainer = new Object2ObjectOpenHashMap<>();
      tooltipsContainer = new Object2ObjectOpenHashMap<>();

      // initialized the area, which is not called in super constructor,
      // this time the linkURI has not been initialized
      init();
      container = new SplitContainer(getRegions()[0].getBounds());
   }

   /**
    * Remove duplicate entries.
    */
   private void distinctList(List<Double> boundaries) {
      for(int i = 1; i < boundaries.size(); i++) {
         if(boundaries.get(i).equals(boundaries.get(i - 1))) {
            boundaries.remove(i--);
         }
      }
   }

   /**
    * Get a reasonable unit size for resizing.
    */
   private double getUnitSize(double size) {
      size = Math.max(size, 0.1);

      while(size < 10) {
         size *= 2;
      }

      return size;
   }

   /**
    * Get drop area id.
    * @return area id.
    */
   public int getDropType() {
      return ChartArea.DROP_REGION_PLOT;
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @throws IOException
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      container.writeData(output);
      super.writeData(output);
      output.writeDouble(layoutbounds.getX());
      output.writeDouble(layoutbounds.getY());
      output.writeDouble(layoutbounds.getWidth());
      output.writeDouble(layoutbounds.getHeight());

      output.writeInt(xboundaries.size());
      for(int i = 0; i < xboundaries.size(); i++) {
         output.writeDouble(xboundaries.get(i));
      }

      output.writeInt(yboundaries.size());
      for(int i = 0; i < yboundaries.size(); i++) {
         output.writeDouble(yboundaries.get(i));
      }

      output.writeInt(getDropType());
      lines.writeData(output);
   }

   /**
    * Get child areas.
    */
   @Override
   protected DefaultArea[] getChildAreas() {
      VGraph graph = (VGraph) vobj;
      List<DefaultArea> visuals = new ArrayList<>();
      Region region0 = getRegion();
      List<VisualObject> topvos = new ArrayList<>();

      for(int i = 0; i < graph.getVisualCount(); i++) {
         Visualizable v = graph.getVisual(i);

         if(v instanceof VisualObject) {
            if(v instanceof GraphVO) {
               VGraph vgraph = ((GraphVO) v).getVGraph();
               List<VisualObject> subvos = new ArrayList<>(GTool.getVOs(vgraph));
               processGraphVO(subvos, vgraph, graph, visuals, region0);
            }
            else {
               topvos.add(((VisualObject) v));
            }
         }
      }

      if(graph.isCancelled()) {
         return new DefaultArea[0];
      }

      processGraphVO(topvos, null, graph, visuals, region0);

      // use evgraph
      addRadarAxesArea(visuals, graph.getCoordinate());
      addRadarLabelAxisArea(visuals);

      DefaultArea[] areas = new DefaultArea[visuals.size()];

      // first to setRelPos() for childAreas, or initInexContainerAreas() will
      // cause error
      for(int i = 0; i < visuals.size(); i++) {
         DefaultArea area = visuals.get(i);
         area.setRelPos(getRelativePosition());
         areas[i] = area;
      }

      return areas;
   }

   /**
    * Get the relative position to the context.
    */
   private void processGraphVO(List<VisualObject> ovos, VGraph graph, VGraph tgraph,
                               List<DefaultArea> visuals, Region region0)
   {
      List<VisualObject> vos = generateSubShape(ovos);
      Coordinate coord = egraph.getCoordinate();
      int subcnt = 1; // number of sub-graphs

      if(coord instanceof FacetCoord) {
         Coordinate[][] coords = ((FacetCoord) coord).getExpandedInnerCoords();
         subcnt = coords.length > 0 ? coords.length * coords[0].length : 1;
      }

      Rectangle2D graphBounds = tgraph.getBounds();
      // true to include line points information
      boolean includeLines = lines.size() / subcnt > 1;
      Map pointValueMap = null; // maps point values to pointVO indexes in the vos list
      Map textValueMap = null; // maps text value indexes to textVO indexes in the vos list

      if(chartInfo != null && chartInfo.isCombinedToolTip() && chartInfo.getToolTip() == null &&
         !chartInfo.isMultiStyles())
      {
         pointValueMap = getRowValuePointMap(vos);
         textValueMap  = getRowValueTextMap(vos);
      }

      // points are sorted from large to small, reverse order of area so the
      // smaller point is selectable when overlapped with larger point
      for(int i = vos.size() - 1; i >= 0; i--) {
         Visualizable v = vos.get(i);

         if(!isEditable(v)) {
            continue;
         }

         if(graph != null && graph.isCancelled()) {
            break;
         }

         if(v instanceof ElementVO) {
            ElementVO elemVO = (ElementVO) v;

            VOText[] texts = elemVO.getVOTexts();
            addTextArea(visuals, texts, elemVO, graph, vos, textValueMap);

            // convert the line/area to points or 3d pie to sub section yet
            if(v instanceof LineVO || GraphUtil.supportSubSection(v)) {
               continue;
            }

            Hyperlink.Ref ref = getHyperlink(elemVO, -1, graph, false, null);
            ChartToolTip tooltip;

            if(pointValueMap == null) {
               tooltip = getToolTip(elemVO, -1, graph, false, null);
               moveTotalToBottom(tooltip);
            }
            else {
               tooltip = getMultiLineToolTip(vos, elemVO,  graph, pointValueMap);
            }

            int lineIdx = (elemVO instanceof ShapeElementVO && includeLines)
               ? ((ShapeElementVO) elemVO).getLineIndex() : -1;

            // added data should not be editable
            if(!isMapEditable(elemVO, tgraph)) {
               continue;
            }

            // topY is used for flipping pies. when legend is on top, we should
            // use the plot bounds since the vo is relative to the plot without
            // the top legend. (56853, 57019, 57482)
            boolean legendOnTop = egraph.getLegendLayout() == GraphConstants.TOP;
            boolean sunburst = chartInfo.getChartType() == GraphTypes.CHART_SUNBURST;
            double topY = legendOnTop && !(tgraph.getCoordinate() instanceof FacetCoord) && !sunburst
               ? tgraph.getBounds().getMaxY()
               : tgraph.getPlotBounds().getMaxY();
            VisualObjectArea varea = new VisualObjectArea(
               elemVO, trans, palette, region0.getBounds(), topY, lineIdx);
            boolean ignore = false;
            List<String> vars = new ArrayList<>();

            // ignore polygon hyperlink if both map point and polygon
            if(elemVO instanceof PolygonVO) {
               EGraph egraph = tgraph.getEGraph();

               for(int j = 0; j < egraph.getElementCount(); j++) {
                  GraphElement elem = egraph.getElement(j);
                  String var = elem.getVar(0);

                  if(elem instanceof PointElement && !vars.contains(var)) {
                     ignore = true;
                     break;
                  }
                  else {
                     vars.add(var);
                  }
               }
            }

            if(ref != null && !ignore) {
               varea.setHyperlink(ref);
               varea.setHyperlinkLabel(ref.getName());
            }

            HRef[] drefs = getDrillHyperlinks(elemVO, -1, false);

            if(drefs != null) {
               int cnt = drefs.length;
               String[] labels = new String[cnt];

               for(int k = 0; k < cnt; k++) {
                  HRef dref = drefs[k];
                  labels[k] = dref.getName();
               }

               varea.setDrillHyperlinks(drefs);
               varea.setDrillHyperlinkLabels(labels);
            }

            varea.setToolTip(tooltip);
            visuals.add(varea);

            Shape[] shapes = elemVO.getShapes();

            for(int j = 0; j < shapes.length; j++) {
               Region[] regions = varea.getRegions(shapes[j], true);

               for(Region region : regions) {
                  Shape nshape = getShapeFromRegion(region);
                  int r = elemVO.getRowIndex();
                  int c = elemVO.getColIndex();
                  addShape(r, c, nshape);
                  tooltipsContainer.put(new Point(r, c), varea.getToolTipString());

                  if(shapes[j] instanceof Donut) {
                     if(varea.getHyperlink() != null) {
                        dlinks.put(region.createShape(), varea.getHyperlink());
                     }

                     HRef[] drillHrefs = varea.getDrillHyperlinks();

                     if(drillHrefs != null && drillHrefs instanceof Hyperlink.Ref[]) {
                        drillLinks.put(region.createShape(), (Hyperlink.Ref[]) varea.getDrillHyperlinks());
                     }
                  }
               }
            }
         }
         else if(v instanceof LabelFormVO &&
                 ((LabelFormVO) v).getForm().getHint("target.index") != null)
         {
            visuals.add(new LabelArea((LabelFormVO) v, trans, palette));
         }
      }
   }

   /**
    * Get the relative position to the context.
    */
   private Point2D getRelativePosition() {
      // since the relative position means relative to the plot on context, and
      // the plot bounds is from GraphBounds, we should use the same here
      GraphBounds gbounds = new GraphBounds((VGraph) vobj, vgraph, chartInfo);
      Rectangle2D rect = gbounds.getPlotBounds();
      rect = GTool.transform(rect, trans);
      return new Point2D.Double(rect.getX(), rect.getY());
   }

   /**
    * Get the shape from region.
    */
   private Shape getShapeFromRegion(Region region) {
      Shape shape = null;

      if(region instanceof PolygonRegion) {
         shape = region.createShape();
      }
      else if(region instanceof RectangleRegion) {
         shape = region.createShape();
      }
      else if(region instanceof EllipseRegion) {
         shape = region.createShape();
      }
      else if(region instanceof AreaRegion) {
         shape = region.createShape();
      }

      return shape;
   }

   /**
    * Get linkable shapes. The key is a data point. The value is shapes.
    */
   public Map<Point, Set<Shape>> getShapes() {
      return shapesContainer;
   }

   /**
    * Get tooltips map. The key is a data point. The value is tooltip string.
    */
   public Map<Point, String> getTooltips() {
      return tooltipsContainer;
   }

   /**
    * Add a shape to the shape container.
    *
    * @param row the row of the data point.
    * @param col the column of the data point.
    * @param shape the shape to add.
    */
   private void addShape(int row, int col, Shape shape) {
      Point point = new Point(row, col);
      shapesContainer.computeIfAbsent(point, p -> new ObjectOpenHashSet<>()).add(shape);
   }

   /**
    * Get regions.
    */
   @Override
   public Region[] getRegions() {
      GraphBounds egbounds = new GraphBounds((VGraph) vobj, vgraph, chartInfo);
      Rectangle2D erect = egbounds.getPlotBounds();
      erect = GTool.transform(erect, trans);
      GraphBounds gbounds = new GraphBounds(vgraph, vgraph, chartInfo);
      Rectangle2D rect = gbounds.getPlotBounds();
      rect = GTool.transform(rect, GTool.getFlipYTransform(vgraph));
      rect = new Rectangle2D.Double(rect.getX(), rect.getY(), erect.getWidth(),
         erect.getHeight());
      return new Region[] {new RectangleRegion(rect)};
   }

   /**
    * Get data set from vgraph.
    */
   private DataSet getDataSet() {
      DataSet data = ((VGraph) vobj).getCoordinate().getDataSet();
      return data instanceof DataSetFilter ?
         ((DataSetFilter) data).getRootDataSet() : data;
   }

   /**
    * Get hyperlink of generated before with row and col index.
    */
   public Hyperlink.Ref getHyperlink(int row, int col) {
      Object obj = linkcache.get(row, col);
      return obj == SparseMatrix.NULL ? null : (Hyperlink.Ref) obj;
   }

   /**
    * Get hyperlink.
    */
   private Hyperlink.Ref getHyperlink(ElementVO elemVO, int col, VGraph graph,
                                      boolean text, VOText vtext)
   {
      String measure = GraphUtil.getHyperlinkMeasure(elemVO, text);

      if(measure == null) {
         return null;
      }

      if(chartInfo instanceof VSMapInfo) {
         measure = MapHelper.getGeoRefFullName((VSMapInfo) chartInfo, measure);
      }

      DataSet dataset = graph != null ? graph.getCoordinate().getDataSet()
         : vgraph.getCoordinate().getDataSet();
      int rowIndex = GraphUtil.getRowIndex(elemVO, col);
      int colIndex;
      int subidx = GraphUtil.getSubRowIndex(elemVO, col);
      GraphElement elem = ((ElementGeometry) elemVO.getGeometry()).getElement();
      boolean useMeasureCol = text ||
         dataset.getHeader(elemVO.getColIndex()).startsWith(IntervalDataSet.TOP_PREFIX);

      if(useMeasureCol && !measure.equals(elemVO.getMeasureName()) ||
         elem instanceof PointElement && ((PointElement) elem).isWordCloud())
      {
         colIndex = dataset.indexOfHeader(measure);
      }
      else {
         colIndex = elemVO.getColIndex();
      }

      if(colIndex < 0 || rowIndex < 0) {
         return null;
      }

      boolean treeContainer = false;

      if(elemVO instanceof TreemapVO) {
         List<String> current = Arrays.asList(((TreemapVO) elemVO).getCurrentTreeDims());
         TreemapElement tree = (TreemapElement) elem;

         // Check if container is a leaf node
         treeContainer = current.size() < tree.getTreeDimCount();
      }

      Object ref = linkcache.get(rowIndex, colIndex);

      // treemap container value is calculated in getTreemapContainerHyperlink
      if(!treeContainer && dataset instanceof AttributeDataSet) {
         if(ref == SparseMatrix.NULL) {
            ref = ((AttributeDataSet) dataset).getHyperlink(colIndex, subidx);

            if(ref instanceof Hyperlink.Ref) {
               final Hyperlink.Ref href = (Hyperlink.Ref) ref;

               if(isStackValue(elemVO, vtext) && text) {
                  setHyperlinkStackParameters((AttributeDataSet) dataset, (VSDataSet) getDataSet(),
                                              href, elemVO, measure);
               }

               if(chartInfo instanceof VSChartInfo) {
                  final VSChartInfo vsChartInfo = (VSChartInfo) this.chartInfo;

                  if(href.isSendSelectionParameters()) {
                     VSUtil.addSelectionParameter(href, vsChartInfo.getLinkSelections());
                  }

                  if(href.isSendReportParameters()) {
                     addLinkParameter(href, vsChartInfo.getLinkVarTable());
                  }
               }

               linkcache.set(rowIndex, colIndex, ref);

               return href;
            }
         }

         if(ref != null) {
            return (Hyperlink.Ref) ref;
         }
      }

      graph = graph == null ? (VGraph) vobj : graph;
      ref = linkcache.get(rowIndex, colIndex);

      if(ref == SparseMatrix.NULL) {
         if(treeContainer) {
            ref = getTreemapContainerHyperlink((TreemapVO) elemVO, rowIndex, colIndex,
                                               graph, measure);
         }
         else if(dataset instanceof AttributeDataSet) {
            ref = ((AttributeDataSet) dataset).getHyperlink(colIndex, subidx);
         }

         if(ref == null || ref == SparseMatrix.NULL) {
            if(isMapEditable(elemVO, graph)) {
               ref = getHyperlink0(measure, subidx, getDataSet(), dataset);
            }
         }

         linkcache.set(rowIndex, colIndex, ref);
      }

      return ref == null || ref == SparseMatrix.NULL ? null : (Hyperlink.Ref) ref;
   }

   private void setHyperlinkStackParameters(AttributeDataSet dataset, VSDataSet vsDataSet,
                                            HRef href, ElementVO elemVO, String measure)
   {
      int index = elemVO.getSubRowIndex();
      Enumeration<String> pnames = href.getParameterNames();
      VGraph graph = (VGraph) vobj;
      DataSet subset = vgraph == null ? null : vgraph.getCoordinate().getDataSet();

      while(pnames.hasMoreElements()) {
         String pname = pnames.nextElement();
         String header = vsDataSet.getHyperlink(measure).getParameterField(pname);
         DataRef ref = vsDataSet.getDataRef(header);

         if(ref instanceof XAggregateRef) {
            int idx = -1;

            if(subset != null) {
               idx = subset.indexOfHeader(header);
            }

            boolean useSub = idx >= 0;
            int[] dataIndexes = getStackTextRowIndexes(elemVO, index);

            if(useSub && subset instanceof SubDataSet) {
               dataIndexes = getSubDataIndexes((SubDataSet) subset, dataIndexes);
            }
            else {
               useSub = false;
            }

            double stackedValue = getStackValue(
               useSub ? subset : dataset, header, dataIndexes, null, graph.getEGraph());
            href.setParameter(pname, stackedValue);
         }
      }
   }

   private Hyperlink.Ref getTreemapContainerHyperlink(TreemapVO treeVO, int rowIndex, int colIndex,
                                                      VGraph graph, String measure)
   {
      List<String> current = Arrays.asList(treeVO.getCurrentTreeDims());
      TreemapElement tree = (TreemapElement)
         ((ElementGeometry) treeVO.getGeometry()).getElement();
      DataSet dset = graph.getCoordinate().getDataSet();
      VSDataSet vsDataSet = null;

      if(dset instanceof VSDataSet) {
         vsDataSet = (VSDataSet) dset;
      }
      else if(dset instanceof SubDataSet && ((SubDataSet) dset).getDataSet() instanceof VSDataSet) {
         vsDataSet = (VSDataSet) ((SubDataSet) dset).getDataSet();
         int rowFromBase = ((SubDataSet) dset).getRowFromBase(rowIndex);

         if(rowFromBase >= 0) {
            rowIndex = rowFromBase;
         }
      }
      else {
         return null;
      }

      Set<String> dims = new HashSet<>(Arrays.asList(tree.getDims()));
      Hyperlink link = vsDataSet.getHyperlink(measure);

      if(link == null) {
         return null;
      }

      Map<String, Object> map = new HashMap<>();
      map.put(measure, dset.getData(colIndex, rowIndex));

      for(int j = 0; j < dset.getColCount(); j++) {
         String col = dset.getHeader(j);
         String coltype = Tool.getDataType(dset.getType(col));

            // if it's a container of treemap, get the total of children for the hyperlink value
            if(!current.contains(col) && XSchema.isNumericType(coltype) && !dims.contains(col)) {
               double total = 0;
               final int row0 = rowIndex;
               List<Object> group = current.stream()
                  .map(c -> dset.getData(c, row0)).collect(Collectors.toList());

               for(int i = 0; i < dset.getRowCount(); i++) {
                  final int row2 = i;
                  List<Object> group2 = current.stream()
                     .map(c -> dset.getData(c, row2)).collect(Collectors.toList());

               if(!group.equals(group2)) {
                  continue;
               }

               Object val = dset.getData(col, row2);

               if(val instanceof Number) {
                  total += ((Number) val).doubleValue();
               }
            }

            Object obj = Tool.getData(coltype, total);
            map.put(col, obj);
         }
         else if(current.contains(col)) {
            map.put(col, dset.getData(col, rowIndex));
         }
      }

      HRef href = ((AttributeDataSet) dset).getHyperlink(colIndex, rowIndex);

      for(String paramName : link.getParameterNames()) {
         String field = link.getParameterField(paramName);

         if(!map.containsKey(field) && href.getParameter(paramName) != null) {
            map.put(field, href.getParameter(paramName));
         }
      }

      Hyperlink.Ref ref = new Hyperlink.Ref(link, map);

      if(chartInfo instanceof VSChartInfo) {
         if(ref.isSendReportParameters()) {
            addLinkParameter(ref, ((VSChartInfo) chartInfo).getLinkVarTable());
         }

         if(ref.isSendSelectionParameters()) {
            Hashtable<String, SelectionVSAssembly> selections = ((VSChartInfo) chartInfo).getLinkSelections();
            inetsoft.uql.viewsheet.internal.VSUtil.addSelectionParameter(ref, selections);
         }
      }

      return ref;
   }

   /**
    * Get hyperlink.
    */
   private Hyperlink.Ref getHyperlink0(String measure, int subridx, DataSet top, DataSet inner) {
      HRef ref = null;

      if(top instanceof BoxDataSet) {
         top = ((BoxDataSet) top).getDataSet();
         measure = BoxDataSet.getBaseName(measure);
      }

      if(top instanceof VSDataSet) {
         Hyperlink link = ((VSDataSet) top).getHyperlink(measure);
         inner = inner == null ? top : inner;
         ref = GraphUtil.getHyperlink(link, inner, subridx);
      }

      if(ref != null && (chartInfo instanceof VSChartInfo)) {
         if(((Hyperlink.Ref) ref).isSendReportParameters()) {
            addLinkParameter(((Hyperlink.Ref) ref), ((VSChartInfo) chartInfo).getLinkVarTable());
         }

         if(((Hyperlink.Ref) ref).isSendSelectionParameters()) {
            Hashtable<String, SelectionVSAssembly> selections = ((VSChartInfo) chartInfo).getLinkSelections();
            inetsoft.uql.viewsheet.internal.VSUtil.
                     addSelectionParameter((Hyperlink.Ref) ref, selections);
         }
      }

      return (Hyperlink.Ref) ref;
   }

   /**
    * Get drill hyperlink.
    */
   private HRef[] getDrillHyperlinks(ElementVO elemVO, int col, boolean text) {
      String measure = GraphUtil.getHyperlinkMeasure(elemVO, text);

      if(measure == null) {
         return null;
      }

      if(chartInfo instanceof VSMapInfo) {
         measure = MapHelper.getGeoRefFullName((VSMapInfo) chartInfo, measure);
      }

      HRef[] refs = null;
      DataSet data = getDataSet();

      if(data instanceof AttributeDataSet) {
         int index = GraphUtil.getRowIndex(elemVO, col);
         refs = ((AttributeDataSet) data).getDrillHyperlinks(measure, index);
      }

      return refs;
   }

   /**
    * Add hyperlink parameter.
    * @param hlink the hyperlink to be set parameters.
    * @param vtable the variable table from sand box.
    */
   private void addLinkParameter(Hyperlink.Ref hlink, VariableTable vtable) {
      if(vtable == null) {
         return;
      }

      Vector exists = new Vector();
      Enumeration pnames = hlink.getParameterNames();
      Enumeration vnames = vtable.keys();

      while(pnames.hasMoreElements()) {
         exists.addElement(pnames.nextElement());
      }

      while(vnames.hasMoreElements()) {
         String name = (String) vnames.nextElement();

         if(exists.contains(name)  || VariableTable.isContextVariable(name)) {
            continue;
         }

         try {
            hlink.setParameter(name, vtable.get(name));
         }
         catch(Exception e) {
         }
      }
   }

   /**
    * Get tool tip for the object.
    * @param evo the element visual object to get tool tip.
    * @param col the column of the indexes in the visual object, specified used
    *  for LineVO, AreaVO and Pie3DVO, others are -1.
    */
   private ChartToolTip getToolTip(ElementVO evo, int col, VGraph vgraph,
                                   boolean isText, VOText vtext)
   {
      return getToolTip(evo, col, vgraph, isText, vtext, false);
   }

   /**
    * Get tool tip for the object.
    * @param evo the element visual object to get tool tip.
    * @param col the column of the indexes in the visual object, specified used
    *  for LineVO, AreaVO and Pie3DVO, others are -1.
    */
   private ChartToolTip getToolTip(ElementVO evo, int col, VGraph vgraph,
                                   boolean isText, VOText vtext, boolean showFullTotalName)
   {
      ChartToolTip tooltip = new ChartToolTip();

      if(evo == null || !(vobj instanceof VGraph) || isLightWeight() ||
         !chartInfo.isTooltipVisible() ||
         !isText && !isMapEditable(evo, vgraph == null ? (VGraph) vobj : vgraph))
      {
         return tooltip;
      }

      if(GraphTypes.isContour(chartInfo.getChartType())) {
         return tooltip;
      }

      if(chartInfo instanceof RadarChartInfo && evo instanceof PointVO && !isText) {
         return tooltip;
      }

      VGraph graph = (VGraph) vobj;
      DataSet dataset = graph.getCoordinate().getDataSet();
      DataSet subset = vgraph == null ? null : vgraph.getCoordinate().getDataSet();
      Geometry geom = evo.getGeometry();

      if(dataset == null || !(geom instanceof ElementGeometry)) {
         return tooltip;
      }

      ElementGeometry egeom = (ElementGeometry) geom;
      GraphElement element = egeom.getElement();
      String[] dims;
      String[] measures;

      if(element instanceof PointElement && ((PointElement) element).isWordCloud()) {
         PointElement pointElement = (PointElement) element;

         if(pointElement.getTextFrame() != null) {
            dims = new String[]{pointElement.getTextFrame().getField()};
         }
         else {
            dims = new String[0];
         }

         if(pointElement.getSizeFrame() != null) {
            measures = new String[]{pointElement.getSizeFrame().getField()};
         }
         else  {
            measures = new String[0];
         }
      }
      else {
         dims = element.getDims();

         if(element instanceof MekkoElement) {
            dims = Arrays.copyOf(dims, dims.length + 1);
            dims[dims.length - 1] = ((MekkoElement) element).getInnerDimension();
         }

         measures = evo instanceof TreemapVO ? ((TreemapVO) evo).getCurrentTreeDims() :
            !GraphTypes.isGantt(chartInfo.getChartType()) ? egeom.getVars() : element.getVars();
      }

      final ChartDescriptor chartDesc = chartInfo.getChartDescriptor();
      PlotDescriptor plotDesc = chartDesc != null ? chartDesc.getPlotDescriptor() : null;

      if(fakeStackTextFrameMap.get(element) == null && isStackChart(null) &&
         plotDesc != null && !(plotDesc.isValuesVisible() && plotDesc.isStackValue() &&
         element instanceof StackableElement && element.isStack() &&
         ((StackableElement) element).isStackGroup()))
      {
         StackTextFrame fakeStackTextFrame = new StackTextFrame(element, measures);
         fakeStackTextFrame.init(dataset);
         fakeStackTextFrameMap.put(element, fakeStackTextFrame);
      }

      List<VSDataRef> list = new ArrayList<>();

      // scatter matrix should use the original measure instead of _Measure_Name and
      // _Measure_Value_
      if(dataset instanceof PairsDataSet) {
         measures = ((PairsDataSet) dataset).getMeasures().toArray(new String[0]);
      }
      else if(dataset instanceof IntervalDataSet) {
         measures = Arrays.stream(measures)
            .map(m -> GraphUtil.getOriginalCol(m)).toArray(String[]::new);
      }

      // for map, show geo refs rather than generated fields
      if(measures.length > 0) {
         String measure = measures[0];

         if(GraphUtil.isLatitudeField(measure)) {
            measures = new String[0];

            for(int i = 0; i < dims.length; i++) {
               if(GraphUtil.isLongitudeField(dims[i])) {
                  dims[i] = GraphUtil.getGeoField(dims[i]);

                  if(chartInfo instanceof VSMapInfo) {
                     dims[i] = MapHelper.getGeoRefFullName((VSMapInfo) chartInfo, dims[i]);
                  }
               }
            }
         }
      }

      String[] others = showOthers(dims) ?
         (String[]) element.getHint("_tip_columns_") : new String[0];
      int[] indexes = evo.getRowIndexes();
      int index;

      if(col == -1 || indexes.length <= col) {
         index = indexes[0];
      }
      else {
         index = indexes[col];
      }

      final int[] subRowIndexes = evo.getSubRowIndexes();
      final int subidx;

      if(subRowIndexes != null && col >= 0 && col < subRowIndexes.length) {
         subidx = subRowIndexes[col];
      }
      else {
         subidx = evo.getSubRowIndex();
      }

      // all
      // should show point for previous-value even if it's filled in by time series. (52568)
      if(index < 0 && !showNullData()) {
         return tooltip;
      }

      if(chartInfo != null) {
         /* should use top dataset so calc columns are available. (59477)
         while(dataset instanceof GeoDataSet || dataset instanceof MappedDataSet) {
            dataset = ((AbstractDataSetFilter) dataset).getDataSet();
         }

         while(subset instanceof GeoDataSet || subset instanceof MappedDataSet) {
            subset = ((AbstractDataSetFilter) subset).getDataSet();
         }
         */

         list.addAll(Arrays.asList(chartInfo.getRTXFields()));

         // scatter matrix should use the measures once (as the measures are repeated on x and y)
         if(dataset instanceof PairsDataSet) {
            for(ChartRef field : chartInfo.getRTYFields()) {
               if(!list.contains(field)) {
                  list.add(field);
               }
            }
         }
         else {
            list.addAll(Arrays.asList(chartInfo.getRTYFields()));
         }

         list.addAll(Arrays.asList(chartInfo.getRTGroupFields()));

         if(chartInfo.supportsPathField() && chartInfo.getRTPathField() != null) {
            list.add(chartInfo.getRTPathField());
         }

         // for multi-aesthetics, extract aesthetics from element instead
         // of chartinfo
         // keep old sequence, so will not cause BC problem
         List<VSDataRef> arefs = Arrays.asList(chartInfo.getAestheticRefs(true));
         list.addAll(arefs);

         if(chartInfo instanceof MapInfo) {
            list.addAll(Arrays.asList(((MapInfo) chartInfo).getRTGeoFields()));
         }
         else if(chartInfo instanceof CandleChartInfo) {
            list.add(((CandleChartInfo) chartInfo).getRTCloseField());
            list.add(((CandleChartInfo) chartInfo).getRTOpenField());
            list.add(((CandleChartInfo) chartInfo).getRTHighField());
            list.add(((CandleChartInfo) chartInfo).getRTLowField());
         }
         else if(chartInfo instanceof RelationChartInfo) {
            list.add(((RelationChartInfo) chartInfo).getRTSourceField());
            list.add(((RelationChartInfo) chartInfo).getRTTargetField());
         }
         else if(chartInfo instanceof GanttChartInfo) {
            list.add(((GanttChartInfo) chartInfo).getRTStartField());
            list.add(((GanttChartInfo) chartInfo).getRTEndField());
            list.add(((GanttChartInfo) chartInfo).getRTMilestoneField());
         }
      }

      while(list.remove(null)) {
         // remove nulls
      }

      // get all bound fields so we can ignore fake ("value") column
      Set<String> allfields = new HashSet<>();
      Set<String> excludeFields = new HashSet<>();
      Set<String> aesthetics = new HashSet<>();

      // if no binding (created from script), add all dims/vars
      if(list.isEmpty()) {
         allfields.addAll(Arrays.asList(dims));
         allfields.addAll(Arrays.asList(measures));
      }

      for(VSDataRef fld : list) {
         // if label is for entire stack, don't show aesthetic fields.
         if(fld instanceof AestheticRef && isText && isStackValue(evo, vtext)) {
            excludeFields.add(fld.getFullName());
            continue;
         }

         allfields.add(fld.getFullName());

         if(fld instanceof AestheticRef) {
            fld = (VSDataRef) ((AestheticRef) fld).getDataRef();
         }

         if(fld instanceof XAggregateRef) {
            XAggregateRef afld = (XAggregateRef) fld;
            String formula = afld.getFormula() != null ? afld.getFormula().getName() : null;

            if(isText && !"SUM".equals(formula) && !"COUNT DISTINCT".equals(formula) &&
               !"COUNT ALL".equals(formula) && isStackValue(evo, vtext))
            {
               excludeFields.add(fld.getFullName());
            }

               /* should support all calc for original data
               if(!(afld.getCalculator() instanceof PercentCalc)) {
                  continue;
               }
               */

            String fname = afld.getFullName();
            String oname = afld.getFullName(false);

            // don't create two entrie for discrete measure. (44296)
            if(fname.startsWith(ChartAggregateRef.DISCRETE_PREFIX)) {
               continue;
            }

            // only support percent calc
            if(!oname.equals(fname)) {
               full2NoCalcNames.put(fname, oname);
            }
         }
      }

      for(VisualFrame frame : element.getVisualFrames()) {
         if(frame != null) {
            String[] fields = frame.getField() == null ? null : new String[] {frame.getField()};

            if(frame instanceof CompositeColorFrame) {
               CompositeColorFrame cColorFrame = (CompositeColorFrame) frame;
               List<String> flds = new ArrayList<>();

               for(int i = 0; i < cColorFrame.getFrameCount(); i++) {
                  VisualFrame innerFrame = cColorFrame.getFrame(i);

                  if(innerFrame.getField() != null) {
                     flds.add(innerFrame.getField());
                  }
               }

               fields = flds.toArray(new String[0]);
            }
            else if(frame instanceof MultiFieldFrame) {
               fields = ((MultiFieldFrame) frame).getFields();
            }

            if(fields == null || fields.length < 1) {
               continue;
            }

            for(String field : fields) {
               if(field == null) {
                  continue;
               }

               if(field.startsWith(SumDataSet.ALL_HEADER_PREFIX)) {
                  field = field.substring(SumDataSet.ALL_HEADER_PREFIX.length());
               }

               aesthetics.add(field);
            }
         }
      }

      allfields.addAll(aesthetics);
      allfields.removeAll(excludeFields);
      Set<String> excludeDims = new HashSet<>();

      if(evo instanceof TreemapVO) {
         Collection<String> treedims = Arrays.asList(((TreemapVO) evo).getCurrentTreeDims());
         TreemapGeometry obj = (TreemapGeometry) evo.getGeometry();
         excludeDims = Arrays.stream(chartInfo.getRTGroupFields())
            .map(f -> f.getFullName())
            .filter(f -> !treedims.contains(f))
            .collect(Collectors.toSet());

         // color of container is inherited from the first child, so it's kind of
         // meaningless for container
         if(!obj.isLeaf()) {
            VisualFrame color = element.getColorFrame();
            VisualFrame size = element.getSizeFrame();
            VisualFrame texture = element.getTextureFrame();
            VisualFrame text = element.getTextFrame();

            if(color != null && color.getField() != null &&
               !dataset.isMeasure(color.getField()) &&
               !treedims.contains(color.getField()) &&
               !(color instanceof LinearColorFrame))
            {
               excludeDims.add(color.getField());
            }

            if(size != null && size.getField() != null &&
               !treedims.contains(size.getField()) &&
               !(size instanceof LinearSizeFrame))
            {
               excludeDims.add(size.getField());
            }

            if(texture != null && texture.getField() != null &&
               !treedims.contains(texture.getField()) &&
               !(texture instanceof LinearTextureFrame))
            {
               excludeDims.add(texture.getField());
            }

            if(text != null && text.getField() != null && !treedims.contains(text.getField()) &&
               !dataset.isMeasure(text.getField()))
            {
               excludeDims.add(text.getField());
            }
         }
      }
      else if(evo instanceof RelationVO) {
         String measure = evo.getMeasureName();
         ElementGeometry geo = (ElementGeometry) evo.getGeometry();
         RelationElement elem = (RelationElement) geo.getElement();
         String fromDim = elem.getSourceDim();

         // for root node, if target field is bound to aesthetic, don't include it in
         // tooltip (46464).
         if(Objects.equals(measure, fromDim)) {
            excludeDims.add(elem.getTargetDim());

            if(elem.getSizeFrame() != null) {
               excludeDims.add(elem.getSizeFrame().getField());
            }

            if(elem.getColorFrame() != null) {
               excludeDims.add(elem.getColorFrame().getField());
            }

            if(elem.getTextureFrame() != null) {
               excludeDims.add(elem.getTextureFrame().getField());
            }

            if(elem.getLineFrame() != null) {
               excludeDims.add(elem.getLineFrame().getField());
            }

            if(elem.getTextFrame() != null) {
               excludeDims.add(elem.getTextFrame().getField());
            }

            if(elem.getNodeColorFrame() != null) {
               excludeDims.add(elem.getNodeColorFrame().getField());
            }

            if(elem.getNodeSizeFrame() != null) {
               excludeDims.add(elem.getNodeSizeFrame().getField());
            }

            // allow source field on aesthetic (47326).
            excludeDims.remove(elem.getSourceDim());
         }
      }

      allfields.removeAll(excludeDims);

      if(chartInfo != null && chartInfo.getToolTip() != null &&
         chartInfo.getToolTip().length() > 0)
      {
         // @by stephenwebster, For Bug #21928
         // The call to distinctRefs is intentionally commented out as it causes a
         // mismatch between design time and runtime replacement indexes when using a
         // customized tooltip.  The original change to create a distinct list
         // was not driven by any real problem on bug1340047352355. Since users can
         // create expressions and variables causing this list to be dynamically sized
         // and unpredictable it should be better to include all binding fields
         // regardless of whether they are duplicates.  Backwards compatibility is a
         // concern, but acceptable. list = distinctRefs(list);

         List<Object> values = new ArrayList<>();
         Set<String> allHeaders = new HashSet<>();
         List<String> headers = new ArrayList<>();

         for(int i = 0; i < list.size(); i++) {
            DataRef ref = list.get(i);
            String header = ref instanceof VSDataRef ? ((VSDataRef) ref).getFullName()
               : ref.getName();
            int idx = -1;
            boolean useSub = false;

            if(GraphTypes.isBoxplot(chartInfo.getChartType()) && ref instanceof ChartAggregateRef) {
               header = BoxDataSet.MEDIUM_PREFIX + header;
            }

            if(subset != null) {
               idx = subset.indexOfHeader(header);
            }

            if(idx >= 0) {
               useSub = true;
            }
            else {
               idx = dataset.indexOfHeader(header);
            }

            int dataIndex = useSub ? subidx : index;

            if(!isCurrentMeasure(header, evo, isText ? vtext : null, egeom, dataset)) {
               excludeDims.add(header);
            }

            if(!allHeaders.contains(header)) {
               allHeaders.add(header);
               headers.add(header);

               // For Bug #2146, stop-gap solution for getting stacked value
               // in a custom tooltip on the "Show Value" text field for aggregate
               // fields. Note: The original stack value logic doesn't really
               // take into consideration the type of aggregation, so this is not
               // working unless you are using a SUM.
               if(isStackValue(evo, vtext) && isText) {
                  if(ref instanceof XAggregateRef) {
                     Format fmt = fmts.getFormat(header);
                     int[] dataIndexes = getStackTextRowIndexes(evo, index);

                     if(useSub && subset instanceof SubDataSet) {
                        dataIndexes = getSubDataIndexes((SubDataSet) subset, dataIndexes);
                     }
                     else {
                        useSub = false;
                     }

                     values.add(getStackValue(
                        useSub ? subset : dataset, header, dataIndexes, fmt, graph.getEGraph()));
                  }
                  else {
                     if(Arrays.asList(dims).contains(header) ||
                        Arrays.asList(measures).contains(header))
                     {
                        values.add(idx < 0 ? null :
                                      (useSub ? subset.getData(idx, dataIndex)
                                       : dataset.getData(idx, dataIndex)));
                     }
                     else {
                        values.add(null);
                     }
                  }
               }
               else {
                  Object value = idx < 0 ? null : getData(useSub ? subset : dataset,
                     header, dataIndex, evo, graph.getEGraph());

                  if(value instanceof DCMergeCell) {
                     value = ((DCMergeCell) value).getOriginalData();
                  }

                  values.add(value);
               }
            }
         }

         String tipfmt = chartInfo.getToolTip();
         List<Object> fields = new ArrayList<>();
         fields.addAll(Arrays.asList(dims));
         fields.addAll(Arrays.asList(measures));
         fields.addAll(aesthetics);

         if(chartInfo.getPathField() != null) {
            fields.add(chartInfo.getPathField().getName());
         }

         ChartRef[] groups = chartInfo.getGroupFields();

         if(groups != null) {
            Arrays.stream(groups).map(group -> group.getFullName()).forEach(fields::add);
         }

         // @by ChrisSpagnoli bug1412008160666 #2 2014-10-28
         // Convert any text references in tooltip format string to numeric
         tipfmt = VSUtil.convertTipFormatToNumeric(tipfmt, headers);
         tipfmt = tipfmt.replace("<br>", "\n");
         tipfmt = getValidTipFormat(tipfmt, headers, fields, excludeDims);

         // avoid null in tip
         for(int i = 0; i < values.size(); i++) {
            // if the parameter is formatted (e.g. {0,number,##,#%}), dont
            // change it to a string, otherwise there is a format exception
            if(values.get(i) == null && tipfmt.contains("{" + i + "}")) {
               values.set(i, "");
            }
            // @by stephenwebster, For Bug #6623
            // Content of custom tooltips is HTML, so we should encode the
            // string values to be safe for HTML.
            else if(values.get(i) instanceof String) {
               values.set(i, Tool.encodeHTML((String) values.get(i)));
            }
         }

         try {
            java.text.MessageFormat fmt = getFormat(tipfmt);
            tooltip.setCustomToolTip(
               fmt != null ? fmt.format(values.toArray(new Object[0])) : null);
         }
         catch(RuntimeException ex) {
            if(ex instanceof IllegalArgumentException &&
                ex.getMessage().startsWith("can't parse argument number"))
            {
               throw new RuntimeException(Catalog.getCatalog().
                  getString("viewer.viewsheet.chart.tooltip"));
            }
            else if(ex.getMessage().startsWith("unknown format type at")) {
               throw new RuntimeException(ex.getMessage() +
                  chartInfo.getToolTip());
            }
            else if(ex instanceof IllegalArgumentException) {
               String message = Catalog.getCatalog().getString(
                       "viewer.viewsheet.chart.tooltip.formatIssue", chartName);
               tooltip.setCustomToolTip(message);
               Tool.addUserWarning(message + ": " + ex.getMessage());
            }
            else {
               tooltip.setCustomToolTip(Catalog.getCatalog().
                       getString("viewer.viewsheet.chart.tooltip.error"));
               Tool.addUserWarning(Catalog.getCatalog().
                       getString("viewer.viewsheet.chart.tooltip.error") + ": " + ex.getMessage());
            }
         }
      }
      else {
         String[][] cols = new String[][] {dims, measures, others};
         HashSet<String> added = new HashSet<>();
         Object[] arr = new Object[list.size()];

         for(int i = 0; i < arr.length; i++) {
            DataRef ref = list.get(i);
            String key = GraphUtil.getName(ref);

            if(!tips.containsKey(key)) {
               String value = getTipsValue(ref);
               tips.put(key, value);
            }
         }

         for(int k = 0; k < cols.length; k++) {
            if(cols[k] == null) {
               continue;
            }

            if(isText && isStackValue(evo, vtext) && cols[k] == others) {
               continue;
            }

            for(int i = 0; i < cols[k].length; i++) {
               String name = cols[k][i];
               String nameNoCalc = full2NoCalcNames.get(name);
               boolean useSub = false;

               if(name == null || name.trim().length() == 0) {
                  continue;
               }

               // for radar, only include dimension value for this point.
               if(k == 0 && evo instanceof ShapeElementVO) {
                  if(((ShapeElementVO) evo).isIgnoredDim(name)) {
                     continue;
                  }
               }

               if(isText && isIgnoreDim(name, evo, col)) {
                  continue;
               }

               if(!allfields.contains(name) && !allfields.contains(BoxDataSet.getBaseName(name))) {
                  continue;
               }

               // Bug #59194, don't trim the header name which may cause a column not found
               // exception.
               // name = name.trim();

               if(subset != null && subset.indexOfHeader(name) >= 0) {
                  useSub = true;
               }

               if(added.contains(name)) {
                  continue;
               }

               added.add(name);

               Format fmt = fmts.getFormat(name);
               String label = "";
               String value = "";
               String ovalue = null;

               if(isText && isStackValue(evo, vtext) && cols[k] == measures) {
                  HashMap<String, String> map = getStackTooltipValues(
                     evo, dataset, subset, useSub, index, subidx, name, nameNoCalc, fmt);
                  label = map.get("label");
                  value = map.get("value");
                  name = getStackTotalName(showFullTotalName ? name : null);
                  tooltip.setStackTotalName(name);

                  if(nameNoCalc != null) {
                     ovalue = map.get("ovalue");
                  }
               }
               else if(isMapEditable(evo, vgraph == null ? (VGraph) vobj : vgraph)) {
                  label = useSub ? getDataString(subset, name, subidx, fmt, false, evo, egraph, null) :
                     getDataString(dataset, name, index, fmt, false, evo, egraph, null);
                  value = useSub ? getDataString(subset, name, subidx, fmt, true, evo, egraph, null) :
                     getDataString(dataset, name, index, fmt, true, evo, egraph, null);

                  if(nameNoCalc != null) {
                     ovalue = useSub ?
                        getDataString(subset, nameNoCalc, subidx, fmt, true, evo, egraph, null) :
                        getDataString(dataset, nameNoCalc, index, fmt, true, evo, egraph, null);
                  }
               }

               addTooltip(label, name, value, nameNoCalc, ovalue, list, vtext, tooltip);

               if(cols[k] == measures && isStackChart(name) &&
                  (!isText || !isStackValue(evo, vtext)))
               {
                  HashMap<String, String> map = getStackTooltipValues(
                     evo, dataset, subset, useSub, index, subidx, name, null, fmt);
                  label = map.get("label");
                  value = map.get("value");
                  name = getStackTotalName(showFullTotalName ? name : null);
                  tooltip.setStackTotalName(name);

                  addTooltip(label, name, value, null, null, list, vtext, tooltip);
               }
            }
         }

         // add count as tooltip per MF requirement. may want t omake it more generic
         // when the graph type is available on gui.
         if(evo instanceof ParaboxPointVO) {
            Object weight = ((ParaboxPointGeometry) evo.getGeometry()).getWeight();
            tooltip.addTooltip(palette.put("Count"), palette.put(weight + ""));
         }

         VisualModel vmodel = egeom.getVisualModel();
         boolean containsPolygon = GraphUtil.containsMapPolygonField(chartInfo);
         boolean containsPoint = GraphUtil.containsMapPointField(chartInfo);
         String dim = dims.length == 0 ? null : dims[0];
         boolean isPolygon = chartInfo instanceof MapInfo &&
            GraphUtil.isPolygonField((MapInfo) chartInfo, dim);
         boolean ignore = isPolygon && containsPolygon && containsPoint;

         if(vmodel != null && !ignore && !(isText && isStackValue(evo, vtext))) {
            if(subset != null && subidx >= 0) {
               appendInfo(vmodel, tooltip, subidx, subset, added, aesthetics, evo, excludeDims);
            }
            else {
               appendInfo(vmodel, tooltip, index, dataset, added, aesthetics, evo, excludeDims);
            }
         }
      }

      HRef ref = getHyperlink(evo, col, vgraph, false, null);

      if(ref != null) {
         String refTip = ref.getToolTip();

         if(refTip != null && refTip.length() > 0) {
            int key = palette.put(Catalog.getCatalog().getString("Hyperlink"));
            int val = palette.put(Tool.localize(refTip));
            tooltip.addTooltip(key, val);
         }
      }

      return tooltip;
   }

   /**
    * @param evo the element visual object to get tool tip.
    * @param dataset dataset of the graph.
    * @param subset  sub dataset of the graph.
    * @param useSub  true if should use sub dataset to get the tooltip data.
    * @param index   the row index of the target element for specific column.
    * @param name    the column name.
    * @param nameNoCalc   the original name for tip.
    * @param fmt     fmt of specific column.
    * @return
    */
   private HashMap<String, String> getStackTooltipValues(ElementVO evo, DataSet dataset,
                                                         DataSet subset, boolean useSub, int index,
                                                         int subIndex, String name, String nameNoCalc,
                                                         Format fmt)
   {
      int[] dataIndexes = null;
      GraphElement elem = ((ElementGeometry) evo.getGeometry()).getElement();

      if(getStackTextFrame(evo) == null && fakeStackTextFrameMap.get(elem) != null) {
         dataIndexes = fakeStackTextFrameMap.get(elem).getStackRowIndexes(index);
      }
      else {
         dataIndexes = getStackTextRowIndexes(evo, index);
      }

      if(useSub && subset instanceof SubDataSet) {
         dataIndexes = getSubDataIndexes((SubDataSet) subset, dataIndexes);
      }
      else {
         useSub = false;
      }

      String label = useSub ?
         getDataString(subset, name, dataIndexes, subIndex, fmt, false, evo, egraph) :
         getDataString(dataset, name, dataIndexes, index, fmt, false, evo, egraph);
      String value = useSub ?
         getDataString(subset, name, dataIndexes, subIndex, fmt, true, evo, egraph) :
         getDataString(dataset, name, dataIndexes, index, fmt, true, evo, egraph);
      HashMap<String, String> result = new HashMap<>();
      result.put("label", label);
      result.put("value", value);

      if(nameNoCalc != null) {
         String ovalue = useSub ?
            getDataString(subset, nameNoCalc, dataIndexes, null, true, evo, egraph, false) :
            getDataString(dataset, nameNoCalc, dataIndexes, null, true, evo, egraph, false);
         result.put("ovalue", ovalue);
      }

      return result;
   }

   private void addTooltip(String label, String name, String value, String oname, String ovalue,
                           List<VSDataRef> list, VOText vtext, ChartToolTip tooltip)
   {
      final List<Legend> legends = getLegends(name);
      AxisDescriptor desc = getAxisDescriptor(name);
      Object[] arr = new Object[list.size()];
      boolean legendAliasReplacedLabel = false;

      if(legends.size() > 0) {
         final String legendValueAlias = getLegendValueAlias(legends, value);

         if(legendValueAlias != null && !legendValueAlias.equals(label)) {
            label = legendValueAlias;
            legendAliasReplacedLabel = true;
         }

         final String legendColumnAlias = getLegendColumnAlias(legends, name);

         if(legendColumnAlias != null) {
            name = legendColumnAlias;
         }
      }

      if(desc != null && !legendAliasReplacedLabel) {
         String flabel = desc.getLabelAlias(value);
         label = flabel == null ? label : flabel;
      }

      label = reviseTipString(arr, list, label, name);

      // when showing labels for all shapes (including states with no data), we just
      // use the shape label. (49222)
      if(label.isEmpty() && vtext != null && GraphUtil.isLatitudeField(vtext.getMeasureName()) &&
         // should not use label if label composed of others. (61864)
         !(vtext.getGraphElement().getTextFrame() instanceof MultiTextFrame))
      {
         label = vtext.getText();
      }

      name = tips.get(name) == null ? name : tips.get(name);
      int key = palette.put(name);
      int val = palette.put(label);
      tooltip.addTooltip(key, val);

      if(ovalue != null) {
         key = palette.put(Tool.localize(oname));
         val = palette.put(ovalue);
         tooltip.addTooltip(key, val);
      }
   }

   private String getStackTotalName(String measureName) {
      return measureName == null ? STACK_TOTAL : STACK_TOTAL + " " + measureName;
   }

   // check if point corresponding to null value should show tooltip.
   private boolean showNullData() {
      return Arrays.stream(chartInfo.getAggregateRefs())
         .anyMatch(a -> {
            Calculator calc = ((ChartAggregateRef) a).getCalculator();
            return calc != null && (calc.getType() == Calculator.RUNNINGTOTAL ||
               calc.getType() == Calculator.VALUE);
         });
   }

   private ChartToolTip getMultiLineToolTip(List<VisualObject> vos, ElementVO evo, VGraph vgraph,
                                             Map<String, List<Integer>> rowIndexMap)
   {
      boolean showFullTotalName = showMeasureTotal();
      ChartToolTip resultTooltip =
         getToolTip(evo, -1, vgraph, false, null, showFullTotalName);
      Set<String> stackTotalNames = new HashSet<>();

      if(!StringUtils.isEmpty(resultTooltip.getStackTotalName())) {
         stackTotalNames.add(resultTooltip.getStackTotalName());
      }

      int ridx = evo.getRowIndex();
      ElementGeometry egeom = (ElementGeometry)evo.getGeometry();
      GraphElement element = egeom.getElement();
      String[] dims = element.getDims();
      String rowValue = getRowValueString(dims, ridx);
      int[] dimIndexes = new int[dims.length];

      for(int i = 0; i < dims.length; i ++) {
         dimIndexes[i] = palette.put(dims[i]);
      }

      List<Integer> pidxs = rowIndexMap.get(rowValue);

      if(pidxs != null) {
         int tooltipCount = 1;
         ChartToolTip tempTip;

         for(Integer pidx : pidxs) {
            ShapeElementVO vo = (ShapeElementVO) vos.get(pidx);

            if(vo.getColIndex() == evo.getColIndex() && vo.getRowIndex() == evo.getRowIndex()) {
               continue;
            }

            tempTip = getToolTip(vo, -1, vgraph, false, null, showFullTotalName);

            for(int dimIndex : dimIndexes) {
               tempTip.removeTooltip(dimIndex); //Remove the dimension data to reduce redundancy
            }

            if(!StringUtils.isEmpty(tempTip.getStackTotalName())) {
               stackTotalNames.add(tempTip.getStackTotalName());
            }

            resultTooltip.appendTooltips(tempTip);
            tooltipCount++;

            if(tooltipCount > 5 || !stackTotalNames.isEmpty() && tooltipCount > 4) {
               break;
            }
         }
      }

      // 1. remove duplicated stack total tooltip,
      // 2. move the stack total to bottom of the tooltip.
      if(!stackTotalNames.isEmpty()) {
         Iterator it = stackTotalNames.iterator();
         ChartToolTip tip = new ChartToolTip();

         while(it.hasNext()) {
            String name = (String) it.next();
            int key = palette.put(name);
            int val = resultTooltip.getTooltipValue(key);
            tip.addTooltip(key, val);
            resultTooltip.removeTooltip(key);
         }

         resultTooltip.appendTooltips(tip);
      }

      return resultTooltip;
   }

   private ChartToolTip getMultiLineTextToolTip(List<VisualObject> vos, ElementVO evo,
                                                VGraph subgraph, int[] textIndexes,
                                                int col, VOText vtext)
   {
      boolean showFullTotalName = showMeasureTotal();
      ChartToolTip resultTooltip =
         getToolTip(evo, col, subgraph, true, vtext, showFullTotalName);
      Set<String> stackTotalNames = new HashSet<>();

      if(!StringUtils.isEmpty(resultTooltip.getStackTotalName())) {
         stackTotalNames.add(resultTooltip.getStackTotalName());
      }

      int tooltipCount = 0;
      int lineIndex = vos.indexOf(evo);

      String[] dims = vtext.getGraphElement().getDims();
      int[] dimIndexes = new int[dims.length];

      for(int i = 0; i < dims.length; i ++) {
         dimIndexes[i] = palette.put(dims[i]);
      }

      ChartToolTip tempTip;
      ElementVO tempVO;

      for(int i = 0; i < textIndexes.length; i ++) {
         if(i == lineIndex || textIndexes[i] == -1) {
            continue;
         }

         tempVO = (ElementVO) vos.get(i);

         VOText voText = tempVO.getVOTexts()[textIndexes[i]];
         tempTip =
            getToolTip(tempVO, textIndexes[i], subgraph, true, voText, showFullTotalName);

         for(int dimIndex : dimIndexes) {
            tempTip.removeTooltip(dimIndex); //Remove the dimension data to reduce redundancy
         }

         if(!StringUtils.isEmpty(tempTip.getStackTotalName())) {
            stackTotalNames.add(tempTip.getStackTotalName());
         }

         resultTooltip.appendTooltips(tempTip);
         tooltipCount ++;

         if(tooltipCount > 5) {
            break;
         }
      }

      // 1. remove duplicated stack total tooltip,
      // 2. move the stack total to bottom of the tooltip.
      if(!stackTotalNames.isEmpty()) {
         Iterator it = stackTotalNames.iterator();
         ChartToolTip tip = new ChartToolTip();
         Set<Integer> added = new HashSet<>();

         while(it.hasNext()) {
            String name = (String) it.next();
            int key = palette.put(name);
            int val = resultTooltip.getTooltipValue(key);
            resultTooltip.removeTooltip(key, true);
            resultTooltip.addTooltip(key, val);
         }
      }

      return resultTooltip;
   }

   private Map<String, ArrayList<Integer>> getRowValuePointMap(List<VisualObject> vos) {
      Map rowIndexMap = new HashMap<String, ArrayList<Integer>>();

      for(int i = 0; i < vos.size(); i ++) {
         VisualObject vo = vos.get(i);

         if(!(vo instanceof ShapeElementVO)) {
            continue;
         }

         ElementGeometry egeom = (ElementGeometry)((ShapeElementVO)vo).getGeometry();
         GraphElement element = egeom.getElement();
         String[] dims = element.getDims();
         int ridx = ((ShapeElementVO) vo).getRowIndex();
         String xValue = getRowValueString(dims, ridx);

         if(rowIndexMap.containsKey(xValue)) {
            ((ArrayList)rowIndexMap.get(xValue)).add(i);
         }
         else {
            ArrayList list = new ArrayList<Integer>();
            list.add(i);
            rowIndexMap.put(xValue, list);
         }
      }

      return rowIndexMap;
   }

   private Map<String, int[]> getRowValueTextMap(List<VisualObject> vos) {
      Map<String, int[]> rowIndexMap = new HashMap<>();
      int voTextOwners = 0;

      for(int i = 0; i < vos.size(); i ++) {
         VisualObject vo = vos.get(i);

         if(vo instanceof ElementVO && ((ElementVO) vo).getVOTexts().length > 0) {
            voTextOwners = i + 1;
         }
      }

      for(int i = 0; i < voTextOwners; i ++) {
         VisualObject vo = vos.get(i);

         if(!(vo instanceof ElementVO && ((ElementVO) vo).getVOTexts().length > 0)) {
            continue;
         }

         VOText[] voTexts = ((ElementVO) vo).getVOTexts();

         for(int j = 0; j < voTexts.length; j ++) {
            if(voTexts[j] == null) {
               continue;
            }

            String[] dims = voTexts[j].getGraphElement().getDims();
            int ridx = ((ElementVO) vo).getRowIndexes()[j];
            String value = getRowValueString(dims, ridx);
            int[] indexArray;

            if(rowIndexMap.containsKey(value)) {
               indexArray = rowIndexMap.get(value);
            }
            else {
               indexArray = new int[voTextOwners];
               Arrays.fill(indexArray, -1);
               rowIndexMap.put(value, indexArray);
            }

            indexArray[i] = j;
         }
      }

      return rowIndexMap;
   }

   private String getRowValueString(String[] dims, int ridx) {
      StringBuilder str = new StringBuilder();

      for(String dim : dims) {
         Object data = egraph.getCoordinate().getDataSet().getData(dim, ridx);

         if(data != null) {
            str.append(data.toString());
         }
      }

      return str.toString();
   }

   /**
    * Get sub data row indexes by the base data row indexes.
    * @param dataIndexes the base data row indexes.
    */
   private int[] getSubDataIndexes(SubDataSet subset, int[] dataIndexes) {
      if(subset == null || dataIndexes == null || dataIndexes.length == 0) {
         return dataIndexes;
      }

      int[] subDataIndexes = new int[dataIndexes.length];

      for(int i = 0; i < dataIndexes.length; i++) {
         int r = subset.getRowFromBase(dataIndexes[i]);
         subDataIndexes[i] = r >= 0 ? r : dataIndexes[i];
      }

      return subDataIndexes;
   }

   private StackTextFrame getStackTextFrame(ElementVO evo) {
      GraphElement elem = ((ElementGeometry) evo.getGeometry()).getElement();
      TextFrame frame = elem.getTextFrame();

      if(frame instanceof ValueTextFrame) {
         frame = ((ValueTextFrame) frame).getStackTextFrame();
      }

      return frame instanceof StackTextFrame ? (StackTextFrame) frame : null;
   }

   /**
    * Get stack text row indexes.
    */
   private int[] getStackTextRowIndexes(ElementVO evo, int index) {
      TextFrame frame = getStackTextFrame(evo);

      if(frame instanceof StackTextFrame) {
         StackTextFrame stackFrame = (StackTextFrame) frame;
         return stackFrame.getStackRowIndexes(index);
      }

      return new int[] {index};
   }

   /**
    * get valid tip format
    * @param tipfmt the tooltip for chart info
    * @param allHeaders all the bound fields for chart info
    * @param fields Fields included in ElementVO
    */
   private String getValidTipFormat(String tipfmt, List<String> allHeaders, List<Object> fields,
                                    Set<String> excludeDims)
   {
      StringBuilder newTipFmts = new StringBuilder();
      Matcher matcher = VALID_TIP_FORMAT_PATTERN.matcher(tipfmt);
      Matcher lineMatcher = Pattern.compile(".*\n").matcher(tipfmt);
      int lineStart = 0;
      int lineEnd = 0;

      while(matcher.find()) {
         while(matcher.start() >= lineEnd) {
            if(lineMatcher.find()) {
               lineStart = lineMatcher.start();
               lineEnd = lineMatcher.end();

               if(matcher.start() >= lineEnd) {
                  newTipFmts.append(lineMatcher.group(0));
               }
            }
            else {
               lineStart = lineEnd;
               lineEnd = tipfmt.length();
            }
         }

         String str1 = tipfmt.substring(lineStart, matcher.start());

         if(matcher.end() > lineEnd) {
            lineEnd = tipfmt.length();
         }

         String str2 = tipfmt.substring(matcher.end(), lineEnd);

         String format = matcher.group(0);
         String columnIndex = matcher.group(1);

         int num = -1;

         try {
            // MessageFormats only allow indices from 0-9.
            num = Integer.parseInt(columnIndex);
         }
         catch(NumberFormatException ex) {
            // didn't match valid MessageFormat format, ignore
         }

         if(num == -1 || num < allHeaders.size() && fields.contains(allHeaders.get(num))) {
            if(num >= 0 && excludeDims.contains(allHeaders.get(num))) {
               continue;
            }

            newTipFmts.append(str1);
            newTipFmts.append(format, format.indexOf(TOOLTIP_TAG) + 1,
               format.lastIndexOf(TOOLTIP_TAG));
            newTipFmts.append(str2);
         }
      }

      if(lineEnd >= 0 && lineEnd != tipfmt.length()) {
         newTipFmts.append(tipfmt.substring(lineEnd));
      }

      if(newTipFmts.length() > 0 && newTipFmts.charAt(newTipFmts.length() - 1) == '\n') {
         newTipFmts.deleteCharAt(newTipFmts.length() - 1);
      }

      return newTipFmts.toString();
   }

   /**
    * If the map bind multi layer geo column, only the highest layer
    * show the tooltip with others filed.
    */
   private boolean showOthers(String[] dims) {
      // boxplot tips handles with special logic in getTooltip
      if(GraphTypes.isBoxplot(chartInfo.getChartType())) {
         return false;
      }

      if(!(chartInfo instanceof MapInfo)) {
         return true;
      }

      MapInfo minfo = (MapInfo) chartInfo;
      ChartRef[] refs = minfo.getGeoFields();

      // Bug #59196, no layers to check when there is no geo fields so just show the tooltip
      if(refs.length == 0) {
         return true;
      }

      int maxLayer = Integer.MIN_VALUE;
      ChartRef mref = null;

      for(ChartRef ref:refs) {
         int layer = ((GeoRef) ref).getGeographicOption().getLayer();

         if(layer >= maxLayer) {
            maxLayer = layer;
            mref = ref;
         }
      }

      if(mref != null) {
         for(String dim:dims) {
            if(dim.equals(mref.getFullName())) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Get the message format.
    */
   private java.text.MessageFormat getFormat(String pattern) {
      // cache the format instances for better performance
      java.text.MessageFormat fmt = fmap.get(pattern);

      if(!fmap.containsKey(pattern)) {
         Locale locale = Catalog.getCatalog().getLocale();

         if(locale == null) {
            locale = Locale.getDefault();
         }

         try {
            fmt = new java.text.MessageFormat(pattern, locale);
            int i = 0;

            // @by stephenwebster, Fix bug1398279992279
            // Wrap the number format in our extended format
            // so that tooltips can support it.
            // @by stephenwebster, For bug1428672575736, add
            // support for ExtendedDateFormat
            for(Format fm : fmt.getFormats()) {
               if(fm instanceof DecimalFormat) {
                  String subpattern = ((DecimalFormat)fm).toPattern();
                  fmt.setFormat(i, new ExtendedDecimalFormat(subpattern,
                                                             DecimalFormatSymbols.getInstance(locale)));
               }
               else if(fm instanceof SimpleDateFormat) {
                  String subpattern = ((SimpleDateFormat)fm).toPattern();
                  fmt.setFormat(i, new ExtendedDateFormat(subpattern, locale));
               }

               i++;
            }
         }
         catch(IllegalArgumentException ex) {
            // ignore if not valid format
         }

         fmap.put(pattern, fmt);
      }

      return fmt;
   }

   /**
    * Get tooltip base on different dataref.
    */
   private String getTipsValue(DataRef ref) {
      if(ref instanceof AbstractAestheticRef) {
         ref = ((AbstractAestheticRef) ref).getDataRef();
      }

      if(!(ref instanceof XAggregateRef)) {
         return Tool.localize(GraphUtil.getCaption(ref));
      }

      XAggregateRef aref = (XAggregateRef) ref;

      if(aref.getCalculator() instanceof PercentCalc) {
         return Tool.localize(aref.getFullName(false));
      }

      return Tool.localize(GraphUtil.getCaption(ref));
   }

   /**
    * Revise the tooltip label.
    */
   private String reviseTipString(Object[] arr, List list, String label, String name) {
      for(int j = 0; j < arr.length; j++) {
         DataRef ref = (DataRef) list.get(j);

         if(ref instanceof AbstractAestheticRef) {
            ref = ((AbstractAestheticRef) ref).getDataRef();
         }

         if(!(ref instanceof XAggregateRef)) {
            continue;
         }

         XAggregateRef aref = (XAggregateRef) ref;

         if(!aref.getFullName().equals(name) ||
            !(aref.getCalculator() instanceof PercentCalc))
         {
            continue;
         }

         String prefix = aref.getCalculator().getPrefix();
         // change the prefix, such as: % of total:_--> % of total
         prefix = prefix.substring(0, prefix.length() - 2);

         if(label.endsWith("%")) {
            label = label.substring(0, label.length() - 1) + prefix;
            return label;
         }

         if(prefix.startsWith("%")) {
            try {
               Double value = Double.parseDouble(label);
               label = value * 100 + prefix;

               return label;
            }
            catch(IllegalArgumentException ex) {
            }
         }

         label += prefix;
         return label;
      }

      return label;
   }

   /**
    * Show stack measure or not.
    */
   private boolean isShowStackMeasure(ChartInfo chartInfo) {
      ChartDescriptor chartDescriptor = chartInfo.getChartDescriptor();

      if(chartDescriptor == null || chartDescriptor.getPlotDescriptor() == null) {
         return false;
      }

      return chartDescriptor.getPlotDescriptor().isStackMeasures();
   }

   /**
    * Show stack value or not.
    */
   private boolean isStackValue(ElementVO vo, VOText vtext) {
      GraphElement elem = (GraphElement) vo.getGraphable();

      if(elem == null) {
         return false;
      }

      return elem.getTextFrame() instanceof StackTextFrame ||
         vtext != null && vtext.isStacked();
   }

   /**
    * Get axis descriptor from this col.
    */
   private AxisDescriptor getAxisDescriptor(String col) {
      if(chartInfo == null) {
         return null;
      }

      if(descMap.containsKey(col)) {
         return descMap.get(col);
      }

      AxisDescriptor axisDesc = null;
      DataRef ref = GraphUtil.getChartRef(chartInfo, col, false);

      if("_Parallel_Label_".equals(col) && chartInfo instanceof RadarChartInfo)
      {
         axisDesc = ((RadarChartInfo) chartInfo).getLabelAxisDescriptor();
      }
      else if(chartInfo.isSeparatedGraph() && ref instanceof ChartRef &&
         !(chartInfo instanceof StockChartInfo) &&
         !(chartInfo instanceof CandleChartInfo))
      {
         axisDesc = ((ChartRef) ref).getAxisDescriptor();
      }
      // if inseparate graph or candle, stock chart, get dimension descriptor
      // from ref, get shared measure descriptor from info
      else if(ref instanceof ChartRef) {
         axisDesc = ((ChartRef) ref).getAxisDescriptor();
      }
      else {
         axisDesc = chartInfo.getAxisDescriptor();
      }

      descMap.put(col, axisDesc);
      return axisDesc;
   }

   /**
    * @param col the column to get the legends of.
    *
    * @return all legends matching col.
    */
   private List<Legend> getLegends(String col) {
      final LegendGroup legendGroup = vgraph.getLegendGroup();

      if(legendGroup == null) {
         return Collections.emptyList();
      }

      final ArrayList<Legend> legends = new ArrayList<>(0);

      for(int i = 0; i < legendGroup.getLegendCount(); i++) {
         final Legend legend = legendGroup.getLegend(i);
         final String legendField = legend.getVisualFrame().getField();

         // legend not tied to a single column
         if(legendField == null) {
            Arrays.stream(legend.getItems())
                    .flatMap(Arrays::stream)
                    .filter(Objects::nonNull)
                    .filter(item -> col.equals(item.getValue()))
                    .findFirst().ifPresent(item -> legends.add(legend));
         }
         else if(col.equals(legendField)) {
            legends.add(legend);
         }
      }

      return legends;
   }

   /**
    * @param legends the list of legends to search.
    * @param col     the original column name.
    *
    * @return a matching column alias or null if none exists.
    */
   private String getLegendColumnAlias(List<Legend> legends, String col) {
      String columnAlias = null;

      for(Legend legend : legends) {
         final String title = legend.getTitle() != null ? legend.getTitle().getText() : null;
         final String legendField = legend.getVisualFrame().getField();

         if(legendField == null) {
            final Optional<LegendItem> legendItem = Arrays.stream(legend.getItems())
                    .flatMap(Arrays::stream)
                    .filter(Objects::nonNull)
                    .filter(item -> col.equals(item.getValue()))
                    .findFirst();

            if(legendItem.isPresent()) {
               columnAlias = legendItem.get().getLabel();
               break;
            }
         }
         else if(!col.equals(title)) {
            columnAlias = title;
            break;
         }
      }

      return columnAlias;
   }

   /**
    * @param legends the list of legends to search.
    * @param value   the matching value to search for.
    *
    * @return a matching value alias or null if none exists.
    */
   private String getLegendValueAlias(List<Legend> legends, String value) {
      for(Legend legend : legends) {
         for(LegendItem[] items : legend.getItems()) {
            for(LegendItem item : items) {
               if(item != null && value.equals(item.getValue()) && !item.getValue().equals(item.getLabel())) {
                  return item.getLabel();
               }
            }
         }
      }

      return null;
   }

   /**
    * Append aesthetic info to tooltip.
    */
   private void appendInfo(VisualModel model, ChartToolTip tooltip, int index, DataSet dataset,
                           Set<String> added, Set<String> aesthetics, ElementVO evo,
                           Set<String> excludeDims)
   {
      if(model == null) {
         return;
      }

      VisualFrame[] frames = { model.getColorFrame(), model.getShapeFrame(),
                               model.getTextureFrame(), model.getLineFrame(),
                               model.getSizeFrame(), model.getTextFrame() };

      frames = Arrays.stream(frames).filter(a -> a != null).toArray(VisualFrame[]::new);
      // sort categorical frames to the front so aliases in text frame
      // (defined in categorical frame) are applied.
      Arrays.sort(frames, (a, b) -> {
         if(a.getClass() != b.getClass()) {
            if(a instanceof CategoricalFrame) {
               return -1;
            }
            else if(b instanceof CategoricalFrame) {
               return 1;
            }
         }

         return 0;
      });

      for(VisualFrame frame : frames) {
         appendLegendInfo(frame, tooltip, index, dataset, added, aesthetics, evo, excludeDims);
      }

      GraphElement elem = ((ElementGeometry) evo.getGeometry()).getElement();

      if(elem instanceof RelationElement) {
         appendLegendInfo(((RelationElement) elem).getNodeColorFrame(),
                          tooltip, index, dataset, added, aesthetics, evo, excludeDims);
         appendLegendInfo(((RelationElement) elem).getNodeSizeFrame(),
                          tooltip, index, dataset, added, aesthetics, evo, excludeDims);
      }
   }

   /**
    * Append aesthetic info to tooltip.
    */
   private void appendLegendInfo(VisualFrame frame, ChartToolTip tooltip,
                                 int index, DataSet dataset, Set<String> added,
                                 Set<String> aesthetics, ElementVO evo, Set<String> excludeDims)
   {
      if(frame == null) {
         return;
      }

      boolean inner = false;

      if(frame instanceof CompositeVisualFrame) {
         CompositeVisualFrame cframe = (CompositeVisualFrame) frame;
         frame = cframe.getGuideFrame();

         if(frame == null) {
            frame = getInnerFrame(cframe);
            inner = frame != null;
         }
      }

      // if frame is not visible, not apply its info
      // if(frame==null || (!frame.isVisible() && !(frame instanceof TextFrame))) {
      // fix bug1311024580841, show invisible legend column in tooltip
      if(frame == null) {
         return;
      }

      String[] fields = {};

      if(frame instanceof MultiFieldFrame) {
         fields = ((MultiFieldFrame) frame).getFields();
      }
      // stacked show value fields needed to be added to tooltip (49927).
      else if(!(frame instanceof StackTextFrame)) {
         String field = frame.getField();

         if(field != null) {
            fields = new String[] { field };
         }
      }

      if(fields.length == 0 && frame instanceof ShapeFrame && chartInfo != null) {
         AestheticRef[] arefs = chartInfo.getAestheticRefs(false);

         for(int i = 0; i < arefs.length; i++) {
            String field = arefs[i].getFullName();

            if(added.contains(field)) {
               continue;
            }

            fields = new String[] { field };
            break;
         }
      }

      TextFrame textFrame = frame.getLegendSpec().getTextFrame();;

      for(int i = 0; i < fields.length; i++) {
         String field = fields[i];
         String ofield = full2NoCalcNames.get(field);

         if(field == null || field.trim().length() == 0) {
            return;
         }

         if(dataset.indexOfHeader(field) < 0 || excludeDims.contains(field)) {
            return;
         }

         if(added.contains(field) || !aesthetics.contains(field) && !inner && i == 0) {
            return;
         }

         // ignore geo shape column.
         if(GraphUtil.isMappedHeader(field)) {
            continue;
         }

         // network brushing shows all values. (58100)
         if(field.startsWith(ElementVO.ALL_PREFIX) &&
            GraphTypes.isRelation(chartInfo.getRTChartType()))
         {
            field = ElementVO.getBaseName(field);
         }

         Format fmt = fmts.getFormat(field);
         String label = getDataString(dataset, field, index, fmt, false, evo, egraph, textFrame);
         String oname = GraphUtil.getOriginalNameForTip(field);

         if(tips.containsKey(oname)) {
            oname = tips.get(oname);
         }
         else {
            oname = GraphUtil.getFullNameNoCalc(field);
         }

         final List<Legend> legends = getLegends(field);

         if(legends.size() > 0) {
            final String legendValueAlias = getLegendValueAlias(legends, label);

            if(legendValueAlias != null) {
               label = legendValueAlias;
            }

            final String legendColumnAlias = getLegendColumnAlias(legends, oname);

            if(legendColumnAlias != null) {
               oname = legendColumnAlias;
            }
         }

         int key = palette.put(Tool.localize(oname));
         int val = palette.put(label);
         tooltip.addTooltip(key, val);
         added.add(field);

         if(ofield != null && !added.contains(ofield)) {
            final Format ofmt = fmts.getFormat(ofield);
            String olabel = getDataString(dataset, ofield, index, ofmt, false, evo, egraph, null);
            oname = GraphUtil.getOriginalNameForTip(ofield);
            key = palette.put(Tool.localize(oname));
            val = palette.put(olabel);
            tooltip.addTooltip(key, val);
            added.add(ofield);
         }
      }
   }

   /**
    * Get the inner frame.
    */
   private VisualFrame getInnerFrame(CompositeVisualFrame frame) {
      for(int i = 0; i < frame.getFrameCount(); i++) {
         VisualFrame tframe = frame.getFrame(i);

         if(tframe.getField() != null) {
            return tframe;
         }
         else if(tframe instanceof CompositeVisualFrame) {
            return getInnerFrame((CompositeVisualFrame) tframe);
         }
      }

      return null;
   }

   /**
    * Convert the line/area's point shape to shapeElementVO and
    * Pie3D VO to sector.
    */
   private List<VisualObject> generateSubShape(List<VisualObject> vos) {
      List<VisualObject> nvos = new ArrayList<>(vos);

      for(Visualizable v : vos) {
         if(!isEditable(v)) {
            continue;
         }

         if(v instanceof LineVO) {
            ElementVO vo = (ElementVO) v;
            Shape[] shapes = vo.getShapes();
            int lineIdx = -1;
            LineGeometry lineGeometry = null;
            boolean radar = GraphTypes.isRadarN(chartInfo);

            List<Point2D> line = new ArrayList<>();
            Point2D[] pts = ((LineVO) vo).getTransformedPoints();
            Point2D relpos = getRelativePosition();
            lineGeometry = (LineGeometry) vo.getGeometry();

            for(Point2D pt : pts) {
               if(LineVO.isNaN(pt)) {
                  continue;
               }

               pt = trans.transform(pt, null);
               pt = new Point2D.Double(pt.getX() - relpos.getX(), pt.getY() - relpos.getY());
               line.add(pt);
            }

            lineIdx = lines.put(line);
            int[] arr = vo.getRowIndexes();

            for(int j = 0; j < shapes.length; j++) {
               int[] subIdxs = vo.getSubRowIndexes();
               ShapeElementVO shapeVO = new ShapeElementVO(vo.getGeometry(), vo.getMeasureName());

               shapeVO.setShape(shapes[j]);
               shapeVO.setRowIndex(arr[j % arr.length]);
               shapeVO.setColIndex(vo.getColIndex());
               shapeVO.setLineIndex(lineIdx);
               shapeVO.setZIndex(vo.getZIndex());
               shapeVO.setSubRowIndexes(new int[]{ subIdxs[j % subIdxs.length] });

               // for radar, set the associated dim on this point.
               if(radar && lineGeometry != null && lineGeometry.getTupleCount() > 0) {
                  double[] tuple = lineGeometry.getTuple(j);

                  for(int k = 0; k < tuple.length; k++) {
                     String tdim = lineGeometry.getElement().getDim(k);

                     if(isIgnoreDim(tdim, vo, j)) {
                        shapeVO.setIgnoredDim(tdim);
                     }
                  }
               }

               nvos.add(shapeVO);
            }
         }
         else if(GraphUtil.supportSubSection(v)) {
            Pie3DVO vo = (Pie3DVO) v;
            Shape[] shapes = ((Pie3DVO) v).getShapes();

            for(int j = 0; j < shapes.length; j++) {
               int[] arr = vo.getRowIndexes();

               if(j >= arr.length) { // script chart
                  continue;
               }

               ShapeElementVO shapeVO = new ShapeElementVO(vo.getGeometry(), vo.getMeasureName());
               shapeVO.setShape(shapes[j]);
               shapeVO.setRowIndex(arr[j]);
               shapeVO.setColIndex(vo.getColIndex());
               shapeVO.setZIndex(vo.getZIndex());
               shapeVO.setSubRowIndexes(new int[]{ vo.getSubRowIndexes()[j] });
               nvos.add(shapeVO);
            }
         }
      }

      return nvos;
   }

   private boolean isIgnoreDim(String tdim, ElementVO vo, int tidx) {
      boolean radar = chartInfo instanceof RadarChartInfo;

      // for radar, set the associated dim on this point.
      if(radar && vo.getGeometry() instanceof LineGeometry) {
         if(GraphUtil.isOuterDimRef(new VSDimensionRef(new AttributeRef(tdim)), chartInfo)) {
            return false;
         }

         LineGeometry lineGeometry = (LineGeometry) vo.getGeometry();
         LineElement lineElement = (LineElement) lineGeometry.getElement();
         double[] tuple = lineGeometry.getTuple(tidx);
         String[] dims = lineElement.getDims();

         for(int k = 0; k < dims.length; k++) {
            if(dims[k].equals(tdim)) {
               return Double.isNaN(tuple[k]);
            }
         }
      }
      else if(radar && vo instanceof PointVO) {
         return !vo.getMeasureName().equals(tdim);
      }

      return false;
   }

   // check if dimension is the current point on a radar
   private boolean isCurrentMeasure(String tdim, ElementVO vo, VOText votext,
                                    ElementGeometry egeom, DataSet dataset)
   {
      boolean radar = chartInfo instanceof RadarChartInfo;
      boolean relation = chartInfo instanceof RelationChartInfo;

      if(votext != null && !relation && votext.getPointMeasureName() != null &&
         dataset.isMeasure(tdim) &&
         !tdim.equals(GraphUtil.getOriginalCol(votext.getPointMeasureName())))
      {
         GraphElement elem = egeom.getElement();

         if(elem.getSizeFrame() != null && tdim.equals(elem.getSizeFrame().getField())) {
            return true;
         }
         else if(elem.getColorFrame() != null && tdim.equals(elem.getColorFrame().getField())) {
            return true;
         }
         else if(elem.getShapeFrame() != null && tdim.equals(elem.getShapeFrame().getField())) {
            return true;
         }
         else if(elem.getTextureFrame() != null && tdim.equals(elem.getTextureFrame().getField())) {
            return true;
         }

         return false;
      }

      // for radar, set the associated dim on this point.
      if(radar && vo instanceof ShapeElementVO) {
         return !((ShapeElementVO) vo).isIgnoredDim(tdim);
      }

      if(votext == null && isShowStackMeasure(chartInfo)) {
         GraphElement element = egeom.getElement();
         String[] vars = element.getVars();

         if(!Tool.equals(tdim, GraphUtil.getOriginalCol(vo.getMeasureName())) &&
            Arrays.stream(vars).anyMatch((var) -> Tool.equals(tdim, var)))
         {
            return false;
         }
      }

      return true;
   }

   /**
    * Set information for text area.
    */
   private void setTextAreaInfo(TextArea tarea, ElementVO v, VOText vtext,
                                int col, VGraph subgraph, List<VisualObject> vos,
                                Map<String, int[]> textValueMap)
   {
      if(tarea == null) {
         return;
      }

      HRef ref = getHyperlink(v, col, subgraph, true, vtext);
      ChartToolTip tooltip;

      if(textValueMap == null) {
         tooltip = getToolTip(v, col, subgraph, true, vtext);
         moveTotalToBottom(tooltip);
      }
      else {
         String[] dims = vtext.getGraphElement().getDims();
         int ridx = v.getRowIndexes()[col];
         String value = getRowValueString(dims, ridx);

         tooltip = getMultiLineTextToolTip(vos, v,  subgraph, textValueMap.get(value), col, vtext);
      }

      int[] arr = v.getRowIndexes();

      if(isStackValue(v, vtext)) {
         arr = getStackTextRowIndexes(v, arr[0]);
      }

      if(col < 0 || col >= arr.length) {
         tarea.setRowIndexes(arr);
      }
      else {
         tarea.setRowIndexes(new int[] {arr[col]});
      }

      if(ref != null) {
         tarea.setHyperlink((Hyperlink.Ref) ref);
         tarea.setHyperlinkLabel(ref.getName());
      }

      HRef[] drefs = getDrillHyperlinks(v, col, true);

      if(drefs != null) {
         int cnt = drefs.length;
         String[] labels = new String[cnt];

         for(int i = 0; i < cnt; i++) {
            HRef dref = drefs[i];
            labels[i] = dref.getName();
         }

         tarea.setDrillHyperlinks(drefs);
         tarea.setDrillHyperlinkLabels(labels);
      }

      tarea.setToolTip(tooltip);

      if(tarea.getRowIndexes().length > 0 && tarea.getRegion() != null) {
         addShape(tarea.getRowIndexes()[0], tarea.getColIndex(), tarea.getRegion().getBounds());
         tooltipsContainer.put(
            new Point(tarea.getRowIndexes()[0], tarea.getColIndex()),
            tarea.getToolTipString());
      }
   }

   /**
    * Change the VOTexts to text areas.
    */
   private void addTextArea(List<DefaultArea> visuals, VOText[] texts, ElementVO elemVO,
                            VGraph subgraph, List<VisualObject> vos,
                            Map<String, int[]> textValueMap)
   {
      if(texts == null) {
         return;
      }

      for(int i = 0; i < texts.length; i++) {
         if(texts[i] == null || texts[i].getZIndex() < 0) {
            continue;
         }

         // when a VOText displays both text from text binding and show values, we need to
         // split it into their respective region so the format can be edited properly.
         for(VOText text : texts[i].getSubTexts()) {
            TextArea area = new TextArea(text, trans, palette);
            setTextAreaInfo(area, elemVO, text, i, subgraph, vos, textValueMap);
            visuals.add(area);

            GraphElement elem = text.getGraphElement();

            if(elem instanceof PointElement && ((PointElement) elem).isWordCloud()) {
               TextFrame frame = elem.getTextFrame();

               if(frame != null && frame.getField() != null && area.getHyperlink() != null) {
                  dlinks.put(area.getRegion().getBounds(), area.getHyperlink());
               }
            }
         }
      }
   }

   /**
    * Add radar axes areas.
    */
   private void addRadarAxesArea(List<DefaultArea> visuals, Coordinate coord) {
      if(coord instanceof FacetCoord) {
         FacetCoord facet = (FacetCoord) coord;
         Coordinate[][] coords = facet.getExpandedInnerCoords();

         for(int i = 0; i < coords.length; i++) {
            for(int j = 0; j < coords[i].length; j++) {
               addRadarAxesArea(visuals, coords[i][j]);
            }
         }
      }
      else if(coord instanceof PolarCoord) {
         PolarCoord polar = (PolarCoord) coord;

         if(polar.getCoordinate() instanceof AbstractParallelCoord) {
            AbstractParallelCoord parallel = (AbstractParallelCoord) polar.getCoordinate();
            Axis[] axes = parallel.getAxes(false);

            for(int i = 0; i < axes.length; i++) {
               DefaultAxis vAxis = (DefaultAxis) axes[i];
               AxisSpec spec = vAxis.getScale().getAxisSpec();

               if(spec.isLineVisible() || spec.isLabelVisible()) {
                  visuals.add(new RadarMeasureLabelsArea(vAxis, trans, palette));
               }
            }
         }
      }
   }

   /**
    * Add the parallel label axis area.
    */
   private void addRadarLabelAxisArea(List<DefaultArea> visuals) {
      // use evgraph
      VGraph vgraph = (VGraph) vobj;
      List<Axis> axes = getAllAxes(vgraph);
      // if polar but not radar, include other axes. (58864)
      boolean all = vgraph.getCoordinate() instanceof PolarCoord &&
         ((PolarCoord) vgraph.getCoordinate()).getCoordinate() instanceof RectCoord;

      // find the polar axis
      for(Axis axis : axes) {
         if(!(axis instanceof PolarAxis) && !all) {
            continue;
         }

         VLabel[] labels = axis.getLabels();

         for(int j = 0; labels != null && j < labels.length; j++) {
            if(labels[j] instanceof VDimensionLabel) {
               VDimensionLabel vlbl = (VDimensionLabel) labels[j];
               String field = vlbl.getDimensionName();
               ChartRef ref = chartInfo.getFieldByName(field, true);
               List<Hyperlink.Ref> alinks = new ArrayList<>();
               Hyperlink.Ref href = null;
               Hyperlink.Ref[] drills = null;

               if(ref instanceof HyperlinkRef) {
                  HyperlinkRef dim = (HyperlinkRef) ref;
                  Hyperlink link = dim.getHyperlink();
                  Map<String, Object> param = GraphUtil.getHyperlinkParam(axis, vlbl,
                     ICoordinate.ALL_MOST, vgraph.getEGraph(), getDataSet());
                  href = getHyperlinkRef(link, param, ref);

                  if(href != null) {
                     if(href.isSendReportParameters() && (chartInfo instanceof VSChartInfo)) {
                        addLinkParameter(href, ((VSChartInfo) chartInfo).getLinkVarTable());
                     }

                     if(link.isSendSelectionParameters() && (chartInfo instanceof VSChartInfo)) {
                        Hashtable<String, SelectionVSAssembly> selections =
                           ((VSChartInfo) chartInfo).getLinkSelections();
                        VSUtil.addSelectionParameter(href, selections);
                     }

                     alinks.add(href);
                  }

                  drills = GraphUtil.getDrillLinks(field, param,
                     getDataSet(), isLightWeight());

                  if(drills != null) {
                     for(int k = 0; k < drills.length; k++) {
                        if(drills[k] != null) {
                           alinks.add(drills[k]);
                        }
                     }
                  }
               }

               RadarDimensionLabelArea labelArea = new RadarDimensionLabelArea(
                  axis, (VDimensionLabel) labels[j],
                  false, trans, palette, alinks.toArray(new Hyperlink.Ref[0]), null);
               labelArea.setHyperlinkExists(href != null);
               Shape shape = labelArea.getRegion().getBounds();
               dlinks.put(shape, href);
               drillLinks.put(shape, drills);
               visuals.add(labelArea);
            }
            else {
               visuals.add(new RadarDimensionLabelArea(
                  axis, (VDimensionLabel) labels[j],
                  false, trans, palette));
            }
         }
      }
   }

   private Hyperlink.Ref getHyperlinkRef(Hyperlink link, Map<String, Object> param, ChartRef ref) {
      if(link == null) {
         return null;
      }

      // if dimension drilled, and a paramter is no longer available, ignore the
      // hyperlink. (42830)
      if(ref instanceof VSChartDimensionRef && ((VSChartDimensionRef) ref).isDateTime()) {
         String dlevel1 = ((VSChartDimensionRef) ref).getDateLevelValue();
         int runtimeLevel = ((VSChartDimensionRef) ref).getDateLevel();
         String dlevel2 = runtimeLevel == 0 ? null : runtimeLevel + "";
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

   /**
    * Get all the axis from VGraph.
    * @param graph the specified VGraph which to get all axes from.
    * @return all axes.
    */
   private List<Axis> getAllAxes(VGraph graph) {
      List<Axis> axisList = new ArrayList<>();

      if(graph == null) {
         return axisList;
      }

      for(int i = 0; i < graph.getAxisCount(); i++) {
         axisList.add(graph.getAxis(i));
      }

      for(int i = 0; i < graph.getVisualCount(); i++) {
         Visualizable v = graph.getVisual(i);

         if(v instanceof GraphVO) {
            VGraph vgraph =((GraphVO) v).getVGraph();
            axisList.addAll(getAllAxes(vgraph));
         }
      }

      return axisList;
   }

   /**
    * Get an unformatted stacked value.
    * Only relevant for aggregate (measure) fields.
    * This duplicates code in getDataString, but the value is not formatted.
    */
   private static double getStackValue(DataSet set, String header, int[] rows,
                                       Format fmt, EGraph egraph)
   {
      double data = 0;

      for(int row : rows) {
         Object obj = set.getData(header, row);

         if(!(obj instanceof Number)) {
            continue;
         }

         String str = getDataString(set, header, row, null, true, null, egraph, null);
         data += parseNumber(fmt, obj, str);
      }

      return data;
   }

   private static double parseNumber(Format fmt, Object obj, String str) {
      double val;

      if(obj instanceof Double) {
         try {
            val = ((Number) doubleFmt.parse(str)).doubleValue();
         }
         catch(Exception ex) {
            val = NumberParserWrapper.getDouble(obj + "");
         }
      }
      else if(fmt != null) {
         try {
            val = ((Number) fmt.parseObject(str)).doubleValue();
         }
         catch(Exception ex) {
            try {
               val = NumberParserWrapper.getDouble(obj + "");
            }
            catch(NumberFormatException e) {
               return Double.NaN;
            }
         }
      }
      else {
         try {
            val = NumberParserWrapper.getDouble(str);
         }
         catch(NumberFormatException e) {
            return Double.NaN;
         }
      }

      return val;
   }

   /**
    * Get the data string.
    */
   private static String getDataString(DataSet dset, String var, int[] rows, Format fmt,
                                       boolean isValue, ElementVO evo, EGraph egraph,
                                       boolean fixStack)
   {
      return getDataString(dset, var, rows, -1, fmt, isValue, evo, egraph, fixStack);
   }

   /**
    * Get the data string.
    */
   private static String getDataString(DataSet dset, String var, int[] rows, int index,
                                       Format fmt, boolean isValue, ElementVO evo, EGraph egraph)
   {
      return getDataString(dset, var, rows, index, fmt, isValue, evo, egraph, true);
   }

   /**
    * Get the data string.
    */
   private static String getDataString(DataSet dset, String var, int[] rows, int index,
                                       Format fmt, boolean isValue, ElementVO evo, EGraph egraph,
                                       boolean fixStack)
   {
      double data = 0;
      String[] vars = { var };

      // handle stacked value for stacked measures. (50007)
      if(evo != null && fixStack) {
         GraphElement elem = ((ElementGeometry) evo.getGeometry()).getElement();

         if(elem.isStack()) {
            vars = elem.getVars();
         }
      }

      boolean stackTotal = index != -1;
      boolean neg = stackTotal && getNumberValue(dset, var, index, fmt, isValue, evo, egraph) < 0;

      for(int row : rows) {
         for(String col : vars) {
            double num = getNumberValue(dset, col, row, fmt, isValue, evo, egraph);

            if(stackTotal && neg && num >= 0 || stackTotal && !neg && num < 0) {
               continue;
            }

            data += num;
         }
      }

      String str = data == (int) data ? ((int) data) + "" : data + "";
      str = fmt != null ? XUtil.format(fmt, str, dset.isMeasure(vars[0])) : str;

      return str;
   }

   private static double getNumberValue(DataSet dset, String col, int row,
                                 Format fmt, boolean isValue, ElementVO evo, EGraph egraph)
   {
      Object obj = dset.getData(col, row);

      if(obj == null) {
         return 0;
      }

      String str = null;

      if(!(obj instanceof Number)) {
         str = getDataString(dset, col, row, fmt, isValue, evo, egraph, null);
      }
      else {
         str = getDataString(dset, col, row, null, isValue, evo, egraph, null);
      }

      return parseNumber(fmt, obj, str);
   }

   /**
    * Get the data string.
    */
   private static Object getData(DataSet dset, String col, int row, ElementVO evo, EGraph egraph) {
      // fix bug1309159129496, if BrushDataSet, current row is get from
      // SubDataSet, we shouldn't use sub's row to get data from brush data set,
      // in order to get the right data, here we use sub's row to get data from
      // sub.
      // @by davyc, because sometimes, data set will be cause some data out of
      // all data, so we will tick those data off, this cause row not mapping
      // (see BrushDataSet.MergedDataSetIndex), so here should just convert the
      // base data's row index to brush data set's row index
      // the following logic will cause CalcColumn data lost, so refix it
      // see bug1309159129496
      /*
      if(dset instanceof BrushDataSet) {
         while(dset instanceof DataSetFilter) {
            DataSetFilter filter = (DataSetFilter) dset;
            dset = filter.getDataSet();
         }
      }
      */
      if(dset instanceof BrushDataSet) {
         row = ((BrushDataSet) dset).getCurrentRowFromBaseRow(row);
      }

      if(row < 0) {
         return null;
      }

      Object obj = null;

      if(evo instanceof TreemapVO) {
         Set<String> current = new HashSet<>(Arrays.asList(((TreemapVO) evo).getCurrentTreeDims()));
         String coltype = Tool.getDataType(dset.getType(col));
         TreemapElement tree = (TreemapElement) ((ElementGeometry) evo.getGeometry()).getElement();
         Set dims = new HashSet(Arrays.asList(tree.getDims()));

         if(current.contains(col) || !XSchema.isNumericType(coltype) || dims.contains(col) ||
            current.size() == tree.getTreeDimCount())
         {
            obj = dset.getData(col, row);
         }
         // if it's a container of treemap, get the total of children for tooltip
         else {
            double total = 0;
            final int row0 = row;
            GraphUtil.getAllScales(egraph.getCoordinate()).stream()
               .filter(s -> s instanceof CategoricalScale)
               .forEach(s -> Arrays.stream(s.getFields()).forEach(a -> current.add(a) ));
            List group = current.stream()
               .map(c -> dset.getData(c, row0)).collect(Collectors.toList());

            for(int i = 0; i < dset.getRowCount(); i++) {
               final int row2 = i;
               List group2 = current.stream()
                  .map(c -> dset.getData(c, row2)).collect(Collectors.toList());

               if(!group.equals(group2)) {
                  continue;
               }

               Object val = dset.getData(col, row2);

               if(val instanceof Number) {
                  total += ((Number) val).doubleValue();
               }
            }

            obj = Tool.getData(coltype, total);
         }
      }
      else {
         obj = dset.getData(col, row);;
      }

      return obj;
   }

   private static String getDataString(DataSet dset, String col, int row, Format fmt,
                                       boolean isValue, ElementVO evo, EGraph egraph,
                                       TextFrame textFrame)
   {
      Object obj = getData(dset, col, row, evo, egraph);

      if(obj instanceof DCMergeDatesCell) {
         DCMergeDatesCell cell = (DCMergeDatesCell) obj;
         obj = cell.getFormatedOriginalDate();
      }
      else if(obj instanceof DCMergeDatePartFilter.MergePartCell) {
         DateComparisonFormat dcFormat = (DateComparisonFormat)
            GraphUtil.getAllScales(egraph.getCoordinate()).stream()
               .map(s -> s.getAxisSpec().getTextSpec().getFormat())
               .filter(f -> f instanceof DateComparisonFormat).findFirst().orElse(null);

         if(dcFormat != null && dcFormat.getDateCol() != null) {
            Object date = dset.getData(dcFormat.getDateCol(), row);

            if(date != null) {
               return dcFormat.format((DCMergeDatePartFilter.MergePartCell) obj, (Date) date);
            }
         }
      }

      if(obj == null || isValue) {
         if(obj instanceof Double) {
            return doubleFmt.format(obj);
         }

         return obj == null ? "" : Tool.getDataString(obj);
      }

      if(textFrame != null) {
         obj = textFrame.getText(obj);
      }

      // try default format
      if(fmt == null && (dset instanceof AttributeDataSet)) {
         AttributeDataSet attr = (AttributeDataSet) dset;
         fmt = attr.getFormat(col, row);
      }

      // force format to number
      if(fmt == null && obj instanceof Number) {
         fmt = FMT;
      }

      if(fmt != null) {
         synchronized(fmt) {
            if(fmt instanceof DateComparisonFormat && obj instanceof Integer) {
               DateComparisonFormat dcFormat = (DateComparisonFormat) fmt;
               TableLens base = new DataSetTable(dset);
               int dateCol = Util.findColumn(base, dcFormat.getDateCol());
               Object val = dset.getData(dateCol, row);

               if(val instanceof Date) {
                  return dcFormat.format((Integer) obj, (Date) val);
               }
            }

            return XUtil.format(fmt, obj, dset.isMeasure(col));
         }
      }

      return Tool.getDataString(obj);
   }

   /**
    * Check if this is editable.
    * @param vo the visual object.
    */
   private static boolean isEditable(Visualizable vo) {
      if(vo instanceof TreemapVO) {
         TreemapGeometry gobj = (TreemapGeometry) ((TreemapVO) vo).getGeometry();
         TreemapElement treemap = (TreemapElement) gobj.getElement();

         // don't send shape for container since clicking on leaf would cause the
         // container to be selected. circle packing container is supported for
         // setting background colors.
         if(treemap.getMapType() == TreemapElement.Type.TREEMAP && !gobj.isLeaf()) {
            return false;
         }
      }

      if(vo instanceof ElementVO) {
         Object obj = ((ElementVO) vo).getHint("editable");
         return !"false".equals(obj); // default is editable
      }

      return true;
   }

   /**
    * Check if this is editable.
    * @param vo the visual object.
    */
   private static boolean isMapEditable(Visualizable vo, VGraph graph) {
      if(vo instanceof PolygonVO) {
         int ridx = ((PolygonVO) vo).getSubRowIndex();
         DataSet dset = graph.getCoordinate().getDataSet();
         return ridx < dset.getRowCount() && ridx >= 0;
      }

      return true;
   }

   /**
    * Paint this area.
    */
   @Override
   public void paintArea(Graphics g, Color color) {
      Rectangle bounds = getRegion().getBounds();
      (new RectangleRegion(new Rectangle(0, 0, bounds.width,
                                         bounds.height))).fill(g, color);
   }

   /**
    * Get the data info for this area.
    */
   @Override
   public ChartAreaInfo getChartAreaInfo() {
      return ChartAreaInfo.createPlot();
   }

   /**
    * Get the number of rows of this area.
    */
   public int getRows() {
      return container.getRows();
   }

   /**
    * Get the number of cols of this area.
    */
   public int getCols() {
      return container.getCols();
   }

   /**
    * Get lineset of this area.
    */
   public LineSet getLines() {
      return lines;
   }

   public Rectangle2D getLayoutBounds() {
      return layoutbounds;
   }

   public List<Double> getXboundaries() {
      return xboundaries;
   }

   public List<Double> getYboundaries() {
      return yboundaries;
   }

   private static class InternalGraphFormats extends GraphFormats {
      public InternalGraphFormats(EGraph egraph) {
         super(egraph);
         this.egraph = egraph;
      }

      @Override
      public Format getFormat(String fld) {
         Object fmt = fmtmap.get(BoxDataSet.getBaseName(fld));

         if(fmt == null) {
            fmt = initFormat(fld);

            if(fmt == null) {
               fmt = "_NULL_";
            }

            fmtmap.put(BoxDataSet.getBaseName(fld), fmt);
         }

         return "_NULL_".equals(fmt) ? null : (Format) fmt;
      }

      private Format initFormat(String fld) {
         int count = egraph.getElementCount();
         TextFrame plotTextFrame = null;
         boolean hasTextField = false;

         for(int i = 0; i < count; i++) {
            GraphElement elem = egraph.getElement(i);
            VisualFrame[] frames = elem.getVisualFrames();
            boolean hasTextAes = false;

            if(elem.getTextFrame() != null && plotTextFrame == null) {
               plotTextFrame = elem.getTextFrame();
            }

            // try aesthetic
            for(int j = 0; j < frames.length; j++) {
               VisualFrame frame = frames[j];

               if(frame instanceof CompositeVisualFrame) {
                  frame = ((CompositeVisualFrame) frame).getGuideFrame();

                  if(frame == null) {
                     CompositeVisualFrame cFrame = (CompositeVisualFrame) frames[j];

                     //from getInnerFrame()
                     for(int k = 0; k < cFrame.getFrameCount(); k++) {
                        VisualFrame tframe = cFrame.getFrame(k);

                        if(tframe.getField() != null) {
                           frame = tframe;
                           break;
                        }
                     }
                  }
               }

               String fld2 = frame == null ? null : frame.getField();

               if(frame instanceof TextFrame && fld2 != null && fld2.trim().length() > 0) {
                  hasTextAes = true;
               }

               // strip off waterfall all-header prefix
               if(fld2 != null && fld2.startsWith("__text__")) {
                  fld2 = fld2.substring("__text__".length());
               }

               if(fld.equals(fld2)) {
                  TextSpec tspec = frame.getLegendSpec().getTextSpec();
                  Format fmt = tspec == null ? null : tspec.getFormat();

                  if(fmt != null && !(fmt instanceof inetsoft.util.MessageFormat)) {
                     return fmt;
                  }

                  if(frame instanceof ValueTextFrame) {
                     ValueTextFrame text2 = (ValueTextFrame) frame;
                     hasTextField = true;
                     // field same as text aesthetic?
                     fmt = text2.getTextFormat();

                     if(fmt != null && !(fmt instanceof inetsoft.util.MessageFormat)) {
                        return fmt;
                     }
                  }
               }

               // @temp tomzhang, not all fields' tooltip should same as painted
               // text, not done
               /**
               if(frame instanceof GraphGenerator.TextFrame2) {
                  tframe2 = (GraphGenerator.TextFrame2) frame;
               }
               */
            }

            // @temp tomzhang, not all fields' tooltip should same as painted
            // text, not done
            /**
            // same as the painted result
            if(tframe2 != null) {
               return tframe2.getVOFormat();
            }
            */

            // try element
            if(!hasTextAes && (!isPointLineElem(elem)) ||
               // fix bug#52312 if pointLine is true, the text frame filed of the plot should be checked,
               // not the text frame filed of the pointLine
               plotTextFrame != null && Tool.isEmptyString(plotTextFrame.getField()))
            {
               for(int j = 0; j < elem.getVarCount(); j++) {
                  String var = elem.getVar(j);

                  if(fld.equals(var)) {
                     TextSpec tspec = elem.getTextSpec();
                     Format fmt = tspec == null ? null : tspec.getFormat();

                     if(fmt != null) {
                        return fmt;
                     }
                  }
               }
            }
         }

         // try axis. we give lower priority to scale since it's more likely to set
         // a format at less precision on a scale than the text element. For
         // example, we may set axis to percentage (#,##0%) while set element text
         // to include decimal places (#,###.##%).
         Scale scale = egraph.getScale(fld);

         // in GraphGenerator.fixGraphProperties(), it only calls getAxisLabelFormat() to
         // use axis format if a text field is bound AND show values is true. we match
         // it here to be consistent with the format used on VOText. (52188)
         // use axis format if no show value or text field for backward compatibility. (52712)
         if(scale != null && (hasTextField || plotTextFrame == null)) {
            AxisSpec aspec = scale.getAxisSpec();
            TextSpec tspec = aspec == null ? null : aspec.getTextSpec();
            Format fmt = tspec == null ? null : tspec.getFormat();

            if(fmt != null) {
               return fmt;
            }
         }

         return null;
      }

      private boolean isPointLineElem(GraphElement elem) {
         return "true".equals(elem.getHint("_show_point_")) &&
            (elem instanceof PointElement || elem instanceof LineElement);

      }

      private final EGraph egraph;
      private final transient Map<String, Object> fmtmap = new HashMap<>();
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

   public VGraph getVGraph() {
      return vgraph;
   }

   /**
    * Set chart name, when tip has error we can show error message for assembly name.
    */
   public void setChartName(String name) {
      this.chartName = name;
   }

   private boolean isStackChart(String aggrName) {
       if(!chartInfo.isMultiAesthetic()) {
         return isStack(chartInfo);
      }

      return chartInfo.getAestheticAggregateRefs(true)
         .stream()
         .filter(aggr -> aggrName == null || Tool.equals(aggr.getFullName(), aggrName))
         .filter(aggr -> isStack(aggr))
         .findAny()
         .isPresent();
   }

   private boolean isStack(ChartBindable bindable) {
      if(bindable == null) {
         return false;
      }

      int chartType = bindable.getRTChartType();
      boolean hasAesthetic = bindable.getRTColorField() != null ||
         bindable.getRTShapeField() != null || bindable.getRTSizeField() != null ||
         bindable.getRTTextField() != null;
      return GraphTypes.isStack(chartType) ||
         (GraphTypes.isWaterfall(chartType) || GraphTypes.isPareto(chartType)) && hasAesthetic;
   }

   /**
    * @return true if need display measure name for stack total tooltip.
    */
   private boolean showMeasureTotal() {
      List<ChartAggregateRef> list = chartInfo.getAestheticAggregateRefs(true);

      return chartInfo != null && chartInfo.isCombinedToolTip() && !chartInfo.isSeparatedGraph() &&
         !GraphTypeUtil.isStackMeasures(this.chartInfo, null) && list.size() > 1;
   }

   private void moveTotalToBottom(ChartToolTip toolTip) {
      String stackTotalName = toolTip.getStackTotalName();

      // move stack total to bottom
      if(stackTotalName != null) {
         int key = palette.put(stackTotalName);
         int val = toolTip.getTooltipValue(key);
         toolTip.removeTooltip(key);
         toolTip.addTooltip(key, val);
      }
   }

   private static final DecimalFormat FMT = new DecimalFormat("0.####");
   private static final Logger LOG = LoggerFactory.getLogger(PlotArea.class);
   private static final FormatCache doubleFmt
      = new FormatCache(new DecimalFormat("#,##0.00"));
   private static final String TOOLTIP_TAG = "|";
   private static final Pattern VALID_TIP_FORMAT_PATTERN
      = Pattern.compile("\\|[^|]*\\{([0-9]+)(,.*)*\\}[^|]*\\|");

   // optimise get tooltips, avoid duplicate calculate.
   private final Map<String, String> tips = new HashMap<>();
   private final Map<String, String> full2NoCalcNames = new HashMap<>();
   private final Map<String, AxisDescriptor> descMap = new HashMap<>();
   private final Map<String, java.text.MessageFormat> fmap = new HashMap<>();

   private final EGraph egraph;
   private final VGraph vgraph;
   private final GraphFormats fmts;
   private final SplitContainer container;
   private final Map<Point, Set<Shape>> shapesContainer;
   private final Map<Point, String> tooltipsContainer;
   private final LineSet lines = new LineSet();
   private final ChartInfo chartInfo;
   private final FixedSizeSparseMatrix linkcache; // hyperlink cache
   // dimension label shape -> hyperlink
   private final Map<Shape, Hyperlink.Ref> dlinks = new HashMap<>();
   // drill hyperlinks
   private final Map<Shape, Hyperlink.Ref[]> drillLinks = new HashMap<>();
   private final Rectangle2D layoutbounds;
   private final List<Double> xboundaries = new ArrayList<>();
   private final List<Double> yboundaries = new ArrayList<>();
   private String chartName;
   private Map<GraphElement, StackTextFrame> fakeStackTextFrameMap = new HashMap<>();
   private static final String STACK_TOTAL = Catalog.getCatalog().getString("Total");
}
