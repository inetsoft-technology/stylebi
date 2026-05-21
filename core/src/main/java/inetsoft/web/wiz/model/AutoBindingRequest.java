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

   public boolean isVisualizationTypeIsExplicit() {
      return visualizationTypeIsExplicit;
   }

   public void setVisualizationTypeIsExplicit(boolean visualizationTypeIsExplicit) {
      this.visualizationTypeIsExplicit = visualizationTypeIsExplicit;
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

   /** Worksheet global path, same as VisualizationConfig.data.source. */
   private String worksheetId;
   /** Expected visualization type string, e.g. "bar", "table", "crosstab", "gauge". */
   private String visualizationType;
   /** True when the user explicitly named the visualization type; false when inferred. */
   private boolean visualizationTypeIsExplicit;
   /**
    * Per-field configurations from the LLM.
    * Uses the existing {@link SimpleFieldInfo} polymorphic hierarchy:
    * {@code fieldType:"dimension"} → {@link DimensionFieldInfo},
    * {@code fieldType:"measure"} → {@link MeasureFieldInfo}.
    */
   private List<SimpleFieldInfo> fieldConfigs;
   /** Optional explicit slot assignments; may be null or empty. */
   private List<ExplicitBinding> explicitBindings;
}
