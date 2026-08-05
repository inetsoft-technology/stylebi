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

/**
 * Request body for {@code POST /api/wiz/chart/aestheticModel}. A pure read — no {@code copy} flag,
 * since nothing is mutated and there is no original to protect.
 */
public class ChartAestheticModelRequest {
   public String getWizRuntimeId() { return wizRuntimeId; }
   public void setWizRuntimeId(String wizRuntimeId) { this.wizRuntimeId = wizRuntimeId; }

   public String getAssemblyName() { return assemblyName; }
   public void setAssemblyName(String assemblyName) { this.assemblyName = assemblyName; }

   /** Durable asset id, used to restore a reaped runtime; optional. */
   public String getViewsheetIdentifier() { return viewsheetIdentifier; }
   public void setViewsheetIdentifier(String viewsheetIdentifier) {
      this.viewsheetIdentifier = viewsheetIdentifier;
   }

   private String wizRuntimeId;
   private String assemblyName;
   private String viewsheetIdentifier;
}
