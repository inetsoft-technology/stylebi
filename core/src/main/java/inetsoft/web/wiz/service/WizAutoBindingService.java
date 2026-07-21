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
package inetsoft.web.wiz.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.ExpiredSheetException;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.internal.graph.ChangeChartTypeProcessor;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.graph.aesthetic.*;
import inetsoft.uql.viewsheet.graph.aesthetic.ColorPalettes;
import java.awt.Color;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.vswizard.handler.VSWizardBindingHandler;
import inetsoft.web.vswizard.model.VSWizardData;
import inetsoft.web.vswizard.model.recommender.*;
import inetsoft.web.vswizard.recommender.ChartRecommenderUtil;
import inetsoft.web.vswizard.recommender.VSDefaultRecommendationFactory;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import inetsoft.web.vswizard.recommender.chart.ChartCombinationUtil;
import inetsoft.web.vswizard.recommender.chart.ChartPreference;
import inetsoft.web.vswizard.recommender.chart.ChartTypeFilter;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.web.binding.model.graph.CalculateInfo;
import inetsoft.web.wiz.WizUtil;
import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.model.BindingInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.awt.*;
import java.security.Principal;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WizAutoBindingService {
   public WizAutoBindingService(ViewsheetService viewsheetService,
                                AssetRepository engine,
                                VSWizardTemporaryInfoService temporaryInfoService,
                                VSWizardBindingHandler bindingHandler,
                                VSDefaultRecommendationFactory defaultRecommendationFactory,
                                WizVsService wizVsService,
                                SecurityEngine securityEngine)
   {
      this.viewsheetService = viewsheetService;
      this.engine = engine;
      this.temporaryInfoService = temporaryInfoService;
      this.bindingHandler = bindingHandler;
      this.defaultRecommendationFactory = defaultRecommendationFactory;
      this.wizVsService = wizVsService;
      this.securityEngine = securityEngine;
   }

   public AutoBindingResponse autoBinding(AutoBindingRequest request, Principal user)
      throws Exception
   {
      return autoBindingInternal(request, user, false);
   }

   /**
    * Whether a cached recommendation (auto-binding) runtime can still be reused. The recommendation
    * runtime is a transient temp viewsheet with no durable asset, so a reaped one cannot be restored
    * and must be replaced; returns false for a gone/expired id so the caller mints a fresh one.
    */
   public boolean isRecommendationRuntimeReusable(String autoBindingRuntimeId, Principal user) throws Exception {
      try {
         return viewsheetService.getViewsheet(autoBindingRuntimeId, user) != null;
      }
      catch(ExpiredSheetException ex) {
         LOG.debug("Recommendation runtime [{}] expired; will mint a fresh one", autoBindingRuntimeId);
         return false;
      }
   }

   private AutoBindingResponse autoBindingInternal(AutoBindingRequest request, Principal user,
                                                   boolean skipExecution)
      throws Exception
   {
      // Action-level gate ("Visual Composer -> Data Viewsheet"): this method may open a brand-new
      // temporary viewsheet via viewsheetService.openTemporaryViewsheet below. It is reachable from
      // two public entry points — autoBinding() directly, and changeType()'s fallback when its
      // cached recommendation runtime is missing/expired — so the check is enforced here, at the
      // single choke point both paths share, rather than duplicated in each caller.
      if(!securityEngine.checkPermission(user, ResourceType.VIEWSHEET, "*", ResourceAction.ACCESS)) {
         throw new SecurityException(Catalog.getCatalog().getString(
            "composer.authorization.permissionDenied"));
      }

      List<SimpleFieldInfo> fieldConfigs = request.getFieldConfigs() != null
         ? request.getFieldConfigs() : Collections.emptyList();
      String worksheetId = request.getWorksheetId();

      // Phase 1: resolve or create the recommendation RVS.
      String autoBindingRuntimeId = request.getAutoBindingRuntimeId();
      boolean createdAutoBindingRvs = false;

      // A cached recommendation runtime that has been reaped (TTL/restart) cannot be reused, and unlike
      // the chart's viewsheet it has no durable asset to restore from — so treat an expired id as
      // absent and mint a fresh one below (mirrors changeType's fallback). Without this, a later
      // update_binding on an expired recommendation runtime would fail outright.
      if(!Tool.isEmptyString(autoBindingRuntimeId)
         && !isRecommendationRuntimeReusable(autoBindingRuntimeId, user))
      {
         autoBindingRuntimeId = null;
      }

      if(Tool.isEmptyString(autoBindingRuntimeId)) {
         Viewsheet.WizInfo wizInfo = new Viewsheet.WizInfo(true, null, null);
         // worksheetId is the worksheet's full asset IDENTIFIER (e.g. "1^2^__NULL__^path^org"),
         // not a bare path — parse it with createAssetEntry, else getSheet finds nothing and the
         // recommender gets an empty worksheet (zero recommendations).
         AssetEntry wsEntry = !Tool.isEmptyString(worksheetId)
            ? AssetEntry.createAssetEntry(worksheetId)
            : null;
         autoBindingRuntimeId = viewsheetService.openTemporaryViewsheet(null, wsEntry, user, wizInfo);
         createdAutoBindingRvs = true;
      }

      boolean succeeded = false;

      try {
         // Always re-initialize so tempChart state is clean regardless of reuse.
         temporaryInfoService.initTemporary(autoBindingRuntimeId, user, new Point(0, 0));
         RuntimeViewsheet rvs = viewsheetService.getViewsheet(autoBindingRuntimeId, user);
         VSTemporaryInfo tempInfo = rvs.getVSTemporaryInfo();

         String tableName = null;
         List<ColumnRef> worksheetColumns = new ArrayList<>();

         if(!Tool.isEmptyString(worksheetId)) {
            AssetEntry wsEntry = AssetEntry.createAssetEntry(worksheetId);

            try {
               AbstractSheet sheet = engine.getSheet(wsEntry, user, true, AssetContent.ALL);

               if(sheet instanceof Worksheet ws) {
                  String wsTableName = request.getWsTableName();
                  WSAssembly primary = null;

                  if(Tool.isEmptyString(wsTableName)) {
                     primary = ws.getPrimaryAssembly();
                  }
                  else {
                     primary = (WSAssembly) ws.getAssembly(wsTableName);
                  }

                  if(primary instanceof TableAssembly ta) {
                     tableName = primary.getName();
                     // An aggregating table exposes its aggregate-output columns (the group/aggregate
                     // aliases) ONLY in the public selection; the private selection still holds the
                     // pre-aggregation base columns. Read public for those so the aggregated columns
                     // are bindable (e.g. an aggregate-only mirror). The aggregate FLAG is not reliable
                     // here — the wiz worksheet-construction flow sets aggregateInfo without calling
                     // setAggregate(true) — so key off a non-empty aggregateInfo, not isAggregate().
                     // Non-aggregating tables keep using the private selection so visibility flags set
                     // by applyFieldVisibility (which writes to the private selection) are honored; the
                     // public selection is regenerated only from visible private columns, so visibility
                     // is preserved for the aggregating case as well.
                     AggregateInfo aggInfo = ta.getAggregateInfo();
                     boolean aggregating = aggInfo != null && !aggInfo.isEmpty();
                     ColumnSelection cs = ta.getColumnSelection(aggregating);

                     for(int i = 0; i < cs.getAttributeCount(); i++) {
                        DataRef ref = cs.getAttribute(i);

                        if(ref instanceof ColumnRef colRef && colRef.isVisible()) {
                           worksheetColumns.add(colRef);
                        }
                     }
                  }
               }
            }
            catch(Exception e) {
               LOG.warn("Failed to load worksheet '{}': {}", worksheetId, e.getMessage());
            }
         }

         Map<String, SimpleFieldInfo> configMap = fieldConfigs.stream()
            .filter(f -> f != null && f.getField() != null)
            .collect(Collectors.toMap(SimpleFieldInfo::getField, f -> f, (a, b) -> a));

         final String tableNameFinal = tableName;
         List<ColumnRef> bindColumns =
            selectBindColumns(worksheetColumns, configMap);

         AssetEntry[] entries = bindColumns.stream()
            .map(col -> buildEntryFromColumn(col, worksheetId, tableNameFinal, configMap))
            .toArray(AssetEntry[]::new);

         bindingHandler.updateTemporaryFields(rvs, entries, tempInfo);
         tempInfo.getTempChart().setSourceInfo(bindingHandler.getCurrentSource(entries, tableName));

         VSChartInfo tempChartInfo = tempInfo.getTempChart().getVSChartInfo();
         applyFieldConfigs(tempChartInfo, configMap);

         String vizType = request.getVisualizationType();
         List<ExplicitBinding> explicitBindings = request.getExplicitBindings();
         // Fail fast on impossible pins (unknown slot/field, measure→dimension-only slot, etc.)
         // before running the recommender, so the caller gets a structured 400.
         validateExplicitBindings(explicitBindings, configMap, entries);
         ChartPreference pref = buildChartPreference(explicitBindings);
         VSWizardData wizardData = new VSWizardData(entries, tempInfo, pref);
         VSRecommendationModel model = defaultRecommendationFactory.recommend(wizardData, rvs, user);
         tempInfo.setRecommendationModel(model);

         // Phase 2: select the primary recommendation and create the configured assembly.
         List<VSObjectRecommendation> recommendations = Collections.emptyList();
         VSObjectRecommendation selectedRec = null;
         VSAssembly primaryAssembly = null;
         // #75460: set when an explicit chart type the recommender substituted away is force-applied,
         // so the response reports the honored type and skips the "not feasible" substitution note.
         String forcedTypeName = null;

         if(model != null) {
            recommendations = model.getRecommendationList().stream()
               .filter(r -> !(r instanceof VSFilterRecommendation))
               .collect(Collectors.toList());

            String intentCategory = request.getIntentCategory();
            selectedRec = selectPrimaryRecommendation(model, vizType, explicitBindings, intentCategory);

            // Strict pins: when explicit bindings are present and the selected chart has NO
            // pin-satisfying candidate (prefInfos empty/null — the constraints filter rejected every
            // combination for these fields), there is no honest placement. Fail with a 400 rather
            // than rendering an unconstrained chart that ignores the pins.
            if(explicitBindings != null && !explicitBindings.isEmpty()
               && selectedRec instanceof VSChartRecommendation vcr
               && (vcr.getPrefInfos() == null || vcr.getPrefInfos().isEmpty()))
            {
               // The conflict is between the pins AS A SET — any single pin may be valid on
               // its own — so report all of them rather than blaming the first.
               List<UnsatisfiableBindingException.Pin> pins = explicitBindings.stream()
                  .map(b -> new UnsatisfiableBindingException.Pin(b.getRole(), b.getField()))
                  .collect(Collectors.toList());
               throw new UnsatisfiableBindingException(pins,
                  "no chart combination supports this placement for the bound fields");
            }

            // Mirror the wizard path: call updatePrimaryAssembly on autoBindingRvs so it gets
            // the same full setup (calc field registration, format, legend, temp assembly cleanup).
            if(selectedRec != null) {
               primaryAssembly = refreshVisualizationBinding(selectedRec, rvs, user);

               // The recommender rebuilds dimensions without the temp chart's ranking/aggregate
               // config, so re-apply fieldConfigs to the RENDERED binding before it executes.
               if(primaryAssembly instanceof ChartVSAssembly chartAsm && chartAsm.getVSChartInfo() != null) {
                  // Honor an explicit chart type the recommender substituted away (no pins in play;
                  // the pinned case is governed by the strict-pins guard above). Convert before
                  // applying fieldConfigs so the aggregate/ranking/format land on the final binding.
                  if(vizType != null && isChartVizType(vizType)
                     && (explicitBindings == null || explicitBindings.isEmpty())
                     && !requestedTypeAvailable(vizType, model, false))
                  {
                     forcedTypeName = applyForcedChartType(chartAsm, vizType);
                  }

                  applyFieldConfigs(chartAsm.getVSChartInfo(), configMap);
               }
               // The crosstab recommender decides row/col header placement itself and ignores
               // rows/cols pins (ChartPreference, the only pin channel into the recommender, models
               // chart slots — x/y/color/... — not rows/cols). Honor those pins here by moving the
               // pinned fields into the row- vs column-header groups on the rendered binding.
               else if(primaryAssembly instanceof CrosstabVSAssembly crosstabAsm &&
                       crosstabAsm.getVSCrosstabInfo() != null)
               {
                  applyCrosstabHeaderPins(crosstabAsm.getVSCrosstabInfo(), explicitBindings);
               }
            }
         }

         // Phase 3: place primary into the output viewsheet.
         CreateViewsheetResult visualizationResult = null;

         if(selectedRec != null && !Tool.isEmptyString(worksheetId)) {
            VisualizationConfig sourceConfig = new VisualizationConfig();
            VisualizationConfig.DataSource ds = new VisualizationConfig.DataSource();
            ds.setSource(worksheetId);
            sourceConfig.setData(ds);

            CreateVisualizationModel vsModel = new CreateVisualizationModel();
            vsModel.setConfig(sourceConfig);
            vsModel.setPrimaryAssembly(primaryAssembly);
            vsModel.setRuntimeId(request.getWizRuntimeId());
            vsModel.setViewsheetIdentifier(request.getViewsheetIdentifier());
            // #75456: carry the data-mode (full vs sampled) into the render so the chart aggregates
            // the chosen amount of data; null/<=0 = full (the agent always omits this).
            vsModel.setSampleMaxRows(request.getSampleMaxRows());

            WizVsService.PostAssemblyHook hook = (wizRvs, asm) -> {
               if(asm instanceof ChartVSAssembly) {
                  WizardRecommenderUtil.syncCalcFields(rvs.getViewsheet(), wizRvs.getViewsheet());
               }
            };

            visualizationResult = skipExecution
               ? wizVsService.createViewsheetSkipExecution(vsModel, user, hook)
               : wizVsService.createViewsheet(vsModel, user, hook);

            // createViewsheet only sets runtimeId when it creates a new RVS.
            // Back-fill it so the client always receives the effective wizRuntimeId.
            if(Tool.isEmptyString(visualizationResult.getRuntimeId())) {
               visualizationResult.setRuntimeId(request.getWizRuntimeId());
            }

            // Readability warnings: a PINNED aesthetic dimension whose cardinality exceeds the slot
            // cap renders but may be unreadable. The recommender already respected caps on UNPINNED
            // slots, so warn only for pinned ones. Honored stays true — we kept the user's pin.
            if(explicitBindings != null && !explicitBindings.isEmpty()
               && primaryAssembly instanceof ChartVSAssembly chartAsm
               && chartAsm.getVSChartInfo() != null
               && visualizationResult.getBinding() != null)
            {
               addReadabilityWarnings(visualizationResult.getBinding(), chartAsm.getVSChartInfo(),
                                      explicitBindings, entries);
            }
         }

         // Phase 4: build response.
         RecommendedVisualization primary = recommendationToVisualization(selectedRec, entries, worksheetId);

         // When we force-applied an explicit type (#75460), report the honored type, not the
         // recommender's substitute that `primary` was derived from.
         if(forcedTypeName != null && primary != null) {
            primary.setVisualizationType(forcedTypeName);
         }

         AutoBindingResponse resp = new AutoBindingResponse();
         resp.setRecommendations(recommendations);
         resp.setPrimary(primary);
         resp.setAutoBindingRuntimeId(autoBindingRuntimeId);
         resp.setVisualizationResult(visualizationResult);
         resp.setCandidates(buildChartTypeCandidates(recommendations));
         resp.setFieldCardinalities(buildFieldCardinalities(entries));

         // Tell the caller when a requested type could not be honored, so it can explain the
         // substitution to the user instead of the swap happening silently.
         boolean pinsPresent = explicitBindings != null && !explicitBindings.isEmpty();

         if(vizType != null && primary != null && forcedTypeName == null
            && !requestedTypeAvailable(vizType, model, pinsPresent)) {
            resp.setSelectionNote(pinsPresent
               ? "Requested chart type '" + vizType + "' has no candidate honoring the explicit " +
                 "bindings; used '" + primary.getVisualizationType() + "' instead."
               : "Requested chart type '" + vizType + "' is not feasible for this data; used '" +
                 primary.getVisualizationType() + "' instead.");
         }

         succeeded = true;
         return resp;
      }
      finally {
         // The recommendation RVS (autoBindingRuntimeId) is kept alive for client reuse.
         // Only clean up when we created it this call but failed before returning the ID.
         if(!succeeded && createdAutoBindingRvs) {
            try {
               temporaryInfoService.destroyTemporary(autoBindingRuntimeId, user);
            }
            catch(Exception e) {
               LOG.warn("Failed to destroy temp viewsheet info [{}]: {}", autoBindingRuntimeId, e.getMessage());
            }

            try {
               viewsheetService.closeViewsheet(autoBindingRuntimeId, user);
            }
            catch(Exception e) {
               LOG.warn("Failed to close temp viewsheet [{}]: {}", autoBindingRuntimeId, e.getMessage());
            }
         }
      }
   }

   // ── AssetEntry building ──────────────────────────────────────────────────────

   private AssetEntry buildEntryFromColumn(ColumnRef col, String wsPath, String tableName,
                                           Map<String, SimpleFieldInfo> configMap) {
      String alias = col.getAlias();
      String colName = (alias != null && !alias.isEmpty()) ? alias : col.getAttribute();
      String path = (wsPath != null && tableName != null)
         ? wsPath + "/" + tableName + "/" + colName : colName;

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.COLUMN, path, null);
      entry.setProperty("assembly", tableName != null ? tableName : "");
      entry.setProperty("attribute", colName);

      String dtype = col.getDataType() != null ? col.getDataType() : XSchema.STRING;
      entry.setProperty("dtype", dtype);

      // The caller's fieldConfig is authoritative about dimension vs measure. Honor it: a declared
      // measure (e.g. DistinctCount/Count over a string id) is a measure regardless of operand
      // type — otherwise the recommender would classify it by dtype, treat the aggregated id as a
      // high-cardinality dimension, and degrade the chart to a raw table. A declared dimension
      // (e.g. a numeric year/code grouped as a category) is likewise kept a dimension. Fall back
      // to the dtype heuristic only when the field has no explicit config.
      SimpleFieldInfo fc = configMap != null ? configMap.get(colName) : null;
      boolean isMeasure;

      if(fc instanceof MeasureFieldInfo) {
         isMeasure = true;
      }
      else if(fc instanceof DimensionFieldInfo) {
         isMeasure = false;
      }
      else {
         isMeasure = XSchema.isNumericType(dtype) && !XSchema.isDateType(dtype);
      }

      setRefType(entry, isMeasure);

      // A top-/bottom-N ranking on a dimension means only N values are shown, so its EFFECTIVE
      // cardinality for chart-feasibility is N — not the full distinct count. The recommender runs
      // before fieldConfigs are applied, so record the rank count on the entry; getCardinality caps
      // by it (see ChartRecommenderUtil.getCardinality) so a ranked high-cardinality dimension stays
      // chartable instead of being vetoed down to a table.
      if(fc instanceof DimensionFieldInfo dimFc && dimFc.getRanking() != null) {
         int n = dimFc.getRanking().getRankingN();

         if(n > 0) {
            entry.setProperty("topN", String.valueOf(n));
         }
      }

      return entry;
   }

   private void setRefType(AssetEntry entry, boolean isMeasure) {
      if(isMeasure) {
         // refType=0 carries no DIMENSION bit; CUBE_COL_TYPE=MEASURES has bit 0 set.
         // WizardRecommenderUtil.isDimension checks both conditions, so both must be false
         // for the entry to be treated as a measure.
         entry.setProperty("refType", "0");
         entry.setProperty(AssetEntry.CUBE_COL_TYPE, String.valueOf(AssetEntry.MEASURES));
      }
      else {
         entry.setProperty("refType", String.valueOf(DataRef.DIMENSION));
         entry.setProperty(AssetEntry.CUBE_COL_TYPE, String.valueOf(AssetEntry.DIMENSIONS));
      }
   }

   // ── Apply fieldConfigs to generated ChartRefs ────────────────────────────────

   private void applyFieldConfigs(VSChartInfo chartInfo, Map<String, SimpleFieldInfo> configMap) {
      int chartType = chartInfo.getChartType();

      for(ChartRef ref : chartInfo.getXFields()) {
         applyFieldConfig(ref, configMap, chartType);
      }

      for(ChartRef ref : chartInfo.getYFields()) {
         applyFieldConfig(ref, configMap, chartType);
      }

      // Aesthetics carry the same per-field config (title/format, ranking/aggregate) as x/y.
      applyAestheticFieldConfig(chartInfo.getColorField(), configMap, chartType);
      applyAestheticFieldConfig(chartInfo.getShapeField(), configMap, chartType);
      applyAestheticFieldConfig(chartInfo.getSizeField(), configMap, chartType);
      applyAestheticFieldConfig(chartInfo.getTextField(), configMap, chartType);
   }

   /**
    * Honor rows/cols explicitBindings pins on a crosstab. The recommender assigns row/col header
    * placement on its own and the pin channel into it (ChartPreference) has no rows/cols slots, so
    * such pins are otherwise dropped. This repartitions the existing header dimensions: a field
    * pinned to "rows"/"cols" moves to that group (in pin order); any unpinned field keeps its
    * current group. Design and runtime headers are kept in sync. No-op when there are no pins.
    */
   private void applyCrosstabHeaderPins(VSCrosstabInfo cinfo, List<ExplicitBinding> bindings) {
      List<String> rowPins = pinnedHeaderFields(bindings, "rows");
      List<String> colPins = pinnedHeaderFields(bindings, "cols");

      if(rowPins.isEmpty() && colPins.isEmpty()) {
         return;
      }

      DataRef[][] design = repartitionHeaders(
         cinfo.getDesignRowHeaders(), cinfo.getDesignColHeaders(), rowPins, colPins);
      cinfo.setDesignRowHeaders(design[0]);
      cinfo.setDesignColHeaders(design[1]);

      DataRef[] rtRows = cinfo.getRuntimeRowHeaders();
      DataRef[] rtCols = cinfo.getRuntimeColHeaders();

      // Runtime headers are normally re-derived from design at execution, but update them in
      // lockstep when already populated so the placement is correct regardless of execution path.
      if(rtRows != null && rtRows.length > 0 || rtCols != null && rtCols.length > 0) {
         DataRef[][] runtime = repartitionHeaders(rtRows, rtCols, rowPins, colPins);
         cinfo.setRuntimeRowHeaders(runtime[0]);
         cinfo.setRuntimeColHeaders(runtime[1]);
      }
   }

   /** Field names pinned to the given crosstab role, in binding order (deduplicated). */
   static List<String> pinnedHeaderFields(List<ExplicitBinding> bindings, String role) {
      if(bindings == null) {
         return Collections.emptyList();
      }

      List<String> fields = new ArrayList<>();

      for(ExplicitBinding b : bindings) {
         if(b != null && role.equals(b.getRole()) && !Tool.isEmptyString(b.getField()) &&
            !fields.contains(b.getField()))
         {
            fields.add(b.getField());
         }
      }

      return fields;
   }

   /**
    * Repartition crosstab header dimensions across rows/cols per the pin lists. Pinned fields are
    * placed in their target group first (in pin order); unpinned fields keep their original group
    * and relative order. Matching is case-insensitive on the dimension's column value. Returns
    * {@code {newRowHeaders, newColHeaders}}.
    */
   static DataRef[][] repartitionHeaders(DataRef[] rows, DataRef[] cols,
                                         List<String> rowPins, List<String> colPins)
   {
      LinkedHashMap<String, DataRef> byName = new LinkedHashMap<>();
      List<String> origRows = new ArrayList<>();
      List<String> origCols = new ArrayList<>();

      if(rows != null) {
         for(DataRef r : rows) {
            String key = headerFieldKey(r);
            byName.put(key, r);
            origRows.add(key);
         }
      }

      if(cols != null) {
         for(DataRef c : cols) {
            String key = headerFieldKey(c);
            byName.put(key, c);
            origCols.add(key);
         }
      }

      List<DataRef> newRows = new ArrayList<>();
      List<DataRef> newCols = new ArrayList<>();
      Set<String> placed = new HashSet<>();

      for(String f : rowPins) {
         String key = f.toLowerCase();

         if(byName.containsKey(key) && placed.add(key)) {
            newRows.add(byName.get(key));
         }
      }

      for(String f : colPins) {
         String key = f.toLowerCase();

         if(byName.containsKey(key) && placed.add(key)) {
            newCols.add(byName.get(key));
         }
      }

      // Unpinned dimensions stay in their original group, preserving order.
      for(String key : origRows) {
         if(placed.add(key)) {
            newRows.add(byName.get(key));
         }
      }

      for(String key : origCols) {
         if(placed.add(key)) {
            newCols.add(byName.get(key));
         }
      }

      return new DataRef[][] { newRows.toArray(new DataRef[0]), newCols.toArray(new DataRef[0]) };
   }

   /** Lowercased field key for a crosstab header dimension, for case-insensitive pin matching. */
   static String headerFieldKey(DataRef ref) {
      String name = null;

      if(ref instanceof VSDimensionRef dim && !Tool.isEmptyString(dim.getGroupColumnValue())) {
         name = dim.getGroupColumnValue();
      }
      else if(ref != null) {
         name = ref.getName();
      }

      return name == null ? "" : name.toLowerCase();
   }

   private void applyAestheticFieldConfig(AestheticRef aestheticRef,
                                          Map<String, SimpleFieldInfo> configMap, int chartType)
   {
      if(aestheticRef != null && aestheticRef.getDataRef() instanceof ChartRef ref) {
         applyFieldConfig(ref, configMap, chartType);
      }
   }

   // ── Post-selection readability warnings ──────────────────────────────────────

   /**
    * Adds a warning {@link CreateViewsheetResult.BindingNote} for each PINNED aesthetic dimension
    * whose distinct-value count exceeds the chart's cap for that slot. Caps come from
    * {@link ChartTypeFilter#aestheticCaps(VSChartInfo)} (index 0=color, 1=shape, 2=size) and
    * cardinality from {@link ChartRecommenderUtil#getCardinality(ChartRef, AssetEntry[])} — the same
    * source the recommender's getAestheticScore uses. Notes are appended to any existing binding notes.
    */
   private void addReadabilityWarnings(CreateViewsheetResult.FlatBinding binding, VSChartInfo ci,
                                       List<ExplicitBinding> pins, AssetEntry[] entries)
   {
      int[] caps = ChartTypeFilter.aestheticCaps(ci);
      List<CreateViewsheetResult.BindingNote> notes = new ArrayList<>();

      addCapWarning(notes, "color", ci.getColorField(), caps[0], pins, entries);
      addCapWarning(notes, "shape", ci.getShapeField(), caps[1], pins, entries);
      addCapWarning(notes, "size", ci.getSizeField(), caps[2], pins, entries);

      if(!notes.isEmpty()) {
         List<CreateViewsheetResult.BindingNote> existing = binding.getNotes();

         if(existing != null && !existing.isEmpty()) {
            List<CreateViewsheetResult.BindingNote> merged = new ArrayList<>(existing);
            merged.addAll(notes);
            binding.setNotes(merged);
         }
         else {
            binding.setNotes(notes);
         }
      }
   }

   private void addCapWarning(List<CreateViewsheetResult.BindingNote> notes, String slot,
                              AestheticRef aref, int cap, List<ExplicitBinding> pins,
                              AssetEntry[] entries)
   {
      if(aref == null || !(aref.getDataRef() instanceof VSChartDimensionRef dim)) {
         return;
      }

      String field = WizardRecommenderUtil.getChartRefFieldName(dim);
      boolean pinned = pins.stream()
         .anyMatch(p -> slot.equals(p.getRole()) && Objects.equals(field, p.getField()));

      if(!pinned) {
         return;
      }

      int n = ChartRecommenderUtil.getCardinality(dim, entries);

      if(n > cap) {
         notes.add(new CreateViewsheetResult.BindingNote(field, slot, true, "warning",
            "cardinality " + n + " exceeds " + slot + " cap " + cap +
            "; legend may be unreadable — consider a roll-up"));
      }
   }

   private void applyFieldConfig(ChartRef ref, Map<String, SimpleFieldInfo> configMap,
                                  int chartType) {
      String fieldName = WizardRecommenderUtil.getChartRefFieldName(ref);
      SimpleFieldInfo fc = configMap.get(fieldName);

      if(fc == null) {
         return;
      }

      if(ref instanceof VSChartDimensionRef dim) {
         if(fc instanceof DimensionFieldInfo dimFc) {
            if(dimFc.getRanking() != null) {
               Ranking r = dimFc.getRanking();
               dim.setRankingOptionValue(String.valueOf(r.getOptionValue()));
               dim.setRankingNValue(String.valueOf(r.getRankingN()));
               dim.setRankingColValue(r.getRankingCol());
               // Group non-ranked values as "Others" vs. discard them. Only meaningful when
               // ranking is active, so it lives inside the ranking block.
               dim.setGroupOthersValue(String.valueOf(r.isGroupOthers()));
            }

            applyDateGroup(dim, dimFc);

            if(dimFc.isNumericBin()) {
               WizardRecommenderUtil.applyNumericBin(dim);
            }

            // Aggregate column to sort bars by; only consulted when order is value-based
            // (17 value-asc / 18 value-desc). Harmless to set for other orders.
            if(dimFc.getSortByCol() != null && !dimFc.getSortByCol().isEmpty()) {
               dim.setSortByColValue(dimFc.getSortByCol());
            }
         }

         if(fc.getOrder() != null) {
            dim.setOrder(fc.getOrder());
         }

         if(fc instanceof DimensionFieldInfo dimFc2 && dimFc2.getManualOrder() != null &&
            !dimFc2.getManualOrder().isEmpty())
         {
            java.util.List<String> orderedList =
               new java.util.ArrayList<>(dimFc2.getManualOrder());
            // Funnel Y-axis is inverted (index 0 renders at bottom, last at top).
            // Reverse so the caller's intuitive top→bottom list displays correctly.
            if(GraphTypes.isFunnel(chartType)) {
               java.util.Collections.reverse(orderedList);
            }
            dim.setManualOrderList(orderedList);
         }
      }
      else if(ref instanceof VSChartAggregateRef agg) {
         if(fc instanceof MeasureFieldInfo meaFc) {
            if(meaFc.getAggregateFormula() != null) {
               agg.setFormulaValue(meaFc.getAggregateFormula());
            }

            if(meaFc.getCalculateInfo() != null) {
               Calculator calc = meaFc.getCalculateInfo().toCalculator();

               if(calc != null) {
                  agg.setCalculator(calc);
               }
            }

            // Apply discrete (plot un-aggregated) and secondaryY (secondary Y axis) INDEPENDENTLY,
            // matching the interactive editor (ChartAggregateInfoFactory.updateAggregateRef). The
            // invalid both-true combination is rejected upstream (fail-loud in the plugin's
            // normalizeFieldConfigs), so this path must not silently normalize a value away — a client
            // doing get_current_chart_state -> edit -> re-apply would otherwise lose a value here that
            // the editor would have kept.
            agg.setDiscrete(meaFc.isDiscrete());
            agg.setSecondaryY(meaFc.isSecondaryY());
         }
      }

      // Common to dimensions and measures: a display title (axis/legend/series label) and a
      // number/date display format. Applies to whichever ref kind matched above.
      applyTitleAndFormat(ref, fc);
   }

   /**
    * Apply a dimension fieldConfig's requested date-group level to the dimension.
    *
    * <p>The caller's level is authoritative and ALWAYS overrides whatever level the recommender
    * picked (it defaults a date-typed dimension to YEAR). In particular an explicit {@code "none"}
    * must reset the dimension to {@link XConstants#NONE_DATE_GROUP}. Previously a {@code "none"} was
    * silently ignored — the guard only applied non-NONE levels — so a column that the worksheet had
    * already bucketed (e.g. {@code Month(date_entered)}) was re-grouped by year at the chart layer,
    * collapsing the intended monthly series. The caller's intent was dropped with no error.
    *
    * <p>No-ops when the fieldConfig requests no level ({@code getDateGroupLevel() == null}). An
    * unsupported level string is logged and ignored rather than thrown, preserving the previous
    * lenient behavior.
    *
    * <p>Package-private and static so it can be unit-tested directly with a mocked dimension ref.
    */
   static void applyDateGroup(VSDimensionRef dim, DimensionFieldInfo dimFc) {
      if(dimFc.getDateGroupLevel() == null) {
         return;
      }

      try {
         int level = WizDateLevelUtil.getDateGroupLevel(dimFc.getDateGroupLevel());
         // Always apply — an explicit "none" (NONE_DATE_GROUP) must override the recommender's
         // default level, not be silently skipped.
         dim.setDateLevelValue(String.valueOf(level));
         dim.setTimeSeries(dimFc.isTimeSeries());
      }
      catch(IllegalArgumentException e) {
         LOG.warn("Ignoring unsupported dateGroupLevel '{}' for field '{}'",
                  dimFc.getDateGroupLevel(), dimFc.getField());
      }
   }

   /**
    * Applies the field's display title (as the ref caption) and number/date format (as the ref's
    * text format) when set in the field config. The format type is inferred from the field's data
    * type: date types use a DateFormat pattern, numeric types a DecimalFormat pattern; other types
    * are left unformatted (a pattern there would be ambiguous).
    */
   private void applyTitleAndFormat(ChartRef ref, SimpleFieldInfo fc) {
      String title = fc.getTitle();

      if(title != null && !title.isEmpty()) {
         if(ref instanceof VSDimensionRef dim) {
            dim.setCaption(title);
         }
         else if(ref instanceof VSAggregateRef agg) {
            agg.setCaption(title);
         }
      }

      String format = fc.getFormat();

      if(format != null && !format.isEmpty()) {
         String dtype = fc.getType();
         String fmtType = XSchema.isDateType(dtype) ? XConstants.DATE_FORMAT
            : XSchema.isNumericType(dtype) ? XConstants.DECIMAL_FORMAT
            : null;

         if(fmtType != null) {
            CompositeTextFormat tf = ref.getTextFormat();

            if(tf != null) {
               tf.setFormat(new XFormatInfo(fmtType, format));
            }
            else {
               LOG.warn("Field '{}' has no text format; format '{}' not applied", fc.getField(), format);
            }
         }
         else {
            LOG.warn("Ignoring format '{}' for non-date/non-numeric field '{}'", format, fc.getField());
         }
      }
   }

   // ── Bind-column selection ────────────────────────────────────────────────────

   /**
    * Decide which worksheet columns autoBinding may bind.
    * When fieldConfigs is non-empty it is the authoritative column list: only columns
    * whose name appears as a key in configMap are bound. A fieldConfig naming a column
    * that does not exist in the worksheet is an error (silently binding everything
    * produced junk measures). When fieldConfigs is empty, all visible columns are eligible.
    *
    * <p>Columns are matched by their display name ({@link ColumnRef#getDisplayName()} =
    * alias when set, else attribute) — the same name {@code buildEntryFromColumn} uses to
    * name the bound entry and the name the caller sees. This matters for aggregate-output
    * columns: an aggregate ColumnRef's {@code getAttribute()} returns the underlying base
    * column (e.g. {@code growth_pct}) while its alias carries the output name (e.g.
    * {@code growth_rate}); matching on the attribute alone would reject the alias the caller
    * (correctly) supplies in fieldConfigs.
    */
   static List<ColumnRef> selectBindColumns(List<ColumnRef> worksheetColumns,
                                            Map<String, SimpleFieldInfo> configMap)
   {
      if(!configMap.isEmpty()) {
         Set<String> available = worksheetColumns.stream()
            .map(ColumnRef::getDisplayName)
            .collect(Collectors.toCollection(LinkedHashSet::new));
         List<String> missing = configMap.keySet().stream()
            .filter(k -> !available.contains(k))
            .sorted()
            .collect(Collectors.toList());

         if(!missing.isEmpty()) {
            throw new IllegalArgumentException(
               "Unknown field(s) in fieldConfigs: " + missing +
               ". Available worksheet columns: " + available);
         }

         return worksheetColumns.stream()
            .filter(c -> configMap.containsKey(c.getDisplayName()))
            .collect(Collectors.toList());
      }

      return worksheetColumns;
   }

   // ── Chart preference helpers ──────────────────────────────────────────────────

   // Slot vocabularies. A role appearing in NEITHER set is an unknown slot.
   // Chart slots (x/y/group/color/shape/size/text) are the ones the constraints filter enforces;
   // rows/cols/aggregates/details are crosstab/table roles that we ACCEPT as known (so a
   // crosstab-role pin does not 400 as "unknown") even though the chart filter does not enforce them.
   private static final Set<String> DIMENSION_SLOTS =
      Set.of("x", "y", "color", "shape", "size", "text", "group", "rows", "cols", "details");
   private static final Set<String> MEASURE_SLOTS =
      Set.of("x", "y", "color", "size", "text", "aggregates");

   /**
    * Validates explicit binding pins up front, before recommendation, so impossible pins fail fast
    * with a structured 400 instead of silently producing a different chart. No-op on empty pins.
    *
    * <p>Field existence is checked against {@code configMap} when field configs are provided
    * (plugin path), else against the worksheet {@code entries} (chat-app path). Measure/dimension
    * slot-compatibility is only enforced when the field's type is known from its config; a field
    * validated against entries alone has unknown measure/dimension role here, so the type check is
    * skipped (the recommender's constraints filter still rejects truly impossible placements).
    */
   static void validateExplicitBindings(List<ExplicitBinding> bindings,
                                        Map<String, SimpleFieldInfo> configMap,
                                        AssetEntry[] entries)
   {
      if(bindings == null || bindings.isEmpty()) {
         return;
      }

      Set<String> entryFieldNames = entries == null ? Collections.emptySet()
         : Arrays.stream(entries)
            .map(WizardRecommenderUtil::getFieldName)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

      for(ExplicitBinding eb : bindings) {
         String role = eb.getRole();
         String field = eb.getField();

         if(role == null) {
            throw new UnsatisfiableBindingException(null, field, "missing slot/role");
         }

         if(field == null) {
            throw new UnsatisfiableBindingException(role, null, "missing field");
         }

         // unknown slot: role belongs to neither the dimension- nor the measure-capable set.
         if(!DIMENSION_SLOTS.contains(role) && !MEASURE_SLOTS.contains(role)) {
            throw new UnsatisfiableBindingException(role, field, "unknown slot");
         }

         SimpleFieldInfo fc = configMap != null ? configMap.get(field) : null;
         boolean hasConfigs = configMap != null && !configMap.isEmpty();

         // Field existence: prefer fieldConfigs (plugin path), else the worksheet entries.
         if(hasConfigs) {
            if(fc == null) {
               throw new UnsatisfiableBindingException(role, field, "field not in fieldConfigs");
            }
         }
         else if(!entryFieldNames.contains(field)) {
            throw new UnsatisfiableBindingException(role, field, "field not in worksheet columns");
         }

         // Measure/dimension slot-compatibility — only when the field's type is known from its config.
         if(fc instanceof MeasureFieldInfo && !MEASURE_SLOTS.contains(role)) {
            throw new UnsatisfiableBindingException(
               role, field, "measure cannot be placed in dimension-only slot");
         }

         if(fc instanceof DimensionFieldInfo && !DIMENSION_SLOTS.contains(role)) {
            throw new UnsatisfiableBindingException(
               role, field, "dimension cannot be placed in measure-only slot");
         }
      }
   }

   private static ChartPreference buildChartPreference(List<ExplicitBinding> bindings) {
      if(bindings == null || bindings.isEmpty()) {
         return null;
      }

      Map<String, Set<String>> slotFields = new HashMap<>();

      for(ExplicitBinding eb : bindings) {
         if(eb.getRole() != null && eb.getField() != null) {
            slotFields.computeIfAbsent(eb.getRole(), k -> new LinkedHashSet<>())
               .add(eb.getField());
         }
      }

      return slotFields.isEmpty() ? null : new ChartPreference(slotFields);
   }

   private RecommendedVisualization buildTableVisualization(ColumnSelection columns,
                                                            AssetEntry[] entries,
                                                            String worksheetPath)
   {
      TableBinding binding = new TableBinding();

      if(columns != null && entries != null) {
         Map<String, AssetEntry> entryByName = Arrays.stream(entries)
            .collect(Collectors.toMap(WizardRecommenderUtil::getFieldName, e -> e, (a, b) -> a));
         List<SimpleFieldInfo> details = new ArrayList<>();

         for(int i = 0; i < columns.getAttributeCount(); i++) {
            // entryByName is keyed on WizardRecommenderUtil.getFieldName(entry), which is the
            // alias-preferred name buildEntryFromColumn assigns. Look up by the same display name
            // (alias when set, else attribute) so aliased columns — e.g. aggregate outputs — are
            // not silently dropped from the table detail list.
            DataRef colRef = columns.getAttribute(i);
            String name = colRef instanceof ColumnRef cr ? cr.getDisplayName() : colRef.getAttribute();
            AssetEntry entry = entryByName.get(name);

            if(entry != null) {
               details.add(entryToSimpleFieldInfo(entry));
            }
         }

         binding.setDetails(details);
      }

      RecommendedVisualization rec = new RecommendedVisualization();
      rec.setVisualizationType("table");
      rec.setConfig(wrapInConfig(binding, worksheetPath));
      return rec;
   }

   // ── Factory output → BindingInfo conversion ──────────────────────────────────

   private CrosstabBinding crosstabInfoToBinding(VSCrosstabInfo info) {
      CrosstabBinding binding = new CrosstabBinding();

      List<DimensionFieldInfo> rows = dataRefsToFields(info.getDesignRowHeaders());
      if(!rows.isEmpty()) {
         binding.setRows(rows);
      }

      List<DimensionFieldInfo> cols = dataRefsToFields(info.getDesignColHeaders());
      if(!cols.isEmpty()) {
         binding.setCols(cols);
      }

      List<MeasureFieldInfo> aggs = Arrays.stream(info.getDesignAggregates())
         .filter(r -> r instanceof VSAggregateRef)
         .map(r -> WizFieldInfoFactory.createCrosstabMeasureFieldInfo((VSAggregateRef) r))
         .collect(Collectors.toList());
      if(!aggs.isEmpty()) {
         binding.setAggregates(aggs);
      }

      return binding;
   }

   private List<DimensionFieldInfo> dataRefsToFields(DataRef[] refs) {
      if(refs == null) {
         return Collections.emptyList();
      }
      return Arrays.stream(refs)
         .filter(r -> r instanceof VSDimensionRef)
         .map(r -> WizFieldInfoFactory.createCrosstabDimensionFieldInfo((VSDimensionRef) r))
         .collect(Collectors.toList());
   }

   private MeasureFieldInfo dataRefToMeasureFieldInfo(DataRef ref) {
      MeasureFieldInfo info = new MeasureFieldInfo();
      info.setField(ref.getAttribute());
      info.setType(ref.getDataType());

      if(ref instanceof VSAggregateRef agg) {
         info.setCalculateInfo(CalculateInfo.createCalcInfo(agg.getCalculator()));
      }

      return info;
   }

   // ── ChartInfo → VisualizationConfig (design doc §4.4) ───────────────────────

   private VisualizationConfig convertToChartConfig(ChartInfo info, String worksheetPath) {
      ChartBinding binding = new ChartBinding();

      List<SimpleFieldInfo> x = chartRefsToFieldInfos(info.getXFields());
      if(!x.isEmpty()) {
         binding.setX(x);
      }

      List<SimpleFieldInfo> y = chartRefsToFieldInfos(info.getYFields());
      if(!y.isEmpty()) {
         binding.setY(y);
      }

      List<SimpleFieldInfo> group = chartRefsToFieldInfos(info.getGroupFields());
      if(!group.isEmpty()) {
         binding.setGroup(group);
      }

      if(info.getColorField() != null) {
         binding.setColor(chartRefToFieldInfo(info.getColorField().getDataRef()));
      }

      if(info.getShapeField() != null) {
         binding.setShape(chartRefToFieldInfo(info.getShapeField().getDataRef()));
      }

      if(info.getSizeField() != null) {
         binding.setSize(chartRefToFieldInfo(info.getSizeField().getDataRef()));
      }

      if(info.getTextField() != null) {
         binding.setText(chartRefToFieldInfo(info.getTextField().getDataRef()));
      }

      return wrapInConfig(binding, worksheetPath);
   }

   private List<SimpleFieldInfo> chartRefsToFieldInfos(ChartRef[] refs) {
      if(refs == null || refs.length == 0) {
         return Collections.emptyList();
      }

      return Arrays.stream(refs)
         .map(this::chartRefToFieldInfo)
         .filter(Objects::nonNull)
         .collect(Collectors.toList());
   }

   private SimpleFieldInfo chartRefToFieldInfo(DataRef ref) {
      if(ref == null) {
         return null;
      }

      if(ref instanceof VSAggregateRef agg) {
         return WizFieldInfoFactory.createChartMeasureFieldInfo(agg);
      }

      if(ref instanceof VSDimensionRef dim) {
         return WizFieldInfoFactory.createChartDimensionFieldInfo(dim);
      }

      SimpleFieldInfo info = new SimpleFieldInfo();
      info.setField(ref.getAttribute());
      return info;
   }

   // ── Entry → FieldInfo conversion ─────────────────────────────────────────────

   private SimpleFieldInfo entryToSimpleFieldInfo(AssetEntry entry) {
      return !WizardRecommenderUtil.isDimension(entry)
         ? entryToMeasureFieldInfo(entry)
         : entryToDimensionFieldInfo(entry);
   }

   private MeasureFieldInfo entryToMeasureFieldInfo(AssetEntry entry) {
      MeasureFieldInfo info = new MeasureFieldInfo();
      info.setField(WizardRecommenderUtil.getFieldName(entry));
      info.setType(entry.getProperty("dtype"));
      return info;
   }

   private DimensionFieldInfo entryToDimensionFieldInfo(AssetEntry entry) {
      DimensionFieldInfo info = new DimensionFieldInfo();
      info.setField(WizardRecommenderUtil.getFieldName(entry));
      info.setType(entry.getProperty("dtype"));
      return info;
   }

   // ── Primary recommendation selection ──────────────────────────────────────────

   /**
    * Selects the primary recommendation and sets {@code selectedIndex} on any
    * {@link VSChartRecommendation} so that {@code refreshVisualizationBinding} can
    * call {@code updatePrimaryAssembly} with the correct sub-type.
    */
   private VSObjectRecommendation selectPrimaryRecommendation(VSRecommendationModel model,
                                                               String vizType,
                                                               List<ExplicitBinding> explicitBindings,
                                                               String intentCategory)
   {
      List<VSObjectRecommendation> recs = model.getRecommendationList();
      boolean pinsPresent = explicitBindings != null && !explicitBindings.isEmpty();

      if(vizType != null) {
         VSObjectRecommendation found = findRecByType(vizType, model, pinsPresent);

         if(found == null && !recs.isEmpty()) {
            LOG.debug("Requested vizType '{}' not found in recommendations; returning top recommendation", vizType);
         }

         // When pins are present and the requested type has no pin-satisfying candidate, fall back to
         // the best SATISFYING candidate (selectTopRecommendation walks prefInfos first) rather than
         // the unconstrained chartInfos — never silently bypass the pins. The selectionNote on the
         // response (driven by requestedTypeAvailable, also pin-aware) reports the substitution.
         return found != null ? found : selectTopRecommendation(recs, intentCategory);
      }

      if(explicitBindings != null && !explicitBindings.isEmpty()) {
         String inferredType = inferNonChartVizType(explicitBindings);

         if(inferredType != null) {
            VSObjectRecommendation found = findRecByType(inferredType, model, pinsPresent);

            if(found != null) {
               return found;
            }
         }
      }

      return selectTopRecommendation(recs, intentCategory);
   }

   /**
    * Finds the recommendation matching {@code vizType} and sets {@code selectedIndex}
    * on {@link VSChartRecommendation} when a specific chart sub-type is requested.
    */
   private VSObjectRecommendation findRecByType(String vizType, VSRecommendationModel model,
                                                boolean pinsPresent)
   {
      for(VSObjectRecommendation rec : model.getRecommendationList()) {
         if(isChartVizType(vizType) && rec instanceof VSChartRecommendation cr) {
            if(setChartIndexForType(cr, vizType, pinsPresent)) {
               return cr;
            }
         }
         else if("crosstab".equals(vizType) && rec instanceof VSCrosstabRecommendation cr) {
            if(cr.getCrosstabInfo() != null) {
               return cr;
            }
         }
         else if("table".equals(vizType) && rec instanceof VSTableRecommendation) {
            return rec;
         }
         else if("gauge".equals(vizType) && rec instanceof VSGaugeRecommendation gr) {
            if(gr.getDataRef() != null) {
               return gr;
            }
         }
         else if("text".equals(vizType) && rec instanceof VSTextRecommendation tr) {
            if(tr.getDataRef() != null) {
               return tr;
            }
         }
      }

      return null;
   }

   /**
    * Returns the first non-filter recommendation, applying {@code intentCategory} scoring
    * when the top recommendation is a chart.
    */
   private VSObjectRecommendation selectTopRecommendation(List<VSObjectRecommendation> recs,
                                                           String intentCategory)
   {
      for(VSObjectRecommendation rec : recs) {
         if(rec instanceof VSChartRecommendation vcr) {
            List<ChartCombinationUtil.ScoredInfo> prefInfos = vcr.getPrefInfos();
            ChartInfo bestInfo = selectBestChartInfo(prefInfos, intentCategory);

            if(bestInfo != null) {
               setChartSelectedIndex(vcr, bestInfo);
               return vcr;
            }

            List<ChartInfo> chartInfos = vcr.getChartInfos();

            if(chartInfos != null && !chartInfos.isEmpty()) {
               vcr.setSelectedIndex(0);
               return vcr;
            }

            continue;
         }

         if(!(rec instanceof VSFilterRecommendation) && isValidNonChartRec(rec)) {
            return rec;
         }
      }

      return null;
   }

   /**
    * Sets {@code selectedIndex} on {@code vcr} for the best chart matching {@code explicitChartType}.
    * When {@code pinsPresent}, the unconstrained {@code chartInfos} fallback is skipped: prefInfos
    * already contains ONLY pin-satisfying candidates, so falling through to chartInfos would
    * silently bypass the pins. A false return then lets the caller pick the best satisfying candidate.
    */
   private boolean setChartIndexForType(VSChartRecommendation vcr, String explicitChartType,
                                        boolean pinsPresent)
   {
      List<ChartCombinationUtil.ScoredInfo> prefInfos = vcr.getPrefInfos();
      List<ChartInfo> chartInfos = vcr.getChartInfos();
      int chartInfosSize = chartInfos != null ? chartInfos.size() : 0;

      if(prefInfos != null) {
         for(int i = 0; i < prefInfos.size(); i++) {
            if(chartTypeMatches(explicitChartType, prefInfos.get(i).getInfo())) {
               vcr.setSelectedIndex(chartInfosSize + i);
               return true;
            }
         }
      }

      // Pins present: do NOT scan unconstrained chartInfos (silent-bypass guard).
      if(pinsPresent) {
         return false;
      }

      if(chartInfos != null) {
         for(int i = 0; i < chartInfosSize; i++) {
            if(chartTypeMatches(explicitChartType, chartInfos.get(i))) {
               vcr.setSelectedIndex(i);
               return true;
            }
         }
      }

      return false;
   }

   /** Sets {@code selectedIndex} on {@code vcr} by identity-matching {@code info} in chartInfos then prefInfos. */
   private static void setChartSelectedIndex(VSChartRecommendation vcr, ChartInfo info) {
      List<ChartInfo> chartInfos = vcr.getChartInfos();
      int chartInfosSize = chartInfos != null ? chartInfos.size() : 0;

      if(chartInfos != null) {
         for(int i = 0; i < chartInfosSize; i++) {
            if(chartInfos.get(i) == info) {
               vcr.setSelectedIndex(i);
               return;
            }
         }
      }

      List<ChartCombinationUtil.ScoredInfo> prefInfos = vcr.getPrefInfos();

      if(prefInfos != null) {
         for(int i = 0; i < prefInfos.size(); i++) {
            if(prefInfos.get(i).getInfo() == info) {
               vcr.setSelectedIndex(chartInfosSize + i);
               return;
            }
         }
      }
   }

   /** Returns the best {@link ChartInfo} from {@code prefInfos}, filtered by {@code intentCategory}. */
   private static ChartInfo selectBestChartInfo(List<ChartCombinationUtil.ScoredInfo> prefInfos,
                                                String intentCategory)
   {
      if(prefInfos == null || prefInfos.isEmpty()) {
         return null;
      }

      ChartInfo highestScored = prefInfos.stream()
         .max(Comparator.comparingInt(ChartCombinationUtil.ScoredInfo::getScore))
         .map(ChartCombinationUtil.ScoredInfo::getInfo)
         .orElse(null);

      Set<Integer> categoryTypes = intentCategory != null
         ? INTENT_CATEGORY_CHART_TYPES.get(intentCategory) : null;

      if(categoryTypes != null && !categoryTypes.isEmpty()) {
         return prefInfos.stream()
            .filter(si -> categoryTypes.contains(si.getInfo().getChartType()))
            .max(Comparator.comparingInt(ChartCombinationUtil.ScoredInfo::getScore))
            .map(ChartCombinationUtil.ScoredInfo::getInfo)
            .orElse(highestScored);
      }

      return highestScored;
   }

   private static boolean isValidNonChartRec(VSObjectRecommendation rec) {
      if(rec instanceof VSCrosstabRecommendation cr) return cr.getCrosstabInfo() != null;
      if(rec instanceof VSTableRecommendation) return true;
      if(rec instanceof VSGaugeRecommendation gr) return gr.getDataRef() != null;
      if(rec instanceof VSTextRecommendation tr) return tr.getDataRef() != null;
      return false;
   }

   /** Converts a selected recommendation to a {@link RecommendedVisualization} for the client response. */
   private RecommendedVisualization recommendationToVisualization(VSObjectRecommendation rec,
                                                                   AssetEntry[] entries,
                                                                   String worksheetPath)
   {
      if(rec == null) {
         return null;
      }

      if(rec instanceof VSChartRecommendation vcr) {
         ChartInfo info = getSelectedChartInfo(vcr);
         return info != null ? toRecommendedVisualization(info, worksheetPath) : null;
      }

      if(rec instanceof VSCrosstabRecommendation cr && cr.getCrosstabInfo() != null) {
         RecommendedVisualization rv = new RecommendedVisualization();
         rv.setVisualizationType("crosstab");
         rv.setConfig(wrapInConfig(crosstabInfoToBinding(cr.getCrosstabInfo()), worksheetPath));
         return rv;
      }

      if(rec instanceof VSTableRecommendation tr) {
         return buildTableVisualization(tr.getColumns(), entries, worksheetPath);
      }

      if(rec instanceof VSOutputRecommendation out) {
         String vizType = rec instanceof VSGaugeRecommendation ? "gauge" : "text";
         OutputBinding ob = new OutputBinding();
         ob.setField(dataRefToMeasureFieldInfo(out.getDataRef()));
         RecommendedVisualization rv = new RecommendedVisualization();
         rv.setVisualizationType(vizType);
         rv.setConfig(wrapInConfig(ob, worksheetPath));
         return rv;
      }

      return null;
   }

   /** Returns the {@link ChartInfo} at {@code vcr.selectedIndex}, handling the combined chartInfos+prefInfos index space. */
   private static ChartInfo getSelectedChartInfo(VSChartRecommendation vcr) {
      int idx = vcr.getSelectedIndex();
      List<ChartInfo> chartInfos = vcr.getChartInfos();
      int chartInfosSize = chartInfos != null ? chartInfos.size() : 0;

      if(idx < chartInfosSize) {
         return chartInfos.get(idx);
      }

      List<ChartCombinationUtil.ScoredInfo> prefInfos = vcr.getPrefInfos();

      if(prefInfos != null) {
         int prefIdx = idx - chartInfosSize;

         if(prefIdx >= 0 && prefIdx < prefInfos.size()) {
            return prefInfos.get(prefIdx).getInfo();
         }
      }

      return chartInfos != null && !chartInfos.isEmpty() ? chartInfos.get(0) : null;
   }

   private String inferNonChartVizType(List<ExplicitBinding> bindings) {
      int chartScore = countRoleMatches(bindings, CHART_ROLES);

      if(countRoleMatches(bindings, CROSSTAB_ROLES) > chartScore) {
         return "crosstab";
      }
      if(countRoleMatches(bindings, TABLE_ROLES) > chartScore) {
         return "table";
      }

      return null;
   }

   private int countRoleMatches(List<ExplicitBinding> bindings, Set<String> roles) {
      int count = 0;

      for(ExplicitBinding eb : bindings) {
         if(roles.contains(eb.getRole())) {
            count++;
         }
      }

      return count;
   }

   private static final Set<String> CHART_ROLES = Set.of("x", "y", "group", "color", "shape", "size", "text");
   private static final Set<String> CROSSTAB_ROLES = Set.of("rows", "cols", "aggregates");
   private static final Set<String> TABLE_ROLES = Set.of("details");

   private RecommendedVisualization toRecommendedVisualization(ChartInfo info, String worksheetPath) {
      String vizType = getChartTypeString(info.getChartType());

      if(vizType == null) {
         return null;
      }

      RecommendedVisualization rec = new RecommendedVisualization();
      rec.setVisualizationType(vizType);
      rec.setConfig(convertToChartConfig(info, worksheetPath));
      return rec;
   }


   private static final Set<String> NON_CHART_VIZ_TYPES = Set.of("table", "crosstab", "gauge", "text");

   /**
    * Maps each intent category to the set of {@link GraphTypes} chart-type constants that best
    * express that intent.  Categories are not mutually exclusive — a chart type may appear in
    * multiple categories.  "other" is absent intentionally: it acts as the no-filter fallback.
    */
   private static final Map<String, Set<Integer>> INTENT_CATEGORY_CHART_TYPES;

   static {
      Map<String, Set<Integer>> m = new HashMap<>();

      // comparison — values side-by-side across categories
      m.put("comparison", Set.of(
         GraphTypes.CHART_BAR, GraphTypes.CHART_BAR_STACK,
         GraphTypes.CHART_3D_BAR, GraphTypes.CHART_3D_BAR_STACK,
         GraphTypes.CHART_RADAR, GraphTypes.CHART_FILL_RADAR,
         GraphTypes.CHART_MEKKO,
         GraphTypes.CHART_AREA, GraphTypes.CHART_AREA_STACK
      ));

      // trend — change over time
      m.put("trend", Set.of(
         GraphTypes.CHART_LINE, GraphTypes.CHART_LINE_STACK,
         GraphTypes.CHART_AREA, GraphTypes.CHART_AREA_STACK,
         GraphTypes.CHART_STEP, GraphTypes.CHART_STEP_STACK,
         GraphTypes.CHART_STEP_AREA, GraphTypes.CHART_STEP_AREA_STACK,
         GraphTypes.CHART_JUMP,
         GraphTypes.CHART_WATERFALL
      ));

      // distribution — spread / frequency of values
      m.put("distribution", Set.of(
         GraphTypes.CHART_BOXPLOT,
         GraphTypes.CHART_POINT, GraphTypes.CHART_POINT_STACK,
         GraphTypes.CHART_SCATTER_CONTOUR,
         GraphTypes.CHART_BAR
      ));

      // proportion — part-to-whole relationships
      m.put("proportion", Set.of(
         GraphTypes.CHART_PIE, GraphTypes.CHART_3D_PIE, GraphTypes.CHART_DONUT,
         GraphTypes.CHART_TREEMAP, GraphTypes.CHART_SUNBURST,
         GraphTypes.CHART_CIRCLE_PACKING, GraphTypes.CHART_ICICLE,
         GraphTypes.CHART_MEKKO,
         GraphTypes.CHART_BAR_STACK, GraphTypes.CHART_AREA_STACK
      ));

      // relationship — correlations, connections, dependencies
      m.put("relationship", Set.of(
         GraphTypes.CHART_POINT, GraphTypes.CHART_POINT_STACK,
         GraphTypes.CHART_SCATTER_CONTOUR,
         GraphTypes.CHART_NETWORK, GraphTypes.CHART_CIRCULAR,
         GraphTypes.CHART_TREE
      ));

      // ranking — ordered comparison, top-N
      m.put("ranking", Set.of(
         GraphTypes.CHART_BAR, GraphTypes.CHART_BAR_STACK,
         GraphTypes.CHART_PARETO,
         GraphTypes.CHART_WATERFALL
      ));

      // geospatial — location-based data
      m.put("geospatial", Set.of(
         GraphTypes.CHART_MAP,
         GraphTypes.CHART_MAP_CONTOUR
      ));

      INTENT_CATEGORY_CHART_TYPES = Collections.unmodifiableMap(m);
   }

   private boolean isChartVizType(String vizType) {
      return vizType != null && !NON_CHART_VIZ_TYPES.contains(vizType);
   }

   /**
    * Flattens the recommendation list into a feasibility-filtered, named chart-type menu (highest
    * fit first). Chart candidates and their scores come from each {@link VSChartRecommendation}'s
    * prefInfos; table/crosstab/gauge/text appear once each when feasible. Scores are normalized to
    * [0,1] for ordering only. Every entry is a type the recommender found feasible for the fields.
    */
   private List<ChartTypeCandidate> buildChartTypeCandidates(List<VSObjectRecommendation> recommendations) {
      if(recommendations == null || recommendations.isEmpty()) {
         return Collections.emptyList();
      }

      // type name -> best raw score seen for that type (preserve first-seen order for ties)
      Map<String, Integer> best = new LinkedHashMap<>();

      for(VSObjectRecommendation rec : recommendations) {
         if(rec instanceof VSChartRecommendation vcr) {
            List<ChartCombinationUtil.ScoredInfo> prefInfos = vcr.getPrefInfos();

            if(prefInfos != null) {
               for(ChartCombinationUtil.ScoredInfo si : prefInfos) {
                  String type = getChartTypeString(si.getInfo().getChartType());

                  if(type != null) {
                     best.merge(type, si.getScore(), Math::max);
                  }
               }
            }
         }
         else if(rec instanceof VSCrosstabRecommendation cr && cr.getCrosstabInfo() != null) {
            best.putIfAbsent("crosstab", 0);
         }
         else if(rec instanceof VSTableRecommendation) {
            best.putIfAbsent("table", 0);
         }
         else if(rec instanceof VSGaugeRecommendation gr && gr.getDataRef() != null) {
            best.putIfAbsent("gauge", 0);
         }
         else if(rec instanceof VSTextRecommendation tr && tr.getDataRef() != null) {
            best.putIfAbsent("text", 0);
         }
      }

      if(best.isEmpty()) {
         return Collections.emptyList();
      }

      int max = best.values().stream().mapToInt(Integer::intValue).max().orElse(0);
      double denom = max > 0 ? max : 1.0;

      return best.entrySet().stream()
         .map(e -> new ChartTypeCandidate(e.getKey(), Math.round((e.getValue() / denom) * 100.0) / 100.0))
         .sorted(Comparator.comparingDouble(ChartTypeCandidate::getScore).reversed())
         .collect(Collectors.toList());
   }

   /**
    * Collects the distinct-value counts the recommender sampled onto the selected dimension entries
    * (set by {@code WizardRecommenderUtil.refreshCardinalityAndHierarchy} during recommend()), keyed
    * by the same field name the client binds with. Skips entries without a cardinality property
    * (date dimensions, measures, or field sets too large for the recommender to sample).
    */
   private Map<String, Integer> buildFieldCardinalities(AssetEntry[] entries) {
      Map<String, Integer> out = new LinkedHashMap<>();

      if(entries == null) {
         return out;
      }

      for(AssetEntry entry : entries) {
         String card = entry.getProperty("cardinality");

         if(card != null) {
            try {
               out.put(WizardRecommenderUtil.getFieldName(entry), Integer.parseInt(card));
            }
            catch(NumberFormatException ignore) {
               // non-numeric cardinality property; skip
            }
         }
      }

      return out;
   }

   /**
    * Side-effect-free check of whether {@code vizType} is satisfiable by some recommendation.
    * Mirrors {@link #findRecByType} (including the donut/area aliasing in {@link #chartTypeMatches})
    * but, unlike findRecByType, does not mutate any {@code selectedIndex}. Used only to decide
    * whether a requested type was honored, so a substitution can be reported.
    */
   private boolean requestedTypeAvailable(String vizType, VSRecommendationModel model,
                                          boolean pinsPresent)
   {
      if(vizType == null || model == null) {
         return false;
      }

      for(VSObjectRecommendation rec : model.getRecommendationList()) {
         if(isChartVizType(vizType) && rec instanceof VSChartRecommendation cr) {
            List<ChartCombinationUtil.ScoredInfo> prefInfos = cr.getPrefInfos();

            if(prefInfos != null) {
               for(ChartCombinationUtil.ScoredInfo si : prefInfos) {
                  if(chartTypeMatches(vizType, si.getInfo())) {
                     return true;
                  }
               }
            }

            // Pins present: only pin-satisfying prefInfos count as "available" — the unconstrained
            // chartInfos were never eligible, so a match there is still a substitution (note it).
            if(pinsPresent) {
               continue;
            }

            List<ChartInfo> chartInfos = cr.getChartInfos();

            if(chartInfos != null) {
               for(ChartInfo ci : chartInfos) {
                  if(chartTypeMatches(vizType, ci)) {
                     return true;
                  }
               }
            }
         }
         else if("crosstab".equals(vizType) && rec instanceof VSCrosstabRecommendation cr) {
            if(cr.getCrosstabInfo() != null) {
               return true;
            }
         }
         else if("table".equals(vizType) && rec instanceof VSTableRecommendation) {
            return true;
         }
         else if("gauge".equals(vizType) && rec instanceof VSGaugeRecommendation gr) {
            if(gr.getDataRef() != null) {
               return true;
            }
         }
         else if("text".equals(vizType) && rec instanceof VSTextRecommendation tr) {
            if(tr.getDataRef() != null) {
               return true;
            }
         }
      }

      return false;
   }

   // ── Chart type string mapping (inverse of WizVsService.getChartType) ─────────

   private static String getChartTypeString(int chartType) {
      return switch(chartType) {
         case GraphTypes.CHART_BAR -> "bar";
         case GraphTypes.CHART_BAR_STACK -> "bar_stack";
         case GraphTypes.CHART_3D_BAR -> "3d_bar";
         case GraphTypes.CHART_3D_BAR_STACK -> "3d_bar_stack";
         case GraphTypes.CHART_AREA -> "area";
         case GraphTypes.CHART_AREA_STACK -> "area_stack";
         case GraphTypes.CHART_POINT -> "point";
         case GraphTypes.CHART_POINT_STACK -> "point_stack";
         case GraphTypes.CHART_STEP_AREA -> "step_area";
         case GraphTypes.CHART_STEP_AREA_STACK -> "step_area_stack";
         case GraphTypes.CHART_INTERVAL -> "interval";
         case GraphTypes.CHART_LINE -> "line";
         case GraphTypes.CHART_LINE_STACK -> "line_stack";
         case GraphTypes.CHART_STEP -> "step_line";
         case GraphTypes.CHART_STEP_STACK -> "step_line_stack";
         case GraphTypes.CHART_JUMP -> "jump_line";
         case GraphTypes.CHART_PIE -> "pie";
         case GraphTypes.CHART_3D_PIE -> "3d_pie";
         case GraphTypes.CHART_DONUT -> "donut";
         case GraphTypes.CHART_RADAR -> "radar";
         case GraphTypes.CHART_FILL_RADAR -> "filled_radar";
         case GraphTypes.CHART_SCATTER_CONTOUR -> "scatter_contour";
         case GraphTypes.CHART_STOCK -> "stock";
         case GraphTypes.CHART_CANDLE -> "candle";
         case GraphTypes.CHART_BOXPLOT -> "boxplot";
         case GraphTypes.CHART_WATERFALL -> "waterfall";
         case GraphTypes.CHART_PARETO -> "pareto";
         case GraphTypes.CHART_TREEMAP -> "treemap";
         case GraphTypes.CHART_SUNBURST -> "sunburst";
         case GraphTypes.CHART_CIRCLE_PACKING -> "circle_packing";
         case GraphTypes.CHART_ICICLE -> "icircle";
         case GraphTypes.CHART_MEKKO -> "marimekko";
         case GraphTypes.CHART_GANTT -> "gantt";
         case GraphTypes.CHART_FUNNEL -> "funnel";
         case GraphTypes.CHART_TREE -> "tree";
         case GraphTypes.CHART_NETWORK -> "network";
         case GraphTypes.CHART_CIRCULAR -> "circular_network";
         case GraphTypes.CHART_MAP_CONTOUR -> "contour_map";
         case GraphTypes.CHART_MAP -> "map";
         default -> null;
      };
   }

   /** Inverse of {@link #getChartTypeString}: a frontend chart-type name → GraphTypes constant, or
    *  -1 when the name is unknown or not a (non-geo) chart type. Map types are excluded because the
    *  geo conversion is handled separately (see {@link WizGeoService}). */
   private static int graphTypeForName(String vizType) {
      if(vizType == null) {
         return -1;
      }

      return switch(vizType) {
         case "bar" -> GraphTypes.CHART_BAR;
         case "bar_stack" -> GraphTypes.CHART_BAR_STACK;
         case "3d_bar" -> GraphTypes.CHART_3D_BAR;
         case "3d_bar_stack" -> GraphTypes.CHART_3D_BAR_STACK;
         case "area" -> GraphTypes.CHART_AREA;
         case "area_stack" -> GraphTypes.CHART_AREA_STACK;
         case "point" -> GraphTypes.CHART_POINT;
         case "point_stack" -> GraphTypes.CHART_POINT_STACK;
         case "step_area" -> GraphTypes.CHART_STEP_AREA;
         case "step_area_stack" -> GraphTypes.CHART_STEP_AREA_STACK;
         case "interval" -> GraphTypes.CHART_INTERVAL;
         case "line" -> GraphTypes.CHART_LINE;
         case "line_stack" -> GraphTypes.CHART_LINE_STACK;
         case "step_line" -> GraphTypes.CHART_STEP;
         case "step_line_stack" -> GraphTypes.CHART_STEP_STACK;
         case "jump_line" -> GraphTypes.CHART_JUMP;
         case "pie" -> GraphTypes.CHART_PIE;
         case "3d_pie" -> GraphTypes.CHART_3D_PIE;
         case "donut" -> GraphTypes.CHART_DONUT;
         case "radar" -> GraphTypes.CHART_RADAR;
         case "filled_radar" -> GraphTypes.CHART_FILL_RADAR;
         case "scatter_contour" -> GraphTypes.CHART_SCATTER_CONTOUR;
         case "stock" -> GraphTypes.CHART_STOCK;
         case "candle" -> GraphTypes.CHART_CANDLE;
         case "boxplot" -> GraphTypes.CHART_BOXPLOT;
         case "waterfall" -> GraphTypes.CHART_WATERFALL;
         case "pareto" -> GraphTypes.CHART_PARETO;
         case "treemap" -> GraphTypes.CHART_TREEMAP;
         case "sunburst" -> GraphTypes.CHART_SUNBURST;
         case "circle_packing" -> GraphTypes.CHART_CIRCLE_PACKING;
         case "icircle" -> GraphTypes.CHART_ICICLE;
         case "marimekko" -> GraphTypes.CHART_MEKKO;
         case "gantt" -> GraphTypes.CHART_GANTT;
         case "funnel" -> GraphTypes.CHART_FUNNEL;
         case "tree" -> GraphTypes.CHART_TREE;
         case "network" -> GraphTypes.CHART_NETWORK;
         case "circular_network" -> GraphTypes.CHART_CIRCULAR;
         default -> -1;
      };
   }

   /**
    * Honor an explicitly-requested chart type the recommender substituted away. The recommender
    * only surfaces types it scores as a good fit; when the user explicitly asks for a renderable
    * type it did not surface (e.g. a line over an ordered categorical axis, or a funnel), convert
    * the built binding to it via the product's {@link ChangeChartTypeProcessor} so the explicit
    * request wins instead of being silently swapped. Returns the applied chart-type name when a
    * conversion happened, otherwise {@code null} (left as the recommender's choice).
    */
   private String applyForcedChartType(ChartVSAssembly chartAsm, String vizType) {
      VSChartInfo cinfo = chartAsm.getVSChartInfo();

      if(cinfo == null) {
         return null;
      }

      int newType = graphTypeForName(vizType);

      if(newType < 0 || GraphTypes.isGeo(newType)) {
         return null;
      }

      int oldType = cinfo.getRTChartType() != 0 ? cinfo.getRTChartType() : cinfo.getChartType();

      if(oldType == newType) {
         return null;
      }

      try {
         cinfo.clearRuntime();
         VSChartInfo converted =
            (VSChartInfo) new ChangeChartTypeProcessor(oldType, newType, null, cinfo).process();

         // For line/area/point conversions, prefer the conventional vertical layout (dimension on
         // x, measure on y). The substituted type the recommender built may have been a HORIZONTAL
         // bar (measure on x, for a high-cardinality category), which after conversion reads as a
         // sideways line/area. Only swap when x is all-measure and y is all-dimension.
         if(GraphTypes.isLine(newType) || GraphTypes.isArea(newType) || GraphTypes.isPoint(newType)) {
            normalizeVerticalAxes(converted);
         }

         chartAsm.setVSChartInfo(converted);
         return getChartTypeString(newType);
      }
      catch(Exception e) {
         LOG.warn("Could not force chart type '{}' (kept recommender choice): {}", vizType, e.getMessage());
         LOG.debug("force chart type stack trace", e);
         return null;
      }
   }

   /** Swap x/y so a dimension-on-x, measure-on-y (vertical) layout is used — only when x holds
    *  exclusively measures and y exclusively dimensions (the horizontal layout a high-cardinality
    *  bar substitute produces). No-op otherwise. */
   private static void normalizeVerticalAxes(VSChartInfo info) {
      ChartRef[] x = info.getXFields();
      ChartRef[] y = info.getYFields();

      if(x.length == 0 || y.length == 0) {
         return;
      }

      boolean xAllMeasure = Arrays.stream(x).allMatch(r -> r instanceof VSChartAggregateRef);
      boolean yAllDim = Arrays.stream(y).allMatch(r -> r instanceof VSChartDimensionRef);

      if(xAllMeasure && yAllDim) {
         List<ChartRef> oldX = new ArrayList<>(Arrays.asList(x));
         List<ChartRef> oldY = new ArrayList<>(Arrays.asList(y));
         info.removeXFields();
         info.removeYFields();
         oldY.forEach(info::addXField);
         oldX.forEach(info::addYField);
      }
   }

   /**
    * Returns true when {@code vizType} (the frontend string such as "donut" or "area") matches
    * the given ChartInfo from the recommendation model.
    *
    * Two special cases require this method instead of a plain string compare:
    * <ul>
    *   <li><b>donut</b>: DonutChartFilter stores chartType=CHART_PIE with isDonut=true,
    *       so getChartTypeString(CHART_PIE) returns "pie", not "donut".</li>
    *   <li><b>area</b>: AreaChartFilter only ever generates CHART_AREA_STACK; the
    *       recommendation model therefore never contains a plain CHART_AREA entry.</li>
    * </ul>
    */
   private static boolean chartTypeMatches(String vizType, ChartInfo ci) {
      if("donut".equals(vizType)) {
         return GraphTypes.isDonut(ci);
      }

      // DonutChartFilter stores chartType=CHART_PIE with isDonut=true; exclude it from "pie"
      if("pie".equals(vizType) && GraphTypes.isDonut(ci)) {
         return false;
      }

      String ciType = getChartTypeString(ci.getChartType());

      if(vizType.equals(ciType)) {
         return true;
      }

      // "area" request can be satisfied by an area_stack recommendation
      return "area".equals(vizType) && "area_stack".equals(ciType);
   }

   // ── VisualizationConfig wrapper ───────────────────────────────────────────────

   private VisualizationConfig wrapInConfig(BindingInfo binding, String worksheetPath) {
      VisualizationConfig config = new VisualizationConfig();
      config.setBindingInfo(binding);

      if(!Tool.isEmptyString(worksheetPath)) {
         VisualizationConfig.DataSource ds = new VisualizationConfig.DataSource();
         ds.setSource(worksheetPath);
         config.setData(ds);
      }

      return config;
   }

   /**
    * Changes the visualization type of an existing wizard viewsheet without re-running queries.
    *
    * <p>The recommendation model previously computed by {@link #autoBinding} is read from the
    * {@code autoBindingRuntimeId} RVS.  If it is absent the service falls back to a full
    * {@link #autoBinding} call (which also handles placing the primary and returning the result).
    * Otherwise the matching recommendation is selected and placed in the
    * {@code wizRuntimeId} RVS without executing the sandbox, so the response is fast and does
    * not carry row data.
    */
   public CreateViewsheetResult changeType(ChangeTypeRequest request, Principal user)
      throws Exception
   {
      String autoBindingRuntimeId = request.getAutoBindingRuntimeId();
      String visualizationType = request.getVisualizationType();
      String wizRuntimeId = request.getWizRuntimeId();
      String worksheetId = request.getWorksheetId();
      String viewsheetIdentifier = request.getViewsheetIdentifier();

      // 1. Try to get the recommendation model stored by a prior autoBinding call.
      VSRecommendationModel model = null;
      VSTemporaryInfo autoBindingTempInfo = null;
      RuntimeViewsheet capturedAutoBindingRvs = null;

      if(!Tool.isEmptyString(autoBindingRuntimeId)) {
         try {
            RuntimeViewsheet autoBindingRvs = viewsheetService.getViewsheet(autoBindingRuntimeId, user);

            if(autoBindingRvs != null) {
               capturedAutoBindingRvs = autoBindingRvs;
               autoBindingTempInfo = autoBindingRvs.getVSTemporaryInfo();

               if(autoBindingTempInfo != null) {
                  model = autoBindingTempInfo.getRecommendationModel();
               }
            }
         }
         catch(Exception e) {
            LOG.warn("Could not read recommendation model from autoBindingRuntimeId {}: {}",
                     autoBindingRuntimeId, e.getMessage());
         }
      }

      // 2. Model missing — fall back to full autoBinding (handles placement too).
      if(model == null) {
         AutoBindingRequest fallback = new AutoBindingRequest();
         fallback.setWorksheetId(worksheetId);
         fallback.setVisualizationType(visualizationType);
         // Do not forward autoBindingRuntimeId: if it was non-empty but invalid (expired RVS),
         // autoBindingInternal() would skip creating a new RVS and fail on the same dead id again.
         fallback.setWizRuntimeId(wizRuntimeId);
         fallback.setViewsheetIdentifier(viewsheetIdentifier);
         AutoBindingResponse resp = autoBindingInternal(fallback, user, true);
         CreateViewsheetResult result = resp.getVisualizationResult();

         if(result == null) {
            result = new CreateViewsheetResult();
         }

         // Forward the newly created autoBindingRuntimeId so the client can use the fast
         // path on subsequent changeType calls instead of triggering another full autoBinding.
         result.setAutoBindingRuntimeId(resp.getAutoBindingRuntimeId());

         // Populate headers/rows for data insight (same as the fast path below).
         if(result.getAssemblyName() != null) {
            try {
               String fallbackRuntimeId = !Tool.isEmptyString(result.getRuntimeId())
                  ? result.getRuntimeId() : wizRuntimeId;
               CreateViewsheetResult dataResult = wizVsService.fetchAssemblyData(
                  fallbackRuntimeId, result.getAssemblyName(), user);
               result.setHeaders(dataResult.getHeaders());
               result.setRows(dataResult.getRows());
               result.setHasData(dataResult.getHasData());
               result.setTruncated(dataResult.getTruncated());
            }
            catch(Exception e) {
               LOG.warn("changeType fallback: failed to fetch assembly data for insight (non-critical): {}", e.getMessage());
               LOG.debug("changeType fallback: fetch assembly data stack trace", e);
            }
         }

         return result;
      }

      // 3. Select the recommendation for the requested type. changeType is an explicit user type
      // switch (no explicit-binding pins in play), so the unconstrained chartInfos fallback is fine.
      VSObjectRecommendation selectedRec = findRecByType(visualizationType, model, false);

      if(selectedRec == null) {
         selectedRec = model.getRecommendationList().stream()
            .filter(r -> !(r instanceof VSFilterRecommendation))
            .findFirst()
            .orElse(null);

         if(selectedRec instanceof VSChartRecommendation vcr) {
            vcr.setSelectedIndex(0);
         }
      }

      if(selectedRec == null || Tool.isEmptyString(worksheetId) || Tool.isEmptyString(wizRuntimeId)) {
         LOG.warn("changeType skipped: selectedRec={}, worksheetId='{}', wizRuntimeId='{}'",
                  selectedRec, worksheetId, wizRuntimeId);
         return new CreateViewsheetResult();
      }

      // Mirror the wizard path: update autoBindingRvs so its state stays consistent
      // (calc fields, format, legend, cleanup of previous temp assembly).
      VSAssembly primaryAssembly = null;

      if(capturedAutoBindingRvs != null) {
         primaryAssembly = refreshVisualizationBinding(selectedRec, capturedAutoBindingRvs, user);
      }

      // 4. Place the new primary in wizRuntimeId without executing the sandbox.
      VisualizationConfig sourceConfig = new VisualizationConfig();
      VisualizationConfig.DataSource ds = new VisualizationConfig.DataSource();
      ds.setSource(worksheetId);
      sourceConfig.setData(ds);

      CreateVisualizationModel vsModel = new CreateVisualizationModel();
      vsModel.setConfig(sourceConfig);
      vsModel.setPrimaryAssembly(primaryAssembly);
      vsModel.setRuntimeId(wizRuntimeId);
      vsModel.setViewsheetIdentifier(viewsheetIdentifier);
      vsModel.setKeepCondition(true);

      final RuntimeViewsheet autoRvsForHook = capturedAutoBindingRvs;
      CreateViewsheetResult result = wizVsService.createViewsheetSkipExecution(vsModel, user,
         (wizRvs, asm) -> {
            if(asm instanceof ChartVSAssembly && autoRvsForHook != null) {
               WizardRecommenderUtil.syncCalcFields(autoRvsForHook.getViewsheet(), wizRvs.getViewsheet());
            }
         });

      if(Tool.isEmptyString(result.getRuntimeId())) {
         result.setRuntimeId(wizRuntimeId);
      }

      // Echo the autoBindingRuntimeId back so the client can reuse it on the next call.
      result.setAutoBindingRuntimeId(autoBindingRuntimeId);

      // Populate headers/rows so the caller can generate data insight.
      // createViewsheetSkipExecution omits row data for speed; fetch it here separately.
      if(result.getAssemblyName() != null) {
         try {
            CreateViewsheetResult dataResult = wizVsService.fetchAssemblyData(
               result.getRuntimeId(), result.getAssemblyName(), user);
            result.setHeaders(dataResult.getHeaders());
            result.setRows(dataResult.getRows());
            result.setHasData(dataResult.getHasData());
            result.setTruncated(dataResult.getTruncated());
         }
         catch(Exception e) {
            LOG.warn("changeType: failed to fetch assembly data for insight (non-critical): {}", e.getMessage());
            LOG.debug("changeType: fetch assembly data stack trace", e);
         }
      }

      return result;
   }

   /**
    * Applies chart-level FORMAT properties (axis titles, y-axis scale, legend placement) to an
    * existing runtime chart and re-renders it. Only the non-null request fields are applied; the
    * rest of the chart is left untouched. Returns the re-executed chart's sampled data (the format
    * change affects the rendered chart, not the underlying rows).
    */
   public CreateViewsheetResult setChartFormat(ChartFormatRequest request, Principal user)
      throws Exception
   {
      // Action-level gate ("Visual Composer -> Data Viewsheet"): mutates and (below) persists a
      // viewsheet, so require the composer action right before touching the runtime. Mirrors autoBinding.
      if(!securityEngine.checkPermission(user, ResourceType.VIEWSHEET, "*", ResourceAction.ACCESS)) {
         throw new SecurityException(Catalog.getCatalog().getString(
            "composer.authorization.permissionDenied"));
      }

      RuntimeViewsheet rvs = WizUtil.getViewsheetOrRestore(
         viewsheetService, request.getWizRuntimeId(), request.getViewsheetIdentifier(), user);

      if(rvs == null || rvs.getViewsheet() == null) {
         throw new Exception("Chart runtime not found: " + request.getWizRuntimeId());
      }

      String effRuntimeId = rvs.getID();

      VSAssembly assembly = rvs.getViewsheet().getAssembly(request.getAssemblyName());

      if(!(assembly instanceof ChartVSAssembly chart)) {
         throw new Exception("Chart assembly not found: " + request.getAssemblyName());
      }

      var info = chart.getChartInfo();
      ChartDescriptor desc = info.getChartDescriptor();
      VSChartInfo vsChartInfo = info.getVSChartInfo();

      // Chart (assembly-level) title — the heading shown above the plot. Setting a value also makes
      // the title visible, so a chart that defaulted to the generic "Chart" heading shows the
      // caller's text instead.
      if(request.getChartTitle() != null &&
         chart.getVSAssemblyInfo() instanceof ChartVSAssemblyInfo cinfo)
      {
         cinfo.setTitleValue(request.getChartTitle());
         cinfo.setTitleVisible(true);
      }

      // Axis titles
      if(desc != null && desc.getTitlesDescriptor() != null) {
         TitlesDescriptor titles = desc.getTitlesDescriptor();

         if(request.getXAxisTitle() != null && titles.getXTitleDescriptor() != null) {
            titles.getXTitleDescriptor().setTitleValue(request.getXAxisTitle());
         }

         if(request.getYAxisTitle() != null && titles.getYTitleDescriptor() != null) {
            titles.getYTitleDescriptor().setTitleValue(request.getYAxisTitle());
         }
      }

      // Y-axis scale — applied to each bound measure's axis descriptor.
      boolean scaleChange = request.getYAxisMin() != null || request.getYAxisMax() != null ||
         request.getYAxisIncrement() != null || request.getYAxisLogarithmic() != null;

      if(scaleChange && vsChartInfo != null && vsChartInfo.getYFields() != null) {
         for(ChartRef yref : vsChartInfo.getYFields()) {
            if(yref == null || yref.getAxisDescriptor() == null) {
               continue;
            }

            AxisDescriptor ax = yref.getAxisDescriptor();

            if(request.getYAxisMin() != null) {
               ax.setMinimum(request.getYAxisMin());
            }

            if(request.getYAxisMax() != null) {
               ax.setMaximum(request.getYAxisMax());
            }

            if(request.getYAxisIncrement() != null) {
               ax.setIncrement(request.getYAxisIncrement());
            }

            if(request.getYAxisLogarithmic() != null) {
               ax.setLogarithmicScale(request.getYAxisLogarithmic());
            }
         }
      }

      // Legend placement
      String note = null;

      if(request.getLegendPosition() != null && desc != null && desc.getLegendsDescriptor() != null) {
         int layout = legendLayout(request.getLegendPosition());

         if(layout < 0) {
            note = "Unknown legendPosition '" + request.getLegendPosition() +
               "'; valid: none, top, right, bottom, left, in_place. Legend left unchanged.";
         }
         else {
            desc.getLegendsDescriptor().setLayout(layout);
         }
      }

      // Marker visibility, shape, and size
      if((request.getMarkerVisible() != null || request.getMarkerShape() != null
         || request.getMarkerSize() != null) && desc != null)
      {
         PlotDescriptor plot = desc.getPlotDescriptor();

         if(plot != null) {
            if(request.getMarkerVisible() != null) {
               plot.setPointLine(request.getMarkerVisible());
            }

            if(request.getMarkerShape() != null) {
               plot.setMarkerShape(request.getMarkerShape());
            }

            if(request.getMarkerSize() != null) {
               plot.setMarkerSize(request.getMarkerSize());
            }
         }
      }

      // Time-gap fill for line/area charts. fillTimeGap completes missing date periods (paired with
      // the date dimension's timeSeries); fillZero=false fills with null (vs 0); fillGapWithDash
      // chooses a dashed connector (true) vs a hard line break (false) across the null gap.
      if((request.getFillTimeGap() != null || request.getFillZero() != null
         || request.getFillGapWithDash() != null) && desc != null)
      {
         PlotDescriptor plot = desc.getPlotDescriptor();

         if(plot != null) {
            if(request.getFillTimeGap() != null) {
               plot.setFillTimeGap(request.getFillTimeGap());
            }

            if(request.getFillZero() != null) {
               plot.setFillZero(request.getFillZero());
            }

            if(request.getFillGapWithDash() != null) {
               plot.setFillGapWithDash(request.getFillGapWithDash());
            }
         }
      }

      // Invalidate the cached runtime descriptor so the change regenerates on re-execute.
      info.setRTChartDescriptor(null);

      // The cached VGraphPair holds a reference to this same VSChartInfo, so the sandbox's
      // staleness check (equalsContent against itself) can never detect the in-place mutation
      // above. Clear the cached graph explicitly — mirroring VSChartDataHandler — or every
      // subsequent render (including brand-new embed connections) serves the stale graph.
      rvs.getViewsheetSandbox().ifPresent(box -> box.clearGraph(request.getAssemblyName()));

      // Commit the in-place mutation to the backing asset. save_viewsheet clones the assembly from
      // the PERSISTED viewsheet (not this live runtime), so a format/color change left only on the
      // runtime — the chart title above the plot in particular — is silently dropped on save.
      // Mirrors how create/changeType call persistViewsheet so their changes survive a save.
      if(request.getViewsheetIdentifier() != null) {
         wizVsService.persistViewsheet(rvs.getViewsheet(), request.getViewsheetIdentifier(), user);
      }

      CreateViewsheetResult result =
         wizVsService.fetchAssemblyData(effRuntimeId, request.getAssemblyName(), user);

      if(result == null) {
         result = new CreateViewsheetResult();
      }

      result.setRuntimeId(effRuntimeId);
      result.setAssemblyName(request.getAssemblyName());

      if(request.getViewsheetIdentifier() != null) {
         result.setViewsheetIdentifier(request.getViewsheetIdentifier());
      }

      if(note != null) {
         result.setNote(note);
      }

      return result;
   }

   /** Maps a legend-position string to a {@link LegendsDescriptor} layout constant; -1 if unknown. */
   private static int legendLayout(String position) {
      return switch(position.toLowerCase()) {
         case "none" -> LegendsDescriptor.NO_LEGEND;
         case "top" -> LegendsDescriptor.TOP;
         case "right" -> LegendsDescriptor.RIGHT;
         case "bottom" -> LegendsDescriptor.BOTTOM;
         case "left" -> LegendsDescriptor.LEFT;
         case "in_place" -> LegendsDescriptor.IN_PLACE;
         default -> -1;
      };
   }

   /**
    * Sets chart COLORS in place on an existing runtime chart and re-renders. The mode is chosen from
    * the chart's color binding:
    *   - no field on color  -> static: staticColor applies to each measure ref.
    *   - dimension on color -> categorical: paletteName / colorList / categoryColors apply.
    *   - measure on color   -> gradient: paletteName resolves to a named gradient frame.
    * Returns a {@code note} on the result when a requested color could not be applied to the current
    * binding (in-place only — the caller should recreate with the right field on color).
    */
   public CreateViewsheetResult setChartColors(ChartColorsRequest request, Principal user)
      throws Exception
   {
      // Action-level gate ("Visual Composer -> Data Viewsheet"): mutates and (below) persists a
      // viewsheet, so require the composer action right before touching the runtime. Mirrors autoBinding.
      if(!securityEngine.checkPermission(user, ResourceType.VIEWSHEET, "*", ResourceAction.ACCESS)) {
         throw new SecurityException(Catalog.getCatalog().getString(
            "composer.authorization.permissionDenied"));
      }

      RuntimeViewsheet rvs = WizUtil.getViewsheetOrRestore(
         viewsheetService, request.getWizRuntimeId(), request.getViewsheetIdentifier(), user);

      if(rvs == null || rvs.getViewsheet() == null) {
         throw new Exception("Chart runtime not found: " + request.getWizRuntimeId());
      }

      String effRuntimeId = rvs.getID();

      VSAssembly assembly = rvs.getViewsheet().getAssembly(request.getAssemblyName());

      if(!(assembly instanceof ChartVSAssembly chart)) {
         throw new Exception("Chart assembly not found: " + request.getAssemblyName());
      }

      var info = chart.getChartInfo();
      VSChartInfo vsChartInfo = info.getVSChartInfo();
      AestheticRef colorField = vsChartInfo == null ? null : vsChartInfo.getColorField();
      String note = null;

      if(colorField == null || colorField.getDataRef() == null) {
         if(request.getStaticColor() != null) {
            if(vsChartInfo == null) {
               note = "Chart has no binding info; static color could not be applied.";
            }
            else {
               applyStaticColor(vsChartInfo, parseColor(request.getStaticColor()));
            }
         }

         if(request.getPaletteName() != null || request.getColorList() != null ||
            request.getCategoryColors() != null)
         {
            note = "This chart has no color dimension, so a palette/per-category colors can't apply. " +
               "Recreate with a dimension on the color aesthetic (fieldConfigs/explicitBindings), or use staticColor.";
         }
      }
      else if(colorField.getDataRef() instanceof VSChartAggregateRef) {
         note = applyGradient(colorField, request);
      }
      else {
         note = applyCategoricalColors(colorField, request);
      }

      info.setRTChartDescriptor(null);

      // The cached VGraphPair holds a reference to this same VSChartInfo, so the sandbox's
      // staleness check (equalsContent against itself) can never detect the in-place mutation
      // above. Clear the cached graph explicitly — mirroring VSChartDataHandler — or every
      // subsequent render (including brand-new embed connections) serves the stale graph.
      rvs.getViewsheetSandbox().ifPresent(box -> box.clearGraph(request.getAssemblyName()));

      // Commit the in-place mutation to the backing asset. save_viewsheet clones the assembly from
      // the PERSISTED viewsheet (not this live runtime), so a format/color change left only on the
      // runtime — the chart title above the plot in particular — is silently dropped on save.
      // Mirrors how create/changeType call persistViewsheet so their changes survive a save.
      if(request.getViewsheetIdentifier() != null) {
         wizVsService.persistViewsheet(rvs.getViewsheet(), request.getViewsheetIdentifier(), user);
      }

      CreateViewsheetResult result =
         wizVsService.fetchAssemblyData(effRuntimeId, request.getAssemblyName(), user);

      if(result == null) {
         result = new CreateViewsheetResult();
      }

      result.setRuntimeId(effRuntimeId);
      result.setAssemblyName(request.getAssemblyName());

      if(request.getViewsheetIdentifier() != null) {
         result.setViewsheetIdentifier(request.getViewsheetIdentifier());
      }

      if(note != null) {
         result.setNote(note);
      }

      return result;
   }

   /**
    * Applies a single static color to every bound measure (aggregate) ref — both the design
    * refs (so the change persists across runtime regeneration and save) and the runtime refs
    * (the renderer reads getRTYFields()/getRTXFields(), clones made at execution time, so
    * painting only the design refs would leave the next render on the old color).
    */
   private void applyStaticColor(VSChartInfo vsChartInfo, Color color) {
      if(vsChartInfo == null) {
         return;
      }

      List<ChartRef> refs = new ArrayList<>();

      if(vsChartInfo.getYFields() != null) {
         refs.addAll(Arrays.asList(vsChartInfo.getYFields()));
      }

      if(vsChartInfo.getXFields() != null) {
         refs.addAll(Arrays.asList(vsChartInfo.getXFields()));
      }

      if(vsChartInfo.getRTYFields() != null) {
         refs.addAll(Arrays.asList(vsChartInfo.getRTYFields()));
      }

      if(vsChartInfo.getRTXFields() != null) {
         refs.addAll(Arrays.asList(vsChartInfo.getRTXFields()));
      }

      for(ChartRef ref : refs) {
         if(ref instanceof VSChartAggregateRef agg) {
            agg.setColorFrame(new StaticColorFrame(color));
         }
      }
   }

   /**
    * Applies categorical colors to a dimension that is bound to the color aesthetic. Returns a note
    * if none of the categorical fields were provided.
    */
   private String applyCategoricalColors(AestheticRef colorField, ChartColorsRequest request) {
      if(request.getPaletteName() != null) {
         CategoricalColorFrame palette = ColorPalettes.getPalette(request.getPaletteName());

         if(palette == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown palette '" +
               request.getPaletteName() + "'. Valid palettes: " +
               String.join(", ", ColorPalettes.getPaletteNames()));
         }

         // Clone: getPalette() returns the shared singleton frame; never set/mutate it directly.
         colorField.setVisualFrame((CategoricalColorFrame) palette.clone());
      }

      if(request.getColorList() != null && !request.getColorList().isEmpty()) {
         CategoricalColorFrame frame = asCategoricalFrame(colorField);
         Color[] colors = request.getColorList().stream()
            .map(WizAutoBindingService::parseColor)
            .toArray(Color[]::new);
         frame.setDefaultColors(colors);
         colorField.setVisualFrame(frame);
      }

      if(request.getCategoryColors() != null && !request.getCategoryColors().isEmpty()) {
         CategoricalColorFrame frame = asCategoricalFrame(colorField);

         for(Map.Entry<String, String> e : request.getCategoryColors().entrySet()) {
            frame.setColor(e.getKey(), parseColor(e.getValue()));
         }

         colorField.setVisualFrame(frame);
      }

      if(request.getStaticColor() != null) {
         return "staticColor was ignored: this chart has a color dimension. Use paletteName, " +
            "colorList, or categoryColors to color the categories.";
      }

      if(request.getPaletteName() == null && request.getColorList() == null &&
         request.getCategoryColors() == null)
      {
         return "No categorical color provided. Pass paletteName, colorList, or categoryColors.";
      }

      return null;
   }

   /** Returns the color field's current CategoricalColorFrame, or a fresh one if it isn't categorical. */
   private CategoricalColorFrame asCategoricalFrame(AestheticRef colorField) {
      VisualFrame frame = colorField.getVisualFrame();
      return frame instanceof CategoricalColorFrame cat ? cat : new CategoricalColorFrame();
   }

   /**
    * Applies a named gradient when a measure is on the color aesthetic. Only paletteName is meaningful
    * for a continuous color scale; colorList/categoryColors return a note.
    */
   private String applyGradient(AestheticRef colorField, ChartColorsRequest request) {
      if(request.getColorList() != null || request.getCategoryColors() != null) {
         return "This chart has a measure on color (a continuous scale), so colorList/categoryColors " +
            "don't apply. Use paletteName with a gradient name (e.g. " + VALID_GRADIENTS + ").";
      }

      if(request.getStaticColor() != null) {
         return "staticColor was ignored: this chart uses a measure-driven color scale. Use paletteName " +
            "with a gradient name.";
      }

      if(request.getPaletteName() == null) {
         return "No gradient provided. Pass paletteName with a gradient name (" + VALID_GRADIENTS + ").";
      }

      ColorFrame gradient = gradientFrameForName(request.getPaletteName());

      if(gradient == null) {
         return "Unknown gradient '" + request.getPaletteName() + "'. Valid gradients: " + VALID_GRADIENTS + ".";
      }

      colorField.setVisualFrame(gradient);
      return null;
   }

   /** The named gradients accepted by {@link #gradientFrameForName} (kept in sync with that switch). */
   private static final String VALID_GRADIENTS = "Blues, Reds, Greens, RdYlBu, RdYlGn, Heat";

   /** Maps a gradient name to its concrete frame (case-insensitive); null when unknown. */
   private static ColorFrame gradientFrameForName(String name) {
      return switch(name.trim().toLowerCase()) {
         case "blues" -> new BluesColorFrame();
         case "reds" -> new RedsColorFrame();
         case "greens" -> new GreensColorFrame();
         case "rdylbu" -> new RdYlBuColorFrame();
         case "rdylgn" -> new RdYlGnColorFrame();
         case "heat" -> new HeatColorFrame();
         default -> null;
      };
   }

   /** Parses a #RRGGBB hex string into a Color; throws on a malformed value. */
   private static Color parseColor(String hex) {
      try {
         return Color.decode(hex.trim());
      }
      catch(NumberFormatException e) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Invalid hex color '" + hex + "' (expected #RRGGBB)");
      }
   }

   /**
    * Calls updatePrimaryAssembly on {@code rvs} to mirror the full wizard setup path
    * (calc field registration, format, legend, temp assembly cleanup), then returns the
    * resulting temp assembly. The caller must have already set {@code selectedIndex} on
    * any {@link VSChartRecommendation} before calling this method.
    */
   private VSAssembly refreshVisualizationBinding(VSObjectRecommendation rec,
                                                   RuntimeViewsheet rvs,
                                                   Principal user) throws Exception
   {
      CommandDispatcher.withDummyDispatcher(user, dispatcher -> {
         bindingHandler.updatePrimaryAssembly(rec, rvs, false, "", dispatcher, false);
         return null;
      });

      return WizardRecommenderUtil.getTempAssembly(rvs.getViewsheet());
   }

   private static final Logger LOG = LoggerFactory.getLogger(WizAutoBindingService.class);

   private final ViewsheetService viewsheetService;
   private final AssetRepository engine;
   private final VSWizardTemporaryInfoService temporaryInfoService;
   private final VSWizardBindingHandler bindingHandler;
   private final VSDefaultRecommendationFactory defaultRecommendationFactory;
   private final WizVsService wizVsService;
   private final SecurityEngine securityEngine;
}
