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
package inetsoft.web.wiz.worksheet;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.mv.MVManager;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.DistributedTableCacheStore;
import inetsoft.test.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.worksheet.model.WorksheetModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81354: a join created through {@code add_join} reports ok but has no columns when an
 * earlier, slow, non-plain table exhausts the shared 2000 ms budget of refreshAssemblies.
 *
 * <p>Real {@link Worksheet}, real {@link AssetQuerySandbox} (active), real
 * {@link WorksheetEditService#apply} + {@code Editor.addJoin}, real {@link WorksheetReadService}.
 * Only the {@link RuntimeWorksheet} shell is a mock (as in
 * {@code SetGroupAggregateDownstreamGuardSandboxTest}). The slow table is a crosstab over an
 * embedded table whose script expression column busy-waits, so its
 * {@code refreshColumnSelection} (which executes a non-plain table's query) really blocks and
 * is never cached.</p>
 *
 * <p>A worksheet script cannot be interrupted (java.lang.Thread and test classes are not
 * permitted in scripts), so the spinner always runs its full {@link #SLOW_MS}. {@code @AfterEach}
 * therefore waits it out, so no spinner ever outlives its test and occupies a carrier thread of
 * the next one; the cost is the time that wait adds to each test that starts one.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
               LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
               WorksheetEditServiceBug81354Test.TestConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetEditServiceBug81354Test {
   /**
    * A non-plain table's refresh runs its query through {@code AssetQuery}, which needs these
    * two beans; the shared {@code @WizAgentTestSupport} context has neither.
    */
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }
   }

   /**
    * Long enough for the 2000 ms window plus the second apply that joins the first one's
    * in-flight refresh (laterUnrelatedApply..., inFlightSameDefinition..., which need the slow
    * refresh still running when that second apply starts); 2800 ms was measured too short.
    */
   private static final long SLOW_MS = 4500;
   private static final String SLOW_EXPR =
      "var t = Date.now(); while(Date.now() - t < " + SLOW_MS + ") {} 1";
   private static final long GENEROUS_UPPER_BOUND_MS = 10_000;

   private final Principal agent = TestPrincipals.user("alice", "host-org");
   private final List<String> keyColumns = List.of("k", "a", "k", "b");
   private Worksheet ws;
   private RuntimeWorksheet rws;
   private AssetQuerySandbox box;
   private WorksheetEditService svc;
   private Logger editLog;
   private ListAppender<ILoggingEvent> appender;
   private long firstApplyAt;

   @BeforeEach
   void captureLog() {
      editLog = (Logger) LoggerFactory.getLogger(WorksheetEditService.class);
      appender = new ListAppender<>();
      appender.start();
      editLog.addAppender(appender);
      editLog.setLevel(Level.DEBUG);
      firstApplyAt = 0;
   }

   @AfterEach
   void releaseLogAndWaitOutTheSpinner() throws Exception {
      editLog.detachAppender(appender);

      if(firstApplyAt > 0) {
         long left = firstApplyAt + SLOW_MS + 300 - System.currentTimeMillis();

         if(left > 0) {
            Thread.sleep(left);
         }
      }
   }

   /**
    * Harness is faithful: the slow table's refresh ends in RenderNotReadyException and the later
    * tables are skipped. Without this the primary could pass vacuously if the slow table ever
    * stopped blocking.
    */
   @Test
   void slowTableExhaustsTheBudgetAndLaterTablesAreSkipped() throws Exception {
      build(Order.SLOW_FIRST);

      long t0 = System.currentTimeMillis();
      apply(ed -> {});

      assertTrue(System.currentTimeMillis() - t0 < GENEROUS_UPPER_BOUND_MS);
      assertTrue(anyLog("Failed to refresh assembly: Slow", "RenderNotReadyException"),
                 logged().toString());
      assertTrue(anyLog("Skipping refresh for assembly Table1"), logged().toString());
      assertTrue(anyLog("Skipping refresh for assembly Table2"), logged().toString());
   }

   /** The case the fix turns green: J is skipped by the loop yet carries its columns. */
   @Test
   void primaryJoinHasItsColumnsEvenWhenAnEarlierSlowTableExhaustsTheRefreshBudget()
      throws Exception
   {
      build(Order.SLOW_FIRST);

      apply(ed -> ed.addJoin("J", "Table1", "k", "Table2", "k", "INNER", null, null));

      // the stated reason, asserted first so another failure is not mistaken for it
      assertTrue(anyLog("Skipping refresh for assembly J - refreshAssemblies budget exhausted"),
                 "setup error, J was not skipped by the budget: " + logged());
      assertTrue(anyLog("Failed to refresh assembly: Slow", "RenderNotReadyException"),
                 "setup error, Slow did not end in RenderNotReadyException: " + logged());

      assertEquals(keyColumns, joinColumnNames(),
                   "add_join reported ok; J must carry Table1.k, a, Table2.k, b");
   }

   /** Boundary control: sources before the slow table populate the join via the recursive tail. */
   @Test
   void tablesBeforeTheSlowTablePopulateTheJoin() throws Exception {
      build(Order.SLOW_LAST);

      apply(ed -> ed.addJoin("J", "Table1", "k", "Table2", "k", "INNER", null, null));

      assertEquals(keyColumns, joinColumnNames());
   }

   /** A later unrelated apply, slow table still hung, must not lose a populated join's columns. */
   @Test
   void laterUnrelatedApplyKeepsAPopulatedJoinPopulated() throws Exception {
      build(Order.SLOW_FIRST);
      apply(ed -> ed.addJoin("J", "Table1", "k", "Table2", "k", "INNER", null, null));
      assertEquals(keyColumns, joinColumnNames(), "precondition: J populated after first apply");

      appender.list.clear();
      apply(ed -> ed.addTable("Z", "c"));

      assertTrue(anyLog("Skipping refresh for assembly J"),
                 "the slow table still starves the loop on the next edit: " + logged());
      assertEquals(keyColumns, joinColumnNames(),
                   "a later unrelated edit must not empty or lose the join's columns");
   }

   /**
    * A slow refresh already in flight (same definition) is joined, not restarted, and still
    * exhausts the window, so the loop skips J; J is populated anyway by the early init. No
    * elapsed-time lower bound: the budget-exhausted log line for J is the proof of exhaustion.
    */
   @Test
   void inFlightSameDefinitionRefreshStillExhaustsTheWindowButJoinIsPopulated() throws Exception {
      build(Order.SLOW_FIRST);
      apply(ed -> {});
      appender.list.clear();

      long t0 = System.currentTimeMillis();
      apply(ed -> ed.addJoin("J", "Table1", "k", "Table2", "k", "INNER", null, null));

      assertTrue(System.currentTimeMillis() - t0 < GENEROUS_UPPER_BOUND_MS);
      assertTrue(anyLog("Skipping refresh for assembly J - refreshAssemblies budget exhausted"),
                 logged().toString());
      assertEquals(keyColumns, joinColumnNames(),
                   "window exhaustion must not starve the join of its columns");
   }

   // ---- fixture -----------------------------------------------------------------------------

   private enum Order { SLOW_FIRST, SLOW_LAST }

   private void build(Order order) throws Exception {
      ws = new Worksheet();

      if(order == Order.SLOW_FIRST) {
         ws.addAssembly(slowCrosstab(SLOW_EXPR));
      }

      ws.addAssembly(keyed("Table1", "a", new Object[][]{ { 1, "x" }, { 2, "y" } }));
      ws.addAssembly(keyed("Table2", "b", new Object[][]{ { 1, "p" }, { 2, "q" } }));

      if(order == Order.SLOW_LAST) {
         ws.addAssembly(slowCrosstab(SLOW_EXPR));
      }

      rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      box = new AssetQuerySandbox(ws, null, new VariableTable());
      box.setActive(true);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      svc = service();
   }

   /** Crosstab (groups g1,g2; Sum(w)) over an embedded table whose script column w busy-waits. */
   private TableAssembly slowCrosstab(String expression) throws Exception {
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "Slow", "g1", "g2");
      t.setEmbeddedData(new XEmbeddedTable(
         new String[]{ "string", "string" },
         new Object[][]{ { "g1", "g2" }, { "u", "v" } }));
      ColumnSelection cs = t.getColumnSelection(false);

      for(String c : List.of("g1", "g2")) {
         ((ColumnRef) cs.getAttribute(c)).setDataType(XSchema.STRING);
      }

      WorksheetMutationSupport.addExpressionColumn(t, "w", expression, "integer", false);
      WorksheetMutationSupport.applyAggregateInfo(t,
         List.of(new WorksheetMutationSupport.GroupSpec("g1"),
                 new WorksheetMutationSupport.GroupSpec("g2")),
         List.of(new WorksheetMutationSupport.AggregateSpec("w", "SUM", null)), true, true);
      return t;
   }

   private EmbeddedTableAssembly keyed(String name, String other, Object[][] rows) {
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, name, "k", other);
      Object[][] data = new Object[rows.length + 1][];
      data[0] = new Object[]{ "k", other };
      System.arraycopy(rows, 0, data, 1, rows.length);
      t.setEmbeddedData(new XEmbeddedTable(new String[]{ "integer", "string" }, data));
      ColumnSelection cs = t.getColumnSelection(false);
      ((ColumnRef) cs.getAttribute("k")).setDataType(XSchema.INTEGER);
      ((ColumnRef) cs.getAttribute(other)).setDataType(XSchema.STRING);
      return t;
   }

   private static SecurityEngine permissive() throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      return securityEngine;
   }

   private WorksheetEditService service() throws Exception {
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      JoinSession s = new JoinSession("TOK", "Worksheet/ws1", "alice~;~host-org",
                                      SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.WORKSHEET), eq("Worksheet/ws1"), any()))
         .thenReturn(rws);
      return new WorksheetEditService(sessions, runtimeAccess,
                                      mock(SheetAgentBroadcastService.class), permissive(),
                                      mock(InnerJoinService.class));
   }

   private void apply(ThrowingConsumer<WorksheetEditService.Editor> m) throws Exception {
      if(firstApplyAt == 0) {
         firstApplyAt = System.currentTimeMillis();
      }

      svc.apply("TOK", agent, m);
   }

   /** J's columns exactly as read_worksheet_model reports them. */
   private List<String> joinColumnNames() {
      WorksheetModel model = new WorksheetReadService().read(rws);
      List<String> names = new ArrayList<>();

      for(WorksheetModel.TableModel t : model.tables()) {
         if("J".equals(t.name())) {
            t.columns().forEach(c -> names.add(c.name()));
         }
      }

      return names;
   }

   private List<String> logged() {
      List<String> lines = new ArrayList<>();

      for(ILoggingEvent e : new ArrayList<>(appender.list)) {
         String line = e.getLevel() + " " + e.getFormattedMessage();

         if(e.getThrowableProxy() != null) {
            line += " [" + e.getThrowableProxy().getClassName() + "]";
         }

         lines.add(line);
      }

      return lines;
   }

   private boolean anyLog(String... fragments) {
      return logged().stream().anyMatch(l -> {
         for(String f : fragments) {
            if(!l.contains(f)) {
               return false;
            }
         }

         return true;
      });
   }
}
