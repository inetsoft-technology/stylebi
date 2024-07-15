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

import inetsoft.graph.Visualizable;
import inetsoft.graph.internal.GTool;

import java.awt.*;
import java.awt.geom.*;

/**
 * This is the grid line on a coordinate.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class GridLine extends Visualizable {
   /**
    * Constructor.
    * @param shape grid line shape.
    * @param axis axis that grid line belongs to.
    * @param zindex the z index.
    */
   public GridLine(Shape shape, Axis axis, int zindex) {
      this(shape, axis, zindex, false);
   }

   /**
    * Constructor.
    * @param shape grid line shape.
    * @param axis axis that grid line belongs to.
    * @param zindex the z index.
    * @param isGrid true if grid line, otherwise separator line
    */
   public GridLine(Shape shape, Axis axis, int zindex, boolean isGrid) {
      this.shape = shape;
      this.axis = axis;
      this.isGrid = isGrid;
      setZIndex(zindex);

      if(axis != null) {
         color = axis.getScale().getAxisSpec().getGridColor();
         style = axis.getScale().getAxisSpec().getGridStyle();
      }
   }

   /**
    * Paint the visual object on the graphics.
    */
   @Override
   public void paint(Graphics2D g) {
      Shape shp = shape;

      if(axis != null) {
         shp = axis.getGridLineTransform().createTransformedShape(shape);
      }

      Stroke ostroke = g.getStroke();
      Color ocolor = g.getColor();
      Rectangle clip = g.getClipBounds();
      Rectangle2D rect = shp.getBounds2D();

      // GridLine always returns null for bounds since the bounds is not known until paint().
      // we check the bounds here (instead of VGraph) to avoid unnecessary painting
      if(clip != null && !clip.intersects(rect.getMinX() - 1, rect.getMinY() - 1,
                                          rect.getWidth() + 2, rect.getHeight() + 2))
      {
         return;
      }

      // @by larryl, in jdk 1.6, a Path2D at the exact same position may be
      // drawn at slightly different offset to a Line2D (AxisLine) at the
      // same position. Change the shape to Line2D to avoid the problem.
      if(rect.getWidth() == 0 || rect.getHeight() == 0) {
         shp = (rect.getWidth() == 0) ?
            new Line2D.Double(rect.getX(), rect.getY(),
                              rect.getX(), rect.getY() + rect.getHeight()) :
            new Line2D.Double(rect.getX(), rect.getY(),
                              rect.getX() + rect.getWidth(), rect.getY());
      }

      // not vertical or horizontal line, anti-alias
      if(rect.getWidth() > 1 && rect.getHeight() > 1 || GTool.isVectorGraphics(g)) {
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      }
      else {
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
      }

      g.setColor(color);
      g.setStroke(GTool.getStroke(style));
      GTool.drawLine(g, shp);
      g.setStroke(ostroke);
      g.setColor(ocolor);
   }

   /**
    * Get the preferred width of this visualizable.
    */
   @Override
   protected double getPreferredWidth0() {
      // egnore this method
      return 0;
   }

   /**
    * Get the preferred height of this visualizable.
    */
   @Override
   protected double getPreferredHeight0() {
      // egnore this method
      return 0;
   }

   /**
    * Get the min width of this visualizable.
    */
   @Override
   protected double getMinWidth0() {
      // egnore this method
      return 0;
   }

   /**
    * Get the min height of this visualizable.
    */
   @Override
   protected double getMinHeight0() {
      // egnore this method
      return 0;
   }

   /**
    * Gets the bounds of this visual in the form of a Rectangle2D object.
    * The bounds specify this visual's width, height, and location relative to
    * the whole graph.
    * @return always return null.
    */
   @Override
   public Rectangle2D getBounds() {
      return null;
   }

   /**
    * Get grid line shape.
    */
   public Shape getShape() {
      return shape;
   }

   /**
    * Set grid line shape.
    */
   public void setShape(Shape shape) {
      this.shape = shape;
   }

   /**
    * Get the axis that contains this axis line.
    */
   public Axis getAxis() {
      return axis;
   }

   /**
    * Set the axis that contains this axis line.
    */
   public void setAxis(Axis axis) {
      this.axis = axis;
   }

   /**
    * Get the line color.
    */
   public Color getColor() {
      return color;
   }

   /**
    * Set the line color.
    */
   public void setColor(Color color) {
      this.color = color;
   }

   /**
    * Get the line style.
    */
   public int getStyle() {
      return style;
   }

   /**
    * Set the line style, e.g. GraphConstants.THIN_LINE.
    */
   public void setStyle(int style) {
      this.style = style;
   }

   /**
    * Returns the distance from one grid line left top corner to another.
    */
   double distance(GridLine line) {
      Rectangle2D bounds1 = shape.getBounds2D();
      Rectangle2D bounds2 = line.getShape().getBounds2D();
      Point2D pos1 = new Point2D.Double(bounds1.getX(), bounds1.getY());
      Point2D pos2 = new Point2D.Double(bounds2.getX(), bounds2.getY());

      return pos1.distance(pos2);
   }

   private Shape shape;
   private Axis axis;
   private Color color;
   private int style;
   private boolean isGrid;
}
