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
      // ROWS frame bounds. startBound == null means "frame-less" — the kernel falls back to the
      // Phase-1/2 default (running for an ordered aggregate, whole partition otherwise, whole
      // partition for FIRST_VALUE/LAST_VALUE). Bound tokens: UNBOUNDED_PRECEDING,
      // UNBOUNDED_FOLLOWING, CURRENT_ROW, PRECEDING (with offset), FOLLOWING (with offset).
      public final String startBound;
      public final int startOffset;
      public final String endBound;
      public final int endOffset;
      // Phase 4: frame mode ("ROWS" default | "RANGE" | "GROUPS") and, for a date/time RANGE
      // value-offset, the interval unit ("year"|"quarter"|"month"|"week"|"day"|"hour"|"minute"|
      // "second"). null/absent offsetUnit means the RANGE offset is a plain numeric value.
      public final String mode;
      public final String offsetUnit;

      public Spec(String header, String fn, int argCol, int n,
                  int[] partCols, int[] orderCols, boolean[] orderAsc) {
         this(header, fn, argCol, n, partCols, orderCols, orderAsc, null, 0, null, 0);
      }

      public Spec(String header, String fn, int argCol, int n,
                  int[] partCols, int[] orderCols, boolean[] orderAsc,
                  String startBound, int startOffset, String endBound, int endOffset) {
         this(header, fn, argCol, n, partCols, orderCols, orderAsc,
              startBound, startOffset, endBound, endOffset, "ROWS", null);
      }

      public Spec(String header, String fn, int argCol, int n,
                  int[] partCols, int[] orderCols, boolean[] orderAsc,
                  String startBound, int startOffset, String endBound, int endOffset,
                  String mode, String offsetUnit) {
         this.header = header;
         this.fn = fn == null ? "" : fn.toUpperCase();
         this.argCol = argCol;
         this.n = n;
         this.partCols = partCols == null ? new int[0] : partCols;
         this.orderCols = orderCols == null ? new int[0] : orderCols;
         this.orderAsc = orderAsc == null ? new boolean[0] : orderAsc;
         this.startBound = startBound;
         this.startOffset = startOffset;
         this.endBound = endBound;
         this.endOffset = endOffset;
         this.mode = mode == null ? "ROWS" : mode.toUpperCase();
         this.offsetUnit = offsetUnit;
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
         int[] fr = frameSlice(spec, idx, start, end, p);
         int hrows = table.getHeaderRowCount();
         return fr == null ? null : table.getObject(idx[fr[0]] + hrows, spec.argCol);
      }
      case "LAST_VALUE": {
         int[] fr = frameSlice(spec, idx, start, end, p);
         int hrows = table.getHeaderRowCount();
         return fr == null ? null : table.getObject(idx[fr[1]] + hrows, spec.argCol);
      }
      case "SUM": case "AVG": case "COUNT": case "MIN": case "MAX": {
         int[] fr = frameSlice(spec, idx, start, end, p);

         if(fr == null) {
            // Empty frame: COUNT is 0; every other aggregate is NULL over zero rows
            // (ANSI SQL / JDBC-pushdown parity — SUM of no rows is NULL, not 0).
            switch(spec.fn) {
            case "COUNT": return 0;
            default:      return null;   // SUM/AVG/MIN/MAX
            }
         }

         int hrows = table.getHeaderRowCount();
         double sum = 0, min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
         int cnt = 0;

         for(int q = fr[0]; q <= fr[1]; q++) {
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
         case "SUM":   return cnt == 0 ? null : sum;
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
         // lens supports has an explicit case above; anything genuinely unsupported must not be
         // silently mis-computed on the in-memory path.
         throw new RuntimeException(
            "window function '" + spec.fn + "' is not supported for in-memory computation");
      }
   }

   /**
    * The frame for {@code spec} at sorted position {@code p} within partition
    * {@code [start,end)}, as absolute indices into {@code idx} (inclusive), or {@code null} if
    * the frame is empty for this row.
    * <p>Frame-less ({@code spec.startBound == null}) reproduces the Phase-1/2 defaults exactly:
    * FIRST_VALUE/LAST_VALUE and an order-less aggregate see the whole partition; an aggregate
    * with an ORDER BY sees the running frame {@code [0,p]}. This branch is UNCHANGED from
    * Phase 3 (byte-parity).
    * <p>An explicit frame dispatches on {@code spec.mode}: {@code ROWS} (default) keeps the
    * Phase-3 {@link #boundPos} physical-offset logic unchanged; {@code RANGE}/{@code GROUPS} are
    * resolved by {@link #rangeSlice}/{@link #groupsSlice}, which return partition-relative
    * {@code [lo,hi]} (possibly out of {@code [0,size)}) and share the same clamp-to-empty logic
    * below as ROWS.
    */
   private int[] frameSlice(Spec spec, Integer[] idx, int start, int end, int p) {
      int size = end - start;
      int lo, hi;

      if(spec.startBound == null) {
         boolean valueFn = "FIRST_VALUE".equals(spec.fn) || "LAST_VALUE".equals(spec.fn);

         if(valueFn || spec.orderCols.length == 0) {
            lo = 0;
            hi = size - 1;         // whole partition
         }
         else {
            lo = 0;
            hi = p;                 // running
         }
      }
      else {
         switch(spec.mode) {
         case "RANGE": {
            int[] fr = rangeSlice(spec, idx, start, end, p);
            lo = fr[0];
            hi = fr[1];
            break;
         }
         case "GROUPS": {
            int[] fr = groupsSlice(spec, idx, start, end, p);
            lo = fr[0];
            hi = fr[1];
            break;
         }
         default:
            lo = boundPos(spec.startBound, spec.startOffset, p, size);   // ROWS
            hi = boundPos(spec.endBound, spec.endOffset, p, size);
         }
      }

      int loc = Math.max(0, lo);
      int hic = Math.min(size - 1, hi);

      if(loc > hic) {
         return null;   // empty frame
      }

      return new int[]{ start + loc, start + hic };
   }

   private static int boundPos(String bound, int offset, int p, int size) {
      switch(bound) {
      case "UNBOUNDED_PRECEDING": return 0;
      case "UNBOUNDED_FOLLOWING": return size - 1;
      case "CURRENT_ROW":         return p;
      case "PRECEDING":           return p - offset;
      case "FOLLOWING":           return p + offset;
      default:
         throw new RuntimeException("invalid window frame bound: " + bound);
      }
   }

   /**
    * RANGE frame resolution for partition-relative position {@code p} within
    * {@code [start,end)} (given as absolute {@code idx} indices). Returns partition-relative
    * {@code [lo,hi]} (may be out of {@code [0,size)}; the caller clamps).
    * <p>{@code CURRENT_ROW} is always resolved as a PEER walk — the contiguous run of rows tied
    * with {@code p} on the order key (via {@link #orderKeyCompare}) — never as a numeric value
    * threshold. This matters for a DESCENDING order key: e.g. order key 30,30,10 (desc) with
    * {@code RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW} must stop at the end of the
    * 30-peer-group (frame {30,30}, NOT {30,30,10}), even though the row with value 10
    * numerically satisfies "value ≤ 30"; a peer group is a POSITION in the sorted sequence, not
    * a value threshold, and the two only coincide for ASCENDING order.
    * <p>{@code PRECEDING}/{@code FOLLOWING} WITH an offset are genuine value bounds. Per the SQL
    * RANGE standard, the offset direction is relative to the ORDER BY SEQUENCE, not raw
    * arithmetic sign: for an ASCENDING key, {@code n PRECEDING} means "value ≥ current − n"; for
    * a DESCENDING key it means "value ≤ current + n" (preceding in a descending sequence is a
    * LARGER value). {@link #rangeStart}/{@link #rangeEnd} apply this directly.
    */
   private int[] rangeSlice(Spec spec, Integer[] idx, int start, int end, int p) {
      int size = end - start;
      boolean asc = spec.orderAsc.length == 0 || spec.orderAsc[0];
      int lo = rangeStart(spec, idx, start, size, p, asc);
      int hi = rangeEnd(spec, idx, start, size, p, asc);
      return new int[]{ lo, hi };
   }

   /** RANGE start-bound resolution (partition-relative). See {@link #rangeSlice}. */
   private int rangeStart(Spec spec, Integer[] idx, int start, int size, int p, boolean asc) {
      switch(spec.startBound) {
      case "UNBOUNDED_PRECEDING":
         return 0;
      case "CURRENT_ROW":
         return peerWalkLeft(spec, idx, start, p);
      case "PRECEDING": {
         int dataRowP = idx[start + p];
         int q = p;

         if(asc) {
            double threshold = offsetOrderValue(spec, dataRowP, -spec.startOffset);

            while(q > 0 && orderValue(spec, idx[start + q - 1]) >= threshold) {
               q--;
            }
         }
         else {
            double threshold = offsetOrderValue(spec, dataRowP, spec.startOffset);

            while(q > 0 && orderValue(spec, idx[start + q - 1]) <= threshold) {
               q--;
            }
         }

         return q;
      }
      default:
         throw new RuntimeException("invalid RANGE start bound: " + spec.startBound);
      }
   }

   /** RANGE end-bound resolution (partition-relative). See {@link #rangeSlice}. */
   private int rangeEnd(Spec spec, Integer[] idx, int start, int size, int p, boolean asc) {
      switch(spec.endBound) {
      case "UNBOUNDED_FOLLOWING":
         return size - 1;
      case "CURRENT_ROW":
         return peerWalkRight(spec, idx, start, size, p);
      case "FOLLOWING": {
         int dataRowP = idx[start + p];
         int q = p;

         if(asc) {
            double threshold = offsetOrderValue(spec, dataRowP, spec.endOffset);

            while(q < size - 1 && orderValue(spec, idx[start + q + 1]) <= threshold) {
               q++;
            }
         }
         else {
            double threshold = offsetOrderValue(spec, dataRowP, -spec.endOffset);

            while(q < size - 1 && orderValue(spec, idx[start + q + 1]) >= threshold) {
               q++;
            }
         }

         return q;
      }
      default:
         throw new RuntimeException("invalid RANGE end bound: " + spec.endBound);
      }
   }

   /** Walk left (decreasing partition-relative index) from {@code p} while tied on order key. */
   private int peerWalkLeft(Spec spec, Integer[] idx, int start, int p) {
      int q = p;

      while(q > 0 && orderKeyCompare(spec, idx[start + q - 1], idx[start + p]) == 0) {
         q--;
      }

      return q;
   }

   /** Walk right (increasing partition-relative index) from {@code p} while tied on order key. */
   private int peerWalkRight(Spec spec, Integer[] idx, int start, int size, int p) {
      int q = p;

      while(q < size - 1 && orderKeyCompare(spec, idx[start + q + 1], idx[start + p]) == 0) {
         q++;
      }

      return q;
   }

   /**
    * GROUPS frame resolution: offsets count DISTINCT order-key groups (peer groups) rather than
    * physical rows. Group boundaries are detected the same way as RANK/DENSE_RANK — a break is
    * where consecutive sorted rows differ on {@link #orderKeyCompare}. Returns
    * partition-relative {@code [lo,hi]} (may already reflect an empty frame via {@code lo>hi}).
    */
   private int[] groupsSlice(Spec spec, Integer[] idx, int start, int end, int p) {
      int size = end - start;
      int[] groupOf = new int[size];
      int g = 0;
      groupOf[0] = 0;

      for(int q = 1; q < size; q++) {
         if(orderKeyCompare(spec, idx[start + q - 1], idx[start + q]) != 0) {
            g++;
         }

         groupOf[q] = g;
      }

      int lastGroup = g;
      int pGroup = groupOf[p];

      int startGroup = groupBoundStart(spec.startBound, spec.startOffset, pGroup);
      int endGroup = groupBoundEnd(spec.endBound, spec.endOffset, pGroup, lastGroup);

      startGroup = Math.max(0, Math.min(lastGroup, startGroup));
      endGroup = Math.max(0, Math.min(lastGroup, endGroup));

      if(startGroup > endGroup) {
         return new int[]{ 0, -1 };   // empty frame — clamps to null in frameSlice
      }

      int lo = -1, hi = -1;

      for(int q = 0; q < size; q++) {
         if(groupOf[q] == startGroup && lo == -1) {
            lo = q;
         }

         if(groupOf[q] == endGroup) {
            hi = q;
         }
      }

      return new int[]{ lo, hi };
   }

   private int groupBoundStart(String bound, int offset, int pGroup) {
      switch(bound) {
      case "UNBOUNDED_PRECEDING": return 0;
      case "CURRENT_ROW":         return pGroup;
      case "PRECEDING":           return pGroup - offset;
      default:
         throw new RuntimeException("invalid GROUPS start bound: " + bound);
      }
   }

   private int groupBoundEnd(String bound, int offset, int pGroup, int lastGroup) {
      switch(bound) {
      case "UNBOUNDED_FOLLOWING": return lastGroup;
      case "CURRENT_ROW":         return pGroup;
      case "FOLLOWING":           return pGroup + offset;
      default:
         throw new RuntimeException("invalid GROUPS end bound: " + bound);
      }
   }

   /** value(dataRow) as a double for a numeric order key, or epoch-ms for a date/time key. */
   private double orderValue(Spec spec, int dataRow) {
      Object v = table.getObject(dataRow + table.getHeaderRowCount(), spec.orderCols[0]);

      if(v instanceof java.util.Date) {
         return ((java.util.Date) v).getTime();
      }

      return ((Number) v).doubleValue();
   }

   /**
    * {@code val(dataRow) + signedOffset} in the order key's units. Numeric keys: the offset is
    * added directly. Date/time keys: {@code spec.offsetUnit} selects the unit — fixed-width
    * units ({@code second, minute, hour, day, week}) are applied as a millisecond delta;
    * calendar units ({@code month, quarter, year}) are applied via per-row {@code java.time}
    * calendar arithmetic (28-31 day months, etc.) and then compared by epoch-ms — matching
    * Postgres {@code INTERVAL} semantics, where "1 month" is calendar-relative, not a fixed
    * 30-day delta.
    */
   private double offsetOrderValue(Spec spec, int dataRow, int signedOffset) {
      Object v = table.getObject(dataRow + table.getHeaderRowCount(), spec.orderCols[0]);

      if(v instanceof java.util.Date) {
         return dateOffsetMs((java.util.Date) v, spec.offsetUnit, signedOffset);
      }

      return ((Number) v).doubleValue() + signedOffset;
   }

   private static double dateOffsetMs(java.util.Date v, String unit, int signedOffset) {
      if(unit == null) {
         return v.getTime() + signedOffset;   // defensive: no unit given, treat as raw ms
      }

      switch(unit.toLowerCase()) {
      case "second": return v.getTime() + signedOffset * 1000L;
      case "minute": return v.getTime() + signedOffset * 60_000L;
      case "hour":   return v.getTime() + signedOffset * 3_600_000L;
      case "day":    return v.getTime() + signedOffset * 86_400_000L;
      case "week":   return v.getTime() + signedOffset * 7L * 86_400_000L;
      case "month": case "quarter": case "year": {
         java.time.ZoneId zone = java.time.ZoneId.systemDefault();
         java.time.LocalDateTime ldt =
            java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(v.getTime()), zone);

         switch(unit.toLowerCase()) {
         case "month":   ldt = ldt.plusMonths(signedOffset); break;
         case "quarter": ldt = ldt.plusMonths(signedOffset * 3L); break;
         default:        ldt = ldt.plusYears(signedOffset); break;
         }

         return ldt.atZone(zone).toInstant().toEpochMilli();
      }
      default:
         throw new RuntimeException("invalid RANGE offsetUnit: " + unit);
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
