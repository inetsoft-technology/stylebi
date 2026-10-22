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

import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The legend padding each legend of a chart receives: a marked chart with no padding of its own
 * takes its dashboard density's value on its color, shape and size legends alike, and anything
 * else passes through unchanged.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class LegendPaddingRenderTest {
   @AfterEach
   void tearDown() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aMarkedChartTakesTheDashboardDensityOnEveryLegend() {
      assertEquals(Collections.nCopies(3, new Insets(1, 6, 2, 6)),
                   legendPaddings(VizMark.MODERN_LIGHT, "comfortable", null, null));
      assertEquals(Collections.nCopies(3, new Insets(0, 2, 0, 2)),
                   legendPaddings(VizMark.MODERN_LIGHT, "dense", null, null));
   }

   @Test
   void aMarkedCompactChartKeepsTheBuiltInLegend() {
      assertEquals(Collections.nCopies(3, (Insets) null),
                   legendPaddings(VizMark.MODERN_LIGHT, "compact", null, null));
   }

   @Test
   void anAuthorPaddingWinsOnAMarkedChart() {
      assertEquals(Collections.nCopies(3, new Insets(3, 3, 3, 3)),
                   legendPaddings(VizMark.MODERN_LIGHT, "comfortable", new Insets(3, 3, 3, 3),
                                  null));
   }

   @Test
   void aStylesheetPaddingWinsOnAMarkedChart() {
      assertEquals(Collections.nCopies(3, new Insets(5, 5, 5, 5)),
                   legendPaddings(VizMark.MODERN_LIGHT, "dense", null, new Insets(5, 5, 5, 5)));
   }

   @Test
   void anUnmarkedChartIsUnchanged() {
      assertEquals(Collections.nCopies(3, (Insets) null),
                   legendPaddings(null, "comfortable", null, null));
      assertEquals(Collections.nCopies(3, new Insets(3, 3, 3, 3)),
                   legendPaddings(null, "comfortable", new Insets(3, 3, 3, 3), null));
   }

   @Test
   void aDashboardDensityBeatsTheOrgDensity() {
      SreeEnv.setProperty("viewsheet.density", "compact");

      assertEquals(Collections.nCopies(3, new Insets(1, 6, 2, 6)),
                   legendPaddings(VizMark.MODERN_LIGHT, "comfortable", null, null));
   }

   /**
    * The padding each legend site hands its frame, on a bar chart of one dimension by one
    * measure: the color legend, the shape legend (a bar's texture frame) and the size legend.
    */
   private static List<Insets> legendPaddings(VizMark mark, String density, Insets author,
                                              Insets stylesheet)
   {
      Object[][] rows = new Object[7][];
      rows[0] = new Object[]{ "State", "Total" };

      for(int i = 1; i < rows.length; i++) {
         rows[i] = new Object[]{ i % 3 == 0 ? "CA" : (i % 3 == 1 ? "NY" : "TX"),
                                 (double) (i * 100) };
      }

      VSChartAggregateRef total = new VSChartAggregateRef();
      total.setDataRef(new BaseField("Total"));
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setMultiStyles(false);
      cinfo.setChartType(GraphTypes.CHART_BAR);
      cinfo.setRTChartType(GraphTypes.CHART_BAR);
      cinfo.addXField(new VSChartDimensionRef(new BaseField("State")));
      cinfo.addYField(total);
      cinfo.updateChartType(false);

      ChartDescriptor chartDesc = new ChartDescriptor();
      LegendsDescriptor legends = chartDesc.getLegendsDescriptor();

      if(stylesheet != null) {
         legends.setPadding(stylesheet, CompositeValue.Type.CSS);
      }

      if(author != null) {
         legends.setPadding(author);
      }

      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(chartDesc);
      assemblyInfo.setVizMark(mark);
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity(density);
      assemblyInfo.setViewsheet(vs);

      GraphElement elem = GraphGenerator.getGenerator(
         assemblyInfo, null, new DefaultDataSet(rows), new VariableTable(), null,
         XSourceInfo.NONE, null).createEGraph().getElement(0);

      return Arrays.asList(elem.getColorFrame().getLegendSpec().getPadding(),
                           elem.getTextureFrame().getLegendSpec().getPadding(),
                           elem.getSizeFrame().getLegendSpec().getPadding());
   }
}
