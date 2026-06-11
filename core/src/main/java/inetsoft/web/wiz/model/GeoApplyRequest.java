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
import java.util.Map;

/**
 * Request body for {@code POST /api/wiz/viewsheet/geo/apply}.
 * Applies manual value-to-geoCode {@code mappings} on the geographic {@code column} of the chart
 * {@code assemblyName}, and treats {@code drop} values as intentionally unmatched.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeoApplyRequest {
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

   public String getColumn() {
      return column;
   }

   public void setColumn(String column) {
      this.column = column;
   }

   public Map<String, String> getMappings() {
      return mappings;
   }

   public void setMappings(Map<String, String> mappings) {
      this.mappings = mappings;
   }

   public List<String> getDrop() {
      return drop;
   }

   public void setDrop(List<String> drop) {
      this.drop = drop;
   }

   public String getViewsheetIdentifier() {
      return viewsheetIdentifier;
   }

   public void setViewsheetIdentifier(String viewsheetIdentifier) {
      this.viewsheetIdentifier = viewsheetIdentifier;
   }

   private String runtimeId;
   private String assemblyName;
   private String column;
   /** dataValue -> geoCode (or display label, resolved to a geoCode by the service). */
   private Map<String, String> mappings;
   /** Values intentionally left unmatched. */
   private List<String> drop;
   /**
    * Optional session viewsheet asset identifier. When present, the resolved feature mappings are
    * persisted back to this asset so a saved map keeps them.
    */
   private String viewsheetIdentifier;
}
