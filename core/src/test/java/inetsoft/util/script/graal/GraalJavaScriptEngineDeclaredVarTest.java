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
 * Bug #78247: for a script compiled by {@code compileDeclaredVars} (a freehand table cell
 * formula) on a {@link DeclaredVarScope} root, the script's own top-level var wins over a
 * same-named member of the scope chain (a parent scope's {@code value} here), in every
 * compile shape; only the names the running script declares are hidden, so a function
 * declared by another script still reads the member. On any other root, and for a script
 * compiled by {@code compile}, the member still wins, as in Rhino.
 */
@Tag("core")
class GraalJavaScriptEngineDeclaredVarTest {
   @BeforeEach void setup() throws Exception {
      engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
      sheet = new MapScope(null);
      assembly = new MapScope(sheet);
      assembly.putMember("value", "assembly");
      assembly.putMember("member78247", "assembly");
   }

   @AfterEach void teardown() {
      engine.close();
   }

   // the plain path, the split path, the split path run as the eval wrapper (value is a
   // CALC global, Bug #77331), and the two eval wrappers of a body with this
   @ParameterizedTest
   @ValueSource(strings = {
      "var member78247 = on ? 5 : undefined; member78247",
      "var member78247; if(on) member78247 = 5; member78247",
      "var value; if(on) value = 5; value",
      "var value; if(on) value = 5; this ? value : 0",
      "var value = on ? 5 : undefined; this ? value : 0",
   })
   void aDeclaredVarWinsOverAParentMember(String script) throws Exception {
      Object compiled = engine.compileDeclaredVars(script);
      assertEquals(Arrays.asList(null, 5.0, null), runs(compiled, cell()), script);
      assertEquals("assembly", assembly.getMember("value"), "the member is not replaced");
      assertEquals("assembly", assembly.getMember("member78247"),
                   "the member is not replaced");
   }

   // the member is still read on another root, as in Rhino
   @Test void theMemberWinsOnAnotherRoot() throws Exception {
      MapScope cell = new MapScope(assembly);
      assertEquals("assembly", engine.exec(engine.compileDeclaredVars("var value; value"), cell, null));
   }

   // a function declared by another script reads the member, not the caller's var
   @Test void aFunctionDeclaredElsewhereReadsTheMember() throws Exception {
      engine.exec(engine.compile("function readValue78247() { return value; }"), sheet, sheet);
      Object script = engine.compileDeclaredVars("var value = 5; [value, readValue78247()]");
      Object result = engine.exec(script, cell(), null);

      assertInstanceOf(Object[].class, result, String.valueOf(result));
      Object[] values = (Object[]) result;
      assertEquals(5.0, ((Number) values[0]).doubleValue());
      assertEquals("assembly", values[1]);
   }

   // a name the script does not declare still reads the member
   @Test void anUndeclaredNameReadsTheMember() throws Exception {
      assertEquals("assembly",
                   engine.exec(engine.compileDeclaredVars("var x = 1; value"), cell(), null));
   }

   // any other script keeps its source, and the member wins even on such a root
   @Test void anOrdinaryCompileIsUnchanged() throws Exception {
      Object script = engine.compile("var value; value");
      assertFalse(String.valueOf(((org.graalvm.polyglot.Source) script).getCharacters())
                     .contains(BindingRootProxy.DECLARED_VARS_MEMBER), String.valueOf(script));
      assertEquals("assembly", engine.exec(script, cell(), null));
   }

   private List<Object> runs(Object script, CellScope cell) throws Exception {
      List<Object> results = new ArrayList<>();

      for(boolean on : new boolean[] { false, true, false }) {
         cell.putMember("on", on);
         Object v = engine.exec(script, cell, null);
         results.add(v instanceof Number n ? n.doubleValue() : v);
      }

      return results;
   }

   private CellScope cell() {
      return new CellScope(assembly);
   }

   private static class MapScope implements ScriptScope {
      MapScope(ScriptScope parent) {
         this.parent = parent;
      }

      public Object getMember(String n) { return m.get(n); }
      public boolean hasMember(String n) { return m.containsKey(n); }
      public void putMember(String n, Object v) { m.put(n, v); }
      public Object[] getMemberKeys() { return m.keySet().toArray(); }
      public ScriptScope getParentScope() { return parent; }
      private final Map<String, Object> m = new HashMap<>();
      private final ScriptScope parent;
   }

   private static final class CellScope extends MapScope implements DeclaredVarScope {
      CellScope(ScriptScope parent) {
         super(parent);
      }
   }

   private GraalJavaScriptEngine engine;
   private MapScope sheet;
   private MapScope assembly;
}
