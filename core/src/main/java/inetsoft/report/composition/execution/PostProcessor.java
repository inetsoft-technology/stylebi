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
import inetsoft.report.TableLens;
import inetsoft.report.filter.*;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.binding.FormulaHeaderInfo;
import inetsoft.report.lens.*;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.util.Tool;
import inetsoft.util.script.ScriptEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.*;
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
      if(cgroup.size() == 0) {
         return base;
      }

      // performance optimization, the base table is always
      // a formula table or xnode table, so it's meaningless but overhead
      // to delegate to base table when querying border or span info
      return new ConditionFilter2(base, cgroup);
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
      ConditionFilter2(TableLens table, ConditionGroup conditions) {
         super(table, conditions);
      }

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
