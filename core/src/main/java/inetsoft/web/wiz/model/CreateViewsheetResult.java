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
 * Row data is capped at {@code WizVsService.MAX_ROWS} rows; when the underlying
 * dataset exceeds that limit {@code truncated} is set to {@code true}.
 *
 * <p>{@code binding} is a flattened view of the assembly's data binding: all dimensions
 * in one list and all measures in another.  It is null for assembly types that carry no
 * aggregate binding (e.g. plain Image), and must be non-null for aggregated types
 * (Chart, Crosstab, Gauge, Text).  Each entry reuses the existing {@link DimensionFieldInfo}
 * / {@link MeasureFieldInfo} model classes; the optional {@code fullName} field on each
 * entry is populated with the human-readable label from {@code VSDimensionRef.getFullName()}
 * or {@code VSAggregateRef.getFullName()}.
 *
 * <p>{@code truncated} is {@code true} when the row list was cut at the server-side row cap;
 * absent from the response when all rows are included.
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

   public Boolean getTruncated() {
      return truncated;
   }

   public void setTruncated(Boolean truncated) {
      this.truncated = truncated;
   }

   public Boolean getSampled() {
      return sampled;
   }

   public void setSampled(Boolean sampled) {
      this.sampled = sampled;
   }

   public Integer getSampleMaxRows() {
      return sampleMaxRows;
   }

   public void setSampleMaxRows(Integer sampleMaxRows) {
      this.sampleMaxRows = sampleMaxRows;
   }

   public String getRuntimeId() {
      return runtimeId;
   }

   public void setRuntimeId(String runtimeId) {
      this.runtimeId = runtimeId;
   }

   public String getAssemblyName() {
      return assemblyName;
   }

   public void setAssemblyName(String assemblyName) {
      this.assemblyName = assemblyName;
   }

   public String getViewsheetIdentifier() {
      return viewsheetIdentifier;
   }

   public void setViewsheetIdentifier(String viewsheetIdentifier) {
      this.viewsheetIdentifier = viewsheetIdentifier;
   }

   public Boolean getHasData() {
      return hasData;
   }

   public void setHasData(Boolean hasData) {
      this.hasData = hasData;
   }

   public String getAutoBindingRuntimeId() {
      return autoBindingRuntimeId;
   }

   public void setAutoBindingRuntimeId(String autoBindingRuntimeId) {
      this.autoBindingRuntimeId = autoBindingRuntimeId;
   }

   public String getNote() {
      return note;
   }

   public void setNote(String note) {
      this.note = note;
   }

   public String getTitle() {
      return title;
   }

   public void setTitle(String title) {
      this.title = title;
   }

   private List<String> headers;
   private List<Map<String, Object>> rows;
   private FlatBinding binding;
   private Boolean truncated;
   // #75456: true when the source worksheet had a finite design-mode sample cap in effect
   // (sampled-preview mode), so aggregates were computed over at most sampleMaxRows detail
   // rows and Sum/Count may be approximate. Null/absent in full-data mode (the default).
   private Boolean sampled;
   private Integer sampleMaxRows;
   private String runtimeId;
   private String assemblyName;
   private String viewsheetIdentifier;
   private Boolean hasData;
   /** Echoed back so the client can reuse the recommendation RVS on the next changeType call. */
   private String autoBindingRuntimeId;
   private String note;
   private String title;

   // -------------------------------------------------------------------------
   // Nested model
   // -------------------------------------------------------------------------

   /**
    * Flat representation of an assembly's data binding.
    * All dimension refs are collected into {@code dimensions} regardless of which
    * axis/role they occupy in the original binding; likewise for {@code measures}.
    * {@code slots} reports the RESOLVED placement (which slot each field landed in);
    * {@code notes} carries structured decisions (e.g. a readability guard overridden
    * because the user pinned the field).
    */
   @JsonInclude(JsonInclude.Include.NON_NULL)
   public static class FlatBinding {
      public FlatBinding(List<DimensionFieldInfo> dimensions, List<MeasureFieldInfo> measures,
                         Map<String, Object> slots)
      {
         this.dimensions = dimensions;
         this.measures = measures;
         this.slots = slots;
      }

      public List<DimensionFieldInfo> getDimensions() {
         return dimensions;
      }

      public List<MeasureFieldInfo> getMeasures() {
         return measures;
      }

      /** Resolved slot placement: list-valued for x/y/group, string-or-null for aesthetics. */
      public Map<String, Object> getSlots() {
         return slots;
      }

      public List<BindingNote> getNotes() {
         return notes;
      }

      public void setNotes(List<BindingNote> notes) {
         this.notes = notes;
      }

      private final List<DimensionFieldInfo> dimensions;
      private final List<MeasureFieldInfo> measures;
      private final Map<String, Object> slots;
      private List<BindingNote> notes;
   }

   /** A structured binding decision surfaced to the caller. */
   @JsonInclude(JsonInclude.Include.NON_NULL)
   public static class BindingNote {
      public BindingNote(String field, String role, boolean honored, String severity, String reason) {
         this.field = field;
         this.role = role;
         this.honored = honored;
         this.severity = severity;
         this.reason = reason;
      }

      public String getField() { return field; }
      public String getRole() { return role; }
      public boolean isHonored() { return honored; }
      public String getSeverity() { return severity; }
      public String getReason() { return reason; }

      private final String field;
      private final String role;
      private final boolean honored;
      private final String severity;
      private final String reason;
   }
}
