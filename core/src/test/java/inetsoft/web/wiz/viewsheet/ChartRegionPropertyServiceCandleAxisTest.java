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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.region.ChartArea;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
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

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80505 item 2, the reporter's chart: a candle or stock chart with x = Month(Date) as a
 * time series, open/high/low/close = Min/Max/Min/Max(TOTAL) and an EMPTY y shelf. The renderer
 * draws and scales one price axis from the shared axis descriptor, so list, set and the
 * visibility read must all reach it. Built from real chart infos and real refs; the controls
 * are a bar with an empty y shelf (still no y axis) and a candle with a populated y shelf.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartRegionPropertyServiceCandleAxisTest {
   @Test
   void candleYAxisIsListedWithItsScaleKeysAndAWriteReachesTheSharedDescriptor() throws Exception {
      Chart c = candle(false);

      assertTrue(ChartRegionResolver.resolve(mock(RuntimeViewsheet.class), c.assembly).has("y"),
                 "binding reports a y axis for OHLC with an empty y shelf");

      Map<String, Object> listed = c.service.list("tok", principal(), "Chart1", "axis", "y", "Min(TOTAL)");
      assertTrue(names(listed).containsAll(List.of("minimum", "maximum", "increment")),
                 listed.toString());

      c.service.set("tok", principal(), "Chart1", "axis", "y", "Min(TOTAL)",
                    Map.of("minimum", "10", "maximum", "500"), "");

      assertEquals(Float.valueOf(500), c.info.getAxisDescriptor().getMaximum());
      assertEquals(Float.valueOf(10), c.info.getAxisDescriptor().getMinimum());

      Map<String, Object> back = c.service.list("tok", principal(), "Chart1", "axis", "y",
                                                  "Min(TOTAL)");
      assertEquals(500.0, Double.parseDouble(String.valueOf(prop(back, "maximum").get("value"))));
   }

   @Test
   void stockBehavesTheSameAndAnOhlcNameAddressesTheAxis() throws Exception {
      Chart c = candle(true);

      c.service.set("tok", principal(), "Chart1", "axis", "y", c.high.getFullName(),
                    Map.of("maximum", "750"), "");

      assertEquals(Float.valueOf(750), c.info.getAxisDescriptor().getMaximum());
      assertTrue(names(c.service.list("tok", principal(), "Chart1", "axis", "y",
                                      c.open.getFullName())).contains("maximum"));

      Exception unknown = assertThrows(IllegalArgumentException.class,
         () -> c.service.list("tok", principal(), "Chart1", "axis", "y", "NOPE"));
      assertTrue(unknown.getMessage().contains("Unknown field"), unknown.getMessage());
   }

   @Test
   void visibilityReportsTheCandleYAxis() {
      Chart c = candle(false);
      assertEquals("visible", ChartVisibilityState.axisVisibility(
         c.info, List.of("y"), false).get("y"));

      c.info.getAxisDescriptor().setLabelVisible(false);
      c.info.getAxisDescriptor().setLineVisible(false);
      assertEquals("hidden", ChartVisibilityState.axisVisibility(
         c.info, List.of("y"), false).get("y"));
   }

   @Test
   void aBarWithAnEmptyYShelfStillHasNoYAxis() {
      DefaultVSChartInfo bar = new DefaultVSChartInfo();
      bar.setChartType(GraphTypes.CHART_BAR);
      bar.addXField(dimension("CATEGORY", XSchema.STRING));
      Chart c = chart(bar);

      assertFalse(ChartRegionResolver.resolve(mock(RuntimeViewsheet.class), c.assembly).has("y"));
      assertEquals(0, ChartRegionPropertyService.axisCandidates(bar, "y").length);
      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> c.service.list("tok", principal(), "Chart1", "axis", "y", null));
      assertTrue(thrown.getMessage().contains("no 'y' axis"), thrown.getMessage());
   }

   @Test
   void aCandleWithAPopulatedYShelfKeepsItsShelfCandidates() {
      Chart c = candle(false);
      VSChartAggregateRef measure = aggregate("QTY", XSchema.DOUBLE, AggregateFormula.SUM);
      c.info.addYField(measure);

      ChartRef[] refs = ChartRegionPropertyService.axisCandidates(c.info, "y");
      assertEquals(1, refs.length);
      assertSame(measure, refs[0]);
      assertEquals(0, ChartRegionPropertyService.axisCandidates(c.info, "y2").length);
   }

   @Test
   void openAndLowBoundToTheSameAggregateAreOneAxisField() {
      Chart c = candle(false);
      ChartRef[] refs = ChartRegionPropertyService.axisCandidates(c.info, "y");
      assertEquals(List.of("Min(TOTAL)", "Max(TOTAL)"),
                   Arrays.stream(refs).map(ChartRef::getFullName).toList());
   }

   @Test
   void unboundPricesGiveNoCandidatesAndAnyBoundPriceGivesOne() {
      CandleVSChartInfo info = new CandleVSChartInfo();
      info.setChartType(GraphTypes.CHART_CANDLE);
      assertEquals(0, ChartRegionPropertyService.axisCandidates(info, "y").length);

      info.setHighField(aggregate("TOTAL", XSchema.DOUBLE, AggregateFormula.MAX));
      assertEquals(1, ChartRegionPropertyService.axisCandidates(info, "y").length);
   }

   private static class Chart {
      final ChartRegionPropertyService service;
      final VSChartInfo info;
      final ChartVSAssembly assembly;
      VSChartAggregateRef open;
      VSChartAggregateRef high;

      Chart(ChartRegionPropertyService service, VSChartInfo info, ChartVSAssembly assembly) {
         this.service = service;
         this.info = info;
         this.assembly = assembly;
      }
   }

   private static Chart candle(boolean stock) {
      CandleVSChartInfo info = stock ? new StockVSChartInfo() : new CandleVSChartInfo();
      info.setChartType(stock ? GraphTypes.CHART_STOCK : GraphTypes.CHART_CANDLE);
      VSChartDimensionRef month = dimension("ORDER_DATE", XSchema.DATE);
      month.setDateLevel(XConstants.MONTH_DATE_GROUP);
      month.setTimeSeries(true);
      info.addXField(month);

      VSChartAggregateRef open = aggregate("TOTAL", XSchema.DOUBLE, AggregateFormula.MIN);
      VSChartAggregateRef high = aggregate("TOTAL", XSchema.DOUBLE, AggregateFormula.MAX);
      info.setOpenField(open);
      info.setHighField(high);
      info.setLowField(aggregate("TOTAL", XSchema.DOUBLE, AggregateFormula.MIN));
      info.setCloseField(aggregate("TOTAL", XSchema.DOUBLE, AggregateFormula.MAX));
      Chart c = chart(info);
      c.open = open;
      c.high = high;
      return c;
   }

   /** The real service and handler over a mocked session; the mocked composer service does to
    * the descriptor what the real one does. */
   private static Chart chart(VSChartInfo info) {
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

      return new Chart(new ChartRegionPropertyService(sessions, regions, handler), info, chart);
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

   private static VSChartDimensionRef dimension(String name, String type) {
      VSChartDimensionRef ref = new VSChartDimensionRef();
      ref.setDataRef(column(name, type));
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
