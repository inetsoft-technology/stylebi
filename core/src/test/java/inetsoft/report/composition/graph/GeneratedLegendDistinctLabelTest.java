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
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.legend.Legend;
import inetsoft.graph.guide.legend.LegendGroup;
import inetsoft.graph.guide.legend.LegendItem;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.util.ExtendedDecimalFormat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A number field on Color, Size or Shape takes the K/M/B default format. On a modern chart the
 * legend reads each value of a narrow range of years; a classic chart keeps today's text.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class GeneratedLegendDistinctLabelTest {
   private static final List<String> YEAR_TICKS =
      List.of("2024", "2024.2", "2024.4", "2024.6", "2024.8", "2025");

   @Test
   void aModernChartSetsTheFlag() {
      assertTrue(generate(Aesthetic.COLOR, true).graph().isLegendDistinctLabels());
   }

   @Test
   void aClassicChartLeavesTheFlagOff() {
      assertFalse(generate(Aesthetic.COLOR, false).graph().isLegendDistinctLabels());
   }

   @Test
   void aModernColorLegendReadsEachYear() {
      assertEquals(List.of("2024", "2025"), bandLabels(generate(Aesthetic.COLOR, true)));
   }

   @Test
   void aClassicColorLegendKeepsItsText() {
      assertEquals(List.of("2K", "2K"), bandLabels(generate(Aesthetic.COLOR, false)));
   }

   @Test
   void aModernSizeLegendReadsEachValue() {
      assertEquals(YEAR_TICKS, itemLabels(generate(Aesthetic.SIZE, true)));
   }

   @Test
   void aClassicSizeLegendKeepsItsText() {
      assertEquals(Collections.nCopies(6, "2K"), itemLabels(generate(Aesthetic.SIZE, false)));
   }

   @Test
   void aModernShapeLegendReadsEachValue() {
      assertEquals(YEAR_TICKS, itemLabels(generate(Aesthetic.SHAPE, true)));
   }

   @Test
   void aClassicShapeLegendKeepsItsText() {
      assertEquals(Collections.nCopies(6, "2K"), itemLabels(generate(Aesthetic.SHAPE, false)));
   }

   private enum Aesthetic { COLOR, SIZE, SHAPE }

   private record Generated(EGraph graph, Legend legend) {
   }

   // the gradient's end labels
   private static List<String> bandLabels(Generated gen) {
      Legend legend = gen.legend();
      List<String> labels = new ArrayList<>();

      for(int i = 0; i < legend.getVisualCount(); i++) {
         if(legend.getVisual(i) instanceof VLabel label && label != legend.getTitle()) {
            labels.add(label.getText());
         }
      }

      return labels;
   }

   private static List<String> itemLabels(Generated gen) {
      Legend legend = gen.legend();
      List<String> labels = new ArrayList<>();

      for(int i = 0; i < legend.getVisualCount(); i++) {
         if(legend.getVisual(i) instanceof LegendItem item) {
            labels.add(item.getLabel());
         }
      }

      return labels;
   }

   // a point chart with Sum(Yr) on one aesthetic, as the binding leaves it
   private static Generated generate(Aesthetic aesthetic, boolean modern) {
      VSChartDimensionRef region = new VSChartDimensionRef(new BaseField("Region"));
      region.setGroupColumnValue("Region");
      VSChartAggregateRef score = aggregate("Score");
      VSChartAggregateRef yr = aggregate("Yr");
      VSAestheticRef aref = new VSAestheticRef();
      aref.setDataRef(yr);
      aref.setRTDataRef(yr);
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_POINT);
      cinfo.setRTChartType(GraphTypes.CHART_POINT);
      cinfo.addXField(region);
      cinfo.addYField(score);

      switch(aesthetic) {
      case COLOR:
         aref.setVisualFrameWrapper(new GradientColorFrameWrapper());
         cinfo.setColorField(aref);
         break;
      case SIZE:
         aref.setVisualFrameWrapper(new LinearSizeFrameWrapper());
         cinfo.setSizeField(aref);
         break;
      default:
         aref.setVisualFrameWrapper(new FillShapeFrameWrapper());
         cinfo.setShapeField(aref);
      }

      cinfo.updateChartType(false);
      ChartDescriptor desc = new ChartDescriptor();
      GraphFormatUtil.fixDefaultNumberFormat(desc, cinfo);
      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(desc);
      assemblyInfo.setVizMark(modern ? VizMark.MODERN_LIGHT : null);
      assemblyInfo.setViewsheet(new Viewsheet());

      String yrName = GraphUtil.getName(yr);
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "Region", GraphUtil.getName(score), yrName },
         { "A", 10.0, 2024.0 }, { "B", 20.0, 2025.0 }, { "C", 30.0, 2024.0 },
         { "D", 40.0, 2025.0 }, { "E", 50.0, 2024.0 } });
      GraphGenerator gen = GraphGenerator.getGenerator(
         assemblyInfo, null, data, new VariableTable(), null, XSourceInfo.NONE, null);
      EGraph graph = gen.createEGraph();
      VGraph vg = Plotter.getPlotter(graph).plotAndLayout(gen.getData(), 0, 0, 600, 400);
      LegendGroup group = vg.getLegendGroup();
      assertNotNull(group, "premise: the chart has a legend");
      Legend legend = null;

      for(int i = 0; i < group.getLegendCount(); i++) {
         if(yrName.equals(group.getLegend(i).getVisualFrame().getField())) {
            legend = group.getLegend(i);
         }
      }

      assertNotNull(legend, "premise: a legend of " + yrName);
      assertInstanceOf(ExtendedDecimalFormat.class,
                       legend.getVisualFrame().getLegendSpec().getTextSpec().getFormat(),
                       "premise: the K/M/B default format");
      return new Generated(graph, legend);
   }

   private static VSChartAggregateRef aggregate(String column) {
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setDataRef(new BaseField(column));
      ref.setFormulaValue("Sum");
      return ref;
   }
}
