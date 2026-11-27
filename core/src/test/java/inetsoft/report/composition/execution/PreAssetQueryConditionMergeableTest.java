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
import inetsoft.uql.JunctionOperator;
import inetsoft.uql.XCondition;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.asset.DateCondition;
import inetsoft.uql.asset.GroupRef;
import inetsoft.uql.asset.RankingCondition;
import inetsoft.uql.asset.SQLBoundTableAssembly;
import inetsoft.uql.asset.SubQueryValue;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static inetsoft.report.composition.execution.MergeableFixture.integer;
import static inetsoft.report.composition.execution.MergeableFixture.scriptColumn;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Whether the conditions of a worksheet table are pushed down as the WHERE and the HAVING of the
 * SQL ({@link PreAssetQuery#isPreConditionListMergeable}, {@link
 * PreAssetQuery#isPostConditionListMergeable}). The rules are the ones of the code: the column of
 * every condition, and the columns that a condition compares with, must be columns the database
 * can see (not a column that a script computes), a condition that is not a plain comparison (a
 * ranking) cannot be pushed down, a date condition of the date conditions of the worksheet can, a
 * "one of" of a Mongo database cannot, and a sub query only when it is on the same data source, can
 * be pushed down itself and the database can have a sub query in a condition.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  MergeableFixture.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PreAssetQueryConditionMergeableTest {
   private static ConditionList list(ConditionItem... items) {
      ConditionList list = new ConditionList();

      for(ConditionItem item : items) {
         if(list.getSize() > 0) {
            list.append(new JunctionOperator());
         }

         list.append(item);
      }

      return list;
   }

   /** The condition "column operation value" of an integer column. */
   private static ConditionItem compare(DataRef column, int operation, Object... values) {
      AssetCondition condition = new AssetCondition(XSchema.INTEGER);
      condition.setOperation(operation);

      for(Object value : values) {
         condition.addValue(value);
      }

      return new ConditionItem(column, condition, 0);
   }

   private static MergeableFixture.Built builtWithScriptColumn(String db) throws Exception {
      return MergeableFixture.build(db, integer("k"), integer("v"),
                                    scriptColumn("twice", "field['k'] * 2"));
   }

   private static boolean where(MergeableFixture.Built built, ConditionItem... items)
      throws Exception
   {
      built.table.setPreConditionList(list(items));
      return built.query().isPreConditionListMergeable();
   }

   // ---- the column of the condition ----

   @Test
   void tableWithoutConditionsIsPushedDown() throws Exception {
      assertTrue(MergeableFixture.simple("derby").query().isPreConditionListMergeable());
   }

   @Test
   void comparisonOfAColumnIsPushedDown() throws Exception {
      assertTrue(where(MergeableFixture.simple("derby"),
                       compare(integer("k"), XCondition.EQUAL_TO, 1)));
   }

   @Test
   void conditionOnAColumnThatAScriptComputesIsNotPushedDown() throws Exception {
      assertFalse(where(builtWithScriptColumn("derby"),
                        compare(scriptColumn("twice", "field['k'] * 2"), XCondition.EQUAL_TO, 2)));
   }

   // one condition that cannot be pushed down is enough, the others do not matter
   @Test
   void oneConditionThatCannotBePushedDownStopsTheList() throws Exception {
      assertFalse(where(builtWithScriptColumn("derby"),
                        compare(integer("k"), XCondition.EQUAL_TO, 1),
                        compare(scriptColumn("twice", "field['k'] * 2"), XCondition.EQUAL_TO, 2)));
      assertTrue(where(MergeableFixture.simple("derby"),
                       compare(integer("k"), XCondition.EQUAL_TO, 1),
                       compare(integer("v"), XCondition.GREATER_THAN, 5)));
   }

   // ---- the value of the condition ----

   @Test
   void comparisonWithAnotherColumnIsPushedDownWhenTheOtherColumnIs() throws Exception {
      assertTrue(where(MergeableFixture.simple("derby"),
                       compare(integer("k"), XCondition.EQUAL_TO, integer("v"))));
      assertFalse(where(builtWithScriptColumn("derby"),
                        compare(integer("k"), XCondition.EQUAL_TO,
                                scriptColumn("twice", "field['k'] * 2"))));
   }

   // ---- the kind of the condition ----

   // a ranking is not a comparison, it is done on the result
   @Test
   void rankingConditionIsNotPushedDown() throws Exception {
      RankingCondition ranking = new RankingCondition();
      ranking.setOperation(XCondition.TOP_N);
      ranking.setN(3);

      assertFalse(where(MergeableFixture.simple("derby"),
                        new ConditionItem(integer("k"), ranking, 0)));
   }

   // a date condition of the worksheet (this year, last 3 months, ...) is made into a range of
   // dates when the SQL is made, so it is pushed down when its column is
   @Test
   void dateConditionIsPushedDownWhenItsColumnIs() throws Exception {
      DateCondition thisYear = new DateCondition.YearCondition(0);

      assertTrue(where(MergeableFixture.simple("derby"),
                       new ConditionItem(integer("k"), thisYear, 0)));
      assertFalse(where(builtWithScriptColumn("derby"),
                        new ConditionItem(scriptColumn("twice", "field['k'] * 2"), thisYear, 0)));
   }

   // ---- a Mongo database ----

   /** The query of the table, on a database that the SQL helper says is Mongo. */
   private static SQLBoundQuery mongoQuery(MergeableFixture.Built built) throws Exception {
      return new SQLBoundQuery(AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(built.ws),
                               built.table, false, false)
      {
         @Override
         protected SQLHelper getSQLHelper(UniformSQL sql) {
            return new SQLHelper() {
               @Override
               public String getSQLHelperType() {
                  return "mongo";
               }
            };
         }
      };
   }

   // "one of" is not pushed down to Mongo, any other operation is
   @Test
   void oneOfIsNotPushedDownToMongo() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("derby");
      built.table.setPreConditionList(list(compare(integer("k"), XCondition.ONE_OF, 1)));
      assertFalse(mongoQuery(built).isPreConditionListMergeable());

      built = MergeableFixture.simple("derby");
      built.table.setPreConditionList(list(compare(integer("k"), XCondition.EQUAL_TO, 1)));
      assertTrue(mongoQuery(built).isPreConditionListMergeable());

      // another database takes the "one of"
      assertTrue(where(MergeableFixture.simple("derby"),
                       compare(integer("k"), XCondition.ONE_OF, 1)));
   }

   /** Runs merge() of the query and returns it. */
   private static SQLBoundQuery merged(SQLBoundQuery query) throws Exception {
      query.merge(new inetsoft.uql.VariableTable());
      return query;
   }

   // merge() takes out the conditions that cannot be pushed down one by one
   // (isConditionItemMergeable) and then asks about the rest (isConditionListMergeable). The
   // one-of check for Mongo is in both, so only the "one of" is done in memory and the other
   // conditions are pushed down. Bug #81229.
   @Test
   void oneOfOnMongoIsTheOnlyConditionKeptInMemory() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("derby");
      built.table.setPreConditionList(list(compare(integer("k"), XCondition.EQUAL_TO, 1),
                                           compare(integer("k"), XCondition.ONE_OF, 1, 2)));
      SQLBoundQuery mongo = merged(mongoQuery(built));

      assertNotNull(mongo.getUniformSQL().getWhere());
      assertEquals(1, mongo.preconds.getSize());
      assertEquals(XCondition.ONE_OF,
                   mongo.preconds.getConditionItem(0).getXCondition().getOperation());

      // the same conditions on another database are both pushed down
      built = MergeableFixture.simple("derby");
      built.table.setPreConditionList(list(compare(integer("k"), XCondition.EQUAL_TO, 1),
                                           compare(integer("k"), XCondition.ONE_OF, 1, 2)));
      SQLBoundQuery other = merged(built.query());

      assertNotNull(other.getUniformSQL().getWhere());
      assertEquals(0, other.preconds.getSize());

      // a condition that is not pushed down for another reason is taken out alone
      built = builtWithScriptColumn("derby");
      built.table.setPreConditionList(list(
         compare(integer("k"), XCondition.EQUAL_TO, 1),
         compare(scriptColumn("twice", "field['k'] * 2"), XCondition.EQUAL_TO, 2)));
      SQLBoundQuery script = merged(built.query());

      assertNotNull(script.getUniformSQL().getWhere());
      assertEquals(1, script.preconds.getSize());
   }

   // a "one of" that is the only condition is done in memory, nothing is pushed down
   @Test
   void oneOfAloneOnMongoIsKeptInMemory() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("derby");
      built.table.setPreConditionList(list(compare(integer("k"), XCondition.ONE_OF, 1, 2)));
      SQLBoundQuery mongo = merged(mongoQuery(built));

      assertNull(mongo.getUniformSQL().getWhere());
      assertEquals(1, mongo.preconds.getSize());
      assertEquals(XCondition.ONE_OF,
                   mongo.preconds.getConditionItem(0).getXCondition().getOperation());
   }

   // with an OR the list is not split, so a "one of" keeps every condition in memory
   @Test
   void oneOfInAnOrOnMongoKeepsEveryConditionInMemory() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("derby");
      ConditionList conditions = list(compare(integer("k"), XCondition.EQUAL_TO, 1),
                                      compare(integer("k"), XCondition.ONE_OF, 1, 2));
      conditions.setJunctionOperator(1, JunctionOperator.OR);
      built.table.setPreConditionList(conditions);
      SQLBoundQuery mongo = merged(mongoQuery(built));

      assertNull(mongo.getUniformSQL().getWhere());
      assertEquals(3, mongo.preconds.getSize());
   }

   // conditions that are not a "one of" are all pushed down to Mongo
   @Test
   void otherConditionsOnMongoAreAllPushedDown() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("derby");
      built.table.setPreConditionList(list(compare(integer("k"), XCondition.EQUAL_TO, 1),
                                           compare(integer("v"), XCondition.GREATER_THAN, 5)));
      SQLBoundQuery mongo = merged(mongoQuery(built));

      assertNotNull(mongo.getUniformSQL().getWhere());
      assertEquals(0, mongo.preconds.getSize());
   }

   // ---- a sub query ----

   private static ConditionItem inSubQuery(MergeableFixture.Built built, SQLBoundTableAssembly sub)
   {
      SubQueryValue value = new SubQueryValue();
      value.setQuery(sub.getName());
      value.setAttribute(integer("k"));
      assertTrue(value.update(built.ws));
      return compare(integer("k"), XCondition.ONE_OF, value);
   }

   @Test
   void subQueryOnTheSameDataSourceIsPushedDown() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("derby");
      SQLBoundTableAssembly sub = built.addTable("T2", "derby", integer("k"), integer("v"));

      assertTrue(where(built, inSubQuery(built, sub)));
   }

   @Test
   void subQueryOnAnotherDataSourceIsNotPushedDown() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("derby");
      SQLBoundTableAssembly sub = built.addTable("T2", "postgresql", integer("k"), integer("v"));

      assertFalse(where(built, inSubQuery(built, sub)));
   }

   // Sybase cannot have a sub query in a condition
   @Test
   void subQueryIsNotPushedDownOnSybase() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("sybase");
      SQLBoundTableAssembly sub = built.addTable("T2", "sybase", integer("k"), integer("v"));

      assertFalse(where(built, inSubQuery(built, sub)));
   }

   // ---- comparison with a sub query uses ANY (bug #82411) ----

   private static ConditionItem subQueryCompare(MergeableFixture.Built built,
                                                SQLBoundTableAssembly sub, int operation,
                                                boolean equal, boolean negated)
   {
      SubQueryValue value = new SubQueryValue();
      value.setQuery(sub.getName());
      value.setAttribute(integer("k"));
      assertTrue(value.update(built.ws));
      AssetCondition condition = new AssetCondition(XSchema.INTEGER);
      condition.setOperation(operation);
      condition.setEqual(equal);
      condition.setNegated(negated);
      condition.addValue(value);
      return new ConditionItem(integer("v"), condition, 0);
   }

   private static String pushedSql(String type, int operation, boolean equal, boolean negated)
      throws Exception
   {
      MergeableFixture.Built built = MergeableFixture.simple(type);
      SQLBoundTableAssembly sub = built.addTable("T2", type, integer("k"), integer("v"));
      built.table.setPreConditionList(list(subQueryCompare(built, sub, operation, equal, negated)));
      SQLBoundQuery query = merged(built.query());
      assertEquals(0, query.preconds.getSize(), type + " " + operation);
      query.getUniformSQL().clearSQLString();
      String sql = query.getUniformSQL().getSQLString();
      return sql.replaceAll("\\s+", " ").replace("( ", "(").toLowerCase();
   }

   @ParameterizedTest
   @CsvSource({
      "derby,    LESS_THAN,    false, false, < any",
      "derby,    LESS_THAN,    true,  false, <= any",
      "derby,    GREATER_THAN, false, false, > any",
      "derby,    GREATER_THAN, true,  false, >= any",
      "derby,    GREATER_THAN, false, true,  > any",
      "derby,    LESS_THAN,    true,  true,  <= any",
      "oracle,   GREATER_THAN, false, false, > any",
      "oracle,   LESS_THAN,    true,  true,  <= any",
      "postgresql, GREATER_THAN, true, true, >= any"
   })
   void comparisonWithSubQueryIsPushedDownAsAny(String type, String operation, boolean equal,
                                                boolean negated, String expected)
      throws Exception
   {
      int op = operation.equals("LESS_THAN") ? XCondition.LESS_THAN : XCondition.GREATER_THAN;
      String sql = pushedSql(type, op, equal, negated);
      assertTrue(sql.contains(expected + " (select"), sql);

      if(negated) {
         assertTrue(sql.contains("not "), sql);
      }
      else {
         assertFalse(sql.contains("not "), sql);
      }
   }

   @Test
   void equalAndOneOfWithSubQueryKeepTheirOperators() throws Exception {
      for(int op : new int[] { XCondition.EQUAL_TO, XCondition.ONE_OF }) {
         String sql = pushedSql("derby", op, false, false);
         assertFalse(sql.contains(" any"), sql);
         assertTrue(op == XCondition.EQUAL_TO ? sql.contains("= (select") :
                       sql.contains(" in (select"), sql);
      }
   }

   private static SQLBoundQuery helperTypeQuery(MergeableFixture.Built built, String type)
      throws Exception
   {
      return new SQLBoundQuery(AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(built.ws),
                               built.table, false, false)
      {
         @Override
         protected SQLHelper getSQLHelper(UniformSQL sql) {
            return new SQLHelper() {
               @Override
               public String getSQLHelperType() {
                  return type;
               }
            };
         }
      };
   }

   @ParameterizedTest
   @CsvSource({ "clickhouse", "google bigquery", "hadoop hive", "impala", "databricks",
                "dremio", "denodo", "exasol", "luciddb", "mongo" })
   void comparisonWithSubQueryIsKeptInMemoryWithoutAnySupport(String type) throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("derby");
      SQLBoundTableAssembly sub = built.addTable("T2", "derby", integer("k"), integer("v"));
      built.table.setPreConditionList(list(
         subQueryCompare(built, sub, XCondition.GREATER_THAN, false, false)));
      assertFalse(helperTypeQuery(built, type).isPreConditionListMergeable(), type);
      SQLBoundQuery query = merged(helperTypeQuery(built, type));
      assertNull(query.getUniformSQL().getWhere(), type);
      assertEquals(1, query.preconds.getSize(), type);

      // a database that supports it, and "in", are not affected
      assertTrue(helperTypeQuery(built, "postgresql").isPreConditionListMergeable());
      built.table.setPreConditionList(list(
         subQueryCompare(built, sub, XCondition.ONE_OF, false, false)));
      assertTrue(helperTypeQuery(built, "clickhouse").isPreConditionListMergeable());
   }

   // ---- the conditions on the result of a group by (having) ----

   @Test
   void conditionOnAnAggregateIsPushedDown() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("derby");
      AggregateInfo info = new AggregateInfo();
      info.addGroup(new GroupRef(integer("k")));
      AggregateRef sum = new AggregateRef(integer("v"), AggregateFormula.SUM);
      info.addAggregate(sum);
      built.table.setAggregateInfo(info);
      built.table.setPostConditionList(list(compare(sum, XCondition.GREATER_THAN, 10)));

      assertTrue(built.query().isPostConditionListMergeable());
   }

   // the having is checked with the group by: a group that is not pushed down takes it along
   @Test
   void conditionOnAnAggregateIsNotPushedDownWithAnAggregateThatIsNot() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("derby");
      AggregateInfo info = new AggregateInfo();
      info.addGroup(new GroupRef(integer("k")));
      AggregateRef last = new AggregateRef(integer("v"), AggregateFormula.LAST);
      info.addAggregate(last);
      built.table.setAggregateInfo(info);
      built.table.setPostConditionList(list(compare(last, XCondition.GREATER_THAN, 10)));

      assertFalse(built.query().isPostConditionListMergeable());
   }
}
