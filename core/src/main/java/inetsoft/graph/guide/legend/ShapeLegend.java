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
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.graph.element.GraphElement;

/**
 * This class is the legend for showing shape aesthetics.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class ShapeLegend extends Legend {
   /**
    * Create a legend for the specified frame.
    * @param frame the legend frame contain legend infomations.
    */
   public ShapeLegend(VisualFrame frame, EGraph graph) {
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
      GraphElement elem = getGraphElement();
      return new ShapeLegendItem(label, value, getVisualFrame(),
                                 elem != null && elem.getColorFrame() != null &&
                                    elem.getColorFrame().getField() != null);
   }
}
