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
 * Bug #78919: minimum/maximum on a time (date) axis, end to end through the real
 * {@code ChartRegionHandler} and {@code AxisPropertyDialogModel}, and then through a real
 * {@code GanttGraphGenerator}, so the assertions are on the descriptor the engine reads and on
 * the rendered {@code TimeScale}, not only on the call succeeding.
 *
 * <p>A Gantt's start, end and milestone are on no shelf. The wiz classifier used to call them a
 * dimension axis and refuse minimum/maximum, after a first check had refused a date because it
 * was not a number.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartRegionPropertyServiceTimeAxisTest {
   private static final long JAN_1 = LocalDate.of(2025, 1, 1)
      .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
   private static final long JUN_1_NOON_UTC =
      Instant.parse("2025-06-01T12:00:00Z").toEpochMilli();

   @Test
   void aDateMinimumAndMaximumOnTheGanttStartFieldMoveTheRenderedTimeAxis() throws Exception {
      Gantt g = gantt(true);

      g.service.set("tok", principal(), "Chart1", "axis", "y", g.startName,
                    Map.of("minimum", "2025-01-01", "maximum", "2025-06-01T12:00:00Z"), "");

      AxisDescriptor desc = g.start.getAxisDescriptor();
      assertEquals(Long.valueOf(JAN_1), desc.getMinimum(), "exact epoch ms, no Float rounding");
      assertEquals(Long.valueOf(JUN_1_NOON_UTC), desc.getMaximum());
      assertEquals(JAN_1, firstTick(g), "the rendered time axis starts at the set minimum");
   }

   @Test
   void epochMillisecondsAreStoredExactly() throws Exception {
      Gantt g = gantt(true);

      g.service.set("tok", principal(), "Chart1", "axis", "y", g.startName,
                    Map.of("minimum", 1735689600000L), "");

      // Float.valueOf("1735689600000") is 1735689633792: the old path moved it by 33.8 s.
      assertEquals(Long.valueOf(1735689600000L), g.start.getAxisDescriptor().getMinimum());
   }

   /** The refuter's finding: on a merged Gantt the dialog writes a descriptor the engine never
    * reads, so the range must land on the start field's own descriptor. */
   @Test
   void aMergedGanttStillMovesTheRenderedTimeAxis() throws Exception {
      Gantt g = gantt(false);

      g.service.set("tok", principal(), "Chart1", "axis", "y", g.startName,
                    Map.of("minimum", "2025-01-01"), "");

      assertEquals(Long.valueOf(JAN_1), g.start.getAxisDescriptor().getMinimum());
      assertNull(g.info.getAxisDescriptor().getMinimum(),
                 "the shared descriptor the Gantt never reads must not take the range");
      assertEquals(JAN_1, firstTick(g));
   }

   /** Review r1: a bare year or a small number used to be read as epoch ms and drawn in 1970. */
   @Test
   void aBareYearOrASmallNumberIsRefusedNotDrawnIn1970() throws Exception {
      for(Object value : List.of("2025", 2025, "99999999", "-5", "2025.5")) {
         Gantt g = gantt(true);

         Exception thrown = assertThrows(IllegalArgumentException.class,
            () -> g.service.set("tok", principal(), "Chart1", "axis", "y", g.startName,
                                Map.of("minimum", value), ""), String.valueOf(value));

         assertTrue(thrown.getMessage().contains("2025-01-01"), thrown.getMessage());
         assertTrue(thrown.getMessage().contains("epoch milliseconds"), thrown.getMessage());
         assertNull(g.start.getAxisDescriptor().getMinimum(), String.valueOf(value));
         verify(g.regions, never()).setAxisPropertyDialogModel(
            anyString(), anyString(), anyString(), anyInt(), any(), any(), anyString(),
            any(Principal.class), any());
      }
   }

   @Test
   void aCompactDateIsReadAsThatDate() throws Exception {
      Gantt g = gantt(true);

      g.service.set("tok", principal(), "Chart1", "axis", "y", g.startName,
                    Map.of("minimum", "20250101"), "");

      assertEquals(Long.valueOf(JAN_1), g.start.getAxisDescriptor().getMinimum());
   }

   @Test
   void anInvalidCompactDateIsRefused() {
      Gantt g = gantt(true);

      assertThrows(IllegalArgumentException.class,
         () -> g.service.set("tok", principal(), "Chart1", "axis", "y", g.startName,
                             Map.of("minimum", "20251399"), ""));
      assertNull(g.start.getAxisDescriptor().getMinimum());
   }

   @Test
   void anEpochMillisecondStringIsStillAccepted() throws Exception {
      Gantt g = gantt(true);

      g.service.set("tok", principal(), "Chart1", "axis", "y", g.startName,
                    Map.of("minimum", "1735689600000", "maximum", "-100000000"), "");

      assertEquals(Long.valueOf(1735689600000L), g.start.getAxisDescriptor().getMinimum());
      assertEquals(Long.valueOf(-100000000L), g.start.getAxisDescriptor().getMaximum());
   }

   @Test
   void theEndFieldRefusalReadsCleanly() throws Exception {
      Gantt g = gantt(true);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> g.service.set("tok", principal(), "Chart1", "axis", "y", g.endName,
                             Map.of("showTicks", false), ""));

      assertFalse(thrown.getMessage().contains("''"), thrown.getMessage());

      String note = (String) g.service.list("tok", principal(), "Chart1", "axis", "y", g.endName)
         .get("note");
      assertTrue(note.contains("'" + g.startName + "'"), note);
      assertFalse(note.contains("''"), note);
   }

   @Test
   void theMissingY2RefusalDoesNotPointToY() {
      Gantt g = gantt(true);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> g.service.list("tok", principal(), "Chart1", "axis", "y2", null));

      assertFalse(thrown.getMessage().contains("target 'y' with field"), thrown.getMessage());
   }

   @Test
   void aWriteOfOtherKeysKeepsTheStoredRangeExact() throws Exception {
      Gantt g = gantt(true);
      g.start.getAxisDescriptor().setMinimum(1735689600000L);

      g.service.set("tok", principal(), "Chart1", "axis", "y", g.startName,
                    Map.of("showTicks", false), "");

      assertEquals(Long.valueOf(1735689600000L), g.start.getAxisDescriptor().getMinimum());
      assertFalse(g.start.getAxisDescriptor().isTicksVisible());
   }

   @Test
   void anEmptyValueClearsTheRange() throws Exception {
      Gantt g = gantt(true);
      g.start.getAxisDescriptor().setMinimum(JAN_1);
      Map<String, Object> patch = new HashMap<>();
      patch.put("minimum", "");

      g.service.set("tok", principal(), "Chart1", "axis", "y", g.startName, patch, "");

      assertNull(g.start.getAxisDescriptor().getMinimum());
   }

   @Test
   void theEndFieldIsRefusedInFavourOfTheStartField() throws Exception {
      Gantt g = gantt(true);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> g.service.set("tok", principal(), "Chart1", "axis", "y", g.endName,
                             Map.of("maximum", "2025-06-01"), ""));

      assertTrue(thrown.getMessage().contains("'" + g.startName + "'"), thrown.getMessage());
      assertNull(g.end.getAxisDescriptor().getMaximum());
      verify(g.regions, never()).setAxisPropertyDialogModel(
         anyString(), anyString(), anyString(), anyInt(), any(), any(), anyString(),
         any(Principal.class), any());
   }

   @Test
   void aValueThatIsNotADateIsRefusedNamingTheAcceptedForms() throws Exception {
      Gantt g = gantt(true);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> g.service.set("tok", principal(), "Chart1", "axis", "y", g.startName,
                             Map.of("minimum", "next tuesday"), ""));

      assertTrue(thrown.getMessage().contains("2025-01-01"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("epoch milliseconds"), thrown.getMessage());
      verify(g.regions, never()).setAxisPropertyDialogModel(
         anyString(), anyString(), anyString(), anyInt(), any(), any(), anyString(),
         any(Principal.class), any());
   }

   @Test
   void scaleOptionsATimeScaleIgnoresAreRefused() {
      Gantt g = gantt(true);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> g.service.set("tok", principal(), "Chart1", "axis", "y", g.startName,
                             Map.of("logarithmicScale", true), ""));

      assertTrue(thrown.getMessage().contains("time (date) axis"), thrown.getMessage());
   }

   @Test
   void listShowsTheRangeAsEpochMillisecondsAndAsADate() throws Exception {
      Gantt g = gantt(true);
      g.start.getAxisDescriptor().setMinimum(JAN_1);

      Map<String, Object> listed =
         g.service.list("tok", principal(), "Chart1", "axis", "y", g.startName);

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> props = (List<Map<String, Object>>) listed.get("properties");
      Map<String, Object> minimum = props.stream()
         .filter(p -> "minimum".equals(p.get("name"))).findFirst().orElseThrow();
      assertEquals(String.valueOf(JAN_1), minimum.get("value"));
      assertEquals(JAN_1, OffsetDateTime.parse((String) minimum.get("date")).toInstant()
         .toEpochMilli());
      assertNotNull(listed.get("note"));
   }

   @Test
   void theMissingXAxisRefusalPointsToYAndTheStartField() {
      Gantt g = gantt(true);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> g.service.list("tok", principal(), "Chart1", "axis", "x", null));

      assertTrue(thrown.getMessage().contains("target 'y' with field '" + g.startName + "'"),
                 thrown.getMessage());
   }

   /** The first tick of the Gantt's rendered time axis. With a set minimum, TimeScale starts its
    * ticks exactly there, so a Float-rounded value would show up as an offset here. */
   private static long firstTick(Gantt g) {
      // What the runtime update does: the generator reads the runtime gantt fields.
      g.info.setRTStartField(g.start);
      g.info.setRTEndField(g.end);

      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { GraphUtil.getName(g.start), GraphUtil.getName(g.end) },
         { date("2025-02-01"), date("2025-02-15") },
         { date("2025-02-10"), date("2025-03-01") },
      });
      GanttGraphGenerator gen = new GanttGraphGenerator(
         g.info, new ChartDescriptor(), null, data, null, null, 0, new Dimension(400, 300));
      EGraph graph = gen.createEGraph();
      TimeScale scale = timeScale(graph.getCoordinate());
      assertNotNull(scale, "the Gantt must render a time scale");
      scale.init(data);
      return (long) scale.getTicks()[0];
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

   private static Date date(String iso) {
      return new Date(LocalDate.parse(iso).atStartOfDay(ZoneId.systemDefault()).toInstant()
                         .toEpochMilli());
   }

   private record Gantt(ChartRegionPropertyService service, RegionPropertyDialogService regions,
                        GanttVSChartInfo info, VSChartAggregateRef start,
                        VSChartAggregateRef end, String startName, String endName) {}

   /** A Gantt with TASK on y and Min(START_DATE)/Max(END_DATE) as start and end, wired to the
    * real handler; the mocked composer service does what the real one does to the descriptor. */
   private static Gantt gantt(boolean separated) {
      GanttVSChartInfo info = new GanttVSChartInfo();
      info.setChartType(GraphTypes.CHART_GANTT);
      info.setSeparatedGraph(separated);

      VSChartDimensionRef task = new VSChartDimensionRef();
      task.setDataRef(column("TASK", XSchema.STRING));
      info.addYField(task);

      VSChartAggregateRef start = aggregate("START_DATE", AggregateFormula.MIN);
      VSChartAggregateRef end = aggregate("END_DATE", AggregateFormula.MAX);
      info.setStartField(start);
      info.setEndField(end);

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
      // The dialog model reads label/enabled state off the laid-out areas; empty ones will do.
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

      return new Gantt(new ChartRegionPropertyService(sessions, regions, handler), regions,
                       info, start, end, start.getFullName(), end.getFullName());
   }

   private static VSChartAggregateRef aggregate(String name, AggregateFormula formula) {
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setDataRef(column(name, XSchema.DATE));
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
