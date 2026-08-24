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
package inetsoft.web.binding.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.binding.event.ChangeChartTypeEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import inetsoft.report.composition.graph.GraphTypeUtil;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * The service side of {@code ChangeChartTypeProcessor}'s refusals.
 *
 * <p>This is the one caller that turns strict field placement on, because it is the one where a
 * person or an agent asked for the retype and can be told no. Two things have to hold when the
 * answer is no, and neither is visible from a processor-level test:
 *
 * <ol>
 *   <li>the chart must be no worse off than before the call — {@code clearRuntime()} has already
 *       run by the time the refusal happens, so skipping {@code updateAssembly} would leave the
 *       aesthetics reported by every read and absent from what renders, which is exactly the
 *       defect the same-type branch in this class was fixed for;</li>
 *   <li>the refusal must arrive as a {@code MessageCommand}, the way composer services report
 *       failure, rather than as an exception out of a controller — the browser then shows a
 *       dialog naming the field, and the wiz tier's {@code CapturingCommandDispatcher} turns the
 *       same command into a {@code CommandErrorException} for the agent.</li>
 * </ol>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChangeChartTypeServiceTest {
   @Test
   void aRefusedRetypeReportsAnErrorInsteadOfThrowing() throws Exception {
      Harness harness = new Harness(measureOnColorBarChart());

      harness.retypeTo(GraphTypes.CHART_PIE);

      ArgumentCaptor<MessageCommand> command = ArgumentCaptor.forClass(MessageCommand.class);
      verify(harness.dispatcher).sendCommand(command.capture());
      assertEquals(MessageCommand.Type.ERROR, command.getValue().getType(),
                   "a refusal has to arrive as an error command, not as a 500 from a throw");
      assertTrue(command.getValue().getMessage().contains("Discount"),
                 "and it has to name the field that blocked the retype: " +
                 command.getValue().getMessage());
   }

   @Test
   void aRefusedRetypeRebuildsTheRuntimeItAlreadyCleared() throws Exception {
      Harness harness = new Harness(measureOnColorBarChart());

      harness.retypeTo(GraphTypes.CHART_PIE);

      verify(harness.box).updateAssembly("Chart1");
   }

   @Test
   void aRefusedRetypeLeavesTheChartsBindingAlone() throws Exception {
      ChartVSAssembly chart = measureOnColorBarChart();
      Harness harness = new Harness(chart);

      harness.retypeTo(GraphTypes.CHART_PIE);

      VSChartInfo info = chart.getVSChartInfo();
      assertEquals(GraphTypes.CHART_BAR, info.getChartType(), "the type must not have changed");
      assertEquals(1, info.getXFieldCount());
      assertNotNull(info.getColorField(), "the measure on color must still be bound");
      assertEquals("Discount", info.getColorField().getDataRef().getName());
   }

   /** The direction that matters more: an ordinary retype must still go all the way through. */
   @Test
   void anAcceptedRetypeSendsNoErrorCommand() throws Exception {
      Harness harness = new Harness(plainBarChart());

      harness.retypeTo(GraphTypes.CHART_PIE);

      verify(harness.dispatcher, never()).sendCommand(argThat(
         c -> c instanceof MessageCommand m && m.getType() == MessageCommand.Type.ERROR));
   }

   /** Bar, with a measure on color: the pie migration has nowhere to move it to. */
   private static ChartVSAssembly measureOnColorBarChart() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      info.addXField(dimension("Month"));
      info.addYField(aggregate("Sales"));
      info.setColorField(aestheticRef(aggregate("Discount")));
      return chartWith(info);
   }

   private static ChartVSAssembly plainBarChart() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      info.addXField(dimension("Month"));
      info.addYField(aggregate("Sales"));
      return chartWith(info);
   }

   private static ChartVSAssembly chartWith(VSChartInfo info) {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      ((ChartVSAssemblyInfo) chart.getVSAssemblyInfo()).setVSChartInfo(info);
      vs.addAssembly(chart);
      return chart;
   }

   private static VSChartDimensionRef dimension(String field) {
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setGroupColumnValue(field);
      dim.setDataRef(new AttributeRef(field));
      return dim;
   }

   private static VSChartAggregateRef aggregate(String column) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue(column);
      agg.setDataRef(new AttributeRef(column));
      agg.setFormula(AggregateFormula.SUM);
      agg.setAggregated(true);
      return agg;
   }

   private static AestheticRef aestheticRef(DataRef dataRef) {
      VSAestheticRef aref = new VSAestheticRef();
      aref.setDataRef(dataRef);
      return aref;
   }

   /** Everything this service touches on the way to (and past) the refusal. */
   private static final class Harness {
      private Harness(ChartVSAssembly chart) throws Exception {
         this.chart = chart;
         when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
         when(rvs.getViewsheet()).thenReturn(chart.getViewsheet());
         when(viewsheetService.getViewsheet(anyString(), any())).thenReturn(rvs);

         service = new ChangeChartTypeService(
            mock(VSBindingService.class), null, mock(CoreLifecycleService.class),
            mock(ChartRefModelFactoryService.class),
            mock(ChangeSeparateStatusController.class), mock(VSAssemblyInfoHandler.class),
            mock(VSChartHandler.class), mock(VSBindingTreeController.class), viewsheetService);
      }

      /**
       * The chart-style permission gate ahead of the code under test answers false for every real
       * type in a test JVM (no security realm, no context principal), which would short-circuit
       * the call before it ever reaches the retype. Only that one static is stubbed —
       * {@code CALLS_REAL_METHODS} leaves the rest of {@code GraphTypeUtil}, which the retype
       * itself leans on, running for real.
       */
      private void retypeTo(int type) throws Exception {
         ChangeChartTypeEvent event = new ChangeChartTypeEvent();
         event.setName(chart.getAbsoluteName());
         event.setType(type);

         try(MockedStatic<GraphTypeUtil> types =
                mockStatic(GraphTypeUtil.class, CALLS_REAL_METHODS))
         {
            types.when(() -> GraphTypeUtil.checkChartStylePermission(anyInt())).thenReturn(true);
            service.changeChartType("rid", event, mock(Principal.class), dispatcher, "");
         }
      }

      private final ChartVSAssembly chart;
      private final RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      private final ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      private final ViewsheetService viewsheetService = mock(ViewsheetService.class);
      private final CommandDispatcher dispatcher = mock(CommandDispatcher.class);
      private final ChangeChartTypeService service;
   }
}
