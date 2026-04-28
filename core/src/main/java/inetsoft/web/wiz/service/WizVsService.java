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
import inetsoft.report.TableLens;
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
import inetsoft.uql.schema.StringValue;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Tool;
import inetsoft.web.composer.wiz.service.VisualizationService;
import inetsoft.web.wiz.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

@Service
public class WizVsService {
   public WizVsService(ViewsheetService viewsheetService, AssetRepository engine) {
      this.viewsheetService = viewsheetService;
      this.engine = engine;
   }

   public CreateViewsheetResult createViewsheet(CreateVisualizationModel model, Principal user) throws Exception {
      final boolean modificationOnly = model.getConfig() == null && model.getConditionModel() != null;
      String runtimeId = model.getRuntimeId();
      boolean createdRuntimeId = false;

      // Modification-only: capture the source viewsheet before opening a fresh runtime.
      // The source is never mutated; the result is an independent clone.
      Viewsheet cloneSource = null;

      if(modificationOnly) {
         if(Tool.isEmptyString(runtimeId)) {
            throw new IllegalArgumentException("runtimeId is required for condition-only modifications");
         }

         cloneSource = getValidatedViewsheet(viewsheetService.getViewsheet(runtimeId, user));
         Viewsheet.WizInfo wizInfo = new Viewsheet.WizInfo(true, null, null);
         runtimeId = viewsheetService.openTemporaryViewsheet(null, null, user, wizInfo);
         createdRuntimeId = true;
      }
      else if(Tool.isEmptyString(runtimeId)) {
         Viewsheet.WizInfo wizInfo = new Viewsheet.WizInfo(true, null, null);
         runtimeId = viewsheetService.openTemporaryViewsheet(null, null, user, wizInfo);
         createdRuntimeId = true;
      }

      try {
         RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, user);
         Viewsheet vs = getValidatedViewsheet(rvs);

         final Viewsheet targetVs;
         final VSAssembly assembly;
         // Only relevant for the incremental standard path (non-null when base entry may be mutated).
         AssetEntry previousBaseEntry = null;

         if(modificationOnly) {
            // Clone the source viewsheet — shares the same base worksheet but has an independent
            // assembly tree, so applying the condition does not affect the original.
            targetVs = cloneSource.clone();
            assembly = findPrimaryAssembly(targetVs);

            if(assembly == null) {
               throw new IllegalStateException("No primary assembly found in cloned viewsheet");
            }
         }
         else {
            SourceContext ctx = resolveSourceContext(model, user);

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
            assembly = createAssembly(targetVs, model.getVisualizationType(), assemblyName,
                                      ctx.config(), ctx.primaryAssemblyName());

            if(assembly == null) {
               throw new IllegalArgumentException("Unsupported visualization type: " + model.getVisualizationType());
            }

            targetVs.addAssembly(assembly);
            assembly.setPrimary(true);
         }

         applyConditionModel(assembly, model.getConditionModel());

         // Always call setViewsheet so the sandbox picks up the updated viewsheet object.
         Viewsheet previousVs = rvs.getViewsheet();
         rvs.setViewsheet(targetVs);

         try {
            CreateViewsheetResult result = executeAndExtract(rvs, assembly);
            result.setBinding(collectFlatBinding(assembly));
            result.setAssemblyName(assembly.getName());

            if(createdRuntimeId) {
               result.setRuntimeId(runtimeId);
            }

            // Modification-only always produces a new asset entry (null = auto-generate UUID).
            String identifierToUse = modificationOnly ? null : model.getViewsheetIdentifier();
            result.setViewsheetIdentifier(persistViewsheet(targetVs, identifierToUse, user));

            return result;
         }
         catch(Exception e) {
            // Rollback: restore the previous viewsheet state.
            rvs.setViewsheet(previousVs);

            if(!createdRuntimeId) {
               // In incremental mode targetVs == previousVs; remove the assembly and restore
               // the base entry to leave the viewsheet in exactly its pre-call state.
               previousVs.removeAssembly(assembly.getName());
               previousVs.setBaseEntry(previousBaseEntry);
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

         targetVs.addAssembly(assembly);
         assembly.setPrimary(true);
         rvs.setViewsheet(targetVs);

         // Execute to validate; result is not needed.
         executeAndExtract(rvs, assembly);

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

      AssetEntry sourceWs;

      try {
         sourceWs = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                   config.getData().getSource(), null);
      }
      catch(Exception e) {
         throw new IllegalArgumentException("Datasource is invalid", e);
      }

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
   private CreateViewsheetResult executeAndExtract(RuntimeViewsheet rvs, VSAssembly assembly)
      throws Exception
   {
      Optional<ViewsheetSandbox> viewsheetSandbox = rvs.getViewsheetSandbox();

      if(viewsheetSandbox.isEmpty()) {
         throw new IllegalStateException("ViewsheetSandbox is empty");
      }

      viewsheetSandbox.get().executeView(assembly.getName(), true);

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
    * Extracts aggregated chart data from the viewsheet sandbox after the assembly is created.
    * Returns a result with null headers/rows if data is unavailable (best-effort).
    */
   private CreateViewsheetResult extractChartData(RuntimeViewsheet rvs, String assemblyName) {
      try {
         Optional<ViewsheetSandbox> boxOpt = rvs.getViewsheetSandbox();

         if(boxOpt.isEmpty()) {
            return new CreateViewsheetResult();
         }

         ViewsheetSandbox box = boxOpt.get();
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

         return result;
      }
      catch(Exception e) {
         LOG.warn("Failed to extract chart data after viewsheet creation for assembly '{}': {}",
                  assemblyName, e.getMessage());
         return new CreateViewsheetResult();
      }
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
    */
   private CreateViewsheetResult.FlatBinding collectFlatBinding(VSAssembly assembly) {
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

      return new CreateViewsheetResult.FlatBinding(dimensions, measures);
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
            DimensionFieldInfo info = new DimensionFieldInfo();
            info.setField(dim.getGroupColumnValue());
            info.setFullName(dim.getFullName());
            dimensions.add(info);
         }
      }
      else if(ref instanceof VSAggregateRef agg) {
         if(seen.add(agg.getFullName())) {
            MeasureFieldInfo info = new MeasureFieldInfo();
            info.setField(agg.getColumnValue());
            info.setFullName(agg.getFullName());
            info.setAggregateFormula(agg.getFormulaValue());
            measures.add(info);
         }
      }
   }

   /**
    * Collects dimensions from row/col headers and measures from aggregates of a crosstab.
    */
   private CreateViewsheetResult.FlatBinding collectCrosstabFlatBinding(VSCrosstabInfo cinfo) {
      List<DimensionFieldInfo> dimensions = new ArrayList<>();
      List<MeasureFieldInfo> measures = new ArrayList<>();

      if(cinfo.getDesignRowHeaders() != null) {
         for(DataRef ref : cinfo.getDesignRowHeaders()) {
            if(ref instanceof VSDimensionRef dim) {
               DimensionFieldInfo info = new DimensionFieldInfo();
               info.setField(dim.getGroupColumnValue());
               info.setFullName(dim.getFullName());
               dimensions.add(info);
            }
         }
      }

      if(cinfo.getDesignColHeaders() != null) {
         for(DataRef ref : cinfo.getDesignColHeaders()) {
            if(ref instanceof VSDimensionRef dim) {
               DimensionFieldInfo info = new DimensionFieldInfo();
               info.setField(dim.getGroupColumnValue());
               info.setFullName(dim.getFullName());
               dimensions.add(info);
            }
         }
      }

      if(cinfo.getDesignAggregates() != null) {
         for(DataRef ref : cinfo.getDesignAggregates()) {
            if(ref instanceof VSAggregateRef agg) {
               MeasureFieldInfo info = new MeasureFieldInfo();
               info.setField(agg.getColumnValue());
               info.setFullName(agg.getFullName());
               info.setAggregateFormula(agg.getFormulaValue());
               measures.add(info);
            }
         }
      }

      if(dimensions.isEmpty() && measures.isEmpty()) {
         return null;
      }

      return new CreateViewsheetResult.FlatBinding(dimensions, measures);
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

      MeasureFieldInfo info = new MeasureFieldInfo();
      info.setField(sbinfo.getColumnValue());
      info.setFullName(ref.getFullName());
      info.setAggregateFormula(sbinfo.getAggregateValue());

      return new CreateViewsheetResult.FlatBinding(List.of(), List.of(info));
   }

   /**
    * Saves the viewsheet to the visualizations root folder.
    *
    * <p>If {@code existingIdentifier} is non-empty the viewsheet is written to that exact
    * entry (overwrite / update).  Otherwise a new UUID-named entry is created under
    * {@link VisualizationService#VISUALIZATION_ROOT_FOLDER_PATH}.
    *
    * @param vs                 the viewsheet to persist
    * @param existingIdentifier optional identifier returned from a previous call; may be null
    * @param user               the current user
    *
    * @return the {@link AssetEntry#toIdentifier() identifier} of the saved entry
    *
    * @throws Exception if the entry identifier is invalid or the repository save fails
    */
   private String persistViewsheet(Viewsheet vs, String existingIdentifier, Principal user)
      throws Exception
   {
      final AssetEntry entry;

      if(!Tool.isEmptyString(existingIdentifier)) {
         entry = AssetEntry.createAssetEntry(existingIdentifier);

         if(entry == null) {
            throw new IllegalArgumentException("Cannot parse viewsheetIdentifier: " + existingIdentifier);
         }

         String existingPath = entry.getPath();

         if(existingPath == null ||
            !existingPath.startsWith(VisualizationService.VISUALIZATION_ROOT_FOLDER_PATH + "/"))
         {
            throw new IllegalArgumentException(
               "viewsheetIdentifier points outside the managed visualizations folder: " + existingPath);
         }
      }
      else {
         IdentityID pId = IdentityID.getIdentityIDFromKey(user.getName());
         AssetEntry folder = new AssetEntry(
            AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER,
            VisualizationService.VISUALIZATION_ROOT_FOLDER_PATH, null);

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
         }

         String uuid = UUID.randomUUID().toString();
         String path = VisualizationService.VISUALIZATION_ROOT_FOLDER_PATH + "/" + uuid;
         entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, path, pId);
      }

      viewsheetService.setViewsheet(vs, entry, user, true, true);
      return entry.toIdentifier();
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
         case "3d_bar" -> GraphTypes.CHART_3D_BAR;
         case "area" -> GraphTypes.CHART_AREA;
         case "point" -> GraphTypes.CHART_POINT;
         case "step_area" -> GraphTypes.CHART_STEP_AREA;
         case "interval" -> GraphTypes.CHART_INTERVAL;
         case "line" -> GraphTypes.CHART_LINE;
         case "step_line" -> GraphTypes.CHART_STEP;
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
      VSChartInfo chartInfo = createVSChartInfo(chartType);
      chartInfo.setChartType(chartType);
      chart.setVSChartInfo(chartInfo);

      createGeoFields(config, chartInfo);

      if(config != null && config.getBindingInfo() instanceof ChartBinding binding) {
         applyChartBinding(chartInfo, binding, chartType);
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
               catch(Exception ignored) {
                  throw new RuntimeException("Invalid layer " + layerConfig.getLayer());
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
      }

      return table;
   }

   private CrosstabVSAssembly createCrosstabAssembly(Viewsheet vs, String name,
                                                     VisualizationConfig config)
   {
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, name);

      if(config != null && config.getBindingInfo() instanceof CrosstabBinding binding) {
         VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();

         if(binding.getRows() != null) {
            DataRef[] rows = binding.getRows().stream()
               .map(this::createVSDimensionRef)
               .toArray(DataRef[]::new);
            cinfo.setDesignRowHeaders(rows);
         }

         if(binding.getCols() != null) {
            DataRef[] cols = binding.getCols().stream()
               .map(this::createVSDimensionRef)
               .toArray(DataRef[]::new);
            cinfo.setDesignColHeaders(cols);
         }

         if(binding.getAggregates() != null) {
            DataRef[] aggrs = binding.getAggregates().stream()
               .map(this::createVSAggregateRef)
               .toArray(DataRef[]::new);
            cinfo.setDesignAggregates(aggrs);
         }

         crosstab.setVSCrosstabInfo(cinfo);
      }

      return crosstab;
   }

