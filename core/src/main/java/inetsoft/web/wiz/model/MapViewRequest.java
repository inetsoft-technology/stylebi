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
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/wiz/viewsheet/chart/map-view/get} and {@code .../set}.
 * For {@code get} only the identifying fields are used. For {@code set}, {@code zoomBy} is a
 * relative zoom step (as the map's zoom buttons), {@code panX}/{@code panY} are relative pans as a
 * fraction of the map width/height, and {@code reset} restores the default view; {@code reset} is
 * exclusive with the others.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MapViewRequest {
   public String getRuntimeId() {
      return runtimeId;
   }

   public void setRuntimeId(String runtimeId) {
      this.runtimeId = runtimeId;
   }

   public String getViewsheetIdentifier() {
      return viewsheetIdentifier;
   }

   public void setViewsheetIdentifier(String viewsheetIdentifier) {
      this.viewsheetIdentifier = viewsheetIdentifier;
   }

   public String getAssemblyName() {
      return assemblyName;
   }

   public void setAssemblyName(String assemblyName) {
      this.assemblyName = assemblyName;
   }

   public Integer getZoomBy() {
      return zoomBy;
   }

   public void setZoomBy(Integer zoomBy) {
      this.zoomBy = zoomBy;
   }

   public Double getPanX() {
      return panX;
   }

   public void setPanX(Double panX) {
      this.panX = panX;
   }

   public Double getPanY() {
      return panY;
   }

   public void setPanY(Double panY) {
      this.panY = panY;
   }

   public Boolean getReset() {
      return reset;
   }

   public void setReset(Boolean reset) {
      this.reset = reset;
   }

   @NotBlank
   private String runtimeId;
   private String viewsheetIdentifier;
   @NotBlank
   private String assemblyName;
   private Integer zoomBy;
   private Double panX;
   private Double panY;
   private Boolean reset;
}
