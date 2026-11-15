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
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.binding.service.VSBindingTreeService;
import inetsoft.web.viewsheet.service.CommandDispatcherService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
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
 * earlier, slow, non-plain table exhausts {@code refreshAssemblies}' shared 2000 ms budget.
 *
 * <p>Real {@link Worksheet}, real {@link AssetQuerySandbox} (active), real
 * {@link WorksheetEditService#apply} + {@code Editor.addJoin}, real {@link WorksheetReadService}.
 * Only the {@link RuntimeWorksheet} shell is a mock (as in
 * {@code SetGroupAggregateDownstreamGuardSandboxTest}). The slow table is a crosstab over an
 * embedded table whose script expression column busy-waits, so its
 * {@code refreshColumnSelection} (which executes a non-plain table's query) really blocks and
 * is never cached.</p>
 *
 * <p>{@link #primaryJoinHasItsColumnsEvenWhenAnEarlierSlowTableExhaustsTheRefreshBudget} is the
 * case the fix must turn green; the rest document the boundary and are green today.</p>
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

   private static final String SLOW_EXPR =
      "var t = Date.now(); while(Date.now() - t < 7000) {} 1";
   private static final String MID_EXPR =
      "var t = Date.now(); while(Date.now() - t < 800) {} 1";
   private static final String FAST_EXPR = "1";

   private final Principal agent = TestPrincipals.user("alice", "host-org");
   private final List<String> keyColumns = List.of("k", "a", "k", "b");
   private Worksheet ws;
   private RuntimeWorksheet rws;
   private AssetQuerySandbox box;
   private WorksheetEditService svc;
   private Logger editLog;
   private ListAppender<ILoggingEvent> appender;

   @BeforeEach
   void captureLog() {
      editLog = (Logger) LoggerFactory.getLogger(WorksheetEditService.class);
      appender = new ListAppender<>();
      appender.start();
      editLog.addAppender(appender);
      editLog.setLevel(Level.DEBUG);
   }

   @AfterEach
   void releaseLog() {
      editLog.detachAppender(appender);
   }

   // ---- M0: the harness is faithful ---------------------------------------------------------

   @Test
   void m0SlowTableEndsInRenderNotReadyAtAboutTwoSecondsAndLaterTablesAreSkipped()
      throws Exception
   {
      build(Order.SLOW_FIRST, SLOW_EXPR, null);

      long t0 = System.currentTimeMillis();
      apply(ed -> {});
      long ms = System.currentTimeMillis() - t0;
      System.out.println("M0 apply(no-op) took " + ms + " ms");
      logged().forEach(l -> System.out.println("M0 LOG " + l));

      assertTrue(ms >= 1800 && ms < 4000, "apply should spend ~the 2000 ms budget, took " + ms);
      assertTrue(anyLog("Failed to refresh assembly: Slow", "RenderNotReadyException"),
                 "Slow's refresh must end in RenderNotReadyException: " + logged());
      assertTrue(anyLog("Skipping refresh for assembly Table1"), logged().toString());
      assertTrue(anyLog("Skipping refresh for assembly Table2"), logged().toString());
   }

   // ---- PRIMARY: the case the fix must turn green -------------------------------------------

   @Test
   void primaryJoinHasItsColumnsEvenWhenAnEarlierSlowTableExhaustsTheRefreshBudget()
      throws Exception
   {
      build(Order.SLOW_FIRST, SLOW_EXPR, null);

      apply(ed -> ed.addJoin("J", "Table1", "k", "Table2", "k", "INNER", null, null));

      logged().forEach(l -> System.out.println("PRIMARY LOG " + l));
      System.out.println("PRIMARY J private=" + count(false) + " public=" + count(true)
                         + " model=" + joinColumnNames());

      // diagnostics of the stated reason, asserted first so a different failure is not mistaken
      // for it: the join was skipped by the shared budget and nothing wrote its selection
      assertTrue(anyLog("Skipping refresh for assembly J - refreshAssemblies budget exhausted"),
                 "setup error, J was not skipped by the budget: " + logged());
      assertTrue(anyLog("Failed to refresh assembly: Slow", "RenderNotReadyException"),
                 "setup error, Slow did not end in RenderNotReadyException: " + logged());

      // the expected behaviour
      assertEquals(keyColumns, joinColumnNames(),
                   "add_join reported ok; J must carry Table1.k, a, Table2.k, b (read_worksheet_model reports the second key as plain k, not k_1)");
   }

   // ---- M1: a join created by Editor.addJoin with no refresh is empty -----------------------

   @Test
   void m1JoinCreatedByAddJoinWithNoRefreshIsEmpty() throws Exception {
      build(Order.TABLES_ONLY, FAST_EXPR, null);

      WorksheetEditService.Editor ed = new WorksheetEditService.Editor(
         ws, agent, permissive(), mock(InnerJoinService.class), null, () -> null);
      ed.addJoin("J", "Table1", "k", "Table2", "k", "INNER", null, null);

      assertNotNull(ws.getAssembly("J"));
      assertEquals(0, count(false));
      assertEquals(0, count(true));
      assertEquals(List.of(), joinColumnNames());
   }

   // ---- M3: sources before the slow table populate the join ---------------------------------

   @Test
   void m3OrderTablesThenSlowPopulatesTheJoin() throws Exception {
      build(Order.SLOW_LAST, SLOW_EXPR, null);

      apply(ed -> ed.addJoin("J", "Table1", "k", "Table2", "k", "INNER", null, null));

      logged().forEach(l -> System.out.println("M3 LOG " + l));
      assertEquals(keyColumns, joinColumnNames());
   }

   // ---- M4: a fast crosstab first populates the join ----------------------------------------

   @Test
   void m4FastCrosstabFirstPopulatesTheJoin() throws Exception {
      build(Order.SLOW_FIRST, FAST_EXPR, null);

      apply(ed -> ed.addJoin("J", "Table1", "k", "Table2", "k", "INNER", null, null));

      logged().forEach(l -> System.out.println("M4 LOG " + l));
      assertFalse(anyLog("Skipping refresh for assembly J"), logged().toString());
      assertEquals(keyColumns, joinColumnNames());
   }

   // ---- M5 (substitute): a first table that is slow but finishes inside the budget ----------

   @Test
   void m5FirstTableThatFinishesInsideTheBudgetPopulatesTheJoin() throws Exception {
      build(Order.SLOW_FIRST, MID_EXPR, null);

      long t0 = System.currentTimeMillis();
      apply(ed -> ed.addJoin("J", "Table1", "k", "Table2", "k", "INNER", null, null));
      System.out.println("M5 apply took " + (System.currentTimeMillis() - t0) + " ms");

      logged().forEach(l -> System.out.println("M5 LOG " + l));
      assertFalse(anyLog("Skipping refresh for assembly J"), logged().toString());
      assertEquals(keyColumns, joinColumnNames());
   }

   // ---- M6: a second unrelated apply leaves the join empty ----------------------------------

   @Test
   void m6SecondUnrelatedApplyLeavesTheJoinEmpty() throws Exception {
      build(Order.SLOW_FIRST, SLOW_EXPR, null);
      apply(ed -> ed.addJoin("J", "Table1", "k", "Table2", "k", "INNER", null, null));
      assertEquals(0, count(false), "precondition: J empty after the first apply");

      appender.list.clear();
      apply(ed -> ed.addTable("Z", "c"));

      logged().forEach(l -> System.out.println("M6 LOG " + l));
      System.out.println("M6 J after 2nd apply private=" + count(false));
      assertEquals(0, count(false), "starvation repeats on the next edit");
   }

   // ---- M7: a direct refresh of the join is cheap while the slow refresh is hung ------------

   @Test
   void m7DirectRefreshOfTheJoinIsQuickAndFillsItWhileTheSlowRefreshIsHung() throws Exception {
      build(Order.SLOW_FIRST, SLOW_EXPR, null);
      apply(ed -> ed.addJoin("J", "Table1", "k", "Table2", "k", "INNER", null, null));
      assertEquals(0, count(false), "precondition: J empty");

      long t0 = System.currentTimeMillis();
      box.refreshColumnSelection("J", false);
      long ms = System.currentTimeMillis() - t0;
      System.out.println("M7 refreshColumnSelection(J,false) took " + ms + " ms; J private="
                         + count(false) + " model=" + joinColumnNames());

      assertTrue(ms < 1500, "direct join refresh must not block on the slow query, took " + ms);
      assertEquals(keyColumns, joinColumnNames());
   }

   @Test
   void m7bDirectRecursiveRefreshOfTheJoinAlsoDoesNotBlock() throws Exception {
      build(Order.SLOW_FIRST, SLOW_EXPR, null);
      apply(ed -> ed.addJoin("J", "Table1", "k", "Table2", "k", "INNER", null, null));

      long t0 = System.currentTimeMillis();
      box.refreshColumnSelection("J", true);
      long ms = System.currentTimeMillis() - t0;
      System.out.println("M7b refreshColumnSelection(J,true) took " + ms + " ms; J private="
                         + count(false));

      assertTrue(ms < 1500, "took " + ms);
      assertEquals(keyColumns, joinColumnNames());
   }

   // ---- M8: getTableLens fills a never-run join; and with a socket session + real broadcast -

   @Test
   void m8GetTableLensFillsANeverRunJoin() throws Exception {
      build(Order.TABLES_ONLY, FAST_EXPR, null);
      WorksheetEditService.Editor ed = new WorksheetEditService.Editor(
         ws, agent, permissive(), mock(InnerJoinService.class), null, () -> null);
      ed.addJoin("J", "Table1", "k", "Table2", "k", "INNER", null, null);
      assertEquals(0, count(false), "precondition");

      box.getTableLens("J", AssetQuerySandbox.DESIGN_MODE);
      System.out.println("M8a after getTableLens(J, DESIGN_MODE) private=" + count(false)
                         + " public=" + count(true));

      assertEquals(keyColumns, joinColumnNames());
   }

   @Test
   void m8cCreateModelFromDoesOrDoesNotFillANeverRunJoin() throws Exception {
      build(Order.TABLES_ONLY, FAST_EXPR, "stomp-1");
      WorksheetEditService.Editor ed = new WorksheetEditService.Editor(
         ws, agent, permissive(), mock(InnerJoinService.class), null, () -> null);
      ed.addJoin("J", "Table1", "k", "Table2", "k", "INNER", null, null);
      assertEquals(0, count(false), "precondition");

      System.out.println("M8c getMode(J)=" + inetsoft.web.composer.ws.assembly.WorksheetEventUtil
         .getMode((TableAssembly) ws.getAssembly("J")) + " (DESIGN_MODE="
         + AssetQuerySandbox.DESIGN_MODE + ", EDIT_MODE=" + AssetQuerySandbox.EDIT_MODE
         + ", LIVE_MODE=" + AssetQuerySandbox.LIVE_MODE + ", RUNTIME_MODE="
         + AssetQuerySandbox.RUNTIME_MODE + ")");
      Object model = inetsoft.web.composer.ws.assembly.WSAssemblyModelFactory.createModelFrom(
         (WSAssembly) ws.getAssembly("J"), rws, agent);
      System.out.println("M8c createModelFrom(J) returned " + (model == null ? null
         : model.getClass().getSimpleName()) + "; J private=" + count(false) + " public="
         + count(true) + " model=" + joinColumnNames());

      assertNotNull(model);
   }

   @Test
   void m8bWithASocketSessionTheRealBroadcastDoesOrDoesNotPopulateTheJoin() throws Exception {
      build(Order.SLOW_FIRST, SLOW_EXPR, "stomp-1");

      CommandDispatcherService dispatcher = mock(CommandDispatcherService.class);
      svc = service(new SheetAgentBroadcastService(dispatcher,
         mock(VSObjectModelFactoryService.class), mock(VSBindingTreeService.class),
         mock(CoreLifecycleService.class)));

      ListAppender<ILoggingEvent> bl = new ListAppender<>();
      bl.start();
      Logger bcastLog = (Logger) LoggerFactory.getLogger(SheetAgentBroadcastService.class);
      bcastLog.addAppender(bl);
      bcastLog.setLevel(Level.DEBUG);
      Logger factoryLog = (Logger) LoggerFactory.getLogger(
         inetsoft.web.composer.ws.assembly.WSAssemblyModelFactory.class);
      factoryLog.addAppender(bl);
      factoryLog.setLevel(Level.DEBUG);

      long t0 = System.currentTimeMillis();

      try {
         apply(ed -> ed.addJoin("J", "Table1", "k", "Table2", "k", "INNER", null, null));
      }
      finally {
         bcastLog.detachAppender(bl);
         factoryLog.detachAppender(bl);
      }

      long ms = System.currentTimeMillis() - t0;
      bl.list.forEach(e -> System.out.println("M8b BCAST LOG " + e.getLevel() + " "
         + e.getFormattedMessage()
         + (e.getThrowableProxy() == null ? "" : " [" + e.getThrowableProxy().getClassName()
            + ": " + e.getThrowableProxy().getMessage() + "]")));
      logged().forEach(l -> System.out.println("M8b LOG " + l));
      System.out.println("M8b add_join with socket session took " + ms + " ms; J private="
                         + count(false) + " model=" + joinColumnNames());

      // documents the observation, whichever way it goes; the primary test is the gate
      assertTrue(ms > 0);
   }

   // ---- M9: a slow refresh already in flight (same definition) still exhausts the window ----

   @Test
   void m9InFlightSameDefinitionRefreshStillExhaustsTheWindow() throws Exception {
      build(Order.SLOW_FIRST, SLOW_EXPR, null);
      apply(ed -> {});
      appender.list.clear();

      long t0 = System.currentTimeMillis();
      apply(ed -> ed.addJoin("J", "Table1", "k", "Table2", "k", "INNER", null, null));
      long ms = System.currentTimeMillis() - t0;
      logged().forEach(l -> System.out.println("M9 LOG " + l));
      System.out.println("M9 second apply took " + ms + " ms; J private=" + count(false));

      assertTrue(ms >= 1800, "the joined in-flight wait still consumes the window, took " + ms);
      assertTrue(anyLog("Skipping refresh for assembly J - refreshAssemblies budget exhausted"),
                 logged().toString());
      assertEquals(0, count(false));
   }

   // ---- fixture -----------------------------------------------------------------------------

   private enum Order { SLOW_FIRST, SLOW_LAST, TABLES_ONLY }

   private void build(Order order, String slowExpression, String socketSessionId)
      throws Exception
   {
      ws = new Worksheet();

      if(order == Order.SLOW_FIRST) {
         ws.addAssembly(slowCrosstab(slowExpression));
      }

      ws.addAssembly(keyed("Table1", "a", new Object[][]{ { 1, "x" }, { 2, "y" } }));
      ws.addAssembly(keyed("Table2", "b", new Object[][]{ { 1, "p" }, { 2, "q" } }));

      if(order == Order.SLOW_LAST) {
         ws.addAssembly(slowCrosstab(slowExpression));
      }

      rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getSocketSessionId()).thenReturn(socketSessionId);
      box = new AssetQuerySandbox(ws, null, new VariableTable());
      box.setActive(true);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      svc = service(mock(SheetAgentBroadcastService.class));
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

   private WorksheetEditService service(SheetAgentBroadcastService broadcast) throws Exception {
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      JoinSession s = new JoinSession("TOK", "Worksheet/ws1", "alice~;~host-org",
                                      SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.WORKSHEET), eq("Worksheet/ws1"), any()))
         .thenReturn(rws);
      return new WorksheetEditService(sessions, runtimeAccess, broadcast, permissive(),
                                      mock(InnerJoinService.class));
   }

   private void apply(ThrowingConsumer<WorksheetEditService.Editor> m) throws Exception {
      svc.apply("TOK", agent, m);
   }

   private int count(boolean pub) {
      return ((TableAssembly) ws.getAssembly("J")).getColumnSelection(pub).getAttributeCount();
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
