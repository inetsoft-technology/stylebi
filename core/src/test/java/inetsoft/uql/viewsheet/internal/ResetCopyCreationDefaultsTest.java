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

import inetsoft.report.internal.graph.ChartCreationDefaults;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The creation-time chart defaults on the reset copy. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ResetCopyCreationDefaultsTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void theResetCopyStacksAnArea() {
      Viewsheet vs = sheet();
      ChartVSAssemblyInfo area = addChart(vs, "area", GraphTypes.CHART_AREA, false);
      ChartVSAssemblyInfo step = addChart(vs, "step", GraphTypes.CHART_STEP_AREA, true);
      ChartVSAssemblyInfo bar = addChart(vs, "bar", GraphTypes.CHART_BAR, false);

      VizModernizeUtil.resetToModern(vs);

      assertEquals(GraphTypes.CHART_AREA_STACK, area.getVSChartInfo().getChartType());
      assertEquals(GraphTypes.CHART_STEP_AREA_STACK, step.getVSChartInfo().getChartType());
      assertEquals(GraphTypes.CHART_BAR, bar.getVSChartInfo().getChartType());
   }

   @Test
   void theResetCopyKeepsTheDateLevel() {
      Viewsheet vs = sheet();
      ChartVSAssemblyInfo bar = addChart(vs, "bar", GraphTypes.CHART_BAR, false);
      VSChartDimensionRef date = dim("Date");
      date.setDataType(XSchema.DATE);
      date.setDateLevelValue(DateRangeRef.YEAR_INTERVAL + "");
      bar.getVSChartInfo().addXField(date);

      VizModernizeUtil.resetToModern(vs);

      VSChartDimensionRef after = (VSChartDimensionRef) bar.getVSChartInfo().getXField(0);
      assertEquals(DateRangeRef.YEAR_INTERVAL, after.getDateLevel());
   }

   @Test
   void theResetCopySortsAPieAndColoursATreemapAndAMarimekko() {
      for(boolean marked : new boolean[] { false, true }) {
         Viewsheet vs = sheet();
         ChartVSAssemblyInfo pie = addChart(vs, "pie", GraphTypes.CHART_PIE, marked);
         VSChartDimensionRef slice = dim("Segment");
         pie.getVSChartInfo().addYField(sum("Sales"));
         pie.getVSChartInfo().setColorField(aesthetic(slice));

         ChartVSAssemblyInfo tree = addChart(vs, "tree", GraphTypes.CHART_TREEMAP, marked);
         tree.getVSChartInfo().addGroupField(dim("Division"));

         ChartVSAssemblyInfo mekko = addChart(vs, "mekko", GraphTypes.CHART_MEKKO, marked);
         mekko.getVSChartInfo().addXField(dim("Region"));
         mekko.getVSChartInfo().addGroupField(dim("Product"));

         VizModernizeUtil.resetToModern(vs);

         assertEquals(XConstants.SORT_VALUE_DESC, slice.getOrder(), "marked " + marked);
         assertEquals("Sum(Sales)", slice.getSortByColValue());
         assertNotNull(tree.getVSChartInfo().getColorField(), "marked " + marked);
         assertEquals("Division", tree.getVSChartInfo().getColorField().getDataRef().getName());
         assertNotNull(mekko.getVSChartInfo().getColorField(), "marked " + marked);
         assertEquals("Product", mekko.getVSChartInfo().getColorField().getDataRef().getName());
      }
   }

   @Test
   void theResetCopyMovesACandleColourDimensionToPanels() {
      Viewsheet vs = sheet();
      ChartVSAssemblyInfo candle;
      ChartVSAssembly assembly = new ChartVSAssembly(vs, "candle");
      assembly.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(assembly);
      candle = (ChartVSAssemblyInfo) assembly.getVSAssemblyInfo();
      VSChartInfo info = new CandleVSChartInfo();
      info.setChartType(GraphTypes.CHART_CANDLE);
      info.addYField(dim("Market"));
      info.setColorField(aesthetic(dim("Ticker")));
      candle.setVSChartInfo(info);

      VizModernizeUtil.resetToModern(vs);

      VSChartInfo after = candle.getVSChartInfo();
      assertNull(after.getColorField());
      assertEquals(2, after.getYFieldCount());
      assertEquals("Ticker", after.getYField(1).getName());
   }

   @Test
   void theResetCopySavesAWaterfallsSourceOrder() {
      Viewsheet vs = sheet();
      ChartVSAssemblyInfo wf = addChart(vs, "wf", GraphTypes.CHART_WATERFALL, false);
      VSChartDimensionRef item = dim("Item");
      wf.getVSChartInfo().addXField(item);
      wf.getVSChartInfo().addYField(sum("Amount"));

      VizModernizeUtil.resetToModern(vs, info -> d -> List.<Object>of("Revenue", "Returns"));

      VSChartDimensionRef after = (VSChartDimensionRef) wf.getVSChartInfo().getXField(0);
      assertEquals(XConstants.SORT_SPECIFIC, after.getOrder());
      assertEquals(Arrays.asList("Revenue", "Returns"), after.getManualOrderList());
   }

   @Test
   void theOneArgumentResetReadsNoSourceOrder() {
      Viewsheet vs = sheet();
      ChartVSAssemblyInfo wf = addChart(vs, "wf", GraphTypes.CHART_WATERFALL, false);
      wf.getVSChartInfo().addXField(dim("Item"));
      wf.getVSChartInfo().addYField(sum("Amount"));

      VizModernizeUtil.resetToModern(vs);

      VSChartDimensionRef after = (VSChartDimensionRef) wf.getVSChartInfo().getXField(0);
      assertEquals(XConstants.SORT_ASC, after.getOrder());
   }

   @Test
   void modernizeAndRevertApplyNoDefault() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      Viewsheet vs = new Viewsheet();
      ChartVSAssemblyInfo area = addChart(vs, "area", GraphTypes.CHART_AREA, false);
      ChartVSAssemblyInfo pie = addChart(vs, "pie", GraphTypes.CHART_PIE, false);
      VSChartDimensionRef slice = dim("Segment");
      pie.getVSChartInfo().addYField(sum("Sales"));
      pie.getVSChartInfo().setColorField(aesthetic(slice));

      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      VizModernizeUtil.modernize(vs);
      VizModernizeUtil.revert(vs);

      assertEquals(GraphTypes.CHART_AREA, area.getVSChartInfo().getChartType());
      assertEquals(XConstants.SORT_ASC, slice.getOrder());
   }

   private static Viewsheet sheet() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      Viewsheet vs = new Viewsheet();
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      return vs;
   }

   private static ChartVSAssemblyInfo addChart(Viewsheet vs, String name, int type,
                                               boolean marked)
   {
      ChartVSAssembly assembly = new ChartVSAssembly(vs, name);
      assembly.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(assembly);
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.getVSChartInfo().setChartType(type);

      if(marked) {
         info.setVizMark(VizMark.MODERN_LIGHT);
      }

      return info;
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef ref = new VSChartDimensionRef(new AttributeRef(null, name));
      ref.setDataType(XSchema.STRING);
      ref.setGroupColumnValue(name);
      return ref;
   }

   private static VSChartAggregateRef sum(String name) {
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setDataRef(new AttributeRef(null, name));
      ref.setColumnValue(name);
      ref.setFormula(AggregateFormula.SUM);
      return ref;
   }

   private static AestheticRef aesthetic(inetsoft.uql.erm.DataRef ref) {
      AestheticRef aes = new VSAestheticRef();
      aes.setDataRef(ref);
      return aes;
   }
}
