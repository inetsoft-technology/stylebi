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
 * Request body for {@code POST /api/wiz/viewsheet/changeType}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChangeTypeRequest {
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

   /**
    * When true, keep the current primary assembly (demoted, not removed) and add the new
    * chart-type assembly as a second, separate primary — used for agent/MCP-driven type changes
    * so the user's original chart card is preserved instead of replaced. Default false
    * (delete-and-replace, the existing UI-click behavior).
    */
   public boolean isCopy() {
      return copy;
   }

   public void setCopy(boolean copy) {
      this.copy = copy;
   }

   /**
    * When set, replace THIS SPECIFIC assembly in place instead of whichever assembly is currently
    * primary — a session shares one output viewsheet/runtime across every turn, so "whichever is
    * primary" is always the latest turn's chart, not necessarily the one the user clicked. Used
    * for changeType on a non-current (historical) card. Ignored (falls back to the primary-based
    * behavior above) when null/empty, the default.
    */
   public String getAssemblyName() {
      return assemblyName;
   }

   public void setAssemblyName(String assemblyName) {
      this.assemblyName = assemblyName;
   }

   /**
    * Field-level overrides (currently only {@code aggregateFormula} is honored) carried over from
    * the visualization being switched away from. changeType's own recommendation model — whether
    * reused from a prior {@code autoBinding} call or freshly recomputed by the fallback below —
    * has no idea what formula the LIVE chart actually ended up using for a measure (e.g. a
    * complex-chart LLM node like the map binding path picks its own formula directly against
    * {@code /viewsheet/create}, entirely bypassing the wizard recommender this model comes from),
    * so its generic per-type default (e.g. Sum for any numeric column) can silently override an
    * intentional choice like Count on the SAME field. Passing the caller's already-resolved
    * formula here lets it win over that default instead of being clobbered on every type switch.
    */
   public List<SimpleFieldInfo> getFieldConfigs() {
      return fieldConfigs;
   }

   public void setFieldConfigs(List<SimpleFieldInfo> fieldConfigs) {
      this.fieldConfigs = fieldConfigs;
   }

   private String worksheetId;
   private String visualizationType;
   /**
    * Recommendation-computation RVS ID returned by a prior {@code autoBinding} call.
    * The recommendation model stored on this RVS is reused to select the new primary.
    * When absent or stale, the service falls back to re-running auto binding.
    */
   private String autoBindingRuntimeId;
   /**
    * Output-viewsheet RVS ID. The primary assembly in this RVS is replaced with the
    * assembly corresponding to {@code visualizationType}.
    */
   private String wizRuntimeId;
   /**
    * Persisted viewsheet identifier returned from the previous call.
    * Passed back so the viewsheet entry is overwritten in place rather than duplicated.
    */
   private String viewsheetIdentifier;
   private boolean copy;
   private String assemblyName;
   private List<SimpleFieldInfo> fieldConfigs;
}
