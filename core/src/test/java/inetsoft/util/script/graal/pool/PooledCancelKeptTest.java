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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testing #77123 (CX2): a caller's cancel (an interrupt of the thread) is never lost in pooled
 * code that runs JS outside a script. At its next guest safepoint Graal turns a set interrupt
 * flag into "Thread was interrupted." and clears the flag, so a catch that swallows that
 * exception swallows the cancel. The flag must still be set when the pooled call returns,
 * whether the call failed on it or not; an interrupt of the pool's own timeout is not a cancel
 * and must not be left on the thread.
 */
@Tag("core")
class PooledCancelKeptTest {
   @BeforeEach
   @AfterEach
   void clearFlag() {
      Thread.interrupted();
   }

   @AfterEach
   void closeSlot() {
      if(slot != null && !slot.isClosed()) {
         slot.close();
         slot.unlock();
      }
   }

   /**
    * A cancel that landed during a batch after its last guest safepoint reaches the batch-end
    * clean, whose JS loops over the globals.
    */
   @Test
   void theCleanKeepsACancelThatLandedBeforeIt() throws Exception {
      slot = newSlot();
      run("var q = 1; 1");
      Thread.currentThread().interrupt();
      CleanHelper.Result result = slot.clean();
      assertTrue(Thread.interrupted(), "the clean lost the cancel (result " + result + ")");
   }

   /**
    * The clean's own timeout interrupt stops it: that interrupt is the pool's, so the thread is
    * not left interrupted (Testing #77123, #5935). A characterisation of #5935 rather than of
    * this fix (it passes before it too); whether a clean outlasts the watchdog's tick depends
    * on the machine, so it is skipped when none did.
    */
   @Test
   void aCleanStoppedByItsOwnTimeoutLeavesNoFlag() throws Exception {
      slot = newSlot();
      StringBuilder js = new StringBuilder();

      for(int i = 0; i < 5_000; i++) {
         js.append("var v").append(i).append(" = ").append(i).append(";\n");
      }

      run(js.append("1").toString());
      slot.cleanTimeout = Duration.ofNanos(1);

      for(int attempt = 0; attempt < 20; attempt++) {
         CleanHelper.Result result = slot.clean();
         assertFalse(Thread.interrupted(), "the clean's own timeout left the thread interrupted");

         if(result.failed()) {
            return;
         }
      }

      Assumptions.abort("no clean outlasted the watchdog's tick on this machine");
   }

   @Test
   void aContextCreationKeepsACancel() throws Exception {
      Thread.currentThread().interrupt();

      try {
         slot = newSlot();
      }
      catch(Exception ex) {
         // the creation may fail on the cancel; the cancel must still be kept
      }

      assertTrue(Thread.interrupted(), "the context creation lost the cancel");
   }

   /**
    * The pooled env's first script, whose checkout creates the context, on a cancelled thread;
    * it may fail on the cancel, as at compile time of a formula table.
    */
   @Test
   void theFirstPooledScriptKeepsACancel() {
      WorksheetScriptEnv env = PoolTestSupport.env();

      try {
         Thread.currentThread().interrupt();
         String outcome;

         try {
            outcome = String.valueOf(PoolTestSupport.run(env, "1 + 1"));
         }
         catch(Exception ex) {
            outcome = String.valueOf(ex);
         }

         assertTrue(Thread.interrupted(), "the first pooled script lost the cancel: " + outcome);
      }
      finally {
         env.retire();
      }
   }

   /**
    * A pooled script with no guest safepoint on a cancelled thread: the script itself does not
    * see the flag, and the clean at its claim's release must not consume it.
    */
   @Test
   void aPooledScriptKeepsACancelThroughItsRelease() throws Exception {
      WorksheetScriptEnv env = PoolTestSupport.env();

      try {
         assertEquals(2, ((Number) PoolTestSupport.run(env, "1 + 1")).intValue());
         PoolTestSupport.run(env, "var w = 1; 1");
         Thread.currentThread().interrupt();
         String outcome;

         // whether the script itself polls the flag is up to Graal; either way the cancel is kept
         try {
            outcome = String.valueOf(PoolTestSupport.run(env, "var u = 2; 3"));
         }
         catch(Exception ex) {
            outcome = String.valueOf(ex);
         }

         assertTrue(Thread.interrupted(), "the release of the claim lost the cancel: " + outcome);
         // once the cancel is handled, the env works as before
         assertEquals("undefined", PoolTestSupport.run(env, "typeof u"));
      }
      finally {
         env.retire();
      }
   }

   /**
    * A host object whose call signals entry, then stays out of guest code until the calling
    * thread is interrupted (at most 10 s), so a cancel lands after the script's last guest
    * safepoint, deterministically.
    */
   public static final class Spinner {
      public int spin() {
         entered.countDown();
         long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);

         while(!Thread.currentThread().isInterrupted() && System.nanoTime() - end < 0) {
            Thread.onSpinWait();
         }

         return 1;
      }

      final CountDownLatch entered = new CountDownLatch(1);
   }

   /**
    * CX2 as it happens in a run: another thread cancels the pooled exec while it is in a host
    * call past its last guest safepoint. The script completes, and the cancel must still be
    * set on the thread after the claim's release (whose clean runs JS).
    */
   @Test
   void aCancelDuringAPooledScriptIsKept() throws Exception {
      WorksheetScriptEnv env = PoolTestSupport.env();
      Spinner spinner = new Spinner();
      Thread self = Thread.currentThread();
      Thread canceller = new Thread(() -> {
         try {
            if(spinner.entered.await(30, TimeUnit.SECONDS)) {
               self.interrupt();
            }
         }
         catch(InterruptedException ignore) {
            // the test ends
         }
      }, "pooled-cancel-canceller");

      try {
         env.put("sp", spinner);
         PoolTestSupport.run(env, "var w = 1; 1");
         canceller.start();
         String outcome;

         // whether the return into guest code polls the flag is up to Graal; either way the
         // cancel is kept
         try {
            outcome = String.valueOf(PoolTestSupport.run(env, "var u = 2; sp.spin()"));
         }
         catch(Exception ex) {
            outcome = String.valueOf(ex);
         }

         canceller.join(30_000);
         assertEquals(0, spinner.entered.getCount(), "the host call did not run");
         assertTrue(Thread.interrupted(), "the cancel was lost: " + outcome);
         assertEquals("undefined", PoolTestSupport.run(env, "typeof u"));
      }
      finally {
         canceller.interrupt();
         env.retire();
      }
   }

   private static Slot newSlot() throws Exception {
      EnvState state = new EnvState();
      return Slot.create(new InitSnapshot("org0", Map.of()), state.snapshot(), 0L, false,
                         Collections.synchronizedMap(new WeakHashMap<>()), new PoolMetrics());
   }

   private Object run(String js) throws Exception {
      WsEngine engine = slot.engine();
      return engine.exec(engine.compile(js), null, null);
   }

   private Slot slot;
}
