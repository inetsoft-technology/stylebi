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
 * Request body for {@code POST /api/wiz/viewsheet/image} — the {@code runtimeId}-keyed sibling of
 * the paired-session {@code /api/wiz/v1/agent/viewsheet/{sessionToken}/image} endpoint. Omitting
 * {@code target} renders the whole viewsheet (chart + any filter controls); naming an assembly
 * renders just that assembly.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VisualizationImageRequest {
   @NotBlank
   private String runtimeId;
   private String viewsheetIdentifier;
   private String target;
   private Integer width;
   private Integer height;

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

   public String getTarget() {
      return target;
   }

   public void setTarget(String target) {
      this.target = target;
   }

   public Integer getWidth() {
      return width;
   }

   public void setWidth(Integer width) {
      this.width = width;
   }

   public Integer getHeight() {
      return height;
   }

   public void setHeight(Integer height) {
      this.height = height;
   }
}
