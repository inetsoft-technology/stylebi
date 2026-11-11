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
package inetsoft.report.composition.graph;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.ChartDescriptor;
import inetsoft.uql.viewsheet.graph.LegendDescriptor;
import inetsoft.uql.viewsheet.graph.LegendsDescriptor;
import inetsoft.uql.viewsheet.graph.VSAestheticRef;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A per-field legend's fill follows the layout of the descriptor that renders, so a script's
 * legend position counts.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VGraphPairLegendLayoutTest {
   // dark, so the in-place fill differs from the white a new legend descriptor starts with
   private static final Color DARK_FILL = new Color(0x3E3C43);

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aScriptMovingTheLegendInPlaceFillsThePerFieldLegend() {
      ChartVSAssemblyInfo info = darkChart();
      VSAestheticRef color = bindColor(info);
      runtimeLayout(info, LegendsDescriptor.IN_PLACE);
      assertEquals(LegendsDescriptor.RIGHT, designLegends(info).getLayout(), "premise");

      ChartFormatDefaults.fix(info);

      assertEquals(DARK_FILL, fill(color));
   }

   @Test
   void aScriptMovingTheLegendOutClearsThePerFieldFill() {
      ChartVSAssemblyInfo info = darkChart();
      VSAestheticRef color = bindColor(info);
      designLegends(info).setLayout(LegendsDescriptor.IN_PLACE);
      runtimeLayout(info, LegendsDescriptor.RIGHT);

      ChartFormatDefaults.fix(info);

      assertNull(fill(color));
   }

   @Test
   void withoutARuntimeCopyTheDesignLayoutDecides() {
      ChartVSAssemblyInfo info = darkChart();
      VSAestheticRef color = bindColor(info);
      designLegends(info).setLayout(LegendsDescriptor.IN_PLACE);
      assertNull(info.getRTChartDescriptor(), "premise");

      ChartFormatDefaults.fix(info);

      assertEquals(DARK_FILL, fill(color));
   }

   @Test
   void eachDescriptorsOwnLegendsFollowItsLayout() {
      ChartVSAssemblyInfo designInPlace = darkChart();
      designLegends(designInPlace).setLayout(LegendsDescriptor.IN_PLACE);
      runtimeLayout(designInPlace, LegendsDescriptor.RIGHT);
      ChartVSAssemblyInfo runtimeInPlace = darkChart();
      runtimeLayout(runtimeInPlace, LegendsDescriptor.IN_PLACE);
      assertEquals(LegendsDescriptor.RIGHT, designLegends(runtimeInPlace).getLayout(), "premise");

      ChartFormatDefaults.fix(designInPlace);
      ChartFormatDefaults.fix(runtimeInPlace);

      assertFills(DARK_FILL, designLegends(designInPlace), "design, in place");
      assertFills(null, runtimeLegends(designInPlace), "runtime, right");
      assertFills(null, designLegends(runtimeInPlace), "design, right");
      assertFills(DARK_FILL, runtimeLegends(runtimeInPlace), "runtime, in place");
   }

   // the colour, shape and size legends of one legends descriptor
   private static void assertFills(Color expected, LegendsDescriptor legends, String what) {
      LegendDescriptor[] all = { legends.getColorLegendDescriptor(),
                                 legends.getShapeLegendDescriptor(),
                                 legends.getSizeLegendDescriptor() };

      for(int i = 0; i < all.length; i++) {
         assertEquals(expected, all[i].getContentTextFormat().getDefaultFormat().getBackground(),
                      what + " " + i);
      }
   }

   private static LegendsDescriptor runtimeLegends(ChartVSAssemblyInfo info) {
      return info.getRTChartDescriptor().getLegendsDescriptor();
   }

   private static ChartVSAssemblyInfo darkChart() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      vs.addAssembly(chart);
      chart.getVSAssemblyInfo().initDefaultFormat();
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      assertEquals(VizMark.MODERN_DARK, info.getVizMark(), "premise: a dark marked chart");
      return info;
   }

   private static VSAestheticRef bindColor(ChartVSAssemblyInfo info) {
      VSAestheticRef color = new VSAestheticRef();
      color.setVisualFrame(new CategoricalColorFrame());
      info.getVSChartInfo().setColorField(color);
      return color;
   }

   private static LegendsDescriptor designLegends(ChartVSAssemblyInfo info) {
      return info.getChartDescriptor().getLegendsDescriptor();
   }

   // a script-made runtime copy, as ChartVSAScriptable makes one
   private static void runtimeLayout(ChartVSAssemblyInfo info, int layout) {
      ChartDescriptor rdesc = (ChartDescriptor) info.getChartDescriptor().clone();
      rdesc.getLegendsDescriptor().setLayout(layout);
      info.setRTChartDescriptor(rdesc);
   }

   private static Color fill(VSAestheticRef ref) {
      return ref.getLegendDescriptor().getContentTextFormat().getDefaultFormat().getBackground();
   }
}
