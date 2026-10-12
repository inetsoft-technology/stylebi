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
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77482, the database merges the columns of a JOIN ... USING, so select * returns one
 * copy and an unqualified reference to the column resolves. The parsed model has no merged
 * column and regenerates an ON join with two copies, so a query with a USING join anywhere
 * is lossy and keeps its sql string in merge and in the query cache normalizer.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLUsingJoinLossyTest {
   static List<String> usingQueries() {
      List<String> list = new ArrayList<>();

      for(String type : new String[] { "", "inner ", "left ", "right ", "full outer " }) {
         String join = type + "join";
         // top level, with select * and an unqualified USING column
         list.add("select * from a " + join + " b using (id)");
         list.add("select id, a.x from a " + join + " b using (id)");
         // chained after another USING join of the column, a USING join of another table's
         // column fails the parse (Bug #77490)
         list.add("select a.x from a join b using (id) " + join + " c using (id)");
         // after an ON join
         list.add("select a.x from a join b using (id) left join c on b.k = c.k " + join +
                  " d using (id)");
         // derived table
         list.add("select t.x from (select a.x from a " + join + " b using (id)) t");
         // exists and in subqueries
         list.add("select a.x from a where exists (select 1 from b " + join + " c using (id))");
         list.add("select a.x from a where a.id in (select id from b " + join + " c using (id))");
         // several columns, a scalar, a having and a nested derived table subquery
         list.add("select a.x from a " + join + " b using (id, k)");
         list.add("select (select max(c.z) from b " + join + " c using (id)) m from a");
         list.add("select a.x from a group by a.x having count(*) > " +
                  "(select count(*) from b " + join + " c using (id))");
         list.add("select t.x from (select u.x from (select a.x from a " + join +
                  " b using (id)) u) t");
      }

      return list;
   }

   @ParameterizedTest
   @MethodSource("usingQueries")
   void usingJoinIsLossy(String text) throws Exception {
      UniformSQL sql = process(text);

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertTrue(XUtil.isParsedSQL(sql));
      assertTrue(sql.isLossy());
      assertEquals(text, sql.getSQLString());
      assertFalse(XUtil.isQueryMergeable(query(sql)));

      // a fresh object holding only the sql string re-derives it
      UniformSQL fresh = new UniformSQL();
      fresh.setSQLString(text, false);
      assertTrue(fresh.isLossy());

      // and it survives saving
      UniformSQL loaded = load(save(sql));
      assertTrue(loaded.isLossy());
      assertEquals(text, loaded.getSQLString());
      assertFalse(XUtil.isQueryMergeable(query(loaded)));
   }

   /**
    * The cache normalizer runs on every execution and must not regenerate the query.
    */
   @ParameterizedTest
   @MethodSource("usingQueries")
   void cacheNormalizerKeepsSqlString(String text) throws Exception {
      JDBCQuery query = query(process(text));
      JDBCQueryCacheNormalizer normalizer = new JDBCQueryCacheNormalizer(query);
      UniformSQL sql = (UniformSQL) query.getSQLDefinition();

      assertFalse(normalizer.isClearedSqlString());
      assertTrue(sql.hasSQLString());
      assertEquals(text, sql.getSQLString());
   }

   /**
    * A query saved without a lossy flag re-derives it from the sql string.
    */
   @Test
   void savedUsingJoinWithoutLossyFlagIsLossy() throws Exception {
      String text = "select * from a left join b using (id)";
      Element xml = save(process(text));
      xml.removeAttribute("lossy");
      UniformSQL loaded = load(xml);

      assertEquals(UniformSQL.PARSE_SUCCESS, loaded.getParseResult());
      assertTrue(loaded.isLossy());
      assertFalse(XUtil.isQueryMergeable(query(loaded)));
   }

   @Test
   void onJoinIsNotLossy() throws Exception {
      String text = "select a.x, b.y from a join b on a.id = b.id";
      UniformSQL sql = process(text);

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertFalse(sql.isLossy());
      assertTrue(XUtil.isQueryMergeable(query(sql)));

      // control for the normalizer test: a non-lossy query is regenerated
      JDBCQuery query = query(process(text));
      assertTrue(new JDBCQueryCacheNormalizer(query).isClearedSqlString());
   }

   // parse the way setSQLString does, keeping the sql string
   private static UniformSQL process(String text) {
      UniformSQL sql = new UniformSQL();
      // Bug #77434 refuses a RIGHT or FULL join mixed with an inner join without a data
      // source, as the sql helper that would generate it is unknown
      sql.setDataSource(GenericJDBCDataSource.create());
      new SQLProcessor(sql).parse(text);
      return sql;
   }

   private static Element save(UniformSQL sql) throws Exception {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      return Tool.parseXML(new StringReader(buffer.toString())).getDocumentElement();
   }

   private static UniformSQL load(Element xml) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parseXML(xml);
      return sql;
   }

   private static JDBCQuery query(UniformSQL sql) {
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(dataSource());
      query.setSQLDefinition(sql);
      return query;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getDatabaseType()).thenReturn(JDBCDataSource.JDBC_ODBC);
      when(ds.getRuntimeProductName()).thenReturn("h2");
      // the merge decision runs on a clone of the query, which clones its data source
      when(ds.clone()).thenReturn(ds);
      return ds;
   }
}
