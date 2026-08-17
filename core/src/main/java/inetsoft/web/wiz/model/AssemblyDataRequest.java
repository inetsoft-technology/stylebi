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

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for reading an existing assembly's rendered data.
 *
 * <p>{@code runtimeId} is the runtime viewsheet id (the wiz-side visualizationId);
 * {@code assemblyName} is the assembly to read. Both must be non-blank. Any assembly type the
 * service dispatches on is valid here — chart, table, crosstab, and the scalar output assemblies
 * (gauge / text).
 *
 * <p><b>These two fields are the whole request, deliberately.</b> They are exactly what the browser
 * embed renders a chart with, so whatever this endpoint returns is by construction the data behind
 * the chart the user is looking at. Adding a row cap, a filter or a binding override would break
 * that correspondence: the caller could then fetch data the chart on screen does not show, and
 * answer a question about the chart from numbers the user cannot see. The row cap in force stays
 * whatever the chart itself was rendered with — see WizVsService#fetchAssemblyData.
 */
public class AssemblyDataRequest {
   @NotBlank
   private String runtimeId;
   @NotBlank
   private String assemblyName;

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
}
