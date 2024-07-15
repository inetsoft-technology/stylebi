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
package inetsoft.graph.coord;

import inetsoft.graph.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DataSetIndex;
import inetsoft.graph.geo.*;
import inetsoft.graph.geo.service.WebMapPainter;
import inetsoft.graph.geo.service.WebMapService;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.internal.text.TextLayoutManager;
import inetsoft.graph.scale.*;

import java.awt.geom.*;
import java.util.*;

/**
 * This coordinate is used to map longtitude and latitude on a rectangular
 * space. The current implementation uses mercator projection.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class GeoCoord extends Coordinate {
   /**
    * Default constructor.
    */
   public GeoCoord() {
   }

   /**
    * Create a geo coord for the map data.
    */
   public GeoCoord(GeoDataSet gdata) {
      this(gdata.getAreaField());
   }

   /**
    * Create a geo coord for the map data.
    * @param field the geography column that is used as map shape feature ids.
    */
   public GeoCoord(String field) {
      setXScale(new LinearScale(GeoDataSet.getLongitudeField(field)));
      setYScale(new LinearScale(GeoDataSet.getLatitudeField(field)));
   }

   /**
    * Check if the dataset should be padded with rows for all shapes in the map.
    */
   public boolean isFullMap() {
      return fullmap;
   }

   /**
    * Set if the dataset should be padded with rows for all shapes in the map.
    */
   public void setFullMap(boolean fullmap) {
      this.fullmap = fullmap;
   }

   /**
    * Get the x scale used in the coordinate.
    */
   public LinearScale getXScale() {
      return xscale;
   }

   /**
    * Set the x scale used in the coordinate.
    */
   public void setXScale(LinearScale xscale) {
      this.xscale = xscale;
      xscale.setScaleOption(0);
   }

   /**
    * Get the y scale used in the coordinate.
    */
   public LinearScale getYScale() {
      return yscale;
   }

   /**
    * Set the y scale used in the coordinate.
    */
   public void setYScale(LinearScale yscale) {
      this.yscale = yscale;
      yscale.setScaleOption(0);
   }

   /**
    * Get the map projection.
    */
   public GeoProjection getProjection() {
      return projection;
   }

   /**
    * Set the map projection.
    */
   public void setProjection(GeoProjection proj) {
      this.projection = (proj == null) ? new MercatorProjection() : proj;
   }

   /**
    * Set the extent of the map. If this is not set, the extent is
    * calculated from the data.
    */
   public void setExtent(double minX, double minY, double maxX, double maxY) {
      if(minX > maxX) {
         double temp = minX;
         minX = maxX;
         maxX = temp;
      }

      if(minY > maxY) {
         double temp = minY;
         minY = maxY;
         maxY = temp;
      }

      this.minX = minX;
      this.minY = minY;
      this.maxX = maxX;
      this.maxY = maxY;
   }

   /**
    * Get the longitude/latitude range used in the current layout. This is the range
    * from data and shape, before it's adjusted for aspect ratio and pan/zoom. The values
    * are not projected.
    */
   public Rectangle2D getLonLat() {
      return lonlat;
   }

   /**
    * Get the zoom factor of this map.
    */
   public double getZoom() {
      return zoom;
   }

   /**
    * Set the zoom factor of this map.
    */
   public void setZoom(double zoom) {
      this.zoom = zoom;
   }

   /**
    * Get the pan offset of x (lon).
    */
   public double getPanX() {
      return panX;
   }

   /**
    * Set the pan offset of x (longitude in projected coordinate).
    */
   public void setPanX(double panX) {
      this.panX = panX;
   }

   /**
    * Get the pan offset of y (lat).
    */
   public double getPanY() {
      return panY;
   }

   /**
    * Set the pan offset of y (latitude in projected coordinate).
    */
   public void setPanY(double panY) {
      this.panY = panY;
   }

   /**
    * Get the padding space added to around the data points.
    */
   public double getPadding() {
      return padding;
   }

   /**
    * Set the padding space added to around the data points. This is a fraction of the
    * lon/lat range.
    * @param padding padding percentage, e.g. 0.1 to add 10% of lat/lon at the edge.
    */
   public void setPadding(double padding) {
      this.padding = padding;
   }

   /**
    * Get the bounding box of the plot area in projected (degree) space. This covers the
    * entire plot area and the lat/lon values may be out of range. For example, if plot
    * is adjusted for aspect ratio, the lat value may be &gt; 180 or &lt; -180.
    */
   public Rectangle2D getBBox() {
      return bbox;
   }

   /**
    * Get the minimum tile size.
    */
   public int getMinTile() {
      return minTile;
   }

   /**
    * Set the minimum tile size. If set, a world map requires at least the specified
    * number of pixels to display. Specifically, the full height of (-180 to 180) requires
    * minTile (e.g. 512) pixels. The zoom level will be adjusted to make sure this
    * requirement is met.
    */
   public void setMinTile(int minTile) {
      this.minTile = minTile;
   }

   /**
    * This method should be called before getPosition() is called for each point on a
    * shape. It determines whether the shape is out of bounds in the coord, and should
    * be 'wrapped' around to the other end.
    */
   public static void startShape(Coordinate coord, double xmin, double xmax) {
      if(coord instanceof GeoCoord) {
         Scale xscale = ((GeoCoord) coord).getXScale();

         // GeoCoord.fit() may center the shapes, so shapes outside of xscale range
         // may still be visiblle. the 2nd condition in 'if' would make sure we dont
         // move the shape unless it's also visible after it's moved. (59120)

         if(xmin > xscale.getMax() && xmax - 360 > xscale.getMin()) {
            xShift.set(-360.0);
         }
         else if(xmax < xscale.getMin() && xmin + 360 < xscale.getMax()) {
            xShift.set(360.0);
         }
         else {
            xShift.set(0.0);
         }
      }
   }

   /**
    * This method should be called after all getPosition() is called on a shape.
    */
   public static void endShape() {
      xShift.remove();
   }

   /**
    * Initialize this coordinate for the specified chart data set.
    */
   @Override
   public void init(DataSet dset) {
      double xmin = Double.MAX_VALUE;
      double xmax = -Double.MAX_VALUE;
      double ymin = Double.MAX_VALUE;
      double ymax = -Double.MAX_VALUE;
      int cnt = (dset instanceof GeoDataSet && isFullMap())
         ? ((GeoDataSet) dset).getFullRowCount() : dset.getRowCount();

      if(cnt == 0 && !isFullMap() && dset instanceof GeoDataSet) {
         cnt = ((GeoDataSet) dset).getFullRowCount();
      }

      setDataSet(dset);

      for(int c = 0; c < dset.getColCount(); c++) {
         String field = dset.getHeader(c);

         if(!GeoShape.class.equals(dset.getType(field))) {
            continue;
         }

         for(int i = 0; i < cnt; i++) {
            GeoShape feature = (GeoShape) dset.getData(field, i);

            if(feature != null) {
               Rectangle2D bounds = feature.getBounds();
               xmin = Math.min(xmin, bounds.getMinX());
               ymin = Math.min(ymin, bounds.getMinY());
               xmax = Math.max(xmax, bounds.getMaxX());
               ymax = Math.max(ymax, bounds.getMaxY());
            }
         }
      }

      // if the explicit latitude/longitude is in the dataset,
      // find the min/max from the latitude/longitude columns
      double[] xpair = getPair(dset, xscale.getFields());
      double[] ypair = getPair(dset, yscale.getFields());

      if(!isEmptyPair(xpair)) {
         xmin = Math.min(xmin, xpair[0]);
         xmax = Math.max(xmax, xpair[1]);
      }

      if(!isEmptyPair(ypair)) {
         ymin = Math.min(ymin, ypair[0]);
         ymax = Math.max(ymax, ypair[1]);
      }

      if(xmin == Double.MAX_VALUE) {
         xmin = projection.getXMin();
         xmax = projection.getXMax();
      }

      if(ymin == Double.MAX_VALUE) {
         ymin = projection.getYMin();
         ymax = projection.getYMax();
      }

      if(padding != 0) {
         double xpad = (xmax - xmin) * padding;
         double ypad = (ymax - ymin) * padding;
         xmin = Math.max(xmin - xpad, projection.getXMin());
         xmax = Math.min(xmax + xpad, projection.getXMax());
         ymin = Math.max(ymin - ypad, projection.getYMin());
         ymax = Math.min(ymax + ypad, projection.getYMax());
      }

      lonlat = new Rectangle2D.Double(xmin, ymin, xmax - xmin, ymax - ymin);

      // apply user set extent
      if(!Double.isNaN(this.minX)) {
         xmin = this.minX;
      }

      if(!Double.isNaN(this.minY)) {
         ymin = this.minY;
      }

      if(!Double.isNaN(this.maxX)) {
         xmax = this.maxX;
      }

      if(!Double.isNaN(this.maxY)) {
         ymax = this.maxY;
      }

      // use projection min/max if no data is available
      if(xmin == Double.MAX_VALUE && xmax == -Double.MAX_VALUE) {
         xmin = projection.getXMin();
         xmax = projection.getXMax();
      }

      if(ymin == Double.MAX_VALUE && ymax == -Double.MAX_VALUE) {
         ymin = projection.getYMin();
         ymax = projection.getYMax();
      }

      // enlarge area for single city
      if(xmin == xmax) {
         xmin = xmin - 10;
         xmax = xmax + 10;
      }

      if(ymin == ymax) {
         ymin = ymin - 10;
         ymax = ymax + 10;
      }

      // project to coordinate space
      ymin = project(0, ymin).getY();
      ymax = project(0, ymax).getY();
      xmin = project(xmin, 0).getX();
      xmax = project(xmax, 0).getX();

      // if not web map, don't pan shapes complete out of bounds (which would be blank).
      if(!isWebMap()) {
         panX = Math.min(panX, xmax - xmin);
         panX = Math.max(panX, xmin - xmax);
         panY = Math.min(panY, ymax - ymin);
         panY = Math.max(panY, ymin - ymax);
      }

      // apply zoom in projected space
      if(zoom != 1) {
         // if not web map, makes no sense to zoom out from the default (complete) view.
         if(!isWebMap()) {
            zoom = Math.max(1, zoom);
         }

         double cx = (xmin + xmax) / 2;
         double cy = (ymin + ymax) / 2;
         double rx = (xmax - xmin) / 2;
         double ry = (ymax - ymin) / 2;

         // don't zoom out too much. mapbox x range is -360 to 360,
         // and y (projected) range is -180 to 180.
         zoom = Math.max(rx / 360, zoom);
         zoom = Math.max(ry / 180, zoom);

         rx /= zoom;
         ry /= zoom;

         xmin = cx - rx;
         xmax = cx + rx;
         ymin = cy - ry;
         ymax = cy + ry;
      }

      /* the while loops below already brings the values within range.
         this shouldn't be needed. (59065)
      // make sure pan doesn't make the map out of valid bounds
      panX = Math.max(panX, -180 - xmin);
      panX = Math.min(panX, 180 - xmin);
      panX = Math.max(panX, -180 - xmax);
      panX = Math.min(panX, 180 - xmax);
       */

      // normalize value
      while(panX > 360) {
         panX -= 360;
      }

      while(panX < -360) {
         panX += 360;
      }

      // apply pan in projected space
      xmin += panX;
      xmax += panX;
      ymin += panY;
      ymax += panY;

      // make sure lat/lon in valid range
      while(xmin < -360) {
         xmin += 360;
         xmax += 360;
      }

      while(xmax > 360) {
         xmin -= 360;
         xmax -= 360;
      }

      xscale.setMin(xmin);
      xscale.setMax(xmax);
      yscale.setMin(ymin);
      yscale.setMax(ymax);

      // coord bounds is the real bounds after layout, and will be available if the
      // graph is re-plotted.
      if(getCoordBounds() != null) {
         init2(getCoordBounds().getWidth(), getCoordBounds().getHeight());
      }
      // layout size is the graph size and is same as coord size if there is no legend.
      else if(getLayoutSize() != null) {
         init2(getLayoutSize().getWidth(), getLayoutSize().getHeight());
      }
   }

   /**
    * Recalculate the scales by taking consideration of physical dimensions.
    */
   private void init2(final double w, final double h) {
      double xmin = xscale.getMin();
      double xmax = xscale.getMax();
      double ymin = yscale.getMin();
      double ymax = yscale.getMax();
      double yrange = ymax - ymin;
      double xrange = xmax - xmin;
      initW = w;
      initH = h;

      // make sure the w/h is in proportion to the longitude/latitude ratio.
      // only need to do this for web map so they line up well. otherwise the
      // aspect ratio is adjusted in fit() by centering the shapes. (52170)
      if(xrange != 0 && yrange != 0 && isWebMap()) {
         if(w / h > xrange / yrange) {
            // adjust bbox to fill in width
            xrange = yrange * w / h;
            double center = (xmin + xmax) / 2;
            xmin = center - xrange / 2;
            xmax = center + xrange / 2;
         }
         else {
            // adjust bbox to fill in height
            yrange = xrange * h / w;
            double center = (ymin + ymax) / 2;
            ymin = center - yrange / 2;
            ymax = center + yrange / 2;
         }

         // make sure lon/lat is in range. (52014)
         if(xmax - xmin > 360) {
            double diff = xmax - xmin - 360;
            xmin += diff / 2;
            xmax -= diff / 2;

            // prefMin and prefMax is the south and north most point with meaningful population
            // at bottom of argentina and top of norway (in projected space where the value
            // is in range of -180 to 180). when we reduce the y range, we prefer to keep
            // as much of the populated area as possible, before evenly split between top
            // and bottom. (54231)
            final double prefMin = -80, prefMax = 135;
            double ydiff = yrange * diff / xrange;

            if(ymax > prefMax) {
               double adj = Math.min(ymax - prefMax, ydiff);
               ymax -= adj;
               ydiff -= adj;
            }

            if(ymin < prefMin) {
               double adj = Math.min(prefMin - ymin, ydiff);
               ymin += adj;
               ydiff -= adj;
            }

            ymin += ydiff / 2;
            ymax -= ydiff / 2;
         }

         // y (projected) range is from -180 to 180.
         if(ymax - ymin > 360) {
            double diff = ymax - ymin - 360;
            ymin += diff / 2;
            ymax -= diff / 2;

            double xdiff = xrange * diff / yrange;
            xmin += xdiff / 2;
            xmax -= xdiff / 2;
         }
      }

      // mapbox won't zoom out beyond a single tile, so if we scale to less than that
      // size, the plot will not be aligned with the map background. here we force the
      // scale to have at least the minimum number of pixels.
      if(minTile > 0) {
         yrange = ymax - ymin;
         xrange = xmax - xmin;
         double yPercent = yrange / 360;
         double tilePercent = h / (double) minTile;

         if(tilePercent < yPercent) {
            double nyrange = 360.0 * h / minTile;
            double adj = (yrange - nyrange) / 2;
            ymin += adj;
            ymax -= adj;

            double nxrange = xrange * nyrange / yrange;
            adj = (xrange - nxrange) / 2;
            xmin += adj;
            xmax -= adj;
         }
      }

      xscale.setMin(xmin);
      xscale.setMax(xmax);
      yscale.setMin(ymin);
      yscale.setMax(ymax);

      if(getPlotSpec().getBackgroundPainter() != null) {
         getPlotSpec().getBackgroundPainter().prepareCoordinate(this, (int) w, (int) h);
      }
   }

   /**
    * Map needs to keep the map latitude/longitude range at the same aspect ratio, which
    * requires the size to be known when initialization the coordinate. But that size
    * could change later when legend is laid out. In that case, we will need to recreate
    * the chart, at which point coord bounds is known.
    */
   @Override
   public boolean requiresReplot() {
      // needs correct coord size to be used in init() for setting aspect ratio as well as
      // web map layout.
      Rectangle2D bounds = getCoordBounds();
      return bounds != null && (initW != bounds.getWidth() || initH != bounds.getHeight());
   }

   /**
    * Check if the pair is empty.
    */
   private boolean isEmptyPair(double[] pair) {
      return pair[0] == 0 && pair[1] == 0;
   }

   /**
    * Get point fields min and max pair.
    * @param dset map dataset.
    * @param fields scale fields.
    */
   private double[] getPair(DataSet dset, String[] fields) {
      List<String> pflds = new ArrayList<>(); // point fields

      for(int i = 0; i < fields.length; i++) {
         if(!GeoShape.class.equals(dset.getType(fields[i]))) {
            pflds.add(fields[i]);
         }
      }

      String[] cols = pflds.toArray(new String[pflds.size()]);
      ScaleRange calc = new LinearRange();

      return calc.calculate(dset, cols, null);
   }

   /**
    * Map a tuple (from logic coordinate space) to the chart coordinate space.
    * @param tuple the tuple in logic space (scaled values).
    * @return position in chart coordinate space.
    */
   @Override
   public Point2D getPosition(double[] tuple) {
      double x = getValue(tuple, 0);
      double y = getValue(tuple, 1);
      Double xShift = GeoCoord.xShift.get();

      // Since map x (longitude) is continuous, a position of 361 is same as 1,
      // so if the coord is from 0 to 361, the point should be mapped to 1
      // instead of 361 (which is outside of view according to the value
      // but is 'wrapped' to position 1).

      if(xShift != null) {
         x += xShift;
      }
      else if(x < xscale.getMin()) {
         // don't move it out of bounds. (60832)
         if(x + 360 < xscale.getMax()) {
            x += 360;
         }
      }
      else if(x > xscale.getMax()) {
         if(x - 360 > xscale.getMin()) {
            x -= 360;
         }
      }

      // projection
      Point2D pt = project(x, y);

      x = getPosition(xscale, pt.getX(), GWIDTH);
      y = getPosition(yscale, pt.getY(), GHEIGHT);

      return new Point2D.Double(x, y);
   }

   /**
    * Get the interval size in this coordinate space.
    * @param interval interval value.
    * @return interval size in this coordinate space.
    */
   @Override
   public double getIntervalSize(double interval) {
      return getIntervalSize(yscale, interval, GWIDTH);
   }

   /**
    * Create axis and other guides for the graph.
    * @param vgraph visual graph to create axis.
    */
   @Override
   public void createAxis(VGraph vgraph) {
      // no axis
   }

   /**
    * Set the screen transformation to fit the graph to the specified output
    * size.
    * @param x horizontal position.
    * @param y vertical position.
    * @param w width to scale to.
    * @param h height to scale to.
    */
   @Override
   public void fit(double x, double y, double w, double h) {
      VGraph vgraph = getVGraph();
      vgraph.setPlotBounds(new Rectangle2D.Double(x, y, w, h));

      AffineTransform c2 = getScaledCoordTransform(x, y, w, h);
      double plotw = w;
      double ploth = h;
      double plotx = x;
      double ploty = y;
      double xrange = xscale.getMax() - xscale.getMin();
      double yrange = yscale.getMax() - yscale.getMin();
      bbox = new Rectangle2D.Double(xscale.getMin(), yscale.getMin(), xrange, yrange);

      // make sure the w/h is in proportion to the longitude/latitude ratio.
      if(xrange != 0 && yrange != 0) {
         if(w / h > xrange / yrange) {
            plotw = (xrange / yrange) * h;
            plotx = x + (w - plotw) / 2;
         }
         else {
            ploth = (yrange / xrange) * w;
            ploty = y + (h - ploth) / 2;
         }
      }

      // vgraph transform
      AffineTransform trans = new AffineTransform();

      trans.translate(x, y);
      trans.scale(plotw / GWIDTH, ploth / GHEIGHT);
      vgraph.concat(trans, true);

      // this must be called after the scale and before c2 is applied
      Rectangle2D box = getElementBounds(vgraph, true);
      double xs = 1;
      double ys = 1;

      if(box != null && !box.isEmpty()) {
         double left = getElementMargin(x - box.getX());
         double bottom = getElementMargin(y - box.getY());
         double right = getElementMargin(box.getMaxX() - x - w + 1);
         double top = getElementMargin(box.getMaxY() - y - h + 1);

         if(left + right + top + bottom > 0) {
            xs = w / box.getWidth();
            ys = h / box.getHeight();

            // use min to make sure the aspect ratio is the same
            xs = ys = Math.min(1, Math.min(xs, ys));

            // used to center the map in the plot
            double contentw = (plotw + left + right) * xs;
            double contenth = (ploth + top + bottom) * ys;

            plotx = x + (w - contentw) / 2 + left * xs;
            ploty = y + (h - contenth) / 2 + bottom * ys;
         }
      }

      AffineTransform trans2 = new AffineTransform();
      trans2.translate(plotx, ploty);
      trans2.scale(xs, ys);
      trans2.translate(-x, -y);
      vgraph.concat(trans2, false);

      vgraph.concat(c2, false);
      layoutText(vgraph, true);
   }

   /**
    * Get a vgraph for calculating element bounds.
    */
   @Override
   VGraph getScaledVGraph() {
      VGraph vgraph = (VGraph) getVGraph().clone();
      double x = getCoordBounds().getX();
      double y = getCoordBounds().getY();
      double w = getCoordBounds().getWidth();
      double h = getCoordBounds().getHeight();
      double plotw = w;
      double ploth = h;
      double plotx = x;
      double ploty = y;
      double xrange = xscale.getMax() - xscale.getMin();
      double yrange = yscale.getMax() - yscale.getMin();

      // make sure the w/h is in proportion to the longitude/latitude ratio
      if(xrange != 0 && yrange != 0) {
         if(w / h > xrange / yrange) {
            plotw = (xrange / yrange) * h;
            plotx = x + (w - plotw) / 2;
         }
         else {
            ploth = (yrange / xrange) * w;
            ploty = y + (h - ploth) / 2;
         }
      }

      // vgraph transform
      AffineTransform trans = new AffineTransform();

      trans.translate(plotx, ploty);
      trans.scale(plotw / GWIDTH, ploth / GHEIGHT);
      vgraph.concat(trans, true);

      Rectangle2D pbounds = new Rectangle2D.Double(x, y, w, h);
      vgraph.setPlotBounds(pbounds);
      // the mergeElementBounds needs plot bounds, setting here is safe
      // as long as the getElementBounds is called before fit
      getVGraph().setPlotBounds(pbounds);

      return vgraph;
   }

   /**
    * Get the number of dimensions in this coordinate.
    */
   @Override
   public int getDimCount() {
      return 2;
   }

   /**
    * Get the scales used in the coordinate.
    */
   @Override
   public Scale[] getScales() {
      return new Scale[] { xscale, yscale };
   }

   /**
    * Create graph geometry (nested chart) for this coordinate.
    * @hidden
    * @param rset the specified root data set.
    * @param ridx the specified index for root data set.
    * @param dset dataset of this graph. This may be a subset if this coordinate
    * is a nested coordinate.
    * @param egraph the top-level graph definition.
    * @param ggraph the parent ggraph.
    * @param facet the specified facet coordinate.
    * @param hint the specified position hint.
    */
   @Override
   public void createSubGGraph(DataSet rset, DataSetIndex ridx, DataSet dset,
                               EGraph egraph, GGraph ggraph, FacetCoord facet, Map xmap, Map ymap,
                               int hint)
   {
      // do nothing
   }

   /**
    * Get unit minimum width.
    * @hidden
    */
   @Override
   public double getUnitMinWidth() {
      return 150;
   }

   /**
    * Get unit minimum height.
    * @hidden
    */
   @Override
   public double getUnitMinHeight() {
      return 100;
   }

   /**
    * Get unit preferred width.
    * @hidden
    */
   @Override
   public double getUnitPreferredWidth() {
      return 400;
   }

   /**
    * Get unit preferred height.
    * @hidden
    */
   @Override
   public double getUnitPreferredHeight() {
      return 300;
   }

   /**
    * This coord has not axis.
    */
   @Override
   public Axis[] getAxes(boolean recursive) {
      return new Axis[0];
   }

   /**
    * Geo coord doesn't support shared scales.
    */
   @Override
   public Object clone(boolean srange) {
      return super.clone();
   }

   /**
    * Map the longitude/latitude to x/y.
    */
   private Point2D project(double x, double y) {
      return projection.project(x, y);
   }

   /**
    * Check if the coordinate has the same structure as this.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      GeoCoord coord = (GeoCoord) obj;
      return projection.getClass().equals(coord.projection.getClass()) &&
         fullmap == coord.fullmap;
   }

   public WebMapService getWebMapService() {
      BackgroundPainter bg = getPlotSpec().getBackgroundPainter();
      return bg instanceof WebMapPainter ? ((WebMapPainter) bg).getWebMapService() : null;
   }

   private boolean isWebMap() {
      return getWebMapService() != null;
   }

   @Override
   public int[] getMaxSteps(String[] lines) {
      int[] maxSteps = super.getMaxSteps(lines);

      // don't move labels too far from points.
      maxSteps[0] = maxSteps[2] = maxSteps[0] / 3;
      maxSteps[1] = maxSteps[3] = 4;
      return maxSteps;
   }

   @Override
   protected void resolveTextOverlapping() {
      TextLayoutManager layout = new TextLayoutManager(getVGraph());
      // don't force labels in plot, or when map is panned, labels out of bounds will all
      // be pushed on the edges.
      layout.setForceInPlot(false);
      layout.resolve();
   }

   private GeoProjection projection = new MercatorProjection();
   private LinearScale xscale;
   private LinearScale yscale;
   private boolean fullmap = true;
   private double minX = Double.NaN;
   private double minY = Double.NaN;
   private double maxX = Double.NaN;
   private double maxY = Double.NaN;
   private double zoom = 1;
   private double panX, panY;
   private double padding = 0;
   private Rectangle2D lonlat;
   private Rectangle2D bbox;
   private int minTile = 0;
   private double initW = -1, initH = -1;
   private static ThreadLocal<Double> xShift = new ThreadLocal<>();
   private static final long serialVersionUID = 1L;
}
