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
package inetsoft.web.graph.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;
import java.util.Objects;

// shared region meta information
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegionMeta {
   public RegionMeta() {
   }

   public RegionMeta(int meaIdx, int dimIdx, int colIdx, String areaType, boolean refLine,
                     boolean hasMeasure, String dataType, Integer axisFldIdx, Boolean valueText)
   {
      this.meaIdx = meaIdx;
      this.dimIdx = dimIdx;
      this.colIdx = colIdx;
      this.areaType = areaType;
      this.refLine = refLine ? refLine : null;
      this.hasMeasure = hasMeasure ? hasMeasure : null;
      this.dataType = dataType;
      this.axisFldIdx = axisFldIdx;
      this.valueText = valueText;
   }

   // The index in the string dictionary that has the measure name for this region
   public int getMeaIdx() {
      return meaIdx;
   }

   // The index in the string dictionary that has the dimension name for this region
   public int getDimIdx() {
      return dimIdx;
   }

   // For interactive areas, the col number in the underlying table data
   public int getColIdx() {
      return colIdx;
   }

   public String getAreaType() {
      return areaType;
   }

   // Check if this region is a VisualObjectArea and can support a reference line
   @Nullable
   public Boolean isRefLine() {
      return refLine;
   }

   // A boolean to determine if a region contains a measure field
   @Nullable
   public Boolean isHasMeasure() {
      return hasMeasure;
   }

   // The data type of the data contained in the region (if applicable)
   @Nullable
   public String getDataType() {
      return dataType;
   }

   // For axes, the index in the string dictionary that has the index of this axis field name
   @Nullable
   public Integer getAxisFldIdx() {
      return axisFldIdx;
   }

   @Nullable
   public Boolean isValueText() {
      return valueText;
   }

   @Override
   public int hashCode() {
      return meaIdx + dimIdx + colIdx + (areaType != null ? areaType.hashCode() : 0) +
         (refLine != null && refLine ? 1 : 0) + (hasMeasure != null && hasMeasure ? 1 : 0) +
         (axisFldIdx != null ? axisFldIdx : 0) + (valueText != null ? 1 : 0);
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof RegionMeta)) {
         return false;
      }

      RegionMeta meta = (RegionMeta) obj;

      return meaIdx == meta.meaIdx && dimIdx == meta.dimIdx && colIdx == meta.colIdx &&
         Objects.equals(areaType, meta.areaType) && Objects.equals(refLine, meta.refLine) &&
         Objects.equals(hasMeasure, meta.hasMeasure) && Objects.equals(dataType, meta.dataType) &&
         Objects.equals(axisFldIdx, meta.axisFldIdx) && Objects.equals(valueText, meta.valueText);
   }

   private int meaIdx;
   private int dimIdx;
   private int colIdx;
   private String areaType;
   private Boolean refLine;
   private Boolean hasMeasure;
   private String dataType;
   private Integer axisFldIdx;
   private Boolean valueText;
}
