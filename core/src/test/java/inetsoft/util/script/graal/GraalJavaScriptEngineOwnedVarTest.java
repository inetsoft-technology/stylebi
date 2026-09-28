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
package inetsoft.util.script.graal;

import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The names a scope owns for its scripts (Testing #77123): the top-level {@code var}s of a
 * formula table's formulas, and how the engine treats them (no declaration hoist).
 */
@Tag("core")
class GraalJavaScriptEngineOwnedVarTest {
   @Test
   void topLevelVarsAreOwned() {
      assertEquals(Set.of("a", "b", "c"), owned("var a = 1; var b, c = f(1, 2);"));
      assertEquals(Set.of("d", "i"), owned("if(x) { var d = 1; } for(var i = 0; i < 3; i++) {}"));
      assertEquals(Set.of("acc"), owned("var acc = (acc || 0) + field['value']; acc"));
   }

   @Test
   void varsOfNestedFunctionsAreNotOwned() {
      assertEquals(Set.of(), owned("function f() { var e = 1; return e; }"));
      assertEquals(Set.of("g"), owned("var g = function() { var h; return h; };"));
      assertEquals(Set.of("k"), owned("var k = () => { var m = 1; return m; };"));
      assertEquals(Set.of("n", "p"), owned("var n = x => x + 1; var p = n(1);"));
      assertEquals(Set.of("f2", "q"), owned("if(a) { var f2 = function(){ var z; }; } var q;"));
   }

   @Test
   void letConstStringsCommentsAndMembersAreNotOwned() {
      assertEquals(Set.of(), owned("let m = 1; const n = 2; m + n"));
      assertEquals(Set.of(), owned("'var s'; \"var t\"; `var ${u}`; // var v\n /* var w */ 1"));
      assertEquals(Set.of(), owned("o.var = 1; /var z/.test(s)"));
   }

   @Test
   void aNameDeclaredWithLetOrConstInAnyFormulaIsNotOwned() {
      assertEquals(Set.of(), GraalJavaScriptEngine.collectOwnedVarNames(
         List.of("let x; x", "var x = 1; x")));
      assertEquals(Set.of("y"), GraalJavaScriptEngine.collectOwnedVarNames(
         Arrays.asList("var x = 1; var y = 2;", null, "const x = 3; x")));
      // a block-level let is block scoped and does not collide
      assertEquals(Set.of("x"), GraalJavaScriptEngine.collectOwnedVarNames(
         List.of("var x = 1; x", "if(a) { let x = 2; } 1")));
   }

   @Test
   void anOwnedVarIsStoredInTheOwnerAndNotHoisted() throws Exception {
      GraalJavaScriptEngine engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());

      try {
         // the eval path (this) and the multi-statement path both run in a wrapper function
         // whose declarations the #75596 hoist copies to the global scope
         String[] scripts = {
            "var p1Own = (p1Own || 0) + 1; this; p1Own",
            "var p1Own2 = (p1Own2 || 0) + 1; if(p1Own2 < 0) { p1Own2 = 0; } p1Own2"
         };

         for(String script : scripts) {
            String name = script.substring(4, script.indexOf(' ', 4));
            Owner owner = new Owner(Set.of(name));
            Object compiled = engine.compile(script);

            for(int i = 1; i <= 3; i++) {
               assertEquals(i, ((Number) engine.exec(compiled, owner, null)).intValue(),
                            script + " run " + i);
            }

            assertEquals(3.0, ((Number) owner.vars.get(name)).doubleValue());
            assertEquals("undefined", engine.exec(engine.compile("typeof " + name), null, null),
                         "hoisted: " + script);
         }

         // an unowned var of the same shape is still hoisted (#75596)
         Object compiled = engine.compile("var p1Free = 7; this; p1Free");
         engine.exec(compiled, new Owner(Set.of()), null);
         assertEquals(7.0, ((Number) engine.exec(engine.compile("p1Free"), null, null))
            .doubleValue());
      }
      finally {
         engine.close();
      }
   }

   /**
    * An owned var that is unset or holds undefined reads as undefined, not null, on every
    * compile path (review B-1 of PR #5806); an explicit null stays null.
    */
   @Test
   void anUnsetOrUndefinedOwnedVarReadsAsUndefined() throws Exception {
      GraalJavaScriptEngine engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());

      try {
         String[] prefixes = { "", "this; ", "if(1 < 0) { throw 'x'; } " };

         for(String prefix : prefixes) {
            Owner owner = new Owner(Set.of("p1Un", "p1Nu"));
            Object init = engine.compile(
               prefix + "var p1Un = (typeof p1Un == 'undefined') ? 100 : p1Un + 1; p1Un");

            for(int i = 0; i < 3; i++) {
               assertEquals(100 + i, ((Number) engine.exec(init, owner, null)).intValue(),
                            prefix + "run " + i);
            }

            owner = new Owner(Set.of("p1Un", "p1Nu"));
            assertEquals("undefined:true", engine.exec(engine.compile(
               prefix + "var p1Un; typeof p1Un + ':' + (p1Un === undefined)"), owner, null));
            assertEquals("undefined:true", engine.exec(engine.compile(
               prefix + "var p1Un = ({}).y; typeof p1Un + ':' + (p1Un == null)"), owner, null));
            assertSame(OwnedVarScope.UNDEFINED, owner.vars.get("p1Un"), prefix);
            assertEquals("object:true", engine.exec(engine.compile(
               prefix + "var p1Nu = null; typeof p1Nu + ':' + (p1Nu === null)"), owner, null));
            assertTrue(owner.vars.containsKey("p1Nu"));
            assertNull(owner.vars.get("p1Nu"), prefix);
            assertEquals("object:true", engine.exec(engine.compile(
               prefix + "typeof p1Nu + ':' + (p1Nu === null)"), owner, null));
         }
      }
      finally {
         engine.close();
      }
   }

   private static Set<String> owned(String script) {
      return GraalJavaScriptEngine.collectOwnedVarNames(List.of(script));
   }

   private static final class Owner implements ScriptScope, OwnedVarScope {
      Owner(Set<String> owned) {
         this.owned = owned;
      }

      @Override
      public boolean ownsVar(String name) {
         return owned.contains(name);
      }

      @Override
      public void putOwnedVar(String name, Value value) {
         vars.put(name, ScriptValueConverter.toOwnedVar(value));
      }

      @Override
      public Object getMember(String name) {
         // as TableRowScope: an owned var not assigned yet reads as undefined
         return vars.containsKey(name) || !owned.contains(name) ? vars.get(name) : UNDEFINED;
      }

      @Override
      public boolean hasMember(String name) {
         return owned.contains(name) || vars.containsKey(name);
      }

      @Override
      public void putMember(String name, Object value) {
         vars.put(name, value);
      }

      @Override
      public Object[] getMemberKeys() {
         return vars.keySet().toArray();
      }

      private final Set<String> owned;
      private final Map<String, Object> vars = new HashMap<>();
   }
}
