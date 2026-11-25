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

import inetsoft.graph.*;
import inetsoft.graph.coord.*;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.visual.LineVO;
import inetsoft.graph.visual.VisualObject;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.report.internal.graph.ChangeChartTypeProcessor;
import inetsoft.report.script.viewsheet.ChartVSAScriptable;
import inetsoft.report.script.viewsheet.VSChartBindingScriptable;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A radar (or filled radar) with a Break By dimension and the same measure on Y twice draws
 * the measure once, like a bar chart does, instead of failing in OneVarParallelCoord.
 * The generator path reaches ConfigurationContext, so the class sets up a Spring context.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RadarSameMeasureTwiceTest {
   private static final String[] PRODUCTS = { "Apparel", "Beauty", "Electronics", "Garden", "Toys" };

   private static VSChartDimensionRef product() {
      return new VSChartDimensionRef(new BaseField("Product"));
   }

   private static VSChartAggregateRef sales(AggregateFormula formula) {
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setDataRef(new BaseField("Sales"));
      ref.setFormula(formula);
      return ref;
   }

   private static RadarVSChartInfo radar(int type, AggregateFormula... formulas) {
      RadarVSChartInfo info = new RadarVSChartInfo();
      info.setChartType(type);
      info.setRTChartType(type);
      info.addGroupField(product());

      for(AggregateFormula formula : formulas) {
         info.addYField(sales(formula));
      }

      info.updateChartType(false);
      return info;
   }

   // one column per distinct measure name, as the chart query returns them
   private static DefaultDataSet data(ChartInfo info) {
      List<String> headers = new ArrayList<>();
      headers.add("Product");

      for(ChartRef ref : info.getRTYFields()) {
         if(ref.isMeasure() && !headers.contains(ref.getFullName())) {
            headers.add(ref.getFullName());
         }
      }

      Object[][] rows = new Object[PRODUCTS.length + 1][];
      rows[0] = headers.toArray();

      for(int i = 0; i < PRODUCTS.length; i++) {
         Object[] row = new Object[headers.size()];
         row[0] = PRODUCTS[i];

         for(int c = 1; c < row.length; c++) {
            row[c] = (double) (i + 1) * 10 * c;
         }

         rows[i + 1] = row;
      }

      return new DefaultDataSet(rows);
   }

   private static EGraph egraph(ChartInfo info, DefaultDataSet data) {
      GraphGenerator gen = GraphGenerator.getGenerator(
         info, new ChartDescriptor(), null, null, data, new VariableTable(),
         XSourceInfo.NONE, null);
      return gen.createEGraph();
   }

   private static VGraph plot(ChartInfo info) {
      DefaultDataSet data = data(info);
      return Plotter.getPlotter(egraph(info, data)).plotAndLayout(data, 0, 0, 400, 400);
   }

   private static Coordinate innerCoord(VGraph vg) {
      return ((PolarCoord) vg.getCoordinate()).getCoordinate();
   }

   private static List<Point2D> vertices(VGraph vg) {
      List<Point2D> list = new ArrayList<>();

      for(VisualObject vo : GTool.getVOs(vg)) {
         if(vo instanceof LineVO line) {
            for(Point2D pt : line.getTransformedPoints()) {
               if(pt != null) {
                  list.add(pt);
               }
            }
         }
      }

      return list;
   }

   private static void assertDrawsLikeSumOnce(int type, VGraph vg) {
      VGraph once = plot(radar(type, AggregateFormula.SUM));

      assertInstanceOf(OneVarParallelCoord.class, innerCoord(vg));
      assertEquals(PRODUCTS.length, ((OneVarParallelCoord) innerCoord(vg)).getParallelScales().length,
                   "one spoke per Break By value");
      assertEquals(PRODUCTS.length, vertices(vg).size());
      assertEquals(vertices(once), vertices(vg));
   }

   @ParameterizedTest(name = "chart type {0}")
   @ValueSource(ints = { GraphTypes.CHART_RADAR, GraphTypes.CHART_FILL_RADAR })
   void sameMeasureTwiceWithBreakByDrawsLikeTheMeasureOnce(int type) {
      RadarVSChartInfo info = radar(type, AggregateFormula.SUM, AggregateFormula.SUM);

      VGraph vg = assertDoesNotThrow(() -> plot(info));

      assertDrawsLikeSumOnce(type, vg);
   }

   @ParameterizedTest(name = "chart type {0}")
   @ValueSource(ints = { GraphTypes.CHART_RADAR, GraphTypes.CHART_FILL_RADAR })
   void sameMeasureTwiceIsTheOneMeasureRadar(int type) {
      RadarVSChartInfo info = radar(type, AggregateFormula.SUM, AggregateFormula.SUM);

      assertTrue(GraphTypes.isRadarOne(info));
      assertFalse(GraphTypes.isRadarN(info));
   }

   @Test
   void editMeasureBackToTheSameAggregateDraws() {
      RadarVSChartInfo info = radar(GraphTypes.CHART_RADAR, AggregateFormula.SUM, AggregateFormula.AVG);
      // Edit Measure: Average back to Sum
      ((VSChartAggregateRef) info.getYField(1)).setFormula(AggregateFormula.SUM);

      VGraph vg = assertDoesNotThrow(() -> plot(info));

      assertDrawsLikeSumOnce(GraphTypes.CHART_RADAR, vg);
   }

   @ParameterizedTest(name = "bar to chart type {0}")
   @ValueSource(ints = { GraphTypes.CHART_RADAR, GraphTypes.CHART_FILL_RADAR })
   void barWithSameMeasureTwiceSwitchedToRadarDraws(int type) {
      DefaultVSChartInfo bar = new DefaultVSChartInfo();
      bar.setChartType(GraphTypes.CHART_BAR);
      bar.addXField(product());
      bar.addYField(sales(AggregateFormula.SUM));
      bar.addYField(sales(AggregateFormula.SUM));
      bar.updateChartType(false);

      ChartInfo info = new ChangeChartTypeProcessor(
         GraphTypes.CHART_BAR, type, false, false, null, bar, false, new ChartDescriptor()).process();
      // runtime type, set by the assembly update after a style change
      info.setRTChartType(type);

      assertInstanceOf(RadarVSChartInfo.class, info);
      assertEquals(type, info.getChartType());
      assertEquals(1, info.getGroupFieldCount(), "the bar's X dimension moves to Break By");
      assertEquals(2, info.getYFieldCount());

      VGraph vg = assertDoesNotThrow(() -> plot(info));

      assertDrawsLikeSumOnce(type, vg);
   }

   @Test
   void scriptSetFormulaToTheSameAggregateDraws() {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("radar-same-measure");
      ChartVSAssembly chart = new ChartVSAssembly();
      ChartVSAssemblyInfo chartInfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      chartInfo.setName("chart1");
      chartInfo.setVSChartInfo(radar(GraphTypes.CHART_RADAR, AggregateFormula.SUM, AggregateFormula.AVG));
      vs.addAssembly(chart);

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getID()).thenReturn("radar-same-measure");
      when(box.getViewsheet()).thenReturn(vs);
      ChartVSAScriptable script = new ChartVSAScriptable(box);
      script.setAssembly("chart1");

      new VSChartBindingScriptable(script)
         .setFormula("Sales", ChartConstants.SUM_FORMULA, ChartConstants.BINDING_FIELD);

      VSChartInfo info = chartInfo.getVSChartInfo();
      assertEquals(info.getYField(0).getFullName(), info.getYField(1).getFullName(),
                   "the script made both measures the same aggregate");

      VGraph vg = assertDoesNotThrow(() -> plot(info));

      assertDrawsLikeSumOnce(GraphTypes.CHART_RADAR, vg);
   }

   @ParameterizedTest(name = "chart type {0}")
   @ValueSource(ints = { GraphTypes.CHART_RADAR, GraphTypes.CHART_FILL_RADAR })
   void oneMeasureWithBreakByKeepsOneSpokePerValue(int type) {
      RadarVSChartInfo info = radar(type, AggregateFormula.SUM);

      assertTrue(GraphTypes.isRadarOne(info));
      VGraph vg = plot(info);

      assertInstanceOf(OneVarParallelCoord.class, innerCoord(vg));
      assertEquals(PRODUCTS.length, ((OneVarParallelCoord) innerCoord(vg)).getParallelScales().length);
      assertEquals(PRODUCTS.length, vertices(vg).size());
   }

   @ParameterizedTest(name = "chart type {0}")
   @ValueSource(ints = { GraphTypes.CHART_RADAR, GraphTypes.CHART_FILL_RADAR })
   void twoDifferentMeasuresKeepOneSpokePerMeasure(int type) {
      RadarVSChartInfo info = radar(type, AggregateFormula.SUM, AggregateFormula.AVG);

      assertFalse(GraphTypes.isRadarOne(info));
      assertTrue(GraphTypes.isRadarN(info));
      VGraph vg = plot(info);

      assertInstanceOf(ParallelCoord.class, innerCoord(vg));
      assertEquals(2, ((ParallelCoord) innerCoord(vg)).getParallelScales().length);
   }
}
