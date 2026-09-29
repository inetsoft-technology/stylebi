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
         Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println(
            "[pool-paranoia] violations=" + PoolParanoia.violations() + " verifies=" +
            PoolParanoia.VERIFIES.get())));
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
    * The clean restores key descriptors but not the global's prototype, so this release
    * keeps the slot unless the paranoid check closes it.
    */
   @Test
   void releaseClosesASlotTheCleanLeftOffItsBaselineWhenOn() throws Exception {
      WorksheetScriptEnv env = env();
      PoolTestSupport.run(env, "1");
      PoolParanoia.forced = true;
      long violations = PoolParanoia.violations();

      PoolTestSupport.run(env, "Object.setPrototypeOf(globalThis, {zqp: 1}); 1");
      assertEquals(violations + 1, PoolParanoia.violations());
      assertEquals("undefined", PoolTestSupport.run(env, "typeof zqp"));
      assertEquals(2, env.getMetrics().getCreations(), "the slot was closed and replaced");
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
