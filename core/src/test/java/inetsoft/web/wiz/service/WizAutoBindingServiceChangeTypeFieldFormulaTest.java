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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.graph.calc.PercentCalc;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XCondition;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.viewsheet.graph.AestheticRef;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.vswizard.handler.SyncChartHandler;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import inetsoft.web.wiz.BindingFieldSettings;
import inetsoft.web.wiz.model.CreateViewsheetResult;
import inetsoft.web.wiz.model.DimensionFieldInfo;
import inetsoft.web.wiz.model.MeasureFieldInfo;
import inetsoft.web.wiz.model.Ranking;
import inetsoft.web.wiz.model.SimpleFieldInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WizAutoBindingService#applyCrosstabAggregateFormulas} — the crosstab
 * counterpart of {@code applyFieldConfigs} added so {@code changeType()} can re-apply a caller's
 * already-resolved measure formula (e.g. Count) onto a freshly-selected crosstab recommendation.
 *
 * <p>Regression for map→crosstab silently turning Count(CUSTOMER_COUNT) into Sum(CUSTOMER_COUNT):
 * a complex-chart LLM node (e.g. the map binding path) picks its own formula directly against
 * {@code /viewsheet/create}, entirely bypassing the wizard recommender — so the recommendation
 * model changeType() consults for the destination type has no idea a measure was already resolved
 * to something other than its own generic per-type default (Sum for any numeric column).
 */
@Tag("core")
class WizAutoBindingServiceChangeTypeFieldFormulaTest {
   private static VSAggregateRef agg(String column) {
      VSAggregateRef ref = mock(VSAggregateRef.class);
      when(ref.getColumnValue()).thenReturn(column);
      return ref;
   }

   private static MeasureFieldInfo measure(String field, String formula) {
      MeasureFieldInfo fc = new MeasureFieldInfo();
      fc.setField(field);
      fc.setAggregateFormula(formula);
      return fc;
   }

   private static Map<String, SimpleFieldInfo> configMap(SimpleFieldInfo... configs) {
      Map<String, SimpleFieldInfo> map = new HashMap<>();

      for(SimpleFieldInfo fc : configs) {
         map.put(fc.getField(), fc);
      }

      return map;
   }

   @Test
   void overridesFormulaForMatchingField() {
      VSAggregateRef ref = agg("CUSTOMER_COUNT");
      VSCrosstabInfo info = mock(VSCrosstabInfo.class);
      when(info.getDesignAggregates()).thenReturn(new DataRef[] { ref });

      WizAutoBindingService.applyCrosstabAggregateFormulas(
         info, configMap(measure("CUSTOMER_COUNT", "Count")));

      verify(ref).setFormulaValue("Count");
   }

   @Test
   void leavesUnmatchedFieldsUntouched() {
      VSAggregateRef ref = agg("ORDER_TOTAL");
      VSCrosstabInfo info = mock(VSCrosstabInfo.class);
      when(info.getDesignAggregates()).thenReturn(new DataRef[] { ref });

      WizAutoBindingService.applyCrosstabAggregateFormulas(
         info, configMap(measure("CUSTOMER_COUNT", "Count")));

      verify(ref, never()).setFormulaValue(anyString());
   }

   @Test
   void emptyConfigMapIsSafe() {
      VSAggregateRef ref = agg("CUSTOMER_COUNT");
      VSCrosstabInfo info = mock(VSCrosstabInfo.class);
      when(info.getDesignAggregates()).thenReturn(new DataRef[] { ref });

      WizAutoBindingService.applyCrosstabAggregateFormulas(info, new HashMap<>());

      verify(ref, never()).setFormulaValue(anyString());
   }

   @Test
   void nullAggregatesIsSafe() {
      VSCrosstabInfo info = mock(VSCrosstabInfo.class);
      when(info.getDesignAggregates()).thenReturn(null);

      assertDoesNotThrow(() -> WizAutoBindingService.applyCrosstabAggregateFormulas(
         info, configMap(measure("CUSTOMER_COUNT", "Count"))));
   }

   // Regression for PR #4509 review: query execution reads getRuntimeAggregates(), not
   // getDesignAggregates() — the design-only override could be silently lost if runtime aggregates
   // were already populated (e.g. by an earlier refreshVisualizationBinding execution) and nothing
   // forces a fresh VSCrosstabInfo.update() before the response's data-fetch step reads them.

   @Test
   void alsoOverridesAlreadyPopulatedRuntimeAggregates() {
      VSAggregateRef designRef = agg("CUSTOMER_COUNT");
      VSAggregateRef runtimeRef = agg("CUSTOMER_COUNT");
      VSCrosstabInfo info = mock(VSCrosstabInfo.class);
      when(info.getDesignAggregates()).thenReturn(new DataRef[] { designRef });
      when(info.getRuntimeAggregates()).thenReturn(new DataRef[] { runtimeRef });

      WizAutoBindingService.applyCrosstabAggregateFormulas(
         info, configMap(measure("CUSTOMER_COUNT", "Count")));

      verify(designRef).setFormulaValue("Count");
      verify(runtimeRef).setFormulaValue("Count");
   }

   @Test
   void emptyRuntimeAggregatesArrayIsLeftAlone() {
      VSAggregateRef designRef = agg("CUSTOMER_COUNT");
      VSCrosstabInfo info = mock(VSCrosstabInfo.class);
      when(info.getDesignAggregates()).thenReturn(new DataRef[] { designRef });
      when(info.getRuntimeAggregates()).thenReturn(new DataRef[0]);

      assertDoesNotThrow(() -> WizAutoBindingService.applyCrosstabAggregateFormulas(
         info, configMap(measure("CUSTOMER_COUNT", "Count"))));

      verify(designRef).setFormulaValue("Count");
   }

