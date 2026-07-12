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

import inetsoft.report.TableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.WindowTableLens;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SortRef;
import inetsoft.uql.asset.WindowExpressionRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.jdbc.PostgreSQLHelper;
import inetsoft.uql.jdbc.SQLHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the in-memory window routing stage in {@link AssetQuery}:
 * {@link AssetQuery#buildWindowSpecs(inetsoft.uql.XTable, ColumnSelection)} — the resolver that
 * translates {@link WindowExpressionRef} columns into {@link WindowTableLens.Spec} instances
 * (base-column indices) — and {@link AssetQuery#buildWindowLens(TableLens, ColumnSelection)},
 * the seam that resolves-against-base + wraps the base in a {@link WindowTableLens}.
 * <p>
 * The critical invariant under test: partitionBy/orderBy/arg indices are resolved against the
 * runtime {@code base} table (which does NOT contain the window columns), NOT against the output
 * column selection (which does). The two are different coordinate spaces; resolving against the
 * selection reads the wrong base column or runs off the end.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WindowRoutingTest {
   /** Base table [stage(0,String), amount(1,Double)] — does NOT contain any window column. */
   private static DefaultTableLens stageAmountBase() {
      Object[][] data = {
         {"stage", "amount"},
         {"A", 30.0},
         {"A", 10.0},
         {"A", 20.0},
         {"B", 5.0},
      };
      DefaultTableLens t = new DefaultTableLens(data);
      t.setHeaderRowCount(1);
      t.setColumnIdentifier(0, "stage");
      t.setColumnIdentifier(1, "amount");
      return t;
   }

   /** Base table [stage(0), amount(1), qty(2)]. */
   private static DefaultTableLens stageAmountQtyBase() {
      Object[][] data = {
         {"stage", "amount", "qty"},
         {"A", 30.0, 1.0},
         {"B", 5.0, 2.0},
      };
      DefaultTableLens t = new DefaultTableLens(data);
      t.setHeaderRowCount(1);
      t.setColumnIdentifier(0, "stage");
      t.setColumnIdentifier(1, "amount");
      t.setColumnIdentifier(2, "qty");
      return t;
   }

   private static Object cell(TableLens t, int dataRow, int col) {
      return t.getObject(dataRow + t.getHeaderRowCount(), col);
   }

   @Test
   void buildsSpecsFromWindowColumnsResolvingIndicesAgainstBase() {
      // base: [stage(0), amount(1)]; output selection additionally carries
      // rn = ROW_NUMBER OVER(PARTITION BY stage ORDER BY amount DESC).
      DefaultTableLens base = stageAmountBase();
      WindowExpressionRef win = new WindowExpressionRef(
         "ROW_NUMBER", null, 0,
         List.of(new ColumnRef(new AttributeRef(null, "stage"))),
         List.of(desc(new ColumnRef(new AttributeRef(null, "amount")))));
      win.setName("rn");
      ColumnRef rn = new ColumnRef(win);
      rn.setSQL(true);

      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      cols.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      cols.addAttribute(rn);

      WindowTableLens.Spec[] specs = AssetQuery.buildWindowSpecs(base, cols);

      assertEquals(1, specs.length);
      assertEquals("rn", specs[0].header);
      assertEquals("ROW_NUMBER", specs[0].fn);
      assertArrayEquals(new int[]{0}, specs[0].partCols);   // stage -> base col 0
      assertArrayEquals(new int[]{1}, specs[0].orderCols);  // amount -> base col 1
      assertArrayEquals(new boolean[]{false}, specs[0].orderAsc);
      assertEquals(-1, specs[0].argCol);                    // ROW_NUMBER has none
   }

   @Test
   void buildWindowSpecs_carriesFrame() {
      // ma = AVG(amount) OVER (ORDER BY amount DESC ROWS BETWEEN 2 PRECEDING AND CURRENT ROW)
      WindowExpressionRef w = new WindowExpressionRef(
         "AVG", new ColumnRef(new AttributeRef(null, "amount")), 0,
         List.of(), List.of(desc(new ColumnRef(new AttributeRef(null, "amount")))));
      w.setFrame("PRECEDING", 2, "CURRENT_ROW", 0);
      w.setName("ma");
      ColumnRef ma = new ColumnRef(w);
      ma.setSQL(true);

      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      cols.addAttribute(ma);

      WindowTableLens.Spec[] specs = AssetQuery.buildWindowSpecs(stageAmountBase(), cols);

      assertEquals(1, specs.length);
      assertEquals("PRECEDING", specs[0].startBound);
      assertEquals(2, specs[0].startOffset);
      assertEquals("CURRENT_ROW", specs[0].endBound);
      assertEquals(0, specs[0].endOffset);
   }

   @Test
   void buildWindowSpecs_carriesModeAndUnit() {
      // trailing = SUM(amount) OVER (ORDER BY amount RANGE BETWEEN INTERVAL '7 day' PRECEDING
      //            AND CURRENT ROW)
      WindowExpressionRef range = new WindowExpressionRef(
         "SUM", new ColumnRef(new AttributeRef(null, "amount")), 0,
         List.of(), List.of(asc(new ColumnRef(new AttributeRef(null, "amount")))));
      range.setFrame("RANGE", "PRECEDING", 7, "CURRENT_ROW", 0, "day");
      range.setName("trailing");
      ColumnRef trailing = new ColumnRef(range);
      trailing.setSQL(true);

      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      cols.addAttribute(trailing);

      WindowTableLens.Spec[] specs = AssetQuery.buildWindowSpecs(stageAmountBase(), cols);

      assertEquals(1, specs.length);
      assertEquals("RANGE", specs[0].mode);
      assertEquals("day", specs[0].offsetUnit);

      // a frame-less / ROWS ref resolves mode "ROWS" and a null offsetUnit
      WindowExpressionRef rowNum = new WindowExpressionRef(
         "ROW_NUMBER", null, 0,
         List.of(new ColumnRef(new AttributeRef(null, "stage"))),
         List.of(desc(new ColumnRef(new AttributeRef(null, "amount")))));
      rowNum.setName("rn");
      ColumnRef rn = new ColumnRef(rowNum);
      rn.setSQL(true);

      ColumnSelection cols2 = new ColumnSelection();
      cols2.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      cols2.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      cols2.addAttribute(rn);

      WindowTableLens.Spec[] specs2 = AssetQuery.buildWindowSpecs(stageAmountBase(), cols2);

      assertEquals(1, specs2.length);
      assertEquals("ROWS", specs2[0].mode);
      assertNull(specs2[0].offsetUnit);
   }

   @Test
   void buildWindowSpecs_frameLessYieldsNullStartBound() {
      WindowExpressionRef win = new WindowExpressionRef(
         "ROW_NUMBER", null, 0,
         List.of(new ColumnRef(new AttributeRef(null, "stage"))),
         List.of(desc(new ColumnRef(new AttributeRef(null, "amount")))));
      win.setName("rn");
      ColumnRef rn = new ColumnRef(win);
      rn.setSQL(true);

      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      cols.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      cols.addAttribute(rn);

      WindowTableLens.Spec[] specs = AssetQuery.buildWindowSpecs(stageAmountBase(), cols);

      assertEquals(1, specs.length);
      assertNull(specs[0].startBound);
      assertEquals(0, specs[0].startOffset);
      assertNull(specs[0].endBound);
      assertEquals(0, specs[0].endOffset);
   }

   @Test
   void noWindowColumnsYieldsEmptySpecs() {
      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      cols.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));

      WindowTableLens.Spec[] specs = AssetQuery.buildWindowSpecs(stageAmountBase(), cols);

      assertEquals(0, specs.length);
   }

   /**
    * SEAM TEST (integration-style, no live query): resolve-against-base + value computation.
    * The window column is placed FIRST in the selection (index 0), while the columns it
    * references live at base indices 0 (stage) and 1 (amount). With the OLD selection-index
    * resolution, partitionBy stage would resolve to selection index 1 (= base "amount") and
    * orderBy amount to selection index 2 (OUT OF BOUNDS for the 2-column base → AIOOBE). With
    * findColumn-against-base the values come out correct. This test FAILS on the old code and
    * PASSES on the fix.
    */
   @Test
   void buildWindowLens_resolvesAgainstBase_withNonTrailingWindowColumn() {
      DefaultTableLens base = stageAmountBase();   // [stage(0), amount(1)]

      // rn = ROW_NUMBER() OVER (PARTITION BY stage ORDER BY amount DESC)
      WindowExpressionRef win = new WindowExpressionRef(
         "ROW_NUMBER", null, 0,
         List.of(new ColumnRef(new AttributeRef(null, "stage"))),
         List.of(desc(new ColumnRef(new AttributeRef(null, "amount")))));
      win.setName("rn");
      ColumnRef rn = new ColumnRef(win);
      rn.setSQL(true);

      // window column FIRST (non-trailing) in the selection — different coordinate space
      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(rn);
      cols.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      cols.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));

      TableLens lens = AssetQuery.buildWindowLens(base, cols);

      // base columns preserved (2), window column appended at index 2
      assertEquals(3, lens.getColCount());
      assertEquals("rn", lens.getObject(0, 2));   // header
      // base data (original order): A/30, A/10, A/20, B/5
      // ROW_NUMBER PARTITION BY stage ORDER BY amount DESC → A:30=1,10=3,20=2 ; B:5=1
      assertEquals("A", cell(lens, 0, 0)); assertEquals(1, cell(lens, 0, 2)); // A/30 → 1
      assertEquals("A", cell(lens, 1, 0)); assertEquals(3, cell(lens, 1, 2)); // A/10 → 3
      assertEquals("A", cell(lens, 2, 0)); assertEquals(2, cell(lens, 2, 2)); // A/20 → 2
      assertEquals("B", cell(lens, 3, 0)); assertEquals(1, cell(lens, 3, 2)); // B/5  → 1
   }

   @Test
   void divergentOrderByAcrossSpecsThrows() {
      // Two window columns on the same table with different, non-empty ORDER BY grammars —
      // WindowTableLens shares a single partition/order across all specs in a table (Phase 2),
      // so this must fail loud rather than silently mis-sort the second column.
      WindowExpressionRef win1 = new WindowExpressionRef(
         "ROW_NUMBER", null, 0, List.of(),
         List.of(desc(new ColumnRef(new AttributeRef(null, "amount")))));
      win1.setName("rn1");
      ColumnRef rn1 = new ColumnRef(win1);
      rn1.setSQL(true);

      WindowExpressionRef win2 = new WindowExpressionRef(
         "RANK", null, 0, List.of(),
         List.of(asc(new ColumnRef(new AttributeRef(null, "qty")))));
      win2.setName("rn2");
      ColumnRef rn2 = new ColumnRef(win2);
      rn2.setSQL(true);

      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      cols.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      cols.addAttribute(new ColumnRef(new AttributeRef(null, "qty")));
      cols.addAttribute(rn1);
      cols.addAttribute(rn2);

      assertThrows(RuntimeException.class,
         () -> AssetQuery.buildWindowSpecs(stageAmountQtyBase(), cols));
   }

   @Test
   void divergentPartitionPresenceAcrossSpecsThrows() {
      // One window column with no PARTITION BY (global) and another with a non-empty
      // PARTITION BY on the same table — WindowTableLens.partCols() applies the first
      // non-empty partition grammar found to EVERY spec, so the un-partitioned spec would
      // silently be partitioned too. Must fail loud instead.
      WindowExpressionRef win1 = new WindowExpressionRef(
         "ROW_NUMBER", null, 0, List.of(),
         List.of(desc(new ColumnRef(new AttributeRef(null, "amount")))));
      win1.setName("rn1");
      ColumnRef rn1 = new ColumnRef(win1);
      rn1.setSQL(true);

      WindowExpressionRef win2 = new WindowExpressionRef(
         "SUM", new ColumnRef(new AttributeRef(null, "amount")), 0,
         List.of(new ColumnRef(new AttributeRef(null, "stage"))),
         List.of());
      win2.setName("rn2");
      ColumnRef rn2 = new ColumnRef(win2);
      rn2.setSQL(true);

      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      cols.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      cols.addAttribute(rn1);
      cols.addAttribute(rn2);

      RuntimeException ex = assertThrows(RuntimeException.class,
         () -> AssetQuery.buildWindowSpecs(stageAmountBase(), cols));
      assertTrue(ex.getMessage().contains("PARTITION BY"));
   }

   @Test
   void unresolvedPartitionByColumnThrows() {
      // partitionBy references "missing", which is not in the BASE table — buildWindowSpecs
      // must fail loud with a named error instead of letting a -1 index flow into
      // WindowTableLens and surface as a cryptic ArrayIndexOutOfBoundsException later.
      WindowExpressionRef win = new WindowExpressionRef(
         "ROW_NUMBER", null, 0,
         List.of(new ColumnRef(new AttributeRef(null, "missing"))),
         List.of(desc(new ColumnRef(new AttributeRef(null, "amount")))));
      win.setName("rn");
      ColumnRef rn = new ColumnRef(win);
      rn.setSQL(true);

      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      cols.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      cols.addAttribute(rn);

      RuntimeException ex = assertThrows(RuntimeException.class,
         () -> AssetQuery.buildWindowSpecs(stageAmountBase(), cols));
      assertTrue(ex.getMessage().contains("missing"));
      assertTrue(ex.getMessage().contains("rn"));
   }

   @Test
   void unresolvedOrderByColumnThrows() {
      // orderBy references "missing", which is not in the BASE table.
      WindowExpressionRef win = new WindowExpressionRef(
         "ROW_NUMBER", null, 0,
         List.of(new ColumnRef(new AttributeRef(null, "stage"))),
         List.of(desc(new ColumnRef(new AttributeRef(null, "missing")))));
      win.setName("rn");
      ColumnRef rn = new ColumnRef(win);
      rn.setSQL(true);

      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      cols.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      cols.addAttribute(rn);

      RuntimeException ex = assertThrows(RuntimeException.class,
         () -> AssetQuery.buildWindowSpecs(stageAmountBase(), cols));
      assertTrue(ex.getMessage().contains("missing"));
      assertTrue(ex.getMessage().contains("rn"));
   }

   @Test
   void buildWindowLens_failsLoudWhenPartitionRefUnresolvedInBase() {
      // Fail-loud through the wrap seam: a partitionBy ref that is not present in `base` must
      // throw a named RuntimeException, not build a lens with a -1 index that later AIOOBEs.
      DefaultTableLens base = stageAmountBase();   // [stage(0), amount(1)] — no "region" column
      WindowExpressionRef win = new WindowExpressionRef(
         "ROW_NUMBER", null, 0,
         List.of(new ColumnRef(new AttributeRef(null, "region"))),   // absent from base
         List.of(desc(new ColumnRef(new AttributeRef(null, "amount")))));
      win.setName("rn");
      ColumnRef rn = new ColumnRef(win);
      rn.setSQL(true);

      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(rn);
      cols.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      cols.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));

      RuntimeException ex = assertThrows(RuntimeException.class,
         () -> AssetQuery.buildWindowLens(base, cols));
      assertTrue(ex.getMessage().contains("region"));
      assertTrue(ex.getMessage().contains("rn"));
   }

   /**
    * Dialect capability gate (Phase 4): PostgreSQL pushes RANGE/GROUPS down as SQL; every other
    * dialect falls back to the in-memory {@link WindowTableLens} engine for those modes. ROWS
    * pushes on every dialect, unchanged from Phase 3.
    */
   @Test
   void framePushable_postgresYesOthersNo() {
      SQLHelper postgresHelper = new PostgreSQLHelper();
      SQLHelper otherHelper = new SQLHelper();   // getSQLHelperType() == "default"

      assertTrue(AssetQuery.framePushable("RANGE", postgresHelper));
      assertTrue(AssetQuery.framePushable("GROUPS", postgresHelper));
      assertFalse(AssetQuery.framePushable("RANGE", otherHelper));
      assertFalse(AssetQuery.framePushable("GROUPS", otherHelper));
      assertTrue(AssetQuery.framePushable("ROWS", otherHelper));    // ROWS pushes everywhere
      assertTrue(AssetQuery.framePushable("ROWS", postgresHelper));
      assertTrue(AssetQuery.framePushable("ROWS", null));           // no dialect (tabular source)
      assertFalse(AssetQuery.framePushable("RANGE", null));
   }

   private static SortRef desc(DataRef ref) {
      SortRef s = new SortRef(ref);
      s.setOrder(XConstants.SORT_DESC);
      return s;
   }

   private static SortRef asc(DataRef ref) {
      SortRef s = new SortRef(ref);
      s.setOrder(XConstants.SORT_ASC);
      return s;
   }
}
