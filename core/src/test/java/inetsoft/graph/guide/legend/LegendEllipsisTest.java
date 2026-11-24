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
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.guide.VLabel;
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

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A legend with the ellipsis on cuts an entry with "…" inside the entry; without it an entry is
 * cut with today's "..". The legend keeps its size cap and has no length limit.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LegendEllipsisTest {
   @Test
   void theFlagIsOffByDefault() {
      assertFalse(new EGraph().isLegendEllipsis());
   }

   @Test
   void aMarkedEntryIsCutWithTheEllipsisInsideTheEntry() {
      List<Entry> entries = entries(chart(true, 420, 280));
      assertTrue(entries.stream().anyMatch(Entry::cut), "premise: a cut entry " + entries);

      for(Entry entry : entries) {
         if(entry.cut()) {
            assertTrue(entry.text().endsWith("…"), entry.toString());
            assertFalse(entry.text().contains(".."), entry.toString());
         }

         assertTrue(entry.drawn().getMaxX() <= entry.item().getMaxX() + 0.5, entry.toString());
      }
   }

   @Test
   void aClassicEntryKeepsTwoDots() {
      List<Entry> entries = entries(chart(false, 420, 280));
      assertTrue(entries.stream().anyMatch(Entry::cut), "premise: a cut entry " + entries);

      for(Entry entry : entries) {
         if(entry.cut()) {
            assertTrue(entry.text().endsWith(".."), entry.toString());
            assertFalse(entry.text().contains("…"), entry.toString());
         }
      }
   }

   @Test
   void aWideMarkedLegendKeepsFullNames() {
      List<Entry> entries = entries(chart(true, 1200, 280));

      assertEquals(NAMES.length, entries.size(), "premise: every entry " + entries);
      entries.forEach(entry -> assertFalse(entry.cut(), entry.toString()));
   }

   @Test
   void theMarkKeepsTheLegendSize() {
      VGraph marked = chart(true, 420, 280);
      VGraph classic = chart(false, 420, 280);

      assertEquals(classic.getLegendGroup().getBounds(), marked.getLegendGroup().getBounds());
      assertEquals(classic.getPlotBounds(), marked.getPlotBounds());
   }

   private record Entry(String name, String text, Rectangle2D item, Rectangle2D drawn) {
      boolean cut() {
         return !text.equals(name);
      }
   }

   private static List<Entry> entries(VGraph vgraph) {
      List<Entry> entries = new ArrayList<>();
      LegendGroup group = vgraph.getLegendGroup();
      assertNotNull(group, "premise: a legend");

      for(int i = 0; i < group.getLegendCount(); i++) {
         Legend legend = group.getLegend(i);

         for(int j = 0; j < legend.getVisualCount(); j++) {
            if(legend.getVisual(j) instanceof LegendItem item) {
               VLabel label = label(item);
               entries.add(new Entry(item.getLabel(), String.join(" ", label.getDisplayLabel()),
                                     item.getBounds(),
                                     label.getTransformedBounds().getBounds2D()));
            }
         }
      }

      return entries;
   }

   private static VLabel label(LegendItem item) {
      for(int i = 0; i < item.getVisualCount(); i++) {
         if(item.getVisual(i) instanceof VLabel label) {
            return label;
         }
      }

      return fail("premise: the entry has a label");
   }

   // a bar per name with the names on colour, the legend on the right at the modern 30% cap
   private static VGraph chart(boolean ellipsis, int width, int height) {
      Object[][] rows = new Object[NAMES.length + 1][];
      rows[0] = new Object[] { "Name", "Sales" };

      for(int i = 0; i < NAMES.length; i++) {
         rows[i + 1] = new Object[] { NAMES[i], 30 - i };
      }

      EGraph graph = new EGraph();
      IntervalElement elem = new IntervalElement("Name", "Sales");
      elem.setColorFrame(new CategoricalColorFrame("Name"));
      graph.addElement(elem);
      graph.setCoordinate(new RectCoord(new CategoricalScale("Name"), new LinearScale("Sales")));
      graph.setLegendLayout(GraphConstants.RIGHT);
      graph.setLegendMaxRatio(0.3);
      graph.setLegendEllipsis(ellipsis);

      return Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, width, height);
   }

   private static final String[] NAMES = {
      "Accounts", "Field Services", "Customer Success Team", "Regional Distribution Partners",
      "Healthcare Provider Services", "Authorised Reseller Network" };
}
