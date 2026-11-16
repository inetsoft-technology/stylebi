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
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.erm.AttributeRef;
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
 * Bug #81450: when an earlier, slow table uses up the shared budget of refreshAssemblies, a later
 * table is skipped, including the {@code AssetUtil.validateConditions} that drops a condition on a
 * column an earlier change removed. Nothing may be deleted on a stale selection, so the edit tells
 * the caller which tables carry conditions that were not re-checked.
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
               WorksheetEditServiceBug81450Test.TestConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetEditServiceBug81450Test {
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

   @Test
   void starvedTableWithAConditionIsDisclosedAndItsConditionIsKept() throws Exception {
      build(true, true);

      String note = apply(ed -> {});

      assertTrue(anyLog("Skipping refresh for assembly Table1 - refreshAssemblies budget exhausted"),
                 "setup error, Table1 was not skipped by the budget: " + logged());
      assertNotNull(note, "the skipped table carries a condition and must be disclosed");
      assertTrue(note.contains("'Table1'"), note);
      assertTrue(note.contains("condition"), note);
      assertTrue(note.contains("refresh_data"), note);
      assertFalse(note.contains("'Slow'") || note.contains("'Table2'"), note);
      assertEquals(1, table1().getPreConditionList().getConditionSize(),
                   "nothing is deleted on a possibly stale selection");
   }

   @Test
   void starvedTablesWithoutConditionsStayQuiet() throws Exception {
      build(true, false);

      assertNull(apply(ed -> {}));
   }

   @Test
   void healthyWorksheetValidatesRemovesTheDanglingConditionAndReportsNothing() throws Exception {
      build(false, true);

      assertNull(apply(ed -> {}));
      assertEquals(0, table1().getPreConditionList().getConditionSize(),
                   "control: a fresh refresh still drops a condition on a missing column");
   }

   @Test
   void controllerAppendsTheDisclosureAfterTheTrapText() {
      assertEquals("trap\n\nstale", WorksheetAgentController.appendNote("trap", "stale"));
      assertEquals("stale", WorksheetAgentController.appendNote(null, "stale"));
      assertEquals("trap", WorksheetAgentController.appendNote("trap", null));
      assertNull(WorksheetAgentController.appendNote(null, null));
   }

   // ---- fixture -----------------------------------------------------------------------------

   private void build(boolean slow, boolean condition) throws Exception {
      ws = new Worksheet();

      if(slow) {
         ws.addAssembly(slowCrosstab(SLOW_EXPR));
      }

      ws.addAssembly(keyed("Table1", "a", new Object[][]{ { 1, "x" }, { 2, "y" } }));
      ws.addAssembly(keyed("Table2", "b", new Object[][]{ { 1, "p" }, { 2, "q" } }));

      if(condition) {
         // a condition on a column the table does not have: what an earlier edit leaves behind
         AssetCondition cond = new AssetCondition();
         cond.setOperation(XCondition.EQUAL_TO);
         cond.setType(XSchema.STRING);
         cond.addValue("a");
         ConditionList list = new ConditionList();
         list.append(new ConditionItem(new ColumnRef(new AttributeRef("gone")), cond, 0));
         table1().setPreConditionList(list);
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

   private TableAssembly table1() {
      return (TableAssembly) ws.getAssembly("Table1");
   }

   /** Applies the mutation and returns what the edit would add to the response note. */
   private String apply(ThrowingConsumer<WorksheetEditService.Editor> m) throws Exception {
      if(firstApplyAt == 0) {
         firstApplyAt = System.currentTimeMillis();
      }

      WorksheetEditService.Editor[] holder = new WorksheetEditService.Editor[1];
      svc.apply("TOK", agent, editor -> {
         holder[0] = editor;
         m.accept(editor);
      });
      return holder[0].unrevalidatedNote();
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
