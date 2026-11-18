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
package inetsoft.report.filter;

import inetsoft.report.composition.execution.AssetQueryCacheNormalizer;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.MaxRowsTableLens;
import inetsoft.report.script.formula.NamedCellRange;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.test.XTableUtil;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SortSummaryFilterTest {
   private static DefaultTableLens people() {
      return new DefaultTableLens(new Object[][] {
         {"dept", "score"},
         {"b", 2},
         {"a", 3},
         {"b", 5},
         {"a", 1},
         {"a", 3}
      });
   }

   @Test
   void sortFilterDistinctAndComparersTwoColumnsMixedOrder() {
      SortFilter sort = new SortFilter(people(), new int[] {0, 1}, new boolean[] {true, false});
      sort.moreRows(Integer.MAX_VALUE);

      XTableUtil.assertEquals(sort, new Object[][] {
         {"dept", "score"},
         {"a", 3},
         {"a", 3},
         {"a", 1},
         {"b", 5},
         {"b", 2}
      });
   }

   @Test
   void sortFilterDistinctAndComparersDescendingFirstColumn() {
      SortFilter sort = new SortFilter(people(), new int[] {0, 1}, new boolean[] {false, true});
      sort.moreRows(Integer.MAX_VALUE);

      XTableUtil.assertEquals(sort, new Object[][] {
         {"dept", "score"},
         {"b", 2},
         {"b", 5},
         {"a", 1},
         {"a", 3},
         {"a", 3}
      });
   }

   @Test
   void sortFilterDistinctAndComparersDistinctMergesDuplicates() {
      SortFilter sort = new SortFilter(people(), new int[] {0, 1}, true);
      sort.setDistinct(true);

      // the distinct row map is the visible row set; the base row count is not narrowed
      assertTrue(sort.moreRows(4));
      assertFalse(sort.moreRows(5));
      Object[][] expected = {{"dept", "score"}, {"a", 1}, {"a", 3}, {"b", 2}, {"b", 5}};

      for(int r = 0; r < expected.length; r++) {
         for(int c = 0; c < 2; c++) {
            assertEquals(expected[r][c], sort.getObject(r, c), "cell " + r + "," + c);
         }
      }
   }

   @Test
   void sortFilterDistinctAndComparersChineseLocaleUsesPinyinOrder() {
      // Locale.setDefault is JVM-global: it is safe because test classes run one at a time
      // within a fork; revisit if method-level parallelism is ever enabled
      Locale old = Locale.getDefault();

      try {
         Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
         DefaultTableLens base = new DefaultTableLens(new Object[][] {
            {"name"},
            {"\u5f20"}, // zhang
            {"\u674e"}, // li
            {"\u738b"}, // wang
            {"\u9648"}  // chen
         });
         SortFilter sort = new SortFilter(base, new int[] {0}, true);
         sort.moreRows(Integer.MAX_VALUE);

         XTableUtil.assertEquals(sort, new Object[][] {
            {"name"},
            {"\u9648"},
            {"\u674e"},
            {"\u738b"},
            {"\u5f20"}
         });
      }
      finally {
         Locale.setDefault(old);
      }
   }

   private static DefaultTableLens sales() {
      return new DefaultTableLens(new Object[][] {
         {"region", "amount", "orders"},
         {"east", 10, 1},
         {"east", 6, 1},
         {"north", 1, 1},
         {"north", 2, 1},
         {"south", 5, 1},
         {"west", 4, 1},
         {"west", 3, 1}
      });
   }

   // SummaryFilter groups adjacent rows, so the base table is ordered by the group column
   private static SummaryFilter regionSummary(Formula[] total) {
      Formula[] formulas = {new SumFormula(), new CountFormula()};
      return new SummaryFilter(sales(), new int[] {0}, new int[] {1, 2}, formulas, total);
   }

   @Test
   void summaryFilterGroupAndTotalSumsAndCountsPerGroup() {
      SummaryFilter summary = regionSummary(null);
      summary.moreRows(Integer.MAX_VALUE);

      XTableUtil.assertEquals(summary, new Object[][] {
         {"region", "amount", "orders"},
         {"east", 16.0, 2},
         {"north", 3.0, 2},
         {"south", 5.0, 1},
         {"west", 7.0, 2}
      });
   }

   @Test
   void summaryFilterGroupAndTotalAppendsGrandTotalRow() {
      Formula[] total = {new SumFormula(), new CountFormula()};
      SummaryFilter summary = regionSummary(total);
      summary.moreRows(Integer.MAX_VALUE);

      XTableUtil.assertEquals(summary, new Object[][] {
         {"region", "amount", "orders"},
         {"east", 16.0, 2},
         {"north", 3.0, 2},
         {"south", 5.0, 1},
         {"west", 7.0, 2},
         {"Total", 31.0, 7}
      });
   }

   @Test
   void summaryFilterTopNAndOthersKeepsHighestGroupsAndMergesRest() {
      SummaryFilter summary = regionSummary(null);
      summary.setTopN(0, 0, 2, false, true, true);
      summary.moreRows(Integer.MAX_VALUE);

      XTableUtil.assertEquals(summary, new Object[][] {
         {"region", "amount", "orders"},
         {"east", 16.0, 2},
         {"west", 7.0, 2},
         {"Others", 8.0, 3}
      });
   }

   @Test
   void summaryFilterTopNAndOthersWithoutOthersDropsRest() {
      SummaryFilter summary = regionSummary(null);
      summary.setTopN(0, 0, 2, false);
      summary.moreRows(Integer.MAX_VALUE);

      XTableUtil.assertEquals(summary, new Object[][] {
         {"region", "amount", "orders"},
         {"east", 16.0, 2},
         {"west", 7.0, 2}
      });
   }

   @Test
   void summaryFilterTopNAndOthersBottomTwoReversed() {
      SummaryFilter summary = regionSummary(null);
      summary.setTopN(0, 0, 2, true);
      summary.moreRows(Integer.MAX_VALUE);

      XTableUtil.assertEquals(summary, new Object[][] {
         {"region", "amount", "orders"},
         {"north", 3.0, 2},
         {"south", 5.0, 1}
      });
   }

   /**
    * A base lens that, the first time it is read by the thread armed for it, waits until the
    * other thread is blocked and then runs the hook while this thread still holds the lens lock
    * of the MaxRowsTableLens above it.
    */
   private static final class HookedLens extends DefaultTableLens {
      HookedLens(Object[][] rows) {
         super(rows);
      }

      @Override
      public boolean moreRows(int row) {
         Runnable run = hook;

         if(run != null && Thread.currentThread() == hookThread && !firing.getAndSet(true)) {
            try {
               run.run();
            }
            finally {
               firing.set(false);
               hook = null;
            }
         }

         return super.moreRows(row);
      }

      volatile Runnable hook;
      volatile Thread hookThread;
      private final AtomicBoolean firing = new AtomicBoolean();
   }

   private static Collection<?> cells(XTable table, String range) throws Exception {
      return new NamedCellRange(range).getCells(table, false);
   }


   private volatile Thread aThread;

   @Test
   void columnMapFilterHidesAndReordersColumns() {
      DefaultTableLens base = new DefaultTableLens(new Object[][] {
         {"a", "b", "c", "d"},
         {1, 2, 3, 4},
         {5, 6, 7, 8}
      });
      // b and d hidden, c moved before a
      ColumnMapFilter filter = new ColumnMapFilter(base, new int[] {2, 0});

      assertEquals(2, filter.getColCount());
      assertEquals(2, filter.getBaseColIndex(0));
      assertEquals(0, filter.getBaseColIndex(1));
      XTableUtil.assertEquals(filter, new Object[][] {
         {"c", "a"},
         {3, 1},
         {7, 5}
      });
      // a column index outside the selection is no cell, not an index error
      assertNull(filter.getObject(1, 2));
      assertNull(filter.getObject(1, -1));
   }

   @Test
   void columnMapFilterDuplicateColumnAndChangedBaseTable() {
      DefaultTableLens base = new DefaultTableLens(new Object[][] {
         {"a", "b", "c"},
         {1, 2, 3}
      });
      ColumnMapFilter filter = new ColumnMapFilter(base, new int[] {1, 1, 0});

      // a column selected twice gets a distinct header the second time
      assertEquals("b", filter.getObject(0, 0));
      assertNotEquals(filter.getObject(0, 0), filter.getObject(0, 1));
      assertEquals(2, filter.getObject(1, 0));
      assertEquals(2, filter.getObject(1, 1));
      assertEquals(1, filter.getObject(1, 2));

      // the user switches: the same selection over a table with another set of columns
      DefaultTableLens other = new DefaultTableLens(new Object[][] {
         {"x", "y", "z", "w"},
         {10, 20, 30, 40}
      });
      filter.setTable(other);
      assertEquals(3, filter.getColCount());
      assertEquals("y", filter.getObject(0, 0));
      assertEquals(20, filter.getObject(1, 1));
      assertEquals(10, filter.getObject(1, 2));
   }

   @Test
   void columnMapFilterIndexBeyondBaseColumnsIsRejectedAtConstruction() {
      DefaultTableLens base = new DefaultTableLens(new Object[][] {{"a", "b"}, {1, 2}});

      assertThrows(ArrayIndexOutOfBoundsException.class,
                   () -> new ColumnMapFilter(base, new int[] {0, 5}));
   }

   private static EmbeddedTableAssembly baseAssembly(Worksheet ws) {
      EmbeddedTableAssembly base = new EmbeddedTableAssembly(ws, "Base");
      base.setEmbeddedData(new XEmbeddedTable(
         new String[] {XSchema.STRING, XSchema.INTEGER, XSchema.INTEGER},
         new Object[][] {{"c", "a", "b"}, {"x", 1, 2}, {"y", 3, 4}}));
      ws.addAssembly(base);
      return base;
   }

   /**
    * A mirror of Base whose columns are c, a, b (a hidden) and one expression column.
    */
   private static MirrorTableAssembly mirrorWithExpression(Worksheet ws, String expression) {
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "Mirror", baseAssembly(ws));
      ws.addAssembly(mirror);
      ColumnSelection columns = mirror.getColumnSelection(false);
      ((ColumnRef) columns.getAttribute("a")).setVisible(false);
      ExpressionRef ref = new ExpressionRef(null, "calc");
      ref.setExpression(expression);
      ColumnRef calc = new ColumnRef(ref);
      calc.setDataType(XSchema.STRING);
      calc.setSQL(false);
      columns.addAttribute(calc);
      mirror.setColumnSelection(columns, false);
      mirror.setColumnSelection(columns.clone(), true);
      return mirror;
   }

   private static List<String> privateColumns(TableAssembly table) {
      ColumnSelection columns = table.getColumnSelection(false);
      List<String> list = new java.util.ArrayList<>();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef ref = (ColumnRef) columns.getAttribute(i);
         list.add(ref.getName() + (ref.isVisible() ? "" : "!hidden"));
      }

      return list;
   }

   @Test
   void columnIndexSensitiveExpressionSkipsTheTransform() {
      Worksheet ws = new Worksheet();
      MirrorTableAssembly mirror = mirrorWithExpression(ws, "field[-1][3] + 'x'");
      List<String> before = privateColumns(mirror);
      assertTrue(before.contains("Base.a!hidden"), before.toString());
      AssetQuerySandbox box = new AssetQuerySandbox(ws);

      AssetQueryCacheNormalizer normalizer =
         new AssetQueryCacheNormalizer(mirror, box, AssetQuerySandbox.LIVE_MODE);

      // the columns keep their order and the hidden one stays hidden
      assertEquals(before, privateColumns(mirror));
      DefaultTableLens lens = new DefaultTableLens(new Object[][] {{"c", "b"}, {"x", 2}});
      assertSame(lens, normalizer.transformTableLens(lens));
   }

   @Test
   void columnIndexSensitiveExpressionOver1000CharsSkipsTheTransform() {
      Worksheet ws = new Worksheet();
      MirrorTableAssembly mirror = mirrorWithExpression(ws, "'" + "z".repeat(1001) + "'");
      List<String> before = privateColumns(mirror);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);

      new AssetQueryCacheNormalizer(mirror, box, AssetQuerySandbox.LIVE_MODE);

      assertEquals(before, privateColumns(mirror));
   }

   @Test
   void plainExpressionStillTransformsTheColumns() {
      Worksheet ws = new Worksheet();
      MirrorTableAssembly mirror = mirrorWithExpression(ws, "field['b'] + 1");
      AssetQuerySandbox box = new AssetQuerySandbox(ws);

      new AssetQueryCacheNormalizer(mirror, box, AssetQuerySandbox.LIVE_MODE);

      // control: all columns made visible and sorted by name
      assertEquals(List.of("Base.a", "Base.b", "Base.c", "calc"), privateColumns(mirror));
   }
}
