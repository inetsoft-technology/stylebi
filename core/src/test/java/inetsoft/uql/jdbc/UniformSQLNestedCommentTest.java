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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77664. {@code ML_COMMENT} used to end at the first {@code "*&#47;"} with no nesting
 * awareness, silently truncating a genuinely nested {@code /* ... *&#47;} comment (PostgreSQL,
 * Derby, SQL Server and DB2 all nest block comments). It now recursively consumes a fully
 * nested comment, so the outer comment only ends at the {@code "*&#47;"} that closes the
 * outermost level. Separately, {@code SL_COMMENT} treated {@code "//"} as a line-comment
 * opener alongside {@code "--"}; {@code "//"} is not standard SQL (and is integer division on
 * some dialects), so it silently swallowed real trailing SQL. Only {@code "--"} is recognized
 * now.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  SQLHelperNotEqualJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLNestedCommentTest {
   // a comment that closes only its inner "/*" (under real nesting rules the outer "/*" is
   // still open) must no longer silently succeed with the tail "where k = 1" treated as live
   // SQL -- it's genuinely unterminated and must fail to parse
   @Test
   void unterminatedOuterCommentFailsParse() {
      String text = "select id from t /* a /* b */ where k = 1";
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(dataSource("h2"));
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), text);
   }

   // a properly balanced nested comment (two "/*", two "*/") is stripped as a single comment,
   // not truncated at the first "*/" with the leftover "c */" rejected as stray tokens
   @Test
   void balancedNestedCommentIsFullyStripped() {
      String text = "select id from t /* a /* b */ c */ where k = 1";
      String generated = regenerate(text, dataSource("h2"));
      assertEquals("select id from t where k = 1", generated);
   }

   @Test
   void balancedNestedCommentMidExpressionIsFullyStripped() {
      String text = "select id from t where k /* a /* b */ c */ = 1";
      String generated = regenerate(text, dataSource("h2"));
      assertEquals("select id from t where k = 1", generated);
   }

   // "//" is not standard SQL and must no longer be silently swallowed as a comment
   @Test
   void slashSlashIsNotTreatedAsComment() {
      String text = "select id from t where k = 1 // c";
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(dataSource("h2"));
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), text);
   }

   // regression: a single, non-nested block comment must still be recognized and stripped
   @Test
   void singleLevelBlockCommentStillWorks() {
      String generated = regenerate("select id from t /* a */ where k = 1", dataSource("h2"));
      assertEquals("select id from t where k = 1", generated);
   }

   // regression: "--" line comments must still be recognized and stripped
   @Test
   void dashDashLineCommentStillWorks() {
      String generated = regenerate("select id from t where k = 1 -- c", dataSource("h2"));
      assertEquals("select id from t where k = 1", generated);
   }

   // regression: a single "/" is still the division operator, unaffected by the comment fix
   @Test
   void divisionOperatorStillWorks() {
      String generated = regenerate("select id from t where k = 10 / 2", dataSource("h2"));
      assertTrue(generated.contains("10/2") || generated.contains("10 / 2"), generated);
   }

   private static String regenerate(String text, JDBCDataSource ds) {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      sql.clearSQLString();
      return normalize(sql.getSQLString());
   }

   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   private static JDBCDataSource dataSource(String type) {
      return SQLHelperNotEqualJoinTest.RowCompare.dataSource(type);
   }
}
