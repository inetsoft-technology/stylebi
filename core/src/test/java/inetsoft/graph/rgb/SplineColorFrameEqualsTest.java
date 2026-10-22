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
package inetsoft.graph.rgb;

import inetsoft.graph.aesthetic.BluesColorFrame;
import inetsoft.graph.aesthetic.GreensColorFrame;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79091: SplineColorFrame.equals() used to compare only the concrete class, never the
 * bound field, so two SplineColorFrame subclass instances (e.g. two BluesColorFrame, StyleBI's
 * hardcoded default measure color frame) bound to different measures were treated as the same
 * frame -- causing EGraph.getVisualFrames() to merge them into a single legend instead of giving
 * each its own, e.g. a network chart's node-color and edge-color legends.
 */
@Tag("core")
class SplineColorFrameEqualsTest {
   @Test
   void sameClassDifferentFieldAreNotEqual() {
      BluesColorFrame frame1 = new BluesColorFrame();
      frame1.setField("Total");
      BluesColorFrame frame2 = new BluesColorFrame();
      frame2.setField("Quantity Purchased");

      assertNotEquals(frame1, frame2);
      assertNotEquals(frame2, frame1);
   }

   @Test
   void sameClassSameFieldAreEqual() {
      BluesColorFrame frame1 = new BluesColorFrame();
      frame1.setField("Total");
      BluesColorFrame frame2 = new BluesColorFrame();
      frame2.setField("Total");

      assertEquals(frame1, frame2);
      // equal objects must hash equally.
      assertEquals(frame1.hashCode(), frame2.hashCode());
   }

   @Test
   void sameClassBothUnboundAreEqual() {
      BluesColorFrame frame1 = new BluesColorFrame();
      BluesColorFrame frame2 = new BluesColorFrame();

      assertEquals(frame1, frame2);
      assertEquals(frame1.hashCode(), frame2.hashCode());
   }

   @Test
   void differentSplineColorFrameSubclassesAreNotEqualEvenWithTheSameField() {
      BluesColorFrame blues = new BluesColorFrame();
      blues.setField("Total");
      GreensColorFrame greens = new GreensColorFrame();
      greens.setField("Total");

      assertNotEquals(blues, greens);
   }
}
