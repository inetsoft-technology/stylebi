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
}
