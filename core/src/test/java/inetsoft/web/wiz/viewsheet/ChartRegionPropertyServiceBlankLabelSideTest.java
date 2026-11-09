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
import inetsoft.test.*;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.composer.vs.dialog.RegionPropertyDialogService;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.graph.model.dialog.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80505 item 5: {@code labelOnSecondaryAxis} with a blank field on a separated multi-measure
 * y axis addressed "the first laid-out area", which is another measure once the first measure's
 * labels were moved to the opposite side. Built from real chart-info objects (a real
 * {@link DefaultVSChartInfo} with real {@link VSChartAggregateRef}s that each own an axis
 * descriptor, as the separated graph reads them).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartRegionPropertyServiceBlankLabelSideTest {
   private static final String TOTAL = "Sum(Total)";
   private static final String QUANTITY = "Sum(Quantity)";

   private record Harness(ChartRegionPropertyService service, RegionPropertyDialogService regions) {}

   private static VSChartAggregateRef sum(String column) {
      VSChartAggregateRef m = new VSChartAggregateRef();
      m.setDataRef(new AttributeRef(column));
      m.setColumnValue(column);
      m.setFormula(AggregateFormula.SUM);
      return m;
   }

   private static Harness harness(boolean separated, boolean relocateFirst, String... measures)
      throws Exception
   {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      VSChartDimensionRef region = new VSChartDimensionRef(new AttributeRef("Region"));
      region.setGroupColumnValue("Region");
      info.addXField(region);

      for(String m : measures) {
         info.addYField(sum(m));
      }

      info.setSeparatedGraph(separated);
      info.setRTXFields(info.getXFields());
      info.setRTYFields(info.getYFields());

      if(relocateFirst) {
         ((ChartAggregateRef) info.getYFields()[0]).getAxisDescriptor()
            .setLabelOnSecondaryAxis(true);
      }

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.setVSChartInfo(info);
      vs.addAssembly(chart);

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
      when(sessions.runtimeId(anyString(), any(Principal.class))).thenReturn("rt1");
      doAnswer(i -> {
         ViewsheetSessionService.Mutation mutation = i.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());
      doAnswer(i -> ((ViewsheetSessionService.Read<?>) i.getArgument(2)).run(rvs, "rt1", null))
         .when(sessions).read(anyString(), any(Principal.class), any());

      RegionPropertyDialogService regions = mock(RegionPropertyDialogService.class);
      AxisPropertyDialogModel model = new AxisPropertyDialogModel();
      model.setAxisLinePaneModel(new AxisLinePaneModel());
      AxisLabelPaneModel labels = new AxisLabelPaneModel();
      labels.setRotationRadioGroupModel(new RotationRadioGroupModel());
      model.setAxisLabelPaneModel(labels);
      model.setAliasPaneModel(new AliasPaneModel());
      model.setLinear(true);
      when(regions.getAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyString(),
                                              any(), anyString(), any(Principal.class)))
         .thenReturn(model);
      return new Harness(
         new ChartRegionPropertyService(sessions, regions, new ChartRegionHandler()), regions);
   }

   private static Principal principal() {
      Principal p = mock(Principal.class);
      when(p.getName()).thenReturn("tester");
      return p;
   }

   private static void set(Harness h, String field, Map<String, Object> patch) throws Exception {
      h.service.set("tok", principal(), "Chart1", "axis", "y", field, patch, "");
   }

   private static void verifyNoWrite(Harness h) throws Exception {
      verify(h.regions, never()).setAxisPropertyDialogModel(
         anyString(), anyString(), anyString(), anyInt(), any(), any(), anyString(),
         any(Principal.class), any());
   }

   private static void verifyWritten(Harness h) throws Exception {
      verify(h.regions).setAxisPropertyDialogModel(
         anyString(), anyString(), anyString(), anyInt(), any(), any(), anyString(),
         any(Principal.class), any());
   }

   @SuppressWarnings("unchecked")
   private static Map<String, Object> listed(Harness h, String field, String key) throws Exception {
      return ((List<Map<String, Object>>) h.service.list(
         "tok", principal(), "Chart1", "axis", "y", field).get("properties")).stream()
         .filter(p -> key.equals(p.get("name"))).findFirst().orElseThrow();
   }

   @Test
   void refusesABlankFieldWriteAfterTotalIsRelocatedNamingFieldAndMeasures() throws Exception {
      for(boolean value : new boolean[] { false, true }) {
         Harness h = harness(true, true, TOTAL, QUANTITY);

         Exception e = assertThrows(IllegalArgumentException.class,
                                    () -> set(h, null, Map.of("labelOnSecondaryAxis", value)));

         assertTrue(e.getMessage().contains("'field'"), e.getMessage());
         assertTrue(e.getMessage().contains(TOTAL) && e.getMessage().contains(QUANTITY),
                    e.getMessage());
         verifyNoWrite(h);
      }
   }

   @Test
   void refusesABlankFieldWriteBeforeAnyRelocationToo() throws Exception {
      Harness h = harness(true, false, TOTAL, QUANTITY);
      assertThrows(IllegalArgumentException.class,
                   () -> set(h, "", Map.of("labelOnSecondaryAxis", false)));
      verifyNoWrite(h);
   }

   @Test
   void doesNotReportAnotherMeasuresValueForABlankFieldRead() throws Exception {
      Harness h = harness(true, true, TOTAL, QUANTITY);

      Map<String, Object> row = listed(h, null, "labelOnSecondaryAxis");

      assertNull(row.get("value"), String.valueOf(row));
      assertEquals(false, row.get("applies"));
      assertTrue(String.valueOf(row.get("reason")).contains("'field'"), String.valueOf(row));
      assertTrue(String.valueOf(row.get("reason")).contains(QUANTITY), String.valueOf(row));
   }

   @Test
   void anExplicitFieldStillWritesAndReadsOnTheSeparatedChart() throws Exception {
      Harness h = harness(true, true, TOTAL, QUANTITY);

      set(h, TOTAL, Map.of("labelOnSecondaryAxis", false));
      verifyWritten(h);

      Map<String, Object> row = listed(h, TOTAL, "labelOnSecondaryAxis");
      assertFalse(row.containsKey("applies"), String.valueOf(row));
   }

   @Test
   void otherKeysWithABlankFieldAreUnchanged() throws Exception {
      Harness h = harness(true, true, TOTAL, QUANTITY);

      set(h, null, Map.of("showAxisLine", false));
      verifyWritten(h);

      Map<String, Object> other = listed(h, null, "showAxisLine");
      assertFalse(other.containsKey("applies"), String.valueOf(other));
   }

   @Test
   void aMergedChartSharesOneDescriptorSoABlankFieldIsUnchanged() throws Exception {
      Harness h = harness(false, false, TOTAL, QUANTITY);

      set(h, null, Map.of("labelOnSecondaryAxis", false));
      verifyWritten(h);

      Map<String, Object> row = listed(h, null, "labelOnSecondaryAxis");
      assertFalse(row.containsKey("applies"), String.valueOf(row));
   }

   @Test
   void aSingleMeasureChartHasNothingToConfuseSoABlankFieldIsUnchanged() throws Exception {
      Harness h = harness(true, false, TOTAL);

      set(h, null, Map.of("labelOnSecondaryAxis", false));
      verifyWritten(h);

      Map<String, Object> row = listed(h, null, "labelOnSecondaryAxis");
      assertFalse(row.containsKey("applies"), String.valueOf(row));
   }
}