   @Test
   void nullRuntimeAggregatesIsSafe() {
      VSAggregateRef designRef = agg("CUSTOMER_COUNT");
      VSCrosstabInfo info = mock(VSCrosstabInfo.class);
      when(info.getDesignAggregates()).thenReturn(new DataRef[] { designRef });
      when(info.getRuntimeAggregates()).thenReturn(null);

      assertDoesNotThrow(() -> WizAutoBindingService.applyCrosstabAggregateFormulas(
         info, configMap(measure("CUSTOMER_COUNT", "Count"))));

      verify(designRef).setFormulaValue("Count");
   }

   /**
    * applyResolvedFormulaOverrides(VSAssembly, Map) dispatch — the piece that decides WHICH
    * applicator (chart vs. crosstab vs. neither) a changeType() result actually reaches. Only the
    * dispatch is exercised here (via a service instance with every unused collaborator left null,
    * mirroring WizAutoBindingServiceSetChartFormatTest's pattern) — not the full changeType() call.
    */
   @Tag("core")
   static class ApplyResolvedFormulaOverridesDispatchTest {
      private final WizAutoBindingService service =
         new WizAutoBindingService(null, null, null, null, null, null, null, null);

      @Test
      void dispatchesChartAssemblyToApplyFieldConfigs() {
         VSChartAggregateRef yRef = mock(VSChartAggregateRef.class);
         when(yRef.getColumnValue()).thenReturn("CUSTOMER_COUNT");

         VSChartInfo chartInfo = mock(VSChartInfo.class);
         when(chartInfo.getXFields()).thenReturn(new inetsoft.uql.viewsheet.graph.ChartRef[0]);
         when(chartInfo.getYFields()).thenReturn(
            new inetsoft.uql.viewsheet.graph.ChartRef[] { yRef });

         ChartVSAssembly chartAsm = mock(ChartVSAssembly.class);
         when(chartAsm.getVSChartInfo()).thenReturn(chartInfo);

         service.applyResolvedFormulaOverrides(
            (VSAssembly) chartAsm, configMap(measure("CUSTOMER_COUNT", "Count")));

         verify(yRef).setFormulaValue("Count");
      }

      @Test
      void dispatchesCrosstabAssemblyToApplyCrosstabAggregateFormulas() {
         VSAggregateRef ref = agg("CUSTOMER_COUNT");
         VSCrosstabInfo crosstabInfo = mock(VSCrosstabInfo.class);
         when(crosstabInfo.getDesignAggregates()).thenReturn(new DataRef[] { ref });

         CrosstabVSAssembly crosstabAsm = mock(CrosstabVSAssembly.class);
         when(crosstabAsm.getVSCrosstabInfo()).thenReturn(crosstabInfo);

         service.applyResolvedFormulaOverrides(
            (VSAssembly) crosstabAsm, configMap(measure("CUSTOMER_COUNT", "Count")));

         verify(ref).setFormulaValue("Count");
      }

      @Test
      void neitherChartNorCrosstabIsANoOp() {
         TableVSAssembly tableAsm = mock(TableVSAssembly.class);

         assertDoesNotThrow(() -> service.applyResolvedFormulaOverrides(
            (VSAssembly) tableAsm, configMap(measure("CUSTOMER_COUNT", "Count"))));
      }

      @Test
      void nullAssemblyIsANoOp() {
         assertDoesNotThrow(() -> service.applyResolvedFormulaOverrides(
            null, configMap(measure("CUSTOMER_COUNT", "Count"))));
      }
   }

   /**
    * syncHighlightOnTypeChange(VSAssembly, VSAssembly) — the fix for the reported bug: a highlight
    * applied via /viewsheet/highlight disappeared after switching chart type through the GUI's
    * "change chart style" action (candidate-type buttons), because changeType()'s fast path never
    * ran SyncInfoHandler#syncConfigs (that only fires on the /viewsheet/autoBinding path — see
    * WizVsService's syncSource block). Only the chart/chart dispatch is exercised here — the actual
    * ref-matching and HighlightGroup copy is SyncChartHandler#syncHighlight's own concern, covered by
    * its own tests.
    */
   @Tag("core")
   static class SyncHighlightOnTypeChangeTest {
      private SyncChartHandler syncChartHandler;
      private WizAutoBindingService service;

      @BeforeEach
      void setUp() {
         syncChartHandler = mock(SyncChartHandler.class);
         service = new WizAutoBindingService(null, null, null, null, null, null, null, syncChartHandler);
      }

      @Test
      void copiesHighlightWhenBothSidesAreCharts() {
         ChartVSAssembly fromChart = mock(ChartVSAssembly.class);
         ChartVSAssembly toChart = mock(ChartVSAssembly.class);

         service.syncHighlightOnTypeChange(fromChart, toChart);

         verify(syncChartHandler).syncHighlight(fromChart, toChart);
      }

      @Test
      void sourceNotAChartIsANoOp() {
         TableVSAssembly fromTable = mock(TableVSAssembly.class);
         ChartVSAssembly toChart = mock(ChartVSAssembly.class);

         service.syncHighlightOnTypeChange(fromTable, toChart);

         verify(syncChartHandler, never()).syncHighlight(any(), any());
      }

      @Test
      void targetNotAChartIsANoOp() {
         ChartVSAssembly fromChart = mock(ChartVSAssembly.class);
         TableVSAssembly toTable = mock(TableVSAssembly.class);

         service.syncHighlightOnTypeChange(fromChart, toTable);

         verify(syncChartHandler, never()).syncHighlight(any(), any());
      }

      @Test
      void nullAssembliesAreANoOp() {
         assertDoesNotThrow(() -> service.syncHighlightOnTypeChange(null, null));
         verify(syncChartHandler, never()).syncHighlight(any(), any());
      }
   }

