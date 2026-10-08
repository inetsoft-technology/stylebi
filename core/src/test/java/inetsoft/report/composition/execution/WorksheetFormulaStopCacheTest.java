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

import inetsoft.mv.MVManager;
import inetsoft.report.TableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.script.graal.ScriptStopTestSupport;
import inetsoft.util.script.graal.ScriptTimeoutGuard;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77949 end to end: a worksheet expression column is computed lazily, after its table
 * was cached (the sandbox's table map and the shared AssetDataCache). When a row of it is
 * stopped by the script timeout there, its reader fails with the stop, and so does every
 * later read of that table; but the caches do not hand that table to the next reader: a read
 * of the same sandbox, or of a new one (another session), computes the table again, with
 * fresh formula vars, and gets every value right. The timeout is real: the formula of one
 * row runs for 1.5 s, past a 1 s timeout, then within a 10 s one.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class, WorksheetFormulaStopCacheTest.TestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetFormulaStopCacheTest {
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      // a cached table is checked against its materialized view (none here)
      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      previousTimeout = ScriptStopTestSupport.setTimeout("1");
      AssetDataCache.getCache().clearCache();
   }

   @AfterEach
   void tearDown() throws Exception {
      Thread.interrupted();
      ScriptStopTestSupport.setTimeout(previousTimeout);
      SreeEnv.remove(PoolConfig.ENABLED);

      for(AssetQuerySandbox box : boxes) {
         if(box.peekScriptEnv() instanceof WorksheetScriptEnv env) {
            env.retire();
         }
      }

      boxes.clear();
      AssetDataCache.getCache().clearCache();
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void stoppedTableIsComputedAgainForTheNextReader(boolean pool) throws Exception {
      assertTimeoutPreemptively(Duration.ofSeconds(120), () -> {
         Worksheet ws = ws();
         AssetQuerySandbox box = box(ws, pool);
         TableLens t = box.getTableLens("A", RUNTIME);
         int out = col(t, "out");
         Throwable stop = null;

         for(int r = 1; r <= ROWS && stop == null; r++) {
            try {
               assertTrue(t.moreRows(r));
               t.getObject(r, out);
            }
            catch(Throwable ex) {
               stop = ex;
            }
         }

         assertNotNull(stop, "the timeout stopped a row");
         assertTrue(ScriptTimeoutGuard.isStop(stop), "the reader gets the stop: " + stop);
         assertTrue(AssetDataCache.isStopped(t));
         // the table handed out keeps failing at the stopped row
         assertTrue(ScriptTimeoutGuard.isStop(
            assertThrows(Throwable.class, () -> t.getObject(SLOW, out))));

         // the formula now ends within the timeout
         ScriptStopTestSupport.setTimeout("10");

         // another session: the shared cache does not hand it the stopped table
         AssetQuerySandbox other = box(ws, pool);
         TableLens t2 = other.getTableLens("A", RUNTIME);
         assertFalse(AssetDataCache.isStopped(t2));
         assertAll(t2, "a new sandbox");

         // the same sandbox: its table map does not hand it the stopped table either
         TableLens t3 = box.getTableLens("A", RUNTIME);
         assertFalse(AssetDataCache.isStopped(t3));
         assertAll(t3, "the same sandbox");
      });
   }

   // every row steps the Date once, from fresh vars: out == id
   private static void assertAll(TableLens t, String what) {
      int id = col(t, "id");
      int out = col(t, "out");

      for(int r = 1; r <= ROWS; r++) {
         assertTrue(t.moreRows(r), what + ", row " + r);
         int rowId = ((Number) t.getObject(r, id)).intValue();
         assertEquals((double) rowId, ((Number) t.getObject(r, out)).doubleValue(),
                      what + ", id " + rowId);
      }
   }

   private AssetQuerySandbox box(Worksheet ws, boolean pool) {
      SreeEnv.setProperty(PoolConfig.ENABLED, Boolean.toString(pool));
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      boxes.add(box);
      assertEquals(pool, box.isScriptPoolMode());
      return box;
   }

   private static Worksheet ws() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "A");
      Object[][] data = new Object[ROWS + 1][];
      data[0] = new Object[] { "value", "id" };

      for(int i = 1; i <= ROWS; i++) {
         data[i] = new Object[] { 1, i };
      }

      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.INTEGER, XSchema.INTEGER }, data));
      ws.addAssembly(table);
      ColumnSelection columns = table.getColumnSelection(false);
      ExpressionRef exp = new ExpressionRef(null, "out");
      exp.setExpression(FORMULA);
      ColumnRef column = new ColumnRef(exp);
      column.setDataType(XSchema.DOUBLE);
      columns.addAttribute(column);
      table.setColumnSelection(columns, false);
      return ws;
   }

   private static int col(TableLens t, String name) {
      t.moreRows(0);

      for(int c = 0; c < t.getColCount(); c++) {
         if(name.equals(String.valueOf(t.getObject(0, c)))) {
            return c;
         }
      }

      throw new AssertionError("no column " + name);
   }

   private static final int ROWS = 3000;
   private static final int SLOW = 2300;
   // a Date var stepped once per row; the row of id SLOW runs for 1.5 s before its step
   private static final String FORMULA =
      "var d = d || new Date(0); " +
      "if(field['id'] == " + SLOW + ") { var t0 = Date.now(); while(Date.now() - t0 < 1500) {} } " +
      "Date.prototype.setTime.call(d, Date.prototype.getTime.call(d) + 1000); " +
      "Date.prototype.getTime.call(d) / 1000";
   private static final int RUNTIME = AssetQuerySandbox.RUNTIME_MODE;
   private final List<AssetQuerySandbox> boxes = new ArrayList<>();
   private String previousTimeout;
}
