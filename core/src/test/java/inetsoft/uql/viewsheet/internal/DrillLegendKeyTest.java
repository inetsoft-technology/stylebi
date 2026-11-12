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
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.XConstants;
import inetsoft.web.viewsheet.handler.chart.VSChartDrillHandler;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A drill never counts toward the legend: it moves the Color field but not the seeded legend key.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DrillLegendKeyTest {
   @AfterEach
   void reset() throws Exception {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
   }

   @Test
   void aDrillFilterOnTheColorFieldDoesNotRequestACount() throws Exception {
      ChartVSAssemblyInfo info = markedChart();
      String before = info.getVSChartInfo().getColorField().getFullName();

      handler()
         .drill(info.getVSChartInfo().getXField(0), assembly, false, false, null, true);
      info.reseedIfResolvedTypeChanged();

      assertNotEquals(before, info.getVSChartInfo().getColorField().getFullName(),
                      "premise: the drill replaced the Color field");
      assertFalse(info.isLegendCountPending());
   }

   @Test
   void aDrillStillReRunsThePerTypeSeed() throws Exception {
      ChartVSAssemblyInfo info = markedChart();
      ResolvedChartType before = info.getSeededType();

      handler()
         .drill(info.getVSChartInfo().getXField(0), assembly, false, false, null, true);
      info.reseedIfResolvedTypeChanged();

      assertNotEquals(before, info.getSeededType(), "premise: the type key moved");
      assertEquals(1, seedRuns, "the per-type seed re-ran");
      assertFalse(info.isLegendCountPending());
   }

   @Test
   void aNoOpDrillLeavesNoGuardForALaterAuthorEdit() throws Exception {
      ChartVSAssemblyInfo info = markedChart();

      info.guardLegendCountFromDrill();
      info.disarmLegendCountGuard();
      info.getVSChartInfo().setColorField(colorOf(dim("region", XConstants.NONE_DATE_GROUP)));
      info.reseedIfResolvedTypeChanged();

      assertTrue(info.isLegendCountPending());
   }

   @Test
   void twoFieldsOfOneActionLeaveNoCount() throws Exception {
      ChartVSAssemblyInfo info = markedChart();
      VSChartDrillHandler handler = handler();

      handler.drill(info.getVSChartInfo().getXField(0), assembly, false, false, null, true);
      handler.drill((ChartRef) info.getVSChartInfo().getColorField().getDataRef(), assembly,
                    false, false, null, true);
      info.reseedIfResolvedTypeChanged();

      assertFalse(info.isLegendCountPending());
   }

   @Test
   void aSecondChartStaysGuardedThroughItsOwnReseedThenDisarms() throws Exception {
      ChartVSAssemblyInfo info = markedChart();
      Viewsheet vs = assembly.getViewsheet();
      ChartVSAssembly other = new ChartVSAssembly(vs, "Chart2");
      vs.addAssembly(other);
      ChartVSAssemblyInfo otherInfo = other.getChartInfo();
      otherInfo.initDefaultFormat();
      otherInfo.setVSChartInfo(bar());
      otherInfo.setVizMark(VizMark.MODERN_LIGHT);
      otherInfo.seedChromeDefaults(VizContext.of(otherInfo));
      otherInfo.reseedIfResolvedTypeChanged();
      otherInfo.clearLegendCountPending();
      info.guardLegendCountFromDrill();
      otherInfo.guardLegendCountFromDrill();

      // the drill moves both charts, the target updates first, then the other one
      info.getVSChartInfo().setColorField(colorOf(dim("region", XConstants.NONE_DATE_GROUP)));
      otherInfo.getVSChartInfo().setColorField(colorOf(dim("region", XConstants.NONE_DATE_GROUP)));
      info.reseedIfResolvedTypeChanged();
      otherInfo.reseedIfResolvedTypeChanged();

      assertFalse(info.isLegendCountPending());
      assertFalse(otherInfo.isLegendCountPending());

      VSChartDrillHandler.disarmLegendCountGuards(vs);
      otherInfo.getVSChartInfo().setColorField(colorOf(dim("state", XConstants.NONE_DATE_GROUP)));
      otherInfo.reseedIfResolvedTypeChanged();

      assertTrue(otherInfo.isLegendCountPending());
   }

   @Test
   void anAuthorsColorSwapStillRequestsACount() throws Exception {
      ChartVSAssemblyInfo info = markedChart();

      info.getVSChartInfo().setColorField(colorOf(dim("region", XConstants.NONE_DATE_GROUP)));
      info.reseedIfResolvedTypeChanged();

      assertTrue(info.isLegendCountPending());
   }

   @Test
   void aKeyChangeLeftBeforeTheDrillStaysAnAuthorsChange() throws Exception {
      ChartVSAssemblyInfo info = markedChart();
      info.getVSChartInfo().setColorField(colorOf(dim("region", XConstants.NONE_DATE_GROUP)));

      handler()
         .drill(info.getVSChartInfo().getXField(0), assembly, false, false, null, true);
      info.reseedIfResolvedTypeChanged();

      assertTrue(info.isLegendCountPending());
   }

   @Test
   void aCloneTakenMidDrillCarriesNoGuard() throws Exception {
      ChartVSAssemblyInfo info = markedChart();
      info.guardLegendCountFromDrill();
      ChartVSAssemblyInfo copy = (ChartVSAssemblyInfo) info.clone();

      copy.getVSChartInfo().setColorField(colorOf(dim("region", XConstants.NONE_DATE_GROUP)));
      copy.reseedIfResolvedTypeChanged();

      assertTrue(copy.isLegendCountPending());
   }

   private VSChartDrillHandler handler() throws Exception {
      ViewsheetService service = mock(ViewsheetService.class);
      when(service.getViewsheet(any(), any())).thenReturn(mock(RuntimeViewsheet.class));
      return new VSChartDrillHandler(null, service, mock(RuntimeViewsheetRef.class));
   }

   // date on X and Color, so a drill filter moves both
   private ChartVSAssemblyInfo markedChart() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      Viewsheet vs = new Viewsheet();
      assembly = new ChartVSAssembly(vs, "Chart1") {
         @Override
         protected VSAssemblyInfo createInfo() {
            return new ChartVSAssemblyInfo() {
               @Override
               protected void seedTypeDefaults(VizContext ctx, ResolvedChartType type) {
                  seedRuns++;
                  super.seedTypeDefaults(ctx, type);
               }
            };
         }
      };
      vs.addAssembly(assembly);
      ChartVSAssemblyInfo info = assembly.getChartInfo();
      info.initDefaultFormat();
      info.setVSChartInfo(bar());
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.seedChromeDefaults(VizContext.of(info));
      info.reseedIfResolvedTypeChanged();
      info.clearLegendCountPending();
      seedRuns = 0;
      return info;
   }

   private static VSChartInfo bar() {
      VSChartInfo bar = new DefaultVSChartInfo();
      bar.addXField(dim("date", XConstants.YEAR_DATE_GROUP));
      bar.addYField(agg("v"));
      bar.setColorField(colorOf(dim("date", XConstants.YEAR_DATE_GROUP)));
      bar.setChartType(GraphTypes.CHART_BAR);
      return bar;
   }

   private static VSAestheticRef colorOf(VSChartDimensionRef ref) {
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(ref);
      return color;
   }

   private static VSChartDimensionRef dim(String name, int level) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new AttributeRef(null, name));
      dim.setGroupColumnValue(name);
      dim.setDataType(level == XConstants.NONE_DATE_GROUP ? XSchema.STRING : XSchema.DATE);
      dim.setDateLevelValue(level + "");
      return dim;
   }

   private static VSChartAggregateRef agg(String name) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new AttributeRef(null, name));
      agg.setColumnValue(name);
      agg.setFormula(AggregateFormula.SUM);
      return agg;
   }

   private ChartVSAssembly assembly;
   private int seedRuns;
}
