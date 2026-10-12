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
import inetsoft.uql.XRepository;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77548, an outer join of a where clause (*=, =* or (+)) is regenerated as an ANSI join of
 * the from clause in the outer-last order, which can put an inner join on the null-supplying
 * side, e.g. b.id(+) = a.id and b.id = c.id and a.id = d.id(+) as ((b JOIN c) RIGHT JOIN a) LEFT
 * JOIN d, which keeps the a rows that the inner join removes. It is refused with a data source
 * that writes ANSI joins, every one but Oracle without ansi join, which writes the (+) joins
 * back. Without a data source it parses, and it is refused once a data source is set.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 UniformSQLWhereOuterJoinAnsiRefusalTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLWhereOuterJoinAnsiRefusalTest {
   // a data source with a real driver and URL needs the credential service, and the JDBC
   // driver types of Config to pick its SQL helper
   @Configuration
   @Import(CredentialService.class)
   static class Config {
      @Bean
      inetsoft.uql.util.Config config(Plugins plugins) {
         return new inetsoft.uql.util.Config(plugins);
      }

      @Bean
      XRepository repository() {
         return mock(XRepository.class);
      }
   }

   // the data sources that write ANSI joins
   private static final String[] ANSI = {
      "generic", "generic-ansi", "h2", "mysql", "postgresql", "sql server", "mongo", "oracle-ansi"
   };

   @ParameterizedTest
   @ValueSource(strings = {
      // the inner join of b, the null-supplying table, was regenerated before the outer joins
      "select a.id, b.id, c.id, d.id from a, b, c, d where b.id(+) = a.id and b.id = c.id " +
         "and a.id = d.id(+)",
      "select a.id, b.id from a, b where a.id = b.id(+)",
      "select a.id, b.id from a, b where a.id *= b.id",
      "select a.id, b.id from a, b where a.id =* b.id",
      "select a.id, b.id, c.id from a left join b on a.id = b.id, c where a.id = c.id(+)",
      // in a subquery or a derived table, the statement is refused
      "select x.id from x where exists (select 1 from a, b where a.id = b.id(+) and a.k = x.k)",
      "select t.id from (select a.id from a, b where a.id = b.id(+)) t"
   })
   void refusedWithAnsiJoins(String text) throws Exception {
      for(String type : ANSI) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(ds);
         new SQLProcessor(sql).parse(text);
         assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), type + ": " + text);
         assertEquals(text, sql.getSQLString(), type);
         assertFalse(XUtil.isParsedSQL(sql), type);

         UniformSQL fresh = new UniformSQL();
         fresh.setDataSource(ds);
         fresh.setSQLString(text, false);
         assertTrue(fresh.isLossy(), type + ": " + text);
         assertFalse(XUtil.isQueryMergeable(query(text, ds)), type + ": " + text);

         Exception ex = assertThrows(Exception.class, () -> {
            UniformSQL parsed = new UniformSQL();
            parsed.setDataSource(ds);
            parsed.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
         });
         assertTrue(ex.getMessage().contains("Unsupported outer join in the where clause"),
                    type + ": " + ex.getMessage());
      }

      // Oracle without ansi join writes the (+) joins back, and without a data source the
      // helper isn't known yet
      for(String type : new String[] { "oracle", null }) {
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(type == null ? null : dataSource(type));
         new SQLProcessor(sql).parse(text);
         assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), type + ": " + text);
      }
   }

   @Test
   void oracleWithoutAnsiJoinWritesTheJoinsBack() throws Exception {
      String text = "select a.id, b.id, c.id from a, b, c where a.id = b.id(+) and b.id = c.id";
      UniformSQL sql = parse(text, dataSource("oracle"));
      assertFalse(sql.isLossy());
      assertTrue(regenerate(sql).endsWith("from a, b, c where a.id = b.id(+) and b.id = c.id"),
                 regenerate(sql));
   }

   @Test
   void refusedWhenADataSourceIsSetLater() throws Exception {
      String text = "select a.id, b.id, c.id from a, b, c where a.id = b.id(+) and b.id = c.id";

      for(String type : ANSI) {
         UniformSQL sql = parsedWithoutDataSource(text);
         sql.setDataSource(dataSource(type));
         assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), type);
         assertTrue(sql.isLossy(), type);
         assertFalse(XUtil.isParsedSQL(sql), type);
         assertEquals(text, sql.getSQLString(), type);
      }

      UniformSQL sql = parsedWithoutDataSource(text);
      sql.setDataSource(dataSource("oracle"));
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertFalse(sql.isLossy());

      // an Oracle data source changed to ansi join
      sql.setDataSource(dataSource("oracle-ansi"));
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
      assertEquals(text, sql.getSQLString());
   }

   @Test
   void savedQueryIsRefusedWhenLoaded() throws Exception {
      // saved with recorded where clause joins (after Bug #77475), parsed without a data source
      String text = "select a.id, b.id, c.id from a, b, c where a.id = b.id(+) and b.id = c.id";
      String xml = xml(parsedWithoutDataSource(text));
      assertTrue(xml.contains("joinClause=\"-1\""), xml);

      // by a query of the data source
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(dataSource("h2"));
      query.parseXML(element("<query_jdbc>" + xml + "</query_jdbc>"));
      UniformSQL loaded = (UniformSQL) query.getSQLDefinition();
      assertEquals(UniformSQL.PARSE_FAILED, loaded.getParseResult());
      assertFalse(XUtil.isQueryMergeable(query));

      // with the data source set before, in isLossy(), before the query cache clears the sql
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(dataSource("h2"));
      sql.parseXML(element(xml));
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertTrue(sql.isLossy());
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
      assertEquals(text, sql.getSQLString());

      // Oracle without ansi join
      query = new JDBCQuery();
      query.setDataSource(dataSource("oracle"));
      query.parseXML(element("<query_jdbc>" + xml + "</query_jdbc>"));
      assertEquals(UniformSQL.PARSE_SUCCESS,
                   ((UniformSQL) query.getSQLDefinition()).getParseResult());
   }

   @Test
   void clearedSqlStringIsNotRefused() throws Exception {
      // the structure is regenerated as before once its sql string is gone
      String text = "select a.id, b.id from a, b where a.id = b.id(+)";
      UniformSQL sql = parse(text, null);
      sql.clearSQLString();
      sql.setDataSource(dataSource("h2"));
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertEquals("select a.id, b.id from a LEFT OUTER JOIN b ON a.id = b.id",
                   normalize(sql.getSQLString()));
   }

   @Test
   void generatedSqlStillParses() throws Exception {
      // the sql that StyleBI writes from joins of the query editor: ANSI joins on every helper
      // but Oracle without ansi join, whose (+) joins parse with its data source
      for(String type : new String[] { "generic", "h2", "postgresql", "oracle", "oracle-ansi" }) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL editor = new UniformSQL();
         editor.setDataSource(ds);
         editor.addTable("a");
         editor.addTable("b");
         editor.addTable("c");
         editor.getSelection().addColumn("a.id");
         editor.getSelection().addColumn("b.id");
         editor.addJoin(new XJoin(new XExpression("a.id", XExpression.FIELD),
                                  new XExpression("b.id", XExpression.FIELD), "*="));
         editor.addJoin(new XJoin(new XExpression("b.id", XExpression.FIELD),
                                  new XExpression("c.id", XExpression.FIELD), "="));
         String generated = normalize(editor.getSQLString());
         assertEquals("oracle".equals(type), generated.contains("(+)"), generated);

         UniformSQL sql = new UniformSQL();
         sql.setDataSource(ds);
         new SQLProcessor(sql).parse(generated);
         assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), type + ": " + generated);
      }
   }

   @Test
   void validExpressionInAScalarSubquery() {
      // the validity check parses without a data source
      assertTrue(XUtil.isSQLExpressionValid("(select max(a.id) from a, b where a.id = b.id(+))"));
   }

   private static UniformSQL parsedWithoutDataSource(String text) throws Exception {
      UniformSQL sql = parse(text, null);
      sql.setSQLString(text, false);
      return sql;
   }

   private static UniformSQL parse(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      return sql;
   }

   private static String regenerate(UniformSQL sql) {
      UniformSQL copy = sql.clone();
      copy.clearSQLString();
      return normalize(copy.getSQLString());
   }

   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   private static String xml(UniformSQL sql) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      sql.writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   private static Element element(String xml) throws Exception {
      return DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
   }

   private static JDBCQuery query(String text, JDBCDataSource ds) {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      new SQLProcessor(sql).parse(text);
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      return query;
   }

   private static JDBCDataSource dataSource(String type) {
      if("generic".equals(type)) {
         return GenericJDBCDataSource.create();
      }

      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77548w_" + type);
      ds.setProductVersion("19");
      ds.setAnsiJoin(type.endsWith("-ansi"));

      switch(type.replace("-ansi", "")) {
      case "generic" -> {
         ds.setDriver("org.example.Driver");
         ds.setURL("jdbc:example:db");
      }
      case "h2" -> {
         ds.setDriver("org.h2.Driver");
         ds.setURL("jdbc:h2:mem:test");
      }
      case "mysql" -> {
         ds.setDriver("com.mysql.cj.jdbc.Driver");
         ds.setURL("jdbc:mysql://localhost/db");
      }
      case "postgresql" -> {
         ds.setDriver("org.postgresql.Driver");
         ds.setURL("jdbc:postgresql://localhost:5432/db");
      }
      case "sql server" -> {
         ds.setDriver("com.microsoft.sqlserver.jdbc.SQLServerDriver");
         ds.setURL("jdbc:sqlserver://localhost;databaseName=db");
      }
      case "mongo" -> {
         ds.setDriver("mongodb.jdbc.MongoDriver");
         ds.setURL("jdbc:mongo://localhost:27017/test");
      }
      case "oracle" -> {
         ds.setDriver("oracle.jdbc.OracleDriver");
         ds.setURL("jdbc:oracle:thin:@localhost:1521:orcl");
         ds.setRuntimeProductName("oracle");
      }
      default -> throw new IllegalArgumentException(type);
      }

      if(!type.startsWith("generic")) {
         assertEquals(type.replace("-ansi", ""), SQLHelper.getSQLHelper(ds).getSQLHelperType());
      }

      return ds;
   }
}
