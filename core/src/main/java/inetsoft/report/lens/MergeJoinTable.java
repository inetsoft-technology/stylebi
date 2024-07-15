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

import inetsoft.report.Comparer;
import inetsoft.report.TableLens;
import inetsoft.report.filter.SortFilter;
import inetsoft.report.filter.SortedTable;
import inetsoft.report.internal.ComparatorComparer;
import inetsoft.util.*;
import org.roaringbitmap.RoaringBitmap;
import org.slf4j.MDC;

import java.util.Map;

class MergeJoinTable extends JoinTable {
   public MergeJoinTable(TableLens leftTable, TableLens rightTable, int[] leftCols,
                         int[] rightCols, int joinType, boolean includeRightJoinCols,
                         int maxRows)
   {
      super(
         leftTable, rightTable, leftCols, rightCols, joinType, includeRightJoinCols,
         maxRows);
      joinThread = new JoinThread(leftTable, rightTable, leftCols, rightCols, joinType);
      joinThread.start();
   }

   @Override
   public void complete() {
      joinThread = null;
      flushPending();
      super.complete();
   }

   @Override
   protected boolean cancelJoin() {
      if(joinThread != null) {
         joinThread.cancel();
         return true;
      }

      return false;
   }

   private JoinThread joinThread;

   private final class JoinThread extends GroupedThread {
      JoinThread(TableLens leftTable, TableLens rightTable,
                 int[] leftCols, int[] rightCols, int joinType)
      {
         if(Thread.currentThread() instanceof GroupedThread) {
            setParent((GroupedThread) Thread.currentThread());
         }

         setPrincipal(ThreadContext.getContextPrincipal());
         this.context = MDC.getCopyOfContextMap();
         this.leftOuter =
            (joinType & JoinTableLens.LEFT_OUTER_JOIN) == JoinTableLens.LEFT_OUTER_JOIN;
         this.rightOuter =
            (joinType & JoinTableLens.RIGHT_OUTER_JOIN) == JoinTableLens.RIGHT_OUTER_JOIN;

         this.leftTable = new SortFilter(leftTable, leftCols, true);
         Comparer comparer = new ComparatorComparer(this::compareValue);
         this.leftTable.setValueNormalizer(MergeJoinTable.this::normalizeKeyValue);

         for(int col : leftCols) {
            this.leftTable.setComparer(col, comparer);
         }

         this.rightTable = new SortFilter(rightTable, rightCols, true);
         this.rightTable.setValueNormalizer(MergeJoinTable.this::normalizeKeyValue);

         for(int col : rightCols) {
            this.rightTable.setComparer(col, comparer);
         }
      }

