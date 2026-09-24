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
package inetsoft.web.wiz.binding;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.graph.calc.ChangeCalc;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.ChartAggregateRefModel;
import inetsoft.web.binding.model.graph.calc.ChangeCalcInfo;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.wiz.binding.model.AssemblyBinding;
import inetsoft.web.wiz.binding.model.FieldRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code BindingReadService}'s {@code dateComparisonSeries} (Bug #77015, DCG-013 b).
 *
 * <p>Its own class rather than a section of {@code BindingReadServiceTest}, because it builds real
 * {@code VSChartAggregateRef} objects and so needs the SREE context that test does without.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class BindingReadServiceDateComparisonTest {
   /**
    * A changeAndValue date comparison renders a y2 change series that lives only on the runtime y
    * fields ({@code ChartDcProcessor.updateAggregatesCalc} appends a calculator-carrying clone
    * there), so the design-time y shelf held only Sum(QUANTITY) and nothing in the read referred
    * to the comparison series at all.
    */
   @Test
   void reportsTheSeriesAnAppliedDateComparisonAddsAtRuntime() {
      VSChartAggregateRef value = new VSChartAggregateRef();
      value.setColumnValue("QUANTITY");
      value.setFormulaValue("Sum");
      VSChartAggregateRef change = (VSChartAggregateRef) value.clone();
      change.setCalculator(new ChangeCalc());
      change.setSecondaryY(true);

      VSChartInfo info = mock(VSChartInfo.class);
      when(info.isAppliedDateComparison()).thenReturn(true);
      when(info.getRTYFields()).thenReturn(new ChartRef[] { value, change });

      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);

      ChartAggregateRefModel designY = new ChartAggregateRefModel();
      designY.setColumnValue("QUANTITY");
      designY.setFormula("Sum");
      ChartBindingModel model = new ChartBindingModel();
      model.addYField(designY);
      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenReturn(model);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("Chart1")).thenReturn(chart);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      AssemblyBinding result = new BindingReadService(binding).read(rvs, "Chart1");

      assertEquals(1, result.shelves().get("y").size(), "the design y shelf is unchanged");
      assertNotNull(result.dateComparisonSeries(), "the comparison series must be reported");
      assertEquals(1, result.dateComparisonSeries().size(),
                   "only the calculator-carrying runtime aggregate is a comparison series");
      FieldRef series = result.dateComparisonSeries().get(0);
      assertEquals("QUANTITY", series.column());
      assertInstanceOf(ChangeCalcInfo.class, series.calculateInfo());
      assertEquals(Boolean.TRUE, series.secondaryY());
      assertNull(value.getCalculator(), "the read must not write onto the live runtime refs");
   }
}
