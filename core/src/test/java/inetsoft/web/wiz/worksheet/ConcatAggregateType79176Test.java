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

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.worksheet.model.WorksheetModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79176 (item 2): an aggregate output over a mixed integer/double concatenation column took
 * the first subtable's narrowed type, so Max/Min/First/Last/Mode read integer (and a fractional
 * post-condition on them was refused) while Sum, whose type is fixed, read double. Runs the
 * production edit + read path.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // the Spring context the real column refresh needs
class ConcatAggregateType79176Test {
   // table names are unique per test: the embedded-data and column-metadata caches are keyed by
   // name and would otherwise leak one test's column types into another
   private static final java.util.concurrent.atomic.AtomicInteger SEQ =
      new java.util.concurrent.atomic.AtomicInteger();
   private final int n = SEQ.incrementAndGet();
   private final String a = "A" + n;
   private final String b = "B" + n;
   private final String u = "U" + n;
   private final Principal agent = TestPrincipals.user("alice", "host-org");
   private AssetQuerySandbox box;
   private RuntimeWorksheet rws;

   @Test
   void maxOverMixedColumnReadsDouble() throws Exception {
      Worksheet ws = worksheet("integer", "double");
      concat(ws, u, a, b);
      aggregate(ws, new WorksheetMutationSupport.AggregateSpec("V", "Max", null, null));
      assertEquals("double", readType(u, "V"));
   }

   @Test
   void maxAndMinOnOneMixedColumnBothReadDouble() throws Exception {
      Worksheet ws = worksheet("integer", "double");
      concat(ws, u, a, b);
      aggregate(ws, new WorksheetMutationSupport.AggregateSpec("V", "Max", "MAXV", null),
                new WorksheetMutationSupport.AggregateSpec("V", "Min", "MINV", null));
      assertEquals("double", readType(u, "MAXV"));
      assertEquals("double", readType(u, "MINV"));
   }

   @Test
   void firstReadsDouble() throws Exception {
      assertSingleAggregateReadsDouble("First");
   }

   @Test
   void lastReadsDouble() throws Exception {
      assertSingleAggregateReadsDouble("Last");
   }

   @Test
   void modeReadsDouble() throws Exception {
      assertSingleAggregateReadsDouble("Mode");
   }

   private void assertSingleAggregateReadsDouble(String formula) throws Exception {
      Worksheet ws = worksheet("integer", "double");
      concat(ws, u, a, b);
      aggregate(ws, new WorksheetMutationSupport.AggregateSpec("V", formula, "X", null));
      assertEquals("double", readType(u, "X"), formula);
   }

   @Test
   void sumControlReadsDouble() throws Exception {
      Worksheet ws = worksheet("integer", "double");
      concat(ws, u, a, b);
      aggregate(ws, new WorksheetMutationSupport.AggregateSpec("V", "Sum", "S", null));
      assertEquals("double", readType(u, "S"));
   }

   @Test
   void maxOverAnAllIntegerColumnStaysInteger() throws Exception {
      Worksheet ws = worksheet("integer", "integer");
      concat(ws, u, a, b);
      aggregate(ws, new WorksheetMutationSupport.AggregateSpec("V", "Max", "MAXV", null),
                new WorksheetMutationSupport.AggregateSpec("V", "Min", "MINV", null));
      assertEquals("integer", readType(u, "MAXV"));
      assertEquals("integer", readType(u, "MINV"));
   }

   @Test
   void fractionalPostConditionOnMaxAndMinIsAccepted() throws Exception {
      Worksheet ws = worksheet("integer", "double");
      concat(ws, u, a, b);
      aggregate(ws, new WorksheetMutationSupport.AggregateSpec("V", "Max", "MAXV", null),
                new WorksheetMutationSupport.AggregateSpec("V", "Min", "MINV", null));
      service(ws).apply("TOK", agent, ed -> ed.setPostConditions(u, greaterEqual("MAXV", "30.2")));
      assertNotNull(((TableAssembly) ws.getAssembly(u)).getPostConditionList());
      service(ws).apply("TOK", agent, ed -> ed.setPostConditions(u, greaterEqual("MINV", "30.2")));
      assertNotNull(((TableAssembly) ws.getAssembly(u)).getPostConditionList());
   }

