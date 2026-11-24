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
import inetsoft.graph.element.RelationElement;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.legend.Legend;
import inetsoft.graph.guide.legend.LegendGroup;
import inetsoft.graph.guide.legend.LegendItem;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A marked relation chart cuts its labels with "…" at the axis label limit, and a marked chart's
 * legend cuts its entries with "…". A classic chart turns neither on.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernLabelCutRulesTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aMarkedRelationChartCutsWithTheEllipsisAtTheLimit() {
      for(int type : RELATION_TYPES) {
         RelationElement relation = relation(type, VizMark.MODERN_LIGHT);

         assertTrue(relation.isLabelEllipsis(), "type " + type);
         assertEquals(ModernLabelRule.LIMIT_PX, relation.getLabelLimit(), "type " + type);
      }
   }

   @Test
   void aClassicRelationChartDoesNot() {
      for(int type : RELATION_TYPES) {
         RelationElement relation = relation(type, null);

         assertFalse(relation.isLabelEllipsis(), "type " + type);
         assertEquals(0, relation.getLabelLimit(), "type " + type);
      }
   }

   @Test
   void aMarkedLegendCutsWithTheEllipsis() {
      assertTrue(pie(VizMark.MODERN_LIGHT).isLegendEllipsis());
      assertFalse(pie(null).isLegendEllipsis());
   }

   @Test
   void aMarkedPieLegendEntryEndsInTheEllipsis() {
      List<String[]> entries = legendEntries(VizMark.MODERN_LIGHT);
      assertTrue(entries.stream().anyMatch(e -> !e[0].equals(e[1])),
                 "premise: a cut entry " + text(entries));

      for(String[] entry : entries) {
         assertTrue(entry[0].equals(entry[1]) || entry[1].endsWith("…"), text(entries));
      }
   }

   private static final int[] RELATION_TYPES = {
      GraphTypes.CHART_TREE, GraphTypes.CHART_NETWORK, GraphTypes.CHART_CIRCULAR };

   private static RelationElement relation(int chartType, VizMark mark) {
      VSChartDimensionRef source = dim("RMgr");
      VSChartDimensionRef target = dim("REmp");
      RelationVSChartInfo cinfo = new RelationVSChartInfo();
      cinfo.setChartType(chartType);
      cinfo.setRTChartType(chartType);
      cinfo.setSourceField(source);
      cinfo.setTargetField(target);
      cinfo.setRTSourceField(source);
      cinfo.setRTTargetField(target);
      cinfo.updateChartType(false);
      Object[][] rows = {
         { "RMgr", "REmp" },
         { "CEO", "Senior Director of Operations" },
         { "CEO", "Chief Financial Officer" },
         { "Chief Financial Officer", "Controller" } };
      EGraph graph = generator(cinfo, rows, mark).createEGraph();

      for(int i = 0; i < graph.getElementCount(); i++) {
         if(graph.getElement(i) instanceof RelationElement elem) {
            return elem;
         }
      }

      return fail("premise: a relation element");
   }

   private static EGraph pie(VizMark mark) {
      return pieGenerator(mark).createEGraph();
   }

   // each legend entry's name and the text it draws, on a 420 x 280 pie
   private static List<String[]> legendEntries(VizMark mark) {
      GraphGenerator gen = pieGenerator(mark);
      VGraph vgraph = Plotter.getPlotter(gen.createEGraph())
         .plotAndLayout(gen.getData(), 0, 0, 420, 280);
      LegendGroup group = vgraph.getLegendGroup();
      assertNotNull(group, "premise: a legend");
      List<String[]> entries = new ArrayList<>();

      for(int i = 0; i < group.getLegendCount(); i++) {
         Legend legend = group.getLegend(i);

         for(int j = 0; j < legend.getVisualCount(); j++) {
            if(legend.getVisual(j) instanceof LegendItem item) {
               for(int k = 0; k < item.getVisualCount(); k++) {
                  if(item.getVisual(k) instanceof VLabel label) {
                     entries.add(new String[] {
                        item.getLabel(), String.join(" ", label.getDisplayLabel()) });
                  }
               }
            }
         }
      }

      return entries;
   }

   private static String text(List<String[]> entries) {
      return entries.stream().map(e -> e[0] + " -> " + e[1]).toList().toString();
   }

   // Sum(HSales) by HName on colour
   private static GraphGenerator pieGenerator(VizMark mark) {
      VSChartInfo pie = new DefaultVSChartInfo();
      VSChartAggregateRef sales = agg("HSales");
      VSChartDimensionRef name = dim("HName");
      pie.addYField(sales);
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(name);
      color.setRTDataRef(name);
      color.setVisualFrameWrapper(new CategoricalColorFrameWrapper());
      pie.setColorField(color);
      pie.setChartType(GraphTypes.CHART_PIE);
      pie.setRTChartType(GraphTypes.CHART_PIE);
      pie.updateChartType(false);
      Object[][] rows = {
         { "HName", GraphUtil.getName(sales) },
         { "Accounts", 30.0 },
         { "Field Services", 28.0 },
         { "Customer Success Team", 26.0 },
         { "Regional Distribution Partners", 24.0 },
         { "Healthcare Provider Services", 22.0 },
         { "Authorised Reseller Network", 20.0 } };
      return generator(pie, rows, mark);
   }

   private static GraphGenerator generator(VSChartInfo cinfo, Object[][] rows, VizMark mark) {
      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);
      assemblyInfo.setViewsheet(new Viewsheet());
      return GraphGenerator.getGenerator(assemblyInfo, null, new DefaultDataSet(rows),
                                         new VariableTable(), null, XSourceInfo.NONE, null);
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new BaseField(name));
      dim.setGroupColumnValue(name);
      return dim;
   }

   private static VSChartAggregateRef agg(String name) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      BaseField field = new BaseField(name);
      field.setDataType(XSchema.DOUBLE);
      agg.setDataRef(field);
      agg.setFormulaValue("Sum");
      return agg;
   }
}
