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
package inetsoft.graph.guide.form;

import com.inetsoft.build.tern.*;
import inetsoft.graph.GraphConstants;
import inetsoft.graph.Visualizable;
import inetsoft.graph.aesthetic.GShape;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.visual.ShapeFormVO;

import java.awt.geom.Dimension2D;
import java.awt.geom.Point2D;
import java.util.Objects;

/**
 * This form can be used to add a GShape to a graph.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=ShapeForm")
public class ShapeForm extends GraphForm {
   /**
    * Default constructor.
    */
   public ShapeForm() {
   }

   /**
    * Create a form to paint a GShape.
    */
   @TernConstructor
   public ShapeForm(GShape shape) {
      setShape(shape);
   }

   /**
    * Set the shape.
    */
   @TernMethod
   public void setShape(GShape shape) {
      this.shape = shape;
   }

   /**
    * Get the shape.
    */
   @TernMethod
   public GShape getShape() {
      return shape;
   }

   /**
    * Set the size of the shape.
    */
   @TernMethod
   public void setSize(Dimension2D size) {
      this.size = size;
   }

   /**
    * Get the size of the shape.
    */
   @TernMethod
   public Dimension2D getSize() {
      return size;
   }

   /**
    * Set the tuple for obtaining the position for the label in the coordinate.
    * The tuple contains scaled values.
    */
   @TernMethod
   public void setTuple(double[] tuple) {
      this.tuple = tuple;
   }

   /**
    * Get the position tuple value.
    */
   @TernMethod
   public double[] getTuple() {
      return this.tuple;
   }

   /**
    * Set the values for obtaining the position for the label in the coordinate.
    * The values are scaled to get the logic space.
    */
   @TernMethod
   public void setValues(Object[] values) {
      this.values = GTool.unwrapArray(values);
   }

   /**
    * Get the position values value.
    */
   @TernMethod
   public Object[] getValues() {
      return this.values;
   }

   /**
    * Set the fixed position for the label.
    * @param pos the point location in graph. If the value is between 0 and
    * 1 (non-inclusive), it's treated as a proportion of the width/height. If the
    * value is negative, it's the distance from the right/top of the graph.
    */
   @TernMethod
   public void setPoint(Point2D pos) {
      this.pos = pos;
   }

   /**
    * Get the fixed position for the label.
    */
   @TernMethod
   public Point2D getPoint() {
      return pos;
   }

   /**
    * Set the the horizontal alignment.
    */
   @TernMethod
   public void setAlignmentX(int alignx) {
      this.alignx = alignx;
   }

   /**
    * Set the the vertical alignment.
    */
   @TernMethod
   public void setAlignmentY(int aligny) {
      this.aligny = aligny;
   }

   /**
    * Gets the the horizontal alignment.
    */
   @TernMethod
   public int getAlignmentX() {
      return alignx;
   }

   /**
    * Gets the the vertical alignment.
    */
   @TernMethod
   public int getAlignmentY() {
      return aligny;
   }

   /**
    * Create a visual object to visualize this element.
    * @param coord the coordinate the visual object is plotted on.
    * @return the new visual object.
    */
   @Override
   public Visualizable createVisual(Coordinate coord) {
      Point2D pt = null;

      if(pos != null) {
         pt = pos;
      }
      else {
         if(values != null) {
            tuple = scale(values, coord);
         }

         if(tuple != null) {
            pt = getPosition(coord, tuple);
         }
      }

      if(pt == null) {
         return null;
      }

      ShapeFormVO form = new ShapeFormVO(this, pt);

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
      return getPoint() != null;
   }

   /**
    * Rotate the shape. The shape is rotated at the center of the shape.
    * @param degree angle in degrees.
    */
   @TernMethod
   public void setRotation(double degree) {
      rotation = degree;
   }

   /**
    * Get the shape rotation angle.
    */
   @TernMethod
   public double getRotation() {
      return rotation;
   }

   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      ShapeForm form = (ShapeForm) obj;
      return Objects.equals(shape, form.shape) &&
         Objects.equals(size, form.size) &&
         Objects.deepEquals(tuple, form.tuple) &&
         Objects.deepEquals(values, form.values) &&
         Objects.equals(pos, form.pos) &&
         rotation == form.rotation &&
         alignx == form.alignx &&
         aligny == form.aligny;
   }

   private GShape shape;
   private Dimension2D size;
   private double[] tuple;
   private Object[] values;
   private Point2D pos;
   private double rotation = 0; // shape transformation
   private int alignx = GraphConstants.CENTER_ALIGNMENT;
   private int aligny = GraphConstants.MIDDLE_ALIGNMENT;
   private static final long serialVersionUID = 1L;
}
