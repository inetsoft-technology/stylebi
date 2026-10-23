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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79176 (item 5): duplicate_assembly refuses a mirror's hidden (outer) copy, naming the
 * copy and the word "mirror", instead of cloning it into a second hidden assembly that no
 * mirror owns. A visible mirror is still duplicable.
 */
@Tag("core")
@WizAgentTestSupport
class DuplicateOuterCopy79176Test {
   @Test
   void aHiddenTablePrimaryCopyIsRefused() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly hidden = table(ws, "OUTER_src_0");
      hidden.setOuter(true);
      ws.addAssembly(hidden);
      List<String> before = names(ws);

      PairingException ex = assertThrows(PairingException.class,
         () -> service(ws).apply("TOK", agent, ed -> ed.duplicateAssembly("OUTER_src_0", "COPY")));

      assertRefusal(ex, "OUTER_src_0");
      assertEquals(before, names(ws), "the refusal changed the worksheet");
      assertNull(ws.getAssembly("COPY"));
   }

   @Test
   void aHiddenVariablePrimaryCopyIsRefused() throws Exception {
      Worksheet ws = new Worksheet();
      DefaultVariableAssembly hidden = new DefaultVariableAssembly(ws, "OUTER_src_0");
      hidden.setVariable(new AssetVariable("RATE"));
      hidden.setOuter(true);
      ws.addAssembly(hidden);
      List<String> before = names(ws);

      PairingException ex = assertThrows(PairingException.class,
         () -> service(ws).apply("TOK", agent, ed -> ed.duplicateAssembly("OUTER_src_0", "COPY")));

      assertRefusal(ex, "OUTER_src_0");
      assertEquals(before, names(ws), "the refusal changed the worksheet");
      assertNull(ws.getAssembly("COPY"));
   }

   @Test
   void aVisibleMirrorIsStillDuplicated() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly source = table(ws, "T");
      ws.addAssembly(source);
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M", source);
      ws.addAssembly(mirror);
      assertFalse(mirror.isOuter());

      service(ws).apply("TOK", agent, ed -> ed.duplicateAssembly("M", "M2"));

      WSAssembly copy = (WSAssembly) ws.getAssembly("M2");
      assertInstanceOf(MirrorTableAssembly.class, copy);
      assertFalse(copy.isOuter());
   }

   @Test
   void anOrdinaryAssemblyIsStillDuplicated() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(table(ws, "T"));

      service(ws).apply("TOK", agent, ed -> ed.duplicateAssembly("T", "T2"));

      assertInstanceOf(EmbeddedTableAssembly.class, ws.getAssembly("T2"));
   }

   private static void assertRefusal(PairingException ex, String sourceName) {
      String msg = ex.getMessage();
      assertTrue(msg.contains(sourceName), msg);
      assertTrue(msg.matches("(?is).*mirror.*"), msg);
      assertTrue(msg.contains("hidden copy"), msg);
      // not rewrapped by the try/catch around the clone, and not mistaken for a missing name
      assertFalse(msg.contains("Cannot duplicate assembly"), msg);
      assertFalse(msg.matches("(?is).*(not found|already exists).*"), msg);
   }

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

   private static List<String> names(Worksheet ws) {
      List<String> names = new ArrayList<>();

      for(Assembly assembly : ws.getAssemblies()) {
         names.add(assembly.getName());
      }

      Collections.sort(names);
      return names;
   }
}
