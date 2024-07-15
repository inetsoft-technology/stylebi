/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.lens;

import inetsoft.mv.data.BitSet;
import inetsoft.report.TableLens;
import inetsoft.util.GroupedThread;
import inetsoft.util.ThreadContext;
import it.unimi.dsi.fastutil.objects.*;
import org.roaringbitmap.IntIterator;

import java.util.Map;

class HashJoinTable extends JoinTable {
   @SuppressWarnings("unchecked")
   public HashJoinTable(TableLens leftTable, TableLens rightTable,
                        int[] leftCols, int[] rightCols, int joinType,
                        boolean includeRightJoinCols, int maxRows)
   {
      super(leftTable, rightTable, leftCols, rightCols, joinType,
            includeRightJoinCols, maxRows);

      idxmaps = new Object2IntMap[leftCols.length];

      for(int i = 0; i < idxmaps.length; i++) {
         idxmaps[i] = new Object2IntOpenHashMap<>();
      }

      joinMap = new JoinMap();
      leftThread = new JoinThread(joinMap, leftTable, rightTable, this, leftCols, true, joinType);
      rightThread =
         new JoinThread(joinMap, leftTable, rightTable, this, rightCols, false, joinType);

      int leftCnt = leftTable.getRowCount();
      int rightCnt = rightTable.getRowCount();

      // scan smaller table first to keep larger table in sequence
      if(leftCnt > 0 && (rightCnt < 0 || leftCnt < rightCnt)) {
         leftThread.setPriority(Math.max(Thread.MIN_PRIORITY,
                                         Math.min(leftThread.getPriority() + 5,
                                                  Thread.MAX_PRIORITY)));
      }
      else if(rightCnt > 0 && (leftCnt < 0 || rightCnt < leftCnt)) {
         rightThread.setPriority(Math.min(Thread.MAX_PRIORITY,
                                          rightThread.getPriority() + 5));
      }

      leftThread.start();
      rightThread.start();
   }

   /**
    * Notification that all the rows have been added. Overridden to release
    * the base tables.
    */
   @Override
   public void complete() {
      try {
         // add rows for outer join: look in the map for join keys where the
         // non-outer table  has no rows and add the rows from the outer table
         // check for null tables because this might be called from the
         // constructor when invalid parameters are received
         if(getJoinType() != JoinTableLens.INNER_JOIN && getLeftTable() != null &&
            getRightTable() != null && joinMap != null) {
            BitSet leftall = new BitSet();
            BitSet rightall = new BitSet();

            for(BitSet[] joinRows : joinMap.map.values()) {
               if((getJoinType() & JoinTableLens.LEFT_OUTER_JOIN) ==
                  JoinTableLens.LEFT_OUTER_JOIN &&
                  joinRows[1].rowCount() == 0) {
                  leftall = leftall.or(joinRows[0]);
               }

               if((getJoinType() & JoinTableLens.RIGHT_OUTER_JOIN) ==
                  JoinTableLens.RIGHT_OUTER_JOIN &&
                  joinRows[0].rowCount() == 0) {
                  rightall = rightall.or(joinRows[1]);
               }
            }

            IntIterator iter = leftall.intIterator();

            while(iter.hasNext()) {
               addRow(iter.next(), -1);

               if(checkMaxRows()) {
                  break;
               }
            }

            iter = rightall.intIterator();

            while(iter.hasNext()) {
               addRow(-1, iter.next());

               if(checkMaxRows()) {
                  break;
               }
            }
         }

         // allow the base tables to be cleaned up, we don't need them anymore
         leftThread = null;
         rightThread = null;
         joinMap = null;
      }
      finally {
         flushPending();
         super.complete();
      }
   }

   @Override
   public boolean cancelJoin() {
      boolean cancelled = false;

      if(leftThread != null) {
         cancelled = true;
         leftThread.cancelJoin();
      }

      if(rightThread != null) {
         cancelled = true;
         rightThread.cancelJoin();
      }

      return cancelled;
   }

   private JoinMap joinMap;
   private JoinThread leftThread;
   private JoinThread rightThread;
   private transient Object2IntMap<Object>[] idxmaps;
   private static final int NULL = Integer.MAX_VALUE - 1;

