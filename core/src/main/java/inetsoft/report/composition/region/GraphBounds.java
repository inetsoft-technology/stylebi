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

import inetsoft.graph.VGraph;
import inetsoft.graph.coord.*;
import inetsoft.graph.guide.VLabel;
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.viewsheet.graph.ChartInfo;

import java.awt.geom.Rectangle2D;

/**
 * This class calculates the bounds of graph bounds. We keep the sizes in double
 * values in VGraph, but the image extraction works at pixel boundaries. All
 * rounding are performed outside of core graph classes to avoid accumulative
 * rounding errors.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class GraphBounds {
   public GraphBounds(VGraph evgraph, VGraph vgraph, ChartInfo cinfo) {
      this.evgraph = evgraph;
      this.vgraph = vgraph;
      this.cinfo = cinfo;
   }

   /**
    * Get plot bounds.
    */
   public Rectangle2D getPlotBounds() {
      if(evgraph == null) {
         return new Rectangle2D.Double(0, 0, 0, 0);
      }

      Rectangle2D box = evgraph.getPlotBounds();
      Coordinate coord = evgraph.getCoordinate();

      if(coord instanceof PolarCoord) {
         // use the full size area as the plot so axis labels are in the bounds
         box = evgraph.getBounds();
      }

      if(isScrollable() && hasAxis(Coordinate.BOTTOM_AXIS)) {
         double xTitleY = getXTitleBounds().getY();
         // exclude bottom axis line
         int adj = coord instanceof FacetCoord && xTitleY < box.getY() ? 2 : 1;
         box = new Rectangle2D.Double(box.getX(), box.getY() + adj,
                                      box.getWidth() - 1, box.getHeight() - adj);
      }

      return roundRectangle(box);
   }

   /**
    * Get content bounds.
    */
   public Rectangle2D getContentBounds() {
      if(evgraph == null) {
         return new Rectangle2D.Double(0, 0, 0, 0);
      }

      Rectangle2D cbox = evgraph.getContentBounds();

      if(cbox == null) {
         return new Rectangle2D.Double(0, 0, 0, 0);
      }

      Rectangle2D box = roundRectangle(cbox);

      // for facet, the right-most axis is not included in unit size
      // calculation, so it is drawn at the edge of the content bounds. We
      // add 1 so it's included in the right Y axis bounds.
      if(isScrollable()) {
         box = new Rectangle2D.Double(box.getX(), box.getY(),
                                      box.getWidth() + 1, box.getHeight());
      }

      return box;
   }

   /**
    * Get the x title bounds.
    */
   public Rectangle2D getXTitleBounds() {
      if(evgraph == null) {
         return new Rectangle2D.Double(0, 0, 0, 0);
      }

      VLabel title = evgraph.getXTitle();
      Rectangle2D box = null;
      Rectangle2D bounds = getContentBounds();

      if(title == null) {
         box = new Rectangle2D.Double(bounds.getX(), bounds.getY(), 0, 0);
      }
      else {
         box = title.getBounds();
      }

      // if there is facet border, make title bigger to include the outer border line
      if(hasFacetBorder(Coordinate.BOTTOM_AXIS)) {
         Rectangle2D plot = evgraph.getPlotBounds();

         if(title != null) {
            if(hasAxis(Coordinate.BOTTOM_AXIS)) {
               box = new Rectangle2D.Double(bounds.getX(), box.getY(), bounds.getWidth(),
                                            box.getHeight() + 1);
            }
            else {
               box = new Rectangle2D.Double(bounds.getX(), box.getY(), bounds.getWidth(),
                                            plot.getY() - box.getY() + 1);
            }
         }
         // force the right border to be shown all the time so it doesn't
         // look broken on the right side when the chart has horizontal scroll
         else {
            // use plot position if no axis since there could be a gap between
            // the outer bounds and the plot (border)
            double y2 = hasAxis(Coordinate.BOTTOM_AXIS) ? bounds.getY() : plot.getY();
            box = new Rectangle2D.Double(bounds.getX(), y2, bounds.getWidth(), 1);
         }
      }

      return roundRectangle(box);
   }

   /**
    * Get the x2 title bounds.
    */
   public Rectangle2D getX2TitleBounds() {
      if(evgraph == null) {
         return new Rectangle2D.Double(0, 0, 0, 0);
      }

      VLabel title = evgraph.getX2Title();
      Rectangle2D box = null;
      Rectangle2D bounds = getContentBounds();

      if(title == null) {
         box = new Rectangle2D.Double(bounds.getX(), bounds.getMaxY(), 0, 0);
      }
      else {
         box = title.getBounds();
      }

      // if there is facet border, make title bigger to include the outer
      // border line
      if(hasFacetBorder(Coordinate.TOP_AXIS)) {
         Rectangle2D plot = evgraph.getPlotBounds();

         if(title != null) {
            if(hasAxis(Coordinate.TOP_AXIS)) {
               box = new Rectangle2D.Double(bounds.getX(), box.getY() - 1, bounds.getWidth(),
                                            box.getHeight() + 1);
            }
            else {
               box = new Rectangle2D.Double(bounds.getX(), plot.getMaxY() - 1, bounds.getWidth(),
                                            bounds.getMaxY() - plot.getMaxY() + 1);
            }
         }
         // force the border to be shown all the time so it doesn't
         // look broken on the right side when the chart has horizontal scroll
         else {
            // use plot position if no axis since there could be a gap between
            // the outer bounds and the plot (border)
            double y2 = hasAxis(Coordinate.TOP_AXIS) ? bounds.getMaxY() : plot.getMaxY();

            // missing part of top border in 3D coord
            if(GraphTypeUtil.is3DCoord(evgraph.getCoordinate())) {
               y2 += 1;
            }

            box = new Rectangle2D.Double(bounds.getX(), y2, bounds.getWidth(), 1);
         }
      }

      return roundRectangle(box);
   }

   /**
    * Get the y title bounds.
    */
   public Rectangle2D getYTitleBounds() {
      if(evgraph == null) {
         return new Rectangle2D.Double(0, 0, 0, 0);
      }

      VLabel title = evgraph.getYTitle();
      Rectangle2D box = null;
      Rectangle2D bounds = getContentBounds();

      if(title == null) {
         box = new Rectangle2D.Double(bounds.getX(), bounds.getY(), 0, 0);
      }
      else {
         box = title.getBounds();
      }

      // if there is facet border, make title bigger to include the outer
      // border line
      if(hasFacetBorder(Coordinate.LEFT_AXIS)) {
         Rectangle2D plot = evgraph.getPlotBounds();

         if(title != null) {
            if(hasAxis(Coordinate.LEFT_AXIS)) {
               box = new Rectangle2D.Double(box.getX(), bounds.getY(), box.getWidth() + 1,
                                            // include the corner pixel
                                            bounds.getHeight() + 1);
            }
            else {
               box = new Rectangle2D.Double(bounds.getX(), bounds.getY(),
                                            plot.getX() - box.getX() + 1,
                                            bounds.getHeight() + 1);
            }
         }
         // force the right border to be shown all the time so it doesn't
         // look broken on the right side when the chart has horizontal scroll
         else {
            // use plot position if no axis since there could be a gap between
            // the outer bounds and the plot (border)
            double x2 = hasAxis(Coordinate.LEFT_AXIS) ? bounds.getX() : plot.getX();
            box = new Rectangle2D.Double(x2, bounds.getY(), 1, bounds.getHeight());
         }
      }

      return roundRectangle(box);
   }

   /**
    * Get the 2nd y title bounds.
    */
   public Rectangle2D getY2TitleBounds() {
      if(evgraph == null) {
         return new Rectangle2D.Double(0, 0, 0, 0);
      }

      VLabel title = evgraph.getY2Title();
      Rectangle2D box = null;
      Rectangle2D bounds = getContentBounds();

      if(title == null) {
         box = new Rectangle2D.Double(bounds.getMaxX(), bounds.getY(), 0, 0);
      }
      else {
         box = title.getBounds();
      }

      // if there is facet border, make title bigger to include the outer border line
      if(hasFacetBorder(Coordinate.RIGHT_AXIS)) {
         Rectangle2D plot = evgraph.getPlotBounds();

         if(title != null) {
            if(hasAxis(Coordinate.RIGHT_AXIS)) {
               box = new Rectangle2D.Double(box.getX() - 1, bounds.getY(), box.getWidth() + 1,
                                            // include the corner pixel
                                            bounds.getHeight() + 1);
            }
            else {
               box = new Rectangle2D.Double(plot.getMaxX() - 1, bounds.getY(),
                                            box.getMaxX() - plot.getMaxX() + 1,
                                            bounds.getHeight() + 1);
            }
         }
         // force the right border to be shown all the time so it doesn't
         // look broken on the right side when the chart has horizontal scroll
         else {
            // use plot position if no axis since there could be a gap between
            // the outer bounds and the plot (border)
            boolean hasY2 = hasAxis(Coordinate.RIGHT_AXIS);
            double x2 = hasY2 || GraphTypeUtil.is3DCoord(evgraph.getCoordinate())
               ? bounds.getMaxX() : plot.getMaxX();
            box = new Rectangle2D.Double(x2 - 1, bounds.getY(), 1, bounds.getHeight());
         }
      }

      return roundRectangle(box);
   }

   /**
    * Get the axis bounds.
    */
   public Rectangle2D getAxisBounds(int axis) {
      if(evgraph == null) {
         return new Rectangle2D.Double(0, 0, 0, 0);
      }

      switch(axis) {
      case Coordinate.TOP_AXIS:
         return getTopXAxisBounds();
      case Coordinate.BOTTOM_AXIS:
         return getBottomXAxisBounds();
      case Coordinate.LEFT_AXIS:
         return getLeftYAxisBounds();
      case Coordinate.RIGHT_AXIS:
         return getRightYAxisBounds();
      }

      return null;
   }

   /**
    * Get top x axis bounds.
    */
   private Rectangle2D getTopXAxisBounds() {
      Rectangle2D bounds = getContentBounds();
      Rectangle2D pb = getPlotBounds();
      // include the facet grid line on two sides
      double x = pb.getX();
      double w = pb.getWidth();
      Rectangle2D t2bounds = getX2TitleBounds();
      double y = pb.getMaxY();
      double h = t2bounds.getY() - y;

      // if no scrolling, including two sides so rotated labels are visible
      if(!GraphUtil.isHScrollable(vgraph, cinfo)) {
         x = bounds.getX();
         w = bounds.getWidth();
      }

      // make sure the rect border is on the top border area
      if(evgraph != null && (evgraph.getCoordinate() instanceof RectCoord ||
         evgraph.getCoordinate() instanceof FacetCoord))
      {
         h = Math.max(h, 1);
      }

      return new Rectangle2D.Double(x, y, w, h);
   }

   /**
    * Get bottom x axis bounds.
    */
   private Rectangle2D getBottomXAxisBounds() {
      if(evgraph == null) {
         return new Rectangle2D.Double(0, 0, 0, 0);
      }

      Rectangle2D bounds = getContentBounds();
      Rectangle2D pb = getPlotBounds();
      Rectangle2D tbounds = getXTitleBounds();
      // include the facet grid line on two sides
      double x = pb.getX();
      double w = pb.getWidth();
      double y = tbounds.getMaxY();
      double h = pb.getY() - tbounds.getMaxY();

      // if no scrolling, including two sides so rotated labels are visible
      if(!GraphUtil.isHScrollable(vgraph, cinfo)) {
         x = bounds.getX();
         w = bounds.getWidth();
      }

      // add 1 to make sure the right grid line is included. (58016)
      return roundRectangle(new Rectangle2D.Double(x, y, w + 1, h));
   }

   /**
    * Get left y axis bounds.
    */
   private Rectangle2D getLeftYAxisBounds() {
      Rectangle2D cbounds = getContentBounds();
      Rectangle2D tbounds = getYTitleBounds();
      Rectangle2D pb = getPlotBounds();
      double x = (tbounds.getWidth() == 0) ? cbounds.getX() : tbounds.getMaxX();
      double titlew = tbounds.getWidth();
      double w = pb.getX() - cbounds.getX() - titlew;
      Rectangle2D bottom = getBottomXAxisBounds();
      double y = bottom.getMaxY();
      double h = pb.getMaxY() - y;

      return new Rectangle2D.Double(x, y, w, h);
   }

   /**
    * Get right y axis bounds.
    */
   private Rectangle2D getRightYAxisBounds() {
      Rectangle2D bounds = getContentBounds();
      Rectangle2D pb = getPlotBounds();
      Rectangle2D y2bounds = getY2TitleBounds();
      double x = pb.getMaxX();
      double w = y2bounds.getX() - pb.getMaxX();
      Rectangle2D bottom = getBottomXAxisBounds();
      double y = bottom.getMaxY();
      double h = pb.getMaxY() - y;

      // make sure the rect border is on the right border area
      if(evgraph != null && (evgraph.getCoordinate() instanceof RectCoord ||
         evgraph.getCoordinate() instanceof FacetCoord))
      {
         w = Math.max(w, 1);
      }

      return new Rectangle2D.Double(x, y, w, h);
   }

   /**
    * Get the top-left corner bounds for facet.
    */
   public Rectangle2D getFacetTLBounds() {
      Rectangle2D top = getTopXAxisBounds();
      Rectangle2D left = getLeftYAxisBounds();

      return new Rectangle2D.Double(left.getX(), top.getY(),
                                    left.getWidth(), top.getHeight());
   }

   /**
    * Get the top-right corner bounds for facet.
    */
   public Rectangle2D getFacetTRBounds() {
      Rectangle2D top = getTopXAxisBounds();
      Rectangle2D right = getRightYAxisBounds();

      return new Rectangle2D.Double(right.getX(), top.getY(),
                                    right.getWidth(), top.getHeight());
   }

   /**
    * Get the bottom-left corner bounds for facet.
    */
   public Rectangle2D getFacetBLBounds() {
      Rectangle2D bottom = getBottomXAxisBounds();
      Rectangle2D left = getLeftYAxisBounds();

      return new Rectangle2D.Double(left.getX(), bottom.getY(),
                                    left.getWidth(), bottom.getHeight());
   }

   /**
    * Get the bottom-right corner bounds for facet.
    */
   public Rectangle2D getFacetBRBounds() {
      Rectangle2D bottom = getBottomXAxisBounds();
      Rectangle2D right = getRightYAxisBounds();

      return new Rectangle2D.Double(right.getX(), bottom.getY(),
                                    right.getWidth(), bottom.getHeight());
   }

   /**
    * Round the rectangle position and size.
    */
   private Rectangle2D roundRectangle(Rectangle2D box) {
      double x = box.getX();
      double y = box.getY();
      double w = box.getWidth();
      double h = box.getHeight();

      // e.g. 3.0000099 or 1.99999999
      if(Math.abs(x - (int) x) < 0.01) {
         x = (int) x;
      }
      else {
         x = Math.ceil(x);
      }

      if(Math.abs(y - (int) y) < 0.01) {
         y = (int) y;
      }
      else {
         y = Math.ceil(y);
      }

      if(Math.abs(w - Math.ceil(w)) < 0.01) {
         w = Math.ceil(w);
      }
      else {
         w = Math.floor(w);
      }

      if(Math.abs(h - Math.ceil(h)) < 0.01) {
         h = Math.ceil(h);
      }
      else {
         h = Math.floor(h);
      }

      return new Rectangle2D.Double(x, y, w, h);
   }

   /**
    * Check if this graph is scrollable.
    */
   public boolean isScrollable() {
      // if isHScrollable (but not facet), we don't include the left side of x axis
      // (see getBottomXAxisBounds), so we should include the facet areas (left-bottom)
      // to avoid x label being chopped off. (52021)
      return (evgraph != null && evgraph.getCoordinate() instanceof FacetCoord) ||
         GraphUtil.isHScrollable(vgraph, cinfo);
   }

   /**
    * Check if facet border is drawn, either by isFacetGrid, or as a facet
    * grid line at the edge.
    */
   public boolean hasFacetBorder(int axisPos) {
      if(hasFacetBorderCache[axisPos] != null) {
         return hasFacetBorderCache[axisPos];
      }

      if(evgraph != null && evgraph.getCoordinate() instanceof FacetCoord) {
         FacetCoord facet = (FacetCoord) evgraph.getCoordinate();

         // facetGrid not supported for 3d coord
         if(facet.isFacetGrid() && facet.getDepth() == 0) {
            return hasFacetBorderCache[axisPos] = true;
         }

         return hasFacetBorderCache[axisPos] = hasAxis(axisPos);
      }

      return hasFacetBorderCache[axisPos] = false;
   }

   /**
    * Check if an axis exists at the position.
    */
   public boolean hasAxis(int axisPos) {
      if(hasAxisCache[axisPos] != null) {
         return hasAxisCache[axisPos];
      }

      return hasAxisCache[axisPos] = evgraph != null &&
         evgraph.getCoordinate().anyAxisAt(axisPos, axis -> axis.isLabelVisible());
   }

   private VGraph evgraph;
   private VGraph vgraph;
   private ChartInfo cinfo;
   // caching
   private Boolean[] hasFacetBorderCache = new Boolean[5];
   private Boolean[] hasAxisCache = new Boolean[5];
}
