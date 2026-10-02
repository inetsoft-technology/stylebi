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

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77331: a var of a split formula named like an engine function (a CALC member such as
 * max, count, value, text) starts undefined on every run, as with the eval wrapper before #77249.
 */
@Tag("core")
class GraalJavaScriptEnginePieceHostGlobalVarTest {
   @BeforeEach void setup() throws Exception {
      engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
   }

   @AfterEach void teardown() {
      engine.close();
   }

   // zz77249 is the control: not an engine global, so the piece reset already covers it
   @ParameterizedTest
   @ValueSource(strings = { "max", "count", "value", "text", "zz77249" })
   void aVarNamedLikeAnEngineFunctionStartsUndefinedOnEveryRun(String name) throws Exception {
      Object script = engine.compile(
         "var " + name + "; if(a > b) " + name + " = a; " + name);
      int[][] runs = { { 1, 5 }, { 9, 2 }, { 1, 5 }, { 1, 5 } };
      List<Object> results = new ArrayList<>();

      for(int[] ab : runs) {
         MapScope scope = new MapScope();
         scope.putMember("a", ab[0]);
         scope.putMember("b", ab[1]);
         Object v = engine.exec(script, scope, scope);
         results.add(v instanceof Number n ? n.doubleValue() : v);
      }

      assertEquals(Arrays.asList(null, 9.0, null, null), results, name);
   }

   // a put() name is an engine global too
   @Test void aVarNamedLikeAPutNameStartsUndefinedOnEveryRun() throws Exception {
      engine.put("zzhost", 1);
      Object script = engine.compile(
         "var zzhost; if(a > b) zzhost = a; zzhost");
      int[][] runs = { { 1, 5 }, { 9, 2 }, { 1, 5 }, { 1, 5 } };
      List<Object> results = new ArrayList<>();

      for(int[] ab : runs) {
         MapScope scope = new MapScope();
         scope.putMember("a", ab[0]);
         scope.putMember("b", ab[1]);
         Object v = engine.exec(script, scope, scope);
         results.add(v instanceof Number n ? n.doubleValue() : v);
      }

      assertEquals(Arrays.asList(null, 9.0, null, null), results);
   }

   // an initializer that runs only on some runs does not make the var safe to keep
   @ParameterizedTest
   @ValueSource(strings = { "max", "zz77249" })
   void aConditionallyInitializedVarStartsUndefinedOnEveryRun(String name) throws Exception {
      Object script = engine.compile(
         "var q = 1; if(c) { var " + name + " = 1; } " + name);
      List<Object> results = new ArrayList<>();

      for(boolean c : new boolean[] { false, true, false }) {
         MapScope scope = new MapScope();
         scope.putMember("c", c);
         Object v = engine.exec(script, scope, scope);
         results.add(v instanceof Number n ? n.doubleValue() : v);
      }

      assertEquals(Arrays.asList(null, 1.0, null), results, name);
   }

   private static final class MapScope implements ScriptScope {
      public Object getMember(String n) { return m.get(n); }
      public boolean hasMember(String n) { return m.containsKey(n); }
      public void putMember(String n, Object v) { m.put(n, v); }
      public Object[] getMemberKeys() { return m.keySet().toArray(); }
      private final Map<String, Object> m = new HashMap<>();
   }

   private GraalJavaScriptEngine engine;
}