   /**
    * Special map implentation for joining. This map is not synchronized since
    * the JoinTable needs to synchronize at more coursely-grained level that
    * that of put/get.
    */
   private static class JoinMap {
      /**
       * Creates a new instance of JoinMap.
       */
      public JoinMap() {
         map = new Object2ObjectOpenHashMap<>();
         leftComplete = rightComplete = false;
      }

      /**
       * Add a row for the left-hand table that has the specified join column
       * values.
       *
       * @param keys the key for the values.
       * @param row the index of the row.
       * @return the rows for right table with the same key value.
       */
      public BitSet putLeft(ListKey keys, int row) {
         BitSet[] rows = map.get(keys);

         if(rows == null) {
            rows = new BitSet[] { new BitSet(), new BitSet() };
            map.put(keys, rows);
         }

         rows[0].set(row);

         if(rows[0].rowCount() % 10 == 0) {
            rows[0].compact();
         }

         return rows[1];
      }

      /**
       * Add a row for the right-hand table that has the specified join column
       * values.
       *
       * @param keys the key for the values.
       * @param row the index of the row.
       * @return the rows for left table with the same key value.
       */
      public BitSet putRight(ListKey keys, int row) {
         BitSet[] rows = map.get(keys);

         if(rows == null) {
            rows = new BitSet[] { new BitSet(), new BitSet() };
            map.put(keys, rows);
         }

         rows[1].set(row);

         if(rows[1].rowCount() % 10 == 0) {
            rows[1].compact();
         }

         return rows[0];
      }

      /**
       * Determines if the left-hand table has been fully scanned.
       *
       * @return <code>true</code> if the left-hand table is complete.
       */
      public boolean isLeftComplete() {
         return leftComplete;
      }

      /**
       * Sets whether the left-hand table has been fully scanned. This method
       * should only be called by JoinThread when it is finished reading the
       * table.
       *
       * @param leftComplete <code>true</code> if the left-hand table is
       *                     complete.
       */
      public void setLeftComplete(boolean leftComplete) {
         this.leftComplete = leftComplete;
      }

      /**
       * Determines if the right-hand table has been fully scanned.
       *
       * @return <code>true</code> if the right-hand table is complete.
       */
      public boolean isRightComplete() {
         return rightComplete;
      }

      /**
       * Sets whether the right-hand table has been fully scanned. This method
       * should only be called by JoinThread when it is finished reading the
       * table.
       *
       * @param rightComplete <code>true</code> if the right-hand table is
       *                      complete.
       */
      public void setRightComplete(boolean rightComplete) {
         this.rightComplete = rightComplete;
      }

      // map that stores keys->left/right rows
      private final Map<ListKey,BitSet[]> map;
      // flags used to determine if the joining is complete
      private boolean leftComplete;
      private boolean rightComplete;
   }

   /**
    * Worker thread that scans one of the tables in join and populates the join
    * hash table and the resultant table.
    */
   private class JoinThread extends GroupedThread {
      /**
       * Creates a new instance of JoinThread.
       *
       * @param map the join hash table.
       * @param leftTable the left-hand table in the join.
       * @param rightTable the right-hand table in the join.
       * @param joinTable the resultant table where the joined rows should be
       *                  appended.
       * @param joinColumns the indices of the columns used for the join.
       * @param scanLeft if <code>true</code> the thread will scan the left-
       *                 hand table; otherwise, the right-hand table will be
       *                 scanned.
       */
      public JoinThread(JoinMap map, TableLens leftTable, TableLens rightTable,
                        JoinTable joinTable, int[] joinColumns,
                        boolean scanLeft, int joinType)
      {
         setPrincipal(ThreadContext.getContextPrincipal());
         this.map = map;
         this.leftTable = leftTable;
         this.rightTable = rightTable;
         this.joinTable = joinTable;
         this.joinColumns = joinColumns;
         this.scanLeft = scanLeft;
         this.joinType = joinType;
      }

