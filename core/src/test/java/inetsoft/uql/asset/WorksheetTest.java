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
package inetsoft.uql.asset;

import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.web.wiz.pairing.TestWorksheets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import inetsoft.web.wiz.pairing.WizAgentTestSupport;

@WizAgentTestSupport
class WorksheetTest {

   /**
    * Regression for a null/blank-named assembly permanently poisoning the lazy name cache:
    * createCache() iterates every assembly and calls {@code ConcurrentHashMap.put(name, ...)},
    * which throws NPE on a null key before {@code this.amap} is reassigned, so once a
    * null-named assembly is present every future getAssembly() call (on ANY name) retries and
    * fails the same way, forever. addAssembly() must refuse a null/blank name outright so it
    * never reaches the assemblies list.
    */
   @Test
   void addAssemblyRejectsNullNameAndDoesNotPoisonLookupForOtherAssemblies() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "Orders", "id");
      ws.addAssembly(t);

      EmbeddedTableAssembly nullNamed = new EmbeddedTableAssembly(ws, null);
      assertFalse(ws.addAssembly(nullNamed));

      assertSame(t, ws.getAssembly("Orders"));
      assertEquals(1, ws.getAssemblies().length);
   }

   @Test
   void addAssemblyRejectsBlankName() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly blankNamed = new EmbeddedTableAssembly(ws, "  ");
      assertFalse(ws.addAssembly(blankNamed));
      assertEquals(0, ws.getAssemblies().length);
   }

   /**
    * Regression for bug 76796: renaming a worksheet table silently discarded its own
    * {@code set_group_aggregate} config when the table was added from a logical-model entity
    * (e.g. {@code add_table(datasource, logicalModel, table)}). Such a table's columns have
    * their {@link AttributeRef} entity set to the logical-model entity name
    * (see {@code WorksheetAgentController#addLogicalModelTable}), which is *also*, by the
    * default worksheet-naming convention, the table's own initial assembly name -- a
    * coincidence, not a semantic link (the entity denotes the logical-model source, never the
    * worksheet assembly). {@code renameAssembly}'s rename-cascade (intended to requalify a
    * group/aggregate ref that points AT some other, renamed source table -- e.g. a join/mirror
    * dependency) must not also fire on the renamed table's own aggregate, or it corrupts the
    * ref's entity into something that no longer resolves against the logical model at all.
    */
   @Test
   void renameAssemblyDoesNotRequalifyItsOwnAggregateRefOnSelfRename() {
      Worksheet ws = new Worksheet();
      BoundTableAssembly table = new BoundTableAssembly(ws, "Product");
      table.setSourceInfo(new SourceInfo(SourceInfo.MODEL, "Examples/Orders", "Return Model"));

      inetsoft.uql.ColumnSelection cs = new inetsoft.uql.ColumnSelection();
      ColumnRef totalColumn = new ColumnRef(new AttributeRef("Product", "Total"));
      cs.addAttribute(totalColumn);
      table.setColumnSelection(cs, false);

      AggregateInfo ginfo = new AggregateInfo();
      // Wraps the same ColumnRef object living in the table's own column selection, matching
      // WorksheetMutationSupport#applyAggregateInfo's no-clone sharing for the primary
      // aggregate column.
      ginfo.addAggregate(new AggregateRef(totalColumn, AggregateFormula.SUM));
      table.setAggregateInfo(ginfo);

      ws.addAssembly(table);

      assertTrue(ws.renameAssembly("Product", "ReturnTotalSummary", true));

      AggregateInfo renamed = table.getAggregateInfo();
      assertEquals(1, renamed.getAggregateCount(),
         "rename_table must not silently drop the aggregate");
      DataRef aggregateRef = renamed.getAggregate(0).getDataRef();
      assertEquals("Product", aggregateRef.getEntity(),
         "the aggregate's underlying column denotes the logical-model entity, not the " +
         "worksheet assembly's own name -- renaming the assembly must not requalify it");
      assertEquals("Total", aggregateRef.getAttribute());

      // The raw (non-aggregated) column selection shares the same ColumnRef object and must
      // remain resolvable too, not just the aggregate ref.
      assertEquals("Product", totalColumn.getEntity());
   }

   /**
    * Regression for bug 76900: {@code ComposedTableAssembly#renameAggregateInfo} (covering
    * {@code MirrorTableAssembly}, join-derived tables, and {@code ConcatenatedTableAssembly})
    * overrides the base method fixed by bug 76796 and correctly calls {@code super()}, but then
    * re-walks its own {@code ginfo} groups/aggregates via {@code ColumnRef.renameColumn} with no
    * self-rename guard of its own -- reopening the same corruption the 76796 guard closed for
    * non-composed tables, just for a mirror/join/concatenated table whose own aggregate column
    * entity happens to equal its own current name.
    */
   @Test
   void renameAssemblyDoesNotRequalifyComposedTablesOwnAggregateRefOnSelfRename() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly source = TestWorksheets.tableWithColumns(ws, "SO", "amount");
      ws.addAssembly(source);

      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M", source);
      inetsoft.uql.ColumnSelection cs = new inetsoft.uql.ColumnSelection();
      ColumnRef amountColumn = new ColumnRef(new AttributeRef("M", "amount"));
      cs.addAttribute(amountColumn);
      mirror.setColumnSelection(cs, false);

      AggregateInfo ginfo = new AggregateInfo();
      ginfo.addAggregate(new AggregateRef(amountColumn, AggregateFormula.SUM));
      mirror.setAggregateInfo(ginfo);
      ws.addAssembly(mirror);

      assertTrue(ws.renameAssembly("M", "M2", true));

      AggregateInfo renamed = mirror.getAggregateInfo();
      assertEquals(1, renamed.getAggregateCount(),
         "rename_table must not silently drop the composed table's own aggregate");
      DataRef aggregateRef = renamed.getAggregate(0).getDataRef();
      assertEquals("M", aggregateRef.getEntity(),
         "the composed table's own aggregate column entity must not be requalified to the " +
         "table's own new name on self-rename");
      assertEquals("amount", aggregateRef.getAttribute());
   }

   /**
    * Regression for bug 76900: {@code ComposedTableAssembly#renameConditionListWrapper} is
    * overridden to requalify entity-qualified refs inside conditions (including a
    * {@code RankingCondition}'s data ref, e.g. a "Top N by Sum(x)" ranking on {@code topns}),
    * and that override is invoked unconditionally from {@code super.renameDepended()} before
    * this table's own self-rename guards run. A ranking condition's data ref commonly shares
    * the exact same {@code AggregateRef} instance as the table's own {@code AggregateInfo}
    * aggregate (mirroring {@code WorksheetMutationSupport#applyAggregateInfo}'s no-clone ref
    * sharing), so without its own guard this path re-corrupts that shared ref on self-rename
    * even though the {@code renameAggregateInfo} and {@code renameDepended} guards above already
    * protect the AggregateInfo/column-selection paths.
    */
   @Test
   void renameAssemblyDoesNotRequalifySharedRankingConditionRefOnSelfRename() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly source = TestWorksheets.tableWithColumns(ws, "SO", "amount");
      ws.addAssembly(source);

      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M", source);
      inetsoft.uql.ColumnSelection cs = new inetsoft.uql.ColumnSelection();
      ColumnRef amountColumn = new ColumnRef(new AttributeRef("M", "amount"));
      cs.addAttribute(amountColumn);
      mirror.setColumnSelection(cs, false);

      AggregateRef aggregateRef = new AggregateRef(amountColumn, AggregateFormula.SUM);
      AggregateInfo ginfo = new AggregateInfo();
      ginfo.addAggregate(aggregateRef);
      mirror.setAggregateInfo(ginfo);

      RankingCondition ranking = new RankingCondition();
      ranking.setN(5);
      ranking.setOperation(RankingCondition.TOP_N);
      // Shares the same AggregateRef instance as the AggregateInfo above, matching how
      // WorksheetMutationSupport wires a "Top N" ranking to the table's own aggregate.
      ranking.setDataRef(aggregateRef);
      inetsoft.uql.ConditionList topns = new inetsoft.uql.ConditionList();
      topns.append(new inetsoft.uql.ConditionItem(amountColumn, ranking, 0));
      mirror.setRankingConditionList(topns);

      ws.addAssembly(mirror);

      assertTrue(ws.renameAssembly("M", "M2", true));

      DataRef rankingRef = ranking.getDataRef();
      assertTrue(rankingRef instanceof AggregateRef, "ranking condition's data ref type must " +
         "be preserved");
      DataRef rankedColumn = ((AggregateRef) rankingRef).getDataRef();
      assertEquals("M", rankedColumn.getEntity(),
         "the ranking condition's shared aggregate column entity must not be requalified to " +
         "the table's own new name on self-rename");
      assertEquals("amount", rankedColumn.getAttribute());
   }

   /**
    * Companion to the self-rename case above: a *different* table's group/aggregate that
    * legitimately depends on the renamed table (the join/mirror scenario the rename-cascade
    * mechanism exists for, added in commit 28a5ede3b) must still be requalified correctly --
    * the self-rename guard must not have broken this cross-table case.
    */
   @Test
   void renameAssemblyStillRequalifiesAnotherTablesAggregateThatDependsOnTheRenamedTable() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly source = TestWorksheets.tableWithColumns(ws, "SO", "amount");
      ws.addAssembly(source);

      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M", source);
      AggregateInfo ginfo = new AggregateInfo();
      ColumnRef amountColumn = new ColumnRef(new AttributeRef("SO", "amount"));
      ginfo.addAggregate(new AggregateRef(amountColumn, AggregateFormula.SUM));
      mirror.setAggregateInfo(ginfo);
      ws.addAssembly(mirror);

      assertTrue(ws.renameAssembly("SO", "SalesOrder", true));

      AggregateInfo mirrorInfo = mirror.getAggregateInfo();
      assertEquals(1, mirrorInfo.getAggregateCount(),
         "a dependent table's aggregate on a genuinely renamed source table must survive");
      DataRef aggregateRef = mirrorInfo.getAggregate(0).getDataRef();
      assertEquals("SalesOrder", aggregateRef.getEntity(),
         "the dependent aggregate must be requalified to the source table's NEW name");
      assertEquals("amount", aggregateRef.getAttribute());
   }

   /**
    * Cross-table companion for the {@code renameColumnSelection}/{@code renameSortInfo} guard
    * added to {@code ComposedTableAssembly#renameDepended} for bug 76900: a different table's
    * sort ref that legitimately depends on the renamed table must still be requalified.
    */
   @Test
   void renameAssemblyStillRequalifiesAnotherTablesSortRefThatDependsOnTheRenamedTable() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly source = TestWorksheets.tableWithColumns(ws, "SO", "amount");
      ws.addAssembly(source);

      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M", source);
      ColumnRef amountColumn = new ColumnRef(new AttributeRef("SO", "amount"));
      SortInfo sortInfo = new SortInfo();
      sortInfo.addSort(new SortRef(amountColumn));
      mirror.setSortInfo(sortInfo);
      ws.addAssembly(mirror);

      assertTrue(ws.renameAssembly("SO", "SalesOrder", true));

      SortRef[] sorts = mirror.getSortInfo().getSorts();
      assertEquals(1, sorts.length,
         "a dependent table's sort ref on a genuinely renamed source table must survive");
      assertEquals("SalesOrder", sorts[0].getDataRef().getEntity(),
         "the dependent sort ref must be requalified to the source table's NEW name");
      assertEquals("amount", sorts[0].getDataRef().getAttribute());
   }

   /**
    * Cross-table companion for the {@code renameConditionListWrapper} guard added to
    * {@code ComposedTableAssembly} for bug 76900: a different table's ranking condition that
    * legitimately depends on the renamed table must still be requalified.
    */
   @Test
   void renameAssemblyStillRequalifiesAnotherTablesRankingConditionThatDependsOnTheRenamedTable() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly source = TestWorksheets.tableWithColumns(ws, "SO", "amount");
      ws.addAssembly(source);

      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M", source);
      ColumnRef amountColumn = new ColumnRef(new AttributeRef("SO", "amount"));
      AggregateRef aggregateRef = new AggregateRef(amountColumn, AggregateFormula.SUM);
      RankingCondition ranking = new RankingCondition();
      ranking.setN(5);
      ranking.setDataRef(aggregateRef);
      inetsoft.uql.ConditionList topns = new inetsoft.uql.ConditionList();
      topns.append(new inetsoft.uql.ConditionItem(amountColumn, ranking, 0));
      mirror.setRankingConditionList(topns);
      ws.addAssembly(mirror);

      assertTrue(ws.renameAssembly("SO", "SalesOrder", true));

      DataRef rankedColumn = ((AggregateRef) ranking.getDataRef()).getDataRef();
      assertEquals("SalesOrder", rankedColumn.getEntity(),
         "the dependent ranking condition's ref must be requalified to the source table's NEW name");
      assertEquals("amount", rankedColumn.getAttribute());
   }
}
