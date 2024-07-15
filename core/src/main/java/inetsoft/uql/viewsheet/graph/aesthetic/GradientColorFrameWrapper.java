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
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.GradientColorFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;

/**
 * This class create color frame that gradient colors
 * between the given two colors.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class GradientColorFrameWrapper extends RGBCubeColorFrameWrapper {
   /**
    * Create the corresponding frame.
    */
   @Override
   protected VisualFrame createVisualFrame() {
      return new GradientColorFrame();
   }

   /**
    * Get the from color value.
    * @return the specified color value.
    */
   public Color getFromColor() {
      GradientColorFrame frame = (GradientColorFrame) getVisualFrame();
      return frame.getFromColor();
   }

   /**
    * Set the from color value.
    * @param fromColor the specified color value.
    */
   public void setDefaultFromColor(Color fromColor) {
      GradientColorFrame frame = (GradientColorFrame) getVisualFrame();
      setChanged(isChanged() || frame.getFromColor() != fromColor);
      frame.setDefaultFromColor(fromColor);
   }

   /**
    * Get the end color value.
    * @return the end color value.
    */
   public Color getToColor() {
      GradientColorFrame frame = (GradientColorFrame) getVisualFrame();
      return frame.getToColor();
   }

   /**
    * Set the end color value.
    * @param toColor the end color value.
    */
   public void setDefaultToColor(Color toColor) {
      GradientColorFrame frame = (GradientColorFrame) getVisualFrame();
      setChanged(isChanged() || frame.getToColor() != toColor);
      frame.setDefaultToColor(toColor);
   }

   /**
    * Set the from user color value.
    * @param fromColor the end color value.
    */
   public void setUserFromColor(Color fromColor) {
      GradientColorFrame frame = (GradientColorFrame) getVisualFrame();
      setChanged(true);
      frame.setUserFromColor(fromColor);
   }

   /**
    * Set the end user color value.
    * @param toColor the end color value.
    */
   public void setUserToColor(Color toColor) {
      GradientColorFrame frame = (GradientColorFrame) getVisualFrame();
      setChanged(true);
      frame.setUserToColor(toColor);
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      GradientColorFrame frame = (GradientColorFrame) getVisualFrame();

      writer.print(" fromColor=\"" + frame.getDefaultFromColor().getRGB()  + "\"");
      writer.print(" toColor=\"" +  frame.getDefaultToColor().getRGB() + "\"");

      if(frame.getCssFromColor() != null) {
         writer.print(" cssFromColor=\"" + frame.getCssFromColor().getRGB()  +
            "\"");
      }

      if(frame.getCssToColor() != null) {
         writer.print(" cssToColor=\"" + frame.getCssToColor().getRGB()  +
            "\"");
      }

      if(frame.getUserFromColor() != null) {
         writer.print(" userFromColor=\"" + frame.getUserFromColor().getRGB()  +
            "\"");
      }

      if(frame.getUserToColor() != null) {
         writer.print(" userToColor=\"" + frame.getUserToColor().getRGB()  +
            "\"");
      }
   }

   /**
    * Parse attributes.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
      String val;
      GradientColorFrame frame = (GradientColorFrame) getVisualFrame();

      if((val = Tool.getAttribute(tag, "fromColor")) != null) {
         frame.setDefaultFromColor(new Color(Integer.parseInt(val)));
      }

      if((val = Tool.getAttribute(tag, "toColor")) != null) {
         frame.setDefaultToColor(new Color(Integer.parseInt(val)));
      }

      val = Tool.getAttribute(tag, "cssFromColor");
      Color color = null;

      if(val != null) {
         color = new Color(Integer.parseInt(val));
      }

      frame.setCssFromColor(color);

      val = Tool.getAttribute(tag, "cssToColor");
      color = null;

      if(val != null) {
         color = new Color(Integer.parseInt(val));
      }

      frame.setCssToColor(color);

      val = Tool.getAttribute(tag, "userFromColor");
      color = null;

      if(val != null) {
         color = new Color(Integer.parseInt(val));
      }

      frame.setUserFromColor(color);

      val = Tool.getAttribute(tag, "userToColor");
      color = null;

      if(val != null) {
         color = new Color(Integer.parseInt(val));
      }

      frame.setUserToColor(color);
   }
}
