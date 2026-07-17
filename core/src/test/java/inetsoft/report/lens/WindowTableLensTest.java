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
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WindowTableLensTest {
   /** Base: header row + data. cols: stage(0,String), amount(1,Double). */
   private static DefaultTableLens base() {
      Object[][] data = {
         {"stage", "amount"},
         {"A", 30.0},
         {"A", 10.0},
         {"A", 20.0},
         {"B", 5.0},
      };
      DefaultTableLens t = new DefaultTableLens(data);
      t.setHeaderRowCount(1);
      return t;
   }

   private static Object cell(TableLens t, int dataRow, int col) {
      return t.getObject(dataRow + t.getHeaderRowCount(), col);
   }

   @Test
   void rowNumber_perPartition_orderedDesc() {
      // ROW_NUMBER() OVER (PARTITION BY stage ORDER BY amount DESC) as "rn"
      WindowTableLens.Spec spec = new WindowTableLens.Spec(
         "rn", "ROW_NUMBER", -1, 0, new int[]{0}, new int[]{1}, new boolean[]{false});
      WindowTableLens lens = new WindowTableLens(base(), new WindowTableLens.Spec[]{spec});

      assertEquals(3, lens.getColCount());                 // 2 base + 1 window
      assertEquals("rn", lens.getObject(0, 2));            // header
      // base rows preserved in ORIGINAL order; rn computed over sorted partition
      // original data: A/30, A/10, A/20, B/5  →  A desc: 30=1,20=2,10=3 ; B: 5=1
      assertEquals("A", cell(lens, 0, 0)); assertEquals(1, cell(lens, 0, 2)); // A/30 → 1
      assertEquals("A", cell(lens, 1, 0)); assertEquals(3, cell(lens, 1, 2)); // A/10 → 3
      assertEquals("A", cell(lens, 2, 0)); assertEquals(2, cell(lens, 2, 2)); // A/20 → 2
      assertEquals("B", cell(lens, 3, 0)); assertEquals(1, cell(lens, 3, 2)); // B/5  → 1
   }

   /** Base with an intentional tie in the order key. cols: stage(0), amount(1). */
   private static DefaultTableLens tiedBase() {
      Object[][] data = {
         {"stage", "amount"},
         {"A", 30.0},
         {"A", 30.0},   // tie with row above on amount
         {"A", 10.0},
         {"B", 7.0},
      };
      DefaultTableLens t = new DefaultTableLens(data);
      t.setHeaderRowCount(1);
      return t;
   }

   @Test
   void rank_and_denseRank_shareTiesButDifferOnGaps() {
      WindowTableLens.Spec rank = new WindowTableLens.Spec(
         "rk", "RANK", -1, 0, new int[]{0}, new int[]{1}, new boolean[]{false});
      WindowTableLens.Spec dense = new WindowTableLens.Spec(
         "dr", "DENSE_RANK", -1, 0, new int[]{0}, new int[]{1}, new boolean[]{false});
      WindowTableLens lens = new WindowTableLens(tiedBase(), new WindowTableLens.Spec[]{rank, dense});
      // A desc: 30(tie),30(tie),10 → RANK 1,1,3 ; DENSE_RANK 1,1,2. B: 7 → 1,1
      assertEquals(1, cell(lens, 0, 2)); assertEquals(1, cell(lens, 0, 3)); // A/30
      assertEquals(1, cell(lens, 1, 2)); assertEquals(1, cell(lens, 1, 3)); // A/30 tie
      assertEquals(3, cell(lens, 2, 2)); assertEquals(2, cell(lens, 2, 3)); // A/10 (RANK skips 2)
      assertEquals(1, cell(lens, 3, 2)); assertEquals(1, cell(lens, 3, 3)); // B/7
   }

   @Test
   void ntile_frontLoadsRemainder() {
      // 4 rows / 3 buckets → sizes 2,1,1 (front-loaded). Global window (no partition).
      Object[][] data = {{"amount"},{40.0},{30.0},{20.0},{10.0}};
      DefaultTableLens t = new DefaultTableLens(data); t.setHeaderRowCount(1);
      WindowTableLens.Spec ntile = new WindowTableLens.Spec(
         "b", "NTILE", -1, 3, new int[0], new int[]{0}, new boolean[]{false});
      WindowTableLens lens = new WindowTableLens(t, new WindowTableLens.Spec[]{ntile});
      // desc: 40,30,20,10 → buckets 1,1,2,3
      assertEquals(1, cell(lens, 0, 1)); // 40
      assertEquals(1, cell(lens, 1, 1)); // 30
      assertEquals(2, cell(lens, 2, 1)); // 20
      assertEquals(3, cell(lens, 3, 1)); // 10
   }

   @Test
   void lag_lead_firstValue_withEdgeNulls() {
      // one partition, ordered by amount ASC: 10,20,30
      Object[][] data = {{"amount"},{10.0},{20.0},{30.0}};
      DefaultTableLens t = new DefaultTableLens(data); t.setHeaderRowCount(1);
      WindowTableLens.Spec lag  = new WindowTableLens.Spec("lg","LAG", 0, 1, new int[0], new int[]{0}, new boolean[]{true});
      WindowTableLens.Spec lead = new WindowTableLens.Spec("ld","LEAD",0, 1, new int[0], new int[]{0}, new boolean[]{true});
      WindowTableLens.Spec fv   = new WindowTableLens.Spec("fv","FIRST_VALUE",0,0,new int[0], new int[]{0}, new boolean[]{true});
      WindowTableLens lens = new WindowTableLens(t, new WindowTableLens.Spec[]{lag, lead, fv});
      // rows in base order == asc order here: 10,20,30
      assertNull(cell(lens, 0, 1));                  // LAG(10) → none before
      assertEquals(20.0, cell(lens, 0, 2));          // LEAD(10) → 20
      assertEquals(10.0, cell(lens, 0, 3));          // FIRST_VALUE → 10
      assertEquals(10.0, cell(lens, 1, 1));          // LAG(20) → 10
      assertEquals(30.0, cell(lens, 1, 2));          // LEAD(20) → 30
      assertEquals(10.0, cell(lens, 1, 3));          // FIRST_VALUE → 10
      assertEquals(20.0, cell(lens, 2, 1));          // LAG(30) → 20
      assertNull(cell(lens, 2, 2));                  // LEAD(30) → none after
      assertEquals(10.0, cell(lens, 2, 3));          // FIRST_VALUE → 10
   }

   @Test
   void windowedSum_runningWhenOrdered_partitionTotalWhenNot() {
      Object[][] data = {{"stage","amount"},{"A",10.0},{"A",20.0},{"A",30.0}};
      DefaultTableLens t = new DefaultTableLens(data); t.setHeaderRowCount(1);
      // running SUM ordered asc: 10,30,60
      WindowTableLens.Spec run = new WindowTableLens.Spec("rt","SUM",1,0,new int[]{0}, new int[]{1}, new boolean[]{true});
      // partition-total SUM (no order): 60,60,60
      WindowTableLens.Spec tot = new WindowTableLens.Spec("pt","SUM",1,0,new int[]{0}, new int[0], new boolean[0]);
      WindowTableLens lens = new WindowTableLens(t, new WindowTableLens.Spec[]{run, tot});
      assertEquals(10.0, cell(lens,0,2)); assertEquals(60.0, cell(lens,0,3));
      assertEquals(30.0, cell(lens,1,2)); assertEquals(60.0, cell(lens,1,3));
      assertEquals(60.0, cell(lens,2,2)); assertEquals(60.0, cell(lens,2,3));
   }

   @Test
   void percentRank_tiesShareFirstPosition() {
      // A desc, tied: 30,30,10 → rank(first-tied-position) 1,1,3 over n=3
      // PERCENT_RANK = (rank-1)/(n-1): (1-1)/2=0.0, (1-1)/2=0.0, (3-1)/2=1.0
      WindowTableLens.Spec pr = new WindowTableLens.Spec(
         "pr", "PERCENT_RANK", -1, 0, new int[]{0}, new int[]{1}, new boolean[]{false});
      WindowTableLens lens = new WindowTableLens(tiedBase(), new WindowTableLens.Spec[]{pr});
      assertEquals(0.0, cell(lens, 0, 2)); // A/30
      assertEquals(0.0, cell(lens, 1, 2)); // A/30 tie
      assertEquals(1.0, cell(lens, 2, 2)); // A/10
      assertEquals(0.0, cell(lens, 3, 2)); // B/7 — sole row in its partition, size<=1 → 0.0
   }

   @Test
   void cumeDist_tiesShareUpperBound() {
      // A desc, tied: 30,30,10 → rows with order key <= current (in desc direction):
      // 30 → {30,30} = 2/3 ; 30 → 2/3 ; 10 → {30,30,10} = 3/3 = 1.0
      WindowTableLens.Spec cd = new WindowTableLens.Spec(
         "cd", "CUME_DIST", -1, 0, new int[]{0}, new int[]{1}, new boolean[]{false});
      WindowTableLens lens = new WindowTableLens(tiedBase(), new WindowTableLens.Spec[]{cd});
      assertEquals(2.0 / 3, cell(lens, 0, 2)); // A/30
      assertEquals(2.0 / 3, cell(lens, 1, 2)); // A/30 tie
      assertEquals(1.0, cell(lens, 2, 2));     // A/10
      assertEquals(1.0, cell(lens, 3, 2));     // B/7 — sole row in its partition
   }

   @Test
   void rankWithNoOrder_tiesWholePartition_evenWhenSharingLensWithOrderedAggregate() {
      // A RANK() OVER (PARTITION BY stage) with NO ORDER BY, sharing the lens with a running
      // SUM that DOES declare ORDER BY amount. The lens sorts rows once by the shared order
      // (amount); the rank kernel must key off ITS OWN (empty) order — so the whole partition
      // ties (RANK = 1 for every row), NOT rank by the SUM's amount order (which would give
      // 1,2,3 within partition A). This test FAILS if the rank kernel uses the shared order.
      Object[][] data = {{"stage","amount"},{"A",10.0},{"A",20.0},{"A",30.0},{"B",5.0}};
      DefaultTableLens t = new DefaultTableLens(data); t.setHeaderRowCount(1);
      // running SUM ordered asc by amount (partition by stage)
      WindowTableLens.Spec run = new WindowTableLens.Spec(
         "rt", "SUM", 1, 0, new int[]{0}, new int[]{1}, new boolean[]{true});
      // RANK partitioned by stage with NO order → whole partition ties at 1
      WindowTableLens.Spec rk = new WindowTableLens.Spec(
         "rk", "RANK", -1, 0, new int[]{0}, new int[0], new boolean[0]);
      WindowTableLens lens = new WindowTableLens(t, new WindowTableLens.Spec[]{run, rk});
      // running SUM (col 2): A → 10,30,60 ; B → 5
      assertEquals(10.0, cell(lens, 0, 2));
      assertEquals(30.0, cell(lens, 1, 2));
      assertEquals(60.0, cell(lens, 2, 2));
      assertEquals(5.0,  cell(lens, 3, 2));
      // RANK (col 3): every row ties within its partition → all 1
      assertEquals(1, cell(lens, 0, 3));
      assertEquals(1, cell(lens, 1, 3));
      assertEquals(1, cell(lens, 2, 3));
      assertEquals(1, cell(lens, 3, 3));
   }

   @Test
   void countAvgMinMax_partitionWide() {
      Object[][] data = {{"stage","amount"},{"A",10.0},{"A",30.0}};
      DefaultTableLens t = new DefaultTableLens(data); t.setHeaderRowCount(1);
      WindowTableLens.Spec[] s = {
         new WindowTableLens.Spec("c","COUNT",1,0,new int[]{0},new int[0],new boolean[0]),
         new WindowTableLens.Spec("a","AVG",1,0,new int[]{0},new int[0],new boolean[0]),
         new WindowTableLens.Spec("mn","MIN",1,0,new int[]{0},new int[0],new boolean[0]),
         new WindowTableLens.Spec("mx","MAX",1,0,new int[]{0},new int[0],new boolean[0]),
      };
      WindowTableLens lens = new WindowTableLens(t, s);
      assertEquals(2, cell(lens,0,2));      // COUNT
      assertEquals(20.0, cell(lens,0,3));   // AVG
      assertEquals(10.0, cell(lens,0,4));   // MIN
      assertEquals(30.0, cell(lens,0,5));   // MAX
   }

   @Test
   void unsupportedFunction_failsLoud_notSilentNull() {
      // Any fn the in-memory kernel does not implement must throw, not silently return null for
      // every row — the SQL path may emit fns this lens doesn't support, so a silent null would
      // diverge. (LAST_VALUE moved to its own supported case in Phase 3 — see lastValue_* tests.)
      WindowTableLens.Spec last = new WindowTableLens.Spec(
         "md", "MEDIAN", 1, 0, new int[0], new int[]{1}, new boolean[]{true});
      WindowTableLens lens = new WindowTableLens(base(), new WindowTableLens.Spec[]{last});
      RuntimeException ex = assertThrows(RuntimeException.class, () -> cell(lens, 0, 2));
      assertTrue(ex.getMessage().contains("MEDIAN"),
                 "error should name the unsupported function: " + ex.getMessage());
   }

   @Test
   void movingAverage_2precedingToCurrent() {
      // amount asc 10,20,30,40 in one partition; AVG ROWS 2 PRECEDING..CURRENT ROW
      Object[][] d = {{"amount"},{10.0},{20.0},{30.0},{40.0}};
      DefaultTableLens t = new DefaultTableLens(d); t.setHeaderRowCount(1);
      WindowTableLens.Spec ma = new WindowTableLens.Spec(
         "ma","AVG",0,0,new int[0],new int[]{0},new boolean[]{true}, "PRECEDING",2,"CURRENT_ROW",0);
      WindowTableLens lens = new WindowTableLens(t, new WindowTableLens.Spec[]{ma});
      assertEquals(10.0, cell(lens,0,1));                 // [10]
      assertEquals(15.0, cell(lens,1,1));                 // [10,20]
      assertEquals(20.0, cell(lens,2,1));                 // [10,20,30]
      assertEquals(30.0, cell(lens,3,1));                 // [20,30,40]
   }

   @Test
   void lastValue_wholePartition() {
      Object[][] d = {{"stage","amount"},{"A",10.0},{"A",30.0},{"A",20.0}};
      DefaultTableLens t = new DefaultTableLens(d); t.setHeaderRowCount(1);
      WindowTableLens.Spec lv = new WindowTableLens.Spec(   // frame-less LAST_VALUE, ordered asc
         "lv","LAST_VALUE",1,0,new int[]{0},new int[]{1},new boolean[]{true});  // no-frame ctor
      WindowTableLens lens = new WindowTableLens(t, new WindowTableLens.Spec[]{lv});
      // asc order 10,20,30 → last = 30 for every row
      assertEquals(30.0, cell(lens,0,2));
      assertEquals(30.0, cell(lens,1,2));
      assertEquals(30.0, cell(lens,2,2));
   }

   @Test
   void emptyFrame_sumIsNull_countIsZero_notSumZero() {
      // single partition, ordered asc: 10,20,30. ROWS BETWEEN 2 FOLLOWING AND 3 FOLLOWING.
      // For the LAST row (p=2, size=3) both bounds fall past the end of the partition → empty
      // frame. SUM/AVG/MIN/MAX must be NULL (ANSI/JDBC parity), NOT 0.0; COUNT must be 0.
      Object[][] d = {{"amount"},{10.0},{20.0},{30.0}};
      DefaultTableLens t = new DefaultTableLens(d); t.setHeaderRowCount(1);
      WindowTableLens.Spec sum = new WindowTableLens.Spec(
         "s","SUM",0,0,new int[0],new int[]{0},new boolean[]{true},"FOLLOWING",2,"FOLLOWING",3);
      WindowTableLens.Spec cnt = new WindowTableLens.Spec(
         "c","COUNT",0,0,new int[0],new int[]{0},new boolean[]{true},"FOLLOWING",2,"FOLLOWING",3);
      WindowTableLens lens = new WindowTableLens(t, new WindowTableLens.Spec[]{sum, cnt});
      assertNull(cell(lens, 2, 1));         // SUM over empty frame on last row → null, not 0.0
      assertEquals(0, cell(lens, 2, 2));    // COUNT over empty frame on last row → 0
   }

   @Test
   void centeredFrame_1precedingTo1following() {
      Object[][] d = {{"amount"},{10.0},{20.0},{30.0}};
      DefaultTableLens t = new DefaultTableLens(d); t.setHeaderRowCount(1);
      WindowTableLens.Spec s = new WindowTableLens.Spec(
         "c","SUM",0,0,new int[0],new int[]{0},new boolean[]{true},"PRECEDING",1,"FOLLOWING",1);
      WindowTableLens lens = new WindowTableLens(t, new WindowTableLens.Spec[]{s});
      assertEquals(30.0, cell(lens,0,1));    // [10,20]
      assertEquals(60.0, cell(lens,1,1));    // [10,20,30]
      assertEquals(50.0, cell(lens,2,1));    // [20,30]
   }

   // ── Phase 4: RANGE / GROUPS frame modes ─────────────────────────────────────

   @Test
   void rows_byteParity_unchanged() {
      // Same as movingAverage_2precedingToCurrent (Phase 3) — the new ctor overload defaults to
      // mode="ROWS" via the Phase-3 11-arg ctor, so this must still yield the Phase-3 numbers.
      Object[][] d = {{"amount"},{10.0},{20.0},{30.0},{40.0}};
      DefaultTableLens t = new DefaultTableLens(d); t.setHeaderRowCount(1);
      WindowTableLens.Spec ma = new WindowTableLens.Spec(
         "ma","AVG",0,0,new int[0],new int[]{0},new boolean[]{true}, "PRECEDING",2,"CURRENT_ROW",0);
      WindowTableLens lens = new WindowTableLens(t, new WindowTableLens.Spec[]{ma});
      assertEquals(10.0, cell(lens,0,1));                 // [10]
      assertEquals(15.0, cell(lens,1,1));                 // [10,20]
      assertEquals(20.0, cell(lens,2,1));                 // [10,20,30]
      assertEquals(30.0, cell(lens,3,1));                 // [20,30,40]
   }

   @Test
   void range_numeric_valueOffset() {
      // amount asc 10,20,25,40 in one partition; SUM RANGE BETWEEN 5 PRECEDING AND CURRENT ROW.
      // Per row, include rows whose amount is within [amt-5, amt]:
      //   10 -> {10} = 10 ; 20 -> {20} = 20 ; 25 -> {20,25} = 45 ; 40 -> {40} = 40
      Object[][] d = {{"amount"},{10.0},{20.0},{25.0},{40.0}};
      DefaultTableLens t = new DefaultTableLens(d); t.setHeaderRowCount(1);
      WindowTableLens.Spec sum = new WindowTableLens.Spec(
         "s", "SUM", 0, 0, new int[0], new int[]{0}, new boolean[]{true},
         "PRECEDING", 5, "CURRENT_ROW", 0, "RANGE", null);
      WindowTableLens lens = new WindowTableLens(t, new WindowTableLens.Spec[]{sum});
      assertEquals(10.0, cell(lens, 0, 1));   // amt=10 -> {10}
      assertEquals(20.0, cell(lens, 1, 1));   // amt=20 -> {20}
      assertEquals(45.0, cell(lens, 2, 1));   // amt=25 -> {20,25}
      assertEquals(40.0, cell(lens, 3, 1));   // amt=40 -> {40}
   }

   @Test
   void range_date_valueOffset_days() {
      // dates spaced 0,1,3,10 days apart, ordered asc. SUM RANGE BETWEEN INTERVAL '2 day'
      // PRECEDING AND CURRENT ROW (offsetUnit=day, offset=2). Row at d0+3 includes rows with
      // date in [d0+1, d0+3] -> the d0+1 and d0+3 rows: amount 20 + 30 = 50.
      Date d0 = Date.valueOf("2026-01-01");
      Date d1 = Date.valueOf("2026-01-02");   // d0+1
      Date d3 = Date.valueOf("2026-01-04");   // d0+3
      Date d10 = Date.valueOf("2026-01-11");  // d0+10
      Object[][] d = {
         {"date", "amount"},
         {d0,  10.0},
         {d1,  20.0},
         {d3,  30.0},
         {d10, 40.0},
      };
      DefaultTableLens t = new DefaultTableLens(d); t.setHeaderRowCount(1);
      WindowTableLens.Spec sum = new WindowTableLens.Spec(
         "s", "SUM", 1, 0, new int[0], new int[]{0}, new boolean[]{true},
         "PRECEDING", 2, "CURRENT_ROW", 0, "RANGE", "day");
      WindowTableLens lens = new WindowTableLens(t, new WindowTableLens.Spec[]{sum});
      assertEquals(10.0, cell(lens, 0, 2));   // d0        -> {d0}                    = 10
      assertEquals(30.0, cell(lens, 1, 2));   // d0+1      -> {d0,d0+1}                = 10+20
      assertEquals(50.0, cell(lens, 2, 2));   // d0+3      -> {d0+1,d0+3}              = 20+30
      assertEquals(40.0, cell(lens, 3, 2));   // d0+10     -> {d0+10}                  = 40
   }

   @Test
   void range_peer_currentRowIncludesTies() {
      // order key desc with a tie: 30,30,10. Running SUM RANGE BETWEEN UNBOUNDED PRECEDING AND
      // CURRENT ROW -> both 30-rows see the combined peer group (60), the 10-row sees all (70).
      // This specifically exercises the DESCENDING-order peer-vs-value-threshold distinction:
      // a naive "value <= current" threshold would wrongly include the 10 for the 30-rows too.
      Object[][] d = {{"amount"},{30.0},{30.0},{10.0}};
      DefaultTableLens t = new DefaultTableLens(d); t.setHeaderRowCount(1);
      WindowTableLens.Spec sum = new WindowTableLens.Spec(
         "s", "SUM", 0, 0, new int[0], new int[]{0}, new boolean[]{false},
         "UNBOUNDED_PRECEDING", 0, "CURRENT_ROW", 0, "RANGE", null);
      WindowTableLens lens = new WindowTableLens(t, new WindowTableLens.Spec[]{sum});
      assertEquals(60.0, cell(lens, 0, 1));   // 30 (first tie) -> {30,30}
      assertEquals(60.0, cell(lens, 1, 1));   // 30 (second tie) -> {30,30}
      assertEquals(70.0, cell(lens, 2, 1));   // 10 -> {30,30,10}
   }

   @Test
   void groups_peerGroupCount() {
      // amount asc with a tied group: 10,10,20,30 -> distinct groups {10},{20},{30} (0,1,2).
      // SUM GROUPS BETWEEN 1 PRECEDING AND CURRENT ROW: the current group plus the one
      // preceding distinct-value group.
      //   group0 (10,10): no preceding group -> just group0 = {10,10} = 20 for both rows
      //   group1 (20):    group0+group1 = {10,10,20} = 40
      //   group2 (30):    group1+group2 = {20,30} = 50
      Object[][] d = {{"amount"},{10.0},{10.0},{20.0},{30.0}};
      DefaultTableLens t = new DefaultTableLens(d); t.setHeaderRowCount(1);
      WindowTableLens.Spec sum = new WindowTableLens.Spec(
         "s", "SUM", 0, 0, new int[0], new int[]{0}, new boolean[]{true},
         "PRECEDING", 1, "CURRENT_ROW", 0, "GROUPS", null);
      WindowTableLens lens = new WindowTableLens(t, new WindowTableLens.Spec[]{sum});
      assertEquals(20.0, cell(lens, 0, 1));   // 10 (first of tied group) -> {10,10}
      assertEquals(20.0, cell(lens, 1, 1));   // 10 (second of tied group) -> {10,10}
      assertEquals(40.0, cell(lens, 2, 1));   // 20 -> {10,10,20}
      assertEquals(50.0, cell(lens, 3, 1));   // 30 -> {20,30}
   }

   // ── Phase 4 review Task 3: symmetric RANGE/GROUPS bounds + null-order-value guard ──────────

   @Test
   void range_symmetricPreceding() {
      // amount asc 10,12,14,16,18 (spacing 2), one partition. SUM RANGE BETWEEN 5 PRECEDING AND
      // 2 PRECEDING -> per row, include rows with amount in [v-5, v-2] (excludes current row,
      // since v-2 < v). Before the fix, endBound="PRECEDING" hit the `default: throw` in
      // rangeEnd (only UNBOUNDED_FOLLOWING/CURRENT_ROW/FOLLOWING were accepted) -> RED.
      Object[][] d = {{"amount"}, {10.0}, {12.0}, {14.0}, {16.0}, {18.0}};
      DefaultTableLens t = new DefaultTableLens(d); t.setHeaderRowCount(1);
      WindowTableLens.Spec sum = new WindowTableLens.Spec(
         "s", "SUM", 0, 0, new int[0], new int[]{0}, new boolean[]{true},
         "PRECEDING", 5, "PRECEDING", 2, "RANGE", null);
      WindowTableLens lens = new WindowTableLens(t, new WindowTableLens.Spec[]{sum});
      assertNull(cell(lens, 0, 1));           // v=10 -> [5,8]   -> {}        = null
      assertEquals(10.0, cell(lens, 1, 1));   // v=12 -> [7,10]  -> {10}      = 10
      assertEquals(22.0, cell(lens, 2, 1));   // v=14 -> [9,12]  -> {10,12}   = 22
      assertEquals(26.0, cell(lens, 3, 1));   // v=16 -> [11,14] -> {12,14}   = 26
      assertEquals(30.0, cell(lens, 4, 1));   // v=18 -> [13,16] -> {14,16}   = 30
   }

   @Test
   void range_symmetricFollowing() {
      // amount asc 10,11,12,13,14 (spacing 1), one partition. SUM RANGE BETWEEN 1 FOLLOWING AND
      // 3 FOLLOWING -> per row, include rows with amount in [v+1, v+3]. Before the fix,
      // startBound="FOLLOWING" hit the `default: throw` in rangeStart -> RED.
      Object[][] d = {{"amount"}, {10.0}, {11.0}, {12.0}, {13.0}, {14.0}};
      DefaultTableLens t = new DefaultTableLens(d); t.setHeaderRowCount(1);
      WindowTableLens.Spec sum = new WindowTableLens.Spec(
         "s", "SUM", 0, 0, new int[0], new int[]{0}, new boolean[]{true},
         "FOLLOWING", 1, "FOLLOWING", 3, "RANGE", null);
      WindowTableLens lens = new WindowTableLens(t, new WindowTableLens.Spec[]{sum});
      assertEquals(36.0, cell(lens, 0, 1));   // v=10 -> [11,13] -> {11,12,13} = 36
      assertEquals(39.0, cell(lens, 1, 1));   // v=11 -> [12,14] -> {12,13,14} = 39
      assertEquals(27.0, cell(lens, 2, 1));   // v=12 -> [13,15] -> {13,14}    = 27
      assertEquals(14.0, cell(lens, 3, 1));   // v=13 -> [14,16] -> {14}       = 14
      assertNull(cell(lens, 4, 1));           // v=14 -> [15,17] -> {}        = null
   }

   @Test
   void groups_precedingRange() {
      // amount asc 10,10,20,30,40 -> distinct groups {10,10}=g0,{20}=g1,{30}=g2,{40}=g3
      // (lastGroup=3). SUM GROUPS BETWEEN 2 PRECEDING AND 1 PRECEDING -> current group's own
      // group is excluded (end = 1 PRECEDING is strictly before the current group).
      //   g0 rows (pGroup=0): raw start=0-2=-2, raw end=0-1=-1 -> rawEnd<0 -> EMPTY (null).
      //   Per Postgres GROUPS semantics this frame lies entirely before group 0 and returns no
      //   rows (SUM of no rows is NULL) — NOT clamped to group 0's own {10,10}. Before the fix,
      //   BOTH raw indices were clamped into [0,lastGroup] before the empty check, so -2/-1 both
      //   clamped to 0 and wrongly returned group 0 (20.0) instead of null.
      //   g1 (20, pGroup=1): raw start=1-2=-1, raw end=1-1=0 -> not empty; clamp start to 0,
      //   end stays 0 -> frame=group0={10,10}=20.0 (unchanged by the fix: this frame is only
      //   PARTIALLY out of range, which is legitimately clamped, not empty).
      //   g2 (30, pGroup=2): raw start=0, raw end=1 -> frame=groups0..1={10,10,20}=40.0.
      //   g3 (40, pGroup=3): raw start=1, raw end=2 -> frame=groups1..2={20,30}=50.0.
      Object[][] d = {{"amount"}, {10.0}, {10.0}, {20.0}, {30.0}, {40.0}};
      DefaultTableLens t = new DefaultTableLens(d); t.setHeaderRowCount(1);
      WindowTableLens.Spec sum = new WindowTableLens.Spec(
         "s", "SUM", 0, 0, new int[0], new int[]{0}, new boolean[]{true},
         "PRECEDING", 2, "PRECEDING", 1, "GROUPS", null);
      WindowTableLens lens = new WindowTableLens(t, new WindowTableLens.Spec[]{sum});
      assertNull(cell(lens, 0, 1));           // group0 (10,10): frame entirely before group0 -> empty
      assertNull(cell(lens, 1, 1));           // group0 (10,10): same
      assertEquals(20.0, cell(lens, 2, 1));   // group1 (20): [group(-1)->0, group0] -> {10,10}
      assertEquals(40.0, cell(lens, 3, 1));   // group2 (30): [group0, group1] -> {10,10,20}
      assertEquals(50.0, cell(lens, 4, 1));   // group3 (40): [group1, group2] -> {20,30}
   }

   @Test
   void groups_followingRange_lastGroupEmpty() {
      // Symmetric to groups_precedingRange: amount asc 10,10,20,30,40 -> groups
      // {10,10}=g0,{20}=g1,{30}=g2,{40}=g3 (lastGroup=3). SUM GROUPS BETWEEN 1 FOLLOWING AND
      // 2 FOLLOWING.
      //   g0 rows (pGroup=0): start=1,end=2 -> frame=groups1..2={20,30}=50.0.
      //   g1 (20, pGroup=1): start=2,end=3 -> frame=groups2..3={30,40}=70.0.
      //   g2 (30, pGroup=2): start=3,end=4 -> not empty (rawStart==lastGroup); clamp end to 3
      //   -> frame=group3={40}=40.0.
      //   g3 (40, pGroup=3, the LAST group): start=4,end=5 -> rawStart(4) > lastGroup(3) ->
      //   EMPTY (null). Before the fix, both raw indices would clamp into [0,3] (start=3,
      //   end=3) and wrongly return group3's own value (40.0) instead of null.
      Object[][] d = {{"amount"}, {10.0}, {10.0}, {20.0}, {30.0}, {40.0}};
      DefaultTableLens t = new DefaultTableLens(d); t.setHeaderRowCount(1);
      WindowTableLens.Spec sum = new WindowTableLens.Spec(
         "s", "SUM", 0, 0, new int[0], new int[]{0}, new boolean[]{true},
         "FOLLOWING", 1, "FOLLOWING", 2, "GROUPS", null);
      WindowTableLens lens = new WindowTableLens(t, new WindowTableLens.Spec[]{sum});
      assertEquals(50.0, cell(lens, 0, 1));   // group0
      assertEquals(50.0, cell(lens, 1, 1));   // group0
      assertEquals(70.0, cell(lens, 2, 1));   // group1
      assertEquals(40.0, cell(lens, 3, 1));   // group2
      assertNull(cell(lens, 4, 1));           // group3 (LAST group) -> empty
   }

   @Test
   void range_descValueOffset() {
      // amount asc in base order 5,10,15,20 but ORDER BY amount DESC -> sorted 20,15,10,5. SUM
      // RANGE BETWEEN 5 PRECEDING AND CURRENT ROW: for a DESC key, "PRECEDING" is a LARGER
      // value, so the frame per row v is {rows with value in [v, v+5]}.
      Object[][] d = {{"amount"}, {5.0}, {10.0}, {15.0}, {20.0}};
      DefaultTableLens t = new DefaultTableLens(d); t.setHeaderRowCount(1);
      WindowTableLens.Spec sum = new WindowTableLens.Spec(
         "s", "SUM", 0, 0, new int[0], new int[]{0}, new boolean[]{false},
         "PRECEDING", 5, "CURRENT_ROW", 0, "RANGE", null);
      WindowTableLens lens = new WindowTableLens(t, new WindowTableLens.Spec[]{sum});
      assertEquals(15.0, cell(lens, 0, 1));   // v=5  -> [5,10]  -> {10,5}     = 15
      assertEquals(25.0, cell(lens, 1, 1));   // v=10 -> [10,15] -> {15,10}    = 25
      assertEquals(35.0, cell(lens, 2, 1));   // v=15 -> [15,20] -> {20,15}    = 35
      assertEquals(20.0, cell(lens, 3, 1));   // v=20 -> [20,25] -> {20}       = 20
   }

   @Test
   void range_dateMonth() {
      // end-of-month dates Jan31/Feb28/Mar31 (2026, not a leap year), asc. SUM RANGE BETWEEN
      // INTERVAL '1 month' PRECEDING AND CURRENT ROW must use calendar-month arithmetic (Fix 1's
      // rewritten rangeStart/rangeEnd must still route PRECEDING+CURRENT_ROW through the
      // existing offsetOrderValue/dateOffsetMs month logic unchanged): Mar31 - 1 month = Feb28
      // (Mar 31 clamps to the shorter Feb), so Mar31's frame must EXCLUDE Jan31.
      java.sql.Date jan31 = java.sql.Date.valueOf("2026-01-31");
      java.sql.Date feb28 = java.sql.Date.valueOf("2026-02-28");
      java.sql.Date mar31 = java.sql.Date.valueOf("2026-03-31");
      Object[][] d = {
         {"date", "amount"},
         {jan31, 100.0},
         {feb28, 200.0},
         {mar31, 300.0},
      };
      DefaultTableLens t = new DefaultTableLens(d); t.setHeaderRowCount(1);
      WindowTableLens.Spec sum = new WindowTableLens.Spec(
         "s", "SUM", 1, 0, new int[0], new int[]{0}, new boolean[]{true},
         "PRECEDING", 1, "CURRENT_ROW", 0, "RANGE", "month");
      WindowTableLens lens = new WindowTableLens(t, new WindowTableLens.Spec[]{sum});
      assertEquals(100.0, cell(lens, 0, 2));   // Jan31 -> {Jan31}               = 100
      assertEquals(300.0, cell(lens, 1, 2));   // Feb28 -> {Jan31,Feb28}         = 300
      assertEquals(500.0, cell(lens, 2, 2));   // Mar31 -> {Feb28,Mar31}, no Jan31 = 500
   }

   @Test
   void range_nullOrderValue_failsLoud() {
      // a null in the numeric ORDER BY column with a RANGE value-threshold frame must fail
      // loud (NULLS handling is deferred), not NPE inside orderValue/offsetOrderValue.
      Object[][] d = {{"amount"}, {10.0}, {null}};
      DefaultTableLens t = new DefaultTableLens(d); t.setHeaderRowCount(1);
      WindowTableLens.Spec sum = new WindowTableLens.Spec(
         "s", "SUM", 0, 0, new int[0], new int[]{0}, new boolean[]{true},
         "PRECEDING", 5, "CURRENT_ROW", 0, "RANGE", null);
      WindowTableLens lens = new WindowTableLens(t, new WindowTableLens.Spec[]{sum});
      RuntimeException ex = assertThrows(RuntimeException.class, () -> cell(lens, 0, 1));
      assertTrue(ex.getMessage().contains("null ORDER BY"),
                 "error should name the null-order-value cause: " + ex.getMessage());
   }

   @Test
   void columnIdentifier_delegatesBaseColumnsToUnderlyingTable() {
      // Regression: WindowTableLens must expose its pass-through (base) columns' identifiers by
      // delegating to the underlying table, exactly as it delegates getColType. Without this the
      // base columns report a null identifier, and a downstream column-resolution step that matches
      // a table-qualified identifier (AssetQuery.getVisibleTableLens -> AssetUtil.findColumn) fails
      // to locate them and silently projects them away — leaving only the window column and a
      // wrongly-empty chart. The window column itself has no base identifier; it defaults to its
      // spec header.
      DefaultTableLens t = base();                 // cols: stage(0), amount(1)
      t.setColumnIdentifier(0, "T.stage");
      t.setColumnIdentifier(1, "T.amount");

      WindowTableLens.Spec spec = new WindowTableLens.Spec(
         "rn", "ROW_NUMBER", -1, 0, new int[]{0}, new int[]{1}, new boolean[]{false});
      WindowTableLens lens = new WindowTableLens(t, new WindowTableLens.Spec[]{spec});

      assertEquals("T.stage", lens.getColumnIdentifier(0),
                   "base column identifier must be delegated to the underlying table");
      assertEquals("T.amount", lens.getColumnIdentifier(1),
                   "base column identifier must be delegated to the underlying table");
      assertEquals("rn", lens.getColumnIdentifier(2),
                   "window column identifier defaults to the spec header");
   }

   @Test
   void columnIdentifier_localOverrideWinsOverBaseDelegation() {
      // An explicitly-set identifier on the lens itself takes precedence over base delegation.
      DefaultTableLens t = base();
      t.setColumnIdentifier(0, "T.stage");

      WindowTableLens.Spec spec = new WindowTableLens.Spec(
         "rn", "ROW_NUMBER", -1, 0, new int[]{0}, new int[]{1}, new boolean[]{false});
      WindowTableLens lens = new WindowTableLens(t, new WindowTableLens.Spec[]{spec});
      lens.setColumnIdentifier(0, "OVERRIDE.stage");

      assertEquals("OVERRIDE.stage", lens.getColumnIdentifier(0),
                   "a locally-set identifier must win over base delegation");
   }
}
