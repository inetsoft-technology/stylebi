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
import inetsoft.uql.XCondition;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import inetsoft.web.wiz.model.DimensionFieldInfo;
import inetsoft.web.wiz.model.MeasureFieldInfo;
import inetsoft.web.wiz.model.Ranking;
import inetsoft.web.wiz.model.SimpleFieldInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
         new WizAutoBindingService(null, null, null, null, null, null, null);

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
         service = new WizAutoBindingService(null, null, tempInfoService, null, null, null, null);
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
         service = new WizAutoBindingService(null, null, tempInfoService, null, null, null, null);
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
}
