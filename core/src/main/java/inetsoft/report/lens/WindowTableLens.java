/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.report.lens;

import inetsoft.report.TableFilter;
import inetsoft.report.TableLens;
import inetsoft.util.Tool;

import java.util.Arrays;

/**
 * In-memory SQL-window-function engine. Given a base {@link TableLens} and a set of
 * {@link Spec} window columns (resolved to base column indices), appends one computed column
 * per window function, computed over the rows sorted by (partitionBy…, orderBy…) and reset at
 * each partition boundary. Base columns are presented unchanged in base row order.
 *
 * Used only on the non-pushdown (tabular / non-mergeable) path — see
 * AssetQuery.getWindowTableLens. For a mergeable JDBC source the OVER is pushed down and this
 * lens is never constructed.
 */
public class WindowTableLens extends AbstractTableLens implements TableFilter {
   /** One window column: {@code fn(argCol) OVER (PARTITION BY partCols ORDER BY orderCols)}. */
   public static final class Spec {
      public final String header;
      public final String fn;        // ROW_NUMBER … MAX (upper-case)
      public final int argCol;       // base col index of the argument, or -1
      public final int n;            // NTILE bucket count / LAG-LEAD offset; 0 = unset
      public final int[] partCols;   // PARTITION BY base col indices (may be empty)
      public final int[] orderCols;  // ORDER BY base col indices (may be empty)
      public final boolean[] orderAsc;

      public Spec(String header, String fn, int argCol, int n,
                  int[] partCols, int[] orderCols, boolean[] orderAsc) {
         this.header = header;
         this.fn = fn == null ? "" : fn.toUpperCase();
         this.argCol = argCol;
         this.n = n;
         this.partCols = partCols == null ? new int[0] : partCols;
         this.orderCols = orderCols == null ? new int[0] : orderCols;
         this.orderAsc = orderAsc == null ? new boolean[0] : orderAsc;
      }
   }

   public WindowTableLens(TableLens table, Spec[] specs) {
      super();
      this.table = table;
      this.specs = specs == null ? new Spec[0] : specs;
      this.ncols = table.getColCount();
   }

   @Override
   public TableLens getTable() {
      return table;
   }

   @Override
   public void setTable(TableLens table) {
      this.table = table;
      invalidate();
   }

   @Override
   public synchronized void invalidate() {
      validated = false;
      computed = null;
   }

   @Override
   public int getBaseRowIndex(int row) {
      return row;   // base order preserved
   }

   @Override
   public int getBaseColIndex(int col) {
      return col < ncols ? col : -1;
   }

   @Override
   public int getColCount() {
      return ncols + specs.length;
   }

   @Override
   public int getRowCount() {
      return table.getRowCount();
   }

   @Override
   public boolean moreRows(int row) {
      return table.moreRows(row);
   }

   @Override
   public int getHeaderRowCount() {
      return table.getHeaderRowCount();
   }

   @Override
   public int getHeaderColCount() {
      return table.getHeaderColCount();
   }

   @Override
   public Class<?> getColType(int col) {
      if(col < ncols) {
         return table.getColType(col);
      }

      String fn = specs[col - ncols].fn;

      switch(fn) {
      case "ROW_NUMBER": case "RANK": case "DENSE_RANK": case "NTILE": case "COUNT":
         return Integer.class;
      case "PERCENT_RANK": case "CUME_DIST": case "AVG": case "SUM": case "MIN": case "MAX":
         // the kernels for these always return boxed Double (see kernel()), regardless of the
         // argument column's declared type — reporting the true runtime type here avoids a
         // downstream ClassCastException from a consumer that casts on the declared col type.
         return Double.class;
      default: // LAG/LEAD/FIRST_VALUE → return the original object, so its type is correct
         int a = specs[col - ncols].argCol;
         return a >= 0 ? table.getColType(a) : Double.class;
      }
   }

   @Override
   public Object getObject(int row, int col) {
      int hrows = table.getHeaderRowCount();

      if(col < ncols) {
         return table.getObject(row, col);
      }

      if(row < hrows) {
         return specs[col - ncols].header;   // header cell
      }

      if(!validated) {
         validate();
      }

      return computed[col - ncols][row - hrows];
   }

