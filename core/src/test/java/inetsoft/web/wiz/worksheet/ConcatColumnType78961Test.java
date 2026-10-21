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
 * Bug #78961: a concatenation stores its first subtable's column type, so a fractional condition
 * value on a UNION of an integer and a double column was refused ("not a valid integer") although
 * the rows and a mirror carry the merged type, and a grouped concatenation reported its group
 * column with the narrowed type. Runs the production edit + refresh path.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // the Spring context the real column refresh needs
class ConcatColumnType78961Test {
   private final Principal agent = TestPrincipals.user("alice", "host-org");
   private AssetQuerySandbox box;
   private RuntimeWorksheet rws;

   @Test
   void addFilterAcceptsAFractionOnAnIntegerPlusDoubleConcatenation() throws Exception {
      Worksheet ws = worksheet("integer", "double", "integer");
      concat(ws, "U", "UNION", "A", "B");
      service(ws).apply("TOK", agent, ed -> ed.addFilter("U", "K", "=", "2.5"));
      assertNotNull(((TableAssembly) ws.getAssembly("U")).getPreConditionList());
   }

   @Test
   void addFilterStillRefusesAFractionWhenEverySubtableIsInteger() throws Exception {
      Worksheet ws = worksheet("integer", "integer", "integer");
      concat(ws, "U", "UNION", "A", "B");
      assertThrows(Exception.class,
                   () -> service(ws).apply("TOK", agent, ed -> ed.addFilter("U", "K", "=", "2.5")));
   }

   @Test
   void setConditionsAcceptsAFractionOnAnIntegerPlusDoubleConcatenation() throws Exception {
      Worksheet ws = worksheet("integer", "double", "integer");
      concat(ws, "U", "UNION", "A", "B");
      service(ws).apply("TOK", agent, ed -> ed.setConditions("U", equalsTo("K", "2.5")));
      assertNotNull(((TableAssembly) ws.getAssembly("U")).getPreConditionList());
   }

   @Test
   void setConditionsStillRefusesAFractionWhenEverySubtableIsInteger() throws Exception {
      Worksheet ws = worksheet("integer", "integer", "integer");
      concat(ws, "U", "UNION", "A", "B");
      assertThrows(Exception.class, () ->
         service(ws).apply("TOK", agent, ed -> ed.setConditions("U", equalsTo("K", "2.5"))));
   }

   @Test
   void nestedConcatenationAcceptsAFraction() throws Exception {
      Worksheet ws = worksheet("integer", "double", "integer");
      concat(ws, "U", "UNION", "A", "B");
      concat(ws, "N", "UNION", "C", "U");
      service(ws).apply("TOK", agent, ed -> ed.addFilter("N", "K", "=", "2.5"));
      assertNotNull(((TableAssembly) ws.getAssembly("N")).getPreConditionList());
   }

   @Test
   void groupedConcatenationReadsTheGroupColumnAtTheMergedType() throws Exception {
      Worksheet ws = worksheet("integer", "double", "integer");
      concat(ws, "U", "UNION", "A", "B");
      group(ws);
      assertEquals("double", readType("U", "K"));
   }

   @Test
   void aggregateOutputNamedLikeASourceColumnKeepsItsOwnType() throws Exception {
      // V is double in B, so the merged V is double, but Count Distinct(V) is an integer output
      Worksheet ws = worksheet("integer", "integer", "integer", "integer", "double");
      concat(ws, "U", "UNION", "A", "B");
      group(ws);
      assertEquals("integer", readType("U", "K"));
      assertEquals("integer", readType("U", "V"));
   }

   @Test
   void postConditionOnAGroupedConcatenationAcceptsAFraction() throws Exception {
      Worksheet ws = worksheet("integer", "double", "integer");
      concat(ws, "U", "UNION", "A", "B");
      group(ws);
      service(ws).apply("TOK", agent, ed -> ed.setPostConditions("U", equalsTo("K", "2.5")));
      assertNotNull(((TableAssembly) ws.getAssembly("U")).getPostConditionList());
   }

   private void group(Worksheet ws) throws Exception {
      service(ws).apply("TOK", agent, ed -> ed.setGroupAggregate(
         "U", List.of(new WorksheetMutationSupport.GroupSpec("K")),
         List.of(new WorksheetMutationSupport.AggregateSpec(
            "V", "Count Distinct", null, null))));
   }

   private static List<WorksheetMutationSupport.ConditionNode> equalsTo(String field, String value) {
      return List.of(new WorksheetMutationSupport.ConditionNode(
         new WorksheetMutationSupport.ConditionSpec(
            field, "=", List.of(value), false, null, null), null, 0));
   }

   private String readType(String table, String column) {
      WorksheetModel model = new WorksheetReadService().read(rws);

      return model.tables().stream().filter(t -> t.name().equals(table)).findFirst().orElseThrow()
         .columns().stream().filter(c -> c.name().equals(column)).findFirst().orElseThrow()
         .type();
   }

   private void concat(Worksheet ws, String name, String op, String... subs) throws Exception {
      service(ws).apply("TOK", agent, ed -> ed.addConcatenation(name, List.of(subs), op));
   }

   private static Worksheet worksheet(String ka, String kb, String kc) {
      return worksheet(ka, kb, kc, "integer", "integer");
   }

   /** Tables A, B, C with columns K and V; V is va in A and vb in B and C. */
   private static Worksheet worksheet(String ka, String kb, String kc, String va, String vb) {
      Worksheet ws = new Worksheet();
      String[] names = { "A", "B", "C" };
      String[] kTypes = { ka, kb, kc };
      String[] vTypes = { va, vb, vb };

      for(int i = 0; i < names.length; i++) {
         EmbeddedTableAssembly table = TestWorksheets.tableWithColumns(ws, names[i], "K", "V");
         String[] types = { kTypes[i], vTypes[i] };
         ColumnSelection selection = table.getColumnSelection();

         for(int j = 0; j < types.length; j++) {
            ((ColumnRef) selection.getAttribute(j)).setDataType(types[j]);
         }

         Object[][] rows = { { "K", "V" }, { value(kTypes[i], 1), value(vTypes[i], 10) },
                             { value(kTypes[i], 2), value(vTypes[i], 20) } };
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
