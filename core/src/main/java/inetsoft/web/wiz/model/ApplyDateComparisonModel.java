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
import inetsoft.web.composer.model.vs.DateComparisonDialogModel;

/**
 * Request body for {@code POST /api/wiz/viewsheet/date-comparison}.
 *
 * <p>The wiz-services caller POSTs:
 * {@code { runtimeId, viewsheetIdentifier?, assemblyName?, copy?, sampleMaxRows?,
 * dateComparisonModel: { dateComparisonPaneModel: <PaneModel> } } }.
 *
 * <p>{@code dateComparisonModel} is a {@link DateComparisonDialogModel}, which Jackson binds
 * directly; its {@code dateComparisonPaneModel.toDateComparisonInfo()} reuses StyleBI's own
 * JSON-to-{@code DateComparisonInfo} conversion (no re-derivation here).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplyDateComparisonModel {
   public String getRuntimeId() {
      return runtimeId;
   }

   public void setRuntimeId(String runtimeId) {
      this.runtimeId = runtimeId;
   }

   /** Optional; resolve the sole chart assembly if absent. */
   public String getAssemblyName() {
      return assemblyName;
   }

   public void setAssemblyName(String assemblyName) {
      this.assemblyName = assemblyName;
   }

   public String getViewsheetIdentifier() {
      return viewsheetIdentifier;
   }

   public void setViewsheetIdentifier(String viewsheetIdentifier) {
      this.viewsheetIdentifier = viewsheetIdentifier;
   }

   public DateComparisonDialogModel getDateComparisonModel() {
      return dateComparisonModel;
   }

   public void setDateComparisonModel(DateComparisonDialogModel dateComparisonModel) {
      this.dateComparisonModel = dateComparisonModel;
   }

   public Integer getSampleMaxRows() {
      return sampleMaxRows;
   }

   public void setSampleMaxRows(Integer sampleMaxRows) {
      this.sampleMaxRows = sampleMaxRows;
   }

   /**
    * Duplicate the target chart before applying the comparison, instead of mutating it in place.
    *
    * <p>Needed exactly when the calling turn has not yet established a chart of its own: a stand-alone
    * "add a year-over-year comparison" against an existing chart must not rewrite the chart the user
    * already has. After a create or a binding rebind the chart IS the turn's own, and the caller sends
    * false so no intermediate copy is orphaned. Same contract as {@code ApplyHighlightModel.copy}.
    */
   public boolean isCopy() {
      return copy;
   }

   public void setCopy(boolean copy) {
      this.copy = copy;
   }

   private String runtimeId;
   private String assemblyName;
   private String viewsheetIdentifier;
   private DateComparisonDialogModel dateComparisonModel;
   private Integer sampleMaxRows;
   private boolean copy;
}
