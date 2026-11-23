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
package inetsoft.graph.geometry;

import inetsoft.graph.GGraph;
import inetsoft.graph.aesthetic.VisualModel;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.visual.Pie3DChartFixture;
import inetsoft.graph.visual.Pie3DVO;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Shape;
import java.awt.geom.Arc2D;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * {@link Pie3DGeometry}: the accessors, and the bases, intervals and row indexes that
 * {@code IntervalElement} fills for a 3D pie.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class Pie3DGeometryTest {
   private static final double EPS = 1e-6;

   private static Pie3DGeometry geometry() {
      return new Pie3DGeometry(mock(GraphElement.class), mock(GGraph.class), "m",
                               mock(VisualModel.class));
   }

   private static Pie3DGeometry geometryOf(Pie3DChartFixture fixture) {
      Pie3DVO vo = fixture.plotOnly().pie();
      return (Pie3DGeometry) vo.getGeometry();
   }

   @Test
   @DisplayName("tuples and intervals are returned in insertion order")
   void accessors() {
      Pie3DGeometry geom = geometry();
      double[][] tuples = { { 1 }, { 2 }, { 3 } };

      for(int i = 0; i < 3; i++) {
         geom.addTuple(tuples[i]);
         geom.addInterval(10 * (i + 1));
      }

      assertEquals(3, geom.getTupleCount());

      for(int i = 0; i < 3; i++) {
         assertArrayEquals(tuples[i], geom.getTuple(i), EPS);
         assertEquals(10 * (i + 1), geom.getInterval(i), EPS);
      }
   }

   @Test
   @DisplayName("clearTuple removes all tuples")
   void clearTuple() {
      Pie3DGeometry geom = geometry();

      geom.addTuple(new double[] { 1 });
      geom.addTuple(new double[] { 2 });
      geom.addInterval(5);
      geom.addInterval(6);
      geom.clearTuple();

      assertEquals(0, geom.getTupleCount());
   }

   @Test
   @DisplayName("values 10, 30, 60 give intervals 10, 30, 60 (data units) and bases 0, 10, 40 "
      + "(running sum); the slices span 36, 108, 216 degrees")
   void intervalsAndBases() {
      Pie3DGeometry geom = geometryOf(new Pie3DChartFixture());

      assertEquals(3, geom.getTupleCount());
      assertArrayEquals(new double[] { 10, 30, 60 },
                        new double[] { geom.getInterval(0), geom.getInterval(1),
                                       geom.getInterval(2) }, EPS);
      assertArrayEquals(new double[] { 0, 10, 40 },
                        new double[] { geom.getTuple(0)[0], geom.getTuple(1)[0],
                                       geom.getTuple(2)[0] }, EPS);

      Shape[] shapes = new Pie3DChartFixture().plot().pie().getShapes();

      assertArrayEquals(new double[] { 36, 108, 216 },
                        new double[] { Math.abs(((Arc2D) shapes[0]).getAngleExtent()),
                                       Math.abs(((Arc2D) shapes[1]).getAngleExtent()),
                                       Math.abs(((Arc2D) shapes[2]).getAngleExtent()) }, 1e-4);
   }

   @Test
   @DisplayName("a null value is skipped: row and sub row indexes are 0 and 2, column index is that "
      + "of the measure")
   void indexes() {
      Pie3DGeometry geom = geometryOf(new Pie3DChartFixture().values(10.0, null, 60.0));

      assertEquals(2, geom.getTupleCount());
      assertArrayEquals(new int[] { 0, 2 }, geom.getRowIndexes());
      assertArrayEquals(new int[] { 0, 2 }, geom.getSubRowIndexes());
      assertEquals(1, geom.getColIndex());
   }

   @Test
   @DisplayName("measures put together in one pie share one stack: bases m1 0 and 30, m2 10 and 60")
   void twoMeasuresShareStack() {
      Pie3DChartFixture fixture = new Pie3DChartFixture().values(10.0, 30.0);
      fixture.values2 = new Object[] { 20.0, 40.0 };
      fixture.plotOnly();

      assertEquals(2, fixture.pies().size());

      Pie3DGeometry m1 = (Pie3DGeometry) fixture.pies().get(0).getGeometry();
      Pie3DGeometry m2 = (Pie3DGeometry) fixture.pies().get(1).getGeometry();

      assertArrayEquals(new double[] { 0, 30 },
                        new double[] { m1.getTuple(0)[0], m1.getTuple(1)[0] }, EPS);
      assertArrayEquals(new double[] { 10, 60 },
                        new double[] { m2.getTuple(0)[0], m2.getTuple(1)[0] }, EPS);
      assertArrayEquals(new double[] { 10, 30 },
                        new double[] { m1.getInterval(0), m1.getInterval(1) }, EPS);
      assertArrayEquals(new double[] { 20, 40 },
                        new double[] { m2.getInterval(0), m2.getInterval(1) }, EPS);
   }

}
