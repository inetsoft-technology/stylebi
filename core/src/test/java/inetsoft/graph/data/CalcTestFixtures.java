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

import java.io.Serializable;
import java.util.*;

/**
 * Small data sets and test calc columns/rows shared by the data set machinery tests.
 */
final class CalcTestFixtures {
   private CalcTestFixtures() {
   }

   /**
    * Five rows {k, v}: b/2, a/1, c/3, a/5, b/4 (v is a Double measure).
    */
   static DefaultDataSet k5() {
      return new DefaultDataSet(new Object[][] {
         { "k", "v" },
         { "b", 2.0 },
         { "a", 1.0 },
         { "c", 3.0 },
         { "a", 5.0 },
         { "b", 4.0 }
      });
   }

   /**
    * Six rows {g, t, v}: A/c/1, A/a/2, A/b/4, B/b/8, B/a/16, B/c/32. Sorted by t with the
    * natural order, the base rows come in the order 1, 4, 2, 3, 0, 5.
    */
   static DefaultDataSet g6() {
      return new DefaultDataSet(new Object[][] {
         { "g", "t", "v" },
         { "A", "c", 1.0 },
         { "A", "a", 2.0 },
         { "A", "b", 4.0 },
         { "B", "b", 8.0 },
         { "B", "a", 16.0 },
         { "B", "c", 32.0 }
      });
   }

   /**
    * Natural ascending order (the compared values are Comparable).
    */
   static class Nat implements Comparator<Object>, Serializable {
      @Override
      @SuppressWarnings({ "unchecked", "rawtypes" })
      public int compare(Object a, Object b) {
         return ((Comparable) a).compareTo(b);
      }
   }

   static Comparator<Object> nat() {
      return new Nat();
   }

   /**
    * Running sum of the values of a field, in the order the rows are calculated; restarts
    * at the first row of a run. A null or non-numeric value counts as 0.
    */
   static class RunningSum implements CalcColumn {
      RunningSum(String header, String field) {
         this.header = header;
         this.field = field;
      }

      @Override
      public Object calculate(DataSet data, int row, boolean first, boolean last) {
         calls++;

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
      public Class<?> getType() {
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

      int calls = 0;
      private double sum = 0;
      private final String header;
      private final String field;
   }

   /**
    * One recorded calculate() call.
    */
   static final class Call {
      Call(int row, boolean first, boolean last, DataSet data) {
         this.row = row;
         this.first = first;
         this.last = last;
         this.data = data;
      }

      final int row;
      final boolean first;
      final boolean last;
      final DataSet data;
   }

   /**
    * Records every call and returns the row index it was called with (as a Double).
    */
   static class Recorder implements CalcColumn {
      Recorder(String header) {
         this(header, true);
      }

      Recorder(String header, boolean measure) {
         this.header = header;
         this.measure = measure;
      }

      @Override
      public Object calculate(DataSet data, int row, boolean first, boolean last) {
         calls.add(new Call(row, first, last, data));
         return (double) row;
      }

      @Override
      public void complete() {
         completes++;
      }

      @Override
      public String getHeader() {
         return header;
      }

      @Override
      public Class<?> getType() {
         return Double.class;
      }

      @Override
      public boolean isMeasure() {
         return measure;
      }

      @Override
      public String getField() {
         return null;
      }

      List<Integer> rows() {
         List<Integer> rows = new ArrayList<>();

         for(Call call : calls) {
            rows.add(call.row);
         }

         return rows;
      }

      final List<Call> calls = new ArrayList<>();
      int completes = 0;
      private final String header;
      private final boolean measure;
   }

   /**
    * Post calc: factor * (value of the field) + offset, null when the field value is not a
    * number.
    */
   static class Derived implements CalcColumn {
      Derived(String header, String field, double factor, double offset) {
         this.header = header;
         this.field = field;
         this.factor = factor;
         this.offset = offset;
      }

      @Override
      public Object calculate(DataSet data, int row, boolean first, boolean last) {
         Object val = data.getData(field, row);
         return val instanceof Number ? factor * ((Number) val).doubleValue() + offset : null;
      }

      @Override
      public void complete() {
         completes++;
      }

      @Override
      public String getHeader() {
         return header;
      }

      @Override
      public Class<?> getType() {
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

      int completes = 0;
      private final String header;
      private final String field;
      private final double factor;
      private final double offset;
   }

   /**
    * Returns a fixed value for every row (INVALID, null and NaN included).
    */
   static class Fixed implements CalcColumn {
      Fixed(String header, Object value, boolean measure) {
         this.header = header;
         this.value = value;
         this.measure = measure;
      }

      @Override
      public Object calculate(DataSet data, int row, boolean first, boolean last) {
         return value;
      }

      @Override
      public String getHeader() {
         return header;
      }

      @Override
      public Class<?> getType() {
         return value instanceof Number ? Double.class : String.class;
      }

      @Override
      public boolean isMeasure() {
         return measure;
      }

      @Override
      public String getField() {
         return null;
      }

      private final String header;
      private final Object value;
      private final boolean measure;
   }

   /**
    * Returns the value of a field below a limit and INVALID from the limit up.
    */
   static class InvalidFrom implements CalcColumn {
      InvalidFrom(String header, String field, double limit) {
         this.header = header;
         this.field = field;
         this.limit = limit;
      }

      @Override
      public Object calculate(DataSet data, int row, boolean first, boolean last) {
         double val = ((Number) data.getData(field, row)).doubleValue();
         return val >= limit ? CalcColumn.INVALID : val;
      }

      @Override
      public String getHeader() {
         return header;
      }

      @Override
      public Class<?> getType() {
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
      private final double limit;
   }

   /**
    * Calc row that appends fixed rows.
    */
   static class AddRows implements CalcRow {
      AddRows(boolean pre, Object[]... rows) {
         this.pre = pre;
         this.rows = Arrays.asList(rows);
      }

      @Override
      public List<Object[]> calculate(DataSet data) {
         calculated++;
         return new ArrayList<>(rows);
      }

      @Override
      public Comparator getComparator(String col) {
         asked.add(col);
         return comparators.get(col);
      }

      @Override
      public boolean isPreColumn() {
         return pre;
      }

      AddRows comparator(String col, Comparator<?> comp) {
         comparators.put(col, comp);
         return this;
      }

      final List<String> asked = new ArrayList<>();
      int calculated = 0;
      private final boolean pre;
      private final List<Object[]> rows;
      private final Map<String, Comparator<?>> comparators = new HashMap<>();
   }

   /**
    * Calc row (post column) that copies the last row of the data set it is given.
    */
   static class CopyLastRow implements CalcRow {
      @Override
      public List<Object[]> calculate(DataSet data) {
         Object[] row = new Object[data.getColCount()];

         for(int c = 0; c < row.length; c++) {
            row[c] = data.getData(c, data.getRowCount() - 1);
         }

         return new ArrayList<>(Collections.singletonList(row));
      }

      @Override
      public Comparator getComparator(String col) {
         return null;
      }
   }

   static List<Object> column(DataSet data, String col) {
      List<Object> list = new ArrayList<>();

      for(int r = 0; r < data.getRowCount(); r++) {
         list.add(data.getData(col, r));
      }

      return list;
   }
}
