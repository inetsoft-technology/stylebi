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
import inetsoft.report.internal.table.MergedRow;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77681 / #77685, a distinct union pass that was superseded on its own thread added one
 * stray row into the newer pass's completed rows. When the newer pass ended exactly on an 8192
 * row block, XSwappableObjectList.add() opened a new fragment instead of refusing the add, and
 * the union showed the row twice.
 *
 * <p>The left base re-fires a change event the first time MergedRow.matches() reads its row 1,
 * which happens on the hash collision of ("BB", "x") with ("Aa", "x"). A listener on the union
 * reads it again, so a nested pass completes the rows before the outer pass adds its last row.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UnionTableLensBlockBoundaryTest {
   // the left base holds ("Aa", "x"), n - 1 unique rows and ("BB", "x"), plus a header row.
   // 8190 makes the union exactly 8192 rows (one object fragment), 300 is mid-block
   @ParameterizedTest
   @ValueSource(ints = { 8190, 300 })
   void reentrantPassAddsNoDuplicateRow(int n) {
      CollisionTable left = new CollisionTable(n);
      TableLens right = new DefaultTableLens(new Object[][] { { "c1", "c2" } });
      UnionTableLens union = new UnionTableLens(left, right);
      union.addChangeListener(event -> union.moreRows(TableLens.EOT));

      union.moreRows(TableLens.EOT);
      assertTrue(left.fired, "the change event was not fired from MergedRow.matches()");

      int expected = n + 2;
      assertEquals(expected, union.getRowCount());
      int bb = 0;

      for(int r = 1; union.moreRows(r); r++) {
         if("BB".equals(union.getObject(r, 0))) {
            bb++;
         }
      }

      assertEquals(1, bb, "BB/x is not in the union exactly once");
      union.dispose();
   }

   private static final class CollisionTable extends DefaultTableLens {
      CollisionTable(int n) {
         super(data(n));
      }

      @Override
      public Object getObject(int r, int c) {
         if(r == 1 && !fired && isReadByMatches()) {
            fired = true;
            fireChangeEvent();
         }

         return super.getObject(r, c);
      }

      private static boolean isReadByMatches() {
         return StackWalker.getInstance().walk(frames -> frames.anyMatch(
            f -> f.getClassName().equals(MergedRow.class.getName()) &&
               f.getMethodName().equals("matches")));
      }

      private static Object[][] data(int n) {
         assertEquals("Aa".hashCode(), "BB".hashCode());
         Object[][] data = new Object[n + 2][];
         data[0] = new Object[] { "c1", "c2" };
         data[1] = new Object[] { "Aa", "x" };

         for(int i = 2; i <= n; i++) {
            data[i] = new Object[] { "k" + i, "v" + i };
         }

         data[n + 1] = new Object[] { "BB", "x" };
         return data;
      }

      private volatile boolean fired;
   }
}
