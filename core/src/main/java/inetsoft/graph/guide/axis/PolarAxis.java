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
package inetsoft.graph.guide.axis;

import inetsoft.graph.*;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.VMeasureTitle;
import inetsoft.graph.internal.*;
import inetsoft.graph.scale.Scale;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * This class is a circular axis used in polar coordinate. It would be possible
 * to transform a DefaultAxis to provide the same functionalities. But it's
 * probably simpler to have a class for this coord only instead of handling
 * arbitrary transformation in DefaultAxis.
 *
 * @hidden
 * @version 10.0
 * @author InetSoft Technology
 */
public class PolarAxis extends Axis {
   /**
    * Default constructor.
    */
   public PolarAxis() {
   }

   /**
    * Create an axis for the specified scale.
    */
   public PolarAxis(Scale scale, VGraph vgraph, String... measures) {
      super(scale, vgraph);
      this.measures = measures;
   }

   /**
    * Set the width of the axis (oval).
    */
   public void setWidth(double w) {
      this.width = w;

      if(line != null) {
         line.setWidth(w);
      }
   }

   /**
    * Get the width of the axis.
    */
   public double getWidth() {
      return width;
   }

   /**
    * Set the height of the axis (oval).
    */
   public void setHeight(double h) {
      this.height = h;

      if(line != null) {
         line.setHeight(h);
      }
   }

   /**
    * Get the height of the axis.
    */
   public double getHeight() {
      return height;
   }

   /**
    * Get the minimum size for displaying this object.
    * @param min true if get min height.
    * @return the size of the axis including the labels.
    */
   private Dimension getMinSize(boolean min) {
      if(!isLabelVisible()) {
         return new Dimension(0, 0);
      }

      Scale scale = getScale();
      double[] ticks = PolarUtil.getTickLocations(scale, scale.getTicks());
      Rectangle bounds = new Rectangle(0, 0, (int) getWidth(),
                                       (int) getHeight());

      for(int i = 0; i < ticks.length; i++) {
         Point2D pt = new Point2D.Double(
            Math.cos(ticks[i]) * width / 2 + width / 2,
            Math.sin(ticks[i]) * height / 2 + height / 2);
         pt = getScreenTransform().transform(pt, null);

         double x = pt.getX();
         double y = pt.getY();
         double labelw = 0;
         double labelh = 0;

         if(min) {
            labelw = vlabels[i].getMinWidth();
            labelh = vlabels[i].getMinHeight();
         }
         else {
            labelw = vlabels[i].getPreferredWidth();
            labelh = vlabels[i].getPreferredHeight();
         }

         x += (x < width / 2) ? -labelw : labelw;
         y += (y < height / 2) ? -labelh : labelh;

         bounds.add((int) x, (int) y);
      }

      return new Dimension(bounds.width, bounds.height);
   }

   /**
    * Get the maximum length of the axis without happing labels drawn beyond
    * edge.
    * @param width the available total width for the axis.
    */
   public double getMaxLength(double width) {
      return width;
   }

   /**
    * Get min width of this vo.
    * @return min width of this vo.
    */
   @Override
   protected double getMinWidth0() {
      return getMinSize(true).getWidth();
   }

   /**
    * Get the min height of this visualizable.
    * @return the min height of this visualizable.
    */
   @Override
   protected double getMinHeight0() {
      return getMinSize(true).getHeight();
   }

   /**
    * Get preferred width of this vo.
    * @return preferred width of this vo.
    */
   @Override
   protected double getPreferredWidth0() {
      return getMinSize(false).getWidth();
   }

   /**
    * Get the preferred height of this visualizable.
    * @return the preferred height of this visualizable.
    */
   @Override
   protected double getPreferredHeight0() {
      return getMinSize(false).getHeight();
   }

   /**
    * Create the visual elements for the axis.
    */
   @Override
   protected void createVisuals() {
      removeAllVisuals();

      vlabels = new VLabel[0];
      line = new PolarAxisLine(this);
      addVisual(line);

      if(isLabelVisible()) {
         Object[] values = getScale().getValues();
         Object[] labels = getValueLabels();
         String[] fields = getScale().getFields();
         String field = (fields.length == 0) ? "" : fields[0];

         vlabels = new VLabel[labels.length];

         for(int i = 0; i < vlabels.length; i++) {
            AxisSpec axisSpec = getScale().getAxisSpec();
            TextSpec textSpec = axisSpec.getTextSpec();

            if(measures != null) {
               vlabels[i] = new VMeasureTitle(labels[i], textSpec, measures[i]);
            }
            else {
               textSpec = DefaultAxis.createLabelSpec(axisSpec, labels[i]);
               vlabels[i] = new VDimensionLabel(labels[i], textSpec, field,
                                                "polar", values[i], 0, null);
            }

            addVisual(vlabels[i]);
         }
      }
   }

