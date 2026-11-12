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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.report.TableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.XConstants;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.swap.XSwapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;

/**
 * Join lenses: {@code JoinTableLens} (hash and merge engines), {@code MergedJoinTableLens},
 * {@code SelfJoinTableLens} and {@code CrossJoinTableLens}.
 * <p>
 * The merge engine is only chosen when {@code join.table.forceHash} is not "true" and the swapper
 * reports less than {@code NORM_MEM}, so the tests that need it make the swapper report
 * {@code LOW_MEM} while the lens is built and then check which engine the lens really holds.
 * <p>
 * Not covered: the join-row swapping to disk once more than 0x1fff * 20 rows are pending, and
 * the descriptor (meta info) of a joined table.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class JoinLensesTest {
   @BeforeEach
   void saveState() throws Exception {
      oldForceHash = SreeEnv.getProperty("join.table.forceHash");
      oldMaxRows = SreeEnv.getProperty("join.table.maxrows");
      oldWarnMaxRows = SreeEnv.getProperty("always.warn.joinMaxRows");
      oldMaxCell = SreeEnv.getProperty("crossJoin.maxCellCount");
      oldInited = field(Tool.class, "sinited").get(null);
      oldSensitive = field(Tool.class, "sensitive").get(null);
      // the case sensitivity is read once from the environment: pin it to the default
      field(Tool.class, "sensitive").set(null, false);
      field(Tool.class, "sinited").set(null, true);
      SreeEnv.setProperty("join.table.maxrows", "");
      Tool.clearUserMessage();
   }

   @AfterEach
   void restoreState() throws Exception {
      SreeEnv.setProperty("join.table.forceHash", oldForceHash);
      SreeEnv.setProperty("join.table.maxrows", oldMaxRows);
      SreeEnv.setProperty("always.warn.joinMaxRows", oldWarnMaxRows);

      if(oldMaxCell == null) {
         SreeEnv.remove("crossJoin.maxCellCount");
      }
      else {
         SreeEnv.setProperty("crossJoin.maxCellCount", oldMaxCell);
      }

      field(Tool.class, "sensitive").set(null, oldSensitive);
      field(Tool.class, "sinited").set(null, oldInited);
      Tool.clearUserMessage();
   }

   // ----------------------------------------------------------------------------------------
   // cross join

   @Test
   void crossJoinCartesian() {
      // two tables of 2 and 4 data rows (ported from the old worksheet cross join case)
      DefaultTableLens left = new DefaultTableLens(new Object[][] {
         { "ADDRESS", "REGION_ID" },
         { "17 Main St.", 2 },
         { "389 York St.", 1 }
      });
      DefaultTableLens right = new DefaultTableLens(new Object[][] {
         { "CUSTOMER_ID", "EMPLOYEE_ID" },
         { 3, 1 },
         { 13, 4 },
         { 18, 2 },
         { 20, 3 }
      });
      CrossJoinTableLens cross = new CrossJoinTableLens(left, right);

      cross.moreRows(TableLens.EOT);
      assertEquals(4, cross.getColCount());
      assertEquals(1, cross.getHeaderRowCount());
      // 1 header row + 2 x 4 data rows
      assertEquals(9, cross.getRowCount());
      assertEquals("[ADDRESS, REGION_ID, CUSTOMER_ID, EMPLOYEE_ID]", row(cross, 0));

      List<String> expected = new ArrayList<>();

      for(String address : new String[] { "17 Main St.|2", "389 York St.|1" }) {
         for(String customer : new String[] { "3|1", "13|4", "18|2", "20|3" }) {
            expected.add(address + "|" + customer);
         }
      }

      Collections.sort(expected);
      assertEquals(expected, data(cross));
      assertFalse(cross.moreRows(9));
   }

   @Test
   void crossJoinMaxRows() {
      SreeEnv.setProperty("join.table.maxrows", "5");
      DefaultTableLens left = new DefaultTableLens(new Object[][] {
         { "L" }, { "a" }, { "b" }, { "c" }
      });
      DefaultTableLens right = new DefaultTableLens(new Object[][] {
         { "R" }, { 1 }, { 2 }, { 3 }, { 4 }
      });
      CrossJoinTableLens cross = new CrossJoinTableLens(left, right);

      // 3 x 4 = 12 pairs, capped at 5 data rows after the header row
      assertTrue(cross.moreRows(5));
      assertFalse(cross.moreRows(6));
      assertEquals(6, cross.getRowCount());
      assertEquals(5, data(cross).size());
      assertEquals(Catalog.getCatalog().getString("join.table.limited", 5),
                   Tool.getUserMessage().getMessage());
   }

   /**
    * The row count once the lens has finished; before that {@code getRowCount()} is negative.
    */
   private static int rowCount(TableLens lens) {
      lens.moreRows(TableLens.EOT);
      return lens.getRowCount();
   }

   @Test
   void crossJoinCellLimit() {
      // 3 rows (with the header) x 4 rows (with the header) x 4 columns = 48 cells
      SreeEnv.setProperty("crossJoin.maxCellCount", "47");
      CrossJoinCellCountBeyondLimitException error = assertThrows(
         CrossJoinCellCountBeyondLimitException.class, () -> new CrossJoinTableLens(
            twoColumns(2), twoColumns(3)));
      assertEquals(Catalog.getCatalog().getString("composer.ws.crossJoin.limitedExceptionMessage"),
                   error.getMessage());
      assertEquals("Cross join result exceeds max cell limit.", error.getMessage());

      // exactly at the limit still joins
      SreeEnv.setProperty("crossJoin.maxCellCount", "48");
      assertEquals(7, rowCount(new CrossJoinTableLens(twoColumns(2), twoColumns(3))));

      // zero, a negative number and text fall back to the default of 50,000,000
      for(String value : new String[] { "0", "-5", "x" }) {
         SreeEnv.setProperty("crossJoin.maxCellCount", value);
         assertEquals(7, rowCount(new CrossJoinTableLens(twoColumns(2), twoColumns(3))),
                      "limit '" + value + "'");
      }
   }

   // ----------------------------------------------------------------------------------------
   // merged (side by side) join

   @Test
   void mergedJoinSideBySide() {
      DefaultTableLens left = new DefaultTableLens(new Object[][] {
         { "A", "B" }, { 1, "a1" }, { 2, "a2" }
      });
      DefaultTableLens right = new DefaultTableLens(new Object[][] {
         { "C" }, { "c1" }, { "c2" }, { "c3" }, { "c4" }
      });
      MergedJoinTableLens merged = new MergedJoinTableLens(left, right);

      assertEquals(3, merged.getColCount());
      // the larger of 3 and 5 rows
      assertEquals(5, merged.getRowCount());
      assertEquals(1, merged.getHeaderRowCount());
      assertEquals("[A, B, C]", row(merged, 0));
      assertEquals("[1, a1, c1]", row(merged, 1));
      assertEquals("[2, a2, c2]", row(merged, 2));
      // the left side is exhausted: null cells
      assertEquals("[null, null, c3]", row(merged, 3));
      assertEquals("[null, null, c4]", row(merged, 4));
      assertTrue(merged.isNull(3, 0));
      assertFalse(merged.isNull(3, 2));

      merged.dispose();
      RuntimeException moreRows = assertThrows(RuntimeException.class, () -> merged.moreRows(0));
      assertTrue(moreRows.getMessage().startsWith("Table is disposed"));
      RuntimeException cols = assertThrows(RuntimeException.class, merged::getColCount);
      assertTrue(cols.getMessage().startsWith("Table is disposed"));
   }

   // ----------------------------------------------------------------------------------------
   // self join

   @Test
   void selfJoinOperators() {
      DefaultTableLens table = new DefaultTableLens(new Object[][] {
         { "A", "B" }, { 1, 1 }, { 1, 2 }, { 2, 1 }, { 3, 3 }, { null, 1 }
      });
      SelfJoinTableLens self = new SelfJoinTableLens(table);

      // no condition: every row passes
      assertEquals(5, data(self).size());

      self.addJoin(0, SelfJoinTableLens.INNER_JOIN, 1);
      assertEquals(Arrays.asList("1|1", "3|3"), data(self));

      self.removeJoins();
      assertEquals(5, data(self).size());

      self.addJoin(0, SelfJoinTableLens.NOT_EQUAL_JOIN, 1);
      assertEquals(Arrays.asList("1|2", "2|1"), data(self));

      self.removeJoins();
      self.addJoin(0, SelfJoinTableLens.GREATER_JOIN, 1);
      assertEquals(Arrays.asList("2|1"), data(self));

      self.removeJoins();
      self.addJoin(0, SelfJoinTableLens.GREATER_EQUAL_JOIN, 1);
      assertEquals(Arrays.asList("1|1", "2|1", "3|3"), data(self));

      self.removeJoins();
      self.addJoin(0, SelfJoinTableLens.LESS_JOIN, 1);
      assertEquals(Arrays.asList("1|2"), data(self));

      self.removeJoins();
      self.addJoin(0, SelfJoinTableLens.LESS_EQUAL_JOIN, 1);
      assertEquals(Arrays.asList("1|1", "1|2", "3|3"), data(self));

      // two conditions are anded: B >= A and B <= A leaves the equal rows
      self.removeJoins();
      self.addJoin(1, SelfJoinTableLens.GREATER_EQUAL_JOIN, 0);
      self.addJoin(1, SelfJoinTableLens.LESS_EQUAL_JOIN, 0);
      assertEquals(Arrays.asList("1|1", "3|3"), data(self));

      // outer joins are refused
      RuntimeException outer = assertThrows(
         RuntimeException.class, () -> self.addJoin(0, XConstants.LEFT_JOIN, 1));
      assertEquals("Unsupported join operation found: " + XConstants.LEFT_JOIN,
                   outer.getMessage());
      assertThrows(RuntimeException.class, () -> self.addJoin(0, XConstants.FULL_JOIN, 1));
   }

   // ----------------------------------------------------------------------------------------
   // JoinTableLens

   @Test
   void fullOuterJoin() {
      for(boolean hash : new boolean[] { true, false }) {
         JoinTableLens joined = join(
            lt(new Object[][] { { 1, "L1" }, { 2, "L2" }, { 3, "L3" } }),
            rt(new Object[][] { { 2, "R2" }, { 3, "R3" }, { 4, "R4" } }),
            new int[] { 0 }, new int[] { 0 }, JoinTableLens.FULL_OUTER_JOIN, true, hash);

         assertEquals(hash ? "HashJoinTable" : "MergeJoinTable", engine(joined));
         assertEquals(Arrays.asList(
            "1|L1|null|null", "2|L2|2|R2", "3|L3|3|R3", "null|null|4|R4"), data(joined),
                      "hash=" + hash);
      }

      assertEquals(JoinTableLens.LEFT_OUTER_JOIN | JoinTableLens.RIGHT_OUTER_JOIN,
                   JoinTableLens.FULL_OUTER_JOIN);
   }

   @Test
   void innerJoinMerge() {
      Object[][] left = { { 1, "L1" }, { 1, "L1b" }, { 2, "L2" }, { null, "LN" }, { 5, "L5" } };
      Object[][] right = { { 1, "R1" }, { 2, "R2" }, { 2, "R2b" }, { null, "RN" }, { 9, "R9" } };
      List<String> expected = new ArrayList<>(Arrays.asList(
         "1|L1|1|R1", "1|L1b|1|R1", "2|L2|2|R2", "2|L2|2|R2b"));
      Collections.sort(expected);

      JoinTableLens merge = join(
         lt(left), rt(right), new int[] { 0 }, new int[] { 0 },
         JoinTableLens.INNER_JOIN, true, false);
      assertEquals("MergeJoinTable", engine(merge));
      assertEquals(expected, data(merge));

      JoinTableLens hash = join(
         lt(left), rt(right), new int[] { 0 }, new int[] { 0 },
         JoinTableLens.INNER_JOIN, true, true);
      assertEquals("HashJoinTable", engine(hash));
      assertEquals(expected, data(hash));
   }

   @Test
   void compositeKey() {
      Object[][] left = {
         { 1, "x", "L1x" }, { 1, "y", "L1y" }, { 2, "x", "L2x" }, { 3, "z", "L3z" }
      };
      Object[][] right = {
         { 1, "x", "R1x" }, { 1, "z", "R1z" }, { 2, "y", "R2y" }, { 3, "z", "R3z" }
      };

      for(boolean hash : new boolean[] { true, false }) {
         JoinTableLens joined = join(
            new DefaultTableLens(withHeader(left, "A", "B", "V")),
            new DefaultTableLens(withHeader(right, "A", "B", "W")),
            new int[] { 0, 1 }, new int[] { 0, 1 }, JoinTableLens.INNER_JOIN, false, hash);

         assertEquals(hash ? "HashJoinTable" : "MergeJoinTable", engine(joined));
         // only the rows equal on both key columns join; the key columns of the right are dropped
         assertEquals(Arrays.asList("1|x|L1x|R1x", "3|z|L3z|R3z"), data(joined), "hash=" + hash);
      }
   }

   @Test
   void engineSelection() {
      Object[][] rows = { { 1, "a" } };

      // forceHash=true wins over a low memory state
      assertEquals("HashJoinTable", engine(build("true", XSwapper.LOW_MEM, rows)));
      assertEquals("HashJoinTable", engine(build("true", XSwapper.CRITICAL_MEM, rows)));
      // anything but "true" leaves the choice to the memory state
      assertEquals("MergeJoinTable", engine(build("false", XSwapper.LOW_MEM, rows)));
      assertEquals("MergeJoinTable", engine(build("false", XSwapper.BAD_MEM, rows)));
      assertEquals("MergeJoinTable", engine(build("", XSwapper.CRITICAL_MEM, rows)));
      assertEquals("HashJoinTable", engine(build("false", XSwapper.NORM_MEM, rows)));
      assertEquals("HashJoinTable", engine(build("false", XSwapper.GOOD_MEM, rows)));
   }

   @Test
   void includeRightJoinColumnsFlag() {
      Object[][] left = { { 1, "L1" }, { 2, "L2" } };
      Object[][] right = { { 1, "R1" }, { 2, "R2" }, { 3, "R3" } };

      for(boolean hash : new boolean[] { true, false }) {
         JoinTableLens without = join(
            lt(left), rt(right), new int[] { 0 }, new int[] { 0 },
            JoinTableLens.INNER_JOIN, false, hash);
         // 2 left + 2 right - 1 key column
         assertEquals(3, without.getColCount());
         assertEquals("[ID, V, W]", row(without, 0));
         assertEquals(Arrays.asList("1|L1|R1", "2|L2|R2"), data(without), "hash=" + hash);

         JoinTableLens with = join(
            lt(left), rt(right), new int[] { 0 }, new int[] { 0 },
            JoinTableLens.INNER_JOIN, true, hash);
         assertEquals(4, with.getColCount());
         assertEquals("[ID, V, ID, W]", row(with, 0));
         assertEquals(Arrays.asList("1|L1|1|R1", "2|L2|2|R2"), data(with), "hash=" + hash);
      }

      // the four argument constructor leaves the right key columns out as well
      JoinTableLens defaults = new JoinTableLens(
         lt(left), rt(right), new int[] { 0 }, new int[] { 0 });
      assertEquals(3, defaults.getColCount());
   }

   @Test
   void maxRowsLimit() {
      SreeEnv.setProperty("join.table.maxrows", "3");
      Object[][] left = new Object[10][];
      Object[][] right = new Object[10][];

      for(int i = 0; i < 10; i++) {
         left[i] = new Object[] { 1, "L" + i };
         right[i] = new Object[] { 1, "R" + i };
      }

      for(String warn : new String[] { "false", "true" }) {
         SreeEnv.setProperty("always.warn.joinMaxRows", warn);

         for(boolean hash : new boolean[] { true, false }) {
            Tool.clearUserMessage();
            JoinTableLens joined = join(
               lt(left), rt(right), new int[] { 0 }, new int[] { 0 },
               JoinTableLens.INNER_JOIN, true, hash);

            // 100 pairs match, the join stops at the limit
            joined.moreRows(TableLens.EOT);
            int dataRows = data(joined).size();
            assertEquals(3, dataRows,
                       "warn=" + warn + ", hash=" + hash + ", rows=" + dataRows);
            assertEquals(dataRows + 1, joined.getRowCount());
            assertFalse(joined.moreRows(dataRows + 1));

            // the message is in the user messages once, however often the lens is asked
            joined.moreRows(1);
            joined.moreRows(2);
            String message = Catalog.getCatalog().getString("join.table.limited", 3);
            assertTrue(Tool.existUserMessage(message));
            assertEquals(message, Tool.getUserMessage().getMessage());
         }
      }

      // below the limit nothing is reported
      SreeEnv.setProperty("join.table.maxrows", "1000");
      JoinTableLens all = join(
         lt(left), rt(right), new int[] { 0 }, new int[] { 0 },
         JoinTableLens.INNER_JOIN, true, true);
      all.moreRows(TableLens.EOT);
      assertEquals(101, all.getRowCount());
      assertNull(Tool.getUserMessage());
   }

   @Test
   @DisplayName("Bug #80298: join.table.maxrows counts data rows only")
   void maxRowsLimitCountsDataRows() {
      SreeEnv.setProperty("join.table.maxrows", "3");
      Object[][] rows = new Object[10][];

      for(int i = 0; i < 10; i++) {
         rows[i] = new Object[] { 1, "v" + i };
      }

      JoinTableLens joined = join(
         lt(rows), rt(rows), new int[] { 0 }, new int[] { 0 },
         JoinTableLens.INNER_JOIN, true, false);
      assertEquals(3, data(joined).size());
   }

   @Test
   void invalidKeyColumnIndex() throws Exception {
      invalidKeyColumnIndex(true);
   }

   @Test
   @DisplayName("Bug #80297: an invalid key column index on the merge engine")
   void invalidKeyColumnIndexMerge() throws Exception {
      invalidKeyColumnIndex(false);
   }

   private void invalidKeyColumnIndex(boolean hash) throws Exception {
      for(int[][] cols : new int[][][] { { { 5 }, { 0 } }, { { 0 }, { 5 } } }) {
         ListAppender<ILoggingEvent> log = capture(ch.qos.logback.classic.Level.ERROR);

         try {
            JoinTableLens joined = join(
               lt(new Object[][] { { 1, "L" } }), rt(new Object[][] { { 1, "R" } }),
               cols[0], cols[1], JoinTableLens.INNER_JOIN, true, hash);

            settle();
            assertFalse(joined.moreRows(0));
            assertEquals(0, joined.getRowCount());
            assertEquals(0, joined.getColCount());
            String side = cols[0][0] == 5 ? "left" : "right";
            assertTrue(messages(log).contains("Invalid join column in " + side + "-hand table"),
                       messages(log).toString());
         }
         finally {
            release(log);
         }
      }
   }

   @Test
   void invalidMaxRowsIgnored() {
      SreeEnv.setProperty("join.table.maxrows", "abc");
      ListAppender<ILoggingEvent> log = capture(ch.qos.logback.classic.Level.WARN);

      try {
         for(boolean hash : new boolean[] { true, false }) {
            JoinTableLens joined = join(
               lt(new Object[][] { { 1, "L1" }, { 1, "L1b" }, { 2, "L2" } }),
               rt(new Object[][] { { 1, "R1" }, { 1, "R1b" }, { 2, "R2" } }),
               new int[] { 0 }, new int[] { 0 }, JoinTableLens.INNER_JOIN, true, hash);

            // no limit: all 2 x 2 + 1 pairs
            assertEquals(5, data(joined).size(), "hash=" + hash);
            assertNull(Tool.getUserMessage());
         }

         assertTrue(messages(log).contains("Invalid value for 'join.table.maxrows' property: abc"),
                    messages(log).toString());
      }
      finally {
         release(log);
      }
   }

   @Test
   void mismatchedKeyColumnCounts() throws Exception {
      for(boolean hash : new boolean[] { true, false }) {
         ListAppender<ILoggingEvent> log = capture(ch.qos.logback.classic.Level.ERROR);

         try {
            JoinTableLens joined = join(
               lt(new Object[][] { { 1, "L" } }), rt(new Object[][] { { 1, "R" } }),
               new int[] { 0, 1 }, new int[] { 0 }, JoinTableLens.INNER_JOIN, true, hash);

            settle();
            assertFalse(joined.moreRows(0));
            assertEquals(0, joined.getRowCount());
            assertTrue(messages(log).contains("Join columns don't match"),
                       messages(log).toString());
         }
         finally {
            release(log);
         }
      }
   }

   @Test
   @DisplayName("Bug #80297: no key columns")
   void noKeyColumns() throws Exception {
      for(boolean hash : new boolean[] { true, false }) {
         ListAppender<ILoggingEvent> log = capture(ch.qos.logback.classic.Level.WARN);

         try {
            JoinTableLens joined = join(
               lt(new Object[][] { { 1, "L" } }), rt(new Object[][] { { 1, "R" } }),
               new int[0], new int[0], JoinTableLens.INNER_JOIN, true, hash);

            settle();
            // not even the header row is produced
            assertFalse(joined.moreRows(0), "hash=" + hash);
            assertEquals(0, joined.getRowCount(), "hash=" + hash);
            assertEquals(0, joined.getHeaderRowCount());
            assertTrue(messages(log).contains("No join columns specified"),
                       messages(log).toString());
         }
         finally {
            release(log);
         }
      }
   }

   @Test
   void caseInsensitiveKeys() throws Exception {
      Object[][] left = { { "a", "L1" }, { "B ", "L2" } };
      Object[][] right = { { "A", "R1" }, { "b", "R2" }, { "c", "R3" } };

      for(boolean hash : new boolean[] { true, false }) {
         field(Tool.class, "sensitive").set(null, false);
         JoinTableLens insensitive = join(
            lt(left), rt(right), new int[] { 0 }, new int[] { 0 },
            JoinTableLens.INNER_JOIN, true, hash);
         // keys are trimmed, and "a" matches "A" when the comparison ignores case
         assertEquals(Arrays.asList("B |L2|b|R2", "a|L1|A|R1"), data(insensitive),
                      "hash=" + hash);

         field(Tool.class, "sensitive").set(null, true);
         JoinTableLens sensitive = join(
            lt(left), rt(right), new int[] { 0 }, new int[] { 0 },
            JoinTableLens.INNER_JOIN, true, hash);
         assertEquals(Collections.emptyList(), data(sensitive), "hash=" + hash);
      }
   }

   @Test
   void headerRowsMin() {
      for(boolean hash : new boolean[] { true, false }) {
         // two header rows on the left, one on the right
         DefaultTableLens twoHeaders = new DefaultTableLens(new Object[][] {
            { "ID", "V" }, { "id-sub", "v-sub" }, { 1, "L1" }, { 2, "L2" }
         });
         twoHeaders.setHeaderRowCount(2);
         JoinTableLens joined = join(
            twoHeaders, new DefaultTableLens(new Object[][] {
               { "ID", "W" }, { 1, "R1" }, { 3, "R3" }
            }),
            new int[] { 0 }, new int[] { 0 }, JoinTableLens.INNER_JOIN, true, hash);

         assertEquals(1, joined.getHeaderRowCount(), "hash=" + hash);
         assertEquals("[ID, V, ID, W]", row(joined, 0));
         // the second header row of the left table is not a data row
         assertEquals(Arrays.asList("1|L1|1|R1"), data(joined));

         // and the other way around
         DefaultTableLens rightTwo = new DefaultTableLens(new Object[][] {
            { "ID", "W" }, { "id-sub", "w-sub" }, { 1, "R1" }, { 2, "R2" }
         });
         rightTwo.setHeaderRowCount(2);
         JoinTableLens reversed = join(
            new DefaultTableLens(new Object[][] { { "ID", "V" }, { 2, "L2" }, { 3, "L3" } }),
            rightTwo, new int[] { 0 }, new int[] { 0 }, JoinTableLens.INNER_JOIN, true, hash);

         assertEquals(1, reversed.getHeaderRowCount());
         assertEquals("[ID, V, ID, W]", row(reversed, 0));
         assertEquals(Arrays.asList("2|L2|2|R2"), data(reversed));
      }

      // two header rows on both sides are both copied
      DefaultTableLens a = new DefaultTableLens(new Object[][] {
         { "ID", "V" }, { "id-a", "v-a" }, { 1, "L1" }
      });
      a.setHeaderRowCount(2);
      DefaultTableLens b = new DefaultTableLens(new Object[][] {
         { "ID", "W" }, { "id-b", "w-b" }, { 1, "R1" }
      });
      b.setHeaderRowCount(2);
      JoinTableLens both = join(
         a, b, new int[] { 0 }, new int[] { 0 }, JoinTableLens.INNER_JOIN, true, true);
      assertEquals(2, both.getHeaderRowCount());
      assertEquals("[ID, V, ID, W]", row(both, 0));
      assertEquals("[id-a, v-a, id-b, w-b]", row(both, 1));
      assertEquals(Arrays.asList("1|L1|1|R1"), data(both));
   }

   @Test
   void cancelDispose() throws Exception {
      for(boolean hash : new boolean[] { true, false }) {
         // the left table delivers its header and first row, then blocks until it is released
         CountDownLatch release = new CountDownLatch(1);
         AtomicReference<Thread> reader = new AtomicReference<>();
         Thread owner = Thread.currentThread();
         DefaultTableLens blocking = new DefaultTableLens(new Object[][] {
            { "ID", "V" }, { 1, "L1" }, { 2, "L2" }, { 3, "L3" }
         }) {
            @Override
            public boolean moreRows(int row) {
               if(row < 2 || Thread.currentThread() == owner) {
                  return super.moreRows(row);
               }

               reader.set(Thread.currentThread());

               try {
                  release.await();
               }
               catch(InterruptedException e) {
                  // cancelled: no more rows
                  return false;
               }

               return super.moreRows(row);
            }
         };

         JoinTableLens joined = join(
            blocking, rt(new Object[][] { { 1, "R1" }, { 2, "R2" } }),
            new int[] { 0 }, new int[] { 0 }, JoinTableLens.INNER_JOIN, true, hash);
         assertFalse(joined.isCancelled());

         joined.cancel();

         assertTrue(joined.isCancelled(), "hash=" + hash);

         // the join worker that was waiting on the table has ended
         Thread worker = reader.get();

         if(worker != null) {
            worker.join(10000);
            assertFalse(worker.isAlive());
         }

         release.countDown();
         assertNotNull(joined.getObject(0, 0));

         // dispose releases the base tables: a read returns null instead of failing
         joined.dispose();
         assertNull(joined.getObject(0, 0));
         assertNull(joined.getObject(0, 1));
         assertSame(null, joined.getLeftTable());
         assertSame(null, joined.getRightTable());
      }
   }

   // ----------------------------------------------------------------------------------------
   // helpers

   private static Object[][] withHeader(Object[][] rows, String... header) {
      Object[][] result = new Object[rows.length + 1][];
      result[0] = header;
      System.arraycopy(rows, 0, result, 1, rows.length);
      return result;
   }

   private static TableLens lt(Object[][] rows) {
      return new DefaultTableLens(withHeader(rows, "ID", "V"));
   }

   private static TableLens rt(Object[][] rows) {
      return new DefaultTableLens(withHeader(rows, "ID", "W"));
   }

   private static DefaultTableLens twoColumns(int dataRows) {
      Object[][] rows = new Object[dataRows + 1][];
      rows[0] = new Object[] { "K", "V" };

      for(int i = 1; i <= dataRows; i++) {
         rows[i] = new Object[] { i, "v" + i };
      }

      return new DefaultTableLens(rows);
   }

   private JoinTableLens build(String forceHash, int memoryState, Object[][] rows) {
      SreeEnv.setProperty("join.table.forceHash", forceHash);

      XSwapper lowMemory = spy(XSwapper.getSwapper());
      doReturn(memoryState).when(lowMemory).getMemoryState();

      try(MockedStatic<XSwapper> swapper = mockStatic(XSwapper.class, CALLS_REAL_METHODS)) {
         swapper.when(XSwapper::getSwapper).thenReturn(lowMemory);
         return new JoinTableLens(
            new DefaultTableLens(withHeader(rows, "ID", "V")),
            new DefaultTableLens(withHeader(rows, "ID", "W")),
            new int[] { 0 }, new int[] { 0 }, JoinTableLens.INNER_JOIN, true);
      }
   }

   /**
    * Builds a join on the hash engine (forceHash) or, for {@code hash == false}, on the merge
    * engine (forceHash off and the swapper reporting low memory while the lens is created).
    */
   private JoinTableLens join(TableLens left, TableLens right, int[] leftCols, int[] rightCols,
                              int type, boolean includeRight, boolean hash)
   {
      SreeEnv.setProperty("join.table.forceHash", Boolean.toString(hash));

      if(hash) {
         return new JoinTableLens(left, right, leftCols, rightCols, type, includeRight);
      }

      XSwapper lowMemory = spy(XSwapper.getSwapper());
      doReturn(XSwapper.LOW_MEM).when(lowMemory).getMemoryState();

      try(MockedStatic<XSwapper> swapper = mockStatic(XSwapper.class, CALLS_REAL_METHODS)) {
         swapper.when(XSwapper::getSwapper).thenReturn(lowMemory);
         return new JoinTableLens(left, right, leftCols, rightCols, type, includeRight);
      }
   }

   /**
    * Waits for the scanner threads of the hash and merge engines that are still running, so a
    * check on the joined rows sees the final state and not a race with the workers.
    */
   private static void settle() throws InterruptedException {
      for(Thread thread : Thread.getAllStackTraces().keySet()) {
         String name = thread.getClass().getName();

         if(name.startsWith("inetsoft.report.lens.HashJoinTable$") ||
            name.startsWith("inetsoft.report.lens.MergeJoinTable$"))
         {
            thread.join(30000);
            assertFalse(thread.isAlive(), name);
         }
      }
   }

   private static String engine(JoinTableLens lens) {
      try {
         return field(JoinTableLens.class, "delegate").get(lens).getClass().getSimpleName();
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }
   }

   /** The cells of one row, like {@code [a, b]}, with null as "null". */
   private static String row(TableLens table, int r) {
      table.moreRows(r);
      List<String> cells = new ArrayList<>();

      for(int c = 0; c < table.getColCount(); c++) {
         cells.add(String.valueOf(table.getObject(r, c)));
      }

      return cells.toString();
   }

   /** The data rows (after the header rows) as "a|b|c", sorted. */
   private static List<String> data(TableLens table) {
      table.moreRows(TableLens.EOT);
      List<String> rows = new ArrayList<>();

      for(int r = table.getHeaderRowCount(); table.moreRows(r); r++) {
         StringBuilder line = new StringBuilder();

         for(int c = 0; c < table.getColCount(); c++) {
            line.append(c == 0 ? "" : "|").append(table.getObject(r, c));
         }

         rows.add(line.toString());
      }

      Collections.sort(rows);
      return rows;
   }

   private static ListAppender<ILoggingEvent> capture(Level level) {
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();

      for(String name : new String[] {
         "inetsoft.report.lens.JoinTable", "inetsoft.report.lens.JoinTableLens" })
      {
         ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(name);
         logger.addAppender(appender);
         logger.setLevel(level);
      }

      return appender;
   }

   private static void release(ListAppender<ILoggingEvent> appender) {
      for(String name : new String[] {
         "inetsoft.report.lens.JoinTable", "inetsoft.report.lens.JoinTableLens" })
      {
         ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(name);
         logger.detachAppender(appender);
         logger.setLevel(null);
      }

      appender.stop();
   }

   private static List<String> messages(ListAppender<ILoggingEvent> appender) {
      List<String> messages = new ArrayList<>();

      for(ILoggingEvent event : new ArrayList<>(appender.list)) {
         messages.add(event.getFormattedMessage());
      }

      return messages;
   }

   private static Field field(Class<?> type, String name) throws Exception {
      Field field = type.getDeclaredField(name);
      field.setAccessible(true);
      return field;
   }

   private String oldForceHash;
   private String oldMaxRows;
   private String oldWarnMaxRows;
   private String oldMaxCell;
   private Object oldInited;
   private Object oldSensitive;
}
