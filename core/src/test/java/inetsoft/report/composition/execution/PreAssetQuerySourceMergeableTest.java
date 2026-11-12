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
import inetsoft.uql.asset.RotatedTableAssembly;
import inetsoft.uql.asset.UnpivotTableAssembly;
import inetsoft.uql.asset.GroupRef;
import inetsoft.uql.asset.SQLBoundTableAssembly;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.TableAssemblyOperator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static inetsoft.report.composition.execution.MergeableFixture.integer;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Whether a worksheet table made of other tables (a mirror, a join, a union, intersect or minus) is
 * pushed down to SQL as a whole ({@link PreAssetQuery#isQueryMergeable}). The rules are the ones of
 * the code: every table it is made of has to be pushable itself, on the same data source, and the
 * database has to support the operation. What a database supports is the list of unsupported
 * operations of {@code sqlhelper.xml} and the SQL helpers: a sub query in the FROM clause (not
 * Informix), a full outer join (not Access, MySQL, Derby, Sybase, H2), a union, intersect and
 * minus (not Access, Derby, Informix, MySQL, Sybase; MySQL takes a union from version 4.1 on of the
 * data source, SQL Server only a union), the maximum number of rows (not Sybase, Informix, H2 and
 * others). A merge join is done by the worksheet, never by the SQL.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  MergeableFixture.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PreAssetQuerySourceMergeableTest {
   private static SQLBoundTableAssembly second(MergeableFixture.Built built, String db)
      throws Exception
   {
      return built.addTable("T2", db, integer("k"), integer("v"));
   }

   private static boolean mergeable(MergeableFixture.Built built, TableAssembly table)
      throws Exception
   {
      return built.queryOf(table).isQueryMergeable(false);
   }

   // ---- a mirror ----

   // a mirror is a sub query in the FROM clause: only Informix cannot
   @ParameterizedTest(name = "{0}")
   @CsvSource({ "derby,true", "h2,true", "postgresql,true", "mysql,true", "oracle,true",
                "sql server,true", "sybase,true", "informix,false" })
   void mirrorIsPushedDownWhenTheDatabaseHasASubQueryInFrom(String db, boolean expected)
      throws Exception
   {
      MergeableFixture.Built built = MergeableFixture.simple(db);

      assertEquals(expected, mergeable(built, built.mirror("M1", built.table)), db);
   }

   @Test
   void mirrorOfATableThatIsNotPushedDownIsNot() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("derby");
      AggregateInfo info = new AggregateInfo();
      info.addGroup(new GroupRef(integer("k")));
      info.addAggregate(new AggregateRef(integer("v"), AggregateFormula.LAST));
      built.table.setAggregateInfo(info);

      assertFalse(mergeable(built, built.mirror("M1", built.table)));
   }

   // a sub query needs a table whose SQL can be a sub query
   @Test
   void mirrorOfATableWhoseSqlCannotBeASubQueryIsNotPushedDown() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("derby");
      built.table.setSQLMergeable(false);

      assertFalse(mergeable(built, built.mirror("M1", built.table)));
   }

   // the maximum number of rows of the base cannot be put into the SQL of a database that has none
   @ParameterizedTest(name = "{0}")
   @CsvSource({ "derby,true", "postgresql,true", "oracle,true", "h2,false", "sybase,false" })
   void mirrorOfATableWithAMaximumNumberOfRows(String db, boolean expected) throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple(db);
      built.table.setMaxRows(100);

      assertEquals(expected, mergeable(built, built.mirror("M1", built.table)), db);
   }

   // ---- a join ----

   @ParameterizedTest(name = "{0}")
   @CsvSource({ "derby,true", "h2,true", "postgresql,true", "mysql,true", "oracle,true",
                "sql server,true", "sybase,true", "informix,false" })
   void innerJoinIsPushedDownWhenTheDatabaseHasASubQueryInFrom(String db, boolean expected)
      throws Exception
   {
      MergeableFixture.Built built = MergeableFixture.simple(db);
      SQLBoundTableAssembly right = second(built, db);

      assertEquals(expected,
                   mergeable(built, built.join("J1", TableAssemblyOperator.INNER_JOIN,
                                               built.table, right)), db);
   }

   // a full outer join needs a database that has it
   @ParameterizedTest(name = "{0}")
   @CsvSource({ "postgresql,true", "oracle,true", "sql server,true", "derby,false", "mysql,false",
                "h2,false", "sybase,false" })
   void fullOuterJoinIsPushedDownWhenTheDatabaseHasIt(String db, boolean expected)
      throws Exception
   {
      MergeableFixture.Built built = MergeableFixture.simple(db);
      SQLBoundTableAssembly right = second(built, db);

      assertEquals(expected,
                   mergeable(built, built.join("J1", TableAssemblyOperator.FULL_JOIN,
                                               built.table, right)), db);
   }

   // a merge join is done by the worksheet, on the rows, also on a database that could join
   @Test
   void mergeJoinIsNeverPushedDown() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("postgresql");
      SQLBoundTableAssembly right = second(built, "postgresql");

      assertFalse(mergeable(built, built.join("J1", TableAssemblyOperator.MERGE_JOIN,
                                              built.table, right)));
   }

   // two tables of two databases cannot be joined by one SQL
   @Test
   void joinOfTablesOfTwoDataSourcesIsNotPushedDown() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("postgresql");
      SQLBoundTableAssembly right = second(built, "oracle");

      assertFalse(mergeable(built, built.join("J1", TableAssemblyOperator.INNER_JOIN,
                                              built.table, right)));
   }

   // the same name is the same source, but a data source with another definition is another one
   @Test
   void joinOfTablesOfDataSourcesThatDifferInTheirDefinitionIsNotPushedDown() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("postgresql");
      SQLBoundTableAssembly right = built.addTableOfChangedDataSource(
         "T2", "postgresql", "jdbc:postgresql://other:5432/test", integer("k"), integer("v"));

      assertFalse(mergeable(built, built.join("J1", TableAssemblyOperator.INNER_JOIN,
                                              built.table, right)));
   }

   @Test
   void joinOfATableThatIsNotPushedDownIsNot() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("postgresql");
      SQLBoundTableAssembly right = second(built, "postgresql");
      AggregateInfo info = new AggregateInfo();
      info.addGroup(new GroupRef(integer("k")));
      info.addAggregate(new AggregateRef(integer("v"), AggregateFormula.LAST));
      right.setAggregateInfo(info);

      assertFalse(mergeable(built, built.join("J1", TableAssemblyOperator.INNER_JOIN,
                                              built.table, right)));
   }

   // the maximum number of rows of a table cannot be put into the SQL of a database that has none
   @ParameterizedTest(name = "{0}")
   @CsvSource({ "postgresql,true", "oracle,true", "h2,false", "sybase,false" })
   void joinOfATableWithAMaximumNumberOfRows(String db, boolean expected) throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple(db);
      SQLBoundTableAssembly right = second(built, db);
      right.setMaxRows(10);

      assertEquals(expected,
                   mergeable(built, built.join("J1", TableAssemblyOperator.INNER_JOIN,
                                               built.table, right)), db);
   }

   // ---- a union, an intersect and a minus ----

   @ParameterizedTest(name = "{0}")
   @CsvSource({ "postgresql,true", "oracle,true", "sql server,true", "h2,true", "mysql,true",
                "derby,false", "sybase,false", "informix,false" })
   void unionIsPushedDownWhenTheDatabaseHasIt(String db, boolean expected) throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple(db);
      SQLBoundTableAssembly bottom = second(built, db);

      assertEquals(expected,
                   mergeable(built, built.concatenate("C1", TableAssemblyOperator.UNION,
                                                      built.table, bottom)), db);
   }

   // MySQL takes a union (from the version of its data source, 8.0 here) but not an intersect
   // and a minus, and SQL Server only a union: its helper answers the concatenation of tables
   // with "union only", not the list of sqlhelper.xml
   @ParameterizedTest(name = "{0}")
   @CsvSource({ "postgresql,true", "oracle,true", "h2,true", "sql server,false", "mysql,false",
                "derby,false", "sybase,false" })
   void intersectIsPushedDownWhenTheDatabaseHasIt(String db, boolean expected) throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple(db);
      SQLBoundTableAssembly bottom = second(built, db);

      assertEquals(expected,
                   mergeable(built, built.concatenate("C1", TableAssemblyOperator.INTERSECT,
                                                      built.table, bottom)), db);
   }

   @ParameterizedTest(name = "{0}")
   @CsvSource({ "postgresql,true", "oracle,true", "h2,true", "sql server,false", "mysql,false",
                "derby,false", "sybase,false" })
   void minusIsPushedDownWhenTheDatabaseHasIt(String db, boolean expected) throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple(db);
      SQLBoundTableAssembly bottom = second(built, db);

      assertEquals(expected,
                   mergeable(built, built.concatenate("C1", TableAssemblyOperator.MINUS,
                                                      built.table, bottom)), db);
   }

   @Test
   void unionOfTablesOfTwoDataSourcesIsNotPushedDown() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("postgresql");
      SQLBoundTableAssembly bottom = second(built, "oracle");

      assertFalse(mergeable(built, built.concatenate("C1", TableAssemblyOperator.UNION,
                                                     built.table, bottom)));
   }

   @Test
   void unionOfTablesOfDataSourcesThatDifferInTheirDefinitionIsNotPushedDown() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("postgresql");
      SQLBoundTableAssembly bottom = built.addTableOfChangedDataSource(
         "T2", "postgresql", "jdbc:postgresql://other:5432/test", integer("k"), integer("v"));

      assertFalse(mergeable(built, built.concatenate("C1", TableAssemblyOperator.UNION,
                                                     built.table, bottom)));
   }

   @Test
   void unionOfATableThatIsNotPushedDownIsNot() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("postgresql");
      SQLBoundTableAssembly bottom = second(built, "postgresql");
      AggregateInfo info = new AggregateInfo();
      info.addGroup(new GroupRef(integer("k")));
      info.addAggregate(new AggregateRef(integer("v"), AggregateFormula.LAST));
      bottom.setAggregateInfo(info);

      assertFalse(mergeable(built, built.concatenate("C1", TableAssemblyOperator.UNION,
                                                     built.table, bottom)));
   }

   // ---- the SQL of a table that the user wrote ----

   private static boolean edited(String sql, boolean parse) throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("postgresql");
      SQLBoundTableAssembly table = built.edited("E1", "postgresql", sql, parse);
      return mergeable(built, table);
   }

   // a SQL that is parsed, with nothing the parser had to leave out, can be built upon
   @Test
   void editedSqlThatIsParsedIsPushedDown() throws Exception {
      assertTrue(edited("select a.k, a.v from a", true));
   }

   // a SQL that is not parsed is not changed, so nothing is added to it
   @Test
   void editedSqlThatIsNotParsedIsNotPushedDown() throws Exception {
      assertFalse(edited("select a.k, a.v from a", false));
   }

   // a join condition that the parser does not keep makes its result lossy
   @Test
   void editedSqlThatTheParserCannotKeepIsNotPushedDown() throws Exception {
      assertFalse(edited("select a.k, b.v from a left join b on a.k < b.k", true));
   }

   // a SQL without a table has nothing to build upon
   @Test
   void editedSqlWithoutATableIsNotPushedDown() throws Exception {
      assertFalse(edited("select 1", true));
   }

   // a SQL that already groups is not grouped again
   @Test
   void editedSqlWithAGroupByIsNotPushedDown() throws Exception {
      assertFalse(edited("select a.k from a group by a.k", true));
   }

   // ---- a table that is not made of SQL ----

   // the rows of a table of the worksheet are not in a database
   @Test
   void tableWithItsDataInTheWorksheetIsNotPushedDown() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("postgresql");

      assertFalse(mergeable(built, built.embedded("D1")));
   }

   @Test
   void rotatedAndUnpivotedTablesAreNotPushedDown() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("postgresql");
      TableAssembly data = built.embedded("D1");
      RotatedTableAssembly rotated = new RotatedTableAssembly(built.ws, "R1", data);
      built.ws.addAssembly(rotated);
      rotated.update();
      UnpivotTableAssembly unpivot = new UnpivotTableAssembly(built.ws, "U1", data);
      built.ws.addAssembly(unpivot);
      unpivot.update();

      assertFalse(mergeable(built, rotated));
      assertFalse(mergeable(built, unpivot));
   }
}