   /**
    * hasHighlightToCarry(VSAssembly) — the upfront filter that lets changeType() skip the RVS lookup
    * and sandbox graph-clear for the common case of switching a chart that was never highlighted.
    * Must mirror SyncChartHandler#syncHighlight's own copy logic closely enough to never
    * under-detect: a false negative here means a real highlight silently stops carrying across a
    * type switch again, exactly the class of bug this fix exists for. Over-detecting (running the
    * lookup when nothing actually matches on the target) is harmless by comparison.
    */
   @Tag("core")
   static class HasHighlightToCarryTest {
      @Test
      void nonChartSourceIsFalse() {
         TableVSAssembly table = mock(TableVSAssembly.class);

         assertFalse(WizAutoBindingService.hasHighlightToCarry(table));
      }

      @Test
      void nullSourceIsFalse() {
         assertFalse(WizAutoBindingService.hasHighlightToCarry(null));
      }

      @Test
      void chartWithNoChartInfoIsFalse() {
         ChartVSAssembly chart = mock(ChartVSAssembly.class);
         when(chart.getVSChartInfo()).thenReturn(null);

         assertFalse(WizAutoBindingService.hasHighlightToCarry(chart));
      }

      @Test
      void chartWithNoHighlightAnywhereIsFalse() {
         VSChartAggregateRef agg = mock(VSChartAggregateRef.class);
         when(agg.getHighlightGroup()).thenReturn(null);
         when(agg.getTextHighlightGroup()).thenReturn(null);

         VSChartInfo info = mock(VSChartInfo.class);
         when(info.getHighlightGroup()).thenReturn(null);
         when(info.getBindingRefs(false)).thenReturn(new ChartRef[] { agg });

         ChartVSAssembly chart = mock(ChartVSAssembly.class);
         when(chart.getVSChartInfo()).thenReturn(info);

         assertFalse(WizAutoBindingService.hasHighlightToCarry(chart));
      }

      @Test
      void aPerRefHighlightGroupIsTrue() {
         VSChartAggregateRef agg = mock(VSChartAggregateRef.class);
         when(agg.getHighlightGroup()).thenReturn(mock(HighlightGroup.class));

         VSChartInfo info = mock(VSChartInfo.class);
         when(info.getBindingRefs(false)).thenReturn(new ChartRef[] { agg });

         ChartVSAssembly chart = mock(ChartVSAssembly.class);
         when(chart.getVSChartInfo()).thenReturn(info);

         assertTrue(WizAutoBindingService.hasHighlightToCarry(chart));
      }

      @Test
      void aPerRefTextHighlightGroupIsTrue() {
         VSChartDimensionRef dim = mock(VSChartDimensionRef.class);
         when(dim.getHighlightGroup()).thenReturn(null);
         when(dim.getTextHighlightGroup()).thenReturn(mock(HighlightGroup.class));

         VSChartInfo info = mock(VSChartInfo.class);
         when(info.getBindingRefs(false)).thenReturn(new ChartRef[] { dim });

         ChartVSAssembly chart = mock(ChartVSAssembly.class);
         when(chart.getVSChartInfo()).thenReturn(info);

         assertTrue(WizAutoBindingService.hasHighlightToCarry(chart));
      }

      /** The merged-chart case: chart-level group, not any single ref's. */
      @Test
      void aChartLevelHighlightGroupIsTrue() {
         VSChartInfo info = mock(VSChartInfo.class);
         when(info.getHighlightGroup()).thenReturn(mock(HighlightGroup.class));
         when(info.getBindingRefs(false)).thenReturn(new ChartRef[0]);

         ChartVSAssembly chart = mock(ChartVSAssembly.class);
         when(chart.getVSChartInfo()).thenReturn(info);

         assertTrue(WizAutoBindingService.hasHighlightToCarry(chart));
      }
   }

   /**
    * applyFieldConfigsToTempChart(RuntimeViewsheet, Map) — records the caller's field settings on the
    * autoBinding RVS's TEMP CHART x/y refs, not just on the rendered assembly.
    *
    * <p>The temp chart is the wizard's durable binding state: every recommendation candidate's x/y refs
    * are CLONES of it (ChartTypeFilter.getAllRefs -> addXYField), so a top-N recorded here survives into
    * every later type recommendation. Applying it only to the rendered assembly — which is all
    * applyFieldConfigs(VSChartInfo, ..) did — meant changeType()'s next rebuild from the recommendation
    * model produced a chart with no ranking at all, silently dropping the user's "show top 3".
    */
   @Tag("core")
   static class ApplyFieldConfigsToTempChartTest {
      private VSWizardTemporaryInfoService tempInfoService;
      private WizAutoBindingService service;

      @BeforeEach
      void setUp() {
         tempInfoService = mock(VSWizardTemporaryInfoService.class);
         service = new WizAutoBindingService(null, null, tempInfoService, null, null, null, null, null);
      }

      private static DimensionFieldInfo topN(String field, int n, String rankingCol) {
         DimensionFieldInfo fc = new DimensionFieldInfo();
         fc.setField(field);
         Ranking ranking = new Ranking();
         ranking.setOptionValue(XCondition.TOP_N);
         ranking.setRankingN(n);
         ranking.setRankingCol(rankingCol);
         fc.setRanking(ranking);
         // An active ranking only takes effect paired with a value-based order (18 = value-desc).
         fc.setOrder(18);
         return fc;
      }

