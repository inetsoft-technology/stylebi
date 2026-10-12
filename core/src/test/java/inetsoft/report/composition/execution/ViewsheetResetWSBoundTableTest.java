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

import java.lang.reflect.Method;
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
@Tag("slow")
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
      BOUND_HOOK = null;
      BOUND_DONE = null;
      BOUND_HOOK_TARGET = "X1";
      RESET_HOOK = null;
      resetThread = null;
      ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "D");
      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.DATE, XSchema.INTEGER, XSchema.INTEGER },
         new Object[][] { { "d", "id", "grp" },
                          { java.sql.Date.valueOf("2024-01-01"), 1, 1 },
                          { java.sql.Date.valueOf("2024-01-02"), 2, 2 },
                          { java.sql.Date.valueOf("2024-02-01"), 3, 1 } }));
      ws.addAssembly(table);

      // what Viewsheet.update() does with the worksheet it loads
      vs = new Viewsheet();
      Method setBase = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBase.setAccessible(true);
      setBase.invoke(vs, ws);
   }

   @AfterEach
   void tearDown() {
      HOOK = null;
      BOUND_HOOK = null;
      BOUND_DONE = null;
      RESET_HOOK = null;
      resetThread = null;

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
      assertStripDoesNotBreakQuery(this::changeCalendar);
   }

   /**
    * The calendar event lands after a crosstab query of a detail calc field appended it to
    * the base table and before the query copied the viewsheet table it binds to. The query
    * used to return without the calc field measure, and no error.
    */
   @Test
   void calendarChangeBeforeTheBoundCopyKeepsTheDetailCalcField() throws Exception {
      createViewsheet(true);
      String expected = cells(fetch());
      assertTrue(expected.contains("Max(acf)"), "no calc field measure: " + expected);

      boolean[] kept = { false };
      BOUND_HOOK = () -> {
         CompletableFuture.runAsync(this::changeCalendar).join();
         kept[0] = hasCalc((TableAssembly) ws.getAssembly("D"));
      };

      Object data = fetch();

      assertNull(BOUND_HOOK, "the strip did not run before the bound copy");
      assertEquals(expected, cells(data), "during the strip");
      assertTrue(kept[0], "the calc field was stripped from the base table");
      assertEquals(expected, cells(fetch()), "after the strip");
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
    * A calendar bound to a detail calc field copies the selection table S_D, which holds the
    * calc fields of D, while another request resets the worksheet. The copy is taken after the
    * reset stripped the calc fields and before it added them to the selection tables again.
    * The table the query reads used to miss the calc field, and the calendar lost its date
    * range with no error when the query read it before the reset added the field again.
    */
   @Test
   void resetDuringTheCalendarCopyKeepsTheSelectionTableCalcField() throws Exception {
      createViewsheet(false, true);
      String expected = range(fetchCalendar());
      assertNotEquals("null", expected, "no date range");

      CountDownLatch inWindow = new CountDownLatch(1);
      CountDownLatch copied = new CountDownLatch(1);
      boolean[] kept = { false };
      boolean[] read = { false };
      Throwable[] resetError = { null };
      BOUND_HOOK_TARGET = "Calendar1";
      BOUND_HOOK = () -> {
         RESET_HOOK = () -> {
            kept[0] = hasCalc((TableAssembly) ws.getAssembly("S_D"));
            inWindow.countDown();

            try {
               assertTrue(copied.await(30, TimeUnit.SECONDS), "the calendar did not copy");
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         };
         resetThread = new Thread(() -> {
            try {
               vs.resetWS();
            }
            catch(Throwable ex) {
               resetError[0] = ex;
            }
         }, "b77915-reset");
         resetThread.start();

         try {
            assertTrue(inWindow.await(30, TimeUnit.SECONDS), "the reset did not create mirrors");
         }
         catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
         }
      };

      // the calendar query copies S_D right after the hook, inside the window. it must not
      // get the range of the first query from the data cache
      BOUND_DONE = table -> {
         TableAssembly inner = ((MirrorTableAssembly) table).getTableAssembly();
         read[0] = inner != null && hasCalc(inner);
         copied.countDown();
      };
      AssetDataCache.getCache().clearCache();
      Object data;

      try {
         data = fetchCalendar();
      }
      finally {
         copied.countDown();
      }

      resetThread.join(30_000);
      assertNull(resetError[0], "the reset failed");
      assertNull(BOUND_HOOK, "the reset did not run before the calendar copy");
      assertEquals(expected, range(data), "during the reset");
      assertTrue(kept[0], "the calc field was stripped from the selection table");
      assertTrue(read[0], "the table the calendar query reads has no calc field");
      assertEquals(expected, range(fetchCalendar()), "after the reset");
   }

   /**
    * A calendar query of a detail calc field prunes and validates the calc fields of its own
    * copy of the selection table, not the shared one that resetWS and other queries change
    * (77867). It used to work on the shared selection table, for the calc fields of a selection
    * table are defined for the table it selects from.
    */
   @Test
   void calendarQueryRunsOnItsOwnCopyOfTheSelectionTable() throws Exception {
      createViewsheet(false, true);
      fetchCalendar();
      List<String> fetched = new ArrayList<>();
      HOOK = table -> {
         TableAssembly inner = table;

         while(inner instanceof MirrorTableAssembly mirror && !"S_D".equals(inner.getName())) {
            inner = mirror.getTableAssembly();
         }

         if(inner != null && "S_D".equals(inner.getName())) {
            fetched.add(table.getName() + (inner == ws.getAssembly("S_D") ? ":shared" : ":own"));
         }
      };

      AssetDataCache.getCache().clearCache();
      fetchCalendar();
      HOOK = null;

      assertFalse(fetched.isEmpty(), "the calendar query did not fetch S_D");
      assertTrue(fetched.stream().allMatch(f -> f.endsWith(":own")),
                 "the calendar query runs on the shared selection table: " + fetched);
   }

   /**
    * An edited calc field expression reaches the base table and its selection table at the
    * next reset, as it did when the reset stripped every calc field.
    */
   @Test
   void resetRefreshesAnEditedCalcField() throws Exception {
      createViewsheet(false, true);
      fetchCalendar();
      assertEquals("field['d']", expression("D", "dcf"));
      assertEquals("field['d']", expression("S_D", "dcf"));

      CalculateRef edited = (CalculateRef) vs.getCalcField("D", "dcf").clone();
      ExpressionRef expr = new ExpressionRef(null, "dcf");
      expr.setExpression("dateAdd('d', 1, field['d'])");
      edited.setDataRef(expr);
      vs.addCalcField("D", edited);
      vs.resetWS();

      assertEquals("dateAdd('d', 1, field['d'])", expression("D", "dcf"), "base table");
      assertEquals("dateAdd('d', 1, field['d'])", expression("S_D", "dcf"), "selection table");
   }

   /**
    * The shared tables keep the calc fields the viewsheet defines for them and lose a deleted
    * one, or one whose detail type changed. The copy a query binds to keeps its calc fields,
    * and a table that is only named like one (MVAssetQuery's V_M..._subQuery) does not.
    */
   @Test
   void resetStripsOnlyStaleCalcFieldsFromTheSharedTables() throws Exception {
      createViewsheet(true);
      fetch();

      TableAssembly base = (TableAssembly) ws.getAssembly("D");
      TableAssembly bound = (TableAssembly) Arrays.stream(ws.getAssemblies())
         .filter(a -> a instanceof TableAssembly t &&
                 "true".equals(t.getProperty(Viewsheet.VS_BOUND_TABLE)))
         .findFirst().orElseThrow(() -> new AssertionError("the bound copy is not marked"));
      addCalc(bound, "bcf", true);
      TableAssembly named = new EmbeddedTableAssembly(ws, "V_MD_subQuery");
      named.setColumnSelection(new ColumnSelection(), false);
      addCalc(named, "acf", true);
      ws.addAssembly(named);
      assertTrue(hasCalc(base), "the query did not append the detail calc field to D");

      vs.resetWS();
      assertTrue(hasCalc(base), "the calc field the viewsheet defines was stripped from D");
      assertFalse(hasCalc(named), "the calc field was not stripped from an unmarked table");

      // the detail type changed
      CalculateRef calc = vs.getCalcField("D", "acf");
      vs.removeCalcField("D", "acf");
      CalculateRef aggregate = new CalculateRef(false);
      aggregate.setDataRef(calc.getDataRef());
      vs.addCalcField("D", aggregate);
      vs.resetWS();
      assertFalse(hasCalc(base), "the calc field of another type was not stripped from D");

      // deleted
      addCalc(base, "acf", false);
      vs.removeCalcField("D", "acf");
      vs.resetWS();
      assertFalse(hasCalc(base), "the deleted calc field was not stripped from D");
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
         for(int i = 0; i < 100; i++) {
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

   private void changeCalendar() {
      CalendarVSAssemblyInfo info =
         (CalendarVSAssemblyInfo) Tool.clone(calendar.getVSAssemblyInfo());
      info.setDates(new String[] { DATES[dateCount++ % DATES.length] });
      assertNotEquals(VSAssembly.NONE_CHANGED, calendar.setVSAssemblyInfo(info),
                      "the calendar change did not reset the worksheet");
   }

   private static void addCalc(TableAssembly table, String name, boolean detail) {
      CalculateRef calc = new CalculateRef(detail);
      ExpressionRef expr = new ExpressionRef(null, name);
      expr.setExpression("2");
      calc.setDataRef(expr);
      ColumnSelection columns = table.getColumnSelection(false);
      columns.addAttribute(calc);
      table.setColumnSelection(columns, false);
   }

   private Object fetchCalendar() throws Exception {
      box.resetDataMap("Calendar1");
      return box.getData("Calendar1");
   }

   private static String range(Object data) {
      return data instanceof Object[] array ? Arrays.deepToString(array) : String.valueOf(data);
   }

   private String expression(String table, String calc) {
      DataRef ref = ((TableAssembly) ws.getAssembly(table)).getColumnSelection(false)
         .getAttribute(calc);
      assertInstanceOf(CalculateRef.class, ref, calc + " is not in " + table);
      return ((ExpressionRef) ((CalculateRef) ref).getDataRef()).getExpression();
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
      createViewsheet(detail, false);
   }

   /**
    * @param calendarOnCalc bind the calendar to a detail calc date field (dcf) of D, and add a
    *                       text that runs RESET_HOOK when resetWS creates the mirror tables,
    *                       after it stripped the calc fields and before it adds them to the
    *                       selection tables again.
    */
   private void createViewsheet(boolean detail, boolean calendarOnCalc) throws Exception {
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
      ColumnRef date = new ColumnRef(new AttributeRef(null, calendarOnCalc ? "dcf" : "d"));
      date.setDataType(XSchema.DATE);
      info.setDataRef(date);
      vs.addAssembly(calendar);

      if(calendarOnCalc) {
         CalculateRef dcf = new CalculateRef(true);
         ExpressionRef dexpr = new ExpressionRef(null, "dcf");
         dexpr.setExpression("field['d']");
         dcf.setDataRef(dexpr);
         dcf.setDataType(XSchema.DATE);
         vs.addCalcField("D", dcf);

         vs.addAssembly(new TextVSAssembly(vs, "Text1") {
            @Override
            public String getTableName() {
               Runnable hook = RESET_HOOK;

               if(hook != null && Thread.currentThread() == resetThread) {
                  RESET_HOOK = null;
                  hook.run();
               }

               return super.getTableName();
            }
         });
      }

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.VIEWSHEET, "test/Bug77915", null);
      vs.setEntry(entry);
      box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry) {
         // runs the hooks before and after the query copies the table it binds to, after the
         // query appended the detail calc fields to the base table
         @Override
         public TableAssembly getBoundTable(TableAssembly assembly, String vassembly,
                                            boolean detail)
            throws Exception
         {
            Runnable hook = BOUND_HOOK;

            if(hook != null && BOUND_HOOK_TARGET.equals(vassembly)) {
               BOUND_HOOK = null;
               hook.run();
            }

            TableAssembly table = super.getBoundTable(assembly, vassembly, detail);
            Consumer<TableAssembly> done = BOUND_DONE;

            if(done != null && BOUND_HOOK_TARGET.equals(vassembly)) {
               BOUND_DONE = null;
               done.accept(table);
            }

            return table;
         }
      };
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
   private static final String[] DATES = { "y2024", "m2024-0" };
   private static volatile Consumer<TableAssembly> HOOK;
   private static volatile Runnable BOUND_HOOK;
   private static volatile Consumer<TableAssembly> BOUND_DONE;
   private static volatile String BOUND_HOOK_TARGET = "X1";
   private static volatile Runnable RESET_HOOK;
   private static volatile Thread resetThread;
   private int dateCount;
   private Worksheet ws;
   private Viewsheet vs;
   private ViewsheetSandbox box;
   private CalendarVSAssembly calendar;
}
