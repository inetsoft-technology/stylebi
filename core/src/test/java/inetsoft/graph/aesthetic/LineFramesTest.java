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
import inetsoft.graph.scale.LinearScale;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link LinearLineFrame} (dash length by ratio) and {@link CategoricalLineFrame} (palette,
 * assignments, clone, equality).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class LineFramesTest {
   private static LinearLineFrame scaled() {
      LinearLineFrame frame = new LinearLineFrame();
      frame.setScale(new LinearScale(0, 100));
      return frame;
   }

   private static GLine byValue(LineFrame frame, Object value) {
      return frame.getLine(value);
   }

   private static DefaultDataSet column(String name, Object... values) {
      Object[][] rows = new Object[values.length + 1][];
      rows[0] = new Object[] { name };

      for(int i = 0; i < values.length; i++) {
         rows[i + 1] = new Object[] { values[i] };
      }

      return new DefaultDataSet(rows);
   }

   private static CategoricalLineFrame frameOn(Object... values) {
      CategoricalLineFrame frame = new CategoricalLineFrame("g");
      frame.init(column("g", values));
      return frame;
   }

   private static GLine[] cycle() {
      return new GLine[] {
         GLine.THIN_LINE, GLine.DOT_LINE, GLine.DASH_LINE, GLine.MEDIUM_DASH, GLine.LARGE_DASH };
   }

   // ------------------------------------------------------------- linear

   @Test
   @DisplayName("linear line frame: the dash grows from 3 to 13 with the ratio, width stays 1")
   void dashByRatio() {
      LinearLineFrame frame = scaled();
      int[] values = { 0, 25, 50, 75, 100 };
      double[] dashes = { 3, 5, 8, 10, 13 };

      for(int i = 0; i < values.length; i++) {
         GLine line = byValue(frame, values[i]);

         assertEquals(dashes[i], line.getDash(), "dash at " + values[i]);
         assertEquals(1, line.getLineWidth(), "width at " + values[i]);
      }
   }

   @Test
   @DisplayName("linear line frame: the dash never decreases with the value and stays within 3 to 13")
   void dashIsMonotone() {
      LinearLineFrame frame = scaled();
      double previous = 0;

      for(int v = 0; v <= 100; v += 5) {
         double dash = byValue(frame, v).getDash();

         assertTrue(dash >= previous, "value " + v);
         assertTrue(dash >= 3 && dash <= 13, "value " + v);
         previous = dash;
      }
   }

   @Test
   @DisplayName("linear line frame: without a scale the dash is 13, null and text have no line")
   void noScaleNullAndText() {
      assertEquals(13, byValue(new LinearLineFrame(), "x").getDash());

      LinearLineFrame frame = scaled();
      assertNull(byValue(frame, null));
      assertNull(byValue(frame, "abc"));
   }

   @Test
   @DisplayName("linear line frame: a value above the maximum is clamped to dash 13")
   void aboveMaxIsClamped() {
      assertEquals(13, byValue(scaled(), 150).getDash());
   }

   @Test
   @DisplayName("linear line frame: a value below the minimum is clamped to dash 3, never negative")
   void belowMinIsClamped() {
      assertEquals(3, byValue(scaled(), -100).getDash());
   }

   @Test
   @DisplayName("linear line frame: a reversed scale swaps the ends, the data set path equals the value path")
   void reversedAndDataSetPath() {
      LinearLineFrame reversed = new LinearLineFrame();
      LinearScale scale = new LinearScale(0, 100);
      scale.setReversed(true);
      reversed.setScale(scale);

      assertEquals(13, byValue(reversed, 0).getDash());
      assertEquals(3, byValue(reversed, 100).getDash());

      LinearLineFrame frame = new LinearLineFrame("v");
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "v", "other" }, { 0, 1 }, { 50, 1 }, { 100, 1 } });
      frame.init(data);
      Object[] values = { 0, 50, 100 };

      for(int row = 0; row < values.length; row++) {
         assertEquals(byValue(frame, values[row]), frame.getLine(data, "other", row),
                      "row " + row);
      }
   }

   // -------------------------------------------------------- categorical

   @Test
   @DisplayName("default palette is a cycle of thin, dot, dash, medium and large dash")
   void paletteIsFiveCycle() {
      CategoricalLineFrame frame = new CategoricalLineFrame();
      GLine[] cycle = cycle();

      for(int i = 0; i <= 30; i++) {
         assertEquals(cycle[i % 5], frame.getLine(i), "index " + i);
      }
   }

   @Test
   @DisplayName("neighbouring categories never get the same line style")
   void adjacentCategoriesDiffer() {
      CategoricalLineFrame frame = new CategoricalLineFrame();

      for(int i = 0; i < 30; i++) {
         assertNotEquals(frame.getLine(i), frame.getLine(i + 1), "indexes " + i + " and " + (i + 1));
      }
   }

   @Test
   @DisplayName("palette constants: dash 0, 1, 3, 6, 9 with width 1, 30 entries")
   void paletteConstants() {
      CategoricalLineFrame frame = new CategoricalLineFrame();
      GLine[] cycle = cycle();
      double[] dashes = { 0, 1, 3, 6, 9 };

      for(int i = 0; i < 5; i++) {
         assertSame(cycle[i], frame.getLine(i));
         assertEquals(dashes[i], frame.getLine(i).getDash(), "dash " + i);
         assertEquals(1, frame.getLine(i).getLineWidth(), "width " + i);
      }

      assertEquals(30, frame.getLineCount());
   }

   @Test
   @DisplayName("values get lines in order of appearance and wrap around the palette")
   void orderAndWrap() {
      Object[] values = new Object[33];

      for(int i = 0; i < values.length; i++) {
         values[i] = "v" + i;
      }

      CategoricalLineFrame frame = frameOn(values);

      assertEquals(GLine.THIN_LINE, byValue(frame, "v0"));
      assertEquals(GLine.DOT_LINE, byValue(frame, "v1"));
      assertEquals(frame.getLine(0), byValue(frame, "v30"));
      assertEquals(frame.getLine(1), byValue(frame, "v31"));
   }

   @Test
   @DisplayName("explicit assignment wins, unknown values get the default line, null is a category")
   void assignmentUnknownAndNull() {
      CategoricalLineFrame frame = new CategoricalLineFrame("g");
      GLine custom = new GLine(4, 2);
      frame.setLine("B", custom);
      frame.init(column("g", "A", "B"));

      assertSame(custom, byValue(frame, "B"));
      assertTrue(frame.isStatic("B"));
      assertNull(byValue(frame, "Z"));

      CategoricalLineFrame withNull = frameOn("A", null, "B");
      assertEquals(GLine.DOT_LINE, byValue(withNull, null));
   }

   @Test
   @DisplayName("measure names path: names get the lines given at the same position")
   void measureNames() {
      CategoricalLineFrame frame = new CategoricalLineFrame();
      frame.init(new Object[] { "m1", "m2" }, new GLine[] { GLine.DASH_LINE, GLine.DOT_LINE });

      assertEquals(GLine.DOT_LINE, byValue(frame, "m2"));
      assertEquals(GLine.DOT_LINE, frame.getLine(column("x", "q"), "m2", 0));
   }

   @Test
   @DisplayName("equals sees line assignments like the shape, texture and size frames do")
   void equalsSeesAssignments() {
      CategoricalLineFrame a = new CategoricalLineFrame("g");
      CategoricalLineFrame b = new CategoricalLineFrame("g");

      assertEquals(a, b);

      b.setLine("A", GLine.DASH_LINE);
      assertNotEquals(a, b);
   }

   @Test
   @DisplayName("unique id sees an assignment and clone edits leave the original unchanged")
   void uniqueIdAndClone() {
      CategoricalLineFrame original = new CategoricalLineFrame("g");
      CategoricalLineFrame assigned = new CategoricalLineFrame("g");
      assigned.setLine("A", GLine.DASH_LINE);

      assertNotEquals(original.getUniqueId(), assigned.getUniqueId());

      CategoricalLineFrame clone = (CategoricalLineFrame) original.clone();
      clone.setLine(0, GLine.LARGE_DASH);
      clone.setLine("A", GLine.DASH_LINE);

      assertEquals(GLine.THIN_LINE, original.getLine(0));
      assertFalse(original.isStatic("A"));
   }

   @Test
   @DisplayName("without a field the headers are the categories, with a field the row value is used")
   void noFieldAndDataSetPath() {
      CategoricalLineFrame headers = new CategoricalLineFrame();
      headers.init(new DefaultDataSet(new Object[][] { { "m1", "m2" }, { 1, 2 } }));

      assertEquals(GLine.THIN_LINE, byValue(headers, "m1"));
      assertEquals(GLine.DOT_LINE, byValue(headers, "m2"));

      CategoricalLineFrame byField = new CategoricalLineFrame("g");
      DefaultDataSet data = column("g", "A", "B");
      byField.init(data);

      assertEquals(GLine.DOT_LINE, byField.getLine(data, "ignored", 1));
   }
}