      /** Wires rvs -> tempInfo -> tempChart -> chartInfo with `xRef` on x and nothing on y. */
      private RuntimeViewsheet rvsWithTempChartX(ChartRef xRef) {
         VSChartInfo chartInfo = mock(VSChartInfo.class);
         when(chartInfo.getXFields()).thenReturn(new ChartRef[] { xRef });
         when(chartInfo.getYFields()).thenReturn(new ChartRef[0]);

         ChartVSAssembly tempChart = mock(ChartVSAssembly.class);
         when(tempChart.getVSChartInfo()).thenReturn(chartInfo);

         VSTemporaryInfo tempInfo = mock(VSTemporaryInfo.class);
         when(tempInfo.getTempChart()).thenReturn(tempChart);

         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(tempInfoService.getVSTemporaryInfo(rvs)).thenReturn(tempInfo);

         return rvs;
      }

      @Test
      void recordsRankingOnTheTempChartDimension() {
         VSChartDimensionRef xRef = mock(VSChartDimensionRef.class);
         when(xRef.getGroupColumnValue()).thenReturn("MONTH");

         service.applyFieldConfigsToTempChart(
            rvsWithTempChartX(xRef), configMap(topN("MONTH", 3, "Sum(amount)")));

         verify(xRef).setRankingOptionValue(String.valueOf(XCondition.TOP_N));
         verify(xRef).setRankingNValue("3");
         verify(xRef).setRankingColValue("Sum(amount)");
         verify(xRef).setOrder(18);
      }

      @Test
      void leavesUnmatchedTempChartFieldsUntouched() {
         VSChartDimensionRef xRef = mock(VSChartDimensionRef.class);
         when(xRef.getGroupColumnValue()).thenReturn("REGION");

         service.applyFieldConfigsToTempChart(
            rvsWithTempChartX(xRef), configMap(topN("MONTH", 3, "Sum(amount)")));

         verify(xRef, never()).setRankingOptionValue(anyString());
         verify(xRef, never()).setRankingNValue(anyString());
      }

      @Test
      void missingTempChartIsANoOp() {
         VSTemporaryInfo tempInfo = mock(VSTemporaryInfo.class);
         when(tempInfo.getTempChart()).thenReturn(null);
         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(tempInfoService.getVSTemporaryInfo(rvs)).thenReturn(tempInfo);

         assertDoesNotThrow(() -> service.applyFieldConfigsToTempChart(
            rvs, configMap(topN("MONTH", 3, "Sum(amount)"))));
      }

      @Test
      void missingTemporaryInfoIsANoOp() {
         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(tempInfoService.getVSTemporaryInfo(rvs)).thenReturn(null);

         assertDoesNotThrow(() -> service.applyFieldConfigsToTempChart(
            rvs, configMap(topN("MONTH", 3, "Sum(amount)"))));
      }

      @Test
      void nullRuntimeViewsheetIsANoOp() {
         assertDoesNotThrow(() -> service.applyFieldConfigsToTempChart(
            null, configMap(topN("MONTH", 3, "Sum(amount)"))));
      }

      /** An empty configMap must not even reach the temp chart — nothing to record. */
      @Test
      void emptyConfigMapNeverReadsTheTempChart() {
         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);

         service.applyFieldConfigsToTempChart(rvs, configMap());

