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

   private static ScriptScope makeParam(String k, String v) {
      MapScope p = new MapScope();
      p.putMember(k, v);
      return p;
   }
}
