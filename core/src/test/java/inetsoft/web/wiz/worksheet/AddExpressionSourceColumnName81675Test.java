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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #81675: add_expression_column refuses a name held by a column of the bound source even
 * when that column was removed from the selection (as the native Create Expression dialog does),
 * and add_column names the real colliding column kind in its refusal.
 */
@Tag("core")
@WizAgentTestSupport
class AddExpressionSourceColumnName81675Test {
   private final Principal agent = TestPrincipals.user("alice", "host-org");

   private static ColumnSelection cols(String... names) {
      ColumnSelection cs = new ColumnSelection();

      for(String n : names) {
         cs.addAttribute(new ColumnRef(new AttributeRef(null, n)));
      }

      return cs;
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

   /** ORDERS selects ORDER_ID only; ORDER_DATE is a source column that was removed. */
   private Worksheet fixture(Worksheet ws, String... selected) {
      ws.addAssembly(TestWorksheets.nonEmbeddedTableWithColumns(ws, "ORDERS", selected));
      return ws;
   }

   private static List<String> names(TableAssembly t) {
      List<String> out = new ArrayList<>();
      ColumnSelection cs = t.getColumnSelection(false);

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         out.add(((ColumnRef) cs.getAttribute(i)).getAttribute());
      }

      return out;
   }

   @Test
   void removedSourceColumnNameIsRefusedAndNothingIsCreated() throws Exception {
      Worksheet ws = fixture(new Worksheet(), "ORDER_ID");
      WorksheetEditService.Editor ed = editor(ws, cols("ORDER_ID", "ORDER_DATE"));

      PairingException ex = assertThrows(PairingException.class,
         () -> ed.addExpressionColumn("ORDERS", "ORDER_DATE", "1", "integer", false));

      assertTrue(ex.getMessage().contains("ORDER_DATE"), ex.getMessage());
      assertEquals(List.of("ORDER_ID"), names((TableAssembly) ws.getAssembly("ORDERS")));
   }

   @Test
   void caseVariantOfARemovedSourceColumnIsRefused() throws Exception {
      Worksheet ws = fixture(new Worksheet(), "ORDER_ID");
      WorksheetEditService.Editor ed = editor(ws, cols("ORDER_ID", "ORDER_DATE"));

      assertThrows(PairingException.class,
         () -> ed.addExpressionColumn("ORDERS", "order_date", "1", "integer", false));
      assertEquals(List.of("ORDER_ID"), names((TableAssembly) ws.getAssembly("ORDERS")));
   }

   @Test
   void sourceColumnRemovedThenRefusedCanBeRestoredByAddColumn() throws Exception {
      Worksheet ws = fixture(new Worksheet(), "ORDER_ID");
      WorksheetEditService.Editor ed = editor(ws, cols("ORDER_ID", "ORDER_DATE"));

      assertThrows(PairingException.class,
         () -> ed.addExpressionColumn("ORDERS", "ORDER_DATE", "1", "integer", false));
      ed.addColumn("ORDERS", "ORDER_DATE", null);

      assertEquals(List.of("ORDER_ID", "ORDER_DATE"),
                   names((TableAssembly) ws.getAssembly("ORDERS")));
   }

   @Test
   void nameTakenByASelectedColumnIsStillRefused() throws Exception {
      Worksheet ws = fixture(new Worksheet(), "ORDER_ID");
      WorksheetEditService.Editor ed = editor(ws, cols("ORDER_ID", "ORDER_DATE"));

      assertThrows(PairingException.class,
         () -> ed.addExpressionColumn("ORDERS", "ORDER_ID", "1", "integer", false));
   }

   @Test
   void freshNameIsAccepted() throws Exception {
      Worksheet ws = fixture(new Worksheet(), "ORDER_ID");
      WorksheetEditService.Editor ed = editor(ws, cols("ORDER_ID", "ORDER_DATE"));

      ed.addExpressionColumn("ORDERS", "Total2", "1", "integer", false);

      assertEquals(List.of("ORDER_ID", "Total2"), names((TableAssembly) ws.getAssembly("ORDERS")));
   }

   @Test
   void unreadableSourceDoesNotBlockAnExpression() throws Exception {
      Worksheet ws = fixture(new Worksheet(), "ORDER_ID");
      WorksheetEditService.Editor ed = editor(ws, null);

      ed.addExpressionColumn("ORDERS", "ORDER_DATE", "1", "integer", false);

      assertTrue(names((TableAssembly) ws.getAssembly("ORDERS")).contains("ORDER_DATE"));
   }

   @Test
   void addColumnOverAnExpressionColumnDoesNotSuggestSetColumnVisibility() throws Exception {
      Worksheet ws = fixture(new Worksheet(), "ORDER_ID");
      WorksheetEditService.Editor ed = editor(ws, null);
      ed.addExpressionColumn("ORDERS", "Calc", "1", "integer", false);

      PairingException ex = assertThrows(PairingException.class,
         () -> ed.addColumn("ORDERS", "Calc", null));

      assertTrue(ex.getMessage().contains("expression column"), ex.getMessage());
      assertFalse(ex.getMessage().contains("set_column_visibility"), ex.getMessage());
   }

   @Test
   void addColumnOverAHiddenColumnKeepsTheVisibilityHint() throws Exception {
      Worksheet ws = fixture(new Worksheet(), "ORDER_ID", "H");
      TableAssembly t = (TableAssembly) ws.getAssembly("ORDERS");
      ((ColumnRef) t.getColumnSelection(false).getAttribute(1)).setVisible(false);
      WorksheetEditService.Editor ed = editor(ws, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ed.addColumn("ORDERS", "H", null));

      assertTrue(ex.getMessage().contains("set_column_visibility"), ex.getMessage());
   }
}