   private GaugeVSAssembly createGaugeAssembly(Viewsheet vs, String name)
   {
      return new GaugeVSAssembly(vs, name);
   }

   private TextVSAssembly createTextAssembly(Viewsheet vs, String name)
   {
      return new TextVSAssembly(vs, name);
   }

   private ImageVSAssembly createImageAssembly(Viewsheet vs, String name,
                                               VisualizationConfig config)
   {
      ImageVSAssembly image = new ImageVSAssembly(vs, name);

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

      return ref;
   }

   private VSChartDimensionRef createVSChartDimensionRef(DimensionFieldInfo field) {
      return createVSChartDimensionRef(field, field);
   }

   private VSChartDimensionRef createVSChartDimensionRef(DimensionFieldInfo dim, SimpleFieldInfo base) {
      VSChartDimensionRef ref = new VSChartDimensionRef();
      ref.setGroupColumnValue(base.getField());

      if(dim != null && dim.getDateGroupLevel() != null) {
         ref.setDateLevelValue(String.valueOf(getDateGroupLevel(dim.getDateGroupLevel())));
      }

      if(dim != null && dim.getRanking() != null) {
         Ranking ranking = dim.getRanking();
         ref.setRankingN(ranking.getRankingN());
         ref.setRankingCol(ranking.getRankingCol());
         ref.setOrder(ranking.getOptionValue());
      }

      return ref;
   }

