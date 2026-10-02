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

import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A value of another context seen by a pooled worksheet script (bug #76960, spec §14.11, lead
 * decision (b)): it is marked foreign where it enters the context, stays a live reference to
 * its owner, and behaves in the script as main's re-viewed foreign value does.
 */
@Tag("core")
class WsForeignRefTest {
   private static final String[] OPS = {
      "o.a", "o.b.c", "'a' in o", "'zz' in o", "(function(){var k=[]; for(var p in o) k.push(p); return k.join()})()",
      "Object.keys(o).join()", "JSON.stringify(o.b)", "typeof o", "typeof o.b", "typeof o.f",
      "o.f(21)", "o.m()", "o.arr.length", "o.arr[1]",
      "(function(){var s=0; for(var x of o.arr) s+=x; return s})()",
      "(function(){var s=0; for(var i=0;i<o.arr.length;i++) s+=o.arr[i]; return s})()",
      "o === o", "o.b === o.b", "String(o.b)", "o.missing === undefined", "new o.K(3).v",
      "typeof o.K"
   };

   @Test
   void foreignObjectReadsLikeMain() throws Exception {
      Map<String, String> main = results(new GraalJavaScriptEnv());
      Map<String, String> pooled = results(env());
      List<String> diffs = new ArrayList<>();

      for(String op : OPS) {
         System.out.println("FOREIGN-PARITY " + op + " | main=" + main.get(op) + " | pooled=" +
                            pooled.get(op));

         if(!Objects.equals(main.get(op), pooled.get(op))) {
            diffs.add(op + ": main=" + main.get(op) + " pooled=" + pooled.get(op));
         }
      }

      assertEquals(List.of(), diffs);
   }

   /**
    * Writes go live to the owner, as on main in production (see
    * {@link #mainWriteToAForeignObjectReachesItsOwnerWithAssertionsOff}).
    */
   @Test
   void writesGoLiveToTheOwner() throws Exception {
      WorksheetScriptEnv env = env();
      Value o = (Value) owner();
      env.put("o", o);
      assertEquals(7.0, run(env, "o.a = 5; o.b.c = 7; o.arr[0] = 9; o.n = {x: 1}; o.n.x + 6"));
      assertEquals(5, o.getMember("a").asInt());
      assertEquals(7, o.getMember("b").getMember("c").asInt());
      assertEquals(9, o.getMember("arr").getArrayElement(0).asInt());
      assertEquals(1, o.getMember("n").getMember("x").asInt());
      assertEquals("true", String.valueOf(run(env, "String(o.b === o.b && o.arr === o.arr)")));
      assertEquals("true", String.valueOf(run(env, "delete o.a; String(!('a' in o))")));
   }

   /**
    * Records main's behaviour for the write comparison above. Without -ea (production) the
    * write reaches the owner; with -ea (the surefire default) Graal's own interop assertion
    * "unexpected interop primitive" (OtherContextGuestObject.migrateReturn) fails it.
    */
   @Test
   void mainWriteToAForeignObjectReachesItsOwnerWithAssertionsOff() throws Exception {
      ScriptEnv plain = plain();
      Value o = (Value) owner();
      plain.put("o", o);
      boolean assertions = false;
      assert assertions = true;

      try {
         run(plain, "o.a = 5; 1");
         assertEquals(5, o.getMember("a").asInt());
      }
      catch(Throwable ex) {
         assertTrue(assertions, "main's write failed with assertions off: " + ex);
      }
   }

   /**
    * The one deliberate difference (spec §14.12 A2, lead ruling (i)): on main a worksheet
    * function stored into a viewsheet object is kept live; pooled, the worksheet context is
    * cleaned and reused, so the function would dangle and the store is rejected (release note).
    * Passing one as a transient call argument still works.
    */
   @Test
   void storingAFunctionIntoAForeignObjectIsRejected() throws Exception {
      WorksheetScriptEnv ws = env();
      ws.put("o", owner());
      assertThrows(Exception.class, () -> run(ws, "o.g = function() { return 1; }; 1"));
      assertEquals(5.0, run(ws, "o.f.call(null, 2) + o.arr.filter(function(x) { return x > 2; }).length"));
   }

