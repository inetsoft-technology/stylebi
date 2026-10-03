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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for Bug #77659: {@code SQLLexer} runs with {@code filter = true} and had no
 * token rule whose first-set included {@code %}, {@code ^}, {@code ~} or {@code #}, so those
 * characters were silently consumed by the generated catch-all instead of being tokenized. The
 * remaining tokens still formed a valid statement (e.g. {@code select a % b from t} collapsed to
 * {@code select a b from t}, parsed as column {@code a} aliased {@code b}), so parsing reported
 * {@code PARSE_SUCCESS} while silently changing the query's meaning.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class UniformSQLOperatorTokenTest {
   @Test
   void modulusOperatorIsPreservedNotTreatedAsAlias() throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parse("select a % b from t", UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      XSelection selection = sql.getSelection();
      assertEquals(1, selection.getColumnCount());
      assertNull(selection.getAlias(0));
      assertTrue(selection.getColumn(0).contains("%"));
      assertTrue(sql.getSQLString().contains("%"));
   }

   @Test
   void caretOperatorIsPreservedNotTreatedAsAlias() throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parse("select a ^ b from t", UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      XSelection selection = sql.getSelection();
      assertEquals(1, selection.getColumnCount());
      assertNull(selection.getAlias(0));
      assertTrue(selection.getColumn(0).contains("^"));
      assertTrue(sql.getSQLString().contains("^"));
   }

   @Test
   void tildeOperatorIsPreservedAsUnaryPrefix() throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parse("select ~a from t", UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      XSelection selection = sql.getSelection();
      assertEquals(1, selection.getColumnCount());
      assertTrue(selection.getColumn(0).contains("~"));
      assertTrue(sql.getSQLString().contains("~"));
   }

   @Test
   void hashOperatorIsPreservedNotTreatedAsAlias() throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parse("select a, b # c from t", UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      XSelection selection = sql.getSelection();
      assertEquals(2, selection.getColumnCount());
      assertNull(selection.getAlias(1));
      assertTrue(selection.getColumn(1).contains("#"));
      assertTrue(sql.getSQLString().contains("#"));
   }
}
