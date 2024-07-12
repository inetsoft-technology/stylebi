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
package inetsoft.graph.visual;

import inetsoft.graph.GraphConstants;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.form.LabelForm;
import inetsoft.graph.guide.form.TagForm;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;

import java.awt.*;
import java.awt.geom.*;

/**
 * Visual object for line form.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class TagFormVO extends LabelFormVO {
   /**
    * Constructor.
    * @param form is the Label Form object contains label information.
    * @param label the label to display.
    * @param pos the position of the object.
    */
   public TagFormVO(TagForm form, Object label, Point2D pos) {
      super(form, label, pos);

      this.opos = (Point2D) pos.clone();
   }

   /**
    * Paint the visual object on the graphics.
    * @param g the graphics context to use for painting.
    */
   @Override
   public void paint(Graphics2D g) {
      super.paint(g);

      Rectangle2D lbox = getVLabel().getTransformedBounds().getBounds2D();
      double x, y;
      Point2D pos;

      if(isFixedPosition()) {
         pos = transformFixedPosition(opos);
      }
      else {
         pos = getScreenTransform().transform(opos, null);
      }

      // label at the right of the point
      if(lbox.getX() > pos.getX()) {
         x = lbox.getX();
         y = lbox.getY() + lbox.getHeight() / 2;
      }
      // label at the left of the point
      else if(lbox.getX() + lbox.getWidth() < pos.getX()) {
         x = lbox.getX() + lbox.getWidth();
         y = lbox.getY() + lbox.getHeight() / 2;
      }
      // label above the point
      else if(lbox.getY() > pos.getY()) {
         x = lbox.getX() + lbox.getWidth() / 2;
         y = lbox.getY();
      }
      // label below the point
      else if(lbox.getY() + lbox.getHeight() < pos.getY()) {
         x = lbox.getX() + lbox.getWidth() / 2;
         y = lbox.getY() + lbox.getHeight();
      }
      else {
         return;
      }

      g = (Graphics2D) g.create();

      if(pos.getX() != x && pos.getY() != y) {
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
      }

      int line = getForm().getLine();

      if(line == GraphConstants.NONE) {
         line = GraphConstants.THIN_LINE;
      }

      Color color = getColor();

      if(color == null) {
         color = getForm().getColor();
      }

      g.setColor(color != null ? color : GDefaults.DEFAULT_LINE_COLOR);
      g.setStroke(GTool.getStroke(line));
      g.draw(new Line2D.Double(pos, new Point2D.Double(x, y)));

      g.dispose();
   }

   @Override
   protected VLabel createLabel(Object label, LabelForm form) {
      VLabel vlabel = super.createLabel(label, form);
      ((FormLabel) vlabel).setRemovable(false);
      return vlabel;
   }

   private Point2D opos; // position of the tagged object
}
