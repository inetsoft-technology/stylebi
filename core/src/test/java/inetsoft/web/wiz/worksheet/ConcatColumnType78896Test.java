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
import inetsoft.uql.erm.DataRef;
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
 * Bug #78896: read_worksheet_model reported a concatenation column with its first subtable's
 * type although the rows (and a mirror of the concatenation) carry the merged type. Runs the
 * production edit + refresh path against a real, active sandbox.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // the Spring context the real column refresh needs
class ConcatColumnType78896Test {
   private final Principal agent = TestPrincipals.user("alice", "host-org");
   private AssetQuerySandbox box;
   private RuntimeWorksheet rws;

   @Test
   void unionOfIntegerAndDoubleReadsAsDouble() throws Exception {
      Worksheet ws = worksheet("integer", "double", "integer");
      concat(ws, "U", "UNION", "A", "B");
      assertEquals("double", readType("U", "K"));
      assertEquals("integer", readType("U", "V"));
   }

   @Test
   void intersectReadsAsDouble() throws Exception {
      Worksheet ws = worksheet("integer", "double", "integer");
      concat(ws, "U", "INTERSECT", "A", "B");
      assertEquals("double", readType("U", "K"));
   }

   @Test
   void doubleFirstReadsAsDouble() throws Exception {
      Worksheet ws = worksheet("integer", "double", "integer");
      concat(ws, "U", "UNION", "B", "A");
      assertEquals("double", readType("U", "K"));
   }

   @Test
   void bothIntegerStaysInteger() throws Exception {
      Worksheet ws = worksheet("integer", "integer", "integer");
      concat(ws, "U", "UNION", "A", "B");
      assertEquals("integer", readType("U", "K"));
   }

   @Test
   void stringColumnStaysString() throws Exception {
      Worksheet ws = worksheet("string", "string", "string");
      concat(ws, "U", "UNION", "A", "B");
      assertEquals("string", readType("U", "K"));
   }

   @Test
   void nestedConcatenationReadsTheMergedTypeOfItsInnerConcatenation() throws Exception {
      Worksheet ws = worksheet("integer", "double", "integer");
      concat(ws, "U", "UNION", "A", "B");
      concat(ws, "U2", "UNION", "U", "C");
      assertEquals("double", readType("U2", "K"));
   }

   @Test
   void expressionColumnKeepsItsOwnTypeAndKIsStillMerged() throws Exception {
      Worksheet ws = worksheet("integer", "double", "integer");
      concat(ws, "U", "UNION", "A", "B");
      service(ws).apply("TOK", agent,
                        ed -> ed.addExpressionColumn("U", "ex", "field['V'] * 2", "integer", false));
      assertEquals("double", readType("U", "K"));
      assertEquals("integer", readType("U", "ex"));
   }

   @Test
   void joinKeyOnAConcatenationUsesTheMergedTypeWithoutTouchingTheStoredOne() throws Exception {
      Worksheet ws = worksheet("integer", "double", "integer");
      concat(ws, "U", "UNION", "A", "B");
      ConcatenatedTableAssembly u = (ConcatenatedTableAssembly) ws.getAssembly("U");
      String stored = storedType(u);

      DataRef key = WorksheetMutationSupport.resolveJoinKey(u, "K");

      assertNotNull(key);
      assertEquals("double", key.getDataType());
      assertEquals(stored, storedType(u), "the stored selection must not be rewritten");
   }

   private static String storedType(TableAssembly t) {
      return ((ColumnRef) t.getColumnSelection(false).getAttribute("K")).getDataType();
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

   /** Tables A, B, C with columns K and V; K has the given type in each, V is an integer. */
   private static Worksheet worksheet(String ka, String kb, String kc) {
      Worksheet ws = new Worksheet();
      String[] names = { "A", "B", "C" };
      String[] kTypes = { ka, kb, kc };

      for(int i = 0; i < names.length; i++) {
         EmbeddedTableAssembly table = TestWorksheets.tableWithColumns(ws, names[i], "K", "V");
         String[] types = { kTypes[i], "integer" };
         ColumnSelection selection = table.getColumnSelection();

         for(int j = 0; j < types.length; j++) {
            ((ColumnRef) selection.getAttribute(j)).setDataType(types[j]);
         }

         Object[][] rows = { { "K", "V" }, { value(kTypes[i], 1), 10 }, { value(kTypes[i], 2), 20 } };
         table.setEmbeddedData(new XEmbeddedTable(types, rows));
         ws.addAssembly(table);
      }

      return ws;
   }

   private static Object value(String type, int n) {
      return switch(type) {
         case "double" -> n + 0.5;
         case "string" -> "s" + n;
         default -> n;
      };
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