      @Override
      protected void doRun() {
         try {
            if(context != null) {
               MDC.setContextMap(context);
            }

            int l = leftTable.getHeaderRowCount();
            int r = rightTable.getHeaderRowCount();
            RoaringBitmap lJoined = new RoaringBitmap();
            RoaringBitmap rJoined = new RoaringBitmap();

            while(!isCancelled()) {
               if(leftTable.moreRows(l) && rightTable.moreRows(r)) {
                  Object[] lTuple = getTuple(leftTable, l);
                  Object[] rTuple = getTuple(rightTable, r);
                  int comp = compareTuples(lTuple, rTuple);

                  if(comp > 0) {
                     if(rightOuter && !rJoined.contains(r)) {
                        addRow(-1, getRightRow(r));

                        if(checkMaxRows()) {
                           break;
                        }
                     }

                     r++;
                  }
                  else if(comp < 0) {
                     if(leftOuter && !lJoined.contains(l)) {
                        addRow(getLeftRow(l), -1);
                     }

                     l++;
                  }
                  else {
                     if(containsNull(lTuple) || containsNull(rTuple)) {
                        // sql does not join on null, don't add inner join
                        if(leftOuter && !lJoined.contains(l)) {
                           addRow(getLeftRow(l), -1);

                           if(checkMaxRows()) {
                              break;
                           }
                        }

                        if(rightOuter && !rJoined.contains(r)) {
                           addRow(-1, getRightRow(r));

                           if(checkMaxRows()) {
                              break;
                           }
                        }
                     }
                     else {
                        int lBase = getLeftRow(l);
                        int rBase = getRightRow(r);

                        // add inner join for current rows
                        addRow(lBase, rBase);

                        if(leftOuter) {
                           lJoined.add(l);
                        }

                        if(rightOuter) {
                           rJoined.add(r);
                        }

                        if(checkMaxRows()) {
                           break;
                        }

                        // add inner join for all right-side rows with same tuple
                        for(int i = r + 1; rightTable.moreRows(i) && !isCancelled(); i++) {
                           Object[] tuple = getTuple(rightTable, i);

                           if(compareTuples(lTuple, tuple) == 0 && !containsNull(tuple)) {
                              addRow(lBase, getRightRow(i));

                              if(rightOuter) {
                                 rJoined.add(i);
                              }

                              if(checkMaxRows()) {
                                 break;
                              }
                           }
                           else {
                              break;
                           }
                        }

                        if(rightOuter) {
                           rJoined.trim();
                        }

                        if(isMaxAlert()) {
                           break;
                        }

                        // add inner join for all left-side rows with same tuple
                        for(int i = l + 1; leftTable.moreRows(i) && !isCancelled(); i++) {
                           Object[] tuple = getTuple(leftTable, i);

                           if(compareTuples(tuple, rTuple) == 0 && !containsNull(tuple)) {
                              addRow(getLeftRow(i), rBase);

                              if(leftOuter) {
                                 lJoined.add(i);
                              }

                              if(checkMaxRows()) {
                                 break;
                              }
                           }
                           else {
                              break;
                           }
                        }

                        if(leftOuter) {
                           lJoined.trim();
                        }

                        if(isMaxAlert()) {
                           break;
                        }
                     }

                     ++l;
                     ++r;
                  }
               }
               else if(leftTable.moreRows(l) && leftOuter) {
                  // add remaining rows from left table for outer join
                  for(int row = l; leftTable.moreRows(row) && !isCancelled(); row++) {
                     if(!lJoined.contains(row)) {
                        addRow(getLeftRow(row), -1);

                        if(checkMaxRows()) {
                           break;
                        }
                     }
                  }

                  break;
               }
               else if(rightTable.moreRows(r) && rightOuter) {
                  // add remaining rows from right table for outer join
                  for(int row = r; rightTable.moreRows(row) && !isCancelled(); row++) {
                     if(!rJoined.contains(row)) {
                        addRow(-1, getRightRow(row));

                        if(checkMaxRows()) {
                           break;
                        }
                     }
                  }

                  break;
               }
               else {
                  break;
               }
            }
         }
         finally {
            leftTable.invalidate();
            rightTable.invalidate();
            complete();
         }
      }

      private int getLeftRow(int row) {
         return leftTable.getBaseRowIndex(row);
      }

      private int getRightRow(int row) {
         return rightTable.getBaseRowIndex(row);
      }

      private Object[] getTuple(SortedTable table, int row) {
         int[] cols = table.getSortCols();
         Object[] tuple = new Object[cols.length];

         for(int i = 0; i < cols.length; i++) {
            tuple[i] = normalizeKeyValue(table.getObject(row, cols[i]));
         }

         return tuple;
      }

      private int compareTuples(Object[] left, Object[] right) {
         for(int i = 0; i < left.length; i++) {
            // case sensitivity handled in normalizeKeyValue
            int result = CoreTool.compare(left[i], right[i], true, true);

            if(result != 0) {
               return result;
            }
         }

         return 0;
      }

      private boolean containsNull(Object[] tuple) {
         for(Object value : tuple) {
            if(value == null) {
               return true;
            }
         }

         return false;
      }

      private int compareValue(Object a, Object b) {
         // case sensitivity handled in normalizeKeyValue
         return CoreTool.compare(a, b, true, true);
      }

      private final SortFilter leftTable;
      private final boolean leftOuter;
      private final SortFilter rightTable;
      private final boolean rightOuter;
      private Map<String, String> context;
   }
}