   /**
    * Layout the bounds, axisline, and axis labels.
    * @param bounds the bounding area for the axis.
    */
   @Override
   public void layout(Shape bounds) {
      if(vlabels == null || vlabels.length == 0 || !isLabelVisible()) {
         return;
      }

      int tick = isTickDown() ? -TICK_SIZE : TICK_SIZE;
      Scale scale = getScale();
      double[] ticks = PolarUtil.getTickLocations(scale, scale.getTicks());
      Point2D center = new Point2D.Double(width / 2, height / 2);
      double r = Math.min(width / 2, height / 2);
      double coordRotation = GTool.getRotation(getScreenTransform());

      ytop = 0; xleft = 0; ybottom = 0; xright = 0;

      for(int i = 0; i < ticks.length; i++) {
         if(!isMinTickVisible() && i == 0 ||
            !isMaxTickVisible() && i == vlabels.length - 1)
         {
            vlabels[i].setZIndex(-1);
         }

         Point2D loc = new Point2D.Double(
            Math.cos(ticks[i]) * width / 2 + center.getX(),
            Math.sin(ticks[i]) * height / 2 + center.getY());

         double distance = center.distance(loc);
         double tickx = tick * (center.getX() - loc.getX()) / distance;
         double ticky = tick * (center.getY() - loc.getY()) / distance;
         // loc2 is the end of tick
         Point2D loc2 = new Point2D.Double(loc.getX() + tickx,
                                           loc.getY() + ticky);

         loc = getScreenTransform().transform(loc, null);
         loc2 = getScreenTransform().transform(loc2, null);

         Point2D lpos = loc2;
         boolean left = loc2.getX() < loc.getX();
         boolean top = loc2.getY() > loc.getY();

         double labelw = PolarUtil.getPieLabelWidth((int) plotSize.getWidth(),
                                                    width / 2, ticks[i],
                                                    vlabels[i], false);
         double labelh = PolarUtil.getPieLabelHeight((int) plotSize.getHeight(),
                                                     width / 2, ticks[i],
                                                     vlabels[i]);
         vlabels[i].setSize(new Dimension((int) labelw, (int) labelh));

         if(!isTickDown()) {
            left = !left;
            top = !top;
            lpos = loc;
         }

         double lx = lpos.getX(), ly = lpos.getY();
         FontMetrics fm = GTool.getFontMetrics(vlabels[i].getFont());

         // center the label above or below
         if(Math.abs(loc.getX() - loc2.getX()) < 0.5) {
            lx -= vlabels[i].getSize().getWidth() / 2;

            // bottom
            if(!top) {
               ly -= fm.getHeight();
            }
         }
         // draw string at left side of tick
         else if(left) {
            lx -= vlabels[i].getSize().getWidth();

            // left bottom
            if(!top) {
               ly -= fm.getHeight();
            }
         }
         // draw string at right side of tick
         else {
            // right bottom
            if(!top) {
               ly -= fm.getHeight();
            }
         }

         ytop = Math.max(ytop, ly);
         ybottom = Math.min(ybottom, ly);
         xleft = Math.min(xleft, lx);
         xright = Math.max(xright, lx);

         lx = lx < vgraph.getBounds().getX() ? vgraph.getBounds().getX() : lx;
         Point2D position = new Point2D.Double(lx, ly);

         vlabels[i].setPosition(position);

         // make sure label is lined up at lpos after rotation
         Point2D offset = vlabels[i].getRotationOffset(
            lpos, ticks[i] + coordRotation);
         vlabels[i].setOffset(offset);

         // make sure the label is in bounds
         Point2D inside = new Point2D.Double(
            Math.cos(ticks[i]) * width * 0.9 / 2 + center.getX(),
            Math.sin(ticks[i]) * height * 0.9 / 2 + center.getY());
         inside = getScreenTransform().transform(inside, null);
         moveInside(vlabels[i], new VLabel[0], bounds, lpos, inside);

         if(offset.getX() != 0 || offset.getY() != 0) {
            Rectangle2D nb = vlabels[i].getTransformedBounds().getBounds2D();
            offset = new Point2D.Double(
               offset.getX() - nb.getX() + position.getX(),
               offset.getY() - nb.getY() + position.getY());

            vlabels[i].setBounds(nb);
            vlabels[i].setOffset(offset);
         }
      }
   }

   /**
    * Get visual labels.
    * @return the visual labels.
    */
   @Override
   public VLabel[] getLabels() {
      return vlabels;
   }

   /**
    * Set plot size.
    */
   public void setPlotSize(DimensionD plotSize) {
      this.plotSize = plotSize;
   }

   /**
    * Get the axis line.
    */
   @Override
   public AxisLine getAxisLine() {
      return line;
   }

   /**
    * Gets the bounds of this visual in the form of a Rectangle2D object.
    * The bounds specify this visual's width, height, and location relative to
    * the whole graph.
    */
   @Override
   public Rectangle2D getBounds() {
      Point2D pos1 = new Point2D.Double(0, 0);

      pos1 = getScreenTransform().transform(pos1, null);
      double x = pos1.getX();
      double y = pos1.getY();
      double w = xleft - xright;
      double h = ytop - ybottom;

      if(w < 0) {
         x += w;
         w = -w;
      }

      if(h < 0) {
         y += h;
         h = -h;
      }

      return new Rectangle2D.Double(x, y, w, h);
   }

   private static final int TICK_SIZE = 3; // tick line length
   private double width; // width of the axis (oval) in pixels
   private double height; // width of the axis (oval) in pixels
   private PolarAxisLine line;
   private VLabel[] vlabels;
   private String[] measures = null;
   private DimensionD plotSize;
   // the top/left/bottom/right of the axis line
   private double ytop = 0, xleft = 0, ybottom = 0, xright = 0;
}
