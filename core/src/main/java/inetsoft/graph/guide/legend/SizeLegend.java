/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph.guide.legend;

import inetsoft.graph.EGraph;
import inetsoft.graph.aesthetic.*;

import java.awt.*;
import java.util.Objects;

/**
 * This class is the legend for showing size aesthetics.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class SizeLegend extends Legend {
   /**
    * Create a legend for the specified frame.
    * @param frame the legend frame contain legend infomations.
    */
   public SizeLegend(VisualFrame frame, EGraph graph) {
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
      return new SizeLegendItem(label, value, getVisualFrame());
   }

   @Override
   protected Color getElementColor(Object val) {
      // mapping size to color doesn't make sense. (50567)
      if(getGraphElement() != null) {
         ColorFrame color2 = getGraphElement().getColorFrame();

         if(color2 instanceof ColorValueColorFrame &&
            !Objects.equals(color2.getField(), getVisualFrame().getField()))
         {
            return null;
         }
      }

      return super.getElementColor(val);
   }
}
