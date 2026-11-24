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

import inetsoft.graph.*;
import inetsoft.graph.aesthetic.DefaultTextFrame;
import inetsoft.graph.aesthetic.StaticSizeFrame;
import inetsoft.graph.coord.RelationCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.RelationElement;
import inetsoft.graph.internal.GTool;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A relation label cut with the ellipsis on ends in "…", and a label outside its node is cut at
 * the label limit. A label inside a tree box keeps cutting at the box.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RelationVOLabelCutTest {
   // the longest label for 10px text
   private static final double LIMIT = 66;

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aMarkedTreeWrapEndsInTheEllipsis() {
      Map<String, String> labels = labels(plot(tree(true, LIMIT), 420, 280));
      assertTrue(labels.values().stream().anyMatch(t -> t.endsWith("…")), labels.toString());

      for(String text : labels.values()) {
         assertFalse(text.contains(".."), labels.toString());
      }
   }

   @Test
   void aClassicTreeWrapKeepsThreeDots() {
      Map<String, String> labels = labels(plot(tree(false, 0), 420, 280));

      assertTrue(labels.values().stream().anyMatch(t -> t.endsWith("...")), labels.toString());
      labels.values().forEach(t -> assertFalse(t.contains("…"), labels.toString()));
   }

   @Test
   void theLimitLeavesTheTreeBoxTextAsItIs() {
      VGraph capped = plot(tree(true, LIMIT), 420, 280);
      VGraph uncapped = plot(tree(true, 0), 420, 280);

      assertBoxesUnderTheLimit(capped);
      assertEquals(labels(uncapped), labels(capped));
      assertEquals(sizes(uncapped), sizes(capped));
   }

   @Test
   void theLimitLeavesAShrunkTreeBoxAsItIs() {
      RelationElement capped = tree(true, LIMIT);
      RelationElement uncapped = tree(true, 0);
      capped.setPlotFit(true);
      uncapped.setPlotFit(true);
      VGraph vcapped = plot(capped, 120, 280);
      VGraph vuncapped = plot(uncapped, 120, 280);

      assertTrue(boxWidths(vcapped).stream().allMatch(w -> w < 40),
                 "premise: the boxes shrink " + boxWidths(vcapped));
      assertBoxesUnderTheLimit(vcapped);
      assertEquals(labels(vuncapped), labels(vcapped));
      assertEquals(sizes(vuncapped), sizes(vcapped));
   }

   @Test
   void aMarkedNetworkLabelIsCutAtTheLimit() {
      for(RelationElement.Algorithm algorithm : OUTSIDE) {
         VGraph vgraph = plot(outside(algorithm, true, LIMIT, null), 420, 280);
         Map<String, String> labels = labels(vgraph);
         Font font = font(vgraph);
         assertEquals(10, font.getSize2D(), "premise: 10px text");
         assertTrue(labels.keySet().stream().anyMatch(n -> width(n, font) > LIMIT),
                    "premise: a name over the limit " + labels);

         for(Map.Entry<String, String> e : labels.entrySet()) {
            String name = e.getKey();
            String text = e.getValue();

            if(width(name, font) > LIMIT) {
               assertTrue(text.endsWith("…"), algorithm + " " + e);
               // the cut measures the text and the mark apart, so allow 1px of rounding
               assertTrue(width(text, font) <= LIMIT + 1, algorithm + " " + e);
            }
            else {
               assertEquals(name, text, algorithm.toString());
            }
         }

         sizes(vgraph).values().forEach(w -> assertTrue(w <= LIMIT + 1e-6, algorithm + " " + w));
      }
   }

   @Test
   void theLimitScalesWithTheFont() {
      VGraph vgraph = plot(outside(RelationElement.Algorithm.ORGANIC, true, LIMIT, 20f), 420, 280);
      Font font = font(vgraph);
      assertEquals(20, font.getSize2D(), "premise: 20px text");
      Collection<Double> widths = sizes(vgraph).values();

      assertTrue(widths.stream().anyMatch(w -> w > LIMIT), widths.toString());
      widths.forEach(w -> assertTrue(w <= LIMIT * 2 + 1e-6, widths.toString()));
   }

   @Test
   void aClassicNetworkLabelIsNotCut() {
      for(RelationElement.Algorithm algorithm : OUTSIDE) {
         VGraph vgraph = plot(outside(algorithm, false, 0, null), 420, 280);
         Map<String, String> labels = labels(vgraph);
         Font font = font(vgraph);
         assertTrue(labels.keySet().stream().anyMatch(n -> width(n, font) > LIMIT),
                    "premise: a name over the limit " + labels);

         for(Map.Entry<String, String> e : labels.entrySet()) {
            // the label's own 150px maximum is the only cut
            if(width(e.getKey(), font) < 150) {
               assertEquals(e.getKey(), e.getValue(), algorithm.toString());
            }
         }
      }
   }

   private static final RelationElement.Algorithm[] OUTSIDE = {
      RelationElement.Algorithm.ORGANIC, RelationElement.Algorithm.CIRCLE };

   private static void assertBoxesUnderTheLimit(VGraph vgraph) {
      Collection<Double> widths = sizes(vgraph).values();
      assertFalse(widths.isEmpty(), "premise: labels");
      widths.forEach(w -> assertTrue(w < LIMIT, "premise: the box is under the limit " + w));
   }

   // a tree with its labels inside 40x30 boxes, as a tree chart is generated
   private static RelationElement tree(boolean ellipsis, double limit) {
      RelationElement elem = element(ellipsis, limit);
      elem.setAlgorithm(RelationElement.Algorithm.COMPACT_TREE);
      elem.setNodeWidth(40);
      elem.setNodeHeight(30);
      elem.setLabelPlacement(GraphConstants.CENTER);
      return elem;
   }

   // a network or circular chart with its labels above the nodes
   private static RelationElement outside(RelationElement.Algorithm algorithm, boolean ellipsis,
                                          double limit, Float fontSize)
   {
      RelationElement elem = element(ellipsis, limit);
      elem.setAlgorithm(algorithm);
      elem.setNodeWidth(5);
      elem.setNodeHeight(5);
      elem.setLabelPlacement(GraphConstants.TOP);

      if(fontSize != null) {
         TextSpec spec = new TextSpec();
         spec.setFont(spec.getFont().deriveFont(fontSize));
         elem.setTextSpec(spec);
      }

      return elem;
   }

   private static RelationElement element(boolean ellipsis, double limit) {
      RelationElement elem = new RelationElement("From", "To");
      elem.setSizeFrame(new StaticSizeFrame(1));
      elem.setNodeSizeFrame(new StaticSizeFrame(1));
      elem.setTextFrame(new DefaultTextFrame("To"));
      elem.setLabelEllipsis(ellipsis);
      elem.setLabelLimit(limit);
      return elem;
   }

   private static VGraph plot(RelationElement elem, int width, int height) {
      EGraph egraph = new EGraph();
      egraph.addElement(elem);
      egraph.setCoordinate(new RelationCoord());
      VGraph vgraph = Plotter.getPlotter(egraph).plotAndLayout(
         new DefaultDataSet(ROWS), 0, 0, width, height);
      assertNotNull(vgraph);
      return vgraph;
   }

   private static List<VOText> texts(VGraph vgraph) {
      List<VOText> texts = new ArrayList<>();

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof RelationVO vo && vo.getVOText() != null) {
            texts.add(vo.getVOText());
         }
      }

      return texts;
   }

   // each node's name and the text it draws
   private static Map<String, String> labels(VGraph vgraph) {
      Map<String, String> labels = new TreeMap<>();
      texts(vgraph).forEach(t -> labels.put(t.getText(), String.join(" ", t.getDisplayLabel())));
      return labels;
   }

   private static Map<String, Double> sizes(VGraph vgraph) {
      Map<String, Double> sizes = new TreeMap<>();
      texts(vgraph).forEach(t -> sizes.put(t.getText(), t.getSize().getWidth()));
      return sizes;
   }

   private static List<Double> boxWidths(VGraph vgraph) {
      List<Double> widths = new ArrayList<>();

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof RelationVO vo) {
            widths.add(vo.getShapes()[0].getBounds2D().getWidth());
         }
      }

      return widths;
   }

   private static Font font(VGraph vgraph) {
      return texts(vgraph).get(0).getFont();
   }

   private static double width(String text, Font font) {
      return GTool.stringWidth(new String[] { text }, font);
   }

   private static final Object[][] ROWS = {
      { "From", "To" },
      { "CEO", "Senior Director of Operations" },
      { "CEO", "Regional Sales Manager North" },
      { "CEO", "Chief Financial Officer" },
      { "Senior Director of Operations", "Plant Supervisor" },
      { "Senior Director of Operations", "Logistics Coordinator East" },
      { "Regional Sales Manager North", "Account Executive" },
      { "Chief Financial Officer", "Controller" },
   };
}
