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
package inetsoft.uql.jdbc;

import inetsoft.test.*;
import inetsoft.uql.path.XSelection;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generating the sql must not change the query model (Bug #77712). The second outer column
 * gets a generated ALIAS_1 because its sub-query alias is too long for Oracle and PostgreSQL
 * and the first outer column already inherited ALIAS_0. The generation used to set that
 * column's alias to its column name, which changed equals and clone, and added an
 * "as <name>" to the sql another helper then generated from the same model.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class GeneratedAliasModelTest {
   private static final String L1 = "customer_lifetime_value_total_amount";
   private static final String L2 = "order_lifetime_value_total_amount_x";

   static Stream<Arguments> cases() {
      Supplier<UniformSQL> same = GeneratedAliasModelTest::sameColumnTwice;
      Supplier<UniformSQL> join = GeneratedAliasModelTest::twoSubqueries;
      Supplier<SQLHelper> oracle = OracleSQLHelper::new;
      Supplier<SQLHelper> postgres = PostgreSQLHelper::new;
      return Stream.of(Arguments.of("same column, oracle", same, oracle),
                       Arguments.of("same column, postgresql", same, postgres),
                       Arguments.of("two subqueries, oracle", join, oracle),
                       Arguments.of("two subqueries, postgresql", join, postgres));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("cases")
   void generationDoesNotChangeTheModel(String name, Supplier<UniformSQL> build,
                                        Supplier<SQLHelper> helper)
   {
      UniformSQL model = build.get();
      String sql = generate(helper.get(), model);
      assertTrue(sql.contains("\"ALIAS_0\" as \"ALIAS_1\""), sql);

      XSelection selection = model.getSelection();
      assertNull(selection.getAlias(0), sql);
      assertNull(selection.getAlias(1), sql);
      assertEquals(build.get().getSelection(), selection);
      assertEquals(build.get().getSelection(), ((UniformSQL) model.clone()).getSelection());

      // generated again, and on a fresh model
      assertEquals(sql, generate(helper.get(), model));
      assertEquals(sql, generate(helper.get(), build.get()));

      // the generic helper accepts the long names, so it writes no alias for either column
      assertEquals(generate(new SQLHelper(), build.get()), generate(new SQLHelper(), model));
      assertNull(model.getSelection().getAlias(1));
   }

   /**
    * Two outer columns with no alias over the same sub-query column.
    */
   private static UniformSQL sameColumnTwice() {
      UniformSQL outer = new UniformSQL();
      outer.addTable("s", subquery("t", L1));
      JDBCSelection selection = (JDBCSelection) outer.getSelection();
      selection.addColumn("s." + L1);
      selection.addColumn("s." + L1);
      selection.setTable("s." + L1, "s");
      return outer;
   }

   /**
    * A join of two sub-queries, which each generate ALIAS_0 for their long alias.
    */
   private static UniformSQL twoSubqueries() {
      UniformSQL outer = new UniformSQL();
      outer.addTable("s1", subquery("t1", L1));
      outer.addTable("s2", subquery("t2", L2));
      JDBCSelection selection = (JDBCSelection) outer.getSelection();
      selection.addColumn("s1." + L1);
      selection.setTable("s1." + L1, "s1");
      selection.addColumn("s2." + L2);
      selection.setTable("s2." + L2, "s2");
      return outer;
   }

   private static UniformSQL subquery(String table, String alias) {
      UniformSQL sub = new UniformSQL();
      sub.addTable(table, table);
      JDBCSelection selection = (JDBCSelection) sub.getSelection();
      selection.addColumn(table + ".v");
      selection.setTable(table + ".v", table);
      selection.setAlias(0, alias);
      sub.setSubQuery(true);
      return sub;
   }

   private static String generate(SQLHelper helper, UniformSQL sql) {
      helper.setUniformSql(sql);
      return helper.generateSentence().replaceAll("\\s+", " ").trim();
   }
}
