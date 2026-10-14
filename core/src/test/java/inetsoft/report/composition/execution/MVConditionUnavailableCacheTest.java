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
package inetsoft.report.composition.execution;

import inetsoft.mv.*;
import inetsoft.mv.data.*;
import inetsoft.report.TableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.script.graal.ScriptTimeoutGuard;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import inetsoft.util.stall.LockStallException;
import inetsoft.util.swap.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78450: a condition expression on a materialized view whose script reads worksheet table
 * L, whose data is not available (a lost swap file or a lock stall) or which failed to load.
 * {@code MVJob.init} replaced the failure by an exception carrying only its message, and the
 * {@code MVExecutionException} arm of {@code ViewsheetSandbox.getData} cached NULL for it, and
 * with {@code mv.ondemand} recreated the healthy MV and returned no data without an error. The
 * failure is built by the real condition script, MV job and MV query, and must reach the reader
 * of the viewsheet with nothing cached and no MV recreated, in both on-demand modes. The same
 * holds for a failure of the MV job itself, which the job pool wraps, and for a stop of the
 * job. An ordinary script error is still cached as NULL (and still recreates the MV on demand).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  ConditionValueSwapLostTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVConditionUnavailableCacheTest {
   @BeforeEach
   void setUp() {
      lost = new LostSwapFile();
      MVManager mgr = MVManager.getManager();
      Mockito.reset(mgr);
      Mockito.when(mgr.list(Mockito.anyBoolean(), Mockito.any())).thenReturn(new MVDef[0]);
   }

   @AfterEach
   void tearDown() {
      SreeEnv.remove(PoolConfig.ENABLED);
      SreeEnv.remove("mv.ondemand");

      for(AssetQuerySandbox box : boxes) {
         if(box.peekScriptEnv() instanceof WorksheetScriptEnv env) {
            env.retire();
         }
      }

      boxes.clear();
      lost.close();
   }

   /**
    * Unavailable data read by the condition script, through MVJob.init and the MV query.
    */
   @ParameterizedTest(name = "{0} pool={1} ondemand={2}")
   @CsvSource({
      "swap, false, false", "swap, false, true", "swap, true, false", "swap, true, true",
      "stall, false, false", "stall, false, true", "stall, true, false", "stall, true, true",
      "load, false, false", "load, false, true", "load, true, false", "load, true, true"
   })
   void unavailableDataInTheConditionIsNotCachedAndKeepsTheMV(String kind, boolean pool,
                                                              boolean ondemand)
      throws Exception
   {
      Throwable failure = viaMVQuery(viaJobInit(conditionScript(kind, pool)));
      assertNotNull(found(failure), "the MV query lost the failure: " + failure);
      assertNotCachedAndKeepsTheMV(failure, ondemand);
   }

   /**
    * A failure of the MV job, which XJobPool wraps as an MVExecutionException that keeps it.
    */
   @ParameterizedTest(name = "{0} ondemand={1}")
   @CsvSource({
      "swap, false", "swap, true", "stall, false", "stall, true", "load, false", "load, true",
      "stop, false", "stop, true"
   })
   void unavailableDataInTheJobIsNotCachedAndKeepsTheMV(String kind, boolean ondemand)
      throws Exception
   {
      Throwable inner = switch(kind) {
         case "swap" -> swapFailure();
         case "stall" -> new LockStallException("b78450-site", "t", 1000, null);
         case "load" -> new TableLoadException("b78450 load failed", null);
         default -> new InterruptedException();
      };
      Throwable failure = viaMVQuery(new MVExecutionException(inner));

      if("stop".equals(kind)) {
         assertTrue(ScriptTimeoutGuard.isStop(failure), "stop lost: " + failure);
      }
      else {
         assertNotNull(found(failure), "the MV query lost the failure: " + failure);
      }

      assertNotCachedAndKeepsTheMV(failure, ondemand);
   }

   /**
    * Unchanged: an ordinary error of the condition script is cached as NULL, and recreates the
    * MV on demand.
    */
   @ParameterizedTest(name = "ondemand={0}")
   @ValueSource(booleans = { false, true })
   void ordinaryConditionErrorIsStillCached(boolean ondemand) throws Exception {
      Throwable failure = viaMVQuery(viaJobInit(conditionScript("ordinary", false)));
      assertInstanceOf(MVExecutionException.class, failure);
      assertNull(found(failure));
      assertFalse(ScriptTimeoutGuard.isStop(failure));

      ViewsheetSandbox box = sandbox(ondemand);
      VSAQuery query = Mockito.mock(VSAQuery.class);
      Mockito.when(query.getData()).thenThrow(failure).thenReturn(42);

      try(MockedStatic<VSAQuery> st = mockQuery(query)) {
         if(ondemand) {
            assertNull(box.getData("Text1"));
         }
         else {
            assertSame(failure, assertThrows(MVExecutionException.class,
                                             () -> box.getData("Text1")));
         }

         assertEquals("__null__", dmap(box).get("Text1", DataMap.NORMAL));
         assertNull(box.getData("Text1"));
         Mockito.verify(query, Mockito.times(1)).getData();
         Mockito.verify(MVManager.getManager(), Mockito.times(ondemand ? 1 : 0))
            .findRuntimeMV(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                           Mockito.any(), Mockito.any(), Mockito.anyBoolean());
      }
   }

   /**
    * Read 1 fails with {@code failure}, nothing is cached and the MV is not recreated; read 2
    * runs the query again and gets the data.
    */
   private void assertNotCachedAndKeepsTheMV(Throwable failure, boolean ondemand)
      throws Exception
   {
      ViewsheetSandbox box = sandbox(ondemand);
      VSAQuery query = Mockito.mock(VSAQuery.class);
      Mockito.when(query.getData()).thenThrow(failure).thenReturn(42);

      try(MockedStatic<VSAQuery> st = mockQuery(query)) {
         assertSame(failure, assertThrows(Exception.class, () -> box.getData("Text1")),
                    "getData() returned no data for a failed MV query");
         assertNull(dmap(box).get("Text1", DataMap.NORMAL),
                    "getData() cached a NULL result for unavailable data in an MV query");
         Mockito.verify(MVManager.getManager(), Mockito.never())
            .findRuntimeMV(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                           Mockito.any(), Mockito.any(), Mockito.anyBoolean());

         assertEquals(42, box.getData("Text1"));
         Mockito.verify(query, Mockito.times(2)).getData();
      }
   }

   /**
    * What the real {@code MVConditionListHandler.executeScript} throws for a condition value
    * script reading L: a lost swap file, a lock stall, a load failure, or an ordinary error.
    */
   private Throwable conditionScript(String kind, boolean pool) throws Exception {
      SreeEnv.setProperty(PoolConfig.ENABLED, pool + "");
      Worksheet ws = worksheet();
      LBox box = new LBox(ws, kind);
      boxes.add(box);
      Field spm = AssetQuerySandbox.class.getDeclaredField("scriptPoolMode");
      spm.setAccessible(true);
      spm.set(box, pool);
      MVConditionListHandler handler = new MVConditionListHandler(
         (TableAssembly) ws.getAssembly("S"), new MV(), new VariableTable(), new MVDef());
      Field f = MVConditionListHandler.class.getDeclaredField("box");
      f.setAccessible(true);
      f.set(handler, box);
      Condition cond = new Condition();
      cond.setOperation(XCondition.GREATER_THAN);
      ExpressionValue ev = new ExpressionValue();
      ev.setType(ExpressionValue.JAVASCRIPT);
      String exp = "ordinary".equals(kind) ? "noSuchVar78450.foo + 1" : "L.length > 0 ? 5 : 0";
      ev.setExpression(exp + " /*b78450mv_" + (++nonce) + "*/");
      cond.addValue(ev);

      Exception ex = assertThrows(Exception.class, () -> handler.executeScript(cond, ev, 0, null));
      assertEquals("ordinary".equals(kind), found(ex) == null, "condition script threw " + ex);
      return ex;
   }

   /**
    * What the real {@code MVJob.init} throws when its condition builder fails with
    * {@code failure}.
    */
   private static Throwable viaJobInit(Throwable failure) {
      RuntimeException rex = (RuntimeException) failure;

      try(MockedStatic<MVStorage> storage = Mockito.mockStatic(MVStorage.class);
          MockedConstruction<MVQueryBuilder> builder =
             Mockito.mockConstruction(MVQueryBuilder.class, (mock, ctx) ->
                Mockito.when(mock.getQuery()).thenThrow(rex)))
      {
         storage.when(() -> MVStorage.getFile(Mockito.anyString())).thenReturn("f");
         storage.when(MVStorage::getInstance).thenReturn(Mockito.mock(MVStorage.class));
         return assertThrows(Exception.class, () -> new MVJob(
            Mockito.mock(TableAssembly.class), "mv", new VariableTable(), null));
      }
   }

   /**
    * What the real {@code MVAssetQuery.getPostBaseTableLens} throws when its executor fails
    * with {@code failure}.
    */
   private static Throwable viaMVQuery(Throwable failure) throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "T");
      ws.addAssembly(table);
      MVAssetQuery query = Mockito.mock(MVAssetQuery.class, Mockito.CALLS_REAL_METHODS);
      set(MVAssetQuery.class, query, "table", table);
      set(PreAssetQuery.class, query, "box", Mockito.mock(AssetQuerySandbox.class));
      MVExecutor executor = Mockito.mock(MVExecutor.class);
      Mockito.when(executor.getData()).thenThrow(failure);

      try(MockedStatic<MVTool> tool = Mockito.mockStatic(MVTool.class)) {
         tool.when(() -> MVTool.newMVExecutor(Mockito.any(), Mockito.any(), Mockito.any(),
                                              Mockito.any())).thenReturn(executor);
         return assertThrows(Exception.class,
                             () -> ((AssetQuery) query).getPostBaseTableLens(new VariableTable()));
      }
   }

   private static Object found(Throwable ex) {
      Object found = DataUnavailable.find(ex);
      return found != null ? found : TableLoadException.find(ex);
   }

   private static SwapFileReadException swapFailure() {
      File file = new File("s78450_1.tdat");
      return new SwapFileReadException(file, new IOException("file not found: " + file));
   }

   private static Worksheet worksheet() {
      Worksheet ws = new Worksheet();
      Object[][] data = new Object[11][];
      data[0] = new Object[] { "id", "v" };

      for(int i = 1; i <= 10; i++) {
         data[i] = new Object[] { i, i };
      }

      EmbeddedTableAssembly s = new EmbeddedTableAssembly(ws, "S");
      s.setEmbeddedData(new XEmbeddedTable(new String[] { XSchema.INTEGER, XSchema.INTEGER }, data));
      ws.addAssembly(s);
      EmbeddedTableAssembly l = new EmbeddedTableAssembly(ws, "L");
      l.setEmbeddedData(new XEmbeddedTable(new String[] { XSchema.INTEGER },
                                           new Object[][] { { "x" }, { 1 }, { 2 } }));
      ws.addAssembly(l);
      return ws;
   }

   private static ViewsheetSandbox sandbox(boolean ondemand) {
      SreeEnv.setProperty("mv.ondemand", ondemand + "");
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setMVOnDemand(ondemand);
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                        AssetEntry.Type.VIEWSHEET, "MV78450", null);
      return new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
   }

   private static MockedStatic<VSAQuery> mockQuery(VSAQuery query) {
      MockedStatic<VSAQuery> st = Mockito.mockStatic(VSAQuery.class, Mockito.CALLS_REAL_METHODS);
      st.when(() -> VSAQuery.createVSAQuery(Mockito.any(), Mockito.any(), Mockito.anyInt()))
         .thenReturn(query);
      return st;
   }

   private static DataMap dmap(ViewsheetSandbox box) throws Exception {
      Field f = ViewsheetSandbox.class.getDeclaredField("dmap");
      f.setAccessible(true);
      return (DataMap) f.get(box);
   }

   private static void set(Class<?> type, Object target, String name, Object value)
      throws Exception
   {
      Field field = type.getDeclaredField(name);
      field.setAccessible(true);
      field.set(target, value);
   }

   /** A sandbox whose table L is lost, stalls or failed to load, as {@code kind} says. */
   private class LBox extends AssetQuerySandbox {
      LBox(Worksheet ws, String kind) {
         super(ws);
         this.kind = kind;
         table = new LostSwapFile.Table(new Object[][] { { "x" }, { 1 }, { 2 } }, 1, true, lost);
         table.lost = "swap".equals(kind);
      }

      @Override
      public TableLens getTableLens(String name, int mode, VariableTable vars) throws Exception {
         if("L".equals(name)) {
            if("stall".equals(kind)) {
               throw new LockStallException("b78450-site", "t", 1000, null);
            }

            if("load".equals(kind)) {
               throw new TableLoadException("b78450 load failed", null);
            }

            return table;
         }

         return super.getTableLens(name, mode, vars);
      }

      private final String kind;
      private final LostSwapFile.Table table;
   }

   private final List<AssetQuerySandbox> boxes = new ArrayList<>();
   private LostSwapFile lost;
   private int nonce;
}