   // ── materialization ───────────────────────────────────────────────────────
   private synchronized void validate() {
      if(validated) {
         return;
      }

      table.moreRows(TableLens.EOT);
      final int hrows = table.getHeaderRowCount();
      final int nrows = Math.max(0, table.getRowCount() - hrows);   // data rows

      // sorted index of data rows: partition keys first (asc), then order keys (per dir)
      Integer[] idx = new Integer[nrows];

      for(int i = 0; i < nrows; i++) {
         idx[i] = i;
      }

      Arrays.sort(idx, this::compareRows);

      computed = new Object[specs.length][nrows];

      // walk partitions (a run of equal partition-key in sorted order)
      int start = 0;

      while(start < nrows) {
         int end = start + 1;

         while(end < nrows && samePartition(idx[start], idx[end])) {
            end++;
         }

         computePartition(idx, start, end);   // [start,end) is one partition, in order
         start = end;
      }

      validated = true;
   }

   private int compareRows(int a, int b) {
      int hrows = table.getHeaderRowCount();

      for(int c : partCols()) {           // partition cols always ascending
         int r = Tool.compare(table.getObject(a + hrows, c), table.getObject(b + hrows, c), true, true);

         if(r != 0) {
            return r;
         }
      }

      // order cols: any spec's orderBy — Phase-2 assumption: all window specs on a table
      // share compatible partition/order OR we sort per the FIRST spec that defines order.
      // Kernels that need a different order recompute their own key (see RANK/running).
      int[] ocols = orderCols();
      boolean[] oasc = orderAsc();

      for(int i = 0; i < ocols.length; i++) {
         int r = Tool.compare(table.getObject(a + hrows, ocols[i]), table.getObject(b + hrows, ocols[i]), true, true);

         if(r != 0) {
            return oasc[i] ? r : -r;
         }
      }

      return Integer.compare(a, b);       // stable
   }

   private boolean samePartition(int a, int b) {
      int hrows = table.getHeaderRowCount();

      for(int c : partCols()) {
         if(Tool.compare(table.getObject(a + hrows, c), table.getObject(b + hrows, c), true, true) != 0) {
            return false;
         }
      }

      return true;
   }

   // partition/order columns are shared across specs in Phase 2 (single window grammar per table);
   // use the first spec that declares them.
   private int[] partCols() {
      for(Spec s : specs) {
         if(s.partCols.length > 0) {
            return s.partCols;
         }
      }

      return new int[0];
   }

   private int[] orderCols() {
      for(Spec s : specs) {
         if(s.orderCols.length > 0) {
            return s.orderCols;
         }
      }

      return new int[0];
   }

   private boolean[] orderAsc() {
      for(Spec s : specs) {
         if(s.orderCols.length > 0) {
            return s.orderAsc;
         }
      }

      return new boolean[0];
   }

   /** Compute every spec for one partition given as sorted data-row indices idx[start..end). */
   private void computePartition(Integer[] idx, int start, int end) {
      int size = end - start;

      for(int s = 0; s < specs.length; s++) {
         Spec spec = specs[s];

         for(int p = 0; p < size; p++) {
            int baseRow = idx[start + p];               // 0-based data row
            computed[s][baseRow] = kernel(spec, idx, start, end, p);
         }
      }
   }

