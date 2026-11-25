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
package inetsoft.graph.element;

import inetsoft.graph.EGraph;
import inetsoft.graph.GGraph;
import inetsoft.graph.aesthetic.TreemapSizeFrame;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.geometry.Geometry;
import inetsoft.graph.geometry.TreemapGeometry;
import inetsoft.graph.treemap.Rect;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end treemap layout: a real TreemapElement/EGraph/GGraph pipeline, reading back the
 * rectangles assigned to the geometries.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class TreemapElementLayoutTest {
   private static final double PLOT = Coordinate.GWIDTH * Coordinate.GHEIGHT;
   private static final double EPS = 1e-6;

   private static List<TreemapGeometry> layout(TreemapElement element, Object[][] rows) {
      DefaultDataSet data = new DefaultDataSet(rows);
      TreemapSizeFrame size = new TreemapSizeFrame("Size");
      size.init(data);
      element.setSizeFrame(size);

      EGraph egraph = new EGraph();
      egraph.addElement(element);
      RectCoord coord = new RectCoord();
      egraph.setCoordinate(coord);
      GGraph ggraph = egraph.createGGraph(coord, data);
      List<TreemapGeometry> result = new ArrayList<>();

      for(int i = 0; i < ggraph.getGeometryCount(); i++) {
         Geometry g = ggraph.getGeometry(i);

         if(g instanceof TreemapGeometry) {
            result.add((TreemapGeometry) g);
         }
      }

      return result;
   }

   private static double area(TreemapGeometry g) {
      Rect r = g.getMapItem().getBounds();
      return r.w * r.h;
   }

   @ParameterizedTest
   @EnumSource(TreemapElement.Algorithm.class)
   void leafAreasAreProportionalToSizeAndFillThePlot(TreemapElement.Algorithm algorithm) {
      TreemapElement element = new TreemapElement("Name");
      element.setAlgorithm(algorithm);
      List<TreemapGeometry> leaves = layout(element, new Object[][] {
         { "Name", "Size" }, { "A", 1 }, { "B", 2 }, { "C", 3 }, { "D", 4 } });

      assertEquals(4, leaves.size());
      double sum = 0;

      for(TreemapGeometry g : leaves) {
         double size = g.getMapItem().getSize();
         Rect r = g.getMapItem().getBounds();
         sum += area(g);
         assertEquals(size / 10, area(g) / PLOT, 1e-9, "leaf of size " + size);
         assertTrue(r.x >= -EPS && r.y >= -EPS && r.x + r.w <= Coordinate.GWIDTH + EPS &&
                    r.y + r.h <= Coordinate.GHEIGHT + EPS, r.toString());
      }

      assertEquals(PLOT, sum, PLOT * 1e-9);
   }

   @Test
   void groupRectIsTheUnionOfItsLeavesAreas() {
      TreemapElement element = new TreemapElement("Group", "Name");
      element.setAlgorithm(TreemapElement.Algorithm.SQUARIFIED);
      List<TreemapGeometry> all = layout(element, new Object[][] {
         { "Group", "Name", "Size" },
         { "G1", "a", 3 }, { "G1", "b", 2 }, { "G1", "c", 5 },
         { "G2", "d", 4 }, { "G2", "e", 6 } });

      List<TreemapGeometry> groups = new ArrayList<>();
      List<TreemapGeometry> leaves = new ArrayList<>();

      for(TreemapGeometry g : all) {
         (g.isLeaf() ? leaves : groups).add(g);
      }

      assertEquals(2, groups.size());
      assertEquals(5, leaves.size());

      double[] groupSizes = { 10, 10 };
      int next = 0;

      for(TreemapGeometry group : groups) {
         Rect gr = group.getMapItem().getBounds();
         double leafArea = 0;
         int count = 0;

         for(TreemapGeometry leaf : leaves) {
            Rect lr = leaf.getMapItem().getBounds();

            if(lr.x >= gr.x - EPS && lr.y >= gr.y - EPS && lr.x + lr.w <= gr.x + gr.w + EPS &&
               lr.y + lr.h <= gr.y + gr.h + EPS)
            {
               leafArea += area(leaf);
               count++;
            }
         }

         assertTrue(count >= 2, "leaves inside group " + gr);
         assertEquals(area(group), leafArea, area(group) * 1e-9 + EPS);
         assertEquals(groupSizes[next++] / 20, area(group) / PLOT, 1e-9);
      }
   }

   @Test
   void leafWithZeroValueKeepsAGeometrySmallerThanTheOthersAndAllFillThePlot() {
      TreemapElement element = new TreemapElement("Name");
      List<TreemapGeometry> leaves = layout(element, new Object[][] {
         { "Name", "Size" }, { "A", 3 }, { "B", 0 }, { "C", 2 } });

      assertEquals(3, leaves.size());
      double sum = 0;
      TreemapGeometry zero = null;

      for(TreemapGeometry g : leaves) {
         sum += area(g);

         if(g.getRowIndex() == 1) {
            zero = g;
         }
      }

      assertNotNull(zero);
      assertTrue(area(zero) > 0);

      for(TreemapGeometry g : leaves) {
         if(g != zero) {
            assertTrue(area(zero) < area(g));
         }
      }

      assertEquals(PLOT, sum, PLOT * 1e-9);
   }
}
