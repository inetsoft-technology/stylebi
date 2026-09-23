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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spec gate G7 and §4.4/§14.1 (bug #76960): after each claim's clean the next claim on that
 * context sees the baseline, or the context is closed; the clean never runs a getter or setter.
 * Each case uses a fresh slot, held by the test thread as a claim would hold it.
 */
@Tag("core")
class SlotCleanTest {
   @AfterEach
   void closeSlot() {
      if(slot != null && !slot.isClosed()) {
         slot.close();
         slot.unlock();
      }
   }

   @Test
   void expressionRedefiningALibraryFunctionIsRestored() throws Exception {
      slot = newSlot(Map.of("libAdd", "function libAdd(a){ return a + 1; }"));
      run("libAdd = function(){ return 0; }; 1");
      assertReusable(slot.clean());
      assertEquals(2.0, run("libAdd(1)"));
   }

   @Test
   void assignedCalcIsRestored() throws Exception {
      slot = newSlot(Map.of());
      run("CALC = 0; 1");
      assertReusable(slot.clean());
      assertEquals("object", run("typeof CALC"));
   }

   @Test
   void libraryFunctionWritingAnUndeclaredGlobalIsCleanedEveryClaim() throws Exception {
      slot = newSlot(Map.of("libWrites", "function libWrites(){ varDay = 7; return 1; }"));

      for(int claim = 0; claim < 3; claim++) {
         assertEquals("undefined", run("typeof varDay"));
         run("libWrites()");
         assertEquals(7.0, run("varDay"));
         assertReusable(slot.clean());
      }

      assertEquals("undefined", run("typeof varDay"));
   }

   @Test
   void libraryFunctionOverwritingCalcIsRestored() throws Exception {
      slot = newSlot(Map.of("libCalc", "function libCalc(){ CALC = 0; return 1; }"));
      run("libCalc()");
      assertReusable(slot.clean());
      assertEquals("object", run("typeof CALC"));
   }

   @Test
   void evalFunctionAndAssignWritesAreRemoved() throws Exception {
      slot = newSlot(Map.of());
      run("eval('x = 1'); Function('y = 1')(); Object.assign(globalThis, {z: 1}); 1");
      CleanHelper.Result result = slot.clean();
      assertReusable(result);
      assertEquals(3, result.removed());
      assertEquals("undefinedundefinedundefined", run("typeof x + typeof y + typeof z"));
   }

   @Test
   void nonConfigurableNonWritableGlobalClosesTheSlot() throws Exception {
      slot = newSlot(Map.of());
      run("Object.defineProperty(globalThis, 'w', {value: 1}); 1");
      CleanHelper.Result result = slot.clean();
      assertTrue(result.failed());
      assertFalse(result.reusable(256));
   }

   @Test
   void configurableThrowingAccessorIsDeletedWithoutRunningIt() throws Exception {
      slot = newSlot(Map.of());
      run("Object.defineProperty(globalThis, 'g', {get(){ throw 1; }, set(v){ throw 2; }, " +
          "configurable: true}); 1");
      CleanHelper.Result result = slot.clean();
      assertReusable(result);
      assertEquals(1, result.removed());
      assertEquals("undefined", run("typeof g"));
   }

   @Test
   void nonConfigurableAccessorClosesTheSlot() throws Exception {
      slot = newSlot(Map.of());
      run("Object.defineProperty(globalThis, 'a', {get(){ throw 1; }}); 1");
      assertTrue(slot.clean().failed());
   }

   @Test
   void frozenGlobalClosesTheSlot() throws Exception {
      slot = newSlot(Map.of());
      run("Object.freeze(globalThis); 1");
      assertTrue(slot.clean().failed());
   }

   @Test
   void patchedBuiltinsDoNotSubvertTheClean() throws Exception {
      slot = newSlot(Map.of());
      run("Map.prototype.get = function(){ return 'evil'; }; " +
          "Object.getOwnPropertyNames = function(){ return []; }; " +
          "Object.defineProperty = function(){ throw new Error('patched'); }; " +
          "leak = 1; isNull = 5; 1");
      assertReusable(slot.clean());
      assertEquals("undefined", run("typeof leak"));
      assertEquals("function", run("typeof isNull"));
   }

   @Test
   void compiledLexicalsNeverPoisonTheSlot() throws Exception {
      slot = newSlot(Map.of());
      assertEquals(1.0, run("let L4 = 1; L4"));
      assertReusable(slot.clean());
      assertEquals(5.0, run("let L4 = 5; L4"));
   }

   @Test
   void moreThan32ConfigurableForeignKeysCloseTheSlot() throws Exception {
      slot = newSlot(Map.of());
      run("for(var i = 0; i < 40; i++) { globalThis['f' + i] = i; } 1");
      CleanHelper.Result result = slot.clean();
      assertTrue(result.tooMany());
      assertFalse(result.reusable(256));
      assertEquals(0, result.removed(), "a closing slot is not cleaned key by key");
   }

   @Test
   void topLevelVarIsResetAndCountedAsLeftover() throws Exception {
      slot = newSlot(Map.of());
      run("var v3 = 5;");
      CleanHelper.Result result = slot.clean();
      assertReusable(result);
      assertEquals(1, result.leftovers());
      assertEquals("undefined", run("typeof v3"));
      assertFalse(result.reusable(0), "leftovers above the threshold close the slot");
   }

   @Test
   void deletedBaselineKeyIsRestored() throws Exception {
      slot = newSlot(Map.of());
      run("delete globalThis.formatDate; 1");
      assertReusable(slot.clean());
      assertEquals("function", run("typeof formatDate"));
   }

   @Test
   void symbolKeysAreRemoved() throws Exception {
      slot = newSlot(Map.of());
      run("globalThis[Symbol.for('x')] = 1; globalThis[Symbol('y')] = 2; 1");
      CleanHelper.Result result = slot.clean();
      assertReusable(result);
      assertEquals(2, result.removed());
      assertEquals(false, run("Symbol.for('x') in globalThis"));
   }

   @Test
   void loopingGetterNeverRunsAndCleanIsFast() throws Exception {
      slot = newSlot(Map.of());
      run("Object.defineProperty(globalThis, 'spin', {get(){ while(true) {} }, " +
          "configurable: true}); 1");
      long start = System.nanoTime();
      assertReusable(slot.clean());
      assertTrue(System.nanoTime() - start < 1_000_000_000L);
   }

   @Test
   void replayedVarsAreExpectedAndTombstonesForgotten() throws Exception {
      EnvState state = new EnvState();
      state.put("k", "v");
      slot = Slot.create(new InitSnapshot("org0", Map.of()), state.snapshot(), 0L, false,
                         Collections.synchronizedMap(new WeakHashMap<>()), new PoolMetrics());
      assertEquals("v", run("k"));

      long from = slot.version();
      state.put("k2", new StringBuilder("host"));
      state.remove("k");
      EnvState.Snapshot now = state.snapshot();
      slot.replay(now.after(from), now.version());
      assertEquals(now.version(), slot.version());

      CleanHelper.Result result = slot.clean();
      assertReusable(result);
      assertEquals(0, result.restored(), "a replayed value is recorded after its own toGuest");
      assertEquals("undefined", run("typeof k"), "a tombstone is not restored by clean");
      assertEquals("host", run("String(k2)"));
   }

   @Test
   void ownPutIsVisibleAndSurvivesClean() throws Exception {
      slot = newSlot(Map.of());
      slot.applyOwn("p", 3);
      assertEquals(3.0, run("p"));
      assertReusable(slot.clean());
      assertEquals(3.0, run("p"));
      slot.removeOwn("p");
      assertReusable(slot.clean());
      assertEquals("undefined", run("typeof p"));
   }

   private static Slot newSlot(Map<String, String> library) throws Exception {
      EnvState state = new EnvState();
      return Slot.create(new InitSnapshot("org0", library), state.snapshot(), 0L, false,
                         Collections.synchronizedMap(new WeakHashMap<>()), new PoolMetrics());
   }

   private Object run(String js) throws Exception {
      WsEngine engine = slot.engine();
      return engine.exec(engine.compile(js), null, null);
   }

   private static void assertReusable(CleanHelper.Result result) {
      assertFalse(result.failed(), "clean failed: " + result);
      assertFalse(result.tooMany(), "too many foreign keys: " + result);
      assertTrue(result.reusable(256), String.valueOf(result));
   }

   private Slot slot;
}
