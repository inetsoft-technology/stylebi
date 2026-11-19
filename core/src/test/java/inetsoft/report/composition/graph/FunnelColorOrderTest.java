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
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.util.List;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * When a funnel coloured by its stage takes funnel order, and whether it shares its colours.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FunnelColorOrderTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aMarkedFunnelColouredByItsStage() {
      VSChartInfo info = funnel("Stage", XConstants.SORT_ASC);

      assertSame(info.getRTYFields()[0], FunnelColorOrder.stage(modern(), info));
   }

   @Test
   void aClassicFunnel() {
      assertNull(FunnelColorOrder.stage(classic(), funnel("Stage", XConstants.SORT_ASC)));
      assertNull(FunnelColorOrder.stage(null, funnel("Stage", XConstants.SORT_ASC)));
   }

   @Test
   void aColourFieldOtherThanTheStage() {
      assertNull(FunnelColorOrder.stage(modern(), funnel("Region", XConstants.SORT_ASC)));
   }

   @Test
   void aBarColouredByItsDimension() {
      VSChartInfo info = funnel("Stage", XConstants.SORT_ASC);
      info.setChartType(GraphTypes.CHART_BAR);
      info.setRTChartType(GraphTypes.CHART_BAR);

      assertNull(FunnelColorOrder.stage(modern(), info));
   }

   @Test
   void anAuthorsDescendingOrderWins() {
      assertNull(FunnelColorOrder.stage(modern(), funnel("Stage", XConstants.SORT_DESC)));
   }

   @Test
   void aManualOrderWins() {
      assertNull(FunnelColorOrder.stage(modern(), funnel("Stage", XConstants.SORT_SPECIFIC)));
   }

   @Test
   void topFirstIsTheReverseOfTheStageValueOrder() {
      VSChartInfo info = funnel("Stage", XConstants.SORT_ASC);
      XDimensionRef stage = FunnelColorOrder.stage(modern(), info);
      Object[] vals = { "Renewal", "Trial", "Visit", "Purchase", "Signup" };

      Arrays.sort(vals, FunnelColorOrder.topFirst(stage, data()));

      assertEquals(List.of("Visit", "Signup", "Trial", "Purchase", "Renewal"),
                   Arrays.asList(vals));
   }

   @Test
   void topFirstFollowsTheStageSortTheDataHolds() {
      // the query sorts a copy of the stage; the chart's own ref keeps the default order
      VSChartDimensionRef sorted = dim("Stage");
      sorted.setOrder(XConstants.SORT_VALUE_ASC);
      sorted.setSortByColValue("Sum(Users)");
      DefaultDataSet data = data();
      data.setComparator("Stage", sorted.createComparator(data));
      Object[] vals = { "Renewal", "Trial", "Visit", "Purchase", "Signup" };

      Arrays.sort(vals, FunnelColorOrder.topFirst(dim("Stage"), data));

      assertEquals(List.of("Visit", "Signup", "Trial", "Purchase", "Renewal"),
                   Arrays.asList(vals));
   }

   @Test
   void relistRestoresTheTopStageFirstAfterAReSync() {
      XDimensionRef stage = FunnelColorOrder.stage(modern(), funnel("Stage", XConstants.SORT_ASC));
      DefaultDataSet data = data();
      CategoricalColorFrame frame = reSynced(stage, data);
      Map<Object, Color> colors = colorsOf(frame);
      EGraph graph = new EGraph();
      graph.setLegendInMarkOrder(true);

      FunnelColorOrder.relist(graph, frame, stage, data);

      assertEquals(List.of("Visit", "Signup", "Trial", "Purchase", "Renewal"),
                   Arrays.asList(frame.getValues()));
      assertEquals(colors, colorsOf(frame));
   }

   @Test
   void relistLeavesOtherGraphsAlone() {
      XDimensionRef stage = FunnelColorOrder.stage(modern(), funnel("Stage", XConstants.SORT_ASC));
      DefaultDataSet data = data();
      List<Object> reSynced = Arrays.asList(reSynced(stage, data).getValues());
      EGraph classic = new EGraph();
      EGraph marked = new EGraph();
      marked.setLegendInMarkOrder(true);

      CategoricalColorFrame frame = reSynced(stage, data);
      FunnelColorOrder.relist(classic, frame, stage, data);
      assertEquals(reSynced, Arrays.asList(frame.getValues()), "a graph without mark order");

      frame = reSynced(stage, data);
      FunnelColorOrder.relist(marked, frame, null, data);
      assertEquals(reSynced, Arrays.asList(frame.getValues()), "no funnel stage");
   }

   // a stage colour frame as the script pass leaves it: re-synced in the stage's value order
   private static CategoricalColorFrame reSynced(XDimensionRef stage, DefaultDataSet data) {
      data.setComparator("Stage", stage.createComparator(data));
      CategoricalColorFrame frame = new CategoricalColorFrame("Stage");
      VSFrameVisitor.syncCategoricalFrame(frame, data);
      assertEquals(List.of("Renewal", "Purchase", "Trial", "Signup", "Visit"),
                   Arrays.asList(frame.getValues()), "premise: value ascending");
      return frame;
   }

   private static Map<Object, Color> colorsOf(CategoricalColorFrame frame) {
      Map<Object, Color> colors = new HashMap<>();

      for(Object value : frame.getValues()) {
         colors.put(value, frame.getColor(value));
      }

      return colors;
   }

   @Test
   void aPartnerChartIsNotStandalone() {
      Viewsheet vs = new Viewsheet();
      VSChartInfo info = place(vs, "Funnel", funnel("Stage", XConstants.SORT_ASC));
      place(vs, "Bar", bar("Stage", true));

      assertFalse(FunnelColorOrder.standalone(info, vs, "Funnel", XSourceInfo.NONE));
   }

   @Test
   void aChartOnAnotherColumnIsNoPartner() {
      Viewsheet vs = new Viewsheet();
      VSChartInfo info = place(vs, "Funnel", funnel("Stage", XConstants.SORT_ASC));
      place(vs, "Bar", bar("Region", true));

      assertTrue(FunnelColorOrder.standalone(info, vs, "Funnel", XSourceInfo.NONE));
   }

   @Test
   void aPartnerWithShareColorsOffDoesNotCount() {
      Viewsheet vs = new Viewsheet();
      VSChartInfo info = place(vs, "Funnel", funnel("Stage", XConstants.SORT_ASC));
      place(vs, "Bar", bar("Stage", false));

      assertTrue(FunnelColorOrder.standalone(info, vs, "Funnel", XSourceInfo.NONE));
   }

   @Test
   void ownShareColorsOffIsStandalone() {
      Viewsheet vs = new Viewsheet();
      VSChartInfo info = place(vs, "Funnel", funnel("Stage", XConstants.SORT_ASC));
      place(vs, "Bar", bar("Stage", true));
      ((CategoricalColorFrame) info.getColorField().getVisualFrame()).setShareColors(false);

      assertTrue(FunnelColorOrder.standalone(info, vs, "Funnel", XSourceInfo.NONE));
   }

   @Test
   void noViewsheetIsStandalone() {
      assertTrue(FunnelColorOrder.standalone(funnel("Stage", XConstants.SORT_ASC), null,
                                             "Funnel", XSourceInfo.NONE));
   }

   @Test
   void aRelationNodeColourPartnerCounts() {
      Viewsheet vs = new Viewsheet();
      VSChartInfo info = place(vs, "Funnel", funnel("Stage", XConstants.SORT_ASC));
      RelationVSChartInfo tree = new RelationVSChartInfo();
      tree.setNodeColorField(color("Stage", XConstants.SORT_ASC));
      place(vs, "Tree", tree);

      assertFalse(FunnelColorOrder.standalone(info, vs, "Funnel", XSourceInfo.NONE));
   }

   // the stage on Y, sorted as applyFunnelSort sorts it, and a colour field with its own order
   static VSChartInfo funnel(String colorField, int colorOrder) {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_FUNNEL);
      info.setRTChartType(GraphTypes.CHART_FUNNEL);
      info.addXField(agg("Users"));
      VSChartDimensionRef stage = dim("Stage");
      stage.setOrder(XConstants.SORT_VALUE_ASC);
      stage.setSortByColValue("Sum(Users)");
      info.addYField(stage);
      info.setColorField(color(colorField, colorOrder));
      return info;
   }

   private static VSChartInfo bar(String colorField, boolean share) {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      info.setRTChartType(GraphTypes.CHART_BAR);
      info.addXField(dim("Region"));
      info.addYField(agg("Users"));
      info.setColorField(color(colorField, XConstants.SORT_ASC));
      ((CategoricalColorFrame) info.getColorField().getVisualFrame()).setShareColors(share);
      return info;
   }

   private static VSAestheticRef color(String field, int order) {
      VSChartDimensionRef ref = dim(field);
      ref.setOrder(order);
      VSAestheticRef color = new VSAestheticRef();
      color.setALLDataRef(ref);
      color.setVisualFrameWrapper(new CategoricalColorFrameWrapper());
      return color;
   }

   private static VSChartInfo place(Viewsheet vs, String name, VSChartInfo info) {
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      chart.setVSChartInfo(info);
      vs.addAssembly(chart);
      return info;
   }

   static DefaultDataSet data() {
      return new DefaultDataSet(new Object[][] {
         { "Stage", "Sum(Users)" },
         { "Purchase", 200 },
         { "Renewal", 100 },
         { "Signup", 600 },
         { "Trial", 400 },
         { "Visit", 1000 },
      });
   }

   static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new BaseField(name));
      dim.setGroupColumnValue(name);
      return dim;
   }

   static VSChartAggregateRef agg(String name) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new BaseField(name));
      agg.setFormulaValue("Sum");
      return agg;
   }

   private static VizContext modern() {
      return VizContext.of(VizMark.MODERN_LIGHT);
   }

   private static VizContext classic() {
      return VizContext.of((VizMark) null);
   }
}
