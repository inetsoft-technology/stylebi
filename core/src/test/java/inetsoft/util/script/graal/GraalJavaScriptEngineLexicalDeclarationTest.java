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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #76980: top-level {@code const}/{@code let} must behave like the Rhino
 * (language version 0) {@code const} — scoped like {@code var} — both across the
 * #75688 statement split and across scripts run on the same engine.
 */
@Tag("core")
class GraalJavaScriptEngineLexicalDeclarationTest {
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

   private Object run(String script) throws Exception {
      MapScope scope = new MapScope();
      return engine.exec(engine.compile(script), scope, scope);
   }

   private double num(String script) throws Exception {
      Object result = run(script);
      assertInstanceOf(Number.class, result, script + " -> " + result);
      return ((Number) result).doubleValue();
   }

   // the triage and refute repro rows (each a lexical declaration followed by a
   // #75688 piece boundary, with a later piece that uses the name)
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "let L4 = 1; L4                                          | 1",
      "const a = 1; const b = 2; a + b                         | 3",
      "let a = 5; if(a > 1) { a * 2 }                          | 10",
      "let s = 0; for(let i = 0; i < 3; i++) { s += i; } s     | 3",
      "let x = 3; function g(){ return x; } g()                | 3",
      "let w = 2; var v = 1; v + w                             | 3",
      "let a = 1; while(false) {} a                            | 1",
      "const c = 7; this.q = 1; if(c > 1) { c }                | 7",
      "let a = 1, b = 2; if(a) {} a + b                        | 3",
      "const k = 3;\\nk * 2                                    | 6",
   })
   void topLevelDeclarationVisibleAcrossSplit(String script, double expected) throws Exception {
      assertEquals(expected, num(script.replace("\\n", "\n")), script);
   }

   // a mixed let/const/class script: let/const survive, class stays confined to
   // its own piece (not rewritten), so only the let/const names are used later
   @Test void letAndConstBesideClass() throws Exception {
      assertEquals(3.0, num("let L2 = 1; const C2 = 2; class K2 {}; L2 + C2"));
   }

   // the #75688 cell-color shape written with const: the earlier array must be
   // the completion value, as Rhino returned it
   @Test void constArrayKeptAsCompletionBeforeEmptyIf() throws Exception {
      Object result = run("const hi = [237,211,237]; if(1 > 0) { hi } if(false) { 0 }");
      assertNotNull(result);
      assertEquals("237,211,237",
                   run("const hi2 = [237,211,237]; if(1 > 0) { hi2.join(',') } if(false) { 0 }"));
   }

   // previously this silently returned 0 (the catch branch), with no error
   @Test void tryCatchDoesNotSilentlySwallowConst() throws Exception {
      assertEquals(1.0, num("const m = {a:1}; try { m.a } catch(e) { 0 }"));
   }

   // an onInit-style top-level const must be visible to a later script on the
   // same engine, as Rhino kept it on the scope
   @Test void onInitConstPersistsToLaterScript() throws Exception {
      run("const PC = 5;");
      assertEquals(5.0, num("PC"));
      assertEquals(6.0, num("PC + 1"));
   }

   // same, through the `this` (eval + #75596 hoist) path
   @Test void onInitConstPersistsThroughThisPath() throws Exception {
      run("const PC2 = 5; this.z = 1;");
      assertEquals(5.0, num("PC2"));
   }

   @Test void onInitLetPersistsToLaterScript() throws Exception {
      run("let PL = 4; if(PL) {}");
      assertEquals(4.0, num("PL"));
   }

   // negative: nested let/const keep their block scoping
   @Test void blockScopedDeclarationsNotRewritten() throws Exception {
      assertEquals("undefined,undefined",
                   run("if(true) { let b1 = 1; const b2 = 2; } typeof b1 + ',' + typeof b2"));
      assertEquals("undefined", run("for(let i1 = 0; i1 < 2; i1++) {} typeof i1"));
      assertEquals("undefined", run("{ const b3 = 1; } typeof b3"));
      assertEquals("undefined", run("function f1() { const b4 = 1; return b4; } f1(); typeof b4"));
      // block-scoped closure capture per iteration still works
      assertEquals(3.0, num("var fs = []; for(let j = 0; j < 3; j++) { fs.push(function(){ return j; }); } " +
                               "fs[0]() + fs[1]() + fs[2]()"));
   }

   // negative: keywords inside strings, templates, regexes and comments untouched
   @Test void occurrencesInLiteralsAndCommentsNotRewritten() throws Exception {
      assertEquals("const x = 1", run("var s1 = 'const x = 1'; if(s1) {} s1"));
      assertEquals("let y = 2", run("var s2 = \"let y = 2\"; if(s2) {} s2"));
      assertEquals("const z", run("var s3 = `const z`; if(s3) {} s3"));
      assertEquals(true, run("var r = /const q/; if(r) {} r.test('const q')"));
      assertEquals(1.0, num("// const c1 = 1;\n/* let c2 = 2; */ var c3 = 1; if(c3) {} c3"));
   }

   // negative: `let` used as an identifier (sloppy mode) and as a property name
   @Test void letAsIdentifierNotRewritten() throws Exception {
      assertEquals(5.0, num("var let = 5; if(let) {} let"));
      assertEquals(2.0, num("var o = {let: 2, const: 3}; if(o) {} o.let"));
      assertEquals(3.0, num("var o2 = {let: 2, const: 3}; if(o2) {} o2.const"));
   }

   @Test void rewriteOnlyTouchesTopLevelDeclarationKeywords() throws Exception {
      assertEquals("var   a = 1; var b = 2; { let c = 3; } for(const d of []) {} 'const e'",
                   rewrite("const a = 1; let b = 2; { let c = 3; } for(const d of []) {} 'const e'"));
      assertEquals("// const x\nvar   y = /let/; `let ${1}`",
                   rewrite("// const x\nconst y = /let/; `let ${1}`"));
      assertEquals("x = let\ny = 1", rewrite("x = let\ny = 1"));
      assertEquals("if(c) let\ny = 1", rewrite("if(c) let\ny = 1"));
      assertEquals("let in o", rewrite("let in o"));
      assertEquals("class K {}", rewrite("class K {}"));
      assertEquals("var   {p, q} = o; var [r] = a", rewrite("const {p, q} = o; let [r] = a"));
   }

   // review r1: after `)` (and other division-ambiguous tokens) the lexer reads
   // `/` as division, so a regex literal's content looks like code; a `const`
   // or `let` there is not a declaration shape and must not be rewritten
   @Test void regexAfterDivisionAmbiguousTokenNotRewritten() throws Exception {
      assertEquals("if(x) /const/.test(s)", rewrite("if(x) /const/.test(s)"));
      assertEquals("if(x) /const x/.test(s)", rewrite("if(x) /const x/.test(s)"));
      assertEquals("if(x) /let y/.test(s)", rewrite("if(x) /let y/.test(s)"));
      assertEquals("while(x) /const [a]/.test(s)", rewrite("while(x) /const [a]/.test(s)"));
      assertEquals("a[0] /const/g", rewrite("a[0] /const/g"));
      assertEquals("n /const {b}/ 2", rewrite("n /const {b}/ 2"));

      MapScope scope = new MapScope();
      scope.putMember("s", "has const here");
      assertEquals(true, engine.exec(engine.compile(
         "var hit = false; if(1) /const/.test(s) && (hit = true); if(false) {} hit"), scope, scope));
      assertEquals(true, engine.exec(engine.compile(
         "var ok = false; if(1) /const here/.test(s) && (ok = true); if(false) {} ok"), scope, scope));
   }

   // round 4: the `)` closing an if/while/for/with head is followed by a
   // statement, so the lexer must read a following `/` as a regex — including
   // one whose text holds `; const`/`; let`, which no shape check can catch
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "if(x) /const/.test(s)",
      "if(x) /const [a-z]+/.test(s)",
      "if(x) /const x/.test(s)",
      "if(x) /; const y/.test(s)",
      "if(x) /; const y = 1/.test(s)",
      "while(x-- > 0) /const/.test(s)",
      "while(x-- > 0) /; const w/.test(s)",
      "if(x) /let/.test(s)",
      "if(x) /let y/.test(s)",
      "if(x) /; let y/.test(s)",
      "while(x-- > 0) /; let [a]/.test(s)",
      "if ((a)) /; const q/.test(s)",
      "if (f(a, (b))) /; let q/.test(s)",
      "for(;;) /; const r/.test(s)",
      "for(var k = 0; k < 1; k++) /; let r/.test(s)",
      "with(o) /; const t/.test(s)",
      "do {} while(x) /; const u/.test(s)",
   })
   void regexAfterControlHeadNotRewritten(String script) throws Exception {
      assertEquals(script, rewrite(script));
   }

   // the tester's probes run end to end: these returned false (a rewritten
   // regex) where main returns true
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "var r = false; if(1) /; const y/.test(s) && (r = true); if(false) {} r",
      "var r = false, x = 1; while(x-- > 0) /const/.test(s) && (r = true); if(false) {} r",
      "var r = false; if(1) /; let y/.test(s) && (r = true); if(false) {} r",
      "var r = false; if ((1)) /x/.test(s) && (r = true); if(false) {} r",
      "var r = false; for(var k = 0; k < 1; k++) /; const y/.test(s) && (r = true); if(false) {} r",
      "var r = false; with({}) /; let y/.test(s) && (r = true); if(false) {} r",
   })
   void regexAfterControlHeadEvaluates(String script) throws Exception {
      MapScope scope = new MapScope();
      scope.putMember("s", "x; const y; let y");
      assertEquals(true, engine.exec(engine.compile(script), scope, scope), script);
   }

   // a `)` closing a call or grouping still ends an expression: `/` is division.
   // If the lexer read `/ 2; const e = d /` as a regex, `const e` would not be
   // rewritten and the later piece would throw ReferenceError.
   @Test void divisionAfterCallOrGroupingParen() throws Exception {
      assertEquals(1.0, num("function f(a) { return a * 2; } var d = f(2) / 2; " +
                               "const e = d / 2; if(e) {} e"));
      assertEquals(2.0, num("var g = (8) / 2 / 2; const h = g; if(h) {} h"));
      assertEquals("x = f(a) / 2; var   e = x / 2", rewrite("x = f(a) / 2; const e = x / 2"));
   }

   // the splitter shares the lexer: a regex after a control head is one token,
   // so no boundary is placed inside it (piecesAllParse remains the backstop)
   @Test void splitterDoesNotBreakInsideRegexAfterControlHead() throws Exception {
      assertEquals(List.of("var r = 0; ", "if(1) /; if(y) {}/.test(s)"),
                   split("var r = 0; if(1) /; if(y) {}/.test(s)"));
      assertEquals(List.of("x = f(a) / 2; ", "if(x) {}"), split("x = f(a) / 2; if(x) {}"));
   }

   // a real declaration after `)` (ASI) is still rewritten
   @Test void constAfterCloseParenOnNextLineRewritten() throws Exception {
      assertEquals("f()\nvar   z = 1", rewrite("f()\nconst z = 1"));
      assertEquals(2.0, num("function f(){}\nf()\nconst z2 = 2\nif(z2) {}\nz2"));
   }

   // #75688 regression check: var-based completion preservation is unchanged
   @Test void varCompletionPreservationUnchanged() throws Exception {
      assertEquals("1,2", String.valueOf(run("var hv = [1,2]; if(1 > 0) { hv.join(',') } if(false) { 0 }")));
      assertEquals(10.0, num("var p = 10; if(p > 5) { p } if(p > 100) { 0 }"));
      assertEquals(4.0, num("var q = 2; if(q) { q * 2 } for(var z = 0; z < 0; z++) { z }"));
   }

   // the documented difference from Rhino: a rewritten const can be reassigned
   @Test void rewrittenConstIsAssignable() throws Exception {
      assertEquals(2.0, num("const t1 = 1; t1 = 2; t1"));
   }

   private static String rewrite(String body) throws Exception {
      java.lang.reflect.Method m = GraalJavaScriptEngine.class
         .getDeclaredMethod("rewriteTopLevelLexicalDeclarations", String.class);
      m.setAccessible(true);
      return (String) m.invoke(null, body);
   }

   @SuppressWarnings("unchecked")
   private static List<String> split(String body) throws Exception {
      java.lang.reflect.Method m = GraalJavaScriptEngine.class
         .getDeclaredMethod("splitTopLevelStatements", String.class);
      m.setAccessible(true);
      return (List<String>) m.invoke(null, body);
   }
}
