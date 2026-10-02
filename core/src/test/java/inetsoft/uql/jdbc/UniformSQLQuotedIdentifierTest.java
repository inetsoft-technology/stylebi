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
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77408, a bare quoted identifier in the select list keeps its quotes when the SQL
 * is regenerated from the parsed {@link UniformSQL}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class UniformSQLQuotedIdentifierTest {
   @Test
   void regeneratedSqlKeepsQuotes() throws Exception {
      UniformSQL sql = parse("select \"x y\", `a b` as c, \"MixedCase\", d from t");
      JDBCSelection selection = (JDBCSelection) sql.getSelection();

      // the column path itself is unquoted, as before
      assertEquals("x y", selection.getColumn(0));
      assertTrue(selection.isQuoted("x y"));
      assertTrue(selection.isQuoted("a b"));
      assertTrue(selection.isQuoted("MixedCase"));
      assertFalse(selection.isQuoted("d"));

      String generated = normalize(sql.getSQLString());
      assertEquals("select \"MixedCase\", \"a b\" as c, d, \"x y\" from t", generated);
   }

   @Test
   void quotedFlagSurvivesXmlRoundTrip() throws Exception {
      UniformSQL sql = parse("select \"x y\", d from t");
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      UniformSQL loaded = new UniformSQL();
      loaded.parseXML(Tool.parseXML(new StringReader(buffer.toString())).getDocumentElement());
      loaded.clearSQLString();

      JDBCSelection selection = (JDBCSelection) loaded.getSelection();
      assertTrue(selection.isQuoted("x y"));
      assertFalse(selection.isQuoted("d"));
      String generated = normalize(loaded.getSQLString());
      assertEquals("select d, \"x y\" from t", generated);
   }

   @Test
   void quotedFlagSurvivesAsteriskExpansion() throws Exception {
      UniformSQL sql = parse("select \"x y\", d from t");
      JDBCUtil.expandAsterisk(sql);
      sql.clearSQLString();

      assertTrue(((JDBCSelection) sql.getSelection()).isQuoted("x y"));
      assertEquals("select d, \"x y\" from t", normalize(sql.getSQLString()));
   }

   // the generated sql is pretty-printed (and its columns sorted)
   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   private static UniformSQL parse(String text) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      return sql;
   }
}
