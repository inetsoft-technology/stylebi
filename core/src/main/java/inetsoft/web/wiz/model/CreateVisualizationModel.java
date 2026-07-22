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
import inetsoft.uql.viewsheet.VSAssembly;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateVisualizationModel {
   public String getVisualizationType() {
      return visualizationType;
   }

   public void setVisualizationType(String visualizationType) {
      this.visualizationType = visualizationType;
   }

   public VisualizationConfig getConfig() {
      return config;
   }

   public void setConfig(VisualizationConfig config) {
      this.config = config;
   }

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

   public VisualizationConditionModel getConditionModel() {
      return conditionModel;
   }

   public void setConditionModel(VisualizationConditionModel conditionModel) {
      this.conditionModel = conditionModel;
   }

   /**
    * A fully-configured assembly produced by the wizard setup path.
    * When set, {@code WizVsService} rebinds it to the target viewsheet directly.
    */
   public VSAssembly getPrimaryAssembly() {
      return primaryAssembly;
   }

   public void setPrimaryAssembly(VSAssembly primaryAssembly) {
      this.primaryAssembly = primaryAssembly;
   }

   public boolean isKeepCondition() {
      return keepCondition;
   }

   public void setKeepCondition(boolean keepCondition) {
      this.keepCondition = keepCondition;
   }

   /**
    * When true AND this call is otherwise eligible for the in-place "modificationOnly" path (see
    * {@code WizVsService.createViewsheetInternal}: {@link #getConfig()}/{@link #getPrimaryAssembly()}
    * are null and {@link #getConditionModel()} is set), duplicate the current primary assembly first
    * and apply the condition to the COPY instead — the original chart is left untouched. Mirrors
    * {@code ChartColorsRequest}/{@code ChartFormatRequest}/{@code ApplyHighlightModel}'s {@code copy}
    * flag. Has no effect outside the modificationOnly path (the standard create/rebind path always
    * produces a fresh assembly on its own). Default false (in-place, the existing behavior).
    */
   public boolean isCopy() {
      return copy;
   }

   public void setCopy(boolean copy) {
      this.copy = copy;
   }

   /**
    * #75456: row cap for sampled-preview mode. Null or &lt;=0 = full data (the default and the
    * agent path); &gt;0 = aggregate at most this many detail rows (faster on heavy/non-mergeable
    * sources, but Sum/Count may be approximate).
    */
   public Integer getSampleMaxRows() {
      return sampleMaxRows;
   }

   public void setSampleMaxRows(Integer sampleMaxRows) {
      this.sampleMaxRows = sampleMaxRows;
   }

   private String visualizationType;
   private VisualizationConfig config;
   private String runtimeId;
   private String viewsheetIdentifier;
   private VisualizationConditionModel conditionModel;
   private Integer sampleMaxRows;
   private transient VSAssembly primaryAssembly;
   private transient boolean keepCondition;
   private boolean copy;
}
