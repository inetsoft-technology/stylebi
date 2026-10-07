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

import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.script.formula.AssetQueryScope;
import inetsoft.report.script.formula.CalcTableScope;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.Worksheet;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Bug #77866: the top-level var store of a scope (#77595) must not keep the scope alive when
 * a var of it refers back to the scope. The engine held the stores in a map weakly keyed by
 * the scope, whose values strongly reached their keys, so every scope made per evaluation
 * (a pooled query view, a condition scope, a calc table scope) stayed as long as the engine.
 * A scope with a {@link ScopeLocals} now holds its own store.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class GraalJavaScriptEngineScopeLocalsTest {
   @BeforeEach void setup() throws Exception {
      engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
      rscope = new OwnerScope(null);
      mid = new OwnerScope(rscope);
   }

   @AfterEach void teardown() {
      engine.close();
   }

   // every compile path, with the scope itself and with a member bean that refers back to it
   @Test void aVarReferringToItsOwnScopeNoLongerKeepsTheScope() throws Exception {
      assertReleased("var self = me; 1", () -> new OwnerScope(mid));
      assertReleased("let self = me; 1", () -> new OwnerScope(mid));
      assertReleased("var self = me; this; 1", () -> new OwnerScope(mid));
      assertReleased("var h = holder; 1", () -> new OwnerScope(mid));
   }

   @Test void aScopeWithAPlainVarIsReleased() throws Exception {
      assertReleased("var x = 1; 1", () -> new OwnerScope(mid));
      assertReleased("var x = 1; 1", () -> new PlainScope(mid));
   }

   // a pooled worksheet condition (PreAssetQuery): a view answers worksheet with itself
   @Test void aQueryViewHoldingWorksheetIsReleased() throws Exception {
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      doReturn(new Worksheet()).when(box).getWorksheet();
      AssetQueryScope shared = new AssetQueryScope(box);

      assertReleased("var ws = worksheet; 1",
                     () -> shared.queryView(new VariableTable(), AssetQuerySandbox.RUNTIME_MODE),
                     null);
      assertNotNull(shared.getScopeLocals());
      assertNotSame(shared.getScopeLocals(), ((AssetQueryScope) shared.clone()).getScopeLocals());
   }

   // a calc table formula: field is a row of the lens, which holds the table scope
   @Test void aCalcTableScopeHoldingFieldIsReleased() throws Exception {
      Field tableScope = CalcTableLens.class.getDeclaredField("tableScope");
      tableScope.setAccessible(true);

      assertReleased("var f = field; 1", () -> {
         CalcTableLens lens = new CalcTableLens(new Object[][] { { "a" }, { 1 } });
         CalcTableScope scope = new CalcTableScope(lens);

         try {
            tableScope.set(lens, scope); // as CalcTableLens.evaluate does
         }
         catch(IllegalAccessException ex) {
            throw new IllegalStateException(ex);
         }

         return scope;
      }, null);

      CalcTableScope scope = new CalcTableScope(new CalcTableLens(new Object[][] { { "a" } }));
      assertNotNull(scope.getScopeLocals());
      assertNotSame(scope.getScopeLocals(), scope.clone().getScopeLocals());
   }

   // #77595 semantics, kept for a scope that holds its own store
   @Test void aVarKeepsItsValueAcrossRunsOfItsScope() throws Exception {
      OwnerScope table1 = new OwnerScope(mid);
      Object script = engine.compile("var zzc = (zzc || 0) + 1; zzc");

      assertEquals(1.0, exec(script, table1));
      assertEquals(2.0, exec(script, table1));
      assertEquals(1.0, exec(script, new OwnerScope(mid)), "another scope");
      assertEquals(3.0, exec(script, table1));
   }

   @Test void childStoresReadTheVarsOfTheirParentStores() throws Exception {
      OwnerScope table1 = new OwnerScope(mid);
      OwnerScope cells = new OwnerScope(table1);
      PlainScope plainCells = new PlainScope(table1);

      run("var zzOwn = 1;", cells); // linked to its parent's store when that is made
      run("var zzOwn = 1;", plainCells);
      run("var zzRate = 2;", table1);
      assertEquals(2.0, run("zzRate", cells));
      assertEquals(2.0, run("zzRate", plainCells));
      run("var zzRate = 10;", cells);
      assertEquals(10.0, run("zzRate", cells));
      assertEquals(2.0, run("zzRate", table1));
      assertEquals("undefined", run("typeof zzRate", new OwnerScope(mid)));
   }

   @Test void aFunctionSeesTheVarsOfTheScopeItWasDeclaredIn() throws Exception {
      OwnerScope table1 = new OwnerScope(mid);
      OwnerScope table2 = new OwnerScope(mid);

      run("var zzk = 'table1'; function zzF() { return zzk; }", table1);
      run("var zzk = 'table2';", table2);
      assertEquals("table1", run("zzF()", table2));
   }

   // each engine (a pooled context) keeps its own store of a shared scope
   @Test void twoEnginesKeepTheirOwnStoresOfOneScope() throws Exception {
      GraalJavaScriptEngine other = new GraalJavaScriptEngine();
      other.init(new HashMap<>());

      try {
         OwnerScope table1 = new OwnerScope(mid);
         engine.exec(engine.compile("var zzv = 'one';"), table1, rscope);
         other.exec(other.compile("var zzv = 'two';"), table1, rscope);

         assertEquals("one", engine.exec(engine.compile("zzv"), table1, rscope));
         assertEquals("two", other.exec(other.compile("zzv"), table1, rscope));
         other.close();
         assertNull(table1.getScopeLocals().get(other), "a closed engine's store is dropped");
         assertNotNull(table1.getScopeLocals().get(engine));
         assertEquals("one", engine.exec(engine.compile("zzv"), table1, rscope));
      }
      finally {
         other.close();
      }
   }

   @Test void initDropsTheStoresOfTheOldContext() throws Exception {
      OwnerScope table1 = new OwnerScope(mid);
      String script = "var zzc = (zzc || 0) + 1; zzc";

      assertEquals(1.0, run(script, table1));
      assertEquals(2.0, run(script, table1));
      engine.init(new HashMap<>());
      assertNull(table1.getScopeLocals().get(engine));
      assertEquals(1.0, run(script, table1));
   }

   private void assertReleased(String script, Supplier<ScriptScope> scopes) throws Exception {
      assertReleased(script, scopes, rscope);
   }

   private void assertReleased(String script, Supplier<ScriptScope> scopes, Object rscope)
      throws Exception
   {
      Object compiled = engine.compile(script);
      List<WeakReference<ScriptScope>> refs = new ArrayList<>();

      for(int i = 0; i < SCOPES; i++) {
         ScriptScope scope = scopes.get();
         engine.exec(compiled, scope, rscope);
         refs.add(new WeakReference<>(scope));
      }

      // the binding root keeps the last run's root until the next run
      engine.exec(engine.compile("1"), new PlainScope(null), null);
      long alive = refs.size();

      for(int i = 0; i < 100 && alive > 0; i++) {
         System.gc();
         Thread.sleep(20);
         alive = refs.stream().filter(r -> r.get() != null).count();
      }

      assertEquals(0, alive, script + ": scopes kept alive by their var stores");
   }

   private Object run(String script, ScriptScope scope) throws Exception {
      return exec(engine.compile(script), scope);
   }

   private Object exec(Object script, ScriptScope scope) throws Exception {
      Object v = engine.exec(script, scope, rscope);
      return v instanceof Number n ? n.doubleValue() : v;
   }

   private static class PlainScope implements ScriptScope {
      PlainScope(ScriptScope parent) {
         this.parent = parent;
         m.put("me", this);
         m.put("holder", new Holder(this));
      }

      public Object getMember(String n) { return m.get(n); }
      public boolean hasMember(String n) { return m.containsKey(n); }
      public void putMember(String n, Object v) { m.put(n, v); }
      public Object[] getMemberKeys() { return m.keySet().toArray(); }
      public ScriptScope getParentScope() { return parent; }
      private final Map<String, Object> m = new HashMap<>();
      private final ScriptScope parent;
   }

   private static final class OwnerScope extends PlainScope {
      OwnerScope(ScriptScope parent) {
         super(parent);
      }

      @Override
      public ScopeLocals getScopeLocals() { return locals; }
      private final ScopeLocals locals = new ScopeLocals();
   }

   public static final class Holder {
      Holder(Object owner) { this.owner = owner; }
      public Object getOwner() { return owner; }
      private final Object owner;
   }

   private static final int SCOPES = 300;
   private GraalJavaScriptEngine engine;
   private OwnerScope rscope;
   private OwnerScope mid;
}
