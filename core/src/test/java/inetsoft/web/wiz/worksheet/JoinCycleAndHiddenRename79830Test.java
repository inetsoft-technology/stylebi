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
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79830: attaching a table that already depends on a join as a source of that join is
 * refused before anything is stored (add_table_to_join and add_join's extend-in-place branch),
 * and rename_table refuses a mirror's hidden copy.
 */
@Tag("core")
@WizAgentTestSupport
class JoinCycleAndHiddenRename79830Test {
   private static final AssetEntry EXT = new AssetEntry(
      AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ext", null);

   private final Principal agent = TestPrincipals.user("alice", "host-org");

   @Test
   void addTableToJoinRefusesATableThatDependsOnTheJoin() throws Exception {
      Worksheet ws = joinWithDependentMirror();

      PairingException ex = assertThrows(PairingException.class, () -> service(ws).apply(
         "TOK", agent, ed -> ed.addTableToJoin("J", "A", "a1", "MJ", "a1", "INNER", null, null)));

      assertTrue(ex.getMessage().contains("circular dependency"), ex.getMessage());
      assertTrue(ex.getMessage().contains("\"MJ\"") && ex.getMessage().contains("\"J\""),
                 ex.getMessage());
      assertEquals(List.of("A", "B"), sources(ws, "J"));
      assertDoesNotThrow(() -> ws.checkDependencies());
   }

   @Test
   void addJoinExtendInPlaceRefusesATableThatDependsOnTheJoin() throws Exception {
      Worksheet ws = joinWithDependentMirror();

      PairingException ex = assertThrows(PairingException.class, () -> service(ws).apply(
         "TOK", agent, ed -> ed.addJoin("J", "J", "a1", "MJ", "a1", "INNER", null, null)));

      assertTrue(ex.getMessage().contains("circular dependency"), ex.getMessage());
      assertEquals(List.of("A", "B"), sources(ws, "J"));
      assertDoesNotThrow(() -> ws.checkDependencies());
   }

   @Test
   void addTableToJoinStillAcceptsAnIndependentTable() throws Exception {
      Worksheet ws = joinWithDependentMirror();

      service(ws).apply("TOK", agent,
         ed -> ed.addTableToJoin("J", "A", "a1", "D", "d1", "INNER", null, null));

      assertEquals(List.of("A", "B", "D"), sources(ws, "J"));
   }

   @Test
   void renameTableRefusesAHiddenCopyNamingTheMirror() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly hidden = table(ws, "OUTER_src_0", "id");
      hidden.setOuter(true);
      ws.addAssembly(hidden);
      ws.addAssembly(new MirrorTableAssembly(ws, "M", EXT, true, hidden));

      PairingException ex = assertThrows(PairingException.class, () -> service(ws).apply(
         "TOK", agent, ed -> ed.renameTable("OUTER_src_0", "RENAMED")));

      assertTrue(ex.getMessage().contains("OUTER_src_0"), ex.getMessage());
      assertTrue(ex.getMessage().contains("hidden copy"), ex.getMessage());
      assertTrue(ex.getMessage().contains("'M'"), ex.getMessage());
      assertSame(hidden, ws.getAssembly("OUTER_src_0"));
      assertNull(ws.getAssembly("RENAMED"));
   }

   @Test
   void renameTableStillRenamesAVisibleTable() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(table(ws, "T", "id"));

      service(ws).apply("TOK", agent, ed -> ed.renameTable("T", "T2"));

      assertNotNull(ws.getAssembly("T2"));
      assertNull(ws.getAssembly("T"));
   }

   /** J = A join B; MJ mirrors J directly, so MJ depends on J; D is unrelated. */
   private Worksheet joinWithDependentMirror() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "A", "a1"));
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "B", "b1"));
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "D", "d1"));
      service(ws).apply("TOK", agent,
         ed -> ed.addJoin("J", "A", "a1", "B", "b1", "INNER", null, null));
      ws.addAssembly(new MirrorTableAssembly(ws, "MJ", (WSAssembly) ws.getAssembly("J")));
      return ws;
   }

   private static List<String> sources(Worksheet ws, String join) {
      return Arrays.asList(((RelationalJoinTableAssembly) ws.getAssembly(join)).getTableNames());
   }

   private static EmbeddedTableAssembly table(Worksheet ws, String name, String column) {
      EmbeddedTableAssembly t = new EmbeddedTableAssembly(ws, name);
      ColumnSelection cs = new ColumnSelection();
      ColumnRef ref = new ColumnRef(new AttributeRef(null, column));
      ref.setDataType(XSchema.INTEGER);
      cs.addAttribute(ref);
      t.setColumnSelection(cs, false);
      return t;
   }

   private WorksheetEditService service(Worksheet ws) throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      JoinSession s = new JoinSession("TOK", "Worksheet/ws1", "alice~;~host-org",
                                      SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(
         eq(SheetType.WORKSHEET), eq("Worksheet/ws1"), eq(agent))).thenReturn(rws);
      return new WorksheetEditService(sessions, runtimeAccess,
                                      mock(SheetAgentBroadcastService.class), securityEngine,
                                      new InnerJoinService(null, null));
   }
}
