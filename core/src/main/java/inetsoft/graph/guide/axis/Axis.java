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
package inetsoft.graph.guide.axis;

import inetsoft.graph.*;
import inetsoft.graph.aesthetic.TextFrame;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.*;
import inetsoft.graph.scale.Scale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.*;
import java.util.Arrays;
import java.util.Vector;

/**
 * This class provides the rendering of axis.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public abstract class Axis extends VContainer {
   /**
    * Default constructor.
    */
   public Axis() {
      setZIndex(GDefaults.AXIS_Z_INDEX);
   }

   /**
    * Create an axis for the specified scale.
    * @param scale the scale for this visual axis.
    * @param vgraph the visual graph.
    */
   public Axis(Scale scale, VGraph vgraph) {
      this();
      AxisSpec spec = scale.getAxisSpec();

      this.vgraph = vgraph;
      this.labelVisible = spec.isLabelVisible();
      this.lineVisible = spec.isLineVisible();
      this.tickVisible = spec.isTickVisible();
      this.lineColor = spec.getLineColor();

      setScale(scale);
   }

   /**
    * Get the scale associated with this axis.
    */
   public Scale getScale() {
      return scale;
   }

   /**
    * Set the scale associated with this axis.
    */
   public void setScale(Scale scale) {
      this.scale = scale;
      createVisuals();
   }

   /**
    * Get the primary axis of this axis.
    */
   public Axis getPrimaryAxis() {
      return primary;
   }

   /**
    * Set the primary axis if this axis is the secondary axis
    * (axis not showing labels) in a RectCoord.
    */
   public void setPrimaryAxis(Axis primary) {
      this.primary = primary;
   }

   /**
    * Get the ticks for displaying on axis.
    */
   public double[] getTicks() {
      return (primary != null) ? primary.getTicks() : scale.getTicks();
   }

   /**
    * Set whether to show axis labels.
    * @param show true if show labels, otherwise false.
    */
   public void setLabelVisible(boolean show) {
      this.labelVisible = show;
      createVisuals();
   }

   /**
    * Check whether to show axis labels.
    * @return true if show labels, otherwise false.
    */
   public boolean isLabelVisible() {
      return labelVisible;
   }

   /**
    * Set whether to show ticks.
    * @param show true if show labels, otherwise false.
    */
   public void setTickVisible(boolean show) {
      this.tickVisible = show;
      createVisuals();
   }

   /**
    * Check whether to show ticks.
    * @return show true if show labels, otherwise false.
    */
   public boolean isTickVisible() {
      return tickVisible;
   }

   /**
    * Set whether to draw the tick at the minimum position.
    */
   public void setMinTickVisible(boolean vis) {
      this.minTickVisible = vis;
   }

   /**
    * Check whether to draw the tick at the minimum position.
    */
   public boolean isMinTickVisible() {
      return minTickVisible;
   }

   /**
    * Set whether to draw the tick at the maximum position.
    */
   public void setMaxTickVisible(boolean vis) {
      this.maxTickVisible = vis;
   }

   /**
    * Check whether to draw the tick at the maximum position.
    */
   public boolean isMaxTickVisible() {
      return maxTickVisible;
   }

   /**
    * Set whether to show axis line.
    * @param show true if show labels, otherwise false.
    */
   public void setLineVisible(boolean show) {
      this.lineVisible = show;
      createVisuals();
   }

   /**
    * Check whether to axis line.
    * @return show true if show labels, otherwise false.
    */
   public boolean isLineVisible() {
      return lineVisible;
   }

   /**
    * Set whether the tick should to drawn pointing down from the axis line.
    * @param tickdown true if tick should to drawn pointing down from the axis
    * line, otherwise false.
    */
   public void setTickDown(boolean tickdown) {
      this.tickdown = tickdown;
   }

   /**
    * Check whether the tick should to drawn pointing down from the axis line.
    * @return true if tick should to drawn pointing down from the axis
    * line, otherwise false.
    */
   public boolean isTickDown() {
      return tickdown;
   }

   /**
    * Add a grid line to the axis.
    * @param line the gird line of this axis.
    */
   public void addGridLine(GridLine line) {
      if(gridlines == null) {
         gridlines = new Vector<>();
      }

      gridlines.add(line);
      line.setAxis(this);

      if(vgraph != null) {
         vgraph.addVisual(line);

         // avoid grid line too close or overlapped
         if(lastVisibleLine == null) {
            lastVisibleLine = line;
         }
         else {
            double dis = lastVisibleLine.distance(line);

            if(dis < GDefaults.TICK_MIN_GAP) {
               line.setZIndex(-1);
            }
            else {
               lastVisibleLine = line;
            }
         }
      }
   }

   /**
    * Get the number of grid lines.
    * @return the number of grid lines.
    */
   public int getGridLineCount() {
      return gridlines != null ? gridlines.size() : 0;
   }

   /**
    * Get the specified grid line.
    * @param idx the grid ine index.
    */
   public GridLine getGridLine(int idx) {
      return gridlines != null ? gridlines.get(idx) : null;
   }

   /**
    * Set/replace a grid line at the specified location.
    * @param idx grid line index.
    * @param line the gird line shape.
    */
   public void setGridLine(int idx, GridLine line) {
      if(gridlines != null) {
         gridlines.set(idx, line);
      }
   }

   /**
    * Remove the grid line at the specified location.
    * @param idx grid line index.
    */
   public void removeGridLine(int idx) {
      if(vgraph != null) {
         vgraph.removeVisual(getGridLine(idx));
      }

      if(gridlines != null) {
         gridlines.remove(idx);
      }
   }

   /**
    * Remove all grid lines.
    */
   public void removeAllGridLines() {
      for(int i = 0; i < getGridLineCount(); i++) {
         vgraph.removeVisual(getGridLine(i));
      }

      gridlines = null;
   }

   /**
    * Transform the grid lines onto the specified coordinate.
    * @param coord the specified coordinate.
    */
   public void transformGridLines(Coordinate coord) {
      for(int i = 0; i < getGridLineCount(); i++) {
         GridLine line = getGridLine(i);
         Shape shape = line.getShape();

         if(!scale.getAxisSpec().isGridAsShape() && shape instanceof Line2D) {
            Point2D p1 = ((Line2D) shape).getP1();
            Point2D p2 = ((Line2D) shape).getP2();

            p1 = (Point2D) coord.transformShape(p1);
            p2 = (Point2D) coord.transformShape(p2);

            shape = new Line2D.Double(p1, p2);
         }
         else {
            shape = (Shape) coord.transformShape(shape);
         }

         line.setShape(shape);
      }
   }

   /**
    * Set the transformation for the grid lines.
    * @param trans transform in the Graphics2D context.
    */
   public void setGridLineTransform(AffineTransform trans) {
      this.gridtrans = (trans == null) ? new GTransform() : trans;
   }

   /**
    * Get the transformation of the grid lines.
    * @return the transformation to map chart coordinate to screen coordinate.
    */
   public AffineTransform getGridLineTransform() {
      if(gridtrans == null) {
         gridtrans = new AffineTransform();
      }

      return gridtrans;
   }

   /**
    * Set the color for drawing axis lines.
    * @param lineColor new color.
    */
   public void setLineColor(Color lineColor) {
      this.lineColor = lineColor;
   }

   /**
    * Get the color for drawing axis lines.
    * @return the color for drawing axis lines.
    */
   public Color getLineColor() {
      return lineColor;
   }

   /**
    * Set the background color of the axis.
    */
   public void setBackground(Color bg) {
      this.bg = bg;
   }

   /**
    * Get the background color of the axis.
    */
   public Color getBackground() {
      return bg;
   }

   /**
    * Create the visual elements for the axis.
    */
   protected abstract void createVisuals();

   /**
    * Layout the bounds, axisline, and axis labels.
    * @param bounds the bounding area for the axis. This is the outer edge the
    * text of the axis should be constrained. It may be different from the
    * axis size and is dependent on coordinate type.
    */
   public abstract void layout(Shape bounds);

   /**
    * Get the axis line.
    */
   public abstract AxisLine getAxisLine();

   /**
    * Get axis labels.
    */
   public abstract VLabel[] getLabels();

   /**
    * Make sure the label is within the outer bounds.
    * @param bounds outer bounds when the label is constrained.
    * @param pos the label tick position.
    * @param pos2 the position to shift to if it's out of bounds.
    * @return true if label is moved.
    */
   boolean moveInside(VLabel vlabel, VLabel[] vlabels, Shape bounds, Point2D pos, Point2D pos2) {
      if(bounds == null) {
         return false;
      }

      double fontH = GTool.getFontMetrics(vlabel.getFont()).getHeight();

      Rectangle2D obox = vlabel.getBounds();
      Rectangle2D bbox = bounds.getBounds2D();

      // don't move label if no space to move
      if(fontH > bbox.getHeight() - 2) {
         // make sure it's not out of bounds so it won't draw on top of axis line
         vlabel.setBounds(new Rectangle2D.Double(obox.getX(), bbox.getMaxY() - obox.getHeight(),
                                                 obox.getWidth(), bbox.getHeight()));

         // if space is too small, label will be drawn outside of the axis area and possibly
         // drawn on top facet axis line. just ignore. this condition happens more easily in
         // deeply nested facet graph where the vgraph (not evgraph) is given a small size
         // so the subgraph axes will have very little space.
         if(obox.getHeight() < fontH / 2 || bbox.getHeight() < obox.getHeight() / 2) {
            vlabel.setZIndex(-1);
         }
         return false;
      }

      double len = pos.distance(pos2);
      double max = (len < 10) ? 5 : Math.max(len - 10, 6);
      Point2D pt = vlabel.getPosition();
      boolean moved = false;

      if(vlabels.length > 1) {
         VLabel neighbor = null;

         if(vlabels[0] == vlabel) {
            neighbor = vlabels[1];
         }
         else if(vlabels[vlabels.length - 1] == vlabel) {
            neighbor = vlabels[vlabels.length - 2];
         }

         // don't move past the neighbor
         if(neighbor != null) {
            double dist = neighbor.getPosition().distance(vlabel.getPosition());
            max = Math.min(max, dist);
         }
      }

      // avoid clipping side of text. (63758)
      int xmoe = pos.getX() == pos2.getX() ? 1 : 0;
      // backward compatibility and also the top/bottom of char rarely touches the edge
      // so allow 1 pixel. (63824)
      int ymoe = 1; // pos.getY() == pos2.getY() ? 1 : 0;

      // move inside bounds
      for(int i = 0; !vlabel.isContained(bounds, xmoe, ymoe) && i < max; i++) {
         double x = pt.getX() + (pos2.getX() - pos.getX()) * (i + 1) / len;
         double y = pt.getY() + (pos2.getY() - pos.getY()) * (i + 1) / len;
         vlabel.setPosition(new Point2D.Double(x, y));
         moved = true;
      }

      if(!moved) {
         return moved;
      }

      if(vlabel.getTextSpec().getRotation() != 0) {
         // rotated labels size not changed, it's allowed to be out of bounds
         return true;
      }

      // if the label bounds is outside of the bounding box, resize the size
      // to the text width.
      Area vbox = new Area(vlabel.getBounds());
      vbox.subtract(new Area(bounds));
      Rectangle2D box = vbox.getBounds2D();

      if(box.getWidth() > 2 && box.getHeight() > 2) {
         String[] str = vlabel.getDisplayLabel();

         pos = vlabel.getTextPosition();

         double x = pos.getX();
         double y = pos.getY();
         double w = vlabel.getPreferredWidth(str);
         double h = vlabel.getPreferredHeight(str);

         // for gui, keep labels same height so selection area is consistent
         if(GTool.isHorizontal(getScreenTransform())) {
            y = vlabel.getPosition().getY();
            h = vlabel.getSize().getHeight();
         }

         vlabel.setBounds(x, y, w, h);
      }

      return true;
   }

   /**
    * Get the label objects for the ticks. This is the raw value that may need
    * to be formatted. If a text frame is set in the axisSpec, the tick value is
    * mapped using the text frame.
    */
   Object[] getValueLabels() {
      Object[] vals = scale.getValues();
      Object[] nvals = new Object[vals.length];
      TextFrame textFrame = scale.getAxisSpec().getTextFrame();

      if(textFrame != null) {
         for(int i = 0; i < vals.length; i++) {
            nvals[i] = textFrame.getText(vals[i]);
         }
      }
      else {
         System.arraycopy(vals, 0, nvals, 0, vals.length);
      }

      return nvals;
   }

   /**
    * Set the coordinate of the axis.
    */
   public void setCoordinate(Coordinate coord) {
      this.coordinate = coord;
   }

   /**
    * Get the coordinate of the axis.
    */
   public Coordinate getCoordinate() {
      return coordinate;
   }

   // Tick may be painted outside of bounds
   @Override
   public boolean isPaintInBounds() {
      return false;
   }

   @Override
   public void layoutCompleted() {
      super.layoutCompleted();

      if(gridlines != null) {
         gridlines.trimToSize();
      }
   }

   /**
    * Remove axis labels.
    */
   public void removeAllLabels() {
   }

   @Override
   public String toString() {
      return super.toString() + "[" + (scale != null ? Arrays.toString(scale.getFields()) : "")
         + "]";
   }

   protected VGraph vgraph;
   private Scale scale;
   private Axis primary;
   private boolean labelVisible = true;
   private boolean tickVisible = true;
   private boolean lineVisible = true;
   private boolean tickdown = true;
   private boolean minTickVisible = true;
   private boolean maxTickVisible = true;
   private AffineTransform gridtrans = null;
   private Vector<GridLine> gridlines = null;
   private Color lineColor = Color.gray;
   private Color bg = null;
   private GridLine lastVisibleLine;
   private Coordinate coordinate = null;

   private static final Logger LOG =
      LoggerFactory.getLogger(Axis.class);
}
