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
import inetsoft.uql.asset.internal.TableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79328: set_primary_assembly and set_table_properties refuse a mirror's hidden (outer)
 * copy, naming the copy and the word "mirror", instead of making a primary nobody can see or
 * editing an assembly the mirror owns.
 */
@Tag("core")
@WizAgentTestSupport
class HiddenCopyPrimary79328Test {
   @Test
   void primaryOnHiddenCopyIsRefusedAndMirrorStaysPrimary() throws Exception {
      Worksheet ws = mirrorOverHiddenCopy();

      PairingException ex = assertThrows(PairingException.class,
         () -> service(ws).apply("TOK", agent, ed -> ed.setPrimaryAssembly("OUTER_src_0")));

      assertRefusal(ex, "OUTER_src_0");
      assertTrue(ex.getMessage().contains("'M'"), ex.getMessage());
      assertEquals("M", ws.getPrimaryAssemblyName());
   }

   @Test
   void propertiesOnHiddenCopyAreRefused() throws Exception {
      Worksheet ws = mirrorOverHiddenCopy();
      TableAssembly hidden = (TableAssembly) ws.getAssembly("OUTER_src_0");

      PairingException ex = assertThrows(PairingException.class, () -> service(ws).apply(
         "TOK", agent, ed -> ed.setTableProperties(
            "OUTER_src_0", null, null, null, null, null, false)));
      assertRefusal(ex, "OUTER_src_0");

      ex = assertThrows(PairingException.class, () -> service(ws).apply(
         "TOK", agent, ed -> ed.setTableProperties(
            "OUTER_src_0", "RENAMED", null, null, null, null, null)));
      assertRefusal(ex, "OUTER_src_0");

      ex = assertThrows(PairingException.class, () -> service(ws).apply(
         "TOK", agent, ed -> ed.setTableProperties(
            "OUTER_src_0", null, null, null, null, null, null)));
      assertRefusal(ex, "OUTER_src_0");

      assertSame(hidden, ws.getAssembly("OUTER_src_0"));
      assertNull(ws.getAssembly("RENAMED"));
      assertTrue(((TableAssemblyInfo) hidden.getTableInfo()).isVisibleTable());
   }

   @Test
   void hiddenVariableCopyCannotBecomePrimary() throws Exception {
      Worksheet ws = mirrorOverHiddenCopy();
      DefaultVariableAssembly v = new DefaultVariableAssembly(ws, "OUTER_var_0");
      v.setVariable(new AssetVariable("RATE"));
      v.setOuter(true);
      ws.addAssembly(v);

      PairingException ex = assertThrows(PairingException.class,
         () -> service(ws).apply("TOK", agent, ed -> ed.setPrimaryAssembly("OUTER_var_0")));

      assertRefusal(ex, "OUTER_var_0");
      assertEquals("M", ws.getPrimaryAssemblyName());
   }

   @Test
   void visibleTableAndMirrorStillBecomePrimary() throws Exception {
      Worksheet ws = mirrorOverHiddenCopy();
      ws.addAssembly(table(ws, "T"));

      service(ws).apply("TOK", agent, ed -> ed.setPrimaryAssembly("T"));
      assertEquals("T", ws.getPrimaryAssemblyName());

      service(ws).apply("TOK", agent, ed -> ed.setPrimaryAssembly("M"));
      assertEquals("M", ws.getPrimaryAssemblyName());
   }

   @Test
   void nonOuterTableStillAcceptsVisibleTableFalse() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = table(ws, "T");
      ws.addAssembly(t);

      service(ws).apply("TOK", agent, ed -> ed.setTableProperties(
         "T", null, null, null, null, null, false));

      assertFalse(((TableAssemblyInfo) t.getTableInfo()).isVisibleTable());
   }

   @Test
   void orphanHiddenCopyFallsBackToGenericMirrorWording() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly hidden = table(ws, "OUTER_src_0");
      hidden.setOuter(true);
      ws.addAssembly(hidden);
      ws.addAssembly(new MirrorTableAssembly(ws, "M", hidden));

      PairingException ex = assertThrows(PairingException.class,
         () -> service(ws).apply("TOK", agent, ed -> ed.setPrimaryAssembly("OUTER_src_0")));

      assertRefusal(ex, "OUTER_src_0");
      assertTrue(ex.getMessage().contains("a mirror"), ex.getMessage());
   }

   @Test
   void nestedHiddenMirrorCopyIsNotNamedAsOwner() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly hidden = table(ws, "OUTER_src_0");
      hidden.setOuter(true);
      ws.addAssembly(hidden);
      MirrorTableAssembly nested = new MirrorTableAssembly(ws, "OUTER_N_0", EXT, true, hidden);
      nested.setOuter(true);
      ws.addAssembly(nested);
      ws.addAssembly(new MirrorTableAssembly(ws, "M", EXT, true, hidden));

      PairingException ex = assertThrows(PairingException.class,
         () -> service(ws).apply("TOK", agent, ed -> ed.setPrimaryAssembly("OUTER_src_0")));

      assertTrue(ex.getMessage().contains("'M'"), ex.getMessage());
      assertFalse(ex.getMessage().contains("OUTER_N_0'"), ex.getMessage());
   }

   private static Worksheet mirrorOverHiddenCopy() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly hidden = table(ws, "OUTER_src_0");
      hidden.setOuter(true);
      ws.addAssembly(hidden);
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M", EXT, true, hidden);
      ws.addAssembly(mirror);
      ws.setPrimaryAssembly("M");
      return ws;
   }

   private static void assertRefusal(PairingException ex, String name) {
      String msg = ex.getMessage();
      assertTrue(msg.contains(name), msg);
      assertTrue(msg.matches("(?is).*mirror.*"), msg);
      assertTrue(msg.contains("hidden copy"), msg);
      assertFalse(msg.matches("(?is).*not found.*"), msg);
   }

   private static final AssetEntry EXT = new AssetEntry(
      AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ext", null);

   private final Principal agent = TestPrincipals.user("alice", "host-org");

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
                                      mock(InnerJoinService.class));
   }

   private static EmbeddedTableAssembly table(Worksheet ws, String name) {
      EmbeddedTableAssembly t = new EmbeddedTableAssembly(ws, name);
      ColumnSelection cs = new ColumnSelection();
      ColumnRef ref = new ColumnRef(new AttributeRef(null, "id"));
      ref.setDataType(XSchema.INTEGER);
      cs.addAttribute(ref);
      t.setColumnSelection(cs, false);
      return t;
   }
}
