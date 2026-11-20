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
package inetsoft.graph.guide.legend;

import inetsoft.graph.EGraph;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.PointElement;

/**
 * This class is the legend for showing color aesthetics.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class ColorLegend extends Legend {
   /**
    * Create a legend for the specified frame.
    * @param frame the legend frame contain legend infomations.
    */
   public ColorLegend(VisualFrame frame, EGraph graph) {
      super(frame, graph);
   }

   /**
    * Create a legend item to display a single value.
    * @param label the item show string.
    * @param value the symbol's type.
    * @return legend item been created.
    */
   @Override
   protected LegendItem createLegendItem(Object label, Object value) {
      return new ColorLegendItem(label, value, getVisualFrame(), getMarkerShape(value));
   }

   /**
    * Get the shape the legend swatch should be drawn with so that it matches the mark on the
    * chart. Only a Gantt milestone point (a fixed-shape marker such as a diamond) is handled;
    * every other color legend keeps the square swatch. If the milestone shape is bound to a
    * field, one swatch can't stand for it, so the square is kept as well.
    *
    * The milestone is looked up in the whole graph. When the entry stands for a measure (a
    * legend keyed by measure name, e.g. bars and milestone sharing one legend), it is the
    * milestone's entry only if the value is the milestone's own measure, so the bars' entry
    * keeps the square. Otherwise (the entry is a field value), the milestone counts as this
    * legend's when it owns the legend's frame or colors itself with that frame instance.
    * @param value the value the legend item stands for.
    * @return the marker shape, or null to draw the default square.
    */
   private GShape getMarkerShape(Object value) {
      EGraph graph = getGraph();
      boolean measureEntry = isMeasure(graph, value);

      for(int i = 0; graph != null && i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);

         if(!(elem instanceof PointElement) ||
            !"true".equals(elem.getHint(PointElement.HINT_GANTT_MILESTONE)))
         {
            continue;
         }

         boolean mine = measureEntry ? hasVar(elem, value)
            : elem == getGraphElement() || elem.getColorFrame() == getVisualFrame();

         // only the entry that stands for the milestone is drawn with its shape; the bars'
         // entries (and any other color legend in the chart) keep the square
         if(!mine) {
            continue;
         }

         ShapeFrame shapes = elem.getShapeFrame();

         // a viewsheet chart wraps the fixed shapes of its measures in a categorical frame
         // keyed by measure name (see VSShapeFrameStrategy.createCombinedFrame), while a
         // plain chart uses a static frame. in both cases the shape of the milestone's own
         // measure is the shape it is drawn with, as long as the frame is not field-bound.
         if(shapes != null && shapes.getField() == null) {
            GShape shape = shapes.getShape(elem.getVarCount() > 0 ? elem.getVar(0) : null);

            if(shape != null) {
               return shape;
            }
         }
      }

      return null;
   }

   /**
    * Check if the value is the measure (variable) of any element in the graph.
    */
   private static boolean isMeasure(EGraph graph, Object value) {
      for(int i = 0; graph != null && i < graph.getElementCount(); i++) {
         if(hasVar(graph.getElement(i), value)) {
            return true;
         }
      }

      return false;
   }

   private static boolean hasVar(GraphElement elem, Object value) {
      if(value == null) {
         return false;
      }

      for(String var : elem.getVars()) {
         if(var.equals(value.toString())) {
            return true;
         }
      }

      return false;
   }
}
