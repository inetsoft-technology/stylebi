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
package inetsoft.graph.data;

import inetsoft.graph.internal.GDefaults;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.ColumnNotFoundException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SubDataSet}: row mapping, conditions, reverse lookup and large mappings.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SubDataSetTest {
   // ---------------------------------------------------------------- helpers

   private static DefaultDataSet k5() {
      return new DefaultDataSet(new Object[][]{
         {"k", "v"}, {"b", 2.0}, {"a", 1.0}, {"c", 3.0}, {"a", 5.0}, {"b", 4.0}});
   }

   private static DefaultDataSet g6() {
      return new DefaultDataSet(new Object[][]{
         {"g", "t", "v"}, {"A", "c", 1.0}, {"A", "a", 2.0}, {"A", "b", 4.0},
         {"B", "b", 8.0}, {"B", "a", 16.0}, {"B", "c", 32.0}});
   }

   private static DefaultDataSet big(int rows, boolean alternating) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[]{"k", "v"};

      for(int i = 0; i < rows; i++) {
         data[i + 1] = new Object[]{alternating ? (i % 2 == 0 ? "x" : "y") : "r" + i, (double) i};
      }

      return new DefaultDataSet(data);
   }

   private static Map<String, Object> cond(Object... kv) {
      Map<String, Object> map = new LinkedHashMap<>();

      for(int i = 0; i < kv.length; i += 2) {
         map.put((String) kv[i], kv[i + 1]);
      }

      return map;
   }

   private static int[] baseRows(DataSet ds) {
      SubDataSet sub = (SubDataSet) ds;
      int[] res = new int[sub.getRowCount()];

      for(int i = 0; i < res.length; i++) {
         res[i] = sub.getBaseRow(i);
      }

      return res;
   }

   /** Calc row that appends fixed rows after the real rows. */
   private static class AddRow implements CalcRow {
      AddRow(Object[]... rows) {
         this.rows = Arrays.asList(rows);
      }

      @Override
      public List<Object[]> calculate(DataSet data) {
         return new ArrayList<>(rows);
      }

      @Override
      public Comparator getComparator(String col) {
         return null;
      }

      private final List<Object[]> rows;
   }

   // ------------------------------------------------------------ construction

   @Test
   void mappingConstructorMapsRowsAndCells() {
      DefaultDataSet base = k5();
      SubDataSet sub = new SubDataSet(base, new int[]{3, 1, 4});

      assertEquals(3, sub.getRowCount());
      assertArrayEquals(new int[]{3, 1, 4}, baseRows(sub));
      assertEquals(-1, sub.getBaseRow(3));
      assertEquals(-1, sub.getBaseRow(-1));
      assertEquals(List.of("a", "a", "b"),
                   List.of(sub.getData("k", 0), sub.getData("k", 1), sub.getData("k", 2)));
      assertEquals(List.of(5.0, 1.0, 4.0),
                   List.of(sub.getData("v", 0), sub.getData("v", 1), sub.getData("v", 2)));
   }

   @Test
   void rangeConstructorTakesRowsFromStartToEnd() {
      SubDataSet sub = new SubDataSet(k5(), 1, 4);

      assertEquals(3, sub.getRowCount());
      assertArrayEquals(new int[]{1, 2, 3}, baseRows(sub));
      assertEquals(0, new SubDataSet(k5(), 2, 2).getRowCount());
   }

   @Test
   void singleConditionSelectsMatchingRows() {
      SubDataSet sub = new SubDataSet(k5(), cond("k", "a"));

      assertEquals(2, sub.getRowCount());
      assertArrayEquals(new int[]{1, 3}, baseRows(sub));
   }

   @Test
   void conditionsAreCombinedWithAnd() {
      assertArrayEquals(new int[]{2}, baseRows(new SubDataSet(g6(), cond("g", "A", "t", "b"))));
      assertEquals(0, new SubDataSet(g6(), cond("g", "A", "t", "zz")).getRowCount());
   }

   @Test
   void arrayConditionValueIsOr() {
      SubDataSet sub = new SubDataSet(k5(), cond("k", new Object[]{"a", "c"}));
      assertArrayEquals(new int[]{1, 2, 3}, baseRows(sub));
   }

   @Test
   void nullConditionMatchesNullAndNullString() {
      DefaultDataSet base = new DefaultDataSet(new Object[][]{
         {"k", "v"}, {"b", 1.0}, {null, 2.0}, {"a", 3.0}, {GDefaults.NULL_STR, 4.0}});
      assertArrayEquals(new int[]{1, 3}, baseRows(new SubDataSet(base, cond("k", null))));

      // control: the same path finds a real value in place of the null cell
      DefaultDataSet control = new DefaultDataSet(new Object[][]{
         {"k", "v"}, {"b", 1.0}, {"x", 2.0}, {"a", 3.0}, {GDefaults.NULL_STR, 4.0}});
      assertArrayEquals(new int[]{1}, baseRows(new SubDataSet(control, cond("k", "x"))));
   }

   @Test
   void numbersAreComparedByValueAcrossTypes() {
      DefaultDataSet base = new DefaultDataSet(new Object[][]{{"n"}, {2.0}, {3}});

      assertArrayEquals(new int[]{0}, baseRows(new SubDataSet(base, cond("n", 2))));
      assertArrayEquals(new int[]{1}, baseRows(new SubDataSet(base, cond("n", 3L))));
   }

   @Test
   @DisplayName("long ids above 2^53 are compared exactly, so neighbours do not match")
   void largeLongValuesAreDistinguished() {
      DefaultDataSet base = new DefaultDataSet(new Object[][]{
         {"id"}, {9007199254740992L}, {9007199254740993L}});

      assertArrayEquals(new int[]{1},
                        baseRows(new SubDataSet(base, cond("id", 9007199254740993L))));
   }

   @Test
   void noMatchGivesEmptyDataSetAndConditionKeyIsTheMap() {
      Map<String, Object> conds = cond("k", "zz");
      SubDataSet sub = new SubDataSet(k5(), conds);

      assertEquals(0, sub.getRowCount());
      assertEquals(-1, sub.getBaseRow(0));
      assertSame(conds, sub.getConditionKey());
   }

   @Test
   void allRowsMatchingBehavesLikeTheBase() {
      SubDataSet all = new SubDataSet(k5(), cond("k", new Object[]{"a", "b", "c"}));
      assertEquals(5, all.getRowCount());
      assertArrayEquals(new int[]{0, 1, 2, 3, 4}, baseRows(all));

      SubDataSet empty = new SubDataSet(k5(), new HashMap<>());
      assertEquals(5, empty.getRowCount());
      assertArrayEquals(new int[]{0, 1, 2, 3, 4}, baseRows(empty));
   }

   // ------------------------------------------------------------ reverse lookup

   @Test
   void getRowFromBaseUsesBinarySearchEncoding() {
      SubDataSet sub = new SubDataSet(k5(), new int[]{1, 3, 4});

      assertEquals(1, sub.getRowFromBase(3));
      assertEquals(0, sub.getRowFromBase(1));
      assertEquals(2, sub.getRowFromBase(4));
      // absent rows: -(insertion point) - 1
      assertEquals(-2, sub.getRowFromBase(2));
      assertEquals(-1, sub.getRowFromBase(0));
      assertEquals(-4, sub.getRowFromBase(9));
   }

   @Test
   void getRowFromBaseOnIdenticalIsCappedToRowCount() {
      SubDataSet sub = new SubDataSet(k5(), cond("k", new Object[]{"a", "b", "c"}));

      assertEquals(3, sub.getRowFromBase(3));
      assertEquals(5, sub.getRowFromBase(10));
   }

   // ---------------------------------------------------------------- large

   @ParameterizedTest
   @ValueSource(ints = {999, 1000, 1001})
   void mappingAroundTheFragmentThreshold(int n) {
      DefaultDataSet base = big(3000, false);
      int[] mapping = new int[n];

      for(int i = 0; i < n; i++) {
         mapping[i] = 2 * i;
      }

      SubDataSet sub = new SubDataSet(base, mapping);

      assertEquals(n, sub.getRowCount());

      for(int i : new int[]{0, n / 2, n - 1}) {
         assertEquals(2 * i, sub.getBaseRow(i));
         assertEquals(i, sub.getRowFromBase(2 * i));
      }

      for(int i = 0; i < n; i++) {
         assertEquals(base.getData(0, 2 * i), sub.getData(0, i));
         assertEquals(base.getData(1, 2 * i), sub.getData(1, i));
      }
   }

   @Test
   void mappingWith65535RowsKeepsEveryRow() {
      checkIdentityMapping(65535);
   }

   private static void checkIdentityMapping(int n) {
      DefaultDataSet base = big(66000, false);
      int[] mapping = new int[n];

      for(int i = 0; i < n; i++) {
         mapping[i] = i;
      }

      SubDataSet sub = new SubDataSet(base, mapping);

      assertEquals(n, sub.getRowCount());
      assertEquals(n - 1, sub.getBaseRow(n - 1));
   }

   @Test
   void conditionMatchingMoreThanAThousandRows() {
      SubDataSet sub = new SubDataSet(big(3000, true), cond("k", "x"));

      assertEquals(1500, sub.getRowCount());

      for(int i : new int[]{0, 1, 749, 1499}) {
         assertEquals(2 * i, sub.getBaseRow(i));
         assertEquals(i, sub.getRowFromBase(2 * i));
      }
   }

   // ------------------------------------------------------------- calc rows

   @Test
   void calcRowsAreMatchedByConditions() {
      DefaultDataSet base = k5();
      base.addCalcRow(new AddRow(new Object[]{"b", 7.0}));
      base.prepareCalc(null, null, true);
      SubDataSet sub = new SubDataSet(base, cond("k", "b"));

      assertEquals(3, sub.getRowCount());
      assertEquals(5, sub.getBaseRow(2));
   }

   // ---------------------------------------------------------- condition key

   @Test
   void conditionKeyIsTheConditionMapWhenPresent() {
      Map<String, Object> conds = cond("k", "a");
      assertSame(conds, new SubDataSet(k5(), conds).getConditionKey());
   }

   @Test
   void emptyMappingHasNoConditionKey() {
      assertNull(new SubDataSet(k5(), new int[0]).getConditionKey());
   }

   // ----------------------------------------------------------------- misc

   @Test
   void cloneDoesNotShareConditions() {
      SubDataSet sub = new SubDataSet(k5(), cond("k", "a"));
      SubDataSet copy = sub.clone();

      copy.getConditions().put("v", 1.0);

      assertEquals(1, sub.getConditions().size());
      assertEquals(sub.getRowCount(), new SubDataSet(k5(), cond("k", "a")).getRowCount());
      assertEquals(2, copy.getConditions().size());
   }

   @Test
   void cloneKeepsRowCountAndMapping() {
      SubDataSet sub = new SubDataSet(k5(), new int[]{3, 1, 4});
      SubDataSet copy = sub.clone();

      assertEquals(sub.getRowCount(), copy.getRowCount());
      assertArrayEquals(baseRows(sub), baseRows(copy));
   }

   @Test
   void innermostFlagDefaultsToTrue() {
      SubDataSet sub = new SubDataSet(k5(), new int[]{0});

      assertTrue(sub.isInnermost());
      sub.setInnermost(false);
      assertFalse(sub.isInnermost());
      sub.setInnermost(true);
      assertTrue(sub.isInnermost());
   }

   @Test
   void subOverSubReadsThroughBothMappings() {
      DefaultDataSet base = k5();
      SubDataSet inner = new SubDataSet(base, new int[]{4, 2, 1, 0});
      SubDataSet outer = new SubDataSet(inner, new int[]{3, 0});
      SubDataSet direct = new SubDataSet(base, new int[]{0, 4});

      assertEquals(2, outer.getRowCount());
      assertEquals(0, outer.getRootRow(0));
      assertEquals(4, outer.getRootRow(1));

      for(int i = 0; i < 2; i++) {
         assertEquals(direct.getData(0, i), outer.getData(0, i));
         assertEquals(direct.getData(1, i), outer.getData(1, i));
      }
   }
}
