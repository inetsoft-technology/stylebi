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
package inetsoft.util.script.graal;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81930: the {@code this} of a plain-called function is the scope the script runs on, not
 * the global object. A fresh engine per test: a helper that wrote to the global object leaked
 * onto the context of every later script.
 */
@Tag("core")
class PlainFunctionThisTest {
   static class MapScope implements ScriptScope {
      final Map<String, Object> m = new LinkedHashMap<>();
      public Object getMember(String n) { return m.get(n); }
      public boolean hasMember(String n) { return m.containsKey(n); }
      public void putMember(String n, Object v) { m.put(n, v); }
      public Object[] getMemberKeys() { return m.keySet().toArray(); }
   }

   private GraalJavaScriptEngine engine;

   @BeforeEach void setup() throws Exception {
      engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
   }

   @AfterEach void teardown() { engine.close(); }

   private MapScope scope() {
      MapScope scope = new MapScope();
      scope.putMember("visible", true);
      scope.putMember("row", 3.0);
      return scope;
   }

   private Object run(String script, MapScope scope) throws Exception {
      return engine.exec(engine.compile(script), scope, scope);
   }

   private Object run(String script) throws Exception {
      return run(script, scope());
   }

   private double num(String script) throws Exception {
      return ((Number) run(script)).doubleValue();
   }

   // ---- closed by the fix: a lost write, an undefined read ----

   @Test void plainFunctionWriteReachesScope() throws Exception {
      MapScope scope = scope();
      run("function h(){ this.visible = false; } h();", scope);
      assertEquals(false, scope.getMember("visible"));
   }

   @Test void plainFunctionReadSeesScopeMember() throws Exception {
      assertEquals("boolean", run("function g(){ return typeof this.visible; } g()"));
   }

   @Test void iifeAndNestedFunctionsSeeScope() throws Exception {
      assertEquals("boolean", run("(function(){ return typeof this.visible; })()"));
      assertEquals("boolean",
         run("function outer(){ function inner(){ return typeof this.visible; } " +
             "return inner(); } outer()"));
   }

   @Test void arrowInsidePlainFunctionSeesScope() throws Exception {
      assertEquals("boolean",
         run("function g(){ return (() => typeof this.visible)(); } g()"));
   }

   @Test void withThisInsidePlainFunctionResolvesScopeNames() throws Exception {
      assertEquals(3.0, num("function g(){ with(this){ return row; } } g()"));
   }

   @Test void helperWriteDoesNotLeakToGlobalOfOtherScope() throws Exception {
      run("function h(){ this.leaked81930 = 1; } h();");
      assertEquals("undefined", run("typeof leaked81930", scope()));
   }

   @Test void freehandFormulaPathSeesRowScope() throws Exception {
      MapScope scope = scope();
      Object src = engine.compileCalleeFallback("(function(){ return this.row; })()");
      assertEquals(3.0, ((Number) engine.exec(src, scope, scope)).doubleValue());
   }

   @Test void declaredVarsPathSeesScope() throws Exception {
      MapScope scope = scope();
      Object src = engine.compileDeclaredVars("(function(){ return this.row; })()");
      assertEquals(3.0, ((Number) engine.exec(src, scope, scope)).doubleValue());
   }

   // ---- receivers that must be kept (regression guards, pass before and after) ----

   @Test void objectMethodKeepsItsReceiver() throws Exception {
      assertEquals(7.0, num("var o = {v:7, m:function(){ return this.v; }}; o.m()"));
      assertEquals(7.0, num("var o = {v:7, m(){ return this.v; }}; o.m()"));
   }

   @Test void getterKeepsItsReceiver() throws Exception {
      assertEquals(7.0, num("var o = {v:7, get g(){ return this.v; }}; o.g"));
   }

   @Test void constructorKeepsNewObject() throws Exception {
      assertEquals(1.0, num("function F(){ this.a = 1; } new F().a"));
      assertEquals("undefined", run("function F(){ this.a = 1; } new F(); typeof this.a"));
   }

   @Test void callApplyBindKeepReceiver() throws Exception {
      assertEquals(5.0, num("function k(){ return this.q; } var t={q:5}; k.call(t)"));
      assertEquals(5.0, num("function k(){ return this.q; } var t={q:5}; k.apply(t, [])"));
      assertEquals(5.0, num("function k(){ return this.q; } var t={q:5}; k.bind(t)()"));
   }

   @Test void forEachThisArgKeepsReceiver() throws Exception {
      assertEquals(5.0,
         num("var r; [1].forEach(function(){ r = this.q; }, {q:5}); r"));
   }

   @Test void classMethodKeepsReceiverAndStaysStrict() throws Exception {
      assertEquals(4.0, num(
         "class C { constructor(){ this.v = 4; } m(){ return this.v; } } new C().m()"));
      // a detached class method is strict: this is undefined, not the scope
      assertEquals("undefined",
         run("class C { m(){ return typeof this; } } var f = new C().m; f()"));
   }

   @Test void functionWithOwnStrictDirectiveStaysStrict() throws Exception {
      assertEquals("undefined",
         run("(function(){ 'use strict'; return typeof this; })()"));
   }

   @Test void topLevelThisIsStillTheScope() throws Exception {
      MapScope scope = scope();
      run("this.visible = false;", scope);
      assertEquals(false, scope.getMember("visible"));
      assertEquals("boolean", run("(() => typeof this.visible)()"));
   }

   @Test void indirectFunctionReturnThisIsTheRealGlobal() throws Exception {
      assertEquals(true, run("Function('return this')() === globalThis"));
      assertEquals(true,
         run("(function(){ return Function('return this')() === globalThis; })()"));
      // a this in the same plain function does not change what the indirect one returns
      assertEquals(true, run(
         "(function(){ var t = this; return Function('return this')() === globalThis; })()"));
   }

   @Test void scriptWithoutThisIsUnchanged() {
      String src = "function f(){ return 1; } f()";
      assertSame(src, PlainFunctionThisRewriter.rewrite(src));
   }

   @Test void unparsableScriptIsUnchanged() {
      String src = "function ( this {{";
      assertSame(src, PlainFunctionThisRewriter.rewrite(src));
   }

   // ---- the global-object idiom: builtins read off the stand-in keep working ----

   @Test void globalObjectIdiomStillFindsBuiltins() throws Exception {
      assertEquals("object|object|function|function|function", run(
         "(function(){ var g = this; return typeof g.Math + '|' + typeof g.JSON + '|' + " +
         "typeof g.parseInt + '|' + typeof g.Date + '|' + typeof g.Array; })()"));
      assertEquals(2.0, num("(function(){ return this.eval('1+1'); })()"));
      assertEquals(3.0, num("(function(){ return this.Math.max(1, 3); })()"));
   }

   @Test void scopeMemberWinsOverBuiltinOfTheSameName() throws Exception {
      MapScope scope = scope();
      scope.putMember("Date", "shadow");
      assertEquals("shadow", run("(function(){ return this.Date; })()", scope));
   }

   // ---- a function defined in one scope and called from another ----

   // Documented behavior: this is resolved per call, from the scope of the script that calls
   // the function (Rhino bound it to the defining scope).
   @Test void functionCalledFromAnotherScopeGetsTheCallersScope() throws Exception {
      MapScope a = new MapScope();
      a.putMember("who", "a");
      MapScope b = new MapScope();
      b.putMember("who", "b");
      run("function whoAmI(){ return this.who; }", a);
      assertEquals("a", run("whoAmI()", a));
      assertEquals("b", run("whoAmI()", b));
   }
}
