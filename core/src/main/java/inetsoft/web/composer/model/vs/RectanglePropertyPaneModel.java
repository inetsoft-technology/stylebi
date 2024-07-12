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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RectanglePropertyPaneModel {
   public LinePropPaneModel getLinePropPaneModel() {
      if(linePropPaneModel == null) {
         linePropPaneModel = new LinePropPaneModel();
      }

      return linePropPaneModel;
   }

   public void setLinePropPaneModel(
      LinePropPaneModel linePropPaneModel)
   {
      this.linePropPaneModel = linePropPaneModel;
   }

   public FillPropPaneModel getFillPropPaneModel() {
      if(fillPropPaneModel == null) {
         fillPropPaneModel = new FillPropPaneModel();
      }

      return fillPropPaneModel;
   }

   public void setFillPropPaneModel(
      FillPropPaneModel fillPropPaneModel)
   {
      this.fillPropPaneModel = fillPropPaneModel;
   }

   public int getRadius() {
      return radius;
   }

   public void setRadius(int radius) {
      this.radius = radius;
   }

   @Override
   public String toString() {
      return "RectanglePropertyPaneModel{" +
         "radius=" + radius +
         ", linePropPaneModel=" + linePropPaneModel +
         ", fillPropPaneModel=" + fillPropPaneModel +
         '}';
   }

   private int radius;
   private LinePropPaneModel linePropPaneModel;
   private FillPropPaneModel fillPropPaneModel;
}
