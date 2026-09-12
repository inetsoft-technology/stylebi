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
package inetsoft.util.swap;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class XSwapperCriticalWaitTest {
   /**
    * waitForMemory() must not block a caller indefinitely when the memory state never
    * recovers. The existing criticalNoSwap escape only fires when a swap sweep frees
    * nothing at all, so before the wall-clock cap a caller could park here forever --
    * holding whatever locks it already had. In the reported chart hang that was a
    * ViewsheetSandbox read lock, which blocked every writer and, because that lock is
    * non-fair, every reader queued behind them, leaving the chart loading forever.
    */
   @Test
   void waitForMemoryGivesUpWhenMemoryNeverRecovers() throws Exception {
      final XSwapper swapper = spy(XSwapper.getSwapper());
      // memory is critical and stays critical, and nothing is ever freed
      doReturn(XSwapper.CRITICAL_MEM).when(swapper).getMemoryState();
      swapper.setMaxCriticalWait(500L);

      final long[] elapsed = new long[1];
      // run on another thread so an unbounded wait fails the test instead of hanging the run
      Thread caller = new Thread(() -> {
         long start = System.currentTimeMillis();
         swapper.waitForMemory();
         elapsed[0] = System.currentTimeMillis() - start;
      });

      caller.setDaemon(true);
      caller.start();
      caller.join(30000L);

      assertTrue(elapsed[0] > 0, "waitForMemory() did not return within 30s");
      assertTrue(elapsed[0] >= 500L, "returned before the cap: " + elapsed[0] + "ms");
      assertTrue(elapsed[0] < 15000L, "cap was not honored: " + elapsed[0] + "ms");
   }
}
