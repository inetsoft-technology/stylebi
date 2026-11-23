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
package inetsoft.graph.aesthetic;

import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.LineElement;
import inetsoft.graph.element.PointElement;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82049: CategoricalLineFrame.equals ignored the per-value assignments and the scripted
 * flags, and PieShapeFrame.equals ignored donut, so the chart merged the legends of two
 * elements drawn differently.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FrameEqualsStateTest {
   @Test
   void lineEqualsComparesPerValueLines() {
      CategoricalLineFrame f1 = new CategoricalLineFrame("x");
      CategoricalLineFrame f2 = new CategoricalLineFrame("x");
      assertEquals(f1, f2);
      assertEquals(f1.hashCode(), f2.hashCode());

      f2.setLine("A", GLine.DASH_LINE);
      assertNotEquals(f1, f2);
      assertNotEquals(f2, f1);

      f1.setLine("A", GLine.DASH_LINE);
      assertEquals(f1, f2);
      assertEquals(f1.hashCode(), f2.hashCode());
   }

   @Test
   void lineEqualsComparesScripted() throws Exception {
      CategoricalLineFrame f1 = new CategoricalLineFrame("x");
      CategoricalLineFrame f2 = new CategoricalLineFrame("x");
      f1.setLine("A", GLine.DASH_LINE);
      f2.setLine("A", GLine.DASH_LINE);
      // what setLine(Object, GLine) records on a script thread
      scripted(f2).add("A");

      assertNotEquals(f1, f2);
   }

   @Test
   void pieEqualsComparesDonut() {
      PieShapeFrame p1 = new PieShapeFrame("y1", "y2");
      PieShapeFrame p2 = new PieShapeFrame("y1", "y2");
      assertEquals(p1, p2);
      assertEquals(p1.getUniqueId(), p2.getUniqueId());

      p2.setDonut(true);
      assertNotEquals(p1, p2);
      assertNotEquals(p2, p1);
      assertNotEquals(p1.getUniqueId(), p2.getUniqueId());

      p1.setDonut(true);
      assertEquals(p1, p2);
      assertEquals(p1.hashCode(), p2.hashCode());
      assertEquals(p1.getUniqueId(), p2.getUniqueId());
   }

   @Test
   void lineLegendsDifferingByValueAreNotMerged() {
      CategoricalLineFrame f1 = new CategoricalLineFrame("x");
      CategoricalLineFrame f2 = new CategoricalLineFrame("x");
      f2.setLine("v00", GLine.LARGE_DASH);

      assertEquals(2, legendCount(new LineElement("x", "y1"), f1, new LineElement("x", "y2"), f2));
   }

   @Test
   void equalLineFramesShareOneLegend() {
      CategoricalLineFrame f1 = new CategoricalLineFrame("x");
      CategoricalLineFrame f2 = new CategoricalLineFrame("x");
      f1.setLine("v00", GLine.LARGE_DASH);
      f2.setLine("v00", GLine.LARGE_DASH);

      assertEquals(1, legendCount(new LineElement("x", "y1"), f1, new LineElement("x", "y2"), f2));
   }

   @Test
   void pieAndDonutGetTwoLegends() {
      PieShapeFrame p1 = new PieShapeFrame("y1", "y2");
      PieShapeFrame p2 = new PieShapeFrame("y1", "y2");
      p2.setDonut(true);

      assertEquals(2, legendCount(new PointElement("x", "y1"), p1, new PointElement("x", "y2"), p2));
   }

   @Test
   void equalPieFramesShareOneLegend() {
      PieShapeFrame p1 = new PieShapeFrame("y1", "y2");
      PieShapeFrame p2 = new PieShapeFrame("y1", "y2");

      assertEquals(1, legendCount(new PointElement("x", "y1"), p1, new PointElement("x", "y2"), p2));
   }

   private static int legendCount(GraphElement e1, VisualFrame f1, GraphElement e2, VisualFrame f2) {
      setFrame(e1, f1);
      setFrame(e2, f2);
      EGraph graph = new EGraph();
      graph.addElement(e1);
      graph.addElement(e2);
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(data(), 0, 0, 600, 400);
      return vgraph.getLegendGroup() == null ? 0 : vgraph.getLegendGroup().getLegendCount();
   }

   private static void setFrame(GraphElement elem, VisualFrame frame) {
      if(frame instanceof LineFrame) {
         elem.setLineFrame((LineFrame) frame);
      }
      else {
         elem.setShapeFrame((ShapeFrame) frame);
      }
   }

   @SuppressWarnings("unchecked")
   private static Set<Object> scripted(CategoricalLineFrame frame) throws Exception {
      Field field = CategoricalLineFrame.class.getDeclaredField("scripted");
      field.setAccessible(true);
      return (Set<Object>) field.get(frame);
   }

   private static DefaultDataSet data() {
      Object[][] rows = new Object[13][];
      rows[0] = new Object[] { "x", "y1", "y2" };

      for(int i = 0; i < 12; i++) {
         rows[i + 1] = new Object[] { String.format("v%02d", i), i + 1, (i + 1) * 2 };
      }

      return new DefaultDataSet(rows);
   }
}
