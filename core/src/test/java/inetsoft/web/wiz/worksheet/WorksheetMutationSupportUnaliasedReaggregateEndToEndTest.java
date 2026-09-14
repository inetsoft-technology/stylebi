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

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.IntegrationTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WBS-046 (Redmine #76627) end-to-end coverage, using a REAL (non-mocked) {@link AssetQuerySandbox}
 * so the actual query engine runs, not just {@code applyAggregateInfo}'s own {@link
 * inetsoft.uql.asset.AggregateInfo} bookkeeping.
 *
 * <p><b>What this file demonstrates, and what it does NOT settle</b> (see {@code bug-wbs046/03-fix.md}
 * for the full writeup): the live 2026-09-12 finding (recorded in
 * {@code bug-wbs046/01-diagnosis.md}/{@code 02-refute.md} in the sibling debug-workflow worktree,
 * and originally in {@code docs/bug-reports/plugin-composer-worksheet-cond-agg-expr.md:939-1004})
 * reported that reusing an un-aliased first aggregate's raw output name as a second, un-mirrored
 * {@code set_group_aggregate} call's field COLLAPSES every output group to the same constant (the
 * fully-ungrouped grand aggregate) against a live, JDBC-backed StyleBI datasource (Examples/Orders).
 * {@link #rawColumnReuseAfterUnaliasedFirstAggregateStaysLegitimateAndDifferentiatedPerGroup} below
 * reproduces the IDENTICAL call shape against an in-memory {@link EmbeddedTableAssembly} instead
 * (no live datasource available to this fixer -- see {@code bug-wbs046/03-fix.md}) and finds it
 * computes CORRECTLY, differentiated per group -- NOT the collapse. Since an embedded table's
 * aggregate is always evaluated in Java post-process and never pushed into a SQL {@code GROUP BY}
 * (unlike a JDBC-backed table, where {@code PreAssetQuery.mergeGroupBy()} merges the grouping into
 * the generated SQL), this test's clean pass is evidence -- not proof -- that the live "collapse to
 * grand constant" mechanism is specific to the SQL-merge path and is a SEPARATE, still-open defect
 * from the alias-tracking gap this task's fix (see {@code WorksheetMutationSupport.applyAggregateInfo}'s
 * "First aggregate on this column" branch, and
 * {@code WorksheetEditServiceMutatorsTest#reAggregatingSameTableWithAutoAliasedOutputNowFailsLoudOnChaining})
 * addresses.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetMutationSupportUnaliasedReaggregateEndToEndTest {
   private static EmbeddedTableAssembly buildTable(Worksheet ws) {
      EmbeddedTableAssembly t = new EmbeddedTableAssembly(ws, "T");
      t.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.STRING, XSchema.STRING, XSchema.DOUBLE },
         new Object[][] {
            { "A", "B", "C" },
            { "X", "p", 10.0 }, { "X", "p", 5.0 },
            { "X", "q", 30.0 }, { "X", "q", 20.0 },
            { "Y", "p", 100.0 }, { "Y", "p", 1.0 },
            { "Y", "q", 5.0 }, { "Y", "q", 2.0 },
         }));
      ws.addAssembly(t);
      return t;
   }

   /**
    * The literal WBS-046 repro shape: a caller who never learns about the fix's new auto-generated
    * alias and keeps referencing the column by its ORIGINAL raw name ("C") on the second,
    * un-mirrored call. Per {@code WorksheetMutationSupport.clearAggregateAliases}, this auto alias
    * is cleared before the second call resolves its fields, so "C" legitimately re-resolves to the
    * raw column -- the SAME "sanctioned reuse" path
    * {@code WorksheetEditServiceMutatorsTest#reAggregatingSameTableClearsStalePriorAlias}'s third
    * call already exercises for an explicitly-aliased first aggregate. This test's own value is
    * the ACTUAL COMPUTED RESULT: does the fresh single-stage aggregate the second call builds over
    * the raw rows correctly differentiate per group, or does it collapse to one grand constant (the
    * live-reported symptom)? See this file's class Javadoc for what a clean pass here does and does
    * not settle.
    */
   @Test
   void rawColumnReuseAfterUnaliasedFirstAggregateStaysLegitimateAndDifferentiatedPerGroup()
      throws Exception
   {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = buildTable(ws);

      // First pass: group by [A, B], Sum(C) -- deliberately NO alias.
      WorksheetMutationSupport.applyAggregateInfo(t,
         List.of(new WorksheetMutationSupport.GroupSpec("A"),
                 new WorksheetMutationSupport.GroupSpec("B")),
         List.of(new WorksheetMutationSupport.AggregateSpec("C", "SUM", null)));

      TableLens firstLens = new AssetQuerySandbox(ws, null, new VariableTable())
         .getTableLens("T", AssetQuerySandbox.RUNTIME_MODE);
      firstLens.moreRows(TableLens.EOT);
      assertEquals("C_1", firstLens.getObject(0, 2),
         "precondition (post-fix): the aggregate output is now auto-aliased \"C_1\" instead of " +
         "keeping the raw column's own bare name \"C\"");

      // Second pass on the SAME table (no mirror), grouped by [A] only, referencing the ORIGINAL
      // raw "C" field again -- not the new "C_1" alias, matching a caller who never re-read the
      // model between calls (the literal WBS-046 repro shape).
      WorksheetMutationSupport.applyAggregateInfo(t,
         List.of(new WorksheetMutationSupport.GroupSpec("A")),
         List.of(new WorksheetMutationSupport.AggregateSpec("C", "NthLargest", null, 2)));

      TableLens secondLens = new AssetQuerySandbox(ws, null, new VariableTable())
         .getTableLens("T", AssetQuerySandbox.RUNTIME_MODE);
      secondLens.moreRows(TableLens.EOT);

      Map<Object, Object> byGroup = new HashMap<>();

      for(int r = 1; r < secondLens.getRowCount(); r++) {
         byGroup.put(secondLens.getObject(r, 0), secondLens.getObject(r, 1));
      }

      System.out.println("[WBS-046] second-call per-group 2nd-largest(C) values: " + byGroup);

      // CORRECT (and what this test actually observes, both before and after this task's fix): a
      // fresh single-stage NthLargest(C, 2) computed straight over the 8 raw rows, grouped by the
      // new (coarser) grouping alone -- X: {10,5,30,20} -> 20; Y: {100,1,5,2} -> 5. Differentiated
      // per group.
      //
      // The live-reported symptom (every group returns 30.0 instead -- the 2nd-largest C value
      // across ALL 8 raw rows, i.e. the fully ungrouped grand aggregate) does NOT reproduce here --
      // see this file's class Javadoc for what that does and does not establish.
      assertEquals(2, byGroup.size(), "expected one differentiated row per distinct A group");
      assertEquals(20.0, ((Number) byGroup.get("X")).doubleValue(), 0.01,
         "X's own 2nd-largest raw C value (10,5,30,20 -> 20), not the grand 2nd-largest (30)");
      assertEquals(5.0, ((Number) byGroup.get("Y")).doubleValue(), 0.01,
         "Y's own 2nd-largest raw C value (100,1,5,2 -> 5), not the grand 2nd-largest (30)");
   }
}
