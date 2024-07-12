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
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * Static line frame defines a static line style for visual objects.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class StaticLineFrameWrapper extends LineFrameWrapper {
   /**
    * Create the corresponding frame.
    */
   @Override
   protected VisualFrame createVisualFrame() {
      return new StaticLineFrame();
   }

   /**
    * Set the line of the line frame.
    * @param line the line of line frame.
    */
   public void setLine(int line) {
      StaticLineFrame frame = (StaticLineFrame) getVisualFrame();

      setChanged(isChanged() || frame.getLine().getStyle() != line);
      frame.setLine(new GLine(line));
   }

   /**
    * Get the line of the line frame.
    * @return the line of line frame.
    */
   public int getLine() {
      StaticLineFrame frame = (StaticLineFrame) getVisualFrame();

      return frame.getLine().getStyle();
   }

   /**
    * Write attributes.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      StaticLineFrame frame = (StaticLineFrame) getVisualFrame();

      writer.print("lineStyle=\"" + frame.getLine().getStyle() + "\"");
   }

   /**
    * Parse attributes.
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
      String val;
      StaticLineFrame frame = (StaticLineFrame) getVisualFrame();

      if((val = Tool.getAttribute(tag, "lineStyle")) != null) {
         frame.setLine(new GLine(Integer.parseInt(val)));
      }
   }
}
