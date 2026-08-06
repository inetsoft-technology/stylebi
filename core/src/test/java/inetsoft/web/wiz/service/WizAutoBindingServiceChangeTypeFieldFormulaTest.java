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

import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.wiz.model.MeasureFieldInfo;
import inetsoft.web.wiz.model.SimpleFieldInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
}