   /** Per-fn value for the row at sorted position {@code p} within partition [start,end). */
   private Object kernel(Spec spec, Integer[] idx, int start, int end, int p) {
      switch(spec.fn) {
      case "ROW_NUMBER":
         return p + 1;
      case "RANK": {
         // rank of a tie = position of the first row sharing its order key
         int first = p;

         while(first > 0 && orderKeyCompare(spec, idx[start + first - 1], idx[start + p]) == 0) {
            first--;
         }

         return first + 1;
      }
      case "DENSE_RANK": {
         int dr = 1;

         for(int q = 1; q <= p; q++) {
            if(orderKeyCompare(spec, idx[start + q - 1], idx[start + q]) != 0) {
               dr++;
            }
         }

         return dr;
      }
      case "NTILE": {
         int size = end - start;
         int k = Math.max(1, spec.n);
         int base = size / k, rem = size % k;   // first `rem` buckets get one extra (front-loaded)
         int bucket, cum = 0;

         for(bucket = 1; bucket <= k; bucket++) {
            int bsize = base + (bucket <= rem ? 1 : 0);

            if(p < cum + bsize) {
               break;
            }

            cum += bsize;
         }

         return bucket;
      }
      case "LAG": case "LEAD": {
         int off = spec.n > 0 ? spec.n : 1;
         int q = spec.fn.equals("LAG") ? p - off : p + off;

         if(q < 0 || q >= (end - start)) {
            return null;
         }

         int hrows = table.getHeaderRowCount();
         return table.getObject(idx[start + q] + hrows, spec.argCol);
      }
      case "FIRST_VALUE": {
         int hrows = table.getHeaderRowCount();
         return table.getObject(idx[start] + hrows, spec.argCol);
      }
      case "SUM": case "AVG": case "COUNT": case "MIN": case "MAX": {
         // running vs. whole-partition is per-spec (this spec's own ORDER BY presence),
         // not the table-wide sort order — a table may mix an ordered running aggregate
         // with an order-less partition-total aggregate (see WindowTableLensTest).
         boolean running = spec.orderCols.length > 0;
         int from = start, to = running ? (start + p + 1) : end;   // running → up to current row
         int hrows = table.getHeaderRowCount();
         double sum = 0, min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
         int cnt = 0;

         for(int q = from; q < to; q++) {
            Object v = table.getObject(idx[q] + hrows, spec.argCol);

            if(v == null) {
               continue;
            }

            double d = ((Number) v).doubleValue();
            sum += d;
            cnt++;
            min = Math.min(min, d);
            max = Math.max(max, d);
         }

         switch(spec.fn) {
         case "COUNT": return cnt;
         case "SUM":   return sum;
         case "AVG":   return cnt == 0 ? null : sum / cnt;
         case "MIN":   return cnt == 0 ? null : min;
         default:      return cnt == 0 ? null : max;   // MAX
         }
      }
      case "PERCENT_RANK": {
         int sz = end - start;
         int first = p;

         while(first > 0 && orderKeyCompare(spec, idx[start + first - 1], idx[start + p]) == 0) {
            first--;
         }

         return sz <= 1 ? 0.0 : ((double) first) / (sz - 1);   // (rank-1)/(n-1)
      }
      case "CUME_DIST": {
         int sz = end - start;
         int last = p;   // number of rows with order key <= current, minus 1

         while(last + 1 < sz && orderKeyCompare(spec, idx[start + last + 1], idx[start + p]) == 0) {
            last++;
         }

         return ((double) (last + 1)) / sz;
      }
      default:
         // Fail loud rather than silently returning null for every row. Every window function this
         // lens supports has an explicit case above; anything else (e.g. LAST_VALUE, which the SQL
         // path emits but the in-memory path cannot without a frame clause, or an unknown fn) must
         // not be silently mis-computed on the in-memory path.
         throw new RuntimeException(
            "window function '" + spec.fn + "' is not supported for in-memory computation");
      }
   }

   /**
    * Compare two data rows (0-based) by the ORDER BY key of a SPECIFIC spec (0 = same order key
    * = tie). Rank/dist kernels must tie on their OWN order, not the table-shared order: a
    * RANK/DENSE_RANK/PERCENT_RANK/CUME_DIST window with NO ORDER BY ties the whole partition
    * (spec.orderCols is empty → the loop below never runs → always 0), even when it shares the
    * lens with an ordered running aggregate. When the spec's own order is non-empty it equals
    * the table-shared order (divergent non-empty orders are rejected upstream in
    * AssetQuery.checkCompatiblePartitionAndOrder), so no behavior changes for ordered specs.
    */
   private int orderKeyCompare(Spec spec, int a, int b) {
      int hrows = table.getHeaderRowCount();
      int[] ocols = spec.orderCols;
      boolean[] oasc = spec.orderAsc;

      for(int i = 0; i < ocols.length; i++) {
         int r = Tool.compare(table.getObject(a + hrows, ocols[i]), table.getObject(b + hrows, ocols[i]), true, true);

         if(r != 0) {
            return oasc[i] ? r : -r;
         }
      }

      return 0;
   }

   private TableLens table;
   private final Spec[] specs;
   private final int ncols;
   private volatile boolean validated;
   private Object[][] computed;   // [specIndex][dataRow]
}
