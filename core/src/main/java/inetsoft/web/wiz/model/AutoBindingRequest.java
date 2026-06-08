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

import java.util.List;

/**
 * Request body for {@code POST /api/wiz/viewsheet/autoBinding}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AutoBindingRequest {
   public String getWorksheetId() {
      return worksheetId;
   }

   public void setWorksheetId(String worksheetId) {
      this.worksheetId = worksheetId;
   }

   public String getVisualizationType() {
      return visualizationType;
   }

   public void setVisualizationType(String visualizationType) {
      this.visualizationType = visualizationType;
   }

   public List<SimpleFieldInfo> getFieldConfigs() {
      return fieldConfigs;
   }

   public void setFieldConfigs(List<SimpleFieldInfo> fieldConfigs) {
      this.fieldConfigs = fieldConfigs;
   }

   public List<ExplicitBinding> getExplicitBindings() {
      return explicitBindings;
   }

   public void setExplicitBindings(List<ExplicitBinding> explicitBindings) {
      this.explicitBindings = explicitBindings;
   }

   public String getIntentCategory() {
      return intentCategory;
   }

   public void setIntentCategory(String intentCategory) {
      this.intentCategory = intentCategory;
   }

   /**
    * Worksheet global path, same as VisualizationConfig.data.source.
    */
   private String worksheetId;
   /**
    * Expected visualization type string, e.g. "bar", "table", "crosstab", "gauge".
    */
   private String visualizationType;
   /**
    * Field configuration preferences (formerly "fieldConfigs").
    * Carries optional per-field semantic annotations produced by the LLM
    * (aggregateFormula, dateGroupLevel, ranking, etc.).  These are applied on top of
    * the columns selected by {@link #visualizationUsedFields}; they do NOT drive
    * which columns are bound — that is controlled exclusively by {@code visualizationUsedFields}.
    */
   private List<SimpleFieldInfo> fieldConfigs;

   /**
    * Columns to bind, derived from the primary worksheet table assembly and reconciled with
    * {@code selectFieldMappings}.  When non-empty, autoBinding filters the worksheet's
    * {@code columnSelection} to exactly these columns (matched by {@code alias ?? name}).
    * Takes precedence over {@link #fieldConfigs} for column selection.
    */
   private List<WorksheetColumnInfo> visualizationUsedFields;
   /**
    * Optional explicit slot assignments; may be null or empty.
    */
   private List<ExplicitBinding> explicitBindings;
   /**
    * Visualization intent category inferred by the LLM.
    * One of: "comparison", "trend", "distribution", "proportion",
    * "relationship", "ranking", "geospatial", "other".
    */
   private String intentCategory;

   public String getAutoBindingRuntimeId() {
      return autoBindingRuntimeId;
   }

   public void setAutoBindingRuntimeId(String autoBindingRuntimeId) {
      this.autoBindingRuntimeId = autoBindingRuntimeId;
   }

   public String getWizRuntimeId() {
      return wizRuntimeId;
   }

   public void setWizRuntimeId(String wizRuntimeId) {
      this.wizRuntimeId = wizRuntimeId;
   }

   public String getViewsheetIdentifier() {
      return viewsheetIdentifier;
   }

   public void setViewsheetIdentifier(String viewsheetIdentifier) {
      this.viewsheetIdentifier = viewsheetIdentifier;
   }

   public List<WorksheetColumnInfo> getVisualizationUsedFields() {
      return visualizationUsedFields;
   }

   public void setVisualizationUsedFields(List<WorksheetColumnInfo> visualizationUsedFields) {
      this.visualizationUsedFields = visualizationUsedFields;
   }

   /**
    * Recommendation-computation RVS ID. Null on first call; returned by the server
    * and passed back on subsequent calls to reuse the same RVS.
    */
   private String autoBindingRuntimeId;

   /**
    * Output-viewsheet RVS ID. Null on first call; returned by the server and passed back
    * on subsequent calls so the primary assembly is updated in place.
    * Mirrors the runtimeId semantics of CreateVisualizationModel.
    */
   private String wizRuntimeId;

   /**
    * Persisted viewsheet identifier returned from the previous call.
    * Null on first call; must be passed back on subsequent calls so persistViewsheet
    * overwrites the existing entry rather than creating a new one.
    */
   private String viewsheetIdentifier;
}
