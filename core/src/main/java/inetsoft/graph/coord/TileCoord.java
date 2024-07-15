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

import inetsoft.graph.AxisSpec;
import inetsoft.graph.VGraph;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.internal.*;
import inetsoft.graph.scale.Scale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.*;
import java.util.*;
import java.util.function.Predicate;

/**
 * A tile coordinate is used to manage multiple coordinates. It could be
 * vertically or horizontally aligned.
 *
 * @hidden
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class TileCoord extends AbstractCoord {
   /**
    * Create an TileCoord aligned vertically.
    * @param coords the specified coordinates.
    */
   public static TileCoord vertical(Coordinate... coords) {
      return new Vertical(coords);
   }

   /**
    * Create an TileCoord aligned horizontally.
    * @param coords the specified coordinates.
    */
   public static TileCoord horizontal(Coordinate... coords) {
      return new Horizontal(coords);
   }

   /**
    * Create an instance of TileCoord.
    * @param coords the specified coordinates.
    */
   protected TileCoord(Coordinate[] coords) {
      super();

      // from left to right, or from bottom to top
      this.coords = coords;
   }

   /**
    * Create key for the array coord.
    */
   public abstract Object createKey(boolean vertical);

   /**
    * Get the x positions at inner coord boundaries.
    * @param tbox top coord bounds.
    * @param pbox parent coord bounds.
    * @param left true to include left side boundary.
    */
   public abstract Line2D[] getXBoundaries(Rectangle2D tbox, Rectangle2D pbox,
                                           boolean left);

   /**
    * Get the y positions at inner coord boundaries.
    * @param tbox top coord bounds.
    * @param pbox parent coord bounds.
    * @param bot true to include bottom side boundary.
    */
   public abstract Line2D[] getYBoundaries(Rectangle2D tbox, Rectangle2D pbox,
                                           boolean bot);

   /**
    * Get x count.
    * @hidden
    */
   public abstract int getXUnitCount();

   /**
    * Get y count.
    * @hidden
    */
   public abstract int getYUnitCount();

   /**
    * Set the screen transformation to fit the graph to the specified output
    * size. The position value(x, y) and size value(w, h) includes axis
    * position and size.
    */
   public void fit() {
      for(int i = 0; i < coords.length; i++) {
         Coordinate coord = coords[i];

         if(coord.getVGraph() != null && coord.getVGraph().isCancelled()) {
            break;
         }

         double x = coord.getCoordBounds().getX();
         double y = coord.getCoordBounds().getY();
         double w = coord.getCoordBounds().getWidth();
         double h = coord.getCoordBounds().getHeight();

         coord.fit(x, y, w, h);
      }
   }

   /**
    * Calculate the element bounds for each inner coord.
    */
   void calcElementBounds() {
      VGraph vgraph = getVGraph();

      for(int i = 0; i < coords.length; i++) {
         if(vgraph != null && vgraph.isCancelled()) {
            return;
         }

         if(coords[i] instanceof FacetCoord) {
            ((FacetCoord) coords[i]).calcElementBounds();
         }
         else {
            coords[i].getElementBounds(null, true);
         }
      }
   }

   /**
    * Expand the nested coordinates into a flat two-dimensional array.
    */
   public abstract Coordinate[][] getExpandedInnerCoords();

   /**
    * Layout the inner coords.
    */
   public abstract void layout(double x, double y, double cellw, double cellh);

   /**
    * Set the position hint.
    */
   public void setHint(int hint) {
      if(isRectCoord()) {
         RectCoord rect = (RectCoord) coords[0];
         Scale hscale = rect.getScaleAt(BOTTOM_AXIS);
         Scale vscale = rect.getScaleAt(LEFT_AXIS);
         Scale hscale2 = rect.getScaleAt(TOP_AXIS);
         Scale vscale2 = rect.getScaleAt(RIGHT_AXIS);
         int hstyle = (hscale == null) ? AxisSpec.AXIS_DOUBLE
            : hscale.getAxisSpec().getAxisStyle();
         int vstyle = (vscale == null) ? AxisSpec.AXIS_DOUBLE
            : vscale.getAxisSpec().getAxisStyle();
         boolean h2 = (hstyle & AxisSpec.AXIS_SINGLE2) == AxisSpec.AXIS_SINGLE2;
         boolean v2 = (vstyle & AxisSpec.AXIS_SINGLE2) == AxisSpec.AXIS_SINGLE2;

         setAxisLabelVisible(TOP_AXIS, (h2 || hscale != hscale2) &&
                                       (hint & TOP_MOST) != 0);
         setAxisLabelVisible(RIGHT_AXIS, (v2 || vscale != vscale2) &&
                                       (hint & RIGHT_MOST) != 0);
         setAxisLabelVisible(LEFT_AXIS, !v2 && (hint & LEFT_MOST) != 0);
         setAxisLabelVisible(BOTTOM_AXIS, !h2 && (hint & BOTTOM_MOST) != 0);

         setAxisTickVisible(LEFT_AXIS, (hint & LEFT_MOST) != 0);
         setAxisTickVisible(BOTTOM_AXIS, (hint & BOTTOM_MOST) != 0);
         setAxisTickVisible(TOP_AXIS, (hint & TOP_MOST) != 0);
         setAxisTickVisible(RIGHT_AXIS, (hint & RIGHT_MOST) != 0);
      }
      else if(coords[0] instanceof FacetCoord) {
         TileCoord[][] tiles = ((FacetCoord) coords[0]).getTileCoords();
         setHint(tiles, hint);
      }
   }

   // set the position hint of child coordinates.
   static void setHint(TileCoord[][] tiles, int hint) {
      for(int r = 0; r < tiles.length; r++) {
         int hint1 = hint;

         if(r != 0) {
            hint1 &= ~ICoordinate.BOTTOM_MOST;
         }

         if(r != tiles.length - 1) {
            hint1 &= ~ICoordinate.TOP_MOST;
         }

         for(int c = 0; c < tiles[r].length; c++) {
            int hint2 = hint1;

            if(c != 0) {
               hint2 &= ~ICoordinate.LEFT_MOST;
            }

            if(c != tiles[r].length - 1) {
               hint2 &= ~ICoordinate.RIGHT_MOST;
            }

            tiles[r][c].setHint(hint2);
         }
      }
   }

   /**
    * Set the parent coordinate for all child coordinates if this coordinate
    * is nested in a facet.
    */
   public void setParentCoordinate(Coordinate pcoord) {
      for(Coordinate coord : coords) {
         coord.setParentCoordinate(pcoord);
      }
   }

   /**
    * Add parent coordinate x, y axes column values for all child coordinates
    * if this coordinate is nested
    * in a facet.
    * @param field the column name.
    * @param value the cell value.
    * @param isx identify if it is a x axis.
    * @hidden
    */
   public void addParentValue(String field, Object value, boolean isx) {
      for(Coordinate coord : coords) {
         coord.addParentValue(field, value, isx);
      }
   }

   /**
    * Get the facet coord.
    */
   public FacetCoord getFacetCoord() {
      return coords[0] instanceof FacetCoord ? (FacetCoord) coords[0] : null;
   }

   /**
    * Check if is facet.
    */
   boolean isFacetCoord() {
      return coords.length > 0 && coords[0] instanceof FacetCoord;
   }

   /**
    * Test if the contained coordinate is RectCoord.
    */
   boolean isRectCoord() {
      return coords[0] instanceof RectCoord;
   }

   /**
    * Check if the coord is contained in this tile coord.
    */
   public boolean containsCoordinate(Coordinate coord) {
      for(int i = 0; i < coords.length; i++) {
         if(coords[i] == coord) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get the parent vgraph.
    */
   @Override
   public VGraph getVGraph() {
      return coords[0].getVGraph();
   }

   /**
    * Set the parent vgraph.
    */
   @Override
   public void setVGraph(VGraph graph) {
      for(int i = 0; i < coords.length; i++) {
         coords[i].setVGraph(graph);
      }
   }

   /**
    * Get the coordinates.
    * @return the coordinates.
    */
   public Coordinate[] getCoordinates() {
      return coords;
   }

   /**
    * Get the number of dimensions in this coordinate.
    * @return number of dimensions in this coordinate.
    */
   @Override
   public int getDimCount() {
      return coords[0].getDimCount();
   }

   /**
    * Get the scales used in the coordinate.
    * @retrun the scales used in the coordinate.
    */
   @Override
   public Scale[] getScales() {
      List list = new ArrayList();

      for(int i = 0; i < coords.length; i++) {
         Scale[] scales = coords[i].getScales();

         for(int j = 0; j < scales.length; j++) {
            list.add(scales[j]);
         }
      }

      Scale[] scales = new Scale[list.size()];
      list.toArray(scales);

      return scales;
   }

   /**
    * Get unit min width.
    * @hidden
    */
   @Override
   public double getUnitMinWidth() {
      double min = 0;

      for(int i = 0; i < coords.length; i++) {
         min = Math.max(coords[i].getUnitMinWidth(), min);
      }

      return min;
   }

   /**
    * Get unit min height.
    * @hidden
    */
   @Override
   public double getUnitMinHeight() {
      double min = 0;

      for(int i = 0; i < coords.length; i++) {
         min = Math.max(coords[i].getUnitMinHeight(), min);
      }

      return min;
   }

   /**
    * Get unit preferred width.
    * @hidden
    */
   @Override
   public double getUnitPreferredWidth() {
      double min = 0;

      for(int i = 0; i < coords.length; i++) {
         min = Math.max(coords[i].getUnitPreferredWidth(), min);
      }

      return min;
   }

   /**
    * Get unit preferred height.
    * @hidden
    */
   @Override
   public double getUnitPreferredHeight() {
      double min = 0;

      for(int i = 0; i < coords.length; i++) {
         min = Math.max(coords[i].getUnitPreferredHeight(), min);
      }

      return min;
   }

   /**
    * Create the axis size map.
    * @hidden
    */
   public void createAxisMap(boolean vertical, Map map, AxisSizeStrategy strategy) {
      if(isFacetCoord()) {
         for(int i = 0; i < coords.length; i++) {
            ((FacetCoord) coords[i]).createAxisMap(vertical, map, strategy);
         }
      }
      else if(isRectCoord()) {
         Object key = createKey(vertical);
         AxisSizeList list = (AxisSizeList) map.get(key);

         if(list == null) {
            list = new AxisSizeList(strategy);
         }

         list.add(this);

         if(list.getCount() > 0) {
            map.put(key, list);
         }
      }
   }

   /**
    * Clone the coordinate array.
    * @return the cloned coordinate array.
    */
   @Override
   public Object clone() {
      try {
         TileCoord carr = (TileCoord) super.clone();
         carr.coords = new Coordinate[coords.length];

         for(int i = 0; i < coords.length; i++) {
            carr.coords[i] = (Coordinate) coords[i].clone();
         }

         return carr;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone coordinates", ex);
      }

      return null;
   }

   /**
    * The vertical coordinate array.
    */
   static class Vertical extends TileCoord {
      /**
       * Create an instance of vertical coordinate array.
       * @param coords the specified coordinates.
       */
      public Vertical(Coordinate[] coords) {
         super(coords);
      }

      /**
       * Get all axes at specified position, e.g. TOP_AXIS.
       */
      @Override
      public DefaultAxis[] getAxesAt(int axis) {
         Vector vec = new Vector();

         switch(axis) {
         case TOP_AXIS:
            int last = coords.length - 1;
            return coords[last].getAxesAt(axis);
         case BOTTOM_AXIS:
            return coords[0].getAxesAt(axis);
         case LEFT_AXIS:
         case RIGHT_AXIS:
            for(int i = 0; i < coords.length; i++) {
               vec.addAll(Arrays.asList(coords[i].getAxesAt(axis)));
            }
            break;
         }

         return (DefaultAxis[]) vec.toArray(new DefaultAxis[vec.size()]);
      }

      @Override
      public boolean anyAxisAt(int axis, Predicate<Axis> func) {
         switch(axis) {
         case TOP_AXIS:
            int last = coords.length - 1;
            return coords[last].anyAxisAt(axis, func);
         case BOTTOM_AXIS:
            return coords[0].anyAxisAt(axis, func);
         case LEFT_AXIS:
         case RIGHT_AXIS:
            for(int i = 0; i < coords.length; i++) {
               if(coords[i].anyAxisAt(axis, func)) {
                  return true;
               }
            }
            break;
         }

         return false;
      }

      /**
       * Create key for the array coord.
       */
      @Override
      public Object createKey(boolean vertical) {
         if(!vertical) {
            return createKey(coords[0], vertical);
         }

         List list = new ArrayList();

         for(int i = 0; i < coords.length; i++) {
            List list2 = (List) createKey(coords[i], true);
            list.addAll(list2);
         }

         return list;
      }

      /**
       * Get x count.
       */
      @Override
      public int getXUnitCount() {
         return GTool.getUnitCount(coords[0], BOTTOM_AXIS, false);
      }

      /**
       * Get y count.
       */
      @Override
      public int getYUnitCount() {
         int count = 0;

         for(int i = 0; i < coords.length; i++) {
            count += GTool.getUnitCount(coords[i], LEFT_AXIS, false);
         }

         return count;
      }

      /**
       * Set axis label visible.
       */
      @Override
      public void setAxisLabelVisible(int axis, boolean vis) {
         super.setAxisLabelVisible(axis, vis);

         for(int i = 0; i < coords.length; i++) {
            boolean visible = vis;

            if(axis == TOP_AXIS && vis) {
               visible = i == coords.length - 1;
            }
            else if(axis == BOTTOM_AXIS && vis) {
               visible = i == 0;
            }

            coords[i].setAxisLabelVisible(axis, visible);
         }
      }

      /**
       * Set axis tick visible.
       */
      @Override
      public void setAxisTickVisible(int axis, boolean vis) {
         super.setAxisTickVisible(axis, vis);

         for(int i = 0; i < coords.length; i++) {
            boolean visible = vis;

            if(axis == TOP_AXIS && vis) {
               visible = i == coords.length - 1;
            }
            else if(axis == BOTTOM_AXIS && vis) {
               visible = i == 0;
            }

            coords[i].setAxisTickVisible(axis, visible);
         }
      }

      /**
       * Get the height of top axis.
       */
      @Override
      public double getAxisSize(int axis) {
         switch(axis) {
         case TOP_AXIS:
            int last = coords.length - 1;
            return coords[last].getAxisSize(axis);
         case BOTTOM_AXIS:
            return coords[0].getAxisSize(axis);
         case LEFT_AXIS:
            return coords[0].getAxisSize(axis);
         case RIGHT_AXIS:
            return coords[0].getAxisSize(axis);
         }

         return 0;
      }

      /**
       * Set the height of top axis.
       */
      @Override
      public void setAxisSize(int axis, double val) {
         switch(axis) {
         case TOP_AXIS:
            int last = coords.length - 1;
            coords[last].setAxisSize(axis, val);
            break;
         case BOTTOM_AXIS:
            coords[0].setAxisSize(axis, val);
            break;
         case LEFT_AXIS:
            for(int i = 0; i < coords.length; i++) {
               coords[i].setAxisSize(axis, val);
            }
            break;
         case RIGHT_AXIS:
            for(int i = 0; i < coords.length; i++) {
               coords[i].setAxisSize(axis, val);
            }
            break;
         }
      }

      /**
       * Get the min height of top axis.
       */
      @Override
      public double getAxisMinSize(int axis) {
         switch(axis) {
         case TOP_AXIS:
            int last = coords.length - 1;
            return coords[last].getAxisMinSize(axis);
         case BOTTOM_AXIS:
            return coords[0].getAxisMinSize(axis);
         case LEFT_AXIS: {
            double min = 0;

            for(int i = 0; i < coords.length; i++) {
               min = Math.max(min, coords[i].getAxisMinSize(axis));
            }

            return min;
         }
         case RIGHT_AXIS: {
            double min = 0;

            for(int i = 0; i < coords.length; i++) {
               min = Math.max(min, coords[i].getAxisMinSize(axis));
            }

            return min;
         }
         }

         return 0;
      }

      /**
       * Get top axis preferred height.
       */
      @Override
      public double getAxisPreferredSize(int axis) {
         switch(axis) {
         case TOP_AXIS:
            int last = coords.length - 1;
            return coords[last].getAxisPreferredSize(axis);
         case BOTTOM_AXIS:
            return coords[0].getAxisPreferredSize(axis);
         case LEFT_AXIS: {
            double min = 0;

            for(int i = 0; i < coords.length; i++) {
               min = Math.max(min, coords[i].getAxisPreferredSize(axis));
            }

            return min;
         }
         case RIGHT_AXIS: {
            double min = 0;

            for(int i = 0; i < coords.length; i++) {
               min = Math.max(min, coords[i].getAxisPreferredSize(axis));
            }

            return min;
         }
         }

         return 0;
      }

      /**
       * Layout the inner coords.
       */
      @Override
      public void layout(double x, double y, double cellw, double cellh) {
         double innery = y;

         for(int i = 0; i < coords.length; i++) {
            Coordinate coord = coords[i];
            double innerw = GTool.getUnitCount(coord, BOTTOM_AXIS, false) * cellw;
            double innerh = GTool.getUnitCount(coord, LEFT_AXIS, false) * cellh;

            innerh += coord.getAxisSize(TOP_AXIS);
            innerh += coord.getAxisSize(BOTTOM_AXIS);
            innerw += coord.getAxisSize(LEFT_AXIS);
            innerw += coord.getAxisSize(RIGHT_AXIS);

            // add depth so the x axis connect in facet
            innerw += getDepth();

            coord.setCoordBounds(new Rectangle2D.Double(x, innery,
                                                        innerw, innerh));

            if(coord.getVGraph() != null) {
               coord.getVGraph().setBounds(x, innery, innerw, innerh);
            }

            if(coord instanceof FacetCoord) {
               ((FacetCoord) coord).layout(x, innery, cellw, cellh);
            }

            innery += innerh;
         }
      }

      /**
       * Expand the nested coordinates into a flat two-dimensional array.
       */
      @Override
      public Coordinate[][] getExpandedInnerCoords() {
         if(!isFacetCoord()) {
            Coordinate[][] arr = new Coordinate[coords.length][1];

            for(int i = 0; i < coords.length; i++) {
               arr[i][0] = coords[i];
            }

            return arr;
         }

         Coordinate[][][][] arr = new Coordinate[coords.length][1][][];

         for(int i = 0; i < arr.length; i++) {
            for(int j = 0; j < arr[i].length; j++) {
               arr[i][j] = ((FacetCoord) coords[i]).getExpandedInnerCoords();
            }
         }

         return FacetCoord.flattenCoords(arr);
      }

      /**
       * Get the x positions at inner coord boundaries.
       * @param tbox top coord bounds.
       * @param pbox parent coord bounds.
       * @param left true to include left side boundary.
       */
      @Override
      public Line2D[] getXBoundaries(Rectangle2D tbox, Rectangle2D pbox,
                                     boolean left) {
         double y1 = tbox.getY();
         double y2 = pbox.getY() + pbox.getHeight() - 1;

         if(coords[0] instanceof FacetCoord) {
            return ((FacetCoord) coords[0]).getXBoundaries(tbox, pbox, left);
         }
         else if(!(coords[0] instanceof RectCoord)) {
            Rectangle2D bounds = coords[0].getVGraph().getBounds();
            double x1 = bounds.getX();
            double x2 = bounds.getX() + bounds.getWidth();
            Line2D line1 = new Line2D.Double(x1, y1, x1, y2);
            Line2D line2 = new Line2D.Double(x2, y1, x2, y2);
            return new Line2D[] {line1, line2};
         }

         /*
         // 2.5d coord actually overlaps at the right/left, don't draw grid
         // otherwise it may cut through left most x labels
         if(getDepth() > 0) {
            y1 = getAxisY((RectCoord) coords[0], BOTTOM_AXIS);
         }
         */

         double x1 = getAxisX((RectCoord) coords[0], LEFT_AXIS);
         double x2 = getAxisX((RectCoord) coords[0], RIGHT_AXIS);
         Line2D line1 = new Line2D.Double(x1, y1, x1, y2);
         Line2D line2 = new Line2D.Double(x2, y1, x2, y2);

         return left ? new Line2D[] {line1, line2} : new Line2D[] {line2};
      }

      /**
       * Get the y positions at inner coord boundaries.
       * @param tbox top coord bounds.
       * @param pbox parent coord bounds.
       * @param bot true to include bottom side boundary.
       */
      @Override
      public Line2D[] getYBoundaries(Rectangle2D tbox, Rectangle2D pbox,
                                     boolean bot) {
         Line2D[] arr = new Line2D[0];
         double x2 = tbox.getX() + tbox.getWidth() - getDepth();

         for(int i = 0; i < coords.length; i++) {
            double x1 = (i != coords.length - 1) ?
               coords[i].getCoordBounds().getX() : pbox.getX();
            double botx1 = (i == 0 && bot) ? pbox.getX() : x1;

            if(coords[i] instanceof FacetCoord) {
               FacetCoord coord2 = (FacetCoord) coords[i];
               arr = (Line2D[])
                  GTool.concatArray(arr, coord2.getYBoundaries(tbox, pbox, bot));
               continue;
            }
            else if(!(coords[i] instanceof RectCoord)) {
               Rectangle2D bounds = coords[i].getVGraph().getBounds();
               double y1 = bounds.getY();
               double y2 = bounds.getY() + bounds.getHeight();
               Line2D line1 = new Line2D.Double(botx1, y1, x2, y1);
               Line2D line2 = new Line2D.Double(x1, y2, x2, y2);
               Line2D[] arr0 = (bot && i == 0) ? new Line2D[] {line1, line2}
                         : new Line2D[] {line2};

               arr = (Line2D[]) GTool.concatArray(arr, arr0);
               continue;
            }

            if(bot && i == 0) {
               double y = getAxisY((RectCoord) coords[i],
                                   BOTTOM_AXIS) - getDepth();
               Line2D line1 = new Line2D.Double(botx1, y, x2, y);
               arr = (Line2D[]) GTool.concatArray(arr, new Line2D[] {line1});
            }

            double y = getAxisY((RectCoord) coords[i], TOP_AXIS);
            Line2D line1 = new Line2D.Double(x1, y, x2, y);
            arr = (Line2D[]) GTool.concatArray(arr, new Line2D[] {line1});
         }

         return arr;
      }

      private static final long serialVersionUID = 1L;
   }

   /**
    * The horizontal coordinate array.
    */
   static class Horizontal extends TileCoord {
      /**
       * Create an instance of vertical coordinate array.
       * @param coords the specified coordinates.
       */
      public Horizontal(Coordinate[] coords) {
         super(coords);
      }

      /**
       * Create key for the array coord.
       */
      @Override
      public Object createKey(boolean vertical) {
         if(vertical) {
            return createKey(coords[0], vertical);
         }

         List list = new ArrayList();

         for(int i = 0; i < coords.length; i++) {
            List list2 = (List) createKey(coords[i], false);
            list.addAll(list2);
         }

         return list;
      }

      /**
       * Get all axes at specified position, e.g. TOP_AXIS.
       */
      @Override
      public DefaultAxis[] getAxesAt(int axis) {
         Vector vec = new Vector();

         switch(axis) {
         case TOP_AXIS:
         case BOTTOM_AXIS:
            for(int i = 0; i < coords.length; i++) {
               vec.addAll(Arrays.asList(coords[i].getAxesAt(axis)));
            }
            break;
         case LEFT_AXIS:
            return coords[0].getAxesAt(axis);
         case RIGHT_AXIS:
            return coords[coords.length - 1].getAxesAt(axis);
         }

         return (DefaultAxis[]) vec.toArray(new DefaultAxis[vec.size()]);
      }

      @Override
      public boolean anyAxisAt(int axis, Predicate<Axis> func) {
         switch(axis) {
         case TOP_AXIS:
         case BOTTOM_AXIS:
            for(int i = 0; i < coords.length; i++) {
               if(coords[i].anyAxisAt(axis, func)) {
                  return true;
               }
            }
            break;
         case LEFT_AXIS:
            return coords[0].anyAxisAt(axis, func);
         case RIGHT_AXIS:
            return coords[coords.length - 1].anyAxisAt(axis, func);
         }

         return false;
      }

      /**
       * Get x count.
       */
      @Override
      public int getXUnitCount() {
         int count = 0;

         for(int i = 0; i < coords.length; i++) {
            count += GTool.getUnitCount(coords[i], BOTTOM_AXIS, false);
         }

         return count;
      }

      /**
       * Get y count.
       */
      @Override
      public int getYUnitCount() {
         return GTool.getUnitCount(coords[0], LEFT_AXIS, false);
      }

      /**
       * Set axis label visible.
       */
      @Override
      public void setAxisLabelVisible(int axis, boolean vis) {
         super.setAxisLabelVisible(axis, vis);

         for(int i = 0; i < coords.length; i++) {
            boolean visible = vis;

            if(axis == LEFT_AXIS && vis) {
               visible = i == 0;
            }
            else if(axis == RIGHT_AXIS && vis) {
               visible = i == coords.length - 1;
            }

            coords[i].setAxisLabelVisible(axis, visible);
         }
      }

      /**
       * Set axis tick visible.
       */
      @Override
      public void setAxisTickVisible(int axis, boolean vis) {
         super.setAxisTickVisible(axis, vis);

         for(int i = 0; i < coords.length; i++) {
            boolean visible = vis;

            if(axis == LEFT_AXIS && vis) {
               visible = i == 0;
            }
            else if(axis == RIGHT_AXIS && vis) {
               visible = i == coords.length - 1;
            }

            coords[i].setAxisTickVisible(axis, visible);
         }
      }

      /**
       * Get the height of top axis.
       */
      @Override
      public double getAxisSize(int axis) {
         switch(axis) {
         case TOP_AXIS:
            return coords[0].getAxisSize(axis);
         case BOTTOM_AXIS:
            return coords[0].getAxisSize(axis);
         case LEFT_AXIS:
            return coords[0].getAxisSize(axis);
         case RIGHT_AXIS:
            int last = coords.length - 1;
            return coords[last].getAxisSize(axis);
         }

         return 0;
      }

      /**
       * Set the height of top axis.
       */
      @Override
      public void setAxisSize(int axis, double val) {
         switch(axis) {
         case TOP_AXIS:
            for(int i = 0; i < coords.length; i++) {
               coords[i].setAxisSize(axis, val);
            }
            break;
         case BOTTOM_AXIS:
            for(int i = 0; i < coords.length; i++) {
               coords[i].setAxisSize(axis, val);
            }
            break;
         case LEFT_AXIS:
            coords[0].setAxisSize(axis, val);
            break;
         case RIGHT_AXIS:
            int last = coords.length - 1;
            coords[last].setAxisSize(axis, val);
         }
      }

      /**
       * Get the min height of top axis.
       */
      @Override
      public double getAxisMinSize(int axis) {
         switch(axis) {
         case TOP_AXIS: {
            double min = 0;

            for(int i = 0; i < coords.length; i++) {
               min = Math.max(min, coords[i].getAxisMinSize(axis));
            }

            return min;
         }
         case BOTTOM_AXIS: {
            double min = 0;

            for(int i = 0; i < coords.length; i++) {
               min = Math.max(min, coords[i].getAxisMinSize(axis));
            }

            return min;
         }
         case LEFT_AXIS:
            return coords[0].getAxisMinSize(axis);
         case RIGHT_AXIS:
            int last = coords.length - 1;
            return coords[last].getAxisMinSize(axis);
         }

         return 0;
      }

      /**
       * Get the preferred height of top axis.
       */
      @Override
      public double getAxisPreferredSize(int axis) {
         switch(axis) {
         case TOP_AXIS: {
            double min = 0;

            for(int i = 0; i < coords.length; i++) {
               min = Math.max(min, coords[i].getAxisPreferredSize(axis));
            }

            return min;
         }
         case BOTTOM_AXIS: {
            double min = 0;

            for(int i = 0; i < coords.length; i++) {
               min = Math.max(min, coords[i].getAxisPreferredSize(axis));
            }

            return min;
         }
         case LEFT_AXIS:
            return coords[0].getAxisPreferredSize(axis);
         case RIGHT_AXIS:
            int last = coords.length - 1;
            return coords[last].getAxisPreferredSize(axis);
         }

         return 0;
      }

      /**
       * Layout the inner coords.
       */
      @Override
      public void layout(double x, double y, double cellw, double cellh) {
         double innerx = x;

         for(int i = 0; i < coords.length; i++) {
            Coordinate coord = coords[i];
            double innerw = GTool.getUnitCount(coord, BOTTOM_AXIS, false) * cellw;
            double innerh = GTool.getUnitCount(coord, LEFT_AXIS, false) * cellh;

            innerh += coord.getAxisSize(TOP_AXIS);
            innerh += coord.getAxisSize(BOTTOM_AXIS);
            innerw += coord.getAxisSize(LEFT_AXIS);
            innerw += coord.getAxisSize(RIGHT_AXIS);

            // add depth so the x axis connect in facet
            if(getCoordinates().length <= 1) {
               innerw += getDepth();
            }

            coord.setCoordBounds(new Rectangle2D.Double(innerx, y,
                                                        innerw, innerh));

            if(coord.getVGraph() != null) {
               coord.getVGraph().setBounds(innerx, y, innerw, innerh);
            }

            if(coord instanceof FacetCoord) {
               ((FacetCoord) coord).layout(innerx, y, cellw, cellh);
            }

            innerx = innerx + innerw;
         }
      }

      /**
       * Expand the nested coordinates into a flat two-dimensional array.
       */
      @Override
      public Coordinate[][] getExpandedInnerCoords() {
         if(!isFacetCoord()) {
            Coordinate[][] arr = new Coordinate[1][coords.length];
            System.arraycopy(coords, 0, arr[0], 0, coords.length);
            return arr;
         }

         Coordinate[][][][] arr = new Coordinate[1][coords.length][][];

         for(int i = 0; i < arr.length; i++) {
            for(int j = 0; j < arr[i].length; j++) {
               arr[i][j] = ((FacetCoord) coords[j]).getExpandedInnerCoords();
            }
         }

         return FacetCoord.flattenCoords(arr);
      }

      /**
       * Get the x positions at inner coord boundaries.
       * @param tbox top coord bounds.
       * @param pbox parent coord bounds.
       * @param left true to include left side boundary.
       */
      @Override
      public Line2D[] getXBoundaries(Rectangle2D tbox, Rectangle2D pbox,
                                     boolean left) {
         Line2D[] arr = new Line2D[0];
         double y1 = tbox.getY() + getDepth();

         for(int i = 0; i < coords.length; i++) {
            double y2 = (i == coords.length - 1) ?
               pbox.getY() + pbox.getHeight() - 1 :
               getCoordBounds().getY() + getCoordBounds().getHeight();
            double lefty2 = (left && i == 0) ?
               pbox.getY() + pbox.getHeight() - 1 : y2;

            if(coords[i] instanceof FacetCoord) {
               FacetCoord coord2 = (FacetCoord) coords[i];
               arr = (Line2D[]) GTool.concatArray(
                  arr, coord2.getXBoundaries(tbox, pbox, left));
               continue;
            }
            else if(!(coords[i] instanceof RectCoord)) {
               Rectangle2D bounds = coords[i].getVGraph().getBounds();
               double x1 = bounds.getX();
               double x2 = bounds.getX() + bounds.getWidth();
               Line2D line1 = new Line2D.Double(x1, y1, x1, lefty2);
               Line2D line2 = new Line2D.Double(x2, y1, x2, y2);

               if(left && i == 0) {
                  arr = (Line2D[]) GTool.concatArray(arr, new Line2D[] {line1});
               }

               arr = (Line2D[]) GTool.concatArray(arr, new Line2D[] {line2});
               continue;
            }

            if(left && i == 0) {
               double x = getAxisX((RectCoord) coords[i], LEFT_AXIS);
               Line2D line1 = new Line2D.Double(x, y1, x, lefty2);
               arr = (Line2D[]) GTool.concatArray(arr, new Line2D[] {line1});
            }

            /*
            // 2.5d coord actually overlaps at the right/left, don't draw grid
            // otherwise it may cut through left most x labels
            if(getDepth() > 0) {
               y1 = getAxisY((RectCoord) coords[i], BOTTOM_AXIS);
            }
            */

            double x = getAxisX((RectCoord) coords[i], RIGHT_AXIS);
            Line2D line1 = new Line2D.Double(x, y1, x, y2);
            arr = (Line2D[]) GTool.concatArray(arr, new Line2D[] {line1});
         }

         return arr;
      }

      /**
       * Get the y positions at inner coord boundaries.
       * @param tbox top coord bounds.
       * @param pbox parent coord bounds.
       * @param bot true to include bottom side boundary.
       */
      @Override
      public Line2D[] getYBoundaries(Rectangle2D tbox, Rectangle2D pbox,
                                     boolean bot) {
         double x1 = pbox.getX();
         double x2 = tbox.getX() + tbox.getWidth();

         if(coords[0] instanceof FacetCoord) {
            return ((FacetCoord) coords[0]).getYBoundaries(tbox, pbox, bot);
         }
         else if(!(coords[0] instanceof RectCoord)) {
            Rectangle2D bounds = coords[0].getVGraph().getBounds();
            double y1 = bounds.getY();
            double y2 = bounds.getY() + bounds.getHeight();
            Line2D line1 = new Line2D.Double(x1, y1, x2, y1);
            Line2D line2 = new Line2D.Double(x1, y2, x2, y2);
            return bot ? new Line2D[] {line1, line2} : new Line2D[] {line2};
         }

         double y1 = getAxisY((RectCoord) coords[0], TOP_AXIS);
         double y2 = getAxisY((RectCoord) coords[0], BOTTOM_AXIS) - getDepth();
         Line2D line1 = new Line2D.Double(x1, y1, x2, y1);
         Line2D line2 = new Line2D.Double(x1, y2, x2, y2);

         return bot ? new Line2D[] {line2, line1} : new Line2D[] {line1};
      }

      private static final long serialVersionUID = 4991749162892780786L;
   }

   /**
    * Get the axis X position.
    */
   static double getAxisX(RectCoord rect, int axis) {
      DefaultAxis yaxis = rect.getAxisAt(axis);

      if(yaxis == null) {
         VGraph vgraph = rect.getVGraph();

         if(vgraph != null) {
            Rectangle2D bounds = vgraph.getPlotBounds();
            return (axis == RIGHT_AXIS)
               ? bounds.getX() + bounds.getWidth() : bounds.getX();
         }

         return 0;
      }

      Point2D pt0 = new Point2D.Double(0, 0);
      double x = yaxis.getScreenTransform().transform(pt0, null).getX();
      return x + rect.getDepth();
   }

   /**
    * Get the axis Y position.
    */
   static double getAxisY(RectCoord rect, int axis) {
      DefaultAxis xaxis = rect.getAxisAt(axis);

      if(xaxis == null) {
         VGraph vgraph = rect.getVGraph();

         if(vgraph != null) {
            Rectangle2D bounds = vgraph.getPlotBounds();
            return (axis == TOP_AXIS)
               ? bounds.getY() + bounds.getHeight() : bounds.getY();
         }

         return 0;
      }

      Point2D pt0 = new Point2D.Double(0, 0);

      pt0 = xaxis.getScreenTransform().transform(pt0, null);
      return pt0.getY() + rect.getDepth();
   }

   /**
    * Create the key for a side of a coord.
    */
   static Object createKey(Coordinate coord, boolean vertical) {
      if(coord instanceof RectCoord) {
         return ((RectCoord) coord).createKey(vertical);
      }

      return new ArrayList();
   }

   /**
    * Get the depth for 3D effect coord.
    */
   double getDepth() {
      for(int i = 0; i < coords.length; i++) {
         if(coords[i] instanceof RectCoord) {
            return ((RectCoord) coords[i]).getDepth();
         }
	 else if(coords[i] instanceof FacetCoord) {
            return ((FacetCoord) coords[i]).getDepth();
         }
      }

      return 0;
   }

   /**
    * Get all axes in this coordinate.
    * @param recursive true to include axes in nested coordinates.
    */
   @Override
   public Axis[] getAxes(boolean recursive) {
      ArrayList<Axis> list = new ArrayList<>();

      for(int i = 0; i < coords.length; i++) {
         Axis[] axes = coords[i].getAxes(recursive);
         list.addAll(Arrays.asList(axes));
      }

      return list.toArray(new Axis[list.size()]);
   }

   /**
    * @hidden
    */
   @Override
   public void copyAxisVisibility(ICoordinate coord) {
      super.copyAxisVisibility(coord);

      if(coord instanceof TileCoord) {
         TileCoord tcoord = (TileCoord) coord;

         for(int i = 0; i < coords.length && i < tcoord.coords.length; i++) {
            coords[i].copyAxisVisibility(tcoord.coords[i]);
         }
      }
   }

   @Override
   public void layoutCompleted() {
      for(Coordinate coord : getCoordinates()) {
         coord.layoutCompleted();
      }
   }

   /**
    * Check if the coordinate has the same structure as this.
    */
   public boolean equalsContent(Object obj) {
      if(!(obj instanceof TileCoord)) {
         return false;
      }

      TileCoord coord = (TileCoord) obj;

      if(!getClass().equals(coord.getClass())) {
         return false;
      }

      if(coords.length != coord.coords.length) {
         return false;
      }

      for(int i = 0; i < coords.length; i++) {
         if(!coords[i].equalsContent(coord.coords[i])) {
            return false;
         }
      }

      return true;
   }

   @Override
   public String toString() {
      return "TileCoord[" + Arrays.toString(coords) + "]";
   }

   protected Coordinate[] coords;

   private static final Logger LOG = LoggerFactory.getLogger(TileCoord.class);
}
