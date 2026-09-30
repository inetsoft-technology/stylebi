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

import inetsoft.util.script.graal.ScriptTimeoutGuard;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The opt-in paranoid check (bug #77123, reliability task 3): {@link PoolParanoia#verify}
 * reports every way a cleaned global can differ from its baseline without changing it, it is
 * off by default and then never runs, and when on, a release whose clean left the global off
 * its baseline closes the slot.
 */
@Tag("core")
class PoolParanoiaTest {
   static {
      // a paranoid suite run reports the node-wide count when its JVM exits, since only
      // every 100th violation is logged; one comes from this class's planted defect
      if(Boolean.getBoolean(PoolParanoia.PROPERTY)) {
         Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // the file is appended across runs and forks: the pid and the JVM's start time
            // tell a run's line from an older one
            String line = "[pool-paranoia] pid=" + ProcessHandle.current().pid() +
               " jvmStart=" + java.time.Instant.ofEpochMilli(
                  java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime()) +
               " violations=" + PoolParanoia.violations() +
               " inconclusive=" + PoolParanoia.inconclusive() + " verifies=" +
               PoolParanoia.VERIFIES.get();
            System.out.println(line);

            // surefire may have closed the fork's stdout by now: also append it to a file in
            // the working directory (core/target/test-workdir)
            try {
               java.nio.file.Files.writeString(
                  java.nio.file.Path.of("pool-paranoia-summary.txt"),
                  java.time.Instant.now() + " " + line + "\n",
                  java.nio.file.StandardOpenOption.CREATE,
                  java.nio.file.StandardOpenOption.APPEND);
            }
            catch(Exception ignore) {
               // the printed line is the report
            }
         }));
      }
   }

   @BeforeEach
   void remember() {
      forcedBefore = PoolParanoia.forced;
   }

   @AfterEach
   void restore() {
      PoolParanoia.forced = forcedBefore;
      PoolParanoia.refresh();

      if(slot != null && !slot.isClosed()) {
         slot.close();
         slot.unlock();
      }
   }

   @Test
   void disabledByDefault() {
      Assumptions.assumeFalse(Boolean.getBoolean(PoolParanoia.PROPERTY), "suite run paranoid");
      PoolParanoia.forced = null;
      PoolParanoia.refresh();
      assertFalse(PoolParanoia.enabled());
   }

   /**
    * A suite run with {@code -Dscript.ws.contextPool.paranoid=true} really is paranoid: the
    * JVM property reaches the check, which SreeEnv alone would not.
    */
   @Test
   void jvmPropertyTurnsItOn() {
      Assumptions.assumeTrue(Boolean.getBoolean(PoolParanoia.PROPERTY), "suite run not paranoid");
      PoolParanoia.forced = null;
      PoolParanoia.refresh();
      assertTrue(PoolParanoia.enabled());
   }

   @Test
   void cleanedGlobalVerifiesEmpty() throws Exception {
      slot = newSlot();
      slot.applyOwn("hostVar", "h");
      run("var v1 = 1; function f1() {} leak = 2; globalThis[Symbol('s')] = 1; " +
          "delete globalThis.parseInt; JSON = 1; hostVar = 3; " +
          "Object.defineProperty(globalThis, 'u', {value: undefined}); 1");
      assertTrue(slot.clean().reusable(256));
      assertEquals(List.of(), verify(), "leftovers holding undefined are not reported");
   }

   @Test
   void keysChangedAfterTheCleanAreReported() throws Exception {
      slot = newSlot();
      assertTrue(slot.clean().reusable(256));
      assertEquals(List.of(), verify());

      // planted defects: a script run after the clean, as if the clean had missed them
      run("zqa = 1; parseInt = 5; delete globalThis.isNaN; globalThis[Symbol.for('zs')] = 1; " +
          "Object.defineProperty(globalThis, 'nc', {value: 2, writable: true}); 1");
      List<String> keys = verify();
      assertEquals(Set.of("extra:zqa", "changed:parseInt", "missing:isNaN",
                          "extra:Symbol(zs)", "extra:nc"), new HashSet<>(keys), keys::toString);
   }

   @Test
   void globalPrototypeAndExtensibilityAreReported() throws Exception {
      slot = newSlot();
      run("Object.setPrototypeOf(globalThis, {p: 1}); 1");
      assertTrue(verify().contains("<prototype>"), verify()::toString);
      slot.close();
      slot.unlock();

      slot = newSlot();
      run("Object.preventExtensions(globalThis); 1");
      assertTrue(verify().contains("<non-extensible>"), verify()::toString);
   }

   @Test
   void verifyChangesNothing() throws Exception {
      slot = newSlot();
      run("zqa = 7; parseInt = 5; 1");
      List<String> first = verify();
      assertEquals(first, verify());
      assertEquals(7.0, run("zqa"));
      assertEquals(5.0, run("parseInt"));
   }

   @Test
   void releaseNeverVerifiesWhenOff() throws Exception {
      PoolParanoia.forced = false;
      WorksheetScriptEnv env = env();
      long before = PoolParanoia.VERIFIES.get();

      for(int i = 0; i < 20; i++) {
         PoolTestSupport.run(env, "leak" + i + " = " + i + "; var v" + i + " = 1; " + i);
      }

      assertEquals(before, PoolParanoia.VERIFIES.get());
      assertEquals(1, env.getMetrics().getCreations());
   }

   @Test
   void releaseVerifiesAndKeepsACleanSlotWhenOn() throws Exception {
      PoolParanoia.forced = true;
      WorksheetScriptEnv env = env();
      long verifies = PoolParanoia.VERIFIES.get();
      long violations = PoolParanoia.violations();

      for(int i = 0; i < 20; i++) {
         PoolTestSupport.run(env, "leak" + i + " = " + i + "; var v" + i + " = 1; delete globalThis.isNaN; " + i);
      }

      // each run is two claims, the compile's and the exec's
      assertEquals(40, PoolParanoia.VERIFIES.get() - verifies);
      assertEquals(violations, PoolParanoia.violations());
      assertEquals(1, env.getMetrics().getCreations(), "a clean slot is kept");
   }

   /**
    * Round 2, #5885: the clean's baseline key list is a dense array with a null prototype, so
    * a script that gives Array.prototype a trapping prototype sees nothing when host vars are
    * put: no trap runs in the expect, the clean or the check, the new host vars stay in the
    * baseline, and the check finds the kept slot at its baseline.
    */
   @Test
   void baselineKeysOfAPutReachNoArrayPrototypeTrap() throws Exception {
      PoolParanoia.forced = true;
      WorksheetScriptEnv env = env();
      PoolTestSupport.Probe probe = new PoolTestSupport.Probe();
      env.put("zqprobe", probe);
      PoolTestSupport.run(env, "1");
      long violations = PoolParanoia.violations();
      // an index read or any write that misses an array's own elements now reaches a trap
      PoolTestSupport.run(env, "Object.setPrototypeOf(Array.prototype, new Proxy(" +
         "Object.prototype, {get(t, k, r) { if(typeof k === 'string' && k >= '0' && " +
         "k <= '9~') zqprobe.hit(); return Reflect.get(t, k, r); }, " +
         "set(t, k, v, r) { zqprobe.hit(); return Reflect.set(t, k, v, r); }})); 1");
      int hits = probe.hits();

      for(int i = 0; i < 5; i++) {
         env.put("zqnew" + i, "x" + i);
      }

      assertEquals("x4", PoolTestSupport.run(env, "zqnew4"));
      assertEquals("x0", PoolTestSupport.run(env, "zqnew0"));
      assertEquals(hits, probe.hits(), "no trap ran");
      assertEquals(violations, PoolParanoia.violations());
      assertEquals(1, env.getMetrics().getCreations(), "the slot is kept");
   }

   /**
    * A key planted after the clean, right before the release's check (as if the clean had
    * missed it), closes the slot when the check is on.
    */
   @Test
   void releaseClosesASlotTheCleanLeftOffItsBaselineWhenOn() throws Exception {
      WorksheetScriptEnv env = env();
      PoolTestSupport.run(env, "1");
      PoolParanoia.forced = true;
      long violations = PoolParanoia.violations();
      PoolParanoia.beforeVerifyHook = s -> {
         PoolParanoia.beforeVerifyHook = null;
         s.engine().context().eval("js", "globalThis.zqp = 1");
      };

      try {
         PoolTestSupport.run(env, "1");
      }
      finally {
         PoolParanoia.beforeVerifyHook = null;
      }

      assertEquals(violations + 1, PoolParanoia.violations());
      assertEquals("undefined", PoolTestSupport.run(env, "typeof zqp"));
      assertEquals(2, env.getMetrics().getCreations(), "the slot was closed and replaced");
   }

   /**
    * FZ1: the clean itself puts a replaced prototype back, so the check agrees and keeps the
    * slot.
    */
   @Test
   void cleanAndCheckAgreeOnARestoredPrototype() throws Exception {
      WorksheetScriptEnv env = env();
      PoolTestSupport.run(env, "1");
      PoolParanoia.forced = true;
      long violations = PoolParanoia.violations();
      PoolTestSupport.run(env, "Object.setPrototypeOf(globalThis, {zqp: 1}); 1");
      assertEquals(violations, PoolParanoia.violations());
      assertEquals("undefined", PoolTestSupport.run(env, "typeof zqp"));
      assertEquals(1, env.getMetrics().getCreations());
   }

   /**
    * Finding S1 (soak): a check stopped by its own time bound says nothing about the global,
    * so it is counted as inconclusive, not as a violation, and the slot is still closed.
    */
   @Test
   void verifyStoppedByItsTimeoutIsInconclusiveAndClosesTheSlot() throws Exception {
      WorksheetScriptEnv env = env();
      PoolTestSupport.run(env, "1");
      PoolParanoia.forced = true;
      long violations = PoolParanoia.violations();
      long inconclusive = PoolParanoia.inconclusive();
      int[] calls = {0};
      // a run is two claims (compile, exec): only the first release's check gets the 1 ms bound
      PoolParanoia.beforeVerifyHook = s -> {
         if(calls[0]++ == 0) {
            // enough extra keys that the check takes far longer than its 1 ms bound
            s.engine().context().eval("js",
               "for(let i = 0; i < 50000; i++) globalThis['zqi' + i] = i;");
            PoolParanoia.verifyTimeout = Duration.ofMillis(1);
         }
         else {
            PoolParanoia.verifyTimeout = CleanHelper.TIMEOUT;
         }
      };

      try {
         PoolTestSupport.run(env, "1");
      }
      finally {
         PoolParanoia.beforeVerifyHook = null;
         PoolParanoia.verifyTimeout = CleanHelper.TIMEOUT;
      }

      assertEquals(2, calls[0]);
      assertEquals(violations, PoolParanoia.violations(), "not a violation");
      assertEquals(inconclusive + 1, PoolParanoia.inconclusive());
      assertEquals("undefined", PoolTestSupport.run(env, "typeof zqi0"));
      assertEquals(2, env.getMetrics().getCreations(), "the slot was closed and replaced");
   }

   /**
    * S1: only an interrupt or cancel of the check is inconclusive; a guest error is not.
    */
   @Test
   void onlyAStoppedCheckIsInconclusive() {
      try(Context context = Context.create("js")) {
         PolyglotException interrupted;

         try(ScriptTimeoutGuard.Guard guard =
                new ScriptTimeoutGuard().guard(context, Duration.ofMillis(50)))
         {
            interrupted = assertThrows(PolyglotException.class,
                                       () -> context.eval("js", "while(true) {}"));
         }

         assertTrue(interrupted.isInterrupted(), String.valueOf(interrupted));
         assertTrue(PoolParanoia.inconclusive(interrupted));

         PolyglotException thrown = assertThrows(PolyglotException.class,
                                                 () -> context.eval("js", "throw new Error('x')"));
         assertFalse(PoolParanoia.inconclusive(thrown));
         assertFalse(PoolParanoia.inconclusive(new IllegalStateException("x")));
      }
   }

   private List<String> verify() {
      return PoolParanoia.verify(slot.engine().context(), slot.cleaner());
   }

   private static Slot newSlot() throws Exception {
      return Slot.create(new InitSnapshot("org0", Map.of()), new EnvState().snapshot(), 0L,
                         false, Collections.synchronizedMap(new WeakHashMap<>()),
                         new PoolMetrics());
   }

   private Object run(String js) throws Exception {
      WsEngine engine = slot.engine();
      return engine.exec(engine.compile(js), null, null);
   }

   private Slot slot;
   private Boolean forcedBefore;
}
