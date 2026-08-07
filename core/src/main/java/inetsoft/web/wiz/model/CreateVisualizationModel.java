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
    * When true, the previous primary assembly is kept (demoted, not deleted) instead of being
    * removed when a new primary is created:
    * <ul>
    *   <li>In the "modificationOnly" path (see {@code WizVsService.createViewsheetInternal}:
    *       {@link #getConfig()}/{@link #getPrimaryAssembly()} are null and
    *       {@link #getConditionModel()} is set), the current primary is duplicated and the
    *       condition is applied to the COPY instead of mutating the original in place. Mirrors
    *       {@code ChartColorsRequest}/{@code ChartFormatRequest}/{@code ApplyHighlightModel}'s
    *       {@code copy} flag.</li>
    *   <li>In the standard create/rebind path (chart-type changes and general creation), the
    *       displaced previous primary is demoted but left in the viewsheet as a second,
    *       non-primary assembly instead of being deleted — used for agent/MCP-driven chart-type
    *       changes so the user's original chart is preserved alongside the new one, rather than
    *       replaced (the UI-click flow leaves this false to keep its existing delete-and-replace
    *       behavior).</li>
    * </ul>
    * Default false (in-place / delete-and-replace, the existing behavior) in both paths. Not
    * consulted by the standard path when {@link #getAssemblyName()} names an assembly to replace —
    * see that field's javadoc.
    */
   public boolean isCopy() {
      return copy;
   }

   public void setCopy(boolean copy) {
      this.copy = copy;
   }

   /**
    * When true, createViewsheetInternal syncs the source chart's configs (resolved via
    * getAssemblyName()) onto the newly-built assembly — reusing the wizard's SyncInfoHandler.
    * Gated so a fresh creation never inherits an unrelated chart's configs.
    */
   public boolean isSyncConfigs() {
      return syncConfigs;
   }

   public void setSyncConfigs(boolean syncConfigs) {
      this.syncConfigs = syncConfigs;
   }

   /**
    * The autoBinding runtime whose wizard temp chart holds this conversation's accumulated
    * binding-field settings (top-N ranking, sort, date group level, aggregate formula).
    *
    * <p>Supplying it lets createViewsheetInternal record THIS request's field settings on that temp
    * chart, which is the only place they survive a later rebuild-from-recommendation: every
    * recommendation candidate's x/y refs are clones of the temp chart's, whereas the rendered assembly
    * is discarded by the next rebuild. Without it a "show top 3" applied here is silently dropped the
    * next time the chart type changes.
    *
    * <p>Optional. Null — the default, and every caller that has no wizard runtime (the MCP path) — just
    * means there is nothing to record; the rendered assembly still gets the settings.
    */
   public String getAutoBindingRuntimeId() {
      return autoBindingRuntimeId;
   }

   public void setAutoBindingRuntimeId(String autoBindingRuntimeId) {
      this.autoBindingRuntimeId = autoBindingRuntimeId;
   }

   /**
    * Which existing chart this call addresses, by name (see
    * {@code WizVsService.createViewsheetInternal}). Null — the default and every pre-existing
    * caller — means the viewsheet's current PRIMARY assembly, i.e. the chart created or copied most
    * recently.
    *
    * <p>Naming one is required whenever the caller acts on a chart that is NOT the newest. A
    * session shares one viewsheet/runtime across all turns, so "whichever assembly is primary" is
    * always the latest turn's chart, not necessarily the one the user targeted. What the named
    * chart is used FOR depends on which path the call takes — the two are mutually exclusive, so
    * one field serves both:
    * <ul>
    *   <li>"modificationOnly" path ({@link #getConfig()}/{@link #getPrimaryAssembly()} null and
    *       {@link #getConditionModel()} set) — the chart to MODIFY (the source the condition is
    *       applied to, or duplicated from when {@link #isCopy()}). Without it, a filter built
    *       against an earlier chart's fields lands on a different chart, which at best filters the
    *       wrong chart and at worst references columns that chart does not bind.</li>
    *   <li>Standard create/rebind path — the chart this call is ABOUT: the binding it rebuilds from,
    *       and, when {@link #isCopy()} is false, the chart to REPLACE in place (the new assembly is
    *       added under this same name with the old one's exact primary state carried over, and no
    *       other assembly's primary flag is touched). With {@link #isCopy()} true the named chart is
    *       kept as history and the result lands in a new assembly, exactly as it would with no name —
    *       the name still matters, because it says which chart's binding and pre-condition to carry.
    *       A click on a card's own chart-type menu is the former, a chat turn about that card the
    *       latter.</li>
    * </ul>
    */
   public String getAssemblyName() {
      return assemblyName;
   }

   public void setAssemblyName(String assemblyName) {
      this.assemblyName = assemblyName;
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
   private String assemblyName;
   private VisualizationConditionModel conditionModel;
   private Integer sampleMaxRows;
   private transient VSAssembly primaryAssembly;
   private transient boolean keepCondition;
   private boolean copy;
   private boolean syncConfigs;
   private String autoBindingRuntimeId;
}
