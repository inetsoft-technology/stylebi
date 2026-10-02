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
package inetsoft.util.script.graal.pool;

import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77568: the paranoid check keeps a caller's cancel on every way out of the check, not
 * only on a normal verdict: when the check fails with a guest error and when its own timeout
 * stops it. A release with no cancel still gets a verdict, keeps the slot and leaves no
 * interrupt behind.
 */
@Tag("core")
class ParanoidCheckFlagTest {
   @BeforeEach
   void remember() {
      Thread.interrupted();
      forcedBefore = PoolParanoia.forced;
   }

   @AfterEach
   void restore() {
      Thread.interrupted();
      PoolParanoia.forced = forcedBefore;
      PoolParanoia.beforeVerifyHook = null;
      PoolParanoia.verifyTimeout = CleanHelper.TIMEOUT;
      PoolParanoia.refresh();

      if(slot != null && !slot.isClosed()) {
         slot.close();
         slot.unlock();
      }
   }

   @Test
   void aFailedCheckKeepsACancel() throws Exception {
      slot = newSlot();
      assertTrue(slot.clean().reusable(256));
      Slot dead = slot;
      // the check of a closed context fails, which is no interrupt of the check
      dead.engine().context().close();
      Thread.currentThread().interrupt();
      List<String> keys = PoolParanoia.verify(dead.engine().context(), dead.cleaner());
      assertTrue(Thread.interrupted(), "the failed check lost the cancel: " + keys);
      assertEquals(1, keys.size(), keys::toString);
      assertTrue(keys.get(0).startsWith("<verify failed: "), keys::toString);
   }

   @Test
   void aCheckStoppedByItsTimeoutKeepsACancel() throws Exception {
      slot = newSlot();
      assertTrue(slot.clean().reusable(256));
      stallCheck(slot);
      PoolParanoia.verifyTimeout = Duration.ofMillis(1);
      Thread.currentThread().interrupt();
      List<String> keys = PoolParanoia.verify(slot.engine().context(), slot.cleaner());
      assertTrue(Thread.interrupted(), "the timed-out check lost the cancel: " + keys);
      assertEquals(1, keys.size(), keys::toString);
      assertTrue(keys.get(0).startsWith(PoolParanoia.INCONCLUSIVE_PREFIX), keys::toString);
   }

   @Test
   void aCheckStoppedByItsTimeoutLeavesNoInterrupt() throws Exception {
      slot = newSlot();
      assertTrue(slot.clean().reusable(256));
      stallCheck(slot);
      PoolParanoia.verifyTimeout = Duration.ofMillis(1);
      List<String> keys = PoolParanoia.verify(slot.engine().context(), slot.cleaner());
      assertFalse(Thread.interrupted(), "the check's own timeout was left on the thread");
      assertEquals(1, keys.size(), keys::toString);
      assertTrue(keys.get(0).startsWith(PoolParanoia.INCONCLUSIVE_PREFIX), keys::toString);
   }

   @Test
   void anUncancelledParanoidReleaseIsVerifiedAndReused() throws Exception {
      PoolParanoia.forced = true;
      WorksheetScriptEnv env = PoolTestSupport.env();

      try {
         PoolTestSupport.run(env, "1");
         long verifies = PoolParanoia.VERIFIES.get();
         long inconclusive = PoolParanoia.inconclusive();
         long violations = PoolParanoia.violations();

         for(int i = 0; i < 5; i++) {
            PoolTestSupport.run(env, "var zqr" + i + " = 1; " + i);
            assertFalse(Thread.currentThread().isInterrupted(), "a release left an interrupt");
         }

         assertEquals(10, PoolParanoia.VERIFIES.get() - verifies, "each claim is checked");
         assertEquals(inconclusive, PoolParanoia.inconclusive());
         assertEquals(violations, PoolParanoia.violations());
         assertEquals(1, env.getMetrics().getCreations(), "the slot is reused");
      }
      finally {
         env.retire();
      }
   }

   /**
    * Make the slot's check run guest code for 30 s before the real check, so that only the
    * check's own timeout ends it in time, however fast the machine runs the real check.
    */
   private static void stallCheck(Slot slot) throws Exception {
      PoolTestSupport.injectVerify(slot, "(function(real) { return function() { " +
         "const end = Date.now() + 30000; while(Date.now() < end) {} return real(); }; })");
   }

   private static Slot newSlot() throws Exception {
      return Slot.create(new InitSnapshot("org0", Map.of()), new EnvState().snapshot(), 0L,
                         false, Collections.synchronizedMap(new WeakHashMap<>()),
                         new PoolMetrics());
   }

   private Slot slot;
   private Boolean forcedBefore;
}
