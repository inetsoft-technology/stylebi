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
 * Request body for {@code POST /api/wiz/viewsheet/geo/detect}.
 * Marks {@code column} on the chart {@code assemblyName} geographic and auto-detects
 * its geo type/layer + matching against built-in features.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeoDetectRequest {
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

   public String getGeoType() {
      return geoType;
   }

   public void setGeoType(String geoType) {
      this.geoType = geoType;
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
   /** Optional explicit map type (e.g. "World", "U.S."); when absent, auto-detected. */
   private String geoType;
   /**
    * Optional session viewsheet asset identifier. When present, the map conversion is persisted
    * back to this asset (save_viewsheet reads from storage, not the live runtime).
    */
   private String viewsheetIdentifier;
}
