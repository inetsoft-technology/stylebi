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

@Tag("core")
class GraalJavaScriptEngineScopeTest {
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

   @Test void scriptResolvesUnqualifiedNameFromScope() throws Exception {
      MapScope scope = new MapScope();
      scope.putMember("parameter", makeParam("region", "West"));
      Object src = engine.compile("parameter.region");
      assertEquals("West", engine.exec(src, scope, scope));
   }

   // Bug #75550: `this` must be bound to the executing scope (Rhino parity), so
   // dashboard scripts that qualify assembly properties as `this.<prop>` resolve
   // instead of reading undefined off globalThis.
   @Test void thisResolvesToScope() throws Exception {
      MapScope scope = new MapScope();
      scope.putMember("parameter", makeParam("region", "West"));
      Object src = engine.compile("this.parameter.region");
      assertEquals("West", engine.exec(src, scope, scope));
   }

   // The eval-based wrapper must still return the statement-list completion
   // value, which scripted value/expression bindings depend on.
   @Test void completionValuePreservedForStatementList() throws Exception {
      MapScope scope = new MapScope();
      scope.putMember("x", 10.0);
      Object src = engine.compile("var y = x + 5;\ny * 2");
      assertEquals(30.0, ((Number) engine.exec(src, scope, scope)).doubleValue());
   }

   // Unqualified assignment must still write through to the owning scope.
   @Test void unqualifiedAssignmentWritesToScope() throws Exception {
      MapScope scope = new MapScope();
      scope.putMember("visible", true);
      Object src = engine.compile("visible = false");
      engine.exec(src, scope, scope);
      assertEquals(false, scope.getMember("visible"));
   }

   // Characterization test for the JS-string-literal encoding used by compile():
   // a body containing a double quote, a backslash, and an embedded newline must
   // survive being embedded in the eval(...) wrapper and evaluate correctly.
   @Test void bodyWithQuotesBackslashesAndNewlinesRoundTrips() throws Exception {
      MapScope scope = new MapScope();
      // script text: var s = "a\"b\\c";<newline>s  -> string value a"b\c
      Object src = engine.compile("var s = \"a\\\"b\\\\c\";\ns");
      assertEquals("a\"b\\c", engine.exec(src, scope, scope));
   }

   // A leading "use strict" that is part of a larger expression (not a standalone
   // directive) must NOT be stripped — doing so would corrupt the script.
   @Test void leadingUseStrictInExpressionIsNotStripped() throws Exception {
      MapScope scope = new MapScope();
      Object src = engine.compile("\"use strict\" + \" x\"");
      assertEquals("use strict x", engine.exec(src, scope, scope));
   }

   // Regression test for Bug #75549: a ScriptScope published as a global (e.g.
   // "viewsheet") must be wrapped as a ScopeProxy so member access such as
   // viewsheet['Chart1'] routes through ScriptScope.getMember() rather than
   // GraalJS's Java host-member reflection, which returned undefined and made
   // viewsheet['Chart1'].someMethod() throw "Cannot read property ... of undefined".
   @Test void publishedScopeGlobalResolvesBracketAndDotAccess() throws Exception {
      MapScope viewsheet = new MapScope();
      MapScope chart = new MapScope();
      chart.putMember("name", "Chart1");
      viewsheet.putMember("Chart1", chart);
      engine.put("viewsheet", viewsheet);

      // bracket access (the exact form reported in #75549) resolves the assembly
      assertEquals("Chart1",
         engine.exec(engine.compile("viewsheet['Chart1'].name"), null, null));
      // dot access resolves equivalently
      assertEquals("Chart1",
         engine.exec(engine.compile("viewsheet.Chart1.name"), null, null));
      // the member itself must be defined, not undefined
      assertEquals(Boolean.FALSE,
         engine.exec(engine.compile("typeof viewsheet['Chart1'] === 'undefined'"), null, null));
   }

