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
 * Bug #77664. A MySQL character-set introducer such as {@code _utf8} lexes as two separate
 * tokens, {@code INTRODUCER} ({@code _}) and {@code IDENT} ({@code utf8}); {@code
 * char_string_lit}'s action in {@code SQLParser.g} used to join them with a hardcoded literal
 * space ({@code _ utf8'...'}), which MySQL rejects. The introducer must stay adjacent to the
 * charset name with no space.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  SQLHelperNotEqualJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLMysqlIntroducerSpacingTest {
   @Test
   void introducerHasNoStraySpace() {
      String generated = regenerate("select * from t where c = _utf8'its'", mysqlDataSource());
      assertTrue(generated.contains("_utf8'its'"), generated);
      assertFalse(generated.contains("_ utf8"), generated);
   }

   // the introducer bug and the '' escape are independent; both must work together
   @Test
   void introducerWithEscapedQuoteRoundTrips() {
      String generated = regenerate("select * from t where c = _utf8'it''s'", mysqlDataSource());
      assertTrue(generated.contains("_utf8'it''s'"), generated);
      assertFalse(generated.contains("_ utf8"), generated);
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

   private static JDBCDataSource mysqlDataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds_mysql");
      ds.setProductVersion("8.0");
      ds.setDriver("com.mysql.cj.jdbc.Driver");
      ds.setURL("jdbc:mysql://localhost:3306/test");
      String helper = SQLHelper.getSQLHelper(ds).getSQLHelperType();
      assertEquals("mysql", helper);
      return ds;
   }
}
