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
import inetsoft.uql.util.XUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77664. {@code SPIDENT_SQUARE} correctly lexes a bracket name containing a literal
 * newline (e.g. {@code [a\nb]}) as one token, but {@code XUtil.isSpecial()}'s special-char
 * switch didn't include newline (or other non-space whitespace), so the name regenerated
 * completely unquoted -- re-lexed, indistinguishable from column {@code a} aliased {@code b}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  SQLHelperNotEqualJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLBracketNewlineQuoteTest {
   @ParameterizedTest
   @ValueSource(strings = { "postgresql", "h2", "oracle" })
   void bracketNameWithNewlineIsQuoted(String type) {
      String text = "select [a\nb] from t";
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(dataSource(type));
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), type);
      sql.clearSQLString();
      String generated = sql.getSQLString();

      // the name must come back quoted, keeping its embedded newline inside the quotes, not
      // regenerated as two bare, newline-separated identifiers (which would silently become
      // column "a" aliased "b")
      assertTrue(generated.contains("\"a\nb\"") || generated.contains("`a\nb`"),
                 type + ": " + generated);
   }

   @Test
   void isSpecialFlagsEmbeddedNewline() {
      assertTrue(XUtil.isSpecial("a\nb", null));
      assertTrue(XUtil.isSpecial("a\rb", null));
      assertTrue(XUtil.isSpecial("a\tb", null));
   }

   private static JDBCDataSource dataSource(String type) {
      return SQLHelperNotEqualJoinTest.RowCompare.dataSource(type);
   }
}
