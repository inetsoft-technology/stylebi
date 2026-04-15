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

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Response model for POST /viewsheet/create.
 *
 * <p>{@code headers} and {@code rows} carry the raw data produced by the assembly
 * (tabular for Table/Crosstab/Chart, single-value row for Gauge/Text).  Both are
 * null when the assembly type produces no extractable data or when data is unavailable.
 *
 * <p>{@code binding} is a flattened view of the assembly's data binding: all dimensions
 * in one list and all measures in another.  It is null for assembly types that carry no
 * aggregate binding (e.g. plain Image), and must be non-null for aggregated types
 * (Chart, Crosstab, Gauge, Text).  Each entry reuses the existing {@link DimensionFieldInfo}
 * / {@link MeasureFieldInfo} model classes; the optional {@code fullName} field on each
 * entry is populated with the human-readable label from {@code VSDimensionRef.getFullName()}
 * or {@code VSAggregateRef.getFullName()}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateViewsheetResult {
   public List<String> getHeaders() {
      return headers;
   }

   public void setHeaders(List<String> headers) {
      this.headers = headers;
   }

   public List<Map<String, Object>> getRows() {
      return rows;
   }

   public void setRows(List<Map<String, Object>> rows) {
      this.rows = rows;
   }

   public FlatBinding getBinding() {
      return binding;
   }

   public void setBinding(FlatBinding binding) {
      this.binding = binding;
   }

   private List<String> headers;
   private List<Map<String, Object>> rows;
   private FlatBinding binding;

   // -------------------------------------------------------------------------
   // Nested model
   // -------------------------------------------------------------------------

   /**
    * Flat representation of an assembly's data binding.
    * All dimension refs are collected into {@code dimensions} regardless of which
    * axis/role they occupy in the original binding; likewise for {@code measures}.
    */
   @JsonInclude(JsonInclude.Include.NON_NULL)
   public static class FlatBinding {
      public FlatBinding(List<DimensionFieldInfo> dimensions, List<MeasureFieldInfo> measures) {
         this.dimensions = dimensions;
         this.measures = measures;
      }

      public List<DimensionFieldInfo> getDimensions() {
         return dimensions;
      }

      public List<MeasureFieldInfo> getMeasures() {
         return measures;
      }

      private final List<DimensionFieldInfo> dimensions;
      private final List<MeasureFieldInfo> measures;
   }
}
