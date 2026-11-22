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

import inetsoft.graph.data.DefaultDataSet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CategoricalShapeFrame}: the default palette, the mapping of values to shapes by order of
 * appearance, wrap-around, explicit assignments, unknown and null values, the measure name path,
 * clone, equality, unique id and legend visibility.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class CategoricalShapeFrameTest {
   /** A one column data set (header then the values). */
   private static DefaultDataSet column(String name, Object... values) {
      Object[][] rows = new Object[values.length + 1][];
      rows[0] = new Object[] { name };

      for(int i = 0; i < values.length; i++) {
         rows[i + 1] = new Object[] { values[i] };
      }

      return new DefaultDataSet(rows);
   }

   private static CategoricalShapeFrame frameOn(Object... values) {
      CategoricalShapeFrame frame = new CategoricalShapeFrame("g");
      frame.init(column("g", values));
      return frame;
   }

   private static GShape of(CategoricalShapeFrame frame, Object value) {
      return frame.getShape(value);
   }

   private static GShape[] palette() {
      return new GShape[] {
         GShape.CIRCLE, GShape.TRIANGLE, GShape.SQUARE, GShape.CROSS, GShape.STAR,
         GShape.DIAMOND, GShape.XSHAPE, GShape.FILLED_CIRCLE, GShape.FILLED_TRIANGLE,
         GShape.FILLED_SQUARE, GShape.FILLED_DIAMOND };
   }

   @Test
   @DisplayName("default palette: the first eleven shapes, then the first seven again")
   void defaultPalette() {
      CategoricalShapeFrame frame = new CategoricalShapeFrame();
      GShape[] first = palette();

      for(int i = 0; i < first.length; i++) {
         assertSame(first[i], frame.getShape(i), "index " + i);
      }

      for(int i = 11; i <= 17; i++) {
         assertSame(first[i - 11], frame.getShape(i), "index " + i);
      }
   }

   @Test
   @DisplayName("default palette: 30 shapes and the index wraps around")
   void paletteSizeAndWrap() {
      CategoricalShapeFrame frame = new CategoricalShapeFrame();

      assertEquals(30, frame.getShapeCount());
      assertSame(GShape.FILLED_CIRCLE, frame.getShape(29));
      assertSame(GShape.CIRCLE, frame.getShape(30));
      assertSame(GShape.TRIANGLE, frame.getShape(31));   }

   @Test
   @DisplayName("values get shapes in order of first appearance")
   void valueToShapeByOrder() {
      CategoricalShapeFrame frame = frameOn("A", "B", "C");

      assertSame(GShape.CIRCLE, of(frame, "A"));
      assertSame(GShape.TRIANGLE, of(frame, "B"));
      assertSame(GShape.SQUARE, of(frame, "C"));
   }

   @Test
   @DisplayName("duplicate values share one slot")
   void duplicatesShareSlot() {
      CategoricalShapeFrame frame = frameOn("B", "A", "B", "C");

      assertSame(GShape.CIRCLE, of(frame, "B"));
      assertSame(GShape.TRIANGLE, of(frame, "A"));
      assertSame(GShape.SQUARE, of(frame, "C"));
   }

   @Test
   @DisplayName("more values than shapes wrap around the palette")
   void wrapAround() {
      Object[] values = new Object[33];

      for(int i = 0; i < values.length; i++) {
         values[i] = "v" + i;
      }

      CategoricalShapeFrame frame = frameOn(values);

      assertSame(GShape.FILLED_CIRCLE, of(frame, "v29"));
      assertSame(GShape.CIRCLE, of(frame, "v30"));
      assertSame(GShape.TRIANGLE, of(frame, "v31"));
      assertSame(GShape.SQUARE, of(frame, "v32"));   }

   @Test
   @DisplayName("an explicit assignment wins and is reported as static")
   void explicitAssignmentWins() {
      CategoricalShapeFrame frame = new CategoricalShapeFrame("g");
      frame.setShape("B", GShape.STAR);
      frame.init(column("g", "A", "B", "C"));

      assertSame(GShape.STAR, of(frame, "B"));
      assertSame(GShape.CIRCLE, of(frame, "A"));
      assertSame(GShape.SQUARE, of(frame, "C"));
      assertTrue(frame.isStatic("B"));
      assertFalse(frame.isStatic("A"));
   }

   @Test
   @DisplayName("pinned: an assignment does not shift the palette slots of the other values")
   void assignmentKeepsPaletteSlots() {
      CategoricalShapeFrame frame = new CategoricalShapeFrame("g");
      frame.setShape("B", GShape.STAR);
      frame.init(column("g", "A", "B", "C"));

      assertSame(GShape.SQUARE, of(frame, "C"));
   }

   @Test
   @DisplayName("an unknown value gets the default shape, null until one is set")
   void unknownValueAndDefaultShape() {
      CategoricalShapeFrame frame = frameOn("A", "B");

      assertNull(of(frame, "Z"));
      assertNull(frame.getDefaultShape());

      frame.setDefaultShape(GShape.STAR);
      assertSame(GShape.STAR, of(frame, "Z"));
      assertSame(GShape.STAR, frame.getDefaultShape());
   }

   @Test
   @DisplayName("a null value is a category of its own")
   void nullValueHasOwnSlot() {
      CategoricalShapeFrame frame = frameOn("A", null, "B");

      assertSame(GShape.TRIANGLE, of(frame, null));
      assertSame(GShape.SQUARE, of(frame, "B"));
   }

   @Test
   @DisplayName("pinned: a number is looked up by its text form against text categories")
   void numberAgainstTextCategories() {
      CategoricalShapeFrame frame = frameOn("1", "2");

      assertSame(GShape.CIRCLE, of(frame, 1));
   }

   @Test
   @DisplayName("measure names path: names get the shapes given at the same position")
   void measureNamesPath() {
      CategoricalShapeFrame frame = new CategoricalShapeFrame();
      frame.init(new Object[] { "m1", "m2" }, new GShape[] { GShape.CIRCLE, GShape.SQUARE });

      assertSame(GShape.SQUARE, of(frame, "m2"));
      assertSame(GShape.SQUARE, frame.getShape(column("x", "q"), "m2", 0));
   }

   @Test
   @DisplayName("without a field the column headers are the categories")
   void noFieldUsesHeaders() {
      CategoricalShapeFrame frame = new CategoricalShapeFrame();
      frame.init(new DefaultDataSet(new Object[][] { { "m1", "m2" }, { 1, 2 } }));

      assertSame(GShape.CIRCLE, of(frame, "m1"));
      assertSame(GShape.TRIANGLE, of(frame, "m2"));
   }

   @Test
   @DisplayName("data set path reads the value of the row and ignores the column")
   void dataSetPathReadsRow() {
      CategoricalShapeFrame frame = new CategoricalShapeFrame("g");
      DefaultDataSet data = column("g", "A", "B", "C");
      frame.init(data);

      assertSame(GShape.TRIANGLE, frame.getShape(data, "x", 1));
      assertSame(GShape.SQUARE, frame.getShape(data, "g", 2));
   }

   @Test
   @DisplayName("removing an assignment restores the palette shape and clears the flags")
   void removeAssignment() {
      CategoricalShapeFrame frame = frameOn("A", "B", "C");
      frame.setShape("B", GShape.STAR);
      frame.setShape("B", null);

      assertFalse(frame.isStatic("B"));
      assertFalse(frame.isScripted("B"));
      assertSame(GShape.TRIANGLE, of(frame, "B"));
   }

   @Test
   @DisplayName("init drops assignments of values that are no longer in the data and keeps present ones")
   void staleAssignmentRemovedOnInit() {
      CategoricalShapeFrame frame = new CategoricalShapeFrame("g");
      frame.setShape("Z", GShape.STAR);
      frame.setShape("A", GShape.CROSS);
      frame.init(column("g", "A", "B"));

      assertFalse(frame.isStatic("Z"));
      assertNull(of(frame, "Z"));
      assertTrue(frame.isStatic("A"));
      assertSame(GShape.CROSS, of(frame, "A"));
   }

   @Test
   @DisplayName("setShape by index writes the slot index modulo the palette size")
   void paletteEditWraps() {
      CategoricalShapeFrame frame = new CategoricalShapeFrame();
      frame.setShape(33, GShape.STAR);

      assertSame(GShape.STAR, frame.getShape(3));
      assertSame(GShape.STAR, frame.getShape(33));   }

   @Test
   @DisplayName("an empty palette gives no shape and no exception")
   void emptyPalette() {
      CategoricalShapeFrame frame = new CategoricalShapeFrame();
      frame.init(new Object[] { "A" }, new GShape[0]);

      assertNull(of(frame, "A"));
   }

   @Test
   @DisplayName("clone is independent of the original in palette and assignments")
   void cloneIndependence() {
      CategoricalShapeFrame original = frameOn("A", "B");
      CategoricalShapeFrame clone = (CategoricalShapeFrame) original.clone();

      assertEquals(original, clone);

      clone.setShape(0, GShape.STAR);
      clone.setShape("A", GShape.CROSS);

      assertEquals(GShape.CIRCLE, original.getShape(0));
      assertFalse(original.isStatic("A"));
      assertNotEquals(original, clone);
   }

   @Test
   @DisplayName("clone of a default frame still holds the default shapes and can be edited")
   void cloneOfDefaultFrame() {
      CategoricalShapeFrame clone = (CategoricalShapeFrame) new CategoricalShapeFrame().clone();

      assertEquals(GShape.CIRCLE, clone.getShape(0));
      assertDoesNotThrow(() -> clone.setShape(0, GShape.STAR));
      assertEquals(GShape.STAR, clone.getShape(0));
   }

   @Test
   @DisplayName("equals and unique id see palette edits and assignments")
   void equalsAndUniqueId() {
      CategoricalShapeFrame a = new CategoricalShapeFrame("g");
      CategoricalShapeFrame b = new CategoricalShapeFrame("g");

      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());
      assertEquals(a.getUniqueId(), b.getUniqueId());

      b.setShape(0, GShape.STAR);
      assertNotEquals(a, b);

      b = new CategoricalShapeFrame("g");
      b.setShape("A", GShape.STAR);
      assertNotEquals(a, b);
      assertNotEquals(a.getUniqueId(), b.getUniqueId());
   }

   @Test
   @DisplayName("assignments of CIRCLE and FILLED_CIRCLE differ in equals and in unique id")
   @Disabled("Bug #82048: Shape legend id ignores the fill of a shape")
   void uniqueIdOfSameClassShapes() {
      CategoricalShapeFrame outline = new CategoricalShapeFrame("g");
      CategoricalShapeFrame filled = new CategoricalShapeFrame("g");
      outline.setShape("A", GShape.CIRCLE);
      filled.setShape("A", GShape.FILLED_CIRCLE);

      assertNotEquals(outline, filled);
      assertNotEquals(outline.getUniqueId(), filled.getUniqueId());
   }

   @Test
   @DisplayName("legend is visible while shapes differ, hidden for alike shapes unless a field is set")
   void legendVisibility() {
      CategoricalShapeFrame different = new CategoricalShapeFrame();
      different.init("A", "B");
      assertTrue(different.isVisible());

      CategoricalShapeFrame alike = new CategoricalShapeFrame();
      alike.init(new Object[] { "A", "B" }, new GShape[] { GShape.CIRCLE });
      assertFalse(alike.isVisible());

      CategoricalShapeFrame alikeWithField = new CategoricalShapeFrame("g");
      alikeWithField.init(new Object[] { "A", "B" }, new GShape[] { GShape.CIRCLE });
      assertTrue(alikeWithField.isVisible());
   }

   @Test
   @DisplayName("a comparator orders the categories before shapes are assigned")
   void comparatorOrder() {
      CategoricalShapeFrame frame = new CategoricalShapeFrame("g");
      frame.setComparator(Comparator.naturalOrder());
      frame.init(column("g", "C", "A", "B"));

      assertSame(GShape.CIRCLE, of(frame, "A"));
      assertSame(GShape.TRIANGLE, of(frame, "B"));
      assertSame(GShape.SQUARE, of(frame, "C"));
   }

   @Test
   @DisplayName("before init every value gets the default shape")
   void beforeInit() {
      CategoricalShapeFrame frame = new CategoricalShapeFrame("g");

      assertNull(of(frame, "A"));

      frame.setDefaultShape(GShape.STAR);
      assertSame(GShape.STAR, of(frame, "A"));
   }

   @Test
   @DisplayName("legend values all have shapes and the first eleven are pairwise different")
   void legendItemsMatchStyles() {
      Object[] values = new Object[12];

      for(int i = 0; i < values.length; i++) {
         values[i] = "v" + i;
      }

      CategoricalShapeFrame frame = frameOn(values);
      Object[] legend = frame.getValues();

      assertEquals(12, legend.length);

      for(Object v : legend) {
         assertNotNull(of(frame, v), String.valueOf(v));
      }

      for(int i = 0; i < 11; i++) {
         for(int j = i + 1; j < 11; j++) {
            assertNotEquals(of(frame, legend[i]), of(frame, legend[j]), i + " vs " + j);
         }
      }
   }
}
