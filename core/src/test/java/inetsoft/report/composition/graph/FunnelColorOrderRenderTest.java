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
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.guide.legend.Legend;
import inetsoft.graph.guide.legend.LegendGroup;
import inetsoft.graph.guide.legend.LegendItem;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XConstants;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A marked funnel coloured by its stage, drawn by the generator: the top stage takes slot one
 * and heads the legend.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FunnelColorOrderRenderTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aMarkedFunnelGivesSlotOneToTheTopStage() {
      Drawn drawn = draw(VizMark.MODERN_LIGHT, f -> {}, new Viewsheet(), List.of(), ROWS);
      Color[] palette = VSChartPaletteDefaults.seedPalette(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(palette[0], drawn.color("Visit"));
      assertEquals(palette[1], drawn.color("Signup"));
   }

   @Test
   void itsLegendListsTheTopStageFirst() {
      Drawn drawn = draw(VizMark.MODERN_LIGHT, f -> {}, new Viewsheet(), List.of(), ROWS);

      assertEquals(TOP_FIRST, drawn.legend());
   }

   @Test
   void aClassicFunnelIsUnchanged() {
      Drawn drawn = draw(null, f -> {}, new Viewsheet(), List.of(), ROWS);
      Color[] palette = VSChartPaletteDefaults.seedPalette(VizContext.of((VizMark) null));

      assertEquals(palette[0], drawn.color("Purchase"));
      // the classic baseline: alphabetical values, reversed for the stacked element
      assertEquals(List.of("Visit", "Trial", "Signup", "Renewal", "Purchase"), drawn.legend());
   }

   @Test
   void derivedPicksFromAnEarlierDrawGiveWay() {
      Color[] palette = VSChartPaletteDefaults.seedPalette(VizContext.of(VizMark.MODERN_LIGHT));
      Drawn drawn = draw(VizMark.MODERN_LIGHT, f -> {
         String[] alphabetical = { "Purchase", "Renewal", "Signup", "Trial", "Visit" };

         for(int i = 0; i < alphabetical.length; i++) {
            f.setDerivedColor(alphabetical[i], palette[i]);
         }
      }, new Viewsheet(), List.of(), ROWS);

      assertEquals(palette[0], drawn.color("Visit"));
   }

   @Test
   void anAuthorsColourForAStageWins() {
      Drawn drawn = draw(VizMark.MODERN_LIGHT, f -> f.setColor("Visit", Color.RED),
                         new Viewsheet(), List.of(), ROWS);

      assertEquals(Color.RED, drawn.color("Visit"));
   }

   @Test
   void aFunnelWithAnOuterDimensionUsesStageTotals() {
      // R1 alone runs Signup, Trial, Purchase, Visit, Renewal; the totals run from Visit
      Object[][] rows = {
         { "Region", "Stage", "Sum(Users)" },
         { "R1", "Visit", 100 },
         { "R1", "Signup", 500 },
         { "R1", "Trial", 300 },
         { "R1", "Purchase", 150 },
         { "R1", "Renewal", 90 },
         { "R2", "Visit", 900 },
         { "R2", "Signup", 100 },
         { "R2", "Trial", 100 },
         { "R2", "Purchase", 50 },
         { "R2", "Renewal", 10 },
      };
      Drawn drawn = draw(VizMark.MODERN_LIGHT, f -> {}, new Viewsheet(), List.of("Region"), rows);
      Color[] palette = VSChartPaletteDefaults.seedPalette(VizContext.of(VizMark.MODERN_LIGHT));

      for(int i = 0; i < TOP_FIRST.size(); i++) {
         assertEquals(palette[i], drawn.color(TOP_FIRST.get(i)), TOP_FIRST.get(i));
      }
   }

   @Test
   void aStandaloneFunnelLeavesTheSharedPicksAlone() {
      Viewsheet vs = new Viewsheet();
      draw(VizMark.MODERN_LIGHT, f -> {}, vs, List.of(), ROWS);

      assertTrue(vs.getSharedFrames().isEmpty());
   }

   @Test
   void aFunnelWithAPartnerKeepsTheSharedPicksAndListsTheTopStageFirst() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly partner = new ChartVSAssembly(vs, "Bar");
      VSChartInfo bar = new DefaultVSChartInfo();
      bar.setChartType(GraphTypes.CHART_BAR);
      bar.setRTChartType(GraphTypes.CHART_BAR);
      bar.addXField(dim("Stage"));
      bar.addYField(agg("Users"));
      bar.setColorField(color());
      partner.setVSChartInfo(bar);
      vs.addAssembly(partner);
      Drawn drawn = draw(VizMark.MODERN_LIGHT, f -> {}, vs, List.of(), ROWS);
      Color[] palette = VSChartPaletteDefaults.seedPalette(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(palette[0], drawn.color("Purchase"));
      assertFalse(vs.getSharedFrames().isEmpty(), "premise: the funnel shares its picks");
      assertEquals(TOP_FIRST, drawn.legend());
   }

   private record Drawn(EGraph graph, VGraph vgraph) {
      Color color(String stage) {
         return graph.getElement(0).getColorFrame().getColor(stage);
      }

      // top to bottom, then left to right
      List<String> legend() {
         List<LegendItem> items = new ArrayList<>();
         LegendGroup group = vgraph.getLegendGroup();

         for(int i = 0; i < group.getLegendCount(); i++) {
            Legend legend = group.getLegend(i);

            for(int j = 0; j < legend.getVisualCount(); j++) {
               if(legend.getVisual(j) instanceof LegendItem) {
                  items.add((LegendItem) legend.getVisual(j));
               }
            }
         }

         items.sort(Comparator.comparingDouble((LegendItem k) -> -k.getBounds().getY())
                       .thenComparingDouble(k -> k.getBounds().getX()));
         List<String> values = new ArrayList<>();
         items.forEach(k -> values.add(String.valueOf(k.getValue())));
         return values;
      }
   }

   private static Drawn draw(VizMark mark, Consumer<CategoricalColorFrame> stored, Viewsheet vs,
                             List<String> outer, Object[][] rows)
   {
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_FUNNEL);
      cinfo.setRTChartType(GraphTypes.CHART_FUNNEL);
      cinfo.addXField(agg("Users"));
      outer.forEach(name -> cinfo.addYField(dim(name)));
      // the runtime stage as applyFunnelSort leaves it
      VSChartDimensionRef stage = dim("Stage");
      stage.setOrder(XConstants.SORT_VALUE_ASC);
      stage.setSortByColValue("Sum(Users)");
      cinfo.addYField(stage);
      VSAestheticRef color = color();
      CategoricalColorFrame frame = (CategoricalColorFrame) color.getVisualFrame();
      frame.setDefaultColors(VSChartPaletteDefaults.seedPalette(VizContext.of(mark)));
      stored.accept(frame);
      cinfo.setColorField(color);
      cinfo.updateChartType(false);

      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setName("Funnel");
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);
      assemblyInfo.setViewsheet(vs);

      GraphGenerator generator = GraphGenerator.getGenerator(
         assemblyInfo, null, new DefaultDataSet(rows), new VariableTable(), null,
         XSourceInfo.NONE, null);
      EGraph graph = generator.createEGraph();
      assertTrue(GraphTypes.isFunnel(cinfo.getRTChartType()), "premise: a funnel");
      VGraph vg = Plotter.getPlotter(graph).plotAndLayout(generator.getData(), 0, 0, 800, 400);
      return new Drawn(graph, vg);
   }

   private static VSAestheticRef color() {
      VSAestheticRef color = new VSAestheticRef();
      color.setALLDataRef(dim("Stage"));
      color.setVisualFrameWrapper(new CategoricalColorFrameWrapper());
      return color;
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new BaseField(name));
      dim.setGroupColumnValue(name);
      return dim;
   }

   private static VSChartAggregateRef agg(String name) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new BaseField(name));
      agg.setFormulaValue("Sum");
      return agg;
   }

   private static final List<String> TOP_FIRST =
      List.of("Visit", "Signup", "Trial", "Purchase", "Renewal");
   private static final Object[][] ROWS = {
      { "Stage", "Sum(Users)" },
      { "Purchase", 200 },
      { "Renewal", 100 },
      { "Signup", 600 },
      { "Trial", 400 },
      { "Visit", 1000 },
   };
}
