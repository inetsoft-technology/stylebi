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
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import inetsoft.graph.aesthetic.VisualFrame;
import java.awt.Color;
import java.util.WeakHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("core")
class CategoricalColorFrameContextTest {
   private static Viewsheet viewsheet() {
      Viewsheet vs = mock(Viewsheet.class);
      Map<SharedFrameParameters, VisualFrame> frames = new WeakHashMap<>();
      when(vs.getSharedFrames()).thenReturn(frames);
      return vs;
   }

   @Test
   void bindSwapsInTheViewsheetsSharedFramesAndResetsDimensionColors() {
      Viewsheet vs = viewsheet();
      CategoricalColorFrame frame = new CategoricalColorFrame();

      try(CategoricalColorFrameContext.Scope outer = CategoricalColorFrameContext.bind(vs)) {
         CategoricalColorFrameContext context = CategoricalColorFrameContext.getContext();
         context.setDimensionColors(Map.of("Games", Color.RED));
         context.addSharedFrame("Category", frame, null);
         assertSame(frame, vs.getSharedFrames().values().iterator().next(),
                    "shared frames must be the viewsheet's own map");

         try(CategoricalColorFrameContext.Scope inner = CategoricalColorFrameContext.bind(vs)) {
            assertTrue(CategoricalColorFrameContext.getContext().getDimensionColors().isEmpty(),
                       "a new binding starts with no dimension colors");
            assertSame(frame, CategoricalColorFrameContext.getContext()
               .getSharedFrame("Category", null));
         }

         assertEquals(Map.of("Games", Color.RED),
                      CategoricalColorFrameContext.getContext().getDimensionColors(),
                      "closing the inner scope restores the outer binding");
      }
   }

   @Test
   void bindingNullGivesAnEmptyContext() {
      try(CategoricalColorFrameContext.Scope scope = CategoricalColorFrameContext.bind(null)) {
         CategoricalColorFrameContext context = CategoricalColorFrameContext.getContext();
         assertTrue(context.getDimensionColors().isEmpty());
         assertNull(context.getSharedFrame("Category", null));
      }
   }

   @Test
   void anUnboundReadSeesNothing() {
      CategoricalColorFrameContext context = CategoricalColorFrameContext.getContext();
      context.setDimensionColors(Map.of("Games", Color.RED));
      context.addSharedFrame("Category", new CategoricalColorFrame(), null);

      CategoricalColorFrameContext after = CategoricalColorFrameContext.getContext();
      assertTrue(after.getDimensionColors().isEmpty());
      assertNull(after.getSharedFrame("Category", null));
   }

   @Test
   void aThreadHoldsNoStateOnceTheScopeThatBoundItCloses() {
      Viewsheet vs = viewsheet();

      try(CategoricalColorFrameContext.Scope scope = CategoricalColorFrameContext.bind(vs)) {
         CategoricalColorFrameContext.getContext().setDimensionColors(Map.of("Games", Color.RED));
         CategoricalColorFrameContext.getContext()
            .addSharedFrame("Category", new CategoricalColorFrame(), null);
      }

      assertTrue(CategoricalColorFrameContext.getContext().getDimensionColors().isEmpty());
      assertNull(CategoricalColorFrameContext.getContext().getSharedFrame("Category", null));
   }

   /**
    * The root cause of bug 78989: one pooled thread works on viewsheet 1 and then on viewsheet 2.
    * Nothing pinned in viewsheet 1 may be visible while operating on viewsheet 2, bound or not.
    */
   @Test
   void viewsheetOnePinsAreNotVisibleToViewsheetTwoOnTheSameThread() {
      Viewsheet vs1 = viewsheet();
      Viewsheet vs2 = viewsheet();

      try(CategoricalColorFrameContext.Scope scope = CategoricalColorFrameContext.bind(vs1)) {
         CategoricalColorFrameContext.getContext().setDimensionColors(Map.of("Games", Color.RED));
         CategoricalColorFrameContext.getContext()
            .addSharedFrame("Category", new CategoricalColorFrame(), null);
      }

      assertNull(CategoricalColorFrameContext.getContext().getSharedFrame("Category", null),
                 "unbound read must not see viewsheet 1");

      try(CategoricalColorFrameContext.Scope scope = CategoricalColorFrameContext.bind(vs2)) {
         assertNull(CategoricalColorFrameContext.getContext().getSharedFrame("Category", null));
         assertTrue(CategoricalColorFrameContext.getContext().getDimensionColors().isEmpty());
      }

      assertEquals(1, vs1.getSharedFrames().size(), "viewsheet 1 keeps its own frame");
      assertTrue(vs2.getSharedFrames().isEmpty());
   }
}
