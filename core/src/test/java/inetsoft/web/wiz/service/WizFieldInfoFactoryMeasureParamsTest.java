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

import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.web.wiz.model.MeasureFieldInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the {@code secondaryField}/{@code nOrP} echo in
 * {@link WizFieldInfoFactory#createChartMeasureFieldInfo} and
 * {@link WizFieldInfoFactory#createCrosstabMeasureFieldInfo}.
 *
 * <p>The bug (#76241 / #76239): neither echo read {@code agg.getSecondaryColumnValue()} or
 * {@code agg.getN()} back onto the response {@link MeasureFieldInfo}, so even after the write-side
 * fix a caller reading the structured {@code secondaryField}/{@code nOrP} keys (rather than parsing
 * {@code fullName}) still saw {@code null} for both. {@code nOrP} is gated on
 * {@code AggregateFormula#hasN()} rather than echoed unconditionally, because
 * {@code VSAggregateRef#getN()} has no "unset" sentinel of its own (it returns 1 whenever N was
 * never set) and would otherwise fabricate {@code nOrP: 1} on every formula, including ones with no
 * N/P concept at all (e.g. Sum).
 */
@Tag("core")
class WizFieldInfoFactoryMeasureParamsTest {
   @Test
   void chartEchoReportsSecondaryFieldAndNOrP() {
      VSChartAggregateRef agg = mock(VSChartAggregateRef.class);
      when(agg.getColumnValue()).thenReturn("PAID");
      when(agg.getSecondaryColumnValue()).thenReturn("QUANTITY");

      AggregateFormula formula = mock(AggregateFormula.class);
      when(formula.hasN()).thenReturn(true);
      when(agg.getFormula()).thenReturn(formula);
      when(agg.getN()).thenReturn(90);

      MeasureFieldInfo info = WizFieldInfoFactory.createChartMeasureFieldInfo(agg);

      assertEquals("QUANTITY", info.getSecondaryField());
      assertEquals(90, info.getNOrP());
   }

   @Test
   void chartEchoDoesNotFabricateNOrPForAFormulaWithoutN() {
      VSChartAggregateRef agg = mock(VSChartAggregateRef.class);
      when(agg.getColumnValue()).thenReturn("PAID");

      AggregateFormula formula = mock(AggregateFormula.class);
      when(formula.hasN()).thenReturn(false);
      when(agg.getFormula()).thenReturn(formula);
      // getN() has no unset state (defaults to 1) — must not be echoed when hasN() is false.
      when(agg.getN()).thenReturn(1);

      MeasureFieldInfo info = WizFieldInfoFactory.createChartMeasureFieldInfo(agg);

      assertNull(info.getSecondaryField());
      assertNull(info.getNOrP());
   }

   @Test
   void crosstabEchoReportsSecondaryFieldAndNOrP() {
      VSAggregateRef agg = mock(VSAggregateRef.class);
      when(agg.getColumnValue()).thenReturn("ORDER_VALUE");
      when(agg.getSecondaryColumnValue()).thenReturn("QUANTITY");

      AggregateFormula formula = mock(AggregateFormula.class);
      when(formula.hasN()).thenReturn(true);
      when(agg.getFormula()).thenReturn(formula);
      when(agg.getN()).thenReturn(90);

      MeasureFieldInfo info = WizFieldInfoFactory.createCrosstabMeasureFieldInfo(agg);

      assertEquals("QUANTITY", info.getSecondaryField());
      assertEquals(90, info.getNOrP());
   }
}
