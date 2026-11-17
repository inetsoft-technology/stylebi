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
      return new ColorLegendItem(label, value, getVisualFrame(), getMarkerShape());
   }

   /**
    * Get the shape the legend swatch should be drawn with so that it matches the mark on the
    * chart. Only a Gantt milestone point (a fixed-shape marker such as a diamond) is handled;
    * every other color legend keeps the square swatch. If the milestone shape is bound to a
    * field, one swatch can't stand for it, so the square is kept as well.
    * @return the marker shape, or null to draw the default square.
    */
   private GShape getMarkerShape() {
      GraphElement elem = getGraphElement();

      if(!(elem instanceof PointElement) ||
         !"true".equals(elem.getHint(PointElement.HINT_GANTT_MILESTONE)))
      {
         return null;
      }

      // if a bar shares this color frame, the one legend stands for the bars too, so a
      // diamond would misdescribe them
      EGraph graph = getGraph();

      for(int i = 0; graph != null && i < graph.getElementCount(); i++) {
         GraphElement other = graph.getElement(i);

         if(other == elem) {
            continue;
         }

         for(VisualFrame obj : other.getVisualFrames()) {
            if(obj == getVisualFrame()) {
               return null;
            }
         }
      }

      ShapeFrame shapes = elem.getShapeFrame();
      return shapes instanceof StaticShapeFrame ? ((StaticShapeFrame) shapes).getShape() : null;
   }
}
