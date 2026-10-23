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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79176 (item 4), {@code CAST(x AS DATE)} and {@code CAST(x AS TIME)} are regenerated
 * with the type unquoted. DATE and TIME lex as keyword tokens, so the cast target used to fall
 * back to a qualified name and was quoted as a keyword-named identifier ({@code AS "DATE"}),
 * which Derby reads as a user-defined type.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLCastDateTimeTypeTest {
   @ParameterizedTest
   @ValueSource(strings = { "date", "DATE", "Date", "time", "TIME", "time(3)", "TIME(3)",
                            "timestamp", "TIMESTAMP", "timestamp(6)", "varchar(10)",
                            "integer", "decimal(10,2)" })
   void castTargetKeepsUserTypeUnquotedInSelect(String type) throws Exception {
      assertEquals("select cast(o.d as " + type + ") from sales o",
                   regenerate("select cast(o.d as " + type + ") from sales o"));
   }

   @ParameterizedTest
   @ValueSource(strings = { "date", "DATE", "time", "time(3)", "timestamp" })
   void castTargetKeepsUserTypeUnquotedInWhere(String type) throws Exception {
      assertEquals("select o.a from sales o where o.d > cast('2020-01-01' as " + type + ")",
                   regenerate("select o.a from sales o where o.d > cast('2020-01-01' as " + type + ")"));
   }

   @Test
   void castWithAliasKeepsUserCase() throws Exception {
      assertEquals("select CAST(o.ORDER_DATE AS DATE) as ODATE from SA.ORDERS o",
                   regenerate("select CAST(o.ORDER_DATE AS DATE) AS ODATE from SA.ORDERS o"));
   }

   @Test
   void quotedCastTargetStaysQuoted() throws Exception {
      // a user-written quoted type is a deliberate choice and must not be unquoted
      assertEquals("select cast(o.d as \"DATE\") from sales o",
                   regenerate("select cast(o.d as \"DATE\") from sales o"));
   }

   @Test
   void columnsNamedDateAndTimeStayQuoted() throws Exception {
      assertEquals("select o.\"date\" from sales o", regenerate("select o.date from sales o"));
      assertEquals("select o.\"time\" from sales o", regenerate("select o.time from sales o"));
      // a column named date next to a cast to date: only the column is quoted
      assertEquals("select cast(o.d as date), o.\"date\" from sales o",
                   regenerate("select cast(o.d as date), o.date from sales o"));
   }

   private static String regenerate(String text) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      sql.clearSQLString();
      // the generated sql is pretty-printed
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }
}
