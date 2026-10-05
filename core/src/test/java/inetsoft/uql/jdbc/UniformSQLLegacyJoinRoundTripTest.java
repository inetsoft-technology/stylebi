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
 * Bug #77548, the round trips the refusals must leave alone: a postgresql query saved by 1.1.0
 * whose joins are recorded from its sql string regenerates the same sql as a fresh parse, with
 * the data source set or through the load of its query; and the (+) joins StyleBI writes for
 * Oracle without ansi join parse again with that data source, however the data source is given,
 * and regenerate the same sql. The *= and =* joins are refused like (+) on ANSI data sources.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 UniformSQLLegacyJoinRoundTripTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLLegacyJoinRoundTripTest {
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

   // postgresql saves of UniformSQLLegacyJoinRefusalTest-1.1.0.txt whose joins are recorded
   @ParameterizedTest
   @ValueSource(strings = {
      "select \"a\".\"id\", \"b\".\"id\" from \"a\" left join \"b\" on \"a\".\"id\" = \"b\".\"id\"",
      "select \"b\".\"id\", \"e\".\"id\", \"g\".\"id\", \"p\".\"id\" from \"b\" left join " +
         "(\"g\" join \"e\" on \"g\".\"id\" = \"e\".\"id\") on \"b\".\"id\" = \"e\".\"id\" join " +
         "\"p\" on \"e\".\"id\" = \"p\".\"id\"",
      "select \"A\".\"ID\", \"b\".\"id\" from \"A\" left join \"b\" on \"A\".\"ID\" = \"b\".\"id\"",
      "select a.id, b.id from a left join b on a.id = b.id",
      "select b.id, e.id, g.id, p.id from b left join (g join e on g.id = e.id) on b.id = e.id " +
         "join p on e.id = p.id"
   })
   void postgresqlSaveRegeneratesAsAFreshParse(String text) throws Exception {
      UniformSQL fresh = new UniformSQL();
      fresh.setDataSource(postgresql());
      new SQLProcessor(fresh).parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, fresh.getParseResult(), text);
      String expected = regenerate(fresh);

      // with the data source set, in isLossy()
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(postgresql());
      sql.parseXML(element(savedXml(text)));
      assertFalse(sql.isLossy(), text);
      assertEquals(expected, regenerate(sql), text);

      // loaded by a query of the data source, the joins are recorded before the data source is
      // set on the sql
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(postgresql());
      query.parseXML(element("<query_jdbc>" + savedXml(text) + "</query_jdbc>"));
      UniformSQL loaded = (UniformSQL) query.getSQLDefinition();
      assertTrue(XUtil.isParsedSQL(loaded), text);
      UniformSQL copy = loaded.clone();
      copy.setDataSource(postgresql());
      copy.setSQLString(null);
      assertEquals(expected, normalize(copy.getSQLString()), text);
   }

   @ParameterizedTest
   @ValueSource(strings = { "*=,=,=*", "*=,*=,=", "=,*=,*=", "=*,=,=" })
   void oracleOuterJoinsRoundTrip(String ops) throws Exception {
      String[] op = ops.split(",");
      UniformSQL editor = new UniformSQL();
      editor.setDataSource(oracle(false));

      for(String table : new String[] { "a", "b", "c", "d" }) {
         editor.addTable(table);
         editor.getSelection().addColumn(table + ".id");
      }

      editor.addJoin(join("a.id", op[0], "b.id"));
      editor.addJoin(join("b.id", op[1], "c.id"));
      editor.addJoin(join("c.id", op[2], "d.id"));
      String generated = normalize(editor.getSQLString());
      assertTrue(generated.contains("(+)"), generated);

      // with the data source
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(oracle(false));
      new SQLProcessor(sql).parse(generated);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), generated);
      assertFalse(sql.isLossy(), generated);
      // Oracle writes the selected columns in upper case
      assertEquals(generated.toLowerCase(), regenerate(sql).toLowerCase());

      // parsed without a data source, the data source set afterwards
      UniformSQL later = new UniformSQL();
      new SQLProcessor(later).parse(generated);
      later.setDataSource(oracle(false));
      assertEquals(UniformSQL.PARSE_SUCCESS, later.getParseResult(), generated);
      assertFalse(later.isLossy(), generated);

      // saved and loaded by a query of the data source
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(oracle(false));
      query.parseXML(element("<query_jdbc>" + xml(sql) + "</query_jdbc>"));
      assertEquals(UniformSQL.PARSE_SUCCESS,
                   ((UniformSQL) query.getSQLDefinition()).getParseResult(), generated);

      // the same sql on Oracle with ansi join is refused
      UniformSQL ansi = new UniformSQL();
      ansi.setDataSource(oracle(true));
      new SQLProcessor(ansi).parse(generated);
      assertEquals(UniformSQL.PARSE_FAILED, ansi.getParseResult(), generated);
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "select a.id, b.id, c.id from a, b, c where a.id *= b.id and b.id = c.id",
      "select a.id, b.id, c.id from a, b, c where a.id =* b.id and b.id = c.id"
   })
   void starOuterJoinsAreRefused(String text) throws Exception {
      for(JDBCDataSource ds : new JDBCDataSource[] { GenericJDBCDataSource.create(), h2() }) {
         // at the parse
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(ds);
         new SQLProcessor(sql).parse(text);
         assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), text);

         // parsed without a data source, the data source set afterwards
         sql = new UniformSQL();
         new SQLProcessor(sql).parse(text);
         assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
         assertFalse(sql.isLossy(), text);
         sql.setDataSource(ds);
         assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), text);
         assertEquals(text, sql.getSQLString());

         // saved without a data source, loaded with it set, in isLossy()
         UniformSQL saved = new UniformSQL();
         new SQLProcessor(saved).parse(text);
         UniformSQL loaded = new UniformSQL();
         loaded.setDataSource(ds);
         loaded.parseXML(element(xml(saved)));
         assertEquals(UniformSQL.PARSE_SUCCESS, loaded.getParseResult(), text);
         assertTrue(loaded.isLossy(), text);
         assertEquals(UniformSQL.PARSE_FAILED, loaded.getParseResult(), text);
         assertEquals(text, loaded.getSQLString());
      }
   }

   private static XJoin join(String column1, String op, String column2) {
      return new XJoin(new XExpression(column1, XExpression.FIELD),
                       new XExpression(column2, XExpression.FIELD), op);
   }

   private static String regenerate(UniformSQL sql) {
      UniformSQL copy = sql.clone();
      copy.clearSQLString();
      return normalize(copy.getSQLString());
   }

   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   private static JDBCDataSource postgresql() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77548t_postgresql");
      ds.setDriver("org.postgresql.Driver");
      ds.setURL("jdbc:postgresql://localhost:5432/db");
      ds.setProductVersion("16");
      assertEquals("postgresql", SQLHelper.getSQLHelper(ds).getSQLHelperType());
      return ds;
   }

   private static JDBCDataSource h2() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77548t_h2");
      ds.setDriver("org.h2.Driver");
      ds.setURL("jdbc:h2:mem:test");
      assertEquals("h2", SQLHelper.getSQLHelper(ds).getSQLHelperType());
      return ds;
   }

   private static JDBCDataSource oracle(boolean ansiJoin) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77548t_oracle_" + ansiJoin);
      ds.setDriver("oracle.jdbc.OracleDriver");
      ds.setURL("jdbc:oracle:thin:@localhost:1521:orcl");
      ds.setRuntimeProductName("oracle");
      ds.setProductVersion("19");
      ds.setAnsiJoin(ansiJoin);
      assertInstanceOf(OracleSQLHelper.class, SQLHelper.getSQLHelper(ds));
      return ds;
   }

   private static String savedXml(String text) throws IOException {
      try(InputStream in = UniformSQLLegacyJoinRoundTripTest.class.getResourceAsStream(
         "UniformSQLLegacyJoinRefusalTest-1.1.0.txt");
          BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
      {
         String line;

         while((line = reader.readLine()) != null) {
            String[] parts = line.split("\\|", 3);

            if(!line.startsWith("#") && parts.length == 3 && parts[0].equals("postgresql") &&
               parts[1].equals(text))
            {
               return parts[2];
            }
         }
      }

      throw new IllegalArgumentException("no saved xml: " + text);
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
}
