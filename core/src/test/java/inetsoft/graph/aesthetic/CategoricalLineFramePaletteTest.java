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
import inetsoft.graph.scale.Scale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82047: the default categorical line palette repeated LARGE_DASH at index 9 and 10, so
 * the 10th and 11th values of a dimension bound to Line were drawn with the same dash.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CategoricalLineFramePaletteTest {
   private static final GLine[] CYCLE = {
      GLine.THIN_LINE, GLine.DOT_LINE, GLine.DASH_LINE, GLine.MEDIUM_DASH, GLine.LARGE_DASH
   };

   @Test
   void defaultPaletteIsAnUnbrokenFiveStyleCycle() {
      CategoricalLineFrame frame = new CategoricalLineFrame();

      assertEquals(30, frame.getLineCount());

      for(int i = 0; i <= 30; i++) {
         assertEquals(CYCLE[i % CYCLE.length], frame.getLine(i), "index " + i);
      }

      for(int i = 0; i < 30; i++) {
         assertNotEquals(frame.getLine(i), frame.getLine(i + 1), "index " + i + " and " + (i + 1));
      }
   }

   @Test
   void tenthAndEleventhValuesGetDifferentDashes() {
      CategoricalLineFrame frame = new CategoricalLineFrame("x");
      frame.init(data(12));

      assertNotEquals(frame.getLine("v09"), frame.getLine("v10"));
      assertEquals(GLine.THIN_LINE, frame.getLine("v10"));
      assertEquals(GLine.DOT_LINE, frame.getLine("v11"));
   }

   @Test
   void setLinesReplacesOnlyThePalette() {
      CategoricalLineFrame frame = new CategoricalLineFrame("x");
      frame.init(data(3));
      frame.setLine("v00", GLine.LARGE_DASH);
      Scale scale = frame.getScale();

      frame.setLines(new GLine[] { GLine.DASH_LINE, GLine.DOT_LINE });

      assertSame(scale, frame.getScale(), "the scale is kept");
      assertEquals(2, frame.getLineCount());
      assertEquals(GLine.LARGE_DASH, frame.getLine("v00"), "the per-value assignment is kept");
      assertEquals(GLine.DOT_LINE, frame.getLine("v01"));
      assertEquals(GLine.DASH_LINE, frame.getLine("v02"), "the palette wraps at its own length");
   }

   @Test
   void setLinesIgnoresNullAndEmpty() {
      CategoricalLineFrame frame = new CategoricalLineFrame();

      frame.setLines(null);
      frame.setLines(new GLine[0]);

      assertEquals(30, frame.getLineCount());
      assertEquals(GLine.THIN_LINE, frame.getLine(0));
   }

   private static DefaultDataSet data(int n) {
      Object[][] rows = new Object[n + 1][];
      rows[0] = new Object[] { "x", "y" };

      for(int i = 0; i < n; i++) {
         rows[i + 1] = new Object[] { String.format("v%02d", i), i };
      }

      return new DefaultDataSet(rows);
   }
}
