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
import java.util.Map;

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

   public List<ChartTypeCandidate> getCandidates() {
      return candidates;
   }

   public void setCandidates(List<ChartTypeCandidate> candidates) {
      this.candidates = candidates;
   }

   public String getSelectionNote() {
      return selectionNote;
   }

   public void setSelectionNote(String selectionNote) {
      this.selectionNote = selectionNote;
   }

   public Map<String, Integer> getFieldCardinalities() {
      return fieldCardinalities;
   }

   public void setFieldCardinalities(Map<String, Integer> fieldCardinalities) {
      this.fieldCardinalities = fieldCardinalities;
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

   /**
    * Feasibility-filtered chart-type menu, flattened from {@link #recommendations} into named,
    * scored entries (highest score first). The caller picks the final type from this menu; only
    * types the recommender found feasible for the current fields appear here.
    */
   private List<ChartTypeCandidate> candidates;

   /**
    * Set only when a requested {@code visualizationType} could not be honored and a different type
    * was substituted (e.g. "requested 'pie' is not feasible for this data; used 'bar'"). Null when
    * the requested type was honored or none was requested.
    */
   private String selectionNote;

   /**
    * Distinct-value counts (cardinality) for the selected non-date dimension fields, keyed by field
    * name. Lets the caller spot high-cardinality dimensions (e.g. 600 cities) and switch to top-N
    * ranking or a roll-up instead of an unreadable chart. Empty when cardinality was not computed
    * (the recommender only samples it for field sets of 9 or fewer) or no such dimension was bound.
    */
   private Map<String, Integer> fieldCardinalities;
}
