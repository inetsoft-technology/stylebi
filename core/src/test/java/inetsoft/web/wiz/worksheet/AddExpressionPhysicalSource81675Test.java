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

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #81675 round 2: a table bound to a physical database table has no source columns to
 * read through the model-only lookup, so the name check silently passed. A physical table whose
 * source columns could not be read is refused (fail closed); every other source keeps skipping
 * the check when there is nothing to check against.
 */
@Tag("core")
@WizAgentTestSupport
class AddExpressionPhysicalSource81675Test {
   private final Principal agent = TestPrincipals.user("alice", "host-org");

   private static ColumnSelection cols(String... names) {
      ColumnSelection cs = new ColumnSelection();

      for(String n : names) {
         cs.addAttribute(new ColumnRef(new AttributeRef(null, n)));
      }

      return cs;
   }

   private static BoundTableAssembly table(Worksheet ws, BoundTableAssembly t, int type) {
      t.setSourceInfo(new SourceInfo(type, "Examples/Orders", "SA.ORDERS"));
      t.setColumnSelection(cols("ORDER_ID"), false);
      ws.addAssembly(t);
      return t;
   }

   private WorksheetEditService.Editor editor(Worksheet ws, ColumnSelection source)
      throws Exception
   {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      return new WorksheetEditService.Editor(ws, agent, securityEngine,
         new InnerJoinService(null, null), null, () -> null, t -> source);
   }

   @Test
   void physicalTableWithUnreadableSourceIsRefusedAndNothingIsCreated() throws Exception {
      Worksheet ws = new Worksheet();
      table(ws, new PhysicalBoundTableAssembly(ws, "ORDERS"), SourceInfo.PHYSICAL_TABLE);
      WorksheetEditService.Editor ed = editor(ws, new ColumnSelection());

      PairingException ex = assertThrows(PairingException.class,
         () -> ed.addExpressionColumn("ORDERS", "ORDER_DATE", "1", "integer", false));

      assertTrue(ex.getMessage().contains("ORDER_DATE"), ex.getMessage());
      assertTrue(ex.getMessage().contains("ORDERS"), ex.getMessage());
      assertEquals(1, ((TableAssembly) ws.getAssembly("ORDERS"))
         .getColumnSelection(false).getAttributeCount());
   }

   @Test
   void physicalTableWithReadableSourceRefusesARemovedColumnName() throws Exception {
      Worksheet ws = new Worksheet();
      table(ws, new PhysicalBoundTableAssembly(ws, "ORDERS"), SourceInfo.PHYSICAL_TABLE);
      WorksheetEditService.Editor ed = editor(ws, cols("ORDER_ID", "ORDER_DATE"));

      PairingException ex = assertThrows(PairingException.class,
         () -> ed.addExpressionColumn("ORDERS", "ORDER_DATE", "1", "integer", false));

      assertTrue(ex.getMessage().contains("ORDER_DATE"), ex.getMessage());
      ed.addExpressionColumn("ORDERS", "Total2", "1", "integer", false);
   }

   @Test
   void physicalTableWithNothingToCheckAgainstIsNotBlocked() throws Exception {
      Worksheet ws = new Worksheet();
      table(ws, new PhysicalBoundTableAssembly(ws, "ORDERS"), SourceInfo.PHYSICAL_TABLE);

      editor(ws, null).addExpressionColumn("ORDERS", "ORDER_DATE", "1", "integer", false);

      assertEquals(2, ((TableAssembly) ws.getAssembly("ORDERS"))
         .getColumnSelection(false).getAttributeCount());
   }

   @Test
   void sqlBoundTableWithAnEmptySourceIsNotBlocked() throws Exception {
      Worksheet ws = new Worksheet();
      // add_sql_query binds a SQL table with a PHYSICAL_TABLE source info too
      table(ws, new SQLBoundTableAssembly(ws, "ORDERS"), SourceInfo.PHYSICAL_TABLE);

      editor(ws, new ColumnSelection())
         .addExpressionColumn("ORDERS", "ORDER_DATE", "1", "integer", false);

      assertEquals(2, ((TableAssembly) ws.getAssembly("ORDERS"))
         .getColumnSelection(false).getAttributeCount());
   }

   @Test
   void modelBoundTableWithAnEmptySourceIsNotBlocked() throws Exception {
      Worksheet ws = new Worksheet();
      table(ws, new BoundTableAssembly(ws, "ORDERS"), SourceInfo.MODEL);

      editor(ws, new ColumnSelection())
         .addExpressionColumn("ORDERS", "ORDER_DATE", "1", "integer", false);

      assertEquals(2, ((TableAssembly) ws.getAssembly("ORDERS"))
         .getColumnSelection(false).getAttributeCount());
   }
}
