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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77554, an outer join ON operand whose table doesn't resolve to a from clause table
 * fails the parse, so the original sql runs. It used to be regenerated without its table,
 * as "from LEFT OUTER JOIN b ON .. , a", and with three or more tables the join was
 * dropped from the from clause, which gave a cross join and different rows.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLUnqualifiedOuterOnRefuseTest {
   @ParameterizedTest
   @ValueSource(strings = {
      // a quoted operand
      "select a.x from a left join b on \"x y\" = b.id",
      // a third table whose ON has one unqualified side, the join that was dropped
      "select a.x from a left join b on a.id = b.id left join c on xy = c.id",
      "select a.x from a left join b on a.id = b.id left join c on \"x y\" = c.id",
      // a nested join on the right side
      "select a.x from a left join (b join c on b.id = c.id) on xy = b.id",
      // subquery and derived table positions
      "select a.x from a where a.id in (select c.id from c left join d on c.id = did)",
      "select t.x from (select a.x from a left join b on xy = b.id) t"
   })
   void unqualifiedOuterJoinOperandFailsCleanly(String text) {
      RecognitionException ex = assertThrows(RecognitionException.class, () -> parse(text));
      assertTrue(ex.getMessage().contains("Unsupported outer join condition"), ex.getMessage());

      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
   }

   private static UniformSQL parse(String text) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      return sql;
   }
}
