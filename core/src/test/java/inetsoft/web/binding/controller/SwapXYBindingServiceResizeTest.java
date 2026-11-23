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
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.binding.event.ChangeSeparateStatusEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Swap XY moves each axis's plot resize to the axis that now shows the same data.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SwapXYBindingServiceResizeTest {
   // initial ratios a draw reports for each orientation
   private static final double VERTICAL_W = 2;
   private static final double VERTICAL_H = 4;
   private static final double HORIZONTAL_W = 8;
   private static final double HORIZONTAL_H = 0.5;

   @Test
   void aWidthEnlargementMovesToTheHeight() throws Exception {
      Fixture f = new Fixture();
      // 1.5x on the width of the vertical bar
      f.resize(true, 3, 1.5, false, 1, 0);

      f.swap();

      VSChartInfo info = f.info();
      assertFalse(info.isWidthResized(), "width flag");
      assertTrue(info.isHeightResized(), "height flag");
      assertEquals(0, info.getUnitWidthRatioPercent(), 0, "width percent");
      assertEquals(1.5, info.getUnitHeightRatioPercent(), 1e-9, "height percent");
      assertEquals(0.75, info.getUnitHeightRatio(), 1e-9, "height ratio");
   }

   @Test
   void aHeightEnlargementMovesToTheWidth() throws Exception {
      Fixture f = new Fixture();
      // 1.5x on the height of the vertical bar
      f.resize(false, 1, 0, true, 6, 1.5);

      f.swap();

      VSChartInfo info = f.info();
      assertTrue(info.isWidthResized(), "width flag");
      assertFalse(info.isHeightResized(), "height flag");
      assertEquals(1.5, info.getUnitWidthRatioPercent(), 1e-9, "width percent");
      assertEquals(0, info.getUnitHeightRatioPercent(), 0, "height percent");
      assertEquals(12, info.getUnitWidthRatio(), 1e-9, "width ratio");
   }

   @Test
   void aResizeOnBothAxesStaysWithItsData() throws Exception {
      Fixture f = new Fixture();
      // 1.5x on the width, 2x on the height
      f.resize(true, 3, 1.5, true, 8, 2);

      f.swap();

      VSChartInfo info = f.info();
      assertTrue(info.isWidthResized() && info.isHeightResized(), "flags");
      assertEquals(2, info.getUnitWidthRatioPercent(), 1e-9, "width percent");
      assertEquals(1.5, info.getUnitHeightRatioPercent(), 1e-9, "height percent");
      assertEquals(16, info.getUnitWidthRatio(), 1e-9, "width ratio");
      assertEquals(0.75, info.getUnitHeightRatio(), 1e-9, "height ratio");
   }

   @Test
   void swappingTwiceRestoresTheResize() throws Exception {
      Fixture f = new Fixture();
      f.resize(true, 3, 1.5, false, 1, 0);

      f.swap();
      f.swap();

      VSChartInfo info = f.info();
      assertTrue(info.isWidthResized(), "width flag");
      assertFalse(info.isHeightResized(), "height flag");
      assertEquals(3, info.getUnitWidthRatio(), 1e-9, "width ratio");
      assertEquals(1, info.getUnitHeightRatio(), 1e-9, "height ratio");
      assertEquals(1.5, info.getUnitWidthRatioPercent(), 1e-9, "width percent");
      assertEquals(0, info.getUnitHeightRatioPercent(), 0, "height percent");
   }

   @Test
   void aChartWithNoResizeKeepsItsRatios() throws Exception {
      Fixture f = new Fixture();

      f.swap();

      VSChartInfo info = f.info();
      assertFalse(info.isWidthResized() || info.isHeightResized(), "flags");
      assertEquals(1, info.getUnitWidthRatio(), 0, "width ratio");
      assertEquals(1, info.getUnitHeightRatio(), 0, "height ratio");
      assertEquals(0, info.getUnitWidthRatioPercent(), 0, "width percent");
      assertEquals(0, info.getUnitHeightRatioPercent(), 0, "height percent");
   }

   /**
    * A bar chart in a sandbox whose execute does not draw; a draw sets the initial ratios.
    */
   private static final class Fixture {
      Fixture() throws Exception {
         vs = new Viewsheet();
         chart = new ChartVSAssembly(vs, NAME);
         vs.addAssembly(chart);
         chart.getVSAssemblyInfo().initDefaultFormat();
         VSChartInfo cinfo = chart.getVSChartInfo();
         cinfo.addXField(dim("state"));
         cinfo.addYField(agg("v"));
         cinfo.setChartType(GraphTypes.CHART_BAR);
         draw();

         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         ViewsheetSandbox box = mock(ViewsheetSandbox.class);
         when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
         when(rvs.getViewsheet()).thenReturn(vs);
         when(box.getVGraphPair(anyString())).thenAnswer(inv -> {
            draw();
            return null;
         });

         ViewsheetService viewsheetService = mock(ViewsheetService.class);
         when(viewsheetService.getViewsheet(any(), any())).thenReturn(rvs);
         service = new SwapXYBindingService(
            mock(VSBindingService.class), mock(CoreLifecycleService.class),
            mock(VSAssemblyInfoHandler.class), mock(VSChartHandler.class), viewsheetService);
      }

      void resize(boolean wresized, double wratio, double wpercent,
                  boolean hresized, double hratio, double hpercent)
      {
         VSChartInfo info = info();
         info.setWidthResized(wresized);
         info.setUnitWidthRatio(wratio);
         info.setUnitWidthRatioPercent(wpercent);
         info.setHeightResized(hresized);
         info.setUnitHeightRatio(hratio);
         info.setUnitHeightRatioPercent(hpercent);
      }

      void swap() throws Exception {
         service.swapXYBinding("vs1", new ChangeSeparateStatusEvent(NAME, false, false),
                               null, mock(CommandDispatcher.class), "/");
      }

      VSChartInfo info() {
         return chart.getVSChartInfo();
      }

      // a draw reports the initial ratios of the chart's current orientation
      private void draw() {
         VSChartInfo info = info();
         boolean horizontal = Arrays.stream(info.getXFields())
            .anyMatch(VSChartAggregateRef.class::isInstance);
         info.setInitialWidthRatio(horizontal ? HORIZONTAL_W : VERTICAL_W);
         info.setInitialHeightRatio(horizontal ? HORIZONTAL_H : VERTICAL_H);
      }

      private final Viewsheet vs;
      private final ChartVSAssembly chart;
      private final SwapXYBindingService service;
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new AttributeRef(null, name));
      dim.setDataType(XSchema.STRING);
      dim.setGroupColumnValue(name);
      return dim;
   }

   private static VSChartAggregateRef agg(String name) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new AttributeRef(null, name));
      agg.setColumnValue(name);
      agg.setFormula(AggregateFormula.SUM);
      return agg;
   }

   private static final String NAME = "Chart1";
}
