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

/**
 * Response body for POST /api/wiz/visualization/dashboard.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WizDashboardResult {
   public String getSavedViewsheetIdentifier() {
      return savedViewsheetIdentifier;
   }

   public void setSavedViewsheetIdentifier(String savedViewsheetIdentifier) {
      this.savedViewsheetIdentifier = savedViewsheetIdentifier;
   }

   /** Curated identifiers that could not be merged (e.g. deleted) and were skipped. */
   public List<String> getSkipped() {
      return skipped;
   }

   public void setSkipped(List<String> skipped) {
      this.skipped = skipped;
   }

   /** Filter fields that were applied to at least one viewsheet in the dashboard. */
   public List<String> getFiltersApplied() {
      return filtersApplied;
   }

   public void setFiltersApplied(List<String> filtersApplied) {
      this.filtersApplied = filtersApplied;
   }

   /** Filter fields that could not be applied to any viewsheet in the dashboard. */
   public List<String> getFiltersSkipped() {
      return filtersSkipped;
   }

   public void setFiltersSkipped(List<String> filtersSkipped) {
      this.filtersSkipped = filtersSkipped;
   }

   private String savedViewsheetIdentifier;
   private List<String> skipped;
   private List<String> filtersApplied;
   private List<String> filtersSkipped;
}
