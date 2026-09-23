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
package inetsoft.report.composition.execution;

import inetsoft.report.Comparer;
import inetsoft.report.TableFilter;
import inetsoft.report.TableLens;
import inetsoft.report.filter.*;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.binding.FormulaHeaderInfo;
import inetsoft.report.lens.*;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.util.Tool;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.ScriptEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Post processing handler.
 *
 * @version 12.2
 * @author InetSoft Technology Corp
 */
public class PostProcessor {
   @SuppressWarnings("WeakerAccess")
   public static TableLens crossJoin(TableLens table1, TableLens table2) {
      return new CrossJoinTableLens(table1, table2);
   }

   public static TableLens join(TableLens table1, TableLens table2,
                                int[] leftCols, int[] rightCols, int joinType)
   {
      return new JoinTableLens(table1, table2, leftCols, rightCols, joinType, true);
   }

   public static TableLens union(TableLens table1, TableLens table2, boolean distinct) {
      UnionTableLens table = new UnionTableLens(table1, table2);
      table.setDistinct(distinct);
      return table;
   }

   public static TableLens intersect(TableLens table1, TableLens table2) {
      return new IntersectTableLens(table1, table2);
   }

   public static TableLens minus(TableLens table1, TableLens table2) {
      return new MinusTableLens(table1, table2);
   }

   public static TableLens mapColumn(TableLens table, int[] colmap) {
      return new ColumnMapFilter(table, colmap);
   }

   public static TableLens filter(TableLens base, ConditionGroup cgroup) {
      return filter(base, cgroup, null);
   }

   /**
    * @param box the sandbox this filter is being created for, or {@code null} if
    *            not running in a query-execution sandbox. Used to keep condition
    *            row-filtering and script execution locks in a consistent
    *            acquisition order (bug #76918); see {@link ConditionFilter2}.
    */
   public static TableLens filter(TableLens base, ConditionGroup cgroup, AssetQuerySandbox box) {
      if(cgroup.size() == 0) {
         return base;
      }

      // performance optimization, the base table is always
      // a formula table or xnode table, so it's meaningless but overhead
      // to delegate to base table when querying border or span info
      return new ConditionFilter2(base, cgroup, box);
   }

   public static TableLens sort(TableLens base, int[] carr, boolean[] sarr,
                                List<? extends Comparer> comparers)
   {
      TableLens stable = new SortFilter(base, carr, sarr);

      if(comparers != null) {
         for(int i = 0; i < carr.length && i < comparers.size(); i++) {
            Comparer comp = comparers.get(i);

            if(comp != null) {
               if(stable instanceof SortFilter) {
                  ((SortFilter) stable).setComparer(carr[i], comp);
               }
            }
         }
      }

      return stable;
   }

   @SuppressWarnings("WeakerAccess")
   public static TableLens tableSummary(TableLens base, int[] carr, Formula[] farr) {
      TableSummaryFilter stable = new TableSummaryFilter2(base, null, carr, farr);
      stable.setSummaryOnly(true);
      return stable;
   }

   @SuppressWarnings("unchecked")
   public static TableLens distinct(TableLens base, int[] arr,
                                    int[] dimTypes, Comparator[] comps)
   {
      return new DistinctTableLens(base, arr, dimTypes, comps);
   }

   public static TableLens maxrows(TableLens base, int maxrows) {
      if(maxrows <= 0) {
         return base;
      }

      return new MaxRowsTableLens(base, maxrows);
   }

