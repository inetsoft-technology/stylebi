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
import inetsoft.graph.Visualizable;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.visual.FormVO;

import java.awt.*;
import java.util.Objects;

/**
 * This is a generic fixed position/size shape.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=DefaultForm")
public class DefaultForm extends GeomForm {
   /**
    * Default constructor.
    */
   public DefaultForm() {
   }

   /**
    * Create a form to paint a fixed shape.
    */
   @TernConstructor
   public DefaultForm(Shape shape) {
      this.shape = shape;
   }

   /**
    * Set the fixed position/size shape.
    */
   @TernMethod
   public void setShape(Shape shape) {
      this.shape = shape;
   }

   /**
    * Get the fixed position/size shape.
    */
   @TernMethod
   public Shape getShape() {
      return shape;
   }

   /**
    * Create a visual object to visualize this element.
    * @param coord the coordinate the visual object is plotted on.
    * @return the new visual object.
    */
   @Override
   public Visualizable createVisual(Coordinate coord) {
      FormVO form = new FormVO(this);
      form.setShape(shape);
      form.setZIndex(getZIndex());

      return form;
   }

   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      DefaultForm form = (DefaultForm) obj;
      return Objects.equals(shape, form.shape);
   }

   private Shape shape;
   private static final long serialVersionUID = 1L;
}
