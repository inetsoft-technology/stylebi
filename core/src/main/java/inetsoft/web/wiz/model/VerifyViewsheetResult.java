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
 * Result of verifying that a saved wiz visualization actually renders: whether the chart
 * produced data, the row count, and — when the underlying query failed — the real
 * data-source error. Unlike opening, verification executes the chart server-side so a save
 * that reopens empty or errors can be detected without a browser embed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerifyViewsheetResult {
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

   public boolean isHasData() {
      return hasData;
   }

   public void setHasData(boolean hasData) {
      this.hasData = hasData;
   }

   public int getRowCount() {
      return rowCount;
   }

   public void setRowCount(int rowCount) {
      this.rowCount = rowCount;
   }

   public String getError() {
      return error;
   }

   public void setError(String error) {
      this.error = error;
   }

   private String viewsheetIdentifier;
   private String assemblyName;
   private boolean hasData;
   private int rowCount;
   private String error;
}
