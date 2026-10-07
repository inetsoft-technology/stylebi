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
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.QueryManager;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77915: a crosstab query fetches its data without the sandbox lock (74001), on the
 * copy of the viewsheet table it binds to, which is registered in the shared worksheet and
 * holds the query's aggregate calc fields. A calendar change (or any other assembly change)
 * of another request calls Viewsheet.resetWS(), which strips the calc fields from the tables
 * of the worksheet. It must not strip them from the copy a query is running on, and two of
 * them must not strip one table at the same time.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class,
                                  ViewsheetResetWSBoundTableTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetResetWSBoundTableTest {
   @Configuration
   static class TestConfig {
      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }

      // runs the hook on the table a query is about to fetch, after the query has prepared
      // the table and released the sandbox locks
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider) {
            @Override
            public TableLens getData(String id, TableAssembly table, AssetQuerySandbox box,
                                     Set ignoredVars, int mode, boolean limit, long ts,
                                     QueryManager qmgr) throws Exception
            {
               Consumer<TableAssembly> hook = HOOK;

               if(hook != null) {
                  hook.accept(table);
               }

               return super.getData(id, table, box, ignoredVars, mode, limit, ts, qmgr);
            }
         };
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      HOOK = null;
      ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "D");
      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.DATE, XSchema.INTEGER, XSchema.INTEGER },
         new Object[][] { { "d", "id", "grp" },
                          { java.sql.Date.valueOf("2024-01-01"), 1, 1 },
                          { java.sql.Date.valueOf("2024-01-02"), 2, 2 },
                          { java.sql.Date.valueOf("2024-02-01"), 3, 1 } }));
      ws.addAssembly(table);

      vs = new Viewsheet();
      Field field = Viewsheet.class.getDeclaredField("ws");
      field.setAccessible(true);
      field.set(vs, ws);
   }

   @AfterEach
   void tearDown() {
      HOOK = null;

      if(box != null) {
         box.dispose();
      }
   }

   /**
    * The calendar event applies the new dates with no sandbox lock while the crosstab query
    * fetches its data.
    */
   @Test
   void calendarChangeDuringFetchKeepsTheAggregateCalcField() throws Exception {
      createViewsheet(false);
      String[] dates = { "y2024", "m2024-0" };
      int[] count = { 0 };

      assertStripDoesNotBreakQuery(() -> {
         CalendarVSAssemblyInfo info =
            (CalendarVSAssemblyInfo) Tool.clone(calendar.getVSAssemblyInfo());
         info.setDates(new String[] { dates[count[0]++ % dates.length] });
         assertNotEquals(VSAssembly.NONE_CHANGED, calendar.setVSAssemblyInfo(info),
                         "the calendar change did not reset the worksheet");
      });
   }

   /**
    * A refresh of another request resets the runtime under the sandbox write lock, which it
    * gets because the query released the locks for the fetch.
    */
   @Test
   void resetRuntimeDuringFetchKeepsTheAggregateCalcField() throws Exception {
      createViewsheet(false);
      assertStripDoesNotBreakQuery(() -> box.resetRuntime());
   }

   /**
    * The shared tables are still stripped, so a deleted or changed calc field does not stay
    * in them, while the copy a query binds to keeps its calc fields.
    */
   @Test
   void resetStillStripsTheSharedTables() throws Exception {
      createViewsheet(true);
      cells(box.getData("X1"));

      TableAssembly base = (TableAssembly) ws.getAssembly("D");
      assertTrue(hasCalc(base), "the query did not append the detail calc field to D");
      CalculateRef calc = new CalculateRef(true);
      ExpressionRef expr = new ExpressionRef(null, "bcf");
      expr.setExpression("2");
      calc.setDataRef(expr);
      TableAssembly bound = (TableAssembly) Arrays.stream(ws.getAssemblies())
         .filter(a -> a instanceof TableAssembly t &&
                 "true".equals(t.getProperty(Viewsheet.VS_BOUND_TABLE)))
         .findFirst().orElseThrow(() -> new AssertionError("the bound copy is not marked"));
      ColumnSelection columns = bound.getColumnSelection(false);
      columns.addAttribute(calc);
      bound.setColumnSelection(columns, false);

      vs.resetWS();

      assertFalse(hasCalc(base), "the calc field was not stripped from the base table");
      assertTrue(hasCalc(bound), "the calc field was stripped from the bound copy");
   }

   /**
    * Two events reset the worksheet at the same time. Both strip the calc field of a shared
    * table and validate its aggregates, which threw "Index 1 out of bounds for length 1".
    */
   @Test
   void concurrentResetsDoNotFail() throws Exception {
      ExecutorService pool = Executors.newFixedThreadPool(2);

      try {
         for(int i = 0; i < 300; i++) {
            TableAssembly table = new EmbeddedTableAssembly(ws, "T");
            ColumnSelection columns = new ColumnSelection();
            ColumnRef grp = new ColumnRef(new AttributeRef(null, "grp"));
            ColumnRef id = new ColumnRef(new AttributeRef(null, "id"));
            CalculateRef calc = new CalculateRef(true);
            ExpressionRef expr = new ExpressionRef(null, "acf");
            expr.setExpression("1");
            calc.setDataRef(expr);
            columns.addAttribute(grp);
            columns.addAttribute(id);
            columns.addAttribute(calc);
            table.setColumnSelection(columns, false);
            AggregateInfo ainfo = new AggregateInfo();
            ainfo.addGroup(new GroupRef(grp));
            ainfo.addAggregate(new AggregateRef(id, AggregateFormula.SUM));
            ainfo.addAggregate(new AggregateRef(calc, AggregateFormula.NONE));
            table.setAggregateInfo(ainfo);
            ws.addAssembly(table);

            CyclicBarrier start = new CyclicBarrier(2);
            Callable<Void> reset = () -> {
               start.await(10, TimeUnit.SECONDS);
               vs.resetWS();
               return null;
            };
            Future<Void> first = pool.submit(reset);
            Future<Void> second = pool.submit(reset);

            try {
               first.get(30, TimeUnit.SECONDS);
               second.get(30, TimeUnit.SECONDS);
            }
            catch(ExecutionException ex) {
               throw new AssertionError("concurrent resets failed at iteration " + i,
                                        ex.getCause());
            }

            assertFalse(hasCalc(table), "the calc field was not stripped");
            assertEquals(1, table.getAggregateInfo().getAggregateCount());
         }
      }
      finally {
         pool.shutdownNow();
      }
   }

   /**
    * Run the crosstab, strip the worksheet on another thread right before the query reads
    * its table, and check the query still returns the same data.
    */
   private void assertStripDoesNotBreakQuery(Runnable strip) throws Exception {
      String expected = cells(fetch());
      assertEquals(EXPECTED, expected, "before the strip");

      boolean[] fired = { false };
      String[] after = { null };
      HOOK = table -> {
         if(fired[0] || !table.getName().startsWith("V_MD")) {
            return;
         }

         fired[0] = true;
         assertSame(table, ws.getAssembly(table.getName()),
                    "the query does not run on the copy in the shared worksheet");
         assertTrue(hasCalc(table), "the query did not append the calc field");
         CompletableFuture.runAsync(strip).join();
         after[0] = hasCalc(table) ? "kept" : "stripped";
      };

      Object data = fetch();
      HOOK = null;

      assertTrue(fired[0], "the strip did not run during the fetch");
      assertEquals("kept", after[0], "the calc field was stripped from the running query");
      assertEquals(expected, cells(data), "during the strip");
      assertEquals(expected, cells(fetch()), "after the strip");
   }

   private Object fetch() throws Exception {
      box.resetDataMap("X1");
      return box.getData("X1");
   }

   /**
    * A crosstab of grp / Sum(id) and an aggregate (or detail) calc field, and a calendar on
    * the same table, as the reported viewsheet has.
    */
   private void createViewsheet(boolean detail) throws Exception {
      CalculateRef calc = new CalculateRef(detail);
      ExpressionRef expr = new ExpressionRef(null, "acf");
      expr.setExpression("1");
      calc.setDataRef(expr);
      calc.setDataType(XSchema.INTEGER);
      vs.addCalcField("D", calc);

      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, "X1");
      crosstab.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "D"));
      VSCrosstabInfo cinfo = new VSCrosstabInfo();
      VSDimensionRef dim = new VSDimensionRef(new AttributeRef(null, "grp"));
      dim.setGroupColumnValue("grp");
      dim.setDataType(XSchema.INTEGER);
      VSAggregateRef sum = new VSAggregateRef();
      sum.setDataRef(new AttributeRef(null, "id"));
      sum.setColumnValue("id");
      sum.setFormulaValue("Sum");
      sum.setOriginalDataType(XSchema.INTEGER);
      VSAggregateRef acf = new VSAggregateRef();
      acf.setDataRef(new AttributeRef(null, "acf"));
      acf.setColumnValue("acf");
      acf.setFormulaValue(detail ? "Max" : "None");
      acf.setOriginalDataType(XSchema.INTEGER);
      cinfo.setDesignRowHeaders(new DataRef[] { dim });
      cinfo.setDesignAggregates(new DataRef[] { sum, acf });
      crosstab.setVSCrosstabInfo(cinfo);
      vs.addAssembly(crosstab);

      calendar = new CalendarVSAssembly(vs, "Calendar1");
      CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) calendar.getVSAssemblyInfo();
      info.setTableName("D");
      ColumnRef date = new ColumnRef(new AttributeRef(null, "d"));
      date.setDataType(XSchema.DATE);
      info.setDataRef(date);
      vs.addAssembly(calendar);

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.VIEWSHEET, "test/Bug77915", null);
      vs.setEntry(entry);
      box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
      box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);
   }

   private static boolean hasCalc(TableAssembly table) {
      ColumnSelection columns = table.getColumnSelection(false);

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         if(columns.getAttribute(i) instanceof CalculateRef) {
            return true;
         }
      }

      return false;
   }

   private static String cells(Object data) {
      assertInstanceOf(TableLens.class, data, "no data");
      TableLens lens = (TableLens) data;
      StringBuilder sb = new StringBuilder();
      lens.moreRows(TableLens.EOT);

      for(int r = 0; r < lens.getRowCount(); r++) {
         for(int c = 0; c < lens.getColCount(); c++) {
            sb.append(lens.getObject(r, c)).append(c < lens.getColCount() - 1 ? "|" : "\n");
         }
      }

      return sb.toString();
   }

   private static final String EXPECTED =
      "grp|null|Sum(id)/acf\n1|Sum(id)|4.0\n1|acf|1.0\n2|Sum(id)|2.0\n2|acf|1.0\n";
   private static volatile Consumer<TableAssembly> HOOK;
   private Worksheet ws;
   private Viewsheet vs;
   private ViewsheetSandbox box;
   private CalendarVSAssembly calendar;
}
