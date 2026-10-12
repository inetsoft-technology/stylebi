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
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.IntegrationTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * Redmine #76480 / VBS-009 (P3 reproduction, rows v9s-O3/O4/O5/O6): {@code remove_column} on a
 * composed table. The account under test: the column IS resolved and removed from the private
 * selection, then the post-write {@code refreshColumnSelection} re-appends it LAST, because
 * {@code JoinQuery.getDefaultColumnSelection0} builds the default from the sub-tables without
 * filtering by the join's own selection. A non-composed (embedded) table and an expression column
 * stay removed.
 *
 * <p>Uses a real, active {@link AssetQuerySandbox} (the {@code JoinDuplicateColumnAliasMechanismTest}
 * pattern) and drives the real {@link WorksheetEditService.Editor#removeColumn} through
 * {@code apply}, with the mocked runtime worksheet handing back that sandbox so
 * {@code refreshAssemblies} runs the production refresh.</p>
 *
 * <p>Post-fix expectations (02-root-cause.md D2): a non-expression column of a composed table (join,
 * mirror) is REFUSED with a {@link PairingException} before anything mutates, and the selection is
 * unchanged in content AND order -- the re-append preserves the count, so only order exposes it.
 * The O3/O4 tests are therefore RED until the refusal lands. {@code ..._mechanism} documents the
 * engine behaviour the refusal exists to avoid (green before and after: it bypasses the Editor).
 * O5/O6 are controls: a non-composed table and an expression column stay removable.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ComposedTableRemoveColumnReappend76480Test {

   private static final String TOKEN = "TOK";
   private final Principal agent = TestPrincipals.user("alice", "host-org");

   @Test
   void v9sO3_joinSourceColumnRemoveIsRefusedAndTheSelectionIsUnchanged() throws Exception {
      Fixture f = joinFixture();
      List<String> before = names(f.join.getColumnSelection(false));
      System.out.println("[76480 O3] join before: " + before);
      assertTrue(before.contains("COMPANY_NAME"), before.toString());
      assertNotNull(WorksheetMutationSupport.resolveFieldOrNull(f.join, "COMPANY_NAME", false),
         "COMPANY_NAME must resolve, so a refusal is not a lookup miss");

      // Today: no exception; COMPANY_NAME is removed, then re-appended last by the refresh.
      PairingException ex = assertThrows(PairingException.class,
         () -> f.svc.apply(TOKEN, agent, ed -> ed.removeColumn("JOINED", "COMPANY_NAME")),
         () -> "join after: " + names(f.join.getColumnSelection(false)));
      System.out.println("[76480 O3] refusal: " + ex.getMessage());
      assertTrue(ex.getMessage().contains("COMPANY_NAME"), ex.getMessage());
      assertTrue(ex.getMessage().contains("set_column_visibility"), ex.getMessage());
      assertEquals(before, names(f.join.getColumnSelection(false)), "count AND order unchanged");
   }

   @Test
   void v9sO3_mechanism_bypassingTheEditorTheRefreshReappendsLast() throws Exception {
      Fixture f = joinFixture();
      ColumnSelection cs = f.join.getColumnSelection();
      DataRef resolved = WorksheetMutationSupport.resolveFieldOrNull(f.join, "COMPANY_NAME", false);
      assertNotNull(resolved);
      cs.removeAttribute(resolved);
      f.join.setColumnSelection(cs);
      List<String> removed = names(f.join.getColumnSelection(false));
      System.out.println("[76480 O3-manual] join after removeAttribute, before refresh: " + removed);
      assertFalse(removed.contains("COMPANY_NAME"), removed.toString());

      f.box.refreshColumnSelection("JOINED", true);
      List<String> refreshed = names(f.join.getColumnSelection(false));
      System.out.println("[76480 O3-manual] join after refreshColumnSelection: " + refreshed);
      assertEquals("COMPANY_NAME", refreshed.get(refreshed.size() - 1), refreshed.toString());
   }

   @Test
   void v9sO4_mirrorSourceColumnRemoveIsRefusedAndTheSelectionIsUnchanged() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly left = embedded(ws, "L",
         new String[]{ "ID", "NAME", "CITY" }, new Object[]{ "1", "a", "x" });
      ws.addAssembly(left);
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M", left);
      ws.addAssembly(mirror);
      AssetQuerySandbox box = activeBox(ws);
      box.refreshColumnSelection("M", true);
      WorksheetEditService svc = service(ws, box);

      List<String> before = names(mirror.getColumnSelection(false));
      System.out.println("[76480 O4] mirror before: " + before);
      // Today: no exception; NAME moves to the end ([ID, NAME, CITY] -> [ID, CITY, NAME]).
      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply(TOKEN, agent, ed -> ed.removeColumn("M", "NAME")),
         () -> "mirror after: " + names(mirror.getColumnSelection(false)));
      assertTrue(ex.getMessage().contains("set_column_visibility"), ex.getMessage());
      assertEquals(before, names(mirror.getColumnSelection(false)), "count AND order unchanged");
   }

   @Test
   void v9sO5_controlNonComposedEmbeddedTableStaysRemoved() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly left = embedded(ws, "L",
         new String[]{ "ID", "NAME", "CITY" }, new Object[]{ "1", "a", "x" });
      ws.addAssembly(left);
      AssetQuerySandbox box = activeBox(ws);
      box.refreshColumnSelection("L", true);
      WorksheetEditService svc = service(ws, box);

      svc.apply(TOKEN, agent, ed -> ed.removeColumn("L", "NAME"));
      List<String> after = names(left.getColumnSelection(false));
      System.out.println("[76480 O5] embedded L after: " + after);
      assertFalse(after.contains("NAME"), after.toString());
   }

   @Test
   void v9sO6_expressionColumnOnAJoinStaysRemoved() throws Exception {
      Fixture f = joinFixture();
      f.svc.apply(TOKEN, agent,
         ed -> ed.addExpressionColumn("JOINED", "E", "1", "integer", false));
      List<String> withE = names(f.join.getColumnSelection(false));
      System.out.println("[76480 O6] join with E: " + withE);
      assertTrue(withE.contains("E"), withE.toString());

      f.svc.apply(TOKEN, agent, ed -> ed.removeColumn("JOINED", "E"));
      List<String> after = names(f.join.getColumnSelection(false));
      System.out.println("[76480 O6] join after removing E: " + after);
      assertFalse(after.contains("E"), after.toString());
   }

   // ---------------------------------------------------------------------------------------

   private record Fixture(RelationalJoinTableAssembly join, AssetQuerySandbox box,
                          WorksheetEditService svc) {}

   /** CUSTOMERS(CUSTOMER_ID, COMPANY_NAME) FULL JOIN ORDERS(CUSTOMER_ID, ORDER_ID) on CUSTOMER_ID. */
   private Fixture joinFixture() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly customers = embedded(ws, "CUSTOMERS",
         new String[]{ "CUSTOMER_ID", "COMPANY_NAME" }, new Object[]{ "1", "Acme" });
      EmbeddedTableAssembly orders = embedded(ws, "ORDERS",
         new String[]{ "CUSTOMER_ID", "ORDER_ID" }, new Object[]{ "1", "100" });
      ws.addAssembly(customers);
      ws.addAssembly(orders);

      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setLeftTable("CUSTOMERS");
      op.setRightTable("ORDERS");
      op.setLeftAttribute(new AttributeRef(null, "CUSTOMER_ID"));
      op.setRightAttribute(new AttributeRef(null, "CUSTOMER_ID"));
      op.setOperation(TableAssemblyOperator.FULL_JOIN);
      TableAssemblyOperator top = new TableAssemblyOperator();
      top.addOperator(op);

      RelationalJoinTableAssembly joined = new RelationalJoinTableAssembly(
         ws, "JOINED", new TableAssembly[]{ customers, orders }, new TableAssemblyOperator[]{ top });
      ws.addAssembly(joined);

      AssetQuerySandbox box = activeBox(ws);
      box.refreshColumnSelection("JOINED", false);
      return new Fixture(joined, box, service(ws, box));
   }

   private static EmbeddedTableAssembly embedded(Worksheet ws, String name, String[] cols,
                                                 Object[] row)
   {
      EmbeddedTableAssembly t = new EmbeddedTableAssembly(ws, name);
      ColumnSelection cs = new ColumnSelection();
      String[] types = new String[cols.length];

      for(int i = 0; i < cols.length; i++) {
         cs.addAttribute(new ColumnRef(new AttributeRef(cols[i])));
         types[i] = "string";
      }

      t.setColumnSelection(cs, false);
      t.setEmbeddedData(new XEmbeddedTable(types, new Object[][]{ cols, row }));
      return t;
   }

   private static AssetQuerySandbox activeBox(Worksheet ws) {
      AssetQuerySandbox box = new AssetQuerySandbox(ws, null, new VariableTable());
      box.setActive(true);
      return box;
   }

   private WorksheetEditService service(Worksheet ws, AssetQuerySandbox box) throws Exception {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(box);

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      JoinSession s = new JoinSession(TOKEN, "Worksheet/ws1", "alice~;~host-org",
                                      SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq(TOKEN), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.WORKSHEET), eq("Worksheet/ws1"), eq(agent)))
         .thenReturn(rws);

      return new WorksheetEditService(sessions, runtimeAccess, mock(SheetAgentBroadcastService.class),
                                      securityEngine, mock(InnerJoinService.class));
   }

   /** Display identity per column, in selection order. */
   private static List<String> names(ColumnSelection cs) {
      List<String> out = new ArrayList<>();

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         DataRef ref = cs.getAttribute(i);
         out.add(ref instanceof ColumnRef cr && cr.getAlias() != null ? cr.getAlias() : ref.getAttribute());
      }

      return out;
   }
}
