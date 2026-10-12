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
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Bug #77866: a scope that holds its own var stores (one per engine) must keep each engine's
 * vars apart when pooled engines run scripts on one shared scope on different threads, must
 * be collectable when it sits under a long-lived scope whose store the engine holds (a calc
 * table under its assembly's scriptable), and must not keep a closed engine alive.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class GraalJavaScriptEngineScopeLocalsConcurrencyTest {
   @BeforeEach void setup() {
      box = mock(AssetQuerySandbox.class);
      doReturn(new Worksheet()).when(box).getWorksheet();
      doReturn(new VariableTable()).when(box).getVariableTable();
   }

   // pooled slots run scripts on one shared scope at the same time, each with its own context
   @Test void enginesSharingAScopeOnDifferentThreadsKeepTheirOwnVars() throws Exception {
      AssetQueryScope shared = new AssetQueryScope(box);
      List<GraalJavaScriptEngine> engines = new ArrayList<>();
      List<List<WeakReference<ScriptScope>>> views = new ArrayList<>();
      ExecutorService pool = Executors.newFixedThreadPool(THREADS);

      try {
         List<Future<?>> runs = new ArrayList<>();
         CountDownLatch start = new CountDownLatch(1);

         for(int t = 0; t < THREADS; t++) {
            GraalJavaScriptEngine engine = new GraalJavaScriptEngine();
            engine.init(new HashMap<>());
            engines.add(engine);
            List<WeakReference<ScriptScope>> refs = new ArrayList<>();
            views.add(refs);
            String tag = "e" + t;

            runs.add(pool.submit(() -> {
               start.await();
               Object count = engine.compile(
                  "var zzTag = zzTag || '" + tag + "'; " +
                  "var zzN = (typeof zzN === 'undefined' ? 0 : zzN) + 1; zzTag + ':' + zzN");
               Object view = engine.compile("var ws = worksheet; 1");

               for(int i = 1; i <= RUNS; i++) {
                  assertEquals(tag + ":" + i, engine.exec(count, shared, null));
                  AssetQueryScope v =
                     shared.queryView(new VariableTable(), AssetQuerySandbox.RUNTIME_MODE);
                  engine.exec(view, v, null);
                  refs.add(new WeakReference<>(v));
               }

               return null;
            }));
         }

         start.countDown();

         for(Future<?> run : runs) {
            run.get(2, TimeUnit.MINUTES);
         }

         for(int t = 0; t < THREADS; t++) {
            GraalJavaScriptEngine engine = engines.get(t);
            assertEquals("e" + t + ":" + RUNS, engine.exec(engine.compile("zzTag + ':' + zzN"),
                                                          shared, null));
            assertNotNull(shared.getScopeLocals().get(engine));
            flush(engine);
         }

         for(List<WeakReference<ScriptScope>> refs : views) {
            assertEquals(0, alive(refs), "views kept alive by their var stores");
         }

         engines.get(0).close();
         assertNull(shared.getScopeLocals().get(engines.get(0)));
         GraalJavaScriptEngine last = engines.get(THREADS - 1);
         assertEquals("e" + (THREADS - 1) + ":" + RUNS,
                      last.exec(last.compile("zzTag + ':' + zzN"), shared, null),
                      "closing one engine leaves the other engines' stores");
      }
      finally {
         pool.shutdownNow();
         engines.forEach(GraalJavaScriptEngine::close);
      }
   }

   // a calc table scope under its assembly's scriptable, whose store the engine holds
   @Test void aCalcTableScopeUnderAnEngineHeldStoreIsReleased() throws Exception {
      GraalJavaScriptEngine engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());

      try {
         Field tableScope = CalcTableLens.class.getDeclaredField("tableScope");
         tableScope.setAccessible(true);
         PlainScope rscope = new PlainScope(null);
         PlainScope sheet = new PlainScope(rscope);
         PlainScope assembly = new PlainScope(sheet);
         engine.exec(engine.compile("var zzRate = 2;"), assembly, rscope);

         Supplier<CalcTableScope> scopes = () -> {
            CalcTableLens lens = new CalcTableLens(new Object[][] { { "a" }, { 1 } });
            CalcTableScope scope = new CalcTableScope(lens);
            scope.setParentScope(assembly);

            try {
               tableScope.set(lens, scope); // as CalcTableLens.evaluate does
            }
            catch(IllegalAccessException ex) {
               throw new IllegalStateException(ex);
            }

            return scope;
         };

         Object script = engine.compile("var f = field; var zzOwn = zzRate * 3; zzOwn");
         List<WeakReference<ScriptScope>> refs = new ArrayList<>();

         for(int i = 0; i < SCOPES; i++) {
            CalcTableScope scope = scopes.get();
            assertEquals(6, ((Number) engine.exec(script, scope, rscope)).intValue(),
                         "reads its parent store");
            refs.add(new WeakReference<>(scope));
         }

         assertEquals("undefined", engine.exec(engine.compile("typeof zzOwn"), assembly, rscope),
                      "a child's var stays out of its parent's store");
         flush(engine);
         assertEquals(0, alive(refs), "calc table scopes kept alive by their var stores");
         assertEquals(2, ((Number) engine.exec(engine.compile("zzRate"), assembly, rscope))
            .intValue(), "the parent keeps its var");
      }
      finally {
         engine.close();
      }
   }

   // a long-lived scope must not keep a closed engine (and its context) alive
   @Test void aLiveScopeDoesNotKeepAClosedEngine() throws Exception {
      AssetQueryScope shared = new AssetQueryScope(box);
      WeakReference<GraalJavaScriptEngine> ref = runAndClose(shared);
      long alive = alive(List.of(ref));

      assertEquals(0, alive, "the closed engine is kept by the scope's var store");
      assertNotNull(shared.getScopeLocals());
   }

   private WeakReference<GraalJavaScriptEngine> runAndClose(AssetQueryScope shared)
      throws Exception
   {
      GraalJavaScriptEngine engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
      engine.exec(engine.compile("var p = parameter; var zz = 1; zz"), shared, null);
      assertNotNull(shared.getScopeLocals().get(engine));
      engine.close();
      assertNull(shared.getScopeLocals().get(engine));
      return new WeakReference<>(engine);
   }

   // the binding root keeps the last run's root until the next run
   private static void flush(GraalJavaScriptEngine engine) throws Exception {
      engine.exec(engine.compile("1"), new PlainScope(null), null);
   }

   private static long alive(List<? extends WeakReference<?>> refs) throws Exception {
      long alive = refs.size();

      for(int i = 0; i < 100 && alive > 0; i++) {
         System.gc();
         Thread.sleep(20);
         alive = refs.stream().filter(r -> r.get() != null).count();
      }

      return alive;
   }

   private static final class PlainScope implements ScriptScope {
      PlainScope(ScriptScope parent) {
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

   private static final int THREADS = 3;
   private static final int RUNS = 200;
   private static final int SCOPES = 300;
   private AssetQuerySandbox box;
}
