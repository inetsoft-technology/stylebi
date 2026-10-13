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
import inetsoft.graph.aesthetic.CategoricalShapeFrame;
import inetsoft.graph.aesthetic.GShape;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.PointElement;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalShapeFrameWrapper;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TipVSAssemblyInfo;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78462: shape frames shared across a viewsheet (#74067) must only be shared between
 * bindings of the same column that have the same shape palette, and a shared frame must not
 * replace the palette of the chart's design time shape binding.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("slow")
@Tag("integration")
class SharedShapeFrameViewsheetTest {
   @BeforeEach
   void setUp() {
      box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      vs = box.getViewsheet();
      vs.clearSharedFrames();
   }

   @Test
   void chartsWithDifferentPalettesKeepTheirOwnShapes() throws Exception {
      for(String[] palettes : new String[][] { { NULL, GOOD }, { GOOD, NULL },
                                               { OTHER, GOOD }, { GOOD, OTHER } })
      {
         vs.clearSharedFrames();
         ChartVSAssembly first = addChart("First" + vs.getAssemblies().length, palettes[0]);
         ChartVSAssembly second = addChart("Second" + vs.getAssemblies().length, palettes[1]);
         String order = palettes[0] + " then " + palettes[1];

         assertEquals(expected(palettes[0]), shapes(first, null), "first chart, " + order);
         assertEquals(expected(palettes[1]), shapes(second, null), "second chart, " + order);
         // render again in the other order with the frames of both charts already shared
         assertEquals(expected(palettes[0]), shapes(first, null), "first chart again, " + order);
      }
   }

   @Test
   void renderDoesNotOverwriteTheDesignPalette() throws Exception {
      ChartVSAssembly nullChart = addChart("NullChart", NULL);
      ChartVSAssembly goodChart = addChart("GoodChart", GOOD);
      String goodXml = paletteXml(goodChart);
      String nullXml = paletteXml(nullChart);

      shapes(nullChart, null);
      shapes(goodChart, null);
      shapes(nullChart, null);

      assertEquals(goodXml, paletteXml(goodChart), "design palette of the good chart");
      assertEquals(nullXml, paletteXml(nullChart), "design palette of the null chart");
      assertTrue(designWrapper(nullChart).isChanged(), "changed flag of the null chart");
      assertTrue(designWrapper(goodChart).isChanged(), "changed flag of the good chart");
   }

   @Test
   void zoomedChartKeepsTheShapesOfItsFullData() throws Exception {
      ChartVSAssembly chart = addChart("ZoomChart", OTHER);
      Map<String, GShape> full = shapes(chart, null);
      assertEquals(expected(OTHER), full);

      VSSelection zoom = new VSSelection();
      VSPoint point = new VSPoint();
      point.addValue(new VSFieldValue("type", "UNNAMED"));
      zoom.addPoint(point);
      chart.setZoomSelection(zoom);

      // without the shared frame UNNAMED would be the first shape of the palette (74067)
      assertEquals(full.get("UNNAMED"), shapes(chart, null).get("UNNAMED"));
   }

   @Test
   void tipViewWithTheSamePaletteBorrowsTheParentShapes() throws Exception {
      ChartVSAssembly parent = addChart("ParentChart", OTHER);
      ChartVSAssembly tip = addChart("TipChart", OTHER);
      setTipView(parent, tip);
      Map<String, GShape> full = shapes(parent, null);
      assertEquals(expected(OTHER), full);

      tip.setTipConditionList(unnamedCondition());
      assertEquals(full.get("UNNAMED"), shapes(tip, null).get("UNNAMED"));
   }

   @Test
   void tipViewWithADifferentPaletteBorrowsTheParentShapes() throws Exception {
      ChartVSAssembly parent = addChart("CustomParent", OTHER);
      ChartVSAssembly tip = addChart("DefaultTip", DEFAULT);
      setTipView(parent, tip);
      Map<String, GShape> full = shapes(parent, null);
      assertEquals(expected(OTHER), full);

      // the tip view only renders filtered data, its own palette would give UNNAMED the first
      // shape of the palette
      tip.setTipConditionList(unnamedCondition());
      assertEquals(full.get("UNNAMED"), shapes(tip, null).get("UNNAMED"));
   }

   @Test
   void tipViewBorrowsItsParentNotTheLastChartOnTheColumn() throws Exception {
      ChartVSAssembly parent = addChart("TipParent", OTHER);
      ChartVSAssembly tip = addChart("ParentTip", DEFAULT);
      ChartVSAssembly other = addChart("UnrelatedNullChart", NULL);
      setTipView(parent, tip);
      tip.setTipConditionList(unnamedCondition());

      // the unrelated chart on the same column renders before and after the parent
      assertEquals(expected(NULL), shapes(other, null));
      Map<String, GShape> full = shapes(parent, null);
      assertEquals(expected(OTHER), full);
      assertEquals(full.get("UNNAMED"), shapes(tip, null).get("UNNAMED"));

      assertEquals(expected(NULL), shapes(other, null));
      assertEquals(full.get("UNNAMED"), shapes(tip, null).get("UNNAMED"));
   }

   @Test
   void tipViewInAContainerBorrowsTheParentShapes() throws Exception {
      ChartVSAssembly parent = addChart("GroupTipParent", OTHER);
      ChartVSAssembly tip = addChart("GroupedTip", DEFAULT);
      GroupContainerVSAssembly group = new GroupContainerVSAssembly(vs, "TipGroup");
      group.setAssemblies(new String[] { tip.getName() });
      vs.addAssembly(group);
      // the data tip view is the container of the chart
      setTipView(parent, group.getName());
      Map<String, GShape> full = shapes(parent, null);
      assertEquals(expected(OTHER), full);

      tip.setTipConditionList(unnamedCondition());
      assertEquals(full.get("UNNAMED"), shapes(tip, null).get("UNNAMED"));
   }

   @Test
   void chartFilteredLikeATipViewWithoutAParentKeepsItsOwnPalette() throws Exception {
      ChartVSAssembly chart = addChart("OtherChart", OTHER);
      ChartVSAssembly filtered = addChart("NoParentChart", DEFAULT);
      shapes(chart, null);

      // not a tip view of any chart, so it doesn't borrow a frame with another palette
      filtered.setTipConditionList(unnamedCondition());
      assertEquals(expected(DEFAULT).get("NAMED"), shapes(filtered, null).get("UNNAMED"));
   }

   @Test
   void flyoverTargetWithADifferentPaletteKeepsItsOwnShapes() throws Exception {
      ChartVSAssembly source = addChart("FlyoverSource", OTHER);
      ChartVSAssembly target = addChart("FlyoverTarget", GOOD);
      assertEquals(expected(OTHER), shapes(source, null));
      Map<String, GShape> full = shapes(target, null);
      assertEquals(expected(GOOD), full);

      target.setTipConditionList(unnamedCondition());
      assertEquals(full.get("UNNAMED"), shapes(target, null).get("UNNAMED"));
   }

   @Test
   void filledAndHollowPalettesAreNotShared() throws Exception {
      ChartVSAssembly hollow = addChart("HollowChart", GOOD);
      ChartVSAssembly filled = addChart("FilledChart", FILLED);
      assertNotEquals(expected(GOOD), expected(FILLED));

      assertEquals(expected(GOOD), shapes(hollow, null));
      assertEquals(expected(FILLED), shapes(filled, null));
      assertEquals(expected(GOOD), shapes(hollow, null));
   }

   @Test
   void measuresWithDifferentPalettesKeepTheirOwnShapes() throws Exception {
      ChartVSAssembly chart = newChart("MultiChart");
      VSChartInfo info = chart.getVSChartInfo();
      info.setMultiStyles(true);
      info.addYField(measure("Sum", NULL));
      info.addYField(measure("Max", GOOD));
      vs.addAssembly(chart);

      assertEquals(expected(NULL), shapes(chart, "Sum(wind)"));
      assertEquals(expected(GOOD), shapes(chart, "Max(wind)"));
      assertEquals(expected(NULL), shapes(chart, "Sum(wind)"));
   }

   private ChartVSAssembly addChart(String name, String palette) {
      ChartVSAssembly chart = newChart(name);
      VSChartInfo info = chart.getVSChartInfo();
      info.setChartType(GraphTypes.CHART_POINT);
      info.addYField(measure("Sum", null));
      info.setShapeField(shapeRef(palette));
      vs.addAssembly(chart);
      return chart;
   }

   private ChartVSAssembly newChart(String name) {
      SourceInfo source =
         (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      chart.setSourceInfo(source);
      VSChartDimensionRef dim =
         new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, "type")));
      dim.setGroupColumnValue("type");
      chart.getVSChartInfo().addXField(dim);
      return chart;
   }

   private VSChartAggregateRef measure(String formula, String palette) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue(formula);

      if(palette != null) {
         agg.setChartType(GraphTypes.CHART_POINT);
         agg.setShapeField(shapeRef(palette));
      }

      return agg;
   }

   private static VSAestheticRef shapeRef(String palette) {
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setGroupColumnValue("type");
      VSAestheticRef shape = new VSAestheticRef();
      shape.setDataRef(dim);
      CategoricalShapeFrameWrapper wrapper = new CategoricalShapeFrameWrapper();

      if(!DEFAULT.equals(palette)) {
         for(int i = 0; i < 16; i++) {
            wrapper.setShape(i, shapeId(palette, i));
         }

         wrapper.setChanged(true);
      }

      shape.setVisualFrameWrapper(wrapper);
      return shape;
   }

   private static String shapeId(String palette, int i) {
      return switch(palette) {
         case NULL -> "null";
         case OTHER -> String.valueOf(900 + (i + 2) % 16);
         // the filled circle and triangle in place of the hollow ones, the same shape classes
         case FILLED -> String.valueOf(i < 2 ? 907 + i : 900 + i);
         default -> String.valueOf(900 + i);
      };
   }

   private static Map<String, GShape> expected(String palette) {
      CategoricalShapeFrame frame =
         (CategoricalShapeFrame) shapeRef(palette).getVisualFrameWrapper().getVisualFrame();
      Map<String, GShape> shapes = new HashMap<>();
      shapes.put("NAMED", frame.getShape(0));
      shapes.put("UNNAMED", frame.getShape(1));
      return shapes;
   }

   /**
    * Render the chart and get the shape of each type value in the point element of the measure,
    * or of the only point element when measure is null.
    */
   private Map<String, GShape> shapes(ChartVSAssembly chart, String measure) throws Exception {
      String name = chart.getAbsoluteName();
      box.clearGraph(name);
      box.resetDataMap(name);
      VGraphPair pair = box.getVGraphPair(name, true, null);
      assertNotNull(pair, name);
      EGraph graph = pair.getEGraph();
      PointElement point = null;

      for(int i = 0; i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);

         if(elem instanceof PointElement &&
            (measure == null || Arrays.asList(elem.getVars()).contains(measure)))
         {
            assertNull(point, "one point element in " + name);
            point = (PointElement) elem;
         }
      }

      assertNotNull(point, "point element in " + name);
      Map<String, GShape> shapes = new HashMap<>();

      for(String value : new String[] { "NAMED", "UNNAMED" }) {
         shapes.put(value, point.getShapeFrame().getShape(value));
      }

      return shapes;
   }

   private static void setTipView(ChartVSAssembly parent, ChartVSAssembly tip) {
      setTipView(parent, tip.getName());
   }

   private static void setTipView(ChartVSAssembly parent, String tipView) {
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) parent.getInfo();
      info.setTipOptionValue(TipVSAssemblyInfo.VIEWTIP_OPTION);
      info.setTipOption(TipVSAssemblyInfo.VIEWTIP_OPTION);
      info.setTipViewValue(tipView);
      info.setTipView(tipView);
   }

   private static ConditionList unnamedCondition() {
      ConditionList conds = new ConditionList();
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue("UNNAMED");
      conds.append(new ConditionItem(new ColumnRef(new AttributeRef(null, "type")), cond, 0));
      return conds;
   }

   private static CategoricalShapeFrameWrapper designWrapper(ChartVSAssembly chart) {
      return (CategoricalShapeFrameWrapper)
         chart.getVSChartInfo().getShapeField().getVisualFrameWrapper();
   }

   private static String paletteXml(ChartVSAssembly chart) {
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      designWrapper(chart).writeXML(writer);
      writer.flush();
      String xml = out.toString();
      return xml.substring(xml.indexOf("<shapes>"), xml.indexOf("</shapes>"));
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   private ViewsheetSandbox box;
   private Viewsheet vs;

   private static final String TABLE = "TableView1";
   private static final String NULL = "null palette";
   private static final String GOOD = "900 palette";
   private static final String OTHER = "rotated palette";
   private static final String FILLED = "filled palette";
   private static final String DEFAULT = "default palette";
}
