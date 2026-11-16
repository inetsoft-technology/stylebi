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

import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.composition.region.ChartArea;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.report.composition.graph.calc.RunningTotalCalc;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XConstants;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Whether a generated axis title drops when it is longer than its axis.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernAxisTitleTest {
   private static final String LONG_NAME =
      "Total revenue measured across every region and product line";

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aGeneratedTitleHidesWhenLongerOnAMarkedChart() {
      EGraph graph = generator("Total", VizMark.MODERN_LIGHT, d -> {}, 400, 240).createEGraph();

      assertTrue(graph.getYTitleSpec().isHideWhenLonger());
      assertTrue(graph.getXTitleSpec().isHideWhenLonger());
   }

   @Test
   void aTypedTitleIsCutAsToday() {
      EGraph graph = generator("Total", VizMark.MODERN_LIGHT,
                               d -> d.getTitlesDescriptor().getYTitleDescriptor()
                                  .setTitleValue("Revenue by region"), 400, 240).createEGraph();

      assertEquals("Revenue by region", graph.getYTitleSpec().getLabel());
      assertFalse(graph.getYTitleSpec().isHideWhenLonger());
   }

   @Test
   void classicTitlesNeverHide() {
      EGraph graph = generator("Total", null, d -> {}, 400, 240).createEGraph();

      assertFalse(graph.getYTitleSpec().isHideWhenLonger());
      assertFalse(graph.getXTitleSpec().isHideWhenLonger());
   }

   @Test
   void anAliasTitleIsGenerated() {
      EGraph graph = generator("Total", VizMark.MODERN_LIGHT, d -> {}, 400, 240,
                               agg -> agg.setCaption("Revenue")).createEGraph();

      assertEquals("Revenue", graph.getYTitleSpec().getLabel());
      assertTrue(graph.getYTitleSpec().isHideWhenLonger());
   }

   @Test
   void aHiddenTitleLeavesNoTitleArea() {
      GraphGenerator gen = generator(LONG_NAME, VizMark.MODERN_LIGHT, d -> {}, 300, 180);
      EGraph graph = gen.createEGraph();
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(gen.getData(), 0, 0, 300, 180);

      assertNotNull(graph.getYTitleSpec().getLabel(), "premise: a Y title is generated");
      assertNull(vgraph.getYTitle());
      ChartArea area = new ChartArea(vgraph, graph, gen.getData(), null, chartInfo);
      assertNull(area.getYTitleArea());
   }

   @Test
   void aHiddenTitleOnAFacetedChartDoesNotThrow() {
      GraphGenerator gen = generator(LONG_NAME, VizMark.MODERN_LIGHT, d -> {}, 300, 180, true);
      EGraph graph = gen.createEGraph();
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(gen.getData(), 0, 0, 300, 180);

      assertDoesNotThrow(() -> new ChartArea(vgraph, graph, gen.getData(), null, chartInfo));
   }

   @Test
   void aPointChartDropsSumOnANumber() {
      EGraph graph = pointGraph(GraphTypes.CHART_POINT, VizMark.MODERN_LIGHT,
                                measure("Quantity", "Sum", null), measure("Price", "Sum", null));

      assertEquals("Price", graph.getYTitleSpec().getLabel());
      assertEquals("Quantity", graph.getXTitleSpec().getLabel());
   }

   @Test
   void anAutoChartResolvedToPointDropsSum() {
      EGraph graph = pointGraph(GraphTypes.CHART_AUTO, VizMark.MODERN_LIGHT,
                                measure("Quantity", "Sum", null), measure("Price", "Sum", null));

      assertEquals("Price", graph.getYTitleSpec().getLabel());
      assertEquals("Quantity", graph.getXTitleSpec().getLabel());
   }

   @Test
   void otherFormulasStay() {
      VSChartAggregateRef count = measure("Order", "Count", null);
      count.setDataRef(field("Order", XSchema.STRING));

      for(VSChartAggregateRef y : new VSChartAggregateRef[] {
         measure("Price", "Average", null), measure("Price", "Max", null), count })
      {
         EGraph graph = pointGraph(GraphTypes.CHART_POINT, VizMark.MODERN_LIGHT,
                                   measure("Quantity", "Sum", null), y);
         EGraph classic = pointGraph(GraphTypes.CHART_POINT, null,
                                     measure("Quantity", "Sum", null), y);

         assertEquals(classic.getYTitleSpec().getLabel(), graph.getYTitleSpec().getLabel());
         assertNotEquals(y.getDataRef().toView(), graph.getYTitleSpec().getLabel());
      }
   }

   @Test
   void twoColumnPercentageAndCalculatorStay() {
      VSChartAggregateRef corr = measure("Price", "Correlation", null);
      corr.setSecondaryColumnValue("Quantity");
      VSChartAggregateRef pct = measure("Price", "Sum", null);
      pct.setPercentageOption(XConstants.PERCENTAGE_OF_GRANDTOTAL);
      VSChartAggregateRef running = measure("Price", "Sum", null);
      running.setCalculator(new RunningTotalCalc());

      for(VSChartAggregateRef y : new VSChartAggregateRef[] { corr, pct, running }) {
         EGraph graph = pointGraph(GraphTypes.CHART_POINT, VizMark.MODERN_LIGHT,
                                   measure("Quantity", "Sum", null), y);
         EGraph classic = pointGraph(GraphTypes.CHART_POINT, null,
                                     measure("Quantity", "Sum", null), y);

         assertEquals(classic.getYTitleSpec().getLabel(), graph.getYTitleSpec().getLabel());
         assertNotEquals("Price", graph.getYTitleSpec().getLabel());
      }
   }

   @Test
   void anAuthorCaptionWins() {
      EGraph graph = pointGraph(GraphTypes.CHART_POINT, VizMark.MODERN_LIGHT,
                                measure("Quantity", "Sum", null),
                                measure("Price", "Sum", "Revenue"));

      assertEquals("Revenue", graph.getYTitleSpec().getLabel());
   }

   @Test
   void onlyPointMeasuresDrop() {
      assertEquals("Sum(Price)", pointGraph(GraphTypes.CHART_BAR, VizMark.MODERN_LIGHT,
                                            null, measure("Price", "Sum", null))
                      .getYTitleSpec().getLabel());
      assertEquals("Price", pointGraph(GraphTypes.CHART_POINT_STACK, VizMark.MODERN_LIGHT,
                                       null, measure("Price", "Sum", null))
                      .getYTitleSpec().getLabel());

      VSChartAggregateRef point = measure("Price", "Sum", null);
      point.setChartType(GraphTypes.CHART_POINT);
      point.setRTChartType(GraphTypes.CHART_POINT);
      VSChartAggregateRef bar = measure("Quantity", "Sum", null);
      bar.setChartType(GraphTypes.CHART_BAR);
      bar.setRTChartType(GraphTypes.CHART_BAR);
      EGraph graph = pointGraph(GraphTypes.CHART_BAR, VizMark.MODERN_LIGHT, d -> {}, true,
                                dim("Store"), point, bar);
      String title = graph.getYTitleSpec().getLabel();

      assertTrue(title.contains("Price") && !title.contains("Sum(Price)"), title);
      assertTrue(title.contains("Sum(Quantity)"), title);
   }

   @Test
   void aTypedTitlePatternKeepsTodaysText() {
      EGraph graph = pointGraph(GraphTypes.CHART_POINT, VizMark.MODERN_LIGHT,
                                d -> d.getTitlesDescriptor().getYTitleDescriptor()
                                   .setTitleValue("By {0}"),
                                false, measure("Quantity", "Sum", null),
                                measure("Price", "Sum", null));

      assertEquals("By Sum(Price)", graph.getYTitleSpec().getLabel());
   }

   @Test
   void classicPointChartKeepsTheFormula() {
      EGraph graph = pointGraph(GraphTypes.CHART_POINT, null,
                                measure("Quantity", "Sum", null), measure("Price", "Sum", null));

      assertEquals("Sum(Price)", graph.getYTitleSpec().getLabel());
      assertEquals("Sum(Quantity)", graph.getXTitleSpec().getLabel());
   }

   private static BaseField field(String name, String type) {
      BaseField field = new BaseField(name);
      field.setDataType(type);
      return field;
   }

   private static VSChartAggregateRef measure(String name, String formula, String caption) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(field(name, XSchema.DOUBLE));
      agg.setFormulaValue(formula);

      if(caption != null) {
         agg.setCaption(caption);
      }

      return agg;
   }

   private EGraph pointGraph(int type, VizMark mark, ChartRef x,
                             VSChartAggregateRef... ys)
   {
      return pointGraph(type, mark, d -> {}, false, x, ys);
   }

   private EGraph pointGraph(int type, VizMark mark, Consumer<ChartDescriptor> desc,
                             boolean multi, ChartRef x, VSChartAggregateRef... ys)
   {
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(type);
      cinfo.setRTChartType(type == GraphTypes.CHART_AUTO ? GraphTypes.CHART_POINT : type);
      List<String> names = new ArrayList<>();

      if(x != null) {
         cinfo.addXField(x);
         names.add(GraphUtil.getName(x));
      }

      for(VSChartAggregateRef y : ys) {
         cinfo.addYField(y);
         names.add(GraphUtil.getName(y));
      }

      if(multi) {
         cinfo.setMultiStyles(true);
      }

      cinfo.updateChartType(false);

      // the permission check in updateChartType has no principal here
      for(VSChartAggregateRef y : ys) {
         if(multi) {
            y.setRTChartType(y.getChartType());
         }
      }

      List<Object[]> rows = new ArrayList<>();
      rows.add(names.toArray());

      for(int i = 0; i < 4; i++) {
         Object[] data = new Object[names.size()];
         java.util.Arrays.fill(data, 10.0 * (i + 1));
         if(x instanceof VSChartDimensionRef) {
            data[0] = "S" + i;
         }
         rows.add(data);
      }

      ChartDescriptor descriptor = new ChartDescriptor();
      desc.accept(descriptor);
      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(descriptor);
      assemblyInfo.setVizMark(mark);

      return GraphGenerator.getGenerator(assemblyInfo, null,
                                         new DefaultDataSet(rows.toArray(new Object[0][])),
                                         new VariableTable(), null, XSourceInfo.NONE,
                                         new Dimension(400, 240)).createEGraph();
   }

   private VSChartInfo chartInfo;

   private GraphGenerator generator(String measure, VizMark mark, Consumer<ChartDescriptor> desc,
                                    int w, int h)
   {
      return generator(measure, mark, desc, w, h, agg -> {});
   }

   private GraphGenerator generator(String measure, VizMark mark, Consumer<ChartDescriptor> desc,
                                    int w, int h, boolean facet)
   {
      return generator(measure, mark, desc, w, h, agg -> {}, facet);
   }

   private GraphGenerator generator(String measure, VizMark mark, Consumer<ChartDescriptor> desc,
                                    int w, int h, Consumer<VSChartAggregateRef> aggFix)
   {
      return generator(measure, mark, desc, w, h, aggFix, false);
   }

   private GraphGenerator generator(String measure, VizMark mark, Consumer<ChartDescriptor> desc,
                                    int w, int h, Consumer<VSChartAggregateRef> aggFix,
                                    boolean facet)
   {
      VSChartDimensionRef store = dim("Store");
      VSChartDimensionRef region = dim("Region");
      VSChartAggregateRef total = new VSChartAggregateRef();
      total.setDataRef(new BaseField(measure));
      total.setFormulaValue("Sum");
      aggFix.accept(total);
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_BAR);
      cinfo.setRTChartType(GraphTypes.CHART_BAR);

      if(facet) {
         cinfo.addXField(region);
      }

      cinfo.addXField(store);
      cinfo.addYField(total);
      cinfo.updateChartType(false);
      chartInfo = cinfo;

      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { GraphUtil.getName(region), GraphUtil.getName(store),
                              GraphUtil.getName(total) });

      for(int r = 0; r < 2; r++) {
         for(int s = 0; s < 4; s++) {
            rows.add(new Object[] { "R" + r, "Store " + s, 10.0 * (s + 1) });
         }
      }

      ChartDescriptor descriptor = new ChartDescriptor();
      desc.accept(descriptor);
      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(descriptor);
      assemblyInfo.setVizMark(mark);

      return GraphGenerator.getGenerator(assemblyInfo, null,
                                         new DefaultDataSet(rows.toArray(new Object[0][])),
                                         new VariableTable(), null, XSourceInfo.NONE,
                                         new Dimension(w, h));
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new BaseField(name));
      dim.setGroupColumnValue(name);
      return dim;
   }
}
