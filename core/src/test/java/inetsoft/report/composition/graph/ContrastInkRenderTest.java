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
import inetsoft.graph.internal.GTool;
import inetsoft.graph.visual.ElementVO;
import inetsoft.graph.visual.RelationVO;
import inetsoft.graph.visual.TreemapVO;
import inetsoft.graph.visual.VOText;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.ColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.LinearSizeFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.uql.viewsheet.internal.VizModernizeUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A label drawn on a mid-tone fill takes the higher-contrast ink on a marked chart, and today's
 * white-preferring ink on a classic one.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ContrastInkRenderTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void theFillSplitsTheTwoInks() {
      assertEquals(Color.WHITE, GTool.getTextColor(FILL), "classic ink");
      assertEquals(Color.BLACK, GTool.getTextColor(FILL, true), "contrast ink");
   }

   @Test
   void aMarkedTreemapCellLabelTurnsBlack() {
      VOText label = label(TreemapVO.class, treemap(), VizMark.MODERN_LIGHT, TREEMAP_ROWS);

      assertTrue(label.isContrastInk());
      assertEquals(Color.BLACK, label.getColor());
   }

   @Test
   void aClassicTreemapCellLabelStaysWhite() {
      VOText label = label(TreemapVO.class, treemap(), null, TREEMAP_ROWS);

      assertFalse(label.isContrastInk());
      assertEquals(Color.WHITE, label.getColor());
   }

   // a marked tree tints its boxes, and their labels take the title ink, not the contrast ink
   @Test
   void aMarkedTreeNodeLabelTakesTheTitleInk() {
      VOText label = firstLabel(RelationVO.class,
                                plot(relation(GraphTypes.CHART_TREE), VizMark.MODERN_LIGHT,
                                     TREE_ROWS));

      assertNull(label.getAutoBackground());
      assertEquals(TITLE_INK, label.getColor());
   }

   @Test
   void aClassicTreeNodeLabelStaysWhite() {
      VOText label = label(RelationVO.class, relation(GraphTypes.CHART_TREE), null, TREE_ROWS);

      assertFalse(label.isContrastInk());
      assertEquals(Color.WHITE, label.getColor());
   }

   // the first label a VO of the type draws on the fill
   private static VOText label(Class<? extends ElementVO> type, VSChartInfo cinfo, VizMark mark,
                               Object[][] rows)
   {
      VOText label = firstLabel(type, plot(cinfo, mark, rows));
      assertEquals(FILL, label.getAutoBackground(), "premise: the label reads the fill");
      return label;
   }

   // the first label a VO of the type draws
   private static VOText firstLabel(Class<? extends ElementVO> type, VGraph vgraph) {
      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(type.isInstance(vgraph.getVisual(i)) &&
            ((ElementVO) vgraph.getVisual(i)).getVOText() != null)
         {
            return ((ElementVO) vgraph.getVisual(i)).getVOText();
         }
      }

      return fail("premise: a " + type.getSimpleName() + " draws a label");
   }

   private static VGraph plot(VSChartInfo cinfo, VizMark mark, Object[][] rows) {
      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);
      VizModernizeUtil.reseedAfterRestore(assemblyInfo);

      // the author's fill, set after the seed so both charts draw it
      VSChartInfo info = assemblyInfo.getVSChartInfo();
      ColorFrameWrapper fill = info instanceof RelationVSChartInfo relation ?
         relation.getNodeColorFrameWrapper() : info.getColorFrameWrapper();
      ((StaticColorFrameWrapper) fill).setUserColor(FILL);

      GraphGenerator gen = GraphGenerator.getGenerator(
         assemblyInfo, null, new DefaultDataSet(rows), new VariableTable(), null,
         XSourceInfo.NONE, null);
      EGraph graph = gen.createEGraph();
      return Plotter.getPlotter(graph).plot(gen.getData());
   }

   // Division then Department, sized by Sum(Total)
   private static VSChartInfo treemap() {
      VSAestheticRef size = new VSAestheticRef();
      size.setDataRef(agg("Total"));
      size.setVisualFrameWrapper(new LinearSizeFrameWrapper());

      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_TREEMAP);
      cinfo.setRTChartType(GraphTypes.CHART_TREEMAP);
      cinfo.addGroupField(dim("Division"));
      cinfo.addGroupField(dim("Department"));
      cinfo.setSizeField(size);
      cinfo.updateChartType(false);
      return cinfo;
   }

   private static VSChartInfo relation(int type) {
      VSChartDimensionRef parent = dim("Parent");
      VSChartDimensionRef child = dim("Child");

      RelationVSChartInfo cinfo = new RelationVSChartInfo();
      cinfo.setChartType(type);
      cinfo.setRTChartType(type);
      cinfo.setSourceField(parent);
      cinfo.setTargetField(child);
      cinfo.setRTSourceField(parent);
      cinfo.setRTTargetField(child);
      cinfo.updateChartType(false);
      return cinfo;
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

   // a mid-tone fill: white under the classic rule, black under the contrast rule
   private static final Color FILL = new Color(0xE5197E);
   // the light chart title colour
   private static final Color TITLE_INK = new Color(0x35342F);
   private static final Object[][] TREEMAP_ROWS = {
      { "Division", "Department", "Sum(Total)" },
      { "East", "Sales", 100.0 },
      { "East", "Support", 200.0 },
      { "West", "Sales", 300.0 },
      { "West", "Support", 150.0 },
   };
   private static final Object[][] TREE_ROWS = {
      { "Parent", "Child" },
      { "A", "B" },
      { "A", "C" },
      { "B", "D" },
   };
}
