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

import com.inetsoft.build.tern.*;
import inetsoft.graph.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DataSetIndex;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.geometry.GraphGeometry;
import inetsoft.graph.guide.axis.*;
import inetsoft.graph.internal.*;
import inetsoft.graph.scale.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.function.Predicate;

/**
 * A facet coordinate is used to create a chart of chart, or nested charts.
 * It is a coordinate with nested coordinates.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=FacetCoord")
public class FacetCoord extends Coordinate {
   /**
    * Default constructor. The outer and inner coordinates must be explicitly
    * set before this object is used.
    */
   public FacetCoord() {
      super();
   }

   /**
    * Create a nested coordinate.
    * @param outer outer coordinate.
    * @param minner inner prototype coordinate. It will be cloned to be used
    * as the coordinate for the sub-graphs.
    */
   public FacetCoord(Coordinate outer, Coordinate minner) {
      this(outer, new Coordinate[] {minner}, true);
   }

   /**
    * Create a nested coordinate.
    * @param outer outer coordinate.
    * @param minners inner prototype coordinates. They will be cloned to be used
    * as the coordinates for the sub-graphs.
    */
   @TernConstructor
   public FacetCoord(Coordinate outer, Coordinate[] minners, boolean vertical) {
      super();
      this.vertical = vertical;

      setOuterCoordinate(outer);
      setInnerCoordinates(minners);
   }

   /**
    * Initialize this coordinate for the specified chart data set.
    */
   @Override
   public void init(DataSet dset) {
      Scale[] scales = getScales();

      for(int i = 0; i < scales.length; i++) {
         // linear scale should not be initiated, e.g. stack linear scale
         if(scales[i] instanceof LinearScale) {
            continue;
         }

         scales[i].init(dset);
      }

      setDataSet(dset);
   }

   /**
    * Get the inner nested coordinates.
    */
   @TernMethod
   public Coordinate[] getInnerCoordinates() {
      return minner.getCoordinates();
   }

   /**
    * Set the inner nested coordinates.
    */
   @TernMethod
   public void setInnerCoordinates(Coordinate[] minners) {
      this.minner = vertical ? TileCoord.vertical(minners) : TileCoord.horizontal(minners);

      if(minners == null) {
         throw new RuntimeException("Inner coord can't be null");
      }

      int innerFacet = 0;

      for(int i = 0; i < minners.length; i++) {
         if(minners[i] == null) {
            throw new RuntimeException("Inner coord can't be null");
         }

         if(minners[i] instanceof FacetCoord) {
            innerFacet++;
         }
      }

      if(innerFacet > 0 && (innerFacet != 1 || minners.length > 1)) {
         throw new RuntimeException("Only single FacetCoord can be nested");
      }
   }

   /**
    * Get the outer coordinate.
    */
   @TernMethod
   public Coordinate getOuterCoordinate() {
      return outer;
   }

   /**
    * Set the outer coordinate.
    */
   @TernMethod
   public void setOuterCoordinate(Coordinate outer) {
      this.outer = outer;

      if(outer != null && !RectCoord.class.equals(outer.getClass())) {
         throw new RuntimeException("Only RectCoord is supported by FacetCoord");
      }

      Scale[] scales = outer == null ? new Scale[0] : outer.getScales();

      for(int i = 0; i < scales.length; i++) {
         if(scales[i] instanceof CategoricalScale) {
            ((CategoricalScale) scales[i]).setFill(true);
            ((CategoricalScale) scales[i]).setGrid(true);
         }

         scales[i].getAxisSpec().setTickVisible(false);
      }
   }

   /**
    * Get the inner coordinate prototype.
    */
   @TernMethod
   public TileCoord getProtoInnerCoord() {
      return minner;
   }

   /**
    * Set the inner coordinates.
    */
   @TernMethod
   void setTileCoords(TileCoord[][] inners) {
      this.inners = inners;
      this.expandedInnerCoords = null;
   }

   /**
    * Set the inner coordinates.
    * @hidden
    */
   @TernMethod
   public TileCoord[][] getTileCoords() {
      return inners;
   }

   /**
    * Transform a shape or point to this coordinate space.
    * @param shape a shape or a point to transform.
    * @return the transformed shape or point.
    */
   @Override
   public Object transformShape(Object shape) {
      // do nothing
      return null;
   }

   /**
    * Map a tuple (from logic coordinate space) to the chart coordinate space.
    * @param tuple the tuple in logic space (scaled values).
    * @return the position of specified tuple.
    */
   @Override
   @TernMethod
   public Point2D getPosition(double[] tuple) {
      // do nothing
      return null;
   }

   /**
    * Get the interval size in this coordinate space.
    */
   @Override
   @TernMethod
   public double getIntervalSize(double interval) {
      // do nothing
      return 0;
   }

   /**
    * Create the geometry objects in graph.
    * @hidden
    * @param rset the specified root data set.
    * @param ridx the specified index for root data set.
    * @param dset the specified sub data set.
    * @param egraph the graph definition.
    * @param ggraph if passed in, new geometry objects are added to this graph.
    * Otherwise a new ggraph is created and returned.
    * @param hint the specified position hint.
    */
   @Override
   public GGraph createGGraph(DataSet rset, DataSetIndex ridx, DataSet dset,
                              EGraph egraph, GGraph ggraph, Map xmap, Map ymap, int hint)
   {
      if(ggraph == null) {
         ggraph = egraph.createGGraph(this, dset);
      }

      // no outer? create runtime coords by itself
      if(outer == null) {
         TileCoord inner = (TileCoord) minner.clone();
         Coordinate[] coords = inner.getCoordinates();

         inner.setHint(hint);
         inner.setParentCoordinate(this);

         for(int i = 0; i < coords.length; i++) {
            Coordinate coord = coords[i];
            coord.init(dset);
         }

         double[] tuple = new double[0];

         for(int i = 0; i < coords.length; i++) {
            Coordinate coord = coords[i];
            GGraph sub = coord.createGGraph(rset, ridx, dset, egraph, null, xmap, ymap, -1);
            GraphGeometry geom = new GraphGeometry(sub, this, coord, tuple);
            ggraph.addGeometry(geom);
         }

         setTileCoords(new TileCoord[][] {{inner}});
      }
      // outer exists? let outer create runtime coords
      else {
         outer.setParentCoordinate(this);
         outer.createSubGGraph(rset, ridx, dset, egraph, ggraph, this, xmap, ymap, hint);
      }

      initScaleWeights();
      initInnerCoords();

      return ggraph;
   }

   /**
    * Any initialization of the inner coords.
    */
   private void initInnerCoords() {
      Coordinate[][] arr = getExpandedInnerCoords();
      boolean topLevel = getParentCoordinate() == null;

      for(int r = 0; r < arr.length; r++) {
         Coordinate[] arr1 = arr[r];

         for(int c = 0; c < arr1.length; c++) {
            Coordinate coord = arr1[c];

            if(coord instanceof RectCoord) {
               RectCoord rect = (RectCoord) coord;

               // the scales are fully initialized at this point.
               // only align for the rightmost inner coord of the top most facet. this way
               // all left axes (which share ranges) will be aligned to the rightmost
               // y axis. (55280)
               if(c == arr1.length - 1 && topLevel) {
                  rect.alignZero();
               }

               // apply padding to min/max labels so axes next to each other on facet
               // won't have labels appears to be concatenated, (53503)
               // e.g. 100 0 appears as 1000.
               Scale left = rect.getScaleAt(ICoordinate.LEFT_AXIS);
               Scale bottom = rect.getScaleAt(ICoordinate.BOTTOM_AXIS);
               final int padding = 2;

               if(left != null && Math.abs(left.getAxisSpec().getTextSpec().getRotation()) == 90) {
                  left.getAxisSpec().setMinPadding(r == 0 ? 0 : padding);
                  left.getAxisSpec().setMaxPadding(r == arr.length - 1 ? 0 : padding);
               }

               if(bottom != null && bottom.getAxisSpec().getTextSpec().getRotation() == 0) {
                  bottom.getAxisSpec().setMinPadding(c == 0 ? 0 : padding);
                  bottom.getAxisSpec().setMaxPadding(c == arr1.length - 1 ? 0 : padding);
               }
            }
         }
      }
   }

   /**
    * Expand all nested coordinates into a flat two-dimensional array.
    */
   @TernMethod
   public Coordinate[][] getExpandedInnerCoords() {
      if(inners.length == 0) {
         return new Coordinate[0][0];
      }

      if(expandedInnerCoords != null) {
         return expandedInnerCoords;
      }

      Coordinate[][][][] coords = new Coordinate[inners.length][inners[0].length][][];

      for(int i = 0; i < inners.length; i++) {
         for(int j = 0; j < inners[i].length; j++) {
            coords[i][j] = inners[i][j].getExpandedInnerCoords();
         }
      }

      return expandedInnerCoords = flattenCoords(coords);
   }

   /**
    * Flatten a two-dimensional array of two-dimensional array into a flat
    * two-dimensional array.
    */
   static Coordinate[][] flattenCoords(Coordinate[][][][] coords) {
      int nrow = 0;
      int ncol = 0;

      for(int i = 0; i < coords.length; i++) {
         if(coords[i].length > 0) {
            nrow += coords[i][0].length;
         }
      }

      for(int i = 0; coords.length > 0 && i < coords[0].length; i++) {
         if(coords[0][i].length > 0) {
            ncol += coords[0][i][0].length;
         }
      }

      Coordinate[][] arr = new Coordinate[nrow][ncol];
      int row = 0, col = 0;

      for(int i = 0; i < coords.length; i++) {
         int r = row;

         for(int j = 0; j < coords[i].length; j++) {
            int c = col;
            r = row;

            for(int r2 = 0; r2 < coords[i][j].length; r2++) {
               c = col;

               for(int c2 = 0; c2 < coords[i][j][r2].length; c2++) {
                  arr[r][c++] = coords[i][j][r2][c2];
               }

               r++;
            }

            col = c;
         }

         row = r;
	 col = 0;
      }

      return arr;
   }

   /**
    * Init scale weights.
    */
   private void initScaleWeights() {
      if(inners == null) {
         return;
      }

      if(outer instanceof RectCoord) {
         RectCoord rect = (RectCoord) outer;
         CategoricalScale xScale = (CategoricalScale) rect.getXScale();

         if(xScale != null && inners.length > 0) {
            Object[] xvalues = xScale.getValues();

            for(int i = 0; i < xvalues.length && i < inners[0].length; i++) {
               xScale.setWeight(xvalues[i], inners[0][i].getXUnitCount());
            }
         }

         CategoricalScale yScale = (CategoricalScale) rect.getYScale();

         if(yScale != null && inners.length > 0 && inners[0].length > 0) {
            Object[] yvalues = yScale.getValues();

            for(int i = 0; i < yvalues.length && i < inners.length; i++) {
               yScale.setWeight(yvalues[i], inners[i][0].getYUnitCount());
            }
         }
      }

      for(int i = 0; i < inners.length; i++) {
         for(int j = 0; j < inners[i].length; j++) {
            TileCoord acoord = inners[i][j];
            Coordinate[] coords = acoord.getCoordinates();

            for(int k = 0; k < coords.length; k++) {
               Coordinate coord = coords[k];

               if(coord instanceof FacetCoord) {
                  ((FacetCoord) coord).initScaleWeights();
               }
            }
         }
      }
   }

   /**
    * Create graph geometry (nested chart) for this coordinate.
    * @hidden
    * @param rset the specified root data set.
    * @param ridx the specified index for root data set.
    * @param dset dataset of this graph. This may be a subset if this coordinate
    * is a nested coordinate.
    * @param graph the top-level graph definition.
    * @param ggraph the parent ggraph.
    * @param facet the facet coordinate.
    * @param hint the specified hint.
    */
   @Override
   public void createSubGGraph(DataSet rset, DataSetIndex ridx, DataSet dset,
                               EGraph graph, GGraph ggraph, FacetCoord facet, Map xmap, Map ymap,
                               int hint)
   {
      // do nothing
   }

   /**
    * Create axis and other guides for the visual graph.
    */
   @Override
   public void createAxis(VGraph vgraph) {
      // if outer coord has no axis or null, outer grid line(separator line) can
      // not be created, so fake axis requires
      if(getParentCoordinate() == null) {
         if(outer == null) {
            outer = new RectCoord();
         }

         if(outer instanceof RectCoord) {
            RectCoord coord = (RectCoord) outer;

            if(coord.getXScale() == null) {
               coord.setXScale(GTool.createFakeScale(coord.getYScale()));
            }

            if(coord.getYScale() == null) {
               coord.setYScale(GTool.createFakeScale(coord.getXScale()));
            }

            coord.setFacet(this);
         }
      }

      if(outer != null) {
         outer.createAxis(vgraph);
      }

      for(int i = 0; i < inners.length; i++) {
         for(int j = 0; j < inners[i].length; j++) {
            TileCoord acoord = inners[i][j];
            Coordinate[] coords = acoord.getCoordinates();

            for(int k = 0; k < coords.length; k++) {
               Coordinate coord = coords[k];

               // if coord is inner most, create axis by itself, otherwise
               // painting all axises on root visual graph leads to z index
               // problem
               if(coord instanceof FacetCoord) {
                  coord.createAxis(vgraph);
               }
            }
         }
      }

      if(outer instanceof RectCoord) {
         setupAxis((RectCoord) outer, BOTTOM_AXIS);
         setupAxis((RectCoord) outer, TOP_AXIS);
         setupAxis((RectCoord) outer, LEFT_AXIS);
         setupAxis((RectCoord) outer, RIGHT_AXIS);
      }
   }

   /**
    * Setup the facet axis properties.
    */
   private void setupAxis(RectCoord coord, int axisPos) {
      DefaultAxis axis = coord.getAxisAt(axisPos);

      if(axis != null) {
         FacetCoord top = (FacetCoord) GTool.getTopCoordinate(this);
         Scale scale = axis.getScale();
         axis.setCenterLabel(true);

         // facet border not supported in 3d coord
         if(top.isFacetGrid() && getDepth() == 0 && scale != null &&
            scale.getFields().length > 0 && axis.isLabelVisible() &&
            scale.getAxisSpec().isLineVisible())
         {
            DefaultAxis[] inner = getAxesAt(axisPos);

            // turn on axis line if no axis line immediately next to it
            axis.setLineVisible(inner.length > 1 &&
               isLabelVisible(Arrays.copyOfRange(inner, 1, inner.length)));
         }
         else {
            axis.setLineVisible(false);
         }
      }
   }

   /**
    * Set the screen transformation to fit the graph to the coord bounds.
    */
   @Override
   @TernMethod
   public void fit(double x, double y, double w, double h) {
      AffineTransform c2 = getScaledCoordTransform(x, y, w, h);

      double toph = getAxisSize(TOP_AXIS);
      double bottomh = getAxisSize(BOTTOM_AXIS);
      double leftw = getAxisSize(LEFT_AXIS);
      double rightw = getAxisSize(RIGHT_AXIS);
      double plotx = x + leftw;
      double ploty = y + bottomh;
      double plotw = w - leftw - rightw;
      double ploth = h - toph - bottomh;
      VGraph vgraph = getVGraph();

      // transform vgraph
      if(vgraph != null) {
         AffineTransform trans = new AffineTransform();
         trans.translate(plotx, ploty);
         trans.scale(plotw / GWIDTH, ploth / GHEIGHT);
         vgraph.concat(trans, true);
      }

      // fit outer coord
      if(outer != null) {
         outer.setCoordBounds(new Rectangle2D.Double(x, y, w, h));
         outer.fit(x, y, w, h);
      }

      // call after outer.fit to override plot bounds set by RectCoord
      if(vgraph != null) {
         vgraph.setPlotBounds(getBounds(plotx, ploty, plotw, ploth, c2));
      }

      if(getParentCoordinate() == null) {
         layout(x, y, 0, 0);
         // make sure shared bounds are calculated before fit
         calcElementBounds();
      }

      for(int i = 0; i < inners.length; i++) {
         for(int j = 0; j < inners[i].length; j++) {
            if(vgraph != null && vgraph.isCancelled()) {
               return;
            }

            inners[i][j].fit();
         }
      }

      if(getParentCoordinate() == null) {
         createGrid(x, y, w, h);
      }
   }

   /**
    * Layout (position and size) of inner coordinates.
    * @param x the horizontal position.
    * @param y the vertical position.
    * @param cellw the unit width.
    * @param cellh the unit height.
    */
   @TernMethod
   public void layout(double x, double y, double cellw, double cellh) {
      // fit inner coords
      if(cellw <= 0 || cellh <= 0) {
         double depth = getDepth();
         VGraph topGraph = GTool.getTopVGraph(this);

         cellw = topGraph.getUnitWidth();
         cellh = topGraph.getUnitHeight();

         // subtract one depth from the width
         if(depth > 0 && inners.length > 0) {
            int xcnt = 0;

            for(int i = 0; i < inners[0].length; i++) {
               xcnt += inners[0][i].getXUnitCount();
            }

            if(getInnerCoordinates().length <= 1 || vertical) {
               cellw = (cellw * xcnt - depth) / xcnt;
            }
         }
      }

      double innery = y + (outer == null ? 0 : outer.getAxisSize(BOTTOM_AXIS));

      for(int i = 0; i < inners.length; i++) {
         double innerx = x + (outer == null ? 0 : outer.getAxisSize(LEFT_AXIS));

         for(int j = 0; j < inners[i].length; j++) {
            TileCoord coord = inners[i][j];
            double innerw = coord.getXUnitCount() * cellw;
            double innerh = coord.getYUnitCount() * cellh;

            innerh = innerh + coord.getAxisSize(TOP_AXIS);
            innerh = innerh + coord.getAxisSize(BOTTOM_AXIS);
            innerw = innerw + coord.getAxisSize(LEFT_AXIS);
            innerw = innerw + coord.getAxisSize(RIGHT_AXIS);
            coord.setCoordBounds(new Rectangle2D.Double(innerx, innery, innerw, innerh));
            coord.layout(innerx, innery, cellw, cellh);
            innerx = innerx + innerw;

            if(j == inners[i].length - 1) {
               innery = innery + innerh;
            }
         }
      }
   }

   /**
    * Create the grid lines and borders.
    */
   private void createGrid(double x, double y, double w, double h) {
      if(!(outer instanceof RectCoord)) {
         return;
      }

      // create the grid for the top-level facet
      createGridLines(x, y, w, h);

      // facet border not supported in 3d coord
      if(isFacetGrid() && getDepth() == 0) {
         createFacetGrid(x, y, w, h);
      }
   }

   /**
    * Create the grid lines.
    */
   private void createGridLines(double x, double y, double w, double h) {
      RectCoord outer2 = (RectCoord) outer;
      DefaultAxis topAxis = outer2.getAxisAt(TOP_AXIS);
      DefaultAxis leftAxis = outer2.getAxisAt(LEFT_AXIS);
      DefaultAxis[] rightAxes = getAxesAt(RIGHT_AXIS);
      DefaultAxis[] topAxes = getAxesAt(TOP_AXIS);
      DefaultAxis[] leftAxes = getAxesAt(LEFT_AXIS);
      DefaultAxis[] bottomAxes = getAxesAt(BOTTOM_AXIS);
      Coordinate[][] einners = getExpandedInnerCoords();
      RectCoord inner = einners.length > 0 && einners[0].length > 0 &&
         einners[0][0] instanceof RectCoord ? (RectCoord) einners[0][0] : null;
      DefaultAxis xaxis = inner == null ? null : inner.getAxisAt(BOTTOM_AXIS);
      DefaultAxis yaxis = inner == null ? null : inner.getAxisAt(LEFT_AXIS);
      // horizontal grid color
      Color hGridColor = (xaxis == null || !xaxis.isLineVisible() ||
         // should use top axis if bottom axis hidden (funnel). (57804)
         !xaxis.isLabelVisible() && topAxis != null)
         ? getFirstAxisLineColor(topAxes, leftAxes, rightAxes)
         : xaxis.getScale().getAxisSpec().getLineColor();
      // vertical grid color
      Color vGridColor = (yaxis == null || !yaxis.isLineVisible() ||
         !yaxis.isLabelVisible() && leftAxis != null)
         ? getFirstAxisLineColor(leftAxes, bottomAxes, topAxes)
         : yaxis.getScale().getAxisSpec().getLineColor();
      AxisSpec topSpec = null;
      AxisSpec leftSpec = null;

      if(topAxis != null) {
         // boundaries are already transformed values
         topAxis.setGridLineTransform(new AffineTransform());
         topSpec = topAxis.getScale().getAxisSpec();

         if(isDefaultGridColor(topSpec)) {
            topSpec.setGridColor(vGridColor);
         }
      }

      if(leftAxis != null) {
         leftAxis.setGridLineTransform(new AffineTransform());
         leftSpec = leftAxis.getScale().getAxisSpec();

         if(isDefaultGridColor(leftSpec)) {
            leftSpec.setGridColor(hGridColor);
         }
      }

      if(inners.length == 0 || inners[0].length == 0) {
         return;
      }

      Rectangle2D tbox = getCoordBounds();
      Coordinate[][] coords = getExpandedInnerCoords();
      Coordinate coord = coords.length > 0 && coords[0].length > 0 ? coords[0][0] : null;

      // 2.5d coord don't draw grid on bottom axis, otherwise it may cut through
      // most x labels
      if(coord instanceof Rect25Coord) {
         double axish = coord.getAxisSize(Coordinate.BOTTOM_AXIS);
         tbox = new Rectangle2D.Double(tbox.getX(), tbox.getY() + axish,
            tbox.getWidth(), tbox.getHeight() - axish);
      }

      // create grid at all x point at coord boundaries
      if(topAxis != null && topSpec.getGridStyle() != GraphConstants.NONE) {
         for(int i = 0; i < inners[0].length; i++) {
            int topIdx = inners.length - 1;
            Line2D[] ps = inners[topIdx][i].getXBoundaries(tbox, getCoordBounds(), i==0);

            // ignore grid if only one inner and is 3d coord
            if(getDepth() > 0 && inners[0].length == 1 && ps.length == 2) {
               break;
            }

            Coordinate[] icoords = inners[0][i].getCoordinates();

            // 2.5d coord actually overlaps at the right/left, don't draw grid
            // otherwise it may cut through left most x labels
            if(getDepth() > 0 && icoords.length > 0) {
               for(int k = 0; k < ps.length; k++) {
                  double y1 = (icoords[0] instanceof RectCoord)
                     ? TileCoord.getAxisY((RectCoord) icoords[0], Coordinate.BOTTOM_AXIS)
                     : ps[k].getY1() + getDepth();

                  ps[k] = new Line2D.Double(ps[k].getX1(), y1, ps[k].getX2(), ps[k].getY2());
               }
            }

            int zindex = topSpec.isGridOnTop() ? GDefaults.GRIDLINE_TOP_Z_INDEX :
               (getDepth() > 0) ? GDefaults.GRIDLINE_Z_INDEX : GDefaults.FACET_GRIDLINE_Z_INDEX;

            for(int j = 0; j < ps.length; j++) {
               GridLine line = new GridLine(ps[j], topAxis, zindex);
               topAxis.addGridLine(line);

               // if there is y2 axis, it's color should be used for the right-most
               // grid line instead of the y axis color. (57904, 61233)
               // apply outer facet axis color if there is no inner axis showing at
               // the position. (58022)
               if(j == ps.length - 1 && rightAxes.length > 0 &&
                  i == inners[0].length - 1 &&
                  rightAxes[0].getScale().getAxisSpec().isLineVisible())
               {
                  line.setColor(rightAxes[0].getLineColor());
               }
            }
         }
      }

      // create grid at all y point at coord boundaries
      if(leftAxis != null && leftSpec.getGridStyle() != GraphConstants.NONE) {
         for(int i = 0; i < inners.length; i++) {
            Line2D[] ps = inners[i][0].getYBoundaries(getCoordBounds(),
                                                      getCoordBounds(), i == 0);
            boolean last = i == inners.length - 1;

            // ignore grid if only one inner and is 3d coord
            if(getDepth() > 0 && inners.length == 1 && ps.length == 2) {
               break;
            }

            int zindex = leftSpec.isGridOnTop() ? GDefaults.GRIDLINE_TOP_Z_INDEX :
               (getDepth() > 0) ? GDefaults.GRIDLINE_Z_INDEX : GDefaults.FACET_GRIDLINE_Z_INDEX;
            boolean hasBottomAxis = getFirstAxisLineColor(bottomAxes) != null;
            boolean y2OnTop = Arrays.stream(topAxes)
               .anyMatch(a -> a.getScale() instanceof LinearScale);

            for(int j = 0; j < ps.length; j++) {
               GridLine line = new GridLine(ps[j], leftAxis, zindex);
               leftAxis.addGridLine(line);

               // apply (rotated) y2 axis color. see above.
               // if bottom x axis is visible, the color of the top axis is same as the
               // bottom (and the top axis line color is disabled on gui). (57958)
               if(j == ps.length - 1 && topAxes.length > 0 && last && (!hasBottomAxis || y2OnTop) &&
                  topAxes[topAxes.length - 1].getScale().getAxisSpec().isLineVisible())
               {
                  line.setColor(topAxes[topAxes.length - 1].getLineColor());
               }
            }
         }
      }
   }

   private boolean isDefaultGridColor(AxisSpec leftSpec) {
      return leftSpec.getGridColor() == null ||
         Objects.equals(leftSpec.getGridColor(), GDefaults.DEFAULT_GRIDLINE_COLOR);
   }

   // Get the first visible axis line color. If not found, check the next group of axes,
   // and return default grid line color if no visible axis line is found.
   private Color getFirstAxisLineColor(DefaultAxis[] ...axesGroups) {
      for(DefaultAxis[] axes : axesGroups) {
         Color color = getFirstAxisLineColor(axes);

         if(color != null) {
            return color;
         }
      }

      return GDefaults.DEFAULT_GRIDLINE_COLOR;
   }

   // find the inner-most (both label and line) visible axis line.
   private Color getFirstAxisLineColor(DefaultAxis[] axes) {
      for(int i = axes.length - 1; i >= 0; i--) {
         if(axes[i].getScale().getAxisSpec().isLineVisible() && axes[i].isLabelVisible()) {
            return axes[i].getLineColor();
         }
      }

      return null;
   }

   /**
    * Create the facet grid.
    */
   private void createFacetGrid(double x, double y, double w, double h) {
      DefaultAxis[] tops = getAxesAt(TOP_AXIS);
      DefaultAxis[] lefts = getAxesAt(LEFT_AXIS);
      DefaultAxis[] bottoms = getAxesAt(BOTTOM_AXIS);
      DefaultAxis[] rights = getAxesAt(RIGHT_AXIS);
      VGraph vgraph = getVGraph();
      RectCoord outer2 = (RectCoord) outer;
      DefaultAxis topAxis = outer2.getAxisAt(TOP_AXIS);
      DefaultAxis leftAxis = outer2.getAxisAt(LEFT_AXIS);

      // fix the bounds to make sure they aligns perfectly with the
      // grid lines if the grid lines are used as the border
      double minX = Double.MAX_VALUE;
      double maxX = 0;
      double minY = Double.MAX_VALUE;
      double maxY = 0;

      if(leftAxis != null && leftAxis.getGridLineCount() > 0) {
         if(!isLabelVisible(tops)) {
            GridLine line = leftAxis.getGridLine(leftAxis.getGridLineCount() - 1);
            h = Math.min(h, line.getShape().getBounds2D().getMaxY() - y);
         }

         if(!isLabelVisible(bottoms)) {
            GridLine line = leftAxis.getGridLine(0);
            y = Math.max(y, line.getShape().getBounds2D().getMinY());
         }

         for(int i = 0; i < leftAxis.getGridLineCount(); i++) {
            GridLine line = leftAxis.getGridLine(i);
            Rectangle2D box = line.getShape().getBounds2D();

            minX = Math.min(minX, box.getMinX());
            maxX = Math.max(maxX, box.getMaxX());
         }
      }

      if(topAxis != null && topAxis.getGridLineCount() > 0) {
         if(!isLabelVisible(lefts)) {
            GridLine line = topAxis.getGridLine(0);
            x = Math.max(x, line.getShape().getBounds2D().getMinX());
         }

         if(!isLabelVisible(rights)) {
            GridLine line = topAxis.getGridLine(topAxis.getGridLineCount() - 1);
            w = Math.min(w, line.getShape().getBounds2D().getMaxX() - x);
         }

         for(int i = 0; i < topAxis.getGridLineCount(); i++) {
            GridLine line = topAxis.getGridLine(i);
            Rectangle2D box = line.getShape().getBounds2D();

            minY = Math.min(minY, box.getMinY());
            maxY = Math.max(maxY, box.getMaxY());
         }
      }

      // create borders or mark the grid lines as border
      double top = (maxY > minY) ? Math.min(maxY, y + h) : y + h;
      double bottom = (maxY > minY) ? Math.max(minY, y) : y;
      double left = (maxX > minX) ? Math.max(minX, x) : Math.max(0, x);
      double right = (maxX > minX) ? Math.min(maxX, x + w) : x + w;

      // top border
      if(isLabelVisible(tops)) {
         vgraph.addVisual(createBorderLine(left, top, right, top));
      }
      else if(leftAxis != null && leftAxis.getGridLineCount() > 0) {
         GridLine line = leftAxis.getGridLine(leftAxis.getGridLineCount() - 1);
         line.setColor(getGridColor());
         line.setZIndex(GDefaults.GRIDLINE_TOP_Z_INDEX);
      }
      else {
         vgraph.addVisual(createBorderLine(left, top, right, top));
      }

      // left border
      if(isLabelVisible(lefts)) {
         vgraph.addVisual(createBorderLine(left, bottom, left, top));
      }
      else if(topAxis != null && topAxis.getGridLineCount() > 0) {
         GridLine line = topAxis.getGridLine(0);
         line.setColor(getGridColor());
         line.setZIndex(GDefaults.GRIDLINE_TOP_Z_INDEX);
      }
      else {
         vgraph.addVisual(createBorderLine(left, bottom, left, top));
      }

      // bottom border
      if(isLabelVisible(bottoms)) {
         vgraph.addVisual(createBorderLine(left, bottom, right, bottom));
      }
      else if(leftAxis != null && leftAxis.getGridLineCount() > 0) {
         GridLine line = leftAxis.getGridLine(0);
         line.setColor(getGridColor());
         line.setZIndex(GDefaults.GRIDLINE_TOP_Z_INDEX);
      }
      else {
         vgraph.addVisual(createBorderLine(left, bottom, right, bottom));
      }

      // right border
      if(isLabelVisible(rights)) {
         vgraph.addVisual(createBorderLine(right, bottom, right, top));
      }
      else if(topAxis != null && topAxis.getGridLineCount() > 0) {
         GridLine line = topAxis.getGridLine(topAxis.getGridLineCount() - 1);
         line.setColor(getGridColor());
         line.setZIndex(GDefaults.GRIDLINE_TOP_Z_INDEX);
      }
      // top axis empty, should draw right border or it will be missing
      else {
         vgraph.addVisual(createBorderLine(right, bottom, right, top));
      }

      // due to rotation, the position of the axes may not fall exactly on
      // the bounds, we fix the discrepencies here to make sure the lines
      // don't disconnection or intersect
      minX = x;
      maxX = x + w;
      minY = y;
      maxY = y + h;

      if(maxX > minX && leftAxis != null) {
         for(int i = 0; i < leftAxis.getGridLineCount(); i++) {
            GridLine line = leftAxis.getGridLine(i);
            Line2D.Double shape = (Line2D.Double) line.getShape();
            double minX0 = Math.min(shape.x1, shape.x2);
            double maxX0 = Math.max(shape.x1, shape.x2);

            shape.x1 = minX0;
            shape.x2 = maxX0;

            if(minX0 < minX) {
               shape.x1 = minX;
            }

            if(maxX0 > maxX) {
               shape.x2 = maxX;
            }
         }
      }

      if(maxY > minY && topAxis != null) {
         for(int i = 0; i < topAxis.getGridLineCount(); i++) {
            GridLine line = topAxis.getGridLine(i);
            Line2D.Double shape = (Line2D.Double) line.getShape();
            double minY0 = Math.min(shape.y1, shape.y2);
            double maxY0 = Math.max(shape.y1, shape.y2);

            shape.y1 = minY0;
            shape.y2 = maxY0;

            if(minY0 < minY) {
               shape.y1 = minY;
            }

            if(maxY0 > maxY) {
               shape.y2 = maxY;
            }
         }
      }
   }

   /**
    * Check if there is any axis label visible.
    */
   private boolean isLabelVisible(DefaultAxis[] axes) {
      for(DefaultAxis axis : axes) {
         if(axis.isLabelVisible()) {
            return true;
         }
      }

      return false;
   }

   /**
    * Create the grid line for border.
    */
   private GridLine createBorderLine(double x1, double y1, double x2, double y2) {
      Line2D line = new Line2D.Double(x1, y1, x2, y2);
      GridLine line2 = new GridLine(line, null, GDefaults.GRIDLINE_TOP_Z_INDEX);

      line2.setColor(gridColor);
      line2.setStyle(GraphConstants.THIN_LINE);
      return line2;
   }

   /**
    * This is not supported on FacetCoord.
    * @hidden
    */
   @Override
   public void setAxisSize(int axis, double val) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Get the axis width or height.
    * @hidden
    */
   @Override
   public double getAxisSize(int axis) {
      double h = (outer == null) ? 0 : outer.getAxisSize(axis);

      if(axis == TOP_AXIS) {
         int lidx = inners.length - 1;
         double ih = 0;

         for(int i = 0; lidx >= 0 && i < inners[lidx].length; i++) {
            ih = Math.max(ih, inners[lidx][i].getAxisSize(axis));
         }

         h += ih;
      }
      else if(axis == BOTTOM_AXIS) {
         double ih = 0;

         for(int i = 0; inners.length > 0 && i < inners[0].length; i++) {
            ih = Math.max(ih, inners[0][i].getAxisSize(axis));
         }

         h += ih;
      }
      else if(inners.length > 0) {
         for(int i = 0; i < inners[0].length; i++) {
            h += inners[0][i].getAxisSize(axis);
         }
      }

      return h;
   }

   /**
    * Get x unit count.
    * @hidden
    */
   public int getXUnitCount() {
      if(xcount < 0) {
        int count = 0;

        if(inners != null && inners.length > 0) {
            for(int i = 0; i < inners[0].length; i++) {
               count = count + inners[0][i].getXUnitCount();
           }
        }

        xcount = count;
     }

      return xcount;
   }

   /**
    * Get y unit count.
    * @hidden
    */
   public int getYUnitCount() {
      if(ycount < 0) {
         int count = 0;

         if(inners != null && inners.length > 0) {
            for(int i = 0; i < inners.length; i++) {
               if(inners[i].length > 0) {
                  count = count + inners[i][0].getYUnitCount();
               }
            }
         }

         ycount = count;
      }

      return ycount;
   }

   /**
    * Get the number of dimensions in this coordinate.
    */
   @Override
   @TernMethod
   public int getDimCount() {
      return (outer == null ? 0 : outer.getDimCount()) + minner.getDimCount();
   }

   /**
    * Get the scales used in the coordinate.
    */
   @Override
   @TernMethod
   public Scale[] getScales() {
      Vector vector = new Vector();
      Scale[] outerScales = outer == null ? new Scale[0] : outer.getScales();
      Scale[] innerScales = minner.getScales();

      vector.addAll(Arrays.asList(outerScales));
      vector.addAll(Arrays.asList(innerScales));
      return (Scale[]) vector.toArray(new Scale[vector.size()]);
   }

   /**
    * Check if inner graphs should be arranged vertically.
    */
   @TernMethod
   public boolean isVertical() {
      return vertical;
   }

   /**
    * Set whether inner graphs should be arranged vertically.
    */
   @TernMethod
   public void setVertical(boolean vertical) {
      if(this.vertical != vertical) {
         this.vertical = vertical;

         if(minner != null) {
            Coordinate[] coords = minner.getCoordinates();
            minner = vertical ? TileCoord.vertical(coords) : TileCoord.horizontal(coords);
         }
      }
   }

   /**
    * Check if the facet grid is drawn.
    */
   @TernMethod
   public boolean isFacetGrid() {
      return facetGrid;
   }

   /**
    * Set whether to draw facet grid lines.
    * @param grid true to draw the facet axis lines (if enabled) and
    * the outer border lines.
    */
   @TernMethod
   public void setFacetGrid(boolean grid) {
      this.facetGrid = grid;
   }

   /**
    * Get the facet grid line color.
    */
   @TernMethod
   public Color getGridColor() {
      return gridColor;
   }

   /**
    * Set the facet grid line color.
    */
   @TernMethod
   public void setGridColor(Color gridColor) {
      this.gridColor = gridColor;
   }

   /**
    * Set the associated VGraph.
    */
   @Override
   public void setVGraph(VGraph vgraph) {
      super.setVGraph(vgraph);

      if(outer != null) {
         outer.setVGraph(vgraph);
      }
   }

   /**
    * Make a copy of this object.
    */
   @Override
   public Object clone() {
      return clone(true);
   }

   /**
    * Make a copy of this object.
    * @param srange true to share scale range, false otherwise.
    */
   @Override
   public Object clone(boolean srange) {
      try {
         FacetCoord coord = (FacetCoord) super.clone();

         coord.outer = (outer == null) ? null : (Coordinate) outer.clone(srange);
         coord.minner = (TileCoord) minner.clone();

         return coord;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone coordinates", ex);
         return null;
      }
   }

   /**
    * Get unit size.
    * @param min true if get min size, false to get preferred size.
    * @param width true if get width, false to get height.
    */
   private double getUnitSize(boolean min, boolean width) {
      double unitSize = 0;

      for(int i = 0; i < inners.length; i++) {
         for(int j = 0; j < inners[i].length; j++) {
            double size;

            if(min) {
               size = width ? inners[i][j].getUnitMinWidth() :
                  inners[i][j].getUnitMinHeight();
            }
            else {
               size = width ? inners[i][j].getUnitPreferredWidth() :
                  inners[i][j].getUnitPreferredHeight();
            }

            if(unitSize < size) {
               unitSize = size;
            }
         }
      }

      return unitSize;
   }

   /**
    * Get unit minimum width.
    * @hidden
    */
   @Override
   public double getUnitMinWidth() {
      return getUnitSize(true, true);
   }

   /**
    * Get unit minimum height.
    * @hidden
    */
   @Override
   public double getUnitMinHeight() {
      return getUnitSize(true, false);
   }

   /**
    * Get unit preferred width.
    * @hidden
    */
   @Override
   public double getUnitPreferredWidth() {
      return getUnitSize(false, true);
   }

   /**
    * Get unit preferred height.
    * @hidden
    */
   @Override
   public double getUnitPreferredHeight() {
      return getUnitSize(false, false);
   }

   /**
    * Get all axes at specified position, e.g. TOP_AXIS.
    * @hidden
    */
   @Override
   public DefaultAxis[] getAxesAt(int axis) {
      Vector vec = new Vector();
      vec.addAll(Arrays.asList(outer == null ? new DefaultAxis[0] : outer.getAxesAt(axis)));

      switch(axis) {
      case TOP_AXIS:
         if(inners.length > 0) {
            int last = inners.length - 1;

            for(int i = 0; i < inners[last].length; i++) {
               DefaultAxis[] tarr = inners[last][i].getAxesAt(axis);
               vec.addAll(Arrays.asList(tarr));
            }
         }

         break;
      case BOTTOM_AXIS:
         for(int i = 0; inners.length > 0 && i < inners[0].length; i++) {
            DefaultAxis[] tarr = inners[0][i].getAxesAt(axis);
            vec.addAll(Arrays.asList(tarr));
         }

         break;
      case LEFT_AXIS:
         for(int i = 0; i < inners.length; i++) {
            if(inners[i].length > 0) {
               DefaultAxis[] tarr  = inners[i][0].getAxesAt(axis);
               vec.addAll(Arrays.asList(tarr));
            }
         }

         break;
      case RIGHT_AXIS:
         if(inners.length > 0 && inners[0].length > 0) {
            int last = inners[0].length - 1;

            for(int i = 0; i < inners.length; i++) {
               DefaultAxis[] tarr  = inners[i][last].getAxesAt(axis);
               vec.addAll(Arrays.asList(tarr));
            }
         }

         break;
      }

      // copy outer and inner
      return (DefaultAxis[]) vec.toArray(new DefaultAxis[vec.size()]);
   }

   @Override
   public boolean anyAxisAt(int axis, Predicate<Axis> func) {
      if(outer != null && outer.anyAxisAt(axis, func)) {
         return true;
      }

      switch(axis) {
      case TOP_AXIS:
         if(inners.length > 0) {
            int last = inners.length - 1;

            for(int i = 0; i < inners[last].length; i++) {
               if(inners[last][i].anyAxisAt(axis, func)) {
                  return true;
               }
            }
         }

         break;
      case BOTTOM_AXIS:
         for(int i = 0; inners.length > 0 && i < inners[0].length; i++) {
            if(inners[0][i].anyAxisAt(axis, func)) {
               return true;
            }
         }

         break;
      case LEFT_AXIS:
         for(int i = 0; i < inners.length; i++) {
            if(inners[i].length > 0) {
               if(inners[i][0].anyAxisAt(axis, func)) {
                  return true;
               }
            }
         }

         break;
      case RIGHT_AXIS:
         if(inners.length > 0 && inners[0].length > 0) {
            int last = inners[0].length - 1;

            for(int i = 0; i < inners.length; i++) {
               if(inners[i][last].anyAxisAt(axis, func)) {
                  return true;
               }
            }
         }

         break;
      }

      return false;
   }

   /**
    * Create the axis size map.
    * @hidden
    */
   public void createAxisMap(boolean vertical, Map map,
                             AxisSizeStrategy strategy)
   {
      if(outer != null) {
         Object key = ((RectCoord) outer).createKey(vertical);
         AxisSizeList list = (AxisSizeList) map.get(key);

         if(list == null) {
            list = new AxisSizeList(strategy);
         }

         list.add(outer);

         if(list.getCount() > 0) {
            map.put(key, list);
         }
      }

      VGraph vgraph = getVGraph();

      for(int i = 0; i < inners.length; i++) {
         for(int j = 0; j < inners[i].length; j++) {
            if(vgraph != null && vgraph.isCancelled()) {
               return;
            }

            inners[i][j].createAxisMap(vertical, map, strategy);
         }
      }
   }

   /**
    * Get the x positions at inner coord boundaries.
    * @param tbox top coord bounds.
    * @param pbox parent coord bounds.
    * @param left true to include left side boundary.
    */
   Line2D[] getXBoundaries(Rectangle2D tbox, Rectangle2D pbox, boolean left) {
      Line2D[] arr = {};
      int topIdx = inners.length - 1;

      if(inners.length == 0) {
         return arr;
      }

      for(int i = 0; i < inners[topIdx].length; i++) {
         Line2D[] ps = inners[topIdx][i].getXBoundaries(tbox, getCoordBounds(), left && i == 0);
         arr = (Line2D[]) GTool.concatArray(arr, ps);
      }

      if(arr.length > 0) {
         double y2 = pbox.getY() + pbox.getHeight() - 1;

         if(left) {
            arr[0] = new Line2D.Double(arr[0].getX1(), arr[0].getY1(), arr[0].getX1(), y2);
         }

         int n = arr.length - 1;
         arr[n] = new Line2D.Double(arr[n].getX1(), arr[n].getY1(), arr[n].getX1(), y2);
      }

      return arr;
   }

   /**
    * Get the y positions at inner coord boundaries.
    * @param tbox top coord bounds.
    * @pbox parent coord bounds.
    * @param bot true to include bottom side boundary.
    */
   Line2D[] getYBoundaries(Rectangle2D tbox, Rectangle2D pbox, boolean bot) {
      Line2D[] arr = {};

      for(int i = 0; i < inners.length; i++) {
         if(inners[i].length == 0) {
            continue;
         }

         Line2D[] ps = inners[i][0].getYBoundaries(tbox, getCoordBounds(), bot && i == 0);
         arr = (Line2D[]) GTool.concatArray(arr, ps);
      }

      // change the first and last line to the pbox size
      if(arr.length > 0) {
         double x1 = pbox.getX();
         int n = arr.length - 1;

         if(bot) {
            arr[0] = new Line2D.Double(x1, arr[0].getY1(), arr[0].getX2(), arr[0].getY1());
         }

         arr[n] = new Line2D.Double(x1, arr[n].getY1(), arr[0].getX2(), arr[n].getY1());
      }

      return arr;
   }

   /**
    * Calculate the element bounds for each inner coord.
    */
   void calcElementBounds() {
      VGraph vgraph = getVGraph();

      if(vgraph != null) {
         EGraph graph = vgraph.getEGraph();

         if(graph != null) {
            // only necessary for keeping element in bounds
            if(graph.stream().noneMatch(GraphElement::isInPlot)) {
               return;
            }
         }
      }

      for(int i = 0; i < inners.length; i++) {
         for(int j = 0; j < inners[i].length; j++) {
            if(vgraph != null && vgraph.isCancelled()) {
               return;
            }

            inners[i][j].calcElementBounds();
         }
      }

      // make sure all coords have the same scaling factor
      Coordinate[][] coords = getExpandedInnerCoords();

      // rows
      for(int i = 0; i < coords.length; i++) {
         double[] edges = new double[] {0, 0, 0, 0};

         for(int j = 0; j < coords[i].length; j++) {
            double[] edge2 = getEdges(coords[i][j]);

            edges[0] = Math.max(edges[0], edge2[0]);
            edges[1] = Math.max(edges[1], edge2[1]);
            edges[2] = Math.max(edges[2], edge2[2]);
            edges[3] = Math.max(edges[3], edge2[3]);
         }

         for(int j = 0; j < coords[i].length; j++) {
            if(vgraph != null && vgraph.isCancelled()) {
               return;
            }

            boolean topbottom = GTool.getRotation(coords[i][j].getCoordTransform()) == 0;
            setEdges(coords[i][j], edges, topbottom);
         }
      }

      // cols
      for(int i = 0; coords.length > 0 && i < coords[0].length; i++) {
         double[] edges = new double[] {0, 0, 0, 0};

         for(int j = 0; j < coords.length; j++) {
            double[] edge2 = getEdges(coords[j][i]);

            edges[0] = Math.max(edges[0], edge2[0]);
            edges[1] = Math.max(edges[1], edge2[1]);
            edges[2] = Math.max(edges[2], edge2[2]);
            edges[3] = Math.max(edges[3], edge2[3]);
         }

         for(int j = 0; j < coords.length; j++) {
            if(vgraph != null && vgraph.isCancelled()) {
               return;
            }

            boolean topbottom = GTool.getRotation(coords[j][i].getCoordTransform()) != 0;
            setEdges(coords[j][i], edges, topbottom);
         }
      }
   }

   /**
    * Get the out-of-bounds edge for element bounds.
    */
   private double[] getEdges(Coordinate coord) {
      Rectangle2D plotbox = coord.getVGraph().getPlotBounds();
      Rectangle2D box = coord.getElementBounds(null, true);

      if(box == null || box.isEmpty()) {
         return new double[] {0, 0, 0, 0};
      }

      double plotx = plotbox.getX();
      double ploty = plotbox.getY();
      double plotw = plotbox.getWidth();
      double ploth = plotbox.getHeight();
      double x = box.getX();
      double y = box.getY();
      double w = box.getWidth();
      double h = box.getHeight();
      double top = y + h - ploty - ploth;
      double right = x + w - plotx - plotw;
      double bottom = ploty - y;
      double left = plotx - x;

      AffineTransform c2 = coord.getScaledCoordTransform();
      double xfactor = GTool.getScaleFactor(c2, 0);
      double yfactor = GTool.getScaleFactor(c2, 90);

      return new double[] {top / yfactor, left / xfactor, bottom / yfactor,
                           right / xfactor};
   }

   /**
    * Set the out-of-bounds edge for element bounds.
    */
   private void setEdges(Coordinate coord, double[] edges, boolean topbottom) {
      Rectangle2D plotbox = coord.getVGraph().getPlotBounds();
      Rectangle2D box = coord.getElementBounds(null, true);
      double plotx = plotbox.getX();
      double ploty = plotbox.getY();
      // this must match how rescale is calculated in RectCoord
      double plotw = plotbox.getWidth() - getDepth();
      double ploth = plotbox.getHeight() - getDepth();

      if(box == null || box.isEmpty()) {
         box = new Rectangle2D.Double(plotx, ploty, plotw, ploth);
      }

      double x = box.getX();
      double y = box.getY();
      double w = box.getWidth();
      double h = box.getHeight();

      AffineTransform c2 = coord.getScaledCoordTransform();
      double xfactor = GTool.getScaleFactor(c2, 0);
      double yfactor = GTool.getScaleFactor(c2, 90);

      if(topbottom) {
         double top = edges[0] * yfactor;
         double bottom = edges[2] * yfactor;

         y = ploty - bottom;
         h = ploty + ploth - y + top;
      }
      else {
         double left = edges[1] * xfactor;
         double right = edges[3] * xfactor;

         x = plotx - left;
         w = plotx + plotw - x + right;
      }

      coord.setElementBounds(new Rectangle2D.Double(x, y, w, h));
   }

   /**
    * Get union of all sub-graph bounds across entire facet.
    */
   Rectangle2D getGlobalSharedBounds(Rectangle2D bounds) {
      if(globalBounds != null) {
         return globalBounds;
      }

      Coordinate[][] inners = getExpandedInnerCoords();

      for(int i = 0; i < inners.length; i++) {
         for(int j = 0; j < inners[i].length; j++) {
            Rectangle2D bounds2 = inners[i][j].getElementBounds(null, false);

            if(bounds == null) {
               bounds = bounds2;
            }
            else if(bounds2 != null) {
               bounds = bounds.createUnion(bounds2);
            }
         }
      }

      return globalBounds = bounds;
   }

   /**
    * Get all axes in this coordinate.
    * @param recursive true to include axes in nested coordinates.
    */
   @Override
   @TernMethod
   public Axis[] getAxes(boolean recursive) {
      Axis[] axes = (outer != null) ? outer.getAxes(recursive) : new Axis[0];
      ArrayList list = new ArrayList();

      list.addAll(Arrays.asList(axes));

      if(recursive) {
         for(TileCoord[] arr : inners) {
            for(TileCoord tile : arr) {
               list.addAll(Arrays.asList(tile.getAxes(recursive)));
            }
         }
      }

      return (Axis[]) list.toArray(new Axis[list.size()]);
   }

   @Override
   public void layoutCompleted() {
      super.layoutCompleted();

      if(outer != null) {
         outer.layoutCompleted();
      }

      if(minner != null) {
         minner.layoutCompleted();
      }

      for(TileCoord[] arr : inners) {
         for(TileCoord tile : arr) {
            tile.layoutCompleted();
         }
      }
   }

   /**
    * Get the depth for 3D effect coord.
    */
   @TernMethod
   public double getDepth() {
      return minner.getDepth();
   }

   /**
    * Add parent coordinate x, y axes column values if this coordinate is nested
    * in a facet.
    * @param field the column name.
    * @param value the cell value.
    * @param isx identify if it is a x axis.
    * @hidden
    */
   @Override
   @TernMethod
   public void addParentValue(String field, Object value, boolean isx) {
      if(outer != null) {
         outer.addParentValue(field, value, isx);
      }

      if(minner != null) {
         minner.addParentValue(field, value, isx);
      }
   }

   /**
    * Check if the coordinate has the same structure as this.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      FacetCoord coord = (FacetCoord) obj;

      return outer.equalsContent(coord.outer) && minner.equalsContent(coord.minner);
   }

   @Override
   public String toString() {
      return super.toString() + "[outer: " + outer + ", inner: " + minner + "]";
   }

   private Coordinate outer; // the outer coordinate
   private TileCoord minner;// the meta data inner coordinate
   // the expanded inner coordinates
   private TileCoord[][] inners = new TileCoord[0][0];
   private transient Coordinate[][] expandedInnerCoords;
   private boolean vertical = true; // vertical or horizontal
   private int xcount = -1; // x unit count
   private int ycount = -1; // y unit count
   private boolean facetGrid = false; // create facet grid
   private Color gridColor = GDefaults.DEFAULT_LINE_COLOR;
   private transient Rectangle2D globalBounds; // cached global elem bounds

   private static final long serialVersionUID = 1L;
   private static final Logger LOG =
      LoggerFactory.getLogger(FacetCoord.class);
}
