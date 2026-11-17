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
package inetsoft.report.composition.region;

import inetsoft.graph.EGraph;
import inetsoft.graph.GraphConstants;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.coord.PolarCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.StackRange;
import inetsoft.graph.visual.BarVO;
import inetsoft.graph.visual.VisualObject;
import inetsoft.report.internal.Region;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Shape;
import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A pie's slice regions stay on the drawn slices when the graph has a top band.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PieRegionTopInsetTest {
   @Test
   void slicesRegionsFollowTheDrawnPieUnderATopBand() {
      double plain = regionOffset(0, GraphConstants.RIGHT);
      double banded = regionOffset(12, GraphConstants.RIGHT);

      assertEquals(plain, banded, 1, "regions move with the drawn pie");
   }

   @Test
   void aTopLegendAndABandStillLineUp() {
      double plain = regionOffset(0, GraphConstants.TOP);
      double banded = regionOffset(12, GraphConstants.TOP);

      assertEquals(plain, banded, 1, "regions move with the drawn pie");
   }

   // top of the slice regions minus the top of the drawn slices, both in screen space
   private static double regionOffset(double inset, int legendLayout) {
      EGraph graph = new EGraph();
      IntervalElement element = new IntervalElement("Sales");
      element.setColorFrame(new CategoricalColorFrame("State"));
      element.setCollisionModifier(GraphElement.MOVE_STACK);
      graph.addElement(element);
      LinearScale sales = new LinearScale("Sales");
      sales.setScaleRange(new StackRange());
      graph.setCoordinate(new PolarCoord(sales));
      graph.setLegendLayout(legendLayout);
      graph.setTopInset(inset);
      Object[][] rows = { { "State", "Sales" }, { "Alabama", 10.0 }, { "Alaska", 20.0 },
                          { "Arizona", 30.0 } };
      DefaultDataSet data = new DefaultDataSet(rows);
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(data, 0, 0, 400, 240);
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_PIE);
      ChartArea area = new ChartArea(vgraph, graph, data, null, info);

      Rectangle2D regions = null;

      for(DefaultArea child : area.getPlotArea().getAllAreas()) {
         if(child instanceof VisualObjectArea) {
            for(Region region : child.getRegions()) {
               regions = union(regions, region.getBounds());
            }
         }
      }

      Rectangle2D drawn = null;

      for(VisualObject vo : GTool.getVOs(vgraph)) {
         if(vo instanceof BarVO slice) {
            Shape shape = slice.getScreenTransform().createTransformedShape(slice.getShape());
            drawn = union(drawn, shape.getBounds2D());
         }
      }

      assertNotNull(regions, "premise: slice regions");
      assertNotNull(drawn, "premise: drawn slices");
      // graph space is y-up; flip at the graph's top to compare in screen space
      double drawnTop = vgraph.getBounds().getMaxY() - drawn.getMaxY();
      return regions.getMinY() - drawnTop;
   }

   private static Rectangle2D union(Rectangle2D a, Rectangle2D b) {
      if(a == null) {
         return (Rectangle2D) b.clone();
      }

      Rectangle2D u = (Rectangle2D) a.clone();
      Rectangle2D.union(u, b, u);
      return u;
   }
}
