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

import inetsoft.graph.coord.MekkoCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.scale.Scale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MekkoGeometry#calcGroupTotals}: the per-group totals that size the marimekko columns.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MekkoGeometryTest {
   private static final double EPS = 1e-9;

   private static DefaultDataSet data(Object... pairs) {
      Object[][] rows = new Object[pairs.length / 2 + 1][];
      rows[0] = new Object[] { "G", "V" };

      for(int i = 0; i < pairs.length / 2; i++) {
         rows[i + 1] = new Object[] { pairs[2 * i], pairs[2 * i + 1] };
      }

      return new DefaultDataSet(rows);
   }

   private static Map<Object, Double> totals(DefaultDataSet data) {
      return MekkoGeometry.calcGroupTotals(data, "G", "V", 0, data.getRowCount(), null);
   }

   private static MekkoCoord coordWithOption(int option) {
      MekkoCoord coord = new MekkoCoord("G", "S");
      coord.getScales()[0].setScaleOption(option);
      return coord;
   }

   @Test
   @DisplayName("the total of a group is the sum of its values")
   void sums() {
      Map<Object, Double> map = totals(data("A", 30.0, "A", 10.0, "B", 20.0, "B", 40.0));

      assertEquals(2, map.size());
      assertEquals(40.0, map.get("A"), EPS);
      assertEquals(60.0, map.get("B"), EPS);
   }

   @Test
   @DisplayName("a negative value counts by its absolute value")
   void negativeValueByAbsolute() {
      assertEquals(40.0, totals(data("A", 30.0, "A", -10.0)).get("A"), EPS);
   }

   @Test
   @DisplayName("a group whose only value is null is present with total 0")
   void nullMeasure() {
      Map<Object, Double> map = totals(data("A", 30.0, "B", null));

      assertEquals(30.0, map.get("A"), EPS);
      assertTrue(map.containsKey("B"));
      assertEquals(0.0, map.get("B"), EPS);
   }

   @Test
   @DisplayName("a group whose only value is a string is present with total 0")
   void nonNumberMeasure() {
      Map<Object, Double> map = totals(data("A", 30.0, "B", "n/a"));

      assertEquals(30.0, map.get("A"), EPS);
      assertTrue(map.containsKey("B"));
      assertEquals(0.0, map.get("B"), EPS);
   }

   @Test
   @DisplayName("only the rows in [start, end) are counted")
   void rowRange() {
      DefaultDataSet data = data("A", 1.0, "A", 10.0, "A", 100.0, "A", 1000.0, "A", 10000.0);
      Map<Object, Double> map = MekkoGeometry.calcGroupTotals(data, "G", "V", 1, 4, null);

      assertEquals(1110.0, map.get("A"), EPS);
   }

   @Test
   @DisplayName("without a coordinate a null group is counted under the null key")
   void nullGroupWithoutCoord() {
      Map<Object, Double> map = totals(data(null, 50.0, "A", 30.0));

      assertEquals(2, map.size());
      assertEquals(50.0, map.get(null), EPS);
      assertEquals(30.0, map.get("A"), EPS);
   }

   @Test
   @DisplayName("a MekkoCoord whose first scale has NO_NULL skips the null group")
   void noNullSkipsNullGroup() {
      DefaultDataSet data = data(null, 50.0, "A", 30.0);
      Map<Object, Double> map = MekkoGeometry.calcGroupTotals(
         data, "G", "V", 0, data.getRowCount(), coordWithOption(Scale.NO_NULL));

      assertFalse(map.containsKey(null));
      assertEquals(30.0, map.get("A"), EPS);
   }

   @Test
   @DisplayName("a MekkoCoord whose scale option is unset still counts the null group")
   void noOptionCountsNullGroup() {
      DefaultDataSet data = data(null, 50.0, "A", 30.0);
      Map<Object, Double> map = MekkoGeometry.calcGroupTotals(
         data, "G", "V", 0, data.getRowCount(), coordWithOption(0));

      assertEquals(50.0, map.get(null), EPS);
      assertEquals(30.0, map.get("A"), EPS);
   }

   @Test
   @DisplayName("a NaN value is ignored like null (total 30, not NaN)")
   void nanIgnored() {
      Map<Object, Double> map = totals(data("A", 30.0, "A", Double.NaN));

      assertEquals(30.0, map.get("A"), EPS);
   }

   @Test
   @DisplayName("Integer, Long, BigDecimal and Float values add up as numbers")
   void numberTypes() {
      Map<Object, Double> map = totals(data("A", 3, "A", 4L, "A", new BigDecimal("5"),
                                            "A", 2.5f));

      assertEquals(14.5, map.get("A"), EPS);
   }
}
