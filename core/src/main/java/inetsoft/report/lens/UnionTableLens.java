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

import inetsoft.report.TableLens;
import inetsoft.report.internal.table.MergedRow;
import inetsoft.report.internal.table.MergedTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Union table lens does the union operation.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class UnionTableLens extends SetTableLens {
   /**
    * Constructor.
    */
   public UnionTableLens() throws Exception {
      super();
   }

   /**
    * Constructor.
    */
   public UnionTableLens(TableLens ltable, TableLens rtable) {
      super(ltable, rtable);
   }

   /**
    * Set the distinct option.
    * @param distinct <tt>true</tt> if distinct, <tt>false</tt>otherwise.
    */
   public void setDistinct(boolean distinct) {
      this.distinct = distinct;
      invalidate();
   }

   /**
    * Check if is distinct.
    * @return <tt>true</tt> if distinct, <tt>false</tt>otherwise.
    */
   public boolean isDistinct() {
      return distinct;
   }

   @Override
   protected Row getRow(int row) {
      if(distinct) {
         return super.getRow(row);
      }
      else {
         // read once and published only once filled: isNull() calls this without the lock,
         // and invalidate() may clear it at any time (bug #77365)
         int[] rowCounts = getRowCounts();
         Row result = null;
         int r = row;

         for(int i = 0; i < getTableCount(); i++) {
            if(rowCounts[i] < 0) {
               TableLens table = getTable(i);

               if(table.moreRows(r)) {
                  if(rowCounts[i] < 0) {
                     rowCounts[i] = table.getRowCount();
                  }

                  result = new Row(i, r);
                  break;
               }

               rowCounts[i] = table.getRowCount();
               r -= rowCounts[i] - 1;
            }
            else if(r < rowCounts[i]) {
               result = new Row(i, r);
               break;
            }
            else {
               r -= rowCounts[i] - 1;
            }
         }

         if(result == null) {
            throw new ArrayIndexOutOfBoundsException(row);
         }

         return result;
      }
   }

   @Override
   public boolean moreRows(int row) {
      // not holding this lens's monitor, SetTableLens.moreRows() may lend the script
      // engine lock while waiting and must reclaim it outside the monitor (bug #76938)
      if(distinct) {
         return super.moreRows(row);
      }

      // the bases are read without this lens's monitor: a base read may take a script
      // engine lock (e.g. a condition filter over a formula lens, pool off), and a thread
      // holding that lock may be waiting for the monitor to read this lens (bug #77874).
      // the monitor guards only the published row count
      if(row != EOT && row < rowCnt) {
         return true;
      }

      // captured before reading, getRow() reads the same array. invalidate() replaces it, so
      // a count read from the bases before an invalidate() is never published (bug #77397)
      int[] rowCounts = getRowCounts();

      if(row == EOT) {
         for(int i = 0; i < getTableCount(); i++) {
            TableLens table = getTable(i);
            table.moreRows(EOT);

            // @by davyc, the getRow logic for setting rowCounts logic
            // is not correct, here we should maintain the rowCounts correct,
            // so someplace like XUtil.getHeader(table, c) will not cause
            // rowCounts data wrong
            // if(rowCounts[i] < 0) {
               rowCounts[i] = table.getRowCount();
            // }
         }

         return false;
      }

      try {
         getRow(row);
      }
      catch(ArrayIndexOutOfBoundsException exc) {
         return false;
      }

      synchronized(this) {
         // a mismatch is an invalidate() since the read (or another reader's lazy create of
         // the array), leave the count to the next read
         if(this.rowCounts == rowCounts) {
            rowCnt = Math.max(rowCnt, row + 1);
         }
      }

      return true;
   }

   /**
    * Get the row count of each table, -1 if not known yet.
    */
   private int[] getRowCounts() {
      int[] rowCounts = this.rowCounts;

      if(rowCounts == null) {
         rowCounts = new int[getTableCount()];
         Arrays.fill(rowCounts, -1);
         this.rowCounts = rowCounts;
      }

      return rowCounts;
   }

   @Override
   public int getRowCount() {
      if(distinct) {
         return super.getRowCount();
      }
      else {
         // reads no state of this lens, so the bases are asked without its monitor: a base's
         // count may take a script engine lock (e.g. a crosstab computes it), and a thread
         // holding that lock may be waiting for the monitor (bug #77874)
         int rcount = 0;

         for(int i = 0; i < getTableCount(); i++) {
            TableLens table = getTable(i);
            int r = table.getRowCount();

            if(r < 0) {
               rcount = -rcount + r - 1;

               if(i > 0) {
                  // skip header
                  ++rcount;
               }

               break;
            }
            else {
               rcount += r;

               if(i > 0) {
                  // skip header
                  --rcount;
               }
            }
         }

         return rcount;
      }
   }

   @Override
   public void invalidate() {
      // reset both under the monitor moreRows() publishes rowCnt with, a stale
      // rowCnt reports rows past the end of a shrunk base (bug #77397)
      synchronized(this) {
         rowCounts = null;
         rowCnt = 0;
      }

      super.invalidate();
   }

   /**
    * Create a merged table.
    * @return the created merged table.
    */
   @Override
   protected MergedTable createMergedTable() throws Exception {
      return new MergedTable() {
         @Override
         protected MergedRow createMergedRow() {
            return new MergedRow2();
         }
      };
   }

   /**
    * Get the merged table visitor.
    * @return the merged table visitor.
    */
   @Override
   protected MergedTable.Visitor getVisitor(Pass pass) {
      return null;
   }

   /**
    * Another merged row.
    */
   private class MergedRow2 extends MergedRow {
      @Override
      public void add(int table, int row) {
         super.add(table, row);
         check(table, row);
      }

      private void check(int tidx, int ridx) {
         int nrows = 0;

         for(int i = 0; i < rows.length; i++) {
            nrows += rows[i].length;
         }

         if(!distinct || nrows == 1) {
            Row row = new Row(tidx, ridx);

            if(rows == null) {
               LOG.debug("Merging table rows interrupted",
                  new Exception("Stack trace"));
               return;
            }

            addSetRow(row);

            if(getSetRowCount() % 20 == 0) {
               synchronized(UnionTableLens.this) {
                  UnionTableLens.this.notifyAll();
               }
            }
         }
      }
   }

   private boolean distinct = true;  // distinct flag
   private volatile int[] rowCounts = null;
   // read without the monitor by moreRows(), written under it (bug #77874)
   private transient volatile int rowCnt = 0;

   private static final Logger LOG = LoggerFactory.getLogger(UnionTableLens.class);
}
