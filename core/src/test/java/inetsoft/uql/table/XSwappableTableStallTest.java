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
package inetsoft.uql.table;

import inetsoft.test.*;
import inetsoft.util.stall.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static inetsoft.util.stall.StallTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The bounded {@code moreRows} of {@link XSwappableTable} throws on a stall and never
 * reports it as the end of the table (bug #76967).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
public class XSwappableTableStallTest {
   @BeforeEach
   public void setUp() {
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 1000, 200, dumpDir));
      pool = readerPool();
   }

   @AfterEach
   public void tearDown() {
      pool.shutdownNow();
      StallPolicy.setOverride(null);
   }

   @Test
   public void stallThrowsInsteadOfEndOfTable() throws Exception {
      XSwappableTable table = new XSwappableTable(2, false);
      table.addRow(new Object[] { "a", "b" });
      Future<Boolean> reader =
         pool.submit(() -> table.moreRows(1, "test.site", () -> 0, () -> new Thread[0]));

      LockStallException stall = stallIn(failureOf(reader, 15));
      assertEquals("test.site", stall.getSite());
      assertFalse(table.isCompleted());
   }

   @Test
   public void producerAddingRowsIsProgress() throws Exception {
      XSwappableTable table = new XSwappableTable(2, false);
      table.addRow(new Object[] { "a", "b" });
      Thread producer = new Thread(() -> {
         try {
            for(int i = 1; i <= 8; i++) {
               Thread.sleep(300);
               table.addRow(new Object[] { "r" + i, i });
            }
         }
         catch(InterruptedException ignore) {
         }
         finally {
            table.complete();
         }
      });
      producer.setDaemon(true);
      producer.start();
      Future<Boolean> reader =
         pool.submit(() -> table.moreRows(8, "test.site", () -> 0, () -> new Thread[0]));

      assertTrue(reader.get(15, TimeUnit.SECONDS), "slow but progressing is not a stall");
   }

   @Test
   public void completedTableRegistersNothing() {
      XSwappableTable table = new XSwappableTable(2, false);
      table.addRow(new Object[] { "a", "b" });
      table.complete();
      long before = WaitRegistry.global().getBeginCount();

      assertFalse(table.moreRows(5, "test.site", () -> 0, () -> new Thread[0]));
      assertTrue(table.moreRows(0, "test.site", () -> 0, () -> new Thread[0]));
      assertEquals(before, WaitRegistry.global().getBeginCount());
   }

   @TempDir
   File dumpDir;
   private ExecutorService pool;
}
