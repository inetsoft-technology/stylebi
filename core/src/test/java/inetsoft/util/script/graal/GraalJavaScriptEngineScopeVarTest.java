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
 * Bug #77595: as in Rhino, a script's top-level var belongs to the scope it runs in. An
 * assembly script's var shadows an onInit or onLoad variable of the same name for that
 * assembly only, never replacing it for the other scripts of the sheet.
 *
 * <p>The scopes mirror a viewsheet: onInit runs on the viewsheet scope ({@code vs}, also the
 * rscope), onLoad on {@code thisViewsheet} (a child of it), an assembly script on the
 * assembly (a child of {@code thisViewsheet}) and a calc table cell on the table scope (a
 * child of its assembly).
 */
@Tag("core")
class GraalJavaScriptEngineScopeVarTest {
   @BeforeEach void setup() throws Exception {
      engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
      vs = new ChainScope(null);
      thisViewsheet = new ChainScope(vs);
   }

   @AfterEach void teardown() {
      engine.close();
   }

   // every compile path: one piece, two pieces (#77249) with and without an initializer,
   // a let without an initializer (#77181), and the this eval wrapper (#75596 hoist)
   @ParameterizedTest
   @ValueSource(strings = {
      "var zzn = 3; zzn",
      "var zzn; if(false) { zzn = 1; } zzn",
      "var zzn = 3; if(false) { zzn = 1; } zzn",
      "let zzn; zzn",
      "var zzn = 3; this; zzn",
      "var zzn = 3; this; if(false) { zzn = 1; } zzn"
   })
   void anAssemblyVarNeverReplacesTheOnInitVariable(String script) throws Exception {
      run("var zzn = 5;", vs);
      ChainScope table1 = new ChainScope(thisViewsheet);
      Object own = run(script, table1);

      assertEquals(script.contains("= 3") ? 3.0 : null, own, "the script's own value");
      assertEquals(5.0, run("zzn", new ChainScope(thisViewsheet)), "another assembly");
      assertEquals(5.0, run("zzn", vs), "onInit's scope");
   }

   @Test void onLoadVariablesAreSeenByEveryAssembly() throws Exception {
      run("var zzLoad = 7;", thisViewsheet);
      assertEquals(7.0, run("zzLoad", new ChainScope(thisViewsheet)));
   }

   @Test void anAssemblyVarKeepsItsValueAcrossRunsOfItsScope() throws Exception {
      ChainScope table1 = new ChainScope(thisViewsheet);
      Object script = engine.compile("var zzc = (zzc || 0) + 1; zzc");

      assertEquals(1.0, exec(script, table1));
      assertEquals(2.0, exec(script, table1));
      assertEquals(1.0, exec(script, new ChainScope(thisViewsheet)), "another assembly");
      assertEquals(3.0, exec(script, table1));
   }

   @Test void twoAssembliesKeepTheirOwnVarOfOneName() throws Exception {
      ChainScope table1 = new ChainScope(thisViewsheet);
      ChainScope table2 = new ChainScope(thisViewsheet);

      run("var zzv = 'one';", table1);
      run("var zzv = 'two';", table2);

      assertEquals("one", run("zzv", table1));
      assertEquals("two", run("zzv", table2));
   }

   // a calc table cell runs on the table scope, a child of its assembly
   @Test void aCellReadsTheVarsOfItsAssemblyScript() throws Exception {
      ChainScope table1 = new ChainScope(thisViewsheet);
      ChainScope cells = new ChainScope(table1);

      run("var zzRate = 2;", table1);
      assertEquals(6.0, run("zzRate * 3", cells));
      // a cell's own var shadows it for the cells only
      run("var zzRate = 10;", cells);
      assertEquals(10.0, run("zzRate", cells));
      assertEquals(2.0, run("zzRate", table1));
   }

   // the cell scope ran first, so its store is linked to its assembly's when that is made
   @Test void aCellSeesAnAssemblyVarDeclaredAfterTheCellsFirstRun() throws Exception {
      ChainScope table1 = new ChainScope(thisViewsheet);
      ChainScope cells = new ChainScope(table1);

      run("var zzOwn = 1;", cells);
      run("var zzRate = 2;", table1);
      assertEquals(2.0, run("zzRate", cells));
   }

   // an unqualified assignment still writes the variable it names, as through Rhino's chain
   @Test void anAssignmentWithoutVarUpdatesTheOnInitVariable() throws Exception {
      run("var zzn = 5;", vs);
      run("zzn = 9;", new ChainScope(thisViewsheet));
      assertEquals(9.0, run("zzn", new ChainScope(thisViewsheet)));
   }

   @Test void anAssemblyVarNamedLikeAnEngineFunctionLeavesTheFunction() throws Exception {
      ChainScope table1 = new ChainScope(thisViewsheet);
      Object before = run("max([1, 4, 2])", vs);

      assertEquals(3.0, run("var max = 3; max", table1));
      assertEquals("function", run("typeof max", new ChainScope(thisViewsheet)));
      assertEquals(before, run("max([1, 4, 2])", vs));
   }

   // a function closes over the vars of the scope it was declared in, as in Rhino
   @Test void aFunctionSeesTheVarsOfTheScopeItWasDeclaredIn() throws Exception {
      ChainScope table1 = new ChainScope(thisViewsheet);
      ChainScope table2 = new ChainScope(thisViewsheet);

      run("var zzk = 'table1'; function zzF() { return zzk; }", table1);
      run("var zzk = 'table2';", table2);
      assertEquals("table1", run("zzF()", table2));
   }

   // onInit and onLoad functions and vars are seen by assembly scripts (#75596)
   @Test void onInitDeclarationsAreSeenByAssemblyScripts() throws Exception {
      run("var zzBase = 100; function zzAdd(x) { return zzBase + x; }", vs);
      assertEquals(105.0, run("zzAdd(5)", new ChainScope(thisViewsheet)));
      assertEquals(105.0, run("this; zzAdd(5)", new ChainScope(thisViewsheet)));
   }

   private Object run(String script, ChainScope scope) throws Exception {
      return exec(engine.compile(script), scope);
   }

   private Object exec(Object script, ChainScope scope) throws Exception {
      Object v = engine.exec(script, scope, vs);
      return v instanceof Number n ? n.doubleValue() : v;
   }

   private static final class ChainScope implements ScriptScope {
      ChainScope(ScriptScope parent) {
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

   private GraalJavaScriptEngine engine;
   private ChainScope vs;
   private ChainScope thisViewsheet;
}
