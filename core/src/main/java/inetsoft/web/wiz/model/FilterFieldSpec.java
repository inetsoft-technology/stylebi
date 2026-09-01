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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

/**
 * One field to attach as an interactive filter control, as sent by
 * {@code POST /api/wiz/viewsheet/filters}. {@code controlType} arrives already resolved by
 * wiz-services (selection_list | time_slider | calendar) — this endpoint never infers a control
 * type from a data type itself.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FilterFieldSpec {
   @NotBlank
   private String field;
   @NotBlank
   private String controlType;
   private String label;
   /**
    * The assembly name wiz-services already tracks for this field (from an earlier
    * add_visualization_filters call), if any. When present and still a live filter-control
    * assembly on the viewsheet, {@code addFilters} UPDATES it in place (same assemblyName,
    * re-titled/re-bound/re-positioned) instead of creating a duplicate — the upsert-by-field
    * half of decision 1. Absent (or stale/no-longer-present) falls back to creating a new control.
    */
   private String existingAssemblyName;

   public String getField() {
      return field;
   }

   public void setField(String field) {
      this.field = field;
   }

   public String getControlType() {
      return controlType;
   }

   public void setControlType(String controlType) {
      this.controlType = controlType;
   }

   public String getLabel() {
      return label;
   }

   public void setLabel(String label) {
      this.label = label;
   }

   public String getExistingAssemblyName() {
      return existingAssemblyName;
   }

   public void setExistingAssemblyName(String existingAssemblyName) {
      this.existingAssemblyName = existingAssemblyName;
   }
}
