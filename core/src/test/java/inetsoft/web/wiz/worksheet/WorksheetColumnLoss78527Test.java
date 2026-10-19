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
import inetsoft.uql.asset.internal.CompositeTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Catalog;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78527: edit_sql_query / edit_table refuse dropping a column a downstream table still
 * reads (WSQ-012), and edit_date_range_column refuses re-levelling a join key (WBS-121). The
 * replace-import half is in {@link WorksheetAgentControllerTest}.
 */
@Tag("core")
@WizAgentTestSupport
class WorksheetColumnLoss78527Test {
   private final Principal agent = TestPrincipals.user("alice", "host-org");

   private WorksheetEditService service(Worksheet ws) throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
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

   // ------------------------------------------------------------------- edit_sql_query / edit_table

   /** Q(ORDER_ID, CUSTOMER_ID, ORDER_TOTAL), a SQL-bound shape, with a mirror M of it. */
   private TableAssembly queryWithMirror(Worksheet ws, WorksheetEditService svc) throws Exception {
      TableAssembly q = TestWorksheets.nonEmbeddedTableWithColumns(
         ws, "Q", "ORDER_ID", "CUSTOMER_ID", "ORDER_TOTAL");
      setType(q, "ORDER_ID", XSchema.INTEGER);
      setType(q, "CUSTOMER_ID", XSchema.INTEGER);
      setType(q, "ORDER_TOTAL", XSchema.DOUBLE);
      ws.addAssembly(q);
      svc.apply("TOK", agent, ed -> ed.addMirror("M", "Q"));
      return q;
   }

   /** The selection the new SQL would produce: one entity-less column per output name. */
   private static ColumnSelection newColumns(String... names) {
      ColumnSelection cols = new ColumnSelection();

      for(String name : names) {
         ColumnRef col = new ColumnRef(new AttributeRef(null, name));
         col.setDataType(XSchema.INTEGER);
         cols.addAttribute(col);
      }

      return cols;
   }

   private static void assertSqlEdit(Worksheet ws, TableAssembly q, ColumnSelection next)
      throws PairingException
   {
      WorksheetAgentController.assertSqlEditAllowsColumnRemoval(
         ws, q, q.getColumnSelection(false), next, "This query would change the type of",
         JoinKeyTypeGuard::sqlRemedy, "The new query of 'Q'", "Keep them. Nothing was changed.");
   }

   @Test
   void sqlEditDroppingAColumnAMirrorExpressionReadsIsRefusedNamingIt() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly q = queryWithMirror(ws, svc);
      svc.apply("TOK", agent, ed -> ed.addExpressionColumn(
         "M", "c2", "field['CUSTOMER_ID'] * 2", "integer", false));

      PairingException ex = assertThrows(PairingException.class,
         () -> assertSqlEdit(ws, q, newColumns("ORDER_ID", "ORDER_TOTAL")));

