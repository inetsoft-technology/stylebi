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
package inetsoft.web.binding.model.graph.aesthetic;

import inetsoft.graph.aesthetic.HSLColorFrame;
import inetsoft.uql.viewsheet.graph.aesthetic.HSLColorFrameWrapper;
import inetsoft.util.Tool;

public abstract class HSLColorModel extends ColorFrameModel {
   public HSLColorModel() {
   }

   public HSLColorModel(HSLColorFrameWrapper wrapper) {
      super(wrapper);
      HSLColorFrame frame = (HSLColorFrame) wrapper.getVisualFrame();
      setColor(Tool.toString(frame.getColor()));
      setCssColor(Tool.toString(frame.getCssColor()));
      setDefaultColor(Tool.toString(frame.getDefaultColor()));
   }

   public void setColor(String color) {
      this.color = color;
   }

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

   private String color;
   private String cssColor;
   private String defaultColor;
}