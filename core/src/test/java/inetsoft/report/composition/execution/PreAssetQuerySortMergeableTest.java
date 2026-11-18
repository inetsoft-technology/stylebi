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
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.asset.GroupRef;
import inetsoft.uql.asset.SortInfo;
import inetsoft.uql.asset.SortRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static inetsoft.report.composition.execution.MergeableFixture.integer;
import static inetsoft.report.composition.execution.MergeableFixture.string;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Whether the sort of a worksheet table is pushed down as the ORDER BY of the SQL
 * ({@link PreAssetQuery#isSortInfoMergeable}). The rules are the ones of the code: no sort is
 * always fine, the database has to keep the order of a group by (H2 does not, see
 * {@code sqlhelper.xml}), a sort by an aggregate needs a database that can order by one (Informix
 * cannot), a table with a first or last aggregate is not sorted in the SQL, a column that a script
 * computes cannot be sorted by the database, and when the table has a group by the sorts of the
 * other columns are dropped.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  MergeableFixture.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PreAssetQuerySortMergeableTest {
   private static SortInfo sortBy(SortRef... sorts) {
      SortInfo info = new SortInfo();

      for(SortRef sort : sorts) {
         info.addSort(sort);
      }

      return info;
   }

   private static AggregateInfo groupByKSumOfV() {
      return groupByK(AggregateFormula.SUM);
   }

   private static AggregateInfo groupByK(AggregateFormula formula) {
      AggregateInfo info = new AggregateInfo();
      info.addGroup(new GroupRef(integer("k")));
      info.addAggregate(new AggregateRef(integer("v"), formula));
      return info;
   }

   // ---- no group by ----

   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "derby", "h2", "postgresql", "mysql", "oracle", "sql server",
                            "informix", "sybase" })
   void tableWithoutASortIsPushedDownOnEveryDatabase(String db) throws Exception {
      assertTrue(MergeableFixture.simple(db).query().isSortInfoMergeable());
   }

   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "derby", "postgresql", "mysql", "oracle", "sql server", "informix",
                            "sybase" })
   void sortByAColumnIsPushedDown(String db) throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple(db);
      built.table.setSortInfo(sortBy(new SortRef(string("name"))));

      assertTrue(built.query().isSortInfoMergeable());
   }

   // H2 does not keep the order of a group by, any sort has to be done after it
   @Test
   void sortIsNotPushedDownOnH2() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("h2");
      built.table.setSortInfo(sortBy(new SortRef(string("name"))));

      assertFalse(built.query().isSortInfoMergeable());
   }

   // no sort is nothing to push down, so H2 is fine with it
   @Test
   void emptySortIsPushedDownOnH2() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("h2");
      built.table.setSortInfo(new SortInfo());

      assertTrue(built.query().isSortInfoMergeable());
   }

   @Test
   void sortByAColumnThatAScriptComputesIsNotPushedDown() throws Exception {
      MergeableFixture.Built built = MergeableFixture.build(
         "derby", integer("k"), integer("v"),
         MergeableFixture.scriptColumn("twice", "field['k'] * 2"));
      built.table.setSortInfo(sortBy(new SortRef(
         MergeableFixture.scriptColumn("twice", "field['k'] * 2"))));

      assertFalse(built.query().isSortInfoMergeable());
   }

   // ---- with a group by ----

   @Test
   void sortByTheGroupColumnIsPushedDown() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("postgresql");
      built.table.setAggregateInfo(groupByKSumOfV());
      built.table.setSortInfo(sortBy(new SortRef(integer("k"))));

      assertTrue(built.query().isSortInfoMergeable());
   }

   @Test
   void sortByAnAggregateIsPushedDownWhenTheDatabaseCanOrderByIt() throws Exception {
      for(String db : new String[] { "derby", "postgresql", "oracle", "sybase" }) {
         MergeableFixture.Built built = MergeableFixture.simple(db);
         built.table.setAggregateInfo(groupByKSumOfV());
         built.table.setSortInfo(sortBy(
            new SortRef(new AggregateRef(integer("v"), AggregateFormula.SUM))));

         assertTrue(built.query().isSortInfoMergeable(), db);
      }
   }

   // Informix cannot order by an aggregate, but it can order by the group column
   @Test
   void sortByAnAggregateIsNotPushedDownOnInformix() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("informix");
      built.table.setAggregateInfo(groupByKSumOfV());
      built.table.setSortInfo(sortBy(
         new SortRef(new AggregateRef(integer("v"), AggregateFormula.SUM))));

      assertFalse(built.query().isSortInfoMergeable());

      built = MergeableFixture.simple("informix");
      built.table.setAggregateInfo(groupByKSumOfV());
      built.table.setSortInfo(sortBy(new SortRef(integer("k"))));

      assertTrue(built.query().isSortInfoMergeable());
   }

   // the order of the first and the last row of a group is not kept in a SQL, so the sort of the
   // table is not pushed down with them
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "first", "last" })
   void tableWithAFirstOrLastAggregateIsNotSortedInSql(String which) throws Exception {
      AggregateFormula formula =
         "first".equals(which) ? AggregateFormula.FIRST : AggregateFormula.LAST;
      MergeableFixture.Built built = MergeableFixture.simple("derby");
      built.table.setAggregateInfo(groupByK(formula));
      built.table.setSortInfo(sortBy(new SortRef(integer("k"))));

      assertFalse(built.query().isSortInfoMergeable());

      // the same sort with another aggregate
      built = MergeableFixture.simple("derby");
      built.table.setAggregateInfo(groupByKSumOfV());
      built.table.setSortInfo(sortBy(new SortRef(integer("k"))));

      assertTrue(built.query().isSortInfoMergeable());
   }

   // a group by keeps only the sorts of the group and aggregate columns; a sort of another
   // column is dropped, so a table with only such a sort has nothing left to push down, also on H2
   @Test
   void sortOfAnotherColumnIsDroppedWhenTheTableHasAGroupBy() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("h2");
      built.table.setAggregateInfo(groupByKSumOfV());
      built.table.setSortInfo(sortBy(new SortRef(string("name"))));

      assertTrue(built.query().isSortInfoMergeable());
   }

   // the sorts that are dropped are removed from the table that the query holds. The query of a
   // worksheet works on a copy of the table (AssetQuerySandbox clones it), here it is the table
   // itself, so the table loses the sort
   @Test
   void sortOfAnotherColumnIsRemovedFromTheTableOfTheQuery() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("postgresql");
      built.table.setAggregateInfo(groupByKSumOfV());
      built.table.setSortInfo(sortBy(new SortRef(string("name")), new SortRef(integer("k"))));
      PreAssetQuery query = built.query();

      assertEquals(2, query.getTable().getSortInfo().getSorts().length);
      assertTrue(query.isSortInfoMergeable());
      assertEquals(1, query.getTable().getSortInfo().getSorts().length);
      assertEquals("a.k", query.getTable().getSortInfo().getSorts()[0].getName());
   }

   // the sort of the group column stays, and H2 does not push a sort down
   @Test
   void sortByTheGroupColumnIsKeptOnH2AndNotPushedDown() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("h2");
      built.table.setAggregateInfo(groupByKSumOfV());
      built.table.setSortInfo(sortBy(new SortRef(string("name")), new SortRef(integer("k"))));

      assertFalse(built.query().isSortInfoMergeable());
   }
}
