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

/**
 * Result of opening a saved wiz viewsheet: the runtime id plus the primary chart
 * assembly name, so callers can drive subsequent runtime operations (filter, browse-data,
 * embed) without a separate assembly lookup.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenViewsheetResult {
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

   public CreateViewsheetResult.FlatBinding getBinding() {
      return binding;
   }

   public void setBinding(CreateViewsheetResult.FlatBinding binding) {
      this.binding = binding;
   }

   private String runtimeId;
   private String assemblyName;
   private CreateViewsheetResult.FlatBinding binding;
}
