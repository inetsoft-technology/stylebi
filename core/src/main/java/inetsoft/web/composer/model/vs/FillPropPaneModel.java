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
import inetsoft.uql.viewsheet.GradientColor;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FillPropPaneModel {
   public String getColor() {
      return color;
   }

   public void setColor(String color) {
      this.color = color;
   }

   public String getColorValue() {
      return colorValue;
   }

   public void setColorValue(String colorValue) {
      this.colorValue = colorValue;
   }

   public int getAlpha() {
      return alpha;
   }

   public void setAlpha(int alpha) {
      this.alpha = alpha;
   }

   public GradientColor getGradientColor() {
      return this.gradientColor;
   }

   public void setGradientColor(GradientColor gradientColor) {
      this.gradientColor = gradientColor;
   }

   @Override
   public String toString() {
      return "FillPropPaneModel{" +
         "color='" + color + '\'' +
         ", alpha=" + alpha +
         ", colorValue=" + colorValue +
         '}';
   }

   private String color;
   private String colorValue;
   private int alpha;
   private GradientColor gradientColor;
}
