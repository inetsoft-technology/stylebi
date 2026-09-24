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
package inetsoft.report.filter;

import inetsoft.report.TableLens;
import inetsoft.report.lens.DefaultTableLens;
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

import static inetsoft.util.stall.StallTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A crosstab over a stalled lens fails its reader with the stall instead of showing partial
 * or empty data (bug #76967, final review I3). Any other failure of the crosstab's processing
 * is still only logged, as before.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
public class CrossTabFilterStallTest {
   @BeforeEach
   public void setUp() {
      resetGlobalStallState();
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 1000, 200, dumpDir));
      pool = readerPool();
   }

   @AfterEach
   public void tearDown() {
      if(gated != null) {
         gated.open();
      }

      pool.shutdownNow();
      StallPolicy.setOverride(null);
   }

   @Test
   public void stalledBaseFailsTheCrosstabReader() throws Exception {
      gated = new GatedTable(30);
      CrossTabFilter crosstab = crosstab(summary(gated));
      Future<Integer> reader = pool.submit(crosstab::getRowCount);

      assertEquals("SummaryFilter.waitForRow", stallIn(failureOf(reader, 15)).getSite());
      // the crosstab was not generated from partial data: a later read fails too
      assertNotNull(stallIn(failureOf(pool.submit(() -> crosstab.getObject(1, 1)), 15)));
   }

   @Test
   public void workerStallIsNotTheEndOfTheCrosstab() throws Exception {
      LockStallException original = new LockStallException("nested.site", "worker", 1234, null);
      CrossTabFilter crosstab = crosstab(summary(new FailingTable(30, 5, original)));
      Future<Integer> reader = pool.submit(crosstab::getRowCount);

      assertSame(original, stallIn(failureOf(reader, 15)).getCause(),
                 "the crosstab's reader gets the worker's stall");
   }

   @Test
   public void otherFailureIsOnlyLoggedAsBefore() throws Exception {
      TableLens base = new DefaultTableLens(data(30)) {
         @Override
         public boolean moreRows(int row) {
            if(row >= 5) {
               throw new IllegalStateException("boom");
            }

            return super.moreRows(row);
         }
      };
      CrossTabFilter crosstab = crosstab(base);

      // as before: the failure is logged by process() and the crosstab has no data
      Throwable failure = failureOf(pool.submit(crosstab::getRowCount), 15);
      assertInstanceOf(NullPointerException.class, failure);
      assertNull(LockStallException.find(failure));
   }

   private static SummaryFilter summary(TableLens base) {
      return new SummaryFilter(base, new int[] { 0 }, new int[] { 1 }, new SumFormula(), null);
   }

   private static CrossTabFilter crosstab(TableLens base) {
      return new CrossTabFilter(base, new int[] { 0 }, new int[0], new int[] { 1 },
                                new Formula[] { new SumFormula() });
   }

   @TempDir
   File dumpDir;
   private ExecutorService pool;
   private GatedTable gated;
}
