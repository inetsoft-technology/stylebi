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

/**
 * Request body for {@code POST /api/wiz/viewsheet/changeType}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChangeTypeRequest {
   public String getWorksheetPath() {
      return worksheetPath;
   }

   public void setWorksheetPath(String worksheetPath) {
      this.worksheetPath = worksheetPath;
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

   private String worksheetPath;
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
}
