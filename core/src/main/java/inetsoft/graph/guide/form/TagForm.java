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
package inetsoft.graph.guide.form;

import com.inetsoft.build.tern.TernClass;
import com.inetsoft.build.tern.TernConstructor;
import inetsoft.graph.GraphConstants;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.visual.LabelFormVO;
import inetsoft.graph.visual.TagFormVO;

import java.awt.*;
import java.awt.geom.Point2D;

/**
 * This is a tag form guide that allows a text to be tagged to a visual object.
 * The text is moved to avoid overlapping with the other elements. A line is
 * drawn from the text pointing to the tagged visual object.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=TagForm")
public class TagForm extends LabelForm {
   /**
    * Default constructor.
    */
   public TagForm() {
   }

   /**
    * Create a label at position specified in logic space (scaled tuple values).
    * @param label label content.
    * @param tuple label point specified as a tuple of values.
    */
   public TagForm(Object label, double[] tuple) {
      super(label, tuple);
   }

   /**
    * Create a label at position specified as data values (to be scaled).
    * @param label label content.
    * @param values label point specified as an unscaled tuple of values.
    */
   @TernConstructor
   public TagForm(Object label, Object[] values) {
      super(label, values);
   }

   /**
    * Create a label at a fixed position.
    * @param pos the position in the graph. The position is in points using the
    * math coordinate (Y grows upwards).
    */
   public TagForm(Object label, Point2D pos) {
      super(label, pos);
   }

   {
      setCollisionModifier(VLabel.MOVE_FREE);
      setColor(Color.GRAY);
      setLine(GraphConstants.THIN_THIN_LINE);
      setInsets(new Insets(0, 2, 0, 2));
   }

   /**
    * Create the visual object for this form.
    */
   @Override
   protected LabelFormVO createFormVO(Object label, Point2D labelpos) {
      return new TagFormVO(this, label, labelpos);
   }

   private static final long serialVersionUID = 1L;
}
