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
import inetsoft.graph.element.TreemapElement;
import inetsoft.graph.geometry.TreemapGeometry;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.visual.TreemapVO;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.LinearSizeFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.TealColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.VisualFrameWrapper;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A marked sunburst or icicle steps each level below its colour field's level toward the card.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LevelStepRenderTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void colourByTheTopLevelStepsBelowIt() {
      TreemapElement elem = element(GraphTypes.CHART_SUNBURST, VizMark.MODERN_LIGHT,
                                    color(dim("Division")));

      assertEquals(0, elem.getLevelStepBase());
      assertEquals(LIGHT_CARD, elem.getLevelStepCard());
   }

   @Test
   void colourByTheSecondLevel() {
      TreemapElement elem = element(GraphTypes.CHART_SUNBURST, VizMark.MODERN_LIGHT,
                                    color(dim("Department")));

      assertEquals(1, elem.getLevelStepBase());
   }

   @Test
   void aStaticSunburstStepsFromTheTop() {
      TreemapElement elem = element(GraphTypes.CHART_SUNBURST, VizMark.MODERN_LIGHT, null);

      assertEquals(0, elem.getLevelStepBase());
      assertEquals(LIGHT_CARD, elem.getLevelStepCard());
   }

   @Test
   void aMeasureOnColourGetsNoSteps() {
      VSAestheticRef color = new VSAestheticRef();
      VSChartAggregateRef var = agg("Var");
      color.setDataRef(var);
      color.setRTDataRef(var);
      color.setVisualFrameWrapper(new TealColorFrameWrapper());

      TreemapElement elem = element(GraphTypes.CHART_SUNBURST, VizMark.MODERN_LIGHT, color);

      assertEquals(-1, elem.getLevelStepBase());
   }

   @Test
   void aRampOnATreeLevelGetsNoSteps() {
      VSAestheticRef color = color(dim("Division"));
      color.setVisualFrameWrapper(new TealColorFrameWrapper());

      TreemapElement elem = element(GraphTypes.CHART_SUNBURST, VizMark.MODERN_LIGHT, color);

      assertEquals(-1, elem.getLevelStepBase());
   }

   @Test
   void aColourFieldOutsideTheTreeGetsNoSteps() {
      TreemapElement elem = element(GraphTypes.CHART_SUNBURST, VizMark.MODERN_LIGHT,
                                    color(dim("Region")));

      assertEquals(-1, elem.getLevelStepBase());
   }

   @Test
   void anIcicleSteps() {
      TreemapElement elem = element(GraphTypes.CHART_ICICLE, VizMark.MODERN_LIGHT,
                                    color(dim("Division")));

      assertEquals(TreemapElement.Type.ICICLE, elem.getMapType());
      assertEquals(0, elem.getLevelStepBase());
      assertEquals(LIGHT_CARD, elem.getLevelStepCard());
   }

   @Test
   void aTreemapGetsNoSteps() {
      TreemapElement elem = element(GraphTypes.CHART_TREEMAP, VizMark.MODERN_LIGHT,
                                    color(dim("Division")));

      assertEquals(TreemapElement.Type.TREEMAP, elem.getMapType());
      assertEquals(-1, elem.getLevelStepBase());
      assertNull(elem.getLevelStepCard());
   }

   @Test
   void aCirclePackingGetsNoSteps() {
      TreemapElement elem = element(GraphTypes.CHART_CIRCLE_PACKING, VizMark.MODERN_LIGHT,
                                    color(dim("Division")));

      assertEquals(TreemapElement.Type.CIRCLE, elem.getMapType());
      assertEquals(-1, elem.getLevelStepBase());
   }

   @Test
   void aClassicSunburstGetsNoSteps() {
      TreemapElement elem = element(GraphTypes.CHART_SUNBURST, null, color(dim("Division")));

      assertEquals(-1, elem.getLevelStepBase());
      assertNull(elem.getLevelStepCard());
   }

   @Test
   void aDarkMarkUsesTheDarkCard() {
      TreemapElement elem = element(GraphTypes.CHART_SUNBURST, VizMark.MODERN_DARK,
                                    color(dim("Division")));

      assertEquals(0, elem.getLevelStepBase());
      assertEquals(DARK_CARD, elem.getLevelStepCard());
   }

   @Test
   void theLabelInkReadsTheSteppedColour() {
      GraphGenerator gen = generator(chart(GraphTypes.CHART_SUNBURST, color(dim("Division"))),
                                     VizMark.MODERN_LIGHT);
      EGraph graph = gen.createEGraph();
      VGraph vgraph = Plotter.getPlotter(graph).plot(gen.getData());
      TreemapVO vo = secondLevel(vgraph);
      TreemapGeometry gobj = (TreemapGeometry) vo.getGeometry();
      TreemapElement elem = (TreemapElement) gobj.getElement();
      Color group = gobj.getColor(0);

      assertNotNull(vo.getVOText(), "premise: the node draws a label");
      assertNotEquals(group, elem.getLevelColor(group, 1), "premise: the level steps");
      assertEquals(elem.getLevelColor(group, 1), vo.getVOText().getAutoBackground());
   }

   @Test
   void theFillIsTheSteppedColour() {
      GraphGenerator gen = generator(chart(GraphTypes.CHART_ICICLE, color(dim("Division"))),
                                     VizMark.MODERN_LIGHT);
      EGraph graph = gen.createEGraph();
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(gen.getData(), 0, 0, 400, 300);
      TreemapVO vo = secondLevel(vgraph);
      TreemapGeometry gobj = (TreemapGeometry) vo.getGeometry();
      TreemapElement elem = (TreemapElement) gobj.getElement();
      Color group = gobj.getColor(0);
      Color stepped = elem.getLevelColor(group, 1);
      Rectangle2D box = vo.getShapes()[0].getBounds2D();

      BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();
      vo.paint(g);
      g.dispose();

      assertTrue(new Rectangle(0, 0, 400, 300).contains(box.getCenterX(), box.getCenterY()),
                 "premise: the node is on the image " + box);
      Color pixel = new Color(image.getRGB((int) box.getCenterX(), (int) box.getCenterY()));
      assertNotEquals(group.getRGB() & 0xFFFFFF, stepped.getRGB() & 0xFFFFFF,
                      "premise: the level steps");
      assertEquals(stepped.getRGB() & 0xFFFFFF, pixel.getRGB() & 0xFFFFFF);
   }

   @Test
   void everyHeadSlotKeepsItsLabelAtEveryStep() {
      for(VizMark mark : new VizMark[] { VizMark.MODERN_LIGHT, VizMark.MODERN_DARK }) {
         VizContext ctx = VizContext.of(mark);
         Color[] palette = VSChartPaletteDefaults.staticPalette(ctx);
         TreemapElement elem = new TreemapElement("Division", "Department");
         elem.setLevelStep(0, VSObjectChromeDefaults.cardComposite(ctx));

         for(int slot = 0; slot < 8; slot++) {
            for(int k = 0; k <= 3; k++) {
               Color step = elem.getLevelColor(palette[slot], k);
               double l = GTool.getLuminance(step);
               double inkL = GTool.getLuminance(GTool.getTextColor(step, true));
               double ratio = (Math.max(l, inkL) + 0.05) / (Math.min(l, inkL) + 0.05);

               assertTrue(ratio >= 4.5, mark + " slot " + slot + " step " + k + " ratio " + ratio);
            }
         }
      }
   }

   // the second-level node that draws a label
   private static TreemapVO secondLevel(VGraph vgraph) {
      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof TreemapVO vo &&
            ((TreemapGeometry) vo.getGeometry()).getLevel() == 0 &&
            ((TreemapGeometry) vo.getGeometry()).getTupleIndex() >= 0)
         {
            return vo;
         }
      }

      return fail("premise: a second-level node is drawn");
   }

   private static TreemapElement element(int chartType, VizMark mark, VSAestheticRef color) {
      EGraph graph = generator(chart(chartType, color), mark).createEGraph();

      assertInstanceOf(TreemapElement.class, graph.getElement(0));
      return (TreemapElement) graph.getElement(0);
   }

   // Division then Department in the tree, sized by Sum(Total)
   private static VSChartInfo chart(int chartType, VSAestheticRef color) {
      VSAestheticRef size = new VSAestheticRef();
      size.setDataRef(agg("Total"));
      size.setVisualFrameWrapper(new LinearSizeFrameWrapper());

      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(chartType);
      cinfo.setRTChartType(chartType);
      cinfo.addGroupField(dim("Division"));
      cinfo.addGroupField(dim("Department"));
      cinfo.setSizeField(size);

      if(color != null) {
         cinfo.setColorField(color);
      }

      cinfo.updateChartType(false);
      return cinfo;
   }

   private static GraphGenerator generator(VSChartInfo cinfo, VizMark mark) {
      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);
      VizModernizeUtil.reseedAfterRestore(assemblyInfo);

      return GraphGenerator.getGenerator(assemblyInfo, null, new DefaultDataSet(ROWS),
                                         new VariableTable(), null, XSourceInfo.NONE, null);
   }

   private static VSAestheticRef color(ChartRef ref) {
      VisualFrameWrapper wrapper = new CategoricalColorFrameWrapper();
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(ref);
      color.setRTDataRef(ref);
      color.setVisualFrameWrapper(wrapper);
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

   private static final Object[][] ROWS = {
      { "Division", "Department", "Region", "Sum(Total)", "Sum(Var)" },
      { "East", "Sales", "North", 100.0, 1.0 },
      { "East", "Support", "North", 200.0, 2.0 },
      { "West", "Sales", "South", 300.0, 3.0 },
      { "West", "Support", "South", 150.0, 4.0 },
   };
   private static final Color LIGHT_CARD = new Color(0xECEBE9);
   private static final Color DARK_CARD = new Color(0x313034);
}
