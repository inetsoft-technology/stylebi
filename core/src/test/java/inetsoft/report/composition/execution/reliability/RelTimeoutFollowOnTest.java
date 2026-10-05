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
package inetsoft.report.composition.execution.reliability;

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.PoolMetrics;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What a worker thread runs after a script timeout (Testing #77123, round 2 R1 / PR #5935).
 * When the timeout's interrupt cannot stop an exec within ctx.interrupt's 2 s bound (the exec
 * sits in a host call that ignores interrupts), Graal leaves the thread's interrupt flag set.
 * Before #5935 the thread's next pooled FTL, in another sandbox, then failed a cell with
 * ExpressionFailedException ("Failed to create a worksheet script context: Thread was
 * interrupted"), and with the pool off the thread's next wait failed. Each case runs on a
 * dedicated worker thread, pool on and off: after the timed-out FTL, the same thread's next
 * FTL (a new sandbox, R1's {@code field['day']} with batches of 256 rows, random reads) must
 * equal the pool-off oracle, and a wait must not fail. A genuine cancel (an interrupt of the
 * thread from the script, from the caller before the run, or from another thread during it)
 * must not be lost: the flag is still set when the run returns.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("slow")
class RelTimeoutFollowOnTest {
   /** A host object the formulas call; its spin ignores interrupts, as a busy host call does. */
   public static final class Spinner {
      public int spin(int ms) {
         long end = System.nanoTime() + ms * 1_000_000L;

         while(System.nanoTime() - end < 0) {
            Thread.onSpinWait();
         }

         spins.incrementAndGet();
         return 1;
      }

      /** Interrupt the calling thread, as a cancel from elsewhere would. */
      public void cancel() {
         Thread.currentThread().interrupt();
      }

      final AtomicInteger spins = new AtomicInteger();
   }

   @BeforeAll
   static void oneSecondTimeout() throws Exception {
      oracle = RelPipeline.run(SCRIPT, Shape.FTL, RelConfig.off(), ReadPattern.SEQUENTIAL);
      previous = SreeEnv.getProperty("script.execution.timeout");
      SreeEnv.setProperty("script.execution.timeout", "1");
      refresh();
      worker = Executors.newSingleThreadExecutor(r -> {
         Thread thread = new Thread(r, "rel-timeout-follow-on");
         thread.setDaemon(true);
         return thread;
      });
   }

   @AfterAll
   static void restore() throws Exception {
      worker.shutdownNow();
      SreeEnv.setProperty("script.execution.timeout", previous);
      refresh();
   }

   @BeforeEach
   void clearFlag() throws Exception {
      onWorker(() -> Thread.interrupted());
   }

   @ParameterizedTest(name = "pool {0}")
   @ValueSource(booleans = { true, false })
   void theNextFtlOnTheThreadIsRightAfterAnInterruptTimeout(boolean pool) throws Exception {
      onWorker(() -> {
         long timeouts = PoolMetrics.nodeInterruptTimeouts();
         Spinner spinner = new Spinner();
         // interrupt at ~1 s, ctx.interrupt gives up at ~3 s, the host call returns at 6 s
         runSpinner(pool, spinner, "sp.spin(6000)");
         assertEquals(1, spinner.spins.get(), "the host call did not run to its end");

         if(pool) {
            assertEquals(1, PoolMetrics.nodeInterruptTimeouts() - timeouts,
                         "the interrupt must have timed out");
         }

         boolean left = Thread.currentThread().isInterrupted();
         String next = outcome(() -> followOn(pool));
         assertFalse(left, "the timed-out interrupt left the thread interrupted; the next FTL " +
                     "on the thread gave: " + next);
         assertEquals("ok", next, "the next FTL on the thread");
         // any wait of the thread, as a lens read, a lock or JDBC does
         Thread.sleep(1);
         assertEquals("ok", outcome(() -> followOn(pool)), "the FTL after that");
         return null;
      });
   }

   /**
    * As above, but the next FTL runs in a sandbox that was already open and had run, so its
    * context exists and is reused rather than created (a long-lived sandbox, or a later item of
    * a short-lived one).
    */
   @ParameterizedTest(name = "pool {0}")
   @ValueSource(booleans = { true, false })
   void theNextFtlInAnOpenSandboxIsRightAfterAnInterruptTimeout(boolean pool) throws Exception {
      onWorker(() -> {
         AssetQuerySandbox open = RelPipeline.sandbox(followOnConfig(pool));

         try {
            assertEquals("ok", outcome(() -> followOn(open)), "the open sandbox's first run");
            Spinner spinner = new Spinner();
            runSpinner(pool, spinner, "sp.spin(6000)");
            assertEquals(1, spinner.spins.get(), "the host call did not run to its end");
            boolean left = Thread.currentThread().isInterrupted();
            String next = outcome(() -> followOn(open));
            assertFalse(left, "the timed-out interrupt left the thread interrupted; the next " +
                        "FTL on the thread gave: " + next);
            assertEquals("ok", next, "the next FTL on the thread");
         }
         finally {
            open.dispose();
         }

         return null;
      });
   }

   @ParameterizedTest(name = "pool {0}")
   @ValueSource(booleans = { true, false })
   void aCancelInTheTimedOutFormulaIsKept(boolean pool) throws Exception {
      onWorker(() -> {
         Spinner spinner = new Spinner();
         runSpinner(pool, spinner, "sp.cancel(); sp.spin(6000)");
         assertEquals(1, spinner.spins.get(), "the host call did not run to its end");
         assertTrue(Thread.interrupted(), "the cancel's interrupt was lost");
         // the cancel is the caller's to handle; once it is, the thread works as before
         assertEquals(oracle, followOn(pool), "the next FTL after the cancel was handled");
         return null;
      });
   }

   @Test
   void aCancelBeforeThePooledRunIsObserved() throws Exception {
      onWorker(() -> {
         Thread.currentThread().interrupt();
         String outcome = outcome(() -> followOn(true));
         assertCancelObserved(outcome);
         Thread.interrupted();
         assertEquals(oracle, followOn(true), "the next FTL after the cancel was handled");
         return null;
      });
   }

   /**
    * Finding R2-R1-F1 (pool independent, the base engine), fixed by #5963: with the pool off,
    * a thread interrupted before its sandbox creates the env lost the interrupt in the env's
    * init, where GraalJavaScriptEngine.installHostGlobals caught Graal's "Thread was
    * interrupted." and only logged it (which also left that engine without its host global
    * names, bug #77181); the run then computed every row as if there had been no cancel.
    */
   @Test
   void aCancelBeforeThePoolOffRunIsObserved() throws Exception {
      onWorker(() -> {
         Thread.currentThread().interrupt();
         assertCancelObserved(outcome(() -> followOn(false)));
         return null;
      });
   }

   @Test
   void aCancelDuringThePoolOffRunIsObserved() throws Exception {
      cancelDuringTheRun(false);
   }

   /**
    * Finding R2-R1-F2 (pool on only), fixed by #5963: a cancel that lands during a pooled
    * batch after the batch's last guest safepoint reaches the batch-end clean, where Graal
    * raises "Thread was interrupted." (clearing the flag); Slot.clean() logged it at debug
    * level and discarded the context, so the run's rows were all computed and the cancel was
    * gone. With the pool off the flag is kept.
    */
   @Test
   void aCancelDuringThePooledRunIsObserved() throws Exception {
      cancelDuringTheRun(true);
   }

   private static void cancelDuringTheRun(boolean pool) throws Exception {
      onWorker(() -> {
         Thread self = Thread.currentThread();
         Spinner spinner = new Spinner();
         // no timeout: the host call ends at 400 ms; another thread cancels at ~150 ms. The
         // sandbox and its first context are made first, so the cancel lands in the formula
         AssetQuerySandbox box = RelPipeline.sandbox(pool ? RelConfig.on() : RelConfig.off());
         ScheduledExecutorService canceller = Executors.newSingleThreadScheduledExecutor();
         String outcome;

         try {
            box.getScriptEnv().put("sp", spinner);
            String warm = spinnerFtl(box, "1");
            assertTrue(warm.startsWith("ok "), "the sandbox's first run: " + warm);
            canceller.schedule(self::interrupt, 150, TimeUnit.MILLISECONDS);
            outcome = spinnerFtl(box, "sp.spin(400)");
         }
         finally {
            canceller.shutdownNow();
            box.dispose();
         }

         assertEquals(1, spinner.spins.get(), "the host call did not run to its end");
         assertCancelObserved(outcome);
         Thread.interrupted();
         assertEquals(oracle, followOn(pool), "the next FTL after the cancel was handled");
         return null;
      });
   }

   /**
    * A cancel is observed when the run fails on it or the thread is still interrupted when the
    * run returns. Graal turns an interrupt of a thread in guest code into "Thread was
    * interrupted." and clears the flag, so a failed run may leave the flag clear.
    */
   private static void assertCancelObserved(String outcome) {
      boolean flag = Thread.currentThread().isInterrupted();
      boolean failed = outcome.contains("nterrupt");
      assertTrue(flag || failed, "the cancel was lost: the run gave " + outcome +
                 " and the thread is no longer interrupted");
   }

   /**
    * @return "ok" if the run gave the oracle, else the first differing cell; then the messages
    * of the errors the run met, including one the harness read past (see RelPipeline.probe).
    */
   private static String outcome(Callable<List<String>> run) {
      List<String> errors = new ArrayList<>();
      RelPipeline.MESSAGES.set(errors);

      try {
         List<String> cells = run.call();
         return (oracle.equals(cells) ? "ok" : "cells " + RelMetamorphicTest.diff(oracle, cells)) +
            (errors.isEmpty() ? "" : ", errors " + errors);
      }
      catch(Exception ex) {
         return "threw " + ex;
      }
      finally {
         RelPipeline.MESSAGES.remove();
      }
   }

   /**
    * Run {@code formula} as a one-row FTL in a new sandbox that knows {@code sp}.
    *
    * @return see {@link #spinnerFtl}.
    */
   private static String runSpinner(boolean pool, Spinner spinner, String formula) {
      AssetQuerySandbox box = RelPipeline.sandbox(pool ? RelConfig.on() : RelConfig.off());

      try {
         box.getScriptEnv().put("sp", spinner);
         return spinnerFtl(box, formula);
      }
      finally {
         box.dispose();
      }
   }

   /**
    * @return "ok" and the value of the one-row FTL of {@code formula}, or "failed" and the
    * row's exception.
    */
   private static String spinnerFtl(AssetQuerySandbox box, String formula) {
      TableLens base = new DefaultTableLens(new Object[][] { { "id" }, { 1 } });
      FormulaTableLens lens = new FormulaTableLens(base, new String[] { RelPipeline.COLUMN },
         new String[] { formula }, box.getScriptEnv(), box.getScope());

      try {
         lens.moreRows(1);
         return "ok " + RelPipeline.str(lens.getObject(1, 1));
      }
      catch(RuntimeException ex) {
         return "failed " + ex;
      }
   }

   /**
    * R1's shape: {@code field['day']} as an FTL in a new sandbox, batches of 256 rows (pool
    * on), random reads, so a batch starts inside the table.
    */
   private static List<String> followOn(boolean pool) throws Exception {
      return RelPipeline.run(SCRIPT, Shape.FTL, followOnConfig(pool), ReadPattern.random(SEED));
   }

   private static List<String> followOn(AssetQuerySandbox box) throws Exception {
      return RelPipeline.run(SCRIPT, Shape.FTL, box, ReadPattern.random(SEED));
   }

   private static RelConfig followOnConfig(boolean pool) {
      return pool ? RelConfig.on().with(PoolConfig.BATCH_ROWS, "256")
         .with(PoolConfig.MAX_BATCH_ROWS, "256") : RelConfig.off();
   }

   private static <T> T onWorker(Callable<T> task) throws Exception {
      try {
         return worker.submit(task).get(2, TimeUnit.MINUTES);
      }
      catch(ExecutionException ex) {
         if(ex.getCause() instanceof Error error) {
            throw error;
         }

         throw ex.getCause() instanceof Exception cause ? cause : ex;
      }
   }

   private static void refresh() throws Exception {
      Field field = GraalJavaScriptEngine.class.getDeclaredField("TIMEOUT_PROP");
      field.setAccessible(true);
      ((SreeEnv.Value) field.get(null)).updateValue();
   }

   private static final String SCRIPT = "field['day']";
   // R1's read order
   private static final long SEED = 5478721568033599388L;
   private static List<String> oracle;
   private static String previous;
   private static ExecutorService worker;
}
