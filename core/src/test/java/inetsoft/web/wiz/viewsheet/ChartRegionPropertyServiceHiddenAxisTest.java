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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.vs.dialog.RegionPropertyDialogService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.viewsheet.controller.chart.VSChartAreasService;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79111: reading, or writing with a blank field, a chart axis whose label and line are both
 * hidden threw an ArrayIndexOutOfBoundsException in {@code ChartRegionHandler.getAxisColumnName}
 * (a raw HTTP 500), because a hidden axis leaves its laid-out axis area with no children. The
 * service now refuses it with a message naming the axis and the remedy.
 *
 * <p>Real stack: EmbeddedVS1, a real chart laid out by the sandbox, the real
 * {@code ChartRegionHandler} and {@code RegionPropertyDialogService}; only the session seam and
 * the property-edit service are faked.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class ChartRegionPropertyServiceHiddenAxisTest {
   // ── controls: a shown axis is unchanged ──────────────────────────────────

   @Test
   void shownAxesReadWithAndWithoutField() throws Exception {
      Chart c = chart(Mode.PLAIN);

      assertNotNull(c.service.list("tok", principal(), CHART, "axis", "x", null));
      assertNotNull(c.service.list("tok", principal(), CHART, "axis", "x", "type"));
      assertNotNull(c.service.list("tok", principal(), CHART, "axis", "y", null));
   }

   @Test
   void labelOnlyHiddenAxisStillReads() throws Exception {
      Chart c = chart(Mode.PLAIN);
      c.dim.getAxisDescriptor().setLabelVisible(false);
      c.info.clearRuntime();

      assertNotNull(c.service.list("tok", principal(), CHART, "axis", "x", null));
      assertNotNull(c.service.list("tok", principal(), CHART, "axis", "x", "type"));
   }

   @Test
   void lineOnlyHiddenAxisStillReads() throws Exception {
      Chart c = chart(Mode.PLAIN);
      c.dim.getAxisDescriptor().setLineVisible(false);
      c.info.clearRuntime();

      assertNotNull(c.service.list("tok", principal(), CHART, "axis", "x", null));
   }

   // ── hidden axis: list ────────────────────────────────────────────────────

   @Test
   void hiddenXListWithoutFieldIsRefusedNotA500() throws Exception {
      Chart c = chart(Mode.PLAIN);
      hide(c.dim.getAxisDescriptor(), c);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> c.service.list("tok", principal(), CHART, "axis", "x", null));
      assertRefusal(e, "x");
      assertTrue(e.getMessage().contains("target: 'type'"), "names the column to show: " +
         e.getMessage());
   }

   @Test
   void hiddenYListWithoutFieldIsRefusedNotA500() throws Exception {
      Chart c = chart(Mode.PLAIN);
      hideY(c);

      assertRefusal(assertThrows(IllegalArgumentException.class,
         () -> c.service.list("tok", principal(), CHART, "axis", "y", null)), "y");
   }

   @Test
   void hiddenXListWithFieldIsRefused() throws Exception {
      Chart c = chart(Mode.PLAIN);
      hide(c.dim.getAxisDescriptor(), c);

      assertRefusal(assertThrows(IllegalArgumentException.class,
         () -> c.service.list("tok", principal(), CHART, "axis", "x", "type")), "x");
   }

   @Test
   void hiddenXDoesNotRefuseTheShownY() throws Exception {
      Chart c = chart(Mode.PLAIN);
      hide(c.dim.getAxisDescriptor(), c);

      assertNotNull(c.service.list("tok", principal(), CHART, "axis", "y", null));
   }

   @Test
   void showingTheAxisAgainReadsAgain() throws Exception {
      Chart c = chart(Mode.PLAIN);
      hide(c.dim.getAxisDescriptor(), c);
      assertThrows(IllegalArgumentException.class,
                   () -> c.service.list("tok", principal(), CHART, "axis", "x", null));

      show(c.dim.getAxisDescriptor(), c);
      assertNotNull(c.service.list("tok", principal(), CHART, "axis", "x", null));
   }

   // ── hidden axis: set ─────────────────────────────────────────────────────

   @Test
   void hiddenXSetWithoutFieldIsRefused() throws Exception {
      Chart c = chart(Mode.PLAIN);
      hide(c.dim.getAxisDescriptor(), c);

      assertRefusal(assertThrows(IllegalArgumentException.class,
         () -> c.service.set("tok", principal(), CHART, "axis", "x", null,
                             Map.of("showAxisLine", false), "")), "x");
   }

   @Test
   void hiddenYSetWithoutFieldIsRefused() throws Exception {
      Chart c = chart(Mode.PLAIN);
      hideY(c);

      assertRefusal(assertThrows(IllegalArgumentException.class,
         () -> c.service.set("tok", principal(), CHART, "axis", "y", null,
                             Map.of("showAxisLine", false), "")), "y");
   }

   /** The field-qualified write never indexes the (empty) area, so the guard leaves it alone. */
   @Test
   void hiddenXSetWithFieldIsNotRefusedByTheGuard() throws Exception {
      Chart c = chart(Mode.PLAIN);
      hide(c.dim.getAxisDescriptor(), c);

      try {
         c.service.set("tok", principal(), CHART, "axis", "x", "type",
                       Map.of("showAxisLine", false), "");
      }
      catch(Exception e) {
         assertFalse(String.valueOf(e.getMessage()).contains("is hidden"), e.toString());
      }
   }

   // ── secondary axes ───────────────────────────────────────────────────────

   /** y2: a second measure on the right axis; hide both descriptors the engine may read. */
   @Test
   void hiddenY2ListNeverEndsInTheRawIndexFailure() throws Exception {
      Chart c = chart(Mode.Y2);
      assertNotNull(c.service.list("tok", principal(), CHART, "axis", "y2", null));
      hide(c.info.getAxisDescriptor2(), c);
      hide(c.agg2.getAxisDescriptor(), c);
      Exception thrown = null;

      try {
         c.service.list("tok", principal(), CHART, "axis", "y2", null);
      }
      catch(Exception e) {
         thrown = e;
      }

      System.out.println("HIDDEN-Y2 outcome: " + (thrown == null ? "read" : thrown.getMessage()));

      // Either the engine still draws something for it (a read) or the clean refusal; never the
      // raw index failure.
      if(thrown != null) {
         assertRefusal(thrown, "y2");
      }
   }

   /** x2: measures on x with the second on the secondary axis (top). */
   @Test
   void hiddenX2ListNeverEndsInTheRawIndexFailure() throws Exception {
      Chart c = chart(Mode.X2);
      assertNotNull(c.service.list("tok", principal(), CHART, "axis", "x2", null));
      hide(c.info.getAxisDescriptor2(), c);
      hide(c.agg2.getAxisDescriptor(), c);
      Exception thrown = null;

      try {
         c.service.list("tok", principal(), CHART, "axis", "x2", null);
      }
      catch(Exception e) {
         thrown = e;
      }

      System.out.println("HIDDEN-X2 outcome: " + (thrown == null ? "read" : thrown.getMessage()));

      if(thrown != null) {
         assertRefusal(thrown, "x2");
      }
   }

   private enum Mode { PLAIN, Y2, X2 }

   // ── helpers ──────────────────────────────────────────────────────────────

   private static void assertRefusal(Exception e, String axis) {
      String m = String.valueOf(e.getMessage());
      assertInstanceOf(IllegalArgumentException.class, e, e.toString());
      assertTrue(m.contains("'" + axis + "'"), m);
      assertTrue(m.contains("hidden"), m);
      assertTrue(m.contains("set_chart_element_visibility"), m);
      assertTrue(m.contains("visible: true"), m);
   }

   private static void hide(AxisDescriptor axis, Chart c) {
      axis.setLabelVisible(false);
      axis.setLineVisible(false);
      c.info.clearRuntime();
      c.box.resetDataMap(CHART);
      c.box.clearGraph(CHART);
   }

   /** The left axis reads the chart's descriptor, or the measure's own when the graph is split. */
   private static void hideY(Chart c) {
      c.agg.getAxisDescriptor().setLabelVisible(false);
      c.agg.getAxisDescriptor().setLineVisible(false);
      hide(c.info.getAxisDescriptor(), c);
   }

   private static void show(AxisDescriptor axis, Chart c) {
      axis.setLabelVisible(true);
      axis.setLineVisible(true);
      c.info.clearRuntime();
      c.box.resetDataMap(CHART);
      c.box.clearGraph(CHART);
   }

   private record Chart(ChartRegionPropertyService service, VSChartInfo info,
                        VSChartDimensionRef dim, VSChartAggregateRef agg,
                        VSChartAggregateRef agg2,
                        ViewsheetSandbox box) {
   }

   private Chart chart(Mode mode) throws Exception {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      user = rvs.getUser();
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      TableVSAssembly table = (TableVSAssembly) vs.getAssembly(TABLE);
      ChartVSAssembly chart = new ChartVSAssembly(vs, CHART);
      chart.setSourceInfo((SourceInfo) table.getSourceInfo().clone());
      VSChartInfo info = chart.getVSChartInfo();
      VSChartDimensionRef dim =
         new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, "type")));
      dim.setGroupColumnValue("type");
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue("Sum");
      VSChartAggregateRef agg2 = null;

      if(mode != Mode.PLAIN) {
         agg2 = new VSChartAggregateRef();
         agg2.setColumnValue("temp");
         agg2.setFormulaValue("Sum");
         agg2.setSecondaryY(true);
      }

      if(mode == Mode.X2) {
         // measures on x, the dimension on y: the secondary axis lands on top
         info.addYField(dim);
         info.addXField(agg);
         info.addXField(agg2);
      }
      else {
         info.addXField(dim);
         info.addYField(agg);

         if(agg2 != null) {
            info.addYField(agg2);
         }
      }

      vs.addAssembly(chart);
      box.resetDataMap(CHART);

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.resolve(anyString(), any())).thenReturn(rvs);
      when(sessions.runtimeId(anyString(), any())).thenReturn(rvs.getID());
      when(sessions.read(anyString(), any(), any())).thenAnswer(inv -> {
         ViewsheetSessionService.Read<?> read = inv.getArgument(2);
         return read.run(rvs, rvs.getID(), null);
      });
      when(sessions.mutate(anyString(), any(), any())).thenAnswer(inv -> {
         ViewsheetSessionService.Mutation mutation = inv.getArgument(2);
         return CapturingCommandDispatcher.withCapturingDispatcher(inv.getArgument(1), d -> {
            mutation.run(rvs, rvs.getID(), d);
            return d.getWarnings();
         });
      });

      ChartRegionHandler handler = new ChartRegionHandler();
      RegionPropertyDialogService regions = new RegionPropertyDialogService(
         mock(VSObjectPropertyService.class), viewsheetService, mock(VSBindingService.class),
         new VSChartAreasService(viewsheetService), handler);
      return new Chart(new ChartRegionPropertyService(sessions, regions, handler), info, dim,
                       agg, agg2, box);
   }

   private XPrincipal principal() {
      return (XPrincipal) user;
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   @Autowired ViewsheetService viewsheetService;

   private Principal user;
   private static final String TABLE = "TableView1";
   private static final String CHART = "Chart1";
}
