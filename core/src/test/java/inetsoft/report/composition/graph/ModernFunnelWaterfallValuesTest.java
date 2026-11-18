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
import inetsoft.graph.GraphConstants;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.geometry.ElementGeometry;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.visual.VOText;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.filter.TextHighlight;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.DefaultTextFrameWrapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A marked funnel or waterfall centres its values in the marks, shows them where they fit, and
 * colours them by the fill; a classic chart and a chart with a Text field draw as before.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernFunnelWaterfallValuesTest {
   private static final Color UP = new Color(0x1C857B);
   private static final Color UP_DARK = new Color(0x3FBFB2);
   private static final Color AUTHOR = new Color(0x00AA00);
   private static final Color HIGHLIGHT = new Color(0xFF00FF);

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void waterfallValuesSitInsideTheirBars() {
      Render r = render(waterfall(), VizMark.MODERN_LIGHT, a -> { });

      for(GraphElement elem : List.of(bar(r), total(r))) {
         assertEquals(GraphConstants.CENTER, elem.getLabelPlacement());
         assertTrue(elem.isValueFitInside());
         assertTrue(elem.isAutoTextColor());
      }
   }

   @Test
   void funnelValuesFitTheirStages() {
      Render r = render(funnel(), VizMark.MODERN_LIGHT, a -> { });
      GraphElement elem = r.graph.getElement(0);

      assertEquals(GraphConstants.CENTER, elem.getLabelPlacement());
      assertTrue(elem.isValueFitInside());
      assertTrue(elem.isAutoTextColor());
   }

   @Test
   void directionFillsTakeWhiteInLightAndBlackInDark() {
      List<VOText> light = shownTexts(render(waterfall(), VizMark.MODERN_LIGHT, a -> { }));
      assertFalse(light.isEmpty(), "premise: a value shows");
      assertColours(light);
      assertTrue(light.stream().anyMatch(t -> UP.equals(fill(t))),
                 "premise: a label on the up fill");
      light.stream().filter(t -> UP.equals(fill(t)))
         .forEach(t -> assertEquals(Color.WHITE, t.getColor()));

      List<VOText> dark = shownTexts(render(waterfall(), VizMark.MODERN_DARK, a -> { }));
      assertColours(dark);
      assertTrue(dark.stream().anyMatch(t -> UP_DARK.equals(fill(t))),
                 "premise: a label on the dark up fill");
      dark.stream().filter(t -> UP_DARK.equals(fill(t)))
         .forEach(t -> assertEquals(Color.BLACK, t.getColor()));
   }

   @Test
   void anAuthorsTextColourWins() {
      Render r = render(waterfall(), VizMark.MODERN_LIGHT, a -> {
         VSChartAggregateRef agg = (VSChartAggregateRef) a.getVSChartInfo().getAggregateRefs()[0];
         agg.getTextFormat().getUserDefinedFormat().setColor(AUTHOR);
      });

      assertFalse(bar(r).isAutoTextColor());
      assertFalse(total(r).isAutoTextColor());
      assertTrue(bar(r).isValueFitInside());
      assertEquals(AUTHOR, shownTexts(r).get(0).getColor());
   }

   @Test
   void aHighlightColourWins() {
      Render r = render(waterfall(), VizMark.MODERN_LIGHT, a -> {
         VSChartAggregateRef agg = (VSChartAggregateRef) a.getVSChartInfo().getAggregateRefs()[0];
         HighlightGroup group = new HighlightGroup();
         group.addHighlight("big", highlight(GraphUtil.getName(agg)));
         agg.setTextHighlightGroup(group);
      });
      List<VOText> texts = shownTexts(r);

      assertTrue(texts.stream().anyMatch(t -> HIGHLIGHT.equals(t.getColor())),
                 "the highlighted label keeps its colour");
      assertTrue(texts.stream().anyMatch(t -> !HIGHLIGHT.equals(t.getColor())),
                 "an unhighlighted label still follows its fill");
      texts.stream().filter(t -> !HIGHLIGHT.equals(t.getColor()))
         .forEach(t -> assertEquals(GTool.getTextColor(fill(t)), t.getColor()));
   }

   @Test
   void textLabelsKeepTodaysPlacement() {
      Render r = render(waterfall(), VizMark.MODERN_LIGHT, a -> {
         VSChartDimensionRef state = dim("State");
         VSAestheticRef text = new VSAestheticRef();
         text.setDataRef(state);
         text.setRTDataRef(state);
         text.setVisualFrameWrapper(new DefaultTextFrameWrapper());
         a.getVSChartInfo().setTextField(text);
      });

      for(GraphElement elem : List.of(bar(r), total(r))) {
         assertEquals(GraphConstants.AUTO, elem.getLabelPlacement());
         assertFalse(elem.isValueFitInside());
         assertFalse(elem.isAutoTextColor());
      }
   }

   @Test
   void classicChartsAreUnchanged() {
      Render w = render(waterfall(), null, a -> { });

      for(GraphElement elem : List.of(bar(w), total(w))) {
         assertEquals(GraphConstants.AUTO, elem.getLabelPlacement());
         assertFalse(elem.isValueFitInside());
         assertFalse(elem.isAutoTextColor());
      }

      Render f = render(funnel(), null, a -> { });
      assertFalse(f.graph.getElement(0).isValueFitInside());
      assertFalse(f.graph.getElement(0).isAutoTextColor());
   }

   // ---- harness -----------------------------------------------------------------------------

   private record Render(GraphGenerator gen, EGraph graph) {
   }

   private static void assertColours(List<VOText> texts) {
      for(VOText text : texts) {
         assertEquals(GTool.getTextColor(fill(text)), text.getColor());
      }
   }

   private static Color fill(VOText text) {
      return ((ElementGeometry) text.getElementVO().getGeometry()).getColor(0);
   }

   // the value labels left visible after layout
   private static List<VOText> shownTexts(Render r) {
      DataSet data = r.gen.getData();
      VGraph vgraph = Plotter.getPlotter(r.graph).plotAndLayout(data, 0, 0, 600, 400);
      List<VOText> texts = new ArrayList<>();

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof VOText text && text.getElementVO() != null &&
            text.getZIndex() >= 0)
         {
            texts.add(text);
         }
      }

      return texts;
   }

   private static GraphElement bar(Render r) {
      return element(r, e -> !"true".equals(e.getHint("_waterfall_")));
   }

   private static GraphElement total(Render r) {
      return element(r, e -> "true".equals(e.getHint("_waterfall_")));
   }

   private static GraphElement element(Render r, Predicate<GraphElement> pick) {
      for(int i = 0; i < r.graph.getElementCount(); i++) {
         if(pick.test(r.graph.getElement(i))) {
            return r.graph.getElement(i);
         }
      }

      return fail("premise: the graph draws the element");
   }

   private static VSChartInfo waterfall() {
      return chart(GraphTypes.CHART_WATERFALL);
   }

   private static VSChartInfo funnel() {
      return chart(GraphTypes.CHART_FUNNEL);
   }

   private static VSChartInfo chart(int type) {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.addXField(dim("State"));
      info.addYField(agg("V"));
      info.setChartType(type);
      info.setRTChartType(type);
      info.updateChartType(false);
      return info;
   }

   private static Render render(VSChartInfo cinfo, VizMark mark,
                                Consumer<ChartVSAssemblyInfo> setup)
   {
      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);
      VizModernizeUtil.reseedAfterRestore(assemblyInfo);
      assemblyInfo.getChartDescriptor().getPlotDescriptor().setValuesVisible(true);
      setup.accept(assemblyInfo);
      String value = GraphUtil.getName(cinfo.getAggregateRefs()[0]);
      Object[][] rows = { { "State", value }, { "A", 500.0 }, { "B", -300.0 }, { "C", 400.0 } };
      GraphGenerator gen = GraphGenerator.getGenerator(
         assemblyInfo, null, new DefaultDataSet(rows), new VariableTable(), null,
         XSourceInfo.NONE, null);
      return new Render(gen, gen.createEGraph());
   }

   private static TextHighlight highlight(String column) {
      TextHighlight h = new TextHighlight();
      h.setName("big");
      h.setForeground(HIGHLIGHT);
      AssetCondition c = new AssetCondition(XSchema.DOUBLE);
      c.setOperation(XCondition.GREATER_THAN);
      c.addValue(450.0);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new ColumnRef(new AttributeRef(null, column)), c, 0));
      h.setConditionGroup(list);
      return h;
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
}
