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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.vswizard.handler.VSWizardBindingHandler;
import inetsoft.web.vswizard.model.VSWizardData;
import inetsoft.web.vswizard.model.recommender.*;
import inetsoft.web.vswizard.recommender.VSDefaultRecommendationFactory;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import inetsoft.web.vswizard.recommender.chart.ChartCombinationUtil;
import inetsoft.web.vswizard.recommender.chart.ChartPreference;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.web.binding.model.graph.CalculateInfo;
import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.model.BindingInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
                                WizVsService wizVsService)
   {
      this.viewsheetService = viewsheetService;
      this.engine = engine;
      this.temporaryInfoService = temporaryInfoService;
      this.bindingHandler = bindingHandler;
      this.defaultRecommendationFactory = defaultRecommendationFactory;
      this.wizVsService = wizVsService;
   }

   public AutoBindingResponse autoBinding(AutoBindingRequest request, Principal user)
      throws Exception
   {
      return autoBindingInternal(request, user, false);
   }

   private AutoBindingResponse autoBindingInternal(AutoBindingRequest request, Principal user,
                                                   boolean skipExecution)
      throws Exception
   {
      List<SimpleFieldInfo> fieldConfigs = request.getFieldConfigs() != null
         ? request.getFieldConfigs() : Collections.emptyList();
      String worksheetId = request.getWorksheetId();

      // Phase 1: resolve or create the recommendation RVS.
      String autoBindingRuntimeId = request.getAutoBindingRuntimeId();
      boolean createdAutoBindingRvs = false;

      if(Tool.isEmptyString(autoBindingRuntimeId)) {
         Viewsheet.WizInfo wizInfo = new Viewsheet.WizInfo(true, null, null);
         AssetEntry wsEntry = !Tool.isEmptyString(worksheetId)
            ? new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, worksheetId, null)
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
            AssetEntry wsEntry = new AssetEntry(
               AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, worksheetId, null);

            try {
               AbstractSheet sheet = engine.getSheet(wsEntry, user, true, AssetContent.ALL);

               if(sheet instanceof Worksheet ws) {
                  WSAssembly primary = ws.getPrimaryAssembly();

                  if(primary instanceof TableAssembly ta) {
                     tableName = primary.getName();
                     // Use private selection so visibility flags set by applyFieldVisibility
                     // (which writes to the private selection) are correctly reflected.
                     ColumnSelection cs = ta.getColumnSelection(false);

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

         // Convert all columns to AssetEntry[]; fieldConfigs only adds config overrides
         final String tableNameFinal = tableName;
         AssetEntry[] entries;

         if(!worksheetColumns.isEmpty()) {
            // Primary path: use all worksheet columns (fieldConfigs as overlay only)
            entries = worksheetColumns.stream()
               .map(col -> buildEntryFromColumn(
                  col, worksheetId, tableNameFinal, configMap.get(col.getAttribute())))
               .toArray(AssetEntry[]::new);
         }
         else {
            // Fallback: worksheet not available, use fieldConfigs directly
            entries = fieldConfigs.stream()
               .map(field -> buildEntryFromFieldInfo(field, worksheetId, tableNameFinal))
               .toArray(AssetEntry[]::new);
         }

         bindingHandler.updateTemporaryFields(rvs, entries, tempInfo);
         tempInfo.getTempChart().setSourceInfo(bindingHandler.getCurrentSource(entries, tableName));

         VSChartInfo tempChartInfo = tempInfo.getTempChart().getVSChartInfo();
         applyFieldConfigs(tempChartInfo, configMap);

         String vizType = request.getVisualizationType();
         List<ExplicitBinding> explicitBindings = request.getExplicitBindings();
         ChartPreference pref = buildChartPreference(explicitBindings);
         VSWizardData wizardData = new VSWizardData(entries, tempInfo, pref);
         VSRecommendationModel model = defaultRecommendationFactory.recommend(wizardData, rvs, user);
         tempInfo.setRecommendationModel(model);

         // Phase 2: compute recommendations and primary binding.
         List<VSObjectRecommendation> recommendations = Collections.emptyList();
         PrimaryBinding primaryBinding = null;

         if(model != null) {
            recommendations = model.getRecommendationList().stream()
               .filter(r -> !(r instanceof VSFilterRecommendation))
               .collect(Collectors.toList());
            List<ChartCombinationUtil.ScoredInfo> prefInfos = null;
            VSObjectRecommendation chartRec = model.findRecommendation(VSRecommendType.CHART, false);

            if(chartRec instanceof VSChartRecommendation vcr) {
               prefInfos = vcr.getPrefInfos();
            }

            String intentCategory = request.getIntentCategory();
            primaryBinding = buildPrimaryVisualization(model, entries, vizType, explicitBindings,
                                                       prefInfos, intentCategory);

            // Mirror the wizard path: call updatePrimaryAssembly on autoBindingRvs so it gets
            // the same full setup (calc field registration, format, legend, temp assembly cleanup).
            if(primaryBinding instanceof PrimaryBinding.ChartPrimaryBinding chartBinding &&
               chartRec instanceof VSChartRecommendation vcr)
            {
               primaryBinding = refreshChartBinding(chartBinding, vcr, rvs, user);
            }
         }

         // Phase 3: place primary into the output viewsheet.
         CreateViewsheetResult visualizationResult = null;

         if(primaryBinding != null && !Tool.isEmptyString(worksheetId)) {
            VisualizationConfig sourceConfig = new VisualizationConfig();
            VisualizationConfig.DataSource ds = new VisualizationConfig.DataSource();
            ds.setSource(worksheetId);
            sourceConfig.setData(ds);

            CreateVisualizationModel vsModel = new CreateVisualizationModel();
            vsModel.setConfig(sourceConfig);
            vsModel.setPrimaryBinding(primaryBinding);
            vsModel.setRuntimeId(request.getWizRuntimeId());
            vsModel.setViewsheetIdentifier(request.getViewsheetIdentifier());

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
         }

         // Phase 4: build response.
         RecommendedVisualization primary = primaryBindingToRecommendedVisualization(primaryBinding, worksheetId);
         AutoBindingResponse resp = new AutoBindingResponse();
         resp.setRecommendations(recommendations);
         resp.setPrimary(primary);
         resp.setAutoBindingRuntimeId(autoBindingRuntimeId);
         resp.setVisualizationResult(visualizationResult);

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
                                           SimpleFieldInfo fc)
   {
      String colName = col.getAttribute();
      String path = (wsPath != null && tableName != null)
         ? wsPath + "/" + tableName + "/" + colName : colName;

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.COLUMN, path, null);
      entry.setProperty("assembly", tableName != null ? tableName : "");
      entry.setProperty("attribute", colName);

      String dtype = col.getDataType() != null ? col.getDataType() : XSchema.STRING;
      entry.setProperty("dtype", dtype);

      String alias = col.getAlias();
      entry.setProperty("caption", alias != null ? alias : colName);

      // fieldConfig fieldType takes precedence over dtype inference (design doc §4.3)
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
      applyFieldConfigToEntry(entry, fc, isMeasure);
      return entry;
   }

   private AssetEntry buildEntryFromFieldInfo(SimpleFieldInfo field, String wsPath,
                                              String tableName)
   {
      String fieldName = field.getField();
      String path = (wsPath != null && tableName != null)
         ? wsPath + "/" + tableName + "/" + fieldName : fieldName;

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.COLUMN, path, null);
      entry.setProperty("assembly", tableName != null ? tableName : "");
      entry.setProperty("attribute", fieldName);

      String dtype = field.getType() != null ? field.getType() : XSchema.STRING;
      entry.setProperty("dtype", dtype);
      entry.setProperty("caption", field.getTitle() != null ? field.getTitle() : fieldName);

      boolean isMeasure = field instanceof MeasureFieldInfo;
      setRefType(entry, isMeasure);
      applyFieldConfigToEntry(entry, field, isMeasure);
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

   private void applyFieldConfigToEntry(AssetEntry entry, SimpleFieldInfo fc, boolean isMeasure) {
      if(fc == null) {
         return;
      }

      // propagate date level as "interval" property consumed by setDefaultDateLevel
      if(!isMeasure && fc instanceof DimensionFieldInfo dim && dim.getDateGroupLevel() != null) {
         int level = WizVsService.getDateGroupLevel(dim.getDateGroupLevel());
         entry.setProperty("interval", String.valueOf(level));
      }

      if(isMeasure && fc instanceof MeasureFieldInfo mea && mea.getAggregateFormula() != null) {
         entry.setProperty("formula", mea.getAggregateFormula());
      }
   }

   // ── Apply fieldConfigs to generated ChartRefs ────────────────────────────────

   private void applyFieldConfigs(VSChartInfo chartInfo, Map<String, SimpleFieldInfo> configMap) {
      for(ChartRef ref : chartInfo.getXFields()) {
         applyFieldConfig(ref, configMap);
      }

      for(ChartRef ref : chartInfo.getYFields()) {
         applyFieldConfig(ref, configMap);
      }

      for(ChartRef ref : chartInfo.getGroupFields()) {
         applyFieldConfig(ref, configMap);
      }

      applyAestheticFieldConfig(chartInfo.getColorField(), configMap);
      applyAestheticFieldConfig(chartInfo.getSizeField(), configMap);
      applyAestheticFieldConfig(chartInfo.getShapeField(), configMap);
      applyAestheticFieldConfig(chartInfo.getTextField(), configMap);
   }

   private void applyAestheticFieldConfig(AestheticRef aestheticRef,
                                          Map<String, SimpleFieldInfo> configMap)
   {
      if(aestheticRef != null && aestheticRef.getDataRef() instanceof ChartRef ref) {
         applyFieldConfig(ref, configMap);
      }
   }

   private void applyFieldConfig(ChartRef ref, Map<String, SimpleFieldInfo> configMap) {
      String fieldName = WizardRecommenderUtil.getChartRefFieldName(ref);
      SimpleFieldInfo fc = configMap.get(fieldName);

      if(fc == null) {
         return;
      }

      if(ref instanceof VSChartDimensionRef dim) {
         if(fc instanceof DimensionFieldInfo dimFc && dimFc.getRanking() != null) {
            Ranking r = dimFc.getRanking();
            dim.setRankingOptionValue(String.valueOf(r.getOptionValue()));
            dim.setRankingNValue(String.valueOf(r.getRankingN()));
            dim.setRankingColValue(r.getRankingCol());
         }

         if(fc.getOrder() != null) {
            dim.setOrder(fc.getOrder());
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
         }
      }
   }

   // ── Chart preference helpers ──────────────────────────────────────────────────

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
                                                            String worksheetId)
   {
      TableBinding binding = new TableBinding();

      if(columns != null && entries != null) {
         Map<String, AssetEntry> entryByName = Arrays.stream(entries)
            .collect(Collectors.toMap(WizardRecommenderUtil::getFieldName, e -> e, (a, b) -> a));
         List<SimpleFieldInfo> details = new ArrayList<>();

         for(int i = 0; i < columns.getAttributeCount(); i++) {
            AssetEntry entry = entryByName.get(columns.getAttribute(i).getAttribute());

            if(entry != null) {
               details.add(entryToSimpleFieldInfo(entry));
            }
         }

         binding.setDetails(details);
      }

      RecommendedVisualization rec = new RecommendedVisualization();
      rec.setVisualizationType("table");
      rec.setConfig(wrapInConfig(binding, worksheetId));
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
         .map(r -> vsAggRefToFieldInfo((VSAggregateRef) r))
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
         .map(r -> vsDimRefToFieldInfo((VSDimensionRef) r))
         .collect(Collectors.toList());
   }

   private DimensionFieldInfo vsDimRefToFieldInfo(VSDimensionRef dim) {
      DimensionFieldInfo info = new DimensionFieldInfo();
      info.setField(dim.getGroupColumnValue());
      info.setType(dim.getDataType());
      int level = dim.getDateLevel();

      if(level != XConstants.NONE_DATE_GROUP) {
         info.setDateGroupLevel(WizVsService.getDateGroupLevelName(level));
      }

      return info;
   }

   private MeasureFieldInfo vsAggRefToFieldInfo(VSAggregateRef agg) {
      MeasureFieldInfo info = new MeasureFieldInfo();
      info.setField(agg.getColumnValue());
      info.setAggregateFormula(agg.getFormula() != null ? agg.getFormula().getFormulaName() : null);
      info.setCalculateInfo(CalculateInfo.createCalcInfo(agg.getCalculator()));
      return info;
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

   private VisualizationConfig convertToChartConfig(ChartInfo info, String worksheetId) {
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

      return wrapInConfig(binding, worksheetId);
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
         MeasureFieldInfo info = new MeasureFieldInfo();
         info.setField(agg.getColumnValue());
         info.setAggregateFormula(agg.getFormulaValue());
         info.setFullName(agg.getFullName());
         info.setCalculateInfo(CalculateInfo.createCalcInfo(agg.getCalculator()));
         return info;
      }

      if(ref instanceof VSDimensionRef dim) {
         DimensionFieldInfo info = new DimensionFieldInfo();
         info.setField(dim.getGroupColumnValue());
         info.setFullName(dim.getFullName());
         int level = dim.getDateLevel();

         if(level != XConstants.NONE_DATE_GROUP) {
            info.setDateGroupLevel(WizVsService.getDateGroupLevelName(level));
         }

         return info;
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

   // ── Primary visualization selection ───────────────────────────────────────────

   /**
    * Selects the primary visualization and returns native engine types so that
    * {@code WizVsService} can create the assembly without an intermediate
    * {@code VisualizationConfig} roundtrip.
    * <ul>
    *   <li>{@code vizType} non-null (chart) → pick best binding of that type from {@code prefInfos}.</li>
    *   <li>{@code vizType} non-null (non-chart) → find it in the recommendation model.</li>
    *   <li>No vizType, explicit bindings → infer viz type from binding role names
    *       (non-chart type wins only when its role-match count strictly exceeds chart roles),
    *       then find it in the recommendation model.</li>
    *   <li>No vizType fallback → if top recommendation is a chart, apply {@code intentCategory}
    *       filter on {@code prefInfos}; otherwise return the top recommendation.</li>
    * </ul>
    */
   private PrimaryBinding buildPrimaryVisualization(
      VSRecommendationModel model,
      AssetEntry[] entries,
      String vizType,
      List<ExplicitBinding> explicitBindings,
      List<ChartCombinationUtil.ScoredInfo> prefInfos,
      String intentCategory)
   {
      List<VSObjectRecommendation> recs = model.getRecommendationList();

      if(vizType != null) {
         if(isChartVizType(vizType)) {
            PrimaryBinding.ChartPrimaryBinding byType = buildBestChartVizByType(prefInfos, vizType);
            if(byType != null) {
               return byType;
            }
         }

         PrimaryBinding found = findPrimaryBindingByType(vizType, model, entries);

         if(found == null && !recs.isEmpty()) {
            LOG.debug("Requested vizType '{}' not found in recommendations; returning top recommendation", vizType);
         }

         return found != null ? found : (!recs.isEmpty() ? toPrimaryBinding(recs.get(0), entries) : null);
      }

      // Infer viz type from explicit binding roles when present.
      if(explicitBindings != null && !explicitBindings.isEmpty()) {
         String inferredType = inferNonChartVizType(explicitBindings);

         if(inferredType != null) {
            PrimaryBinding found = findPrimaryBindingByType(inferredType, model, entries);
            if(found != null) {
               return found;
            }
         }
      }

      // Fallback: delegate viz-type decision to the recommendation engine.
      // Iterate past VSFilterRecommendation (the only type toPrimaryBinding maps to null).
      for(VSObjectRecommendation rec : recs) {
         if(rec instanceof VSChartRecommendation) {
            PrimaryBinding.ChartPrimaryBinding chartPrimary = buildBestChartViz(prefInfos, intentCategory);
            return chartPrimary != null ? chartPrimary : toPrimaryBinding(rec, entries);
         }

         PrimaryBinding converted = toPrimaryBinding(rec, entries);

         if(converted != null) {
            return converted;
         }
      }

      return null;
   }

   /**
    * Package-private so {@code changeType()} can select a binding from a cached model.
    */
   PrimaryBinding findPrimaryBindingByType(String vizType, VSRecommendationModel model,
                                           AssetEntry[] entries)
   {
      for(VSObjectRecommendation rec : model.getRecommendationList()) {
         if(isChartVizType(vizType) && rec instanceof VSChartRecommendation cr) {
            List<ChartInfo> infos = cr.getChartInfos();

            if(infos != null) {
               for(ChartInfo ci : infos) {
                  if(chartTypeMatches(vizType, ci)) {
                     return new PrimaryBinding.ChartPrimaryBinding(ci);
                  }
               }
            }
         }
         else if("crosstab".equals(vizType) && rec instanceof VSCrosstabRecommendation cr) {
            if(cr.getCrosstabInfo() != null) {
               return new PrimaryBinding.CrosstabPrimaryBinding(cr.getCrosstabInfo());
            }
         }
         else if("table".equals(vizType) && rec instanceof VSTableRecommendation tr) {
            return new PrimaryBinding.TablePrimaryBinding(tr.getColumns(), entries);
         }
         else if("gauge".equals(vizType) && rec instanceof VSGaugeRecommendation gr) {
            if(gr.getDataRef() != null) {
               return new PrimaryBinding.GaugePrimaryBinding(gr.getDataRef());
            }
         }
         else if("text".equals(vizType) && rec instanceof VSTextRecommendation tr) {
            if(tr.getDataRef() != null) {
               return new PrimaryBinding.TextPrimaryBinding(tr.getDataRef());
            }
         }
      }

      return null;
   }

   private PrimaryBinding toPrimaryBinding(VSObjectRecommendation rec, AssetEntry[] entries) {
      return switch(rec) {
         case VSChartRecommendation cr -> {
            List<ChartInfo> infos = cr.getChartInfos();
            yield infos != null && !infos.isEmpty()
               ? new PrimaryBinding.ChartPrimaryBinding(infos.get(0))
               : null;
         }
         case VSCrosstabRecommendation cr when cr.getCrosstabInfo() != null ->
            new PrimaryBinding.CrosstabPrimaryBinding(cr.getCrosstabInfo());
         case VSTableRecommendation tr ->
            new PrimaryBinding.TablePrimaryBinding(tr.getColumns(), entries);
         case VSGaugeRecommendation gr when gr.getDataRef() != null ->
            new PrimaryBinding.GaugePrimaryBinding(gr.getDataRef());
         case VSTextRecommendation tr when tr.getDataRef() != null ->
            new PrimaryBinding.TextPrimaryBinding(tr.getDataRef());
         default -> null;
      };
   }

   private RecommendedVisualization primaryBindingToRecommendedVisualization(PrimaryBinding binding,
                                                                             String worksheetId)
   {
      if(binding == null) {
         return null;
      }

      return switch(binding) {
         case PrimaryBinding.ChartPrimaryBinding cb ->
            toRecommendedVisualization(cb.info(), worksheetId);
         case PrimaryBinding.CrosstabPrimaryBinding cb -> {
            RecommendedVisualization rec = new RecommendedVisualization();
            rec.setVisualizationType("crosstab");
            rec.setConfig(wrapInConfig(crosstabInfoToBinding(cb.info()), worksheetId));
            yield rec;
         }
         case PrimaryBinding.TablePrimaryBinding tb ->
            buildTableVisualization(tb.columns(), tb.entries(), worksheetId);
         case PrimaryBinding.GaugePrimaryBinding gb -> {
            OutputBinding outputBinding = new OutputBinding();
            outputBinding.setField(dataRefToMeasureFieldInfo(gb.dataRef()));
            RecommendedVisualization rec = new RecommendedVisualization();
            rec.setVisualizationType("gauge");
            rec.setConfig(wrapInConfig(outputBinding, worksheetId));
            yield rec;
         }
         case PrimaryBinding.TextPrimaryBinding tb -> {
            OutputBinding outputBinding = new OutputBinding();
            outputBinding.setField(dataRefToMeasureFieldInfo(tb.dataRef()));
            RecommendedVisualization rec = new RecommendedVisualization();
            rec.setVisualizationType("text");
            rec.setConfig(wrapInConfig(outputBinding, worksheetId));
            yield rec;
         }
      };
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

   private PrimaryBinding.ChartPrimaryBinding buildBestChartVizByType(
      List<ChartCombinationUtil.ScoredInfo> prefInfos, String explicitChartType)
   {
      if(prefInfos == null || prefInfos.isEmpty()) {
         return null;
      }

      return prefInfos.stream()
         .map(ChartCombinationUtil.ScoredInfo::getInfo)
         .filter(ci -> chartTypeMatches(explicitChartType, ci))
         .findFirst()
         .map(PrimaryBinding.ChartPrimaryBinding::new)
         .orElse(null);
   }

   private PrimaryBinding.ChartPrimaryBinding buildBestChartViz(
      List<ChartCombinationUtil.ScoredInfo> prefInfos, String intentCategory)
   {
      if(prefInfos == null || prefInfos.isEmpty()) {
         return null;
      }

      Set<Integer> categoryTypes = intentCategory != null
         ? INTENT_CATEGORY_CHART_TYPES.get(intentCategory) : null;

      ChartInfo highestScored = prefInfos.stream()
         .max(Comparator.comparingInt(ChartCombinationUtil.ScoredInfo::getScore))
         .map(ChartCombinationUtil.ScoredInfo::getInfo)
         .orElse(null);

      ChartInfo bestInfo;

      if(categoryTypes != null && !categoryTypes.isEmpty()) {
         bestInfo = prefInfos.stream()
            .filter(si -> categoryTypes.contains(si.getInfo().getChartType()))
            .max(Comparator.comparingInt(ChartCombinationUtil.ScoredInfo::getScore))
            .map(ChartCombinationUtil.ScoredInfo::getInfo)
            .orElse(highestScored);
      }
      else {
         bestInfo = highestScored;
      }

      return bestInfo != null ? new PrimaryBinding.ChartPrimaryBinding(bestInfo) : null;
   }

   private RecommendedVisualization toRecommendedVisualization(ChartInfo info, String worksheetId) {
      String vizType = getChartTypeString(info.getChartType());

      if(vizType == null) {
         return null;
      }

      RecommendedVisualization rec = new RecommendedVisualization();
      rec.setVisualizationType(vizType);
      rec.setConfig(convertToChartConfig(info, worksheetId));
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

   private VisualizationConfig wrapInConfig(BindingInfo binding, String worksheetId) {
      VisualizationConfig config = new VisualizationConfig();
      config.setBindingInfo(binding);

      if(!Tool.isEmptyString(worksheetId)) {
         VisualizationConfig.DataSource ds = new VisualizationConfig.DataSource();
         ds.setSource(worksheetId);
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
    * Otherwise the matching {@link PrimaryBinding} is selected and placed in the
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
         return result;
      }

      // 3. Select the primary binding for the requested type.
      PrimaryBinding primaryBinding = findPrimaryBindingByType(visualizationType, model, null);

      if(primaryBinding == null) {
         List<VSObjectRecommendation> recs = model.getRecommendationList().stream()
            .filter(r -> !(r instanceof VSFilterRecommendation))
            .collect(Collectors.toList());

         if(!recs.isEmpty()) {
            primaryBinding = toPrimaryBinding(recs.get(0), null);
         }
      }

      if(primaryBinding == null || Tool.isEmptyString(worksheetId) || Tool.isEmptyString(wizRuntimeId)) {
         LOG.warn("changeType skipped: primaryBinding={}, worksheetId='{}', wizRuntimeId='{}'",
                  primaryBinding, worksheetId, wizRuntimeId);
         return new CreateViewsheetResult();
      }

      // Mirror the wizard path: update autoBindingRvs so its state stays consistent
      // (calc fields, format, legend, cleanup of previous temp assembly).
      if(primaryBinding instanceof PrimaryBinding.ChartPrimaryBinding chartBinding &&
         capturedAutoBindingRvs != null)
      {
         VSObjectRecommendation chartRec = model.findRecommendation(VSRecommendType.CHART, false);

         if(chartRec instanceof VSChartRecommendation vcr) {
            primaryBinding = refreshChartBinding(chartBinding, vcr, capturedAutoBindingRvs, user);
         }
      }

      // 4. Place the new primary in wizRuntimeId without executing the sandbox.
      VisualizationConfig sourceConfig = new VisualizationConfig();
      VisualizationConfig.DataSource ds = new VisualizationConfig.DataSource();
      ds.setSource(worksheetId);
      sourceConfig.setData(ds);

      CreateVisualizationModel vsModel = new CreateVisualizationModel();
      vsModel.setConfig(sourceConfig);
      vsModel.setPrimaryBinding(primaryBinding);
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

      return result;
   }

   /**
    * Calls updatePrimaryAssembly on {@code rvs} to mirror the full wizard setup path
    * (calc field registration, format, legend, temp assembly cleanup), then extracts the
    * updated ChartInfo from the resulting temp assembly. Returns the refreshed binding, or
    * the original binding if the temp assembly is not a chart.
    */
   private PrimaryBinding refreshChartBinding(PrimaryBinding.ChartPrimaryBinding chartBinding,
                                               VSChartRecommendation vcr,
                                               RuntimeViewsheet rvs,
                                               Principal user) throws Exception
   {
      syncChartSelectedIndex(vcr, chartBinding.info());

      CommandDispatcher.withDummyDispatcher(user, dispatcher -> {
         bindingHandler.updatePrimaryAssembly(vcr, rvs, false, "", dispatcher, false);
         return null;
      });

      VSAssembly autoTempAsm = WizardRecommenderUtil.getTempAssembly(rvs.getViewsheet());

      if(autoTempAsm instanceof ChartVSAssembly autoChart) {
         return new PrimaryBinding.ChartPrimaryBinding(
            ((ChartVSAssemblyInfo) autoChart.getInfo()).getVSChartInfo());
      }

      return chartBinding;
   }

   /**
    * Sets selectedIndex on {@code vcr} so that {@code addChartVSAssembly} fetches
    * {@code selectedInfo}. Checks chartInfos first (plain index), then prefInfos
    * (chartInfos.size() + prefIdx) to cover both selection paths.
    */
   private static void syncChartSelectedIndex(VSChartRecommendation vcr, ChartInfo selectedInfo) {
      List<ChartInfo> chartInfos = vcr.getChartInfos();
      int chartInfosSize = chartInfos != null ? chartInfos.size() : 0;

      if(chartInfos != null) {
         for(int i = 0; i < chartInfosSize; i++) {
            if(chartInfos.get(i) == selectedInfo) {
               vcr.setSelectedIndex(i);
               return;
            }
         }
      }

      List<ChartCombinationUtil.ScoredInfo> prefInfos = vcr.getPrefInfos();

      if(prefInfos != null) {
         for(int i = 0; i < prefInfos.size(); i++) {
            if(prefInfos.get(i).getInfo() == selectedInfo) {
               vcr.setSelectedIndex(chartInfosSize + i);
               return;
            }
         }
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(WizAutoBindingService.class);

   private final ViewsheetService viewsheetService;
   private final AssetRepository engine;
   private final VSWizardTemporaryInfoService temporaryInfoService;
   private final VSWizardBindingHandler bindingHandler;
   private final VSDefaultRecommendationFactory defaultRecommendationFactory;
   private final WizVsService wizVsService;
}
