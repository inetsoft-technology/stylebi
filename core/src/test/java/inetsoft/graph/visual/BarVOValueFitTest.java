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

import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A value centred in a bar shows only where its box fits the bar with 3px above and below it and
 * runs at most 1px past each side.
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
      assertTrue(hidden(laid.text));
   }

   @Test
   void aNarrowBarHidesItsValue() {
      Laid laid = lay(24, 30, "-1,250", true, 400, 240);

      assertBar(laid, 60, 8);
      assertTrue(hidden(laid.text));
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
   void aLabelNotCentredIsNeverFitted() {
      Laid laid = lay(5, 6, "520", true, 400, 240, GraphConstants.TOP);

      assertBar(laid, 12, 40);
      assertTrue(shown(laid.text));
   }

   @Test
   void aLabelMayRunOnePixelPastEachSide() {
      Laid laid = lay(5, 50, "520", true, 400, 240);
      VOText own = own(laid);
      Dimension2D label = labelSize(laid, own);

      assertTrue(fits(laid, own, label.getWidth() - 2, label.getHeight() + 20),
                 "label " + label + " on a bar 2px narrower");
      assertFalse(fits(laid, own, label.getWidth() - 2.1, label.getHeight() + 20),
                  "label " + label + " on a bar 2.1px narrower");
   }

   @Test
   void aLabelNeedsThreePixelsAboveAndBelow() {
      Laid laid = lay(5, 50, "520", true, 400, 240);
      VOText own = own(laid);
      Dimension2D label = labelSize(laid, own);

      assertTrue(fits(laid, own, label.getWidth(), label.getHeight() + 6),
                 "label " + label + " on a bar 6px taller");
      assertFalse(fits(laid, own, label.getWidth(), label.getHeight() + 5.9),
                  "label " + label + " on a bar 5.9px taller");
   }

   @Test
   void aLaidOutBarNeedsThreePixelsAboveAndBelow() {
      double perUnit = lay(5, 50, "520", true, 400, 240).bar.getBounds().getHeight() / 50;
      Laid probe = lay(5, 50, "520", true, 400, 240);
      double label = probe.text.stream().filter(t -> t.getElementVO() == probe.bar).findFirst()
         .orElseThrow().getPreferredHeight();

      Laid fits = lay(5, (label + 6 + 0.01) / perUnit, "520", true, 400, 240);
      Laid short1 = lay(5, (label + 6 - 1) / perUnit, "520", true, 400, 240);

      assertEquals(label + 6, fits.bar.getBounds().getHeight(), 0.5, "premise: bar height");
      assertTrue(shown(fits.text), "bar " + fits.bar.getBounds().getHeight() + " label " + label);
      assertTrue(hidden(short1.text),
                 "bar " + short1.bar.getBounds().getHeight() + " label " + label);
   }

   @Test
   void withoutTheFlagNothingIsHidden() {
      // few enough categories that the labels do not overlap each other; overlapping labels are
      // thinned out with a random jitter, which would hide one at random
      Laid laid = lay(10, 30, "-1,250", false, 400, 240);

      assertBar(laid, 60, 18);
      assertTrue(hidden(lay(10, 30, "-1,250", true, 400, 240).text),
                 "premise: the same narrow bar hides its value when the flag is on");
      assertTrue(shown(laid.text));
   }

   private static VOText own(Laid laid) {
      return laid.text.stream().filter(t -> t.getElementVO() == laid.bar).findFirst()
         .orElseThrow();
   }

   // the label size the fit test measures
   private static Dimension2D labelSize(Laid laid, VOText text) {
      fits(laid, text, 1000, 1000);
      return text.getSize();
   }

   // centre the label on a bar box of the given size and report whether it shows
   private static boolean fits(Laid laid, VOText text, double width, double height) {
      laid.bar.layoutText0(laid.vgraph, GraphConstants.CENTER,
                           new Rectangle2D.Double(20, 20, width, height), false, text);
      return text.getZIndex() >= 0;
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

   private static boolean hidden(List<VOText> texts) {
      assertFalse(texts.isEmpty(), "premise: a value label");
      return texts.stream().noneMatch(t -> t.getZIndex() >= 0);
   }

   private record Laid(VGraph vgraph, BarVO bar, List<VOText> text) {
   }

   private static Laid lay(int categories, double value, String label, boolean fit, int w, int h) {
      return lay(categories, value, label, fit, w, h, GraphConstants.CENTER);
   }

   private static Laid lay(int categories, double value, String label, boolean fit, int w, int h,
                           int placement)
   {
      Object[][] rows = new Object[categories + 1][];
      rows[0] = new Object[] { "State", "Sales", "Label" };

      for(int i = 0; i < categories; i++) {
         rows[i + 1] = new Object[] { "S" + i, value, label };
      }

      IntervalElement elem = new IntervalElement("State", "Sales");
      elem.setTextFrame(new DefaultTextFrame("Label"));
      elem.setLabelPlacement(placement);
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
