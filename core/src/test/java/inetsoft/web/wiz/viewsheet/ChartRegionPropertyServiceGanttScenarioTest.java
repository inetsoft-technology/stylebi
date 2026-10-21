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
package inetsoft.web.wiz.viewsheet;

import inetsoft.graph.EGraph;
import inetsoft.graph.coord.*;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.scale.TimeScale;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.graph.GanttGraphGenerator;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.region.ChartArea;
import inetsoft.test.*;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.composer.vs.dialog.RegionPropertyDialogService;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.graph.model.dialog.AxisPropertyDialogModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.security.Principal;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78919, the reporter's exact chart: a Gantt with y = [PHASE, TASK_NAME], start
 * Min(START_DATE), end Max(END_DATE) and milestone Min(MILESTONE_DATE), driven through
 * list/set like the MCP tools, plus neighbouring axes built from real (unmocked) chart infos:
 * a numeric measure axis, a dimension axis and a single date-measure axis.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartRegionPropertyServiceGanttScenarioTest {
   private static final ZoneId ZONE = ZoneId.systemDefault();
   private static final long JAN_1 = LocalDate.of(2025, 1, 1).atStartOfDay(ZONE)
      .toInstant().toEpochMilli();
   private static final long JUN_20 = LocalDate.of(2025, 6, 20).atStartOfDay(ZONE)
      .toInstant().toEpochMilli();

   @Test
   void theReportersFlowListsAndSetsADateRangeThatMovesTheRenderedAxis() throws Exception {
      Gantt g = gantt(true);

      // The time axis is offered on y with minimum/maximum; x points at y + the start field.
      Map<String, Object> listed = g.h.service.list("tok", principal(), "Chart1", "axis", "y",
                                                    "Min(START_DATE)");
      assertTrue(names(listed).containsAll(List.of("minimum", "maximum")), listed.toString());
      assertNotNull(listed.get("note"));
      Exception noX = assertThrows(IllegalArgumentException.class,
         () -> g.h.service.list("tok", principal(), "Chart1", "axis", "x", null));
      assertTrue(noX.getMessage().contains("target 'y' with field 'Min(START_DATE)'"),
                 noX.getMessage());

      g.h.service.set("tok", principal(), "Chart1", "axis", "y", "Min(START_DATE)",
                      Map.of("minimum", "2025-01-01", "maximum", "2025-06-20"), "");

      assertEquals(Long.valueOf(JAN_1), g.start.getAxisDescriptor().getMinimum());
      assertEquals(Long.valueOf(JUN_20), g.start.getAxisDescriptor().getMaximum());

      Map<String, Object> back = g.h.service.list("tok", principal(), "Chart1", "axis", "y",
                                                  "Min(START_DATE)");
      assertEquals(String.valueOf(JAN_1), prop(back, "minimum").get("value"));
      assertEquals(LocalDate.of(2025, 1, 1).atStartOfDay(ZONE).toOffsetDateTime(),
                   OffsetDateTime.parse((String) prop(back, "minimum").get("date")));
      assertEquals(LocalDate.of(2025, 6, 20).atStartOfDay(ZONE).toOffsetDateTime(),
                   OffsetDateTime.parse((String) prop(back, "maximum").get("date")));

      TimeScale scale = render(g);
      assertEquals(JAN_1, (long) scale.getTicks()[0], "rendered axis starts at the minimum");
      assertTrue(scale.getMax() >= JUN_20, "rendered axis reaches the maximum");
      assertTrue(scale.getMax() < JUN_20 + 40L * 86400000L, "and not far past it");
   }

   @Test
   void epochMillisAsTextOrNumberAreAcceptedTheSameWay() throws Exception {
      Gantt g = gantt(true);

      g.h.service.set("tok", principal(), "Chart1", "axis", "y", "Min(START_DATE)",
                      Map.of("minimum", "1735689600000", "maximum", 1750377600000L), "");

      assertEquals(Long.valueOf(1735689600000L), g.start.getAxisDescriptor().getMinimum());
      assertEquals(Long.valueOf(1750377600000L), g.start.getAxisDescriptor().getMaximum());
      assertEquals(1735689600000L, (long) render(g).getTicks()[0]);
   }

   @Test
   void garbageIsRefusedNamingTheAcceptedFormsAndWritesNothing() throws Exception {
      Gantt g = gantt(true);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> g.h.service.set("tok", principal(), "Chart1", "axis", "y", "Min(START_DATE)",
                               Map.of("minimum", "2025-13-45"), ""));

      assertTrue(thrown.getMessage().contains("2025-01-01") &&
                 thrown.getMessage().contains("epoch milliseconds"), thrown.getMessage());
      assertFalse(thrown.getMessage().contains("finite number"), thrown.getMessage());
      assertNull(g.start.getAxisDescriptor().getMinimum());
      verifyNoDialogWrite(g.h);
   }

   @Test
   void endAndMilestoneWritesPointAtTheStartField() throws Exception {
      Gantt g = gantt(true);

      for(String field : List.of("Max(END_DATE)", "Min(MILESTONE_DATE)")) {
         Exception thrown = assertThrows(IllegalArgumentException.class,
            () -> g.h.service.set("tok", principal(), "Chart1", "axis", "y", field,
                                  Map.of("minimum", "2025-01-01"), ""));
         assertTrue(thrown.getMessage().contains("Pass field 'Min(START_DATE)'"),
                    thrown.getMessage());
      }

      verifyNoDialogWrite(g.h);
   }

   @Test
   void aMergedGanttRendersTheRange() throws Exception {
      Gantt g = gantt(false);

      g.h.service.set("tok", principal(), "Chart1", "axis", "y", "Min(START_DATE)",
                      Map.of("minimum", "2025-01-01", "maximum", "2025-06-20"), "");

      TimeScale scale = render(g);
      assertEquals(JAN_1, (long) scale.getTicks()[0]);
      assertTrue(scale.getMax() >= JUN_20);
   }

   /** Neighbour: a numeric measure keeps numeric min/max and a log scale. */
   @Test
   void aNumericMeasureAxisKeepsNumericRangeAndLogScale() throws Exception {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      info.addXField(dimension("REGION"));
      VSChartAggregateRef sales = aggregate("SALES", XSchema.DOUBLE, AggregateFormula.SUM);
      info.addYField(sales);
      Harness h = harness(info);

      h.service.set("tok", principal(), "Chart1", "axis", "y", sales.getFullName(),
                    Map.of("minimum", "10.5", "maximum", "200", "logarithmicScale", true), "");

      AxisDescriptor desc = h.handler.getAxisDescriptor(info, sales.getFullName(), "y",
                                                        new java.util.concurrent.atomic.AtomicBoolean());
      assertEquals(10.5, desc.getMinimum().doubleValue(), 1e-9);
      assertEquals(200, desc.getMaximum().doubleValue(), 1e-9);
      assertTrue(desc.isLogarithmicScale());

      Exception date = assertThrows(IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Chart1", "axis", "y", sales.getFullName(),
                             Map.of("minimum", "2025-01-01"), ""));
      assertTrue(date.getMessage().contains("finite number"), date.getMessage());
   }

   /** Neighbour: a dimension axis still refuses minimum/maximum. */
   @Test
   void aDimensionAxisStillRefusesARange() throws Exception {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      info.addXField(dimension("REGION"));
      info.addYField(aggregate("SALES", XSchema.DOUBLE, AggregateFormula.SUM));
      Harness h = harness(info);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Chart1", "axis", "x", "REGION",
                             Map.of("minimum", "1"), ""));

      assertTrue(thrown.getMessage().contains("dimension or discrete measure"),
                 thrown.getMessage());
      assertFalse(thrown.getMessage().contains("Gantt"), thrown.getMessage());
      verifyNoDialogWrite(h);
   }

   /** Neighbour: a single date measure takes a date range exactly and refuses log/reverse. */
   @Test
   void aSingleDateMeasureAxisTakesADateRange() throws Exception {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_POINT);
      info.addXField(dimension("REGION"));
      VSChartAggregateRef last = aggregate("ORDER_DATE", XSchema.DATE, AggregateFormula.MAX);
      info.addYField(last);
      Harness h = harness(info);

      h.service.set("tok", principal(), "Chart1", "axis", "y", last.getFullName(),
                    Map.of("minimum", "2025-01-01"), "");

      AxisDescriptor desc = h.handler.getAxisDescriptor(info, last.getFullName(), "y",
                                                        new java.util.concurrent.atomic.AtomicBoolean());
      assertEquals(Long.valueOf(JAN_1), desc.getMinimum());

      for(String key : List.of("logarithmicScale", "reverse", "shared")) {
         Exception thrown = assertThrows(IllegalArgumentException.class,
            () -> h.service.set("tok", principal(), "Chart1", "axis", "y", last.getFullName(),
                                Map.of(key, true), ""));
         assertTrue(thrown.getMessage().contains("time (date) axis"), thrown.getMessage());
      }
   }

   private static TimeScale render(Gantt g) {
      g.info.setRTStartField(g.start);
      g.info.setRTEndField(g.end);
      g.info.setRTMilestoneField(g.milestone);

      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { GraphUtil.getName(g.start), GraphUtil.getName(g.end), GraphUtil.getName(g.milestone) },
         { date("2025-02-01"), date("2025-02-15"), date("2025-02-10") },
         { date("2025-02-10"), date("2025-03-01"), date("2025-03-01") },
      });
      GanttGraphGenerator gen = new GanttGraphGenerator(
         g.info, new ChartDescriptor(), null, data, null, null, 0, new Dimension(400, 300));
      EGraph graph = gen.createEGraph();
      TimeScale scale = timeScale(graph.getCoordinate());
      assertNotNull(scale, "the Gantt must render a time scale");
      scale.init(data);
      return scale;
   }

   private static TimeScale timeScale(Coordinate coord) {
      if(coord instanceof RectCoord rect) {
         Scale y = rect.getYScale();
         return y instanceof TimeScale time ? time : null;
      }

      if(coord instanceof FacetCoord facet) {
         for(Coordinate inner : facet.getInnerCoordinates()) {
            TimeScale time = timeScale(inner);

            if(time != null) {
               return time;
            }
         }
      }

      return null;
   }

   private static void verifyNoDialogWrite(Harness h) throws Exception {
      verify(h.regions, never()).setAxisPropertyDialogModel(
         anyString(), anyString(), anyString(), anyInt(), any(), any(), anyString(),
         any(Principal.class), any());
   }

   @SuppressWarnings("unchecked")
   private static List<String> names(Map<String, Object> listed) {
      return ((List<Map<String, Object>>) listed.get("properties")).stream()
         .map(p -> (String) p.get("name")).toList();
   }

   @SuppressWarnings("unchecked")
   private static Map<String, Object> prop(Map<String, Object> listed, String name) {
      return ((List<Map<String, Object>>) listed.get("properties")).stream()
         .filter(p -> name.equals(p.get("name"))).findFirst().orElseThrow();
   }

   private static Date date(String iso) {
      return new Date(LocalDate.parse(iso).atStartOfDay(ZONE).toInstant().toEpochMilli());
   }

   private record Gantt(Harness h, GanttVSChartInfo info, VSChartAggregateRef start,
                        VSChartAggregateRef end, VSChartAggregateRef milestone) {}

   private record Harness(ChartRegionPropertyService service, RegionPropertyDialogService regions,
                          ChartRegionHandler handler) {}

   private static Gantt gantt(boolean separated) {
      GanttVSChartInfo info = new GanttVSChartInfo();
      info.setChartType(GraphTypes.CHART_GANTT);
      info.setSeparatedGraph(separated);
      info.addYField(dimension("PHASE"));
      info.addYField(dimension("TASK_NAME"));

      VSChartAggregateRef start = aggregate("START_DATE", XSchema.DATE, AggregateFormula.MIN);
      VSChartAggregateRef end = aggregate("END_DATE", XSchema.DATE, AggregateFormula.MAX);
      VSChartAggregateRef milestone =
         aggregate("MILESTONE_DATE", XSchema.DATE, AggregateFormula.MIN);
      info.setStartField(start);
      info.setEndField(end);
      info.setMilestoneField(milestone);

      return new Gantt(harness(info), info, start, end, milestone);
   }

   /** The real service and handler over a mocked session; the mocked composer service does to
    * the descriptor what the real one does. */
   private static Harness harness(VSChartInfo info) {
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);
      when(chart.getVSAssemblyInfo()).thenReturn(mock(ChartVSAssemblyInfo.class));
      when(chart.getAbsoluteName()).thenReturn("Chart1");
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(chart);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      ChartRegionHandler handler = new ChartRegionHandler();
      RegionPropertyDialogService regions = mock(RegionPropertyDialogService.class);
      ChartArea area = mock(ChartArea.class, RETURNS_MOCKS);

      try {
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
         when(sessions.runtimeId(anyString(), any(Principal.class))).thenReturn("rt1");
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
         doAnswer(invocation -> {
            ViewsheetSessionService.Read<?> read = invocation.getArgument(2);
            return read.run(rvs, "rt1", null);
         }).when(sessions).read(anyString(), any(Principal.class), any());

         when(regions.getAxisPropertyDialogModel(anyString(), anyString(), anyString(),
                                                 anyString(), any(), anyString(),
                                                 any(Principal.class)))
            .thenAnswer(invocation -> handler.createAxisPropertyDialogModel(
               info, area, invocation.getArgument(2),
               Integer.parseInt(invocation.getArgument(3)), invocation.getArgument(4), false,
               false));
         doAnswer(invocation -> {
            handler.updateAxisPropertyDialogModel(
               invocation.getArgument(5), info, area, invocation.getArgument(2),
               invocation.getArgument(3), invocation.getArgument(4), false);
            return null;
         }).when(regions).setAxisPropertyDialogModel(
            anyString(), anyString(), anyString(), anyInt(), any(),
            any(AxisPropertyDialogModel.class), anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return new Harness(new ChartRegionPropertyService(sessions, regions, handler), regions,
                         handler);
   }

   private static VSChartDimensionRef dimension(String name) {
      VSChartDimensionRef ref = new VSChartDimensionRef();
      ref.setDataRef(column(name, XSchema.STRING));
      return ref;
   }

   private static VSChartAggregateRef aggregate(String name, String type,
                                                AggregateFormula formula)
   {
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setDataRef(column(name, type));
      ref.setFormula(formula);
      return ref;
   }

   private static ColumnRef column(String name, String type) {
      ColumnRef column = new ColumnRef(new AttributeRef(name));
      column.setDataType(type);
      return column;
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
