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

import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77331: whether a split formula runs as the eval wrapper is decided when it runs,
 * against the host globals of the Context running it, not of the one that compiled it.
 */
@Tag("core")
class GraalJavaScriptEnginePieceHostGlobalExecTest {
   @BeforeEach void setup() throws Exception {
      engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
   }

   @AfterEach void teardown() {
      engine.close();
   }

   // a cached script compiled where the name is not a global runs on an engine where it is
   @Test void aPutNameOfTheRunningEngineStartsUndefinedOnEveryRun() throws Exception {
      Object script = engine.compile(SCRIPT);
      assertInstanceOf(GraalJavaScriptEngine.PieceScript.class, script);
      assertEquals(EXPECTED, runs(engine, script));

      GraalJavaScriptEngine other = new GraalJavaScriptEngine();

      try {
         other.init(new HashMap<>());
         other.put("zzexec77331", 1);
         assertEquals(EXPECTED, runs(other, script));
      }
      finally {
         other.close();
      }
   }

   // a name put after the compile
   @Test void aNamePutAfterCompileStartsUndefinedOnEveryRun() throws Exception {
      Object script = engine.compile(SCRIPT);
      assertEquals(EXPECTED, runs(engine, script));
      engine.put("zzexec77331", 1);
      assertEquals(EXPECTED, runs(engine, script));
   }

   // #75596: the value is still visible to a later script, as with the eval wrapper
   @Test void aVarNamedLikeAnEngineFunctionIsVisibleToALaterScript() throws Exception {
      MapScope scope = new MapScope();
      engine.exec(engine.compile("var count; if(true) count = 3;"), scope, scope);
      assertEquals(3.0, num(engine.exec(engine.compile("count"), scope, scope)));
   }

   // an env variable of a pooled worksheet context is a host global of that context
   @Test void anEnvVariableOfAPooledContextStartsUndefinedOnEveryRun() throws Exception {
      WorksheetScriptEnv env = PoolTestSupport.env();
      env.put("zzexec77331", 1);
      Object script = env.compile(SCRIPT);
      List<Object> results = new ArrayList<>();

      for(int[] ab : RUNS) {
         MapScope scope = scope(ab);
         results.add(num(env.exec(script, scope, scope, null)));
      }

      assertEquals(EXPECTED, results);
   }

   private static List<Object> runs(GraalJavaScriptEngine engine, Object script)
      throws Exception
   {
      List<Object> results = new ArrayList<>();

      for(int[] ab : RUNS) {
         MapScope scope = scope(ab);
         results.add(num(engine.exec(script, scope, scope)));
      }

      return results;
   }

   private static MapScope scope(int[] ab) {
      MapScope scope = new MapScope();
      scope.putMember("a", ab[0]);
      scope.putMember("b", ab[1]);
      return scope;
   }

   private static Object num(Object v) {
      return v instanceof Number n ? n.doubleValue() : v;
   }

   private static final class MapScope implements ScriptScope {
      public Object getMember(String n) { return m.get(n); }
      public boolean hasMember(String n) { return m.containsKey(n); }
      public void putMember(String n, Object v) { m.put(n, v); }
      public Object[] getMemberKeys() { return m.keySet().toArray(); }
      private final Map<String, Object> m = new HashMap<>();
   }

   private static final String SCRIPT =
      "var zzexec77331; if(a > b) zzexec77331 = a; zzexec77331";
   private static final int[][] RUNS = { { 1, 5 }, { 9, 2 }, { 1, 5 }, { 1, 5 } };
   private static final List<Object> EXPECTED = Arrays.asList(null, 9.0, null, null);
   private GraalJavaScriptEngine engine;
}
