/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.model;

import com.fasterxml.jackson.annotation.*;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "fieldType", defaultImpl = SimpleFieldInfo.class)
@JsonSubTypes({
    @JsonSubTypes.Type(value = SimpleFieldInfo.class, name = "simple"),
    @JsonSubTypes.Type(value = DimensionFieldInfo.class, name = "dimension"),
    @JsonSubTypes.Type(value = MeasureFieldInfo.class, name = "measure")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class SimpleFieldInfo extends FieldInfo {
   public Integer getOrder() {
      return order;
   }

   public void setOrder(Integer order) {
      this.order = order;
   }

   public Boolean getVisible() {
      return visible;
   }

   public void setVisible(Boolean visible) {
      this.visible = visible;
   }

   private Integer order;
   // Column visibility for table `details`; null = default (visible). Ignored by chart/crosstab.
   private Boolean visible;
}
