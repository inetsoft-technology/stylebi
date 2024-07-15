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

import inetsoft.graph.aesthetic.SizeFrame;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * Static size frame defines a static size for visual objects.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public abstract class SizeFrameWrapper extends VisualFrameWrapper {
   /**
    * Set the largest value of the range [0..1], smallest <= largest.
    */
   public void setLargest(double val) {
      SizeFrame frame = (SizeFrame) getVisualFrame();

      setChanged(isChanged() || frame.getLargest() != val);
      frame.setLargest(val);
   }

   /**
    * Get the largest value of the range.
    */
   public double getLargest() {
      SizeFrame frame = (SizeFrame) getVisualFrame();

      return frame.getLargest();
   }

   /**
    * Set the smallest value of the range [0..1], smallest <= largest.
    */
   public void setSmallest(double val) {
      SizeFrame frame = (SizeFrame) getVisualFrame();

      setChanged(isChanged() || frame.getSmallest() != val);
      frame.setSmallest(val);
   }

   /**
    * Get the smallest value of the range.
    */
   public double getSmallest() {
      SizeFrame frame = (SizeFrame) getVisualFrame();
      return frame.getSmallest();
   }

   public void setMax(double max) {
      SizeFrame frame = (SizeFrame) getVisualFrame();
      frame.setMax(max);
   }

   public double getMax() {
      SizeFrame frame = (SizeFrame) getVisualFrame();
      return frame.getMax();
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      SizeFrame frame = (SizeFrame) getVisualFrame();

      writer.print(" largestSize=\"" + frame.getLargest() + "\"");
      writer.print(" smallestSize=\"" + frame.getSmallest() + "\"");
   }

   /**
    * Parse attributes.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
      String val;
      SizeFrame frame = (SizeFrame) getVisualFrame();

      if((val = Tool.getAttribute(tag, "largestSize")) != null) {
         frame.setLargest(Double.parseDouble(val));
      }

      if((val = Tool.getAttribute(tag, "smallestSize")) != null) {
         frame.setSmallest(Double.parseDouble(val));
      }
   }
}