         verify(tempInfoService, never()).getVSTemporaryInfo(any(RuntimeViewsheet.class));
      }
   }

   /**
    * applyTempChartFieldSettings(RuntimeViewsheet, VSAssembly) — the read side of the temp chart as
    * durable binding state: restores its x/y field settings onto an assembly that was just rebuilt
    * from a recommendation.
    *
    * <p>Needed because changeType()'s fast path reuses the CACHED recommendation model, whose candidate
    * chart infos were cloned from the temp chart at autoBinding time — i.e. before any later edit. A
    * fresh recommend inherits the settings through that clone; a cached one is a stale snapshot, so the
    * settings have to be re-applied here or the type switch renders without them.
    *
    * <p>Only settings the user actually expressed are copied. A default-valued temp ref must NOT be
    * copied over the candidate, or it would wipe the choices the recommender made for the target type.
    */
   @Tag("core")
   static class ApplyTempChartFieldSettingsTest {
      private VSWizardTemporaryInfoService tempInfoService;
      private WizAutoBindingService service;

      @BeforeEach
      void setUp() {
         tempInfoService = mock(VSWizardTemporaryInfoService.class);
         service = new WizAutoBindingService(null, null, tempInfoService, null, null, null, null, null);
      }

      private static VSChartDimensionRef rankedTempDim(String field) {
         VSChartDimensionRef dim = mock(VSChartDimensionRef.class);
         when(dim.getGroupColumnValue()).thenReturn(field);
         when(dim.getRankingOptionValue()).thenReturn(String.valueOf(XCondition.TOP_N));
         when(dim.getRankingNValue()).thenReturn("3");
         when(dim.getRankingColValue()).thenReturn("Sum(amount)");
         when(dim.getGroupOthersValue()).thenReturn("false");
         when(dim.getOrder()).thenReturn(18);
         return dim;
      }

      private static VSChartDimensionRef plainDim(String field) {
         VSChartDimensionRef dim = mock(VSChartDimensionRef.class);
         when(dim.getGroupColumnValue()).thenReturn(field);
         return dim;
      }

      /** Wires rvs -> tempInfo -> tempChart(x = tempX) and returns the rvs. */
      private RuntimeViewsheet rvsWithTempX(ChartRef tempX) {
         VSChartInfo tempChartInfo = mock(VSChartInfo.class);
         when(tempChartInfo.getXFields()).thenReturn(new ChartRef[] { tempX });
         when(tempChartInfo.getYFields()).thenReturn(new ChartRef[0]);

         ChartVSAssembly tempChart = mock(ChartVSAssembly.class);
         when(tempChart.getVSChartInfo()).thenReturn(tempChartInfo);

         VSTemporaryInfo tempInfo = mock(VSTemporaryInfo.class);
         when(tempInfo.getTempChart()).thenReturn(tempChart);

         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(tempInfoService.getVSTemporaryInfo(rvs)).thenReturn(tempInfo);

         return rvs;
      }

      private static ChartVSAssembly rebuiltChartWithX(ChartRef x) {
         VSChartInfo info = mock(VSChartInfo.class);
         when(info.getXFields()).thenReturn(new ChartRef[] { x });
         when(info.getYFields()).thenReturn(new ChartRef[0]);

         ChartVSAssembly asm = mock(ChartVSAssembly.class);
         when(asm.getVSChartInfo()).thenReturn(info);
         return asm;
      }

      @Test
      void restoresRankingOntoTheRebuiltChart() {
         VSChartDimensionRef target = plainDim("MONTH");

         service.applyTempChartFieldSettings(
            rvsWithTempX(rankedTempDim("MONTH")), rebuiltChartWithX(target));

         verify(target).setRankingOptionValue(String.valueOf(XCondition.TOP_N));
         verify(target).setRankingNValue("3");
         verify(target).setRankingColValue("Sum(amount)");
         verify(target).setGroupOthersValue("false");
         // Order is part of the ranking's meaning (18 = value-desc); without it top-N does nothing.
         verify(target).setOrder(18);
      }

      @Test
      void leavesARefWithNoTempCounterpartAlone() {
         VSChartDimensionRef target = plainDim("REGION");

         service.applyTempChartFieldSettings(
            rvsWithTempX(rankedTempDim("MONTH")), rebuiltChartWithX(target));

         verify(target, never()).setRankingOptionValue(anyString());
         verify(target, never()).setOrder(anyInt());
      }

      /**
       * The load-bearing guard: an unranked temp ref must not push its defaults onto the candidate.
       * The recommender picks the target type's own ordering, and clobbering it would trade one silent
       * data loss for another.
       */
      @Test
      void anUnrankedTempRefDoesNotClobberTheCandidate() {
         VSChartDimensionRef target = plainDim("MONTH");

         service.applyTempChartFieldSettings(
            rvsWithTempX(plainDim("MONTH")), rebuiltChartWithX(target));

         verify(target, never()).setRankingOptionValue(anyString());
         verify(target, never()).setRankingNValue(anyString());
         verify(target, never()).setOrder(anyInt());
      }

      @Test
      void restoresAnAggregateFormula() {
         VSChartAggregateRef tempY = mock(VSChartAggregateRef.class);
         when(tempY.getColumnValue()).thenReturn("amount");
         when(tempY.getFormulaValue()).thenReturn("Average");

         VSChartAggregateRef target = mock(VSChartAggregateRef.class);
         when(target.getColumnValue()).thenReturn("amount");

         service.applyTempChartFieldSettings(rvsWithTempX(tempY), rebuiltChartWithX(target));

         verify(target).setFormulaValue("Average");
      }

      @Test
      void missingTempChartIsANoOp() {
         VSTemporaryInfo tempInfo = mock(VSTemporaryInfo.class);
         when(tempInfo.getTempChart()).thenReturn(null);
         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(tempInfoService.getVSTemporaryInfo(rvs)).thenReturn(tempInfo);

         assertDoesNotThrow(() -> service.applyTempChartFieldSettings(
            rvs, rebuiltChartWithX(plainDim("MONTH"))));
      }

      @Test
      void nullArgumentsAreANoOp() {
         assertDoesNotThrow(() -> service.applyTempChartFieldSettings(null, null));
      }
   }

   /**
    * BindingFieldSettings — the ref-pairing primitive both directions run on.
    *
    * <p>From review on PR #4526. The service-level tests above drive one ref per side, which leaves the
    * primitive's own indexing, its record/restore asymmetry and the crosstab key untested — and three of
    * the four defects that review found lived exactly there.
    *
    * <p>Real refs, not mocks: what is under test IS the accessor each key is derived from, so stubbing
    * {@code getGroupColumnValue()} / {@code getColumnValue()} would assume away the crosstab defect. Only
    * the assembly/info containers are mocked, and only to hand back the ref arrays. Real refs pull in
    * GDefaults / AggregateFormula static init, hence the SreeHome context — same harness as
    * SyncChartHandlerNullTempInfoTest, which drives real assemblies for the same reason.
    */
   @ExtendWith(SpringExtension.class)
   @ContextConfiguration(classes = { BaseTestConfiguration.class },
                         initializers = ConfigurationContextInitializer.class)
   @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
   @SreeHome
   @Tag("core")
   static class BindingFieldSettingsTest {
      private static VSChartDimensionRef chartDim(String column) {
         VSChartDimensionRef dim = new VSChartDimensionRef();
         dim.setGroupColumnValue(column);
         return dim;
      }

      private static VSChartDimensionRef rankedChartDim(String column) {
         VSChartDimensionRef dim = chartDim(column);
         dim.setRankingOptionValue(String.valueOf(XCondition.TOP_N));
         dim.setRankingNValue("3");
         dim.setRankingColValue("DistinctCount(ORDER_ID)");
         dim.setOrder(18);
         return dim;
      }

      private static VSChartAggregateRef chartAgg(String column, String formula) {
         VSChartAggregateRef agg = new VSChartAggregateRef();
         agg.setColumnValue(column);

         if(formula != null) {
            agg.setFormulaValue(formula);
         }

         return agg;
      }

      /**
       * RECORD must clear, not merge. The source is the chart the user is looking at, so a ranking it no
       * longer carries is a removal — merging would leave the old value on the temp chart and the next
       * rebuild would push it back, reverting the user's own edit a turn later and self-reinforcingly.
       */
      @Test
      void recordClearsARankingTheSourceNoLongerCarries() {
         VSChartDimensionRef source = chartDim("MONTH");
         VSChartDimensionRef temp = rankedChartDim("MONTH");

         BindingFieldSettings.record(new DataRef[] { source }, new DataRef[] { temp });

         assertNull(temp.getRankingOptionValue());
         assertNull(temp.getRankingNValue());
      }

      /** RESTORE keeps the opposite rule: an unranked source must not wipe the recommender's choice. */
      @Test
      void restoreLeavesTheTargetAloneWhenTheSourceCarriesNothing() {
         VSChartDimensionRef source = chartDim("MONTH");
         VSChartDimensionRef candidate = rankedChartDim("MONTH");

         BindingFieldSettings.restore(new DataRef[] { source }, new DataRef[] { candidate });

         assertEquals(String.valueOf(XCondition.TOP_N), candidate.getRankingOptionValue());
         assertEquals("3", candidate.getRankingNValue());
      }

      @Test
      void restoreCarriesTheRankingOntoAMatchingColumn() {
         VSChartDimensionRef candidate = chartDim("MONTH");

         BindingFieldSettings.restore(
            new DataRef[] { rankedChartDim("MONTH") }, new DataRef[] { candidate });

         assertEquals(String.valueOf(XCondition.TOP_N), candidate.getRankingOptionValue());
         assertEquals("3", candidate.getRankingNValue());
         assertEquals(18, candidate.getOrder());
      }

      /**
       * The key is the bound column and cannot encode the formula — that is what is being carried — so two
       * aggregates of one column are indistinguishable. Carry nothing rather than let the first ref's
       * formula land on the second measure, which would silently turn Average(amount) into Sum(amount).
       */
      @Test
      void twoAggregatesOfOneColumnCarryNothing() {
         VSChartAggregateRef target = chartAgg("amount", "Max");

         BindingFieldSettings.restore(
            new DataRef[] { chartAgg("amount", "Sum"), chartAgg("amount", "Average") },
            new DataRef[] { target });

         assertEquals("Max", target.getFormulaValue());
      }

      /** Same rule on the target side: one date column bound at two levels must not both take one source. */
      @Test
      void oneColumnBoundTwiceOnTheTargetCarriesNothing() {
         VSChartDimensionRef year = chartDim("ORDER_DATE");
         VSChartDimensionRef quarter = chartDim("ORDER_DATE");

         BindingFieldSettings.restore(
            new DataRef[] { rankedChartDim("ORDER_DATE") }, new DataRef[] { year, quarter });

         assertNull(year.getRankingOptionValue());
         assertNull(quarter.getRankingOptionValue());
      }

      /**
       * A crosstab's design headers are plain VSDimensionRef, whose getAttribute() returns the underlying
       * column attribute rather than the group column VALUE the chart side keys on. Keying off the base
       * type's value accessor is what lets the two sides pair up at all.
       */
      @Test
      void aCrosstabHeaderPairsWithTheTempChartDimension() {
         VSDimensionRef header = new VSDimensionRef();
         header.setGroupColumnValue("MONTH");

         VSCrosstabInfo info = mock(VSCrosstabInfo.class);
         when(info.getDesignRowHeaders()).thenReturn(new DataRef[] { header });
         when(info.getDesignColHeaders()).thenReturn(new DataRef[0]);
         when(info.getDesignAggregates()).thenReturn(new DataRef[0]);

         CrosstabVSAssembly crosstab = mock(CrosstabVSAssembly.class);
         when(crosstab.getVSCrosstabInfo()).thenReturn(info);

         BindingFieldSettings.restore(
            new DataRef[] { rankedChartDim("MONTH") }, BindingFieldSettings.refsOf(crosstab));

         assertEquals(String.valueOf(XCondition.TOP_N), header.getRankingOptionValue());
         assertEquals("3", header.getRankingNValue());
      }

      /**
       * Calculator is mutable and VSAggregateRef.clone() treats it as owned state. Installing the same
       * instance on both refs would leave the long-lived temp chart sharing one object with a rendered
       * assembly, where a later mutation on either side silently changes the other.
       */
      @Test
      void theCalculatorIsClonedRatherThanShared() {
         PercentCalc calculator = new PercentCalc();
         calculator.setLevel(PercentCalc.SUB_TOTAL);

         VSChartAggregateRef source = chartAgg("amount", "Sum");
         source.setCalculator(calculator);
         VSChartAggregateRef target = chartAgg("amount", null);

         BindingFieldSettings.restore(new DataRef[] { source }, new DataRef[] { target });

         assertNotNull(target.getCalculator());
         assertNotSame(calculator, target.getCalculator());
      }

      /** snapshot() must detach, or a caller that records later would see post-mutation values. */
      @Test
      void snapshotDetachesTheRefsFromTheAssembly() {
         VSChartDimensionRef live = rankedChartDim("MONTH");

         VSChartInfo info = mock(VSChartInfo.class);
         when(info.getXFields()).thenReturn(new ChartRef[] { live });
         when(info.getYFields()).thenReturn(new ChartRef[0]);

         ChartVSAssembly chart = mock(ChartVSAssembly.class);
         when(chart.getVSChartInfo()).thenReturn(info);

         DataRef[] snapshot = BindingFieldSettings.snapshot(chart);
         live.setRankingOptionValue(String.valueOf(XCondition.NONE));

         assertNotSame(live, snapshot[0]);
         assertEquals(String.valueOf(XCondition.TOP_N),
                      ((VSChartDimensionRef) snapshot[0]).getRankingOptionValue());
      }

      /**
       * columnKeys is the "is the temp chart still about this chart" test, so it must span every slot a
       * column can sit in — a dimension moved onto colour for a pie is still the same bound column, and
       * reporting a difference there would force a needless rebuild on every type switch.
       */
      @Test
      void columnKeysSpansXYAndTheAesthetics() {
         VSChartInfo info = mock(VSChartInfo.class);
         when(info.getXFields()).thenReturn(new ChartRef[] { chartDim("MONTH") });
         when(info.getYFields()).thenReturn(new ChartRef[] { chartAgg("amount", "Sum") });

         AestheticRef color = mock(AestheticRef.class);
         when(color.getDataRef()).thenReturn(chartDim("REGION"));
         when(info.getColorField()).thenReturn(color);

         ChartVSAssembly chart = mock(ChartVSAssembly.class);
         when(chart.getVSChartInfo()).thenReturn(info);

         assertEquals(Set.of("MONTH", "amount", "REGION"), BindingFieldSettings.columnKeys(chart));
      }

      /** A crosstab keys off the same value accessors, so the two sides compare on equal terms. */
      @Test
      void columnKeysReadsACrosstabsDesignHeaders() {
         VSDimensionRef header = new VSDimensionRef();
         header.setGroupColumnValue("MONTH");
         VSAggregateRef aggregate = new VSAggregateRef();
         aggregate.setColumnValue("amount");

         VSCrosstabInfo info = mock(VSCrosstabInfo.class);
         when(info.getDesignRowHeaders()).thenReturn(new DataRef[] { header });
         when(info.getDesignColHeaders()).thenReturn(new DataRef[0]);
         when(info.getDesignAggregates()).thenReturn(new DataRef[] { aggregate });

         CrosstabVSAssembly crosstab = mock(CrosstabVSAssembly.class);
         when(crosstab.getVSCrosstabInfo()).thenReturn(info);

         assertEquals(Set.of("MONTH", "amount"), BindingFieldSettings.columnKeys(crosstab));
      }

      /**
       * Unlike the record/restore index, a column bound twice is NOT dropped here. The question is which
       * columns are bound, and dropping an ambiguous one would make a chart binding Sum(amount) and
       * Average(amount) compare equal to one binding neither.
       */
      @Test
      void columnKeysKeepsAColumnBoundTwice() {
         VSChartInfo info = mock(VSChartInfo.class);
         when(info.getXFields()).thenReturn(new ChartRef[0]);
         when(info.getYFields()).thenReturn(
            new ChartRef[] { chartAgg("amount", "Sum"), chartAgg("amount", "Average") });

         ChartVSAssembly chart = mock(ChartVSAssembly.class);
         when(chart.getVSChartInfo()).thenReturn(info);

         assertEquals(Set.of("amount"), BindingFieldSettings.columnKeys(chart));
      }
   }

   /**
    * tempChartDescribes(VSTemporaryInfo, VSAssembly) — whether the temp chart, and the recommendation
    * model generated from it, still describe the chart a changeType call targets.
    *
    * <p>A conversation reuses one recommendation runtime whose temp chart is re-seeded from whichever
    * chart autoBinding last built. Aiming a type change at an earlier history card would otherwise
    * rebuild that card from the LATEST chart's binding entirely, so this predicate is what routes such a
    * call into a rebuild — in both directions, since after that rebuild the temp chart describes the
    * historical card and a call on the latest chart is the stale one.
    */
   @Tag("core")
   static class TempChartDescribesTest {
      private WizAutoBindingService service;

      @BeforeEach
      void setUp() {
         service = new WizAutoBindingService(null, null, null, null, null, null, null, null);
      }

      private static ChartVSAssembly chartBinding(String... columns) {
         ChartRef[] x = new ChartRef[columns.length];

         for(int i = 0; i < columns.length; i++) {
            VSChartDimensionRef dim = mock(VSChartDimensionRef.class);
            when(dim.getGroupColumnValue()).thenReturn(columns[i]);
            x[i] = dim;
         }

         VSChartInfo info = mock(VSChartInfo.class);
         when(info.getXFields()).thenReturn(x);
         when(info.getYFields()).thenReturn(new ChartRef[0]);

         ChartVSAssembly chart = mock(ChartVSAssembly.class);
         when(chart.getVSChartInfo()).thenReturn(info);
         return chart;
      }

      private static VSTemporaryInfo tempInfo(String sourceName, String... columns) {
         // Built before the stubbing that consumes it: chartBinding() stubs mocks of its own, and
         // nesting that inside when(...) leaves Mockito with an unfinished stubbing.
         ChartVSAssembly tempChart = chartBinding(columns);
         VSTemporaryInfo info = mock(VSTemporaryInfo.class);
         when(info.getTempChart()).thenReturn(tempChart);
         when(info.getWizSourceAssemblyName()).thenReturn(sourceName);
         return info;
      }

      private static VSAssembly target(String name, String... columns) {
         ChartVSAssembly chart = chartBinding(columns);
         when(chart.getName()).thenReturn(name);
         return chart;
      }

      @Test
      void theChartItWasSeededFromMatches() {
         assertTrue(service.tempChartDescribes(
            tempInfo("Chart2", "MONTH", "amount"), target("Chart2", "MONTH", "amount")));
      }

      /** The historical-card case: same columns is not enough, the settings belong to another chart. */
      @Test
      void aDifferentChartWithTheSameColumnsDoesNotMatch() {
         assertFalse(service.tempChartDescribes(
            tempInfo("Chart2", "MONTH", "amount"), target("Chart1", "MONTH", "amount")));
      }

      /**
       * The name alone is not enough either: an explicit re-bind lands in a newly named assembly and the
       * record step carries the name across, but it cannot add a column to the temp chart — so a measure
       * added that way leaves the temp chart stale for a chart whose name it legitimately holds.
       */
      @Test
      void theRightChartWithAnAddedColumnDoesNotMatch() {
         assertFalse(service.tempChartDescribes(
            tempInfo("Chart2", "MONTH"), target("Chart2", "MONTH", "amount")));
      }

      /** Cleared marker — the record step's way of saying "stale, rebuild from the chart". */
      @Test
      void aClearedMarkerDoesNotMatch() {
         assertFalse(service.tempChartDescribes(
            tempInfo(null, "MONTH"), target("Chart2", "MONTH")));
      }

      /**
       * Unreadable state must degrade to the pre-existing fast path. Forcing a rebuild off state we
       * could not read would turn a missing temp chart into a full re-recommendation on every call.
       */
      @Test
      void unreadableStateReportsAMatch() {
         VSTemporaryInfo noTempChart = mock(VSTemporaryInfo.class);
         when(noTempChart.getTempChart()).thenReturn(null);

         assertTrue(service.tempChartDescribes(noTempChart, target("Chart2", "MONTH")));
         assertTrue(service.tempChartDescribes(null, target("Chart2", "MONTH")));
         assertTrue(service.tempChartDescribes(tempInfo("Chart2", "MONTH"), null));
      }
   }

   /**
    * deriveFieldConfigs / sourceTableName — how a rebuild learns what the target chart is bound to.
    */
   @Tag("core")
   static class DeriveFieldConfigsTest {
      private WizVsService wizVsService;
      private WizAutoBindingService service;

      @BeforeEach
      void setUp() {
         wizVsService = mock(WizVsService.class);
         service = new WizAutoBindingService(null, null, null, null, null, wizVsService, null, null);
      }

      @Test
      void flattensTheTargetsDimensionsAndMeasures() {
         DimensionFieldInfo month = new DimensionFieldInfo();
         month.setField("MONTH");
         MeasureFieldInfo amount = measure("amount", "Sum");

         ChartVSAssembly target = mock(ChartVSAssembly.class);
         when(wizVsService.collectFlatBinding(target)).thenReturn(
            new CreateViewsheetResult.FlatBinding(List.of(month), List.of(amount), Map.of()));

         assertEquals(List.of(month, amount), service.deriveFieldConfigs(target));
      }

      /**
       * Empty rather than null, and empty is what makes the caller stand down: with nothing to rebuild
       * FROM, a staleness verdict is unactionable and the fast path must stay.
       */
      @Test
      void anUnreadableTargetDerivesNothing() {
         ChartVSAssembly target = mock(ChartVSAssembly.class);
         when(wizVsService.collectFlatBinding(target)).thenReturn(null);

         assertEquals(List.of(), service.deriveFieldConfigs(target));
         assertEquals(List.of(), service.deriveFieldConfigs(null));
      }

      /**
       * The target's own table, not the worksheet's primary: a conversation's worksheet accumulates a
       * table per rebuild generation, and resolving the target's columns against the wrong one fails
       * every one of them as unknown.
       */
      @Test
      void readsTheTargetsOwnWorksheetTable() {
         ChartVSAssembly target = mock(ChartVSAssembly.class);
         when(target.getSourceInfo()).thenReturn(
            new SourceInfo(SourceInfo.ASSET, null, "Query1_2"));

         assertEquals("Query1_2", WizAutoBindingService.sourceTableName(target));
      }

      @Test
      void noSourceInfoLeavesTheTableUnnamed() {
         ChartVSAssembly target = mock(ChartVSAssembly.class);
         when(target.getSourceInfo()).thenReturn(null);

         assertNull(WizAutoBindingService.sourceTableName(target));
         assertNull(WizAutoBindingService.sourceTableName(null));
         assertNull(WizAutoBindingService.sourceTableName(mock(TableVSAssembly.class)));
      }

      private static ColumnRef wsColumn(String name) {
         ColumnRef col = mock(ColumnRef.class);
         when(col.getDisplayName()).thenReturn(name);
         return col;
      }

      private ChartVSAssembly targetBoundTo(String... fields) {
         List<DimensionFieldInfo> dimensions = new ArrayList<>();

         for(String field : fields) {
            DimensionFieldInfo dim = new DimensionFieldInfo();
            dim.setField(field);
            dimensions.add(dim);
         }

         ChartVSAssembly target = mock(ChartVSAssembly.class);
         when(target.getName()).thenReturn("Chart1");
         when(wizVsService.collectFlatBinding(target)).thenReturn(
            new CreateViewsheetResult.FlatBinding(dimensions, List.of(), Map.of()));
         return target;
      }

      /**
       * An INFERRED list is only used when the worksheet table actually has every column. `fieldConfigs`
       * is enforced strictly — selectBindColumns throws on a column the table does not have, which is
       * right for a caller that named its fields and wrong for a list inferred on its behalf.
       */
      @Test
      void derivesOnlyWhenEveryColumnIsInTheTable() {
         ChartVSAssembly target = targetBoundTo("MONTH", "REGION");

         assertEquals(2, service.derivedFieldConfigsWithin(
            target, List.of(wsColumn("MONTH"), wsColumn("REGION"), wsColumn("amount"))).size());
      }

      /**
       * All-or-nothing rather than dropping the missing ones: half a binding is not the chart the caller
       * meant either, so it falls back to the pre-existing behavior (every visible column, re-slotted).
       * Reachable when the chart's table was replaced by a later rebuild generation.
       */
      @Test
      void derivesNothingWhenAColumnIsMissing() {
         ChartVSAssembly target = targetBoundTo("MONTH", "REGION");

         assertEquals(List.of(),
                      service.derivedFieldConfigsWithin(target, List.of(wsColumn("MONTH"))));
         assertEquals(List.of(), service.derivedFieldConfigsWithin(target, List.of()));
      }
   }
}