   @Test
   void fractionalPostConditionOnAllIntegerMaxIsStillRefused() throws Exception {
      Worksheet ws = worksheet("integer", "integer");
      concat(ws, u, a, b);
      aggregate(ws, new WorksheetMutationSupport.AggregateSpec("V", "Max", "MAXV", null));
      assertThrows(Exception.class, () -> service(ws).apply(
         "TOK", agent, ed -> ed.setPostConditions(u, greaterEqual("MAXV", "30.2"))));
   }

   private void aggregate(Worksheet ws, WorksheetMutationSupport.AggregateSpec... aggs)
      throws Exception
   {
      service(ws).apply("TOK", agent, ed -> ed.setGroupAggregate(
         u, List.of(new WorksheetMutationSupport.GroupSpec("K")), List.of(aggs)));
   }

   private static List<WorksheetMutationSupport.ConditionNode> greaterEqual(
      String field, String value)
   {
      return List.of(new WorksheetMutationSupport.ConditionNode(
         new WorksheetMutationSupport.ConditionSpec(
            field, ">=", List.of(value), false, null, null), null, 0));
   }

   private String readType(String table, String column) {
      WorksheetModel model = new WorksheetReadService().read(rws);

      var columns = model.tables().stream().filter(t -> t.name().equals(table)).findFirst()
         .orElseThrow().columns();

      return columns.stream().filter(c -> column.equals(c.alias()) || (c.alias() == null && c.name().equals(column)))
         .findFirst()
         .orElseThrow(() -> new AssertionError("no column " + column + " in " + columns))
         .type();
   }

   private void concat(Worksheet ws, String name, String... subs) throws Exception {
      service(ws).apply("TOK", agent, ed -> ed.addConcatenation(name, List.of(subs), "UNION"));
   }

   /** Tables A and B with columns K (integer) and V; V is va in A and vb in B. */
   private Worksheet worksheet(String va, String vb) {
      Worksheet ws = new Worksheet();
      String[] names = { a, b };
      String[] vTypes = { va, vb };

      for(int i = 0; i < names.length; i++) {
         EmbeddedTableAssembly table = TestWorksheets.tableWithColumns(ws, names[i], "K", "V");
         String[] types = { "integer", vTypes[i] };
         ColumnSelection selection = table.getColumnSelection();

         for(int j = 0; j < types.length; j++) {
            ((ColumnRef) selection.getAttribute(j)).setDataType(types[j]);
         }

         Object[][] rows = { { "K", "V" }, { 1, value(vTypes[i], 10) },
                             { 2, value(vTypes[i], 20) } };
         table.setEmbeddedData(new XEmbeddedTable(types, rows));
         ws.addAssembly(table);
      }

      return ws;
   }

   private static Object value(String type, int n) {
      return "double".equals(type) ? n + 0.5 : n;
   }

   private WorksheetEditService service(Worksheet ws) throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);

      if(rws == null) {
         rws = mock(RuntimeWorksheet.class);
         box = new AssetQuerySandbox(ws);
         box.setActive(true);
         when(rws.getAssetQuerySandbox()).thenReturn(box);
      }

      when(rws.getWorksheet()).thenReturn(ws);
      JoinSession s = new JoinSession("TOK", "Worksheet/ws1", "alice~;~host-org",
                                      SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(
         eq(SheetType.WORKSHEET), eq("Worksheet/ws1"), eq(agent))).thenReturn(rws);
      return new WorksheetEditService(sessions, runtimeAccess, broadcast, securityEngine,
                                      new InnerJoinService(null, null));
   }
}