   public static TableLens formula(TableLens table, String[] headers,
                                   String[] formulas, ScriptEnv senv,
                                   Object scope, Boolean[] mergeables,
                                   String tableName,
                                   List<FormulaHeaderInfo> hinfos,
                                   List<Class<?>> types, boolean[] restricted)
   {
      boolean[] mergeable2 = null;

      if(mergeables != null) {
         mergeable2 = new boolean[mergeables.length];

         for(int i = 0; i < mergeable2.length; i++) {
            mergeable2[i] = mergeables[i];
         }
      }

      Pattern fieldPattern = Pattern.compile("^field\\[(?:'|\")([^'\"]+)(?:'|\")\\]$");
      ArrayList aliasList = new ArrayList();

      for(int i = 0; i < formulas.length; i++) {
         String f = formulas[i];

         if(Tool.isEmptyString(f) || f.trim().isEmpty()) {
            continue;
         }

         f = f.trim();
         Matcher matcher = fieldPattern.matcher(f);
         String alias = matcher.matches() ? matcher.group(1) : null;

         if(alias == null) {
            continue;
         }

         int idx = Util.findColumn(table, alias);

         if(idx < 0 || idx >= table.getColCount()) {
            continue;
         }

         Class<?> clazz = table.getColType(idx);

         if(clazz.equals(types.get(i))) {
            aliasList.add(alias);
         }
      }

      // optimization, all expressions are aliases, just use a column map
      if(aliasList.size() == formulas.length) {
         ColumnIndexMap columnIndexMap = new ColumnIndexMap(table, true);
         int[] acols = aliasList.stream()
                              .mapToInt(a -> Util.findColumn(columnIndexMap, a))
                              .toArray();

         if(!Arrays.stream(acols).anyMatch(c -> c < 0)) {
            IntStream cols = IntStream.concat(IntStream.range(0, table.getColCount()),
                                              Arrays.stream(acols));
            ColumnMapFilter filter = new ColumnMapFilter(table, cols.toArray());

            for(int i = 0; i < headers.length; i++) {
               filter.setObject(0, i + table.getColCount(), headers[i]);
               filter.setColumnIdentifier(i + table.getColCount(), headers[i]);
            }

            return filter;
         }
      }

      FormulaTableLens ftbl = new FormulaTableLens(
         table, headers, formulas, senv, scope, mergeable2);

      ftbl.setTableName(tableName);
      ftbl.setFormulaHeaderInfo(hinfos);

      for(int i = 0; i < restricted.length; i++) {
         ftbl.setRestricted(i, restricted[i]);
      }

      int count = table.getColCount();

      for(int i = 0; i < types.size(); i++) {
         ftbl.setColType(i + count, types.get(i));
      }

      return ftbl;
   }

   // @aoa true if the aggregate will be aggregated again
   @SuppressWarnings("WeakerAccess")
   public static TableLens preSummarize(TableLens base, int[] garr, int[] sarr, Formula[] farr) {
      return null;
   }

   public static TableLens renameColumns(TableLens base, Object[] headers, String[] ids) {
      AttributeTableLens htable = new AssetTableLens(base);
      boolean changed = false;

      for(int i = 0; i < headers.length; i++) {
         if(!Tool.equals(headers[i], base.getObject(0, i))) {
            htable.setObject(0, i, headers[i]);
            changed = true;
         }

         base.setColumnIdentifier(i, ids[i]);
         htable.setColumnIdentifier(i, ids[i]);
      }

      return changed ? htable : base;
   }

   private static final class ConditionFilter2 extends ConditionFilter {
      ConditionFilter2(TableLens table, ConditionGroup conditions, AssetQuerySandbox box) {
         super(table, conditions);
         this.box = box;
         // only a FormulaTableLens column read can cascade into script
         // execution from inside checkCondition()/moreRows() -- see
         // containsFormulaTableLens() below.
         this.needsScriptLock = box != null && containsFormulaTableLens(table);
      }

      /**
       * Populating this filter's row map (via the inherited synchronized
       * {@code moreRows()}) can cascade into evaluating a calculated field, which
       * blocks on the query sandbox's GraalJS engine lock -- and a guest script
       * running concurrently on a *different* thread, already holding that same
       * engine lock, can re-enter this exact method (a table-row column read
       * routes back through {@code getObject()}/{@code getBaseRowIndex()}) and
       * block on this filter's own monitor. Two different threads then each hold
       * one of these locks while waiting on the other: an AB-BA deadlock
       * (bug #76918).
       *
       * <p>Acquiring the engine lock here, before the inherited monitor, keeps
       * both locks in the same order for every path into this filter, so the
       * cycle cannot form: a thread already inside script execution just
       * re-acquires its own (reentrant) engine lock and proceeds straight to the
       * monitor, while a thread about to trigger script execution must first wait
       * for the engine lock -- without yet holding this filter's monitor for
       * anyone else to wait on. Only locks when a script engine already exists
       * for this sandbox, so filters that never end up evaluating a script are
       * not forced to create one just to establish the ordering.
       *
       * <p>Narrower still (bug #76935): the engine lock is only requested at all
       * when {@link #needsScriptLock} says this filter's own base table can reach
       * a {@code FormulaTableLens} formula column -- the only place a
       * {@code checkCondition()}/{@code ConditionGroup.evaluate()} column read can
       * cascade into GraalJS script execution (confirmed by
       * {@code ConditionFilterGraalLockOrderingTest}'s own account of the original
       * jstack trace: {@code table.moreRows() -> FormulaTableLens.exec() ->
       * GraalJavaScriptEngine.exec()}'s {@code lock.lock()}). A filter that can
       * never reach a formula column can never itself block waiting on the engine
       * lock while holding this monitor, so it can never be the "A" side of the
       * AB-BA cycle above regardless of what unrelated scripts elsewhere in the
       * same sandbox are doing -- skipping the lock for it does not reopen
       * #76918, it only stops it from queuing behind scripts it was never at risk
       * of deadlocking against in the first place.
       *
       * <p>The held lock is recorded on this thread, so a lens below this filter
       * that would otherwise hand its processing to a background worker and wait
       * for it runs it on this thread instead, or lends the lock to that worker
       * while waiting for it (bug #76938).
       */
      @Override
      public boolean moreRows(int row) {
         Lock execLock = needsScriptLock ? box.peekScriptExecutionLock() : null;

         if(execLock == null) {
            return super.moreRows(row);
         }

         execLock.lock();
         JavaScriptEngine.pushHeldScriptLock(execLock);

         try {
            return super.moreRows(row);
         }
         finally {
            JavaScriptEngine.popHeldScriptLock();
            execLock.unlock();
         }
      }