   @Test
   void foreignValuesAreCountedAndUnwrappedAtTheHostBoundary() throws Exception {
      WorksheetScriptEnv ws = env();
      Taker taker = new Taker();
      Value o = (Value) owner();
      ws.put("o", o);
      ws.put("taker", taker);
      long before = WsValueCopier.foreignValueCount();
      run(ws, "taker.take(o.b); taker.takeMap(o); 1");
      // counted once, where o was marked on entry (replayed into the claimed context)
      assertTrue(WsValueCopier.foreignValueCount() >= before + 1);
      assertEquals(1, ((Map<?, ?>) taker.last).get("a"), "a live map view, as on main");
      assertFalse(taker.last instanceof CopyMap);
      Object result = run(ws, "o.b");
      assertTrue(result instanceof Value value && value.getContext().equals(o.getContext()),
                 "an exec result of a foreign value is the owner's live value: " + result);
   }

   /**
    * Spec §14.12 A1 (lead ruling (ii)): toHost of a foreign array keeps main's Object[] shape,
    * walked at main's moment, so e.g. a ONE_OF condition value built from it is element-wise
    * (ConditionGroup / PreAssetQuery.getScriptValue expect Object[]); objects stay live.
    */
   @Test
   void toHostOfAForeignArrayIsMainsObjectArray() throws Exception {
      for(ScriptEnv env : new ScriptEnv[] { plain(), env() }) {
         env.put("o", owner());
         Object arr = run(env, "o.arr");
         assertTrue(arr instanceof Object[], env + ": " + arr);
         assertEquals(List.of(1.0, 2.0, 3.0), Arrays.asList((Object[]) arr));
         Object nested = run(env, "[o.arr, 4]");
         assertTrue(((Object[]) nested)[0] instanceof Object[], String.valueOf(env));
         Object obj = run(env, "o.b");
         assertTrue(obj instanceof Value, env + ": " + obj);
      }
   }

   /**
    * A foreign value nested in an own object or array passed to a Java method arrives as the
    * same Java type as on main (a live polyglot view), never as the ForeignRef proxy.
    */
   @Test
   void nestedForeignValueInAJavaArgumentIsMainsType() throws Exception {
      List<String> types = new ArrayList<>();

      for(ScriptEnv env : new ScriptEnv[] { plain(), env() }) {
         Taker taker = new Taker();
         env.put("o", owner());
         env.put("taker", taker);
         run(env, "taker.take({x: o.b}); 1");
         Object x = ((Map<?, ?>) taker.last).get("x");
         run(env, "taker.take([o.arr]); 1");
         Object element = ((List<?>) taker.last).get(0);
         assertFalse(x instanceof ForeignRef || element instanceof ForeignRef);
         assertTrue(x instanceof Map, String.valueOf(x));
         assertEquals(2, ((Map<?, ?>) x).get("c"));
         types.add(x.getClass().getName() + "|" + element.getClass().getName());
      }

      assertEquals(types.get(0), types.get(1), "pooled element types differ from main");
   }

   /**
    * A non-pooled exec nested in a pooled one (a host call that runs a viewsheet script) does
    * not see the worksheet context's marks, so its own values are never wrapped or copied.
    */
   @Test
   void nestedPlainExecIsNotMarked() throws Exception {
      GraalJavaScriptEnv vs = plain();
      Probe probe = new Probe();
      vs.put("probe", probe);
      WorksheetScriptEnv ws = env();
      ws.put("cb", new Callback(vs));
      ws.put("probe", probe);
      Object nested = run(ws, "probe.mark(); cb.nested('probe.mark(); ({a: 1})'); probe.mark(); 1");
      assertEquals(List.of(true, false, true), probe.marks);
      assertNull(WsExecContext.currentContext());
      assertEquals(1.0, nested);
   }

   public static final class Probe {
      public void mark() {
         marks.add(WsExecContext.currentContext() != null);
      }

      final List<Boolean> marks = new ArrayList<>();
   }

   private static GraalJavaScriptEnv plain() {
      GraalJavaScriptEnv env = new GraalJavaScriptEnv();
      env.init();
      return env;
   }

   private static Map<String, String> results(ScriptEnv env) throws Exception {
      if(env instanceof GraalJavaScriptEnv plain) {
         plain.init();
      }

      env.put("o", owner());
      Map<String, String> results = new LinkedHashMap<>();

      for(String op : OPS) {
         try {
            results.put(op, String.valueOf(run(env, "String(" + op + ")")));
         }
         catch(Exception ex) {
            results.put(op, "ERR " + ex.getClass().getSimpleName());
         }
      }

      return results;
   }

   private static Object owner() throws Exception {
      GraalJavaScriptEnv vs = new GraalJavaScriptEnv();
      vs.init();
      return run(vs, "({a: 1, b: {c: 2}, arr: [1, 2, 3], f: function(x) { return x * 2; }, " +
                     "m: function() { return this.a; }, K: function(v) { this.v = v; }})");
   }
}
