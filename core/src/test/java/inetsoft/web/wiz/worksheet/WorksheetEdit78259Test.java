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
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Redmine #78259: remove_concat_subtable must not orphan dependents (WBS-111), and a mappings-only
 * edit_named_group must keep a type-attached group's data type (WBS-113).
 */
@Tag("core")
@WizAgentTestSupport
class WorksheetEdit78259Test {
   private WorksheetEditService service(Worksheet ws, Principal agent) throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);
      JoinSession s = new JoinSession("TOK", "Worksheet/ws1", "alice~;~host-org",
                                      SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.WORKSHEET), eq("Worksheet/ws1"), eq(agent)))
         .thenReturn(rws);
      return new WorksheetEditService(
         sessions, runtimeAccess, broadcast, securityEngine, mock(InnerJoinService.class));
   }

   private static EmbeddedTableAssembly table(Worksheet ws, String name) {
      EmbeddedTableAssembly t = new EmbeddedTableAssembly(ws, name);
      ColumnSelection cs = new ColumnSelection();
      ColumnRef c = new ColumnRef(new AttributeRef(null, "n"));
      c.setDataType(XSchema.INTEGER);
      cs.addAttribute(c);
      t.setColumnSelection(cs, false);
      ws.addAssembly(t);
      return t;
   }

   private Worksheet concatWorksheet(int sources) {
      Worksheet ws = new Worksheet();

      for(int i = 1; i <= sources; i++) {
         table(ws, "M" + i);
      }

      return ws;
   }

   private Worksheet build(WorksheetEditService svc, Principal agent, Worksheet ws, int sources,
                           boolean withMirror) throws Exception
   {
      List<String> names = new java.util.ArrayList<>();

      for(int i = 1; i <= sources; i++) {
         names.add("M" + i);
      }

      svc.apply("TOK", agent, ed -> ed.addConcatenation("CC", names, "UNION"));

      if(withMirror) {
         svc.apply("TOK", agent, ed -> ed.addMirror("CCM", "CC"));
      }

      return ws;
   }

   @Test
   void removeConcatSubtableRefusesWhenItWouldDeleteAConcatWithDependents() throws Exception {
      Worksheet ws = concatWorksheet(2);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(ws, agent);
      build(svc, agent, ws, 2, true);

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.removeConcatSubtable("CC", "M2")));

      assertTrue(ex.getMessage().contains("CCM"), ex.getMessage());
      assertNotNull(ws.getAssembly("CC"));
      assertNotNull(ws.getAssembly("CCM"));
      assertEquals(2, ((ConcatenatedTableAssembly) ws.getAssembly("CC")).getTableAssemblies().length);
   }

   @Test
   void removeConcatSubtableAllowedWhenTwoSourcesRemainDespiteDependents() throws Exception {
      Worksheet ws = concatWorksheet(3);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(ws, agent);
      build(svc, agent, ws, 3, true);

      svc.apply("TOK", agent, ed -> ed.removeConcatSubtable("CC", "M3"));

      assertEquals(2, ((ConcatenatedTableAssembly) ws.getAssembly("CC")).getTableAssemblies().length);
      assertNotNull(ws.getAssembly("CCM"));
   }

   @Test
   void removeConcatSubtableDeletesConcatWithoutDependents() throws Exception {
      Worksheet ws = concatWorksheet(2);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(ws, agent);
      build(svc, agent, ws, 2, false);

      svc.apply("TOK", agent, ed -> ed.removeConcatSubtable("CC", "M2"));

      assertNull(ws.getAssembly("CC"));
   }

   @ParameterizedTest
   @CsvSource({
      "date,2024-01-15", "integer,1", "double,1.5", "float,1.5", "long,1", "short,1", "byte,1",
      "boolean,true", "string,a", "char,a", "time,10:30:00", "timeInstant,2024-01-15 10:30:00"})
   void mappingsOnlyEditKeepsTypeAttachedGroupDataType(String type, String value) throws Exception {
      Worksheet ws = new Worksheet();
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(ws, agent);
      List<WorksheetMutationSupport.GroupMapping> mappings =
         List.of(new WorksheetMutationSupport.GroupMapping("G1", List.of(value)));
      svc.apply("TOK", agent, ed -> ed.addNamedGroup("NG", null, null, type, mappings, true));

      svc.apply("TOK", agent, ed -> ed.editNamedGroup("NG", null, mappings, true));

      DefaultNamedGroupAssembly nga = (DefaultNamedGroupAssembly) ws.getAssembly("NG");
      ConditionList cl = nga.getNamedGroupInfo().getGroupCondition("G1");
      Condition c = (Condition) ((ConditionItem) cl.getItem(0)).getXCondition();
      assertEquals(type, c.getType());
      assertEquals(type, nga.getAttachedDataType());
   }

   @Test
   void mappingsOnlyEditOnColumnAttachedGroupStillUsesColumnType() throws Exception {
      Worksheet ws = new Worksheet();
      table(ws, "T");
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(ws, agent);
      List<WorksheetMutationSupport.GroupMapping> mappings =
         List.of(new WorksheetMutationSupport.GroupMapping("G1", List.of("1")));
      svc.apply("TOK", agent, ed -> ed.addNamedGroup("NG", "T", "n", null, mappings, true));

      svc.apply("TOK", agent, ed -> ed.editNamedGroup("NG", null, mappings, true));

      DefaultNamedGroupAssembly nga = (DefaultNamedGroupAssembly) ws.getAssembly("NG");
      Condition c = (Condition) ((ConditionItem) nga.getNamedGroupInfo()
         .getGroupCondition("G1").getItem(0)).getXCondition();
      assertEquals(XSchema.INTEGER, c.getType());
   }
}
