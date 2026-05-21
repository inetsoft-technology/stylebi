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
import inetsoft.util.Tool;
import inetsoft.web.vswizard.handler.VSWizardBindingHandler;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import inetsoft.web.vswizard.recommender.chart.ChartCombinationUtil;
import inetsoft.web.vswizard.recommender.chart.ChartPreference;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
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
                                 VSWizardBindingHandler bindingHandler)
   {
      this.viewsheetService = viewsheetService;
      this.engine = engine;
      this.temporaryInfoService = temporaryInfoService;
      this.bindingHandler = bindingHandler;
   }

   public AutoBindingResponse autoBinding(AutoBindingRequest request, Principal user)
      throws Exception
   {
      List<SimpleFieldInfo> fieldConfigs = request.getFieldConfigs() != null
         ? request.getFieldConfigs() : Collections.emptyList();
      String worksheetId = request.getWorksheetId();

      Viewsheet.WizInfo wizInfo = new Viewsheet.WizInfo(true, null, null);
      String runtimeId = viewsheetService.openTemporaryViewsheet(null, null, user, wizInfo);

      try {
         temporaryInfoService.initTemporary(runtimeId, user, new Point(0, 0));
         RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, user);
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
                     ColumnSelection cs = ta.getColumnSelection();

                     for(int i = 0; i < cs.getAttributeCount(); i++) {
                        DataRef ref = cs.getAttribute(i);

                        if(ref instanceof ColumnRef colRef) {
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

         VSChartInfo tempChartInfo = tempInfo.getTempChart().getVSChartInfo();
         applyFieldConfigs(tempChartInfo, configMap);

         String vizType = request.getVisualizationType();
         boolean vizTypeIsExplicit = request.isVisualizationTypeIsExplicit();
         List<ExplicitBinding> explicitBindings = request.getExplicitBindings();
         ChartPreference pref = buildChartPreference(explicitBindings);

         // Single call: produces both the recommendations list (default-scored) and the
         // preference-ranked infos for primary selection.
         ChartCombinationUtil.ChartInfosResult chartResult =
            buildChartInfos(entries, tempChartInfo, pref);

         List<RecommendedVisualization> recommendations =
            buildRecommendations(entries, worksheetId, chartResult);

         List<ChartCombinationUtil.ScoredInfo> prefInfos =
            chartResult != null ? chartResult.getPrefInfos() : null;

         String intentCategory = request.getIntentCategory();
         RecommendedVisualization primary = buildPrimaryVisualization(
            recommendations, worksheetId, vizType, vizTypeIsExplicit,
            explicitBindings, prefInfos, intentCategory);

         AutoBindingResponse resp = new AutoBindingResponse();
         resp.setRecommendations(recommendations);
         resp.setPrimary(primary);
         return resp;
      }
      finally {
         try {
            temporaryInfoService.destroyTemporary(runtimeId, user);
         }
         catch(Exception e) {
            LOG.warn("Failed to destroy temp viewsheet info [{}]: {}", runtimeId, e.getMessage());
         }

         try {
            viewsheetService.closeViewsheet(runtimeId, user);
         }
         catch(Exception e) {
            LOG.warn("Failed to close temp viewsheet [{}]: {}", runtimeId, e.getMessage());
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
         if(fc instanceof MeasureFieldInfo meaFc && meaFc.getAggregateFormula() != null) {
            agg.setFormulaValue(meaFc.getAggregateFormula());
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

   // ── Build full recommendations list ───────────────────────────────────────────

   private ChartCombinationUtil.ChartInfosResult buildChartInfos(
      AssetEntry[] entries, VSChartInfo tempChartInfo, ChartPreference pref)
   {
      long dimCount = Arrays.stream(entries).filter(e -> !isMeasureEntry(e)).count();
      long measCount = entries.length - dimCount;

      if(dimCount == 0 || measCount == 0 || entries.length > 10) {
         return null;
      }

      try {
         return ChartCombinationUtil.getChartInfosWithScores(
            entries, tempChartInfo, new ColumnSelection(), true, pref);
      }
      catch(Exception e) {
         LOG.warn("ChartCombinationUtil.getChartInfosWithScores failed: {}", e.getMessage());
         return null;
      }
   }

   private List<RecommendedVisualization> buildRecommendations(
      AssetEntry[] entries, String worksheetId,
      ChartCombinationUtil.ChartInfosResult chartResult)
   {
      List<RecommendedVisualization> recs = new ArrayList<>();
      long dimCount = Arrays.stream(entries).filter(e -> !isMeasureEntry(e)).count();
      long measCount = entries.length - dimCount;

      if(chartResult != null) {
         for(ChartInfo ci : chartResult.getInfos()) {
            String vizType = getChartTypeString(ci.getChartType());

            if(vizType != null) {
               RecommendedVisualization rec = new RecommendedVisualization();
               rec.setVisualizationType(vizType);
               rec.setConfig(convertToChartConfig(ci, worksheetId));
               recs.add(rec);
            }
         }
      }

      if(dimCount > 0 && measCount > 0) {
         recs.add(buildCrosstabRecommendation(entries, worksheetId));
      }

      recs.add(buildTableRecommendation(entries, worksheetId));

      // Gauge recommendations apply for a single measure with no dimensions.
      if(measCount == 1 && dimCount == 0) {
         recs.add(buildOutputRecommendation(entries, worksheetId));
      }

      return recs;
   }

   private boolean isMeasureEntry(AssetEntry entry) {
      return String.valueOf(AssetEntry.MEASURES).equals(entry.getProperty(AssetEntry.CUBE_COL_TYPE));
   }

   private RecommendedVisualization buildTableRecommendation(AssetEntry[] entries,
                                                               String worksheetId)
   {
      TableBinding binding = new TableBinding();
      binding.setDetails(Arrays.stream(entries)
         .map(this::entryToSimpleFieldInfo)
         .collect(Collectors.toList()));

      RecommendedVisualization rec = new RecommendedVisualization();
      rec.setVisualizationType("table");
      rec.setConfig(wrapInConfig(binding, worksheetId));
      return rec;
   }

   private RecommendedVisualization buildCrosstabRecommendation(AssetEntry[] entries,
                                                                  String worksheetId)
   {
      CrosstabBinding binding = new CrosstabBinding();
      List<DimensionFieldInfo> rows = new ArrayList<>();
      List<MeasureFieldInfo> aggregates = new ArrayList<>();

      for(AssetEntry entry : entries) {
         if(isMeasureEntry(entry)) {
            aggregates.add(entryToMeasureFieldInfo(entry));
         }
         else {
            rows.add(entryToDimensionFieldInfo(entry));
         }
      }

      if(!rows.isEmpty()) {
         binding.setRows(rows);
      }

      if(!aggregates.isEmpty()) {
         binding.setAggregates(aggregates);
      }

      RecommendedVisualization rec = new RecommendedVisualization();
      rec.setVisualizationType("crosstab");
      rec.setConfig(wrapInConfig(binding, worksheetId));
      return rec;
   }

   private RecommendedVisualization buildOutputRecommendation(AssetEntry[] entries,
                                                               String worksheetId)
   {
      OutputBinding binding = new OutputBinding();

      for(AssetEntry entry : entries) {
         if(isMeasureEntry(entry)) {
            binding.setField(entryToMeasureFieldInfo(entry));
            break;
         }
      }

      RecommendedVisualization rec = new RecommendedVisualization();
      rec.setVisualizationType("gauge");
      rec.setConfig(wrapInConfig(binding, worksheetId));
      return rec;
   }

   // ── ChartInfo → VisualizationConfig (design doc §4.4) ───────────────────────

   private VisualizationConfig convertToChartConfig(ChartInfo info, String worksheetId) {
      ChartBinding binding = new ChartBinding();

      List<SimpleFieldInfo> x = chartRefsToFieldInfos(info.getXFields());
      if(!x.isEmpty()) binding.setX(x);

      List<SimpleFieldInfo> y = chartRefsToFieldInfos(info.getYFields());
      if(!y.isEmpty()) binding.setY(y);

      List<SimpleFieldInfo> group = chartRefsToFieldInfos(info.getGroupFields());
      if(!group.isEmpty()) binding.setGroup(group);

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
      return isMeasureEntry(entry)
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
    * Selects the primary visualization.
    * <ul>
    *   <li>Explicit chart type → pick best binding of that type from {@code prefInfos}.</li>
    *   <li>Explicit non-chart type → find it in {@code recommendations}.</li>
    *   <li>Auto-select with explicit bindings → infer viz type from binding role names
    *       (non-chart type wins only when its role-match count strictly exceeds chart roles),
    *       then find it in {@code recommendations}.</li>
    *   <li>Auto-select fallback → if top recommendation is a chart use {@code prefInfos},
    *       otherwise return the top recommendation.</li>
    * </ul>
    */
   private RecommendedVisualization buildPrimaryVisualization(
      List<RecommendedVisualization> recommendations,
      String worksheetId,
      String vizType, boolean vizTypeIsExplicit,
      List<ExplicitBinding> explicitBindings,
      List<ChartCombinationUtil.ScoredInfo> prefInfos,
      String intentCategory)
   {
      if(vizTypeIsExplicit && vizType != null) {
         if(isChartVizType(vizType)) {
            return buildBestChartVizByType(prefInfos, vizType, worksheetId);
         }

         RecommendedVisualization found = findInRecommendations(vizType, recommendations);
         return found != null ? found : (!recommendations.isEmpty() ? recommendations.get(0) : null);
      }

      // Infer viz type from explicit binding roles when present.
      if(explicitBindings != null && !explicitBindings.isEmpty()) {
         String inferredType = inferNonChartVizType(explicitBindings);

         if(inferredType != null) {
            RecommendedVisualization found = findInRecommendations(inferredType, recommendations);
            if(found != null) return found;
         }
      }

      // Fallback: delegate viz-type decision to the recommendation engine.
      if(!recommendations.isEmpty()) {
         RecommendedVisualization topRec = recommendations.get(0);

         if(isChartVizType(topRec.getVisualizationType())) {
            RecommendedVisualization chartPrimary =
               buildBestChartViz(prefInfos, intentCategory, worksheetId);
            return chartPrimary != null ? chartPrimary : topRec;
         }

         return topRec;
      }

      return null;
   }

   private RecommendedVisualization findInRecommendations(String vizType,
                                                            List<RecommendedVisualization> recommendations)
   {
      return recommendations.stream()
         .filter(r -> vizType.equals(r.getVisualizationType()))
         .findFirst()
         .orElse(null);
   }

   private String inferNonChartVizType(List<ExplicitBinding> bindings) {
      int chartScore = countRoleMatches(bindings, CHART_ROLES);

      if(countRoleMatches(bindings, CROSSTAB_ROLES) > chartScore) return "crosstab";
      if(countRoleMatches(bindings, TABLE_ROLES) > chartScore) return "table";
      if(countRoleMatches(bindings, GAUGE_ROLES) > chartScore) return "gauge";

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
   private static final Set<String> GAUGE_ROLES = Set.of("field");

   private RecommendedVisualization buildBestChartVizByType(
      List<ChartCombinationUtil.ScoredInfo> prefInfos,
      String explicitChartType, String worksheetId)
   {
      if(prefInfos == null || prefInfos.isEmpty()) {
         return null;
      }

      ChartInfo bestInfo = prefInfos.stream()
         .map(ChartCombinationUtil.ScoredInfo::getInfo)
         .filter(ci -> explicitChartType.equals(getChartTypeString(ci.getChartType())))
         .findFirst()
         .orElse(prefInfos.get(0).getInfo());

      return toRecommendedVisualization(bestInfo, worksheetId);
   }

   private RecommendedVisualization buildBestChartViz(
      List<ChartCombinationUtil.ScoredInfo> prefInfos,
      String intentCategory, String worksheetId)
   {
      if(prefInfos == null || prefInfos.isEmpty()) {
         return null;
      }

      ChartInfo bestInfo;
      Set<Integer> categoryTypes = intentCategory != null
         ? INTENT_CATEGORY_CHART_TYPES.get(intentCategory) : null;

      if(categoryTypes != null && !categoryTypes.isEmpty()) {
         bestInfo = prefInfos.stream()
            .filter(si -> categoryTypes.contains(si.getInfo().getChartType()))
            .map(ChartCombinationUtil.ScoredInfo::getInfo)
            .findFirst()
            .orElse(prefInfos.get(0).getInfo());
      }
      else {
         bestInfo = prefInfos.get(0).getInfo();
      }

      return toRecommendedVisualization(bestInfo, worksheetId);
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


   private static final Set<String> NON_CHART_VIZ_TYPES = Set.of("table", "crosstab", "gauge");

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
         GraphTypes.CHART_MAP_CONTOUR,
         GraphTypes.CHART_POLYGON
      ));

      INTENT_CATEGORY_CHART_TYPES = Collections.unmodifiableMap(m);
   }

   private boolean isChartVizType(String vizType) {
      return vizType != null && !NON_CHART_VIZ_TYPES.contains(vizType);
   }

   // ── Chart type string mapping (inverse of WizVsService.getChartType) ─────────

   private static String getChartTypeString(int chartType) {
      return switch(chartType) {
         case GraphTypes.CHART_BAR, GraphTypes.CHART_BAR_STACK -> "bar";
         case GraphTypes.CHART_3D_BAR, GraphTypes.CHART_3D_BAR_STACK -> "3d_bar";
         case GraphTypes.CHART_AREA, GraphTypes.CHART_AREA_STACK -> "area";
         case GraphTypes.CHART_POINT, GraphTypes.CHART_POINT_STACK -> "point";
         case GraphTypes.CHART_STEP_AREA, GraphTypes.CHART_STEP_AREA_STACK -> "step_area";
         case GraphTypes.CHART_INTERVAL -> "interval";
         case GraphTypes.CHART_LINE, GraphTypes.CHART_LINE_STACK -> "line";
         case GraphTypes.CHART_STEP, GraphTypes.CHART_STEP_STACK -> "step_line";
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

   private static final Logger LOG = LoggerFactory.getLogger(WizAutoBindingService.class);

   private final ViewsheetService viewsheetService;
   private final AssetRepository engine;
   private final VSWizardTemporaryInfoService temporaryInfoService;
   private final VSWizardBindingHandler bindingHandler;
}
