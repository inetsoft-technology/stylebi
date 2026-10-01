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

import antlr.RecognitionException;
import inetsoft.test.*;
import inetsoft.uql.util.XUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77435, a natural join has no join columns in the sql text and UniformSQL can't
 * record one, so it used to parse successfully and regenerate as a cross join
 * ({@code from a, b}). A natural join now fails the parse, so the original sql is kept
 * and the query is not mergeable.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLNaturalJoinTest {
   @ParameterizedTest
   @ValueSource(strings = {
      // reporter's examples
      "select * from a natural join b",
      "select a.x from a natural left join b",
      // every join type, with and without OUTER
      "select a.x from a natural inner join b",
      "select a.x from a natural left outer join b",
      "select a.x from a natural right join b",
      "select a.x from a natural right outer join b",
      "select a.x from a natural full join b",
      "select a.x from a natural full outer join b",
      "SELECT A.X FROM A NATURAL JOIN B",
      // in a chain, first and later positions
      "select a.x from a natural join b natural join c",
      "select a.x from a join b on a.id = b.id natural join c",
      "select a.x from a natural join b join c on a.id = c.id",
      // after an outer join, and followed by one
      "select a.x from a left join b on a.id = b.id natural join c",
      "select a.x from a natural join b left join c on a.id = c.id",
      // inside parentheses
      "select a.x from a left join (b natural join c) on a.id = b.id",
      "select a.x from (a natural join b) left join c on a.id = c.id",
      // aliased tables and a derived right operand
      "select t1.x from a t1 natural join b t2",
      "select t1.x from a as t1 natural left join b as t2",
      "select a.x from a natural join (select * from b) t2",
      // comma-listed left tables
      "select a.x from a, b natural join c",
      // derived table and EXISTS subqueries
      "select * from (select a.x from a natural join b) t",
      "select a.x from a where exists (select 1 from b natural join c)",
      "select a.x from a where a.id in (select b.id from b natural left join c)",
      // a natural join with ON or USING is invalid sql, but must not be accepted either
      "select a.x from a natural join b on a.id = b.id",
      "select a.x from a natural join b using (id)"
   })
   void naturalJoinFailsCleanly(String text) throws Exception {
      RecognitionException ex = assertThrows(RecognitionException.class, () -> parse(text));
      assertTrue(ex.getMessage().contains("Unsupported natural join"), ex.getMessage());

      // the query processor keeps the original sql and reports the parse failure
      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
      assertEquals(text, sql.getSQLString());
      assertFalse(XUtil.isParsedSQL(sql));

      // the lazy lossy check re-parses the sql, so a fresh object is lossy too
      assertTrue(sql.isLossy());

      UniformSQL fresh = new UniformSQL();
      fresh.setSQLString(text, false);
      assertTrue(fresh.isLossy());
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      // a cross join is a cartesian product, which from a, b represents correctly
      "select a.x from a cross join b | select a.x from a, b",
      // inner and outer joins with ON are unchanged
      "select a.x from a join b on a.id = b.id | where a.id = b.id",
      "select a.x from a inner join b on a.id = b.id | where a.id = b.id",
      "select a.x from a left join b on a.id = b.id | a LEFT OUTER JOIN b ON a.id = b.id",
      "select a.x from a right join b on b.id = a.id | a RIGHT OUTER JOIN b ON a.id = b.id",
      // a comma join with a where join is unchanged
      "select a.x from a, b where a.id = b.id | where a.id = b.id"
   })
   void otherJoinsStillParse(String text, String expected) throws Exception {
      UniformSQL sql = parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertFalse(sql.isLossy());

      String generated = sql.getSQLString().replaceAll("\\s+", " ").trim();
      assertTrue(generated.contains(expected), generated);

      UniformSQL processed = new UniformSQL();
      new SQLProcessor(processed).parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, processed.getParseResult());
   }

   private static UniformSQL parse(String text) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      return sql;
   }
}
