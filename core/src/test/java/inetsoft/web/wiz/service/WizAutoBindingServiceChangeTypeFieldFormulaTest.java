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
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
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
}
