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

import inetsoft.graph.aesthetic.StaticSizeFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.uql.CompositeValue;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * Static size frame defines a static size for visual objects.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class StaticSizeFrameWrapper extends SizeFrameWrapper {
   /**
    * Create the corresponding frame.
    */
   @Override
   protected VisualFrame createVisualFrame() {
      return new StaticSizeFrame();
   }

   /**
    * Get the size of the static size frame.
    * @return the size of static size frame.
    */
   public double getSize() {
      StaticSizeFrame frame = (StaticSizeFrame) getVisualFrame();

      return frame.getSize();
   }

   /**
    * Set the size of the static size frame.
    * @param size the size of static size frame.
    */
   public void setSize(double size) {
      setSize(size, CompositeValue.Type.USER);
   }

   /**
    * Set the size of the static size frame.
    * @param size the size of static size frame.
    * @param type the type of value to set: DEFAULT, CSS, USER
    */
   public void setSize(double size, CompositeValue.Type type) {
      StaticSizeFrame frame = (StaticSizeFrame) getVisualFrame();

      if(type == CompositeValue.Type.USER) {
         setChanged(isChanged() || frame.getSize() != size);
      }

      frame.setSize(size, type);
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      StaticSizeFrame frame = (StaticSizeFrame) getVisualFrame();
      writer.print(" size=\"" + frame.getSizeCompositeValue() + "\"");
   }

   /**
    * Parse attributes.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
      String val;
      StaticSizeFrame frame = (StaticSizeFrame) getVisualFrame();

      if((val = Tool.getAttribute(tag, "size")) != null) {
         // Bug #55730, if static frame is not changed then read the value as a default
         frame.getSizeCompositeValue().parse(val, !isChanged());
      }
   }
}
