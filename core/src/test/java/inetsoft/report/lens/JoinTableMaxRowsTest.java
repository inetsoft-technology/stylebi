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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
