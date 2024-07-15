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

import inetsoft.graph.aesthetic.StaticColorFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;
import java.util.Objects;

/**
 * Static color frame.
 *
 * @version 10.0
 * @author InetSoft Technology Corp.
 */
public class StaticColorFrameWrapper extends ColorFrameWrapper {
   /**
    * Create the corresponding frame.
    */
   @Override
   protected VisualFrame createVisualFrame() {
      return new StaticColorFrame();
   }

   /**
    * Get the static color value of the static color frame.
    * @return the static color value of static color frame.
    */
   public Color getColor() {
      StaticColorFrame frame = (StaticColorFrame) getVisualFrame();
      return frame.getColor();
   }

   /**
    * Set the static color value of the static color frame.
    * @param color the static color value of static color frame.
    */
   public void setDefaultColor(Color color) {
      StaticColorFrame frame = (StaticColorFrame) getVisualFrame();
      // this is problematic. it causes color to be marked as changed on initialization
      // of aggregate, and when color is reset on ui. (61597)
      //setChanged(isChanged() || !Objects.equals(frame.getColor(), color));
      frame.setDefaultColor(color);
   }

   public Color getUserColor() {
      StaticColorFrame frame = (StaticColorFrame) getVisualFrame();
      return frame.getUserColor();
   }

   public Color getDefaultColor() {
      StaticColorFrame frame = (StaticColorFrame) getVisualFrame();
      return frame.getDefaultColor();
   }

   public void setUserColor(Color color) {
      StaticColorFrame frame = (StaticColorFrame) getVisualFrame();
      setChanged(isChanged() || !Objects.equals(frame.getColor(), color));
      frame.setUserColor(color);
   }

   public void setDcRuntimeColor(Color dcRuntimeColor) {
      if(dcRuntimeColor == null) {
         if(originalColor != null) {
            setUserColor(originalColor);
         }

         this.originalColor = null;
      }
      else {
         this.originalColor = ((StaticColorFrame) getVisualFrame()).getUserColor();
         setUserColor(dcRuntimeColor);
      }
   }

   /**
    * Write the content part(child node) of XML segment.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      StaticColorFrame frame = (StaticColorFrame) getVisualFrame();

      if(frame.getDefaultColor() != null) {
         writer.print("color=\"" + frame.getDefaultColor().getRGB() + "\"");
      }

      if(frame.getCssColor() != null) {
         writer.print(" cssColor=\"" + frame.getCssColor().getRGB() + "\"");
      }

      if(frame.getUserColor() != null) {
         writer.print(" userColor=\"" + frame.getUserColor().getRGB() + "\"");
      }
   }

   /**
    * Write the content part(child node) of XML segment.
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);

      String val;
      StaticColorFrame frame = (StaticColorFrame) getVisualFrame();

      if((val = Tool.getAttribute(tag, "color")) != null) {
         frame.setDefaultColor(new Color(Integer.parseInt(val)));
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

   private Color originalColor;
}
