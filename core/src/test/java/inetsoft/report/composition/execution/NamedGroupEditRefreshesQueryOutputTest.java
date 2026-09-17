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
package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.IntegrationTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.worksheet.WorksheetEditService;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #76731-WBS-051, fix-round addendum (review request): runs the same
 * {@code editNamedGroupRefreshesStandaloneLeafTableGroupRefImmediately} fixture (standalone
 * leaf table, named group edited via {@code editNamedGroup}) through a real, non-mocked
 * {@link AssetQuerySandbox}/{@link AssetQuery}/{@link TableLens} pipeline -- the same layer
 * {@link AssetQuery#createSortOrder}/{@code createDateSortOrder} (AssetQuery.java:3841,3892)
 * read {@code GroupRef.getNamedGroupInfo()} to build the query's grouping -- and asserts on the
 * actual rendered group labels/aggregates, not just {@code GroupRef}'s cached object state.
 *
 * <p><b>Read this before trusting this test as a regression guard for the bug:</b> it is
 * NOT one. Verified (revert-and-rerun, both directions) that this assertion passes identically
 * with and without the {@code editNamedGroup} sweep fix in
 * {@code WorksheetEditService.editNamedGroup()}. The reason is a second, independent, already-
 * existing self-heal mechanism this bug's diagnosis/refutation never exercised (both stopped at
 * inspecting {@code GroupRef.getNamedGroupInfo()} directly, never actually built a real
 * {@link AssetQuery}): every concrete {@link AssetQuery} subclass's constructor
 * unconditionally calls {@code this.table.update()} on the exact table it is querying --
 * {@code EmbeddedQuery.java:61}, and identically in {@code BoundQuery}/{@code JoinQuery}/
 * {@code MirrorQuery}/{@code ConcatenatedQuery}/etc. -- which re-runs
 * {@code AbstractTableAssembly.update()} -> {@code AggregateInfo.update(ws)} ->
 * {@code GroupRef.update(ws)} on that table's own groups, before any {@code SortOrder} is ever
 * built from them. So building a query FOR a table always self-heals that table's own
 * {@code GroupRef}s first, regardless of composite membership and regardless of whether this
 * PR's sweep exists -- the staleness this PR fixes is only ever observable by code that reads
 * {@code GroupRef.getNamedGroupInfo()} directly off a table object without first (re-)querying
 * it, exactly what the three {@code WorksheetEditServiceTest} regression tests for this bug do.
 * Kept as a positive, real-query-engine confirmation that the post-fix worksheet state renders
 * correctly end-to-end (useful documentation of this self-heal boundary for future readers), not
 * as an additional regression discriminator -- see {@code 04-fix-r1.md} for the full writeup and
 * why no test at this exact layer can discriminate for this specific defect.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class NamedGroupEditRefreshesQueryOutputTest {

   private static TableLens run(Worksheet ws, TableAssembly table) throws Exception {
      AssetQuerySandbox box = new AssetQuerySandbox(ws, null, new VariableTable());
      box.setActive(true);

      AssetQuery query = AssetQuery.createAssetQuery(
         table, AssetQuerySandbox.LIVE_MODE, box, false, -1L, true, false);
      TableLens lens = query.getTableLens(new VariableTable());
      lens.moreRows(TableLens.EOT);
      return lens;
   }

   /** Maps each distinct value in column 0 (the grouped "state" column) to its column-1 sum. */
   private static Map<Object, Object> groupTotals(TableLens lens) {
      Map<Object, Object> totals = new HashMap<>();

      for(int r = 1; r < lens.getRowCount(); r++) {
         totals.put(lens.getObject(r, 0), lens.getObject(r, 1));
      }

      return totals;
   }

   @Test
   void editNamedGroupRenameIsReflectedInRealQueryOutput() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t1 = new EmbeddedTableAssembly(ws, "T1");
      ColumnSelection cs = new ColumnSelection();
      ColumnRef stateRef = new ColumnRef(new AttributeRef("state"));
      ColumnRef amountRef = new ColumnRef(new AttributeRef("amount"));
      cs.addAttribute(stateRef);
      cs.addAttribute(amountRef);
      t1.setColumnSelection(cs, false);
      t1.setEmbeddedData(new XEmbeddedTable(new String[]{ "string", "double" },
         new Object[][]{
            { "state", "amount" },
            { "CA", 100.0 },
            { "CA", 50.0 },
            { "NY", 30.0 },
         }));
      ws.addAssembly(t1);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = new JoinSession("TOK", "Worksheet/foo-7", "alice~;~host-org",
                                     SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                     JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      svc.apply("TOK", agent,
                ed -> ed.addNamedGroup("G", null, null, "string",
                                       List.of(new WorksheetMutationSupport.GroupMapping(
                                          "West", List.of("CA"))),
                                       false));
      svc.apply("TOK", agent,
                ed -> ed.setGroupAggregate("T1",
                   List.of(new WorksheetMutationSupport.GroupSpec("state", null, "G")),
                   List.of(new WorksheetMutationSupport.AggregateSpec("amount", "Sum", "total"))));

      // Same rename this bug's other tests exercise at the GroupRef object-graph level --
      // here, run the real query engine against the result instead.
      svc.apply("TOK", agent,
                ed -> ed.editNamedGroup("G",
                                        List.of(new WorksheetMutationSupport.GroupMapping(
                                           "East", List.of("CA"))),
                                        false));

      TableAssembly table = (TableAssembly) ws.getAssembly("T1");
      Map<Object, Object> totals = groupTotals(run(ws, table));

      assertEquals(Map.of("East", 150.0, "NY", 30.0), totals,
         "real query output must reflect the renamed group (\"East\", summing both CA rows to " +
         "150) immediately after editNamedGroup -- not the stale pre-edit \"West\" label the " +
         "GroupRef's cached NamedGroupInfo clone would have produced without the fix");
   }
}
