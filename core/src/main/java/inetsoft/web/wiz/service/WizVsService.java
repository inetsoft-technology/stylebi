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
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.graph.data.*;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.TableLens;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.XNodeMetaTable;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.uql.XTable;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.internal.graph.MapData;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import inetsoft.web.wiz.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Service
public class WizVsService {
   public WizVsService(ViewsheetService viewsheetService, AssetRepository engine) {
      this.viewsheetService = viewsheetService;
      this.engine = engine;
   }

   @FunctionalInterface
   public interface PostAssemblyHook {
      void apply(RuntimeViewsheet rvs, VSAssembly assembly) throws Exception;
   }

   public CreateViewsheetResult createViewsheet(CreateVisualizationModel model, Principal user) throws Exception {
      return createViewsheetInternal(model, user, false, null);
   }

   public CreateViewsheetResult createViewsheet(CreateVisualizationModel model, Principal user,
                                                PostAssemblyHook hook) throws Exception
   {
      return createViewsheetInternal(model, user, false, hook);
   }

   /**
    * Places the primary assembly without executing the sandbox.
    * Returns a {@link CreateViewsheetResult} with binding and assembly metadata but no row data.
    */
   CreateViewsheetResult createViewsheetSkipExecution(CreateVisualizationModel model, Principal user)
      throws Exception
   {
      return createViewsheetInternal(model, user, true, null);
   }

   CreateViewsheetResult createViewsheetSkipExecution(CreateVisualizationModel model, Principal user,
                                                      PostAssemblyHook hook) throws Exception
   {
      return createViewsheetInternal(model, user, true, hook);
   }

