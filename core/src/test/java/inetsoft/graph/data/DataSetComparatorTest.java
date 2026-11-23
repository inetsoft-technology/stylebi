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

import inetsoft.graph.data.CalcTestFixtures.*;
import inetsoft.test.*;
import inetsoft.util.DefaultComparator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.util.*;

import static inetsoft.graph.data.CalcTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of {@link CombinedDataSetComparator}, {@link DataSetComparator#getComparator}
 * and the comparator lookup of {@link AbstractDataSet} (base comparator plus calc rows).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSetComparatorTest {
   /**
    * Compares the character at an index of two strings.
    */
   private static class CharAt implements Comparator<Object>, Serializable {
      CharAt(int index) {
         this.index = index;
      }

      @Override
      public int compare(Object a, Object b) {
         calls++;
         return Character.compare(((String) a).charAt(index), ((String) b).charAt(index));
      }

      @Override
      public String toString() {
         return "CharAt" + index;
      }

      int calls = 0;
      private final int index;
   }

   /**
    * Puts one value after every other value, keeps the rest equal.
    */
   private static class LastValue implements Comparator<Object>, Serializable {
      LastValue(Object last) {
         this.last = last;
      }

      @Override
      public int compare(Object a, Object b) {
         if(Objects.equals(a, b)) {
            return 0;
         }

         return Objects.equals(a, last) ? 1 : Objects.equals(b, last) ? -1 : 0;
      }

      private final Object last;
   }

   /**
    * Compares two rows by column v, remembers the data set it is bound to.
    */
   private static class RowCmp implements DataSetComparator {
      RowCmp(DataSet bound) {
         this.bound = bound;
      }

      @Override
      public int compare(Object a, Object b) {
         return ((Comparable) a).compareTo(b);
      }

      @Override
      public int compare(DataSet data, int row1, int row2) {
         return ((Comparable) data.getData("v", row1)).compareTo(data.getData("v", row2));
      }

      @Override
      public DataSetComparator getComparator(DataSet data) {
         return new RowCmp(data);
      }

      @Override
      public Comparator getComparator(int row) {
         return this;
      }

      private final DataSet bound;
   }

   private static List<String> sorted(Comparator<Object> comp, String... values) {
      List<String> list = new ArrayList<>(Arrays.asList(values));
      list.sort(comp);
      return list;
   }

   private static CombinedDataSetComparator combined(Comparator<Object> c1, Comparator<Object> c2) {
      return new CombinedDataSetComparator("k", c1, c2);
   }

   private static int sign(int value) {
      return Integer.signum(value);
   }

   @Test
   @DisplayName("the second comparator has precedence, the first only breaks its ties")
   void secondComparatorHasPrecedence() {
      // c2 compares the first character and decides first, c1 (second character) breaks ties
      CombinedDataSetComparator comp = combined(new CharAt(1), new CharAt(0));

      assertEquals(Arrays.asList("a1", "a2", "b1", "b2"), sorted(comp, "b2", "a2", "a1", "b1"));
   }

   @Test
   @DisplayName("exchanging the members exchanges the precedence")
   void exchangedMembersExchangePrecedence() {
      CombinedDataSetComparator comp = combined(new CharAt(0), new CharAt(1));

      assertEquals(Arrays.asList("a1", "b1", "a2", "b2"), sorted(comp, "b2", "a2", "a1", "b1"));
   }

   @Test
   @DisplayName("the first comparator is only asked when the second returns equal")
   void firstComparatorOnlyAskedOnTie() {
      CharAt first = new CharAt(1);
      CombinedDataSetComparator decided = combined(first, (a, b) -> -1);

      decided.compare("a1", "b2");

      assertEquals(0, first.calls);

      CombinedDataSetComparator tied = combined(first, (a, b) -> 0);

      tied.compare("a1", "b2");

      assertEquals(1, first.calls);
   }

   @Test
   @DisplayName("the result of the deciding comparator is passed through unchanged")
   void resultValuesPassThrough() {
      assertEquals(-7, combined((a, b) -> 3, (a, b) -> -7).compare("a", "b"));
      assertEquals(3, combined((a, b) -> 3, (a, b) -> 0).compare("a", "b"));
      assertEquals(0, combined((a, b) -> 0, (a, b) -> 0).compare("a", "b"));
   }

   @Test
   @DisplayName("row comparison with plain members has the sign of the comparison of the cell values")
   void rowComparisonWithPlainMembers() {
      DefaultDataSet k5 = k5();
      CombinedDataSetComparator comp = combined(nat(), nat());

      for(int r1 = 0; r1 < 5; r1++) {
         for(int r2 = 0; r2 < 5; r2++) {
            int expected = sign(((String) k5.getData("k", r1)).compareTo((String) k5.getData("k", r2)));

            assertEquals(expected, sign(comp.compare(k5, r1, r2)), r1 + " vs " + r2);
         }
      }
   }

   @Test
   @DisplayName("row comparison asks a DataSetComparator member by rows first, then the plain member")
   void rowComparisonWithDataSetComparatorMember() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "k", "v" },
         { "b", 2.0 },
         { "a", 2.0 },
         { "c", 1.0 }
      });
      CombinedDataSetComparator comp = combined(nat(), new RowCmp(ds));

      assertTrue(comp.compare(ds, 2, 0) < 0, "smaller v first");
      assertTrue(comp.compare(ds, 0, 2) > 0);
      assertTrue(comp.compare(ds, 1, 0) < 0, "equal v: k decides, a before b");
      assertTrue(comp.compare(ds, 0, 1) > 0);
   }

   @Test
   @DisplayName("rebinding to a data set replaces the DataSetComparator member and keeps the plain one")
   void rebindReplacesDataSetComparatorMember() {
      DefaultDataSet k5 = k5();
      RowCmp row = new RowCmp(null);
      Comparator<Object> plain = nat();
      CombinedDataSetComparator comp = combined(row, plain);

      CombinedDataSetComparator rebound = (CombinedDataSetComparator) comp.getComparator(k5);

      assertNotSame(comp, rebound);
      assertEquals("k", rebound.getColumn());
      assertInstanceOf(RowCmp.class, rebound.getFirstComparator());
      assertNotSame(row, rebound.getFirstComparator());
      assertSame(k5, ((RowCmp) rebound.getFirstComparator()).bound);
      assertSame(plain, rebound.getSecondComparator());
   }

   @Test
   @DisplayName("rebinding also replaces a DataSetComparator in the second (higher precedence) member")
   void rebindReplacesSecondMember() {
      DefaultDataSet k5 = k5();
      RowCmp row = new RowCmp(null);
      Comparator<Object> plain = nat();

      CombinedDataSetComparator rebound =
         (CombinedDataSetComparator) combined(plain, row).getComparator(k5);

      assertSame(plain, rebound.getFirstComparator());
      assertInstanceOf(RowCmp.class, rebound.getSecondComparator());
      assertNotSame(row, rebound.getSecondComparator());
      assertSame(k5, ((RowCmp) rebound.getSecondComparator()).bound);
   }

   @Test
   @DisplayName("a filter rebinds a DataSetComparator of its base to itself")
   void filterRebindsBaseComparatorToItself() {
      DefaultDataSet k5 = k5();
      k5.setComparator("k", new RowCmp(null));
      AbstractDataSetFilter filter = new AbstractDataSetFilter(k5) {
      };

      Comparator comp = filter.getComparator("k");

      assertInstanceOf(RowCmp.class, comp);
      assertSame(filter, ((RowCmp) comp).bound);

      Comparator orig = filter.getOrigComparator("k");

      assertInstanceOf(RowCmp.class, orig);
      assertSame(filter, ((RowCmp) orig).bound);
   }

   @Test
   @DisplayName("the comparator of a row is the combined comparator itself")
   void comparatorOfRowIsSelf() {
      CombinedDataSetComparator comp = combined(nat(), nat());

      assertSame(comp, comp.getComparator(3));
   }

   @Test
   @DisplayName("accessors return the column and both members, toString names both")
   void accessorsAndToString() {
      Comparator<Object> c1 = new CharAt(0);
      Comparator<Object> c2 = new CharAt(1);
      CombinedDataSetComparator comp = combined(c1, c2);

      assertEquals("k", comp.getColumn());
      assertSame(c1, comp.getFirstComparator());
      assertSame(c2, comp.getSecondComparator());
      assertTrue(comp.toString().contains(c1.toString()));
      assertTrue(comp.toString().contains(c2.toString()));
   }

   @Test
   @DisplayName("static getComparator: null stays null, plain stays the same, a DataSetComparator is rebound")
   void staticGetComparator() {
      DefaultDataSet k5 = k5();
      Comparator<Object> plain = nat();
      RowCmp row = new RowCmp(null);

      assertNull(DataSetComparator.getComparator(null, k5));
      assertSame(plain, DataSetComparator.getComparator(plain, k5));

      Comparator rebound = DataSetComparator.getComparator(row, k5);

      assertInstanceOf(RowCmp.class, rebound);
      assertNotSame(row, rebound);
      assertSame(k5, ((RowCmp) rebound).bound);
   }

   @Test
   @DisplayName("without calc rows the data set gives its own comparator or null")
   void dataSetComparatorWithoutCalcRows() {
      DefaultDataSet none = k5();
      DefaultDataSet withNat = k5();
      Comparator<Object> comp = nat();
      withNat.setComparator("k", comp);

      assertNull(none.getComparator("k"));
      assertSame(comp, withNat.getComparator("k"));
   }

   @Test
   @DisplayName("a calc row comparator alone is returned as is, for its column only")
   void calcRowComparatorOnly() {
      DefaultDataSet k5 = k5();
      LastValue totalLast = new LastValue("Total");
      k5.addCalcRow(new AddRows(false, new Object[] { "Total", 0.0 }).comparator("k", totalLast));

      assertSame(totalLast, k5.getComparator("k"));
      assertNull(k5.getComparator("v"));
   }

   @Test
   @DisplayName("base comparator and calc row comparator combine, the calc row comparator wins")
   void baseAndCalcRowComparatorCombine() {
      DefaultDataSet k5 = k5();
      Comparator<Object> base = nat();
      LastValue totalLast = new LastValue("Total");
      k5.setComparator("k", base);
      k5.addCalcRow(new AddRows(false, new Object[] { "Total", 0.0 }).comparator("k", totalLast));

      Comparator comp = k5.getComparator("k");

      assertInstanceOf(CombinedDataSetComparator.class, comp);
      assertSame(base, ((CombinedDataSetComparator) comp).getFirstComparator());
      assertSame(totalLast, ((CombinedDataSetComparator) comp).getSecondComparator());
      assertEquals(Arrays.asList("a", "b", "Total"), sorted(comp, "Total", "a", "b"));
   }

   @Test
   @DisplayName("two calc rows: the later one has precedence, the earlier one breaks its ties")
   void twoCalcRowComparators() {
      DefaultDataSet k5 = k5();
      k5.setComparator("k", nat());
      k5.addCalcRow(new AddRows(false, new Object[] { "x", 0.0 }).comparator("k", new LastValue("x")));
      k5.addCalcRow(new AddRows(false, new Object[] { "y", 0.0 }).comparator("k", new LastValue("y")));

      assertEquals(Arrays.asList("a", "x", "y"), sorted(k5.getComparator("k"), "x", "y", "a"));
   }

   @Test
   @DisplayName("a calc row without comparator leaves the base comparator unchanged")
   void calcRowWithoutComparatorKeepsBase() {
      DefaultDataSet k5 = k5();
      Comparator<Object> base = nat();
      k5.setComparator("k", base);
      k5.addCalcRow(new AddRows(false, new Object[] { "z", 0.0 }));

      assertSame(base, k5.getComparator("k"));
   }

   @Test
   @DisplayName("the calc rows are asked for the column the comparator is requested for")
   void calcRowsAskedForRequestedColumn() {
      DefaultDataSet k5 = k5();
      AddRows row = new AddRows(false, new Object[] { "z", 0.0 });
      k5.addCalcRow(row);

      k5.getComparator("k");

      assertEquals(Collections.singletonList("k"), row.asked);
   }

   @Test
   @DisplayName("getOrigComparator is the registered instance, getComparator is rebound and combined")
   void origComparatorIsRegisteredInstance() {
      DefaultDataSet k5 = k5();
      RowCmp registered = new RowCmp(null);
      LastValue totalLast = new LastValue("Total");
      k5.setComparator("k", registered);
      k5.addCalcRow(new AddRows(false, new Object[] { "Total", 0.0 }).comparator("k", totalLast));

      assertSame(registered, k5.getOrigComparator("k"));

      Comparator comp = k5.getComparator("k");

      assertInstanceOf(CombinedDataSetComparator.class, comp);

      Comparator first = ((CombinedDataSetComparator) comp).getFirstComparator();

      assertInstanceOf(RowCmp.class, first);
      assertNotSame(registered, first);
      assertSame(k5, ((RowCmp) first).bound);
      assertSame(totalLast, ((CombinedDataSetComparator) comp).getSecondComparator());
   }

   @Test
   @DisplayName("a filter sees the calc row comparator of its base, combined with the base comparator")
   void filterSeesBaseCalcRowComparator() {
      DefaultDataSet k5 = k5();
      Comparator<Object> base = nat();
      LastValue totalLast = new LastValue("Total");
      k5.setComparator("k", base);
      k5.addCalcRow(new AddRows(false, new Object[] { "Total", 0.0 }).comparator("k", totalLast));
      AbstractDataSetFilter filter = new AbstractDataSetFilter(k5) {
      };

      Comparator comp = filter.getComparator("k");

      assertInstanceOf(CombinedDataSetComparator.class, comp);
      assertSame(base, ((CombinedDataSetComparator) comp).getFirstComparator());
      assertSame(totalLast, ((CombinedDataSetComparator) comp).getSecondComparator());
   }

   @Test
   @DisplayName("a filter over a base without comparator returns the calc row comparator itself")
   void filterWithoutBaseComparatorReturnsCalcRowComparator() {
      DefaultDataSet k5 = k5();
      LastValue totalLast = new LastValue("Total");
      k5.addCalcRow(new AddRows(false, new Object[] { "Total", 0.0 }).comparator("k", totalLast));
      AbstractDataSetFilter filter = new AbstractDataSetFilter(k5) {
      };

      assertSame(totalLast, filter.getComparator("k"));
   }

   @Test
   @DisplayName("combined comparator is antisymmetric and transitive over random values")
   void combinedComparatorIsATotalOrder() {
      CombinedDataSetComparator comp = combined(new CharAt(1), new CharAt(0));
      Random random = new Random(42);
      String alphabet = "abc";

      for(int i = 0; i < 200; i++) {
         String a = "" + alphabet.charAt(random.nextInt(3)) + alphabet.charAt(random.nextInt(3));
         String b = "" + alphabet.charAt(random.nextInt(3)) + alphabet.charAt(random.nextInt(3));
         String c = "" + alphabet.charAt(random.nextInt(3)) + alphabet.charAt(random.nextInt(3));

         assertEquals(-sign(comp.compare(b, a)), sign(comp.compare(a, b)), a + " " + b);

         if(comp.compare(a, b) <= 0 && comp.compare(b, c) <= 0) {
            assertTrue(comp.compare(a, c) <= 0, a + " " + b + " " + c);
         }
      }
   }

   @Test
   @DisplayName("null and empty string compare the same way against a value, and sort first")
   void nullAndEmptyStringSortFirst() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "k" },
         { null },
         { "" },
         { "a" }
      });
      CombinedDataSetComparator comp = combined(new DefaultComparator(true),
                                                new DefaultComparator(true));

      int nullVsValue = comp.compare(ds, 0, 2);
      int emptyVsValue = comp.compare(ds, 1, 2);

      assertTrue(nullVsValue < 0);
      assertEquals(sign(emptyVsValue), sign(nullVsValue));
   }

   @Test
   @DisplayName("a serialized and read combined comparator keeps its column and ordering")
   void serializationRoundTrip() throws Exception {
      CombinedDataSetComparator comp = combined(new CharAt(1), new CharAt(0));
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(comp);
      }

      CombinedDataSetComparator copy;

      try(ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
         copy = (CombinedDataSetComparator) in.readObject();
      }

      assertEquals("k", copy.getColumn());
      assertEquals(Arrays.asList("a1", "a2", "b1", "b2"), sorted(copy, "b2", "a2", "a1", "b1"));
   }

   @Test
   @DisplayName("the waterfall comparator sorts the Total header after every value and keeps the base order")
   void sumHeaderComparatorSortsTotalLast() {
      DefaultDataSet k5 = k5();
      k5.setComparator("k", nat());
      SumDataSet sum = new SumDataSet(k5, new String[] { "v" }, "k");
      sum.prepareCalc(null, null, true);

      Comparator comp = sum.getComparator("k");

      assertEquals(Arrays.asList("a", "b", "c", "Total"), sorted(comp, "Total", "c", "a", "b"));
   }
}
