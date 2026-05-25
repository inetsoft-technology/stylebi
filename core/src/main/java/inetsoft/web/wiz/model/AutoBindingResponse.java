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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.wiz.model;

import inetsoft.web.vswizard.model.recommender.VSObjectRecommendation;

import java.util.List;

/**
 * Response body for {@code POST /api/wiz/viewsheet/autoBinding}.
 */
public class AutoBindingResponse {
   public List<VSObjectRecommendation> getRecommendations() {
      return recommendations;
   }

   public void setRecommendations(List<VSObjectRecommendation> recommendations) {
      this.recommendations = recommendations;
   }

   public RecommendedVisualization getPrimary() {
      return primary;
   }

   public void setPrimary(RecommendedVisualization primary) {
      this.primary = primary;
   }

   public String getAutoBindingRuntimeId() {
      return autoBindingRuntimeId;
   }

   public void setAutoBindingRuntimeId(String autoBindingRuntimeId) {
      this.autoBindingRuntimeId = autoBindingRuntimeId;
   }

   public CreateViewsheetResult getVisualizationResult() {
      return visualizationResult;
   }

   public void setVisualizationResult(CreateViewsheetResult visualizationResult) {
      this.visualizationResult = visualizationResult;
   }

   /**
    * All candidate visualizations: charts ordered by vsWizard score, then table (always),
    * crosstab (only when both dimensions and measures are present),
    * and gauge (only when there is exactly one measure and no dimensions).
    */
   private List<VSObjectRecommendation> recommendations;

   /**
    * Best-fit visualization built from user preferences; null when no preference can be determined.
    */
   private RecommendedVisualization primary;

   /**
    * Recommendation-computation RVS ID. Client must pass this back in subsequent
    * autoBinding requests to reuse the same RVS and avoid repeated open/close overhead.
    */
   private String autoBindingRuntimeId;

   /**
    * Full result of the primary visualization creation: headers, rows, binding,
    * assemblyName, viewsheetIdentifier, hasData, truncated, and runtimeId (the
    * output-viewsheet RVS ID, i.e. wizRuntimeId). Client must pass
    * visualizationResult.runtimeId back as wizRuntimeId and
    * visualizationResult.viewsheetIdentifier back as viewsheetIdentifier on subsequent
    * calls so the primary assembly is updated in place. Null when primary is null.
    */
   private CreateViewsheetResult visualizationResult;
}
