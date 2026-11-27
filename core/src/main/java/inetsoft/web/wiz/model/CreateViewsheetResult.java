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

import java.util.ArrayList;
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

   public List<ApplyWarning> getWarnings() {
      return warnings;
   }

   public void setWarnings(List<ApplyWarning> warnings) {
      this.warnings = warnings;
   }

   /**
    * Append one warning, creating the list on first use. Callers collect these as they walk a request's
    * options, so the alternative is the same null check repeated at every skip site — which is exactly
    * where it would eventually be forgotten and the skip go back to being silent.
    */
   public void addWarning(ApplyWarning warning) {
      if(warning == null) {
         return;
      }

      if(warnings == null) {
         warnings = new ArrayList<>();
      }

      warnings.add(warning);
   }

   /** Convenience for the common call shape; see {@link ApplyWarning} for what belongs here. */
   public void addWarning(String option, String reason) {
      addWarning(new ApplyWarning(option, reason));
   }

   public String getTitle() {
      return title;
   }

   public void setTitle(String title) {
      this.title = title;
   }

   public AssemblyState getAssemblyState() {
      return assemblyState;
   }

   public void setAssemblyState(AssemblyState assemblyState) {
      this.assemblyState = assemblyState;
   }

   public Boolean getAssemblyStateUnavailable() {
      return assemblyStateUnavailable;
   }

   public void setAssemblyStateUnavailable(Boolean assemblyStateUnavailable) {
      this.assemblyStateUnavailable = assemblyStateUnavailable;
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
   /**
    * The copy-then-apply fallback channel, and nothing else: set only when a {@code copy=true} request
    * could not duplicate the chart and the change was applied to the original in place. It is the one
    * thing that tells the caller its original chart is no longer untouched, so anything else written
    * here is read as that — see {@code warnings} for options that simply did not take effect.
    */
   private String note;
   private String title;
   /**
    * Options this request asked for that did not take effect while the rest of it applied. Absent
    * (rather than empty) when everything applied, so a caller can test the field itself.
    */
   private List<ApplyWarning> warnings;
   /**
    * What the visualization assembly itself holds after this endpoint's last change (source, its
    * own conditions, date comparison, highlights, plain-table columns). Null when it could not be
    * built; see {@code WizVsService#fillAssemblyState}.
    */
   private AssemblyState assemblyState;
   /**
    * TRUE only when this endpoint tried to fill {@code assemblyState} and the read failed, so
    * {@code assemblyState} is absent because it is unknown, not because the endpoint never fills it.
    * Absent otherwise (including on a successful fill). The wiz side treats TRUE as "assemblyState is
    * null for this response" and drops its previous state instead of keeping a stale one.
    */
   private Boolean assemblyStateUnavailable;

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

   /**
    * The assembly's own state, read from the assembly model rather than a rendered lens:
    * {@code conditions} is its own pre-condition list (MetadataApiService leaf format, phase
    * "chart"), {@code dateComparison} the normalized DateComparisonService read (only when a
    * comparison is in effect), {@code highlights} every highlight group, {@code tableColumns} the
    * visible columns of a plain table (null for every other type).
    */
   @JsonInclude(JsonInclude.Include.NON_NULL)
   public static class AssemblyState {
      public AssemblySource getSource() {
         return source;
      }

      public void setSource(AssemblySource source) {
         this.source = source;
      }

      public List<WorksheetStructure.ConditionLeaf> getConditions() {
         return conditions;
      }

      public void setConditions(List<WorksheetStructure.ConditionLeaf> conditions) {
         this.conditions = conditions;
      }

      public Map<String, Object> getDateComparison() {
         return dateComparison;
      }

      public void setDateComparison(Map<String, Object> dateComparison) {
         this.dateComparison = dateComparison;
      }

      public List<AssemblyHighlight> getHighlights() {
         return highlights;
      }

      public void setHighlights(List<AssemblyHighlight> highlights) {
         this.highlights = highlights;
      }

      public TableColumns getTableColumns() {
         return tableColumns;
      }

      public void setTableColumns(TableColumns tableColumns) {
         this.tableColumns = tableColumns;
      }

      private AssemblySource source;
      private List<WorksheetStructure.ConditionLeaf> conditions = new ArrayList<>();
      private Map<String, Object> dateComparison;
      private List<AssemblyHighlight> highlights = new ArrayList<>();
      private TableColumns tableColumns;
   }

   /**
    * Where the assembly reads its data: {@code kind} is "worksheet", "model" or "physical" (from the
    * viewsheet's base entry), {@code worksheet} the base worksheet's identifier (worksheet kind only),
    * {@code table} the bound table with the outer-table suffix stripped.
    */
   @JsonInclude(JsonInclude.Include.NON_NULL)
   public record AssemblySource(String kind, String worksheet, String table) {
   }

   /**
    * One highlight: {@code target} is the field or cell column it is keyed to (null for a chart-level
    * or output group), {@code clauses} its conditions in reading order. The clauses are flattened
    * for display: nesting (junction levels) is not rendered, so they describe the highlight rather
    * than restate its exact grouping.
    */
   @JsonInclude(JsonInclude.Include.NON_NULL)
   public record AssemblyHighlight(String name, String target, List<String> clauses) {
   }

   /** The visible columns of a plain table, in display order. */
   @JsonInclude(JsonInclude.Include.NON_NULL)
   public record TableColumns(List<String> columns) {
   }
}
