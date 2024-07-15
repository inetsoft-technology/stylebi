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

import inetsoft.graph.GraphConstants;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.form.LabelForm;
import inetsoft.graph.internal.*;

import java.awt.*;
import java.awt.geom.*;

/**
 * Visual object for line form.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class LabelFormVO extends FormVO {
   /**
    * Constructor.
    * @param form is the Label Form object contains label information.
    * @param label the label to display.
    * @param pos the position of the object.
    */
   public LabelFormVO(LabelForm form, Object label, Point2D pos) {
      super(form);

      this.label = label;
      this.pos = pos;

      vlabel = createLabel(label, form);
      setFixedPosition(form.isFixedPosition());
   }

   /**
    * Create a VLabel for displaying the text.
    */
   protected VLabel createLabel(Object label, LabelForm form) {
      VLabel vlabel = new FormLabel(label, form.getTextSpec());

      vlabel.setCollisionModifier(form.getCollisionModifier());
      vlabel.setInsets(form.getInsets());
      vlabel.setLabelForm(true);

      return vlabel;
   }

   /**
    * Get the text label.
    */
   public String getLabel() {
      return (label == null) ? "" : label.toString();
   }

   /**
    * Get the VLabel used by the form.
    */
   public VLabel getVLabel() {
      if(otrans == null || !otrans.equals(getScreenTransform())) {
         Point2D pos0 = pos;
         double w = vlabel.getPreferredWidth();
         double h = vlabel.getPreferredHeight();
         LabelForm form = (LabelForm) getForm();

         // don't transform if label is at fixed position
         if(isFixedPosition()) {
            pos0 = transformFixedPosition(pos0);
         }
         else {
            pos0 = getScreenTransform().transform(pos0, null);
         }

         switch(form.getAlignmentX()) {
         case GraphConstants.CENTER_ALIGNMENT:
            pos0 = new Point2D.Double(pos0.getX() - w / 2, pos0.getY());
            vlabel.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
            break;
         case GraphConstants.RIGHT_ALIGNMENT:
            pos0 = new Point2D.Double(pos0.getX() - w, pos0.getY());
            vlabel.setAlignmentX(GraphConstants.RIGHT_ALIGNMENT);
            break;
         default:
            vlabel.setAlignmentX(GraphConstants.LEFT_ALIGNMENT);
            break;
         }

         switch(form.getAlignmentY()) {
         case GraphConstants.MIDDLE_ALIGNMENT:
            pos0 = new Point2D.Double(pos0.getX(), pos0.getY() - h / 2);
            vlabel.setAlignmentY(GraphConstants.MIDDLE_ALIGNMENT);
            break;
         case GraphConstants.TOP_ALIGNMENT:
            pos0 = new Point2D.Double(pos0.getX(), pos0.getY() - h);
            vlabel.setAlignmentY(GraphConstants.TOP_ALIGNMENT);
            break;
         default:
            vlabel.setAlignmentY(GraphConstants.BOTTOM_ALIGNMENT);
            break;
         }

         pos0 = new Point2D.Double(pos0.getX() + form.getXOffset(),
                                   pos0.getY() + form.getYOffset());
         vlabel.setPosition(pos0);
         vlabel.setSize(new DimensionD(w, h));
         otrans = new AffineTransform(getScreenTransform());
      }

      return vlabel;
   }

   /**
    * Get the label bounds.
    */
   @Override
   public Rectangle2D getBounds() {
      return getVLabel().getTransformedBounds().getBounds2D();
   }

   /**
    * Get the size that is unscaled by screen transformation.
    * @param pos the position (side) in the shape, e.g. GraphConstants.TOP.
    */
   @Override
   public double getUnscaledSize(int pos, Coordinate coord) {
      if(pos == GraphConstants.TOP) {
         boolean hor = GTool.isHorizontal(coord.getCoordTransform());
         return hor ? vlabel.getPreferredHeight() : vlabel.getPreferredWidth();
      }

      return 0;
   }

   /**
    * Paint the visual object on the graphics.
    * @param g the graphics context to use for painting.
    */
   @Override
   public void paint(Graphics2D g) {
      LabelForm form = (LabelForm) getForm();
      VLabel label = getVLabel();

      label.paint(g);

      if(form.getLine() != GraphConstants.NONE) {
         Color color = getColor();

         if(color == null) {
            color = form.getColor();
         }

         g.setStroke(GTool.getStroke(form.getLine()));
         g.setColor(color != null ? color : GDefaults.DEFAULT_LINE_COLOR);
         g.draw(label.getTransformedBounds());
      }
   }

   @Override
   public Object clone() {
      LabelFormVO vo = (LabelFormVO) super.clone();

      if(vlabel != null) {
         vo.vlabel = (VLabel) vlabel.clone();
      }

      return vo;
   }

   private Object label;
   private Point2D pos;
   private VLabel vlabel;
   private transient Object otrans;
}
