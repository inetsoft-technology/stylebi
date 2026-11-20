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
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.RelationElement;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.HeatColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.awt.Dimension;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The box tint a marked tree chart hands its relation element.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernTreeBoxTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aMarkedTreeIsTinted() {
      RelationElement light = element(GraphTypes.CHART_TREE, VizMark.MODERN_LIGHT, c -> { });
      assertEquals(0.15, light.getBoxTintAlpha(), 1e-9);
      assertEquals(new Color(0xECEBE9), light.getBoxTintBackdrop());
      assertEquals(new Color(0x35342F), light.getBoxInk());

      RelationElement dark = element(GraphTypes.CHART_TREE, VizMark.MODERN_DARK, c -> { });
      assertEquals(0.15, dark.getBoxTintAlpha(), 1e-9);
      assertEquals(new Color(0x313034), dark.getBoxTintBackdrop());
      assertEquals(new Color(0xE6E0E9), dark.getBoxInk());
   }

   @Test
   void anAuthorsTextColourWins() {
      RelationElement elem = element(GraphTypes.CHART_TREE, VizMark.MODERN_LIGHT, c ->
         c.getTargetField().getTextFormat().getUserDefinedFormat().setColor(Color.RED));

      assertEquals(0.15, elem.getBoxTintAlpha(), 1e-9);
      assertNull(elem.getBoxInk());
   }

   @Test
   void aMeasureOnNodeColourStaysSolid() {
      RelationElement elem = element(GraphTypes.CHART_TREE, VizMark.MODERN_LIGHT, c -> {
         VSAestheticRef color = new VSAestheticRef();
         VSChartAggregateRef qty = agg("Qty", "Sum");
         color.setDataRef(qty);
         color.setRTDataRef(qty);
         color.setVisualFrameWrapper(new HeatColorFrameWrapper());
         c.setNodeColorField(color);
      });

      assertEquals(0, elem.getBoxTintAlpha(), 1e-9);
   }

   @Test
   void networkAndCircularStaySolid() {
      for(int type : new int[] { GraphTypes.CHART_NETWORK, GraphTypes.CHART_CIRCULAR }) {
         RelationElement elem = element(type, VizMark.MODERN_LIGHT, c -> { });

         assertEquals(0, elem.getBoxTintAlpha(), 1e-9, "chart type " + type);
         assertNull(elem.getBoxInk(), "chart type " + type);
      }
   }

   @Test
   void classicTreesAreUnchanged() {
      RelationElement elem = element(GraphTypes.CHART_TREE, null, c -> { });

      assertEquals(0, elem.getBoxTintAlpha(), 1e-9);
      assertNull(elem.getBoxInk());
   }

   @Test
   void aBrushedTreeIsNotTinted() {
      for(Brush brush : new Brush[] { Brush.SOURCE, Brush.TARGET }) {
         RelationElement elem = element(GraphTypes.CHART_TREE, VizMark.MODERN_LIGHT, c -> { },
                                        brush);

         assertEquals(0, elem.getBoxTintAlpha(), 1e-9, brush.name());
         assertNull(elem.getBoxInk(), brush.name());
      }
   }

   @Test
   void anUnbrushedMarkedTreeKeepsTheTint() {
      assertEquals(0.15, element(GraphTypes.CHART_TREE, VizMark.MODERN_LIGHT, c -> { },
                                 Brush.NONE).getBoxTintAlpha(), 1e-9);
   }

   private enum Brush { NONE, SOURCE, TARGET }

   private static RelationElement element(int chartType, VizMark mark,
                                          Consumer<RelationVSChartInfo> setup)
   {
      return element(chartType, mark, setup, Brush.NONE);
   }

   private static RelationElement element(int chartType, VizMark mark,
                                          Consumer<RelationVSChartInfo> setup, Brush brush)
   {
      VSChartDimensionRef source = dim("Parent");
      VSChartDimensionRef target = dim("Child");
      VSChartAggregateRef qty = agg("Qty", "Sum");
      Object[][] rows = {
         { "Parent", "Child", GraphUtil.getName(qty) },
         { "A", "B", 3.0 }, { "A", "C", 7.0 }, { "B", "D", 11.0 } };
      RelationVSChartInfo cinfo = new RelationVSChartInfo();
      cinfo.setChartType(chartType);
      cinfo.setRTChartType(chartType);
      cinfo.setSourceField(source);
      cinfo.setTargetField(target);
      cinfo.setRTSourceField(source);
      cinfo.setRTTargetField(target);
      cinfo.updateChartType(false);
      setup.accept(cinfo);

      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);

      if(brush == Brush.SOURCE) {
         VSPoint point = new VSPoint();
         point.addValue(new VSFieldValue("Parent", "A"));
         VSSelection selection = new VSSelection();
         selection.addPoint(point);
         assemblyInfo.setBrushSelection(selection);
      }

      Object[][] brushed = { rows[0], rows[1] };
      EGraph graph = GraphGenerator.getGenerator(
         assemblyInfo, brush == Brush.TARGET ? new DefaultDataSet(rows) : null,
         new DefaultDataSet(brush == Brush.TARGET ? brushed : rows), new VariableTable(), null,
         XSourceInfo.NONE, new Dimension(400, 300)).createEGraph();

      for(int i = 0; i < graph.getElementCount(); i++) {
         if(graph.getElement(i) instanceof RelationElement elem) {
            return elem;
         }
      }

      fail("premise: a relation element");
      return null;
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new BaseField(name));
      dim.setGroupColumnValue(name);
      return dim;
   }

   private static VSChartAggregateRef agg(String name, String formula) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new BaseField(name));
      agg.setFormulaValue(formula);
      return agg;
   }
}
