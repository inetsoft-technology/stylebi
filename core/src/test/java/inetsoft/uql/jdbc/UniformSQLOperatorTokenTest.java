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
 *
 * <p>{@code #} is deliberately NOT wired into the grammar as a binary operator (review round 2
 * of PR #6240, bug #77659): the parser has no dialect context, and {@code #} means different
 * things per database (a comment opener in MySQL, part of a valid identifier in Oracle/SQL
 * Server, XOR only in PostgreSQL), so treating it as one true operator is wrong for most
 * dialects. {@code HASH} stays a real lexer token that no parser rule accepts, the same pattern
 * as {@code BACKSLASH} (#77640), so {@code #} still fails the parse with a clear error instead
 * of silently vanishing via the catch-all.
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
   void hashOperatorIsPreservedNotTreatedAsAlias() {
      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse("select a, b # c from t");

      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
   }
}