   // Bug #75596: a top-level `var` declared in one script (e.g. a viewsheet
   // onInit script) must persist so a later script executed on the same engine
   // (e.g. an assembly script) can resolve it. Regression from Bug #75550, whose
   // direct-eval-in-a-function wrapper hoisted such declarations into the
   // transient wrapper frame where they were discarded.
   @Test void topLevelVarPersistsAcrossExecutions() throws Exception {
      MapScope init = new MapScope();
      engine.exec(engine.compile("var color = [1, 2, 3]"), init, init);

      MapScope assembly = new MapScope();
      Object result = engine.exec(engine.compile("color"), assembly, assembly);
      assertNotNull(result, "variable declared in a prior script must be visible");
      assertEquals(3, ((Object[]) result).length);
   }

   // Bug #75596: a top-level `function` declared in one script (e.g. a viewsheet
   // onLoad script) must likewise persist for later scripts to call.
   @Test void topLevelFunctionPersistsAcrossExecutions() throws Exception {
      MapScope load = new MapScope();
      engine.exec(engine.compile("function dbl(v){ return v * 2; }"), load, load);

      MapScope assembly = new MapScope();
      Object result = engine.exec(engine.compile("dbl(21)"), assembly, assembly);
      assertEquals(42.0, ((Number) result).doubleValue());
   }

   // Comma-separated declarators in a single `var` statement must all persist.
   @Test void multipleVarDeclaratorsPersist() throws Exception {
      MapScope init = new MapScope();
      engine.exec(engine.compile("var a = 1, b = 2, c = 3"), init, init);

      MapScope assembly = new MapScope();
      assertEquals(6.0,
         ((Number) engine.exec(engine.compile("a + b + c"), assembly, assembly)).doubleValue());
   }

   // The declaration-hoist must not disturb a script that also uses `this`: the
   // #75550 this-binding and completion value must both still hold, and a
   // variable declared in a prior script must remain readable from it.
   @Test void hoistCoexistsWithThisBindingAndPriorDeclarations() throws Exception {
      MapScope init = new MapScope();
      engine.exec(engine.compile("var factor = 10"), init, init);

      MapScope scope = new MapScope();
      scope.putMember("parameter", makeParam("region", "West"));
      Object result = engine.exec(
         engine.compile("this.parameter.region + factor"), scope, scope);
      assertEquals("West10", result);
   }

   // The declaration scanner must not mistake the slashes inside a regex literal
   // for a `//` comment (which would blank the rest of the line and drop a
   // following declaration). Here the regex contains an escaped slash, so its
   // last two characters are adjacent slashes.
   @Test void varAfterRegexWithSlashesOnSameLinePersists() throws Exception {
      MapScope init = new MapScope();
      engine.exec(engine.compile("var re = /\\//; var color = [1, 2, 3]"), init, init);

      MapScope assembly = new MapScope();
      Object result = engine.exec(engine.compile("color"), assembly, assembly);
      assertNotNull(result, "declaration after a regex literal must still be hoisted");
      assertEquals(3, ((Object[]) result).length);
   }

   // The scanner must handle nested template literals (a backtick inside a
   // `${ ... }` substitution) so a declaration following the template is not
   // dropped from collection.
   @Test void varAfterNestedTemplateLiteralPersists() throws Exception {
      MapScope init = new MapScope();
      engine.exec(
         engine.compile("var t = `a ${true ? `b` : `c`} d`; var color = 7"), init, init);

      MapScope assembly = new MapScope();
      assertEquals(7.0,
         ((Number) engine.exec(engine.compile("color"), assembly, assembly)).doubleValue());
   }

   // Bug #75625: a body that does not reference `this` must compile to the fast
   // top-level `with(__scope__){...}` form (parsed once and reused), not the
   // direct-eval wrapper that re-parses the body on every execution. Pinning the
   // emitted wrapper guards the branch decision itself.
   @Test void bodyWithoutThisUsesFastWithWrapper() throws Exception {
      String code = compiledSource("parameter.region");
      assertFalse(code.contains("eval("), "this-free body must not use the eval wrapper");
      assertTrue(code.contains("with(__scope__)"), "this-free body must use the with wrapper");
   }

   // Bug #75625: a body that references `this` must still compile to the
   // direct-eval-in-a-function form so top-level `this` binds to the scope (the
   // #75550 behavior the fast path cannot provide).
   @Test void bodyWithThisUsesEvalWrapper() throws Exception {
      String code = compiledSource("this.parameter.region");
      assertTrue(code.contains("eval("), "this-referencing body must keep the eval wrapper");
   }

