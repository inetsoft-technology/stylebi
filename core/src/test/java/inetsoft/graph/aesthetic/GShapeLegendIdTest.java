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
package inetsoft.graph.aesthetic;

import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.PointElement;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.ImageShapes;
import inetsoft.uql.viewsheet.graph.aesthetic.ShapeFrameWrapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82048: GShape.getLegendId ignored fill, so a hollow and a filled shape of the same kind
 * shared a legend id, the chart dropped one of their legends, and the (All) measures pane
 * reported a common shape for measures with different shapes.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class GShapeLegendIdTest {
   @Test
   void hollowAndFilledShapesHaveDifferentLegendIds() {
      assertNotEquals(GShape.CIRCLE, GShape.FILLED_CIRCLE);
      assertNotEquals(GShape.CIRCLE.getLegendId(), GShape.FILLED_CIRCLE.getLegendId());
      assertNotEquals(GShape.SQUARE.getLegendId(), GShape.FILLED_SQUARE.getLegendId());
   }

   @Test
   void outlineDoesNotChangeTheLegendId() {
      GShape outlined = GShape.CIRCLE.create(true, false);

      // equals ignores outline, so the legend id must too
      assertEquals(GShape.CIRCLE, outlined);
      assertEquals(GShape.CIRCLE.getLegendId(), outlined.getLegendId());
   }

   @Test
   void staticShapeFrameUniqueIdsDiffer() {
      assertNotEquals(new StaticShapeFrame(GShape.CIRCLE).getUniqueId(),
                      new StaticShapeFrame(GShape.FILLED_CIRCLE).getUniqueId());
   }

   @Test
   void hollowAndFilledValueShapesGetTwoLegends() {
      assertEquals(2, legendCount(GShape.CIRCLE, GShape.FILLED_CIRCLE));
   }

   @Test
   void sameValueShapesShareOneLegend() {
      assertEquals(1, legendCount(GShape.CIRCLE, GShape.CIRCLE));
      assertEquals(1, legendCount(GShape.FILLED_CIRCLE, GShape.FILLED_CIRCLE));
   }

   @Test
   void allMeasuresPaneSeesMixedFill() {
      VSChartAggregateRef a1 = new VSChartAggregateRef();
      VSChartAggregateRef a2 = new VSChartAggregateRef();
      a1.setShapeFrame(new StaticShapeFrame(GShape.CIRCLE));
      a2.setShapeFrame(new StaticShapeFrame(GShape.FILLED_CIRCLE));
      AllChartAggregateRef all = new AllChartAggregateRef(new VSChartInfo(), new ArrayList<>(List.of(a1, a2)));

      assertNull(all.getShapeFrameWrapper(), "mixed static shapes have no common shape");
   }

   @Test
   void builtinSvgShapesStillResolveToTheirImageName() {
      for(String name : ImageShapes.getBuiltins()) {
         if(!name.endsWith(".svg")) {
            continue;
         }

         GShape fresh = new SVGShape("/inetsoft/uql/viewsheet/graph/shapes/" + name);
         assertEquals(name, ShapeFrameWrapper.getID(ImageShapes.getShape(name)), name);
         assertEquals(name, ShapeFrameWrapper.getID(fresh), name);
      }
   }

   private static int legendCount(GShape s1, GShape s2) {
      CategoricalShapeFrame f1 = new CategoricalShapeFrame("x");
      CategoricalShapeFrame f2 = new CategoricalShapeFrame("x");
      f1.setShape("v00", s1);
      f2.setShape("v00", s2);
      PointElement e1 = new PointElement("x", "y1");
      PointElement e2 = new PointElement("x", "y2");
      e1.setShapeFrame(f1);
      e2.setShapeFrame(f2);
      EGraph graph = new EGraph();
      graph.addElement(e1);
      graph.addElement(e2);
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(data(), 0, 0, 600, 400);
      return vgraph.getLegendGroup() == null ? 0 : vgraph.getLegendGroup().getLegendCount();
   }

   private static DefaultDataSet data() {
      Object[][] rows = new Object[4][];
      rows[0] = new Object[] { "x", "y1", "y2" };

      for(int i = 0; i < 3; i++) {
         rows[i + 1] = new Object[] { String.format("v%02d", i), i + 1, (i + 1) * 2 };
      }

      return new DefaultDataSet(rows);
   }
}
