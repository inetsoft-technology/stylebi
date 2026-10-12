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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for Bug #77660: {@code direct_select_stmt_n_rows} in SQLParser.g had no EOF
 * anchor after its optional trailing {@code (SEMI)?}, so {@link UniformSQL#parse(String, int,
 * long)} with {@code PARSE_ALL} silently dropped any statement text following a {@code ;} instead
 * of rejecting it, reporting {@code PARSE_SUCCESS} regardless.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class UniformSQLTrailingStatementTest {
   @Test
   void trailingStatementAfterSemicolonIsRejected() {
      UniformSQL sql = new UniformSQL();

      assertThrows(Exception.class, () ->
         sql.parse("select a from t where b = 1; drop table t", UniformSQL.PARSE_ALL,
                   UniformSQL.PARSE_PERIOD));
   }

   @Test
   void trailingSemicolonWithNothingAfterStillParses() throws Exception {
      UniformSQL sql = new UniformSQL();

      sql.parse("select a from t where b = 1;", UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
   }

   @Test
   void trailingCommentAfterSemicolonStillParses() throws Exception {
      UniformSQL sql = new UniformSQL();

      sql.parse("select a from t where b = 1; -- drop table t", UniformSQL.PARSE_ALL,
                UniformSQL.PARSE_PERIOD);

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
   }

   @Test
   void trailingGarbageWithoutSemicolonIsStillRejected() {
      UniformSQL sql = new UniformSQL();

      assertThrows(Exception.class, () ->
         sql.parse("select a from t where b = 1 garbage garbage", UniformSQL.PARSE_ALL,
                   UniformSQL.PARSE_PERIOD));
   }
}
