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
package inetsoft.web.binding.model.graph.aesthetic;

import inetsoft.graph.aesthetic.StaticColorFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticColorFrameWrapper;
import inetsoft.util.Tool;

public class StaticColorModel extends ColorFrameModel {
   public StaticColorModel() {
   }

   public StaticColorModel(StaticColorFrameWrapper wrapper) {
      super(wrapper);
      StaticColorFrame frame = (StaticColorFrame) wrapper.getVisualFrame();
      setColor(Tool.toString(wrapper.getColor()));
      setCssColor(Tool.toString(frame.getCssColor()));
      setDefaultColor(Tool.toString(frame.getDefaultColor()));
   }

   /**
    * Set the current using color.
    */
   public void setColor(String color) {
      this.color = color;
   }

   /**
    * Set the current using color.
    */
   public String getColor() {
      return color;
   }

   /**
    * Set the original color to support reset color.
    */
   public void setDefaultColor(String color) {
      this.defaultColor = color;
   }

   /**
    * Set the original color to support reset color.
    */
   public String getDefaultColor() {
      return defaultColor;
   }

    /**
    * Set the original color to support reset color.
    */
   public void setCssColor(String color) {
      this.cssColor = color;
   }

   /**
    * Set the original color to support reset color.
    */
   public String getCssColor() {
      return cssColor;
   }

   @Override
   public VisualFrame createVisualFrame() {
      return new StaticColorFrame();
   }

   private String color = null;
   private String cssColor = null;
   private String defaultColor = null;
}
