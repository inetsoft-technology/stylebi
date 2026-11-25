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
package inetsoft.graph.coord;

import inetsoft.graph.AxisSpec;
import inetsoft.graph.EGraph;
import inetsoft.graph.GraphConstants;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.element.LineElement;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.internal.ICoordinate;
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

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The grid a primary axis draws when the secondary axis has none: its lines at the plot edges
 * are left out only when its spec asks for it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RectCoordPrimaryGridTest {
   @Test
   void aTrimmedPrimaryGridLeavesOutTheEdgeLines() {
      VGraph vgraph = layout(true, GraphConstants.NONE);
      Rectangle2D plot = vgraph.getPlotBounds();
      List<Double> ys = gridYs(left(vgraph));

      assertEquals(4, ys.size(), "the lines at 100 to 400: " + ys);

      for(double y : ys) {
         assertTrue(y > plot.getMinY() + 1 && y < plot.getMaxY() - 1,
                    "line at " + y + " on the edge of " + plot);
      }
   }

   @Test
   void anUntrimmedPrimaryGridKeepsTheEdgeLines() {
      VGraph vgraph = layout(false, GraphConstants.NONE);
      Rectangle2D plot = vgraph.getPlotBounds();
      List<Double> ys = gridYs(left(vgraph));

      assertEquals(6, ys.size(), "the lines at 0 to 500: " + ys);
      assertTrue(ys.stream().anyMatch(y -> Math.abs(y - plot.getMinY()) < 0.5), ys + " " + plot);
      assertTrue(ys.stream().anyMatch(y -> Math.abs(y - plot.getMaxY()) < 0.5), ys + " " + plot);
   }

   @Test
   void aHiddenSecondaryAxisStillPlacesThePrimaryGridInThePlot() {
      VGraph vgraph = layout(true, GraphConstants.NONE, AxisSpec.AXIS_NONE);
      Rectangle2D plot = vgraph.getPlotBounds();
      DefaultAxis primary = left(vgraph);

      assertEquals(layout(false, GraphConstants.NONE, AxisSpec.AXIS_NONE).getPlotBounds(), plot,
                   "the hidden axis takes no room");
      assertEquals(4, primary.getGridLineCount(), "the lines at 100 to 400");

      for(int i = 0; i < primary.getGridLineCount(); i++) {
         Rectangle2D line = primary.getGridLine(i).getShape().getBounds2D();
         assertTrue(line.getMinX() >= plot.getMinX() - 1 && line.getMaxX() <= plot.getMaxX() + 1 &&
                    line.getY() > plot.getMinY() + 1 && line.getY() < plot.getMaxY() - 1,
                    "line " + line + " outside " + plot);
      }
   }

   @Test
   void aSecondaryGridStillDrawsAndIsTrimmed() {
      VGraph vgraph = layout(true, GraphConstants.THIN_LINE);
      RectCoord coord = (RectCoord) vgraph.getCoordinate();

      assertEquals(0, left(vgraph).getGridLineCount());
      assertEquals(3, coord.getAxisAt(ICoordinate.RIGHT_AXIS).getGridLineCount(),
                   "the lines at 0.1 to 0.3");
   }

   private static DefaultAxis left(VGraph vgraph) {
      return ((RectCoord) vgraph.getCoordinate()).getAxisAt(ICoordinate.LEFT_AXIS);
   }

   private static List<Double> gridYs(DefaultAxis axis) {
      List<Double> ys = new ArrayList<>();

      for(int i = 0; i < axis.getGridLineCount(); i++) {
         ys.add(axis.getGridLine(i).getShape().getBounds2D().getY());
      }

      return ys;
   }

   // Quantity bars on 0 to 500 by 100, an Other line on a secondary 0 to 0.4 by 0.1
   private static VGraph layout(boolean trimmed, int secondaryGrid) {
      return layout(trimmed, secondaryGrid, AxisSpec.AXIS_SINGLE);
   }

   private static VGraph layout(boolean trimmed, int secondaryGrid, int secondaryStyle) {
      LinearScale y = new LinearScale("Quantity");
      y.setMin(0);
      y.setMax(500);
      y.setIncrement(100);
      y.getAxisSpec().setGridStyle(GraphConstants.THIN_LINE);
      y.getAxisSpec().setEdgeGridTrimmed(trimmed);
      LinearScale y2 = new LinearScale("Other");
      y2.setMin(0);
      y2.setMax(0.4);
      y2.setIncrement(0.1);
      y2.getAxisSpec().setGridStyle(secondaryGrid);
      y2.getAxisSpec().setAxisStyle(secondaryStyle);
      RectCoord coord = new RectCoord(new CategoricalScale("State"), y);
      coord.setYScale2(y2);
      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("State", "Quantity"));
      graph.addElement(new LineElement("State", "Other"));
      graph.setCoordinate(coord);
      Object[][] rows = { { "State", "Quantity", "Other" }, { "A", 100.0, 0.1 },
                          { "B", 450.0, 0.3 }, { "C", 250.0, 0.2 } };

      return Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, 400, 240);
   }
}
