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
package inetsoft.graph.guide.form;

import com.inetsoft.build.tern.*;
import inetsoft.graph.Visualizable;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.visual.RectFormVO;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Objects;

/**
 * This is a rectangle form guide.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=RectForm")
public class RectForm extends GeomForm {
   /**
    * Default constructor.
    */
   public RectForm() {
   }

   /**
    * Create a fixed position and size rectangle. The values are in points in
    * math coordinate.
    */
   public RectForm(Rectangle2D rect) {
      this.tlpt = new Point2D.Double(rect.getMinX(), rect.getMaxY());
      this.brpt = new Point2D.Double(rect.getMaxX(), rect.getMinY());
   }

   /**
    * Create a rectangle from two points in logic space (scaled tuple).
    * @param topLeft top-left tuple of a rectangle.
    * @param bottomRight bottom-right tuple of a rectangle.
    */
   public RectForm(double[] topLeft, double[] bottomRight) {
      this.topLeft = topLeft;
      this.bottomRight = bottomRight;
   }

   /**
    * Create a rectangle from two points specified as unscaled values.
    * @param topLeft top-left unscaled tuple of a rectangle.
    * @param bottomRight bottom-right unscaled tuple of a rectangle.
    */
   @TernConstructor
   public RectForm(Object[] topLeft, Object[] bottomRight) {
      this.tlvalues = GTool.unwrapArray(topLeft);
      this.brvalues = GTool.unwrapArray(bottomRight);
   }

   /**
    * Set the point for the top-left corner.
    * @param point the point location in graph. If the value is between 0 and
    * 1 (non-inclusive), it's treated as a proportion of the width/height. If the
    * value is negative, it's the distance from the right/top of the graph.
    */
   @TernMethod
   public void setTopLeftPoint(Point2D point) {
      this.tlpt = point;
   }

   /**
    * Get the point for the top-left corner.
    */
   @TernMethod
   public Point2D getTopLeftPoint() {
      return tlpt;
   }

   /**
    * Set the point for the bottom-right corner.
    * @param point the point location in graph. If the value is between 0 and
    * 1 (non-inclusive), it's treated as a proportion of the width/height. If the
    * value is negative, it's the distance from the right/top of the graph.
    */
   @TernMethod
   public void setBottomRightPoint(Point2D point) {
      this.brpt = point;
   }

   /**
    * Get the point for the bottom-right corner.
    */
   @TernMethod
   public Point2D getBottomRightPoint() {
      return brpt;
   }

   /**
    * Set the tuple for the top-left corner.
    */
   @TernMethod
   public void setTopLeftTuple(double[] tuple) {
      this.topLeft = tuple;
   }

   /**
    * Get the tuple for the top-left corner.
    */
   @TernMethod
   public double[] getTopLeftTuple() {
      return topLeft;
   }

   /**
    * Set the tuple for the bottom-right corner.
    */
   @TernMethod
   public void setBottomRightTuple(double[] tuple) {
      this.bottomRight = tuple;
   }

   /**
    * Get the tuple for the bottom-right corner.
    */
   @TernMethod
   public double[] getBottomRightTuple() {
      return bottomRight;
   }

   /**
    * Set the values for the top-left corner.
    */
   @TernMethod
   public void setTopLeftValues(Object[] values) {
      this.tlvalues = GTool.unwrapArray(values);
   }

   /**
    * Get the values for the top-left corner.
    */
   @TernMethod
   public Object[] getTopLeftValues() {
      return tlvalues;
   }

   /**
    * Set the values for the bottom-right corner.
    */
   @TernMethod
   public void setBottomRightValues(Object[] values) {
      this.brvalues = GTool.unwrapArray(values);
   }

   /**
    * Get the values for the bottom-right corner.
    */
   @TernMethod
   public Object[] getBottomRightValues() {
      return brvalues;
   }

   /**
    * Create a visual object to visualize this element.
    * @param coord the coordinate the visual object is plotted on.
    * @return the new visual object.
    */
   @Override
   public Visualizable createVisual(Coordinate coord) {
      Rectangle2D shape;

      if(tlpt != null && brpt != null) {
         shape = new Rectangle2D.Double(tlpt.getX(), brpt.getY(),
                                        brpt.getX() - tlpt.getX(),
                                        tlpt.getY() - brpt.getY());
      }
      else {
         if(tlvalues != null) {
            topLeft = scale(tlvalues, coord);
         }

         if(brvalues != null) {
            bottomRight = scale(brvalues, coord);
         }

         Point2D pt1 = null;
         Point2D pt2 = null;

         if(bottomRight != null && topLeft != null) {
            pt1 = getPosition(coord, bottomRight);
            pt2 = getPosition(coord, topLeft);
         }

         if(pt1 == null || pt2 == null) {
            return null;
         }

         if(ignoreNegative && (pt1.getY() > pt2.getY() || pt2.getX() > pt1.getX())) {
            return null;
         }

         double x = Math.min(pt1.getX(), pt2.getX());
         double y = Math.min(pt1.getY(), pt2.getY());
         double width = Math.abs(pt2.getX() - pt1.getX());
         double height = Math.abs(pt2.getY() - pt1.getY());
         shape = new Rectangle2D.Double(x, y, width, height);
      }

      RectFormVO form = new RectFormVO(this, shape);

      form.setZIndex(getZIndex());
      form.setFixedPosition(isFixedPosition());

      return form;
   }

   /**
    * Check if form is at fixed position.
    * @hidden
    */
   @Override
   public boolean isFixedPosition() {
      return getTopLeftPoint() != null && getBottomRightPoint() != null;
   }

   /**
    * Set whether to ignore negative size rectangle.
    */
   @TernMethod
   public void setIgnoreNegative(boolean ignore) {
      this.ignoreNegative = ignore;
   }

   /**
    * Check whether to ignore negative size rectangle.
    */
   @TernMethod
   public boolean isIgnoreNegative() {
      return ignoreNegative;
   }

   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      RectForm form = (RectForm) obj;
      return Objects.deepEquals(topLeft, form.topLeft) &&
         Objects.deepEquals(bottomRight, form.bottomRight) &&
         Objects.deepEquals(tlvalues, form.tlvalues) &&
         Objects.deepEquals(brvalues, form.brvalues) &&
         Objects.equals(tlpt, form.tlpt) &&
         Objects.equals(brpt, form.brpt) &&
         ignoreNegative == form.ignoreNegative;
   }

   private double[] topLeft; // scaled tuple
   private double[] bottomRight; // scaled tuple
   private Object[] tlvalues; // unscaled values
   private Object[] brvalues; // unscaled values
   private Point2D tlpt; // top-left fixed point
   private Point2D brpt; // top-left fixed point
   private boolean ignoreNegative = false;
   private static final long serialVersionUID = 1L;
}