      assertEquals("The new query of 'Q' would drop column(s) \"CUSTOMER_ID\" that other " +
                   "tables still use: 'M' (expression column 'c2'). Keep them. Nothing was " +
                   "changed.", ex.getMessage());
   }

   @Test
   void sqlEditDroppingAColumnAMirrorConditionOrRangeColumnReadsIsRefused() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly q = queryWithMirror(ws, svc);
      svc.apply("TOK", agent, ed -> {
         ed.addMirror("M2", "Q");
         ed.addFilter("M", "CUSTOMER_ID", "=", "1");
         ed.addNumericRangeColumn("M2", "ORDER_TOTAL", new double[] { 10, 100 }, null);
      });

      PairingException ex = assertThrows(PairingException.class,
         () -> assertSqlEdit(ws, q, newColumns("ORDER_ID")));

      assertTrue(ex.getMessage().contains("'M' (pre-condition)"), ex.getMessage());
      assertTrue(ex.getMessage().contains("'M2' (range column"), ex.getMessage());
      assertTrue(ex.getMessage().contains("\"CUSTOMER_ID\", \"ORDER_TOTAL\""), ex.getMessage());
   }

   /** WSQ-012's control: `o.CUSTOMER_ID AS CUSTOMER_ID` keeps the output name. */
   @Test
   void sqlEditKeepingTheOutputNameUnderAnAliasIsAccepted() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly q = queryWithMirror(ws, svc);
      svc.apply("TOK", agent, ed -> ed.addExpressionColumn(
         "M", "c2", "field['CUSTOMER_ID'] * 2", "integer", false));
      ColumnSelection next = newColumns("ORDER_ID", "ORDER_TOTAL");
      ColumnRef aliased = new ColumnRef(new AttributeRef(null, "CUST"));
      aliased.setAlias("CUSTOMER_ID");
      aliased.setDataType(XSchema.INTEGER);
      next.addAttribute(aliased);

      assertSqlEdit(ws, q, next);
      assertSqlEdit(ws, q, newColumns("ORDER_ID", "CUSTOMER_ID", "ORDER_TOTAL"));
   }

   /**
    * WSQ-009 is pinned: an aggregate on a dropped column is accepted here, and the plugin's
    * snapshot diff reports it as a warning.
    */
   @Test
   void sqlEditDroppingAColumnOnlyAnAggregateReadsIsStillAccepted() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly q = queryWithMirror(ws, svc);
      svc.apply("TOK", agent, ed -> ed.setGroupAggregate(
         "M", List.of(new WorksheetMutationSupport.GroupSpec("CUSTOMER_ID")),
         List.of(new WorksheetMutationSupport.AggregateSpec("ORDER_TOTAL", "Sum", null)),
         false, false));

      assertSqlEdit(ws, q, newColumns("ORDER_ID", "CUSTOMER_ID", "ORDER_TOTAL_V2"));
   }

   /**
    * The table's own condition and sort are not part of this check (Known limits); its own
    * expression and range columns are (Bug #78681, see below).
    */
   @Test
   void sqlEditDroppingAColumnOnlyTheTableItselfReadsIsNotRefusedByTheNewCheck()
      throws Exception
   {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly q = queryWithMirror(ws, svc);
      svc.apply("TOK", agent, ed -> ed.addFilter("Q", "CUSTOMER_ID", "=", "1"));

      assertSqlEdit(ws, q, newColumns("ORDER_ID", "ORDER_TOTAL"));
   }

   private TableAssembly queryWithJoin(Worksheet ws, WorksheetEditService svc) throws Exception {
      TableAssembly q = queryWithMirror(ws, svc);
      EmbeddedTableAssembly r = TestWorksheets.tableWithColumns(ws, "R", "k");
      setType(r, "k", XSchema.INTEGER);
      ws.addAssembly(r);
      svc.apply("TOK", agent, ed -> ed.addJoin(
         "J", "Q", "CUSTOMER_ID", "R", "k", "INNER", null, null));
      return q;
   }

   /** Bug #78811: the join key refusal names the join, not just the column. */
   @Test
   void sqlEditDroppingAJoinKeyIsRefusedNamingTheJoin() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly q = queryWithJoin(ws, svc);

      PairingException ex = assertThrows(PairingException.class,
         () -> assertSqlEdit(ws, q, newColumns("ORDER_ID", "ORDER_TOTAL")));

      assertTrue(ex.getMessage().contains("'J'"), ex.getMessage());
      assertTrue(ex.getMessage().contains("join key on \"CUSTOMER_ID\""), ex.getMessage());
      assertFalse(ex.getMessage().contains(
         Catalog.getCatalog().getString("common.columnDependency", "CUSTOMER_ID")),
                  ex.getMessage());
   }

   @Test
   void sqlEditRenamingAJoinKeyIsRefusedNamingTheJoin() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly q = queryWithJoin(ws, svc);

      PairingException ex = assertThrows(PairingException.class,
         () -> assertSqlEdit(ws, q, newColumns("ORDER_ID", "CUST_ID", "ORDER_TOTAL")));

      assertTrue(ex.getMessage().contains("'J'"), ex.getMessage());
      assertTrue(ex.getMessage().contains("join key on \"CUSTOMER_ID\""), ex.getMessage());
   }

   @Test
   void sqlEditDroppingAKeyAlsoReadByAConditionListsBothDependents() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly q = queryWithJoin(ws, svc);
      svc.apply("TOK", agent, ed -> ed.addFilter("M", "CUSTOMER_ID", "=", "1"));

      PairingException ex = assertThrows(PairingException.class,
         () -> assertSqlEdit(ws, q, newColumns("ORDER_ID", "ORDER_TOTAL")));

      assertTrue(ex.getMessage().contains("'J' (join key"), ex.getMessage());
      assertTrue(ex.getMessage().contains("'M' (pre-condition)"), ex.getMessage());
   }

   @Test
   void sqlEditDroppingAKeyOfAJoinOverACrossJoinNamesTheJoin() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly q = queryWithMirror(ws, svc);
      EmbeddedTableAssembly s = TestWorksheets.tableWithColumns(ws, "S", "k");
      setType(s, "k", XSchema.INTEGER);
      ws.addAssembly(s);
      svc.apply("TOK", agent, ed -> ed.addCrossJoin("X", "Q", "S"));
      EmbeddedTableAssembly r = TestWorksheets.tableWithColumns(ws, "R", "k");
      setType(r, "k", XSchema.INTEGER);
      ws.addAssembly(r);
      svc.apply("TOK", agent, ed -> ed.addJoin(
         "JX", "X", "CUSTOMER_ID", "R", "k", "INNER", null, null));

      PairingException ex = assertThrows(PairingException.class,
         () -> assertSqlEdit(ws, q, newColumns("ORDER_ID", "ORDER_TOTAL")));

      assertTrue(ex.getMessage().contains("'JX'"), ex.getMessage());
   }

   // ------------------------------------------------------------------- Bug #78681: own derived columns

   /** Q(ORDER_ID, CUSTOMER_ID, ORDER_DATE) with own bracket, dot and Year(ORDER_DATE) columns. */
   private TableAssembly queryWithOwnDerived(Worksheet ws, WorksheetEditService svc)
      throws Exception
   {
      TableAssembly q = TestWorksheets.nonEmbeddedTableWithColumns(
         ws, "Q", "ORDER_ID", "CUSTOMER_ID", "ORDER_DATE");
      setType(q, "ORDER_ID", XSchema.INTEGER);
      setType(q, "CUSTOMER_ID", XSchema.INTEGER);
      setType(q, "ORDER_DATE", XSchema.DATE);
      ws.addAssembly(q);
      svc.apply("TOK", agent, ed -> {
         ed.addExpressionColumn("Q", "Dbl", "field['CUSTOMER_ID'] * 2", "integer", false);
         ed.addExpressionColumn("Q", "Dbl2", "field.CUSTOMER_ID * 2", "integer", false);
         ed.addDateRangeColumn("Q", "ORDER_DATE", "YEAR");
      });
      return q;
   }

   /** What editSqlQuery does with the fresh projection before it commits it. */
   private static List<String> editQuery(Worksheet ws, TableAssembly q, String... projected)
      throws PairingException
   {
      ColumnSelection next = newColumns(projected);
      WorksheetAgentController.carryOwnDerivedColumns("Q", q.getColumnSelection(false), next);
      assertSqlEdit(ws, q, next);
      List<String> names = new ArrayList<>();

      for(int i = 0; i < next.getAttributeCount(); i++) {
         names.add(next.getAttribute(i).getName());
      }

      return names;
   }

   @Test
   void sqlEditWithTheSameOrAKeepInputQueryKeepsTheOwnExpressionAndRangeColumns()
      throws Exception
   {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly q = queryWithOwnDerived(ws, svc);
      String year = DateRangeRef.getName("ORDER_DATE", DateRangeRef.YEAR_INTERVAL);

      assertEquals(List.of("ORDER_ID", "CUSTOMER_ID", "ORDER_DATE", "Dbl", "Dbl2", year),
                   editQuery(ws, q, "ORDER_ID", "CUSTOMER_ID", "ORDER_DATE"));
      assertEquals(List.of("CUSTOMER_ID", "ORDER_DATE", "Dbl", "Dbl2", year),
                   editQuery(ws, q, "CUSTOMER_ID", "ORDER_DATE"));
   }

   @Test
   void sqlEditDroppingAnInputAnOwnExpressionReadsIsRefusedNamingTheColumn() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly q = queryWithOwnDerived(ws, svc);

      PairingException ex = assertThrows(PairingException.class,
         () -> editQuery(ws, q, "ORDER_ID", "ORDER_DATE"));

      assertTrue(ex.getMessage().contains("\"CUSTOMER_ID\""), ex.getMessage());
      assertTrue(ex.getMessage().contains("table 'Q' itself"), ex.getMessage());
      assertTrue(ex.getMessage().contains("Dbl"), ex.getMessage());
   }

   @Test
   void sqlEditDroppingTheDateAnOwnRangeColumnReadsIsRefused() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly q = queryWithOwnDerived(ws, svc);

      PairingException ex = assertThrows(PairingException.class,
         () -> editQuery(ws, q, "ORDER_ID", "CUSTOMER_ID"));

      assertTrue(ex.getMessage().contains("\"ORDER_DATE\""), ex.getMessage());
      assertTrue(ex.getMessage().contains("table 'Q' itself"), ex.getMessage());
   }

   @Test
   void sqlEditProjectingAColumnNamedLikeAnOwnDerivedColumnIsRefused() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly q = queryWithOwnDerived(ws, svc);

      PairingException ex = assertThrows(PairingException.class,
         () -> editQuery(ws, q, "ORDER_ID", "CUSTOMER_ID", "ORDER_DATE", "Dbl"));

      assertTrue(ex.getMessage().contains("\"Dbl\""), ex.getMessage());
      assertTrue(ex.getMessage().contains("Nothing was changed"), ex.getMessage());
   }

   /** edit_table (tabular) already keeps derived columns in the rebuilt selection. */
   @Test
   void tabularEditKeepingDerivedColumnsButDroppingTheirInputIsRefused() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly q = queryWithOwnDerived(ws, svc);
      ColumnSelection kept = newColumns("ORDER_ID", "ORDER_DATE");

      for(ColumnRef column : new ColumnRef[] {
         (ColumnRef) q.getColumnSelection(false).getAttribute("Dbl"),
         (ColumnRef) q.getColumnSelection(false).getAttribute("Dbl2") })
      {
         kept.addAttribute(column);
      }

      PairingException ex = assertThrows(PairingException.class, () -> assertSqlEdit(ws, q, kept));

      assertTrue(ex.getMessage().contains("\"CUSTOMER_ID\""), ex.getMessage());
      assertTrue(ex.getMessage().contains("table 'Q' itself"), ex.getMessage());
   }

   @Test
   void tabularEditKeepingTheInputAndDerivedColumnsIsAccepted() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly q = queryWithOwnDerived(ws, svc);
      ColumnSelection kept = q.getColumnSelection(false).clone();

      assertSqlEdit(ws, q, kept);
   }

   // ------------------------------------------------------------------- edit_date_range_column

   private static final String YEAR = DateRangeRef.getName("d", DateRangeRef.YEAR_INTERVAL);
   private static final String QUARTER =
      DateRangeRef.getName("d", DateRangeRef.QUARTER_INTERVAL);

   /** An embedded table (d date, v) with a Year(d) range column. */
   private static void dated(Worksheet ws, String name) {
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, name, "d", "v");
      setType(t, "d", XSchema.DATE);
      ws.addAssembly(t);
   }

   private void datedWithYear(Worksheet ws, WorksheetEditService svc, String... names)
      throws Exception
   {
      for(String name : names) {
         dated(ws, name);
         svc.apply("TOK", agent, ed -> ed.addDateRangeColumn(name, "d", "YEAR"));
      }
   }

   private static List<String> operatorNames(Worksheet ws, String join) {
      CompositeTableAssemblyInfo info = (CompositeTableAssemblyInfo)
         ((CompositeTableAssembly) ws.getAssembly(join)).getTableInfo();
      List<String> names = new ArrayList<>();
      Enumeration<?> iter = info.getOperatorTables();

      while(iter.hasMoreElements()) {
         String[] pair = (String[]) iter.nextElement();

         for(TableAssemblyOperator.Operator op : info.getOperator(pair[0], pair[1]).getOperators()) {
            names.add(op.getLeftTable() + "." + op.getLeftAttribute().getName() + "=" +
                      op.getRightTable() + "." + op.getRightAttribute().getName());
         }
      }

      return names;
   }

   private static List<String> names(Worksheet ws, String table) {
      ColumnSelection cs = ((TableAssembly) ws.getAssembly(table)).getColumnSelection(false);
      List<String> names = new ArrayList<>();

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         names.add(cs.getAttribute(i).getName());
      }

      return names;
   }

   /** WBS-121: re-levelling A's Year(d) under J = A join B on Year(d). */
   @Test
   void reLevellingAJoinKeyRangeColumnIsRefusedNamingTheJoinAndNothingChanges() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      datedWithYear(ws, svc, "A", "B");
      svc.apply("TOK", agent, ed -> ed.addJoin("J", "A", YEAR, "B", YEAR, "INNER", null, null));
      List<String> before = operatorNames(ws, "J");

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.editDateRangeColumn("A", YEAR, "QUARTER")));

      assertEquals("Cannot change \"" + YEAR + "\" to QUARTER on table \"A\": it is a join key " +
                   "of 'J' (join key on \"" + YEAR + "\"). Re-levelling renames it to \"" +
                   QUARTER + "\", which changes what that join matches on. Edit the join's keys " +
                   "first, or add a second range column at the new level with " +
                   "add_date_range_column. Nothing was changed.", ex.getMessage());
      assertEquals(List.of("d", "v", YEAR), names(ws, "A"));
      assertEquals(before, operatorNames(ws, "J"));
   }

   /** The guard follows the key through a mirror, as allowsDeletion does. */
   @Test
   void reLevellingAKeyAJoinUsesThroughAMirrorIsRefused() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      datedWithYear(ws, svc, "A", "B");
      svc.apply("TOK", agent, ed -> ed.addMirror("MA", "A"));
      svc.apply("TOK", agent, ed -> ed.addJoin("J", "MA", YEAR, "B", YEAR, "INNER", null, null));

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.editDateRangeColumn("A", YEAR, "QUARTER")));

      assertTrue(ex.getMessage().contains("'J'"), ex.getMessage());
      assertEquals(List.of("d", "v", YEAR), names(ws, "A"));
   }

   @Test
   void reLevellingANonKeyRangeColumnIsAccepted() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      datedWithYear(ws, svc, "A");

      svc.apply("TOK", agent, ed -> ed.editDateRangeColumn("A", YEAR, "QUARTER"));

      assertEquals(List.of("d", "v", QUARTER), names(ws, "A"));
   }

   /**
    * Refuter's K: A is in no join, so the guard lets the re-level through, and K = B join C on a
    * same-named Year(d) key must keep both of its sides.
    */
   @Test
   void reLevellingAnUnjoinedRangeColumnLeavesAnUnrelatedJoinOnASameNamedKeyIntact()
      throws Exception
   {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      datedWithYear(ws, svc, "A", "B", "C");
      svc.apply("TOK", agent, ed -> ed.addJoin("K", "B", YEAR, "C", YEAR, "INNER", null, null));

      svc.apply("TOK", agent, ed -> ed.editDateRangeColumn("A", YEAR, "QUARTER"));

      assertEquals(List.of("d", "v", QUARTER), names(ws, "A"));
      assertEquals(List.of("B." + YEAR + "=C." + YEAR), operatorNames(ws, "K"));
   }

   private static void setType(TableAssembly t, String col, String type) {
      ((ColumnRef) t.getColumnSelection(false).getAttribute(col)).setDataType(type);
   }
}
