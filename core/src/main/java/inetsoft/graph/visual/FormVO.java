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
package inetsoft.graph.visual;

import inetsoft.graph.Graphable;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.guide.form.GeomForm;
import inetsoft.graph.guide.form.GraphForm;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;

import java.awt.*;
import java.awt.geom.*;

/**
 * This is the base class for form guides visual objects.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class FormVO extends VisualObject {
   /**
    * Constractor.
    * @param form is the form object.
    */
   public FormVO(GraphForm form) {
      this.form = form;
   }

   /**
    * Get the graphable object that produced this visualizable.
    */
   @Override
   public Graphable getGraphable() {
      return form;
   }

   /**
    * Get form.
    * @return the form object.
    */
   public GraphForm getForm() {
      return form;
   }

   /**
    * Set the shape for this form.
    * @param shape is the form will paint.
    */
   public void setShape(Shape shape) {
      this.shape = shape;
   }

   /**
    * Get the shape for this form.
    * @return form's shape.
    */
   public Shape getShape() {
      return shape;
   }

   /**
    * Set the color for the form visual object.
    */
   public void setColor(Color color) {
      this.color = color;
   }

   /**
    * Get the color of the form visual object.
    */
   public Color getColor() {
      return color;
   }

   /**
    * Transform the logic coordinate to chart coordinate.
    * @param coord the visual object's coordinate.
    */
   @Override
   public void transform(Coordinate coord) {
      this.coord = coord;

      if(!isFixedPosition()) {
         setShape((Shape) coord.transformShape(shape));
      }
   }

   /**
    * Paint the visual object on the graphics.
    * @param g the graphics context to use for painting.
    */
   @Override
   public void paint(Graphics2D g) {
      Shape shape = getShape();

      if(shape == null) {
         return;
      }

      shape = getTransformedShape(shape);

      Graphics2D g2 = (Graphics2D) g.create();
      Color color = (this.color != null) ? this.color : form.getColor();

      if(color == null) {
         color = GDefaults.DEFAULT_LINE_COLOR;
      }

      float red = (float) (color.getRed() / 255.0);
      float green = (float) (color.getGreen() / 255.0);
      float blue = (float) (color.getBlue() / 255.0);
      float alpha = (float) ((form.getAlpha()) / 100.0);
      Color color1 = new Color(red, green, blue, alpha);
      g2.setColor(color1);
      g2.setStroke(GTool.getStroke(form.getLine()));
      paint(g2, shape);
      g2.dispose();
   }

   /**
    * Paint the shape.
    */
   protected void paint(Graphics2D g, Shape shape) {
      if(form instanceof GeomForm && ((GeomForm) form).isFill()) {
         g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                            RenderingHints.VALUE_STROKE_PURE);
         g.fill(shape);
      }
      else {
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
         g.draw(shape);
      }
   }

   /**
    * Get the bounding box of the visual object in the graphics output.
    * @return the bounding box of the visual object in the graphics output.
    */
   @Override
   public Rectangle2D getBounds() {
      Shape shape = getShape();

      if(shape == null) {
         return null;
      }

      shape = getTransformedShape(shape);
      return shape.getBounds2D();
   }

   /**
    * Get min width of this vo.
    * @return min width of this vo.
    */
   @Override
   protected double getMinWidth0() {
      return 0;
   }

   /**
    * Get the min height of this visualizable.
    * @return the min height of this visualizable.
    */
   @Override
   protected double getMinHeight0() {
      return 0;
   }

   /**
    * Get preferred width of this vo.
    * @return preferred width of this vo.
    */
   @Override
   protected double getPreferredWidth0() {
      return 0;
   }

   /**
    * Get the preferred height of this visualizable.
    * @return the preferred height of this visualizable.
    */
   @Override
   protected double getPreferredHeight0() {
      return 0;
   }

   /**
    * Check if the shape is at fixed position. If true, screen transformation
    * is not applied to the shape and position.
    */
   public boolean isFixedPosition() {
      return fixed;
   }

   /**
    * Set if the shape is at fixed position.
    */
   public void setFixedPosition(boolean fixed) {
      this.fixed = fixed;
   }

   /**
    * Handle negative distance (from top/right) in fixed position.
    */
   protected Point2D transformFixedPosition(Point2D pt) {
      return GTool.transformFixedPosition(coord, pt);
   }

   /**
    * Get a shape transformed with screen transformation.
    */
   protected Shape getTransformedShape(Shape shape) {
      if(!isFixedPosition()) {
         shape = getScreenTransform().createTransformedShape(shape);
      }

      return applyOffset(shape);
   }

   /**
    * Apply the form offset to the shape.
    */
   protected Shape applyOffset(Shape shape) {
      if(form != null) {
         int xoffset = form.getXOffset();
         int yoffset = form.getYOffset();

         if(xoffset != 0 || yoffset != 0) {
            AffineTransform trans = AffineTransform.getTranslateInstance(
               xoffset, yoffset);
            shape = trans.createTransformedShape(shape);
         }
      }

      return shape;
   }

   /**
    * Check if this object should be kept inside plot area.
    */
   @Override
   public boolean isInPlot() {
      return form.isInPlot();
   }

   /**
    * Get the associated coordinate.
    */
   protected Coordinate getCoordinate() {
      return coord;
   }

   @Override
   public void layoutCompleted() {
      if(form != null) {
         form.layoutCompleted();
      }
   }

   private Shape shape;
   private GraphForm form;
   private Coordinate coord;
   private Color color = null;
   private boolean fixed = false;
}
