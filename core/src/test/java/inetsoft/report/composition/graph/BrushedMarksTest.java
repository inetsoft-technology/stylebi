/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.graph.aesthetic.CompositeColorFrame;
import inetsoft.graph.aesthetic.StaticColorFrame;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.filter.Highlight;
import inetsoft.report.filter.TextHighlight;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class BrushedMarksTest {
   @Test
   void aRowMatchingTheBrushConditionIsBrushed() {
      assertTrue(BrushedMarks.isBrushed(sourceComposite(0), data(), 0));
   }

   @Test
   void aRowNotMatchingTheBrushConditionIsNotBrushed() {
      assertFalse(BrushedMarks.isBrushed(sourceComposite(0), data(), 1));
   }

   @Test
   void aCompositeWithNoHighlightFrameIsNotBrushed() {
      CompositeColorFrame frame = new CompositeColorFrame();
      frame.addFrame(new StaticColorFrame(Color.BLUE));
      assertFalse(BrushedMarks.isBrushed(frame, data(), 0));
   }

   @Test
   void aNonCompositeFrameIsNotBrushed() {
      assertFalse(BrushedMarks.isBrushed(new StaticColorFrame(Color.BLUE), data(), 0));
   }

   @Test
   void aNullFrameOrDataIsNotBrushed() {
      assertFalse(BrushedMarks.isBrushed(null, data(), 0));
      assertFalse(BrushedMarks.isBrushed(sourceComposite(0), null, 0));
   }

   @Test
   void aNullGeometryIsNotBrushed() {
      assertFalse(BrushedMarks.isBrushed(null));
   }

   @Test
   void brushedMarksSortAfterUnbrushedOnes() {
      assertEquals(1, BrushedMarks.order(true, false));
      assertEquals(-1, BrushedMarks.order(false, true));
      assertEquals(0, BrushedMarks.order(true, true));
      assertEquals(0, BrushedMarks.order(false, false));
   }

   @Test
   void aTargetChartLayerAnswersFromItsMarker() {
      CompositeColorFrame brushedLayer = new CompositeColorFrame();
      brushedLayer.addFrame(new CompanionBrushColorFrame(
         null, true, new StaticColorFrame(Color.BLUE), false));
      assertTrue(BrushedMarks.isBrushed(brushedLayer, data(), 0));

      CompositeColorFrame allDataLayer = new CompositeColorFrame();
      allDataLayer.addFrame(new CompanionBrushColorFrame(
         null, false, new StaticColorFrame(Color.BLUE), false));
      assertFalse(BrushedMarks.isBrushed(allDataLayer, data(), 0));
   }

   @Test
   void aMarkedSourceChartAnswersPerRow() {
      HLColorFrame predicate = new HLColorFrame(null, null, null) {
         @Override
         public Highlight getHighlight(DataSet data, int row) {
            return row == 0 ? new TextHighlight() : null;
         }
      };

      CompositeColorFrame composite = new CompositeColorFrame();
      composite.addFrame(new CompanionBrushColorFrame(
         predicate, false, new StaticColorFrame(Color.BLUE), false));
      composite.addFrame(new StaticColorFrame(Color.BLUE));

      assertTrue(BrushedMarks.isBrushed(composite, data(), 0));
      assertFalse(BrushedMarks.isBrushed(composite, data(), 1));
   }

   @Test
   void aLegacyTargetCompositeHasNoMarkerAndIsNeverBrushed() {
      // the shape applyBrushing(ColorFrame, ColorFrame) builds when companionBrushing is off:
      // a flat highlight/dim colour plus the base palette, no marker frame of either kind.
      CompositeColorFrame legacy = new CompositeColorFrame();
      legacy.addFrame(new StaticColorFrame(Color.RED));
      legacy.addFrame(new StaticColorFrame(Color.BLUE));

      assertFalse(BrushedMarks.hasMarker(legacy));
      assertFalse(BrushedMarks.isBrushed(legacy, data(), 0));
      assertFalse(BrushedMarks.isBrushed(legacy, data(), 1));
   }

   @Test
   void aCompositeWithACompanionFrameHasAMarkerAndAnswersFromIt() {
      CompositeColorFrame composite = new CompositeColorFrame();
      composite.addFrame(new CompanionBrushColorFrame(
         null, true, new StaticColorFrame(Color.BLUE), false));
      composite.addFrame(new StaticColorFrame(Color.BLUE));

      assertTrue(BrushedMarks.hasMarker(composite));
      assertTrue(BrushedMarks.isBrushed(composite, data(), 0));
   }

   private DataSet data() {
      return new DefaultDataSet(new Object[][] {
         { "Region", "Sales" },
         { "East", 100.0 },
         { "West", 5.0 }
      });
   }

   // the composite a brushing-source chart carries: HLColorFrame over the real palette.
   // getHighlight is stubbed so the test does not depend on the condition-building API.
   private CompositeColorFrame sourceComposite(int brushedRow) {
      HLColorFrame hframe = new HLColorFrame(null, null, null) {
         @Override
         public Highlight getHighlight(DataSet data, int row) {
            return row == brushedRow ? new TextHighlight() : null;
         }
      };

      CompositeColorFrame composite = new CompositeColorFrame();
      composite.addFrame(hframe);
      composite.addFrame(new StaticColorFrame(Color.BLUE));
      return composite;
   }
}
