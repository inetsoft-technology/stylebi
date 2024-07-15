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
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.StaticShapeFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * Static shape frame defines a static shape for visual objects.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class StaticShapeFrameWrapper extends ShapeFrameWrapper {
   /**
    * Create the corresponding frame.
    */
   @Override
   protected VisualFrame createVisualFrame() {
      return new StaticShapeFrame();
   }

   /**
    * Get the shape option value of the static shape frame.
    */
   public String getShape() {
      StaticShapeFrame frame = (StaticShapeFrame) getVisualFrame();

      return getID(frame.getShape());
   }

   /**
    * Set the static option value of static shape frame.
    */
   public void setShape(String shape) {
      StaticShapeFrame frame = (StaticShapeFrame) getVisualFrame();

      setChanged(isChanged() || !Tool.equals(getID(frame.getShape()), shape));
      frame.setShape(getGShape(shape));
   }

   /**
    * Write attributes.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      StaticShapeFrame frame = (StaticShapeFrame) getVisualFrame();
      String shapeID = getID(frame.getShape());

      if(shapeID != null) {
         writer.print("shape=\"" + shapeID + "\"");
      }
   }

   /**
    * Parse attributes.
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
      String val;
      StaticShapeFrame frame = (StaticShapeFrame) getVisualFrame();

      if((val = Tool.getAttribute(tag, "shape")) != null) {
         frame.setShape(getGShape(val));
      }
   }
}
