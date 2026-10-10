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

   // review r2: a declaration on the line after a postfix `++`/`--` (or any
   // other statement end) is at statement position by ASI and must be rewritten
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "var i = 0; i++\\nconst d = 1; if(d) { d }",
      "var i = 0; i--\\nconst d = 1; if(d) { d }",
      "var x = 0; x--\\nconst d = 1; if(d) { d }",
      "var a = 1; a\\nconst d = 1; if(d) { d }",
      "var a = [0]; a[0]++\\nconst d = 1; if(d) { d }",
      "var a = [5]; a[0]\\nconst d = 1; if(d) { d }",
      "function f() { return 0; } f()\\nconst d = 1; if(d) { d }",
      "var i = 0; i++ // note\\nconst d = 1; if(d) { d }",
      "var i = 0; i++ /* a\\nb */ const d = 1; if(d) { d }",
      "var i = 0; i++\\nlet d = 1; if(d) { d }",
      "var i = 3; i--\\nlet d = 1; if(d) { d }",
   })
   void declarationAfterLineBreakRewritten(String script) throws Exception {
      assertEquals(1.0, num(script.replace("\\n", "\n")), script);
   }

   // `let` after a line break that follows an operator may still be an operand
   @Test void letAfterOperatorLineBreakNotRewritten() throws Exception {
      assertEquals("x = y +\nlet [0]", rewrite("x = y +\nlet [0]"));
      assertEquals("x =\nlet\ny = 1", rewrite("x =\nlet\ny = 1"));
      assertEquals("i++\nvar d = 1", rewrite("i++\nlet d = 1"));
      assertEquals("i++\nvar   d = 1", rewrite("i++\nconst d = 1"));
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

   // ---- Bug #77181: an initializer-less rewritten let/const must start undefined
   // on every run of the plain with(__scope__) path, as FormulaTableLens runs it:
   // compiled once, one engine, one reused row scope, only the row value changing.

   private List<Object> rows(String script, Object... values) throws Exception {
      Object compiled = engine.compile(script);
      MapScope row = new MapScope();
      List<Object> out = new ArrayList<>();

      for(Object v : values) {
         row.putMember("a", v);
         out.add(engine.exec(compiled, row, row));
      }

      return out;
   }

   private static List<Object> highNullNull() {
      return Arrays.asList("High", null, null);
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
      "let r; (a > 5) && (r = 'High'); r",
      "let r2; r2 = a > 5 ? 'High' : r2; r2",
      "let r3; let s3; (a > 5) && (r3 = 'High'); r3",
      "let r4, s4; (a > 5) && (r4 = 'High'); r4",
      "let s5, r5; (a > 5) && (r5 = 'High'); r5",
      "let x6 = 1, r6; (a > 5) && (r6 = 'High'); r6",
      "let x7 = Math.max(1, 2), r7; (a > 5) && (r7 = 'High'); r7",
      "let x8 = [1, 2], y8 = {p: 1, q: 2}, r8; (a > 5) && (r8 = 'High'); r8",
      "let x9 = 'a,b', z9 = `c,${1 + 1}`, r9; (a > 5) && (r9 = 'High'); r9",
      "let x10 = function(p, q) { return p, q; }, r10; (a > 5) && (r10 = 'High'); r10",
      "let {p11} = {p11: 1}, r11; (a > 5) && (r11 = 'High'); r11",
      "const r12; (a > 5) && (r12 = 'High'); r12",
      "let r13 /* c, d */; (a > 5) && (r13 = 'High'); r13",
      "let r14;\\n(a > 5) && (r14 = 'High');\\nr14",
      "let r15\\n(a > 5) && (r15 = 'High'); r15",
      "let x16 = 1\\n, r16; (a > 5) && (r16 = 'High'); r16",
      // the paths that were already right stay right
      "let r17; if(a > 5) r17 = 'High'; r17",
      "let r18 = null; (a > 5) && (r18 = 'High'); r18",
      "let r19; (a > 5) && (r19 = 'High'); this; r19",
      "{ let r20; (a > 5) && (r20 = 'High'); r20 }",
   })
   void initializerlessDeclarationStartsUndefinedEveryRow(String script) throws Exception {
      assertEquals(highNullNull(), rows(script.replace("\\n", "\n"), 10, 1, 1), script);
   }

   // the reporter's counter: each row starts from undefined
   @Test void counterDoesNotAccumulateAcrossRows() throws Exception {
      assertEquals(Arrays.asList(1.0, 1.0, 1.0),
                   rows("let n; n = (n || 0) + 1; n", 10, 1, 1).stream()
                      .map(v -> ((Number) v).doubleValue()).toList());
   }

   // native var is unchanged: its global persists across runs (#75596)
   @Test void nativeVarStillKeepsValue() throws Exception {
      assertEquals(Arrays.asList("High", "High", "High"),
                   rows("var rv; (a > 5) && (rv = 'High'); rv", 10, 1, 1));
   }

   // different formulas sharing a name on one engine
   @Test void noLeakAcrossFormulas() throws Exception {
      assertEquals("fromA", run("let q; q = 'fromA'; q"));
      assertNull(run("let q; q"));
   }

   // a later script that only reads the name is not reset (#76980 persistence)
   @Test void readerOfInitializerlessLetKeepsValue() throws Exception {
      run("let pk; pk = 7;");
      assertEquals(7.0, num("pk"));
   }

   // the reset is outside the with: a same-named scope member is never written
   @Test void scopeMemberNotClobbered() throws Exception {
      MapScope scope = new MapScope();
      scope.putMember("data", "SCOPE");
      assertEquals("SCOPE", engine.exec(engine.compile("let data; data"), scope, scope));
      assertEquals("SCOPE", scope.getMember("data"));
   }

   // engine-owned globals are never reset by a declaration of the same name
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "split|function", "isNull|function", "Math|object", "CALC|object",
      "java|object", "Packages|object", "globalThis|object", "parameter|string",
      "viewsheet|string",
   })
   void engineGlobalsNotReset(String name, String type) throws Exception {
      engine.close();
      engine = new GraalJavaScriptEngine();
      Map<String, Object> vars = new HashMap<>();
      vars.put("parameter", "P");
      engine.init(vars);
      engine.put("viewsheet", "VS");

      run("let " + name + "; 1");
      assertEquals(type, run("typeof " + name), name);
      // and the engine still runs scripts afterwards
      assertEquals(3.0, num("1 + 2"));
   }

   @Test void scopeBindingNeverReset() throws Exception {
      run("let __scope__; 1");
      MapScope scope = new MapScope();
      scope.putMember("a", 4);
      assertEquals(5.0, ((Number) engine.exec(engine.compile("a + 1"), scope, scope)).doubleValue());
   }

   // `let split;` then a later script that calls split(...)
   @Test void letSplitDoesNotBreakLaterSplitCall() throws Exception {
      run("let split; 1");
      assertEquals("b", run("split('a,b', ',')[1]"));
   }

   // error line numbers do not move with the reset prefix
   @Test void errorLineUnchanged() {
      Exception ex = assertThrows(Exception.class,
                                  () -> run("let rl;\nundefinedFn77181()"));
      assertTrue(ex.getMessage().contains("(line 2)"), ex.getMessage());
   }

   // a syntax error keeps its line:column with the reset prefix
   @Test void syntaxErrorPositionUnchanged() {
      Exception ex = assertThrows(Exception.class, () -> run("let rs;\nlet x = ;"));
      assertTrue(ex.getMessage().contains("<cmd>:2:8"), ex.getMessage());
   }

   // `let` inside a string, comment, template or regex declares nothing, so the
   // implicit global the body assigns is not reset and persists as before
   @ParameterizedTest
   @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
      "var s1 = 'let q1;'; (a > 5) && (q1 = 'High'); q1",
      "// let q2;\\n(a > 5) && (q2 = 'High'); q2",
      "/* let q3; */ (a > 5) && (q3 = 'High'); q3",
      "var t4 = `let q4; ${1}`; (a > 5) && (q4 = 'High'); q4",
      "var re5 = /let q5;/; (a > 5) && (q5 = 'High'); q5",
   })
   void letInLiteralOrCommentNotReset(String script) throws Exception {
      assertEquals(Arrays.asList("High", "High", "High"),
                   rows(script.replace("\\n", "\n"), 10, 1, 1), script);
   }

   // `let` as a property name is neither rewritten nor collected
   @Test void letAsPropertyName() throws Exception {
      assertEquals(Arrays.asList("1|High", "1|", "1|"),
                   rows("var o6 = {}; o6.let = 1; let r6; (a > 5) && (r6 = 'High'); " +
                        "[o6.let, r6].join('|')", 10, 1, 1));
      assertEquals(Arrays.asList(2.0, null, null),
                   rows("var o7 = {let: 2}; let r7; (a > 5) && (r7 = o7.let); r7", 10, 1, 1)
                      .stream().map(v -> v == null ? null : ((Number) v).doubleValue()).toList());
   }

   // a put() made after the formula was compiled is still never reset; since
   // #77321 the declaration is a native block let there, so it reads undefined
   @Test void putAfterCompileNotReset() throws Exception {
      Object compiled = engine.compile("let lateput; lateput");
      engine.put("lateput", "LP");
      MapScope scope = new MapScope();
      assertNull(engine.exec(compiled, scope, scope));
      assertEquals("LP", run("lateput"));
   }

   // pool on: one span holds one context across the rows, as FormulaTableLens
   // batches them, so the reset must work there too; an env variable is not reset
   @Test void pooledSpanStartsUndefinedEveryRow() throws Exception {
      inetsoft.util.script.graal.pool.WorksheetScriptEnv env =
         inetsoft.util.script.graal.pool.PoolTestSupport.env();
      env.put("envv", "E");
      Object r = env.compile("let r; (a > 5) && (r = 'High'); r");
      Object n = env.compile("let n; n = (n || 0) + 1; n");
      Object e = env.compile("let envv; envv");
      Object reader = env.compile("envv");
      List<Object> outR = new ArrayList<>(), outN = new ArrayList<>(), outE = new ArrayList<>();
      MapScope row = new MapScope();

      try(var span = env.openSpan()) {
         for(Object v : new Object[] { 10, 1, 1 }) {
            row.putMember("a", v);
            outR.add(env.exec(r, row, row, null));
            outN.add(((Number) env.exec(n, row, row, null)).doubleValue());
            outE.add(env.exec(e, row, row, null));
            outE.add(env.exec(reader, row, row, null));
         }
      }

      assertEquals(highNullNull(), outR);
      assertEquals(Arrays.asList(1.0, 1.0, 1.0), outN);
      // #77321: the declaration is a native block let (undefined), the env value survives
      assertEquals(Arrays.asList(null, "E", null, "E", null, "E"), outE);
   }

   // review r1: an undeclared global after a `i++ / 2` initializer is not reset
   @Test void globalAfterPostfixDivisionNotReset() throws Exception {
      run("i = 1; a = 2; c = 5; d = 6");
      run("let x = i++ / 2; bar = a / 1, c; x");
      run("let y = i-- / 2; bar = a / 1, d; y");
      assertEquals(5.0, num("c"));
      assertEquals(6.0, num("d"));
   }

   // review r1: the top-level lexer reads `/` after a postfix `++`/`--` as
   // division too, so a later `let` is still seen (and rewritten)
   @Test void letAfterPostfixDivisionRewritten() throws Exception {
      String body = "x = i++ / 2; let r; b = r / 1";
      assertNotEquals(body, rewrite(body));
      assertEquals(2.0, num("var i = 3; var x = i++ / 2; let r; r = x * 4 / 3; if(false) {} r"));
      assertEquals(2.0, num("var j = 3; var y = j-- / 2; let q; q = y * 4 / 3; if(false) {} q"));
   }

   // minor r1: a put() made while the span holds the context goes straight
   // through Slot.applyOwn (not the init snapshot or replay); the hostGlobal
   // hook there must still keep the let/const reset off it
   @Test void pooledPutInsideSpanNotReset() throws Exception {
      inetsoft.util.script.graal.pool.WorksheetScriptEnv env =
         inetsoft.util.script.graal.pool.PoolTestSupport.env();
      Object e = env.compile("let inspan; inspan");
      Object reader = env.compile("inspan");
      List<Object> out = new ArrayList<>();
      MapScope row = new MapScope();

      try(var span = env.openSpan()) {
         env.exec(e, row, row, null);   // claims the context for this thread
         env.put("inspan", "I");

         for(int k = 0; k < 3; k++) {
            out.add(env.exec(e, row, row, null));
            out.add(env.exec(reader, row, row, null));
         }
      }

      // #77321: the declaration is a native block let (undefined), the put value survives
      assertEquals(Arrays.asList(null, "I", null, "I", null, "I"), out);
   }

   // ---- Bug #77321: an initializer-less top-level let/const named like a global the
   // engine defines (a lower-case CALC function, a global function, a put() name) must
   // start undefined on every run of a single-piece formula, and the engine global
   // must stay intact. The single-piece `&&` form: an `if` would split the body into
   // pieces (the #77331 path), which was already right.

   @ParameterizedTest
   @CsvSource({ "value", "count", "sum", "year", "max", "split" })
   void hostGlobalNamedLetStartsUndefinedEveryRow(String name) throws Exception {
      String typeBefore = String.valueOf(run("typeof " + name));
      String script = "let " + name + "; a > 5 && (" + name + " = 'High'); " + name;

      assertEquals(highNullNull(), rows(script, 10, 1, 1), script);
      // a first row that does not assign reads undefined, not the engine function
      assertEquals(Arrays.asList(null, "High", null), rows(script, 1, 10, 1), script);
      assertEquals(typeBefore, run("typeof " + name), name);
      assertEquals("function", typeBefore, name);
   }

   @Test void calcFunctionStillWorksAfterAssigningRuns() throws Exception {
      rows("let count; a > 5 && (count = 'High'); count", 10, 1, 1);
      rows("let value; a > 5 && (value = 'High'); value", 10, 1, 1);
      assertEquals(3.0, num("count([1,2,3])"));
      assertEquals("function", run("typeof value"));
   }

   @Test void constNamedLikeCalcFunction() throws Exception {
      assertEquals(highNullNull(), rows("const value; a > 5 && (value = 'High'); value", 10, 1, 1));
      assertEquals("function", run("typeof value"));
   }

   // a declaration mixing a colliding and a non-colliding name
   @Test void mixedDeclarationNamedLikeCalcFunction() throws Exception {
      assertEquals(highNullNull(),
                   rows("let value, total; a > 5 && (value = 'High'); value", 10, 1, 1));
      assertEquals(highNullNull(),
                   rows("let x = 1, count; a > 5 && (count = 'High'); count", 10, 1, 1));
      assertEquals("function", run("typeof count"));
   }

   // a host function that calls the engine global while the body runs still sees it
   @Test void hostFunctionCallingEngineGlobalInsideBody() throws Exception {
      run("function lib77321() { return typeof value; }");
      assertEquals(Arrays.asList("function", null, null),
                   rows("let value; a > 5 && (value = lib77321()); value", 10, 1, 1));
   }

   // a put() name: decided per exec against the running engine's host globals
   @Test void putNameStartsUndefinedEveryRow() throws Exception {
      Object compiled = engine.compile("let pv77321; a > 5 && (pv77321 = 'High'); pv77321");
      engine.put("pv77321", "P");
      MapScope row = new MapScope();
      List<Object> out = new ArrayList<>();

      for(Object v : new Object[] { 10, 1, 1 }) {
         row.putMember("a", v);
         out.add(engine.exec(compiled, row, row));
      }

      assertEquals(highNullNull(), out);
      assertEquals("P", run("pv77321"));
   }

   // a body that also declares the name with var is an early error as a native let
   // block: it falls back to the eval wrapper, never to the leaking plain Source
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "let value; var value; a > 5 && (value = 'High'); value",
      "let count; let count; a > 5 && (count = 'High'); count",
   })
   void duplicateDeclarationFallsBackToEvalWrapper(String script) throws Exception {
      Object compiled = engine.compile(script);
      assertInstanceOf(GraalJavaScriptEngine.PlainScript.class, compiled);
      assertTrue(((GraalJavaScriptEngine.PlainScript) compiled).colliding()
                    .getCharacters().toString().contains("eval("), script);
      assertEquals(highNullNull(), rows(script, 10, 1, 1), script);
   }

   @Test void collidingSourceKeepsNativeLet() throws Exception {
      GraalJavaScriptEngine.PlainScript compiled = (GraalJavaScriptEngine.PlainScript)
         engine.compile("const value; let x = 1; let y = 2, total; value");
      // only the var is declared in the var store of the scope (Bug #77595)
      assertEquals("with(__inetsoft_declare__(__scope__.__inetsoft_own_locals__,[\"x\"])){" +
                   "with(__scope__){let   value; var x = 1; let y = 2, total; value\n}}",
                   compiled.colliding().getCharacters().toString());
   }

   // a name that is no host global keeps the #77181 plain Source (and its #76980
   // cross-script visibility)
   @Test void nonCollidingNameKeepsPlainSource() throws Exception {
      Object compiled = engine.compile("let total; a > 5 && (total = 'High'); total");
      assertInstanceOf(GraalJavaScriptEngine.PlainScript.class, compiled);
      GraalJavaScriptEngine.PlainScript plain = (GraalJavaScriptEngine.PlainScript) compiled;
      assertSame(plain.plain(), plain.source(Set.of("value", "count")));
      assertSame(plain.colliding(), plain.source(Set.of("total")));
      assertSame(plain.colliding(), plain.source(null));
      assertEquals(highNullNull(), rows("let total; a > 5 && (total = 'High'); total", 10, 1, 1));
      run("let pk77321; pk77321 = 7;");
      assertEquals(7.0, num("pk77321"));
      // a body without reset names still compiles to a bare Source
      assertInstanceOf(org.graalvm.polyglot.Source.class, engine.compile("var q = 1; q"));
   }

   @Test void plainScriptEqualByContent() throws Exception {
      String script = "let value; a > 5 && (value = 'High'); value";
      Object a = engine.compile(script);
      Object b = engine.compile(script);
      assertNotSame(a, b);
      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());
      // the compiled body, as PieceScript names it (WsExecContext logs it)
      assertEquals("var value; a > 5 && (value = 'High'); value", a.toString());
   }

   @Test void collidingErrorLineUnchanged() {
      Exception ex = assertThrows(Exception.class,
                                  () -> run("let value;\nundefinedFn77321()"));
      assertTrue(ex.getMessage().contains("(line 2)"), ex.getMessage());

      ex = assertThrows(Exception.class,
                        () -> run("let value; var value;\nundefinedFn77321()"));
      assertTrue(ex.getMessage().contains("(line 2)"), ex.getMessage());
   }

   // accepted behavior change (pre-#76980 semantics): a read before the declaration
   // is in the temporal dead zone
   @Test void collidingReadBeforeDeclarationIsTdz() {
      Exception ex = assertThrows(Exception.class,
                                  () -> run("var t77321 = typeof value; let value; t77321"));
      assertTrue(ex.getMessage().contains("ReferenceError"), ex.getMessage());
   }

   // accepted behavior change (pre-#76980 semantics): a function declared in the body
   // closes over the block let, not the engine global
   @Test void collidingBodyFunctionSeesBlockLet() throws Exception {
      assertEquals("X", run("let value; function get77321() { return value; } " +
                              "value = 'X'; get77321()"));
      assertEquals("X", run("get77321()"));
      assertEquals("function", run("typeof value"));
   }

   // the completion value of the body is kept
   @Test void collidingCompletionValue() throws Exception {
      assertEquals(7.0, num("let value; 7"));
      assertNull(run("let value;"));
      assertEquals(3.0, num("let value; value = 1; value + 2"));
   }

   // pool on: the pooled env names and CALC functions are host globals there too
   @Test void pooledSpanHostGlobalNamedLetStartsUndefinedEveryRow() throws Exception {
      inetsoft.util.script.graal.pool.WorksheetScriptEnv env =
         inetsoft.util.script.graal.pool.PoolTestSupport.env();
      env.put("envw", "E");
      Object v = env.compile("let value; (a > 5) && (value = 'High'); value");
      Object w = env.compile("let envw; (a > 5) && (envw = 'High'); envw");
      Object t = env.compile("typeof value + ',' + envw");
      List<Object> outV = new ArrayList<>(), outW = new ArrayList<>(), outT = new ArrayList<>();
      MapScope row = new MapScope();

      try(var span = env.openSpan()) {
         for(Object a : new Object[] { 10, 1, 1 }) {
            row.putMember("a", a);
            outV.add(env.exec(v, row, row, null));
            outW.add(env.exec(w, row, row, null));
            outT.add(env.exec(t, row, row, null));
         }
      }

      assertEquals(highNullNull(), outV);
      assertEquals(highNullNull(), outW);
      assertEquals(Collections.nCopies(3, "function,E"), outT);
   }

   // ---- Bug #77321 verification: gaps the refuter listed

   // a library function that calls the engine global value(...) while the body runs
   @Test void libraryCallingCalcFunctionInsideBody() throws Exception {
      run("function lib77321v() { return value('5'); }");
      List<Object> out = rows("let value; a > 5 && (value = lib77321v()); value", 10, 1, 1);
      assertEquals(Arrays.asList(5.0, null, null), out.stream()
         .map(v -> v == null ? null : ((Number) v).doubleValue()).toList());
      assertEquals("function", run("typeof value"));
   }

   // nested exec on the same engine: an inner colliding script run from inside the
   // outer body neither sees nor replaces the outer block let, nor the engine global
   @Test void nestedExecOnSameEngine() throws Exception {
      Object inner = engine.compile("let value; b > 5 && (value = 'In'); value");
      Object outer = engine.compile(
         "let value; a > 5 && (value = 'High'); var i77321 = inner(); i77321 + '|' + value");
      MapScope innerRow = new MapScope();
      innerRow.putMember("b", 10);
      MapScope row = new MapScope();
      row.putMember("inner", (org.graalvm.polyglot.proxy.ProxyExecutable) args -> {
         try {
            return engine.exec(inner, innerRow, innerRow);
         }
         catch(Exception ex) {
            throw new RuntimeException(ex);
         }
      });
      List<Object> out = new ArrayList<>();

      for(Object v : new Object[] { 10, 1, 1 }) {
         row.putMember("a", v);
         out.add(engine.exec(outer, row, row));
      }

      assertEquals(Arrays.asList("In|High", "In|undefined", "In|undefined"), out);
      assertEquals("function", run("typeof value"));
   }

   // a scope member named like the CALC function: the block let shadows it, so the
   // assignment neither leaks through the member nor overwrites it
   @Test void scopeMemberNamedValue() throws Exception {
      Object compiled = engine.compile("let value; a > 5 && (value = 'High'); value");
      MapScope row = new MapScope();
      row.putMember("value", "SCOPE");
      List<Object> out = new ArrayList<>();

      for(Object v : new Object[] { 10, 1, 1 }) {
         row.putMember("a", v);
         out.add(engine.exec(compiled, row, row));
      }

      assertEquals(highNullNull(), out);
      assertEquals("SCOPE", row.getMember("value"));
      assertEquals("function", run("typeof value"));
   }

   // an intentional assignment of a host global without a declaration (onInit style)
   // keeps replacing the global and stays visible to later scripts
   @Test void undeclaredHostGlobalAssignmentStillPersists() throws Exception {
      assertInstanceOf(org.graalvm.polyglot.Source.class, engine.compile("value = 5"));
      run("value = 5");
      assertEquals(5.0, num("value"));
      assertEquals(6.0, num("value + 1"));
   }

   // the extractor: which names it takes, and where it declines
   @ParameterizedTest
   @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
      "let r;                                  | r",
      "let r, s;                               | r,s",
      "let x = 1, r;                           | r",
      "let x = Math.max(1, 2), r;              | r",
      "let x = f(a, [b, {c: d}]), r, s = 2, t  | r,t",
      "let x = 'a,b', r;                       | r",
      "let x = /,/g, r;                        | r",
      "let r\\nfoo = 1, bar = 2                | r",
      "let x = a\\nfoo = 1, bar = 2            | \"\"",
      "let x = a +\\nb, r;                     | \"\"",
      "let x = 1\\n, r                         | r",
      "let r; let s; var v; const c;           | r,s,c",
      "let {p} = o, r;                         | r",
      "let r = 1;                              | \"\"",
      "{ let r; }                              | \"\"",
      "for(let i; ;) {}                        | \"\"",
      "var r;                                  | \"\"",
      "let __scope__;                          | \"\"",
      // review r1: a `/` after a postfix `++`/`--` is division, not a regex
      // start, so `/ 2; bar = a /` is not swallowed and `c` is not collected
      "let x = i++ / 2; bar = a / 1, c;        | \"\"",
      "let x = i-- / 2; bar = a / 1, c;        | \"\"",
      "let x = a[0]++ / 2; bar = a / 1, c;     | \"\"",
      "let x = f() -- / 2; bar = a / 1, c;     | \"\"",
      "let x = i++ / 2, r;                     | r",
      // and a `/` after an if/while/for/with head's `)` is a regex, as in scanTopLevel
      "let x = function(){ if(a) /[)]/.test(s), q; };  | \"\"",
      "let x = function(){ if(a) /[)]/.test(s) }, r;   | r",
   })
   void initializerlessNames(String body, String expected) throws Exception {
      java.lang.reflect.Method m = GraalJavaScriptEngine.class
         .getDeclaredMethod("collectInitializerlessLexicalNames", String.class);
      m.setAccessible(true);
      @SuppressWarnings("unchecked")
      Set<String> names = (Set<String>) m.invoke(null, body.replace("\\n", "\n"));
      assertEquals(expected, String.join(",", names), body);
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
