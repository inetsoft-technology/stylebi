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

import inetsoft.graph.aesthetic.HSLColorFrame;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;

/**
 * This class defines a color frame for continuous numeric values. This is the
 * base class for color frames that using the HSL scale.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class HSLColorFrameWrapper extends LinearColorFrameWrapper {
   /**
    * Get the color value of the color frame.
    * @return the color value of the color frame.
    */
   public Color getColor() {
      HSLColorFrame frame = (HSLColorFrame) getVisualFrame();

      return frame.getColor();
   }

   /**
    * Set the static color value of the static color frame.
    * @param color the static color value of static color frame.
    */
   public void setColor(Color color) {
      HSLColorFrame frame = (HSLColorFrame) getVisualFrame();
      setChanged(isChanged() || frame.getColor() != color);
      frame.setDefaultColor(color);
   }

   /**
    * Set the user color value
    * @param color static color value of static color frame.
    */
   public void setUserColor(Color color) {
      HSLColorFrame frame = (HSLColorFrame) getVisualFrame();
      setChanged(true);
      frame.setUserColor(color);
   }

   /**
    * Write attributes.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      HSLColorFrame frame = (HSLColorFrame) getVisualFrame();

      writer.print(" color=\"" +  frame.getDefaultColor().getRGB() + "\" ");

      if(frame.getCssColor() != null) {
         writer.print(" cssColor=\"" + frame.getCssColor().getRGB() + "\"");
      }

      if(frame.getUserColor() != null) {
         writer.print(" userColor=\"" + frame.getUserColor().getRGB() + "\"");
      }
   }

   /**
    * Parse attributes.
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
      String val;
      HSLColorFrame frame = (HSLColorFrame) getVisualFrame();

      if((val = Tool.getAttribute(tag, "color")) != null) {
         Color color = new Color(Integer.parseInt(val));
         frame.setDefaultColor(color);
      }

      val = Tool.getAttribute(tag, "cssColor");
      Color color = null;

      if(val != null) {
         color = new Color(Integer.parseInt(val));
      }

      frame.setCssColor(color);

      val = Tool.getAttribute(tag, "userColor");
      color = null;

      if(val != null) {
         color = new Color(Integer.parseInt(val));
      }

      frame.setUserColor(color);
   }
}
