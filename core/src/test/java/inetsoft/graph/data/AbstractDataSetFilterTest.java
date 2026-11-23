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

import inetsoft.graph.EGraph;
import inetsoft.graph.coord.Coordinate;
import inetsoft.uql.viewsheet.ColumnNotFoundException;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static inetsoft.graph.data.CalcTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link AbstractDataSetFilter} through an identity subclass and through
 * {@link SubDataSet} and {@link SortedDataSet} chains.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AbstractDataSetFilterTest {
   private static final Comparator<Object> NAT = CalcTestFixtures.nat();

   // ---------------------------------------------------------------- helpers

   /** Dimension n (Integer 1,2,3, not a measure) and measure m. */
   private static DefaultDataSet pn() {
      return new DefaultDataSet(new Object[][]{{"n", "m"}, {1, 10.0}, {2, 20.0}, {3, 30.0}}) {
         @Override
         protected boolean isMeasure0(String col) {
            return !"n".equals(col) && super.isMeasure0(col);
         }
      };
   }

   private static AbstractDataSetFilter idf(DataSet base) {
      return new AbstractDataSetFilter(base) {
      };
   }

   private static SubDataSet sub(DataSet base, int... mapping) {
      return new SubDataSet(base, mapping);
   }

   /** Calc column with a header and a field, returning the running sum of the field. */
   private static class RT implements CalcColumn {
      RT() {
         this("rt", "v");
      }

      RT(String header, String field) {
         this.header = header;
         this.field = field;
      }

      @Override
      public Object calculate(DataSet data, int row, boolean first, boolean last) {
         if(first) {
            sum = 0;
         }

         Object val = data.getData(field, row);

         if(val instanceof Number) {
            sum += ((Number) val).doubleValue();
         }

         return sum;
      }

      @Override
      public String getHeader() {
         return header;
      }

      @Override
      public Class getType() {
         return Double.class;
      }

      @Override
      public boolean isMeasure() {
         return true;
      }

      @Override
      public String getField() {
         return field;
      }

      private final String header;
      private final String field;
      private double sum;
   }

   /** Calc column that records every call and returns the row index. */
   private static class Rec implements CalcColumn {
      @Override
      public Object calculate(DataSet data, int row, boolean first, boolean last) {
         rows.add(row);
         return (double) row;
      }

      @Override
      public String getHeader() {
         return "rec";
      }

      @Override
      public Class getType() {
         return Double.class;
      }

      @Override
      public boolean isMeasure() {
         return true;
      }

      @Override
      public String getField() {
         return "v";
      }

      final List<Integer> rows = new ArrayList<>();
   }

   /** Post calc column that doubles another calc column. */
   private static class Dep implements CalcColumn {
      @Override
      public Object calculate(DataSet data, int row, boolean first, boolean last) {
         return 2 * ((Number) data.getData("rt", row)).doubleValue();
      }

      @Override
      public String getHeader() {
         return "dbl";
      }

      @Override
      public Class getType() {
         return Double.class;
      }

      @Override
      public boolean isMeasure() {
         return true;
      }

      @Override
      public String getField() {
         return "rt";
      }
   }

   /** Comparator on whole rows that remembers the data set it is bound to. */
   private static class RowCmp implements DataSetComparator {
      RowCmp() {
         this.bound = null;
      }

      RowCmp(DataSet bound) {
         this.bound = bound;
      }

      @Override
      public DataSetComparator getComparator(DataSet data) {
         return new RowCmp(data);
      }

      @Override
      public Comparator getComparator(int row) {
         return this;
      }

      @Override
      public int compare(DataSet data, int row1, int row2) {
         return ((Comparable) data.getData("v", row1)).compareTo(data.getData("v", row2));
      }

      @Override
      public int compare(Object o1, Object o2) {
         return ((Comparable) o1).compareTo(o2);
      }

      final DataSet bound;
   }

   /** Data set that records the arguments of prepareGraph. */
   private static class RecordingDataSet extends DefaultDataSet {
      RecordingDataSet() {
         super(new Object[][]{{"k", "v"}, {"a", 1.0}});
      }

      @Override
      public void prepareGraph(EGraph graph, Coordinate coord, DataSet dataset) {
         calls++;
         this.graph = graph;
         this.coord = coord;
         this.dataset = dataset;
      }

      int calls;
      EGraph graph;
      Coordinate coord;
      DataSet dataset;
   }

   // ------------------------------------------------------- identity filter

   @Test
   void identityFilterEqualsBase() {
      DefaultDataSet base = k5();
      AbstractDataSetFilter filter = idf(base);

      assertEquals(base.getRowCount(), filter.getRowCount());
      assertEquals(base.getColCount(), filter.getColCount());

      for(int c = 0; c < base.getColCount(); c++) {
         String header = base.getHeader(c);
         assertEquals(header, filter.getHeader(c));
         assertEquals(c, filter.indexOfHeader(header));
         assertEquals(base.getType(header), filter.getType(header));
         assertEquals(base.isMeasure(header), filter.isMeasure(header));

         for(int r = 0; r < base.getRowCount(); r++) {
            assertEquals(base.getData(c, r), filter.getData(c, r));
         }
      }
   }

   @Test
   void identityFilterMapsRowsAndColumnsToThemselves() {
      AbstractDataSetFilter filter = idf(k5());

      for(int r = 0; r < 5; r++) {
         assertEquals(r, filter.getBaseRow(r));
      }

      assertEquals(0, filter.getBaseCol(0));
      assertEquals(1, filter.getBaseCol(1));
      assertEquals(-1, filter.getBaseCol(2));
   }

   // ---------------------------------------------------------- root mapping

   @Test
   void rootRowAndColumnThroughAChain() {
      DefaultDataSet base = k5();
      base.setComparator("k", NAT);
      SubDataSet sub = sub(base, 2, 3, 0);
      SortedDataSet sorted = new SortedDataSet(sub, "k");

      // sub rows are r2(c), r3(a), r0(b), sorted by k: a, b, c
      assertEquals(1, sorted.getBaseRow(0));
      assertEquals(2, sorted.getBaseRow(1));
      assertEquals(0, sorted.getBaseRow(2));
      assertEquals(3, sorted.getRootRow(0));
      assertEquals(0, sorted.getRootRow(1));
      assertEquals(2, sorted.getRootRow(2));
      assertEquals(0, sorted.getRootCol(0));
      assertSame(base, sorted.getRootDataSet());
      assertSame(sub, sorted.getDataSet());
   }

   @Test
   void rowWithoutBaseRowHasNoRootRow() {
      SubDataSet sub = sub(k5(), 2, 3, 0);

      assertEquals(-1, sub.getBaseRow(3));
      assertEquals(-1, sub.getRootRow(3));
      assertTrue(sub.getRootRow(-1) < 0);
   }

   @Test
   void rootColumnOfAFilterCalcColumnIsMissing() {
      AbstractDataSetFilter filter = idf(k5());
      filter.addCalcColumn(new RT());
      filter.prepareCalc(null, null, true);

      assertEquals(-1, filter.getRootCol(2));
      assertEquals(0, filter.getRootCol(0));
      assertEquals(1, filter.getRootCol(1));

      AbstractDataSetFilter nested = idf(sub(k5(), 1, 2));
      assertEquals(0, nested.getRootCol(0));
      assertEquals(1, nested.getRootCol(1));
   }

   // ------------------------------------------------------- calc management

   @Test
   void calcColumnsAreMergedBaseFirstWithoutDuplicates() {
      CalcColumn c1 = new RT("c1", "v");
      CalcColumn c2 = new RT("c2", "v");
      DefaultDataSet base = k5();
      base.addCalcColumn(c1);

      AbstractDataSetFilter own = idf(base);
      own.addCalcColumn(c2);
      assertEquals(List.of(c1, c2), own.getCalcColumns(false));
      assertEquals(List.of(c2), own.getCalcColumns(true));

      // the same instance on both levels is listed once
      AbstractDataSetFilter both = idf(base);
      both.addCalcColumn(c2);
      both.addCalcColumn(c1);
      assertEquals(List.of(c1, c2), both.getCalcColumns(false));
   }

   @Test
   @DisplayName("pinned: calc rows are merged own first, base rows added when not yet listed")
   void calcRowsAreMergedOwnFirst() {
      CalcRow a = new AddRows(false, new Object[]{"x", 1.0});
      CalcRow b = new AddRows(false, new Object[]{"y", 2.0});
      DefaultDataSet base = k5();
      base.addCalcRow(a);
      AbstractDataSetFilter filter = idf(base);
      filter.addCalcRow(b);
      filter.addCalcRow(a);

      assertEquals(List.of(b, a), filter.getCalcRows());
   }

   @Test
   void removeCalcValuesCascadeToTheBase() {
      // everything removed
      DefaultDataSet base = preparedBase();
      AbstractDataSetFilter filter = idf(base);
      assertEquals(6, base.getRowCount());
      filter.removeCalcValues();
      assertEquals(5, base.getRowCount());
      assertNull(base.getData(2, 0));

      // only the column values removed
      base = preparedBase();
      filter = idf(base);
      filter.removeCalcColValues();
      assertEquals(6, base.getRowCount());
      assertNull(base.getData(2, 0));

      // only the row values removed
      base = preparedBase();
      filter = idf(base);
      filter.removeCalcRowValues();
      assertEquals(5, base.getRowCount());
      assertEquals(2.0, base.getData(2, 0));
   }

   private static DefaultDataSet preparedBase() {
      DefaultDataSet base = k5();
      base.addCalcColumn(new RT());
      base.addCalcRow(new AddRows(false, new Object[]{"z", 9.0}));
      base.prepareCalc(null, null, true);
      return base;
   }

   @Test
   void filterCalcColumnIsComputedOverTheFilteredRows() {
      SubDataSet filter = sub(k5(), 3, 1, 4);
      filter.addCalcColumn(new RT());
      filter.prepareCalc(null, null, true);

      // v is 5, 1, 4 over the filter rows
      assertEquals(5.0, filter.getData("rt", 0));
      assertEquals(6.0, filter.getData("rt", 1));
      assertEquals(10.0, filter.getData("rt", 2));
   }

   @Test
   void baseCalcColumnIsReadThroughTheFilter() {
      DefaultDataSet base = k5();
      base.addCalcColumn(new RT());
      base.prepareCalc(null, null, true);
      SubDataSet filter = sub(base, 3, 1, 4);

      assertEquals(3, filter.getColCount());
      assertEquals(11.0, filter.getData("rt", 0));
      assertEquals(3.0, filter.getData("rt", 1));
      assertEquals(15.0, filter.getData("rt", 2));
      assertEquals("rt", filter.getHeader(2));
      assertEquals(2, filter.indexOfHeader("rt"));
   }

   @Test
   void columnCountFollowsBaseChangesMadeDirectly() {
      DefaultDataSet base = k5();
      AbstractDataSetFilter filter = idf(base);
      assertEquals(2, filter.getColCount());

      base.addCalcColumn(new RT());
      base.prepareCalc(null, null, true);
      assertEquals(3, filter.getColCount());

      base.removeCalcValues();
      assertEquals(2, filter.getColCount());
   }

   @Test
   void indexOfHeaderAllFlagIsForwardedToTheBase() {
      DefaultDataSet base = k5();
      base.addCalcColumn(new RT());
      AbstractDataSetFilter filter = idf(base);

      assertEquals(-1, filter.indexOfHeader("rt"));
      assertEquals(2, filter.indexOfHeader("rt", true));
   }

   @Test
   void deepCloneCopiesTheBaseAndShallowCloneSharesIt() {
      DefaultDataSet base = k5();
      base.addCalcColumn(new RT());
      AbstractDataSetFilter filter = idf(base);

      AbstractDataSetFilter deep = (AbstractDataSetFilter) filter.clone(false);
      AbstractDataSetFilter shallow = (AbstractDataSetFilter) filter.clone(true);

      assertNotSame(base, deep.getDataSet());
      ((AbstractDataSet) deep.getDataSet()).addCalcColumn(new RT("extra", "v"));
      assertEquals(1, base.getCalcColumns().size());
      assertSame(base, shallow.getDataSet());
   }

   @Test
   void disposeReachesTheBaseButNotThroughADeepClone() {
      DefaultDataSet base = k5();
      AbstractDataSetFilter filter = idf(base);
      filter.dispose();

      assertTrue(base.isDisposed());
      assertTrue(filter.isDisposed());

      DefaultDataSet base2 = k5();
      AbstractDataSetFilter filter2 = idf(base2);
      AbstractDataSetFilter copy = (AbstractDataSetFilter) filter2.clone(false);
      copy.dispose();

      assertTrue(copy.isDisposed());
      assertFalse(base2.isDisposed());
      assertFalse(filter2.isDisposed());
   }

   @Test
   void typeAndMeasureAreDelegatedAndUnknownColumnThrows() {
      SubDataSet sub = sub(k5(), 0, 1);

      assertEquals(Double.class, sub.getType("v"));
      assertTrue(sub.isMeasure("v"));
      assertThrows(ColumnNotFoundException.class, () -> sub.getType("zz"));
   }

   @Test
   void dataSetComparatorIsRebindToTheFilter() {
      DefaultDataSet base = k5();
      base.setComparator("k", new RowCmp());
      base.setComparator("v", NAT);
      AbstractDataSetFilter filter = idf(base);

      RowCmp fromFilter = (RowCmp) filter.getComparator("k");
      RowCmp fromBase = (RowCmp) base.getComparator("k");

      assertSame(filter, fromFilter.bound);
      assertSame(base, fromBase.bound);
      assertSame(NAT, filter.getComparator("v"));
   }

   @Test
   void origComparatorIsBoundToTheFilterToo() {
      DefaultDataSet base = new DefaultDataSet(new Object[][]{
         {"k", "v"}, {"b", 2.0}, {"a", 1.0}});
      RowCmp registered = new RowCmp();
      base.setComparator("k", registered);
      AbstractDataSetFilter filter = idf(base);

      assertSame(filter, ((RowCmp) filter.getOrigComparator("k")).bound);
      assertSame(filter, ((RowCmp) filter.getComparator("k")).bound);
      assertSame(registered, base.getOrigComparator("k"));
   }

   @Test
   void prepareGraphIsDelegatedToTheBase() {
      RecordingDataSet base = new RecordingDataSet();
      AbstractDataSetFilter filter = idf(base);
      EGraph graph = mock(EGraph.class);
      Coordinate coord = mock(Coordinate.class);
      DataSet dataset = k5();

      filter.prepareGraph(graph, coord, dataset);

      assertEquals(1, base.calls);
      assertSame(graph, base.graph);
      assertSame(coord, base.coord);
      assertSame(dataset, base.dataset);
   }

   @Test
   void rowCountIncludesBaseCalcRows() {
      DefaultDataSet base = k5();
      base.addCalcRow(new AddRows(false, new Object[]{"z", 9.0}));
      base.prepareCalc(null, null, true);
      AbstractDataSetFilter filter = idf(base);

      assertEquals(6, filter.getRowCount());
      assertEquals("z", filter.getData(0, 5));
      assertEquals(9.0, filter.getData(1, 5));
   }

   @Test
   void ownCalcColumnIsIndexedAfterBaseCalcColumns() {
      DefaultDataSet base = k5();
      base.addCalcColumn(new RT());
      base.prepareCalc(null, null, true);
      AbstractDataSetFilter filter = idf(base);
      filter.addCalcColumn(new Rec());
      filter.prepareCalc(null, null, true);

      List<String> headers = new ArrayList<>();

      for(CalcColumn col : filter.getCalcColumns(false)) {
         headers.add(col.getHeader());
      }

      assertEquals(List.of("rt", "rec"), headers);
      assertEquals(3, filter.indexOfHeader("rec"));
      assertEquals("rec", filter.getHeader(3));
   }

   @Test
   void chainOfSubAndSortedMatchesTheBaseCellByCell() {
      for(long seed = 1; seed <= 20; seed++) {
         Random rnd = new Random(seed);
         Object[][] rows = new Object[201][];
         rows[0] = new Object[]{"k", "v"};

         for(int i = 0; i < 200; i++) {
            rows[i + 1] = new Object[]{"key" + rnd.nextInt(10), (double) i};
         }

         DefaultDataSet base = new DefaultDataSet(rows);
         base.setComparator("k", NAT);

         List<Integer> all = new ArrayList<>();

         for(int i = 0; i < 200; i++) {
            all.add(i);
         }

         Collections.shuffle(all, rnd);
         int[] mapping = all.subList(0, 50 + rnd.nextInt(100)).stream()
            .mapToInt(Integer::intValue).toArray();
         SubDataSet sub = new SubDataSet(base, mapping);
         SortedDataSet sorted = new SortedDataSet(sub, "k");

         assertEquals(mapping.length, sorted.getRowCount());

         for(int i = 0; i < sorted.getRowCount(); i++) {
            int root = sub.getBaseRow(sorted.getBaseRow(i));
            assertEquals(root, sorted.getRootRow(i), "seed " + seed);

            for(int c = 0; c < 2; c++) {
               assertEquals(base.getData(c, root), sorted.getData(c, i), "seed " + seed);
            }
         }
      }
   }

   @Test
   void filterOwnCalcRowIsReadThroughTheFilter() {
      DefaultDataSet base = k5();
      AbstractDataSetFilter filter = idf(base);
      filter.addCalcRow(new AddRows(false, new Object[]{"z", 9.0}));
      filter.prepareCalc(null, null, true);

      assertEquals(6, filter.getRowCount());
      assertEquals("z", filter.getData(0, 5));
      assertEquals(9.0, filter.getData(1, 5));
      assertEquals(5, base.getRowCount());
   }

   @Test
   void disposedBaseSkipsCalculation() {
      DefaultDataSet base = k5();
      AbstractDataSetFilter filter = idf(base);
      Rec rec = new Rec();
      filter.addCalcColumn(rec);
      base.dispose();

      assertTrue(filter.isDisposed());
      assertDoesNotThrow(() -> filter.prepareCalc(null, null, true));
      assertTrue(rec.rows.isEmpty());
   }

   @Test
   void projectForwardIsPassedDownAndDoesNotCycle() {
      DefaultDataSet base = pn();
      AbstractDataSetFilter inner = idf(base);
      AbstractDataSetFilter outer = idf(inner);

      outer.setRowsProjectedForward(2);

      assertEquals(2, inner.getRowsProjectedForward());
      assertEquals(2, base.getRowsProjectedForward());
      assertEquals(0, outer.rowsProjectedForward);
   }

   @Test
   void calcColumnOnTheFilterCanReadABaseCalcColumn() {
      DefaultDataSet base = k5();
      base.addCalcColumn(new RT());
      base.prepareCalc(null, null, true);
      AbstractDataSetFilter filter = idf(base);
      filter.addCalcColumn(new Dep());
      filter.prepareCalc(null, null, true);

      double[] expected = {4, 6, 12, 22, 30};

      for(int i = 0; i < expected.length; i++) {
         assertEquals(expected[i], filter.getData("dbl", i));
      }
   }
}
