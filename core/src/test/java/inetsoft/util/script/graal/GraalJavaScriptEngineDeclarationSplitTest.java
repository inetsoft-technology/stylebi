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

import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77076: a declaration completes empty and keeps the earlier completion value (unlike
 * an if without else or an unrun loop), so the #75688 completion wrapper need not split before
 * one. Splitting there sent every formula with a top-level declaration down the per-piece
 * direct-eval path, which GraalJS re-parses on every execution (#75625): about 25x slower per
 * row than the parse-once with(__scope__) form.
 */
@Tag("core")
class GraalJavaScriptEngineDeclarationSplitTest {
   static class MapScope implements ScriptScope {
      final Map<String, Object> m = new LinkedHashMap<>();
      public Object getMember(String n) { return m.get(n); }
      public boolean hasMember(String n) { return m.containsKey(n); }
      public void putMember(String n, Object v) { m.put(n, v); }
      public Object[] getMemberKeys() { return m.keySet().toArray(); }
   }

   @BeforeEach void setup() throws Exception {
      engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
   }

   @AfterEach void teardown() {
      engine.close();
   }

   @Test void declarationsAreNotSplitPoints() throws Exception {
      for(String body : new String[] {
         "var v = 5; var w = v + 1; w",
         "var a = 1, b = 2; a + b",
         "function f() { return 1; } f()",
         "class C { } new C()",
         "x = 1; var y = x; y" })
      {
         assertEquals(List.of(body), split(body), body);
      }
   }

   @Test void controlFlowAfterADeclarationIsStillSplit() throws Exception {
      assertEquals(List.of("var a = 7; ", "if(false) {}"), split("var a = 7; if(false) {}"));
   }

   @Test void aDeclarationFormulaIsParsedOnce() throws Exception {
      Object script = engine.compile("var v = 5; var w = v + 1; w");

      assertTrue(script instanceof Source, String.valueOf(script));
      assertTrue(((Source) script).getCharacters().toString().startsWith("with(__scope__)"),
                 "the per-piece eval wrapper re-parses on every execution: " + script);
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "var v = 5; var w = v + 1; w                | 6",
      "7; var x = 1;                              | 7",
      "7; var x = 1; if(false) {}                 | 7",
      "7; function f() {}                         | 7",
      "var a = 2; if(a) { a * 3 } var b = 1;      | 6",
      "var p = 10; if(p > 5) { p } if(p > 100) { 0 } | 10",
      "let s = 0; for(let i = 0; i < 3; i++) { s += i; } s | 3" })
   void completionValuesAreUnchanged(String script, double expected) throws Exception {
      Object result = run(script);
      assertInstanceOf(Number.class, result, script + " -> " + result);
      assertEquals(expected, ((Number) result).doubleValue(), script);
   }

   @Test void topLevelDeclarationsPersistAcrossExecutions() throws Exception {
      run("var kept77076 = 4; function twice77076(n) { return n * 2; }");

      assertEquals(10.0, ((Number) run("twice77076(kept77076 + 1)")).doubleValue());
   }

   private Object run(String script) throws Exception {
      MapScope scope = new MapScope();
      return engine.exec(engine.compile(script), scope, scope);
   }

   @SuppressWarnings("unchecked")
   private static List<String> split(String body) throws Exception {
      java.lang.reflect.Method m = GraalJavaScriptEngine.class
         .getDeclaredMethod("splitTopLevelStatements", String.class);
      m.setAccessible(true);
      return (List<String>) m.invoke(null, body);
   }

   private GraalJavaScriptEngine engine;
}
