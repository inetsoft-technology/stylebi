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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77552, a NOT applied to a condition that is already negated (not (not x), and a NOT
 * around not in, is not null, not between, not like or not exists) was parsed as the single
 * negation, so the regenerated SQL returned different rows. Every shape is parsed and
 * regenerated with the default helper and with H2 with and without the ANSI option, and must
 * return the same rows as the original on random Derby data with nulls, and regenerate to
 * itself.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 SQLHelperNotEqualJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class SQLParserDoubleNegationTest {
   private static final String SEL = "select a.id ai, a.k ak, a.s az, b.id bi, b.k bk ";
   private static final String SEL_A = "select a.id ai, a.k ak, a.s az from a ";

   @ParameterizedTest
   @ValueSource(strings = {
      SEL + "from a, b where b.k = 2 or not (not (a.k = 1))",
      SEL + "from a, b where not (not (a.id = b.k))",
      SEL + "from a, b where a.k = 1 or not (not (a.id = b.k))",
      SEL + "from a, b where not (not (a.k = 1 or b.k = 2))",
      SEL + "from a, b where not (not (a.id = b.id and a.k = b.k))",
      SEL_A + "where not (a.k not in (1, 2))",
      SEL_A + "where not a.k not in (1, 2)",
      SEL_A + "where not (a.k is not null)",
      SEL_A + "where not a.k is not null",
      SEL_A + "where not (a.k not between 1 and 2)",
      SEL_A + "where not (a.s not like '1%')",
      SEL_A + "where not (not exists (select 1 from b where b.id = a.id))",
      "select a.k ak, count(*) n from a group by a.k having not (not (count(*) > 1))",
      SEL + "from a inner join b on a.id = b.id and not (not (b.k = 1))",
      SEL + "from a left join b on a.id = b.id where not (not (b.k = 1))",
      SEL_A + "where a.k in (select b.k from b where not (not (b.k = 1)))",
      SEL_A + "where exists (select 1 from b where not (b.k not in (1)) and b.id = a.id)",
      "select a.id ai, case when not (not (a.k = 1)) then 1 else 0 end c from a",
      SEL_A + "where not (not (not (a.k = 1)))",
      SEL_A + "where not (not (not (not (a.k = 1))))",
   })
   void sameRows(String text) throws Exception {
      for(String type : new String[] { "default", "h2", "h2-ansi" }) {
         String generated = generate(text, type);
         assertEquals(generated, generate(generated, type), type + " round trip: " + text);
         assertEquals(0, diffCount(text, generated), type + ": " + text + " -> " + generated);
      }
   }

   // the double negation is the condition itself, and a NOT around a negated operator is the
   // operator without its negation. A root join comes back as a join, an INNER JOIN ON = with
   // the ANSI option, where the collapsed not (..) was written as ON a.id <> b.k
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "from a, b where not (not (a.id = b.k))|h2|from a, b where a.id = b.k",
      "from a, b where not (not (a.id = b.k))|h2-ansi|from a INNER JOIN b ON a.id = b.k",
      "from a where not (a.k is not null)|h2|from a where a.k is null",
      "from a where not a.k is not null|h2-ansi|from a where a.k is null",
      "from a where not (a.k not in (1, 2))|h2|from a where a.k IN (1,2)",
      "from a where not (not (not (not (a.k = 1))))|h2|from a where a.k = 1",
      "from a where not (not (not (a.k = 1)))|h2|from a where not (a.k = 1)",
      "from a where not (not exists (select 1 from b where b.id = a.id))|h2-ansi|" +
         "from a where EXISTS ( select 1 from b where b.id = a.id)",
      "from a where not (a.s not like '1%')|h2|from a where a.s LIKE '1%'",
   })
   void regenerated(String tail, String type, String expected) throws Exception {
      String generated = generate("select a.id ai " + tail, type);
      assertEquals(expected, generated.substring(generated.indexOf(" from ") + 1), type);
   }

   // parse and regenerate, with the data source of the type, or none for the default helper
   private static String generate(String text, String type) throws Exception {
      UniformSQL sql = new UniformSQL();

      if(!"default".equals(type)) {
         sql.setDataSource(SQLHelperNotEqualJoinTest.RowCompare.dataSource(type));
      }

      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   // the number of random datasets with nulls on which the two queries return different rows
   private static int diffCount(String original, String generated) throws Exception {
      Driver driver = (Driver) Class.forName("org.apache.derby.iapi.jdbc.AutoloadedDriver")
         .getDeclaredConstructor().newInstance();
      Random random = new Random(77552);
      Integer[] values = { null, 0, 1, 2 };
      String[] strings = { null, "'1'", "'10'", "'2'" };
      int diff = 0;

      try(Connection con = driver.connect("jdbc:derby:memory:not77552;create=true",
                                          new Properties());
          Statement st = con.createStatement())
      {
         for(String table : new String[] { "a", "b" }) {
            try {
               st.execute("drop table " + table);
            }
            catch(SQLException ignore) {
            }

            st.execute("create table " + table + " (id int, k int, s varchar(8))");
         }

         for(int n = 0; n < 150; n++) {
            for(String table : new String[] { "a", "b" }) {
               st.execute("delete from " + table);

               for(int r = random.nextInt(5); r > 0; r--) {
                  st.execute("insert into " + table + " values (" +
                                values[random.nextInt(4)] + ", " + values[random.nextInt(4)] +
                                ", " + strings[random.nextInt(4)] + ")");
               }
            }

            if(!SQLHelperNotEqualJoinTest.RowCompare.rows(con, original)
               .equals(SQLHelperNotEqualJoinTest.RowCompare.rows(con, generated)))
            {
               diff++;
            }
         }
      }

      return diff;
   }
}
