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

import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.element.SchemaElement;
import inetsoft.graph.schema.CandlePainter;
import inetsoft.graph.schema.SchemaTestSupport;
import inetsoft.graph.schema.SchemaTestSupport.Mapping;
import inetsoft.graph.visual.SchemaVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static inetsoft.graph.schema.SchemaTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Schema geometry: the variable part of the tuple, and a visual object that is built from its own
 * tuple and carries the row and column indexes.
 */
@Tag("core")
class SchemaGeometryTest {
   private static SchemaElement element(int dims) {
      SchemaElement element = new SchemaElement(new CandlePainter());

      for(int i = 0; i < dims; i++) {
         element.addDim("d" + i);
      }

      return element;
   }

   @ParameterizedTest(name = "{0} dimensions")
   @CsvSource({ "0, '9,8,7,6,5'", "1, '8,7,6,5'", "2, '7,6,5'" })
   @DisplayName("the variable tuple is the tuple without the leading dimension values")
   void varTuple(int dims, String expected) {
      double[] tuple = { 9, 8, 7, 6, 5 };
      SchemaGeometry geometry = geometry(element(dims), tuple, "h", "c", "o", "l");
      String[] parts = expected.split(",");
      double[] vars = new double[parts.length];

      for(int i = 0; i < vars.length; i++) {
         vars[i] = Double.parseDouble(parts[i]);
      }

      assertArrayEquals(vars, geometry.getVarTuple(), EPS);
   }

   @Test
   @DisplayName("the tuple is the array passed in")
   void tupleIsSameArray() {
      double[] tuple = tuple(1, 2, 3, 4);

      assertSame(tuple, geometry(element(1), tuple, "h", "c", "o", "l").getTuple());
   }

   @Test
   @DisplayName("visual objects of two geometries of one element are independent")
   void createVisualIsIndependent() {
      SchemaElement element = SchemaTestSupport.element(new CandlePainter());
      RectCoord coord = coord(true, Mapping.SCALED, 100);
      SchemaGeometry first = geometry(element, tuple(80, 60, 40, 20), "h", "c", "o", "l");
      SchemaGeometry second = geometry(element, tuple(180, 160, 140, 120), "h", "c", "o", "l");

      SchemaVO vo1 = (SchemaVO) first.createVisual(coord);
      SchemaVO vo2 = (SchemaVO) second.createVisual(coord);

      assertNotEquals(vo1.getBounds(), vo2.getBounds());
      assertBounds(vo1.getBounds(), 34, 199, 32, 602);
      assertBounds(vo2.getBounds(), 34, 1199, 32, 602);
      // the prototype painter of the element was cloned, never initialised (no shapes yet)
      assertThrows(NullPointerException.class, () -> element.getPainter().getShape(0));
   }

   @Test
   @DisplayName("the visual object carries the column, row and sub row indexes")
   void createVisualIndexes() {
      SchemaGeometry geometry = geometry(SchemaTestSupport.element(new CandlePainter()),
                                         tuple(80, 60, 40, 20), "h", "c", "o", "l");
      geometry.setColIndex(3);
      geometry.setRowIndex(4);
      geometry.setSubRowIndex(5);

      SchemaVO vo = (SchemaVO) geometry.createVisual(coord(true, Mapping.SCALED, 100));

      assertEquals(3, vo.getColIndex());
      assertEquals(4, vo.getRowIndex());
      assertEquals(5, vo.getSubRowIndex());
   }
}