      /**
       * Runs this thread.
       */
      @Override
      protected void doRun() {
         try {
            TableLens scanTable = scanLeft ? leftTable : rightTable;

            boolean ojoin = scanLeft ?
               ((joinType & JoinTableLens.LEFT_OUTER_JOIN) ==
                  JoinTableLens.LEFT_OUTER_JOIN) :
               ((joinType & JoinTableLens.RIGHT_OUTER_JOIN) ==
                  JoinTableLens.RIGHT_OUTER_JOIN);

            OUTER:
            for(int row = scanTable.getHeaderRowCount();
                scanTable.moreRows(row) && !cancelled; row++)
            {
               Object[] columnValues = new Object[joinColumns.length];

               for(int i = 0; i < columnValues.length; i++) {
                  columnValues[i] = scanTable.getObject(row, joinColumns[i]);

                  // is null and not outer? ignore it to keep in sync with sql
                  if(!ojoin && columnValues[i] == null ) {
                     continue OUTER;
                  }
               }

               BitSet rows;
               ListKey key = new ListKey(columnValues);

               synchronized(map) {
                  if(scanLeft) {
                     rows = map.putLeft(key, row);
                  }
                  else {
                     rows = map.putRight(key, row);
                  }

                  if(rows != null) {
                     IntIterator iter = rows.intIterator();

                     while(iter.hasNext()) {
                        int i = iter.next();
                        int leftRow = scanLeft ? row : i;
                        int rightRow = scanLeft ? i : row;

                        joinTable.addRow(leftRow, rightRow);

                        if(checkMaxRows()) {
                           break OUTER;
                        }
                     }
                  }
               }
            }
         }
         finally {
            synchronized(map) {
               if(scanLeft) {
                  if(map.isRightComplete()) {
                     joinTable.complete();
                  }
                  else {
                     map.setLeftComplete(true);
                  }
               }
               else {
                  if(map.isLeftComplete()) {
                     joinTable.complete();
                  }
                  else {
                     map.setRightComplete(true);
                  }
               }
            }
         }
      }

      /**
       * Cancel the join operation.
       */
      public void cancelJoin() {
         this.cancelled = true;
         this.interrupt();

         try {
            this.join();
         }
         catch(Exception ignore) {
         }
      }

      private final JoinMap map;
      private final TableLens leftTable;
      private final TableLens rightTable;
      private final JoinTable joinTable;
      private final int[] joinColumns;
      private final boolean scanLeft;
      private boolean cancelled;
      private final int joinType;
   }

   class ListKey {
      public ListKey(Object[] vals) {
         keys = new int[vals.length];

         for(int i = 0; i < vals.length; i++) {
            keys[i] = indexOf(i, vals[i]);
         }
      }

      @Override
      public final boolean equals(Object obj) {
         if(obj == null) {
            return false;
         }

         if(!(obj instanceof ListKey)) {
            return false;
         }

         int[] keys2 = ((ListKey) obj).keys;

         if(keys.length != keys2.length) {
            return false;
         }

         for(int i = 0; i < keys.length; i++) {
            if(keys[i] != keys2[i]) {
               return false;
            }

            // same as SQL
            if(keys[i] == NULL || keys2[i] == NULL) {
               return false;
            }
         }

         return true;
      }

      /**
       * Get the hash code.
       * @return the hash code.
       */
      @Override
      public final int hashCode() {
         if(hash != 0) {
            return hash;
         }

         for(int key : keys) {
            hash = (hash << 4) | key;
         }

         return hash;
      }

      /**
       * Find the index of the value in the value table.
       */
      private int indexOf(int n, Object val) {
         val = normalizeKeyValue(val);

         if(val == null) {
            return NULL;
         }

         int idx = 0;

         if(idxmaps != null) {
            // @by jasons, bug1274882392491, need to synchronize on key index map
            synchronized(idxmaps[n]) {
               if(idxmaps[n].containsKey(val)) {
                  idx = idxmaps[n].getInt(val);
               }
               else {
                  idx = idxmaps[n].size();
                  idxmaps[n].put(val, idx);
               }
            }
         }

         return idx;
      }

      private final int[] keys;
      private transient int hash = 0;
   }
}
