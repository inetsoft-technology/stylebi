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
package inetsoft.graph.guide.legend;

import inetsoft.graph.*;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class StackedLegendOrderTest {
   @Test
   void aModernTopLegendOnAVerticalStackListsTheTopSegmentFirst() {
      assertEquals("C", leftmost(items(GraphConstants.TOP, false, true, true, 400, 3)));
   }

   @Test
   void aModernTopLegendOnAHorizontalStackReadsLikeTheSegments() {
      assertEquals("A", leftmost(items(GraphConstants.TOP, true, true, true, 400, 3)));
   }

   @Test
   void aSideLegendIsUnchanged() {
      for(boolean transposed : new boolean[] { false, true }) {
         for(boolean modern : new boolean[] { false, true }) {
            assertEquals("C", topmost(items(GraphConstants.RIGHT, transposed, modern, true, 400, 3)));
         }
      }
   }

   @Test
   void aClassicTopLegendIsUnchanged() {
      assertEquals("A", leftmost(items(GraphConstants.TOP, false, false, true, 400, 3)));
   }

   @Test
   void aClusteredLegendIsUnchanged() {
      assertEquals("A", leftmost(items(GraphConstants.TOP, false, true, false, 400, 3)));
   }

   @Test
   void aLegendOfTheBarFieldIsUnchanged() {
      assertEquals("A", leftmost(items(GraphConstants.TOP, false, true, true, 400, 3, "X")));
   }

   @Test
   void aWrappedReversedRowKeepsItsColumns() {
      List<LegendItem> items = items(GraphConstants.TOP, false, true, true, 130, 5);
      Map<Double, List<LegendItem>> rows = new TreeMap<>();

      for(LegendItem item : items) {
         rows.computeIfAbsent(item.getBounds().getY(), k -> new ArrayList<>()).add(item);
      }

      assertTrue(rows.size() > 1, "legend should wrap");
      List<List<LegendItem>> list = new ArrayList<>(rows.values());
      Collections.reverse(list);
      assertEquals(3, list.get(0).size());
      assertEquals(2, list.get(1).size());
      double[] cols = list.get(0).stream().mapToDouble(i -> i.getBounds().getX()).sorted()
         .toArray();
      assertEquals("E", leftmost(list.get(0)));
      assertEquals("B", leftmost(list.get(1)));

      for(List<LegendItem> row : list) {
         Set<Double> xs = new HashSet<>();

         for(LegendItem item : row) {
            assertTrue(xs.add(item.getBounds().getX()), "items share a column");
            assertTrue(Arrays.binarySearch(cols, item.getBounds().getX()) >= 0,
                       "item is off the column grid");
         }
      }
   }

   private static String leftmost(List<LegendItem> items) {
      return String.valueOf(items.stream()
         .min(Comparator.comparingDouble(i -> i.getBounds().getX())).get().getValue());
   }

   private static String topmost(List<LegendItem> items) {
      return String.valueOf(items.stream()
         .max(Comparator.comparingDouble(i -> i.getBounds().getY())).get().getValue());
   }

   private static List<LegendItem> items(int layout, boolean transposed, boolean inMarkOrder,
                                         boolean stacked, int width, int count)
   {
      return items(layout, transposed, inMarkOrder, stacked, width, count, "Color");
   }

   private static List<LegendItem> items(int layout, boolean transposed, boolean inMarkOrder,
                                         boolean stacked, int width, int count,
                                         String colorField)
   {
      Object[][] rows = new Object[count + 1][];
      rows[0] = new Object[] { "X", "Color", "V" };
      boolean byBar = "X".equals(colorField);

      for(int i = 0; i < count; i++) {
         String name = String.valueOf((char) ('A' + i));
         rows[i + 1] = new Object[] { byBar ? name : "P", name, 10 * (i + 1) };
      }

      EGraph graph = new EGraph();
      IntervalElement elem = new IntervalElement("X", "V");

      if(stacked) {
         elem.setCollisionModifier(GraphElement.MOVE_STACK);
      }

      elem.setColorFrame(new CategoricalColorFrame(colorField));
      graph.addElement(elem);
      RectCoord coord = new RectCoord(new CategoricalScale("X"), new LinearScale("V"));

      if(transposed) {
         coord.transpose();
      }

      graph.setCoordinate(coord);
      graph.setLegendLayout(layout);
      graph.setLegendInMarkOrder(inMarkOrder);
      VGraph vg = Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, width, 240);
      List<LegendItem> result = new ArrayList<>();
      LegendGroup group = vg.getLegendGroup();

      for(int i = 0; i < group.getLegendCount(); i++) {
         Legend legend = group.getLegend(i);

         for(int j = 0; j < legend.getVisualCount(); j++) {
            if(legend.getVisual(j) instanceof LegendItem) {
               result.add((LegendItem) legend.getVisual(j));
            }
         }
      }

      return result;
   }
}
