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
 * Deserialized form of the TypeScript {@code VisualizationHints} object emitted by
 * {@code visualizationHintsExtractionNode}.  Only the fields consumed by the
 * autoBinding service are mapped; all other LLM-generated fields are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VisualizationHintsModel {
   public String getChartType() {
      return chartType;
   }

   public void setChartType(String chartType) {
      this.chartType = chartType;
   }

   public boolean isChartTypeIsExplicit() {
      return chartTypeIsExplicit;
   }

   public void setChartTypeIsExplicit(boolean chartTypeIsExplicit) {
      this.chartTypeIsExplicit = chartTypeIsExplicit;
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

   /** ComponentType string value, e.g. "bar", "line", "crosstab". */
   private String chartType;
   /** True when the user explicitly named the chart type; false when inferred. */
   private boolean chartTypeIsExplicit;
   /**
    * Per-field configurations from the LLM.
    * Deserialized using the existing {@link SimpleFieldInfo} polymorphic hierarchy:
    * {@code fieldType:"dimension"} → {@link DimensionFieldInfo},
    * {@code fieldType:"measure"} → {@link MeasureFieldInfo}.
    */
   private List<SimpleFieldInfo> fieldConfigs;
   /** Optional explicit slot assignments; may be null or empty. */
   private List<ExplicitBinding> explicitBindings;
}
