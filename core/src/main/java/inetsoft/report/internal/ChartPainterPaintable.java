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
package inetsoft.report.internal;

import inetsoft.graph.data.*;
import inetsoft.report.*;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.graph.IntervalDataSet;
import inetsoft.report.composition.region.ChartArea;
import inetsoft.report.composition.region.PlotArea;
import inetsoft.uql.VariableTable;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.Enumeration;
import java.util.Map;

/**
 * The ChartPainterPaintable encapsulate printing of a chart painter.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class ChartPainterPaintable extends LinkedShapePainterPaintable {
   /**
    * Default constructor
    */
   public ChartPainterPaintable() {
      super(new ChartElementDef());
   }

   /**
    * Construct a chart painter paintable.
    */
   public ChartPainterPaintable(float x, float y, float painterW,
      float painterH, Dimension pd, int prefW,
      int prefH, ReportElement elem, Painter painter,
      int offsetX, int offsetY, int rotation)
   {
      super(x, y, painterW, painterH, pd, prefW, prefH, elem,
            (ChartPainter) painter, offsetX, offsetY, rotation);
   }

   /**
    * Process Hyperlink.
    */
   @Override
   protected void processHyperlink() {
      ChartPainter cpainter = (ChartPainter) painter;
      DataSet chartData = cpainter.getChartDataSet();

      if(chartData == null || !(chartData instanceof AttributeDataSet)) {
         return;
      }

      ReportSheet report = getElement().getReport();
      VariableTable params = report == null ? null : report.getVariableTable();
      ChartInfo info = ((ChartElementDef) elem).getChartInfo();
      boolean contains = GraphUtil.containsMapPointField(info) &&
         GraphUtil.containsMapPolygon(info);
      AttributeDataSet data = (AttributeDataSet) chartData;
      int ccnt = cpainter.getDatasetSize();
      boolean containLink = false;

      content:
      for(int col = 0; col < ccnt; col++) {
         boolean ignore = false;
         String header = data.getHeader(col);
         header = GraphUtil.getGeoField(header);

         if(contains && GraphUtil.isPolygonField((MapInfo) info, header)) {
            ignore = true;
         }

         for(int row = 0; row < cpainter.getDatasetCount(); row++) {
            if(hasHyperlink(data, header, row) && !ignore) {
               containLink = true;
               break content;
            }

            HRef[] links = getDrillHyperlinks(data, col, row);

            if(links.length > 0) {
               containLink = true;
               break content;
            }
         }
      }

      ChartPainter opainter = null;

      if(containLink) {
         // layout network twice changes layout to change (since there are some random number
         // numbers involved. making a copy to avoid. (57413)
         if(GraphTypes.isRelation(info.getChartType())) {
            opainter = ((ChartPainter) painter).clone();
         }

         paint(new EmptyGraphics(null));
      }

      // update dataset after painting for it might be changed by graph generator
      chartData = cpainter.getChartDataSet();
      ChartArea area = cpainter.getChartArea();

      // @by davyc, add dimension hyperlink, the pair of shape -> hyperlink may
      // be have been added in above, but it was not correct, here override it
      if(area == null) {
         return;
      }

      PlotArea plot = area.getPlotArea();
      java.util.List<CalcColumn> calcs = chartData.getCalcColumns();
      boolean calcInit = calcs.size() == 0 || chartData.indexOfHeader(calcs.get(0).getHeader()) >= 0;
      int baseccnt = chartData.getColCount();
      ccnt = calcInit ? cpainter.getDatasetSize() : baseccnt + calcs.size();

      for(int col = 0; containLink && col < ccnt; col++) {
         // if both point and polygon exist, polygon does not apply hyperlink
         boolean ignore = false;
         String header = calcInit || col < baseccnt ? chartData.getHeader(col) :
            calcs.get(col - baseccnt).getHeader();
         header = GraphUtil.getGeoField(header);

         if(contains && GraphUtil.isPolygonField((MapInfo) info, header)) {
            ignore = true;
         }

         for(int row = 0; row < cpainter.getDatasetCount() && !chartData.isDisposed(); row++) {
            Hyperlink.Ref ref = plot.getHyperlink(row, col);
            HRef[] links = getDrillHyperlinks(chartData, col, row);

            if(header.startsWith(IntervalDataSet.TOP_PREFIX)) {
               int baseCol = chartData.indexOfHeader(GraphUtil.getOriginalCol(header));

               if(baseCol >= 0) {
                  if(ref == null) {
                     ref = plot.getHyperlink(row, baseCol);
                  }

                  if(links == null || links.length == 0) {
                     links = getDrillHyperlinks(chartData, baseCol, row);
                  }
               }
            }
            else if(links == null && chartData instanceof BoxDataSet) {
               String baseHeader = ((BoxDataSet) chartData).getBaseName(header);

               if(Tool.equals(header, baseHeader)) {
                  links = getDrillHyperlinks(chartData, chartData.indexOfHeader(baseHeader), row);
               }
            }

            Util.mergeParameters(ref, params);
            Util.updateLink(ref);

            for(int k = 0; links != null && k < links.length; k++) {
               Util.mergeParameters((Hyperlink.Ref) links[k], params);
            }

            Shape[] shapes = cpainter.getShapes(row, col);

            if(ref != null) {
               shapes = cpainter.getShapes(row, col);
            }

            for(int k = 0; shapes != null && k < shapes.length; k++) {
               if(ref != null && !ignore && shapes[k] != null) {
                  setHyperlink(shapes[k], ref);
               }

               if(links != null && links.length > 0) {
                  Hyperlink.Ref[] refs = new Hyperlink.Ref[links.length];
                  System.arraycopy(links, 0, refs, 0, links.length);
                  setDrillHyperlinks(shapes[k], refs);
               }
            }
         }
      }

      loadHyperlinks(area.getDimensionLinks(), params);
      loadHyperlinks(plot.getDimensionLinks(), params);
      loadDrillLinks(area.getDrillLinks(), params);
      loadDrillLinks(plot.getDrillLinks(), params);

      if(opainter != null) {
         painter = opainter;
      }
   }

   private void loadHyperlinks(Map<Shape, Hyperlink.Ref> links, VariableTable params) {
      if(links == null) {
         return;
      }

      for(Shape s : links.keySet()) {
         Hyperlink.Ref ref = links.get(s);

         Util.mergeParameters(ref, params);
         Util.updateLink(ref);
         // override setted in above
         setHyperlink(s, ref);
      }
   }

   private void loadDrillLinks(Map<Shape, Hyperlink.Ref[]> drillLinks, VariableTable params) {
      if(drillLinks == null) {
         return;
      }

      for(Shape s : drillLinks.keySet()) {
         Hyperlink.Ref[] links = drillLinks.get(s);

         for(int i = 0; links != null && i < links.length; i++) {
            Util.mergeParameters(links[i], params);
         }

         setDrillHyperlinks(s, links);
      }
   }

   /**
    * Get hyperlink.
    */
   private boolean hasHyperlink(DataSet data, String col, int row) {
      if(data instanceof BoxDataSet) {
         data = ((BoxDataSet) data).getDataSet();
      }

      if(data instanceof AttributeDataSet) {
         return ((AttributeDataSet) data).getHyperlink(col, row) != null;
      }

      return false;
   }

   /**
    * Get drill hyperlinks.
    */
   private HRef[] getDrillHyperlinks(DataSet data, int col, int row) {
      if(data instanceof BoxDataSet) {
         String header =  data.getHeader(col);
         String baseName = ((BoxDataSet) data).getBaseName(header);
         col = !Tool.equals(header, baseName) ? data.indexOfHeader(baseName) : col;
         data = ((BoxDataSet) data).getDataSet();
      }

      if(!(data instanceof AttributeDataSet) || col >= data.getColCount()) {
         return new HRef[0];
      }

      AttributeDataSet attributeDataSet = (AttributeDataSet) data;
      String header = attributeDataSet.getHeader(col);

      if(GraphUtil.isMappedHeader(header)) {
         header = GraphUtil.getGeoField(header);
         return attributeDataSet.getDrillHyperlinks(header, row);
      }
      else {
         return attributeDataSet.getDrillHyperlinks(col, row);
      }
   }

   /**
    * Get the location of the data point that contains the specified screen
    * coordinates.
    *
    * @param x0 the X-coordinate.
    * @param y0 the Y-coordinate.
    *
    * @return a Point, where x is the row and y is the column of the data point
    *         or <code>null</code> if the specified coordinates are not
    *         contained in any data point.
    */
   public Point locate(int x0, int y0) {
      x0 = (int) (x0 - x);
      y0 = (int) (y0 - y);
      return ((ChartPainter) painter).locate(x0, y0);
   }

   /**
    * Get the all drill hyperlinks on this element for specified location.
    */
   @Override
   public Hyperlink.Ref[] getHyperlinks(Point loc) {
      Enumeration shapes = getHyperlinkAreas();

      // performance consideration
      if(getRotation() != 0 && trans == null) {
         trans = RotationTransformer.getRotationTransformer(getOriginPoint(), this);
      }

      while(shapes.hasMoreElements()) {
         Shape shape = (Shape) shapes.nextElement();

         if(isShapeContainsPoint(shape, loc, trans)) {
            return getHyperlinks(shape);
         }
      }

      return null;
   }

   /**
    * Check if the shape contains the specific point.
    */
   private boolean isShapeContainsPoint(Shape shape, Point loc, RotationTransformer tans) {
      if(trans == null) {
         return shape.contains(loc.x, loc.y);
      }

      loc = (Point) loc.clone();
      loc.x += getLocation().x;
      loc.y += getLocation().y;
      loc.y = -loc.y;
      loc = trans.getRotated2OriginalPos(loc);
      loc.y = -loc.y;
      loc.x -= getLocation().x;
      loc.y -= getLocation().y;
      return shape.contains(loc.x, loc.y);
   }

   /**
    * Get the origin point.
    */
   public Point getOriginPoint() {
      if(isFragment()) {
         int x = getLocation().x;
         int y = getLocation().y - getYOffset();

         return new Point(x, y);
      }

      return new Point(getLocation().x, getLocation().y);
   }

   /**
    * Set the report element that this paintable area corresponds to.
    */
   @Override
   public void setElement(ReportElement elem) {
      super.setElement(elem);

      if(painter instanceof ChartPainter && elem instanceof ChartElementDef) {
         ((ChartPainter) painter).chart = (ChartElementDef) elem;
      }
   }

   @Override
   public boolean isSerializable() {
      // chart is script chart, not serializable
      return !(painter instanceof ChartPainter) || !((ChartPainter) painter).scriptChart;
   }

   @Override
   protected int getBorderWidth() {
      ChartPainter cpainter = (ChartPainter) getPainter();
      ChartDescriptor desc = cpainter.getChartDescriptor();
      // add a pixel for grid border. (57729)
      return desc.getPlotDescriptor().isFacetGrid() ? 1 : 0;
   }

   private transient RotationTransformer trans;
   private static final Logger LOG = LoggerFactory.getLogger(ChartPainterPaintable.class);
}
