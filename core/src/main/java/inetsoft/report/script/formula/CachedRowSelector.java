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
package inetsoft.report.script.formula;

import com.google.common.util.concurrent.Striped;
import inetsoft.uql.XTable;
import inetsoft.util.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An selector for selecting rows based on column value. The table group
 * structure is cached and reused.
 *
 * @version 8.0, 7/27/2005
 * @author InetSoft Technology Corp
 */
class CachedRowSelector extends AbstractGroupRowSelector {
   /**
    * Get a selector for the table and groups.
    */
   public static CachedRowSelector getSelector(XTable table,
                                               Map<String, NamedCellRange.GroupSpec> specs)
   {
      CachedRowSelectorRef ref;
      SelectorKey key = new SelectorKey(table, specs.keySet());
      Lock lock = locks.get(key);
      lock.lock();

      try {
         ref = selectorCache.get(key);

         if(ref == null) {
            ref = new CachedRowSelectorRef(table, specs);
            selectorCache.put(key, ref);
         }
      }
      finally {
         lock.unlock();
      }

      return ref.get();
   }

   /**
    * Create a cached selector for a specific table and group combination.
    */
   private CachedRowSelector(XTable table, Map groupspecs) {
      super(table, groupspecs);
      this.table = table;
   }

   /**
    * Initialize the group columns and values.
    */
   @Override
   protected void init(XTable table, Map groupspecs) {
      // @by stephenwebster, For Bug #19457
      // The groupspec keyset equality disregards order, however the groupmap tuples require
      // that the keyset be iterated in the same order.  So, here we force the map into a naturally
      // ordered map so the groupmap tuples will match regardless of the order they are supplied.
      super.init(table, new TreeMap(groupspecs));
   }

   /**
    * Set the new groups to process and lock the selector.
    */
   public void prepare(Map groupspecs) {
      procLock.lock();

      if(!groupspecs.equals(this.groupspecs)) {
         boolean keychanged = this.groupspecs == null ||
            !groupspecs.keySet().equals(this.groupspecs.keySet());
         this.groupspecs = groupspecs;

         init(table, groupspecs);

         // @optimization, since we create tuple for each group combination,
         // if the groups are not changed, no need to re-create the mapping
         if(keychanged) {
            createMap(table);
         }
      }

      // keep null apart from "", as the detail rows (and equalsGroup) do (Bug #82397)
      TupleValues tupleValues = new TupleValues(true);

      for(int i = 0; i < values.length; i++) {
         Object value = values[i];

         if(gcolumns[i] >= 0) {
            value = getData(value, gcolumns[i]);
         }

         tupleValues.add(value);
      }

      Tuple tuple = new Tuple(tupleValues.toArray());
      currGroup = groupmap.get(tuple);
   }

   private Object getData(Object value, int gcolumn) {
      Object data = CoreTool.getData(table.getColType(gcolumn), value);

      return value != null && data == null ? value : data;
   }

   /**
    * Must be called after the selector is no longer in use (after prepare).
    */
   public void endProcess() {
      procLock.unlock();
   }

   /**
    * Get the starting row index in the range.
    */
   public int getStartRow() {
      if(currGroup != null && !missingCol) {
         return currGroup.rows[0];
      }

      return 0;
   }

   /**
    * Get the ending row index in the range.
    */
   public int getEndRow() {
      if(currGroup != null && !missingCol) {
         return currGroup.rows[currGroup.rows.length - 1];
      }

      return 0;
   }

   /**
    * Get the rows of the current group, in ascending order. The array is the exact row set
    * of the group, so a caller can visit the group's rows without walking the interval
    * between the first and the last of them (which is the whole table when the rows of the
    * groups are interleaved, Bug #82482). It is immutable once built, so it can be used
    * after {@link #prepare(Map)} without further synchronization as long as the caller
    * holds the selector (before {@link #endProcess()}).
    *
    * @return the rows, or null if there is no current group or the group column is missing;
    * the caller then uses the {@link #match(XTable, int, int)} protocol over a range.
    */
   public int[] getGroupRows() {
      if(currGroup != null && !missingCol) {
         return currGroup.rows;
      }

      return null;
   }

   /**
    * Check if the row is included.
    */
   @Override
   public int match(XTable table, int row, int col) {
      if(missingCol) {
         return RangeProcessor.NO;
      }

      if(currGroup != null) {
         if(currGroup.contains(row)) {
            return RangeProcessor.YES;
         }
         else {
            return RangeProcessor.NO;
         }
      }

      return RangeProcessor.BREAK;
   }

   /**
    * Create a vector containing the group column values.
    */
   private Tuple createTuple(XTable table, int row) {
      TupleValues vec = new TupleValues(true);

      for(int i = 0; i < gcolumns.length; i++) {
         Object value = getValue(table, row, i);

         if(gcolumns[i] >= 0) {
            value = getData(value, gcolumns[i]);
         }

         vec.add(value);
      }

      return new Tuple(vec.toArray());
   }

   /**
    * Create a mapping from group values to rows.
    */
   private void createMap(XTable table) {
      groupmap.clear();

      if(missingCol) {
         return;
      }

      Map<Tuple, RowList> lists = new Object2ObjectOpenHashMap<>();

      for(int r = table.getHeaderRowCount(); table.moreRows(r); r++) {
         Tuple tuple = createTuple(table, r);
         RowList rows = lists.get(tuple);

         if(rows == null) {
            rows = new RowList();
            lists.put(tuple, rows);
         }

         rows.add(r);
      }

      // keep only the trimmed row arrays, not the growable lists
      for(Map.Entry<Tuple, RowList> e : lists.entrySet()) {
         groupmap.put(e.getKey(), new GroupRows(e.getValue().toArray()));
      }
   }

   /**
    * Growable list of ascending row indexes.
    */
   private static final class RowList {
      void add(int row) {
         if(size == rows.length) {
            rows = Arrays.copyOf(rows, size * 2);
         }

         rows[size++] = row;
      }

      int[] toArray() {
         return Arrays.copyOf(rows, size);
      }

      private int[] rows = new int[4];
      private int size;
   }

   /**
    * The exact rows (ascending) of one group.
    */
   private static final class GroupRows {
      GroupRows(int[] rows) {
         this.rows = rows;
      }

      boolean contains(int row) {
         return row >= rows[0] && row <= rows[rows.length - 1] &&
            Arrays.binarySearch(rows, row) >= 0;
      }

      private final int[] rows; // never empty, ascending
   }

   private static final class CachedRowSelectorRef {
      CachedRowSelectorRef(XTable table, Map<String, NamedCellRange.GroupSpec> specs) {
         this.table = table;
         this.specs = specs;
      }

      public synchronized CachedRowSelector get() {
         if(selector == null) {
            selector = new CachedRowSelector(table, specs);
         }

         return selector;
      }

      private final XTable table;
      private final Map<String, NamedCellRange.GroupSpec> specs;
      private volatile CachedRowSelector selector;
   }

   private XTable table;
   private Map groupspecs;
   private GroupRows currGroup; // current group rows
   private Map<Tuple, GroupRows> groupmap = new Object2ObjectOpenHashMap<>(); // Vector values -> rows
   private ReentrantLock procLock = new ReentrantLock();
   // HashMap with custom SelectorKey ([XTable, Set]) -> CachedRowSelectorRef
   private static final DataCache<SelectorKey, CachedRowSelectorRef> selectorCache =
      new DataCache<>(20, 60000);
   private static final Striped<Lock> locks = Striped.lazyWeakLock(256);
}
