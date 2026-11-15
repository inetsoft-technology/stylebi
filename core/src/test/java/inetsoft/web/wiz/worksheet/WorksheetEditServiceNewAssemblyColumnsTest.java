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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81354 fix coverage: every plain assembly the Editor creates in one {@code apply} gets its
 * columns even when an earlier slow non-plain table exhausts {@code refreshAssemblies}' shared
 * budget. One case per creation path claimed by the fix, plus the boundaries. Same harness as
 * {@link WorksheetEditServiceBug81354Test} (real Worksheet/sandbox/apply/Editor, mock
 * RuntimeWorksheet shell, slow crosstab first).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
               LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
               WorksheetEditServiceNewAssemblyColumnsTest.TestConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetEditServiceNewAssemblyColumnsTest {
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
    * Busy-waits longer than two 2000 ms refresh budgets: the redo test applies twice and the
    * second apply joins the first one's in-flight refresh. Scripts cannot reach test code or
    * Thread ("Class not permitted in scripts"), so it cannot be cancelled; a leaked spinner
    * outlives its test by up to ~2.5 s.
    */
   private static final String SLOW_EXPR =
      "var t = Date.now(); while(Date.now() - t < 4500) {} 1";

   private final Principal agent = TestPrincipals.user("alice", "host-org");
   private Worksheet ws;
   private RuntimeWorksheet rws;
   private WorksheetEditService svc;
   private Logger editLog;
   private ListAppender<ILoggingEvent> appender;

   @BeforeEach
   void setUp() throws Exception {
      editLog = (Logger) LoggerFactory.getLogger(WorksheetEditService.class);
      appender = new ListAppender<>();
      appender.start();
      editLog.addAppender(appender);

      ws = new Worksheet();
      ws.addAssembly(slowCrosstab());
      ws.addAssembly(keyed("Table1", "a"));
      ws.addAssembly(keyed("Table2", "b"));
      ws.addAssembly(keyed("Table3", "c"));
      rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = new AssetQuerySandbox(ws, null, new VariableTable());
      box.setActive(true);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      svc = service();
   }

   @AfterEach
   void tearDown() {
      editLog.detachAppender(appender);
   }

   @Test
   void twoTableJoin() throws Exception {
      svc.apply("TOK", agent,
                ed -> ed.addJoin("J", "Table1", "k", "Table2", "k", "INNER", null, null));
      assertBudgetWasExhausted("J");
      assertEquals(List.of("k", "a", "k", "b"), columns("J"));
   }

   @Test
   void crossJoin() throws Exception {
      svc.apply("TOK", agent, ed -> ed.addCrossJoin("J", "Table1", "Table2"));
      assertBudgetWasExhausted("J");
      assertEquals(List.of("k", "a", "k", "b"), columns("J"));
   }

   @Test
   void mergeJoin() throws Exception {
      svc.apply("TOK", agent, ed -> ed.addMergeJoin("J", new String[]{ "Table1", "Table2" }));
      assertBudgetWasExhausted("J");
      assertEquals(List.of("k", "a", "k", "b"), columns("J"));
   }

   @Test
   void multiTableJoin() throws Exception {
      svc.apply("TOK", agent, ed -> ed.addJoin("J", List.of(
         new WorksheetMutationSupport.JoinPathSpec("Table1", "k", "Table2", "k", "INNER"),
         new WorksheetMutationSupport.JoinPathSpec("Table2", "k", "Table3", "k", "INNER"))));
      assertBudgetWasExhausted("J");
      assertEquals(List.of("k", "a", "k", "b", "k", "c"), columns("J"));
   }

   @Test
   void concatenation() throws Exception {
      svc.apply("TOK", agent,
                ed -> ed.addConcatenation("J", List.of("Table1", "Table3"), "UNION"));
      assertBudgetWasExhausted("J");
      assertEquals(List.of("k", "a"), columns("J"));
   }

   @Test
   void mirror() throws Exception {
      svc.apply("TOK", agent, ed -> ed.addMirror("J", "Table1"));
      assertBudgetWasExhausted("J");
      assertEquals(List.of("k", "a"), columns("J"));
   }

   /** An existing assembly whose selection is already populated is not touched. */
   @Test
   void existingAssemblyIsNotReinitialized() throws Exception {
      TableAssembly t1 = (TableAssembly) ws.getAssembly("Table1");
      Object first = t1.getColumnSelection(false).getAttribute(0);
      svc.apply("TOK", agent, ed -> {});
      assertSame(first, t1.getColumnSelection(false).getAttribute(0));
   }

   /** An empty assembly not created by this apply is left to the budgeted loop. */
   @Test
   void assemblyNotCreatedByThisApplyIsNotInitialized() throws Exception {
      new WorksheetEditService.Editor(ws, agent, permissive(), mock(InnerJoinService.class), null,
                                      () -> null)
         .addJoin("Old", "Table1", "k", "Table2", "k", "INNER", null, null);
      assertTrue(columns("Old").isEmpty(), "precondition: created outside apply, so empty");

      svc.apply("TOK", agent, ed -> {});

      assertTrue(columns("Old").isEmpty());
   }

   /** Delete-and-recreate under the same name in one apply counts as new (identity, not name). */
   @Test
   void recreatedAssemblyWithSameNameIsInitialized() throws Exception {
      svc.apply("TOK", agent,
                ed -> ed.addJoin("J", "Table1", "k", "Table2", "k", "INNER", null, null));
      assertFalse(columns("J").isEmpty());
      appender.list.clear();

      svc.apply("TOK", agent, ed -> {
         ed.deleteTable("J");
         ed.addJoin("J", "Table2", "k", "Table3", "k", "INNER", null, null);
      });

      assertEquals(List.of("k", "b", "k", "c"), columns("J"));
   }

   /**
    * The checkpoint stored by the creating edit must carry the columns: redo installs that
    * snapshot and does not run the early init, and its own refresh is starved by the slow table.
    */
   @Test
   void redoOfTheCreatingEditRestoresTheColumns() throws Exception {
      List<AbstractSheet> checkpoints = new ArrayList<>();
      when(rws.addCheckpoint(any())).thenAnswer(inv -> {
         checkpoints.add(inv.getArgument(0));
         return checkpoints.size() - 1;
      });

      svc.apply("TOK", agent,
                ed -> ed.addJoin("J", "Table1", "k", "Table2", "k", "INNER", null, null));
      assertEquals(1, checkpoints.size());

      // what RuntimeWorksheet.redo does: install a clone of the stored checkpoint
      Worksheet redone = (Worksheet) checkpoints.get(0).clone();
      when(rws.getWorksheet()).thenReturn(redone);
      AssetQuerySandbox box2 = new AssetQuerySandbox(redone, null, new VariableTable());
      box2.setActive(true);
      when(rws.getAssetQuerySandbox()).thenReturn(box2);
      ws = redone;

      svc.applyOnRuntimeNoCheckpoint("TOK", agent, r -> null);

      assertEquals(List.of("k", "a", "k", "b"), columns("J"));
   }

   /**
    * A join whose column refresh blocks (a source reaching slow metadata) must not hang the edit:
    * the early init has its own cap, the edit returns and the join is simply still empty.
    */
   @Test
   void blockedColumnRefreshDoesNotHangTheEdit() throws Exception {
      ws.removeAssembly("Slow");
      CountDownLatch blocked = new CountDownLatch(1);
      CountDownLatch entered = new CountDownLatch(1);
      AssetQuerySandbox spyBox = spy(new AssetQuerySandbox(ws, null, new VariableTable()));
      spyBox.setActive(true);
      doAnswer(inv -> {
         if("J".equals(inv.getArgument(0))) {
            entered.countDown();
            blocked.await(30, TimeUnit.SECONDS);
         }

         return inv.callRealMethod();
      }).when(spyBox).refreshColumnSelection(eq("J"), anyBoolean(), anyBoolean());
      when(rws.getAssetQuerySandbox()).thenReturn(spyBox);

      try {
         long t0 = System.currentTimeMillis();
         svc.apply("TOK", agent,
                   ed -> ed.addJoin("J", "Table1", "k", "Table2", "k", "INNER", null, null));
         long ms = System.currentTimeMillis() - t0;

         assertTrue(entered.await(1, TimeUnit.SECONDS), "the blocking refresh was never reached");
         assertTrue(ms < 10_000, "edit must return despite a blocked refresh, took " + ms);
         assertEquals(List.of(), columns("J"));
      }
      finally {
         blocked.countDown();
      }
   }

   /** Rotate is non-plain: its refresh executes the source query, so it stays with the loop. */
   @Test
   void nonPlainNewAssemblyIsNotRefreshedEarly() throws Exception {
      svc.apply("TOK", agent, ed -> ed.addRotate("R", "Table1"));
      assertTrue(logged().stream().anyMatch(l -> l.contains("Skipping refresh for assembly R")),
                 logged().toString());
   }

   private void assertBudgetWasExhausted(String name) {
      assertTrue(logged().stream().anyMatch(
         l -> l.contains("Skipping refresh for assembly " + name + " - refreshAssemblies budget")),
         "setup error, the shared budget did not starve " + name + ": " + logged());
   }

   private List<String> logged() {
      List<String> l = new ArrayList<>();

      for(ILoggingEvent e : new ArrayList<>(appender.list)) {
         l.add(e.getLevel() + " " + e.getFormattedMessage());
      }

      return l;
   }

   private List<String> columns(String name) {
      List<String> names = new ArrayList<>();

      for(WorksheetModel.TableModel t : new WorksheetReadService().read(rws).tables()) {
         if(name.equals(t.name())) {
            t.columns().forEach(c -> names.add(c.name()));
         }
      }

      return names;
   }

   private TableAssembly slowCrosstab() throws Exception {
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "Slow", "g1", "g2");
      t.setEmbeddedData(new XEmbeddedTable(
         new String[]{ "string", "string" },
         new Object[][]{ { "g1", "g2" }, { "u", "v" } }));
      ColumnSelection cs = t.getColumnSelection(false);

      for(String c : List.of("g1", "g2")) {
         ((ColumnRef) cs.getAttribute(c)).setDataType(XSchema.STRING);
      }

      WorksheetMutationSupport.addExpressionColumn(t, "w", SLOW_EXPR, "integer", false);
      WorksheetMutationSupport.applyAggregateInfo(t,
         List.of(new WorksheetMutationSupport.GroupSpec("g1"),
                 new WorksheetMutationSupport.GroupSpec("g2")),
         List.of(new WorksheetMutationSupport.AggregateSpec("w", "SUM", null)), true, true);
      return t;
   }

   private EmbeddedTableAssembly keyed(String name, String other) {
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, name, "k", other);
      t.setEmbeddedData(new XEmbeddedTable(new String[]{ "integer", "string" },
         new Object[][]{ { "k", other }, { 1, "x" }, { 2, "y" } }));
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
}
