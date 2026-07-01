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

   private static ScriptScope makeParam(String k, String v) {
      MapScope p = new MapScope();
      p.putMember(k, v);
      return p;
   }
}