   private VSDimensionRef createVSDimensionRef(DimensionFieldInfo field) {
      VSDimensionRef ref = new VSDimensionRef();
      ref.setGroupColumnValue(field.getField());

      if(field.getDateGroupLevel() != null) {
         ref.setDateLevelValue(String.valueOf(getDateGroupLevel(field.getDateGroupLevel())));
      }

      if(field.getRanking() != null) {
         Ranking ranking = field.getRanking();
         ref.setRankingN(ranking.getRankingN());
         ref.setRankingCol(ranking.getRankingCol());
         ref.setOrder(ranking.getOptionValue());
      }

      return ref;
   }

   private VSAggregateRef createVSAggregateRef(MeasureFieldInfo field) {
      VSAggregateRef ref = new VSAggregateRef();
      ref.setColumnValue(field.getField());

      if(field.getAggregateFormula() != null) {
         ref.setFormulaValue(field.getAggregateFormula());
      }

      return ref;
   }

   private int getDateGroupLevel(String level) {
      if(level == null) {
         return XConstants.NONE_DATE_GROUP;
      }

      // Map dateLevels.ts 'name' values to DateRangeRef expected format
      String mappedLevel = switch(level.toLowerCase()) {
         // Interval levels
         case "year" -> "Year";
         case "quarter" -> "Quarter";
         case "month" -> "Month";
         case "week" -> "Week";
         case "day" -> "Day";
         case "hour" -> "Hour";
         case "minute" -> "Minute";
         case "second" -> "Second";
         // Part levels
         case "quarter of year" -> "QuarterOfYear";
         case "month of year" -> "MonthOfYear";
         case "week of year" -> "WeekOfYear";
         case "week of month" -> "WeekOfMonth";
         case "day of year" -> "DayOfYear";
         case "day of month" -> "DayOfMonth";
         case "day of week" -> "DayOfWeek";
         case "hour of day" -> "HourOfDay";
         case "minute of hour" -> "MinuteOfHour";
         case "second of minute" -> "SecondOfMinute";
         // Full week levels
         case "year of week" -> "YearOfWeek";
         case "quarter of week" -> "QuarterOfWeek";
         case "month of week" -> "MonthOfWeek";
         case "quarter of week part" -> "QuarterOfWeekN";
         case "month of week part" -> "MonthOfWeekN";
         // None
         case "none" -> null;
         default -> throw new IllegalArgumentException("Unsupported date level: " + level);
      };

      if(mappedLevel == null) {
         return XConstants.NONE_DATE_GROUP;
      }

      return DateRangeRef.getDateRangeOption(mappedLevel);
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
    * Applies the given {@link VisualizationConditionModel} to {@code assembly} as a pre-condition list.
    * No-ops silently when the model is null, empty, or the assembly is not a data assembly.
    */
   private void applyConditionModel(VSAssembly assembly, VisualizationConditionModel conditionModel) {
      if(conditionModel == null || conditionModel.getConditions() == null ||
         conditionModel.getConditions().isEmpty())
      {
         return;
      }

      if(!(assembly instanceof DataVSAssembly dataAssembly)) {
         LOG.warn("Cannot apply condition to non-data assembly type: {}", assembly.getClass().getSimpleName());
         return;
      }

      dataAssembly.setPreConditionList(buildConditionList(conditionModel));
   }

   /**
    * Converts a {@link VisualizationConditionModel} into a {@link ConditionList}.
    * Junction operators are inserted between consecutive condition entries.
    */
   private ConditionList buildConditionList(VisualizationConditionModel wizModel) {
      ConditionList conditionList = new ConditionList();
      List<VisualizationConditionModel.Entry> entries = wizModel.getConditions();

      for(int i = 0; i < entries.size(); i++) {
         VisualizationConditionModel.Entry entry = entries.get(i);

         if(i > 0) {
            int junctionType = "or".equalsIgnoreCase(entry.getJunction())
               ? JunctionOperator.OR
               : JunctionOperator.AND;
            conditionList.append(new JunctionOperator(junctionType, 0));
         }

         VisualizationConditionModel.ConditionSpec spec = entry.getCondition();

         if(spec == null) {
            continue;
         }

         AttributeRef attr = new AttributeRef(null, spec.getField());
         Condition condition = new Condition();
         condition.setOperation(mapConditionOperation(spec.getOperation()));
         condition.setNegated(spec.isNegated());

         if(spec.getEqual() != null) {
            condition.setEqual(spec.getEqual());
         }

         if(spec.getValues() != null) {
            for(VisualizationConditionModel.ValueSpec val : spec.getValues()) {
               condition.addValue(convertConditionValue(val));
            }
         }

         ConditionItem item = new ConditionItem(attr, condition, 0);
         conditionList.append(item);
      }

      return conditionList;
   }

   private Object convertConditionValue(VisualizationConditionModel.ValueSpec val) {
      if(val == null) {
         return null;
      }

      String type = val.getType();
      Object value = val.getValue();

      if(type == null) {
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
         default -> throw new IllegalArgumentException("Unknown condition operation: " + operation);
      };
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

      if(path == null || !path.startsWith(VisualizationService.VISUALIZATION_ROOT_FOLDER_PATH + "/")) {
         throw new IllegalArgumentException(
            "Viewsheet is not in the managed visualizations folder and cannot be deleted: " + path);
      }

      engine.removeSheet(entry, user, true);
   }

   private final ViewsheetService viewsheetService;
   private final AssetRepository engine;

   private static final Logger LOG = LoggerFactory.getLogger(WizVsService.class);
   static final int MAX_ROWS = 10_000;
}