   // Bug #75625: `this` appearing only inside a string literal is not a real
   // this-reference, but the conservative detector still routes it to the eval
   // form. This must remain correct (result unchanged); it is merely not the fast
   // path.
   @Test void thisInsideStringStillEvaluatesCorrectly() throws Exception {
      MapScope scope = new MapScope();
      Object src = engine.compile("\"this is text\"");
      assertEquals("this is text", engine.exec(src, scope, scope));
   }

   // Bug #75625: the fast path must preserve the statement-list completion value
   // for a multi-statement, this-free body (value/expression bindings depend on
   // it) — the same guarantee the eval form provided.
   @Test void fastPathPreservesStatementListCompletionValue() throws Exception {
      String code = compiledSource("var y = 5;\ny * 2");
      assertFalse(code.contains("eval("), "multi-statement this-free body must use the fast path");

      MapScope scope = new MapScope();
      assertEquals(10.0,
         ((Number) engine.exec(engine.compile("var y = 5;\ny * 2"), scope, scope)).doubleValue());
   }

   // Bug #75688: Rhino kept the "last non-empty" completion value, so a
   // value-producing statement followed by an `if(false)` (no else) that yields
   // no value returned the earlier value. Current ECMAScript overwrites it with
   // `undefined`. A cell-background / value binding such as the one below must
   // still return the earlier array (the reported symptom was a missing
   // background color). The trailing control-flow statement routes the body to
   // the completion-preserving wrapper.
   @Test void trailingUnrunControlFlowKeepsEarlierCompletionValue() throws Exception {
      String cmd = "if(price > 500) { [237,211,237] }\nif(row > 1){ if(value < 0) { [14,179,250] } }";
      String code = compiledSource(cmd);
      assertTrue(code.contains("eval("),
                 "multi-statement body ending in control-flow must use the completion wrapper");

      MapScope scope = new MapScope();
      scope.putMember("price", 2850.0);
      scope.putMember("row", 1.0);
      scope.putMember("value", 100.0);
      Object result = engine.exec(engine.compile(cmd), scope, scope);
      assertArrayEquals(new Object[] { 237.0, 211.0, 237.0 }, (Object[]) result);
   }

   // Bug #75688: when the trailing control-flow statement *does* produce a value,
   // it overrides the earlier one (last non-`undefined` wins).
   @Test void trailingControlFlowValueOverridesEarlier() throws Exception {
      String cmd = "if(price > 500) { [237,211,237] }\nif(row > 1){ if(value < 0) { [14,179,250] } }";
      MapScope scope = new MapScope();
      scope.putMember("price", 2850.0);
      scope.putMember("row", 2.0);
      scope.putMember("value", -5.0);
      Object result = engine.exec(engine.compile(cmd), scope, scope);
      assertArrayEquals(new Object[] { 14.0, 179.0, 250.0 }, (Object[]) result);
   }

   // Bug #75688: `var`/`function` declarations must remain visible to later
   // statements across the per-piece evals of the completion wrapper.
   @Test void completionWrapperKeepsDeclarationsVisibleAcrossStatements() throws Exception {
      // The last statement is control-flow (routes to the wrapper) and reads a
      // var and a function declared in earlier pieces.
      String cmd = "var base = 3;\nfunction dbl(x){ return x * 2 }\nif(base > 0){ dbl(base) }";
      String code = compiledSource(cmd);
      assertTrue(code.contains("eval("), "body ending in control-flow must use the completion wrapper");

      MapScope scope = new MapScope();
      assertEquals(6.0, ((Number) engine.exec(engine.compile(cmd), scope, scope)).doubleValue());
   }

   // Bug #75688: the earlier value must survive even when the two statements are
   // separated only by a newline (ASI), not a `;` or a preceding `}` — a natural
   // "default value, then conditional override" shape.
   @Test void newlineSeparatedValueThenUnrunControlFlowKeepsValue() throws Exception {
      String cmd = "[237,211,237]\nif(row > 1){ [14,179,250] }";
      String code = compiledSource(cmd);
      assertTrue(code.contains("eval("), "value-then-control-flow must use the completion wrapper");

      MapScope scope = new MapScope();
      scope.putMember("row", 1.0);
      assertArrayEquals(new Object[] { 237.0, 211.0, 237.0 },
                        (Object[]) engine.exec(engine.compile(cmd), scope, scope));
   }

