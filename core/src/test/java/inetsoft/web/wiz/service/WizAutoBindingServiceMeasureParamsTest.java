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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.web.wiz.model.MeasureFieldInfo;
import inetsoft.web.wiz.model.SimpleFieldInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * Regression tests for {@link WizAutoBindingService#applyFieldConfig} and
 * {@link WizAutoBindingService#applyAggregateFormulasTo}.
 *
 * <p>The bug (#76241 / #76239): both methods applied {@code aggregateFormula} from an incoming
 * {@link MeasureFieldInfo} but never read {@code secondaryField} or {@code nOrP} — so a two-column
 * formula (WeightedAverage, ...) or an N/P formula (PthPercentile, ...) always kept the
 * {@code VSAggregateRef} constructor default (empty secondary column, N=1) regardless of what the
 * autoBinding request sent. Fixed by applying both fields wherever {@code aggregateFormula} is
 * applied.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizAutoBindingServiceMeasureParamsTest {
   private static MeasureFieldInfo measureFc(String aggregateFormula, String secondaryField, Integer nOrP) {
      MeasureFieldInfo fc = new MeasureFieldInfo();
      fc.setAggregateFormula(aggregateFormula);
      fc.setSecondaryField(secondaryField);
      fc.setNOrP(nOrP);
      return fc;
   }

   // ── applyFieldConfig (chart path) ──────────────────────────────────────────

   @Test
   void secondaryFieldIsAppliedToChartAggregateRef() {
      VSChartAggregateRef agg = mock(VSChartAggregateRef.class);
      when(agg.getColumnValue()).thenReturn("PAID");
      Map<String, SimpleFieldInfo> configMap =
         Map.of("PAID", measureFc("WeightedAverage", "QUANTITY", null));

      WizAutoBindingService.applyFieldConfig(agg, configMap, 0);

      verify(agg).setSecondaryColumnValue("QUANTITY");
   }

   /**
    * Coverage proof that the fix is formula-agnostic, not incidentally correct for
    * {@code WeightedAverage} alone: {@code AggregateFormula.isTwoColumns()} has 7 overrides
    * ({@code Correlation}, {@code Covariance}, {@code WeightedAvg}, {@code SumWT}, {@code Sum2},
    * {@code First}, {@code Last} — 6 of them user-facing, {@code Sum2} internal-only), and the
    * write site never branches on which one is in play.
    */
   @Test
   void secondaryFieldIsAppliedForAnyTwoColumnFormula() {
      VSChartAggregateRef agg = mock(VSChartAggregateRef.class);
      when(agg.getColumnValue()).thenReturn("PAID");
      Map<String, SimpleFieldInfo> configMap =
         Map.of("PAID", measureFc("SumWT", "QUANTITY", null));

      WizAutoBindingService.applyFieldConfig(agg, configMap, 0);

      verify(agg).setSecondaryColumnValue("QUANTITY");
   }

   @Test
   void nOrPIsAppliedToChartAggregateRef() {
      VSChartAggregateRef agg = mock(VSChartAggregateRef.class);
      when(agg.getColumnValue()).thenReturn("ORDER_VALUE");
      Map<String, SimpleFieldInfo> configMap =
         Map.of("ORDER_VALUE", measureFc("PthPercentile", null, 90));

      WizAutoBindingService.applyFieldConfig(agg, configMap, 0);

      verify(agg).setN(90);
   }

   @Test
   void absentSecondaryFieldAndNOrPAreNotApplied() {
      VSChartAggregateRef agg = mock(VSChartAggregateRef.class);
      when(agg.getColumnValue()).thenReturn("PAID");
      Map<String, SimpleFieldInfo> configMap = Map.of("PAID", measureFc("Sum", null, null));

      WizAutoBindingService.applyFieldConfig(agg, configMap, 0);

      verify(agg, never()).setSecondaryColumnValue(any());
      verify(agg, never()).setN(anyInt());
   }

   // ── applyAggregateFormulasTo (crosstab path) ───────────────────────────────

   @Test
   void secondaryFieldIsAppliedToCrosstabAggregateRef() {
      VSAggregateRef agg = mock(VSAggregateRef.class);
      when(agg.getColumnValue()).thenReturn("PAID");
      Map<String, SimpleFieldInfo> configMap =
         Map.of("PAID", measureFc("WeightedAverage", "QUANTITY", null));

      WizAutoBindingService.applyAggregateFormulasTo(new DataRef[] { agg }, configMap);

      verify(agg).setSecondaryColumnValue("QUANTITY");
   }

   @Test
   void nOrPIsAppliedToCrosstabAggregateRef() {
      VSAggregateRef agg = mock(VSAggregateRef.class);
      when(agg.getColumnValue()).thenReturn("ORDER_VALUE");
      Map<String, SimpleFieldInfo> configMap =
         Map.of("ORDER_VALUE", measureFc("PthPercentile", null, 90));

      WizAutoBindingService.applyAggregateFormulasTo(new DataRef[] { agg }, configMap);

      verify(agg).setN(90);
   }
}
