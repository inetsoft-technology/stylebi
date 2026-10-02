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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class XSwapperGCThrottleTest {
   /**
    * Bug #77579: doGC() runs a full collection, so it must be throttled, back off while
    * memory stays critical, let a waiter bypass the back-off, and never run twice for
    * concurrent callers. The swapper's throttle state is shared by the spy and the bean, so
    * the steps run in one method, in order.
    */
   @Test
   void doGCIsThrottledAndBacksOff() throws Exception {
      final XSwapper swapper = spy(XSwapper.getSwapper());
      final AtomicInteger gcs = new AtomicInteger();
      doAnswer(inv -> gcs.incrementAndGet()).when(swapper).runGC();
      // memory is really full, so a collection doesn't help
      doReturn(XSwapper.CRITICAL_MEM).when(swapper).getMemoryState();
      swapper.setGCMinInterval(500L);
      Thread.sleep(600L);

      assertTrue(swapper.doGC(false), "first collection was throttled");
      assertFalse(swapper.doGC(false), "collection inside the interval was not throttled");
      assertEquals(1, gcs.get());

      // still critical, so the interval backed off to 1000ms
      Thread.sleep(600L);
      assertFalse(swapper.doGC(false), "back-off was not applied");
      // a waiter honors only the base interval; back-off is now 2000ms
      assertTrue(swapper.doGC(true), "waiter was held off by the back-off");
      assertEquals(2, gcs.get());

      // memory recovers after the next collection, which resets the back-off
      doReturn(XSwapper.GOOD_MEM).when(swapper).getMemoryState();
      Thread.sleep(600L);
      assertFalse(swapper.doGC(false), "back-off was not applied");
      assertTrue(swapper.doGC(true), "waiter was held off by the back-off");
      Thread.sleep(600L);
      assertTrue(swapper.doGC(false), "back-off was not reset after memory recovered");
      assertEquals(4, gcs.get());

      // concurrent callers claim the slot, so only one of them collects
      Thread.sleep(600L);
      final int count = 8;
      final CountDownLatch start = new CountDownLatch(1);
      final CountDownLatch done = new CountDownLatch(count);
      final AtomicInteger ran = new AtomicInteger();

      for(int i = 0; i < count; i++) {
         Thread thread = new Thread(() -> {
            try {
               start.await();

               if(swapper.doGC(true)) {
                  ran.incrementAndGet();
               }
            }
            catch(InterruptedException ignore) {
            }
            finally {
               done.countDown();
            }
         });
         thread.setDaemon(true);
         thread.start();
      }

      start.countDown();
      assertTrue(done.await(10, java.util.concurrent.TimeUnit.SECONDS));
      assertEquals(1, ran.get(), "concurrent callers stacked collections");
      assertEquals(5, gcs.get());
   }
}
