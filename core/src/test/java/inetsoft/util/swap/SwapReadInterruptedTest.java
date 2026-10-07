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
package inetsoft.util.swap;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.DistinctTableLens;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.uql.table.XTableFragment;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77916: an interrupt (a script timeout or a cancel) during a swap read closes the
 * FileChannel of the read. It was reported as a lost swap file. It is now a
 * {@link SwapReadInterruptedException}, which is still a {@link SwapFileReadException} so every
 * reader keeps failing loudly, the interrupt flag is kept, and the next read gets the data.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SwapReadInterruptedTest {
   @AfterEach
   void clearInterrupt() {
      Thread.interrupted();
   }

   @Test
   void interruptedIntFragmentReadIsNotALostFile() {
      XIntFragment fragment = new XIntFragment(intValues());
      assertTrue(fragment.swap(), "fragment was not swapped");
      File file = fragment.getFile(fragment.prefix + ".tdat");

      Thread.currentThread().interrupt();
      SwapReadInterruptedException ex =
         assertThrows(SwapReadInterruptedException.class, () -> fragment.getSafely(5));
      assertInterrupted(ex, file);
      Thread.interrupted();

      assertTrue(file.exists(), "swap file of an interrupted read was removed");
      assertEquals(1005, fragment.getSafely(5));
      assertTrue(fragment.swap(), "fragment was not swapped again");
      assertEquals(1007, fragment.getSafely(7));
      fragment.dispose();
   }

   @Test
   void interruptedObjectFragmentReadIsNotALostFile() {
      XObjectFragment<String> fragment =
         new XObjectFragment<>((char) 100, (char) 100, String.class);

      for(int i = 0; i < 100; i++) {
         fragment.add("v" + i);
      }

      fragment.complete();
      assertTrue(fragment.swap(), "fragment was not swapped");
      File file = fragment.getFile(fragment.prefix + "_0.tdat");

      Thread.currentThread().interrupt();
      SwapReadInterruptedException ex =
         assertThrows(SwapReadInterruptedException.class, () -> fragment.getSafely(5));
      assertInterrupted(ex, file);
      Thread.interrupted();

      assertTrue(file.exists(), "swap file of an interrupted read was removed");
      assertEquals("v5", fragment.getSafely(5));
      fragment.dispose();
   }

   @Test
   void interruptedTableColumnLoadIsNotALostFile() {
      for(boolean typed : new boolean[] { true, false }) {
         XSwappableTable table = createTable(typed);
         XTableFragment fragment = table.getTables()[0];
         assertTrue(fragment.swap(false), "table fragment was not swapped");
         Tool.clearUserMessage();

         for(int c = 0; c < table.getColCount(); c++) {
            final int col = c;
            Thread.currentThread().interrupt();
            SwapReadInterruptedException ex = assertThrows(
               SwapReadInterruptedException.class, () -> table.getObject(5, col),
               fragment.getColumns()[c].getClass().getSimpleName());
            assertInterrupted(ex, fragment.getSwapFile());
            Thread.interrupted();
         }

         assertNull(Tool.getUserMessage(), "an interrupted read reported a missing swap file");
         assertTrue(fragment.getSwapFile().exists(), "swap file of an interrupted read was removed");
         assertEquals(4, table.getObject(5, 0));
         assertEquals("s4", table.getObject(5, 1));

         if(typed) {
            assertEquals(4.5, table.getObject(5, 2));
            assertEquals(new Timestamp(4000L), table.getObject(5, 3));
         }

         assertTrue(fragment.swap(false), "table fragment was not swapped again");
         assertEquals("s5", table.getObject(6, 1));
         table.dispose();
      }
   }

   /**
    * A read that fails because the file is gone is still a lost file while the thread is
    * interrupted.
    */
   @Test
   void missingSwapFileOfInterruptedThreadIsStillLost() {
      XIntFragment fragment = new XIntFragment(intValues());
      assertTrue(fragment.swap(), "fragment was not swapped");
      File file = fragment.getFile(fragment.prefix + ".tdat");
      assertTrue(file.delete(), "swap file was not deleted");

      Thread.currentThread().interrupt();
      SwapFileReadException ex =
         assertThrows(SwapFileReadException.class, () -> fragment.getSafely(5));
      assertFalse(ex instanceof SwapReadInterruptedException, ex.toString());
      fragment.dispose();
   }

   /**
    * A distinct table whose base read is interrupted fails loudly, it never completes with
    * the rows read before the interrupt.
    */
   @Test
   void distinctOverInterruptedBaseReadFailsLoudly() {
      XIntFragment fragment = new XIntFragment(intValues());
      assertTrue(fragment.swap(), "fragment was not swapped");
      DefaultTableLens base = new DefaultTableLens(DATA) {
         @Override
         public boolean moreRows(int row) {
            if(row >= 3) {
               // a cancel of the reading thread while it reads a swapped fragment. the read
               // may run on a pool thread, which must not keep the flag after the test
               Thread.currentThread().interrupt();

               try {
                  fragment.getSafely(0);
               }
               finally {
                  Thread.interrupted();
               }
            }

            return super.moreRows(row);
         }
      };
      DistinctTableLens lens = new DistinctTableLens(base, new int[] { 0 }, false);

      assertThrows(SwapReadInterruptedException.class, () -> lens.moreRows(XTable.EOT));
      assertThrows(SwapFileReadException.class, () -> lens.moreRows(XTable.EOT),
                   "the distinct table completed with the rows read before the interrupt");
      fragment.dispose();
   }

   private static void assertInterrupted(SwapReadInterruptedException ex, File file) {
      assertEquals(file, ex.getFile());
      assertFalse(ex.getMessage().contains("not available"), ex.getMessage());
      assertNotNull(SwapFileReadException.find(ex));
      assertSame(ex, DataUnavailable.find(ex));
      assertTrue(Thread.currentThread().isInterrupted(), "the interrupt flag was cleared");
   }

   private static int[] intValues() {
      int[] values = new int[100];

      for(int i = 0; i < values.length; i++) {
         values[i] = 1000 + i;
      }

      return values;
   }

   private static XSwappableTable createTable(boolean typed) {
      XSwappableTable table = typed ?
         new XSwappableTable(new Class[] { Integer.class, String.class, Double.class, Timestamp.class }) :
         new XSwappableTable(2, false);
      table.addRow(typed ? new Object[] { "a", "b", "c", "d" } : new Object[] { "a", "b" });

      for(int i = 0; i < 50; i++) {
         table.addRow(typed ? new Object[] { i, "s" + i, i + 0.5, new Timestamp(i * 1000L) } :
                         new Object[] { i, "s" + i });
      }

      table.complete();
      return table;
   }

   private static final Object[][] DATA = {
      { "key", "value" }, { "b", 1 }, { "a", 2 }, { "c", 3 }, { "a", 4 }, { "b", 5 }, { "d", 6 }
   };
}
