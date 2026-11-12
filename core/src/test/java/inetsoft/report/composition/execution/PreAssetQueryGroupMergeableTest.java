/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.PluginsTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.GroupRef;
import inetsoft.uql.asset.RankingCondition;
import inetsoft.uql.asset.SortInfo;
import inetsoft.uql.asset.SortRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static inetsoft.report.composition.execution.MergeableFixture.integer;
import static inetsoft.report.composition.execution.MergeableFixture.scriptColumn;
import static inetsoft.report.composition.execution.MergeableFixture.string;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The rest of the rules that decide whether a worksheet table is pushed down to SQL as a whole
 * ({@link PreAssetQuery#isQueryMergeable}): the group by ({@code isAggregateInfoMergeable}), the
 * selected columns, the ranking, and the answer for the whole table. The rules are the ones of the
 * code: a crosstab, a time series group, a named group, a group under a where that cannot be
 * pushed down, and a ranking that is per group of the results or of a nested group are done after
 * the SQL; a table whose columns are all hidden has nothing to select; a ranking is never pushed
 * down; SQLite does not take date and boolean columns.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  MergeableFixture.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PreAssetQueryGroupMergeableTest {
   private static AggregateInfo groupByKSumOfV() {
      AggregateInfo info = new AggregateInfo();
      info.addGroup(new GroupRef(integer("k")));
      info.addAggregate(new AggregateRef(integer("v"), AggregateFormula.SUM));
      return info;
   }

   private static ColumnRef hidden(ColumnRef column) {
      column.setVisible(false);
      return column;
   }

   /** The ranking top 3 of a column. */
   private static ConditionList ranking(RankingCondition condition, ColumnRef column) {
      condition.setOperation(XCondition.TOP_N);
      condition.setN(3);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(column, condition, 0));
      return list;
   }

   // ---- the group by ----

   @Test
   void tableWithoutAGroupByIsPushedDown() throws Exception {
      assertTrue(MergeableFixture.simple("derby").query().isAggregateInfoMergeable());
   }

   @Test
   void groupByAndAnAggregateIsPushedDown() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("derby");
      built.table.setAggregateInfo(groupByKSumOfV());

      assertTrue(built.query().isAggregateInfoMergeable());
   }

   // a crosstab has a group for the rows and one for the columns, and aggregates
   @Test
   void crosstabIsNotPushedDown() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("derby");
      AggregateInfo info = groupByKSumOfV();
      info.addGroup(new GroupRef(string("name")));
      info.setCrosstab(true);
      built.table.setAggregateInfo(info);

      assertFalse(built.query().isAggregateInfoMergeable());

      // the same groups without the crosstab option are a plain group by
      built = MergeableFixture.simple("derby");
      info = groupByKSumOfV();
      info.addGroup(new GroupRef(string("name")));
      built.table.setAggregateInfo(info);

      assertTrue(built.query().isAggregateInfoMergeable());
   }

   @Test
   void timeSeriesGroupIsNotPushedDown() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("derby");
      AggregateInfo info = new AggregateInfo();
      GroupRef group = new GroupRef(integer("k"));
      group.setTimeSeries(true);
      info.addGroup(group);
      info.addAggregate(new AggregateRef(integer("v"), AggregateFormula.SUM));
      built.table.setAggregateInfo(info);

      assertFalse(built.query().isAggregateInfoMergeable());
   }

   // the order of the values of a named group (the groups that the user made) is not a SQL order
   @Test
   void namedGroupIsNotPushedDown() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("derby");
      AggregateInfo info = new AggregateInfo();
      GroupRef group = new GroupRef(integer("k"));
      group.setNamedGroupAssembly("my groups");
      info.addGroup(group);
      info.addAggregate(new AggregateRef(integer("v"), AggregateFormula.SUM));
      built.table.setAggregateInfo(info);

      assertFalse(built.query().isAggregateInfoMergeable());
   }

   // a group by is not pushed down when the where is not: the group has to run on the rows that
   // the where leaves
   @Test
   void groupByIsNotPushedDownWithAWhereThatIsNot() throws Exception {
      MergeableFixture.Built built = MergeableFixture.build(
         "derby", integer("k"), integer("v"), scriptColumn("twice", "field['k'] * 2"));
      built.table.setAggregateInfo(groupByKSumOfV());
      AssetCondition condition = new AssetCondition(XSchema.INTEGER);
      condition.setOperation(XCondition.EQUAL_TO);
      condition.addValue(2);
      ConditionList where = new ConditionList();
      where.append(new ConditionItem(scriptColumn("twice", "field['k'] * 2"), condition, 0));
      built.table.setPreConditionList(where);

      assertFalse(built.query().isAggregateInfoMergeable());
   }

   // ---- the ranking of a group by ----

   // the top 3 of the first group column can be done on the result of the group by
   @Test
   void rankingOfTheFirstGroupColumnFitsTheGroupBy() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("derby");
      built.table.setAggregateInfo(groupByKSumOfV());
      built.table.setRankingConditionList(ranking(new RankingCondition(), integer("k")));

      assertTrue(built.query().isAggregateInfoMergeable());
   }

   @Test
   void rankingThatGroupsTheOthersDoesNotFitTheGroupBy() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("derby");
      built.table.setAggregateInfo(groupByKSumOfV());
      RankingCondition condition = new RankingCondition();
      condition.setGroupOthers(true);
      built.table.setRankingConditionList(ranking(condition, integer("k")));

      assertFalse(built.query().isAggregateInfoMergeable());
   }

   // a ranking of the second group column (a group inside a group) is done on the summary
   @Test
   void rankingOfANestedGroupDoesNotFitTheGroupBy() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("derby");
      AggregateInfo info = new AggregateInfo();
      info.addGroup(new GroupRef(integer("k")));
      info.addGroup(new GroupRef(string("name")));
      info.addAggregate(new AggregateRef(integer("v"), AggregateFormula.SUM));
      built.table.setAggregateInfo(info);
      built.table.setRankingConditionList(ranking(new RankingCondition(), string("name")));

      assertFalse(built.query().isAggregateInfoMergeable());
   }

   // a ranking by the value of another column needs the summary of that column
   @Test
   void rankingByAnotherColumnDoesNotFitTheGroupBy() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("derby");
      built.table.setAggregateInfo(groupByKSumOfV());
      RankingCondition condition = new RankingCondition();
      condition.setDataRef(new AggregateRef(integer("v"), AggregateFormula.SUM));
      built.table.setRankingConditionList(ranking(condition, integer("k")));

      assertFalse(built.query().isAggregateInfoMergeable());
   }

   // ---- the ranking itself ----

   @Test
   void tableWithoutARankingIsPushedDown() throws Exception {
      assertTrue(MergeableFixture.simple("derby").query().isRankingMergeable());
   }

   // a worksheet ranking is always done on the result, never in the SQL
   @Test
   void rankingIsNeverPushedDown() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("derby");
      built.table.setRankingConditionList(ranking(new RankingCondition(), integer("k")));

      assertFalse(built.query().isRankingMergeable());
   }

   // ---- the selected columns ----

   @Test
   void visibleColumnsAreSelected() throws Exception {
      assertTrue(MergeableFixture.simple("derby").query().isColumnSelectionMergeable());
   }

   // a table that selects no column has nothing to push down
   @Test
   void tableWithOnlyHiddenColumnsIsNotPushedDown() throws Exception {
      MergeableFixture.Built built = MergeableFixture.build(
         "derby", hidden(integer("k")), hidden(integer("v")));

      assertFalse(built.query().isColumnSelectionMergeable());
   }

   @Test
   void hiddenColumnsAreNotChecked() throws Exception {
      // a hidden column that a script computes does not stop the others
      MergeableFixture.Built built = MergeableFixture.build(
         "derby", integer("k"), hidden(scriptColumn("twice", "field['k'] * 2")));

      assertTrue(built.query().isColumnSelectionMergeable());

      // a visible one does
      built = MergeableFixture.build(
         "derby", integer("k"), scriptColumn("twice", "field['k'] * 2"));

      assertFalse(built.query().isColumnSelectionMergeable());
   }

   // SQLite has no date and no boolean type
   @Test
   void sqliteDoesNotTakeDateAndBooleanColumns() throws Exception {
      assertTrue(MergeableFixture.build("sqlite", integer("k"), string("name"))
                    .query().isColumnSelectionMergeable());
      assertFalse(MergeableFixture.build("sqlite", integer("k"),
                                         MergeableFixture.column("day", XSchema.DATE))
                     .query().isColumnSelectionMergeable());
      assertFalse(MergeableFixture.build("sqlite", integer("k"),
                                         MergeableFixture.column("flag", XSchema.BOOLEAN))
                     .query().isColumnSelectionMergeable());
      // another database takes them
      assertTrue(MergeableFixture.build("derby", integer("k"),
                                        MergeableFixture.column("day", XSchema.DATE))
                    .query().isColumnSelectionMergeable());
   }

   // ---- the whole table ----

   @Test
   void tableThatPassesEveryStageIsPushedDown() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("derby");
      built.table.setAggregateInfo(groupByKSumOfV());

      assertTrue(built.query().isQueryMergeable(false));
   }

   // each part of the table can stop the push down of all of it
   @Test
   void oneStageThatFailsStopsTheWholeTable() throws Exception {
      // the group by
      MergeableFixture.Built built = MergeableFixture.simple("derby");
      AggregateInfo info = new AggregateInfo();
      info.addGroup(new GroupRef(integer("k")));
      info.addAggregate(new AggregateRef(integer("v"), AggregateFormula.LAST));
      built.table.setAggregateInfo(info);
      assertFalse(built.query().isQueryMergeable(false));

      // the sort, on H2
      built = MergeableFixture.simple("h2");
      SortInfo sort = new SortInfo();
      sort.addSort(new SortRef(string("name")));
      built.table.setSortInfo(sort);
      assertFalse(built.query().isQueryMergeable(false));

      // the ranking
      built = MergeableFixture.simple("derby");
      built.table.setRankingConditionList(ranking(new RankingCondition(), integer("k")));
      assertFalse(built.query().isQueryMergeable(false));

      // the selected columns
      built = MergeableFixture.build("derby", hidden(integer("k")));
      assertFalse(built.query().isQueryMergeable(false));
   }

   // the answer is kept after the first question: an aggregate that is added to the table later is
   // not seen by that query, a new query of the table sees it
   @Test
   void answerOfAQueryIsKept() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("derby");
      built.table.setAggregateInfo(groupByKSumOfV());
      PreAssetQuery query = built.query();

      assertTrue(query.isQueryMergeable(false));
      built.table.getAggregateInfo().addAggregate(
         new AggregateRef(integer("v"), AggregateFormula.LAST));
      assertTrue(query.isQueryMergeable(false));
      assertFalse(built.query().isQueryMergeable(false));
   }
}
