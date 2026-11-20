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
package inetsoft.report.composition.graph;

import inetsoft.graph.EGraph;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.RelationElement;
import inetsoft.uql.viewsheet.graph.aesthetic.LinearColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.VisualFrameWrapper;
import inetsoft.uql.viewsheet.internal.VizContext;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A marked chart draws its seeded Teal as a ramp that turns diverging when its data crosses zero.
 * The stored frame stays the seeded Teal; only the render clone is replaced.
 */
public final class DivergingRamps {
   private DivergingRamps() {
   }

   /**
    * The frame to draw for a render clone of a colour frame: an auto-diverging Teal in place of
    * an unflagged seeded Teal on a marked chart, otherwise the frame itself.
    */
   public static VisualFrame substitute(VisualFrame frame, VisualFrameWrapper wrapper,
                                        VizContext ctx)
   {
      // exact class, so a subclass or another ramp draws as stored
      if(ctx == null || !ctx.modern || frame == null || frame.getClass() != TealColorFrame.class) {
         return frame;
      }

      // an author's ramp pick draws as picked
      if(wrapper instanceof LinearColorFrameWrapper &&
         ((LinearColorFrameWrapper) wrapper).isAuthorRamp())
      {
         return frame;
      }

      return AutoDivergingColorFrame.from((TealColorFrame) frame);
   }

   /**
    * The fields whose auto-diverging frame draws the diverging ramp, among each element's colour
    * frame and each relation element's node colour frame.
    */
   public static Set<String> drawnFields(EGraph graph) {
      Set<String> fields = new LinkedHashSet<>();

      for(int i = 0; i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);
         collect(elem.getColorFrame(), fields);

         if(elem instanceof RelationElement) {
            collect(((RelationElement) elem).getNodeColorFrame(), fields);
         }
      }

      return fields;
   }

   // highlight and brushing wrap the ramp in a composite
   private static void collect(VisualFrame frame, Set<String> fields) {
      if(frame instanceof CompositeColorFrame) {
         CompositeColorFrame composite = (CompositeColorFrame) frame;

         for(int i = 0; i < composite.getFrameCount(); i++) {
            collect(composite.getFrame(i), fields);
         }
      }
      else if(frame instanceof AutoDivergingColorFrame &&
         ((AutoDivergingColorFrame) frame).isDiverging() && frame.getField() != null)
      {
         fields.add(frame.getField());
      }
   }
}
