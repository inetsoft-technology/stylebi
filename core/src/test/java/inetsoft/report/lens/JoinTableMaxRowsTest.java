/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
import inetsoft.uql.XTable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81092: join.table.maxrows limits the data rows of a join, the header rows are not
 * counted, on both the hash and the merge engine, and the limit is exact when both hash scanner
 * threads produce rows.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class JoinTableMaxRowsTest {
   @ParameterizedTest(name = "hash={0}")
   @ValueSource(booleans = { true, false })
   void limitCountsDataRowsNotHeader(boolean hash) {
      // 1 header row + 6 data rows per side, every row joins every row of the other side
      JoinTable lens = join(hash, 3, table(1, 6, 1));
      assertEquals(3, dataRows(lens));
   }

   @ParameterizedTest(name = "hash={0}")
   @ValueSource(booleans = { true, false })
   void limitWithoutHeader(boolean hash) {
      JoinTable lens = join(hash, 3, table(0, 6, 1));
      assertEquals(3, dataRows(lens));
   }

   @ParameterizedTest(name = "hash={0}")
   @ValueSource(booleans = { true, false })
   void noLimitReturnsAllRows(boolean hash) {
      JoinTable lens = join(hash, -1, table(1, 3, 1));
      assertEquals(9, dataRows(lens));
   }

   @ParameterizedTest(name = "hash={0}")
   @ValueSource(booleans = { true, false })
   void limitAboveRowCountReturnsAllRows(boolean hash) {
      JoinTable lens = join(hash, 10, table(1, 3, 1));
      assertEquals(9, dataRows(lens));
   }

   /** Both hash scanner threads add rows, the limit must hold on every run. */
   @Test
   void hashLimitIsExactWithBothScanners() {
      for(int i = 0; i < 300; i++) {
         // keys 0..4, each key twice per side: 20 matching pairs
         JoinTable lens = join(true, 7, keyed(1, 10));
         assertEquals(7, dataRows(lens), "run " + i);
      }
   }

   /**
    * Bug #81186: the hash and the merge engine honor join.table.maxrows the same way on every
    * join type, with and without header row. At most maxrows data rows come back (0 gives none),
    * every row belongs to the full join, the unlimited result is the same on both engines, and
    * the header rows are kept.
    */
   @ParameterizedTest(name = "type={0} header={1} max={2}")
   @MethodSource("matrix")
   void enginesAgreeOnLimit(int type, int headers, int max) {
      List<String> fullHash = rows(joinOf(true, type, Integer.MAX_VALUE, headers));
      List<String> fullMerge = rows(joinOf(false, type, Integer.MAX_VALUE, headers));
      assertEquals(fullHash, fullMerge, "unlimited result differs between engines");
      int total = fullHash.size();

      for(boolean hash : new boolean[] { true, false }) {
         JoinTable lens = joinOf(hash, type, max, headers);
         List<String> got = rows(lens);
         assertEquals(headers, lens.getHeaderRowCount());
         assertEquals(Math.min(max, total), got.size(), "hash=" + hash);
         List<String> rest = new ArrayList<>(fullHash);

         for(String row : got) {
            assertTrue(rest.remove(row), "row not in the full join (hash=" + hash + "): " + row);
         }
      }

      // the merge engine is deterministic
      assertEquals(rows(joinOf(false, type, max, headers)),
                   rows(joinOf(false, type, max, headers)));
   }

   static List<Arguments> matrix() {
      List<Arguments> list = new ArrayList<>();
      int[] types = { JoinTableLens.INNER_JOIN, JoinTableLens.LEFT_OUTER_JOIN,
                      JoinTableLens.RIGHT_OUTER_JOIN, JoinTableLens.FULL_OUTER_JOIN };

      for(int type : types) {
         for(int headers = 0; headers <= 1; headers++) {
            for(int max : new int[] { 0, 1, 3, 100 }) {
               list.add(Arguments.of(type, headers, max));
            }
         }
      }

      return list;
   }

   private static JoinTable joinOf(boolean hash, int type, int max, int headers) {
      TableLens left = sides(headers, 1, 2, 3, 3, 4);
      TableLens right = sides(headers, 3, 4, 4, 5, 6);
      int[] cols = { 0 };

      return hash ?
         new HashJoinTable(left, right, cols, cols, type, true, max) :
         new MergeJoinTable(left, right, cols, cols, type, true, max);
   }

   /** the sorted data rows (as text) of the join, header rows excluded */
   private static List<String> rows(JoinTable lens) {
      lens.moreRows(XTable.EOT);
      List<String> list = new ArrayList<>();

      for(int r = lens.getHeaderRowCount(); r < lens.getRowCount(); r++) {
         StringBuilder sb = new StringBuilder();

         for(int c = 0; c < lens.getColCount(); c++) {
            sb.append(lens.getObject(r, c)).append('|');
         }

         list.add(sb.toString());
      }

      Collections.sort(list);
      return list;
   }

   private static TableLens sides(int headers, int... keys) {
      Object[][] data = new Object[headers + keys.length][];

      for(int i = 0; i < headers + keys.length; i++) {
         data[i] = i < headers ? new Object[] { "k", "v" } : new Object[] { keys[i - headers], i };
      }

      DefaultTableLens lens = new DefaultTableLens(data);
      lens.setHeaderRowCount(headers);
      return lens;
   }

   // the engines are built directly: JoinTableLens falls back to the hash engine on its own
   // when memory is tight, so forceHash=false does not guarantee the merge engine
   private static JoinTable join(boolean hash, int max, TableLens table) {
      int limit = max < 0 ? Integer.MAX_VALUE : max;
      int[] cols = { 0 };

      return hash ?
         new HashJoinTable(table, table, cols, cols, JoinTableLens.INNER_JOIN, true, limit) :
         new MergeJoinTable(table, table, cols, cols, JoinTableLens.INNER_JOIN, true, limit);
   }

   private static int dataRows(JoinTable lens) {
      lens.moreRows(XTable.EOT);
      return lens.getRowCount() - lens.getHeaderRowCount();
   }

   /** header + n data rows, all with the same key */
   private static TableLens table(int headers, int n, int key) {
      Object[][] data = new Object[headers + n][];

      for(int i = 0; i < headers + n; i++) {
         data[i] = i < headers ? new Object[] { "k", "v" } : new Object[] { key, i };
      }

      DefaultTableLens lens = new DefaultTableLens(data);
      lens.setHeaderRowCount(headers);
      return lens;
   }

   /** header + n data rows, key = i % 5 */
   private static TableLens keyed(int headers, int n) {
      Object[][] data = new Object[headers + n][];

      for(int i = 0; i < headers + n; i++) {
         data[i] = i < headers ? new Object[] { "k", "v" } : new Object[] { (i - headers) % 5, i };
      }

      DefaultTableLens lens = new DefaultTableLens(data);
      lens.setHeaderRowCount(headers);
      return lens;
   }

}
