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
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.DefaultTextFrame;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
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

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A value centred in a bar shows only where its box and a 3px margin fit inside the bar.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class BarVOValueFitTest {
   @Test
   void aValueThatFitsShows() {
      Laid laid = lay(5, 28, "520", true, 400, 240);

      assertBar(laid, 60, 40);
      assertTrue(shown(laid.text), "premise: a bar 60 tall and 40 wide");
   }

   @Test
   void aShortBarHidesItsValue() {
      Laid laid = lay(5, 6, "520", true, 400, 240);

      assertBar(laid, 12, 40);
      assertFalse(shown(laid.text));
   }

   @Test
   void aNarrowBarHidesItsValue() {
      Laid laid = lay(24, 30, "-1,250", true, 400, 240);

      assertBar(laid, 60, 8);
      assertFalse(shown(laid.text));
   }

   @Test
   void aRelayoutRestoresAFittingLabel() {
      Laid laid = lay(5, 28, "520", true, 400, 240);
      assertTrue(shown(laid.text), "premise: the label fits at first");
      VOText own = laid.text.stream().filter(t -> t.getElementVO() == laid.bar).findFirst()
         .orElseThrow();
      Object label = own.getLabel();

      // a longer label no longer fits, as a narrower chart would leave it
      own.setLabel("1,234,567,890,123,456");
      own.invalidate();
      laid.bar.layoutText(laid.vgraph);
      assertFalse(own.getZIndex() >= 0);

      own.setLabel(label);
      own.invalidate();
      laid.bar.layoutText(laid.vgraph);
      assertTrue(own.getZIndex() >= 0);
   }

   @Test
   void withoutTheFlagNothingIsHidden() {
      Laid laid = lay(24, 30, "-1,250", false, 400, 240);

      assertBar(laid, 60, 8);
      assertTrue(shown(laid.text));
   }

   // the bar box is near the expected size, so each case tests what its name says
   private static void assertBar(Laid laid, double height, double width) {
      Rectangle2D box = laid.bar.getBounds();

      assertEquals(height, box.getHeight(), 8, "bar height " + box);
      assertEquals(width, box.getWidth(), 8, "bar width " + box);
   }

   private static boolean shown(List<VOText> texts) {
      assertFalse(texts.isEmpty(), "premise: a value label");
      return texts.stream().allMatch(t -> t.getZIndex() >= 0);
   }

   private record Laid(VGraph vgraph, BarVO bar, List<VOText> text) {
   }

   private static Laid lay(int categories, double value, String label, boolean fit, int w, int h) {
      Object[][] rows = new Object[categories + 1][];
      rows[0] = new Object[] { "State", "Sales", "Label" };

      for(int i = 0; i < categories; i++) {
         rows[i + 1] = new Object[] { "S" + i, value, label };
      }

      IntervalElement elem = new IntervalElement("State", "Sales");
      elem.setTextFrame(new DefaultTextFrame("Label"));
      elem.setLabelPlacement(GraphConstants.CENTER);
      elem.setValueFitInside(fit);
      LinearScale scale = new LinearScale("Sales");
      scale.setMin(0);
      scale.setMax(100);
      EGraph graph = new EGraph();
      graph.addElement(elem);
      graph.setCoordinate(new RectCoord(new CategoricalScale("State"), scale));
      VGraph vgraph = Plotter.getPlotter(graph)
         .plotAndLayout(new DefaultDataSet(rows), 0, 0, w, h);
      BarVO bar = null;
      List<VOText> texts = new ArrayList<>();

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof BarVO vo && bar == null) {
            bar = vo;
         }
         else if(vgraph.getVisual(i) instanceof VOText text) {
            texts.add(text);
         }
      }

      assertNotNull(bar, "premise: a bar");
      return new Laid(vgraph, bar, texts);
   }
}
