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
package inetsoft.graph.visual;

import inetsoft.graph.EGraph;
import inetsoft.graph.GraphConstants;
import inetsoft.graph.TextSpec;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.RelationCoord;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.RelationElement;
import inetsoft.graph.geometry.RelationGeometry;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.graphics.SVGSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * How a tree node box is painted with and without the tint.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RelationVOTintTest {
   private static final Color BLUE = new Color(0x0490FF);
   private static final Color BACKDROP = new Color(0xECEBE9);
   private static final Color INK = new Color(0x35342F);
   // white under the classic ink rule, black under the contrast rule
   private static final Color MID_TONE = new Color(0xE5197E);

   // the batik SVG support is not on the test classpath; seed a stub and restore the cache after
   private SVGSupport savedSvgSupport;

   @BeforeEach
   void stubSvgSupport() {
      savedSvgSupport = SVGSupport._CACHE[0];
      SVGSupport._CACHE[0] = mock(SVGSupport.class);
   }

   @AfterEach
   void reset() {
      SVGSupport._CACHE[0] = savedSvgSupport;
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aTintedBoxFillsLightAndOutlinesFull() {
      RelationElement elem = element(new StaticColorFrame(BLUE), 0.15, BACKDROP, null);
      Painted p = paintAll(plot(elem)).get(0);

      assertColor(new Color(0xC9DDEC), p.fill, 2);
      assertEquals(BLUE, p.outline);
      assertTrue(p.antiAlias);
   }

   @Test
   void theInkDrawsInsideLabels() {
      RelationElement elem = element(new StaticColorFrame(BLUE), 0.15, BACKDROP, INK);
      elem.setTextFrame(new DefaultTextFrame("To"));
      elem.setLabelPlacement(GraphConstants.CENTER);
      VGraph vgraph = plot(elem);
      int labels = 0;

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof VOText text) {
            labels++;
            assertEquals(INK, text.getTextSpec().getColor());
         }
      }

      assertTrue(labels > 0, "premise: labels");
   }

   @Test
   void aDimensionColourTintsEachNode() {
      CategoricalColorFrame colors = new CategoricalColorFrame("To");
      colors.init("L1", "L2", "L3");
      RelationElement elem = element(colors, 0.15, BACKDROP, null);
      VGraph vgraph = plot(elem);
      List<Painted> painted = paintAll(vgraph);
      List<RelationVO> vos = nodes(vgraph);
      assertTrue(painted.size() > 2);

      for(int i = 0; i < painted.size(); i++) {
         Color own = ((RelationGeometry) vos.get(i).getGeometry()).getColor();
         assertColor(blend(own, 0.15, BACKDROP), painted.get(i).fill, 2);
         assertColor(own, painted.get(i).outline, 0);
      }

      assertTrue(painted.stream().map(p -> p.fill).distinct().count() > 1,
                 painted.stream().map(p -> String.valueOf(p.fill)).toList().toString());
   }

   @Test
   void aHighlightedNodeStaysSolid() {
      CompositeColorFrame frame = new CompositeColorFrame();
      frame.addFrame(new Highlight("L1", Color.RED));
      frame.addFrame(new StaticColorFrame(BLUE));
      VGraph vgraph = plot(element(frame, 0.15, BACKDROP, INK));
      List<RelationVO> vos = nodes(vgraph);
      List<Painted> painted = paintAll(vgraph);
      int solid = 0;
      int tinted = 0;

      for(int i = 0; i < painted.size(); i++) {
         Color own = ((RelationGeometry) vos.get(i).getGeometry()).getColor();

         if(Color.RED.equals(own)) {
            solid++;
            assertEquals(Color.RED, painted.get(i).fill);
            assertNull(painted.get(i).outline);
         }
         else {
            tinted++;
            assertColor(blend(BLUE, 0.15, BACKDROP), painted.get(i).fill, 2);
         }
      }

      assertTrue(solid > 0);
      assertTrue(tinted > 0);
   }

   @Test
   void withoutTheTintNothingChanges() {
      Painted p = paintAll(plot(element(new StaticColorFrame(BLUE), 0, null, null))).get(0);

      assertEquals(BLUE, p.fill);
      assertNull(p.outline);
   }

   @Test
   void thePlotTransparencyAppliesToTheTint() {
      RelationElement elem = element(new StaticColorFrame(BLUE), 0.15, BACKDROP, null);
      elem.setHint(GraphElement.HINT_ALPHA, 0.5);
      Painted p = paintAll(plot(elem)).get(0);

      assertEquals(128, p.fill.getAlpha(), 2);
      assertEquals(128, p.outline.getAlpha(), 2);
   }

   @Test
   void aScriptBorderColourIsTheOutline() {
      RelationElement elem = element(new StaticColorFrame(BLUE), 0.15, BACKDROP, null);
      elem.setBorderColor(Color.RED);
      Painted p = paintAll(plot(elem)).get(0);

      assertColor(new Color(0xC9DDEC), p.fill, 2);
      assertEquals(Color.RED, p.outline);
   }

   @Test
   void aScriptFillColourIsNotTinted() {
      RelationElement elem = element(new StaticColorFrame(BLUE), 0.15, BACKDROP, null);
      elem.setFillColor(Color.GREEN);
      Painted p = paintAll(plot(elem)).get(0);

      assertEquals(Color.GREEN, p.fill);
   }

   @Test
   void theInkIsNotWrittenToTheSharedSpec() {
      RelationElement elem = element(new StaticColorFrame(BLUE), 0.15, BACKDROP, INK);
      elem.setTextFrame(new DefaultTextFrame("To"));
      elem.setLabelPlacement(GraphConstants.CENTER);
      TextSpec shared = elem.getTextSpec();
      Color before = shared.getColor();
      plot(elem);

      assertEquals(before, shared.getColor());
   }

   @Test
   void aSolidNodeLabelTakesTheContrastInk() {
      VGraph vgraph = plot(autoInk(element(new StaticColorFrame(MID_TONE), 0, null, null)));
      List<VOText> labels = labels(vgraph);
      assertFalse(labels.isEmpty(), "premise: labels");

      for(VOText text : labels) {
         assertEquals(MID_TONE, text.getAutoBackground());
         // the white-preferring ink would be white on this fill
         assertEquals(Color.BLACK, text.getColor());
      }
   }

   @Test
   void aTintedNodeLabelTakesTheInkOverTheContrastInk() {
      VGraph vgraph = plot(autoInk(element(new StaticColorFrame(MID_TONE), 0.15, BACKDROP, INK)));
      List<VOText> labels = labels(vgraph);
      assertFalse(labels.isEmpty(), "premise: labels");

      for(VOText text : labels) {
         assertNull(text.getAutoBackground());
         assertEquals(INK, text.getColor());
      }
   }

   // labels inside the nodes with the automatic, higher-contrast ink a marked chart sets
   private static RelationElement autoInk(RelationElement elem) {
      elem.setTextFrame(new DefaultTextFrame("To"));
      elem.setLabelPlacement(GraphConstants.CENTER);
      elem.setAutoTextColor(true);
      elem.setContrastInk(true);
      return elem;
   }

   private static List<VOText> labels(VGraph vgraph) {
      List<VOText> labels = new ArrayList<>();

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof VOText text) {
            labels.add(text);
         }
      }

      return labels;
   }

   // the opaque result of a colour at an alpha over a backdrop
   private static Color blend(Color c, double alpha, Color backdrop) {
      return new Color(
         (int) Math.round(c.getRed() * alpha + backdrop.getRed() * (1 - alpha)),
         (int) Math.round(c.getGreen() * alpha + backdrop.getGreen() * (1 - alpha)),
         (int) Math.round(c.getBlue() * alpha + backdrop.getBlue() * (1 - alpha)));
   }

   private static void assertColor(Color expected, Color actual, int tolerance) {
      assertNotNull(actual, "colour");
      assertEquals(expected.getRed(), actual.getRed(), tolerance, "red of " + actual);
      assertEquals(expected.getGreen(), actual.getGreen(), tolerance, "green of " + actual);
      assertEquals(expected.getBlue(), actual.getBlue(), tolerance, "blue of " + actual);
   }

   private static RelationElement element(ColorFrame colors, double alpha, Color backdrop,
                                          Color ink)
   {
      RelationElement elem = new RelationElement("From", "To");
      elem.setAlgorithm(RelationElement.Algorithm.COMPACT_TREE);
      elem.setSizeFrame(new StaticSizeFrame(1));
      elem.setNodeSizeFrame(new StaticSizeFrame(1));
      elem.setNodeColorFrame(colors);
      elem.setBoxTint(alpha, backdrop);
      elem.setBoxInk(ink);
      return elem;
   }

   private static VGraph plot(RelationElement elem) {
      EGraph egraph = new EGraph();
      egraph.addElement(elem);
      egraph.setCoordinate(new RelationCoord());
      DataSet data = new DefaultDataSet(new Object[][] {
         { "From", "To" }, { "R", "L1" }, { "R", "L2" }, { "R", "L3" } });
      VGraph vgraph = Plotter.getPlotter(egraph).plotAndLayout(data, 0, 0, 300, 200);
      assertNotNull(vgraph);
      return vgraph;
   }

   private static List<RelationVO> nodes(VGraph vgraph) {
      List<RelationVO> vos = new ArrayList<>();

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof RelationVO vo) {
            vos.add(vo);
         }
      }

      return vos;
   }

   // the colour each node was filled with and outlined in
   private static List<Painted> paintAll(VGraph vgraph) {
      List<Painted> list = new ArrayList<>();

      for(RelationVO vo : nodes(vgraph)) {
         Graphics2D g = mock(Graphics2D.class);
         Graphics2D g2 = mock(Graphics2D.class);
         when(g.create()).thenReturn(g2);
         Painted p = new Painted();
         Color[] current = new Color[1];
         doAnswer(i -> {
            current[0] = i.getArgument(0);
            return null;
         }).when(g2).setColor(any(Color.class));
         doAnswer(i -> {
            p.fill = current[0];
            return null;
         }).when(g2).fill(any(Shape.class));
         doAnswer(i -> {
            p.outline = current[0];
            return null;
         }).when(g2).draw(any(Shape.class));
         doAnswer(i -> {
            if(RenderingHints.KEY_ANTIALIASING.equals(i.getArgument(0))) {
               p.antiAlias = RenderingHints.VALUE_ANTIALIAS_ON.equals(i.getArgument(1));
            }

            return null;
         }).when(g2).setRenderingHint(any(RenderingHints.Key.class), any());
         vo.paint(g);
         list.add(p);
      }

      return list;
   }

   private static final class Painted {
      Color fill;
      Color outline;
      boolean antiAlias;
   }

   // a highlight frame colouring one named node
   private static final class Highlight extends ColorFrame {
      Highlight(String value, Color color) {
         this.value = value;
         this.color = color;
      }

      @Override
      public Color getColor(DataSet data, String col, int row) {
         return value.equals(data.getData("To", row)) ? color : null;
      }

      @Override
      public Color getColor(Object val) {
         return null;
      }

      @Override
      public boolean isHighlightFrame() {
         return true;
      }

      @Override
      public boolean isApplicable(String field) {
         return true;
      }

      private final String value;
      private final Color color;
   }
}
