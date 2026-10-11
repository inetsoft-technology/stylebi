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
package inetsoft.report.composition.execution.lockcycle;

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.*;
import inetsoft.web.wiz.service.WizVsService;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.script.graal.pool.PoolConfig;
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

import java.util.List;
import java.util.concurrent.TimeUnit;

import static inetsoft.report.composition.execution.lockcycle.LockCycleHarness.drain;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78283, two ways a worksheet expression column reads another table by name.
 *
 * <p>Self reference: a worksheet expression column that names its own table ({@code T['id']} on
 * {@code T}) read the table from inside its own build. The build had marked its data key as
 * executing, so the nested read waited for that marker, owned by the waiting thread itself,
 * until the 10 minute cache wait timeout, and held the embedded table's monitor meanwhile. The
 * read now fails at once with a message naming the table, and releases the monitor.
 *
 * <p>Array result: {@code RT['RV']} reads a whole column of {@code RT}, an {@code Object[]} for
 * a table of 2+ rows, whatever the spelling, and a scalar column stored it as is
 * ({@code [Ljava.lang.Object;@...}). A worksheet expression column now fails the query instead.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
   LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
   WorksheetExpressionTableReadTest.TestConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
@Timeout(value = 90, unit = TimeUnit.SECONDS)
public class WorksheetExpressionTableReadTest {
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }
   }

   @BeforeEach
   public void setUp() {
      harness = new LockCycleHarness();
   }

   @AfterEach
   public void tearDown() throws Exception {
      harness.close();

      if(box != null) {
         box.dispose();
      }

      SreeEnv.remove(PoolConfig.ENABLED);
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   public void tableReadingItselfFailsNamedAndReleasesMonitor(boolean pool) throws Exception {
      open(pool);
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = embedded(ws, "T", 3);
      expression(t, "self", "T['id']");
      box = sandbox(ws, pool);

      Exception failure = assertThrows(Exception.class, () -> harness.await(
         harness.start(() -> read(box, "T")).future,
         KNOWN_CAP, "T reading itself"));
      assertNamed(failure, "T");

      // the build released T's monitor: another thread gets it, and T fails again, fast
      assertEquals("held", harness.await(harness.start(() -> {
         synchronized(t) {
            return "held";
         }
      }).future, KNOWN_CAP, "monitor of T after the failed build"));
      failure = assertThrows(Exception.class, () -> harness.await(
         harness.start(() -> read(box, "T")).future,
         KNOWN_CAP, "second read of T"));
      assertNamed(failure, "T");
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   public void cycleThroughAnotherTableTerminates(boolean pool) throws Exception {
      open(pool);
      Worksheet ws = new Worksheet();
      expression(embedded(ws, "A", 2), "ax", "B.length");
      expression(embedded(ws, "B", 2), "bx", "A.length");
      box = sandbox(ws, pool);

      // A's build builds B, whose formula reads A again on this thread. The nested B build fails
      // with the circular reference (B comes back as a failed query), where it waited for A
      // forever before; A itself finishes.
      harness.await(harness.start(() -> read(box, "A")).future, KNOWN_CAP, "A <-> B cycle");
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   public void readingAnotherTableByNameStillWorks(boolean pool) throws Exception {
      open(pool);
      Worksheet ws = new Worksheet();
      embedded(ws, "A", 3);
      expression(embedded(ws, "X", 2), "alen", "A.length");
      box = sandbox(ws, pool);

      // read twice: once building X, once from X's cached lens
      for(int i = 0; i < 2; i++) {
         assertEquals(3, harness.await(harness.start(() ->
            read(box, "X").size()).future,
            KNOWN_CAP, "X reading A by name"));
      }
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   public void arrayResultFailsForEverySpelling(boolean pool) throws Exception {
      open(pool);
      String[] spellings = { "RT['RV']", "RT['R' + 'V']", "var t = 'RT'; this[t]['RV']", "RT.RV" };

      for(String spelling : spellings) {
         Worksheet ws = new Worksheet();
         embedded(ws, "RT", "RV", 2, 100);
         expression(embedded(ws, "A", "id", 2, 1), "x", spelling);
         AssetQuerySandbox sandbox = sandbox(ws, pool);

         try {
            Exception failure = assertThrows(Exception.class, () -> harness.await(harness.start(
               () -> read(sandbox, "A")).future,
               KNOWN_CAP, spelling));
            String message = String.valueOf(failure.getMessage());
            assertTrue(message.contains("Expression column 'x' returned an array of 2 values"),
                       spelling + ": " + message);
            assertFalse(message.contains("\n"), "single-line message expected: " + message);
         }
         finally {
            sandbox.dispose();
         }
      }
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   public void aggregateOrIndexedReadOfAnotherTableStillWorks(boolean pool) throws Exception {
      open(pool);
      Worksheet ws = new Worksheet();
      embedded(ws, "RT", "RV", 2, 100);
      EmbeddedTableAssembly a = embedded(ws, "A", "id", 2, 1);
      expression(a, "total", "sum(RT['RV'])");
      expression(a, "first", "RT['RV'][0]");
      box = sandbox(ws, pool);

      List<List<Object>> rows = harness.await(harness.start(
         () -> read(box, "A")).future,
         KNOWN_CAP, "aggregate read of RT");
      // header row, then 2 data rows: id, total, first
      assertEquals(3, rows.size());
      assertEquals(300, ((Number) rows.get(1).get(1)).intValue());
      assertEquals(100, ((Number) rows.get(2).get(2)).intValue());
   }

   /**
    * Read {@code name} as the worksheet preview does: a failed query comes back as a design
    * lens carrying its cause, which {@code checkFailedQuery} throws.
    */
   private static List<List<Object>> read(AssetQuerySandbox sandbox, String name)
      throws Exception
   {
      TableLens lens = sandbox.getTableLens(name, AssetQuerySandbox.RUNTIME_MODE);
      WizVsService.checkFailedQuery(lens, false);
      return drain(lens);
   }

   private static void assertNamed(Exception failure, String table) {
      String message = String.valueOf(failure.getMessage());
      assertTrue(message.contains("Table '" + table + "' references itself"), message);
      assertFalse(message.contains("\n"), "single-line message expected: " + message);
   }

   private static void open(boolean pool) {
      SreeEnv.setProperty(PoolConfig.ENABLED, Boolean.toString(pool));
   }

   private static AssetQuerySandbox sandbox(Worksheet ws, boolean pool) {
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      assertEquals(pool, box.isScriptPoolMode());
      return box;
   }

   private static EmbeddedTableAssembly embedded(Worksheet ws, String name, int rows) {
      return embedded(ws, name, "id", rows, 1);
   }

   private static EmbeddedTableAssembly embedded(Worksheet ws, String name, String column,
                                                 int rows, int first)
   {
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, name);
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { column };

      for(int i = 1; i <= rows; i++) {
         data[i] = new Object[] { first == 1 ? i : first * i };
      }

      table.setEmbeddedData(new XEmbeddedTable(new String[] { XSchema.INTEGER }, data));
      ws.addAssembly(table);
      return table;
   }

   private static EmbeddedTableAssembly expression(EmbeddedTableAssembly table, String name,
                                                   String script)
   {
      ExpressionRef exp = new ExpressionRef(null, name);
      exp.setExpression(script);
      ColumnRef column = new ColumnRef(exp);
      column.setDataType(XSchema.INTEGER);
      ColumnSelection columns = table.getColumnSelection(false);
      columns.addAttribute(column);
      table.setColumnSelection(columns, false);
      return table;
   }

   private static final long KNOWN_CAP = 15;
   private LockCycleHarness harness;
   private AssetQuerySandbox box;
}
