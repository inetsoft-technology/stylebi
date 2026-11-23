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

import inetsoft.graph.EGraph;
import inetsoft.graph.GGraph;
import inetsoft.graph.aesthetic.VisualModel;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.PointElement;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.visual.PointVO;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Point2D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PointGeometry hands its tuple and its row/column indexes over to the PointVO it creates.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PointGeometryTest {
   private static LinearScale lin(double min, double max, String... fields) {
      LinearScale scale = new LinearScale(fields);
      scale.setMin(min);
      scale.setMax(max);
      return scale;
   }

   private static RectCoord xyCoord(EGraph egraph) {
      RectCoord coord = new RectCoord(lin(0, 10, "x"), lin(0, 100, "y"));
      egraph.setCoordinate(coord);
      return coord;
   }

   private static PointGeometry newGeometry(double[] tuple) {
      EGraph egraph = new EGraph();
      PointElement elem = new PointElement("x", "y");
      egraph.addElement(elem);
      RectCoord coord = xyCoord(egraph);
      DefaultDataSet data = new DefaultDataSet(new Object[][] { { "x", "y" }, { 2.0, 60.0 } });
      return new PointGeometry(elem, egraph.createGGraph(coord, data), "y", 0, new VisualModel(),
                               tuple);
   }

   @Test
   @DisplayName("clearTuple releases the tuple")
   void clearTupleReleasesTuple() {
      PointGeometry geom = newGeometry(new double[] { 2, 60 });
      assertNotNull(geom.getTuple());

      geom.clearTuple();

      assertNull(geom.getTuple());
   }

   @Test
   @DisplayName("createVisual carries the row, sub row and column index to the PointVO")
   void createVisualCarriesIndexes() {
      PointGeometry geom = newGeometry(new double[] { 2, 60 });
      geom.setRowIndex(4);
      geom.setSubRowIndex(2);
      geom.setColIndex(3);
      EGraph egraph = new EGraph();
      RectCoord coord = xyCoord(egraph);

      PointVO vo = (PointVO) geom.createVisual(coord);

      assertEquals(4, vo.getRowIndex());
      assertEquals(2, vo.getSubRowIndex());
      assertEquals(3, vo.getColIndex());
   }

   @Test
   @DisplayName("indexes of geometries created from unsorted rows follow the sorted order")
   void indexesWithUnsortedRows() {
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "x", "y" }, { 3.0, 30.0 }, { 1.0, 10.0 }, { 2.0, 20.0 } });
      EGraph egraph = new EGraph();
      PointElement elem = new PointElement("x", "y");
      elem.setSortData(true);
      egraph.addElement(elem);
      RectCoord coord = xyCoord(egraph);

      GGraph ggraph = egraph.createGGraph(coord, data);

      assertEquals(3, ggraph.getGeometryCount());
      int[] rows = { 1, 2, 0 };

      for(int i = 0; i < 3; i++) {
         PointGeometry geom = (PointGeometry) ggraph.getGeometry(i);

         assertEquals(i, geom.getTupleIndex(), "tuple index " + i);
         assertEquals(rows[i], geom.getSubRowIndex(), "sub row " + i);
         assertEquals(rows[i], geom.getRowIndex(), "root row " + i);
      }
   }

   @Test
   @DisplayName("column index is the header position of the variable")
   void columnIndexIsHeaderPositionOfVariable() {
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "Cat", "m1", "m2" }, { "A", 10.0, 20.0 }, { "B", 30.0, 40.0 } });
      CategoricalScale xs = new CategoricalScale("Cat");
      xs.init(data);
      EGraph egraph = new EGraph();
      PointElement elem = new PointElement();
      elem.addDim("Cat");
      elem.addVar("m1");
      elem.addVar("m2");
      egraph.addElement(elem);
      RectCoord coord = new RectCoord(xs, lin(0, 100, "m1", "m2"));
      egraph.setCoordinate(coord);

      GGraph ggraph = egraph.createGGraph(coord, data);

      assertEquals(4, ggraph.getGeometryCount());
      int[] cols = new int[4];

      for(int i = 0; i < 4; i++) {
         cols[i] = ((PointGeometry) ggraph.getGeometry(i)).getColIndex();
      }

      // row-major: A m1, A m2, B m1, B m2
      assertArrayEquals(new int[] { 1, 2, 1, 2 }, cols);
   }

   @Test
   @DisplayName("PointVO position is the tuple mapped to the logical 1000 x 1000 plot")
   void voPositionFromTuple() {
      PointGeometry geom = newGeometry(new double[] { 2, 60 });
      EGraph egraph = new EGraph();
      RectCoord coord = xyCoord(egraph);

      PointVO vo = (PointVO) geom.createVisual(coord);
      Point2D pos = vo.getPosition();

      // x: (2 - 0) * 1000 / 10, y: (60 - 0) * 1000 / 100
      assertEquals(200, pos.getX(), 1e-9);
      assertEquals(600, pos.getY(), 1e-9);
   }
}
