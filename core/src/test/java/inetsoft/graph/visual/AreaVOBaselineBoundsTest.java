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
package inetsoft.graph.visual;

import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.Visualizable;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.AreaElement;
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

import java.awt.geom.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies AreaVO.getBounds() extends to the fill baseline, not just the data points.
 * A bounds that stops at the lowest data point lets VGraph.paintVisualizables cull the
 * area from a baseline-band tile when a tall plot is sliced into rows, leaving an empty
 * strip above the baseline (the maximized-area-chart gap).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AreaVOBaselineBoundsTest {
   @Test
   void boundsExtendBelowDataPointsToBaseline() {
      // Values well above the 0 baseline so the fill spans a large band below the data points.
      DefaultDataSet data = new DefaultDataSet(new Object[][]{
         { "x",  "y"   },
         { "A",  50.0  },
         { "B",  70.0  },
         { "C",  55.0  },
         { "D",  65.0  },
      });

      CategoricalScale xScale = new CategoricalScale("x");
      xScale.init(data);

      LinearScale yScale = new LinearScale("y");
      yScale.init(data);

      AreaElement element = new AreaElement();
      element.addDim("x");
      element.addVar("y");

      EGraph egraph = new EGraph();
      egraph.addElement(element);
      egraph.setCoordinate(new RectCoord(xScale, yScale));

      VGraph vgraph = Plotter.getPlotter(egraph).plotAndLayout(data, 0, 0, 400, 400);
      assertNotNull(vgraph, "plot/layout must produce a graph");

      AreaVO area = findAreaVO(vgraph);
      assertNotNull(area, "an AreaVO must be produced for the area element");

      Rectangle2D dataBounds = pointBounds(area.getTransformedPoints());
      Rectangle2D areaBounds = area.getBounds();
      assertNotNull(dataBounds, "data points must have a bounding box");
      assertNotNull(areaBounds, "area must have bounds");

      double eps = 0.5;
      // The area's bounds must still cover the data curve...
      assertTrue(areaBounds.getMinX() <= dataBounds.getMinX() + eps &&
                 areaBounds.getMaxX() >= dataBounds.getMaxX() - eps &&
                 areaBounds.getMinY() <= dataBounds.getMinY() + eps &&
                 areaBounds.getMaxY() >= dataBounds.getMaxY() - eps,
                 "area bounds " + areaBounds + " must contain the data-point bounds " + dataBounds);

      // ...and extend far past it toward the baseline. Values 50-70 sit in the top of a
      // [0..max] scale, so the fill-to-baseline band is several times the data-point band.
      // The >2x threshold separates the real baseline extension from the small per-point
      // line-radius padding that super.getBounds() already adds (without the fix the area
      // would only be that padding taller, and this would fail).
      assertTrue(areaBounds.getHeight() > dataBounds.getHeight() * 2.0,
                 "area bounds height " + areaBounds.getHeight() +
                 " must be well beyond the data-point band height " + dataBounds.getHeight() +
                 " (bounds must reach the fill baseline)");
   }

   private static AreaVO findAreaVO(VGraph vgraph) {
      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         Visualizable v = vgraph.getVisual(i);

         if(v instanceof AreaVO) {
            return (AreaVO) v;
         }
      }

      return null;
   }

   private static Rectangle2D pointBounds(Point2D[] pts) {
      Rectangle2D bounds = null;

      for(Point2D pt : pts) {
         if(pt == null || Double.isNaN(pt.getX()) || Double.isNaN(pt.getY())) {
            continue;
         }

         Rectangle2D r = new Rectangle2D.Double(pt.getX(), pt.getY(), 0, 0);
         bounds = bounds == null ? r : bounds.createUnion(r);
      }

      return bounds;
   }
}
