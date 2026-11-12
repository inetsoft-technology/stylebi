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
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.vswizard.handler.SyncChartHandler;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The wizard copies the legend layout and titles with their author flags and never stamps one.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class WizardSyncLegendTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aSeededTopLegendSyncedIntoAMarkedTargetIsNotTheAuthors() {
      gateOn();
      ChartVSAssembly original = barOn(new Viewsheet(), "Chart1");
      ChartVSAssembly target = barOn(new Viewsheet(), "Chart2");
      LegendsDescriptor from = legends(original);
      assertEquals(LegendsDescriptor.TOP, from.getLayout(), "premise: a seeded top legend");
      assertFalse(from.isUserLayout(), "premise");

      sync(original, target);

      LegendsDescriptor to = legends(target);
      assertEquals(LegendsDescriptor.TOP, to.getLayout());
      assertFalse(to.isUserLayout());
      assertFalse(to.getColorLegendDescriptor().isUserTitleVisible());
      assertFalse(to.getColorLegendDescriptor().isTitleVisible());
   }

   @Test
   void anAuthorsLayoutTravelsWithItsFlag() {
      gateOn();
      ChartVSAssembly original = barOn(new Viewsheet(), "Chart1");
      legends(original).setAuthorLayout(LegendsDescriptor.LEFT);
      legends(original).getColorLegendDescriptor().setAuthorTitleVisible(true);
      ChartVSAssembly target = barOn(new Viewsheet(), "Chart2");

      sync(original, target);

      LegendsDescriptor to = legends(target);
      assertEquals(LegendsDescriptor.LEFT, to.getLayout());
      assertTrue(to.isUserLayout());
      assertTrue(to.getColorLegendDescriptor().isTitleVisible());
      assertTrue(to.getColorLegendDescriptor().isUserTitleVisible());
   }

   @Test
   void aSeededLayoutStaysOffAnUnmarkedTarget() {
      gateOn();
      ChartVSAssembly original = barOn(new Viewsheet(), "Chart1");
      LegendsDescriptor from = legends(original);
      assertNotNull(original.getChartInfo().getVizMark(), "premise: a marked source");
      assertEquals(LegendsDescriptor.TOP, from.getLayout(), "premise: a seeded top legend");
      assertFalse(from.getColorLegendDescriptor().isTitleVisible(), "premise: a seeded title");
      gateOff();
      ChartVSAssembly target = barOn(new Viewsheet(), "Chart2");
      assertNull(target.getChartInfo().getVizMark(), "premise: an unmarked target");
      assertEquals(LegendsDescriptor.RIGHT, legends(target).getLayout(), "premise: classic");

      sync(original, target);

      LegendsDescriptor to = legends(target);
      assertEquals(LegendsDescriptor.RIGHT, to.getLayout());
      assertFalse(to.isUserLayout());
      assertTrue(to.getColorLegendDescriptor().isTitleVisible());
      assertFalse(to.getColorLegendDescriptor().isUserTitleVisible());
   }

   @Test
   void anAuthorsLayoutTravelsIntoAnUnmarkedTarget() {
      gateOn();
      ChartVSAssembly original = barOn(new Viewsheet(), "Chart1");
      legends(original).setAuthorLayout(LegendsDescriptor.BOTTOM);
      legends(original).getColorLegendDescriptor().setAuthorTitleVisible(false);
      gateOff();
      ChartVSAssembly target = barOn(new Viewsheet(), "Chart2");

      sync(original, target);

      LegendsDescriptor to = legends(target);
      assertEquals(LegendsDescriptor.BOTTOM, to.getLayout());
      assertTrue(to.isUserLayout());
      assertFalse(to.getColorLegendDescriptor().isTitleVisible());
      assertTrue(to.getColorLegendDescriptor().isUserTitleVisible());
   }

   @Test
   void theVerdictTravels() {
      gateOn();
      ChartVSAssembly original = barOn(new Viewsheet(), "Chart1");
      original.getChartInfo().setLegendCrowded(true);
      ChartVSAssembly target = barOn(new Viewsheet(), "Chart2");
      assertFalse(target.getChartInfo().isLegendCrowded(), "premise");

      sync(original, target);

      assertTrue(target.getChartInfo().isLegendCrowded());
   }

   private static void sync(ChartVSAssembly original, ChartVSAssembly target) {
      new SyncChartHandler(new ChartRegionHandler())
         .syncChart(new VSTemporaryInfo(), original, target, false, true);
   }

   private static LegendsDescriptor legends(ChartVSAssembly chart) {
      return chart.getChartDescriptor().getLegendsDescriptor();
   }

   private static void gateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   }

   private static void gateOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
   }

   private static ChartVSAssembly barOn(Viewsheet vs, String name) {
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      vs.addAssembly(chart);
      chart.getVSAssemblyInfo().initDefaultFormat();
      ChartVSAssemblyInfo info = chart.getChartInfo();
      VSChartInfo cinfo = info.getVSChartInfo();
      VSChartDimensionRef dim = new VSChartDimensionRef(new AttributeRef(null, "state"));
      dim.setDataType(XSchema.STRING);
      dim.setGroupColumnValue("state");
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new AttributeRef(null, "v"));
      agg.setColumnValue("v");
      agg.setFormula(AggregateFormula.SUM);
      cinfo.addXField(dim);
      cinfo.addYField(agg);
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(new VSChartDimensionRef(new AttributeRef(null, "region")));
      color.setVisualFrameWrapper(new CategoricalColorFrameWrapper());
      cinfo.setColorField(color);
      cinfo.setChartType(GraphTypes.CHART_BAR);
      info.seedChromeDefaults(VizContext.of(info));
      return chart;
   }
}
