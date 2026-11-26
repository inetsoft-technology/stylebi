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

import inetsoft.graph.GGraph;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.geometry.Geometry;
import inetsoft.graph.geometry.SchemaGeometry;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.schema.BoxPainter;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Geometries created by a schema element (box, candle and stock charts): one per row and schema;
 * rows with a null or NaN value are skipped while the others keep their original row and column
 * indexes. Scales are created from the data, which needs the Spring context.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class SchemaElementChartPathTest {
   // -------------------------------------------------------------------- createGeometry

   private static final String[][] SCHEMAS = { { "m1", "m2" }, { "m3", "m4" } };

   private static SchemaElement chartElement() {
      SchemaElement element = new SchemaElement(new BoxPainter());
      element.addDim("Cat");

      for(String[] schema : SCHEMAS) {
         element.addSchema(schema);
      }

      return element;
   }

   /** Three categories A, B, C; the cell (row, column) of the measures may be replaced. */
   private static DefaultDataSet data(Object[][] replace) {
      Object[][] rows = {
         { "Cat", "m1", "m2", "m3", "m4" },
         { "A", 10.0, 20.0, 30.0, 40.0 },
         { "B", 11.0, 21.0, 31.0, 41.0 },
         { "C", 12.0, 22.0, 32.0, 42.0 } };

      for(Object[] r : replace) {
         rows[(Integer) r[0]][(Integer) r[1]] = r[2];
      }

      return new DefaultDataSet(rows);
   }

   private static List<SchemaGeometry> geometries(SchemaElement element, DefaultDataSet data) {
      LinearScale shared = new LinearScale(0, 100);
      CategoricalScale cat = new CategoricalScale("Cat");
      cat.init(data);
      GGraph graph = mock(GGraph.class);
      when(graph.getScale(anyString())).thenAnswer(inv -> "Cat".equals(inv.getArgument(0)) ? cat
         : shared);
      List<SchemaGeometry> list = new ArrayList<>();
      doAnswer(inv -> list.add((SchemaGeometry) (Geometry) inv.getArgument(0)))
         .when(graph).addGeometry(org.mockito.ArgumentMatchers.any());

      element.createGeometry(data, graph);
      return list;
   }

   @Test
   @DisplayName("one geometry per row and schema")
   void geometryCount() {
      assertEquals(6, geometries(chartElement(), data(new Object[0][])).size());
   }

   @Test
   @DisplayName("an element with an empty schema creates no geometry")
   void emptySchema() {
      SchemaElement element = new SchemaElement(new BoxPainter());
      element.addDim("Cat");
      element.addSchema();

      assertEquals(0, element.getVars().length);
      assertTrue(geometries(element, data(new Object[0][])).isEmpty());
   }

   private static List<String> describe(List<SchemaGeometry> geometries) {
      List<String> list = new ArrayList<>();

      for(SchemaGeometry g : geometries) {
         list.add(g.getVar() + "@" + g.getSubRowIndex());
      }

      return list;
   }

   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "null", "NaN", "text" })
   @DisplayName("a null, NaN or non-numeric (maps to NaN) value skips that schema of that row only; indexes keep the data rows")
   void missingValueSkipsOneGeometry(String kind) {
      Object missing = kind.equals("null") ? null : kind.equals("NaN") ? (Object) Double.NaN : "text";
      // row 2 (category B), column m3 (schema 1)
      List<SchemaGeometry> list =
         geometries(chartElement(), data(new Object[][] { { 2, 3, missing } }));

      assertEquals(5, list.size());
      assertEquals(Set.of("m1@0", "m1@1", "m1@2", "m3@0", "m3@2"), new HashSet<>(describe(list)));
   }

   @Test
   @DisplayName("the column index of a geometry is the header index of the first column of its schema")
   void columnIndex() {
      List<SchemaGeometry> list = geometries(chartElement(), data(new Object[0][]));

      for(SchemaGeometry g : list) {
         assertEquals(g.getVar().equals("m1") ? 1 : 3, g.getColIndex(), g.getVar());
      }
   }
}
