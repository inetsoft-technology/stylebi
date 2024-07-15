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
package inetsoft.web.portal.model.database.cube.xmla;

import java.util.List;

public class CubeModel {
   public CubeModel() {
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public List<CubeDimensionModel> getDimensions() {
      return dimensions;
   }

   public void setDimensions(List<CubeDimensionModel> dimensions) {
      this.dimensions = dimensions;
   }

   public List<CubeMeasureModel> getMeasures() {
      return measures;
   }

   public void setMeasures(List<CubeMeasureModel> measures) {
      this.measures = measures;
   }

   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   public String getCaption() {
      return caption;
   }

   public void setCaption(String caption) {
      this.caption = caption;
   }

   private String name;
   private List<CubeDimensionModel> dimensions;
   private List<CubeMeasureModel> measures;
   private String type;
   private String caption;
}
