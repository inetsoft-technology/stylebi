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
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.CandleVSChartInfo;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.binding.controller.ChangeChartAestheticService;
import inetsoft.web.binding.controller.ChangeChartRefService;
import inetsoft.web.binding.controller.ChangeChartTypeService;
import inetsoft.web.binding.controller.ChangeSeparateStatusService;
import inetsoft.web.binding.controller.SwapXYBindingService;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.ChartDimensionRefModel;
import inetsoft.web.binding.model.graph.ChartRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81687: the call sites of the calculator-shift guard. Each test fails if its call is
 * removed: the refusal, its message and the rollback come from the guard run on the chart info
 * the write left behind.
 */
@WizAgentTestSupport
@Tag("core")
class ChartBindingCalculatorShiftCallSitesTest {
   private static VSChartDimensionRef quarter() {
      AttributeRef attribute = new AttributeRef(null, "Order Date");
      attribute.setDataType(XSchema.DATE);
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setDataRef(new ColumnRef(attribute));
      dim.setDateLevelValue(String.valueOf(XConstants.QUARTER_DATE_GROUP));
      return dim;
   }

   private static VSChartAggregateRef sumWithChange(int from) {
      AttributeRef attribute = new AttributeRef(null, "Sales");
      attribute.setDataType(XSchema.DOUBLE);
      VSChartAggregateRef sales = new VSChartAggregateRef();
      sales.setDataRef(new ColumnRef(attribute));
      sales.setFormulaValue("Sum");
      ChangeCalc calc = new ChangeCalc();
      calc.setFrom(from);
      calc.setColumnName("Quarter(Order Date)");
      sales.setCalculator(calc);
      return sales;
   }

   private static Principal principal() {
      return () -> "admin";
   }

   private static ChartBindingModel dateModel() {
      ChartBindingModel model = new ChartBindingModel();
      ChartDimensionRefModel date = new ChartDimensionRefModel();
      date.setColumnValue("Order Date");
      date.setName("Order Date");
      model.setXFields(List.<ChartRefModel>of(date));
      return model;
   }

   private static ViewsheetSessionService sessionsFor(RuntimeViewsheet rvs, VSChartInfo info)
      throws Exception
   {
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(chart);
      when(rvs.getViewsheet()).thenReturn(vs);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      doAnswer(invocation -> {
         ((ViewsheetSessionService.Mutation) invocation.getArgument(2)).run(rvs, "rt1", null);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());
      return sessions;
   }

   private static ChartBindingService chartService(ViewsheetSessionService sessions,
                                                   ChartBindingModel model)
   {
      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenReturn(model);
      return new ChartBindingService(
         sessions, binding, mock(ChangeChartRefService.class), mock(ChangeChartTypeService.class),
         mock(SwapXYBindingService.class), mock(ChangeSeparateStatusService.class),
         mock(DataRefModelFactoryService.class));
   }

   private static void assertRefused(IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("calculateInfo.from 7 (Previous Month)"), e.getMessage());
      assertTrue(e.getMessage().contains("quarter"), e.getMessage());
      assertTrue(e.getMessage().contains("rolled back"), e.getMessage());
   }

   /** The write order is calculator first (already on y), the quarter x written afterwards. */
   @Test
   void setShelfRefusesAndRollsBackWhenTheLevelIsWrittenAfterTheCalculator() throws Exception {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_LINE);
      info.addXField(quarter());
      info.addYField(sumWithChange(7));
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.restoreCurrentCheckpoint()).thenReturn(true);
      ChartBindingService service = chartService(sessionsFor(rvs, info), dateModel());

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> service.setShelf("tok", principal(), "Chart1", "x",
            List.of(new FieldRef("Order Date", "dimension", null, "quarter", null)), null, ""));

      verify(rvs).restoreCurrentCheckpoint();
      assertRefused(e);
   }

   @Test
   void setShelfAcceptsAShiftThatLands() throws Exception {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_LINE);
      info.addXField(quarter());
      info.addYField(sumWithChange(5));
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      ChartBindingService service = chartService(sessionsFor(rvs, info), dateModel());

      assertDoesNotThrow(() -> service.setShelf("tok", principal(), "Chart1", "x",
         List.of(new FieldRef("Order Date", "dimension", null, "quarter", null)), null, ""));
      verify(rvs, never()).restoreCurrentCheckpoint();
   }

   @Test
   void setSingleShelfRefusesAndRollsBackACalculatorOnACandleMeasure() throws Exception {
      CandleVSChartInfo info = new CandleVSChartInfo();
      info.setChartType(GraphTypes.CHART_CANDLE);
      info.addXField(quarter());
      info.setHighField(sumWithChange(7));
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.restoreCurrentCheckpoint()).thenReturn(true);
      ChartBindingService service = chartService(sessionsFor(rvs, info), new ChartBindingModel());

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> service.setSingleShelf("tok", principal(), "Chart1", "high",
            new FieldRef("Sales", "measure", "Sum", null, null), null, ""));

      verify(rvs).restoreCurrentCheckpoint();
      assertRefused(e);
   }

   @Test
   void aestheticWriteRefusesAndRollsBack() throws Exception {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_LINE);
      info.addXField(quarter());
      info.addYField(sumWithChange(7));
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.restoreCurrentCheckpoint()).thenReturn(true);
      ViewsheetSessionService sessions = sessionsFor(rvs, info);
      when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenReturn(new ChartBindingModel());
      ChartAestheticAgentService service = new ChartAestheticAgentService(
         sessions, binding, mock(ChangeChartAestheticService.class),
         mock(DataRefModelFactoryService.class));

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> service.setField("tok", principal(), "Chart1", "color",
            new FieldRef("Region", "dimension", null, null, null), null, ""));

      verify(rvs).restoreCurrentCheckpoint();
      assertRefused(e);
   }
}