   private CreateViewsheetResult createViewsheetInternal(CreateVisualizationModel model,
                                                         Principal user,
                                                         boolean skipExecution,
                                                         PostAssemblyHook hook) throws Exception
   {
      String runtimeId = model.getRuntimeId();
      boolean createdRuntimeId = false;

      if(Tool.isEmptyString(runtimeId)) {
         Viewsheet.WizInfo wizInfo = new Viewsheet.WizInfo(true, null, null);
         runtimeId = viewsheetService.openTemporaryViewsheet(null, null, user, wizInfo);
         createdRuntimeId = true;
      }

      try {
         RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, user);
         Viewsheet vs = getValidatedViewsheet(rvs);

         final Viewsheet targetVs;
         final VSAssembly assembly;
         // Tracks the assembly displaced from primary in the standard path; restored on rollback.
         VSAssembly previousPrimaryAssembly = null;
         // In modificationOnly (in-place filter), the assembly's prior condition, restored on rollback.
         ConditionList previousCondition = null;
         // Only relevant for the incremental standard path (non-null when base entry may be mutated).
         AssetEntry previousBaseEntry = null;
         boolean modificationOnly = model.getConfig() == null && model.getPrimaryAssembly() == null
            && model.getConditionModel() != null;
         // Track the worksheet table name for aggregate condition handling
         String wsTableName = null;

         if(modificationOnly) {
            targetVs = vs;
            VSAssembly sourceAssembly = findPrimaryAssembly(targetVs);

            if(sourceAssembly == null) {
               throw new IllegalStateException("No primary assembly found in viewsheet for modification");
            }

            // Get the worksheet table name from the source assembly's source info
            if(sourceAssembly instanceof DataVSAssembly dataAsm) {
               SourceInfo srcInfo = dataAsm.getSourceInfo();
               wsTableName = srcInfo != null ? srcInfo.getSource() : null;
               // Snapshot the existing condition so a failure can roll back the in-place change.
               previousCondition = dataAsm.getPreConditionList() != null
                  ? dataAsm.getPreConditionList().clone() : null;
            }

            // Apply the filter to the EXISTING primary assembly in place — keep its name and
            // identity. Copying to a unique "<name>_1" assembly (the old behavior) broke
            // save_viewsheet: save loads the persisted viewsheet and looks the assembly up by name,
            // so it never found the renamed copy, and every filter call churned a new assembly.
            assembly = sourceAssembly;
         }
         else {
            SourceContext ctx = resolveSourceContext(model, user);
            wsTableName = ctx.primaryAssemblyName();

            // Incremental mode: reuse the existing viewsheet when runtimeId was supplied.
            if(createdRuntimeId) {
               targetVs = new Viewsheet(ctx.sourceWs());
               targetVs.syncWizData(vs);
            }
            else {
               targetVs = vs;
            }

            // Snapshot before any mutation so we can restore on failure.
            previousBaseEntry = targetVs.getBaseEntry();

            if(!createdRuntimeId && !ctx.sourceWs().equals(previousBaseEntry)) {
               targetVs.setBaseEntry(ctx.sourceWs());
            }

            String assemblyName = uniqueAssemblyName(targetVs, ctx.title());

            if(model.getPrimaryAssembly() != null) {
               assembly = rebindAssembly(targetVs, assemblyName, model.getPrimaryAssembly());

               if(assembly == null) {
                  throw new IllegalArgumentException("Unsupported primary assembly type: "
                                                        + model.getPrimaryAssembly().getClass().getName());
               }
            }
            else {
               assembly = createAssembly(targetVs, model.getVisualizationType(), assemblyName,
                                         ctx.config(), ctx.primaryAssemblyName());

               if(assembly == null) {
                  throw new IllegalArgumentException("Unsupported visualization type: " + model.getVisualizationType());
               }
            }

            // Clear old primary before adding the new assembly; capture it for rollback.
            Assembly[] existingAssemblies = targetVs.getAssemblies();

            if(existingAssemblies != null) {
               for(Assembly a : existingAssemblies) {
                  if(a instanceof VSAssembly va && va.isPrimary()) {
                     previousPrimaryAssembly = va;
                     va.setPrimary(false);
                  }
               }
            }

            targetVs.addAssembly(assembly);
            assembly.setPrimary(true);

            // Sync pre-condition from the replaced assembly to the new one when the caller
            // is performing a visualization type change (e.g. table → chart).
            if(model.isKeepCondition() &&
               previousPrimaryAssembly instanceof DataVSAssembly oldDataAsm &&
               assembly instanceof DataVSAssembly newDataAsm)
            {
               ConditionList cond = oldDataAsm.getPreConditionList();

               if(cond != null) {
                  newDataAsm.setPreConditionList(cond.clone());
               }
            }
         }

         // Always call setViewsheet so the sandbox picks up the updated viewsheet object.
         Viewsheet previousVs = rvs.getViewsheet();
         rvs.setViewsheet(targetVs);

         // Check if aggregate conditions exist - if so, push everything to worksheet
         VisualizationConditionModel conditionModel = model.getConditionModel();
         boolean hasAggregateConditions = conditionModel != null && conditionModel.hasAggregateConditions();

         // Collect binding for result
         CreateViewsheetResult.FlatBinding binding = collectFlatBinding(assembly);
         AssetEntry wsEntry = null;
         Worksheet originWs = null;
         boolean wsModified = false;
         boolean removedPreviousPrimary = false;

         try {
            // All mutations happen inside this try so the catch can roll back everything
            if(hasAggregateConditions && wsTableName != null && binding != null) {
               wsEntry = targetVs.getBaseEntry();
               Worksheet ws = (Worksheet) engine.getSheet(wsEntry, user, false, AssetContent.ALL);
               wsTableName = VSUtil.stripOuter(wsTableName);
               Assembly wsAssembly = ws != null ? ws.getAssembly(wsTableName) : null;

               if(wsAssembly instanceof AbstractTableAssembly tableAsm) {
                  // Clone original worksheet for rollback before any modifications
                  originWs = (Worksheet) ws.clone();

                  // Push aggregation and conditions to worksheet
                  PreAggregationMapping preAggMapping = pushAggregationToWorksheet(
                     binding, conditionModel, tableAsm);

                  if(wsEntry != null) {
                     engine.setSheet(wsEntry, ws, user, true);
                     wsModified = true;
                  }

                  // Update VS assembly to use pre-aggregated columns
                  updateVsBindingForPreAggregation(assembly, preAggMapping);

                  // Re-collect binding after updating for pre-aggregation
                  binding = collectFlatBinding(assembly);
               }
               else {
                  // Fallback to original path if worksheet table not found
                  LOG.warn("Aggregate conditions specified but worksheet table '{}' not found; " +
                              "aggregate conditions will be ignored", wsTableName);
                  applyConditionModel(assembly, conditionModel);
               }
            }
            else {
               // Original path: apply base conditions to VS assembly
               applyConditionModel(assembly, conditionModel);
            }

            // In incremental mode, GenerateWsService may have added new WS assemblies. Reload
            // inside try so any failure triggers the same rollback as an execution failure.
            if(wsModified || !createdRuntimeId && !modificationOnly) {
               targetVs.reloadBaseWorksheet(engine, user);
            }

            if(hook != null) {
               hook.apply(rvs, assembly);
            }

            // Materialize any auto-binned "Range@<measure>" histogram dimension into a real range calc
            // field on the output viewsheet, so a SAVED chart reopens. Range@ is a wizard-only shorthand
            // (the wizard executor strips it and bins the base column at preview time); a saved viewsheet
            // executes the normal path and would fail with ColumnNotFoundException: Range@<col>. This is
            // done here — not in the wizard recommender — because the create/save path rebuilds the chart
            // on a fresh output viewsheet that has no VSTemporaryInfo, so bins are computed from the
            // chart's SourceInfo. rvs.getViewsheet() == targetVs here (set above), so the calc field lands
            // on the viewsheet that save_viewsheet persists. Runs before execution so the chart executes
            // (and saves) against the materialized binding.
            if(assembly instanceof ChartVSAssembly rangeChart && rangeChart.getVSChartInfo() != null
               && rangeChart.getSourceInfo() != null)
            {
               SourceInfo rangeSrc = rangeChart.getSourceInfo();
               ChartRef[] rangeRefs = rangeChart.getVSChartInfo().getBindingRefs(false);

               if(rangeRefs != null) {
                  for(ChartRef cr : rangeRefs) {
                     if(cr instanceof VSDimensionRef vdim && vdim.getGroupColumnValue() != null
                        && vdim.getGroupColumnValue().startsWith("Range@"))
                     {
                        WizardRecommenderUtil.createRangeDimension(
                           vdim, rvs, rangeChart.getVSChartInfo().getAggregateInfo(), rangeSrc, rangeSrc.getSource());

                        // A Range@ binding is a histogram: the binned bars represent a continuous
                        // distribution, so they must read as one contiguous band, not discrete
                        // category bars. The full-band width is set on the size frame in
                        // HistogramChartFilter; here we also drop the rounded corners (default 0.3)
                        // and the bar border so adjacent bins butt cleanly together.
                        ChartDescriptor cdesc = rangeChart.getChartDescriptor();

                        if(cdesc != null && cdesc.getPlotDescriptor() != null) {
                           PlotDescriptor plot = cdesc.getPlotDescriptor();
                           plot.setBarCornerRadius(0);
                           plot.setBorderColor(null);
                        }
                     }
                  }
               }
            }

            // Title crosstab/table assemblies with a binding-derived heading instead of StyleBI's
            // bland default "Table". The plugin recommender path (create_viewsheet) doesn't run the
            // interactive wizard's title logic (VSWizardBindingHandler), so derive it here on the
            // finalized assembly — design headers if present, else the runtime headers.
            if(assembly instanceof CrosstabVSAssembly ctAssembly
               && ctAssembly.getVSCrosstabInfo() != null)
            {
               VSCrosstabInfo ci = ctAssembly.getVSCrosstabInfo();
               String gridTitle = buildGridTitle(
                  ci.getDesignAggregates(), ci.getDesignColHeaders(), ci.getDesignRowHeaders());

               if(gridTitle == null) {
                  gridTitle = buildGridTitle(
                     ci.getRuntimeAggregates(), ci.getRuntimeColHeaders(), ci.getRuntimeRowHeaders());
               }

               applyGridTitle(ctAssembly, gridTitle);
            }
            else if(assembly instanceof TableVSAssembly tblAssembly
               && tblAssembly.getColumnSelection() != null)
            {
               ColumnSelection sel = tblAssembly.getColumnSelection();
               DataRef[] detailRefs = new DataRef[sel.getAttributeCount()];

               for(int i = 0; i < sel.getAttributeCount(); i++) {
                  detailRefs[i] = sel.getAttribute(i);
               }

               applyGridTitle(tblAssembly, buildGridTitle(null, null, detailRefs));
            }
            else if(assembly instanceof ChartVSAssembly titleChart
               && titleChart.getVSChartInfo() != null)
            {
               // Charts default to the generic "Chart" title (ChartVSAssemblyInfo's
               // new TitleInfo("Chart")); replace it with a binding-derived one, e.g.
               // "Sum(amount) by sales_stage". applyGridTitle only sets the value (not visibility),
               // so a chart whose title isn't shown is unaffected. Gather refs the same way
               // collectChartFlatBinding does — x/y/group PLUS aesthetics — because for some types
               // (e.g. treemap) the measure lives on the size aesthetic, not in getBindingRefs.
               VSChartInfo titleInfo = titleChart.getVSChartInfo();
               List<DataRef> titleCandidates = new ArrayList<>();
               ChartRef[] titleRefs = titleInfo.getBindingRefs(false);

               if(titleRefs != null) {
                  Collections.addAll(titleCandidates, titleRefs);
               }

               for(AestheticRef aref : new AestheticRef[]{
                  titleInfo.getColorField(), titleInfo.getShapeField(),
                  titleInfo.getSizeField(), titleInfo.getTextField()
               }) {
                  if(aref != null && aref.getDataRef() != null) {
                     titleCandidates.add(aref.getDataRef());
                  }
               }

               List<DataRef> chartMeasures = new ArrayList<>();
               List<DataRef> chartDims = new ArrayList<>();
               Set<String> titleSeen = new HashSet<>();

               for(DataRef cr : titleCandidates) {
                  if(cr == null) {
                     continue;
                  }

                  if(cr instanceof VSAggregateRef && titleSeen.add("m:" + cr.getName())) {
                     chartMeasures.add(cr);
                  }
                  else if(cr instanceof VSDimensionRef && titleSeen.add("d:" + cr.getName())) {
                     chartDims.add(cr);
                  }
               }

               applyGridTitle(titleChart, buildGridTitle(
                  chartMeasures.toArray(new DataRef[0]), null, chartDims.toArray(new DataRef[0])));
            }

            CreateViewsheetResult result;

            if(skipExecution) {
               result = new CreateViewsheetResult();
            }
            else {
               int sampleMaxRows = model.getSampleMaxRows() != null ? model.getSampleMaxRows() : 0;
               result = executeAndExtract(rvs, assembly, sampleMaxRows);
               boolean metadataMode = rvs.getViewsheet().getViewsheetInfo().isMetadata();
               result.setHasData(computeHasData(metadataMode, result));
            }

            result.setBinding(binding);
            result.setAssemblyName(assembly.getName());

            if(createdRuntimeId) {
               result.setRuntimeId(runtimeId);
            }

            // For skipExecution (changeType): remove the displaced primary before persisting
            // so the stored viewsheet contains only the new assembly.
            if(skipExecution && previousPrimaryAssembly != null && !createdRuntimeId) {
               targetVs.removeAssembly(previousPrimaryAssembly.getName());
               removedPreviousPrimary = true;
            }

            String identifierToUse = model.getViewsheetIdentifier();
            result.setViewsheetIdentifier(persistViewsheet(targetVs, identifierToUse, user));

            return result;
         }
         catch(Exception e) {
            // Rollback: restore the previous viewsheet state.
            rvs.setViewsheet(previousVs);

            // Rollback worksheet if it was modified
            if(wsModified && originWs != null && wsEntry != null) {
               try {
                  engine.setSheet(wsEntry, originWs, user, true);
               }
               catch(Exception rollbackEx) {
                  LOG.warn("Failed to rollback worksheet during error recovery: {}",
                           rollbackEx.getMessage());
               }
            }

            if(!createdRuntimeId) {
               if(modificationOnly) {
                  // In-place filter: restore the assembly's prior condition rather than removing
                  // the (existing) assembly, which was mutated, not added, this call.
                  if(assembly instanceof DataVSAssembly dataAsm) {
                     dataAsm.setPreConditionList(previousCondition);
                  }
               }
               else {
                  // In incremental mode targetVs == previousVs; remove the new assembly and
                  // restore any displaced primary to leave the viewsheet in its pre-call state.
                  previousVs.removeAssembly(assembly.getName());

                  if(previousPrimaryAssembly != null) {
                     previousPrimaryAssembly.setPrimary(true);

                     // Re-add the old primary if it was removed by the skipExecution path.
                     if(removedPreviousPrimary) {
                        previousVs.addAssembly(previousPrimaryAssembly);
                     }
                  }

                  previousVs.setBaseEntry(previousBaseEntry);
               }
            }

            throw e;
         }
      }
      catch(Exception e) {
         if(createdRuntimeId) {
            try {
               viewsheetService.closeViewsheet(runtimeId, user);
            }
            catch(Exception ex) {
               LOG.warn("Failed to close temporary viewsheet [{}] during error cleanup: {}", runtimeId, ex.getMessage());
            }
         }

         throw e;
      }
   }

   /**
    * Validates the binding configuration by opening a temporary viewsheet, creating the assembly,
    * and executing the sandbox — without persisting anything.  Throws on any configuration or
    * execution error; returns normally when the binding is valid.  The temporary runtime
    * viewsheet is closed immediately after validation completes.
    *
    * @param model the visualization model ({@code runtimeId} is ignored; a fresh viewsheet is
    *              always used)
    * @param user  the current user
    */
   public void validateBinding(CreateVisualizationModel model, Principal user) throws Exception {
      String runtimeId = buildAndExecuteFresh(model, user);

      try {
         viewsheetService.closeViewsheet(runtimeId, user);
      }
      catch(Exception ex) {
         LOG.warn("Failed to close temporary viewsheet [{}] after binding validation: {}", runtimeId, ex.getMessage());
      }
   }

   /**
    * Opens a fresh temporary runtime viewsheet, creates the assembly described by {@code model},
    * and executes the sandbox view.  On failure the temporary viewsheet is closed automatically;
    * on success the caller is responsible for closing it.
    *
    * @return the {@code runtimeId} of the temporary viewsheet
    */
   private String buildAndExecuteFresh(CreateVisualizationModel model, Principal user)
      throws Exception
   {
      Viewsheet.WizInfo wizInfo = new Viewsheet.WizInfo(true, null, null);
      String runtimeId = viewsheetService.openTemporaryViewsheet(null, null, user, wizInfo);

      try {
         RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, user);
         // WizInfo is set explicitly above via openTemporaryViewsheet; getValidatedViewsheet will always pass.
         Viewsheet vs = getValidatedViewsheet(rvs);
         SourceContext ctx = resolveSourceContext(model, user);

         Viewsheet targetVs = new Viewsheet(ctx.sourceWs());
         targetVs.syncWizData(vs);

         VSAssembly assembly = createAssembly(targetVs, model.getVisualizationType(), ctx.title(),
                                              ctx.config(), ctx.primaryAssemblyName());

         if(assembly == null) {
            throw new IllegalArgumentException("Unsupported visualization type: " + model.getVisualizationType());
         }

         // Clear old primary before adding the new assembly.
         Assembly[] existingAssemblies = targetVs.getAssemblies();

         if(existingAssemblies != null) {
            for(Assembly a : existingAssemblies) {
               if(a instanceof VSAssembly va && va.isPrimary()) {
                  va.setPrimary(false);
               }
            }
         }

         targetVs.addAssembly(assembly);
         assembly.setPrimary(true);
         rvs.setViewsheet(targetVs);

         // Execute to validate; result is not needed.
         executeAndExtract(rvs, assembly, model.getSampleMaxRows() != null ? model.getSampleMaxRows() : 0);

         return runtimeId;
      }
      catch(Exception e) {
         try {
            viewsheetService.closeViewsheet(runtimeId, user);
         }
         catch(Exception ex) {
            LOG.warn("Failed to close temporary viewsheet [{}] during error cleanup: {}", runtimeId, ex.getMessage());
         }

         throw e;
      }
   }

   /**
    * Validates the given {@link RuntimeViewsheet} and returns its {@link Viewsheet}.
    * Throws if the RVS is null, its viewsheet is missing, or WizInfo is not configured.
    */
   private Viewsheet getValidatedViewsheet(RuntimeViewsheet rvs) {
      if(rvs == null) {
         throw new IllegalStateException("Runtime Viewsheet not found");
      }

      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         throw new IllegalArgumentException("Runtime Viewsheet does not contain a Viewsheet object");
      }

      if(vs.getWizInfo() == null) {
         throw new IllegalArgumentException("Runtime Viewsheet does not have WizInfo configured");
      }

      if(!vs.getWizInfo().isWizVisualization()) {
         throw new IllegalArgumentException("Runtime Viewsheet is not configured as a Wiz visualization");
      }

      return vs;
   }

   /**
    * Parses the visualization config, resolves the source worksheet, and returns a
    * {@link SourceContext} containing the config, source {@link AssetEntry}, title, and primary
    * assembly name.  Throws on missing or invalid configuration.
    */
   private SourceContext resolveSourceContext(CreateVisualizationModel model, Principal user)
      throws Exception
   {
      VisualizationConfig config = model.getConfig();

      if(config == null || config.getData() == null || config.getData().getSource() == null) {
         throw new IllegalArgumentException("Invalid configuration, missing source");
      }

      String title = config.getTitle() != null && !config.getTitle().isEmpty()
         ? config.getTitle()
         : "vs_" + System.currentTimeMillis();
      // config.getData().getSource() is the worksheet's full asset IDENTIFIER, not a bare path —
      // parse it with createAssetEntry, else getSheet returns null ("Cannot find worksheet").
      AssetEntry sourceWs = AssetEntry.createAssetEntry(config.getData().getSource());
      AbstractSheet sheet = engine.getSheet(sourceWs, user, true, AssetContent.ALL);

      if(!(sheet instanceof Worksheet worksheet)) {
         throw new IllegalStateException("Cannot find worksheet");
      }

      WSAssembly primaryAssembly = worksheet.getPrimaryAssembly();

      if(primaryAssembly == null) {
         throw new IllegalStateException("Worksheet has no primary assembly");
      }

      return new SourceContext(config, sourceWs, primaryAssembly.getName(), title);
   }

   /**
    * Executes the sandbox view for {@code assembly} and extracts the result data.
    * Dispatches to the appropriate extract method based on assembly type.
    */
   private CreateViewsheetResult executeAndExtract(RuntimeViewsheet rvs, VSAssembly assembly,
                                                   int sampleMaxRows)
      throws Exception
   {
      Optional<ViewsheetSandbox> viewsheetSandbox = rvs.getViewsheetSandbox();

      if(viewsheetSandbox.isEmpty()) {
         throw new IllegalStateException("ViewsheetSandbox is empty");
      }

      ViewsheetSandbox box = viewsheetSandbox.get();

      // #75456: cancel + data-mode. Truly abort any in-flight query left over from a prior render
      // (e.g. a slow full-data query the user is superseding by toggling to sampled) at the source
      // — cancelAllQueries() -> QueryManager.cancel() -> Statement.cancel() — rather than leaving it
      // running. Then apply the requested row cap to the source worksheet so the (re)render
      // aggregates the chosen amount of data, dropping cached results so the new cap is honored.
      // sampleMaxRows <= 0 = full data (the default and the agent path).
      box.cancelAllQueries();
      var bws = rvs.getViewsheet() != null ? rvs.getViewsheet().getBaseWorksheet() : null;

      if(bws != null) {
         bws.getWorksheetInfo().setDesignMaxRows(Math.max(sampleMaxRows, 0));

         try {
            box.resetDataMap(assembly.getName());
            box.clearGraph(assembly.getName());
         }
         catch(Exception ignore) {
         }
      }

      box.executeView(assembly.getName(), true);

      if(assembly instanceof ChartVSAssembly) {
         return extractChartData(rvs, assembly.getName());
      }
      else if(assembly instanceof TableVSAssembly || assembly instanceof CrosstabVSAssembly) {
         return extractTableLensData(rvs, assembly.getName());
      }
      else if(assembly instanceof OutputVSAssembly) {
         return extractOutputAssemblyData(rvs, assembly.getName());
      }
      else {
         return new CreateViewsheetResult();
      }
   }

   /**
    * Executes the sandbox for an existing assembly and returns headers/rows/hasData.
    * Called after {@code createViewsheetSkipExecution} to lazily populate row data
    * (e.g. for data insight generation on a type switch).
    */
   CreateViewsheetResult fetchAssemblyData(String runtimeId, String assemblyName,
                                           Principal user) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, user);

      if(rvs == null) {
         return new CreateViewsheetResult();
      }

      Viewsheet vs = rvs.getViewsheet();
      VSAssembly assembly = vs.getAssembly(assemblyName);

      if(assembly == null) {
         return new CreateViewsheetResult();
      }

      // #75456: preserve the current data-mode on this lazy re-fetch path — pass the design-max
      // already set on the source worksheet (no-op for full; keeps sampled mode sampled).
      var fetchWs = vs.getBaseWorksheet();
      int curMax = fetchWs != null ? fetchWs.getWorksheetInfo().getDesignMaxRows() : 0;
      CreateViewsheetResult result = executeAndExtract(rvs, assembly, curMax);

      if(result != null) {
         // Order matters: executeAndExtract runs executeView above, which populates the
         // chart's RT refs. collectFlatBinding prefers RT refs, so it must come after.
         result.setBinding(collectFlatBinding(assembly));
      }

      boolean metadataMode = vs.getViewsheetInfo().isMetadata();
      result.setHasData(computeHasData(metadataMode, result));
      return result;
   }

   private static boolean computeHasData(boolean metadataMode, CreateViewsheetResult result) {
      return !metadataMode && result.getRows() != null && !result.getRows().isEmpty();
   }

   private record SourceContext(VisualizationConfig config, AssetEntry sourceWs,
                                String primaryAssemblyName, String title)
   {
   }

   /**
    * Returns {@code name} if no assembly with that name exists in {@code vs}; otherwise
    * appends {@code _1}, {@code _2}, … until a free slot is found.
    */
   private String uniqueAssemblyName(Viewsheet vs, String name) {
      if(vs.getAssembly(name) == null) {
         return name;
      }

      int i = 1;

      while(vs.getAssembly(name + "_" + i) != null) {
         i++;
      }

      return name + "_" + i;
   }

   /**
    * Throws if the lens chain contains failed-query fallback data. When a live query fails
    * (e.g. a computed-column expression that is invalid SQL for the data source), AssetQuery
    * substitutes design-time sample data instead of erroring, and the chart path replaces
    * that meta table with generated sample rows (name1/999.99). The wiz path must surface
    * the failure instead of returning fabricated rows as if they were real query results.
    */
   // package-private for testing
   static void checkFailedQuery(XTable lens) {
      checkFailedQuery(lens, true);
   }

   /**
    * Throws if the lens chain contains failed-query fallback data. When a live query fails
    * (e.g. a computed-column expression that is invalid SQL for the data source), AssetQuery
    * substitutes design-time sample data instead of erroring, and the chart path replaces
    * that meta table with generated sample rows (name1/999.99). The wiz path must surface
    * the failure instead of returning fabricated rows as if they were real query results.
    *
    * @param wrapExpressionError when true, throw the expression-specific {@link #failedQueryError}
    *        message (the default for chart/binding callers); when false, throw the raw underlying
    *        cause instead, for callers surfacing failures on non-expression tables (e.g. a raw SQL
    *        query table) where the "check the expression columns" advice would mislead.
    */
   static void checkFailedQuery(XTable lens, boolean wrapExpressionError) {
      if(lens == null) {
         return;
      }

      List<XTable> allTables = new ArrayList<>();
      Util.listNestedTable(lens, XTable.class, allTables);

      for(XTable t : allTables) {
         String cause = failedQueryCause(t);

         if(cause != null) {
            throw new IllegalArgumentException(
               wrapExpressionError ? failedQueryError(cause)
                  : (cause.isBlank() ? "Worksheet query failed." : cause));
         }
      }
   }

   /**
    * Detect a failed-query table and return its underlying cause. Two signals, depending on the
    * path: the worksheet/table path surfaces the raw design meta table (isFailedQueryDefault);
    * the chart path replaces it with generated sample data carrying FAILED_QUERY_PROPERTY (see
    * ChartVSAQuery). Returns null when the table is not a failed query, an empty string when it
    * failed but no specific cause was captured, or the cause message (e.g. the JDBC/SQL error).
    */
   static String failedQueryCause(XTable t) {
      if(t instanceof XNodeMetaTable meta && meta.isFailedQueryDefault()) {
         String cause = meta.getFailedQueryMessage();
         return cause == null ? "" : cause;
      }

      if(t instanceof AbstractTableLens atl) {
         Object prop = atl.getProperty(XNodeMetaTable.FAILED_QUERY_PROPERTY);

         if(prop != null) {
            // The property value carries the real cause; "true" is the legacy bare flag.
            return prop instanceof String s && !"true".equals(s) && !s.isBlank() ? s : "";
         }
      }

      return null;
   }

   /**
    * Compose the user-facing failed-query error, appending the underlying data-source cause
    * (e.g. "column X does not exist") when known so the caller can fix the real problem instead
    * of guessing at expression columns.
    */
   static String failedQueryError(String cause) {
      String message =
         "Worksheet query failed — a computed-column expression or filter is invalid " +
         "for the data source. Check the worksheet's expression columns.";

      if(cause != null && !cause.isBlank()) {
         message += " (cause: " + cause + ")";
      }

      return message;
   }

   /**
    * Extracts aggregated chart data from the viewsheet sandbox after the assembly is created.
    * Returns a result with null headers/rows if data is unavailable (best-effort).
    */
   private CreateViewsheetResult extractChartData(RuntimeViewsheet rvs, String assemblyName) {
      ViewsheetSandbox box = null;

      try {
         Optional<ViewsheetSandbox> boxOpt = rvs.getViewsheetSandbox();

         if(boxOpt.isEmpty()) {
            return new CreateViewsheetResult();
         }

         box = boxOpt.get();
         VGraphPair pair = box.getVGraphPair(assemblyName, true);

         if(pair == null) {
            return new CreateViewsheetResult();
         }

         DataSet dset = pair.getData();

         if(dset == null) {
            return new CreateViewsheetResult();
         }

         // Unwrap dataset wrappers to reach the aggregated data
         while(true) {
            if(dset instanceof VSDataSet) {
               break;
            }
            else if(dset instanceof PairsDataSet) {
               dset = ((PairsDataSet) dset).getDataSet();
            }
            else if(dset instanceof DataSetFilter) {
               dset = ((DataSetFilter) dset).getDataSet();
            }
            else {
               break;
            }
         }

         if(dset instanceof VSDataSet vds) {
            checkFailedQuery(vds.getTable());
         }

         int colCount = dset.getColCount();
         int rowCount = dset.getRowCount();
         int limit = Math.min(rowCount, MAX_ROWS);

         List<String> headers = new ArrayList<>(colCount);

         for(int c = 0; c < colCount; c++) {
            headers.add(dset.getHeader(c));
         }

         List<Map<String, Object>> rows = new ArrayList<>(limit);

         for(int r = 0; r < limit; r++) {
            Map<String, Object> row = new LinkedHashMap<>(colCount);

            for(int c = 0; c < colCount; c++) {
               row.put(headers.get(c), dset.getData(c, r));
            }

            rows.add(row);
         }

         CreateViewsheetResult result = new CreateViewsheetResult();
         result.setHeaders(headers);
         result.setRows(rows);

         if(rowCount > MAX_ROWS) {
            result.setTruncated(true);
         }

         // #75456: if the source worksheet had a finite design-mode sample cap in effect
         // (sampled-preview mode), the chart aggregated at most that many detail rows, so Sum/Count
         // may be approximate. Flag it so the caller can warn. Full-data mode (the default) sets the
         // worksheet design-max to 0 (unlimited) -> no flag. Best-effort; absence just means no warning.
         try {
            var bws = rvs.getViewsheet() != null ? rvs.getViewsheet().getBaseWorksheet() : null;
            int dmax = bws != null ? bws.getWorksheetInfo().getDesignMaxRows() : 0;

            if(dmax > 0) {
               result.setSampled(true);
               result.setSampleMaxRows(dmax);
            }
         }
         catch(Exception ignore) {
         }

         return result;
      }
      catch(IllegalArgumentException e) {
         // Failed-query fallback detected: propagate instead of returning fabricated data.
         if(box != null) {
            box.clearGraph(assemblyName);
         }

         throw e;
      }
      catch(Exception e) {
         LOG.warn("Failed to extract chart data after viewsheet creation for assembly '{}': {}",
                  assemblyName, e.getMessage());

         // If initGraph threw an exception, it still sets completed=true but leaves vgraph=null.
         // That broken pair would cause getAssemblyImage to loop on retryAfter=1 indefinitely.
         // Clearing it here allows getAssemblyImage to reinitialize on the next request.
         if(box != null) {
            box.clearGraph(assemblyName);
         }

         return new CreateViewsheetResult();
      }
   }

   /** Outcome of executing a chart assembly for verification: did it render data, and how many rows. */
   public record VerifyResult(boolean hasData, int rowCount) {}

   /**
    * Execute a chart assembly's data to verify the saved visualization actually renders, without
    * materializing the rows. Surfaces the real data-source error by throwing (via checkFailedQuery)
    * when the underlying query failed and design sample data was substituted. Returns hasData=false
    * with rowCount=0 when the sandbox/graph/dataset is simply unavailable.
    */
   public VerifyResult verifyChartData(RuntimeViewsheet rvs, String assemblyName) throws Exception {
      Optional<ViewsheetSandbox> boxOpt = rvs.getViewsheetSandbox();

      if(boxOpt.isEmpty()) {
         return new VerifyResult(false, 0);
      }

      ViewsheetSandbox box = boxOpt.get();
      VGraphPair pair = box.getVGraphPair(assemblyName, true);

      if(pair == null) {
         return new VerifyResult(false, 0);
      }

      DataSet dset = pair.getData();

      if(dset == null) {
         return new VerifyResult(false, 0);
      }

      // Unwrap dataset wrappers to reach the aggregated data (mirrors extractChartData).
      DataSet unwrapped = dset;

      while(true) {
         if(unwrapped instanceof VSDataSet) {
            break;
         }
         else if(unwrapped instanceof PairsDataSet) {
            unwrapped = ((PairsDataSet) unwrapped).getDataSet();
         }
         else if(unwrapped instanceof DataSetFilter) {
            unwrapped = ((DataSetFilter) unwrapped).getDataSet();
         }
         else {
            break;
         }
      }

      if(unwrapped instanceof VSDataSet vds) {
         // Throws IllegalArgumentException carrying the real cause when the query failed.
         checkFailedQuery(vds.getTable());
      }

      int rowCount = dset.getRowCount();
      return new VerifyResult(rowCount > 0, rowCount);
   }

   /**
    * Extracts tabular data from a Table or Crosstab assembly via its TableLens.
    * Row 0 through headerRowCount-1 are header rows; data begins at headerRowCount.
    * <p>
    * For Crosstab: all column-dimension headers are temporarily moved to row headers so the
    * result is a flat table (no pivoted columns).  The original binding is restored in a
    * finally block regardless of success or failure.
    * <p>
    * Returns a result with null headers/rows if data is unavailable (best-effort).
    */
   private CreateViewsheetResult extractTableLensData(RuntimeViewsheet rvs, String assemblyName) {
      Optional<ViewsheetSandbox> boxOpt = rvs.getViewsheetSandbox();

      if(boxOpt.isEmpty()) {
         return new CreateViewsheetResult();
      }

      ViewsheetSandbox box = boxOpt.get();

      // Snapshot the original crosstab binding so it can be restored after data extraction.
      VSCrosstabInfo crosstabInfo = null;
      DataRef[] originalRowHeaders = null;
      DataRef[] originalColHeaders = null;

      try {
         VSAssembly vsAssembly = rvs.getViewsheet().getAssembly(assemblyName);

         if(vsAssembly instanceof CrosstabVSAssembly crosstab) {
            crosstabInfo = crosstab.getVSCrosstabInfo();
            originalRowHeaders = crosstabInfo.getDesignRowHeaders();
            originalColHeaders = crosstabInfo.getDesignColHeaders();

            if(originalColHeaders != null && originalColHeaders.length > 0) {
               // Merge col-dimensions into row-dimensions so the tablelens is fully flat.
               DataRef[] safeRowHeaders = originalRowHeaders != null ? originalRowHeaders : new DataRef[0];
               DataRef[] flatRows = new DataRef[safeRowHeaders.length + originalColHeaders.length];
               System.arraycopy(safeRowHeaders, 0, flatRows, 0, safeRowHeaders.length);
               System.arraycopy(originalColHeaders, 0, flatRows, safeRowHeaders.length, originalColHeaders.length);
               crosstabInfo.setDesignRowHeaders(flatRows);
               crosstabInfo.setDesignColHeaders(new DataRef[0]);
               // Invalidate cached data so the next getTableData() uses the flattened layout.
               box.resetDataMap(assemblyName);
            }
            else {
               // No col headers to flatten; no need to restore later.
               crosstabInfo = null;
            }
         }

         TableLens table = box.getTableData(assemblyName);

         if(table == null) {
            return new CreateViewsheetResult();
         }

         checkFailedQuery(table);

         int colCount = table.getColCount();
         int headerRows = table.getHeaderRowCount();

         List<String> headers = new ArrayList<>(colCount);

         for(int c = 0; c < colCount; c++) {
            Object h = table.getObject(0, c);
            headers.add(h != null ? h.toString() : "col" + c);
         }

         List<Map<String, Object>> rows = new ArrayList<>();
         int r = headerRows;

         while(table.moreRows(r) && rows.size() < MAX_ROWS) {
            Map<String, Object> row = new LinkedHashMap<>(colCount);

            for(int c = 0; c < colCount; c++) {
               row.put(headers.get(c), table.getObject(r, c));
            }

            rows.add(row);
            r++;
         }

         CreateViewsheetResult result = new CreateViewsheetResult();
         result.setHeaders(headers);
         result.setRows(rows);

         if(table.moreRows(r)) {
            result.setTruncated(true);
         }

         return result;
      }
      catch(IllegalArgumentException e) {
         // Failed-query fallback detected: propagate instead of returning fabricated data.
         throw e;
      }
      catch(Exception e) {
         LOG.warn("Failed to extract table data after viewsheet creation for assembly '{}': {}",
                  assemblyName, e.getMessage());
         return new CreateViewsheetResult();
      }
      finally {
         // Restore the original crosstab binding so the assembly remains consistent with the
         // VisualizationConfig that was used to create it.
         if(crosstabInfo != null) {
            crosstabInfo.setDesignRowHeaders(originalRowHeaders);
            crosstabInfo.setDesignColHeaders(originalColHeaders);
            box.resetDataMap(assemblyName);
         }
      }
   }

   /**
    * Extracts the computed scalar value from a Gauge or Text output assembly.
    * Triggers sandbox execution via getData(), then reads the value set on the assembly.
    * Returns a single-row, single-column result, or a result with null data if unavailable.
    */
   private CreateViewsheetResult extractOutputAssemblyData(RuntimeViewsheet rvs, String assemblyName) {
      try {
         Optional<ViewsheetSandbox> boxOpt = rvs.getViewsheetSandbox();

         if(boxOpt.isEmpty()) {
            return new CreateViewsheetResult();
         }

         ViewsheetSandbox box = boxOpt.get();

         VSAssembly vsAssembly = rvs.getViewsheet().getAssembly(assemblyName);

         if(!(vsAssembly instanceof OutputVSAssembly outputAssembly)) {
            return new CreateViewsheetResult();
         }

         // executeOutput runs the scalar query via getData() and then calls assembly.setValue(),
         // which is required before getValue() returns the computed result.
         box.executeOutput(outputAssembly.getAssemblyEntry());

         Object value = outputAssembly.getValue();

         if(value == null) {
            return new CreateViewsheetResult();
         }

         CreateViewsheetResult result = new CreateViewsheetResult();
         result.setHeaders(List.of("value"));
         result.setRows(List.of(Map.of("value", value)));
         return result;
      }
      catch(Exception e) {
         LOG.warn("Failed to extract output assembly data after viewsheet creation for assembly '{}': {}",
                  assemblyName, e.getMessage());
         return new CreateViewsheetResult();
      }
   }

   /**
    * Builds a {@link CreateViewsheetResult.FlatBinding} from the assembly's current binding.
    * Returns null for assembly types that carry no aggregate binding (e.g. Table, Image).
    *
    * <p>For charts, slot collection prefers RT refs, which are only populated after the
    * viewsheet has executed (executeView). Callers using a cold RVS get design refs via
    * fallback — accurate enough for the echo, but the slots are not RT-resolved. Call
    * after execution when RT-accurate slots are required.
    */
   public CreateViewsheetResult.FlatBinding collectFlatBinding(VSAssembly assembly) {
      if(assembly instanceof ChartVSAssembly chart) {
         return collectChartFlatBinding(chart.getVSChartInfo());
      }
      else if(assembly instanceof CrosstabVSAssembly crosstab) {
         return collectCrosstabFlatBinding(crosstab.getVSCrosstabInfo());
      }
      else if(assembly instanceof OutputVSAssembly output) {
         return collectOutputFlatBinding(output);
      }

      return null;
   }

   /**
    * Collects dimensions and measures from all binding slots of a chart:
    * x/y/group fields, aesthetic refs (color, shape, size, text, path), and
    * chart-type-specific fields (high/low/close/open, start/end/milestone,
    * source/target, node aesthetics).
    */
   private CreateViewsheetResult.FlatBinding collectChartFlatBinding(VSChartInfo info) {
      List<DimensionFieldInfo> dimensions = new ArrayList<>();
      List<MeasureFieldInfo> measures = new ArrayList<>();
      Set<String> seen = new HashSet<>();

      // Main binding: x, y, group
      for(ChartRef ref : info.getBindingRefs(false)) {
         classifyChartRef(ref, dimensions, measures, seen);
      }

      // Aesthetic refs: color, shape, size, text
      for(AestheticRef aref : new AestheticRef[]{
         info.getColorField(), info.getShapeField(),
         info.getSizeField(), info.getTextField()
      }) {
         if(aref != null) {
            classifyChartRef(aref.getDataRef(), dimensions, measures, seen);
         }
      }

      // Path field
      ChartRef pathField = info.getPathField();
      if(pathField != null) {
         classifyChartRef(pathField, dimensions, measures, seen);
      }

      // Candle / Stock: high, low, close, open (all measures)
      if(info instanceof CandleVSChartInfo cinfo) {
         for(ChartRef ref : new ChartRef[]{
            cinfo.getHighField(), cinfo.getLowField(),
            cinfo.getCloseField(), cinfo.getOpenField()
         }) {
            classifyChartRef(ref, dimensions, measures, seen);
         }
      }

      // Gantt: start, end, milestone (dimensions acting as date ranges)
      if(info instanceof GanttVSChartInfo ginfo) {
         for(ChartRef ref : new ChartRef[]{
            ginfo.getStartField(), ginfo.getEndField(), ginfo.getMilestoneField()
         }) {
            classifyChartRef(ref, dimensions, measures, seen);
         }
      }

      // Relation (Tree / Network / Circular): source, target + node aesthetics
      if(info instanceof RelationVSChartInfo rinfo) {
         classifyChartRef(rinfo.getSourceField(), dimensions, measures, seen);
         classifyChartRef(rinfo.getTargetField(), dimensions, measures, seen);

         for(AestheticRef aref : new AestheticRef[]{
            rinfo.getNodeColorField(), rinfo.getNodeSizeField()
         }) {
            if(aref != null) {
               classifyChartRef(aref.getDataRef(), dimensions, measures, seen);
            }
         }
      }

      if(dimensions.isEmpty() && measures.isEmpty()) {
         return null;
      }

      return new CreateViewsheetResult.FlatBinding(dimensions, measures, collectChartSlots(info));
   }

   /**
    * Resolved slot placement for a chart: which slot each bound field landed in.
    * Reads the RUNTIME refs when present (the renderer reads getRT*Fields(); design and
    * runtime can diverge — mekko trims X at runtime, drill visibility, period-calendar
    * injects a runtime dim), falling back to design refs before execution.
    * Measure refs are reported as their full aggregate name ("Sum(amount)") to match
    * the rankingCol convention; dimensions by field name.
    */
   static Map<String, Object> collectChartSlots(VSChartInfo info) {
      Map<String, Object> slots = new LinkedHashMap<>();
      slots.put("x", slotNames(rtOrDesign(info.getRTXFields(), info.getXFields())));
      slots.put("y", slotNames(rtOrDesign(info.getRTYFields(), info.getYFields())));
      slots.put("group", slotNames(rtOrDesign(info.getRTGroupFields(), info.getGroupFields())));
      slots.put("color", aestheticSlotName(info.getColorField()));
      slots.put("shape", aestheticSlotName(info.getShapeField()));
      slots.put("size", aestheticSlotName(info.getSizeField()));
      slots.put("text", aestheticSlotName(info.getTextField()));
      return slots;
   }

   private static ChartRef[] rtOrDesign(ChartRef[] rt, ChartRef[] design) {
      return rt != null && rt.length > 0 ? rt : design;
   }

   private static List<String> slotNames(ChartRef[] refs) {
      List<String> names = new ArrayList<>();

      if(refs != null) {
         for(ChartRef ref : refs) {
            if(ref != null) {
               names.add(slotName(ref));
            }
         }
      }

      return names;
   }

   private static String slotName(DataRef ref) {
      return ref instanceof VSAggregateRef agg ? agg.getFullName()
         : WizardRecommenderUtil.getChartRefFieldName(ref);
   }

   private static String aestheticSlotName(AestheticRef aref) {
      return aref == null || aref.getDataRef() == null ? null : slotName(aref.getDataRef());
   }

   /**
    * Classifies a single chart DataRef into either the dimensions or measures list.
    * VSDimensionRef (and subclasses) → DimensionFieldInfo with title from getFullName().
    * VSAggregateRef (and subclasses) → MeasureFieldInfo with title from getFullName().
    * Fields already present in {@code seen} (keyed by fullName) are skipped to avoid
    * duplicates when a field appears in multiple binding slots (e.g. axis and color aesthetic).
    */
   private void classifyChartRef(DataRef ref,
                                 List<DimensionFieldInfo> dimensions,
                                 List<MeasureFieldInfo> measures,
                                 Set<String> seen)
   {
      if(ref == null) {
         return;
      }

      if(ref instanceof VSDimensionRef dim) {
         if(seen.add(dim.getFullName())) {
            dimensions.add(WizFieldInfoFactory.createDimensionFieldInfo(dim));
         }
      }
      else if(ref instanceof VSAggregateRef agg) {
         if(seen.add(agg.getFullName())) {
            measures.add(WizFieldInfoFactory.createMeasureFieldInfo(agg));
         }
      }
   }

   private static void collectDimensionFieldInfos(DataRef[] refs,
                                                  List<DimensionFieldInfo> dimensions)
   {
      if(refs == null) {
         return;
      }

      for(DataRef ref : refs) {
         if(ref instanceof VSDimensionRef dim) {
            dimensions.add(WizFieldInfoFactory.createDimensionFieldInfo(dim));
         }
      }
   }

   private static void collectMeasureFieldInfos(DataRef[] refs,
                                                List<MeasureFieldInfo> measures)
   {
      if(refs == null) {
         return;
      }

      for(DataRef ref : refs) {
         if(ref instanceof VSAggregateRef agg) {
            measures.add(WizFieldInfoFactory.createMeasureFieldInfo(agg));
         }
      }
   }

   /**
    * Collects dimensions from row/col headers and measures from aggregates of a crosstab.
    */
   private CreateViewsheetResult.FlatBinding collectCrosstabFlatBinding(VSCrosstabInfo cinfo) {
      List<DimensionFieldInfo> dimensions = new ArrayList<>();
      List<MeasureFieldInfo> measures = new ArrayList<>();

      collectDimensionFieldInfos(cinfo.getDesignRowHeaders(), dimensions);
      collectDimensionFieldInfos(cinfo.getDesignColHeaders(), dimensions);
      collectMeasureFieldInfos(cinfo.getDesignAggregates(), measures);

      if(dimensions.isEmpty() && measures.isEmpty()) {
         return null;
      }

      return new CreateViewsheetResult.FlatBinding(dimensions, measures, collectCrosstabSlots(cinfo));
   }

   /**
    * Resolved slot placement for a crosstab: row/col header dimensions and aggregate
    * measures. Measure refs are reported as their full aggregate name ("Sum(amount)")
    * to match the chart {@code measures} list and the rankingCol convention; dimensions
    * by field name.
    */
   static Map<String, Object> collectCrosstabSlots(VSCrosstabInfo cinfo) {
      Map<String, Object> slots = new LinkedHashMap<>();
      slots.put("rows", refSlotNames(cinfo.getDesignRowHeaders()));
      slots.put("cols", refSlotNames(cinfo.getDesignColHeaders()));
      slots.put("aggregates", refSlotNames(cinfo.getDesignAggregates()));
      return slots;
   }

   /**
    * Collects slot names from an arbitrary {@link DataRef} array, applying the same
    * field-name convention as {@link #slotName(DataRef)} (measures by full aggregate
    * name, dimensions by field name). Used for crosstab row/col/aggregate slots.
    */
   private static List<String> refSlotNames(DataRef[] refs) {
      List<String> names = new ArrayList<>();

      if(refs != null) {
         for(DataRef ref : refs) {
            if(ref != null) {
               names.add(slotName(ref));
            }
         }
      }

      return names;
   }

   /**
    * Extracts the scalar measure from a Gauge or Text output assembly's binding.
    * Returns a FlatBinding with an empty dimensions list and a single measure.
    */
   private CreateViewsheetResult.FlatBinding collectOutputFlatBinding(OutputVSAssembly assembly) {
      ScalarBindingInfo sbinfo = assembly.getScalarBindingInfo();

      if(sbinfo == null || sbinfo.getColumnValue() == null || sbinfo.getColumnValue().isEmpty()) {
         return null;
      }

      VSAggregateRef ref = new VSAggregateRef();
      ref.setColumnValue(sbinfo.getColumnValue());

      if(sbinfo.getAggregateValue() != null) {
         ref.setFormulaValue(sbinfo.getAggregateValue());
      }

      String secondaryColumn = sbinfo.getColumn2Value();

      if(secondaryColumn != null && !secondaryColumn.isEmpty()) {
         ref.setSecondaryColumnValue(secondaryColumn);
      }

      String nValueStr = sbinfo.getNValue();

      if(nValueStr != null && !nValueStr.isEmpty()) {
         try {
            int nValue = Integer.parseInt(nValueStr);

            if(nValue != 0) {
               ref.setN(nValue);
            }
         }
         catch(NumberFormatException ignored) {
            // Ignore non-numeric N values
         }
      }

      MeasureFieldInfo info = new MeasureFieldInfo();
      info.setField(sbinfo.getColumnValue());
      info.setFullName(ref.getFullName());
      info.setAggregateFormula(sbinfo.getAggregateValue());

      if(ref.getSecondaryColumnValue() != null) {
         info.setSecondaryField(ref.getSecondaryColumnValue());
      }

      if(ref.getN() != 0) {
         info.setNOrP(ref.getN());
      }

      Map<String, Object> slots = new LinkedHashMap<>();
      slots.put("value", ref.getFullName());

      return new CreateViewsheetResult.FlatBinding(List.of(), List.of(info), slots);
   }

   /**
    * Saves the viewsheet to the visualizations root folder.
    *
    * <p>If {@code existingIdentifier} is non-empty the viewsheet is written to that exact
    * entry (overwrite / update).  Otherwise a new UUID-named entry is created under
    * {@link WizVisualizationService#VISUALIZATION_ROOT_FOLDER_PATH}.
    *
    * @param vs                 the viewsheet to persist
    * @param existingIdentifier optional identifier returned from a previous call; may be null
    * @param user               the current user
    *
    * @return the {@link AssetEntry#toIdentifier() identifier} of the saved entry
    *
    * @throws Exception if the entry identifier is invalid or the repository save fails
    *
    * <p>Public so the in-place chart edits in {@link WizAutoBindingService} (set chart format /
    * colors) can commit their runtime mutation to the backing asset — {@code save_viewsheet}
    * reads the PERSISTED viewsheet, so an unpersisted runtime change (e.g. the chart title) is
    * otherwise silently dropped on save.
    */
   public String persistViewsheet(Viewsheet vs, String existingIdentifier, Principal user)
      throws Exception
   {
      final AssetEntry entry;

      if(!Tool.isEmptyString(existingIdentifier)) {
         entry = AssetEntry.createAssetEntry(existingIdentifier);

         if(entry == null) {
            throw new IllegalArgumentException("Cannot parse viewsheetIdentifier: " + existingIdentifier);
         }

         String existingPath = entry.getPath();

         // Accept both managed folders: ROOT holds session viewsheets, COMPONENTS holds standalone
         // saved visualizations (the Wiz Portal Visualizations tab). A reloaded saved viz being
         // re-bound in place (#75486) lives in COMPONENTS, so restricting to ROOT alone rejected
         // every saved-chart re-bind (mirrors the open endpoint's #3901 fix).
         if(existingPath == null ||
            !(existingPath.startsWith(WizVisualizationService.VISUALIZATION_ROOT_FOLDER_PATH + "/") ||
              existingPath.startsWith(WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH + "/")))
         {
            throw new IllegalArgumentException(
               "viewsheetIdentifier points outside the managed visualizations folder: " + existingPath);
         }
      }
      else {
         IdentityID pId = IdentityID.getIdentityIDFromKey(user.getName());
         AssetEntry folder = new AssetEntry(
            AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER,
            WizVisualizationService.VISUALIZATION_ROOT_FOLDER_PATH, null);

         try {
            if(!engine.containsEntry(folder)) {
               engine.addFolder(folder, user);
            }
         }
         catch(Exception e) {
            // Folder may have been created concurrently; proceed if it now exists.
            if(!engine.containsEntry(folder)) {
               throw e;
            }

            // Folder exists now, but log the original exception in case it was a different error
            // (e.g., permissions issue that succeeded on retry or a transient failure).
            LOG.debug("Exception during folder creation (folder now exists, proceeding): {}",
                      e.getMessage());
         }

         String uuid = UUID.randomUUID().toString();
         String path = WizVisualizationService.VISUALIZATION_ROOT_FOLDER_PATH + "/" + uuid;
         entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, path, pId);
      }

      viewsheetService.setViewsheet(vs, entry, user, true, true);
      return entry.toIdentifier();
   }

   /**
    * Creates a new assembly of the same type as {@code src}, bound to {@code vs} and renamed to
    * {@code name}, then copies the binding info directly from {@code src}. Used when the caller
    * already has a fully-configured temp assembly (from the wizard setup path) and only needs it
    * rehosted in a different viewsheet.
    */
   private VSAssembly rebindAssembly(Viewsheet vs, String name, VSAssembly src) {
      BiFunction<Viewsheet, String, VSAssembly> factory = ASSEMBLY_FACTORIES.get(src.getClass());

      if(factory == null) {
         LOG.warn("rebindAssembly: unsupported assembly type {}; falling back to null", src.getClass().getName());
         return null;
      }

      VSAssembly dest = factory.apply(vs, name);
      dest.setVSAssemblyInfo(src.getVSAssemblyInfo().clone());
      return dest;
   }

   private VSAssembly createAssembly(Viewsheet vs, String type, String name,
                                     VisualizationConfig config, String tname)
   {
      if(type == null) {
         return null;
      }

      VSAssembly vsAssembly;

      int chartType = getChartType(type);

      if(chartType >= 0) {
         vsAssembly = createChartAssembly(vs, name, chartType, config);
      }
      else {
         vsAssembly = switch(type) {
            case "table" -> createTableAssembly(vs, name, config);
            case "crosstab" -> createCrosstabAssembly(vs, name, config);
            case "gauge" -> createGaugeAssembly(vs, name);
            case "text" -> createTextAssembly(vs, name);
            case "image" -> createImageAssembly(vs, name, config);
            default -> null;
         };
      }

      if(vsAssembly != null && config != null && config.getDescription() != null &&
         !config.getDescription().isEmpty())
      {
         vsAssembly.getVSAssemblyInfo().setDescription(config.getDescription());
      }

      if(vsAssembly instanceof DataVSAssembly dataVSAssembly) {
         dataVSAssembly.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, tname));
      }
      else if(vsAssembly instanceof OutputVSAssembly outputVSAssembly && config.getBindingInfo() instanceof OutputBinding outputBinding) {
         ScalarBindingInfo sbinfo = outputVSAssembly.getScalarBindingInfo();

         if(sbinfo == null) {
            sbinfo = new ScalarBindingInfo();
            outputVSAssembly.setScalarBindingInfo(sbinfo);
         }

         sbinfo.setTableName(tname);

         if(outputBinding.getField() != null) {
            sbinfo.setColumnValue(outputBinding.getField().getField());

            if(outputBinding.getField().getAggregateFormula() != null) {
               sbinfo.setAggregateValue(outputBinding.getField().getAggregateFormula());
            }
         }
      }

      return vsAssembly;
   }

   /**
    * Returns the GraphTypes constant for chart types, or -1 for non-chart types.
    */
   private int getChartType(String type) {
      return switch(type) {
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
         case "contour_map" -> GraphTypes.CHART_MAP_CONTOUR;
         case "map" -> GraphTypes.CHART_MAP;
         default -> -1;
      };
   }

   private ChartVSAssembly createChartAssembly(Viewsheet vs, String name, int chartType,
                                               VisualizationConfig config)
   {
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      chart.initDefaultFormat();
      VSChartInfo chartInfo = createVSChartInfo(chartType);
      chartInfo.setChartType(chartType);
      chart.setVSChartInfo(chartInfo);

      createGeoFields(config, chartInfo);

      if(config != null && config.getBindingInfo() instanceof ChartBinding binding) {
         applyChartBinding(chartInfo, binding, chartType);
         GraphUtil.fixVisualFrames(chartInfo);
      }

      return chart;
   }

   private void createGeoFields(VisualizationConfig config, VSChartInfo chartInfo) {
      if(config != null && config.getLayers() != null) {
         for(Layer layerConfig : config.getLayers()) {
            if(layerConfig.getField() == null) {
               continue;
            }

            VSChartGeoRef geoRef = new VSChartGeoRef();
            geoRef.setGroupColumnValue(layerConfig.getField());
            GeographicOption geoOption = geoRef.getGeographicOption();

            if(layerConfig.getLayer() != null) {
               try {
                  int layerId = MapData.getLayer(layerConfig.getLayer());
                  geoOption.setLayerValue(String.valueOf(layerId));
               }
               catch(Exception e) {
                  throw new RuntimeException("Invalid layer '" + layerConfig.getLayer() +
                                                "': " + e.getMessage(), e);
               }
            }

            if(layerConfig.getMap() != null) {
               geoOption.getMapping().setType(layerConfig.getMap());
            }

            chartInfo.getGeoColumns().addAttribute(geoRef);
         }
      }
   }

   /**
    * Creates the appropriate VSChartInfo subclass for the given chart type.
    */
   private VSChartInfo createVSChartInfo(int chartType) {
      return switch(chartType) {
         case GraphTypes.CHART_STOCK -> new StockVSChartInfo();
         case GraphTypes.CHART_CANDLE -> new CandleVSChartInfo();
         case GraphTypes.CHART_RADAR, GraphTypes.CHART_FILL_RADAR -> new RadarVSChartInfo();
         case GraphTypes.CHART_TREE, GraphTypes.CHART_NETWORK, GraphTypes.CHART_CIRCULAR ->
            new RelationVSChartInfo();
         case GraphTypes.CHART_GANTT -> new GanttVSChartInfo();
         case GraphTypes.CHART_MAP, GraphTypes.CHART_MAP_CONTOUR -> new VSMapInfo();
         default -> new DefaultVSChartInfo();
      };
   }

   private void applyChartBinding(VSChartInfo chartInfo, ChartBinding binding, int chartType) {
      // Relation charts: Tree, Network, Circular Network
      if(chartInfo instanceof RelationVSChartInfo relationInfo) {
         if(binding.getX() != null) {
            for(SimpleFieldInfo f : binding.getX()) {
               chartInfo.addXField(createChartRef(f));
            }
         }

         if(binding.getY() != null) {
            for(SimpleFieldInfo f : binding.getY()) {
               chartInfo.addYField(createChartRef(f));
            }
         }

         // Edge aesthetics: color, shape, size
         if(binding.getColor() != null) {
            chartInfo.setColorField(createAestheticRef(binding.getColor()));
         }

         if(binding.getShape() != null) {
            chartInfo.setShapeField(createAestheticRef(binding.getShape()));
         }

         if(binding.getSize() != null) {
            chartInfo.setSizeField(createAestheticRef(binding.getSize()));
         }

         // Source / target node fields
         if(binding.getSource() != null) {
            relationInfo.setSourceField(createVSChartDimensionRef(binding.getSource()));
         }

         if(binding.getTarget() != null) {
            relationInfo.setTargetField(createVSChartDimensionRef(binding.getTarget()));
         }

         // Node aesthetics: color, size
         if(binding.getNode() != null) {
            ChartBinding.NodeBinding node = binding.getNode();

            if(node.getColor() != null) {
               relationInfo.setNodeColorField(createAestheticRef(node.getColor()));
            }

            if(node.getSize() != null) {
               relationInfo.setNodeSizeField(createAestheticRef(node.getSize()));
            }

            if(node.getText() != null) {
               relationInfo.setTextField(createAestheticRef(node.getText()));
            }
         }
      }
      // Map charts: Map, Contour Map (x=longitude, y=latitude)
      else if(chartInfo instanceof VSMapInfo mapInfo) {
         if(binding.getLongitude() != null) {
            for(SimpleFieldInfo f : binding.getLongitude()) {
               chartInfo.addXField(createChartRef(f));
            }
         }

         if(binding.getLatitude() != null) {
            for(SimpleFieldInfo f : binding.getLatitude()) {
               chartInfo.addYField(createChartRef(f));
            }
         }

         if(binding.getGeo() != null) {
            for(SimpleFieldInfo f : binding.getGeo()) {
               VSChartGeoRef geoRef = new VSChartGeoRef();
               geoRef.setGroupColumnValue(f.getField());
               mapInfo.addGeoField(geoRef);
            }
         }

         if(binding.getGroup() != null) {
            for(SimpleFieldInfo f : binding.getGroup()) {
               chartInfo.addGroupField(createChartRef(f));
            }
         }

         if(binding.getShape() != null && chartType == GraphTypes.CHART_MAP) {
            chartInfo.setShapeField(createAestheticRef(binding.getShape()));
         }

         if(binding.getSize() != null) {
            chartInfo.setSizeField(createAestheticRef(binding.getSize()));
         }

         if(binding.getText() != null) {
            chartInfo.setTextField(createAestheticRef(binding.getText()));
         }

         if(binding.getPath() != null && chartType == GraphTypes.CHART_MAP) {
            chartInfo.setPathField(createChartRef(binding.getPath()));
         }

         if(binding.getColor() != null) {
            chartInfo.setColorField(createAestheticRef(binding.getColor()));
         }
      }

      // Gantt chart: x/y are dimensions; start/end/milestone are date aesthetics
      else if(chartInfo instanceof GanttVSChartInfo ganttInfo) {
         if(binding.getX() != null) {
            for(SimpleFieldInfo f : binding.getX()) {
               chartInfo.addXField(createChartRef(f));
            }
         }

         if(binding.getY() != null) {
            for(SimpleFieldInfo f : binding.getY()) {
               chartInfo.addYField(createChartRef(f));
            }
         }

         if(binding.getStart() != null) {
            ganttInfo.setStartField(createVSChartDimensionRef(binding.getStart()));
         }

         if(binding.getEnd() != null) {
            ganttInfo.setEndField(createVSChartDimensionRef(binding.getEnd()));
         }

         if(binding.getMilestone() != null) {
            ganttInfo.setMilestoneField(createVSChartDimensionRef(binding.getMilestone()));
            AggregateInfo ainfo = chartInfo.getAggregateInfo();
            AttributeRef attr = new AttributeRef(null, binding.getMilestone().getField());
            ainfo.addGroup(new GroupRef(attr));
            VSEventUtil.fixAggInfoByConvertRef(ainfo, VSEventUtil.CONVERT_TO_MEASURE,
                                               binding.getMilestone().getField());
         }
      }
      // Stock chart: x/y + high/low/close/open + color/text only (no shape/size)
      // Candle chart: x/y + high/low/close/open + color/shape/size/text
      else if(chartInfo instanceof CandleVSChartInfo candleInfo) {
         if(binding.getX() != null) {
            for(SimpleFieldInfo f : binding.getX()) {
               chartInfo.addXField(createChartRef(f));
            }
         }

         if(binding.getY() != null) {
            for(SimpleFieldInfo f : binding.getY()) {
               chartInfo.addYField(createChartRef(f));
            }
         }

         if(binding.getColor() != null) {
            chartInfo.setColorField(createAestheticRef(binding.getColor()));
         }

         if(binding.getText() != null) {
            chartInfo.setTextField(createAestheticRef(binding.getText()));
         }

         if(binding.getHigh() != null) {
            candleInfo.setHighField(createVSChartAggregateRef(binding.getHigh()));
         }

         if(binding.getLow() != null) {
            candleInfo.setLowField(createVSChartAggregateRef(binding.getLow()));
         }

         if(binding.getClose() != null) {
            candleInfo.setCloseField(createVSChartAggregateRef(binding.getClose()));
         }

         if(binding.getOpen() != null) {
            candleInfo.setOpenField(createVSChartAggregateRef(binding.getOpen()));
         }

         if(chartType == GraphTypes.CHART_CANDLE) {
            if(binding.getShape() != null) {
               chartInfo.setShapeField(createAestheticRef(binding.getShape()));
            }

            if(binding.getSize() != null) {
               chartInfo.setSizeField(createAestheticRef(binding.getSize()));
            }
         }
      }
      // TreeMap group: Tree Map, Sun Burst, Circle Packing, ICircle
      // x (dimension), y (measure), t (dimension hierarchy → added to X)
      else if(isTreeMapChartType(chartType)) {
         if(binding.getX() != null) {
            for(SimpleFieldInfo f : binding.getX()) {
               chartInfo.addXField(createChartRef(f));
            }
         }

         if(binding.getY() != null) {
            for(SimpleFieldInfo f : binding.getY()) {
               chartInfo.addYField(createChartRef(f));
            }
         }

         if(binding.getT() != null) {
            for(DimensionFieldInfo f : binding.getT()) {
               chartInfo.addXField(createVSChartDimensionRef(f));
            }
         }

         if(binding.getColor() != null) {
            chartInfo.setColorField(createAestheticRef(binding.getColor()));
         }

         if(binding.getShape() != null) {
            chartInfo.setShapeField(createAestheticRef(binding.getShape()));
         }

         if(binding.getSize() != null) {
            chartInfo.setSizeField(createAestheticRef(binding.getSize()));
         }

         if(binding.getText() != null) {
            chartInfo.setTextField(createAestheticRef(binding.getText()));
         }

         if(binding.getPath() != null) {
            chartInfo.setPathField(createChartRef(binding.getPath()));
         }
      }
      else {
         // Default: Bar, 3D Bar, Area, Point, Step Area, Interval, Line, Step Line, Jump Line,
         //          Pie, 3D Pie, Donut, Radar, Filled Radar, Boxplot, Waterfall, Pareto,
         //          Marimekko, Funnel, Scatter Contour
         if(binding.getX() != null) {
            for(SimpleFieldInfo f : binding.getX()) {
               chartInfo.addXField(createChartRef(f));
            }
         }

         if(binding.getY() != null) {
            for(SimpleFieldInfo f : binding.getY()) {
               chartInfo.addYField(createChartRef(f));
            }
         }

         if(binding.getGroup() != null && chartType != GraphTypes.CHART_FUNNEL &&
            chartType != GraphTypes.CHART_BOXPLOT && chartType != GraphTypes.CHART_WATERFALL &&
            chartType != GraphTypes.CHART_PARETO)
         {
            for(SimpleFieldInfo f : binding.getGroup()) {
               chartInfo.addGroupField(createChartRef(f));
            }
         }

         if(binding.getColor() != null) {
            chartInfo.setColorField(createAestheticRef(binding.getColor()));
         }

         if(binding.getShape() != null && chartType != GraphTypes.CHART_SCATTER_CONTOUR) {
            chartInfo.setShapeField(createAestheticRef(binding.getShape()));
         }

         if(binding.getSize() != null && chartType != GraphTypes.CHART_MEKKO) {
            chartInfo.setSizeField(createAestheticRef(binding.getSize()));
         }

         if(binding.getText() != null) {
            chartInfo.setTextField(createAestheticRef(binding.getText()));
         }

         if(binding.getPath() != null && (chartType == GraphTypes.CHART_LINE ||
            chartType == GraphTypes.CHART_STEP || chartType == GraphTypes.CHART_JUMP))
         {
            chartInfo.setPathField(createChartRef(binding.getPath()));
         }
      }
   }

   private boolean isTreeMapChartType(int chartType) {
      return chartType == GraphTypes.CHART_TREEMAP ||
         chartType == GraphTypes.CHART_SUNBURST ||
         chartType == GraphTypes.CHART_CIRCLE_PACKING ||
         chartType == GraphTypes.CHART_ICICLE;
   }

   private AestheticRef createAestheticRef(SimpleFieldInfo field) {
      VSAestheticRef ref = new VSAestheticRef();
      ref.setDataRef(createChartRef(field));
      return ref;
   }

   private TableVSAssembly createTableAssembly(Viewsheet vs, String name,
                                               VisualizationConfig config)
   {
      TableVSAssembly table = new TableVSAssembly(vs, name);
      table.initDefaultFormat();

      if(config != null && config.getBindingInfo() instanceof TableBinding binding
         && binding.getDetails() != null)
      {
         ColumnSelection columns = new ColumnSelection();

         for(SimpleFieldInfo field : binding.getDetails()) {
            AttributeRef attr = new AttributeRef(null, field.getField());

            if(field.getType() != null) {
               attr.setDataType(field.getType());
            }

            columns.addAttribute(new ColumnRef(attr));
         }

         table.setColumnSelection(columns);

         // A detail table's columns ARE its content, so title it with them rather than "Table".
         DataRef[] details = new DataRef[columns.getAttributeCount()];

         for(int i = 0; i < columns.getAttributeCount(); i++) {
            details[i] = columns.getAttribute(i);
         }

         applyGridTitle(table, buildGridTitle(null, null, details));
      }

      return table;
   }

   /**
    * Build a human-readable title like "Sum(amount) by sales_stage, name" from a grid binding's
    * measures + dimensions, so a crosstab/table reads like a real visualization instead of showing
    * StyleBI's bland default "Table". The plugin create path (unlike the interactive wizard's
    * VSWizardBindingHandler) does not otherwise title these assemblies. Returns null when there's
    * nothing to build from (caller then leaves the default).
    */
   private static String buildGridTitle(DataRef[] measures, DataRef[] cols, DataRef[] rows) {
      StringBuilder meas = new StringBuilder();

      if(measures != null) {
         for(DataRef m : measures) {
            String v = m == null ? null : m.toView();

            if(v != null && !v.isEmpty()) {
               if(meas.length() > 0) { meas.append(", "); }
               meas.append(v);
            }
         }
      }

      StringBuilder dims = new StringBuilder();

      for(DataRef[] group : new DataRef[][]{ cols, rows }) {
         if(group == null) { continue; }

         for(DataRef d : group) {
            String v = d == null ? null : d.toView();

            if(v != null && !v.isEmpty()) {
               if(dims.length() > 0) { dims.append(", "); }
               dims.append(v);
            }
         }
      }

      if(meas.length() > 0 && dims.length() > 0) { return meas + " by " + dims; }
      if(meas.length() > 0) { return meas.toString(); }
      if(dims.length() > 0) { return dims.toString(); }
      return null;
   }

   /** Set a derived display title on a titled assembly (crosstab/table); no-op on blank input. */
   private void applyGridTitle(VSAssembly assembly, String title) {
      if(title == null || title.isEmpty()
         || !(assembly.getVSAssemblyInfo() instanceof inetsoft.uql.viewsheet.internal.TitledVSAssemblyInfo ti))
      {
         return;
      }

      // Only fill in StyleBI's bland placeholder titles ("Chart"/"Table") or a blank — never
      // override a title that was already set to something meaningful (e.g. a chart the recommender
      // titled). This keeps the fix to genuinely-untitled assemblies (treemap, crosstab, table).
      String current = ti.getTitleValue();

      if(current == null || current.isEmpty()
         || "Chart".equals(current) || "Table".equals(current))
      {
         ti.setTitleValue(title);
      }
   }

   private CrosstabVSAssembly createCrosstabAssembly(Viewsheet vs, String name,
                                                     VisualizationConfig config)
   {
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, name);

      if(config != null && config.getBindingInfo() instanceof CrosstabBinding binding) {
         VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
         DataRef[] rows = null, cols = null, aggrs = null;

         if(binding.getRows() != null) {
            rows = binding.getRows().stream()
               .map(this::createVSDimensionRef)
               .toArray(DataRef[]::new);
            cinfo.setDesignRowHeaders(rows);
         }

         if(binding.getCols() != null) {
            cols = binding.getCols().stream()
               .map(this::createVSDimensionRef)
               .toArray(DataRef[]::new);
            cinfo.setDesignColHeaders(cols);
         }

         if(binding.getAggregates() != null) {
            aggrs = binding.getAggregates().stream()
               .map(this::createVSAggregateRef)
               .toArray(DataRef[]::new);
            cinfo.setDesignAggregates(aggrs);
         }

         crosstab.setVSCrosstabInfo(cinfo);

         // Replace the bland default "Table" title with a binding-derived one (e.g.
         // "Sum(amount) by sales_stage, name") so the crosstab reads like a real visualization.
         applyGridTitle(crosstab, buildGridTitle(aggrs, cols, rows));
      }

      return crosstab;
   }

   private GaugeVSAssembly createGaugeAssembly(Viewsheet vs, String name)
   {
      GaugeVSAssembly assembly = new GaugeVSAssembly(vs, name);
      assembly.initDefaultFormat();
      return assembly;
   }

   private TextVSAssembly createTextAssembly(Viewsheet vs, String name)
   {
      TextVSAssembly assembly = new TextVSAssembly(vs, name);
      assembly.initDefaultFormat();
      return assembly;
   }

   private ImageVSAssembly createImageAssembly(Viewsheet vs, String name,
                                               VisualizationConfig config)
   {
      ImageVSAssembly image = new ImageVSAssembly(vs, name);
      image.initDefaultFormat();

      if(config != null && config.getBindingInfo() instanceof ImageBinding binding
         && binding.getImage() != null)
      {
         image.setValue(binding.getImage());
      }

      return image;
   }

   private ChartRef createChartRef(SimpleFieldInfo field) {
      if(field instanceof MeasureFieldInfo measure) {
         return createVSChartAggregateRef(measure);
      }

      return createVSChartDimensionRef(field instanceof DimensionFieldInfo dim ? dim : null, field);
   }

   private VSChartAggregateRef createVSChartAggregateRef(MeasureFieldInfo field) {
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setColumnValue(field.getField());

      if(field.getAggregateFormula() != null) {
         ref.setFormulaValue(field.getAggregateFormula());
      }

      if(field.getSecondaryField() != null) {
         ref.setSecondaryColumnValue(field.getSecondaryField());
      }

      if(field.getCalculateInfo() != null) {
         Calculator calc = field.getCalculateInfo().toCalculator();

         if(calc != null) {
            ref.setCalculator(calc);
         }
      }

      return ref;
   }

   private VSChartDimensionRef createVSChartDimensionRef(DimensionFieldInfo field) {
      return createVSChartDimensionRef(field, field);
   }

   private VSChartDimensionRef createVSChartDimensionRef(DimensionFieldInfo dim, SimpleFieldInfo base) {
      VSChartDimensionRef ref = new VSChartDimensionRef();
      ref.setGroupColumnValue(base.getField());

      if(base.getOrder() != null) {
         ref.setOrder(base.getOrder());
      }

      if(dim != null && dim.getDateGroupLevel() != null) {
         ref.setDateLevelValue(String.valueOf(getDateGroupLevel(dim.getDateGroupLevel())));
         ref.setTimeSeries(dim.isTimeSeries());
      }

      if(dim != null && dim.getRanking() != null) {
         Ranking ranking = dim.getRanking();
         ref.setRankingNValue(String.valueOf(ranking.getRankingN()));
         ref.setRankingColValue(ranking.getRankingCol());
         ref.setRankingOptionValue(String.valueOf(ranking.getOptionValue()));
      }

      return ref;
   }

   private VSDimensionRef createVSDimensionRef(DimensionFieldInfo field) {
      VSDimensionRef ref = new VSDimensionRef();
      ref.setGroupColumnValue(field.getField());

      if(field.getOrder() != null) {
         ref.setOrder(field.getOrder());
      }

      if(field.getDateGroupLevel() != null) {
         ref.setDateLevelValue(String.valueOf(getDateGroupLevel(field.getDateGroupLevel())));
         ref.setTimeSeries(field.isTimeSeries());
      }

      if(field.getRanking() != null) {
         Ranking ranking = field.getRanking();
         ref.setRankingNValue(String.valueOf(ranking.getRankingN()));
         ref.setRankingColValue(ranking.getRankingCol());
         ref.setRankingOptionValue(String.valueOf(ranking.getOptionValue()));
      }

      return ref;
   }

   private VSAggregateRef createVSAggregateRef(MeasureFieldInfo field) {
      VSAggregateRef ref = new VSAggregateRef();
      ref.setColumnValue(field.getField());

      if(field.getAggregateFormula() != null) {
         ref.setFormulaValue(field.getAggregateFormula());
      }

      if(field.getSecondaryField() != null) {
         ref.setSecondaryColumnValue(field.getSecondaryField());
      }

      if(field.getCalculateInfo() != null) {
         Calculator calc = field.getCalculateInfo().toCalculator();

         if(calc != null) {
            ref.setCalculator(calc);
         }
      }

      return ref;
   }

   /**
    * Builds a VSAggregateRef from MeasureFieldInfo and returns its fullName.
    * This ensures consistent fullName generation across the codebase.
    */
   private String buildVSAggregateRefFullName(MeasureFieldInfo measure) {
      return buildVSAggregateRefFullName(
         measure.getField(),
         measure.getAggregateFormula(),
         measure.getSecondaryField(),
         measure.getNOrP());
   }

   /**
    * Builds a VSAggregateRef from individual parameters and returns its fullName.
    * This ensures consistent fullName generation across the codebase.
    *
    * @param columnValue    the primary column/field name
    * @param formulaValue   the aggregate formula (e.g., "Sum", "Average")
    * @param secondaryField the secondary field name for two-column formulas (may be null)
    * @param nOrP           the N or P parameter for Nth/Percentile formulas (may be null)
    *
    * @return the computed fullName from VSAggregateRef
    */
   private String buildVSAggregateRefFullName(String columnValue, String formulaValue,
                                              String secondaryField, Integer nOrP)
   {
      VSAggregateRef ref = new VSAggregateRef();
      ref.setColumnValue(columnValue);

      if(formulaValue != null) {
         ref.setFormulaValue(formulaValue);
      }

      if(secondaryField != null) {
         ref.setSecondaryColumnValue(secondaryField);
      }

      if(nOrP != null) {
         ref.setN(nOrP);
      }

      return ref.getFullName();
   }

   public static int getDateGroupLevel(String level) {
      return WizDateLevelUtil.getDateGroupLevel(level);
   }

   /**
    * Converts an integer date level constant (from XConstants/DateRangeRef) back to
    * the human-readable name used by the Wiz AI service (e.g., "year", "month").
    * This is the reverse of {@link #getDateGroupLevel(String)}.
    *
    * @param level the integer date level constant
    *
    * @return the human-readable name, or null if level is NONE_DATE_GROUP or unrecognized
    */
   public static String getDateGroupLevelName(int level) {
      return WizDateLevelUtil.getDateGroupLevelName(level);
   }

   /**
    * Returns the first assembly marked as primary in the viewsheet, or null if none exists.
    */
   private VSAssembly findPrimaryAssembly(Viewsheet vs) {
      Assembly[] assemblies = vs.getAssemblies();

      if(assemblies == null) {
         return null;
      }

      for(Assembly a : assemblies) {
         if(a instanceof VSAssembly vsAssembly && vsAssembly.isPrimary()) {
            return vsAssembly;
         }
      }

      return null;
   }

   /**
    * Applies base conditions from the condition model to the VS assembly.
    * Only base conditions are applied here; aggregate conditions require pushing to worksheet.
    */
   private void applyConditionModel(VSAssembly assembly, VisualizationConditionModel conditionModel) {
      if(conditionModel == null || conditionModel.getBaseConditions() == null ||
         conditionModel.getBaseConditions().isEmpty())
      {
         return;
      }

      if(!(assembly instanceof DataVSAssembly dataAssembly)) {
         LOG.warn("Cannot apply condition to non-data assembly type: {}", assembly.getClass().getSimpleName());
         return;
      }

      // Resolve the assembly's candidate condition fields so condition values can be coerced
      // to each field's data type (see buildConditionItem).
      ColumnSelection columns = VSUtil.getBaseColumns(dataAssembly, true);
      dataAssembly.setPreConditionList(buildConditionList(conditionModel, columns));
   }

   /**
    * Builds a ConditionList from the base conditions in the model.
    */
   private ConditionList buildConditionList(VisualizationConditionModel wizModel,
                                            ColumnSelection columns)
   {
      ConditionList conditionList = new ConditionList();

      if(wizModel != null && wizModel.getBaseConditions() != null) {
         appendConditionNodes(wizModel.getBaseConditions(), 0, conditionList, new boolean[]{ true },
                              null, columns);
      }

      return conditionList;
   }

   /**
    * Recursively appends condition nodes to {@code result}.
    *
    * @param nodes            the sibling nodes at the current level
    * @param depth            current nesting depth (0 = top-level); controls junction/item level values
    * @param result           the ConditionList being built
    * @param isFirst          single-element boolean array used to track whether any item has been
    *                         appended yet at the current level (avoids a leading junction)
    * @param dimColumnMapping optional set of DateRangeRef column names that were pushed to worksheet;
    *                         used for validating dateGroupLevel to DateRangeRef column names
    * @param columns          optional candidate condition fields used to resolve each field's data
    *                         type so condition values are coerced correctly; may be null
    */
   private void appendConditionNodes(List<VisualizationConditionModel.ConditionNode> nodes,
                                     int depth,
                                     ConditionList result,
                                     boolean[] isFirst,
                                     Set<String> dimColumnMapping,
                                     ColumnSelection columns)
   {
      for(VisualizationConditionModel.ConditionNode node : nodes) {
         if(node == null) {
            continue;
         }

         if(node instanceof VisualizationConditionModel.ConditionLeaf leaf) {
            VisualizationConditionModel.ConditionSpec spec = leaf.getCondition();

            if(spec == null || Tool.isEmptyString(spec.getField())) {
               continue;
            }

            if(!isFirst[0]) {
               result.append(new JunctionOperator(parseJunction(leaf.getJunction()), depth));
            }

            result.append(buildConditionItem(spec, depth, dimColumnMapping, columns));
            isFirst[0] = false;
         }
         else if(node instanceof VisualizationConditionModel.ConditionGroup group) {
            List<VisualizationConditionModel.ConditionNode> items = group.getItems();

            if(items == null || items.isEmpty()) {
               continue;
            }

            // Build the group's contents into a temporary list first so we can check
            // whether it produced any valid items before committing a junction.
            ConditionList groupContents = new ConditionList();
            appendConditionNodes(items, depth + 1, groupContents, new boolean[]{ true },
                                 dimColumnMapping, columns);

            if(groupContents.isEmpty()) {
               continue;
            }

            if(!isFirst[0]) {
               result.append(new JunctionOperator(parseJunction(group.getJunction()), depth));
            }

            for(int i = 0; i < groupContents.getSize(); i++) {
               if(i % 2 == 0) {
                  result.append(groupContents.getConditionItem(i));
               }
               else {
                  result.append(groupContents.getJunctionOperator(i));
               }
            }

            isFirst[0] = false;
         }
      }
   }

   /**
    * Builds a ConditionItem from a condition spec.
    * When {@code aggregateFormula} is present, produces an {@link AggregateRef}-based item
    * suitable for a post-condition (HAVING); otherwise produces an {@link AttributeRef}-based
    * item for a pre-condition (WHERE).
    *
    * @param spec             the condition specification
    * @param level            the nesting level for the condition item
    * @param dimColumnMapping optional set of DateRangeRef column names that were pushed to worksheet;
    *                         used for mapping dateGroupLevel to DateRangeRef column names
    * @param columns          optional candidate condition fields used to resolve the field's data
    *                         type; may be null
    */
   private ConditionItem buildConditionItem(VisualizationConditionModel.ConditionSpec spec,
                                            int level,
                                            Set<String> dimColumnMapping,
                                            ColumnSelection columns)
   {
      Condition condition = new Condition();
      condition.setOperation(mapConditionOperation(spec.getOperation()));
      condition.setNegated(spec.isNegated());

      if(spec.getEqual() != null) {
         condition.setEqual(spec.getEqual());
      }

      // Set the condition's data type before adding values so each value is coerced to the type
      // the field expects (e.g. "100" -> 100.0, date strings -> Date). Without this the values
      // stay as raw strings and numeric/date comparisons fail.
      String dataType = resolveConditionType(spec, columns);

      if(dataType != null) {
         condition.setType(dataType);
      }

      if(spec.getValues() != null) {
         for(VisualizationConditionModel.ValueSpec val : spec.getValues()) {
            condition.addValue(convertConditionValue(val));
         }
      }

      // Aggregate (HAVING) condition: build AggregateRef so the engine evaluates it correctly
      // against the post-aggregation result set.
      if(spec.getAggregateFormula() != null) {
         DataRef baseRef = new ColumnRef(new AttributeRef(null, spec.getField()));
         DataRef secondaryRef = spec.getSecondaryField() != null
            ? new ColumnRef(new AttributeRef(null, spec.getSecondaryField())) : null;
         AggregateFormula formula = AggregateFormula.getFormula(spec.getAggregateFormula());

         if(formula == null) {
            LOG.warn("Aggregate formula '{}' for measure '{}' is null or unrecognized; " +
                        "defaulting to SUM", spec.getAggregateFormula(), spec.getField());
            formula = AggregateFormula.SUM;
         }

         AggregateRef aggregateRef = new AggregateRef(baseRef, secondaryRef, formula);

         if(spec.getNOrP() != null && formula.hasN()) {
            aggregateRef.setN(spec.getNOrP());
         }

         return new ConditionItem(aggregateRef, condition, level);
      }

      // Base (WHERE) condition: use AttributeRef, resolving dateGroupLevel to DateRangeRef name if applicable.
      String fieldName = spec.getField();

      if(spec.getDateGroupLevel() != null) {
         int dateLevel = getDateGroupLevel(spec.getDateGroupLevel());

         if(dimColumnMapping != null && dateLevel != XConstants.NONE_DATE_GROUP) {
            String dateRangeName = DateRangeRef.getName(spec.getField(), dateLevel);

            if(dimColumnMapping.contains(dateRangeName)) {
               fieldName = dateRangeName;
            }
         }
      }

      return new ConditionItem(new AttributeRef(null, fieldName), condition, level);
   }

   /**
    * Resolves the data type that condition values should be coerced to for the given spec.
    * For aggregate (HAVING) conditions the formula's result type takes precedence (e.g. Count
    * always yields an integer regardless of the base field); a null formula result type means the
    * result keeps the base field's type (e.g. Sum, Max), so it falls through to the column lookup.
    *
    * @return the resolved {@link XSchema} data type, or null if it cannot be determined.
    */
   private String resolveConditionType(VisualizationConditionModel.ConditionSpec spec,
                                       ColumnSelection columns)
   {
      if(spec.getAggregateFormula() != null) {
         AggregateFormula formula = AggregateFormula.getFormula(spec.getAggregateFormula());

         if(formula != null && formula.getDataType() != null) {
            return formula.getDataType();
         }
      }

      if(columns != null && spec.getField() != null) {
         DataRef ref = columns.getAttribute(spec.getField());

         if(ref != null) {
            return ref.getDataType();
         }
      }

      return null;
   }

   private int parseJunction(String junction) {
      return "or".equalsIgnoreCase(junction) ? JunctionOperator.OR : JunctionOperator.AND;
   }

   private Object convertConditionValue(VisualizationConditionModel.ValueSpec val) {
      if(val == null) {
         return null;
      }

      String type = val.getType();
      Object value = val.getValue();

      if(type == null || value == null) {
         return value;
      }

      switch(type.toUpperCase()) {
      case "FIELD":
         return new AttributeRef(null, String.valueOf(value));
      case "VARIABLE":
      case "SESSION_DATA": {
         if(value instanceof String) {
            String str = (String) value;

            if(str.startsWith("$(") && str.endsWith(")")) {
               String name = str.substring(2, str.length() - 1);
               UserVariable variable = new UserVariable();
               variable.setName(name);
               variable.setAlias(name);
               variable.setValueNode(new StringValue(name));
               return variable;
            }
         }

         return value;
      }
      case "EXPRESSION": {
         ExpressionValue expr = new ExpressionValue();
         expr.setExpression(String.valueOf(value));
         expr.setType(ExpressionValue.JAVASCRIPT);
         return expr;
      }
      default:
         return value;
      }
   }

   /**
    * Maps a TypeScript {@code ConditionOperation} string to the corresponding {@link XCondition} int constant.
    */
   private int mapConditionOperation(String operation) {
      if(operation == null) {
         return XCondition.EQUAL_TO;
      }

      return switch(operation) {
         case "EQUAL_TO" -> XCondition.EQUAL_TO;
         case "ONE_OF" -> XCondition.ONE_OF;
         case "LESS_THAN" -> XCondition.LESS_THAN;
         case "GREATER_THAN" -> XCondition.GREATER_THAN;
         case "BETWEEN" -> XCondition.BETWEEN;
         case "STARTING_WITH" -> XCondition.STARTING_WITH;
         case "CONTAINS" -> XCondition.CONTAINS;
         case "LIKE" -> XCondition.LIKE;
         case "NULL" -> XCondition.NULL;
         case "DATE_IN" -> XCondition.DATE_IN;
         default -> XCondition.EQUAL_TO;
      };
   }

   /**
    * Pushes aggregation and conditions to the worksheet table.
    * When aggregate conditions exist, all groups, aggregates, base conditions (pre),
    * and aggregate conditions (post) are pushed to the worksheet level.
    * Returns a PreAggregationMapping containing dimension column mappings and pushed measure fullNames.
    */
   private PreAggregationMapping pushAggregationToWorksheet(
      CreateViewsheetResult.FlatBinding binding,
      VisualizationConditionModel conditionModel,
      AbstractTableAssembly table)
   {
      ColumnSelection cols = table.getColumnSelection(false);
      AggregateInfo aggInfo = new AggregateInfo();
      Set<String> dimColumnMapping = new HashSet<>();
      Set<String> pushedMeasureFullNames = new HashSet<>();
      boolean columnsChanged = false;

      // Add GroupRefs for dimensions
      if(binding.getDimensions() != null) {
         for(DimensionFieldInfo dim : binding.getDimensions()) {
            DataRef colRef = cols.getAttribute(dim.getField());

            if(colRef == null) {
               LOG.warn("Dimension field '{}' not found in worksheet column selection; " +
                           "skipping from aggregation", dim.getField());
               continue;
            }

            GroupRef groupRef;

            if(dim.getDateGroupLevel() != null) {
               int dateLevel = getDateGroupLevel(dim.getDateGroupLevel());
               String fullName = DateRangeRef.getName(dim.getField(), dateLevel);
               DataRef innerRef = colRef;

               if(colRef instanceof ColumnRef) {
                  innerRef = ((ColumnRef) colRef).getDataRef();
               }

               DateRangeRef rangeRef = new DateRangeRef(fullName, innerRef, dateLevel);

               if(colRef instanceof ColumnRef origCol) {
                  rangeRef.setOriginalType(origCol.getDataType());
               }

               ColumnRef dateCol = new ColumnRef(rangeRef);
               dateCol.setDataType(rangeRef.getDataType());

               // Add the DateRangeRef column to the column selection if not present
               if(cols.getAttribute(fullName) == null) {
                  int baseIdx = cols.indexOfAttribute(colRef);

                  if(baseIdx >= 0) {
                     cols.addAttribute(baseIdx, dateCol);
                  }
                  else {
                     cols.addAttribute(dateCol);
                  }

                  columnsChanged = true;
               }

               groupRef = new GroupRef(dateCol);
               groupRef.setTimeSeries(dim.isTimeSeries());
               groupRef.setDateGroup(dateLevel);
               dimColumnMapping.add(fullName);
            }
            else {
               groupRef = new GroupRef(colRef);
            }

            aggInfo.addGroup(groupRef);
         }
      }

      // Add AggregateRefs for measures
      if(binding.getMeasures() != null) {
         for(MeasureFieldInfo measure : binding.getMeasures()) {
            DataRef colRef = cols.getAttribute(measure.getField());

            if(colRef == null) {
               LOG.warn("Measure field '{}' not found in worksheet column selection; " +
                           "skipping from aggregation", measure.getField());
               continue;
            }

            AggregateFormula formula = AggregateFormula.getFormula(measure.getAggregateFormula());

            if(formula == null) {
               LOG.warn("Aggregate formula '{}' for measure '{}' is null or unrecognized; " +
                           "defaulting to SUM", measure.getAggregateFormula(), measure.getField());
               formula = AggregateFormula.SUM;
            }

            // Look up secondary field for formulas that require it (Correlation, Covariance, WeightedAverage, SumWT)
            DataRef secondaryColRef = null;

            if(measure.getSecondaryField() != null) {
               secondaryColRef = cols.getAttribute(measure.getSecondaryField());
            }

            AggregateRef aggRef = new AggregateRef(colRef, secondaryColRef, formula);

            if(measure.getNOrP() != null && formula.hasN()) {
               aggRef.setN(measure.getNOrP());
            }

            aggInfo.addAggregate(aggRef);
            pushedMeasureFullNames.add(buildVSAggregateRefFullName(measure));
         }
      }

      // Update the column selection if we added new DateRangeRef columns
      if(columnsChanged) {
         table.setColumnSelection(cols, false);
      }

      table.setAggregateInfo(aggInfo);
      table.setAggregate(!aggInfo.isEmpty());

      // Apply base conditions as pre-conditions and aggregate conditions as post-conditions
      applyConditionsToWorksheet(table, conditionModel, dimColumnMapping);

      return new PreAggregationMapping(dimColumnMapping, pushedMeasureFullNames);
   }

   /**
    * Applies base conditions as pre-conditions and aggregate conditions as post-conditions.
    *
    * @param table            the worksheet table assembly
    * @param conditionModel   the condition model containing base and aggregate conditions
    * @param dimColumnMapping set of DateRangeRef column names that were pushed to worksheet (for date grouping)
    */
   private void applyConditionsToWorksheet(
      AbstractTableAssembly table,
      VisualizationConditionModel conditionModel,
      Set<String> dimColumnMapping)
   {
      if(conditionModel == null) {
         return;
      }

      // Candidate condition fields, used to coerce condition values to each field's data type.
      ColumnSelection columns = table.getColumnSelection(false);

      // Apply base conditions as pre-conditions (WHERE equivalent)
      if(conditionModel.getBaseConditions() != null && !conditionModel.getBaseConditions().isEmpty()) {
         ConditionList preCondList = new ConditionList();
         appendConditionNodes(conditionModel.getBaseConditions(), 0, preCondList,
                              new boolean[]{ true }, null, columns);

         if(!preCondList.isEmpty()) {
            table.setPreConditionList(preCondList);
         }
      }

      // Apply aggregate conditions as post-conditions (HAVING equivalent)
      // Aggregate conditions are built as AggregateRef items (field+formula+secondaryField+nOrP)
      // so the engine matches them against post-aggregation (HAVING) result columns.
      // Use dimColumnMapping to translate dimension field+dateGroupLevel to DateRangeRef column names
      if(conditionModel.getAggregateConditions() != null && !conditionModel.getAggregateConditions().isEmpty()) {
         ConditionList postCondList = new ConditionList();
         appendConditionNodes(conditionModel.getAggregateConditions(), 0, postCondList,
                              new boolean[]{ true }, dimColumnMapping, columns);

         if(!postCondList.isEmpty()) {
            table.setPostConditionList(postCondList);
         }
      }
   }

   /**
    * Updates VS assembly binding to use pre-aggregated columns from worksheet.
    * Sets formula to NONE and date level to NONE since aggregation is done in worksheet.
    */
   private void updateVsBindingForPreAggregation(
      VSAssembly assembly,
      PreAggregationMapping preAggMapping)
   {
      if(assembly instanceof ChartVSAssembly chart) {
         updateChartBindingForPreAggregation(chart.getVSChartInfo(), preAggMapping);
      }
      else if(assembly instanceof CrosstabVSAssembly crosstab) {
         updateCrosstabBindingForPreAggregation(crosstab.getVSCrosstabInfo(), preAggMapping);
      }
      else if(assembly instanceof OutputVSAssembly output) {
         updateOutputBindingForPreAggregation(output, preAggMapping);
      }
   }

   private void updateChartBindingForPreAggregation(
      VSChartInfo chartInfo,
      PreAggregationMapping preAggMapping)
   {
      Set<String> dimColumnMapping = preAggMapping.dimColumnMapping();
      Set<String> measureFullNames = preAggMapping.pushedMeasureFullNames();

      // Update X fields
      for(int i = 0; i < chartInfo.getXFieldCount(); i++) {
         ChartRef ref = chartInfo.getXField(i);
         updateChartRefForPreAggregation(ref, dimColumnMapping, measureFullNames);
      }

      // Update Y fields
      for(int i = 0; i < chartInfo.getYFieldCount(); i++) {
         ChartRef ref = chartInfo.getYField(i);
         updateChartRefForPreAggregation(ref, dimColumnMapping, measureFullNames);
      }

      // Update group fields
      for(int i = 0; i < chartInfo.getGroupFieldCount(); i++) {
         ChartRef ref = chartInfo.getGroupField(i);
         updateChartRefForPreAggregation(ref, dimColumnMapping, measureFullNames);
      }

      // Update aesthetic refs
      updateAestheticRefForPreAggregation(chartInfo.getColorField(), dimColumnMapping, measureFullNames);
      updateAestheticRefForPreAggregation(chartInfo.getShapeField(), dimColumnMapping, measureFullNames);
      updateAestheticRefForPreAggregation(chartInfo.getSizeField(), dimColumnMapping, measureFullNames);
      updateAestheticRefForPreAggregation(chartInfo.getTextField(), dimColumnMapping, measureFullNames);
   }

   private void updateChartRefForPreAggregation(
      ChartRef ref,
      Set<String> dimColumnMapping,
      Set<String> pushedMeasureFullNames)
   {
      if(ref == null) {
         return;
      }

      if(ref instanceof VSChartAggregateRef aggRef) {
         // If this measure was pushed to worksheet, just set formula to NONE
         // The column name stays the same since aggRef.getFullName() already matches worksheet output
         if(pushedMeasureFullNames.contains(aggRef.getFullName())) {
            aggRef.setColumnValue(aggRef.getName());
            aggRef.setFormulaValue(AggregateFormula.NONE.getFormulaName());
         }
      }
      else if(ref instanceof VSChartDimensionRef dimRef) {
         if(dimColumnMapping.contains(dimRef.getFullName())) {
            dimRef.setGroupColumnValue(dimRef.getFullName());
            dimRef.setDateLevelValue(null);
            dimRef.setDateLevel(DateRangeRef.NONE);
         }
      }
   }

   private void updateAestheticRefForPreAggregation(
      AestheticRef aref,
      Set<String> dimColumnMapping,
      Set<String> pushedMeasureFullNames)
   {
      if(aref == null) {
         return;
      }

      DataRef dataRef = aref.getDataRef();

      if(dataRef instanceof ChartRef chartRef) {
         updateChartRefForPreAggregation(chartRef, dimColumnMapping, pushedMeasureFullNames);
      }
   }

   private void updateCrosstabBindingForPreAggregation(
      VSCrosstabInfo crosstabInfo,
      PreAggregationMapping preAggMapping)
   {
      Set<String> dimColumnMapping = preAggMapping.dimColumnMapping();
      Set<String> pushedMeasureFullNames = preAggMapping.pushedMeasureFullNames();

      // Update row and col headers
      updateDimensionRefsForPreAggregation(crosstabInfo.getDesignRowHeaders(), dimColumnMapping);
      updateDimensionRefsForPreAggregation(crosstabInfo.getDesignColHeaders(), dimColumnMapping);

      // Update aggregates
      updateAggregateRefsForPreAggregation(crosstabInfo.getDesignAggregates(), pushedMeasureFullNames);
   }

   /**
    * Updates VSDimensionRef elements in the array for pre-aggregation.
    * For dimensions with date grouping that were pushed to worksheet, clears the date level.
    */
   private void updateDimensionRefsForPreAggregation(DataRef[] refs, Set<String> dimColumnMapping) {
      if(refs == null) {
         return;
      }

      for(DataRef ref : refs) {
         if(ref instanceof VSDimensionRef dimRef) {
            int dateLevel = dimRef.getDateLevel();

            if(XSchema.isDateType(dimRef.getDataType()) && dateLevel != XConstants.NONE_DATE_GROUP
               && dimColumnMapping.contains(dimRef.getFullName()))
            {
               dimRef.setGroupColumnValue(dimRef.getFullName());
               dimRef.setDateLevelValue(null);
               dimRef.setDateLevel(DateRangeRef.NONE);
            }
         }
      }
   }

   /**
    * Updates VSAggregateRef elements in the array for pre-aggregation.
    * For measures that were pushed to worksheet, sets formula to NONE.
    */
   private void updateAggregateRefsForPreAggregation(DataRef[] refs, Set<String> pushedMeasureFullNames) {
      if(refs == null) {
         return;
      }

      for(DataRef ref : refs) {
         if(ref instanceof VSAggregateRef aggRef) {
            String fullName = aggRef.getFullName();

            if(pushedMeasureFullNames.contains(fullName)) {
               aggRef.setColumnValue(aggRef.getName());
               aggRef.setFormulaValue(AggregateFormula.NONE.getFormulaName());
            }
         }
      }
   }

   private void updateOutputBindingForPreAggregation(
      OutputVSAssembly output,
      PreAggregationMapping preAggMapping)
   {
      ScalarBindingInfo sbinfo = output.getScalarBindingInfo();

      if(sbinfo == null) {
         return;
      }

      Set<String> pushedMeasureFullNames = preAggMapping.pushedMeasureFullNames();
      DataRef column = sbinfo.getColumn();

      if(column != null && sbinfo.getAggregateValue() != null) {
         // Use design-time getColumn2Value() to match collectOutputFlatBinding, not resolved
         // getSecondaryColumn().getName() which can return a different string
         String col2 = sbinfo.getColumn2Value();
         String fullName = buildVSAggregateRefFullName(
            sbinfo.getColumnValue(),
            sbinfo.getAggregateValue(),
            col2 != null && !col2.isEmpty() ? col2 : null,
            sbinfo.getN() != 0 ? sbinfo.getN() : null);

         if(pushedMeasureFullNames.contains(fullName)) {
            sbinfo.setAggregateValue(AggregateFormula.NONE.getFormulaName()); // Data already aggregated
         }
      }
   }

   /**
    * Deletes the persisted viewsheet identified by the given asset entry identifier.
    *
    * @param identifier the identifier returned by a previous {@link #createViewsheet} call
    * @param user       the current user
    *
    * @throws IllegalArgumentException if the identifier cannot be parsed
    * @throws Exception if the repository removal fails
    */
   public void deleteViewsheet(String identifier, Principal user) throws Exception {
      AssetEntry entry = AssetEntry.createAssetEntry(identifier);

      if(entry == null) {
         throw new IllegalArgumentException("Cannot parse viewsheet identifier: " + identifier);
      }

      String path = entry.getPath();

      if(path == null || !path.startsWith(WizVisualizationService.VISUALIZATION_ROOT_FOLDER_PATH + "/")) {
         throw new IllegalArgumentException(
            "Viewsheet is not in the managed visualizations folder and cannot be deleted: " + path);
      }

      AssetEntry wsEntry = null;

      try {
         // AssetContent.NO_DATA strips assembly/embeddedData nodes but preserves
         // <worksheetEntry>, so getBaseEntry() is always populated at this level.
         AbstractSheet sheet = engine.getSheet(entry, user, true, AssetContent.NO_DATA);

         if(sheet instanceof Viewsheet vs) {
            AssetEntry baseEntry = vs.getBaseEntry();

            if(baseEntry != null && baseEntry.getPath() != null &&
               baseEntry.getPath().startsWith(GenerateWsService.WORKSHEET_ROOT_FOLDER_PATH + "/"))
            {
               wsEntry = baseEntry;
            }
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to retrieve source worksheet entry for viewsheet [{}]",
                  identifier, e);
      }

      engine.removeSheet(entry, user, true);

      // Wiz enforces a strict 1-viewsheet-to-1-worksheet contract: each wiz viewsheet
      // owns its worksheet exclusively, so no dependency check is needed before deletion.
      if(wsEntry != null) {
         try {
            engine.removeSheet(wsEntry, user, true);
         }
         catch(Exception e) {
            LOG.warn("Failed to delete source worksheet [{}] for viewsheet [{}]",
                     wsEntry.toIdentifier(), identifier, e);
         }
      }
   }

   /**
    * Removes a single assembly from a live runtime viewsheet and persists the change
    * back to the managed visualization asset so it survives runtime expiry. Idempotent —
    * a missing/expired runtime or an already-absent assembly is treated as success.
    *
    * @param runtimeId    the runtime viewsheet id (visualizationId on the wiz side)
    * @param assemblyName the assembly to remove
    * @param user         the current user
    */
   public void removeVisualization(String runtimeId, String assemblyName, Principal user)
      throws Exception
   {
      if(runtimeId == null || runtimeId.isEmpty() || assemblyName == null || assemblyName.isEmpty()) {
         throw new IllegalArgumentException("runtimeId and assemblyName are required");
      }

      RuntimeViewsheet rvs;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, user);
      }
      catch(Exception e) {
         LOG.warn("Runtime viewsheet [{}] unavailable; skipping assembly removal: {}",
                  runtimeId, e.getMessage());
         return;
      }

      if(rvs == null) {
         LOG.warn("Runtime viewsheet [{}] not found; skipping assembly removal", runtimeId);
         return;
      }

      Viewsheet vs = rvs.getViewsheet();

      if(vs == null || vs.getAssembly(assemblyName) == null) {
         return; // idempotent: nothing to remove
      }

      vs.removeAssembly(assemblyName);
      rvs.getViewsheetSandbox().ifPresent(box -> {
         box.resetDataMap(assemblyName);

         try {
            box.clearGraph(assemblyName);
         }
         catch(Exception ignore) {}
      });

      // Persist the removal so it survives runtime expiry. Only write back when the runtime
      // is backed by a managed visualization entry (mirrors deleteViewsheet's folder guard);
      // a transient/unsaved runtime has no persistent entry and is left runtime-only.
      AssetEntry entry = rvs.getEntry();
      String path = entry != null ? entry.getPath() : null;

      if(path != null &&
         (path.startsWith(WizVisualizationService.VISUALIZATION_ROOT_FOLDER_PATH + "/") ||
          path.startsWith(WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH + "/")))
      {
         engine.setSheet(entry, vs, user, true);
      }
   }

   /**
    * Holds the mapping information from pushAggregationToWorksheet.
    *
    * @param dimColumnMapping       set of DateRangeRef column names (for date grouping) that were pushed to worksheet
    * @param pushedMeasureFullNames set of measure fullNames (e.g., "Sum(Amount)") that were pushed to worksheet
    */
   private record PreAggregationMapping(
      Set<String> dimColumnMapping,
      Set<String> pushedMeasureFullNames
   )
   {
   }

   private final ViewsheetService viewsheetService;
   private final AssetRepository engine;

   private static final Logger LOG = LoggerFactory.getLogger(WizVsService.class);
   private static final Map<Class<?>, BiFunction<Viewsheet, String, VSAssembly>> ASSEMBLY_FACTORIES = Map.of(
      ChartVSAssembly.class, ChartVSAssembly::new,
      CrosstabVSAssembly.class, CrosstabVSAssembly::new,
      TableVSAssembly.class, TableVSAssembly::new,
      GaugeVSAssembly.class, GaugeVSAssembly::new,
      TextVSAssembly.class, TextVSAssembly::new
   );
   static final int MAX_ROWS = 10_000;
}
