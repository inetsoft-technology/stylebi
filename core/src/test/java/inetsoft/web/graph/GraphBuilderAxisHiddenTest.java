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
package inetsoft.web.graph;

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
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizModernizeUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which charts offer "Show All Axes": a line the modern seed turned off is not a hidden axis.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class GraphBuilderAxisHiddenTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aSeededModernBarHidesNoAxis() {
      for(boolean separated : new boolean[] { true, false }) {
         ChartVSAssemblyInfo info = bar(true, separated);
         VSChartInfo cinfo = info.getVSChartInfo();
         String kind = separated ? "separated" : "combined";
         assertFalse(valueAxis(cinfo).isLineVisible(), "premise: the seed took the line, " + kind);

         assertFalse(GraphBuilder.isAxisHidden(cinfo, false, modern(info)), "normal mode, " + kind);
         assertFalse(GraphBuilder.isAxisHidden(cinfo, true, modern(info)), "max mode, " + kind);
      }
   }

   @Test
   void anAuthorHiddenValueLineIsAHiddenAxis() {
      for(boolean separated : new boolean[] { true, false }) {
         ChartVSAssemblyInfo info = bar(true, separated);
         VSChartInfo cinfo = info.getVSChartInfo();
         valueAxis(cinfo).setAuthorLineVisible(false);

         assertTrue(GraphBuilder.isAxisHidden(cinfo, false, modern(info)),
                    separated ? "separated" : "combined");
      }
   }

   @Test
   void aModernMaxModeLineOffIsAHiddenAxis() {
      ChartVSAssemblyInfo info = bar(true, true);
      VSChartInfo cinfo = info.getVSChartInfo();
      cinfo.getXField(0).getAxisDescriptor().setMaxModeLineVisible(false);

      assertTrue(GraphBuilder.isAxisHidden(cinfo, true, modern(info)));
   }

   @Test
   void aClassicBarWithALineOffHidesAnAxis() {
      for(boolean separated : new boolean[] { true, false }) {
         ChartVSAssemblyInfo info = bar(false, separated);
         VSChartInfo cinfo = info.getVSChartInfo();
         String kind = separated ? "separated" : "combined";
         assertFalse(GraphBuilder.isAxisHidden(cinfo, false, modern(info)),
                     "premise: none hidden, " + kind);

         valueAxis(cinfo).setLineVisible(false);

         assertFalse(valueAxis(cinfo).isUserLineVisible(), "premise: no flag, " + kind);
         assertTrue(GraphBuilder.isAxisHidden(cinfo, false, modern(info)), kind);
      }
   }

   // ---- harness -----------------------------------------------------------------------------

   // a seeded bar of state by Sum(v), marked when the gate is on at creation
   private static ChartVSAssemblyInfo bar(boolean marked, boolean separated) {
      SreeEnv.setProperty("viewsheet.modernVisualization", marked ? "true" : "false");
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      VSChartInfo cinfo = info.getVSChartInfo();
      cinfo.setSeparatedGraph(separated);
      cinfo.addXField(dim("state"));
      cinfo.addYField(agg("v"));
      cinfo.setChartType(GraphTypes.CHART_BAR);
      VizModernizeUtil.reseedAfterRestore(info);
      assertEquals(marked, modern(info), "premise: the chart's mark");
      return info;
   }

   // a separated chart draws each measure on its own axis, a combined one on the chart's
   private static AxisDescriptor valueAxis(VSChartInfo cinfo) {
      return cinfo.isSeparatedGraph() ?
         cinfo.getYField(0).getAxisDescriptor() : cinfo.getAxisDescriptor();
   }

   private static boolean modern(ChartVSAssemblyInfo info) {
      return VizContext.of(info).modern;
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
}
