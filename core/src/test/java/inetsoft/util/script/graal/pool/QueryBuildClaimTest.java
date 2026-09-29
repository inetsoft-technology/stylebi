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

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.util.script.ScriptSpan;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.concurrent.*;

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * G10 piece Q (Testing #77123): the scripts of one query build share one lazily claimed
 * context ({@link SlotClaim#openBuild()}), with the four amendments of the refute: a context
 * an interrupt could not stop is left at the build's next top-level script (1); a variable
 * another thread sets during the build is seen by its next script (2); the clean's cap of
 * {@value PoolConfig#MAX_FOREIGN_DELETES} deleted implicit globals applies per build (3); a
 * reset during a build ends it on its context (4). Plus a timeout during a build.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class QueryBuildClaimTest {
   @BeforeEach
   void setUp() {
      env = env();
      executor = Executors.newSingleThreadExecutor(r -> {
         Thread t = new Thread(r);
         t.setDaemon(true);
         return t;
      });
   }

   @AfterEach
   void tearDown() {
      executor.shutdownNow();
      assertEquals(0, SlotClaim.openClaims(), "a claim was left open");
   }

   @Test
   void allScriptsOfABuildShareOneClaimAndOneClean() throws Exception {
      try(SlotClaim.Build ignored = SlotClaim.openBuild()) {
         for(int i = 0; i < 20; i++) {
            assertEquals((double) i + 1, run(env, i + " + 1"));
            env.checkFunction("f" + i, "function f" + i + "() { return 1; }");

            // a formula table batch and a nested build join the build's claim
            try(ScriptSpan span = env.openSpan(); SlotClaim.Build nested = SlotClaim.openBuild()) {
               assertEquals(2.0, run(env, "1 + 1"));
            }
         }

         assertEquals(0, env.getMetrics().getCleans(), "cleaned before the build ended");
      }

      assertEquals(1, env.getMetrics().getCheckouts());
      assertEquals(1, env.getMetrics().getCleans());
   }

   @Test
   void aBuildThatRunsNoScriptTakesNoContext() {
      try(SlotClaim.Build ignored = SlotClaim.openBuild();
          ScriptSpan span = env.openSpan())
      {
         env.put("v", 1);
      }

      assertEquals(0, env.getMetrics().getCheckouts());
      assertEquals(0, env.getMetrics().getSize());
   }

   @Test
   void withoutABuildEveryScriptTakesItsOwnClaim() throws Exception {
      for(int i = 0; i < 5; i++) {
         // a compile and an exec, each its own claim
         run(env, "1");
      }

      assertEquals(10, env.getMetrics().getCheckouts());
   }

   @Test
   void aGlobalOfOneBuildIsNotSeenByTheNext() throws Exception {
      try(SlotClaim.Build ignored = SlotClaim.openBuild()) {
         run(env, "g = 5");
         // the build's scripts share their globals, as all scripts do with the pool off
         assertEquals(5, ((Number) run(env, "g")).intValue());
      }

      try(SlotClaim.Build ignored = SlotClaim.openBuild()) {
         assertEquals("undefined", run(env, "typeof g"));
      }

      assertEquals(1, env.getMetrics().getCreations(), "the context was not reused");
   }

   /**
    * Amendment 1: an interrupt that could not stop a script leaves the context unknown; the
    * build's next top-level script runs on a fresh one, and the old one is closed.
    */
   @Test
   void aContextAnInterruptCouldNotStopIsLeftAtTheNextTopLevelScript() throws Exception {
      Slot first;

      try(SlotClaim.Build ignored = SlotClaim.openBuild()) {
         run(env, "x = 1");
         first = SlotClaim.current(env.pool()).peekSlot();
         first.engine().onInterruptTimeout();
         assertEquals("undefined", run(env, "typeof x"));
         Slot second = SlotClaim.current(env.pool()).peekSlot();
         assertNotSame(first, second);
         assertTrue(first.isClosed());
         assertEquals(2.0, run(env, "1 + 1"));
         assertSame(second, SlotClaim.current(env.pool()).peekSlot());
      }

      assertEquals(1, env.getMetrics().getSwaps());
      assertEquals(1, env.getMetrics().getDoomedCloses());
      assertEquals(2, env.getMetrics().getCheckouts());
      assertEquals(1, env.getMetrics().getSize());
   }

   /**
    * Amendment 1: inside a span of the build (a formula table batch) the span's scripts go on
    * on the context, as on a batch claim; the next top-level script leaves it.
    */
   @Test
   void insideASpanTheContextGoesOnUntilTheSpanEnds() throws Exception {
      Slot first;

      try(SlotClaim.Build ignored = SlotClaim.openBuild()) {
         try(ScriptSpan span = env.openSpan()) {
            run(env, "y = 1");
            first = SlotClaim.current(env.pool()).peekSlot();
            first.engine().onInterruptTimeout();
            assertEquals(1, ((Number) run(env, "y")).intValue());
            assertSame(first, SlotClaim.current(env.pool()).peekSlot());
         }

         assertFalse(first.isClosed());
         run(env, "1");
         assertNotSame(first, SlotClaim.current(env.pool()).peekSlot());
         assertTrue(first.isClosed());
      }

      assertEquals(1, env.getMetrics().getSwaps());
   }

   /**
    * Amendment 1: without a later top-level script the build's release closes the context.
    */
   @Test
   void aContextAnInterruptCouldNotStopIsClosedAtTheBuildEnd() throws Exception {
      Slot first;

      try(SlotClaim.Build ignored = SlotClaim.openBuild()) {
         run(env, "1");
         first = SlotClaim.current(env.pool()).peekSlot();
         first.engine().onInterruptTimeout();
      }

      assertTrue(first.isClosed());
      assertEquals(0, env.getMetrics().getSwaps());
      assertEquals(1, env.getMetrics().getDoomedCloses());
      assertEquals(0, env.getMetrics().getCleans(), "a doomed context is not cleaned");
   }

   /**
    * Amendment 2: a variable another thread sets during the build is seen by the build's
    * next script, as by a claim per script and with the pool off; a removal too.
    */
   @Test
   void aVariableAnotherThreadSetsIsSeenByTheNextScript() throws Exception {
      env.put("r", 1);

      try(SlotClaim.Build ignored = SlotClaim.openBuild()) {
         assertEquals("undefined", run(env, "typeof v"));
         executor.submit(() -> env.put("v", 7)).get(10, TimeUnit.SECONDS);
         assertEquals(7, ((Number) run(env, "v")).intValue());

         try(ScriptSpan span = env.openSpan()) {
            executor.submit(() -> env.put("v", 8)).get(10, TimeUnit.SECONDS);
            assertEquals(8, ((Number) run(env, "v")).intValue());
         }

         executor.submit(() -> env.remove("r")).get(10, TimeUnit.SECONDS);
         assertEquals("undefined", run(env, "typeof r"));
         // this thread's own put is applied at once
         env.put("w", 3);
         assertEquals(3, ((Number) run(env, "w")).intValue());
      }

      assertEquals(1, env.getMetrics().getCheckouts());
   }

   /**
    * Amendment 3: the clean's cap of deleted implicit globals applies to the whole build,
    * whose scripts' implicit globals all fall on its one clean: within the cap the context is
    * reused, above it the context is closed instead of cleaned. Either way the next build
    * sees none of them.
    */
   @Test
   void theCapOfDeletedImplicitGlobalsAppliesPerBuild() throws Exception {
      int cap = PoolConfig.MAX_FOREIGN_DELETES;
      buildWithGlobals(cap);
      assertEquals(1, env.getMetrics().getCreations(), "a build within the cap was closed");
      assertNoGlobals(cap + 1);
      assertEquals(1, env.getMetrics().getCreations());

      buildWithGlobals(cap + 1);
      assertNoGlobals(cap + 1);
      assertEquals(2, env.getMetrics().getCreations(), "a build above the cap was reused");
   }

   private void buildWithGlobals(int n) throws Exception {
      try(SlotClaim.Build ignored = SlotClaim.openBuild()) {
         for(int i = 0; i < n; i++) {
            run(env, "implicit" + i + " = " + i);
         }
      }
   }

   private void assertNoGlobals(int n) throws Exception {
      try(SlotClaim.Build ignored = SlotClaim.openBuild()) {
         for(int i = 0; i < n; i++) {
            assertEquals("undefined", run(env, "typeof implicit" + i));
         }
      }
   }

   /**
    * Amendment 4: a reset of the env during a build (on its thread or another) does not
    * swap the build's context: the build ends on it, and its release closes it; the next
    * build runs on a fresh one.
    */
   @Test
   void aResetDuringABuildEndsTheBuildOnItsContext() throws Exception {
      for(boolean elsewhere : new boolean[] { false, true }) {
         WorksheetScriptEnv env = env();
         Slot first;

         try(SlotClaim.Build ignored = SlotClaim.openBuild()) {
            run(env, "h = 1");
            first = SlotClaim.current(env.pool()).peekSlot();

            if(elsewhere) {
               executor.submit(env::reset).get(10, TimeUnit.SECONDS);
            }
            else {
               env.reset();
            }

            assertEquals(1, ((Number) run(env, "h")).intValue());
            assertSame(first, SlotClaim.current(env.pool()).peekSlot());
            assertFalse(first.isClosed());
         }

         assertTrue(first.isClosed(), "the retired context was kept");
         assertEquals(0, env.getMetrics().getSwaps());

         try(SlotClaim.Build ignored = SlotClaim.openBuild()) {
            assertEquals("undefined", run(env, "typeof h"));
            assertNotSame(first, SlotClaim.current(env.pool()).peekSlot());
         }
      }
   }

   /**
    * A script that times out during a build fails alone: the interrupt stopped it, so the
    * build's context stays and its next script runs on it, and the context is reused after.
    */
   @Test
   void aTimeoutDuringABuildFailsOnlyItsScript() throws Exception {
      String previous = SreeEnv.getProperty("script.execution.timeout");
      SreeEnv.setProperty("script.execution.timeout", "1");
      refreshTimeout();

      try {
         Slot first;

         try(SlotClaim.Build ignored = SlotClaim.openBuild()) {
            run(env, "timedVar = 1");
            first = SlotClaim.current(env.pool()).peekSlot();
            long start = System.nanoTime();
            assertThrows(Exception.class, () -> run(env, "while(true) {}"));
            assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(30));
            assertFalse(first.isDoomed());
            assertEquals(1, ((Number) run(env, "timedVar")).intValue());
            assertSame(first, SlotClaim.current(env.pool()).peekSlot());
         }

         assertFalse(first.isClosed());
         assertEquals(1, env.getMetrics().getCheckouts());

         try(SlotClaim.Build ignored = SlotClaim.openBuild()) {
            assertEquals("undefined", run(env, "typeof timedVar"));
         }

         assertEquals(1, env.getMetrics().getCreations());
      }
      finally {
         if(previous == null) {
            SreeEnv.remove("script.execution.timeout");
         }
         else {
            SreeEnv.setProperty("script.execution.timeout", previous);
         }

         refreshTimeout();
      }
   }

   @Test
   void aThrowOutOfABuildReleasesItsClaim() throws Exception {
      assertThrows(IllegalStateException.class, () -> {
         try(SlotClaim.Build ignored = SlotClaim.openBuild()) {
            run(env, "1");
            throw new IllegalStateException("query failed");
         }
      });

      assertEquals(0, SlotClaim.openClaims());
      assertEquals(1, env.getMetrics().getCleans());
   }

   @Test
   void aLeakedBuildIsEndedWithTheThreadsClaims() throws Exception {
      Future<Integer> task = executor.submit(() -> {
         SlotClaim.openBuild();
         run(env, "1");
         SlotClaim.releaseLeaked("test");
         // no build is left open: this script's claim is its own
         run(env, "2");
         return SlotClaim.openClaims();
      });

      assertEquals(0, task.get(10, TimeUnit.SECONDS));
      // the leaked claim's context was released, so the next script reused it
      assertEquals(1, env.getMetrics().getSize(), "a context was left locked");
   }

   private static void refreshTimeout() throws Exception {
      Field field = GraalJavaScriptEngine.class.getDeclaredField("TIMEOUT_PROP");
      field.setAccessible(true);
      ((SreeEnv.Value) field.get(null)).updateValue();
   }

   private WorksheetScriptEnv env;
   private ExecutorService executor;
}
