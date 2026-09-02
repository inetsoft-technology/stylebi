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
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionListWrapper;
import inetsoft.uql.XCondition;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@WizAgentTestSupport
class WorksheetEditServiceMutatorsTest {

   // =========================================================================
   // Helper — builds a WorksheetEditService with mocked deps for a given rws
   // =========================================================================

   private WorksheetEditService service(RuntimeWorksheet rws, String runtimeId,
                                        Principal agent, String token)
      throws PairingException, inetsoft.sree.security.SecurityException
   {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);

      return service(rws, runtimeId, agent, token, securityEngine);
   }

   /**
    * Same as {@link #service(RuntimeWorksheet, String, Principal, String)} but lets the caller
    * supply an already-stubbed {@link SecurityEngine} mock, so permission checks can be denied
    * (or captured) for specific {@link ResourceType}s.
    */
   private WorksheetEditService service(RuntimeWorksheet rws, String runtimeId,
                                        Principal agent, String token,
                                        SecurityEngine securityEngine)
      throws PairingException, inetsoft.sree.security.SecurityException
   {
      SheetSessionService sessions       = mock(SheetSessionService.class);
      SheetRuntimeAccess  runtimeAccess  = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);

      JoinSession s = new JoinSession(token, runtimeId, "alice~;~host-org",
                                      SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq(token), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.WORKSHEET), eq(runtimeId), eq(agent)))
         .thenReturn(rws);

      return new WorksheetEditService(sessions, runtimeAccess, broadcast, securityEngine, mock(InnerJoinService.class));
   }

   private RuntimeWorksheet rws(Worksheet ws) {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      return rws;
   }

   /** Plain (non-date-bucketed) group specs for the given column names. */
   private static List<WorksheetMutationSupport.GroupSpec> groups(String... fields) {
      return java.util.Arrays.stream(fields)
         .map(WorksheetMutationSupport.GroupSpec::new)
         .toList();
   }

   // =========================================================================
   // Filter tests
   // =========================================================================

   @Test
   void addFilterAddsCondition() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addFilter("T", "a", "=", "hello"));

      assertNotNull(t.getPreConditionList());
      assertFalse(t.getPreConditionList().isEmpty());
   }

   @Test
   void addFilterAppendsSecondConditionWithAnd() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> {
         ed.addFilter("T", "a", "=", "hello");
         ed.addFilter("T", "b", "=", "world");
      });

      // Two conditions + one AND junction = size 3
      assertEquals(3, t.getPreConditionList().getConditionList().getSize());
   }

   @Test
   void removeFilterRemovesCondition() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> {
         ed.addFilter("T", "a", "=", "hello");
         ed.removeFilter("T", "a");
      });

      assertTrue(t.getPreConditionList() == null || t.getPreConditionList().isEmpty());
   }

   @Test
   void removeFilterLeavesOtherConditions() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> {
         ed.addFilter("T", "a", "=", "hello");
         ed.addFilter("T", "b", "=", "world");
         ed.removeFilter("T", "a");
      });

      // Only the "b" condition should remain — size 1 (just the ConditionItem)
      assertNotNull(t.getPreConditionList());
      assertFalse(t.getPreConditionList().isEmpty());
      assertEquals(1, t.getPreConditionList().getConditionList().getSize());
   }

   /**
    * L2 repair-review Finding D: {@code requireColumn(TableAssembly, String)} used to check
    * only {@code cs.getAttribute(column) == null}, which does not see a column matched purely
    * via {@link ColumnRef#getAlias()} when the alias is not "applied"
    * ({@code aalias == false}, per {@link ColumnRef#getName()}). {@link WorksheetMutationSupport
    * #resolveField} (used by the actual filter-creation call one line below) already resolves
    * such a column via its own alias-fallback loop, so before the fix this column was
    * resolvable by {@code addFilter} but rejected by its own guard before ever reaching that
    * resolution -- a false-negative "Column not found" for a column that plainly exists.
    */
   @Test
   void addFilterAcceptsAColumnMatchedOnlyByItsUnappliedAlias() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a", "b");
      ColumnRef aliased = (ColumnRef) t.getColumnSelection(false).getAttribute("a");
      aliased.setAlias("aliasA");
      aliased.setApplyingAlias(false);
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addFilter("T", "aliasA", "=", "hello"));

      assertNotNull(t.getPreConditionList());
      assertFalse(t.getPreConditionList().isEmpty());
   }

   @Test
   void addFilterRejectsEmbeddedTable() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.addFilter("T", "a", "=", "hello")));

      assertTrue(ex.getMessage().toLowerCase().contains("snapshot"));
      assertTrue(t.getPreConditionList() == null || t.getPreConditionList().isEmpty());
   }

   @Test
   void addFilterRejectsSnapshotEmbeddedTable() throws Exception {
      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly t = TestWorksheets.snapshotTableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.addFilter("T", "a", "=", "hello")));

      assertTrue(t.getPreConditionList() == null || t.getPreConditionList().isEmpty());
   }

   // =========================================================================
   // Aggregate tests
   // =========================================================================

   @Test
   void setGroupAggregateAppliesInfo() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "cat", "val");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T",
                              groups("cat"),
                              List.of(new WorksheetMutationSupport.AggregateSpec("val", "SUM", null)))
      );

      AggregateInfo ai = t.getAggregateInfo();
      assertNotNull(ai);
      assertFalse(ai.isEmpty());
      assertEquals(1, ai.getGroupCount());
      assertEquals(1, ai.getAggregateCount());
   }

   // Bug #75954: NthLargest/NthSmallest/etc. silently dropped their N operand and the
   // aggregate quietly computed as N=1 (Max/Min) instead — no error anywhere.
   @Test
   void setGroupAggregateAppliesNForParametrizedFormula() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "cat", "val");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T",
            groups("cat"),
            List.of(new WorksheetMutationSupport.AggregateSpec(
               "val", "NthLargest", "fifth_largest", 5)))
      );

      AggregateInfo ai = t.getAggregateInfo();
      assertEquals(1, ai.getAggregateCount());
      assertEquals(5, ai.getAggregate(0).getN());
   }

   /**
    * L2 Group 2 findings 13/14 root-cause narrowing: live testing showed {@code secondaryColumn}/
    * {@code percentageOf} have no effect on {@code preview_worksheet_data}'s actual output despite
    * looking structurally correct in {@link WorksheetMutationSupport#applyAggregateInfo}. This test
    * rules out the wiz-agent mutation path itself (and, transitively, Jackson record
    * deserialization of {@link WorksheetMutationSupport.AggregateSpec}, which a standalone
    * {@code ObjectMapper} repro also confirmed picks the canonical 6-arg constructor correctly):
    * the {@link AggregateRef} added to the {@link TableAssembly}'s {@link AggregateInfo} carries
    * both fields correctly immediately after {@code setGroupAggregate} returns, through the exact
    * code path the live MCP tool call uses. The still-undiagnosed part of the defect is therefore
    * downstream, in the query-execution layer ({@code AssetQuery}'s summary-building loops, e.g.
    * {@code AssetQuery.java:1994-2104} and {@code :2717-2790}, both of which iterate an
    * {@code AggregateRef[] aggregates} array whose ultimate source was not traced this pass) — not
    * in any code this lane's audit or fixes touch.
    */
   @Test
   void setGroupAggregateAppliesSecondaryColumnAndPercentageToTheAggregateInfo() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "cat", "val", "val2");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T",
            groups("cat"),
            List.of(new WorksheetMutationSupport.AggregateSpec(
               "val", "First", "first_val", null, "val2", "grand_total")))
      );

      AggregateInfo ai = t.getAggregateInfo();
      inetsoft.uql.asset.AggregateRef ar = ai.getAggregate(0);
      assertNotNull(ar.getSecondaryColumn(), "secondaryColumn should survive setGroupAggregate");
      assertEquals("val2", ar.getSecondaryColumn().getName());
      assertTrue(ar.isPercentage());
      assertEquals(inetsoft.uql.XConstants.PERCENTAGE_OF_GRANDTOTAL, ar.getPercentageOption());
   }

   // N must be ignored (not throw, not corrupt the ref) for a formula that doesn't use it.
   @Test
   void setGroupAggregateIgnoresNForNonParametrizedFormula() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "cat", "val");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T",
            groups("cat"),
            List.of(new WorksheetMutationSupport.AggregateSpec("val", "SUM", null, 5)))
      );

      AggregateInfo ai = t.getAggregateInfo();
      assertEquals(1, ai.getAggregateCount());
      assertEquals(0, ai.getAggregate(0).getN());
   }

   // Bug #75954 follow-up: a second (or later) aggregate on the same column goes through
   // the secondary-aggregate -> new-expression-column -> new-primary-aggregate conversion
   // (see the "Convert secondary aggregates" block below) rather than the plain path
   // covered by setGroupAggregateAppliesNForParametrizedFormula above — N must survive
   // that conversion too.
   @Test
   void setGroupAggregateAppliesNThroughSecondaryConversion() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "cat", "price");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T",
            groups("cat"),
            List.of(new WorksheetMutationSupport.AggregateSpec("price", "SUM", "total_price"),
                    new WorksheetMutationSupport.AggregateSpec(
                       "price", "NthLargest", "third_largest_price", 3))));

      AggregateInfo ai = t.getAggregateInfo();
      assertEquals(2, ai.getAggregateCount());
      assertEquals(0, ai.getAggregate(0).getN());
      assertEquals(3, ai.getAggregate(1).getN());
   }

   @Test
   void sameColumnAggregatesKeepDistinctAliases() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "cat", "price");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T",
            groups("cat"),
            List.of(new WorksheetMutationSupport.AggregateSpec("price", "MIN", "min_price"),
                    new WorksheetMutationSupport.AggregateSpec("price", "MAX", "max_price"),
                    new WorksheetMutationSupport.AggregateSpec("price", "COUNT", "n"))));

      AggregateInfo ai = t.getAggregateInfo();
      assertEquals(3, ai.getAggregateCount());

      // Each aggregate must carry its own alias — with a shared ColumnRef the second
      // alias silently overwrote the first (Min/Max both ended up named max_price).
      // The first aggregate aliases the shared column-selection ref (that is how the
      // output column is named); subsequent ones are converted to secondary aggregates
      // on their own expression columns carrying their own aliases.
      ColumnRef ref0 = (ColumnRef) ai.getAggregate(0).getDataRef();
      ColumnRef ref1 = (ColumnRef) ai.getAggregate(1).getDataRef();
      ColumnRef ref2 = (ColumnRef) ai.getAggregate(2).getDataRef();
      assertEquals("min_price", ref0.getAlias());
      assertEquals("max_price", ref1.getAlias());
      assertEquals("n", ref2.getAlias());

      // The 2nd and 3rd secondaries must bind to DISTINCT expression columns —
      // the unique-name scan previously missed aliased expression columns, so the
      // third aggregate collided with the second (both bound to price_1).
      assertEquals("price_1", ref1.getAttribute());
      assertEquals("price_2", ref2.getAttribute());

      // The first alias lands on the shared base ref (output naming mechanism);
      // the second must NOT have overwritten it.
      ColumnRef base = null;
      ColumnSelection cs2 = t.getColumnSelection(false);

      for(int i = 0; i < cs2.getAttributeCount(); i++) {
         if(cs2.getAttribute(i) instanceof ColumnRef cr && "price".equals(cr.getAttribute()) &&
            !(cr.getDataRef() instanceof ExpressionRef))
         {
            base = cr;
            break;
         }
      }

      assertNotNull(base);
      assertEquals("min_price", base.getAlias());
   }

   @Test
   void reAggregatingSameTableClearsStalePriorAlias() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "cust", "store", "amount");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // First pass: per-customer average, aliased "customer_avg". This sets the alias
      // directly on the shared "amount" ColumnRef (the output-naming mechanism).
      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T", groups("cust", "store"),
            List.of(new WorksheetMutationSupport.AggregateSpec("amount", "AVG", "customer_avg"))));

      // Second pass on the SAME table (no mirror), attempting to chain by referencing
      // the first pass's output alias as the new aggregate's input field. Before the
      // fix, "customer_avg" silently resolved back to the raw "amount" ColumnRef (the
      // alias was still sitting on it) and computed a flat AVG over un-aggregated rows
      // — numerically indistinguishable from never aggregating by customer at all, but
      // presented as if it were the average of per-customer averages. It must now fail
      // loud instead, since "customer_avg" no longer resolves to anything once the
      // prior aggregate's aliases are cleared.
      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.setGroupAggregate("T", groups("store"),
               List.of(new WorksheetMutationSupport.AggregateSpec(
                  "customer_avg", "AVG", "avg_of_avgs")))));
      assertTrue(ex.getMessage().contains("customer_avg"));

      // The raw "amount" column must be usable again under its own name — clearing the
      // stale alias must not leave the column unreachable.
      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T", groups("store"),
            List.of(new WorksheetMutationSupport.AggregateSpec("amount", "SUM", "total"))));
      assertEquals(1, t.getAggregateInfo().getAggregateCount());
   }

   @Test
   void setGroupAggregateCrosstabTogglesAggregateInfo() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "cust", "store", "amount");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T", groups("cust", "store"),
            List.of(new WorksheetMutationSupport.AggregateSpec("amount", "SUM", null)), true));
      assertTrue(t.getAggregateInfo().isCrosstab());

      // Switching back off must clear it again, matching the Composer dialog's own toggle.
      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T", groups("cust", "store"),
            List.of(new WorksheetMutationSupport.AggregateSpec("amount", "SUM", null)), false));
      assertFalse(t.getAggregateInfo().isCrosstab());

      // The 3-arg overload (no explicit crosstab) must default to false.
      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T", groups("cust", "store"),
            List.of(new WorksheetMutationSupport.AggregateSpec("amount", "SUM", null))));
      assertFalse(t.getAggregateInfo().isCrosstab());
   }

   @Test
   void renameAliasSurvivesReAggregation() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "cust", "store", "amount");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // Deliberate rename: writes the SAME ColumnRef.alias field that
      // applyAggregateInfo uses to label aggregate outputs.
      svc.apply("TOK", agent, ed -> ed.renameColumn("T", "amount", "revenue"));

      // Aggregate the renamed column WITHOUT an explicit output alias, then
      // re-aggregate. clearAggregateAliases used to wipe every aggregate ref's alias
      // indiscriminately, destroying the deliberate rename; it must now clear only the
      // aliases applyAggregateInfo itself recorded.
      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T", groups("cust"),
            List.of(new WorksheetMutationSupport.AggregateSpec("amount", "SUM", null))));
      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T", groups("store"),
            List.of(new WorksheetMutationSupport.AggregateSpec("amount", "SUM", null))));

      ColumnRef base = (ColumnRef) t.getColumnSelection(false).getAttribute("revenue");
      assertNotNull(base, "the renamed column must still resolve by its alias");
      assertEquals("revenue", base.getAlias(),
         "a rename_column alias on an aggregated column must survive re-aggregation");
   }

   /** Finds a column by its base attribute name, which an alias on it would otherwise shadow. */
   private static ColumnRef columnByAttribute(TableAssembly t, String attribute) {
      ColumnSelection cs = t.getColumnSelection(false);

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         if(cs.getAttribute(i) instanceof ColumnRef cr && attribute.equals(cr.getAttribute())) {
            return cr;
         }
      }

      return null;
   }

   /**
    * A preview between the aggregate and the clear used to strand the output alias for good.
    *
    * <p>clearAggregateAliases nulls the alias on the ColumnRef inside the OLD AggregateInfo, which
    * is normally the very object the column selection holds. WorksheetPreviewService snapshots and
    * restores the AggregateInfo around RUNTIME_MODE execution -- necessary, since
    * AbstractTableAssembly.replaceVariables rewrites it in place -- and AggregateInfo.clone()
    * deep-clones every DataRef. Afterwards the two are separate objects, so the clear nulled an
    * alias nothing reads and the column the model reports kept a name like "total" over
    * un-aggregated rows. Live-confirmed 2026-08-25 (L2 Finding 20): the alias survived a clear only
    * when a preview had run in between. The clone here is that preview, reduced to the one step
    * that matters.
    */
   @Test
   void aggregateAliasIsClearedEvenAfterTheAggregateInfoHasBeenCloned() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "cust", "store", "amount");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T", groups("cust"),
            List.of(new WorksheetMutationSupport.AggregateSpec("amount", "SUM", "total"))));
      assertEquals("total", columnByAttribute(t, "amount").getAlias(),
         "precondition: the aggregate labelled its output column");

      // What a preview does: execute against a cloned AggregateInfo, then install the clone.
      t.setAggregateInfo((AggregateInfo) t.getAggregateInfo().clone());

      svc.apply("TOK", agent, ed -> ed.setGroupAggregate("T", List.of(), List.of()));

      ColumnRef base = columnByAttribute(t, "amount");
      assertNotNull(base, "the raw column must still be there after the clear");
      assertNull(base.getAlias(),
         "an aggregate output alias must not outlive the aggregate that created it");
   }

   /** Same separation, but the second call re-aggregates rather than clearing outright. */
   @Test
   void aggregateAliasIsClearedOnReAggregationAfterTheAggregateInfoHasBeenCloned()
      throws Exception
   {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "cust", "store", "amount");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T", groups("cust"),
            List.of(new WorksheetMutationSupport.AggregateSpec("amount", "SUM", "total"))));
      t.setAggregateInfo((AggregateInfo) t.getAggregateInfo().clone());

      // Chaining on the prior output alias must still fail loud: that refusal is only correct
      // because the alias is gone, and it was reachable again once a clone stranded it.
      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.setGroupAggregate("T", groups("store"),
               List.of(new WorksheetMutationSupport.AggregateSpec("total", "AVG", "avg_total")))));
      assertTrue(ex.getMessage().contains("total"));
      assertNull(columnByAttribute(t, "amount").getAlias(),
         "the prior output alias must be gone even though the AggregateInfo was cloned");
   }

   /** The negative control: the clear must not reach a deliberate rename_column alias. */
   @Test
   void renameAliasSurvivesAClearAfterTheAggregateInfoHasBeenCloned() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "cust", "store", "amount");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.renameColumn("T", "amount", "revenue"));
      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T", groups("cust"),
            List.of(new WorksheetMutationSupport.AggregateSpec("amount", "SUM", null))));

      t.setAggregateInfo((AggregateInfo) t.getAggregateInfo().clone());

      svc.apply("TOK", agent, ed -> ed.setGroupAggregate("T", List.of(), List.of()));

      assertEquals("revenue", columnByAttribute(t, "amount").getAlias(),
         "a rename_column alias must survive a clear that follows a preview");
   }

   /**
    * The clear now reaches into the column selection by name, so it has to be narrow: matching on
    * the alias alone would strip an unrelated column that happens to carry the same one.
    */
   @Test
   void clearingAnAggregateAliasLeavesAnotherColumnCarryingTheSameAliasAlone() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "cust", "store", "amount");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T", groups("cust"),
            List.of(new WorksheetMutationSupport.AggregateSpec("amount", "SUM", "total"))));

      // Set by hand rather than through renameColumn, which would refuse the collision -- the
      // point is that the sweep is bounded by the base attribute, not that this state is reachable.
      columnByAttribute(t, "store").setAlias("total");
      t.setAggregateInfo((AggregateInfo) t.getAggregateInfo().clone());

      svc.apply("TOK", agent, ed -> ed.setGroupAggregate("T", List.of(), List.of()));

      assertNull(columnByAttribute(t, "amount").getAlias(), "the aggregated column loses its alias");
      assertEquals("total", columnByAttribute(t, "store").getAlias(),
         "a different column sharing the alias must be left alone");
   }

   @Test
   void renameAliasSurvivesReAggregationAfterFailedIntermediateCall() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "cust", "store", "amount");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.renameColumn("T", "amount", "revenue"));
      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T", groups("cust"),
            List.of(new WorksheetMutationSupport.AggregateSpec("amount", "SUM", null))));

      // A FAILED intermediate call must not consume the alias bookkeeping of the
      // still-active AggregateInfo — otherwise the next successful call would fall
      // into the unknown-provenance clear-all fallback and wipe the rename.
      assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.setGroupAggregate("T", groups("no_such_group"),
               List.of(new WorksheetMutationSupport.AggregateSpec("amount", "SUM", null)))));

      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T", groups("store"),
            List.of(new WorksheetMutationSupport.AggregateSpec("amount", "SUM", null))));

      ColumnRef base = (ColumnRef) t.getColumnSelection(false).getAttribute("revenue");
      assertNotNull(base, "the renamed column must still resolve by its alias");
      assertEquals("revenue", base.getAlias(),
         "a failed aggregate call in between must not cause the rename to be wiped");
   }

   @Test
   void setGroupAggregateFailsLoudOnUnknownColumn() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "cat", "val");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // An unresolvable field previously produced a bogus AttributeRef that the engine
      // silently dropped — a plausible-but-wrong result. It must fail loud instead.
      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.setGroupAggregate("T", groups("cat"),
               List.of(new WorksheetMutationSupport.AggregateSpec("no_such_col", "SUM", "x")))));
      assertTrue(ex.getMessage().contains("no_such_col"));
      assertTrue(ex.getMessage().contains("Available columns"));

      PairingException ex2 = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.setGroupAggregate("T", groups("no_such_group"), List.of())));
      assertTrue(ex2.getMessage().contains("no_such_group"));
   }

   @Test
   void setGroupAggregateAppliesNamedGroup() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "state", "amount");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      List<WorksheetMutationSupport.GroupMapping> mappings = List.of(
         new WorksheetMutationSupport.GroupMapping("Northeast", List.of("NY", "NJ")));
      svc.apply("TOK", agent, ed ->
         ed.addNamedGroup("NortheastGroup", "T", "state", null, mappings, true));

      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T",
            List.of(new WorksheetMutationSupport.GroupSpec("state", null, "NortheastGroup")),
            List.of(new WorksheetMutationSupport.AggregateSpec("amount", "SUM", null))));

      AggregateInfo ai = t.getAggregateInfo();
      assertEquals(1, ai.getGroupCount());
      GroupRef gr = ai.getGroup(0);
      assertEquals("NortheastGroup", gr.getNamedGroupAssembly());
      assertNotNull(gr.getNamedGroupInfo(),
         "update() must actually resolve the named group, not just record its name -- " +
         "this is what add_named_group's own tool description promises set_group_aggregate " +
         "will do (PWA-006)");
   }

   @Test
   void setGroupAggregateFailsLoudOnUnknownNamedGroup() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "state", "amount");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // GroupRef.update() itself fails silently (returns false, leaves getNamedGroupInfo()
      // null) on an unresolvable assembly name -- this must be caught and surfaced loudly
      // instead, or the exact silent-drop bug this fix targets would just move one level down.
      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.setGroupAggregate("T",
               List.of(new WorksheetMutationSupport.GroupSpec("state", null, "NoSuchGroup")),
               List.of())));
      assertTrue(ex.getMessage().contains("NoSuchGroup"));
   }

   @Test
   void setGroupAggregateRejectsNamedGroupWithDateLevel() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "orderDate", "amount");
      ws.addAssembly(t);
      ColumnRef dateCol = (ColumnRef) t.getColumnSelection(false).getAttribute("orderDate");
      dateCol.setDataType(XSchema.DATE);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.addNamedGroup("SomeGroup", "T", "orderDate", null, List.of(), false));

      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.setGroupAggregate("T",
               List.of(new WorksheetMutationSupport.GroupSpec("orderDate", "QUARTER", "SomeGroup")),
               List.of())));
      assertTrue(ex.getMessage().contains("mutually exclusive"));
   }

   @Test
   void setGroupAggregateAppliesDatasourceScopedNamedGroup() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "state", "amount");
      ws.addAssembly(t);

      // Built the way WorksheetAgentController#addDatasourceScopedNamedGroup builds a
      // datasource/logical-model-scoped "Only For" grouping (Bug #76097's shape, and this
      // bug's own PWA-006 repro): attached to a MODEL SourceInfo/AttributeRef, not to any
      // worksheet table -- unlike Editor.addNamedGroup's table+column path used by the other
      // tests above. GroupRef.update()'s COLUMN_ATTACHED branch matches only by attribute
      // name (not table/source identity, see GroupRef.java:443-450), so this must resolve
      // for set_group_aggregate even though it would NOT resolve for set_cell_binding's
      // stricter, source-aware FieldRefFactory#resolveNamedGroupInfo matcher.
      NamedGroupInfo ngi = new NamedGroupInfo();
      ngi.setOthers(XConstants.LEAVE_OTHERS);
      AttributeRef attrRef = new AttributeRef("Customer", "state");
      attrRef.setDataType(XSchema.STRING);
      ConditionList conds = WorksheetMutationSupport.buildGroupConditionList(
         XSchema.STRING, new ColumnRef(attrRef),
         new WorksheetMutationSupport.GroupMapping("Northeast", List.of("NY", "NJ")), ws);
      ngi.setGroupCondition("Northeast", conds);

      DefaultNamedGroupAssembly nga = new DefaultNamedGroupAssembly(ws, "NortheastGroup");
      nga.setNamedGroupInfo(ngi);
      nga.setAttachedType(AttachedAssembly.COLUMN_ATTACHED);
      nga.setAttachedSource(new SourceInfo(SourceInfo.MODEL, "Examples/Orders", "Order Model"));
      nga.setAttachedAttribute(new ColumnRef(attrRef));
      ws.addAssembly(nga);

      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T",
            List.of(new WorksheetMutationSupport.GroupSpec("state", null, "NortheastGroup")),
            List.of(new WorksheetMutationSupport.AggregateSpec("amount", "SUM", null))));

      AggregateInfo ai = t.getAggregateInfo();
      GroupRef gr = ai.getGroup(0);
      assertEquals("NortheastGroup", gr.getNamedGroupAssembly());
      assertNotNull(gr.getNamedGroupInfo(),
         "a datasource-scoped named group must resolve via set_group_aggregate even though " +
         "its attached source (MODEL/Examples/Orders) differs from the worksheet table's " +
         "own source, since GroupRef.update() matches only by attribute name");
   }

   @Test
   void setGroupAggregateFailsLoudOnNamedGroupColumnMismatch() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "state", "region", "amount");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      List<WorksheetMutationSupport.GroupMapping> mappings = List.of(
         new WorksheetMutationSupport.GroupMapping("Northeast", List.of("NY", "NJ")));
      svc.apply("TOK", agent, ed ->
         ed.addNamedGroup("NortheastGroup", "T", "state", null, mappings, true));

      // NortheastGroup is COLUMN_ATTACHED to "state", not "region". GroupRef.update()
      // matches only by attribute name (GroupRef.java:443-450) and on a mismatch
      // silently returns true while leaving getNamedGroupInfo() null, degrading to plain
      // group-by-region with no error -- the exact PWA-006 symptom via a different
      // trigger (mismatched field/namedGroup pairing instead of a nonexistent name).
      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.setGroupAggregate("T",
               List.of(new WorksheetMutationSupport.GroupSpec("region", null, "NortheastGroup")),
               List.of(new WorksheetMutationSupport.AggregateSpec("amount", "SUM", null)))));
      assertTrue(ex.getMessage().contains("NortheastGroup"));
      assertTrue(ex.getMessage().contains("region"));
   }

   @Test
   void setGroupAggregateFailsLoudOnNamedGroupDataTypeMismatch() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "state", "amount");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // table/column omitted -> a standalone, DATA_TYPE_ATTACHED named group (see
      // WorksheetEditService.Editor#addNamedGroup) scoped to INTEGER columns, while
      // "state" (from TestWorksheets.tableWithColumns) defaults to STRING. GroupRef.update()
      // checks AssetUtil.isCompatible(dtype, ref.getDataType()) and on a mismatch silently
      // returns true while leaving getNamedGroupInfo() null, same silent degrade as the
      // COLUMN_ATTACHED mismatch above.
      List<WorksheetMutationSupport.GroupMapping> mappings = List.of(
         new WorksheetMutationSupport.GroupMapping("Big", List.of("100")));
      svc.apply("TOK", agent, ed ->
         ed.addNamedGroup("BigNumbers", null, null, XSchema.INTEGER, mappings, true));

      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.setGroupAggregate("T",
               List.of(new WorksheetMutationSupport.GroupSpec("state", null, "BigNumbers")),
               List.of(new WorksheetMutationSupport.AggregateSpec("amount", "SUM", null)))));
      assertTrue(ex.getMessage().contains("BigNumbers"));
      assertTrue(ex.getMessage().contains("state"));
   }

   @Test
   void setGroupAggregateTreatsNullGroupsAsEmpty() throws Exception {
      // WorksheetAgentController defaults a missing "aggregates" key to List.of() but
      // passes a missing "groups" key through as null unchanged; a null groups list
      // paired with a non-empty aggregates list must not NPE in the group loop below the
      // (groups empty && aggregates empty) early-return guard.
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "cat", "val");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T", null,
            List.of(new WorksheetMutationSupport.AggregateSpec("val", "SUM", null))));

      AggregateInfo ai = t.getAggregateInfo();
      assertEquals(0, ai.getGroupCount());
      assertEquals(1, ai.getAggregateCount());
   }

   @Test
   void setGroupAggregateAppliesDateLevelToDateColumn() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "orderDate", "total");
      ws.addAssembly(t);
      ColumnRef dateCol = (ColumnRef) t.getColumnSelection(false).getAttribute("orderDate");
      dateCol.setDataType(XSchema.DATE);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T",
            List.of(new WorksheetMutationSupport.GroupSpec("orderDate", "QUARTER")),
            List.of(new WorksheetMutationSupport.AggregateSpec("total", "SUM", null))));

      AggregateInfo ai = t.getAggregateInfo();
      assertEquals(1, ai.getGroupCount());

      // setDateGroup() alone is read by the Composer's Group and Aggregate dialog
      // (GroupRefModel#getDgroup), but PreAssetQuery.mergeGroupBy() builds the actual
      // GROUP BY SQL purely from the GroupRef's DataRef and never consults
      // getDateGroup() — so on any regular JDBC-mergeable table, a GroupRef still
      // wrapping the raw column would silently group by the exact date value instead of
      // by quarter. The DataRef itself must be a DateRangeRef-wrapped column for the
      // bucketing to actually take effect; setDateGroup() is only a round-trip marker.
      assertEquals(XConstants.QUARTER_DATE_GROUP, ai.getGroup(0).getDateGroup());
      DataRef groupDataRef = ai.getGroup(0).getDataRef();
      assertInstanceOf(ColumnRef.class, groupDataRef);
      DataRef inner = ((ColumnRef) groupDataRef).getDataRef();
      assertInstanceOf(DateRangeRef.class, inner);
      assertEquals(XConstants.QUARTER_DATE_GROUP, ((DateRangeRef) inner).getDateOption());
      assertEquals("orderDate", ((DateRangeRef) inner).getDataRef().getName());

      // The raw "orderDate" column is kept alongside the new wrapped column — matching
      // AggregateDialogService#processDateGrouping, which inserts rather than replaces —
      // so it stays independently usable (e.g. still shows up in the Composer's Group
      // and Aggregate dialog, still filterable) exactly as it would after applying a
      // date level through the UI.
      assertNotNull(t.getColumnSelection(false).getAttribute("orderDate"));
      ColumnRef rawAfter = (ColumnRef) t.getColumnSelection(false).getAttribute("orderDate");
      assertFalse(rawAfter.getDataRef() instanceof DateRangeRef);
   }

   @Test
   void setGroupAggregateDropsStaleDateRangeColumnOnLevelChange() throws Exception {
      // Re-grouping the same field at a different level (e.g. the agent changes
      // "group by Quarter" to "group by Month") must not leave the previous level's
      // materialized DateRangeRef column ("Quarter(orderDate)") behind as an orphan —
      // each call resolves the field back to the untouched raw column, so without
      // explicit cleanup a stale wrapped column accumulates per level change and
      // pollutes read_worksheet_model's column list with phantom entries forever.
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "orderDate", "total");
      ws.addAssembly(t);
      ColumnRef dateCol = (ColumnRef) t.getColumnSelection(false).getAttribute("orderDate");
      dateCol.setDataType(XSchema.DATE);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T",
            List.of(new WorksheetMutationSupport.GroupSpec("orderDate", "QUARTER")),
            List.of(new WorksheetMutationSupport.AggregateSpec("total", "SUM", null))));

      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T",
            List.of(new WorksheetMutationSupport.GroupSpec("orderDate", "MONTH")),
            List.of(new WorksheetMutationSupport.AggregateSpec("total", "SUM", null))));

      ColumnSelection cs = t.getColumnSelection(false);
      assertNull(cs.getAttribute("Quarter(orderDate)"),
                 "stale Quarter(orderDate) column must be dropped when re-grouped to MONTH");
      assertNotNull(cs.getAttribute("Month(orderDate)"));
      assertNotNull(cs.getAttribute("orderDate"), "raw base column must still be present");
      assertEquals(3, cs.getAttributeCount(),
                    "expected exactly orderDate, Month(orderDate), total — no leftovers");

      AggregateInfo ai = t.getAggregateInfo();
      assertEquals(1, ai.getGroupCount());
      assertEquals("Month(orderDate)", ai.getGroup(0).getDataRef().getName());
   }

   @Test
   void setGroupAggregateDropsStaleDateRangeColumnWhenFieldRemovedFromGroups() throws Exception {
      // set_group_aggregate fully replaces the AggregateInfo each call, so a field
      // simply absent from a later call's `groups` (the agent restructures to group by
      // something else entirely, without mentioning the date field again) is exactly as
      // "no longer grouped" as one explicitly re-leveled — the previously materialized
      // "Quarter(orderDate)" must not be left behind just because this call's cleanup
      // only used to look at fields present in THIS call's groups.
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t =
         TestWorksheets.tableWithColumns(ws, "T", "orderDate", "cat", "total");
      ws.addAssembly(t);
      ColumnRef dateCol = (ColumnRef) t.getColumnSelection(false).getAttribute("orderDate");
      dateCol.setDataType(XSchema.DATE);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T",
            List.of(new WorksheetMutationSupport.GroupSpec("orderDate", "QUARTER")),
            List.of(new WorksheetMutationSupport.AggregateSpec("total", "SUM", null))));

      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T",
            groups("cat"),
            List.of(new WorksheetMutationSupport.AggregateSpec("total", "SUM", null))));

      ColumnSelection cs = t.getColumnSelection(false);
      assertNull(cs.getAttribute("Quarter(orderDate)"),
                 "stale Quarter(orderDate) column must be dropped when orderDate is no " +
                 "longer grouped at all");
      assertNotNull(cs.getAttribute("orderDate"), "raw base column must still be present");
   }

   @Test
   void setGroupAggregateDropsStaleDateRangeColumnOnFullClear() throws Exception {
      // A call with both groups and aggregates empty (e.g. "show me the raw rows again")
      // must clean up a stale DateRangeRef column exactly like a re-level or a
      // field-dropped-from-groups call does — AggregateDialogService#applyAggregateInfo
      // (the UI path this mirrors) runs its cleanup sweep unconditionally, even when the
      // new AggregateInfo ends up completely empty, so this must too.
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "orderDate", "total");
      ws.addAssembly(t);
      ColumnRef dateCol = (ColumnRef) t.getColumnSelection(false).getAttribute("orderDate");
      dateCol.setDataType(XSchema.DATE);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T",
            List.of(new WorksheetMutationSupport.GroupSpec("orderDate", "QUARTER")),
            List.of(new WorksheetMutationSupport.AggregateSpec("total", "SUM", null))));

      svc.apply("TOK", agent, ed -> ed.setGroupAggregate("T", List.of(), List.of()));

      ColumnSelection cs = t.getColumnSelection(false);
      assertNull(cs.getAttribute("Quarter(orderDate)"),
                 "stale Quarter(orderDate) column must be dropped on a full clear");
      assertNotNull(cs.getAttribute("orderDate"), "raw base column must still be present");
      assertTrue(t.getAggregateInfo().isEmpty());
      assertFalse(t.isAggregate());
   }

   @Test
   void setGroupAggregatePreservesAddDateRangeColumnCreatedColumn() throws Exception {
      // A column created via add_date_range_column is a deliberate, standalone derived
      // column (not tied to any group) — a later set_group_aggregate call that groups
      // the SAME base date column at some level must not sweep it away just because it
      // wraps the same base attribute as the new group's DateRangeRef column.
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "orderDate", "total");
      ws.addAssembly(t);
      ColumnRef dateCol = (ColumnRef) t.getColumnSelection(false).getAttribute("orderDate");
      dateCol.setDataType(XSchema.DATE);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addDateRangeColumn("T", "orderDate", "YEAR"));

      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T",
            List.of(new WorksheetMutationSupport.GroupSpec("orderDate", "QUARTER")),
            List.of(new WorksheetMutationSupport.AggregateSpec("total", "SUM", null))));

      ColumnSelection cs = t.getColumnSelection(false);
      assertNotNull(cs.getAttribute("Year(orderDate)"),
                    "add_date_range_column's standalone derived column must survive");
      assertNotNull(cs.getAttribute("Quarter(orderDate)"));
      assertNotNull(cs.getAttribute("orderDate"));
   }

   // =========================================================================
   // editDateRangeColumn / editNumericRangeColumn (Bug #76084)
   // =========================================================================

   @Test
   void editDateRangeColumnChangesOptionAndRenamesInPlace() throws Exception {
      // The reported bug: an AI agent asked to change "QuarterOfYear(Order Date)" to a
      // month-level grouping had no edit tool, so it deleted and re-added the column
      // instead of updating it in place. This is the in-place path that must exist instead.
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "orderDate", "total");
      ws.addAssembly(t);
      ColumnRef dateCol = (ColumnRef) t.getColumnSelection(false).getAttribute("orderDate");
      dateCol.setDataType(XSchema.DATE);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addDateRangeColumn("T", "orderDate", "QUARTER_OF_YEAR"));
      svc.apply("TOK", agent, ed ->
         ed.editDateRangeColumn("T", "QuarterOfYear(orderDate)", "MONTH_OF_YEAR"));

      ColumnSelection cs = t.getColumnSelection(false);
      assertNull(cs.getAttribute("QuarterOfYear(orderDate)"),
                 "old option's name must be gone — the column was renamed, not duplicated");
      DataRef edited = cs.getAttribute("MonthOfYear(orderDate)");
      assertNotNull(edited, "renamed column must exist under the new option's name");
      DataRef unwrapped = edited instanceof ColumnRef cr ? cr.getDataRef() : edited;
      assertInstanceOf(DateRangeRef.class, unwrapped);
      assertEquals(DateRangeRef.MONTH_OF_YEAR_PART, ((DateRangeRef) unwrapped).getDateOption());
      assertNotNull(cs.getAttribute("orderDate"), "source date column must be untouched");
      assertEquals(1, cs.stream().filter(r -> r instanceof ColumnRef cr &&
         cr.getDataRef() instanceof DateRangeRef).count(),
         "must not leave a stray duplicate column behind");
   }

   @Test
   void editDateRangeColumnFailsLoudOnNonRangeColumn() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "orderDate", "total");
      ws.addAssembly(t);
      ColumnRef dateCol = (ColumnRef) t.getColumnSelection(false).getAttribute("orderDate");
      dateCol.setDataType(XSchema.DATE);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // "orderDate" is the raw source column, not a range column derived from it.
      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed -> ed.editDateRangeColumn("T", "orderDate", "MONTH")));
      assertTrue(ex.getMessage().contains("not a date range column"));
   }

   @Test
   void editDateRangeColumnFailsLoudOnMissingColumn() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "orderDate", "total");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed -> ed.editDateRangeColumn("T", "Nope(orderDate)", "MONTH")));
      assertTrue(ex.getMessage().contains("Column not found"));
   }

   @Test
   void editDateRangeColumnFailsLoudOnRenameCollision() throws Exception {
      // Two range columns already exist at different options (QUARTER_OF_YEAR and
      // MONTH_OF_YEAR). Editing the first to also become MONTH_OF_YEAR would rename it onto
      // the second's name — must be refused, not silently shadow the other column.
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "orderDate", "total");
      ws.addAssembly(t);
      ColumnRef dateCol = (ColumnRef) t.getColumnSelection(false).getAttribute("orderDate");
      dateCol.setDataType(XSchema.DATE);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addDateRangeColumn("T", "orderDate", "QUARTER_OF_YEAR"));
      svc.apply("TOK", agent, ed -> ed.addDateRangeColumn("T", "orderDate", "MONTH_OF_YEAR"));

      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.editDateRangeColumn("T", "QuarterOfYear(orderDate)", "MONTH_OF_YEAR")));
      assertTrue(ex.getMessage().contains("MonthOfYear(orderDate)"));

      ColumnSelection cs = t.getColumnSelection(false);
      assertNotNull(cs.getAttribute("QuarterOfYear(orderDate)"), "refused edit must leave the original untouched");
      DataRef stillMonth = cs.getAttribute("MonthOfYear(orderDate)");
      assertNotNull(stillMonth, "the pre-existing MonthOfYear column must survive, not be shadowed");
      assertEquals(2, cs.stream().filter(r -> r instanceof ColumnRef cr &&
         cr.getDataRef() instanceof DateRangeRef).count(),
         "still exactly two distinct range columns — no shadowing duplicate");
   }

   @Test
   void editDateRangeColumnAllowsNoOpWhenOptionUnchanged() throws Exception {
      // dateOption resolving to the SAME name as the column already has must not trip the
      // new collision guard against itself.
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "orderDate", "total");
      ws.addAssembly(t);
      ColumnRef dateCol = (ColumnRef) t.getColumnSelection(false).getAttribute("orderDate");
      dateCol.setDataType(XSchema.DATE);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addDateRangeColumn("T", "orderDate", "QUARTER_OF_YEAR"));
      svc.apply("TOK", agent, ed ->
         ed.editDateRangeColumn("T", "QuarterOfYear(orderDate)", "QUARTER_OF_YEAR"));

      ColumnSelection cs = t.getColumnSelection(false);
      assertNotNull(cs.getAttribute("QuarterOfYear(orderDate)"));
   }

   @Test
   void editDateRangeColumnRenameDoesNotLeaveStaleHashForLaterAdd() throws Exception {
      // Regression test for a real bug caught in code review (not a hypothetical): the
      // ColumnRef wrapping a DateRangeRef caches its hashCode() (AbstractDataRef.chash) on
      // first use, from getEntity()+getAttribute() — and ColumnSelection's backing
      // ListWithFastLookup uses that cached hashCode() for add_date_range_column's O(1)
      // addAttribute exclusivity check (rebuilt lazily by iterating elements' hashCode(), so a
      // per-element stale cache survives even a full map rebuild). Renaming dateRef in place
      // without resetting that cache left a later add_date_range_column, whose generated name
      // happened to collide with the just-renamed column, undetected — silently producing two
      // columns both reporting getName() == "MonthOfYear(orderDate)". Empirically confirmed
      // both that the bug existed (bare rename: 2 columns) and that WorksheetEditService's
      // fix (ColumnRef#setDataRef to force cname/chash invalidation) resolves it (1 column).
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "orderDate", "total");
      ws.addAssembly(t);
      ColumnRef dateCol = (ColumnRef) t.getColumnSelection(false).getAttribute("orderDate");
      dateCol.setDataType(XSchema.DATE);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addDateRangeColumn("T", "orderDate", "QUARTER_OF_YEAR"));
      // A second, distinct add exercises addAttribute's exclusivity check (and so builds
      // ListWithFastLookup's indexOfs cache) the same way any real multi-column worksheet
      // would before the edit below ever runs.
      svc.apply("TOK", agent, ed -> ed.addNumericRangeColumn("T", "total", new double[] { 0, 1 }, null));
      svc.apply("TOK", agent, ed ->
         ed.editDateRangeColumn("T", "QuarterOfYear(orderDate)", "MONTH_OF_YEAR"));

      // Reaches the exact name the rename above just produced.
      //
      // This used to be a silent no-op: addAttribute's exclusivity check dropped the duplicate and
      // the call still answered success, so a caller issuing it believed a second column existed.
      // add_date_range_column now refuses the collision up front (requireDerivedColumnFits,
      // mirroring what ValueRangeService:204 does with an ERROR command), which is the same
      // outcome for the asset and an honest one for the caller.
      //
      // Note for whoever revisits the cached-hashCode fix described above: it is untouched and
      // still required, but this path no longer reaches it -- the guard runs first, and it
      // compares names rather than relying on hashCode. So this test now locks in the refusal,
      // not the hash invalidation.
      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.addDateRangeColumn("T", "orderDate", "MONTH_OF_YEAR")));

      assertTrue(ex.getMessage().contains("MonthOfYear(orderDate)"), ex.getMessage());

      ColumnSelection cs = t.getColumnSelection(false);
      long monthColumns = cs.stream()
         .filter(r -> "MonthOfYear(orderDate)".equals(r.getName()))
         .count();
      assertEquals(1, monthColumns,
         "the refused add must not have duplicated the name the edit just produced");
      assertEquals(4, cs.getAttributeCount(),
         "and must not have added a column");
   }

   @Test
   void editNumericRangeColumnChangesBoundariesWithoutRenaming() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "amount", "total");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addNumericRangeColumn("T", "amount", new double[] { 0, 50, 100 }, null));
      svc.apply("TOK", agent, ed ->
         ed.editNumericRangeColumn("T", "amount_range", new double[] { 0, 25, 75, 150 }, null));

      ColumnSelection cs = t.getColumnSelection(false);
      DataRef edited = cs.getAttribute("amount_range");
      assertNotNull(edited, "column name must be unchanged — boundaries alone don't rename it");
      DataRef unwrapped = edited instanceof ColumnRef cr ? cr.getDataRef() : edited;
      assertInstanceOf(NumericRangeRef.class, unwrapped);
      assertArrayEquals(new double[] { 0, 25, 75, 150 },
         ((NumericRangeRef) unwrapped).getValueRangeInfo().getValues());
   }

   @Test
   void editNumericRangeColumnPreservesShowBottomTopAndInclusiveType() throws Exception {
      // Regression test from code review: showBottomValue/showTopValue/inclusiveType are also
      // user-settable (the Composer's own Range Column dialog exposes them) but this tool only
      // ever takes boundaries/labels — replacing the ValueRangeInfo outright instead of mutating
      // the existing one would silently reset those three back to constructor defaults on every
      // edit, discarding a customization this tool was never asked to touch.
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "amount", "total");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addNumericRangeColumn("T", "amount", new double[] { 0, 50, 100 }, null));

      // Simulate a customization made through some other path (the Composer's Range Column
      // dialog, in practice) that this tool never exposes a way to set itself.
      ColumnRef added = (ColumnRef) t.getColumnSelection(false).getAttribute("amount_range");
      NumericRangeRef addedRef = (NumericRangeRef) added.getDataRef();
      addedRef.getValueRangeInfo().setShowBottomValue(false);
      addedRef.getValueRangeInfo().setShowTopValue(false);
      addedRef.getValueRangeInfo().setInclusiveType(InclusiveType.UPPER);

      svc.apply("TOK", agent, ed ->
         ed.editNumericRangeColumn("T", "amount_range", new double[] { 0, 25, 75, 150 }, null));

      ColumnSelection cs = t.getColumnSelection(false);
      DataRef edited = cs.getAttribute("amount_range");
      DataRef unwrapped = edited instanceof ColumnRef cr ? cr.getDataRef() : edited;
      ValueRangeInfo info = ((NumericRangeRef) unwrapped).getValueRangeInfo();
      assertArrayEquals(new double[] { 0, 25, 75, 150 }, info.getValues());
      assertFalse(info.isShowBottomValue(), "edit must not reset showBottomValue to its default");
      assertFalse(info.isShowTopValue(), "edit must not reset showTopValue to its default");
      assertEquals(InclusiveType.UPPER, info.getInclusiveType(), "edit must not reset inclusiveType to its default");
   }

   @Test
   void editNumericRangeColumnFailsLoudOnEmptyBoundaries() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "amount", "total");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addNumericRangeColumn("T", "amount", new double[] { 0, 50, 100 }, null));

      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed -> ed.editNumericRangeColumn("T", "amount_range", new double[0], null)));
      assertTrue(ex.getMessage().contains("boundaries must be a non-empty array"));
   }

   @Test
   void editNumericRangeColumnFailsLoudOnNonRangeColumn() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "amount", "total");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed -> ed.editNumericRangeColumn("T", "amount", new double[] { 0, 50 }, null)));
      assertTrue(ex.getMessage().contains("not a numeric range column"));
   }

   // =========================================================================
   // Custom bucket labels on numeric range columns (Bug #75942)
   // =========================================================================

   @Test
   void addNumericRangeColumnAppliesCustomLabels() throws Exception {
      // The reported bug: asked for boundaries 10000/50000 labeled "Below standard" /
      // "Meets standard" / "Good", the AI said this required a formula column even though
      // the Composer's own Range Column dialog already supports it — ValueRangeInfo.labels.
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "total", "other");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addNumericRangeColumn("T", "total",
         new double[] { 10000, 50000 },
         new String[] { "Below standard", "Meets standard", "Good" }));

      ColumnSelection cs = t.getColumnSelection(false);
      DataRef added = cs.getAttribute("total_range");
      assertNotNull(added);
      DataRef unwrapped = added instanceof ColumnRef cr ? cr.getDataRef() : added;
      assertInstanceOf(NumericRangeRef.class, unwrapped);
      assertArrayEquals(new String[] { "Below standard", "Meets standard", "Good" },
         ((NumericRangeRef) unwrapped).getValueRangeInfo().getLabels());
      // The label ordering claim itself, not just that setLabels was called: ask the
      // generated script expression which label applies below the first boundary, and
      // confirm it is the bottom label, not the top or middle one.
      assertTrue(((NumericRangeRef) unwrapped).getScriptExpression().contains("\"Below standard\""));
   }

   @Test
   void addNumericRangeColumnFailsLoudOnWrongLabelCount() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "total", "other");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // 2 boundaries need 3 labels (below, between, above); only 2 given.
      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed -> ed.addNumericRangeColumn("T", "total",
            new double[] { 10000, 50000 }, new String[] { "Low", "High" })));
      assertTrue(ex.getMessage().contains("exactly 3 entries"));
      assertTrue(ex.getMessage().contains("got 2"));

      ColumnSelection cs = t.getColumnSelection(false);
      assertNull(cs.getAttribute("total_range"), "refused add must not leave a half-built column");
   }

   @Test
   void addNumericRangeColumnAllowsOmittedLabels() throws Exception {
      // null/empty labels must not be misread as "wrong count" — it means "no custom labels".
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "total", "other");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.addNumericRangeColumn("T", "total", new double[] { 10000, 50000 }, null));
      svc.apply("TOK", agent, ed -> ed.editNumericRangeColumn("T", "total_range",
         new double[] { 10000, 50000 }, new String[0]));

      ColumnSelection cs = t.getColumnSelection(false);
      DataRef edited = cs.getAttribute("total_range");
      DataRef unwrapped = edited instanceof ColumnRef cr ? cr.getDataRef() : edited;
      assertEquals(0, ((NumericRangeRef) unwrapped).getValueRangeInfo().getLabels().length);
   }

   @Test
   void editNumericRangeColumnOmittingLabelsClearsPreExistingOnes() throws Exception {
      // Regression test from code review (stylebi-wiz PR #1857): the tool description claims
      // omitting `labels` on an edit clears any PRE-EXISTING custom labels, distinct from
      // addNumericRangeColumnAllowsOmittedLabels above, which only exercises a column that never
      // had labels to begin with. Must hold even after editNumericRangeColumn started mutating
      // the existing ValueRangeInfo (to preserve showBottomValue/showTopValue/inclusiveType)
      // instead of always replacing it — labels must still not be one of the preserved fields.
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "total", "other");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addNumericRangeColumn("T", "total",
         new double[] { 10000, 50000 }, new String[] { "Below standard", "Meets standard", "Good" }));
      svc.apply("TOK", agent, ed ->
         ed.editNumericRangeColumn("T", "total_range", new double[] { 10000, 50000 }, null));

      ColumnSelection cs = t.getColumnSelection(false);
      DataRef edited = cs.getAttribute("total_range");
      DataRef unwrapped = edited instanceof ColumnRef cr ? cr.getDataRef() : edited;
      assertEquals(0, ((NumericRangeRef) unwrapped).getValueRangeInfo().getLabels().length,
         "omitting labels on an edit must clear the column's previously-set custom labels");
   }

   @Test
   void editNumericRangeColumnFailsLoudOnWrongLabelCountAndLeavesColumnUnchanged() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "total", "other");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addNumericRangeColumn("T", "total",
         new double[] { 10000, 50000 }, new String[] { "Below standard", "Meets standard", "Good" }));

      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed -> ed.editNumericRangeColumn("T", "total_range",
            new double[] { 10000, 30000, 50000 }, new String[] { "Below standard", "Meets standard", "Good" })));
      assertTrue(ex.getMessage().contains("exactly 4 entries"));

      // Refused edit must leave the original boundaries/labels untouched.
      ColumnSelection cs = t.getColumnSelection(false);
      DataRef unwrapped = ((ColumnRef) cs.getAttribute("total_range")).getDataRef();
      assertArrayEquals(new double[] { 10000, 50000 },
         ((NumericRangeRef) unwrapped).getValueRangeInfo().getValues());
   }

   @Test
   void setGroupAggregateFailsLoudOnUnrecognizedDateLevel() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "orderDate", "total");
      ws.addAssembly(t);
      ColumnRef dateCol = (ColumnRef) t.getColumnSelection(false).getAttribute("orderDate");
      dateCol.setDataType(XSchema.DATE);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // An unrecognized dateLevel must fail loud instead of silently defaulting to a
      // yearly grouping, matching the fail-loud stance already taken for unknown columns.
      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.setGroupAggregate("T",
               List.of(new WorksheetMutationSupport.GroupSpec("orderDate", "MONTHLY")),
               List.of(new WorksheetMutationSupport.AggregateSpec("total", "SUM", null)))));
      assertTrue(ex.getMessage().contains("MONTHLY"));
   }

   @Test
   void setGroupAggregateFailsLoudOnDateLevelForNonDateColumn() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "cat", "val");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // "cat" is a plain string column (the default type from TestWorksheets); a dateLevel
      // silently applied to it would produce a plausible-but-meaningless grouping instead
      // of failing loud.
      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.setGroupAggregate("T",
               List.of(new WorksheetMutationSupport.GroupSpec("cat", "QUARTER")),
               List.of(new WorksheetMutationSupport.AggregateSpec("val", "SUM", null)))));
      assertTrue(ex.getMessage().contains("cat"));
      assertTrue(ex.getMessage().contains("date type"));
   }

   // =========================================================================
   // Ranking tests
   // =========================================================================

   @Test
   void setRankingOnAggregatedTableResolvesAggregateRef() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "employee", "total");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> {
         ed.setGroupAggregate("T", groups("employee"),
            List.of(new WorksheetMutationSupport.AggregateSpec("total", "SUM", null)));
         ed.setRanking("T",
            new WorksheetMutationSupport.RankingSpec("total", 3, "TOP_N", false));
      });

      ConditionList cl = t.getRankingConditionList().getConditionList();
      assertNotNull(cl);
      assertFalse(cl.isEmpty());

      // Ranking is evaluated post-aggregate: it must bind to the Sum(total) aggregate
      // ref, not a plain pre-aggregate reference to the raw "total" column — otherwise
      // the filter displays as "total top3" instead of "Sum(total) top3".
      DataRef ref = cl.getConditionItem(0).getAttribute();
      assertTrue(ref instanceof AggregateRef,
         "ranking on an aggregated table must resolve to the AggregateRef, got: " + ref);
      assertEquals("total", ((AggregateRef) ref).getAttribute());
   }

   @Test
   void setRankingOnAggregatedTableByDimensionResolvesGroupRef() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "employee", "total");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // Ranking need not target the aggregate — it can also rank by one of the
      // group-by (pre-aggregate) dimensions, e.g. top 3 employees alphabetically.
      svc.apply("TOK", agent, ed -> {
         ed.setGroupAggregate("T", groups("employee"),
            List.of(new WorksheetMutationSupport.AggregateSpec("total", "SUM", null)));
         ed.setRanking("T",
            new WorksheetMutationSupport.RankingSpec("employee", 3, "TOP_N", false));
      });

      ConditionList cl = t.getRankingConditionList().getConditionList();
      DataRef ref = cl.getConditionItem(0).getAttribute();
      assertTrue(ref instanceof GroupRef,
         "ranking by a group dimension must resolve to the GroupRef, got: " + ref);
      assertEquals("employee", ref.getAttribute());
   }

   @Test
   void setRankingWithOfResolvesGroupRankedByAggregateRef() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "employee", "total");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // "top 3 employees of Sum(total)" — field names the group to rank, 'of' names the
      // aggregate to rank it by. Both must resolve to their real AggregateInfo refs, not
      // a raw pre-aggregate ColumnRef, or the condition silently ranks by nothing.
      svc.apply("TOK", agent, ed -> {
         ed.setGroupAggregate("T", groups("employee"),
            List.of(new WorksheetMutationSupport.AggregateSpec("total", "SUM", null)));
         ed.setRanking("T",
            new WorksheetMutationSupport.RankingSpec("employee", 3, "TOP_N", false, "total"));
      });

      ConditionList cl = t.getRankingConditionList().getConditionList();
      DataRef attr = cl.getConditionItem(0).getAttribute();
      assertTrue(attr instanceof GroupRef,
         "'field' must resolve to the GroupRef, got: " + attr);
      assertEquals("employee", attr.getAttribute());

      RankingCondition rc = (RankingCondition) cl.getConditionItem(0).getXCondition();
      DataRef ofRef = rc.getDataRef();
      assertTrue(ofRef instanceof AggregateRef,
         "'of' must resolve to the AggregateRef, got: " + ofRef);
      assertEquals("total", ((AggregateRef) ofRef).getAttribute());
   }

   @Test
   void setRankingOnUngroupedTableResolvesRawColumn() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "employee", "total");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.setRanking("T",
            new WorksheetMutationSupport.RankingSpec("total", 3, "TOP_N", false)));

      ConditionList cl = t.getRankingConditionList().getConditionList();
      DataRef ref = cl.getConditionItem(0).getAttribute();

      // No AggregateInfo is set, so ranking must still fall back to the plain column —
      // ranking on a non-aggregated table must keep working exactly as before.
      assertFalse(ref instanceof AggregateRef);
      assertEquals("total", ref.getAttribute());
   }

   @Test
   void setRankingFallsBackToPrivateSelectionWhenColumnMissingFromPublic() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "employee", "total");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // Simulate what set_column_visibility ultimately produces: a column present in
      // the PRIVATE selection but absent from the PUBLIC one (the public selection is
      // what getColumnSelection(true) — the "post" lookup — falls back to). No
      // AggregateInfo is set, so the AggregateInfo lookup is a no-op; ranking must still
      // find "total" via the PRIVATE selection instead of returning a bogus
      // AttributeRef, or a hidden column becomes unrankable.
      ColumnSelection publicSelection = t.getColumnSelection(false).clone();
      publicSelection.removeAttribute(publicSelection.getAttribute("total"));
      t.setColumnSelection(publicSelection, true);

      svc.apply("TOK", agent, ed ->
         ed.setRanking("T", new WorksheetMutationSupport.RankingSpec("total", 3, "TOP_N", false)));

      ConditionList cl = t.getRankingConditionList().getConditionList();
      DataRef ref = cl.getConditionItem(0).getAttribute();
      assertTrue(ref instanceof ColumnRef,
         "ranking must fall back to the private selection when the column is missing " +
         "from the public one, got: " + ref);
      assertEquals("total", ref.getAttribute());
   }

   @Test
   void setRankingOnAggregatedTableByUnrelatedColumnResolvesRawColumn() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t =
         TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "employee", "total", "orderNumber");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // Grouping/aggregating a table does not prune its column selection — a column
      // that is neither a group nor an aggregate (here "orderNumber") remains a plain,
      // resolvable column. Ranking by it must not be forced into an aggregate/group
      // match; it must resolve to its own raw ColumnRef.
      svc.apply("TOK", agent, ed -> {
         ed.setGroupAggregate("T", groups("employee"),
            List.of(new WorksheetMutationSupport.AggregateSpec("total", "SUM", null)));
         ed.setRanking("T",
            new WorksheetMutationSupport.RankingSpec("orderNumber", 3, "TOP_N", false));
      });

      ConditionList cl = t.getRankingConditionList().getConditionList();
      DataRef ref = cl.getConditionItem(0).getAttribute();
      assertFalse(ref instanceof AggregateRef);
      assertFalse(ref instanceof GroupRef);
      assertTrue(ref instanceof ColumnRef,
         "ranking by a column outside the group/aggregate must resolve to its raw ColumnRef, got: " + ref);
      assertEquals("orderNumber", ref.getAttribute());
   }

   @Test
   void setRankingAcceptsVariableBinding() throws Exception {
      // Regression for Bug #75950: 'n' used to be hardcoded to a plain int, so a
      // Top-N count could never be bound to a worksheet variable the way filter
      // condition values support $(name). RankingCondition.setN(Object) already
      // supported it -- the gap was purely in how RankingSpec carried the value.
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "employee", "total");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.setRanking("T",
            new WorksheetMutationSupport.RankingSpec("total", "$(topN)", "TOP_N", false)));

      ConditionList cl = t.getRankingConditionList().getConditionList();
      RankingCondition rc = (RankingCondition) cl.getConditionItem(0).getXCondition();
      assertTrue(Condition.isVariable(rc.getN()) || rc.getN() instanceof UserVariable,
         "n should round-trip as a variable reference, got: " + rc.getN());
      UserVariable[] vars = rc.getAllVariables();
      assertEquals(1, vars.length);
      assertEquals("topN", vars[0].getName());
   }

   @Test
   void setRankingAcceptsNumericStringForN() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "employee", "total");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.setRanking("T",
            new WorksheetMutationSupport.RankingSpec("total", "5", "TOP_N", false)));

      ConditionList cl = t.getRankingConditionList().getConditionList();
      RankingCondition rc = (RankingCondition) cl.getConditionItem(0).getXCondition();
      assertEquals(5, rc.getN());
   }

   @Test
   void setRankingRejectsInvalidN() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "employee", "total");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.setRanking("T",
               new WorksheetMutationSupport.RankingSpec("total", "not-a-number", "TOP_N", false))));
      assertTrue(ex.getMessage().contains("not-a-number"),
         "error should name the rejected value, got: " + ex.getMessage());
   }

   // =========================================================================
   // Variable choices tests (Bug #76328)
   // =========================================================================

   private static DefaultVariableAssembly variableAssembly(Worksheet ws, String name, String type) {
      AssetVariable var = new AssetVariable(name);
      var.setTypeNode(XSchema.createPrimitiveType(type));
      DefaultVariableAssembly assembly = new DefaultVariableAssembly(ws, name);
      assembly.setVariable(var);
      ws.addAssembly(assembly);
      return assembly;
   }

   private static WorksheetMutationSupport.VariableChoicesSpec embeddedChoices(
      List<String> values, List<String> labels, String displayStyle)
   {
      return new WorksheetMutationSupport.VariableChoicesSpec(
         values, labels, null, null, null, displayStyle);
   }

   private static WorksheetMutationSupport.VariableChoicesSpec queryChoices(
      String table, String labelColumn, String valueColumn, String displayStyle)
   {
      return new WorksheetMutationSupport.VariableChoicesSpec(
         null, null, table, labelColumn, valueColumn, displayStyle);
   }

   @Test
   void editVariableSetsTypedChoicesAndValues() throws Exception {
      Worksheet ws = new Worksheet();
      variableAssembly(ws, "topN", XSchema.INTEGER);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.editVariable("topN", null, null, null,
            embeddedChoices(List.of("1", "5", "10"), List.of("One", "Five", "Ten"), null)));

      AssetVariable updated = ((DefaultVariableAssembly) ws.getAssembly("topN")).getVariable();
      assertArrayEquals(new Object[] {"One", "Five", "Ten"}, updated.getChoices());
      assertArrayEquals(new Object[] {1, 5, 10}, updated.getValues(),
         "values must be typed to the variable's data type (Integer), not raw strings");
      assertEquals(UserVariable.COMBOBOX, updated.getDisplayStyle(),
         "no explicit displayStyle should default to a single-select combobox");
      assertFalse(updated.isMultipleSelection());
   }

   @Test
   void editVariableDefaultsLabelsToValuesWhenOmitted() throws Exception {
      Worksheet ws = new Worksheet();
      variableAssembly(ws, "region", XSchema.STRING);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.editVariable("region", null, null, null,
            embeddedChoices(List.of("East", "West"), null, null)));

      AssetVariable updated = ((DefaultVariableAssembly) ws.getAssembly("region")).getVariable();
      assertArrayEquals(new Object[] {"East", "West"}, updated.getChoices());
      assertArrayEquals(new Object[] {"East", "West"}, updated.getValues());
   }

   @ParameterizedTest
   @CsvSource({
      "list, true",
      "checkboxes, false",
      "radio, false",
      "combobox, false",
   })
   void editVariableAppliesExplicitDisplayStyle(String style, boolean expectMultiple)
      throws Exception
   {
      // Mirrors VariableAssemblyDialogService.convertModelToAssetVariable: multipleSelection
      // is only ever derived from displayStyle == LIST, never from CHECKBOXES -- even though
      // checkboxes are inherently multi-valued in the UI, isMultipleSelection() itself is not
      // where that is expressed. Matching that exactly keeps this tool's output identical to
      // what a human produces through the Composer's own Variable dialog.
      Worksheet ws = new Worksheet();
      variableAssembly(ws, "region", XSchema.STRING);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.editVariable("region", null, null, null,
            embeddedChoices(List.of("East", "West"), null, style)));

      AssetVariable updated = ((DefaultVariableAssembly) ws.getAssembly("region")).getVariable();
      assertEquals(expectMultiple, updated.isMultipleSelection());
   }

   @Test
   void editVariableTogglingDisplayStyleAloneUpdatesExistingPicker() throws Exception {
      // An explicit displayStyle must take effect even when neither 'values' nor 'table' is
      // resupplied this call -- e.g. switching an existing combobox to a checkbox list without
      // touching its value list.
      Worksheet ws = new Worksheet();
      DefaultVariableAssembly assembly = variableAssembly(ws, "region", XSchema.STRING);
      assembly.getVariable().setChoices(new Object[] {"East", "West"});
      assembly.getVariable().setValues(new Object[] {"East", "West"});
      assembly.getVariable().setDisplayStyle(UserVariable.COMBOBOX);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.editVariable("region", null, null, null,
            new WorksheetMutationSupport.VariableChoicesSpec(
               null, null, null, null, null, "checkboxes")));

      AssetVariable updated = ((DefaultVariableAssembly) ws.getAssembly("region")).getVariable();
      assertEquals(UserVariable.CHECKBOXES, updated.getDisplayStyle());
      assertArrayEquals(new Object[] {"East", "West"}, updated.getChoices(),
         "toggling style alone must not disturb the existing value list");
   }

   @Test
   void editVariableEmptyValuesClearsExistingChoices() throws Exception {
      Worksheet ws = new Worksheet();
      DefaultVariableAssembly assembly = variableAssembly(ws, "region", XSchema.STRING);
      assembly.getVariable().setChoices(new Object[] {"East", "West"});
      assembly.getVariable().setValues(new Object[] {"East", "West"});
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.editVariable("region", null, null, null, embeddedChoices(List.of(), null, null)));

      AssetVariable updated = ((DefaultVariableAssembly) ws.getAssembly("region")).getVariable();
      assertNull(updated.getChoices());
      assertNull(updated.getValues());
      assertEquals(UserVariable.NONE, updated.getDisplayStyle());
   }

   @Test
   void editVariableOmittingChoicesLeavesExistingPickerUnchanged() throws Exception {
      Worksheet ws = new Worksheet();
      DefaultVariableAssembly assembly = variableAssembly(ws, "region", XSchema.STRING);
      assembly.getVariable().setChoices(new Object[] {"East", "West"});
      assembly.getVariable().setValues(new Object[] {"East", "West"});
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.editVariable("region", null, "New Label", null, null));

      AssetVariable updated = ((DefaultVariableAssembly) ws.getAssembly("region")).getVariable();
      assertArrayEquals(new Object[] {"East", "West"}, updated.getChoices());
      assertArrayEquals(new Object[] {"East", "West"}, updated.getValues());
   }

   @Test
   void editVariableRejectsMismatchedLabelsLength() throws Exception {
      Worksheet ws = new Worksheet();
      variableAssembly(ws, "region", XSchema.STRING);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.editVariable("region", null, null, null,
               embeddedChoices(List.of("East", "West"), List.of("Only one"), null))));
      assertTrue(ex.getMessage().contains("labels"));
   }

   @Test
   void editVariableRejectsInvalidChoicesWithoutApplyingLabelTypeOrDefaultValue()
      throws Exception
   {
      // Reviewer-caught regression: editVariable applied label/type/defaultValue directly to
      // the LIVE AssetVariable before validating choices, so a rejected call (here: mismatched
      // labels/values lengths) still left those earlier field changes committed on the live
      // worksheet even though the whole call reported failure. editVariable now edits a scratch
      // copy and only publishes it once every field -- including choices -- has validated.
      Worksheet ws = new Worksheet();
      DefaultVariableAssembly assembly = variableAssembly(ws, "region", XSchema.STRING);
      assembly.getVariable().setAlias("Original Label");
      // setTypeNode (inside variableAssembly's setup) auto-creates a placeholder value node
      // whenever none exists yet, so the baseline here is that placeholder, not null -- capture
      // it by reference to prove the rejected call didn't replace it with a new one.
      Object originalValueNode = assembly.getVariable().getValueNode();
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      assertThrows(IllegalArgumentException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.editVariable("region", "integer", "New Label", "5",
               embeddedChoices(List.of("East", "West"), List.of("Only one"), null))));

      AssetVariable updated = ((DefaultVariableAssembly) ws.getAssembly("region")).getVariable();
      assertEquals("Original Label", updated.getAlias(),
         "a rejected call must not leave 'label' applied");
      assertEquals(XSchema.STRING, updated.getTypeNode().getType(),
         "a rejected call must not leave 'type' applied");
      assertSame(originalValueNode, updated.getValueNode(),
         "a rejected call must not leave 'defaultValue' applied");
   }

   @Test
   void editVariableRejectsUnparsableEmbeddedValue() throws Exception {
      Worksheet ws = new Worksheet();
      variableAssembly(ws, "topN", XSchema.INTEGER);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // Tool.getData("integer", "abc") silently returns null rather than throwing -- editVariable
      // must reject this itself instead of writing a null value whose label ("abc") and
      // underlying value end up silently out of sync.
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.editVariable("topN", null, null, null,
               embeddedChoices(List.of("5", "abc"), null, null))));
      assertTrue(ex.getMessage().contains("abc"), ex.getMessage());

      AssetVariable updated = ((DefaultVariableAssembly) ws.getAssembly("topN")).getVariable();
      assertNull(updated.getChoices(), "a rejected value must not leave a partial picker applied");
   }

   @Test
   void editVariableRejectsUnknownDisplayStyle() throws Exception {
      Worksheet ws = new Worksheet();
      variableAssembly(ws, "region", XSchema.STRING);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.editVariable("region", null, null, null,
               embeddedChoices(List.of("East", "West"), null, "dropdown"))));
      assertTrue(ex.getMessage().contains("dropdown"));
   }

   @Test
   void editVariableRejectsUnknownDisplayStyleWithoutMutatingExistingChoices() throws Exception {
      // applyOnRuntime mutates the live worksheet with no rollback on a thrown exception, so a
      // validation failure discovered only after values/table were already applied would leave
      // the variable half-updated. displayStyle must be parsed before any mutation.
      Worksheet ws = new Worksheet();
      DefaultVariableAssembly assembly = variableAssembly(ws, "region", XSchema.STRING);
      assembly.getVariable().setChoices(new Object[] {"East", "West"});
      assembly.getVariable().setValues(new Object[] {"East", "West"});
      assembly.getVariable().setDisplayStyle(UserVariable.COMBOBOX);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      assertThrows(IllegalArgumentException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.editVariable("region", null, null, null,
               embeddedChoices(List.of("North", "South"), null, "dropdown"))));

      AssetVariable updated = ((DefaultVariableAssembly) ws.getAssembly("region")).getVariable();
      assertArrayEquals(new Object[] {"East", "West"}, updated.getChoices(),
         "a rejected displayStyle must not leave the new (unvalidated) values committed");
      assertArrayEquals(new Object[] {"East", "West"}, updated.getValues());
      assertEquals(UserVariable.COMBOBOX, updated.getDisplayStyle());
   }

   @Test
   void editVariableRejectsMismatchedLabelsLengthWithoutMutatingExistingQuerySource()
      throws Exception
   {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.tableWithColumns(ws, "Regions", "RegionName", "RegionId");
      ws.addAssembly(t);
      DefaultVariableAssembly assembly = variableAssembly(ws, "region", XSchema.STRING);
      assembly.getVariable().setTableName("Regions");
      assembly.getVariable().setLabelAttribute(t.getColumnSelection().getAttribute("RegionName"));
      assembly.getVariable().setValueAttribute(t.getColumnSelection().getAttribute("RegionId"));
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      assertThrows(IllegalArgumentException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.editVariable("region", null, null, null,
               embeddedChoices(List.of("East", "West"), List.of("Only one"), null))));

      AssetVariable updated = ((DefaultVariableAssembly) ws.getAssembly("region")).getVariable();
      assertEquals("Regions", updated.getTableName(),
         "a rejected labels/values mismatch must not clear the existing query source");
      assertNotNull(updated.getLabelAttribute());
      assertNotNull(updated.getValueAttribute());
   }

   @Test
   void editVariableRejectsBothValuesAndTable() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "Regions", "RegionName", "RegionId"));
      variableAssembly(ws, "region", XSchema.STRING);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.editVariable("region", null, null, null,
               new WorksheetMutationSupport.VariableChoicesSpec(
                  List.of("East", "West"), null, "Regions", "RegionName", "RegionId", null))));
      assertTrue(ex.getMessage().contains("mutually exclusive"));
   }

   @Test
   void editVariableSwitchesToQuerySource() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t =
         TestWorksheets.tableWithColumns(ws, "Regions", "RegionName", "RegionId");
      ws.addAssembly(t);
      variableAssembly(ws, "region", XSchema.STRING);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.editVariable("region", null, null, null,
            queryChoices("Regions", "RegionName", "RegionId", "combobox")));

      AssetVariable updated = ((DefaultVariableAssembly) ws.getAssembly("region")).getVariable();
      assertEquals("Regions", updated.getTableName());
      assertNotNull(updated.getLabelAttribute());
      assertEquals("RegionName", updated.getLabelAttribute().getAttribute());
      assertNotNull(updated.getValueAttribute());
      assertEquals("RegionId", updated.getValueAttribute().getAttribute());
      assertEquals(UserVariable.COMBOBOX, updated.getDisplayStyle());
   }

   @Test
   void editVariableSwitchingToQuerySourceClearsEmbeddedList() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "Regions", "RegionName", "RegionId"));
      DefaultVariableAssembly assembly = variableAssembly(ws, "region", XSchema.STRING);
      assembly.getVariable().setChoices(new Object[] {"East", "West"});
      assembly.getVariable().setValues(new Object[] {"East", "West"});
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.editVariable("region", null, null, null,
            queryChoices("Regions", "RegionName", "RegionId", null)));

      AssetVariable updated = ((DefaultVariableAssembly) ws.getAssembly("region")).getVariable();
      assertNull(updated.getChoices(),
         "switching to query mode must clear the leftover embedded list");
      assertNull(updated.getValues());
   }

   @Test
   void editVariableSwitchingToEmbeddedClearsQuerySource() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.tableWithColumns(ws, "Regions", "RegionName", "RegionId");
      ws.addAssembly(t);
      DefaultVariableAssembly assembly = variableAssembly(ws, "region", XSchema.STRING);
      assembly.getVariable().setTableName("Regions");
      assembly.getVariable().setLabelAttribute(t.getColumnSelection().getAttribute("RegionName"));
      assembly.getVariable().setValueAttribute(t.getColumnSelection().getAttribute("RegionId"));
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.editVariable("region", null, null, null,
            embeddedChoices(List.of("East", "West"), null, null)));

      AssetVariable updated = ((DefaultVariableAssembly) ws.getAssembly("region")).getVariable();
      assertNull(updated.getTableName(),
         "switching to embedded mode must clear the leftover query source");
      assertNull(updated.getLabelAttribute());
      assertNull(updated.getValueAttribute());
   }

   @Test
   void editVariableRejectsQueryModeMissingValueColumn() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "Regions", "RegionName", "RegionId"));
      variableAssembly(ws, "region", XSchema.STRING);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.editVariable("region", null, null, null,
               queryChoices("Regions", "RegionName", null, null))));
      assertTrue(ex.getMessage().contains("valueColumn"));
   }

   @Test
   void editVariableRejectsQueryModeUnknownTable() throws Exception {
      Worksheet ws = new Worksheet();
      variableAssembly(ws, "region", XSchema.STRING);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.editVariable("region", null, null, null,
               queryChoices("NoSuchTable", "RegionName", "RegionId", null))));
      assertTrue(ex.getMessage().contains("NoSuchTable"));
   }

   @Test
   void editVariableRejectsCircularQuerySourceViaRankingCondition() throws Exception {
      // Confirmed live: mirroring a table whose own ranking condition reads $(TopN), then
      // pointing $(TopN)'s own picker at that mirror, silently built a circular dependency --
      // computing the picker's values would require the variable's value first.
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly allSales = TestWorksheets.tableWithColumns(ws, "AllSales", "Total");
      ws.addAssembly(allSales);
      WorksheetMutationSupport.setRanking(allSales,
         new WorksheetMutationSupport.RankingSpec("Total", "$(TopN)", "TOP_N", false));
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "AllSalesMirror", allSales);
      ws.addAssembly(mirror);
      variableAssembly(ws, "TopN", XSchema.INTEGER);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.editVariable("TopN", null, null, null,
               queryChoices("AllSalesMirror", "Total", "Total", null))));
      assertTrue(ex.getMessage().contains("circular"), ex.getMessage());
      assertTrue(ex.getMessage().contains("AllSales"), ex.getMessage());
      assertTrue(ex.getMessage().contains("TopN"), ex.getMessage());

      AssetVariable updated = ((DefaultVariableAssembly) ws.getAssembly("TopN")).getVariable();
      assertNull(updated.getTableName(), "a rejected circular source must not be applied");
   }

   @Test
   void editVariableRejectsCircularQuerySourceThroughTransitiveMirrorChain() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly allSales = TestWorksheets.tableWithColumns(ws, "AllSales", "Total");
      ws.addAssembly(allSales);
      WorksheetMutationSupport.setRanking(allSales,
         new WorksheetMutationSupport.RankingSpec("Total", "$(TopN)", "TOP_N", false));
      MirrorTableAssembly mirror1 = new MirrorTableAssembly(ws, "M1", allSales);
      ws.addAssembly(mirror1);
      MirrorTableAssembly mirror2 = new MirrorTableAssembly(ws, "M2", mirror1);
      ws.addAssembly(mirror2);
      variableAssembly(ws, "TopN", XSchema.INTEGER);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // The cycle is two mirror-hops away from M2 -- proves the dependency walk is transitive,
      // not just a check of the immediately-named table.
      assertThrows(IllegalArgumentException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.editVariable("TopN", null, null, null,
               queryChoices("M2", "Total", "Total", null))));
   }

   @Test
   void editVariableRejectsCircularQuerySourceViaFilterCondition() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly allSales = TestWorksheets.tableWithColumns(ws, "AllSales", "Region");
      ws.addAssembly(allSales);
      WorksheetMutationSupport.addFilter(allSales, "Region", "=", "$(Region)");
      variableAssembly(ws, "Region", XSchema.STRING);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.editVariable("Region", null, null, null,
               queryChoices("AllSales", "Region", "Region", null))));
      assertTrue(ex.getMessage().contains("circular"), ex.getMessage());
   }

   @Test
   void editVariableAllowsQuerySourceFromIndependentCopy() throws Exception {
      // Mirrors the live scenario's actual fix: an independent copy (not a mirror) with no
      // conditions of its own has no dependency on the filtered/ranked table at all.
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly allSales = TestWorksheets.tableWithColumns(ws, "AllSales", "Total");
      ws.addAssembly(allSales);
      WorksheetMutationSupport.setRanking(allSales,
         new WorksheetMutationSupport.RankingSpec("Total", "$(TopN)", "TOP_N", false));
      EmbeddedTableAssembly copy = TestWorksheets.tableWithColumns(ws, "AllSalesValues", "Total");
      ws.addAssembly(copy);
      variableAssembly(ws, "TopN", XSchema.INTEGER);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.editVariable("TopN", null, null, null,
            queryChoices("AllSalesValues", "Total", "Total", null)));

      AssetVariable updated = ((DefaultVariableAssembly) ws.getAssembly("TopN")).getVariable();
      assertEquals("AllSalesValues", updated.getTableName());
   }

   @Test
   void editVariableAllowsQuerySourceReferencingADifferentVariable() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly allSales = TestWorksheets.tableWithColumns(ws, "AllSales", "Total");
      ws.addAssembly(allSales);
      WorksheetMutationSupport.setRanking(allSales,
         new WorksheetMutationSupport.RankingSpec("Total", "$(OtherVar)", "TOP_N", false));
      variableAssembly(ws, "TopN", XSchema.INTEGER);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.editVariable("TopN", null, null, null,
            queryChoices("AllSales", "Total", "Total", null)));

      AssetVariable updated = ((DefaultVariableAssembly) ws.getAssembly("TopN")).getVariable();
      assertEquals("AllSales", updated.getTableName());
   }

   // =========================================================================
   // Expression column test
   // =========================================================================

   @Test
   void addExpressionColumnAddsRef() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent,
         ed -> ed.addExpressionColumn("T", "computed", "field['a'] * 2", "integer", false));

      ColumnSelection cs = t.getColumnSelection(false);
      DataRef ref = cs.getAttribute("computed");
      assertNotNull(ref, "expression column 'computed' should be present");
   }

   @Test
   void addExpressionColumnRewritesDateSubtraction() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t =
         TestWorksheets.tableWithColumns(ws, "T", "start_date", "end_date", "amount");
      ColumnSelection cs = t.getColumnSelection(false);
      ((ColumnRef) cs.getAttribute("start_date")).setDataType(XSchema.TIME_INSTANT);
      ((ColumnRef) cs.getAttribute("end_date")).setDataType(XSchema.TIME_INSTANT);
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // Plain date subtraction silently evaluates to null in the Rhino engine —
      // the mutator must rewrite it to the .getTime() form.
      svc.apply("TOK", agent, ed -> ed.addExpressionColumn(
         "T", "days", "(field['end_date'] - field['start_date']) / 86400000",
         "double", false));

      ColumnRef col = (ColumnRef) t.getColumnSelection(false).getAttribute("days");
      assertNotNull(col);
      String expr = ((ExpressionRef) col.getDataRef()).getExpression();
      assertEquals("((field['end_date'].getTime() - field['start_date'].getTime())) / 86400000",
                   expr);

      // Subtraction of non-date columns must be left untouched.
      svc.apply("TOK", agent, ed -> ed.addExpressionColumn(
         "T", "diff", "field['amount'] - field['amount']", "double", false));
      ColumnRef col2 = (ColumnRef) t.getColumnSelection(false).getAttribute("diff");
      String expr2 = ((ExpressionRef) col2.getDataRef()).getExpression();
      assertEquals("field['amount'] - field['amount']", expr2);

      // SQL-mode expressions must be left untouched.
      svc.apply("TOK", agent, ed -> ed.addExpressionColumn(
         "T", "sql_days", "field['end_date'] - field['start_date']", "double", true));
      ColumnRef col3 = (ColumnRef) t.getColumnSelection(false).getAttribute("sql_days");
      String expr3 = ((ExpressionRef) col3.getDataRef()).getExpression();
      assertEquals("field['end_date'] - field['start_date']", expr3);
   }

   @Test
   void addExpressionColumnInfersNumericTypeForPureArithmeticExpression() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ColumnSelection cs = t.getColumnSelection(false);
      ((ColumnRef) cs.getAttribute("a")).setDataType(XSchema.INTEGER);
      ((ColumnRef) cs.getAttribute("b")).setDataType(XSchema.INTEGER);
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // No "type" argument -- a purely-arithmetic expression over numeric columns must
      // infer numeric instead of silently falling through to the untyped "string"
      // default.
      svc.apply("TOK", agent,
         ed -> ed.addExpressionColumn("T", "computed", "field['a'] * field['b']", null, false));

      ColumnRef col = (ColumnRef) t.getColumnSelection(false).getAttribute("computed");
      assertNotNull(col);
      assertEquals(XSchema.DOUBLE, col.getDataType());
   }

   @Test
   void addExpressionColumnInfersNumericTypeForThreeFactorExpression() throws Exception {
      // The three-factor, parenthesized-subtraction shape from the bug doc's G9 case --
      // must infer the same as the two-factor case above.
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b", "c");
      ColumnSelection cs = t.getColumnSelection(false);
      ((ColumnRef) cs.getAttribute("a")).setDataType(XSchema.DOUBLE);
      ((ColumnRef) cs.getAttribute("b")).setDataType(XSchema.DOUBLE);
      ((ColumnRef) cs.getAttribute("c")).setDataType(XSchema.DOUBLE);
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addExpressionColumn(
         "T", "computed", "field['a'] * field['b'] * (1 - field['c'])", null, false));

      ColumnRef col = (ColumnRef) t.getColumnSelection(false).getAttribute("computed");
      assertNotNull(col);
      assertEquals(XSchema.DOUBLE, col.getDataType());
   }

   @Test
   void addExpressionColumnLeavesStringDefaultForNonNumericExpression() throws Exception {
      Worksheet ws = new Worksheet();
      // "a" and "b" are left at their default (untyped -> string) type.
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // Referenced fields are not numeric -- must not guess; the existing "string"
      // default is preserved.
      svc.apply("TOK", agent,
         ed -> ed.addExpressionColumn("T", "computed", "field['a'] + field['b']", null, false));

      ColumnRef col = (ColumnRef) t.getColumnSelection(false).getAttribute("computed");
      assertNotNull(col);
      assertEquals(XSchema.STRING, col.getDataType());
   }

   @Test
   void addExpressionColumnHonorsExplicitTypeOverInference() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ColumnSelection cs = t.getColumnSelection(false);
      ((ColumnRef) cs.getAttribute("a")).setDataType(XSchema.INTEGER);
      ((ColumnRef) cs.getAttribute("b")).setDataType(XSchema.INTEGER);
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // Even though the expression would infer as numeric, an explicit "type" argument
      // must still win over inference (no regression on the existing explicit-type path).
      svc.apply("TOK", agent, ed ->
         ed.addExpressionColumn("T", "computed", "field['a'] * field['b']", "integer", false));

      ColumnRef col = (ColumnRef) t.getColumnSelection(false).getAttribute("computed");
      assertNotNull(col);
      assertEquals(XSchema.INTEGER, col.getDataType());
   }

   // =========================================================================
   // Duplicate-name rejection tests — add_column / add_expression_column
   // =========================================================================

   @Test
   void addColumnRejectsDuplicateName() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // Adding anyway used to fall back to AssetUtil.findAlias, which silently
      // renamed the new column instead of rejecting the collision.
      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed -> ed.addColumn("T", "a", null)));
      assertTrue(ex.getMessage().contains("already exists"));

      int count = t.getColumnSelection(false).getAttributeCount();
      assertEquals(2, count, "the duplicate column must not have been added");
   }

   @Test
   void addColumnRejectsNameCollidingWithAlias() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.renameColumn("T", "a", "x"));

      // Collides with the ALIAS of an existing column, not its attribute name.
      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed -> ed.addColumn("T", "x", null)));
      assertTrue(ex.getMessage().contains("already exists"));
   }

   @Test
   void addExpressionColumnRejectsDuplicateName() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent,
         ed -> ed.addExpressionColumn("T", "computed", "field['a'] * 2", "integer", false));

      // Same name twice: a second ColumnRef sharing the identity would make later
      // lookups by name (set_conditions, set_sort, edit_expression, ...) ambiguous.
      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent,
            ed -> ed.addExpressionColumn("T", "computed", "field['a'] * 3", "integer", false)));
      assertTrue(ex.getMessage().contains("already exists"));
      assertTrue(ex.getMessage().contains("edit_expression"),
         "the error should point at edit_expression as the intended operation");

      // Colliding with an existing RAW column must be rejected too.
      PairingException ex2 = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent,
            ed -> ed.addExpressionColumn("T", "a", "field['a'] * 2", "integer", false)));
      assertTrue(ex2.getMessage().contains("already exists"));
   }

   // =========================================================================
   // Sort test
   // =========================================================================

   @Test
   void setSortSetsDirectionOnColumn() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.setSort("T", "a", "DESC"));

      SortInfo si = t.getSortInfo();
      assertNotNull(si);
      assertEquals(1, si.getSortCount());
      assertEquals(XConstants.SORT_DESC, si.getSort(0).getOrder());
   }

   @Test
   void setSortReplacesExistingSort() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> {
         ed.setSort("T", "a", "ASC");
         ed.setSort("T", "a", "DESC");
      });

      SortInfo si = t.getSortInfo();
      assertEquals(1, si.getSortCount(), "should have exactly one sort for column a");
      assertEquals(XConstants.SORT_DESC, si.getSort(0).getOrder());
   }

   // =========================================================================
   // Join tests
   // =========================================================================

   @Test
   void addJoinAddsAssembly() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly left  = TestWorksheets.tableWithColumns(ws, "L", "id", "name");
      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "R", "id", "value");
      ws.addAssembly(left);
      ws.addAssembly(right);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent,
         ed -> ed.addJoin("J", "L", "id", "R", "id", "INNER", null, null));

      Assembly a = ws.getAssembly("J");
      assertNotNull(a, "join assembly 'J' should be in the worksheet");
      assertInstanceOf(RelationalJoinTableAssembly.class, a);
   }

   @Test
   void removeJoinRemovesAssembly() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly left  = TestWorksheets.tableWithColumns(ws, "L", "id");
      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "R", "id");
      ws.addAssembly(left);
      ws.addAssembly(right);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> {
         ed.addJoin("J", "L", "id", "R", "id", "LEFT", null, null);
         ed.removeJoin("J");
      });

      assertNull(ws.getAssembly("J"), "join assembly 'J' should have been removed");
   }

   // =========================================================================
   // Column-dependency guard tests (Redmine #75968)
   //
   // The Composer UI has always refused to hide/remove/rename a column a dependent
   // join still keys on (SetColumnVisibleService / DeleteColumnsService / RenameColumnService,
   // via WorksheetControllerService#allowsDeletion). The wiz plugin's Editor skipped that
   // check entirely, letting an agent silently break a join. These tests lock in the fix.
   // =========================================================================

   @Test
   void removeColumnRefusesAJoinKeyStillUsedByADependentJoin() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly left  = TestWorksheets.tableWithColumns(ws, "L", "id", "name");
      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "R", "id", "value");
      ws.addAssembly(left);
      ws.addAssembly(right);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent,
         ed -> ed.addJoin("J", "L", "id", "R", "id", "INNER", null, null));

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.removeColumn("L", "id")));

      assertTrue(ex.getMessage().contains("id"), ex.getMessage());
      assertNotNull(left.getColumnSelection(false).getAttribute("id"),
         "the join key must survive the refusal");
   }

   @Test
   void removeColumnStillWorksOnAColumnNotUsedByTheJoin() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly left  = TestWorksheets.tableWithColumns(ws, "L", "id", "name");
      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "R", "id", "value");
      ws.addAssembly(left);
      ws.addAssembly(right);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> {
         ed.addJoin("J", "L", "id", "R", "id", "INNER", null, null);
         ed.removeColumn("L", "name");
      });

      assertNull(left.getColumnSelection(false).getAttribute("name"),
         "a column the join doesn't key on must still be removable");
   }

   @Test
   void setColumnVisibilityRefusesHidingAJoinKeyStillUsedByADependentJoin() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly left  = TestWorksheets.tableWithColumns(ws, "L", "id", "name");
      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "R", "id", "value");
      ws.addAssembly(left);
      ws.addAssembly(right);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent,
         ed -> ed.addJoin("J", "L", "id", "R", "id", "INNER", null, null));

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.setColumnVisibility("L", "id", false)));

      assertTrue(ex.getMessage().contains("id"), ex.getMessage());
      ColumnRef idRef = (ColumnRef) left.getColumnSelection(false).getAttribute("id");
      assertTrue(idRef.isVisible(), "the join key must remain visible after the refusal");
   }

   @Test
   void renameColumnRefusesRenamingAJoinKeyStillUsedByADependentJoin() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly left  = TestWorksheets.tableWithColumns(ws, "L", "id", "name");
      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "R", "id", "value");
      ws.addAssembly(left);
      ws.addAssembly(right);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent,
         ed -> ed.addJoin("J", "L", "id", "R", "id", "INNER", null, null));

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.renameColumn("L", "id", "renamedId")));

      assertTrue(ex.getMessage().contains("id"), ex.getMessage());
      ColumnRef idRef = (ColumnRef) left.getColumnSelection(false).getAttribute("id");
      assertNull(idRef.getAlias(), "the join key must not be renamed by the refused edit");
   }

   // =========================================================================
   // Edit-in-place tests
   // =========================================================================

   @Test
   void editConditionReplacesExistingFilter() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> {
         ed.addFilter("T", "a", "=", "old");
         ed.editCondition("T", "a", "=", "new");
      });

      // After edit_condition, exactly one ConditionItem with value "new"
      assertNotNull(t.getPreConditionList());
      assertEquals(1, t.getPreConditionList().getConditionList().getSize());
   }

   @Test
   void editConditionRejectsEmbeddedTable() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.editCondition("T", "a", "=", "new")));

      assertTrue(t.getPreConditionList() == null || t.getPreConditionList().isEmpty());
   }

   @Test
   void setConditionsRejectsSnapshotEmbeddedTable() throws Exception {
      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly t = TestWorksheets.snapshotTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.setConditions("T", List.of())));

      assertTrue(t.getPreConditionList() == null || t.getPreConditionList().isEmpty());
   }

   @Test
   void setPostConditionsRejectsEmbeddedTable() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.setPostConditions("T", List.of())));

      assertTrue(t.getPostConditionList() == null || t.getPostConditionList().isEmpty());
   }

   @Test
   void editExpressionUpdatesExistingColumn() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.addExpressionColumn("T", "calc", "field['a'] * 1", "integer", false));

      svc.apply("TOK", agent, ed ->
         ed.editExpression("T", "calc", "field['a'] * 2", "integer", false));

      ColumnSelection cs = t.getColumnSelection(false);
      DataRef ref = cs.getAttribute("calc");
      assertNotNull(ref, "'calc' column should still exist");
      // Check the expression was updated
      assertInstanceOf(ColumnRef.class, ref);
      ColumnRef cr = (ColumnRef) ref;
      assertInstanceOf(ExpressionRef.class, cr.getDataRef());
      assertEquals("field['a'] * 2", ((ExpressionRef) cr.getDataRef()).getExpression());
   }

   @Test
   void editExpressionAddsWhenNotFound() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.editExpression("T", "newcalc", "field['a'] + 1", "integer", false));

      assertNotNull(t.getColumnSelection(false).getAttribute("newcalc"),
                    "'newcalc' should be added as a new expression column");
   }

   @Test
   void editExpressionInfersNumericTypeWhenTypeOmitted() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ColumnSelection cs = t.getColumnSelection(false);
      ((ColumnRef) cs.getAttribute("a")).setDataType(XSchema.INTEGER);
      ((ColumnRef) cs.getAttribute("b")).setDataType(XSchema.INTEGER);
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // Original add omits "type" against a non-numeric expression, landing on the
      // untyped "string" default -- same gap as add_expression_column.
      svc.apply("TOK", agent,
         ed -> ed.addExpressionColumn("T", "calc", "'literal'", null, false));

      // A later edit_expression, also omitting "type", switches to a purely-arithmetic
      // numeric expression -- it must infer numeric instead of keeping the stale string
      // default.
      svc.apply("TOK", agent,
         ed -> ed.editExpression("T", "calc", "field['a'] * field['b']", null, false));

      ColumnRef col = (ColumnRef) t.getColumnSelection(false).getAttribute("calc");
      assertNotNull(col);
      assertEquals(XSchema.DOUBLE, col.getDataType());
   }

   @Test
   void editExpressionPreservesExplicitPriorTypeWhenTypeOmitted() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ColumnSelection cs = t.getColumnSelection(false);
      ((ColumnRef) cs.getAttribute("a")).setDataType(XSchema.INTEGER);
      ((ColumnRef) cs.getAttribute("b")).setDataType(XSchema.INTEGER);
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.addExpressionColumn("T", "calc", "field['a'] * 1", "integer", false));

      // Omitting "type" on edit_expression must leave a real, previously-set explicit
      // type alone -- per the "null = leave unchanged" contract -- even though the new
      // expression would otherwise infer as numeric (double).
      svc.apply("TOK", agent, ed ->
         ed.editExpression("T", "calc", "field['a'] * field['b']", null, false));

      ColumnRef col = (ColumnRef) t.getColumnSelection(false).getAttribute("calc");
      assertNotNull(col);
      assertEquals(XSchema.INTEGER, col.getDataType());
   }

   @Test
   void editExpressionPreservesExplicitStringPriorTypeWhenTypeOmitted() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ColumnSelection cs = t.getColumnSelection(false);
      ((ColumnRef) cs.getAttribute("a")).setDataType(XSchema.INTEGER);
      ((ColumnRef) cs.getAttribute("b")).setDataType(XSchema.INTEGER);
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.addExpressionColumn("T", "calc", "field['a'] * 1", "string", false));

      // An explicit "string" prior type happens to equal the untyped fallback's
      // *derived* getDataType() value, so a guard that compares against that value
      // (instead of checking whether a type was ever explicitly set) would wrongly
      // re-infer here. Omitting "type" on edit_expression must still leave a real,
      // previously-set explicit "string" type alone, even though the new expression
      // would otherwise infer as numeric (double).
      svc.apply("TOK", agent, ed ->
         ed.editExpression("T", "calc", "field['a'] * field['b']", null, false));

      ColumnRef col = (ColumnRef) t.getColumnSelection(false).getAttribute("calc");
      assertNotNull(col);
      assertEquals(XSchema.STRING, col.getDataType());
   }

   @Test
   void editJoinUpdatesKeyColumns() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly left  = TestWorksheets.tableWithColumns(ws, "L", "id", "altId");
      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "R", "id", "altId");
      ws.addAssembly(left);
      ws.addAssembly(right);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addJoin("J", "L", "id", "R", "id", "INNER", null, null));
      svc.apply("TOK", agent, ed -> ed.editJoin("J", "altId", "altId", "LEFT", null, null));

      Assembly a = ws.getAssembly("J");
      assertNotNull(a);
      assertInstanceOf(RelationalJoinTableAssembly.class, a);
      // Verify a LEFT join was set (the operator count stayed 1)
      RelationalJoinTableAssembly join = (RelationalJoinTableAssembly) a;
      @SuppressWarnings("unchecked")
      java.util.Enumeration<TableAssemblyOperator> iter =
         (java.util.Enumeration<TableAssemblyOperator>) join.getOperators();
      assertTrue(iter.hasMoreElements());
      TableAssemblyOperator top = iter.nextElement();
      assertEquals(1, top.getOperatorCount());
      assertEquals(TableAssemblyOperator.LEFT_JOIN, top.getOperator(0).getOperation());
      assertEquals("altId", top.getOperator(0).getLeftAttribute().getAttribute());
      assertEquals("altId", top.getOperator(0).getRightAttribute().getAttribute());
   }

   @Test
   void editJoinThrowsWhenAssemblyNotFound() throws Exception {
      Worksheet ws = new Worksheet();
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.editJoin("NOPE", "a", "b", "INNER", null, null)));
   }

   // =========================================================================
   // Add table test
   // =========================================================================

   @Test
   void addTableAddsAssembly() throws Exception {
      Worksheet ws = new Worksheet();
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addTable("NewTable", "col1", "col2"));

      Assembly a = ws.getAssembly("NewTable");
      assertNotNull(a, "assembly 'NewTable' should be in the worksheet");
      assertInstanceOf(EmbeddedTableAssembly.class, a);

      EmbeddedTableAssembly t = (EmbeddedTableAssembly) a;
      assertNotNull(t.getColumnSelection(false).getAttribute("col1"));
      assertNotNull(t.getColumnSelection(false).getAttribute("col2"));
   }

   // =========================================================================
   // Permission gate tests — addCrossJoin / addExpressionColumn / editExpression
   // =========================================================================
   //
   // Each gated op calls requirePermission(ResourceType), which is backed by
   // securityEngine.checkPermission(agent, <type>, "*", ResourceAction.ACCESS). These tests
   // pin down two things per op: (1) denial throws SecurityException *and* the underlying
   // mutation never happens, and (2) the EXACT ResourceType checked is the one documented for
   // that op — guarding against a copy-paste mix-up between CROSS_JOIN and
   // WORKSHEET_EXPRESSION_COLUMN across the three call sites added together.

   /** Builds a securityEngine mock granting every permission except the one given. */
   private SecurityEngine securityEngineDenying(Principal agent, ResourceType deniedType)
      throws SecurityException
   {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      when(securityEngine.checkPermission(
         eq(agent), eq(deniedType), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);
      return securityEngine;
   }

   /** Builds a securityEngine mock granting every permission — used for ResourceType capture. */
   private SecurityEngine securityEngineGrantingAll() throws SecurityException {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      return securityEngine;
   }

   // --- addCrossJoin -------------------------------------------------------

   @Test
   void addCrossJoinDeniedThrowsAndDoesNotAddAssembly() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly left  = TestWorksheets.tableWithColumns(ws, "L", "id");
      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "R", "id");
      ws.addAssembly(left);
      ws.addAssembly(right);
      Principal agent = TestPrincipals.user("alice", "host-org");
      SecurityEngine securityEngine = securityEngineDenying(agent, ResourceType.CROSS_JOIN);
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK", securityEngine);

      assertThrows(SecurityException.class, () ->
         svc.apply("TOK", agent, ed -> ed.addCrossJoin("J", "L", "R")));

      assertNull(ws.getAssembly("J"),
         "cross join assembly must not be created when CROSS_JOIN permission is denied");
   }

   @Test
   void addCrossJoinChecksCrossJoinResourceTypeExactly() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly left  = TestWorksheets.tableWithColumns(ws, "L", "id");
      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "R", "id");
      ws.addAssembly(left);
      ws.addAssembly(right);
      Principal agent = TestPrincipals.user("alice", "host-org");
      SecurityEngine securityEngine = securityEngineGrantingAll();
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK", securityEngine);

      svc.apply("TOK", agent, ed -> ed.addCrossJoin("J", "L", "R"));

      ArgumentCaptor<ResourceType> typeCaptor = ArgumentCaptor.forClass(ResourceType.class);
      verify(securityEngine).checkPermission(
         eq(agent), typeCaptor.capture(), eq("*"), eq(ResourceAction.ACCESS));
      assertEquals(ResourceType.CROSS_JOIN, typeCaptor.getValue(),
         "addCrossJoin must check ResourceType.CROSS_JOIN, not e.g. WORKSHEET_EXPRESSION_COLUMN");

      assertNotNull(ws.getAssembly("J"),
         "cross join assembly should be created when permission is granted");
   }

   // --- addExpressionColumn -------------------------------------------------

   @Test
   void addExpressionColumnDeniedThrowsAndDoesNotAddColumn() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      SecurityEngine securityEngine =
         securityEngineDenying(agent, ResourceType.WORKSHEET_EXPRESSION_COLUMN);
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK", securityEngine);

      assertThrows(SecurityException.class, () ->
         svc.apply("TOK", agent,
            ed -> ed.addExpressionColumn("T", "computed", "field['a'] * 2", "integer", false)));

      assertNull(t.getColumnSelection(false).getAttribute("computed"),
         "expression column must not be added when WORKSHEET_EXPRESSION_COLUMN permission is denied");
   }

   @Test
   void addExpressionColumnChecksExpressionColumnResourceTypeExactly() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      SecurityEngine securityEngine = securityEngineGrantingAll();
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK", securityEngine);

      svc.apply("TOK", agent,
         ed -> ed.addExpressionColumn("T", "computed", "field['a'] * 2", "integer", false));

      ArgumentCaptor<ResourceType> typeCaptor = ArgumentCaptor.forClass(ResourceType.class);
      verify(securityEngine).checkPermission(
         eq(agent), typeCaptor.capture(), eq("*"), eq(ResourceAction.ACCESS));
      assertEquals(ResourceType.WORKSHEET_EXPRESSION_COLUMN, typeCaptor.getValue(),
         "addExpressionColumn must check ResourceType.WORKSHEET_EXPRESSION_COLUMN, not e.g. CROSS_JOIN");

      assertNotNull(t.getColumnSelection(false).getAttribute("computed"),
         "expression column should be added when permission is granted");
   }

   // --- editExpression -------------------------------------------------------

   @Test
   void editExpressionDeniedThrowsAndDoesNotChangeColumn() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      // Seed the existing expression column directly via WorksheetMutationSupport, bypassing
      // the Editor entirely, so setup does not itself consult securityEngine.
      WorksheetMutationSupport.addExpressionColumn(t, "calc", "field['a'] * 1", "integer", false);
      Principal agent = TestPrincipals.user("alice", "host-org");
      SecurityEngine securityEngine =
         securityEngineDenying(agent, ResourceType.WORKSHEET_EXPRESSION_COLUMN);
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK", securityEngine);

      assertThrows(SecurityException.class, () ->
         svc.apply("TOK", agent,
            ed -> ed.editExpression("T", "calc", "field['a'] * 2", "integer", false)));

      ColumnRef col = (ColumnRef) t.getColumnSelection(false).getAttribute("calc");
      assertNotNull(col, "'calc' column should still be present");
      assertEquals("field['a'] * 1", ((ExpressionRef) col.getDataRef()).getExpression(),
         "expression must be unchanged when WORKSHEET_EXPRESSION_COLUMN permission is denied");
   }

   @Test
   void editExpressionChecksExpressionColumnResourceTypeExactly() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      WorksheetMutationSupport.addExpressionColumn(t, "calc", "field['a'] * 1", "integer", false);
      Principal agent = TestPrincipals.user("alice", "host-org");
      SecurityEngine securityEngine = securityEngineGrantingAll();
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK", securityEngine);

      svc.apply("TOK", agent,
         ed -> ed.editExpression("T", "calc", "field['a'] * 2", "integer", false));

      ArgumentCaptor<ResourceType> typeCaptor = ArgumentCaptor.forClass(ResourceType.class);
      verify(securityEngine).checkPermission(
         eq(agent), typeCaptor.capture(), eq("*"), eq(ResourceAction.ACCESS));
      assertEquals(ResourceType.WORKSHEET_EXPRESSION_COLUMN, typeCaptor.getValue(),
         "editExpression must check ResourceType.WORKSHEET_EXPRESSION_COLUMN, not e.g. CROSS_JOIN");

      ColumnRef col = (ColumnRef) t.getColumnSelection(false).getAttribute("calc");
      assertEquals("field['a'] * 2", ((ExpressionRef) col.getDataRef()).getExpression(),
         "expression should be updated when permission is granted");
   }

   // --- addJoin("CROSS", ...) delegation to the CROSS_JOIN gate --------------

   @Test
   void addJoinCrossTypeDeniedThrowsAndDoesNotAddAssembly() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly left  = TestWorksheets.tableWithColumns(ws, "L", "id");
      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "R", "id");
      ws.addAssembly(left);
      ws.addAssembly(right);
      Principal agent = TestPrincipals.user("alice", "host-org");
      SecurityEngine securityEngine = securityEngineDenying(agent, ResourceType.CROSS_JOIN);
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK", securityEngine);

      assertThrows(SecurityException.class, () ->
         svc.apply("TOK", agent,
            ed -> ed.addJoin("J", "L", "id", "R", "id", "CROSS", null, null)));

      assertNull(ws.getAssembly("J"),
         "addJoin(\"CROSS\",...) must go through the same CROSS_JOIN gate as addCrossJoin " +
         "and must not create the assembly when denied");
   }

   @Test
   void addJoinNonCrossTypeDoesNotRequireCrossJoinPermission() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly left  = TestWorksheets.tableWithColumns(ws, "L", "id");
      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "R", "id");
      ws.addAssembly(left);
      ws.addAssembly(right);
      Principal agent = TestPrincipals.user("alice", "host-org");
      // CROSS_JOIN denied — an INNER join must succeed anyway, proving the gate is scoped to
      // cross joins only.
      SecurityEngine securityEngine = securityEngineDenying(agent, ResourceType.CROSS_JOIN);
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK", securityEngine);

      svc.apply("TOK", agent, ed -> ed.addJoin("J", "L", "id", "R", "id", "INNER", null, null));

      assertNotNull(ws.getAssembly("J"),
         "INNER join must succeed even though CROSS_JOIN permission is denied");
      verify(securityEngine, never()).checkPermission(
         eq(agent), eq(ResourceType.CROSS_JOIN), anyString(), any(ResourceAction.class));
   }

   // --- editJoin("CROSS", ...) must clear the same CROSS_JOIN gate -----------

   @Test
   void editJoinToCrossTypeDeniedThrowsAndDoesNotChangeOperator() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly left  = TestWorksheets.tableWithColumns(ws, "L", "id");
      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "R", "id");
      ws.addAssembly(left);
      ws.addAssembly(right);
      Principal agent = TestPrincipals.user("alice", "host-org");
      // CROSS_JOIN denied. An INNER join is allowed (gate is scoped to cross joins),
      // so the attacker first creates one, then tries to rewrite it into a cross join
      // via editJoin — which must hit the same gate and be rejected.
      SecurityEngine securityEngine = securityEngineDenying(agent, ResourceType.CROSS_JOIN);
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK", securityEngine);

      svc.apply("TOK", agent, ed -> ed.addJoin("J", "L", "id", "R", "id", "INNER", null, null));

      assertThrows(SecurityException.class, () ->
         svc.apply("TOK", agent, ed -> ed.editJoin("J", "id", "id", "CROSS", null, null)));

      // The operator must remain the original INNER join — the mutation must not have
      // happened before the permission check threw.
      RelationalJoinTableAssembly join = (RelationalJoinTableAssembly) ws.getAssembly("J");
      assertNotNull(join, "join assembly should still exist");
      @SuppressWarnings("unchecked")
      java.util.Enumeration<TableAssemblyOperator> iter =
         (java.util.Enumeration<TableAssemblyOperator>) join.getOperators();
      assertTrue(iter.hasMoreElements());
      TableAssemblyOperator top = iter.nextElement();
      assertEquals(TableAssemblyOperator.INNER_JOIN, top.getOperator(0).getOperation(),
         "editJoin(\"CROSS\",...) must not downgrade the join to a cross join when " +
         "CROSS_JOIN permission is denied");
   }

   @Test
   void editJoinNonCrossTypeDoesNotRequireCrossJoinPermission() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly left  = TestWorksheets.tableWithColumns(ws, "L", "id");
      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "R", "id");
      ws.addAssembly(left);
      ws.addAssembly(right);
      Principal agent = TestPrincipals.user("alice", "host-org");
      // CROSS_JOIN denied — editing to a LEFT join must still succeed, proving the gate
      // is scoped to cross joins only.
      SecurityEngine securityEngine = securityEngineDenying(agent, ResourceType.CROSS_JOIN);
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK", securityEngine);

      svc.apply("TOK", agent, ed -> ed.addJoin("J", "L", "id", "R", "id", "INNER", null, null));
      svc.apply("TOK", agent, ed -> ed.editJoin("J", "id", "id", "LEFT", null, null));

      RelationalJoinTableAssembly join = (RelationalJoinTableAssembly) ws.getAssembly("J");
      @SuppressWarnings("unchecked")
      java.util.Enumeration<TableAssemblyOperator> iter =
         (java.util.Enumeration<TableAssemblyOperator>) join.getOperators();
      assertTrue(iter.hasMoreElements());
      TableAssemblyOperator top = iter.nextElement();
      assertEquals(TableAssemblyOperator.LEFT_JOIN, top.getOperator(0).getOperation(),
         "editing to a LEFT join must succeed even though CROSS_JOIN permission is denied");
      verify(securityEngine, never()).checkPermission(
         eq(agent), eq(ResourceType.CROSS_JOIN), anyString(), any(ResourceAction.class));
   }

   // =========================================================================
   // SQL query column-name sanitization
   // =========================================================================

   @Test
   void sanitizeSqlColumnNamesCleansMangledQualifiedName() {
      // Reproduces what QueryManagerService.getColumnSelection() actually returns for
      // an unaliased qualified column like "SELECT f.title FROM ..." (no AS clause):
      // the parser's alias-detection falls back to the fully quoted qualified
      // expression instead of null, so the raw attribute ends up as the literal
      // string `"f"."title"` — quote characters included.
      ColumnSelection cs = new ColumnSelection();
      ColumnRef mangled = new ColumnRef(new AttributeRef("\"f\".\"title\""));
      cs.addAttribute(mangled);

      WorksheetMutationSupport.sanitizeSqlColumnNames(cs);

      // Regression: an earlier version of this fix only set the display alias,
      // leaving getAttribute() (which a SELECT * expansion over a derived table
      // actually walks) still mangled — live-testing against a real per-group-
      // ranking escape-hatch query ("SELECT * FROM (SELECT f.title, ROW_NUMBER()...)
      // ranked") confirmed that a downstream wrap of such a query kept failing with
      // "table not found or produced no data" until the underlying attribute itself
      // (not just the alias) was replaced.
      assertEquals("title", mangled.getAttribute());
   }

   @Test
   void sanitizeSqlColumnNamesLeavesCleanNamesAlone() {
      ColumnSelection cs = new ColumnSelection();
      ColumnRef clean = new ColumnRef(new AttributeRef("revenue"));
      cs.addAttribute(clean);

      WorksheetMutationSupport.sanitizeSqlColumnNames(cs);

      assertEquals("revenue", clean.getAttribute(),
                   "a column name with no embedded quote must be left untouched");
   }

   @Test
   void sanitizeSqlSelectionAliasesClearsMangledAliasSoIndexOfColumnFallbackRuns() {
      // Regression for a second layer of the same bug: fixing ColumnSelection alone
      // (sanitizeSqlColumnNames) left the UniformSQL's OWN XSelection alias intact.
      // At query-execution time, PreAssetQuery/BoundQuery.getAttributeColumn resolves
      // each output column via XSelection.indexOfColumn(name, ...), which has a
      // fallback for exactly this case (an unaliased qualified column, matched by its
      // trailing identifier) — but that fallback loop skips any index where
      // getAlias(i) != null. Since the mangled alias is non-null, the fallback never
      // ran and the column was silently dropped from the executed result — worse than
      // the original crash, since nothing signaled the loss. Live-tested: a wrapped
      // "SELECT * FROM (SELECT f.title, ROW_NUMBER()...) ranked" query stopped
      // crashing after the ColumnSelection fix, but the resulting rows had `rn` only
      // — `title`/`rating` had vanished entirely.
      inetsoft.uql.jdbc.UniformSQL sql = new inetsoft.uql.jdbc.UniformSQL();
      inetsoft.uql.jdbc.JDBCSelection selection = new inetsoft.uql.jdbc.JDBCSelection();
      int idx = selection.addColumn("f.title");
      selection.setAlias(idx, "\"f\".\"title\"");
      sql.setSelection(selection);

      WorksheetMutationSupport.sanitizeSqlSelectionAliases(sql);

      assertNull(selection.getAlias(idx),
                 "mangled alias must be cleared so XSelection.indexOfColumn's qualified-suffix fallback runs");
   }

   @Test
   void sanitizeSqlSelectionAliasesSurvivesAPriorNegativeIndexOfColumnLookup() {
      // The actual root cause behind 4 straight failed live-test cycles on this bug:
      // XSelection.indexOfColumn memoizes results — INCLUDING misses (-1) — in a
      // Map<String, Integer> keyed only by the searched name (XSelection.java ~line
      // 760-807). QueryManagerService.getColumnSelection's own internal metadata
      // resolution calls indexOfColumn("title", ...) for this exact column BEFORE
      // this sanitizer ever runs — while the mangled alias is still in place, so the
      // qualified-suffix fallback is skipped and the miss gets cached permanently.
      // Clearing the alias afterward (what this method already did) was correct but
      // insufficient: XSelection.setAlias() never called indexmap.clear(), unlike
      // every sibling mutator (setColumn, addColumn, etc.) in the same class — so the
      // stale cached -1 kept being returned to every later indexOfColumn("title", ...)
      // call at actual query-execution time, regardless of the alias fix. This is
      // fixed at the source (XSelection.setAlias, both branches) rather than by
      // reaching into the selection's cache from here. This test reproduces the
      // production ordering exactly: populate the negative cache FIRST, sanitize
      // SECOND, and confirm the fallback is actually reachable afterward — the
      // previous test only checked the alias field, which stayed green throughout
      // all 4 failed live cycles and never would have caught this.
      inetsoft.uql.jdbc.UniformSQL sql = new inetsoft.uql.jdbc.UniformSQL();
      inetsoft.uql.jdbc.JDBCSelection selection = new inetsoft.uql.jdbc.JDBCSelection();
      int idx = selection.addColumn("f.title");
      selection.setAlias(idx, "\"f\".\"title\"");
      sql.setSelection(selection);

      assertEquals(-1, selection.indexOfColumn("title", false, true),
                   "sanity check: the mangled alias shadows the qualified-suffix fallback, as production observed");

      WorksheetMutationSupport.sanitizeSqlSelectionAliases(sql);

      assertEquals(idx, selection.indexOfColumn("title", false, true),
                   "clearing the alias must invalidate the memoized negative lookup, not just the alias field");
   }

   @Test
   void sanitizeSqlSelectionAliasesLeavesRealAliasesAlone() {
      inetsoft.uql.jdbc.UniformSQL sql = new inetsoft.uql.jdbc.UniformSQL();
      inetsoft.uql.jdbc.JDBCSelection selection = new inetsoft.uql.jdbc.JDBCSelection();
      int idx = selection.addColumn("ROW_NUMBER() OVER (ORDER BY x)");
      selection.setAlias(idx, "rn");
      sql.setSelection(selection);

      WorksheetMutationSupport.sanitizeSqlSelectionAliases(sql);

      assertEquals("rn", selection.getAlias(idx),
                   "a genuine explicit alias with no embedded quote must be left untouched");
   }

   @Test
   void realParserConfirmsDerivedTableSubqueryIsNestedUniformSQL() throws Exception {
      // Confirms, with the REAL grammar parser (SQLLexer/SQLParser via SQLProcessor — no JDBC
      // connection, no live server), that a derived-table subquery in the FROM clause is
      // represented as a NESTED UniformSQL, reachable via SelectTable.getName(). This is the
      // same structure UniformSQL itself already walks recursively elsewhere — see
      // UniformSQL.applyVariableTable(UniformSQL) (~line 242-250: "obj instanceof UniformSQL")
      // and UniformSQL.writeXML0 (~line 967-976: "issql = name instanceof UniformSQL").
      //
      // Note on scope: parsing the raw SQL TEXT (this test) needs no datasource and is fully
      // offline. But the mangled `"f"."title"`-style alias itself is NOT produced at this raw
      // parse stage — the real parser leaves such unaliased-qualified columns with alias=null
      // (verified: inner selection has column "f.title" / alias null here). The mangled string
      // is manufactured later, once a live datasource resolves real column metadata (via
      // JDBCUtil.fixUniformSQLInfo -> QueryManagerService.getColumnSelection), which cannot be
      // reproduced without a live DB connection. The next test picks up from the REAL parsed
      // structure and hand-injects that later-stage mangled alias to verify the fix mechanism.
      String sqlText =
         "SELECT * FROM (\n" +
         "  SELECT f.title, f.rating, ROW_NUMBER() OVER (PARTITION BY f.rating ORDER BY f.rental_rate DESC) AS rn\n" +
         "  FROM film f\n" +
         ") ranked WHERE rn <= 2";

      inetsoft.uql.jdbc.UniformSQL sql = new inetsoft.uql.jdbc.UniformSQL();
      sql.setParseSQL(true);

      synchronized(sql) {
         sql.setSQLString(sqlText, true);
         sql.wait(10_000);
      }

      assertEquals(inetsoft.uql.jdbc.UniformSQL.PARSE_SUCCESS, sql.getParseResult(),
                   "the real grammar parser should parse this fully offline");

      inetsoft.uql.jdbc.SelectTable[] outerTables = sql.getSelectTable();
      assertEquals(1, outerTables.length, "outer query should have exactly one FROM-clause table");
      assertInstanceOf(inetsoft.uql.jdbc.UniformSQL.class, outerTables[0].getName(),
                       "a derived-table subquery in the FROM clause is represented as a NESTED " +
                       "UniformSQL stored as SelectTable.getName() — confirms the nested-subquery " +
                       "hypothesis");

      inetsoft.uql.jdbc.UniformSQL inner = (inetsoft.uql.jdbc.UniformSQL) outerTables[0].getName();
      inetsoft.uql.path.XSelection innerSelection = inner.getSelection();
      boolean hasUnaliasedQualifiedTitle = false;

      for(int i = 0; i < innerSelection.getColumnCount(); i++) {
         if("f.title".equals(innerSelection.getColumn(i)) && innerSelection.getAlias(i) == null) {
            hasUnaliasedQualifiedTitle = true;
         }
      }

      assertTrue(hasUnaliasedQualifiedTitle,
                "the inner subquery's own selection should contain the unaliased 'f.title' column " +
                "that a live-datasource metadata pass later mangles into \"f\".\"title\"");
   }

   @Test
   void sanitizeSqlSelectionAliasesRecursesIntoNestedSubquerySelection() throws Exception {
      // Builds on the previous test's confirmed structure (real parser, no JDBC connection) and
      // hand-injects the mangled alias that a live-datasource metadata pass produces on the
      // INNER subquery's own selection (not reproducible offline — see previous test's note).
      // Verifies the fixed sanitizeSqlSelectionAliases recurses into every derived-table
      // subquery reachable via SelectTable.getName(), not just the outer sql.getSelection().
      String sqlText =
         "SELECT * FROM (\n" +
         "  SELECT f.title, f.rating, ROW_NUMBER() OVER (PARTITION BY f.rating ORDER BY f.rental_rate DESC) AS rn\n" +
         "  FROM film f\n" +
         ") ranked WHERE rn <= 2";

      inetsoft.uql.jdbc.UniformSQL sql = new inetsoft.uql.jdbc.UniformSQL();
      sql.setParseSQL(true);

      synchronized(sql) {
         sql.setSQLString(sqlText, true);
         sql.wait(10_000);
      }

      assertEquals(inetsoft.uql.jdbc.UniformSQL.PARSE_SUCCESS, sql.getParseResult());

      inetsoft.uql.jdbc.SelectTable[] outerTables = sql.getSelectTable();
      inetsoft.uql.jdbc.UniformSQL inner = (inetsoft.uql.jdbc.UniformSQL) outerTables[0].getName();
      inetsoft.uql.path.XSelection innerSelection = inner.getSelection();
      int titleIdx = -1;

      for(int i = 0; i < innerSelection.getColumnCount(); i++) {
         if("f.title".equals(innerSelection.getColumn(i))) {
            titleIdx = i;
         }
      }

      assertTrue(titleIdx >= 0, "the real parser should produce an 'f.title' column in the inner selection");

      // Hand-inject the later-stage mangled alias onto the REAL, parser-produced inner
      // selection column (see live-tested symptom documented on sanitizeSqlColumnNames above).
      innerSelection.setAlias(titleIdx, "\"f\".\"title\"");

      WorksheetMutationSupport.sanitizeSqlSelectionAliases(sql);

      assertNull(innerSelection.getAlias(titleIdx),
                "fix must recurse into the nested subquery's own selection and clear the mangled " +
                "alias there, not just on the outer sql.getSelection()");
   }

   // =========================================================================
   // Column type tests
   // =========================================================================

   @Test
   void changeColumnTypeRejectsPhysicalBoundTable() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.changeColumnType("T", "a", "integer")));

      assertTrue(ex.getMessage().toLowerCase().contains("cannot be changed"));
      DataRef ref = t.getColumnSelection(false).getAttribute("a");
      assertInstanceOf(ColumnRef.class, ref);
      assertNotEquals("integer", ((ColumnRef) ref).getDataType());
   }

   @Test
   void changeColumnTypeAllowsEmbeddedTable() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.changeColumnType("T", "a", "integer"));

      DataRef ref = t.getColumnSelection(false).getAttribute("a");
      assertInstanceOf(ColumnRef.class, ref);
      assertEquals("integer", ((ColumnRef) ref).getDataType());
   }

   @Test
   void changeColumnTypeRejectsExpressionAggregateMeasureOnEmbeddedTable() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> {
         ed.addExpressionColumn("T", "e", "field['a']", "double", false);
         ed.setGroupAggregate("T", List.of(), List.of(
            new WorksheetMutationSupport.AggregateSpec("e", "SUM", null)));
      });

      // Mirrors the UI's isExpressionAggregate exclusion: "e" would otherwise be
      // allowed via cr.isExpression() (EmbeddedTableAssembly is also unconditionally
      // allowed), but its exposed type is computed from the SUM formula when the
      // query runs, not read back off this override, so it must be rejected.
      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.changeColumnType("T", "e", "integer")));

      assertTrue(ex.getMessage().toLowerCase().contains("cannot be changed"));
   }

   @Test
   void changeColumnTypeAllowsNonExpressionAggregateMeasureOnEmbeddedTable() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.setGroupAggregate("T", groups("a"),
         List.of(new WorksheetMutationSupport.AggregateSpec("b", "SUM", null))));

      // "b" is a raw (non-expression) aggregate measure. The UI's isExpressionAggregate
      // check only excludes EXPRESSION-typed aggregates, so this must stay allowed here
      // too — the backend guard should not be more restrictive than the UI.
      svc.apply("TOK", agent, ed -> ed.changeColumnType("T", "b", "integer"));

      DataRef ref = t.getColumnSelection(false).getAttribute("b");
      assertInstanceOf(ColumnRef.class, ref);
      assertEquals("integer", ((ColumnRef) ref).getDataType());
   }

   @Test
   void changeColumnTypeAllowsGroupColumnOnAggregatedEmbeddedTable() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.setGroupAggregate("T", groups("a"),
         List.of(new WorksheetMutationSupport.AggregateSpec("b", "SUM", null))));

      // The group-by column passes its value through unchanged (unlike an aggregate
      // output), so its type change should still be allowed even on an aggregated table.
      svc.apply("TOK", agent, ed -> ed.changeColumnType("T", "a", "integer"));

      DataRef ref = t.getColumnSelection(false).getAttribute("a");
      assertInstanceOf(ColumnRef.class, ref);
      assertEquals("integer", ((ColumnRef) ref).getDataType());
   }

   /**
    * The defect this guards: {@code XEmbeddedTable#setDataType} writes the new type into its
    * internal {@code types[]} array unconditionally, before converting a single row. When
    * {@code confirmed=false} (force=false) and a value can't be parsed, it throws instead of
    * nulling the value out -- but nothing had reverted {@code types[col]}, so the "Nothing was
    * changed" exception left the embedded table's own bookkeeping pointing at the rejected type
    * even though not one value had actually been converted. {@code EmbeddedTableAssembly
    * #getEmbeddedData()} returns that same live array (not a copy), so the desync landed
    * directly on the assembly and would have surfaced the next time
    * {@code refreshColumnType()} read it.
    */
   @Test
   void changeColumnTypeWithoutConfirmationLeavesEmbeddedTableUnchanged() throws Exception {
      Worksheet ws = new Worksheet();
      // Column "a" holds "x1"/"x2" -- not parseable as integer -- so force=false throws.
      EmbeddedTableAssembly t = embeddedWithData(ws, "T");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.changeColumnType("T", "a", "integer", false)));

      assertTrue(ex.getMessage().contains("Nothing was changed"), ex.getMessage());
      DataRef ref = t.getColumnSelection(false).getAttribute("a");
      assertInstanceOf(ColumnRef.class, ref);
      assertEquals(XSchema.STRING, ((ColumnRef) ref).getDataType(),
         "the ColumnRef's declared type must be rolled back");
      assertEquals(XSchema.STRING, t.getEmbeddedData().getDataType(0),
         "the embedded table's internal types[] must be rolled back too, not just the ColumnRef");
   }

   // =========================================================================
   // Column reorder tests
   // =========================================================================

   @Test
   void reorderColumnsAppliesRequestedOrder() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b", "c");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.reorderColumns("T", List.of("c", "a", "b")));

      ColumnSelection cs = t.getColumnSelection(false);
      assertEquals("c", cs.getAttribute(0).getAttribute());
      assertEquals("a", cs.getAttribute(1).getAttribute());
      assertEquals("b", cs.getAttribute(2).getAttribute());
   }

   @Test
   void reorderColumnsMatchesColumnsByBareNameNotEntityQualifiedName() throws Exception {
      // Bug #75999: columns whose entity is non-blank (e.g. unpivot header columns,
      // whose entity gets retroactively set to the base table's name the first time
      // the table is queried) were never matched against the caller's bare column
      // names, so reorderColumns silently fell back to the original order for them.
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = new EmbeddedTableAssembly(ws, "T");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef("T", "a")));
      cs.addAttribute(new ColumnRef(new AttributeRef("T", "b")));
      cs.addAttribute(new ColumnRef(new AttributeRef("T", "c")));
      t.setColumnSelection(cs, false);
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.reorderColumns("T", List.of("c", "a", "b")));

      ColumnSelection reordered = t.getColumnSelection(false);
      assertEquals("c", reordered.getAttribute(0).getAttribute(),
         "entity-qualified columns must still be matched by their bare attribute name");
      assertEquals("a", reordered.getAttribute(1).getAttribute());
      assertEquals("b", reordered.getAttribute(2).getAttribute());
   }

   @Test
   void reorderColumnsDoesNotDropColumnsWithAmbiguousBareAttributeName() throws Exception {
      // A join/concatenated table can expose two columns with the same bare attribute
      // name but different entities (e.g. "Customers.ID" and "Orders.ID"). Keying the
      // lookup solely on the bare name would let the second collide with and silently
      // erase the first from the reordered selection.
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = new EmbeddedTableAssembly(ws, "T");
      ColumnSelection cs = new ColumnSelection();
      ColumnRef customerId = new ColumnRef(new AttributeRef("Customers", "ID"));
      ColumnRef orderId = new ColumnRef(new AttributeRef("Orders", "ID"));
      ColumnRef name = new ColumnRef(new AttributeRef(null, "Name"));
      cs.addAttribute(customerId);
      cs.addAttribute(orderId);
      cs.addAttribute(name);
      t.setColumnSelection(cs, false);
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.reorderColumns("T", List.of("Name", "ID")));

      ColumnSelection reordered = t.getColumnSelection(false);
      assertEquals(3, reordered.getAttributeCount(),
         "an ambiguous bare-name collision must not silently drop a column");
      assertTrue(reordered.containsAttribute(customerId), "Customers.ID must survive reordering");
      assertTrue(reordered.containsAttribute(orderId), "Orders.ID must survive reordering");
   }

   @Test
   void reorderColumnsRefusedOnCrosstabTable() throws Exception {
      // Bug #76082: the Composer UI disables "Reorder Table Columns" for a crosstab
      // table (ws-details-pane.component.ts#isSupportChangeColumnOrder), because column
      // layout there is driven by the row/column groups, not the column selection order.
      // reorderColumns() had no equivalent guard, so it silently dropped names that don't
      // match a static column (e.g. a pivoted value like "2024") and returned success.
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t =
         TestWorksheets.tableWithColumns(ws, "T", "orderDate", "employee", "total");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed ->
         ed.setGroupAggregate("T",
            groups("orderDate", "employee"),
            List.of(new WorksheetMutationSupport.AggregateSpec("total", "SUM", null))));
      t.getAggregateInfo().setCrosstab(true);

      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed -> ed.reorderColumns("T", List.of("2024", "employee"))));
      assertTrue(ex.getMessage().contains("crosstab"), "Unexpected message: " + ex.getMessage());
      assertTrue(ex.getMessage().contains("T"), "Unexpected message: " + ex.getMessage());
   }

   // =========================================================================
   // Cell/row edits: snapshot write protection
   // =========================================================================

   /**
    * An embedded table carrying real data, so the cell/row ops reach their actual write and a
    * failure to write shows up as an unchanged value rather than as an exception.
    *
    * <p>Row 0 of an {@link XEmbeddedTable} is the header row, which is where
    * {@code setEmbeddedData} derives the column selection from.
    */
   private EmbeddedTableAssembly embeddedWithData(Worksheet ws, String name) {
      EmbeddedTableAssembly t = new EmbeddedTableAssembly(ws, name);
      t.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.STRING, XSchema.STRING },
         new Object[][] { { "a", "b" }, { "x1", "y1" }, { "x2", "y2" } }));
      return t;
   }

   @Test
   void editCellWritesToAPlainEmbeddedTable() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = embeddedWithData(ws, "T");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.editCell("T", 0, 0, "changed"));

      assertEquals("changed", t.getEmbeddedData().getObject(1, 0),
         "a plain embedded table must still accept edit_cell -- the snapshot guard must not "
            + "widen into this path");
   }

   @Test
   void editCellRefusesSnapshotEmbeddedTableInsteadOfDiscardingTheWrite() throws Exception {
      // The defect this guards: SnapshotEmbeddedTableAssembly.getEmbeddedData() returns a freshly
      // built wrapper on every call, and every XEmbeddedTable mutator is copy-and-swap rather than
      // in-place, so the write landed on a throwaway object. The op reported success and changed
      // nothing.
      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly t = TestWorksheets.snapshotTableWithColumns(ws, "S", "a", "b");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.editCell("S", 0, 0, "changed")));

      assertTrue(ex.getMessage().contains("snapshot"), ex.getMessage());
      assertTrue(ex.getMessage().contains("EMBEDDED_SNAPSHOT"),
         "the refusal must name the type an agent can check beforehand: " + ex.getMessage());
      assertTrue(ex.getMessage().contains("import_csv_table"),
         "the refusal must name the one workaround that actually exists: " + ex.getMessage());
   }

   @Test
   void insertRowRefusesSnapshotEmbeddedTable() throws Exception {
      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly t = TestWorksheets.snapshotTableWithColumns(ws, "S", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.insertRow("S", 0)));

      assertTrue(ex.getMessage().startsWith("insert_row"), ex.getMessage());
      assertTrue(ex.getMessage().contains("snapshot"), ex.getMessage());
   }

   @Test
   void deleteRowRefusesSnapshotEmbeddedTable() throws Exception {
      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly t = TestWorksheets.snapshotTableWithColumns(ws, "S", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.deleteRow("S", 0)));

      assertTrue(ex.getMessage().startsWith("delete_row"), ex.getMessage());
      assertTrue(ex.getMessage().contains("snapshot"), ex.getMessage());
   }

   @Test
   void cellEditsKeepTheirOriginalNonEmbeddedRefusalWording() throws Exception {
      // Pinned verbatim on purpose: the consolidated Composer plugin test plan cites this exact
      // string as the live evidence for its L2 Finding 1 re-verification. Rewording it silently
      // invalidates that record.
      Worksheet ws = new Worksheet();
      ws.addAssembly(TestWorksheets.nonEmbeddedTableWithColumns(ws, "B", "a"));
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.editCell("B", 0, 0, "v")));

      assertEquals("edit_cell only works on embedded tables: B", ex.getMessage());
   }

   // =========================================================================
   // Column ops: snapshot write protection
   // =========================================================================

   @Test
   void addColumnRefusesSnapshotEmbeddedTable() throws Exception {
      // Same missing write-back as the cell/row ops: data.insertCol() lands on a throwaway
      // wrapper, so the ColumnSelection would gain a column the data does not have.
      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly t = TestWorksheets.snapshotTableWithColumns(ws, "S", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.addColumn("S", "newcol", "string")));

      assertTrue(ex.getMessage().startsWith("add_column"), ex.getMessage());
      assertTrue(ex.getMessage().contains("EMBEDDED_SNAPSHOT"), ex.getMessage());
      assertTrue(ex.getMessage().contains("add_expression_column"),
         "the refusal must name the column kind that does work here: " + ex.getMessage());
      assertNull(t.getColumnSelection(false).getAttribute("newcol"),
         "nothing may be added to the selection when the data cannot follow");
   }

   @Test
   void addColumnStillWorksOnAPlainEmbeddedTable() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = embeddedWithData(ws, "T");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addColumn("T", "newcol", "string"));

      assertNotNull(t.getColumnSelection(false).getAttribute("newcol"),
         "the snapshot guard must not widen into the plain embedded path");
   }

   @Test
   void removeColumnRefusesASnapshotsDataColumn() throws Exception {
      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly t = TestWorksheets.snapshotTableWithColumns(ws, "S", "a", "b");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.removeColumn("S", "a")));

      assertTrue(ex.getMessage().startsWith("remove_column"), ex.getMessage());
      assertTrue(ex.getMessage().contains("data column"), ex.getMessage());
      assertNotNull(t.getColumnSelection(false).getAttribute("a"),
         "the column must survive the refusal");
   }

   /**
    * The carve-out, and the reason this could not be Finding 5's flat guard: an expression column
    * exists only in the ColumnSelection, never in the data, so removing it strands nothing. The
    * Composer allows exactly this and no more.
    */
   @Test
   void removeColumnAllowsASnapshotsExpressionColumn() throws Exception {
      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly t = TestWorksheets.snapshotTableWithColumns(ws, "S", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent,
         ed -> ed.addExpressionColumn("S", "calc", "1 + 1", XSchema.INTEGER, false));
      assertNotNull(t.getColumnSelection(false).getAttribute("calc"),
         "precondition: an expression column can be added to a snapshot");

      svc.apply("TOK", agent, ed -> ed.removeColumn("S", "calc"));

      assertNull(t.getColumnSelection(false).getAttribute("calc"),
         "an expression column has no data behind it, so removing it from a snapshot is safe");
      assertNotNull(t.getColumnSelection(false).getAttribute("a"),
         "the data column is untouched");
   }

   // =========================================================================
   // Concatenation tests
   // =========================================================================

   private static ColumnRef col(String name, String dataType) {
      return col(name, dataType, true);
   }

   private static ColumnRef col(String name, String dataType, boolean visible) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, name));
      ref.setDataType(dataType);
      ref.setVisible(visible);
      return ref;
   }

   /**
    * Builds a table from already-configured columns. The types and visibility have to be set
    * <em>before</em> the private selection is installed: {@code setColumnSelection} regenerates the
    * public selection from clones of the visible private columns, and it is the public selection
    * the concatenation checks read — so a type set on a private column afterwards never reaches
    * them.
    */
   private static EmbeddedTableAssembly table(Worksheet ws, String name, ColumnRef... cols) {
      EmbeddedTableAssembly t = new EmbeddedTableAssembly(ws, name);
      ColumnSelection cs = new ColumnSelection();

      for(ColumnRef c : cols) {
         cs.addAttribute(c);
      }

      t.setColumnSelection(cs, false);
      return t;
   }

   /**
    * Different types are not automatically a mismatch: {@code AssetUtil.isMergeable} — and
    * therefore Composer — treats all the number types as interchangeable, and likewise the string
    * types. Refusing these would block concatenations the product itself considers valid.
    */
   @Test
   void addConcatenationAcceptsColumnTypesThatMerge() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = table(ws, "A", col("n", XSchema.INTEGER), col("s", XSchema.STRING));
      EmbeddedTableAssembly b = table(ws, "B", col("n", XSchema.DOUBLE), col("s", XSchema.CHAR));
      ws.addAssembly(a);
      ws.addAssembly(b);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addConcatenation("U", List.of("A", "B"), "UNION"));

      assertTrue(ws.getAssembly("U") instanceof ConcatenatedTableAssembly);
   }

   /**
    * {@code distinct} is threaded onto each pair's operator only "when asked" (a null leaves the
    * engine's default), so a {@code true} has to actually land on the built
    * {@link ConcatenatedTableAssembly}'s operator rather than silently being dropped.
    */
   @Test
   void addConcatenationThreadsDistinctIntoTheOperator() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = table(ws, "A", col("id", XSchema.INTEGER));
      EmbeddedTableAssembly b = table(ws, "B", col("id", XSchema.INTEGER));
      ws.addAssembly(a);
      ws.addAssembly(b);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addConcatenation("U", List.of("A", "B"), "UNION", true));

      ConcatenatedTableAssembly ctbl = (ConcatenatedTableAssembly) ws.getAssembly("U");
      assertTrue(ctbl.getOperator(0).isDistinct(),
         "distinct=true must be threaded through to the pair's operator");
   }

   /**
    * Sources are combined BY POSITION, so a pair that lines up numerically but not by type produces
    * a column carrying two unrelated kinds of value — which renders as an ordinary column and
    * reports no error anywhere. The message has to name the position and both sides, since that is
    * what tells the caller which column to reorder or retype.
    */
   @Test
   void addConcatenationRejectsColumnsThatDoNotLineUpByType() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a =
         table(ws, "A", col("id", XSchema.INTEGER), col("name", XSchema.STRING));
      EmbeddedTableAssembly b =
         table(ws, "B", col("id", XSchema.INTEGER), col("amount", XSchema.INTEGER));
      ws.addAssembly(a);
      ws.addAssembly(b);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.addConcatenation("U", List.of("A", "B"), "UNION")));

      assertTrue(ex.getMessage().contains("position 2"), ex.getMessage());
      assertTrue(ex.getMessage().contains("name"), ex.getMessage());
      assertTrue(ex.getMessage().contains("amount"), ex.getMessage());
      assertNull(ws.getAssembly("U"), "nothing may be added when the sources do not line up");
   }

   /**
    * ws.removeAssembly does not clean up what depended on the deleted table: its removeMirrors call
    * returns immediately unless the assembly BEING deleted is itself an outer mirror. So the
    * dependent survived pointing at a name that was gone, every query against it failed, and no
    * tool on this surface could repair it. Reproduced live before the guard existed.
    *
    * <p>The UI's own write path checks the same thing with the same helper
    * ({@code WSRemoveAssembliesService#removeAssemblies}, AssetEventUtil.hasDependent); it skips
    * and warns because it is deleting a batch, while this op deletes one named table and so
    * refuses.
    */
   @Test
   void deleteTableRefusesWhenSomethingIsBuiltOnIt() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = table(ws, "A", col("id", XSchema.INTEGER));
      ws.addAssembly(a);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");
      svc.apply("TOK", agent, ed -> ed.addMirror("M", "A"));

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.deleteTable("A")));

      assertTrue(ex.getMessage().contains("built on it"), ex.getMessage());
      assertNotNull(ws.getAssembly("A"), "the table must still be there");
      assertNotNull(ws.getAssembly("M"), "and so must the mirror that depends on it");
   }

   /** A table nothing depends on still deletes — the guard must not block ordinary deletion. */
   @Test
   void deleteTableStillDeletesWhenNothingDependsOnIt() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(table(ws, "A", col("id", XSchema.INTEGER)));
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.deleteTable("A"));

      assertNull(ws.getAssembly("A"));
   }

   /**
    * AssemblyInfo:254 writes the name into a CDATA section verbatim, so a name containing the
    * terminator closes it early and leaves malformed XML in storage. The Composer's Angular
    * whitelist refuses it; nothing on this path did, and the save path validates no assembly name.
    *
    * <p>Only the terminator is refused, not the whole whitelist: '/' and '"' are legal CDATA
    * content and round-trip intact, so refusing them here would break names that already work.
    */
   @Test
   void renameTableRefusesANameThatWouldBreakTheStoredXml() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = table(ws, "A", col("id", XSchema.INTEGER));
      ws.addAssembly(a);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.renameTable("A", "bad]]>name")));

      assertTrue(ex.getMessage().contains("]]>"), ex.getMessage());
      assertNotNull(ws.getAssembly("A"), "the rename must not have happened");
      assertNull(ws.getAssembly("bad]]>name"));
   }

   /** A slash and a quote are legal CDATA content, so the guard must let them through. */
   @Test
   void renameTableAllowsCharactersTheComposerRefusesButStorageAccepts() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(table(ws, "A", col("id", XSchema.INTEGER)));
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.renameTable("A", "a/b\"c"));

      assertNotNull(ws.getAssembly("a/b\"c"));
   }

   /**
    * {@code requireStorableName} was wired into {@code placeAssembly}/{@code renameTable} only.
    * The multi-table {@code addJoin} overload cannot go through {@code placeAssembly} (it has to
    * register the join before wiring its edges via {@code InnerJoinService}), so it bypassed the
    * guard the same way an unescaped-CDATA name would corrupt storage from any of these entry
    * points.
    */
   @Test
   void addJoinMultiTableRefusesANameThatWouldBreakTheStoredXml() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(table(ws, "A", col("id", XSchema.INTEGER)));
      ws.addAssembly(table(ws, "B", col("id", XSchema.INTEGER)));
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");
      List<WorksheetMutationSupport.JoinPathSpec> paths =
         List.of(new WorksheetMutationSupport.JoinPathSpec("A", "id", "B", "id", "INNER"));

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.addJoin("bad]]>name", paths)));

      assertTrue(ex.getMessage().contains("]]>"), ex.getMessage());
      assertNull(ws.getAssembly("bad]]>name"));
   }

   /**
    * Same gap as above, for {@code duplicateAssembly}: it calls {@code ws.addAssembly(clone)}
    * directly after {@code setName}, bypassing {@code placeAssembly}'s
    * {@code requireStorableName} check.
    */
   @Test
   void duplicateAssemblyRefusesANameThatWouldBreakTheStoredXml() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(table(ws, "A", col("id", XSchema.INTEGER)));
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.duplicateAssembly("A", "bad]]>name")));

      assertTrue(ex.getMessage().contains("]]>"), ex.getMessage());
      assertNull(ws.getAssembly("bad]]>name"));
   }

   /**
    * Same gap again, for {@code renameVariable}: it calls {@code ws.renameAssembly} directly with
    * no name check at all, even though a variable name hits the same unescaped-CDATA write path
    * as a table name.
    */
   @Test
   void renameVariableRefusesANameThatWouldBreakTheStoredXml() throws Exception {
      Worksheet ws = new Worksheet();
      variableAssembly(ws, "region", XSchema.STRING);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.renameVariable("region", "bad]]>name")));

      assertTrue(ex.getMessage().contains("]]>"), ex.getMessage());
      assertNotNull(ws.getAssembly("region"), "the rename must not have happened");
      assertNull(ws.getAssembly("bad]]>name"));
   }

   /**
    * A negative limit is a mistake, not a way to say "unlimited". The Composer's control refuses it
    * (FormValidators.positiveIntegerInRange); this path folded it into -1 and reported success, so
    * a caller asking for a limit got none and no indication of it.
    */
   @Test
   void setTablePropertiesRefusesANegativeMaxRows() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(table(ws, "A", col("id", XSchema.INTEGER)));
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent,
                         ed -> ed.setTableProperties("A", null, null, -100, null, null, null, null)));

      assertTrue(ex.getMessage().contains("negative"), ex.getMessage());
   }

   /**
    * {@code rowCount} is the dialog's "Rows" field for an embedded table
    * ({@code TablePropertyDialogService:113-121}), which counts data rows only -- the stored
    * table carries an extra header row ({@code getRowCount() - 1} in the dialog's terms). None of
    * grow, shrink, the non-embedded no-op, or the negative rejection had a test.
    */
   @Test
   void setTablePropertiesGrowsEmbeddedRowCount() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = embeddedWithData(ws, "T"); // header + 2 data rows
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent,
                ed -> ed.setTableProperties("T", null, null, null, null, null, null, 5));

      assertEquals(6, t.getEmbeddedData().getRowCount(),
         "5 data rows plus the header row");
   }

   @Test
   void setTablePropertiesShrinksEmbeddedRowCount() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = embeddedWithData(ws, "T"); // header + 2 data rows
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent,
                ed -> ed.setTableProperties("T", null, null, null, null, null, null, 1));

      assertEquals(2, t.getEmbeddedData().getRowCount(),
         "1 data row plus the header row");
   }

   @Test
   void setTablePropertiesIgnoresRowCountOnANonEmbeddedTable() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // The dialog omits the "Rows" control for a non-embedded table; this must not throw just
      // because a caller sent it anyway.
      assertDoesNotThrow(() -> svc.apply(
         "TOK", agent, ed -> ed.setTableProperties("T", null, null, null, null, null, null, 5)));
   }

   @Test
   void setTablePropertiesRefusesANegativeRowCount() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = embeddedWithData(ws, "T");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent,
                         ed -> ed.setTableProperties("T", null, null, null, null, null, null, -1)));

      assertTrue(ex.getMessage().contains("negative"), ex.getMessage());
      assertEquals(3, t.getEmbeddedData().getRowCount(), "the refused call must not touch the data");
   }

   /**
    * Making a table primary also exposes it to viewsheets, as WSPrimaryService:84 does. Only
    * matters for a table whose flag was cleared earlier, which is why the test clears it first.
    */
   @Test
   void setPrimaryAssemblyAlsoMakesTheTableVisibleToViewsheets() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = table(ws, "A", col("id", XSchema.INTEGER));
      ws.addAssembly(a);
      ((inetsoft.uql.asset.internal.TableAssemblyInfo) a.getTableInfo()).setVisibleTable(false);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.setPrimaryAssembly("A"));

      assertTrue(((inetsoft.uql.asset.internal.TableAssemblyInfo) a.getTableInfo()).isVisibleTable(),
                 "the primary table must be reachable from a viewsheet binding");
   }

   /**
    * The aggregate flag follows whether the table groups, not which display mode is selected:
    * TableModeService#setLiveTableMode:353 and #setDefaultTableMode:334 compute it, while
    * full/detail/edit force it false. "live" and "default" left it untouched here, so a table
    * switched to "full" earlier kept aggregate=false after coming back — and isAggregate() is real
    * query state, selecting the public vs private column selection and forming part of the lens
    * cache key.
    */
   @Test
   void setTableModeRestoresTheAggregateFlagWhenReturningToLive() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a =
         table(ws, "A", col("g", XSchema.STRING), col("n", XSchema.INTEGER));
      ws.addAssembly(a);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");
      svc.apply("TOK", agent, ed -> ed.setGroupAggregate(
         "A", groups("g"),
         List.of(new WorksheetMutationSupport.AggregateSpec("n", "SUM", null))));
      svc.apply("TOK", agent, ed -> ed.setTableMode("A", "full"));
      assertFalse(((inetsoft.uql.asset.internal.TableAssemblyInfo) a.getTableInfo()).isAggregate(),
                  "precondition: full mode forces the flag off");

      svc.apply("TOK", agent, ed -> ed.setTableMode("A", "live"));

      assertTrue(((inetsoft.uql.asset.internal.TableAssemblyInfo) a.getTableInfo()).isAggregate(),
                 "returning to live must restore it, since the table still groups");
   }

   /**
    * Same fix as {@link #setTableModeRestoresTheAggregateFlagWhenReturningToLive}, for the
    * {@code "default"} branch: {@code TableModeService#setDefaultTableMode:334} computes the
    * aggregate flag the same way {@code setLiveTableMode} does, but only {@code "live"} had a
    * test for it.
    */
   @Test
   void setTableModeRestoresTheAggregateFlagForDefaultMode() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a =
         table(ws, "A", col("g", XSchema.STRING), col("n", XSchema.INTEGER));
      ws.addAssembly(a);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");
      svc.apply("TOK", agent, ed -> ed.setGroupAggregate(
         "A", groups("g"),
         List.of(new WorksheetMutationSupport.AggregateSpec("n", "SUM", null))));
      svc.apply("TOK", agent, ed -> ed.setTableMode("A", "full"));
      assertFalse(((inetsoft.uql.asset.internal.TableAssemblyInfo) a.getTableInfo()).isAggregate(),
                  "precondition: full mode forces the flag off");

      svc.apply("TOK", agent, ed -> ed.setTableMode("A", "default"));

      assertTrue(((inetsoft.uql.asset.internal.TableAssemblyInfo) a.getTableInfo()).isAggregate(),
                 "the \"default\" branch must recompute the flag the same way \"live\" does, not "
                 + "leave it forced off from the earlier full-mode switch");
      assertTrue(a.isLiveData(), "\"default\" is live for an embedded table");
   }

   /**
    * Both range columns take their name from the source column and the option rather than from the
    * caller, so issuing the same call twice generated the same name twice — and a second column
    * with the same identity makes every later lookup by name ambiguous. ValueRangeService:204
    * refuses it; this path did not.
    */
   @Test
   void addDateRangeColumnRefusesASecondColumnWithTheSameGeneratedName() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(table(ws, "A", col("when", XSchema.DATE)));
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");
      svc.apply("TOK", agent, ed -> ed.addDateRangeColumn("A", "when", "YEAR"));

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.addDateRangeColumn("A", "when", "YEAR")));

      assertTrue(ex.getMessage().contains("already exists"), ex.getMessage());
   }

   /**
    * {@code requireDerivedColumnFits} has two branches -- a name collision (covered above) and a
    * column-count cap -- and only the collision branch had a test. This exercises the cap branch
    * via {@code max.col.count}, restored in a {@code finally} so the property does not leak into
    * later tests.
    */
   @Test
   void addDateRangeColumnRefusesAtTheColumnCapWithoutMutating() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "orderDate", "total");
      ws.addAssembly(t);
      ColumnRef dateCol = (ColumnRef) t.getColumnSelection(false).getAttribute("orderDate");
      dateCol.setDataType(XSchema.DATE);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      String original = SreeEnv.getProperty("max.col.count");

      try {
         SreeEnv.setProperty("max.col.count", "2");

         PairingException ex = assertThrows(PairingException.class,
            () -> svc.apply("TOK", agent,
                            ed -> ed.addDateRangeColumn("T", "orderDate", "QUARTER_OF_YEAR")));

         assertTrue(ex.getMessage().toLowerCase().contains("maximum"), ex.getMessage());
         assertEquals(2, t.getColumnSelection(false).getAttributeCount(),
            "the refused add must not have added a column past the cap");
      }
      finally {
         SreeEnv.setProperty("max.col.count", original);
      }
   }

   /**
    * {@code Worksheet.addAssembly} replaces a same-named assembly without complaint, so reusing a
    * name never created a second assembly — it destroyed the first and handed every dependent of
    * that name to the replacement. Reproduced through this op against a live server: a
    * concatenation named after one of its own sources became its own source, ended up with zero
    * columns, and left the concatenation downstream of the destroyed original reading from an empty
    * table, while the call reported a 500 and all of it stuck.
    *
    * <p>The surviving assembly must still be the original, not a replacement — asserting only that
    * the call threw would pass even if the overwrite had already happened.
    */
   @Test
   void addConcatenationRefusesANameAlreadyInUse() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = table(ws, "A", col("id", XSchema.INTEGER));
      EmbeddedTableAssembly b = table(ws, "B", col("id", XSchema.INTEGER));
      ws.addAssembly(a);
      ws.addAssembly(b);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.addConcatenation("A", List.of("A", "B"), "UNION")));

      assertTrue(ex.getMessage().contains("already exists"), ex.getMessage());
      assertSame(a, ws.getAssembly("A"), "the existing assembly must not be replaced");
      assertTrue(ws.getAssembly("A") instanceof EmbeddedTableAssembly,
                 "A must still be the original table, not a concatenation standing in its place");
   }

   /**
    * A source listed twice counts its rows twice in the UNION and reports nothing.
    * {@code ConcatenateTablesService#checkValidity} refuses it with
    * {@code common.table.unionDuplicate}; this path never ported the check.
    */
   @Test
   void addConcatenationRefusesARepeatedSource() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = table(ws, "A", col("id", XSchema.INTEGER));
      ws.addAssembly(a);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.addConcatenation("U", List.of("A", "A"), "UNION")));

      assertTrue(ex.getMessage().contains("twice"), ex.getMessage());
      assertNull(ws.getAssembly("U"), "nothing may be added when a source is repeated");
   }

   /** Same rule as above, reached through the other entry point. */
   @Test
   void addConcatSubtableRefusesASourceAlreadyPresent() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = table(ws, "A", col("id", XSchema.INTEGER));
      EmbeddedTableAssembly b = table(ws, "B", col("id", XSchema.INTEGER));
      ws.addAssembly(a);
      ws.addAssembly(b);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");
      svc.apply("TOK", agent, ed -> ed.addConcatenation("U", List.of("A", "B"), "UNION"));

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.addConcatSubtable("U", "A")));

      assertTrue(ex.getMessage().contains("already a source"), ex.getMessage());
      assertEquals(2, ((ConcatenatedTableAssembly) ws.getAssembly("U")).getTableAssemblies().length,
                   "the subtable list must be unchanged");
   }

   /**
    * The defect this guards: adding a mirror of a concatenation back into that concatenation closed
    * a C -> M -> C cycle. The mutation committed, {@code apply} checkpointed it, and the caller got
    * an unhandled 500 from further down the post-edit pipeline — so a caller that reasonably
    * concluded nothing had changed was wrong.
    *
    * <p>Two things make the pre-write placement necessary rather than merely tidy.
    * {@code Worksheet#checkDependencies} is reached from
    * {@code AssetQuerySandbox#refreshColumnSelection} only after the commit and the checkpoint. And
    * {@code AbstractWSAssembly#checkDependency} cannot see a two-node cycle at all:
    * {@code AssetUtil#getDependedAssemblies0} seeds its visited set with the root and passes
    * {@code included=false} for it, so a loop back to the root is dropped as already-visited and
    * never reaches the array it matches against.
    *
    * <p>Asserting the subtable count is the point of the test. A version of this fix that threw
    * after {@code setTableAssemblies} would satisfy the exception assertion and still leave the
    * cycle in the asset.
    */
   @Test
   void addConcatSubtableRefusesACycleWithoutMutating() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = table(ws, "A", col("id", XSchema.INTEGER));
      EmbeddedTableAssembly b = table(ws, "B", col("id", XSchema.INTEGER));
      ws.addAssembly(a);
      ws.addAssembly(b);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");
      svc.apply("TOK", agent, ed -> ed.addConcatenation("C", List.of("A", "B"), "UNION"));
      svc.apply("TOK", agent, ed -> ed.addMirror("M", "C"));

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.addConcatSubtable("C", "M")));

      assertTrue(ex.getMessage().contains("circular"), ex.getMessage());
      assertTrue(ex.getMessage().contains("M"), ex.getMessage());
      assertEquals(2, ((ConcatenatedTableAssembly) ws.getAssembly("C")).getTableAssemblies().length,
                   "the cycle must be refused before the subtable list is written");
   }

   /**
    * The defect this guards: {@code add_concat_subtable(concatName, concatName)} was caught by
    * neither existing guard. The duplicate-source loop only walks {@code ctbl}'s current
    * subtables, and {@code ctbl} is never one of those -- it IS the concatenation. And
    * {@code checkCyclicalDependency(ws, ctbl, newTable)} can't see it either when
    * {@code newTable == ctbl}: {@code AssetUtil#getDependedAssemblies0} seeds its visited set
    * with the root and passes {@code included=false} for it, so before the attach there is
    * nothing yet linking {@code ctbl} back to itself. Without an explicit check, {@code ctbl}
    * was appended to its own {@code TableAssemblies} array.
    */
   @Test
   void addConcatSubtableRefusesSelfReferenceWithoutMutating() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = table(ws, "A", col("id", XSchema.INTEGER));
      EmbeddedTableAssembly b = table(ws, "B", col("id", XSchema.INTEGER));
      ws.addAssembly(a);
      ws.addAssembly(b);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");
      svc.apply("TOK", agent, ed -> ed.addConcatenation("C", List.of("A", "B"), "UNION"));

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.addConcatSubtable("C", "C")));

      assertTrue(ex.getMessage().contains("itself"), ex.getMessage());
      assertEquals(2, ((ConcatenatedTableAssembly) ws.getAssembly("C")).getTableAssemblies().length,
                   "the self-reference must be refused before the subtable list is written");
   }

   /**
    * With three sources the mismatch is between the third and the first, which pins down that the
    * loop keeps checking past the first pair and names the offending source rather than whichever
    * one happened to come second.
    *
    * <p>It does not distinguish anchoring on {@code sources[0]} from anchoring on the predecessor,
    * and no test can: {@code isMergeable} is an equivalence relation over disjoint type classes, so
    * a third source that clashes with the first necessarily clashes with the second as well.</p>
    */
   @Test
   void addConcatenationChecksEverySourceNotJustTheFirstPair() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a =
         table(ws, "A", col("id", XSchema.INTEGER), col("name", XSchema.STRING));
      EmbeddedTableAssembly b =
         table(ws, "B", col("id", XSchema.INTEGER), col("name", XSchema.STRING));
      EmbeddedTableAssembly c =
         table(ws, "C", col("id", XSchema.INTEGER), col("amount", XSchema.INTEGER));
      ws.addAssembly(a);
      ws.addAssembly(b);
      ws.addAssembly(c);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent,
                         ed -> ed.addConcatenation("U", List.of("A", "B", "C"), "UNION")));

      assertTrue(ex.getMessage().contains("position 2"), ex.getMessage());
      assertTrue(ex.getMessage().contains("\"C\""),
                 "the third source is the one that clashes and has to be named: " + ex.getMessage());
      assertNull(ws.getAssembly("U"));
   }

   /**
    * The counts come from the PUBLIC column selection — visible columns only — while the read
    * model's column list includes hidden ones. Saying "visible" keeps the number in the refusal
    * reconcilable with the number a caller counts in the model it just read.
    */
   @Test
   void addConcatenationRejectsDifferentColumnCountsAndSaysTheyAreVisibleCounts() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = TestWorksheets.tableWithColumns(ws, "A", "id", "name");
      EmbeddedTableAssembly b = TestWorksheets.tableWithColumns(ws, "B", "id");
      ws.addAssembly(a);
      ws.addAssembly(b);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.addConcatenation("U", List.of("A", "B"), "UNION")));

      assertTrue(ex.getMessage().contains("2 visible columns"), ex.getMessage());
      assertTrue(ex.getMessage().contains("1 visible column"), ex.getMessage());
      assertNull(ws.getAssembly("U"));
   }

   /**
    * Hidden columns are excluded from the public selection, so hiding one really does change which
    * columns line up: {@code A(id, hidden:integer, name)} concatenates with {@code B(id, name)}
    * because the hidden column is not there as far as the server is concerned. A check reading the
    * private selection instead would see three columns against two and refuse it.
    */
   @Test
   void addConcatenationLinesUpVisibleColumnsOnly() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = table(ws, "A", col("id", XSchema.INTEGER),
                                     col("hidden", XSchema.INTEGER, false),
                                     col("name", XSchema.STRING));
      EmbeddedTableAssembly b =
         table(ws, "B", col("id", XSchema.INTEGER), col("name", XSchema.STRING));
      ws.addAssembly(a);
      ws.addAssembly(b);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addConcatenation("U", List.of("A", "B"), "UNION"));

      assertTrue(ws.getAssembly("U") instanceof ConcatenatedTableAssembly);
   }

   // =========================================================================
   // Mirror auto-update tests
   // =========================================================================

   /**
    * {@code MirrorAssemblyImpl.setAutoUpdate} returns early for anything that is not an outer
    * mirror, and {@code isAutoUpdate} then answers {@code true} regardless — so the write is
    * accepted and discarded without a word. Every mirror {@code add_mirror} creates is an inner
    * mirror ({@code MirrorTableAssembly(ws, name, assembly)} passes {@code outer = false}), so this
    * is the normal case for an agent, not an edge case.
    */
   @Test
   void setMirrorAutoUpdateRefusesAMirrorThatCannotHonourIt() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly base = TestWorksheets.tableWithColumns(ws, "BASE", "col");
      ws.addAssembly(base);
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M", base);
      ws.addAssembly(mirror);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.setMirrorAutoUpdate("M", false)));

      assertTrue(ex.getMessage().contains("autoUpdate"), ex.getMessage());
      assertTrue(mirror.isAutoUpdate(), "the flag was never actually changed");
   }

   @Test
   void setMirrorAutoUpdateStillWorksOnAnOuterMirror() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly base = TestWorksheets.tableWithColumns(ws, "BASE", "col");
      ws.addAssembly(base);
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "/other-ws", null);
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M", entry, true, base);
      ws.addAssembly(mirror);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.setMirrorAutoUpdate("M", false));

      assertFalse(mirror.isAutoUpdate());
   }

   // =========================================================================
   // set_table_properties -- renaming is a table property
   // =========================================================================

   // A worksheet table has no display name apart from its name, so setting one is a rename. This
   // used to take an "alias" argument and drop it behind a comment, returning success unchanged.

   @Test
   void setTablePropertiesRenamesTheTable() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent,
                ed -> ed.setTableProperties("T", "Scores", null, null, null, null, null));

      assertNotNull(ws.getAssembly("Scores"));
      assertNull(ws.getAssembly("T"));
   }

   @Test
   void setTablePropertiesAppliesTheOtherFieldsToTheRenamedTable() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent,
                ed -> ed.setTableProperties(
                   "T", "Scores", "the description", 25, true, false, false));

      TableAssembly renamed = (TableAssembly) ws.getAssembly("Scores");
      assertEquals("the description", renamed.getDescription());
      assertEquals(25, renamed.getMaxRows());
      assertTrue(renamed.isDistinct());
      assertFalse(renamed.isSQLMergeable());
      assertFalse(renamed.isVisibleTable());
   }

   @Test
   void setTablePropertiesSetsTheMergeableFlag() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      t.setSQLMergeable(true);
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent,
                ed -> ed.setTableProperties("T", null, null, null, null, false, null));

      assertFalse(((TableAssembly) ws.getAssembly("T")).isSQLMergeable());
   }

   @Test
   void omittingMergeableLeavesTheExistingFlagAlone() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      t.setSQLMergeable(false);
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent,
                ed -> ed.setTableProperties("T", null, "note", null, null, null, null));

      assertFalse(((TableAssembly) ws.getAssembly("T")).isSQLMergeable(),
                  "mergeable was omitted, so the existing flag must be untouched");
   }

   @Test
   void setTablePropertiesSetsTheVisibleInViewsheetFlag() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      t.setVisibleTable(true);
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent,
                ed -> ed.setTableProperties("T", null, null, null, null, null, false));

      assertFalse(((TableAssembly) ws.getAssembly("T")).isVisibleTable());
   }

   @Test
   void omittingVisibleInViewsheetLeavesTheExistingFlagAlone() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      t.setVisibleTable(false);
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent,
                ed -> ed.setTableProperties("T", null, "note", null, null, null, null));

      assertFalse(((TableAssembly) ws.getAssembly("T")).isVisibleTable(),
                  "visibleInViewsheet was omitted, so the existing flag must be untouched");
   }

   /**
    * The rename runs first precisely so this holds: a name already in use fails the whole call and
    * leaves the other fields alone, rather than half-writing the patch.
    */
   @Test
   void aRenameOntoAnExistingNameChangesNothingAtAll() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      TableAssembly other = TestWorksheets.nonEmbeddedTableWithColumns(ws, "Taken", "a");
      ws.addAssembly(t);
      ws.addAssembly(other);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      assertThrows(Exception.class, () -> svc.apply(
         "TOK", agent,
         ed -> ed.setTableProperties("T", "Taken", "should not land", 9, true, null, null)));

      TableAssembly untouched = (TableAssembly) ws.getAssembly("T");
      assertNotNull(untouched, "the table must keep its original name");
      assertNull(untouched.getDescription(), "no property may be applied when the rename fails");
   }

   @Test
   void omittingTheNameLeavesItAloneAndStillAppliesTheRest() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent,
                ed -> ed.setTableProperties("T", null, "just a note", null, null, null, null));

      assertNotNull(ws.getAssembly("T"));
      assertEquals("just a note", ((TableAssembly) ws.getAssembly("T")).getDescription());
   }

   /**
    * Worksheet.renameAssembly checks only that the old name exists and the new one is free, so a
    * blank name passes both of its guards and leaves a table nothing can address afterwards.
    */
   @Test
   void aBlankNameIsRefusedRatherThanLeavingTheTableUnaddressable() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      assertThrows(Exception.class, () -> svc.apply(
         "TOK", agent,
         ed -> ed.setTableProperties("T", "   ", "should not land", null, null, null, null)));

      assertNotNull(ws.getAssembly("T"), "the table keeps its name");
      assertNull(((TableAssembly) ws.getAssembly("T")).getDescription(),
                 "nothing else in the patch may be applied either");
   }

   /** Passing the name it already has is a no-op rename, not a collision with itself. */
   @Test
   void passingTheSameNameIsNotTreatedAsACollision() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent,
                ed -> ed.setTableProperties("T", "T", "kept", null, null, null, null));

      assertEquals("kept", ((TableAssembly) ws.getAssembly("T")).getDescription());
   }

   // =========================================================================
   // Filter values and operators
   // =========================================================================

   private static Condition firstCondition(TableAssembly t) {
      return (Condition) ((ConditionItem) t.getPreConditionList().getConditionList()
         .getItem(0)).getXCondition();
   }

   /**
    * The condition is typed from the resolved column, so on a numeric column a $(name) reference
    * does not parse and used to land as 0.0 -- not even the variable's default. The filter still
    * read as valid and still returned rows, so a parameterised filter silently became a constant.
    */
   @Test
   void aVariableReferenceSurvivesAsAVariableNotAsZero() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ((ColumnRef) t.getColumnSelection().getAttribute("a")).setDataType(XSchema.DOUBLE);
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addFilter("T", "a", ">", "$(Floor)"));

      Object v = firstCondition(t).getValue(0);
      assertInstanceOf(UserVariable.class, v, "a $(name) value must stay a variable");
      assertEquals("Floor", ((UserVariable) v).getName());
   }

   /** One condition through set_conditions, so the $(name) handling can be compared to addFilter's. */
   private static void setOneCondition(WorksheetEditService svc, Principal agent, String value)
      throws Exception
   {
      svc.apply("TOK", agent, ed -> ed.setConditions("T", List.of(
         new WorksheetMutationSupport.ConditionNode(
            new WorksheetMutationSupport.ConditionSpec(
               "a", ">", List.of(value), false, null), null, 0))));
   }

   /** A $(name) through set_conditions must stay a variable, typed from its column. */
   @Test
   void aVariableThroughSetConditionsCarriesItsColumnType() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ((ColumnRef) t.getColumnSelection().getAttribute("a")).setDataType(XSchema.DOUBLE);
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      setOneCondition(svc, agent, "$(Floor)");

      Object v = firstCondition(t).getValue(0);
      assertInstanceOf(UserVariable.class, v, "a $(name) value must stay a variable");
      assertEquals("Floor", ((UserVariable) v).getName());
      // Passes on either side of the shared-helper change, and pinned for that reason: the
      // refactor must not alter the type a variable arrives with. Typing was never the defect,
      // and the reason is not the bare constructor -- which does leave the declaration-site
      // StringType default (UserVariable:689) -- but Condition.addValue, which routes every value
      // through convertType, whose `else if(val instanceof UserVariable)` branch
      // (Condition:2129-2149) sets the type node from the condition's OWN type. The variable was
      // therefore typed from the column by the time it was stored, whichever way it was built.
      // Measured rather than reasoned: with this file's fix reverted and the sibling "$()" test
      // failing as the canary that the revert had compiled, the type still read "double".
      assertEquals(XSchema.DOUBLE, ((UserVariable) v).getTypeNode().getType(),
         "the variable must carry the column's type, as it does through add_filter");
   }

   /**
    * The real difference between the two paths: set_conditions had no lower bound on the name, so
    * "$()" became a variable named "" -- one nothing can ever resolve, accepted without complaint.
    */
   @Test
   void anEmptyVariableNameIsNotTreatedAsAVariableThroughSetConditions() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      setOneCondition(svc, agent, "$()");

      assertEquals("$()", firstCondition(t).getValue(0),
         "an empty name is a literal, not a variable nothing can ever resolve");
   }

   /** The non-variable path must be untouched: a plain value still arrives as itself. */
   @Test
   void anOrdinaryValueThroughSetConditionsIsUnchanged() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      setOneCondition(svc, agent, "hello");

      assertEquals("hello", firstCondition(t).getValue(0));
   }

   @Test
   void anOrdinaryValueIsStillCoercedToTheColumnType() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addFilter("T", "a", "=", "hello"));

      assertEquals("hello", firstCondition(t).getValue(0));
   }

   /**
    * Defaulting an unrecognised operator to equals returns a different data set with nothing on
    * screen marking it -- "gt", a shape a caller reaches for when it does not know the vocabulary,
    * used to become an equality test. Not spelled ">": that always had its own case and never
    * reached the default branch, so it could not demonstrate this at all.
    */
   @Test
   void anUnrecognisedOperatorIsRefusedRatherThanAppliedAsEquals() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      Exception thrown = assertThrows(Exception.class, () -> svc.apply(
         "TOK", agent, ed -> ed.addFilter("T", "a", "gt", "1")));

      assertTrue(thrown.getMessage().contains("gt") ||
                 thrown.getCause() != null && thrown.getCause().getMessage().contains("gt"),
                 "the refusal must name the operator it rejected");
      assertNull(t.getPreConditionList() == null ? null : firstConditionOrNull(t),
                 "nothing may be written when the operator is refused");
   }

   /**
    * The refusal exists so a caller can self-correct from it, which only works if it offers
    * everything the parser takes. LIKE was accepted by the switch and absent from the message, and
    * nothing caught it because nothing tied the two together -- this does, in both directions.
    */
   @Test
   void everyAcceptedOperatorIsOfferedByTheRefusalMessage() {
      String message = assertThrows(IllegalArgumentException.class,
         () -> WorksheetMutationSupport.parseOperation("nonsense")).getMessage();

      for(String op : List.of("=", "!=", "<", "<=", ">", ">=", "BETWEEN", "ONE_OF", "NOT_ONE_OF",
                              "STARTING_WITH", "CONTAINS", "LIKE", "NULL", "NOT_NULL", "DATE_IN"))
      {
         assertDoesNotThrow(() -> WorksheetMutationSupport.parseOperation(op),
                            op + " must be a recognised operator");
         assertTrue(message.contains(op),
                    "the refusal must offer " + op + " -- a caller reading it to self-correct "
                    + "cannot discover an operator the message leaves out");
      }
   }

   /**
    * "Absent" has to include whitespace, not just null. add_named_group omits the operator on
    * purpose and the contract says an absent one means equals, so a blank string reaching the
    * refusal would break a caller that had done nothing wrong.
    */
   @Test
   void aBlankOperatorStillMeansEquals() {
      assertEquals(XCondition.EQUAL_TO, WorksheetMutationSupport.parseOperation("   "));
      assertEquals(XCondition.EQUAL_TO, WorksheetMutationSupport.parseOperation(""));
      assertEquals(XCondition.EQUAL_TO, WorksheetMutationSupport.parseOperation(null));
   }

   /**
    * PC-006 (bug corpus #76350): list_condition_operators/list_condition_date_ranges advertise
    * date_in (ConditionVocabulary maps it to XCondition.DATE_IN), but this worksheet-side parser
    * had no DATE_IN case at all -- add_filter/set_conditions rejected the exact operator name the
    * vocabulary endpoint told the caller was legal. Only the operator-name half is fixed here;
    * resolving a named range (e.g. "Last month") into a literal condition value is untraced and
    * intentionally not attempted by this test.
    */
   @Test
   void dateInIsAcceptedCaseInsensitively() {
      assertEquals(XCondition.DATE_IN, WorksheetMutationSupport.parseOperation("DATE_IN"));
      assertEquals(XCondition.DATE_IN, WorksheetMutationSupport.parseOperation("date_in"));
   }

   // =========================================================================
   // date_in value resolution (PC-006 follow-on item C)
   // =========================================================================

   private static XCondition firstXCondition(TableAssembly t) {
      return ((ConditionItem) t.getPreConditionList().getConditionList().getItem(0))
         .getXCondition();
   }

   /**
    * A builtin named range must resolve to the actual DateCondition the rest of the product uses
    * for it (mirroring ConditionUtil.fromModelToConditionList's DATE_IN branch) -- not a plain
    * Condition holding the raw string, which never carries date-range semantics through this
    * object shape.
    */
   @Test
   void addFilterWithDateInResolvesNamedRangeToADateCondition() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addFilter("T", "a", "date_in", "Last month"));

      XCondition xc = firstXCondition(t);
      assertInstanceOf(DateCondition.MonthCondition.class, xc,
         "date_in must resolve to a real DateCondition, not a plain Condition");
      DateCondition.MonthCondition mc = (DateCondition.MonthCondition) xc;
      assertEquals(0, mc.getYearN());
      assertEquals(1, mc.getMonthN());
   }

   /**
    * A builtin name in the wrong case (e.g. an LLM normalizing "Last month" to "LAST MONTH")
    * must still resolve, mirroring ConditionUtil.fromModelToConditionList's now-case-insensitive
    * builtin lookup -- not fail to match and fall through to an unresolved condition.
    */
   @Test
   void addFilterWithDateInResolvesANameInTheWrongCase() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addFilter("T", "a", "date_in", "LAST MONTH"));

      XCondition xc = firstXCondition(t);
      assertInstanceOf(DateCondition.MonthCondition.class, xc,
         "a wrong-cased builtin name must still resolve to a real DateCondition");
      DateCondition.MonthCondition mc = (DateCondition.MonthCondition) xc;
      assertEquals(0, mc.getYearN());
      assertEquals(1, mc.getMonthN());
   }

   /**
    * An unmatched/typo'd range name must fail loudly, naming the bad value -- not silently
    * substitute the shared rescue mechanism's hardcoded "one year ago" default (see
    * ConditionTest#toSqlConditionSilentlyDefaultsOnAnUnmatchedName for that default's own
    * characterization).
    */
   @Test
   void addFilterWithDateInRejectsAnUnknownRangeName() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
         svc.apply("TOK", agent, ed -> ed.addFilter("T", "a", "date_in", "not a real range")));

      assertTrue(thrown.getMessage().contains("not a real range"),
         "the refusal must name the bad value");
      assertNull(firstConditionOrNull(t), "nothing may be written when the value is rejected");
   }

   /**
    * A worksheet-level DateRangeAssembly is the reference implementation's second tier (after
    * built-in names) -- the gap the shared Condition.toSqlCondition(boolean, String) rescue
    * mechanism does not have.
    */
   @Test
   void addFilterWithDateInResolvesAWorksheetNamedDateRange() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);

      DateCondition.MonthCondition rangeCondition = new DateCondition.MonthCondition(2, 0);
      DefaultDateRangeAssembly range = new DefaultDateRangeAssembly(ws, "Recent Orders");
      range.setDateRange(rangeCondition);
      ws.addAssembly(range);

      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addFilter("T", "a", "date_in", "Recent Orders"));

      XCondition xc = firstXCondition(t);
      assertInstanceOf(DateCondition.MonthCondition.class, xc);
      DateCondition.MonthCondition mc = (DateCondition.MonthCondition) xc;
      assertEquals(0, mc.getYearN());
      assertEquals(2, mc.getMonthN());
      assertNotSame(rangeCondition, xc,
         "must be a clone -- the assembly's own DateRange must not be aliased into the condition");
   }

   /**
    * The worksheet DateRangeAssembly name match must not be case-sensitive -- an LLM might pass
    * the range's name in the wrong case, and it must still resolve via the case-insensitive
    * fallback scan (only reached once the exact-name lookup above it misses), mirroring
    * ConditionUtil.fromModelToConditionList's DATE_IN branch.
    */
   @Test
   void addFilterWithDateInResolvesAWorksheetNamedDateRangeInTheWrongCase() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);

      DateCondition.MonthCondition rangeCondition = new DateCondition.MonthCondition(2, 0);
      DefaultDateRangeAssembly range = new DefaultDateRangeAssembly(ws, "MyRange");
      range.setDateRange(rangeCondition);
      ws.addAssembly(range);

      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addFilter("T", "a", "date_in", "myrange"));

      XCondition xc = firstXCondition(t);
      assertInstanceOf(DateCondition.MonthCondition.class, xc,
         "a wrong-case worksheet DateRangeAssembly name must still resolve to a real " +
         "DateCondition, not fall through unresolved");
      DateCondition.MonthCondition mc = (DateCondition.MonthCondition) xc;
      assertEquals(0, mc.getYearN());
      assertEquals(2, mc.getMonthN());
   }

   /**
    * When two worksheet DateRangeAssemblies differ only by case, an exact-name match must win
    * over the case-insensitive fallback scan -- otherwise which one resolves depends on
    * {@code Worksheet#getAssemblies()} iteration order, which callers cannot rely on. This is
    * the determinism guarantee the exact-match-first ordering in the fix exists to provide.
    */
   @Test
   void addFilterWithDateInPrefersExactNameMatchOverCaseInsensitiveScan() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);

      DateCondition.MonthCondition exactRange = new DateCondition.MonthCondition(2, 0);
      DefaultDateRangeAssembly exact = new DefaultDateRangeAssembly(ws, "MyRange");
      exact.setDateRange(exactRange);
      ws.addAssembly(exact);

      DateCondition.MonthCondition otherCaseRange = new DateCondition.MonthCondition(5, 1);
      DefaultDateRangeAssembly otherCase = new DefaultDateRangeAssembly(ws, "MYRANGE");
      otherCase.setDateRange(otherCaseRange);
      ws.addAssembly(otherCase);

      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addFilter("T", "a", "date_in", "MyRange"));

      XCondition xc = firstXCondition(t);
      assertInstanceOf(DateCondition.MonthCondition.class, xc);
      DateCondition.MonthCondition mc = (DateCondition.MonthCondition) xc;
      assertEquals(0, mc.getYearN());
      assertEquals(2, mc.getMonthN(),
         "an exact-name match must be used, not whichever case-insensitive candidate the scan " +
         "happens to hit first");
   }

   /** Mirrors addFilter's resolution through set_conditions's separate condition-building path. */
   @Test
   void setConditionsWithDateInResolvesNamedRangeToADateCondition() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.setConditions("T", List.of(
         new WorksheetMutationSupport.ConditionNode(
            new WorksheetMutationSupport.ConditionSpec(
               "a", "date_in", List.of("Last month"), false, null), null, 0))));

      XCondition xc = firstXCondition(t);
      assertInstanceOf(DateCondition.MonthCondition.class, xc);
      DateCondition.MonthCondition mc = (DateCondition.MonthCondition) xc;
      assertEquals(0, mc.getYearN());
      assertEquals(1, mc.getMonthN());
   }

   /**
    * add_named_group/edit_named_group share buildGroupConditionList's condition-building logic
    * with no enum constraint on their operation field, so date_in is reachable here too -- the
    * same defect surface, fixed at the shared method.
    */
   @Test
   void buildGroupConditionListWithDateInResolvesNamedRangeToADateCondition() throws Exception {
      Worksheet ws = new Worksheet();
      DataRef conditionRef = new AttributeRef(null, "a");
      WorksheetMutationSupport.GroupMapping mapping =
         new WorksheetMutationSupport.GroupMapping("g1", List.of("Last month"), "date_in");

      ConditionList conds = WorksheetMutationSupport.buildGroupConditionList(
         XSchema.DATE, conditionRef, mapping, ws);

      XCondition xc = ((ConditionItem) conds.getItem(0)).getXCondition();
      assertInstanceOf(DateCondition.MonthCondition.class, xc);
      DateCondition.MonthCondition mc = (DateCondition.MonthCondition) xc;
      assertEquals(0, mc.getYearN());
      assertEquals(1, mc.getMonthN());
   }

   @Test
   void buildGroupConditionListWithDateInRejectsAnUnknownRangeName() {
      Worksheet ws = new Worksheet();
      DataRef conditionRef = new AttributeRef(null, "a");
      WorksheetMutationSupport.GroupMapping mapping = new WorksheetMutationSupport.GroupMapping(
         "g1", List.of("not a real range"), "date_in");

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
         WorksheetMutationSupport.buildGroupConditionList(XSchema.DATE, conditionRef, mapping, ws));

      assertTrue(thrown.getMessage().contains("not a real range"),
         "the refusal must name the bad value");
   }

   private static Object firstConditionOrNull(TableAssembly t) {
      ConditionListWrapper w = t.getPreConditionList();
      return w == null || w.isEmpty() ? null : firstCondition(t);
   }

   /** An omitted operator still means equals -- add_named_group leaves it out on purpose. */
   @Test
   void anOmittedOperatorStillMeansEquals() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addFilter("T", "a", null, "x"));

      assertEquals(XCondition.EQUAL_TO, firstCondition(t).getOperation());
   }

   // =========================================================================
   // L2 parity audit regression tests (2026-08-31 StyleBI-side fix batch)
   // =========================================================================

   @Test
   void setConditionsRejectsUnresolvableField() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed -> ed.setConditions("T", List.of(
            new WorksheetMutationSupport.ConditionNode(
               new WorksheetMutationSupport.ConditionSpec(
                  "NoSuchColumn", "=", List.of("x"), false, null),
               null, 0)))));

      assertTrue(ex.getMessage().contains("NoSuchColumn"));
      assertTrue(t.getPreConditionList() == null || t.getPreConditionList().isEmpty());
   }

   @Test
   void setRankingRejectsUnresolvableField() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed -> ed.setRanking("T",
            new WorksheetMutationSupport.RankingSpec("NoSuchColumn", 3, "TOP_N", false))));

      assertTrue(ex.getMessage().contains("NoSuchColumn"));
   }

   @Test
   void setRankingRejectsEmbeddedTable() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed -> ed.setRanking("T",
            new WorksheetMutationSupport.RankingSpec("a", 3, "TOP_N", false))));

      assertTrue(ex.getMessage().toLowerCase().contains("snapshot"));
   }

   @Test
   void setRankingRejectsUnrecognizedOperation() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
         svc.apply("TOK", agent, ed -> ed.setRanking("T",
            new WorksheetMutationSupport.RankingSpec("a", 3, "BOGUS_N", false))));

      assertTrue(ex.getMessage().contains("BOGUS_N"));
   }

   @Test
   void setRankingOfRejectsBooleanAggregate() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "employee", "isPaid");
      ColumnRef boolCol = (ColumnRef) t.getColumnSelection(false).getAttribute("isPaid");
      boolCol.setDataType(XSchema.BOOLEAN);
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
         svc.apply("TOK", agent, ed -> {
            ed.setGroupAggregate("T", groups("employee"),
               List.of(new WorksheetMutationSupport.AggregateSpec("isPaid", "MAX", null)));
            ed.setRanking("T",
               new WorksheetMutationSupport.RankingSpec("employee", 3, "TOP_N", false, "isPaid"));
         }));

      assertTrue(ex.getMessage().contains("isPaid"));
   }

   @Test
   void setRankingsEstablishesMultipleIndependentRankings() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "customer", "employee");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.setRankings("T", List.of(
         new WorksheetMutationSupport.RankingSpec("customer", 3, "TOP_N", false),
         new WorksheetMutationSupport.RankingSpec("employee", 2, "BOTTOM_N", false))));

      ConditionList cl = t.getRankingConditionList().getConditionList();
      assertEquals(3, cl.getSize(), "two rankings joined by one AND junction");
      assertEquals("customer", cl.getConditionItem(0).getAttribute().getAttribute());
      assertEquals("employee", cl.getConditionItem(2).getAttribute().getAttribute());
   }

   @Test
   void setConditionsFieldValueSpecComparesToAnotherColumn() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.setConditions("T", List.of(
         new WorksheetMutationSupport.ConditionNode(
            new WorksheetMutationSupport.ConditionSpec("a", ">", null, false, null,
               List.of(new WorksheetMutationSupport.ConditionValueSpec("field", "b", null, null))),
            null, 0))));

      Condition c = firstCondition(t);
      assertTrue(c.getValue(0) instanceof DataRef,
         "a 'field' valueSpec must resolve to a DataRef, got: " + c.getValue(0));
      assertEquals("b", ((DataRef) c.getValue(0)).getAttribute());
   }

   /**
    * Review finding on PR #4920: {@code conditionValue}'s FIELD branch resolves
    * {@code spec.field()} via {@code resolveField}, which falls back to an unresolvable
    * {@code new AttributeRef(null, field)} placeholder for a name that matches nothing --
    * exactly the silent failure {@code requireConditionFields} exists to prevent for
    * {@code node.condition().field()}. This proves a typo'd/nonexistent column name in a
    * FIELD-typed {@code valueSpecs()} entry (a column-vs-column comparison condition's value
    * side) is now rejected the same way, instead of silently producing a broken placeholder
    * ref.
    */
   @Test
   void setConditionsRejectsUnresolvableFieldValueSpec() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed -> ed.setConditions("T", List.of(
            new WorksheetMutationSupport.ConditionNode(
               new WorksheetMutationSupport.ConditionSpec("a", ">", null, false, null,
                  List.of(new WorksheetMutationSupport.ConditionValueSpec(
                     "field", "typo'd_column", null, null))),
               null, 0)))));

      assertTrue(ex.getMessage().contains("typo'd_column"));
      assertTrue(t.getPreConditionList() == null || t.getPreConditionList().isEmpty());
   }

   @Test
   void setConditionsExpressionValueSpecBuildsExpressionValue() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.setConditions("T", List.of(
         new WorksheetMutationSupport.ConditionNode(
            new WorksheetMutationSupport.ConditionSpec("a", ">", null, false, null,
               List.of(new WorksheetMutationSupport.ConditionValueSpec(
                  "expression", null, "field['b']*2", "js"))),
            null, 0))));

      Condition c = firstCondition(t);
      assertTrue(c.getValue(0) instanceof ExpressionValue,
         "an 'expression' valueSpec must build an ExpressionValue, got: " + c.getValue(0));
      ExpressionValue expr = (ExpressionValue) c.getValue(0);
      assertEquals("field['b']*2", expr.getExpression());
      assertEquals(ExpressionValue.JAVASCRIPT, expr.getType());
   }

   @Test
   void setConditionsChoiceQueryAppliesToVariableValue() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.setConditions("T", List.of(
         new WorksheetMutationSupport.ConditionNode(
            new WorksheetMutationSupport.ConditionSpec("a", "=", List.of("$(myVar)"), false,
               null, null, "T]:[a"),
            null, 0))));

      Condition c = firstCondition(t);
      assertTrue(c.getValue(0) instanceof UserVariable,
         "a $(name) value must still resolve to a UserVariable, got: " + c.getValue(0));
      assertEquals("T]:[a", ((UserVariable) c.getValue(0)).getChoiceQuery());
   }

   @Test
   void setGroupAggregateRejectsSameColumnAsGroupAndAggregate() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "customer");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed -> ed.setGroupAggregate("T", groups("customer"),
            List.of(new WorksheetMutationSupport.AggregateSpec("customer", "COUNT", null)))));

      assertTrue(ex.getMessage().contains("customer"));
   }

   @Test
   void setGroupAggregateAppliesTimeSeriesAndClearsConflictingSort() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "orderDate");
      ColumnRef dateCol = (ColumnRef) t.getColumnSelection(false).getAttribute("orderDate");
      dateCol.setDataType(XSchema.DATE);
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> {
         // First call creates the DateRangeRef-wrapped "Year(orderDate)" group column --
         // matching AggregateDialogService.java:455-467, the conflicting-sort check looks
         // for a sort on THAT wrapped column, not the raw source column, so the sort has to
         // be set on it, simulating a second edit that finds an already-time-series group
         // with a sort a prior call (or the native dialog) placed on the bucketed column.
         ed.setGroupAggregate("T",
            List.of(new WorksheetMutationSupport.GroupSpec("orderDate", "YEAR", true)),
            List.of());
         String wrappedName = t.getAggregateInfo().getGroup(0).getDataRef().getName();
         ed.setSort("T", wrappedName, "ASC");
         ed.setGroupAggregate("T",
            List.of(new WorksheetMutationSupport.GroupSpec("orderDate", "YEAR", true)),
            List.of());
      });

      AggregateInfo ai = t.getAggregateInfo();
      assertTrue(ai.getGroup(0).isTimeSeries());
      assertTrue(t.getSortInfo() == null || t.getSortInfo().getSorts().length == 0,
         "a time-series group must clear a pre-existing sort on the bucketed date column");
   }

   @Test
   void setGroupAggregateAppliesSecondaryColumn() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "amount", "weight");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.setGroupAggregate("T", groups(),
         List.of(new WorksheetMutationSupport.AggregateSpec(
            "amount", "WEIGHTED AVG", null, null, "weight", null))));

      AggregateInfo ai = t.getAggregateInfo();
      AggregateRef ar = ai.getAggregate(0);
      assertNotNull(ar.getSecondaryColumn());
      assertEquals("weight", ar.getSecondaryColumn().getAttribute());
   }

   @Test
   void setGroupAggregateAppliesPercentageOfGrandTotal() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "customer", "amount");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.setGroupAggregate("T", groups("customer"),
         List.of(new WorksheetMutationSupport.AggregateSpec(
            "amount", "SUM", null, null, null, "grand_total"))));

      AggregateInfo ai = t.getAggregateInfo();
      AggregateRef ar = ai.getAggregate(0);
      assertTrue(ar.isPercentage());
      assertEquals(inetsoft.uql.XConstants.PERCENTAGE_OF_GRANDTOTAL, ar.getPercentageOption());
   }

   @Test
   void setSortDirectionOnlyChangePreservesPriority() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> {
         ed.setSort("T", "a", "ASC");
         ed.setSort("T", "b", "ASC");
         ed.setSort("T", "a", "DESC"); // direction-only change on the FIRST column
      });

      inetsoft.uql.asset.SortRef[] sorts = t.getSortInfo().getSorts();
      assertEquals(2, sorts.length);
      assertEquals("a", sorts[0].getAttribute(),
         "a direction-only change must not move the column to last priority");
      assertEquals(XConstants.SORT_DESC, sorts[0].getOrder());
      assertEquals("b", sorts[1].getAttribute());
   }

   @Test
   void setSortRejectsUnresolvableField() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed -> ed.setSort("T", "NoSuchColumn", "ASC")));

      assertTrue(ex.getMessage().contains("NoSuchColumn"));
   }

   @Test
   void editCellRejectsUnparsableValueAndKeepsPriorValue() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = new EmbeddedTableAssembly(ws, "T");
      t.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.STRING }, new Object[][] { { "n" }, { "42" } }));
      ((ColumnRef) t.getColumnSelection(false).getAttribute("n")).setDataType(XSchema.INTEGER);
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed -> ed.editCell("T", 0, 0, "not-a-number")));

      assertNotNull(ex.getMessage(), "must be a clean PairingException, not a raw parse exception");
      assertEquals("42", t.getEmbeddedData().getObject(1, 0),
         "a rejected parse must leave the prior cell value untouched");
   }

   @Test
   void editCellRejectsValueOverMaxCellSize() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = new EmbeddedTableAssembly(ws, "T");
      t.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.STRING }, new Object[][] { { "s" }, { "orig" } }));
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");
      String tooLong = "x".repeat(inetsoft.report.internal.Util.getOrganizationMaxCellSize() + 1);

      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed -> ed.editCell("T", 0, 0, tooLong)));

      assertTrue(ex.getMessage().contains("character"));
      assertEquals("orig", t.getEmbeddedData().getObject(1, 0));
   }

   // =========================================================================
   // L2-Group7 — editVariable type/default-value validation, deleteVariable
   // dependency block
   // =========================================================================

   @Test
   void editVariableRejectsUnrecognizedType() throws Exception {
      Worksheet ws = new Worksheet();
      variableAssembly(ws, "region", XSchema.STRING);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // L2-Group7: XSchema.createPrimitiveType returns null, with no exception and no log, for
      // a type string the Composer's own (closed, 12-value) Type dropdown could never submit --
      // editVariable previously stored that null type node without complaint.
      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed -> ed.editVariable("region", "varchar", null, null, null)));
      assertTrue(ex.getMessage().contains("varchar"), ex.getMessage());

      AssetVariable updated = ((DefaultVariableAssembly) ws.getAssembly("region")).getVariable();
      assertEquals(XSchema.STRING, updated.getTypeNode().getType(),
         "a rejected type change must not leave the variable's type altered");
   }

   @Test
   void editVariableRejectsUnparsableDefaultValue() throws Exception {
      Worksheet ws = new Worksheet();
      DefaultVariableAssembly assembly = variableAssembly(ws, "topN", XSchema.INTEGER);
      // setTypeNode (inside variableAssembly's setup) auto-creates a placeholder value node
      // whenever none exists yet, so the baseline here is that placeholder, not null -- capture
      // it by reference to prove the rejected call didn't replace it with a new one, matching
      // editVariableRejectsInvalidChoicesWithoutApplyingLabelTypeOrDefaultValue's pattern above.
      Object originalValueNode = assembly.getVariable().getValueNode();
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      // L2-Group7: previously fell back to storing the raw, unparsed string ("not_a_number")
      // as the declared-integer variable's default value instead of failing loud.
      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed ->
            ed.editVariable("topN", null, null, "not_a_number", null)));
      assertTrue(ex.getMessage().contains("not_a_number"), ex.getMessage());

      AssetVariable updated = ((DefaultVariableAssembly) ws.getAssembly("topN")).getVariable();
      assertSame(originalValueNode, updated.getValueNode(),
         "a rejected default value must not leave a partial value node applied");
   }

   @Test
   void deleteVariableRejectsWhenReferencedByACondition() throws Exception {
      Worksheet ws = new Worksheet();
      variableAssembly(ws, "minTotal", XSchema.DOUBLE);
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.addFilter("T", "a", ">", "$(minTotal)"));

      // L2-Group7: mirrors WSRemoveAssembliesService.removeAssemblies, which refuses to delete
      // an assembly AssetEventUtil.hasDependent() reports as still referenced instead of
      // deleting it and leaving a dangling $(minTotal) reference behind.
      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed -> ed.deleteVariable("minTotal")));
      assertTrue(ex.getMessage().contains("minTotal"), ex.getMessage());
      assertNotNull(ws.getAssembly("minTotal"), "a referenced variable must not be deleted");
      assertFalse(t.getPreConditionList().isEmpty(),
         "the referencing condition must be left untouched");
   }

   @Test
   void deleteVariableSucceedsWhenNotReferenced() throws Exception {
      Worksheet ws = new Worksheet();
      variableAssembly(ws, "unused", XSchema.STRING);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(rws(ws), "Worksheet/ws1", agent, "TOK");

      svc.apply("TOK", agent, ed -> ed.deleteVariable("unused"));

      assertNull(ws.getAssembly("unused"));
   }
}