      /**
       * @return {@code true} if {@code table}, or any table it wraps -- following
       * both the single-child {@link TableFilter} chain (joins built from a
       * single source, e.g. {@code SelfJoinTableLens}) and the two-child
       * {@link BinaryTableFilter} chain ({@code JoinTableLens},
       * {@code MergedJoinTableLens}, {@code CrossJoinTableLens}, and
       * {@code SetTableLens}, the base of union/minus/intersect) -- is a
       * {@link FormulaTableLens}. Reading one of its columns can compile/execute
       * a JavaScript formula (see {@code FormulaTableLens.getObject()}/
       * {@code moreRows()}), and a join or set-op result can embed one of these
       * on either side without itself being wrapped by an outer
       * {@code FormulaTableLens} -- each side of a join/union is its own
       * independently-recursed query, so a per-side formula column is already
       * baked into that side's result before the join/union ever executes,
       * while the *outer* query may have no expression columns of its own (see
       * bug #76935 review round 1: both {@code TableFilter} and
       * {@code BinaryTableFilter} must be walked, not just the former, or a
       * formula embedded on one side of a join/union is invisible to this
       * check and the join's first (lazy) access can run that formula's script
       * while this filter still holds its own monitor -- reopening #76918).
       */
      private static boolean containsFormulaTableLens(TableLens table) {
         if(table == null) {
            return false;
         }

         if(table instanceof FormulaTableLens) {
            return true;
         }

         if(table instanceof TableFilter) {
            for(TableLens child : ((TableFilter) table).getTables()) {
               if(containsFormulaTableLens(child)) {
                  return true;
               }
            }
         }

         if(table instanceof BinaryTableFilter) {
            for(TableLens child : ((BinaryTableFilter) table).getTables()) {
               if(containsFormulaTableLens(child)) {
                  return true;
               }
            }
         }

         return false;
      }

      private final AssetQuerySandbox box;
      private final boolean needsScriptLock;

      @Override
      public final int getColBorder(int r, int c) {
         return THIN_LINE;
      }

      @Override
      public final int getRowBorder(int r, int c) {
         return THIN_LINE;
      }

      @Override
      public final Dimension getSpan(int r, int c) {
         return null;
      }

      @Override
      public final boolean isLineWrap(int r, int c) {
         return true;
      }

      @Override
      public final Font getFont(int r, int c) {
         return null;
      }

      @Override
      public final Insets getInsets(int r, int c) {
         return null;
      }

      @Override
      public Color getColBorderColor(int r, int c) {
         return Color.black;
      }

      @Override
      public Color getRowBorderColor(int r, int c) {
         return Color.black;
      }

      /* need to pass through default alignment
      @Override
      public int getAlignment(int r, int c) {
         return H_LEFT | H_CENTER;
      }
      */

      @Override
      public Color getBackground(int r, int c) {
         return null;
      }

      @Override
      public Color getForeground(int r, int c) {
         return null;
      }
   }

   private static final class TableSummaryFilter2 extends TableSummaryFilter {
      TableSummaryFilter2(TableLens table, String label, int[] sumcols, Formula[] calc) {
         super(table, label, sumcols, calc);
      }

      @Override
      public int getTrailerRowCount() {
         // @by larryl, by default the last row is marked as summary, which
         // causes the data path to be different from the design time setting.
         // Since here we always use summary only, the table is really treated
         // as a regular table by the consumer.
         return 0;
      }
   }

   /**
    * Set whether this thread is used for creating data for MV.
    */
   @SuppressWarnings("WeakerAccess")
   public static void setCreatingMV(boolean creating) {
      creatingMV.set(creating);
   }

   private static final ThreadLocal<Boolean> creatingMV = ThreadLocal.withInitial(() -> false);
   private static final Logger LOG = LoggerFactory.getLogger(PostProcessor.class);
}
