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

package inetsoft.web.wiz;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.ExpiredSheetException;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.AestheticRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WizUtil {
   public static String decodeId(String id) {
      String decodedId;

      if(id == null || id.isEmpty()) {
         decodedId = null;
      }
      else {
         try {
            decodedId = new String(Base64.getDecoder().decode(id), StandardCharsets.UTF_8);
         }
         catch(IllegalArgumentException e) {
            decodedId = null;
         }
      }

      return decodedId;
   }

   /**
    * Resolve the runtime viewsheet for a wiz modify operation, transparently restoring it when the
    * runtime has been reaped (TTL expiry / server restart).
    *
    * The runtime viewsheet is transient, but the viewsheet asset that {@code viewsheetIdentifier}
    * points to is durable (every wiz create/modify rewrites it via persistViewsheet), so a reaped
    * runtime can be reopened from the identifier into a fresh runtime carrying the same state. Callers
    * MUST read {@link RuntimeViewsheet#getID()} off the returned value to pick up the (possibly new)
    * runtimeId and echo it back to the client, so subsequent edits target the live runtime instead of
    * the reaped one.
    *
    * @param viewsheetService    the runtime registry (resolve + reopen).
    * @param runtimeId           the runtime id the client believes is active (may be reaped).
    * @param viewsheetIdentifier the durable asset identifier to restore from; may be null/empty.
    * @param user                the requesting principal.
    * @return the live RuntimeViewsheet (the existing one, or a freshly reopened one).
    * @throws ExpiredSheetException if the runtime is gone AND no identifier is available to restore from.
    */
   public static RuntimeViewsheet getViewsheetOrRestore(ViewsheetService viewsheetService,
                                                        String runtimeId, String viewsheetIdentifier,
                                                        Principal user)
      throws Exception
   {
      try {
         return viewsheetService.getViewsheet(runtimeId, user);
      }
      catch(ExpiredSheetException ex) {
         if(Tool.isEmptyString(viewsheetIdentifier)) {
            // Nothing durable to restore from (e.g. the asset was explicitly removed) — surface expiry.
            throw ex;
         }

         AssetEntry entry = AssetEntry.createAssetEntry(viewsheetIdentifier);

         if(entry == null) {
            throw ex;
         }

         String restoredId = viewsheetService.openViewsheet(entry, user, false);
         LOG.debug("Restored reaped runtime [{}] from identifier [{}] as [{}]",
                   runtimeId, viewsheetIdentifier, restoredId);
         return viewsheetService.getViewsheet(restoredId, user);
      }
   }

   /**
    * Applies max mode state to the primary assembly of a viewsheet without refreshing.
    * The caller is responsible for triggering a viewsheet refresh afterward.
    *
    * @param vs      the viewsheet.
    * @param maxSize the max mode dimensions.
    */
   public static void prepareMaxMode(Viewsheet vs, Dimension maxSize) {
      if(vs == null || vs.getWizInfo() == null || !vs.getWizInfo().isWizVisualization() ||
         maxSize == null || maxSize.width <= 0 || maxSize.height <= 0)
      {
         return;
      }

      for(Assembly assembly : vs.getAssemblies()) {
         if(!(assembly instanceof VSAssembly vsAssembly)) {
            continue;
         }

         VSAssemblyInfo info = vsAssembly.getVSAssemblyInfo();

         if(info instanceof ChartVSAssemblyInfo chartInfo) {
            chartInfo.setMaxSize(maxSize);
            vs.setMaxMode(true);
            setMaxModeZIndex(vs, info, maxSize);
            return;
         }
         else if(info instanceof TableDataVSAssemblyInfo tableInfo) {
            tableInfo.setMaxSize(maxSize);
            vs.setMaxMode(true);
            setMaxModeZIndex(vs, info, maxSize);
            return;
         }
      }
   }

   private static void setMaxModeZIndex(Viewsheet vs, VSAssemblyInfo info, Dimension maxSize) {
      if(maxSize == null) {
         return;
      }

      Assembly[] assemblies = vs.getAssemblies(true, true);

      if(assemblies == null || assemblies.length == 0) {
         return;
      }

      VSAssembly top = (VSAssembly) assemblies[assemblies.length - 1];
      int zIndex = top.getVSAssemblyInfo().getZIndex() + 1;

      if(info instanceof ChartVSAssemblyInfo chartInfo) {
         chartInfo.setMaxModeZIndex(zIndex);
      }
      else if(info instanceof TableDataVSAssemblyInfo tableInfo) {
         tableInfo.setMaxModeZIndex(zIndex);
      }
   }

   // ── Binding-field settings: the wizard temp chart as durable state ───────────────────────────
   //
   // The temp chart in an autoBinding RVS's VSTemporaryInfo is what every recommendation is generated
   // from: ChartCombinationUtil reads temp.getXFields()/getYFields() and ChartTypeFilter.getAllRefs(true)
   // puts a CLONE of each temp ref into every candidate chart info. So a setting recorded on the temp
   // chart is inherited by every candidate of every chart type, while one applied only to the rendered
   // assembly is discarded by the next rebuild-from-recommendation. These helpers move settings in both
   // directions: onto the temp chart when an edit lands, and back off it when a cached recommendation
   // (changeType's fast path) produced a pre-edit snapshot.

   /** The temp chart of an autoBinding RVS, or null when the RVS never went through autoBinding. */
   public static ChartVSAssembly getTempChart(RuntimeViewsheet autoBindingRvs) {
      if(autoBindingRvs == null) {
         return null;
      }

      VSTemporaryInfo tempInfo = autoBindingRvs.getVSTemporaryInfo();
      return tempInfo == null ? null : tempInfo.getTempChart();
   }

   /**
    * Every ref carrying binding-field settings on this assembly, flattened: a chart's x/y plus the
    * dimensions/measures on its aesthetic slots (a dimension moved onto color/size for pie or treemap
    * keeps its ranking), or a crosstab's design row/col headers plus aggregates.
    */
   public static DataRef[] bindingFieldRefs(VSAssembly assembly) {
      List<DataRef> refs = new ArrayList<>();

      if(assembly instanceof ChartVSAssembly chartAsm && chartAsm.getVSChartInfo() != null) {
         VSChartInfo info = chartAsm.getVSChartInfo();
         addAll(refs, info.getXFields());
         addAll(refs, info.getYFields());

         for(AestheticRef aref : new AestheticRef[] {
            info.getColorField(), info.getShapeField(), info.getSizeField(), info.getTextField() })
         {
            if(aref != null && aref.getDataRef() != null) {
               refs.add(aref.getDataRef());
            }
         }
      }
      else if(assembly instanceof CrosstabVSAssembly crosstabAsm &&
              crosstabAsm.getVSCrosstabInfo() != null)
      {
         VSCrosstabInfo info = crosstabAsm.getVSCrosstabInfo();
         addAll(refs, info.getDesignRowHeaders());
         addAll(refs, info.getDesignColHeaders());
         addAll(refs, info.getDesignAggregates());
      }

      return refs.toArray(new DataRef[0]);
   }

   /**
    * Copies binding-field settings from {@code fromRefs} onto the {@code toRefs} that name the same
    * field. Refs with no counterpart on the other side are left alone.
    */
   public static void copySettingsByFieldName(DataRef[] fromRefs, DataRef[] toRefs) {
      if(fromRefs == null || toRefs == null) {
         return;
      }

      Map<String, DataRef> index = new HashMap<>();

      for(DataRef ref : fromRefs) {
         String name = WizardRecommenderUtil.getChartRefFieldName(ref);

         if(name != null && !name.isEmpty()) {
            index.putIfAbsent(name, ref);
         }
      }

      if(index.isEmpty()) {
         return;
      }

      for(DataRef to : toRefs) {
         String name = WizardRecommenderUtil.getChartRefFieldName(to);
         DataRef from = name == null ? null : index.get(name);

         if(from != null && from != to) {
            copyBindingFieldSettings(from, to);
         }
      }
   }

   /**
    * Copies the binding-field settings of one already-paired ref onto another: a dimension's top-/bottom-N
    * ranking, value/manual sort and date grouping; a measure's aggregate formula and calculator.
    *
    * <p>Property-by-property rather than replacing {@code to} with {@code from.clone()}, because the two
    * are frequently DIFFERENT subtypes — a map candidate's x is a {@code VSChartGeoRef}, and the
    * recommender converts dimensions/measures per target type — and swapping the object in would drop
    * that subtype along with the binding depending on it. Mirrors StyleBI's own cross-subtype carry-over,
    * the {@code VSChartGeoRef(VSChartDimensionRef)} constructor, whose property list this tracks.
    *
    * <p>Only settings the source actually carries are copied. A default-valued source must not overwrite
    * a value the recommender chose for the target type — that would trade one silent loss for another.
    */
   public static void copyBindingFieldSettings(DataRef from, DataRef to) {
      if(from instanceof VSDimensionRef fromDim && to instanceof VSDimensionRef toDim) {
         copyDimensionSettings(fromDim, toDim);
      }
      else if(from instanceof VSAggregateRef fromAgg && to instanceof VSAggregateRef toAgg) {
         copyAggregateSettings(fromAgg, toAgg);
      }
   }

   private static void copyDimensionSettings(VSDimensionRef from, VSDimensionRef to) {
      boolean ranked = isActiveRanking(from.getRankingOptionValue());

      if(ranked) {
         to.setRankingOptionValue(from.getRankingOptionValue());
         to.setRankingNValue(from.getRankingNValue());
         to.setRankingColValue(from.getRankingColValue());
         to.setGroupOthersValue(from.getGroupOthersValue());
      }

      String sortByCol = from.getSortByColValue();
      boolean hasSortByCol = sortByCol != null && !sortByCol.isEmpty();

      if(hasSortByCol) {
         to.setSortByColValue(sortByCol);
      }

      List manualOrder = from.getManualOrderList();
      boolean hasManualOrder = manualOrder != null && !manualOrder.isEmpty();

      if(hasManualOrder) {
         to.setManualOrderList(new ArrayList<>(manualOrder));
      }

      // `order` is what gives a ranking or a value-/manual-sort its meaning (17 value-asc, 18 value-desc,
      // MANUAL). Copy it only when one of those is present — a bare default order would overwrite the
      // sort the recommender chose for the target type.
      if(ranked || hasSortByCol || hasManualOrder) {
         to.setOrder(from.getOrder());
      }

      String dateLevel = from.getDateLevelValue();

      if(dateLevel != null && !dateLevel.isEmpty()) {
         to.setDateLevelValue(dateLevel);
         to.setTimeSeries(from.isTimeSeries());
      }
   }

   /** True when the ranking option actually selects a top-/bottom-N (not NONE, not unset). */
   private static boolean isActiveRanking(String optionValue) {
      if(optionValue == null || optionValue.isEmpty()) {
         return false;
      }

      try {
         int option = Integer.parseInt(optionValue);
         return option == XCondition.TOP_N || option == XCondition.BOTTOM_N;
      }
      catch(NumberFormatException e) {
         // A dynamic (expression-valued) ranking option — not resolvable here, so carry it over rather
         // than dropping a setting the user did express.
         return true;
      }
   }

   private static void copyAggregateSettings(VSAggregateRef from, VSAggregateRef to) {
      String formula = from.getFormulaValue();

      if(formula != null && !formula.isEmpty()) {
         to.setFormulaValue(formula);
      }

      if(from.getCalculator() != null) {
         to.setCalculator(from.getCalculator());
      }
   }

   private static void addAll(List<DataRef> refs, DataRef[] arr) {
      if(arr != null) {
         for(DataRef ref : arr) {
            if(ref != null) {
               refs.add(ref);
            }
         }
      }
   }

   public static final String ANNOTATION_RAW_DATA_MAX_ROW = "annotation.rawdata.maxrow";

   private static final Logger LOG = LoggerFactory.getLogger(WizUtil.class);
}
