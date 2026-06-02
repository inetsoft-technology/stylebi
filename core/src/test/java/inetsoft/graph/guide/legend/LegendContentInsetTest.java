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
import inetsoft.graph.GraphConstants;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.aesthetic.CategoricalShapeFrame;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the bottom inset in {@link Legend#getContentPreferredBounds()}. The content bounds
 * (which size the viewer's scrollable content tile) must leave a gap below the last row so its
 * symbol doesn't sit flush against the bottom border when the content is scrolled to the end.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class LegendContentInsetTest {
   @Test
   void contentBoundsLeaveBottomInsetBelowLastRow() {
      DefaultDataSet data = new DefaultDataSet(new Object[][]{
         { "Cat", "State", "Region", "m1" },
         { "A", "CA", "West",  10.0 },
         { "B", "NY", "East",  20.0 },
         { "C", "TX", "South", 15.0 },
         { "D", "FL", "South", 25.0 },
         { "E", "WA", "West",  30.0 },
         { "F", "IL", "Mid",   12.0 },
      });

      CategoricalScale xScale = new CategoricalScale("Cat");
      xScale.init(data);
      LinearScale yScale = new LinearScale("m1");
      yScale.init(data);

      IntervalElement element = new IntervalElement();
      element.addDim("Cat");
      element.addVar("m1");

      CategoricalColorFrame color = new CategoricalColorFrame("State");
      color.init(data);
      color.getLegendSpec().setSymbolSize(50);
      color.getLegendSpec().setBorder(GraphConstants.THIN_LINE);
      element.setColorFrame(color);

      CategoricalShapeFrame shape = new CategoricalShapeFrame("Region");
      shape.init(data);
      shape.getLegendSpec().setSymbolSize(50);
      shape.getLegendSpec().setBorder(GraphConstants.THIN_LINE);
      element.setShapeFrame(shape);

      EGraph egraph = new EGraph();
      egraph.addElement(element);
      egraph.setCoordinate(new RectCoord(xScale, yScale));
      egraph.setLegendLayout(GraphConstants.RIGHT);

      VGraph vgraph = Plotter.getPlotter(egraph).plotAndLayout(data, 0, 0, 600, 260);
      LegendGroup group = vgraph.getLegendGroup();

      for(int i = 0; i < group.getVisualCount(); i++) {
         Legend legend = (Legend) group.getVisual(i);
         Rectangle2D content = legend.getContentPreferredBounds();
         double lastRowBottom = Double.MAX_VALUE;

         for(LegendItem[] row : legend.getItems()) {
            for(LegendItem it : row) {
               if(it != null) {
                  lastRowBottom = Math.min(lastRowBottom, it.getBounds().getY());
               }
            }
         }

         // content bottom must sit below the last row by at least a few px (the bottom inset)
         assertTrue(lastRowBottom - content.getY() >= 3.5,
            "legend[" + i + "] missing bottom inset: lastRowBottom=" + lastRowBottom
               + " contentBottom=" + content.getY());
      }
   }
}