   // Bug #75688: a standalone `while` loop (not a do-while tail) after a block
   // must start a new statement, so the block's value is not clobbered when the
   // loop yields nothing.
   @Test void standaloneWhileAfterBlockDoesNotClobber() throws Exception {
      String cmd = "if(price > 500){ [237,211,237] }\nwhile(false){ }";
      String code = compiledSource(cmd);
      assertTrue(code.contains("eval("), "trailing standalone while must use the completion wrapper");

      MapScope scope = new MapScope();
      scope.putMember("price", 2850.0);
      assertArrayEquals(new Object[] { 237.0, 211.0, 237.0 },
                        (Object[]) engine.exec(engine.compile(cmd), scope, scope));
   }

   // Bug #75688: `else if` and a do-while's trailing `while` are part of one
   // statement and must never be split apart. (A bad split would make a piece
   // like `if(a){} else ` fail to parse; parse-validation then falls back, but
   // the splitter must not create the bad boundary in the first place.)
   @Test void elseIfIsNotSplit() throws Exception {
      MapScope scope = new MapScope();
      scope.putMember("a", false);
      scope.putMember("b", true);
      assertArrayEquals(new Object[] { 2.0 },
         (Object[]) engine.exec(engine.compile("if(a){ [1] } else if(b){ [2] }"), scope, scope));
   }

   @Test void doWhileTrailingWhileIsNotSplit() throws Exception {
      MapScope scope = new MapScope();
      // last statement is control-flow (do-while), so it routes to the wrapper;
      // the trailing `while(...)` must stay attached to its `do` body.
      Object r = engine.exec(engine.compile("var i = 0;\ndo { i = i + 1 } while(i < 3)"), scope, scope);
      assertEquals(3.0, ((Number) r).doubleValue());
   }

   // Bug #75688: an `async` modifier must stay attached to the `function` it
   // precedes — the splitter must not place a boundary between `async` and
   // `function`. A bad split evaluates a bare `async` identifier (ReferenceError)
   // and strips the async-ness; both halves parse alone, so piecesAllParse cannot
   // catch it (async must be in SUPPRESS_BOUNDARY_AFTER).
   @Test void asyncFunctionDeclarationIsNotSplit() throws Exception {
      MapScope scope = new MapScope();
      assertArrayEquals(new Object[] { 1.0, 2.0, 3.0 },
         (Object[]) engine.exec(
            engine.compile("[1,2,3]\nasync function foo(){ return 1; }"), scope, scope));
   }

   // Bug #75688: a labeled control-flow statement (`name: if(...)`) must split
   // off as one piece (boundary before the label), so an earlier value survives
   // when the labeled statement yields nothing. The boundary goes before the
   // label identifier, keeping `label: stmt` together.
   @Test void labeledTrailingControlFlowKeepsEarlierValue() throws Exception {
      String cmd = "[7,7,7]\nouter: if(false){ [1,1,1] }";
      String code = compiledSource(cmd);
      assertTrue(code.contains("eval("), "labeled trailing control-flow must use the completion wrapper");

      MapScope scope = new MapScope();
      assertArrayEquals(new Object[] { 7.0, 7.0, 7.0 },
                        (Object[]) engine.exec(engine.compile(cmd), scope, scope));
   }

   // Bug #75688: a top-level ternary must not be mistaken for a label (its `:` is
   // preceded by `?`, not a boundary-permitting token) — it stays a single piece.
   @Test void topLevelTernaryIsNotSplitAsLabel() throws Exception {
      MapScope scope = new MapScope();
      scope.putMember("cond", true);
      assertArrayEquals(new Object[] { 1.0 },
         (Object[]) engine.exec(engine.compile("cond ? [1] : [2]"), scope, scope));
   }

   private String compiledSource(String cmd) throws Exception {
      return ((org.graalvm.polyglot.Source) engine.compile(cmd)).getCharacters().toString();
   }

   private static ScriptScope makeParam(String k, String v) {
      MapScope p = new MapScope();
      p.putMember(k, v);
      return p;
   }
}
