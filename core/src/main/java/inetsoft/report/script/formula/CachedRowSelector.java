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

import inetsoft.uql.XTable;
import inetsoft.util.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.*;
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
   public static CachedRowSelector getSelector(XTable table, Map specs) {
      CachedRowSelectorRef ref;

      synchronized(selectorCache) {
         SelectorKey key = new SelectorKey(table, specs.keySet());
         ref = selectorCache.get(key);

         if(ref == null) {
            ref = new CachedRowSelectorRef(table, specs);
            selectorCache.put(key, ref);
         }
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

      TupleValues tupleValues = new TupleValues();

      for(int i = 0; i < values.length; i++) {
         Object value = values[i];

         if(gcolumns[i] >= 0) {
            value = getData(value, gcolumns[i]);
         }

         tupleValues.add(value);
      }

      Tuple tuple = new Tuple(tupleValues.toArray());
      currGroup = (RangeBitSet) groupmap.get(tuple);
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
         return currGroup.getStart();
      }

      return 0;
   }

   /**
    * Get the ending row index in the range.
    */
   public int getEndRow() {
      if(currGroup != null && !missingCol) {
         return currGroup.getEnd();
      }

      return 0;
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
         if(currGroup.get(row)) {
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
      TupleValues vec = new TupleValues();

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
    * Create a mapping from group values to rows (BitSet).
    */
   private void createMap(XTable table) {
      groupmap.clear();

      if(missingCol) {
         return;
      }

      for(int r = table.getHeaderRowCount(); table.moreRows(r); r++) {
         Tuple tuple = createTuple(table, r);
         BitSet rows = groupmap.get(tuple);

         if(rows == null) {
            rows = new RangeBitSet(r);
            groupmap.put(tuple, rows);
         }

         rows.set(r);
      }
   }

   private static class RangeBitSet extends BitSet {
      /**
       * @param baseIdx the smallest row that may be set.
       */
      public RangeBitSet(int baseIdx) {
         this.baseIdx = baseIdx;
         start = baseIdx;
         end = baseIdx;
      }

      @Override
      public void set(int idx) {
         if(idx < start) {
            start = idx;
         }

         if(idx > end) {
            end = idx;
         }

         super.set(idx - baseIdx);
      }

      @Override
      public boolean get(int idx) {
         if(idx < start || idx > end) {
            return false;
         }

         return super.get(idx - baseIdx);
      }

      public int getStart() {
         return start;
      }

      public int getEnd() {
         return end;
      }

      // idx range
      private int start;
      private int end;
      private int baseIdx; // all set() must be >= baseIdx
   }

   private static class SelectorKey {
      private final XTable table;
      private final Set groupKeys;

      SelectorKey(XTable table, Set groupKeys) {
         this.table = table;
         this.groupKeys = new HashSet(groupKeys);
      }

      @Override
      public boolean equals(Object obj) {
         if(!(obj instanceof SelectorKey)) {
            return false;
         }

         SelectorKey other = (SelectorKey) obj;
         return table == other.table && groupKeys.equals(other.groupKeys);
      }

      @Override
      public int hashCode() {
         return Objects.hash(System.identityHashCode(table), groupKeys);
      }
   }

   private static final class CachedRowSelectorRef {
      CachedRowSelectorRef(XTable table, Map specs) {
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
      private final Map specs;
      private volatile CachedRowSelector selector;
   }

   private XTable table;
   private Map groupspecs;
   private RangeBitSet currGroup; // current group rows
   private Map<Tuple, BitSet> groupmap = new Object2ObjectOpenHashMap<>(); // Vector values -> BitSet
   private ReentrantLock procLock = new ReentrantLock();
   // HashMap with custom SelectorKey ([XTable, Set]) -> CachedRowSelectorRef
   private static final DataCache<SelectorKey, CachedRowSelectorRef> selectorCache =
      new DataCache<>(20, 60000);
}
