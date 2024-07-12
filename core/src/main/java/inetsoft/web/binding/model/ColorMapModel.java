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
package inetsoft.web.binding.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Data transfer object that represents the {@link ColorMap}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ColorMapModel {
   public ColorMapModel() {
   }

   public ColorMapModel(String option, String color) {
      this.option = option;
      this.color = color;
   }

   public String getOption() {
      return this.option;
   }

   public void setOption(String option) {
      this.option = option;
   }

   public String getColor() {
      return this.color;
   }

   public void setColor(String color) {
      this.color = color;
   }

   @Override
   public String toString() {
      return "ColorMapModel[" + option + "," + color + "]";
   }

   private String option;
   private String color;
}
