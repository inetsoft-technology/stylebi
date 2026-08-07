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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Request body for {@code POST /api/wiz/viewsheet/autoBinding}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AutoBindingRequest {
   public String getWorksheetId() {
      return worksheetId;
   }

   public void setWorksheetId(String worksheetId) {
      this.worksheetId = worksheetId;
   }

   public String getVisualizationType() {
      return visualizationType;
   }

   public void setVisualizationType(String visualizationType) {
      this.visualizationType = visualizationType;
   }

   public List<SimpleFieldInfo> getFieldConfigs() {
      return fieldConfigs;
   }

   public void setFieldConfigs(List<SimpleFieldInfo> fieldConfigs) {
      this.fieldConfigs = fieldConfigs;
   }

   public List<ExplicitBinding> getExplicitBindings() {
      return explicitBindings;
   }

   public void setExplicitBindings(List<ExplicitBinding> explicitBindings) {
      this.explicitBindings = explicitBindings;
   }

   public String getIntentCategory() {
      return intentCategory;
   }

   public void setIntentCategory(String intentCategory) {
      this.intentCategory = intentCategory;
   }

   /**
    * Worksheet global path, same as VisualizationConfig.data.source.
    */
   private String worksheetId;
   /**
    * Expected visualization type string, e.g. "bar", "table", "crosstab", "gauge".
    */
   private String visualizationType;
   /**
    * All fields to bind for visualization, each with its configuration
    * (aggregateFormula, dateGroupLevel, ranking, sort order, etc.).
    * This is the authoritative list: autoBinding filters the worksheet's
    * {@code columnSelection} to exactly these columns and applies their configs.
    * When empty or absent, all visible worksheet columns are eligible.
    */
   private List<SimpleFieldInfo> fieldConfigs;

   /**
    * Optional explicit slot assignments; may be null or empty.
    */
   private List<ExplicitBinding> explicitBindings;
   /**
    * Visualization intent category inferred by the LLM.
    * One of: "comparison", "trend", "distribution", "proportion",
    * "relationship", "ranking", "geospatial", "other".
    */
   private String intentCategory;

   public String getAutoBindingRuntimeId() {
      return autoBindingRuntimeId;
   }

   public void setAutoBindingRuntimeId(String autoBindingRuntimeId) {
      this.autoBindingRuntimeId = autoBindingRuntimeId;
   }

   public String getWizRuntimeId() {
      return wizRuntimeId;
   }

   public void setWizRuntimeId(String wizRuntimeId) {
      this.wizRuntimeId = wizRuntimeId;
   }

   public String getViewsheetIdentifier() {
      return viewsheetIdentifier;
   }

   public void setViewsheetIdentifier(String viewsheetIdentifier) {
      this.viewsheetIdentifier = viewsheetIdentifier;
   }

   public String getWsTableName() {
      return wsTableName;
   }

   public void setWsTableName(String wsTableName) {
      this.wsTableName = wsTableName;
   }

   /**
    * #75456: row cap for sampled-preview mode. Null or &lt;=0 = full data (default; the agent
    * always omits this). &gt;0 = aggregate at most this many detail rows for a faster preview on
    * heavy sources, at the cost of approximate Sum/Count.
    */
   public Integer getSampleMaxRows() {
      return sampleMaxRows;
   }

   public void setSampleMaxRows(Integer sampleMaxRows) {
      this.sampleMaxRows = sampleMaxRows;
   }

   /**
    * When true AND this call targets an existing primary (wizRuntimeId set), keep the current
    * primary assembly (demoted, not removed) and add the new assembly as a second, separate
    * primary instead of replacing it — used for agent/MCP-driven binding changes (e.g. pie/donut
    * chart-type changes, which route through autoBinding) so the user's original chart card is
    * preserved. Default false (delete-and-replace, the existing behavior). No effect on a
    * first-time call with no existing primary to displace.
    */
   public boolean isCopy() {
      return copy;
   }

   public void setCopy(boolean copy) {
      this.copy = copy;
   }

   /**
    * When true, the create call this autoBinding drives should sync the previous (source) chart's
    * configs — highlight/format/condition/etc. — onto the freshly-bound assembly. Set by the AI
    * layer only on a continuation (rebuild) turn; false on a fresh creation.
    */
   public boolean isSyncConfigs() {
      return syncConfigs;
   }

   public void setSyncConfigs(boolean syncConfigs) {
      this.syncConfigs = syncConfigs;
   }

   /**
    * When set, replace THIS SPECIFIC assembly in place instead of whichever assembly is currently
    * primary — a session shares one output viewsheet/runtime across every turn, so "whichever is
    * primary" is always the latest turn's chart, not necessarily the one the user targeted. Used
    * for changeType (on pie/donut-style targets, which route through autoBinding) on a non-current
    * (historical) card. Ignored (falls back to the primary-based behavior above) when null/empty.
    */
   public String getAssemblyName() {
      return assemblyName;
   }

   public void setAssemblyName(String assemblyName) {
      this.assemblyName = assemblyName;
   }

   /**
    * Carry the displaced assembly's pre-condition onto the freshly-bound one — forwarded straight to
    * {@link inetsoft.web.wiz.model.CreateVisualizationModel#isKeepCondition()}.
    *
    * <p>Server-side only: the sole caller is {@code changeType}'s rebuild branch, which replaces a chart
    * the user already filtered and so must keep that filter, exactly as the fast path does. A plain
    * {@code /viewsheet/autoBinding} request is a (re)bind, not a type switch, and leaves this false — its
    * own condition handling goes through {@code syncConfigs}.
    *
    * <p>{@code @JsonIgnore}, not merely {@code transient}: Jackson binds through the setter and ignores
    * the field's transient marker unless {@code MapperFeature.PROPAGATE_TRANSIENT_MARKER} is on, which it
    * is not here — so without the annotation a raw request body could set this and silently carry a
    * filter onto a rebind that asked for none.
    */
   @JsonIgnore
   public boolean isKeepCondition() {
      return keepCondition;
   }

   @JsonIgnore
   public void setKeepCondition(boolean keepCondition) {
      this.keepCondition = keepCondition;
   }

   /**
    * Recommendation-computation RVS ID. Null on first call; returned by the server
    * and passed back on subsequent calls to reuse the same RVS.
    */
   private String autoBindingRuntimeId;

   /**
    * Output-viewsheet RVS ID. Null on first call; returned by the server and passed back
    * on subsequent calls so the primary assembly is updated in place.
    * Mirrors the runtimeId semantics of CreateVisualizationModel.
    */
   private String wizRuntimeId;

   /**
    * Persisted viewsheet identifier returned from the previous call.
    * Null on first call; must be passed back on subsequent calls so persistViewsheet
    * overwrites the existing entry rather than creating a new one.
    */
   private String viewsheetIdentifier;

   /**
    * Worksheet table name.
    * Specifies which table within the worksheet to bind.
    */
   private String wsTableName;

   /**
    * #75456: sampled-preview row cap; null/&lt;=0 = full data (default).
    */
   private Integer sampleMaxRows;

   private boolean copy;
   private String assemblyName;
   private boolean syncConfigs;
   private transient boolean keepCondition;
}