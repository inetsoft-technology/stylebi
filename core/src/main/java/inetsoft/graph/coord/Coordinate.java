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
package inetsoft.graph.coord;

import com.inetsoft.build.tern.TernMethod;
import inetsoft.graph.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DataSetIndex;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.internal.*;
import inetsoft.graph.internal.text.TextLayoutManager;
import inetsoft.graph.internal.text.WordCloudLayout;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.visual.*;
import inetsoft.util.MessageException;
import inetsoft.util.log.LogLevel;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.*;
import java.util.*;
import java.util.function.Predicate;

/**
 * A coordinate defines the dimensional space for rendering graph elements.
 * It converts a logical graph definition to physical drawing elements.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class Coordinate extends AbstractCoord implements Cloneable, ILayout {
   /**
    * Get the logic space width.
    * @hidden
    */
   public static final double GWIDTH = 1000;
   /**
    * Get the logic space height.
    * @hidden
    */
   public static final double GHEIGHT = 1000;

   /**
    * Map a tuple (from logic coordinate space) to the chart coordinate space.
    * @param tuple the tuple in logic space (scaled values).
    * @return the position of specified tuple.
    */
   public abstract Point2D getPosition(double[] tuple);

   /**
    * Get the interval size in this coordinate space.
    * @param interval interval value.
    * @return interval size in this coordinate space.
    */
   public abstract double getIntervalSize(double interval);

   /**
    * Create axis and other guides for the visual graph.
    * @param vgraph visual graph to create axis.
    */
   public abstract void createAxis(VGraph vgraph);

   /**
    * Create graph geometry (nested chart) for this coordinate.
    * @param rset the specified root data set.
    * @param ridx the specified index for root data set.
    * @param dset dataset of this graph. This may be a subset if this coordinate
    * is a nested coordinate.
    * @param graph the top-level graph definition.
    * @param ggraph the parent ggraph.
    * @param facet the inner coordinate.
    * @param hint the specified position hint.
    * @hidden
    */
   public abstract void createSubGGraph(DataSet rset, DataSetIndex ridx,
      DataSet dset, EGraph graph, GGraph ggraph, FacetCoord facet, Map xmap,
      Map ymap, int hint);

   /**
    * Get the transformation on the coordinate positions.
    * @return the transformation on the coordinate positions.
    */
   public AffineTransform getCoordTransform() {
      return ctrans;
   }

   /**
    * Set the transformation on the coordinate positions.
    */
   public void setCoordTransform(AffineTransform ctrans) {
      this.ctrans = ctrans == null ? new GTransform() : ctrans;
   }

   /**
    * Transform the coordinate so the plot is flipped vertically.
    * @param vertical true to reflect on vertical axis.
    */
   @TernMethod
   public void reflect(boolean vertical) {
      AffineTransform trans = new AffineTransform();

      if(vertical) {
         AffineTransform flip = new AffineTransform(1, 0, 0, -1, 0, 0);

         trans.translate(0, 1000);
         trans.concatenate(flip);
      }
      else {
         AffineTransform flip = new AffineTransform(-1, 0, 0, 1, 0, 0);

         trans.translate(1000, 0);
         trans.concatenate(flip);
      }

      ctrans.concatenate(trans);
   }

   /**
    * Rotate the plot counter-clock wise.
    */
   @TernMethod
   public void rotate(double degree) {
      AffineTransform trans = new AffineTransform();
      Point2D corner = new Point2D.Double(1000, 1000);

      trans.rotate(Math.PI * degree / 180);
      corner = trans.transform(corner, null);

      double tx = (corner.getX() < 0) ? -corner.getX() : 0;
      double ty = (corner.getY() < 0) ? -corner.getY() : 0;

      ctrans.translate(tx, ty);
      ctrans.concatenate(trans);
   }

   /**
    * Transpose the coordinate (flip along northest-southwest diagonal line).
    */
   @TernMethod
   public void transpose() {
      reflect(true);
      rotate(270);
   }

   /**
    * Transform the visual objects in the container to this coordinate space.
    */
   public void transform(VContainer vc) {
      for(int i = 0; i < vc.getVisualCount(); i++) {
         Visualizable v = vc.getVisual(i);

         if(!(v instanceof VisualObject)) {
            continue;
         }

         VisualObject vo = (VisualObject) v;
         vo.transform(this);
      }
   }

   /**
    * Transform a shape or point to this coordinate space.
    * @param geom a shape or a point to transform.
    * @return the transformed shape or point.
    */
   public Object transformShape(Object geom) {
      return geom;
   }

   /**
    * Get the specified dimension value for this coordinate.
    * @param tuple the tuple of the graph element.
    * @param idx the dimension index to retrieve value.
    */
   @TernMethod
   public double getValue(double[] tuple, int idx) {
      int n = tuple.length - (getDimCount() - idx);

      if(n < 0) {
         throw new MessageException("There is not enough dimensions for the coordinate: " + idx +
                                    " of " + tuple.length + " in " + getDimCount() + " dimensions",
                                    LogLevel.WARN, false);
      }

      return tuple[n];
   }

   /**
    * Set the plot specification.
    */
   @TernMethod
   public void setPlotSpec(PlotSpec spec) {
      this.plotSpec = spec;
   }

   /**
    * Get the plot specification.
    */
   @TernMethod
   public PlotSpec getPlotSpec() {
      return plotSpec;
   }

   /**
    * Get the maximum width of an item in this coordinate without overlapping.
    */
   @TernMethod
   public double getMaxWidth() {
      return 1000;
   }

   /**
    * Get the maximum height of an item in this coordinate without overlapping.
    */
   @TernMethod
   public double getMaxHeight() {
      return 1000;
   }

   /**
    * Get axis minimum width or height.
    * @hidden
    */
   @Override
   public double getAxisMinSize(int axis) {
      return 0;
   }

   /**
    * Get axis preferred width or height.
    * @hidden
    */
   @Override
   public double getAxisPreferredSize(int axis) {
      return 0;
   }

   /**
    * Create the geometry objects in graph.
    * @param rset the specified root data set.
    * @param ridx the specified index for root data set.
    * @param dset the specified sub data set.
    * @param graph the graph definition.
    * @param ggraph if passed in, new geometry objects are added to this graph.
    * Otherwise a new ggraph is created and returned.
    * @param hint the specified hint.
    * @hidden
    */
   public GGraph createGGraph(DataSet rset, DataSetIndex ridx, DataSet dset,
      EGraph graph, GGraph ggraph, Map xmap, Map ymap, int hint)
   {
      if(ggraph == null) {
         ggraph = graph.createGGraph(this, dset);
      }

      // axis visibility controlled by the axis style
      if(hint != -1) {
         setAxisLabelVisible(TOP_AXIS, (hint & TOP_MOST) != 0);
         setAxisLabelVisible(RIGHT_AXIS, (hint & RIGHT_MOST) != 0);
         setAxisLabelVisible(LEFT_AXIS, (hint & LEFT_MOST) != 0);
         setAxisLabelVisible(BOTTOM_AXIS, (hint & BOTTOM_MOST) != 0);
      }

      return ggraph;
   }

   /**
    * Initialize this coordinate for the specified chart data set.
    */
   public void init(DataSet dset) {
      Scale[] scales = getScales();

      for(int i = 0; i < scales.length; i++) {
         scales[i].init(dset);
      }

      setDataSet(dset);
   }

   /**
    * Set the screen transformation to fit the graph to the coord bounds.
    */
   public abstract void fit(double x, double y, double w, double h);

   /**
    * Get the graph elements bounding box in the graphic space.
    * @param vgraph the associated vgraph. The graph should be transformed
    * according to the fit logic (before the re-scale is applied).
    * @param others true to include the bounds of graph on the same row and
    * column in a facet.
    */
   Rectangle2D getElementBounds(VGraph vgraph, boolean others) {
      if(others && elemBounds != null) {
         return elemBounds;
      }
      else if(!others && elemBounds0 != null) {
         return elemBounds0;
      }

      if(vgraph == null) {
         vgraph = getScaledVGraph();
      }

      if(vgraph == null || vgraph.isCancelled()) {
         return null;
      }

      layoutText(vgraph, false);

      Rectangle2D bounds = getElementBounds(vgraph);

      if(!others) {
         elemBounds0 = bounds;
         return bounds != null ? bounds : new Rectangle2D.Double(0, 0, 0, 0);
      }

      Coordinate coord0 = GTool.getTopVGraph(this).getCoordinate();
      double[] unscaledSizes = getUnscaledSizes(vgraph);

      // find the top/bottom bounds of all elements on the same row in a facet
      if(coord0 instanceof FacetCoord) {
         // tree/network scale is shared across entire facet. may want to make an interface
         // to capture this characteristic if there are more chart sharing the same trait.
         if(GTool.isRelation(coord0)) {
            bounds = ((FacetCoord) coord0).getGlobalSharedBounds(bounds);
         }
         else if(bounds != null) {
            bounds = getSharedBounds(vgraph, bounds, (FacetCoord) coord0, unscaledSizes);
         }
      }

      if(bounds != null && !vgraph.isCancelled()) {
         bounds = adjustElementBounds(bounds, vgraph, unscaledSizes);
      }

      elemBounds = bounds != null ? bounds : new Rectangle2D.Double(0,0,0,0);
      return bounds;
   }

   /**
    * Calculate the bounds across facet for calculation of shared scales. For example,
    * RectCoord would scale uniformly on x across same column and on y across same row.
    */
   private Rectangle2D getSharedBounds(VGraph vgraph, Rectangle2D bounds, FacetCoord coord0,
                                       double[] unscaledSizes)
   {
      Coordinate[][] inners = coord0.getExpandedInnerCoords();
      int row = -1, col = -1;

      outer1:
      for(int i = 0; i < inners.length; i++) {
         for(int j = 0; j < inners[i].length; j++) {
            if(inners[i][j] == this) {
               row = i;
               col = j;
               break outer1;
            }
         }
      }

      if(row >= 0) {
         // merge top/bottom bounds (left/right if rotated)
         for(int i = 0; i < inners[row].length && !vgraph.isCancelled(); i++) {
            Coordinate icoord = inners[row][i];
            bounds = mergeElementBounds(bounds, icoord, vgraph, unscaledSizes, true);
         }

         // merge left/right bounds (top/bottom if rotated)
         for(int i = 0; i < inners.length && !vgraph.isCancelled(); i++) {
            Coordinate icoord = inners[i][col];
            bounds = mergeElementBounds(bounds, icoord, vgraph, unscaledSizes, false);
         }
      }

      return bounds;
   }

   /**
    * Get the element bounds in the vgraph, without adjusting for rescale.
    */
   Rectangle2D getElementBounds(VGraph vgraph) {
      Rectangle2D bounds = null;

      // find the bounds of all elements in this graph
      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         Visualizable visual = vgraph.getVisual(i);
         Rectangle2D box = null;

         // vo object labels
         if(visual instanceof VOText) {
            box = getTextBounds((VOText) visual, vgraph);

            // adjust for rotation offset
            // this logic may be better place in the getBounds() of VLabel
            // however, that makes the getBounds and setBounds asymmetric
            Point2D offset = ((VOText) visual).getOffset();

            if(offset != null && box != null) {
               box = new Rectangle2D.Double(box.getX() + offset.getX(),
                                            box.getY() + offset.getY(),
                                            box.getWidth(), box.getHeight());
            }
         }
         else if(visual instanceof LabelFormVO) {
            box = getTextBounds((LabelFormVO) visual, vgraph);
         }
         else if(visual instanceof VisualObject) {
            box = getVOBounds((VisualObject) visual);
         }

         // ignore empty and invalid shape
         if(!isValidBounds(box)) {
            continue;
         }

         if(bounds == null) {
            bounds = box;
         }
         else {
            bounds = bounds.createUnion(box);
         }
      }

      return bounds;
   }

   /**
    * Check if the bounds is valid.
    */
   private boolean isValidBounds(Rectangle2D box) {
      return box != null && !Double.valueOf(box.getX()).isNaN() &&
         !Double.valueOf(box.getY()).isNaN() &&
         !Double.valueOf(box.getWidth()).isNaN() &&
         !Double.valueOf(box.getHeight()).isNaN();
   }

   /**
    * Set the cached element bounds.
    */
   void setElementBounds(Rectangle2D box) {
      elemBounds = box;
   }

   /**
    * Merge the left/right bounds from icoord. If the icoord is rotated,
    * merge the top/bottom instead.
    */
   private Rectangle2D mergeElementBounds(Rectangle2D bounds, Coordinate icoord,
                                          VGraph vgraph, double[] unscaledSizes,
                                          boolean topbottom)
   {
      if(icoord == this || icoord == null) {
         return bounds;
      }

      Rectangle2D b2 = icoord.getElementBounds(null, false);

      if(b2 == null || b2.isEmpty()) {
         return bounds;
      }

      VGraph vgraph2 = icoord.getVGraph();
      Rectangle2D coordb = vgraph.getPlotBounds();
      Rectangle2D coordb2 = vgraph2.getPlotBounds();

      if(coordb.getWidth() == 0 || coordb.getHeight() == 0 ||
         coordb2.getWidth() == 0 || coordb2.getHeight() == 0)
      {
         return bounds;
      }

      // create the smallest possible bounds for merging if the graph is empty
      if(bounds == null || bounds.isEmpty()) {
         bounds = new Rectangle2D.Double(coordb.getX() + coordb.getWidth() / 2,
                                         coordb.getY() + coordb.getHeight() / 2,
                                         1, 1);
      }

      double[] unscaledSizes0 = icoord.getUnscaledSizes(vgraph2);
      double unscaledTop = unscaledSizes0[0];
      double unscaledLeft = unscaledSizes0[1];
      double unscaledBottom = unscaledSizes0[2];
      double unscaledRight = unscaledSizes0[3];

      AffineTransform trans = new AffineTransform();
      trans.translate(coordb.getX(), coordb.getY());
      trans.scale(coordb.getWidth() / coordb2.getWidth(),
                  coordb.getHeight() / coordb2.getHeight());
      trans.translate(-coordb2.getX(), -coordb2.getY());

      // unscaled sizes shouldn't be transformed
      b2 = new Rectangle2D.Double(b2.getX() + unscaledLeft,
                                  b2.getY() + unscaledBottom,
                                  b2.getWidth() - unscaledLeft - unscaledRight,
                                  b2.getHeight() - unscaledTop - unscaledBottom);

      // make sure width/height is not 0, otherwise transform would change it
      // to (0, 0, 0, 0), which would be incorrect
      b2 = new Rectangle2D.Double(b2.getX(), b2.getY(),
                                  Math.max(b2.getWidth(), 0.1),
                                  Math.max(b2.getHeight(), 0.1));

      // transform to current coord space
      //b2 = trans.createTransformedShape(b2).getBounds2D();
      // optimization, createTransformedShape() is much more expensive than
      // transforming points
      Point2D minPt = new Point2D.Double(b2.getMinX(), b2.getMinY());
      Point2D maxPt = new Point2D.Double(b2.getMaxX(), b2.getMaxY());
      minPt = trans.transform(minPt, null);
      maxPt = trans.transform(maxPt, null);
      b2 = new Rectangle2D.Double(Math.min(minPt.getX(), maxPt.getX()),
                                  Math.min(minPt.getY(), maxPt.getY()),
                                  Math.abs(minPt.getX() - maxPt.getX()),
                                  Math.abs(minPt.getY() - maxPt.getY()));

      // restore unscaled size
      b2 = new Rectangle2D.Double(b2.getX() - unscaledLeft,
                                  b2.getY() - unscaledBottom,
                                  b2.getWidth() + unscaledLeft + unscaledRight,
                                  b2.getHeight() + unscaledTop + unscaledBottom);

      if(b2.getWidth() == 0 || b2.getHeight() == 0) {
         return bounds;
      }

      if(!GTool.isHorizontal(getCoordTransform())) {
         topbottom = !topbottom;
      }

      // merging the unscaledSizes by using max is not completely accurate since
      // a large unscaled size for a small element bounds may not impact the overall
      // layout if a smaller scalled size but larger element bounds overwhelms.
      // to make it 100% accurate, we will need to carry the original element bounds
      // information with each unscaleSizes corresponding to the coord, and then
      // calculate the overall scaled size, then reverse engineer the unscaled sizes
      // from the calculation. since it only matters in edge cases and is not
      // functional, we will leave it until future consideration.

      if(!topbottom) {
         b2 = new Rectangle2D.Double(b2.getX(), bounds.getY(), b2.getWidth(), 1);
         unscaledSizes[1] = Math.max(unscaledSizes[1], unscaledLeft);
         unscaledSizes[3] = Math.max(unscaledSizes[3], unscaledRight);
      }
      else {
         b2 = new Rectangle2D.Double(bounds.getX(), b2.getY(), 1, b2.getHeight());
         unscaledSizes[0] = Math.max(unscaledSizes[0], unscaledTop);
         unscaledSizes[2] = Math.max(unscaledSizes[2], unscaledBottom);
      }

      return bounds.createUnion(b2);
   }

   /**
    * Layout the text labels.
    * @param resolve true to resolve overlapping.
    */
   protected void layoutText(VGraph vgraph, boolean resolve) {
      if(vgraph == null) {
         return;
      }

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         Visualizable visual = vgraph.getVisual(i);

         if(!(visual instanceof ElementVO)) {
            continue;
         }

         if(vgraph.isCancelled()) {
            return;
         }

         if(resolve) {
            ((ElementVO) visual).layoutCompleted(this);
         }

         ((ElementVO) visual).layoutText(vgraph);
      }

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         Visualizable visual = vgraph.getVisual(i);

         if(visual instanceof ElementVO) {
            ((ElementVO) visual).postLayoutText();
         }
      }

      if(resolve) {
         WordCloudLayout wordCloud = new WordCloudLayout(vgraph);
         wordCloud.layout();

         vgraph.getCoordinate().resolveTextOverlapping();
      }
   }

   /**
    * Resolve label overlapping.
    */
   protected void resolveTextOverlapping() {
      TextLayoutManager layout = new TextLayoutManager(getVGraph());
      layout.resolve();
   }

   /**
    * Remove the grid line at the minimum tick if the tick is the same as
    * the minimum of the scale.
    * @param axis the specified axis object.
    */
   static void removeMinGridLine(Axis axis) {
      if(axis.getGridLineCount() > 0) {
         Scale scale = axis.getScale();
         double[] ticks = scale.getTicks();

         if(ticks[0] == scale.getMin()) {
            axis.removeGridLine(0);
         }
      }
   }

   /**
    * Remove the grid line at the maximum tick if the tick is the same as
    * the maximum of the scale.
    * @param axis the specified axis object.
    */
   static void removeMaxGridLine(Axis axis) {
      if(axis.getGridLineCount() > 0) {
         Scale scale = axis.getScale();
         double[] ticks = scale.getTicks();

         if(ticks[ticks.length - 1] == scale.getMax()) {
            axis.removeGridLine(axis.getGridLineCount() - 1);
         }
      }
   }

   /**
    * Get the parent coordinate if this coordinate is nested in a facet.
    */
   @TernMethod
   public Coordinate getParentCoordinate() {
      return pcoord;
   }

   /**
    * Set the parent coordinate if this coordinate is nested in a facet.
    */
   @TernMethod
   public void setParentCoordinate(Coordinate pcoord) {
      this.pcoord = pcoord;
   }

   /**
    * Add parent coordinate x, y axes column values if this coordinate is nested
    * in a facet.
    * @param field the column name.
    * @param value the cell value.
    * @param isx identify if it is a x axis.
    * @hidden
    */
   public void addParentValue(String field, Object value, boolean isx) {
      Object2ObjectOpenHashMap<String,Object> pvalues = isx ? xpvalues : ypvalues;

      if(pvalues == null) {
         pvalues = new Object2ObjectOpenHashMap<>();

         if(isx) {
            xpvalues = pvalues;
         }
         else {
            ypvalues = pvalues;
         }
      }

      pvalues.put(field, value);
   }

   /**
    * Get parent values.
    * @param isx to get x axes' values.
    * @return the values.
    * @hidden
    */
   public Map<String,Object> getParentValues(boolean isx) {
      Map<String,Object> xymap = isx ? xpvalues : ypvalues;
      return xymap == null ? new HashMap<>() : xymap;
   }

   /**
    * Set associated VGraph.
    */
   @Override
   public void setVGraph(VGraph vgraph) {
      this.vgraph = vgraph;
   }

   /**
    * Get associated VGraph.
    */
   @Override
   public VGraph getVGraph() {
      return vgraph;
   }

   /**
    * Get data set.
    */
   @TernMethod
   public DataSet getDataSet() {
      return dataSet;
   }

   /**
    * Set data set.
    */
   @TernMethod
   public void setDataSet(DataSet dset) {
      this.dataSet = dset;
   }

   /**
    * Get all axes at specified position, e.g. TOP_AXIS.
    * @hidden
    */
   @Override
   public DefaultAxis[] getAxesAt(int axis) {
      return new DefaultAxis[0];
   }

   @Override
   public boolean anyAxisAt(int axis, Predicate<Axis> func) {
      return Arrays.stream(getAxesAt(axis)).anyMatch(func);
   }

   /**
    * Get the preferred width of this visualizable.
    * @hidden
    */
   @Override
   public double getPreferredWidth() {
      return 0;
   }

   /**
    * Get the preferred height of this visualizable.
    * @hidden
    */
   @Override
   public double getPreferredHeight() {
      return 0;
   }

   /**
    * Get the minimum width of this visualizable.
    * @hidden
    */
   @Override
   public double getMinWidth() {
      return 0;
   }

   /**
    * Get the minimum height of this visualizable.
    * @hidden
    */
   @Override
   public double getMinHeight() {
      return 0;
   }

   /**
    * Get the axis width or height.
    * @param axis the axis position, e.g. TOP_AXIS.
    * @hidden
    */
   @Override
   public double getAxisSize(int axis) {
      return 0;
   }

   /**
    * Set the width or height of the axis.
    * @param axis the axis position, e.g. TOP_AXIS.
    * @hidden
    */
   @Override
   public void setAxisSize(int axis, double val) {
   }

   /**
    * Get coord transform for the coord bounds.
    */
   protected AffineTransform getScaledCoordTransform() {
      double coordx = 0, coordy = 0, coordw = 0, coordh = 0;

      if(getCoordBounds() != null) {
         coordx = getCoordBounds().getX();
         coordy = getCoordBounds().getY();
         coordw = getCoordBounds().getWidth();
         coordh = getCoordBounds().getHeight();
      }

      return getScaledCoordTransform(coordx, coordy, coordw, coordh);
   }

   /**
    * Get coord transform for the specified graph area. The transformation
    * matrix is scaled to match the output area.
    * @param x the x position of the origin for scaling.
    * @param y the y position of the origin for scaling.
    * @param w the width of the output.
    * @param h the height of the output.
    */
   protected AffineTransform getScaledCoordTransform(double x, double y, double w, double h) {
      GTransform c2 = (GTransform) getCoordTransform().clone();

      if(w == 0 || h == 0) {
         return c2;
      }

      c2.scaleTo(w / 1000.0, h / 1000.0);
      // the transformation should be done with the shapes at 0 origin, add
      // translates around the transformation so it's not done at an offset
      c2.concatenate(AffineTransform.getTranslateInstance(-x, -y));
      c2.preConcatenate(AffineTransform.getTranslateInstance(x, y));

      return c2;
   }

   /**
    * Get the bounds with transformtion.
    */
   static Rectangle2D getBounds(double x, double y, double w, double h,
                                AffineTransform trans)
   {
      Rectangle2D rect = new Rectangle2D.Double(x, y, w, h);
      return trans.createTransformedShape(rect).getBounds2D();
   }

   /**
    * Get the margin increase for out-of-bounds elements.
    */
   static double getElementMargin(double v) {
      return Math.max(v, 0);
   }

   /**
    * Get the size that is unscaled by screen transformation.
    * @return the unscaled sizes in an array, top, left, bottom, right.
    */
   double[] getUnscaledSizes(VGraph vgraph) {
      if(unscaledSizes != null) {
         return unscaledSizes;
      }

      double[] sizes = {0, 0, 0, 0};

      if(vgraph == null) {
         vgraph = getVGraph();

         if(vgraph != null && vgraph.isCancelled()) {
            return sizes;
         }

         vgraph = getScaledVGraph();
      }

      if(vgraph == null) {
         return sizes;
      }

      double[] vs = {Double.NaN, Double.NaN, Double.NaN, Double.NaN};
      int[] poses = {GraphConstants.TOP, GraphConstants.LEFT,
                     GraphConstants.BOTTOM, GraphConstants.RIGHT};
      double[] tsizes = {0, 0, 0, 0};

      // make sure text is at right position
      layoutText(vgraph, false);

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         Visualizable visual = vgraph.getVisual(i);

         if(visual instanceof PlotObject) {
            if(!((PlotObject) visual).isInPlot()) {
               continue;
            }
         }
         else {
            continue;
         }

         Rectangle2D box = visual.getBounds();

         if(box == null || box.getWidth() == 0 || box.getHeight() == 0) {
            continue;
         }

         for(int k = 0; k < poses.length; k++) {
            int pos = poses[k];
            double nv = 0;
            int sign = 1;
            double size = 0;
            double tsize = 0;

            switch(pos) {
            case GraphConstants.TOP:
               nv = box.getY() + box.getHeight();
               break;
            case GraphConstants.LEFT:
               sign = -1;
               nv = box.getX();
               break;
            case GraphConstants.BOTTOM:
               sign = -1;
               nv = box.getY();
               break;
            case GraphConstants.RIGHT:
            default:
               nv = box.getX() + box.getWidth();
               break;
            }

            if(visual instanceof PlotObject) {
               PlotObject vo = (PlotObject) visual;

               if(visual instanceof LabelFormVO || visual instanceof VOText) {
                  tsize = vo.getUnscaledSize(pos, this);
               }
               else {
                  size = vo.getUnscaledSize(pos, this);
               }
            }

            if(Double.isNaN(vs[k]) || nv * sign > vs[k] * sign) {
               if(tsize > 0) {
                  tsizes[k] = tsize;
               }
               else if(size > 0) {
                  sizes[k] = size;
               }

               vs[k] = nv;
            }
         }
      }

      // text is on top of vo, so the unscaled size is in addition to
      // the vo's unscaled size
      for(int i = 0; i < sizes.length; i++) {
         sizes[i] += tsizes[i];
      }

      return unscaledSizes = sizes;
   }

   /**
    * Get a vgraph for calculating element bounds.
    */
   VGraph getScaledVGraph() {
      return null;
   }

   /**
    * Get the text bounds in coord.
    */
   private Rectangle2D getTextBounds(VOText visual, VGraph vgraph) {
      // if the element is not set keep in plot property, ignore the vo
      if(!visual.isInPlot()) {
         return null;
      }

      GraphElement elem = visual.getGraphElement();
      return getTextBounds(visual, vgraph, visual, elem);
   }

   /**
    * Get the text bounds in coord.
    */
   private Rectangle2D getTextBounds(LabelFormVO visual, VGraph vgraph) {
      // if the element is not set keep in plot property, ignore the vo
      if(!visual.isInPlot()) {
         return null;
      }

      return getTextBounds(visual.getVLabel(), vgraph, visual, null);
   }

   /**
    * Get the text bounds in coord.
    */
   private Rectangle2D getTextBounds(VLabel visual, VGraph vgraph, PlotObject vo,
                                     GraphElement elem)
   {
      double rotation = GTool.getRotation(getCoordTransform());
      Rectangle2D box = visual.getBounds();

      // text bounds is post transformation, but at this point the coord
      // transformation has not been applied. Rotate to get the correct
      // width and height.
      if(rotation != 0) {
         int placement = (elem == null) ? 0 : elem.getLabelPlacement();
         int votextPlacement = 0;

         if(visual instanceof VOText) {
            votextPlacement = ((VOText) visual).getPlacement();
         }

         // label (target) form is move-up when it's for a vertical target line.
         // (52260, 52549, 52567, 52600)
         // bar places label at right so the height is the size at top (before transformation).
         if(visual.getCollisionModifier() == VLabel.MOVE_UP ||
            // point with label on right (with nil shape)
            placement == GraphConstants.RIGHT)
         {
            box = getRotatedBounds(box, rotation);
         }
         // if label is placed on top of a horizontal box (boxplot), the unscaled size is
         // the height but the x position (in unrotated space) should be the right side of
         // the box instead of the center of text. (63776)
         else if(visual.getCollisionModifier() == VLabel.MOVE_RIGHT &&
            votextPlacement == GraphConstants.TOP)
         {
            ElementVO elementVO = ((VOText) visual).getElementVO();
            Rectangle2D ebounds = vgraph.getScreenTransform()
               .createTransformedShape(elementVO.getBounds()).getBounds2D();
            box = new Rectangle2D.Double(ebounds.getMaxX() + VisualObject.TEXT_GAP,
                                         ebounds.getY() + ebounds.getHeight() / 2,
                                         box.getHeight(), box.getHeight());
         }
         // others keep label on top so the height is at right (before trans).
         // for the purpose of calculating rescale, we only care about the direction
         // where it may exceed the plot bounds, so the height is what matters.
         else {
            box = new Rectangle2D.Double(box.getX() + box.getWidth() / 2, box.getY(),
                                         box.getHeight(), box.getHeight());
         }
      }

      Rectangle2D pbounds = vgraph.getPlotBounds();

      if(visual instanceof VOText) {
         int placement = ((VOText) visual).getPlacement();

         // text at bottom should be pushed up by text layout, and not
         // adding a rescale to the coord
         if(placement != GraphConstants.BOTTOM && box.getY() < pbounds.getY()) {
            box = new Rectangle2D.Double(box.getX(), pbounds.getY(),
                                         box.getWidth(), box.getHeight());
         }

         // should only add rescale on left if text is placed on the left. (51506)
         if(placement != GraphConstants.LEFT && box.getX() < pbounds.getX()) {
            box = new Rectangle2D.Double(pbounds.getX(), box.getY(),
                                         box.getWidth(), box.getHeight());
         }
      }

      // convert unscaled size (text) to scalable size (like bar)
      AffineTransform c2 = getScaledCoordTransform();
      double xfactor = GTool.getScaleFactor(c2, 0);
      double yfactor = GTool.getScaleFactor(c2, 90);
      Coordinate coord = vgraph.getCoordinate();
      boolean reversed = coord instanceof RectCoord &&
         ((RectCoord) coord).getYScale() instanceof LinearScale &&
         ((LinearScale) ((RectCoord) coord).getYScale()).isReversed();
      int textPos = reversed ? GraphConstants.BOTTOM : GraphConstants.TOP;
      double unscaledH = vo.getUnscaledSize(textPos, this);
      double[] graphUnscaled = getUnscaledSizes(vgraph);
      // if there is unscaled at bottom, the adjustment will need to be countered. (52600, 52632)
      double leftAdj = graphUnscaled[1];
      double bottomAdj = graphUnscaled[2];

      box = new Rectangle2D.Double(
         box.getX(), box.getY(), box.getWidth() * xfactor + leftAdj,
         unscaledH + (box.getHeight() - unscaledH) * yfactor + bottomAdj);

      return box;
   }

   private Rectangle2D getRotatedBounds(Rectangle2D box, double rotation) {
      AffineTransform trans = AffineTransform.getRotateInstance(
         rotation, box.getX(), box.getY());
      // move bounds so the x position is the same (instead of shifted left).
      trans.translate(0, -box.getHeight());
      return trans.createTransformedShape(box).getBounds2D();
   }

   /**
    * Get visual object bounds.
    */
   private Rectangle2D getVOBounds(VisualObject vo) {
      // if not constrained to be in plot, ignore bounds
      if(!vo.isInPlot()) {
         return null;
      }

      Rectangle2D box = vo.getBounds();

      if(box == null) {
         return null;
      }

      if(box.getWidth() == 0 || box.getHeight() == 0) {
         // some vo may return 0 height (e.g. line form)
         if(box.getWidth() != 0) {
            box = new Rectangle2D.Double(box.getX(), box.getY(), box.getWidth(), 0.01);
         }
         else if(box.getHeight() != 0) {
            box = new Rectangle2D.Double(box.getX(), box.getY(), 0.01, box.getHeight());
         }
         else {
            return null;
         }
      }

      // make sure the shape doesn't fall on axis
      box = new Rectangle2D.Double(box.getX() - 1, box.getY(),
                                   box.getWidth() + 1, box.getHeight());

      return box;
   }

   /**
    * If the plot bounds will be scaled, check if we need to increase the
    * left/bottom so the unscaled parts on the left and bottom would not
    * be pushed out of bounds.
    */
   private Rectangle2D adjustElementBounds(Rectangle2D box, VGraph graph,
                                           double[] unscaledSizes)
   {
      Rectangle2D plotbox = graph.getPlotBounds();
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

      AffineTransform c2 = getScaledCoordTransform();
      double xfactor = GTool.getScaleFactor(c2, 0);
      double yfactor = GTool.getScaleFactor(c2, 90);
      double unscaledTop = unscaledSizes[0];
      double unscaledLeft = unscaledSizes[1];
      double unscaledBottom = unscaledSizes[2];
      double unscaledRight = unscaledSizes[3];

      // the top/bottom and left/right are calculated as follows:
      // y1 is the scaled position of the vo at the bottom(e.g. center of point)
      // y2 is the scaled position of the vo at the top
      // h1 is the unscaled size at bottom
      // h2 is the unscaled size at the top
      // The following three equations are solved:
      // (y1 + bottom) * s >= h1
      // (y2 + bottom) * s + h2 <= ploth
      // s = ploth / (ploth + top + bottom)
      //
      // the calculation of left/right is similar (replace y with x, w with h)

      double y1 = unscaledBottom - bottom;
      double y2 = ploth - (unscaledTop - top);
      double h1 = unscaledBottom * yfactor;
      double h2 = unscaledTop * yfactor;

      double x1 = unscaledLeft - left;
      double x2 = plotw - (unscaledRight - right);
      double w1 = unscaledLeft * xfactor;
      double w2 = unscaledRight * xfactor;

      top += unscaledTop * (yfactor - 1);
      right += unscaledRight * (xfactor - 1);
      bottom += unscaledBottom * (yfactor - 1);
      left += unscaledLeft * (xfactor - 1);

      // calculate scale, bottom == 0 or top == 0

      // both top and bottom
      double ys0 = (y2 != y1) ? (ploth - h2 - h1) / (y2 - y1) : 1;
      // bottom only
      double ys1 = (ploth != y1) ? (ploth - h1) / (ploth - y1) : 1;
      // top only
      double ys2 = (y2 != 0) ? (ploth - h2) / y2 : 1;

      ys0 = (ys0 < 0) ? 1 : ys0;
      ys1 = (ys1 < 0) ? 1 : ys1;
      ys2 = (ys2 < 0) ? 1 : ys2;

      double ys = Math.min(ys0, Math.min(ys1, ys2));

      if(ys > 0 && ys < 1) {
         // calculate bottom from scale
         double bottom1 = (h1 - y1 * ys) / ys;
         double bottom2 = (ploth - h2 - y2 * ys) / ys;

         if(ys == ys0) {
            bottom = Math.max(bottom, bottom1);
            bottom = Math.max(bottom, bottom2);
         }
         else if(ys == ys1) {
            bottom = Math.max(bottom, bottom1);
         }
         else {
            bottom = Math.max(bottom, bottom2);
         }

         // calculate top
         double top0 = (y2 * ploth + h2 * ploth + h2 * bottom
                        - ploth * ploth) / (ploth - h2);

         top = Math.max(top, top0);
      }

      // calculate x scale, left, right, same as y
      double xs1 = (plotw != x1) ? (plotw - w1) / (plotw - x1) : 1;
      double xs2 = (x2 != 0) ? (plotw - w2) / x2 : 1;
      double xs0 = (x1 != x2) ? (plotw - w2 - w1) / (x2 - x1) : 1;

      xs0 = (xs0 < 0) ? 1 : xs0;
      xs1 = (xs1 < 0) ? 1 : xs1;
      xs2 = (xs2 < 0) ? 1 : xs2;

      double xs = Math.min(xs0, Math.min(xs1, xs2));

      if(xs > 0 && xs < 1) {
         double left1 = (w1 - x1 * xs) / xs;
         double left2 = (plotw - w2 - x2 * xs) / xs;

         if(xs == xs0) {
            left = Math.max(left, left1);
            left = Math.max(left, left2);
         }
         else if(xs == xs1) {
            left = Math.max(left, left1);
         }
         else {
            left = Math.max(left, left2);
         }

         double right0 = (x2 * plotw + w2 * plotw + w2 * left
                          - plotw * plotw) / (plotw - w2);

         right = Math.max(right, right0);
      }

      y = ploty - bottom;
      x = plotx - left;
      h = ploty + ploth - y + top;
      w = plotx + plotw - x + right;

      return new Rectangle2D.Double(x, y, w, h);
   }

   /**
    * Get the position of a value at a scale line.
    * @param scale the special scale.
    * @param val the logical value.
    * @param length the coord one axis length.
    * @return the position of a value at a scale line.
    */
   static double getPosition(Scale scale, double val, double length) {
      if(scale == null) {
         // to handle Scale.MIN_VALUE and Scale.MAX_VALUE, which should be
         // mapped to 0 and 1 if scale doesn't exist
         return Math.min(val, 1) * length;
      }

      double max = scale.getMax();
      double min = scale.getMin();

      if(min == max) {
         return 0;
      }

      return (val - min) * length / (max - min);
   }

   /**
    * Get the size of an interval on a scale line.
    * @param scale the special scale.
    * @param interval the special interval value.
    * @param length the coord one axis length.
    */
   static double getIntervalSize(Scale scale, double interval, double length) {
      if(scale == null) {
         return 0;
      }

      double min = scale.getMin();
      double max = scale.getMax();
      double range = max - min;

      if(min == 0 && max == Double.MAX_VALUE && interval == 0) {
         return length;
      }

      if(range == 0) {
         return interval == 0 ? length : 0;
      }

      return interval * length / range;
   }

   /**
    * Make a copy of this object.
    * @param srange true to share scale range, false otherwise.
    */
   public abstract Object clone(boolean srange);

   /**
    * Make a copy of this object.
    */
   @Override
   public Object clone() {
      Coordinate coord = (Coordinate) super.clone();
      coord.ctrans = (AffineTransform) ctrans.clone();
      coord.dataSet = dataSet;

      if(xpvalues != null) {
         coord.xpvalues = xpvalues.clone();
      }

      if(ypvalues != null) {
         coord.ypvalues = ypvalues.clone();
      }

      return coord;
   }

   /**
    * Check if the coordinate has the same structure as this.
    */
   public boolean equalsContent(Object obj) {
      if(!(obj instanceof Coordinate)) {
         return false;
      }

      Coordinate coord = (Coordinate) obj;

      if(!getClass().equals(coord.getClass())) {
         return false;
      }

      Scale[] scales = getScales();
      Scale[] scales2 = coord.getScales();

      if(scales.length != scales2.length) {
         return false;
      }

      for(int i = 0; i < scales.length; i++) {
         if(!scales[i].equalsContents(scales2[i])) {
            return false;
         }
      }

      return Objects.equals(ctrans, coord.ctrans) && Objects.equals(plotSpec, coord.plotSpec);
   }

   /**
    * Accessor to set the min of the trend line.
    * Used for projecting the tics/tlocs.
    * @hidden
    */
   public void setTrendLineMin(double min) {
      trendLineMin = min;
   }

   /**
    * Accessor to set the max of the trend line.
    * Used for projecting the tics/tlocs.
    * @hidden
    */
   public void setTrendLineMax(double max) {
      trendLineMax = max;
   }

   /**
    * @hidden
    */
   public double getTrendLineMin() {
      return trendLineMin;
   }

   /**
    * @hidden
    */
   public double getTrendLineMax() {
      return trendLineMax;
   }

   @Override
   public void layoutCompleted() {
      super.layoutCompleted();

      if(xpvalues != null) {
         xpvalues.trim();
      }

      if(ypvalues != null) {
         ypvalues.trim();
      }

      // GTransform keeps history for transformation and is not necessary after layout
      if(ctrans instanceof GTransform && !requiresReplot()) {
         ctrans = new AffineTransform(ctrans);
      }

      for(Axis axis : getAxes(false)) {
         axis.layoutCompleted();
      }
   }

   /**
    * Get the maximum steps that can be moved in each direction (right, up, left, down).
    */
   public int[] getMaxSteps(String[] lines) {
      int xstep = (int) Arrays.stream(lines).mapToLong(String::length).max().orElse(0) * 3;
      return new int[] {xstep, 10, xstep, 10};
   }

   private VGraph vgraph; // vgraph object
   private Coordinate pcoord; // parent coord
   private PlotSpec plotSpec = new PlotSpec();
   // parent value map: x/y axis identifier --> map(column field --> value)
   private Object2ObjectOpenHashMap<String,Object> xpvalues;
   private Object2ObjectOpenHashMap<String,Object> ypvalues;
   private AffineTransform ctrans = new GTransform(); // affine tranform
   private transient DataSet dataSet; // dataset

   private transient Rectangle2D elemBounds; // cached merged elem bounds
   private transient Rectangle2D elemBounds0; // cached unmerged elem bounds
   private transient double[] unscaledSizes; // cached unscaled sizes

   private double trendLineMin = Double.POSITIVE_INFINITY;
   private double trendLineMax = Double.NEGATIVE_INFINITY;

   private static final Logger LOG = LoggerFactory.getLogger(Coordinate.class);
}
